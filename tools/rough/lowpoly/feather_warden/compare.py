"""羽守: 今のモデルと低ポリ 3 案の比較画像を作り、メッシュ・アトラスも書き出す。

実行: python tools/rough/lowpoly/feather_warden/compare.py
出力: docs/review/lowpoly_roughs/feather_warden/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク・鉤槍のメッシュとアトラス)
      docs/review/lowpoly_roughs/lp_feather_warden.png
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import *  # noqa
from old import old_faces  # noqa
from PIL import Image, ImageDraw  # noqa
import opt_a, opt_b, opt_c  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A 鷲兜の番人", opt_a), ("b", "B 嵐の隼", opt_b), ("c", "C 鐘の守り手", opt_c)]
COLW, GAP, X0 = 376, 16, 24
HW = 184
NOTE = {
    "a": "直立した騎士の輪郭。銅の鷲の嘴の兜に避雷針の冠、重ね羽の肩当て、背から垂れる風切りのマント、鳥の脚。灰青の鋼と白い羽に銅の縁取り。",
    "b": "前のめりで首を突き出した隼。藍黒の鎧、嘴だけ銅の細い兜、後ろへ流れる冠羽と尾羽、折りたたんだ翼のマント、長い爪。鉤槍は細い銛と吹き流し。",
    "c": "低く幅広い鐘。丸い銅の面の中は空、放射状の避雷針の冠、緑青の青銅の胴、地面まで届く羽の裾、体を抱くマント。鉤槍は大きな環の鉤の杖。",
}


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def fit_old(faces, yaw, pitch, size, margin=0.04, side=None):
    side = side or max(size)
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(side, side), margin=margin)
    cv = bg_panel(*size)
    cv.paste(im, ((size[0] - side) // 2, (size[1] - side) // 2))
    return cv


def main():
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod.build, mod.PAL, getattr(mod, "GEAR_PAL", None), body_hint=getattr(mod, "HINT", 128))
        r["label"], r["mod"] = label, mod
        results.append(r)
        print(key, "tris", r["tris"], "spear", r["gtris"], r["size"], r["gsize"])
    F = voxel.font
    canvas = Image.new("RGB", (1600, 3000), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "羽守 (feather_warden) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (ゲームに入れる版の形式)", font=F(26), fill=WH)
    d.text((24, 50), "雷翼の巨神の背の番人。骨の名前・階層・pivot は今の geo と同じ (今のアニメがそのまま動く)。鉤槍は別メッシュで socket_main に付く。", font=F(15), fill=GR)
    d.text((24, 72), "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。光る点は目 (兜の奥) と避雷針の先だけ。羽は軸と羽枝の線をドットで描いてある。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    def colx(i):
        return X0 + i * (COLW + GAP)

    names = ["今 (箱)"] + [o[1] for o in OPTS]
    atl = [combined_atlas(r) for r in results]
    # ---------------- 1 段目: 全身 前 3/4 / 後ろ 3/4
    top, PH = 104, 560
    of, n_ent, n_gear = old_faces()
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((212, 32)):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, PH)
            if i == 0:
                bgp.paste(fit_old(of, yaw, 13, (HW, PH), 0.0, side=410), (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = atl[i - 1]
                im = render_pose(r, big, gl, yoff, {}, yaw, 13, (HW, PH), (0, 23.8, -1), 8.9)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top))
            frame(px, top, HW, PH)
        if i == 0:
            t, col = f"今 (箱 {n_ent} 個 + 鉤槍 {n_gear} 個)", WH
        else:
            r = results[i - 1]
            t, col = f"{r['label']}  {r['tris']} 三角形 (+槍 {r['gtris']})", CY
        d.text((x, top + PH + 6), t, font=F(18), fill=col)
        d.text((x, top + PH + 28), "前 3/4 (左)・後ろ 3/4 (右)", font=F(13), fill=GR)
    # ---------------- 2 段目: 顔のアップ (前・斜め)
    top2 = top + PH + 58
    h2 = 250
    hf, _, _ = old_faces(only_head=True)
    for i in range(4):
        x = colx(i)
        for j, (yaw, pitch) in enumerate(((190, 6), (140, 8))):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, h2)
            if i == 0:
                bgp.paste(voxel.render(hf, yaw=yaw, pitch=pitch, size=(HW, h2), margin=0.04), (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = atl[i - 1]
                im = render_pose(r, big, gl, yoff, {}, yaw, pitch, (HW, h2), r["mod"].HEAD_CENTER, r["mod"].HEAD_SCALE, gear=False)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top2))
            frame(px, top2, HW, h2)
        d.text((x, top2 + h2 + 6), names[i] + " の顔 (正面 / 斜め)", font=F(15), fill=CY if i else WH)
    # ---------------- 3 段目: 急降下 (dive) の姿勢
    top3 = top2 + h2 + 40
    PH3 = 470
    of2, _, _ = old_faces(DIVE)
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((150, 255)):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, PH3)
            if i == 0:
                bgp.paste(fit_old(of2, yaw, 8, (HW, PH3), 0.0, side=330), (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = atl[i - 1]
                im = render_pose(r, big, gl, yoff, DIVE, yaw, 8, (HW, PH3), (0, 17, -6), 7.4)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top3))
            frame(px, top3, HW, PH3)
        d.text((x, top3 + PH3 + 6), "急降下 (dive: 外套を広げて突っ込む)" if i == 0 else names[i], font=F(15), fill=CY if i else WH)
    # ---------------- 4 段目: 鉤槍
    top4 = top3 + PH3 + 40
    GH, gw = 300, 100
    import json as _j
    gg = _j.loads((ASSETS / "geo/gear/hooked_glaive.geo.json").read_text(encoding="utf-8"))
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((90, 0)):
            px = x + j * (gw + 6)
            bgp = bg_panel(gw, GH)
            if i == 0:
                fs = voxel.load_geo(gg, ASSETS / "textures/gear/hooked_glaive.png", ASSETS / "textures/gear/hooked_glaive_glowmask.png")
                bgp.paste(fit_old(fs, 180 if j == 0 else 90, 0, (gw, GH), 0.02), (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = atl[i - 1]
                faces = []
                for f in gear_shifted(r, yoff):
                    g = copy.copy(f)
                    g.verts = np.array([flipxz(p) * np.array([-1, 1, -1]) for p in f.verts])
                    faces.append(g)
                im = render(faces, big, gl, 180 if j == 0 else 90, 0, (gw, GH), (-3, 14, 0), 6.0)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top4))
            frame(px, top4, gw, GH)
        d.text((x, top4 + GH + 6), ("今の鉤槍" if i == 0 else names[i][:1] + " の鉤槍") + " (正面 / 横)", font=F(14), fill=CY if i else WH)
        if i > 0:
            r = results[i - 1]
            at = Image.fromarray(r["gatlas"], "RGBA")
            bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
            bgc.alpha_composite(at)
            asz = r["gsize"] * 2
            ax = x + 2 * (gw + 6)
            canvas.paste(bgc.convert("RGB").resize((asz, asz), Image.NEAREST), (ax, top4))
            frame(ax, top4, asz, asz)
            d.text((ax, top4 + asz + 6), f"槍のアトラス {r['gsize']}x{r['gsize']} (2 倍)", font=F(13), fill=GR)
    # ---------------- 5 段目: アトラスと数字・メモ
    top5 = top4 + GH + 44
    tx = Image.open(ASSETS / "textures/entity/feather_warden.png").convert("RGBA")
    for i in range(1, 4):
        r = results[i - 1]
        x = colx(i)
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        asz = r["size"]
        canvas.paste(bgc.convert("RGB"), (x, top5))
        frame(x, top5, asz, asz)
        d.text((x, top5 + asz + 6), f"{r['label'][:1]} 本体のアトラス {r['size']}x{r['size']} (等倍)", font=F(14), fill=CY)
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        lines = [r["label"], f"本体 {r['tris']} + 槍 {r['gtris']} 三角形",
                 f"頭 {pt('head')} / 胴 {pt('body')} / 腕 {pt('arm_l')}+{pt('forearm_l')} ・ {pt('arm_r')}+{pt('forearm_r')}",
                 f"マント {pt('cloak_l')}×2 / 脚 {pt('leg_l')}×2"]
        yy = top5 + asz + 34
        for k, t in enumerate(lines):
            d.text((x, yy), t, font=F(15 if k else 18), fill=CY if k == 0 else GR)
            yy += 24
        txt = NOTE[r["key"]]
        for s0 in range(0, len(txt), 24):
            d.text((x, yy), txt[s0:s0 + 24], font=F(14), fill=GR)
            yy += 20
    x = colx(0)
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    canvas.paste(bgc.convert("RGB").resize((tx.width // 2, tx.height // 2), Image.NEAREST), (x, top5))
    d.text((x, top5 + tx.height // 2 + 6), f"今のテクスチャ {tx.width}x{tx.height} (半分に縮小)", font=F(14), fill=WH)
    y = top5 + tx.height // 2 + 40
    for t in (f"今: 箱 {n_ent} 個 + 鉤槍の箱 {n_gear} 個", "予算: 普通の敵 約 600 三角形、武器 250 以下",
              "骨: root(Y 180 度) > body > head / cloak_l / cloak_r", "   / arm_l > forearm_l / arm_r > forearm_r", "   > socket_main(空・休め -15 度) / leg_l / leg_r",
              "鉤槍の骨: glaive (受け口 socket_main の局所)"):
        d.text((x, y), t, font=F(13), fill=GR)
        y += 20
    bottom = top5 + max(r["size"] for r in results) + 34 + 4 * 24 + 6 * 20 + 20
    canvas = canvas.crop((0, 0, 1600, min(canvas.size[1], bottom)))
    out = OUT / "lp_feather_warden.png"
    canvas.save(out)
    print("saved", out, canvas.size)


if __name__ == "__main__":
    main()
