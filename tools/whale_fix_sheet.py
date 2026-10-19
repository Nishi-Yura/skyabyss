# -*- coding: utf-8 -*-
"""tools/shots/whale_fix.txt が撮った絵から、確認用の一枚を docs/review/previews/whale_fix_sheet.png に組む (岩鯨の背の作り直し。版 18)。

使い方: ./gradlew runShots -Pscript=tools/shots/whale_fix.txt のあと、python -I tools/whale_fix_sheet.py
出力: docs/review/previews/whale_fix_sheet.png (日本語の見出しつき。落ち着いた色。音なし)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "run/shots/whale_fix/shots/whale_fix"
OUT = ROOT / "docs/review/previews/whale_fix_sheet.png"

TILES = [
    ("bell_landing", "鐘楼: 入口の岩棚から。吊り鐘と銅の柱"),
    ("bell_bells", "鐘楼: 五つの鐘が低い順に並ぶ"),
    ("west_a", "西: 肋骨の谷。飛び石 → 島 → 骨の橋"),
    ("west_b", "西: 竪穴。島と登る飛び石"),
    ("west_c", "西: 崩れ橋。長い橋と島"),
    ("east_e1", "東: 第一の広間 (練兵場)。灯りの道と赤い旗"),
    ("east_e2", "東: 第二の広間 (射手の回廊)。北の戸へ"),
    ("east_ck", "東: 番兵の詰所 (兵舎)。寝台と作業台"),
    ("east_e3", "東: 番兵長の広間。赤い絨毯と金の柱"),
    ("east_door_sign", "東: 北の戸と、戸のそばの碑"),
    ("hub", "鼓動の間: 低く吊るした灯りと床の灯りの道"),
    ("seal_corridor", "北の封印のさき: そのまま心臓の回廊"),
    ("fin_left", "左のひれの先: 碑と宝箱"),
    ("fin_right_ledge", "右のひれの先: 柵の切れ目と隠し棚"),
    ("library", "骨の書庫: 黒樫と書棚"),
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
