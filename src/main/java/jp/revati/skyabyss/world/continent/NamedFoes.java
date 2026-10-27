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

// 名のある強敵 (docs/design/44 の 5): 土地ごとに一体の名づきの強敵。土地ごとに決まった棲み家 (見どころの一つ。lair) があり、
// 地図にいつも出る (MapMarks.foeMarks)。人が棲み家の LAIR_NEAR 以内に来ると、その土地に生きている名づきがいなければ、棲み家に一体現れる。
// 生きている間は光る縁取りと、空へ伸びる光の柱 (遠くから見える)。名札・体力の帯 (近くの人に)・現れたときの知らせ。
// 倒すと近くの人みなに鐘銭、その土地の次は 20 分後。離れて消えたときは 2 分後。昼夜では閉じない (状態で入口を閉じない決まり)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NamedFoes {
    public static final String TAG = "skyabyss_named_foe";
    public static final int EVERY = 40;
    // 棲み家に近づいたとみなす距離と、名づきが棲み家から離れてよい距離 (超えたら棲み家へ戻る)
    public static final int LAIR_NEAR = 72;
    public static final double LEASH = 36;
    // 棲み家に選ぶ見どころの種類 (先にあるものほど選ぶ)
    static final String[] LAIR_KINDS = {"ruined_tower", "burnt_fort", "barrow", "stone_circle", "graveyard", "monastery", "forge_ruin", "mine", "cave",
            "watchtower", "bandit_camp", "ruined_house", "titan_ribs"};
    public static final double BAR_RANGE = 40;
    public static final double REWARD_RANGE = 32;
    public static final long COOLDOWN_KILLED = 24000;
    public static final long COOLDOWN_LOST = 2400;
    static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "named_foe_health");

    // entity: 名づきにする敵、name: 名札の鍵、health: 体力の倍率 (野営の敵を名づきにするとき)、coins: 倒した人ごとの鐘銭
    public record Def(String entity, String name, double health, int coins) {
    }

    // 土地の番号 (ContinentMap: MEADOW, OUTSKIRTS, WHALE, TIDE, STORM, EMBER) ごと
    static final Def[] DEFS = {
        new Def("skyabyss:bone_standard", "entity.skyabyss.named.meadow", 2.0, 40),
        // はじまりの島には名のある強敵を出さない (峠の頭目は峠の野盗の場面だけ。IslandTrail。2026-10-27 の声)
        null,
        new Def("skyabyss:rock_boar", "entity.skyabyss.rock_boar", 1.0, 80),
        new Def("skyabyss:tide_crab", "entity.skyabyss.tide_crab", 1.0, 120),
        new Def("skyabyss:storm_ram", "entity.skyabyss.storm_ram", 1.0, 160),
        new Def("skyabyss:ash_lizard", "entity.skyabyss.ash_lizard", 1.0, 220),
    };

    private static final Map<Integer, UUID> ALIVE = new HashMap<>();
    private static final Map<Integer, Long> NEXT = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<UUID, Integer> REGION_OF = new HashMap<>();
    private static BlockPos[] lairs;

    private NamedFoes() {
    }

    // 土地ごとの棲み家 (見どころの位置。y は 0)。名づきのいない土地・見どころのない土地は null
    @Nullable
    public static synchronized BlockPos lair(int region) {
        if (lairs == null) {
            List<ContinentPois.Poi> all = ContinentPois.all();
            BlockPos[] out = new BlockPos[DEFS.length];
            for (int r = 0; r < DEFS.length; r++) {
                if (DEFS[r] == null) {
                    continue;
                }
                search:
                for (String kind : LAIR_KINDS) {
                    for (ContinentPois.Poi poi : all) {
                        if (poi.kind().equals(kind) && ContinentMap.get().region(poi.x(), poi.z()) == r) {
                            out[r] = new BlockPos(poi.x(), 0, poi.z());
                            break search;
                        }
                    }
                }
            }
            lairs = out;
        }
        return region >= 0 && region < lairs.length ? lairs[region] : null;
    }

    // 今その土地の名づきが生きているか
    public static boolean alive(int region) {
        return ALIVE.containsKey(region);
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
            if (p.isAlive() && !p.isSpectator() && !jp.revati.skyabyss.story.reel.StoryReels.watching(p)) {
                trySpawn(level, p);
            }
        }
        leash(level);
    }

    // その人が棲み家の近くにいれば、その土地の名づきを棲み家に出す
    @Nullable
    public static Mob trySpawn(ServerLevel level, ServerPlayer p) {
        int region = ContinentMap.get().region(p.getBlockX(), p.getBlockZ());
        Def d = def(region);
        BlockPos lair = lair(region);
        if (d == null || lair == null || ALIVE.containsKey(region) || level.getGameTime() < NEXT.getOrDefault(region, 0L)
                || Math.hypot(p.getX() - lair.getX(), p.getZ() - lair.getZ()) > LAIR_NEAR || !level.isLoaded(lair)) {
            return null;
        }
        for (int tries = 0; tries < 8; tries++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = tries == 0 ? 0 : 3 + level.random.nextDouble() * 8;
            int x = (int) Math.floor(lair.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(lair.getZ() + Math.sin(angle) * dist);
            BlockPos at = RegionSpawner.ground(level, x, z);
            if (at == null || jp.revati.skyabyss.opening.site.SiteGuard.inTown(level, at)) {
                continue;
            }
            Mob m = spawnAt(level, region, at, p);
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    // 棲み家から LEASH より離れた名づきは、狙いを捨てて棲み家へ歩いて戻る (道や町まで追ってこない)
    static void leash(ServerLevel level) {
        for (var en : ALIVE.entrySet()) {
            BlockPos lair = lair(en.getKey());
            if (lair != null && level.getEntity(en.getValue()) instanceof Mob m && Math.hypot(m.getX() - lair.getX(), m.getZ() - lair.getZ()) > LEASH) {
                m.setTarget(null);
                m.getNavigation().moveTo(lair.getX() + 0.5, m.getY(), lair.getZ() + 0.5, 1.2);
            }
        }
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
            // 遠くから見える目印: 光る縁取り (名づきだけ) と、頭の上から空へ伸びる光の柱 (BEAM_RANGE 以内の人に)
            var glow = le.getEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            if (glow == null || glow.getDuration() < 40) {
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0, false, false));
            }
            beam(level, le);
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

    static final double BEAM_RANGE = 160;

    static void beam(ServerLevel level, LivingEntity le) {
        double top = le.getY() + le.getBbHeight() + 1;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(le) < BEAM_RANGE * BEAM_RANGE) {
                for (int i = 0; i < 12; i++) {
                    double y = top + level.random.nextDouble() * 40;
                    level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.END_ROD, true, le.getX(), y, le.getZ(), 1, 0.08, 0.4, 0.08, 0.0);
                }
                level.sendParticles(p, net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, true, le.getX(), le.getY() + 0.2, le.getZ(), 3, 0.6, 0.1, 0.6, 0.01);
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
            jp.revati.skyabyss.advancement.Beats.fire(p, "named:any");
            jp.revati.skyabyss.advancement.Beats.fire(p, "named:" + RegionSpawner.REGIONS[region]);
            Notice.gain(p, "message.skyabyss.named.down", e.getDisplayName(), d.coins());
        }
    }

    // 試遊用: /skyabyss dev named = 今いる土地の名づきを、棲み家・間を問わずそばに出す (権限 2)。その土地に生きている名づきがいれば出さない
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal(SkyAbyss.MODID).then(net.minecraft.commands.Commands.literal("dev")
                .requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("named").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel level = p.serverLevel();
                    int region = ContinentMap.get().region(p.getBlockX(), p.getBlockZ());
                    Def d = def(region);
                    if (d == null || ALIVE.containsKey(region)) {
                        ctx.getSource().sendFailure(Component.literal(d == null ? "no named foe for this land" : "a named foe of this land is already alive"));
                        return 0;
                    }
                    double a = level.random.nextDouble() * Math.PI * 2;
                    for (int tries = 0; tries < 12; tries++) {
                        int x = (int) Math.floor(p.getX() + Math.cos(a + tries) * 12);
                        int z = (int) Math.floor(p.getZ() + Math.sin(a + tries) * 12);
                        BlockPos at = RegionSpawner.ground(level, x, z);
                        if (at != null && spawnAt(level, region, at, p) != null) {
                            return 1;
                        }
                    }
                    ctx.getSource().sendFailure(Component.literal("no open ground nearby"));
                    return 0;
                }))));
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
