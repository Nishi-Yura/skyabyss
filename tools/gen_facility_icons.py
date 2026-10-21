# -*- coding: utf-8 -*-
"""都の施設の番人の頭の上の印 (16x24 のドット絵、役ごとに 1 つ) を書く。描き方は tools/gen_npc_marker_icons.py と同じ
(暗い縁取り、左上の明るい縁、右下の影)。色は淡い骨の色だけにして、話の印 (金の「!」・青緑の巻物) と見分ける。
使い方: python tools/gen_facility_icons.py
出力: src/main/resources/assets/skyabyss/textures/gui/npc_marker/fac_<役>.png (15 枚)
      docs/review/npc_roughs/facility_icons.png (6 倍の一覧。最後に今の話の印を並べて比べる)
"""
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/npc_marker"
REVIEW = ROOT / "docs/review/npc_roughs"
W, H = 16, 24
OUTLINE = (26, 18, 8, 255)
# 淡い骨の色 (吹き出しの PALE より少しくすませる)
BONE = {"base": (204, 192, 164), "light": (236, 228, 204), "dark": (132, 120, 98)}

# ---- 形 ("#" = 塗る) と、縁取りの色で抜く点 (列, 行)
BANNER = ["##........", "##########", "##########", "##########", "##########", "##########", "##########",
          "######..##", "#####....#", "##........", "##........", "##........", "##........", "##........",
          "##........", "####......"]
BANNER_H = [(5, 3), (6, 3), (5, 4), (6, 4), (2, 6), (3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6)]
HAMMER = ["############", "############", "############", ".##########.", ".....##.....", ".....##.....", ".....##.....",
          ".....##.....", ".....##.....", ".....##.....", ".....##.....", ".....##.....", ".....##.....", "....####...."]
HAMMER_H = [(3, 1), (8, 1), (5, 10), (6, 10)]
LANTERN = ["...####...", "...#..#...", "..######..", ".########.", "..######..", "..#.##.#..", "..#.##.#..",
           "..#.##.#..", "..#.##.#..", "..######..", ".########.", "..######.."]
FLASK = ["...####...", "...####...", "....##....", "....##....", "...####...", "..######..", ".########.",
         "##########", "##########", "##########", "##########", ".########.", "..######.."]
FLASK_H = [(3, 2), (6, 2)] + [(x, 7) for x in range(1, 9)]
CHEST = [".############.", "##############", "##############", "##############", "##############", "##############",
         "##############", "##############", "##############", "##############", "##############"]
CHEST_H = [(x, 4) for x in range(14)] + [(6, 5), (7, 5), (6, 6), (7, 6)] + [(3, y) for y in range(1, 11) if y != 4] + \
          [(10, y) for y in range(1, 11) if y != 4]
BED = ["##............", "##............", "##............", "##..###.......", "##..###.......", "##############",
       "##############", "##############", "##..........##", "##..........##"]
BED_H = [(x, 7) for x in range(2, 12)]
FEATHER = ["......##", ".....###", "....####", "...#####", "...####.", "..#####.", "..####..", ".#####..",
           ".####...", ".###....", "##......", "#.......", "#.......", "#......."]
FEATHER_H = [(6, 1), (5, 2), (5, 3), (4, 4), (4, 5), (3, 6), (3, 7), (2, 8), (2, 9), (1, 10)]
TORII = ["##############", ".############.", "...##....##...", ".############.", "...##....##...", "...##....##...",
         "...##....##...", "...##....##...", "...##....##...", "...##....##...", "...##....##...", "...##....##...",
         "..####..####.."]
SHIELD = ["############", "############", "############", "############", "############", "############",
          ".##########.", ".##########.", "..########..", "...######...", "....####....", ".....##....."]
SHIELD_H = [(5, y) for y in range(1, 10)] + [(6, y) for y in range(1, 10)]
BOOK = [".####...####.", "######.######", "#############", "#############", "#############", "#############",
        "#############", "#############", "#############", "######.######"]
BOOK_H = [(6, y) for y in range(2, 9)] + [(x, y) for y in (3, 5, 7) for x in (2, 3, 4, 8, 9, 10)]
MASK = ["..##########..", ".############.", "##############", "##############", "##############", ".############.",
        "..####..####..", "...###..###..."]
MASK_H = [(x, y) for y in (2, 3) for x in (3, 4, 5, 8, 9, 10)]
GRAVE = ["...######...", "..########..", ".##########.", ".##########.", ".##########.", ".##########.",
         ".##########.", ".##########.", ".##########.", ".##########.", "############", "############"]
GRAVE_H = [(x, y) for y in (4, 6) for x in range(3, 9)]
MAP = ["##..........##", "##############", "##############", "##############", "##############", "##############",
       "##############", "##############", "##############", "##############", "##############", "##..........##"]
