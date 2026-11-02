# -*- coding: utf-8 -*-
"""上弦の崖 (六章 区画 2) の配置 3 案を、上から見た高さの図と、東西の断面で描く (docs/design/46 の 4.4)。
場所は着く所の東 (x 60〜220)。月の面は y 11。野営は崖の根元、主の間 (天秤の皿) は頂。探す場所 3: 跳び石の塔・崩れる棚・天秤の岩。

A 弧の登り   上弦の月の形に東へ弧を描く尾根。弧の内側に段々の岩棚が登り、頂は弧の東の端
B 柱の林     盆地に高さの違う岩の柱が林立する。柱から柱へ跳んで螺旋に登り、真ん中の一番高い柱の上が主の間
C 三段の断崖 三つの大きな段 (台地) が縦の崖で区切られる。一段に一つずつ探す場所、頂の段の縁に主の間

  python tools/rough/moon_cliff_layouts.py   -> docs/review/moon_roughs/cliff_layouts.png
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import voxel

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/moon_roughs/cliff_layouts.png"
W, L = 170, 120          # x 55〜225、z -60〜60
X0, Z0 = 55, -60
S = 4
SURF = 11
TOP = 80


def noise(seed, sc=8):
    r = np.random.default_rng(seed)
    g = r.random((L // sc + 3, W // sc + 3))
    im = Image.fromarray((g * 255).astype(np.uint8)).resize(((W // sc + 3) * sc, (L // sc + 3) * sc), Image.BICUBIC)
    return np.asarray(im, float)[:L, :W] / 255.0


ZZ, XX = np.mgrid[0:L, 0:W]
WX = XX + X0
WZ = ZZ + Z0


def color(h):
    t = np.clip((h - SURF) / (TOP - SURF), 0, 1)[..., None]
    low = np.array((150, 156, 168))
    mid = np.array((96, 100, 116))
    high = np.array((216, 220, 232))
    c = np.where(t < 0.5, low * (1 - t * 2) + mid * (t * 2), mid * (1 - (t - 0.5) * 2) + high * ((t - 0.5) * 2))
    return c


def render(h, marks, title, desc, path_pts):
    img = color(h)
    # 段差の陰 (東西の勾配で)
    gx = np.zeros_like(h)
    gx[:, 1:] = h[:, 1:] - h[:, :-1]
    img = img * (1 - np.clip(-gx, 0, 6)[..., None] * 0.08) + np.clip(gx, 0, 6)[..., None] * 6
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W * S, L * S), Image.NEAREST)
    d = ImageDraw.Draw(im)
    f = voxel.font(14)
    pts = [((x - X0) * S, (z - Z0) * S) for x, z in path_pts]
    d.line(pts, fill=(255, 220, 120), width=3)
    COL = {"camp": (250, 200, 110), "place": (150, 210, 255), "lord": (255, 120, 110), "enter": (240, 240, 240)}
    for kind, x, z, label in marks:
        px, pz = (x - X0) * S, (z - Z0) * S
        c = COL[kind]
        if kind == "lord":
            d.ellipse((px - 26, pz - 26, px + 26, pz + 26), outline=c, width=3)
        d.ellipse((px - 8, pz - 8, px + 8, pz + 8), fill=c, outline=(20, 20, 30), width=2)
        hh = int(h[min(L - 1, z - Z0), min(W - 1, x - X0)])
        lab = "%s (y %d)" % (label, hh)
        tw = d.textlength(lab, font=f)
        tx = px + 12 if px + 12 + tw < W * S - 4 else px - 12 - tw
        d.rectangle((tx - 3, pz - 9, tx + tw + 3, pz + 9), fill=(10, 12, 20))
        d.text((tx, pz - 8), lab, font=f, fill=c)
    d.text((W * S - 40, 6), "北 ↑", font=f, fill=(20, 20, 30))
    d.text((6, 6), "← 着く所 (西)", font=f, fill=(20, 20, 30))
    # 断面 (道の通り道に沿った高さ)
    prof_h = 170
    prof = Image.new("RGB", (W * S, prof_h), (14, 16, 26))
    pd = ImageDraw.Draw(prof)
    zc = L // 2
    line = h[zc]
    for xi in range(W):
        y = prof_h - 10 - (line[xi] - SURF + 4) * (prof_h - 30) / (TOP - SURF + 8)
        pd.rectangle((xi * S, min(y, prof_h - 10), xi * S + S - 1, prof_h - 10), fill=(110, 116, 132))
    pd.text((6, 4), "東西の断面 (z 0。高さは 2 倍に伸ばした)", font=f, fill=(200, 206, 220))
    head = Image.new("RGB", (W * S, 140), (16, 16, 22))
    hd = ImageDraw.Draw(head)
    hd.text((8, 6), title, font=voxel.font(20), fill=(236, 232, 218))
    y = 36
    fs = voxel.font(14)
    for line_ in desc:
        cur = ""
        for ch in line_:
            if hd.textlength(cur + ch, font=fs) > W * S - 16 and ch not in "。、)":
                hd.text((8, y), cur, font=fs, fill=(190, 196, 210))
                y += 20
                cur = ""
            cur += ch
        hd.text((8, y), cur, font=fs, fill=(190, 196, 210))
        y += 20
    cv = Image.new("RGB", (W * S, 140 + L * S + prof_h), (16, 16, 22))
    cv.paste(head, (0, 0))
    cv.paste(im, (0, 140))
    cv.paste(prof, (0, 140 + L * S))
    return cv


def opt_a():
    n = noise(3)
    # 弧: 中心 (120, -70) の円の一部 (半径 75〜95)。東へ行くほど高い
    r = np.hypot(WX - 115, WZ + 75)
    band = (r > 70 + n * 6) & (r < 100 + n * 6)
    t = np.clip((WX - 70) / 140.0, 0, 1)
    h = np.full((L, W), float(SURF))
    # 弧の内側の段々 (半径で段)
    steps = np.floor((100 - r) / 6.0)
    terr = SURF + (t * (TOP - SURF)) * np.clip(1 - np.abs(r - 85) / 18, 0, 1) + n * 3
    h = np.where(band, np.round(terr / 4) * 4, h)
    h = np.where(band & (r < 78), h - 6, h)
    def arc(deg):
        a = math.radians(deg)
        return int(round(115 + 85 * math.cos(a))), int(round(-75 + 85 * math.sin(a)))
    # 弧の上の点 (西の根元 → 東の端)。最後の点を頂にする (弧の東の端を一番高く盛る)
    seq = [arc(d) for d in (118, 102, 84, 66, 48)]
    tx, tz = seq[-1]
    top = np.hypot(WX - tx, WZ - tz) < 9
    h = np.where(top, TOP, h)
    marks = [("enter", 62, 2, "入口"), ("camp", 74, 10, "野営"), ("place", *seq[1], "1 跳び石の塔"), ("place", *seq[2], "2 崩れる棚"),
             ("place", *seq[3], "3 天秤の岩"), ("lord", *seq[4], "主の間")]
    path = [(62, 2), (74, 10)] + seq
    return render(h, marks, "A 弧の登り", ["上弦の月の形に東へ弧を描く尾根。弧の内側の段々の岩棚を登り、頂は弧の東の端。",
                                          "道は一本で、登るほど崖が高くなる。長所: 迷わない、下を見ると谷 (落ちの怖さ)。短所: 一本道"], path)


def opt_b():
    n = noise(5)
    h = np.full((L, W), float(SURF - 6))      # 盆地 (月の面より 6 低い)
    basin = np.hypot((WX - 140) / 80.0, WZ / 55.0) < 1.0
    h = np.where(basin, h, SURF)
    rng = np.random.default_rng(9)
    pillars = []
    for i in range(22):
        a = i / 22 * 6 * math.pi
        rad = 60 - i * 2.4
        cx, cz = 140 + math.cos(a) * rad * 1.1, math.sin(a) * rad * 0.8
        hh = SURF + 6 + i * 2.8
        pr = 3.5 + rng.random() * 2.5
        pillars.append((cx, cz, hh, pr))
    pillars.append((140, 0, TOP, 9))
    for cx, cz, hh, pr in pillars:
        m = np.hypot(WX - cx, WZ - cz) < pr + n * 1.5
        h = np.where(m, np.maximum(h, hh), h)
    def pil(i):
        return int(round(pillars[i][0])), int(round(pillars[i][1]))
    marks = [("enter", 62, 0, "入口"), ("camp", 74, 6, "野営 (盆地の縁)"), ("place", *pil(5), "1 跳び石の塔"), ("place", *pil(11), "2 崩れる棚"),
             ("place", *pil(17), "3 天秤の岩"), ("lord", 140, 0, "主の間 (一番高い柱)")]
    path = [(62, 0), (74, 6)] + [pil(i) for i in range(0, 22)] + [(140, 0)]
    return render(h, marks, "B 柱の林", ["盆地に高さの違う岩の柱が林立する。柱から柱へ跳んで螺旋に登り、真ん中の一番高い柱の上が主の間。",
                                        "長所: 軽身をいちばん使う、どこからも頂が見える。短所: 落ちると盆地の底から登り直し"], path)


def opt_c():
    n = noise(7)
    h = np.full((L, W), float(SURF))
    tiers = [(85, SURF + 14), (130, SURF + 32), (175, SURF + 52), (205, TOP)]
    for x_edge, hh in tiers:
        edge = x_edge + (n - 0.5) * 14
        h = np.where(WX > edge, hh + n * 2, h)
    # 段の上の起伏
    h = np.round(h)
    marks = [("enter", 62, 0, "入口"), ("camp", 80, 10, "野営 (一段目の崖の根元)"), ("place", 108, -20, "1 跳び石の塔"), ("place", 152, 22, "2 崩れる棚"),
             ("place", 190, -18, "3 天秤の岩"), ("lord", 212, 4, "主の間")]
    path = [(62, 0), (80, 10), (95, 0), (108, -20), (140, 0), (152, 22), (180, 6), (190, -18), (205, -4), (212, 4)]
    return render(h, marks, "C 三段の断崖", ["三つの大きな段 (台地) が縦の崖で区切られ、東へ登る。一段に一つずつ探す場所、頂の段の縁に主の間。",
                                            "段の上は広く、崖の面を登る所 (崩れる棚・跳び石) が見せ場。長所: 段ごとに区切りがはっきり。短所: 段の上が平らで単調になりやすい"], path)


if __name__ == "__main__":
    ims = [opt_a(), opt_b(), opt_c()]
    gap = 16
    cv = Image.new("RGB", (max(i.width for i in ims) + gap * 2, sum(i.height for i in ims) + gap * 4 + 50), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((gap, 12), "上弦の崖 (区画 2) の配置 3 案", font=voxel.font(20),
           fill=(236, 232, 218))
    d.text((gap, 38), "上から見た高さ (白いほど高い。月の面 y 11、頂 y 80) と東西の断面。黄の線 = 道", font=voxel.font(14), fill=(190, 196, 210))
    y = 64
    for im in ims:
        cv.paste(im, (gap, y))
        y += im.height + gap
    OUT.parent.mkdir(parents=True, exist_ok=True)
    cv.save(OUT)
    print("ok", cv.size)
