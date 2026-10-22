# -*- coding: utf-8 -*-
"""前の私の落とし物 (深淵の墓の遺品 12 点) の絵 (32x32)。docs/design/36 の 9 章・決定 257。

  python tools/abyss/gen_abyss_relic_art.py              採用の型 (既定 B) で絵と item モデルを書く
  python tools/abyss/gen_abyss_relic_art.py --style A    型を A (平ら) にして書き直す (A / B / C)
  python tools/abyss/gen_abyss_relic_art.py --sheet-only 型の比べ用の一覧だけ書く

出力:
  src/main/resources/assets/skyabyss/textures/item/abyss_relic_<層>_<番号>.png
  src/main/resources/assets/skyabyss/models/item/abyss_relic_<層>_<番号>.json
  docs/review/abyss_relics/relic_icons_rough.png   3 型 × 12 点の一覧 (型を選ぶための比べ用。毎回書く)

型 (ラフ 3 案):
  A 平ら      : 色を一色ずつ塗り、濃い縁。影なし
  B 二色の縁  : 縁取りに加えて、左上に明るい縁・右下に暗い縁 (深淵の既存の手持ち品と同じ作り)。今は仮の採用
  C 淡い光    : 色をやわらげ、後ろに淡い光。縁は暗くせず明るい白の縁
型を選び直したら --style で書き直せば、絵と模型が入れ替わる (中身の図形は変えない)。
"""
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "src/main/resources/assets/skyabyss/textures/item")
MOD = os.path.join(ROOT, "src/main/resources/assets/skyabyss/models/item")
REVIEW = os.path.join(ROOT, "docs/review/abyss_relics")

DEFAULT_STYLE = "B"
# 一覧の画の中の名 (PIL の既定の字は日本語が出ないので英字)
STYLE_NAMES = {"A": "A flat", "B": "B two-tone (provisional)", "C": "C soft glow"}
DARK_OUTLINE = (22, 16, 30, 255)
HALO = (255, 246, 222, 230)
PANEL = (34, 28, 44, 255)


def mix(c, target, k):
    """色 c を target へ k (0〜1) だけ寄せる。"""
    return tuple(int(round(c[i] + (target[i] - c[i]) * k)) for i in range(3)) + (255,)


def lighten(c, k):
    return mix(c, (255, 255, 255), k)


def darken(c, k):
    return mix(c, (0, 0, 0), k)


# ---------------------------------------------------------------- 形 (32x32 の L 画像に 255 で塗る。穴は 0)
def bone_body(d):
    d.line([(9, 23), (22, 10)], fill=255, width=6)
    for box in ((3, 17, 11, 25), (6, 23, 14, 31), (20, 7, 28, 15), (18, 4, 26, 12)):
        d.ellipse(box, fill=255)


def bone_detail(d):
    d.line([(14, 17), (17, 14)], fill=255, width=1)


def lamp_body(d):
    d.polygon([(10, 8), (22, 8), (19, 15), (13, 15)], fill=255)
    d.rectangle((15, 15, 17, 22), fill=255)
    d.rectangle((9, 22, 23, 26), fill=255)
    d.polygon([(13, 9), (19, 9), (17, 13), (15, 13)], fill=0)


def lamp_detail(d):
    d.rectangle((9, 22, 23, 23), fill=255)


def notebook_body(d):
    d.polygon([(7, 6), (24, 8), (25, 25), (9, 26), (8, 16)], fill=255)


def notebook_detail(d):
    for y in (12, 16, 20):
        d.line([(11, y), (22, y + 1)], fill=255, width=1)


def salt_body(d):
    d.polygon([(16, 4), (26, 11), (25, 23), (16, 28), (7, 23), (6, 11)], fill=255)


def salt_detail(d):
    d.line([(6, 11), (16, 17), (26, 11)], fill=255, width=1)
    d.line([(16, 17), (16, 28)], fill=255, width=1)


