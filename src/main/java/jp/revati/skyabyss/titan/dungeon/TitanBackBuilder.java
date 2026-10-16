package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の背を建てる。ワールドで初めて必要になったとき (サーバー起動時) に一度だけ実行する。
// 配置は TitanBackLayout を参照。ここでは島そのもの・地表の遺跡・祠・鼓動の間・宝物庫を建て、
// 西の翼・東の翼・闇の水路はそれぞれの Builder に任せる
public final class TitanBackBuilder {
    private TitanBackBuilder() {
    }

    public static void build(ServerLevel level, TitanBackData data) {
        long start = System.currentTimeMillis();
        BuildKit kit = new BuildKit(level, RandomSource.create(20261015L));
        // アスレチックと迷路の形は、建てるたびに新しい種で決める
        data.reseed(level.getRandom());
        buildIsland(kit);
        buildSurface(kit);
        buildHub(kit);
        buildTreasury(kit);
        // 宝物庫の奥の扉の先: 心臓の回廊と、最終ボスの心臓の間
        HeartArenaBuilder.build(kit);
        // 鼓動の間の柱は、戸口・はしご・封印・碑文をすべて置いたあとに立てる (それらの前を避けるため)
        kit.pilasters(HUB, 4);
        kit.wallLamps(HUB, 4, FLOOR + 4);
        kit.wallLamps(ENTRY, 3, FLOOR + 3);
        WestWingBuilder.build(kit);
        EastWingBuilder.build(kit);
        MazeBuilder.buildFixtures(kit);
        MazeBuilder.buildMaze(new BuildKit(level, RandomSource.create(data.mazeSeed())), data.mazeSeed());
        // 体内を掘ってから階段を通す (先に階段を作ると、通路の壁でふさがれる)
        buildShrineAndStairs(kit);
        for (BlockPos p : RESONANCE_STONES) {
            kit.set(p, ModBlocks.RESONANCE_STONE.get().defaultBlockState());
        }
        // 物語の断片の碑文 (部屋と柱をすべて置いたあとに掛ける)
        for (Inscription lore : LORE_SIGNS) {
            kit.wallSign(lore.pos(), lore.facing(), lore.key());
        }
        data.markBuilt();
        SkyAbyss.LOGGER.info("Built the stone whale's back in {} ms", System.currentTimeMillis() - start);
    }

