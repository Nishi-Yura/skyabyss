"""岩鯨の心臓 (lp_whale_heart) の低ポリ 3 案を作り、比較画像と manifest を書く。

実行: python tools/rough/lowpoly/lp_whale_heart/build_all.py
出力: docs/review/lowpoly_roughs/lp_whale_heart/{a,b,c}/p{1,2,3}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_whale_heart.png
      docs/review/lowpoly_roughs/lp_whale_heart_manifest.json
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
    "a": ("A 地層の板",
          "今の殻の延長。八枚の楔を 4 段の斜めの板 (庇が出る) に作り直し、段の境に影が落ちる。冠は面取りした八角、棘は骨色の細い牙、"
          "心臓は解剖図の低ポリ心臓 (心尖が細く、上の割れ目、大動脈と肺動脈)。青灰の石 + 苔。最も今に近く、一番まとまっている。"),
    "b": ("B 割れ岩",
          "砕けかけた大岩。板の縁を斜めに落とした不揃いな多面体で、板のあいだに太い溶岩の溝 (継ぎ目) が走る。冠は荒い岩の帽子と立った欠片、"
          "棘は黒い結晶、心臓は面ごとに平らに光る赤い宝石の塊と突き出る結晶。焦げた鉄色の岩 + 錆。最も荒々しく、シルエットが強い。"),
    "c": ("C 眼球",
          "元の参考画像 (青白い眼球・赤い棘・細かい虹彩) に寄せた案。ほぼ真球の白い石の殻に赤い棘が生え、正面の継ぎ目はレンズ形の光る瞳孔、"
          "まわりに虹彩を描く (段階 2 で光る)。心臓は血管の走る眼球で、段階 3 は核が虹彩になる。一番キャラクター性が高いが、石の殻の面影は薄い。"),
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
    im = render_glow(res["world"], res["atlas"], res["glow"], yaw, pitch, size, center, scale, gcol=opt["gcol"])
    bg = panel_bg(*size)
    bg.paste(im, (0, 0), im)
    return bg


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
    canvas = Image.new("RGB", (W, 3600), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((28, 14), "岩鯨の心臓 (stone_whale_heart) — 低ポリ非立方体 + ドット絵テクスチャ 3 案", font=F(26), fill=WH)
    d.text((28, 50), "骨の名前・階層・pivot・休めの回転は今の geo (段階 1/2/3 で別) と同じ。三角形 ≤ 2,000 / 段階。テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。"
                     "今の箱のモデルは同じ縮尺・同じ角度で並べる。", font=F(15), fill=GR)

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
    # --- 前 3/4 (段階 1)
    PH = 520
    cen, sc = (0, 13.5, 0), 15.0
    for i in range(cols):
        if i == 0:
            im = cur_shot(1, YAW_F, PITCH, (PW, PH), cen, sc)
        else:
            o = OPTS[i - 1]
            im = new_shot(results[o["id"]][1], o, YAW_F, PITCH, (PW, PH), cen, sc)
        canvas.paste(im, (colx(i), y))
        frame(colx(i), y, PW, PH)
        lab = names[i] if i else f"今 (箱 {n_cubes[1]} 個 = 三角形 {n_cubes[1] * 12} 相当)"
        d.text((colx(i), y + PH + 6), lab + " — 前 3/4", font=F(18), fill=WH if i == 0 else CY)
        if i:
            d.text((colx(i), y + PH + 30), f"段階 1: {results[OPTS[i - 1]['id']][1]['tris']} 三角形", font=F(15), fill=GR)
    y += PH + 64
    # --- 後ろ 3/4 (段階 1)
    for i in range(cols):
        if i == 0:
            im = cur_shot(1, YAW_B, PITCH, (PW, PH), cen, sc)
        else:
            o = OPTS[i - 1]
            im = new_shot(results[o["id"]][1], o, YAW_B, PITCH, (PW, PH), cen, sc)
        canvas.paste(im, (colx(i), y))
        frame(colx(i), y, PW, PH)
        d.text((colx(i), y + PH + 6), (names[i] if i else "今") + " — 後ろ 3/4", font=F(18), fill=WH if i == 0 else CY)
    y += PH + 40
    # --- 核のアップ (段階 3) と 段階 2 の心臓
    CH = 380
    cen3, sc3 = (0, 13.5, -3.0), 21.0
    for i in range(cols):
        if i == 0:
            im = cur_shot(3, 190, 8, (PW, CH), cen3, sc3)
        else:
            o = OPTS[i - 1]
            im = new_shot(results[o["id"]][3], o, 190, 8, (PW, CH), cen3, sc3)
        canvas.paste(im, (colx(i), y))
        frame(colx(i), y, PW, CH)
        d.text((colx(i), y + CH + 6), (names[i] if i else "今") + " — 核・心臓のアップ (段階 3 前)", font=F(18), fill=WH if i == 0 else CY)
    y += CH + 44
    # --- 段階 1 → 2 → 3
    d.text((20, y), "段階 1 → 2 → 3 (前 3/4。段階 2 は板 4・5 が剥がれ、段階 3 は殻が椀になり心臓が鎖で吊られる)", font=F(20), fill=WH)
    y += 34
    TW, TH = 300, 390
    cens = {1: (0, 13.5, 0), 2: (0, 14.5, 0), 3: (0, 14.5, 0)}
    scs = {1: 11.2, 2: 10.6, 3: 10.8}
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
                     "(隠れた面を除くと 5,000〜6,000 面。docs/design/15 の 4.2)", "", "石の殻 = 板 8 枚 + 継ぎ目 8 本の階段状の球。",
                     "段階 2 は板 4・5 が無く心臓が見える。", "段階 3 は椀・鎖・骨の枠・爪。"]
        else:
            rr = results[o["id"]]
            lines = [f"三角形: 段階 1 = {rr[1]['tris']:,} / 段階 2 = {rr[2]['tris']:,} / 段階 3 = {rr[3]['tris']:,}",
                     f"アトラス: {rr[1]['size']}² / {rr[2]['size']}² / {rr[3]['size']}² (1 ブロック 32 テクセル)"]
            desc = DESC[o["id"]][1]
            # 折り返し
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
    d.text((20, y), "アトラス (段階 1。3 倍表示・最近傍)", font=F(20), fill=WH)
    y += 34
    xx = 20
    ay = y
    for o in OPTS:
        rr = results[o["id"]][1]
        at = Image.fromarray(rr["atlas"], "RGBA")
        bg = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bg.alpha_composite(at)
        sz = rr["size"] * 2
        canvas.paste(bg.convert("RGB").resize((sz, sz), Image.NEAREST), (xx, y))
        frame(xx, y, sz, sz)
        d.text((xx, y + sz + 4), f"{DESC[o['id']][0]} {rr['size']}×{rr['size']}", font=F(15), fill=CY)
        xx += sz + 20
        ay = max(ay, y + sz + 30)
    canvas = canvas.crop((0, 0, W, min(canvas.size[1], ay - 250)))
    if save:
        canvas.save(OUT / "lp_whale_heart.png")
        print("saved sheet", canvas.size)
        manifest = {
            "id": "lp_whale_heart",
            "short": "岩鯨の心臓 低ポリ",
            "kind": "低ポリ 3案",
            "title": "岩鯨の心臓 — 低ポリ非立方体 + ドット絵 3 案 (最終ボス・段階 1/2/3)",
            "sheets": ["lowpoly_roughs/lp_whale_heart.png"],
            "extra": ("統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の心臓。骨名・階層・pivot・休めの回転は今の geo (段階 1/2/3) のまま、"
                      "今の intro/death を含むアニメがそのまま動く。三角形 (段階 1 / 2 / 3): "
                      + " / ".join(f"{DESC[o['id']][0].split()[0]} {results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}・{results[o['id']][3]['tris']:,}"
                                   for o in OPTS)
                      + f"。今は箱 {n_cubes[1]}/{n_cubes[2]}/{n_cubes[3]} 個 (面 5,000〜6,000)。上限は 2,000 三角形/段階。"
                        "メッシュは lowpoly_roughs/lp_whale_heart/{a,b,c}/p{1,2,3}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": DESC[o["id"]][0], "desc": DESC[o["id"]][1]} for o in OPTS},
        }
        (OUT / "lp_whale_heart_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
