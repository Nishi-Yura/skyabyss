package jp.revati.skyabyss.titan.solid;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.world.phys.Vec3;

// 巨神の立入禁止の体積 (docs/design/02 の「体に入れない」)。tools/gen_titan_volumes.py が近景のメッシュから焼いた柱の表を読む。
// 座標は巨神の向きの局所 (横 = 左が +, 上, 前)、単位はブロック、柱は 2 ブロック四方。各柱は「中が詰まった区間」の並び (半ブロックの整数)。
//  - keepOut: メッシュを 3 ブロック膨らませた体積。人はここに入れない (やさしく押し出す。カメラも入れない)
//  - core: 0.5 ブロックだけ膨らませた体。矢や弾はここで消え、人が入ったら一息に外へ出す
public final class TitanVolume {
    private static final Map<String, TitanVolume> CACHE = new HashMap<>();
    // 押し出しの探索の向き (局所の単位ベクトル)。水平は 15 度刻み、上下は斜めも
    private static final double[][] DIRS = buildDirs();

    private final double cell;
    private final double x0;
    private final double z0;
    private final int nx;
    private final int nz;
    private final double margin;
    private final int[][] keepOut;
    private final int[][] core;

    private TitanVolume(double cell, double x0, double z0, int nx, int nz, double margin, int[][] keepOut, int[][] core) {
        this.cell = cell;
        this.x0 = x0;
        this.z0 = z0;
        this.nx = nx;
        this.nz = nz;
        this.margin = margin;
        this.keepOut = keepOut;
        this.core = core;
    }

    // 巨神の名前 (stone_whale / tide_shell / thunder_wing / ember_titan) の体積。無ければ null
    public static synchronized TitanVolume get(String name) {
        if (CACHE.containsKey(name)) {
            return CACHE.get(name);
        }
        TitanVolume v = load(name);
        CACHE.put(name, v);
        return v;
    }

