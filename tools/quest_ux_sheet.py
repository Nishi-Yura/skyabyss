# -*- coding: utf-8 -*-
"""tools/shots/quest_ux.txt の絵を 1 枚の表にまとめる (NPC の頭の上の印と、左上の章の札)。

使い方: python tools/quest_ux_sheet.py [出力ファイル]
入力: run/shots/quest_ux/shots/quest_ux/<名前>.png
出力: docs/review/previews/quest_ux_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/quest_ux_sheet.png"
CELLS = [
    ("near_chat", "Small dot over the storyteller: a chat you have not heard (nothing else pending)"),
    ("near_new", "Gold ! over the storyteller (next main step); chapter label top-left"),
    ("near_ready", "Gold ? over both (report stage)"),
    ("far_40", "Seen from about 40 blocks, drawn larger"),
    ("wall_near", "Behind a wall at 10 blocks: still visible"),
    ("wall_far", "Behind a wall at 32 blocks: hidden"),
    ("pouch_hint", "New pouch item: small NEW hint beside the hotbar"),
    ("pouch_new", "Pouch screen: NEW badge on slots not yet looked at"),
]
W, H = 640, 360
COLS = 2
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 22)
    f = ROOT / f"run/shots/quest_ux/shots/quest_ux/{name}.png"
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + 22))
    d.text((x + 6, y + 5), label, fill=(230, 230, 230))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
