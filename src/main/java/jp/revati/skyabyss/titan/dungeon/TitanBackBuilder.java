package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BackBuildJobs;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の背を建てる。ワールドで初めて必要になったとき (サーバー起動時) に一度だけ実行する。
// 配置は TitanBackLayout を参照。ここでは島そのもの・地表の遺跡・祠・鼓動の間・宝物庫を建て、
// 西の翼・東の翼・闇の水路はそれぞれの Builder に任せる
public final class TitanBackBuilder {
    private TitanBackBuilder() {
    }

    // その場で最後まで建てる。起動時の予約 (schedule) が途中なら取り消す (置いた分は「変わるブロックだけ」なので飛ばせる)
    public static void build(ServerLevel level, TitanBackData data) {
        BackBuildJobs.cancel(level.dimension());
        long start = System.currentTimeMillis();
        BuildKit kit = new BuildKit(level, RandomSource.create(20261015L));
        // アスレチックと迷路の形は、建てるたびに新しい種で決める
        data.reseed(level.getRandom());
        buildIsland(kit);
        buildRest(level, data, kit, start);
    }

    // 起動時: 島の体を何 tick かに分けて置き、置き終えた tick に残りを建てる (BackBuildJobs。13 の 24.5節)
    public static void schedule(ServerLevel level, TitanBackData data) {
        BuildKit kit = new BuildKit(level, RandomSource.create(20261015L));
        data.reseed(level.getRandom());
        BackBuildJobs.schedule("stone whale back", level, WhaleShape.job(kit), () -> buildRest(level, data, kit, System.currentTimeMillis()));
    }

