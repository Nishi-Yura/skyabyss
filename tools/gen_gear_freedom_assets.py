"""装備の自由度の品の絵を作る (欠片 5 種、アスペクトの芯、技の修飾ルーン)。32x32、暗い縁取りと 3 段の面。

使い方: python tools/gen_gear_freedom_assets.py
出力先: src/main/resources/assets/skyabyss/textures/item/{socket_shard_<属性>,aspect_core,skill_rune}.png
欠片は角のある石の形に、属性の色と小さな印 (岩=四角、風=三本線、潮=波、灰=炎、骨=十字)。
芯は金の八角に渦、ルーンは暗い板に紫の刻み。何度実行しても同じ物ができる。
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/textures/item"
OUTLINE = (14, 16, 22, 255)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def facet(img, pts, base, light=1.25, dark=0.62):
    """多角形を縁取りし、左上を明るく、右下を暗く塗る。"""
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=OUTLINE)
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    inner = [(cx + (x - cx) * 0.82, cy + (y - cy) * 0.82) for x, y in pts]
    d.polygon(inner, fill=shade(base, 1.0))
    # 面: 左上の明るい三角と右下の暗い三角
    n = len(inner)
    top = [inner[0], inner[1 % n], (cx, cy)]
    low = [inner[n // 2], inner[(n // 2 + 1) % n], (cx, cy)]
    d.polygon(top, fill=shade(base, light))
    d.polygon(low, fill=shade(base, dark))
    d.polygon([inner[n - 1], inner[0], (cx, cy)], fill=shade(base, light * 0.92))
    d.polygon([inner[n // 2 - 1], inner[n // 2], (cx, cy)], fill=shade(base, dark * 1.15))


def symbol(img, kind, color):
    d = ImageDraw.Draw(img)
    c = color
    if kind == "stone":
        d.rectangle([13, 13, 18, 18], outline=c)
        d.point((15, 15), fill=c)
    elif kind == "wind":
        for dy in (-3, 0, 3):
            d.line([(12, 16 + dy), (19, 16 + dy)], fill=c)
    elif kind == "tide":
        for i in range(0, 8):
            d.point((12 + i, 15 + (1 if (i // 2) % 2 else 0)), fill=c)
            d.point((12 + i, 19 + (1 if (i // 2) % 2 else 0)), fill=c)
    elif kind == "ash":
        d.polygon([(16, 11), (19, 17), (17, 21), (14, 21), (12, 17), (14, 15)], fill=c)
    elif kind == "bone":
        d.line([(16, 11), (16, 21)], fill=c)
        d.line([(12, 15), (20, 15)], fill=c)


ELEMENTS = {
    "stone": (150, 142, 124),
    "wind": (212, 172, 64),
    "tide": (62, 178, 168),
    "ash": (222, 104, 52),
    "bone": (214, 204, 176),
}


def shard(kind, base):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # 欠片: 少し傾いた六角の石。穴に差す形なので、上に小さな切り欠き
    pts = [(10, 6), (21, 4), (28, 12), (26, 25), (14, 28), (5, 19)]
    facet(img, pts, base)
    d = ImageDraw.Draw(img)
    d.line([(12, 8), (20, 6)], fill=shade(base, 1.6))
    d.point((8, 17), fill=shade(base, 1.5))
    symbol(img, kind, shade(base, 0.38))
    return img


def core():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    gold = (232, 188, 84)
    pts = [(11, 3), (21, 3), (29, 11), (29, 21), (21, 29), (11, 29), (3, 21), (3, 11)]
    facet(img, pts, gold, 1.2, 0.6)
    d = ImageDraw.Draw(img)
    # 中の渦
    ring = shade(gold, 0.35)
    d.arc([9, 9, 23, 23], 20, 300, fill=ring)
    d.arc([12, 12, 20, 20], 200, 480, fill=ring)
    d.point((16, 16), fill=(255, 244, 200, 255))
    d.line([(8, 9), (14, 6)], fill=(255, 240, 190, 255))
    return img


def rune():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    slate = (84, 88, 110)
    pts = [(8, 3), (24, 3), (27, 7), (27, 26), (23, 29), (9, 29), (5, 26), (5, 7)]
    facet(img, pts, slate, 1.2, 0.68)
    d = ImageDraw.Draw(img)
    glow = (196, 156, 255, 255)
    deep = (126, 92, 214, 255)
    # 刻み: 縦の線と枝 (技の印)
    d.line([(16, 8), (16, 24)], fill=deep)
    d.line([(15, 8), (15, 24)], fill=glow)
    d.line([(15, 13), (21, 9)], fill=glow)
    d.line([(15, 19), (10, 15)], fill=glow)
    d.point((21, 9), fill=(255, 255, 255, 255))
    d.point((10, 15), fill=(255, 255, 255, 255))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for kind, base in ELEMENTS.items():
        shard(kind, base).save(OUT / f"socket_shard_{kind}.png")
    core().save(OUT / "aspect_core.png")
    rune().save(OUT / "skill_rune.png")
    print("wrote 7 textures to", OUT)


if __name__ == "__main__":
    main()