def tag_body(d):
    d.rounded_rectangle((8, 5, 24, 27), radius=3, fill=255)
    d.rectangle((15, 8, 17, 10), fill=0)


def tag_marks_detail(d):
    d.line([(12, 15), (20, 15)], fill=255, width=1)
    d.line([(12, 19), (18, 19)], fill=255, width=1)


def tag_blank_detail(d):
    d.rectangle((15, 8, 17, 10), fill=255)


def shells_body(d):
    for box in ((2, 14, 14, 27), (9, 9, 23, 23), (18, 14, 30, 27)):
        d.ellipse(box, fill=255)


def shells_detail(d):
    for x in (8, 16, 24):
        d.line([(x, 24), (x, 16)], fill=255, width=1)


def cord_body(d):
    d.line([(6, 8), (14, 14), (18, 22), (26, 24)], fill=255, width=4, joint="curve")


def cord_detail(d):
    d.ellipse((23, 21, 28, 27), fill=255)


def ring_body(d):
    d.ellipse((4, 8, 28, 24), fill=255)
    d.ellipse((9, 11, 23, 21), fill=0)


def ring_detail(d):
    d.ellipse((19, 8, 23, 11), fill=255)


def bell_body(d):
    d.ellipse((14, 4, 18, 8), fill=255)
    d.polygon([(9, 9), (23, 9), (26, 22), (6, 22)], fill=255)
    d.rectangle((5, 20, 27, 23), fill=255)
    d.ellipse((14, 23, 18, 28), fill=255)


def bell_detail(d):
    d.line([(9, 15), (23, 15)], fill=255, width=1)
    d.ellipse((14, 23, 18, 28), fill=255)


def glove_body(d):
    d.polygon([(9, 13), (22, 11), (24, 24), (10, 26)], fill=255)
    for box in ((9, 5, 13, 13), (13, 4, 17, 12), (17, 5, 21, 12), (21, 7, 25, 13)):
        d.rectangle(box, fill=255)


def glove_detail(d):
    for box in ((10, 4, 12, 6), (14, 3, 16, 5), (18, 4, 20, 6), (22, 6, 24, 8)):
        d.rectangle(box, fill=255)


def stone_body(d):
    d.polygon([(6, 10), (14, 5), (24, 7), (27, 16), (22, 26), (10, 27), (5, 18)], fill=255)


def heart_detail(d):
    d.ellipse((11, 11, 16, 16), fill=255)
    d.ellipse((17, 11, 22, 16), fill=255)
    d.polygon([(11, 14), (22, 14), (16.5, 22)], fill=255)


# 層・番号・品の id・基本の色・筋の色・形・筋の形
RELICS = [
    ("bone", 1, (226, 220, 200), (96, 66, 84), bone_body, bone_detail),
    ("bone", 2, (196, 150, 70), (36, 26, 40), lamp_body, lamp_detail),
    ("bone", 3, (236, 228, 205), (70, 80, 112), notebook_body, notebook_detail),
    ("salt", 1, (214, 232, 240), (140, 182, 210), salt_body, salt_detail),
    ("salt", 2, (168, 120, 80), (236, 224, 200), tag_body, tag_marks_detail),
    ("salt", 3, (240, 214, 200), (190, 150, 140), shells_body, shells_detail),
    ("storm", 1, (66, 62, 70), (220, 110, 40), cord_body, cord_detail),
    ("storm", 2, (196, 110, 60), (110, 180, 150), ring_body, ring_detail),
    ("storm", 3, (214, 176, 84), (120, 80, 40), bell_body, bell_detail),
    ("ash", 1, (120, 110, 120), (230, 120, 40), glove_body, glove_detail),
    ("ash", 2, (230, 230, 236), (60, 60, 70), tag_body, tag_blank_detail),
    ("ash", 3, (130, 128, 140), (200, 50, 60), stone_body, heart_detail),
]


def relic_id(layer, n):
    return "abyss_relic_%s_%d" % (layer, n)


