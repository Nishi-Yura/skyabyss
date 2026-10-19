package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import jp.revati.skyabyss.titan.audit.BackReach;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 余燼の背の「詰み監査」の指定 (docs/design/13 の詰み監査)。戸は開いているものとして、灰の道の湖は冷え殻で渡れるものとして調べる。
// 体から灰の原へ落ちても、灰の原を歩いて見送りの石へ戻れる (灰の原は範囲に入れて歩く)。体の外へ高く落ちる (12 マス超) と倒れて戻される
public final class EmberBackAudit {
    private EmberBackAudit() {
    }

    private static BackReach.Box box(Box b, int g) {
        return new BackReach.Box(b.minX() - g, b.minY() - g, b.minZ() - g, b.maxX() + g, b.maxY() + g, b.maxZ() + g);
    }

    public static BackReach.Spec spec(ServerLevel level) {
        BackReach.Spec s = new BackReach.Spec("ember", new BackReach.Box(-165, 44, -292, 165, 240, 365));
        s.longFallIsExit = true;
        s.entrances.add(ARRIVAL.above());
        s.exits.add(ARRIVAL.above());
        s.exits.add(new BlockPos(0, PLAIN_TOP + 1, RETURN_STONE.minZ()));
        s.walkableLava = p -> p.getY() == jp.revati.skyabyss.titan.ember.back.trial.AshPath.LAVA_Y && WEST.contains(p);
        // 穴・はしごを調べる範囲: 部屋と通路 (体の外の灰の原は広いので外す)
        for (Box b : ROOMS.values()) {
            s.scan.add(box(b, 2));
        }
        s.scan.add(new BackReach.Box(-12, 66, -240, 12, 100, -82));
        s.scan.add(new BackReach.Box(-48, 78, -60, 50, 100, -8));
        s.scan.add(new BackReach.Box(-10, 116, -64, 10, 210, -44));
        s.scan.add(new BackReach.Box(-6, 134, -66, 14, 150, 34));
        s.scan.add(box(GATE_SHAFT, 2));
        s.scan.add(box(CRACK, 1));
        // 灰の道の湖: 冷え殻を渡る仕掛け。落ちたら溶岩 (倒れて休み場へ戻る)
        s.allowHole.add(new BackReach.Box(WEST.minX(), 70, 14, WEST.maxX(), 84, 105));
        for (Rest r : RESTS) {
            s.goals.put("rest " + r.name(), r.floor().above());
        }
        s.goals.put("return stone", new BlockPos(0, PLAIN_TOP + 1, RETURN_STONE.minZ()));
        s.goals.put("west door", WEST_DOOR.min());
        s.goals.put("east door", EAST_DOOR.min());
        s.goals.put("south door", SOUTH_DOOR.min());
        s.goals.put("smith door", SMITH_DOOR.min());
        s.goals.put("west stone", STONE_WEST.north());
        s.goals.put("east stone", STONE_EAST.north());
        s.goals.put("south stone", STONE_SOUTH.north());
        s.goals.put("heart door", HEART_DOOR.min());
        // 試練の階 (EmberDeck): 四つのセルの石 (戸は開いているものとして)
        s.goals.put("deck e02 stone", EmberDeck.E02_STONE.north());
        s.goals.put("deck e10 stone", EmberDeck.E10_STONE.north());
        s.goals.put("deck e06 stone", EmberDeck.E06_STONE.north());
        s.goals.put("deck e07 stone", EmberDeck.E07_STONE.north());
        s.goals.put("well top (crater)", new BlockPos(HUB_CENTER.getX() + 11, EmberBodyBuilder.crater() + 1, HUB_CENTER.getZ()));
        s.goals.put("back top", new BlockPos(0, BACK_EXIT_FLOOR + 1, EmberBodyBuilder.backTubeEnd()));
        for (Cache c : CACHES) {
            s.goals.put("cache " + c.id(), c.pos().relative(c.facing()));
        }
        return s;
    }

    public static BackReach.Report run(ServerLevel level) {
        return BackReach.run(level, spec(level));
    }
}
