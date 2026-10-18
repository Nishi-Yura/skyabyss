"""tools/gen_weapon_swing_shots.py の絵を比べる表にまとめる。

使い方: python tools/weapon_swing_sheet.py [撮った絵のフォルダ] [出力フォルダ (既定 docs/review/previews)]
出力: <出力>/weapon_swing_sheet.png (代表 4 本。行 = 武器 x (一人称 昔 / 一人称 今 / 三人称 昔 / 三人称 今)、列 = 振り始めから 1,3,5,7,9,11 tick 後)
      <出力>/weapon_swing_<武器>.png (武器ごと)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/weapon_swing/shots/weapon_swing"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews"
TICKS = [1, 3, 5, 7, 9, 11]
BOX = {"fp": (480, 100, 1280, 620), "tp": (420, 190, 860, 620)}
MAIN = ["whalebone_greatsword", "smith_hammer", "captain_pike", "waterway_fang"]
ROWS = [("fp", "old"), ("fp", "new"), ("tp", "old"), ("tp", "new")]
LABEL = {"old": "BEFORE", "new": "AFTER"}


def cell(w, mode, view, t, size):
    f = SRC / f"{w}_{mode}_{view}_{t:02d}.png"
    if not f.exists():
        return Image.new("RGB", (size, size), (40, 20, 20))
    im = Image.open(f).convert("RGB").crop(BOX[view])
    s = size / max(im.size)
    im = im.resize((int(im.width * s), int(im.height * s)), Image.LANCZOS)
    c = Image.new("RGB", (size, size), (20, 20, 24))
    c.paste(im, ((size - im.width) // 2, (size - im.height) // 2))
    return c


def sheet(weapons, size):
    W = size * len(TICKS)
    H = size * len(ROWS) * len(weapons)
    img = Image.new("RGB", (W, H), (20, 20, 24))
    d = ImageDraw.Draw(img)
    for wi, w in enumerate(weapons):
        for ri, (view, mode) in enumerate(ROWS):
            y = (wi * len(ROWS) + ri) * size
            for ci, t in enumerate(TICKS):
                img.paste(cell(w, mode, view, t, size), (ci * size, y))
                d.text((ci * size + 4, y + 4), f"{view} {LABEL[mode]} t+{t}", fill=(255, 255, 0) if mode == "new" else (255, 140, 140))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sheet(MAIN, 200).save(OUT / "weapon_swing_sheet.png")
    names = sorted({p.name.split("_old_")[0] for p in SRC.glob("*_old_fp_01.png")})
    for w in names:
        sheet([w], 240).save(OUT / f"weapon_swing_{w}.png")
    print(len(names), "weapons")


if __name__ == "__main__":
    main()
