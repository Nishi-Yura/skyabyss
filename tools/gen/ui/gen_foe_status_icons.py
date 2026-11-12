# -*- coding: utf-8 -*-
# 敵の状態のアイコン (9x9 を 1 列に並べた絵。client/hud/FoeStatusClient が番号で使う)。
# 縁の色で弱体 (赤い縁) と強化 (金の縁) を分ける。中の絵は 7x7。
# 出力: src/main/resources/assets/skyabyss/textures/gui/foe_status.png
# 番号は combat/FoeStatus.Kind の並びと同じ。足すときは末尾に。
#   python -X utf8 tools/gen/ui/gen_foe_status_icons.py
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "main", "resources", "assets", "skyabyss",
                   "textures", "gui", "foe_status.png")

PAL = {
    ".": None,
    "k": (24, 22, 30, 255),      # 濃い色
    "w": (240, 236, 224, 255),   # 白
    "s": (178, 188, 204, 255),   # 鋼
    "S": (110, 120, 142, 255),   # 鋼の影
    "g": (250, 210, 90, 255),    # 金
    "o": (246, 140, 60, 255),    # 橙
    "O": (200, 70, 30, 255),     # 濃い橙
    "y": (255, 236, 140, 255),   # 炎の芯
    "r": (232, 70, 80, 255),     # 赤
    "R": (150, 30, 44, 255),     # 赤の影
    "b": (110, 176, 246, 255),   # 青
    "B": (50, 96, 176, 255),     # 青の影
    "c": (120, 230, 222, 255),   # 青緑
    "p": (196, 140, 255, 255),   # 紫
    "P": (120, 70, 190, 255),    # 紫の影
    "l": (150, 226, 110, 255),   # 緑
    "L": (70, 140, 60, 255),     # 緑の影
    "q": (255, 150, 186, 255),   # 桃
    "T": (90, 90, 100, 255),     # 灰
}

PLATE = (16, 14, 22, 235)
RIM_DEBUFF = (190, 58, 70, 255)
RIM_BUFF = (226, 190, 92, 255)

# (中の絵, 強化か)
ICONS = [
    # 0 燃焼 (炎)
    (["...o...",
      "..oo.o.",
      ".ooOoo.",
      ".oOyOo.",
      "ooyyyoo",
      "oOyyyOo",
      ".OOOOO."], False),
    # 1 鈍足 (砂時計)
    (["bbbbbbb",
      ".BwwwB.",
      "..BwB..",
      "...B...",
      "..BwB..",
      ".BwwwB.",
      "bbbbbbb"], False),
    # 2 弱体 (下向きの矢)
    (["..ppp..",
      "..ppp..",
      "..ppp..",
      "PpppppP",
      ".PpppP.",
      "..PpP..",
      "...P..."], False),
    # 3 毒 (どくろ)
    ([".lllll.",
      "lllllll",
      "lkklkkl",
      "lllllll",
      ".lLLLl.",
      ".l.l.l.",
      "......."], False),
    # 4 出血 (しずく)
    (["...r...",
      "..rrr..",
      ".rrrrr.",
      ".rwrrr.",
      "rwrrrRr",
      "rrrrrRr",
      ".rRRRr."], False),
    # 5 目くらまし (斜線の入った目)
    (["......T",
      "..wwwT.",
      ".wwkTw.",
      "wwkTkww",
      ".wTkww.",
      ".Twww..",
      "T......"], False),
    # 6 浮き (上向きの山形)
    (["...w...",
      "..w.w..",
      ".w...w.",
      "...w...",
      "..w.w..",
      ".w...w.",
      "......."], False),
    # 7 崩れ (星)
    ([".g.....",
      "gwg..g.",
      ".g..gwg",
      ".....g.",
      "..g....",
      ".gwg...",
      "..g...."], False),
    # 8 剛力 (上向きの矢)
    (["...r...",
      "..rrr..",
      ".rrrrr.",
      "rrrrrrr",
      "..rrr..",
      "..rRr..",
      "..rRr.."], True),
    # 9 守り (盾)
    ([".sssss.",
      "swwsssS",
      "swsssSS",
      "ssssSSS",
      ".sssSS.",
      "..sSS..",
      "...S..."], True),
    # 10 俊足 (右向きの山形)
    (["c..c...",
      ".c..c..",
      "..c..c.",
      "...c..c",
      "..c..c.",
      ".c..c..",
      "c..c..."], True),
    # 11 再生 (桃のハート)
    ([".qq.qq.",
      "qwqqqqq",
      "qqqqqqq",
      ".qqqqq.",
      "..qqq..",
      "...q...",
      "......."], True),
    # 12 障壁 (金のハート)
    ([".gg.gg.",
      "gwggggg",
      "ggggggg",
      ".ggggg.",
      "..ggg..",
      "...g...",
      "......."], True),
]


def main():
    img = Image.new("RGBA", (9 * len(ICONS), 9), (0, 0, 0, 0))
    for n, (rows, buff) in enumerate(ICONS):
        ox = n * 9
        rim = RIM_BUFF if buff else RIM_DEBUFF
        for y in range(9):
            for x in range(9):
                corner = (x in (0, 8)) and (y in (0, 8))
                if corner:
                    continue
                edge = x in (0, 8) or y in (0, 8)
                img.putpixel((ox + x, y), rim if edge else PLATE)
        assert len(rows) == 7, n
        for y, row in enumerate(rows):
            assert len(row) == 7, (n, y)
            for x, ch in enumerate(row):
                c = PAL[ch]
                if c is not None:
                    img.putpixel((ox + 1 + x, 1 + y), c)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    big = img.resize((img.width * 8, img.height * 8), Image.NEAREST)
    print(OUT, img.size)
    return big


if __name__ == "__main__":
    big = main()
    prev = os.environ.get("PREVIEW")
    if prev:
        big.save(prev)
