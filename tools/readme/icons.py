"""16x16 item-style icons, drawn with primitives then auto-outlined."""
from PIL import Image, ImageDraw
import numpy as np
from pix import *


def _new():
    img = Image.new("RGBA", (16, 16), TRANSP)
    return img, ImageDraw.Draw(img)


def _fin(img, rim=INK):
    a = np.array(img)
    outline_alpha(a, rim)
    return a


def sword():
    img, d = _new()
    d.line((12, 2, 5, 9), fill=H("e8eff7"), width=2)
    d.line((13, 3, 6, 10), fill=H("9fb0c4"), width=1)
    d.line((13, 2, 13, 2), fill=H("ffffff"))
    d.line((3, 7, 8, 12), fill=GOLD, width=1)
    d.line((4, 7, 9, 12), fill=GOLD_D, width=1)
    d.line((4, 10, 2, 12), fill=H("7a4a28"), width=2)
    d.point((1, 13), fill=GOLD)
    return _fin(img)


def bow():
    img, d = _new()
    d.arc((3, 1, 13, 14), 270, 90, fill=H("8a5a2e"), width=2)
    d.line((8, 2, 8, 13), fill=H("e8dcc0"))
    d.line((2, 7, 11, 7), fill=H("b98a55"))
    d.polygon([(12, 7), (10, 5), (10, 9)], fill=H("c3ccd6"))
    d.polygon([(2, 7), (1, 5), (3, 5)], fill=H("d8d8d8"))
    d.polygon([(2, 8), (1, 10), (3, 10)], fill=H("d8d8d8"))
    return _fin(img)


def book(cover=TEAL_D, mark=H("c8453c"), glyph=TEAL_L):
    img, d = _new()
    d.rectangle((3, 2, 13, 13), fill=cover)
    d.rectangle((3, 2, 4, 13), fill=ABYSS3)
    d.rectangle((5, 3, 12, 3), fill=glyph)
    d.rectangle((13, 3, 13, 12), fill=BONE)
    d.rectangle((5, 13, 13, 13), fill=BONE_D)
    d.rectangle((7, 5, 10, 8), fill=GOLD)
    d.rectangle((8, 6, 9, 7), fill=cover)
    d.rectangle((11, 9, 11, 14), fill=mark)
    return _fin(img)


def bell():
    img, d = _new()
    d.ellipse((3, 2, 12, 9), fill=GOLD)
    d.polygon([(3, 6), (12, 6), (14, 12), (1, 12)], fill=GOLD)
    d.rectangle((1, 12, 14, 12), fill=GOLD_D)
    d.line((5, 3, 4, 8), fill=GOLD_L)
    d.line((12, 8, 13, 11), fill=GOLD_D)
    d.rectangle((7, 0, 8, 1), fill=GOLD_D)
    d.ellipse((6, 12, 9, 14), fill=H("7a4a28"))
    return _fin(img)


def campfire():
    img, d = _new()
    d.line((1, 13, 14, 10), fill=H("6b4423"), width=2)
    d.line((1, 10, 14, 13), fill=H("8a5a2e"), width=2)
    d.polygon([(8, 0), (12, 7), (11, 11), (5, 11), (3, 7), (6, 5)], fill=EMBER)
    d.polygon([(8, 4), (10, 8), (9, 11), (6, 11), (5, 8), (7, 7)], fill=EMBER_L)
    d.polygon([(8, 7), (9, 10), (7, 11), (6, 10)], fill=GOLD_L)
    return _fin(img)


def chain():
    img, d = _new()
    for i, (x, y) in enumerate(((1, 1), (5, 5), (9, 9))):
        d.rectangle((x, y, x + 5, y + 5), fill=H("aab4c0"))
        d.rectangle((x + 2, y + 2, x + 3, y + 3), fill=TRANSP)
        d.line((x, y, x, y + 4), fill=H("dfe6ee"))
        d.line((x, y + 5, x + 5, y + 5), fill=H("6f7a88"))
        d.line((x + 5, y + 1, x + 5, y + 5), fill=H("6f7a88"))
    return _fin(img)


