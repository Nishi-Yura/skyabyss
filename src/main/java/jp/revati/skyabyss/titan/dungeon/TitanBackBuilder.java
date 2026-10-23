package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BackBuildJobs;
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

    // その場で最後まで建てる。起動時の予約 (schedule) が途中なら、その続きを最後まで進める (最初からやり直さない)
    public static void build(ServerLevel level, TitanBackData data) {
        if (BackBuildJobs.finishNow(level.dimension()) && !data.needsBuild()) {
            return;
        }
        long start = System.currentTimeMillis();
        BuildKit kit = new BuildKit(level, RandomSource.create(20261015L));
        // アスレチックと迷路の形は、建てるたびに新しい種で決める
        data.reseed(level.getRandom());
        buildIsland(kit);
        BackBuildJobs.Steps rest = restSteps(level, data, kit, start, null);
        while (!rest.step(Long.MAX_VALUE)) {
            // 最後まで
        }
    }

    // 起動時: 島の体を何 tick かに分けて置き、そのあとの仕上げも手順ごとに tick に分けて建てる (BackBuildJobs。13 の 24.5節)
    public static void schedule(ServerLevel level, TitanBackData data) {
        BuildKit kit = new BuildKit(level, RandomSource.create(20261015L));
        data.reseed(level.getRandom());
        long start = System.currentTimeMillis();
        BackBuildJobs.schedule("stone whale back", level, restSteps(level, data, kit, start,
                () -> jp.revati.skyabyss.titan.BackBuildJobs.afterAsync(WhaleShape::preload, () -> WhaleShape.jobWithClear(kit))), () -> {
        });
    }

    // 体を置いたあとの仕上げ (部屋・中身・修復)。手順の順は前の一度に建てる形と同じ
    private static BackBuildJobs.Steps restSteps(ServerLevel level, TitanBackData data, BuildKit kit, long start,
            @org.jetbrains.annotations.Nullable java.util.function.Supplier<BackBuildJobs.Job> body) {
        BackBuildJobs.Steps s = new BackBuildJobs.Steps("stone whale back");
        if (body != null) {
            s.thenJob("body", body);
        }
        // 仕上げが触る範囲のチャンクを先に読み込む (読みに行くと、その場で tick が止まる)
        s.thenJob("load area", () -> BackBuildJobs.loadArea(level, -RX, -RZ, RX, RZ));
        s.then("surface", () -> buildSurface(kit));
        // 四方が 1 高い床の中で 1 マスだけ低いくぼみを埋める (背の殻の模型の窪みと、ひれの縁。体の外)
        s.then("dimples", () -> {
            smoothDimples(kit, -27, -17, 138, 152, 93, 103);
            smoothDimples(kit, 54, 120, 70, 100, -75, -25);
            smoothDimples(kit, -120, -54, 70, 112, -75, -25);
        });
        // 心臓の間 (体の外の封じた部屋。trial/raid/InstanceArenas)
        jp.revati.skyabyss.trial.raid.InstanceArenas.addHeartSteps(s, level, jp.revati.skyabyss.progress.Titans.STONE_WHALE);
        s.then("mark built", () -> {
            data.markBuilt();
            SkyAbyss.LOGGER.info("Built the stone whale's back in {} ms", System.currentTimeMillis() - start);
        });
        return s;
    }

    // 床 (上が 2 マス空いた固いブロック) で、四方の隣の 1 つ上の高さに固いブロックがあるものは、1 つ低く埋まったくぼみなので、1 つ上を同じブロックで埋める
    private static void smoothDimples(BuildKit kit, int x1, int x2, int y1, int y2, int z1, int z2) {
        int filled = 0;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = y1; y <= y2; y++) {
                    BlockState here = kit.get(x, y, z);
                    if (here.isAir() || !here.getFluidState().isEmpty() || !here.canOcclude() || !kit.get(x, y + 1, z).isAir() || !kit.get(x, y + 2, z).isAir()) {
                        continue;
                    }
                    boolean pit = true;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int nx = x + d.getStepX();
                        int nz = z + d.getStepZ();
                        if (kit.get(nx, y + 1, nz).isAir() || !kit.get(nx, y + 2, nz).isAir() || !kit.get(nx, y + 3, nz).isAir() || !kit.get(nx, y + 4, nz).isAir()) {
                            pit = false;
                            break;
                        }
                    }
                    if (pit && kit.get(x, y + 3, z).isAir()) {
                        kit.set(x, y + 1, z, here);
                        filled++;
                    }
                }
            }
        }
        if (filled > 0) {
            SkyAbyss.LOGGER.info("Whale back: filled {} floor dimples", filled);
        }
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
        for (int z = SHRINE_Z + 17; z <= PLATFORM_Z - 14; z++) {
            for (int x = -2; x <= 2; x++) {
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
        for (int z : new int[]{102, 134}) {
            int base = top(5, z) + 1;
            kit.set(5, base, z, BuildKit.POLISHED);
            kit.set(5, base + 1, z, BuildKit.POLISHED);
            kit.set(5, base + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // 錨石 (左のわき腹の鉄の留め具) から、雲の海へ垂れる鎖。乗り込むときに上ってくる鎖
        anchorChain(kit);
        buildPlatform(kit);
        // 到着の台 (錨石の外) と、そこから背の広場へ体の中を登る階段
        buildFlankPlatform(kit);
    }

    // モデルの錨石 (左のわき腹の鉄の留め具) から、外へ張り出して雲の海の少し上まで垂れる鎖
    // (地上で岩鯨が垂らす鎖と同じく、胴の外へ渡してから下ろす)
    private static void anchorChain(BuildKit kit) {
        // 錨石から外へ渡す腕は、骨の梁 (固い) にする。垂れる鎖は、その梁の先から途切れずに下りる (宙から鎖がぶら下がらない)
        BlockState arm = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState down = Blocks.CHAIN.defaultBlockState();
        int y = ANCHOR_CHAIN_Y;
        int z = ANCHOR_CHAIN_Z;
        for (int x = ANCHOR_STONE_X; x >= ANCHOR_CHAIN_X; x--) {
            if (kit.get(x, y + 1, z).isAir()) {
                kit.set(x, y + 1, z, arm);
            }
        }
        for (int yy = y; yy >= BASE_Y - 42; yy--) {
            kit.set(ANCHOR_CHAIN_X, yy, z, down);
        }
    }

    // 尾の付け根の広場: 到着地点。円形の石畳 (外はレンガ、中ほどは市松と線、中央は扇形の模様)、
    // 北の骨の門 (祠の方角)、錨石の祠、縁の柵、灯りの柱と旗、ベンチ、焚き火の休み場を置く。
    // 背の丸みは、石畳の下を埋め、上を空けて平らにする。到着は (0, 123, 98)、錨石は (0, 122, 103)
    private static final double PLAZA_RX = 11.0;
    private static final double PLAZA_RZ = 14.0;

    private static double plazaDistance(int x, int z) {
        return Math.hypot(x / PLAZA_RX, (z - PLATFORM_Z) / PLAZA_RZ);
    }

    // 北の門の前後 (柵を置かず、石畳の道をあける)
    private static boolean plazaGate(int x, int z) {
        return Math.abs(x) <= 6 && z <= PLATFORM_Z - 9;
    }

    private static void buildPlatform(BuildKit kit) {
        int py = platformY();
        RandomSource random = kit.random;
        // 円形の石畳と、下の埋め
        for (int x = -12; x <= 12; x++) {
            for (int z = PLATFORM_Z - 15; z <= PLATFORM_Z + 15; z++) {
                double d = plazaDistance(x, z);
                if (d > 1.0) {
                    continue;
                }
                kit.fill(x, Math.min(py - 3, top(x, z)), z, x, py - 1, z, Blocks.DEEPSLATE.defaultBlockState());
                kit.set(x, py, z, plazaFloor(x, z, d, random));
                kit.fill(x, py + 1, z, x, py + 8, z, BuildKit.AIR);
                if (d < 0.84 && random.nextFloat() < 0.06f) {
                    kit.set(x, py + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        // 縁の柵: 石畳の外へ面した所に、低い石の柵を巡らす (北の門の前は除く)
        for (int x = -12; x <= 12; x++) {
            for (int z = PLATFORM_Z - 15; z <= PLATFORM_Z + 15; z++) {
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
            int x = (int) Math.round(9.5 * Math.cos(a));
            int z = PLATFORM_Z + (int) Math.round(12.3 * Math.sin(a));
            if (plazaGate(x, z) || (x == 0 && z == PLATFORM_Z - 12)) {
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
        kit.set(-7, py + 1, PLATFORM_Z - 5, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        kit.set(7, py + 1, PLATFORM_Z - 5, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        // 魂の焚き火の休み場 (祠の脇): 焚き火と、そのまわりのベンチ
        kit.set(-7, py + 1, PLATFORM_Z + 6, jp.revati.skyabyss.relic.RelicBlocks.hearth(jp.revati.skyabyss.relic.RelicStyle.WHALE, true));
        kit.set(-8, py + 1, PLATFORM_Z + 6, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        kit.set(-7, py + 1, PLATFORM_Z + 7, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
        // 広場の錨石の祠の台は飾り (錨石は左のわき腹の台にある)。案内の看板は置かない
    }

    // 左のわき腹の台 (到着): 錨石 (モデルの鉄の留め具) の外へ張り出した石の台。床は空だった所だけに置く (留め具と体はそのまま)。
    // 北・南・西の縁に低い柵 (鎖の垂れる所は開ける)、四隅に灯り。錨石 (石の磁石) を留め具の外の面にく。
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
        for (int x : new int[]{FLANK_X0, FLANK_X0 + 9}) {
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
        int gz = PLATFORM_Z - 11;
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState beam = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x : new int[]{-5, 5}) {
            for (int y = py + 1; y <= py + 5; y++) {
                kit.set(x, y, gz, bone);
            }
            kit.set(x, py + 6, gz, bone);
        }
        for (int x = -4; x <= 4; x++) {
            kit.set(x, py + 7, gz, beam);
        }
        kit.hangingLantern(0, gz, py + 7, 1);
    }

    // 錨石の祠: 錨石 (石の磁石) を、磨いた石の台と、後ろの壁と屋根で囲む。屋根から鎖と魂のランタンを下げる
    private static void buildShrine(BuildKit kit, int py) {
        for (int x = -2; x <= 2; x++) {
            for (int z = PLATFORM_Z + 3; z <= PLATFORM_Z + 7; z++) {
                kit.set(x, py, z, BuildKit.CHISELED);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = py + 1; y <= py + 5; y++) {
                kit.set(3 * side, y, PLATFORM_Z + 7, BuildKit.POLISHED);
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = PLATFORM_Z + 4; z <= PLATFORM_Z + 8; z++) {
                kit.set(x, py + 6, z, BuildKit.POLISHED);
            }
        }
        // 後ろの壁は置かない (広場から尾と尾びれが見えるように)。屋根を支える柱だけ
        // 鎖: 屋根の下から、錨石の上へ。中央には魂のランタン
        kit.set(-2, py + 5, PLATFORM_Z + 5, Blocks.CHAIN.defaultBlockState());
        kit.set(2, py + 5, PLATFORM_Z + 5, Blocks.CHAIN.defaultBlockState());
        kit.hangingLantern(0, PLATFORM_Z + 5, py + 6, 1);
    }

    static BlockState sealed() {
        return ModBlocks.SEALED_GATE.get().defaultBlockState();
    }
}
