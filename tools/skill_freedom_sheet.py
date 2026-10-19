# -*- coding: utf-8 -*-
"""tools/shots/skill_freedom.txt の絵を 1 枚にまとめる (スキルの自由度: 全部の木・タグ・木のセット・熟練・構え・連鎖・鍵石・試着の K 画面)。

使い方: python tools/skill_freedom_sheet.py [撮った絵のフォルダ] [出力ファイル]
出力: docs/review/previews/skill_freedom_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/skill_freedom/shots/skill_freedom"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/skill_freedom_sheet.png"
CELLS = [
    ("freedom_1_open", "天職の前でも、全部の木が全部の行まで開いている"),
    ("freedom_3_sets", "木のセット (2・4・6 点で小さな追加)。天職は専門: 要・天性・割引"),
    ("freedom_2_mastery", "熟練 3 で刻印の 4 つ目。熟練は使う・当てる・予告をよけて当てるで育つ"),
    ("freedom_4_build", "構え (守・疾・歌)・連鎖 (3 本)・タグの数・鍵石 (2 つまで。代価つき)"),
    ("freedom_5_tryon", "試着: 仮に振って数字を確かめ、確定か取り消し"),
]
W, H = 520, 293
COLS = 2
LABEL = 28
rows = (len(CELLS) + COLS - 1) // COLS
BG = (26, 28, 34)
font = None
for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/YuGothM.ttc"):
    try:
        font = ImageFont.truetype(name, 13)
        break
    except OSError:
        pass
img = Image.new("RGB", (W * COLS + 8 * (COLS + 1), (H + LABEL + 8) * rows + 8), BG)
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = 8 + (i % COLS) * (W + 8)
    y = 8 + (i // COLS) * (H + LABEL + 8)
    f = SRC / (name + ".png")
    if f.exists():
        im = Image.open(f).convert("RGB")
        # 画面の中心 (K の盤) を切り出して、余白を減らす
        w, h = im.size
        im = im.crop((int(w * 0.04), int(h * 0.10), int(w * 0.96), int(h * 0.90))).resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + LABEL))
    d.text((x + 2, y + 6), label, fill=(176, 182, 196), font=font)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
