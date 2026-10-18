"""予告の床の印 (decal) の撮影をまとめて、docs/review/previews/telegraph_sheet.png を作る。

使い方: python tools/telegraph_sheet.py [出力のパス]
入力: run/shots/<台本>/shots/<台本>/*.png。台本は tools/shots/ の
      telegraph_stomp.txt (上から、番兵長)、telegraph_stomp_eye.txt (目の高さ、番兵長)、
      telegraph_heart.txt (上から、心臓)、telegraph_heart_eye.txt (目の高さ、心臓)
出力: 3 列の表。上半分 = 目の高さ、下半分 = 上から (行ごとに 3 枚)。ラベル付き。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/telegraph_sheet.png"
CELL = (640, 360)
COLS = 3
LABEL_H = 18

# (台本, 絵の名前, ラベル)。3 枚ずつ 1 行
EYE = [
    ("telegraph_stomp_eye", "eye_stomp_a", "eye level: captain stomp, circle appears"),
    ("telegraph_stomp_eye", "eye_stomp_b", "eye level: stomp fills"),
    ("telegraph_stomp_eye", "eye_stomp_hit", "eye level: stomp impact flash"),
    ("telegraph_stomp_eye", "eye_sweep_a", "eye level: captain sweep cone fills"),
    ("telegraph_stomp_eye", "eye_sweep_b", "eye level: sweep, swung part"),
    ("telegraph_heart_eye", "eye_thrust_a", "eye level: heart thrust x3"),
    ("telegraph_heart_eye", "eye_leap_a", "eye level: heart slam, core circle + green lanes"),
    ("telegraph_heart_eye", "eye_leap_b", "eye level: heart slam, expanding ring"),
    ("telegraph_heart_eye", "eye_chain_b", "eye level: heart chain, sweeping"),
]
TOP = [
    ("telegraph_stomp", "stomp_a", "above: captain stomp, circle appears"),
    ("telegraph_stomp", "stomp_b", "above: stomp fills"),
    ("telegraph_stomp", "stomp_hit", "above: stomp impact flash"),
    ("telegraph_stomp", "sweep_a", "above: captain sweep cone fills"),
    ("telegraph_heart", "thrust_a", "above: heart thrust x3"),
    ("telegraph_heart", "leap_a", "above: heart slam, core circle + green lanes"),
    ("telegraph_heart", "leap_b", "above: heart slam, expanding ring"),
    ("telegraph_heart", "chain_a", "above: heart chain, 3 sweep sectors"),
    ("telegraph_heart", "chain_b", "above: heart chain, sweeping"),
    ("telegraph_heart", "beam_a", "above: heart beam, fans"),
    ("telegraph_heart", "crack_a", "above: heart crack, lines at 5 targets"),
    ("telegraph_heart", "drop_a", "above: heart shell drop, 6 + 2 circles"),
]


def load(script, name):
    f = ROOT / "run/shots" / script / "shots" / script / f"{name}.png"
    if not f.exists():
        return Image.new("RGB", CELL, (60, 20, 20))
    return Image.open(f).convert("RGB").resize(CELL, Image.LANCZOS)


def block(items, img, y0):
    d = ImageDraw.Draw(img)
    for i, (script, name, label) in enumerate(items):
        x = (i % COLS) * CELL[0]
        y = y0 + (i // COLS) * (CELL[1] + LABEL_H)
        d.rectangle((x, y, x + CELL[0], y + LABEL_H), fill=(16, 16, 24))
        d.text((x + 6, y + 3), label, fill=(235, 235, 235))
        img.paste(load(script, name), (x, y + LABEL_H))
    return y0 + ((len(items) + COLS - 1) // COLS) * (CELL[1] + LABEL_H)


def main():
    rows = ((len(EYE) + COLS - 1) // COLS) + ((len(TOP) + COLS - 1) // COLS)
    img = Image.new("RGB", (CELL[0] * COLS, rows * (CELL[1] + LABEL_H)), (16, 16, 24))
    y = block(EYE, img, 0)
    block(TOP, img, y)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
