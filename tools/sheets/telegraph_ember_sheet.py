"""余燼の心臓・灰の鍛え手の予告の絵 (tools/shots/telegraph_ember_heart.txt, telegraph_ember_smith.txt) を一枚にまとめる。

使い方: python tools/sheets/telegraph_ember_sheet.py [心臓の絵のフォルダ] [鍛え手の絵のフォルダ] [出力]
既定: run/shots/<台本名>/shots/<台本名>/ と docs/review/previews/telegraph_ember_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]


def folder(name):
    return ROOT / "run/shots" / name / "shots" / name


HEART = Path(sys.argv[1]) if len(sys.argv) > 1 else folder("telegraph_ember_heart")
SMITH = Path(sys.argv[2]) if len(sys.argv) > 2 else folder("telegraph_ember_smith")
OUT = Path(sys.argv[3]) if len(sys.argv) > 3 else ROOT / "docs/review/previews/telegraph_ember_sheet.png"

CELLS = [
    (HEART, "breath_a", "heart: breath (6p, lines)"),
    (HEART, "pulse_a", "heart: pulse (lanes)"),
    (HEART, "orb_a", "heart: molten orbs (circles)"),
    (HEART, "gale_a", "heart: gale (circle + rings)"),
    (HEART, "pellet_a", "heart: ash pellets (circles)"),
    (HEART, "fading_a", "heart: fading beat"),
    (SMITH, "slam_a", "smith: hammer slam (3 strips)"),
    (SMITH, "slam_vent", "smith: vents"),
    (SMITH, "cold_b", "smith: cold slam ring + lanes"),
    (SMITH, "breath_a", "smith: bellows breath (cones)"),
    (SMITH, "sparks_a", "smith: sparks (circles)"),
    (SMITH, "sparks_b", "smith: sparks landing"),
]
W, H, COLS = 640, 360, 3


def main():
    rows = (len(CELLS) + COLS - 1) // COLS
    sheet = Image.new("RGB", (W * COLS, H * rows), (20, 20, 24))
    d = ImageDraw.Draw(sheet)
    for i, (src, name, label) in enumerate(CELLS):
        x, y = (i % COLS) * W, (i // COLS) * H
        f = src / f"{name}.png"
        if f.exists():
            sheet.paste(Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS), (x, y))
        else:
            d.text((x + 8, y + H // 2), f"missing {name}", fill=(255, 120, 120))
        d.rectangle((x, y, x + 330, y + 16), fill=(0, 0, 0))
        d.text((x + 4, y + 2), label, fill=(255, 255, 0))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("wrote", OUT)


main()
