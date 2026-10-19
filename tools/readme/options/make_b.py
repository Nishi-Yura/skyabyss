"""README concept B: a vertical descent map. One strip per altitude, from the sky to the abyss, with a Minecraft-style Y gauge."""
from common import *

W = 280
BH = 72
RAIL = 28


def rail(a, y0, y1, label, tone=ABYSS2):
    a[y0:y1, 0:RAIL] = tone
    a[y0:y1, RAIL - 1] = INK
    a[y0:y1, 0] = INK
    for y in range(y0 + 2, y1, 6):
        a[y, RAIL - 5:RAIL - 1] = STONE_L if (y - y0) % 12 == 2 else STONE_D
    text(a, 3, y0 + BH // 2 - 6 if y1 - y0 <= BH else y0 + 8, label, GOLD_L, 12, shadow=ABYSS0)


def strip(zone, sub1, sub2, ylab, stops, scene, deco, tcol=WHITE, seed=1):
    a = canvas(W, BH)
    vgrad(a, stops)
    deco(a)
    rail(a, 0, BH, ylab)
    a[0, RAIL:] = INK; a[BH - 1, :] = INK; a[:, W - 1] = INK
    fancy_title(a, RAIL + 8 + text_width(zone, 24) // 2, 4, zone, 24, 1, face="biz", depth=2, bold=True)
    text(a, RAIL + 9, 42, sub1, tcol, 12, shadow=ABYSS0)
    if sub2:
        text(a, RAIL + 9, 47, sub2, tcol, 12, shadow=ABYSS0)
    if scene is not None:
        sx, sy = W - 96 - 6, (BH - 54) // 2
        a[sy - 1:sy + 55, sx - 1:sx + 97] = INK
        a[sy:sy + 54, sx:sx + 96] = scene
    return a


def clouds(a, spec, light=CLOUD, shade=CLOUD_S, dark=None):
    for (x, y, w, h) in spec:
        cloud(a, x, y, w, h, light, shade, dark)


def deco_storm(a):
    clouds(a, [(70, 14, 70, 7), (150, 12, 60, 6), (120, 62, 90, 8), (60, 64, 60, 7)], STORM_L, STORM, STORM_D)
    for (x, y) in ((110, 28), (111, 29), (112, 30), (111, 31), (112, 32), (113, 33)):
        a[y, x] = GOLD_L
    for (x, y) in ((156, 40), (157, 41), (158, 42), (157, 43)):
        a[y, x] = GOLD


def deco_cloudsea(a):
    clouds(a, [(60, 60, 90, 8), (150, 66, 100, 9), (250, 60, 60, 7), (30, 20, 34, 4)], CLOUD, CLOUD_S, CLOUD_D)


def deco_ground(a):
    gy = 56
    a[gy:, RAIL:] = MOSS
    a[gy, RAIL:] = H("5ac08a")
    noise_fill(a, RAIL, gy + 1, W - 2, BH - 2, [MOSS, MOSS_D, H("6b4a30"), H("85603e")], [3, 2, 2, 1], 8)
    a[gy + 4:, RAIL:] = H("6b4a30")
    noise_fill(a, RAIL, gy + 4, W - 2, BH - 2, [H("6b4a30"), H("85603e"), H("523820")], [4, 2, 2], 9)
    a[gy, RAIL:] = H("5ac08a")
    cloud(a, 80, 24, 34, 4, CLOUD, CLOUD_S)
    cloud(a, 220, 18, 40, 4, CLOUD, CLOUD_S)
    # camp (campfire, bell, footprints) at the ground line, drawn at 1x
    paste(a, icons.get("campfire"), 172, gy - 16)
    paste(a, icons.get("bell"), 198, gy - 16)
    paste(a, icons.get("footprint"), 140, gy - 15)
    paste(a, icons.get("footprint"), 232, gy - 15)


def deco_sea(a):
    rs = np.random.RandomState(4)
    for _ in range(40):
        x, y = rs.randint(RAIL + 4, W - 3), rs.randint(6, BH - 3)
        a[y, x:x + 3] = scenes.SEA1 if y < 28 else scenes.SEA2


def deco_under(a):
    noise_fill(a, RAIL, 0, W - 2, BH - 2, [COAL, COAL_L, STONE_DD, COAL], [4, 3, 1, 3], 11)
    rs = np.random.RandomState(12)
    for _ in range(26):
        x, y = rs.randint(RAIL + 3, W - 3), rs.randint(30, BH - 3)
        a[y, x] = EMBER if rs.rand() < .5 else EMBER_D
        if rs.rand() < .3:
            a[y, x + 1] = EMBER_L
    a[BH - 8:BH - 1, RAIL:] = EMBER_D
    a[BH - 4:BH - 1, RAIL:] = EMBER
    for x in range(RAIL, W - 1, 5):
        a[BH - 4, x] = EMBER_L


def deco_abyss(a):
    rs = np.random.RandomState(13)
    for _ in range(30):
        a[rs.randint(2, BH - 2), rs.randint(RAIL + 3, W - 3)] = [ABYSS3, TEAL_D, TEAL][rs.randint(3)]
    a[50, 180] = TEAL_L; a[49, 180] = TEAL; a[51, 180] = TEAL; a[50, 179] = TEAL; a[50, 181] = TEAL
    a[24, 232] = TEAL_L; a[23, 232] = TEAL_D; a[25, 232] = TEAL_D


def top():
    Hh = 132
    a = canvas(W, Hh)
    vgrad(a, [(22, ABYSS1), (44, STORM_D), (66, SKY1), (92, SKY2), (Hh, SKY3)])
    star_field(a, 0, 40, 40, 2, (SKY4, SKY3, WHITE))
    twinkle(a, 70, 12); twinkle(a, 220, 20, WHITE, SKY3)
    scenes.whale(a, 90, 80, 170, scenes.WHALE_HAZE, chain=False)
    clouds(a, [(60, 100, 90, 8), (160, 108, 120, 9), (250, 100, 80, 8), (110, 90, 30, 4)], CLOUD, CLOUD_S, CLOUD_D)
    a[:, 0:RAIL] = ABYSS2
    rail(a, 0, Hh, "Y320")
    a[:, 0] = INK; a[:, RAIL - 1] = INK; a[0, :] = INK; a[:, W - 1] = INK
    cx = RAIL + (W - RAIL) // 2
    fancy_title(a, cx, 18, "天と淵のあいだ", 28, 1, face="biz", depth=3, bold=True)
    s = "BETWEEN THE SKY AND THE ABYSS"
    w = text_width(s, 12)
    text(a, cx - w // 2, 62, s, TEAL_L, 12, shadow=ABYSS0)
    vw = text_width(VERSION, 12) + 8
    frame(a, W - vw - 6, 6, W - 7, 18, face=ABYSS2, hi=ABYSS3, lo=ABYSS1)
    text(a, W - vw - 2, 7, VERSION, GOLD_L, 12, shadow=ABYSS0)
    # descent arrow in the rail
    for i in range(5):
        a[Hh - 22 + i, 10 - i:11 + i] = GOLD_L if i else GOLD
    a[Hh - 34:Hh - 22, 10] = GOLD
    return save(a, "b_sky.png", 3)


def make_strips():
    out = []
    out.append(save(strip("雷雲", "巨神は、四体。", "", "Y280", [(20, STORM_D), (46, STORM), (BH, STORM_L)], scene_img("storm"), deco_storm), "b_storm.png", 3))
    out.append(save(strip("雲海", "旅の支度", "", "Y220", [(20, SKY2), (44, SKY3), (BH, SKY4)], scene_img("whale"), deco_cloudsea, tcol=WHITE), "b_clouds.png", 3))
    out.append(save(strip("地表", "はじめかた", "", "Y64", [(24, SKY3), (BH, SKY4)], None, deco_ground), "b_ground.png", 3))
    out.append(save(strip("海", "手の動き", "", "Y62", [(14, scenes.SEA1), (30, scenes.SEA2), (52, scenes.SEA3), (BH, scenes.SEA4)], scene_img("tide"), deco_sea), "b_sea.png", 3))
    out.append(save(strip("地の底", "見張り番のために", "", "Y-40", [(BH, COAL)], scene_img("ember"), deco_under, tcol=EMBER_L), "b_under.png", 3))
    out.append(save(strip("淵", "書きかけのところ", "", "Y-64", [(24, ABYSS1), (BH, ABYSS0)], None, deco_abyss, tcol=TEAL_L), "b_abyss.png", 3))
    return out


if __name__ == "__main__":
    outdir("b")
    res = [top()] + make_strips()
    for p in res:
        print(os.path.basename(p[0]), round(p[1], 1))
