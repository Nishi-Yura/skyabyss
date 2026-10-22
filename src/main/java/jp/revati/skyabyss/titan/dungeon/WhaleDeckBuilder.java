package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.FLOOR;
import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.*;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.trial.whale.W06Score;
import jp.revati.skyabyss.trial.RingArenaCell;
import jp.revati.skyabyss.trial.whale.W03Moss;
import jp.revati.skyabyss.trial.whale.W04Vents;
import jp.revati.skyabyss.trial.whale.W07Gates;
import jp.revati.skyabyss.trial.whale.W10Fort;
import jp.revati.skyabyss.trial.whale.W11Coffin;
import jp.revati.skyabyss.trial.whale.W12Arena;
import jp.revati.skyabyss.trial.whale.W08Shadows;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;

// 試練の階 (WhaleDeck。docs/design/29) を建てる。鼓動の間のあと・ボス部屋の大門より前に、TitanBackBuilder から呼ぶ。
// 部屋を掘る → 戸口・階段 → セルごとの仕掛け (梁・肋骨・取っ手・高台・石) → 休み場・板。戸 (封印の扉) は TrialRuntime.installGates が置く
// (戸口は空けておく)。体の中は詰まった石なので、掘るだけでよい (階段・部屋の壁の輪が体の中に収まることは WhaleDeckSelfTest が見る)
final class WhaleDeckBuilder {
    private WhaleDeckBuilder() {
    }

    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState GRAY = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState BLACK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState PAD = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();

    static void build(BuildKit kit) {
        long t0 = System.currentTimeMillis();
        for (TitanBackLayout.Box b : new TitanBackLayout.Box[] {D0, C1, S0, W06, W07, W10}) {
            kit.room(b);
        }
        // 戸口 (D0 → C1 → S0 は開いた通路。ほかは戸 = 入口の封印を、あとで置く)
        kit.doorway(40, 44, 25, 25, Y, 5);
        kit.doorway(40, 44, 67, 67, Y, 5);
        kit.doorway(39, 39, 32, 36, Y, 5);
        kit.doorway(-8, -4, 81, 82, Y, 5);
        kit.doorway(28, 32, 81, 82, Y, 5);
        buildStair(kit);
        buildD0(kit);
        buildW06(kit);
        buildW07(kit);
        buildW10(kit);
        buildSouth(kit);
        // S0・C1 の飾り
        kit.restPoint(S0);
        kit.pilasters(S0, 6);
        kit.ribs(S0, 5, true);
        kit.wallLamps(S0, 4, Y + 4);
        kit.ribs(C1, 4, true);
        kit.wallLamps(C1, 3, Y + 3);
        // 石の差し口と戻りの光る床
        kit.set(W06_STONE, jp.revati.skyabyss.trial.TrialGoal.mark());
        kit.set(W07_STONE, jp.revati.skyabyss.trial.TrialGoal.mark());
        kit.set(W10_STONE, jp.revati.skyabyss.trial.TrialGoal.mark());
        for (BlockPos stone : new BlockPos[] {W03_STONE, W04_STONE, W11_STONE, W12_STONE, W08_STONE}) {
            kit.set(stone, jp.revati.skyabyss.trial.TrialGoal.mark());
        }
        for (BlockPos pad : new BlockPos[] {W06_PAD, W07_PAD, W10_PAD, W03_PAD, W04_PAD, W11_PAD, W12_PAD, W08_PAD}) {
            kit.set(pad, PAD);
        }
        jp.revati.skyabyss.SkyAbyss.LOGGER.info("Built the whale trial deck in {} ms", System.currentTimeMillis() - t0);
    }

