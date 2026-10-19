package jp.revati.skyabyss.client.lowpoly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;

// 非キューブの低ポリメッシュ (docs/design/15_high_poly.md 9 章)。読み込み時に骨ごとの配列へ焼いておき、描画では変換して流すだけにする。
// 三角形は QUADS の描画型に合わせ、同一平面で平行四辺形に並ぶ二枚はひとつの四角にまとめ、残りは 4 つ目の頂点を繰り返して出す。
public final class LowPolyMesh {
    public final String name;
    public final ResourceLocation texture;
    public final ResourceLocation glowMask; // 光る画素だけに元の色を置いた画像 (<name>_glow.png、install_lowpoly.py が --gear のときに焼く)。無ければ null
    // true: 頂点は受け口の骨の原点が基準 (武器・盾)。false: モデル全体の絶対座標 (GeckoLib の geo の cube と同じ)
    public final boolean socket;
    public final Map<String, Bone> bones = new LinkedHashMap<>();
    public final int triangles;
    public final int quads;

    private LowPolyMesh(String name, ResourceLocation texture, ResourceLocation glowMask, boolean socket, int triangles, int quads) {
        this.name = name;
        this.texture = texture;
        this.glowMask = glowMask;
        this.socket = socket;
        this.triangles = triangles;
        this.quads = quads;
    }

    // 骨一つぶんの焼いた頂点。pos は描画空間 (GeckoLib が cube を焼くのと同じ x 反転、1/16 ブロック → ブロック) の頂点 4 つずつ (12 float / quad)。
    // uv は 0..1 の頂点 4 つずつ (8 float / quad)。nrm は面の法線 (3 float / quad、描画空間で外向き)
    public static final class Bone {
        public final String name;
        public final String parent;
        public final float[] pos;
        public final float[] uv;
        public final float[] nrm;
        // 頂点ごとの明るさ (quad ごとに 4 つ)。巨神だけ: 下ほど暗い・面が下向きだと暗い・塞いだ穴の面は暗い。無ければ null
        public final float[] vshade;
        public final int quadCount;
        // 塞いだ穴の面 (巨神) が始まる quad の番号 (無ければ quadCount)。LowPoly.titanWeight が false のときはここまでしか描かない
        public final int capStart;
        // 巨神の焼き込みの陰をかけた頂点色。色 (colour) が前と同じなら作り直さない (描画スレッドだけが読み書きする)
        int[] shadedColours;
        int shadedFor;
        // 発光の重ね描きで、発光マスクに光る画素が掛かる quad か (LowPolyGlow が作る)。maskFor はどのマスクで作ったか
        boolean[] glowQuads;
        ResourceLocation glowQuadsFor;

        Bone(String name, String parent, float[] pos, float[] uv, float[] nrm, float[] vshade, int capStart) {
            this.name = name;
            this.parent = parent;
            this.pos = pos;
            this.uv = uv;
            this.nrm = nrm;
            this.vshade = vshade;
            this.quadCount = nrm.length / 3;
            this.capStart = capStart;
        }
    }