def chestplate(main=ROCK, lite=ROCK_L, dark=ROCK_D, trim=BONE):
    img, d = _new()
    d.polygon([(2, 2), (6, 2), (8, 4), (10, 2), (14, 2), (15, 7), (12, 8), (12, 14), (4, 14), (4, 8), (1, 7)], fill=main)
    d.rectangle((4, 8, 11, 14), fill=main)
    d.polygon([(6, 2), (8, 4), (10, 2), (10, 3), (8, 6), (6, 3)], fill=dark)
    d.line((2, 2, 1, 6), fill=lite)
    d.line((14, 3, 15, 7), fill=dark)
    d.line((5, 9, 5, 13), fill=lite)
    d.line((4, 14, 11, 14), fill=dark)
    d.rectangle((7, 9, 8, 12), fill=trim)
    return _fin(img)


def anvil():
    img, d = _new()
    d.polygon([(1, 3), (14, 3), (12, 6), (10, 7), (10, 9), (12, 12), (12, 14), (3, 14), (3, 12), (5, 9), (5, 7), (3, 6)], fill=H("6c717c"))
    d.line((1, 3, 14, 3), fill=H("a7adb8"))
    d.line((3, 14, 12, 14), fill=H("40444d"))
    d.line((5, 8, 5, 12), fill=H("8a909b"))
    d.rectangle((6, 4, 7, 4), fill=TEAL_L)
    d.point((8, 1), fill=TEAL_L); d.point((10, 0), fill=WHITE); d.point((11, 2), fill=TEAL_L)
    return _fin(img)


def footprint():
    img, d = _new()
    d.ellipse((4, 6, 11, 14), fill=ROCK)
    d.ellipse((5, 7, 10, 13), fill=ROCK_D)
    d.rectangle((2, 3, 3, 5), fill=ROCK_L)
    d.rectangle((5, 1, 6, 3), fill=ROCK_L)
    d.rectangle((8, 1, 9, 3), fill=ROCK_L)
    d.rectangle((11, 3, 12, 5), fill=ROCK_L)
    d.rectangle((6, 9, 7, 11), fill=SKY3)
    d.point((9, 10), fill=SKY3)
    return _fin(img)


def torn_page():
    img, d = _new()
    d.polygon([(3, 1), (12, 1), (13, 3), (12, 5), (13, 7), (12, 9), (13, 11), (11, 14), (3, 14)], fill=BONE)
    d.polygon([(3, 12), (11, 14), (3, 14)], fill=BONE_D)
    for y in (4, 6, 8, 10):
        d.line((5, y, 10, y), fill=H("7a6a4a"))
    d.line((5, 4, 7, 4), fill=TEAL_D)
    return _fin(img)


def torch():
    img, d = _new()
    d.line((5, 14, 9, 7), fill=H("7a4a28"), width=3)
    d.line((6, 14, 10, 7), fill=H("a86f3c"), width=1)
    d.polygon([(10, 0), (13, 4), (11, 7), (8, 7), (7, 4), (9, 3)], fill=EMBER)
    d.polygon([(10, 3), (11, 5), (10, 7), (9, 7), (8, 5)], fill=GOLD_L)
    return _fin(img)


def party():
    img, d = _new()
    for x, c in ((1, TEAL), (7, CORAL)):
        d.ellipse((x + 1, 2, x + 6, 7), fill=H("e0b890"))
        d.rectangle((x + 1, 2, x + 6, 3), fill=H("5a3a22"))
        d.rectangle((x, 9, x + 7, 14), fill=c)
        d.rectangle((x + 2, 8, x + 5, 8), fill=H("e0b890"))
    return _fin(img)


def tablet():
    img, d = _new()
    d.polygon([(3, 14), (3, 4), (6, 1), (10, 1), (13, 4), (13, 14)], fill=H("5d6874"))
    d.line((3, 4, 6, 1), fill=H("8896a4"))
    d.line((13, 6, 13, 14), fill=H("3c444e"))
    for (x, y) in ((6, 5), (9, 5), (6, 8), (8, 8), (10, 8), (7, 11), (10, 11)):
        d.rectangle((x, y, x + 1, y), fill=TEAL_L)
    return _fin(img)


def slash():
    img, d = _new()
    d.arc((-4, 0, 14, 17), 280, 35, fill=H("e8f4ff"), width=3)
    d.arc((-6, 0, 14, 17), 290, 30, fill=SKY3, width=1)
    d.point((13, 12), fill=WHITE)
    return _fin(img)


