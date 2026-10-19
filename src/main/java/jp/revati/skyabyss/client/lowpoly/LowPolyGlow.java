package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

// 発光の重ね描き (AutoGlowingGeoLayer) は発光マスクの光る画素の所だけを描くので、光る画素がひとつも掛からない quad は流しても何も出ない。
// 骨ごと・マスクごとに、quad の uv の箱 (まわり 1 画素ぶん広げる) に光る画素があるかを一度だけ求め、重ね描きではその quad を飛ばす。
// 出る絵は同じで、頂点の数だけが減る。マスクが読めない・箱が 0..1 の外に出るなどのときは飛ばさない (null = 全部流す)。
final class LowPolyGlow {
    // 光る画素の数の累積表 (width+1) x (height+1)
    private record Mask(int w, int h, int[] sum) {
        boolean any(int x0, int y0, int x1, int y1) {
            x0 = Math.max(0, x0);
            y0 = Math.max(0, y0);
            x1 = Math.min(w, x1);
            y1 = Math.min(h, y1);
            if (x1 <= x0 || y1 <= y0) {
                return false;
            }
            int s = w + 1;
            return sum[y1 * s + x1] - sum[y0 * s + x1] - sum[y1 * s + x0] + sum[y0 * s + x0] > 0;
        }
    }

    private static final Mask NONE = new Mask(0, 0, new int[0]);
    private static final Map<ResourceLocation, Mask> MASKS = new HashMap<>();

    private LowPolyGlow() {
    }

    static void clear() {
        MASKS.clear();
    }

    // 骨の quad ごとの「光る画素が掛かるか」。マスクが使えなければ null
    static boolean[] quads(LowPolyMesh.Bone bone, ResourceLocation maskTexture) {
        if (bone.glowQuadsFor == maskTexture) {
            return bone.glowQuads;
        }
        Mask mask = MASKS.computeIfAbsent(maskTexture, LowPolyGlow::load);
        boolean[] out = null;
        if (mask != NONE) {
            out = new boolean[bone.quadCount];
            for (int q = 0; q < out.length; q++) {
                float minU = Float.MAX_VALUE;
                float minV = Float.MAX_VALUE;
                float maxU = -Float.MAX_VALUE;
                float maxV = -Float.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    float u = bone.uv[q * 8 + k * 2];
                    float v = bone.uv[q * 8 + k * 2 + 1];
                    minU = Math.min(minU, u);
                    maxU = Math.max(maxU, u);
                    minV = Math.min(minV, v);
                    maxV = Math.max(maxV, v);
                }
                if (minU < 0f || minV < 0f || maxU > 1f || maxV > 1f) {
                    out[q] = true;
                    continue;
                }
                out[q] = mask.any((int) Math.floor(minU * mask.w) - 1, (int) Math.floor(minV * mask.h) - 1,
                        (int) Math.ceil(maxU * mask.w) + 1, (int) Math.ceil(maxV * mask.h) + 1);
            }
        }
        bone.glowQuads = out;
        bone.glowQuadsFor = maskTexture;
        return out;
    }

    private static Mask load(ResourceLocation loc) {
        try {
            Resource r = Minecraft.getInstance().getResourceManager().getResource(loc).orElse(null);
            if (r == null) {
                return NONE;
            }
            try (InputStream in = r.open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth();
                int h = img.getHeight();
                int[] sum = new int[(w + 1) * (h + 1)];
                for (int y = 0; y < h; y++) {
                    int row = 0;
                    for (int x = 0; x < w; x++) {
                        // 光る = マスクの画素が 0 でない (GeckoLib の GeoGlowingTextureMeta.fromExistingImage と同じ判定。αが 0 でも数える側に倒す)
                        if (img.getPixelRGBA(x, y) != 0) {
                            row++;
                        }
                        sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
                    }
                }
                return new Mask(w, h, sum);
            }
        } catch (Exception e) {
            SkyAbyss.LOGGER.warn("[lowpoly] cannot read glow mask {}: {}", loc, e.toString());
            return NONE;
        }
    }
}
