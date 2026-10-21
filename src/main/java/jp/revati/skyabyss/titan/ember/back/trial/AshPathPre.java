package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// 周回の灰の道 (E01) の前の湖 (32 の 4 章、R5。版 31。trial/LongWalk)。灰の道の部屋の北西の、体の中の空き (x -66..-50, z -100..-22) に掘る。
//   北の岸 (休み場 wp・灰の壺の台・灰溜まり) → 前の第一の湖 (島 2) → 中ノ岸 → 前の第二の湖 (島 1) → 南の岸 → 通り道 → 灰の降る庭の北西の戸 (WEST_PRE_GATE)
// 決まりは灰の道と同じ (灰をまいた冷え殻を渡る・島の灰溜まりで壺が満ちる・溶岩に呑まれると最後に立った所へ)。周回のグループだけが wp へ運ばれ、
// 戸はそのグループの今の段が E01 のときだけ開く
public final class AshPathPre {
    // 岸 (z の範囲) と湖 (z の範囲)
    public static final int[][] BANKS = {{-99, -89}, {-59, -52}, {-29, -23}};
    public static final int[][] LAKES = {{-88, -60}, {-51, -30}};
    // 島 (中心 x, z、半径 2 = 5 × 5)
    public static final int[][] ISLANDS = {{-58, -79}, {-54, -69}, {-59, -41}};

    private AshPathPre() {
    }

    static boolean lavaRow(int z) {
        for (int[] r : LAKES) {
            if (z >= r[0] && z <= r[1]) {
                return true;
            }
        }
        return false;
    }

    static boolean island(int x, int z) {
        for (int[] i : ISLANDS) {
            if (Math.abs(x - i[0]) <= 2 && Math.abs(z - i[1]) <= 2) {
                return true;
            }
        }
        return false;
    }

    // EmberBackRooms.register("west", ...) の二つ目 (灰の道の部屋のあと)
    public static void build(ServerLevel level, EmberBackLayout.Box westBox) {
        EmberBackLayout.Box box = EmberBackLayout.WEST_PRE;
        int floor = EmberBackLayout.FLOOR_WEST;
        RoomKit k = new RoomKit(level, box, floor);
        // 体の中を掘る (箱の内側)
        k.air(box.minX() + 1, box.minY() + 1, box.minZ() + 1, box.maxX() - 1, box.maxY() - 1, box.maxZ() - 1);
        k.shell(true);
        int x1 = box.minX() + 1;
        int x2 = box.maxX() - 1;
        // 岸
        for (int[] b : BANKS) {
            k.fill(x1, box.minY() + 1, b[0], x2, floor - 1, b[1], RoomKit.BASALT);
            k.fill(x1, floor, b[0], x2, floor, b[1], RoomKit.SMOOTH);
        }
        // 湖 (溶岩の面は床と同じ高さ)。島は床の高さまでの柱
        for (int[] r : LAKES) {
            for (int x = x1; x <= x2; x++) {
                for (int z = r[0]; z <= r[1]; z++) {
                    boolean isl = island(x, z);
                    for (int y = floor - 2; y <= floor; y++) {
                        k.set(x, y, z, isl ? (y == floor ? RoomKit.SMOOTH : RoomKit.BASALT) : Blocks.LAVA.defaultBlockState());
                    }
                    k.set(x, floor - 3, z, RoomKit.BLACKSTONE);
                }
            }
        }
        // 灰溜まり (壺が満ちる): 北の岸・島・中ノ岸
        k.ashPool(-53, -97, 3, 2);
        for (int[] i : ISLANDS) {
            k.ashPool(i[0] - 1, i[1] - 1, 2, 2);
        }
        k.ashPool(-62, -57, 3, 2);
        for (int[] b : BANKS) {
            k.drifts(x1, b[0], x2, b[1], 18);
        }
        // 丸天井と壁の肋 (灰の道と同じ言葉)
        k.relief(box.minZ() + 1, box.maxZ() - 1, 10, AshPathPre::lavaRow);
        k.vault(x1, x2, box.minZ() + 1, box.maxZ() - 1, 9, 14, null);
        // 岸の灯りの柱と灰の木
        for (int x : new int[] {-63, -53}) {
            for (int z : new int[] {-91, -55, -26}) {
                k.post(x, z, 4);
            }
        }
        k.ashTree(-63, -97, 5);
        k.ashTree(-52, -24, 4);
        // 灰の壺の台 (北の岸。休み場 wp の東)
        k.set(-53, floor + 1, -93, EmberTrialSetup.ASH_URN_STAND.get().defaultBlockState());
        // 通り道 (南の岸 → 灰の降る庭の北西の戸)
        EmberBackLayout.Box pass = EmberBackLayout.WEST_PRE_PASS;
        RoomKit p = new RoomKit(level, pass.grow(1), floor);
        p.fill(pass.minX() - 1, floor, pass.minZ(), pass.maxX() + 1, pass.maxY() + 1, pass.maxZ(), RoomKit.BRICKS);
        p.fill(pass.minX(), floor + 1, pass.minZ(), pass.maxX(), pass.maxY(), pass.maxZ(), RoomKit.AIR);
        p.fill(pass.minX(), floor, pass.minZ(), pass.maxX(), floor, pass.maxZ(), RoomKit.SMOOTH);
        p.set(pass.minX(), floor + 4, (pass.minZ() + pass.maxZ()) / 2, RoomKit.SHROOM);
        p.set(pass.maxX(), floor + 4, (pass.minZ() + pass.maxZ()) / 2, RoomKit.SHROOM);
        // 南の岸の北の壁の口 (前の湖の箱の南の壁)
        for (int x = pass.minX(); x <= pass.maxX(); x++) {
            for (int y = floor + 1; y <= pass.maxY(); y++) {
                level.setBlock(new BlockPos(x, y, box.maxZ()), RoomKit.AIR, RoomKit.FLAGS);
            }
        }
        // 戸 (灰の降る庭の北の壁。庭の側の一列も空ける)
        for (BlockPos g : EmberBackLayout.WEST_PRE_GATE) {
            level.setBlock(g.south(), RoomKit.AIR, RoomKit.FLAGS);
            level.setBlock(g, ModBlocks.SEALED_GATE.get().defaultBlockState(), RoomKit.FLAGS);
        }
    }

    // 前の湖の中か (呑まれた・灰溜まり・壺の見回りで、灰の道と同じに扱う所)
    public static boolean contains(net.minecraft.world.phys.Vec3 v) {
        return EmberBackLayout.WEST_PRE.contains(v) || EmberBackLayout.WEST_PRE_PASS.grow(1).contains(v);
    }

    public static List<EmberBackLayout.Box> boxes() {
        return List.of(EmberBackLayout.WEST_PRE, EmberBackLayout.WEST_PRE_PASS);
    }
}
