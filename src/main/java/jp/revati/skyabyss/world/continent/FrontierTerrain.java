package jp.revati.skyabyss.world.continent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 外周の見せ場の地形 (2026-11-06 の頁「大陸の外れ」の m_terrain = a。issue #19 の ①)。外れの帯 (FrontierBand) の中だけ、土地ごとに:
//   岩鯨 = 骨の谷: 巨神の肋骨の弧 (骨のブロック) が地面から突き出て並ぶ。地面は苔の岩場のまだら
//   潮殻 = 干潟と岩礁: 海べり (海の高さ -2〜+7) をならして干潟 (泥と砂、ところどころ潮だまり) にし、岩礁の柱 (凝灰岩・海晶) を立てる
//   雷翼 = 氷河と浮き岩: 高い所の平らな面に氷河の舌 (固めた氷・青氷の筋・雪)。浮き岩 (雪をかぶった石の塊) が宙に浮く
//   余燼 = 冷えた熔岩の川と灰の砂丘: うねる筋に沿って玄武岩と黒石の流れ (ところどころマグマ)、その外は灰の砂丘 (起伏 + 灰色の砂)
// 高さを変えるもの (干潟・砂丘・熔岩の川の窪み) は ContinentMap.rawSurface から呼ぶ (道のならし・置き場の高さと食い違わないように)。
// 置く物 (肋骨・浮き岩・岩礁) は ContinentChunkGenerator が柱ごとに足す。
// 町からの距離で FAR から FADE かけて現れ、外れの集落・奥のダンジョン (SPOT_CLEAR)・見どころ (POI_CLEAR) のそばと道のそば (置く物だけ) には出ない。
// 変えたら upgrade/ContinentRegen の VERSION を上げる
public final class FrontierTerrain {
    static final double FADE = 110;
    static final double SPOT_CLEAR = 75;
    static final double SPOT_FADE = 45;
    static final double SITE_CLEAR = 120;
    static final double LAIR_CLEAR = 140;
    static final double POI_CLEAR = 34;
    static final double POI_FADE = 26;
    // 置く物の升 (一升に多くて一つ)
    static final int CELL = 72;
    static final double FEATURE_ROAD_CLEAR = 34;

    private FrontierTerrain() {
    }

    private static List<ContinentPois.Poi> pois;

    static List<ContinentPois.Poi> pois() {
        if (pois == null) {
            pois = ContinentPois.all();
        }
        return pois;
    }

    static double smooth01(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    // 0〜1: 外れの見せ場の強さ (巨神の土地でなければ 0)
    public static double weight(int region, int x, int z) {
        if (!FrontierBand.titanLand(region)) {
            return 0;
        }
        double w = smooth01((FrontierBand.townDistance(x, z) - FrontierBand.FAR) / FADE);
        if (w <= 0) {
            return 0;
        }
        for (ContinentLayout.Frontier f : ContinentLayout.get().frontier()) {
            double d = Math.hypot(f.pos().getX() - x, f.pos().getZ() - z);
            if (d < SPOT_CLEAR + SPOT_FADE) {
                w *= smooth01((d - SPOT_CLEAR) / SPOT_FADE);
            }
        }
        // 巨神の跡地 (章の場所。余燼の跡地は外れの帯にかかる) のまわりも変えない
        for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
            BlockPos site = ContinentLayout.get().site(t);
            if (site != null) {
                double d = Math.hypot(site.getX() - x, site.getZ() - z);
                if (d < SITE_CLEAR + SPOT_FADE) {
                    w *= smooth01((d - SITE_CLEAR) / SPOT_FADE);
                }
            }
        }
        if (w <= 0) {
            return 0;
        }
        for (ContinentPois.Poi p : pois()) {
            double dx = p.x() - x;
            double dz = p.z() - z;
            if (Math.abs(dx) < POI_CLEAR + POI_FADE && Math.abs(dz) < POI_CLEAR + POI_FADE) {
                w *= smooth01((Math.hypot(dx, dz) - POI_CLEAR) / POI_FADE);
            }
        }
        return w;
    }

    // ---------------------------------------------------------------- 高さ (ContinentMap.rawSurface から)
    public static double height(int region, int x, int z, double h) {
        if (!FrontierBand.titanLand(region)) {
            return h;
        }
        double w = weight(region, x, z);
        if (w <= 0) {
            return h;
        }
        double sea = ContinentChunkGenerator.SEA_LEVEL;
        switch (region) {
            case ContinentMap.TIDE -> {
                // 干潟: 海べりを海の高さのすぐ下〜上にならす。潮だまりは低い所
                if (h > sea - 3 && h < sea + 8) {
                    double flat = sea - 0.4 + 1.3 * ContinentMap.value(x / 11.0, z / 11.0, 0x7D21);
                    double k = w * smooth01(1 - Math.abs(h - (sea + 2)) / 6.0);
                    return h + (flat - h) * k;
                }
            }
            case ContinentMap.EMBER -> {
                if (h > sea + 3) {
                    double river = lavaRiver(x, z);
                    if (river > 0) {
                        return h - 2.2 * river * w;
                    }
                    // 灰の砂丘: 風下へ傾いた畝 (なだらか)
                    double d = dune(x, z);
                    return h + 2.0 * d * w;
                }
            }
            default -> {
            }
        }
        return h;
    }

    // 熔岩の川: うねる筋の中心で 1、幅の端で 0
    static double lavaRiver(int x, int z) {
        double n = ContinentMap.value(x / 95.0, z / 95.0, 0x41A7) + 0.35 * ContinentMap.value(x / 31.0, z / 31.0, 0x2E9);
        double a = Math.abs(n);
        return a < 0.045 ? 1 - a / 0.045 : 0;
    }

    // 砂丘の畝 (0〜1)
    static double dune(int x, int z) {
        double u = (x * 0.8 + z * 0.6) / 13.0 + 1.6 * ContinentMap.value(x / 40.0, z / 40.0, 0x5D3);
        double s = 0.5 + 0.5 * Math.sin(u);
        return s * s * (0.6 + 0.4 * ContinentMap.value(x / 60.0, z / 60.0, 0x6E1));
    }

    // ---------------------------------------------------------------- 地面の上の数段 (ContinentChunkGenerator.solid の前に)。変えないなら null
    public static BlockState ground(int region, double w, int x, int y, int z, int top, int depth, int slope) {
        if (w <= 0.05 || depth > 3) {
            return null;
        }
        double h = ContinentMap.hash(x, y, z ^ 0x6B1);
        double sea = ContinentChunkGenerator.SEA_LEVEL;
        // 現れ始めの縁はまだらに混ぜる
        if (h * 0.5 + 0.5 > w * 1.4) {
            return null;
        }
        switch (region) {
            case ContinentMap.WHALE -> {
                // 苔の岩場: 苔と苔の丸石のまだら、坂は凝灰岩
                if (depth == 0) {
                    double p = ContinentMap.value(x / 9.0, z / 9.0, 0x1B3);
                    if (slope >= 2) {
                        return h > 0.2 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    }
                    if (p > 0.35) {
                        return Blocks.MOSS_BLOCK.defaultBlockState();
                    }
                    if (p < -0.55) {
                        return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    }
                }
            }
            case ContinentMap.TIDE -> {
                if (top < sea + 3 && top >= sea - 3) {
                    if (depth == 0) {
                        double p = ContinentMap.value(x / 7.0, z / 7.0, 0x2A9);
                        return p > 0.25 ? Blocks.MUD.defaultBlockState() : p < -0.5 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    }
                    return Blocks.PACKED_MUD.defaultBlockState();
                }
            }
            case ContinentMap.STORM -> {
                // 氷河の舌: 高い所の緩い面。青氷の筋
                if (top > sea + 45 && slope <= 2) {
                    double tongue = ContinentMap.value(x / 48.0, z / 48.0, 0x3F7);
                    if (tongue > 0.15) {
                        if (depth == 0) {
                            double streak = Math.abs(ContinentMap.value(x / 21.0, z / 21.0, 0x4C1));
                            return streak < 0.06 ? Blocks.BLUE_ICE.defaultBlockState() : h > 0.6 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                        }
                        return depth < 3 ? Blocks.PACKED_ICE.defaultBlockState() : null;
                    }
                }
            }
            case ContinentMap.EMBER -> {
                if (top > sea + 2) {
                    double river = lavaRiver(x, z);
                    if (river > 0) {
                        if (depth == 0 && river > 0.75 && h > 0.55) {
                            return Blocks.MAGMA_BLOCK.defaultBlockState();
                        }
                        return h > -0.2 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    }
                    // 灰の砂丘: 畝の上と、そのまわりのまだらだけ灰色の砂 (荒野の赤い縞は残す)
                    double crest = dune(x, z) + 0.25 * ContinentMap.value(x / 6.0, z / 6.0, 0x2D1);
                    if (slope <= 1 && crest > 0.55) {
                        if (depth == 0) {
                            return h > -0.9 ? Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        }
                        return depth == 1 ? Blocks.GRAVEL.defaultBlockState() : null;
                    }
                }
            }
            default -> {
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 置く物 (肋骨・浮き岩・岩礁)
    // kind: 0 肋骨の並び、1 浮き岩、2 岩礁の柱の群れ。cx, cz = 中心、base = 中心の地面、angle = 背骨の向き、size = 大きさの揺らぎ
    record Feature(int kind, int cx, int cz, int base, double angle, double size, long seed) {
    }

    private static final Map<Long, Feature> CELLS = new HashMap<>();
    private static final Feature NONE = new Feature(-1, 0, 0, 0, 0, 0, 0);

    static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    static Feature cell(ContinentMap map, int i, int j) {
        long k = key(i, j);
        synchronized (CELLS) {
            Feature f = CELLS.get(k);
            if (f != null) {
                return f;
            }
        }
        Feature f = makeCell(map, i, j);
        synchronized (CELLS) {
            CELLS.put(k, f);
        }
        return f;
    }

    static Feature makeCell(ContinentMap map, int i, int j) {
        // 升の真ん中から ±CELL/5 ずらした所。升の半分を越えない大きさにする (隣の升に掛からない)
        int cx = i * CELL + CELL / 2 + (int) Math.round(ContinentMap.hash(i, j, 0x51D) * CELL / 5.0);
        int cz = j * CELL + CELL / 2 + (int) Math.round(ContinentMap.hash(j, i, 0x2B7) * CELL / 5.0);
        if (!map.inside(cx, cz)) {
            return NONE;
        }
        int region = map.region(cx, cz);
        if (!FrontierBand.titanLand(region) || ContinentMap.hash(i, j, 0x7E3) < (region == ContinentMap.TIDE ? -0.2 : 0.1)) {
            return NONE;
        }
        if (weight(region, cx, cz) < 0.95 || FrontierCaches.roadNear(ContinentLayout.get(), cx, cz, FEATURE_ROAD_CLEAR)) {
            return NONE;
        }
        // 巨神の居場所 (体と祠の広場) から離す
        for (ContinentLairs.Lair l : ContinentLairs.all().values()) {
            if (Math.hypot(l.x() - cx, l.z() - cz) < LAIR_CLEAR) {
                return NONE;
            }
        }
        int base = map.surface(cx, cz);
        int sea = ContinentChunkGenerator.SEA_LEVEL;
        int kind;
        switch (region) {
            case ContinentMap.WHALE -> kind = 0;
            case ContinentMap.STORM -> kind = 1;
            case ContinentMap.TIDE -> kind = 2;
            default -> {
                return NONE;
            }
        }
        if (kind == 2) {
            // 岩礁は浅い水の中だけ (まわりも水)
            if (base > sea - 1 || base < sea - 6) {
                return NONE;
            }
            for (int[] d : new int[][] {{10, 0}, {-10, 0}, {0, 10}, {0, -10}}) {
                if (map.surface(cx + d[0], cz + d[1]) > sea - 1) {
                    return NONE;
                }
            }
        }
        if (kind == 1) {
            // 浮き岩は、まわり 16 の地面が岩の底より十分下にある所だけ (崖や峰にくっつけない)
            int highest = base;
            for (int a = 0; a < 12; a++) {
                double ang = a * Math.PI / 6;
                for (int rr : new int[] {8, 16, 26}) {
                    highest = Math.max(highest, map.surface(cx + (int) Math.round(Math.cos(ang) * rr), cz + (int) Math.round(Math.sin(ang) * rr)));
                }
            }
            if (highest > base + 4) {
                return NONE;
            }
        }
        if (kind != 2 && base <= sea + 1) {
            return NONE;
        }
        // 肋骨は平らな所だけ (斜面に弧を立てると足が宙に浮く)
        if (kind == 0) {
            for (int[] d : new int[][] {{12, 0}, {-12, 0}, {0, 12}, {0, -12}}) {
                if (Math.abs(map.surface(cx + d[0], cz + d[1]) - base) > 4) {
                    return NONE;
                }
            }
        }
        double angle = (ContinentMap.hash(i, j, 0x19F) + 1) * Math.PI;
        double size = 0.8 + 0.4 * (ContinentMap.hash(i, j, 0x6C3) * 0.5 + 0.5);
        return new Feature(kind, cx, cz, base, angle, size, ((long) i * 31 + j) ^ 0x5DEECE66DL);
    }

    // 柱 (x, z) に置く物: {下の y, 上の y, 種類の色} の並び (無ければ空)。地面 top より上だけ
    public static void column(ContinentMap map, int x, int z, int top, ColumnSink sink) {
        int i = Math.floorDiv(x, CELL);
        int j = Math.floorDiv(z, CELL);
        Feature f = cell(map, i, j);
        if (f.kind() < 0) {
            return;
        }
        switch (f.kind()) {
            case 0 -> ribs(f, x, z, top, sink);
            case 1 -> floatingRock(f, x, z, sink);
            case 2 -> reef(f, x, z, top, sink);
            default -> {
            }
        }
    }

    public interface ColumnSink {
        void put(int y0, int y1, BlockState state);
    }

    // 肋骨: 背骨の向きに 5 本の弧 (幅 W・高さ H の半円)。ところどころ欠ける・片側だけ
    static void ribs(Feature f, int x, int z, int top, ColumnSink sink) {
        double dx = x + 0.5 - f.cx();
        double dz = z + 0.5 - f.cz();
        double ax = Math.cos(f.angle());
        double az = Math.sin(f.angle());
        double along = dx * ax + dz * az;
        double across = -dx * az + dz * ax;
        double w = 15 * f.size();
        double hgt = 14 * f.size();
        int n = 5;
        double step = 5.5;
        for (int r = 0; r < n; r++) {
            double t = (r - (n - 1) / 2.0) * step;
            if (Math.abs(along - t) > 0.75) {
                continue;
            }
            double hr = ContinentMap.hash(r, (int) f.seed(), 0x2F1);
            // 前と後ろの肋骨は短い (背の端)。一本は片側だけ
            double scale = 1 - 0.25 * Math.abs(r - (n - 1) / 2.0) / ((n - 1) / 2.0);
            double half = w / 2 * scale;
            if (Math.abs(across) > half + 0.6) {
                continue;
            }
            if (hr < -0.55 && across < 0) {
                continue;
            }
            double hh = hgt * scale;
            double a0 = Math.max(-half, across - 0.5);
            double a1 = Math.min(half, across + 0.5);
            double y0 = hh * Math.sqrt(Math.max(0, 1 - (a0 / half) * (a0 / half)));
            double y1 = hh * Math.sqrt(Math.max(0, 1 - (a1 / half) * (a1 / half)));
            double lo = Math.min(y0, y1) - 1.0;
            double hi = Math.max(y0, y1);
            int base = f.base();
            // 足は地面に埋める
            int from = (int) Math.floor(base + lo);
            if (Math.abs(across) > half - 1.5) {
                from = Math.min(from, top - 2);
            }
            sink.put(Math.max(from, top - 2), (int) Math.floor(base + hi), Blocks.BONE_BLOCK.defaultBlockState());
        }
        // 背骨の名残: 中心線の低い背 (地面から 1 段、ところどころ)
        if (Math.abs(across) < 0.8 && Math.abs(along) < (n - 1) / 2.0 * step + 2 && ContinentMap.hash((int) Math.floor(along), (int) f.seed(), 0x3A9) > -0.3) {
            sink.put(top + 1, top + 1, Blocks.BONE_BLOCK.defaultBlockState());
        }
    }

    // 浮き岩: 地面から 18〜30 の宙の、上が平らで下のとがった石の塊。上は雪
    static void floatingRock(Feature f, int x, int z, ColumnSink sink) {
        double rx = 6.5 * f.size();
        double rz = 5.0 * f.size();
        double dx = x + 0.5 - f.cx();
        double dz = z + 0.5 - f.cz();
        double ca = Math.cos(f.angle());
        double sa = Math.sin(f.angle());
        double u = (dx * ca + dz * sa) / rx;
        double v = (-dx * sa + dz * ca) / rz;
        double q = u * u + v * v + 0.18 * ContinentMap.value(x / 3.0, z / 3.0, 0x77);
        if (q >= 1) {
            return;
        }
        double cy = f.base() + 20 + 8 * (ContinentMap.hash((int) f.seed(), 1, 0x11) * 0.5 + 0.5);
        double s = Math.sqrt(1 - q);
        int topY = (int) Math.floor(cy + 1.5 * s);
        int bottom = (int) Math.floor(cy - 7.5 * s * s);
        sink.put(bottom, topY - 1, ContinentMap.hash(x, z, 0x5A) > 0.4 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        sink.put(topY, topY, q < 0.8 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState());
    }

    // 岩礁: 中心のまわりに 4〜6 本の柱 (半径 1.5〜2.5、海の高さから 2〜5 上)
    static void reef(Feature f, int x, int z, int top, ColumnSink sink) {
        int sea = ContinentChunkGenerator.SEA_LEVEL;
        int n = 4 + (int) ((ContinentMap.hash((int) f.seed(), 2, 0x3D) * 0.5 + 0.5) * 3);
        for (int k = 0; k < n; k++) {
            double a = f.angle() + k * 2.4;
            double dist = 4 + 9 * (ContinentMap.hash(k, (int) f.seed(), 0x61) * 0.5 + 0.5);
            double px = f.cx() + Math.cos(a) * dist;
            double pz = f.cz() + Math.sin(a) * dist;
            double r = 1.5 + (ContinentMap.hash(k, (int) f.seed(), 0x2C) * 0.5 + 0.5);
            double d = Math.hypot(x + 0.5 - px, z + 0.5 - pz);
            if (d < r) {
                int h = sea + 2 + (int) Math.round(3 * (ContinentMap.hash(k, (int) f.seed(), 0x4E) * 0.5 + 0.5) * (1 - d / r * 0.6));
                double m = ContinentMap.hash(x, z, 0x19);
                BlockState st = m > 0.35 ? Blocks.PRISMARINE.defaultBlockState() : m < -0.5 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                sink.put(top + 1, h, st);
                return;
            }
        }
    }

    // 試し・撮影: 置く物の中心 (種類ごと)
    public static BlockPos firstFeature(int kind) {
        ContinentMap map = ContinentMap.get();
        for (int j = -2600 / CELL; j <= 2600 / CELL; j++) {
            for (int i = -2600 / CELL; i <= 2600 / CELL; i++) {
                Feature f = cell(map, i, j);
                if (f.kind() == kind) {
                    return new BlockPos(f.cx(), f.base(), f.cz());
                }
            }
        }
        return null;
    }
}
