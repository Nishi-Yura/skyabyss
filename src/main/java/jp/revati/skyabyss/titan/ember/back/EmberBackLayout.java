package jp.revati.skyabyss.titan.ember.back;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 余燼の背 (ember_back) の配置図: 部屋の箱と休み場 (docs/design/13_ember.md の 3.3節、26 の 11 章「余燼の拡大」)。
//
// 版 20 で水平 x1.6・垂直 x1.3 の体 (body.bin。tools/gen_ember_back_body.py の既定) に合わせて引き直した。
// 戦いの部屋 (鍛え手の間 半径 13・熾の底 半径 16 とその前室) の大きさは変えない (縮めるかどうかは判断待ち)。位置だけ体に合わせた。
//
// 体の流れ (北 = 頭 -Z から南 = 尾 +Z へ。西 -X が巨神の左):
//   口 (舌の上。到着)  →  喉 (幅 7 の首 → 玄武の鱗の尾根の広間 → 幅 7 の坂)  →  炉心の広間 (火口の真下。半径 27)
//   広間から三つの試練へ (冷えた溶岩洞で降りる): 西 灰の道 (灰降りの庭 → 試しの流れ → 第一の湖 → 中ノ岸 → 第二の湖 → 奥の岸)
//                                                東 鍛えの間 (鍛えの回廊 → 前室 → 鞴・金床・大炉 → 鍛え手の扉 → 鍛え手の間)
//                                                南 火守りの谷 (灰降りの庭 → 灰運びの道と噴気 → 火床 → 奥の岸)
//   三つの石がそろうと広間の中央の炉が開き、螺旋で下の熾の底へ。広間の南東の壁の螺旋 → 床 119 の踊り場 (試練の階へ)。
//
// 座標: y は「床のブロックの y」(人が立つのは +1)。灰の原の地表 (いちばん上のブロック) は y 63 (PLAIN_TOP)。体の中心の左右は x 0。
// 箱の中の座標は、できるだけ箱の角 (min) や中心からの相対で書くこと。
public final class EmberBackLayout {
    // 配置の版。上がったら体を建て直す (TitanBackData.markBuilt)。13〜: 版 12 までの履歴は docs/design/13 の 3.3節。
    // 30: 任意の翼 (見張り台の張り出し・亀裂・火の井戸の縦穴・背へ出る洞・火口の宝箱)・抜け道の床を外す。試練の階への踊り場 (床 119) と壁の螺旋は残す
    // 24: 試練の階の南の広間 (EmberDeck。docs/design/29 の 3.4 の P3): 北西の鎖橋の間 E03・北東の鋳型の間 E08・南の廊下 SP と三つの南の広間 (荷車 E11・巣 E12・熾の道 E04)。回廊は南へ延びた
    // 23: 試練の階 (EmberDeck。docs/design/29): 螺旋の踊り場から南の浅い所に、新作の試練 E02・E06・E07・E10 の四つのセルと広間・回廊・休み場 6 つ・試練の板。冷えた溶岩洞の口の戸 (原型の三つ)
    // 20: 体を x1.6・y1.3 に (26 の 11 章)。喉・広間・三つの試練・井戸を引き直し、冷えた溶岩洞・灰降りの庭・尾根の広間・任意の翼を足した
    public static final int LAYOUT_VERSION = 30;
    // 灰の原のいちばん上のブロックの y (dimension/ember_back.json の層と合わせる)
    public static final int PLAIN_TOP = 63;
    // 体の中心 (遠くへ歩いたかを見る。体の長さ 621 の半分に余白)
    public static final int BODY_CX = 0;
    public static final int BODY_CZ = 37;
    public static final double FAR = 380;
    // 体の外形の箱 (body.bin の表の範囲: x -148〜147、y 64〜217、z -273〜348。熾の底は灰の原の下 y 46 から)
    public static final Box BODY = new Box(-152, 44, -280, 152, 230, 354);

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
    public static final int MOUTH_FLOOR = 69;
    public static final BlockPos ARRIVAL = new BlockPos(0, MOUTH_FLOOR, -252);
    public static final float ARRIVAL_YAW = 0f;
    // 見送りの石 (3 × 2。下の顎の先の外、灰の原の上の空気の箱)。ここに 0.5 秒いると地上へ (2.5節)。口の床からは 6 段の段で下りる
    public static final Box RETURN_STONE = new Box(-1, PLAIN_TOP + 1, -278, 1, PLAIN_TOP + 3, -277);
    // 口の中 (到着・見送りの段。ピースフルでも歩ける所)
    public static final Box MOUTH = new Box(-11, PLAIN_TOP + 1, -279, 11, 80, -238);
    // 喉 (口の奥から広間の北の扉まで。幅 7 の首 z -237〜-209 → 尾根の広間 z -208〜-132 (半幅 12) → 幅 7 の坂 z -131〜-83)
    public static final Box THROAT = new Box(-12, 69, -237, 12, 98, -83);
    // 喉の坂の休み場の平らな所 (床 78。尾根の広間と坂のあいだ)
    public static final int THROAT_LANDING = 78;

