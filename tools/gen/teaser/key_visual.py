# -*- coding: utf-8 -*-
"""ティザー映像だけの一枚絵 (キービジュアル)。物語のドット絵 (tools/story/pixel) と同じ道具 pxkit で、480x270 に描いて 4 倍にする。

  python -X utf8 tools/gen/teaser/key_visual.py [a|b|c ...]

出力: docs/review/teaser_roughs/key_<案>.png (480x270) と key_<案>_x4.png (1920x1080)。上の 3 割は題を載せるので空けておく。
  A 空を渡る四巨神: 夕暮れの空いっぱいに岩鯨、遠くに潮殻・雷翼・余燼。崖の上の旅人が見上げる
  B 天と淵のあいだ: 上は月と岩鯨の空、下は赤く光る淵。あいだの浮き島に旅人
  C 巨神の背に立つ: 雲海の上を泳ぐ岩鯨の背の上に旅人。朝の光、遠くに三体
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


def a():
    c = K.Canvas(W, H)
    c.vgrad([(0, (18, 20, 52)), (0.45, (70, 60, 110)), (0.75, (210, 120, 90)), (1, (250, 190, 120))])
    c.stars(120, 7, ymax=90)
    moon(c, 400, 46, 13)
    puffs(c, 200, 9, 3, (150, 110, 130), (240, 170, 140), 0.8, rmin=8, rmax=20)
    titan(c, "ember_titan_far", 70, 150, 70, 34, yaw=60, tint=(0.55, 0.45, 0.6))
    titan(c, "thunder_wing_far", 395, 120, 90, 40, yaw=-40, tint=(0.6, 0.55, 0.75))
    titan(c, "tide_shell_far", 300, 175, 70, 30, yaw=-70, tint=(0.6, 0.5, 0.65))
    titan(c, "stone_whale", 225, 128, 300, 120, yaw=-100, pitch=4)
    c.ridge(238, 14, 0.03, 11, (24, 18, 30), rough=0.4)
    c.poly([(300, 270), (330, 222), (372, 214), (420, 230), (480, 226), (480, 270)], (14, 10, 20))
    person(c, 370, 214, (10, 8, 14), 1.4, cape=(60, 30, 40))
    return c


def b():
    c = K.Canvas(W, H)
    c.vgrad([(0, (8, 12, 34)), (0.42, (40, 52, 96)), (0.55, (24, 20, 40)), (0.8, (40, 10, 14)), (1, (20, 4, 8))])
    c.stars(140, 9, ymax=110)
    moon(c, 96, 52, 16)
    titan(c, "stone_whale", 320, 70, 230, 90, yaw=-80, pitch=10)
    # 淵の光
    c.glow(240, 262, 120, (220, 60, 40), 1.6)
    c.glow(240, 270, 50, (255, 150, 90), 1.2)
    for i in range(7):
        P.bolt(c, 240 + (i - 3) * 30, 270, 240 + (i - 3) * 8, 210, 40 + i, color=(250, 120, 80), glow=(120, 30, 20))
    # あいだの浮き島
    c.poly([(170, 168), (310, 168), (290, 182), (262, 204), (232, 214), (206, 198), (184, 182)], (40, 34, 44))
    c.poly([(170, 168), (310, 168), (304, 172), (176, 172)], (90, 110, 70))
    person(c, 236, 168, (10, 10, 16), 1.5, cape=(90, 40, 50))
    return c


def cc():
    c = K.Canvas(W, H)
    c.vgrad([(0, (60, 100, 170)), (0.55, (170, 190, 220)), (0.8, (250, 220, 170)), (1, (250, 240, 220))])
    c.glow(400, 200, 120, (255, 220, 160), 1.0)
    puffs(c, 220, 14, 5, (220, 220, 236), (255, 255, 255), 0.85, rmin=10, rmax=26)
    titan(c, "thunder_wing_far", 80, 110, 80, 36, yaw=40, tint=(0.75, 0.8, 0.95))
    titan(c, "tide_shell_far", 140, 150, 60, 26, yaw=50, tint=(0.75, 0.8, 0.95))
    titan(c, "ember_titan_far", 420, 112, 60, 28, yaw=-50, tint=(0.8, 0.8, 0.95))
    titan(c, "stone_whale", 250, 200, 420, 150, yaw=-90, pitch=22)
    person(c, 262, 150, (20, 18, 26), 1.6, cape=(140, 50, 50))
    return c


PAL = ["#0a0812", "#14101e", "#22203a", "#343a64", "#4a5a8c", "#6e80b0", "#a0b0d0", "#d8e0ee", "#ffffff",
       "#3a1820", "#6a2a2c", "#a04a3a", "#d07a54", "#f0b07a", "#f8d8a8",
       "#2a3020", "#4a5a34", "#7a8a50", "#5a4a40", "#8a7464", "#c8b090", "#e05040", "#ff9a60"]

OPTIONS = {"a": a, "b": b, "c": cc}


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
