package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 戦いの部屋の「広さの使い方」(設計書 26 の 8.7 の B。ユーザーの決定: 部屋の大きさは変えない)。
//
//  一人・二人でも同じ広間。そのかわり
//   1. 敵は入口側に寄せて湧かせる (spawnNear・orderNear・bearingFor。人数が増えるほど広間いっぱいに戻る)
//   2. 床に「罠の板」(3×3、ひびの入った床と芯の一枚) を置く。踏んで 10 tick いると 40 tick の予告 (Telegraph。床に円が満ちる) のあとに働く。
//      人も敵も踏めば動かす (敵を押し込む・誘い込むのに使える)。人への傷は小さく (3)、敵へは体力の 25% (6〜24)。
//      崩れる床 (足を取られる) と噴き上げ (打ち上げて落とす) が交互。当たる円の外は無傷
//   3. 遮蔽の柱 (2×2、高さ 4)。半分は常設、残りは人数が 3 人から・5 人から、そのグループにだけ立つ (封印の扉のブロック)
//  罠の数は人数で増える (1 人 35% → 6 人 100%)。罠の板は敵のいる間だけ働き、敵がいなくなれば眠る
//
// 床と柱は、グループが部屋に入ったときに一度だけ置く (RoomPlayData に覚える。建て直されていたら置き直す)。置く場所は、平らな床・上が空気・
// 壁や置物から 1 マス離れた所だけ (部屋の仕掛けと重ならない)。入口・湧き場所・罠・柱は互いに離す (layout の距離の決まり)。
public final class RoomPlay {
    private RoomPlay() {
    }

    public enum Theme {
        BONE(Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.BONE_BLOCK, Blocks.BONE_BLOCK, SoundEvents.SKELETON_HURT, ParticleTypes.SOUL),
        TIDE(Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, ParticleTypes.BUBBLE_POP),
        STORM(Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER, SoundEvents.LIGHTNING_BOLT_IMPACT,
                ParticleTypes.ELECTRIC_SPARK),
        EMBER(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.SHROOMLIGHT, Blocks.BLACKSTONE, SoundEvents.BLAZE_SHOOT, ParticleTypes.SMALL_FLAME);

        public final Block ring;
        public final Block accent;
        public final Block pillar;
        final SoundEvent sound;
        final ParticleOptions spark;

        Theme(Block ring, Block accent, Block pillar, SoundEvent sound, ParticleOptions spark) {
            this.ring = ring;
            this.accent = accent;
            this.pillar = pillar;
            this.sound = sound;
            this.spark = spark;
        }
    }

    // 部屋。床の高さ floorY は床のブロックの y (人は floorY + 1 に立つ)。keepClear は湧き場所 (柱・罠を離す所)
    public record Room(String titan, String id, Theme theme, int x1, int x2, int z1, int z2, int floorY, int height, BlockPos entrance,
            List<BlockPos> keepClear) {
        public AABB aabb() {
            return new AABB(x1, floorY - 2, z1, x2 + 1, floorY + height + 2, z2 + 1);
        }

        public int area() {
            return (x2 - x1 + 1) * (z2 - z1 + 1);
        }
    }

    // ---------------------------------------------------------------- 数字 (人数の決まり)
    public static final int TRAP_R_TENTHS = 22;
    public static final double TRAP_R = TRAP_R_TENTHS / 10.0;
    // 罠の円の中で、踏みとして数える範囲 (板の 3×3 とほぼ同じ)
    public static final double TRIGGER_R = 1.6;
    public static final int TRIGGER_TICKS = 10;
    public static final int WARN_TICKS = 40;
    public static final int COOLDOWN_TICKS = 200;
    // ほかの傷とこの罠の当たりのあいだに空ける間
    public static final int SPACING_TICKS = 10;
    public static final float PLAYER_DAMAGE = 3f;
    public static final int PILLAR_SIZE = 2;
    public static final int PILLAR_HEIGHT = 4;
    // 柱の段の人数 (2 段目は 3 人から、3 段目は 5 人から)
    public static final int TIER2_FROM = 3;
    public static final int TIER3_FROM = 5;
    // 湧きを寄せる強さ (残す割合)。1 人 45%・2 人 60%・3 人 80%・4 人から全部
    private static final double[] KEEP = {1.0, 0.45, 0.60, 0.80, 1.0};
    // 入口からこれより近くには湧かせない (人の目の前に湧かない)
    public static final double MIN_SPAWN_FROM_ENTRANCE = 7.0;

