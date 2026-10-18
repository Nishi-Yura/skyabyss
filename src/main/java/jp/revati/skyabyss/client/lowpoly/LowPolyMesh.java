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
    public final ResourceLocation glowMask; // 光る画素だけを置いた画像。無ければ null
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
        public final int quadCount;

        Bone(String name, String parent, float[] pos, float[] uv, float[] nrm) {
            this.name = name;
            this.parent = parent;
            this.pos = pos;
            this.uv = uv;
            this.nrm = nrm;
            this.quadCount = nrm.length / 3;
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
        ResourceLocation glow = glowExists ? ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + texName + "_glowmask.png") : null;
        boolean socket = json.has("socket") && json.get("socket").getAsBoolean();
        JsonObject bonesJson = json.getAsJsonObject("bones");

        Map<String, Bone> baked = new LinkedHashMap<>();
        int tris = 0;
        int quads = 0;
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
            List<int[]> quadList = mergeTriangles(v, uv, t);
            int q = quadList.size();
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
            }
            String parent = b.has("parent") && !b.get("parent").isJsonNull() ? b.get("parent").getAsString() : null;
            baked.put(e.getKey(), new Bone(e.getKey(), parent, pos, uvOut, nrm));
            tris += n;
            quads += q;
        }
        LowPolyMesh mesh = new LowPolyMesh(name, tex, glow, socket, tris, quads);
        mesh.bones.putAll(baked);
        return mesh;
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
