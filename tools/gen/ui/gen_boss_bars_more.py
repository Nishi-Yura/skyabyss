# -*- coding: utf-8 -*-
"""残りのボスの帯の絵 (枠 182x16 と顔の印 16x16)。PhasedBossBar の寸法 (溝は x 3〜177、y 3〜8) に合わせる。

python -I tools/gen/ui/gen_boss_bars_more.py [--sheet 出力.png]
出力: src/main/resources/assets/skyabyss/textures/gui/boss/<型>_frame_pN.png と <印>_icon_pN.png (N は 1 から)
族ごとの枠 (型):
  moon           月の族 (段 3・白銀と青)        : 枠 moon_frame, 印 full_keeper・moon_core・moon_scale・shadow_embracer・waning_blade
  cloud          雲海の族 (段 3・白と金)        : 枠 cloud_frame, 印 lamp_eye・last_bellkeeper・pilgrim_eye・pilgrim_moon・pilgrim_plume・rewinder・founder
  cloud_solo     雲海の一段 (段なし)            : 枠 cloud_solo, 印 pilgrim_hand・circuit_stake
  tower_solo     塔 (段なし・白金と黒紫の半々)  : 枠 tower_solo, 印 tower_master
  storm_solo     雷の章 (段なし・銅と黄)        : 枠 storm_solo, 印 scorched_warden
  ember_solo     熾の章 (段なし・熾の色)        : 枠 ember_solo, 印 elder_thrall
  shore_solo     潮の番人 (段なし・真珠と青緑)   : 枠 shore_solo, 印 shore_warden
  named_<土地>   名のある強敵 (段なし・土地の土の色): 枠と印が同じ名 (named_meadow・named_stone・named_tide・named_ochre・named_ash)
段: 段のある型は一段目が落ち着いた縁、二段目は縁が明るく刻みが細かく、三段目は縁が白むと同時に印の差し色が明るくなる。
段の境の印 (33%・66%) は段のある枠だけに描く。
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/boss"
W, H = 182, 16
DARK = (10, 12, 18, 255)
GROOVE = (18, 20, 28, 255)
MARKS = [round(3 + (178 - 3) * f) for f in (0.33, 0.66)]

# 枠の型 -> (外の縁, 縁, 内の線, 飾り, 段があるか, 段三の白み)。二つ組の色 ((左), (右)) は半々の塔だけ
FRAMES = {
    "moon": ((34, 42, 62), (176, 196, 222), (150, 186, 236), (226, 236, 252), True),
    "cloud": ((96, 84, 52), (236, 224, 190), (232, 196, 96), (255, 246, 214), True),
    "cloud_solo": ((96, 84, 52), (236, 224, 190), (232, 196, 96), (255, 246, 214), False),
    "tower_solo": (((120, 100, 50), (28, 20, 40)), ((240, 220, 150), (58, 40, 82)), ((240, 216, 130), (150, 110, 200)),
                   ((255, 248, 220), (200, 180, 230)), False),
    "storm_solo": ((60, 36, 20), (214, 140, 72), (250, 196, 84), (255, 230, 140), False),
    "ember_solo": ((56, 22, 16), (206, 88, 44), (255, 140, 70), (255, 200, 120), False),
    "shore_solo": ((24, 58, 60), (196, 236, 230), (110, 216, 204), (240, 255, 250), False),
    "named_meadow": ((46, 52, 30), (150, 166, 96), (196, 214, 120), (226, 236, 170), False),
    "named_stone": ((48, 44, 40), (160, 150, 136), (206, 198, 180), (236, 230, 216), False),
    "named_tide": ((20, 52, 50), (92, 160, 150), (150, 220, 206), (210, 246, 236), False),
    "named_ochre": ((60, 44, 20), (176, 134, 60), (232, 190, 100), (250, 226, 160), False),
    "named_ash": ((46, 24, 18), (140, 70, 44), (204, 122, 80), (250, 190, 140), False),
}

# 印の型 -> (枠の型, 縁の色, 本体の色, 差し色, 白, 段三の差し色, 形)。段のある印は三段とも同じ形で、縁と差し色だけ変わる
ICONS = {
    "full_keeper": ("moon", (176, 196, 222), (226, 236, 252), (150, 186, 236), (255, 255, 255), (255, 228, 150), "full"),
    "moon_core": ("moon", (176, 196, 222), (210, 224, 244), (120, 160, 230), (255, 255, 255), (255, 228, 150), "core"),
    "moon_scale": ("moon", (176, 196, 222), (200, 212, 232), (150, 186, 236), (255, 255, 255), (255, 228, 150), "scale"),
    "shadow_embracer": ("moon", (176, 196, 222), (70, 80, 110), (150, 186, 236), (255, 255, 255), (255, 228, 150), "embrace"),
    "waning_blade": ("moon", (176, 196, 222), (226, 236, 252), (120, 160, 230), (255, 255, 255), (255, 228, 150), "blade"),
    "lamp_eye": ("cloud", (236, 224, 190), (255, 246, 214), (232, 196, 96), (255, 255, 255), (255, 214, 96), "eye"),
    "last_bellkeeper": ("cloud", (236, 224, 190), (240, 232, 200), (232, 196, 96), (255, 255, 255), (255, 214, 96), "bell"),
    "pilgrim_eye": ("cloud", (236, 224, 190), (255, 246, 214), (232, 196, 96), (255, 255, 255), (255, 214, 96), "ringeye"),
    "pilgrim_moon": ("cloud", (236, 224, 190), (255, 246, 214), (232, 196, 96), (255, 255, 255), (255, 214, 96), "chainmoon"),
    "pilgrim_plume": ("cloud", (236, 224, 190), (255, 250, 236), (232, 196, 96), (255, 255, 255), (255, 214, 96), "plume"),
    "rewinder": ("cloud", (236, 224, 190), (240, 232, 200), (232, 196, 96), (255, 255, 255), (255, 214, 96), "rewind"),
    "founder": ("cloud", (236, 224, 190), (240, 232, 200), (232, 196, 96), (255, 255, 255), (255, 214, 96), "pole"),
    "pilgrim_hand": ("cloud_solo", (236, 224, 190), (255, 250, 236), (232, 196, 96), (255, 255, 255), None, "hand"),
    "circuit_stake": ("cloud_solo", (236, 224, 190), (150, 170, 196), (232, 196, 96), (255, 255, 255), None, "stake"),
    "tower_master": ("tower_solo", (240, 220, 150), (255, 248, 220), (200, 180, 230), (255, 255, 255), (58, 40, 82), "tower"),
    "scorched_warden": ("storm_solo", (214, 140, 72), (196, 110, 56), (250, 196, 84), (255, 255, 255), None, "visor"),
    "elder_thrall": ("ember_solo", (206, 88, 44), (80, 52, 46), (255, 140, 70), (255, 255, 255), None, "hood"),
    "shore_warden": ("shore_solo", (196, 236, 230), (236, 244, 240), (110, 216, 204), (255, 255, 255), None, "shell"),
    "named_meadow": ("named_meadow", (150, 166, 96), (226, 236, 170), (196, 214, 120), (255, 255, 255), None, "flag"),
    "named_stone": ("named_stone", (160, 150, 136), (180, 168, 150), (206, 198, 180), (255, 255, 255), None, "boar"),
    "named_tide": ("named_tide", (92, 160, 150), (210, 246, 236), (150, 220, 206), (255, 255, 255), None, "crab"),
    "named_ochre": ("named_ochre", (176, 134, 60), (240, 214, 150), (232, 190, 100), (255, 255, 255), None, "ram"),
    "named_ash": ("named_ash", (140, 70, 44), (204, 122, 80), (250, 190, 140), (255, 255, 255), None, "lizard"),
}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def pick(v, x):
    """二つ組 ((左), (右)) なら x で選ぶ。単色ならそのまま"""
    if isinstance(v[0], tuple):
        return v[0] if x < W // 2 else v[1]
    return v


def frame(kind, ph):
    outer, rim, inner, deco, phased = FRAMES[kind][:5]
    a = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = a.load()
    alert = 0.15 * (ph - 1) if phased else 0.0
    step = {1: 16, 2: 10, 3: 7}[ph] if phased else 16
    for y in range(12):
        for x in range(W):
            if (x in (0, W - 1)) and y in (0, 11):
                continue
            edge = min(y, 11 - y, x, W - 1 - x)
            rimc = lerp(pick(rim, x), (255, 255, 255), alert)
            if edge == 0:
                c = pick(outer, x)
            elif edge == 1:
                c = rimc
            elif edge == 2:
                c = DARK[:3]
            else:
                c = GROOVE[:3]
            px[x, y] = (*c, 255)
    for x in range(3, W - 3):
        px[x, 2] = (*pick(inner, x), 255)
    # 上の縁の刻み (段が進むほど細かく)
    for x in range(6, W - 6, step):
        for k in (0, 1):
            if x + k < W:
                px[x + k, 0] = (*pick(deco, x), 255)
    if phased and ph == 3:
        for x in range(4, W - 4, 22):
            for dx, dy in ((0, 0), (1, 1), (2, 1), (3, 2)):
                if x + dx < W:
                    px[x + dx, 10 - dy] = (*pick(deco, x), 255)
    if phased:
        for mx in MARKS:
            for dx, y in ((0, 12), (-1, 13), (0, 13), (1, 13), (0, 14)):
                px[mx + dx, y] = (*pick(inner, mx), 255)
            px[mx, 9] = (*pick(inner, mx), 255)
    return a


def icon(name, ph):
    kind, ring, body, acc, white, acc3, shape = ICONS[name]
    phased = FRAMES[kind][4]
    ring_c = lerp(ring, (255, 255, 255), 0.2 * (ph - 1)) if phased else ring
    a = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(a)
    # 円の台: 縁の色 (外) と暗い地 (内)
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if r <= 7.6:
                c = ring_c if r > 6.4 else DARK[:3]
                if shape == "tower" and x >= 8 and r > 6.4:
                    c = acc3
                d.point((x, y), fill=(*c, 255))
    ac = acc
    if phased and ph == 3:
        ac = lerp(acc, acc3, 0.6)
    B = (*body, 255)
    A = (*ac, 255)
    Wh = (*white, 255)
    D = (*DARK[:3], 255)

    if shape == "full":
        d.ellipse((3, 3, 12, 12), fill=B)
        d.point([(6, 6), (9, 9), (7, 10), (10, 5)], fill=A)
        d.ellipse((6, 6, 9, 9), fill=Wh)
    elif shape == "core":
        d.ellipse((4, 4, 11, 11), fill=B)
        d.ellipse((6, 6, 9, 9), fill=Wh)
        for i in range(0, 360, 12):
            x = round(7.5 + 6 * math.cos(math.radians(i)))
            y = round(7.5 + 2.4 * math.sin(math.radians(i)) + 0.0)
            if 1 <= x <= 14 and 1 <= y <= 14:
                d.point((x, y), fill=A)
    elif shape == "scale":
        d.line((3, 4, 12, 4), fill=B)
        d.line((7, 4, 7, 12), fill=B)
        d.line((3, 4, 2, 7), fill=A)
        d.line((12, 4, 13, 7), fill=A)
        d.line((1, 7, 4, 7), fill=A)
        d.line((10, 7, 13, 7), fill=A)
        d.line((1, 7, 2, 9), fill=A)
        d.line((4, 7, 3, 9), fill=A)
        d.line((10, 7, 11, 9), fill=A)
        d.line((13, 7, 12, 9), fill=A)
        d.line((5, 12, 9, 12), fill=B)
    elif shape == "embrace":
        d.line((4, 2, 4, 8), fill=B)
        d.line((4, 8, 2, 12), fill=B)
        d.line((11, 2, 11, 8), fill=B)
        d.line((11, 8, 13, 12), fill=B)
        d.line((5, 3, 5, 8), fill=B)
        d.ellipse((5, 5, 10, 11), fill=D)
        d.point([(7, 8), (8, 8)], fill=A)
        d.line((3, 13, 5, 12), fill=A)
        d.line((11, 12, 13, 13), fill=A)
    elif shape == "blade":
        d.ellipse((2, 3, 10, 11), fill=B)
        d.ellipse((4, 2, 11, 9), fill=D)
        d.line((5, 13, 12, 6), fill=A)
        d.point((12, 5), fill=Wh)
    elif shape == "eye":
        d.polygon([(2, 8), (8, 4), (14, 8), (8, 12)], fill=B)
        d.ellipse((6, 6, 10, 10), fill=A)
        d.point((8, 8), fill=D)
        d.line((2, 8, 14, 8), fill=D)
    elif shape == "bell":
        d.polygon([(4, 10), (5, 5), (8, 3), (11, 5), (12, 10), (13, 11), (3, 11)], fill=B)
        d.ellipse((7, 9, 9, 11), fill=A)
        d.point((8, 2), fill=A)
    elif shape == "ringeye":
        for i in range(0, 360, 30):
            d.point((round(7.5 + 6 * math.cos(math.radians(i))), round(7.5 + 6 * math.sin(math.radians(i)))), fill=A)
        d.polygon([(5, 8), (8, 6), (11, 8), (8, 10)], fill=B)
        d.point((8, 8), fill=Wh)
    elif shape == "chainmoon":
        d.ellipse((2, 3, 9, 10), fill=B)
        d.ellipse((4, 2, 10, 8), fill=D)
        d.ellipse((10, 10, 12, 12), outline=A)
        d.ellipse((11, 12, 13, 14), outline=A)
    elif shape == "plume":
        d.line((3, 12, 11, 4), fill=Wh)
        d.polygon([(5, 9), (9, 3), (12, 4), (8, 9)], fill=B)
        d.polygon([(4, 11), (7, 9), (9, 12)], fill=A)
    elif shape == "rewind":
        d.arc((2, 2, 13, 13), 200, 60, fill=B, width=1)
        d.line((10, 12, 12, 10), fill=A)
        d.line((10, 12, 13, 12), fill=A)
        d.point((8, 8), fill=A)
    elif shape == "pole":
        d.line((8, 2, 8, 4), fill=Wh)
        d.polygon([(5, 5), (11, 5), (12, 11), (4, 11)], fill=B)
        d.ellipse((7, 11, 9, 13), fill=A)
    elif shape == "hand":
        d.ellipse((4, 7, 11, 13), fill=B)
        d.line((5, 7, 4, 3), fill=B)
        d.line((7, 6, 7, 2), fill=B)
        d.line((9, 6, 9, 2), fill=B)
        d.line((11, 7, 12, 3), fill=B)
        d.line((4, 9, 2, 7), fill=A)
    elif shape == "stake":
        d.polygon([(6, 2), (10, 2), (9, 13), (7, 13)], fill=B)
        d.line((6, 5, 10, 5), fill=A)
        d.line((6, 9, 10, 9), fill=A)
        d.point([(3, 7), (13, 7)], fill=A)
    elif shape == "tower":
        d.rectangle((5, 5, 10, 13), fill=B)
        d.rectangle((5, 3, 6, 4), fill=B)
        d.rectangle((9, 3, 10, 4), fill=B)
        d.rectangle((7, 10, 8, 13), fill=D)
        d.point((8, 7), fill=Wh)
        # 半々: 右の半分は黒紫
        for y in range(16):
            for x in range(8, 16):
                if a.getpixel((x, y)) == (*B[:3], 255):
                    d.point((x, y), fill=(*acc3, 255))
    elif shape == "visor":
        d.rectangle((4, 4, 11, 8), fill=B)
        d.point([(5, 6), (10, 6)], fill=A)
        d.polygon([(1, 9), (4, 6), (4, 10)], fill=A)
        d.polygon([(14, 9), (11, 6), (11, 10)], fill=A)
    elif shape == "hood":
        d.polygon([(8, 2), (4, 12), (12, 12)], fill=B)
        d.polygon([(6, 9), (10, 9), (8, 7)], fill=D)
        d.point([(7, 10), (9, 10)], fill=A)
    elif shape == "shell":
        d.polygon([(8, 12), (3, 7), (4, 4), (8, 3), (12, 4), (13, 7)], fill=B)
        for tx in (5, 8, 11):
            d.line((8, 12, tx, 4), fill=A)
        d.ellipse((7, 8, 9, 10), fill=Wh)
    elif shape == "flag":
        d.line((4, 2, 4, 13), fill=B)
        d.polygon([(5, 3), (12, 5), (5, 8)], fill=A)
    elif shape == "boar":
        d.ellipse((3, 4, 12, 11), fill=B)
        d.line((5, 10, 3, 12), fill=Wh)
        d.line((10, 10, 12, 12), fill=Wh)
        d.point([(6, 6), (9, 6)], fill=D)
    elif shape == "crab":
        d.ellipse((4, 7, 11, 11), fill=B)
        d.ellipse((1, 4, 3, 6), fill=A)
        d.ellipse((12, 4, 14, 6), fill=A)
        d.line((4, 11, 2, 13), fill=B)
        d.line((11, 11, 13, 13), fill=B)
        d.point([(6, 5), (9, 5)], fill=Wh)
    elif shape == "ram":
        d.arc((2, 3, 9, 10), 100, 260, fill=B, width=2)
        d.arc((6, 3, 13, 10), -80, 80, fill=B, width=2)
        d.ellipse((5, 6, 10, 11), fill=B)
        d.point([(7, 8), (9, 8)], fill=D)
    elif shape == "lizard":
        d.line((3, 12, 11, 5), fill=B, width=2)
        d.ellipse((10, 3, 13, 6), fill=B)
        d.line((6, 8, 4, 10), fill=A)
        d.line((9, 6, 11, 8), fill=A)
        d.point((12, 4), fill=D)
    return a


def make_all():
    OUT.mkdir(parents=True, exist_ok=True)
    out = []
    for kind, spec in FRAMES.items():
        phases = (1, 2, 3) if spec[4] else (1,)
        for ph in phases:
            p = OUT / ("%s_frame_p%d.png" % (kind, ph))
            frame(kind, ph).save(p)
            out.append(p)
    for name, spec in ICONS.items():
        phases = (1, 2, 3) if FRAMES[spec[0]][4] else (1,)
        for ph in phases:
            p = OUT / ("%s_icon_p%d.png" % (name, ph))
            icon(name, ph).save(p)
            out.append(p)
    return out


def sheet(path):
    names = [(n, 1) for n in ICONS] + [(n, 3) for n in ICONS if FRAMES[ICONS[n][0]][4]]
    cols = 8
    rows = (len(names) + cols - 1) // cols
    img = Image.new("RGBA", (cols * 80, rows * 80), (44, 48, 60, 255))
    for i, (n, ph) in enumerate(names):
        im = icon(n, ph).resize((64, 64), Image.NEAREST)
        img.paste(im, ((i % cols) * 80 + 6, (i // cols) * 80 + 6), im)
    img.save(path)


if __name__ == "__main__":
    files = make_all()
    print("%d 枚の絵を書いた -> %s" % (len(files), OUT))
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])
