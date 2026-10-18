"""珊瑚憑き 2 回目: 骨格に見えない 3 案の比較シートを作り、メッシュ・アトラスも書き出す。

実行: python tools/rough/lowpoly/coral_thrall2/compare.py
出力: docs/review/lowpoly_roughs/coral_thrall2/{a,b,c}/ と docs/review/lowpoly_roughs/coral_thrall2.png
"""
import copy
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import *  # noqa
from PIL import Image, ImageDraw  # noqa
import opt_a, opt_b, opt_c  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A 岩礁の溺れ人", opt_a), ("b", "B 海綿と昆布の外套", opt_b), ("c", "C 枝珊瑚の長身", opt_c)]
COLW, GAP, X0 = 376, 16, 24
HW = 184
OLD_OPT = HERE.parent / "coral_thrall" / "opt_a.py"
SENT = OUT / "bone_sentinel_lowpoly.png"


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def mem_option(mod):
    """書き出さずに本体・銛・アトラスだけを作る (旧 A の表示用)。"""
    body, spear, eyes = mod.build()
    size, P, N, M, L = build_atlas(body, getattr(mod, "HINT", 128), 0)
    atlas, glow = paint(body, size, P, N, M, mod.PAL, eyes)
    ssize, sP, sN, sM, sL = build_atlas(spear, 64, 0)
    satlas, sglow = paint(spear, ssize, sP, sN, sM, mod.PAL, ())
    return dict(body=body, statue=[], spear=spear, size=size, atlas=atlas, glow=glow, ssize=ssize, satlas=satlas, sglow=sglow)


def spear_local(r, big, glow, yoff, yaw, size):
    fl = []
    for f in spear_faces_shifted(r, yoff):
        g = copy.copy(f)
        g.verts = np.array(f.verts, float)
        fl.append(g)
    return render(fl, big, glow, yaw, 0, size, (0, 8.0, 0), 8.6)


