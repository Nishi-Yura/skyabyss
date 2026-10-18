package jp.revati.skyabyss.titan.ember.back;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 余燼の背 (ember_back) の配置図: 部屋の箱と休み場 (docs/design/13_ember.md の 3.3節、11.2節の「部屋の箱の約束」、18 章)。
//
// **担当 B が体 (body.bin、推奨 A 火山の背を 1.7 倍) に合わせて確定した値** (版 2)。名前は骨組みのまま。
// C1・C2・C3・E・F は、自分の箱の中だけにブロックを置く (箱の外の壁・床・天井・通路は B)。箱が足りなければ B に頼む (自分で外を削らない)。
// 箱の中の座標は、できるだけ箱の角 (min) や中心からの相対で書くこと。
//
// 建てる順 (EmberBodyBuilder): 体を置く → B が箱を掘る (箱の中はすべて空気、いちばん下の段 minY だけ仮の床) と、箱の外の壁・通路・広間
//   → 各担当が EmberBackRooms.register で渡した建て方を、west・east・smith・south・heart の順に呼ぶ
//   → 最後に B が「取っておく場所」(reserved) に休み場・熾り石・光る床・碑文を置く (担当の中身より後なので、上書きされない)。
// **取っておく場所 (reserved(部屋))**: 担当は、ここにブロックを置かない (置いても B が上書きする)。床の高さは各 FLOOR_* にそろえる。
//
// 座標の決まり (潮殻の TideBackLayout と同じ): y は「床のブロックの y」(人が立つのは +1)。頭は北 (-Z)、口は北の端。西 (-X) が巨神の左。
// 灰の原の地表 (いちばん上のブロック) は y 63 (PLAIN_TOP)。体の中心の左右は x 0。
//
//   z -178  [口: 到着 (舌の上) 床 68]  北の顎の先に見送りの段 → 灰の原
//   z -134    | 喉の坂 (幅 6、上り。床 68 → 85。中ほどに休み場 throat 床 76)
//   z  -49  [炉心の広間 HUB 中心 (0, 85, -34) 半径 14]  天井の中央から火の井戸 (火山の火口へ)
//              | 中央の大きな炉の下 (熾の底の門) から螺旋で下へ (床 50)
//   z  -29  [熾の底の前室 HEART_ANTE 床 50] → 扉 z -22 → [熾の底 HEART 中心 (0, 50, -4)] (灰の原より下)
//   z   -8  [灰の道 WEST 西の脇腹]  [火守り SOUTH 背骨の下の谷、上は背の亀裂]  [鍛えの間 EAST 東の脇腹]
//   z   32                                                                     [鍛え手の間 SMITH]
//   z   62  (谷の奥)
public final class EmberBackLayout {
    // 配置の版。上がったら体を建て直す (TitanBackData.markBuilt)。1: 骨組みの仮の箱 (体はまだ建てない)、2: 体と部屋を建てる、3: 口の灯り・炉の煙を低く
    public static final int LAYOUT_VERSION = 4;
    // 灰の原のいちばん上のブロックの y (dimension/ember_back.json の層と合わせる)
    public static final int PLAIN_TOP = 63;
    // 体の中心 (遠くへ歩いたかを見る。2.5節の 220 ブロック)
    public static final int BODY_CX = 0;
    public static final int BODY_CZ = 0;
    public static final double FAR = 230;
    // 体の外形の箱 (body.bin の表の範囲: x -107〜106、y 64〜226、z -179〜212。ブロックは頭の先 z -175・尾の先 z 210・脚を入れた幅 x -101〜100・火口の縁 y 175。熾の底は灰の原の下 y 46 から)
    public static final Box BODY = new Box(-100, 44, -180, 100, 200, 226);

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

