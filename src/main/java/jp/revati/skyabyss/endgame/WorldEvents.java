package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteGuard;
import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import jp.revati.skyabyss.world.continent.NamedFoes;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 大陸の大事件 (docs/design/54 の 3.3。Wynncraft のワールドイベント)。
//   EVERY (45 分) ごとに、岩鯨・潮殻・雷翼・余燼のどれかの土地の見どころに、その土地の名のある強敵の大型版「大いなる〜」が来る。
//   来たことは二章からの人みなに知らせ、地図 (MapMarks.foeMarks) と赤い光の柱で場所を示す。誰かが SPAWN_NEAR まで来ると現れる。
//   大型版: 体力は (12 + 近くの人 1 人につき 6) 倍、攻め 1.6 倍、大きさ 1.8 倍。近くのいちばん高い Lv が土地の目安 (TOP_LEVEL) より上なら、
//   差 1 につき体力 +12%・攻め +0.08 倍 (levelHealth・levelDamage)。30 秒ごとにその土地の敵が 2 体寄る。いる所から LEASH 離れると戻る。
//   LAST (15 分) で倒せなければ去る。倒すと、与えた分が SHARE 以上の人みなに報い: 名づきの鐘銭 × 4、封の箱 1
//   (レア 65・エピック 28・ヒロイック 7。帯は土地の目安の Lv)、土地の点 +4。いちばん与えた人は箱をもう一つ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WorldEvents {
    public static final int EVERY = 20 * 60 * 45;
    public static final int FIRST = 20 * 60 * 15;
    public static final int LAST = 20 * 60 * 15;
    public static final double SPAWN_NEAR = 72;
    public static final double LEASH = 40;
    public static final double BAR_RANGE = 80;
    public static final double SHARE = 0.03;
    public static final int ADDS_EVERY = 20 * 30;
    public static final int MIN_CHAPTER = 2;
    public static final String TAG = "skyabyss_world_event";
    // 前触れと封じのあいだに寄る敵 (去るときにいっしょに消す)
    public static final String ADD_TAG = "skyabyss_world_event_add";
    static final int RGB = 0xE8443A;
    static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "world_event_health");
    static final ResourceLocation DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "world_event_damage");
    static final ResourceLocation SCALE_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "world_event_scale");

    // いまの大事件 (一つだけ)
    public static final class Event {
        final int region;
        final BlockPos spot;
        @Nullable
        final String place;
        final long endAt;
        @Nullable
        UUID boss;
        float maxHealth;
        // 大きくする前の体力と、望む体力 (前の体力 × 倍率)。体力の上限 (1024) を超える分は、受ける傷を割って補う (onIncoming)
        float baseHealth;
        float wanted;
        long nextAdds;
        final Map<UUID, Float> dealt = new HashMap<>();
        @Nullable
        ServerBossEvent bar;
        // 戦いの流れ (WorldEventFight): まだ / 前触れ / 大いなるもの
        static final int WAITING = 0;
        static final int HERALD = 1;
        static final int GREAT = 2;
        int stage = WAITING;
        final List<UUID> herald = new ArrayList<>();
        // 前触れの体そのもの (区画がまだ動いていないと UUID で引けないので)
        final List<Mob> heraldMobs = new ArrayList<>();
        long heraldUntil;
        long nextHazard;
        int sealsDone;
        final List<WorldEventFight.Seal> seals = new ArrayList<>();
        long sealUntil;
        long nextSealAdds;
        boolean enraged;

        Event(int region, BlockPos spot, @Nullable String place, long endAt) {
            this.region = region;
            this.spot = spot;
            this.place = place;
            this.endAt = endAt;
        }

        public int region() {
            return region;
        }

        public BlockPos spot() {
            return spot;
        }
    }

    @Nullable
    private static Event current;
    private static long nextAt = -1;

    private WorldEvents() {
    }

    @Nullable
    // 次の大事件までの tick (まだ決まっていない・止めている世界なら -1。クエストログの「今日・今週」quest/Clocks)
    public static long ticksUntilNext(net.minecraft.server.MinecraftServer server) {
        if (nextAt < 0 || current != null) {
            return -1;
        }
        return Math.max(0, nextAt - server.overworld().getGameTime());
    }

    public static Event current() {
        return current;
    }

    static boolean eligible(ServerPlayer p) {
        return !(p instanceof FakePlayer) && Coins.chapter(p) >= MIN_CHAPTER;
    }

    // ---------------------------------------------------------------- 毎秒
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7 || jp.revati.skyabyss.SelfTestRunner.enabled()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (!jp.revati.skyabyss.rpg.rules.RpgRules.adventure(level)) {
            return;
        }
        long now = level.getGameTime();
        if (nextAt < 0) {
            nextAt = now + FIRST;
        }
        if (current == null) {
            if (now >= nextAt && level.players().stream().anyMatch(WorldEvents::eligible)) {
                begin(level, ContinentMap.WHALE + level.random.nextInt(4));
            }
            return;
        }
        step(level, now);
    }

    public static void step(ServerLevel level, long now) {
        Event e = current;
        if (e == null) {
            return;
        }
        if (now >= e.endAt) {
            leave(level);
            return;
        }
        Entity boss = e.boss == null ? null : level.getEntity(e.boss);
        if (e.boss == null) {
            ServerPlayer near = null;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && Math.hypot(p.getX() - e.spot.getX(), p.getZ() - e.spot.getZ()) <= SPAWN_NEAR && level.isLoaded(e.spot)) {
                    near = p;
                    break;
                }
            }
            if (near != null && e.stage == Event.WAITING) {
                // まず前触れ (先触れと群れ)。出せなければすぐ大いなるもの
                if (WorldEventFight.herald(level, e, near) == 0) {
                    spawnBoss(level, e, near);
                }
            } else if (near != null && e.stage == Event.HERALD && (WorldEventFight.heraldLeft(level, e) == 0 || now >= e.heraldUntil)) {
                spawnBoss(level, e, near);
            } else if (e.stage == Event.HERALD && now % 20 == 0) {
                // 前触れの残り (2026-11-08 の声「エリアに何体敵残ってるか出してほしい」)
                int left = WorldEventFight.heraldLeft(level, e);
                for (ServerPlayer p : level.players()) {
                    if (!p.isSpectator() && Math.hypot(p.getX() - e.spot.getX(), p.getZ() - e.spot.getZ()) <= SPAWN_NEAR) {
                        Notice.bar(p, jp.revati.skyabyss.ModText.battle("message.skyabyss.world_event.herald_left", left));
                    }
                }
            }
        } else if (boss instanceof Mob m && m.isAlive()) {
            if (Math.hypot(m.getX() - e.spot.getX(), m.getZ() - e.spot.getZ()) > LEASH) {
                m.setTarget(null);
                m.getNavigation().moveTo(e.spot.getX() + 0.5, m.getY(), e.spot.getZ() + 0.5, 1.3);
            }
            // 寄る敵は封じのあいだだけ (WorldEventFight)。ふだんは体力が半分を切ってから、間を空けて
            if (now >= e.nextAdds && m.getHealth() < m.getMaxHealth() * 0.5f && !WorldEventFight.sealed(e)) {
                e.nextAdds = now + ADDS_EVERY;
                adds(level, e, m);
            }
            var glow = m.getEffect(MobEffects.GLOWING);
            if (glow == null || glow.getDuration() < 40) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            }
            if (e.bar != null) {
                WorldEventFight.step(level, e, m, now);
                e.bar.setProgress(Math.max(0f, m.getHealth() / m.getMaxHealth()));
                for (ServerPlayer p : level.players()) {
                    boolean near = p.distanceToSqr(m) < BAR_RANGE * BAR_RANGE;
                    if (near && !e.bar.getPlayers().contains(p)) {
                        e.bar.addPlayer(p);
                    } else if (!near && e.bar.getPlayers().contains(p)) {
                        e.bar.removePlayer(p);
                    }
                }
            }
        } else if (boss == null && e.boss != null && level.isLoaded(e.spot)) {
            // 区画は読まれているのに体がない (消えた): もう一度現れられるようにする
            clearBar(e);
            e.boss = null;
        }
        // 赤い光の柱 (二章からの人みなに。倒すまで)
        for (ServerPlayer p : level.players()) {
            if (eligible(p) && p.connection != null) {
                PacketDistributor.sendToPlayer(p, new TitanEndNetwork.Beam("world_event", RGB, e.spot.getX() + 0.5, e.spot.getY() + 1.0, e.spot.getZ() + 0.5,
                        2.0f, 1.5f, 2.0f, 0f, e.spot.getY(), e.spot.getY() + 90, 1.1f, 40));
            }
        }
    }

    // ---------------------------------------------------------------- 始まり・現れる・去る・倒れる
    public static Event begin(ServerLevel level, int region) {
        if (current != null) {
            leave(level);
        }
        Spot s = spot(level, region);
        current = new Event(region, s.pos(), s.place(), level.getGameTime() + LAST);
        NamedFoes.Def d = NamedFoes.def(region);
        Component boss = Component.translatable("entity.skyabyss.world_event.name", Component.translatable(d == null ? "entity.skyabyss.rock_boar" : d.name()));
        Component where = s.place() == null ? Component.translatable("map.skyabyss.town." + jp.revati.skyabyss.world.continent.ContinentTowns.region(region - ContinentMap.WHALE))
                : Component.translatable(s.place());
        for (ServerPlayer p : level.players()) {
            if (eligible(p)) {
                Notice.alert(p, "message.skyabyss.world_event.begin", where, boss);
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 0.9f);
            }
        }
        return current;
    }

    record Spot(BlockPos pos, @Nullable String place) {
    }

    // その土地の見どころのうち、町から離れた所 (なければ土地の真ん中あたり)
    static Spot spot(ServerLevel level, int region) {
        ContinentMap map = ContinentMap.get();
        List<ContinentPois.Poi> ok = new ArrayList<>();
        for (ContinentPois.Poi poi : ContinentPois.all()) {
            if (poi.site() && map.region(poi.x(), poi.z()) == region
                    && !SiteGuard.inTown(level, new BlockPos(poi.x(), map.surface(poi.x(), poi.z()), poi.z()))) {
                ok.add(poi);
            }
        }
        if (!ok.isEmpty()) {
            ContinentPois.Poi poi = ok.get(level.random.nextInt(ok.size()));
            return new Spot(new BlockPos(poi.x(), map.surface(poi.x(), poi.z()) + 1, poi.z()), poi.titleKey());
        }
        BlockPos lair = NamedFoes.lair(region);
        BlockPos at = lair == null ? BlockPos.ZERO : lair;
        return new Spot(new BlockPos(at.getX(), map.surface(at.getX(), at.getZ()) + 1, at.getZ()), null);
    }

    @Nullable
    public static Mob spawnBoss(ServerLevel level, Event e, ServerPlayer near) {
        NamedFoes.Def d = NamedFoes.def(e.region);
        if (d == null) {
            return null;
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(d.entity())).orElse(null);
        if (type == null) {
            return null;
        }
        BlockPos at = null;
        for (int t = 0; t < 12 && at == null; t++) {
            at = Circuits.ground(level, e.spot.getX() + level.random.nextInt(7) - 3, e.spot.getZ() + level.random.nextInt(7) - 3);
        }
        if (at == null) {
            at = near.blockPosition().offset(6, 0, 0);
        }
        if (!(type.create(level) instanceof Mob m)) {
            return null;
        }
        int people = 0;
        int top = 0;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(at.getX(), at.getY(), at.getZ()) < BAR_RANGE * BAR_RANGE) {
                people++;
                top = Math.max(top, jp.revati.skyabyss.rpg.Growth.get(p).level);
            }
        }
        // いちばん高い Lv の人が土地の目安より上なら、その差だけ固く強く (Wynncraft のワールドイベントと同じく、強い人が来ても手応えが残る)
        int gap = Math.max(0, top - Circuits.TOP_LEVEL[Math.max(0, Math.min(Circuits.TOP_LEVEL.length - 1, e.region))]);
        double mult = health(Math.max(1, people)) * levelHealth(gap);
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        double base = m.getMaxHealth();
        modifier(m.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, mult - 1);
        modifier(m.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, levelDamage(gap) - 1);
        modifier(m.getAttribute(Attributes.SCALE), SCALE_ID, 0.8);
        m.setHealth(m.getMaxHealth());
        e.baseHealth = (float) base;
        e.wanted = (float) (mult * base);
        Component name = Component.translatable("entity.skyabyss.world_event.name", Component.translatable(d.name()));
        m.setCustomName(name);
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        m.addTag(TAG);
        // 現れる前に記す (onJoin が知らない大いなるものを消すので)
        e.boss = m.getUUID();
        if (!level.addFreshEntity(m)) {
            e.boss = null;
            return null;
        }
        m.setTarget(near);
        e.maxHealth = m.getMaxHealth();
        e.nextAdds = level.getGameTime() + ADDS_EVERY;
        e.stage = Event.GREAT;
        e.nextHazard = level.getGameTime() + 8 * 20L;
        clearBar(e);
        e.bar = new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(m) < BAR_RANGE * BAR_RANGE) {
                Notice.alert(p, "message.skyabyss.world_event.appear", name);
                p.playNotifySound(SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 0.7f);
            }
        }
        return m;
    }

    // 体力の倍率 (近くの人の数で伸びる)
    public static double health(int people) {
        return 6 + 3 * (people - 1);
    }

    // 近くのいちばん高い Lv と土地の目安の Lv の差 (gap) での、体力と攻めの倍率
    public static double levelHealth(int gap) {
        return 1 + 0.12 * Math.max(0, gap);
    }

    public static double levelDamage(int gap) {
        return 1.6 + 0.08 * Math.max(0, gap);
    }

    static void modifier(@Nullable AttributeInstance a, ResourceLocation id, double amount) {
        if (a != null && !a.hasModifier(id)) {
            a.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    static void adds(ServerLevel level, Event e, Mob boss) {
        ContinentMap map = ContinentMap.get();
        for (int i = 0, tries = 0; i < 2 && tries < 12; tries++) {
            int x = (int) Math.floor(boss.getX() + level.random.nextInt(17) - 8);
            int z = (int) Math.floor(boss.getZ() + level.random.nextInt(17) - 8);
            BlockPos g = Circuits.ground(level, x, z);
            ServerPlayer target = level.getNearestPlayer(boss, 32) instanceof ServerPlayer sp ? sp : null;
            if (g == null || target == null) {
                continue;
            }
            RegionSpawner.Entry en = RegionSpawner.pick(e.region, map.biome(x, z), RegionSpawner.night(level), level.random.nextInt(1000));
            Mob add = en == null ? null : RegionSpawner.spawn(level, en.entity(), g, target);
            if (add != null) {
                add.addTag(ADD_TAG);
                add.setTarget(target);
                i++;
            }
        }
    }

    static void clearBar(Event e) {
        if (e.bar != null) {
            e.bar.removeAllPlayers();
            e.bar = null;
        }
    }

    // 時間切れ: 大いなるものは去る
    public static void leave(ServerLevel level) {
        Event e = current;
        if (e == null) {
            return;
        }
        current = null;
        nextAt = level.getGameTime() + EVERY;
        clearBar(e);
        if (e.boss != null && level.getEntity(e.boss) instanceof LivingEntity le && le.isAlive()) {
            le.discard();
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(le) < BAR_RANGE * BAR_RANGE) {
                    Notice.warn(p, "message.skyabyss.world_event.gone", le.getDisplayName());
                }
            }
        }
        clearBeams(level);
        clearAdds(level, e);
    }

    // 前触れと寄った敵を消す (去るとき)
    static void clearAdds(ServerLevel level, Event e) {
        for (UUID id : e.herald) {
            if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
                le.discard();
            }
        }
        for (Mob m : e.heraldMobs) {
            if (!m.isRemoved()) {
                m.discard();
            }
        }
        for (Entity en : level.getAllEntities()) {
            if (en.getTags().contains(ADD_TAG) && en.isAlive() && en.distanceToSqr(e.spot.getX(), e.spot.getY(), e.spot.getZ()) < 96 * 96) {
                en.discard();
            }
        }
    }

    static void clearBeams(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.connection != null && !(p instanceof FakePlayer)) {
                PacketDistributor.sendToPlayer(p, new TitanEndNetwork.Beam("world_event", RGB, 0, 0, 0, 0f, 0f, 0f, 0f, 0, 0, 0f, 1));
            }
        }
    }

    // 与えた分 (人ごと)
    public static void dealt(UUID who, float amount) {
        Event e = current;
        if (e != null && amount > 0) {
            e.dealt.merge(who, amount, Float::sum);
        }
    }

    // 取り残された大いなるもの (時間切れのとき区画が読まれていなかった・サーバーが立ち直した・二体目になった) は、読まれたときに消す
    @SubscribeEvent
    static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getEntity().getTags().contains(TAG)) {
            return;
        }
        Event e = current;
        if (e == null || e.boss == null || !e.boss.equals(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // 固さ (soak): 体力の上限を超えた分は、受ける傷を割って補う
    @SubscribeEvent
    static void onIncoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        Event e = current;
        if (e != null && e.boss != null && event.getEntity().getUUID().equals(e.boss)) {
            float a = event.getAmount() / soak(e, event.getEntity());
            event.setAmount(WorldEventFight.sealed(e) ? a * WorldEventFight.SEALED : a);
        }
    }

    static float soak(Event e, LivingEntity boss) {
        return Math.max(1f, e.wanted / Math.max(1f, boss.getMaxHealth()));
    }

    // 実際の固さ (体力 × 傷の割り。自己テスト用)
    public static double effectiveHealth(Event e, LivingEntity boss) {
        return boss.getMaxHealth() * soak(e, boss);
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        Event e = current;
        if (e == null || e.boss == null || !event.getEntity().getUUID().equals(e.boss)) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            dealt(p.getUUID(), event.getNewDamage());
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        Event e = current;
        LivingEntity dead = event.getEntity();
        if (e == null || e.boss == null || !dead.getUUID().equals(e.boss) || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        current = null;
        nextAt = level.getGameTime() + EVERY;
        clearBar(e);
        clearBeams(level);
        reward(level, e, dead.getDisplayName());
    }

    // 倒したときの報い (人の一覧は、倒した場にいなくても与えた分のある人みな。落ちている人は除く)
    public static List<ServerPlayer> reward(ServerLevel level, Event e, Component name) {
        float total = 0;
        for (float v : e.dealt.values()) {
            total += v;
        }
        UUID top = null;
        float best = 0;
        for (var en : e.dealt.entrySet()) {
            if (en.getValue() > best) {
                best = en.getValue();
                top = en.getKey();
            }
        }
        NamedFoes.Def d = NamedFoes.def(e.region);
        int coins = (d == null ? 100 : d.coins()) * 4;
        int band = jp.revati.skyabyss.item.wild.WildGearTypes.band(Circuits.TOP_LEVEL[Math.max(0, Math.min(Circuits.TOP_LEVEL.length - 1, e.region))]);
        List<ServerPlayer> paid = new ArrayList<>();
        for (var en : e.dealt.entrySet()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                p = TEST.get(en.getKey());
            }
            if (p == null || total <= 0 || en.getValue() / total < SHARE) {
                continue;
            }
            int pct = Math.round(en.getValue() / total * 100);
            Coins.give(p, coins);
            int boxes = en.getKey().equals(top) ? 2 : 1;
            for (int i = 0; i < boxes; i++) {
                int r = p.getRandom().nextInt(100);
                GearTier tier = r < 65 ? GearTier.RARE : r < 93 ? GearTier.EPIC : GearTier.HEROIC;
                Circuits.giveBox(p, BoxKind.rarity(tier, band));
            }
            RegionPoints.add(p, e.region, 4);
            Notice.gain(p, "message.skyabyss.world_event.down", name, pct, coins, boxes);
            paid.add(p);
        }
        return paid;
    }

    // 自己テストの人 (FakePlayer は人の一覧にいない)
    static final Map<UUID, ServerPlayer> TEST = new HashMap<>();

    // 自己テスト・開発用: 記録を空にする
    public static void reset(ServerLevel level) {
        if (current != null) {
            leave(level);
        }
        current = null;
        nextAt = -1;
        TEST.clear();
    }

    public static long nextAt() {
        return nextAt;
    }

    // ---------------------------------------------------------------- 開発用
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        // /skyabyss event start [土地 2〜5] | here (いる所のそばに大いなるものを出す) | goto | end
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("event")
                        .then(Commands.literal("start").executes(c -> {
                            ServerLevel level = c.getSource().getServer().overworld();
                            begin(level, ContinentMap.WHALE + level.random.nextInt(4));
                            return 1;
                        }).then(Commands.argument("region", com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 5)).executes(c -> {
                            begin(c.getSource().getServer().overworld(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "region"));
                            return 1;
                        })))
                        .then(Commands.literal("goto").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Event e = current;
                            if (e == null) {
                                return 0;
                            }
                            int x = e.spot.getX() + 14;
                            int z = e.spot.getZ() + 14;
                            p.teleportTo(x + 0.5, ContinentMap.get().surface(x, z) + 2, z + 0.5);
                            return 1;
                        }))
                        .then(Commands.literal("end").executes(c -> {
                            leave(c.getSource().getServer().overworld());
                            return 1;
                        }))
                        // 見た目の確かめ: いる所の前に、封じの輪と同じ金色の「ここに立つ」印を三つ (10 秒)
                        .then(Commands.literal("goalrings").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            for (int i = 0; i < 3; i++) {
                                double a = Math.PI / 2 + (i - 1) * 0.7;
                                jp.revati.skyabyss.combat.Telegraph.in(p.serverLevel(), jp.revati.skyabyss.combat.TelegraphShape.circle(
                                        p.getX() - Math.cos(a) * 7 * (i == 1 ? 1.3 : 1), p.getZ() - Math.sin(a) * 7 * (i == 1 ? 1.3 : 1), WorldEventFight.SEAL_RADIUS))
                                        .floor(p.getY()).warn(0).active(200).goal().start();
                            }
                            return 1;
                        }))));
    }
}
