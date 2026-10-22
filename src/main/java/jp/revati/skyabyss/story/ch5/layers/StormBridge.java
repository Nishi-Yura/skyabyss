package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 拍子で落ちる雷の道 (docs/design/36 の 8 章。雷の層 y 0〜-120)。層の作りは tools/abyss/layer_storm.py (銅の吊り橋・三区間・避雷の像の印 idol_0〜2)。
//   雷は世界の時間で決まる拍 (BEAT = 80 tick = 4 秒) に一度。予告 (WARN = 30 tick = 1.5 秒) を区間の床に赤で出し、拍で当てる。
//   心臓の音 (鼓動) は、鼓動の術を覚えた人にだけ、拍の前に二度聞こえる (BEAT - 20 と BEAT - 12)。予告のない拍は、そのまま安全な間になる。
//   区間の番号は PATTERN の順 (拍の番号で決まる)。避雷の像 (lightning_idol。abyss/mobs) を壊すと、その区間は止む（以後、雷はほかの区間へ回る）。
//   帯電の羽虫 (charged_moth) が橋の近くにいれば、いちばん近い区間へ雷を引く。
//   動くのは、深淵に人がいて橋の近く (RANGE 以内) にいるときだけ。雷の拍そのものは世界の時間なので、止まっている間も拍は進む（再開しても同じ拍）。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBridge {
    public static final int BEAT = 80;
    public static final int WARN = 30;
    public static final int[] PATTERN = {0, 2, 1, 2, 0, 1};
    public static final int SECTIONS = 3;
    public static final double RANGE = 48;
    public static final double SECTION_RADIUS = 4.5;
    public static final float HIT = 9f;
    public static final double MOTH_DRAW = 40;
    public static final int STATE_UNSPAWNED = 0;
    public static final int STATE_ALIVE = 1;
    public static final int STATE_STOPPED = 2;
    static final String IDOL_TAG = "skyabyss_storm_idol_";
    static final String MOTH_TAG = "skyabyss_storm_moth";
    static final String IDOL = "lightning_idol";
    static final String MOTH = "charged_moth";

    private StormBridge() {
    }

    // 拍 beat の区間の番号 (PATTERN の順)
    public static int pattern(long beat) {
        return PATTERN[(int) Math.floorMod(beat, (long) PATTERN.length)];
    }

    // 拍 beat に雷が落ちる区間。states は区間ごとの状態 (STATE_*)。moth は羽虫が引く区間 (無ければ -1)。
    // 止まった区間へは落ちず、次に生きている区間へ回る。生きている区間が無ければ -1 (その拍は安全な間)
    public static int sectionAt(long beat, int[] states, int moth) {
        int base = moth >= 0 ? moth : pattern(beat);
        for (int k = 0; k < SECTIONS; k++) {
            int s = (base + k) % SECTIONS;
            if (states[s] != STATE_STOPPED) {
                return s;
            }
        }
        return -1;
    }

    // 区間の真ん中 (印 idol_s の足もと)。無ければ null
    public static Vec3 sectionCenter(int s) {
        BlockPos p = idolPos(s);
        return p == null ? null : new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    public static BlockPos idolPos(int s) {
        return AbyssBuild.mark("idol_" + s);
    }

    // 橋の真ん中 (両端の印の中ほど)。無ければ null
    public static Vec3 center() {
        BlockPos a = AbyssBuild.mark("storm_bridge_a");
        BlockPos b = AbyssBuild.mark("storm_bridge_b");
        if (a == null || b == null) {
            return null;
        }
        return new Vec3((a.getX() + b.getX()) / 2.0 + 0.5, (a.getY() + b.getY()) / 2.0, (a.getZ() + b.getZ()) / 2.0 + 0.5);
    }

    // 試験用・表示用: 区間の状態
    public static int[] states(MinecraftServer server) {
        return Data.get(server).states.clone();
    }

    // 試験用: 区間を止める（像が壊れたのと同じ）
    public static void stop(MinecraftServer server, int s) {
        Data d = Data.get(server);
        d.states[s] = STATE_STOPPED;
        d.setDirty();
    }

    // 試験用: 区間の状態を戻す
    public static void reset(MinecraftServer server) {
        Data d = Data.get(server);
        for (int s = 0; s < SECTIONS; s++) {
            d.states[s] = STATE_UNSPAWNED;
        }
        d.setDirty();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!AbyssBuild.built(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        Vec3 c = center();
        if (level == null || c == null) {
            return;
        }
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : java.util.List.copyOf(level.players())) {
            if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) < RANGE && Math.abs(p.getY() - c.y) < 40) {
                near.add(p);
            }
        }
        if (near.isEmpty()) {
            return;
        }
        Data d = Data.get(server);
        long t = level.getGameTime();
        int phase = (int) Math.floorMod(t, (long) BEAT);
        long beat = Math.floorDiv(t, (long) BEAT);
        if (t % 20 == 0) {
            upkeep(level, d, near, c);
        }
        if (phase == BEAT - 20 || phase == BEAT - 12) {
            heartbeat(near);
        }
        if (phase == BEAT - WARN) {
            strike(level, d, beat, c);
        }
    }

    // 像と羽虫の見張り: 像は一度だけ出し、消えたら止める。羽虫は橋の近くに一匹いるようにする
    private static void upkeep(ServerLevel level, Data d, List<ServerPlayer> near, Vec3 c) {
        for (int s = 0; s < SECTIONS; s++) {
            BlockPos pos = idolPos(s);
            if (pos == null) {
                continue;
            }
            if (d.states[s] == STATE_UNSPAWNED) {
                spawn(level, IDOL, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, IDOL_TAG + s);
                d.states[s] = STATE_ALIVE;
                d.setDirty();
            } else if (d.states[s] == STATE_ALIVE && level.hasChunkAt(pos) && !alive(level, pos, IDOL_TAG + s, 6)) {
                d.states[s] = STATE_STOPPED;
                d.setDirty();
                SkyAbyss.LOGGER.info("[abyss] storm section {} stopped (idol down)", s);
                for (ServerPlayer p : near) {
                    Notice.bar(p, Component.translatable("message.skyabyss.abyss.storm.idol_down"));
                }
            }
        }
        if (level.hasChunkAt(BlockPos.containing(c)) && !alive(level, BlockPos.containing(c), MOTH_TAG, 60)) {
            spawn(level, MOTH, c.x, c.y + 6, c.z, MOTH_TAG);
        }
    }

    private static void heartbeat(List<ServerPlayer> near) {
        for (ServerPlayer p : near) {
            if (AbyssArts.data(p).has(AbyssArt.HEARTBEAT)) {
                p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1f, 1f);
            }
        }
    }

    // 拍の予告と、当たり (WARN 後)
    private static void strike(ServerLevel level, Data d, long beat, Vec3 c) {
        int s = sectionAt(beat, d.states, mothSection(level, c));
        if (s < 0) {
            return;
        }
        Vec3 sc = sectionCenter(s);
        if (sc == null) {
            return;
        }
        Telegraph.in(level, TelegraphShape.circle(sc.x, sc.z, SECTION_RADIUS)).floor(sc.y).warn(WARN).danger().owner(null)
                .onImpact(lvl -> bolt(lvl, sc))
                .onHit((lvl, p) -> p.hurt(lvl.damageSources().lightningBolt(), HIT))
                .start();
    }

    private static void bolt(ServerLevel lvl, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(lvl);
        if (b != null) {
            b.moveTo(at.x, at.y, at.z);
            b.setVisualOnly(true);
            lvl.addFreshEntity(b);
        }
        lvl.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2f, 1f);
    }

    // 橋の近くの羽虫が、いちばん近い区間を引く (無ければ -1)
    static int mothSection(ServerLevel level, Vec3 c) {
        List<Entity> moths = level.getEntitiesOfClass(Entity.class, new AABB(c.x - MOTH_DRAW, c.y - 20, c.z - MOTH_DRAW,
                c.x + MOTH_DRAW, c.y + 20, c.z + MOTH_DRAW), e -> e.isAlive() && e.getTags().contains(MOTH_TAG));
        if (moths.isEmpty()) {
            return -1;
        }
        Vec3 m = moths.get(0).position();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int s = 0; s < SECTIONS; s++) {
            Vec3 sc = sectionCenter(s);
            if (sc == null) {
                continue;
            }
            double dd = sc.distanceToSqr(m.x, sc.y, m.z);
            if (dd < bestD) {
                bestD = dd;
                best = s;
            }
        }
        return best;
    }

    private static boolean alive(ServerLevel level, BlockPos at, String tag, double r) {
        return !level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(r), e -> e.isAlive() && e.getTags().contains(tag)).isEmpty();
    }

    private static void spawn(ServerLevel level, String id, double x, double y, double z, String tag) {
        Entity e = AbyssMobSpawner.spawnAt(level, id, x, y, z, 1);
        if (e != null) {
            e.removeTag(GuardMob.TEMP_TAG);
            e.addTag(tag);
        }
    }

    // 区間ごとの状態 (ワールドに一つ)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        final int[] states = new int[SECTIONS];

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_storm_bridge");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            int[] s = tag.getIntArray("States");
            for (int i = 0; i < Math.min(SECTIONS, s.length); i++) {
                d.states[i] = s[i];
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putIntArray("States", states);
            return tag;
        }
    }
}
