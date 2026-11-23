package jp.revati.skyabyss.client.splat;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

// 低ポリのメッシュ (lowpoly/<名前>.json と textures/lowpoly/ の絵) から点の模型をその場で作る (手で彫った .bin が無い模型すべて)。
// 面に一様に点を撒き (間隔 h ごとの升目に一つ)、絵の色を取り、透明な画素は捨て、光る画素 (発光マスク) は光る点にする。
// 陰は升目のまわりで均した法線で焼き、4 段の明るさにディザで落とす (ピクセルアートの段)。巨神は下ほど暗く。
// 体のモブ (受け口でないメッシュ) は、親と子の骨の点に近い点を二本の骨に分けて、継ぎ目で折れずに曲がるようにする。
// 間隔 h は模型の大きさの 1/140 (細かすぎる・点が多すぎるときは広げる)。tools/gen/splat/ の手彫りと同じ見た目の決まり
public final class SplatGen {
    private static final int MAX_POINTS = 60_000;
    private static final int MAX_POINTS_SOCKET = 14_000;
    // 色を引く uv は三角形の中心へこの割合だけ寄せる (絵の詰め方の縁の黒い画素を拾わない)
    private static final float UV_INSET = 0.12f;
    // 巨神は一体だけで大きく見えるので、点の上限を上げる
    private static final int MAX_POINTS_TITAN = 400_000;
    private static final float[] LEVELS = {0.58f, 0.72f, 0.86f, 1.0f};
    private static final float[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    // 主光 (描画の空間。上と前から)
    private static final float KX = -0.33f, KY = 0.85f, KZ = -0.41f;

    private SplatGen() {
    }

    private record Image(int w, int h, int[] abgr) {
        int at(float u, float v) {
            int x = Math.min(w - 1, Math.max(0, (int) (u * w)));
            int y = Math.min(h - 1, Math.max(0, (int) (v * h)));
            return abgr[y * w + x];
        }
    }

    private static Image load(ResourceManager rm, ResourceLocation loc) {
        if (loc == null) {
            return null;
        }
        Optional<Resource> r = rm.getResource(loc);
        if (r.isEmpty()) {
            return null;
        }
        try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = img.getPixelRGBA(x, y);
                }
            }
            return new Image(w, h, px);
        } catch (Exception e) {
            SkyAbyss.LOGGER.warn("[splat] could not read {}", loc, e);
            return null;
        }
    }

    private static ResourceLocation suffixed(ResourceLocation tex, String suffix) {
        return tex.withPath(p -> p.endsWith(".png") ? p.substring(0, p.length() - 4) + suffix + ".png" : p);
    }

    // paint: 塗る絵 (メッシュの絵か、武器の強化の段の絵)。光る画素は、体のモブの発光マスク (<絵>_glowmask.png) か、品の光る画素の絵 (<絵>_glow.png)
    public static SplatCloud generate(LowPolyMesh mesh, ResourceLocation paint, ResourceManager rm) {
        Image tex = load(rm, paint);
        Image glow = load(rm, suffixed(paint, "_glowmask"));
        if (glow == null) {
            glow = load(rm, suffixed(paint, "_glow"));
        }
        if (glow == null) {
            glow = load(rm, mesh.glowMask);
        }
        if (tex == null) {
            return null;
        }
        List<LowPolyMesh.Bone> bones = new ArrayList<>(mesh.bones.values());
        int nb = bones.size();
        String[] names = new String[nb];
        // 大きさと面積
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        double area = 0;
        for (int b = 0; b < nb; b++) {
            LowPolyMesh.Bone bone = bones.get(b);
            names[b] = bone.name;
            float[] p = bone.pos;
            for (int i = 0; i + 2 < p.length; i += 3) {
                minX = Math.min(minX, p[i]);
                maxX = Math.max(maxX, p[i]);
                minY = Math.min(minY, p[i + 1]);
                maxY = Math.max(maxY, p[i + 1]);
                minZ = Math.min(minZ, p[i + 2]);
                maxZ = Math.max(maxZ, p[i + 2]);
            }
            for (int q = 0; q < bone.capStart; q++) {
                area += quadArea(p, q);
            }
        }
        if (area <= 0) {
            return null;
        }
        float maxDim = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        boolean titan = mesh.name.matches("(stone_whale|tide_shell|thunder_wing|ember_titan)(_mid|_far)?");
        // 品・持ち物 (受け口のメッシュ) は小さく、画面に何個も並ぶので点を少なめに
        int cap = titan ? MAX_POINTS_TITAN : mesh.socket ? MAX_POINTS_SOCKET : MAX_POINTS;
        // 巨神は上限の点の数まで細かく (大きさの割合で決めると粗すぎる)、ほかは大きさの 1/140
        // 小さい模型 (品・小さい敵) も手に持つと大きく映るので、間隔は大きさの 1/140 (下限 0.1/16 ブロック)
        float h = titan ? (float) Math.sqrt(area / cap) : Math.max(0.1f / 16f, Math.min(0.5f, maxDim / 140f));
        if (area / (h * h) > cap) {
            h = (float) Math.sqrt(area / cap);
        }
        SplittableRandom rng = new SplittableRandom(mesh.name.hashCode());

        // 骨ごとに撒く
        List<float[]> boneOut = new ArrayList<>(); // 骨ごと: 点 (x y z nx ny nz r g b glow) の並び
        List<Long2IntOpenHashMap> boneCells = new ArrayList<>();
        float inv = 1f / h;
        for (int b = 0; b < nb; b++) {
            LowPolyMesh.Bone bone = bones.get(b);
            float[] p = bone.pos;
            float[] uv = bone.uv;
            // 撒いた点 (位置・uv・面の法線)
            FloatList s = new FloatList();
            for (int q = 0; q < bone.capStart; q++) {
                for (int t = 0; t < 2; t++) {
                    int i0 = 0, i1 = t == 0 ? 1 : 2, i2 = t == 0 ? 2 : 3;
                    float a = triArea(p, q, i0, i1, i2);
                    if (a < 1e-9f) {
                        continue;
                    }
                    int n = (int) Math.ceil(a * inv * inv * 2.5f);
                    for (int k = 0; k < n; k++) {
                        float r1 = (float) rng.nextDouble();
                        float r2 = (float) rng.nextDouble();
                        if (r1 + r2 > 1f) {
                            r1 = 1f - r1;
                            r2 = 1f - r2;
                        }
                        float w0 = 1f - r1 - r2;
                        float[] v = new float[8];
                        for (int c = 0; c < 3; c++) {
                            v[c] = w0 * p[q * 12 + i0 * 3 + c] + r1 * p[q * 12 + i1 * 3 + c] + r2 * p[q * 12 + i2 * 3 + c];
                        }
                        float c0 = w0 * (1f - UV_INSET) + UV_INSET / 3f, c1 = r1 * (1f - UV_INSET) + UV_INSET / 3f, c2 = r2 * (1f - UV_INSET) + UV_INSET / 3f;
                        v[3] = c0 * uv[q * 8 + i0 * 2] + c1 * uv[q * 8 + i1 * 2] + c2 * uv[q * 8 + i2 * 2];
                        v[4] = c0 * uv[q * 8 + i0 * 2 + 1] + c1 * uv[q * 8 + i1 * 2 + 1] + c2 * uv[q * 8 + i2 * 2 + 1];
                        v[5] = bone.nrm[q * 3];
                        v[6] = bone.nrm[q * 3 + 1];
                        v[7] = bone.nrm[q * 3 + 2];
                        s.add(v);
                    }
                }
            }
            int ns = s.size / 8;
            // 順を混ぜてから、升目に一つだけ残す。法線は升目ごとに足しておく
            int[] order = new int[ns];
            for (int i = 0; i < ns; i++) {
                order[i] = i;
            }
            for (int i = ns - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }
            Long2IntOpenHashMap keep = new Long2IntOpenHashMap();
            keep.defaultReturnValue(-1);
            Long2ObjectOpenHashMap<float[]> nsum = new Long2ObjectOpenHashMap<>();
            for (int oi = 0; oi < ns; oi++) {
                int i = order[oi];
                float[] a = s.data;
                long key = key(fl(a[i * 8] * inv), fl(a[i * 8 + 1] * inv), fl(a[i * 8 + 2] * inv));
                float[] acc = nsum.computeIfAbsent(key, k -> new float[3]);
                acc[0] += a[i * 8 + 5];
                acc[1] += a[i * 8 + 6];
                acc[2] += a[i * 8 + 7];
                if (keep.get(key) < 0) {
                    keep.put(key, i);
                }
            }
            FloatList out = new FloatList();
            Long2IntOpenHashMap cellOf = new Long2IntOpenHashMap();
            cellOf.defaultReturnValue(-1);
            for (Long2IntOpenHashMap.Entry e : keep.long2IntEntrySet()) {
                int i = e.getIntValue();
                float[] a = s.data;
                float x = a[i * 8], y = a[i * 8 + 1], z = a[i * 8 + 2];
                int col = tex.at(a[i * 8 + 3], a[i * 8 + 4]);
                if ((col >>> 24) < 128) {
                    continue;
                }
                int ix = fl(x * inv), iy = fl(y * inv), iz = fl(z * inv);
                // 均した法線 (まわり 3x3x3 の升目)
                float sx = 0, sy = 0, sz = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            float[] acc = nsum.get(key(ix + dx, iy + dy, iz + dz));
                            if (acc != null) {
                                sx += acc[0];
                                sy += acc[1];
                                sz += acc[2];
                            }
                        }
                    }
                }
                float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (len < 1e-6f) {
                    sx = a[i * 8 + 5];
                    sy = a[i * 8 + 6];
                    sz = a[i * 8 + 7];
                    len = 1f;
                }
                sx /= len;
                sy /= len;
                sz /= len;
                int r = col & 255, g = (col >> 8) & 255, bl = (col >> 16) & 255;
                boolean lit = false;
                if (glow != null) {
                    int gc = glow.at(a[i * 8 + 3], a[i * 8 + 4]);
                    lit = (gc >>> 24) > 16 && ((gc & 0xFFFFFF) != 0);
                }
                if (!lit) {
                    float lam = Math.max(0f, sx * KX + sy * KY + sz * KZ);
                    float l = 0.5f + 0.32f * lam + 0.18f * (0.5f + 0.5f * sy);
                    if (titan) {
                        l *= LowPolyMesh.titanShade(y, minY, maxY, sy);
                    }
                    float d = BAYER[(ix + iz) & 3][iy & 3] / 16f;
                    float f = (l - LEVELS[0]) / (LEVELS[3] - LEVELS[0]) * 3f + (d - 0.5f) * 0.9f;
                    float lv = LEVELS[Math.max(0, Math.min(3, Math.round(f)))];
                    r = (int) (r * lv);
                    g = (int) (g * lv);
                    bl = (int) (bl * lv);
                }
                cellOf.put(key(fl(x * inv * 0.5f), fl(y * inv * 0.5f), fl(z * inv * 0.5f)), out.size / 10);
                out.add(new float[] {x, y, z, sx, sy, sz, r, g, bl, lit ? 1 : 0});
            }
            boneOut.add(out.trim());
            boneCells.add(cellOf);
        }

        int total = 0;
        for (float[] o : boneOut) {
            total += o.length / 10;
        }
        SplatCloud.Part part = new SplatCloud.Part(total);
        // 骨の親子 (継ぎ目の混ぜ用)
        Map<String, Integer> idx = new java.util.HashMap<>();
        for (int b = 0; b < nb; b++) {
            idx.put(names[b], b);
        }
        int[] parent = new int[nb];
        for (int b = 0; b < nb; b++) {
            Integer pi = bones.get(b).parent == null ? null : idx.get(bones.get(b).parent);
            parent[b] = pi == null ? -1 : pi;
        }
        boolean blend = !mesh.socket;
        int k = 0;
        float size = h * 1.45f;
        for (int b = 0; b < nb; b++) {
            float[] o = boneOut.get(b);
            int n = o.length / 10;
            // 混ぜる相手の骨 (親と子)
            List<Integer> near = new ArrayList<>();
            if (blend) {
                if (parent[b] >= 0) {
                    near.add(parent[b]);
                }
                for (int c = 0; c < nb; c++) {
                    if (parent[c] == b) {
                        near.add(c);
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                float x = o[i * 10], y = o[i * 10 + 1], z = o[i * 10 + 2];
                part.x[k] = x;
                part.y[k] = y;
                part.z[k] = z;
                part.size[k] = size * (0.9f + 0.2f * (float) rng.nextDouble());
                part.argb[k] = 0xFF000000 | ((int) o[i * 10 + 6] << 16) | ((int) o[i * 10 + 7] << 8) | (int) o[i * 10 + 8];
                part.nx[k] = (byte) Math.round(o[i * 10 + 3] * 127f);
                part.ny[k] = (byte) Math.round(o[i * 10 + 4] * 127f);
                part.nz[k] = (byte) Math.round(o[i * 10 + 5] * 127f);
                part.flags[k] = (byte) (o[i * 10 + 9] > 0.5f ? SplatCloud.F_GLOW : 0);
                part.boneA[k] = (byte) b;
                part.boneB[k] = (byte) b;
                part.weightA[k] = 1f;
                if (!near.isEmpty()) {
                    float best = 2f * h;
                    int other = -1;
                    for (int c : near) {
                        float d = nearest(boneOut.get(c), boneCells.get(c), x, y, z, inv * 0.5f, best);
                        if (d < best) {
                            best = d;
                            other = c;
                        }
                    }
                    if (other >= 0) {
                        float wo = 0.5f * (1f - best / (2f * h));
                        part.boneB[k] = (byte) other;
                        part.weightA[k] = 1f - wo;
                    }
                }
                k++;
            }
        }
        return new SplatCloud(mesh.name, names, part, new SplatCloud.Part(0));
    }

    // 骨 c の点のうち (x,y,z) に一番近い点までの距離 (limit より遠ければ limit)。cells は 2h の升目に一点ずつ
    private static float nearest(float[] o, Long2IntOpenHashMap cells, float x, float y, float z, float inv2, float limit) {
        int ix = fl(x * inv2), iy = fl(y * inv2), iz = fl(z * inv2);
        float best = limit * limit;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int i = cells.get(key(ix + dx, iy + dy, iz + dz));
                    if (i >= 0) {
                        float ex = o[i * 10] - x, ey = o[i * 10 + 1] - y, ez = o[i * 10 + 2] - z;
                        best = Math.min(best, ex * ex + ey * ey + ez * ez);
                    }
                }
            }
        }
        return (float) Math.sqrt(best);
    }

    private static float quadArea(float[] p, int q) {
        return triArea(p, q, 0, 1, 2) + triArea(p, q, 0, 2, 3);
    }

    private static float triArea(float[] p, int q, int a, int b, int c) {
        int o = q * 12;
        float ux = p[o + b * 3] - p[o + a * 3], uy = p[o + b * 3 + 1] - p[o + a * 3 + 1], uz = p[o + b * 3 + 2] - p[o + a * 3 + 2];
        float vx = p[o + c * 3] - p[o + a * 3], vy = p[o + c * 3 + 1] - p[o + a * 3 + 1], vz = p[o + c * 3 + 2] - p[o + a * 3 + 2];
        float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return 0.5f * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    private static int fl(float v) {
        return (int) Math.floor(v);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    // float の伸びる並び
    private static final class FloatList {
        float[] data = new float[1024];
        int size;

        void add(float[] v) {
            if (size + v.length > data.length) {
                data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + v.length));
            }
            System.arraycopy(v, 0, data, size, v.length);
            size += v.length;
        }

        float[] trim() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
