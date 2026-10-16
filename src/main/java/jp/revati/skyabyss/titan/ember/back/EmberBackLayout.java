package jp.revati.skyabyss.titan.ember.back;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 余燼の背 (ember_back) の配置図: 部屋の箱と休み場 (docs/design/13_ember.md の 3.3節、11.2節の「部屋の箱の約束」)。
//
// **今の値は骨組み (担当 0) が置いた仮の値**。体の形 (tools/gen_ember_back_body.py → body.bin) ができたら、担当 B が直して公開する。
// C1・C2・C3・E・F は、自分の箱の中だけにブロックを置く (箱の外の壁・床・天井は B)。箱が足りなければ B に頼む (自分で外を削らない)。
// 箱の中の座標は、できるだけ箱の角 (min) や中心からの相対で書くこと。B が箱を動かしても、中身はそのまま付いて来る。
//
// 座標の決まり (潮殻の TideBackLayout と同じ): y は「床のブロックの y」(人が立つのは +1)。頭は北 (-Z)、口は北の端。
// 灰の原の地表 (いちばん上のブロック) は y 63 (PLAIN_TOP)。体の中心の左右は x 0。
//
//                         [口: 到着 (舌の上)]  z -150 前後、床 65。北に見送りの段 → 灰の原
//                               | 喉の坂 (休み場 throat)
//                        [炉心の広間 HUB] 中心 (0, 85, -60) 半径 14
//                               | 螺旋の階段 (大きな炉の下)
//                   [熾の底の前室 HEART_ANTE] (休み場 heart)
//   [灰の道 WEST]       [熾の底 HEART] 灰の原より下 (腹の底)       [鍛えの間 EAST] → [鍛え手の間 SMITH]
//                               |
//                        [火守り SOUTH] 背骨の下の谷 (上は背の亀裂)
public final class EmberBackLayout {
    // 配置の版。上がったら体を建て直す (TitanBackData.markBuilt)。1: 骨組みの仮の箱 (体はまだ建てない)
    public static final int LAYOUT_VERSION = 1;
    // 灰の原のいちばん上のブロックの y (dimension/ember_back.json の層と合わせる)
    public static final int PLAIN_TOP = 63;
    // 体の中心 (遠くへ歩いたかを見る。2.5節の 220 ブロック)
    public static final int BODY_CX = 0;
    public static final int BODY_CZ = 0;
    public static final double FAR = 220;
    // 体の外形の箱 (頭の先から尾の先まで約 370、脚を入れた幅 約 100。B が body.bin に合わせて直す)
    public static final Box BODY = new Box(-60, 40, -190, 60, 150, 190);

