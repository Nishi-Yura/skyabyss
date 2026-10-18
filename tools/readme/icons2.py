"""Extra 16x16 icons for the 0.2.0 README (grave, cinema, trophy, flag, cog, ring, gem, warning, scroll, jar)."""
from PIL import Image, ImageDraw
from pix import *
from icons import _new, _fin, ALL


def grave():
    img, d = _new()
    d.rectangle((1, 13, 14, 14), fill=MOSS_D)
    d.rectangle((2, 13, 5, 13), fill=MOSS); d.rectangle((10, 13, 13, 13), fill=MOSS)
    d.polygon([(4, 13), (4, 5), (6, 2), (9, 2), (11, 5), (11, 13)], fill=ROCK)
    d.line((4, 5, 6, 2), fill=ROCK_L); d.line((4, 6, 4, 13), fill=ROCK_L)
    d.line((11, 6, 11, 13), fill=ROCK_D)
    d.rectangle((7, 4, 8, 9), fill=ROCK_D); d.rectangle((6, 6, 9, 7), fill=ROCK_D)
    d.point((5, 12), fill=MOSS); d.point((6, 12), fill=MOSS); d.point((10, 12), fill=MOSS)
    return _fin(img)


def clapper():
    img, d = _new()
    d.rectangle((1, 6, 14, 14), fill=H("3a3f4a"))
    d.rectangle((1, 6, 14, 7), fill=H("565c6a"))
    d.polygon([(1, 1), (13, 0), (14, 5), (2, 6)], fill=H("2a2e38"))
    for x in (2, 6, 10):
        d.polygon([(x, 1), (x + 2, 1), (x + 3, 5), (x + 1, 5)], fill=WHITE)
    d.polygon([(6, 9), (6, 13), (10, 11)], fill=TEAL_L)
    return _fin(img)


def trophy():
    img, d = _new()
    d.polygon([(4, 1), (12, 1), (11, 8), (9, 10), (7, 10), (5, 8)], fill=GOLD)
    d.rectangle((4, 1, 5, 6), fill=GOLD_L)
    d.arc((0, 1, 5, 7), 90, 270, fill=GOLD_D, width=1); d.arc((10, 1, 15, 7), 270, 90, fill=GOLD_D, width=1)
    d.rectangle((7, 10, 8, 12), fill=GOLD_D)
    d.rectangle((4, 13, 11, 14), fill=H("7a4a28")); d.line((4, 13, 11, 13), fill=H("a86f3c"))
    d.point((7, 4), fill=WHITE)
    return _fin(img)


def flag():
    img, d = _new()
    d.line((3, 1, 3, 14), fill=H("7a4a28"), width=2)
    d.polygon([(5, 1), (14, 3), (11, 6), (14, 9), (5, 8)], fill=CORAL)
    d.polygon([(5, 1), (14, 3), (12, 4), (5, 4)], fill=CORAL_L)
    d.line((5, 8, 11, 6), fill=CORAL_D)
    return _fin(img)


def cog():
    img, d = _new()
    for (x, y) in ((6, 0), (6, 13), (0, 6), (13, 6), (2, 2), (11, 2), (2, 11), (11, 11)):
        d.rectangle((x, y, x + 2, y + 2), fill=H("8d939c"))
    d.ellipse((2, 2, 13, 13), fill=H("8d939c"))
    d.ellipse((3, 3, 12, 12), fill=H("b6bcc6"))
    d.ellipse((6, 6, 9, 9), fill=ABYSS2)
    d.line((4, 4, 4, 8), fill=WHITE)
    return _fin(img)


def ring():
    img, d = _new()
    d.ellipse((2, 4, 13, 14), outline=GOLD, width=2)
    d.arc((2, 4, 13, 14), 180, 270, fill=GOLD_L, width=2)
    d.polygon([(8, 0), (11, 3), (8, 6), (5, 3)], fill=TEAL_L)
    d.polygon([(8, 0), (11, 3), (8, 3)], fill=WHITE)
    d.polygon([(8, 3), (11, 3), (8, 6)], fill=TEAL)
    return _fin(img)


def gem():
    img, d = _new()
    d.polygon([(3, 2), (12, 2), (15, 6), (8, 14), (1, 6)], fill=SKY2)
    d.polygon([(3, 2), (8, 2), (8, 14), (1, 6)], fill=SKY3)
    d.polygon([(8, 2), (12, 2), (15, 6), (8, 14)], fill=SKY1)
    d.line((1, 6, 15, 6), fill=SKY4)
    d.point((4, 3), fill=WHITE); d.point((5, 3), fill=WHITE)
    return _fin(img)


def warning():
    img, d = _new()
    d.polygon([(8, 1), (15, 14), (1, 14)], fill=GOLD)
    d.polygon([(8, 1), (15, 14), (8, 14)], fill=GOLD_D)
    d.rectangle((7, 5, 8, 10), fill=INK); d.rectangle((7, 12, 8, 12), fill=INK)
    return _fin(img)


def jar():
    img, d = _new()
    d.rectangle((2, 4, 13, 14), fill=H("a86f3c"))
    d.rectangle((2, 4, 13, 5), fill=H("c88a4c")); d.rectangle((2, 13, 13, 14), fill=H("7a4a28"))
    d.rectangle((1, 2, 14, 4), fill=H("7a4a28")); d.rectangle((1, 2, 14, 2), fill=H("a86f3c"))
    d.rectangle((6, 7, 9, 10), fill=GOLD); d.rectangle((7, 8, 8, 9), fill=GOLD_L)
    return _fin(img)


def scroll():
    img, d = _new()
    d.rectangle((3, 3, 12, 13), fill=BONE)
    d.rectangle((3, 12, 12, 13), fill=BONE_D)
    d.ellipse((1, 1, 5, 5), fill=BONE_D); d.ellipse((10, 11, 14, 15), fill=BONE_D)
    for y in (5, 7, 9):
        d.line((5, y, 10, y), fill=H("7a6a4a"))
    d.line((5, 5, 7, 5), fill=TEAL_D)
    return _fin(img)


def helm():
    img, d = _new()
    d.polygon([(3, 14), (2, 7), (4, 3), (8, 1), (12, 3), (14, 7), (13, 14), (10, 14), (10, 10), (6, 10), (6, 14)], fill=H("aab4c0"))
    d.polygon([(8, 1), (12, 3), (14, 7), (13, 14), (10, 14), (10, 10), (8, 10)], fill=H("8892a0"))
    d.line((3, 7, 4, 3), fill=WHITE)
    d.rectangle((6, 6, 10, 7), fill=ABYSS1)
    return _fin(img)


ALL.update({"grave": grave, "clapper": clapper, "trophy": trophy, "flag": flag, "cog": cog, "ring": ring,
            "gem": gem, "warning": warning, "jar": jar, "scroll": scroll, "helm": helm})
