"""村の NPC 2 人 (語り部 storyteller、鯨追い whale_chaser) のラフ 3 案と、採用案の書き出し。

- 語り部: 腰の曲がった老婆。A = 肩掛けと結縄の素朴な老婆、B = 頭巾と織りの前掛けの巫女寄り、
  C = 背負い籠と継ぎ当ての山里の老婆。
- 鯨追い: 痩せて背の高い旅人。A = 長い外套と広いつばの帽子、B = 鯨骨の留め具と羽の帽子、
  C = 裾のぎざぎざの長外套と遠眼鏡のケースの帽子。

立方体は 0.5 単位の格子、テクスチャは 1 単位 = 2 画素 (1 ブロック = 32px)、面ごとの uv。
部品は面で接するだけ (置くたびに重なりを調べ、最後に voxel.check_overlaps でも確かめる)。
中が空の部品 (外套・袖の壁) は、足や体と重ならないよう壁だけの箱にする。
座標: y が上、前は +z。書き出す geo だけ root 骨を yaw 180 度回して前を -z にする (mod のモブと同じ向き)。
光る部分は作らない (glowmask は書かない)。

実行 (リポジトリ直下):
    python tools/rough/npc.py            ラフ 2 人 × 3 案、比較シート、採用案の顔アイコン (プレビュー) を docs/review/npc_roughs/ に書く
    python tools/rough/npc.py --install  CHOSEN の案を src/main/resources に書き出す
                                         (geo, texture, animation, 顔アイコン 3 表情 × 2 人)
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import mobs_small_v2 as v2  # noqa: E402
import watcher as W  # noqa: E402  (Mdl・cloth・view・head_view だけ借りる。watcher.py は書き換えない)

ROOT = vx.ROOT
OUT = ROOT / "docs" / "review" / "npc_roughs"
SRC = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss"
CHOSEN = {"storyteller": "a", "whale_chaser": "a"}  # 採用案 (レビュー後に決めて書き換える)
VIEW = (300, 420)
ICON = 80
EXPRS = ("neutral", "smile", "grave")
NPCS = ("storyteller", "whale_chaser")
LABEL = {
    "storyteller": {"a": "A 肩掛けの老婆", "b": "B 頭巾の巫女寄り", "c": "C 背負い籠の山里の老婆"},
    "whale_chaser": {"a": "A 長外套・広つば・鯨骨と羽", "b": "B 鯨骨と羽の帽子 (山高)", "c": "C 裾のぎざぎざ外套"},
}
HEIGHT = {"storyteller": 24.5, "whale_chaser": 30.0}  # 単位 (1 ブロック = 16 単位)
ICON_YMIN = {"storyteller": 13.0, "whale_chaser": 19.0}

RAMP = v2.RAMP
RAMP.update({
    "sk_old": ("#6E4E3B", "#9A7257", "#B38B6D", "#C9A386"),
    "sk_tan": ("#5E3F2C", "#84593E", "#A37150", "#B98A63"),
    "hair_gray": ("#6F6C68", "#A7A49D", "#C4C1B9", "#DCD9D1"),
    "undyed": ("#5E5646", "#7F7460", "#9C927B", "#B7AE97"),
    "indigo": ("#141C2B", "#1F2B40", "#2C3B57", "#3B4D6E"),
    "ochre": ("#4A3A20", "#6A5330", "#876B42", "#A3844F"),
    "rope": ("#40321F", "#5E4A2D", "#7E6440", "#9A7D55"),
    "straw": ("#5F5526", "#7F7239", "#9C9050", "#B7AB6A"),
    "coat": ("#353328", "#4F4C3C", "#6B6751", "#85805F"),
    "leather": ("#24180F", "#38281A", "#4C3823", "#63492F"),
    "canvas": ("#4D4536", "#6E6450", "#8A7F66", "#A69B7E"),
    "scarf": ("#4A241A", "#6A3426", "#8A4A34", "#A3604A"),
    "brass": ("#4F3F1A", "#7A6230", "#A08443", "#BFA45C"),
    "map": ("#5F5439", "#86795A", "#A69B77", "#C2B894"),
    "pot": ("#25272A", "#3B3F45", "#515761", "#686E79"),
})

# ---------------------------------------------------------------- 顔 (頭の正面に 0.5 単位のマス目で描く)
# 1 文字 = 0.5 単位の 1 画素。上の行が頭の上。文字は色の表 (ST_COL / WC_COL) で引く。
ST_BOX = (-3.5, 3.5, 16.5, 23.5, -3.5, 3.5)   # 語り部の頭 (7 x 7)
ST_COL = {"k": "#2B2019", "g": "#CFCAC0", "w": "#8E6C55", "n": "#9A7257", "r": "#B9785F", "m": "#5A3A2E"}
ST_FACE = {
    "neutral": [
        "..............", "..wwwwwwwwww..", "..............", "...ggg..ggg...",
        "..............", "...kkk..kkk...", "..............", "......nn......",
        "......nn......", "..............", "....mmmmmm....", "..............",
        "..............", "..............",
    ],
    "smile": [
        "..............", "..wwwwwwwwww..", "..............", "...ggg..ggg...",
        "....k....k....", "...k.k..k.k...", "..............", "......nn......",
        "..rr..nn..rr..", "..............", "...m......m...", "....mmmmmm....",
        "..............", "..............",
    ],
    "grave": [
        "..............", "..wwwwwwwwww..", "..............", "...gg....gg...",
        ".....g..g.....", "...kkk..kkk...", "..............", "......nn......",
        "......nn......", "..............", "....mmmmmm....", "...m......m...",
        "..............", "..............",
    ],
}
WC_BOX = (-3.0, 3.0, 21.0, 27.0, -3.0, 3.0)   # 鯨追いの頭 (6 x 6)
WC_COL = {"k": "#1E1712", "g": "#3B2E24", "w": "#8E6A52", "n": "#8A5F45", "m": "#4A2F25", "d": "#4B4238"}
WC_FACE = {
    "neutral": [
        "............", "..wwwwwwww..", "............", "..ggg..ggg..",
        "............", "..kk....kk..", "..w......w..", "............",
        ".....nn.....", ".....nn.....", "...mmmmmm...", "d.dd.dd.dd.d",
    ],
    "smile": [
        "............", "..wwwwwwww..", "............", "..ggg..ggg..",
        "............", "..ww....ww..", ".wkkw..wkkw.", "............",
        ".....nn.....", "..m..nn..m..", "...mmmmmm...", "d.dd.dd.dd.d",
    ],
    "grave": [
        "............", "..wwwwwwww..", "...w....w...", "...ggggggg..",
        "............", "..kk....kk..", "..w......w..", "............",
        ".....nn.....", ".....nn.....", "..mmmmmmmm..", "d.md.dd.dm.d",
    ],
}


def face_det(box, rows, colors):
    """頭の正面 (z1 の面) に、文字の絵 (rows) を描く。box = (x0, x1, y0, y1, z0, z1)。"""
    x0, x1, y0, y1, _, z1 = box
    assert len(rows) == (y1 - y0) * 2, (len(rows), box)
    for r in rows:
        assert len(r) == (x1 - x0) * 2, (r, box)

    def det(X, Y, Z, f):
        if f != "south" or abs(Z - z1) > 1e-6:
            return None
        c = int(math.floor((X - x0) * 2))
        r = int(math.floor((y1 - Y) * 2))
        if 0 <= r < len(rows) and 0 <= c < len(rows[r]):
            return colors.get(rows[r][c])
        return None
    return det


# ---------------------------------------------------------------- 箱を置く小道具

def bx(m, bone, x0, x1, y0, y1, z0, z1, mat, det=None):
    m.box(bone, x0, y0, z0, x1, y1, z1, mat, det)


def side(m, bone, s, x0, x1, y0, y1, z0, z1, mat, det=None):
    """s=+1 は x0..x1 の箱、s=-1 は x を反転した箱 (左右の骨に一つずつ置く)。"""
    a, b = sorted((s * x0, s * x1))
    m.box(bone, a, y0, z0, b, y1, z1, mat, det)


def shell(m, bone, x0, x1, y0, y1, z0, z1, t, mat, det=None):
    """四方の壁だけの箱 (中は空)。前 (+z)・後・左右の壁。厚み t。"""
    m.box(bone, x0, y0, z1 - t, x1, y1, z1, mat, det)
    m.box(bone, x0, y0, z0, x1, y1, z0 + t, mat, det)
    m.box(bone, x0, y0, z0 + t, x0 + t, y1, z1 - t, mat, det)
    m.box(bone, x1 - t, y0, z0 + t, x1, y1, z1 - t, mat, det)


def weave(c1, c2):
    """織り模様 (前掛け・籠): 2 画素ごとの格子に二色。"""
    def det(X, Y, Z, f):
        if f not in ("south", "north"):
            return None
        c = math.floor(X * 2)
        r = math.floor(Y * 2)
        if r % 4 == 0 and c % 2 == 0:
            return c1
        if c % 4 == 1 and r % 2 == 1:
            return c2
        return None
    return det


def patches(col):
    """継ぎ当て: 前の面に、位置で決まる四角い当て布。"""
    def det(X, Y, Z, f):
        if f != "south":
            return None
        if (math.floor(X / 2) + math.floor(Y / 3)) % 5 == 0 and math.floor(X * 2) % 4 in (0, 1):
            return col
        return None
    return det


def finish(m, height):
    geo, tex, gl = m.finish()
    g = geo["minecraft:geometry"][0]
    g["description"]["visible_bounds_width"] = 3
    g["description"]["visible_bounds_height"] = 3
    g["description"]["visible_bounds_offset"] = [0, 1.5, 0]
    for b in g["bones"]:
        if "parent" not in b:
            b["parent"] = "root"
    g["bones"].insert(0, {"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 180.0, 0.0]})
    return geo, tex, gl


# ---------------------------------------------------------------- 語り部

def fold(mat, period=4):
    """布のひだ: 前と後ろの面に、縦の濃い筋だけ (まだらは使わない)。"""
    dk = RAMP[mat][0]

    def det(X, Y, Z, f):
        if f not in ("south", "north"):
            return None
        return dk if math.floor(X * 2) % period == 0 else None
    return det


def stripes(c1, c2, every=4):
    """太めの縞 (織り模様): 縦の帯と、ところどころの横の帯。"""
    def det(X, Y, Z, f):
        if f not in ("south", "north", "east", "west"):
            return None
        c = math.floor((X + Z) * 2)
        r = math.floor(Y * 2)
        if c % every < every // 2:
            return c1
        if r % 6 == 0:
            return c2
        return None
    return det


def storyteller(v, expr="neutral"):
    """語り部: 腰から上を前へ 15 度倒した torso 骨の中に、着物・肩掛け・頭・腕を置く。
    脚は body の子 (まっすぐ)。前かがみで首が前に出るシルエット。"""
    m = W.Mdl("storyteller")
    m.bone("body", (0, 8, 0))
    m.bone("legL", (1.5, 8, 0), "body")
    m.bone("legR", (-1.5, 8, 0), "body")
    m.bone("torso", (0, 3.5, 0), "body")
    m.bone("armL", (4.5, 14, 0), "torso")
    m.bone("armR", (-4.5, 14, 0), "torso")
    m.bone("head", (0, 16.5, 0), "torso")
    m.bone("hair", (0, 16.5, 0), "head")
    m.bone("shawl", (0, 14, 0), "torso")
    m.bone("cord", (0, 7, 4), "torso")
    m.bone("basket", (0, 9, -5), "torso")
    for b in m.bones:
        if b["name"] == "torso":
            b["rotation"] = [10.0, 0.0, 0.0]  # 前へ 10 度 (体の前傾。支点は裾の上端)

    robe = {"a": "undyed", "b": "indigo", "c": "ochre"}[v]
    rd = fold(robe, 4)
    if v == "c":
        rd = W.combine(patches(RAMP["undyed"][1]), rd)
    shawl_mat = {"a": "indigo", "b": "undyed", "c": "indigo"}[v]

    # 脚と草履 (裾の下から足先)
    for s, leg in ((1, "legL"), (-1, "legR")):
        side(m, leg, s, 0.5, 2.5, 1, 3.5, -1, 1, "sk_old")
        side(m, leg, s, 0.5, 2.5, 0, 1, -1.5, 2, "straw")

    # 着物: 四方の壁。前と後ろの裾は歯のようにぎざぎざ
    shell(m, "torso", -4, 4, 3.5, 14, -3.5, 3.5, 0.5, robe, rd)
    for x0, x1, y0 in [(-4, -2.5, 3), (-2.5, -1, 2.5), (-1, 0.5, 3), (0.5, 2, 2.5), (2, 4, 3)]:
        bx(m, "torso", x0, x1, y0, 3.5, 3, 3.5, robe, rd)
        bx(m, "torso", x0, x1, y0, 3.5, -3.5, -3, robe, rd)

    # 肩掛け: 肩の帯と、背の丸み (腰の曲がり)
    bx(m, "shawl", -4.5, 4.5, 14, 15.5, -4, 3.5, shawl_mat, stripes(RAMP[shawl_mat][1], RAMP[shawl_mat][2]))
    bx(m, "shawl", -3, 3, 14.5, 17, -6, -4, "indigo" if v != "b" else "undyed")  # 背の盛り上がり
    if v == "a":
        # 胸から前へ垂れる肩掛けの前の布 (喉元は V 字に開く)、結び目、垂れ端
        flap = ["".join("." if (r < 3 and 6 <= c <= 9) else "#" for c in range(16)) for r in range(9)]
        m.plate("shawl", -4, 14, 3.5, flap, 0.5, "indigo", stripes(RAMP["indigo"][1], RAMP["indigo"][2]))
        bx(m, "shawl", -1, 1, 8.5, 9.5, 4, 4.5, "indigo")             # 結び目
        bx(m, "shawl", 0.5, 1.5, 4, 8.5, 4, 4.5, "indigo")            # 垂れ端 (右)
        bx(m, "shawl", -1.5, -0.5, 4, 8.5, 4, 4.5, "indigo")          # 垂れ端 (左)

    # 首と頭 (頭の支点は首の付け根)
    bx(m, "torso", -1.5, 1.5, 15.5, 16.5, -1.5, 1.5, "sk_old")
    head_det = face_det(ST_BOX, ST_FACE[expr], ST_COL)
    bx(m, "head", -3.5, 3.5, 16.5, 23.5, -3.5, 3.5, "sk_old", head_det)
    bx(m, "head", -2, 2, 15.5, 16.5, 2, 3.5, "sk_old")            # あご (一段細く突き出す)
    bx(m, "head", -0.5, 0.5, 18.5, 20, 3.5, 4, "sk_old")          # 鼻
    bx(m, "head", -2.5, 2.5, 21.5, 22, 3.5, 4, "sk_old")          # 眉の張り出し
    for s in (1, -1):
        side(m, "head", s, 3.5, 4, 18, 19.5, 0, 2.5, "sk_old")    # 頬骨
        side(m, "head", s, 3.5, 4, 19.5, 21, 0, 1, "sk_old")  # 耳

    # 髪・頭巾
    if v == "b":
        bx(m, "hair", -3.5, 3.5, 23.5, 24.5, -3.5, 3.5, "undyed")  # 頭巾の天
        bx(m, "hair", -3.5, 3.5, 17, 23.5, -4, -3.5, "undyed")     # 後ろ
        bx(m, "hair", -3.5, 3.5, 22.5, 23.5, 3.5, 4, "undyed")     # 額の縁
        for s in (1, -1):
            side(m, "hair", s, 3.5, 4, 17.5, 23.5, -3.5, 0, "undyed")
    else:
        bx(m, "hair", -3.5, 3.5, 23.5, 24, -3.5, 3.5, "hair_gray")  # 頭頂の髪の塊
        bx(m, "hair", -3.5, 3.5, 17, 23.5, -4, -3.5, "hair_gray")   # 後ろの髪
        for s in (1, -1):
            side(m, "hair", s, 3.5, 4, 19.5, 23.5, -3.5, -0.5, "hair_gray")
        if v == "a":
            bx(m, "hair", -1.5, 1.5, 24, 25, -3.5, -1.5, "hair_gray")  # 後ろの髷

    # 腕: 広い袖、袖口が広がる。手は袖口の下
    for s, arm in ((1, "armL"), (-1, "armR")):
        side(m, arm, s, 4, 6, 6, 14, -1.5, 1.5, robe, rd)
        side(m, arm, s, 4.5, 6.5, 4.5, 6, -2, 2, robe, rd)          # 袖口の広がり
        side(m, arm, s, 4, 5.5, 3, 4.5, -0.5, 1, "sk_old")          # 手

    if v == "a":
        # 腰から下がる結縄: 結び目 4 個 (1 単位の玉) と、その間の細い縄
        for y0 in (6, 4, 2, 0):
            bx(m, "cord", -3, -2, y0, y0 + 1, 4, 4.5, "rope")
        for y0 in (5, 3, 1):
            bx(m, "cord", -2.5, -2, y0, y0 + 1, 4, 4.5, "rope")
        bx(m, "cord", -2.5, -2, 7, 7.5, 4, 4.5, "rope")
    if v == "b":
        apron = ["#" * 12 for _ in range(16)]
        m.plate("torso", -3, 12, 3.5, apron, 0.5, "undyed", stripes(RAMP["undyed"][0], RAMP["undyed"][3]))
    if v == "c":
        bx(m, "basket", -3, 3, 4, 13.5, -6, -4, "rope", stripes(RAMP["rope"][0], RAMP["rope"][2], 4))
        bx(m, "basket", -3.5, 3.5, 13.5, 14, -6.5, -4, "rope")
        for s in (1, -1):
            side(m, "torso", s, 1.5, 2.5, 9, 14, -4, -3.5, "canvas")
    return m


def whale_chaser(v, expr="neutral"):
    """鯨追い: 膝下までの外套 (前は開いて中の服が見える)、ブーツの折り返し、広い袖、背の袋。"""
    m = W.Mdl("whale_chaser")
    m.bone("body", (0, 6, 0))
    m.bone("legL", (1.5, 5, 0), "body")
    m.bone("legR", (-1.5, 5, 0), "body")
    m.bone("armL", (4.5, 18.5, 0), "body")
    m.bone("armR", (-4.5, 18.5, 0), "body")
    m.bone("head", (0, 21, 0), "body")
    m.bone("hat", (0, 21, 0), "head")
    m.bone("pack", (0, 10, -5), "body")

    coat = {"a": "coat", "b": "ochre", "c": "coat"}[v]
    cd = fold(coat, 3)
    if v == "c":
        cd = W.combine(patches(RAMP["coat"][1]), cd)
    scarf = {"a": "scarf", "b": "undyed", "c": "scarf"}[v]

    # 脚: ズボン、ブーツ、折り返し
    for s, leg in ((1, "legL"), (-1, "legR")):
        side(m, leg, s, 0.5, 2.5, 2, 6, -1, 1, "canvas")
        side(m, leg, s, 0.5, 2.5, 0, 1.5, -1.5, 1.5, "leather")
        side(m, leg, s, 0.5, 3, 1.5, 2, -1.5, 1.5, "leather")  # 折り返し

    # 外套: 壁。前は中央で開き (x -0.5..0.5)、中の服が見える。裾は膝下で歯のようにぎざぎざ
    bx(m, "body", -3.5, 3.5, 6, 20, -2.5, -2, coat, cd)  # 後ろの壁
    bx(m, "body", -3.5, -3, 6, 20, -2, 2, coat, cd)      # 左の壁
    bx(m, "body", 3, 3.5, 6, 20, -2, 2, coat, cd)         # 右の壁
    bx(m, "body", -3.5, -0.5, 6, 20, 2, 2.5, coat, cd)  # 前の壁 (左)
    bx(m, "body", 0.5, 3.5, 6, 20, 2, 2.5, coat, cd)    # 前の壁 (右)
    if v == "a":
        teeth = [(-3.5, -2.5, 5), (-2.5, -1, 5.5), (-1, 0.5, 5), (0.5, 2, 5.5), (2, 3.5, 5)]
    elif v == "b":
        teeth = [(-3.5, -3, 5), (-3, -2, 5.5), (-2, -1, 5), (-1, 0, 5.5), (0, 1, 5), (1, 2, 5.5), (2, 3, 5), (3, 3.5, 5.5)]
    else:
        teeth = [(-3.5, -2.5, 5), (-2.5, -1.5, 5.5), (-1.5, -0.5, 5), (-0.5, 0.5, 5.5), (0.5, 1.5, 5),
                 (1.5, 2.5, 5.5), (2.5, 3.5, 5)]
    for x0, x1, y0 in teeth:
        bx(m, "body", x0, x1, y0, 6, 2, 2.5, coat, cd)
    if v == "c":
        for x0, x1, y0 in teeth[::2]:
            bx(m, "body", x0, x1, y0, 6, -2.5, -2, coat, cd)

    # 中の服 (シャツ)、前の合わせ (襟)、ボタン、ポーチ、遠眼鏡のケース
    bx(m, "body", -2.5, 2.5, 8, 19.5, -1.5, 1.5, "undyed")
    bx(m, "body", 0.5, 1.5, 12, 19, 2.5, 3, coat, cd)     # 合わせの襟 (右)
    bx(m, "body", -1.5, -0.5, 12, 19, 2.5, 3, coat, cd)   # 合わせの襟 (左)
    bx(m, "body", 1, 1.5, 14, 14.5, 3, 3.5, "brass")       # ボタン
    bx(m, "body", 1, 1.5, 17, 17.5, 3, 3.5, "brass")
    bx(m, "body", -2.5, -0.5, 9, 11, 2.5, 3.5, "leather")  # ポーチ
    if v in ("a", "c"):
        bx(m, "body", 0, 2, 6.5, 10, 2.5, 3.5, "leather")  # 遠眼鏡のケース

    # 襟、首の布、肩当て (ケープ状の短い肩掛け)、背のケープ
    bx(m, "body", -4, 4, 20, 20.5, -2.5, 2.5, coat, cd)
    bx(m, "body", -2, 2, 20.5, 21, -2, 3.5, scarf)
    bx(m, "body", -1.5, -0.5, 17, 20.5, 3, 3.5, scarf)     # 首の布の垂れ
    bx(m, "body", -4.5, 4.5, 18.5, 20, -3.5, -2.5, coat, cd)
    for s in (1, -1):
        side(m, "body", s, 3.5, 5, 18.5, 20, -2, 2, coat, cd)  # 肩当て

    # 背の袋: 紐の結び目、丸めた地図 (段で円柱に)、鍋
    if v == "b":
        pk = (-3, 3, 8, 16, -5, -3.5)
    else:
        pk = (-3, 3, 6, 17, -5, -3.5)
    bx(m, "pack", pk[0], pk[1], pk[2], pk[3], pk[4], pk[5], "canvas", stripes(RAMP["canvas"][0], RAMP["canvas"][2], 3))
    for s in (1, -1):
        side(m, "body", s, 1.5, 2.5, 14, 18, -3.5, -2.5, "leather")
    bx(m, "pack", -2.5, 2.5, 17, 17.5, -5, -4, "map")       # 地図 (下段)
    bx(m, "pack", -2, 2, 17.5, 18, -5, -4.5, "map")       # 地図 (上段)
    bx(m, "pack", -3.5, -3, 8, 12, -4.5, -3.5, "canvas")    # 脇のポケット
    bx(m, "pack", -1.5, 1.5, 4.5, 6, -5.5, -5, "pot")       # 鍋
    bx(m, "pack", -1, 1, 6, 6.5, -5.5, -5, "leather")       # 紐の結び目

    # 腕: 太い袖、手袋
    for s, arm in ((1, "armL"), (-1, "armR")):
        side(m, arm, s, 3.5, 6, 7.5, 18.5, -1.5, 1.5, coat, cd)
        side(m, arm, s, 3.5, 6.5, 5.5, 7.5, -2, 2, coat, cd)
        side(m, arm, s, 3.5, 6, 4, 5.5, -1, 1, "leather")

    # 頭: 日焼けした顔、こけた頬、無精ひげ。つばは額より前へ張り出す
    head_det = face_det(WC_BOX, WC_FACE[expr], WC_COL)
    bx(m, "head", -3, 3, 21, 27, -3, 3, "sk_tan", head_det)
    bx(m, "head", -0.5, 0.5, 23, 24.5, 3, 4, "sk_tan")
    bx(m, "head", -2.5, 2.5, 24.5, 25, 3, 3.5, "sk_tan")
    for s in (1, -1):
        side(m, "head", s, 3, 3.5, 23, 24.5, -0.5, 0.5, "sk_tan")

    # 帽子: 広いつば (前へ張り出す)、山、鯨骨の留め具と羽
    crown_top = 30.0 if v == "b" else 29.5
    bx(m, "hat", -5.5, 5.5, 27, 27.5, -5.5, 6.5, "leather")
    bx(m, "hat", -2.5, 2.5, 27.5, crown_top, -2.5, 2.5, "leather")
    if v in ("a", "b"):
        bx(m, "hat", -1, 1, 27.5, 28, 2.5, 3, "bone")
        bx(m, "hat", 2.5, 3.5, 28, 29, -0.5, 0.5, "undyed")
    else:
        bx(m, "hat", -2.5, 2.5, 27.5, 28, 2.5, 3, "bone")
    if v == "c":
        for s in (1, -1):
            side(m, "hat", s, 4.5, 5.5, 26.5, 27, 4.5, 5.5, "leather")
        bx(m, "hat", -0.5, 0.5, 28, 29, 2.5, 3, "brass")
    return m



# ---------------------------------------------------------------- 語り部 A改 (採用)
# A 案 (肩掛けの老婆) の作り直し。第 1・2 回のラフは頭が 7 単位の立方体で胴の半分ほどもあり、村人の箱頭に見えた。
#  - 頭を 5×5×5 に小さくし、首から前へ突き出す (腰の曲がった老婆の「くの字」を、骨の回転を使わず段のずらしで作る。
#    GeckoLib は骨の回転の符号を反転して読むので、回転に頼ると前かがみが後ろへ反ってしまうおそれがある)
#  - 顔は 0.5 単位で彫る: 眉の張り出し (目が影に入る)、頬骨、鉤鼻、耳の半分を覆う髪、後ろの髷と簪
#  - 背の丸み (肩掛けの盛り上がり二段)、帯と背の結び目、腰から垂れる結縄 (結び目 3 つと房)
#  - 手は胸の下で組む (広い袖の袂が垂れ、袖口から出た両手が帯の上で合わさる)
RAMP.update({
    "tabi": ("#9C968A", "#C4BEB0", "#D8D3C6", "#E6E2D8"),
    "kimono": ("#3E3A33", "#58524A", "#6F685E", "#857D71"),
    "obi": ("#4A3320", "#6B4A2C", "#86603B", "#A07750"),
    "pin": ("#3A2614", "#5A3C20", "#7A5430", "#946B42"),
})
ST2_COL = {"h": "#B9B5AC", "k": "#241A14", "w": "#7E5E49", "m": "#5A372B", "r": "#B5806A", "l": "#C59C7F"}
# 頭の前の面 (x -2.5..2.5、y 18..23 → 10×10 画素)。上の行が上。眉 (y21.5..22)・鼻・頬骨の箱が前に重なる行は塗っても見えない
ST2_FACE = {
    "neutral": ["hhhhhhhhhh", "hw......wh", "..........", ".w......w.", ".kk....kk.",
                "w........w", "..........", "..w....w..", "...mmmm...", "....ww...."],
    "smile": ["hhhhhhhhhh", "hw......wh", "..........", "..........", ".kk....kk.",
              "w.l....l.w", "..........", ".rm....mr.", "..mmmmmm..", "....ww...."],
    "grave": ["hhhhhhhhhh", "hww....wwh", "..........", "..kk..kk..", ".kk....kk.",
              "w........w", "..........", "..w....w..", "..mmmmmm..", "..m....m.."],
}


def kimono(front_z):
    """着物: 縦のひだ、裾の濃い帯、前の合わせ (右前に重なる縦の線)。"""
    dk, md, lt, hi = RAMP["kimono"]
    edge = RAMP["undyed"][2]

    def det(X, Y, Z, f):
        if f in ("up", "down"):
            return None
        if Y < 3.5:
            return dk if math.floor(X * 2 + Z * 2) % 3 else RAMP["kimono"][1]
        if f == "south" and abs(Z - front_z) < 1e-6 and 0.5 <= X < 1.5:
            return edge if 0.5 <= X < 1 else dk
        u = X if f in ("south", "north") else Z
        if math.floor(u * 2) % 5 == 0:
            return dk
        return None
    return det


def storyteller_d(v, expr="neutral"):
    m = W.Mdl("storyteller")
    m.bone("body", (0, 2.5, 0))
    m.bone("legL", (1.25, 2.5, 0.5), "body")
    m.bone("legR", (-1.25, 2.5, 0.5), "body")
    m.bone("armL", (4.5, 16.5, 1.5), "body")
    m.bone("armR", (-4.5, 16.5, 1.5), "body")
    m.bone("head", (0, 17.5, 2.5), "body")
    m.bone("hair", (0, 23, 2.5), "head")
    m.bone("shawl", (0, 15, 0), "body")
    m.bone("cord", (2.25, 10.5, 3.75), "body")

    sd = stripes(RAMP["indigo"][1], RAMP["indigo"][3], 6)
    # 足: 足袋と草履
    for s, leg in ((1, "legL"), (-1, "legR")):
        side(m, leg, s, 0.5, 2, 0.5, 2.5, -0.5, 1.5, "tabi")
        side(m, leg, s, 0.5, 2, 0.5, 1, 1.5, 2.5, "tabi")          # つま先
        side(m, leg, s, 0.5, 2.5, 0, 0.5, -1, 2.5, "straw")        # 草履
    # 着物の裾 (下が少し広い)。上体は段ごとに前へずらして、腰の曲がりを作る
    bx(m, "body", -4, 4, 2.5, 6, -3, 3.5, "kimono", kimono(3.5))
    bx(m, "body", -3.5, 3.5, 6, 10.5, -2.5, 3, "kimono", kimono(3))
    # 帯と、背の結び目
    bx(m, "body", -4, 4, 10.5, 12, -3, 3.5, "obi", stripes(RAMP["obi"][1], RAMP["obi"][3], 8))
    bx(m, "body", -1.5, 1.5, 10, 12, -4, -3, "obi")
    # 胸 (着物。肩掛けの下からのぞく)
    bx(m, "body", -3, 3, 12, 16, -1.5, 3.5, "kimono", kimono(3.5))
    # 肩掛け: 背の丸み (二段)・脇・肩の上・前の V 字の板・背の裾のぎざぎざ
    bx(m, "shawl", -3, 3, 12, 17.5, -3, -1.5, "indigo", sd)                 # 背の盛り上がり
    bx(m, "shawl", -2.5, 2.5, 13.5, 17, -4, -3, "indigo", sd)               # 背の丸みの頂
    for s in (1, -1):
        side(m, "shawl", s, 3, 4, 12.5, 16, -1.5, 3.5, "indigo", sd)        # 脇
    bx(m, "shawl", -4, 4, 16, 17.5, -1.5, 4, "indigo", sd)                   # 肩の上
    vfront = ["".join("." if abs(c - 6.5) < 3.5 - r * 0.6 else "#" for c in range(14)) for r in range(5)]
    m.plate("shawl", -3.5, 16, 3.5, vfront, 0.5, "indigo", sd)              # 前の板 (喉元が V 字に開く)
    bx(m, "shawl", -0.5, 0.5, 13.5, 14, 4, 4.5, "indigo")                    # 前の結び目
    for x0, x1, y0 in [(-3, -2, 11), (-2, -1.5, 10.5), (1.5, 2, 10.5), (2, 3, 11)]:
        bx(m, "shawl", x0, x1, y0, 12, -3.5, -3, "indigo", sd)              # 背の裾 (帯の外側)
    # 首: 前へ出る
    bx(m, "body", -1, 1, 17.5, 18, 1.5, 3.5, "sk_old")
    # 頭: 肩より前へ突き出す。顔の彫り
    head_box = (-2.5, 2.5, 18, 23, 0, 5)
    hd = face_det(head_box, ST2_FACE[expr], ST2_COL)
    bx(m, "head", -2.5, 2.5, 18, 23, 0, 5, "sk_old", hd)
    bx(m, "head", -2, 2, 21.5, 22, 5, 5.5, "sk_old")                         # 眉の張り出し
    bx(m, "head", -0.5, 0.5, 19.5, 21, 5, 5.5, "sk_old")                     # 鼻
    bx(m, "head", -0.5, 0.5, 19.5, 20, 5.5, 6, "sk_old")                     # 鼻先 (鉤鼻)
    for s in (1, -1):
        side(m, "head", s, 1, 2, 19.5, 20, 5, 5.5, "sk_old")                # 頬骨
        side(m, "head", s, 2.5, 3, 19.5, 21, 1.5, 2.5, "sk_old")            # 耳
    # 髪: 頭頂 (生え際は顔より少し奥)・後ろ・耳の上を覆う横の髪、髷と簪
    bx(m, "hair", -2.5, 2.5, 23, 23.5, 0, 4.5, "hair_gray")
    bx(m, "hair", -3, 3, 19, 23.5, -0.5, 0, "hair_gray")
    for s in (1, -1):
        side(m, "hair", s, 2.5, 3, 21, 23, 0, 3.5, "hair_gray")
    bx(m, "hair", -1.5, 1.5, 23.5, 25, -0.5, 2, "hair_gray")                # 髷
    bx(m, "hair", -0.5, 0.5, 25, 25.5, 0, 1, "hair_gray")
    bx(m, "hair", 1.5, 3, 24, 24.5, 0.5, 1, "pin")                          # 簪
    # 腕: 肩から垂れる広い袖 (下に袂がたまる)、前へ曲げた袖口、帯の上で組んだ手
    for s, arm in ((1, "armL"), (-1, "armR")):
        side(m, arm, s, 4, 5.5, 13.5, 16.5, 0.5, 3.5, "kimono", kimono(99))  # 上の袖
        side(m, arm, s, 4, 5.5, 11, 13.5, 0.5, 3, "kimono", kimono(99))      # 袂 (垂れた袖の底)
        side(m, arm, s, 2.5, 5, 12, 13.5, 3.5, 5, "kimono", kimono(99))      # 袖口 (前へ曲げた前腕)
        side(m, arm, s, 0, 2.5, 12, 13, 4, 5, "sk_old")                     # 手 (真ん中で合わさる)
    # 結縄: 帯から垂れる縄に、結び目 3 つと房
    for y0, y1, knot in [(9.5, 10.5, False), (8.5, 9.5, True), (7.5, 8.5, False), (6.5, 7.5, True),
                         (5.5, 6.5, False), (4.5, 5.5, True), (3, 4.5, False)]:
        if knot:
            bx(m, "cord", 1.5, 3, y0, y1, 3.5, 4.5, "rope")
        else:
            bx(m, "cord", 2, 2.5, y0, y1, 3.5, 4, "rope")
    return m


BUILD = {"storyteller": storyteller, "whale_chaser": whale_chaser}


def _story(v, expr="neutral"):
    return storyteller_d(v, expr) if v == "d" else storyteller(v, expr)


BUILD["storyteller"] = _story
LABEL["storyteller"]["d"] = "A改 肩掛けの老婆 (採用)"
HEIGHT["storyteller"] = 26.0
ICON_YMIN["storyteller"] = 15.5
CHOSEN["storyteller"] = "d"


def build(npc, letter, expr="neutral"):
    m = BUILD[npc](letter, expr)
    geo, tex, gl = finish(m, HEIGHT[npc])
    return m, geo, tex, gl


# ---------------------------------------------------------------- 表示・顔アイコン

def face_icon(geo, tex, gl, ymin, yaw=18.0, pitch=5.0, px=ICON):
    """頭と肩の肖像を、背景を抜いた RGBA (px x px) にする。背景を二色で描き、変わらない画素だけを残す。"""
    faces = vx.load_geo(geo, tex, gl)
    keep = [f for f in faces if (f.origin + 0.5 * f.eu + 0.5 * f.ev)[1] >= ymin]
    dark = np.array(vx.render(keep, yaw=180 + yaw, pitch=pitch, size=(px, px), bg="dark", margin=0.06))
    light = np.array(vx.render(keep, yaw=180 + yaw, pitch=pitch, size=(px, px), bg="light", margin=0.06))
    mask = np.abs(dark.astype(int) - light.astype(int)).sum(-1) < 6
    ring = np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))) > 0
    ring &= ~mask
    out = np.zeros((px, px, 4), np.uint8)
    out[..., :3] = dark
    out[..., 3] = mask * 255
    out[ring] = (24, 20, 18, 255)
    return Image.fromarray(out, "RGBA")


def write_geo(path, geo):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def animations(npc, bones):
    """待機 (idle)・歩き (walk)・話す (talk)。角度は度 (GeckoLib の molang の sin は度)。
    GeckoLib はファイルの回転の X を反転して読む (ラフ座標で -X が「腕を前へ上げる」なので、ファイルには +X を書く)。
    左右対称に振れる動き (歩き・揺れ) は符号を取り違えても見た目が変わらないので、向きに意味があるのは話すときの腕だけ。
    頭の向きとうなずきはコード (NpcRenderer) が入れるので、ここでは頭を動かさない。bones にない骨の分は書かない。"""
    if npc == "storyteller":
        walk_leg, walk_arm, bob, raise_r, raise_l = 10, 2, 0.2, 14, 4
    else:
        walk_leg, walk_arm, bob, raise_r, raise_l = 20, 14, 0.4, 26, 3
    anims = {
        "animation.%s.idle" % npc: {
            "loop": True, "animation_length": 4.0, "bones": {
                "body": {"position": ["0", "math.sin(query.anim_time * 90) * 0.2", "0"]},
                "shawl": {"rotation": ["math.sin(query.anim_time * 90 + 40) * 0.8", "0", "0"]},
                "armL": {"rotation": ["math.sin(query.anim_time * 90 + 30) * 1.2", "0", "0"]},
                "armR": {"rotation": ["math.sin(query.anim_time * 90 + 90) * 1.2", "0", "0"]},
                "cord": {"rotation": ["0", "0", "math.sin(query.anim_time * 90) * 3"]},
                "pack": {"position": ["0", "math.sin(query.anim_time * 90 + 60) * 0.15", "0"]},
            }},
        "animation.%s.walk" % npc: {
            "loop": True, "animation_length": 1.2 if npc == "storyteller" else 1.0, "bones": {
                "legL": {"rotation": ["math.sin(query.anim_time * %d) * %d" % (300 if npc == "storyteller" else 360, walk_leg), "0", "0"]},
                "legR": {"rotation": ["-math.sin(query.anim_time * %d) * %d" % (300 if npc == "storyteller" else 360, walk_leg), "0", "0"]},
                "armL": {"rotation": ["-math.sin(query.anim_time * 360) * %d" % walk_arm, "0", "0"]},
                "armR": {"rotation": ["math.sin(query.anim_time * 360) * %d" % walk_arm, "0", "0"]},
                "body": {"position": ["0", "math.abs(math.sin(query.anim_time * 360)) * %s" % bob, "0"]},
                "cord": {"rotation": ["0", "0", "math.sin(query.anim_time * 600) * 8"]},
            }},
        "animation.%s.talk" % npc: {
            "loop": True, "animation_length": 3.0, "bones": {
                # 右手を少し前へ上げて、ゆっくり振る (ファイルの +X = 前へ)
                "armR": {"rotation": ["%d + math.sin(query.anim_time * 120) * 4" % raise_r, "0", "0"]},
                "armL": {"rotation": ["%d + math.sin(query.anim_time * 120 + 90) * 1.5" % raise_l, "0", "0"]},
                "body": {"position": ["0", "math.sin(query.anim_time * 120) * 0.15", "0"]},
                "shawl": {"rotation": ["math.sin(query.anim_time * 120 + 40) * 1", "0", "0"]},
            }},
    }
    for a in anims.values():
        a["bones"] = {k: v for k, v in a["bones"].items() if k in bones}
    return {"format_version": "1.8.0", "animations": anims}


def faces_sheet(icons, path):
    """顔アイコン 6 枚を 3 倍に拡大して並べる (行: 人、列: 表情)。"""
    s = 240
    pad = 24
    head = 40
    W_ = pad + (s + pad) * 3
    H_ = head + 2 * (s + pad + 30)
    out = Image.new("RGB", (W_, H_), (34, 32, 42))
    d = ImageDraw.Draw(out)
    names = {"storyteller": "語り部", "whale_chaser": "鯨追い"}
    ex = {"neutral": "ふつう", "smile": "笑み", "grave": "沈む"}
    d.text((pad, 10), "顔アイコン (80px を 3 倍で表示。左: ふつう、中: 笑み、右: 沈む)", font=vx.font(20), fill=(230, 230, 240))
    for r, npc in enumerate(NPCS):
        y = head + r * (s + pad + 30)
        d.text((pad, y), names[npc], font=vx.font(20), fill=(120, 220, 230))
        for c, e in enumerate(EXPRS):
            img = icons[(npc, e)].resize((s, s), Image.NEAREST)
            x = pad + c * (s + pad)
            bg = Image.new("RGBA", (s, s), (56, 52, 66, 255))
            bg.alpha_composite(img)
            out.paste(bg.convert("RGB"), (x, y + 30))
            d.text((x, y + 30 + s + 4), ex[e], font=vx.font(16), fill=(170, 170, 190))
    out.save(path)


def main(install=False):
    OUT.mkdir(parents=True, exist_ok=True)
    report = []
    for npc in NPCS:
        cols = []
        for letter in ("a", "b", "c", "d") if npc == "storyteller" else ("a", "b", "c"):
            m, geo, tex, gl = build(npc, letter)
            overlaps = vx.check_overlaps(geo)
            d = OUT / npc / letter
            write_geo(d / "geo.json", geo)
            d.mkdir(parents=True, exist_ok=True)
            Image.fromarray(tex, "RGBA").save(d / "texture.png")
            imgs = [W.view(geo, tex, gl, 25, 12, size=VIEW), W.view(geo, tex, gl, 90, 4, size=VIEW),
                    W.view(geo, tex, gl, 210, 14, size=VIEW), W.head_view(geo, tex, gl, None, yaw=20)]
            cols.append((LABEL[npc][letter], imgs))
            line = (f"{npc} {letter.upper()}: boxes {len(m.boxes)}, texture {tex.shape[1]}x{tex.shape[0]}, "
                    f"glow px 0, overlaps {overlaps if overlaps else 'none'}")
            report.append(line)
            print(line, flush=True)
        note = "行: 正面3/4・横・背面3/4・頭の近景。1 ブロック = 32px、0.5 単位の立方体。顔は色・形を見るための通常の色"
        title = "語り部 (storyteller) ラフ 3 案" if npc == "storyteller" else "鯨追い (whale_chaser) ラフ 3 案"
        vx.sheet(title, cols, note).save(OUT / f"{npc}_roughs.png")
    (OUT / "report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")

    icons = {}
    for npc in NPCS:
        letter = CHOSEN[npc]
        for e in EXPRS:
            _, geo, tex, gl = build(npc, letter, e)
            icons[(npc, e)] = face_icon(geo, tex, gl, ICON_YMIN[npc])
    faces_sheet(icons, OUT / "faces.png")

    if install:
        for npc in NPCS:
            letter = CHOSEN[npc]
            m, geo, tex, gl = build(npc, letter)
            assert not vx.check_overlaps(geo), npc
            write_geo(SRC / "geo" / "entity" / f"{npc}.geo.json", geo)
            (SRC / "textures" / "entity").mkdir(parents=True, exist_ok=True)
            Image.fromarray(tex, "RGBA").save(SRC / "textures" / "entity" / f"{npc}.png")
            bones = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
            (SRC / "animations" / "entity").mkdir(parents=True, exist_ok=True)
            (SRC / "animations" / "entity" / f"{npc}.animation.json").write_text(
                json.dumps(animations(npc, bones), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
            face_dir = SRC / "textures" / "gui" / "npc" / "face"
            face_dir.mkdir(parents=True, exist_ok=True)
            for e in EXPRS:
                icons[(npc, e)].save(face_dir / f"{npc}_{e}.png")
        print("installed", CHOSEN, flush=True)


if __name__ == "__main__":
    main(install="--install" in sys.argv)
