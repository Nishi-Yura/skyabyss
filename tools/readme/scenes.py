"""Banner and four titan scenes (distant silhouettes + traits only)."""
import numpy as np
from pix import *

TW, TH = 96, 54


def sparkle(a, pts, col):
    for x, y in pts:
        if 0 <= x < a.shape[1] and 0 <= y < a.shape[0]:
            a[y, x] = col


def whale(w, h, a, x0, y0, s=1.0, body=ROCK, lite=ROCK_L, dark=ROCK_D, bone=BONE, flip=False, ol=INK):
    """Rock whale facing right. (x0,y0)=tail tip; s = scale."""
    path = bez3((x0, y0 + 2 * s), (x0 + 22 * s, y0 + 14 * s), (x0 + 50 * s, y0 + 12 * s), (x0 + 78 * s, y0 - 4 * s), 80)
    rad = [(1.5 + 9.5 * np.sin(min(1, (i / 80) * 1.15 + 0.12) * np.pi * 0.85)) * s for i in range(81)]
    rad = [r if i < 70 else r * (1 - 0.02 * (i - 70)) for i, r in enumerate(rad)]
    m = tube(w, h, path, rad)
    # flukes
    tx, ty = path[0]
    fl = mask(w, h, lambda d: d.polygon([(tx + 3 * s, ty + 1 * s), (tx - 9 * s, ty - 9 * s), (tx - 4 * s, ty - 1 * s), (tx - 10 * s, ty + 6 * s)], fill=255))
    fin = mask(w, h, lambda d: d.polygon([(x0 + 56 * s, y0 + 12 * s), (x0 + 50 * s, y0 + 22 * s), (x0 + 62 * s, y0 + 14 * s)], fill=255))
    full = m | fl | fin
    outline(a, full, ol)
    a[full] = body
    band(a, full, dark, int(y0 + 13 * s), dither=True)
    # bone belly stripe
    belly = m & (np.mgrid[0:h, 0:w][0] > y0 + 15 * s) & shift(~m, 0, -2)
    # ridges (bone spikes) along the back
    for i in range(6, 68, 7):
        px, py = path[i]
        r = rad[i]
        sp = mask(w, h, lambda d, px=px, py=py, r=r: d.polygon([(px - 3 * s, py - r + 1), (px + 1, py - r - 5 * s), (px + 3 * s, py - r + 1)], fill=255))
        a[sp] = bone
        outline(a, sp & ~m, ol)
    bevel(a, m, lite, dark)
    a[fl | fin] = body
    bevel(a, fl | fin, lite, dark)
    # jaw line and eye
    hx, hy = path[74]
    a[int(hy + 3 * s), int(hx - 8 * s):int(hx + 7 * s)] = dark
    a[int(hy - 1 * s), int(hx + 2 * s)] = WHITE
    a[int(hy - 1 * s), int(hx + 3 * s)] = ol
    # bone belly
    by = int(path[50][1] + rad[50] - 3 * s)
    for i in range(24, 66):
        px, py = path[i]
        yy = int(py + rad[i] - 2 * s)
        for k in range(0, 2):
            if 0 <= yy - k < h and m[yy - k, int(px)] and ((i + k) % 2 == 0):
                a[yy - k, int(px)] = bone
    return full


def scene_whale():
    a = canvas(TW, TH)
    vgrad(a, [(14, SKY1), (26, SKY2), (38, SKY3), (TH, SKY4)])
    for i in range(0, 90, 7):
        a[(i * 5) % 20, (i * 13) % TW] = SKY3
    cloud(a, 14, 47, 38, 7)
    cloud(a, 78, 51, 40, 6, CLOUD, CLOUD_S, CLOUD_D)
    cloud(a, 66, 12, 26, 4, SKY4, SKY3)
    whale(TW, TH, a, 12, 12, 0.8)
    # tiny tower on the back for scale
    for x in range(44, 47):
        for y in range(8, 14):
            a[y, x] = STONE_D
    a[7, 43:48] = STONE; a[6, 44:47] = STONE_L; a[9, 45] = EMBER_L
    cloud(a, 40, 51, 34, 7, CLOUD, CLOUD_S, CLOUD_D)
    cloud(a, 4, 36, 18, 4, SKY4, SKY3)
    return a


