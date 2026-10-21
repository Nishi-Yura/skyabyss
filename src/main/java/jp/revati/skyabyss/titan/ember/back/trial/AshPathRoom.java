package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 灰の道の部屋の中身 (13 の 5.2節、26 の 11 章。部屋の箱は EmberBackLayout.WEST。B が箱を掘ったあとに EmberBackRooms から呼ばれる)。
// 箱: x -62..-24、y 77..106、z -14..112。流れは南へ一本: 入口 → 灰降りの庭 (休み場 w0・壺の台・灰の木・墓所) z -13..14
//   → 試しの流れ (幅いっぱいの溶岩の溝。壺の使い方を教える) z 15..21 → 試しの岸 z 22..31 → 第一の湖 z 32..62 (島 4)
//   → 中ノ岸 (休み場 w1) z 63..76 → 第二の湖 z 77..104 (島 4。蛾と燠虫が出る = ひねり) → 奥の岸 z 105..111 (熾り石)。
// 溶岩は y 78〜80 (面は床と同じ 80)、湖の底は y 77。島と岸は y 77〜80 の柱 (上が床)。
// 規則を一つずつ: 流れで「灰をまくと 12 秒の道」→ 第一の湖で「島で灰を満たす」→ 第二の湖で「蛾が殻を早く崩す・転がる虫が押す」。
public final class AshPathRoom {
    // 墓所 (庭の西の隅。東に口)
    public static final EmberBackLayout.Box TOMB = new EmberBackLayout.Box(-61, EmberBackLayout.FLOOR_WEST, -9, -54, EmberBackLayout.FLOOR_WEST + 6, 3);
    // 灰の木 (x, z, 幹の高さ)
    private static final int[][] TREES = {{-33, -9, 5}, {-30, 1, 6}, {-34, 11, 4}, {-30, 10, 5}, {-55, 10, 5}, {-30, 28, 4}, {-56, 27, 5}, {-33, 67, 5},
            {-55, 68, 4}, {-30, 74, 5}, {-56, 74, 6}, {-31, 108, 4}, {-55, 108, 4}};

    private AshPathRoom() {
    }

    // 岸の z の範囲 (庭・試しの岸・中ノ岸・奥の岸)
    static int[][] banks(EmberBackLayout.Box box) {
        return new int[][] {{box.minZ() + 1, AshPath.CHANNEL_Z1 - 1}, {AshPath.TEACH_Z1, AshPath.TEACH_Z2}, {AshPath.MID_Z1, AshPath.MID_Z2},
                {AshPath.LAKE2_Z2 + 1, box.maxZ() - 1}};
    }

    // EmberBackRooms.register("west", AshPathRoom::build)
    public static void build(ServerLevel level, EmberBackLayout.Box box) {
        RoomKit k = new RoomKit(level, "west");
        int floor = EmberBackLayout.FLOOR_WEST;
        // 外周の壁・床・天井
        k.shell(true);
        // 岸の床 (取っておく場所の床も同じ高さで置く)
        for (int[] b : banks(box)) {
            k.fill(box.minX() + 1, box.minY() + 1, b[0], box.maxX() - 1, floor - 1, b[1], RoomKit.BASALT);
            k.fill(box.minX() + 1, floor, b[0], box.maxX() - 1, floor, b[1], RoomKit.SMOOTH);
        }
        buildLake(level, AshPath.patternOf(level));
        // 灰の吹きだまり (岸の床のまだら)
        for (int[] b : banks(box)) {
            k.drifts(box.minX() + 1, b[0], box.maxX() - 1, b[1], 20);
        }
        // 洞の建築: 丸天井と肋、壁の肋、熔脈 (マグマの筋)
        k.relief(box.minZ() + 1, box.maxZ() - 1, 12, AshPathRoom::lavaRow);
        k.vault(box.minX() + 1, box.maxX() - 1, box.minZ() + 1, box.maxZ() - 1, 14, 22, null);
        k.veins(box.minZ() + 3, box.maxZ() - 3, 9);
        k.veinsEnds(box.minX() + 3, box.maxX() - 3, 6);
        // 奥の岸の石の門 (熾り石の岸を囲む)
        k.stoneGate(-50, -36, AshPath.LAKE2_Z2 + 2, 11);
        // 岸の灯りの柱 (床の灯り。道ぞいに 3 列)
        for (int x : new int[] {-56, -43, -30}) {
            for (int z : new int[] {-9, 3, 12, 25, 30, 65, 75, 108}) {
                if (x == -43 && (z == -9 || z == 108)) {
                    continue;
                }
                k.post(x, z, 4);
            }
        }
        // 灰の木 (庭・岸の灰降りの景色)
        for (int[] t : TREES) {
            k.ashTree(t[0], t[1], t[2]);
        }
        // 灰の壺の台 (入口の庭。休み場 w0 の東)
        k.set(-37, floor + 1, -6, EmberTrialSetup.ASH_URN_STAND.get().defaultBlockState());
        // 灰溜まり (灰色の羊毛。熱が冷える・壺が満ちる): 庭・試しの岸・中ノ岸・奥の岸
        for (int[] p : new int[][] {{-34, 8}, {-54, 8}, {-34, 24}, {-54, 24}, {-34, 65}, {-54, 65}, {-60, 108}}) {
            k.ashPool(p[0], p[1], 3, 2);
        }
        tomb(k);
        // 周回の前の湖と、庭の北西の戸 (版 31。部屋を建て直すたびに一緒に建てる。戸は庭の北の壁にあるため)
        AshPathPre.build(level, box);
    }