    // ---------------------------------------------------------------- 広間 (炉心の広間。円 半径 27、天井まで 24。火口の真下)
    public static final int FLOOR_HUB = 92;
    public static final BlockPos HUB_CENTER = new BlockPos(0, FLOOR_HUB, -54);
    public static final int HUB_RADIUS = 27;
    public static final Box HUB = new Box(-28, FLOOR_HUB, -82, 28, 116, -26);

    // ---------------------------------------------------------------- 三つの試練と部屋の箱
    // 灰の道 (幅 39・奥行き 127、天井まで 26)。灰降りの庭 z -14〜14 → 試しの流れ z 15〜21 → 試しの岸 z 22〜31 → 第一の湖 z 32〜62
    //   → 中ノ岸 (休み場 w1) z 63〜76 → 第二の湖 z 77〜104 → 奥の岸 z 105〜112 → C1
    public static final int FLOOR_WEST = 80;
    public static final Box WEST = new Box(-62, 77, -14, -24, 106, 112);
    // 鍛えの間 (幅 41・奥行き 71、天井まで 22)。鍛えの回廊 z -14〜19 → 前室 z 20〜33 → 柱の列 → 金床・槽 → 鞴・大炉 (z 20〜56 が版 12 の部屋の拡大前の形を移した所) → C2
    public static final int FLOOR_EAST = 86;
    public static final Box EAST = new Box(24, 86, -14, 64, 108, 56);
    // 鍛え手の間 (円 半径 13、天井 14。戦いの部屋は大きさ据え置き) → E
    public static final BlockPos SMITH_CENTER = new BlockPos(44, 86, 73);
    public static final int SMITH_RADIUS = 13;
    public static final Box SMITH = new Box(31, 86, 60, 57, 100, 86);
    // 火守りの谷 (幅 35・奥行き 75、天井まで 29)。天井は閉じた洞 → C3
    public static final int FLOOR_SOUTH = 83;
    public static final Box SOUTH = new Box(-17, 83, -8, 17, 112, 66);
    // 熾の底の前室 (9 × 7。螺旋の階段の下。大きさ据え置き) → B (碑文 lore_ember_heart と休み場 heart)
    public static final int FLOOR_HEART = 50;
    public static final Box HEART_ANTE = new Box(-4, FLOOR_HEART, -29, 4, 56, -23);
    // 熾の底 (円 半径 16、天井まで 14。床の輪: 台 0〜4、区画 4〜15、溝 15〜16。区画の下に深さ 3 の溶岩の溜め。戦いの部屋は大きさ据え置き) → F
    public static final BlockPos HEART_CENTER = new BlockPos(0, FLOOR_HEART, -4);
    public static final int HEART_RADIUS = 16;
    public static final Box HEART = new Box(-17, 46, -21, 17, 64, 13);

