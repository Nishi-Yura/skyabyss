package jp.revati.skyabyss.client.look;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

// 技の試作 d の形 (2026-11-27、参照の動画に寄せる): 光るもの (炎) は 2D の板、固いもの (玉・岩くず) は 3D の箱。陰なし・明るさ最大。
// 絵の決まり (tools/models/skill_rough/d/*.bbmodel の 1 枚の絵の u で部位を見分ける):
//   u < 0.5 = 炎の絵 4 種 (幅 1/8 ずつ。v 0 が先、v 1 が根元)、0.625..0.75 = 熾 (玉の割れ目)、0.75..0.875 = 岩 (玉と岩くず)、0.875.. = 火の粉。
// t = tick (揺れ)、burn = 燃え尽きの進み 0..1 (AttackProps.afterburn の間)。
//   炎: 揺れて 3 tick ごとに絵を入れ替える。燃え尽きでは先から縮んで赤黒くなり、途中で煙と小さな火を出して消える。
//   岩くず: 落ちて、地面で砕ける。玉: 割れ目が冷えて暗くなり、終わりに面ごとに割れて崩れる。火の粉: すぐ消える。
public final class BurnDraw {
    // 粒を出す所 (模型の座標)。kind: SMOKE 炎の燃え尽き / ROCK 岩くずが地面に着いた / CRUMBLE 玉が崩れた
    public interface Sink {
        void at(float x, float y, float z, int kind);
    }

    public static final int SMOKE = 0, ROCK = 1, CRUMBLE = 2;
    private static final int FIRE = 0, OTHER = 1, EMBER = 2, STONE = 3, SPARK = 4;

    private BurnDraw() {
    }