    static boolean lavaRow(int z) {
        for (int[] r : AshPath.LAVA_RANGES) {
            if (z >= r[0] && z <= r[1]) {
                return true;
            }
        }
        return false;
    }

    // 墓所: 低い壁で囲った灰の民の墓 (北・南・東。東の壁に 3 幅の口)。棺 2 つと蝋燭、奥の壁に宝箱
    private static void tomb(RoomKit k) {
        int floor = EmberBackLayout.FLOOR_WEST;
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
        for (int x = TOMB.minX(); x <= TOMB.maxX() + 1; x++) {
            k.fill(x, floor + 1, TOMB.minZ() - 1, x, floor + 2, TOMB.minZ() - 1, RoomKit.BRICKS);
            k.fill(x, floor + 1, TOMB.maxZ() + 1, x, floor + 2, TOMB.maxZ() + 1, RoomKit.BRICKS);
        }
        for (int z = TOMB.minZ() - 1; z <= TOMB.maxZ() + 1; z++) {
            if (z >= -4 && z <= -2) {
                continue;
            }
            k.fill(TOMB.maxX() + 1, floor + 1, z, TOMB.maxX() + 1, floor + 2, z, RoomKit.BRICKS);
            k.set(TOMB.maxX() + 1, floor + 3, z, wall);
        }
        // 口の両脇の柱と灯り
        for (int z : new int[] {-5, -1}) {
            k.fill(TOMB.maxX() + 1, floor + 1, z, TOMB.maxX() + 1, floor + 4, z, RoomKit.BRICKS);
            k.set(TOMB.maxX() + 1, floor + 5, z, RoomKit.SHROOM);
        }
        // 棺 (磨いた黒石 1 × 3 と、上の蝋燭)
        for (int z0 : new int[] {-8, 0}) {
            k.fill(-58, floor + 1, z0, -58, floor + 1, z0 + 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            k.set(-58, floor + 2, z0 + 1, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        }
        k.set(-58, floor + 1, -4, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        k.set(-56, floor + 1, -4, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        // 墓所の灯り (奥の壁と天井)
        k.set(-61, floor + 4, -8, RoomKit.SHROOM);
        k.set(-61, floor + 4, 2, RoomKit.SHROOM);
    }

    // 湖を建て直す (建てるときとリセットのとき)。溶岩・島・灰溜まりを置き、湖の上の空気を空ける
    public static void buildLake(ServerLevel level, int pattern) {
        AshPath.lakeBuilt(pattern);
        RoomKit k = new RoomKit(level, "west");
        int floor = EmberBackLayout.FLOOR_WEST;
        int x1 = AshPath.LAKE_X1;
        int x2 = AshPath.LAKE_X2;
        for (int[] r : AshPath.LAVA_RANGES) {
            k.fill(x1, floor - 3, r[0], x2, floor - 3, r[1], RoomKit.BASALT);
            k.fill(x1, floor - 2, r[0], x2, floor, r[1], Blocks.LAVA.defaultBlockState());
            k.air(x1, floor + 1, r[0], x2, floor + 13, r[1]);
        }
        // 溶岩の中の岩の柱 (景色。島とその 4 ブロック・中央の通り道・湖の端の 3 ブロックは避ける)
        java.util.List<AshPath.Island> islands = AshPath.PATTERNS.get(pattern);
        k.stacks(x1 + 1, x2 - 1, AshPath.CHANNEL_Z1 + 1, AshPath.LAKE2_Z2 - 1, 5, 6, (x, z) -> {
            if (!lavaRow(z) || Math.abs(x + 43) <= 6) {
                return true;
            }
            for (int[] r : AshPath.LAVA_RANGES) {
                if (z >= r[0] && z <= r[1] && (z <= r[0] + 2 || z >= r[1] - 2)) {
                    return true;
                }
            }
            for (AshPath.Island i : islands) {
                if (Math.abs(x - i.cx()) <= i.half() + 4 && Math.abs(z - i.cz()) <= i.half() + 4) {
                    return true;
                }
            }
            return false;
        });
        for (AshPath.Island island : AshPath.PATTERNS.get(pattern)) {
            for (int x = island.cx() - island.half(); x <= island.cx() + island.half(); x++) {
                for (int z = island.cz() - island.half(); z <= island.cz() + island.half(); z++) {
                    if (!island.contains(x, z)) {
                        continue;
                    }
                    k.fill(x, floor - 3, z, x, floor - 1, z, RoomKit.BASALT);
                    k.set(x, floor, z, RoomKit.SMOOTH);
                }
            }
            BlockPos pud = island.puddle();
            k.set(pud.getX(), floor, pud.getZ(), Blocks.GRAY_WOOL.defaultBlockState());
        }
    }
}
