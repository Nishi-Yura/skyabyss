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
    }

    public static synchronized ContinentMap get() {
        if (instance == null) {
            int[] head = new int[4];
            byte[] h = read("height.bin", head);
            byte[] b = read("biome.bin", head);
            byte[] r = read("region.bin", head);
            instance = new ContinentMap(head[0], head[1], head[2], head[3], h, b, r);
        }
        return instance;
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
        return (int) Math.floor(roads().grade(x, z, rawSurface(x, z)));
    }

    // ならす前の高さ (点 (x, z) の揺らぎを足した値)
    private double raw(double x, double z) {
        return rawSurface((int) Math.floor(x), (int) Math.floor(z));
    }

    double rawSurface(int x, int z) {
        double h = smooth(x + 0.5, z + 0.5, null);
        // 揺らぎの大きさは、まわりの傾き (升の値をつないだ高さの、±4 ブロックの差) からなめらかに決める。
        // 升ごと・土地ごとに切り替えると、升の四角の形の段ができる (2026-10-25 に見つけた「四角い穴」)
        double gx = smooth(x + 4.5, z + 0.5, null) - smooth(x - 3.5, z + 0.5, null);
        double gz = smooth(x + 0.5, z + 4.5, null) - smooth(x + 0.5, z - 3.5, null);
        double g = Math.abs(gx) + Math.abs(gz);
        double amp = Math.min(1.0, g / 3.0) * (1.2 + 1.8 * Math.max(0.0, Math.min(1.0, (g - 3.0) / 12.0)));
        return h + amp * detail(x, z);
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