    // その人数で湧き場所に残す割合
    public static double keepFraction(int entrants) {
        return KEEP[Math.max(1, Math.min(entrants, KEEP.length - 1))];
    }

    // 罠の総数 (部屋の広さから)。3〜8
    public static int trapTotal(int area) {
        return Math.max(3, Math.min(8, Math.round(area / 140f)));
    }

    // 柱の総数 (部屋の広さから)。4〜14
    public static int pillarTotal(int area) {
        return Math.max(4, Math.min(14, Math.round(area / 75f)));
    }

    // 働いている罠の数: 1 人 35%、一人増えるごとに 13% (6 人で全部)。少なくとも 1 つ
    public static int trapsArmed(int total, int entrants) {
        if (total <= 0) {
            return 0;
        }
        int n = Math.max(1, Math.min(entrants, 6));
        return Math.max(1, Math.min(total, (int) Math.ceil(total * (0.35 + 0.13 * (n - 1)) - 1e-9)));
    }

    // 柱の i 番目の段 (0 = 常設。半分、1 = 3 人から、2 = 5 人から)
    public static int pillarTier(int index, int total) {
        if (index < (int) Math.ceil(total * 0.5)) {
            return 0;
        }
        return index < (int) Math.ceil(total * 0.8) ? 1 : 2;
    }

    // その人数で立つ柱の最高の段
    public static int pillarTierFor(int entrants) {
        return entrants >= TIER3_FROM ? 2 : entrants >= TIER2_FROM ? 1 : 0;
    }

    public static int pillarsVisible(int total, int entrants) {
        int n = 0;
        int max = pillarTierFor(entrants);
        for (int i = 0; i < total; i++) {
            if (pillarTier(i, total) <= max) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 湧き場所を入口側に寄せる
    // 候補を入口に近い順に並べ、人数に応じた割合だけ残す (入口の近すぎる所は、残りが足りるかぎり除く)。4 人以上は並べ替えず全部
    public static List<BlockPos> spawnNear(List<BlockPos> candidates, BlockPos entrance, int entrants) {
        if (entrants >= 4 || candidates.size() <= 3) {
            return candidates;
        }
        List<BlockPos> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(p -> flat(p, entrance)));
        int keep = Math.max(3, (int) Math.ceil(sorted.size() * keepFraction(entrants)));
        // 入口に近すぎない候補を近い順に。足りなければ、近すぎる候補のうち入口から遠いものから足す
        List<BlockPos> far = new ArrayList<>();
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos p : sorted) {
            (flat(p, entrance) >= MIN_SPAWN_FROM_ENTRANCE ? far : near).add(p);
        }
        List<BlockPos> out = new ArrayList<>(far.subList(0, Math.min(keep, far.size())));
        for (int i = near.size() - 1; i >= 0 && out.size() < keep; i--) {
            out.add(near.get(i));
        }
        return List.copyOf(out);
    }

    // 番号つきの湧き口 (砦の 6 つの口など) を、少人数のときだけ入口に近い順に並べ替える。ids はもとの順 (多人数はそのまま)
    public static int[] orderNear(int[] ids, List<BlockPos> spots, BlockPos entrance, int entrants) {
        if (entrants >= 4) {
            return ids;
        }
        List<Integer> sorted = new ArrayList<>();
        for (int id : ids) {
            sorted.add(id);
        }
        sorted.sort(Comparator.comparingDouble(id -> flat(spots.get(id), entrance)));
        int[] out = new int[ids.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = sorted.get(i);
        }
        return out;
    }

