# -*- coding: utf-8 -*-
"""tools/shots/relic_blocks.txt が撮った絵から、遺物ブロックの全様式の一枚を docs/review/previews/mod_blocks_sheet2.png に組む。

使い方: python tools/gen/shots/gen_relic_shots.py → ./gradlew runShots --no-daemon -Pscript=tools/shots/relic_blocks.txt のあと、python -I tools/sheets/relic_blocks_sheet.py
出力: docs/review/previews/mod_blocks_sheet2.png (日本語の見出しつき。落ち着いた色。音なしで撮る)
各絵は左から 鯨・潮・嵐・余燼 の順。手前の列と奥の列が状態違い。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/relic_blocks/shots/relic_blocks"
OUT = ROOT / "docs/review/previews/mod_blocks_sheet2.png"

TILES = [
    ("relic_relic_cache", "遺物の箱  手前: 閉じた / 奥: 蓋が上がる"),
    ("relic_rest_hearth", "休み火  手前: 灯った / 奥: 冷えた"),
    ("relic_relic_lever", "遺物のレバー  手前: 切 / 奥: 入"),
    ("relic_relic_button", "遺物のボタン  手前: 戻 / 奥: 押した"),
    ("relic_relic_plate", "遺物の感圧板  手前: 戻 / 奥: 踏んだ"),
    ("relic_relic_bell", "遺物の鐘  手前: 静か / 奥: 鳴る"),
    ("relic_tablet", "碑  手前: 立碑 / 奥: 壁掛け"),
    ("relic_trial_seal", "試練済みの封印 (越えた試練の入口)"),
]
W, H, COLS = 640, 360, 2
BG = (28, 30, 34)
FG = (204, 208, 212)
SUB = (150, 156, 164)


def font(size):
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


f, fs = font(16), font(13)
HEAD = 30
rows = (len(TILES) + COLS - 1) // COLS
sheet = Image.new("RGB", (W * COLS, HEAD + (H + 26) * rows), BG)
d = ImageDraw.Draw(sheet)
d.text((8, 6), "遺物ブロックの四つの様式 (各絵: 左から 鯨 = 骨と深層岩 / 潮 = 鱗と貝 / 嵐 = 銅と羽 / 余燼 = 玄武岩と琥珀)", fill=SUB, font=fs)
for i, (name, title) in enumerate(TILES):
    x = (i % COLS) * W
    y = HEAD + (i // COLS) * (H + 26)
    p = SHOTS / f"{name}.png"
    if p.exists():
        sheet.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (x, y + 26))
    d.text((x + 8, y + 4), title, fill=FG, font=f)
OUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUT)
print(OUT)
