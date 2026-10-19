package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.BreathLayout.*;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.titan.group.FirstRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 息の間の道しるべ (docs/design/24 の「見える進み」「周回は既知の道」)。
//  - 外: 甲羅の上 (T1) の、上る段の口から息の間の戸口までの光の点線と、戸口の光の柱 (そこにいる人にだけ見える。触れよの光)
//  - 周回: 心臓を止めたグループには、各皿の「着地から光る泡まで」の盆の底が光る道すじになる (FirstRun の既知の道。そのグループの画面だけ)
final class BreathGuide {
    private BreathGuide() {
    }

    // 戸口 (崖の穴の外の窪み)
    static final Vec3 DOOR = new Vec3(UPPER_GATE_X - 1.5, UPPER_1.floor() + 1.0, UPPER_GATE_Z + 0.5);
    // 上る段の口 (甲羅の上へ出る所)
    static final Vec3 STAIR_TOP = new Vec3(TOP_STAIR_X1 + 2.5, TOP_Y + 1.0, TOP_STAIR_Z1 + 2.5);

    static boolean nearDoor(Vec3 p) {
        return p.y > TOP_Y - 2 && p.y < TOP_Y + 14 && p.distanceToSqr(DOOR) < 70 * 70;
    }

    // 既知の道 (周回): 皿ごとの着地から泡までの盆の底、縁の入口の泡への道
    static List<BlockPos> guideCells() {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (Node n : nodes()) {
            if (n.kind() != Kind.DISH && n.kind() != Kind.ENTRY) {
                continue;
            }
            double ax = n.landX();
            double az = n.landZ();
            double bx = n.padX();
            double bz = n.padZ();
            double len = Math.hypot(bx - ax, bz - az);
            int y = n.kind() == Kind.DISH ? n.dish() >= 0 ? dish(n.dish()).floorY() - 1 : LIP : LIP;
            for (double t = 0; t <= len; t += 0.5) {
                int x = (int) Math.floor(ax + (bx - ax) * t / Math.max(len, 1e-6));
                int z = (int) Math.floor(az + (bz - az) * t / Math.max(len, 1e-6));
                BlockPos p = new BlockPos(x, y, z);
                if (n.kind() == Kind.DISH) {
                    // 盆の中だけ (縁の輪の石は変えない)
                    if (!dish(n.dish()).basinContains(x, z)) {
                        continue;
                    }
                }
                cells.add(p);
            }
        }
        return new ArrayList<>(cells);
    }

    static void register() {
        FirstRun.register(FirstRun.Room.firstRun(BreathSummit.FIRST_ROOM_ID, jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK, Breath.activeBox())
                .guide(guideCells()).guideState(TideKit.LAMP));
    }

    // 5 tick ごと (TideGuide から)
    static void tick(ServerLevel level, int counter) {
        if (counter % 2 != 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || !nearDoor(p.position())) {
                continue;
            }
            // 戸口の光の柱 (遠くから見える)
            for (int i = 0; i < 5; i++) {
                level.sendParticles(p, ParticleTypes.END_ROD, false, DOOR.x, DOOR.y + 0.5 + i * 1.6, DOOR.z, 1, 0.12, 0.2, 0.12, 0.0);
            }
            // 上る段の口から戸口まで、すぐ先の点線 (その人のまわりの 14 ブロック)
            double len = DOOR.distanceTo(STAIR_TOP);
            for (double s = 0; s <= len; s += 2.5) {
                double t = s / len;
                double x = STAIR_TOP.x + (DOOR.x - STAIR_TOP.x) * t;
                double z = STAIR_TOP.z + (DOOR.z - STAIR_TOP.z) * t;
                if (Math.hypot(p.getX() - x, p.getZ() - z) < 14) {
                    level.sendParticles(p, ParticleTypes.END_ROD, false, x, TOP_Y + 1.3, z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    static AABB doorBox() {
        return new AABB(DOOR.x - 3, TOP_Y, DOOR.z - 3, DOOR.x + 3, TOP_Y + 6, DOOR.z + 3);
    }
}
