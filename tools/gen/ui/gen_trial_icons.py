# -*- coding: utf-8 -*-
"""試練の板 (TrialBoardScreen) の絵の番号 icons.png を書く。8 列 × 3 行、一つ 32 px、全部で 256 × 96。
番号は TrialBoardScreen の定数と同じ (0 歩 1 智 2 闘 3 配置 4 数 5 敵 6 危険 7 目標 / 8〜14 類 (cross beat carry solve together fight escort) / 15 静か 16 極 17 不明)。
使い方: python tools/gen/ui/gen_trial_icons.py
出力: src/main/resources/assets/skyabyss/textures/gui/trial/icons.png
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/trial/icons.png"
S = 32
COLS, ROWS = 8, 3
GOLD = (242, 193, 78, 255)
TEAL = (127, 214, 208, 255)
TEXT = (230, 232, 238, 255)
DARK = (14, 17, 24, 255)
PURPLE = (194, 91, 214, 255)


def _new():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def walk(d):  # 足跡
    for cx, cy in ((11, 12), (21, 20)):
        d.ellipse((cx - 4, cy - 6, cx + 4, cy + 6), fill=TEAL)
        for tx in (-3, 0, 3):
            d.ellipse((cx + tx - 1, cy - 9 - abs(tx) // 2, cx + tx + 1, cy - 7 - abs(tx) // 2), fill=TEAL)


def wit(d):  # 目 (見る・智)
    d.ellipse((3, 9, 29, 23), outline=TEAL, width=2)
    d.ellipse((12, 12, 20, 20), fill=GOLD)


def fight(d):  # 交差した剣
    d.line((5, 5, 27, 27), fill=TEXT, width=3)
    d.line((27, 5, 5, 27), fill=TEXT, width=3)
    d.line((4, 26, 9, 21), fill=GOLD, width=3)
    d.line((28, 26, 23, 21), fill=GOLD, width=3)


def layout(d):  # 配置 (格子)
    for x in (4, 14):
        for y in (4, 14):
            d.rectangle((x, y, x + 13, y + 13), outline=GOLD, width=2)


def number(d):  # 数 (さいころ)
    d.rounded_rectangle((4, 4, 27, 27), radius=4, outline=TEXT, width=2)
    for x, y in ((10, 10), (21, 10), (15, 16), (10, 22), (21, 22)):
        d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=TEXT)


def enemy(d):  # 敵 (頭蓋)
    d.ellipse((5, 4, 27, 22), fill=TEXT)
    d.rectangle((10, 18, 22, 27), fill=TEXT)
    d.ellipse((9, 11, 14, 17), fill=DARK)
    d.ellipse((18, 11, 23, 17), fill=DARK)


def hazard(d):  # 危険 (三角の注意)
    d.polygon([(16, 4), (29, 27), (3, 27)], outline=GOLD, width=2)
    d.line((16, 11, 16, 20), fill=GOLD, width=3)
    d.ellipse((14, 22, 18, 25), fill=GOLD)


def goal(d):  # 目標 (旗)
    d.line((8, 4, 8, 28), fill=TEXT, width=2)
    d.polygon([(9, 5), (26, 10), (9, 16)], fill=GOLD)


def cross(d):  # 類: 交差
    d.line((7, 7, 25, 25), fill=GOLD, width=4)
    d.line((25, 7, 7, 25), fill=GOLD, width=4)


def beat(d):  # 類: 打つ (星の破裂)
    d.polygon([(16, 3), (19, 12), (29, 12), (21, 18), (24, 28), (16, 22), (8, 28), (11, 18), (3, 12), (13, 12)], fill=GOLD)


def carry(d):  # 類: 運ぶ (包み)
    d.rounded_rectangle((5, 10, 27, 27), radius=3, outline=TEAL, width=2)
    d.arc((10, 3, 22, 15), 180, 360, fill=TEAL, width=2)


def solve(d):  # 類: 解く (丸に印)
    d.ellipse((4, 4, 28, 28), outline=TEAL, width=2)
    d.line((10, 16, 14, 21, 22, 11), fill=TEAL, width=3)


def together(d):  # 類: ともに (二つの丸)
    d.ellipse((3, 9, 17, 23), outline=TEAL, width=2)
    d.ellipse((15, 9, 29, 23), outline=TEAL, width=2)


def fight_kind(d):  # 類: 戦う (盾)
    d.polygon([(5, 6), (27, 6), (25, 18), (16, 28), (7, 18)], outline=PURPLE, width=2)
    d.line((16, 10, 16, 22), fill=PURPLE, width=2)


def escort(d):  # 類: 護る (矢印つきの盾)
    d.polygon([(6, 5), (26, 5), (24, 17), (16, 27), (8, 17)], outline=TEAL, width=2)
    d.line((12, 16, 20, 16), fill=TEAL, width=2)


def quiet(d):  # 静か (月)
    d.ellipse((5, 4, 27, 28), fill=TEXT)
    d.ellipse((11, 2, 31, 26), fill=(0, 0, 0, 0))


def extreme(d):  # 極 (紫の星)
    d.polygon([(16, 2), (20, 12), (30, 12), (22, 18), (25, 29), (16, 23), (7, 29), (10, 18), (2, 12), (12, 12)], fill=PURPLE)


def unknown(d):  # 不明 (?)
    d.arc((7, 4, 25, 20), 200, 20, fill=TEXT, width=3)
    d.line((16, 17, 16, 21), fill=TEXT, width=3)
    d.ellipse((14, 25, 18, 29), fill=TEXT)


ICONS = [walk, wit, fight, layout, number, enemy, hazard, goal,
         cross, beat, carry, solve, together, fight_kind, escort,
         quiet, extreme, unknown]


def main():
    sheet = Image.new("RGBA", (COLS * S, ROWS * S), (0, 0, 0, 0))
    for i, draw in enumerate(ICONS):
        im, d = _new()
        draw(d)
        sheet.paste(im, ((i % COLS) * S, (i // COLS) * S), im)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("wrote", OUT, sheet.size, len(ICONS), "icons")


if __name__ == "__main__":
    main()
