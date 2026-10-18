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
        s.entrances.add(arrival());
        s.exits.add(arrival());
        // 穴・はしごを調べるのは、体の中の部屋 (地表の背の遺跡・ひれは歩ける所だけ見る)
        for (TitanBackLayout.Box b : List.of(HUB, ENTRY, TREASURY, W0, WA, W1, WB, W2, WC, E0, E1, CE1, E3_CORRIDOR, E3, MAZE_EXIT_ROOM, HEART_REST, HEART)) {
            s.scan.add(new Box(b.x1() - 1, b.floorY() - 14, b.z1() - 1, b.x2() + 1, b.ceilingY(), b.z2() + 1));
        }
        s.scan.add(new Box(-24, FLOOR - 2, -64, 24, FLOOR + 8, 6));
        // 西の谷は、落ちたら直前の休み場へ戻される (FALL_LINE) わざとの穴。足もとは溶岩
        for (TitanBackLayout.Box pit : PITS) {
            s.allowHole.add(new Box(pit.x1() - 1, 70, pit.z1() - 1, pit.x2() + 1, pit.ceilingY(), pit.z2() + 1));
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
        return BackReach.run(level, spec());
    }
}
