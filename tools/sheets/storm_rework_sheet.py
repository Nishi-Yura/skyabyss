# -*- coding: utf-8 -*-
"""tools/shots/storm_rework.txt の絵を 1 枚の表にまとめる (雷翼の背の作り直し A 肋の間・B 雷の回路・C 拍の舵)。

使い方: python tools/sheets/storm_rework_sheet.py [出力ファイル]
入力: run/shots/storm_rework/shots/storm_rework/<名前>.png
出力: docs/review/previews/storm_rework_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/storm_rework_sheet.png"
SRC = ROOT / "run/shots/storm_rework/shots/storm_rework"
CELLS = [
    ("rib_entrance_hud", "A rib hall: entrance, goal panel"),
    ("rib_spiral_low", "A grate spiral (4 per hop)"),
    ("rib_layer1", "A layer 1 ledge"),
    ("rib_layer2", "A layer 2 ledge"),
    ("rib_top_stone", "A top ledge: wind stone"),
    ("rib_down_rings", "A down rings from the top"),
    ("rib_outside_tower", "A tower on the wing (outside)"),
    ("circuit2_dark_hud", "B circuit 2: lure pad, lever, conduits"),
    ("circuit2_armed", "B lever down: orange path armed"),
    ("circuit2_solved", "B solved: lit conduits stay lit"),
    ("circuit3_solved", "B circuit 3 (two-stage)"),
    ("helm_west_idle", "C helms at the west entrance"),
    ("helm_west_storm_locked_hud", "C storm lock: helm lit, gauge shows lock"),
    ("helm_front", "C front entrance helms"),
]
COLS = 3
W, H = 560, 315
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 22)
    f = SRC / f"{name}.png"
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + 22))
    else:
        d.text((x + 6, y + 60), "missing: " + name, fill=(220, 90, 90))
    d.text((x + 6, y + 5), label, fill=(230, 230, 230))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