    public static void draw(LowPolyMesh mesh, PoseStack pose, MultiBufferSource buffers, float clear, float t, float burn, float afterTicks, Sink sink) {
        quads(mesh, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), clear, t, burn, afterTicks, sink);
        if (mesh.glowMask != null) {
            quads(mesh, pose, buffers.getBuffer(RenderType.eyes(mesh.glowMask)), clear, t, burn, afterTicks, null);
        }
    }

    private static void quads(LowPolyMesh mesh, PoseStack pose, VertexConsumer buf, float clear, float t, float burn, float afterTicks, Sink sink) {
        PoseStack.Pose p = pose.last();
        org.joml.Vector3f c = new org.joml.Vector3f();
        float[] xs = new float[4], ys = new float[4], zs = new float[4], us = new float[4], vs = new float[4];
        for (LowPolyMesh.Bone b : mesh.bones.values()) {
            for (int q = 0; q < b.quadCount; q++) {
                float cx = 0, cy = 0, cz = 0, au = 0;
                for (int k = 0; k < 4; k++) {
                    int o = q * 12 + k * 3;
                    xs[k] = b.pos[o];
                    ys[k] = b.pos[o + 1];
                    zs[k] = b.pos[o + 2];
                    us[k] = b.uv[q * 8 + k * 2];
                    vs[k] = b.uv[q * 8 + k * 2 + 1];
                    cx += xs[k] / 4f;
                    cy += ys[k] / 4f;
                    cz += zs[k] / 4f;
                    au += us[k] / 4f;
                }
                if (clear > 0f) {
                    c.set(cx, cy, cz);
                    p.pose().transformPosition(c);
                    if (c.lengthSquared() < clear * clear) {
                        continue;
                    }
                }
                int part = au < 0.5f ? FIRE : au < 0.625f ? OTHER : au < 0.75f ? EMBER : au < 0.875f ? STONE : SPARK;
                boolean orb = part == EMBER || (part == STONE && Math.hypot(cx, cz) < 0.6 && cy < 0.9f);
                float r = 1f, g = 1f, bl = 1f;
                if (part == FIRE) {
                    if (!fire(xs, ys, zs, us, vs, au, t, burn, sink)) {
                        continue;
                    }
                    r = 1f - 0.3f * burn;
                    g = 1f - 0.65f * burn;
                    bl = 1f - 0.8f * burn;
                } else if (part == SPARK) {
                    if (burn > 0.15f) {
                        continue;
                    }
                } else if (orb) {
                    // 割れ目が冷える: 熾は赤黒く、岩は少し暗く
                    float cool = Math.min(1f, burn * 1.6f);
                    if (part == EMBER) {
                        r = 1f - 0.45f * cool;
                        g = 1f - 0.85f * cool;
                        bl = 1f - 0.9f * cool;
                    } else {
                        r = g = bl = 1f - 0.35f * cool;
                    }
                    if (burn > 0.55f) {
                        // 崩れる: 面ごとに外へ割れて落ち、縮んで消える
                        float k = (burn - 0.55f) / 0.45f;
                        if (k > 0.95f) {
                            continue;
                        }
                        float ox = cx, oy = cy - 0.3f, oz = cz;
                        float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz) + 1e-4f;
                        float dx = ox / len * k * 0.45f, dy = oy / len * k * 0.2f - k * k * 0.5f, dz = oz / len * k * 0.45f;
                        for (int i = 0; i < 4; i++) {
                            xs[i] = cx + (xs[i] - cx) * (1f - k) + dx;
                            ys[i] = Math.max(0f, cy + (ys[i] - cy) * (1f - k) + dy);
                            zs[i] = cz + (zs[i] - cz) * (1f - k) + dz;
                        }
                        if (sink != null) {
                            sink.at(0f, 0.3f, 0f, CRUMBLE);
                        }
                    }
                } else if (part == STONE && burn > 0f) {
                    // 岩くずが落ちる (重さ一定)。地面に着いたら消えて砕ける粒
                    float tt = burn * afterTicks;
                    float dy = -0.5f * 0.11f * tt * tt;
                    if (cy + dy < 0.06f) {
                        if (sink != null) {
                            sink.at(cx, 0f, cz, ROCK);
                        }
                        continue;
                    }
                    for (int i = 0; i < 4; i++) {
                        ys[i] += dy;
                    }
                }
                int col = 0xFF000000 | ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (bl * 255f);
                for (int k = 0; k < 4; k++) {
                    buf.addVertex(p, xs[k], ys[k], zs[k]).setColor(col).setUv(us[k], vs[k])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
                }
            }
        }
    }

    // 炎の板 1 枚: 揺れ・伸び縮み・絵の入れ替え、燃え尽きの縮み。描かないときは false
    private static boolean fire(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au, float t, float burn, Sink sink) {
        // 十字の 2 枚は同じ根元 (v = 1 の 2 点の真ん中) を持つので、同じ揺れの位相になる
        float bx = 0, bz = 0, by = 0;
        int n = 0;
        for (int k = 0; k < 4; k++) {
            if (vs[k] > 0.5f) {
                bx += xs[k];
                bz += zs[k];
                by += ys[k];
                n++;
            }
        }
        bx /= Math.max(1, n);
        bz /= Math.max(1, n);
        by /= Math.max(1, n);
        float height = 1f - burn * 1.35f;
        if (height < 0.08f) {
            return false;
        }
        if (burn > 0.3f && sink != null) {
            sink.at(bx, by, bz, SMOKE);
        }
        float ph = (float) (((Math.sin(bx * 12.9898 + bz * 78.233) * 43758.5453) % 1.0 + 1.0) % 1.0) * 6.283f;
        float sway = (float) Math.sin(t * 0.35f + ph) * 0.10f + (float) Math.sin(t * 0.83f + ph * 2.1f) * 0.04f;
        float lift = (float) Math.sin(t * 0.6f + ph * 1.7f) * 0.06f;
        int frame = (int) Math.floor(t / 3f + ph) & 7;
        int idx = (int) Math.floor(au * 8f);
        float col0 = idx / 8f;
        float shift = ((idx + frame) & 3) / 8f - col0;
        for (int k = 0; k < 4; k++) {
            float up = ys[k] - by;
            float tip = (1f - vs[k]) * (1f - vs[k]);
            xs[k] += sway * up * tip;
            zs[k] += sway * 0.6f * up * tip;
            ys[k] = by + up * height * (1f + lift * (1f - vs[k]));
            if (frame >= 4) {
                us[k] = col0 + (col0 + 0.125f - us[k]);
            }
            us[k] += shift;
        }
        return true;
    }
}
