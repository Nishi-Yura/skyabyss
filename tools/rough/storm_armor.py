"""雷翼の羽衣 (thunder_wing の防具 4 部位) のラフ 3 案 (A/B/C) を作る。

使い方 (リポジトリ直下で): python -X utf8 tools/rough/storm_armor.py
出力 (docs/review/storm_gear_roughs/):
  compare_armor.png             3 案の 3D 比較 (正面 / 背面 / 3/4、人型の台に着せた絵)
  compare_armor_icons.png       3 案のインベントリ用アイコン比較 (兜・胸・脚・靴)
  armor/<a|b|c>/thunder_wing_armor.geo.json      GeckoLib の防具モデル (骨は tide_shell_armor と同じ 8 本)
  armor/<a|b|c>/thunder_wing_armor.png           本体テクスチャ
  armor/<a|b|c>/thunder_wing_armor_glowmask.png  稲妻の発光部のマスク (画素が 1 つ以上ある)
  armor/<a|b|c>/icons/thunder_wing_<部位>.png    32x32 のアイコン
  armor/<a|b|c>/layers/thunder_wing_layer_{1,2}.png  バニラの防具の層 (64x32)
  armor/<a|b|c>/check.json                       重なり・埋まりの検査結果
プレビューは docs/review/previews/storm_armor/ (git に入れない)。

形の決まり (tide_armor.py と同じ):
- 座標は 0.5 単位の格子 (テクスチャは 1 単位 = K 画素)。回転は使わない。
- 箱どうしは重ねない (面で接するだけ)。check_overlaps で確かめる。
- 防具は人型の体から 0.5 単位離して置く。頭は覗き穴 (顔の正面 y 25..29.5) を開けておく。
- 3 案で同じ骨・同じ寸法。形が違うのは、A 羽重ねの外套 / B 鷲の頭巾 / C 雷縞の衣。
- アイコンは 3D の箱を正面から投影して描く (色・飾りが 3D と同じになる)。
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from voxel import check_overlaps, icon_big, load_geo, render, sheet  # noqa: E402

ROOT = HERE.parents[1]
OUT = ROOT / "docs/review/storm_gear_roughs"
ARMOR_OUT = OUT / "armor"
PREVIEW = ROOT / "docs/review/previews/storm_armor"

NAME = "thunder_wing_armor"
K = 4                       # 1 単位あたりの画素 (texel) 数
TOL = 1e-6
PIECES = ["helmet", "chestplate", "leggings", "boots"]
PIECE_BONES = {"helmet": ["armorHead"], "chestplate": ["armorBody", "armorRightArm", "armorLeftArm"],
               "leggings": ["armorRightLeg", "armorLeftLeg"], "boots": ["armorRightBoot", "armorLeftBoot"]}

LIGHT = np.array([-0.45, 0.72, -0.55])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
FACES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
         "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

OUTLINE = (20, 18, 28)
# 材料の名前 -> 色 (雷翼の鷲 A 案の色。B / C は明暗と配分を変える)
OPTIONS = {
    "a": {
        "title": "A 羽重ねの外套",
        "sub": "slate plumage layers, copper crest helm, lightning glow",
        "pal": {"shell": (78, 96, 128), "scute": (132, 152, 184), "trim": (232, 236, 242),
                "accent": (198, 112, 60), "glow": (150, 212, 255)},
    },
    "b": {
        "title": "B 鷲の頭巾",
        "sub": "dark slate hood with copper beak, wing-shaped pauldrons",
        "pal": {"shell": (48, 60, 86), "scute": (160, 168, 184), "trim": (232, 236, 242),
                "accent": (140, 74, 40), "glow": (226, 238, 255)},
    },
    "c": {
        "title": "C 雷縞の衣",
        "sub": "light cloth-and-feather robe, lightning stripes",
        "pal": {"shell": (78, 96, 128), "scute": (160, 168, 184), "trim": (232, 236, 242),
                "accent": (198, 112, 60), "glow": (226, 238, 255)},
    },
}
RECOMMEND = "a"   # 推奨案 (install_storm_armor.py の既定)。比べたあとで決める

# 人型の台 (バニラの体の箱。防具の位置と見比べるため)
STAND = [
    ((-4, 24, -4), (4, 32, 4)),    # 頭
    ((-4, 12, -2), (4, 24, 2)),    # 胴
    ((-8, 10, -2), (-4, 22, 2)),   # 右腕
    ((4, 10, -2), (8, 22, 2)),     # 左腕
    ((-4, 0, -2), (0, 12, 2)),     # 右脚
    ((0, 0, -2), (4, 12, 2)),      # 左脚
]


# ---------------------------------------------------------------- 形の部品
def bx(x0, y0, z0, x1, y1, z1, mat):
    return {"min": (x0, y0, z0), "max": (x1, y1, z1), "mat": mat}


def mirror(b):
    (x0, y0, z0), (x1, y1, z1) = b["min"], b["max"]
    return {"min": (-x1, y0, z0), "max": (-x0, y1, z1), "mat": b["mat"]}


def _ov(a, b):
    ov = np.minimum(a["max"], b["max"]) - np.maximum(a["min"], b["min"])
    return bool((ov > TOL).all())


def relief(boxes, mat, face, plane, x0, x1, y0, y1, cell=1.5, size=1.0, depth=0.5):
    """板の面 (face: "z-" は手前、"z+" は奥) に、羽の盛り上がりを千鳥に並べる。既存の箱と重なる所は置かない。"""
    out = []
    r = 0
    y = y0
    while y + size <= y1 + TOL:
        x = x0 + (cell / 2 if r % 2 else 0)
        while x + size <= x1 + TOL:
            if face == "z-":
                b = bx(x, y, plane - depth, x + size, y + size, plane, mat)
            else:
                b = bx(x, y, plane, x + size, y + size, plane + depth, mat)
            if not any(_ov(b, c) for c in boxes + out):
                out.append(b)
            x += cell
        y += cell
        r += 1
    return out


def tiles(boxes, mat, axis, sign, plane, lo, hi, cell=1.5, size=1.0, depth=0.5):
    """面 (axis 軸に垂直、plane の位置、sign は外向き +1 / -1) に、盛り上がった羽の房を千鳥に並べる。
    lo / hi は範囲 (x, y, z の順、axis の成分は使わない)。既存の箱と体積で重なる所は置かない。"""
    ai = "xyz".index(axis)
    ui, vi = [i for i in range(3) if i != ai]
    out = []
    v = lo[vi]
    r = 0
    while v + size <= hi[vi] + TOL:
        u = lo[ui] + (cell / 2 if r % 2 else 0)
        while u + size <= hi[ui] + TOL:
            a0, a1 = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
            a0[ui], a1[ui] = u, u + size
            a0[vi], a1[vi] = v, v + size
            if sign > 0:
                a0[ai], a1[ai] = plane, plane + depth
            else:
                a0[ai], a1[ai] = plane - depth, plane
            b = bx(*a0, *a1, mat)
            if not any(_ov(b, c) for c in boxes + out):
                out.append(b)
            u += cell
        v += cell
        r += 1
    return out


# ---------------------------------------------------------------- 3 案の形
def parts_a():
    """A 羽重ねの外套: 灰青の羽を重ねた外套。兜は銅の冠毛、背に 2 段の羽の垂れ。胸に銅の留め金と稲妻の筋。"""
    head = [
        bx(-4.5, 32, -4.5, 4.5, 32.5, 4.5, "trim"),       # 兜の縁 (白羽)
        bx(-4, 32.5, -4, 4, 33.5, 4, "shell"),            # 兜の丸み
        bx(-3, 33.5, -3, 3, 34.5, 3, "shell"),            # 頂の段
        bx(-0.5, 34.5, -3, 0.5, 35.5, 3, "accent"),       # 銅の冠毛 (前から後ろ)
        bx(-4.5, 29.5, -5, 4.5, 32, -4, "shell"),         # 額の庇 (覗き穴の上)
        bx(-2.5, 31, -5.5, -1, 31.5, -5, "glow"),         # 額の稲妻
        bx(0.5, 30, -5.5, 2.5, 30.5, -5, "glow"),
        bx(-5, 25, -3, -4, 31, 3, "shell"),               # 頬の羽 (右。左は反転)
        bx(-4.5, 22, 4, 4.5, 32, 4.5, "shell"),           # 後ろの垂れ (上)
    ]
    head += [mirror(head[7])]
    head += tiles(head, "scute", "z", 1, 4.5, (-4.5, 22, 0), (4.5, 32, 0))     # 垂れの羽の房

    body = [
        bx(-4, 13, -3.5, 4, 24, -2.5, "shell"),           # 胸の板 (体から 0.5 離す)
        bx(-4, 12, -3.5, 4, 13, -2.5, "trim"),            # 腰の白羽の縁
        bx(-4, 12, 2.5, 4, 24, 3.5, "shell"),             # 背の板
        bx(-4.5, 14, 3.5, 4.5, 22, 4.5, "shell"),         # 外套 (背の羽の層 1)
        bx(-4.5, 8, 4.5, 4.5, 14, 5, "trim"),             # 外套 (背の羽の層 2、白羽)
        bx(-0.5, 17, -4, 0.5, 20, -3.5, "accent"),        # 胸の銅の留め金
        bx(-2.5, 21, -4, -1.5, 21.5, -3.5, "glow"),       # 胸の稲妻 (折れ線)
        bx(-1.5, 20.5, -4, -1, 21, -3.5, "glow"),
    ]
    body += tiles(body, "scute", "z", 1, 4.5, (-4.5, 14, 0), (4.5, 22, 0))     # 外套の羽の房
    body += relief(body, "scute", "z-", -3.5, -3.5, 3.5, 14, 22)               # 胸の羽の盛り上がり

    arm = [
        bx(-9, 22, -3, -4, 23.5, 3, "shell"),             # 肩の羽 (上)
        bx(-9.5, 23.5, -3.5, -4, 24, 3.5, "trim"),        # 肩の白羽の縁
        bx(-8.5, 10.5, -2.5, -8, 21.5, 2.5, "shell"),     # 袖 (外側)
        bx(-8, 10.5, -2.5, -4, 21.5, -2, "shell"),        # 袖 (前)
        bx(-8, 10.5, 2, -4, 21.5, 2.5, "shell"),          # 袖 (後ろ)
        bx(-8.5, 9.5, -2.5, -4.5, 10, 2.5, "trim"),       # 袖口の白羽
    ]
    arm += tiles(arm, "scute", "x", -1, -8.5, (-8.5, 11, -2.5), (-8.5, 21, 2.5))  # 袖の羽の房

    leg = [
        bx(-4, 6, -3, 0, 11, -2.5, "shell"),              # 前の板
        bx(-4, 11, -3, 0, 12, -2.5, "trim"),              # 前の白羽の縁
        bx(-4, 6, 2.5, 0, 11, 3, "shell"),                # 後ろの板
        bx(-4, 11, 2.5, 0, 12, 3, "trim"),
        bx(-4.5, 6, -2.5, -4, 10, 2.5, "shell"),          # 外側の板
        bx(-2.5, 8, -3.5, -1.5, 8.5, -3, "glow"),         # 腿の稲妻
    ]
    leg += relief(leg, "scute", "z-", -3, -3.5, -0.5, 6, 10)                  # 腿の羽の盛り上がり

    boot = [
        bx(-4, 0, -3, 0, 5.5, -2.5, "shell"),             # 靴の前
        bx(-4, 5.5, -3, 0, 6, -2.5, "trim"),              # 足首の白羽
        bx(-4.5, 0, -2.5, -4, 6, 2.5, "shell"),           # 外側
        bx(-4, 0, 2.5, 0, 5.5, 3, "shell"),               # 後ろ
        bx(-4, 5.5, 2.5, 0, 6, 3, "trim"),
        bx(-4, 0, -4, 0, 2, -3, "accent"),                # つま先の銅
    ]
    boot += tiles(boot, "scute", "x", -1, -4.5, (-4.5, 0, -2.5), (-4.5, 6, 2.5))  # 外側の羽の房

    return _sides(head, body, arm, leg, boot)


def parts_b():
    """B 鷲の頭巾: 暗い灰青の頭巾に銅のくちばし。肩は翼の形に広がり、羽の段が外へ出る。胸は白羽の V と銅の帯。"""
    head = [
        bx(-4.5, 32, -4.5, 4.5, 32.5, 4.5, "trim"),       # 頭巾の縁
        bx(-4, 32.5, -4, 4, 34, 4, "shell"),              # 頭巾の丸み
        bx(-1, 34, -3, 1, 35, 1, "trim"),                 # 頭の白羽の冠
        bx(-1, 35, 1, 1, 36, 4, "trim"),                  # 冠の後ろ
        bx(-4.5, 29.5, -5, 4.5, 32, -4, "shell"),         # 額 (覗き穴の上)
        bx(-1.5, 29.5, -6, 1.5, 31.5, -5, "accent"),      # 鷲のくちばし
        bx(-0.5, 28.5, -6.5, 0.5, 29.5, -6, "accent"),    # くちばしの先
        bx(-3.5, 31, -5.5, -2.5, 31.5, -5, "glow"),       # 額の稲妻
        bx(2.5, 31, -5.5, 3.5, 31.5, -5, "glow"),
        bx(-5, 25, -3, -4, 31, 3, "shell"),               # 頭巾の垂れ (右。左は反転)
        bx(-4.5, 22, 4, 4.5, 32, 4.5, "shell"),           # 後ろの垂れ
    ]
    head += [mirror(head[9])]
    head += tiles(head, "trim", "z", 1, 4.5, (-4.5, 22, 0), (4.5, 32, 0))     # 垂れの白羽の房

    body = [
        bx(-4, 12, -3.5, 4, 24, -2.5, "shell"),           # 胸の板
        bx(-2.5, 15, -4, 2.5, 23, -3.5, "trim"),          # 白羽の V
        bx(-4, 12, -4, 4, 13.5, -3.5, "accent"),          # 銅の帯
        bx(-4, 12, 2.5, 4, 24, 3.5, "shell"),             # 背の板
        bx(-5, 16, 3.5, 5, 22, 4.5, "shell"),             # 翼の外套 (上)
        bx(-4.5, 8, 3.5, 4.5, 16, 4.5, "shell"),          # 翼の外套 (下)
        bx(-0.5, 18, 4.5, 0.5, 22, 5, "glow"),            # 背の稲妻
    ]
    body += tiles(body, "scute", "z", 1, 4.5, (-5, 16, 0), (5, 22, 0))
    body += tiles(body, "trim", "z", 1, 4.5, (-4.5, 8, 0), (4.5, 16, 0))

    arm = [
        bx(-10, 22, -3.5, -4, 23.5, 3.5, "shell"),        # 翼の肩 (上の段)
        bx(-11, 19, -3, -8.5, 22, 3, "scute"),            # 翼の段 1 (外へ広がる)
        bx(-11.5, 16, -2.5, -8.5, 19, 2.5, "shell"),      # 翼の段 2
        bx(-12.5, 17, -1, -11.5, 18, 1, "accent"),        # 翼の先 (銅)
        bx(-7.5, 23.5, -0.5, -5.5, 24, 0.5, "glow"),      # 肩の稲妻
        bx(-8.5, 10.5, -2.5, -8, 21.5, 2.5, "shell"),     # 袖 (外側)
        bx(-8, 10.5, -2.5, -4, 21.5, -2, "shell"),        # 袖 (前)
        bx(-8, 10.5, 2, -4, 21.5, 2.5, "shell"),          # 袖 (後ろ)
        bx(-8.5, 9.5, -2.5, -4.5, 10, 2.5, "trim"),       # 袖口の白羽
    ]
    arm += tiles(arm, "scute", "x", -1, -11, (-11, 16, -2.5), (-11, 19, 2.5))   # 翼の段の羽の房

    leg = [
        bx(-4, 6, -3, 0, 12, -2.5, "shell"),              # 前の板 (羽の裾)
        bx(-4, 11, -3.5, 0, 12, -3, "trim"),              # 前の白羽の縁
        bx(-4.5, 6, -2.5, -4, 10, 2.5, "shell"),          # 外側
        bx(-4, 6, 2.5, 0, 12, 3, "shell"),                # 後ろ
        bx(-2, 8, -3.5, -1, 9, -3, "glow"),               # 腿の稲妻
    ]
    leg += tiles(leg, "scute", "z", -1, -3, (-4, 6, 0), (0, 11, 0))     # 前の灰色の筋
    leg += tiles(leg, "trim", "z", 1, 3, (-4, 6, 0), (0, 11, 0))

    boot = [
        bx(-4, 0, -3, 0, 5.5, -2.5, "shell"),             # 靴の前
        bx(-4, 5.5, -3, 0, 6, -2.5, "accent"),            # 足首の銅
        bx(-4.5, 0, -2.5, -4, 6, 2.5, "shell"),           # 外側
        bx(-4, 0, 2.5, 0, 5.5, 3, "shell"),               # 後ろ
        bx(-4, 0, -4, 0, 1.5, -3, "accent"),              # つま先の銅
        bx(-3.5, 0, -5, -0.5, 1, -4, "accent"),           # 爪
    ]
    boot += tiles(boot, "scute", "x", -1, -4.5, (-4.5, 0, -2.5), (-4.5, 6, 2.5))

    return _sides(head, body, arm, leg, boot)


def parts_c():
    """C 雷縞の衣: 明るい布と羽の長い衣。前は白い衣に稲妻の縦縞。袖は広く、裾は脚へ垂れる。"""
    head = [
        bx(-4.5, 32, -4.5, 4.5, 32.5, 4.5, "shell"),      # 頭巾の縁 (灰青)
        bx(-4, 32.5, -4, 4, 33.5, 4, "trim"),             # 頭巾の布
        bx(-3, 33.5, -3, 3, 34.5, 3, "trim"),
        bx(-4.5, 29.5, -5, 4.5, 32, -4, "trim"),          # 額の布 (覗き穴の上)
        bx(-3, 31, -5.5, 3, 31.5, -5, "glow"),            # 額の稲妻の帯
        bx(-5, 25, -3, -4, 31, 3, "shell"),               # 頬の布 (右。左は反転)
        bx(-0.5, 24, 5, 0.5, 27, 5.5, "glow"),            # 後ろの稲妻
        bx(-4.5, 24, 4, 4.5, 32, 5, "trim"),              # 後ろに垂れる布
    ]
    head += [mirror(head[5])]
    head += tiles(head, "scute", "z", 1, 5, (-4.5, 24, 0), (4.5, 32, 0))

    body = [
        bx(-4, 12, -3.5, 4, 24, -2.5, "trim"),            # 衣の前 (長い)
        bx(-4, 12, 2.5, 4, 24, 3.5, "trim"),              # 衣の後ろ
        bx(-4.5, 23, -4, 4.5, 24, -3.5, "accent"),        # 襟 (銅)
        bx(-4, 13, -4, 4, 14, -3.5, "shell"),             # 腰の帯
        bx(-1.5, 14, -4, -1, 21, -3.5, "glow"),           # 稲妻の縦縞 (左)
        bx(1, 14, -4, 1.5, 20, -3.5, "glow"),             # 稲妻の縦縞 (右)
        bx(-4.5, 12, 3.5, 4.5, 24, 4.5, "scute"),         # 背の羽の垂れ
    ]
    body += tiles(body, "scute", "z", -1, -3.5, (-4, 14, 0), (4, 23, 0))         # 衣の布の筋
    body += tiles(body, "shell", "z", 1, 4.5, (-4.5, 12, 0), (4.5, 24, 0))       # 背の羽の房

    arm = [
        bx(-9, 22, -3, -4, 23, 3, "trim"),                # 肩の布
        bx(-9, 10, -2.5, -8, 22, 2.5, "trim"),            # 袖 (外側、広め)
        bx(-8, 10, -2.5, -4, 22, -2, "trim"),             # 袖 (前)
        bx(-8, 10, 2, -4, 22, 2.5, "trim"),               # 袖 (後ろ)
        bx(-9, 9, -3, -4.5, 10, 3, "accent"),             # 袖口の銅
        bx(-9.5, 14, -0.5, -9, 20, 0, "glow"),            # 袖の稲妻
    ]
    arm += tiles(arm, "scute", "x", -1, -9.5, (-9.5, 10, -2.5), (-9.5, 22, 2.5))  # 袖の布の筋

    leg = [
        bx(-4, 6, -3, 0, 12, -2.5, "trim"),               # 裾の前
        bx(-4.5, 6, -2.5, -4, 10, 2.5, "shell"),          # 外側
        bx(-4, 6, 2.5, 0, 12, 3, "trim"),                 # 裾の後ろ
        bx(-2, 7, -3.5, -1.5, 11, -3, "glow"),            # 腿の稲妻
    ]
    leg += tiles(leg, "scute", "z", -1, -3, (-4, 6, 0), (0, 12, 0))

    boot = [
        bx(-4, 0, -3, 0, 6, -2.5, "trim"),                # 靴の前 (布)
        bx(-4.5, 0, -2.5, -4, 6, 2.5, "trim"),            # 外側
        bx(-4, 0, 2.5, 0, 6, 3, "trim"),                  # 後ろ
        bx(-4, 0, -4, 0, 2, -3, "shell"),                 # つま先の灰青
        bx(-2, 2.5, -3.5, -1.5, 4, -3, "glow"),           # 靴の稲妻
    ]
    boot += tiles(boot, "scute", "x", -1, -4.5, (-4.5, 0, -2.5), (-4.5, 6, 2.5))

    return _sides(head, body, arm, leg, boot)


def _sides(head, body, arm, leg, boot):
    """右側 (x<0) の箱を作り、左側は反転して作る。中央の箱は 1 つだけ作る。"""
    return [
        ("armorHead", (0, 24, 0), head),
        ("armorBody", (0, 24, 0), body),
        ("armorRightArm", (-5, 22, 0), arm),
        ("armorLeftArm", (5, 22, 0), [mirror(b) for b in arm]),
        ("armorRightLeg", (-1.9, 12, 0), leg),
        ("armorLeftLeg", (1.9, 12, 0), [mirror(b) for b in leg]),
        ("armorRightBoot", (-1.9, 12, 0), boot),
        ("armorLeftBoot", (1.9, 12, 0), [mirror(b) for b in boot]),
    ]


def parts(opt):
    return {"a": parts_a, "b": parts_b, "c": parts_c}[opt]()


# ---------------------------------------------------------------- UV とテクスチャ
def texel_dims(b):
    return tuple(max(1, round((b["max"][i] - b["min"][i]) * K)) for i in range(3))


def net_faces(U0, V0, dx, dy, dz):
    """箱の展開図 (標準の box UV) を面ごとの (u, v, w, h) に分ける"""
    return {
        "up": (U0 + dz, V0, dx, dz),
        "down": (U0 + dz + dx, V0, dx, dz),
        "west": (U0, V0 + dz, dz, dy),
        "north": (U0 + dz, V0 + dz, dx, dy),
        "east": (U0 + dz + dx, V0 + dz, dz, dy),
        "south": (U0 + 2 * dz + dx, V0 + dz, dx, dy),
    }


def pack(blocks, pad=1):
    """(w, h, key) の矩形を棚詰めで並べ、入る最小の 2 の冪の正方形を返す"""
    for size in (256, 512, 1024, 2048):
        x = y = shelf = 0
        pos = {}
        ok = True
        for w, h, key in sorted(blocks, key=lambda b: (-b[1], -b[0])):
            if x + w > size:
                x = 0
                y += shelf + pad
                shelf = 0
            if w > size or y + h > size:
                ok = False
                break
            pos[key] = (x, y)
            x += w + pad
            shelf = max(shelf, h)
        if ok:
            return size, pos
    raise SystemExit("テクスチャに収まらない")


def paint_face(mat, normal, w, h, rng, pal):
    """1 面を描く。光の向きで明るさを変え、縁の 1 画素を明暗で際立たせる。"""
    base = np.array(pal[mat], float)
    lam = float(np.dot(np.array(normal, float), LIGHT))
    shade = 0.62 + 0.42 * (lam + 1) / 2
    yy, xx = np.mgrid[0:h, 0:w]
    noise = 1 + (rng.random((h, w)) - 0.5) * 0.1
    if mat == "glow":
        img = np.tile(base, (h, w, 1)) * noise[..., None]
        return np.clip(img, 0, 255)
    img = np.tile(base * shade, (h, w, 1)) * noise[..., None]
    if mat in ("shell", "scute", "trim"):
        seam = ((xx % 8) == 0) | ((yy % 8) == 0)   # 羽の継ぎ目
        img[seam] *= 0.78
    if mat == "accent":
        img[rng.random((h, w)) < 0.12] *= 1.3          # 銅のつぶ
    img[yy == 0] *= 1.12
    img[xx == 0] *= 1.12
    edge = (yy == h - 1) | (xx == w - 1)
    img[edge] *= 0.7
    return np.clip(img, 0, 255)


def build_geo(opt):
    """geo (dict) と本体テクスチャ・発光マスク (配列) を作る"""
    pal = OPTIONS[opt]["pal"]
    bones = parts(opt)
    cubes = []
    for bi, (_, _, boxes) in enumerate(bones):
        for ci, b in enumerate(boxes):
            cubes.append((bi, ci, b, texel_dims(b)))
    blocks = [(2 * dz + 2 * dx, dz + dy, (bi, ci)) for bi, ci, _, (dx, dy, dz) in cubes]
    size, pos = pack(blocks)
    tex = np.zeros((size, size, 4), np.uint8)
    glowm = np.zeros((size, size, 4), np.uint8)
    geo_bones = []
    for bi, (bname, pivot, boxes) in enumerate(bones):
        out = []
        for ci, b in enumerate(boxes):
            dx, dy, dz = texel_dims(b)
            U0, V0 = pos[(bi, ci)]
            faces = net_faces(U0, V0, dx, dy, dz)
            rng = np.random.default_rng(bi * 1000 + ci)
            uv = {}
            for fname, (u, v, w, h) in faces.items():
                uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
                rgb = paint_face(b["mat"], FACES[fname], w, h, rng, pal)
                tex[v:v + h, u:u + w, :3] = rgb.astype(np.uint8)
                tex[v:v + h, u:u + w, 3] = 255
                if b["mat"] == "glow":
                    glowm[v:v + h, u:u + w, :3] = pal["glow"]
                    glowm[v:v + h, u:u + w, 3] = 255
            out.append({
                "origin": [_fmt(v) for v in b["min"]],
                "size": [_fmt(b["max"][i] - b["min"][i]) for i in range(3)],
                "uv": uv,
            })
        geo_bones.append({"name": bname, "pivot": [_fmt(c) for c in pivot], "cubes": out})
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.thunder_wing_armor",
                "texture_width": size,
                "texture_height": size,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": geo_bones,
        }],
    }
    return geo, tex, glowm


def _fmt(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


# ---------------------------------------------------------------- 検査
def clearance(geo):
    """防具の箱が人型の体 (台) に食い込んでいないかを調べる"""
    probs = []
    g = geo["minecraft:geometry"][0]
    for b in g["bones"]:
        for i, c in enumerate(b["cubes"]):
            o = np.array(c["origin"], float)
            e = o + np.array(c["size"], float)
            for si, (s0, s1) in enumerate(STAND):
                ov = np.minimum(e, s1) - np.maximum(o, s0)
                if (ov > TOL).all():
                    probs.append(f"{b['name']}[{i}] が体 {si} に食い込む vol={ov.prod():.2f}")
    return probs


def count_cubes(geo):
    return sum(len(b["cubes"]) for b in geo["minecraft:geometry"][0]["bones"])


# ---------------------------------------------------------------- アイコン (32x32)
# 3D の箱を正面から投影して描く (色・飾りが 3D と同じになる)。窓は部位ごとに決める。
ICON_WINDOW = {"helmet": ((-5, 5), (29, 36.5)), "chestplate": ((-10, 10), (8, 25)),
               "leggings": ((-5, 5), (5, 12.5)), "boots": ((-6, 6), (-0.5, 6.5))}


def make_icon(piece, opt):
    pal = OPTIONS[opt]["pal"]
    boxes = []
    parts_map = {n: bs for n, _, bs in parts(opt)}
    for bn in PIECE_BONES[piece]:
        boxes += parts_map[bn]
    (x0, x1), (y0, y1) = ICON_WINDOW[piece]
    s = min(30 / (x1 - x0), 30 / (y1 - y0))
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 手前にある箱を最後に描く (奥から順に)
    for b in sorted(boxes, key=lambda b: -b["min"][2]):
        px0 = round(1 + (b["min"][0] - x0) * s)
        px1 = round(1 + (b["max"][0] - x0) * s)
        py0 = round(1 + (y1 - b["max"][1]) * s)
        py1 = round(1 + (y1 - b["min"][1]) * s)
        px1 = max(px1, px0 + 1)
        py1 = max(py1, py0 + 1)
        if b["mat"] == "glow":
            d.rectangle((px0, py0, px1 - 1, py1 - 1), fill=(*pal["glow"], 255))
        else:
            d.rectangle((px0, py0, px1 - 1, py1 - 1), fill=(*pal[b["mat"]], 255), outline=(*OUTLINE, 255))
    return img


def make_layer(opt, which):
    """バニラの防具の層 (64x32)。頭・胴・靴は 1、脚は 2。"""
    pal = OPTIONS[opt]["pal"]
    img = np.zeros((32, 64, 4), np.uint8)
    img[..., :3] = pal["shell"]
    img[..., 3] = 255
    yy, xx = np.mgrid[0:32, 0:64]
    dark = (np.array(pal["scute"], float) * 0.8).astype(np.uint8)
    img[(yy % 8) == 0, :3] = dark
    img[(xx % 16) == 0, :3] = dark
    img[:8, :, :3] = np.array(pal["trim"] if which == 1 else pal["accent"], np.uint8)
    return Image.fromarray(img, "RGBA")


# ---------------------------------------------------------------- 描画
def stand_model():
    names = ["head", "body", "rarm", "larm", "rleg", "lleg"]
    bones = []
    for n, (o, e) in zip(names, STAND):
        bones.append({"name": n, "pivot": [0, 0, 0],
                      "cubes": [{"origin": list(o), "size": [e[i] - o[i] for i in range(3)], "uv": [0, 0]}]})
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.stand", "texture_width": 64, "texture_height": 64},
        "bones": bones}]}
    tex = np.full((64, 64, 4), (110, 110, 124, 255), np.uint8)
    return geo, tex


def render_views(geo, tex, glowm):
    sgeo, stex = stand_model()
    faces = load_geo(sgeo, stex) + load_geo(geo, tex, glowm)
    views = [("正面", 180, 12), ("背面", 0, 12), ("3/4", 215, 22)]
    return [render(faces, yaw=y, pitch=p, size=(420, 420)) for _, y, p in views]


def main():
    ARMOR_OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    compare_cols, icon_cols, report = [], [], {}
    for opt in ["a", "b", "c"]:
        geo, tex, glowm = build_geo(opt)
        d = ARMOR_OUT / opt
        (d / "icons").mkdir(parents=True, exist_ok=True)
        (d / "layers").mkdir(parents=True, exist_ok=True)
        (d / f"{NAME}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / f"{NAME}.png")
        Image.fromarray(glowm, "RGBA").save(d / f"{NAME}_glowmask.png")
        icons = {}
        for p in PIECES:
            ic = make_icon(p, opt)
            ic.save(d / "icons" / f"thunder_wing_{p}.png")
            icons[p] = ic
        make_layer(opt, 1).save(d / "layers" / "thunder_wing_layer_1.png")
        make_layer(opt, 2).save(d / "layers" / "thunder_wing_layer_2.png")

        overlaps = check_overlaps(geo)
        clips = clearance(geo)
        glow_px = int((glowm[..., 3] > 0).sum())
        rep = {"cubes": count_cubes(geo), "texture": list(tex.shape[:2]), "glow_pixels": glow_px,
               "overlaps": overlaps, "clearance": clips}
        (d / "check.json").write_text(json.dumps(rep, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report[opt] = rep
        views = render_views(geo, tex, glowm)
        for (name, im) in zip(["正面", "背面", "3/4"], views):
            im.save(PREVIEW / f"{opt}_{name.replace('/', '')}.png")
        compare_cols.append((OPTIONS[opt]["title"], views))
        icon_cols.append((OPTIONS[opt]["title"], [icon_big(np.array(icons[p]), px=6) for p in PIECES]))
        print(f"{opt}: 箱 {rep['cubes']}, テクスチャ {tex.shape[1]}x{tex.shape[0]}, 重なり {len(overlaps)}, 食い込み {len(clips)}, 光る画素 {glow_px}")
        for line in overlaps + clips:
            print("  ", line)

    sheet_img = sheet("雷翼の羽衣 ラフ 3案 (3D)", compare_cols,
                      note="行: 正面 / 背面 / 3/4。人型の台に着せた絵。台の色は灰色")
    sheet_img.save(OUT / "compare_armor.png")
    icon_img = sheet("雷翼の羽衣 ラフ 3案 (アイコン)", icon_cols,
                     note="行: 兜 / 胸 / 脚 / 靴。3D の箱を正面から描いたアイコン")
    icon_img.save(OUT / "compare_armor_icons.png")
    print(json.dumps({k: {"overlaps": len(v["overlaps"]), "clearance": len(v["clearance"])}
                      for k, v in report.items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