    public static LowPolyMesh parse(String name, JsonObject json, boolean glowExists) {
        int[] size = {256, 256};
        if (json.has("texture_size")) {
            JsonArray a = json.getAsJsonArray("texture_size");
            size = new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
        }
        String texName = textureName(json, name);
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + texName + ".png");
        ResourceLocation glow = glowExists ? ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + texName + "_glow.png") : null;
        boolean socket = (json.has("socket") && json.get("socket").getAsBoolean()) || json.has("attach");
        JsonObject bonesJson = json.getAsJsonObject("bones");

        Map<String, Bone> baked = new LinkedHashMap<>();
        int tris = 0;
        int quads = 0;
        // 巨神: 全体の高さの範囲 (下を暗くする焼き込み用)
        boolean titan = isTitan(name);
        float yMin = Float.MAX_VALUE;
        float yMax = -Float.MAX_VALUE;
        if (titan) {
            for (Map.Entry<String, JsonElement> e : bonesJson.entrySet()) {
                for (float[] p : floats(e.getValue().getAsJsonObject().getAsJsonArray("vertices"), 3)) {
                    yMin = Math.min(yMin, p[1]);
                    yMax = Math.max(yMax, p[1]);
                }
            }
        }
        for (Map.Entry<String, JsonElement> e : bonesJson.entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            float[][] v = floats(b.getAsJsonArray("vertices"), 3);
            float[][] uv = floats(b.getAsJsonArray("uvs"), 2);
            if (uv.length != v.length) {
                throw new IllegalArgumentException(name + "/" + e.getKey() + ": uvs must be per vertex (" + uv.length + " vs " + v.length + ")");
            }
            // 0..1 に収まらない値は画素座標とみなす
            boolean pixels = false;
            for (float[] t : uv) {
                if (t[0] > 1.0001f || t[1] > 1.0001f) {
                    pixels = true;
                }
            }
            JsonArray tj = b.getAsJsonArray("triangles");
            int n = tj.size();
            int[][] t = new int[n][3];
            for (int i = 0; i < n; i++) {
                JsonArray a = tj.get(i).getAsJsonArray();
                for (int k = 0; k < 3; k++) {
                    t[i][k] = a.get(k).getAsInt();
                    if (t[i][k] < 0 || t[i][k] >= v.length) {
                        throw new IllegalArgumentException(name + "/" + e.getKey() + ": triangle index out of range");
                    }
                }
            }
            int origVerts = v.length;
            // tools/mesh_close.py が書いた蓋: 元の頂点は cap_vertex_start までで、あとは塞ぎの面 (巨神では暗く出す)。読み込み時に塞ぐ必要は無い
            boolean preClosed = b.has("cap_vertex_start");
            if (preClosed) {
                origVerts = b.get("cap_vertex_start").getAsInt();
            }
            if (titan && n > 0 && !preClosed) {
                // 骨の開いた縁 (関節・接ぎ目) に、内側へ引っ込めた暗い蓋をする。ひれ・顎・首が動いて継ぎ目が開いても、中が透けない
                Capped c = capHoles(v, uv, t);
                v = c.v();
                uv = c.uv();
                t = c.t();
                n = t.length;
            }
            List<int[]> quadList = mergeTriangles(v, uv, t);
            int q = quadList.size();
            float[] vshade = titan ? new float[q * 4] : null;
            int capStart = q;
            for (int i = 0; i < q; i++) {
                if ((titan || !preClosed) && quadList.get(i)[0] >= origVerts) {
                    capStart = i;
                    break;
                }
            }
            float[] pos = new float[q * 12];
            float[] uvOut = new float[q * 8];
            float[] nrm = new float[q * 3];
            for (int i = 0; i < q; i++) {
                int[] f = quadList.get(i);
                for (int k = 0; k < 4; k++) {
                    float[] p = v[f[k]];
                    pos[i * 12 + k * 3] = -p[0] / 16f;
                    pos[i * 12 + k * 3 + 1] = p[1] / 16f;
                    pos[i * 12 + k * 3 + 2] = p[2] / 16f;
                    float[] c = uv[f[k]];
                    uvOut[i * 8 + k * 2] = pixels ? c[0] / size[0] : c[0];
                    uvOut[i * 8 + k * 2 + 1] = pixels ? c[1] / size[1] : c[1];
                }
                // 外向きの法線: 反時計回りの三角形 (geo 空間) の外積。描画空間は x を反転しているので x だけ符号を替える
                float[] a = v[f[0]];
                float[] bb = v[f[1]];
                float[] c = v[f[2]];
                float ux = bb[0] - a[0], uy = bb[1] - a[1], uz = bb[2] - a[2];
                float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
                float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-9f) {
                    ny = 1;
                    len = 1;
                }
                nrm[i * 3] = -nx / len;
                nrm[i * 3 + 1] = ny / len;
                nrm[i * 3 + 2] = nz / len;
                if (vshade != null) {
                    for (int k = 0; k < 4; k++) {
                        vshade[i * 4 + k] = f[k] >= origVerts ? CAP_SHADE : weightShade(v[f[k]][1], yMin, yMax, ny / len);
                    }
                }
            }
            String parent = b.has("parent") && !b.get("parent").isJsonNull() ? b.get("parent").getAsString() : null;
            baked.put(e.getKey(), new Bone(e.getKey(), parent, pos, uvOut, nrm, vshade, capStart));
            tris += n;
            quads += q;
        }
        LowPolyMesh mesh = new LowPolyMesh(name, tex, glow, socket, tris, quads);
        mesh.bones.putAll(baked);
        return mesh;
    }

    // ---- 巨神 (stone_whale / tide_shell / thunder_wing / ember_titan とその mid・far) だけの焼き込み
    // 蓋の面の暗さ・体の下の暗さの下限 (1 = 手を加えない)
    static final float CAP_SHADE = 0.30f;
    private static final float BOTTOM_SHADE = 0.52f;

    static boolean isTitan(String name) {
        return name.matches("(stone_whale|tide_shell|thunder_wing|ember_titan)(_mid|_far)?");
    }

    // 重さの見せ方 (docs/design/02 の「重さ」): 体の下ほど暗く (地面・海面に近い側の陰)、面が下を向くと暗く、上は明るいまま
    static float weightShade(float y, float yMin, float yMax, float ny) {
        float h = yMax > yMin ? Math.max(0f, Math.min(1f, (y - yMin) / (yMax - yMin))) : 1f;
        float t = Math.max(0f, Math.min(1f, h / 0.7f));
        float g = BOTTOM_SHADE + (1f - BOTTOM_SHADE) * t * t * (3f - 2f * t);
        float nt = ny < -0.3f ? 0.82f : ny < 0.3f ? 0.96f : 1f;
        return g * nt;
    }

    private record Capped(float[][] v, float[][] uv, int[][] t) {
    }

    // 三角形の辺のうち一枚にしか使われていない辺 (開いた縁) をつないで輪にし、輪の重心を骨の中心の側へ少し引っ込めた頂点との扇で塞ぐ。
    // 蓋の頂点は元の頂点の複製 (uv は縁の三角形の中心の一点) で、元の面の絵・法線には触らない
    private static Capped capHoles(float[][] v, float[][] uv, int[][] t) {
        Map<String, Integer> weld = new java.util.HashMap<>();
        int[] canon = new int[v.length];
        for (int i = 0; i < v.length; i++) {
            String key = Math.round(v[i][0] * 50) + "," + Math.round(v[i][1] * 50) + "," + Math.round(v[i][2] * 50);
            Integer id = weld.get(key);
            if (id == null) {
                id = weld.size();
                weld.put(key, id);
            }
            canon[i] = id;
        }
        // 辺 (小さい番号, 大きい番号) の数と、最初の向き付きの辺 {数, 元の始点, 元の終点, 三角形}
        Map<Long, int[]> edges = new LinkedHashMap<>();
        for (int ti = 0; ti < t.length; ti++) {
            for (int k = 0; k < 3; k++) {
                int a = t[ti][k];
                int b = t[ti][(k + 1) % 3];
                int ca = canon[a];
                int cb = canon[b];
                if (ca == cb) {
                    continue;
                }
                long key = ((long) Math.min(ca, cb) << 32) | Math.max(ca, cb);
                int[] e = edges.get(key);
                if (e == null) {
                    edges.put(key, new int[] {1, a, b, ti});
                } else {
                    e[0]++;
                }
            }
        }
        Map<Integer, List<int[]>> from = new LinkedHashMap<>();
        for (int[] e : edges.values()) {
            if (e[0] == 1) {
                from.computeIfAbsent(canon[e[1]], k -> new ArrayList<>()).add(e);
            }
        }
        if (from.isEmpty()) {
            return new Capped(v, uv, t);
        }
        float cx = 0;
        float cy = 0;
        float cz = 0;
        for (float[] p : v) {
            cx += p[0];
            cy += p[1];
            cz += p[2];
        }
        cx /= v.length;
        cy /= v.length;
        cz /= v.length;
        List<float[]> nv = new ArrayList<>(java.util.Arrays.asList(v));
        List<float[]> nuv = new ArrayList<>(java.util.Arrays.asList(uv));
        List<int[]> nt = new ArrayList<>(java.util.Arrays.asList(t));
        for (List<int[]> starts : new ArrayList<>(from.values())) {
            while (!starts.isEmpty()) {
                // 一つの輪をたどる
                List<int[]> loop = new ArrayList<>();
                int[] e = starts.remove(0);
                loop.add(e);
                int guard = 0;
                while (guard++ < 2000) {
                    List<int[]> next = from.get(canon[e[2]]);
                    if (next == null || next.isEmpty()) {
                        break;
                    }
                    int[] n2 = next.remove(0);
                    if (n2 == loop.get(0)) {
                        break;
                    }
                    loop.add(n2);
                    e = n2;
                }
                if (loop.size() < 3) {
                    continue;
                }
                float lx = 0;
                float ly = 0;
                float lz = 0;
                for (int[] le : loop) {
                    lx += v[le[1]][0];
                    ly += v[le[1]][1];
                    lz += v[le[1]][2];
                }
                lx /= loop.size();
                ly /= loop.size();
                lz /= loop.size();
                int apex = nv.size();
                nv.add(new float[] {lx + (cx - lx) * 0.2f, ly + (cy - ly) * 0.2f, lz + (cz - lz) * 0.2f});
                nuv.add(new float[] {0, 0});
                boolean apexSet = false;
                for (int[] le : loop) {
                    int[] tri = t[le[3]];
                    float[] flat = {(uv[tri[0]][0] + uv[tri[1]][0] + uv[tri[2]][0]) / 3f, (uv[tri[0]][1] + uv[tri[1]][1] + uv[tri[2]][1]) / 3f};
                    if (!apexSet) {
                        nuv.set(apex, flat);
                        apexSet = true;
                    }
                    int a = nv.size();
                    nv.add(v[le[1]]);
                    nuv.add(flat);
                    int b = nv.size();
                    nv.add(v[le[2]]);
                    nuv.add(flat);
                    // 辺と逆向き (b → a) にして、塞いだ面が外を向くようにする
                    nt.add(new int[] {b, a, apex});
                }
            }
        }
        return new Capped(nv.toArray(new float[0][]), nuv.toArray(new float[0][]), nt.toArray(new int[0][]));
    }

    // メッシュ JSON の "texture" (無ければ名前) から、textures/lowpoly/ の下の画像名を取る
    static String textureName(JsonObject json, String name) {
        String s = json.has("texture") ? json.get("texture").getAsString() : name;
        int c = s.indexOf(':');
        if (c >= 0) {
            s = s.substring(c + 1);
        }
        return s.endsWith(".png") ? s.substring(0, s.length() - 4) : s;
    }

    // 隣り合う二枚 [a,b,c] [a,c,d] が位置も uv も平行四辺形 (a+c = b+d) なら一つの四角 (a,b,c,d) にする
    private static List<int[]> mergeTriangles(float[][] v, float[][] uv, int[][] t) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        while (i < t.length) {
            int[] p = t[i];
            if (i + 1 < t.length) {
                int[] q = t[i + 1];
                if (p[0] == q[0] && p[2] == q[1] && parallelogram(v, uv, p[0], p[1], p[2], q[2])) {
                    out.add(new int[] {p[0], p[1], p[2], q[2]});
                    i += 2;
                    continue;
                }
            }
            out.add(new int[] {p[0], p[1], p[2], p[2]});
            i++;
        }
        return out;
    }

    private static boolean parallelogram(float[][] v, float[][] uv, int a, int b, int c, int d) {
        for (int k = 0; k < 3; k++) {
            if (Math.abs(v[a][k] + v[c][k] - v[b][k] - v[d][k]) > 1e-3f) {
                return false;
            }
        }
        for (int k = 0; k < 2; k++) {
            if (Math.abs(uv[a][k] + uv[c][k] - uv[b][k] - uv[d][k]) > 1e-5f) {
                return false;
            }
        }
        return true;
    }

    private static float[][] floats(JsonArray a, int width) {
        float[][] r = new float[a.size()][width];
        for (int i = 0; i < r.length; i++) {
            JsonArray row = a.get(i).getAsJsonArray();
            for (int k = 0; k < width; k++) {
                r[i][k] = row.get(k).getAsFloat();
            }
        }
        return r;
    }
}
