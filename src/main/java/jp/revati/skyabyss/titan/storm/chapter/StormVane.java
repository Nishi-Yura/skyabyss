package jp.revati.skyabyss.titan.storm.chapter;

import jp.revati.skyabyss.util.Players;

import java.util.LinkedHashSet;
import java.util.Set;

import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 風見の丘の矢 (三章の小要素。docs/design/12 の 12.3)。石の台に立つ銅の針の柱の頭に、横向きの避雷針 4 本 (矢) と鉄格子 1 つ (尾) を付け、
// 巨神 (雷翼) が地上にいればその方角を、いなければ「風に逆らう向き」を指す。三章の人が近く (96 ブロック) にいる間、5 秒おきに向きを直す。
// 風の向きは日ごとに決まる (windAngle。八方位のどれか)。風見は風に逆らうので、巨神がいない日は風の逆を指す。
// 壊さない: 矢の位置にある避雷針・鉄格子だけを置き換える (人が置いたブロックは触らない)。
public final class StormVane {
    public static final String ID = "storm_vane";
    // 矢の長さ (柱の頭から)
    static final int ARM = 4;
    static final int UPDATE_TICKS = 100;
    static final double NEAR = 96;
    static final int FLAGS = 2 | 16;

    private StormVane() {
    }

    // ---------------------------------------------------------------- 向き
    // 風の向き (ラジアン。x が東、z が南。atan2(dz, dx))。ゲーム内の日ごとに八方位のどれか
    public static double windAngle(long day) {
        return Math.floorMod(day * 5L + 3L, 8L) * Math.PI / 4.0;
    }

    // 巨神がいないときの矢: 風に逆らう
    public static double defaultBearing(long day) {
        return windAngle(day) + Math.PI;
    }

    public static double bearingTo(BlockPos from, Vec3 target) {
        return Math.atan2(target.z - (from.getZ() + 0.5), target.x - (from.getX() + 0.5));
    }

    // 二つの向きの差 (0〜π)
    public static double diff(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2);
        return d > Math.PI ? Math.PI * 2 - d : d;
    }

    // ---------------------------------------------------------------- 矢を立てる
    static boolean ours(BlockState s) {
        return s.is(Blocks.LIGHTNING_ROD) || s.is(Blocks.IRON_BARS);
    }

    static Direction cardinal(double ux, double uz) {
        if (Math.abs(ux) >= Math.abs(uz)) {
            return ux >= 0 ? Direction.EAST : Direction.WEST;
        }
        return uz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // top (柱の頭の位置) のまわりの矢を、angle の向きに作り直す。変えたら true
    public static boolean orient(ServerLevel level, BlockPos top, double angle) {
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);
        Set<Long> arm = new LinkedHashSet<>();
        for (int k = 1; k <= ARM; k++) {
            long cx = Math.round(k * ux);
            long cz = Math.round(k * uz);
            if (cx != 0 || cz != 0) {
                arm.add(BlockPos.asLong((int) cx, 0, (int) cz));
            }
        }
        Direction facing = cardinal(ux, uz);
        BlockState rod = Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, facing);
        long tail = BlockPos.asLong((int) Math.round(-ux), 0, (int) Math.round(-uz));
        boolean changed = false;
        // 柱の頭 (縦の針)
        BlockState head = level.getBlockState(top);
        if (head.isAir() || head.is(Blocks.LIGHTNING_ROD)) {
            BlockState up = Blocks.LIGHTNING_ROD.defaultBlockState();
            if (head != up) {
                level.setBlock(top, up, FLAGS);
                changed = true;
            }
        }
        for (int dx = -ARM; dx <= ARM; dx++) {
            for (int dz = -ARM; dz <= ARM; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos at = top.offset(dx, 0, dz);
                long key = BlockPos.asLong(dx, 0, dz);
                BlockState now = level.getBlockState(at);
                BlockState want = arm.contains(key) ? rod : (key == tail && !arm.contains(key) ? Blocks.IRON_BARS.defaultBlockState() : null);
                if (want == null) {
                    if (ours(now)) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
                        changed = true;
                    }
                    continue;
                }
                if (now.isAir() || ours(now)) {
                    if (want.is(Blocks.IRON_BARS) ? !now.is(Blocks.IRON_BARS) : !now.equals(want)) {
                        level.setBlock(at, want, FLAGS);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    // 矢 (柱の頭と横の針・尾) を全部片づける。重複を掃除するとき用
    static void clear(ServerLevel level, BlockPos top) {
        for (int dx = -ARM; dx <= ARM; dx++) {
            for (int dz = -ARM; dz <= ARM; dz++) {
                BlockPos at = top.offset(dx, 0, dz);
                if (level.hasChunkAt(at) && ours(level.getBlockState(at))) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    // 立っている矢の向き (いちばん遠い横向きの針の方角)。矢がなければ NaN (テスト・確認用)
    public static double readBearing(ServerLevel level, BlockPos top) {
        double best = -1;
        double angle = Double.NaN;
        for (int dx = -ARM; dx <= ARM; dx++) {
            for (int dz = -ARM; dz <= ARM; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockState(top.offset(dx, 0, dz)).is(Blocks.LIGHTNING_ROD)) {
                    double d = dx * dx + dz * dz;
                    if (d > best) {
                        best = d;
                        angle = Math.atan2(dz, dx);
                    }
                }
            }
        }
        return angle;
    }

    // 今の矢の向き: 巨神がいればその方角、いなければ風に逆らう向き
    static double target(ServerLevel level, BlockPos top) {
        StormWingEntity titan = StormKeeper.KEEPER.current(level);
        return titan != null ? bearingTo(top, titan.position()) : defaultBearing(level.getDayTime() / 24000L);
    }

    // ---------------------------------------------------------------- 小要素の仕上げ (ChapterSites の Post)
    static void finish(SiteJob job) {
        BlockPos top = job.mark("top");
        if (top == null) {
            return;
        }
        job.entry.extra("top", top);
        orient(job.level, top, target(job.level, top));
    }

    // 5 秒おき: 三章の人が近くにいる風見の矢を直す
    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        java.util.List<ServerPlayer> storm = new java.util.ArrayList<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.level().dimension() == Level.OVERWORLD && p.isAlive() && Chapters.of(p) == Chapters.STORM) {
                storm.add(p);
            }
        }
        if (storm.isEmpty()) {
            return;
        }
        for (SiteData.Entry e : SiteData.get(level).allEntries()) {
            if (!ID.equals(e.siteId) || !e.built) {
                continue;
            }
            BlockPos top = e.extra("top");
            if (top == null || !level.hasChunkAt(top)) {
                continue;
            }
            for (ServerPlayer p : storm) {
                if (Math.hypot(p.getX() - top.getX(), p.getZ() - top.getZ()) <= NEAR) {
                    orient(level, top, target(level, top));
                    break;
                }
            }
        }
    }
}
