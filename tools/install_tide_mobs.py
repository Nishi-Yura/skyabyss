"""潮殻の背の敵 3 種 (珊瑚憑き・磯走り・泡吹き) と珊瑚の銛を、ゲームの素材として書き出す。

使い方: リポジトリ直下で  PYTHONIOENCODING=utf-8 python tools/install_tide_mobs.py [--eyes original]
出力:
  src/main/resources/assets/skyabyss/geo/entity/{coral_thrall,reef_scuttler,bubble_puffer}.geo.json
  src/main/resources/assets/skyabyss/textures/entity/<名前>{,_glowmask}.png (珊瑚憑きは像の姿の _statue も)
  src/main/resources/assets/skyabyss/animations/entity/<名前>.animation.json
  src/main/resources/assets/skyabyss/{geo,textures,animations}/gear/coral_spear{,_statue}.*  (銛。受け口 socket_main に付く)
  docs/review/tide_shell_roughs/bubble_puffer_eyes.png  (泡吹きの目の、調整の前と後)
  docs/review/previews/tide_mobs/*.png  (確認用の絵。git には入れない)

形の元は rough/tide_mobs_v2.py の A 案 (ユーザーが選んだもの: 珊瑚憑き A、磯走り A、泡吹き A)。次を変えた:
- 珊瑚憑き: 頭を 0.5 単位で彫り直した (丸い頭蓋、細い下顎、くぼんだ眼窩、眉の段)。v2 の頭は箱が 3 つ重なった形だった。
  像の姿のときだけ見せる骨 statue_coral (体じゅうに生えた珊瑚) を足し、像のテクスチャ (_statue) は白く塗る。
  銛は水平に構える (前腕と受け口の骨の初めの角度)。突きで前へ出る。
- 泡吹き: 目を小さく、濁った白に、縦に細い瞳孔、左右へ離して付けた (「かわいく見えすぎる」の対策)。
  --eyes original で v2 の目 (大きく黒目がち、白い光) に戻せる。
  ふくらみは別モデルにせず、体の骨の拡大と、棘の骨 spines の出し入れで表す (08 の 14.3節)。

座標: ラフと同じ (前が +z、y が上、1 単位 = 1/16 ブロック)。書き出す geo は root 骨を yaw 180 度回す。
角度: ANIM と骨の初めの角度は「意図した向き」(ラフの絵で見える向き) で書き、ファイルには (-x, +y, -z) で書く
(GeckoLib は X と Y の回転の符号を反転し、モデルを X で鏡に写す。tools/README と install_tide_shell.py と同じ)。
位置 (position) はそのまま書く (X は GeckoLib の反転と鏡写しで打ち消し合う)。
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "rough"))
import voxel as vx  # noqa: E402
import tide_mobs as tm  # noqa: E402
import tide_mobs_v2 as v2  # noqa: E402

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/tide_mobs"
EYES_PNG = ROOT / "docs/review/tide_shell_roughs/bubble_puffer_eyes.png"
SIGN = (-1, 1, -1)

# 泡吹きの目と、珊瑚憑きの眼窩の色
tm.RAMP["eye"] = ("#4A524C", "#7C8679", "#A9B2A3", "#C9D0C0")
MILK = "#C3CBBC"       # 濁った白
MILK_DIM = "#9AA595"   # 白濁の縁
PUPIL = "#120F16"      # 細い瞳孔


# ---------------------------------------------------------------- 共通

def prune_same_bone(m):
    """0.5 の格子で、6 方向すべてが「同じ骨」の立方体で塞がった立方体を外して、箱に組み直す。
    v2.prune は骨をまたいで塞がりを数えるので、動く骨 (腕・棘) の下に穴が開く。ここでは同じ骨の中だけで数える。"""
    cell = {}
    for bx in m.boxes:
        x0, y0, z0, x1, y1, z1 = bx["b"]
        for i in range(round(x0 * 2), round(x1 * 2)):
            for j in range(round(y0 * 2), round(y1 * 2)):
                for k in range(round(z0 * 2), round(z1 * 2)):
                    cell[(i, j, k)] = (bx["bone"], bx["mat"])
    dirs = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    while True:
        dead = [q for q, (bone, _) in cell.items()
                if all(cell.get((q[0] + a, q[1] + b, q[2] + c), (None,))[0] == bone for a, b, c in dirs)]
        if not dead:
            break
        for q in dead:
            del cell[q]
    m.boxes = []
    groups = {}
    for q, bm in cell.items():
        groups.setdefault(bm, []).append(q)
    for (bone, mat), qs in sorted(groups.items(), key=lambda kv: (kv[0][0], kv[0][1])):
        left = set(qs)
        for q in sorted(qs, key=lambda t: (t[1], t[2], t[0])):
            if q not in left:
                continue
            ex = 1
            while (q[0] + ex, q[1], q[2]) in left:
                ex += 1
            ey = 1
            while all((q[0] + a, q[1] + ey, q[2]) in left for a in range(ex)):
                ey += 1
            ez = 1
            while all((q[0] + a, q[1] + b, q[2] + ez) in left for a in range(ex) for b in range(ey)):
                ez += 1
            for a in range(ex):
                for b in range(ey):
                    for c in range(ez):
                        left.discard((q[0] + a, q[1] + b, q[2] + c))
            m.box(bone, q[0] / 2, q[1] / 2, q[2] / 2, (q[0] + ex) / 2, (q[1] + ey) / 2, (q[2] + ez) / 2, mat)


def finish(m, rest=None):
    """面の絵を塗って geo を作る。rest = {骨: 意図した初めの角度}。
    戻り値: game (ファイルに書く geo。符号を直した角度)、view (確認の絵用の geo。意図した角度)、tex、gl"""
    prune_same_bone(m)
    geo, tex, gl = m.finish()
    over = vx.check_overlaps(geo)
    assert not over, (m.ident, over[:5])
    assert gl[..., 3].any(), f"光る画素がない: {m.ident}"
    view = tm.to_game(copy.deepcopy(geo))
    game = tm.to_game(copy.deepcopy(geo))
    for g, signed in ((view, False), (game, True)):
        for b in g["minecraft:geometry"][0]["bones"]:
            if rest and b["name"] in rest:
                r = rest[b["name"]]
                b["rotation"] = [float(r[i] * (SIGN[i] if signed else 1)) for i in range(3)]
    return dict(game=game, view=view, tex=tex, gl=gl, n=len(m.boxes))


def write_model(name, r, folder="entity", ident=None, tex=None, gl=None):
    geo = copy.deepcopy(r["game"])
    if ident:
        geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + ident
    (ASSETS / "geo" / folder).mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures" / folder).mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo" / folder / f"{name}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(r["tex"] if tex is None else tex, "RGBA").save(ASSETS / "textures" / folder / f"{name}.png")
    g = r["gl"] if gl is None else gl
    assert g[..., 3].any(), f"光る画素がない: {name}"
    Image.fromarray(g, "RGBA").save(ASSETS / "textures" / folder / f"{name}_glowmask.png")


# ---------------------------------------------------------------- 珊瑚憑き (A、頭を彫り直し)

FZ = 2.5                                  # 顔の前の面
SOCKETS = ((-2.0, -0.5), (0.5, 2.0))       # 眼窩の x の範囲
SOCK_Y = (27.5, 29.0)
SOCK_GLOW = ((-1.5, -1.0), (1.0, 1.5))     # 眼窩の奥の、ほのかな光 (1 画素)
THRALL_REST = {"forearm_r": (-70, 0, 0), "socket_main": (150, 0, 0)}


def thrall_extra(X, Y, Z, f, mat):
    # 眼窩 (彫ったくぼみ) の底と壁は暗く、奥に海硝子の光を 1 画素
    if mat in ("bone", "bone_dk") and FZ - 0.5 - 1e-6 <= Z <= FZ + 1e-6 and SOCK_Y[0] - 1e-6 <= Y <= SOCK_Y[1] + 1e-6:
        for a, b in SOCKETS:
            if a - 1e-6 <= X <= b + 1e-6:
                if f == "south" and abs(Z - (FZ - 0.5)) < 1e-6:
                    if any(c <= X <= d for c, d in SOCK_GLOW) and 28.0 <= Y <= 28.5:
                        return tm.RAMP["seaglass"][1]
                    return tm.RAMP["dark"][0]
                if f != "south":
                    return tm.RAMP["dark"][1]
    if f == "south" and abs(Z - FZ) < 1e-6:
        if -0.5 <= X <= 0.5 and 27.0 <= Y <= 27.5:
            return tm.RAMP["dark"][0]                     # 鼻の穴
    if mat == "bone_dk" and f == "south" and 25.5 <= Y <= 26.5 and -1.25 <= X <= 1.25:
        # 下顎の歯 (骨の色と暗いすき間を交互に)
        return tm.RAMP["bone"][3] if (math.floor(X * 2) % 2 == 0 and Y > 26.0) else tm.RAMP["dark"][0]
    return None


def thrall_glow(X, Y, Z, f, mat):
    return (mat in ("bone", "bone_dk") and f == "south" and abs(Z - (FZ - 0.5)) < 1e-6
            and any(c <= X <= d for c, d in SOCK_GLOW) and 28.0 <= Y <= 28.5)


def thrall_head(m):
    """丸い頭蓋と細い下顎。眼窩は 0.5 の深さで彫る (絵で描くのでなく、本当にくぼませる)。"""
    def socket(x, y, z):
        return z > FZ - 0.5 and SOCK_Y[0] < y < SOCK_Y[1] and any(a < x < b for a, b in SOCKETS)

    # 頭蓋: 少し右へ傾いた楕円体 (後ろが膨らむ)
    cran = v2.ellip((0.25, 31.0, -0.25), (3.1, 2.7, 3.0))
    v2.fill(m, "head", lambda x, y, z: cran(x, y, z) and y > 28.5 and not socket(x, y, z), "bone",
            (-3.5, 28.5, -3.5, 3.5, 34.0, 3.0))
    # 顔の下半分: 細くなる楕円体を、前の面 (z = FZ) で平らに切る
    face = v2.ellip((0.0, 28.4, 0.6), (2.6, 2.1, 2.3))
    v2.fill(m, "head", lambda x, y, z: face(x, y, z) and y > 26.5 and z < FZ and not socket(x, y, z), "bone",
            (-3.0, 26.5, -2.0, 3.0, 30.5, FZ))
    # 眉の段 (眼窩の上に 0.5 出る)
    v2.fb(m, "head", (-2.5, 29.0, FZ, 2.5, 29.5, FZ + 0.5), "bone_dk")
    # 頬骨 (横へ 0.5 出る)
    v2.fb(m, "head", (-3.0, 27.5, 0.5, -2.5, 28.5, 2.0), "bone_dk")
    v2.fb(m, "head", (2.5, 27.5, 0.5, 3.0, 28.5, 2.0), "bone_dk")
    # 下顎 (細く、前へ少し出る) と歯
    v2.fb(m, "head", (-1.5, 25.5, 0.5, 1.5, 26.5, 2.5), "bone_dk")
    v2.fb(m, "head", (-1.0, 25.0, 1.0, 1.0, 25.5, 2.0), "bone_dk")
    # こめかみの珊瑚の芽 (左だけ。左右で違う形にする)
    v2.fb(m, "head", (2.5, 30.0, -1.0, 3.5, 31.0, 0.0), "coral_o")
    v2.fb(m, "head", (3.0, 31.0, -1.0, 3.5, 32.0, -0.5), "coral_p")
    # 珊瑚の冠 (v2 の A。頭蓋に食い込む所は空いた所だけ埋める)
    for bx, mat in (((-1.5, 33.0, -1.0, 1.5, 34.0, 1.0), "coral_p"), ((-2.0, 34.0, -1.0, -1.0, 36.0, 0.0), "coral_o"),
                    ((0.0, 34.0, -0.5, 1.0, 38.0, 0.5), "coral_p"), ((0.5, 38.0, -0.5, 1.0, 38.5, 0.5), "coral_v"),
                    ((-3.0, 33.0, 0.5, -1.5, 35.0, 1.5), "coral_v"), ((1.5, 33.0, -2.0, 2.5, 35.5, -1.0), "coral_o"),
                    ((2.5, 35.0, -1.5, 3.0, 36.0, -1.0), "coral_p"), ((1.5, 35.5, -2.0, 2.0, 36.0, -1.5), "coral_v")):
        v2.fb(m, "head", bx, mat)


def thrall_statue_coral(m):
    """像のときだけ見せる珊瑚 (statue_coral 骨)。体のまわりの空いた所に、枝と房を生やす。"""
    blobs = [  # (中心, 半径, 材質)
        ((-3.5, 24.5, 1.5), (1.2, 1.0, 1.0), "coral_p"),
        ((3.5, 22.0, 1.8), (1.0, 1.5, 0.8), "coral_o"),
        ((2.5, 17.0, 2.0), (1.4, 1.2, 0.8), "barnacle"),
        ((-2.0, 18.5, -2.0), (1.5, 1.5, 0.8), "coral_v"),
        ((1.0, 21.0, -2.2), (1.2, 1.8, 0.7), "coral_p"),
        ((0.0, 15.0, 2.0), (2.0, 1.0, 0.7), "barnacle"),
        ((-3.0, 12.0, 1.5), (1.0, 1.5, 0.8), "coral_o"),
        ((3.0, 11.5, -1.0), (0.9, 1.8, 0.9), "coral_p"),
    ]
    for c, r, mat in blobs:
        v2.fill(m, "statue_coral", v2.ellip(c, r), mat, v2.bb_of(c, r))
    # 頭の後ろと肩から伸びる枝
    for bx, mat in (((-1.0, 31.0, -3.5, 0.0, 34.0, -3.0), "coral_o"), ((-1.5, 33.5, -3.5, -1.0, 35.0, -3.0), "coral_o"),
                    ((0.5, 30.0, -3.5, 1.5, 31.0, -3.0), "coral_p"), ((-4.5, 26.5, -0.5, -3.5, 29.0, 0.5), "coral_v"),
                    ((-5.0, 29.0, -0.5, -4.5, 30.0, 0.0), "coral_v"), ((4.5, 25.5, 0.5, 5.0, 28.0, 1.0), "coral_p")):
        v2.fb(m, "statue_coral", bx, mat)
    # 足もとの房 (地面に根を張ったように)
    for bx, mat in (((-4.0, 0.0, -2.0, -3.0, 2.0, -1.0), "coral_p"), ((3.0, 0.0, 1.0, 4.5, 1.5, 2.0), "barnacle"),
                    ((-1.0, 0.0, -2.5, 1.0, 1.0, -1.5), "coral_o"), ((3.5, 0.0, -2.0, 4.0, 2.5, -1.5), "coral_v")):
        v2.fb(m, "statue_coral", bx, mat)


def thrall():
    m = tm.Model("coral_thrall")
    m.extra, m.glowfn = thrall_extra, thrall_glow
    hand_x = (3.0, 4.5)
    m.bone("body", (0, 14, 0))
    m.bone("head", (0, 26.5, 0), "body")
    m.bone("arm_r", (-4, 24, 0), "body")
    m.bone("forearm_r", (-3.75, 16, 0.5), "arm_r")
    m.bone("socket_main", (-3.75, 11.5, 2.5), "forearm_r")
    m.bone("arm_l", (4, 24, 0), "body")
    m.bone("forearm_l", (3.75, 16, 0), "arm_l")
    m.bone("leg_r", (-2, 13.5, 0))
    m.bone("leg_l", (2, 13.5, 0))
    m.bone("statue_coral", (0, 14, 0), "body")
    sb, fb, sbf = tm.sb, v2.fb, v2.sbf
    for sx in (-1, 1):
        lb = "leg_r" if sx < 0 else "leg_l"
        sb(m, lb, sx, 1, 3, 6.5, 13.5, -1, 1, "bone_dk")
        sb(m, lb, sx, 1, 2.5, 1, 6.5, -0.5, 1, "bone_dk")
        sb(m, lb, sx, 1.5, 2, 6, 6.5, 1, 1.5, "bone")          # 膝の瘤
        sb(m, lb, sx, 0.5, 3, 0, 1, -1, 2.5, "barnacle")
    m.box("body", -3, 13.5, -1.5, 3, 16, 1.5, "bone_dk")
    for a, b, y0 in [(-3.5, -2.5, 9.5), (-1.5, -0.5, 11.0), (0.5, 1.5, 8.5), (2.5, 3.5, 10.5)]:
        fb(m, "body", (a, y0, 1.5, b, 13.5, 2.5), "cloth")
    for a, b, y0 in ((-2.5, -1.5, 10.5), (1.5, 2.5, 9.5)):
        fb(m, "body", (a, y0, -2.5, b, 13.5, -1.5), "cloth")
    m.box("body", -3, 16, -1.5, 3, 25, 1.5, "bone")
    m.box("body", -0.5, 16, -2, 0.5, 24, -1.5, "bone_dk")
    for x0 in (-2.5, 0.5):
        fb(m, "body", (x0, 17, 1.5, x0 + 2, 23, 2), "bone_dk")
    for sx, arm in ((-1, "arm_r"), (1, "arm_l")):
        sbf(m, arm, sx, 3, 4.5, 16, 24, -1, 1, "bone")
        sbf(m, arm, sx, 3, 4.5, 24, 25, -1, 2, "barnacle")
    for sx in (-1, 1):
        sbf(m, "arm_r" if sx < 0 else "arm_l", sx, 3.5, 4.5, 25, 26.5, -0.5, 0.5, "coral_o")
        sbf(m, "arm_r" if sx < 0 else "arm_l", sx, 4, 4.5, 26.5, 27, 0, 0.5, "coral_p")
    lo, hi = hand_x
    sbf(m, "forearm_r", -1, lo, hi, 12.5, 16, -1, 2, "bone")
    sbf(m, "forearm_r", -1, lo, hi, 10.5, 12.5, 1.5, 3.5, "bone_dk")
    sbf(m, "forearm_l", 1, lo, hi, 8, 16, -1, 1, "bone")
    sbf(m, "forearm_l", 1, lo, hi, 6.5, 8, -1, 1.5, "bone_dk")
    # 首
    m.box("body", -1.5, 25, -1, 1.5, 26.5, 1, "bone_dk")
    thrall_head(m)
    for k in range(7):  # 斜めの布の帯
        fb(m, "body", (-3.5 + k, 22 - 0.5 * k, 1.5, -2.5 + k, 23.5 - 0.5 * k, 2.5), "cloth")
    # ここまでで埋まりを外してから、像の珊瑚を足す (像の珊瑚で体の面が消えないように、同じ骨の中だけで数える)
    thrall_statue_coral(m)
    return m


def thrall_statue_tex(tex, gl, geo):
    """像の姿: 全体を白く (石膏化)、像の珊瑚はうすい桃色の白。光は眼窩の奥だけを暗く残す。"""
    st = tm.bleach(tex)
    sgl = gl.copy()
    a = sgl[..., 3] > 0
    sgl[..., :3][a] = (sgl[..., :3][a] * 0.45).astype(np.uint8)
    return st, sgl


# ---------------------------------------------------------------- 磯走り (A)

def scuttler():
    m = v2.scuttler2("a")
    m.ident = "reef_scuttler"
    return m


# ---------------------------------------------------------------- 泡吹き (A、目を調整。骨を分ける)

def puffer(eyes="dull"):
    """v2 の puffer2("a", 1.0) と同じ形を、骨に分けて作る。
    body (体と背びれ)、spines (棘。ふくらむと外へ出る)、fin_r / fin_l (胸びれ)、tail (尾びれ)。"""
    s = 1.0
    m = tm.Model("bubble_puffer")
    rx, ry, rz = 6.0, 5.5, 6.5
    cy = ry + 1.0
    c = (0.0, cy, 0.0)
    m.bone("body", (0, cy, 0))
    m.bone("spines", (0, cy, 0), "body")
    yc = math.floor(cy - 1.5 * s) + 0.25
    m.bone("fin_r", (-4.5, yc, 1.0), "body")
    m.bone("fin_l", (4.5, yc, 1.0), "body")
    m.bone("tail", (0, cy, -rz + 0.5), "body")
    body_pred = v2.ellip(c, (rx, ry, rz))
    bbox = (-math.ceil(rx) - 1, 0, -math.ceil(rz) - 1, math.ceil(rx) + 1, math.ceil(cy + ry) + 1, math.ceil(rz) + 1)
    v2.fill(m, "body", body_pred, None, bbox, sz=2, mat_fn=lambda p: "belly" if p[1] < cy - 1.5 * s else "skin")

    # 目 (調整後): 体の面から 0.5 出る小さな玉。左右へ離して付ける
    if eyes == "dull":
        eye_rects = [(-4.5, -3.0), (3.0, 4.5)]
        ey0, ey1 = 7.0, 8.5
        occ = v2.occ_set(m)
        for a, b in eye_rects:
            # 目の範囲で、体の一番前の面 (zf) と一番奥の面 (zb) を、置いた立方体から求める
            fronts = []
            for i in range(round(a * 2), round(b * 2)):
                for j in range(round(ey0 * 2), round(ey1 * 2)):
                    ks = [k for (ii, jj, k) in occ if ii == i and jj == j]
                    fronts.append((max(ks) + 1) / 2 if ks else 0.0)
            zb, zf = min(fronts), max(fronts)
            # 目の前の面を、体の一番前の面より 0.5 前に出す (目は平らな小さな玉になる)
            v2.fb(m, "body", (a, ey0, zb, b, ey1, zf + 0.5), "eye")

    dirs = tm.fib(56)
    L = 1.0

    def spines(x, y, z):
        p = (x, y - cy, z)
        for d in dirs:
            rs = 1.0 / math.sqrt((d[0] / rx) ** 2 + (d[1] / ry) ** 2 + (d[2] / rz) ** 2)
            t = p[0] * d[0] + p[1] * d[1] + p[2] * d[2]
            perp = math.sqrt(max(0.0, sum(v * v for v in p) - t * t))
            if rs - 0.6 <= t <= rs + L and perp <= 0.4:
                return True
        return False
    v2.fill(m, "spines", spines, "bone", bbox, sz=1)

    def dorsal(x, y, z):
        return abs(x) <= 0.5 and -3.0 <= z <= 1.5 and cy + ry - 1.0 <= y <= cy + ry + (1.5 if z < 0 else 1.0)
    v2.fill(m, "body", dorsal, "skin", (-1, cy, -3.5, 1, cy + ry + 2, 2), sz=1)

    def pec(x, y, z):
        return 4.0 <= abs(x) <= rx + 1.5 and abs(y - yc) < 0.1 and -1.0 <= z <= 2.5
    v2.fill(m, "fin_r", lambda x, y, z: x < 0 and pec(x, y, z), "skin", (-rx - 2, yc - 1, -2, 0, yc + 1, 3), sz=1)
    v2.fill(m, "fin_l", lambda x, y, z: x > 0 and pec(x, y, z), "skin", (0, yc - 1, -2, rx + 2, yc + 1, 3), sz=1)

    def tail(x, y, z):
        if abs(x) > 0.5:
            return False
        zb = -rz + 0.5
        if not (zb - 3.5 <= z <= zb + 0.5):
            return False
        t = (zb - z) / 3.5
        return abs(y - cy) <= 1.5 + 2.0 * t
    v2.fill(m, "tail", tail, "skin", (-1, cy - 4, -rz - 4, 1, cy + 4, -rz + 1), sz=1)

    eye_y = cy + 0.5
    if eyes == "original":
        eyes_r = ((-3.5, -1.5), (1.5, 3.5))
        eye_rect = [(a, b, eye_y - 1.5, eye_y + 1.0) for a, b in eyes_r]
        glint = [(a + 0.3, a + 0.8, eye_y + 0.2, eye_y + 0.8) for a, _ in eyes_r]
    else:
        eye_rect, glint = [], []

    def in_rects(rects, X, Y):
        return any(a <= X <= b and c_ <= Y <= d for a, b, c_, d in rects)

    def mouth(X, Y):
        if eyes == "original":
            return -1.0 <= X <= 1.0 and cy - 2.5 <= Y <= cy - 2.0
        # 調整後: 口は少し広く、両端が下がる (むすっとした口)
        return (-1.25 <= X <= 1.25 and cy - 2.5 <= Y <= cy - 2.0) or (1.25 <= abs(X) <= 1.75 and cy - 3.0 <= Y <= cy - 2.5)

    def extra(X, Y, Z, f, mat):
        if mat == "eye":
            if f != "south":
                return MILK_DIM
            # 3x3 画素: 真ん中の縦の列が細い瞳孔、まわりは濁った白
            if abs(X - (-3.75 if X < 0 else 3.75)) <= 0.25:
                return PUPIL
            return MILK
        if f != "south":
            return None
        if in_rects(eye_rect, X, Y):
            if in_rects(glint, X, Y):
                return tm.RAMP["seaglass"][3]
            return tm.RAMP["dark"][0]
        if mouth(X, Y) and mat in ("skin", "belly"):
            return tm.RAMP["dark"][0]
        return None

    def glow(X, Y, Z, f, mat):
        if eyes == "original":
            return f == "south" and in_rects(glint, X, Y)
        # 濁った白目だけが、暗い所でうすく光る (瞳孔は光らない)
        return mat == "eye" and f == "south" and abs(X - (-3.75 if X < 0 else 3.75)) > 0.25
    m.extra, m.glowfn = extra, glow
    return m


def dim_glow(gl, k):
    out = gl.copy()
    a = out[..., 3] > 0
    out[..., :3][a] = (out[..., :3][a] * k).astype(np.uint8)
    return out


# ---------------------------------------------------------------- 銛

def spear():
    m = v2.build_spear()
    return m


def write_spear():
    m = spear()
    v2.prune(m)
    geo, tex, gl = m.finish()
    assert not vx.check_overlaps(geo)
    for name, t in (("coral_spear", tex), ("coral_spear_statue", tm.bleach(tex))):
        g = copy.deepcopy(geo)
        g["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + name
        (ASSETS / "geo/gear").mkdir(parents=True, exist_ok=True)
        (ASSETS / "geo/gear" / f"{name}.geo.json").write_text(json.dumps(g, indent=1) + "\n", encoding="utf-8")
        Image.fromarray(t, "RGBA").save(ASSETS / "textures/gear" / f"{name}.png")
        Image.fromarray(gl if name == "coral_spear" else dim_glow(gl, 0.4), "RGBA").save(ASSETS / "textures/gear" / f"{name}_glowmask.png")
        anim = {"format_version": "1.8.0", "animations": {f"animation.{name}.idle": {"loop": True, "animation_length": 1.0, "bones": {}}}}
        (ASSETS / "animations/gear").mkdir(parents=True, exist_ok=True)
        (ASSETS / "animations/gear" / f"{name}.animation.json").write_text(json.dumps(anim, indent=1) + "\n", encoding="utf-8")
    return dict(geo=geo, tex=tex, gl=gl)


# ---------------------------------------------------------------- アニメーション
# 値は意図した向き (度、ラフの絵で見える向き)。
#   {"loop": True|False|"hold", "len": 秒, "bones": {骨: {"rotation"|"position"|"scale": 値}}}
#   値は 3 つの molang の式 (文字列。query.anim_time と math.sin を使う。sin は度) か、
#   キーフレーム {時刻: (x, y, z)} (直線で補間)。

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


ANIM_THRALL = {
    "idle": {"loop": True, "len": 4.0, "bones": {
        "body": {"rotation": [sn(1.5, 90), "0", sn(1.0, 90, 90)]},
        "head": {"rotation": [sn(2, 90, 45), sn(6, 90), f"5 + {sn(2, 90, 180)}"]},
        "arm_l": {"rotation": [sn(3, 90, 30), "0", f"-3 + {sn(2, 90)}"]},
        "arm_r": {"rotation": [sn(1.5, 90, 60), "0", "0"]},
    }},
    "walk": {"loop": True, "len": 1.0, "bones": {
        "leg_r": {"rotation": [sn(22, 360), "0", "0"]},
        "leg_l": {"rotation": [sn(-22, 360), "0", "0"]},
        "arm_l": {"rotation": [sn(-16, 360), "0", "-3"]},
        "arm_r": {"rotation": [sn(5, 360, 180), "0", "0"]},
        "body": {"rotation": ["-4", sn(4, 360), "0"], "position": ["0", f"0.4 * math.abs(math.sin(360 * query.anim_time))", "0"]},
        "head": {"rotation": ["4", sn(-4, 360), "5"]},
    }},
    "attack": {"loop": False, "len": 0.9, "bones": {
        # 構え (引く) → 0.45 秒で突く (腕を前へ、前腕は逆へ回して銛を水平に保つ。体の傾きのぶんも前腕で戻す) → 戻る
        "arm_r": {"rotation": {0: (0, 0, 0), 0.3: (28, 0, 0), 0.45: (-48, 0, 0), 0.6: (-44, 0, 0), 0.9: (0, 0, 0)}},
        "forearm_r": {"rotation": {0: (0, 0, 0), 0.3: (-36, 0, 0), 0.45: (60, 0, 0), 0.6: (56, 0, 0), 0.9: (0, 0, 0)}},
        "body": {"rotation": {0: (0, 0, 0), 0.3: (8, -14, 0), 0.45: (-16, 12, 0), 0.6: (-14, 10, 0), 0.9: (0, 0, 0)},
                 "position": {0: (0, 0, 0), 0.3: (0, 0, -1), 0.45: (0, -0.5, 2.5), 0.6: (0, -0.5, 2.5), 0.9: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.3: (-8, 10, 0), 0.45: (10, -8, 0), 0.9: (0, 0, 0)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 0.3: (-14, 0, -6), 0.45: (20, 0, -10), 0.9: (0, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.35, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.08: (9, 0, -4), 0.35: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.08: (14, 0, 8), 0.35: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 1.0, "bones": {
        # 膝から崩れ、前へうつ伏せに倒れる
        "body": {"rotation": {0: (0, 0, 0), 0.25: (10, 0, 6), 0.7: (-78, 0, 8), 1.0: (-84, 0, 8)},
                 "position": {0: (0, 0, 0), 0.25: (0, -2, 0), 0.7: (0, -11, 0), 1.0: (0, -12, 0)}},
        "leg_r": {"rotation": {0: (0, 0, 0), 0.25: (-30, 0, 0), 0.7: (70, 0, 0), 1.0: (82, 0, 0)},
                  "position": {0: (0, 0, 0), 0.25: (0, -2, 0), 0.7: (0, -11, 0), 1.0: (0, -12, 0)}},
        "leg_l": {"rotation": {0: (0, 0, 0), 0.25: (-24, 0, 0), 0.7: (74, 0, 0), 1.0: (84, 0, 0)},
                  "position": {0: (0, 0, 0), 0.25: (0, -2, 0), 0.7: (0, -11, 0), 1.0: (0, -12, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.25: (20, 0, 10), 1.0: (30, 20, 14)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 0.7: (-60, 0, -20), 1.0: (-150, 0, -25)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 0.7: (-40, 0, 10), 1.0: (-80, 0, 20)}},
    }},
    # 像: 動かない。少し前かがみで、首をかしげたまま固まる
    "statue": {"loop": True, "len": 1.0, "bones": {
        "body": {"rotation": ["-6", "8", "0"]},
        "head": {"rotation": ["12", "-10", "10"]},
        "arm_l": {"rotation": ["-12", "0", "-6"]},
        "arm_r": {"rotation": ["-10", "0", "0"]},
        "leg_r": {"rotation": ["-8", "0", "0"]},
        "leg_l": {"rotation": ["8", "0", "0"]},
    }},
    # 目覚め: 像の姿勢から、殻を割るように震えて立ち直る
    "wake": {"loop": False, "len": 1.0, "bones": {
        "body": {"rotation": {0: (-6, 8, 0), 0.15: (-4, 5, 5), 0.3: (-6, 8, -5), 0.45: (-2, 3, 4), 0.7: (4, 0, 0), 1.0: (0, 0, 0)}},
        "head": {"rotation": {0: (12, -10, 10), 0.3: (-10, 15, -6), 0.55: (-14, 0, 0), 1.0: (0, 0, 0)}},
        "arm_l": {"rotation": {0: (-12, 0, -6), 0.3: (-25, 0, -18), 1.0: (0, 0, 0)}},
        "arm_r": {"rotation": {0: (-10, 0, 0), 0.4: (-20, 0, 8), 1.0: (0, 0, 0)}},
        "leg_r": {"rotation": {0: (-8, 0, 0), 1.0: (0, 0, 0)}},
        "leg_l": {"rotation": {0: (8, 0, 0), 1.0: (0, 0, 0)}},
    }},
}

ANIM_SCUTTLER = {
    "idle": {"loop": True, "len": 2.0, "bones": {
        "body": {"position": ["0", sn(0.25, 180), "0"]},
        "claw_r": {"rotation": [f"-4 + {sn(6, 360)}", sn(5, 180), "0"]},
        "claw_l": {"rotation": [f"-4 + {sn(6, 360, 120)}", sn(-5, 180), "0"]},
        "leg_r": {"rotation": ["0", "0", sn(3, 180)]},
        "leg_l": {"rotation": ["0", "0", sn(-3, 180)]},
    }},
    "walk": {"loop": True, "len": 0.3, "bones": {
        "leg_r": {"rotation": ["0", sn(16, 1200), f"{sn(14, 1200, 90)}"]},
        "leg_l": {"rotation": ["0", sn(16, 1200, 180), f"{sn(-14, 1200, 270)}"]},
        "body": {"position": ["0", f"0.5 * math.abs(math.sin(1200 * query.anim_time))", "0"], "rotation": ["0", "0", sn(3, 1200)]},
        "claw_r": {"rotation": ["-12", sn(8, 1200), "0"]},
        "claw_l": {"rotation": ["-12", sn(-8, 1200), "0"]},
    }},
    "attack": {"loop": False, "len": 0.45, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.12: (10, 0, 0), 0.22: (-14, 0, 0), 0.45: (0, 0, 0)},
                 "position": {0: (0, 0, 0), 0.12: (0, 0, -1), 0.22: (0, 0, 2), 0.45: (0, 0, 0)}},
        "claw_r": {"rotation": {0: (0, 0, 0), 0.12: (30, 18, 0), 0.22: (-35, -10, 0), 0.45: (0, 0, 0)}},
        "claw_l": {"rotation": {0: (0, 0, 0), 0.12: (30, -18, 0), 0.22: (-35, 10, 0), 0.45: (0, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.06: (8, 0, 10), 0.3: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 1.0, "bones": {
        # ひっくり返り、脚を縮めて止まる
        "body": {"rotation": {0: (0, 0, 0), 0.3: (0, 0, 120), 0.5: (0, 0, 180), 1.0: (0, 0, 180)},
                 "position": {0: (0, 0, 0), 0.3: (0, 4, 0), 0.5: (0, 4.5, 0), 1.0: (0, 4.5, 0)}},
        "leg_r": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, -25), 1.0: (0, 0, -45)}},
        "leg_l": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, 25), 1.0: (0, 0, 45)}},
        "claw_r": {"rotation": {0: (0, 0, 0), 1.0: (40, 20, 0)}},
        "claw_l": {"rotation": {0: (0, 0, 0), 1.0: (40, -20, 0)}},
    }},
    # 裏返り (会心の一撃を受けて 3 秒): 背を下にして、脚をばたつかせる
    "flipped": {"loop": True, "len": 0.4, "bones": {
        "body": {"rotation": ["0", "0", f"180 + {sn(6, 900)}"], "position": ["0", "4.5", "0"]},
        "leg_r": {"rotation": ["0", sn(20, 900), sn(25, 900, 90)]},
        "leg_l": {"rotation": ["0", sn(-20, 900), sn(-25, 900, 90)]},
        "claw_r": {"rotation": [sn(25, 900), "0", "0"]},
        "claw_l": {"rotation": [sn(25, 900, 180), "0", "0"]},
    }},
}

ANIM_PUFFER = {
    "idle": {"loop": True, "len": 2.0, "bones": {
        "body": {"position": ["0", sn(0.8, 180), "0"], "rotation": [sn(3, 180, 90), "0", "0"]},
        "tail": {"rotation": ["0", sn(14, 360), "0"]},
        "fin_r": {"rotation": ["0", sn(16, 540), sn(10, 540)]},
        "fin_l": {"rotation": ["0", sn(-16, 540), sn(-10, 540)]},
    }},
    "walk": {"loop": True, "len": 1.0, "bones": {
        "body": {"position": ["0", sn(0.6, 360), "0"], "rotation": ["-4", sn(4, 360), "0"]},
        "tail": {"rotation": ["0", sn(26, 720), "0"]},
        "fin_r": {"rotation": ["0", sn(24, 720), sn(14, 720)]},
        "fin_l": {"rotation": ["0", sn(-24, 720), sn(-14, 720)]},
    }},
    # 泡を吹く: 頬をふくらませ (体を少し大きく)、前へ吐き出す
    "attack": {"loop": False, "len": 0.7, "bones": {
        "body": {"scale": {0: (1, 1, 1), 0.35: (1.12, 1.1, 1.08), 0.45: (0.94, 0.95, 1.04), 0.7: (1, 1, 1)},
                 "rotation": {0: (0, 0, 0), 0.35: (6, 0, 0), 0.45: (-12, 0, 0), 0.7: (0, 0, 0)}},
        "tail": {"rotation": {0: (0, 0, 0), 0.35: (0, 20, 0), 0.45: (0, -25, 0), 0.7: (0, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.06: (10, 0, 16), 0.3: (0, 0, 0)}},
        "spines": {"scale": {0: (1, 1, 1), 0.06: (1.08, 1.08, 1.08), 0.3: (1, 1, 1)}},
    }},
    "death": {"loop": "hold", "len": 1.0, "bones": {
        # しぼんで、腹を上にして沈む
        "body": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, 120), 1.0: (0, 0, 175)},
                 "position": {0: (0, 0, 0), 0.5: (0, -2, 0), 1.0: (0, -4, 0)},
                 "scale": {0: (1, 1, 1), 0.3: (1.1, 1.1, 1.1), 1.0: (0.85, 0.8, 0.85)}},
        "spines": {"scale": {0: (1, 1, 1), 1.0: (0.9, 0.9, 0.9)}},
        "fin_r": {"rotation": {0: (0, 0, 0), 1.0: (0, 0, 30)}},
        "fin_l": {"rotation": {0: (0, 0, 0), 1.0: (0, 0, -30)}},
        "tail": {"rotation": {0: (0, 0, 0), 1.0: (0, 25, 0)}},
    }},
    # ふくらむ (0.35 秒) → ふくらんだまま (ループ) → しぼむ (0.5 秒)
    "inflate": {"loop": "hold", "len": 0.35, "bones": {
        "body": {"scale": {0: (1, 1, 1), 0.2: (1.55, 1.55, 1.55), 0.35: (1.45, 1.45, 1.45)}},
        "spines": {"scale": {0: (1, 1, 1), 0.35: (1.12, 1.12, 1.12)}},
    }},
    "inflated": {"loop": True, "len": 1.0, "bones": {
        "body": {"scale": [f"1.45 + {sn(0.03, 360)}", f"1.45 + {sn(0.03, 360)}", f"1.45 + {sn(0.03, 360)}"]},
        "spines": {"scale": ["1.12", "1.12", "1.12"]},
        "fin_r": {"rotation": ["0", sn(30, 900), sn(14, 900)]},
        "fin_l": {"rotation": ["0", sn(-30, 900), sn(-14, 900)]},
        "tail": {"rotation": ["0", sn(20, 900), "0"]},
    }},
    "deflate": {"loop": False, "len": 0.5, "bones": {
        "body": {"scale": {0: (1.45, 1.45, 1.45), 0.5: (1, 1, 1)}},
        "spines": {"scale": {0: (1.12, 1.12, 1.12), 0.5: (1, 1, 1)}},
    }},
}


def _num(v):
    return float(round(v, 4))


def _signed(expr, sgn):
    return expr if sgn == 1 else ("0" if expr.strip() == "0" else f"-({expr})")


def anim_json(prefix, spec):
    out = {}
    for name, a in spec.items():
        bones = {}
        for bone, chans in a["bones"].items():
            bj = {}
            for ch, val in chans.items():
                sg = SIGN if ch == "rotation" else (1, 1, 1)
                if isinstance(val, dict):
                    bj[ch] = {f"{t:.4g}": {"vector": [_num(v[i] * sg[i]) for i in range(3)]} for t, v in sorted(val.items())}
                else:
                    bj[ch] = [_signed(val[i], sg[i]) for i in range(3)]
            bones[bone] = bj
        entry = {"animation_length": a["len"], "bones": bones}
        if a["loop"] == "hold":
            entry["loop"] = "hold_on_last_frame"
        else:
            entry["loop"] = bool(a["loop"])
        out[f"animation.{prefix}.{name}"] = entry
    return {"format_version": "1.8.0", "animations": out}


def write_anim(prefix, spec):
    (ASSETS / "animations/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations/entity" / f"{prefix}.animation.json").write_text(
        json.dumps(anim_json(prefix, spec), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def eval_channel(val, t):
    if isinstance(val, dict):
        ks = sorted(val)
        if t <= ks[0]:
            return np.array(val[ks[0]], float)
        for k0, k1 in zip(ks, ks[1:]):
            if k0 <= t <= k1:
                u = (t - k0) / (k1 - k0) if k1 > k0 else 0
                return np.array(val[k0], float) * (1 - u) + np.array(val[k1], float) * u
        return np.array(val[ks[-1]], float)
    env = {"msin": lambda d: math.sin(math.radians(d)), "mabs": abs, "t": t}
    res = []
    for e in val:
        e2 = e.replace("math.sin", "msin").replace("math.abs", "mabs").replace("query.anim_time", "t")
        res.append(float(eval(e2, {}, env)))
    return np.array(res)


def pose_at(spec, name, t):
    """意図した回転 {骨: (x, y, z)} (確認の絵用。位置と拡大は絵に入れない)"""
    pose = {}
    for bone, chans in spec[name]["bones"].items():
        if "rotation" in chans:
            pose[bone] = tuple(eval_channel(chans["rotation"], t))
    return pose


# ---------------------------------------------------------------- 確認の絵

def faces_of(r, pose=None, tex=None, gl=None, hide=()):
    g = copy.deepcopy(r["view"])
    for b in g["minecraft:geometry"][0]["bones"]:
        if b["name"] in hide:
            b["cubes"] = []
    return vx.load_geo(g, r["tex"] if tex is None else tex, r["gl"] if gl is None else gl, pose=pose)


def with_spear(r, sp, pose=None, tex=None, gl=None, sp_tex=None, hide=()):
    """銛を受け口に付けた面 (別の geo のまま、受け口の骨の変換を使う)"""
    g = copy.deepcopy(r["view"])
    gb = g["minecraft:geometry"][0]
    p = next(b["pivot"] for b in gb["bones"] if b["name"] == "socket_main")
    for b in gb["bones"]:
        if b["name"] in hide:
            b["cubes"] = []
    body_faces = vx.load_geo(g, r["tex"] if tex is None else tex, r["gl"] if gl is None else gl, pose=pose)
    for b in gb["bones"]:
        b["cubes"] = []
    sbones = copy.deepcopy(sp["geo"])["minecraft:geometry"][0]["bones"]
    for b in sbones:
        b["name"] = "spear_" + b["name"]
        b["parent"] = "socket_main" if "parent" not in b else "spear_" + b["parent"]
        b["pivot"] = [a + c for a, c in zip(b["pivot"], p)]
        for c in b.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
    gb["bones"] += sbones
    return body_faces + vx.load_geo(g, sp["tex"] if sp_tex is None else sp_tex, sp["gl"], pose=pose)


def label(img, text):
    d = ImageDraw.Draw(img)
    d.text((10, 8), text, font=vx.font(18), fill=(230, 230, 240))
    return img


def previews(th, st_tex, st_gl, sc, pu, sp):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    cols = []
    rows = [label(tm.game_render(with_spear(th, sp, hide=("statue_coral",)), 205, 18), "正面3/4"),
            label(tm.game_render(with_spear(th, sp, hide=("statue_coral",)), 270, 8), "横"),
            label(tm.game_render(with_spear(th, sp, pose=pose_at(ANIM_THRALL, "attack", 0.3), hide=("statue_coral",)), 250, 12), "突き: 構え"),
            label(tm.game_render(with_spear(th, sp, pose=pose_at(ANIM_THRALL, "attack", 0.45), hide=("statue_coral",)), 250, 12), "突き"),
            label(tm.game_render(with_spear(th, sp, pose=pose_at(ANIM_THRALL, "statue", 0), tex=st_tex, gl=st_gl, sp_tex=tm.bleach(sp["tex"])), 205, 12), "像"),
            label(tm.game_render(with_spear(th, sp, pose=pose_at(ANIM_THRALL, "death", 1.0), hide=("statue_coral",)), 250, 20), "死")]
    cols.append(("珊瑚憑き", rows))
    head = vx.render(faces_of(th, hide=("statue_coral", "arm_r", "arm_l", "forearm_r", "forearm_l", "socket_main")),
                     yaw=-205, pitch=10, size=(360, 360)).transpose(tm.FLIP)
    cols.append(("頭 (彫り直し)", [label(head, "頭")]))
    rows = [label(tm.game_render(faces_of(sc), 205, 18), "正面3/4"),
            label(tm.game_render(faces_of(sc, pose=pose_at(ANIM_SCUTTLER, "attack", 0.22)), 270, 10), "噛みつき"),
            label(tm.game_render(faces_of(sc, pose={"body": (0, 0, 180)}), 205, 25), "裏返り")]
    cols.append(("磯走り", rows))
    rows = [label(tm.game_render(faces_of(pu), 205, 18), "正面3/4"),
            label(tm.game_render(faces_of(pu), 270, 8), "横"),
            label(tm.game_render(faces_of(pu), 180, 4), "正面")]
    cols.append(("泡吹き", rows))
    vx.sheet("潮殻の背の敵 (取り込み後の確認)", cols, note="ゲームの向き (X の鏡写し) で描いた。角度は意図した向き").save(PREVIEW / "tide_mobs.png")


def eyes_compare():
    """泡吹きの目の、調整の前 (v2 の A) と後を並べる"""
    before = finish(puffer("original"))
    after = finish(puffer("dull"))
    after["gl"] = dim_glow(after["gl"], 0.5)

    def views(r):
        out = []
        for yaw, pitch, size in ((205, 18, (360, 360)), (180, 4, (360, 360))):
            out.append(tm.game_render(faces_of(r), yaw, pitch))
        # 目の寄り (正面の上半分を拡大)
        big = vx.render(faces_of(r), yaw=-180, pitch=4, size=(720, 720)).transpose(tm.FLIP)
        out.append(big.crop((150, 230, 570, 500)).resize((360, 231), Image.LANCZOS))
        return out
    cols = [("前 (v2 の A)", views(before)), ("後 (濁った小さな目)", views(after))]
    EYES_PNG.parent.mkdir(parents=True, exist_ok=True)
    vx.sheet("泡吹きの目の調整", cols,
             note="後: 小さく濁った白目、細い瞳孔、左右へ離す。戻す: --eyes original").save(EYES_PNG)


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--eyes", choices=("dull", "original"), default="dull")
    args = ap.parse_args()

    sp = write_spear()

    th = finish(thrall(), rest=THRALL_REST)
    write_model("coral_thrall", th)
    st_tex, st_gl = thrall_statue_tex(th["tex"], th["gl"], th["game"])
    Image.fromarray(st_tex, "RGBA").save(ASSETS / "textures/entity/coral_thrall_statue.png")
    Image.fromarray(st_gl, "RGBA").save(ASSETS / "textures/entity/coral_thrall_statue_glowmask.png")
    write_anim("coral_thrall", ANIM_THRALL)

    sc = finish(scuttler())
    write_model("reef_scuttler", sc)
    write_anim("reef_scuttler", ANIM_SCUTTLER)

    pu = finish(puffer(args.eyes))
    if args.eyes == "dull":
        pu["gl"] = dim_glow(pu["gl"], 0.5)   # 濁った目の光は弱く (暗い所でうすく見える程度)
    write_model("bubble_puffer", pu)
    write_anim("bubble_puffer", ANIM_PUFFER)

    for name, r in (("coral_thrall", th), ("reef_scuttler", sc), ("bubble_puffer", pu)):
        print(f"{name}: 箱 {r['n']}、テクスチャ {r['tex'].shape[1]}x{r['tex'].shape[0]}、光る画素 {int((r['gl'][..., 3] > 0).sum())}")
    previews(th, st_tex, st_gl, sc, pu, sp)
    eyes_compare()


if __name__ == "__main__":
    main()
