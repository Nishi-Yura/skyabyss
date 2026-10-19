# -*- coding: utf-8 -*-
"""tools/shots/trial_p2.txt の絵を 1 枚の表にまとめる (試練のプール P2。docs/design/29 の 3.6)。落ち着いた色・日本語の見出し。

使い方: python -I tools/trial_p2_sheet.py [出力ファイル]
入力: run/shots/trial_p2/shots/trial_p2/<名前>.png
出力: docs/review/previews/trial_p2_sheet.png (git add -f)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/trial_p2_sheet.png"
SRC = ROOT / "run/shots/trial_p2/shots/trial_p2"

CELLS = [
    ("w03", "岩鯨 W03 苔の滑り坂", "流れを灯りで読み、骨の島を踏む"),
    ("w04", "岩鯨 W04 噴気孔の昇り", "白く瞬いて吹き上がる孔で昇る"),
    ("w11", "岩鯨 W11 棺の護送", "光る道に沿って棺を押し、守る"),
    ("w12", "岩鯨 W12 鯨骨の闘技場", "ひびの入った床から崩れる円い床"),
    ("t02_ebb", "潮殻 T02 潮の背渡り (引き潮)", "砂州の道。重なる間は浮き石も"),
    ("t02_flood", "潮殻 T02 潮の背渡り (満ち潮)", "浮き石の道。乗りつづけると沈む"),
    ("t06", "潮殻 T06 貝の合唱", "壁画の色の順に貝を叩く。読み方が変わる"),
    ("t07", "潮殻 T07 二つの海", "栓で水を動かし金の目盛りへ合わせる"),
    ("t10", "潮殻 T10 満ち潮の円環", "砂が洗われて水面になる円い島"),
]
W, H, COLS = 420, 236, 3
PAD = 8
CAP = 46
BG = (29, 33, 36)
FG = (201, 196, 184)
SUB = (140, 146, 148)
ACC = (120, 160, 150)


def font(size):
    for p in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


rows = (len(CELLS) + COLS - 1) // COLS
HEAD = 56
FOOT = 34
img = Image.new("RGB", (COLS * (W + PAD) + PAD, HEAD + rows * (H + CAP + PAD) + FOOT), BG)
d = ImageDraw.Draw(img)
d.text((PAD + 4, 10), "試練のプール P2  岩鯨 +4・潮殻 +4", fill=FG, font=font(24))
d.text((PAD + 4, 38), "一つの決まりを教え、次でひねる。一人で解け、人数でぶれ、配置・数字・障害・目標の修飾を持つ", fill=SUB, font=font(13))
for i, (name, title, note) in enumerate(CELLS):
    x = PAD + (i % COLS) * (W + PAD)
    y = HEAD + (i // COLS) * (H + CAP + PAD)
    f = SRC / f"{name}.png"
    if f.exists():
        d.rectangle([x - 1, y - 1, x + W, y + H], outline=(60, 66, 70))
        img.paste(Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS), (x, y))
    d.text((x + 2, y + H + 5), title, fill=ACC, font=font(16))
    d.text((x + 2, y + H + 26), note, fill=SUB, font=font(12))
d.text((PAD + 4, img.height - 24), "配置の版 21 / 岩鯨 11・潮殻 7 の試練を引く / 全変種 2,574 通りを自己テスト / 歩く道は閉じた足場として検査", fill=SUB, font=font(12))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT, img.size)
