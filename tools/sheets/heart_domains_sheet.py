"""四つの心臓の「技の名前」と「領域」の撮影をまとめて、docs/review/previews/heart_domains_sheet.png を作る。

使い方: python tools/sheets/heart_domains_sheet.py [出力のパス]
入力: run/shots/heart_domains/shots/heart_domains/{cue,card,active}_{whale,tide,storm,ember}.png
      (./gradlew runShots -Pscript=tools/shots/heart_domains.txt。日本語の画面・音は 0)
出力: 4 列 (岩鯨・潮殻・雷翼・余燼) × 3 行 (技の名前の一行 / 領域の題字と天の声 / 領域のあいだ) の表。ラベル付き。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/heart_domains_sheet.png"
SHOTS = ROOT / "run/shots/heart_domains/shots/heart_domains"
CELL = (640, 360)
LABEL_H = 26
HEARTS = [("whale", "岩鯨の心臓  石の海"), ("tide", "潮殻の心臓  満ち潮"), ("storm", "雷翼の心臓  雷の檻"), ("ember", "余燼の心臓  炉心")]
ROWS = [("cue", "技の名前 (ボスバーの下)"), ("card", "領域の宣言 (題字 + 天の声)"), ("active", "領域のあいだ (霧・床の紋・予告・残りと砕く進み)")]


def font(size):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/yugothic.ttf"):
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def load(kind, heart):
    f = SHOTS / f"{kind}_{heart}.png"
    if not f.exists():
        return Image.new("RGB", CELL, (60, 20, 20))
    return Image.open(f).convert("RGB").resize(CELL, Image.LANCZOS)


def main():
    fnt = font(15)
    img = Image.new("RGB", (CELL[0] * len(HEARTS), (CELL[1] + LABEL_H) * len(ROWS)), (16, 16, 24))
    d = ImageDraw.Draw(img)
    for r, (kind, rlabel) in enumerate(ROWS):
        for c, (heart, hlabel) in enumerate(HEARTS):
            x = c * CELL[0]
            y = r * (CELL[1] + LABEL_H)
            d.rectangle((x, y, x + CELL[0], y + LABEL_H), fill=(16, 16, 24))
            d.text((x + 8, y + 4), f"{hlabel} / {rlabel}", fill=(235, 235, 235), font=fnt)
            img.paste(load(kind, heart), (x, y + LABEL_H))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
