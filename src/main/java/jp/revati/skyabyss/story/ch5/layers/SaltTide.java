package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 塩の層の「満ち引きの塩の湖」(docs/design/36 の 8 章)。器と橋と棚は tools/abyss/layer_salt.py。
//   水の高さは深淵の潮 (ワールドの時間だけで決まる。再起動しても同じ) で、引き潮 (印 tide_salt_low) と満ち潮 (印 tide_salt_high) のあいだを上下する。
//   水の列は設計図の「水」の所だけ。一 tick に一列ずつ動かす。人が深淵にいて、湖の塊が読み込まれているときだけ動く。
//   満ち潮では、重さの術を使っている人は湖の中で浮いて、水面より高い棚へ上がれる (それ以外は届かない)。
//   満ち潮の湖では、潮だまりの手 (tide_hands) が水の底から人の足をつかみに来る。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SaltTide {
    public static final long PERIOD = 2400;          // 潮の周期 (tick。二分)
    static final double LAKE_R = 11;                 // 湖の内の半径 (器の内)
    static final double LIFT = 0.28;                 // 重さの術で浮く速さ (一 tick の上り)
    static final int HANDS_EVERY = 160;              // 満ち潮の湖で、一人に手が来る間隔
    static final int FORM = 1;

    // 水の列: 高さ y → その高さの水の所 (設計図の水だけ)。最初に一度だけ作る
    private static Map<Integer, List<BlockPos>> rows;
    // いま水のある一番上の高さ (null = まだ合わせていない)
    private static Integer shown;

    private SaltTide() {
    }

    // ---------------------------------------------------------------- 潮の決まり (時間だけで決まる)

    // 時間 t の水の高さ。周期の始めが引き潮、半分で満ち潮 (余弦で滑らかに)
    public static int levelAt(long t) {
        int low = lowLevel();
        int high = highLevel();
        double phase = Math.floorMod(t, PERIOD) / (double) PERIOD;
        double amp = (1 - Math.cos(2 * Math.PI * phase)) / 2;
        return low + (int) Math.round((high - low) * amp);
    }

    public static int lowLevel() {
        BlockPos p = AbyssBuild.mark("tide_salt_low");
        return p == null ? 0 : p.getY();
    }

    public static int highLevel() {
        BlockPos p = AbyssBuild.mark("tide_salt_high");
        return p == null ? 0 : p.getY();
    }

    // 重さの術の浮き (上へ動く速さ。術を使っていなければ 0)
    public static double lift(double relief) {
        return relief <= 0 ? 0 : LIFT * Math.min(1, relief * 2);
    }

    // 満ち潮か (水面が高い方の三分の一より上)
    public static boolean highTide(int level) {
        int low = lowLevel();
        int high = highLevel();
        return level >= high - Math.max(1, (high - low) / 3);
    }

    // ---------------------------------------------------------------- 水の列

    static synchronized Map<Integer, List<BlockPos>> rows() {
        if (rows == null) {
            Map<Integer, List<BlockPos>> m = new TreeMap<>();
            for (Blueprint.Cell c : Blueprint.get(AbyssBuild.BLUEPRINT).cells) {
                if (c.state().is(Blocks.WATER)) {
                    m.computeIfAbsent(c.y(), k -> new ArrayList<>()).add(new BlockPos(c.x(), c.y(), c.z()));
                }
            }
            rows = m;
        }
        return rows;
    }

    // 湖の塊が全部読み込まれているか (一部だけ読み込まれていると、列がずれるので待つ)
    static boolean loaded(ServerLevel level) {
        for (List<BlockPos> r : rows().values()) {
            for (BlockPos p : r) {
                if (!level.hasChunkAt(p)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 高さ y の列を水にする (water) または空気にする。全部読み込まれていることが前提
    static void setRow(ServerLevel level, int y, boolean water) {
        List<BlockPos> r = rows().get(y);
        if (r == null) {
            return;
        }
        for (BlockPos p : r) {
            level.setBlock(p, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // 水の高さを一気に合わせる (自己テスト・最初の一度)
    public static void syncTo(ServerLevel level, int target) {
        for (int y : rows().keySet()) {
            setRow(level, y, y <= target);
        }
        shown = target;
    }

    // 高さ y の水の一つ (自己テスト・確かめ用)
    public static BlockPos firstRowCell(int y) {
        List<BlockPos> r = rows().get(y);
        return r == null || r.isEmpty() ? null : r.get(0);
    }

    public static int shownLevel() {
        return shown == null ? -1 : shown;
    }

    // ---------------------------------------------------------------- 毎 tick

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!AbyssBuild.built(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !loaded(level)) {
            return;
        }
        int target = levelAt(level.getGameTime());
        if (shown == null) {
            syncTo(level, target);
        } else if (shown < target) {
            setRow(level, shown + 1, true);
            shown = shown + 1;
            if (shown == highLevel()) {
                LayerNotes.near(level, AbyssBuild.mark("lake_salt"), 60, "message.skyabyss.abyss_layers.tide_high");
            }
        } else if (shown > target) {
            setRow(level, shown, false);
            shown = shown - 1;
            if (shown == lowLevel()) {
                LayerNotes.near(level, AbyssBuild.mark("lake_salt"), 60, "message.skyabyss.abyss_layers.tide_low");
            }
        }
        boolean high = highTide(shown);
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || !inLake(p)) {
                continue;
            }
            // 浮き: 満ち潮で、重さの術を使っている人だけ水面の上へ上がれる
            if (high && p.isInWater()) {
                double relief = AbyssArts.weightRelief(p);
                double up = lift(relief);
                if (up > 0) {
                    p.setDeltaMovement(p.getDeltaMovement().x, Math.max(p.getDeltaMovement().y, up), p.getDeltaMovement().z);
                    p.fallDistance = 0;
                    p.hurtMarked = true;
                }
            }
            // 潮だまりの手: 満ち潮の湖で、足もとの底から
            if (high && (level.getGameTime() + p.getId()) % HANDS_EVERY == 0) {
                BlockPos floor = AbyssBuild.mark("lake_salt");
                if (floor != null) {
                    AbyssMobSpawner.spawnAt(level, "tide_hands", p.getX(), floor.getY(), p.getZ(), FORM);
                }
            }
        }
    }

    // 湖の中 (器の内の半径の中。底から満ち潮の水面の上の三つまで)
    static boolean inLake(ServerPlayer p) {
        BlockPos floor = AbyssBuild.mark("lake_salt");
        if (floor == null) {
            return false;
        }
        double dx = p.getX() - floor.getX(), dz = p.getZ() - floor.getZ();
        return dx * dx + dz * dz <= LAKE_R * LAKE_R && p.getY() >= floor.getY() && p.getY() <= highLevel() + 3;
    }
}
