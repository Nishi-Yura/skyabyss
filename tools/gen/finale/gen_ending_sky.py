# -*- coding: utf-8 -*-
"""終章の結末のあとの空の絵 (docs/design/50 の 4.4。client/sky/EndingSkyClient が方向に置く板に貼る)。

  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/finale/gen_ending_sky.py

  textures/environment/finale_crack.png  壊す: 空の裂け目 (256x52。芯は深い藍、縁は白い光と淡い紫。外は透明)
  textures/environment/finale_wing.png   受け入れる: 巡礼者の翼 (128x128。巡礼者の羽の形を淡い金と白の影絵に。外は透明)
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/environment"
sys.path.insert(0, str(ROOT / "tools/story/pixel"))
import pxkit as K  # noqa: E402


def crack():
    W, H = 256, 52
    rnd = random.Random(7)
    pts = []
    for i in range(23):
        x = 6 + i * (W - 12) / 22
        y = H / 2 + rnd.uniform(-10, 10) * (1 - abs(i - 11) / 14)
        pts.append((x, y))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.line(pts, fill=(200, 170, 240, 200), width=9)
    glow = glow.filter(ImageFilter.GaussianBlur(3))
    edge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(edge)
    d.line(pts, fill=(250, 244, 255, 255), width=5)
    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(core)
    d.line(pts, fill=(22, 12, 44, 255), width=3)
    # 枝分かれの小さな裂け目
    for i in (4, 9, 14, 18):
        x, y = pts[i]
        x2, y2 = x + rnd.uniform(6, 14), y + rnd.choice((-1, 1)) * rnd.uniform(6, 12)
        ImageDraw.Draw(edge).line([(x, y), (x2, y2)], fill=(240, 230, 255, 220), width=3)
        ImageDraw.Draw(core).line([(x, y), (x2, y2)], fill=(30, 18, 54, 255), width=1)
    img = Image.alpha_composite(Image.alpha_composite(glow, edge), core)
    # 端は細く消える
    px = img.load()
    for x in range(W):
        f = min(1.0, min(x, W - 1 - x) / 30.0)
        for y in range(H):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, int(a * f))
    img.save(OUT / "finale_crack.png")


def wing():
    img, _ = K.render_mesh("pilgrim_plume", (128, 128), yaw=180, pitch=0)
    im = Image.fromarray(img.astype("uint8"), "RGBA").copy()
    # 淡い金と白に寄せ、少しぼかした光の縁
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                l = (r + g + b) / 3
                px[x, y] = (int(200 + l * 0.22), int(190 + l * 0.22), int(150 + l * 0.3), int(a * 0.92))
    halo = im.filter(ImageFilter.GaussianBlur(3))
    hp = halo.load()
    for y in range(halo.height):
        for x in range(halo.width):
            r, g, b, a = hp[x, y]
            hp[x, y] = (255, 236, 190, int(a * 0.5))
    Image.alpha_composite(halo, im).save(OUT / "finale_wing.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    crack()
    wing()
    print("ok")
