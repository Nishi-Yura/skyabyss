# -*- coding: utf-8 -*-
"""tools/shots/boss_gates.txt の絵を 1 枚の表にまとめる (ボス部屋の大門 8 か所)。

使い方: python tools/sheets/boss_gates_sheet.py [出力ファイル]
入力: run/shots/boss_gates/shots/boss_gates/gate_<巨神>_<場所>.png
出力: docs/review/previews/boss_gates_sheet.png (左が中ボスの広間の入口、右が心臓の間の入口。上から 岩鯨・潮殻・雷翼・余燼)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/boss_gates_sheet.png"
ROWS = [
    ("whale", [("sentinel", "Stone whale: sentinel captain hall"), ("heart", "Stone whale: heart chamber")]),
    ("tide", [("flats", "Tide shell: Beckoner flats"), ("heart", "Tide shell: heart chamber")]),
    ("storm", [("nest", "Thunder wing: raiju nest"), ("heart", "Thunder wing: heart chamber (keel)")]),
    ("ember", [("smith", "Ember: ash smith door"), ("heart", "Ember: heart chamber")]),
]
W, H = 640, 360
img = Image.new("RGB", (W * 2, (H + 22) * len(ROWS)), (20, 20, 24))
d = ImageDraw.Draw(img)
for r, (titan, cells) in enumerate(ROWS):
    for c, (place, label) in enumerate(cells):
        x = c * W
        y = r * (H + 22)
        f = ROOT / f"run/shots/boss_gates/shots/boss_gates/gate_{titan}_{place}.png"
        if f.exists():
            im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
            img.paste(im, (x, y + 22))
        d.text((x + 6, y + 5), label, fill=(230, 230, 230))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