    // ---------------------------------------------------------------- 箱 (両端を含む整数の箱)
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("empty box");
            }
        }

        public boolean contains(BlockPos p) {
            return contains(p.getX(), p.getY(), p.getZ());
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public boolean contains(Vec3 v) {
            return v.x >= minX && v.x < maxX + 1 && v.y >= minY && v.y < maxY + 1 && v.z >= minZ && v.z < maxZ + 1;
        }

        public boolean intersects(Box o) {
            return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
        }

        public boolean inside(Box o) {
            return minX >= o.minX && maxX <= o.maxX && minY >= o.minY && maxY <= o.maxY && minZ >= o.minZ && maxZ <= o.maxZ;
        }

        public BlockPos min() {
            return new BlockPos(minX, minY, minZ);
        }

        public BlockPos center() {
            return new BlockPos((minX + maxX) >> 1, (minY + maxY) >> 1, (minZ + maxZ) >> 1);
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        // エンティティの検索用 (ブロックの箱いっぱい)
        public AABB aabb() {
            return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }

    // ---------------------------------------------------------------- 口と喉 (→ B が建てる。A の乗り込み・見送りが使う)
    // 到着点 (舌の上の床のブロック。人は +1 に立つ) と向き (南 = 喉の奥を向く)
    public static final BlockPos ARRIVAL = new BlockPos(0, 65, -150);
    public static final float ARRIVAL_YAW = 0f;
    // 見送りの石 (3 × 2。いちばん下の段、灰の原の上)。ここに 0.5 秒いると地上へ (2.5節)
    public static final Box RETURN_STONE = new Box(-1, PLAIN_TOP + 1, -173, 1, PLAIN_TOP + 3, -172);
    // 口の中 (到着・見送りの段。ピースフルでも歩ける所)
    public static final Box MOUTH = new Box(-6, PLAIN_TOP + 1, -174, 6, 80, -142);

    // ---------------------------------------------------------------- 試練と部屋の箱 (11.2節の約束。中身を建てる担当を右に)
    // 炉心の広間 (円 半径 14、天井まで 18。中央の大きな炉が熾の底の門) → B
    public static final BlockPos HUB_CENTER = new BlockPos(0, 85, -60);
    public static final int HUB_RADIUS = 14;
    public static final Box HUB = new Box(-15, 85, -75, 15, 103, -45);
    // 灰の道 (溶岩の湖 約 40 × 60、天井まで 20) → C1
    public static final Box WEST = new Box(-47, 70, -30, -8, 92, 30);
    // 鍛えの間 (約 36 × 40、天井まで 16。大炉・鞴・金床・槽) → C2
    public static final Box EAST = new Box(8, 80, -30, 43, 96, 9);
    // 鍛え手の間 (円 半径 13、天井 14) → E
    public static final BlockPos SMITH_CENTER = new BlockPos(26, 80, 26);
    public static final int SMITH_RADIUS = 13;
    public static final Box SMITH = new Box(12, 80, 12, 40, 94, 40);
    // 火守り (幅 20・長さ 70・高さ 40 の谷。上は背の亀裂) → C3
    public static final Box SOUTH = new Box(-10, 70, 45, 10, 110, 115);
    // 熾の底の前室 (6 × 6) → B (碑文 lore_ember_heart と休み場 heart)
    public static final Box HEART_ANTE = new Box(-3, 57, -43, 2, 62, -38);
    // 熾の底 (円 半径 16、天井まで 14。床の輪: 台 0〜4、区画 4〜15、溝 15〜16。区画の下に深さ 3 の溶岩の溜め) → F
    public static final BlockPos HEART_CENTER = new BlockPos(0, 56, -20);
    public static final int HEART_RADIUS = 16;
    public static final Box HEART = new Box(-17, 52, -37, 17, 69, -3);

    // 部屋の名前 → 箱 (コマンドの go・自己テストの重なりの検査・リセットで中の人を広間へ移す範囲)
    public static final Map<String, Box> ROOMS = rooms();

    private static Map<String, Box> rooms() {
        Map<String, Box> m = new LinkedHashMap<>();
        m.put("mouth", MOUTH);
        m.put("hub", HUB);
        m.put("west", WEST);
        m.put("east", EAST);
        m.put("smith", SMITH);
        m.put("south", SOUTH);
        m.put("heart_ante", HEART_ANTE);
        m.put("heart", HEART);
        return java.util.Collections.unmodifiableMap(m);
    }

    // ---------------------------------------------------------------- 休み場 (7.3節。焚き火の前の床のブロック) → B
    public record Rest(String name, BlockPos floor, float yaw) {
    }

    public static final List<Rest> RESTS = List.of(
            new Rest("throat", new BlockPos(0, 75, -115), 0f),
            new Rest("hub", new BlockPos(0, 85, -52), 180f),
            new Rest("w0", new BlockPos(-12, 72, -26), 90f),
            new Rest("w1", new BlockPos(-28, 72, 0), 90f),
            new Rest("e0", new BlockPos(12, 80, -26), -90f),
            new Rest("s0", new BlockPos(0, 70, 48), 0f),
            new Rest("heart", new BlockPos(0, 57, -40), 0f));

    public static Rest rest(String name) {
        for (Rest r : RESTS) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown rest " + name);
    }

    private EmberBackLayout() {
    }
}
