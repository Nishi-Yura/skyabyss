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
        BackReach.Spec s = new BackReach.Spec("turtle", new Box(-190, 38, -245, 190, 230, 170));
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
        s.goals.put("east gallery", new BlockPos(22, HUB_LEDGE + 1, -4));
        s.goals.put("hub floor", new BlockPos(8, HUB_FLOOR + 1, 8));
        s.goals.put("flats", new BlockPos(0, FLATS_FLOOR + 1, -100));
        s.goals.put("front stone", STONE_FRONT.offset(0, 0, 1));
        s.goals.put("west stone", STONE_WEST.offset(1, 0, 0));
        s.goals.put("beach", new BlockPos(-30, BEACH.floor() + 1, 80));
        if (TideDeck.placed()) {
            // 試練の階 (TideDeck): 足場の戸 (砂州・浮き石・円環の床・橋。y = 床のブロック) は、三つの配置すべてを閉じた足場として歩く
            // (配置ごとの道は、セルの audit が調べる)。戸口の戸 (y が床より上) は開いているものとして通る
            s.solidGate = p -> p.getY() == TideDeck.F && p.getX() >= 0 && p.getX() <= 100 && p.getZ() >= 45 && p.getZ() <= 95
                    || p.getY() == TideDeck.G && p.getX() >= 0 && p.getX() <= 20 && p.getZ() >= 75 && p.getZ() <= 108;
            s.goals.put("deck hall", new BlockPos(40, TideDeck.F + 1, 42));
            s.goals.put("deck T02 islet", new BlockPos(12, TideDeck.F + 1, 70));
            s.goals.put("deck T02 far shore", new BlockPos(12, TideDeck.F + 1, 90));
            s.goals.put("deck T02 stone", TideDeck.T02_STONE.east());
            s.goals.put("deck T10 foyer south", new BlockPos(40, TideDeck.F + 1, 84));
            s.goals.put("deck T10 stone", TideDeck.T10_STONE.east());
            s.goals.put("deck T06 stone", TideDeck.T06_STONE.east());
            s.goals.put("deck T07 stone", TideDeck.T07_STONE.east());
            // 下の階 (P3): 階段の下の広間・各セルの石 (水路・傘の戸は開いているものとして通る)
            s.goals.put("deck2 hall", new BlockPos(40, TideDeck.G + 1, 68));
            s.goals.put("deck2 hall far", new BlockPos(86, TideDeck.G + 1, 68));
            s.goals.put("deck2 T03 islet", new BlockPos(8, TideDeck.G + 1, 92));
            s.goals.put("deck2 T03 stone", TideDeck.T03_STONE.east());
            s.goals.put("deck2 T04 shore", new BlockPos(26, TideDeck.G + 1, 113));
            s.goals.put("deck2 T04 stone", TideDeck.T04_STONE.east());
            s.goals.put("deck2 T11 altar", new BlockPos(44, TideDeck.G + 1, 105));
            s.goals.put("deck2 T11 stone", TideDeck.T11_STONE.east());
            s.goals.put("deck2 T08 bowls", new BlockPos(62, TideDeck.G + 1, 84));
            s.goals.put("deck2 T08 stone", TideDeck.T08_STONE.east());
            s.goals.put("deck2 T12 arena", new BlockPos(85, TideDeck.G + 1, 80));
            s.goals.put("deck2 T12 stone", TideDeck.T12_STONE.east());
        }
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
