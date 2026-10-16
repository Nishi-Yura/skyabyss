"""潮殻の鎧 (tide_shell の防具 4 部位) のラフ 3 案 (A/B/C) を作る。

使い方 (リポジトリ直下で): python tools/rough/tide_armor.py
出力 (docs/review/tide_gear_roughs/):
  compare_armor.png             3 案の 3D 比較 (正面 / 背面 / 3/4、人型の台に着せた絵)
  compare_armor_icons.png       3 案のインベントリ用アイコン比較 (頭・胴・脚・靴)
  armor/<a|b|c>/tide_shell_armor.geo.json      GeckoLib の防具モデル (骨は stone_whale_armor と同じ 8 本)
  armor/<a|b|c>/tide_shell_armor.png           本体テクスチャ
  armor/<a|b|c>/tide_shell_armor_glowmask.png  青い発光部のマスク (画素が 1 つ以上ある)
  armor/<a|b|c>/icons/tide_shell_<部位>.png    32x32 のアイコン
  armor/<a|b|c>/layers/tide_shell_layer_{1,2}.png  バニラの防具の層 (64x32)
  armor/<a|b|c>/check.json                     重なり・埋まりの検査結果
プレビューは docs/review/previews/tide_armor/ (git に入れない)。

形の決まり:
- 座標は 0.5 単位の格子 (テクスチャは 1 単位 = K 画素)。回転は使わない。
- 箱どうしは重ねない (面で接するだけ)。check_overlaps で確かめる。
- 防具は人型の体から 0.5 単位離して置く。頭は覗き穴 (顔の正面) を開けておく。
- 3 案の形は同じ骨・同じ寸法。違うのは材料の色と、兜の飾り・肩の飾り・胸の模様。
- B 珊瑚の鎧は 2 周目で形を作り直した (parts_b / _icon_shapes_b)。海亀の甲羅の段、胸の大板、肩の段、珊瑚の枝。
  A / C は 1 周目のまま (形の変更なし)。
- 材料の名前 (shell / scute / trim / accent / glow) は色の表で決める。アイコンも同じ表を使う。
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
OUT = ROOT / "docs/review/tide_gear_roughs"
ARMOR_OUT = OUT / "armor"
PREVIEW = ROOT / "docs/review/previews/tide_armor"

NAME = "tide_shell_armor"
K = 4                       # 1 単位あたりの画素 (texel) 数
TOL = 1e-6
PIECES = ["helmet", "chestplate", "leggings", "boots"]
PIECE_BONES = {"helmet": ["armorHead"], "chestplate": ["armorBody", "armorRightArm", "armorLeftArm"],
               "leggings": ["armorRightLeg", "armorLeftLeg"], "boots": ["armorRightBoot", "armorLeftBoot"]}

# 光源 (左上・手前寄り、y が上)
LIGHT = np.array([-0.45, 0.72, -0.55])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
FACES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
         "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

OUTLINE = (20, 18, 28)
# 材料の名前 -> 色 (案ごとに変える)
OPTIONS = {
    "a": {
        "title": "A 甲羅の鱗",
        "sub": "teal shell + pearl trim, scute ridge",
        "pal": {"shell": (74, 118, 112), "scute": (104, 150, 140), "trim": (218, 210, 190),
                "accent": (244, 150, 92), "glow": (120, 240, 232)},
    },
    "b": {
        "title": "B 珊瑚の鎧",
        "sub": "darker teal, coral spikes on crest and pauldrons",
        "pal": {"shell": (62, 104, 100), "scute": (92, 136, 128), "trim": (218, 210, 190),
                "accent": (240, 126, 146), "glow": (120, 240, 232)},
    },
    "c": {
        "title": "C 潮門の殻",
        "sub": "pearl shell with barnacle studs, moon-silver trim",
        "pal": {"shell": (218, 210, 190), "scute": (200, 192, 170), "trim": (200, 208, 220),
                "accent": (126, 118, 102), "glow": (120, 240, 232)},
    },
}
RECOMMEND = "b"   # 推奨案 (install_tide_armor.py の既定)

# 人型の台 (バニラの体の箱。防具の位置と見比べるため)
STAND = [
    ((-4, 24, -4), (4, 32, 4)),    # 頭
    ((-4, 12, -2), (4, 24, 2)),    # 胴
    ((-8, 10, -2), (-4, 22, 2)),   # 右腕
    ((4, 10, -2), (8, 22, 2)),     # 左腕
    ((-4, 0, -2), (0, 12, 2)),     # 右脚
    ((0, 0, -2), (4, 12, 2)),      # 左脚
]


# ---------------------------------------------------------------- 形
def bx(x0, y0, z0, x1, y1, z1, mat):
    return {"min": (x0, y0, z0), "max": (x1, y1, z1), "mat": mat}


def mirror(b):
    (x0, y0, z0), (x1, y1, z1) = b["min"], b["max"]
    return {"min": (-x1, y0, z0), "max": (-x0, y1, z1), "mat": b["mat"]}


def _ov(a, b):
    ov = np.minimum(a["max"], b["max"]) - np.maximum(a["min"], b["min"])
    return bool((ov > TOL).all())


def relief(boxes, mat, face, plane, x0, x1, y0, y1, cell=1.5, size=1.0, depth=0.5):
    """板の面 (face: "z-" は手前、"z+" は奥) に、鱗の盛り上がりを千鳥に並べる。
    既存の箱と体積で重なる所は置かない (面で接するだけなら置く)。盛り上がりは 0.5 単位。"""
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
    """面 (axis 軸に垂直、plane の位置、sign は外向き +1 / -1) に、盛り上がった鱗を千鳥に並べる。
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


