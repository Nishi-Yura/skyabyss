"""Banner and four titan scenes (distant silhouettes + traits only)."""
import numpy as np
from pix import *

TW, TH = 96, 54


def sparkle(a, pts, col):
    for x, y in pts:
        if 0 <= x < a.shape[1] and 0 <= y < a.shape[0]:
            a[y, x] = col


WHALE_PAL = dict(body=H("4d5568"), lite=H("6b7590"), dark=H("363c4c"), mortar=H("2c3140"), belly=H("d2c8a8"), belly_d=H("a89e80"),
                 moss=H("5f8a42"), ol=INK, glow=H("66ecff"), glow_l=H("d6fbff"), ruin=H("7d7a88"), ruin_d=H("5a5766"), eye=H("ffc65a"), bricks=True)
WHALE_HAZE = dict(body=H("7f95b8"), lite=H("9fb2cf"), dark=H("6a82a8"), mortar=H("6a82a8"), belly=H("c9d5e4"), belly_d=H("aebfd4"),
                  moss=H("8fb09c"), ol=H("5d7499"), glow=H("a8f0f4"), glow_l=H("e0fbff"), ruin=H("9aa9c2"), ruin_d=H("8294b2"), eye=H("e8d8a0"), bricks=False)


def whale(a, x0, y0, L, pal, ruins=True, chain=True):
    """Stone whale seen from the side, facing right: long slate-brick body, beige belly,
    trailing pectoral fin, tail flukes, and the old ruins (glowing arches, pillar, tower) on its back."""
    h, w = a.shape[:2]
    ts = [0, .12, .35, .6, .85, 1.0]
    rs = [.012, .03, .075, .088, .075, .045]
    n = 100
    path = bez3((x0, y0 + .03 * L), (x0 + .3 * L, y0 + .12 * L), (x0 + .65 * L, y0 + .1 * L), (x0 + L, y0 - .02 * L), n)
    rad = [max(1.0, float(np.interp(i / n, ts, rs)) * L) for i in range(n + 1)]
    m = tube(w, h, path, rad)
    tx, ty = path[0]
    fl = mask(w, h, lambda d: d.polygon([(tx + .02 * L, ty), (tx - .06 * L, ty - .11 * L), (tx - .02 * L, ty - .005 * L), (tx - .07 * L, ty + .08 * L), (tx + .02 * L, ty + .02 * L)], fill=255))
    bx, by = path[60][0], path[60][1] + rad[60] * .55
    fin = mask(w, h, lambda d: d.polygon([(bx + .02 * L, by), (bx - .03 * L, by - .01 * L), (bx - .15 * L, by + .15 * L), (bx - .03 * L, by + .09 * L)], fill=255))
    full = m | fl | fin
    outline(a, full, pal["ol"])
    a[full] = pal["body"]
    yy = np.mgrid[0:h, 0:w][0]
    cols = np.where(m.any(axis=0))[0]
    tops = {}
    for x in cols:
        ys = np.where(m[:, x])[0]
        top, bot = ys.min(), ys.max()
        tops[x] = top
        thr = top + 0.6 * (bot - top)
        for y in ys:
            if y >= thr and x > x0 + .07 * L:
                a[y, x] = pal["belly"] if y > thr + 0.6 or (x % 2 == 0) else pal["belly_d"]
        if x0 + .22 * L < x < x0 + .85 * L:
            a[top, x] = pal["moss"]
            if (x % 3) != 0:
                a[top + 1, x] = pal["moss"]
    if pal["bricks"]:
        for y in range(h):
            for x in cols:
                if m[y, x] and tuple(a[y, x]) == pal["body"] and y % 3 == 0 and ((x + (y // 3) * 3) % 7 != 0):
                    a[y, x] = pal["mortar"]
    bevel(a, m, pal["lite"], pal["dark"])
    a[fl | fin] = pal["body"]
    bevel(a, fl | fin, pal["lite"], pal["dark"])
    # eye + mouth line
    ex, ey = path[88]
    a[int(ey + .01 * L), int(ex)] = pal["eye"]
    a[int(ey + .01 * L), int(ex) - 1] = pal["eye"]
    mx, my = path[100]
    a[int(my + .35 * rad[100]), int(mx - .16 * L):int(mx - .01 * L)] = pal["dark"] if pal["bricks"] else pal["ol"]
    if ruins:
        def top_at(t):
            x = int(path[int(t * 100)][0])
            return x, tops.get(x, int(path[int(t * 100)][1]))
        # two arches
        for t, wd in ((.44, .14), (.6, .115)):
            x, ty_ = top_at(t)
            wd = int(wd * L); r_ = wd // 2
            arc = mask(w, h, lambda d, x=x, ty_=ty_, r_=r_: (d.pieslice((x - r_, ty_ - r_ - 1, x + r_, ty_ + r_ - 1), 180, 360, fill=255),
                                                              d.pieslice((x - r_ + 2, ty_ - r_ + 1, x + r_ - 2, ty_ + r_ - 3), 180, 360, fill=0)))
            arc[ty_ - 0:, :] = False
            outline(a, arc, pal["ol"])
            a[arc] = pal["ruin"]
            glow = arc & (((np.mgrid[0:h, 0:w][1]) + yy) % 3 == 0)
            a[glow] = pal["glow"]
            a[arc & (yy == ty_ - r_ - 1)] = pal["glow_l"] if pal["bricks"] else pal["glow"]
        # pillar and tower
        x, ty_ = top_at(.76)
        pw, ph = max(2, int(.03 * L)), int(.09 * L)
        pil = mask(w, h, lambda d: d.rectangle((x - pw // 2, ty_ - ph, x + pw // 2, ty_), fill=255))
        outline(a, pil & ~m, pal["ol"]); a[pil] = pal["ruin"]; a[pil & (np.mgrid[0:h, 0:w][1] > x)] = pal["ruin_d"]
        x, ty_ = top_at(.28)
        tw, th = max(4, int(.065 * L)), int(.1 * L)
        tw_ = mask(w, h, lambda d: (d.rectangle((x - tw // 2, ty_ - th, x + tw // 2, ty_), fill=255), d.rectangle((x - tw // 2 - 1, ty_ - th - 2, x + tw // 2 + 1, ty_ - th), fill=255)))
        outline(a, tw_ & ~m, pal["ol"]); a[tw_] = pal["ruin"]; a[tw_ & (np.mgrid[0:h, 0:w][1] > x)] = pal["ruin_d"]
        a[ty_ - th // 2 - 1:ty_ - th // 2 + 1, x:x + 1] = pal["glow"]
    if chain:
        cx = int(path[30][0]); cy = int(path[30][1] + rad[30])
        for k in range(int(.14 * L)):
            if cy + k < h:
                a[cy + k, cx] = H("9aa4b4") if k % 2 == 0 else pal["ol"]
                if k % 4 == 0 and cx + 1 < w:
                    a[cy + k, cx + 1] = H("9aa4b4")
    return full


def scene_whale():
    a = canvas(TW, TH)
    vgrad(a, [(14, SKY1), (26, SKY2), (38, SKY3), (TH, SKY4)])
    for i in range(0, 90, 7):
        a[(i * 5) % 14, (i * 13) % TW] = SKY3
    cloud(a, 78, 12, 26, 4, SKY4, SKY3)
    whale(a, 11, 14, 82, WHALE_PAL)
    cloud(a, 20, 47, 40, 7, CLOUD, CLOUD_S, CLOUD_D)
    cloud(a, 76, 51, 44, 7, CLOUD, CLOUD_S, CLOUD_D)
    cloud(a, 50, 40, 26, 4, CLOUD, CLOUD_S)
    return a


TS = H("3f8a82"); TS_L = H("6cc0b0"); TS_D = H("276660"); TS_DD = H("1b4a48")
SEA1 = H("3a86c0"); SEA2 = H("2c6aa8"); SEA3 = H("1f4e88"); SEA4 = H("163a6a")


def scene_tide():
    a = canvas(TW, TH)
    vgrad(a, [(10, H("6fa8d0")), (20, H("a8d0e0")), (28, H("f0cfa8")), (29, H("f6dcb4"))])
    sea_y = 29
    vgrad(a, [(34, SEA1), (42, SEA2), (48, SEA3), (TH, SEA4)], y0=sea_y)
    cloud(a, 22, 12, 24, 4, H("fbe9d0"), H("e8c8b0"))
    cloud(a, 82, 9, 18, 3, H("fbe9d0"), H("e8c8b0"))
    a[27:30, 70:80] = H("f8e8c0")
    cx, cy = 54, 38
    # shell dome, partly sunk in the sea
    dome = mask(TW, TH, lambda d: d.pieslice((cx - 31, cy - 27, cx + 31, cy + 27), 180, 360, fill=255))
    # flippers: big front paddle (left, forward) and rear paddle (right)
    ff = mask(TW, TH, lambda d: d.polygon([(30, 36), (24, 40), (12, 46), (6, 52), (16, 51), (30, 46), (38, 40)], fill=255))
    rf = mask(TW, TH, lambda d: d.polygon([(72, 38), (82, 44), (90, 50), (84, 52), (74, 47), (68, 42)], fill=255))
    ff2 = mask(TW, TH, lambda d: d.polygon([(42, 40), (40, 47), (34, 52), (44, 52), (50, 46), (50, 40)], fill=255))
    # head on a neck, facing left
    neck = mask(TW, TH, lambda d: d.polygon([(26, 28), (32, 26), (36, 36), (26, 38)], fill=255))
    head = mask(TW, TH, lambda d: (d.ellipse((12, 20, 28, 33), fill=255), d.polygon([(12, 27), (7, 30), (13, 33)], fill=255)))
    full = dome | ff | rf | ff2 | neck | head
    outline(a, full)
    a[ff | rf | ff2] = TS_D
    a[neck] = TS_D
    a[head] = H("e2d8b0")
    a[dome] = TS
    yy, xx = np.mgrid[0:TH, 0:TW]
    # concentric brick rings on the shell
    for r in range(8, 30, 5):
        ring = dome & (np.hypot((xx - cx) / 1.0, (yy - cy) * 1.15) >= r) & (np.hypot((xx - cx), (yy - cy) * 1.15) < r + 1)
        a[ring] = TS_D
    for ang in range(-80, 81, 20):
        t = np.radians(ang)
        for r in range(2, 30):
            x = int(cx + np.sin(t) * r * 1.05)
            y = int(cy - np.cos(t) * r)
            if 0 <= y < TH and dome[y, x] and (r // 5) % 2 == 0:
                a[y, x] = TS_D
    bevel(a, dome, TS_L, TS_DD)
    bevel(a, ff | rf | ff2 | neck, TS, TS_DD)
    bevel(a, head, H("f6f0d0"), H("a89e78"))
    # eye + beak line
    a[25, 17] = INK; a[25, 18] = INK; a[24, 17] = WHITE
    a[31, 9:15] = H("a89e78")
    # coral patches on the shell
    for (px, py, c) in ((36, 17, H("e8607e")), (68, 21, H("f0883a")), (30, 24, H("a864c8")), (60, 24, H("e8607e")), (42, 28, H("f0883a"))):
        a[py:py + 2, px:px + 3] = c
        a[py + 1, px + 2] = tuple(int(v * .7) for v in c[:3]) + (255,)
    # stone lantern pavilion with a glowing top
    px, py = 52, 11
    pav = mask(TW, TH, lambda d: (d.rectangle((px - 5, py, px + 5, py + 9), fill=255), d.rectangle((px - 6, py - 3, px + 6, py), fill=255)))
    outline(a, pav & ~dome)
    a[pav] = H("8a8a94")
    a[py + 3:py + 9, px - 2:px + 3] = H("2a2a34")
    a[py - 3:py, px - 6:px + 7] = H("aeaeb8")
    a[py - 4, px - 2:px + 3] = H("8cf0ff"); a[py - 5, px - 1:px + 2] = WHITE
    a[py:py + 10, px + 3:px + 6][pav[py:py + 10, px + 3:px + 6]] = H("62626c")
    # water: dither over the lower flippers, ripples, foam
    for y in range(36, TH):
        for x in range(TW):
            if (ff | rf | ff2)[y, x] and y >= 43 and (x + y) % 2 == 0:
                a[y, x] = SEA3
    for (rx, ry, rw) in ((20, 44, 14), (62, 43, 12), (46, 49, 20), (82, 47, 12), (10, 49, 8)):
        a[ry, rx - rw // 2:rx + rw // 2] = H("9ad0f0")
        a[ry + 1, rx - rw // 2 + 2:rx + rw // 2 - 2] = H("5a98d0")
    a[36, 28:72][dome[36, 28:72]] = TS_DD
    return a


def scene_storm():
    a = canvas(TW, TH)
    vgrad(a, [(12, H("141a38")), (24, H("232e5a")), (36, H("34457a")), (TH, H("4a5f90"))])
    for (x, y, w_) in ((16, 10, 40), (80, 8, 36), (50, 5, 50)):
        cloud(a, x, y, w_, 7, H("2c3562"), H("1e2548"), H("1e2548"))
    cloud(a, 14, 50, 36, 7, H("56688f"), H("46557c"), H("38456a"))
    cloud(a, 84, 51, 40, 7, H("56688f"), H("46557c"), H("38456a"))
    SL = H("4a526a"); SLL = H("6e7a96"); SLD = H("2e3448"); FE = H("dfe3ee"); FE_D = H("a8b0c4")
    cx, cy = 48, 30

    def wing(sign):
        pts = [(cx, cy - 6), (cx + sign * 14, cy - 17), (cx + sign * 32, cy - 21), (cx + sign * 46, cy - 16), (cx + sign * 47, cy - 8)]
        for k in range(8):
            xk = cx + sign * (46 - k * 5.2)
            pts += [(xk, cy + 1 - (3 if k % 2 else 0) + k * 0.5), (xk - sign * 2.5, cy + 6 + k * 0.4 - (0 if k % 2 else 2))]
        pts += [(cx + sign * 4, cy + 6), (cx, cy + 4)]
        return mask(TW, TH, lambda d: d.polygon(pts, fill=255))
    wl, wr = wing(-1), wing(1)
    body = mask(TW, TH, lambda d: (d.ellipse((cx - 8, cy - 8, cx + 8, cy + 12), fill=255),
                                   d.polygon([(cx - 4, cy + 9), (cx + 4, cy + 9), (cx + 3, cy + 20), (cx, cy + 24), (cx - 3, cy + 20)], fill=255)))
    head = mask(TW, TH, lambda d: d.ellipse((cx - 5, cy - 16, cx + 5, cy - 6), fill=255))
    # crest: orange flame fan
    crest = mask(TW, TH, lambda d: d.polygon([(cx - 3, cy - 14), (cx - 9, cy - 22), (cx - 5, cy - 20), (cx - 5, cy - 26), (cx - 2, cy - 21), (cx, cy - 28), (cx + 2, cy - 21), (cx + 5, cy - 26), (cx + 5, cy - 20), (cx + 9, cy - 22), (cx + 3, cy - 14)], fill=255))
    beak = mask(TW, TH, lambda d: d.polygon([(cx - 3, cy - 9), (cx + 3, cy - 9), (cx, cy - 1)], fill=255))
    wings = wl | wr
    full = wings | body | head | crest | beak
    outline(a, full)
    a[wings] = SL
    a[body | head] = SLL
    band(a, body, SL, cy + 4)
    a[crest] = H("c8643a")
    a[crest & (np.mgrid[0:TH, 0:TW][0] < cy - 21)] = H("ffd070")
    a[beak] = H("e0cc9c")
    a[cy - 11, cx - 3] = H("ffd070"); a[cy - 11, cx + 2] = H("ffd070")
    # white feather tips along the trailing edge
    tipm = wings & ~shift(wings, 0, -2)
    a[tipm] = FE
    a[wings & ~shift(wings, 0, -3) & ~tipm] = FE_D
    bevel(a, wings, SLL, SLD)
    bevel(a, body | head, H("9aa8c8"), SLD)
    # gold lightning veins + orange glowing discs on each wing
    for sign in (-1, 1):
        gx, gy = cx + sign * 7, cy - 6
        for step_ in range(13):
            x = gx + sign * step_ * 3
            y = gy - 6 * np.sin(step_ / 12 * np.pi / 1.2) + (2 if step_ % 2 else 0)
            for k in range(2):
                xx_, yy_ = int(x), int(y) + k
                if 0 <= yy_ < TH and 0 <= xx_ < TW and wings[yy_, xx_]:
                    a[yy_, xx_] = GOLD_L
        dx, dy = cx + sign * 26, cy - 4
        disc = mask(TW, TH, lambda d: d.ellipse((dx - 4, dy - 4, dx + 4, dy + 4), fill=255)) & wings
        a[disc] = H("c8643a")
        a[mask(TW, TH, lambda d: d.ellipse((dx - 2, dy - 2, dx + 2, dy + 2), fill=255)) & wings] = H("ffb04a")
        a[dy, dx] = GOLD_L
    sparkle(a, [(8, 12), (88, 10), (26, 38), (70, 40), (4, 28), (92, 26)], GOLD_L)

    def bolt(x, y, pts, col=GOLD_L):
        px, py = x, y
        for dx, dy in pts:
            for k in range(abs(dy)):
                py += 1 if dy > 0 else -1
                if 0 <= py < TH and 0 <= px < TW:
                    a[py, px] = col
                    if px + 1 < TW: a[py, px + 1] = col
            px += dx
    bolt(8, 14, [(3, 5), (-3, 4), (4, 6), (-2, 6), (3, 8)])
    bolt(88, 12, [(-3, 5), (3, 5), (-4, 6), (2, 5), (-3, 9)])
    return a


def scene_ember():
    a = canvas(TW, TH)
    vgrad(a, [(10, H("1a1418")), (20, H("2e2024")), (30, H("5a2c26")), (38, H("a8431e")), (TH, H("e8742a"))])
    far = mask(TW, TH, lambda d: d.polygon([(0, 40), (10, 34), (18, 38), (28, 32), (40, 40), (60, 40), (72, 34), (84, 38), (95, 34), (95, 42), (0, 42)], fill=255))
    a[far] = H("3a1c1c")
    G = H("3e3a42"); GL = H("5e5a64"); GD = H("26222a")
    # ground
    ground = mask(TW, TH, lambda d: d.polygon([(0, 46), (95, 46), (95, 53), (0, 53)], fill=255))
    # body: low, long, splayed lizard
    body = mask(TW, TH, lambda d: (d.ellipse((18, 30, 82, 48), fill=255), d.polygon([(22, 36), (4, 44), (2, 47), (24, 46)], fill=255)))
    legs = np.zeros((TH, TW), bool)
    for pts in (((24, 40), (30, 40), (22, 50), (16, 51), (17, 47)), ((38, 42), (44, 42), (42, 50), (36, 51)),
                ((60, 42), (66, 42), (68, 50), (74, 51), (60, 49)), ((72, 40), (78, 38), (86, 48), (91, 50), (80, 51), (72, 46))):
        legs |= mask(TW, TH, lambda d, pts=pts: d.polygon(list(pts), fill=255))
    head = mask(TW, TH, lambda d: d.polygon([(74, 32), (86, 34), (92, 38), (92, 44), (80, 47), (72, 44)], fill=255))
    # volcano on the back
    volc = mask(TW, TH, lambda d: d.polygon([(30, 36), (43, 14), (59, 14), (72, 36)], fill=255))
    rim = mask(TW, TH, lambda d: d.polygon([(41, 18), (43, 10), (59, 10), (61, 18)], fill=255))
    a[ground] = COAL
    full = body | legs | head | volc | rim
    outline(a, full)
    a[legs] = H("4a464e")
    bevel(a, legs, GL, GD)
    a[body | head] = G
    band(a, body | head, GD, 42)
    a[volc] = COAL
    a[rim] = H("c8c2b8")
    # lava veins
    for x0_, wob in ((47, 1), (52, -1), (56, 1), (40, -1), (63, 1)):
        x = x0_
        for y in range(15 if x0_ in (47, 52, 56) else 22, 38):
            if volc[y, int(x)]:
                a[y, int(x)] = EMBER
                if y % 3 == 0:
                    a[y, int(x) + 1] = EMBER_L
            x += (wob * 0.45 if y % 2 else 0) + (x0_ - 52) * 0.12
    a[rim & (np.mgrid[0:TH, 0:TW][0] > 12)] = H("e6dfd2")
    a[10:13, 44:58] = EMBER_L
    a[10, 47:55] = GOLD_L
    bevel(a, body | head, GL, GD)
    # glowing eye + red mouth with teeth
    a[36, 83:86] = GOLD_L; a[37, 84] = INK
    mouth = mask(TW, TH, lambda d: d.polygon([(77, 41), (92, 42), (92, 44), (81, 46)], fill=255)) & head
    a[mouth] = H("c0281c")
    for x in (79, 82, 85, 88, 91):
        a[41 if x > 80 else 42, x] = WHITE
    # smoke plume above the crater
    cloud(a, 52, 8, 14, 3, H("5a5660"), H("423e48"), H("423e48"))
    cloud(a, 60, 3, 18, 3, H("5a5660"), H("423e48"), H("423e48"))
    a[0:2, 62:80] = H("423e48")
    # ground cracks with embers
    for (x, y) in ((4, 48), (22, 51), (40, 49), (54, 51), (72, 50), (88, 48), (12, 52), (46, 52)):
        a[y, x:x + 5] = EMBER
        a[y + (1 if y < 52 else 0), x + 2:x + 4] = EMBER_L
    sparkle(a, [(12, 28), (30, 22), (74, 22), (84, 26), (22, 38), (8, 16), (92, 14), (36, 10)], EMBER_L)
    sparkle(a, [(14, 24), (80, 18), (90, 30)], EMBER)
    return a


def banner():
    W, Hh = 280, 100
    a = canvas(W, Hh)
    vgrad(a, [(16, SKY1), (32, SKY2), (48, SKY3), (66, SKY4), (Hh, SKY4)])
    sparkle(a, [(12, 6), (60, 4), (120, 3), (200, 6), (250, 10), (30, 20), (270, 4), (90, 12)], SKY4)
    for (x, y, w_) in ((24, 54, 40), (250, 52, 44), (150, 47, 28)):
        cloud(a, x, y, w_, 5, SKY4, SKY3)
    # the far stone whale, hazy, swimming behind the cloud banks
    whale(a, 78, 48, 192, WHALE_HAZE, ruins=True, chain=True)
    cloud(a, 40, 68, 90, 8)
    cloud(a, 150, 71, 110, 9)
    cloud(a, 245, 68, 80, 8)
    cloud(a, 118, 59, 34, 4, CLOUD, CLOUD_S)
    cloud(a, 222, 58, 28, 4, CLOUD, CLOUD_S)
    top = 76
    lip = np.zeros((Hh, W), bool)
    for x in range(W):
        yy = top + int(2 + 2 * np.sin(x / 9.0) + 2 * np.sin(x / 3.7))
        lip[yy:, x] = True
    sub = np.zeros((Hh, W, 4), np.uint8)
    vgrad(sub, [(top + 8, ABYSS3), (top + 14, ABYSS2), (top + 20, ABYSS1), (Hh, ABYSS0)], y0=top)
    a[lip] = sub[lip]
    a[lip & ~shift(lip, 0, 1)] = INK
    sparkle(a, [(14, 92), (15, 92), (15, 93), (16, 94), (14, 94), (262, 90), (263, 90), (263, 91), (262, 92), (264, 92), (8, 82), (270, 83)], TEAL)
    sparkle(a, [(15, 91), (263, 89)], TEAL_L)
    # title: big no-AA glyphs, emboldened, stone extrusion
    fancy_title(a, W // 2, 7, "天と淵のあいだ", 30, 1, face="biz", depth=3, bold=True)
    sub_s = "BETWEEN THE SKY AND THE ABYSS"
    wsub = text_width(sub_s, 12)
    sx = W // 2 - wsub // 2
    text(a, sx, 84, sub_s, TEAL_L, 12, shadow=ABYSS0)
    for ox in (sx - 11, sx + wsub + 6):
        for d in range(0, 4):
            a[88 - d:89 + d, ox + 2] = TEAL if d else TEAL_L
            a[88, ox + 2 - d:ox + 3 + d] = TEAL if d else TEAL_L
    return a


def label_box(a, x, y, name, sub, col):
    tooltip(a, x, y, [(name, col)])


def titan_card(scene, name, sub, col, fname):
    a = scene
    # stone frame around the scene
    h, w = a.shape[:2]
    out = canvas(w, h)
    out[:] = a
    out[0:1, :] = INK; out[h - 1:h, :] = INK; out[:, 0:1] = INK; out[:, w - 1:w] = INK
    out[1, 1:w - 1] = H("ffffff40")[:3] + (255,) if False else out[1, 1:w - 1]
    label_box(out, 4, h - 21, name, sub, col)
    return out


def make_all():
    out = []
    out.append(save(banner(), "banner.png", 3))
    defs = [
        (scene_whale, "岩鯨", "空を泳ぐ", H("d8d8d8"), "titan_whale.png"),
        (scene_tide, "潮殻", "海を歩く", CORAL_L, "titan_tide.png"),
        (scene_storm, "雷翼", "嵐をまとう", GOLD_L, "titan_storm.png"),
        (scene_ember, "余燼", "地を泳ぐ", EMBER_L, "titan_ember.png"),
    ]
    for fn, n, s, c, f in defs:
        out.append(save(titan_card(fn(), n, s, c, f), f, 4))
    return out


if __name__ == "__main__":
    for p in make_all():
        print(p)
