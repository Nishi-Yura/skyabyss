package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

// 東の翼「番兵の回廊」(版 13 で x1.6): 広間を順に制圧していく戦いの回廊。
//   東の前室 (休み場) → 第一の広間 → 番兵長の控えの間 (休み場) → 短い通路 → 番兵長の広間 (共鳴石)
//   第一の広間の南の戸 → 休み場 e2r → 第二の広間 (射手の波) → 休み場 ckr → 番兵の詰所 (老番兵。奥の戸の鍵 = 割符を持つ)
// 番兵長の広間の戸 (E3_LOCK) は、初回は詰所の老番兵を倒すまで閉じている (周回は開いている。FirstRun の既知の道)。
// 広間に入ると入口が閉じ、番兵の波が押し寄せる。倒しきると出口が開く (TitanBackTrials)。
// 広間には身を隠せる柱が並ぶ。出口は最初から封じておき、制圧で開く
final class EastWingBuilder {
    private EastWingBuilder() {
    }

    static void build(BuildKit kit) {
        kit.room(E0);
        kit.room(E1);
        kit.room(CE1);
        kit.room(E3);
        kit.room(E3_CORRIDOR);   // 控えの間 → 番兵長の広間
        kit.room(E2);
        kit.room(CK);
        kit.room(E2_PASS);       // 第一の広間 → 第二の広間の間の休み場
        kit.room(CK_PASS);       // 第二の広間 → 詰所の間の休み場
        for (List<BlockPos> door : List.of(E1_ENTRANCE, E1_EXIT, CE1_WEST_DOOR, E3_ENTRANCE, E1_SOUTH, E2_ENTRANCE, E2_EXIT, CK_ENTRANCE, E3_LOCK)) {
            for (BlockPos p : door) {
                kit.set(p, BuildKit.AIR);
            }
        }
        // 出口は制圧するまで閉じている (入口は戦いが始まると閉じる)
        for (List<BlockPos> exit : List.of(E1_EXIT, E1_SOUTH, E2_EXIT, E3_LOCK)) {
            for (BlockPos p : exit) {
                kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }

        for (Box hall : List.of(E1, E2, E3, CK)) {
            hallInterior(kit, hall);
        }
        throneDais(kit);
        // 番兵長の広間から鼓動の間へ戻る扉 (石が歌うまで封じられている)。広間を掘ったあとに置く
        for (BlockPos p : EAST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        kit.restPoint(E0);
        kit.restPoint(CE1);
        kit.restPoint(E2_PASS);
        kit.restPoint(CK_PASS);
        kit.wallLamps(E3_CORRIDOR, 3, FLOOR + 4);
        kit.pilasters(E0, 3);
        for (Box rest : List.of(E0, CE1, E2_PASS, CK_PASS)) {
            kit.wallLamps(rest, 3, FLOOR + 4);
        }
        kit.moss(E0, 0.05f);
        // 詰所: 奥の壁に番兵の旗 (割符の戸の印)
        kit.lantern(CK.x2() - 1, FLOOR + 1, CK.z2());
        kit.lantern(CK.x1() + 1, FLOOR + 1, CK.z2());
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
        // 身を隠す柱 (間隔 8 マス。戸口の前と湧き場所は避ける)
        for (int x = hall.x1(); x <= hall.x2(); x++) {
            for (int z = hall.z1(); z <= hall.z2(); z++) {
                if ((x - hall.x1()) % 8 == 4 && (z - hall.z1()) % 8 == 4 && !isSpawnPoint(hall, x, z)
                        && x > hall.x1() && x < hall.x2() && z > hall.z1() && z < hall.z2()) {
                    for (int y = hall.floorY() + 1; y < hall.ceilingY(); y++) {
                        kit.set(x, y, z, y == hall.floorY() + 1 || y == hall.ceilingY() - 1 ? BuildKit.CHISELED : BuildKit.POLISHED);
                    }
                }
            }
        }
        kit.pilasters(hall, 5);
        kit.wallLamps(hall, 5, hall.floorY() + 6);
        kit.ribs(hall, 6, hall.z2() - hall.z1() >= hall.x2() - hall.x1());
        int step = Math.max(8, Math.min(hall.x2() - hall.x1(), hall.z2() - hall.z1()) / 2);
        for (int x = hall.x1() + 3; x <= hall.x2() - 3; x += step) {
            for (int z = hall.z1() + 3; z <= hall.z2() - 3; z += step) {
                kit.hangingLantern(x, z, hall.ceilingY(), 3);
            }
        }
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

    // 戦いの湧き場所 (TitanBackTrials が使う)。広間の四隅の内側・辺の中ほど・中央 (9 か所)
    static List<BlockPos> spawnPoints(Box hall) {
        BlockPos c = hall.center();
        int y = hall.floorY() + 1;
        int x1 = hall.x1() + 2;
        int x2 = hall.x2() - 2;
        int z1 = hall.z1() + 2;
        int z2 = hall.z2() - 2;
        List<BlockPos> list = new ArrayList<>();
        list.add(new BlockPos(x1, y, z1));
        list.add(new BlockPos(x2, y, z1));
        list.add(new BlockPos(x1, y, z2));
        list.add(new BlockPos(x2, y, z2));
        list.add(new BlockPos(c.getX(), y, c.getZ()));
        list.add(new BlockPos(c.getX(), y, z1));
        list.add(new BlockPos(c.getX(), y, z2));
        list.add(new BlockPos(x1, y, c.getZ()));
        list.add(new BlockPos(x2, y, c.getZ()));
        return List.copyOf(list);
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
