"""心臓の番人の絵の仕上げ ラフ 3 案 (docs/design/36 の 6.6「まだ: 絵の手描きの仕上げ」。2026-10-28)。形は今の本番 (warden_model.py) のまま、絵だけを替える。
  A 古い骨   骨に古い染み (継ぎ目と下ほど暗い)、肋に刻まれた細い線、太いひび。虚の体は深い黒紫に疎らな光の粒
  B 塩の殻   骨の上の面に白い塩の結晶の斑がこびりつき、下は湿った灰色。虚の体は淡い紫の光沢
  C 淵の苔   骨の窪みに青緑の苔 (スカルクのような粒) が育ち、骨は乾いた白。虚の体は苔の色の筋
どれも心臓・目・肋の光る筋 (発光) は今のまま。光るのは青緑だけ。

  python tools/rough/lowpoly/abyss/warden_paint.py   -> docs/review/warden_paint/{a,b,c}/heart_warden_atlas.png・_glowmask.png・views.png, compare.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import warden_model as M  # noqa: E402

OUT = M.ROOT / "docs/review/warden_paint"
BASE = M.paint_face_texels
noise, mix = M.value_noise, M.mix


def style_a(mat, p, n, edge):
    col, lit = BASE(mat, p, n, edge)
    up = np.clip(n[:, 1] * 0.5 + 0.5, 0, 1)
    if mat == "bone":
        # 古い染み: 下の面と低い所ほど茶色く沈む
        low = np.clip((30 - p[:, 1]) / 30, 0, 1)
        stain = noise(p, 0.8, 51.0)
        t = np.clip(0.55 * low + 0.6 * (stain - 0.45) + 0.25 * (1 - up), 0, 1)
        col = col * (1 - t[:, None] * 0.75) + np.array([132, 86, 44], float)[None, :] * (t[:, None] * 0.75)
        col[lit] = col[lit]  # 光る筋はそのまま
        # 刻まれた細い線 (縦の筋を等間隔に)
        carve = np.abs(((p[:, 1] * 0.9 + noise(p, 0.5, 61.0) * 2.0) % 3.0) - 1.5) < 0.09
        col[carve & (up > 0.3)] *= 0.45
        # 太いひび (暗い)
        crack = np.abs(noise(p, 0.7, 71.0) - 0.5) < 0.012
        col[crack] *= 0.3
    elif mat == "void":
        speck = noise(p, 9.0, 81.0) > 0.93
        col[speck] = mix([120, 110, 170], [190, 180, 230], noise(p[speck], 3.0, 82.0))
        col *= 0.8
    return col, lit


def style_b(mat, p, n, edge):
    col, lit = BASE(mat, p, n, edge)
    up = np.clip(n[:, 1] * 0.5 + 0.5, 0, 1)
    if mat == "bone":
        damp = np.clip(1 - up, 0, 1)
        col = col * (1 - damp[:, None] * 0.35) + np.array([96, 100, 104], float)[None, :] * (damp[:, None] * 0.35)
        salt = noise(p, 1.3, 91.0) + 0.35 * up
        crust = salt > 0.78
        grain = noise(p[crust], 8.0, 92.0)
        col[crust] = mix([214, 220, 224], [250, 252, 252], grain)
        edgec = (salt > 0.72) & ~crust
        col[edgec] = col[edgec] * 0.7 + np.array([200, 206, 210], float) * 0.3
    elif mat == "void":
        sheen = np.clip(up * 0.8 + 0.3 * (noise(p, 1.2, 95.0) - 0.5), 0, 1)
        col = col * (1 - sheen[:, None] * 0.35) + np.array([120, 96, 150], float)[None, :] * (sheen[:, None] * 0.35)
    return col, lit


def style_c(mat, p, n, edge):
    col, lit = BASE(mat, p, n, edge)
    up = np.clip(n[:, 1] * 0.5 + 0.5, 0, 1)
    if mat == "bone":
        dry = np.clip(0.15 + 0.2 * up, 0, 1)
        col = col * (1 - dry[:, None]) + np.array([236, 230, 212], float)[None, :] * dry[:, None]
        moss = noise(p, 1.0, 101.0) + 0.3 * (1 - up) + 0.25 * np.clip((40 - p[:, 1]) / 40, 0, 1)
        grow = moss > 0.66
        g = noise(p[grow], 7.0, 102.0)
        col[grow] = mix([10, 40, 44], [30, 92, 90], g)
        dots = grow & (noise(p, 11.0, 103.0) > 0.86)
        col[dots] = np.array([60, 150, 140], float)
    elif mat == "void":
        vein = np.abs(noise(p, 1.4, 111.0) - 0.5) < 0.03
        col[vein] = col[vein] * 0.4 + np.array([30, 110, 104], float) * 0.6
    return col, lit


STYLES = {"a": ("A 古い骨", style_a), "b": ("B 塩の殻", style_b), "c": ("C 淵の苔", style_c)}


def main():
    faces = M.build()
    size = M.chart_unwrap(faces)
    sheets = []
    for k, (label, fn) in STYLES.items():
        M.paint_face_texels = fn
        atlas, glow = M.paint(faces, size)
        d = OUT / k
        d.mkdir(parents=True, exist_ok=True)
        Image.fromarray(atlas, "RGBA").save(d / "heart_warden_atlas.png")
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        Image.fromarray(gm, "RGBA").save(d / "heart_warden_glowmask.png")
        rv = M.views(faces, atlas, glow, size)
        row = Image.new("RGB", (3 * 300 + 2 * 14, 320), (16, 16, 22))
        for i, (_, im) in enumerate(rv):
            row.paste(im, (i * 314, 0))
        row.save(d / "views.png")
        sheets.append((label, row))
    M.paint_face_texels = BASE
    cv = Image.new("RGB", (18 * 2 + sheets[0][1].width, 40 + len(sheets) * 350), (16, 16, 22))
    dr = ImageDraw.Draw(cv)
    F = M._ck.voxel.font
    dr.text((18, 10), "心臓の番人 絵の仕上げ 3 案 (形は同じ。正面・3/4・横)", font=F(22), fill=(236, 232, 218))
    for i, (label, row) in enumerate(sheets):
        y = 40 + i * 350
        dr.text((18, y), label, font=F(18), fill=(150, 232, 255))
        cv.paste(row, (18, y + 26))
    cv.save(OUT / "compare.png")
    print("ok", size)


if __name__ == "__main__":
    main()
