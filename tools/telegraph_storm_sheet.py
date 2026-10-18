"""tools/shots/telegraph_storm.txt の絵を一枚の表にまとめる。

使い方: python tools/telegraph_storm_sheet.py [撮った絵のフォルダ] [出力 png]
出力: docs/review/previews/telegraph_storm_sheet.png (雷翼の心臓の 9 技と雷獣の 3 技。6 人のつもりで撮った印)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/telegraph_storm/shots/telegraph_storm"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/telegraph_storm_sheet.png"
CELLS = [
    ("summon_a", "SUMMON"), ("blade_a", "BLADE warn"), ("blade_b", "BLADE sweep"),
    ("chain_a", "CHAIN"), ("wingbeat_a", "WINGBEAT warn"), ("wingbeat_b", "WINGBEAT ring"),
    ("charge_a", "CHARGE warn"), ("charge_b", "CHARGE active"), ("feather_a", "FEATHER"),
    ("flash_a", "FLASH"), ("web_a", "WEB warn"), ("web_b", "WEB sweep"),
    ("barrage_a", "BARRAGE"), ("pounce_a", "RAIJU POUNCE"),
]
W, H, COLS = 640, 360, 3


def main():
    rows = (len(CELLS) + COLS - 1) // COLS
    img = Image.new("RGB", (W * COLS, H * rows), (20, 20, 24))
    d = ImageDraw.Draw(img)
    for i, (name, label) in enumerate(CELLS):
        x, y = (i % COLS) * W, (i // COLS) * H
        f = SRC / f"{name}.png"
        if f.exists():
            im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
            img.paste(im, (x, y))
        d.rectangle((x, y, x + 230, y + 22), fill=(0, 0, 0))
        d.text((x + 6, y + 5), label, fill=(255, 255, 255))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


main()