MAP_H = [(6, 3), (10, 3), (7, 4), (9, 4), (8, 5), (7, 6), (9, 6), (6, 7), (10, 7)] + [(3, 9), (4, 8), (5, 9)] + \
        [(2, y) for y in range(1, 11)] + [(11, y) for y in range(1, 11)]
MUG = [".##.###.##...", "##########...", "#############", "##########..#", "##########..#", "##########..#",
       "##########..#", "##########..#", "#############", "##########...", "##########...", ".########...."]
MUG_H = [(x, 1) for x in range(0, 10, 2)] + [(3, y) for y in range(3, 11)] + [(6, y) for y in range(3, 11)]


def target():
    """的 (同心の輪)。直径 13、輪と中心を縁取りの色で抜く。"""
    rows, holes = [], []
    c = 6.0
    for y in range(13):
        r = ""
        for x in range(13):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            r += "#" if d <= 6.5 else "."
            if 3.6 <= d < 4.6 or d < 0.6:
                holes.append((x, y))
        rows.append(r)
    return rows, holes


ICONS = {
    "guild_clerk": (BANNER, BANNER_H), "smith": (HAMMER, HAMMER_H), "tool_merchant": (LANTERN, []),
    "apothecary": (FLASK, FLASK_H), "storage_keeper": (CHEST, CHEST_H), "innkeeper": (BED, BED_H),
    "perch_keeper": (FEATHER, FEATHER_H), "shrine_priest": (TORII, []), "keep_veteran": (SHIELD, SHIELD_H),
    "archivist": (BOOK, BOOK_H), "training_master": target(), "black_marketeer": (MASK, MASK_H),
    "gravekeeper": (GRAVE, GRAVE_H), "mapmaker": (MAP, MAP_H), "drunkard": (MUG, MUG_H),
}


def mask_of(rows):
    return {(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "#"}


def icon(rows, pal, holes=()):
    """gen_npc_marker_icons.icon と同じ描き方。"""
    shape = mask_of(rows)
    sw, sh = max(x for x, _ in shape) + 1, max(y for _, y in shape) + 1
    assert sw <= W - 2 and sh <= H - 2, (sw, sh)
    ox, oy = (W - sw) // 2, (H - sh) // 2
    shape = {(x + ox, y + oy) for x, y in shape}
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for x in range(W):
        for y in range(H):
            if (x, y) in shape:
                continue
            if any((x + dx, y + dy) in shape for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = OUTLINE
    for x, y in shape:
        light = (x - 1, y) not in shape or (x, y - 1) not in shape
        dark = (x + 1, y) not in shape or (x, y + 1) not in shape
        c = pal["light"] if light and not dark else pal["dark"] if dark and not light else pal["base"]
        px[x, y] = c + (255,)
    for hx, hy in holes:
        px[hx + ox, hy + oy] = OUTLINE
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    REVIEW.mkdir(parents=True, exist_ok=True)
    imgs = {}
    for role, (rows, holes) in ICONS.items():
        im = icon(rows, BONE, holes)
        im.save(OUT / f"fac_{role}.png")
        imgs[role] = im
    # 一覧 (6 倍)。最後に今の話の印 (金の「!」・青緑の巻物・吹き出し) を並べる
    sys.path.insert(0, str(ROOT / "tools/rough"))
    import voxel
    S = 6
    cw, ch = W * S + 40, H * S + 40
    ref = [n for n in ("bang_gold", "scroll_teal", "chat") if (OUT / f"{n}.png").exists()]
    cols = 9
    items = list(imgs.items()) + [(n, Image.open(OUT / f"{n}.png").convert("RGBA")) for n in ref]
    rows_n = (len(items) + cols - 1) // cols
    cv = Image.new("RGB", (cols * cw + 20, rows_n * ch + 60), (52, 60, 74))
    d = ImageDraw.Draw(cv)
    d.text((14, 12), "施設の番人の頭の上の印 (16x24, 6 倍)。最後の 3 つは今の話の印 (比べ用)", font=voxel.font(18), fill=(236, 232, 218))
    for i, (name, im) in enumerate(items):
        x = 10 + (i % cols) * cw + 20
        y = 50 + (i // cols) * ch
        big = im.resize((W * S, H * S), Image.NEAREST)
        cv.paste(big, (x, y), big)
        d.text((x - 16, y + H * S + 6), name, font=voxel.font(13), fill=(200, 210, 220))
    cv.save(REVIEW / "facility_icons.png")
    print("wrote", len(imgs), "icons ->", OUT)


if __name__ == "__main__":
    main()
