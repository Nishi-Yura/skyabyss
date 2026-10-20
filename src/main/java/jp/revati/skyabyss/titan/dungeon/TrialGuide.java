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
    public static final List<String> IDS = List.of("wings", "west", "bell", "east_e1", "east_ck", "east_e3", "east_stone", "maze",
            "heart", "board", "deck", "door");

    static void tick(ServerPlayer player) {
        if (player.tickCount % 40 != 7 || player.isSpectator() || !player.isAlive() || !TitanBackTrials.isBelowSurface(player)) {
            return;
        }
        var level = player.serverLevel();
        UUID group = Groups.keyOf(player);
        BackGroupData.Record record = GroupProgress.of(level, group);
        Goal goal;
        if (WhaleDeck.placed() && !record.isHeartDefeated() && !record.isGateOpened() && onDeckSide(player.blockPosition())) {
            // 試練の階を置いたあとの、引いた試練の案内 (版 22)。引いた三つが新作のセルのとき・試練の階にいるとき
            goal = deckGoal(player, level, group, record);
        } else {
            goal = next(record, player.blockPosition());
        }
        if (goal == null) {
            return;
        }
        BlockPos at = goal.at();
        PacketDistributor.sendToPlayer(player, new TitanEndNetwork.Beam(KEY_PREFIX + goal.id(), GOLD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                goal.hx(), goal.hy(), goal.hz(), 0f, at.getY(), at.getY() + 9, 0.35f, 70));
    }

    // ---------------------------------------------------------------- 試練の階の案内 (版 22)
    // 鼓動の間・入口の通路・試練の階の中にいるか (原型の西の翼・東の翼・下の水路の中は含まない)
    static boolean onDeckSide(BlockPos p) {
        if (p.getY() < FLOOR - 10) {
            return false;
        }
        // 試練の階のセルはすべて z 4 より南で、床が y 105 より上 (原型の西・東の翼は z 5 より北)
        if (p.getZ() >= 4 && p.getY() >= WhaleDeck.Y - 14 && p.getY() <= WhaleDeck.Y + 30) {
            return true;
        }
        return HUB.contains(p) || ENTRY.contains(p) || WhaleDeck.STAIR.contains(p);
    }

    // 次の目的 (試練の階の案内)。なければ null (セルの中・全部越えたあとなど)
    // 1. 鼓動の間で、まだ板を読んでいない人 → 試練の板
    // 2. 引いた試練のうち、越えていない新作のセルがある → 階段を上って試練の階へ (階にいれば、いちばん近いセルの戸へ)
    // 3. 引いた試練が原型だけ → 今までどおり翼の入口 (next)
    private static Goal deckGoal(ServerPlayer player, net.minecraft.server.level.ServerLevel level, UUID group, BackGroupData.Record record) {
        BlockPos pos = player.blockPosition();
        var draw = jp.revati.skyabyss.trial.TrialData.current(level, group);
        List<jp.revati.skyabyss.trial.TrialCell> open = new java.util.ArrayList<>();
        boolean legacyLeft = false;
        for (String key : List.of("0", "1", "2")) {
            if (jp.revati.skyabyss.titan.group.TrialSteles.rung(level.getServer(), group, jp.revati.skyabyss.titan.group.TrialSteles.signal("whale", key))) {
                continue;
            }
            var cell = jp.revati.skyabyss.trial.TrialRegions.cellOfKey("whale", key, draw);
            if (cell == null || !cell.runtime()) {
                legacyLeft = true;
            } else {
                open.add(cell);
            }
        }
        boolean hubSide = HUB.contains(pos) || ENTRY.contains(pos) || WhaleDeck.STAIR.contains(pos);
        if (hubSide && !jp.revati.skyabyss.trial.TrialIntro.seen(player, jp.revati.skyabyss.trial.TrialIntro.BOARD_READ) && (!open.isEmpty() || legacyLeft)) {
            return new Goal("board", WhaleDeck.BOARD_HUB, 0.9f, 1f, 0.9f);
        }
        if (open.isEmpty()) {
            return legacyLeft && hubSide ? next(record, pos) : null;
        }
        // 引いた新作のセルの中にいる: セルが案内する (ここでは何も出さない)
        for (var cell : open) {
            if (cell.ports().area().contains(player.position())) {
                return null;
            }
        }
        if (hubSide) {
            BlockPos door = new BlockPos(WhaleDeck.STAIR_DOOR_X, FLOOR + 1, (WhaleDeck.STAIR_Z0 + WhaleDeck.STAIR_Z1) / 2);
            return new Goal("deck", door, 0.7f, 1.5f, 2.5f);
        }
        // 試練の階の中: いちばん近い、引いた新作のセルの戸
        BlockPos best = null;
        List<BlockPos> bestDoor = List.of();
        double bestD = Double.MAX_VALUE;
        for (var cell : open) {
            List<BlockPos> door = cell.ports().entrance();
            if (door.isEmpty()) {
                continue;
            }
            BlockPos c = door.get(door.size() / 2);
            double d = c.distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = c;
                bestDoor = door;
            }
        }
        if (best == null) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos b : bestDoor) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }
        float hx = Math.max(0.7f, (maxX - minX + 1) / 2f);
        float hz = Math.max(0.7f, (maxZ - minZ + 1) / 2f);
        return new Goal("door", best, hx, 2.5f, hz);
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
                return null;
            }
            if (!record.isHallCleared("e1")) {
                return new Goal("east_e1", E1_EXIT.get(E1_EXIT.size() / 2), 2.5f, 3f, 0.7f);
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
                return null;
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
                return null;
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