        public BlockPos max() {
            return new BlockPos(maxX, maxY, maxZ);
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

        public Box grow(int d) {
            return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
        }

        // エンティティの検索用 (ブロックの箱いっぱい)
        public AABB aabb() {
            return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }

    // ---------------------------------------------------------------- 口と喉 (→ B が建てる。A の乗り込み・見送りが使う)
    // 到着点 (舌の上の床のブロック。人は +1 に立つ) と向き (南 = 喉の奥を向く)
    public static final int MOUTH_FLOOR = 68;
    public static final BlockPos ARRIVAL = new BlockPos(0, MOUTH_FLOOR, -150);
    public static final float ARRIVAL_YAW = 0f;
    // 見送りの石 (3 × 2。下の顎の先の外、灰の原の上の空気の箱)。ここに 0.5 秒いると地上へ (2.5節)。口の床からは 5 段の段で下りる
    public static final Box RETURN_STONE = new Box(-1, PLAIN_TOP + 1, -178, 1, PLAIN_TOP + 3, -177);
    // 口の中 (到着・見送りの段。ピースフルでも歩ける所)
    public static final Box MOUTH = new Box(-7, PLAIN_TOP + 1, -178, 7, 79, -134);
    // 喉の坂の休み場の平らな所 (床 76)
    public static final int THROAT_LANDING = 76;

    // ---------------------------------------------------------------- 試練と部屋の箱 (11.2節の約束。中身を建てる担当を右に)
    // 炉心の広間 (円 半径 14、天井まで 18。中央の大きな炉が熾の底の門。天井の中央から火の井戸) → B
    public static final BlockPos HUB_CENTER = new BlockPos(0, 85, -34);
    public static final int HUB_RADIUS = 14;
    public static final Box HUB = new Box(-15, 85, -49, 15, 103, -19);
    // 灰の道 (溶岩の湖 25 × 65、天井まで 21) → C1。入口の岸と奥の岸の床は FLOOR_WEST (溶岩の面はその 1 下を勧める)
    public static final int FLOOR_WEST = 76;
    public static final Box WEST = new Box(-37, 73, -8, -13, 94, 56);
    // 鍛えの間 (27 × 37、天井まで 15。大炉・鞴・金床・槽) → C2。奥 (z 28) の壁に鍛え手の扉の通り道 SMITH_DOOR
    public static final int FLOOR_EAST = 80;
    public static final Box EAST = new Box(13, 80, -8, 39, 95, 28);
    // 鍛え手の間 (円 半径 13、天井 14) → E
    public static final BlockPos SMITH_CENTER = new BlockPos(26, 80, 45);
    public static final int SMITH_RADIUS = 13;
    public static final Box SMITH = new Box(13, 80, 32, 39, 94, 58);
    // 火守り (幅 21・長さ 69・高さ 31 の谷。上は背の亀裂 CRACK で、背の上まで抜けて空が見える) → C3
    public static final int FLOOR_SOUTH = 78;
    public static final Box SOUTH = new Box(-10, 78, -6, 10, 108, 62);
    // 熾の底の前室 (9 × 7。螺旋の階段の下) → B (碑文 lore_ember_heart と休み場 heart)
    public static final int FLOOR_HEART = 50;
    public static final Box HEART_ANTE = new Box(-4, FLOOR_HEART, -29, 4, 56, -23);
    // 熾の底 (円 半径 16、天井まで 14。床の輪: 台 0〜4、区画 4〜15、溝 15〜16。区画の下に深さ 3 の溶岩の溜め) → F
    public static final BlockPos HEART_CENTER = new BlockPos(0, FLOOR_HEART, -4);
    public static final int HEART_RADIUS = 16;
    public static final Box HEART = new Box(-17, 46, -21, 17, 64, 13);

    // ---------------------------------------------------------------- 通り道 (B が壁を抜く。中身の担当は、箱の中の続きを空けておく)
    // 試練の入口 (広間からの通路が箱に入る所。箱の外の 1 枚の壁。3 幅 × 4 高)
    public static final Box WEST_DOOR = new Box(-26, FLOOR_WEST + 1, -9, -24, FLOOR_WEST + 4, -9);
    public static final Box EAST_DOOR = new Box(20, FLOOR_EAST + 1, -9, 22, FLOOR_EAST + 4, -9);
    public static final Box SOUTH_DOOR = new Box(-1, FLOOR_SOUTH + 1, -7, 1, FLOOR_SOUTH + 4, -7);
    // 鍛えの間の奥 → 鍛え手の間 (EAST と SMITH のあいだの 3 枚の壁)。扉 (SealedGateBlock) を置くのは C2、戦いの間に閉じるのは E (BossRoom)
    public static final Box SMITH_DOOR = new Box(25, FLOOR_EAST + 1, 29, 27, FLOOR_EAST + 4, 31);
    // 前室 → 熾の底 (1 枚の壁)。封印を置いて開け閉めするのは F (BossRoom)
    public static final Box HEART_DOOR = new Box(-1, FLOOR_HEART + 1, -22, 1, FLOOR_HEART + 4, -22);
    // 広間の扉 (円の縁の外の 1 枚の壁の真ん中のブロック。ここから通路)
    public static final BlockPos HUB_DOOR_NORTH = new BlockPos(0, 86, -49);
    public static final BlockPos HUB_DOOR_WEST = new BlockPos(-15, 86, -34);
    public static final BlockPos HUB_DOOR_EAST = new BlockPos(15, 86, -34);
    public static final BlockPos HUB_DOOR_SOUTH = new BlockPos(0, 86, -19);

    // ---------------------------------------------------------------- 熾の底の門・火の井戸・背の亀裂 (→ B)
    // 広間の中央の大きな炉の底の格子 (3 × 3、広間の床の高さ)。三つの石がそろうと開き、下の螺旋の階段へ
    public static final Box HEART_GATE = new Box(-1, 85, -35, 1, 85, -33);
    // 門の下の螺旋の縦穴 (格子の真下の 3 × 3。中央の柱のまわりを 1 段ずつ下りる)。前室の床 (50) まで。底から南へ短い通路で前室へ
    public static final Box GATE_SHAFT = new Box(-1, FLOOR_HEART + 1, -35, 1, 84, -33);
    // 火の井戸 (広間の天井の中央から火山の火口まで。半径 3 の縦穴、そのまわりに半径 6.6 までの螺旋の段 (内側に手すり))。
    // 広間の南東の壁の中の小さな螺旋 (WELL_STAIR) で床 105 まで上り、短い通路で井戸の螺旋の下の端へ。井戸の螺旋の途中から南へ背の上への出口
    public static final double WELL_RADIUS = 6.6;
    public static final int WELL_BOTTOM = 105;
    public static final BlockPos WELL_STAIR = new BlockPos(8, 85, -20);
    // 背の上への出口の通路の床 (井戸の南から z -1 まで)
    public static final int BACK_EXIT_FLOOR = 118;
    // 背の亀裂 (火守りの谷の天井から背の上まで。上に格子の手すり)
    public static final Box CRACK = new Box(-2, 109, 14, 2, 140, 56);

    // ---------------------------------------------------------------- 熾り石・抜け道の光る床・碑文 (→ B が置く。担当の箱の中の reserved)
    // 熾り石 (ブロックの位置。床 +1)。西は奥の岸、東は鍛え手の間の奥、南は谷の奥 (火床の向こう)
    public static final BlockPos STONE_WEST = new BlockPos(-25, FLOOR_WEST + 1, 55);
    public static final BlockPos STONE_EAST = new BlockPos(26, FLOOR_EAST + 1, 57);
    public static final BlockPos STONE_SOUTH = new BlockPos(0, FLOOR_SOUTH + 1, 61);
    // 抜け道の光る床 (3 × 3 の中心の床のブロック)。石が鳴ると光り、踏むと広間の扉の内側 (SHORTCUT_*_TO) へ運ぶ
    public static final BlockPos PAD_WEST = new BlockPos(-25, FLOOR_WEST, 51);
    public static final BlockPos PAD_EAST = new BlockPos(26, FLOOR_EAST, 53);
    public static final BlockPos PAD_SOUTH = new BlockPos(0, FLOOR_SOUTH, 57);
    public static final BlockPos SHORTCUT_WEST_TO = new BlockPos(-11, 86, -34);
    public static final BlockPos SHORTCUT_EAST_TO = new BlockPos(11, 86, -34);
    public static final BlockPos SHORTCUT_SOUTH_TO = new BlockPos(0, 86, -23);

    // 碑文 (9.2節。看板の位置と、文が向く向き。看板の後ろか下に台のブロックを B が置く)
    public record Inscription(String id, BlockPos pos, Direction facing) {
    }

    public static final List<Inscription> INSCRIPTIONS = List.of(
            new Inscription("lore_ember_mouth", new BlockPos(3, MOUTH_FLOOR + 1, -146), Direction.NORTH),
            new Inscription("lore_ember_hub", new BlockPos(0, 87, -37), Direction.NORTH),
            new Inscription("lore_ember_west", new BlockPos(-28, FLOOR_WEST + 1, 55), Direction.NORTH),
            new Inscription("lore_ember_east", new BlockPos(23, FLOOR_EAST + 1, 57), Direction.NORTH),
            new Inscription("lore_ember_south", new BlockPos(3, FLOOR_SOUTH + 1, 61), Direction.NORTH),
            new Inscription("lore_ember_heart", new BlockPos(3, FLOOR_HEART + 1, -28), Direction.SOUTH));

    // 試練の入口の一言の看板 (広間の扉の内側の脇。3 枚)
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("entrance_west", new BlockPos(-14, 87, -36), Direction.EAST),
            new Inscription("entrance_east", new BlockPos(14, 87, -36), Direction.WEST),
            new Inscription("entrance_south", new BlockPos(2, 87, -20), Direction.NORTH));

