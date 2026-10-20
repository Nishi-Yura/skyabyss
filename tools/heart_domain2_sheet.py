"""岩鯨の心臓の領域の仕掛けと、ボスの HUD の撮影をまとめて、docs/review/previews/heart_domain2_sheet.png を作る。

使い方: python tools/heart_domain2_sheet.py [出力のパス]
入力: run/shots/heart_domain2/shots/heart_domain2/{demo1,demo3,tap,wrong,right}.png (./gradlew runShots -Pscript=tools/shots/heart_domain2.txt。日本語の画面・音は 0)
      src/main/resources/assets/skyabyss/textures/gui/attack/*.png (技の印の一部を下の帯に並べる)
出力: 3 列 x 2 行の撮影 (手本 1・手本 3・自分の番 / 間違い / 正解) + 技の印の帯。ラベル付き。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/heart_domain2_sheet.png"
SHOTS = ROOT / "run/shots/heart_domain2/shots/heart_domain2"
ICONS = ROOT / "src/main/resources/assets/skyabyss/textures/gui/attack"
CELL = (640, 360)
LABEL_H = 24
CELLS = [
    ("demo1", "自分の番: 板「光った順に骨を叩け 1/3」。骨の上に光の線"),
    ("demo3", "手本: 板「骨が鳴る順を覚えろ」。光った骨は金色・音も鳴る"),
    ("tap", "手本の最中: 技の名前 (印つき) が二つ出ても、右上の板は動かない"),
    ("wrong", "間違い: 叩いた骨が赤く光る・板が赤・低い音"),
    ("right", "正解: 叩いた骨が緑・板が緑・澄んだ音 (2/3 へ)"),
    ("demo0", "手本の前: 領域の板と、ボスバー (少し上へ・名前と顔の印は見える)"),
]
STRIP = ["whale_sea_wave", "whale_sea_spike", "whale_thrust", "whale_beam", "tide_flood_rise", "tide_whirl", "storm_cage_bars", "storm_abyss_pillars", "ember_core_rings", "ember_orb", "captain_erupt", "pearl_keeper_volley", "raiju_rail", "ash_smith_quench"]


def font(size):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def load(name):
    f = SHOTS / (name + ".png")
    if not f.exists():
        return Image.new("RGB", CELL, (60, 20, 20))
    return Image.open(f).convert("RGB").resize(CELL, Image.LANCZOS)


def main():
    fnt = font(14)
    cols, rows = 3, 2
    strip_h = 86
    img = Image.new("RGB", (CELL[0] * cols, (CELL[1] + LABEL_H) * rows + strip_h), (16, 16, 24))
    d = ImageDraw.Draw(img)
    for i, (name, label) in enumerate(CELLS):
        x = (i % cols) * CELL[0]
        y = (i // cols) * (CELL[1] + LABEL_H)
        d.text((x + 8, y + 4), label, fill=(235, 235, 235), font=fnt)
        img.paste(load(name), (x, y + LABEL_H))
    y0 = rows * (CELL[1] + LABEL_H)
    d.text((8, y0 + 4), "技の名前の印 (全 85 種のうち 14 種。16px のドット絵)", fill=(235, 235, 235), font=fnt)
    for i, key in enumerate(STRIP):
        f = ICONS / (key + ".png")
        if f.exists():
            ic = Image.open(f).convert("RGBA").resize((56, 56), Image.NEAREST)
            img.paste(ic, (8 + i * 66, y0 + 24), ic)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
