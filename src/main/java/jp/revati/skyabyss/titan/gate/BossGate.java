package jp.revati.skyabyss.titan.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// ボス部屋の入口の大門 (四つの背中で共通の言葉)。「この門の先にボスがいる」とひと目でわかる形にそろえる。
//  - 開口 (封印の扉が入る所) を、背の高い石の枠 (柱・まぐさ・軒) が囲む。開口そのものと扉のブロックには触れない
//  - 枠の上に、心臓の大きなレリーフ (光るブロック) と、両端の光の飾り。まぐさの前の縁は光の帯
//  - 前庭 (任意): 広く高く掘り、床に絨毯の道と光る縁、柱の並木 (灯り付き) を置く
// 巨神ごとに材質 (Palette) だけが違う。座標は「局所座標」: u = 進む向きに向かって右 (開口の中心が 0)、
// v = 進む向きの座標 (扉の面が 0、手前が負)、y = 床のブロックが 0 (人が立つのは 1)。
public final class BossGate {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // 材質。glow = 心臓のレリーフと飾りの灯り (光源)、lamp = 柱の先と床の灯り (光源)
    public record Palette(BlockState base, BlockState pier, BlockState frame, BlockState trim, BlockState panel,
                          BlockState glow, BlockState lamp, BlockState carpet, BlockState edge, BlockState floor, BlockState wall,
                          BlockState crest) {
    }

    // 大門の寸法。base = 扉の面の真ん中の床のブロック、approach = 人が扉へ向かって進む向き。
    // half = 開口の幅の半分 (1 なら 3 幅)、height = 開口の高さ。depth = 扉の手前に張り出す奥行き (2 か 3)。
    // courtLen > 0 なら前庭を掘る (幅は courtHalf の左右、高さは courtHeight)。runLen = 絨毯の道の長さ (前庭がなければ 0 でよい)。
    // bigEmblem = false なら小さい心臓 (5×4)、true なら 7×6
    public static final class Spec {
        final BlockPos base;
        final Direction approach;
        int half = 1;
        int height = 4;
        int depth = 3;
        int courtLen = 0;
        int courtHalf = 5;
        int courtHeight = 14;
        int runLen = -1;
        boolean bigEmblem = true;
        boolean pylons = true;
        int limit = Integer.MAX_VALUE;
        int lowDepth = -1;
        int vaultLen = 0;
        int vaultHalf = 3;

        public Spec(BlockPos base, Direction approach) {
            this.base = base;
            this.approach = approach;
        }

        public Spec half(int v) {
            half = v;
            return this;
        }

        public Spec height(int v) {
            height = v;
            return this;
        }

        public Spec depth(int v) {
            depth = v;
            return this;
        }

        public Spec court(int len, int courtHalf, int courtHeight) {
            this.courtLen = len;
            this.courtHalf = courtHalf;
            this.courtHeight = courtHeight;
            return this;
        }

        public Spec run(int len) {
            runLen = len;
            return this;
        }

        public Spec smallEmblem() {
            bigEmblem = false;
            return this;
        }

        // 天井の高さの上限 (局所の y)。これより上には何も置かない・掘らない (低い部屋の入口用)
        public Spec limit(int maxLocalY) {
            limit = maxLocalY;
            return this;
        }

        // 人の高さ (開口の高さまで) の柱の奥行き。既定は depth。柱の横を歩いて回る所 (大炉の後ろなど) は 1 にして道を残す
        public Spec lowDepth(int v) {
            lowDepth = v;
            return this;
        }

        // 扉の手前の len ブロックの、開口より上 (床からの高さ h+1 以上。天井の高さから) だけを広い高い空間にする。低い通路・前室の天井を上げて、
        // 離れた所からも枠の上の心臓のレリーフが見えるようにする (床・壁の下のほうは変えない)。幅は左右 half
        public Spec vault(int len, int half) {
            vaultLen = len;
            vaultHalf = half;
            return this;
        }

        public Spec noPylons() {
            pylons = false;
            return this;
        }
    }

    private static final String[] HEART_BIG = {
            ".XX.XX.",
            "XXXXXXX",
            "XXXXXXX",
            ".XXXXX.",
            "..XXX..",
            "...X..."};
    private static final String[] HEART_SMALL = {
            ".X.X.",
            "XXXXX",
            ".XXX.",
            "..X.."};