def scene_tide():
    a = canvas(TW, TH)
    vgrad(a, [(10, H("6fa8c8")), (20, H("9ccbd0")), (30, H("f0c9a0")), (31, H("f6d9b0"))])
    sea_y = 30
    vgrad(a, [(36, H("2f8aa0")), (44, H("246f8a")), (50, H("1c566f")), (TH, H("14405a"))], y0=sea_y)
    cloud(a, 20, 14, 22, 4, H("fbe9d0"), H("e8c8b0"))
    cloud(a, 80, 9, 18, 3, H("fbe9d0"), H("e8c8b0"))
    # distant sun glow
    a[26:30, 66:74] = H("f8e0b0")
    # shell dome
    cx, cy = 50, 33
    def f(d):
        d.pieslice((cx - 30, cy - 26, cx + 30, cy + 26), 180, 360, fill=255)
        d.rectangle((cx - 30, cy - 1, cx + 30, cy), fill=255)
    dome = mask(TW, TH, f)
    # legs
    legs = np.zeros((TH, TW), bool)
    for lx in (26, 36, 62, 72):
        legs |= mask(TW, TH, lambda d, lx=lx: d.polygon([(lx, 31), (lx + 6, 31), (lx + 5, 44), (lx + 2, 46), (lx - 1, 44)], fill=255))
    head = mask(TW, TH, lambda d: (d.ellipse((10, 24, 26, 36), fill=255), d.polygon([(20, 29), (28, 29), (28, 34), (20, 36)], fill=255)))
    neck = mask(TW, TH, lambda d: d.polygon([(18, 28), (30, 20), (34, 30), (22, 36)], fill=255))
    full = dome | legs | head | neck
    outline(a, full)
    a[legs] = MOSS_D
    a[head | neck] = MOSS
    a[dome] = CORAL
    yy = np.mgrid[0:TH, 0:TW][0]
    band(a, dome, CORAL_D, 30)
    band(a, dome & (yy < 14 + 12), CORAL_L, 0)  # placeholder (overwritten below)
    a[dome] = CORAL
    band(a, dome, CORAL_D, 29)
    # shell ridges (radial plates)
    for ang in (-60, -35, -12, 12, 35, 60):
        t = np.radians(ang)
        for r in range(6, 26):
            x = int(cx + np.sin(t) * r * 1.15)
            y = int(cy - np.cos(t) * r)
            if dome[y, x]:
                a[y, x] = CORAL_L if r % 3 else CORAL_D
    bevel(a, dome, CORAL_L, CORAL_D)
    # spikes
    for sx in (cx - 20, cx - 10, cx, cx + 10, cx + 20):
        top = int(cy - 26 * np.sqrt(max(0, 1 - ((sx - cx) / 30) ** 2)))
        sp = mask(TW, TH, lambda d, sx=sx, top=top: d.polygon([(sx - 2, top + 2), (sx, top - 5), (sx + 2, top + 2)], fill=255))
        a[sp] = BONE
        outline(a, sp & ~dome)
    # moss patches
    for (mx, my) in ((38, 12), (55, 9), (66, 15), (46, 17), (72, 22)):
        for dx in range(-2, 3):
            if dome[my, mx + dx]:
                a[my, mx + dx] = MOSS
                if dome[my + 1, mx + dx] and abs(dx) < 2:
                    a[my + 1, mx + dx] = MOSS_D
    bevel(a, head | neck, H("7cd09a"), MOSS_D)
    a[26, 17] = WHITE; a[26, 18] = INK
    # water over legs + rings
    a[44:TH, :][~(legs[44:TH, :])] = a[44:TH, :][~(legs[44:TH, :])]
    for (rx, ry, rw) in ((20, 46, 12), (36, 47, 14), (60, 47, 14), (74, 46, 12), (46, 51, 20)):
        a[ry, rx - rw // 2:rx + rw // 2] = H("8fd0d8")
        a[ry + 1, rx - rw // 2 + 2:rx + rw // 2 - 2] = H("5aa8bc")
    for (x, y) in ((8, 40), (88, 38), (14, 52), (84, 51)):
        a[y, x:x + 3] = H("8fd0d8")
    return a


def scene_storm():
    a = canvas(TW, TH)
    vgrad(a, [(12, H("141a38")), (24, H("232e5a")), (36, H("34457a")), (TH, H("4a5f90"))])
    # storm clouds
    for (x, y, w_, c1, c2) in ((16, 10, 40, H("1e2548"), H("2c3562")), (80, 8, 36, H("1e2548"), H("2c3562")), (50, 5, 50, H("171d3c"), H("232a52"))):
        cloud(a, x, y, w_, 7, c2, c1, c1)
    cloud(a, 14, 50, 36, 7, H("56688f"), H("46557c"), H("38456a"))
    cloud(a, 84, 51, 40, 7, H("56688f"), H("46557c"), H("38456a"))
    cx, cy = 48, 28
    # wings (scalloped)
    def wing(sign):
        pts = [(cx, cy - 6), (cx + sign * 14, cy - 17), (cx + sign * 30, cy - 20), (cx + sign * 45, cy - 14), (cx + sign * 40, cy - 7)]
        pts += [(cx + sign * 41, cy - 3), (cx + sign * 34, cy - 2), (cx + sign * 33, cy + 4), (cx + sign * 26, cy + 1), (cx + sign * 24, cy + 8), (cx + sign * 17, cy + 3), (cx + sign * 13, cy + 9), (cx + sign * 8, cy + 3), (cx, cy + 6)]
        return mask(TW, TH, lambda d: d.polygon(pts, fill=255))
    wl, wr = wing(-1), wing(1)
    body = mask(TW, TH, lambda d: (d.ellipse((cx - 8, cy - 8, cx + 8, cy + 12), fill=255),
                                   d.polygon([(cx - 3, cy + 8), (cx + 3, cy + 8), (cx + 2, cy + 22), (cx, cy + 25), (cx - 2, cy + 22)], fill=255)))
    head = mask(TW, TH, lambda d: (d.ellipse((cx - 5, cy - 16, cx + 5, cy - 6), fill=255), d.polygon([(cx - 2, cy - 7), (cx + 2, cy - 7), (cx, cy - 1)], fill=255)))
    full = wl | wr | body | head
    outline(a, full)
    a[wl | wr] = STORM
    band(a, (wl | wr), STORM_D, cy - 4)
    a[body | head] = STORM_L
    band(a, body, STORM, cy + 6)
    bevel(a, wl | wr, STORM_L, STORM_D)
    bevel(a, body | head, H("7aa0ee"), STORM_D)
    # gold leading edge + crest + beak
    for sign in (-1, 1):
        for t in range(0, 40):
            x = int(cx + sign * (6 + t * 0.95))
            y = int(cy - 7 - 12 * np.sin(min(1, t / 26) * np.pi / 2) + (0 if t < 26 else (t - 26) * 0.75))
            if 0 <= y < TH and 0 <= x < TW and (wl | wr)[y, x]:
                a[y, x] = GOLD
                if y + 1 < TH and (wl | wr)[y + 1, x]:
                    a[y + 1, x] = GOLD_D
    crest = mask(TW, TH, lambda d: d.polygon([(cx - 2, cy - 15), (cx, cy - 22), (cx + 2, cy - 15)], fill=255))
    a[crest] = GOLD; outline(a, crest & ~head)
    beak = mask(TW, TH, lambda d: d.polygon([(cx - 2, cy - 7), (cx + 2, cy - 7), (cx, cy)], fill=255))
    a[beak] = GOLD_L
    a[cy - 11, cx - 3] = GOLD_L; a[cy - 11, cx + 2] = GOLD_L
    # lightning
    def bolt(x, y, pts, col=GOLD_L):
        px, py = x, y
        for dx, dy in pts:
            for k in range(abs(dy)):
                py += 1 if dy > 0 else -1
                if 0 <= py < TH and 0 <= px < TW:
                    a[py, px] = col
                    if px + 1 < TW: a[py, px + 1] = col
            px += dx
    bolt(8, 12, [(3, 5), (-3, 4), (4, 6), (-2, 6), (3, 8)])
    bolt(86, 10, [(-3, 5), (3, 5), (-4, 6), (2, 5), (-3, 9)])
    bolt(24, 24, [(2, 3), (-2, 3), (2, 4)], WHITE)
    sparkle(a, [(30, 18), (70, 20), (20, 36), (76, 34), (6, 30), (90, 28)], GOLD_L)
    return a


def scene_ember():
    a = canvas(TW, TH)
    vgrad(a, [(10, H("1a1418")), (20, H("2e2024")), (30, H("5a2c26")), (38, H("a8431e")), (TH, H("e8742a"))])
    # glow horizon + smoke
    cloud(a, 20, 12, 30, 5, H("3a2c30"), H("2a2024"), H("2a2024"))
    cloud(a, 76, 16, 32, 5, H("3a2c30"), H("2a2024"), H("2a2024"))
    # far crags
    far = mask(TW, TH, lambda d: d.polygon([(0, 38), (10, 30), (18, 36), (28, 28), (40, 38), (60, 38), (72, 30), (84, 36), (95, 31), (95, 40), (0, 40)], fill=255))
    a[far] = H("3a1c1c")
    # ground
    g = mask(TW, TH, lambda d: d.polygon([(0, 40), (30, 38), (60, 40), (95, 38), (95, 53), (0, 53)], fill=255))
    # worm body arch (a lump rising from the ground)
    path = bez3((8, 46), (22, 6), (62, 4), (80, 46), 90)
    rad = [8.5 + 2.0 * np.sin(i / 90 * np.pi) for i in range(91)]
    worm = tube(TW, TH, path, rad)
    worm &= ~(np.mgrid[0:TH, 0:TW][0] > 46)
    # head (right side) with glowing maw
    hx, hy = path[-1]
    head = mask(TW, TH, lambda d: d.polygon([(70, 34), (88, 38), (92, 46), (72, 46)], fill=255))
    full = worm | head
    outline(a, full)
    a[g] = COAL
    a[worm | head] = COAL_L
    band(a, worm | head, COAL, 24)
    # segment rings with ember seams
    for i in range(6, 86, 6):
        px, py = path[i]
        qx, qy = path[i + 2]
        dx, dy = qx - px, qy - py
        n = np.hypot(dx, dy) or 1
        nx, ny = -dy / n, dx / n
        for r in range(-12, 13):
            x, y = int(px + nx * r), int(py + ny * r)
            if 0 <= x < TW and 0 <= y < TH and worm[y, x]:
                a[y, x] = EMBER if (abs(r) > 2 and (i // 6) % 2 == 0) else COAL
    bevel(a, worm | head, H("4a4042"), COAL)
    # maw
    maw = mask(TW, TH, lambda d: d.polygon([(82, 41), (91, 43), (91, 46), (79, 46)], fill=255)) & head
    a[maw] = EMBER_L
    a[43, 82:90] = GOLD_L
    for x in (83, 86, 89):
        a[41, x] = BONE; a[42, x] = BONE
    a[38, 78] = EMBER_L; a[38, 79] = EMBER_L
    # cracks in the ground
    for (x, y) in ((6, 48), (20, 50), (38, 47), (54, 50), (70, 49), (88, 48), (12, 52), (46, 52)):
        a[y, x:x + 5] = EMBER
        a[y + (1 if y < 52 else 0), x + 2:x + 4] = EMBER_L
    for x in range(0, TW, 3):
        if x % 7 == 0:
            a[39 if x % 2 else 40, x] = EMBER_D
    # sparks
    sparkle(a, [(12, 28), (30, 20), (44, 12), (60, 22), (84, 24), (72, 10), (24, 38), (94, 18), (6, 14)], EMBER_L)
    sparkle(a, [(14, 22), (50, 16), (88, 30), (36, 30)], EMBER)
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


def banner():
    W, Hh = 280, 100
    a = canvas(W, Hh)
    vgrad(a, [(18, SKY1), (36, SKY2), (52, SKY3), (66, SKY4), (Hh, SKY4)])
    # stars-ish sparkles high up
    sparkle(a, [(12, 6), (60, 10), (120, 4), (200, 8), (250, 14), (30, 22), (150, 16), (270, 5)], SKY4)
    # distant cloud banks
    for (x, y, w_) in ((30, 60, 60), (110, 64, 80), (200, 61, 70), (262, 65, 50)):
        cloud(a, x, y, w_, 8)
    # huge far whale (hazy)
    whale(W, Hh, a, 168, 14, 1.05, body=H("6c88b0"), lite=H("8ea8c8"), dark=H("55709a"), bone=H("c8d6e6"), ol=H("3d5a85"))
    cloud(a, 90, 70, 110, 9, CLOUD, CLOUD_S, CLOUD_D)
    cloud(a, 220, 72, 90, 8, CLOUD, CLOUD_S, CLOUD_D)
    # the abyss: dark band with stepped, dithered edge
    top = 76
    ab = [(top + 6, ABYSS3), (top + 12, ABYSS2), (top + 18, ABYSS1), (Hh, ABYSS0)]
    vgrad(a, ab, y0=top)
    # ragged cliff lip across the top of the abyss
    lip = np.zeros((Hh, W), bool)
    for x in range(W):
        yy = top + int(2 + 2 * np.sin(x / 9.0) + 2 * np.sin(x / 3.7))
        lip[yy:, x] = True
    keep = lip
    a[(np.mgrid[0:Hh, 0:W][0] >= top) & ~keep] = H("8fb8d8")
    # re-run: sky+cloud remain above lip; below lip is abyss
    sub = np.zeros((Hh, W, 4), np.uint8)
    vgrad(sub, [(top + 8, ABYSS3), (top + 14, ABYSS2), (top + 20, ABYSS1), (Hh, ABYSS0)], y0=top)
    a[keep] = sub[keep]
    lipedge = keep & ~shift(keep, 0, 1)
    a[lipedge] = INK
    # teal inscription glyphs glowing in the dark
    gl = [(14, 92), (15, 92), (15, 93), (16, 94), (14, 94), (262, 90), (263, 90), (263, 91), (262, 92), (264, 92), (8, 82), (270, 83)]
    sparkle(a, gl, TEAL)
    sparkle(a, [(15, 91), (263, 89)], TEAL_L)
    # title
    fancy_title(a, W // 2, 24, "天と淵のあいだ", 16, 2)
    sub_s = "BETWEEN THE SKY AND THE ABYSS"
    wsub = text_width(sub_s, 12)
    text(a, W // 2 - wsub // 2, 84, sub_s, TEAL_L, 12, shadow=ABYSS0)
    return a


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
