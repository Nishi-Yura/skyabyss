"""肋の射手: 今のモデルと低ポリ 3 案の比較画像を作り、メッシュ・アトラスも書き出す。

実行: python tools/rough/lowpoly/rib_archer/compare.py
出力: docs/review/lowpoly_roughs/rib_archer/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク・弓のメッシュとアトラス)
      docs/review/lowpoly_roughs/rib_archer.png
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
OPTS = [("a", "A 巻き角の長頭", opt_a), ("b", "B 棘背の猫背", opt_b), ("c", "C ひげ板の頭巾", opt_c)]
COLW, GAP, X0 = 376, 16, 24
HW = 184


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_render(faces, yaw, pitch, size, margin=0.0):
    return voxel.render(faces, yaw=yaw, pitch=pitch, size=size, margin=margin)


def fit_old(faces, yaw, pitch, size, want_h, margin=0.04):
    """今のモデルを正方形に描いて中央を切り出す (縮尺は全身の高さで決まる)。"""
    side = max(size)
    im = old_render(faces, yaw, pitch, (side, side), margin)
    x0 = (side - size[0]) // 2
    y0 = (side - size[1]) // 2
    return im.crop((x0, y0, x0 + size[0], y0 + size[1]))


def main():
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod.build, mod.PAL, getattr(mod, "BOW_PAL", None), body_hint=getattr(mod, "HINT", 128))
        r["label"] = label
        r["mod"] = mod
        results.append(r)
        print(key, "tris", r["data"]["triangle_count"], "bow", r["bdata"]["triangle_count"], r["size"], r["bsize"])
    F = voxel.font
    PH = 560
    canvas = Image.new("RGB", (1600, 2600), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "肋の射手 (rib_archer) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (ゲームに入れる版の形式)", font=F(26), fill=WH)
    d.text((24, 50), "骨の名前・階層・pivot は今の geo と同じ (今のアニメーションがそのまま動く)。弓は別メッシュで socket_off に付き、"
                     "string_up / string_lo の骨も同じ。テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    def colx(i):
        return X0 + i * (COLW + GAP)

    # ---- 見出し列: 今 / A / B / C
    top = 96
    names = ["今 (箱)"] + [o[1] for o in OPTS]
    # ---------------- 1 段目: 全身 前 3/4・後ろ 3/4
    of, n_ent, n_bow = old_faces()
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((212, 32)):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, PH)
            if i == 0:
                im = fit_old(of, yaw, 13, (HW, PH), 470)
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                im = render_pose(r, big, gl, yoff, {}, yaw, 13, (HW, PH), (0, 18.2, -0.5), 14.8)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top))
            frame(px, top, HW, PH)
        if i == 0:
            t = f"今 (箱 {n_ent} 個 + 弓 {n_bow} 個)"
            col = WH
        else:
            r = results[i - 1]
            t = f"{r['label']}  {r['data']['triangle_count']} 三角形 (+弓 {r['bdata']['triangle_count']})"
            col = CY
        d.text((x, top + PH + 6), t, font=F(18), fill=col)
        d.text((x, top + PH + 28), "前 3/4 (左)・後ろ 3/4 (右)", font=F(13), fill=GR)
    # ---------------- 2 段目: 頭のアップ (前・横)
    top2 = top + PH + 58
    h2 = 250
    hf, _, _ = old_faces(only_skull=True)
    for i in range(4):
        x = colx(i)
        for j, (yaw, pitch) in enumerate(((200, 8), (140, 8))):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, h2)
            if i == 0:
                im = old_render(hf, yaw, pitch, (HW, h2), 0.04)
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                cz = r["mod"].HEAD_CENTER
                im = render_pose(r, big, gl, yoff, {}, yaw, pitch, (HW, h2), cz, r["mod"].HEAD_SCALE, body=True, bow=False)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top2))
            frame(px, top2, HW, h2)
        d.text((x, top2 + h2 + 6), names[i] + " の頭 (前 / 斜め)", font=F(15), fill=CY if i else WH)
    # ---------------- 3 段目: 撃つ姿勢 (shoot の 0.45 秒)
    top3 = top2 + h2 + 40
    PH3 = 520
    of2, _, _ = old_faces(SHOOT, SHOOT_STRING)
    for i in range(4):
        x = colx(i)
        for j, yaw in enumerate((150, 250)):
            px = x + j * (HW + 8)
            bgp = bg_panel(HW, PH3)
            if i == 0:
                im = fit_old(of2, yaw, 10, (HW, PH3), 440)
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                im = render_pose(r, big, gl, yoff, SHOOT, yaw, 10, (HW, PH3), (0, 18.8, -2.5), 14.0, SHOOT_STRING)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top3))
            frame(px, top3, HW, PH3)
        d.text((x, top3 + PH3 + 6), "撃つ (shoot の 0.45 秒: 弓を引き絞る)" if i == 0 else names[i], font=F(15), fill=CY if i else WH)
    # ---------------- 4 段目: 弓 (休め・引き絞り) と弓のアトラス
    top4 = top3 + PH3 + 40
    BH = 300
    bwid = 100
    bowold, _, _ = old_faces()
    for i in range(4):
        x = colx(i)
        for j in range(2):
            px = x + j * (bwid + 6)
            bgp = bg_panel(bwid, BH)
            if i == 0:
                g, _, _ = old_faces({"socket_off": (-20.0, 0, -9.0)} if False else None)
                # 弓だけ: 弓の骨の面 (本体を除く) は取れないので、全体から弓の周りを切り出す代わりに geo を直接読む
                import json as _j, copy as _c
                bg = _j.loads((ASSETS / "geo/gear/rib_archer_bow.geo.json").read_text(encoding="utf-8"))
                if j == 1:
                    for b in bg["minecraft:geometry"][0]["bones"]:
                        if b["name"] == "string_up":
                            b["rotation"] = [12.8, 0, 0]
                        if b["name"] == "string_lo":
                            b["rotation"] = [-12.8, 0, 0]
                fs = voxel.load_geo(bg, ASSETS / "textures/gear/rib_archer_bow.png")
                im = fit_old(fs, 90, 0, (bwid, BH), 0, 0.04)
                bgp.paste(im, (0, 0))
            else:
                r = results[i - 1]
                big, gl, yoff = combined_atlas(r)
                faces = bow_faces_shifted(r, yoff)
                import copy as _c
                fl = []
                for f in faces:
                    g = _c.copy(f)
                    g.verts = np.array([flipxz(p) * np.array([-1, 1, -1]) for p in f.verts])   # 弓の局所座標のまま見る (x, z は元の向き)
                    fl.append(g)
                st = {"string_up": (12.8, 0, 0), "string_lo": (-12.8, 0, 0)} if j == 1 else {}
                fl2 = []
                for f in faces:
                    g = _c.copy(f)
                    pts = []
                    for p in f.verts:
                        q = np.asarray(p, float)
                        if f.bone in st:
                            pv = np.array(BOW_BONES[f.bone][1])
                            q = rot_x(st[f.bone][0]) @ (q - pv) + pv
                        pts.append(q)
                    g.verts = np.array(pts)
                    # 法線を作り直す
                    kk = len(g.verts)
                    n = np.cross(g.verts[2] - g.verts[0], g.verts[3 % kk] - g.verts[1]) if kk == 4 else \
                        np.cross(g.verts[1] - g.verts[0], g.verts[2] - g.verts[0])
                    n = nrm(n)
                    if np.dot(n, f.normal) < 0:
                        n = -n
                    g.normal = n
                    fl2.append(g)
                im = render(fl2, big, gl, 90, 0, (bwid, BH), (0, 0, -0.5), 17.5)
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (px, top4))
            frame(px, top4, bwid, BH)
        d.text((x, top4 + BH + 6), ("今の弓 " if i == 0 else names[i][:1] + " の弓 ") + "(休め / 引き絞り・横から)", font=F(14), fill=CY if i else WH)
        if i > 0:
            r = results[i - 1]
            at = Image.fromarray(r["batlas"], "RGBA")
            bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
            bgc.alpha_composite(at)
            asz = r["bsize"] * 2
            ax = x + 2 * (bwid + 6)
            canvas.paste(bgc.convert("RGB").resize((asz, asz), Image.NEAREST), (ax, top4))
            frame(ax, top4, asz, asz)
            d.text((ax, top4 + asz + 6), f"弓のアトラス {r['bsize']}×{r['bsize']} (2 倍)", font=F(13), fill=GR)
    # ---------------- 5 段目: 本体のアトラスと数字・設計メモ
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
        d.text((x, top5 + asz + 6), f"{r['label'][:1]} 本体のアトラス {r['size']}×{r['size']} (2 倍表示)", font=F(14), fill=CY)
    # 今のモデルの列: 今のテクスチャ
    x = colx(0)
    tx = Image.open(ASSETS / "textures/entity/rib_archer.png").convert("RGBA")
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    canvas.paste(bgc.convert("RGB").resize((tx.width * 1, tx.height * 1), Image.NEAREST), (x, top5))
    d.text((x, top5 + tx.height + 6), f"今のテクスチャ {tx.width}×{tx.height}", font=F(14), fill=WH)
    y = top5 + tx.height + 40
    for t in (f"今: 箱 {n_ent} 個 (面 882) + 弓の箱 {n_bow} 個", "予算: 普通の敵 約 600 三角形、弓は別",
              "骨: root(Y 180 度) > spine > skull / arm_l >", "   forearm_l > socket_off(空) / arm_r > forearm_r", "   / leg_l / leg_r",
              "弓の骨: bow > string_up / string_lo (draw アニメの弦が動く)"):
        d.text((x, y), t if len(t) < 40 else t, font=F(13), fill=GR)
        y += 20
    # 数字の表
    ty = top5 + 270
    ex = {"a": "細長く縦の輪郭。頭は前へ長い鯨の頭骨、後ろへ流れる長い角 2 本、逆関節の脚。弓は鯨の肋骨を削った反りの強い刃の弓 (外へ棘)。",
          "b": "前かがみの獣。短い嘴と横へ張る頬の棘、背骨に沿う棘の列、前へ爪のように出る肋、尻尾。弓は棘だらけの角を曲げたような弓。",
          "c": "ひげ板を段に重ねた尖った頭巾の奥に光る 2 点。肩の重ね板・前垂れ・背のマントもひげ板。細く真っ直ぐな脚。弓は薄い積層の長弓。"}
    for i in range(1, 4):
        r = results[i - 1]
        x = colx(i)
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        lines = [f"{r['label']}",
                 f"本体 {r['data']['triangle_count']} + 弓 {r['bdata']['triangle_count']} 三角形",
                 f"頭 {pt('skull')} / 胴 {pt('spine')} / 左腕 {pt('arm_l')}+{pt('forearm_l')} / 右腕 {pt('arm_r')}+{pt('forearm_r')} / 脚 {pt('leg_l')}×2"]
        yy = ty if False else top5 + r["size"] * 2 + 34
        for k, t in enumerate(lines):
            d.text((x, yy), t, font=F(15 if k else 18), fill=CY if k == 0 else GR)
            yy += 24
        # 説明 (折り返し)
        txt = ex[r["key"]]
        for s0 in range(0, len(txt), 24):
            d.text((x, yy), txt[s0:s0 + 24], font=F(14), fill=GR)
            yy += 20
    out = OUT / "rib_archer.png"
    canvas = canvas.crop((0, 0, 1600, min(canvas.size[1], top5 + 500)))
    canvas.save(out)
    print("saved", out, canvas.size)


if __name__ == "__main__":
    main()