    private static TitanVolume load(String name) {
        String path = "/data/skyabyss/titan_volumes/" + name + ".json";
        try (InputStream in = TitanVolume.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new TitanVolume(o.get("cell").getAsDouble(), o.get("x0").getAsDouble(), o.get("z0").getAsDouble(), o.get("nx").getAsInt(),
                    o.get("nz").getAsInt(), o.get("margin").getAsDouble(), cols(o.getAsJsonArray("cols")), cols(o.getAsJsonArray("core")));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int[][] cols(JsonArray a) {
        int[][] out = new int[a.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray c = a.get(i).getAsJsonArray();
            int[] spans = new int[c.size()];
            for (int k = 0; k < spans.length; k++) {
                spans[k] = c.get(k).getAsInt();
            }
            out[i] = spans;
        }
        return out;
    }

    // 試験用: 柱の表を直接作る (単位は半ブロック)
    static TitanVolume ofBox(double cell, double x0, double z0, int nx, int nz, double margin, int loHalf, int hiHalf) {
        int[][] cols = new int[nx * nz][];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = new int[] {loHalf, hiHalf};
        }
        return new TitanVolume(cell, x0, z0, nx, nz, margin, cols, cols);
    }

    public double margin() {
        return margin;
    }

    // 局所 (横, 上, 前) が立入禁止 (3 ブロック膨らませた体積) の中か
    public boolean inKeepOut(double side, double up, double fwd) {
        return inside(keepOut, side, up, fwd);
    }

    // 局所 (横, 上, 前) が体 (0.5 ブロック膨らませた体積) の中か
    public boolean inCore(double side, double up, double fwd) {
        return inside(core, side, up, fwd);
    }

    private boolean inside(int[][] cols, double side, double up, double fwd) {
        int i = (int) Math.floor((side - x0) / cell);
        int j = (int) Math.floor((fwd - z0) / cell);
        if (i < 0 || j < 0 || i >= nx || j >= nz) {
            return false;
        }
        int[] spans = cols[i * nz + j];
        double h = up * 2.0;
        for (int k = 0; k + 1 < spans.length; k += 2) {
            if (h >= spans[k] && h <= spans[k + 1]) {
                return true;
            }
        }
        return false;
    }

    // 体の柱 (core) の一本: 局所の横・前 (柱の真ん中) と、詰まった区間のいちばん下・上 (局所の高さ)
    public record Column(double side, double fwd, double lo, double hi) {
    }

    private java.util.List<Column> coreColumns;

    // 体の柱の一覧 (落ちる影・雲や水しぶきを出す所を選ぶのに使う。描画側が何度も呼ぶので一度だけ作る)
    public synchronized java.util.List<Column> coreColumns() {
        if (coreColumns == null) {
            java.util.List<Column> l = new java.util.ArrayList<>();
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < nz; j++) {
                    int[] spans = core[i * nz + j];
                    if (spans.length >= 2) {
                        l.add(new Column(x0 + (i + 0.5) * cell, z0 + (j + 0.5) * cell, spans[0] / 2.0, spans[spans.length - 1] / 2.0));
                    }
                }
            }
            coreColumns = java.util.List.copyOf(l);
        }
        return coreColumns;
    }

    // 地面や海面へ落ちる影の格子 (真上から見た体の輪郭)。4 ブロックの升目の角ごとの濃さ (0〜1。体の下は 1、立入禁止の縁の外側へなだらかに 0)
    public record Shadow(double x0, double z0, double tile, int ni, int nj, float[] corner) {
        public float alphaAt(int i, int j) {
            return corner[i * (nj + 1) + j];
        }
    }

    private Shadow shadow;

    public synchronized Shadow shadow() {
        if (shadow == null) {
            int tiles = 2;
            int ni = (nx + tiles - 1) / tiles;
            int nj = (nz + tiles - 1) / tiles;
            float[][] cover = new float[ni][nj];
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < nz; j++) {
                    float c = core[i * nz + j].length > 0 ? 1f : keepOut[i * nz + j].length > 0 ? 0.5f : 0f;
                    cover[i / tiles][j / tiles] += c / (tiles * tiles);
                }
            }
            float[] corner = new float[(ni + 1) * (nj + 1)];
            for (int i = 0; i <= ni; i++) {
                for (int j = 0; j <= nj; j++) {
                    float sum = 0;
                    for (int di = -1; di <= 0; di++) {
                        for (int dj = -1; dj <= 0; dj++) {
                            int ii = i + di;
                            int jj = j + dj;
                            if (ii >= 0 && jj >= 0 && ii < ni && jj < nj) {
                                sum += cover[ii][jj];
                            }
                        }
                    }
                    corner[i * (nj + 1) + j] = sum / 4f;
                }
            }
            shadow = new Shadow(x0, z0, cell * tiles, ni, nj, corner);
        }
        return shadow;
    }

    // 点が立入禁止の中にあるとき、いちばん近い外 (blocked でない所) への局所の移動量。外なら null。
    // blocked は、立入禁止の中 + 通り道 (乗り場) を除いた判定を呼び出し側が渡す。canStand は行き先に立てるか (地面や壁の中へ押し込まない)
    public Vec3 exit(double side, double up, double fwd, Predicate<Vec3> blocked, Predicate<Vec3> canStand) {
        if (!blocked.test(new Vec3(side, up, fwd))) {
            return null;
        }
        double best = Double.MAX_VALUE;
        Vec3 bestMove = null;
        for (double[] d : DIRS) {
            for (double s = 0.5; s <= 90.0; s += 0.5) {
                Vec3 p = new Vec3(side + d[0] * s, up + d[1] * s, fwd + d[2] * s);
                if (!blocked.test(p)) {
                    if (s < best && canStand.test(p)) {
                        best = s;
                        bestMove = new Vec3(d[0] * (s + 0.25), d[1] * (s + 0.25), d[2] * (s + 0.25));
                    }
                    break;
                }
                if (s >= best) {
                    break;
                }
            }
        }
        return bestMove;
    }

    private static double[][] buildDirs() {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        for (int a = 0; a < 360; a += 15) {
            double r = Math.toRadians(a);
            l.add(new double[] {Math.cos(r), 0, Math.sin(r)});
            for (int e : new int[] {30, 60}) {
                double c = Math.cos(Math.toRadians(e));
                double s = Math.sin(Math.toRadians(e));
                l.add(new double[] {Math.cos(r) * c, s, Math.sin(r) * c});
                l.add(new double[] {Math.cos(r) * c, -s, Math.sin(r) * c});
            }
        }
        l.add(new double[] {0, 1, 0});
        l.add(new double[] {0, -1, 0});
        return l.toArray(new double[0][]);
    }
}
