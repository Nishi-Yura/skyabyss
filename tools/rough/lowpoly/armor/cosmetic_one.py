"""飾り装備 (docs/design/54 の 6.1。職を問わない見た目だけの品) の一品一案。最初の組 = 巨神の外套 4 品。
どれも今の防具の上に羽織る (胴の骨 armorBody。背の布は防具の背 z 3 の外 3.4 から)。色違いにしない: 形がそれぞれ違う。
  岩鯨の外套 (whale)  : 鯨の革の長い外套。裾は尾鰭の二つの葉、肩に鯨骨のくびき (前で骨の留め具)、左肩に藤壺の群れ
  潮殻の肩掛け (tide) : 肩を覆う貝殻の鱗の短い肩掛け (三段)、そこから垂れる海藻の帯 5 本、胸に真珠の留め
  雷翼の片羽 (thunder): 右肩だけの羽の外套 (風切羽が背の右へ流れる)。羽の縁に光る稲妻の筋、胸に銅の留め輪
  余燼の外套 (ember)  : 頭巾を首の後ろに下ろした灰の外套。裾は焦げて裂け、裂け目の先に熾火。胸に鎖の留め
実行: python cosmetic_one.py  -> docs/review/cosmetic_one/titan_capes.png (それぞれ 前・横・後ろ・3/4。下に巨神の通常の防具を着せる)
"""
from __future__ import annotations

import math
import sys

from PIL import Image, ImageDraw

import aview
import build_one
import titan_one
from akit import *  # noqa: F401,F403
from akit import V, bx, crv, spk, shell, feather, ROOT

B_ = "armorBody"
OUT = ROOT / "docs/review/cosmetic_one"


def slab_ring(y, z, hw, bulge, th, n=9, dy=None, cx=0.0):
    """布の板の一段 (x の向きに -hw..hw、前の面 z、後ろの面 z + th。真ん中が bulge だけ後ろへふくらむ)。閉じた輪"""
    front, back = [], []
    for i in range(n):
        t = -1 + 2 * i / (n - 1)
        x = cx + hw * t
        zz = z + bulge * (1 - t * t)
        yy = y + (dy[i] if dy is not None else 0.0)
        front.append(V(x, yy, zz))
        back.append(V(x, yy, zz + th))
    return front + back[::-1]


def cloak(mat, rows, th=0.5, n=9, caps=(True, True)):
    """rows = [(y, z, hw, bulge, dy)]。上から下へ"""
    return shell(B_, mat, [slab_ring(y, z, hw, b, th, n, dy) for (y, z, hw, b, dy) in rows], caps=caps)


# ---------------------------------------------------------------- 岩鯨の外套
def whale():
    f = []
    # 長い外套 (暗い鯨の革): 肩 (y 24) から膝の下まで。下へ広がり、裾は骨色の帯 (尾鰭の二つの葉の形)
    fluke = [2.2, 0.6, -0.6, -1.4, 1.6, -1.4, -0.6, 0.6, 2.2]
    f += cloak("cloth", [(24.2, 2.9, 4.6, 0.3, None), (20.0, 3.3, 5.0, 0.6, None), (14.0, 3.7, 5.6, 1.0, None),
                         (8.0, 4.2, 6.1, 1.3, None), (5.0, 4.45, 6.3, 1.4, [d * 0.6 for d in fluke])], th=0.55)
    f += cloak("sec", [(5.2, 4.4, 6.32, 1.4, [d * 0.6 for d in fluke]), (3.2, 4.7, 6.5, 1.5, fluke)], th=0.65)
    # 肩のくびき: 太い鯨骨の弧が肩の上を前から後ろへ (左右)。前で胸の骨の留め具に合わさる
    for s in (-1, 1):
        f += crv(B_, "edge", [(s * 1.2, 23.0, -3.2), (s * 3.2, 24.8, -2.7), (s * 4.8, 25.2, 0.0), (s * 4.5, 24.4, 3.0)], 1.0, 0.8, sides=5)
        # 肋の牙 (肩の外へ反る)
        f += spk(B_, "edge", (s * 4.6, 24.8, 1.2), (s * 0.8, 0.7, 0.3), 3.0, 0.55, sides=4, rings=2)
    f += bx(B_, "edge", (0, 22.6, -3.3), (3.2, 1.9, 0.9))
    f += spk(B_, "gem", (0, 22.6, -3.8), (0, 0, -1), 0.7, 0.5, sides=5, rings=1)
    # 左肩の藤壺の群れ (くびきの上)
    f += titan_one.barnacle_cluster(B_, (4.4, 25.3, 0.8), 8, 1.5, r=0.45, h=0.9)
    # 外套の背: 骨の背骨の列 (尾へ細る)
    for k in range(6):
        y = 22.0 - k * 2.9
        z = 3.55 + 0.33 * k * 0.9 + 0.9 * (1 - (0 ** 2)) * 0.6
        f += bx(B_, "edge", (0, y, z + 0.35), (1.4 - k * 0.12, 1.1, 0.6))
    return {"cape": f}


