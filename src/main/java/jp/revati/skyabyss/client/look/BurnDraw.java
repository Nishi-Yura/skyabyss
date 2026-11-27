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
// 氷の技 (frost、凍て潮): 同じ u の決まりで、炎の絵 = 冷気の光、熾 = 氷の芯の光、岩 = 氷、火の粉 = 光の粒。
//   冷気の光: ゆっくり揺れて絵を入れ替え、消えるときは昇りながら細って消える (粒は雪)。光の粒は瞬いて先に消える。
//   氷: 芯の光が冷めて、真ん中から外へ順に面ごとに砕けて落ちる (粒は氷のかけら)。
// 雷の技 (thunder、雷の一撃): 炎の絵 = 雷、熾 = 焼けた砂のガラス、岩 = 焦げた岩、火の粉 = 地を走る電気。
//   雷: 揺れず、絵を毎 tick 入れ替えて瞬く。消え始めにちらついて一気に消える (粒は電気の火花)。
//   地を走る電気: ばらばらに点いたり消えたりして、半ばまでに消える。ガラスは冷めて暗くなる。
//   岩とガラス: 白い煙をあげ、終わりに面ごとに地面へ沈むように崩れる (粒は黒い石)。
// 星の技 (star、瞬き・星屑): 炎の絵 = 光の柱 (瞬きは残り香、星屑は落ちた所に立つ光)、火の粉 = 星。
//   星屑の星の石 (岩) と芯 (熾) は雷の岩と同じく、煙 (きらめき) をあげ、冷めて沈むように崩れる。
//   光の柱: ゆっくり揺れて絵を入れ替え、消えるときは細って上へ伸び、半ばで消える (粒は花火のきらめき)。
//   星: 瞬きながらゆっくり昇り、ひとつずつ消える。
// 巨神斬り (cleave): 炎の絵 = 割れ目から噴く光の幕、0.5..0.625 = 振り下ろしの光の弧、熾 = 大剣の樋の光、
//   岩 = 大剣の鋼 (v < 0.75) とめくれた岩板 (v 0.75..)。
//   弧: 始め (後ろ) から順に縮んで、燃え尽きの 0.3 までに消える (粒は END_ROD)。
//   光の幕: 先 (遠く) から順に、割れ目へ沈むように縮む (粒は GLOW)。
//   岩板: 先から順に震えて、地面へ沈む (粒は深層岩)。大剣: 樋の光が冷めて、最後にまっすぐ地へ沈む (土ぼこり)。
// 岩呼び (earth): 炎の絵 = 牙の根元から湧く土煙の光 (絵の下 24 画素)、熾 = 牙の継ぎ目のひび、岩 = 砂岩の牙。
//   土煙: ゆっくりふくらむ。消えるときは横へ広がって昇り、薄く縮んで消える (粒は DUST_PLUME)。
//   牙: ひびが冷めて、上の段から面ごとに割れて落ち、根元は最後に崩れる (粒は砂岩)。
// 鯨尾薙ぎ (tide): 炎の絵 = 輪の水しぶきの壁 (絵の下 24 画素)、熾 = 背の光る斑、岩 = 鯨の背 (下の面は腹)。
//   しぶき: 小さく揺れる。消えるときは上から崩れ落ちる (粒は SPLASH)。
//   鯨: 前から時計回りに順に地へ潜る。尾びれは最後 (粒は SPLASH と泡)。
// 盾の踏み込み (rush): 炎の絵 = 盾の前の当たりの閃光、0.5..0.625 = 道に残る盾の残像、熾 = 盾の金の紋、
//   岩 = 紅の盾と青銅の縁 (v < 0.75) と押しのけた土の畝 (v 0.75..)。
//   閃光: すぐ縮んで消える (粒は CRIT)。残像: 後ろから順に縮んで消える (粒は END_ROD)。
//   畝: 後ろから順に崩れて沈む (粒は土)。盾: 紋が冷めて、面ごとに割れて外へ落ちる (粒は紅の焼き物)。
// 盾殴り (bash、盾の踏み込みのすぐ後に同じ足もとへ): 踏み込みの帯 (|x| < 0.9) の外だけに置く。炎の絵 = 左右へ走る寝かせた三日月の波
//   (u < 0.125 と 0.25..)、0.125..0.25 の下半分 = 両脇の火花の噴き、熾 = 楔の上の白熱、岩 = 左右へ開く玄武岩の楔。
//   波: 内の波から順に外へ飛びながら広がって消える (粒は POOF)。火花: 落ちながら縮む (粒は LAVA)。
//   楔: 白熱が冷めて、内から順に地へ沈む (粒は黒石)。
// 鯨骨の振り (bone、巨神斬りの刻印 2): 炎の絵 = 背骨の外を追う菫の残像の帯 (上半分、u が尾 → 頭) と頭骨の霊気の噴き (下半分)、
//   熾 = 眼窩の光、岩 = 鯨骨 (背骨は前を左後ろから右前へ半周、頭骨は右前の地面)。
//   帯: 尾の側から順に縮んで消える (粒は WITCH)。噴き: 昇って広がり消える。
//   骨: 尾から頭へ順に震えて崩れ落ちる (粒は骨のブロック)。頭骨と眼窩は最後。
// 回し斬り (spin、鯨尾薙ぎの刻印 8): 炎の絵 = 水平の斬撃の輪 (u < 0.125 = 下の輪、0.125..0.25 = 上の輪)、
//   熾 = 石柱の斬り口の光、岩 = 斬られた石柱 (高さ 0.46 より上 = ずれ落ちる上の段)。
//   輪: 燃える間も回り続ける (下は φ の小さい側へ、上は逆へ)。燃え尽きでは外へ広がって細り消える (粒は SWEEP_ATTACK)。
//   石柱: 上の段が斬り口から接線へ滑り落ち、そのあと根元が沈む (粒は方解石)。
public final class BurnDraw {
    // 粒を出す所 (模型の座標)。kind: SMOKE 炎の燃え尽き / ROCK 岩くずが地面に着いた / CRUMBLE 玉が崩れた
    public interface Sink {
        void at(float x, float y, float z, int kind);
    }

