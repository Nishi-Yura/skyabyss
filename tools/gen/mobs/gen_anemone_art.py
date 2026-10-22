"""光る海のイソギンチャク (持ち運ぶ灯) の絵と、灯の台のブロックの絵を作る (docs/design/23 の案 B)。

使い方: python -I tools/gen/mobs/gen_anemone_art.py
出力:
  docs/review/anemone/item_{a,b,c}.png     灯の品のアイコン 3 案 (32x32)
  docs/review/anemone/pedestal_{a,b,c}.png 灯の台の表面 3 案 (32x32)
  docs/review/anemone/sheet.png            3 案ずつ並べた確認用の絵
  src/main/resources/assets/skyabyss/textures/ に既定 (案 A) を入れる。選ばれたら次のように入れ直す
    --install a|b|c         品と台を同じ案で入れる
    --item a|b|c --pedestal a|b|c   別々に入れる
"""
import argparse
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/review/anemone"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures"

# 岩鯨の石の色 (深層岩の青灰) と、灯の青緑
ROCK_D = (32, 53, 64, 255)
ROCK_M = (44, 74, 85, 255)
ROCK_H = (92, 118, 128, 255)
BONE = (226, 216, 192, 255)
BONE_D = (160, 150, 128, 255)
RUST = (138, 92, 52, 255)
GLOW_C = (214, 255, 246, 255)
GLOW_M = (127, 214, 208, 255)
GLOW_D = (46, 140, 150, 255)
GLOW_DD = (24, 78, 96, 255)
INK = (14, 30, 40, 255)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def line(img, x0, y0, x1, y1, colors):
    """先へ向けて細る線。colors = 根もと → 先の順の色"""
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        t = i / n
        x = round(x0 + (x1 - x0) * t)
        y = round(y0 + (y1 - y0) * t)
        px(img, x, y, colors[min(int(t * len(colors)), len(colors) - 1)])
        if t < 0.45:
            px(img, x + 1, y, colors[min(int(t * len(colors)) + 1, len(colors) - 1)])


def halo(img, cx, cy, r, col=GLOW_M, alpha=70):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.height):
        for x in range(img.width):
            d = math.hypot(x - cx, (y - cy) * 1.1)
            if d < r:
                a = int(alpha * (1 - d / r) ** 1.5)
                layer.putpixel((x, y), (col[0], col[1], col[2], a))
    img.alpha_composite(layer)


def tuft(img, cx, base_y, n, length, spread, curve=0.0):
    """根もとから放射状に伸びる触手"""
    for i in range(n):
        a = -math.pi / 2 + (i - (n - 1) / 2) * spread / max(n - 1, 1)
        ln = length * (0.78 + 0.22 * math.cos((i - (n - 1) / 2) * 0.9))
        x1 = cx + math.cos(a) * ln + curve * (i - (n - 1) / 2)
        y1 = base_y + math.sin(a) * ln
        line(img, cx, base_y, round(x1), round(y1), [GLOW_DD, GLOW_D, GLOW_M, GLOW_C])
        px(img, round(x1), round(y1), GLOW_C)


def item_a():
    """A: 石の根もとから触手が広がる、素のイソギンチャク"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    halo(im, 16, 13, 15)
    d = ImageDraw.Draw(im)
    d.ellipse((9, 22, 23, 29), fill=ROCK_M, outline=INK)
    d.ellipse((11, 23, 20, 26), fill=ROCK_H)
    d.rectangle((13, 17, 18, 24), fill=GLOW_D, outline=INK)
    d.rectangle((14, 18, 16, 23), fill=GLOW_M)
    tuft(im, 15, 18, 9, 13, 2.6, 0.0)
    tuft(im, 16, 18, 5, 7, 2.0, 0.4)
    return im


def item_b():
    """B: 小さな石の栓をした瓶に入れたイソギンチャク (持ち歩く提灯)"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    halo(im, 16, 17, 15, alpha=60)
    d = ImageDraw.Draw(im)
    d.rectangle((13, 4, 18, 7), fill=BONE, outline=INK)
    d.rectangle((12, 8, 19, 9), fill=RUST, outline=INK)
    d.rounded_rectangle((8, 10, 23, 28), radius=3, fill=(40, 90, 104, 255), outline=INK)
    d.rounded_rectangle((9, 11, 22, 27), radius=2, fill=GLOW_DD)
    tuft(im, 15, 26, 7, 12, 2.4)
    d.rectangle((9, 25, 22, 28), fill=ROCK_M)
    d.line((9, 24, 22, 24), fill=ROCK_H)
    d.line((10, 12, 10, 20), fill=(180, 235, 235, 255))
    d.rounded_rectangle((8, 10, 23, 28), radius=3, outline=INK)
    return im