    // ---------------------------------------------------------------- 部屋の一覧
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

    // 中身を担当が建てる部屋 (EmberBackRooms.register の名前。建てる順)
    public static final List<String> TRIAL_ROOMS = List.of("west", "east", "smith", "south", "heart");

    // 箱の中の床の高さ (仮の床と、取っておく場所の床)
    public static int floorOf(String room) {
        return switch (room) {
            case "west" -> FLOOR_WEST;
            case "east", "smith" -> FLOOR_EAST;
            case "south" -> FLOOR_SOUTH;
            case "heart", "heart_ante" -> FLOOR_HEART;
            case "mouth" -> MOUTH_FLOOR;
            default -> ROOMS.get(room).minY();
        };
    }

    // ---------------------------------------------------------------- 休み場 (7.3節。焚き火の前の床のブロック) → B
    // 休み場は 5 × 5 の台 (床は floor の y、焚き火は台の南の端の真ん中)。試練の箱の中の休み場も B が最後に置く
    public record Rest(String name, BlockPos floor, float yaw) {
        // 台の 5 × 5 と、その上 3 段 (取っておく場所)
        public Box area() {
            return new Box(floor.getX() - 2, floor.getY(), floor.getZ() - 2, floor.getX() + 2, floor.getY() + 3, floor.getZ() + 2);
        }

