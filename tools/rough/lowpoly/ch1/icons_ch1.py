"""第一章のアイテム 32x32 の絵 4 枚 (旅の覚書 2 案、鼓動守 2 案) と、シート。
材質の色と縁の作りは、既存のアイテム絵 (石の護符・琥珀の護符など) に合わせる: 暗い輪郭 1 画素、左上の光、限られた色。

実行: python tools/rough/lowpoly/ch1/icons_ch1.py
出力: docs/review/ch1_roughs/icons/{notebook_a,notebook_b,pulse_ward_a,pulse_ward_b}_32.png
      docs/review/ch1_roughs/icons_ch1_32.png (1x / 8x を暗い・明るい背景と、インベントリの枠で)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[4]
OUT = ROOT / "docs/review/ch1_roughs"
ICON_DIR = OUT / "icons"
OUTLINE = (26, 24, 30, 255)

FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def c(rgb):
    return tuple(rgb) + (255,)


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def poly(im, pts, rgb):
    ImageDraw.Draw(im).polygon(pts, fill=c(rgb))


def rect(im, x0, y0, x1, y1, rgb):
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=c(rgb))


def px(im, x, y, rgb):
    if 0 <= x < 32 and 0 <= y < 32:
        im.putpixel((x, y), c(rgb))


def line(im, pts, rgb):
    ImageDraw.Draw(im).line(pts, fill=c(rgb), width=1)


def ellipse(im, box, rgb):
    ImageDraw.Draw(im).ellipse(box, fill=c(rgb))


def outline(im):
    """透明に接する不透明な画素を暗い輪郭にする (1 画素)。"""
    a = np.array(im)
    op = a[..., 3] > 0
    pad = np.pad(op, 1)
    nb = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    ring = nb & ~op
    for y, x in zip(*np.nonzero(ring)):
        im.putpixel((int(x), int(y)), OUTLINE)
    return im


def light_edge(im, rgb):
    """左上の光: 形の上と左の縁の 1 画素を明るい色にする (輪郭の内側)。"""
    a = np.array(im)
    op = a[..., 3] > 0
    up = np.pad(op, ((1, 0), (0, 0)))[:-1, :]
    lf = np.pad(op, ((0, 0), (1, 0)))[:, :-1]
    edge = op & (~up | ~lf)
    for y, x in zip(*np.nonzero(edge)):
        if tuple(a[y, x, :3]) != tuple(OUTLINE[:3]):
            im.putpixel((int(x), int(y)), c(rgb))
    return im


# ------------------------------------------------------------------ 旅の覚書 (手帳)

def notebook_a():
    """灰青の表紙、クリームの小口、苔の葉、鯨道の矢 (琥珀の糸で縫う)。"""
    im = canvas()
    rect(im, 8, 4, 25, 27, (232, 222, 196))      # 小口 (ページ)
    for y in range(6, 27, 3):
        line(im, [(24, y), (25, y)], (196, 184, 156))
    rect(im, 5, 3, 21, 27, (62, 78, 96))          # 表紙
    rect(im, 6, 4, 20, 26, (78, 96, 116))         # 表紙の面
    rect(im, 5, 27, 21, 27, (44, 56, 70))         # 下の縁
    for y in range(5, 27, 2):                      # 糸 (縫い目): 左の縁に琥珀の点線
        px(im, 7, y, (214, 146, 56))
    # 苔の葉 (押し葉)
    poly(im, [(11, 8), (15, 6), (18, 9), (16, 13), (12, 13)], (88, 124, 60))
    line(im, [(12, 12), (16, 8)], (56, 84, 40))
    # 鯨道の矢 (琥珀)
    line(im, [(9, 19), (17, 19)], (214, 146, 56))
    line(im, [(14, 16), (17, 19), (14, 22)], (214, 146, 56))
    # 題の帯 (白い小札)
    rect(im, 9, 24, 16, 24, (206, 200, 186))
    return light_edge(outline(im), (112, 130, 150))


def notebook_b():
    """骨白の表紙、濃い石の縁、琥珀の糸。矢の印は濃い石の色。"""
    im = canvas()
    rect(im, 8, 4, 25, 27, (236, 228, 204))
    for y in range(6, 27, 3):
        line(im, [(24, y), (25, y)], (200, 190, 162))
    rect(im, 5, 3, 21, 27, (206, 196, 172))      # 骨白の表紙
    rect(im, 5, 3, 21, 4, (52, 56, 66))           # 上の濃い石の縁
    rect(im, 5, 26, 21, 27, (52, 56, 66))         # 下の縁
    rect(im, 5, 3, 6, 27, (52, 56, 66))           # 左の縁
    for y in range(6, 26, 2):                      # 琥珀の糸 (右の縁)
        px(im, 19, y, (214, 146, 56))
    # 鯨道の矢 (濃い石)
    line(im, [(9, 14), (16, 14)], (60, 66, 78))
    line(im, [(13, 11), (16, 14), (13, 17)], (60, 66, 78))
    # 骨の留め (小さい丸)
    ellipse(im, (10, 20, 14, 24), (240, 232, 212))
    ellipse(im, (11, 21, 13, 23), (200, 188, 160))
    return light_edge(outline(im), (246, 240, 224))


# ------------------------------------------------------------------ 鼓動守 (お守り)

def pulse_ward_a():
    """岩殻の護り: 灰色の岩殻の板に骨の小さな貝、中心に琥珀の核。茶の紐。"""
    im = canvas()
    line(im, [(16, 1), (16, 4)], (112, 76, 46))
    line(im, [(15, 2), (17, 2)], (112, 76, 46))
    # 岩殻の板 (灰色、角を落とした円)
    poly(im, [(10, 6), (22, 6), (27, 11), (27, 23), (22, 28), (10, 28), (5, 23), (5, 11)], (116, 124, 136))
    rect(im, 9, 7, 22, 8, (150, 158, 168))        # 上の光
    poly(im, [(6, 12), (9, 9), (9, 27), (6, 23)], (84, 92, 104))   # 左の影
    # 骨の貝 (中心の外)
    poly(im, [(10, 10), (14, 9), (14, 13), (10, 13)], (226, 212, 180))
    # 琥珀の核 (鼓動の光)
    ellipse(im, (12, 13, 20, 21), (200, 120, 40))
    ellipse(im, (13, 14, 19, 20), (255, 186, 70))
    px(im, 14, 15, (255, 240, 190))
    # 岩殻の割れ目
    line(im, [(21, 12), (24, 16), (22, 20)], (70, 78, 90))
    return light_edge(outline(im), (170, 178, 190))


def pulse_ward_b():
    """骨白の護り: 骨の貝 (濃い石の縁)、琥珀の鼓動の線が中を走る。深緑の紐。"""
    im = canvas()
    line(im, [(10, 1), (16, 6)], (62, 90, 52))
    line(im, [(22, 1), (16, 6)], (62, 90, 52))
    ellipse(im, (6, 6, 26, 28), (58, 62, 72))     # 濃い石の縁
    ellipse(im, (8, 8, 24, 26), (232, 224, 198))  # 骨の面
    # 骨の貝の筋
    for k in range(3):
        line(im, [(10 + k, 22 - 2 * k), (16, 14 - k), (22 - k, 22 - 2 * k)], (200, 190, 160))
    # 鼓動の線 (琥珀): 中央の横を走る
    pts = [(7, 17), (11, 17), (13, 13), (15, 21), (17, 15), (19, 17), (25, 17)]
    line(im, pts, (232, 150, 52))
    for x, y in pts[1:-1]:
        px(im, x, y, (255, 206, 110))
    return light_edge(outline(im), (250, 246, 232))


ICONS = {
    "notebook_a": notebook_a,
    "notebook_b": notebook_b,
    "pulse_ward_a": pulse_ward_a,
    "pulse_ward_b": pulse_ward_b,
}


def sheet(icons):
    S = 8
    labels = {"notebook_a": "旅の覚書 A 灰青の表紙", "notebook_b": "旅の覚書 B 骨白の表紙",
              "pulse_ward_a": "鼓動守 A 岩殻の護り", "pulse_ward_b": "鼓動守 B 骨白の護り"}
    cols = ["1x 暗い背景", "8x 暗い背景", "1x 明るい背景", "8x 明るい背景", "インベントリの枠 (4x)"]
    CW, RH = 280, 270
    LABW = 200
    W = LABW + len(cols) * CW + 10
    H = 60 + len(icons) * RH + 20
    S_img = Image.new("RGB", (W, H), (24, 28, 36))
    dr = ImageDraw.Draw(S_img)
    dr.text((10, 10), "第一章のアイテム 32x32 (旅の覚書・鼓動守、各 2 案)", font=font(20), fill=(255, 255, 255))
    for j, cname in enumerate(cols):
        dr.text((LABW + j * CW + 6, 38), cname, font=font(13), fill=(200, 205, 215))
    for i, (key, im) in enumerate(icons):
        y = 60 + i * RH
        dr.text((10, y + 8), labels[key], font=font(14), fill=(255, 232, 160))
        dark = Image.new("RGBA", (32, 32), (46, 52, 62, 255))
        light = Image.new("RGBA", (32, 32), (214, 208, 196, 255))
        d1 = dark.copy(); d1.alpha_composite(im)
        l1 = light.copy(); l1.alpha_composite(im)
        d8 = d1.resize((256, 256), Image.NEAREST)
        l8 = l1.resize((256, 256), Image.NEAREST)
        x = LABW
        S_img.paste(d1.convert("RGB"), (x, y))
        S_img.paste(d8.convert("RGB"), (x + CW, y))
        S_img.paste(l1.convert("RGB"), (x + 2 * CW, y))
        S_img.paste(l8.convert("RGB"), (x + 3 * CW, y))
        # インベントリの枠: 灰の枠 (4x) に絵を置く
        slot = Image.new("RGBA", (40, 40), (139, 139, 139, 255))
        ImageDraw.Draw(slot).rectangle([0, 0, 39, 39], outline=(55, 55, 55, 255))
        ImageDraw.Draw(slot).rectangle([1, 1, 38, 38], outline=(255, 255, 255, 120))
        slot.alpha_composite(im, (4, 4))
        S_img.paste(slot.resize((160, 160), Image.NEAREST).convert("RGB"), (x + 4 * CW, y))
    return S_img


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    icons = []
    for key, fn in ICONS.items():
        im = fn()
        im.save(ICON_DIR / f"{key}_32.png")
        icons.append((key, im))
    S_img = sheet(icons)
    p = OUT / "icons_ch1_32.png"
    S_img.save(p)
    print("sheet", p, S_img.size)


if __name__ == "__main__":
    main()
