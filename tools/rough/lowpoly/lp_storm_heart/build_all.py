"""雷翼の心臓 (lp_storm_heart) の低ポリ 3 案を作り、比較画像と manifest を書く。

実行: python tools/rough/lowpoly/lp_storm_heart/build_all.py
出力: docs/review/lowpoly_roughs/lp_storm_heart/{a,b,c}/p{1,2,3}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_storm_heart.png
      docs/review/lowpoly_roughs/lp_storm_heart_manifest.json
"""
from __future__ import annotations

import importlib
import json

from common import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
YAW_F, YAW_B = 200, 20
F = voxel.font
CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [importlib.import_module("opt_" + k).OPT for k in "abc"]

DESC = {
    "a": ("A 風切りの殻",
          "嵐の傷 (採用済みの C) の素直な低ポリ化。丸い石の心臓を、銅の軸と羽枝の縞を持つ大きな羽根の板が抱き、先は切れ込み、"
          "石の傷から稲妻が漏れる。段階 2 で翼が開いて上に冠の羽、段階 3 で羽が抜け、骨と光の網が残る。最も今に近く、まとまっている。"),
    "b": ("B 避雷針の骨籠",
          "銅 (緑青の吹いた避雷針) の言葉。心臓は銅の輪の籠に入り、大動脈の先に静電気の玉が灯る。翼は銅の軸に櫛の歯の羽枝が並ぶ、"
          "細く軽いシルエット。段階 3 は輪が割れ、骨の枠に銅の棒と稲妻が走る。暗い鋼・緑青・電光の渋い案。"),
    "c": ("C 帯電の羽冠",
          "暗い藍の面取りの心臓から、帯電した針羽が四方へ逆立ち、先が光る (段階ごとに数・長さ・灯りが増す)。翼は稲妻の形に折れた"
          "幅広の板 (嵐の布) で、縁は深く裂ける。段階 3 は稲妻の骨と太い光の網に針が刺さる。最も激しく、一番光る案。"),
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
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
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
    im = lpkit.render(res["faces"], res["atlas"], res["glow"], yaw, pitch, size, center, scale)
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
    canvas = Image.new("RGB", (W, 3600), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((28, 14), "雷翼の心臓 (storm_heart) — 低ポリ非立方体 + ドット絵テクスチャ 3 案", font=F(26), fill=WH)
    d.text((28, 50), "骨の名前・pivot は今の geo (段階 1/2/3 で別。親なし・休めの回転なし) と同じ。三角形 ≤ 2,000 / 段階。"
                     "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。今の箱のモデルは同じ縮尺・同じ角度で並べる。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    cols, PW, gap, x0 = 4, 380, 14, 20
    colx = lambda i: x0 + i * (PW + gap)
    names = ["今 (箱のモデル)"] + [DESC[o["id"]][0] for o in OPTS]
    y = 90
    for title, yaw, ph, cen_, sc_, PH_, pitch in (
            ("前 3/4 (段階 1)", YAW_F, 1, (0, 17, 0.5), 12.5, 420, 12),
            ("後ろ 3/4 (段階 1)", YAW_B, 1, (0, 17, 0.5), 12.5, 420, 12),
            ("核・心臓のアップ (段階 3)", 190, 3, (0, 18, -4.0), 20.0, 380, 8)):
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
        y += PH_ + 56
    d.text((20, y), "段階 1 → 2 → 3 (前 3/4。段階 1 = 畳んだ翼 / 段階 2 = 翼が開き冠の羽が立つ / 段階 3 = 羽が抜けて骨と稲妻の網)", font=F(20), fill=WH)
    y += 34
    TW, TH = 300, 330
    cens = {1: (0, 17, 0.5), 2: (0, 30, 0), 3: (0, 20, 0)}
    scs = {1: 8.6, 2: 6.3, 3: 6.8}
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
            lines = [f"箱: 段階 1 = {n_cubes[1]} 個 / 段階 2 = {n_cubes[2]} 個 / 段階 3 = {n_cubes[3]} 個",
                     f"三角形相当 (箱 × 12): {n_cubes[1] * 12:,} / {n_cubes[2] * 12:,} / {n_cubes[3] * 12:,}", "",
                     "段階 1: 丸い心臓 (heart) を羽根の輪 (wing_l/r) が", "抱き、上に銅の大動脈 (vessel)、正面で核 (core) が光る。",
                     "段階 2: 翼が横へ開き、上に冠の羽 (crest)。心臓が上がる。",
                     "段階 3: 羽が抜け、指の骨 (finger)・節 (joint)・", "稲妻の網 (web)・羽の欠片 (shed) が残る。"]
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
        bg = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bg.alpha_composite(at)
        szw, szh = bg.size[0] * 2, bg.size[1] * 2
        canvas.paste(bg.convert("RGB").resize((szw, szh), Image.NEAREST), (xx, y))
        frame(xx, y, szw, szh)
        d.text((xx, y + szh + 4), f"{DESC[o['id']][0]} {rr['size']}×{rr['size']} (使用部分)", font=F(15), fill=CY)
        xx += szw + 20
        ay = max(ay, y + szh + 30)
    canvas = canvas.crop((0, 0, W, min(canvas.size[1], ay)))
    if save:
        canvas.save(OUT / "lp_storm_heart.png")
        print("saved sheet", canvas.size)
        tri = " / ".join(f"{DESC[o['id']][0].split()[0]} {results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}・{results[o['id']][3]['tris']:,}"
                         for o in OPTS)
        manifest = {
            "id": "lp_storm_heart",
            "short": "雷翼の心臓 低ポリ",
            "kind": "低ポリ 3案",
            "title": "雷翼の心臓 — 低ポリ非立方体 + ドット絵 3 案 (最終ボス・段階 1/2/3)",
            "sheets": ["lowpoly_roughs/lp_storm_heart.png"],
            "extra": ("統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の心臓。骨名・pivot は今の geo (段階 1/2/3。親なし・休めの回転なし) のまま、"
                      "intro/death を含む今のアニメがそのまま動く。雷翼の言葉 (嵐・羽軸と羽枝・静電気・銅・稲妻の傷) で、段階ごとの灯りの増加を保つ。"
                      f"三角形 (段階 1 / 2 / 3): {tri}。今は箱 {n_cubes[1]}/{n_cubes[2]}/{n_cubes[3]} 個。上限は 2,000 三角形/段階。"
                      "メッシュは lowpoly_roughs/lp_storm_heart/{a,b,c}/p{1,2,3}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": DESC[o["id"]][0], "desc": DESC[o["id"]][1]} for o in OPTS},
        }
        (OUT / "lp_storm_heart_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
