"""tools/shots/titans_fix.txt が撮った絵から、確認用の一枚を docs/review/previews/ に組む (日本語の見出し・音なし・控えめな色)。
使い方: ./gradlew runShots -Pscript=tools/shots/titans_fix.txt のあと、python -I tools/sheets/titans_fix_sheet.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/titans_fix/shots/titans_fix"
OUT = ROOT / "docs/review/previews/titans_fix_sheet.png"
TILES = [
    ("tide_low_in_water", "亀: 休むとき腹が水に沈む (喫水 11)"),
    ("tide_doorway_front", "亀の入口: 光の門と水面の光の道 (内向きの山形)"),
    ("tide_doorway_above", "同じ入口を上から (昇る柱はなし)"),
]


def font(size):
    for n in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(n, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    cols, tw, th = 2, 640, 360
    rows = (len(TILES) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + 26)), (34, 36, 40))
    d = ImageDraw.Draw(sheet)
    f = font(15)
    for i, (name, label) in enumerate(TILES):
        x, y = (i % cols) * tw, (i // cols) * (th + 26)
        p = SHOTS / f"{name}.png"
        if p.exists():
            sheet.paste(Image.open(p).convert("RGB").resize((tw, th)), (x, y + 26))
        d.text((x + 8, y + 4), label, font=f, fill=(214, 210, 200))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(OUT)


main()