def parts_b():
    """B 珊瑚の鎧 (2 周目の直し): 海亀の甲羅の形。
    兜は段々の甲羅 (真珠色の縁、頂に珊瑚の枝、額の庇と頬当て、顔の正面は目の高さまで開ける)。
    胴は背の 3 段の甲羅と、胸の大きな板 4 枚 (中央は珊瑚の竜骨と青い核)。
    肩は 3 段の板 (段を 0.5 ずつずらして重ねる) と珊瑚の枝。脛は鱗の板、靴は厚い底とつま先の覆い。
    箱どうしは重ねない (面で接するだけ)。右側 (x<0) を作って、左は反転して作る。中央の箱は 1 つだけ。"""
    # 頭 (顔は z<0 の正面)
    head = [
        bx(-4.5, 32, -4.5, 4.5, 32.5, 4.5, "trim"),     # 兜の縁 (真珠色)
        bx(-4, 32.5, -4, 4, 33.5, 4, "shell"),          # 甲羅の段 1
        bx(-3, 33.5, -3, 3, 34.5, 3, "shell"),          # 段 2
        bx(-2, 34.5, -2, 2, 35, 2, "scute"),            # 段 3 (頂)
        bx(-4.5, 29.5, -5, 4.5, 32, -4, "shell"),       # 額の庇 (目の上だけ)
        bx(-0.5, 30.5, -5.5, 0.5, 31.5, -5, "glow"),    # 額の青い光
        bx(-4, 25, 4, 4, 32, 4.5, "shell"),             # うなじの覆い
    ]
    head += [
        bx(-0.5, 35, -0.5, 0.5, 36, 0.5, "accent"),     # 珊瑚の幹
        bx(-0.5, 36, -0.5, 0.5, 37, 0.5, "accent"),     # 幹の先
        bx(-1, 35.5, -0.5, -0.5, 36.5, 0.5, "accent"),  # 左の枝
        bx(0.5, 35.5, -0.5, 1, 36.5, 0.5, "accent"),    # 右の枝
    ]
    cheek = [
        bx(-5, 25, -3.5, -4, 30, 2.5, "shell"),         # 頬当て
        bx(-5.5, 25.5, -2.5, -5, 28, 1.5, "scute"),     # 頬当ての段
    ]
    head += cheek + [mirror(b) for b in cheek]
    head += tiles(head, "scute", "z", -1, -4, (-4, 32.5, 0), (4, 33.5, 0))           # 段 1 の前の鱗
    head += tiles(head, "scute", "x", -1, -4, (-4, 32.5, -3), (-4, 33.5, 3))         # 段 1 の左の鱗
    head += tiles(head, "scute", "x", 1, 4, (4, 32.5, -3), (4, 33.5, 3))             # 段 1 の右の鱗

    # 胴 (前の板は体の前から 0.5 離す。背は 3 段で膨らむ)
    body = [
        bx(-4, 12, 2.5, 4, 24, 3, "shell"),             # 背の甲羅 1 段
        bx(-3.5, 12.5, 3, 3.5, 23.5, 3.5, "shell"),     # 2 段
        bx(-2.5, 13.5, 3.5, 2.5, 22.5, 4, "scute"),     # 頂の段
        bx(-3.5, 19.5, -3.5, 3.5, 23.5, -2.5, "shell"), # 胸の上の板
        bx(-3, 13, -3.5, 3, 19.5, -2.5, "shell"),       # 腹の板
        bx(-3.5, 23.5, -3.5, 3.5, 24, -2.5, "trim"),    # 胸の縁 (上)
        bx(-3.5, 12.5, -3.5, 3.5, 13, -2.5, "trim"),    # 腹の縁 (下)
        bx(-3, 20, -4, -0.5, 23, -3.5, "scute"),        # 胸の大板 (左上)
        bx(0.5, 20, -4, 3, 23, -3.5, "scute"),          # 胸の大板 (右上)
        bx(-3, 14, -4, -0.5, 19, -3.5, "scute"),        # 腹の大板 (左下)
        bx(0.5, 14, -4, 3, 19, -3.5, "scute"),          # 腹の大板 (右下)
        bx(-0.5, 20, -4, 0.5, 23, -3.5, "accent"),      # 中央の珊瑚の竜骨 (上)
        bx(-0.5, 16.5, -4, 0.5, 20, -3.5, "glow"),      # 中央の青い核
        bx(-0.5, 14, -4, 0.5, 16.5, -3.5, "accent"),    # 中央の珊瑚の竜骨 (下)
    ]
    tuft = [  # 背の珊瑚の枝 (右。左は反転)
        bx(-3, 18, 3.5, -2.5, 19.5, 4, "accent"),       # 幹
        bx(-3.5, 18.5, 3.5, -3, 19.5, 4, "accent"),     # 枝
        bx(-3, 19.5, 3.5, -2.5, 20, 4, "accent"),       # 先
    ]
    body += tuft + [mirror(b) for b in tuft]

    # 腕 (右)。袖の 3 面 (外・前・後)、手首の輪、肩の 3 段、珊瑚の枝
    arm = [
        bx(-9, 10, -2.5, -8.5, 22.5, 2.5, "shell"),     # 袖 (外側)
        bx(-8.5, 10, -3, -4.5, 22.5, -2.5, "shell"),    # 袖 (前)
        bx(-8.5, 10, 2.5, -4.5, 22.5, 3, "shell"),      # 袖 (後ろ)
        bx(-9, 9.5, -3, -5, 10, 3, "trim"),             # 手首の輪 (脚の外側の板に触れない幅)
        bx(-9.5, 22.5, -3.5, -9, 23, 3.5, "trim"),      # 肩の縁 (外側)
        bx(-9, 22.5, -3.5, -4.5, 23, 3.5, "shell"),     # 肩の段 1
        bx(-9, 23, -2.5, -5, 23.5, 2.5, "shell"),       # 肩の段 2
        bx(-8.5, 23.5, -2, -5.5, 24, 2, "scute"),       # 肩の段 3 (頂)
        bx(-9.5, 23, -0.5, -9, 24, 0.5, "accent"),      # 肩の珊瑚 (幹)
        bx(-10, 23.5, -0.5, -9.5, 24.5, 0.5, "accent"), # 枝
        bx(-9.5, 24, -0.5, -9, 25, 0.5, "accent"),      # 先
    ]
    arm += tiles(arm, "scute", "z", -1, -3, (-8.5, 12, 0), (-4.5, 20.5, 0))          # 袖 (前) の鱗

    # 脚 (右)。太もも (y 6..12) の板と膝の盛り上がり、外側の青い印
    leg = [
        bx(-5, 6, -2.5, -4.5, 10, 2.5, "shell"),        # 外側の板 (腕の下まで。腕の体積には入れない)
        bx(-4.5, 6, -3, -0.5, 11, -2.5, "shell"),       # 前の板 (下)
        bx(-4.5, 11, -3, -0.5, 12, -2.5, "trim"),       # 前の縁 (上)
        bx(-4.5, 6, 2.5, -0.5, 11, 3, "shell"),         # 後ろの板 (下)
        bx(-4.5, 11, 2.5, -0.5, 12, 3, "trim"),         # 後ろの縁 (上)
        bx(-3.5, 6.5, -3.5, -1.5, 8.5, -3, "shell"),    # 膝の盛り上がり 1
        bx(-3, 7, -4, -2, 8, -3.5, "scute"),            # 膝の盛り上がり 2
        bx(-5.5, 8, -1, -5, 9, 1, "glow"),              # 外側の青い印
    ]
    leg += tiles(leg, "scute", "z", -1, -3, (-4.5, 6, 0), (-0.5, 11, 0))             # 前の鱗
    leg += tiles(leg, "scute", "z", 1, 3, (-4.5, 6, 0), (-0.5, 11, 0))               # 後ろの鱗

    # 靴 (右)。厚い底 (外側に 1 段出す)、つま先の覆い、かかとの段、足首の縁
    boot = [
        bx(-5.5, 0, -3, -5, 1, 3, "scute"),             # 厚い底 (外側)
        bx(-5, 0, -2.5, -4.5, 5, 2.5, "shell"),         # 外側の板
        bx(-4.5, 0, -3, -0.5, 5, -2.5, "shell"),        # 前の板
        bx(-4.5, 0, -4, -0.5, 1.5, -3, "scute"),        # つま先の覆い
        bx(-4.5, 0, 2.5, -0.5, 5, 3, "shell"),          # 後ろの板
        bx(-4.5, 0, 3, -0.5, 2, 3.5, "scute"),          # かかとの段
        bx(-5, 5, -2.5, -4.5, 6, 2.5, "trim"),          # 足首の縁 (外側)
        bx(-4.5, 5, -3, -0.5, 6, -2.5, "trim"),         # 足首の縁 (前)
        bx(-4.5, 5, 2.5, -0.5, 6, 3, "trim"),           # 足首の縁 (後ろ)
    ]
    boot += tiles(boot, "scute", "x", -1, -5, (-5, 0, -2.5), (-5, 5, 2.5))           # 外側の鱗

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
    """骨の一覧 [(名前, pivot, 箱のリスト)]。右側 (x<0) を作って、左側は x を反転して作る。
    中央の箱 (mid) は 1 つだけ、左右の箱 (side) は右と左の 2 つ作る。"""
    if opt == "b":
        return parts_b()
    # 頭
    head_mid = [
        bx(-4, 24, 4, 4, 32.5, 5, "shell"),           # 後ろの壁
        bx(-4, 30, -5, 4, 32.5, -4, "shell"),         # 額の庇 (覗き穴の上)
        bx(-4, 24, -5, 4, 25, -4, "shell"),           # あご (覗き穴の下)
        bx(-5, 32.5, -5, 5, 33.5, 5, "shell"),        # 兜の天蓋
        bx(-3.5, 33.5, -3.5, 3.5, 34.5, 3.5, "scute"),  # 天蓋の段
        bx(-1.5, 30.5, -5.5, 1.5, 31.5, -5, "glow"),  # 額の青い光
    ]
    head_side = [
        bx(4, 24, -5, 5, 32.5, 5, "shell"),           # 側壁 (右は反転)
        bx(5, 25, -3, 5.5, 31, 3, "scute"),           # 頬当て
    ]
    crest = {
        "a": [bx(-0.5, 34.5, -3, 0.5, 35.5, 3, "scute")],
        "b": [bx(-0.5, 34.5, -0.5, 0.5, 36.5, 0.5, "accent"),
              bx(0.5, 35.5, -0.5, 1.5, 36.5, 0.5, "accent"),
              bx(-1.5, 35.5, -0.5, -0.5, 36.5, 0.5, "accent")],
        "c": [bx(-0.5, 34.5, -3, 0.5, 35.5, 3, "trim")],
    }[opt]
    head = head_mid + crest + head_side + [mirror(b) for b in head_side]

    # 胴 (前の板は体の前から 0.5 離す)
    body_mid = [
        bx(-4, 12, 2.5, 4, 24, 3.5, "shell"),          # 背の板
        bx(-4, 12, -3.5, 4, 24, -2.5, "shell"),        # 胸の板
        bx(-4, 20.5, -4, 4, 21, -3.5, "scute"),        # 胸の段 (上)
        bx(-4, 14.5, -4, 4, 15, -3.5, "scute"),        # 胸の段 (下)
        bx(-1, 15.5, -4.5, 1, 20.5, -3.5, "glow"),     # 中央の青い核
    ]
    stud_mat = {"a": "trim", "b": "trim", "c": "accent"}[opt]
    studs = [bx(-3.5, 13, -4, -3, 13.5, -3.5, stud_mat), bx(-3.5, 22.5, -4, -3, 23, -3.5, stud_mat)]
    if opt == "c":   # 潮門の殻: 藤壺のびょうを増やす
        studs += [bx(-3.5, 17.5, -4, -3, 18, -3.5, "accent"), bx(-3.5, 9.5, -4, -3, 10, -3.5, "accent")]
    bump = {"a": "scute", "b": "accent", "c": "accent"}[opt]
    body = body_mid + studs + [mirror(b) for b in studs]   # びょうは右と左の 2 つずつ
    body += relief(body, bump, "z-", -3.5, -3.5, 3.5, 12.5, 23.0)    # 胸の鱗の盛り上がり
    body += relief(body, bump, "z+", 3.5, -3.5, 3.5, 12.5, 23.0)     # 背の鱗

    # 腕 (右)。肩当て・袖・前後の板・手首の輪
    arm = [
        bx(-9, 22, -3, -4, 23.5, 3, "shell"),          # 肩当て
        bx(-9.5, 23.5, -3.5, -4, 24, 3.5, "trim"),     # 肩の縁
        bx(-8.5, 10.5, -2.5, -8, 21.5, 2.5, "shell"),  # 袖 (外側)
        bx(-8, 10.5, -2.5, -4, 21.5, -2, "shell"),     # 前の板
        bx(-8, 10.5, 2, -4, 21.5, 2.5, "shell"),       # 後ろの板
        bx(-8.5, 9.5, -2.5, -4.5, 10, 2.5, "trim"),    # 手首の輪 (腕の下に接する)
    ]
    if opt == "a":
        arm.append(bx(-8.5, 24, -1, -5.5, 24.5, 1, "scute"))   # 肩の鱗の段
    if opt == "b":
        arm += [bx(-8.5, 24, -1, -7.5, 25.5, -0.5, "accent"),
                bx(-8.5, 24, 0.5, -7.5, 25.5, 1, "accent"),
                bx(-6.5, 24, -1, -5.5, 25.5, -0.5, "accent")]
    if opt == "c":
        arm.append(bx(-9.5, 24, -3.5, -5, 24.5, 3.5, "accent"))   # 藤壺の段
    arm += relief(arm, bump, "z-", -2.5, -7.5, -4.5, 11.0, 20.5)  # 籠手の鱗

    # 脚 (右)。太もも (y 6..12) と靴 (y 0..6) は面で接するだけ
    leg = [
        bx(-4, 6, -3, 0, 12, -2.5, "shell"),           # 前の板
        bx(-4.5, 6, -2.5, -4, 10, 2.5, "shell"),       # 外側の板 (腕の下まで)
        bx(-4, 6, 2.5, 0, 12, 3, "shell"),             # 後ろの板
        bx(-2, 8, -3.5, -1, 9, -3, "glow"),            # 腿の青い印
    ]
    head += relief(head, bump, "z-", -5.0, -3.5, 3.5, 30.5, 32.0)  # 額の鱗
    boot = [
        bx(-4, 0, -3, 0, 6, -2.5, "shell"),            # 靴の前
        bx(-4.5, 0, -2.5, -4, 6, 2.5, "shell"),        # 靴の外側
        bx(-4, 0, 2.5, 0, 6, 3, "shell"),              # 靴の後ろ
        bx(-4, 0, -4, 0, 2, -3, "scute"),              # つま先
        bx(-5, 4, -2, -4.5, 4.5, 2, "trim"),           # 外くるぶしの縁
    ]
    leg += relief(leg, bump, "z-", -3.0, -3.5, -0.5, 6.5, 11.0)   # 腿の鱗
    boot += relief(boot, bump, "z-", -3.0, -3.5, -0.5, 0.5, 5.0)  # 靴の鱗
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
    if mat in ("shell", "scute"):
        seam = ((xx % 8) == 0) | ((yy % 8) == 0)   # 鱗の継ぎ目
        img[seam] *= 0.72
    if mat == "accent":
        img[rng.random((h, w)) < 0.12] *= 1.3          # 珊瑚・藤壺のつぶ
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
                "identifier": "geometry.tide_shell_armor",
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
ICON_LABEL = {"shell": 1, "scute": 2, "trim": 3, "accent": 4, "glow": 5, "dark": 6}


