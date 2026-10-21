"""旅の地図 (M キー。docs/design/33 の 4 章) の素材を作る。

使い方: python tools/gen_travel_map_assets.py

出力 (src/main/resources/assets/skyabyss/textures/gui/map/ 以下。どれも 2 倍密度):
    - marks.png  512x64   印 32x32 (画面では 16x16)。0 行目 = 印の体、32 行目 = 縁取りの型 (白。画面で巨神の色に染める)
                          並び (MapSprites.java と同じ): 町・野営地・足跡・骨・碑・章の場所・巨神・墓・仲間・目的・噂・自分・はじまりの地・祠・光の輪・積み石
    - paper.png  384x128  地の模様 128x128 を 3 つ (A 石板の粒・B 羊皮紙・C 墨の紙)。画面では繰り返して貼る
    - disc_a.png 512x512  A の丸い海図 (海図の画面 chart.png と同じ雲の海のまだら。縁の外は透明)

色は共鳴の炉の画面 (gen_forge_assets.py) の石と骨、碑文の青緑は伝承の書と同じ。何度実行しても同じ物ができる (乱数の種を固定)。
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_forge_assets import (OUTLINE, SLATE, SLATE_DARK, BONE_LIGHT, BONE, BONE_MID, BONE_DARK,  # noqa: E402
                              TEAL, TEAL_DIM, PALE, GOLD)

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "src/main/resources/assets/skyabyss/textures/gui/map"

STONE_HI = (150, 156, 166, 255)
STONE = (104, 110, 122, 255)
STONE_LO = (66, 70, 82, 255)
EMBER = (255, 154, 60, 255)
EMBER_HI = (255, 214, 140, 255)
WHITE = (255, 255, 255, 255)


def outline(img):
    """不透明な画素のまわりに、暗い縁を付ける"""
    a = np.array(img)
    alpha = a[..., 3] > 0
    grown = alpha.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown |= np.roll(np.roll(alpha, dx, axis=1), dy, axis=0)
    a[grown & ~alpha] = OUTLINE
    return Image.fromarray(a, "RGBA")


def rim(img):
    """縁取りの型: 暗い縁のさらに外側 2 画素 (画面では 1 GUI 画素) を白で"""
    a = np.array(img)
    alpha = a[..., 3] > 0
    grown = alpha.copy()
    for _ in range(2):
        g = grown.copy()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g |= np.roll(np.roll(grown, dx, axis=1), dy, axis=0)
        grown = g
    out = np.zeros_like(a)
    out[grown & ~alpha] = WHITE
    return Image.fromarray(out, "RGBA")


def canvas():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


# ---------------------------------------------------------------- 印 (32x32)

def town():
    """町: 鐘楼 (骨の塔に尖った屋根、青緑の鐘)"""
    img, d = canvas()
    d.rectangle([11, 12, 20, 26], fill=BONE)
    d.rectangle([11, 12, 12, 26], fill=BONE_LIGHT)
    d.rectangle([19, 12, 20, 26], fill=BONE_MID)
    d.polygon([(9, 12), (16, 4), (22, 12)], fill=BONE_DARK)
    d.polygon([(10, 12), (16, 5), (16, 12)], fill=BONE_MID)
    d.rectangle([14, 14, 17, 19], fill=OUTLINE)
    d.polygon([(14, 19), (15, 15), (16, 15), (17, 19)], fill=TEAL)
    d.point((15, 16), fill=PALE)
    d.rectangle([14, 22, 17, 26], fill=SLATE_DARK)
    d.rectangle([8, 26, 23, 27], fill=BONE_DARK)
    return outline(img)


def camp():
    """野営地: 帆布の天幕と焚き火"""
    img, d = canvas()
    d.polygon([(6, 25), (14, 9), (22, 25)], fill=BONE)
    d.polygon([(14, 9), (22, 25), (16, 25)], fill=BONE_MID)
    d.polygon([(12, 25), (14, 18), (16, 25)], fill=SLATE_DARK)
    d.line([14, 9, 14, 6], fill=BONE_DARK)
    d.ellipse([20, 21, 27, 26], fill=STONE_LO)
    d.polygon([(21, 23), (23, 17), (24, 20), (26, 18), (26, 23)], fill=EMBER)
    d.polygon([(23, 23), (24, 20), (25, 23)], fill=EMBER_HI)
    return outline(img)


def footprint():
    """巨大な足跡: 足の裏の形 (くぼみ) と三本の指"""
    img, d = canvas()
    d.ellipse([9, 11, 22, 27], fill=STONE_LO)
    d.ellipse([11, 13, 20, 25], fill=(48, 52, 62, 255))
    for (x, y) in ((8, 5), (14, 3), (20, 5)):
        d.ellipse([x, y, x + 5, y + 6], fill=STONE_LO)
        d.point((x + 2, y + 2), fill=STONE)
    d.arc([11, 13, 20, 25], 200, 300, fill=STONE)
    return outline(img)


def ribs():
    """巨神の骨: 地面から反り立つ肋 3 本"""
    img, d = canvas()
    for i, x in enumerate((7, 13, 19)):
        h = (14, 20, 16)[i]
        d.arc([x - 2, 27 - h, x + 8, 27 + h], 180, 270, fill=BONE, width=3)
        d.arc([x - 2, 27 - h, x + 8, 27 + h], 190, 250, fill=BONE_LIGHT, width=1)
    d.rectangle([5, 26, 27, 27], fill=BONE_DARK)
    return outline(img)


def waymark():
    """碑: 立った石に青緑の碑文"""
    img, d = canvas()
    d.polygon([(11, 27), (10, 9), (16, 4), (22, 9), (21, 27)], fill=STONE)
    d.polygon([(11, 27), (10, 9), (16, 4), (16, 27)], fill=STONE_HI)
    d.polygon([(18, 27), (21, 9), (21, 27)], fill=STONE_LO)
    d.line([16, 9, 16, 22], fill=TEAL)
    d.line([13, 13, 19, 13], fill=TEAL)
    d.line([14, 18, 18, 18], fill=TEAL_DIM)
    d.point((16, 9), fill=PALE)
    d.rectangle([8, 26, 24, 27], fill=STONE_LO)
    return outline(img)


def site():
    """章の場所: 小さな積み石の廃墟と、青緑の灯"""
    img, d = canvas()
    d.rectangle([7, 21, 25, 26], fill=STONE_LO)
    d.rectangle([9, 15, 18, 21], fill=STONE)
    d.rectangle([9, 15, 11, 21], fill=STONE_HI)
    d.rectangle([19, 18, 24, 21], fill=STONE)
    d.rectangle([12, 10, 16, 15], fill=STONE_HI)
    d.line([7, 21, 25, 21], fill=OUTLINE)
    d.line([18, 15, 18, 21], fill=OUTLINE)
    d.point((22, 16), fill=TEAL)
    d.point((22, 15), fill=PALE)
    return outline(img)


def titan():
    """巨神: 大きなひし形に一つ目 (縁取りの色が巨神ごとの色)"""
    img, d = canvas()
    d.polygon([(16, 3), (28, 16), (16, 29), (4, 16)], fill=(30, 40, 48, 255))
    d.polygon([(16, 5), (26, 16), (16, 16), (6, 16)], fill=(44, 60, 70, 255))
    d.ellipse([10, 12, 22, 20], fill=BONE_LIGHT)
    d.ellipse([13, 13, 19, 19], fill=TEAL)
    d.ellipse([15, 14, 17, 18], fill=OUTLINE)
    d.point((14, 14), fill=PALE)
    return outline(img)


def grave():
    """墓: 丸い頭の墓石と骨の十字"""
    img, d = canvas()
    d.rounded_rectangle([10, 8, 22, 27], radius=6, fill=STONE)
    d.rectangle([10, 14, 12, 27], fill=STONE_HI)
    d.rectangle([20, 14, 22, 27], fill=STONE_LO)
    d.rectangle([15, 11, 17, 21], fill=BONE)
    d.rectangle([13, 14, 19, 15], fill=BONE)
    d.rectangle([7, 26, 25, 27], fill=STONE_LO)
    return outline(img)


def party():
    """仲間: 丸い札 (縁取りの色がパーティの色)"""
    img, d = canvas()
    d.ellipse([8, 8, 24, 24], fill=(36, 42, 54, 255))
    d.ellipse([10, 10, 22, 22], fill=(56, 66, 82, 255))
    d.ellipse([13, 11, 19, 17], fill=BONE_LIGHT)
    d.pieslice([10, 16, 22, 28], 180, 360, fill=BONE)
    d.ellipse([8, 8, 24, 24], outline=OUTLINE)
    return img


def objective():
    """いまの目的: 金のひし形 (目的の標と同じ)"""
    img, d = canvas()
    d.polygon([(16, 5), (27, 16), (16, 27), (5, 16)], fill=GOLD)
    d.polygon([(16, 8), (24, 16), (16, 16), (8, 16)], fill=(255, 226, 140, 255))
    d.polygon([(16, 11), (21, 16), (16, 21), (11, 16)], fill=(200, 140, 40, 255))
    d.point((15, 9), fill=WHITE)
    return outline(img)


def rumor():
    """噂: 霞んだ渦 (場所はおおよそ。方角だけ)"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    a = np.zeros((32, 32, 4), dtype=np.uint8)
    rng = np.random.default_rng(33)
    ys, xs = np.mgrid[0:32, 0:32]
    r = np.hypot(xs + 0.5 - 16, ys + 0.5 - 16)
    alpha = np.clip(1.2 - r / 12.0, 0, 1) * 150 + rng.normal(0, 18, (32, 32))
    alpha = np.where(r < 13, np.clip(alpha, 0, 170), 0)
    a[..., 0], a[..., 1], a[..., 2] = 170, 210, 214
    a[..., 3] = alpha.astype(np.uint8)
    img = Image.fromarray(a, "RGBA")
    d = ImageDraw.Draw(img)
    for k in range(10):
        ang = k / 10 * 2 * math.pi
        d.point((int(16 + math.cos(ang) * 10), int(16 + math.sin(ang) * 10)), fill=TEAL)
    # 「?」のかわりに、細い点の十字 (文字を使わない)
    for t in (-4, -2, 2, 4):
        d.point((16 + t, 16), fill=PALE)
        d.point((16, 16 + t), fill=PALE)
    d.point((16, 16), fill=WHITE)
    return img


