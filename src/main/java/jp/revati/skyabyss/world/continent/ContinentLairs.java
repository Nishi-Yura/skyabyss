package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

// 大陸の巨神の居場所 (docs/design/40 の 3.6。2026-10-28 の試遊「巨神が出るところ固定にして、判定でブロック壊すのやめたら」)。
// 四体とも決まった所で休む (data/skyabyss/continent/lairs.json)。地面の上で休む巨神 (潮殻・雷翼・余燼) は、体の下の地面を地形の生成で
// 体より低くしておく (cap)。体の縁から外へは 1 ブロックにつき LIP_SLOPE ずつ高くして、まわりの地形へなじませる。
// 休んでいる巨神がブロックを壊すこと (TitanClearing) は、大陸ではしない
public final class ContinentLairs {
    private ContinentLairs() {
    }

    // 体の縁から外へ、許す高さが上がる割合 (ブロックごと)
    static final double LIP_SLOPE = 0.9;
    // 体の外のなじませの幅 (これより外は制限しない)
    static final int LIP = 40;

    public record Lair(String titan, double x, double z, float yaw, double base, @Nullable String volume) {
        public boolean grounded() {
            return volume != null && !Double.isNaN(base);
        }
    }

    private record Caps(int x0, int z0, int w, int d, int[] cap) {
        int at(int x, int z) {
            int i = x - x0;
            int j = z - z0;
            if (i < 0 || j < 0 || i >= w || j >= d) {
                return Integer.MAX_VALUE;
            }
            return cap[j * w + i];
        }
    }

    private static Map<String, Lair> lairs;
    private static List<Caps> caps;