def main():
    spec = importlib.util.spec_from_file_location("old_opt_a", OLD_OPT)
    old = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(old)
    rold = mem_option(old)
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod.build, mod.PAL, getattr(mod, "SPEAR_PAL", None), body_hint=getattr(mod, "HINT", 128),
                         statue_fn=getattr(mod, "statue_coral", None))
        r["label"], r["mod"] = label, mod
        results.append(r)
        print(key, "body", r["data"]["triangle_count_normal"], "statue", r["data"]["triangle_count_statue_only"], "spear", r["sdata"]["triangle_count"])
    F = voxel.font
    canvas = Image.new("RGB", (1600, 3000), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "珊瑚憑き (coral_thrall) 第 2 弾 — 「鯨の骨格の使い回し」に見えない 3 案", font=F(26), fill=WH)
    d.text((24, 50), "前回選んだ A (枝角の冠) の枝分かれする珊瑚の冠は残し、肋骨・象牙色の骨・頭蓋骨の顔をやめた。体そのものが珊瑚・岩・海綿でできた「珊瑚に憑かれた溺れ人」。"
                     "骨の名前・階層・pivot は今の geo と同じ。銛は別メッシュ、像は白い石のアトラス。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    def colx(i):
        return X0 + i * (COLW + GAP)

    def pane(i, j, y, w, h, im):
        px = colx(i) + j * (w + 8)
        bgp = bg_panel(w, h)
        bgp.paste(im, (0, 0), im if im.mode == "RGBA" else None)
        canvas.paste(bgp, (px, y))
        frame(px, y, w, h)

    top = 96
    PH = 560
    big0, gl0, yo0 = combined_atlas(rold)
    # ---- 1 段目: 全身
    for i in range(4):
        for j, yaw in enumerate((212, 32)):
            if i == 0:
                if j == 0:
                    im = render_pose(rold, big0, gl0, yo0, {}, 212, 13, (HW, PH // 2 - 4), (0, 19.5, -0.5), 6.4)
                    px = colx(0)
                    bgp = bg_panel(HW, PH // 2 - 4)
                    bgp.paste(im, (0, 0), im)
                    canvas.paste(bgp, (px, top))
                    frame(px, top, HW, PH // 2 - 4)
                    d.text((px + 6, top + 6), "前回 A (骨)", font=F(13), fill=WH)
                else:
                    sc = Image.open(SENT).convert("RGB").crop((414, 90, 795, 730)).resize((HW, int(640 * HW / 381)), Image.LANCZOS)
                    px = colx(0) + HW + 8
                    sc = sc.crop((0, 0, HW, PH // 2 - 4)) if sc.height >= PH // 2 - 4 else sc
                    canvas.paste(sc, (px, top))
                    frame(px, top, HW, PH // 2 - 4)
                    d.text((px + 6, top + 6), "鯨の背の骨の番兵", font=F(13), fill=WH)
                    # 下半分: 旧 A の後ろ
                continue
            r = results[i - 1]
            big, gl, yoff = combined_atlas(r)
            im = render_pose(r, big, gl, yoff, {}, yaw, 13, (HW, PH), (0, 19.5, -0.5), 12.2 if r["key"] != "c" else 11.4)
            pane(i, j, top, HW, PH, im)
        if i == 0:
            # 左列の下半分: 旧 A の顔と、骨の番兵の顔 (対比)
            hf = render_pose(rold, big0, gl0, yo0, {}, 200, 8, (HW, PH // 2 - 4), (0, 31.5, -1.5), 14.5, spear=False)
            pane(0, 0, top + PH // 2 + 4, HW, PH // 2 - 4, hf)
            d.text((colx(0) + 6, top + PH // 2 + 10), "前回 A の顔", font=F(13), fill=WH)
            sc = Image.open(SENT).convert("RGB").crop((1201, 90, 1543, 530)).resize((HW, int(440 * HW / 342)), Image.LANCZOS)
            sc = sc.crop((0, 0, HW, min(sc.height, PH // 2 - 4)))
            canvas.paste(sc, (colx(0) + HW + 8, top + PH // 2 + 4))
            frame(colx(0) + HW + 8, top + PH // 2 + 4, HW, PH // 2 - 4)
            d.text((colx(0) + HW + 14, top + PH // 2 + 10), "番兵の顔", font=F(13), fill=WH)
            d.text((colx(0), top + PH + 6), "対比: 前回 A と鯨の背の骨 (小)", font=F(18), fill=WH)
            d.text((colx(0), top + PH + 28), "どちらも象牙色の骨・肋骨・頭蓋骨の顔", font=F(13), fill=GR)
        else:
            r = results[i - 1]
            d.text((colx(i), top + PH + 6), f"{r['label']}  {r['data']['triangle_count_normal']} 三角形 (+銛 {r['sdata']['triangle_count']})", font=F(18), fill=CY)
            d.text((colx(i), top + PH + 28), "前 3/4 (左)・後ろ 3/4 (右)", font=F(13), fill=GR)
    # ---- 2 段目: 顔のアップ
    top2 = top + PH + 58
    h2 = 250
    for i in range(1, 4):
        r = results[i - 1]
        big, gl, yoff = combined_atlas(r)
        for j, (yaw, pitch) in enumerate(((200, 8), (140, 8))):
            im = render_pose(r, big, gl, yoff, {}, yaw, pitch, (HW, h2), r["mod"].HEAD_CENTER, r["mod"].HEAD_SCALE, spear=False)
            pane(i, j, top2, HW, h2, im)
        d.text((colx(i), top2 + h2 + 6), r["label"][:1] + " の顔 (前 / 斜め)", font=F(15), fill=CY)
    # 左列: 顔の特徴メモ
    notes = ["顔の作り", "A: 平らな石の面 + 海硝子の目", "   歯・顎なし、紫の海藻のひげ", "B: イソギンチャクの円盤", "   触手の襟の中の暗い窪みに目",
             "C: 筋入りの二枚貝の殻", "   殻の隙間の窪みに目", "共通: 目の 2 点だけが光る"]
    for k, t in enumerate(notes):
        d.text((colx(0), top2 + 6 + 24 * k), t, font=F(15 if k else 18), fill=WH if k == 0 else GR)
    # ---- 3 段目: 突き
    top3 = top2 + h2 + 40
    PH3 = 480
    for i in range(1, 4):
        r = results[i - 1]
        big, gl, yoff = combined_atlas(r)
        for j, pose in enumerate((WINDUP, THRUST)):
            im = render_pose(r, big, gl, yoff, pose, 130, 10, (HW, PH3), (0, 19.5, -4.0), 11.2, pos=POS if j == 1 else None)
            pane(i, j, top3, HW, PH3, im)
        d.text((colx(i), top3 + PH3 + 6), r["label"][:1] + " の突き (attack: 構え 0.3 秒 / 突き 0.45 秒)", font=F(14), fill=CY)
    for j, pose in enumerate((WINDUP, THRUST)):
        im = render_pose(rold, big0, gl0, yo0, pose, 130, 10, (HW, PH3), (0, 19.5, -4.0), 11.2, pos=POS if j == 1 else None)
        pane(0, j, top3, HW, PH3, im)
    d.text((colx(0), top3 + PH3 + 6), "前回 A の突き", font=F(14), fill=WH)
    # ---- 4 段目: 銛と像
    top4 = top3 + PH3 + 40
    BH, bw = 330, 74
    for i in range(1, 4):
        r = results[i - 1]
        big, gl, yoff = combined_atlas(r)
        for j, yaw in enumerate((0, 90)):
            im = spear_local(r, big, gl, yoff, yaw, (bw, BH))
            px = colx(i) + j * (bw + 6)
            bgp = bg_panel(bw, BH)
            bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top4))
            frame(px, top4, bw, BH)
        sx = colx(i) + 2 * (bw + 6)
        sw = COLW - 2 * (bw + 6)
        bgp = bg_panel(sw, BH)
        sbig = statue_atlas(big.copy())
        im = render_pose(r, sbig, gl, yoff, {}, 212, 13, (sw, BH), (0, 19.0, -0.5), 7.6 if r["key"] != "c" else 7.0, statue=True)
        bgp.paste(im, (0, 0), im)
        canvas.paste(bgp, (sx, top4))
        frame(sx, top4, sw, BH)
        d.text((colx(i), top4 + BH + 6), r["label"][:1] + " の銛 (正面 / 横)・像 (白い石 + 骨 statue_coral)", font=F(13), fill=CY)
    for j, yaw in enumerate((0, 90)):
        im = spear_local(rold, big0, gl0, yo0, yaw, (bw, BH))
        px = colx(0) + j * (bw + 6)
        bgp = bg_panel(bw, BH)
        bgp.paste(im, (0, 0), im)
        canvas.paste(bgp, (px, top4))
        frame(px, top4, bw, BH)
    d.text((colx(0), top4 + BH + 6), "前回 A の銛", font=F(13), fill=WH)
    # ---- 5 段目: アトラスとメモ
    top5 = top4 + BH + 44
    ex = {"a": "濡れた岩礁そのものになった重い猫背。胸にフジツボと短い珊瑚のかたまり、左肩に脳珊瑚。顔は平らな石に海硝子の目。頭・肩・背から枝分かれする枝珊瑚の冠 (前回選んだ冠をそのまま)。銛は枝珊瑚の三つ又。",
          "b": "黄橙色の穴だらけの海綿の体。深い青紫の昆布を外套のように肩から背へ垂らし、肩と背に管海綿の筒。顔はイソギンチャクの円盤、頭の上に細い枝珊瑚。銛は巻貝の尖塔。丸く垂れる輪郭。",
          "c": "背が高く細い枝珊瑚の影。胴は深い青紫の芯、桃・橙・紅の枝が四方へ伸びて輪郭がぎざつく。頭は筋入りの二枚貝の殻に冠。銛は枝分かれした珊瑚の小枝。縦に伸びる輪郭。"}
    for i in range(1, 4):
        r = results[i - 1]
        x = colx(i)
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        asz = r["size"] * 2
        canvas.paste(bgc.convert("RGB").resize((asz, asz), Image.NEAREST), (x, top5))
        frame(x, top5, asz, asz)
        sa = Image.fromarray(r["satlas"], "RGBA")
        bgs = Image.new("RGBA", sa.size, (40, 40, 52, 255))
        bgs.alpha_composite(sa)
        canvas.paste(bgs.convert("RGB"), (x + asz + 8, top5))
        d.text((x + asz + 8, top5 + 70), f"銛 {r['ssize']}²", font=F(12), fill=GR)
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        lines = [r["label"], f"本体 {r['data']['triangle_count_normal']} + 銛 {r['sdata']['triangle_count']} (+像の珊瑚 {r['data']['triangle_count_statue_only']})",
                 f"頭 {pt('head')} / 胴 {pt('body')} / 右腕 {pt('arm_r')}+{pt('forearm_r')} / 左腕 {pt('arm_l')}+{pt('forearm_l')} / 脚 {pt('leg_l')}×2"]
        yy = top5 + asz + 10
        for k, t in enumerate(lines):
            d.text((x, yy), t, font=F(15 if k else 18), fill=CY if k == 0 else GR)
            yy += 24
        txt = ex[r["key"]]
        for s0 in range(0, len(txt), 24):
            d.text((x, yy), txt[s0:s0 + 24], font=F(14), fill=GR)
            yy += 20
    x = colx(0)
    for k, t in enumerate(("予算: 普通の敵 約 600 三角形、銛は 250 以下", "骨: root(Y 180 度) > body > head / arm_r >", "   forearm_r(休め X 70) > socket_main(X -150, 空)",
                           "   / arm_l > forearm_l / statue_coral(像のみ)", "   と root > leg_r / leg_l", "光る点: 目の 2 点 (本体)、銛の海硝子 (銛)",
                           "パレット: 珊瑚の桃・橙・紅 / 海の青緑 / 深い青紫 / 濡れた石", "  (象牙色と苔緑は鯨の背のものなので使わない)")):
        d.text((x, top5 + 6 + 22 * k), t, font=F(13), fill=GR)
    out = OUT / "coral_thrall2.png"
    canvas = canvas.crop((0, 0, 1600, top5 + 560))
    canvas.save(out)
    print("saved", out, canvas.size)


if __name__ == "__main__":
    main()
