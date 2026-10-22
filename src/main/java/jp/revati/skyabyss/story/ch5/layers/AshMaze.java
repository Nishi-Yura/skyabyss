package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssShortcuts;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 真っ暗な灰の迷路 (docs/design/36 の 8 章。灰の層 y -120〜-240)。層の作りは tools/abyss/layer_ash.py (迷路の箱・入口の廊下・燠の番人の間)。
//   - 道: 迷路の床・壁から、入口 (ash_maze_in) と燠の番人の間 (ash_gate) のあいだの道を BFS で求める（置いたブロックから）。
//     道の跡は、光の術の灯り (AbyssLight) が届いた所でだけ、粒で見える（灯りの外では見えない）。
//   - 足音: 壁の向こうの足音。人が燠の番人の間から遠いほど間が長く、近いほど短い (footstepGap)。
//   - 見る者: 迷路の真ん中 (watch_ash_maze) の近くで一度だけ、輪郭の粒が立つ。近づくと消える（触れない）。
//   - 燠の番人: 人が番人の間に入ったとき一度だけ湧く (ember_keeper)。倒れたら AbyssShortcuts.open(server, "ash")。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AshMaze {
    public static final String KEY = "ash";
    public static final double REACH = 60;
    public static final double GATE_R = 6;
    public static final double WATCH_R = 9;
    public static final double WATCH_GONE_R = 3;
    public static final double LIGHT_R = 7;
    public static final int LIGHT_MIN = 4;
    public static final int STEP_FAR = 220;
    public static final int STEP_NEAR = 60;
    static final String KEEPER_TAG = "skyabyss_ash_keeper";
    static final String KEEPER = "ember_keeper";

    // 道 (一度だけ求める。迷路が建っていなければ空のまま)
    private static List<BlockPos> path = new ArrayList<>();
    // 見る者を見た人・足音の時計・入った人 (実行中だけ。人が入り直すと一度目の知らせが出る)
    private static final Map<UUID, Integer> STEP_CLOCK = new HashMap<>();
    private static final Set<UUID> ENTERED = new HashSet<>();
    private static final Set<UUID> WATCH_GONE = new HashSet<>();
    private static final Random RAND = new Random(0x5a11L);

    private AshMaze() {
    }

    // 迷路の入口・番人の間・見張りの印
    public static BlockPos entrance() {
        return AbyssBuild.mark("ash_maze_in");
    }

    public static BlockPos gate() {
        return AbyssBuild.mark("ash_gate");
    }

    public static BlockPos watchSpot() {
        return AbyssBuild.mark("watch_ash_maze");
    }

    // 道: 入口から番人の間まで、迷路の床の上を辿った列 (求めるのは一度だけ)。建っていなければ空
    public static List<BlockPos> path(ServerLevel level) {
        if (path.isEmpty()) {
            path = solve(level);
        }
        return path;
    }

    // 入口から番人の間まで、迷路の中だけを広さ優先で辿る。迷路の外（縦穴の外の壁の内側や空の所）へは出ない
    static List<BlockPos> solve(ServerLevel level) {
        BlockPos from = entrance();
        BlockPos to = gate();
        if (from == null || to == null) {
            return new ArrayList<>();
        }
        Map<BlockPos, BlockPos> came = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(from);
        came.put(from, from);
        boolean found = false;
        while (!q.isEmpty() && !found) {
            BlockPos cur = q.poll();
            for (BlockPos nx : new BlockPos[]{cur.east(), cur.west(), cur.south(), cur.north()}) {
                if (came.containsKey(nx) || !inMaze(nx, from) || !walkable(level, nx)) {
                    continue;
                }
                came.put(nx, cur);
                if (nx.equals(to) || nx.distSqr(to) <= 4) {
                    to = nx;
                    found = true;
                    break;
                }
                q.add(nx);
            }
        }
        List<BlockPos> out = new ArrayList<>();
        if (!found) {
            jp.revati.skyabyss.SkyAbyss.LOGGER.warn("[abyss] ash maze: no route from {} to {} (searched {} cells; from walkable {}, below {}, at {})",
                    from, to, came.size(), walkable(level, from), level.getBlockState(from.below()), level.getBlockState(from));
            return out;
        }
        for (BlockPos p = to; ; p = came.get(p)) {
            out.add(0, p);
            if (p.equals(from)) {
                break;
            }
        }
        return out;
    }

    // 迷路の箱 (入口から 40 マス以内で、縦穴の外側の半径 23 より外)
    private static boolean inMaze(BlockPos p, BlockPos from) {
        return p.distSqr(from) <= REACH * REACH && Math.hypot(p.getX(), p.getZ()) >= 23.5;
    }

    // 歩ける所: 足もとが床 (空気でない)・頭の上が空く (空気)
    private static boolean walkable(ServerLevel level, BlockPos p) {
        return !level.getBlockState(p.below()).isAir() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
    }

    // 人が迷路にいるとき、一定の間隔で動く: 灯りの下の道を見せる・足音・見る者・番人
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!AbyssBuild.built(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        BlockPos in = entrance();
        if (level == null || in == null) {
            return;
        }
        long t = level.getGameTime();
        List<ServerPlayer> here = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(in) <= REACH * REACH && Math.hypot(p.getX(), p.getZ()) >= 23.5) {
                here.add(p);
            }
        }
        if (here.isEmpty()) {
            return;
        }
        Data d = Data.get(server);
        for (ServerPlayer p : here) {
            if (ENTERED.add(p.getUUID())) {
                Notice.bar(p, Component.translatable("message.skyabyss.abyss.ash.enter"));
            }
            footstep(p, level);
        }
        if (t % 10 == 0) {
            reveal(level, here);
        }
        if (t % 10 == 5) {
            watch(level, here);
        }
        if (t % 20 == 10) {
            keeper(server, level, d, here);
        }
    }

    // 足音: 時計を進め、間が来たら壁の向こうで鳴らす（人からおよそ 3〜5 マス、前後左右のどこか）
    private static void footstep(ServerPlayer p, ServerLevel level) {
        int clock = STEP_CLOCK.merge(p.getUUID(), 1, Integer::sum);
        if (clock < footstepGap(p)) {
            return;
        }
        STEP_CLOCK.put(p.getUUID(), 0);
        double a = RAND.nextDouble() * Math.PI * 2;
        double r = 3 + RAND.nextDouble() * 2;
        level.playSound(null, p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r, SoundEvents.STONE_STEP,
                SoundSource.AMBIENT, 0.9f, 0.7f + RAND.nextFloat() * 0.3f);
    }

    // 足音の間 (tick)。番人の間に近いほど短い。公開の入口: AbyssHorror の足音と同じ時計で使える
    public static int footstepGap(ServerPlayer p) {
        BlockPos g = gate();
        BlockPos in = entrance();
        if (g == null || in == null) {
            return STEP_FAR;
        }
        double near = Math.sqrt(p.blockPosition().distSqr(g));
        double all = Math.sqrt(in.distSqr(g));
        double k = Math.max(0, Math.min(1, near / Math.max(1, all)));
        return (int) Math.round(STEP_NEAR + (STEP_FAR - STEP_NEAR) * k);
    }

    // 道の跡: 灯りの届く所の道だけを粒で見せる (人の近く、灯りが明るい所)
    private static void reveal(ServerLevel level, List<ServerPlayer> here) {
        List<BlockPos> route = path(level);
        if (route.isEmpty()) {
            return;
        }
        for (ServerPlayer p : here) {
            for (BlockPos b : route) {
                if (b.distSqr(p.blockPosition()) > LIGHT_R * LIGHT_R) {
                    continue;
                }
                if (AbyssLight.lightAt(level, b) >= LIGHT_MIN) {
                    level.sendParticles(p, ParticleTypes.SOUL, true, b.getX() + 0.5, b.getY() + 0.1, b.getZ() + 0.5, 1, 0.15, 0.0, 0.15, 0.0);
                }
            }
        }
    }

    // 見る者: 見張りの印の近くで、人が近づくまで輪郭の粒が立つ。近づくと消え、二度と出ない（その人には）
    private static void watch(ServerLevel level, List<ServerPlayer> here) {
        BlockPos w = watchSpot();
        if (w == null) {
            return;
        }
        for (ServerPlayer p : here) {
            if (WATCH_GONE.contains(p.getUUID())) {
                continue;
            }
            double d = Math.sqrt(p.blockPosition().distSqr(w));
            if (d <= WATCH_GONE_R) {
                WATCH_GONE.add(p.getUUID());
                continue;
            }
            if (d <= WATCH_R) {
                for (int i = 0; i < 6; i++) {
                    level.sendParticles(p, ParticleTypes.SMOKE, true, w.getX() + 0.5, w.getY() + i * 0.35, w.getZ() + 0.5, 2, 0.12, 0.05, 0.12, 0.01);
                }
            }
        }
    }

    // 燠の番人: 番人の間に入った人がいれば一度だけ湧く。倒れたら近道を開く (一度だけ)
    private static void keeper(MinecraftServer server, ServerLevel level, Data d, List<ServerPlayer> here) {
        BlockPos g = gate();
        if (g == null) {
            return;
        }
        if (d.state == 0) {
            boolean inside = false;
            for (ServerPlayer p : here) {
                if (Math.sqrt(p.blockPosition().distSqr(g)) <= GATE_R) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                return;
            }
            Entity e = AbyssMobSpawner.spawnAt(level, KEEPER, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 1);
            if (e != null) {
                e.removeTag(GuardMob.TEMP_TAG);
                e.addTag(KEEPER_TAG);
            }
            d.state = 1;
            d.setDirty();
            return;
        }
        if (d.state == 1 && level.hasChunkAt(g)) {
            boolean alive = !level.getEntitiesOfClass(Entity.class, new AABB(g).inflate(30),
                    e -> e.isAlive() && e.getTags().contains(KEEPER_TAG)).isEmpty();
            if (!alive) {
                clearGate(server);
            }
        }
    }

    // 番人を倒した: 近道を開き、迷路の中の人に知らせる（試験用にも使う）
    public static void clearGate(MinecraftServer server) {
        Data d = Data.get(server);
        d.state = 2;
        d.setDirty();
        boolean opened = AbyssShortcuts.open(server, KEY);
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level != null) {
            for (ServerPlayer p : level.players()) {
                if (opened) {
                    Notice.bar(p, Component.translatable("message.skyabyss.abyss.ash.keeper_down"));
                }
            }
        }
        SkyAbyss.LOGGER.info("[abyss] ash gate cleared (shortcut {})", opened ? "opened" : "already open");
    }

    // 番人の状態 (0 まだ・1 戦い中・2 倒した)
    public static int gateState(MinecraftServer server) {
        return Data.get(server).state;
    }

    // 試験用: 番人の状態を戻す (近道は別に閉じる)
    public static void resetGate(MinecraftServer server) {
        Data d = Data.get(server);
        d.state = 0;
        d.setDirty();
        AbyssShortcuts.reset(server);
    }

    // 番人の間の状態
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        int state;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_ash_maze");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.state = tag.getInt("Gate");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Gate", state);
            return tag;
        }
    }
}
