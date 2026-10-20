package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.*;

import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.trial.ember.E02Vents;
import jp.revati.skyabyss.trial.ember.E03Chain;
import jp.revati.skyabyss.trial.ember.E04Carry;
import jp.revati.skyabyss.trial.ember.E06Flues;
import jp.revati.skyabyss.trial.ember.E07Bellows;
import jp.revati.skyabyss.trial.ember.E08Mold;
import jp.revati.skyabyss.trial.ember.E10Furnace;
import jp.revati.skyabyss.trial.ember.E11Cart;
import jp.revati.skyabyss.trial.ember.E12Nest;
import jp.revati.skyabyss.trial.ember.EmberCellKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

// 試練の階 (EmberDeck。docs/design/29) の中身を建てる。EmberBodyBuilder が箱を掘り (deckRooms)、休み場・碑文のあと (fixtures) にここを呼ぶ。
// 戸 (封印の扉) は TrialRuntime.installGates が置く (戸口は空けておく)。何度建てても同じ
public final class EmberDeckBuilder {
    private EmberDeckBuilder() {
    }

    private static final BlockState GLOW = EmberKit.GLOW;
    private static final BlockState PAD_RING = Blocks.POLISHED_BASALT.defaultBlockState();
    private static final BlockState PAD_CENTER = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();

