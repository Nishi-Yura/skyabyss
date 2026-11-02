"""装備の自由度の品の絵を作る (巨神の粉 5 種、アスペクトの芯、技の修飾ルーン)。32x32、暗い縁取りと 3 段の面。

使い方: python tools/gen/items/gen_gear_freedom_assets.py
出力先: src/main/resources/assets/skyabyss/textures/item/{socket_shard_<属性>,aspect_core,skill_rune}.png
粉 (前の欠片) は小さな粉の盛りに光る粒 (属性の色)。
芯は金の八角に渦、ルーンは暗い板に紫の刻み。何度実行しても同じ物ができる。
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/textures/item"
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
    """巨神の粉 (docs/design/41)。2026-10-29: 5 枚が同じ山の色替えだったので、土地ごとに別の形に (一品一案)。
    stone = 砕けた石の薄片と粉、wind = 風に流れる羽毛のような粉、tide = 濡れた砂に残る真珠の粒、
    ash = 割れ目が熾る炭、bone = 折れた骨の欠片。どれも左上が明るく右下が暗い。"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi, lo, glow = shade(base, 1.35), shade(base, 0.7), shade(base, 1.7)
    if kind == "stone":
        # 斜めに割れた薄い石の片 (二枚) と、下にこぼれた粉
        facet(img, [(6, 8), (19, 5), (23, 12), (12, 17)], base, 1.3, 0.65)
        facet(img, [(13, 17), (24, 13), (27, 20), (18, 25)], base, 1.25, 0.6)
        d.line([(9, 11), (18, 8)], fill=glow)
        for x, y in ((6, 26), (9, 27), (13, 28), (22, 27), (26, 25), (5, 23)):
            d.point((x, y), fill=lo)
        d.point((16, 10), fill=(255, 255, 255, 255))
    elif kind == "wind":
        # 右上へ吹き上がる三筋の粉 (羽毛のような弧)
        for k, (x0, y0) in enumerate(((5, 24), (8, 27), (13, 28))):
            pts = [(x0 + t * 1.6, y0 - t * t * 0.42 - t * 0.6) for t in range(0, 12)]
            for n, (x, y) in enumerate(pts):
                c = glow if n > 8 else hi if n > 4 else base + (255,)
                d.point((round(x), round(y)), fill=c)
                d.point((round(x) + 1, round(y)), fill=OUTLINE if n < 9 else c)
        for x, y in ((24, 6), (27, 9), (22, 4), (26, 13), (20, 9)):
            d.point((x, y), fill=glow)
        d.ellipse([3, 27, 15, 30], fill=OUTLINE)
        d.ellipse([4, 28, 14, 29], fill=lo)
    elif kind == "tide":
        # 濡れた砂の盛りに、真珠の粒が三つ (丸く光る)
        d.polygon([(2, 27), (8, 20), (16, 18), (24, 20), (30, 27)], fill=OUTLINE)
        d.polygon([(4, 26), (9, 21), (16, 19), (23, 21), (28, 26)], fill=lo)
        d.polygon([(4, 26), (9, 21), (16, 19), (12, 26)], fill=base + (255,))
        d.line([(3, 28), (29, 28)], fill=shade(base, 0.45))
        for cx, cy, r in ((10, 15, 3), (19, 12, 4), (24, 18, 2)):
            d.ellipse([cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1], fill=OUTLINE)
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hi)
            d.ellipse([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], fill=glow)
            d.point((cx - 1, cy - 1), fill=(255, 255, 255, 255))
            d.ellipse([cx, cy, cx + r, cy + r], fill=hi) if r > 2 else None
    elif kind == "ash":
        # 黒い炭の塊の割れ目が熾る
        facet(img, [(5, 12), (14, 6), (26, 9), (28, 20), (18, 27), (7, 24)], shade((60, 44, 40), 1.0), 1.25, 0.6)
        for a, b in (((10, 19), (17, 11)), ((17, 11), (23, 15)), ((14, 22), (20, 19)), ((17, 11), (16, 8))):
            d.line([a, b], fill=OUTLINE)
        for a, b in (((11, 19), (17, 12)), ((17, 12), (22, 15)), ((15, 22), (20, 20))):
            d.line([a, b], fill=glow)
        d.point((17, 12), fill=(255, 240, 200, 255))
        d.point((19, 14), fill=(255, 240, 200, 255))
        for x, y in ((8, 28), (12, 29), (24, 27), (27, 24)):
            d.point((x, y), fill=lo)
    else:
        # 折れた骨: 太い骨の片 (両端の瘤) と、細い破片
        d.line([(6, 24), (22, 8)], fill=OUTLINE, width=7)
        d.line([(7, 23), (21, 9)], fill=base + (255,), width=5)
        d.line([(8, 21), (20, 9)], fill=hi, width=1)
        d.line([(10, 24), (22, 12)], fill=lo, width=1)
        for cx, cy in ((7, 23), (21, 9)):
            d.ellipse([cx - 4, cy - 3, cx + 3, cy + 4], fill=OUTLINE)
            d.ellipse([cx - 3, cy - 2, cx + 2, cy + 3], fill=base + (255,))
            d.point((cx - 2, cy - 1), fill=hi)
        # 折れ口 (ぎざぎざ) と破片
        d.polygon([(22, 8), (25, 5), (24, 9), (27, 8), (24, 12)], fill=OUTLINE)
        d.polygon([(23, 8), (25, 6), (24, 9), (25, 9), (23, 11)], fill=hi)
        d.polygon([(24, 22), (29, 18), (29, 24), (25, 27)], fill=OUTLINE)
        d.polygon([(25, 22), (28, 19), (28, 23), (26, 26)], fill=base + (255,))
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
    # 芯とルーンは、あとで描き直した絵が採用されている (上書きしない。SKYABYSS_ALLOW_REGEN=1 のときだけ書く)
    import os
    if os.environ.get("SKYABYSS_ALLOW_REGEN") == "1":
        core().save(OUT / "aspect_core.png")
        rune().save(OUT / "skill_rune.png")
    print("wrote 7 textures to", OUT)


if __name__ == "__main__":
    main()
