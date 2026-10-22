# -*- coding: utf-8 -*-
"""tools/shots/deep_skills.txt の絵を 1 枚にまとめる (橋・淵・前世の技・変異と紋の盤の K 画面と、変異の見た目)。

使い方: python tools/sheets/deep_skills_sheet.py [撮った絵のフォルダ] [出力ファイル]
出力: docs/review/previews/deep_skills_sheet.png (落ち着いた色。ラベルは日本語)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/deep_skills/shots/deep_skills"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/deep_skills_sheet.png"
# (ファイル名, ラベル, 切り出し: 'ui' = 画面の中心 / 'world' = 全体)
CELLS = [
    ("deep_1_bridge", "橋の輪: 隣り合う 2 本の木で技を 3 つずつ → 辺の橋が開く (金の枠)", "ui"),
    ("deep_2_deep", "淵: 転生した人にだけ。転生 1 回で入口、5 回で全段 (転 1・3・4・5)", "ui"),
    ("deep_3_past", "前世の技: 転生 1 回につき 2 点の別枠の点。6 本、転生の回数で開く", "ui"),
    ("deep_4_ext", "変異 (8 技・一度きり) と紋の盤 (穴は転生で 3 → 6、中心の技と紋を選ぶ)", "ui"),
    ("deep_5_cliff", "変異 断崖: 扇ではなく、前へ岩の列が突き上がる", "world"),
    ("deep_6_blade", "変異 飛ぶ刃: 近づかず、三日月の刃が前へ飛ぶ", "world"),
    ("deep_7_meteor", "変異 熾の墜落: 前 6 ブロックへ火の塊が落ち、熾火の床を残す", "world"),
]
W, H = 400, 225
COLS = 3
LABEL = 24
rows = (len(CELLS) + COLS - 1) // COLS
BG = (30, 32, 38)
INK = (168, 174, 188)
font = None
for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/YuGothM.ttc"):
    try:
        font = ImageFont.truetype(name, 12)
        break
    except OSError:
        pass
img = Image.new("RGB", (W * COLS + 8 * (COLS + 1), (H + LABEL + 8) * rows + 8), BG)
d = ImageDraw.Draw(img)
for i, (name, label, mode) in enumerate(CELLS):
    x = 8 + (i % COLS) * (W + 8)
    y = 8 + (i // COLS) * (H + LABEL + 8)
    f = SRC / (name + ".png")
    if f.exists():
        im = Image.open(f).convert("RGB")
        w, h = im.size
        if mode == "ui":
            im = im.crop((int(w * 0.05), int(h * 0.05), int(w * 0.95), int(h * 0.89)))
        im = im.resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + LABEL))
    d.text((x + 2, y + 5), label, fill=INK, font=font)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
