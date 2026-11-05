# -*- coding: utf-8 -*-
"""ティザー映像だけの一枚絵 (キービジュアル)。物語のドット絵 (tools/story/pixel) と同じ道具 pxkit で、480x270 に描いて 4 倍にする。

  python -X utf8 tools/gen/teaser/key_visual.py [a|b|c ...]

出力: docs/review/teaser_roughs/key_<案>.png (480x270) と key_<案>_x4.png (1920x1080)。上の 3 割は題を載せるので空けておく。
  A 空を渡る四巨神 (選んだ案。2026-11-05): 夕暮れの空いっぱいに岩鯨、遠くに潮殻・雷翼・余燼。崖の上の旅人が見上げる
  (B 天と淵のあいだ・C 巨神の背に立つ は選ばれず消した。見比べは key_compare.png)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tools" / "story" / "pixel"))
import numpy as np  # noqa: E402

import pxkit as K  # noqa: E402
import parts as P  # noqa: E402
from opening import moon, puffs  # noqa: E402

W, H = 480, 270
OUT = K.ROOT / "docs/review/teaser_roughs"


def titan(c, name, x, y, w, h, yaw, pitch=8, tint=None, silhouette=None):
    img, _ = K.render_mesh(name, (w, h), yaw=yaw, pitch=pitch, tint=tint, silhouette=silhouette)
    c.sprite(img, int(x - w / 2), int(y - h / 2))


def person(c, x, y, color, s=1.0, cape=None):
    # 旅人: 外套の裾が風になびく
    c.poly([(x - 2 * s, y), (x + 2 * s, y), (x + 1.6 * s, y - 9 * s), (x - 1.6 * s, y - 9 * s)], color)
    if cape:
        c.poly([(x + 1.6 * s, y - 8 * s), (x + 6 * s, y - 5 * s), (x + 5 * s, y - 2 * s), (x + 1.8 * s, y - 3 * s)], cape)
    c.disc(x, y - 11 * s, 2.0 * s, color)


def birds(c, pts, color):
    for x, y, s in pts:
        P.line(c, x - 2 * s, y - s, x, y, color)
        P.line(c, x, y, x + 2 * s, y - s, color)


def a():
    # 夕暮れ: 上は星の夜 (題の場所)、地平の右に沈む日。三重の雲の層、その上を四体が渡る。手前の崖の旅人が見上げる
    c = K.Canvas(W, H)
    c.vgrad([(0, (12, 14, 40)), (0.32, (34, 36, 80)), (0.55, (96, 70, 120)), (0.72, (200, 116, 104)), (0.86, (246, 170, 112)), (1, (252, 214, 150))])
    c.stars(170, 7, ymax=100)
    moon(c, 418, 40, 11)
    c.glow(400, 226, 150, (255, 170, 110), 1.1)
    c.disc(400, 222, 9, (255, 236, 190))
    # 遠い雲の層 (日の側の縁が明るい)
    puffs(c, 196, 46, 3, (176, 116, 128), (255, 210, 168), 400, rmin=5, rmax=12)
    titan(c, "ember_titan_far", 66, 172, 64, 30, yaw=60, tint=(0.55, 0.42, 0.55))
    titan(c, "tide_shell_far", 300, 182, 64, 28, yaw=-70, tint=(0.58, 0.46, 0.58))
    puffs(c, 210, 40, 5, (132, 84, 108), (238, 168, 140), 400, rmin=7, rmax=15)
    titan(c, "thunder_wing_far", 404, 122, 96, 44, yaw=-40, tint=(0.55, 0.5, 0.7))
    titan(c, "stone_whale", 212, 126, 320, 128, yaw=-100, pitch=4)
    birds(c, [(318, 96, 1.2), (330, 92, 1.0), (342, 99, 1.1), (126, 88, 0.9)], (30, 24, 40))
    puffs(c, 228, 34, 9, (88, 56, 84), (196, 124, 120), 400, rmin=9, rmax=18)
    # 手前の崖 (縁に夕日の光) と草
    c.poly([(286, 270), (314, 226), (350, 214), (392, 212), (430, 222), (480, 218), (480, 270)], (16, 12, 22))
    P.line(c, 314, 226, 350, 214, (120, 64, 60))
    P.line(c, 350, 214, 392, 212, (160, 84, 70))
    P.line(c, 392, 212, 430, 222, (120, 64, 60))
    for i, x in enumerate(range(318, 478, 7)):
        h = 2 + (i * 37) % 4
        P.line(c, x, 226 - (x - 318) * 0.08 + 6, x + 1, 226 - (x - 318) * 0.08 + 6 - h, (34, 26, 36))
    person(c, 372, 212, (10, 8, 14), 1.9, cape=(150, 44, 46))
    # 旅人の縁の光 (日の側)
    P.line(c, 375.2, 212, 375.2, 196, (200, 110, 80))
    return c


PAL = ["#0a0812", "#14101e", "#22203a", "#343a64", "#4a5a8c", "#6e80b0", "#a0b0d0", "#d8e0ee", "#ffffff",
       "#3a1820", "#6a2a2c", "#a04a3a", "#d07a54", "#f0b07a", "#f8d8a8",
       "#2a3020", "#4a5a34", "#7a8a50", "#5a4a40", "#8a7464", "#c8b090", "#e05040", "#ff9a60"]

OPTIONS = {"a": a}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    keys = sys.argv[1:] or list(OPTIONS)
    for k in keys:
        img = OPTIONS[k]().quantize(PAL, dither=0.9)
        img.save(OUT / ("key_%s.png" % k))
        img.resize((W * 4, H * 4), resample=0).save(OUT / ("key_%s_x4.png" % k))
        print("key", k)


if __name__ == "__main__":
    main()
