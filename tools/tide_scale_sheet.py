# -*- coding: utf-8 -*-
"""tools/shots/tide_scale.txt の絵を 1 枚の表にまとめる (潮殻の背の拡大。26 の 9 章)。落ち着いた色・日本語の見出し。

使い方: python tools/tide_scale_sheet.py [出力ファイル]
入力: run/shots/tide_scale/shots/tide_scale/<名前>.png
出力: docs/review/previews/tide_scale_sheet.png (git add -f)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/tide_scale_sheet.png"
SRC = ROOT / "run/shots/tide_scale/shots/tide_scale"

CELLS = [
    ("overview", "全体", "四段の台地と塔。ひれは前後に二対"),
    ("beach", "潮門の浜", "35×35 の砂岩の大広間。肋のドームと帆立貝"),
    ("hub", "潮の広間", "半径 23 の沈んだ水盤。放射の肋と床の環"),
    ("garden_c", "珊瑚の庭", "幅 5 の水路。場所ごとに色が違う"),
    ("garden_clam", "昆布のうろ", "南の青緑の地帯。大貝の淵へ"),
    ("watch_core", "潮見の間", "緑青の銅の水門の広間。灯りの道"),
    ("neck", "首の廊下", "骨のアーチ。壁の窪みに碑と宝"),
    ("flats", "群れの干潟", "鯨骨の肋が渡る孵り場。巣 5・島 14"),
    ("fin_room", "貝の間", "決まりを教え、次の間でひねる"),
    ("fin_tip", "ひれの先端", "碑と宝。景色の報い"),
    ("breath", "息の間", "気室 半径 15。皿をたどって昇る"),
    ("crown_ring", "冠の回廊", "気室を巻く輪。骨の柱と宝"),
]
W, H, COLS = 520, 292, 4
PAD = 10
CAP = 52
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
HEAD = 64
FOOT = 40
img = Image.new("RGB", (COLS * (W + PAD) + PAD, HEAD + rows * (H + CAP + PAD) + FOOT), BG)
d = ImageDraw.Draw(img)
d.text((PAD + 4, 14), "潮殻の背  版 20  水平 ×1.6・垂直 ×1.3", fill=FG, font=font(26))
d.text((PAD + 4, 44), "場所ごとに材質・目印・案内・宝を替えた。行き止まりには碑と宝。ひれの道には貝の間", fill=SUB, font=font(14))
for i, (name, title, note) in enumerate(CELLS):
    x = PAD + (i % COLS) * (W + PAD)
    y = HEAD + (i // COLS) * (H + CAP + PAD)
    f = SRC / f"{name}.png"
    if f.exists():
        d.rectangle([x - 1, y - 1, x + W, y + H], outline=(60, 66, 70))
        img.paste(Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS), (x, y))
    d.text((x + 2, y + H + 6), title, fill=ACC, font=font(18))
    d.text((x + 2, y + H + 30), note, fill=SUB, font=font(13))
d.text((PAD + 4, img.height - 30), "配置の版 20 / 体 4,610,293 ブロック / 建設 約 7 秒 + 体 42 秒 / 休み場 10 / 碑と宝 15 / 貝の間 4", fill=SUB, font=font(13))
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT, img.size)
