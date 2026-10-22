# -*- coding: utf-8 -*-
"""tools/shots/ember_scale.txt が撮った絵から、確認用の一枚を docs/review/previews/ember_scale_sheet.png に組む (余燼の背の拡大。版 20)。

使い方: ./gradlew runShots -Pscript=tools/shots/ember_scale.txt のあと、python -I tools/sheets/ember_scale_sheet.py
出力: docs/review/previews/ember_scale_sheet.png (日本語の見出しつき。落ち着いた色。音なし)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/ember_scale/shots/ember_scale"
OUT = ROOT / "docs/review/previews/ember_scale_sheet.png"

TILES = [
    ("mouth", "口: 舌の上の到着。灯りの柱と見送りの石"),
    ("ridge_hall", "喉: 玄武の鱗の尾根の広間。段と骨の肋"),
    ("hub_south", "炉心の広間: 丸天井と色の道 (南の赤)"),
    ("tube_west_down", "冷えた溶岩洞: 広間から西の試練へ下る"),
    ("west_court", "西: 灰降りの庭。休み場と灰の木"),
    ("west_lake2", "西: 第二の湖 (蛾と燠虫のひねり)"),
    ("west_far", "西: 奥の岸の石の門と熾り石"),
    ("east_gallery", "東: 鍛えの回廊。熔脈の溝と手順の碑"),
    ("east_forge", "東: 大炉。鞴・金床・槽は床の色で見分ける"),
    ("south_lane", "南: 灰運びの道と噴気。碑が規則を言う"),
    ("back_tube", "背へ出る洞: 背の上の出口まで灯りの道"),
    ("overview_tail", "全景: 火山を背負う火蜥蜴の体 (x1.6)"),
]
W, H, COLS = 480, 270, 3
BG = (30, 32, 36)
FG = (200, 206, 210)


def font(size):
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


f = font(15)
rows = (len(TILES) + COLS - 1) // COLS
sheet = Image.new("RGB", (W * COLS, (H + 26) * rows), BG)
d = ImageDraw.Draw(sheet)
for i, (name, title) in enumerate(TILES):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 26)
    p = SHOTS / f"{name}.png"
    if p.exists():
        sheet.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (x, y + 26))
    d.text((x + 6, y + 4), title, fill=FG, font=f)
OUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUT)
print(OUT)