# ---------------------------------------------------------------- 潮殻の肩掛け
def scallop(c, d_out, w, L, mat="main"):
    """貝殻一枚 (扇の板)。c = 付け根、d_out = 開く向き"""
    return feather(B_, mat, c, (c[0] + d_out[0] * L, c[1] + d_out[1] * L, c[2] + d_out[2] * L), w, th=0.35, big=True, n=(0, 0, 1))


def tide():
    f = []
    # 三段の貝殻の鱗: 首のまわりから肩・背へ、下向きに寝かせて重ねる。段ごとに一回り大きく、下の段が上の段の下へ潜る
    for row, (y, r, n, L, w) in enumerate(((25.0, 4.2, 9, 2.6, 1.0), (23.6, 5.0, 11, 3.0, 1.15), (22.0, 5.6, 13, 3.3, 1.25))):
        for i in range(n):
            a = math.radians(-115 + 230 * i / (n - 1))   # 0 = 背の真ん中 (+Z)。前 (-Z) の真ん中は開ける
            x, z = math.sin(a) * r, math.cos(a) * r * 0.66
            if z < -1.4:
                continue
            base = (x, y, z)
            tip = (x * 1.08, y - L, z * 1.08 + 0.15)
            f += feather(B_, "main" if (i + row) % 2 else "sec", base, tip, w, th=0.35, big=True, n=(math.sin(a), 0.4, math.cos(a)))
    # 海藻の帯 5 本 (背の肩掛けの下から、裾 y 4 まで。平たく、少し波打つ)
    for k, x in enumerate((-3.6, -1.8, 0.0, 1.8, 3.6)):
        top = 19.6 + (0.4 if k % 2 else 0.0)
        bot = 4.0 + 1.2 * (k % 2)
        rows = []
        for j in range(5):
            t = j / 4
            yy = top + (bot - top) * t
            rows.append(slab_ring(yy, 3.9 + t * 1.3, 0.75 - 0.25 * t, 0.0, 0.3, 3, cx=x + 0.5 * math.sin(t * 5 + k)))
        f += shell(B_, "cloth", rows, caps=(True, True))
    # 胸の真珠の留め (左右の鱗をつなぐ紐と真珠三つ)
    f += crv(B_, "cord", [(-2.4, 23.4, -3.3), (0, 22.6, -3.6), (2.4, 23.4, -3.3)], 0.22, 0.22, sides=4)
    for x in (-1.0, 0.0, 1.0):
        f += spk(B_, "gem", (x, 22.4 - (0.3 if x == 0 else 0), -3.7), (0, 0, -1), 0.5, 0.4, sides=6, rings=1)
    return {"cape": f}


# ---------------------------------------------------------------- 雷翼の片羽
def thunder():
    f = []
    # 右肩の羽の肩当て (肩を覆って下へ重なる短い羽)
    for k in range(5):
        a = math.radians(-60 + k * 30)
        bx_, bz = -4.2 + math.sin(a) * 0.4, math.sin(a) * 2.2
        f += feather(B_, "main" if k % 2 else "sec", (bx_, 25.2, bz), (-7.0, 20.4 - (k % 2) * 0.8, bz * 1.2), 1.2, th=0.3, n=(-0.8, 0.5, 0))
    # 背の右へ流れる長い風切羽 (肩の下から腰の下へ。外ほど長い)
    for k in range(7):
        x = -0.4 - k * 0.85
        tip_y = 12.0 - k * 1.15
        f += feather(B_, "main" if k % 2 == 0 else "sec", (x, 23.0 - k * 0.2, 3.3 + k * 0.05), (x - 0.6 - k * 0.25, tip_y, 4.6 + k * 0.2), 1.0, th=0.28, n=(0, 0, 1))
    # 羽の縁の稲妻の筋 (光る。風切羽の外の縁に沿って折れる)
    f += crv(B_, "gem", [(-6.3, 22.8, 3.8), (-7.0, 18.6, 4.4), (-6.0, 17.0, 4.5), (-6.9, 12.0, 5.0), (-6.0, 10.4, 5.1), (-6.8, 5.6, 5.4)], 0.3, 0.22, sides=4)
    # 胸の銅の留め輪 (右肩の羽から左の脇へ渡す革の帯と輪)
    f += crv(B_, "cord", [(-3.6, 23.8, -2.9), (-0.6, 20.6, -3.3), (2.6, 17.4, -3.0), (4.2, 16.0, -1.0)], 0.35, 0.35, sides=4)
    ring = [(math.cos(math.radians(a)) * 0.9 - 0.6, 20.6 + math.sin(math.radians(a)) * 0.9, -3.6) for a in range(0, 361, 45)]
    f += crv(B_, "metal", ring, 0.28, 0.28, sides=4)
    return {"cape": f}


