package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 岩鯨の背の「次の目的」(版 18。26 の 8.6): いま居る場所とそのグループの進みから、次にすること一つを決め、
// 目的の帯 (左上) の文と、行き先の目印 (小さな光の柱と枠。TitanEndNetwork.Beam, key "trial_obj:<id>") をその人にだけ送る。
// 帯の文は lang の objective.skyabyss.trial_<id>。目印は数秒で消えるので、40 tick ごとに送り直す
public final class TrialGuide {
    public static final String KEY_PREFIX = "trial_obj:";
    private static final int GOLD = 0xF2C14E;

    // 行き先 (id・目印の位置・枠の大きさ)
    public record Goal(String id, BlockPos at, float hx, float hy, float hz) {
    }

    private TrialGuide() {
    }

    // 目的の id の一覧 (lang の確認用)
    public static final List<String> IDS = List.of("wings", "west", "bell", "east_e1", "east_e2", "east_ck", "east_e3", "east_stone", "maze",
            "stone_back", "heart");

    static void tick(ServerPlayer player) {
        if (player.tickCount % 40 != 7 || player.isSpectator() || !player.isAlive() || !TitanBackTrials.isBelowSurface(player)) {
            return;
        }
        var level = player.serverLevel();
        UUID group = Groups.keyOf(player);
        BackGroupData.Record record = GroupProgress.of(level, group);
        Goal goal = next(record, player.blockPosition());
        if (goal == null) {
            return;
        }
        BlockPos at = goal.at();
        PacketDistributor.sendToPlayer(player, new TitanEndNetwork.Beam(KEY_PREFIX + goal.id(), GOLD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                goal.hx(), goal.hy(), goal.hz(), 0f, at.getY(), at.getY() + 9, 0.35f, 70));
    }

    // 次の目的 (純粋: 記録と位置だけから)。なければ null (心臓を倒したあとなど)
    static Goal next(BackGroupData.Record record, BlockPos pos) {
        if (record.isHeartDefeated()) {
            return null;
        }
        boolean west = record.flag(TitanBackTrials.STONE_FLAG + 0);
        boolean east = record.flag(TitanBackTrials.STONE_FLAG + 1);
        boolean lower = record.flag(TitanBackTrials.STONE_FLAG + 2);
        if (record.isGateOpened()) {
            return new Goal("heart", HEART_GATE.get(HEART_GATE.size() / 2).below(2), 3f, 3f, 0.7f);
        }
        if (inEast(pos)) {
            if (east) {
                return new Goal("stone_back", EAST_RETURN.get(EAST_RETURN.size() / 2), 0.7f, 1.5f, 1.5f);
            }
            if (!record.isHallCleared("e1")) {
                return new Goal("east_e1", E1_EXIT.get(E1_EXIT.size() / 2), 2.5f, 3f, 0.7f);
            }
            if (!record.isHallCleared("e2")) {
                return new Goal("east_e2", E2_EXIT.get(E2_EXIT.size() / 2), 2.5f, 3f, 0.7f);
            }
            if (!record.isHallCleared("ck")) {
                return new Goal("east_ck", E3_LOCK.get(E3_LOCK.size() / 2), 0.7f, 3f, 2.5f);
            }
            if (!record.isHallCleared("e3")) {
                return new Goal("east_e3", E3.center(), 2f, 2f, 2f);
            }
            return new Goal("east_stone", RESONANCE_STONES.get(1), 1f, 1.5f, 1f);
        }
        if (inWest(pos)) {
            if (west) {
                return new Goal("stone_back", WEST_RETURN.get(WEST_RETURN.size() / 2), 0.7f, 1.5f, 1.5f);
            }
            if (BELL.contains(pos) || BELL_PASS.contains(pos) || (W2.contains(pos) && !record.flag(BellTower.FLAG))) {
                if (!record.flag(BellTower.FLAG) && pos.getY() > FLOOR) {
                    return new Goal("bell", BellTower.bellPos(1), 1f, 1f, 1f);
                }
            }
            return new Goal("west", RESONANCE_STONES.get(0), 1f, 1.5f, 1f);
        }
        if (pos.getY() < FLOOR - 10) {
            if (lower) {
                return new Goal("stone_back", LOWER_RETURN.get(LOWER_RETURN.size() / 2), 0.7f, 1.5f, 1.5f);
            }
            return new Goal("maze", RESONANCE_STONES.get(2), 1f, 1.5f, 1f);
        }
        // 鼓動の間・入口の通路ほか: 鳴らしていない石のうち、いちばん近い翼の入口
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        if (!west) {
            best = W0.center();
            bestD = best.distSqr(pos);
        }
        if (!east && E0.center().distSqr(pos) < bestD) {
            best = E0.center();
            bestD = best.distSqr(pos);
        }
        if (!lower && mazeEntry().distSqr(pos) < bestD) {
            best = mazeEntry();
        }
        return best == null ? null : new Goal("wings", best, 1.2f, 1.5f, 1.2f);
    }

    private static BlockPos mazeEntry() {
        return new BlockPos(MAZE_ENTRY_ROOM.x1() + 10, FLOOR + 1, MAZE_ENTRY_ROOM.z1() - 2);
    }

    private static boolean inEast(BlockPos p) {
        return p.getX() >= 19 && p.getZ() < 5 && p.getY() >= FLOOR - 2 && p.getY() < FLOOR + 14 && p.getZ() >= -125 && p.getX() <= 60;
    }

    private static boolean inWest(BlockPos p) {
        return p.getX() <= -19 && p.getX() >= -60 && p.getZ() >= -130 && p.getZ() <= -20 && p.getY() >= FLOOR - 12 && p.getY() < FLOOR + 30;
    }
}
