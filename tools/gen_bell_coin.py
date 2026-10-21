#!/usr/bin/env python3
"""都の通貨「鐘銭」(bell coin) の 32x32 の絵、3 案。

  python tools/gen_bell_coin.py               -> docs/review/coin_roughs/{a,b,c}.png と compare.png (8 倍)
  python tools/gen_bell_coin.py --install a   -> 選んだ案を assets/skyabyss/textures/item/bell_coin.png へ

描き方はいまの 32x32 の品物と同じ: 1 画素の暗い縁取り (20, 18, 28)、平らな 3〜4 段の色、光は左上 (上と左の縁が明るく、
下と右の縁が暗い)。くぼみ (穴・刻み) は逆に、上と左の内側が暗い。
  A 角穴の丸い銭      古い青銅。真ん中の角穴の上に小さな鐘の浮き彫り、縁は一段高い。緑青の点
  B 鐘の形の銭        銭そのものが鐘の形。上に紐の穴、口の帯と真ん中の筋
  C 紐に通した銭の束  角穴の銭を三枚、紐で斜めに連ねる。紐の端が下に垂れる
"""
import argparse
import os
import shutil

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "review", "coin_roughs")
ITEM = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "item", "bell_coin.png")
N = 32
OUTLINE = (20, 18, 28)
# 古い青銅の段 (暗 → 明) と緑青
BRONZE = [(84, 54, 30), (122, 82, 42), (170, 120, 60), (212, 164, 92), (240, 206, 140)]
PATINA = [(70, 112, 98), (104, 150, 128)]
CORD = [(70, 40, 34), (122, 62, 48), (164, 92, 66)]


