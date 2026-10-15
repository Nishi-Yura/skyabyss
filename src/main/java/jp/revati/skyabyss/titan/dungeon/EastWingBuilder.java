package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

// 東の翼「番兵の回廊」: 三つの広間を順に制圧していく戦いの回廊。
//   東の前室 (休み場) → 第一の広間 → 休み場 → 第二の広間 → 休み場 → 番兵長の広間 (共鳴石)
// 広間に入ると入口が閉じ、番兵の波が押し寄せる。倒しきると出口が開く (TitanBackTrials)。
// 広間には身を隠せる柱が並ぶ。出口は最初から封じておき、制圧で開く
final class EastWingBuilder {
    private EastWingBuilder() {
    }

    static void build(BuildKit kit) {
        kit.room(E0);
        kit.room(E1);
        kit.room(CE1);
        kit.room(E2);
        kit.room(CE2);
        kit.room(E3);
        kit.room(new Box(16, 18, -57, -53, FLOOR, 4));   // 休み場 → 番兵長の広間
        for (List<BlockPos> door : List.of(E1_ENTRANCE, E1_EXIT, E2_ENTRANCE, E2_EXIT)) {
            for (BlockPos p : door) {
                kit.set(p, BuildKit.AIR);
            }
        }
        // 出口は制圧するまで閉じている (入口は戦いが始まると閉じる)
        for (List<BlockPos> exit : List.of(E1_EXIT, E2_EXIT)) {
            for (BlockPos p : exit) {
                kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }

        // 番兵長の広間から鼓動の間へ戻る扉 (石が歌うまで封じられている)。広間を掘ったあとに置く
        for (BlockPos p : EAST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        for (Box hall : List.of(E1, E2, E3)) {
            hallInterior(kit, hall);
        }
        throneDais(kit);
        kit.restPoint(E0, "sign.skyabyss.rest_e0", Direction.SOUTH, new BlockPos(19, FLOOR + 3, E0.z1()));
        kit.restPoint(CE1, "sign.skyabyss.rest_e1", Direction.NORTH, new BlockPos(27, FLOOR + 3, CE1.z2()));
        kit.restPoint(CE2, "sign.skyabyss.rest_e2", Direction.SOUTH, new BlockPos(19, FLOOR + 3, CE2.z1()));
        kit.pilasters(E0, 3);
        for (Box rest : List.of(E0, CE1, CE2)) {
            kit.wallLamps(rest, 3, FLOOR + 3);
        }
        kit.moss(E0, 0.05f);
        kit.wallSign(new BlockPos(16, FLOOR + 3, -21 + 2), Direction.EAST, "sign.skyabyss.arena");
    }

    // 広間: 床の模様、身を隠す柱、壁の柱、天井の骨の梁、吊るした灯り
    private static void hallInterior(BuildKit kit, Box hall) {
        kit.floorBorder(hall);
        for (int x = hall.x1(); x <= hall.x2(); x++) {
            for (int z = hall.z1(); z <= hall.z2(); z++) {
                if ((x + z) % 4 == 0) {
                    kit.set(x, hall.floorY(), z, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        for (int x = hall.x1(); x <= hall.x2(); x++) {
            for (int z = hall.z1(); z <= hall.z2(); z++) {
                if ((x - hall.x1()) % 5 == 3 && (z - hall.z1()) % 5 == 3 && !isSpawnPoint(hall, x, z)
                        && x > hall.x1() && x < hall.x2() && z > hall.z1() && z < hall.z2()) {
                    for (int y = hall.floorY() + 1; y < hall.ceilingY(); y++) {
                        kit.set(x, y, z, y == hall.floorY() + 1 || y == hall.ceilingY() - 1 ? BuildKit.CHISELED : BuildKit.POLISHED);
                    }
                }
            }
        }
        kit.pilasters(hall, 4);
        kit.wallLamps(hall, 4, hall.floorY() + 4);
        kit.ribs(hall, 5, hall.z2() - hall.z1() >= hall.x2() - hall.x1());
        BlockPos c = hall.center();
        kit.hangingLantern(c.getX(), c.getZ(), hall.ceilingY(), 2);
        kit.hangingLantern(hall.x1() + 2, hall.z1() + 2, hall.ceilingY(), 1);
        kit.hangingLantern(hall.x2() - 2, hall.z2() - 2, hall.ceilingY(), 1);
        kit.hangingLantern(hall.x1() + 2, hall.z2() - 2, hall.ceilingY(), 1);
        kit.hangingLantern(hall.x2() - 2, hall.z1() + 2, hall.ceilingY(), 1);
        kit.moss(hall, 0.03f);
        // 番兵の湧く場所には苔を残さない
        for (BlockPos p : spawnPoints(hall)) {
            if (kit.get(p.getX(), p.getY(), p.getZ()).is(Blocks.MOSS_CARPET)) {
                kit.set(p.getX(), p.getY(), p.getZ(), BuildKit.AIR);
            }
        }
    }

    // 番兵長の広間の奥 (共鳴石のある南端): 石を囲む彫刻の床と、両脇の灯り。抜け道の前は空けておく
    private static void throneDais(BuildKit kit) {
        BlockPos stone = RESONANCE_STONES.get(1);
        for (int x = stone.getX() - 2; x <= stone.getX() + 2; x++) {
            for (int z = stone.getZ() - 2; z <= stone.getZ() + 1; z++) {
                kit.set(x, FLOOR, z, (x + z) % 2 == 0 ? BuildKit.CHISELED : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
        }
        kit.lantern(stone.getX() - 3, FLOOR + 1, stone.getZ());
        kit.lantern(stone.getX() + 3, FLOOR + 1, stone.getZ());
    }

    // 戦いの湧き場所 (TitanBackTrials が使う)。広間の四隅の内側と中央
    static List<BlockPos> spawnPoints(Box hall) {
        BlockPos c = hall.center();
        int y = hall.floorY() + 1;
        return List.of(
                new BlockPos(hall.x1() + 2, y, hall.z1() + 2), new BlockPos(hall.x2() - 2, y, hall.z1() + 2),
                new BlockPos(hall.x1() + 2, y, hall.z2() - 2), new BlockPos(hall.x2() - 2, y, hall.z2() - 2),
                new BlockPos(c.getX(), y, c.getZ()));
    }

    private static boolean isSpawnPoint(Box hall, int x, int z) {
        for (BlockPos p : spawnPoints(hall)) {
            if (Math.abs(p.getX() - x) <= 1 && Math.abs(p.getZ() - z) <= 1) {
                return true;
            }
        }
        return false;
    }
}
