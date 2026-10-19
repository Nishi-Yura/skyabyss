package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// 火守りの谷の部屋の中身 (13 の 5.4節、26 の 9 章。部屋の箱は EmberBackLayout.SOUTH: x -17..17、y 83..112、z -8..66、床 83)。
// 流れは南へ一本: 入口 (z -7) → 灰降りの庭 (z -7..10。休み場 s0・灰の木・宝箱) → 灰運びの道と噴気の谷 (z 11..40。噴気の割れ目 16 本、
//   灰溜まり 4 か所、燠虫 2 体) → 火床の壇 (z 44..48。火床は z 46) → 熾り石の岸 (z 56..65)。
// 天井の中ほど (z 22..52) は背の亀裂 (CRACK) へ抜ける (背の上から灰が降りる所)。亀裂そのものは箱の外 (B)。
// 規則を一つずつ: 庭の碑で「熾を火床へ」→ 谷の碑で「噴気は 16 秒で巡る」→ 灰運びを噴気の上へ誘う (ひねり)。
public final class HearthRoom {
    // 灰溜まり (灰色の羊毛): 谷の 4 か所。立つと熱が冷える (B)
    public static final BlockPos[] PUDDLES = {new BlockPos(-12, EmberBackLayout.FLOOR_SOUTH, 16), new BlockPos(12, EmberBackLayout.FLOOR_SOUTH, 23),
            new BlockPos(-12, EmberBackLayout.FLOOR_SOUTH, 35), new BlockPos(12, EmberBackLayout.FLOOR_SOUTH, 40)};
    // 灰の木 (x, z, 幹の高さ)
    private static final int[][] TREES = {{-12, -3, 5}, {12, -3, 6}, {-13, 7, 5}, {13, 8, 4}, {-8, 10, 4}, {8, 4, 5}, {-14, 52, 4}, {14, 51, 5}};

    private HearthRoom() {
    }

    // EmberBackRooms.register("south", HearthRoom::build)
    public static void build(ServerLevel level, EmberBackLayout.Box box) {
        RoomKit k = new RoomKit(level, "south");
        int floor = EmberBackLayout.FLOOR_SOUTH;
        k.shell(true);
        k.fill(box.minX() + 1, floor, box.minZ() + 1, box.maxX() - 1, floor, box.maxZ() - 1, RoomKit.SMOOTH);
        k.drifts(box.minX() + 1, box.minZ() + 1, box.maxX() - 1, box.maxZ() - 1, 18);
        // 背の亀裂の下: 天井を抜く
        EmberBackLayout.Box crack = EmberBackLayout.CRACK;
        k.air(crack.minX(), box.maxY(), crack.minZ(), crack.maxX(), box.maxY(), crack.maxZ());
        // 谷の両壁に熱い継ぎ目 (マグマ) と灯り (茂み)
        for (int z = 10; z <= 62; z += 6) {
            k.set(box.minX() + 1, floor + 2, z, RoomKit.MAGMA);
            k.set(box.maxX() - 1, floor + 2, z, RoomKit.MAGMA);
            k.set(box.minX() + 1, floor + 7, z + 3, RoomKit.SHROOM);
            k.set(box.maxX() - 1, floor + 7, z + 3, RoomKit.SHROOM);
        }
        // 壁ぎわの灯りの柱
        for (int z : new int[] {5, 17, 29, 41, 53, 62}) {
            k.post(-15, z, 4);
            k.post(15, z, 4);
        }
        // 灰降りの庭: 灰の木
        for (int[] t : TREES) {
            k.ashTree(t[0], t[1], t[2]);
        }
        // 火床の台 (5 x 5) とそのまわりの赤い縁 (火の座)
        BlockPos h = Hearth.HEARTH;
        for (int x = -3; x <= 3; x++) {
            for (int z = h.getZ() - 3; z <= h.getZ() + 3; z++) {
                boolean inner = Math.abs(x) <= 2 && Math.abs(z - h.getZ()) <= 2;
                k.set(x, floor, z, inner ? RoomKit.BRICKS : RoomKit.NETHER_BRICKS);
            }
        }
        for (int dx : new int[] {-4, 4}) {
            for (int dz : new int[] {-3, 3}) {
                k.post(dx, h.getZ() + dz, 5);
            }
        }
        // 灰運びの道: 入口から火床の手前まで、低い壁 (高さ 2) に挟まれた幅 5 の道。灰運びはここを歩く。壁の隙 (2 か所) から人が誘い込める。
        // 道の床は黒石にして、道とわかるようにする。人の通り道は両脇 (x ±4〜16)。壁の上は空いていて、飛び道具は通る
        for (int z = HearthVents.LANE_Z0; z <= HearthVents.LANE_Z1; z++) {
            for (int x = -2; x <= 2; x++) {
                k.set(x, floor, z, RoomKit.BLACKSTONE);
            }
            if (z < HearthVents.LANE_GAP_Z0 || z > HearthVents.LANE_GAP_Z1) {
                for (int hh = 1; hh <= 2; hh++) {
                    k.set(-HearthVents.LANE_WALL_X, floor + hh, z, RoomKit.BRICKS);
                    k.set(HearthVents.LANE_WALL_X, floor + hh, z, RoomKit.BRICKS);
                }
            } else {
                k.set(-HearthVents.LANE_WALL_X, floor, z, RoomKit.BLACKSTONE);
                k.set(HearthVents.LANE_WALL_X, floor, z, RoomKit.BLACKSTONE);
            }
        }
        // 灰運びの道の両端に門柱 (谷の入口と火床の手前)
        for (int z : new int[] {HearthVents.LANE_Z0 - 1, HearthVents.LANE_Z1 + 1}) {
            for (int x : new int[] {-3, 3}) {
                k.fill(x, floor + 1, z, x, floor + 4, z, RoomKit.BRICKS);
                k.set(x, floor + 5, z, RoomKit.SHROOM);
            }
        }
        // 噴気の割れ目 (床のブロックを置き替える)
        for (BlockPos v : HearthVents.VENTS) {
            k.set(v, EmberTrialSetup.HEAT_VENT.get().defaultBlockState());
        }
        for (BlockPos p : PUDDLES) {
            k.ashPool(p.getX(), p.getZ(), 3, 2);
        }
        // 天井の鍾乳 (亀裂の下は除く)
        int top = box.maxY() - 1;
        for (int x = box.minX() + 2; x <= box.maxX() - 2; x += 2) {
            for (int z = box.minZ() + 2; z <= box.maxZ() - 2; z += 2) {
                int hs = RoomKit.hash(x, 12, z);
                boolean underCrack = x >= crack.minX() - 1 && x <= crack.maxX() + 1 && z >= crack.minZ() - 1 && z <= crack.maxZ() + 1;
                if (hs < 45 && !underCrack) {
                    int len = 2 + hs % 4;
                    for (int y = top; y > top - len; y--) {
                        k.set(x, y, z, RoomKit.BASALT);
                    }
                }
            }
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
