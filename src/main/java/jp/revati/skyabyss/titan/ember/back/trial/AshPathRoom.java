package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 灰の道の部屋の中身 (13 の 4.1節。部屋の箱は EmberBackLayout.WEST。B が箱を掘ったあとに EmberBackRooms から呼ばれる)。
// 箱: x -37..-13、y 73..94、z -8..56。入口の岸 (z -8..-1) → 溶岩の湖 (x -36..-14、z 0..48) と島 7 つ → 奥の岸 (z 49..56)。
// 溶岩は y 74〜76 (面は床と同じ 76)、湖の底は y 73。島と岸は y 74〜76 の柱 (上が床)。
public final class AshPathRoom {
    private AshPathRoom() {
    }

    // EmberBackRooms.register("west", AshPathRoom::build)
    public static void build(ServerLevel level, EmberBackLayout.Box box) {
        RoomKit k = new RoomKit(level, "west");
        int floor = EmberBackLayout.FLOOR_WEST;
        // 外周の壁・床・天井
        k.shell(true);
        // 岸の床 (入口の岸と奥の岸。取っておく場所の床も同じ高さで置く)
        k.fill(box.minX() + 1, box.minY(), box.minZ() + 1, box.maxX() - 1, floor, AshPath.LAKE_Z1 - 1, RoomKit.SMOOTH);
        k.fill(box.minX() + 1, box.minY(), AshPath.LAKE_Z2 + 1, box.maxX() - 1, floor, box.maxZ() - 1, RoomKit.SMOOTH);
        // 入口の通り道の床 (B の通路。床だけ)
        buildLake(level, AshPath.patternOf(level));
        // 灰の壺の台 (入口の岸。休み場 w0 の東)
        k.set(-19, floor + 1, -4, EmberTrialSetup.ASH_URN_STAND.get().defaultBlockState());
        // 岸の飾り: 壁ぎわの柱と、奥の岸の灰溜まり
        for (int x : new int[] {-34, -16}) {
            for (int z : new int[] {-6, 52}) {
                k.fill(x, floor + 1, z, x, floor + 4, z, RoomKit.BRICKS);
                k.set(x, floor + 5, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        k.set(-34, floor, 52, Blocks.GRAY_WOOL.defaultBlockState());
    }

    // 湖を建て直す (建てるときとリセットのとき)。溶岩・島・灰溜まりを置き、湖の上の空気を空ける
    public static void buildLake(ServerLevel level, int pattern) {
        AshPath.lakeBuilt(pattern);
        RoomKit k = new RoomKit(level, "west");
        int floor = EmberBackLayout.FLOOR_WEST;
        int x1 = AshPath.LAKE_X1;
        int x2 = AshPath.LAKE_X2;
        int z1 = AshPath.LAKE_Z1;
        int z2 = AshPath.LAKE_Z2;
        k.fill(x1, 73, z1, x2, 73, z2, RoomKit.BASALT);
        k.fill(x1, 74, z1, x2, floor, z2, Blocks.LAVA.defaultBlockState());
        k.air(x1, floor + 1, z1, x2, EmberBackLayout.WEST.maxY() - 1, z2);
        for (AshPath.Island island : AshPath.PATTERNS.get(pattern)) {
            for (int x = island.cx() - island.half(); x <= island.cx() + island.half(); x++) {
                for (int z = island.cz() - island.half(); z <= island.cz() + island.half(); z++) {
                    if (!island.contains(x, z)) {
                        continue;
                    }
                    k.fill(x, 74, z, x, floor - 1, z, RoomKit.BASALT);
                    k.set(x, floor, z, RoomKit.SMOOTH);
                }
            }
            BlockPos pud = island.puddle();
            k.set(pud.getX(), floor, pud.getZ(), Blocks.GRAY_WOOL.defaultBlockState());
            if (island.w1()) {
                // 休み場の台の東へ一つはみ出した灰溜まりの足場 (台の外は B が触らない)
                k.fill(pud.getX(), 74, pud.getZ(), pud.getX(), floor - 1, pud.getZ(), RoomKit.BASALT);
            }
        }
    }
}