    // 円の上に並べて湧かせる敵 (嵐の巣など) の角度: 3 人以上は与えられた角 (配置で回る) のまま、一人・二人は入口の向き (bearing) の左右に寄せる。
    // 入口の真上には湧かせない (0.9 ラジアン以上離す。半径 10 で入口まで 8.8 マス): i 番目は左右交互に、0.9・0.9・1.2〜1.35 …
    public static double angleNear(double original, int i, int count, double bearing, int entrants) {
        if (entrants >= 3) {
            return original;
        }
        double step = entrants <= 1 ? 0.3 : 0.45;
        double offset = (i % 2 == 0 ? 1 : -1) * (MIN_SPAWN_ANGLE + step * (i / 2));
        return bearing + offset;
    }

    public static final double MIN_SPAWN_ANGLE = 0.9;

    public static double bearingFor(double cx, double cz, BlockPos entrance) {
        return Math.atan2(entrance.getZ() + 0.5 - cz, entrance.getX() + 0.5 - cx);
    }

    static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    // ---------------------------------------------------------------- 置き場所 (罠・柱)
    public record Layout(List<BlockPos> traps, List<BlockPos> pillars) {
        public boolean isEmpty() {
            return traps.isEmpty() && pillars.isEmpty();
        }
    }

    // 部屋の決まった置き場所。trapOk / pillarOk は、その場所に置けるか (世界を見る。試験では常に true)。決定的 (部屋の名前で乱数を固定)
    // 罠の位置は板の中心 (床のブロックの座標)、柱の位置は 2×2 の最小の角 (床のブロックの座標)
    public static Layout layout(Room r, Predicate<BlockPos> trapOk, Predicate<BlockPos> pillarOk) {
        Random rng = new Random(r.id().hashCode() * 31L + 7L);
        List<BlockPos> grid = new ArrayList<>();
        for (int x = r.x1() + 3; x <= r.x2() - 3; x += 2) {
            for (int z = r.z1() + 3; z <= r.z2() - 3; z += 2) {
                grid.add(new BlockPos(x, r.floorY(), z));
            }
        }
        java.util.Collections.shuffle(grid, rng);
        List<BlockPos> traps = new ArrayList<>();
        int nTrap = trapTotal(r.area());
        for (BlockPos p : grid) {
            if (traps.size() >= nTrap) {
                break;
            }
            if (flat(p, r.entrance()) < 9 || tooNear(p.getX() + 0.5, p.getZ() + 0.5, r.keepClear(), 3.2) || tooNearPos(p.getX() + 0.5, p.getZ() + 0.5, traps, 9.0)) {
                continue;
            }
            if (trapOk.test(p)) {
                traps.add(p);
            }
        }
        java.util.Collections.shuffle(grid, rng);
        List<BlockPos> pillars = new ArrayList<>();
        int nPillar = pillarTotal(r.area());
        for (BlockPos p : grid) {
            if (pillars.size() >= nPillar) {
                break;
            }
            // 柱の中心は 2×2 の真ん中 (x + 1.0, z + 1.0)
            double cx = p.getX() + 1.0;
            double cz = p.getZ() + 1.0;
            if (Math.hypot(cx - r.entrance().getX() - 0.5, cz - r.entrance().getZ() - 0.5) < 7 || tooNear(cx, cz, r.keepClear(), 3.0)
                    || tooNearPos(cx, cz, traps, 5.0) || tooNearPillars(cx, cz, pillars, 5.0)) {
                continue;
            }
            if (pillarOk.test(p)) {
                pillars.add(p);
            }
        }
        // 働く罠は先頭から: 入口に近い順 (一人のときも、罠が自分と敵の通り道にある)
        traps.sort(Comparator.comparingDouble(p -> flat(p, r.entrance())));
        return new Layout(List.copyOf(traps), List.copyOf(pillars));
    }

    private static boolean tooNear(double x, double z, List<BlockPos> points, double d) {
        for (BlockPos p : points) {
            if (Math.hypot(x - p.getX() - 0.5, z - p.getZ() - 0.5) < d) {
                return true;
            }
        }
        return false;
    }

    private static boolean tooNearPos(double x, double z, List<BlockPos> traps, double d) {
        for (BlockPos p : traps) {
            if (Math.hypot(x - p.getX() - 0.5, z - p.getZ() - 0.5) < d) {
                return true;
            }
        }
        return false;
    }

