"""新月の谷 (六章 区画 1) の配置 3 案を上から見た図にする (docs/design/46 の 4.3)。
谷は着く所から南へ下る、長さ 160・幅 60 ほど。両側は玄武岩の崖、底は月の砂と割れた岩の尖り。真ん中を底の見えない裂け目が横切る。
置くもの: 野営 (入口の岩陰)・探す場所 3 (影の橋・鏡の窪・落ちた星の穴)・奥の門・主の間 (丸い窪み、まわりに灯りの台)。

A 一本の谷: 北の入口から南の主の間へ一本道。裂け目 (影の橋) を真ん中で渡り、鏡の窪・星の穴は道の脇
B 三つ叉の谷: 入口の野営から谷が三つに分かれ、それぞれの奥に探す場所。三つ揃うと真ん中の奥の門が開く
C 輪の谷: 谷の真ん中に高い岩の台地。谷は台地をぐるりと回り、裂け目が輪を二か所で断つ。主の間は台地の上 (門から登る)

  python tools/rough/moon_valley_layouts.py   -> docs/review/moon_roughs/valley_layouts.png
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import voxel

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/moon_roughs/valley_layouts.png"
W, L = 72, 172           # 図の範囲 (ブロック)。谷は x 6..66, z 6..166
S = 5                    # 1 ブロック = 5 px

DUST = np.array((150, 156, 168))
DUST2 = np.array((120, 126, 140))
CLIFF = np.array((40, 44, 58))
CLIFF_TOP = np.array((92, 96, 108))
CHASM = np.array((6, 6, 12))
BRIDGE = np.array((30, 30, 60))
ROAD = np.array((182, 186, 196))
SPIRE = np.array((70, 76, 92))


def noise(seed):
    rng = np.random.default_rng(seed)
    n = rng.random((L // 4 + 2, W // 4 + 2))
    from PIL import Image as I
    im = I.fromarray((n * 255).astype(np.uint8)).resize((W, L), I.BICUBIC)
    return np.asarray(im, float) / 255.0


def base(floor):
    """floor: (L, W) bool 谷底。崖は谷底のまわり (高さは谷底から離れるほど上がる)"""
    from scipy.ndimage import distance_transform_edt as edt
    d = edt(~floor)
    img = np.zeros((L, W, 3))
    n = noise(3)
    img[:] = DUST * (0.92 + 0.12 * n[..., None])
    rough = floor & (n > 0.72)
    img[rough] = DUST2
    cl = ~floor
    t = np.clip(d / 9.0, 0, 1)[..., None]
    img[cl] = (CLIFF * (1 - t) + CLIFF_TOP * t)[cl]
    # 崖の縁の影 (谷底側の 1 マス)
    from scipy.ndimage import binary_dilation
    rim = binary_dilation(cl, iterations=1) & floor
    img[rim] = img[rim] * 0.7
    return img


def disk(img, cx, cz, r, col, ring=None):
    zz, xx = np.mgrid[0:L, 0:W]
    m = (xx - cx) ** 2 + (zz - cz) ** 2 <= r * r
    img[m] = col
    if ring is not None:
        m2 = m & ((xx - cx) ** 2 + (zz - cz) ** 2 > (r - 1.2) ** 2)
        img[m2] = ring


def chasm(img, floor, pts, w):
    zz, xx = np.mgrid[0:L, 0:W]
    m = np.zeros((L, W), bool)
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        dx, dz = x1 - x0, z1 - z0
        t = np.clip(((xx - x0) * dx + (zz - z0) * dz) / (dx * dx + dz * dz), 0, 1)
        d = np.hypot(xx - (x0 + t * dx), zz - (z0 + t * dz))
        m |= d < w / 2
    m &= floor
    img[m] = CHASM
    return m


def road(img, pts, w=2.2):
    zz, xx = np.mgrid[0:L, 0:W]
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        dx, dz = x1 - x0, z1 - z0
        t = np.clip(((xx - x0) * dx + (zz - z0) * dz) / (dx * dx + dz * dz + 1e-9), 0, 1)
        d = np.hypot(xx - (x0 + t * dx), zz - (z0 + t * dz))
        m = d < w / 2
        img[m] = img[m] * 0.55 + ROAD * 0.45


def bridge(img, a, b, w=3):
    zz, xx = np.mgrid[0:L, 0:W]
    (x0, z0), (x1, z1) = a, b
    dx, dz = x1 - x0, z1 - z0
    t = np.clip(((xx - x0) * dx + (zz - z0) * dz) / (dx * dx + dz * dz), 0, 1)
    d = np.hypot(xx - (x0 + t * dx), zz - (z0 + t * dz))
    m = d < w / 2
    img[m] = BRIDGE
    # 橋の板の縞
    k = m & (((xx + zz) % 3) == 0)
    img[k] = BRIDGE * 1.6


def spires(img, floor, seed, n=40, keep=()):
    rng = random.Random(seed)
    for _ in range(n):
        x, z = rng.uniform(8, W - 8), rng.uniform(10, L - 10)
        if not floor[int(z), int(x)]:
            continue
        if any(math.hypot(x - kx, z - kz) < kr for kx, kz, kr in keep):
            continue
        disk(img, x, z, rng.uniform(0.9, 1.8), SPIRE)


def valley_mask(fn):
    zz, xx = np.mgrid[0:L, 0:W]
    return fn(xx.astype(float), zz.astype(float))


def render(img, marks, title, desc):
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W * S, L * S), Image.NEAREST)
    d = ImageDraw.Draw(im)
    f = voxel.font(14)
    COL = {"camp": (250, 200, 110), "place": (150, 210, 255), "gate": (220, 180, 255), "lord": (255, 120, 110), "land": (240, 240, 240)}
    for kind, x, z, label in marks:
        px, pz = x * S, z * S
        c = COL[kind]
        if kind == "lord":
            d.ellipse((px - 34, pz - 34, px + 34, pz + 34), outline=c, width=3)
            for i in range(6):
                a = i / 6 * 2 * math.pi
                qx, qz = px + math.cos(a) * 40, pz + math.sin(a) * 40
                d.rectangle((qx - 4, qz - 4, qx + 4, qz + 4), fill=(200, 220, 255))
        elif kind == "gate":
            d.rectangle((px - 12, pz - 5, px + 12, pz + 5), fill=c)
        elif kind == "land":
            d.polygon([(px, pz - 9), (px + 9, pz), (px, pz + 9), (px - 9, pz)], fill=c)
        else:
            d.ellipse((px - 9, pz - 9, px + 9, pz + 9), fill=c, outline=(20, 20, 30), width=2)
        tw = d.textlength(label, font=f)
        tx = px + 12 if px + 12 + tw < W * S - 4 else px - 12 - tw
        d.rectangle((tx - 3, pz - 9, tx + tw + 3, pz + 9), fill=(10, 12, 20))
        d.text((tx, pz - 8), label, font=f, fill=c)
    # 物差し (20 ブロック)
    d.line((8, L * S - 14, 8 + 20 * S, L * S - 14), fill=(240, 240, 240), width=3)
    d.text((8, L * S - 34), "20 ブロック", font=f, fill=(240, 240, 240))
    d.text((W * S - 40, 6), "北 ↑", font=f, fill=(240, 240, 240))
    head = Image.new("RGB", (W * S, 190), (16, 16, 22))
    hd = ImageDraw.Draw(head)
    hd.text((8, 6), title, font=voxel.font(20), fill=(236, 232, 218))
    y = 36
    fs = voxel.font(14)
    for line in desc:
        cur = ""
        for ch in line:
            if hd.textlength(cur + ch, font=fs) > W * S - 16:
                hd.text((8, y), cur, font=fs, fill=(190, 196, 210))
                y += 20
                cur = ""
            cur += ch
        hd.text((8, y), cur, font=fs, fill=(190, 196, 210))
        y += 20
    cv = Image.new("RGB", (W * S, 190 + L * S), (16, 16, 22))
    cv.paste(head, (0, 0))
    cv.paste(im, (0, 190))
    return cv


def opt_a():
    """一本の谷"""
    def f(x, z):
        c = 36 + 6 * np.sin(z / 22.0)
        w = 21 + 4 * np.sin(z / 13.0 + 1)
        m = np.abs(x - c) < w
        m |= (x - 36) ** 2 + (z - 150) ** 2 < 18 ** 2
        return m & (z > 6) & (z < 168)
    fl = valley_mask(f)
    img = base(fl)
    ch = chasm(img, fl, [(4, 74), (30, 80), (46, 74), (70, 82)], 7)
    bridge(img, (34, 70), (34, 86))
    disk(img, 18, 112, 6, DUST2 * 0.8, ring=np.array((210, 220, 240)))
    disk(img, 54, 122, 5.5, CHASM * 2, ring=np.array((70, 80, 100)))
    disk(img, 36, 150, 15, DUST2 * 0.85)
    spires(img, fl & ~ch, 5, 46, keep=[(30, 16, 8), (34, 78, 10), (18, 112, 8), (54, 122, 8), (36, 150, 18), (36, 136, 4)])
    road(img, [(36, 6), (32, 16), (34, 40), (34, 70)])
    road(img, [(34, 86), (30, 100), (22, 110), (32, 120), (48, 120), (40, 133), (36, 136)])
    marks = [("land", 36, 8, "着く所"), ("camp", 30, 18, "野営 (前の自分)"), ("place", 34, 78, "1 影の橋 (裂け目)"),
             ("place", 18, 112, "2 鏡の窪"), ("place", 54, 122, "3 落ちた星の穴"), ("gate", 36, 134, "奥の門"), ("lord", 36, 152, "主の間")]
    return render(img, marks, "A 一本の谷", ["入口から南の主の間へ一本道。迷わない。", "裂け目の影の橋を渡らないと先へ進めない (最初の探す場所 = 関所)。",
                                              "鏡の窪・星の穴は道の脇の窪地。", "長所: 分かりやすい。短所: 景色の変化が少ない。"])


def opt_b():
    """三つ叉の谷"""
    def f(x, z):
        m = (np.abs(x - 36) < 16) & (z > 6) & (z < 58)
        m |= (np.abs(x - 36) < 26) & (z > 40) & (z < 62)
        for (cx0, cz0), (cx1, cz1), w in (((20, 56), (8, 120), 10), ((36, 58), (36, 150), 9), ((52, 56), (64, 120), 10)):
            zz, xx = z, x
            t = np.clip(((xx - cx0) * (cx1 - cx0) + (zz - cz0) * (cz1 - cz0)) / ((cx1 - cx0) ** 2 + (cz1 - cz0) ** 2), 0, 1)
            m |= np.hypot(xx - (cx0 + t * (cx1 - cx0)), zz - (cz0 + t * (cz1 - cz0))) < w
        m |= (x - 12) ** 2 + (z - 124) ** 2 < 11 ** 2
        m |= (x - 60) ** 2 + (z - 124) ** 2 < 11 ** 2
        m |= (x - 36) ** 2 + (z - 152) ** 2 < 15 ** 2
        return m
    fl = valley_mask(f)
    img = base(fl)
    ch = chasm(img, fl, [(0, 96), (14, 92), (26, 100)], 7)
    for xb in (14, 35, 58):
        pass
    bridge(img, (17, 88), (13, 104))
    disk(img, 12, 124, 6, DUST2 * 0.8, ring=np.array((210, 220, 240)))
    disk(img, 60, 124, 5.5, CHASM * 2, ring=np.array((70, 80, 100)))
    disk(img, 36, 152, 13, DUST2 * 0.85)
    spires(img, fl & ~ch, 9, 46, keep=[(30, 18, 8), (15, 96, 10), (12, 124, 8), (60, 124, 8), (36, 152, 16), (36, 118, 6)])
    road(img, [(36, 6), (34, 20), (36, 50)])
    road(img, [(36, 50), (20, 60), (17, 88)])
    road(img, [(13, 104), (12, 116)])
    road(img, [(36, 50), (52, 60), (60, 90), (60, 116)])
    road(img, [(36, 50), (36, 84)])
    marks = [("land", 36, 8, "着く所"), ("camp", 30, 20, "野営 (前の自分)"), ("place", 15, 96, "1 影の橋"),
             ("place", 12, 124, "2 鏡の窪"), ("place", 60, 124, "3 落ちた星の穴"), ("gate", 36, 118, "奥の門 (欠片 3 で開く)"), ("lord", 36, 152, "主の間")]
    return render(img, marks, "B 三つ叉の谷", ["入口の広場から谷が三つに分かれ、左右の奥に探す場所。", "真ん中の谷は奥の門で閉じ、欠片 3 つで開く。裂け目は左の谷だけを断つ。",
                                                "長所: 順番を選べる。短所: 行って戻る道が増える。"])


def opt_c():
    """輪の谷"""
    def f(x, z):
        cx, cz = 36, 92
        r = np.hypot((x - cx) / 1.0, (z - cz) / 2.0)
        m = (r > 12) & (r < 30)
        m |= (np.abs(x - 36) < 14) & (z > 6) & (z < 40)
        return m & (z > 6) & (z < 168)
    fl = valley_mask(f)
    img = base(fl)
    # 台地 (真ん中の高い岩): 上は灰の平ら
    zz, xx = np.mgrid[0:L, 0:W]
    mesa = np.hypot(xx - 36, (zz - 92) / 2.0) < 11
    img[mesa] = CLIFF_TOP * 1.3
    ch = chasm(img, fl, [(0, 108), (24, 112), (36, 104), (48, 112), (72, 106)], 6)
    bridge(img, (14, 104), (14, 118))
    disk(img, 58, 70, 5.5, DUST2 * 0.8, ring=np.array((210, 220, 240)))
    disk(img, 36, 148, 5.5, CHASM * 2, ring=np.array((70, 80, 100)))
    disk(img, 36, 92, 9, DUST2 * 0.85)
    spires(img, fl & ~ch, 11, 50, keep=[(30, 18, 8), (14, 110, 10), (58, 70, 8), (36, 148, 8), (36, 74, 6)])
    road(img, [(36, 6), (34, 20), (36, 40), (20, 60), (12, 90), (14, 104)])
    road(img, [(14, 118), (22, 140), (36, 148), (52, 140), (62, 110), (58, 70), (48, 52), (36, 40)])
    road(img, [(36, 64), (36, 76)])
    marks = [("land", 36, 8, "着く所"), ("camp", 30, 20, "野営 (前の自分)"), ("place", 14, 110, "1 影の橋"),
             ("place", 58, 70, "2 鏡の窪"), ("place", 36, 148, "3 落ちた星の穴"), ("gate", 36, 66, "奥の門 (台地へ登る)"), ("lord", 36, 92, "主の間 (台地の上)")]
    return render(img, marks, "C 輪の谷", ["谷の真ん中に高い岩の台地。谷は台地をぐるりと回る。", "裂け目が輪を断ち、影の橋で渡ると一周できる。三つ揃うと台地の門が開く。",
                                            "主の間は台地の上で、谷じゅうから見える。長所: 目印がいつも見える。短所: 作る量が多い。"])


if __name__ == "__main__":
    ims = [opt_a(), opt_b(), opt_c()]
    gap = 16
    cv = Image.new("RGB", (sum(i.width for i in ims) + gap * 4, max(i.height for i in ims) + 60 + gap), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((gap, 12), "新月の谷 (六章 区画 1) の配置 3 案 — 上から見た図 (北が上・着く所から南へ下る)", font=voxel.font(22), fill=(236, 232, 218))
    d.text((gap, 40), "■ 黒 = 裂け目 / 藍の縞 = 影の橋 / 灰の点 = 岩の尖り / 白い筋 = 道 / 主の間のまわりの白い四角 = 灯りの台", font=voxel.font(13), fill=(190, 196, 210))
    x = gap
    for im in ims:
        cv.paste(im, (x, 60 + gap // 2))
        x += im.width + gap
    OUT.parent.mkdir(parents=True, exist_ok=True)
    cv.save(OUT)
    print("ok", OUT, cv.size)