    public static synchronized Map<String, Lair> all() {
        if (lairs == null) {
            Map<String, Lair> out = new HashMap<>();
            try (InputStream in = ContinentLairs.class.getResourceAsStream("/data/skyabyss/continent/lairs.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                        if (!e.getValue().isJsonObject() || e.getKey().equals("shrines")) {
                            continue;
                        }
                        JsonObject l = e.getValue().getAsJsonObject();
                        out.put(e.getKey(), new Lair(e.getKey(), l.get("x").getAsDouble(), l.get("z").getAsDouble(), l.get("yaw").getAsFloat(),
                                l.has("base") ? l.get("base").getAsDouble() : Double.NaN, l.has("volume") ? l.get("volume").getAsString() : null));
                    }
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.warn("Continent lairs could not be read: {}", e.toString());
            }
            lairs = Map.copyOf(out);
        }
        return lairs;
    }

    public static boolean continent(ServerLevel level) {
        ServerLevel over = level.dimension() == Level.OVERWORLD ? level : level.getServer().overworld();
        return over.getChunkSource().getGenerator() instanceof ContinentChunkGenerator;
    }

    // 自己テストの間は、居場所を使わない (巨神の段の多くは、人のそばへ現れて去る前の動きを確かめる)。居場所を確かめる段だけ testLive = true。
    // 地形の生成 (cap・祠の広場) は変わらない
    public static volatile boolean testLive;

    public static boolean active(ServerLevel level) {
        return continent(level) && (testLive || !jp.revati.skyabyss.SelfTestRunner.enabled());
    }

    // 大陸の世界のときだけ、その巨神の居場所 (titan = 見守りの枠の名: stone_whale / tide_shell / thunder_wing / ember)
    @Nullable
    public static Lair of(ServerLevel level, String titan) {
        return active(level) ? all().get(titan) : null;
    }

    // ---------------------------------------------------------------- 祠の広場
    // 周回の祠 (titan/shrine/TitanShrine) は、土地の町から跡地へ向かう道の、町を出て 90 ほどの道ばた (lairs.json の shrines)。
    // 地形の生成で、半径 PAD_R を平らにし、PAD_BLEND かけてまわりへなじませる (台座が宙に浮いたり埋まったりしない)
    static final int PAD_R = 7;
    static final int PAD_BLEND = 16;

    private static Map<String, int[]> shrines;
    // 広場の高さ (初めて要ったときに地図から読む)
    private static final Map<String, Integer> PAD_Y = new java.util.concurrent.ConcurrentHashMap<>();

    public static synchronized Map<String, int[]> shrines() {
        if (shrines == null) {
            Map<String, int[]> out = new HashMap<>();
            try (InputStream in = ContinentLairs.class.getResourceAsStream("/data/skyabyss/continent/lairs.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (o.has("shrines")) {
                        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("shrines").entrySet()) {
                            var a = e.getValue().getAsJsonArray();
                            out.put(e.getKey(), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
                        }
                    }
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.warn("Continent shrines could not be read: {}", e.toString());
            }
            shrines = Map.copyOf(out);
        }
        return shrines;
    }

    // 大陸の世界のときだけ、その巨神の祠の中心 (x, z)
    @Nullable
    public static int[] shrine(ServerLevel level, String titan) {
        return continent(level) ? shrines().get(titan) : null;
    }

    // 地形の生成から (ContinentMap.surface): 祠の広場をならした高さ。広場の外は h のまま
    static double pad(ContinentMap map, int x, int z, double h) {
        for (Map.Entry<String, int[]> e : shrines().entrySet()) {
            int[] c = e.getValue();
            double d = Math.hypot(x - c[0], z - c[1]);
            if (d >= PAD_R + PAD_BLEND) {
                continue;
            }
            int y = PAD_Y.computeIfAbsent(e.getKey(), k -> (int) Math.floor(map.graded(c[0], c[1])));
            double w = d <= PAD_R ? 1 : 1 - (d - PAD_R) / PAD_BLEND;
            w = w * w * (3 - 2 * w);
            return h + (y - h) * w;
        }
        return h;
    }

    // 祠の広場の平らな所 (と少しの縁) か
    static boolean onPad(int x, int z) {
        for (int[] c : shrines().values()) {
            if (Math.hypot(x - c[0], z - c[1]) <= PAD_R + 3) {
                return true;
            }
        }
        return false;
    }

    // 地形の生成から: (x, z) の地面の上面が、これより高くてはいけない値 (居場所の外は Integer.MAX_VALUE)
    public static int cap(int x, int z) {
        int best = Integer.MAX_VALUE;
        for (Caps c : caps()) {
            best = Math.min(best, c.at(x, z));
        }
        return best;
    }

    // 体の下となじませの幅の中か、祠の広場か (木・見どころ・道を置かない所)
    public static boolean inside(int x, int z) {
        return cap(x, z) != Integer.MAX_VALUE || onPad(x, z);
    }

    private static List<Caps> caps() {
        List<Caps> c = caps;
        if (c == null) {
            synchronized (ContinentLairs.class) {
                if (caps == null) {
                    List<Caps> out = new ArrayList<>();
                    for (Lair l : all().values()) {
                        if (l.grounded()) {
                            Caps built = build(l);
                            if (built != null) {
                                out.add(built);
                            }
                        }
                    }
                    caps = List.copyOf(out);
                }
                c = caps;
            }
        }
        return c;
    }

    @Nullable
    private static Caps build(Lair l) {
        TitanVolume v = TitanVolume.get(l.volume());
        if (v == null) {
            return null;
        }
        double fx = -Mth.sin(l.yaw() * Mth.DEG_TO_RAD);
        double fz = Mth.cos(l.yaw() * Mth.DEG_TO_RAD);
        List<TitanVolume.Column> cols = v.coreColumns();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TitanVolume.Column c : cols) {
            double wx = l.x() + fx * c.fwd() + fz * c.side();
            double wz = l.z() + fz * c.fwd() - fx * c.side();
            minX = Math.min(minX, Mth.floor(wx));
            minZ = Math.min(minZ, Mth.floor(wz));
            maxX = Math.max(maxX, Mth.floor(wx));
            maxZ = Math.max(maxZ, Mth.floor(wz));
        }
        int x0 = minX - LIP - 2;
        int z0 = minZ - LIP - 2;
        int w = maxX - minX + 2 * LIP + 5;
        int d = maxZ - minZ + 2 * LIP + 5;
        double[] f = new double[w * d];
        java.util.Arrays.fill(f, Double.POSITIVE_INFINITY);
        // 体からの距離 (縁のなじませの幅を測る)
        double[] dist = new double[w * d];
        java.util.Arrays.fill(dist, Double.POSITIVE_INFINITY);
        for (TitanVolume.Column c : cols) {
            double wx = l.x() + fx * c.fwd() + fz * c.side();
            double wz = l.z() + fz * c.fwd() - fx * c.side();
            // 地面の上面のブロックが体の底に触れない高さ
            double top = Math.floor(l.base() + c.lo()) - 1;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int k = (Mth.floor(wz) + dz - z0) * w + (Mth.floor(wx) + dx - x0);
                    f[k] = Math.min(f[k], top);
                    dist[k] = 0;
                }
            }
        }
        // 縁から外へなだらかに (面取りの距離変換。斜めは √2)
        relax(f, w, d, LIP_SLOPE);
        relax(dist, w, d, 1.0);
        int[] cap = new int[w * d];
        for (int k = 0; k < cap.length; k++) {
            cap[k] = dist[k] > LIP || Double.isInfinite(f[k]) ? Integer.MAX_VALUE : (int) Math.floor(f[k]);
        }
        return new Caps(x0, z0, w, d, cap);
    }

    private static void relax(double[] f, int w, int d, double s) {
        double sd = s * Math.sqrt(2);
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    int k = j * w + i;
                    double best = f[k];
                    if (i > 0) {
                        best = Math.min(best, f[k - 1] + s);
                    }
                    if (j > 0) {
                        best = Math.min(best, f[k - w] + s);
                        if (i > 0) {
                            best = Math.min(best, f[k - w - 1] + sd);
                        }
                        if (i < w - 1) {
                            best = Math.min(best, f[k - w + 1] + sd);
                        }
                    }
                    f[k] = best;
                }
            }
            for (int j = d - 1; j >= 0; j--) {
                for (int i = w - 1; i >= 0; i--) {
                    int k = j * w + i;
                    double best = f[k];
                    if (i < w - 1) {
                        best = Math.min(best, f[k + 1] + s);
                    }
                    if (j < d - 1) {
                        best = Math.min(best, f[k + w] + s);
                        if (i < w - 1) {
                            best = Math.min(best, f[k + w + 1] + sd);
                        }
                        if (i > 0) {
                            best = Math.min(best, f[k + w - 1] + sd);
                        }
                    }
                    f[k] = best;
                }
            }
        }
    }
}