    private static boolean tooNearPillars(double cx, double cz, List<BlockPos> pillars, double d) {
        for (BlockPos p : pillars) {
            if (Math.hypot(cx - p.getX() - 1.0, cz - p.getZ() - 1.0) < d) {
                return true;
            }
        }
        return false;
    }

    // 罠の位置 (板の中心) と柱が、円・足跡として重ならない: 試験が使う距離の検査。問題の一覧 (空なら良い)
    public static List<String> overlaps(Room r, Layout l) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.traps().size(); i++) {
            BlockPos t = l.traps().get(i);
            double tx = t.getX() + 0.5;
            double tz = t.getZ() + 0.5;
            if (tx - TRAP_R < r.x1() || tx + TRAP_R > r.x2() + 1 || tz - TRAP_R < r.z1() || tz + TRAP_R > r.z2() + 1) {
                out.add(r.id() + " trap " + i + " sticks out of the room");
            }
            if (Math.hypot(tx - r.entrance().getX() - 0.5, tz - r.entrance().getZ() - 0.5) < TRAP_R + 6) {
                out.add(r.id() + " trap " + i + " is at the entrance");
            }
            for (BlockPos k : r.keepClear()) {
                if (Math.hypot(tx - k.getX() - 0.5, tz - k.getZ() - 0.5) < TRAP_R + 1.0) {
                    out.add(r.id() + " trap " + i + " covers a spawn point " + k);
                }
            }
            for (int j = i + 1; j < l.traps().size(); j++) {
                BlockPos u = l.traps().get(j);
                if (Math.hypot(tx - u.getX() - 0.5, tz - u.getZ() - 0.5) < 2 * TRAP_R + 2) {
                    out.add(r.id() + " traps " + i + " and " + j + " overlap");
                }
            }
            for (int j = 0; j < l.pillars().size(); j++) {
                BlockPos p = l.pillars().get(j);
                // 柱の 2×2 の最も近い点と、罠の円の距離
                double nx = Math.max(p.getX(), Math.min(tx, p.getX() + PILLAR_SIZE));
                double nz = Math.max(p.getZ(), Math.min(tz, p.getZ() + PILLAR_SIZE));
                if (Math.hypot(tx - nx, tz - nz) < TRAP_R + 1.0) {
                    out.add(r.id() + " pillar " + j + " is inside trap " + i);
                }
            }
        }
        for (int j = 0; j < l.pillars().size(); j++) {
            BlockPos p = l.pillars().get(j);
            if (p.getX() < r.x1() + 2 || p.getX() + PILLAR_SIZE > r.x2() - 1 || p.getZ() < r.z1() + 2 || p.getZ() + PILLAR_SIZE > r.z2() - 1) {
                out.add(r.id() + " pillar " + j + " hugs the wall");
            }
            for (BlockPos k : r.keepClear()) {
                if (Math.hypot(p.getX() + 1.0 - k.getX() - 0.5, p.getZ() + 1.0 - k.getZ() - 0.5) < 2.5) {
                    out.add(r.id() + " pillar " + j + " sits on a spawn point " + k);
                }
            }
            for (int m = j + 1; m < l.pillars().size(); m++) {
                BlockPos q = l.pillars().get(m);
                if (Math.hypot(p.getX() - q.getX(), p.getZ() - q.getZ()) < 4.5) {
                    out.add(r.id() + " pillars " + j + " and " + m + " are too close");
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 置く (世界の見かけ)
    // 床のブロックの y (人は y + 1 に立つ) の、板の 3×3 と柱の 2×2 の場所が置けるか: 平らな床 (ふつうのブロック・流体なし・
    // ブロックエンティティなし)・上が空気で、まわり 1 マスも空気 (壁や置物から離れている)
    public static boolean tileOk(ServerLevel level, BlockPos floor, int half, int clearAbove) {
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                boolean edge = Math.abs(dx) > half || Math.abs(dz) > half;
                for (int dy = 1; dy <= clearAbove; dy++) {
                    BlockState s = level.getBlockState(floor.offset(dx, dy, dz));
                    if (!s.isAir() || !s.getFluidState().isEmpty()) {
                        return false;
                    }
                }
                BlockState f = level.getBlockState(floor.offset(dx, 0, dz));
                if (edge) {
                    if (f.isAir() || !f.getFluidState().isEmpty()) {
                        return false;
                    }
                } else if (f.getBlock().getClass() != Block.class || f.hasBlockEntity() || !f.getFluidState().isEmpty()
                        || !f.isFaceSturdy(level, floor.offset(dx, 0, dz), net.minecraft.core.Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 板 (3×3) は half = 1、柱 (2×2) の最小の角から tile を調べるときは、中心の隅 (x + 0, z + 0) を基準に half = 1 の 3×3 で見る
    public static boolean trapSiteOk(ServerLevel level, BlockPos center) {
        return tileOk(level, center, 1, 3);
    }

    public static boolean pillarSiteOk(ServerLevel level, BlockPos corner) {
        return tileOk(level, corner, 1, PILLAR_HEIGHT + 1);
    }

    // 板と柱を置く (常設の柱の段 0 だけ。段 1・2 は封印の扉のブロックで、グループごとに立てる)。置いた数 (罠, 柱)
    static void placeBlocks(ServerLevel level, Room r, Layout l) {
        BlockState ring = r.theme().ring.defaultBlockState();
        BlockState accent = r.theme().accent.defaultBlockState();
        for (BlockPos t : l.traps()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(t.offset(dx, 0, dz), dx == 0 && dz == 0 ? accent : ring, Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockState pillar = r.theme().pillar.defaultBlockState();
        List<BlockPos> gated = new ArrayList<>();
        for (int i = 0; i < l.pillars().size(); i++) {
            BlockPos p = l.pillars().get(i);
            int tier = pillarTier(i, l.pillars().size());
            for (BlockPos c : pillarCells(p)) {
                if (tier == 0) {
                    level.setBlock(c, pillar, Block.UPDATE_CLIENTS);
                } else {
                    gated.add(c);
                }
            }
        }
        // 段 1・2 の柱の場所に封印の扉を置く (既定は開いている = だれにも見えず、当たりもない)
        GroupGates.place(level, gated, true);
    }

    // 柱の 2×2 の、高さ PILLAR_HEIGHT のブロックの座標 (floor の一つ上から)
    public static List<BlockPos> pillarCells(BlockPos corner) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = 0; dx < PILLAR_SIZE; dx++) {
            for (int dz = 0; dz < PILLAR_SIZE; dz++) {
                for (int dy = 1; dy <= PILLAR_HEIGHT; dy++) {
                    out.add(corner.offset(dx, dy, dz));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 動き (グループごと)
    public enum Kind {
        CRUMBLE, BURST
    }

    // 試験・記録用: 罠の動きの履歴 (起動した tick・当たった tick)
    public record Event(String room, int trap, Kind kind, long started, long impact) {
    }

    private static final List<Event> LOG = new ArrayList<>();

    public static List<Event> log() {
        return LOG;
    }

    public static void clearLog() {
        LOG.clear();
    }

    private static final class Trap {
        final BlockPos at;
        final Kind kind;
        final int index;
        int standing;
        long cooldownUntil;
        @Nullable
        Telegraph telegraph;
        long startedAt;

        Trap(BlockPos at, Kind kind, int index) {
            this.at = at;
            this.kind = kind;
            this.index = index;
        }
    }

    // グループごと・部屋ごとの、いまの戦いのメモ (保存しない)
    private static final class State {
        final Room room;
        final UUID group;
        final List<Trap> traps = new ArrayList<>();
        final Layout layout;
        int peak = 1;
        long lastIn;
        int tierShown = -1;
        boolean hinted;

        State(Room room, UUID group, Layout layout, long now) {
            this.room = room;
            this.group = group;
            this.layout = layout;
            this.lastIn = now;
            for (int i = 0; i < layout.traps().size(); i++) {
                traps.add(new Trap(layout.traps().get(i), i % 2 == 0 ? Kind.CRUMBLE : Kind.BURST, i));
            }
        }
    }

    private static final Map<String, State> STATES = new HashMap<>();
    // 置き場所が空だった部屋を、しばらく調べ直さない (毎回の走査をしない)
    private static final Map<String, Long> EMPTY_UNTIL = new HashMap<>();
    // 5 tick ごとの走査で、その間隔 (tick)
    public static final int STEP = 5;
    // 部屋にそのグループの人がいなくなってから、状態を捨てるまで
    private static final int LEAVE_TICKS = 300;

    private static String key(Room r, UUID g) {
        return r.id() + "/" + g;
    }

    public static void clearAll() {
        STATES.clear();
        EMPTY_UNTIL.clear();
    }

    // 部屋の置き場所 (なければ世界を見て決めて置く)。人がいて、床のある部屋だけ
    @Nullable
    static Layout ensureLayout(ServerLevel level, Room r, long now) {
        RoomPlayData data = RoomPlayData.get(level);
        Layout saved = data.get(r.id());
        if (saved != null) {
            if (stillThere(level, r, saved)) {
                return saved;
            }
            // 建て直されていた (板の芯・柱が消えている): 置き直す
            data.remove(r.id());
        }
        Long until = EMPTY_UNTIL.get(r.id());
        if (until != null && now < until) {
            return null;
        }
        Layout l = layout(r, p -> trapSiteOk(level, p), p -> pillarSiteOk(level, p));
        if (l.isEmpty()) {
            EMPTY_UNTIL.put(r.id(), now + 600L);
            return null;
        }
        placeBlocks(level, r, l);
        data.put(r.id(), l);
        return l;
    }

    private static boolean stillThere(ServerLevel level, Room r, Layout l) {
        if (!l.traps().isEmpty()) {
            return level.getBlockState(l.traps().get(0)).is(r.theme().accent);
        }
        if (!l.pillars().isEmpty()) {
            BlockPos above = l.pillars().get(0).above();
            return level.getBlockState(above).is(r.theme().pillar) || GroupGates.isGate(level, above);
        }
        return false;
    }

    // 一回りの動き。level の room に、いる人 (グループごと) と、そのグループの敵を見て、罠と柱を動かす
    public static void step(ServerLevel level, Room room, long now) {
        List<ServerPlayer> inside = new ArrayList<>();
        AABB box = room.aabb();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isAlive() && !p.isSpectator() && box.contains(p.getX(), p.getY(), p.getZ())) {
                inside.add(p);
            }
        }
        Map<UUID, List<ServerPlayer>> byGroup = new HashMap<>();
        for (ServerPlayer p : inside) {
            UUID g = Groups.keyOf(p);
            if (g != null) {
                byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<UUID, List<ServerPlayer>> e : byGroup.entrySet()) {
            stepGroup(level, room, e.getKey(), e.getValue(), now);
        }
        // いなくなったグループの片付け
        STATES.values().removeIf(s -> {
            if (!s.room.equals(room) || byGroup.containsKey(s.group) || now - s.lastIn < LEAVE_TICKS) {
                return false;
            }
            release(level, s);
            return true;
        });
    }

    private static void stepGroup(ServerLevel level, Room room, UUID group, List<ServerPlayer> players, long now) {
        State s = STATES.get(key(room, group));
        if (s == null) {
            Layout l = ensureLayout(level, room, now);
            if (l == null) {
                return;
            }
            s = new State(room, group, l, now);
            STATES.put(key(room, group), s);
        }
        s.lastIn = now;
        s.peak = Math.max(s.peak, players.size());
        applyCover(level, s);
        List<Mob> foes = level.getEntitiesOfClass(Mob.class, room.aabb(),
                m -> m.isAlive() && m instanceof Enemy && (Groups.keyOf(m) == null || group.equals(Groups.keyOf(m))));
        int armed = foes.isEmpty() ? 0 : trapsArmed(s.traps.size(), s.peak);
        if (armed > 0 && !s.hinted) {
            s.hinted = true;
            for (ServerPlayer p : players) {
                if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                    continue;
                }
                Notice.bar(p, ModText.hint("message.skyabyss.room_trap"));
            }
        }
        for (Trap t : s.traps) {
            boolean live = t.index < armed;
            if (t.telegraph != null) {
                if (t.telegraph.isDone()) {
                    t.telegraph = null;
                    t.cooldownUntil = now + COOLDOWN_TICKS;
                }
                continue;
            }
            if (!live || now < t.cooldownUntil) {
                t.standing = 0;
                continue;
            }
            if (now % 20 < STEP) {
                BlockPos sp = jp.revati.skyabyss.trial.TrialFrame.shift(t.at);
                level.sendParticles(room.theme().spark, sp.getX() + 0.5, sp.getY() + 1.1, sp.getZ() + 0.5, 2, 0.9, 0.05, 0.9, 0.01);
            }
            if (anyoneOn(level, t, s, players, foes)) {
                t.standing += STEP;
            } else {
                t.standing = 0;
            }
            if (t.standing >= TRIGGER_TICKS) {
                trigger(level, s, t, now);
            }
        }
    }

    private static boolean onPlate(Trap t, net.minecraft.world.entity.LivingEntity e) {
        return e.onGround() && Math.abs(e.getY() - (t.at.getY() + 1)) < 1.0
                && Math.hypot(e.getX() - t.at.getX() - 0.5, e.getZ() - t.at.getZ() - 0.5) <= TRIGGER_R;
    }

    private static boolean anyoneOn(ServerLevel level, Trap t, State s, List<ServerPlayer> players, List<Mob> foes) {
        for (ServerPlayer p : players) {
            if (onPlate(t, p)) {
                return true;
            }
        }
        for (Mob m : foes) {
            if (onPlate(t, m)) {
                return true;
            }
        }
        return false;
    }

    private static void trigger(ServerLevel level, State s, Trap t, long now) {
        // 区画の写しの部屋 (TrialFrame。TrialWindow の中) では、予告と当たりを写しの座標に出す (予告は決まりの外の tick で動くので)
        BlockPos at = jp.revati.skyabyss.trial.TrialFrame.shift(t.at);
        double cx = at.getX() + 0.5;
        double cz = at.getZ() + 0.5;
        double floor = at.getY() + 1;
        // ほかの傷のある予告が、この円に重なって当たる前後 (当たる瞬間の 10 tick 前から 10 tick 後) は始めない (傷が重ならない公平さ)。
        // 始められなければ、次の走査で試す (板の上の人・敵はそのまま数え続ける)
        for (int ahead = WARN_TICKS - SPACING_TICKS; ahead <= WARN_TICKS + SPACING_TICKS; ahead++) {
            for (double[] d : new double[][] {{0, 0}, {TRAP_R * 0.7, 0}, {-TRAP_R * 0.7, 0}, {0, TRAP_R * 0.7}, {0, -TRAP_R * 0.7}}) {
                if (Telegraph.dangerAt(level, cx + d[0], floor, cz + d[1], ahead)) {
                    return;
                }
            }
        }
        UUID group = s.group;
        Kind kind = t.kind;
        Theme theme = s.room.theme();
        BlockState floorState = level.getBlockState(t.at);
        t.startedAt = now;
        t.standing = 0;
        t.telegraph = Telegraph.in(level, TelegraphShape.circle(cx, cz, TRAP_R)).floor(floor).reach(2.5).jumpClear(0).warn(WARN_TICKS).danger()
                .victims(p -> p.isAlive() && !p.isSpectator() && group.equals(Groups.keyOf(p)))
                .startSound(kind == Kind.CRUMBLE ? SoundEvents.DEEPSLATE_BREAK : SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.6f, 0.7f)
                .onHit((lv, p) -> hitPlayer(lv, p, kind))
                .onImpact(lv -> impact(lv, group, theme, kind, cx, cz, floor, floorState, t, now)).start();
    }

    private static void hitPlayer(ServerLevel level, ServerPlayer p, Kind kind) {
        float dmg = Math.min(PLAYER_DAMAGE, p.getHealth() - 1f);
        if (dmg > 0) {
            p.hurt(level.damageSources().generic(), dmg);
        }
        if (kind == Kind.CRUMBLE) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true));
        } else {
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.7, 0));
            p.hurtMarked = true;
        }
    }

    private static void impact(ServerLevel level, UUID group, Theme theme, Kind kind, double cx, double cz, double floor, BlockState floorState,
            Trap t, long started) {
        LOG.add(new Event(t.at.toShortString(), t.index, kind, started, started + WARN_TICKS));
        level.playSound(null, cx, floor, cz, kind == Kind.CRUMBLE ? SoundEvents.GENERIC_EXPLODE.value() : theme.sound, SoundSource.HOSTILE, 1.2f,
                kind == Kind.CRUMBLE ? 0.9f : 1.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floorState), cx, floor + 0.2, cz, 40, 1.1, 0.2, 1.1, 0.12);
        level.sendParticles(theme.spark, cx, floor + 0.5, cz, 24, 1.0, 0.6, 1.0, 0.05);
        AABB zone = new AABB(cx - TRAP_R, floor - 1.5, cz - TRAP_R, cx + TRAP_R, floor + 2.5, cz + TRAP_R);
        for (Mob m : level.getEntitiesOfClass(Mob.class, zone, m -> m.isAlive() && m instanceof Enemy
                && (Groups.keyOf(m) == null || group.equals(Groups.keyOf(m))))) {
            if (Math.hypot(m.getX() - cx, m.getZ() - cz) > TRAP_R) {
                continue;
            }
            float dmg = Math.max(6f, Math.min(24f, m.getMaxHealth() * 0.25f));
            m.hurt(level.damageSources().generic(), dmg);
            if (kind == Kind.CRUMBLE) {
                // 崩れた床に足を取られ、しばらく動けない
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4, false, true));
            } else {
                double dx = m.getX() - cx;
                double dz = m.getZ() - cz;
                double len = Math.max(0.3, Math.hypot(dx, dz));
                m.setDeltaMovement(m.getDeltaMovement().add(dx / len * 0.5, 1.0, dz / len * 0.5));
                m.hurtMarked = true;
            }
        }
    }

    // 人数に合わせて、そのグループにだけ柱の段 1・2 を立てる・外す (人や敵が入っている柱は、空くまで立てない)
    private static void applyCover(ServerLevel level, State s) {
        int tier = pillarTierFor(s.peak);
        if (tier == s.tierShown) {
            return;
        }
        boolean done = true;
        for (int t = 1; t <= 2; t++) {
            List<BlockPos> cells = new ArrayList<>();
            for (int i = 0; i < s.layout.pillars().size(); i++) {
                if (pillarTier(i, s.layout.pillars().size()) == t) {
                    cells.addAll(pillarCells(s.layout.pillars().get(i)));
                }
            }
            if (t <= tier) {
                if (!GroupGates.isOpen(level, s.group, cells) || cells.isEmpty()) {
                    continue;
                }
                if (anyoneIn(level, cells)) {
                    done = false;
                    continue;
                }
                GroupGates.seal(level, s.group, cells);
            } else {
                GroupGates.release(level, s.group, cells);
            }
        }
        if (done) {
            s.tierShown = tier;
        }
    }

    private static boolean anyoneIn(ServerLevel level, List<BlockPos> cells) {
        for (BlockPos c : cells) {
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(c).inflate(0.4), e -> e.isAlive()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void release(ServerLevel level, State s) {
        List<BlockPos> all = new ArrayList<>();
        for (BlockPos p : s.layout.pillars()) {
            all.addAll(pillarCells(p));
        }
        GroupGates.release(level, s.group, all);
        for (Trap t : s.traps) {
            if (t.telegraph != null) {
                t.telegraph.cancel();
            }
        }
    }

    // 試験用: 人を指定して一つのグループを動かす (パーティを作らずに、人数だけを変えて試す)
    public static void stepGroupForTest(ServerLevel level, Room room, UUID group, List<ServerPlayer> players, long now) {
        stepGroup(level, room, group, players, now);
    }

    // 試験用: 予告を出している罠の数
    public static int activeTraps(Room room, UUID group) {
        State s = STATES.get(key(room, group));
        int n = 0;
        if (s != null) {
            for (Trap t : s.traps) {
                if (t.telegraph != null) {
                    n++;
                }
            }
        }
        return n;
    }

    // 試験用: 部屋の状態 (働いている罠の数・立っている柱の段) を読む
    @Nullable
    public static int[] probeState(Room room, UUID group) {
        State s = STATES.get(key(room, group));
        if (s == null) {
            return null;
        }
        return new int[] {s.peak, s.tierShown, s.traps.size()};
    }
}