def item_c():
    """C: 吊り下げた鐘のかたちの、くらげ風のイソギンチャク"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    halo(im, 16, 14, 14, alpha=70)
    d = ImageDraw.Draw(im)
    d.line((16, 1, 16, 6), fill=BONE_D)
    d.ellipse((13, 0, 18, 3), outline=BONE_D)
    d.pieslice((7, 6, 25, 24), 180, 360, fill=GLOW_M, outline=INK)
    d.pieslice((10, 8, 22, 20), 180, 360, fill=GLOW_C)
    d.line((8, 15, 24, 15), fill=GLOW_D)
    for i, x in enumerate(range(9, 24, 2)):
        ln = 6 + (i % 3) * 2
        for k in range(ln):
            px(im, x + (1 if k % 4 > 1 else 0), 16 + k, GLOW_M if k < ln - 2 else GLOW_C)
    for i in range(3):
        d.point((13 + i * 3, 11), fill=GLOW_D)
    return im


def stone_tile(rng, base, hi, lo, cracks=6):
    im = Image.new("RGBA", (32, 32), base)
    for _ in range(140):
        x, y = rng.randrange(32), rng.randrange(32)
        px(im, x, y, rng.choice([hi, lo, base, lo]))
    for _ in range(cracks):
        x, y = rng.randrange(32), rng.randrange(32)
        for _ in range(rng.randrange(4, 9)):
            px(im, x % 32, y % 32, lo)
            x += rng.choice([-1, 0, 1])
            y += 1
    return im


def pedestal_a():
    """A: 深層岩の石柱。縦に青緑の溝が一本走る"""
    rng = random.Random(11)
    im = stone_tile(rng, ROCK_M, ROCK_H, ROCK_D)
    d = ImageDraw.Draw(im)
    d.rectangle((14, 0, 17, 31), fill=GLOW_DD)
    d.rectangle((15, 0, 16, 31), fill=GLOW_D)
    for y in range(2, 32, 8):
        d.rectangle((13, y, 18, y + 1), fill=ROCK_D)
        d.point((15, y), fill=GLOW_M)
    d.rectangle((0, 0, 31, 1), fill=ROCK_D)
    d.rectangle((0, 30, 31, 31), fill=ROCK_D)
    return im


def pedestal_b():
    """B: 鯨骨の杯。骨の白に石の縁、錆の帯"""
    rng = random.Random(22)
    im = stone_tile(rng, BONE, (240, 232, 212, 255), BONE_D, cracks=4)
    d = ImageDraw.Draw(im)
    for y in (0, 1, 30, 31):
        d.rectangle((0, y, 31, y), fill=ROCK_M)
    d.rectangle((0, 12, 31, 14), fill=RUST)
    d.rectangle((0, 13, 31, 13), fill=(176, 120, 70, 255))
    for x in range(2, 32, 8):
        d.rectangle((x, 20, x + 2, 26), fill=BONE_D)
        d.point((x + 1, 21), fill=ROCK_D)
    return im


def pedestal_c():
    """C: 貝と珊瑚の籠。紫がかった青の石に、さざ波の筋"""
    rng = random.Random(33)
    im = stone_tile(rng, (52, 66, 92, 255), (96, 112, 150, 255), (30, 40, 62, 255), cracks=3)
    d = ImageDraw.Draw(im)
    for y in range(3, 30, 5):
        for x in range(32):
            yy = y + round(math.sin(x / 3.0 + y) * 1.2)
            px(im, x, yy, (120, 150, 190, 255))
            px(im, x, yy + 1, (30, 40, 62, 255))
    for x, y in ((5, 8), (20, 18), (11, 25), (26, 5)):
        d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=GLOW_D, outline=ROCK_D)
        d.point((x, y), fill=GLOW_C)
    return im


def bloom():
    """台の上でともる灯 (ブロックの上の十字の板に貼る)"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((13, 22, 18, 31), fill=GLOW_D, outline=INK)
    d.rectangle((14, 23, 16, 30), fill=GLOW_M)
    tuft(im, 15, 23, 11, 20, 2.8)
    tuft(im, 16, 23, 5, 10, 2.0, 0.5)
    return im


def bed():
    """灯の苗床: 砂と石にイソギンチャクが生える (灯の受け取り場所)"""
    rng = random.Random(44)
    im = stone_tile(rng, (178, 164, 128, 255), (205, 192, 156, 255), (122, 108, 82, 255), cracks=2)
    for x, y in ((6, 8), (22, 6), (13, 20), (25, 24), (4, 26)):
        layer = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        tuft(layer, x, y + 3, 5, 5, 2.2)
        im.alpha_composite(layer)
    return im


