package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BackReach.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 岩鯨の背の「詰み監査」の指定 (docs/design/02 の詰み監査)。調べる部品は titan/audit/BackReach
public final class TitanBackAudit {
    private TitanBackAudit() {
    }

    public static BackReach.Spec spec() {
        BackReach.Spec s = new BackReach.Spec("whale", new Box(-RX, 40, -RZ, RX, 175, RZ));
        s.voidY = BASE_Y - 50;
        s.entrances.add(arrival());
        s.exits.add(arrival());
        // 穴・はしごを調べるのは、体の中の部屋 (地表の背の遺跡・ひれは歩ける所だけ見る)
        for (TitanBackLayout.Box b : List.of(HUB, ENTRY, TREASURY, W0, WA, W1, WB, W2, WC, E0, E1, CE1, E3_CORRIDOR, E3, MAZE_EXIT_ROOM, HEART_REST, HEART)) {
            s.scan.add(new Box(b.x1() - 1, b.floorY() - 14, b.z1() - 1, b.x2() + 1, b.ceilingY(), b.z2() + 1));
        }
        s.scan.add(new Box(-24, FLOOR - 2, -64, 24, FLOOR + 8, 6));
        // 西の谷は、落ちたら直前の休み場へ戻される (FALL_LINE) わざとの穴。足もとは溶岩
        for (TitanBackLayout.Box pit : PITS) {
            s.resets.add(new Box(pit.x1() - 1, 70, pit.z1() - 1, pit.x2() + 1, pit.ceilingY(), pit.z2() + 1));
            // 足場の並びは谷の内側だけ。縁の一列 (戸口の岩棚・壁ぎわ) は穴の検査から外さない
            s.allowHole.add(new Box(pit.x1() + 1, 70, pit.z1() + 1, pit.x2() - 1, pit.ceilingY(), pit.z2() - 1));
        }
        s.goals.put("plaza", plaza());
        s.goals.put("forge", forge().west());
        s.goals.put("shrine", new BlockPos(0, shrineY() + 1, SHRINE_Z));
        s.goals.put("hub", HUB.center().offset(0, 0, 6));
        s.goals.put("entry", ENTRY.center());
        s.goals.put("treasury", TREASURY.center());
        s.goals.put("rest w0", W0.center().offset(2, 0, 2));
        s.goals.put("rest w1", W1.center().offset(2, 0, 2));
        s.goals.put("rest w2", W2.center().offset(2, 0, 2));
        s.goals.put("west stone ledge", new BlockPos(-14, 98, -31));
        s.goals.put("rest e0", E0.center().offset(2, 0, 2));
        s.goals.put("e1", E1.center());
        s.goals.put("rest ce1", CE1.center().offset(2, 0, 2));
        s.goals.put("e3", E3.center());
        s.goals.put("east stone", RESONANCE_STONES.get(1).offset(-1, 0, 0));
        s.goals.put("maze exit room", MAZE_EXIT_ROOM.center());
        s.goals.put("lower stone", RESONANCE_STONES.get(2).offset(-1, 0, 0));
        s.goals.put("rest heart", HEART_REST.center().offset(1, 0, 1));
        s.goals.put("heart arena", HEART.center());
        return s;
    }

    public static BackReach.Report run(ServerLevel level) {
        BackReach.Spec s = spec();
        BlockPos mz0 = TitanBackTrials.checkpointSpawns(level).get(TitanBackTrials.MAZE_ENTRY);
        if (mz0 != null) {
            s.goals.put("rest mz0 (maze entrance room)", mz0);
        }
        BlockPos mz = TitanBackTrials.checkpointSpawns(level).get(TitanBackTrials.MAZE_REST);
        if (mz != null) {
            s.goals.put("rest mz (maze mid ledge)", mz);
        }
        // 灯の台 (23): 5 つぜんぶ、水の中を泳いで台の上まで行けること (戸は開いているものとして通る)
        java.util.List<BlockPos> stands = AnemoneLights.stands(TitanBackData.get(level).mazeSeed());
        for (int i = 0; i < stands.size(); i++) {
            s.goals.put("lamp stand " + (i + 1), stands.get(i).above());
        }
        BackReach.Report rep = BackReach.run(level, s);
        if (!rep.failures.isEmpty()) {
            List<List<int[]>> courses = ParkourCourses.current(level);
            for (int c = 0; c < courses.size(); c++) {
                StringBuilder sb = new StringBuilder();
                for (int[] st : courses.get(c)) {
                    boolean ok = rep.reached.contains(new BlockPos(st[0], st[1] + 1, st[2]).asLong());
                    sb.append(st[0]).append(",").append(st[1]).append(",").append(st[2]).append(ok ? "+ " : "- ");
                }
                jp.revati.skyabyss.SkyAbyss.LOGGER.warn("REACH dbg course {}: {}", c, sb);
            }
        }
        return rep;
    }
}
