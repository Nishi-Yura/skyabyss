package jp.revati.skyabyss.world.continent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 名のある強敵 (docs/design/44 の 5): 土地ごとに一体、まれに現れる名づきの強敵。
// 人がその土地にいる間、2 秒ごとに 1/CHANCE で、32〜48 ブロック先に一体だけ現れる (その土地に生きている名づきがいれば出ない)。
// 名札・体力の帯 (近くの人に)・現れたときの知らせ。倒すと近くの人みなに鐘銭、その土地の次は 20 分後。離れて消えたときは 2 分後
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NamedFoes {
    public static final String TAG = "skyabyss_named_foe";
    public static final int EVERY = 40;
    public static final int CHANCE = 90;
    public static final int MIN_DIST = 32;
    public static final int MAX_DIST = 48;
    public static final double BAR_RANGE = 40;
    public static final double REWARD_RANGE = 32;
    public static final long COOLDOWN_KILLED = 24000;
    public static final long COOLDOWN_LOST = 2400;
    static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "named_foe_health");

    // entity: 名づきにする敵、name: 名札の鍵、night: 夜だけ、health: 体力の倍率 (野営の敵を名づきにするとき)、coins: 倒した人ごとの鐘銭
    public record Def(String entity, String name, boolean night, double health, int coins) {
    }

    // 土地の番号 (ContinentMap: MEADOW, OUTSKIRTS, WHALE, TIDE, STORM, EMBER) ごと
    static final Def[] DEFS = {
        new Def("skyabyss:bone_standard", "entity.skyabyss.named.meadow", true, 2.0, 40),
        new Def("skyabyss:bandit_chief", "entity.skyabyss.named.island", false, 1.8, 25),
        new Def("skyabyss:rock_boar", "entity.skyabyss.rock_boar", false, 1.0, 80),
        new Def("skyabyss:tide_crab", "entity.skyabyss.tide_crab", false, 1.0, 120),
        new Def("skyabyss:storm_ram", "entity.skyabyss.storm_ram", false, 1.0, 160),
        new Def("skyabyss:ash_lizard", "entity.skyabyss.ash_lizard", false, 1.0, 220),
    };

    private static final Map<Integer, UUID> ALIVE = new HashMap<>();
    private static final Map<Integer, Long> NEXT = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<UUID, Integer> REGION_OF = new HashMap<>();

    private NamedFoes() {
    }

    @Nullable
    public static Def def(int region) {
        return region >= 0 && region < DEFS.length ? DEFS[region] : null;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (!BARS.isEmpty() && server.getTickCount() % 5 == 0) {
            bars(level);
        }
        if (server.getTickCount() % EVERY != 31 || jp.revati.skyabyss.SelfTestRunner.enabled()) {
            return;
        }
        if (!(level.getChunkSource().getGenerator() instanceof ContinentChunkGenerator) || !jp.revati.skyabyss.rpg.rules.RpgRules.adventure(level)) {
            return;
        }
        forget(level);
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && level.random.nextInt(CHANCE) == 0) {
                trySpawn(level, p);
            }
        }
    }

    // その人の土地に名づきを出せれば出す
    @Nullable
    public static Mob trySpawn(ServerLevel level, ServerPlayer p) {
        int region = ContinentMap.get().region(p.getBlockX(), p.getBlockZ());
        Def d = def(region);
        if (d == null || ALIVE.containsKey(region) || level.getGameTime() < NEXT.getOrDefault(region, 0L) || (d.night() && !RegionSpawner.night(level))) {
            return null;
        }
        for (int tries = 0; tries < 6; tries++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = MIN_DIST + level.random.nextDouble() * (MAX_DIST - MIN_DIST);
            int x = (int) Math.floor(p.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(angle) * dist);
            BlockPos at = RegionSpawner.ground(level, x, z);
            if (at == null || ContinentMap.get().region(x, z) != region || jp.revati.skyabyss.opening.site.SiteGuard.inTown(level, at) || ContinentRoads.flattened(x + 0.5, z + 0.5)) {
                continue;
            }
            Mob m = spawnAt(level, region, at, p);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    // 決まった場所に名づきを出す (自己テスト・命令からも使う)
    @Nullable
    public static Mob spawnAt(ServerLevel level, int region, BlockPos at, ServerPlayer owner) {
        Def d = def(region);
        if (d == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(d.entity()))) {
            return null;
        }
        Mob m = RegionSpawner.spawn(level, d.entity(), at, owner);
        if (m == null) {
            return null;
        }
        Component name = Component.translatable(d.name());
        m.setCustomName(name);
        m.setCustomNameVisible(true);
        m.addTag(TAG);
        if (d.health() != 1.0) {
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && !hp.hasModifier(HEALTH_ID)) {
                hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, d.health() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        m.setHealth(m.getMaxHealth());
        ALIVE.put(region, m.getUUID());
        REGION_OF.put(m.getUUID(), region);
        BARS.put(m.getUUID(), new ServerBossEvent(name, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(m) < 80 * 80) {
                Notice.alert(p, "message.skyabyss.named.appear", name);
            }
        }
        return m;
    }

    public static boolean named(Entity e) {
        return e.getTags().contains(TAG);
    }

    @Nullable
    public static ServerBossEvent bar(UUID id) {
        return BARS.get(id);
    }

    // 体力の帯: 近くの人に見せ、遠い人から外す。倒れた・消えた物の帯は片づける
    static void bars(ServerLevel level) {
        for (var it = BARS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Entity e = level.getEntity(en.getKey());
            ServerBossEvent bar = en.getValue();
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                bar.removeAllPlayers();
                it.remove();
                continue;
            }
            bar.setProgress(Math.max(0f, le.getHealth() / le.getMaxHealth()));
            for (ServerPlayer p : level.players()) {
                boolean near = p.distanceToSqr(le) < BAR_RANGE * BAR_RANGE;
                if (near && !bar.getPlayers().contains(p)) {
                    bar.addPlayer(p);
                } else if (!near && bar.getPlayers().contains(p)) {
                    bar.removePlayer(p);
                }
            }
        }
    }

    // 離れて消えた名づきの記録を落とす (次は COOLDOWN_LOST 後)
    static void forget(ServerLevel level) {
        for (var it = ALIVE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Entity e = level.getEntity(en.getValue());
            if (e == null || !e.isAlive()) {
                if (e == null) {
                    NEXT.put(en.getKey(), level.getGameTime() + COOLDOWN_LOST);
                }
                REGION_OF.remove(en.getValue());
                it.remove();
            }
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !named(e)) {
            return;
        }
        Integer region = REGION_OF.remove(e.getUUID());
        Def d = region == null ? null : def(region);
        if (region != null) {
            ALIVE.remove(region);
            NEXT.put(region, level.getGameTime() + COOLDOWN_KILLED);
        }
        ServerBossEvent bar = BARS.remove(e.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        if (d == null) {
            return;
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, e.getBoundingBox().inflate(REWARD_RANGE), ServerPlayer::isAlive)) {
            jp.revati.skyabyss.facility.Coins.give(p, d.coins());
            Notice.gain(p, "message.skyabyss.named.down", e.getDisplayName(), d.coins());
        }
    }

    // 自己テスト用: 記録を空にする
    public static void reset(ServerLevel level) {
        for (UUID id : List.copyOf(BARS.keySet())) {
            BARS.remove(id).removeAllPlayers();
        }
        ALIVE.clear();
        NEXT.clear();
        REGION_OF.clear();
    }

    public static long nextAt(int region) {
        return NEXT.getOrDefault(region, 0L);
    }
}