def tally():
    """入口の数え石: 深層岩の石に、三つの丸い窪み (灯の台と同じ数。丸は横一列、y 13〜17、中心は x 8・16・24)"""
    rng = random.Random(55)
    im = stone_tile(rng, ROCK_M, ROCK_H, ROCK_D, cracks=4)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 31, 31), outline=ROCK_D)
    for i in range(3):
        cx = 7 + i * 8
        d.ellipse((cx - 1, 13, cx + 3, 17), fill=INK, outline=ROCK_D)
    return im


def tally_dot():
    """数え石の点灯した丸 (丸ひとつぶん。32x32 の中の 14〜18 に描く)"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((14, 14, 18, 18), fill=GLOW_M, outline=GLOW_DD)
    d.point((16, 16), fill=GLOW_C)
    return im


def mock_pedestal(tile, lit):
    """確認用の前から見た図 (台 + 杯 + 灯)"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    if lit:
        halo(im, 16, 12, 15, alpha=90)

    def blit(x0, y0, x1, y1, sx, sy):
        for y in range(y1 - y0):
            for x in range(x1 - x0):
                im.putpixel((x0 + x, y0 + y), tile.getpixel(((sx + x) % 32, (sy + y) % 32)))
    blit(10, 26, 22, 29, 2, 2)
    blit(13, 14, 19, 26, 8, 4)
    blit(9, 11, 23, 15, 2, 20)
    if lit:
        b = bloom().resize((16, 16), Image.NEAREST)
        im.alpha_composite(b, (8, -2))
    return im


def sheet(items, peds):
    S = 8
    cw, ch = 32 * S + 24, 32 * S + 40
    W = cw * 3 + 24
    H = 40 + ch * 3 + 20
    sh = Image.new("RGB", (W, H), (22, 28, 34))
    bg = Image.new("RGBA", (32 * S, 32 * S), (20, 40, 56, 255))
    d = ImageDraw.Draw(sh)
    for col, k in enumerate("abc"):
        x0 = 12 + col * cw
        d.text((x0, 10), f"option {k.upper()}", fill=(230, 235, 240))
        tile = bg.copy()
        tile.alpha_composite(items[k].resize((32 * S, 32 * S), Image.NEAREST))
        sh.paste(tile.convert("RGB"), (x0, 40))
        d.text((x0, 40 + 32 * S + 6), "item icon", fill=(150, 165, 180))
        t2 = bg.copy()
        t2.alpha_composite(peds[k].resize((32 * S, 32 * S), Image.NEAREST))
        sh.paste(t2.convert("RGB"), (x0, 40 + ch))
        d.text((x0, 40 + ch + 32 * S + 6), "pedestal surface", fill=(150, 165, 180))
        for j, lit in enumerate((False, True)):
            mm = Image.new("RGBA", (16 * S, 32 * S), (20, 40, 56, 255))
            fig = mock_pedestal(peds[k], lit).resize((32 * S, 32 * S), Image.NEAREST).crop((8 * S, 0, 24 * S, 32 * S))
            mm.alpha_composite(fig)
            sh.paste(mm.convert("RGB"), (x0 + j * (16 * S), 40 + ch * 2))
        d.text((x0, 40 + ch * 2 + 32 * S + 6), "unlit / lit (mock)", fill=(150, 165, 180))
    return sh


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", choices="abc")
    ap.add_argument("--item", choices="abc")
    ap.add_argument("--pedestal", choices="abc")
    args = ap.parse_args()
    items = {"a": item_a(), "b": item_b(), "c": item_c()}
    peds = {"a": pedestal_a(), "b": pedestal_b(), "c": pedestal_c()}
    for k in "abc":
        save(items[k], OUT / f"item_{k}.png")
        save(peds[k], OUT / f"pedestal_{k}.png")
    save(sheet(items, peds), OUT / "sheet.png")
    item_k = args.item or args.install or "a"
    ped_k = args.pedestal or args.install or "a"
    save(items[item_k], TEX / "item/glow_anemone.png")
    save(peds[ped_k], TEX / "block/anemone_pedestal.png")
    save(bloom(), TEX / "block/anemone_bloom.png")
    save(bed(), TEX / "block/anemone_bed.png")
    save(tally(), TEX / "block/anemone_tally.png")
    save(tally_dot(), TEX / "block/anemone_tally_dot.png")
    print("installed item", item_k, "pedestal", ped_k)


if __name__ == "__main__":
    main()
