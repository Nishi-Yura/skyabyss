# -*- coding: utf-8 -*-
"""tools/shots/titan_ground.txt の絵を、前と後を並べた 1 枚の表にまとめる。

使い方: python tools/sheets/titan_ground_sheet.py [撮った絵のフォルダ] [出力ファイル]
出力: docs/review/previews/titan_ground_sheet.png (行 = 場面、左 = 直す前、右 = 直した後)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/titan_ground/shots/titan_ground"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/titan_ground_sheet.png"

ROWS = [
    ("s1_before_view", "s1_after_view", "丘の岸: 人の目から", "前: 旧い休み場所。甲羅が丘にめり込む", "後: 沖の開けた水 (127 ブロック先) で休む。体が丘に触れず、浮かない"),
    ("s1_before_aerial", "s1_after_aerial", "丘の岸: 沖から見下ろす", "前", "後"),
    ("s2_before_view", "s2_after_view", "荒い岸: 人の目から", "前: 旧い休み場所。丘・木にめり込む", "後: 触れない開けた水がないので寄らない (浮かせない)"),
    ("s2_before_aerial", "s2_after_aerial", "荒い岸: 沖から見下ろす", "前", "後"),
    ("s3_before_door10", "s3_after_door10", "乗り込み口: 10 ブロックから", "前: 標が門の口 (甲羅の縁の中) にあり体に隠れる", "後: 門の真外の水の上に輪と光の柱"),
    ("s3_before_door30", "s3_after_door30", "乗り込み口: 30 ブロックから", "前", "後"),
]
W, H = 640, 360
HEAD = 30
font = None
for name in ("C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
    try:
        font = ImageFont.truetype(name, 15)
        break
    except OSError:
        continue
if font is None:
    font = ImageFont.load_default()

img = Image.new("RGB", (W * 2, (H + HEAD) * len(ROWS)), (20, 20, 24))
d = ImageDraw.Draw(img)
for r, (a, b, title, la, lb) in enumerate(ROWS):
    y = r * (H + HEAD)
    for c, (name, label) in enumerate(((a, la), (b, lb))):
        x = c * W
        f = SRC / (name + ".png")
        if f.exists():
            im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
            img.paste(im, (x, y + HEAD))
        d.text((x + 6, y + 6), (title + "  " if c == 0 else "") + label, fill=(235, 235, 235), font=font)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
