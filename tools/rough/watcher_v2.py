"""「見る者」(watcher) の見た目の作り直し (v2)。

v1 で採った B「留まる者の影」は、ゲームの夜の中では黒く細長い体と光る 2 つの目になり、バニラのエンダーマンに
そっくりだった。v2 では、エンダーマンと一目で分かれるシルエットを 3 案作り、B とエンダーマンを並べて比べる。

エンダーマンと分けるための決まり (3 案共通):
- 背丈は人に寄せる (2.0〜2.2 ブロック。エンダーマンは 2.9)
- 頭から裾までひと続きの布。腕と脚の隙間を見せない
- 目は横に並んだ 2 点にしない (縦の光 1 つ、または左右非対称)
- 色は黒ではなく、夜霧に溶ける青灰。布の縁だけが霧のようにごくかすかに光る (光るマスク)

- D「頭巾の留まる者」: 頭巾と外套。肩の高さが左右で違い、頭巾が前へ垂れる。頭巾の奥に縦の光が 1 つ、中心から外れて
- E「自分に似た影」: プレイヤーと同じ四角い頭が、長すぎる首の上に載る。肩から外套。片目だけが縦に光る
- F「喪の鐘」: 裾の広い鐘の形の外套。肩の線がなく、頭巾を深く垂れる。顔の割れ目に縦の光

座標は watcher.py と同じ (y が上、前は +z、0.5 単位、1 単位 = 2px)。重なりは置くたびに調べる。

実行 (リポジトリ直下):
    python tools/rough/watcher_v2.py            ラフと比較シートを docs/review/watcher_roughs/v2/ に書く
    python tools/rough/watcher_v2.py --install  さらに採用案 (CHOSEN) を src/main/resources に書き出す
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
import watcher as w1  # noqa: E402

ROOT = vx.ROOT
OUT = ROOT / "docs" / "review" / "watcher_roughs" / "v2"
SRC = w1.SRC
CHOSEN = "e"

RAMP = w1.RAMP
RAMP.update({
    # 夜霧の青灰。夜の光 (0.3 前後) の下で、地面の草と同じくらいに沈む明るさ
    "mist": ("#252D38", "#323C49", "#414C5A", "#53606F"),
    "mist_edge": ("#2C3542", "#3B4655", "#4C5868", "#606D7E"),
    "mist_pale": ("#56616E", "#6D7986", "#86929F", "#A2AEBA"),
    "hollow": ("#06070A", "#0A0C10", "#0E1116", "#13171D"),
    "skin": ("#3C434C", "#4F5761", "#646D78", "#7C8691"),
    "ender": ("#0B0B0E", "#141418", "#1C1C22", "#26262D"),
})
EYE_GLOW = "#D5E6F6"
MIST_GLOW = "#1B232C"   # 布の縁のかすかな光 (光るマスクに入れる色。加算なので暗い色でも夜空に浮く)
ENDER_EYE = "#D78CF0"


class Mdl(w1.Mdl):
    pass


def cloth(mat, folds=5, glow_below=None, glow_rim=None):
    """布のひだ。glow_below: この高さより下の面 (裾のほつれ) をかすかに光らせる。
    glow_rim: (x0, x1, y0, y1) の範囲の前面の縁を光らせる、など細かい指定は使わず、裾だけにする。"""
    base = w1.cloth(mat, folds)

    def det(X, Y, Z, f):
        if glow_below is not None and Y < glow_below and f not in ("up", "down"):
            # 下ほど淡く: ほつれた裾が霧に溶ける
            if w1._h(math.floor(X * 2), math.floor(Y * 2), math.floor(Z * 2)) % 3 != 0:
                return RAMP["mist_pale"][0]
        return base(X, Y, Z, f)

    def glow(X, Y, Z, f):
        return glow_below is not None and Y < glow_below and f not in ("up", "down") and \
            w1._h(math.floor(X * 2), math.floor(Y * 2), math.floor(Z * 2)) % 3 == 0
    return det, glow


RIM_GLOW = "#202935"


def rim(mat, folds=5):
    """肩や頭の上の面だけを、月明かりの縁のようにかすかに光らせる布。夜空を背にしたとき、肩の線と頭の形が浮く。"""
    base = w1.cloth(mat, folds)

    def det(X, Y, Z, f):
        return RIM_GLOW if f == "up" else base(X, Y, Z, f)

    def glow(X, Y, Z, f):
        return f == "up"
    return det, glow


def eye_slit(rects, z, color=EYE_GLOW):
    def det(X, Y, Z, f):
        if f == "south" and abs(Z - z) < 1e-6 and w1.v2.in_rects(X, Y, rects):
            return color
        return None

    def glow(X, Y, Z, f):
        return det(X, Y, Z, f) is not None
    return det, glow


def any_glow(*fns):
    def g(X, Y, Z, f):
        return any(fn and fn(X, Y, Z, f) for fn in fns)
    return g


def mist_glow_color(fn):
    """光る判定 fn の画素を、霧の光の色で塗る detail。"""
    def det(X, Y, Z, f):
        return MIST_GLOW if fn(X, Y, Z, f) else None
    return det


def ragged_hem(m, bone, x0, x1, z0, z1, y_top, bottoms, mat, det, glow):
    """裾のほつれ: 1 単位幅の列を並べ、列ごとに下端の高さを変える (正面から見て裾がぎざぎざ)。"""
    n = int(round(x1 - x0))
    for i in range(n):
        yb = bottoms[i % len(bottoms)]
        if yb < y_top:
            m.box(bone, x0 + i, yb, z0, x0 + i + 1, y_top, z1, mat, det, glow)


def wisps(m, bone, pts):
    for x, y, z in pts:
        m.box(bone, x, y, z, x + 0.5, y + 0.5, z + 0.5, "mist_pale", lambda *a: "#2E3946", lambda *a: True)


# ---------------------------------------------------------------- D: 頭巾の留まる者

def concept_d():
    m = Mdl("watcher")
    m.bone("body", (0, 14, 0))
    m.bone("hem", (0, 14, 0), "body")
    m.bone("scarf", (3, 26, 3), "body")
    m.bone("hand", (-3.25, 17, 3), "body")
    m.bone("head", (0, 27.5, 0.5), "body")
    m.bone("mist", (0, 30, 0), "body")
    M, E = "mist", "mist_edge"
    hem_det, hem_glow = cloth(M, 5, glow_below=3)
    hem_det = w1.combine(mist_glow_color(hem_glow), hem_det)
    c = w1.cloth(M, 5)
    ce = w1.cloth(E, 4)
    rd, rg = rim(E, 4)
    rm, rmg = rim(M, 5)

    # 裾 (鐘のように少し広がる)。いちばん下はほつれて霧に溶ける
    ragged_hem(m, "hem", -5.5, 5.5, -3.5, 3.5, 2, [0.5, 0, 1, 0.5, 1.5, 0, 0.5, 1, 0, 1.5, 0.5], M, hem_det, hem_glow)
    m.box("hem", -5.5, 2, -3.5, 5.5, 8, 3.5, M, hem_det, hem_glow)
    m.box("hem", -5, 8, -3, 5, 14, 3, M, c)
    # 胴: 少し前かがみ (上ほど前へ)
    m.box("body", -5, 14, -3, 5, 20, 3, M, c)
    m.box("body", -5, 20, -2.5, 5, 24, 3, M, c)
    # 肩: 左 (+x) が高く張り、右は落ちている
    m.box("body", -5, 24, -2.5, 5.5, 26, 3.5, E, rd, rg)
    m.box("body", 1.5, 26, -2, 5.5, 27.5, 3, E, rd, rg)    # 左の肩 (高い)
    m.box("body", -4.5, 26, -2, -1.5, 26.5, 3, E, rd, rg)  # 右の肩 (落ちる)
    m.box("body", -1.5, 26, -1.5, 1.5, 27.5, 2.5, M, c)    # 首もと
    # 左の肩から前へ垂れる、ほつれた布の端
    scarf = [24, 22, 26, 20]
    m.plate("scarf", 2, 24, 3, w1.ragged(4, 26, lambda i: scarf[i]), 0.5, E, ce)
    # 右の袖口から、細い指の手が一本だけのぞく
    m.box("hand", -4.5, 15, 3, -2, 17, 4, E, ce)
    m.box("hand", -4, 13, 3, -2.5, 15, 3.5, "skin")
    m.box("hand", -4, 11, 3, -3.5, 13, 3.5, "skin")
    m.box("hand", -3, 11.5, 3, -2.5, 13, 3.5, "skin")
    # 頭巾: 前へ垂れ、奥は暗い。光は縦に一つ、中心から外れて
    eye_rects = [(0.5, 1.0, 29.5, 31.5)]
    e_det, e_glow = eye_slit(eye_rects, 2)
    m.box("head", -3.5, 27.5, -2.5, 3.5, 33.5, 1.5, M, c)                   # 頭巾の後ろ
    m.box("head", -2, 28, 1.5, 2, 32.5, 2, "hollow", e_det, e_glow)         # 顔の闇 (光る切れ目)
    m.box("head", -2, 27.5, 1.5, 2, 28, 3, E, ce)                          # あごの下のたるみ
    m.box("head", 2, 27.5, 1.5, 3.5, 33.5, 4, E, ce)                        # 頭巾の縁 (左)
    m.box("head", -3.5, 27.5, 1.5, -2, 33, 4.5, E, ce)                      # 頭巾の縁 (右。少し長く垂れる)
    m.box("head", -2, 32.5, 1.5, 2, 33.5, 4.5, E, rd, rg)                   # ひさし
    m.box("head", -3, 33.5, -2.5, 3, 35, 3, M, rm, rmg)                     # 頭頂
    m.box("head", -1.5, 35, -2.5, 1.5, 35.5, 1, M, rm, rmg)                 # 頭巾の先 (後ろへ)
    m.box("head", -2, 33.5, -3.5, 2, 34.5, -2.5, M)                         # 後ろへ垂れる先
    m.bone("eyes", (0.75, 30.5, 2.05), "head")
    wisps(m, "mist", [(4.5, 28.5, 0), (-3, 36.5, -1), (2, 37.5, 0.5), (-5, 27.5, 1), (0.5, 39, -1.5)])
    pose = {"head": (8, 0, -6)}
    return m, pose


# ---------------------------------------------------------------- E: 自分に似た影

def concept_e():
    m = Mdl("watcher")
    m.bone("body", (0, 12, 0))
    m.bone("hem", (0, 12, 0), "body")
    m.bone("mantle", (0, 24, 0), "body")
    # 頭の支点は x=0 に置く (支点を横にずらすと、ゲームの目の光の位置が支点のずれの 2 倍だけ横にずれた)
    m.bone("head", (0, 27, 0), "body")
    m.bone("mist", (0, 30, 0), "body")
    M, E = "mist", "mist_edge"
    rd, rg = rim(E, 4)
    hem_det, hem_glow = cloth(M, 5, glow_below=2.5)
    hem_det = w1.combine(mist_glow_color(hem_glow), hem_det)
    c = w1.cloth(M, 5)
    ce = w1.cloth(E, 4)
    # 長い上着: 脚の形は見せない (足もとまでひと続き)
    ragged_hem(m, "hem", -5, 5, -3, 3, 1.5, [0.5, 0, 1, 0, 0.5, 1, 0, 0.5, 0, 1], M, hem_det, hem_glow)
    m.box("hem", -5, 1.5, -3, 5, 6, 3, M, hem_det, hem_glow)
    m.box("hem", -4.5, 6, -2.5, 4.5, 12, 2.5, M, c)
    m.box("body", -4, 12, -2, 4, 16, 2, M, c)               # 腰 (細い)
    m.box("body", -4.5, 16, -2.5, 4.5, 22, 2.5, M, c)
    m.box("body", -6.5, 22, -2.5, 6.5, 24, 2.5, E, rd, rg)  # 四角い肩 (プレイヤーの肩幅)
    # 長すぎる首。少しずつ横へずれて、頭が肩の真ん中から外れる (細い棒だと人形に見えたので、太さを人に寄せた)
    m.box("body", -1.5, 24, -1.5, 1.5, 25.5, 1.5, M, c)
    m.box("body", -1, 25.5, -1.5, 2, 27, 1.5, M, c)
    # えり: 外套が首の付け根を巻く
    m.box("mantle", -3, 24, 1.5, 3, 25, 2.5, E, rd, rg)
    m.box("mantle", -3, 24, -2.5, 3, 25, -1.5, E, rd, rg)
    m.box("mantle", 1.5, 24, -1.5, 3, 25, 1.5, E, rd, rg)
    m.box("mantle", -3, 24, -1.5, -1.5, 25, 1.5, E, rd, rg)
    # 背の外套: 肩から腿まで、裾はほつれる
    back = [27, 31, 28, 32, 29, 26, 32, 30, 27, 31, 26, 30, 28, 32, 27, 29, 31, 26, 28, 30, 27, 32, 29, 26]
    m.plate("mantle", -6, 22, -3, w1.ragged(24, 36, lambda i: back[i]), 0.5, E, ce)
    # 肩から外套: 腕を覆う。手は見せない。左は肘のあたりで終わり、右だけ腿まで長く垂れる (左右で腕の長さが違って見える)
    left = [16, 18, 15, 17, 14, 18, 16, 15]
    right = [24, 26, 23, 25, 22, 26, 24, 23]
    for side in (1, -1):
        x0 = 4.5 if side > 0 else -6.5
        depth = left if side > 0 else right
        rows = w1.ragged(4, 26, lambda i, _d=depth: _d[i * 2 % 8])
        # 外套の袖 (腕を包む板)。体の横に、体と面で接する
        m.plate("mantle", x0, 22, -2.5, rows, 5, E, ce)
    # 四角い頭 (7 単位。プレイヤーより少し小さい)。片目だけが縦に光る
    # 顔はのっぺりとして、左の目の位置にだけ縦の光がある (もう片方の目も口もない)。
    # 光る目は eyes 骨の小さな立方体 (顔の面からわずかに浮く)。ゲームの目の光のにじみは、この立方体の真ん中に置く
    hd, hg = rim(M, 7)
    m.box("head", -2.5, 27, -3.5, 4.5, 34, 3.5, M, hd, hg)
    m.bone("eyes", (2.75, 30.75, 3.75), "head")
    m.box("eyes", 2.5, 30, 3.5, 3, 31.5, 4, "eye", lambda *a: EYE_GLOW, lambda *a: True)
    wisps(m, "mist", [(5, 25, 0), (-4, 34.5, -1), (2.5, 36, 0.5), (-6, 26, 1)])
    pose = {"head": (4, 0, -12)}
    return m, pose


# ---------------------------------------------------------------- F: 喪の鐘

def concept_f():
    m = Mdl("watcher")
    m.bone("body", (0, 14, 0))
    m.bone("hem", (0, 14, 0), "body")
    m.bone("head", (0, 26, 0.5), "body")
    m.bone("mist", (0, 30, 0), "body")
    M, E = "mist", "mist_edge"
    hem_det, hem_glow = cloth(M, 5, glow_below=3)
    hem_det = w1.combine(mist_glow_color(hem_glow), hem_det)
    c = w1.cloth(M, 5)
    ce = w1.cloth(E, 4)
    ragged_hem(m, "hem", -6.5, 6.5, -4.5, 4.5, 2, [0, 1, 0.5, 0, 1.5, 0.5, 0, 1, 0, 0.5, 1.5, 0, 0.5], M, hem_det, hem_glow)
    m.box("hem", -6.5, 2, -4.5, 6.5, 6, 4.5, M, hem_det, hem_glow)
    m.box("hem", -5.5, 6, -4, 5.5, 14, 4, M, c)
    m.box("body", -4.5, 14, -3.5, 4.5, 21, 3.5, M, c)
    rd, rg = rim(E, 4)
    m.box("body", -3.5, 21, -3, 3.5, 26, 3, E, rd, rg)   # なで下ろした肩 (肩の線がない)
    # 深く垂れた頭巾: 前へ突き出し、顔は下を向く
    eye_rects = [(-0.25 - 0.25, 0.0, 27, 30)]
    e_det, e_glow = eye_slit(eye_rects, 4)
    m.box("head", -3, 26, -2, 3, 31, 3, M, c)
    m.box("head", -1.5, 26.5, 3, 1.5, 30, 3.5, "hollow")
    m.box("head", -1.5, 26, 3.5, 1.5, 30.5, 4, "hollow", e_det, e_glow)
    m.box("head", 1.5, 26, 3, 3, 31, 5, E, ce)
    m.box("head", -3, 26, 3, -1.5, 31, 5, E, ce)
    m.box("head", -1.5, 30.5, 3.5, 1.5, 31, 5, E, ce)
    m.box("head", -1.5, 30, 3, 1.5, 30.5, 3.5, E, ce)
    m.box("head", -2.5, 31, -2, 2.5, 32, 4, M, *rim(M))
    m.bone("eyes", (-0.25, 28.5, 4.05), "head")
    wisps(m, "mist", [(4, 27, 0), (-3.5, 33, 0), (1, 34.5, -1)])
    pose = {"head": (14, 0, 0)}
    return m, pose


# ---------------------------------------------------------------- 比べる相手: エンダーマン (バニラの寸法の近似)

def enderman():
    m = Mdl("enderman")
    m.bone("body", (0, 26, 0))
    m.bone("head", (0, 38, 0), "body")
    K = "ender"
    m.box("body", 1, 0, -1, 3, 26, 1, K)
    m.box("body", -3, 0, -1, -1, 26, 1, K)
    m.box("body", -4, 26, -2, 4, 38, 2, K)
    m.box("body", 4, 8, -1, 6, 38, 1, K)
    m.box("body", -6, 8, -1, -4, 38, 1, K)
    e_det, e_glow = eye_slit([(-3, -0.5, 41, 41.5), (0.5, 3, 41, 41.5)], 4, ENDER_EYE)
    m.box("head", -4, 38, -4, 4, 46, 4, K, e_det, e_glow)
    return m, {}


# ---------------------------------------------------------------- 一覧

CONCEPTS = {
    "b": ("B 留まる者の影 (v1)", w1.concept_b, "v1 の採用案。夜のゲーム画面ではエンダーマンに見えた"),
    "d": ("D 頭巾の留まる者", concept_d, "頭巾と外套。肩の高さが左右で違い、頭巾が前へ垂れる。奥に縦の光が一つ"),
    "e": ("E 自分に似た影", concept_e, "プレイヤーと同じ四角い頭が長すぎる首に載る。片目だけ縦に光る"),
    "f": ("F 喪の鐘", concept_f, "裾の広い鐘の形。肩の線がなく、頭巾を深く垂れる。顔の割れ目に縦の光"),
    "x": ("エンダーマン (比較)", enderman, "バニラのエンダーマン (寸法の近似、2.9 ブロック)"),
}
# 目の光のにじみ (ゲームの WatcherRenderer と同じ考え): 目の位置ごとに (横の半径, 縦の半径) の倍率
GLINTS = {
    "b": ([(-1.0, 37.25, 2.55), (1.0, 37.25, 2.55)], (1.0, 1.0)),
    "d": ([(0.75, 30.5, 2.05)], (0.6, 1.8)),
    "e": ([(2.75, 30.75, 4.05)], (0.6, 1.8)),
    "f": ([(-0.25, 28.5, 4.05)], (0.6, 1.8)),
    "x": ([], (1, 1)),
}


HAZE = (12, 15, 20)   # 布のごくかすかな光 (夜霧が月明かりをはらんだ程度)。真っ暗な夜空でも人の形がうっすら残る


def build(letter):
    label, fn, _ = CONCEPTS[letter]
    m, pose = fn()
    geo, tex, gl = w1.finish_geo(m)
    if letter in ("d", "e", "f"):
        # 光っていない布の画素に、霧のかすかな光を一様に足す (暗い穴は除く)。
        # 明るい画素だけに足すと、ひだのまだらが点々と光って見えたので一様にした
        cloth_px = (tex[..., 3] > 0) & (gl[..., 3] == 0) & (tex[..., :3].astype(int).sum(-1) > 90)
        gl[cloth_px] = (*HAZE, 255)
    return m, geo, tex, gl, pose


# ---------------------------------------------------------------- 描画

FRAME = (-25.0, 25.0, 0.0, 50.0)  # 縮尺をそろえるための透明な枠 (x0, x1, y0, y1)


def framed(faces):
    x0, x1, y0, y1 = FRAME
    clear = np.zeros((1, 1, 4), np.uint8)
    f = vx.Face(np.array([x0, y0, -30.0]), np.array([x1 - x0, 0, 0.0]), np.array([0, y1 - y0, 0.0]), clear, None, 1.0)
    return faces + [f]


def eyes_only(gl):
    """比較シートでは、目のような明るい光だけをにじませる (霧のかすかな光までにじませると、全身が光って見えて誤解を招く)。"""
    g = gl.copy()
    g[g[..., :3].astype(int).sum(-1) < 300] = 0
    return g


def view(geo, tex, gl, pose, yaw, pitch=4, size=(300, 300), gain=1.0, bg="dark"):
    gl = eyes_only(gl)
    t = tex.copy()
    t[..., :3] = np.clip(t[..., :3].astype(float) * gain, 0, 255).astype(np.uint8)
    F = framed(vx.load_geo(geo, t, gl, pose=pose))
    return vx.render(F, yaw=yaw + 180, pitch=pitch, size=size, margin=0.02, bg=bg)


def head_view(geo, tex, gl, pose, head_bottom, size=(260, 260)):
    F = vx.load_geo(geo, tex, eyes_only(gl), pose=pose)
    keep = [f for f in F if (f.origin + 0.5 * f.eu + 0.5 * f.ev)[1] >= head_bottom - 1.5]
    return vx.render(keep, yaw=180 + 18, pitch=6, size=size, margin=0.12)


def layers(geo, tex, gl, pose, px_per_unit):
    """正面 (pitch 0) から描いた (色, 形のマスク, 光る画素の色)。1 単位 = px_per_unit 画素、FRAME の大きさ。"""
    x0, x1, y0, y1 = FRAME
    W, H = int(round((x1 - x0) * px_per_unit)), int(round((y1 - y0) * px_per_unit))
    F = framed(vx.load_geo(geo, tex, gl, pose=pose))
    kw = dict(yaw=180, pitch=0, size=(W, H), margin=0.0, bloom=False, outline=False)
    on_dark = np.array(vx.render(F, bg="dark", **kw)).astype(float)
    on_light = np.array(vx.render(F, bg="light", **kw)).astype(float)
    mask = (np.abs(on_dark - on_light).sum(-1) < 6).astype(float)
    black = tex.copy()
    black[..., :3] = 0
    lit = gl[..., 3] > 0
    black[lit, :3] = gl[lit, :3]
    gonly = np.array(vx.render(framed(vx.load_geo(geo, black, gl, pose=pose)), bg="dark", **kw)).astype(float)
    gonly[mask == 0] = 0
    gonly[gonly.sum(-1) < 25] = 0
    return on_dark, mask, gonly


NIGHT_LIGHT = np.array([0.26, 0.29, 0.36])   # 真夜中の空の光で照らした体の明るさ (ゲーム内の草の暗さに合わせた)
SKY_TOP, SKY_LOW = np.array([3, 4, 7.0]), np.array([8, 10, 16.0])
GROUND = (10, 13, 10)


def night_row(letters, blocks_away, scene=(420, 150), title=""):
    """letters の見る者を、同じ稜線に並べ、blocks_away ブロック先から見た大きさで描く (1080p・視野 70 度の画素)。
    体は夜の光で暗くし、光る画素は加算、目の光のにじみを重ねる。最後に 2 倍に拡大。"""
    W, H = scene
    px_unit = 1080 / (2 * math.tan(math.radians(35))) / 16 / blocks_away
    yy = np.linspace(0, 1, H)[:, None, None]
    img = (SKY_TOP * (1 - yy) + SKY_LOW * yy) * np.ones((1, W, 1))
    rng = np.random.default_rng(7)
    for _ in range(30):
        img[rng.integers(0, H // 2), rng.integers(0, W)] = (70, 74, 86)
    ridge = int(H * 0.8)
    img[ridge:] = GROUND
    add = np.zeros_like(img)
    big = 12.0
    k = px_unit / big
    n = len(letters)
    for i, letter in enumerate(letters):
        _, geo, tex, gl, pose = build(letter)
        col, mask, gonly = layers(geo, tex, gl, pose, big)
        hh, ww = mask.shape
        nw, nh = max(1, int(round(ww * k))), max(1, int(round(hh * k)))
        def rs(a):
            return np.array(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).resize((nw, nh), Image.LANCZOS)).astype(float)
        c = rs(col * NIGHT_LIGHT)
        mk = rs(mask * 255)[..., None] / 255 if mask.ndim == 2 else rs(mask) / 255
        g = rs(gonly)
        cx = int(W * (i + 0.5) / n)
        top = ridge - nh
        left = cx - nw // 2
        sl = (slice(max(top, 0), top + nh), slice(max(left, 0), min(left + nw, W)))
        oy, ox = sl[0].start - top, sl[1].start - left
        hh2, ww2 = sl[0].stop - sl[0].start, sl[1].stop - sl[1].start
        mk = mk[oy:oy + hh2, ox:ox + ww2]
        img[sl] = img[sl] * (1 - mk) + c[oy:oy + hh2, ox:ox + ww2] * mk
        add[sl] += g[oy:oy + hh2, ox:ox + ww2]
        # 目の光のにじみ: 半径 = 距離 × 0.0032 ブロック (WatcherRenderer.GLINT_SCALE)
        pts, (ax, ay) = GLINTS[letter]
        r_blocks = max(0.03, blocks_away * 0.0032)
        r_px = r_blocks * 16 * px_unit
        x0f, _, y0f, y1f = FRAME
        for ex, ey, _ez in pts:
            sx = left + (ex - x0f) * px_unit
            sy = top + (y1f - ey) * px_unit
            gimg = Image.new("L", (W, H), 0)
            ImageDraw.Draw(gimg).ellipse((sx - r_px * ax, sy - r_px * ay, sx + r_px * ax, sy + r_px * ay), fill=255)
            gimg = gimg.filter(ImageFilter.GaussianBlur(max(0.6, r_px * 0.45)))
            ga = np.array(gimg).astype(float)[..., None] / 255
            add += ga * np.array([150, 165, 185.0])
    out = Image.fromarray(np.clip(img + add, 0, 255).astype(np.uint8)).resize((W * 2, H * 2), Image.NEAREST)
    d = ImageDraw.Draw(out)
    d.text((10, 6), title or f"{blocks_away} ブロック先", font=vx.font(18), fill=(190, 195, 210))
    for i, letter in enumerate(letters):
        cx = int(W * (i + 0.5) / n) * 2
        lab = CONCEPTS[letter][0].split(" ")[0]
        d.text((cx - 8, H * 2 - 26), lab if letter != "x" else "エ", font=vx.font(16), fill=(150, 155, 170))
    return out


# ---------------------------------------------------------------- 書き出し

def animations(bone_names):
    """待機だけ。呼吸のような揺れ、裾と垂れ布のゆらぎ、霧のかけらの上下。頭の向きと傾きはコードで動かす。"""
    bones = {
        "body": {"rotation": ["math.sin(query.anim_time * 60) * 0.7", "0", "math.sin(query.anim_time * 30) * 0.5"]},
        "hem": {"rotation": ["math.sin(query.anim_time * 60 + 70) * 0.6", "0", "math.sin(query.anim_time * 60 + 20) * 0.8"]},
        "scarf": {"rotation": ["math.sin(query.anim_time * 120) * 1.5", "0", "math.sin(query.anim_time * 60 + 30) * 1.0"]},
        "mantle": {"rotation": ["math.sin(query.anim_time * 60 + 40) * 0.8", "0", "0"]},
        "hand": {"rotation": ["math.sin(query.anim_time * 180) * 1.2", "0", "0"]},
        "mist": {"position": ["0", "math.sin(query.anim_time * 60) * 0.5", "0"]},
    }
    return {"format_version": "1.8.0", "animations": {"animation.watcher.idle": {
        "loop": True, "animation_length": 6.0, "bones": {k: v for k, v in bones.items() if k in bone_names}}}}


def head_bottom(geo):
    g = geo["minecraft:geometry"][0]
    return [b for b in g["bones"] if b["name"] == "head"][0]["pivot"][1]


def main(install=False):
    OUT.mkdir(parents=True, exist_ok=True)
    cols, report = [], []
    for letter in ("b", "d", "e", "f", "x"):
        m, geo, tex, gl, pose = build(letter)
        label = CONCEPTS[letter][0]
        overlaps = vx.check_overlaps(geo)
        ys = [c["origin"][1] + c["size"][1] for b in geo["minecraft:geometry"][0]["bones"] for c in b.get("cubes", [])
              if b["name"] not in ("ash", "mist")]
        report.append(f"{label}: boxes {len(m.boxes)} height {max(ys) / 16:.2f} blocks texture {tex.shape[1]}x{tex.shape[0]} "
                      f"glow px {(gl[..., 3] > 0).sum()} overlaps {overlaps if overlaps else 'none'}")
        print(report[-1], flush=True)
        if letter in ("d", "e", "f"):
            d = OUT / letter
            d.mkdir(parents=True, exist_ok=True)
            (d / "geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
            Image.fromarray(tex, "RGBA").save(d / "texture.png")
            Image.fromarray(gl, "RGBA").save(d / "glowmask.png")
        imgs = [view(geo, tex, gl, pose, 25, gain=2.0), view(geo, tex, gl, pose, 90, gain=2.0),
                view(geo, tex, gl, pose, 0, gain=1.0)]
        imgs.append(head_view(geo, tex, gl, pose, head_bottom(geo)) if letter != "x" else
                    Image.new("RGB", (260, 260), (18, 18, 26)))
        cols.append((label, imgs))
    note = ("行: 正面3/4・横 (確認用に明るさ ×2)・正面 (本来の色)・頭の近景。5 列とも同じ縮尺 "
            "(1 ブロック = 32px、0.5 単位の立方体)")
    vx.sheet("見る者 v2: エンダーマンと分ける 3 案 (D・E・F) と v1 の B", cols, note).save(OUT / "watcher_v2_roughs.png")
    rows = [night_row(["b", "d", "e", "f", "x"], 60, title="60 ブロック先 (1080p・視野 70 度、真夜中の空)"),
            night_row(["b", "d", "e", "f", "x"], 40, title="40 ブロック先"),
            night_row(["b", "d", "e", "f", "x"], 12, scene=(420, 260), title="12 ブロック先 (近く)")]
    wdt = max(r.width for r in rows) + 24
    hgt = sum(r.height + 16 for r in rows) + 60
    sheet = Image.new("RGB", (wdt, hgt), (18, 18, 26))
    ImageDraw.Draw(sheet).text((12, 10), "夜の見え方: 左から B (v1)・D・E・F・エンダーマン (目の光のにじみ込み)",
                               font=vx.font(18), fill=(230, 230, 240))
    y = 48
    for r in rows:
        sheet.paste(r, (12, y))
        y += r.height + 16
    sheet.save(OUT / "watcher_v2_night.png")
    (OUT / "report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    if install:
        do_install()


def do_install(letter=CHOSEN):
    m, geo, tex, gl, pose = build(letter)
    assert not vx.check_overlaps(geo)
    assert (gl[..., 3] > 0).any(), "glowmask has no glowing pixel"
    (SRC / "geo" / "entity" / "watcher.geo.json").write_text(json.dumps(geo, ensure_ascii=False), encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(SRC / "textures" / "entity" / "watcher.png")
    Image.fromarray(gl, "RGBA").save(SRC / "textures" / "entity" / "watcher_glowmask.png")
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    (SRC / "animations" / "entity" / "watcher.animation.json").write_text(
        json.dumps(animations(names), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    # 目の光のにじみ: 芯を広めにした丸 (ゲームでは縦長の板に貼るので、縦長の光になる)
    glint = np.zeros((16, 16, 4), np.uint8)
    yy, xx = np.mgrid[0:16, 0:16]
    a = np.clip(1 - np.hypot(xx - 7.5, yy - 7.5) / 7.5, 0, 1) ** 1.4
    for i, v in enumerate((196, 214, 236, 255)):
        glint[..., i] = (v * a).astype(np.uint8)
    Image.fromarray(glint, "RGBA").save(SRC / "textures" / "entity" / "watcher_glint.png")
    print("installed", letter, "boxes", len(m.boxes), flush=True)


if __name__ == "__main__":
    main(install="--install" in sys.argv)
