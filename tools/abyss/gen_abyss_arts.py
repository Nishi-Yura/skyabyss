"""深淵術の絵 (docs/design/36 の 5 節・アイコン 32x32)。ラフ 3 案ずつ、案 A を仮の絵として使う。

出力:
  docs/review/abyss_arts/roughs.png              選定ページ用のシート (3 術 x 3 案。見やすく 4 倍)
  src/main/resources/assets/skyabyss/textures/gui/abyss_art/<術>.png   案 A (仮)

実行: python tools/abyss/gen_abyss_arts.py   (Pillow が要る)
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SIZE = 32
ARTS = ["light", "weight", "heartbeat"]
ROUGH_DIR = os.path.join(ROOT, "docs", "review", "abyss_arts")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "gui", "abyss_art")

BASE = (22, 26, 40, 255)
EDGE = (120, 150, 200, 255)
GLOW = (170, 220, 255, 255)
GOLD = (240, 210, 130, 255)
RED = (200, 80, 90, 255)
BONE = (230, 230, 220, 255)


def base(img):
    d = ImageDraw.Draw(img)
    d.ellipse((1, 1, SIZE - 2, SIZE - 2), fill=BASE, outline=EDGE)
    return d


def light_a(img):
    # 太陽のような光の輪と四つの筋
    d = base(img)
    cx = cy = 16
    d.ellipse((10, 10, 22, 22), fill=GLOW)
    for k in range(8):
        a = k * math.pi / 4
        d.line((cx + 8 * math.cos(a), cy + 8 * math.sin(a), cx + 12 * math.cos(a), cy + 12 * math.sin(a)), fill=GOLD, width=2)


def light_b(img):
    # 灯り: 枠と炎
    d = base(img)
    d.rectangle((11, 9, 21, 23), outline=GOLD, width=2)
    d.polygon([(16, 11), (19, 17), (16, 21), (13, 17)], fill=GLOW)
    d.rectangle((9, 7, 23, 9), fill=GOLD)
    d.rectangle((9, 23, 23, 25), fill=GOLD)


def light_c(img):
    # 目と星: 暗い中で見えるもの
    d = base(img)
    d.ellipse((7, 11, 25, 21), outline=GLOW, width=2)
    d.ellipse((13, 13, 19, 19), fill=GLOW)
    for dx, dy in ((4, 4), (-4, -5), (5, -6)):
        d.point((16 + dx * 2, 16 + dy * 2), fill=GOLD)


def weight_a(img):
    # 重りが下へ引く矢印
    d = base(img)
    d.rectangle((13, 6, 19, 18), fill=BONE)
    d.polygon([(10, 18), (22, 18), (16, 26)], fill=EDGE)
    d.rectangle((8, 26, 24, 28), fill=BONE)


def weight_b(img):
    # 羽根: 軽くなる
    d = base(img)
    d.polygon([(8, 22), (14, 6), (24, 10), (20, 24)], fill=BONE)
    d.line((10, 23, 22, 9), fill=EDGE, width=1)


def weight_c(img):
    # 石を積んだ塔が浮く
    d = base(img)
    d.rectangle((10, 20, 22, 25), fill=BONE)
    d.rectangle((12, 14, 20, 19), fill=BONE)
    d.rectangle((14, 9, 18, 13), fill=BONE)
    d.arc((5, 5, 27, 27), 200, 340, fill=GLOW, width=1)


def heartbeat_a(img):
    # 心臓と鼓動の線
    d = base(img)
    d.ellipse((7, 8, 15, 16), fill=RED)
    d.ellipse((17, 8, 25, 16), fill=RED)
    d.polygon([(7, 13), (25, 13), (16, 25)], fill=RED)
    d.line((4, 17, 11, 17, 13, 13, 16, 20, 19, 15, 21, 17, 28, 17), fill=GLOW, width=1)


def heartbeat_b(img):
    # 波紋: 壁の向こうへ響く
    d = base(img)
    for r in (4, 8, 12):
        d.ellipse((16 - r, 16 - r, 16 + r, 16 + r), outline=GLOW if r != 8 else GOLD)
    d.ellipse((13, 13, 19, 19), fill=RED)


def heartbeat_c(img):
    # 骨の鈴: 鳴らすと聞こえる
    d = base(img)
    d.line((16, 6, 16, 22), fill=BONE, width=2)
    d.ellipse((11, 19, 21, 27), fill=BONE)
    d.ellipse((13, 4, 19, 10), fill=GOLD)


DRAW = {
    "light": [light_a, light_b, light_c],
    "weight": [weight_a, weight_b, weight_c],
    "heartbeat": [heartbeat_a, heartbeat_b, heartbeat_c],
}


def render(fn):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    fn(img)
    return img


def main():
    os.makedirs(ROUGH_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    scale = 4
    sheet = Image.new("RGBA", (3 * (SIZE * scale + 16) + 16, len(ARTS) * (SIZE * scale + 16) + 16), (40, 40, 48, 255))
    for row, art in enumerate(ARTS):
        for col, fn in enumerate(DRAW[art]):
            img = render(fn)
            big = img.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            sheet.alpha_composite(big, (16 + col * (SIZE * scale + 16), 16 + row * (SIZE * scale + 16)))
            if col == 0:
                img.save(os.path.join(TEX_DIR, art + ".png"))
    sheet.save(os.path.join(ROUGH_DIR, "roughs.png"))
    print("wrote", os.path.join(ROUGH_DIR, "roughs.png"))


if __name__ == "__main__":
    main()
