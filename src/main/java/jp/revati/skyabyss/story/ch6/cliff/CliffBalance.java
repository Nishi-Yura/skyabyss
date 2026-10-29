package jp.revati.skyabyss.story.ch6.cliff;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 天秤の岩 (上弦の崖の探す場所 3。docs/design/46 の 4.4)。道の内に二枚の皿 (西 = 道の側、東 = 高い柱の側) が真ん中の柱の梁から鎖で下がる。
//   重さ: 錘は 2、皿に立つ人と敵は 1。東の皿の高さ (水平から) の目標 = 3 × (西の重さ − 東の重さ)、±6 まで。6 tick ごとに 1 ずつ動き、乗っている者も運ぶ。
//   高い柱の上 (水平から 14) の欠片へは、東の皿が上がりきって (6) から軽身で跳ぶ (残り 8)。水平からは届かない
//   錘は道の脇の山から持ち、西の皿の上で右クリックで載せる。近くに人がいなくなって 60 秒で皿は水平に戻り、錘は山へ戻る
public final class CliffBalance {
    public static final int MAX_LIFT = 6;
    public static final int PER_WEIGHT = 2;
    public static final int STEP_TICKS = 6;
    public static final int CARRY_MAX = 2;
    public static final int IDLE_RESET = 1200;
    static final double PLATE_R = 2.55;
    static final int[][] WEIGHT_SLOTS = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    // いまの皿の高さ (東の皿が水平から上がった数。西の皿は同じだけ下がる)
    private static int lift;
    private static int clock;
    private static int idle;
    private static ServerLevel builtFor;

    private CliffBalance() {
    }

    public static int lift() {
        return lift;
    }

    static BlockPos west() {
        return MoonCliff.mark("plate_w");
    }

    static BlockPos east() {
        return MoonCliff.mark("plate_e");
    }

    static int level0() {
        return MoonCliff.mark("plate_w").getY();
    }

