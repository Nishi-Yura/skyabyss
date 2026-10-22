# -*- coding: utf-8 -*-
"""tools/shots/whale_scale.txt の絵を 1 枚の表にまとめる (岩鯨の背の拡大。乗り込み口の陣・背・各部屋)。

使い方: python tools/sheets/whale_scale_sheet.py [出力ファイル]
入力: run/shots/whale_scale/shots/whale_scale/<名前>.png
出力: docs/review/previews/whale_scale_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/whale_scale_sheet.png"
SRC = ROOT / "run/shots/whale_scale/shots/whale_scale"
BOARD = ROOT / "run/shots/whale_board/shots/whale_board"
CELLS = [
    ("@board_far", "Boarding sigil under the chain (far)"),
    ("@board_close", "Boarding sigil (close, flat on the ground)"),
    ("@board_above", "Boarding sigil (from above)"),
    ("arrival", "Arrival flank platform (east: anchor, forge, stair)"),
    ("climb", "Climb stair breaking out onto the back"),
    ("plaza", "Plaza on the tail root (north: ridge gate, shrine)"),
    ("shrine", "Shrine pavilion and stair mouth"),
    ("hub", "Heartbeat hall (37 x 41, 16 high)"),
    ("west_a", "West wing: rib valley, three lanes"),
    ("west_b", "West wing: shaft valley"),
    ("bell", "Counting-song bell tower"),
    ("east_e1", "East wing: first hall (21 x 47)"),
    ("east_e2", "East wing: second hall (archers)"),
    ("east_ck", "East wing: guard post (old sentinel)"),
    ("east_e3", "East wing: sentinel captain hall (wide)"),
    ("maze_hatch", "Hub floor hatch down to the dark waterway"),
    ("maze_entry", "Waterway: dry entry room"),
    ("heart_rest", "Heart corridor rest point"),
    ("heart_gate", "Heart chamber gate and vault"),
    ("heart_arena", "Heart chamber (radius 25)"),
    ("song", "Song shrine (before the heart)"),
    ("library", "Bone library (optional wing)"),
    ("camp", "Back camp (optional)"),
    ("tail_vista", "Tail vista (optional)"),
    ("overview", "Whole body from the tail"),
]
W, H, COLS = 640, 360, 3
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 22)
    f = (BOARD if name.startswith("@") else SRC) / f"{name.lstrip('@')}.png"
    if f.exists():
        img.paste(Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS), (x, y + 22))
    d.text((x + 6, y + 5), label, fill=(230, 230, 230))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