    private final ServerLevel level;
    private final Spec s;
    private final Palette p;
    private final Direction fwd;
    private final Direction right;

    private BossGate(ServerLevel level, Spec spec, Palette palette) {
        this.level = level;
        this.s = spec;
        this.p = palette;
        this.fwd = spec.approach;
        this.right = spec.approach.getClockWise();
    }

    // 大門を建てる。何度呼んでも同じ形になる
    public static void build(ServerLevel level, Spec spec, Palette palette) {
        new BossGate(level, spec, palette).run();
    }

    // 大門の上端の局所の高さ (飾りの先まで)。前庭の高さの目安に使う
    public static int totalHeight(int height, boolean bigEmblem) {
        return height + 4 + (bigEmblem ? 6 : 4) + 2;
    }

    private BlockPos at(int u, int v, int y) {
        return s.base.offset(fwd.getStepX() * v + right.getStepX() * u, y, fwd.getStepZ() * v + right.getStepZ() * u);
    }

    private void put(int u, int v, int y, BlockState st) {
        if (y > s.limit) {
            return;
        }
        level.setBlock(at(u, v, y), st, FLAGS);
    }

    private void air(int u, int v, int y) {
        if (y > s.limit) {
            return;
        }
        if (!level.getBlockState(at(u, v, y)).isAir()) {
            put(u, v, y, Blocks.AIR.defaultBlockState());
        }
    }

    // 殻の壁: 空気でなければ張り替える (空いている向こうの部屋は塞がない)
    private void shell(int u, int v, int y) {
        if (y > s.limit) {
            return;
        }
        if (!level.getBlockState(at(u, v, y)).isAir()) {
            put(u, v, y, p.wall);
        }
    }

