#!/usr/bin/env python3
"""gen_boss_polish_shots.py で撮った絵を一枚に並べる: docs/review/dungeon_bosses/<主>_ingame.png

    python -I tools/sheets/boss_polish_sheet.py <主>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]

if __name__ == "__main__":
    boss = sys.argv[1]
    src = ROOT / "run/shots" / f"boss_polish_{boss}" / "shots" / f"boss_polish_{boss}"
    files = sorted(src.glob(f"{boss}_*.png"), key=lambda p: p.stat().st_mtime)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/meiryo.ttc", 18)
    except OSError:
        font = None
    cols = 4
    rows = (len(files) + cols - 1) // cols
    cv = Image.new("RGB", (480 * cols, 294 * rows), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, f in enumerate(files):
        im = Image.open(f).convert("RGB").resize((480, 270))
        x, y = (i % cols) * 480, (i // cols) * 294
        cv.paste(im, (x, y + 24))
        d.text((x + 8, y + 1), f.stem[len(boss) + 1:], fill=(150, 232, 255), font=font)
    out = ROOT / "docs/review/dungeon_bosses" / f"{boss}_ingame.png"
    cv.save(out)
    print(out)
