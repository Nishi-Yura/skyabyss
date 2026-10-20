package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

// 雷翼の背の左右の翼 (26 の 3.3)。西 = 風切りの骨 (骨の翼桁: 白い骨の梁が天井を渡る、割れた床、肘の洞、肋の間)、
// 東 = 雷の骨 (銅の稲妻の筋が壁と天井を走る、回路の戸が四つ、手首の瘤 = 雷獣の巣)。
// 道は広間から一本: 肩の階段 → 肩の部屋 (羽根・舵・休み場) → 翼の道。どちらの翼も「一つの決まりを教えて、ひねる」部屋の並び
final class StormWingBuilder {
    private StormWingBuilder() {
    }

    // ---------------------------------------------------------------- 西
    static void west(StormKit kit) {
        shoulderPass(kit, WEST_PASS, -1, Style.FEATHER, Style.BONE);
        kit.room(W0, Style.BONE, 5);
        spars(kit, W0.x1() + 2, W0.x2() - 2, 5, W0.floor(), W0.height(), W0.z1(), W0.z2());
        // 肩の部屋の羽根の台 (中央に白い羽の飾り台。風切りの羽根がここで手に入る)
        featherDais(kit, (W0.x1() + W0.x2()) / 2, WZ, W0.floor());
        // 上腕の骨 (空洞の骨の中の回廊。割れた床が二つ。間は島)
        bone(kit, WEST_UPPER_X1, WEST_UPPER_X2);
        // 肘の洞 (丸い広間。休み場 w1。骨の柱が四本)
        kit.cave(WEST_ELBOW, ELBOW_R, BONE_FLOOR, BONE_FLOOR + 14, Style.BONE, 8);
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                bonePillar(kit, WEST_ELBOW.getX() + sx * 8, WEST_ELBOW.getZ() + sz * 8, BONE_FLOOR, 9);
            }
        }
        // 前腕: 縦の肋の間 (25 の 5章 A。縦の空洞の登り) と、肘からの廊下
        StormRib.build(kit);
        bone(kit, RIB_X2 + 1, WEST_FORE_X2);
        vault(kit, WEST_VAULT, WEST_VAULT_DOOR, Style.BONE, VAULT_CACHE_W, true);
        // 休み場の腰掛け
        for (Rest r : RESTS) {
            if (r.id().equals("wm") || r.id().equals("w0")) {
                int rx = r.campfire().getX();
                StormDeco.bench(kit, rx - 1, rx + 1, BONE_Z2, BONE_Z2, BONE_FLOOR + 1, Direction.NORTH);
            }
        }
    }

    // 肩の階段の広間: 広間の壁から平らに PASS_FLAT マス、そのあと 1 段ずつ上って肩の部屋の床へ (dir -1 = 西、+1 = 東)
    static void shoulderPass(StormKit kit, Area p, int dir, Style first, Style last) {
        int xIn = dir < 0 ? p.x2() : p.x1();
        int xOut = dir < 0 ? p.x1() : p.x2();
        int rise = BONE_FLOOR - HUB.floor();
        for (int i = 0; ; i++) {
            int x = xIn + dir * i;
            int j = i - PASS_FLAT;
            int floor = HUB.floor() + Math.max(0, Math.min(rise, j));
            Style style = i < 12 ? first : last;
            kit.room(x, x, p.z1(), p.z2(), floor, p.height(), style, 0);
            // 一段ごとに 1 上がる (階段は上がった先の床のブロックの代わりに置く)
            if (j >= 1 && j <= rise) {
                for (int z = p.z1(); z <= p.z2(); z++) {
                    kit.stair(x, floor, z, dir < 0 ? Direction.WEST : Direction.EAST, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
                }
            }
            if (i % 4 == 2) {
                kit.solid(x, floor + 4, p.z1() - 1, StormKit.LAMP);
                kit.solid(x, floor + 4, p.z2() + 1, StormKit.LAMP);
            }
            // 床の道しるべ (行き先の色の線。真ん中の 1 列)
            if (j < 1 || j > rise) {
                kit.put(x, floor, WZ, dir < 0 ? StormDeco.WEST_LINE : StormDeco.EAST_LINE);
            } else if (j % 3 == 0) {
                kit.stair(x, floor, WZ, dir < 0 ? Direction.WEST : Direction.EAST, dir < 0 ? Blocks.QUARTZ_STAIRS.defaultBlockState()
                        : Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
            }
            if (x == xOut) {
                break;
            }
        }
    }

    // 空洞の骨の一本 (x1〜x2)。割れた床・低い梁・昇り風の穴・高い梁・骨の翼桁 (天井を渡る白い骨の梁と、風よけの柱)
    static void bone(StormKit kit, int x1, int x2) {
        kit.room(x1, x2, BONE_Z1, BONE_Z2, BONE_FLOOR, BONE_HEIGHT, Style.BONE, 6);
        // 翼桁: 6 ブロックおきに、北と南の壁の柱と、天井の梁。割れた床のそばは避ける
        for (int x = x2 - 3; x >= x1 + 2; x -= 6) {
            if (nearGap(x, 2)) {
                continue;
            }
            spar(kit, x, BONE_FLOOR, BONE_HEIGHT, BONE_Z1, BONE_Z2);
        }
    }

    static boolean nearGap(int x, int margin) {
        for (int[] g : WEST_GAPS) {
            if (x >= g[0] - margin && x <= g[1] + margin) {
                return true;
            }
        }
        return false;
    }

    // 翼桁 (骨の梁) 一組: 北と南の壁ぎわの太い骨の柱 (床から天井まで) と、天井を渡る梁。
    // 横風 (嵐の拍) をよける陰の柱は、床の上の低い柱 (風上から見て 3 ブロックの陰ができる) を北から南へ 3 本
    static void spar(StormKit kit, int x, int floor, int height, int z1, int z2) {
        for (int z : new int[] {z1, z2}) {
            for (int y = floor + 1; y <= floor + height; y++) {
                kit.put(x, y, z, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
                kit.put(x + 1, y, z, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
            }
        }
        for (int z = z1; z <= z2; z++) {
            kit.put(x, floor + height, z, z == (z1 + z2) / 2 ? StormKit.LAMP : StormKit.BONE);
            kit.put(x + 1, floor + height, z, StormKit.BONE);
        }
        // 風よけの低い柱 (3 本)
        int mid = (z1 + z2) / 2;
        for (int z : new int[] {mid - 6, mid, mid + 6}) {
            for (int y = floor + 1; y <= floor + 3; y++) {
                kit.put(x, y, z, StormKit.BONE);
            }
            kit.put(x, floor + 4, z, StormKit.CALCITE);
        }
    }

    // 部屋の骨の梁 (肩の部屋・島など。柱は壁の一番外側)
    static void spars(StormKit kit, int xa, int xb, int step, int floor, int height, int z1, int z2) {
        for (int x = xa; x <= xb; x += step) {
            for (int z : new int[] {z1, z2}) {
                for (int y = floor + 1; y <= floor + height; y++) {
                    kit.put(x, y, z, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
                }
            }
            for (int z = z1; z <= z2; z++) {
                kit.put(x, floor + height, z, StormKit.BONE);
            }
        }
    }

    static void bonePillar(StormKit kit, int cx, int cz, int floor, int height) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int y = floor + 1; y <= floor + height; y++) {
                    kit.put(cx + dx, y, cz + dz, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
                }
            }
        }
        kit.put(cx, floor + height + 1, cz, StormKit.LAMP);
    }

    // 肩の部屋の羽の台 (3x3 の銅の台と白い羽の柱。ただの飾り。羽根は部屋の中で渡る)
    private static void featherDais(StormKit kit, int cx, int cz, int floor) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                kit.put(cx + dx, floor, cz + dz, ring ? StormKit.COPPER : StormKit.FEATHER_WHITE);
            }
        }
        for (int y = floor + 1; y <= floor + 4; y++) {
            kit.put(cx, y, cz, y == floor + 4 ? StormKit.LAMP : StormKit.COPPER);
        }
    }

    // 横の部屋 (骨の書庫・雷の炉): 回廊の北の壁から幅 3 の戸口で入る。奥の壁に碑二つと宝箱 (StormCaches)。書庫は本棚、炉は銅のコイルの柱。必須ではない
    private static void vault(StormKit kit, Area a, int[] door, Style style, BlockPos chest, boolean shelves) {
        kit.room(a, style, 4);
        // 戸口 (回廊の北の壁と、部屋の南の壁)。高さ 5
        for (int x = door[0]; x <= door[1]; x++) {
            for (int z = a.z2() + 1; z <= BONE_Z1 - 1; z++) {
                for (int y = a.floor() + 1; y <= a.floor() + 5; y++) {
                    kit.air(x, y, z);
                }
                kit.put(x, a.floor(), z, StormKit.floor(style, x, z));
            }
        }
        // 戸口の枠 (銅) と上の灯り
        for (int z = a.z2() + 1; z <= BONE_Z1 - 1; z++) {
            for (int y = a.floor() + 1; y <= a.floor() + 5; y++) {
                kit.put(door[0] - 1, y, z, y == a.floor() + 3 ? StormKit.LAMP : StormKit.COPPER_BLOCK);
                kit.put(door[1] + 1, y, z, y == a.floor() + 3 ? StormKit.LAMP : StormKit.COPPER_BLOCK);
            }
        }
        for (int x = door[0] - 1; x <= door[1] + 1; x++) {
            kit.put(x, a.floor() + 6, BONE_Z1 - 1, Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
        }
        // 床の道しるべ (戸口から宝箱まで、銅の線)
        int mid = (door[0] + door[1]) / 2;
        for (int z = BONE_Z1 - 1; z >= chest.getZ() + 1; z--) {
            kit.put(mid, a.floor(), z, StormKit.COPPER);
        }
        // 奥の壁ぎわの宝箱 (見た目だけ。右クリックで一度だけ小さな贈り物)
        kit.put(chest.getX(), chest.getY(), chest.getZ(), jp.revati.skyabyss.relic.RelicBlocks.cache(jp.revati.skyabyss.relic.RelicStyle.STORM, Direction.SOUTH));
        kit.put(chest.getX() - 1, chest.getY(), chest.getZ(), StormKit.LAMP);
        kit.put(chest.getX() + 1, chest.getY(), chest.getZ(), StormKit.LAMP);
        // 側の壁: 書庫は本棚 (3 段)、炉は銅のコイルの柱
        for (int sx : new int[] {a.x1(), a.x2()}) {
            for (int z = a.z1() + 2; z <= a.z2() - 1; z++) {
                for (int y = a.floor() + 1; y <= a.floor() + 3; y++) {
                    if (shelves) {
                        kit.put(sx, y, z, (z + y) % 5 == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
                    } else if ((z - a.z1()) % 3 == 1) {
                        kit.put(sx, y, z, y == a.floor() + 3 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : StormKit.COPPER_BLOCK);
                    }
                }
            }
        }
        // 真ん中の机 (低い台。書庫は羽の台、炉は銅の台)
        int cx = (a.x1() + a.x2()) / 2;
        int cz = (a.z1() + a.z2()) / 2 + 1;
        for (int dx = 0; dx <= 1; dx++) {
            kit.put(cx + dx, a.floor() + 1, cz, shelves ? StormKit.CALCITE : StormKit.COPPER_BLOCK);
        }
        kit.put(cx, a.floor() + 2, cz, shelves ? StormKit.LAMP : Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    // 道の形 (リセットの種): 割れた床の低い梁の位置と、昇り風の穴と高い梁。建て直さずに試練の側が置き直す
    static void carveWestRoute(StormKit kit, int route) {
        int half = LOW_BEAM_WIDTH / 2;
        for (int gi = 0; gi < WEST_GAPS.length; gi++) {
            int[] g = WEST_GAPS[gi];
            // 割れた床: 床と、その下の骨を嵐の雲まで抜く
            for (int x = g[0]; x <= g[1]; x++) {
                for (int z = BONE_Z1; z <= BONE_Z2; z++) {
                    int bottom = StormBirdBuilder.BIRD.exists() ? Math.min(StormBirdBuilder.BIRD.bottom(x, z), BONE_FLOOR) : BONE_FLOOR - 3;
                    for (int y = bottom - 1; y <= BONE_FLOOR; y++) {
                        kit.air(x, y, z);
                    }
                    for (int y = BONE_FLOOR + 1; y < HIGH_BEAM_Y; y++) {
                        kit.air(x, y, z);
                    }
                }
            }
            // 縁は 1 段高い (割れた床の両側の 1 列)。梁の入口の脇は灯り
            for (int z = BONE_Z1; z <= BONE_Z2; z++) {
                kit.put(g[0] - 1, BONE_FLOOR + 1, z, Blocks.CALCITE.defaultBlockState());
                kit.put(g[1] + 1, BONE_FLOOR + 1, z, Blocks.CALCITE.defaultBlockState());
            }
            // 低い梁 (幅 3。形は道の形で 3 通り: 北・南・食い違い)
            int pattern = (route + gi) % 3;
            int zA = pattern == 0 ? WZ - 6 : pattern == 1 ? WZ + 6 : WZ - 5;
            int zB = pattern == 2 ? WZ + 5 : zA;
            int mid = (g[0] + g[1]) / 2;
            for (int x = g[0] - 1; x <= g[1] + 1; x++) {
                int zc = x <= mid ? zA : zB;
                for (int z = zc - half; z <= zc + half; z++) {
                    if (x == g[0] - 1 || x == g[1] + 1) {
                        // 縁の切れ目 (梁に乗る所だけ縁を下げる)
                        kit.put(x, BONE_FLOOR + 1, z, StormKit.AIR);
                        kit.carved().add(BlockPos.asLong(x, BONE_FLOOR + 1, z));
                    }
                    kit.put(x, BONE_FLOOR, z, StormKit.BONE);
                }
            }
            if (zA != zB) {
                for (int z = Math.min(zA, zB) - half; z <= Math.max(zA, zB) + half; z++) {
                    for (int x = mid - 1; x <= mid + 1; x++) {
                        kit.put(x, BONE_FLOOR, z, StormKit.BONE);
                    }
                }
            }
            // 梁の入口の目印: 縁の灯り (梁の両脇の縁に光る石)
            for (int x : new int[] {g[0] - 1, g[1] + 1}) {
                int zc = x <= mid ? zA : zB;
                kit.put(x, BONE_FLOOR + 1, zc - half - 1, StormKit.LAMP);
                kit.put(x, BONE_FLOOR + 1, zc + half + 1, StormKit.LAMP);
            }
        }
        // 昇り風の穴と、嵐の高い道 (二つの割れた床の上をまとめて渡る梁。幅 2、z WZ-1〜WZ)
        for (int arm = 0; arm < WEST_VENT_X.length; arm++) {
            int vx = WEST_VENT_X[arm];
            StormBirdBuilder.vent(kit, new BlockPos(vx, BONE_FLOOR, WZ - 1));
            // 梁は両端を骨の壁につなぐ (浮かせない)。東は上腕の東の壁、西は上腕の西の壁まで
            for (int x = WEST_UPPER_X2; x >= WEST_UPPER_X1; x--) {
                for (int z = WZ - 1; z <= WZ; z++) {
                    kit.put(x, HIGH_BEAM_Y, z, StormKit.BONE);
                }
                // 陰の柱 (梁の北の側の列の上、5 ブロックおき。梁に立つ)。柱のすぐ東 (肩の側) の同じ列に立つと、横風をよけられる
                if (Math.floorMod(x, 5) == 0) {
                    kit.put(x, HIGH_BEAM_Y + 1, WZ - 1, StormKit.CALCITE);
                    kit.put(x, HIGH_BEAM_Y + 2, WZ - 1, StormKit.CALCITE);
                }
                // 梁の下に 6 ブロックおきに灯り (嵐の高い道を明るく)
                if (Math.floorMod(x, 6) == 0) {
                    kit.put(x, HIGH_BEAM_Y - 1, WZ - 1, StormKit.LAMP);
                }
            }
        }
    }

    static boolean inGap(int x) {
        for (int[] g : WEST_GAPS) {
            if (x >= g[0] && x <= g[1]) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 東
    static void east(StormKit kit) {
        shoulderPass(kit, EAST_PASS, 1, Style.FEATHER, Style.THUNDER);
        kit.room(E0, Style.THUNDER, 5);
        featherDais(kit, (E0.x1() + E0.x2()) / 2, WZ, E0.floor());
        // 回廊 (回路 4 つ・骨の戸 4 枚。端は雷獣の巣)
        kit.room(EAST_X1, EAST_X2, BONE_Z1, BONE_Z2, BONE_FLOOR, EAST_HEIGHT, Style.THUNDER, 7);
        // 稲妻の筋 (骨の壁と天井を走る銅の筋と光。手首の瘤へ集まる)
        for (int x = EAST_X1; x <= EAST_X2; x++) {
            int y = BONE_FLOOR + 5 + (int) Math.round(2 * Math.sin(x * 0.45));
            kit.solid(x, y, BONE_Z1 - 1, StormKit.COPPER);
            kit.solid(x, y, BONE_Z2 + 1, StormKit.COPPER);
            if (x % 6 == 3) {
                kit.solid(x, y + 1, BONE_Z1 - 1, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
                kit.solid(x, y + 1, BONE_Z2 + 1, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            }
            kit.solid(x, BONE_FLOOR + EAST_HEIGHT + 1, WZ + (int) Math.round(4 * Math.sin(x * 0.3)), StormKit.COPPER);
            if (x % 8 == 4) {
                kit.solid(x, BONE_FLOOR + EAST_HEIGHT + 1, WZ + (int) Math.round(4 * Math.sin(x * 0.3)), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            }
        }
        // 骨の戸 (壁と、口の封印と、銅の枠)
        for (int dx : EAST_DOORS) {
            door(kit, dx);
        }
        // 雷の回路 (25 の 5章 B): 柱・導線・レバー・誘いの床。戸の柱 (EAST_RODS) もここで建てる
        StormCircuit.build(kit);
        // 休み場の腰掛け
        for (Rest r : RESTS) {
            if (r.id().equals("e0") || r.id().equals("e1") || r.id().equals("e2") || r.id().equals("e3")) {
                int rx = r.campfire().getX();
                StormDeco.bench(kit, rx - 1, rx + 1, BONE_Z2, BONE_Z2, BONE_FLOOR + 1, Direction.NORTH);
            }
        }
        vault(kit, EAST_VAULT, EAST_VAULT_DOOR, Style.THUNDER, VAULT_CACHE_E, false);
        // 前室 (最後の戸と雷獣の巣の口のあいだ): 銅の柱の並び
        for (int x = EAST_DOORS[EAST_DOORS.length - 1] + 4; x <= EAST_X2 - 4; x += 4) {
            for (int sz : new int[] {BONE_Z1 + 1, BONE_Z2 - 1}) {
                for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + EAST_HEIGHT - 1; y++) {
                    kit.put(x, y, sz, y % 4 == 0 ? StormKit.COPPER_BLOCK : StormKit.COPPER);
                }
                kit.put(x, BONE_FLOOR + EAST_HEIGHT, sz, StormKit.LAMP);
            }
        }
        // 右手首の瘤 (雷獣の巣)
        kit.cave(RAIJU_NEST, RAIJU_NEST_RADIUS, RAIJU_NEST.getY(), RAIJU_NEST_CEILING, Style.THUNDER, 8);
        for (BlockPos r : raijuRods()) {
            StormBirdBuilder.rod(kit, r, 0);
            // 接地の栓 (銅の取っ手 = レバー)。柱の根元の、洞の真ん中の側
            int dx = Integer.signum(RAIJU_NEST.getX() - r.getX());
            int dz = Integer.signum(RAIJU_NEST.getZ() - r.getZ());
            BlockPos lever = r.offset(Math.abs(dx) >= Math.abs(dz) ? dx : 0, 0, Math.abs(dx) >= Math.abs(dz) ? 0 : dz);
            Direction face = Direction.getNearest(lever.getX() - r.getX(), 0, lever.getZ() - r.getZ());
            kit.put(lever.getX(), lever.getY() + 1, lever.getZ(), jp.revati.skyabyss.relic.RelicBlocks.leverBase(jp.revati.skyabyss.relic.RelicStyle.STORM).setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, face));
        }
        // 頂の台 (東の石) と、真ん中の昇り風の穴 (雷獣を倒すと立つ)
        Area p = EAST_PERCH;
        for (int x = p.x1(); x <= p.x2(); x++) {
            for (int z = p.z1(); z <= p.z2(); z++) {
                kit.put(x, p.floor(), z, StormKit.COPPER);
                for (int y = p.floor() + 1; y <= p.floor() + 2; y++) {
                    kit.air(x, y, z);
                }
            }
        }
        // 台の角の柱 (台から洞の天井までつなぐ。台が宙に浮かないように)
        for (int y = p.floor() + 1; y <= RAIJU_NEST_CEILING + 1; y++) {
            kit.put(p.x2(), y, p.z1(), StormKit.TILE);
        }
        StormBirdBuilder.vent(kit, RAIJU_VENT);
    }

    // 骨の戸 x: 回廊いっぱいの壁に、口 (z WZ±DOOR_HALF、高さ DOOR_HEIGHT) の封印。口の両脇に銅の太い柱、上に彫り銅の枠と灯り
    private static void door(StormKit kit, int dx) {
        BlockState sealed = ModBlocks.SEALED_GATE.get().defaultBlockState();
        for (int z = BONE_Z1; z <= BONE_Z2; z++) {
            for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + EAST_HEIGHT; y++) {
                boolean mouth = Math.abs(z - WZ) <= DOOR_HALF && y <= BONE_FLOOR + DOOR_HEIGHT;
                kit.put(dx, y, z, mouth ? sealed : StormKit.wall(Style.THUNDER, dx, y, z));
            }
        }
        int lintel = BONE_FLOOR + DOOR_HEIGHT + 1;
        for (int z = WZ - DOOR_HALF - 1; z <= WZ + DOOR_HALF + 1; z++) {
            for (int sx = dx - 1; sx <= dx + 1; sx++) {
                kit.put(sx, lintel, z, Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
            }
        }
        for (int sz : new int[] {WZ - DOOR_HALF - 1, WZ + DOOR_HALF + 1}) {
            for (int sx = dx - 1; sx <= dx + 1; sx++) {
                for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + DOOR_HEIGHT; y++) {
                    kit.put(sx, y, sz, StormKit.COPPER_BLOCK);
                }
            }
        }
        // 戸の上の光る紋 (戸が開いたとわかる灯り。口の真上の枠の上)
        kit.put(dx, lintel + 1, WZ, StormKit.LAMP);
        kit.put(dx - 1, lintel + 1, WZ, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        kit.put(dx + 1, lintel + 1, WZ, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
    }
}
