"""潮殻の心臓・潮招きの予告の絵 (tools/shots/telegraph_tide_heart.txt・telegraph_tide_boss.txt) を 1 枚の表にまとめる。

使い方: python tools/telegraph_tide_sheet.py [出力 (既定 docs/review/previews/telegraph_tide_sheet.png)]
入力: run/shots/telegraph_tide_heart/shots/telegraph_tide_heart/*.png と run/shots/telegraph_tide_boss/shots/telegraph_tide_boss/*.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
HEART = ROOT / "run/shots/telegraph_tide_heart/shots/telegraph_tide_heart"
BOSS = ROOT / "run/shots/telegraph_tide_boss/shots/telegraph_tide_boss"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/telegraph_tide_sheet.png"

CELLS = [
    (HEART, "breath_a", "heart: breath (4 lines, n=6)"),
    (HEART, "shell_a", "heart: shell strike (core + safe lanes)"),
    (HEART, "shell_b", "heart: shell strike (triple ring)"),
    (HEART, "whirl_a", "heart: whirl (blast + pools)"),
    (HEART, "flood_a", "heart: coral spears"),
    (HEART, "bubble_a", "heart: bubble pillars"),
    (HEART, "pearl_b", "heart: pearls lock to circles"),
    (HEART, "ebb_b", "heart: ebb (blast + ripples)"),
    (HEART, "moon_a", "heart: moon wave (3 fans)"),
    (HEART, "moon_b", "heart: moon wave sweeping"),
    (HEART, "rain_a", "heart: pearl rain (14 circles)"),
    (BOSS, "slam_a", "beckoner: claw slam (3 lanes)"),
    (BOSS, "breath_a", "beckoner: bubble breath (8 lanes)"),
    (BOSS, "burrow_a", "beckoner: burrow (circle + geysers)"),
    (BOSS, "roar_a", "beckoner: roar (circle + ripples)"),
]
COLS = 3
W, H = 640, 360


def main():
    rows = (len(CELLS) + COLS - 1) // COLS
    img = Image.new("RGB", (COLS * W, rows * H), (20, 20, 24))
    d = ImageDraw.Draw(img)
    for i, (folder, name, label) in enumerate(CELLS):
        x, y = (i % COLS) * W, (i // COLS) * H
        f = folder / f"{name}.png"
        if f.exists():
            im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
            img.paste(im, (x, y))
        else:
            d.rectangle((x, y, x + W - 1, y + H - 1), fill=(60, 20, 20))
            d.text((x + 8, y + H // 2), "missing " + name, fill=(255, 140, 140))
        d.rectangle((x, y, x + W - 1, y + 18), fill=(0, 0, 0))
        d.text((x + 6, y + 3), label, fill=(255, 255, 0))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