    private static void buildRest(ServerLevel level, TitanBackData data, BuildKit kit, long start) {
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
        // 休み場の復活位置に苔の敷物が落ちていたら除く (立てる所を空けておく)
        for (BlockPos spawn : TitanBackTrials.checkpointSpawns().values()) {
            if (kit.get(spawn.getX(), spawn.getY(), spawn.getZ()).is(Blocks.MOSS_CARPET)) {
                kit.set(spawn, BuildKit.AIR);
            }
        }
        // 背・ひれの上の、落ちたら出られない凹み (詰み監査で見つけて埋める。部屋・通路の中は直さずに報告に残す)
        int repaired = jp.revati.skyabyss.titan.audit.BackReach.repair(level, TitanBackAudit::spec,
                java.util.List.of(new jp.revati.skyabyss.titan.audit.BackReach.Box(-RX, 99, -RZ, RX, 175, RZ)), 200, 14, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        if (repaired > 0) {
            SkyAbyss.LOGGER.info("Whale back: filled {} blocks of dead-end pockets on the outside", repaired);
        }
        data.markBuilt();
        SkyAbyss.LOGGER.info("Built the stone whale's back in {} ms", System.currentTimeMillis() - start);
    }

    // ---------------------------------------------------------------- 島: 岩鯨と同じ形のブロックの鯨 (WhaleShape)
    private static void buildIsland(BuildKit kit) {
        long t0 = System.currentTimeMillis();
        int placed = WhaleShape.place(kit);
        SkyAbyss.LOGGER.info("Placed the stone whale: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
    }

    // ---------------------------------------------------------------- 背の上: 広場から祠への踏み跡、道の灯り、錨石から垂れる鎖、尾の広場
    // 背の上の景色 (岩の殻、背骨の骨、半ば埋まった石の門のアーチ、折れた柱、岩の板) はモデルのまま。ここでは道しるべだけを足す
    private static void buildSurface(BuildKit kit) {
        RandomSource random = kit.random;
        // 祠から広場までの踏み跡 (苔の上の石)。背骨 (x -3) の東に沿う
        for (int z = SHRINE_Z + 11; z <= PLATFORM_Z - 9; z++) {
            for (int x = -1; x <= 1; x++) {
                if (random.nextFloat() < 0.7f) {
                    BlockState path = switch (random.nextInt(3)) {
                        case 0 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                        case 1 -> Blocks.TUFF_BRICKS.defaultBlockState();
                        default -> Blocks.POLISHED_TUFF.defaultBlockState();
                    };
                    kit.set(x, top(x, z), z, path);
                }
            }
        }
        // 踏み跡の脇の灯りの柱 (石の門のアーチ z 48〜60 は避ける)
        for (int z : new int[]{64, 84}) {
            int base = top(3, z) + 1;
            kit.set(3, base, z, BuildKit.POLISHED);
            kit.set(3, base + 1, z, BuildKit.POLISHED);
            kit.set(3, base + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // 錨石 (左のわき腹の鉄の留め具) から、雲の海へ垂れる鎖。乗り込むときに上ってくる鎖
        anchorChain(kit);
        buildPlatform(kit);
        // 到着の台 (錨石の外) と、そこから背の広場へ体の中を登る階段
        buildFlankPlatform(kit);
        buildClimb(kit);
    }

    // モデルの錨石 (左のわき腹の鉄の留め具) から、外へ張り出して雲の海の少し上まで垂れる鎖
    // (地上で岩鯨が垂らす鎖と同じく、胴の外へ渡してから下ろす)
    private static void anchorChain(BuildKit kit) {
        BlockState along = Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState down = Blocks.CHAIN.defaultBlockState();
        int y = ANCHOR_CHAIN_Y;
        int z = ANCHOR_CHAIN_Z;
        for (int x = ANCHOR_STONE_X; x > ANCHOR_CHAIN_X; x--) {
            if (kit.get(x, y, z).isAir()) {
                kit.set(x, y, z, along);
            }
        }
        for (int yy = y; yy >= BASE_Y - 42; yy--) {
            kit.set(ANCHOR_CHAIN_X, yy, z, down);
        }
    }

    // 尾の付け根の広場: 到着地点。円形の石畳 (外はレンガ、中ほどは市松と線、中央は扇形の模様)、
    // 北の骨の門 (祠の方角)、錨石の祠、縁の柵、灯りの柱と旗、ベンチ、焚き火の休み場を置く。
    // 背の丸みは、石畳の下を埋め、上を空けて平らにする。到着は (0, 123, 98)、錨石は (0, 122, 103)
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
        // 階段の facing は背の高い側 (背もたれ)。ベンチは背もたれを道の外へ向ける
        kit.set(-4, py + 1, PLATFORM_Z - 3, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        kit.set(4, py + 1, PLATFORM_Z - 3, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        // 魂の焚き火の休み場 (祠の脇): 焚き火と、そのまわりのベンチ
        kit.set(-4, py + 1, PLATFORM_Z + 3, Blocks.SOUL_CAMPFIRE.defaultBlockState()
                .setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.set(-5, py + 1, PLATFORM_Z + 3, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        kit.set(-4, py + 1, PLATFORM_Z + 4, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        // 広場の錨石の祠の台は飾り (錨石は左のわき腹の台にある)。案内の看板は置かない (BackWhispers)
    }

    // 左のわき腹の台 (到着): 錨石 (モデルの鉄の留め具) の外へ張り出した石の台。床は空だった所だけに置く (留め具と体はそのまま)。
    // 北・南・西の縁に低い柵 (鎖の垂れる所は開ける)、四隅に灯り。錨石 (石の磁石) を留め具の外の面に、共鳴の炉を北に置く。
    // 台の北の端 (z STAIR_Z0〜STAIR_Z1) から、東の体の面へ登りの階段が始まる (buildClimb)
    private static void buildFlankPlatform(BuildKit kit) {
        int fy = FLANK_FLOOR_Y;
        RandomSource random = kit.random;
        for (int x = FLANK_X0; x <= FLANK_X1; x++) {
            for (int z = FLANK_Z0; z <= FLANK_Z1; z++) {
                if (!kit.get(x, fy, z).isAir()) {
                    continue;
                }
                boolean edge = x == FLANK_X0 || z == FLANK_Z0 || z == FLANK_Z1;
                kit.set(x, fy, z, edge ? BuildKit.BRICKS : (x + z) % 2 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
                // 台の下: 縁は二段、中は一段の石組み (宙に薄い板が浮かないように)
                if (kit.get(x, fy - 1, z).isAir()) {
                    kit.set(x, fy - 1, z, edge ? BuildKit.CRACKED : Blocks.DEEPSLATE.defaultBlockState());
                }
                if (edge && kit.get(x, fy - 2, z).isAir() && random.nextFloat() < 0.7f) {
                    kit.set(x, fy - 2, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            }
        }
        // 縁の柵 (西・北・南)。鎖の垂れる西の縁 (z 108〜112) は、鎖が見えるよう低い柵にしない
        for (int x = FLANK_X0; x <= FLANK_X1; x++) {
            for (int z = FLANK_Z0; z <= FLANK_Z1; z++) {
                boolean edge = x == FLANK_X0 || z == FLANK_Z0 || z == FLANK_Z1;
                boolean chainGap = x == FLANK_X0 && z >= ANCHOR_CHAIN_Z - 2 && z <= ANCHOR_CHAIN_Z + 2;
                if (edge && !chainGap && kit.get(x, fy, z).is(BuildKit.BRICKS.getBlock())) {
                    kit.set(x, fy + 1, z, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        // 隅の灯りの柱
        for (int x : new int[]{FLANK_X0, FLANK_X0 + 6}) {
            for (int z : new int[]{FLANK_Z0, FLANK_Z1}) {
                if (kit.get(x, fy, z).isAir()) {
                    continue;
                }
                kit.set(x, fy + 1, z, BuildKit.POLISHED);
                kit.set(x, fy + 2, z, BuildKit.POLISHED);
                kit.set(x, fy + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
        // 錨石 (石の磁石) を留め具の外の面に。鎖の根元の下
        kit.set(anchor(), Blocks.LODESTONE.defaultBlockState());
        kit.set(forge(), ModBlocks.RESONANCE_FORGE.get().defaultBlockState().setValue(ResonanceForgeBlock.FACING, Direction.EAST));
    }

    // 台から背の広場への登りの階段: 体の中を東へ、1 ブロックごとに 1 上がる (幅 3・高さ 4)。広場の石畳 (南西) へ出る。
    // 体の面に近い所は、背の殻を割って階段が背へ出る。灯りを段ごとに交互に吊るす
    private static void buildClimb(BuildKit kit) {
        int steps = stairSteps();
        BlockState up = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        for (int i = 0; i < steps; i++) {
            int x = STAIR_X0 + i;
            int feet = FLANK_FLOOR_Y + 1 + i;
            for (int z = STAIR_Z0; z <= STAIR_Z1; z++) {
                kit.fill(x, feet, z, x, feet + 3, z, BuildKit.AIR);
                if (i > 0) {
                    kit.set(x, feet - 1, z, up);
                } else if (kit.get(x, feet - 1, z).isAir()) {
                    kit.set(x, feet - 1, z, BuildKit.POLISHED);
                }
            }
            // 灯り: 6 段ごとに、天井の中央から
            if (i > 0 && i % 6 == 3 && !kit.get(x, feet + 4, STAIR_Z0 + 1).isAir()) {
                kit.set(x, feet + 3, STAIR_Z0 + 1, lantern);
            }
        }
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
        kit.set(0, py, PLATFORM_Z + 3, BuildKit.CHISELED);
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
        // 後ろの壁は置かない (広場から尾と尾びれが見えるように)。屋根を支える柱だけ
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
        // 南の口 (広場から来る踏み跡) の段: 背は南へ下がっていくので、祠の床まで一段ずつ上がる段を置く
        BlockState up = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int i = 1; i <= 8; i++) {
            int z = z2 + i;
            int y = sy - i;
            boolean any = false;
            for (int x = -2; x <= 2; x++) {
                int t = top(x, z);
                if (t >= y) {
                    continue;
                }
                any = true;
                kit.fill(x, t + 1, z, x, y - 1, z, BuildKit.BRICKS);
                kit.set(x, y, z, up);
                kit.fill(x, y + 1, z, x, y + 4, z, BuildKit.AIR);
            }
            if (!any) {
                break;
            }
        }
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
        // はしごの頂は 97 まで。98 (戸と同じ高さ) だと、支えの壁が戸になって、戸が開くと浮いてしまう。頂の隣 (-10, 98〜99) が戸 = 出口
        for (int y = FLOOR + 1; y <= 97; y++) {
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
