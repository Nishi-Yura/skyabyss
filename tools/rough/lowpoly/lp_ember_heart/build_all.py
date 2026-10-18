"""余燼の心臓 (lp_ember_heart) の低ポリ 3 案を作り、比較画像と manifest を書く。

実行: python tools/rough/lowpoly/lp_ember_heart/build_all.py
出力: docs/review/lowpoly_roughs/lp_ember_heart/{a,b,c}/p{1,2,3}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_ember_heart.png
      docs/review/lowpoly_roughs/lp_ember_heart_manifest.json
"""
from __future__ import annotations

import importlib
import json

from common import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
YAW_F, YAW_B = 200, 20
F = voxel.font
CY, GR, WH = (255, 190, 110), (170, 164, 160), (240, 232, 220)
OPTS = [importlib.import_module("opt_" + k).OPT for k in "abc"]

DESC = {
    "a": ("A 玄武岩の板割れ",
          "採用済みの「B 板割れ」の素直な低ポリ化。丸い玉を、黒い玄武岩の板 (面取りした低い屋根) が隙間なく覆い、細い割れ目の底が熾の橙に光る。"
          "段階 2 は上が熔け落ちて板の椀になり、割れ目が広がって黄に白熱、むき出しの心臓と核、熔けた垂れが下がる。"
          "段階 3 は白い灰の板に穴があき、割れ目は鈍い赤、中に小さく暗い残り火。最も今に近く、まとまっている。"),
    "b": ("B ずれた熔岩板",
          "少数の大きな板が、傾きと段差をつけて浮いた殻。割れ目が広く深く、板の側面の下ほど熾に光る (板の下に熔岩が見える)。"
          "焼けた赤褐色のスコリアで、一枚一枚が重く見える。段階 2 は縁の板が外へめくれて椀になり、段階 3 は灰になった板がずれて大きな穴をあける。"
          "三角形が最も少なく軽い (約 520 / 1,090 / 1,040)。"),
    "c": ("C 黒曜の鱗",
          "小さな板を多く並べ、それぞれが尖った頂点を持つ結晶のうろこにした殻。継ぎ目の角から細い棘が立ち、割れ目は細く明るい。"
          "赤みのある黒いガラス質で、面の縁がきらりと光る。段階 2 は椀の縁に鱗が逆立ち、段階 3 は灰に曇って棘が折れる。"
          "最も細かく鋭い案で、三角形は一番多い (約 1,570 / 1,570 / 1,660)。"),
}


def panel_bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


_cur = {}


def cur_faces(phase):
    if phase not in _cur:
        _cur[phase] = voxel.load_geo(GEO / f"{SUBJ}_p{phase}.geo.json", ASSETS / f"textures/entity/{SUBJ}_p{phase}.png",
                                     ASSETS / f"textures/entity/{SUBJ}_p{phase}_glowmask.png")
    return _cur[phase]


def cur_shot(phase, yaw, pitch, size, center, scale):
    faces = cur_faces(phase)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    Vm = Rx @ Ry
    pts = []
    for f in faces:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(Vm @ (f.origin + a * f.eu + b * f.ev))
    pts = np.array(pts)
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1], 1e-6)
    cxs, cys = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    big = (size[0] * 2, size[1] * 2)
    m = (1 - scale * span / min(big)) / 2
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=big, margin=m)
    pc = Vm @ np.asarray(center, float)
    px = (pc[0] - cxs) * scale + big[0] / 2
    py = -(pc[1] - cys) * scale + big[1] / 2
    box = (int(round(px - size[0] / 2)), int(round(py - size[1] / 2)), int(round(px + size[0] / 2)), int(round(py + size[1] / 2)))
    return im.crop(box).convert("RGB")


def new_shot(res, yaw, pitch, size, center, scale):
    im = render_ember(res["faces"], res["atlas"], res["glow"], yaw, pitch, size, center, scale)
    bg = panel_bg(*size).convert("RGBA")
    bg.alpha_composite(im)
    return bg.convert("RGB")


