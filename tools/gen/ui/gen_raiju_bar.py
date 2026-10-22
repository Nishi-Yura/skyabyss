"""雷獣 (中ボス) の 10 段のボスバーの絵 (枠・刻み・顔の印) を描く (Pillow)。段階はない。帯の色は青白 (12 の 5.2節)。

使い方: リポジトリ直下で  python tools/gen/ui/gen_raiju_bar.py [--option a|b|c]   (既定は ADOPTED)
出力: src/main/resources/assets/skyabyss/textures/gui/boss/raiju_frame.png   (182x16。枠は 0〜11 行、帯の溝は x 3〜177、y 3〜8)
      src/main/resources/assets/skyabyss/textures/gui/boss/raiju_notches.png (182x16。帯の上に重ねる 10 段の刻み。ほかは透明)
      src/main/resources/assets/skyabyss/textures/gui/boss/raiju_icon.png    (16x16。狼の頭の横顔、光る瞳)
確認: docs/review/raiju_bar_roughs.png (3 案を 4 倍に拡大し、帯を 100% / 60% / 痺れ (黄) で塗った見本。git に入れる比較の 1 枚)
      docs/review/previews/raiju_bar.png (採用案だけ)

寸法と描き方は RaijuClient (岩鯨・潮殻の心臓のバーと同じ寸法)。帯の色はゲームの側で塗る (RaijuClient.FILL)。
3 案:
- a 稲妻の縁 (採用): 灰青の縁の上下を、白金の稲妻の折れ線が走る。両端に銅の金具 (避雷の柱の銅)
- b 放電の粒: 白い縁に、青白の火花の点が散る。軽いが、潮殻の真珠の白のバーと似る
- c 避雷針の端: 暗い縁、両端に避雷針の形の飾り。形は強いが、帯の縁が暗く、帯との差が小さい
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/boss"
ROUGHS = ROOT / "docs/review/raiju_bar_roughs.png"
PREVIEW = ROOT / "docs/review/previews/raiju_bar.png"
ADOPTED = "a"
W, H = 182, 16
TRACK = (3, 3, 178, 9)
DARK = (12, 14, 22)
GROOVE = (20, 24, 36)
STORM = (74, 82, 104)
STORM_L = (120, 132, 160)
ASH_W = (214, 218, 228)
COPPER = (196, 112, 70)
COPPER_D = (140, 78, 48)
BOLT = (250, 246, 210)
BOLT_B = (170, 210, 255)
FILL = (156, 207, 255)
FILL_TOP = (230, 244, 255)
FILL_STUN = (242, 226, 122)

STYLE = {
    "a": dict(outer=(36, 40, 58), rim=STORM_L),
    "b": dict(outer=(120, 128, 150), rim=ASH_W),
    "c": dict(outer=(18, 20, 30), rim=(54, 60, 80)),
}


def base(opt):
    s = STYLE[opt]
    a = np.zeros((H, W, 4), np.uint8)
    for y in range(12):
        for x in range(W):
            edge = min(y, 11 - y, x, W - 1 - x)
            c = s["outer"] if edge == 0 else s["rim"] if edge == 1 else DARK if edge == 2 else GROOVE
            a[y, x] = (*c, 255)
    return a


def px(a, x, y, c, alpha=255):
    if 0 <= x < W and 0 <= y < H:
        a[y, x] = (*c, alpha)


def frame(opt):
    a = base(opt)
    if opt == "a":
        # 上下の縁を走る稲妻の折れ線 (縁の 0〜2 行と 9〜11 行の中で上下する)
        # 縁の 2 行 (0〜1 と 10〜11) の中で折れる。14 画素ごとに一度、外の縁へ跳ねる (稲妻の角)
        for x in range(6, W - 6):
            k = x % 14
            yt = 0 if k in (4, 5, 6) else 1
            px(a, x, yt, BOLT if k in (4, 5, 6) else (206, 220, 244))
            kb = (x + 7) % 14
            yb = 11 if kb in (4, 5, 6) else 10
            px(a, x, yb, BOLT if kb in (4, 5, 6) else (206, 220, 244))
        # 両端の銅の金具 (4x12) と光る鋲
        for x0 in (0, W - 5):
            for y in range(12):
                for x in range(x0, x0 + 5):
                    edge = min(y, 11 - y, x - x0, x0 + 4 - x)
                    px(a, x, y, COPPER_D if edge == 0 else COPPER)
            px(a, x0 + 2, 5, BOLT_B)
            px(a, x0 + 2, 6, BOLT_B)
        # 溝の左右の端を、金具の色で 1 画素だけ締める
    elif opt == "b":
        rng = np.random.default_rng(7)
        for x in range(4, W - 4):
            if rng.random() < 0.18:
                px(a, x, int(rng.integers(0, 2)), BOLT_B)
            if rng.random() < 0.18:
                px(a, x, 10 + int(rng.integers(0, 2)), BOLT_B)
    else:
        for x0, sgn in ((0, 1), (W - 1, -1)):
            for y in range(12):
                px(a, x0, y, COPPER)
                px(a, x0 + sgn, y, COPPER_D)
            for y in (0, 1, 2):
                px(a, x0 + sgn * 2, y, BOLT)
    return a


def notches():
    """帯の上に重ねる 10 段の刻み (9 本の暗い線と、その右の 1 画素の明るい線)。溝の外は透明"""
    a = np.zeros((H, W, 4), np.uint8)
    x0, y0, x1, y1 = TRACK
    for k in range(1, 10):
        x = x0 + round((x1 - x0) * k / 10)
        for y in range(y0, y1):
            px(a, x, y, (16, 22, 40), 200)
            px(a, x + 1, y, (230, 240, 255), 60)
    return a


ICON = [
    "................",
    "..#..#..........",
    "..##.##.........",
    "..#######.......",
    ".#########......",
    ".###oE####......",
    "##########......",
    "##############..",
    "###############.",
    ".###########dd#.",
    "..#########dd...",
    "...######tt.....",
    "....#####t......",
    ".....###mm.mm.m.",
    "......#.........",
    "................",
]


def icon(opt):
    a = np.zeros((16, 16, 4), np.uint8)
    fur = {"a": STORM_L, "b": ASH_W, "c": STORM}[opt]
    dark = (50, 56, 76)
    for y, row in enumerate(ICON):
        for x, ch in enumerate(row):
            if ch == "#":
                a[y, x] = (*fur, 255) if y < 9 else (*dark, 255)
            elif ch == "o":
                a[y, x] = (*dark, 255)
            elif ch == "E":
                a[y, x] = (*BOLT, 255)
            elif ch == "d":
                a[y, x] = (*dark, 255)
            elif ch == "t":
                a[y, x] = (*ASH_W, 255)
            elif ch == "m":
                a[y, x] = (*BOLT_B, 255)
    # 縁取り (読みやすさのため、透明の所の隣を暗く)
    alpha = a[..., 3] > 0
    out = a.copy()
    for y in range(16):
        for x in range(16):
            if not alpha[y, x] and any(0 <= y + dy < 16 and 0 <= x + dx < 16 and alpha[y + dy, x + dx]
                                       for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y, x] = (*DARK, 200)
    return out


def sample(fr, nt, ic, frac, stun=False):
    """見本: ゲームと同じ順 (枠 → 帯 → 刻み)、顔の印を左に"""
    img = Image.new("RGBA", (W + 4, 36), (40, 44, 56, 255))
    im = Image.fromarray(ic, "RGBA")
    img.alpha_composite(im, (2, 0))
    d = ImageDraw.Draw(img)
    d.text((22, 3), "Raiju" + ("  stunned" if stun else ""), fill=(255, 255, 255))
    f = Image.fromarray(fr, "RGBA")
    img.alpha_composite(f, (2, 18))
    x0, y0, x1, y1 = TRACK
    n = round((x1 - x0) * frac)
    if n > 0:
        col = FILL_STUN if stun else FILL
        d.rectangle([2 + x0, 18 + y0, 2 + x0 + n - 1, 18 + y1 - 1], fill=(*col, 255))
        d.line([2 + x0, 18 + y0, 2 + x0 + n - 1, 18 + y0], fill=(*FILL_TOP, 255))
    img.alpha_composite(Image.fromarray(nt, "RGBA"), (2, 18))
    return img.resize((img.width * 4, img.height * 4), Image.NEAREST)


def sheet(opts):
    nt = notches()
    rows = []
    for o in opts:
        fr, ic = frame(o), icon(o)
        rows.append([sample(fr, nt, ic, 1.0), sample(fr, nt, ic, 0.6), sample(fr, nt, ic, 0.35, stun=True)])
    w = rows[0][0].width
    h = rows[0][0].height
    out = Image.new("RGBA", (w * 3 + 40, (h + 30) * len(rows) + 20), (24, 26, 34, 255))
    d = ImageDraw.Draw(out)
    import sys
    sys.path.insert(0, str(ROOT / "tools/rough"))
    import voxel as vx
    font = vx.font(20)
    names = {"a": "a 稲妻の縁 (採用)", "b": "b 放電の粒", "c": "c 避雷針の端"}
    for i, (o, row) in enumerate(zip(opts, rows)):
        y = 10 + i * (h + 30)
        d.text((10, y - 4), names[o], fill=(230, 230, 240), font=font)
        for j, im in enumerate(row):
            out.alpha_composite(im, (10 + j * (w + 10), y + 20))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", choices=("a", "b", "c"), default=ADOPTED)
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    Image.fromarray(frame(args.option), "RGBA").save(OUT / "raiju_frame.png")
    Image.fromarray(notches(), "RGBA").save(OUT / "raiju_notches.png")
    Image.fromarray(icon(args.option), "RGBA").save(OUT / "raiju_icon.png")
    ROUGHS.parent.mkdir(parents=True, exist_ok=True)
    sheet(["a", "b", "c"]).save(ROUGHS)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet([args.option]).save(PREVIEW)
    print("raiju bar:", args.option, "->", OUT)


if __name__ == "__main__":
    main()
