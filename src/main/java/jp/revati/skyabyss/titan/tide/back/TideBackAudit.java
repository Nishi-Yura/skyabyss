package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BackReach.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 潮殻の背の「詰み監査」の指定 (docs/design/08 の詰み監査)。干潮 (水盤が空) で、戸は開いているものとして調べる
public final class TideBackAudit {
    private TideBackAudit() {
    }

    private static Box box(Area a) {
        return new Box(a.x1() - 1, a.floor() - 2, a.z1() - 1, a.x2() + 1, a.floor() + a.height() + 1, a.z2() + 1);
    }

    public static BackReach.Spec spec() {
        BackReach.Spec s = new BackReach.Spec("turtle", new Box(-115, 38, -135, 115, 190, 125));
        s.longFallIsExit = true;
        s.entrances.add(ARRIVAL);
        s.exits.add(ARRIVAL);
        // 島から海に落ちて、甲羅の外の水に 5 秒いると、潮に押し戻される (TideTravel)
        s.exitNode = p -> shellS2(p.getX() + 0.5, p.getZ() + 0.5) > 1.02 && p.getY() < SEA + 1;
        for (Rest r : RESTS) {
            s.goals.put("rest " + r.id(), r.spawn());
        }
        s.goals.put("clam", new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1] - 2));
        s.goals.put("altar", new BlockPos(ALTAR[0] + 1, ALTAR_TOP + 1, ALTAR[1] + 1));
        s.goals.put("east gallery", new BlockPos(10, HUB_LEDGE + 1, -2));
        s.goals.put("hub floor", new BlockPos(5, HUB_FLOOR + 1, 5));
        s.goals.put("flats", new BlockPos(0, FLATS_FLOOR + 1, -85 + FLATS_DZ));
        s.goals.put("front stone", STONE_FRONT.offset(0, 0, 1));
        s.goals.put("west stone", STONE_WEST.offset(1, 0, 0));
        s.goals.put("beach", new BlockPos(-22, BEACH.floor() + 1, 50));
        s.goals.put("shell top", new BlockPos(TOP_STAIR_X1 + 2, shellTop(TOP_STAIR_X1 + 2, TOP_STAIR_Z1 - 4) + 1, TOP_STAIR_Z1 - 4));
        return s;
    }

    // 庭の道の形 (3 通り) × 潮見の間の配置 (4 通り) のすべてで調べる。干潮、戸は開いているものとして
    public static BackReach.Report runAll(ServerLevel level, java.util.UUID group) {
        BackReach.Report all = new BackReach.Report();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int route = 0; route < GARDEN_ROUTES.length; route++) {
            for (int east = 0; east < 4; east++) {
                TideRooms.releaseGroup(level, group);
                TideBackState.Group state = TideBackState.of(level, group);
                state.record.putInt("tide.route", route);
                state.record.putInt("tide.east", east);
                for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
                    TideField.settleNow(level, p, 0);
                }
                TideRooms.claim(level, TideRooms.GARDEN, group);
                TideRooms.claim(level, TideRooms.WATCH, group);
                TideRooms.claim(level, TideRooms.FLATS, group);
                jp.revati.skyabyss.titan.group.GroupGates.setDefault(level, TideGates.flats(), true);
                BackReach.Report r = BackReach.run(level, spec());
                String tag = "[route " + route + ", east " + east + "] ";
                for (String f : r.failures) {
                    counts.merge(f, 1, Integer::sum);
                }
                all.notes.add(tag + r.notes.get(0));
            }
        }
        // 同じ指摘は、何通りの配置で出たかを添えて一度だけ
        counts.forEach((f, n) -> all.failures.add(f + (n < GARDEN_ROUTES.length * 4 ? " (in " + n + " of " + GARDEN_ROUTES.length * 4 + " layouts)" : "")));
        return all;
    }
}