    // ---------------------------------------------------------------- 通り道 (B が壁を抜く。中身の担当は、箱の中の続きを空けておく)
    // 試練の入口 (広間からの冷えた溶岩洞が箱に入る所。箱の外の 1 枚の壁。幅 7 × 高さ 6)
    public static final Box WEST_DOOR = new Box(-46, FLOOR_WEST + 1, -15, -40, FLOOR_WEST + 6, -15);
    public static final Box EAST_DOOR = new Box(41, FLOOR_EAST + 1, -15, 47, FLOOR_EAST + 6, -15);
    public static final Box SOUTH_DOOR = new Box(-3, FLOOR_SOUTH + 1, -9, 3, FLOOR_SOUTH + 6, -9);
    // 鍛えの間の奥 → 鍛え手の間 (EAST と SMITH のあいだの 3 枚の壁)。扉 (SealedGateBlock) を置くのは C2、戦いの間に閉じるのは E (BossRoom)
    public static final Box SMITH_DOOR = new Box(43, FLOOR_EAST + 1, 57, 45, FLOOR_EAST + 4, 59);
    // 前室 → 熾の底 (1 枚の壁)。封印を置いて開け閉めするのは F (BossRoom)
    public static final Box HEART_DOOR = new Box(-1, FLOOR_HEART + 1, -22, 1, FLOOR_HEART + 4, -22);
    // 広間の扉 (円の縁の外の 1 枚の壁の真ん中のブロック。ここから通路・洞)
    public static final BlockPos HUB_DOOR_NORTH = new BlockPos(0, FLOOR_HUB + 1, -82);
    public static final BlockPos HUB_DOOR_WEST = new BlockPos(-28, FLOOR_HUB + 1, -54);
    public static final BlockPos HUB_DOOR_EAST = new BlockPos(28, FLOOR_HUB + 1, -54);
    public static final BlockPos HUB_DOOR_SOUTH = new BlockPos(0, FLOOR_HUB + 1, -26);
    // 冷えた溶岩洞 (広間の西・東の扉から横へ、そこから試練の入口へ下る南の脚。南は広間の南の扉から真っすぐ)。幅 7
    public static final int TUBE_WEST_X = -43;
    public static final int TUBE_EAST_X = 44;
    public static final int TUBE_HALF = 3;
    public static final int TUBE_Z = -54;

    // ---------------------------------------------------------------- 熾の底の門・火の井戸の踊り場 (→ B)
    // 広間の中央の大きな炉の底の格子 (3 × 3、広間の床の高さ)。三つの石がそろうと開き、下の螺旋の階段へ
    public static final Box HEART_GATE = new Box(-1, FLOOR_HUB, -55, 1, FLOOR_HUB, -53);
    // 門の下の螺旋の縦穴 (格子の真下の 3 × 3。中央の柱のまわりを 1 段ずつ下りる)。前室の床 (50) まで。底から南へ通路で前室へ
    public static final Box GATE_SHAFT = new Box(-1, FLOOR_HEART + 1, -55, 1, FLOOR_HUB - 1, -53);
    // 火の井戸の踊り場: 壁の螺旋 WELL_STAIR で床 92 から上り、床 119 が試練の階 (EmberDeck) への踊り場。井戸の縦穴・火口・背へ出る洞は無い
    public static final int WELL_BOTTOM = 119;
    public static final BlockPos WELL_STAIR = new BlockPos(14, FLOOR_HUB, -31);

    // ---------------------------------------------------------------- 熾り石・碑文 (→ B が置く。担当の箱の中の reserved)
    // 熾り石 (ブロックの位置。床 +1)。西は奥の岸、東は鍛え手の間の奥、南は谷の奥 (火床の向こう)
    public static final BlockPos STONE_WEST = new BlockPos(TUBE_WEST_X, FLOOR_WEST + 1, 111);
    public static final BlockPos STONE_EAST = new BlockPos(TUBE_EAST_X, FLOOR_EAST + 1, 85);
    public static final BlockPos STONE_SOUTH = new BlockPos(0, FLOOR_SOUTH + 1, 64);
    // 試練の部屋から広間の扉の内側へ戻すときの着き場 (EmberRooms の eject。抜け道の床は無い)
    public static final BlockPos EJECT_WEST = new BlockPos(-23, FLOOR_HUB + 1, -54);
    public static final BlockPos EJECT_EAST = new BlockPos(23, FLOOR_HUB + 1, -54);
    public static final BlockPos EJECT_SOUTH = new BlockPos(0, FLOOR_HUB + 1, -31);