# ---------------------------------------------------------------- 余燼の外套
def ember():
    f = []
    # 外套 (灰の布): 裾は焦げて裂け、裾の帯は焦げ色 (trim)
    rag = [0.8, -1.2, 0.4, -1.8, 0.2, -1.0, 1.2, -1.6, 0.6]
    f += cloak("cloth", [(23.6, 3.0, 4.4, 0.4, None), (18.0, 3.4, 5.0, 0.8, None), (11.0, 3.9, 5.6, 1.1, None),
                         (6.6, 4.25, 5.9, 1.25, [d * 0.5 for d in rag])], th=0.5)
    f += cloak("trim", [(6.8, 4.2, 5.92, 1.25, [d * 0.5 for d in rag]), (5.0, 4.4, 6.0, 1.3, rag)], th=0.6)
    # 頭巾を首の後ろに下ろした塊 (丸い襟巻き。外套より明るい灰)
    f += shell(B_, "sec", [slab_ring(26.0, 1.6, 3.4, 0.8, 1.8, 9), slab_ring(24.6, 2.2, 4.6, 1.4, 2.2, 9), slab_ring(23.0, 2.8, 4.4, 1.2, 1.5, 9),
                           slab_ring(22.0, 3.1, 3.4, 0.9, 0.8, 9)], caps=(True, True))
    # 裂け目の先の熾火 (光る) と、背を走る熱の筋
    for i, x in enumerate((-4.5, -1.5, 1.5, 4.5)):
        y = 5.0 + rag[[1, 3, 5, 7][i]] - 0.1
        f += spk(B_, "gem", (x, y + 0.6, 4.9), (0, -1, 0.1), 1.2, 0.35, sides=4, rings=1)
    f += crv(B_, "gem", [(-1.0, 21.4, 3.7), (0.6, 17.0, 4.3), (-0.4, 12.0, 4.6), (0.8, 7.2, 5.0)], 0.24, 0.2, sides=4)
    # 胸の鎖の留め (輪を三つと、左右の金具)
    for k, x in enumerate((-1.6, 0.0, 1.6)):
        ring = [(x + math.cos(math.radians(a)) * 0.7, 23.2 - abs(x) * 0.15 + math.sin(math.radians(a)) * 0.5, -3.3) for a in range(0, 361, 60)]
        f += crv(B_, "metal", ring, 0.22, 0.22, sides=4)
    f += bx(B_, "metal", (-3.0, 23.6, -2.9), (1.2, 1.2, 0.8))
    f += bx(B_, "metal", (3.0, 23.6, -2.9), (1.2, 1.2, 0.8))
    return {"cape": f}


CAPES = [("whale", "岩鯨の外套", whale), ("tide", "潮殻の肩掛け", tide), ("thunder", "雷翼の片羽", thunder), ("ember", "余燼の外套", ember)]


def main():
    aview.SS = 2
    OUT.mkdir(parents=True, exist_ok=True)
    TW, TH = 260, 420
    views = ((180, 6, True, "前 (防具の上)"), (0, 6, True, "後ろ (防具の上)"), (0, 6, False, "後ろ (外套だけ)"), (215, 14, False, "3/4 (外套だけ)"))
    rows = []
    for T, label, fn in CAPES:
        cape = aview.build_set(fn(), T, "common")
        full = aview.build_set(titan_one.build(T, "common"), T, "common")
        full.update(cape)
        im = Image.new("RGB", (TW * 4, TH + 26), aview.PLAT)
        dr = ImageDraw.Draw(im)
        for i, (yaw, pit, armor, nm) in enumerate(views):
            im.paste(aview.render(full if armor else cape, T, yaw, pit, (TW, TH), 8.2, center=(0, 19.0, 0)), (TW * i, 0))
            dr.text((TW * i + 8, TH + 4), nm if i else f"{label}  {cape['cape']['tris']} 面", font=build_one.font(15), fill=(230, 222, 200))
        rows.append(im)
    S = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
    y = 0
    for r in rows:
        S.paste(r, (0, y))
        y += r.height
    S.save(OUT / "titan_capes.png")
    print("wrote", OUT / "titan_capes.png")


if __name__ == "__main__":
    main()
