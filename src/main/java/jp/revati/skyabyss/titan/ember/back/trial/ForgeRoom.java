package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

// 鍛えの間の部屋の中身 (13 の 5.3節、26 の 11 章。部屋の箱は EmberBackLayout.EAST: x 24..64、y 86..108、z -14..56、床 86)。
// 流れは南へ一本: 入口 → 鍛えの回廊 (z -13..19。色の見本・手順の碑・工房・灯りの道) → 前室 (z 20..33。休み場 e0) → 柱の列 (z 34)
//   → 金床と刻み (z 39〜41)・焼き入れの槽 → 鞴 (z 48)・大炉 (z 50〜54) → 奥の壁 (z 57〜59) に鍛え手の扉 (封印の扉)。扉の前は B の取っておく場所。
// 版 12 までの部屋 (z -8..28) を x +18・y +6・z +28 に移した所が、道具のある所 (Forge.DX・DY・DZ)。回廊は新しく足した前庭。
// 規則を一つずつ: 回廊の手順の碑と色の見本で「熱の色」を教え → 道具のそばの碑で「鞴 → 金床 → 槽 → 扉」を一つずつ → 打つ順が変わる (ひねり)。
public final class ForgeRoom {
    // 工房 (回廊の東の張り出し。西に口)
    public static final EmberBackLayout.Box WORKSHOP = new EmberBackLayout.Box(56, EmberBackLayout.FLOOR_EAST, -3, 63, EmberBackLayout.FLOOR_EAST + 6, 9);

