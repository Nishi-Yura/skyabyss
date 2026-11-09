package jp.revati.skyabyss.world.continent;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

// 大陸の地形の図 (docs/design/40 の 3 章)。tools/gen/world/gen_continent.py が書いた height.bin (地面の高さ Y)・biome.bin (生物群系の番号)・region.bin (土地の番号)。
// 1 升 = SCALE ブロック。升の間は高さをなめらかにつなぎ (双一次)、細かな揺らぎを足す。図の外は深い海。
// 種に関係なく、どの世界でも同じ形
public final class ContinentMap {
    static final int OUTSIDE_Y = 30;
    private static ContinentMap instance;

    final int size;
    final int scale;
    final int originX;
    final int originZ;
    private final byte[] height;
    private final byte[] biome;
    private final byte[] region;

    // 土地の番号 (region.bin。gen_continent.py の REGION_IDS の並び)
    public static final int MEADOW = 0;
    public static final int OUTSKIRTS = 1;
    public static final int WHALE = 2;
    public static final int TIDE = 3;
    public static final int STORM = 4;
    public static final int EMBER = 5;

    private ContinentMap(int size, int scale, int originX, int originZ, byte[] height, byte[] biome, byte[] region) {
        this.size = size;
        this.scale = scale;
        this.originX = originX;
        this.originZ = originZ;
        this.height = height;
        this.biome = biome;
        this.region = region;
        buildRelief();
    }

    // ---------------------------------------------------------------- 起伏 (2026-11-03 の声「もっと地形に作り込みが欲しい。地形が作り込まれてないと、移動の楽しみが減る」)
    // 図の高さ (升を双一次でつないだもの) は、平らな土地では一枚の板になる。土地ごとの強さで二つを足す:
    //   うねり: 25 ブロックほどの丘と小さなこぶ (平原・野・林・丘陵。砂漠は砂丘)
    //   段丘: 丘陵・峰・赤い荒野で、高さを 7〜9 の段に寄せる (段の縁は崖になり、solid の地層の面が出る)
    // 強さは升ごとに決めてから広くぼかし (升の境で段ができないように)、都・町・野営地・船着き場のまわりと海べりでは 0 にする。
    // 前の版で作った世界は、地上を作り直して合わせる (upgrade/ContinentRegen)
    private float[] swellAmp;
    private float[] terraceK;


    // うねりの強さ (ブロック。±この値)
    static float swellOf(int b) {
        return switch (b) {
            case ContinentBiomes.PLAINS -> 2.2f;
            case ContinentBiomes.MEADOW -> 2.6f;
            case ContinentBiomes.SAVANNA -> 2.0f;
            case ContinentBiomes.FOREST, ContinentBiomes.BIRCH_FOREST, ContinentBiomes.FLOWER_FOREST -> 2.8f;
            case ContinentBiomes.TAIGA, ContinentBiomes.PINE_TAIGA -> 3.0f;
            case ContinentBiomes.WINDSWEPT_HILLS -> 3.4f;
            case ContinentBiomes.GRAVELLY_HILLS, ContinentBiomes.STONY_PEAKS, ContinentBiomes.SNOWY_SLOPES, ContinentBiomes.DESERT -> 3.0f;
            case ContinentBiomes.BADLANDS, ContinentBiomes.WOODED_BADLANDS -> 1.6f;
            case ContinentBiomes.ERODED_BADLANDS -> 2.2f;
            case ContinentBiomes.SWAMP -> 0.8f;
            case ContinentBiomes.BEACH -> 0.6f;
            default -> 0f;
        };
    }