    // 箱の中をすべて床にする (掘ったあと)。戸口の床も
    static void floors(EmberKit kit) {
        for (EmberBackLayout.Box b : ROOMS) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    kit.set(x, Y, z, EmberKit.ruinFloor(x, z));
                }
            }
        }
        for (List<BlockPos> gate : GATES) {
            for (BlockPos p : gate) {
                kit.set(p.getX(), Y, p.getZ(), EmberKit.ruinFloor(p.getX(), p.getZ()));
            }
        }
    }

    static void build(EmberKit kit) {
        corridors(kit);
        hall(kit);
        cellE02(kit);
        cellE10(kit);
        cellE06(kit);
        cellE07(kit);
        cellE03(kit);
        cellE08(kit);
        cellE11(kit);
        cellE12(kit);
        cellE04(kit);
        spine(kit);
        // 灰溜まり (3 × 2。冷える)
        for (int[] a : ASH) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    kit.set(a[0] + dx, Y, a[1] + dz, EmberKit.ASH);
                }
            }
        }
        // 石の差し口 (熾り石)・戻りの光る床 (中心は彫った石、まわりは暗い磨き石。光るのはそのグループの画面だけ)
        for (BlockPos stone : new BlockPos[] {E02_STONE, E10_STONE, E06_STONE, E07_STONE, E03_STONE, E08_STONE, E11_STONE, E12_STONE, E04_STONE}) {
            kit.set(stone.below(), EmberKit.RUIN_CHISELED);
            kit.set(stone, EmberBackSetup.KINDLE_STONE.get().defaultBlockState().setValue(KindleStoneBlock.ACTIVE, false));
        }
        for (BlockPos pad : new BlockPos[] {E02_PAD, E10_PAD, E06_PAD, E07_PAD, E03_PAD, E08_PAD, E11_PAD, E12_PAD, E04_PAD}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.set(pad.offset(dx, 0, dz), dx == 0 && dz == 0 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN_DARK);
                }
            }
        }
        // 試練の板 (炉心の広間の休み場のそば・試練の階の広間)
        kit.set(BOARD_HUB, ModBlocks.TRIAL_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        kit.set(BOARD_D0, ModBlocks.TRIAL_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        for (EmberBackLayout.Inscription in : BOARDS) {
            kit.board(in.pos(), in.facing(), "sign.skyabyss.ember_board_" + in.id(), DyeColor.WHITE);
        }
    }

    // 壁の灯り (茂み光) を、壁の固いブロックの中へ埋める (空気なら置かない)
    private static void lamp(EmberKit kit, int x, int y, int z) {
        if (!kit.get(x, y, z).isAir()) {
            kit.set(x, y, z, GLOW);
        }
    }

    // 部屋の両脇の壁の灯り (z を step おきに)、天井の灯り
    private static void sideLamps(EmberKit kit, EmberBackLayout.Box b, int step, int y) {
        for (int z = b.minZ() + 2; z <= b.maxZ() - 1; z += step) {
            lamp(kit, b.minX() - 1, y, z);
            lamp(kit, b.maxX() + 1, y, z);
        }
    }

    private static void ceilingLamps(EmberKit kit, EmberBackLayout.Box b, int x, int stepZ, int ceiling) {
        for (int z = b.minZ() + 3; z <= b.maxZ() - 2; z += stepZ) {
            lamp(kit, x, ceiling + 1, z);
        }
    }

    private static void corridors(EmberKit kit) {
        sideLamps(kit, C0, 5, Y + 3);
        sideLamps(kit, KE, 6, Y + 4);
        sideLamps(kit, KW, 6, Y + 4);
        // 回廊の床の中央の灯りの列 (石の床に埋める)
        for (int z = KE.minZ() + 3; z <= KE.maxZ() - 2; z += 8) {
            kit.set((KE.minX() + KE.maxX()) / 2, Y, z, EmberKit.EMBERLIGHT);
        }
        for (int z = KW.minZ() + 3; z <= KW.maxZ() - 2; z += 8) {
            kit.set((KW.minX() + KW.maxX()) / 2, Y, z, EmberKit.EMBERLIGHT);
        }
        for (int z = C0.minZ() + 3; z <= C0.maxZ() - 2; z += 6) {
            kit.set((C0.minX() + C0.maxX()) / 2, Y, z, EmberKit.EMBERLIGHT);
        }
    }

    private static void hall(EmberKit kit) {
        sideLamps(kit, D0, 5, Y + 4);
        for (int x = D0.minX() + 3; x <= D0.maxX() - 2; x += 7) {
            lamp(kit, x, TOP + 1, 12);
            lamp(kit, x, Y + 4, D0.minZ() - 1);
            lamp(kit, x, Y + 4, D0.maxZ() + 1);
        }
    }

    // 戸口の上の灯り (壁の中。両側から見える)
    private static void gateLamps(EmberKit kit, int wallX, int z1, int z2, int dir) {
        lamp(kit, wallX, Y + 6, z1);
        lamp(kit, wallX, Y + 6, z2);
    }

    // ---------------------------------------------------------------- E02 噴気の渡り (A)
    private static void cellE02(EmberKit kit) {
        EmberBackLayout.Box a = A;
        for (int b = 0; b < E02Vents.BANDS; b++) {
            for (int l = 0; l < E02Vents.LANES; l++) {
                EmberCellKit.buildPatch(kit, E02Vents.cx(l), Y, E02Vents.cz(b));
            }
        }
        sideLamps(kit, a, 5, Y + 3);
        for (int z = a.minZ() + 4; z <= a.maxZ() - 2; z += 9) {
            lamp(kit, 24, TOP + 1, z);
        }
        gateLamps(kit, 13, 23, 27, -1);
        // 奥の岸の飾り: 石の両脇に灯りの柱
        for (int dx : new int[] {-3, 3}) {
            for (int y = Y + 1; y <= Y + 3; y++) {
                kit.set(E02_STONE.getX() + dx, y, E02_STONE.getZ(), EmberKit.BASALT);
            }
            kit.set(E02_STONE.getX() + dx, Y + 4, E02_STONE.getZ(), GLOW);
        }
    }

    // ---------------------------------------------------------------- E10 鞴持ちの炉 (B)
    private static void cellE10(EmberKit kit) {
        EmberBackLayout.Box b = B;
        // 炉の土台 (5 × 5、高さ 1) と炉 (3 × 3 × 3)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                kit.set(E10Furnace.FURNACE_X + dx, Y + 1, E10Furnace.FURNACE_Z + dz, edge ? EmberKit.RUIN_DARK : EmberKit.RUIN_CHISELED);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    kit.set(E10Furnace.FURNACE_X + dx, Y + 2 + dy, E10Furnace.FURNACE_Z + dz, dy == 2 ? EmberKit.BASALT : EmberKit.RUIN_DARK);
                }
            }
        }
        // 煙突 (炉の上、天井まで)
        for (int y = Y + 5; y <= TOP; y++) {
            kit.set(E10Furnace.FURNACE_X, y, E10Furnace.FURNACE_Z, EmberKit.RAIL);
        }
        for (int[] v : E10Furnace.VENTS) {
            EmberCellKit.buildPatch(kit, v[0], Y, v[1]);
        }
        sideLamps(kit, b, 5, Y + 3);
        for (int z = b.minZ() + 4; z <= b.maxZ() - 2; z += 8) {
            lamp(kit, 19, TOP + 1, z);
            lamp(kit, 29, TOP + 1, z);
        }
        gateLamps(kit, 13, 73, 77, -1);
        for (int dx : new int[] {-3, 3}) {
            for (int y = Y + 1; y <= Y + 3; y++) {
                kit.set(E10_STONE.getX() + dx, y, E10_STONE.getZ(), EmberKit.BASALT);
            }
            kit.set(E10_STONE.getX() + dx, Y + 4, E10_STONE.getZ(), GLOW);
        }
    }

    // ---------------------------------------------------------------- 版 24 の新しい部屋 (E03・E08・E11・E12・E04)。
    // 石・休み場・光る床は共通の部品 (上の build)。ここは床の形 (鎖橋の坑・鋳型の台・荷車の道・巣の穴)・灯り・柱だけ

    // 鎖橋の間 (NW): 段と岸と休み場と戻りの床のほかは、床を抜いて坑にする (空気、その下は溶岩)
    private static void cellE03(EmberKit kit) {
        EmberBackLayout.Box b = NW;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                if (!E03Chain.solid(x, z)) {
                    kit.set(x, Y, z, EmberKit.AIR);
                    kit.set(x, Y - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                }
            }
        }
        sideLamps(kit, b, 5, Y + 3);
        // 坑の上の天井の灯り (坑の歩ける所は 4 以上の明るさに。格子 4 ブロックごと)
        for (int x = -30; x <= -14; x += 4) {
            for (int z = 2; z <= 18; z += 4) {
                lamp(kit, x, TOP + 1, z);
            }
        }
        gateLamps(kit, -13, 8, 12, 1);
    }

    // 鋳型の間 (NE): 注ぎ口と三つの鋳型の台 (石の台。色は見かけの上書き)
    private static void cellE08(EmberKit kit) {
        EmberBackLayout.Box b = NE;
        kit.set(E08Mold.SPOUT_X, Y + 1, E08Mold.SPOUT_Z, EmberKit.BASALT);
        kit.set(E08Mold.SPOUT_X, Y, E08Mold.SPOUT_Z, EmberKit.RUIN_CHISELED);
        for (int x : new int[] {25, 29, 33}) {
            kit.set(x, Y + 1, E08Mold.MOLD_Z, EmberKit.BASALT);
            kit.set(x, Y, E08Mold.MOLD_Z, EmberKit.RUIN_CHISELED);
        }
        sideLamps(kit, b, 5, Y + 3);
        for (int x = b.minX() + 2; x <= b.maxX(); x += 5) {
            lamp(kit, x, TOP + 1, 12);
        }
        gateLamps(kit, 23, 8, 12, -1);
    }

    // 荷車の道 (SW): 道は見かけの上書き (床の形は同じ)。灯りの柱だけ
    private static void cellE11(EmberKit kit) {
        EmberBackLayout.Box b = SW;
        sideLamps(kit, b, 6, Y + 3);
        for (int x = b.minX() + 2; x <= b.maxX() - 2; x += 6) {
            lamp(kit, x, Y + 6, 112);
        }
        for (int x = E11Cart.ROAD_X0; x <= E11Cart.ROAD_X0 + E11Cart.LAST; x += 5) {
            kit.set(x, Y + 1, E11Cart.ROAD_Z - 1, EmberKit.BASALT);
            kit.set(x, Y + 1, E11Cart.ROAD_Z + 1, EmberKit.BASALT);
        }
        for (int x = -25; x <= -20; x += 5) {
            lamp(kit, x, Y + 6, 109);
        }
    }

    // 大燠虫の巣 (SC): 三つの穴と噴気の割れ目 (障害)。中心は広く
    private static void cellE12(EmberKit kit) {
        EmberBackLayout.Box b = SC;
        for (int[] v : E12Nest.VENTS) {
            EmberCellKit.buildPatch(kit, v[0], Y, v[1]);
        }
        sideLamps(kit, b, 6, Y + 3);
        for (int x = b.minX() + 3; x <= b.maxX() - 2; x += 7) {
            lamp(kit, x, Y + 6, 112);
        }
        lamp(kit, -4, Y + 6, 109);
        lamp(kit, 4, Y + 6, 109);
    }

    // 熾の道 (SE): 熾り石の台 (道の始まり)・道と水の溜まりは見かけの上書き
    private static void cellE04(EmberKit kit) {
        EmberBackLayout.Box b = SE;
        kit.set(E04Carry.BRAZIER.getX(), Y, E04Carry.BRAZIER.getZ(), EmberKit.RUIN_CHISELED);
        kit.set(E04Carry.BRAZIER.getX(), Y + 1, E04Carry.BRAZIER.getZ(), EmberKit.BASALT);
        sideLamps(kit, b, 5, Y + 3);
        for (int x = b.minX() + 3; x <= b.maxX() - 2; x += 6) {
            lamp(kit, x, Y + 6, 112);
        }
        lamp(kit, 20, Y + 6, 109);
        lamp(kit, 26, Y + 6, 109);
    }

    // 南の廊下 (SP): 回廊 (KE・KW) と三つの南の広間をつなぐ。灯りだけ
    private static void spine(EmberKit kit) {
        for (int x = SP.minX() + 3; x <= SP.maxX() - 2; x += 8) {
            lamp(kit, x, Y + 6, 106);
        }
    }

    // ---------------------------------------------------------------- E06 煙突の風読み (C)
    private static void cellE06(EmberKit kit) {
        EmberBackLayout.Box c = C;
        BlockState slab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (BlockPos base : E06Flues.union()) {
            // 足もとの台 (3 × 3 の暗い石) と柱 (高さ 4) と蓋 (石の板)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.set(base.getX() + dx, Y, base.getZ() + dz, dx == 0 && dz == 0 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN_DARK);
                }
            }
            for (int y = Y + 1; y <= Y + E06Flues.PILLAR; y++) {
                kit.set(base.getX(), y, base.getZ(), y == Y + 1 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN);
            }
            kit.set(base.getX(), Y + E06Flues.PILLAR + 1, base.getZ(), slab);
        }
        // 奥の仕切り (炉の扉の壁)。戸口は 5 × 5 (戸は TrialRuntime が置く)
        partitionZ(kit, c, E06_EXIT_Z, -27, -23);
        sideLamps(kit, c, 5, Y + 3);
        for (int z = c.minZ() + 4; z <= c.maxZ() - 2; z += 8) {
            lamp(kit, -24, TOP + 1, z);
        }
        gateLamps(kit, -13, 23, 27, 1);
        // 戸の上の灯り
        lamp(kit, -27, Y + 7, E06_EXIT_Z);
        lamp(kit, -23, Y + 7, E06_EXIT_Z);
        for (int dx : new int[] {-3, 3}) {
            for (int y = Y + 1; y <= Y + 3; y++) {
                kit.set(E06_STONE.getX() + dx, y, E06_STONE.getZ(), EmberKit.BASALT);
            }
            kit.set(E06_STONE.getX() + dx, Y + 4, E06_STONE.getZ(), GLOW);
        }
    }

    // z の仕切りの壁 (箱いっぱい、床から天井まで)。x1〜x2 は戸口 (高さ 5。その上は壁)
    private static void partitionZ(EmberKit kit, EmberBackLayout.Box b, int z, int x1, int x2) {
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = Y + 1; y <= TOP; y++) {
                boolean door = x >= x1 && x <= x2 && y <= G1;
                if (!door) {
                    kit.set(x, y, z, y > Y + 5 || x < x1 - 1 || x > x2 + 1 ? EmberKit.RUIN : EmberKit.RUIN_CHISELED);
                }
            }
        }
    }

    // ---------------------------------------------------------------- E07 三つの鞴 (D)
    private static void cellE07(EmberKit kit) {
        EmberBackLayout.Box d = D;
        for (int k = 0; k < 4; k++) {
            int cx = E07Bellows.PAD_X[k];
            int cz = E07Bellows.PAD_Z[k];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.set(cx + dx, Y, cz + dz, dx == 0 && dz == 0 ? PAD_CENTER : PAD_RING);
                }
            }
            // 鞴の塔 (踏み板の北。炎の段を見せる四つの灯)
            BlockPos t = E07Bellows.tower(k);
            for (int i = 0; i < E07Bellows.TOWER; i++) {
                kit.set(t.above(i), EmberKit.RUIN);
            }
            kit.set(t.above(E07Bellows.TOWER), EmberKit.RAIL);
        }
        // 炉 (3 × 3 × 3) と土台
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                kit.set(E07Bellows.FURNACE_X + dx, Y, E07Bellows.FURNACE_Z + dz, edge ? EmberKit.RUIN_DARK : EmberKit.RUIN_CHISELED);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    kit.set(E07Bellows.FURNACE_X + dx, Y + 1 + dy, E07Bellows.FURNACE_Z + dz, dy == 2 ? EmberKit.BASALT : EmberKit.RUIN_DARK);
                }
            }
        }
        partitionZ(kit, d, E07_EXIT_Z, -27, -23);
        sideLamps(kit, d, 5, Y + 3);
        for (int z = d.minZ() + 4; z <= d.maxZ() - 2; z += 8) {
            lamp(kit, -24, TOP + 1, z);
        }
        gateLamps(kit, -13, 65, 69, 1);
        lamp(kit, -27, Y + 7, E07_EXIT_Z);
        lamp(kit, -23, Y + 7, E07_EXIT_Z);
        for (int dx : new int[] {-3, 3}) {
            for (int y = Y + 1; y <= Y + 3; y++) {
                kit.set(E07_STONE.getX() + dx, y, E07_STONE.getZ(), EmberKit.BASALT);
            }
            kit.set(E07_STONE.getX() + dx, Y + 4, E07_STONE.getZ(), GLOW);
        }
    }
}