def self_arrow():
    """自分: 上 (北) を向いた矢じり。画面で向きに回す"""
    img, d = canvas()
    d.polygon([(16, 3), (26, 27), (16, 21), (6, 27)], fill=BONE_LIGHT)
    d.polygon([(16, 3), (16, 21), (6, 27)], fill=BONE)
    d.polygon([(16, 9), (20, 20), (16, 17), (12, 20)], fill=TEAL)
    d.point((16, 10), fill=PALE)
    return outline(img)


def spawn():
    """はじまりの地: 骨の色の小さな星 (海図と同じ)"""
    img, d = canvas()
    d.polygon([(16, 5), (19, 13), (27, 16), (19, 19), (16, 27), (13, 19), (5, 16), (13, 13)], fill=BONE_LIGHT)
    d.polygon([(16, 5), (19, 13), (16, 16), (13, 13)], fill=WHITE)
    d.polygon([(16, 27), (13, 19), (16, 16), (19, 19)], fill=BONE_MID)
    return outline(img)


def shrine():
    """祠: 二本の柱に横木 (心臓を止めたあとに出る。32 の祠の置き場が決まったら使う)"""
    img, d = canvas()
    d.rectangle([9, 12, 11, 27], fill=BONE)
    d.rectangle([21, 12, 23, 27], fill=BONE_MID)
    d.rectangle([6, 8, 26, 10], fill=BONE_LIGHT)
    d.rectangle([8, 13, 24, 14], fill=BONE_DARK)
    d.polygon([(16, 17), (19, 21), (16, 25), (13, 21)], fill=TEAL)
    d.point((16, 19), fill=PALE)
    return outline(img)


