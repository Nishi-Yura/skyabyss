# -*- coding: utf-8 -*-
"""大陸を四つの島に分ける案 (2026-11-08 の声「それぞれの大陸を4つに分けて海が間にある様にして。それぞれの大陸での要素をもっと
作り込めたり、濃くすることができるのでは」)。いまの大陸の図 (region.bin・biome.bin) の上で、海の入れ方を三通りに描いて比べる。

島の分け方は三案とも同じ: 西 = 都と嵐の高地 (と外れの谷・はじまり)、北 = 岩鯨の丘陵、東 = 潮の浜、南 = 灰の荒野。
  A 細い海峡   : 土地の境に幅 48 ブロックの海峡。橋と渡し船でつなぐ。一つの大陸が割れた感じ
  B 広い海     : 境に幅 150 ブロックの海。島の縁は少し痩せ、海峡に小島。船の旅で渡る
  C 真ん中の海 : 内海を大きく広げて四つの島の真ん中の海に。外へ向かう海峡は細く、島は輪のように並ぶ

    python -I -X utf8 tools/rough/world/island_split.py   → docs/review/roughs_island_split/islands.png
"""
from __future__ import annotations

import gzip
import json
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / "src/main/resources/data/skyabyss/continent"
OUT = ROOT / "docs/review/roughs_island_split"
N = 1000
SCALE = 4
REGIONS = ['meadow', 'start', 'whale', 'tide', 'storm', 'ember']
ISLAND = {'meadow': 0, 'start': 0, 'storm': 0, 'whale': 1, 'tide': 2, 'ember': 3}
ISLAND_NAMES = ['西の島 (都・嵐)', '北の島 (岩鯨)', '東の島 (潮)', '南の島 (灰)']
ISLAND_COL = [(120, 200, 120), (170, 170, 170), (230, 210, 140), (220, 120, 70)]


