# -*- coding: utf-8 -*-
"""tools/shots/storm_polish.txt と sight_thunder.txt の絵を 1 枚の表にまとめる (雷翼の背の磨き: 肋の間の外の姿・拍の舵・雷翼の初見)。

使い方: python tools/sheets/storm_polish_sheet.py [出力ファイル]
入力: run/shots/storm_polish/shots/storm_polish/<名前>.png、run/shots/sight_thunder/shots/sight_thunder/<名前>.png
出力: docs/review/previews/storm_polish_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/storm_polish_sheet.png"
A = ROOT / "run/shots/storm_polish/shots/storm_polish"
B = ROOT / "run/shots/sight_thunder/shots/sight_thunder"
CELLS = [
    (A, "rib_ext_sw", "Rib spire from the south-west: flared base, bone ribs, crown"),
    (A, "rib_ext_south", "From the south: ribs lean in, pilasters, storm eyes"),
    (A, "rib_ext_ne", "From the north-east: blended into the wing"),
    (A, "rib_ext_base", "Base: dark wing stone turns to white feather"),
    (A, "rib_ext_crown", "Crown: arched ribs, lightning-rod spire"),
    (A, "rib_ext_top", "From above: arcs gather at the apex"),
    (A, "helm_west_idle", "Helms at the west entrance, idle (dark gems)"),
    (A, "helm_calm_close", "Calm helm locked: pale blue / silver, gems lit"),
    (A, "helm_storm_close", "Storm helm locked: dark stone / gold, gems lit"),
    (B, "sight_d", "Thunder titan first sight scene plays (dialogue)"),
    (B, "sight_e", "After the scene: chapter card, quest started"),
]
COLS = 3
W, H = 560, 315
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (src, name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 22)
    f = src / f"{name}.png"
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + 22))
    else:
        d.text((x + 6, y + 60), "missing: " + name, fill=(220, 90, 90))
    d.text((x + 6, y + 5), label, fill=(230, 230, 230))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
