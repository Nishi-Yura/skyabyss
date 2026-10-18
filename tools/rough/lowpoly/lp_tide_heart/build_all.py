"""潮殻の心臓 (lp_tide_heart) の低ポリ 3 案を作り、比較画像と manifest を書く。

実行: python tools/rough/lowpoly/lp_tide_heart/build_all.py
出力: docs/review/lowpoly_roughs/lp_tide_heart/{a,b,c}/p{1,2,3}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_tide_heart.png
      docs/review/lowpoly_roughs/lp_tide_heart_manifest.json
"""
from __future__ import annotations

import importlib
import json
import sys

from common import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
YAW_F, YAW_B, PITCH = 200, 20, 13
F = voxel.font
CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [importlib.import_module("opt_" + k).OPT for k in "abc"]

DESC = {
    "a": ("A 真珠の巻貝",
          "今の姿の延長。殻はなめらかな八角断面の渦で、気室の仕切りの稜が横切り、口の縁が広がる。心臓は放射状の溝を持つ丸い真珠で、"
          "溝から核の光が漏れ、紫の血管が這う。桃・橙の珊瑚の枝と紫のいぼ。段階が進むと殻が欠けて青い室が見え、段階 3 で月と滴が回る。"
          "最も今に近く、一番まとまっている。"),
    "b": ("B 珊瑚の殻",
          "殻が十一枚ほどの厚い板に割れ、珊瑚 (橙・桃・紫・青緑) が生えてのしかかる。口の縁は冠の棘、心臓は珊瑚の血管といぼに包まれ、"
          "核の環はイソギンチャクの触手。暖かい色が多く、シルエットが荒く賑やか。段階 2・3 で板が離れて減り、青い室が現れる。"),
    "c": ("C 深海の殻",
          "暗い藍のアンモナイト。菱形の稜が前に立ち、外周に鋭い棘が並ぶ。心臓は七枚の花弁が閉じた蕾で、下の暗い玉が隙間から青く光る"
          " (段階 2 で花弁が開き、段階 3 で反り返って玉が燃える)。縁・割れ目・糸が青く光る、静かに不気味な案。"),
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
    """今の箱のモデルを、新モデルと同じ縮尺・中心で描く (voxel の自動縮尺を逆算して切り出す)。"""
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


def new_shot(res, opt, yaw, pitch, size, center, scale):
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
    n_cubes = {p: cube_counts(p) for p in (1, 2, 3)}

    W = 1600
    canvas = Image.new("RGB", (W, 3300), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((28, 14), "潮殻の心臓 (tide_heart) — 低ポリ非立方体 + ドット絵テクスチャ 3 案", font=F(26), fill=WH)
    d.text((28, 50), "骨の名前・pivot は今の geo (段階 1/2/3 で別。親なし・休めの回転なし) と同じ。三角形 ≤ 2,000 / 段階。"
                     "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。今の箱のモデルは同じ縮尺・同じ角度で並べる。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    cols = 4
    PW = 380
    gap = 14
    x0 = 20

    def colx(i):
        return x0 + i * (PW + gap)

    names = ["今 (箱のモデル)"] + [DESC[o["id"]][0] for o in OPTS]
    y = 90
    PH = 420
    cen, sc = (1, 15.2, 0), 8.9
    for title, yaw, ph, cen_, sc_, PH_ in (("前 3/4 (段階 1)", YAW_F, 1, cen, sc, PH), ("後ろ 3/4 (段階 1)", YAW_B, 1, cen, sc, PH),
                                           ("核・心臓のアップ (段階 3 前)", 190, 3, (0, 15.5, -3.0), 21.0, 380)):
        for i in range(cols):
            pitch = 13 if ph == 1 else 8
            if i == 0:
                im = cur_shot(ph, yaw, pitch, (PW, PH_), cen_, sc_)
            else:
                o = OPTS[i - 1]
                im = new_shot(results[o["id"]][ph], o, yaw, pitch, (PW, PH_), cen_, sc_)
            canvas.paste(im, (colx(i), y))
            frame(colx(i), y, PW, PH_)
            lab = names[i] if i else "今 (箱のモデル)"
            d.text((colx(i), y + PH_ + 6), lab + " — " + title.split(" (")[0], font=F(17), fill=WH if i == 0 else CY)
            if ph == 1 and yaw == YAW_F:
                t2 = (f"箱 {n_cubes[1]:,} 個 = 三角形 {n_cubes[1] * 12:,} 相当") if i == 0 else f"段階 1: {results[OPTS[i - 1]['id']][1]['tris']:,} 三角形"
                d.text((colx(i), y + PH_ + 28), t2, font=F(15), fill=GR)
        y += PH_ + 56
    # --- 段階 1 → 2 → 3
    d.text((20, y), "段階 1 → 2 → 3 (前 3/4。段階 1 = 閉じた殻 / 段階 2 = 殻が割れて青い室が見える / 段階 3 = 殻の欠片・月・滴が回る)", font=F(20), fill=WH)
    y += 34
    TW, TH = 300, 330
    cens = {1: (1, 15.2, 0), 2: (1, 15.2, 0), 3: (-1, 16, 0)}
    scs = {1: 7.4, 2: 7.8, 3: 7.8}
    rows = [("今", None)] + [(DESC[o["id"]][0], o) for o in OPTS]
    for ri, (label, o) in enumerate(rows):
        for ph in (1, 2, 3):
            xx = 20 + (ph - 1) * (TW + 10)
            if o is None:
                im = cur_shot(ph, 205, PITCH, (TW, TH), cens[ph], scs[ph])
            else:
                im = new_shot(results[o["id"]][ph], o, 205, PITCH, (TW, TH), cens[ph], scs[ph])
            canvas.paste(im, (xx, y))
            frame(xx, y, TW, TH)
            d.text((xx + 6, y + 4), f"段階 {ph}", font=F(15), fill=GR)
        tx = 20 + 3 * (TW + 10) + 14
        d.text((tx, y + 6), label, font=F(22), fill=WH if o is None else CY)
        if o is None:
            lines = [f"箱: 段階 1 = {n_cubes[1]} 個 / 段階 2 = {n_cubes[2]} 個 / 段階 3 = {n_cubes[3]} 個",
                     f"三角形相当 (箱 × 12): {n_cubes[1] * 12:,} / {n_cubes[2] * 12:,} / {n_cubes[3] * 12:,}",
                     "", "渦巻の殻 (spiral・aperture_lip) が丸い心臓 (heart) を囲み、", "正面で核 (core_eye) が光る。上に赤い珊瑚 (vessel)。",
                     "段階 2 は殻が割れ、青い室 (chamber)。段階 3 は", "月 (moon_crescent)・滴 (water_drop)・真珠 (orbit)。"]
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
    # --- アトラス (段階 1)
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
        canvas.save(OUT / "lp_tide_heart.png")
        print("saved sheet", canvas.size)
        manifest = {
            "id": "lp_tide_heart",
            "short": "潮殻の心臓 低ポリ",
            "kind": "低ポリ 3案",
            "title": "潮殻の心臓 — 低ポリ非立方体 + ドット絵 3 案 (最終ボス・段階 1/2/3)",
            "sheets": ["lowpoly_roughs/lp_tide_heart.png"],
            "extra": ("統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の心臓。骨名・pivot は今の geo (段階 1/2/3。親なし・休めの回転なし) のまま、"
                      "今の intro/death を含むアニメがそのまま動く。三角形 (段階 1 / 2 / 3): "
                      + " / ".join(f"{DESC[o['id']][0].split()[0]} {results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}・{results[o['id']][3]['tris']:,}"
                                   for o in OPTS)
                      + f"。今は箱 {n_cubes[1]}/{n_cubes[2]}/{n_cubes[3]} 個 (面 数千)。上限は 2,000 三角形/段階。"
                        "メッシュは lowpoly_roughs/lp_tide_heart/{a,b,c}/p{1,2,3}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": DESC[o["id"]][0], "desc": DESC[o["id"]][1]} for o in OPTS},
        }
        (OUT / "lp_tide_heart_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