def font(size):
    for p in ("C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryo.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def read(name):
    raw = gzip.decompress((DATA / name).read_bytes())
    # 頭: SKCN と大きさ・縮尺・端 (4 x int)。そのあと 1 升 1 バイト (行が z)
    return np.frombuffer(raw[20:], dtype=np.uint8).reshape(N, N)


def load():
    h = read("height.bin").astype(int)
    reg = read("region.bin").astype(int)
    lay = json.loads((DATA / "layout.json").read_text(encoding="utf-8"))
    return h, reg, lay


def to_px(x, z):
    return (x / SCALE + N / 2, z / SCALE + N / 2)


def islands_of(reg, land):
    isl = np.full(reg.shape, -1)
    for i, k in enumerate(REGIONS):
        isl[(reg == i)] = ISLAND[k]
    isl[~land] = -1
    return isl


def border_dist(isl, land):
    """各升から、違う島の升までの近さ (升)。陸だけ"""
    d = np.full(isl.shape, np.inf)
    for k in range(4):
        other = land & (isl != k) & (isl >= 0)
        dist = ndimage.distance_transform_edt(~other)
        d = np.where(isl == k, np.minimum(d, dist), d)
    return d


def option(h, reg, lay, kind):
    land = h >= 63
    isl = islands_of(reg, land)
    bd = border_dist(isl, land)
    sea = ~land.copy()
    islets = np.zeros_like(land)
    if kind == "A":
        cut = bd < 6          # 両側から 6 升 = 幅 12 升 = 48 ブロック
        sea |= cut
    elif kind == "B":
        cut = bd < 19         # 幅 ~150 ブロック
        sea |= cut
        # 縁を少し痩せさせる
        coast = ndimage.distance_transform_edt(~sea)
        sea |= coast < 6
        rng = np.random.default_rng(5)
        ys, xs = np.nonzero(cut & land)
        for k in rng.choice(len(ys), 14, replace=False):
            yy, xx = np.ogrid[:N, :N]
            r = rng.uniform(5, 9)
            islets |= ((yy - ys[k]) ** 2 + (xx - xs[k]) ** 2) < r * r
        islets &= cut & land
        sea &= ~islets
    else:
        # 内海を広げる: 内海 (陸に囲まれた水) の中心から半径を足す
        lab, n = ndimage.label(~land)
        outer = lab[0, 0]
        inner = (~land) & (lab != outer)
        cy, cx = ndimage.center_of_mass(inner)
        yy, xx = np.mgrid[:N, :N]
        r = np.hypot(yy - cy, xx - cx)
        sea |= r < 150
        sea |= bd < 5
    return sea, islets, isl


def draw(h, reg, lay, kind, title, lines):
    sea, islets, isl = option(h, reg, lay, kind)
    img = np.zeros((N, N, 3), float)
    shade = np.clip((h - 63) / 120.0, 0, 1)
    for k in range(4):
        m = (isl == k) & ~sea
        img[m] = np.array(ISLAND_COL[k]) * (0.75 + 0.35 * shade[m])[:, None]
    img[islets] = (200, 190, 150)
    depth = ndimage.distance_transform_edt(sea)
    img[sea] = np.stack([np.full(depth[sea].shape, 40.0), 90 - np.clip(depth[sea], 0, 40), 150 - np.clip(depth[sea], 0, 50) * 0.8], -1)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((600, 600), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    s = 600 / N
    def pt(x, z):
        px, pz = to_px(x, z)
        return px * s, pz * s
    cx, cz = pt(*lay["capital"])
    d.rectangle([cx - 7, cz - 7, cx + 7, cz + 7], fill=(255, 230, 120), outline=(40, 30, 10), width=2)
    for k, (x, z) in lay["towns"].items():
        px, pz = pt(x, z)
        d.ellipse([px - 5, pz - 5, px + 5, pz + 5], fill=(255, 255, 255), outline=(30, 30, 30), width=2)
    for k, (x, z) in lay["sites"].items():
        px, pz = pt(x, z)
        d.polygon([(px, pz - 7), (px + 6, pz + 5), (px - 6, pz + 5)], fill=(240, 80, 80), outline=(30, 10, 10))
    # 島ごとの名前 (重心)
    for k in range(4):
        m = (isl == k) & ~sea
        if m.any():
            yy, xx = ndimage.center_of_mass(m)
            d.text((xx * s - 40, yy * s - 8), ISLAND_NAMES[k], font=font(15), fill=(255, 255, 255), stroke_width=3, stroke_fill=(20, 20, 30))
    W = 620
    sheet = Image.new("RGB", (W, 600 + 150), (16, 15, 20))
    sheet.paste(im, (10, 10))
    dd = ImageDraw.Draw(sheet)
    dd.text((14, 620), title, font=font(20), fill=(255, 220, 150))
    y = 650
    for ln in lines:
        dd.text((14, y), ln, font=font(14), fill=(220, 215, 225))
        y += 20
    return sheet


def main():
    h, reg, lay = load()
    opts = [
        ("A", "A 細い海峡 (幅 48)", ["土地の境に細い海峡。橋と渡し船でつなぐ。", "一つの大陸が割れた形。道はほぼいまのまま、海峡で切れる所に橋。", "移るのが楽で、島ごとの濃さは少し控えめ。"]),
        ("B", "B 広い海 (幅 150・小島あり)", ["島のあいだに広い海。渡るのは船 (渡し船・乗り物の船)。", "島の縁は少し痩せ、海に小島 (見どころ・宝を置ける)。", "島ごとに気候と敵がはっきり分かれる。いちばん濃くできる。"]),
        ("C 真ん中の海", "C 真ん中の海 (内海を広げる)", ["内海を大きく広げ、四つの島が真ん中の海を囲む。", "外へ向かう海峡は細い。真ん中の海に、", "五章の淵への入口や大事件の舞台を置ける。"]),
    ]
    sheets = [draw(h, reg, lay, k[0], t, ls) for k, t, ls in opts]
    W = sum(s.width for s in sheets) + 20
    out = Image.new("RGB", (W, sheets[0].height + 70), (16, 15, 20))
    d = ImageDraw.Draw(out)
    d.text((14, 12), "大陸を四つの島に (西: 都と嵐・北: 岩鯨・東: 潮・南: 灰)", font=font(24), fill=(240, 232, 214))
    d.text((14, 44), "■ 都  ● 町  ▲ 巨神の跡地。地形・高さ・土地の色はいまの図から。海の入れ方だけ三通り", font=font(14), fill=(170, 165, 180))
    x = 10
    for s in sheets:
        out.paste(s, (x, 70))
        x += s.width
    OUT.mkdir(parents=True, exist_ok=True)
    out.save(OUT / "islands.png")
    print("wrote", OUT / "islands.png")


if __name__ == "__main__":
    main()
