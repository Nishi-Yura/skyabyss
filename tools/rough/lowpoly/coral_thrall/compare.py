"""珊瑚憑き: 今のモデルと低ポリ 3 案の比較画像を作り、メッシュ・アトラスも書き出す。

実行: python tools/rough/lowpoly/coral_thrall/compare.py
出力: docs/review/lowpoly_roughs/coral_thrall/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク・像のアトラス・銛のメッシュとアトラス)
      docs/review/lowpoly_roughs/coral_thrall.png
"""
import copy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import *  # noqa
from old import old_faces  # noqa
from PIL import Image, ImageDraw  # noqa
import opt_a, opt_b, opt_c  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A 枝角の冠", opt_a), ("b", "B 扇珊瑚の背負い", opt_b), ("c", "C 磯巾着の鬣", opt_c)]
COLW, GAP, X0 = 376, 16, 24
HW = 184


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def fit_old(faces, yaw, pitch, size, margin=0.04):
    side = max(size)
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(side, side), margin=margin)
    x0 = (side - size[0]) // 2
    y0 = (side - size[1]) // 2
    return im.crop((x0, y0, x0 + size[0], y0 + size[1]))


def spear_local(r, big, glow, yoff, yaw, size, statue=False):
    """銛を局所座標のまま (先端が上) 描く。"""
    fl = []
    for f in spear_faces_shifted(r, yoff):
        g = copy.copy(f)
        g.verts = np.array(f.verts, float)
        fl.append(g)
    return render(fl, big, glow, yaw, 0, size, (0, 8.0, 0), 8.6)


