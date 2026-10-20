"""専用ブロックの見た目案 (3 案 x 8 種 x 2 状態) を 32x32 のドット絵で描き、レビュー用のシートに並べる。

使い方: python tools/mod_blocks_sheet.py   (PIL のみ。軽い)
出力: docs/review/mod_blocks/<id>_<a|b|c>_<state>.png (32x32 の素) と <id>_<a|b|c>.png (2 状態を横に並べた 5 倍)、sheet.png (全体)
共通の様式 = 巨像の遺物: 彫った石の枠に素材の象嵌、一本の光る筋 (巨像の「脈」)。
  A 鯨骨と深層岩 (青い光) / B 潮の鱗と貝 (青緑と珊瑚) / C 灰の鉄と刻印 (琥珀の光)
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "docs/review/mod_blocks"
S = 32

PAL = {
    "a": dict(o=(14, 18, 26), d=(33, 42, 56), m=(52, 65, 84), l=(82, 98, 120), h=(120, 138, 158),
              k1=(139, 131, 106), k2=(186, 177, 148), k3=(226, 220, 196), g=(70, 200, 224), g2=(190, 250, 255), gd=(24, 74, 92)),
    "b": dict(o=(16, 14, 38), d=(34, 38, 84), m=(52, 62, 120), l=(80, 96, 160), h=(124, 142, 200),
              k1=(150, 96, 176), k2=(206, 172, 226), k3=(246, 232, 250), g=(84, 226, 190), g2=(200, 255, 236), gd=(24, 84, 78),
              c1=(160, 50, 76), c2=(228, 104, 122)),
    "c": dict(o=(14, 12, 14), d=(36, 34, 40), m=(58, 55, 64), l=(88, 84, 94), h=(124, 118, 130),
              k1=(112, 66, 44), k2=(170, 104, 62), k3=(222, 160, 100), g=(255, 168, 56), g2=(255, 232, 150), gd=(104, 54, 16)),
}


class Cv:
    def __init__(self):
        self.im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        self.px = self.im.load()

    def p(self, x, y, c):
        if 0 <= x < S and 0 <= y < S and c is not None:
            self.px[x, y] = tuple(c) + (255,)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.p(x, y, c)

    def hl(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.p(x, y, c)

    def vl(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.p(x, y, c)


def hsh(x, y, s=0):
    v = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
    v = (v ^ (v >> 13)) * 1274126177
    return (v >> 8) & 255


def stone(c, x0, y0, x1, y1, P, s=1, cut=0):
    """彫った石の箱: 外線・斜面の明暗・粒。cut=角を落とす px 数"""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if cut and ((x - x0 + y - y0 < cut) or (x1 - x + y - y0 < cut)):
                continue
            col = P["m"]
            r = hsh(x, y, s)
            if r < 40:
                col = P["d"]
            elif r > 215:
                col = P["l"]
            c.p(x, y, col)
    for x in range(x0 + (cut or 0), x1 - (cut or 0) + 1):
        c.p(x, y0, P["l"])
        c.p(x, y1, P["d"])
    for y in range(y0 + (cut or 0), y1 + 1):
        c.p(x0, y, P["l"] if y < y1 else P["d"])
        c.p(x1, y, P["d"])


def outline(c, P):
    snap = [[c.px[x, y][3] for x in range(S)] for y in range(S)]
    for y in range(S):
        for x in range(S):
            if snap[y][x] == 0 and any(0 <= x + dx < S and 0 <= y + dy < S and snap[y + dy][x + dx]
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c.p(x, y, P["o"])


def shadow(c, x0, x1, y):
    for x in range(x0, x1 + 1):
        if c.px[x, y][3] == 0:
            c.p(x, y, (0, 0, 0))
            c.px[x, y] = (0, 0, 0, 70)


def glowdot(c, x, y, P, big=False):
    c.p(x, y, P["g2"])
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if c.px[x + dx, y + dy][3] == 0 or big:
            c.p(x + dx, y + dy, P["g"])


def scales(c, x0, y0, x1, y1, A, B):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if ((x + (y // 2 % 2) * 2) % 4 == 0 and y % 2 == 1):
                c.p(x, y, A)
            elif (x + (y // 2 % 2) * 2) % 4 in (1, 2) and y % 2 == 0:
                c.p(x, y, B)


# ------------------------------------------------------------------ 描画
def chest(L, st):
    P, c = PAL[L], Cv()
    op = st == "open"
    if not op:
        stone(c, 4, 15, 27, 28, P, 3)
        stone(c, 4, 8, 27, 15, P, 4, cut=3)
        c.hl(5, 26, 15, P["o"])
    else:
        stone(c, 6, 3, 25, 9, P, 5, cut=2)         # 起こした蓋 (奥)
        c.rect(7, 4, 24, 8, P["d"])
        stone(c, 4, 12, 27, 28, P, 3)
        c.rect(5, 13, 26, 17, P["o"])             # 口
        for y in range(13, 18):
            for x in range(6, 26):
                t = (17 - y) / 5
                if hsh(x, y, 9) < 90 + t * 120:
                    c.p(x, y, P["gd"] if y < 16 else P["g"])
        for x in (9, 14, 19, 22):
            c.p(x, 13 + hsh(x, 2) % 3, P["g2"])
    y0 = 15 if not op else 18
    # 素材ごとの象嵌
    if L == "a":    # 鯨骨のあばら
        for bx in (8, 22):
            for y in range(y0 if op else 9, 28):
                if op and y < 18:
                    continue
                c.rect(bx, y, bx + 2, y, P["k2"])
                c.p(bx, y, P["k3"]); c.p(bx + 2, y, P["k1"])
        if not op:
            for bx in (8, 22):
                c.rect(bx - 1, 9, bx + 3, 9, P["k1"])
    elif L == "b":  # 鱗の蓋 + 貝の錠
        if not op:
            scales(c, 5, 9, 26, 14, P["d"], P["l"])
        else:
            scales(c, 5, 19, 26, 27, P["d"], P["l"])
    else:           # 鉄の帯
        yy = 19 if not op else 22
        c.hl(4, 27, yy, P["k2"]); c.hl(4, 27, yy + 1, P["k1"]); c.hl(4, 27, yy - 1, P["k3"])
        for x in (6, 12, 19, 25):
            c.p(x, yy, P["k3"])
        if not op:
            c.hl(4, 27, 11, P["k2"]); c.hl(4, 27, 12, P["k1"])
    # 錠 (光る目)
    ly = 15 if not op else 21
    if L == "a":
        c.rect(14, ly, 17, ly + 3, P["k1"]); c.rect(15, ly + 1, 16, ly + 2, P["g"]); c.p(15, ly + 1, P["g2"])
    elif L == "b":
        for i, w in enumerate((1, 2, 3, 3)):
            c.hl(15 - w, 16 + w, ly + i, P["k2"] if i % 2 == 0 else P["k3"])
        c.p(15, ly + 3, P["g2"]); c.p(16, ly + 3, P["g"])
    else:
        c.rect(14, ly, 17, ly + 3, P["k1"])
        c.p(15, ly + 1, P["g2"]); c.p(16, ly + 2, P["g"]); c.p(14, ly + 3, P["g"]); c.p(17, ly + 3, P["g"])
    shadow(c, 5, 27, 29)
    outline(c, P)
    return c


def hearth(L, st):
    P, c = PAL[L], Cv()
    lit = st == "lit"
    # 台
    stone(c, 5, 23, 26, 28, P, 11)
    stone(c, 7, 19, 24, 23, P, 12)
    if L == "a":    # あばらの輪
        for bx in (6, 10, 21, 25):
            c.vl(bx, 11, 22, P["k2"]); c.vl(bx + 1, 12, 22, P["k1"]) if bx < 15 else c.vl(bx - 1, 12, 22, P["k1"])
        c.hl(7, 10, 10, P["k3"]); c.hl(21, 24, 10, P["k3"]); c.hl(10, 21, 9, P["k2"])
        c.p(6, 11, P["k3"]); c.p(25, 11, P["k3"])
    elif L == "b":  # 貝の杯
        for i, (x0, x1) in enumerate(((7, 24), (8, 23), (9, 22), (10, 21))):
            c.hl(x0, x1, 15 + i, P["k2"] if i % 2 else P["k3"])
        for x in range(8, 24, 3):
            c.vl(x, 13, 18, P["k1"])
        c.hl(6, 25, 13, P["k3"]); c.hl(6, 25, 14, P["k2"]); c.p(6, 13, P["k1"]); c.p(25, 13, P["k1"])
        c.rect(10, 19, 21, 19, P["k1"])
    else:           # 鉄の三脚と皿
        c.hl(7, 24, 15, P["k3"]); c.hl(7, 24, 16, P["k2"]); c.hl(8, 23, 17, P["k1"]); c.hl(10, 21, 18, P["k1"])
        for lx in (8, 23):
            c.vl(lx, 17, 22, P["k2"]); c.vl(lx + 1, 17, 22, P["k1"])
        c.p(7, 15, P["k2"]); c.p(24, 15, P["k2"])
    if lit:
        fy = 14 if L != "a" else 15
        for yy, (x0, x1) in enumerate(((15, 16), (14, 17), (13, 18), (13, 18), (14, 17), (14, 17))):
            y = fy - 5 + yy - 0
            for x in range(x0, x1 + 1):
                c.p(x, y + 2, P["g"] if (x + y) % 5 else P["g2"])
        c.p(15, fy - 5, P["g2"]); c.p(16, fy - 4, P["g2"]); c.p(15, fy - 6, P["g"])
        c.hl(11, 20, fy + 3, P["g"]) if L != "a" else None
        for x, y in ((10, 11), (21, 9), (12, 6), (19, 5)):
            c.p(x, y, P["g"])
        c.p(15, fy - 3, P["g2"]); c.p(16, fy - 3, P["g2"]); c.p(15, fy - 2, P["g2"])
    else:
        for x in (13, 15, 17):
            c.p(x, 17 if L == "a" else 16, P["gd"])
        c.hl(12, 19, 18 if L == "a" else 17, P["d"])
        c.p(15, 17, P["g"]) if L == "c" else None
    shadow(c, 6, 27, 29)
    outline(c, P)
    return c


def lever(L, st):
    P, c = PAL[L], Cv()
    on = st == "on"
    stone(c, 5, 21, 26, 28, P, 21)          # 台座
    c.hl(8, 23, 21, P["h"])
    stone(c, 12, 17, 19, 21, P, 22)
    c.p(15, 19, P["k3"]); c.p(16, 19, P["k2"])
    # 柄 (斜め): 左= 切, 右= 入
    pts = [(15 + (i if on else -i) * 1 // 1, 18 - i) for i in range(0, 9)] if False else None
    dx = 1 if on else -1
    x, y = 15, 18
    for i in range(10):
        xx = x + dx * (i * 6 // 10 + 0)
        yy = y - i
        k = P["k2"] if L != "c" else P["k2"]
        c.p(xx, yy, P["k3"]); c.p(xx + 1, yy, k); c.p(xx + 2 if False else xx + 1, yy, k)
        c.p(xx, yy, P["k3"])
    tx = x + dx * (9 * 6 // 10)
    ty = y - 9
    if L == "a":
        c.rect(tx - 1, ty - 3, tx + 2, ty, P["k2"]); c.rect(tx, ty - 2, tx + 1, ty - 1, P["g"] if on else P["gd"])
        c.p(tx, ty - 2, P["g2"] if on else P["g"])
    elif L == "b":
        c.rect(tx - 1, ty - 3, tx + 2, ty, P["k3"]); c.p(tx - 1, ty - 3, P["k2"]); c.p(tx + 2, ty - 3, P["k2"]); c.p(tx + 2, ty, P["k2"])
        c.rect(tx, ty - 2, tx + 1, ty - 1, P["g"] if on else P["c1"]); c.p(tx, ty - 2, P["g2"] if on else P["c2"])
    else:
        c.rect(tx - 1, ty - 2, tx + 2, ty, P["k2"]); c.hl(tx - 1, tx + 2, ty - 3, P["k3"])
        c.rect(tx, ty - 1, tx + 1, ty, P["g"] if on else P["gd"]); c.p(tx, ty - 1, P["g2"] if on else P["g"])
    # 台座の溝の光
    c.hl(9, 22, 25, P["g"] if on else P["gd"]); c.hl(9, 22, 26, P["d"])
    if on:
        c.p(9, 25, P["g2"]); c.p(22, 25, P["g2"])
    shadow(c, 6, 27, 29)
    outline(c, P)
    return c


def button(L, st):
    P, c = PAL[L], Cv()
    pr = st == "pressed"
    stone(c, 5, 5, 26, 26, P, 31, cut=3)          # 壁の板
    stone(c, 8, 8, 23, 23, P, 32, cut=2) if False else None
    c.rect(9, 9, 22, 22, P["d"])
    c.hl(9, 22, 9, P["o"]); c.vl(9, 9, 22, P["o"])
    # 押し込み部
    inset = 2 if pr else 0
    lo, hi = 11 + inset, 20 - inset
    if L == "a":
        c.rect(lo, lo, hi, hi, P["k2"]); c.hl(lo, hi, lo, P["k3"]); c.vl(lo, lo, hi, P["k3"]); c.hl(lo, hi, hi, P["k1"]); c.vl(hi, lo, hi, P["k1"])
    elif L == "b":
        for i in range(0, 5):
            c.hl(lo + (1 if i < 1 else 0), hi - (1 if i < 1 else 0), lo + i, P["k3"] if i % 2 == 0 else P["k2"])
        c.rect(lo, lo + 5, hi, hi, P["k2"]); c.hl(lo, hi, hi, P["k1"])
    else:
        c.rect(lo, lo, hi, hi, P["k1"]); c.hl(lo, hi, lo, P["k3"]); c.vl(lo, lo, hi, P["k2"]); c.hl(lo + 1, hi, hi, P["o"])
        for x, y in ((lo + 1, lo + 1), (hi - 1, lo + 1), (lo + 1, hi - 1), (hi - 1, hi - 1)):
            c.p(x, y, P["k3"])
    mid = 15
    gl = P["g"] if pr else P["gd"]
    if L == "a":
        c.rect(mid, mid, mid + 1, mid + 1, gl); c.p(mid, mid, P["g2"] if pr else P["g"])
    elif L == "b":
        c.rect(mid, mid, mid + 1, mid + 1, gl if pr else P["c1"]); c.p(mid, mid, P["g2"] if pr else P["c2"])
    else:
        c.p(mid, mid - 1, gl); c.p(mid + 1, mid + 1, gl); c.p(mid - 1 + 1, mid + 2 - 1, gl); c.p(mid, mid, P["g2"] if pr else P["g"])
    # 枠の光る角
    for x, y in ((6, 6), (25, 6), (6, 25), (25, 25)):
        pass
    for x, y in ((7, 8), (24, 8), (7, 23), (24, 23)):
        c.p(x, y, P["g"] if pr else P["gd"])
    outline(c, P)
    return c


def plate(L, st):
    P, c = PAL[L], Cv()
    pr = st == "pressed"
    top = 19 if pr else 17
    # 周りの床 (影)
    c.rect(2, 24, 29, 27, P["d"]); c.hl(2, 29, 24, P["m"])
    c.hl(2, 29, 27, P["o"])
    # 板の上面 (斜め見下ろし) と前の厚み
    for y in range(top - 5, top + 1):
        inset = (top - y) // 2
        for x in range(5 + inset, 27 - inset):
            r = hsh(x, y, 41)
            c.p(x, y, P["l"] if r > 210 else (P["m"] if r > 45 else P["d"]))
    th = 3 if not pr else 1
    for y in range(top + 1, top + 1 + th):
        c.hl(5, 26, y, P["d"]); c.p(5, y, P["m"])
    c.hl(5, 26, top + th, P["o"])
    # 上面の象嵌
    cy = top - 2
    if L == "a":
        c.hl(9, 22, cy, P["k2"]); c.hl(10, 21, cy - 1, P["k3"]); c.hl(10, 21, cy + 1, P["k1"])
        c.hl(14, 17, cy, P["g"] if pr else P["gd"])
    elif L == "b":
        for i, (x0, x1) in enumerate(((13, 18), (11, 20), (10, 21))):
            c.hl(x0, x1, cy - 1 + i, P["k3"] if i % 2 == 0 else P["k2"])
        c.hl(14, 17, cy + 1, P["g"] if pr else P["c1"])
    else:
        c.hl(9, 22, cy, P["k1"]); c.hl(9, 22, cy - 1, P["k2"])
        for x in (11, 15, 19):
            c.p(x, cy, P["g"] if pr else P["gd"])
    # 縁の光の筋
    c.hl(7, 24, top - 4, P["g"] if pr else P["gd"]) if False else None
    for x in (7, 24):
        c.p(x, top - 3 if x == 7 else top - 3, P["g"] if pr else P["gd"])
    if pr:
        for x in (8, 12, 19, 23):
            c.p(x, top + th + 1 + (x % 2), P["g"])
        c.hl(6, 25, top + th + 1, P["gd"])
    outline(c, P)
    return c


def tablet(L, st):
    P, c = PAL[L], Cv()
    lit = st == "lit"
    # 台座 + 板
    stone(c, 7, 25, 24, 28, P, 51)
    stone(c, 9, 6, 22, 25, P, 52, cut=4 if L != "c" else 1)
    if L == "a":    # 肋骨の飾り縁
        c.vl(9, 8, 24, P["k2"]); c.vl(22, 8, 24, P["k2"]); c.vl(10, 9, 24, P["k1"]); c.vl(21, 9, 24, P["k1"])
        c.hl(13, 18, 5, P["k3"]); c.hl(12, 19, 6, P["k2"])
    elif L == "b":  # 貝のかんむり
        for i, (x0, x1) in enumerate(((13, 18), (11, 20), (10, 21))):
            c.hl(x0, x1, 3 + i, P["k3"] if i % 2 == 0 else P["k2"])
        for x in (12, 15, 16, 19):
            c.p(x, 4, P["k1"])
        scales(c, 10, 21, 21, 24, P["d"], P["l"])
    else:           # 鉄の縁と鋲
        c.hl(9, 22, 6, P["k2"]); c.hl(9, 22, 7, P["k1"]); c.vl(9, 6, 24, P["k1"]); c.vl(22, 6, 24, P["k1"])
        for x, y in ((10, 8), (21, 8), (10, 23), (21, 23)):
            c.p(x, y, P["k3"])
    # 刻んだ文字行 (光る)
    ink = P["g"] if lit else P["gd"]
    for i, y in enumerate((10, 13, 16, 19)):
        w = (8, 6, 8, 5)[i]
        x = 12
        while x < 12 + w:
            run = 2 + hsh(x, y, 7) % 2
            c.hl(x, min(x + run - 1, 19), y, ink)
            x += run + 1
    if lit:
        c.p(15, 10, P["g2"]); c.p(14, 16, P["g2"]); c.p(17, 19, P["g2"])
        for x, y in ((8, 12), (23, 15), (8, 20)):
            c.p(x, y, P["g"]) if c.px[x, y][3] == 0 else None
    shadow(c, 8, 25, 29)
    outline(c, P)
    return c


def seal(L, st):
    P, c = PAL[L], Cv()
    cl = st == "cleared"
    cx, cy, R = 15.5, 15.5, 12
    for y in range(S):
        for x in range(S):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= R:
                r = hsh(x, y, 61)
                col = P["m"] if r > 40 else P["d"]
                if r > 218:
                    col = P["l"]
                if d > R - 1.4:
                    col = P["l"] if (x + y) < 31 else P["d"]
                c.p(x, y, col)
    # 内輪
    for y in range(S):
        for x in range(S):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 7.2 <= d <= 8.4:
                c.p(x, y, P["k2"] if (x + y) % 3 else P["k1"])
            elif d < 7.2:
                c.p(x, y, P["d"] if not cl else P["gd"])
    # 三つの刻み (三本の肋筋)
    if not cl:
        # 閉じた目 + 鎖の帯
        c.hl(9, 22, 15, P["o"]); c.hl(10, 21, 16, P["k1"]); c.hl(12, 19, 14, P["k1"])
        for x in (9, 12, 19, 22):
            c.p(x, 17, P["k1"])
        c.rect(15, 5, 16, 26, P["k1"]) if False else None
        if L == "a":
            c.vl(15, 8, 22, P["k2"]); c.vl(16, 8, 22, P["k1"])
        elif L == "b":
            for y in range(8, 23, 2):
                c.hl(14, 17, y, P["k2"]); c.hl(15, 16, y + 1, P["k1"])
        else:
            c.hl(8, 23, 12, P["k1"]); c.hl(8, 23, 19, P["k1"]); c.vl(15, 8, 23, P["k2"])
        for x, y in ((15, 15), (16, 15)):
            c.p(x, y, P["gd"])
    else:
        # 開いた目 + 放射の光
        for y in range(11, 21):
            for x in range(9, 23):
                d = abs(x - cx) / 7 + abs(y - cy) / 5
                if d <= 1:
                    c.p(x, y, P["g"] if d > 0.35 else P["g2"])
        c.rect(15, 14, 16, 17, P["o"]); c.p(15, 14, P["gd"])
        for x, y in ((15, 6), (16, 6), (15, 25), (16, 25), (6, 15), (6, 16), (25, 15), (25, 16)):
            c.p(x, y, P["g"])
        for x, y in ((9, 9), (22, 9), (9, 22), (22, 22)):
            c.p(x, y, P["g2"])
    # 縁の飾り: 4 つの突起
    for x, y in ((15, 2), (16, 2), (15, 29), (16, 29), (2, 15), (2, 16), (29, 15), (29, 16)):
        c.p(x, y, P["k2"] if L != "b" else P["k3"])
    for x, y in ((15, 3), (16, 3), (15, 28), (16, 28), (3, 15), (3, 16), (28, 15), (28, 16)):
        c.p(x, y, P["k1"])
    outline(c, P)
    return c


def bell(L, st):
    P, c = PAL[L], Cv()
    ring = st == "ring"
    # 枠 (門)
    c.rect(3, 4, 5, 28, P["m"]); c.rect(26, 4, 28, 28, P["m"]); c.rect(3, 4, 28, 6, P["m"])
    for y in range(4, 29):
        for x in list(range(3, 6)) + list(range(26, 29)):
            r = hsh(x, y, 71)
            c.p(x, y, P["d"] if r < 50 else (P["l"] if r > 215 else P["m"]))
    for x in range(3, 29):
        for y in range(4, 7):
            r = hsh(x, y, 72)
            c.p(x, y, P["d"] if r < 50 else (P["l"] if r > 215 else P["m"]))
    c.hl(3, 28, 4, P["l"]); c.vl(3, 4, 28, P["l"]); c.hl(6, 25, 7, P["o"])
    c.hl(3, 28, 28, P["d"])
    sh = 1 if ring else 0
    bx = 16 + sh
    # 吊り手
    c.vl(bx - 1, 7, 9, P["k1"]); c.vl(bx, 7, 9, P["k2"])
    # 鐘の体
    prof = [(11, 14), (10, 15), (9, 16), (9, 17), (8, 18), (8, 19), (7, 20), (7, 21), (6, 22), (6, 23), (5, 24)]
    pr = [(2, 1), (3, 2), (3, 3), (4, 4), (4, 5), (5, 5), (5, 6), (6, 6), (6, 7), (7, 7), (7, 8)]
    for i, (hw, _) in enumerate(((3, 0), (4, 0), (4, 0), (5, 0), (5, 0), (5, 0), (6, 0), (6, 0), (7, 0), (7, 0), (8, 0))):
        y = 10 + i
        for x in range(bx - hw, bx + hw):
            r = hsh(x, y, 73)
            col = P["k2"]
            if x < bx - hw + 2:
                col = P["k3"]
            elif x >= bx + hw - 2:
                col = P["k1"]
            c.p(x, y, col)
    if L == "a":
        for y in (12, 15, 18):
            c.hl(bx - 4 + (y - 12) // 2, bx + 3 - (y - 12) // 2 + 0, y, P["k1"]) if False else None
        c.vl(bx - 1, 11, 20, P["k1"]); c.vl(bx + 1, 11, 20, P["k1"])
    elif L == "b":
        for y in range(11, 21, 2):
            c.hl(bx - 5, bx + 4, y, P["k1"]) if y > 12 else None
        c.hl(bx - 8, bx + 7, 20, P["c2"]); c.hl(bx - 8, bx + 7, 21, P["c1"])
    else:
        c.hl(bx - 7, bx + 6, 14, P["k1"]); c.hl(bx - 7, bx + 6, 15, P["o"])
        c.hl(bx - 8, bx + 7, 20, P["k3"]); c.hl(bx - 8, bx + 7, 21, P["k1"])
    # 舌
    c.vl(bx, 22, 24, P["k1"]); c.p(bx, 25, P["g"] if ring else P["gd"]); c.p(bx - 1, 25, P["g"] if ring else P["gd"])
    c.hl(bx - 8, bx + 7, 22, P["k1"]) if L == "a" else None
    if ring:
        for dx, ys in ((-11, (14, 17)), (-13, (12, 19)), (10, (14, 17)), (12, (12, 19))):
            for y in range(ys[0], ys[1] + 1):
                c.p(bx + dx + (1 if dx < 0 else 0), y, P["g"] if abs(dx) < 12 else P["gd"])
        c.p(bx, 27, P["g2"]); c.p(bx - 3, 26, P["g"]); c.p(bx + 3, 26, P["g"])
    shadow(c, 4, 27, 29)
    outline(c, P)
    return c


BLOCKS = [
    ("chest", "キャッシュの箱", chest, ("closed", "open")),
    ("hearth", "休み火", hearth, ("cold", "lit")),
    ("lever", "仕掛けのレバー", lever, ("off", "on")),
    ("button", "仕掛けのボタン", button, ("up", "pressed")),
    ("plate", "感圧板", plate, ("up", "pressed")),
    ("tablet", "語りの碑", tablet, ("dim", "lit")),
    ("seal", "試練の封印", seal, ("sealed", "cleared")),
    ("bell", "鐘", bell, ("still", "ring")),
]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for bid, _, fn, states in BLOCKS:
        row = []
        for L in "abc":
            ims = []
            for st in states:
                cv = fn(L, st)
                cv.im.save(OUT / f"{bid}_{L}_{st}.png")
                ims.append(cv.im)
            pair = Image.new("RGBA", (S * 2 + 4, S), (0, 0, 0, 0))
            pair.paste(ims[0], (0, 0)); pair.paste(ims[1], (S + 4, 0))
            bg = Image.new("RGBA", pair.size, (22, 28, 36, 255))
            bg.alpha_composite(pair)
            bg.resize((pair.width * 5, pair.height * 5), Image.NEAREST).save(OUT / f"{bid}_{L}.png")
            row.append(bg)
        rows.append(row)
    sc, gap = 3, 8
    cw = rows[0][0].width * sc // 5
    sheet = Image.new("RGB", (cw * 3 + gap * 4, (S * sc + gap) * len(rows) + gap), (16, 20, 26))
    for r, row in enumerate(rows):
        for i, im in enumerate(row):
            sheet.paste(im.resize((cw, S * sc), Image.NEAREST).convert("RGB"), (gap + i * (cw + gap), gap + r * (S * sc + gap)))
    sheet.save(OUT / "sheet.png")


if __name__ == "__main__":
    main()