def main(save=True):
    results = {}
    for opt in OPTS:
        results[opt["id"]] = {}
        for ph in (1, 2, 3):
            r = build_phase(opt, ph, OUTDIR / opt["id"], save=save)
            results[opt["id"]][ph] = r
            print(opt["id"], ph, r["tris"], "tris, atlas", r["size"])
            assert r["tris"] <= 2000, "tri budget"
    n_cubes = {p: cube_counts(p) for p in (1, 2, 3)}

    W = 1600
    canvas = Image.new("RGB", (W, 3600), (16, 14, 18))
    d = ImageDraw.Draw(canvas)
    d.text((28, 14), "余燼の心臓 (ember_heart) — 低ポリ非立方体 + ドット絵テクスチャ 3 案", font=F(26), fill=WH)
    d.text((28, 50), "骨の名前・pivot は今の geo (段階 1/2/3 で別。親なし・pivot (0,16,0)・休めの回転なし) と同じ。三角形 ≤ 2,000 / 段階。", font=F(15), fill=GR)
    d.text((28, 72), "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。今の箱のモデルは同じ縮尺・同じ角度で並べる。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(66, 56, 60))

    cols, PW, gap, x0 = 4, 380, 14, 20
    colx = lambda i: x0 + i * (PW + gap)
    names = ["今 (箱のモデル)"] + [DESC[o["id"]][0] for o in OPTS]
    y = 106
    for title, yaw, ph, cen_, sc_, PH_, pitch in (
            ("前 3/4 (段階 1)", YAW_F, 1, (0, 15.5, 0), 13.0, 400, 12),
            ("後ろ 3/4 (段階 1)", YAW_B, 1, (0, 15.5, 0), 13.0, 400, 12),
            ("核・心臓のアップ (段階 2)", 195, 2, (0, 16, -3), 21.0, 380, 8)):
        for i in range(cols):
            if i == 0:
                im = cur_shot(ph, yaw, pitch, (PW, PH_), cen_, sc_)
            else:
                im = new_shot(results[OPTS[i - 1]["id"]][ph], yaw, pitch, (PW, PH_), cen_, sc_)
            canvas.paste(im, (colx(i), y))
            frame(colx(i), y, PW, PH_)
            d.text((colx(i), y + PH_ + 6), names[i] + " — " + title.split(" (")[0], font=F(17), fill=WH if i == 0 else CY)
            if ph == 1 and yaw == YAW_F:
                t2 = (f"箱 {n_cubes[1]:,} 個 = 三角形 {n_cubes[1] * 12:,} 相当") if i == 0 else f"段階 1: {results[OPTS[i - 1]['id']][1]['tris']:,} 三角形"
                d.text((colx(i), y + PH_ + 28), t2, font=F(15), fill=GR)
            if ph == 2:
                t2 = (f"箱 {n_cubes[2]:,} 個 = 三角形 {n_cubes[2] * 12:,} 相当") if i == 0 else f"段階 2: {results[OPTS[i - 1]['id']][2]['tris']:,} 三角形"
                d.text((colx(i), y + PH_ + 28), t2, font=F(15), fill=GR)
        y += PH_ + 56
    d.text((20, y), "段階 1 → 2 → 3 (前 3/4。段階 1 = 灰と玄武岩の殻に熾の割れ目 / 段階 2 = 殻の上が熔け落ち、白熱した心臓がむき出し / 段階 3 = 灰になり、暗い残り火)", font=F(20), fill=WH)
    y += 34
    TW, TH = 300, 330
    cens = {1: (0, 15.5, 0), 2: (0, 14.5, 0), 3: (0, 17, 0)}
    scs = {1: 9.0, 2: 7.0, 3: 7.0}
    rows = [("今", None)] + [(DESC[o["id"]][0], o) for o in OPTS]
    for label, o in rows:
        for ph in (1, 2, 3):
            xx = 20 + (ph - 1) * (TW + 10)
            if o is None:
                im = cur_shot(ph, 205, 11, (TW, TH), cens[ph], scs[ph])
            else:
                im = new_shot(results[o["id"]][ph], 205, 11, (TW, TH), cens[ph], scs[ph])
            canvas.paste(im, (xx, y))
            frame(xx, y, TW, TH)
            d.text((xx + 6, y + 4), f"段階 {ph}", font=F(15), fill=GR)
        tx = 20 + 3 * (TW + 10) + 14
        d.text((tx, y + 6), label, font=F(22), fill=WH if o is None else CY)
        if o is None:
            lines = [f"箱: 段階 1 = {n_cubes[1]:,} 個 / 段階 2 = {n_cubes[2]:,} 個 / 段階 3 = {n_cubes[3]:,} 個",
                     f"三角形相当 (箱 × 12): {n_cubes[1] * 12:,} / {n_cubes[2] * 12:,} / {n_cubes[3] * 12:,}", "",
                     "段階 1: 丸い玄武岩の殻 (crust) と、板の割れ目に光る", "底 (crust_glow)。殻の中に心臓と核がある。",
                     "段階 2: 殻の上が熔け、椀 (crust) が残る。むき出しの", "心臓 (heart)・核 (core_eye)・血管 (vessel)。垂れ (drips)。",
                     "段階 3: 白い灰の殻 (ash_shell) に穴。小さな残り火の核。"]
        else:
            rr = results[o["id"]]
            lines = [f"三角形: 段階 1 = {rr[1]['tris']:,} / 段階 2 = {rr[2]['tris']:,} / 段階 3 = {rr[3]['tris']:,}",
                     f"アトラス: {rr[1]['size']}² / {rr[2]['size']}² / {rr[3]['size']}² (1 ブロック 32 テクセル)"]
            desc = DESC[o["id"]][1]
            line, wrapped = "", []
            for ch in desc:
                line += ch
                if len(line) >= 34:
                    wrapped.append(line)
                    line = ""
            if line:
                wrapped.append(line)
            lines += [""] + wrapped
        ty = y + 40
        for t in lines:
            d.text((tx, ty), t, font=F(15), fill=GR)
            ty += 22
        y += TH + 12
    y += 10
    d.text((20, y), "アトラス (段階 1。2 倍表示・最近傍)", font=F(20), fill=WH)
    y += 34
    xx = 20
    ay = y
    for o in OPTS:
        rr = results[o["id"]][1]
        at = Image.fromarray(rr["atlas"], "RGBA")
        used = np.nonzero(rr["atlas"][..., 3].any(1))[0].max() + 2
        at = at.crop((0, 0, at.size[0], min(at.size[1], used)))
        bg = Image.new("RGBA", at.size, (40, 36, 44, 255))
        bg.alpha_composite(at)
        k = 2 if rr["size"] <= 128 else 1
        szw, szh = bg.size[0] * k, bg.size[1] * k
        canvas.paste(bg.convert("RGB").resize((szw, szh), Image.NEAREST), (xx, y))
        frame(xx, y, szw, szh)
        d.text((xx, y + szh + 4), f"{DESC[o['id']][0]} {rr['size']}×{rr['size']} (使用部分{'・2 倍' if k == 2 else ''})", font=F(15), fill=CY)
        xx += szw + 20
        ay = max(ay, y + szh + 30)
    canvas = canvas.crop((0, 0, W, min(canvas.size[1], ay)))
    if save:
        canvas.save(OUT / "lp_ember_heart.png")
        print("saved sheet", canvas.size)
        tri = " / ".join(f"{DESC[o['id']][0].split()[0]} {results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}・{results[o['id']][3]['tris']:,}"
                         for o in OPTS)
        manifest = {
            "id": "lp_ember_heart",
            "short": "余燼の心臓 低ポリ",
            "kind": "低ポリ 3案",
            "title": "余燼の心臓 — 低ポリ非立方体 + ドット絵 3 案 (最終ボス・段階 1/2/3)",
            "sheets": ["lowpoly_roughs/lp_ember_heart.png"],
            "extra": ("統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の心臓。骨名・pivot は今の geo (段階 1/2/3。親なし・pivot (0,16,0)・休めの回転なし) のまま、"
                      "intro/death を含む今のアニメがそのまま動く。余燼の言葉 (灰の殻・玄武岩の板・ボロノイの板割れ・割れ目の床が光る・火の粉・遅くなる鼓動・冷え) で、"
                      "採用済みの「B 板割れ」を低ポリにした。段階ごとの進み (1 細い橙の割れ目 → 2 広く黄に白熱 → 3 灰で鈍い赤) と体力バーの段階を保つ。"
                      f"三角形 (段階 1 / 2 / 3): {tri}。今は箱 {n_cubes[1]:,}/{n_cubes[2]:,}/{n_cubes[3]:,} 個。上限は 2,000 三角形/段階。"
                      "メッシュは lowpoly_roughs/lp_ember_heart/{a,b,c}/p{1,2,3}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": DESC[o["id"]][0], "desc": DESC[o["id"]][1]} for o in OPTS},
        }
        (OUT / "lp_ember_heart_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