    // ---------------------------------------------------------------- 島
    private static void buildIsland(BuildKit kit) {
        RandomSource random = kit.random;
        for (int x = -RX - 2; x <= RX + 2; x++) {
            for (int z = -RZ - 2; z <= RZ + 2; z++) {
                double s = shape(x, z);
                int t = top(x, z);
                // 建て直したときに古い建物が宙に残らないよう、地表の上をきれいにする
                for (int y = (s > 0 ? t + 1 : BASE_Y - 40); y <= t + 18; y++) {
                    if (!kit.get(x, y, z).isAir()) {
                        kit.set(x, y, z, BuildKit.AIR);
                    }
                }
                if (s <= 0) {
                    continue;
                }
                int b = bottom(x, z);
                boolean rim = s < 0.07;
                for (int y = b; y <= t; y++) {
                    BlockState state;
                    int depth = t - y;
                    if (depth == 0) {
                        float r = random.nextFloat();
                        state = r < 0.55f ? Blocks.MOSS_BLOCK.defaultBlockState()
                                : r < 0.9f ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    } else if (depth <= 2) {
                        state = random.nextFloat() < 0.7f ? Blocks.DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    } else if (y == b || rim) {
                        float r = random.nextFloat();
                        state = r < 0.65f ? Blocks.DEEPSLATE.defaultBlockState()
                                : r < 0.9f ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    } else {
                        state = random.nextFloat() < 0.75f ? Blocks.TUFF.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                    }
                    kit.set(x, y, z, state);
                }
                // 腹から垂れる根と、縁から垂れるツタ
                if (random.nextFloat() < 0.1f) {
                    kit.set(x, b - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
                }
                if (rim && random.nextFloat() < 0.35f) {
                    hangVines(kit, x, z, t);
                }
            }
        }
    }

    private static void hangVines(BuildKit kit, int x, int z, int t) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int ox = x + dir.getStepX();
            int oz = z + dir.getStepZ();
            if (shape(ox, oz) > 0) {
                continue;
            }
            int length = 3 + kit.random.nextInt(9);
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true);
            for (int y = t - 1; y > t - 1 - length && y >= bottom(x, z); y--) {
                kit.set(ox, y, oz, vine);
            }
            return;
        }
    }

    // ---------------------------------------------------------------- 地表: 背骨の道、柱列、門、突き出た肋骨、木々、尾の台
    private static void buildSurface(BuildKit kit) {
        RandomSource random = kit.random;
        for (int z = -104; z <= PLATFORM_Z - 5; z++) {
            // 祠 (南の端) から尾の台までは、磨いた石畳を敷く
            boolean paved = z >= SHRINE_Z + 11 && z <= PLATFORM_Z - 9;
            for (int x = -1; x <= 1; x++) {
                if (paved) {
                    kit.set(x, top(x, z), z, x == 0 || z % 2 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
                } else if (random.nextFloat() < 0.75f) {
                    BlockState path = switch (random.nextInt(3)) {
                        case 0 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                        case 1 -> Blocks.TUFF_BRICKS.defaultBlockState();
                        default -> Blocks.GRAVEL.defaultBlockState();
                    };
                    kit.set(x, top(x, z), z, path);
                }
            }
        }
        // 石畳の脇に、灯りの柱を立てる
        for (int z : new int[]{42, 56, 70, 84}) {
            for (int side = -1; side <= 1; side += 2) {
                int base = top(2 * side, z) + 1;
                kit.set(2 * side, base, z, BuildKit.POLISHED);
                kit.set(2 * side, base + 1, z, BuildKit.POLISHED);
                kit.set(2 * side, base + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
        // 道の両側の柱列。祠と尾の台のまわりは空ける。いくつかは折れている
        for (int z = -98; z <= 84; z += 14) {
            if (z > SHRINE_Z - 14 && z < SHRINE_Z + 14) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                int height = random.nextFloat() < 0.35f ? 3 + random.nextInt(4) : 9 + random.nextInt(4);
                pillar(kit, 11 * side, z, height);
            }
        }
        gateArch(kit, -70);
        gateArch(kit, 56);
        // 体から突き出た巨大な肋骨の弧
        for (int z : new int[]{-40, -24, 40, 70}) {
            boneArc(kit, z, 26);
        }
        // 苔むした大岩と、アザレアの木
        for (int i = 0; i < 10; i++) {
            int x = (random.nextBoolean() ? 1 : -1) * (16 + random.nextInt(18));
            int z = -95 + random.nextInt(180);
            if (shape(x, z) > 0.15) {
                boulder(kit, x, z);
            }
        }
        var trees = kit.level().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(TreeFeatures.AZALEA_TREE);
        for (int i = 0; i < 14; i++) {
            int x = (random.nextBoolean() ? 1 : -1) * (6 + random.nextInt(28));
            int z = -100 + random.nextInt(190);
            if (shape(x, z) > 0.2 && Math.abs(x) > 4 && !(z > SHRINE_Z - 14 && z < SHRINE_Z + 14)) {
                BlockPos pos = new BlockPos(x, top(x, z) + 1, z);
                trees.ifPresent(t -> t.value().place(kit.level(), kit.level().getChunkSource().getGenerator(), random, pos));
            }
        }
        // 頭の先の見張り台: 二本の角のような柱
        int hz = -108;
        int hy = top(0, hz);
        kit.fill(-3, hy, hz - 2, 3, hy, hz + 2, BuildKit.POLISHED);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 7; i++) {
                kit.set(3 * side + (i > 4 ? side : 0), hy + i, hz - (i > 5 ? 1 : 0), BuildKit.TILES);
            }
        }
        buildPlatform(kit);
    }

    private static void pillar(BuildKit kit, int x, int z, int height) {
        int base = top(x, z) + 1;
        kit.set(x, base, z, BuildKit.CHISELED);
        for (int i = 1; i < height; i++) {
            kit.set(x, base + i, z, i % 3 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
        }
        if (height >= 8) {
            kit.set(x, base + height, z, BuildKit.CHISELED);
        } else {
            kit.set(x, base + height, z, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
            for (int i = 0; i < 3; i++) {
                int fx = x + kit.random.nextInt(5) - 2;
                int fz = z + kit.random.nextInt(5) - 2;
                kit.set(fx, top(fx, fz) + 1, fz, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
            }
        }
    }

    // 道をまたぐ石の門 (2本の柱と、段のついた梁)
    private static void gateArch(BuildKit kit, int z) {
        int base = Math.max(top(-5, z), top(5, z)) + 1;
        for (int side = -1; side <= 1; side += 2) {
            for (int y = base - 3; y < base + 10; y++) {
                kit.set(5 * side, y, z, y == base + 9 ? BuildKit.CHISELED : BuildKit.POLISHED);
            }
        }
        for (int x = -6; x <= 6; x++) {
            kit.set(x, base + 10, z, BuildKit.TILES);
        }
        for (int x = -3; x <= 3; x++) {
            kit.set(x, base + 11, z, Math.abs(x) == 3 ? Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST) : BuildKit.CHISELED);
        }
    }

    // 地表を割って突き出た、鯨の肋骨の弧
    private static void boneArc(BuildKit kit, int z, int radius) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        int cy = top(0, z) - radius / 2;
        for (int a = 0; a <= 180; a += 2) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(rad) * radius);
            int y = cy + (int) Math.round(Math.sin(rad) * radius);
            if (y > top(x, z)) {
                kit.set(x, y, z, bone);
                kit.set(x, y, z + 1, bone);
            }
        }
    }

    private static void boulder(BuildKit kit, int cx, int cz) {
        int base = top(cx, cz);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    if (x * x + z * z + y * y * 2 <= 5 + kit.random.nextInt(2)) {
                        kit.set(cx + x, base + 1 + y, cz + z, kit.random.nextFloat() < 0.6f
                                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    }
                }
            }
        }
    }

    // 尾の台: 到着地点の広場。円形の石畳 (外はレンガ、中ほどは市松と線、中央は扇形の模様)、
    // 北の骨の門 (遺跡の方角)、錨石の祠、縁の柵、灯りの柱と旗、ベンチ、焚き火の休み場、三つの試練の碑文を置く
    // 到着は (0, 107, 94)、錨石は (0, 106, 99)。どちらも動かさない
    private static final double PLAZA_RX = 7.0;
    private static final double PLAZA_RZ = 9.0;

    private static double plazaDistance(int x, int z) {
        return Math.hypot(x / PLAZA_RX, (z - PLATFORM_Z) / PLAZA_RZ);
    }

    // 北の門の前後 (柵を置かず、石畳の道をあける)
    private static boolean plazaGate(int x, int z) {
        return Math.abs(x) <= 4 && z <= PLATFORM_Z - 6;
    }

    private static void buildPlatform(BuildKit kit) {
        int py = platformY();
        RandomSource random = kit.random;
        // 円形の石畳と、下の埋め
        for (int x = -8; x <= 8; x++) {
            for (int z = PLATFORM_Z - 10; z <= PLATFORM_Z + 10; z++) {
                double d = plazaDistance(x, z);
                if (d > 1.0) {
                    continue;
                }
                kit.fill(x, Math.min(py - 3, top(x, z)), z, x, py - 1, z, Blocks.DEEPSLATE.defaultBlockState());
                kit.set(x, py, z, plazaFloor(x, z, d, random));
                kit.fill(x, py + 1, z, x, py + 6, z, BuildKit.AIR);
                if (d < 0.84 && random.nextFloat() < 0.06f) {
                    kit.set(x, py + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        // 縁の柵: 石畳の外へ面した所に、低い石の柵を巡らす (北の門の前は除く)
        for (int x = -8; x <= 8; x++) {
            for (int z = PLATFORM_Z - 10; z <= PLATFORM_Z + 10; z++) {
                if (plazaDistance(x, z) > 1.0 || plazaGate(x, z)) {
                    continue;
                }
                boolean edge = plazaDistance(x + 1, z) > 1.0 || plazaDistance(x - 1, z) > 1.0
                        || plazaDistance(x, z + 1) > 1.0 || plazaDistance(x, z - 1) > 1.0;
                if (edge) {
                    kit.set(x, py + 1, z, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        buildGate(kit, py);
        buildShrine(kit, py);
        // 縁の柱: 灯りを吊るす柱と、旗を立てる柱を交互に
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = (int) Math.round(6.0 * Math.cos(a));
            int z = PLATFORM_Z + (int) Math.round(7.7 * Math.sin(a));
            if (plazaGate(x, z) || (x == 0 && z == PLATFORM_Z - 8)) {
                continue;
            }
            for (int y = py + 1; y <= py + 3; y++) {
                kit.set(x, y, z, BuildKit.POLISHED);
            }
            if (k == 0 || k == 4) {
                kit.set(x, py + 4, z, Blocks.LIGHT_BLUE_BANNER.defaultBlockState());
            } else {
                kit.lantern(x, py + 4, z);
            }
        }
        // 休みのベンチ (入口の両脇)
        kit.set(-4, py + 1, PLATFORM_Z - 3, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        kit.set(4, py + 1, PLATFORM_Z - 3, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        // 魂の焚き火の休み場 (祠の脇): 焚き火と、そのまわりのベンチ
        kit.set(-4, py + 1, PLATFORM_Z + 3, Blocks.SOUL_CAMPFIRE.defaultBlockState()
                .setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.set(-5, py + 1, PLATFORM_Z + 3, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        kit.set(-4, py + 1, PLATFORM_Z + 4, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        // 尾の台には案内の看板を置かない。錨石と炉の使い方は、着いた最初の一度だけ囁きで伝える (BackWhispers)
        kit.set(forge(), ModBlocks.RESONANCE_FORGE.get().defaultBlockState().setValue(ResonanceForgeBlock.FACING, Direction.EAST));
    }

    // 円形の石畳の模様。縁はレンガ、一つ内側に彫刻の線、その内側は市松、中央は扇形の模様
    private static BlockState plazaFloor(int x, int z, double d, RandomSource random) {
        if (d > 0.84) {
            return random.nextFloat() < 0.15f ? BuildKit.CRACKED : BuildKit.BRICKS;
        }
        if (d > 0.56) {
            return (x + z) % 2 == 0 ? BuildKit.POLISHED : BuildKit.TILES;
        }
        if (d > 0.5) {
            return BuildKit.CHISELED;
        }
        if (d < 0.16) {
            return BuildKit.CHISELED;
        }
        double a = Math.atan2(z - PLATFORM_Z, x);
        int sector = Math.floorMod((int) Math.floor((a + Math.PI) / (Math.PI / 4)), 8);
        return sector % 2 == 0 ? BuildKit.TILES : BuildKit.POLISHED;
    }

    // 北の骨の門: 遺跡のほうへ抜ける、鯨の肋骨の門。中央に魂のランタンを吊るす
    private static void buildGate(BuildKit kit, int py) {
        int gz = PLATFORM_Z - 7;
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState beam = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x : new int[]{-3, 3}) {
            for (int y = py + 1; y <= py + 3; y++) {
                kit.set(x, y, gz, bone);
            }
            kit.set(x, py + 4, gz, bone);
        }
        for (int x = -2; x <= 2; x++) {
            kit.set(x, py + 5, gz, beam);
        }
        kit.hangingLantern(0, gz, py + 5, 1);
    }

    // 錨石の祠: 錨石 (石の磁石) を、磨いた石の台と、後ろの壁と屋根で囲む。屋根から鎖と魂のランタンを下げる
    private static void buildShrine(BuildKit kit, int py) {
        for (int x = -1; x <= 1; x++) {
            for (int z = PLATFORM_Z + 2; z <= PLATFORM_Z + 4; z++) {
                kit.set(x, py, z, BuildKit.CHISELED);
            }
        }
        kit.set(anchor(), Blocks.LODESTONE.defaultBlockState());
        for (int side = -1; side <= 1; side += 2) {
            for (int y = py + 1; y <= py + 3; y++) {
                kit.set(2 * side, y, PLATFORM_Z + 4, BuildKit.POLISHED);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = PLATFORM_Z + 3; z <= PLATFORM_Z + 5; z++) {
                kit.set(x, py + 4, z, BuildKit.POLISHED);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = py + 1; y <= py + 3; y++) {
                kit.set(x, y, PLATFORM_Z + 5, BuildKit.BRICKS);
            }
        }
        kit.set(0, py + 2, PLATFORM_Z + 5, BuildKit.CHISELED);
        // 鎖: 屋根の下から、錨石の上へ。中央には魂のランタン
        kit.set(-2, py + 3, PLATFORM_Z + 3, Blocks.CHAIN.defaultBlockState());
        kit.set(2, py + 3, PLATFORM_Z + 3, Blocks.CHAIN.defaultBlockState());
        kit.hangingLantern(0, PLATFORM_Z + 3, py + 4, 1);
    }


    // ---------------------------------------------------------------- 祠と体内への階段
    private static void buildShrineAndStairs(BuildKit kit) {
        int sy = shrineY();
        int z1 = SHRINE_Z - 10;
        int z2 = SHRINE_Z + 10;
        for (int x = -5; x <= 5; x++) {
            for (int z = z1; z <= z2; z++) {
                kit.fill(x, sy - 3, z, x, sy - 1, z, Blocks.TUFF.defaultBlockState());
                boolean border = Math.abs(x) == 5 || z == z1 || z == z2;
                kit.set(x, sy, z, border ? BuildKit.TILES : BuildKit.POLISHED);
                kit.fill(x, sy + 1, z, x, sy + 8, z, BuildKit.AIR);
            }
        }
        // 四隅と中ほどの柱、二段の屋根
        for (int x : new int[]{-5, 5}) {
            for (int z : new int[]{z1, SHRINE_Z, z2}) {
                for (int y = sy + 1; y <= sy + 5; y++) {
                    kit.set(x, y, z, y == sy + 5 ? BuildKit.CHISELED : BuildKit.POLISHED);
                }
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if ((x * 7 + z * 13) % 6 != 0) {
                    kit.set(x, sy + 6, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                }
                if (Math.abs(x) <= 3 && z >= z1 + 3 && z <= z2 - 3) {
                    kit.set(x, sy + 7, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                }
            }
        }
        kit.hangingLantern(0, SHRINE_Z + 6, sy + 6, 1);
        // 北へ下りていく階段。1段ごとに1つ低くなり、体内の床の高さで終わる
        BlockState step = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int i = 0; ; i++) {
            int z = SHRINE_Z - 1 - i;
            int y = sy - 1 - i;
            if (y < FLOOR) {
                break;
            }
            for (int x = -2; x <= 2; x++) {
                boolean inside = Math.abs(x) <= 1;
                for (int yy = y; yy <= y + 4; yy++) {
                    if (inside) {
                        kit.set(x, yy, z, yy == y ? (y == FLOOR ? BuildKit.POLISHED : step) : BuildKit.AIR);
                    } else if (yy < sy && !kit.get(x, yy, z).isAir()) {
                        kit.set(x, yy, z, i % 4 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : BuildKit.BRICKS);
                    }
                }
            }
            if (i % 6 == 3 && y + 5 < sy) {
                kit.lantern(2, y + 2, z);
            }
        }
    }

    // ---------------------------------------------------------------- 鼓動の間
    private static void buildHub(BuildKit kit) {
        kit.room(HUB);
        kit.room(ENTRY);
        // 西・東の翼へ向かう通路
        kit.room(new Box(-15, -10, -23, -21, FLOOR, 4));
        kit.room(new Box(10, 15, -23, -21, FLOOR, 4));
        kit.ribs(HUB, 4, true);
        kit.ribs(ENTRY, 3, true);
        kit.floorBorder(HUB);
        // 中央: 眠っている心臓の台座と、それを囲む模様
        kit.set(0, FLOOR + 1, -22, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        kit.set(0, FLOOR + 2, -22, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            kit.set(d.getStepX() * 2, FLOOR + 1, -22 + d.getStepZ() * 2,
                    Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -25; z <= -19; z++) {
                int r = Math.max(Math.abs(x), Math.abs(z + 22));
                if (r == 3 || (r == 1 && (x == 0 || z == -22))) {
                    kit.set(x, FLOOR, z, BuildKit.CHISELED);
                }
            }
        }
        for (int[] l : new int[][]{{-5, -16}, {5, -16}, {-5, -28}, {5, -28}, {0, -13}, {-5, -22}, {5, -22}, {0, -30}}) {
            kit.hangingLantern(l[0], l[1], HUB.ceilingY(), 2);
        }
        kit.lantern(-1, FLOOR + 1, -3);
        kit.lantern(1, FLOOR + 1, 2);
        kit.moss(HUB, 0.04f);
        for (int[] s : new int[][]{{-7, -14}, {7, -30}, {-6, -30}, {7, -15}}) {
            kit.stalactite(s[0], HUB.ceilingY(), s[1], 2);
        }
        // 看板は試練の入口の一言だけ (西・東・下)。封印の扉のことは、鼓動の間に入った最初の一度だけ囁く (BackWhispers)
        for (Inscription s : TRIAL_SIGNS) {
            kit.wallSign(s.pos(), s.facing(), s.key());
        }
        // 西・東の石を鳴らすと開く抜け道 (鼓動の間の側)
        for (BlockPos p : WEST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        BlockState ladderEast = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = FLOOR + 1; y <= 98; y++) {
            kit.set(-9, y, -31, ladderEast);
        }
        for (BlockPos p : EAST_RETURN) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- 宝物庫
    private static void buildTreasury(BuildKit kit) {
        // 壁はほかの部屋と同じ深層岩。島ではブロックを壊せないので、特別な石で囲む必要はない
        kit.room(TREASURY);
        kit.ribs(TREASURY, 3, true);
        kit.floorBorder(TREASURY);
        for (int x = TREASURY.x1(); x <= TREASURY.x2(); x++) {
            for (int z = TREASURY.z1(); z <= TREASURY.z2(); z++) {
                if ((x + z) % 2 == 0) {
                    kit.set(x, FLOOR, z, BuildKit.CHISELED);
                }
            }
        }
        // 宝物庫の封印 (鼓動の間との境の壁) と、奥の扉
        for (BlockPos p : GATE) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        for (BlockPos p : INNER_GATE) {
            kit.set(p, ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        // 眠る台座 (飾り): 磁石石を、紫水晶とろうそくが囲む。欠片は心臓を倒すと受け取れる
        kit.set(PEDESTAL.below(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        kit.set(PEDESTAL, Blocks.LODESTONE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos c = PEDESTAL.relative(d);
            kit.set(c, d.getAxis() == Direction.Axis.X ? Blocks.AMETHYST_CLUSTER.defaultBlockState()
                    : Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        }
        for (int[] l : new int[][]{{-4, -36}, {4, -36}, {-4, -42}, {4, -42}}) {
            kit.hangingLantern(l[0], l[1], TREASURY.ceilingY(), 1);
        }
    }

    static BlockState sealed() {
        return ModBlocks.SEALED_GATE.get().defaultBlockState();
    }
}
