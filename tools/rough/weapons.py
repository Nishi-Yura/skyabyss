"""武器ラフ v2: 鯨骨の大剣 / 番兵長の鉾 / 水路の顎牙 / 骨棘の矢。

各案は 16x16 のアイコンを手で書いた文字列で定義する。
  - 文字は領域 (役割) を表す。パレットの各文字に「色・厚み・発光」を1つずつ決めてある。
    厚みは領域ごとの規則: 縁 (e, k) = 1、本体 (b, s, m) = 2、稜線 (r, t, Y) = 3、
    鍔 (G, g, h)・宝石 (Z)・ 厚い柄頭 = 4 (宝石は発光)。
  - 厚みの地図は、パレットの厚みを領域に当てはめて作る。その後、4近傍が全部違う孤立画素を
    近傍の中央値にならす (ギザギザ防止)。
  - 縁の色は、本体と同じ色相で暗くした色 (暗い骨の茶、暗いスレート、暗い青緑)。ほぼ黒は使わない。
乱数は使わない (再実行で同じ結果)。

実行: リポジトリ直下で  python tools/rough/weapons.py
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from voxel import ROOT, extrude, voxel_faces, render, icon_big, sheet  # noqa: E402

OUT = ROOT / "docs" / "roughs"


def C(hex_, depth, glow=False):
    """パレットの1色: (RGBA の16進, 厚み, 発光するか)"""
    return (hex_, depth, glow)


# ---------------------------------------------------------------- 鯨骨の大剣

# 文字の意味 (刃): k 外縁 / e 刃の縁 / b 本体 / r 稜線 (中央の1px) / m 本体の陰
#                  (鍔): G 手前の鍔 / g 奥の鍔 / Z 宝石 (発光) / (柄): F 柄 / f 柄の縁 / P 柄頭 / p 柄頭の縁
PAL_GS_A = {
    "k": C("3b2d21", 1), "e": C("6e5538", 1), "b": C("cdac7c", 2), "r": C("f6e7c4", 3), "m": C("a58459", 2),
    "G": C("8b6b47", 4), "g": C("5c4530", 4), "Z": C("5fe8ff", 4, True),
    "F": C("5a3f2c", 2), "f": C("3a2a1e", 1), "P": C("d8c39c", 3), "p": C("7a5f3f", 2),
}
PAL_GS_B = {  # 重厚: 灰スレートの骨、苔の柄、鍔に暗い縁の帯
    "k": C("1f262d", 1), "e": C("3d4a55", 1), "b": C("7d8b95", 2), "r": C("c7d3da", 3), "m": C("5e6a73", 2),
    "G": C("6b7782", 4), "g": C("48535c", 4), "h": C("3a444d", 3), "Z": C("5fe8ff", 4, True),
    "F": C("55703f", 2), "f": C("34472a", 1), "P": C("7b8790", 3), "p": C("4d565e", 2),
}
PAL_GS_C = {  # 異形: 青緑の骨。稜線が共鳴の光で発光
    "k": C("153c44", 1), "e": C("2a6e75", 1), "b": C("86d3cb", 2), "Y": C("8ff4ea", 3, True), "m": C("4fa39c", 2),
    "G": C("3f8c8a", 4), "g": C("2c6464", 4), "Z": C("5fe8ff", 4, True),
    "F": C("1d4a4f", 2), "f": C("163a3f", 1), "P": C("6fb6b0", 3), "p": C("3e7c78", 2),
}

_BLADE = "kebrme"  # 刃の1行の並び (左上の縁 → 本体 → 稜線 → 右下の縁)

GS_A_ICON = [
    "...........kebrm",
    "..........kebrme",
    ".........kebrme.",
    "........kebrme..",
    ".......kebrme...",
    "..gG..kebrme....",
    "...gGkebrme.....",
    "....gGbrme......",
    ".....gZ.........",
    "....fFgG........",
    "...fFf.gG.......",
    "..fFf...gG......",
    ".fFf.....g......",
    "fFf.............",
    "Ff..............",
    "Pp..............",
]
GS_B_ICON = [
    "...........kebrm",
    "..........kebrme",
    ".........kebrme.",
    "........kebrme..",
    ".......kebrme...",
    "..gG..kebrme....",
    "...gGhebrme.....",
    "....gGhrme......",
    ".....gZh........",
    "....fFgGh.......",
    "...fFf.gGh......",
    "..fFf...gGh.....",
    ".fFf.....g......",
    "fFf.............",
    "Ff..............",
    "Pp..............",
]
GS_C_ICON = [
    "...........kebYm",
    "..........kebYme",
    ".........kebYme.",
    "........kebYme..",
    ".......kebYme...",
    "..gG..kebYme....",
    "...gGkebYme.....",
    "....gGbYme......",
    ".....gZ.........",
    "....fFgG........",
    "...fFf.gG.......",
    "..fFf...gG......",
    ".fFf.....g......",
    "fFf.............",
    "Ff..............",
    "Pp..............",
]

# ---------------------------------------------------------------- 番兵長の鉾
# 刃先 (上右) の鉤 (左上に張り出す大きな三日月) と、斜めの柄 (左下へ)。
# 柄: k 外縁 / s 柄の本体 / t 柄の稜線。鉤の文字は刃と同じ。

PAL_PIKE_A = {
    "k": C("3b2d21", 1), "e": C("6e5538", 1), "b": C("cdac7c", 2), "r": C("f6e7c4", 3), "m": C("a58459", 2),
    "s": C("b8985f", 2), "t": C("efdcae", 3),
}
PAL_PIKE_B = {  # 重厚: 灰スレート。柄の付け根に発光する宝石
    "k": C("1f262d", 1), "e": C("3d4a55", 1), "b": C("7d8b95", 2), "r": C("c7d3da", 3), "m": C("5e6a73", 2),
    "s": C("4f5a63", 2), "t": C("8a98a2", 3), "Z": C("5fe8ff", 4, True),
}
PAL_PIKE_C = {  # 異形: 青緑。鉤と刃の稜線が発光
    "k": C("153c44", 1), "e": C("2a6e75", 1), "b": C("86d3cb", 2), "Y": C("8ff4ea", 3, True), "m": C("4fa39c", 2),
    "s": C("3d8f8a", 2), "t": C("6fd0c6", 3),
}

PIKE_A_ICON = [
    ".....ke....kebrm",
    "....kbe...kebrme",
    "...kbre..kebrme.",
    "..kbrbe.kebrme..",
    "..kbbbekebrme...",
    "...kbekebrme....",
    "....ee.kstk.....",
    ".....kkstk......",
    ".....kstk.......",
    "....kstk........",
    "...kstk.........",
    "..kstk..........",
    ".kstk...........",
    "kstk............",
    "stk.............",
    "tk..............",
]
PIKE_B_ICON = [
    ".....ke....kebrm",
    "....kbe...kebrme",
    "...kbre..kebrme.",
    "..kbrbe.kebrme..",
    "..kbbbekebrme...",
    "...kbekebrme....",
    "....ee.kZsk.....",
    ".....kkstk......",
    ".....kstk.......",
    "....kstk........",
    "...kstk.........",
    "..kstk..........",
    ".kstk...........",
    "kstk............",
    "stk.............",
    "tk..............",
]
PIKE_C_ICON = [
    ".....ke....kebYm",
    "....kbe...kebYme",
    "...kbYe..kebYme.",
    "..kbYbe.kebYme..",
    "..kbbbekebYme...",
    "...kbekebYme....",
    "....ee.kstk.....",
    ".....kkstk......",
    ".....kstk.......",
    "....kstk........",
    "...kstk.........",
    "..kstk..........",
    ".kstk...........",
    "kstk............",
    "stk.............",
    "tk..............",
]

# ---------------------------------------------------------------- 水路の顎牙 (短剣)

PAL_FANG_A = {  # 王道: まっすぐな短い牙。鍔なし。白い骨に青灰の縁
    "k": C("3a3d4a", 1), "e": C("7d8a94", 1), "b": C("d7e0dc", 2), "r": C("f4f9f6", 3),
    "F": C("3c4650", 2), "f": C("2a323a", 1), "P": C("b6c4bf", 3), "p": C("6b7a76", 2),
}
PAL_FANG_B = {  # 重厚: 返し (かえし) つき。血の染みた稜線
    "k": C("2a2d36", 1), "e": C("6a6470", 1), "b": C("a79c94", 2), "r": C("b3394a", 3), "m": C("8c837d", 2),
    "y": C("4a4048", 1), "F": C("3b2f2c", 2), "f": C("262020", 1), "P": C("8c7d74", 3), "p": C("5a4d46", 2),
}
PAL_FANG_C = {  # 異形: 上がくるりと曲がる牙。背の線が発光
    "k": C("1a2a33", 1), "e": C("3c6f73", 1), "b": C("9fd8d0", 2), "Y": C("8ff4ea", 3, True),
    "F": C("1f4348", 2), "f": C("14292d", 1), "P": C("6fb0aa", 3), "p": C("3c7c76", 2),
}

FANG_A_ICON = [
    "............kebr",
    "...........kebrk",
    "..........kebrk.",
    ".........kebrk..",
    "........kebrk...",
    ".......kebrk....",
    "......kebrk.....",
    "......fFf.......",
    ".....fFf........",
    "....fFf.........",
    "...fFf..........",
    "..fFf...........",
    ".fFf............",
    "fFf.............",
    "Ff..............",
    "Pp..............",
]
FANG_B_ICON = [
    "...........kebrm",
    "..........kebrme",
    ".........kebrmek",
    "........kebrmek.",
    ".......kebrmeky.",
    "......kebrmeky..",
    ".....kebrmeky...",
    "......fFf.......",
    ".....fFf........",
    "....fFf.........",
    "...fFf..........",
    "..fFf...........",
    ".fFf............",
    "fFf.............",
    "Ff..............",
    "Pp..............",
]
FANG_C_ICON = [
    ".............kYk",
    "............keYk",
    "...........keYek",
    "..........keYbek",
    ".........keYbek.",
    "........keYbek..",
    ".......keYbek...",
    "......keYbek....",
    ".....fFf........",
    "....fFf.........",
    "...fFf..........",
    "..fFf...........",
    ".fFf............",
    "fFf.............",
    "Ff..............",
    "Pp..............",
]

# ---------------------------------------------------------------- 骨棘の矢

PAL_ARROW_A = {  # 王道: 白い骨の矢じり、2枚羽 (斜めの帯)
    "k": C("3b2d21", 1), "a": C("f6e7c4", 3), "b": C("cdac7c", 2), "w": C("d9d4c7", 2), "x": C("9c9686", 1),
}
PAL_ARROW_B = {  # 重厚: 返しつきの矢じり、濃い灰の一枚羽
    "k": C("1e1a22", 1), "a": C("eee6cc", 3), "b": C("cfc3a0", 2), "c": C("9d9070", 2),
    "g": C("6fe6ff", 3, True), "f": C("3b3a48", 2), "x": C("6a6a78", 1),
}
PAL_ARROW_C = {  # 異形: 青緑の矢。背の芯が発光。羽は魚の背びれ状
    "k": C("17303a", 1), "a": C("e6fff7", 3), "b": C("9fe6d6", 2), "c": C("4aa39a", 2), "e": C("1f5c5a", 2),
    "g": C("6fe6ff", 3, True),
}

ARROW_A_ICON = [
    "..............ka", ".............kbk", "............kbak", "...........kbabk", "..........kbabk.",
    ".........kbabk..", "........kbk.....", ".......kbk......", "......kbk.......", "..xw.kbk........",
    ".xw.kbk.........", "xw.kbk..........", "w.kbk...........", ".kbk.wx.........", "kbk.wx..........",
    "bk.wx...........",
]
ARROW_B_ICON = [
    "..............ka", "............kbgk", "...........kbgck", ".........kkbgck.", ".........kbgck..",
    ".......kbbgck...", "........kbk.....", ".......kck......", "......kck.......", "..xf.kck........",
    ".xf.kck.........", "xf.kck..........", "f.kck...........", ".kck.fx.........", "kck.fx..........",
    "ck.fx...........",
]
ARROW_C_ICON = [
    "..............kg", ".............kbk", "............kbak", "...........kbabk", "..........kbabk.",
    ".........kbabk..", "........kbk.....", ".......kck......", "......kbk.......", "..ce.kck........",
    ".ce.kbk.........", "ce.kck..........", "e.kbk...........", ".kck.ec.........", "kck.ec..........",
    "ck.ec...........",
]

ITEMS = {
    "whalebone_greatsword": {"title": "鯨骨の大剣", "opts": [
        ("a", "A 王道", PAL_GS_A, GS_A_ICON),
        ("b", "B 重厚", PAL_GS_B, GS_B_ICON),
        ("c", "C 異形", PAL_GS_C, GS_C_ICON)]},
    "captain_pike": {"title": "番兵長の鉾", "opts": [
        ("a", "A 王道", PAL_PIKE_A, PIKE_A_ICON),
        ("b", "B 重厚", PAL_PIKE_B, PIKE_B_ICON),
        ("c", "C 異形", PAL_PIKE_C, PIKE_C_ICON)]},
    "waterway_fang": {"title": "水路の顎牙", "opts": [
        ("a", "A 王道", PAL_FANG_A, FANG_A_ICON),
        ("b", "B 重厚", PAL_FANG_B, FANG_B_ICON),
        ("c", "C 異形", PAL_FANG_C, FANG_C_ICON)]},
    "bone_spike_arrow": {"title": "骨棘の矢", "opts": [
        ("a", "A 王道", PAL_ARROW_A, ARROW_A_ICON),
        ("b", "B 重厚", PAL_ARROW_B, ARROW_B_ICON),
        ("c", "C 異形", PAL_ARROW_C, ARROW_C_ICON)]},
}


def hex2rgba(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def build_maps(rows, pal):
    """文字の列から、色・発光・厚みの地図を作る。厚みは領域 (文字) の値をそのまま使う。"""
    assert len(rows) == 16, f"rows={len(rows)}"
    icon = np.zeros((16, 16, 4), np.uint8)
    glow = np.zeros((16, 16), bool)
    depth = np.zeros((16, 16), int)
    for y, row in enumerate(rows):
        assert len(row) == 16, f"row {y} len {len(row)}: {row!r}"
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            hx, d, g = pal[ch]
            icon[y, x] = hex2rgba(hx)
            glow[y, x] = g
            depth[y, x] = d
    return icon, glow, depth


def despike(depth):
    """4近傍がすべて不透明で、4つとも違う厚みの画素は、近傍の中央値にする (孤立した棘・くぼみを消す)。"""
    out = depth.copy()
    H, W = depth.shape
    for y in range(1, H - 1):
        for x in range(1, W - 1):
            d = depth[y, x]
            if d == 0:
                continue
            nb = [depth[y - 1, x], depth[y + 1, x], depth[y, x - 1], depth[y, x + 1]]
            if min(nb) == 0:
                continue
            if all(v != d for v in nb):
                out[y, x] = sorted(nb)[2]
    return out


def build_option(item, key, pal, rows):
    icon, glow, depth = build_maps(rows, pal)
    depth = despike(depth)
    d = OUT / "weapons" / item / key
    d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(icon).save(d / "icon.png")
    Image.fromarray((depth * 60).astype(np.uint8), "L").save(d / "depth.png")
    grid, gl = extrude(icon, depth=depth, glow=glow)
    faces = voxel_faces(grid, gl)
    return icon, faces


def main():
    for item, spec in ITEMS.items():
        cols = []
        for key, label, pal, rows in spec["opts"]:
            icon, faces = build_option(item, key, pal, rows)
            front = render(faces, yaw=30, pitch=25, size=(520, 520), margin=0.03)
            side = render(faces, yaw=90, pitch=0, size=(520, 520), margin=0.03)
            cols.append((label, [icon_big(icon, 12), front, side]))
        img = sheet(
            f"{spec['title']} ({item})",
            cols,
            note="行: 上 = 16x16 拡大 / 中 = 3D yaw30 pitch25 / 下 = 側面 yaw90 (厚みの確認)",
        )
        img.save(OUT / f"weapons_{item}.png")
        print("wrote", OUT / f"weapons_{item}.png")


if __name__ == "__main__":
    main()