def glow():
    """光の輪: 子に乗っているとき、行ける印の下に敷く (画面で淡い青に染めて明滅)"""
    a = np.zeros((32, 32, 4), dtype=np.uint8)
    ys, xs = np.mgrid[0:32, 0:32]
    r = np.hypot(xs + 0.5 - 16, ys + 0.5 - 16)
    alpha = np.clip(1 - np.abs(r - 11) / 4.5, 0, 1) * 220
    a[..., :3] = 255
    a[..., 3] = alpha.astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def cairn():
    """積み石 (道しるべの線の点。小さい)"""
    img, d = canvas()
    d.ellipse([12, 18, 20, 22], fill=STONE_LO)
    d.ellipse([13, 14, 19, 18], fill=STONE)
    d.ellipse([14, 11, 18, 14], fill=STONE_HI)
    return outline(img)


SPRITES = [town, camp, footprint, ribs, waymark, site, titan, grave, party, objective, rumor, self_arrow, spawn, shrine, glow, cairn]
# 縁取りを付けない印 (光の輪・噂・積み石は縁なし)
NO_RIM = {"glow", "rumor", "cairn"}


def marks():
    img = Image.new("RGBA", (512, 64), (0, 0, 0, 0))
    for i, fn in enumerate(SPRITES):
        s = fn()
        img.alpha_composite(s, (i * 32, 0))
        if fn.__name__ not in NO_RIM:
            img.alpha_composite(rim(s), (i * 32, 32))
    return img


