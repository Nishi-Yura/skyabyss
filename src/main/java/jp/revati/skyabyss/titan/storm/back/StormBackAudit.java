package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BackReach.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 雷翼の背の「詰み監査」の指定 (docs/design/12 の詰み監査)。戸は開いているものとして、昇り風は凪の高さ (4 段) だけで調べる (25 の 2章: どの拍でも全部の試練に行ける。固定の穴だけが 13 段)。
// 西の骨の中は風切りの羽根で滑空できるので、何段落ちてもよい。雲の下 (CLOUD_Y より下) へ落ちると嵐に投げ返される = 出口
public final class StormBackAudit {
    private StormBackAudit() {
    }

    public static BackReach.Spec spec(ServerLevel level) {
        BackReach.Spec s = new BackReach.Spec("storm", new Box(-225, 105, -145, 225, 200, 145));
        s.voidY = CLOUD_Y + 5;
        s.longFallIsExit = true;
        s.entrances.add(ARRIVAL);
        s.exits.add(ARRIVAL);
        // 床の穴・はしごを調べるのは体の中 (外の羽毛のすきまは、落ちても嵐に投げ返されるだけ)
        s.scan.add(new Box(-16, 128, 12, 16, 160, 45));
        s.scan.add(new Box(-16, 129, -20, 16, 162, 12));
        s.scan.add(new Box(-150, 138, -26, 150, 158, 2));
        s.scan.add(new Box(RIB_X1 - 2, 158, BONE_Z1 - 2, RIB_X2 + 2, RIB_TOP_Y + 8, BONE_Z2 + 2));
        s.scan.add(new Box(-12, 144, -100, 12, 160, -18));
        s.scan.add(new Box(-28, 112, -40, 22, 137, 6));
        // 西の骨の割れた床 (WEST_GAPS): わざとの穴。落ちると雲の下へ出て嵐に投げ返される
        for (int[] g : WEST_GAPS) {
            s.allowHole.add(new Box(g[0] - 1, 130, BONE_Z1, g[1] + 1, 175, BONE_Z2 + 1));
        }
        s.glide = p -> StormWest.inBone(p.getCenter()) || p.getX() < -110;
        // 昇り風: 穴の上 (中心から 1 マス以内) に立つと、穴の上の柱の空いている所から、横 3 マス以内の立てる所へ
        List<StormWest.Vent> vents = new ArrayList<>(StormWest.vents());
        vents.add(new StormWest.Vent(RAIJU_VENT, EAST_PERCH.floor() + 2 - RAIJU_VENT.getY() - 1));
        s.extra = (probe, out) -> {
            BlockPos p = probe.pos();
            for (StormWest.Vent v : vents) {
                BlockPos c = v.floor();
                if (p.getY() != c.getY() + 1 || Math.abs(p.getX() - c.getX()) > 1 || Math.abs(p.getZ() - c.getZ()) > 1) {
                    continue;
                }
                double h = v.fixedHeight() >= 0 ? v.fixedHeight() : StormBackClock.CALM_LIFT;
                int top = c.getY() + 1 + (int) Math.floor(h);
                for (int y = c.getY() + 1; y <= top; y++) {
                    if (!probe.passable(new BlockPos(p.getX(), y + 1, p.getZ()))) {
                        top = y;
                        break;
                    }
                }
                for (int y = c.getY() + 2; y <= top + 1; y++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos m = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                            if (probe.valid(m)) {
                                out.add(m);
                            }
                        }
                    }
                }
            }
        };
        for (Rest r : RESTS) {
            s.goals.put("rest " + r.id(), r.spawn());
        }
        s.goals.put("gallery", new BlockPos(0, UPPER_GALLERY_Y + 1, -17));
        s.goals.put("rib floor", new BlockPos(RIB_X2 - 1, BONE_FLOOR + 1, -17));
        s.goals.put("west stone ledge", WEST_STONE.offset(1, 0, 0));
        s.goals.put("tail hall", TAIL_CENTER);
        s.goals.put("eye hall", EYE_HALL_CENTER.offset(1, 0, 6));
        s.goals.put("raiju nest", RAIJU_NEST.offset(-6, 1, 0));
        s.goals.put("east perch", EAST_STONE.offset(-1, 0, 0));
        s.goals.put("beak room", FRONT_STONE.offset(1, 0, 0));
        s.goals.put("heart room", HEART_ROOM.offset(0, 2, 8));
        return s;
    }

    public static BackReach.Report run(ServerLevel level) {
        return BackReach.run(level, spec(level));
    }
}
