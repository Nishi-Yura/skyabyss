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
// 踏み込み (lunge、暗殺者): 炎の絵 = 突きの筋 (v < 0.5) と衝撃の輪 (v >= 0.5)、OTHER = 苦無の柄の巻き、
//   熾 = 苦無の刃の縁 (牡丹)、岩 = 苦無の黒鋼。
//   筋は尾 (原点の側) から細って消え、輪は広がって消え (粒は牡丹の塵)、苦無は刃の向きへ地へ沈む (粒は黒い石)。
// 後ろ跳び (backstep、暗殺者): 炎の絵 = 煙玉の煙 (v < 0.5) と跳んだ弧の点線 (v >= 0.5)、OTHER = 素焼きの殻の内の黒釉、
//   熾 = 撒き菱の先、岩 = 素焼きの殻 (v < 0.5) と撒き菱の黒鉄 (v >= 0.5)。
//   煙は燃える間もふくらみ、燃え尽きで広がって昇り消える (粒は大きな煙)。点線は跳んだ順に消える。
//   殻と撒き菱は角ごとにずれて地へ沈む (粒は素焼きのかけらと煙)。
// 霧隠れ (mist_veil、暗殺者): 炎の絵 = 霧の幕 (v < 0.5) と昇る筋 (v >= 0.5)、OTHER = 縄と札、岩 = 変わり身の丸太。
//   霧は燃える間ゆらぎ、燃え尽きで外へ広がりながら地へ沈んで消える (粒は雲)。筋は昇り続ける。
//   丸太は燃え尽きの初めに煙とともにしぼんで消える (粒は雲と木くず)。
// 双牙 (twin_fang、暗殺者): 炎の絵 = 顎の閉じる閃き (u < 0.25) と毒のしぶき (0.25..0.5)、熾 = 牙の黄の帯、岩 = 黒い甲殻の牙。
//   閃きは昇りながら細って消え、しぶきは外へ広がって地へ落ちる (粒は黄の塵)。牙は地へ引っ込む (粒は黒い石)。
// 風裂き (wind_rend_fly、暗殺者、飛ぶ形): 炎の絵 = 風車の刃 (進む向きを軸に回る)、OTHER と岩 = 木の葉、熾 = 葉脈。
//   飛ぶ間も着いてからも z 軸まわりに回る。燃え尽きで刃は広がって消え (粒は萌黄の塵)、葉は外へ散って落ちる (粒は葉)。
// 雲穿ち (cloud_pierce_fly、弓使い、飛ぶ形): 炎の絵 = 破れた雲の輪 (u < 0.25)・飛行機雲 (v < 0.19)・矢じりの前の衝撃波、
//   OTHER = 矢羽、熾 = 矢じり、岩 = 骨の矢柄。矢は進む向きを軸に回り、雲の輪は逆へゆっくり回る。
//   燃え尽きで衝撃波はすぐ広がって消え、飛行機雲は尾から縮み、雲の輪は広がって薄れ (粒は鴇色の塵と雲)、矢は面ごとに割れて落ちる (粒は骨)。
// 骨の罠 (bone_trap、弓使い): 炎の絵 = 踏み板から立つ鬼火 (u < 0.25) と顎の内の光の帯、OTHER = 牙、熾 = 牙の先と踏み板の印、
//   岩 = 骨の顎 (v < 0.5) と動かない骨 (蝶番・ばね・踏み板、v >= 0.5)。鬼火は燃える間ゆらぐ。
//   燃え尽きの初めに顎が x 軸まわりに跳ね上がって噛み合い (光の帯は床へ沈む。粒は臙脂の塵)、鬼火は伸びて細って消え、
//   罠は地へ沈む (粒は骨)。
// 空の雨 (sky_rain、弓使い): 炎の絵 = 降りしきる矢の光の筋 (u < 0.25、燃える間は上から下へ流れ続ける) と刺さった所の衝撃の星 (脈打つ)、
//   OTHER = 矢羽、熾 = 月白の帯、岩 = 鉛色の矢柄。燃え尽きで筋は流れ落ちて消え、星は広がって消え、刺さった矢は地へ沈む (粒は銀の塵と砂利)。
// 空の雨の降る矢 (sky_rain_fall、FALL は X 軸 90 度で描くので模型の +z = 下): 光の尾は着くと矢じりへ縮み、矢は刺さったまま沈む (粒は銀の塵と砂利)。
// 雪縛り (snare_shot、弓使い): 炎の絵 = 床の雪の渦 (u < 0.25, v < 0.5、ゆっくり回る)・締まる閃き (u < 0.25, v >= 0.5)・雪の舞、
//   OTHER = 縄、岩 = 墨の杭。燃え尽きで閃きは横へ伸びて消え、縄の輪は締まってから切れて落ち (粒は雪)、
//   雪の舞は昇って消え、渦は広がって消え、杭は地へ沈む (粒は黒い石)。
// 雪縛りの矢 (snare_shot_fly、飛ぶ形): 矢柄に巻いた縄が着くとほどけて外へ散り、矢は雪になって縮み、雪の尾は矢じりへ縮む。
// 守りの唄 (ward_song、呪術師): 炎の絵 = 寝かせた唄の輪 (u < 0.25) と縦の唄の幕、熾 = 真珠、岩 = 真珠貝の扇。
//   輪は燃える間ゆっくり回って上下し、幕はゆらぐ。燃え尽きで輪は広がって昇り消え (粒は水浅葱の塵と音符)、
//   幕は床へ沈み、扇は内へ閉じてから地へ沈む (粒は方解石)。
// 引き潮 (ebb_tide、呪術師): 炎の絵 = 引き波の波頭 (u < 0.25、縦の板の弧 3 段) と床を引く泡のレース、OTHER = 海松色の藻、岩 = 藤壺の岩。
//   波頭は燃える間上下に揺れ、藻は先がなびく。燃え尽きで波頭は放った人へ駆け戻りながら低くなって消え (粒は泡としぶき)、
//   レースも引かれて消え、藻は放った人へ引きずられてから岩ごと地へ沈む (粒は乾いた昆布)。
// 巡りの唄 (cycle_song、呪術師、守りの唄の刻印 8): 炎の絵 = 螺旋の唄の帯 (u < 0.25, v < 0.5)・床の花の輪 (u < 0.25, v >= 0.5)・藤の房、
//   熾 = 鈴の舌、岩 = 暗い銀の鈴の柱。帯はゆっくり巡り、花の輪は逆へ回り、房と鈴は揺れる。
//   燃え尽きで鈴が大きく鳴り (揺れ)、帯は速く巡りながら昇って広がり消え、房は花びらになって落ち (粒は桜の花びらと紅藤の塵)、
//   輪は広がって消え、柱は地へ沈む (粒は鉄)。
// 呪いの印 (curse_mark、呪術師、敵の足もと): 炎の絵 = 頭上の朱印 (u < 0.25。v < 0.5 が大きい輪、v >= 0.5 が内の印)・字の糸 (u < 0.3125)・札、
//   OTHER = 朱漆の帯、岩 = 黒漆の杭。印は燃える間ゆっくり回って脈打ち、杭は震える。
//   燃え尽きで印は敵の頭へ押し下がりながら縮んで消え (粒は朱の塵)、糸は杭の頭へ縮み、札は下から燃え上がって消え (粒は灰と煙)、
//   杭は地へ沈む (粒は黒い石)。
// 灯の守り札 (lamp_charm、呪術師): 炎の絵 = 床の灯だまり (u < 0.25, v < 0.5)・灯の差す筋 (u < 0.25, v >= 0.5)・行灯の紙 (u < 0.375)・炎、
//   OTHER = 銅の縁、岩 = 暗い銅の灯籠。炎はゆらぎ、行灯は鉤から小さく揺れ、筋は脈打ち、灯だまりはゆっくり回る。
//   燃え尽きで炎は縮んで消え (粒は煙)、筋は細って消え、灯だまりは縮み、紙は暗くなり、行灯は床へ落ちてから灯籠ごと地へ沈む (粒は銅)。
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
    public static final int STYLE_EMBER = 0, STYLE_FROST = 1, STYLE_THUNDER = 2, STYLE_STAR = 3, STYLE_CLEAVE = 4, STYLE_EARTH = 5, STYLE_TIDE = 6, STYLE_RUSH = 7, STYLE_BASH = 8, STYLE_BONE = 9, STYLE_SPIN = 10, STYLE_LUNGE = 11, STYLE_BACK = 12, STYLE_MIST = 13, STYLE_FANG = 14, STYLE_WIND = 15, STYLE_PIERCE = 16, STYLE_TRAP = 17, STYLE_RAIN = 18, STYLE_RAINFALL = 19, STYLE_SNARE = 20, STYLE_SNAREFLY = 21, STYLE_WARD = 22, STYLE_EBB = 23, STYLE_CYCLE = 24, STYLE_CURSE = 25, STYLE_LAMP = 26;

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
        if (kind.equals("ss_lunge")) {
            return STYLE_LUNGE;
        }
        if (kind.equals("ss_backstep")) {
            return STYLE_BACK;
        }
        if (kind.equals("ss_mist_veil")) {
            return STYLE_MIST;
        }
        if (kind.equals("ss_ward_song")) {
            return STYLE_WARD;
        }
        if (kind.equals("ss_ebb_tide")) {
            return STYLE_EBB;
        }
        if (kind.equals("ss_cycle_song")) {
            return STYLE_CYCLE;
        }
        if (kind.equals("ss_curse_mark")) {
            return STYLE_CURSE;
        }
        if (kind.equals("ss_lamp_charm")) {
            return STYLE_LAMP;
        }
        if (kind.equals("ss_snare_shot")) {
            return STYLE_SNARE;
        }
        if (kind.equals("ss_snare_shot_fly")) {
            return STYLE_SNAREFLY;
        }
        if (kind.equals("ss_sky_rain")) {
            return STYLE_RAIN;
        }
        if (kind.equals("ss_sky_rain_fall")) {
            return STYLE_RAINFALL;
        }
        if (kind.equals("ss_bone_trap")) {
            return STYLE_TRAP;
        }
        if (kind.equals("ss_twin_fang")) {
            return STYLE_FANG;
        }
        if (kind.equals("ss_wind_rend_fly")) {
            return STYLE_WIND;
        }
        if (kind.equals("ss_cloud_pierce_fly")) {
            return STYLE_PIERCE;
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
                            : style == STYLE_LUNGE ? lungeQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_BACK ? backQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_MIST ? mistQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_FANG ? fangQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_WIND ? windQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_PIERCE ? pierceQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_TRAP ? trapQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_WARD ? wardQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_EBB ? ebbQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_CYCLE ? cycleQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_CURSE ? curseQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_LAMP ? lampQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_RAIN ? rainQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_SNARE ? snareQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_SNAREFLY ? snareFlyQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
                            : style == STYLE_RAINFALL ? rainFallQuad(part, xs, ys, zs, us, vs, au, cx, cy, cz, t, burn, sink)
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

    // z 軸 (y = 0.25 の芯) まわりに d ラジアン回す
    private static void rollZ(float[] xs, float[] ys, float d) {
        float cs = (float) Math.cos(d), sn = (float) Math.sin(d);
        for (int i = 0; i < 4; i++) {
            float x = xs[i], y = ys[i] - 0.25f;
            xs[i] = x * cs - y * sn;
            ys[i] = 0.25f + x * sn + y * cs;
        }
    }

    // 雪縛り (地の縛り) の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int snareQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                 float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK || part == EMBER) {
            return 0;
        }
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE && au < 0.25f && av < 0.5f) {
            // 雪の渦: ゆっくり回り、燃え尽きで広がって消える
            float k = burn / 0.6f;
            if (k >= 1f) {
                return 0;
            }
            float d = t * 0.08f;
            float cs = (float) Math.cos(d), sn = (float) Math.sin(d), sc = 1f + 0.8f * k;
            for (int i = 0; i < 4; i++) {
                float x = xs[i], z = zs[i];
                xs[i] = (x * cs - z * sn) * sc;
                zs[i] = (x * sn + z * cs) * sc;
            }
            return 0xFFFFFFFF;
        }
        if (part == FIRE && au < 0.25f) {
            // 締まる閃き: 燃え尽きで横へ伸びながら細って消える
            float k = burn / 0.2f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                xs[i] *= 1f + 1.2f * k;
                zs[i] *= 1f + 1.2f * k;
                ys[i] = cy + (ys[i] - cy) * (1f - 0.8f * k);
            }
            return 0xFFFFFFFF;
        }
        if (part == FIRE) {
            // 雪の舞: ゆるく揺れ、燃え尽きで昇って消える
            float k = burn / 0.7f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                ys[i] += 0.03f * (float) Math.sin(t * 0.2f) + 0.8f * k;
                xs[i] *= 1f + 0.3f * k;
                zs[i] *= 1f + 0.3f * k;
            }
            return 0xFFFFFFFF;
        }
        if (part == OTHER) {
            // 縄: 締まってから切れて落ちる
            float k1 = Math.min(1f, burn / 0.2f);
            float sq = 1f - 0.3f * k1;
            for (int i = 0; i < 4; i++) {
                xs[i] *= sq;
                zs[i] *= sq;
            }
            float k = (burn - 0.25f) / 0.6f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                float h = hash(cx, cy, cz);
                float out = 1f + 0.6f * k;
                float dy = -k * k * 1.3f;
                for (int i = 0; i < 4; i++) {
                    xs[i] *= out;
                    zs[i] *= out;
                    ys[i] = Math.max(0.02f, ys[i] + dy * (0.7f + 0.6f * h));
                }
                if (k < 0.3f && sink != null && h < 0.3f) {
                    sink.at(cx * sq, cy, cz * sq, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        // 杭: 地へ沈む
        float k = (burn - 0.4f) / 0.6f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 0.8f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.3f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return 0xFFFFFFFF;
    }

    // 雪縛りの矢の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int snareFlyQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                    float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK) {
            return 0;
        }
        if (part == FIRE) {
            // 雪の尾: 矢じり (-z) へ縮む
            float k = burn / 0.35f;
            if (k >= 1f) {
                return 0;
            }
            float front = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
            for (int i = 0; i < 4; i++) {
                zs[i] = front + (zs[i] - front) * (1f - k);
            }
            return 0xFFFFFFFF;
        }
        if (part == OTHER) {
            // 縄: 矢柄の芯から外へほどけて散る
            float k = burn / 0.8f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                xs[i] *= 1f + 2.2f * k;
                ys[i] = 0.25f + (ys[i] - 0.25f) * (1f + 2.2f * k) - 0.4f * k * k;
            }
            return 0xFFFFFFFF;
        }
        // 矢: 雪になって縮む
        float k = burn / 0.55f;
        if (k >= 1f) {
            return 0;
        }
        if (k > 0f) {
            shrink(xs, ys, zs, cx, cy, cz, k);
            if (k > 0.5f && sink != null && hash(cx, cy, cz) < 0.3f) {
                sink.at(cx, cy, cz, SMOKE);
            }
        }
        return 0xFFFFFFFF;
    }

    // 空の雨 (地の形) の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int rainQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK) {
            return 0;
        }
        if (part == FIRE && au < 0.25f) {
            // 光の筋: 上から下へ流れ、上へ戻る (板ごとに位相をずらす)。燃え尽きで流れ落ちて消える
            float k = burn / 0.4f;
            if (k >= 1f) {
                return 0;
            }
            float ph = t * 0.08f + hash(cx, 0f, cz) * 3f;
            float off = (ph - (float) Math.floor(ph)) * 0.45f + k * 1.2f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= off;
            }
            return 0xFFFFFFFF;
        }
        if (part == FIRE) {
            // 衝撃の星: 脈打ち、燃え尽きで広がって消える
            float k = burn / 0.5f;
            if (k >= 1f) {
                return 0;
            }
            float sc = (1f + 0.12f * (float) Math.sin(t * 0.5f + hash(cx, 0f, cz) * 6f)) * (1f + 0.9f * k);
            for (int i = 0; i < 4; i++) {
                xs[i] = cx + (xs[i] - cx) * sc;
                zs[i] = cz + (zs[i] - cz) * sc;
            }
            if (k > 0.5f && sink != null) {
                sink.at(cx, 0.05f, cz, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.4f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.4f * cool;
            b = 1f - 0.3f * cool;
        }
        // 刺さった矢: 地へ沈む
        float k = (burn - 0.3f) / 0.7f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 0.7f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.2f) {
                sink.at(cx, 0.03f, cz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 空の雨の降る矢の面 1 枚 (模型の +z = 下)。形を書き換えて色を返す。描かないときは 0
    private static int rainFallQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                    float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK) {
            return 0;
        }
        if (part == FIRE) {
            // 光の尾: 着くと矢じり (+z) へ縮んで消える
            float k = burn / 0.3f;
            if (k >= 1f) {
                return 0;
            }
            if (k > 0f) {
                float head = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
                for (int i = 0; i < 4; i++) {
                    zs[i] = head + (zs[i] - head) * (1f - k);
                }
                if (sink != null) {
                    sink.at(0f, 0.05f, 0f, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.4f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.4f * cool;
            b = 1f - 0.3f * cool;
        }
        // 矢: 刺さったまま地へ沈む
        float k = (burn - 0.35f) / 0.65f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 1.4f;
            for (int i = 0; i < 4; i++) {
                zs[i] += d;
            }
            if (k > 0.3f && sink != null) {
                sink.at(0f, 0.03f, 0f, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 灯の守り札の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int lampQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK || part == EMBER) {
            return 0;
        }
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE && au < 0.25f) {
            if (av < 0.5f) {
                // 灯だまり: ゆっくり回り、燃え尽きで縮む
                float k = burn / 0.45f;
                if (k >= 1f) {
                    return 0;
                }
                float a = t * 0.02f, sc = 1f - 0.8f * k;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                for (int i = 0; i < 4; i++) {
                    float x = xs[i], z = zs[i];
                    xs[i] = (x * cs - z * sn) * sc;
                    zs[i] = (x * sn + z * cs) * sc;
                }
                return 0xFFFFFFFF;
            }
            // 灯の差す筋: 脈打ち、燃え尽きで細る
            float k = burn / 0.3f;
            if (k >= 1f) {
                return 0;
            }
            float sc = (1f + 0.06f * (float) Math.sin(t * 0.2f)) * (1f - k);
            for (int i = 0; i < 4; i++) {
                xs[i] *= sc;
                zs[i] *= sc;
            }
            return 0xFFFFFFFF;
        }
        // 行灯 (鉤から下、真ん中): 小さく揺れ、燃え尽きの途中で床へ落ちる
        boolean lantern = Math.abs(cx) < 0.25f && Math.abs(cz) < 0.25f && cy > 0.9f && cy < 1.75f;
        float drop = 0f;
        if (lantern) {
            float sw = 0.05f * (float) Math.sin(t * 0.12f) * (1f - Math.min(1f, burn * 3f));
            float k2 = net.minecraft.util.Mth.clamp((burn - 0.3f) / 0.25f, 0f, 1f);
            drop = 0.95f * k2 * k2;
            for (int i = 0; i < 4; i++) {
                xs[i] += (1.8f - ys[i]) * sw;
                ys[i] -= drop;
            }
            if (k2 >= 1f && sink != null && hash(cx, cy, cz) < 0.3f) {
                sink.at(cx, 0.1f, cz, ROCK);
            }
        }
        float col = 1f;
        if (part == FIRE) {
            if (au >= 0.375f) {
                // 炎: ゆらぎ、燃え尽きで底へ縮んで消える
                float k = burn / 0.3f;
                if (k >= 1f) {
                    return 0;
                }
                float base = 1.025f - drop;
                float fl = (1f + 0.12f * (float) Math.sin(t * 1.3f) + 0.06f * (float) Math.sin(t * 2.9f)) * (1f - k);
                for (int i = 0; i < 4; i++) {
                    xs[i] *= 1f + 0.1f * (float) Math.sin(t * 0.9f);
                    zs[i] *= 1f + 0.1f * (float) Math.sin(t * 0.9f);
                    ys[i] = base + (ys[i] - base) * fl;
                }
                if (k > 0.5f && sink != null) {
                    sink.at(0f, 1.15f - drop, 0f, SMOKE);
                }
                return 0xFFFFFFFF;
            }
            // 紙: 燃え尽きで暗くなる
            col = 1f - 0.6f * Math.min(1f, burn / 0.4f);
        }
        // 地へ沈む
        float k = (burn - 0.6f) / 0.4f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 2.1f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.2f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return part == FIRE ? rgb(col, col * 0.95f, col * 0.9f) : 0xFFFFFFFF;
    }

    // 呪いの印の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int curseQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                 float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK || part == EMBER) {
            return 0;
        }
        if (part == FIRE) {
            float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
            if (au < 0.25f) {
                // 朱印: 回って脈打つ。燃え尽きで敵の頭へ押し下がりながら縮む
                boolean inner = av >= 0.5f;
                float k = burn / 0.4f;
                if (k >= 1f) {
                    return 0;
                }
                float a = inner ? -t * 0.08f : t * 0.05f;
                float sc = (1f + 0.05f * (float) Math.sin(t * 0.5f)) * (1f - 0.7f * k * k);
                float drop = 0.9f * k * k;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                for (int i = 0; i < 4; i++) {
                    float x = xs[i], z = zs[i];
                    xs[i] = (x * cs - z * sn) * sc;
                    zs[i] = (x * sn + z * cs) * sc;
                    ys[i] -= drop;
                }
                if (!inner && k > 0.7f && sink != null) {
                    for (int j = 0; j < 4; j++) {
                        float b = j * 1.5708f + 0.4f;
                        sink.at(0.5f * sc * (float) Math.cos(b), cy - drop, 0.5f * sc * (float) Math.sin(b), SMOKE);
                    }
                }
                return 0xFFFFFFFF;
            }
            float lo = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
            float hi = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));
            if (au < 0.3125f) {
                // 字の糸: 燃え尽きの初めに杭の頭へ縮む
                float k = burn / 0.25f;
                if (k >= 1f) {
                    return 0;
                }
                for (int i = 0; i < 4; i++) {
                    ys[i] = lo + (ys[i] - lo) * (1f - k);
                }
                return 0xFFFFFFFF;
            }
            // 札: 下から燃え上がる
            float k = burn / 0.6f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                ys[i] = hi - (hi - ys[i]) * (1f - k);
            }
            if (k > 0.1f && sink != null && hash(cx, cy, cz) < 0.8f) {
                sink.at(cx, hi - (hi - lo) * (1f - k), cz, SMOKE);
            }
            float r = 1f, g = 1f - 0.4f * k, b = 1f - 0.5f * k;
            return rgb(r, g, b);
        }
        // 杭と帯: 燃える間と燃え尽きの初めは震え、後半に地へ沈む
        float shake = burn < 0.5f ? 0.012f + 0.03f * burn : 0f;
        for (int i = 0; i < 4; i++) {
            xs[i] += shake * (float) Math.sin(t * 2.7f + cz * 9f);
            zs[i] += shake * (float) Math.cos(t * 2.3f + cx * 9f);
        }
        float k = (burn - 0.5f) / 0.5f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 1.5f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.2f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return 0xFFFFFFFF;
    }

    // 巡りの唄の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int cycleQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                 float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK || part == OTHER) {
            return 0;
        }
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE && au < 0.25f) {
            boolean floor = av >= 0.5f;
            float k = burn / (floor ? 0.5f : 0.6f);
            if (k >= 1f) {
                return 0;
            }
            // 帯は巡り、花の輪は逆へ回る。燃え尽きで帯は速く巡って昇り広がる
            float a = floor ? -t * 0.04f : t * 0.07f + 2.5f * k * k;
            float grow = 1f + (floor ? 0.8f : 0.6f) * k;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            for (int i = 0; i < 4; i++) {
                float x = xs[i], z = zs[i];
                xs[i] = (x * cs - z * sn) * grow;
                zs[i] = (x * sn + z * cs) * grow;
                if (!floor) {
                    ys[i] += 0.9f * k;
                }
            }
            if (!floor && k > 0.7f && sink != null && hash(cx, cy, cz) < 0.5f) {
                sink.at(xs[0], ys[0], zs[0], SMOKE);
            }
            return 0xFFFFFFFF;
        }
        float rad = (float) Math.hypot(cx, cz) + 1e-4f;
        float tx = -cz / rad, tz = cx / rad;  // 接線
        if (part == FIRE) {
            // 藤の房: 下ほど揺れ、燃え尽きで花びらになって落ちる
            float k = burn / 0.5f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                float sw = 0.07f * (1.3f - ys[i]) * (float) Math.sin(t * 0.3f + cx * 3f + cz * 2f);
                xs[i] += tx * sw;
                zs[i] += tz * sw;
                ys[i] -= 0.9f * k * k;
            }
            if (k > 0.3f && sink != null && hash(cx, cy, cz) < 0.7f) {
                sink.at(cx, cy - 0.9f * k * k, cz, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        // 鈴と舌 (腕の先、半径 0.5 の辺り、高い所): 揺れる。燃え尽きの初めに大きく鳴る
        boolean bell = rad < 0.6f && cy > 0.8f && cy < 1.28f;
        if (bell) {
            float k1 = Math.min(1f, burn / 0.45f);
            float ang = 0.08f * (float) Math.sin(t * 0.25f + rad * 5f) + (burn > 0f ? 0.45f * (float) Math.sin(burn * 34f) * (1f - 0.6f * k1) : 0f);
            for (int i = 0; i < 4; i++) {
                float d = (1.3f - ys[i]) * (float) Math.sin(ang);
                xs[i] += tx * d;
                zs[i] += tz * d;
            }
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.4f * cool;
            g = 1f - 0.55f * cool;
            b = 1f - 0.35f * cool;
        }
        // 地へ沈む
        float k = (burn - 0.45f) / 0.55f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 1.6f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.2f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 引き潮の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int ebbQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                               float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK || part == EMBER) {
            return 0;
        }
        float rad = (float) Math.hypot(cx, cz);
        if (part == FIRE) {
            if (au < 0.25f) {
                // 波頭: 燃える間は上下に揺れ、燃え尽きで放った人へ駆け戻りながら低くなる
                float k = burn / 0.55f;
                if (k >= 1f) {
                    return 0;
                }
                float f = 1f - 0.7f * k * (2f - k);
                float hgt = (1f + 0.14f * (float) Math.sin(t * 0.35f + rad * 3f)) * (float) Math.pow(1f - k, 0.7f);
                for (int i = 0; i < 4; i++) {
                    xs[i] *= f;
                    zs[i] *= f;
                    ys[i] *= hgt;
                }
                if (k > 0.45f && sink != null && hash(cx, cy, cz) < 0.6f) {
                    sink.at(cx * f, 0.25f * hgt, cz * f, SMOKE);
                }
                return 0xFFFFFFFF;
            }
            // 泡のレース: 引かれて消える
            float k = burn / 0.45f;
            if (k >= 1f) {
                return 0;
            }
            float f = 1f - 0.55f * k;
            for (int i = 0; i < 4; i++) {
                xs[i] *= f;
                zs[i] *= f;
            }
            return 0xFFFFFFFF;
        }
        // 藻: 先がなびく (岩は揺れない)
        if (part == OTHER) {
            for (int i = 0; i < 4; i++) {
                float w = 0.35f * ys[i] * (float) Math.sin(t * 0.4f + cz * 4f + cx * 2f);
                xs[i] += w;
            }
        }
        // 燃え尽きの前半: 藻は放った人へ引きずられる
        float k1 = Math.min(1f, burn / 0.5f);
        if (part == OTHER && k1 > 0f) {
            float f = 1f - 0.35f * k1 * k1;
            for (int i = 0; i < 4; i++) {
                xs[i] *= f;
                zs[i] *= f;
            }
        }
        // 後半: 地へ沈む
        float k = (burn - 0.5f) / 0.5f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 0.45f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.25f) {
                float f = part == OTHER ? 0.65f : 1f;
                sink.at(cx * f, 0.05f, cz * f, ROCK);
            }
        }
        return 0xFFFFFFFF;
    }

    // 守りの唄の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int wardQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK || part == OTHER) {
            return 0;
        }
        if (part == FIRE) {
            if (au < 0.25f) {
                // 唄の輪: 段ごとに逆へ回り、上下に揺れる。燃え尽きで広がって昇り消える
                float dir = cy > 0.5f ? -1f : 1f;
                float a = dir * t * 0.05f + cy * 2f;
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                float k = burn / 0.5f;
                if (k >= 1f) {
                    return 0;
                }
                float grow = 1f + 1.3f * k * k;
                float lift = 0.04f * (float) Math.sin(t * 0.22f + cy * 5f) + 0.7f * k;
                for (int i = 0; i < 4; i++) {
                    float x = xs[i], z = zs[i];
                    xs[i] = (x * cs - z * sn) * grow;
                    zs[i] = (x * sn + z * cs) * grow;
                    ys[i] += lift;
                }
                if (k > 0.75f && sink != null) {
                    float rr = 0.8f * grow * (1.1f - cy * 0.5f);
                    for (int j = 0; j < 4; j++) {
                        float b = j * 1.5708f + cy;
                        sink.at(rr * (float) Math.cos(b), cy + lift, rr * (float) Math.sin(b), SMOKE);
                    }
                }
                return 0xFFFFFFFF;
            }
            // 唄の幕: 上ほど外へゆらぐ。燃え尽きで床へ沈む
            float k = burn / 0.35f;
            if (k >= 1f) {
                return 0;
            }
            float r = (float) Math.hypot(cx, cz) + 1e-4f;
            for (int i = 0; i < 4; i++) {
                float h = ys[i];
                float w = 0.05f * h * (float) Math.sin(t * 0.3f + h * 4f + cx * 3f);
                xs[i] += cx / r * w;
                zs[i] += cz / r * w;
                ys[i] = h * (1f - k);
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            // 真珠: 燃え尽きで灰色に曇る
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.45f * cool;
            g = 1f - 0.45f * cool;
            b = 1f - 0.35f * cool;
        }
        // 扇: 内へ閉じる (上ほど真ん中へ寄る)
        float rad = (float) Math.hypot(cx, cz) + 1e-4f;
        float k1 = Math.min(1f, burn / 0.45f);
        if (k1 > 0f) {
            float e = k1 * k1 * (3f - 2f * k1);
            for (int i = 0; i < 4; i++) {
                float pull = 0.55f * ys[i] * e;
                xs[i] -= cx / rad * pull;
                zs[i] -= cz / rad * pull;
            }
        }
        // 地へ沈む
        float k = (burn - 0.45f) / 0.55f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 1.1f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.2f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 骨の罠の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int trapQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK) {
            return 0;
        }
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE) {
            if (au < 0.25f) {
                // 鬼火: 上ほどゆらぎ、燃え尽きで伸びて細って消える
                float k = burn / 0.35f;
                if (k >= 1f) {
                    return 0;
                }
                for (int i = 0; i < 4; i++) {
                    float h = ys[i];
                    xs[i] += 0.12f * h * (float) Math.sin(t * 0.35f + h * 3f);
                    zs[i] += 0.12f * h * (float) Math.cos(t * 0.3f + h * 3f);
                    if (k > 0f) {
                        ys[i] = h * (1f + 0.9f * k);
                        xs[i] *= 1f - 0.7f * k;
                        zs[i] *= 1f - 0.7f * k;
                    }
                }
                if (k > 0.6f && sink != null) {
                    sink.at(0f, 1.6f, 0f, SMOKE);
                }
                return 0xFFFFFFFF;
            }
            // 光の帯: 顎が閉じる間に床へ沈む
            float k = burn / 0.15f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                ys[i] *= 1f - k;
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.4f);
            r = 1f - 0.4f * cool;
            g = 1f - 0.6f * cool;
            b = 1f - 0.5f * cool;
        }
        // 顎と牙: 蝶番の線 (x 軸) まわりに跳ね上がって噛み合う。動かない骨と踏み板はそのまま
        boolean jaw = Math.hypot(cx, cz) > 0.4f && !(part == STONE && av >= 0.5f);
        if (jaw && burn > 0f) {
            float k = Math.min(1f, burn / 0.16f);
            float a = (float) (Math.PI * 0.47) * k * k * (cz < 0f ? 1f : -1f);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            for (int i = 0; i < 4; i++) {
                float y = ys[i], z = zs[i];
                ys[i] = y * cs - z * sn;
                zs[i] = y * sn + z * cs;
            }
            if (k >= 1f && sink != null) {
                sink.at(0f, 0.6f, 0f, SMOKE);
            }
        }
        // 罠ごと地へ沈む
        float k = (burn - 0.4f) / 0.6f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 0.9f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.25f) {
                sink.at(cx, 0.05f, cz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 雲穿ちの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int pierceQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                  float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK) {
            return 0;
        }
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE && au < 0.25f) {
            // 雲の輪: 逆へゆっくり回り、燃え尽きで広がって後ろへ流れ、薄れて消える
            rollZ(xs, ys, -t * 0.12f);
            float k = burn / 0.75f;
            if (k >= 1f) {
                return 0;
            }
            if (k > 0f) {
                float sc = 1f + 0.9f * k;
                for (int i = 0; i < 4; i++) {
                    xs[i] *= sc;
                    ys[i] = 0.25f + (ys[i] - 0.25f) * sc;
                    zs[i] = cz + (zs[i] - cz) * sc + 0.5f * k;
                }
                if (k > 0.3f && sink != null) {
                    sink.at(cx * sc, cy, cz + 0.5f * k, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        rollZ(xs, ys, t * 0.5f);
        if (part == FIRE) {
            if (av < 0.19f) {
                // 飛行機雲: 尾 (+z) から前の端へ縮んで細る
                float k = burn / 0.45f;
                if (k >= 1f) {
                    return 0;
                }
                float front = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
                for (int i = 0; i < 4; i++) {
                    zs[i] = front + (zs[i] - front) * (1f - k);
                    xs[i] = xs[i] * (1f - 0.6f * k);
                    ys[i] = 0.25f + (ys[i] - 0.25f) * (1f - 0.6f * k);
                }
                return 0xFFFFFFFF;
            }
            // 衝撃波: すぐ広がって消える
            float k = burn / 0.2f;
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                xs[i] *= 1f + 0.8f * k;
                ys[i] = 0.25f + (ys[i] - 0.25f) * (1f + 0.8f * k);
            }
            return 0xFFFFFFFF;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.5f);
            r = 1f - 0.25f * cool;
            g = 1f - 0.5f * cool;
            b = 1f - 0.45f * cool;
        }
        // 矢: 面ごとに割れて、ばらばらの向きへ落ちる
        float k = (burn - 0.2f) / 0.75f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float h = hash(cx, cy, cz);
            float ox = (h - 0.5f) * 0.9f * k, oy = (hash(cz, cx, cy) - 0.3f) * 0.4f * k - 1.1f * k * k, oz = (hash(cy, cz, cx) - 0.5f) * 0.6f * k;
            float sc = 1f - 0.5f * k;
            float mx = (xs[0] + xs[1] + xs[2] + xs[3]) / 4f, my = (ys[0] + ys[1] + ys[2] + ys[3]) / 4f, mz = (zs[0] + zs[1] + zs[2] + zs[3]) / 4f;
            for (int i = 0; i < 4; i++) {
                xs[i] = mx + (xs[i] - mx) * sc + ox;
                ys[i] = my + (ys[i] - my) * sc + oy;
                zs[i] = mz + (zs[i] - mz) * sc + oz;
            }
            if (k > 0.75f && sink != null && h < 0.3f) {
                sink.at(mx + ox, my + oy, mz + oz, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 風裂きの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int windQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == SPARK) {
            return 0;
        }
        // 芯 (y = 0.25) を軸に z まわりに回す
        float d = t * 0.8f;
        float cs = (float) Math.cos(d), sn = (float) Math.sin(d);
        float ry = cy - 0.25f;
        float ncx = cx * cs - ry * sn, ncy = 0.25f + cx * sn + ry * cs;
        for (int i = 0; i < 4; i++) {
            float x = xs[i], y = ys[i] - 0.25f;
            xs[i] = x * cs - y * sn;
            ys[i] = 0.25f + x * sn + y * cs;
        }
        if (part == FIRE) {
            float k = burn / 0.5f;
            if (k >= 1f) {
                return 0;
            }
            if (k > 0f) {
                float sc = 1f + 0.8f * k;
                for (int i = 0; i < 4; i++) {
                    xs[i] *= sc;
                    ys[i] = 0.25f + (ys[i] - 0.25f) * sc;
                }
                if (k > 0.4f && k < 0.5f && sink != null) {
                    sink.at(ncx * sc, 0.25f + (ncy - 0.25f) * sc, cz, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        // 木の葉: 外へ散り、後ろへ流れて落ちる
        float k = (burn - 0.1f) / 0.8f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float ox = ncx * 0.9f * k, oy = (ncy - 0.25f) * 0.9f * k - k * k * 0.9f, oz = 0.35f * k;
            for (int i = 0; i < 4; i++) {
                xs[i] += ox;
                ys[i] += oy;
                zs[i] += oz;
            }
            if (k > 0.8f && sink != null && hash(cx, cy, cz) < 0.35f) {
                sink.at(ncx + ox, ncy + oy, cz + oz, ROCK);
            }
        }
        return 0xFFFFFFFF;
    }

    // 双牙の面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int fangQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            if (au < 0.25f) {
                // 顎の閃き: 昇りながら縦に細って消える
                float k = burn / 0.3f;
                if (k >= 1f) {
                    return 0;
                }
                for (int i = 0; i < 4; i++) {
                    ys[i] = cy + (ys[i] - cy) * (1f - 0.8f * k) + 0.4f * k;
                    xs[i] = cx + (xs[i] - cx) * (1f + 0.3f * k);
                    zs[i] = cz + (zs[i] - cz) * (1f + 0.3f * k);
                }
                return 0xFFFFFFFF;
            }
            // 毒のしぶき: 外へ広がって地へ落ちる
            float k = burn / 0.45f;
            if (k >= 1f) {
                return 0;
            }
            if (k > 0f) {
                for (int i = 0; i < 4; i++) {
                    xs[i] *= 1f + 0.6f * k;
                    zs[i] *= 1f + 0.6f * k;
                    ys[i] *= 1f - k;
                }
                if (k > 0.5f && sink != null) {
                    sink.at(cx * 1.3f, 0.1f, cz * 1.3f, SMOKE);
                }
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK || part == OTHER) {
            return 0;
        }
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.35f * cool;
            g = 1f - 0.55f * cool;
            b = 1f - 0.6f * cool;
        }
        // 牙: 地へ引っ込む (根元から真下へ)
        float k = (burn - 0.25f) / 0.6f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 2.0f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.3f) {
                sink.at(cx > 0f ? 0.69f : -0.69f, 0.05f, 0f, ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 霧隠れの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int mistQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE) {
            float ph = (float) Math.atan2(cx, -cz);
            if (av >= 0.5f) {
                // 昇る筋: ずっと昇り、燃え尽きで細って消える
                float k = burn / 0.5f;
                if (k >= 1f) {
                    return 0;
                }
                float up = 0.012f * t + 0.3f * k;
                for (int i = 0; i < 4; i++) {
                    ys[i] = cy + (ys[i] - cy) * (1f - k) + up;
                }
                return 0xFFFFFFFF;
            }
            // 霧の幕: 角ごとにずれてゆらぐ。燃え尽きで外へ広がり、地へ沈んで消える (後ろの真ん中から左右の端へ)
            float sway = (float) Math.sin(t * 0.09f + ph * 2f) * 0.06f;
            float side = Math.abs(Math.abs(ph) - (float) Math.PI);  // 0 = 真後ろ
            float k = (burn - side * 0.12f) / 0.6f;
            if (k >= 1f) {
                return 0;
            }
            float out = 1f + (k > 0f ? 0.45f * k : 0f);
            float down = k > 0f ? 1f - k : 1f;
            float cs = (float) Math.cos(sway), sn = (float) Math.sin(sway);
            for (int i = 0; i < 4; i++) {
                float x = xs[i] * cs - zs[i] * sn, z = zs[i] * cs + xs[i] * sn;
                xs[i] = x * out;
                zs[i] = z * out;
                ys[i] = ys[i] * down * (1f + 0.04f * (float) Math.sin(t * 0.12f + ph));
            }
            if (k > 0.3f && k < 0.4f && sink != null) {
                sink.at(cx * out, 0.3f, cz * out, SMOKE);
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK || part == EMBER) {
            return 0;
        }
        // 変わり身の丸太 (と縄・札): 燃え尽きの初めにしぼんで消える
        float k = (burn - 0.2f) / 0.25f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            shrink(xs, ys, zs, 0f, 0.84f, 0.56f, k);
            for (int i = 0; i < 4; i++) {
                ys[i] += 0.15f * k;
            }
            if (sink != null) {
                sink.at(0f, 0.6f, 0.56f, ROCK);
            }
        }
        return 0xFFFFFFFF;
    }

    // 後ろ跳びの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int backQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                float cx, float cy, float cz, float t, float burn, Sink sink) {
        float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
        if (part == FIRE) {
            if (av < 0.5f) {
                // 煙: 足もとを軸にふくらみ、燃え尽きで広がりながら昇って消える (上の塊は少し遅い)
                float k = (burn - (cy > 1.0f ? 0.12f : 0f)) / 0.6f;
                if (k >= 1f) {
                    return 0;
                }
                float sc = 1f + 0.02f * Math.min(t, 15f) + (k > 0f ? 0.7f * k : 0f);
                float up = 0.012f * Math.min(t, 15f) + (k > 0f ? 0.6f * k : 0f);
                for (int i = 0; i < 4; i++) {
                    xs[i] *= sc;
                    zs[i] *= sc;
                    ys[i] = ys[i] * sc + up;
                }
                if (k > 0.4f && k < 0.5f && sink != null) {
                    sink.at(cx * sc, cy * sc + up, cz * sc, SMOKE);
                }
                return 0xFFFFFFFF;
            }
            // 弧の点線: 跳んだ順 (原点の側から着地の側へ) に縮んで消える
            float f = Math.max(0f, (-cz - 0.25f) / 2.3f);
            float k = (burn / 0.5f - f) / 0.25f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                shrink(xs, ys, zs, cx, cy, cz, k);
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK) {
            return 0;
        }
        boolean iron = part == EMBER || (part == STONE && av >= 0.5f);
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.8f);
            r = 1f - 0.5f * cool;
            g = 1f - 0.6f * cool;
            b = 1f - 0.6f * cool;
        }
        // 角 (12 度ごと) で沈む順をずらす
        float ph = (float) Math.toDegrees(Math.atan2(cx, cz));
        float hp = hash(Math.round(ph / 12f), 1.7f, 0f);
        float st = 0.35f + 0.35f * hp;
        float k = (burn - st) / 0.3f;
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            for (int i = 0; i < 4; i++) {
                ys[i] -= k * 0.3f;
            }
            shrink(xs, ys, zs, cx, cy - k * 0.3f, cz, k * 0.35f);
            if (sink != null && hash(cx, cy, cz) < 0.3f) {
                sink.at(cx, 0.05f, cz, iron ? CRUMBLE : ROCK);
            }
        }
        return rgb(r, g, b);
    }

    // 踏み込みの面 1 枚。形を書き換えて色を返す。描かないときは 0
    private static int lungeQuad(int part, float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float au,
                                 float cx, float cy, float cz, float t, float burn, Sink sink) {
        if (part == FIRE) {
            float av = (vs[0] + vs[1] + vs[2] + vs[3]) / 4f;
            if (av >= 0.5f) {
                // 衝撃の輪: 燃える間もゆっくり広がり、燃え尽きで大きく広がって消える (前の輪ほど遅い)
                float k = (burn - (-cz - 0.5f) * 0.12f) / 0.45f;
                if (k >= 1f) {
                    return 0;
                }
                float sc = 1f + 0.025f * Math.min(t, 12f) + (k > 0f ? 0.9f * k : 0f);
                for (int i = 0; i < 4; i++) {
                    xs[i] = cx + (xs[i] - cx) * sc;
                    ys[i] = cy + (ys[i] - cy) * sc;
                }
                if (k > 0.5f && k < 0.6f && sink != null) {
                    sink.at(cx, cy, cz, SMOKE);
                }
                return 0xFFFFFFFF;
            }
            // 突きの筋: 尾 (原点の側、f = 0) から頭 (f = 1) へ順に細って消える
            float f = Math.max(0f, (-cz - 0.25f) / 2.25f);
            float k = (burn / 0.55f - f) / 0.3f;
            if (k > 0f) {
                if (k >= 1f) {
                    return 0;
                }
                for (int i = 0; i < 4; i++) {
                    ys[i] = cy + (ys[i] - cy) * (1f - k);
                    xs[i] = cx + (xs[i] - cx) * (1f - 0.6f * k);
                    zs[i] -= k * 0.35f;
                }
            }
            return 0xFFFFFFFF;
        }
        if (part == SPARK) {
            return 0;
        }
        // 苦無: 刃の向き (柄頭 → 先) へ地へ沈む
        float k = (burn - 0.4f) / 0.6f;
        float r = 1f, g = 1f, b = 1f;
        if (part == EMBER) {
            float cool = Math.min(1f, burn * 1.6f);
            r = 1f - 0.4f * cool;
            g = 1f - 0.75f * cool;
            b = 1f - 0.55f * cool;
        }
        if (k > 0f) {
            if (k >= 1f) {
                return 0;
            }
            float d = k * k * 2.6f;
            for (int i = 0; i < 4; i++) {
                ys[i] -= 0.574f * d;
                zs[i] -= 0.819f * d;
            }
            if (sink != null && hash(cx, cy, cz) < 0.25f) {
                sink.at(0.12f * (int) (k * 4f), 0.05f, -4.9f, ROCK);
            }
        }
        return rgb(r, g, b);
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
