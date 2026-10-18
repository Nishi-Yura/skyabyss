"""0.2.0 additions: version plaque, chapter strip, rarity strip, inline section icons."""
import numpy as np
from pix import *
import icons, icons2  # noqa: F401  (icons2 registers the new icons)


def version_plaque(a, x, y, label="v0.2.1"):
    w = text_width(label, 12) + 8
    h = 13
    frame(a, x, y, x + w - 1, y + h - 1, face=ABYSS2, hi=ABYSS3, lo=ABYSS1)
    text(a, x + 4, y + 1, label, GOLD_L, 12, shadow=ABYSS0)
    return w


def chapters(fname="chapters.png"):
    W, Hh = 277, 48
    a = canvas(W, Hh)
    items = [("序章", "空の影", SKY3, SKY2), ("一章", "岩鯨", H("d8d8d8"), ROCK_L), ("二章", "潮殻", CORAL_L, CORAL),
             ("三章", "雷翼", GOLD_L, GOLD), ("四章", "余燼", EMBER_L, EMBER), ("五章", "続く", None, None)]
    tw, gap = 42, 5
    for i, (c, n, tc, ac) in enumerate(items):
        x = i * (tw + gap)
        if ac is not None:
            frame(a, x, 0, x + tw - 1, Hh - 1, face=H("3a3a40"), hi=H("8a8a94"), lo=H("1a1a1e"))
            a[2:5, x + 2:x + tw - 2] = ac
            cw = text_width(c, 12); text(a, x + tw // 2 - cw // 2, 10, c, WHITE, 12, shadow=H("1a1a1e"))
            nw = text_width(n, 12); text(a, x + tw // 2 - nw // 2, 26, n, tc, 12, shadow=H("1a1a1e"))
        else:
            for xx in range(x, x + tw, 3):
                a[0, xx] = a[Hh - 1, xx] = STONE_D
            for yy in range(0, Hh, 3):
                a[yy, x] = a[yy, x + tw - 1] = STONE_D
            cw = text_width(c, 12); text(a, x + tw // 2 - cw // 2, 10, c, STONE_L, 12)
            nw = text_width(n, 12); text(a, x + tw // 2 - nw // 2, 26, n, STONE, 12)
        if i < len(items) - 1:
            a[Hh // 2, x + tw:x + tw + gap] = STONE_L
    return save(a, fname, 3)


def tint_sword(blade, edge, glow=0):
    s = icons.sword().copy()
    for (src, dst) in ((H("e8eff7"), blade), (H("9fb0c4"), edge)):
        m = np.all(s == np.array(src), axis=2)
        s[m] = dst
    if glow:
        for (x, y) in ((11, 5), (9, 3), (7, 7), (5, 9))[:glow * 2]:
            s[y, x] = TEAL_L
    return s


def rarity(fname="rarity.png"):
    W, Hh = 280, 62
    a = canvas(W, Hh)
    frame(a, 0, 0, W - 1, Hh - 1, face=H("c6c6c6"), hi=H("ffffff"), lo=H("555555"))
    a[2:Hh - 2, 2:W - 2] = H("c6c6c6")
    cols = [("コモン", WHITE, tint_sword(H("e8eff7"), H("9fb0c4"))),
            ("アンコモン", H("55ff55"), tint_sword(H("bfe8b0"), H("5aa05a"))),
            ("レア", H("5599ff"), tint_sword(H("a8c8ff"), H("3d6fd0"))),
            ("伝説", H("ffaa00"), tint_sword(GOLD_L, GOLD_D))]
    colw = 62
    for i, (name, col, ic) in enumerate(cols):
        x = 8 + i * (colw + 6)
        slot(a, x + colw // 2 - 9, 6, 18)
        paste(a, ic, x + colw // 2 - 8, 7)
        nw = text_width(name, 12)
        text(a, x + colw // 2 - nw // 2, 28, name, col, 12, shadow=H("3f3f3f"))
    for j, (lab, g) in enumerate((("+0", 0), ("+5", 1), ("+10", 2))):
        x = 10 + j * 46
        slot(a, x, 42, 18)
        paste(a, tint_sword(H("e8eff7"), H("9fb0c4"), g), x + 1, 43)
        text(a, x + 21, 46, lab, H("3a3a3a"), 12)
    text(a, 152, 46, "強化で見た目が変わる", H("3a3a3a"), 12)
    return save(a, fname, 3)


def icon(name, fname):
    return save(icons.get(name), fname, 3)


def make_all():
    out = [chapters(), rarity()]
    for n in ("grave", "clapper", "trophy", "flag", "ring", "gem", "jar", "scroll",
              "party", "tablet", "chestplate", "whale_icon", "book", "bell"):
        out.append(icon(n, "icon_%s.png" % n))
    return out
