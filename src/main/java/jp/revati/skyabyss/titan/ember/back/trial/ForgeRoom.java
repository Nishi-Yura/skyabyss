package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

// 鍛えの間の部屋の中身 (13 の 4.2節。部屋の箱は EmberBackLayout.EAST: x 13..39、y 80..95、z -8..28、床 80)。
// 前室 (z -8..5、休み場 e0) → 柱の列 (z 6) → 大炉と鞴 (z 20〜26)・金床と刻み (z 11〜12)・焼き入れの槽 (z 13)。
// 奥 (z 29..31) の 3 枚の壁に、鍛え手の扉 (封印の扉) を置く。扉の前は B の取っておく場所
public final class ForgeRoom {
    private ForgeRoom() {
    }

    // EmberBackRooms.register("east", ForgeRoom::build)
    public static void build(ServerLevel level, EmberBackLayout.Box box) {
        RoomKit k = new RoomKit(level, "east");
        int floor = EmberBackLayout.FLOOR_EAST;
        k.shell(true);
        k.fill(box.minX() + 1, floor, box.minZ() + 1, box.maxX() - 1, floor, box.maxZ() - 1, RoomKit.SMOOTH);
        // 柱の列 (前室と炉の間)
        for (int x : new int[] {16, 22, 30, 36}) {
            k.fill(x, floor + 1, 6, x, floor + 5, 6, RoomKit.BRICKS);
            k.set(x, floor + 6, 6, Blocks.LANTERN.defaultBlockState());
        }
        // 大炉: 本体と煙突。口 (炉の塊) は FORGE の位置、まわりの枠はレンガとマグマ
        k.fill(23, floor + 1, 22, 29, floor + 6, 26, RoomKit.BRICKS);
        k.fill(25, floor + 7, 23, 27, box.maxY() - 1, 25, RoomKit.NETHER_BRICKS);
        k.fill(25, floor + 1, 22, 27, floor + 3, 22, RoomKit.BRICKS);
        k.set(25, floor + 2, 22, RoomKit.MAGMA);
        k.set(27, floor + 2, 22, RoomKit.MAGMA);
        k.set(Forge.FORGE, EmberTrialSetup.ASH_FORGE.get().defaultBlockState());
        // 鞴の踏み板と、その脇の柱
        k.set(Forge.BELLOWS_A, EmberTrialSetup.FORGE_BELLOWS.get().defaultBlockState());
        k.set(Forge.BELLOWS_B, EmberTrialSetup.FORGE_BELLOWS.get().defaultBlockState());
        for (int x : new int[] {20, 32}) {
            k.fill(x, floor + 1, 20, x, floor + 3, 20, RoomKit.BRICKS);
        }
        // 金床と刻みの壁
        k.set(Forge.ANVIL, EmberTrialSetup.ASH_ANVIL.get().defaultBlockState());
        k.fill(23, floor + 1, 11, 28, floor + 2, 11, RoomKit.BRICKS);
        k.set(23, floor + 3, 11, RoomKit.BRICKS);
        k.set(28, floor + 3, 11, RoomKit.BRICKS);
        k.set(23, floor + 4, 11, Blocks.LANTERN.defaultBlockState());
        k.set(28, floor + 4, 11, Blocks.LANTERN.defaultBlockState());
        k.set(Forge.TROUGH_B.getX() + 1, floor + 1, Forge.TROUGH_B.getZ(), RoomKit.BRICKS);
        k.set(Forge.TROUGH_B.getX() + 1, floor + 2, Forge.TROUGH_B.getZ(), Blocks.LANTERN.defaultBlockState());
        // 焼き入れの槽
        k.set(Forge.TROUGH_A, EmberTrialSetup.QUENCH_TROUGH.get().defaultBlockState());
        k.set(Forge.TROUGH_B, EmberTrialSetup.QUENCH_TROUGH.get().defaultBlockState());
        // 天井の灯
        for (int x : new int[] {20, 32}) {
            for (int z : new int[] {0, 12, 22}) {
                k.set(x, box.maxY(), z, Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
        buildInner(level, Forge.patternFor(jp.revati.skyabyss.titan.ember.back.EmberBackState.get(level).firstForgeUsed(),
                jp.revati.skyabyss.titan.ember.back.EmberBackState.get(level).seed()));
    }

    // リセットで直す所: 金床の刻みの色 (打つ順)、大炉の口、扉
    static void buildInner(ServerLevel level, int[] pattern) {
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
        // 鍛え手の扉 (3 x 4 x 3 の封印)
        BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
        EmberBackLayout.Box d = EmberBackLayout.SMITH_DOOR;
        for (int x = d.minX(); x <= d.maxX(); x++) {
            for (int y = d.minY(); y <= d.maxY(); y++) {
                for (int z = d.minZ(); z <= d.maxZ(); z++) {
                    level.setBlock(new BlockPos(x, y, z), gate, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