    // 皿の石の列 (中心からの相対)
    static List<int[]> cells() {
        List<int[]> out = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= PLATE_R * PLATE_R) {
                    out.add(new int[] {dx, dz});
                }
            }
        }
        return out;
    }

    static int westY() {
        return level0() - lift;
    }

    static int eastY() {
        return level0() + lift;
    }

    // ---------------------------------------------------------------- 組む・片付ける
    public static void reset(ServerLevel level) {
        clearPlate(level, west(), level0() - MAX_LIFT - 1, level0() + MAX_LIFT + 2);
        clearPlate(level, east(), level0() - MAX_LIFT - 1, level0() + MAX_LIFT + 2);
        lift = 0;
        placePlate(level, west(), westY(), new boolean[4]);
        placePlate(level, east(), eastY(), null);
        beam(level);
        builtFor = level;
        idle = 0;
    }

    private static void clearPlate(ServerLevel level, BlockPos c, int y0, int y1) {
        for (int[] o : cells()) {
            for (int y = y0; y <= y1; y++) {
                BlockPos p = new BlockPos(c.getX() + o[0], y, c.getZ() + o[1]);
                BlockState st = level.getBlockState(p);
                if (st.getBlock() instanceof BalancePlateBlock || st.getBlock() instanceof MoonWeightBlock || st.is(Blocks.CHAIN)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 鎖 (皿の真ん中の上、梁まで)
        int top = MoonCliff.mark("pivot").getY();
        for (int y = y0; y < top; y++) {
            BlockPos p = new BlockPos(c.getX(), y, c.getZ());
            if (level.getBlockState(p).is(Blocks.CHAIN)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    // 皿を置く (y = 皿の石の高さ)。weights: 西の皿の錘の口 (null = 東)
    private static void placePlate(ServerLevel level, BlockPos c, int y, boolean[] weights) {
        for (int[] o : cells()) {
            level.setBlock(new BlockPos(c.getX() + o[0], y, c.getZ() + o[1]), CliffBlocks.BALANCE_PLATE.get().defaultBlockState(), 3);
        }
        if (weights != null) {
            for (int i = 0; i < WEIGHT_SLOTS.length; i++) {
                if (weights[i]) {
                    level.setBlock(new BlockPos(c.getX() + WEIGHT_SLOTS[i][0], y + 1, c.getZ() + WEIGHT_SLOTS[i][1]), CliffBlocks.MOON_WEIGHT.get().defaultBlockState(), 3);
                }
            }
        }
        int top = MoonCliff.mark("pivot").getY();
        for (int cy = y + 1; cy < top; cy++) {
            BlockPos p = new BlockPos(c.getX(), cy, c.getZ());
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.CHAIN.defaultBlockState(), 3);
            }
        }
    }

    // 梁 (柱の上の台から両の皿の真上へ。向きは皿の並びに合わせる)
    private static void beam(ServerLevel level) {
        BlockPos w = west();
        BlockPos e = east();
        int y = MoonCliff.mark("pivot").getY();
        boolean alongZ = Math.abs(e.getZ() - w.getZ()) >= Math.abs(e.getX() - w.getX());
        int n = Math.max(Math.abs(e.getX() - w.getX()), Math.abs(e.getZ() - w.getZ()));
        for (int i = 0; i <= n; i++) {
            BlockPos p = new BlockPos((int) Math.round(w.getX() + (e.getX() - w.getX()) * i / (double) n), y,
                    (int) Math.round(w.getZ() + (e.getZ() - w.getZ()) * i / (double) n));
            if (p.equals(MoonCliff.mark("pivot"))) {
                continue;
            }
            level.setBlock(p, Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongZ ? Direction.Axis.Z : Direction.Axis.X), 3);
        }
    }

    // ---------------------------------------------------------------- 重さ
    static boolean[] weightSlots(ServerLevel level) {
        boolean[] out = new boolean[WEIGHT_SLOTS.length];
        BlockPos c = west();
        for (int i = 0; i < WEIGHT_SLOTS.length; i++) {
            out[i] = level.getBlockState(new BlockPos(c.getX() + WEIGHT_SLOTS[i][0], westY() + 1, c.getZ() + WEIGHT_SLOTS[i][1])).getBlock() instanceof MoonWeightBlock;
        }
        return out;
    }

    public static int weights(ServerLevel level) {
        int n = 0;
        for (boolean b : weightSlots(level)) {
            n += b ? 1 : 0;
        }
        return n;
    }

    // 皿の上に立っている生き物 (足が皿の上面にある)
    static List<LivingEntity> riders(ServerLevel level, BlockPos c, int y) {
        AABB box = new AABB(c.getX() - 3, y + 0.5, c.getZ() - 3, c.getX() + 4, y + 2.6, c.getZ() + 4);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()
                && Math.hypot(e.getX() - (c.getX() + 0.5), e.getZ() - (c.getZ() + 0.5)) <= PLATE_R + 0.4 && e.getY() >= y + 0.9 && e.getY() <= y + 1.6);
    }

    public static int target(ServerLevel level) {
        int w = PER_WEIGHT * weights(level) + riders(level, west(), westY()).size();
        int e = riders(level, east(), eastY()).size();
        return Math.max(-MAX_LIFT, Math.min(MAX_LIFT, 3 * (w - e)));
    }

    // ---------------------------------------------------------------- 毎 tick (CliffTicker から。人が近くにいる時だけ動かす)
    static void tick(ServerLevel level) {
        if (builtFor != level) {
            reset(level);
        }
        boolean near = level.getNearestPlayer(MoonCliff.mark("pivot").getX(), MoonCliff.mark("pivot").getY(), MoonCliff.mark("pivot").getZ(), 48,
                false) != null;
        if (!near) {
            if (++idle >= IDLE_RESET && (lift != 0 || weights(level) > 0)) {
                reset(level);
            }
            return;
        }
        idle = 0;
        if (++clock % STEP_TICKS != 0) {
            return;
        }
        int want = target(level);
        if (want != lift) {
            step(level, Integer.signum(want - lift));
        }
    }

    // 一段動かす (dir = +1 で東が上がり西が下がる)。乗っている者も同じだけ運ぶ
    public static void step(ServerLevel level, int dir) {
        boolean[] slots = weightSlots(level);
        List<LivingEntity> onW = riders(level, west(), westY());
        List<LivingEntity> onE = riders(level, east(), eastY());
        clearPlate(level, west(), westY(), westY() + 1);
        clearPlate(level, east(), eastY(), eastY() + 1);
        lift += dir;
        placePlate(level, west(), westY(), slots);
        placePlate(level, east(), eastY(), null);
        for (LivingEntity e : onE) {
            ride(e, dir);
        }
        for (LivingEntity e : onW) {
            ride(e, -dir);
        }
        level.playSound(null, MoonCliff.mark("pivot"), CliffBlocks.PLATE_MOVE.get(), SoundSource.BLOCKS, 1.0f, 0.8f + 0.05f * (lift + MAX_LIFT));
    }

    private static void ride(Entity e, int dy) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY() + dy, sp.getZ(), sp.getYRot(), sp.getXRot());
        } else {
            e.setPos(e.getX(), e.getY() + dy, e.getZ());
        }
        e.fallDistance = 0;
    }

    // ---------------------------------------------------------------- 錘
    public static int carried(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(CliffBlocks.MOON_WEIGHT_ITEM.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    static void pickWeight(ServerPlayer p, BlockPos pile) {
        if (carried(p) >= CARRY_MAX) {
            Notice.narrate(p, "message.skyabyss.moon.weight.full");
            return;
        }
        Quests.give(p, new ItemStack(CliffBlocks.MOON_WEIGHT_ITEM.get()));
        p.serverLevel().playSound(null, pile, CliffBlocks.WEIGHT_SET.get(), SoundSource.BLOCKS, 0.8f, 1.2f);
        Notice.narrate(p, "message.skyabyss.moon.weight.pick");
    }

    static void setWeight(ServerPlayer p, BlockPos clicked, ItemStack stack) {
        ServerLevel level = p.serverLevel();
        BlockPos w = west();
        boolean onWest = level.getBlockState(clicked).getBlock() instanceof BalancePlateBlock && clicked.getY() == westY()
                && Math.hypot(clicked.getX() - w.getX(), clicked.getZ() - w.getZ()) <= PLATE_R + 0.1;
        if (!onWest) {
            Notice.narrate(p, "message.skyabyss.moon.weight.where");
            return;
        }
        boolean[] slots = weightSlots(level);
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i]) {
                continue;
            }
            double d = Math.hypot(w.getX() + WEIGHT_SLOTS[i][0] - clicked.getX(), w.getZ() + WEIGHT_SLOTS[i][1] - clicked.getZ());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best < 0) {
            Notice.narrate(p, "message.skyabyss.moon.weight.plate_full");
            return;
        }
        BlockPos at = new BlockPos(w.getX() + WEIGHT_SLOTS[best][0], westY() + 1, w.getZ() + WEIGHT_SLOTS[best][1]);
        if (!level.getBlockState(at).isAir()) {
            return;
        }
        level.setBlock(at, CliffBlocks.MOON_WEIGHT.get().defaultBlockState(), 3);
        stack.shrink(1);
        level.playSound(null, at, CliffBlocks.WEIGHT_SET.get(), SoundSource.BLOCKS, 1.2f, 0.7f);
    }

    static void takeWeight(ServerPlayer p, BlockPos at) {
        ServerLevel level = p.serverLevel();
        if (!(level.getBlockState(at).getBlock() instanceof MoonWeightBlock)) {
            return;
        }
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        Quests.give(p, new ItemStack(CliffBlocks.MOON_WEIGHT_ITEM.get()));
        level.playSound(null, at, CliffBlocks.WEIGHT_SET.get(), SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    // 崖を離れた人の錘は山へ戻す (持ち出さない)
    static void dropCarried(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(CliffBlocks.MOON_WEIGHT_ITEM.get())) {
                s.setCount(0);
            }
        }
    }

    // 自己テスト: 錘を置く・外す
    public static void putWeightForTest(ServerLevel level, int slot) {
        BlockPos w = west();
        level.setBlock(new BlockPos(w.getX() + WEIGHT_SLOTS[slot][0], westY() + 1, w.getZ() + WEIGHT_SLOTS[slot][1]), CliffBlocks.MOON_WEIGHT.get().defaultBlockState(), 3);
    }

    public static BlockPos plateTop(boolean eastPlate) {
        BlockPos c = eastPlate ? east() : west();
        return new BlockPos(c.getX(), (eastPlate ? eastY() : westY()) + 1, c.getZ());
    }
}
