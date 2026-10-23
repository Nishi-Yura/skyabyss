#!/usr/bin/env python3
"""雷翼の心臓の「羽の雨」の粒 skyabyss:storm_feather の絵 (16 x 16、4 枚。粒ごとに一枚を選ぶ)。

  python tools/gen/ui/gen_storm_feather_particle.py

出力: src/main/resources/assets/skyabyss/textures/particle/storm_feather_{0..3}.png
羽: 斜めの細い羽 (軸は濃い灰青、羽弁は淡い氷の青から縁で少し濃く、根もとに雷の金の一点)。
前の粒 (end_rod・白い塵) は大きな白い四角に見えたので、輪郭のある小さな羽にする。粒の大きさと近くで薄れるのは StormFeatherParticle。
"""
import math
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "particle")

RACHIS = (74, 92, 122)
VANE_IN = (226, 240, 255)
VANE_OUT = (150, 184, 226)
EDGE = (58, 74, 104)
GOLD = (255, 214, 110)


def feather(curve, width, notch, length):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def axis(t):
        # 軸: 左下 (根) から右上 (先) へ、少し曲がる
        x = 2.5 + t * length + curve * 0.5 * math.sin(t * math.pi)
        y = 13.5 - t * length + curve * 0.5 * math.sin(t * math.pi)
        return x, y

    cover = {}
    for i in range(400):
        t = i / 399.0
        ax, ay = axis(t)
        # 羽弁の幅: 根 (羽柄) は無し、中ほどで広く、先で閉じる
        w = width * math.sin(min(1.0, (t - 0.12) / 0.88 * 1.05) * math.pi) ** 0.7 if t > 0.12 else 0
        nx, ny = 0.7071, 0.7071  # 軸に直交 (斜め)
        for k in range(-12, 13):
            s = k / 12.0 * w
            # 切れ込み (羽枝の割れ)
            if notch and abs(t - notch) < 0.05 and s > 0:
                continue
            key = (int(round(ax + nx * s)), int(round(ay + ny * s)))
            d = abs(s) / max(w, 1e-3)
            if cover.get(key, 9) >= 0:
                cover[key] = min(cover.get(key, 9), d)
        cover[(int(round(ax)), int(round(ay)))] = -1  # 軸
    for (x, y), d in cover.items():
        if not (0 <= x < 16 and 0 <= y < 16):
            continue
        c = RACHIS if d < 0 else tuple(int(a + (b - a) * d ** 1.5) for a, b in zip(VANE_IN, VANE_OUT))
        px[x, y] = c + (255,)
    # 縁取り (輪郭が読めるように、外側に半透明の暗い縁)
    edge = []
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] == 255
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge:
        px[x, y] = EDGE + (190,)
    # 根の金の一点
    bx, by = axis(0.03)
    px[int(round(bx)), int(round(by))] = GOLD + (255,)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    specs = [(0.8, 2.4, 0.55, 10.5), (-0.6, 2.0, 0.0, 10.0), (1.2, 2.7, 0.38, 11.0), (0.2, 1.8, 0.7, 9.0)]
    for i, sp in enumerate(specs):
        feather(*sp).save(os.path.join(OUT, "storm_feather_%d.png" % i))
    print("wrote", len(specs), "storm feather sprites")


if __name__ == "__main__":
    main()