    // 段丘の強さ (0..1)
    static float terraceOf(int b) {
        return switch (b) {
            case ContinentBiomes.WINDSWEPT_HILLS -> 0.6f;
            case ContinentBiomes.GRAVELLY_HILLS -> 0.5f;
            case ContinentBiomes.STONY_PEAKS -> 0.5f;
            case ContinentBiomes.SNOWY_SLOPES -> 0.3f;
            case ContinentBiomes.BADLANDS -> 0.9f;
            case ContinentBiomes.WOODED_BADLANDS -> 0.7f;
            case ContinentBiomes.ERODED_BADLANDS -> 0.5f;
            case ContinentBiomes.TAIGA, ContinentBiomes.PINE_TAIGA -> 0.2f;
            default -> 0f;
        };
    }

    private void buildRelief() {
        int n = size * size;
        float[] a = new float[n];
        float[] k = new float[n];
        for (int c = 0; c < n; c++) {
            int b = biome[c] & 0xFF;
            a[c] = swellOf(b);
            k[c] = terraceOf(b);
            // はじまりの島は道と寄り道の窪みを手で形にしたので、うねりは控えめに
            if ((region[c] & 0xFF) == OUTSKIRTS) {
                a[c] = Math.min(a[c], 1.0f);
                k[c] = 0f;
            }
        }
        // 半径 5 升 (20 ブロック) の箱を二度: 升の境の切り替えを 40 ブロックほどでなじませる
        for (int pass = 0; pass < 2; pass++) {
            a = blur(a, 5);
            k = blur(k, 5);
        }
        // 平らに残す所 {x, z, ここまでは 0, ここからは元のまま}
        ContinentLayout lay = ContinentLayout.get();
        java.util.List<int[]> flats = new java.util.ArrayList<>();
        flats.add(new int[] {lay.capital().getX(), lay.capital().getZ(), 175, 215});
        flats.add(new int[] {lay.start().getX(), lay.start().getZ(), 30, 60});
        flats.add(new int[] {lay.dock().getX(), lay.dock().getZ(), 24, 48});
        for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
            net.minecraft.core.BlockPos town = lay.town(t);
            if (town != null) {
                flats.add(new int[] {town.getX(), town.getZ(), 100, 135});
            }
        }
        for (java.util.List<net.minecraft.core.BlockPos> f : lay.ferryDocks()) {
            for (net.minecraft.core.BlockPos p : f) {
                flats.add(new int[] {p.getX(), p.getZ(), 24, 48});
            }
        }
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int x = originX + i * scale;
                int z = originZ + j * scale;
                double f = 1.0;
                for (int[] q : flats) {
                    double d = Math.hypot(x - q[0], z - q[1]);
                    if (d < q[3]) {
                        double t = Math.max(0.0, (d - q[2]) / (q[3] - q[2]));
                        f = Math.min(f, t * t * (3 - 2 * t));
                    }
                }
                a[j * size + i] *= (float) f;
                k[j * size + i] *= (float) f;
            }
        }
        // 段丘は道から離す (道の芯から 10 までは 0、40 で元のまま)。道は段丘の谷を通り、崖の帯は道から見上げる所に出る
        // (道の均しは前後 18 ブロックの平均なので、道の真下に 8 段の崖があると、道に 2 段の段差が残った)
        float[] road = roadDistance(lay);
        for (int c = 0; c < size * size; c++) {
            double t = Math.max(0.0, Math.min(1.0, (road[c] - 10.0) / 30.0));
            k[c] *= (float) (t * t * (3 - 2 * t));
        }
        swellAmp = a;
        terraceK = k;
    }

    // 升ごとの、いちばん近い道の芯までの距離 (ブロック。道を升に描いてから、縦横斜めの二度なめで広げる近い値)
    private float[] roadDistance(ContinentLayout lay) {
        float[] d = new float[size * size];
        java.util.Arrays.fill(d, 1e6f);
        for (java.util.List<net.minecraft.core.BlockPos> line : lay.roads()) {
            for (int n = 0; n + 1 < line.size(); n++) {
                net.minecraft.core.BlockPos p0 = line.get(n);
                net.minecraft.core.BlockPos p1 = line.get(n + 1);
                double len = Math.hypot(p1.getX() - p0.getX(), p1.getZ() - p0.getZ());
                int steps = Math.max(1, (int) Math.ceil(len / (scale * 0.5)));
                for (int q = 0; q <= steps; q++) {
                    double x = p0.getX() + (p1.getX() - p0.getX()) * q / (double) steps;
                    double z = p0.getZ() + (p1.getZ() - p0.getZ()) * q / (double) steps;
                    int i = (int) Math.round((x - originX) / scale);
                    int j = (int) Math.round((z - originZ) / scale);
                    if (i >= 0 && j >= 0 && i < size && j < size) {
                        d[j * size + i] = 0f;
                    }
                }
            }
        }
        float s1 = scale;
        float s2 = (float) (scale * Math.sqrt(2));
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float v = d[j * size + i];
                if (i > 0) {
                    v = Math.min(v, d[j * size + i - 1] + s1);
                }
                if (j > 0) {
                    v = Math.min(v, d[(j - 1) * size + i] + s1);
                    if (i > 0) {
                        v = Math.min(v, d[(j - 1) * size + i - 1] + s2);
                    }
                    if (i + 1 < size) {
                        v = Math.min(v, d[(j - 1) * size + i + 1] + s2);
                    }
                }
                d[j * size + i] = v;
            }
        }
        for (int j = size - 1; j >= 0; j--) {
            for (int i = size - 1; i >= 0; i--) {
                float v = d[j * size + i];
                if (i + 1 < size) {
                    v = Math.min(v, d[j * size + i + 1] + s1);
                }
                if (j + 1 < size) {
                    v = Math.min(v, d[(j + 1) * size + i] + s1);
                    if (i + 1 < size) {
                        v = Math.min(v, d[(j + 1) * size + i + 1] + s2);
                    }
                    if (i > 0) {
                        v = Math.min(v, d[(j + 1) * size + i - 1] + s2);
                    }
                }
                d[j * size + i] = v;
            }
        }
        return d;
    }

    // 升の値の箱ぼかし (縦横に分けて)
    private float[] blur(float[] src, int r) {
        float[] tmp = new float[src.length];
        float[] out = new float[src.length];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float s = 0;
                int c = 0;
                for (int d = -r; d <= r; d++) {
                    int ii = i + d;
                    if (ii >= 0 && ii < size) {
                        s += src[j * size + ii];
                        c++;
                    }
                }
                tmp[j * size + i] = s / c;
            }
        }
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float s = 0;
                int c = 0;
                for (int d = -r; d <= r; d++) {
                    int jj = j + d;
                    if (jj >= 0 && jj < size) {
                        s += tmp[jj * size + i];
                        c++;
                    }
                }
                out[j * size + i] = s / c;
            }
        }
        return out;
    }

    // 段丘の強さ (0..1)。崖の面の材を決めるのに使う (ContinentChunkGenerator)
    public double terraceAt(int x, int z) {
        return field(terraceK, x + 0.5, z + 0.5);
    }

    // 升の値を双一次でつないだ値 (図の外は 0)
    private double field(float[] f, double x, double z) {
        double fx = (x - originX) / scale;
        double fz = (z - originZ) / scale;
        int i = (int) Math.floor(fx);
        int j = (int) Math.floor(fz);
        if (i < 0 || j < 0 || i + 1 >= size || j + 1 >= size) {
            return 0;
        }
        double tx = fx - i;
        double tz = fz - j;
        double top = f[j * size + i] + (f[j * size + i + 1] - f[j * size + i]) * tx;
        double bottom = f[(j + 1) * size + i] + (f[(j + 1) * size + i + 1] - f[(j + 1) * size + i]) * tx;
        return top + (bottom - top) * tz;
    }

    // うねり (-1..1): 27・13・6 ブロックの細かさを重ねる
    static double swell(int x, int z) {
        return 0.55 * value(x / 27.0, z / 27.0, 0x1E57) + 0.3 * value(x / 13.0, z / 13.0, 0x2C6B) + 0.15 * value(x / 6.0, z / 6.0, 0x4F81);
    }

    // 段丘: 高さ h を、段の高さ 7〜9 (場所でゆっくり変わる) の段に寄せた値。段の 8 割は平ら、残りで一気に次の段へ上がる
    // (細かな段を重ねると田の段々のようになる。少ない高い崖の帯にして、solid の地層の面を見せる)。
    // 段の境は等高線から少しずらす (どこまでも同じ高さの縁が続かないように)
    static double terrace(int x, int z, double h) {
        double step = 8.0 + 1.0 * value(x / 61.0, z / 61.0, 0x3D17);
        double o = 3.0 * value(x / 19.0, z / 19.0, 0x6E29);
        double t = (h + o) / step;
        double base = Math.floor(t);
        double f = t - base;
        double rise = f < 0.8 ? 0.0 : (f - 0.8) / 0.2;
        rise = rise * rise * (3 - 2 * rise);
        return (base + rise) * step - o;
    }

    // 読み込みは一度だけ (holder。ワールド生成のスレッドとサーバースレッドがクラスのロックを取り合わないように。2026-11-02 の監査)
    private static final class Holder {
        static final ContinentMap I = load();
    }

    private static ContinentMap load() {
        int[] head = new int[4];
        byte[] h = read("height.bin", head);
        byte[] b = read("biome.bin", head);
        byte[] r = read("region.bin", head);
        return new ContinentMap(head[0], head[1], head[2], head[3], h, b, r);
    }

    public static ContinentMap get() {
        return Holder.I;
    }

    private static byte[] read(String name, int[] head) {
        String path = "/data/skyabyss/continent/" + name;
        try (InputStream raw = ContinentMap.class.getResourceAsStream(path)) {
            if (raw == null) {
                throw new IllegalStateException("missing " + path);
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'S' || magic[1] != 'K' || magic[2] != 'C' || magic[3] != 'N') {
                throw new IllegalStateException("bad header in " + path);
            }
            for (int i = 0; i < 4; i++) {
                head[i] = in.readInt();
            }
            byte[] data = new byte[head[0] * head[0]];
            in.readFully(data);
            return data;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private int raw(int i, int j) {
        if (i < 0 || j < 0 || i >= size || j >= size) {
            return OUTSIDE_Y;
        }
        return height[j * size + i] & 0xFF;
    }

    // 升の値をつないだ高さ (揺らぎなし)。range には四隅の高さの差を入れる (平らな所では揺らぎを足さない)
    double smooth(double x, double z, double[] range) {
        double fx = (x - originX) / scale;
        double fz = (z - originZ) / scale;
        int i = (int) Math.floor(fx);
        int j = (int) Math.floor(fz);
        double tx = fx - i;
        double tz = fz - j;
        int a = raw(i, j);
        int b = raw(i + 1, j);
        int c = raw(i, j + 1);
        int d = raw(i + 1, j + 1);
        if (range != null) {
            range[0] = Math.max(Math.max(a, b), Math.max(c, d)) - Math.min(Math.min(a, b), Math.min(c, d));
        }
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    // 道の均し (ContinentRoads)。初めて要ったときに作る
    private volatile ContinentRoads roads;

    private ContinentRoads roads() {
        ContinentRoads r = roads;
        if (r == null) {
            synchronized (this) {
                r = roads;
                if (r == null) {
                    r = ContinentRoads.build(ContinentLayout.get().roads(), (px, pz) -> raw(px, pz));
                    roads = r;
                }
            }
        }
        return r;
    }

    // 地面の上面の Y (道のそばは道の高さへならす)
    public int surface(int x, int z) {
        // 巨神の居場所では、体の下の地面を体より低くする。祠の広場はならす (ContinentLairs)
        return Math.min(ContinentLairs.cap(x, z), (int) Math.floor(ContinentLairs.pad(this, x, z, graded(x, z))));
    }

    // 道のそばをならした高さ (居場所と祠の広場の前)
    double graded(int x, int z) {
        return roads().grade(x, z, rawSurface(x, z));
    }

    // ならす前の高さ (点 (x, z) の揺らぎを足した値)
    private double raw(double x, double z) {
        return rawSurface((int) Math.floor(x), (int) Math.floor(z));
    }

    double rawSurface(int x, int z) {
        double h = smooth(x + 0.5, z + 0.5, null);
        double k = 0.0;
        // 起伏 (buildRelief)。海べり (海の高さ +1〜+5) では弱めて、浅瀬に島や穴を作らない
        double shore = Math.max(0.0, Math.min(1.0, (h - 64.0) / 4.0));
        if (shore > 0.0) {
            double amp = field(swellAmp, x + 0.5, z + 0.5) * shore;
            if (amp > 0.01) {
                h += amp * swell(x, z);
            }
            k = field(terraceK, x + 0.5, z + 0.5) * shore;
            if (k > 0.01) {
                h += (terrace(x, z, h) - h) * k;
            }
        }
        // 揺らぎの大きさは、まわりの傾き (升の値をつないだ高さの、±4 ブロックの差) からなめらかに決める。
        // 升ごと・土地ごとに切り替えると、升の四角の形の段ができる (2026-10-25 に見つけた「四角い穴」)
        double gx = smooth(x + 4.5, z + 0.5, null) - smooth(x - 3.5, z + 0.5, null);
        double gz = smooth(x + 0.5, z + 4.5, null) - smooth(x + 0.5, z - 3.5, null);
        double g = Math.abs(gx) + Math.abs(gz);
        double amp = Math.min(1.0, g / 3.0) * (1.2 + 1.8 * Math.max(0.0, Math.min(1.0, (g - 3.0) / 12.0)));
        // 段丘の所では弱める (段の平らな面に細かな穴が開かない。崖の縁は段丘が作る)
        amp *= 1.0 - 0.75 * k;
        // 外れの帯の見せ場の地形 (干潟・灰の砂丘・熔岩の川の窪み。FrontierTerrain)
        return FrontierTerrain.height(region(x, z), x, z, h + amp * detail(x, z));
    }

    // 土地の番号 (近い升)
    public int biome(int x, int z) {
        int i = Math.floorDiv(x - originX + scale / 2, scale);
        int j = Math.floorDiv(z - originZ + scale / 2, scale);
        if (i < 0 || j < 0 || i >= size || j >= size) {
            return ContinentBiomes.DEEP_OCEAN;
        }
        return biome[j * size + i] & 0xFF;
    }

    // 土地の番号 (近い升)。図の外は都のまわり
    public int region(int x, int z) {
        int i = Math.floorDiv(x - originX + scale / 2, scale);
        int j = Math.floorDiv(z - originZ + scale / 2, scale);
        if (i < 0 || j < 0 || i >= size || j >= size) {
            return MEADOW;
        }
        return region[j * size + i] & 0xFF;
    }

    public boolean inside(int x, int z) {
        return x >= originX && z >= originZ && x < originX + size * scale && z < originZ + size * scale;
    }

    // 細かな揺らぎ (-1..1)。決まった値の格子をなめらかにつなぐ。二つの細かさを重ねる
    static double detail(int x, int z) {
        return 0.65 * value(x / 7.0, z / 7.0, 0x51A2) + 0.35 * value(x / 3.0, z / 3.0, 0x7C13);
    }

    static double value(double x, double z, int salt) {
        int i = (int) Math.floor(x);
        int j = (int) Math.floor(z);
        double tx = x - i;
        double tz = z - j;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = hash(i, j, salt);
        double b = hash(i + 1, j, salt);
        double c = hash(i, j + 1, salt);
        double d = hash(i + 1, j + 1, salt);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    // -1..1
    static double hash(int i, int j, int salt) {
        long h = i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