def mask_of(body):
    m = Image.new("L", (32, 32), 0)
    body(ImageDraw.Draw(m))
    return m


def detail_of(detail, body):
    d_img = Image.new("L", (32, 32), 0)
    detail(ImageDraw.Draw(d_img))
    # 筋は形の中にだけ出す
    return ImageChops.multiply(d_img, mask_of(body))


def paint(out, color, mask):
    out.paste(Image.new("RGBA", (32, 32), color), (0, 0), mask)


def glow(out, color):
    """後ろの淡い光 (型 C)。"""
    for y in range(32):
        for x in range(32):
            dist = math.hypot(x - 15.5, y - 15.5)
            a = int(max(0, 78 * (1 - dist / 16)))
            if a > 0:
                out.alpha_composite(Image.new("RGBA", (1, 1), color[:3] + (a,)), (x, y))


def render(style, base, accent, body, detail):
    m = mask_of(body)
    dm = detail_of(detail, body)
    dilated = m.filter(ImageFilter.MaxFilter(3))
    edge = ImageChops.subtract(dilated, m)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    if style == "A":
        paint(out, DARK_OUTLINE, edge)
        paint(out, base + (255,), m)
        paint(out, accent + (255,), dm)
    elif style == "B":
        paint(out, DARK_OUTLINE, edge)
        paint(out, base + (255,), m)
        hi = ImageChops.subtract(m, ImageChops.offset(m, 1, 1))
        sh = ImageChops.subtract(m, ImageChops.offset(m, -1, -1))
        paint(out, lighten(base, 0.35), hi)
        paint(out, darken(base, 0.3), sh)
        paint(out, accent + (255,), dm)
    elif style == "C":
        glow(out, base)
        paint(out, HALO, edge)
        paint(out, lighten(base, 0.25), m)
        paint(out, lighten(accent, 0.1), dm)
    else:
        raise SystemExit("unknown style: " + style)
    return out


def write_textures(style):
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MOD, exist_ok=True)
    for layer, n, base, accent, body, detail in RELICS:
        rid = relic_id(layer, n)
        render(style, base, accent, body, detail).save(os.path.join(TEX, rid + ".png"))
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + rid}}
        with open(os.path.join(MOD, rid + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(model, f, ensure_ascii=False, indent=2)
            f.write("\n")


def write_sheet():
    """3 型 × 12 点を一枚に。各点を 3 倍にして並べる。"""
    scale = 3
    cell = 32 * scale + 8
    label_w = 150
    top = 28
    sheet = Image.new("RGBA", (label_w + cell * len(RELICS), top + cell * 3), PANEL)
    draw = ImageDraw.Draw(sheet)
    for i, (layer, n, *_rest) in enumerate(RELICS):
        draw.text((label_w + i * cell + 6, 8), "%s %d" % (layer, n), fill=(230, 220, 240, 255))
    for row, style in enumerate("ABC"):
        y0 = top + row * cell
        draw.text((10, y0 + cell // 2 - 6), STYLE_NAMES[style], fill=(230, 220, 240, 255))
        for i, (layer, n, base, accent, body, detail) in enumerate(RELICS):
            icon = render(style, base, accent, body, detail).resize((32 * scale, 32 * scale), Image.NEAREST)
            sheet.alpha_composite(icon, (label_w + i * cell + 4, y0 + 4))
    os.makedirs(REVIEW, exist_ok=True)
    sheet.save(os.path.join(REVIEW, "relic_icons_rough.png"))


def main(argv):
    style = DEFAULT_STYLE
    if "--style" in argv:
        style = argv[argv.index("--style") + 1].upper()
    if "--sheet-only" not in argv:
        write_textures(style)
        print("textures written in style", style)
    write_sheet()
    print("sheet written:", os.path.join(REVIEW, "relic_icons_rough.png"))


if __name__ == "__main__":
    main(sys.argv[1:])