    // ---------------------------------------------------------------- 階段 (入口の通路の東の壁 → D0)
    // x 5〜20 は東へ上る階段 (1 段ごとに 1 上がる)、x 21 は D0 に面した平らな踊り場。幅 5 (z 8〜12)、頭上は 6
    private static void buildStair(BuildKit kit) {
        kit.doorway(STAIR_DOOR_X, STAIR_DOOR_X, STAIR_Z0, STAIR_Z1, FLOOR, 6);
        BlockState step = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM);
        for (int x = STAIR_X0; x <= LAND_X; x++) {
            int yb = x <= STAIR_X1 ? stairY(x) : Y;
            for (int z = STAIR_Z0; z <= STAIR_Z1; z++) {
                kit.set(x, yb, z, x <= STAIR_X1 ? step : BuildKit.POLISHED);
                kit.fill(x, yb + 1, z, x, yb + 6, z, BuildKit.AIR);
                if (!kit.get(x, yb + 7, z).isAir()) {
                    kit.set(x, yb + 7, z, BuildKit.BRICKS);
                }
            }
            for (int z : new int[] {STAIR_Z0 - 1, STAIR_Z1 + 1}) {
                for (int y = yb; y <= yb + 7; y++) {
                    if (!kit.get(x, y, z).isAir()) {
                        kit.set(x, y, z, kit.wallStone());
                    }
                }
            }
            // 壁の灯り (南の壁に、4 段ごと)
            if (x % 4 == 1 && x <= STAIR_X1) {
                kit.set(x, yb + 3, STAIR_Z1 + 1, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------- 試練の階の広間 D0
    private static void buildD0(BuildKit kit) {
        kit.restPoint(D0);
        kit.pilasters(D0, 6);
        kit.ribs(D0, 5, true);
        kit.wallLamps(D0, 4, Y + 4);
    }

    // ---------------------------------------------------------------- W06 骨の楽譜
    private static void buildW06(BuildKit kit) {
        for (int r = 0; r < W06Score.RIBS; r++) {
            int x = W06Score.RIB_X[r];
            kit.fill(x, Y + 1, W06Score.RIB_Z, x, Y + 3, W06Score.RIB_Z, BONE);
            kit.set(W06Score.cap(r), GRAY);
        }
        for (int k = 0; k < W06Score.RIBS; k++) {
            kit.set(W06Score.slotTarget(k), GRAY);
            kit.set(W06Score.slotProgress(k), BLACK);
        }
        kit.set(W06Score.LECTERN, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        // 石の小部屋 (南西の隅 x 24〜27、z 38〜42)。北の壁 z 37、東の戸 x 28 (戸の上は壁)
        int top = W06.floorY() + W06.height();
        kit.fill(24, Y + 1, 37, 28, top, 37, BuildKit.BRICKS);
        kit.fill(28, Y + 6, 38, 28, top, 42, BuildKit.BRICKS);
        kit.fill(28, Y + 1, 37, 28, top, 37, BuildKit.BRICKS);
        kit.lantern(26, Y + 1, 42);
        kit.ribs(W06, 5, true);
        kit.wallLamps(W06, 4, Y + 3);
    }

    // ---------------------------------------------------------------- W07 三つの水門
    private static void buildW07(BuildKit kit) {
        for (int k = 0; k < 4; k++) {
            int cx = W07Gates.PAD_X[k];
            int cz = W07Gates.PAD_Z[k];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.set(cx + dx, Y, cz + dz, dx == 0 && dz == 0 ? BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                }
            }
            kit.lantern(cx - 2, Y + 1, cz - 2);
            kit.lantern(cx + 2, Y + 1, cz + 2);
        }
        int top = W07.floorY() + W07.height();
        // 小部屋 (x -8〜-4、z 109〜111)。両脇の壁と、戸の上の壁
        kit.fill(-9, Y + 1, 108, -9, top, 111, BuildKit.BRICKS);
        kit.fill(-3, Y + 1, 108, -3, top, 111, BuildKit.BRICKS);
        kit.fill(-8, Y + 6, 108, -4, top, 108, BuildKit.BRICKS);
        for (int i = 0; i < 3; i++) {
            kit.set(W07Gates.lamp(i), BLACKSTONE);
        }
        kit.ribs(W07, 6, true);
        kit.wallLamps(W07, 4, Y + 4);
        for (int z : new int[] {92, 100}) {
            kit.hangingLantern(-6, z, W07.ceilingY(), 4);
        }
    }

    // ---------------------------------------------------------------- W10 骨の砲手の砦
    private static void buildW10(BuildKit kit) {
        BlockState stairN = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        for (int[] s : W10Fort.SLOTS) {
            kit.fill(s[0] - 1, Y + 1, s[1] - 1, s[0] + 1, Y + 1, s[1] + 1, BuildKit.BRICKS);
            kit.set(s[0], Y + 1, s[1] + 2, stairN);
            kit.lantern(s[0] - 1, Y + 2, s[1] - 1);
        }
        for (int[] c : W10Fort.COVERS) {
            kit.fill(c[0], Y + 1, c[2], c[1], Y + 3, c[2], BuildKit.BRICKS);
        }
        int top = W10.floorY() + W10.height();
        // 小部屋 (x 28〜32、z 112〜113)。両脇の壁と、戸の上の壁
        kit.fill(27, Y + 1, 111, 27, top, 113, BuildKit.BRICKS);
        kit.fill(33, Y + 1, 111, 33, top, 113, BuildKit.BRICKS);
        kit.fill(28, Y + 6, 111, 32, top, 111, BuildKit.BRICKS);
        deckRest(kit, REST_W10);
        kit.ribs(W10, 6, true);
        kit.wallLamps(W10, 4, Y + 4);
        for (int z : new int[] {90, 100}) {
            kit.hangingLantern(30, z, W10.ceilingY(), 4);
        }
    }

    // 決まった位置の休み場: 魂の焚き火と四方の腰掛け (背もたれは外へ)、足もとの灯り。床が無い (縁の外の) 所には置かない
    private static void deckRest(BuildKit kit, String id) {
        BlockPos c = campfire(id);
        int fy = c.getY() - 1;
        kit.set(c.getX(), fy, c.getZ(), BuildKit.CHISELED);
        kit.set(c, jp.revati.skyabyss.relic.RelicBlocks.hearth(jp.revati.skyabyss.relic.RelicStyle.WHALE, true));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int bx = c.getX() + d.getStepX() * 2;
            int bz = c.getZ() + d.getStepZ() * 2;
            // 縁の外が空いている (落ちる) 所には置かない
            if (!kit.get(bx, fy, bz).isAir() && kit.get(bx, fy + 1, bz).isAir() && !kit.get(bx + d.getStepX(), fy, bz + d.getStepZ()).isAir()) {
                kit.set(bx, fy + 1, bz, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM));
            }
        }
        kit.lantern(c.getX() + 1, fy + 1, c.getZ() - 1);
    }

    // ---------------------------------------------------------------- 版 21: 最南の広間 S1 と、新しい四つのセル
    private static final int BOTTOM_Y = 107;
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SEA_LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState GATE = ModBlocks.SEALED_GATE.get().defaultBlockState();

    // 掘る: 内側を空け、床・天井の石を敷く (まわりの壁には触れない。細い通路が隣の部屋の壁を挟むため)
    private static void carve(BuildKit kit, int x1, int x2, int z1, int z2, int floorY, int height) {
        kit.fill(x1, floorY + 1, z1, x2, floorY + height, z2, BuildKit.AIR);
        kit.fill(x1, floorY, z1, x2, floorY, z2, kit.pal().floor());
        kit.fill(x1, floorY + height + 1, z1, x2, floorY + height + 1, z2, kit.pal().ceiling());
    }

    private static void buildSouth(BuildKit kit) {
        // 細い通路 C2 (S0 の南の壁から S1 の北へ)・最南の広間 S1
        carve(kit, 10, 12, 81, 117, Y, 5);
        kit.room(S1);
        // 戸口 (S1 の南の壁は 2 枚)
        kit.doorway(-42, -38, 127, 128, Y, 5);
        kit.doorway(-22, -18, 127, 128, Y, 5);
        kit.doorway(0, 4, 127, 128, Y, 5);
        kit.doorway(22, 26, 127, 128, Y, 5);
        buildW08(kit);
        buildW03(kit);
        buildW04(kit);
        buildW11(kit);
        buildW12(kit);
        // 広間 S1・通路 C2 の飾りと灯り
        kit.restPoint(S1);
        kit.pilasters(S1, 6);
        kit.ribs(S1, 5, true);
        kit.wallLamps(S1, 4, Y + 4);
        for (int z = 84; z <= 116; z += 4) {
            lampIn(kit, 9, Y + 3, z);
            lampIn(kit, 13, Y + 3, z);
        }
    }

    // 壁の石・床の石を灯りに換える (固い所だけ。封印の扉のブロックは消えるので換えない)
    private static void lampIn(BuildKit kit, int x, int y, int z) {
        BlockState cur = kit.get(x, y, z);
        if (!cur.isAir() && !cur.is(ModBlocks.SEALED_GATE.get()) && !cur.is(Blocks.WATER)) {
            kit.set(x, y, z, SEA_LANTERN);
        }
    }

    // ---------------------------------------------------------------- W03 苔の滑り坂
    private static void buildW03(BuildKit kit) {
        int x1 = W03_X1;
        int x2 = W03_X2;
        for (int[] seg : W03_SEGS) {
            kit.room(new TitanBackLayout.Box(x1, x2, seg[0], seg[1], seg[2], 9));
        }
        // 床: 広間 (磨いた石)・流れ (苔)・島の列 (穴。底は水)
        for (int i = 0; i < W03_SEGS.length; i++) {
            int[] seg = W03_SEGS[i];
            boolean lane = W03Moss.laneOf(seg[0]) >= 0;
            boolean gap = seg[1] - seg[0] == 1;
            if (gap) {
                int f = seg[2];
                kit.fill(x1, f - 6, seg[0], x2, f, seg[1], BuildKit.AIR);
                kit.fill(x1, f - 6, seg[0], x2, f - 5, seg[1], WATER);
                // 穴の両わきの壁ぎわの足もと (流れの端が穴へ落ちないよう、床は穴の手前で止まる)
            } else {
                kit.fill(x1, seg[2], seg[0], x2, seg[2], seg[1], lane ? MOSS : kit.pal().floor());
            }
        }
        // 島 (三つの配置すべての分。封印の扉のブロック。グループごとに残る・消える)
        for (BlockPos p : W03Moss.union()) {
            kit.set(p, GATE);
        }
        // 広間の床の飾り: 休み場の広間・出口の広間は縁取り
        for (int[] seg : new int[][] {W03_SEGS[0], W03_SEGS[6], W03_SEGS[10]}) {
            for (int x = x1; x <= x2; x++) {
                kit.set(x, seg[2], seg[0], BuildKit.TILES);
                kit.set(x, seg[2], seg[1], BuildKit.TILES);
            }
        }
        kit.restPoint(W03_REST_A);
        kit.restPoint(W03_REST_M);
        // 灯り: 両側の壁に、床から 3 つ上の高さで三行おき (天井は高く、真ん中まで明るくするため)。穴の底にも (水の中でも見える)
        for (int[] seg : W03_SEGS) {
            for (int z = seg[0]; z <= seg[1]; z += 3) {
                lampIn(kit, x1 - 1, seg[2] + 3, z);
                lampIn(kit, x2 + 1, seg[2] + 3, z);
            }
            if (seg[1] - seg[0] == 1) {
                for (int x : new int[] {x1 + 2, (x1 + x2) / 2, x2 - 2}) {
                    kit.set(x, seg[2] - 7, seg[0], SEA_LANTERN);
                }
            }
        }
    }

    // ---------------------------------------------------------------- W04 噴気孔の昇り
    private static void buildW04(BuildKit kit) {
        kit.room(W04);
        kit.room(W04_STONE_ROOM);
        kit.doorway(-22, -18, 151, 152, Y, 5);
        int x1 = W04.x1();
        int x2 = W04.x2();
        // 入口の岩棚 (北、床と同じ高さ)・出口の岩棚 (南、出口の戸の前)
        kit.fill(x1, 108, 129, x2, Y - 1, 131, BuildKit.BRICKS);
        kit.fill(x1, Y, 129, x2, Y, 131, BuildKit.POLISHED);
        kit.fill(W04Vents.X0 + W04Vents.EXIT_U1, 108, 148, W04Vents.X0 + W04Vents.EXIT_U2, Y - 1, 150, BuildKit.BRICKS);
        kit.fill(W04Vents.X0 + W04Vents.EXIT_U1, Y, 148, W04Vents.X0 + W04Vents.EXIT_U2, Y, 150, BuildKit.POLISHED);
        // 縁取り (岩棚の縁を見やすく)
        for (int x = x1; x <= x2; x++) {
            kit.set(x, Y, 131, BuildKit.TILES);
        }
        for (int x = W04Vents.X0 + W04Vents.EXIT_U1; x <= W04Vents.X0 + W04Vents.EXIT_U2; x++) {
            kit.set(x, Y, 148, BuildKit.TILES);
        }
        // 三つの配置の岩棚 (一段目 111・二段目 115)。柱は石、上面は封印の扉のブロック
        for (int level = 1; level <= 2; level++) {
            int top = level == 1 ? W04Vents.LEDGE1 : W04Vents.LEDGE2;
            for (int c = 0; c < W04Vents.CAND_U.length; c++) {
                List<BlockPos> cells = W04Vents.ledge(level, c);
                for (BlockPos p : cells) {
                    kit.fill(p.getX(), 108, p.getZ(), p.getX(), top - 1, p.getZ(), BuildKit.BRICKS);
                    kit.set(p, GATE);
                }
            }
        }
        // 底の孔の台・栓の台 (見かけの上書きで光る)
        for (BlockPos p : W04Vents.vent(0, 0).cells()) {
            kit.set(p, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        for (BlockPos p : W04Vents.plugCells()) {
            kit.set(p, Blocks.IRON_BLOCK.defaultBlockState());
        }
        deckRest(kit, REST_W04);
        // 灯り: 底の床 (岩棚の下を避けた三つの列)・岩棚の柱の北の面・壁に高さを変えて・天井に吊るした灯り
        for (int v : new int[] {6, 12, 18}) {
            for (int u : new int[] {2, 6, 10, 14, 18}) {
                lampIn(kit, W04Vents.X0 + u, BOTTOM_Y, W04Vents.Z0 + v);
            }
        }
        for (int level = 1; level <= 2; level++) {
            for (int c = 0; c < W04Vents.CAND_U.length; c++) {
                BlockPos any = W04Vents.ledge(level, c).get(0);
                int top = level == 1 ? W04Vents.LEDGE1 : W04Vents.LEDGE2;
                lampIn(kit, any.getX() + 2, top - 1, any.getZ());
                lampIn(kit, any.getX() + 2, top - 3, any.getZ() + 4);
            }
        }
        for (int y : new int[] {110, 114, 118, 122, 126}) {
            kit.wallLamps(new TitanBackLayout.Box(x1, x2, 129, 150, 107, 24), 4, y);
        }
        for (int[] at : new int[][] {{-20, 134}, {-20, 141}, {-14, 138}, {-26, 138}}) {
            kit.hangingLantern(at[0], at[1], W04.ceilingY(), 5);
        }
        kit.ribs(W04, 6, true);
        kit.wallLamps(W04_STONE_ROOM, 3, Y + 3);
        kit.ribs(W04_STONE_ROOM, 3, true);
    }

    // ---------------------------------------------------------------- W11 棺の護送
    private static void buildW11(BuildKit kit) {
        kit.room(W11);
        kit.room(W11_STONE_ROOM);
        kit.doorway(0, 4, 166, 167, Y, 5);
        kit.pilasters(W11, 6);
        kit.ribs(W11, 5, false);
        kit.wallLamps(W11, 4, Y + 4);
        kit.wallLamps(W11_STONE_ROOM, 3, Y + 3);
        for (int z = 137; z <= 161; z += 8) {
            for (int x : new int[] {-3, 7}) {
                kit.hangingLantern(x, z, W11.ceilingY(), 2);
            }
        }
        // 灯り: 床に海のランタンを四マスおきに (広間の真ん中まで明るくする)
        for (int z = 131; z <= 163; z += 4) {
            for (int x : new int[] {-4, 2, 8}) {
                lampIn(kit, x, Y, z);
            }
        }
        // 祭壇 (道の終わり。戸の前の壇)
        for (int x = 0; x <= 4; x++) {
            kit.set(x, Y, 165, BuildKit.CHISELED);
        }
        deckRest(kit, REST_W11);
        kit.ribs(W11_STONE_ROOM, 3, true);
    }

    // ---------------------------------------------------------------- W12 鯨骨の闘技場
    private static void buildW12(BuildKit kit) {
        int cx = W12_CX;
        int cz = W12_CZ;
        kit.room(W12_FOYER_N);
        kit.room(W12_FOYER_S);
        // 円い広間: 内側を掘る (半径 11.6)。円の床 (半径 10.2) の下は穴 (底は水)、芯 (半径 5.2) の下は石
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                double r = Math.hypot(dx, dz);
                int x = cx + dx;
                int z = cz + dz;
                if (r <= RingArenaCell.R_HALL) {
                    kit.fill(x, Y + 1, z, x, Y + 9, z, BuildKit.AIR);
                    if (r > RingArenaCell.R_CORE) {
                        kit.fill(x, Y - 6, z, x, Y, z, BuildKit.AIR);
                        kit.fill(x, Y - 6, z, x, Y - 5, z, WATER);
                    } else {
                        kit.set(x, Y, z, r <= 1.6 ? BuildKit.CHISELED : BuildKit.POLISHED);
                    }
                } else if (r <= RingArenaCell.R_HALL + 1.2) {
                    // 壁の灯り (円の外の石に埋める。三十度おきに高さを替えて)
                    int k = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 15.0);
                    if (Math.floorMod(k, 2) == 0) {
                        lampIn(kit, x, Y + (Math.floorMod(k / 2, 2) == 0 ? 2 : 6), z);
                    }
                }
            }
        }
        // 崩れる床 (封印の扉のブロック)・橋
        W12Arena arena = new W12Arena();
        for (BlockPos p : arena.discCells()) {
            kit.set(p, GATE);
        }
        kit.doorway(22, 26, 136, 137, Y, 5);
        kit.doorway(22, 26, 159, 160, Y, 5);
        for (BlockPos p : W12Arena.BRIDGE_IN) {
            kit.set(p, GATE);
        }
        for (BlockPos p : W12Arena.BRIDGE_OUT) {
            kit.set(p, GATE);
        }
        deckRest(kit, REST_W12);
        kit.pilasters(W12_FOYER_N, 6);
        kit.wallLamps(W12_FOYER_N, 4, Y + 4);
        kit.wallLamps(W12_FOYER_S, 4, Y + 4);
        kit.ribs(W12_FOYER_N, 5, false);
        kit.ribs(W12_FOYER_S, 5, false);
        // 芯の床の灯り (輪が二つ。崩れても残る)
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            lampIn(kit, cx + (int) Math.round(Math.cos(a) * 3.2), Y, cz + (int) Math.round(Math.sin(a) * 3.2));
            lampIn(kit, cx + (int) Math.round(Math.cos(a + Math.PI / 6) * 4.8), Y, cz + (int) Math.round(Math.sin(a + Math.PI / 6) * 4.8));
        }
        // 広間の天井の灯り (芯の上に輪)
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            kit.set(cx + (int) Math.round(Math.cos(a) * 6), Y + 10, cz + (int) Math.round(Math.sin(a) * 6), SEA_LANTERN);
        }
        kit.set(cx, Y + 10, cz, SEA_LANTERN);
        // 穴の底の灯り (水の中でも見える)
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            kit.set(cx + (int) Math.round(Math.cos(a) * 8), Y - 7, cz + (int) Math.round(Math.sin(a) * 8), SEA_LANTERN);
        }
    }

    // ---------------------------------------------------------------- W08 影絵の間 (S1 の北の壁の戸から北へ)
    private static void buildW08(BuildKit kit) {
        kit.room(W08);
        kit.room(W08_STONE_ROOM);
        // 戸口: S1 の北の壁 (z 116・117)・東の壁 (x -31・-30。石の小部屋へ)
        kit.doorway(-42, -38, 116, 117, Y, 5);
        kit.doorway(-31, -30, 104, 108, Y, 5);
        // 幕の壁 (北、z 97) と台 (灯の台 x -37〜-33・柱の台 x -47〜-43、段 y Y+1〜Y+3): 磨いた黒石。幕の行は見かけで描く
        BlockState socket = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        for (int i = 0; i < W08Shadows.MAX_PIECES; i++) {
            for (int k = 0; k < W08Shadows.SLOTS; k++) {
                kit.set(W08Shadows.lampSocket(i, k), socket);
                kit.set(W08Shadows.pillarSocket(i, k), socket);
            }
        }
        for (int c = 0; c < W08Shadows.CELLS; c++) {
            for (int y : new int[] {W08Shadows.ROW_TARGET, W08Shadows.ROW_TARGET + 1, W08Shadows.ROW_MATCH, W08Shadows.ROW_LIVE, W08Shadows.ROW_LIVE + 1}) {
                kit.set(W08Shadows.SCREEN_X0 + c, y, W08Shadows.SCREEN_Z, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // 灯の列 (奥) と柱の列 (まんなか) の床の筋
        for (int k = 0; k < W08Shadows.SLOTS; k++) {
            kit.set(W08Shadows.LAMP_X0 + k, Y, W08Shadows.LAMP_Z, Blocks.COPPER_BLOCK.defaultBlockState());
        }
        for (int k = 0; k <= W08Shadows.SLOTS; k++) {
            kit.set(W08Shadows.PILLAR_X0 + k, Y, W08Shadows.PILLAR_Z, Blocks.COPPER_BLOCK.defaultBlockState());
            kit.set(W08Shadows.PILLAR_X0 + k, Y, W08Shadows.PILLAR_Z - 1, Blocks.COPPER_BLOCK.defaultBlockState());
        }
        kit.wallLamps(W08, 4, Y + 4);
        kit.wallLamps(W08_STONE_ROOM, 3, Y + 3);
        kit.ribs(W08, 5, false);
        kit.ribs(W08_STONE_ROOM, 3, true);
        for (int[] at : new int[][] {{-46, 100}, {-34, 100}, {-46, 107}, {-34, 107}, {-46, 113}, {-34, 113}, {-40, 100}, {-40, 107}, {-40, 114}}) {
            lampIn(kit, at[0], Y, at[1]);
        }
        for (int[] at : new int[][] {{-44, 101}, {-36, 101}, {-44, 107}, {-36, 107}, {-40, 112}}) {
            kit.hangingLantern(at[0], at[1], W08.ceilingY(), 3);
        }
        deckRest(kit, REST_W08);
    }
}