    public static final int SMOKE = 0, ROCK = 1, CRUMBLE = 2, GLINT = 3;
    private static final int FIRE = 0, OTHER = 1, EMBER = 2, STONE = 3, SPARK = 4;

    private BurnDraw() {
    }

    // 消え方の種類: 炎 (熾の玉) / 氷 / 雷
    public static final int STYLE_EMBER = 0, STYLE_FROST = 1, STYLE_THUNDER = 2, STYLE_STAR = 3, STYLE_CLEAVE = 4, STYLE_EARTH = 5, STYLE_TIDE = 6, STYLE_RUSH = 7, STYLE_BASH = 8, STYLE_BONE = 9, STYLE_SPIN = 10;

    public static int style(String kind) {
        if (kind.equals("ss_rock_call")) {
            return STYLE_EARTH;
        }
        if (kind.equals("ss_whale_sweep")) {
            return STYLE_TIDE;
        }
        if (kind.equals("ss_shield_rush")) {
            return STYLE_RUSH;
        }
        if (kind.equals("ss_shield_bash")) {
            return STYLE_BASH;
        }
        if (kind.equals("ss_bone_swing")) {
            return STYLE_BONE;
        }
        if (kind.equals("ss_spin_cut")) {
            return STYLE_SPIN;
        }
        if (kind.equals("ss_titan_cleave")) {
            return STYLE_CLEAVE;
        }
        if (kind.equals("ss_thunder_bolt")) {
            return STYLE_THUNDER;
        }
        if (kind.equals("ss_blink") || kind.equals("ss_stardust")) {
            return STYLE_STAR;
        }
        return kind.contains("frost") ? STYLE_FROST : STYLE_EMBER;
    }

    // 氷の消え方で描く技
    public static boolean frost(String kind) {
        return style(kind) == STYLE_FROST;
    }

    public static void draw(LowPolyMesh mesh, PoseStack pose, MultiBufferSource buffers, float clear, float t, float burn, float afterTicks, Sink sink) {
        draw(mesh, pose, buffers, clear, t, burn, afterTicks, sink, STYLE_EMBER);
    }