def main():
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod.build, mod.PAL, getattr(mod, "SPEAR_PAL", None), body_hint=getattr(mod, "HINT", 128),
                         statue_fn=getattr(mod, "statue_coral", None))
        r["label"] = label
        r["mod"] = mod
        results.append(r)
        print(key, "body", r["data"]["triangle_count_normal"], "statue", r["data"]["triangle_count_statue_only"], "spear",
              r["sdata"]["triangle_count"], r["size"], r["ssize"])
    F = voxel.font
    PH = 560
    canvas = Image.new("RGB", (1600, 3000), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "珊瑚憑き (coral_thrall) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (ゲームに入れる版の形式)", font=F(26), fill=WH)
    d.text((24, 50), "骨の名前・階層・pivot は今の geo と同じ (今のアニメーションがそのまま動く)。銛は別メッシュで socket_main に付き、像は同じモデルに骨 statue_coral を足して白いテクスチャ。"
                     "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    def colx(i):
        return X0 + i * (COLW + GAP)

    names = ["今 (箱)"] + [o[1] for o in OPTS]
    of, n_ent, n_sp = old_faces()
    # ---------------- 1 段目: 全身 前 3/4・後ろ 3/4
    top = 96
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((212, 32)):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, PH)
            if i == 0:
                im = fit_old(of, yaw, 13, (HW, PH))
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                im = render_pose(r, big, gl, yoff, {}, yaw, 13, (HW, PH), (0, 19.5, -0.5), 12.6)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top))
            frame(px, top, HW, PH)
        if i == 0:
            t = f"今 (箱 {n_ent} 個 + 銛 {n_sp} 個)"
            col = WH
        else:
            r = results[i - 1]
            t = f"{r['label']}  {r['data']['triangle_count_normal']} 三角形 (+銛 {r['sdata']['triangle_count']})"
            col = CY
        d.text((x, top + PH + 6), t, font=F(18), fill=col)
        d.text((x, top + PH + 28), "前 3/4 (左)・後ろ 3/4 (右)", font=F(13), fill=GR)
    # ---------------- 2 段目: 顔のアップ (前・斜め)
    top2 = top + PH + 58
    h2 = 250
    hf, _, _ = old_faces(only_head=True)
    for i in range(4):
        x = colx(i)
        for j, (yaw, pitch) in enumerate(((200, 8), (140, 8))):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, h2)
            if i == 0:
                im = voxel.render(hf, yaw=yaw, pitch=pitch, size=(HW, h2), margin=0.04)
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                im = render_pose(r, big, gl, yoff, {}, yaw, pitch, (HW, h2), r["mod"].HEAD_CENTER, r["mod"].HEAD_SCALE, spear=False)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top2))
            frame(px, top2, HW, h2)
        d.text((x, top2 + h2 + 6), names[i] + " の顔 (前 / 斜め)", font=F(15), fill=CY if i else WH)
    # ---------------- 3 段目: 突き (attack: 0.3 秒の構え → 0.45 秒の突き)
    top3 = top2 + h2 + 40
    PH3 = 480
    of_w, _, _ = old_faces(WINDUP)
    of_t, _, _ = old_faces(THRUST)
    for i in range(4):
        x = colx(i)
        for j, (pose, yaw) in enumerate(((WINDUP, 130), (THRUST, 130))):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, PH3)
            if i == 0:
                sq = voxel.render(of_w if j == 0 else of_t, yaw=yaw, pitch=10, size=(PH3, PH3), margin=0.02).resize((HW, HW), Image.LANCZOS)
                bgp.paste(sq, (0, (PH3 - HW) // 2), sq)
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                im = render_pose(r, big, gl, yoff, pose, yaw, 10, (HW, PH3), (0, 19.5, -4.0), 11.2, pos=POS if j == 1 else None)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top3))
            frame(px, top3, HW, PH3)
        d.text((x, top3 + PH3 + 6), ("今の突き " if i == 0 else names[i][:1] + " の突き ") + "(attack: 構え 0.3 秒 / 突き 0.45 秒)", font=F(14), fill=CY if i else WH)
    # ---------------- 4 段目: 銛 (正面・横) と像
    top4 = top3 + PH3 + 40
    BH = 330
    bw = 74
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((0, 90)):
            px = x + j * (bw + 6)
            bgp = bg_panel(bw, BH)
            if i == 0:
                import json as _j
                bg = _j.loads((ASSETS / "geo/gear/coral_spear.geo.json").read_text(encoding="utf-8"))
                fs = voxel.load_geo(bg, ASSETS / "textures/gear/coral_spear.png", ASSETS / "textures/gear/coral_spear_glowmask.png")
                im = fit_old(fs, yaw + 180 if yaw == 0 else 90, 0, (bw, BH), 0.02)
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                im = spear_local(r, big, gl, yoff, yaw, (bw, BH))
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top4))
            frame(px, top4, bw, BH)
        # 像
        sx = x + 2 * (bw + 6)
        sw = COLW - 2 * (bw + 6)
        bgp = bg_panel(sw, BH)
        if i == 0:
            sf, _, _ = old_faces(statue=True)
            im = fit_old(sf, 212, 13, (sw, BH), 0.03)
            bgp.paste(im, (0, 0))
        else:
            r = results[i - 1]
            big, gl, yoff = combined_atlas(r)
            sbig = big.copy()
            sbig = statue_atlas(sbig)
            im = render_pose(r, sbig, gl, yoff, {}, 212, 13, (sw, BH), (0, 19.0, -0.5), 7.6, statue=True)
            bgp.paste(im, (0, 0), im)
        canvas.paste(bgp, (sx, top4))
        frame(sx, top4, sw, BH)
        d.text((x, top4 + BH + 6), ("今の銛 (正面 / 横)・像" if i == 0 else names[i][:1] + " の銛 (正面 / 横)・像 (白い石 + 骨 statue_coral)"), font=F(13), fill=CY if i else WH)
    # ---------------- 5 段目: アトラスと数字・設計メモ
    top5 = top4 + BH + 44
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
        canvas.paste(bgs.convert("RGB").resize((sa.width * 1, sa.height * 1), Image.NEAREST), (x + asz + 8, top5))
        d.text((x + asz + 8, top5 + 70), f"銛 {r['ssize']}²", font=F(12), fill=GR)
        d.text((x, top5 + asz + 6), f"{r['label'][:1]} 本体のアトラス {r['size']}×{r['size']} (2 倍表示)", font=F(14), fill=CY)
    x = colx(0)
    tx = Image.open(ASSETS / "textures/entity/coral_thrall.png").convert("RGBA")
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    canvas.paste(bgc.convert("RGB"), (x, top5))
    d.text((x, top5 + tx.height + 6), f"今のテクスチャ {tx.width}×{tx.height}", font=F(14), fill=WH)
    y = top5 + tx.height + 40
    for t in (f"今: 箱 {n_ent} 個 + 銛の箱 {n_sp} 個", "予算: 普通の敵 約 600 三角形、銛は 250 以下",
              "骨: root(Y 180 度) > body > head / arm_r >", "   forearm_r(休め X 70) > socket_main(X -150, 空)",
              "   / arm_l > forearm_l / statue_coral(像のみ)", "   と root > leg_r / leg_l",
              "光る点: 目の 2 点 (本体)、銛の海硝子 (銛)"):
        d.text((x, y), t, font=F(13), fill=GR)
        y += 20
    ex = {"a": "細く背の高い白骨に、鹿の角のように枝分かれする枝珊瑚の冠と肩の枝。肋 3 対、腰に昆布の前垂れ。銛は枝珊瑚の三つ又 (先が光る海硝子)。縦へ尖る輪郭。",
          "b": "石になった体に、卓珊瑚の重ね板の肩当て・脳珊瑚の頭巾、背に縁のぎざついた扇珊瑚の大きな円盤。銛は扇珊瑚の平たい葉の穂先。幅広で板の重なる輪郭。",
          "c": "干物のような体に、イソギンチャクの触手が鬣になって頭・肩・背から放射状に垂れる。猫背、左手は触手の爪。銛は反った節の角と根元の触手の襟。丸く垂れる輪郭。"}
    for i in range(1, 4):
        r = results[i - 1]
        x = colx(i)
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        lines = [f"{r['label']}",
                 f"本体 {r['data']['triangle_count_normal']} + 銛 {r['sdata']['triangle_count']} (+像の珊瑚 {r['data']['triangle_count_statue_only']})",
                 f"頭 {pt('head')} / 胴 {pt('body')} / 右腕 {pt('arm_r')}+{pt('forearm_r')} / 左腕 {pt('arm_l')}+{pt('forearm_l')} / 脚 {pt('leg_l')}×2"]
        yy = top5 + r["size"] * 2 + 34
        for k, t in enumerate(lines):
            d.text((x, yy), t, font=F(15 if k else 18), fill=CY if k == 0 else GR)
            yy += 24
        txt = ex[r["key"]]
        for s0 in range(0, len(txt), 24):
            d.text((x, yy), txt[s0:s0 + 24], font=F(14), fill=GR)
            yy += 20
    out = OUT / "coral_thrall.png"
    canvas = canvas.crop((0, 0, 1600, min(canvas.size[1], top5 + 560)))
    canvas.save(out)
    print("saved", out, canvas.size)


if __name__ == "__main__":
    main()
