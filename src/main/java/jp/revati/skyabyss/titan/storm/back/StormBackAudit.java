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
        BackReach.Spec s = new BackReach.Spec("storm", new Box(-330, 105, -190, 330, 235, 230));
        s.voidY = CLOUD_Y + 5;
        s.longFallIsExit = true;
        s.entrances.add(ARRIVAL);
        s.exits.add(ARRIVAL);
        // 床の穴・はしごを調べるのは体の中 (外の羽毛のすきまは、落ちても嵐に投げ返されるだけ)
        s.scan.add(new Box(-16, 130, 8, 16, 165, 66));
        s.scan.add(new Box(-24, 129, HUB.z1() - 3, 24, 162, HUB.z2() + 3));
        // 翼の中 (部屋と通路の空気だけ。翼の背の凹みは調べずに、建てたあとの修復 BackReach.repair が埋める)
        s.scan.add(new Box(RIB_X1 - 2, 138, BONE_Z1 - 1, -23, BONE_FLOOR + BONE_HEIGHT, BONE_Z2 + 1));
        s.scan.add(new Box(23, 138, BONE_Z1 - 3, RAIJU_NEST.getX() + 14, RAIJU_NEST_CEILING, BONE_Z2 + 3));
        s.scan.add(new Box(RIB_X1 - 2, 168, BONE_Z1 - 2, RIB_X2 + 2, RIB_TOP_Y + 8, BONE_Z2 + 2));
        s.scan.add(new Box(-20, 144, -152, 20, 172, HUB.z1() - 1));
        s.scan.add(new Box(-36, 112, -54, 24, HUB.floor(), 8));
        // 任意の翼 (26 の 9.5): 背の広場・二つの塔・尾羽の庭
        s.scan.add(new Box(-48, StormExtra.DECK_Y, 9, 48, StormExtra.TOP_Y + 9, 37));
        s.scan.add(new Box(-36, StormExtra.GARDEN_Y, StormExtra.GARDEN_Z1, 36, StormExtra.GARDEN_Y + 16, StormExtra.GARDEN_Z2));
        // 西の骨の割れた床 (WEST_GAPS): わざとの穴。落ちると雲の下へ出て嵐に投げ返される
        for (int[] g : WEST_GAPS) {
            s.allowHole.add(new Box(g[0] - 1, 130, BONE_Z1, g[1] + 1, 175, BONE_Z2 + 1));
        }
        s.glide = p -> StormWest.inBone(p.getCenter());
        // 昇り風: 穴の上 (中心から 1 マス以内) に立つと、穴の上の柱の空いている所から、横 3 マス以内の立てる所へ
        List<StormWest.Vent> vents = new ArrayList<>(StormWest.vents());
        vents.add(new StormWest.Vent(RAIJU_VENT, EAST_PERCH.floor() + 2 - RAIJU_VENT.getY() - 1));
        List<StormDeck.Hop> hops = StormDeck.placed() ? StormDeck.hops() : List.of();
        s.extra = (probe, out) -> {
            BlockPos p = probe.pos();
            // 試練の甲板: 昇り風の穴の上に立てば、次の島へ滑空して着ける (凪の昇り風 4 で余裕がある。StormDeck.glideMargin)
            for (StormDeck.Hop h : hops) {
                if (p.getY() == h.from().getY() + 1 && Math.abs(p.getX() - h.from().getX()) <= 1 && Math.abs(p.getZ() - h.from().getZ()) <= 1) {
                    for (BlockPos f : h.to().floor()) {
                        BlockPos m = f.above();
                        if (probe.valid(m)) {
                            out.add(m);
                        }
                    }
                }
            }
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
        s.goals.put("gallery", new BlockPos(0, UPPER_GALLERY_Y + 1, GALLERY.z1() + 3));
        s.goals.put("rib floor", new BlockPos(RIB_X2 - 1, BONE_FLOOR + 1, WZ));
        s.goals.put("plaza", PLAZA_CENTER);
        s.goals.put("crown", new BlockPos(0, F0.floor() + 1, (F0.z1() + F0.z2()) / 2 + 3));
        s.goals.put("east door 3", new BlockPos(EAST_DOORS[EAST_DOORS.length - 1] + 3, BONE_FLOOR + 1, WZ));
        s.goals.put("west island", new BlockPos(WEST_ISLAND.x1() + 3, BONE_FLOOR + 1, WZ));
        s.goals.put("elbow", WEST_ELBOW.offset(0, 1, 4));
        s.goals.put("west vault", VAULT_CACHE_W.offset(0, 0, 3));
        s.goals.put("east vault", VAULT_CACHE_E.offset(0, 0, 3));
        s.goals.put("cache west", CACHE_WEST.offset(0, 0, 2));
        s.goals.put("cache east", CACHE_EAST.offset(0, 0, 2));
        s.goals.put("west stone ledge", WEST_STONE.offset(1, 0, 0));
        s.goals.put("tail hall", TAIL_CENTER);
        s.goals.put("eye hall", EYE_HALL_CENTER.offset(1, 0, 6));
        s.goals.put("raiju nest", RAIJU_NEST.offset(-6, 1, 0));
        s.goals.put("east perch", EAST_STONE.offset(-1, 0, 0));
        s.goals.put("beak room", FRONT_STONE.offset(1, 0, 0));
        s.goals.put("heart room", HEART_ROOM.offset(0, 2, 8));
        for (var e : StormExtra.goals().entrySet()) {
            s.goals.put(e.getKey(), e.getValue());
        }
        if (StormDeck.placed()) {
            s.goals.putAll(StormDeck.goals());
            s.goals.putAll(StormDeck2.goals());
            // 第二の甲板の頂 (S12) の扇の足場は、閉じた足場として歩く
            s.solidGate = StormDeck2::isFloorGate;
        }
        return s;
    }

    public static BackReach.Report run(ServerLevel level) {
        return BackReach.run(level, spec(level));
    }
}