# ---------------------------------------------------------------- 地の模様 (128x128 を 3 つ。繰り返して貼る)

def tileable_noise(rng, n, blobs, rmin, rmax):
    """端がつながるまだら (128 の輪の上で距離を測る)"""
    ys, xs = np.mgrid[0:n, 0:n]
    field = np.zeros((n, n))
    for _ in range(blobs):
        bx, by, br = rng.uniform(0, n), rng.uniform(0, n), rng.uniform(rmin, rmax)
        dx = np.minimum(np.abs(xs - bx), n - np.abs(xs - bx))
        dy = np.minimum(np.abs(ys - by), n - np.abs(ys - by))
        field += np.exp(-(dx * dx + dy * dy) / (2 * br * br)) * rng.uniform(0.4, 1.0)
    field -= field.min()
    return field / max(1e-6, field.max())


def paper():
    n = 128
    img = Image.new("RGBA", (n * 3, n), (0, 0, 0, 0))
    rng = np.random.default_rng(20261021)
    # A: 石板の粒 (炉の画面と同じ色に、ごく弱いまだら)
    blob = tileable_noise(rng, n, 30, 6, 18)
    base = np.array(SLATE[:3], dtype=float)[None, None, :] * (1 - blob[..., None] * 0.18) \
        + np.array(SLATE_DARK[:3], dtype=float)[None, None, :] * (blob[..., None] * 0.18)
    rgb = np.clip(base + rng.normal(0, 2.0, (n, n, 1)), 0, 255)
    img.paste(Image.fromarray(np.dstack([rgb.astype(np.uint8), np.full((n, n), 255, np.uint8)]), "RGBA"), (0, 0))
    # B: 羊皮紙 (黄ばんだ地に、染みと繊維)
    blob = tileable_noise(rng, n, 40, 5, 22)
    fib = tileable_noise(rng, n, 120, 1, 2.5)
    light = np.array([214, 196, 156], dtype=float)
    dark = np.array([176, 152, 108], dtype=float)
    rgb = light[None, None, :] * (1 - blob[..., None] * 0.55) + dark[None, None, :] * (blob[..., None] * 0.55)
    rgb -= fib[..., None] * 10
    rgb = np.clip(rgb + rng.normal(0, 3.0, (n, n, 1)), 0, 255)
    img.paste(Image.fromarray(np.dstack([rgb.astype(np.uint8), np.full((n, n), 255, np.uint8)]), "RGBA"), (n, 0))
    # C: 墨の紙 (ほぼ黒の青に、薄い墨のにじみ。画面では半分透けさせる)
    blob = tileable_noise(rng, n, 24, 8, 26)
    deep = np.array([10, 14, 20], dtype=float)
    wash = np.array([22, 40, 48], dtype=float)
    rgb = deep[None, None, :] * (1 - blob[..., None] * 0.6) + wash[None, None, :] * (blob[..., None] * 0.6)
    rgb = np.clip(rgb + rng.normal(0, 1.5, (n, n, 1)), 0, 255)
    img.paste(Image.fromarray(np.dstack([rgb.astype(np.uint8), np.full((n, n), 255, np.uint8)]), "RGBA"), (n * 2, 0))
    return img


