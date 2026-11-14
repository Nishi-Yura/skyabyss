package jp.revati.skyabyss.client.lowpoly;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

// 光る層の絵 (2026-11-11 の声「画像では光っているが、マイクラ内で見たら光っていない」「画像にはあるのにマイクラ内では透明」)。
// GeckoLib の AutoGlowingTexture は、発光マスクの光る画素を元の絵から消して (透明にして) 別の絵へ移す。光る層が描かれない時
// (番人の闇・眠る巨神の光が消える時・quad を飛ばした時など) は、その所が透明になり光りもしなかった。
// ここでは元の絵は触らず、光る画素だけに元の色を置いた絵を別に作る (装備の _glow.png と同じやり方)。元の絵はいつも全部の色が出て、光る層は上に重ねて光らせる。
// 作った絵は skyabyss:lowpoly_glow/<元の絵の道> に置く。資源を読み直すと捨てる (LowPolyMeshes)
public final class LowPolyGlowTexture {
    private static final Map<ResourceLocation, ResourceLocation> MADE = new HashMap<>();

    private LowPolyGlowTexture() {
    }

    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation id : MADE.values()) {
            if (id != null) {
                mc.getTextureManager().release(id);
            }
        }
        MADE.clear();
    }

    // base の光る画素だけの絵。マスクが無い・読めない・光る画素が無いときは null
    public static ResourceLocation of(ResourceLocation base, ResourceLocation mask) {
        if (MADE.containsKey(base)) {
            return MADE.get(base);
        }
        ResourceLocation out = null;
        try (NativeImage b = read(base); NativeImage m = read(mask)) {
            if (b != null && m != null && b.getWidth() == m.getWidth() && b.getHeight() == m.getHeight()) {
                NativeImage g = new NativeImage(b.getWidth(), b.getHeight(), true);
                int lit = 0;
                for (int y = 0; y < b.getHeight(); y++) {
                    for (int x = 0; x < b.getWidth(); x++) {
                        int mv = m.getPixelRGBA(x, y);
                        int a = (mv >>> 24) & 255;
                        if (mv != 0 && a > 0) {
                            int bv = b.getPixelRGBA(x, y);
                            g.setPixelRGBA(x, y, (a << 24) | (bv & 0x00FFFFFF));
                            lit++;
                        } else {
                            g.setPixelRGBA(x, y, 0);
                        }
                    }
                }
                if (lit > 0) {
                    out = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lowpoly_glow/" + base.getPath());
                    Minecraft.getInstance().getTextureManager().register(out, new DynamicTexture(g));
                } else {
                    g.close();
                }
            } else if (b != null && m != null) {
                SkyAbyss.LOGGER.warn("[lowpoly] glowmask size {}x{} does not match {} {}x{}", m.getWidth(), m.getHeight(), base, b.getWidth(), b.getHeight());
            }
        } catch (Exception e) {
            SkyAbyss.LOGGER.warn("[lowpoly] could not build the glow texture for {}", base, e);
        }
        MADE.put(base, out);
        return out;
    }

    private static NativeImage read(ResourceLocation loc) throws java.io.IOException {
        Resource r = Minecraft.getInstance().getResourceManager().getResource(loc).orElse(null);
        if (r == null) {
            return null;
        }
        try (InputStream in = r.open()) {
            return NativeImage.read(in);
        }
    }
}
