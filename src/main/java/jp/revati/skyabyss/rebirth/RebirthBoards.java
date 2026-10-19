package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.HubTablets;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

// 鼓動の間の順位の石 (C11) と飾り板 (C21): 立てる・光らせる・開く中身を作る (サーバー側)。
// 置き場は試練の碑 (HubTablets) と重ならない開いた床から決まった手順で二つ選ぶ (背の作りが変わっても追う)。人がいるあいだ 10 秒に一度見て、無ければ立てる
public final class RebirthBoards {
    // 記録が更新されてから光る長さ (tick)
    public static final long FRESH_TICKS = 12_000L;
    public static final int LIST_SIZE = 10;

    private record Spots(BlockPos stone, BlockPos board, Direction stoneFacing, Direction boardFacing) {
    }

    private static final Map<String, Spots> SPOTS = new HashMap<>();

    private RebirthBoards() {
    }

    public static void clearCache() {
        SPOTS.clear();
    }

    // ---------------------------------------------------------------- 開く中身
    public static void openLeaderboard(ServerPlayer player) {
        List<List<RebirthNet.Line>> lists = new ArrayList<>();
        for (RebirthLeaderboard.Metric m : RebirthLeaderboard.Metric.values()) {
            List<RebirthNet.Line> lines = new ArrayList<>();
            for (RebirthLeaderboard.Row r : Rebirths.top(player.server, m, LIST_SIZE)) {
                long value = switch (m) {
                    case REBIRTHS -> r.rebirths();
                    case FASTEST -> r.fastestLife();
                    case FEWEST_DEATHS -> r.fewestDeaths();
                };
                lines.add(new RebirthNet.Line(r.name(), r.rebirths(), value));
            }
            lists.add(lines);
        }
        PacketDistributor.sendToPlayer(player, new RebirthNet.BoardPayload(0, RebirthView.data(player).hidden(), lists, List.of()));
    }

    public static List<RebirthNet.RecordLine> recordLines(MinecraftServer server, @Nullable String here) {
        RebirthStats stats = RebirthStats.get(server);
        long now = server.overworld().getGameTime();
        List<RebirthNet.RecordLine> lines = new ArrayList<>();
        for (String titan : Titans.all()) {
            RebirthStats.Record r = stats.record(titan);
            boolean fresh = r != null && fresh(now, r.at());
            lines.add(new RebirthNet.RecordLine(Progression.titanName(titan), r == null ? "" : r.name(), r == null ? -1 : r.ticks(), fresh, titan.equals(here)));
        }
        return lines;
    }

    public static void openRecords(ServerPlayer player) {
        String here = Progression.titanOfBack(player.level().dimension());
        PacketDistributor.sendToPlayer(player, new RebirthNet.BoardPayload(1, false, List.of(), recordLines(player.server, here)));
    }

    public static boolean fresh(long now, long at) {
        return now - at >= 0 && now - at < FRESH_TICKS;
    }

    // ---------------------------------------------------------------- 立てる・光らせる
    // 人がいるあいだ呼ぶ (TrialHub から 10 秒に一度)。立てられたら true
    public static boolean install(ServerLevel back, String titan) {
        if (!HubTablets.enabled(titan)) {
            return false;
        }
        Spots spots = SPOTS.get(titan);
        if (spots == null) {
            spots = find(back, titan);
            if (spots == null) {
                return false;
            }
            SPOTS.put(titan, spots);
        }
        if (!back.hasChunkAt(spots.stone) || !back.hasChunkAt(spots.board)) {
            return false;
        }
        if (!back.getBlockState(spots.stone).is(RebirthSetup.STONE.get())) {
            back.setBlock(spots.stone, RebirthSetup.STONE.get().defaultBlockState(), 3);
        }
        boolean fresh = isFresh(back.getServer(), titan);
        BlockState want = RebirthSetup.BOARD.get().defaultBlockState().setValue(RecordBoardBlock.FACING, spots.boardFacing).setValue(RecordBoardBlock.FRESH, fresh);
        BlockState cur = back.getBlockState(spots.board);
        if (!cur.is(RebirthSetup.BOARD.get()) || cur.getValue(RecordBoardBlock.FRESH) != fresh) {
            back.setBlock(spots.board, want, 3);
        }
        return true;
    }

    // 記録が更新されたとき: 立っている板をすぐ光らせる
    public static void refresh(MinecraftServer server, String titan) {
        var dim = Titans.backOf(titan);
        ServerLevel back = dim == null ? null : server.getLevel(dim);
        if (back != null) {
            install(back, titan);
        }
    }

    public static boolean isFresh(MinecraftServer server, String titan) {
        RebirthStats.Record r = RebirthStats.get(server).record(titan);
        return r != null && fresh(server.overworld().getGameTime(), r.at());
    }

    @Nullable
    public static BlockPos stoneOf(String titan) {
        Spots s = SPOTS.get(titan);
        return s == null ? null : s.stone;
    }

    @Nullable
    public static BlockPos boardOf(String titan) {
        Spots s = SPOTS.get(titan);
        return s == null ? null : s.board;
    }

    // 休み場のまわり (半径 5〜9) を、碑の角度 (12 度おき 0 から) とずらした 6 度から 12 度おきに探し、碑・互いに 4 マス以上離れた二つ
    @Nullable
    private static Spots find(ServerLevel back, String titan) {
        BlockPos hub = ReturnBeacons.hubOf(titan);
        if (hub == null || !back.hasChunkAt(hub)) {
            return null;
        }
        List<HubTablets.Spot> tablets = HubTablets.spots(back, titan);
        if (tablets.isEmpty()) {
            return null;
        }
        int[] radii = {7, 6, 8, 5, 9};
        List<BlockPos> chosen = new ArrayList<>();
        for (int a = 186; a < 186 + 360 && chosen.size() < 2; a += 12) {
            for (int r : radii) {
                double rad = Math.toRadians(a);
                BlockPos p = standing(back, hub.getX() + (int) Math.round(Math.cos(rad) * r), hub.getY(), hub.getZ() + (int) Math.round(Math.sin(rad) * r));
                if (p == null || near(p, tablets, chosen)) {
                    continue;
                }
                chosen.add(p);
                break;
            }
        }
        if (chosen.size() < 2) {
            return null;
        }
        return new Spots(chosen.get(0), chosen.get(1), facing(chosen.get(0), hub), facing(chosen.get(1), hub));
    }

    private static boolean near(BlockPos p, List<HubTablets.Spot> tablets, List<BlockPos> chosen) {
        for (HubTablets.Spot t : tablets) {
            if (t.pos().distSqr(p) < 16) {
                return true;
            }
        }
        for (BlockPos c : chosen) {
            if (c.distSqr(p) < 16) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos standing(ServerLevel back, int x, int y0, int z) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos p = new BlockPos(x, y0 + dy * sign, z);
                if (isFree(back, p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean isFree(ServerLevel back, BlockPos p) {
        BlockState at = back.getBlockState(p);
        boolean self = at.is(RebirthSetup.STONE.get()) || at.is(RebirthSetup.BOARD.get());
        if (!(self || at.isAir()) || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()) {
            return false;
        }
        if (!back.getBlockState(p.below()).isFaceSturdy(back, p.below(), Direction.UP)) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = back.getBlockState(p.relative(d));
            if (!s.isAir() && !s.is(RebirthSetup.STONE.get()) && !s.is(RebirthSetup.BOARD.get())) {
                return false;
            }
        }
        return true;
    }

    private static Direction facing(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }
}