    private void run() {
        int dh = s.half;
        int h = s.height;
        int f = s.depth;
        int p0 = dh + 2;
        int p1 = dh + 3;
        String[] heart = s.bigEmblem ? HEART_BIG : HEART_SMALL;
        int er = heart.length;
        int tympBottom = h + 3;
        int cornice = tympBottom + er;
        int top = cornice + 2;

        // ---- 天井を上げる (開口より上だけ)
        if (s.vaultLen > 0) {
            int vt = top + 2;
            for (int v = -s.vaultLen; v <= -1; v++) {
                for (int u = -s.vaultHalf; u <= s.vaultHalf; u++) {
                    for (int y = h + 1; y <= vt - 1; y++) {
                        air(u, v, y);
                    }
                    shell(u, v, vt);
                }
                for (int y = h + 1; y <= vt; y++) {
                    shell(-s.vaultHalf - 1, v, y);
                    shell(s.vaultHalf + 1, v, y);
                }
            }
        }
        // ---- 前庭 (掘る)
        if (s.courtLen > 0) {
            int ch = Math.max(s.courtHeight, top + 1);
            int len = s.courtLen;
            int ch2 = s.courtHalf;
            for (int v = -len; v <= -1; v++) {
                for (int u = -ch2; u <= ch2; u++) {
                    for (int y = 1; y <= ch; y++) {
                        air(u, v, y);
                    }
                    put(u, v, 0, p.floor);
                    shell(u, v, ch + 1);
                }
                for (int y = 0; y <= ch + 1; y++) {
                    shell(-ch2 - 1, v, y);
                    shell(ch2 + 1, v, y);
                }
            }
        }
        // ---- 扉の前の高い空間 (レリーフと軒のぶん。前庭がなくても空ける)
        for (int v = -f; v <= -1; v++) {
            for (int u = -p1; u <= p1; u++) {
                for (int y = 1; y <= top + 1; y++) {
                    air(u, v, y);
                }
            }
        }
        // ---- 床: 絨毯の道と光る縁
        int run = s.runLen >= 0 ? s.runLen : s.courtLen;
        for (int v = -Math.max(run, f); v <= -1; v++) {
            for (int u = -dh; u <= dh; u++) {
                put(u, v, 0, p.carpet);
            }
            boolean light = Math.floorMod(v, 3) == 0;
            put(-dh - 1, v, 0, light ? p.lamp : p.edge);
            put(dh + 1, v, 0, light ? p.lamp : p.edge);
        }
        // ---- 柱の並木 (前庭がある所だけ)
        if (s.courtLen > 0 && s.pylons) {
            for (int v = -4; v >= -s.courtLen; v -= 4) {
                for (int side = -1; side <= 1; side += 2) {
                    int u = side * p0;
                    put(u, v, 1, p.base);
                    for (int y = 2; y <= h; y++) {
                        put(u, v, y, p.pier);
                    }
                    put(u, v, h + 1, p.trim);
                    put(u, v, h + 2, p.lamp);
                }
            }
        }
        // ---- 扉の枠
        for (int side = -1; side <= 1; side += 2) {
            // 袖 (開口のすぐ外の立ち上がり)
            for (int v = -1; v <= 0; v++) {
                for (int y = 1; y <= h; y++) {
                    put(side * (dh + 1), v, y, p.frame);
                }
            }
            // 柱 (二列。足もとは台、前の面は縁取り、前の足もととまぐさの下に灯り)
            int vMin = -(s.lowDepth > 0 ? Math.min(s.lowDepth, f) : f);
            for (int uu = p0; uu <= p1; uu++) {
                int u = side * uu;
                for (int v = vMin; v <= 0; v++) {
                    boolean front = v == vMin;
                    put(u, v, 1, p.base);
                    put(u, v, 2, front ? p.trim : p.base);
                    for (int y = 3; y <= h; y++) {
                        put(u, v, y, front ? p.trim : p.pier);
                    }
                }
                put(u, vMin, 3, uu == p0 ? p.lamp : p.trim);
                put(u, vMin, h, uu == p0 ? p.lamp : p.trim);
            }
        }
        // 開口の上のまぐさ (二段)。前の縁は光の帯 (一つおきの灯り)
        for (int u = -p1; u <= p1; u++) {
            for (int v = -f; v <= 0; v++) {
                put(u, v, h + 1, v == -f && Math.floorMod(u, 2) == 0 ? p.glow : p.frame);
                put(u, v, h + 2, p.trim);
            }
        }
        // 開口の袖の内側のまぐさ (扉の面の上)
        for (int u = -dh; u <= dh; u++) {
            for (int v = -1; v <= 0; v++) {
                put(u, v, h + 1, p.frame);
            }
        }
        // ---- 破風 (心臓のレリーフの背景の壁) と、レリーフ
        for (int u = -p1; u <= p1; u++) {
            for (int y = tympBottom; y < cornice; y++) {
                for (int v = -(f - 2); v <= 0; v++) {
                    put(u, v, y, p.panel);
                }
            }
        }
        int w = heart[0].length();
        for (int row = 0; row < er; row++) {
            for (int col = 0; col < w; col++) {
                if (heart[row].charAt(col) == 'X') {
                    put(col - w / 2, -(f - 1), cornice - 1 - row, p.glow);
                }
            }
        }
        // ---- 軒と冠
        for (int u = -p1 - 1; u <= p1 + 1; u++) {
            for (int v = -f; v <= 0; v++) {
                put(u, v, cornice, p.trim);
            }
        }
        for (int u = -p1; u <= p1; u++) {
            for (int v = -(f - 1); v <= 0; v++) {
                put(u, v, cornice + 1, p.frame);
            }
        }
        // 両端の光の飾り (2×2×2) と、真ん中の冠石
        for (int side = -1; side <= 1; side += 2) {
            for (int uu = p0; uu <= p1; uu++) {
                for (int v = -(f - 1); v <= -(f - 2); v++) {
                    put(side * uu, v, cornice + 1, p.glow);
                    put(side * uu, v, cornice + 2, p.glow);
                }
            }
        }
        put(0, -(f - 1), cornice + 2, p.crest);
        // ---- 外の控え壁 (前庭の壁ぎわで、段になって枠へつながる)
        if (s.courtLen > 0 && s.courtHalf >= p1 + 2) {
            for (int side = -1; side <= 1; side += 2) {
                for (int v = -2; v <= 0; v++) {
                    for (int y = 1; y <= h + 4; y++) {
                        put(side * (p1 + 1), v, y, y > h + 2 ? p.base : p.frame);
                    }
                    for (int y = 1; y <= h + 1; y++) {
                        put(side * (p1 + 2), v, y, p.base);
                    }
                }
                put(side * (p1 + 1), -2, h + 5, p.lamp);
            }
        }
    }
}
