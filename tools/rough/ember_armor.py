"""余燼の鎧 (余燼の巨神の防具 4 部位) のラフ 3 案 (A/B/C) を作る。

使い方 (リポジトリ直下で): PYTHONIOENCODING=utf-8 python -B tools/rough/ember_armor.py
出力 (docs/review/ember_gear_roughs/):
  compare_armor.png             3 案の 3D 比較 (正面 / 背面 / 3/4、人型の台に着せた絵)
  compare_armor_icons.png       3 案のインベントリ用アイコン比較 (頭・胴・脚・靴)
  armor/<a|b|c>/ember_armor.geo.json           GeckoLib の防具モデル (骨は stone_whale_armor と同じ 8 本)
  armor/<a|b|c>/ember_armor.png                本体テクスチャ
  armor/<a|b|c>/ember_armor_glowmask.png       光る部分 (溶岩の筋・火口) のマスク (画素が 1 つ以上ある)
  armor/<a|b|c>/icons/ember_<部位>.png         32x32 のアイコン
  armor/<a|b|c>/layers/ember_layer_{1,2}.png   バニラの防具の層 (64x32)
  armor/<a|b|c>/check.json                     重なり・埋まりの検査結果
プレビューは docs/review/previews/ember_armor/ (git に入れない)。

形の決まり:
- 座標は 0.5 単位の格子 (テクスチャは 1 単位 = K 画素)。回転は使わない。
- 箱どうしは重ねない (面で接するだけ)。check_overlaps で確かめる。
- 防具は人型の体から 0.5 単位離して置く。頭は覗き穴 (顔の正面) を開けておく。
- 3 案の骨・寸法は同じ。違うのは飾り (兜の火山の頂、肩の噴気孔、胸の溶岩の筋) と材料の濃さ。
- 材料は玄武岩 (plate)・灰の凝灰岩 (ash)・鱗の盛り上がり (scute)・暗い穴 (hole)・
  溶岩の筋 (seam、光る)・溶けた黄 (molten、光る)。青と白は使わない。
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
OUT = ROOT / "docs/review/ember_gear_roughs"
ARMOR_OUT = OUT / "armor"
PREVIEW = ROOT / "docs/review/previews/ember_armor"

NAME = "ember_armor"
K = 4                       # 1 単位あたりの画素 (texel) 数
TOL = 1e-6
PIECES = ["helmet", "chestplate", "leggings", "boots"]
GLOW = ("seam", "molten")
ICON_LABEL = {"plate": 1, "scute": 2, "ash": 3, "hole": 4, "seam": 5, "molten": 6}

# 光源 (左上・手前寄り、y が上)
LIGHT = np.array([-0.45, 0.72, -0.55])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
FACES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
         "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

OUTLINE = (18, 14, 14)
OPTIONS = {
    "a": {
        "title": "A 火山の背",
        "sub": "basalt plates, volcano crest with molten tip, vent rings on the shoulders",
        "pal": {"plate": (46, 44, 50), "scute": (70, 66, 70), "ash": (112, 96, 84),
                "hole": (16, 14, 16), "seam": (255, 112, 36), "molten": (255, 206, 86)},
    },
    "b": {
        "title": "B 溶岩の縞",
        "sub": "darker basalt, horizontal lava bands on the chest, ridge with ember nubs",
        "pal": {"plate": (38, 36, 42), "scute": (60, 56, 60), "ash": (120, 114, 108),
                "hole": (16, 14, 16), "seam": (255, 130, 40), "molten": (255, 206, 86)},
    },
    "c": {
        "title": "C 割れた殻",
        "sub": "warm grey ash plates split by cracks, smoke holes, no crest glow",
        "pal": {"plate": (58, 54, 56), "scute": (84, 78, 76), "ash": (150, 140, 128),
                "hole": (16, 14, 16), "seam": (255, 104, 32), "molten": (255, 206, 86)},
    },
}
RECOMMEND = "a"   # 推奨案 (install_ember_armor.py の既定)

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
    """板の面 (face: "z-" は手前、"z+" は奥) に、盛り上がりを千鳥に並べる。既存の箱と重なる所は置かない。"""
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
    """面 (axis に垂直、plane の位置、sign は外向き +1 / -1) に、盛り上がりを千鳥に並べる。"""
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


def chest_a_boxes():
    """A 火山の背の胸 (前の面、z -4..-3.5)。前の面を 0.5 単位の枡に分け、材料ごとに長方形にまとめる。
    溶岩の筋は中央の幹 (2 枡幅) から左右に枝分かれし、板の間の割れ目を通る。幹の先は溶けた黄。
    整数は 0.5 単位の枡の番号 (X は x の 2 倍、Y は y の 2 倍)。枡は重ならないので箱も重ならない。"""
    seam = {(-1, y) for y in range(25, 46)} | {(0, y) for y in range(25, 46)}     # 幹 (y 12.5..23)
    seam |= {(-2, 34), (-3, 34), (-4, 34), (-5, 34), (-5, 35), (-6, 35), (-6, 36)}  # 左の枝
    seam |= {(1, 40), (2, 40), (3, 40), (3, 41), (4, 41), (5, 41), (5, 42), (6, 42)}  # 右の枝
    seam |= {(-4, 27), (5, 30)}                                                     # 小さな割れ目
    molten = {(-1, 46), (0, 46)}                                                    # 幹の先 (y 23..23.5)

    def mat_of(X, Y):
        if (X, Y) in molten:
            return "molten"
        if (X, Y) in seam:
            return "seam"
        if Y <= 29:
            return "ash"        # y 12..15 (灰褐色の凝灰岩)
        if Y <= 39:
            return "plate"      # y 15..20
        return "scute"          # y 20..23.5

    cells = {(X, Y): mat_of(X, Y) for X in range(-7, 7) for Y in range(24, 47)}
    done = set()
    out = []
    for Y in range(24, 47):
        for X in range(-7, 7):
            if (X, Y) in done:
                continue
            m = cells[(X, Y)]
            w = 1
            while X + w < 7 and cells[(X + w, Y)] == m and (X + w, Y) not in done:
                w += 1
            h = 1
            while Y + h < 47 and all(cells[(X + i, Y + h)] == m and (X + i, Y + h) not in done for i in range(w)):
                h += 1
            for i in range(w):
                for j in range(h):
                    done.add((X + i, Y + j))
            out.append(bx(X / 2, Y / 2, -4, (X + w) / 2, (Y + h) / 2, -3.5, m))
    return out


def parts(opt):
    """骨の一覧 [(名前, pivot, 箱のリスト)]。右側 (x<0) を作って、左側は x を反転して作る。
    段の付け方 (階段) は 3 案で同じ。飾りだけ案ごとに変える。"""
    # ---- 頭: 顔の正面 (z<0) は目の高さまで開ける。頂に火山 (A)、割れた殻 (C)、平らな縞 (B)
    head = [
        bx(-4, 24, 4, 4, 32.5, 5, "plate"),            # 後ろの壁
        bx(-4, 30, -5, 4, 32.5, -4, "plate"),          # 額の庇 (覗き穴の上)
        bx(-4, 24, -5, 4, 25, -4, "plate"),            # あご (覗き穴の下)
        bx(-5, 32.5, -5, 5, 33.5, 5, "plate"),         # 兜の天蓋
        bx(-3.5, 33.5, -3.5, 3.5, 34.5, 3.5, "scute"), # 天蓋の段
        bx(-0.5, 30.5, -5.5, 0.5, 31.5, -5, "seam") if opt == "b" else bx(-0.5, 30.5, -5.5, 0.5, 31.5, -5, "plate"),
    ]
    head += relief(head, "scute", "z-", -5.0, -3.5, 3.5, 30.5, 32.0)   # 額の鱗
    if opt == "a":   # 火山: 錐の台の上に火口の縁 (玄武岩の輪)。溶岩は縁より低く、輪の中に沈める
        head += [bx(-2, 34.5, -2, 2, 35.5, 2, "plate"),
                 bx(-1.5, 35.5, 1, 1.5, 36.5, 1.5, "plate"),   # 火口の縁 (前)
                 bx(-1.5, 35.5, -1.5, 1.5, 36.5, -1, "plate"),  # 縁 (後)
                 bx(-1.5, 35.5, -1, -1, 36.5, 1, "plate"),      # 縁 (左)
                 bx(1, 35.5, -1, 1.5, 36.5, 1, "plate"),        # 縁 (右)
                 bx(-1, 35.5, -1, 1, 36, 1, "molten")]          # 溶岩 (縁より 0.5 低い)
        head += [bx(-5, 32, -5.5, 5, 32.5, -5, "scute")]         # 天蓋の前の面取り (段)
    elif opt == "b":  # 溶岩の縞: 平らな背びれ
        head += [bx(-1.5, 34.5, -3, 1.5, 35.5, 3, "scute")]
    else:            # 割れた殻: 頂が 2 つに割れている
        head += [bx(-2, 34.5, -1.5, 0, 35.5, 1.5, "ash"),
                 bx(0.5, 34.5, -1, 2, 36, 1, "plate")]
    side = [
        bx(4, 24, -5, 5, 32.5, 5, "plate"),            # 側壁 (右は反転)
        bx(5, 25, -3, 5.5, 31, 3, "ash"),              # 頬当て (灰の凝灰岩)
    ]
    if opt == "a":   # 頬の噴気の口
        side.append(bx(5.5, 28, -0.5, 6, 29, 0.5, "seam"))
    if opt == "b":   # 頬の溶岩の点
        side.append(bx(5.5, 26, -1, 6, 27, 1, "seam"))
    head += side + [mirror(b) for b in side]

    # ---- 胴: 前の板は 3 段の階段 (中央は溶岩の筋・縞・割れ目)、背は 3 段の棘
    body = [
        bx(-4, 12, 2.5, 4, 24, 3.5, "plate"),          # 背の板
        bx(-4, 12, -3.5, 4, 23.5, -2.5, "plate"),      # 胸の板 (上の縁の手前まで)
        bx(-3.5, 23.5, -3.5, 3.5, 24, -2.5, "ash"),    # 胸の縁 (上、灰)
    ]
    tiers = [(20, 23.5, "scute"), (15, 19.5, "plate"), (12.5, 14.5, "ash")]
    if opt != "a":
        for y0, y1, m in tiers:   # 段の左右の箱 (中央は別に作る)
            body += [bx(-3.5, y0, -4, -0.5, y1, -3.5, m), bx(0.5, y0, -4, 3.5, y1, -3.5, m)]
    if opt == "a":    # 玄武岩の胸を 0.5 単位の枡に分け、溶岩の筋は幹から枝分かれさせる
        body += chest_a_boxes()
    elif opt == "b":  # 段の間の溶岩の縞 (全幅)、中央は板のまま
        body += [bx(-0.5, 20, -4, 0.5, 23.5, -3.5, "plate"),
                 bx(-0.5, 15, -4, 0.5, 19.5, -3.5, "plate"),
                 bx(-0.5, 12.5, -4, 0.5, 14.5, -3.5, "plate"),
                 bx(-3.5, 19.5, -4, 3.5, 20, -3.5, "seam")]
    else:             # 割れた殻: 中央の割れ目は暗い穴 (煙の孔)
        body += [bx(-0.5, 15, -4, 0.5, 19.5, -3.5, "hole"),
                 bx(-0.5, 12.5, -4, 0.5, 14.5, -3.5, "scute")]
    # 背の棘 (背骨): 上ほど段を重ねる。先の溶岩の点は A と B
    spine = [
        bx(-1.5, 13, 3.5, 1.5, 16, 5.0, "plate"),      # 背骨 1 (下、幅広)
        bx(-1, 16, 3.5, 1, 19, 5.5, "scute"),          # 背骨 2 (頂、いちばん高い)
        bx(-1.5, 19, 3.5, 1.5, 22, 5.0, "plate"),      # 背骨 3
        bx(-1, 22, 3.5, 1, 23.5, 4.5, "scute"),        # 背骨 4 (尾の先)
    ]
    if opt == "a":   # 背骨の両脇に段の板 (背が平らな壁に見えないように)
        side_plates = [bx(-3, 13, 3.5, -1.5, 16, 4.0, "plate"),
                       bx(-3, 16, 3.5, -1.5, 19, 4.0, "scute"),
                       bx(-3, 19, 3.5, -1.5, 22, 4.0, "plate")]
        spine += side_plates + [mirror(b) for b in side_plates]
    if opt in ("a", "b"):
        spine += [bx(-0.5, 17, 5.5, 0.5, 18.5, 6.0, "seam"),     # 頂の溶岩の口
                  bx(-0.5, 20, 5.0, 0.5, 21, 5.5, "seam")]
    if opt == "a":
        spine += [bx(-0.5, 23.5, 4, 0.5, 24, 4.5, "molten")]    # 尾の先の火口
    body += spine
    body += relief(body, "scute", "z+", 3.5, -3.5, 3.5, 12.5, 23.0)   # 背の鱗の盛り上がり

    # ---- 腕 (右): 袖 3 面、手首の輪、肩の噴気孔 (A と C) か肩の塊 (B)
    arm = [
        bx(-8.5, 10.5, -2.5, -8, 21.5, 2.5, "plate"),  # 袖 (外側)
        bx(-8, 10.5, -2.5, -4, 21.5, -2, "plate"),     # 前の板
        bx(-8, 10.5, 2, -4, 21.5, 2.5, "plate"),       # 後ろの板
        bx(-8.5, 9.5, -2.5, -4.5, 10, 2.5, "ash"),     # 手首の輪
        bx(-9, 22, -3, -4, 23.5, 3, "plate"),          # 肩当て
        bx(-9.5, 23.5, -3.5, -4, 24, 3.5, "ash"),      # 肩の縁 (灰)
    ]
    if opt in ("a", "c"):   # 噴気孔: 灰の縁の輪と中の穴 (A は溶岩、C は暗い穴)
        core = "seam" if opt == "a" else "hole"
        arm += [bx(-7.5, 24, 1, -5.5, 25, 1.5, "ash"),
                bx(-7.5, 24, -1.5, -5.5, 25, -1, "ash"),
                bx(-8, 24, -1, -7.5, 25, 1, "ash"),
                bx(-5.5, 24, -1, -5, 25, 1, "ash"),
                bx(-7.5, 24, -1, -5.5, 24.5, 1, core)]
    else:
        arm += [bx(-8, 24, -1.5, -5, 25, 1.5, "scute")]   # 肩の塊 (B)
    if opt == "a":   # 肘の板 (前に出る) を先に置き、鱗はそれを避けて並べる
        arm += [bx(-8.5, 14.5, -3, -4, 16.5, -2.5, "scute")]
    arm += relief(arm, "scute", "z-", -2.5, -8, -4, 11.0, 21.0)       # 籠手の鱗
    if opt == "a":   # 袖の外側は段の鱗 (千鳥に半単位ずつずらす)
        arm += tiles(arm, "scute", "x", -1, -8.5, (-8.5, 11, -2), (-8.5, 21, 2))

    # ---- 脚 (右): 太もも (y 6..12) の板、膝の盛り上がり、外側の溶岩の筋
    leg = [
        bx(-4, 6, -3, 0, 12, -2.5, "plate"),           # 前の板
        bx(-4.5, 6, -2.5, -4, 10, 2.5, "plate"),       # 外側の板 (腕の下まで)
        bx(-4, 6, 2.5, 0, 12, 3, "plate"),             # 後ろの板
        bx(-3.5, 6.5, -3.5, -1.5, 8.5, -3, "scute"),   # 膝の盛り上がり 1
        bx(-3, 7, -4, -2, 8, -3.5, "scute"),           # 膝の盛り上がり 2
        bx(-5.5, 8, -1, -5, 9, 1, "seam"),             # 外側の溶岩の筋
    ]
    if opt == "b":
        leg += [bx(-4, 10.5, -3.5, 0, 11, -3, "seam")]  # 太ももの溶岩の縞
    leg += relief(leg, "scute", "z-", -3.0, -3.5, -0.5, 6.5, 11.0)    # 腿の鱗

    # ---- 靴 (右): 厚い底、つま先の覆い、かかとの段、足首の灰の縁
    boot = [
        bx(-5.5, 0, -3, -5, 1, 3, "scute"),            # 厚い底 (外側)
        bx(-5, 0, -2.5, -4.5, 5, 2.5, "plate"),        # 外側の板
        bx(-4.5, 0, -3, -0.5, 5, -2.5, "plate"),       # 前の板
        bx(-4.5, 0, -4, -0.5, 1.5, -3, "scute"),       # つま先の覆い
        bx(-4.5, 0, 2.5, -0.5, 5, 3, "plate"),         # 後ろの板
        bx(-4.5, 0, 3, -0.5, 2, 3.5, "scute"),         # かかとの段
        bx(-5, 5, -2.5, -4.5, 6, 2.5, "ash"),          # 足首の縁 (外側)
        bx(-4.5, 5, -3, -0.5, 6, -2.5, "ash"),         # 足首の縁 (前)
        bx(-4.5, 5, 2.5, -0.5, 6, 3, "ash"),           # 足首の縁 (後ろ)
    ]
    boot += relief(boot, "scute", "z-", -3.0, -3.5, -0.5, 0.5, 5.0)   # 靴の鱗

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
    """1 面を描く。光の向きで明るさを変え、縁の 1 画素を暗くして箱の段を際立たせる。"""
    base = np.array(pal[mat], float)
    yy, xx = np.mgrid[0:h, 0:w]
    noise = 1 + (rng.random((h, w)) - 0.5) * 0.12
    if mat in GLOW:
        # 溶岩: 中心が明るく、縁が少し暗い (にじみは発光マスクで出す)
        img = np.tile(base, (h, w, 1)) * noise[..., None]
        img[(yy == 0) | (xx == 0) | (yy == h - 1) | (xx == w - 1)] *= 0.75
        return np.clip(img, 0, 255)
    lam = float(np.dot(np.array(normal, float), LIGHT))
    shade = 0.62 + 0.42 * (lam + 1) / 2
    img = np.tile(base * shade, (h, w, 1)) * noise[..., None]
    if mat in ("plate", "scute"):
        seam = ((xx % 8) == 0) | ((yy % 8) == 0)       # 玄武岩の割れ目
        img[seam] *= 0.6
    if mat == "ash":
        img[rng.random((h, w)) < 0.15] *= 1.25         # 灰の凝灰岩の粒
    if mat == "hole":
        img *= 0.8
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
                if b["mat"] in GLOW:
                    glowm[v:v + h, u:u + w, :3] = pal[b["mat"]]
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
                "identifier": "geometry.ember_armor",
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
def _icon_shapes(piece, opt):
    """(材料名, 描く形) の列。後から描いたものが上に乗る。3D の段・板・飾りと同じ並びにする。"""
    if piece == "helmet":
        crest = [("plate", ("rect", (11, 2, 21, 8))),       # 火口の縁 (天蓋に続く)
                 ("molten", ("rect", (14, 4, 18, 6)))] if opt == "a" else \
                [("scute", ("rect", (9, 1, 23, 4)))] if opt == "b" else \
                [("ash", ("rect", (9, 1, 15, 4))), ("plate", ("rect", (16, 1, 23, 4)))]
        ear = [] if opt == "a" else \
              [("seam", ("rect", (2, 16, 4, 18)))] if opt == "b" else []
        return [
            ("plate", ("ell", (5, 6, 27, 27))),         # 甲羅の丸み (兜)
            ("scute", ("rect", (7, 6, 25, 9))),         # 天蓋の段
            ("ash", ("rect", (4, 21, 28, 23))),         # 縁 (灰)
            ("scute", ("rect", (9, 12, 13, 14))),       # 額の鱗
            ("scute", ("rect", (19, 12, 23, 14))),
            ("hole", ("rect", (10, 18, 14, 20))),       # 目の穴 (顔は開けてある)
            ("hole", ("rect", (18, 18, 22, 20))),
        ] + crest + ear
    if piece == "chestplate":
        mid = [("seam", ("rect", (15, 12, 17, 22))), ("molten", ("rect", (15, 22, 17, 25))),
               ("seam", ("rect", (10, 16, 15, 18))), ("seam", ("rect", (17, 19, 22, 21))),
               ("seam", ("rect", (10, 17, 12, 19))), ("seam", ("rect", (21, 20, 23, 22)))] if opt == "a" else \
              [("seam", ("rect", (9, 19, 23, 21)))] if opt == "b" else \
              [("hole", ("rect", (15, 14, 17, 19)))]
        shoulder = [("ash", ("ell", (1, 4, 10, 12))), ("hole", ("rect", (4, 6, 7, 9)))] if opt in ("a", "c") else \
                   [("scute", ("ell", (1, 4, 10, 12)))]
        shoulder_r = [("ash", ("ell", (22, 4, 31, 12))), ("hole", ("rect", (25, 6, 28, 9)))] if opt in ("a", "c") else \
                     [("scute", ("ell", (22, 4, 31, 12)))]
        return [
            ("plate", ("poly", [(9, 7), (23, 7), (25, 12), (23, 27), (9, 27), (7, 12)])),
            ("scute", ("rect", (9, 11, 15, 19))),       # 胸の段
            ("scute", ("rect", (17, 11, 23, 19))),
            ("scute", ("rect", (9, 20, 15, 25))),       # 腹の段
            ("scute", ("rect", (17, 20, 23, 25))),
            ("ash", ("rect", (9, 25, 23, 27))),         # 縁 (灰)
        ] + mid + shoulder + shoulder_r
    if piece == "leggings":
        return [
            ("ash", ("rect", (8, 3, 24, 7))),           # 腰の縁
            ("plate", ("rect", (8, 7, 15, 28))),
            ("plate", ("rect", (17, 7, 24, 28))),
            ("scute", ("ell", (9, 12, 15, 17))),        # 膝の盛り上がり
            ("scute", ("ell", (17, 12, 23, 17))),
            ("scute", ("rect", (9, 20, 14, 23))),       # すねの板
            ("scute", ("rect", (18, 20, 23, 23))),
            ("ash", ("rect", (8, 26, 24, 28))),         # 裾
            ("seam", ("rect", (9, 13, 10, 19))),        # 外側の溶岩の筋 (板の中に入れる)
        ] + ([("seam", ("rect", (9, 10, 23, 11)))] if opt == "b" else [])
    if piece == "boots" and opt == "a":   # 足の形: すねの筒、つま先の出た足、底
        return [
            ("plate", ("rect", (4, 5, 13, 20))),           # 左のすね
            ("plate", ("poly", [(3, 19), (13, 19), (13, 22), (16, 24), (16, 27), (3, 27)])),  # 左の足 (つま先は右)
            ("ash", ("rect", (4, 5, 13, 8))),              # 足首の縁
            ("scute", ("rect", (3, 25, 16, 27))),          # 底
            ("seam", ("rect", (7, 10, 9, 11))),            # 溶岩の筋
            ("plate", ("rect", (18, 5, 27, 20))),          # 右のすね
            ("plate", ("poly", [(18, 19), (28, 19), (28, 27), (15, 27), (15, 24), (18, 22)])),  # 右の足 (つま先は左)
            ("ash", ("rect", (18, 5, 27, 8))),
            ("scute", ("rect", (15, 25, 28, 27))),
            ("seam", ("rect", (21, 10, 23, 11))),
        ]
    return [  # boots
        ("plate", ("rect", (4, 5, 13, 22))),
        ("plate", ("rect", (18, 5, 27, 22))),
        ("ash", ("rect", (4, 5, 13, 8))),               # 足首の縁
        ("ash", ("rect", (18, 5, 27, 8))),
        ("scute", ("rect", (5, 13, 8, 17))),            # 脛の鱗
        ("scute", ("rect", (19, 13, 22, 17))),
        ("scute", ("rect", (3, 22, 14, 27))),           # 厚い底
        ("scute", ("rect", (17, 22, 28, 27))),
        ("ash", ("rect", (3, 22, 14, 23))),             # つま先の縁
        ("ash", ("rect", (17, 22, 28, 23))),
        ("seam", ("rect", (7, 10, 9, 11))),             # 溶岩の筋
        ("seam", ("rect", (21, 10, 23, 11))),
    ]


def make_icon(piece, opt):
    pal = OPTIONS[opt]["pal"]
    lab = np.zeros((32, 32), int)
    for name, (kind, arg) in _icon_shapes(piece, opt):
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
    for y in range(32):
        for x in range(32):
            if not solid[y, x]:
                continue
            key = names[lab[y, x]]
            if key == "hole":
                out[y, x] = (*OUTLINE, 255)
                continue
            if key in GLOW:
                out[y, x] = (*pal[key], 255)
                continue
            if empty_up[y, x] or empty_left[y, x]:
                k = 1.14
            elif empty_down[y, x] or empty_right[y, x]:
                k = 0.7
            else:
                k = 0.92
            if key in ("plate", "scute") and y % 4 == 0:
                k *= 0.8                       # 割れ目
            if key == "ash" and (x + y) % 3 == 0:
                k *= 1.1                       # 灰の粒
            c = np.array(pal[key], float) * k
            out[y, x] = (*np.clip(c, 0, 255).astype(np.uint8), 255)
    outline = solid & (empty_up | empty_left | empty_down | empty_right)
    out[outline & ~np.isin(lab, [ICON_LABEL[g] for g in GLOW])] = (*OUTLINE, 255)
    return Image.fromarray(out, "RGBA")


def make_layer(opt, which):
    """バニラの防具の層 (64x32)。頭・胴・靴は 1、脚は 2。上の 8 行は縁 (灰 / 溶けた黄)。"""
    pal = OPTIONS[opt]["pal"]
    img = np.zeros((32, 64, 4), np.uint8)
    img[..., :3] = pal["plate"]
    img[..., 3] = 255
    yy, xx = np.mgrid[0:32, 0:64]
    dark = (np.array(pal["scute"], float) * 0.7).astype(np.uint8)
    img[(yy % 8) == 0, :3] = dark
    img[(xx % 16) == 0, :3] = dark
    img[:8, :, :3] = np.array(pal["ash"] if which == 1 else pal["seam"], np.uint8)
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
            ic.save(d / "icons" / f"ember_{p}.png")
            icons[p] = ic
        make_layer(opt, 1).save(d / "layers" / "ember_layer_1.png")
        make_layer(opt, 2).save(d / "layers" / "ember_layer_2.png")

        overlaps = check_overlaps(geo)
        clips = clearance(geo)
        glow_px = int((glowm[..., 3] > 0).sum())
        rep = {"cubes": count_cubes(geo), "texture": list(tex.shape[:2]), "glow_pixels": glow_px,
               "overlaps": overlaps, "clearance": clips}
        (d / "check.json").write_text(json.dumps(rep, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report[opt] = rep
        views = render_views(geo, tex, glowm)
        for name, im in zip(["front", "back", "34"], views):
            im.save(PREVIEW / f"{opt}_{name}.png")
        compare_cols.append((OPTIONS[opt]["title"], views))
        icon_cols.append((OPTIONS[opt]["title"], [icon_big(np.array(icons[p]), px=6) for p in PIECES]))
        print(f"{opt}: 箱 {rep['cubes']}, テクスチャ {tex.shape[1]}x{tex.shape[0]}, 重なり {len(overlaps)}, 食い込み {len(clips)}, 光る画素 {glow_px}")
        for line in overlaps + clips:
            print("  ", line)

    sheet_img = sheet("余燼の鎧 ラフ 3案 (3D)", compare_cols,
                      note="行: 正面 / 背面 / 3/4。人型の台に着せた絵。台の色は灰色")
    sheet_img.save(OUT / "compare_armor.png")
    icon_img = sheet("余燼の鎧 ラフ 3案 (アイコン)", icon_cols,
                     note="行: 兜 / 胸 / 脚 / 靴。3D と同じ色・同じ飾り")
    icon_img.save(OUT / "compare_armor_icons.png")
    print(json.dumps({k: {"overlaps": len(v["overlaps"]), "clearance": len(v["clearance"])}
                      for k, v in report.items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