def _icon_shapes_b(piece):
    """B 珊瑚の鎧のアイコン (2 周目)。3D の段・板・枝と同じ並びにする。"""
    if piece == "helmet":
        return [
            ("shell", ("ell", (5, 6, 27, 27))),             # 甲羅の丸み
            ("trim", ("rect", (4, 21, 28, 23))),            # 縁 (真珠色)
            ("scute", ("rect", (9, 12, 13, 14))),           # 段の鱗
            ("scute", ("rect", (14, 9, 18, 11))),
            ("scute", ("rect", (19, 12, 23, 14))),
            ("scute", ("rect", (7, 14, 25, 16))),           # 額の庇
            ("dark", ("rect", (10, 18, 14, 20))),           # 目の穴 (顔は開けてある)
            ("dark", ("rect", (18, 18, 22, 20))),
            ("glow", ("rect", (15, 14, 17, 16))),           # 額の青い光
            ("accent", ("rect", (14, 0, 18, 6))),           # 頂の珊瑚の幹
            ("accent", ("rect", (10, 2, 14, 4))),           # 枝
            ("accent", ("rect", (18, 2, 22, 4))),
        ]
    if piece == "chestplate":
        return [
            ("shell", ("poly", [(9, 8), (23, 8), (25, 12), (23, 27), (9, 27), (7, 12)])),
            ("scute", ("rect", (10, 11, 15, 19))),          # 胸の大板
            ("scute", ("rect", (17, 11, 22, 19))),
            ("scute", ("rect", (10, 20, 15, 25))),          # 腹の大板
            ("scute", ("rect", (17, 20, 22, 25))),
            ("accent", ("rect", (15, 11, 17, 14))),         # 中央の珊瑚の竜骨
            ("glow", ("rect", (15, 14, 17, 20))),           # 中央の青い核
            ("accent", ("rect", (15, 20, 17, 25))),
            ("trim", ("rect", (9, 26, 23, 27))),            # 縁
            ("shell", ("ell", (0, 4, 9, 13))),              # 左の肩
            ("scute", ("ell", (1, 3, 8, 9))),
            ("accent", ("rect", (2, 0, 4, 4))),             # 肩の珊瑚
            ("accent", ("rect", (0, 2, 2, 4))),
            ("shell", ("ell", (23, 4, 32, 13))),            # 右の肩
            ("scute", ("ell", (24, 3, 31, 9))),
            ("accent", ("rect", (28, 0, 30, 4))),
            ("accent", ("rect", (30, 2, 32, 4))),
        ]
    if piece == "leggings":
        return [
            ("trim", ("rect", (8, 3, 24, 7))),              # 腰の縁
            ("shell", ("rect", (8, 7, 15, 28))),
            ("shell", ("rect", (17, 7, 24, 28))),
            ("scute", ("ell", (9, 12, 15, 17))),            # 膝の盛り上がり
            ("scute", ("ell", (17, 12, 23, 17))),
            ("scute", ("rect", (9, 20, 14, 23))),           # すねの板
            ("scute", ("rect", (18, 20, 23, 23))),
            ("trim", ("rect", (8, 26, 24, 28))),            # 裾
            ("glow", ("rect", (14, 4, 18, 6))),             # 腰の青い印
        ]
    return [  # boots
        ("shell", ("rect", (4, 5, 13, 22))),
        ("shell", ("rect", (18, 5, 27, 22))),
        ("trim", ("rect", (4, 5, 13, 8))),                  # 足首の縁
        ("trim", ("rect", (18, 5, 27, 8))),
        ("scute", ("rect", (5, 13, 8, 17))),                # 脛の鱗
        ("scute", ("rect", (19, 13, 22, 17))),
        ("scute", ("rect", (3, 22, 14, 27))),               # 厚い底
        ("scute", ("rect", (17, 22, 28, 27))),
        ("trim", ("rect", (3, 22, 14, 23))),                # つま先の縁
        ("trim", ("rect", (17, 22, 28, 23))),
        ("glow", ("rect", (7, 10, 9, 11))),
        ("glow", ("rect", (21, 10, 23, 11))),
    ]