    // 砥石 (床に置く向き。壁づけだと後ろに支えが要る)
    private static final BlockState GRIND = Blocks.GRINDSTONE.defaultBlockState()
            .setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
            .setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING, Direction.EAST);

    private ForgeRoom() {
    }

    // EmberBackRooms.register("east", ForgeRoom::build)
    public static void build(ServerLevel level, EmberBackLayout.Box box) {
        RoomKit k = new RoomKit(level, "east");
        int floor = EmberBackLayout.FLOOR_EAST;
        k.shell(true);
        k.fill(box.minX() + 1, floor, box.minZ() + 1, box.maxX() - 1, floor, box.maxZ() - 1, RoomKit.SMOOTH);
        gallery(k, box);
        core(k, box);
        workshop(k);
        // 洞の建築: 丸天井と肋、熔脈 (壁のマグマの筋)、回廊の溶岩の溝 (熔脈の回廊)
        k.vault(box.minX() + 1, box.maxX() - 1, box.minZ() + 1, box.maxZ() - 1, 14, 19, null);
        k.veins(box.minZ() + 3, box.maxZ() - 3, 8);
        k.veinsEnds(box.minX() + 3, box.maxX() - 3, 6);
        veinChannels(k);
        buildInner(level, Forge.ownerPattern(level));
    }

    // 壁の柱 (黒石レンガ。上に光る茂み)
    private static void pilaster(RoomKit k, int x, int z, int h) {
        int floor = EmberBackLayout.FLOOR_EAST;
        for (int y = floor + 1; y <= floor + h; y++) {
            k.set(x, y, z, RoomKit.BRICKS);
        }
        k.set(x, floor + h + 1, z, RoomKit.SHROOM);
    }

    // 鍛えの回廊 (z -13..19): 中央の灯りの道 (幅 7。x 41..47)、壁の柱、梁、西の壁に炉と金床と作業台の並び
    private static void gallery(RoomKit k, EmberBackLayout.Box box) {
        int floor = EmberBackLayout.FLOOR_EAST;
        int cx = EmberBackLayout.TUBE_EAST_X;
        for (int z = box.minZ() + 1; z <= 33; z++) {
            for (int x = cx - 3; x <= cx + 3; x++) {
                int edge = Math.abs(x - cx);
                BlockState s = edge == 3 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : edge == 0 && Math.floorMod(z, 4) == 0
                        ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : RoomKit.BRICKS;
                if (edge == 3 && Math.floorMod(z, 2) != 0) {
                    s = RoomKit.POLISHED;
                }
                k.set(x, floor, z, s);
            }
        }
        for (int z = -9; z <= 31; z += 8) {
            pilaster(k, box.minX() + 1, z, 13);
            pilaster(k, box.maxX() - 1, z, 13);
        }
        // 回廊の柱の列 (道の両側)
        for (int x : new int[] {33, 55}) {
            for (int z : new int[] {-5, 5, 15}) {
                k.post(x, z, 6);
            }
        }
        // 梁 (壁から壁へ。天井の下)
        for (int z : new int[] {-6, 8, 22}) {
            for (int x = box.minX() + 1; x <= box.maxX() - 1; x++) {
                k.set(x, floor + 13, z, RoomKit.BRICKS);
            }
            for (int dx : new int[] {-6, 0, 6}) {
                k.set(cx + dx, floor + 12, z, Blocks.CHAIN.defaultBlockState());
                k.set(cx + dx, floor + 11, z, RoomKit.LANTERN.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            }
        }
        // 西の壁の炉・金床・作業台 (灰の民の工房の並び)。炉は火が入っている
        BlockState furnace = Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, Direction.EAST).setValue(BlastFurnaceBlock.LIT, true);
        for (int z : new int[] {-9, -3, 3, 9, 15}) {
            k.set(box.minX() + 1, floor + 1, z, furnace);
            k.set(box.minX() + 1, floor + 2, z, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
        }
        for (int z : new int[] {-6, 0, 12}) {
            k.set(box.minX() + 2, floor + 1, z, Blocks.ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING, Direction.NORTH));
        }
        k.set(box.minX() + 2, floor + 1, 6, Blocks.SMITHING_TABLE.defaultBlockState());
        k.set(box.minX() + 2, floor + 1, 18, GRIND);
        // 色の見本 (黒 → 赤 → 橙 → 黄 → 白。碑 east_colors の東の台)
        BlockState[] colors = {Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState(),
                Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState()};
        for (int i = 0; i < colors.length; i++) {
            k.fill(49 + i, floor + 1, -8, 49 + i, floor + 2, -8, colors[i]);
            k.set(49 + i, floor + 3, -8, RoomKit.POLISHED);
        }
    }

    // 熔脈の回廊の溶岩の溝: 道 (x 41〜47) の両側 4 ブロック先 (x 37 と 51) に、床の高さで細い溶岩の溝が南北に走る (z -11〜18)。
    // 溝の両側は 2 段の黒石レンガの縁。溝の端は床で閉じている (溶岩は流れ出さない)。道の端から溝まで 4 ブロックあるので熱は届かない
    private static void veinChannels(RoomKit k) {
        int floor = EmberBackLayout.FLOOR_EAST;
        for (int x : new int[] {37, 51}) {
            for (int z = -11; z <= 18; z++) {
                k.set(x, floor, z, Blocks.LAVA.defaultBlockState());
                k.set(x - 1, floor + 1, z, RoomKit.BRICKS);
                k.set(x - 1, floor + 2, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                k.set(x + 1, floor + 1, z, RoomKit.BRICKS);
                k.set(x + 1, floor + 2, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
            }
            // 溝の端のしるし: 柱と灯り
            for (int z : new int[] {-12, 19}) {
                k.fill(x, floor + 1, z, x, floor + 4, z, RoomKit.BRICKS);
                k.set(x, floor + 5, z, RoomKit.SHROOM);
            }
        }
    }

    // 工房: 低い壁で囲った灰の民の作業場 (西に 3 幅の口)。溶岩の釜と砥石、奥の壁に宝箱
    private static void workshop(RoomKit k) {
        int floor = EmberBackLayout.FLOOR_EAST;
        EmberBackLayout.Box w = WORKSHOP;
        for (int x = w.minX(); x <= w.maxX(); x++) {
            k.fill(x, floor + 1, w.minZ() - 1, x, floor + 2, w.minZ() - 1, RoomKit.BRICKS);
            k.fill(x, floor + 1, w.maxZ() + 1, x, floor + 2, w.maxZ() + 1, RoomKit.BRICKS);
        }
        for (int z = w.minZ() - 1; z <= w.maxZ() + 1; z++) {
            if (z >= 1 && z <= 3) {
                continue;
            }
            k.fill(w.minX() - 1, floor + 1, z, w.minX() - 1, floor + 2, z, RoomKit.BRICKS);
        }
        for (int z : new int[] {0, 4}) {
            k.fill(w.minX() - 1, floor + 1, z, w.minX() - 1, floor + 4, z, RoomKit.BRICKS);
            k.set(w.minX() - 1, floor + 5, z, RoomKit.SHROOM);
        }
        k.set(w.minX() + 1, floor + 1, w.minZ(), Blocks.LAVA_CAULDRON.defaultBlockState());
        k.set(w.minX() + 1, floor + 1, w.maxZ(), Blocks.LAVA_CAULDRON.defaultBlockState());
        k.set(w.minX() + 3, floor + 1, w.minZ(), GRIND);
        k.set(w.minX() + 3, floor + 1, w.maxZ(), Blocks.CAULDRON.defaultBlockState());
        k.set(w.maxX(), floor + 4, w.minZ() + 1, RoomKit.SHROOM);
        k.set(w.maxX(), floor + 4, w.maxZ() - 1, RoomKit.SHROOM);
    }

    // 道具のある所 (版 12 の部屋を x +18・y +6・z +28): 柱の列・大炉・鞴・金床と刻みの壁・槽・天井の灯り
    private static void core(RoomKit k, EmberBackLayout.Box box) {
        int floor = EmberBackLayout.FLOOR_EAST;
        int dx = Forge.DX;
        int dz = Forge.DZ;
        // 柱の列 (前室と炉の間)
        for (int x : new int[] {16, 22, 30, 36}) {
            k.fill(x + dx, floor + 1, 6 + dz, x + dx, floor + 5, 6 + dz, RoomKit.BRICKS);
            k.set(x + dx, floor + 6, 6 + dz, RoomKit.LANTERN);
        }
        // 大炉: 本体と煙突。口 (炉の塊) は FORGE の位置、まわりの枠はレンガとマグマ
        k.fill(23 + dx, floor + 1, 22 + dz, 29 + dx, floor + 6, 26 + dz, RoomKit.BRICKS);
        k.fill(25 + dx, floor + 7, 23 + dz, 27 + dx, box.maxY() - 1, 25 + dz, RoomKit.NETHER_BRICKS);
        k.fill(25 + dx, floor + 1, 22 + dz, 27 + dx, floor + 3, 22 + dz, RoomKit.BRICKS);
        k.set(25 + dx, floor + 2, 22 + dz, RoomKit.MAGMA);
        k.set(27 + dx, floor + 2, 22 + dz, RoomKit.MAGMA);
        k.set(Forge.FORGE, EmberTrialSetup.ASH_FORGE.get().defaultBlockState());
        // 大炉の背の面に光る継ぎ目 (扉の前から見える)
        for (int x = 24 + dx; x <= 28 + dx; x += 2) {
            k.set(x, floor + 3, 26 + dz, RoomKit.SHROOM);
        }
        // 鞴の踏み板と、その脇の柱。踏み板のまわりは赤い床の印
        k.set(Forge.BELLOWS_A, EmberTrialSetup.FORGE_BELLOWS.get().defaultBlockState());
        k.set(Forge.BELLOWS_B, EmberTrialSetup.FORGE_BELLOWS.get().defaultBlockState());
        for (int x : new int[] {20, 32}) {
            k.fill(x + dx, floor + 1, 20 + dz, x + dx, floor + 3, 20 + dz, RoomKit.BRICKS);
        }
        for (BlockPos b : new BlockPos[] {Forge.BELLOWS_A, Forge.BELLOWS_B}) {
            station(k, b.getX(), b.getZ(), 1, Blocks.RED_TERRACOTTA.defaultBlockState());
        }
        // 金床と刻みの壁。金床のまわりは橙の床の印
        k.set(Forge.ANVIL, EmberTrialSetup.ASH_ANVIL.get().defaultBlockState());
        station(k, Forge.ANVIL.getX(), Forge.ANVIL.getZ(), 1, Blocks.ORANGE_TERRACOTTA.defaultBlockState());
        k.fill(23 + dx, floor + 1, 11 + dz, 28 + dx, floor + 2, 11 + dz, RoomKit.BRICKS);
        k.set(23 + dx, floor + 3, 11 + dz, RoomKit.BRICKS);
        k.set(28 + dx, floor + 3, 11 + dz, RoomKit.BRICKS);
        k.set(23 + dx, floor + 4, 11 + dz, RoomKit.LANTERN);
        k.set(28 + dx, floor + 4, 11 + dz, RoomKit.LANTERN);
        k.set(Forge.TROUGH_B.getX() + 1, floor + 1, Forge.TROUGH_B.getZ(), RoomKit.BRICKS);
        k.set(Forge.TROUGH_B.getX() + 1, floor + 2, Forge.TROUGH_B.getZ(), RoomKit.LANTERN);
        // 焼き入れの槽。まわりは青い床の印
        k.set(Forge.TROUGH_A, EmberTrialSetup.QUENCH_TROUGH.get().defaultBlockState());
        k.set(Forge.TROUGH_B, EmberTrialSetup.QUENCH_TROUGH.get().defaultBlockState());
        station(k, Forge.TROUGH_A.getX(), Forge.TROUGH_A.getZ(), 1, Blocks.BLUE_TERRACOTTA.defaultBlockState());
        station(k, Forge.TROUGH_B.getX(), Forge.TROUGH_B.getZ(), 1, Blocks.BLUE_TERRACOTTA.defaultBlockState());
        // 天井の灯 (道具のある所)
        for (int x : new int[] {20, 32}) {
            for (int z : new int[] {0, 12, 22}) {
                k.set(x + dx, box.maxY(), z + dz, RoomKit.SHROOM);
            }
        }
        // 壁の柱 (前室から奥まで)
        for (int z = 39; z <= 55; z += 8) {
            pilaster(k, box.minX() + 1, z, 13);
            pilaster(k, box.maxX() - 1, z, 13);
        }
    }

    // 道具の床の印 (中心の床のまわり半径 r の床を、色つきの粘土に。上のブロックには触らない)
    private static void station(RoomKit k, int cx, int cz, int r, BlockState s) {
        int floor = EmberBackLayout.FLOOR_EAST;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                k.set(x, floor, z, s);
            }
        }
    }

    // リセットで直す所: 金床の刻みの色 (打つ順。部屋の持ち主の記録から)、大炉の口、扉のブロック (開け閉めはグループごと)
    static void buildInner(ServerLevel level, int[] pattern) {
        Forge.patternBuilt(pattern);
        for (int i = 0; i < Forge.MARKS.length; i++) {
            BlockPos m = Forge.MARKS[i];
            level.setBlock(m, i < pattern.length ? Forge.woolOf(pattern[i]) : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(m.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(Forge.MARKS[0].above(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), Block.UPDATE_CLIENTS);
        level.setBlock(Forge.FORGE, EmberTrialSetup.ASH_FORGE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        for (BlockPos p : new BlockPos[] {Forge.BELLOWS_A, Forge.BELLOWS_B}) {
            level.setBlock(p, EmberTrialSetup.FORGE_BELLOWS.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // 鍛え手の扉 (3 x 4 x 3 の封印。既定は閉じている。火の鍵で開けたグループにだけ開く)
        GroupGates.place(level, jp.revati.skyabyss.titan.ember.back.EmberBackTrials.boxBlocks(EmberBackLayout.SMITH_DOOR), false);
    }
}