    // 碑文 (9.2節。看板の位置と、文が向く向き。看板の後ろか下に台のブロックを B が置く)
    public record Inscription(String id, BlockPos pos, Direction facing) {
    }

    public static final List<Inscription> INSCRIPTIONS = List.of(
            new Inscription("lore_ember_mouth", new BlockPos(5, MOUTH_FLOOR + 1, -246), Direction.NORTH),
            new Inscription("lore_ember_hub", new BlockPos(0, FLOOR_HUB + 2, -58), Direction.NORTH),
            new Inscription("lore_ember_west", new BlockPos(TUBE_WEST_X - 3, FLOOR_WEST + 1, 111), Direction.NORTH),
            new Inscription("lore_ember_east", new BlockPos(TUBE_EAST_X - 3, FLOOR_EAST + 1, 85), Direction.NORTH),
            new Inscription("lore_ember_south", new BlockPos(3, FLOOR_SOUTH + 1, 65), Direction.NORTH),
            new Inscription("lore_ember_heart", new BlockPos(3, FLOOR_HEART + 1, -28), Direction.SOUTH));

    // 試練の入口の一言の看板 (広間の扉の内側の脇。3 枚)。扉の色の道 (西 = 灰・青の灯、東 = 橙の灯、南 = 赤の灯) の脇に立てる
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("entrance_west", new BlockPos(-26, FLOOR_HUB + 2, -58), Direction.EAST),
            new Inscription("entrance_east", new BlockPos(26, FLOOR_HUB + 2, -58), Direction.WEST),
            new Inscription("entrance_south", new BlockPos(4, FLOOR_HUB + 2, -28), Direction.NORTH));

    // 部屋の中の掲示 (規則を一つずつ教える碑。lang の sign.skyabyss.ember_board_<id>。何度建てても同じ)
    public static final List<Inscription> BOARDS = List.of(
            // 喉・広間 (向きは、そこへ歩いてくる人に文が向く向き)
            new Inscription("throat_neck", new BlockPos(-3, 71, -229), Direction.NORTH),
            new Inscription("hub_stones", new BlockPos(-4, FLOOR_HUB + 2, -81), Direction.SOUTH),
            // 西 灰の道
            new Inscription("west_rule", new BlockPos(-47, FLOOR_WEST + 2, 11), Direction.NORTH),
            new Inscription("west_lake1", new BlockPos(-47, FLOOR_WEST + 2, 29), Direction.NORTH),
            new Inscription("west_mid", new BlockPos(-47, FLOOR_WEST + 2, 66), Direction.NORTH),
            new Inscription("west_tomb", new BlockPos(-60, FLOOR_WEST + 2, -6), Direction.EAST),
            // 東 鍛えの間
            new Inscription("east_steps", new BlockPos(40, FLOOR_EAST + 2, -9), Direction.NORTH),
            new Inscription("east_colors", new BlockPos(48, FLOOR_EAST + 2, -9), Direction.NORTH),
            new Inscription("east_bellows", new BlockPos(44, FLOOR_EAST + 2, 44), Direction.NORTH),
            new Inscription("east_anvil", new BlockPos(47, FLOOR_EAST + 2, 37), Direction.NORTH),
            new Inscription("east_trough", new BlockPos(52, FLOOR_EAST + 2, 39), Direction.NORTH),
            new Inscription("east_door", new BlockPos(44, FLOOR_EAST + 4, 55), Direction.SOUTH),
            // 南 火守り
            new Inscription("south_rule", new BlockPos(-6, FLOOR_SOUTH + 2, 6), Direction.NORTH),
            new Inscription("south_lane", new BlockPos(7, FLOOR_SOUTH + 2, 8), Direction.NORTH),
            new Inscription("south_hearth", new BlockPos(6, FLOOR_SOUTH + 2, 42), Direction.NORTH));

    // 任意の翼の宝箱 (右クリックで一度だけ小さな贈り物。EmberCaches)。id と置く位置 (台の上の宝箱)。碑は ember_cache_<id> を横に
    public record Cache(String id, BlockPos pos, Direction facing) {
    }

    public static final List<Cache> CACHES = List.of(
            new Cache("tomb", new BlockPos(-61, FLOOR_WEST + 1, -3), Direction.EAST),
            new Cache("workshop", new BlockPos(63, FLOOR_EAST + 1, 6), Direction.WEST),
            new Cache("garden", new BlockPos(-16, FLOOR_SOUTH + 1, 3), Direction.EAST));

    // ---------------------------------------------------------------- 部屋の一覧
    // 部屋の名前 → 箱 (コマンドの go・自己テストの重なりの検査・リセットで中の人を広間へ移す範囲)
    public static final Map<String, Box> ROOMS = rooms();

    private static Map<String, Box> rooms() {
        Map<String, Box> m = new LinkedHashMap<>();
        m.put("mouth", MOUTH);
        m.put("throat", THROAT);
        m.put("hub", HUB);
        m.put("west", WEST);
        m.put("east", EAST);
        m.put("smith", SMITH);
        m.put("south", SOUTH);
        m.put("heart_ante", HEART_ANTE);
        m.put("heart", HEART);
        for (String name : EmberDeck.NAMES) {
            m.put(name, EmberDeck.box(name));
        }
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
            case "hub" -> FLOOR_HUB;
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

    public static final List<Rest> RESTS = java.util.stream.Stream.concat(List.of(
            new Rest("throat", new BlockPos(0, THROAT_LANDING, -125), 0f),
            new Rest("hub", new BlockPos(0, FLOOR_HUB, -72), 0f),
            new Rest("w0", new BlockPos(TUBE_WEST_X, FLOOR_WEST, -6), 0f),
            new Rest("w1", new BlockPos(TUBE_WEST_X, FLOOR_WEST, 70), 0f),
            new Rest("e0", new BlockPos(TUBE_EAST_X - 5, FLOOR_EAST, 24), 0f),
            new Rest("s0", new BlockPos(0, FLOOR_SOUTH, 0), 0f),
            new Rest("heart", new BlockPos(0, FLOOR_HEART, -27), 0f)).stream(), EmberDeck.RESTS.stream()).toList();

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
                out.add(new Box(-46, FLOOR_WEST, -14, -40, FLOOR_WEST + 6, -12));
                out.add(rest("w0").area());
                // 中ノ岸の休み場
                out.add(rest("w1").area());
                // 奥の岸 (熾り石・光る床・碑文。床は FLOOR_WEST。第二の湖はこの岸の北の縁 z 104 まで)
                out.add(new Box(-48, FLOOR_WEST, 105, -38, FLOOR_WEST + 6, 111));
            }
            case "east" -> {
                out.add(new Box(41, FLOOR_EAST, -14, 47, FLOOR_EAST + 6, -12));
                out.add(rest("e0").area());
                // 鍛え手の扉の前 (扉そのものは C2 が SMITH_DOOR に置く)
                out.add(new Box(43, FLOOR_EAST, 55, 45, FLOOR_EAST + 4, 56));
            }
            case "smith" -> {
                out.add(new Box(43, FLOOR_EAST, 60, 45, FLOOR_EAST + 4, 61));
                // 奥 (東の熾り石・光る床・碑文)
                out.add(new Box(39, FLOOR_EAST, 80, 49, FLOOR_EAST + 4, 86));
            }
            case "south" -> {
                out.add(new Box(-3, FLOOR_SOUTH, -8, 3, FLOOR_SOUTH + 6, -6));
                out.add(rest("s0").area());
                // 谷の奥 (南の熾り石・光る床・碑文。火床は C3 がこれより北に置く)
                out.add(new Box(-5, FLOOR_SOUTH, 56, 5, FLOOR_SOUTH + 5, 65));
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
