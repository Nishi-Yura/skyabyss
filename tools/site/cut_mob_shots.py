"""wiki の敵の姿の絵の、緑の幕を抜く。

入力: run/shots/wiki_mobs/shots/wiki_mobs/<敵>.png (tools/shots/wiki_mobs.txt。gen_mob_shots.py が書く)
出力: tools/sources/site/mobs/<敵>.png (幕を抜き、体のまわりで切って 256 四方に置いた RGBA)。サイトの絵は gen_site_shots.py が作る

幕は、絵の縁から幕の色でつながっている所だけ抜く (緑の体の敵に穴をあけないため)。体に囲まれた幕は、ライムの濃い色だけ抜く。
使い方 (リポジトリ直下で): python -I tools/site/cut_mob_shots.py
"""
import glob
import os
from collections import deque

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "run", "shots", "wiki_mobs", "shots", "wiki_mobs")
OUT = os.path.join(ROOT, "tools", "sources", "site", "mobs")
SIZE = 256
# 抜いたあとに残った画素がこれより少ない絵は、何も写っていない (戦いの場の中でしか姿を出さない主など) として出さない
MIN_PIXELS = 1500


def cut(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.int32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    screen = (g > 40) & (g > r * 1.25) & (g > b * 1.5)
    strong = (g > 60) & (g > r * 1.6) & (g > b * 3)
    h, w = screen.shape
    bg = np.zeros_like(screen)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if screen[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if screen[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w and screen[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True
                q.append((ny, nx))
    alpha = np.where(bg | strong, 0, 255).astype(np.uint8)
    if int((alpha > 0).sum()) < MIN_PIXELS:
        return None
    im = Image.fromarray(np.dstack([a.astype(np.uint8), alpha]), "RGBA")
    box = im.getbbox()
    im = im.crop(box)
    side = max(im.width, im.height) + 8
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((side - im.width) // 2, side - im.height - 4))
    return canvas.resize((SIZE, SIZE), Image.LANCZOS) if side > SIZE else canvas


def main():
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for p in sorted(glob.glob(os.path.join(SRC, "*.png"))):
        im = cut(p)
        if im is None:
            print("empty:", os.path.basename(p))
            continue
        im.save(os.path.join(OUT, os.path.basename(p)), optimize=True)
        n += 1
    print("mobs:", n)


if __name__ == "__main__":
    main()
