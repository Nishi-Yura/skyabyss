"""Banner and four titan scenes (distant silhouettes + traits only)."""
import numpy as np
from pix import *

TW, TH = 96, 54


def sparkle(a, pts, col):
    for x, y in pts:
        if 0 <= x < a.shape[1] and 0 <= y < a.shape[0]:
            a[y, x] = col


WHALE_PAL = dict(body=H("3c4658"), lite=H("5a6a84"), dark=H("262c3a"), side=H("4a5770"), plate=H("a08868"), plate_l=H("c8b08a"),
                 plate_d=H("6e5c48"), moss=H("6f8a4a"), spike=H("e2d8bc"), ol=INK, tooth=H("efe8d4"), eye=H("ffc65a"), detail=True)
WHALE_HAZE = dict(body=H("7f95b8"), lite=H("9fb2cf"), dark=H("6a82a8"), side=H("8aa0c2"), plate=H("a7b4c8"), plate_l=H("bcc7d8"),
                  plate_d=H("8d9cb6"), moss=H("98aebc"), spike=H("c9d5e4"), ol=H("5d7499"), tooth=H("c9d5e4"), eye=H("e8d8a0"), detail=False)


def whale(a, x0, y0, L, pal, chain=True):
    """Stone whale (low-poly option C) seen from the side, facing right: a long slate-blue body whose back is
    crusted with tan rock plates and pale spikes, a row of teeth along the jaw, a long thin pectoral fin and thin flukes."""
    h, w = a.shape[:2]
    ts = [0, .12, .35, .6, .85, 1.0]
    rs = [.012, .03, .075, .088, .078, .05]
    n = 100
    path = bez3((x0, y0 + .03 * L), (x0 + .3 * L, y0 + .12 * L), (x0 + .65 * L, y0 + .1 * L), (x0 + L, y0 - .02 * L), n)
    rad = [max(1.0, float(np.interp(i / n, ts, rs)) * L) for i in range(n + 1)]
    m = tube(w, h, path, rad)
    tx, ty = path[0]
    fl = mask(w, h, lambda d: d.polygon([(tx + .02 * L, ty), (tx - .08 * L, ty - .1 * L), (tx - .03 * L, ty - .005 * L), (tx - .09 * L, ty + .07 * L), (tx + .02 * L, ty + .02 * L)], fill=255))
    bx, by = path[66][0], path[66][1] + rad[66] * .5
    fin = mask(w, h, lambda d: d.polygon([(bx + .02 * L, by), (bx - .02 * L, by - .01 * L), (bx - .2 * L, by + .2 * L), (bx - .17 * L, by + .2 * L)], fill=255))
    full = m | fl | fin
    outline(a, full, pal["ol"])
    a[full] = pal["body"]
    cols = np.where(m.any(axis=0))[0]
    tops = {}
    for x in cols:
        ys = np.where(m[:, x])[0]
        top, bot = ys.min(), ys.max()
        tops[x] = top
        # lower flank a shade lighter (the mesh's blue side), the back covered by rock plates
        for y in ys:
            if y > top + 0.62 * (bot - top):
                a[y, x] = pal["side"] if (x + y) % 3 else pal["body"]
        if x0 + .1 * L < x < x0 + .86 * L:
            depth = int(max(2, 0.42 * (bot - top)))
            for y in range(top, top + depth):
                a[y, x] = pal["plate"]
    bevel(a, m, pal["lite"], pal["dark"])
    if pal["detail"]:
        # hex plate seams on the back
        for x in cols:
            if not (x0 + .1 * L < x < x0 + .86 * L):
                continue
            top = tops[x]
            depth = int(max(2, 0.42 * (np.where(m[:, x])[0].max() - top)))
            for y in range(top + 1, top + depth):
                u = (x - x0) / L * 100
                cell = int((u + (y // 4) * 2.5) // 5)
                if int(u + (y // 4) * 2.5) % 5 == 0 or (y - top) % 4 == 0:
                    a[y, x] = pal["plate_d"]
                elif (cell * 7 + y) % 5 == 0:
                    a[y, x] = pal["plate_l"]
                elif (cell * 3 + x) % 11 == 0:
                    a[y, x] = pal["moss"]
            a[top, x] = pal["plate_l"]
        # pale spikes poking up from the plates
        for k, t in enumerate(np.arange(.16, .9, .055)):
            x = int(path[int(t * 100)][0])
            if x in tops:
                hgt = 2 + (k % 3 == 1)
                for d_ in range(hgt):
                    if tops[x] - 1 - d_ >= 0:
                        a[tops[x] - 1 - d_, x] = pal["spike"]
                a[tops[x] - 1, x - 1] = pal["ol"]
    else:
        for k, t in enumerate(np.arange(.16, .9, .055)):
            x = int(path[int(t * 100)][0])
            if x in tops and tops[x] - 1 >= 0:
                a[tops[x] - 1, x] = pal["spike"]
    a[fl | fin] = pal["body"]
    bevel(a, fl | fin, pal["lite"], pal["dark"])
    # eye, mouth line and teeth
    ex, ey = path[90]
    a[int(ey + .005 * L), int(ex)] = pal["eye"]
    mx, my = path[100]
    x_a, x_b = int(mx - .17 * L), int(mx - .01 * L)
    ys = np.where(m[:, x_a])[0]
    jy = int(ys.max() - .3 * (ys.max() - ys.min()))
    a[jy, x_a:x_b] = pal["ol"]
    if pal["detail"]:
        for x in range(x_a + 1, x_b, 2):
            a[jy - 1, x] = pal["tooth"]
            if (x // 2) % 2 == 0:
                a[jy + 1, x + 1] = pal["tooth"]
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
    """Tide shell (low-poly option B): a four-terrace reef island of sand on a teal reef skirt, a stone watch tower with a
    glowing lantern on the top terrace, coral on the steps, the tide gate in its flank and a long tiled head low in the sea."""
    a = canvas(TW, TH)
    vgrad(a, [(10, H("6fa8d0")), (20, H("a8d0e0")), (28, H("f0cfa8")), (29, H("f6dcb4"))])
    sea_y = 31
    vgrad(a, [(36, SEA1), (43, SEA2), (48, SEA3), (TH, SEA4)], y0=sea_y)
    cloud(a, 20, 10, 24, 4, H("fbe9d0"), H("e8c8b0"))
    cloud(a, 84, 8, 18, 3, H("fbe9d0"), H("e8c8b0"))
    a[29:31, 72:84] = H("f8e8c0")
    SAND = H("d8c49a"); SAND_L = H("efe0b8"); SAND_D = H("a8946c"); SAND_DD = H("7c6a4c")
    REEF = H("3f9a8e"); REEF_L = H("6cc8b4"); REEF_D = H("27685f")
    yy, xx = np.mgrid[0:TH, 0:TW]
    cx = 54
    # head: long, low, tile-plated, facing left; flippers: flat paddles at the waterline
    head = mask(TW, TH, lambda d: d.polygon([(30, 31), (14, 33), (6, 36), (12, 38), (30, 38)], fill=255))
    ff = mask(TW, TH, lambda d: d.polygon([(30, 36), (20, 41), (14, 45), (24, 44), (36, 39)], fill=255))
    rf = mask(TW, TH, lambda d: d.polygon([(76, 36), (86, 40), (92, 44), (84, 44), (72, 39)], fill=255))
    skirt = mask(TW, TH, lambda d: d.ellipse((cx - 32, 27, cx + 32, 41), fill=255))
    tiers = []
    for k, (hw, top) in enumerate(((28, 28), (22, 23), (16, 18), (10, 13))):
        bot = top + 5 if k < 3 else 29
        tiers.append(mask(TW, TH, lambda d, hw=hw, top=top, bot=bot: d.polygon([(cx - hw, bot), (cx - hw + 2, top), (cx + hw - 2, top), (cx + hw, bot)], fill=255)))
    isle = tiers[0] | tiers[1] | tiers[2] | tiers[3]
    tx = cx + 1
    tower = mask(TW, TH, lambda d: (d.rectangle((tx - 2, 3, tx + 2, 14), fill=255), d.rectangle((tx - 3, 1, tx + 3, 4), fill=255)))
    full = head | ff | rf | skirt | isle | tower
    outline(a, full)
    a[ff | rf] = REEF_D
    bevel(a, ff | rf, REEF, H("1b4a48"))
    a[head] = H("e2d8b8")
    for x in range(8, 30, 3):
        a[33:38, x][head[33:38, x]] = H("b8ac88")
    bevel(a, head, H("f6f0dc"), H("a89e78"))
    a[34, 12] = INK; a[33, 12] = WHITE
    for x in range(7, 26, 2):
        if head[37, x]:
            a[37, x] = WHITE
    a[skirt] = REEF
    a[skirt & ((xx * 3 + yy * 5) % 7 == 0)] = REEF_L
    bevel(a, skirt, REEF_L, REEF_D)
    # sand terraces: lit tops, dark risers, a darker band at each step
    for k, t in enumerate(tiers):
        a[t] = SAND
        a[t & ((xx + yy * 3) % 9 == 0)] = SAND_L
        edge = t & ~shift(t, 0, 1)
        a[edge] = SAND_L
    bevel(a, isle, SAND_L, SAND_DD)
    # the step below each upper terrace: a dark shadow row, then the lit ledge of the terrace under it
    for k in range(1, 4):
        t = tiers[k]
        a[t & ~shift(t, 0, -1)] = SAND_DD
        a[t & ~shift(t, 0, -2) & shift(t, 0, -1)] = SAND_D
    # tide gate in the right flank: dark frame, glowing water
    gx, gy = cx + 20, 24
    a[gy:gy + 9, gx:gx + 6] = H("2a2e3a")
    a[gy + 1:gy + 9, gx + 1:gx + 5] = H("5ad8e0")
    a[gy + 2:gy + 9:2, gx + 1:gx + 5] = H("9af0f0")
    # watch tower with a glowing lantern
    a[tower] = H("8a8a94")
    a[tower & (xx > tx)] = H("62626c")
    a[1:4, tx - 3:tx + 4] = H("aeaeb8")
    a[4:7, tx - 1:tx + 2] = H("8cf0ff"); a[5, tx] = WHITE
    for y in range(8, 14, 3):
        a[y, tx - 2:tx + 3] = H("74747e")
    # coral on the steps: orange heads, purple crystals, pink branches, yellow fans
    for (px, py, c) in ((36, 25, H("f0883a")), (68, 21, H("f0883a")), (44, 16, H("f0883a")), (63, 15, H("a864c8")), (40, 21, H("a864c8")),
                        (72, 26, H("e8607e")), (48, 11, H("e8b83a")), (30, 27, H("e8b83a"))):
        a[py:py + 2, px:px + 2] = c
        a[py - 1, px] = c
        a[py + 1, px + 1] = tuple(int(v * .7) for v in c[:3]) + (255,)
    # water over the lower skirt and flippers, ripples, foam
    for y in range(37, TH):
        for x in range(TW):
            if (skirt | ff | rf)[y, x] and (x + y) % 2 == 0:
                a[y, x] = SEA2
    for (rx, ry, rw) in ((16, 46, 14), (60, 45, 16), (40, 50, 20), (84, 48, 12), (8, 41, 8)):
        a[ry, rx - rw // 2:rx + rw // 2] = H("9ad0f0")
        a[ry + 1, rx - rw // 2 + 2:rx + rw // 2 - 2] = H("5a98d0")
    a[37, 22:86][(skirt | ff | rf)[37, 22:86]] = H("cfeef0")
    return a


def scene_storm():
    """Thunder wing (low-poly option A, the storm eagle) in flight, facing left: slate-blue back, pale grey belly,
    long swept wings with white feather tips, a hooked yellow beak, a tan crest and two small spires on its back."""
    a = canvas(TW, TH)
    vgrad(a, [(12, H("141a38")), (24, H("232e5a")), (36, H("34457a")), (TH, H("4a5f90"))])
    for (x, y, w_) in ((16, 10, 40), (80, 6, 36), (50, 4, 50)):
        cloud(a, x, y, w_, 7, H("2c3562"), H("1e2548"), H("1e2548"))
    cloud(a, 14, 50, 36, 7, H("56688f"), H("46557c"), H("38456a"))
    cloud(a, 84, 51, 40, 7, H("56688f"), H("46557c"), H("38456a"))
    SL = H("3e4a66"); SLL = H("62708e"); SLD = H("262c40"); BELLY = H("b4bcca"); BELLY_D = H("8a94a8")
    FE = H("e8ecf4"); FE_D = H("aab2c4"); BEAK = H("e8c04a"); BEAK_D = H("a8801e"); CREST = H("c8a878")
    yy, xx = np.mgrid[0:TH, 0:TW]
    cy = 30
    # far wing (behind, darker) and near wing: broad, raised and swept back, ending in spread primary feathers
    def wing(root, tip, chord, fingers=5):
        (rx, ry), (tx_, ty_) = root, tip
        lead = [(rx, ry), (rx + (tx_ - rx) * .35, ry - (ry - ty_) * .55), (tx_ - 4, ty_)]
        trail = []
        for k in range(fingers):
            u = k / (fingers - 1.0)
            fx = tx_ - 1 - k * 2.2
            fy = ty_ + 2 + k * 2.4
            trail += [(fx + 4, fy - 1), (fx + 1, fy + 1)]
        trail += [(tx_ - 14, ty_ + chord + 2), (rx + (tx_ - rx) * .45, ry - (ry - ty_) * .2 + chord), (rx + 6, ry + chord)]
        pts = lead + trail
        return mask(TW, TH, lambda d: d.polygon(pts, fill=255))
    far = wing((48, cy - 3), (74, 2), 8)
    near = wing((40, cy - 1), (92, 6), 11, 6)
    body = mask(TW, TH, lambda d: d.ellipse((22, cy - 5, 62, cy + 7), fill=255))
    tail = mask(TW, TH, lambda d: d.polygon([(58, cy - 2), (74, cy - 3), (77, cy + 2), (72, cy + 6), (58, cy + 5)], fill=255))
    head = mask(TW, TH, lambda d: d.ellipse((12, cy - 8, 28, cy + 3), fill=255))
    beak = mask(TW, TH, lambda d: d.polygon([(14, cy - 5), (7, cy - 4), (4, cy - 1), (7, cy + 1), (9, cy - 1), (14, cy)], fill=255))
    crest = mask(TW, TH, lambda d: d.polygon([(21, cy - 7), (27, cy - 12), (26, cy - 8), (31, cy - 11), (29, cy - 7), (33, cy - 8), (29, cy - 4)], fill=255))
    legs = mask(TW, TH, lambda d: (d.polygon([(36, cy + 5), (39, cy + 5), (38, cy + 10), (34, cy + 11)], fill=255),
                                   d.polygon([(44, cy + 5), (47, cy + 5), (46, cy + 10), (42, cy + 11)], fill=255)))
    full = far | near | body | tail | head | beak | crest | legs
    outline(a, full)
    a[far] = SLD
    bevel(a, far, SL, H("1a1e2e"))
    a[tail] = SL
    a[tail & (xx > 73)] = FE_D
    a[body | head] = SL
    a[(body | head) & (yy > cy + 1)] = BELLY
    a[(body | head) & (yy == cy + 1)] = BELLY_D
    bevel(a, body | head, SLL, SLD)
    a[crest] = CREST
    a[crest & (yy < cy - 9)] = H("ecd8a8")
    a[beak] = BEAK
    a[beak & (yy > cy - 2)] = BEAK_D
    a[legs] = BEAK_D
    a[cy - 4, 17] = H("ffd070"); a[cy - 4, 16] = INK
    a[near] = SL
    # covert rows, then white tips along the trailing edge of the near wing
    a[near & ((yy + xx // 6) % 4 == 0)] = SLL
    tip = near & ~shift(near, 0, -1)
    a[tip] = FE
    a[near & ~shift(near, 0, -2) & ~tip] = FE_D
    bevel(a, near, SLL, SLD)
    a[tip] = FE
    # two small spires on the back, with gold tips
    for x in (36, 46):
        top = np.where(body[:, x])[0].min()
        a[top - 4:top, x] = H("8a90a0")
        a[top - 5, x] = GOLD_L
        a[top - 4:top, x + 1] = SLD
    sparkle(a, [(8, 12), (88, 46), (26, 44), (70, 42), (4, 28), (60, 16)], GOLD_L)

    def bolt(x, y, pts, col=GOLD_L):
        px, py = x, y
        for dx, dy in pts:
            for k in range(abs(dy)):
                py += 1 if dy > 0 else -1
                if 0 <= py < TH and 0 <= px < TW:
                    a[py, px] = col
                    if px + 1 < TW: a[py, px + 1] = col
            px += dx
    bolt(10, 2, [(3, 4), (-3, 3), (3, 4)])
    bolt(84, 30, [(-3, 5), (3, 5), (-4, 6), (2, 6)])
    return a


def scene_ember():
    """Ember titan (low-poly option C, skin turning to rock): a low grey-brown salamander whose back is crusting over
    with black basalt plates split by glowing cracks, a rocky volcano with a burning crater, and an open, toothed mouth."""
    a = canvas(TW, TH)
    vgrad(a, [(10, H("1a1418")), (20, H("2e2024")), (30, H("5a2c26")), (38, H("a8431e")), (TH, H("e8742a"))])
    far = mask(TW, TH, lambda d: d.polygon([(0, 40), (10, 34), (18, 38), (28, 32), (40, 40), (60, 40), (72, 34), (84, 38), (95, 34), (95, 42), (0, 42)], fill=255))
    a[far] = H("3a1c1c")
    SK = H("80706a"); SK_L = H("a8968a"); SK_D = H("4e423e"); CR = H("24201f"); CR_L = H("3e3634"); CR_D = H("141112")
    yy, xx = np.mgrid[0:TH, 0:TW]
    ground = mask(TW, TH, lambda d: d.polygon([(0, 46), (95, 46), (95, 53), (0, 53)], fill=255))
    body = mask(TW, TH, lambda d: (d.ellipse((18, 31, 80, 48), fill=255), d.polygon([(22, 37), (4, 44), (2, 47), (24, 46)], fill=255)))
    legs = np.zeros((TH, TW), bool)
    for pts in (((24, 41), (30, 41), (22, 50), (15, 51), (17, 47)), ((38, 43), (44, 43), (42, 50), (35, 51)),
                ((58, 43), (64, 43), (67, 50), (73, 51), (59, 49)), ((70, 41), (76, 39), (84, 48), (89, 50), (78, 51), (70, 46))):
        legs |= mask(TW, TH, lambda d, pts=pts: d.polygon(list(pts), fill=255))
    head = mask(TW, TH, lambda d: d.polygon([(72, 33), (85, 34), (93, 37), (94, 41), (90, 46), (78, 47), (70, 44)], fill=255))
    volc = mask(TW, TH, lambda d: d.polygon([(28, 38), (36, 26), (44, 14), (58, 14), (64, 24), (74, 38)], fill=255))
    rim = mask(TW, TH, lambda d: d.polygon([(42, 18), (44, 11), (58, 11), (60, 18)], fill=255))
    a[ground] = COAL
    full = body | legs | head | volc | rim
    outline(a, full)
    # skin: grey-brown, lighter on the flank, toes and jaw
    a[body | head | legs] = SK
    a[(body | head) & (yy > 42)] = SK_D
    bevel(a, legs, SK_L, SK_D)
    # basalt crust spreading down from the back over the skin (ragged lower edge)
    crust = (body | head) & (yy < 38 + ((xx * 7) % 5) - (xx > 70) * 3)
    a[crust] = CR
    a[crust & ((xx * 5 + yy * 3) % 11 == 0)] = CR_L
    bevel(a, body | head, SK_L, SK_D)
    # volcano of dark stones, lava cracks running down, a burning crater
    a[volc] = CR
    cell = ((xx // 2 + (yy // 2) * 2) * 2654435761 + (yy // 2) * 40503) % 97
    a[volc & (cell % 4 == 0)] = CR_L
    a[volc & (cell % 7 == 1)] = CR_D
    a[volc & ~shift(volc, -1, 0)] = CR_D
    for x0_, wob in ((47, 1), (52, -1), (56, 1), (40, -1), (63, 1)):
        x = x0_
        for y in range(15 if x0_ in (47, 52, 56) else 24, 40):
            if (volc | crust)[y, int(x)]:
                a[y, int(x)] = EMBER
                if y % 3 == 0:
                    a[y, int(x) + 1] = EMBER_L
            x += (wob * 0.45 if y % 2 else 0) + (x0_ - 52) * 0.12
    # cracks in the crust of the back
    for (x, y, l) in ((24, 37, 6), (66, 36, 5), (31, 40, 4), (73, 37, 4)):
        a[y, x:x + l][crust[y, x:x + l]] = EMBER_D
        a[y - 1, x + 1:x + l - 1][crust[y - 1, x + 1:x + l - 1]] = EMBER
    a[rim] = CR_L
    a[rim & (yy < 13)] = EMBER_D
    a[10:12, 44:58] = EMBER_L
    a[10, 47:55] = GOLD_L
    # glowing eye under a heavy lid, open red mouth with teeth
    a[36, 84:87] = GOLD_L; a[35, 83:88] = CR
    mouth = mask(TW, TH, lambda d: d.polygon([(76, 41), (93, 40), (93, 42), (88, 45), (80, 45)], fill=255)) & head
    a[mouth] = H("c0281c")
    a[mouth & (yy == 43)] = H("ff6a2a")
    for x in (78, 81, 84, 87, 90):
        a[41, x] = WHITE
        a[44 if x < 88 else 43, x + 1] = WHITE
    # smoke plume above the crater
    cloud(a, 52, 8, 14, 3, H("5a5660"), H("423e48"), H("423e48"))
    cloud(a, 60, 3, 18, 3, H("5a5660"), H("423e48"), H("423e48"))
    a[0:2, 62:80] = H("423e48")
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
    whale(a, 78, 48, 192, WHALE_HAZE, chain=True)
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
    sx = W // 2 - wsub // 2 - 24
    text(a, sx, 84, sub_s, TEAL_L, 12, shadow=ABYSS0)
    for ox in (sx - 11, sx + wsub + 6):
        for d in range(0, 4):
            a[88 - d:89 + d, ox + 2] = TEAL if d else TEAL_L
            a[88, ox + 2 - d:ox + 3 + d] = TEAL if d else TEAL_L
    import extras
    extras.version_plaque(a, W - 5 - (text_width("v0.2.0", 12) + 8) + 1, 83)
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
