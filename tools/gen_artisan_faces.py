#!/usr/bin/env python3
"""旅の職人の対話画面の顔 (80x80 の 3 枚: neutral / smile / grave) を、低ポリの頭から描き起こす。

  python tools/gen_artisan_faces.py   -> assets/skyabyss/textures/gui/npc/face/artisan_{neutral,smile,grave}.png

頭のメッシュ (docs/review/ch1_roughs/artisan_colorways/a の head 骨) を正面 (-Z 側) から見た絵にする。章の色は顔には出ないので 1 組だけ。
表情の差は、ラフに別の絵がないので、口もとと眉のあたりの数画素だけ描き足す (smile: 口角を上げる / grave: 眉を寄せて全体をやや暗く)。
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "review", "ch1_roughs", "artisan_colorways", "a")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "gui", "npc", "face")
SS = 6  # 超解像の倍率
SIZE = 80


def render():
    mesh = json.load(open(os.path.join(SRC, "artisan_colorways_a_mesh.json"), encoding="utf-8"))
    atlas = np.array(Image.open(os.path.join(SRC, "artisan_colorways_a_atlas.png")).convert("RGBA"))
    ah, aw = atlas.shape[:2]
    head = mesh["bones"]["head"]
    V = np.array(head["vertices"])
    U = np.array(head["uvs"])
    # 顔の枠: x -4.4..4.4、y 22.2..31.4 (額の眼鏡と髪まで入れる)
    x0, x1, y0, y1 = -4.6, 4.6, 21.9, 31.5
    W = SIZE * SS
    H = SIZE * SS
    img = np.zeros((H, W, 4), dtype=np.uint8)
    zbuf = np.full((H, W), 1e9)
    for t in head["triangles"]:
        p = V[t]
        n = np.cross(p[1] - p[0], p[2] - p[0])
        if n[2] > 0:  # 後ろ向き (-Z から見て裏)
            continue
        px = (p[:, 0] - x0) / (x1 - x0) * W
        px = W - px  # -Z から見ると +X (armL) が画面の左
        py = (y1 - p[:, 1]) / (y1 - y0) * H
        minx, maxx = int(max(0, np.floor(px.min()))), int(min(W - 1, np.ceil(px.max())))
        miny, maxy = int(max(0, np.floor(py.min()))), int(min(H - 1, np.ceil(py.max())))
        if maxx < minx or maxy < miny:
            continue
        gx, gy = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        d = (py[1] - py[2]) * (px[0] - px[2]) + (px[2] - px[1]) * (py[0] - py[2])
        if abs(d) < 1e-9:
            continue
        l0 = ((py[1] - py[2]) * (gx - px[2]) + (px[2] - px[1]) * (gy - py[2])) / d
        l1 = ((py[2] - py[0]) * (gx - px[2]) + (px[0] - px[2]) * (gy - py[2])) / d
        l2 = 1 - l0 - l1
        inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
        if not inside.any():
            continue
        z = l0 * p[0, 2] + l1 * p[1, 2] + l2 * p[2, 2]
        u = l0 * U[t[0], 0] + l1 * U[t[1], 0] + l2 * U[t[2], 0]
        v = l0 * U[t[0], 1] + l1 * U[t[1], 1] + l2 * U[t[2], 1]
        tx = np.clip((u * aw).astype(int), 0, aw - 1)
        ty = np.clip((v * ah).astype(int), 0, ah - 1)
        col = atlas[ty, tx]
        sub_z = zbuf[miny:maxy + 1, minx:maxx + 1]
        sub_img = img[miny:maxy + 1, minx:maxx + 1]
        take = inside & (z < sub_z) & (col[..., 3] > 0)
        sub_z[take] = z[take]
        sub_img[take] = col[take]
    out = Image.fromarray(img, "RGBA").resize((SIZE, SIZE), Image.LANCZOS)
    return out


def with_background(face):
    bg = Image.new("RGBA", (SIZE, SIZE), (24, 28, 36, 255))
    bg.alpha_composite(face)
    return bg


def main():
    face = with_background(render())
    os.makedirs(OUT, exist_ok=True)
    neutral = face.copy()
    neutral.save(os.path.join(OUT, "artisan_neutral.png"))
    # 笑み: 顔の下の方の口もとの両端を上げる (暗い画素の細い弧を足す)
    smile = face.copy()
    d = ImageDraw.Draw(smile)
    mouth = (92, 56, 44, 255)
    d.line([(33, 57), (36, 60), (44, 60), (47, 57)], fill=mouth, width=1)
    smile = Image.blend(smile, Image.new("RGBA", smile.size, (255, 236, 200, 255)), 0.05)
    smile.save(os.path.join(OUT, "artisan_smile.png"))
    # 険しい顔: 全体をやや暗く、眉のあたりに斜めの影
    grave = face.copy()
    grave = Image.blend(grave, Image.new("RGBA", grave.size, (10, 12, 20, 255)), 0.22)
    d = ImageDraw.Draw(grave)
    brow = (40, 30, 26, 255)
    d.line([(26, 37), (34, 40)], fill=brow, width=2)
    d.line([(54, 37), (46, 40)], fill=brow, width=2)
    d.line([(34, 60), (46, 60)], fill=(70, 46, 38, 255), width=1)
    grave.save(os.path.join(OUT, "artisan_grave.png"))
    print("wrote artisan faces ->", OUT)


if __name__ == "__main__":
    main()