def disc_a():
    """A の丸い海図 (海図の画面 chart.png の雲の海と同じ色)。半径 250 画素、縁は描かない (画面で骨の輪を描く)"""
    n, R = 512, 250
    rng = np.random.default_rng(20261018)
    ys, xs = np.mgrid[0:n, 0:n]
    c = n / 2
    dist = np.hypot(xs + 0.5 - c, ys + 0.5 - c)
    t = np.clip(dist / R, 0, 1)
    blob = np.zeros((n, n))
    for _ in range(90):
        bx, by, br = rng.uniform(c - R, c + R), rng.uniform(c - R, c + R), rng.uniform(18, 54)
        blob += np.exp(-((xs - bx) ** 2 + (ys - by) ** 2) / (2 * br * br))
    blob = np.clip(blob * 0.45 - 0.15, 0, 1)
    base = np.array([18, 44, 56]) * (1 - t[..., None] * 0.5) + np.array([10, 22, 30]) * t[..., None] * 0.5
    col = base + blob[..., None] * np.array([20, 34, 36]) + rng.normal(0, 1.5, (n, n))[..., None]
    a = np.zeros((n, n, 4), dtype=np.uint8)
    a[..., :3] = np.clip(col, 0, 255).astype(np.uint8)
    a[..., 3] = np.where(dist <= R, 255, 0)
    return Image.fromarray(a, "RGBA")


def preview(m):
    """確かめ用: 印を 4 倍で並べた絵 (docs/review/map_roughs/marks_preview.png)"""
    out = Image.new("RGBA", (16 * 36 * 2, 2 * 36 * 2), (28, 32, 41, 255))
    for i in range(16):
        body = m.crop((i * 32, 0, i * 32 + 32, 32))
        r = m.crop((i * 32, 32, i * 32 + 32, 64))
        tinted = Image.new("RGBA", (32, 32), (127, 244, 255, 255))
        tinted.putalpha(r.getchannel("A"))
        cell = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        cell.alpha_composite(tinted)
        cell.alpha_composite(body)
        out.alpha_composite(cell.resize((64, 64), Image.NEAREST), (i * 72 + 4, 4))
        out.alpha_composite(body.resize((64, 64), Image.NEAREST), (i * 72 + 4, 76))
    return out


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    m = marks()
    m.save(OUT / "marks.png")
    paper().save(OUT / "paper.png")
    disc_a().save(OUT / "disc_a.png")
    rev = REPO / "docs/review/map_roughs"
    rev.mkdir(parents=True, exist_ok=True)
    preview(m).convert("RGB").save(rev / "marks_preview.png")
    print("wrote", OUT)


if __name__ == "__main__":
    build()
