"""README concept C: a minimal title-card style. A few large, quiet plates on deep navy."""
from common import *

W = 280
PALE = H("cfe8f0")


def night(Hh, seed, stars=40, top=ABYSS0, bot=ABYSS2):
    a = canvas(W, Hh)
    third = Hh // 3
    vgrad(a, [(third, top), (2 * third, ABYSS1), (Hh, bot)])
    star_field(a, 0, int(Hh * .7), stars, seed, (SKY4, SKY3, TEAL_L, ABYSS3))
    return a


def hairline_frame(a, m=7, col=TEAL_D, accent=GOLD):
    h, w = a.shape[:2]
    a[m, m:w - m] = col; a[h - m - 1, m:w - m] = col
    a[m:h - m, m] = col; a[m:h - m, w - m - 1] = col
    for (x, y) in ((m, m), (w - m - 1, m), (m, h - m - 1), (w - m - 1, h - m - 1)):
        a[y - 1:y + 2, x - 1:x + 2] = accent
        a[y, x] = GOLD_L


def title():
    Hh = 170
    a = night(Hh, 31, 46)
    silhouette_whale(a, 30, 84, 220, ABYSS3, ABYSS2)
    cloud(a, 60, 150, 120, 9, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 220, 152, 110, 9, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 140, 160, 130, 8, ABYSS2, ABYSS1, ABYSS0)
    twinkle(a, 40, 24, WHITE, SKY3); twinkle(a, 236, 40, WHITE, SKY3)
    fancy_title(a, W // 2, 36, "天と淵のあいだ", 26, 1, face="biz", light=WHITE, mid=PALE, dark=SKY3, side=ABYSS3, depth=2, bold=True)
    s = "BETWEEN THE SKY AND THE ABYSS"
    w = text_width(s, 12)
    text(a, W // 2 - w // 2, 72, s, TEAL_L, 12, shadow=ABYSS0)
    hairline_frame(a)
    vw = text_width(VERSION, 12)
    text(a, W // 2 - vw // 2, Hh - 24, VERSION, GOLD_L, 12, shadow=ABYSS0)
    return save(a, "c_title.png", 3)


def arch(sc, w, h):
    """Crop the centre of a 96x54 scene to w x h with an arched (rounded) top."""
    x0 = (sc.shape[1] - w) // 2
    c = sc[:h, x0:x0 + w].copy()
    r = w // 2
    for y in range(r):
        dx = int(round(r - (r * r - (r - y) ** 2) ** 0.5))
        c[y, :dx] = TRANSP; c[y, w - dx:] = TRANSP
    return c


def four():
    Hh = 150
    a = night(Hh, 32, 30)
    cw, gap = 58, 14
    x0 = (W - (4 * cw + 3 * gap)) // 2
    items = [("whale", "一章", "岩鯨", "空を泳ぐ", H("d8d8d8")), ("tide", "二章", "潮殻", "海を歩く", CORAL_L),
             ("storm", "三章", "雷翼", "嵐をまとう", GOLD_L), ("ember", "四章", "余燼", "地を泳ぐ", EMBER_L)]
    for i, (sc, ch, nm, ac, col) in enumerate(items):
        x = x0 + i * (cw + gap)
        win = arch(scene_img(sc), cw, 54)
        m = win[:, :, 3] > 0
        pad = np.zeros((54 + 4, cw + 4), bool)
        pad[2:56, 2:2 + cw] = m
        ring = dilate(pad, 1) & ~pad
        full = canvas(cw + 6, 54 + 6)
        pm = np.zeros((60, cw + 6), bool)
        pm[3:57, 3:3 + cw] = m
        rm = dilate(pm, 1) & ~pm
        full[rm] = col
        paste(full, win, 3, 3)
        paste(a, full, x - 3, 14)
        w1 = text_width(ch, 12)
        text(a, x + cw // 2 - w1 // 2, 82, ch, col, 12, shadow=ABYSS0)
        w2 = text_width(nm, 16)
        text(a, x + cw // 2 - w2 // 2, 98, nm, WHITE, 16, shadow=ABYSS0)
        w3 = text_width(ac, 12)
        text(a, x + cw // 2 - w3 // 2, 120, ac, SKY3, 12, shadow=ABYSS0)
    hairline_frame(a, 4)
    return save(a, "c_four.png", 3)


def pack():
    Hh = 110
    a = night(Hh, 33, 22, ABYSS1, ABYSS2)
    cols = [("Minecraft", "1.21.1"), ("NeoForge", "1.21.1"), ("GeckoLib", "必須"), ("Java", "21")]
    cw = W // 4
    for i, (lab, val) in enumerate(cols):
        cx = cw * i + cw // 2
        w = text_width(val, 16)
        text(a, cx - w // 2, 26, val, WHITE, 16, shadow=ABYSS0)
        w = text_width(lab, 12)
        text(a, cx - w // 2, 50, lab, TEAL_L, 12, shadow=ABYSS0)
        if i:
            a[22:62, cw * i] = ABYSS3
    s = "みんなで遊ぶなら、同じ jar を。"
    w = text_width(s, 12)
    text(a, W // 2 - w // 2, 80, s, GOLD_L, 12, shadow=ABYSS0)
    hairline_frame(a, 4, TEAL_D)
    return save(a, "c_pack.png", 3)


def end():
    Hh = 96
    a = night(Hh, 34, 30, ABYSS0, ABYSS3)
    silhouette_whale(a, 96, 52, 96, ABYSS3, ABYSS2)
    cloud(a, 40, 94, 90, 7, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 244, 94, 100, 7, ABYSS3, ABYSS2, ABYSS1)
    for i, s in enumerate(("空と地の底のあいだで、", "あなたを待っているものがあります。")):
        w = text_width(s, 12)
        text(a, W // 2 - w // 2, 14 + i * 15, s, PALE, 12, shadow=ABYSS0)
    twinkle(a, 30, 24, WHITE, SKY3); twinkle(a, 250, 18, WHITE, SKY3)
    return save(a, "c_end.png", 3)


def ornament():
    Hh = 14
    a = canvas(W, Hh)
    cx, cy = W // 2, Hh // 2
    for dx, col in ((-24, TEAL_D), (0, GOLD), (24, TEAL_D)):
        x = cx + dx
        a[cy, x - 2:x + 3] = col
        a[cy - 1, x - 1:x + 2] = col; a[cy + 1, x - 1:x + 2] = col
        a[cy - 2, x] = col; a[cy + 2, x] = col
    a[cy, cx] = GOLD_L
    # make the ornament readable on both light and dark GitHub pages
    return save(a, "c_orn.png", 3)


if __name__ == "__main__":
    outdir("c")
    for f in (title, four, pack, end, ornament):
        p = f()
        print(os.path.basename(p[0]), round(p[1], 1))
