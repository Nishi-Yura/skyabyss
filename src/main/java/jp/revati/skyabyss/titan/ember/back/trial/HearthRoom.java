package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// 火守りの谷の部屋の中身 (13 の 4.3節。部屋の箱は EmberBackLayout.SOUTH: x -10..10、y 78..108、z -6..62、床 78)。
// 入口 (z -6、休み場 s0) → 谷 (噴気の割れ目 10 本、灰溜まり 3 か所、燠虫 2 体) → 奥の火床 (z 52) → 熾り石の岸 (z 56〜62)。
// 天井の中ほど (z 14..56) は背の亀裂 (CRACK) へ抜ける。亀裂そのものは箱の外 (B)
public final class HearthRoom {
    // 灰溜まり (灰色の羊毛): 谷の 3 か所。立つと熱が冷える (B)
    public static final BlockPos[] PUDDLES = {new BlockPos(-8, EmberBackLayout.FLOOR_SOUTH, 22), new BlockPos(8, EmberBackLayout.FLOOR_SOUTH, 40),
            new BlockPos(-7, EmberBackLayout.FLOOR_SOUTH, 48)};

    private HearthRoom() {
    }

    // EmberBackRooms.register("south", HearthRoom::build)
    public static void build(ServerLevel level, EmberBackLayout.Box box) {
        RoomKit k = new RoomKit(level, "south");
        int floor = EmberBackLayout.FLOOR_SOUTH;
        k.shell(true);
        k.fill(box.minX() + 1, floor, box.minZ() + 1, box.maxX() - 1, floor, box.maxZ() - 1, RoomKit.SMOOTH);
        // 背の亀裂の下: 天井を抜く
        EmberBackLayout.Box crack = EmberBackLayout.CRACK;
        k.air(crack.minX(), box.maxY(), crack.minZ(), crack.maxX(), box.maxY(), crack.maxZ());
        // 谷の両壁に熱い継ぎ目 (マグマ) と岩の柱
        for (int z = 4; z <= 58; z += 6) {
            k.set(box.minX() + 1, floor + 2, z, RoomKit.MAGMA);
            k.set(box.maxX() - 1, floor + 2, z, RoomKit.MAGMA);
        }
        // 火床の台 (5 x 5)
        for (int x = -2; x <= 2; x++) {
            for (int z = 50; z <= 54; z++) {
                k.set(x, floor, z, RoomKit.BRICKS);
            }
        }
        // 噴気の割れ目 (床のブロックを置き替える)
        for (BlockPos v : HearthVents.VENTS) {
            k.set(v, EmberTrialSetup.HEAT_VENT.get().defaultBlockState());
        }
        for (BlockPos p : PUDDLES) {
            k.set(p, Blocks.GRAY_WOOL.defaultBlockState());
        }
        buildInner(level);
    }

    // リセットで直す所: 火床 (火は 20 に戻る)・噴気の見た目
    static void buildInner(ServerLevel level) {
        BlockPos h = Hearth.HEARTH;
        level.setBlock(h, EmberTrialSetup.EMBER_HEARTH.get().defaultBlockState().setValue(HearthBlock.LEVEL, Hearth.level(Hearth.START)),
                Block.UPDATE_CLIENTS);
        for (BlockPos v : HearthVents.VENTS) {
            if (level.getBlockState(v).is(EmberTrialSetup.HEAT_VENT.get())) {
                level.setBlock(v, EmberTrialSetup.HEAT_VENT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