def volley():
    img, d = _new()
    for (x, y) in ((1, 3), (3, 7), (1, 11)):
        d.line((x, y, x + 9, y), fill=H("b98a55"))
        d.polygon([(x + 12, y), (x + 9, y - 2), (x + 9, y + 2)], fill=H("c3ccd6"))
        d.point((x, y - 1), fill=H("e8e8e8")); d.point((x, y + 1), fill=H("e8e8e8"))
    return _fin(img)


def guard():
    img, d = _new()
    d.polygon([(2, 2), (14, 2), (14, 8), (8, 15), (2, 8)], fill=STORM_L)
    d.polygon([(8, 2), (14, 2), (14, 8), (8, 15)], fill=STORM)
    d.line((2, 2, 2, 8), fill=SKY3)
    d.line((8, 4, 8, 12), fill=GOLD)
    d.line((4, 7, 12, 7), fill=GOLD)
    return _fin(img)


def dash():
    img, d = _new()
    d.polygon([(7, 1), (13, 8), (7, 15), (9, 8)], fill=EMBER_L)
    d.polygon([(7, 1), (9, 8), (7, 15)], fill=EMBER)
    d.line((0, 5, 5, 5), fill=H("d8e4f0")); d.line((1, 8, 6, 8), fill=H("d8e4f0")); d.line((0, 11, 5, 11), fill=H("d8e4f0"))
    return _fin(img)


def star():
    img, d = _new()
    d.polygon([(8, 0), (10, 6), (15, 8), (10, 10), (8, 15), (6, 10), (1, 8), (6, 6)], fill=GOLD_L)
    d.polygon([(8, 3), (9, 7), (8, 12), (7, 7)], fill=WHITE)
    return _fin(img)


def person():
    img, d = _new()
    d.polygon([(8, 1), (12, 4), (13, 14), (3, 14), (4, 4)], fill=H("5a3a6a"))
    d.ellipse((5, 4, 10, 9), fill=H("e0b890"))
    d.rectangle((5, 4, 10, 5), fill=H("3a2450"))
    d.line((3, 14, 13, 14), fill=H("3a2450"))
    d.point((6, 7), fill=INK); d.point((9, 7), fill=INK)
    return _fin(img)


def cloud_shadow():
    img, d = _new()
    d.ellipse((1, 3, 8, 9), fill=CLOUD); d.ellipse((5, 1, 12, 8), fill=CLOUD); d.rectangle((2, 7, 14, 10), fill=CLOUD)
    d.rectangle((2, 10, 14, 10), fill=CLOUD_S)
    d.ellipse((3, 11, 14, 14), fill=ABYSS2)
    return _fin(img)


def whale_icon():
    img, d = _new()
    d.ellipse((2, 4, 14, 12), fill=ROCK)
    d.polygon([(2, 8), (0, 3), (3, 6)], fill=ROCK)
    d.polygon([(0, 13), (2, 8), (3, 11)], fill=ROCK)
    d.rectangle((3, 10, 13, 11), fill=BONE)
    d.arc((4, 0, 8, 5), 180, 360, fill=H("66ecff")); d.arc((9, 1, 12, 5), 180, 360, fill=H("66ecff"))
    d.rectangle((13, 2, 13, 4), fill=ROCK_L)
    d.point((12, 7), fill=WHITE)
    return _fin(img)


ALL = {
    "sword": sword, "bow": bow, "book": book, "bell": bell, "campfire": campfire, "chain": chain,
    "chestplate": chestplate, "anvil": anvil, "footprint": footprint, "torn_page": torn_page, "torch": torch,
    "party": party, "tablet": tablet, "slash": slash, "volley": volley, "guard": guard, "dash": dash, "star": star, "person": person, "cloud_shadow": cloud_shadow, "whale_icon": whale_icon,
}


def get(name):
    return ALL[name]()


def armor_set():
    return [
        chestplate(ROCK, ROCK_L, ROCK_D, BONE),
        chestplate(CORAL, CORAL_L, CORAL_D, MOSS),
        chestplate(STORM, STORM_L, STORM_D, GOLD),
        chestplate(COAL_L, H("4a4042"), COAL, EMBER),
    ]