def disc(cx, cy, r):
    yy, xx = np.mgrid[0:N, 0:N]
    return (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r


def rect(x0, y0, x1, y1):
    m = np.zeros((N, N), bool)
    m[y0:y1 + 1, x0:x1 + 1] = True
    return m


def polym(pts):
    im = Image.new("L", (N, N), 0)
    ImageDraw.Draw(im).polygon(pts, fill=255)
    return np.array(im) > 0


def sh(m, dx, dy):
    out = np.zeros_like(m)
    ys = slice(max(dy, 0), N + min(dy, 0))
    yd = slice(max(-dy, 0), N + min(-dy, 0))
    xs = slice(max(dx, 0), N + min(dx, 0))
    xd = slice(max(-dx, 0), N + min(-dx, 0))
    out[ys, xs] = m[yd, xd]
    return out


class Can:
    def __init__(self):
        self.img = np.zeros((N, N, 4), np.uint8)

    def put(self, m, c):
        self.img[m, :3] = c
        self.img[m, 3] = 255

    def raised(self, m, ramp=BRONZE, base=2):
        """盛り上がった面: 上・左の縁が明るく、下・右の縁が暗い。"""
        up = m & ~sh(m, 0, 1)
        left = m & ~sh(m, 1, 0)
        down = m & ~sh(m, 0, -1)
        right = m & ~sh(m, -1, 0)
        self.put(m, ramp[base])
        self.put((up | left) & ~(down | right), ramp[min(base + 1, len(ramp) - 1)])
        self.put((down | right) & ~(up | left), ramp[max(base - 1, 0)])

    def sunk(self, m, ramp=BRONZE, base=1):
        """くぼみ: 上・左の内側が暗く、下・右の内側が明るい。"""
        up = m & ~sh(m, 0, 1)
        left = m & ~sh(m, 1, 0)
        down = m & ~sh(m, 0, -1)
        right = m & ~sh(m, -1, 0)
        self.put(m, ramp[base])
        self.put(up | left, ramp[max(base - 1, 0)])
        self.put((down | right) & ~(up | left), ramp[min(base + 1, len(ramp) - 1)])

    def outline(self, m):
        ring = (sh(m, 1, 0) | sh(m, -1, 0) | sh(m, 0, 1) | sh(m, 0, -1)) & ~m
        self.put(ring, OUTLINE)

    def hole(self, m):
        self.img[m] = 0

    def dots(self, pts, c):
        for x, y in pts:
            self.img[y, x, :3] = c
            self.img[y, x, 3] = 255


def bell_shape(cx, top, w, h):
    """小さな鐘の形 (上が丸く、口が広がる)。"""
    pts = [(cx - w * 0.28, top + h * 0.15), (cx - w * 0.34, top + h * 0.6), (cx - w * 0.5, top + h * 0.92),
           (cx + w * 0.5, top + h * 0.92), (cx + w * 0.34, top + h * 0.6), (cx + w * 0.28, top + h * 0.15)]
    return polym(pts) | disc(cx, top + h * 0.22, w * 0.28)


def coin_a():
    c = Can()
    body = disc(16, 16, 12.6)
    c.outline(body)
    c.raised(body, base=2)
    # 一段高い縁の内側の溝 (きれいな暗い輪)
    face = disc(16, 16, 10.0)
    c.put(disc(16, 16, 11.0) & ~face, BRONZE[1])
    c.put(face, BRONZE[2])
    # 面の左上は少し明るく、右下は暗く (縁の影)
    c.put(face & ~sh(face, 1, 1), BRONZE[0])
    # 鐘の浮き彫り (角穴の上)
    rows = ["...##...", "..####..", ".######.", ".######.", ".######.", "########", "...##..."]
    b = np.zeros((N, N), bool)
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            b[5 + j, 12 + i] = ch == "#"
    b &= face
    c.put((sh(b, 1, 0) | sh(b, -1, 0) | sh(b, 0, 1) | sh(b, 0, -1)) & face & ~b, BRONZE[1])
    c.put(sh(b, 1, 1) & face & ~b, BRONZE[0])
    c.raised(b, base=3)
    # 角穴 (縁は明るい段、上と左の内側は暗い、中は抜き)
    c.put(rect(12, 13, 19, 20), BRONZE[3])
    c.put(rect(13, 14, 18, 19), OUTLINE)
    c.hole(rect(14, 15, 17, 18))
    # 緑青の点と、左上の光
    c.dots([(9, 22), (10, 23), (22, 23), (8, 12)], PATINA[0])
    c.dots([(9, 21)], PATINA[1])
    c.dots([(9, 7), (8, 8), (10, 6)], BRONZE[4])
    return c.img


def coin_b():
    c = Can()
    body = bell_shape(16, 3.5, 25, 26) | rect(4, 26, 27, 28)
    loop = disc(16, 3.6, 3.2)
    m = body | loop
    c.outline(m)
    c.raised(m, base=2)
    c.hole(disc(16, 3.6, 1.4))
    c.put(disc(16, 3.6, 2.2) & ~disc(16, 3.6, 1.4) & ~sh(disc(16, 3.6, 1.4), 1, 1), BRONZE[1])
    # 肩の帯・口の帯 (くぼみ) と真ん中の筋
    c.sunk(rect(9, 10, 22, 10) & body, base=1)
    c.sunk(rect(6, 23, 25, 24) & body, base=1)
    c.raised(rect(15, 12, 16, 21), base=3)
    # 口の縁 (一段明るい)
    c.put(rect(5, 27, 26, 27) & m, BRONZE[3])
    c.dots([(10, 18), (11, 19), (21, 15), (8, 25)], PATINA[0])
    c.dots([(10, 13), (11, 12)], BRONZE[4])
    return c.img


def small_coin(c, cx, cy, r):
    body = disc(cx, cy, r)
    c.outline(body)
    c.raised(body, base=2)
    rim = disc(cx, cy, r - 1.6) & ~disc(cx, cy, r - 2.4)
    c.sunk(rim, base=1)
    hx, hy = int(cx) - 1, int(cy) - 1
    c.put(rect(hx - 1, hy - 1, hx + 2, hy + 2), OUTLINE)
    return body


def coin_c():
    c = Can()
    # 紐 (下の銭の後ろから上へ)
    cord = np.zeros((N, N), bool)
    for t in np.linspace(0, 1, 60):
        x, y = 6 + 20 * t, 26 - 20 * t
        cord |= disc(x, y, 1.0)
    c.outline(cord)
    c.raised(cord, CORD, 1)
    # 三枚の銭 (下から上へ、重ねて)
    for cx, cy in ((9.5, 22.5), (16, 16), (22.5, 9.5)):
        small_coin(c, cx, cy, 7.2)
        hx, hy = int(cx) - 1, int(cy) - 1
        c.put(rect(hx, hy, hx + 1, hy + 1), CORD[2])
    # 紐の結びと垂れ (左下)
    tail = polym([(2, 27), (5, 25), (6, 27), (4, 31), (2, 31)])
    c.outline(tail)
    c.raised(tail, CORD, 1)
    c.dots([(7, 24), (14, 19), (20, 12)], PATINA[0])
    c.dots([(6, 19), (13, 12), (19, 6)], BRONZE[4])
    return c.img


OPTS = {"a": ("A 角穴の丸い銭", coin_a), "b": ("B 鐘の形の銭", coin_b), "c": ("C 紐に通した銭の束", coin_c)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", choices=list(OPTS))
    a = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    imgs = {}
    for k, (_, fn) in OPTS.items():
        im = Image.fromarray(fn(), "RGBA")
        im.save(os.path.join(OUT, k + ".png"))
        imgs[k] = im
    # 比べ: 8 倍の 3 案 + 原寸と、いまの品物と並べた 4 倍
    import sys
    sys.path.insert(0, os.path.join(ROOT, "tools", "rough"))
    import voxel
    S = 8
    ref_names = ["moontide_pearl", "road_hardtack", "sentry_tally", "rock_whetstone"]
    item_dir = os.path.dirname(ITEM)
    refs = [Image.open(os.path.join(item_dir, n + ".png")).convert("RGBA") for n in ref_names if os.path.exists(os.path.join(item_dir, n + ".png"))]
    W = 3 * (N * S + 30) + 40
    H = N * S + 60 + 40 + (N * 4 + 40) * 3 + 20
    cv = Image.new("RGB", (W, H), (52, 60, 74))
    d = ImageDraw.Draw(cv)
    d.text((16, 10), "鐘銭 (bell coin) 32x32 の 3 案 (8 倍)", font=voxel.font(20), fill=(236, 232, 218))
    for i, (k, im) in enumerate(imgs.items()):
        x = 20 + i * (N * S + 30)
        big = im.resize((N * S, N * S), Image.NEAREST)
        cv.paste(big, (x, 46), big)
        d.text((x, 46 + N * S + 6), OPTS[k][0], font=voxel.font(16), fill=(150, 232, 255))
    y = 46 + N * S + 40
    d.text((16, y), "いまの品物と並べて (4 倍・原寸)", font=voxel.font(16), fill=(200, 210, 220))
    y += 26
    for i, (k, im) in enumerate(imgs.items()):
        x = 20
        row = [im] + refs
        for j, r in enumerate(row):
            big = r.resize((N * 4, N * 4), Image.NEAREST)
            cv.paste(big, (x + j * (N * 4 + 8), y), big)
        xx = x + len(row) * (N * 4 + 8) + 10
        for j, r in enumerate(row):
            cv.paste(r, (xx + j * 36, y + 48), r)
        d.text((xx, y + 90), OPTS[k][0], font=voxel.font(14), fill=(150, 232, 255))
        y += N * 4 + 16
    cv = cv.crop((0, 0, W, y + 10))
    cv.save(os.path.join(OUT, "compare.png"))
    print("wrote", OUT)
    if a.install:
        os.makedirs(os.path.dirname(ITEM), exist_ok=True)
        shutil.copyfile(os.path.join(OUT, a.install + ".png"), ITEM)
        print("installed", a.install, "->", ITEM)


if __name__ == "__main__":
    main()