def icon_shapes(piece, opt):
    """(ラベル名, 描く形) の列。後から描いたものが上に乗る。"""
    if opt == "b":
        return _icon_shapes_b(piece)
    if piece == "helmet":
        crest = {"a": "scute", "b": "accent", "c": "trim"}[opt]
        return [
            ("shell", ("ell", (5, 4, 27, 24))),
            ("shell", ("rect", (5, 14, 27, 22))),
            ("trim", ("rect", (5, 20, 27, 22))),
            ("scute", ("rect", (8, 6, 24, 8))),
            (crest, ("rect", (14, 0, 18, 4))),
            ("dark", ("rect", (10, 14, 22, 18))),
            ("glow", ("rect", (15, 15, 17, 17))),
        ]
    if piece == "chestplate":
        spike = "accent" if opt == "b" else "scute"
        return [
            ("shell", ("poly", [(9, 7), (23, 7), (25, 12), (23, 27), (9, 27), (7, 12)])),
            ("scute", ("ell", (1, 4, 10, 12))),
            ("scute", ("ell", (22, 4, 31, 12))),
            (spike, ("rect", (2, 1, 4, 3))),
            (spike, ("rect", (27, 1, 29, 3))),
            ("scute", ("rect", (9, 12, 23, 13))),
            ("scute", ("rect", (9, 20, 23, 21))),
            ("glow", ("rect", (14, 14, 18, 19))),
            ("trim", ("rect", (9, 25, 23, 27))),
        ]
    if piece == "leggings":
        return [
            ("trim", ("rect", (8, 3, 24, 7))),
            ("shell", ("rect", (8, 7, 15, 28))),
            ("shell", ("rect", (17, 7, 24, 28))),
            ("scute", ("rect", (9, 14, 14, 19))),
            ("scute", ("rect", (18, 14, 23, 19))),
            ("glow", ("rect", (14, 4, 18, 6))),
        ]
    return [  # boots
        ("shell", ("rect", (4, 5, 13, 20))),
        ("shell", ("rect", (3, 20, 14, 26))),
        ("shell", ("rect", (18, 5, 27, 20))),
        ("shell", ("rect", (17, 20, 28, 26))),
        ("trim", ("rect", (4, 5, 13, 7))),
        ("trim", ("rect", (18, 5, 27, 7))),
        ("scute", ("rect", (3, 24, 14, 26))),
        ("scute", ("rect", (17, 24, 28, 26))),
        ("glow", ("rect", (7, 12, 9, 13))),
        ("glow", ("rect", (21, 12, 23, 13))),
    ]


