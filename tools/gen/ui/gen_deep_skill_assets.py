"""橋・淵・前世の技・紋・職の新しい技の絵 (icons2.png、256x192、32x32 の升目 8x6) を書く。docs/design/31 の A09・A14・B08・B09、43。

番号 (Skill.iconIndex は 100 + n、SkillTree.iconIndex は 117 + n):
  0..4   橋 (砂塵の盾突き・潮風の唄・蒸気の鐘・熾の矢雨・狙い石)
  5..10  淵 (引き・鏡・残響・貢ぎ・顎・誓い)
  11..16 前世の技 (遺響の一撃・旧き見張り・墓鐘・記憶の刃・最期の光・魂の糸)
  17..19 木の印 (橋・淵・前世)
  20..24 紋 (岩・風・潮・灰・骨)
  25..36 職の新しい技 (星読み: 雷の矢・星読みの心得・熾火の玉・凍て潮・転移・星の誓い / 炉の踏み・霧隠れ・呪いの印・灯の守り札・盾の踏み込み・射手の跳び退き)
既存の icons.png と同じ丸い楯の形。縁の色で系統を分ける (橋 = 2 つのタグの二色、淵 = 紫黒、前世 = 淡い金)。
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skill/icons2.png"

INK = (18, 22, 28, 255)
TAG = {"rock": (201, 166, 107), "wind": (143, 227, 194), "tide": (111, 184, 255), "ash": (255, 138, 74), "bone": (232, 226, 207)}
WHITE = (236, 240, 244, 255)
SOFT = (170, 178, 196, 255)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def medallion(rim_a, rim_b, disc=(30, 36, 48)):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((1, 1, 30, 30), fill=INK)
    # 縁: 左上 / 右下 で二色に割る (同色なら 1 色)
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5)
            if 12.0 <= r < 14.9:
                img.putpixel((x, y), rgba(rim_a if (x + y) < 31 else rim_b))
            elif r < 12.0:
                shade_k = 1.0 - 0.35 * ((y - 3) / 26.0)
                img.putpixel((x, y), (int(disc[0] * shade_k), int(disc[1] * shade_k), int(disc[2] * shade_k), 255))
    return img


def put(img, pts, color):
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=color)


def line(img, pts, color, w=1):
    ImageDraw.Draw(img).line(pts, fill=color, width=w)


def dot(img, x, y, color, r=1):
    ImageDraw.Draw(img).ellipse((x - r, y - r, x + r, y + r), fill=color)


def arc(img, box, a0, a1, color, w=1):
    ImageDraw.Draw(img).arc(box, a0, a1, fill=color, width=w)


def ring(img, box, color, w=1):
    ImageDraw.Draw(img).ellipse(box, outline=color, width=w)


# ---------------------------------------------------------------- 橋
def dust_charge(i):
    sand = rgba(TAG["rock"])
    put(i, [(11, 8), (21, 8), (21, 17), (16, 24), (11, 17)], rgba(shade(TAG["rock"], 0.75)))
    put(i, [(13, 10), (19, 10), (19, 16), (16, 20), (13, 16)], sand)
    for y, x0 in ((10, 3), (15, 2), (20, 4)):
        line(i, [(x0, y), (x0 + 6, y)], rgba(TAG["wind"]))
    line(i, [(23, 12), (27, 12)], rgba(TAG["wind"]))
    line(i, [(23, 18), (26, 18)], rgba(TAG["wind"]))


def sea_breeze(i):
    w, t = rgba(TAG["wind"]), rgba(TAG["tide"])
    for k, c in ((0, t), (4, shade(TAG["tide"], 0.8))):
        for x in range(6, 26):
            y = 19 + k + round(2.2 * math.sin((x - 6) * 0.55))
            i.putpixel((x, y), c)
            i.putpixel((x, y + 1), c)
    arc(i, (9, 6, 21, 16), 200, 40, w, 2)
    arc(i, (12, 9, 24, 18), 20, 220, w, 1)
    dot(i, 21, 8, w)


def steam_bell(i):
    t, a = rgba(TAG["tide"]), rgba(TAG["ash"])
    put(i, [(10, 21), (22, 21), (20, 12), (18, 9), (14, 9), (12, 12)], rgba(shade(TAG["ash"], 0.85)))
    put(i, [(12, 19), (20, 19), (18, 12), (14, 12)], a)
    line(i, [(9, 22), (23, 22)], rgba(SOFT), 2)
    dot(i, 16, 24, rgba(SOFT), 1)
    for x in (11, 16, 21):
        for y in range(4, 8):
            if (x + y) % 2 == 0:
                i.putpixel((x + (y % 2), y), t)
        i.putpixel((x, 3), t)


def cinder_volley(i):
    a, b = rgba(TAG["ash"]), rgba(TAG["bone"])
    for (x1, y1, x0, y0) in ((25, 8, 8, 24), (26, 15, 8, 24), (24, 22, 8, 24)):
        line(i, [(x0, y0), (x1, y1)], b)
        dot(i, x1, y1, a, 1)
        i.putpixel((x1 + 1, y1 - 1), rgba((255, 212, 120)))
    line(i, [(7, 25), (11, 21)], b, 2)


def stone_aim(i):
    r, b = rgba(TAG["rock"]), rgba(TAG["bone"])
    put(i, [(9, 15), (23, 15), (23, 23), (9, 23)], rgba(shade(TAG["rock"], 0.7)))
    line(i, [(9, 19), (23, 19)], rgba(shade(TAG["rock"], 0.5)))
    line(i, [(16, 15), (16, 19)], rgba(shade(TAG["rock"], 0.5)))
    ring(i, (9, 5, 23, 19), b)
    line(i, [(16, 3), (16, 9)], b)
    line(i, [(16, 15), (16, 21)], b)
    line(i, [(7, 12), (13, 12)], b)
    line(i, [(19, 12), (25, 12)], b)
    dot(i, 16, 12, r)


# ---------------------------------------------------------------- 淵
V = (150, 110, 230)
VD = (88, 60, 150)


def deep_pull(i):
    cx = cy = 15.5
    for k in range(70):
        a = k * 0.28
        r = 11.0 * (1 - k / 76.0)
        x, y = int(round(cx + math.cos(a) * r)), int(round(cy + math.sin(a) * r))
        i.putpixel((x, y), rgba(V if k > 20 else (210, 190, 255)))
    dot(i, 16, 16, WHITE, 1)


def deep_mirror(i):
    line(i, [(16, 5), (16, 27)], rgba(SOFT))
    put(i, [(7, 10), (15, 16), (7, 22)], rgba(V))
    put(i, [(25, 10), (17, 16), (25, 22)], rgba(shade(V, 0.6)))
    dot(i, 16, 16, WHITE)


def deep_echo(i):
    for r, c in ((10, shade(V, 0.55)), (7, shade(V, 0.8)), (4, rgba(V))):
        ring(i, (16 - r, 16 - r, 16 + r, 16 + r), c)
    dot(i, 16, 16, WHITE, 1)
    for a in (0.5, 2.1, 3.7, 5.3):
        i.putpixel((int(16 + math.cos(a) * 12), int(16 + math.sin(a) * 12)), rgba((210, 190, 255)))


def deep_tithe(i):
    dot(i, 16, 16, WHITE, 2)
    for k, tag in enumerate(("rock", "wind", "tide", "ash", "bone")):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        dot(i, int(round(16 + math.cos(a) * 8.5)), int(round(16 + math.sin(a) * 8.5)), rgba(TAG[tag]), 2)
        line(i, [(16, 16), (int(round(16 + math.cos(a) * 8.5)), int(round(16 + math.sin(a) * 8.5)))], rgba(VD))


def deep_maw(i):
    put(i, [(5, 10), (27, 10), (27, 14), (5, 14)], rgba(VD))
    put(i, [(5, 18), (27, 18), (27, 22), (5, 22)], rgba(VD))
    for x in range(6, 27, 5):
        put(i, [(x, 14), (x + 4, 14), (x + 2, 18)], WHITE)
        put(i, [(x + 2, 18), (x + 4, 14), (x, 14)], WHITE) if False else None
    for x in range(8, 27, 5):
        put(i, [(x, 18), (x + 4, 18), (x + 2, 14)], rgba(shade(V, 1.2)))
    line(i, [(5, 16), (27, 16)], INK)


def deep_oath(i):
    put(i, [(16, 4), (28, 16), (16, 28), (4, 16)], rgba(VD))
    put(i, [(16, 8), (24, 16), (16, 24), (8, 16)], INK)
    ring(i, (10, 11, 22, 21), rgba(V))
    dot(i, 16, 16, WHITE, 2)
    dot(i, 16, 16, INK, 1)


# ---------------------------------------------------------------- 前世
G = (236, 214, 150)
GD = (170, 146, 90)


def echo_strike(i):
    line(i, [(8, 7), (24, 25)], rgba(G), 3)
    line(i, [(12, 5), (28, 22)], rgba(GD), 2)
    line(i, [(6, 11), (20, 28)], rgba(shade(GD, 0.6)), 1)
    dot(i, 24, 25, WHITE)


def old_vigil(i):
    put(i, [(12, 12), (20, 12), (21, 24), (11, 24)], rgba(GD))
    put(i, [(13, 14), (19, 14), (19, 22), (13, 22)], rgba((255, 224, 150)))
    line(i, [(11, 11), (21, 11)], rgba(G), 2)
    line(i, [(16, 6), (16, 11)], rgba(G))
    arc(i, (13, 4, 19, 10), 180, 360, rgba(G))
    dot(i, 16, 18, WHITE, 1)


def grave_bell(i):
    put(i, [(10, 22), (22, 22), (20, 14), (18, 9), (14, 9), (12, 14)], rgba(GD))
    put(i, [(12, 20), (20, 20), (18, 14), (14, 14)], rgba(G))
    line(i, [(9, 23), (23, 23)], WHITE, 2)
    dot(i, 16, 26, WHITE, 1)
    line(i, [(16, 3), (16, 7)], rgba(G))
    line(i, [(14, 5), (18, 5)], rgba(G))


def memory_edge(i):
    put(i, [(23, 5), (27, 9), (13, 23), (9, 19)], rgba(G))
    put(i, [(23, 5), (27, 9), (26, 10), (22, 6)], WHITE)
    line(i, [(7, 21), (11, 25)], rgba(GD), 3)
    for k, (x, y) in enumerate(((6, 8), (10, 6), (5, 13), (8, 10))):
        i.putpixel((x, y), rgba(G, 200 - 30 * k))


def final_light(i):
    for k in range(12):
        a = k * math.pi / 6
        r0, r1 = 5, 12 if k % 2 == 0 else 9
        line(i, [(int(16 + math.cos(a) * r0), int(16 + math.sin(a) * r0)), (int(16 + math.cos(a) * r1), int(16 + math.sin(a) * r1))],
             WHITE if k % 2 == 0 else rgba(G))
    dot(i, 16, 16, WHITE, 3)


def soul_thread(i):
    pts = []
    for k in range(60):
        t = k / 59 * 2 * math.pi
        pts.append((16 + 10 * math.sin(t), 16 + 6 * math.sin(2 * t)))
    for k in range(len(pts) - 1):
        line(i, [pts[k], pts[k + 1]], rgba(G if k % 2 else GD), 2)
    dot(i, 16, 16, WHITE)


# ---------------------------------------------------------------- 木の印
def tree_hybrid(i):
    ring(i, (6, 9, 18, 21), rgba(TAG["rock"]), 2)
    ring(i, (14, 9, 26, 21), rgba(TAG["wind"]), 2)
    ring(i, (10, 14, 22, 26), rgba(TAG["tide"]), 2)


def tree_deep(i):
    deep_pull(i)
    ring(i, (4, 4, 28, 28), rgba(VD))


def tree_past(i):
    put(i, [(9, 6), (23, 6), (17, 16), (23, 26), (9, 26), (15, 16)], rgba(GD))
    put(i, [(11, 8), (21, 8), (16, 15)], rgba(G))
    put(i, [(12, 24), (20, 24), (16, 19)], rgba(G))
    line(i, [(8, 6), (24, 6)], WHITE, 1)
    line(i, [(8, 26), (24, 26)], WHITE, 1)


# ---------------------------------------------------------------- 職の新しい技 (docs/design/43)。星読みは夜の藍の縁
S = (150, 160, 240)
SD = (70, 70, 150)
Y = (255, 236, 140)


def thunder_bolt(i):
    put(i, [(19, 4), (10, 17), (15, 17), (12, 28), (23, 13), (17, 13), (21, 4)], rgba(Y))
    line(i, [(19, 5), (12, 16)], WHITE)
    dot(i, 12, 27, WHITE)


def star_reading(i):
    put(i, [(16, 5), (18, 14), (27, 16), (18, 18), (16, 27), (14, 18), (5, 16), (14, 14)], rgba(S))
    put(i, [(16, 9), (17, 15), (16, 16), (15, 15)], WHITE)
    for x, y in ((8, 8), (24, 9), (23, 24)):
        i.putpixel((x, y), WHITE)


def ember_orb(i):
    for k in range(5):
        dot(i, 9 + k * 2, 22 - k * 2, rgba((255, 120 + k * 20, 40), 120 + k * 25), 1)
    dot(i, 19, 13, rgba((255, 150, 50)), 6)
    dot(i, 19, 13, rgba((255, 210, 110)), 4)
    dot(i, 18, 12, WHITE, 1)


def frost_tide(i):
    c = (190, 230, 255)
    for k in range(6):
        a = k * math.pi / 3
        x1, y1 = 16 + math.cos(a) * 11, 16 + math.sin(a) * 11
        line(i, [(16, 16), (x1, y1)], rgba(c), 2)
        bx, by = 16 + math.cos(a) * 7, 16 + math.sin(a) * 7
        for s_ in (-1, 1):
            b = a + s_ * 0.7
            line(i, [(bx, by), (bx + math.cos(b) * 3, by + math.sin(b) * 3)], rgba(c))
    dot(i, 16, 16, WHITE, 1)


def blink(i):
    ring(i, (5, 18, 13, 26), rgba(SD), 1)
    dot(i, 9, 22, rgba(S), 2)
    for k in range(4):
        x = 12 + k * 3
        y = 20 - k * 3
        i.putpixel((x, y), rgba(S))
        i.putpixel((x + 1, y - 1), rgba(S, 160))
    dot(i, 23, 9, WHITE, 3)
    dot(i, 23, 9, rgba(S), 1)


def star_oath(i):
    ring(i, (6, 6, 26, 26), rgba(S), 2)
    pts = []
    for k in range(10):
        a = -math.pi / 2 + k * math.pi / 5
        r = 8 if k % 2 == 0 else 3.5
        pts.append((16 + math.cos(a) * r, 16 + math.sin(a) * r))
    put(i, pts, rgba(Y))
    dot(i, 16, 16, WHITE, 1)


def forge_stomp(i):
    a = TAG["ash"]
    put(i, [(11, 6), (17, 6), (17, 16), (22, 16), (22, 20), (10, 20)], rgba(shade(a, 0.6)))
    line(i, [(11, 6), (17, 6)], rgba(a))
    line(i, [(5, 23), (27, 23)], rgba(shade(a, 0.8)), 2)
    for k, x in enumerate((6, 12, 20, 26)):
        put(i, [(x - 2, 27), (x, 22 - (k % 2) * 2), (x + 2, 27)], rgba((255, 170, 70)))


def mist_veil(i):
    w = TAG["wind"]
    for k, y in enumerate((10, 16, 22)):
        pts = [(5 + t, y + math.sin((t + k * 3) / 3.0) * 2) for t in range(0, 23)]
        for j in range(len(pts) - 1):
            line(i, [pts[j], pts[j + 1]], rgba(w, 230 - k * 50), 2)
    dot(i, 22, 16, WHITE, 1)


def curse_mark(i):
    c = (190, 110, 230)
    pts = []
    for k in range(40):
        t = k / 39 * 3.2 * math.pi
        r = 1 + t * 1.0
        pts.append((16 + math.cos(t) * r, 16 + math.sin(t) * r))
    for j in range(len(pts) - 1):
        line(i, [pts[j], pts[j + 1]], rgba(c), 2)
    dot(i, 16, 16, WHITE, 1)


def lamp_charm(i):
    t = TAG["tide"]
    line(i, [(16, 4), (16, 8)], rgba(shade(t, 0.8)))
    put(i, [(11, 9), (21, 9), (22, 23), (10, 23)], rgba(shade(t, 0.55)))
    put(i, [(13, 11), (19, 11), (19, 21), (13, 21)], rgba((255, 224, 150)))
    dot(i, 16, 16, WHITE, 2)
    line(i, [(9, 24), (23, 24)], rgba(t), 2)
    ring(i, (4, 6, 28, 30), rgba((255, 224, 150), 90), 1)


def shield_rush(i):
    r = TAG["rock"]
    for k, y in enumerate((11, 16, 21)):
        line(i, [(4 + k, y), (11, y)], rgba(r, 200 - k * 50))
    put(i, [(13, 7), (25, 7), (25, 17), (19, 26), (13, 17)], rgba(shade(r, 0.7)))
    put(i, [(15, 9), (23, 9), (23, 16), (19, 23), (15, 16)], rgba(r))
    line(i, [(19, 9), (19, 22)], rgba(shade(r, 0.5)))
    dot(i, 21, 12, WHITE)


def archer_leap(i):
    b = TAG["bone"]
    put(i, [(5, 16), (13, 9), (13, 13), (24, 13), (24, 19), (13, 19), (13, 23)], rgba(b))
    for k, x in enumerate((26, 28)):
        line(i, [(x, 12 + k), (x, 20 - k)], rgba(b, 180 - k * 60))
    dot(i, 8, 16, WHITE)


# ---------------------------------------------------------------- 紋
def glyph(tag):
    def draw(i):
        c = TAG[tag]
        put(i, [(16, 3), (27, 16), (16, 29), (5, 16)], INK)
        put(i, [(16, 5), (25, 16), (16, 27), (7, 16)], rgba(shade(c, 0.7)))
        put(i, [(16, 5), (25, 16), (16, 16)], rgba(c))
        put(i, [(16, 5), (7, 16), (16, 16)], rgba(shade(c, 1.15)))
        line(i, [(16, 5), (16, 27)], rgba(shade(c, 0.45)))
        line(i, [(7, 16), (25, 16)], rgba(shade(c, 0.45)))
        i.putpixel((12, 10), WHITE)
        i.putpixel((13, 9), WHITE)
    return draw


ICONS = [
    (dust_charge, TAG["rock"], TAG["wind"]), (sea_breeze, TAG["wind"], TAG["tide"]), (steam_bell, TAG["tide"], TAG["ash"]),
    (cinder_volley, TAG["ash"], TAG["bone"]), (stone_aim, TAG["bone"], TAG["rock"]),
    (deep_pull, V, VD), (deep_mirror, V, VD), (deep_echo, V, VD), (deep_tithe, V, VD), (deep_maw, V, VD), (deep_oath, V, VD),
    (echo_strike, G, GD), (old_vigil, G, GD), (grave_bell, G, GD), (memory_edge, G, GD), (final_light, G, GD), (soul_thread, G, GD),
    (tree_hybrid, TAG["bone"], TAG["bone"]), (tree_deep, V, VD), (tree_past, G, GD),
]
GLYPHS = [glyph(t) for t in ("rock", "wind", "tide", "ash", "bone")]
# 25 番から: 職の新しい技 (Skill.iconIndex の 125 + n)
JOB_ICONS = [
    (thunder_bolt, S, SD), (star_reading, S, SD), (ember_orb, S, SD), (frost_tide, S, SD), (blink, S, SD), (star_oath, S, SD),
    (forge_stomp, TAG["ash"], TAG["ash"]), (mist_veil, TAG["wind"], TAG["wind"]), (curse_mark, TAG["tide"], TAG["tide"]),
    (lamp_charm, TAG["tide"], TAG["tide"]), (shield_rush, TAG["rock"], TAG["rock"]), (archer_leap, TAG["bone"], TAG["bone"]),
]


def sheet():
    img = Image.new("RGBA", (256, 192), (0, 0, 0, 0))
    for n, (fn, a, b) in enumerate(ICONS):
        m = medallion(a, b, disc=(38, 30, 54) if fn.__name__.startswith(("deep", "tree_deep")) else (30, 36, 48))
        fn(m)
        img.paste(m, ((n % 8) * 32, (n // 8) * 32))
    for k, fn in enumerate(GLYPHS):
        n = 20 + k
        g = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        fn(g)
        img.paste(g, ((n % 8) * 32, (n // 8) * 32))
    for k, (fn, a, b) in enumerate(JOB_ICONS):
        n = 25 + k
        m = medallion(a, b, disc=(28, 30, 58) if a == S else (30, 36, 48))
        fn(m)
        img.paste(m, ((n % 8) * 32, (n // 8) * 32))
    return img


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet().save(OUT)
    print("wrote", OUT)
