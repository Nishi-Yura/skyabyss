package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.FLOOR;
import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.*;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.trial.whale.W02Bell;
import jp.revati.skyabyss.trial.whale.W06Score;
import jp.revati.skyabyss.trial.whale.W07Gates;
import jp.revati.skyabyss.trial.whale.W10Fort;
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
        for (TitanBackLayout.Box b : new TitanBackLayout.Box[] {D0, C1, S0, W06, W07, W10, W02}) {
            kit.room(b);
        }
        // 戸口 (D0 → C1 → S0 は開いた通路。ほかは戸 = 入口の封印を、あとで置く)
        kit.doorway(40, 44, 25, 25, Y, 5);
        kit.doorway(40, 44, 67, 67, Y, 5);
        kit.doorway(21, 21, 20, 24, Y, 5);
        kit.doorway(39, 39, 32, 36, Y, 5);
        kit.doorway(-8, -4, 81, 82, Y, 5);
        kit.doorway(28, 32, 81, 82, Y, 5);
        buildStair(kit);
        buildD0(kit);
        buildW02(kit);
        buildW06(kit);
        buildW07(kit);
        buildW10(kit);
        // S0・C1 の飾り
        kit.restPoint(S0);
        kit.pilasters(S0, 6);
        kit.ribs(S0, 5, true);
        kit.wallLamps(S0, 4, Y + 4);
        kit.ribs(C1, 4, true);
        kit.wallLamps(C1, 3, Y + 3);
        // 石の差し口と戻りの光る床
        kit.set(W02_STONE, ModBlocks.RESONANCE_STONE.get().defaultBlockState());
        kit.set(W06_STONE, ModBlocks.RESONANCE_STONE.get().defaultBlockState());
        kit.set(W07_STONE, ModBlocks.RESONANCE_STONE.get().defaultBlockState());
        kit.set(W10_STONE, ModBlocks.RESONANCE_STONE.get().defaultBlockState());
        for (BlockPos pad : new BlockPos[] {W02_PAD, W06_PAD, W07_PAD, W10_PAD}) {
            kit.set(pad, PAD);
        }
        // 試練の板 (鼓動の間の南の壁ぎわ・試練の階の広間の北の壁ぎわ)
        kit.set(BOARD_HUB, ModBlocks.TRIAL_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        kit.set(BOARD_D0, ModBlocks.TRIAL_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
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

    // ---------------------------------------------------------------- W02 鐘楼の綱渡り
    private static void buildW02(BuildKit kit) {
        TitanBackLayout.Box w = W02;
        // 穴の底の溶岩 (y 113)。足を踏み外した人は、溶岩に届く前に (y 117.5 より下で) 休み場へ戻される
        kit.fill(w.x1(), w.floorY() + 1, w.z1(), w.x2(), w.floorY() + 1, w.z2(), LAVA);
        // 岩棚 A (入口)・中ほどの台 M・向こうの岩棚 C: 底から Y まで石を積んだ島
        for (TitanBackLayout.Box b : new TitanBackLayout.Box[] {W02_LEDGE_A, W02_MID, W02_LEDGE_C}) {
            kit.fill(b.x1(), w.floorY() + 1, b.z1(), b.x2(), Y - 1, b.z2(), BuildKit.BRICKS);
            for (int x = b.x1(); x <= b.x2(); x++) {
                for (int z = b.z1(); z <= b.z2(); z++) {
                    boolean edge = x == b.x1() || x == b.x2() || z == b.z1() || z == b.z2();
                    kit.set(x, Y, z, edge ? BuildKit.TILES : BuildKit.POLISHED);
                }
            }
        }
        // 梁 (封印の扉のブロック。三つの配置すべての分。グループごとに止まる・振れる・消える)
        BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
        for (BlockPos p : W02Bell.union()) {
            kit.set(p, gate);
        }
        // 鐘: 入口の岩棚と中ほどの台
        BlockState bell = Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH);
        kit.set(W02Bell.BELL_A, bell);
        kit.set(W02Bell.BELL_M, bell);
        deckRest(kit, REST_W02_A);
        deckRest(kit, REST_W02_M);
        deckRest(kit, REST_W02_C);
        // 飾り: 天井の肋骨・吊るした灯り・壁の灯り
        kit.ribs(w, 6, true);
        for (int z : new int[] {32, 46, 60}) {
            kit.hangingLantern(13, z, w.ceilingY(), 5);
        }
        kit.wallLamps(w, 4, Y + 6);
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
        kit.set(c.getX(), Y, c.getZ(), BuildKit.CHISELED);
        kit.set(c, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int bx = c.getX() + d.getStepX() * 2;
            int bz = c.getZ() + d.getStepZ() * 2;
            // 縁の外が空いている (落ちる) 所には置かない
            if (!kit.get(bx, Y, bz).isAir() && kit.get(bx, Y + 1, bz).isAir() && !kit.get(bx + d.getStepX(), Y, bz + d.getStepZ()).isAir()) {
                kit.set(bx, Y + 1, bz, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM));
            }
        }
        kit.lantern(c.getX() + 1, Y + 1, c.getZ() - 1);
    }
}
