# -*- coding: utf-8 -*-
"""武器の技の書き換え (rpg/skill/Rune の書き換え。武器を主の手に持つと効く) の絵 (icons4.png、256 x 256、32 x 32 の升目 8 x 8) を書く。
Skill の絵の番号 300 + n (SkillHud.drawIcon)。n は Rune の書き換えの並びの順 (Rune.rewriteIndex と同じ)。
2026-11-09 の声「武器のスキル変化、詳細がわからないし、属性変わってるように見えて、見た目やアイコンも変わってないので、何がスキル変化かわからない」:
元の技の絵を、書き換えの出どころの色 (潮 = 青・雷 = 黄・熾 = 橙・鯨 = 石・月 = 銀・神器 = 金・深淵 = 紫・逸品 = 銅) に塗り直し、
縁をその色にして、右下に出どころの小さな印 (波・稲妻・炎・岩・月・星・目・刃) を付ける。

    python -I tools/gen/ui/gen_rewrite_icons.py
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent.parent
SKILL = ROOT / "src/main/java/jp/revati/skyabyss/rpg/skill/Skill.java"
RUNE = ROOT / "src/main/java/jp/revati/skyabyss/rpg/skill/Rune.java"
ICONS = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skill/icons.png"
ICONS2 = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skill/icons2.png"
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skill/icons4.png"

SOURCES = {
    "tide": ((70, 160, 240), "wave"), "storm": ((250, 220, 90), "bolt"), "ember": ((250, 120, 50), "flame"),
    "whale": ((180, 160, 120), "rock"), "moon": ((200, 210, 236), "moon"), "divine": ((250, 214, 120), "star"),
    "abyss": ((170, 110, 240), "eye"), "relic": ((210, 150, 90), "blade"),
}
INK = (12, 14, 20, 255)


def skills():
    names = re.findall(r'^    ([A-Z_0-9]+)\("', SKILL.read_text(encoding="utf-8"), re.M)
    return names


def icon_index(names, skill):
    o = names.index(skill)
    tb = names.index("THUNDER_BOLT")
    return o if o < 30 else (105 + (o - 30) if o < tb else 125 + (o - tb))


def rewrites():
    out = []
    for m in re.finditer(r'^    ([A-Z_0-9]+)\("([a-z_0-9]+)", Skill\.([A-Z_0-9]+), (\d+), "([a-z_0-9]+)"\)', RUNE.read_text(encoding="utf-8"), re.M):
        out.append((m.group(1), m.group(2), m.group(3), m.group(5)))
    return out


def source(rid, weapon):
    for key in ("tide", "storm", "ember", "whale", "moon", "divine"):
        if rid.startswith(key) or weapon.startswith(key) or ("_" + key + "_") in weapon:
            return key
    if weapon.startswith("abyss") or rid.startswith("abyss"):
        for key in ("tide", "storm", "ember", "whale"):
            if "_" + key + "_" in weapon:
                return key
        return "abyss"
    return "relic"


def base_icon(index, a, b):
    if index >= 100:
        n = index - 100
        return b.crop(((n % 8) * 32, (n // 8) * 32, (n % 8) * 32 + 32, (n // 8) * 32 + 32))
    return a.crop(((index % 8) * 32, (index // 8) * 32, (index % 8) * 32 + 32, (index // 8) * 32 + 32))


def recolor(img, rgb):
    """円盤の中は出どころの色に寄せ (明るさは元のまま)、縁の輪 (半径 12〜15) はその色の二色に"""
    out = img.copy()
    px = out.load()
    for y in range(32):
        for x in range(32):
            r, g, bl, al = px[x, y]
            if al == 0:
                continue
            d = math.hypot(x - 15.5, y - 15.5)
            lum = (0.3 * r + 0.59 * g + 0.11 * bl) / 255
            if 12.0 <= d < 14.9:
                k = 1.0 if (x + y) < 31 else 0.62
                px[x, y] = (int(rgb[0] * k), int(rgb[1] * k), int(rgb[2] * k), 255)
            elif d < 12.0:
                t = 0.45
                nr = int(r * (1 - t) + rgb[0] * lum * t * 1.3)
                ng = int(g * (1 - t) + rgb[1] * lum * t * 1.3)
                nb = int(bl * (1 - t) + rgb[2] * lum * t * 1.3)
                px[x, y] = (min(255, nr), min(255, ng), min(255, nb), al)
    return out


def badge(img, rgb, glyph):
    d = ImageDraw.Draw(img)
    cx, cy, r = 25, 25, 6
    d.ellipse((cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1), fill=INK)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=tuple(int(c * 0.45) for c in rgb) + (255,))
    c = tuple(rgb) + (255,)
    w = (250, 250, 250, 255)
    if glyph == "wave":
        d.arc((cx - 4, cy - 3, cx + 0, cy + 2), 180, 360, fill=c, width=1)
        d.arc((cx, cy - 3, cx + 4, cy + 2), 0, 180, fill=c, width=1)
        d.line((cx - 4, cy + 3, cx + 4, cy + 3), fill=w)
    elif glyph == "bolt":
        d.polygon([(cx + 1, cy - 5), (cx - 3, cy + 1), (cx, cy + 1), (cx - 1, cy + 5), (cx + 3, cy - 1), (cx, cy - 1)], fill=c)
    elif glyph == "flame":
        d.polygon([(cx, cy - 5), (cx + 3, cy + 1), (cx + 1, cy + 4), (cx - 1, cy + 4), (cx - 3, cy + 1)], fill=c)
        d.polygon([(cx, cy - 1), (cx + 1, cy + 3), (cx - 1, cy + 3)], fill=w)
    elif glyph == "rock":
        d.polygon([(cx - 4, cy + 3), (cx - 2, cy - 2), (cx + 1, cy - 4), (cx + 4, cy + 3)], fill=c)
        d.line((cx - 1, cy - 1, cx + 1, cy + 2), fill=INK)
    elif glyph == "moon":
        d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=c)
        d.ellipse((cx - 2, cy - 5, cx + 5, cy + 2), fill=tuple(int(x * 0.45) for x in rgb) + (255,))
    elif glyph == "star":
        pts = []
        for k in range(10):
            a = math.radians(-90 + k * 36)
            rr = 5 if k % 2 == 0 else 2
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        d.polygon(pts, fill=c)
    elif glyph == "eye":
        d.ellipse((cx - 5, cy - 2, cx + 5, cy + 2), fill=c)
        d.ellipse((cx - 1, cy - 2, cx + 1, cy + 2), fill=INK)
    else:
        d.line((cx - 4, cy + 4, cx + 4, cy - 4), fill=c, width=2)
        d.line((cx - 4, cy + 1, cx - 1, cy + 4), fill=w)


def main():
    names = skills()
    a = Image.open(ICONS).convert("RGBA")
    b = Image.open(ICONS2).convert("RGBA")
    rows = rewrites()
    assert len(rows) <= 64, len(rows)
    sheet = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for n, (const, rid, skill, weapon) in enumerate(rows):
        rgb, glyph = SOURCES[source(rid, weapon)]
        img = recolor(base_icon(icon_index(names, skill), a, b), rgb)
        badge(img, rgb, glyph)
        sheet.paste(img, ((n % 8) * 32, (n // 8) * 32))
    sheet.save(OUT)
    print("rewrites", len(rows), "->", OUT)


if __name__ == "__main__":
    main()