    public static void draw(LowPolyMesh mesh, PoseStack pose, MultiBufferSource buffers, float clear, float t, float burn, float afterTicks, Sink sink, int style) {
        quads(mesh, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), clear, t, burn, afterTicks, sink, style);
        if (mesh.glowMask != null) {
            quads(mesh, pose, buffers.getBuffer(RenderType.eyes(mesh.glowMask)), clear, t, burn, afterTicks, null, style);
        }
    }

    private static void quads(LowPolyMesh mesh, PoseStack pose, VertexConsumer buf, float clear, float t, float burn, float afterTicks, Sink sink, int style) {
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
                if (style != STYLE_EMBER) {
                    int col = style == STYLE_FROST ? frostQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_STAR ? starQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_CLEAVE ? cleaveQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_EARTH ? earthQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_TIDE ? tideQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_RUSH ? rushQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_BASH ? bashQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_BONE ? boneQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_SPIN ? spinQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : thunderQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink);
                    if (col == 0) {
                        continue;
                    }
                    for (int k = 0; k < 4; k++) {
                        buf.addVertex(p, xs[k], ys[k], zs[k]).setColor(col).setUv(us[k], vs[k])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
                    }
                    continue;
                }
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

    private static float hash(float x, float y, float z) {
        return (float) (((Math.sin(x * 12.9898 + y * 39.346 + z * 78.233) * 43758.5453) % 1.0 + 1.0) % 1.0);
    }

    private static int rgb(float r, float g, float b) {
        return 0xFF000000 | ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }

    // 氷の技の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int frostQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                 float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            // 冷気の光: 揺れは小さくゆっくり。消えるときは昇りながら細る
            if (!wave(xs, ys, zs, us, vs, au, t, Math.min(1f, burn * 1.7f), 0.55f, 0.6f, null)) {
                return 0;
            }
            float lb = Math.min(1f, burn * 1.7f);
            float rise = lb * lb * 0.9f;
            for (int k = 0; k < 4; k++) {
                ys[k] += rise;
            }
            if (lb > 0.3f && lb < 1f && sink != null && hash(cx, 0f, cz) < 0.5f) {
                sink.at(cx, cy + rise, cz, SMOKE);
            }
            float d = 1f - 0.25f * lb;
            return rgb(d, d, 1f);
        }
        if (part == SPARK) {
            // 光の粒: 瞬いて、消え始めにすぐ消える
            if (burn > 0.12f) {
                return 0;
            }
            float ph = hash(cx, cy, cz) * 6.283f;
            float k = 0.55f + 0.45f * Math.abs((float) Math.sin(t * 0.7f + ph));
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * k;
                ys[i] = cy + (ys[i] - cy) * k;
                zs[i] = cz + (zs[i] - cz) * k;
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            // 芯の光が冷める
            float cool = Math.min(1f, burn * 2f);
            r = 1f - 0.3f * cool;
            g = 1f - 0.18f * cool;
            b = 1f - 0.05f * cool;
        }
        if (burn > 0f && (part == EMBER || part == STONE)) {
            // 砕ける: 真ん中から外へ順に、面ごとに外へ割れて落ち、縮んで消える
            float rad = (float) Math.hypot(cx, cz);
            float start = 0.3f + 0.4f * Math.min(1f, rad / 1.5f);
            float h0 = hash(cx, cy, cz);
            float pre = (burn - start + 0.18f) / 0.18f;
            if (pre > 0f && pre < 1f) {
                // ひびが入って震える
                float sh = (float) Math.sin(t * 2.7f + h0 * 40f) * 0.012f * pre;
                for (int i = 0; i < 4; i++) {
                    xs[i] += sh;
                    zs[i] -= sh;
                }
            }
            float k = (burn - start) / 0.3f;
            if (k > 0f) {
                if (k > 0.95f) {
                    return 0;
                }
                float h = hash(cx, cy, cz);
                float ox = cx + (h - 0.5f) * 0.4f, oz = cz + (hash(cz, cx, cy) - 0.5f) * 0.4f;
                float len = (float) Math.sqrt(ox * ox + oz * oz) + 1e-4f;
                float dx = ox / len * k * (0.25f + 0.25f * h), dz = oz / len * k * (0.25f + 0.25f * h);
                float dy = k * 0.25f - k * k * (0.6f + cy * 0.5f);
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * (1f - k) + dx;
                    ys[i] = Math.max(0.02f, cy + (ys[i] - cy) * (1f - k) + dy);
                    zs[i] = cz + (zs[i] - cz) * (1f - k) + dz;
                }
                if (sink != null && h < 0.3f) {
                    sink.at(cx, cy, cz, CRUMBLE);
                }
            }
        }
        return rgb(r, g, b);
    }

    // 面を真ん中へ k だけ縮める
    private static void shrink(float[] xs, float[] ys, float[] zs, float cx, float cy, float cz, float k) {
        for (int i = 0; i < 4; i++) {
            xs[i] = cx + (xs[i] - cx) * (1f - k);
            ys[i] = cy + (ys[i] - cy) * (1f - k);
            zs[i] = cz + (zs[i] - cz) * (1f - k);
        }
    }

    // 回し斬りの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int spinQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            boolean upper = au >= 0.125f;
            float k = (burn - (upper ? 0.1f : 0f)) / 0.4f;
            if (k >= 1f) {
                return 0;
            }
            // 回す (φ を d だけ進める: x' = x cos d - z sin d, z' = z cos d + x sin d)
            float d = upper ? t * 0.28f : -t * 0.35f;
            float cd = (float) Math.cos(d), sd = (float) Math.sin(d);
            float spread = k > 0f ? 1f + 0.7f * k : 1f;
            float thin = k > 0f ? 1f - 0.6f * k : 1f;
            float ncx = (cx * cd - cz * sd) * spread, ncz = (cz * cd + cx * sd) * spread;
            for (int i = 0; i < 4; i++) {
                float x = xs[i] * cd - zs[i] * sd, z = zs[i] * cd + xs[i] * sd;
                xs[i] = ncx + (x * spread - ncx) * thin;
                zs[i] = ncz + (z * spread - ncz) * thin;
                ys[i] = cy + (ys[i] - cy) * thin + (upper ? 0.25f : -0.05f) * Math.max(0f, k);
            }
            if (k > 0.3f && k < 0.4f && sink != null) {
                sink.at(ncx, cy, ncz, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        if (part == OTHER || part == SPARK) {
            return 0xFFFFFFFF;
        }
        // 石柱: 角 (30 + 60 i 度) ごとに少しずつずらす
        float ph = (float) Math.toDegrees(Math.atan2(cx, -cz));
        int idx = Math.floorMod(Math.round((ph - 30f) / 60f), 6);
        float hp = ((idx * 5) % 6) / 6f;
        float pr = (float) Math.toRadians(30 + 60 * idx);
        float tx = (float) Math.cos(pr), tz = (float) Math.sin(pr);
        boolean top = part == EMBER || cy > 0.46f;
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.8f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.35f * cool;
            b = 1f - 0.4f * cool;
        }
        if (top) {
            float st = 0.15f + 0.25f * hp;
            float k = (burn - st) / 0.3f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                float slide = k * 0.45f, drop = k * k * 0.8f;
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * (1f - 0.4f * k) + tx * slide;
                    zs[i] = cz + (zs[i] - cz) * (1f - 0.4f * k) + tz * slide;
                    ys[i] = Math.max(0.02f, cy + (ys[i] - cy) * (1f - 0.4f * k) - drop);
                }
                if (sink != null && k > 0.7f && hash(cx, cy, cz) < 0.4f) {
                    sink.at(cx + tx * slide, 0.05f, cz + tz * slide, ROCK);
                }
            }
            return rgb(r, g, b);
        }
        float st = 0.5f + 0.25f * hp;
        float k = (burn - st) / 0.25f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                ys[i] -= k * 0.5f;
            }
            shrink(xs, ys, zs, cx, cy - k * 0.5f, cz, k * 0.3f);
            if (sink != null && hash(cx, cy, cz) < 0.3f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return 0xFFFFFFFF;
    }

    // 鯨骨の振りの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int boneQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) * 0.25f;
        if (part == FIRE) {
            if (av < 0.5f) {
                // 帯: 燃える間は上下に小さく揺れ、燃え尽きでは尾 (u が小さい) から順に縦に縮んで消える
                float st = 0.4f * (au / 0.5f);
                float k = (burn - st) / 0.22f;
                if (k >= 1f) {
                    return 0;
                }
                float lift = (float) Math.sin(t * 0.5f + au * 20f) * 0.03f;
                float sq = k > 0f ? 1f - k : 1f;
                for (int i = 0; i < 4; i++) {
                    ys[i] = cy + (ys[i] - cy) * sq + lift;
                }
                if (k > 0f && sink != null) {
                    sink.at(cx, cy, cz, SMOKE);
                }
                return 0xFFFFFFFF;
            }
            // 噴き: 燃え尽きの 0.35 までに昇って広がり消える
            float k = burn / 0.35f;
            if (k >= 1f) {
                return 0;
            }
            float g = 1f + 0.5f * k;
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * g;
                zs[i] = cz + (zs[i] - cz) * g;
                ys[i] += k * 0.5f;
            }
            if (k > 0.3f && sink != null) {
                sink.at(cx, cy + k * 0.5f, cz, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        if (part == OTHER || part == SPARK) {
            return 0xFFFFFFFF;
        }
        // 骨: 前から +x へ測った角で尾 (-110 度) から頭 (+58 度) へ順に崩れる
        float ph = (float) Math.toDegrees(Math.atan2(cx, -cz));
        float f = Math.max(0f, Math.min(1f, (ph + 110f) / 168f));
        float h = hash(cx, cy, cz);
        float st = 0.3f + 0.45f * f;
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.4f);
            r = 1f - 0.5f * cool;
            g = 1f - 0.6f * cool;
            b = 1f - 0.3f * cool;
        }
        float pre = (burn - st + 0.1f) / 0.1f;
        if (pre > 0f && pre < 1f) {
            float sh = (float) Math.sin(t * 3.3f + h * 20f) * 0.012f;
            for (int i = 0; i < 4; i++) {
                xs[i] += sh;
            }
        }
        float k = (burn - st) / 0.25f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float drop = k * k * (0.25f + cy * 0.8f);
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * (1f - 0.5f * k);
                ys[i] = Math.max(0.02f, cy + (ys[i] - cy) * (1f - 0.5f * k) - drop);
                zs[i] = cz + (zs[i] - cz) * (1f - 0.5f * k);
            }
            if (sink != null && h < 0.3f) {
                sink.at(cx, Math.max(0.05f, cy - drop), cz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 盾殴りの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int bashQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) * 0.25f;
        if (part == FIRE) {
            boolean spray = au >= 0.125f && au < 0.25f && av >= 0.5f;
            if (spray) {
                // 火花: 燃え尽きの 0.3 までに落ちながら縮む
                float k = burn / 0.3f;
                if (k >= 1f) {
                    return 0;
                }
                if (k > 0f) {
                    for (int i = 0; i < 4; i++) {
                        ys[i] -= k * k * 0.5f;
                    }
                    shrink(xs, ys, zs, cx, cy - k * k * 0.5f, cz, k);
                    if (sink != null) {
                        sink.at(cx, cy, cz, GLINT);
                    }
                }
                return 0xFFFFFFFF;
            }
            // 波: 内の波 (|cx| が小さい) から順に、外 (±x) へ飛びながら広がって消える。燃える間は小さく脈打つ
            float st = 0.25f * Math.max(0f, Math.min(1f, (Math.abs(cx) - 0.9f) / 1.0f));
            float side = Math.signum(cx);
            float k = (burn - st) / 0.3f;
            if (k >= 1f) {
                return 0;
            }
            float g = 1f + 0.04f * (float) Math.sin(t * 2.1f - Math.abs(cx) * 3f);
            float push = 0f;
            if (k > 0f) {
                g += 0.9f * k;
                push = 0.9f * k;
                if (sink != null) {
                    sink.at(cx + side * push, cy, cz, SMOKE);
                }
            }
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * g + side * push;
                ys[i] = cy + (ys[i] - cy) * g;
                zs[i] = cz + (zs[i] - cz) * g;
            }
            return k > 0.6f ? rgb(1f, 1f - (k - 0.6f), 1f - 1.5f * (k - 0.6f)) : 0xFFFFFFFF;
        }
        if (part == OTHER || part == SPARK) {
            return 0xFFFFFFFF;
        }
        // 楔と白熱: 白熱が冷めて、内 (|cx| が小さい) から順に地へ沈む
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 2f);
            r = 1f - 0.5f * cool;
            g = 1f - 0.7f * cool;
            b = 1f - 0.75f * cool;
        }
        float h = hash(cx, cy, cz);
        float st = 0.3f + 0.35f * Math.max(0f, Math.min(1f, (Math.abs(cx) - 0.85f) / 0.7f));
        float pre = (burn - st + 0.08f) / 0.08f;
        if (pre > 0f && pre < 1f) {
            float sh = (float) Math.sin(t * 3.7f + h * 20f) * 0.012f;
            for (int i = 0; i < 4; i++) {
                xs[i] += sh;
            }
        }
        float k = (burn - st) / 0.3f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                ys[i] -= k * 0.45f;
            }
            shrink(xs, ys, zs, cx, cy - k * 0.45f, cz, k * 0.35f);
            if (sink != null && h < 0.35f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 盾の踏み込みの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int rushQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) * 0.25f;
        if (part == FIRE) {
            // 閃光: 燃え尽きの 0.15 までに縮んで消える
            float k = burn / 0.15f;
            if (k >= 1f) {
                return 0;
            }
            wave(xs, ys, zs, us, vs, au, t, 0f, 1.4f, 0.2f, null);
            if (k > 0f) {
                shrink(xs, ys, zs, cx, cy, cz, k);
                if (sink != null) {
                    sink.at(cx, cy, cz, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        if (part == OTHER) {
            // 残像: 後ろ (手前、-z が小さい) から順に縮む
            float st = 0.03f + 0.2f * Math.max(0f, Math.min(1f, (-cz - 1.3f) / 2.2f));
            float k = (burn - st) / 0.15f;
            if (k >= 1f) {
                return 0;
            }
            if (k > 0f) {
                shrink(xs, ys, zs, cx, cy, cz, k);
                if (sink != null) {
                    sink.at(cx, cy, cz, GLINT);
                }
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK) {
            return 0xFFFFFFFF;
        }
        float h = hash(cx, cy, cz);
        if (part == STONE && av >= 0.75f) {
            // 畝: 後ろから順に崩れて沈む
            float st = 0.12f + 0.35f * Math.max(0f, Math.min(1f, (-cz - 0.4f) / 4f));
            float k = (burn - st) / 0.3f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                for (int i = 0; i < 4; i++) {
                    ys[i] -= k * 0.35f;
                }
                shrink(xs, ys, zs, cx, cy - k * 0.35f, cz, k * 0.4f);
                if (sink != null && h < 0.4f) {
                    sink.at(cx, 0.05f, cz, ROCK);
                }
            }
            return 0xFFFFFFFF;
        }
        // 盾: 紋が冷めて、面ごとに割れて外へ落ちる
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.6f * cool;
            b = 1f - 0.6f * cool;
        }
        float st = 0.5f + 0.2f * h;
        float pre = (burn - st + 0.1f) / 0.1f;
        if (pre > 0f && pre < 1f) {
            float sh = (float) Math.sin(t * 3.5f + h * 20f) * 0.015f;
            for (int i = 0; i < 4; i++) {
                xs[i] += sh;
            }
        }
        float k = (burn - st) / 0.3f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            // 盾の真ん中 (0, 0.85, -4.4) から外へ
            float ox = cx, oy = cy - 0.85f, oz = cz + 4.4f;
            float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz) + 1e-4f;
            float dx = ox / len * k * 0.4f, dz = oz / len * k * 0.4f - k * 0.25f;
            float dy = oy / len * k * 0.2f - k * k * (0.4f + cy * 0.6f);
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * (1f - 0.6f * k) + dx;
                ys[i] = Math.max(0.02f, cy + (ys[i] - cy) * (1f - 0.6f * k) + dy);
                zs[i] = cz + (zs[i] - cz) * (1f - 0.6f * k) + dz;
            }
            if (sink != null && h < 0.3f) {
                sink.at(cx + dx, Math.max(0.05f, cy + dy), cz + dz, CRUMBLE);
            }
        }
        return rgb(r, g, b);
    }

    // 鯨尾薙ぎの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int tideQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            // しぶき: 上から崩れ落ちる
            float b = Math.min(1f, burn * 1.5f);
            if (!wave(xs, ys, zs, us, vs, au, t, b * 0.75f, 1.0f, 0.3f, null)) {
                return 0;
            }
            if (sink != null && b > 0.15f && hash(cx, (float) Math.floor(t * 0.5f), cz) < 0.35f) {
                sink.at(cx, Math.max(0.1f, cy * (1f - b)), cz, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.5f * cool;
            g = 1f - 0.4f * cool;
            b = 1f - 0.3f * cool;
        }
        if (part == EMBER || part == STONE) {
            // 前 (-z) から時計回りに順に潜る。後ろの尾びれ (高い所) は最後
            float a = (float) Math.atan2(cx, cz);
            float frac = (float) (((a - Math.PI) / (2 * Math.PI)) % 1.0 + 1.0) % 1f;
            float st = cy > 0.45f ? 0.55f : 0.08f + 0.4f * frac;
            float k = (burn - st) / 0.35f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                float drop = k * k * (0.35f + cy * 1.3f) + k * 0.1f;
                for (int i = 0; i < 4; i++) {
                    ys[i] -= drop;
                }
                if (sink != null && hash(cx, cy, cz) < 0.25f) {
                    sink.at(cx, 0.05f, cz, CRUMBLE);
                }
            }
        }
        return rgb(r, g, b);
    }

    // 岩呼びの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int earthQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                 float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            // 土煙: 消えるときは根元へしぼんで消え、粒の土煙が昇る
            float b = Math.min(1f, burn * 1.6f);
            if (!wave(xs, ys, zs, us, vs, au, t, b * 0.74f, 0.5f, 0.35f, null)) {
                return 0;
            }
            if (b > 0f) {
                float bx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25f, bz = (zs[0] + zs[1] + zs[2] + zs[3]) * 0.25f;
                float thin = 1f - 0.6f * b;
                for (int i = 0; i < 4; i++) {
                    xs[i] = bx + (xs[i] - bx) * thin;
                    zs[i] = bz + (zs[i] - bz) * thin;
                }
                if (sink != null && b > 0.2f && hash(bx, (float) Math.floor(t * 0.5f), bz) < 0.3f) {
                    sink.at(bx, cy, bz, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.8f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.65f * cool;
            b = 1f - 0.7f * cool;
        }
        if (part == EMBER || part == STONE) {
            // 上の段から割れて落ちる。根元は最後
            float st = 0.12f + 0.5f * (1f - Math.max(0f, Math.min(1f, cy / 1.05f)));
            float h = hash(cx, cy, cz);
            float pre = (burn - st + 0.08f) / 0.08f;
            if (pre > 0f && pre < 1f) {
                float sh = (float) Math.sin(t * 3.3f + h * 25f) * 0.015f;
                for (int i = 0; i < 4; i++) {
                    xs[i] += sh;
                    zs[i] -= sh;
                }
            }
            float k = (burn - st) / 0.3f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                float len = (float) Math.sqrt(cx * cx + cz * cz) + 1e-4f;
                float dx = cx / len * k * 0.15f, dz = cz / len * k * 0.15f;
                float drop = k * k * (0.3f + cy * 1.1f);
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * (1f - 0.5f * k) + dx;
                    ys[i] = cy + (ys[i] - cy) * (1f - 0.5f * k) - drop;
                    zs[i] = cz + (zs[i] - cz) * (1f - 0.5f * k) + dz;
                }
                if (sink != null && h < 0.3f) {
                    sink.at(cx + dx, Math.max(0.05f, cy - drop), cz + dz, CRUMBLE);
                }
            }
        }
        return rgb(r, g, b);
    }

    // 巨神斬りの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int cleaveQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                  float cx, float cy, float cz, float t, float burn, Sink sink) {
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) * 0.25f;
        // 遠さ (前へ 0..1)。地割れは前 (-z) へ 1.3..3.1 ブロック
        float far = Math.max(0f, Math.min(1f, (-cz - 1.3f) / 1.8f));
        if (part == FIRE) {
            // 光の幕: 遠いものから割れ目へ沈む
            float st = 0.04f + 0.26f * (1f - far);
            float b = Math.max(0f, Math.min(1f, (burn - st) / 0.4f));
            return wave(xs, ys, zs, us, vs, au, t, b, 0.8f, 0.45f, sink) ? 0xFFFFFFFF : 0;
        }
        if (part == OTHER) {
            // 光の弧: 始め (v 0) から打ち込んだ所 (v 1) へ順に縮んで消える
            float st = (1f - av) * 0.15f;
            float k = (burn - st) / 0.15f;
            if (k >= 1f) {
                return 0;
            }
            if (k > 0f) {
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * (1f - k);
                    ys[i] = cy + (ys[i] - cy) * (1f - k) + k * 0.15f;
                    zs[i] = cz + (zs[i] - cz) * (1f - k);
                }
                if (sink != null && hash(cx, cy, cz) < 0.6f) {
                    sink.at(cx, cy, cz, GLINT);
                }
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK) {
            return 0xFFFFFFFF;
        }
        boolean slab = part == STONE && av >= 0.75f;
        if (slab) {
            // 岩板: 遠いものから、震えて地面へ沈む
            float st = 0.2f + 0.3f * (1f - far);
            float h = hash(cx, cy, cz);
            float pre = (burn - st + 0.1f) / 0.1f;
            if (pre > 0f && pre < 1f) {
                float sh = (float) Math.sin(t * 3.1f + h * 30f) * 0.02f;
                for (int i = 0; i < 4; i++) {
                    xs[i] += sh;
                }
            }
            float k = (burn - st) / 0.28f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * (1f - 0.35f * k);
                    zs[i] = cz + (zs[i] - cz) * (1f - 0.35f * k);
                    ys[i] -= k * 0.8f;
                }
                if (sink != null && h < 0.35f) {
                    sink.at(cx, 0.05f, cz, CRUMBLE);
                }
            }
            return 0xFFFFFFFF;
        }
        // 大剣 (鋼と樋の光): 光が冷めて、最後にまっすぐ地へ沈む
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.55f * cool;
            g = 1f - 0.4f * cool;
            b = 1f - 0.3f * cool;
        }
        float k = (burn - 0.55f) / 0.4f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float drop = (k * k * 0.6f + k * 0.4f) * 2.9f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= drop;
            }
            if (sink != null && hash(cx, cy, cz) < 0.05f) {
                // 刺さった所 (前へ 1.16) に土ぼこり。tick ごとに少しずらして何度か
                sink.at(((int) t % 6) * 0.09f - 0.22f, 0.02f, -1.16f, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 星の技の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int starQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            // 光の柱: ゆっくり揺れる。消えるときは細って上へ伸び、半ばで消える
            float b = Math.min(1f, burn * 2f);
            if (b >= 0.95f) {
                return 0;
            }
            wave(xs, ys, zs, us, vs, au, t, 0f, 0.7f, 0.5f, null);
            float bx = 0f, bz = 0f, by = 1e9f;
            for (int k = 0; k < 4; k++) {
                bx += xs[k] * 0.25f;
                bz += zs[k] * 0.25f;
                by = Math.min(by, ys[k]);
            }
            float thin = 1f - b, stretch = 1f + b * 0.8f;
            for (int k = 0; k < 4; k++) {
                xs[k] = bx + (xs[k] - bx) * thin;
                zs[k] = bz + (zs[k] - bz) * thin;
                ys[k] = by + (ys[k] - by) * stretch + b * 0.3f;
            }
            if (b > 0.1f && sink != null && hash(bx, (float) Math.floor(t), bz) < 0.07f) {
                sink.at(bx, by + 0.5f + b, bz, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK) {
            // 星: 瞬きながら昇り、ひとつずつ消える
            float h = hash(Math.round(cx * 4f), Math.round(cy * 4f), Math.round(cz * 4f));
            float end = 0.35f + 0.6f * h;
            if (burn > end) {
                return 0;
            }
            float k = (0.6f + 0.4f * Math.abs((float) Math.sin(t * 0.8f + h * 6.283f))) * (burn > end - 0.15f ? (end - burn) / 0.15f : 1f);
            float up = (t * 0.02f + burn * 0.6f) * (0.5f + h);
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * k;
                ys[i] = cy + (ys[i] - cy) * k + up;
                zs[i] = cz + (zs[i] - cz) * k;
            }
            return 0xFFFFFFFF;
        }
        if (part == EMBER || part == STONE) {
            // 星の石: 雷の岩と同じ冷め方・崩れ方
            return thunderQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink);
        }
        return 0xFFFFFFFF;
    }

    // 雷の技の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int thunderQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                   float cx, float cy, float cz, float t, float burn, Sink sink) {
        float tick = (float) Math.floor(t);
        if (part == FIRE) {
            // 雷: 揺れず、絵を毎 tick 入れ替える。たまに 1 tick 消えて瞬く。消え始めはちらついて 0.35 までに消える
            float on = hash(cx + tick * 0.37f, 1.3f, cz);
            float gate = burn <= 0f ? 0.12f : burn >= 0.35f ? 2f : 0.12f + burn / 0.35f;
            if (on < gate) {
                return 0;
            }
            wave(xs, ys, zs, us, vs, au, t * 3f, 0f, 0f, 0f, null);
            if (burn > 0f && sink != null && hash(cx, tick, cz) < 0.25f) {
                sink.at(cx, cy, cz, ROCK);
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK) {
            // 地を走る電気: ばらばらに点いたり消えたり。半ばまでに消える
            float on = hash(cx + tick * 0.53f, cy, cz + tick * 0.11f);
            if (on > 0.75f * (1f - burn / 0.6f)) {
                return 0;
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            // 焼けた砂のガラス: 冷めて暗くなる
            float cool = Math.min(1f, burn * 1.3f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.5f * cool;
            b = 1f - 0.25f * cool;
        }
        if (part == EMBER || part == STONE) {
            float h = hash(cx, cy, cz);
            if (burn > 0.12f && burn < 0.65f && sink != null && hash(cx, tick, cz) < 0.04f) {
                sink.at(cx, cy, cz, SMOKE);
            }
            // 崩れる: 塊ごとにずれて、面が縮みながら地面へ沈む
            float start = 0.45f + 0.2f * hash(Math.round(cx * 2f), 0f, Math.round(cz * 2f));
            float k = (burn - start) / 0.3f;
            if (k > 0f) {
                if (k > 0.95f) {
                    return 0;
                }
                float len = (float) Math.sqrt(cx * cx + cz * cz) + 1e-4f;
                float dx = cx / len * k * 0.12f, dz = cz / len * k * 0.12f;
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * (1f - k) + dx;
                    ys[i] = cy + (ys[i] - cy) * (1f - k) - k * (0.15f + cy * 0.6f);
                    zs[i] = cz + (zs[i] - cz) * (1f - k) + dz;
                }
                if (sink != null && h < 0.2f) {
                    sink.at(cx, Math.max(0.05f, cy - k * 0.2f), cz, CRUMBLE);
                }
            }
        }
        return rgb(r, g, b);
    }

    // 炎の板 1 枚: 揺れ・伸び縮み・絵の入れ替え、燃え尽きの縮み。描かないときは false
    private static boolean fire(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au, float t, float burn, Sink sink) {
        return wave(xs, ys, zs, us, vs, au, t, burn, 1f, 1f, sink);
    }

    // 2D の板の揺れ。speed = 揺れの速さ、amp = 揺れの大きさ (炎は 1)
    private static boolean wave(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au, float t, float burn, float speed, float amp, Sink sink) {
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
        float sway = ((float) Math.sin(t * 0.35f * speed + ph) * 0.10f + (float) Math.sin(t * 0.83f * speed + ph * 2.1f) * 0.04f) * amp;
        float lift = (float) Math.sin(t * 0.6f * speed + ph * 1.7f) * 0.06f * amp;
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