        public BlockPos campfire() {
            return floor.offset(0, 1, 2);
        }
    }

    public static final List<Rest> RESTS = List.of(
            new Rest("throat", new BlockPos(0, THROAT_LANDING, -102), 0f),
            new Rest("hub", new BlockPos(0, 85, -45), 0f),
            new Rest("w0", new BlockPos(-25, FLOOR_WEST, -4), 0f),
            new Rest("w1", new BlockPos(-25, FLOOR_WEST, 24), 0f),
            new Rest("e0", new BlockPos(21, FLOOR_EAST, -4), 0f),
            new Rest("s0", new BlockPos(0, FLOOR_SOUTH, -2), 0f),
            new Rest("heart", new BlockPos(0, FLOOR_HEART, -27), 0f));

    public static Rest rest(String name) {
        for (Rest r : RESTS) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        throw new IllegalArgumentException("unknown rest " + name);
    }

    // ---------------------------------------------------------------- 取っておく場所 (→ B が最後に置く。担当は空けておく)
    // 入口の通り道 (扉から 3 ブロック内側まで)、休み場の台、奥の熾り石・光る床・碑文の台
    public static List<Box> reserved(String room) {
        List<Box> out = new ArrayList<>();
        switch (room) {
            case "west" -> {
                out.add(new Box(-26, FLOOR_WEST, -8, -24, FLOOR_WEST + 4, -7));
                out.add(rest("w0").area());
                // 中ほどの島 (C1 の島の並び 3 通りのどれにも入れる島。B が 5 × 5 の島と焚き火を置く)
                out.add(rest("w1").area());
                // 奥の岸 (熾り石・光る床・碑文。床は FLOOR_WEST。C1 の湖はこの岸の北の縁 z 49 まで)
                out.add(new Box(-29, FLOOR_WEST, 49, -21, FLOOR_WEST + 4, 56));
            }
            case "east" -> {
                out.add(new Box(20, FLOOR_EAST, -8, 22, FLOOR_EAST + 4, -7));
                out.add(rest("e0").area());
                // 鍛え手の扉の前 (扉そのものは C2 が SMITH_DOOR に置く)
                out.add(new Box(25, FLOOR_EAST, 27, 27, FLOOR_EAST + 4, 28));
            }
            case "smith" -> {
                out.add(new Box(25, FLOOR_EAST, 32, 27, FLOOR_EAST + 4, 33));
                // 奥 (東の熾り石・光る床・碑文)
                out.add(new Box(22, FLOOR_EAST, 52, 30, FLOOR_EAST + 4, 58));
            }
            case "south" -> {
                out.add(new Box(-1, FLOOR_SOUTH, -6, 1, FLOOR_SOUTH + 4, -5));
                out.add(rest("s0").area());
                // 谷の奥 (南の熾り石・光る床・碑文。火床は C3 がこれより北に置く)
                out.add(new Box(-4, FLOOR_SOUTH, 56, 4, FLOOR_SOUTH + 4, 62));
                // 背の亀裂の下 (谷の天井に抜ける縦の溝。C3 はここを空けておく)
                out.add(new Box(CRACK.minX(), 100, CRACK.minZ(), CRACK.maxX(), SOUTH.maxY(), CRACK.maxZ()));
            }
            case "heart" -> out.add(new Box(-1, FLOOR_HEART, -21, 1, FLOOR_HEART + 4, -20));
            default -> {
            }
        }
        return out;
    }

    private EmberBackLayout() {
    }
}