def make_icon(piece, opt):
    pal = OPTIONS[opt]["pal"]
    lab = np.zeros((32, 32), int)
    for name, (kind, arg) in icon_shapes(piece, opt):
        m = Image.new("L", (32, 32), 0)
        d = ImageDraw.Draw(m)
        if kind == "ell":
            d.ellipse(arg, fill=255)
        elif kind == "rect":
            d.rectangle((arg[0], arg[1], arg[2] - 1, arg[3] - 1), fill=255)
        else:
            d.polygon(arg, fill=255)
        lab[np.array(m) > 0] = ICON_LABEL[name]
    names = {v: k for k, v in ICON_LABEL.items()}
    out = np.zeros((32, 32, 4), np.uint8)
    solid = lab > 0
    empty_up = np.pad(~solid, ((1, 0), (0, 0)), constant_values=True)[:-1]
    empty_left = np.pad(~solid, ((0, 0), (1, 0)), constant_values=True)[:, :-1]
    empty_down = np.pad(~solid, ((0, 1), (0, 0)), constant_values=True)[1:]
    empty_right = np.pad(~solid, ((0, 0), (0, 1)), constant_values=True)[:, 1:]
    yy, xx = np.mgrid[0:32, 0:32]
    for y in range(32):
        for x in range(32):
            if not solid[y, x]:
                continue
            key = names[lab[y, x]]
            if key == "dark":
                out[y, x] = (*OUTLINE, 255)
                continue
            if key == "glow":
                out[y, x] = (*pal["glow"], 255)
                continue
            if empty_up[y, x] or empty_left[y, x]:
                k = 1.14
            elif empty_down[y, x] or empty_right[y, x]:
                k = 0.7
            else:
                k = 0.92
            if key in ("shell", "scute") and y % 4 == 0:
                k *= 0.8                       # 鱗の継ぎ目
            if key == "accent" and (x + y) % 3 == 0:
                k *= 1.12
            c = np.array(pal[key], float) * k
            out[y, x] = (*np.clip(c, 0, 255).astype(np.uint8), 255)
    # 外の輪郭線
    outline = solid & (empty_up | empty_left | empty_down | empty_right)
    out[outline & (lab != ICON_LABEL["glow"])] = (*OUTLINE, 255)
    return Image.fromarray(out, "RGBA")


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
        geo_path = d / f"{NAME}.geo.json"
        geo_path.write_text(json.dumps(geo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / f"{NAME}.png")
        Image.fromarray(glowm, "RGBA").save(d / f"{NAME}_glowmask.png")
        icons = {}
        for p in PIECES:
            ic = make_icon(p, opt)
            ic.save(d / "icons" / f"tide_shell_{p}.png")
            icons[p] = ic
        make_layer(opt, 1).save(d / "layers" / "tide_shell_layer_1.png")
        make_layer(opt, 2).save(d / "layers" / "tide_shell_layer_2.png")

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

    sheet_img = sheet("潮殻の鎧 ラフ 3案 (3D)", compare_cols,
                      note="行: 正面 / 背面 / 3/4。人型の台に着せた絵。台の色は灰色")
    sheet_img.save(OUT / "compare_armor.png")
    icon_img = sheet("潮殻の鎧 ラフ 3案 (アイコン)", icon_cols,
                     note="行: 兜 / 胸 / 脚 / 靴。3D と同じ色・同じ飾り")
    icon_img.save(OUT / "compare_armor_icons.png")
    print(json.dumps({k: {"overlaps": len(v["overlaps"]), "clearance": len(v["clearance"])}
                      for k, v in report.items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
