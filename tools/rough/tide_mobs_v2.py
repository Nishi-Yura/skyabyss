"""潮の民の敵 3 種のラフ第 2 版: 珊瑚憑き (coral_thrall) / 磯走り (reef_scuttler) / 泡吹き (bubble_puffer)。
v1 (tide_mobs.py) の部品 (Model, export, 銛, 色の段, 描画) を使い、形だけ作り直す。

実行: リポジトリ直下で  PYTHONIOENCODING=utf-8 python tools/rough/tide_mobs_v2.py
出力: docs/review/tide_shell_roughs/mobs/<subject>/<a|b|c>/ と、比較シート docs/review/tide_shell_roughs/<subject>.png

方針:
- 立方体は重ねない。立体を足すときは fill() で「空いている 0.5 単位の格子」だけを埋める (重なりは作れない)。
- 0.5 単位の格子で削って凹凸を出す (大きな体の芯だけ 1 単位)。
- 回転は使わない (重なり検査が全部の立方体に効くように)。曲がり・前かがみは段を 0.5 ずつずらして作る。
- 膝は、大腿・膝の瘤・すねの 3 段で折れた脚にする。
- 光る画素は glowmask。目の奥は暗く、ほのかな光を 1 画素だけ入れる。
"""
from __future__ import annotations

import copy
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import tide_mobs as tm  # noqa: E402

REVIEW = tm.REVIEW
MOBS = tm.MOBS
VIEW = tm.VIEW
RAMP = tm.RAMP
sb = tm.sb
fib = tm.fib


# ---------------------------------------------------------------- 空きの格子に埋める

def occ_set(m):
    """既にある箱が占める 0.5 単位の格子の番地 (i, j, k) の集合。"""
    s = set()
    for bx in m.boxes:
        x0, y0, z0, x1, y1, z1 = bx["b"]
        for i in range(round(x0 * 2), round(x1 * 2)):
            for j in range(round(y0 * 2), round(y1 * 2)):
                for k in range(round(z0 * 2), round(z1 * 2)):
                    s.add((i, j, k))
    return s


def fill(m, bone, pred, mat, bbox, sz=1, mat_fn=None):
    """pred(cx, cy, cz) が真の格子の立方体を、空いている所だけ埋める。
    bbox = (x0, y0, z0, x1, y1, z1) 単位。sz は立方体の辺 (半単位。1 = 0.5 単位、2 = 1 単位)。
    同じ材質が x 方向に続く所はまとめて 1 つの箱にする。"""
    occ = occ_set(m)
    X0, Y0, Z0 = (int(math.floor(v * 2)) for v in bbox[:3])
    X1, Y1, Z1 = (int(math.ceil(v * 2)) for v in bbox[3:])
    cells = {}
    for i in range(X0, X1, sz):
        for j in range(Y0, Y1, sz):
            for k in range(Z0, Z1, sz):
                c = ((i + sz / 2) / 2, (j + sz / 2) / 2, (k + sz / 2) / 2)
                if not pred(*c):
                    continue
                if any((i + a, j + b, k + d) in occ for a in range(sz) for b in range(sz) for d in range(sz)):
                    continue
                cells[(i, j, k)] = mat_fn(c) if mat_fn else mat
    # 6 方向すべて塞がっている立方体は見えない (埋まり) ので入れない
    occ2 = occ | set(cells) if sz == 1 else None
    if sz == 1:
        for key in [q for q in cells if all(((q[0] + dx, q[1] + dy, q[2] + dz) in occ2) for dx, dy, dz in
                                             ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]:
            del cells[key]
    keys = sorted(cells, key=lambda t: (t[1], t[2], t[0]))
    n = 0
    while n < len(keys):
        x, y, z = keys[n]
        mt = cells[keys[n]]
        run = 1
        while n + run < len(keys) and keys[n + run] == (x + sz * run, y, z) and cells[keys[n + run]] == mt:
            run += 1
        m.box(bone, x / 2, y / 2, z / 2, (x + sz * run) / 2, (y + sz) / 2, (z + sz) / 2, mt)
        n += run
    return len(cells)


def fb(m, bone, box, mat, sz=1):
    """箱の範囲を、空いている所だけ埋める (くっつく飾りや、はみ出しを防ぐための埋め方)。"""
    x0, y0, z0, x1, y1, z1 = box
    return fill(m, bone, lambda x, y, z: True, mat, (x0, y0, z0, x1, y1, z1), sz=sz)


def sbf(m, bone, sx, a, b, y0, y1, z0, z1, mat):
    """sb と同じ左右対称の箱だが、空いている所だけ埋める (先にある飾りと重ならない)。"""
    lo, hi = sorted((sx * a, sx * b))
    fb(m, bone, (lo, y0, z0, hi, y1, z1), mat)


def ellip(c, r):
    cx, cy, cz = c
    rx, ry, rz = r
    return lambda x, y, z: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2 <= 1.0


def bb_of(c, r, pad=0.5):
    return (c[0] - r[0] - pad, c[1] - r[1] - pad, c[2] - r[2] - pad, c[0] + r[0] + pad, c[1] + r[1] + pad, c[2] + r[2] + pad)


def prune(m):
    """全体の 0.5 格子で、6 方向すべて塞がった立方体を外し、残りを箱に組み直す (埋まりを無くす)。"""
    cell = {}
    for bx in m.boxes:
        x0, y0, z0, x1, y1, z1 = bx["b"]
        for i in range(round(x0 * 2), round(x1 * 2)):
            for j in range(round(y0 * 2), round(y1 * 2)):
                for k in range(round(z0 * 2), round(z1 * 2)):
                    cell[(i, j, k)] = (bx["bone"], bx["mat"])
    dirs = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    while True:
        dead = [q for q in cell if all((q[0] + a, q[1] + b, q[2] + c) in cell for a, b, c in dirs)]
        if not dead:
            break
        for q in dead:
            del cell[q]
    m.boxes = []
    groups = {}
    for q, bm in cell.items():
        groups.setdefault(bm, []).append(q)
    for (bone, mat), qs in groups.items():
        left = set(qs)
        for q in sorted(qs, key=lambda t: (t[1], t[2], t[0])):
            if q not in left:
                continue
            # x, y, z の順に、同じ材質の空きが続く所まで伸ばす (貪欲な直方体分割)
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


def finish_export(m, folder: Path, root=True):
    """書き出し。重なりは Model.box が置くたびに調べている。埋まりを外してから書き出す。"""
    prune(m)
    r = tm.export(m, folder, root=root)
    if r["buried"] is None:
        r["buried"] = tm.buried_count(m.boxes)
    return r


def report(label, r):
    print(f"{label}: 箱 {r['n']}、重なり {r['overlaps'] or 'なし'}、埋まり {r['buried']}")


# ---------------------------------------------------------------- 銛 (短く、別モデル)

def build_spear():
    m = tm.Model("coral_spear")
    m.bone("spear", (0, 0, 0))

    def ring(y0, y1, mat):  # 軸の周りを囲む輪 (軸の 1x1 の穴を残す)
        m.box("spear", -1, y0, -1, -0.5, y1, 1, mat)
        m.box("spear", 0.5, y0, -1, 1, y1, 1, mat)
        m.box("spear", -0.5, y0, -1, 0.5, y1, -0.5, mat)
        m.box("spear", -0.5, y0, 0.5, 0.5, y1, 1, mat)

    def rod(y0, y1, mat):
        m.box("spear", -0.5, y0, -0.5, 0.5, y1, 0.5, mat)

    m.box("spear", -0.5, -9.5, -0.5, 0.5, -9, 0.5, "bone_dk")  # 石突き
    rod(-9, -2, "bone_dk")
    ring(-2, 2, "cloth")          # 握りの布巻き
    rod(2, 8, "bone")
    ring(8, 9, "coral_o")         # 珊瑚の節
    rod(9, 13, "bone")
    ring(13, 14, "coral_v")
    rod(14, 16, "bone")
    m.box("spear", -1, 16, -1, 1, 17, 1, "coral_p")               # 根元の輪
    m.box("spear", -1, 17, -0.5, 1, 17.5, 0.5, "coral_p")         # 銛の首
    m.box("spear", -2, 17.5, -0.5, -1, 18.5, 0.5, "coral_o")      # 返し (左)
    m.box("spear", 1, 17.5, -0.5, 2, 18.5, 0.5, "coral_o")        # 返し (右)
    m.box("spear", -1, 17.5, -0.5, 1, 19.5, 0.5, "coral_p")       # 穂
    m.box("spear", -0.5, 19.5, -0.5, 0.5, 21.5, 0.5, "seaglass")  # 穂先 (海硝子が光る)
    return m


# ---------------------------------------------------------------- 珊瑚憑き

def face_eyes2(fz):
    """頭の前の面 (z = fz) の、くぼんだ目 (暗い穴) と、奥の小さな光。"""
    OUT = ((-2.0, -0.5, 27.5, 29.5), (0.5, 2.0, 27.5, 29.5))
    IN = ((-1.5, -0.5, 28.0, 28.5), (0.5, 1.5, 28.0, 28.5))  # ほのかな光の画素

    def inside(rects, X, Y):
        return any(a <= X <= b and c <= Y <= d for a, b, c, d in rects)

    def extra(X, Y, Z, f, mat):
        if mat == "bone" and f == "south" and abs(Z - fz) < 1e-6:
            if inside(IN, X, Y):
                return RAMP["seaglass"][1]
            if inside(OUT, X, Y):
                return RAMP["dark"][0]
        if mat == "bone_dk" and f == "south" and abs(Z - fz) < 1e-6 and 25.5 <= Y <= 26.5 and -1.0 <= X <= 1.0:
            return RAMP["dark"][0]  # 口の切れ目
        return None

    def glow(X, Y, Z, f, mat):
        return mat == "bone" and f == "south" and abs(Z - fz) < 1e-6 and inside(IN, X, Y)
    return extra, glow


def thrall2(opt: str):
    m = tm.Model(f"coral_thrall_{opt}")
    lean = opt == "c"
    fz = 4.5 if lean else 2.5          # 顔の前の面
    m.extra, m.glowfn = face_eyes2(fz)
    hand_x = {"a": (3.0, 4.5), "b": (4.0, 6.0), "c": (3.0, 4.5)}[opt]
    socket_p = (-(hand_x[0] + hand_x[1]) / 2, 11.5, 2.5)

    m.bone("body", (0, 14, 0))
    m.bone("head", (0, 26.5, 0), "body")
    m.bone("arm_r", (-4, 24, 0), "body")
    m.bone("forearm_r", (-4, 16, 0), "arm_r")
    m.bone("socket_main", socket_p, "forearm_r")    # 銛を持つ受け口 (右の手の中心)
    m.bone("arm_l", (4, 24, 0), "body")
    m.bone("forearm_l", (4, 16, 0), "arm_l")
    m.bone("leg_r", (-2, 13.5, 0))
    m.bone("leg_l", (2, 13.5, 0))

    # ---- 脚 (細い。大腿・すね・足の 3 段)
    for sx in (-1, 1):
        lb = "leg_r" if sx < 0 else "leg_l"
        sb(m, lb, sx, 1, 3, 6.5, 13.5, -1, 1, "bone_dk")
        sb(m, lb, sx, 1, 2.5, 1, 6.5, -0.5, 1, "bone_dk")
        sb(m, lb, sx, 0.5, 3, 0, 1, -1, 2.5, "barnacle")

    # ---- 腰と、垂れ布の裾
    m.box("body", -3, 13.5, -1.5, 3, 16, 1.5, "bone_dk")
    hems = [(-3.5, -2.5, 9.5), (-1.5, -0.5, 11.0), (0.5, 1.5, 8.5), (2.5, 3.5, 10.5)]
    for a, b, y0 in hems:
        fb(m, "body", (a, y0, 1.5, b, 13.5, 2.5), "cloth")
    if opt != "c":
        for a, b, y0 in ((-2.5, -1.5, 10.5), (1.5, 2.5, 9.5)):
            fb(m, "body", (a, y0, -2.5, b, 13.5, -1.5), "cloth")

    # ---- 胸・背骨
    if opt == "a":
        m.box("body", -3, 16, -1.5, 3, 25, 1.5, "bone")
        m.box("body", -0.5, 16, -2, 0.5, 24, -1.5, "bone_dk")          # 背骨
        for x0 in (-2.5, 0.5):                                       # あばら (0.5 出る)
            fb(m, "body", (x0, 17, 1.5, x0 + 2, 23, 2), "bone_dk")
    elif opt == "b":
        m.box("body", -4, 16, -2, 4, 25, 2, "bone")
        m.box("body", -0.5, 16, -2.5, 0.5, 24, -2, "bone_dk")
        for x0 in (-3, -1, 1):
            fb(m, "body", (x0, 17, 2, x0 + 1.5, 23.5, 2.5), "bone_dk")
    else:  # 前かがみ: 段を 0.5 ずつ前へずらす
        m.box("body", -3, 16, -1.5, 3, 19, 1.5, "bone")
        m.box("body", -3, 19, -0.5, 3, 22, 2, "bone")
        m.box("body", -3, 22, 0.5, 3, 25, 2.5, "bone")
        m.box("body", -2.5, 19, -2, 2.5, 25, -0.5, "kelp")                # 背の瘤
        fb(m, "body", (-2.5, 19, -2, 2.5, 25, 0.5), "kelp")
        for a, b, y0, y1 in ((-3, -2, 6, 19), (-1.5, -0.5, 8, 19), (0.5, 1.5, 7, 19), (2, 3, 9, 19)):
            m.box("body", a, y0, -2.5, b, y1, -1.5, "kelp")          # 海藻の外套
        for x0 in (-1.5, 0.5):
            fb(m, "body", (x0, 17, 2.5, x0 + 1.5, 23, 3), "bone_dk")

    # ---- 両腕
    for sx, arm in ((-1, "arm_r"), (1, "arm_l")):
        if opt == "b":
            sbf(m, arm, sx, 4, 6, 16, 24, -1.5, 1.5, "bone")
        elif opt == "c":
            sbf(m, arm, sx, 3, 4.5, 16, 24, 0, 2, "bone")
        else:
            sbf(m, arm, sx, 3, 4.5, 16, 24, -1, 1, "bone")
        top = 24 if opt != "c" else 24
        sbf(m, arm, sx, hand_x[0] if opt == "b" else 3, hand_x[1] if opt == "b" else 4.5, top, top + 1, -1, 2, "barnacle")

    # 肩の珊瑚 (枝)
    for sx in (-1, 1):
        sbf(m, "arm_r" if sx < 0 else "arm_l", sx, 3.5, 4.5, 25, 26.5, -0.5, 0.5, "coral_o")
        sbf(m, "arm_r" if sx < 0 else "arm_l", sx, 4, 4.5, 26.5, 27, 0, 0.5, "coral_p")
    if opt == "b":
        fb(m, "body", (3.5, 22, -2.5, 6.5, 25.5, 2.5), "barnacle")  # 大きな肩当て (空いた所だけ)

    # 右の腕 (銛): 下へ、そして手が前に出る
    lo, hi = hand_x
    sbf(m, "forearm_r", -1, lo, hi, 12.5, 16, -1, 2, "bone")
    sbf(m, "forearm_r", -1, lo, hi, 10.5, 12.5, 1.5, 3.5, "bone_dk")
    # 左の腕: 垂れた手 (B は珊瑚の棍棒)
    sbf(m, "forearm_l", 1, lo, hi, 8, 16, 0 if opt == "c" else -1, 2 if opt == "c" else 1, "bone")
    if opt == "b":
        sbf(m, "forearm_l", 1, 4.5, 5.5, 5, 8, -0.5, 0.5, "bone_dk")       # 棍棒の柄
        sbf(m, "forearm_l", 1, 3.5, 6.5, 0, 5, -1.5, 2.5, "brain")         # 棍棒の頭
    else:
        sbf(m, "forearm_l", 1, lo, hi, 6.5, 8, 0 if opt == "c" else -1, 3 if opt == "c" else 1.5, "bone_dk")

    # ---- 首と頭
    if lean:
        m.box("body", -1.5, 25, 0, 1.5, 27, 2, "bone_dk")
        m.box("head", -2, 25.5, 2, 2, 27, 3.5, "bone_dk")                  # 顎
        m.box("head", -2.5, 27, 0.5, 2.5, 29.5, 4.5, "bone")               # 顔
        m.box("head", -3, 29.5, -1, 3, 33, 3, "bone")                      # 頭蓋 (後ろへ傾く)
        m.box("head", -3, 29, 4.5, 3, 29.5, 5, "bone_dk")                  # 眉の骨
    else:
        m.box("body", -1.5, 25, -1, 1.5, 27, 1, "bone_dk")
        m.box("head", -2, 25.5, 1, 2, 27, 3, "bone_dk")                    # 顎 (前へ出る)
        m.box("head", -2.5, 27, -2, 2.5, 29.5, 2.5, "bone")                # 顔
        m.box("head", -2.5, 29.5, -2.5, 3.5, 33, 2.5, "bone")              # 頭蓋 (右へ傾く)
        m.box("head", -3, 29, 2.5, 3, 29.5, 3, "bone_dk")                  # 眉の骨
    # 目の周りの頬の段 (0.5 出る)
    fb(m, "head", (-3.0, 27.0, fz - 0.5, -2.5, 29.5, fz), "bone_dk")
    fb(m, "head", (2.5, 27.0, fz - 0.5, 3.0, 29.5, fz), "bone_dk")

    if opt == "a":  # 珊瑚の冠: 枝分かれ
        m.box("head", -1.5, 33, -1, 1.5, 34, 1, "coral_p")
        m.box("head", -2, 34, -1, -1, 36, 0, "coral_o")
        m.box("head", 0, 34, -0.5, 1, 38, 0.5, "coral_p")
        m.box("head", 0.5, 38, -0.5, 1, 38.5, 0.5, "coral_v")
        m.box("head", -3, 33, 0.5, -1.5, 35, 1.5, "coral_v")
        m.box("head", 1.5, 33, -2, 2.5, 35.5, -1, "coral_o")
        m.box("head", 2.5, 35, -1.5, 3, 36, -1, "coral_p")
        m.box("head", 1.5, 35.5, -2, 2, 36, -1.5, "coral_v")
    elif opt == "b":  # 脳珊瑚の頭の殻 (片側)
        fill(m, "head", ellip((-1.5, 33.5, 0), (2.5, 1.5, 2.5)), "brain", bb_of((-1.5, 33.5, 0), (2.5, 1.5, 2.5)))
    else:  # 光るイソギンチャクの頭
        m.box("head", -2.5, 33, fz - 4.5, 2.5, 34, fz - 1.5, "coral_p")
        for i, x0 in enumerate((-2, -1, 0, 1, 2)):
            z0 = fz - 4 if i % 2 == 0 else fz - 3
            m.box("head", x0, 34, z0 - 0.5, x0 + 0.5, 35.5, z0, "coral_v")
            m.box("head", x0, 35.5, z0 - 0.5, x0 + 0.5, 36, z0, "seaglass")

    # ---- 珊瑚の出っ張り (体の表面)
    if opt == "a":
        for k in range(7):  # 斜めの布の帯
            fb(m, "body", (-3.5 + k, 22 - 0.5 * k, 1.5, -2.5 + k, 23.5 - 0.5 * k, 2.5), "cloth")
    if opt == "b":  # 右の肩を脳珊瑚が包む
        fill(m, "body", ellip((4.5, 25.0, 0.5), (3.0, 2.4, 2.6)), "brain", bb_of((4.5, 25.0, 0.5), (3.0, 2.4, 2.6)))
        fill(m, "body", ellip((-3.5, 15.5, -2.0), (1.5, 1.2, 1.5)), "brain", bb_of((-3.5, 15.5, -2.0), (1.5, 1.2, 1.5)))
    if opt == "c":  # 藤壺の房 (背)
        fb(m, "body", (-1.5, 20.5, -2.5, -0.5, 21.5, -2.0), "barnacle")
        fb(m, "body", (1, 16, -2.5, 2, 17, -2.0), "barnacle")
    return m


def mob_faces2(geo_game, tex, gl, spear=None, socket="socket_main", spear_pose=None):
    """珊瑚憑きの面 + 受け口に付けた銛の面。銛は別の geo・別のテクスチャのまま、受け口の傾きを足す。"""
    faces = vx.load_geo(geo_game, tex, gl)
    if spear is None:
        return faces
    sp_geo, sp_tex, sp_gl = spear
    g = copy.deepcopy(geo_game)
    gb = g["minecraft:geometry"][0]
    p = next(b["pivot"] for b in gb["bones"] if b["name"] == socket)
    for b in gb["bones"]:
        b["cubes"] = []
    sb_ = copy.deepcopy(sp_geo)["minecraft:geometry"][0]["bones"]
    for b in sb_:
        b["name"] = "spear_" + b["name"]
        b["parent"] = socket if "parent" not in b else "spear_" + b["parent"]
        b["pivot"] = [a + c for a, c in zip(b["pivot"], p)]
        for c in b.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
    gb["bones"] += sb_
    pose = {socket: spear_pose} if spear_pose else None
    return faces + vx.load_geo(g, sp_tex, sp_gl, pose=pose)


THRALL_VIEWS = [("front34", 205, 18), ("side", 270, 8), ("back34", 25, 18)]


def run_thrall(spear_r):
    folder = MOBS / "coral_thrall"
    sp = (spear_r["geo"], spear_r["tex"], spear_r["gl"])
    sp_bleached = (spear_r["geo"], tm.bleach(spear_r["tex"]), spear_r["gl"])
    cols = []
    labels = {"a": "A 細身・珊瑚の冠", "b": "B 重装・脳珊瑚", "c": "C 前かがみ・イソギンチャク"}
    for opt in "abc":
        r = finish_export(thrall2(opt), folder / opt)
        report(f"coral_thrall/{opt}", r)
        rows = []
        for name, yaw, pitch in THRALL_VIEWS:
            img = tm.game_render(mob_faces2(r["geo"], r["tex"], r["gl"], sp, spear_pose=(0, 0, 10)), yaw, pitch)
            img.save(folder / opt / f"render_{name}.png")
            rows.append(img)
        statue = tm.game_render(mob_faces2(r["geo"], tm.bleach(r["tex"]), r["gl"], sp_bleached, spear_pose=(0, 0, 10)), 205, 10)
        statue.save(folder / opt / "render_statue.png")
        rows.append(statue)
        cols.append((labels[opt], rows))
    vx.sheet("coral_thrall ラフ 第2版 (珊瑚憑き + 珊瑚の銛)", cols,
             note="行: 正面3/4 / 横 / 背面3/4 / 石膏化 (潮が満ちて凍った姿)。銛は約1.9ブロック、少し傾けて持つ").save(REVIEW / "coral_thrall.png")
    return cols


# ---------------------------------------------------------------- 磯走り

def leg_knee(m, lb, sx, z0, z1, mat="crab_dk"):
    """脚 1 本: 腿 (外へ)・膝の瘤 (上へ)・すね (下へ)・足。0.5 単位の段で折れる。"""
    sb(m, lb, sx, 5, 6.5, 3, 4, z0, z1, mat)
    sb(m, lb, sx, 6.5, 7.5, 4, 5.5, z0, z1, "crab")      # 膝
    sb(m, lb, sx, 7.5, 8.5, 0.5, 4, z0, z1, mat)          # すね
    sb(m, lb, sx, 7.5, 9.5, 0, 0.5, z0, z1, mat)          # 足先


def claw_pincer(m, cb, sx, zf, mat="crab"):
    """はさみ: 腕 (手首 2 段) と、上下に 0.5 のすき間を空けた 2 枚のはさみ。"""
    sb(m, cb, sx, 3.5, 5.5, 3, 4.5, zf, zf + 2, mat)
    sb(m, cb, sx, 3.5, 5.5, 2.5, 4.5, zf + 2, zf + 3, mat)
    sb(m, cb, sx, 4, 6.5, 4, 5, zf + 3, zf + 5.5, mat)            # 上のはさみ
    sb(m, cb, sx, 4, 6.5, 2.5, 3.5, zf + 3, zf + 5.5, "crab_dk")  # 下のはさみ (すき間 0.5)


def scuttler2(opt: str):
    m = tm.Model(f"reef_scuttler_{opt}")
    m.bone("body", (0, 3, 0))
    m.bone("leg_r", (-5, 3, 0), "body")
    m.bone("leg_l", (5, 3, 0), "body")
    m.bone("claw_r", (-4, 3, 4), "body")
    m.bone("claw_l", (4, 3, 4), "body")
    if opt == "a":
        zslots = ((-3, -2), (-0.5, 0.5), (2, 3))
        m.box("body", -5, 3, -4, 5, 5.5, 4, "crab")
        m.box("body", -4.5, 5.5, -3.5, 4.5, 6, 3.5, "crab")
        m.box("body", -2, 6, -3, 2, 7, 0, "ruin")          # 割れた瓦の小さな塔
        fbz = 4.0
        for a, b in ((-3.5, -1.5), (1.5, 3.5)):
            fb(m, "body", (a, 6, -3, b, 6.5, -1), "ruin")  # 瓦の欠け
        top_front = 3.5
    elif opt == "b":
        zslots = ((-3, -2), (-0.5, 0.5), (2, 3))
        m.box("body", -4, 3, -3, 4, 5.5, 3, "crab")
        m.box("body", -3.5, 5.5, -2.5, 3.5, 6, 2.5, "crab")
        m.box("body", -3.5, 2.5, -8, 3.5, 5.5, -3, "clay")  # 壺の胴
        m.box("body", -3, 5.5, -7.5, 3, 6.5, -3.5, "clay")  # 壺の肩
        m.box("body", -3.5, 6.5, -8, 0, 7, -3, "clay")      # 壺の縁 (左)
        m.box("body", 0.5, 6.5, -8, 3.5, 7, -6, "clay")     # 壺の縁 (右、欠けている)
        fbz = 3.0
        top_front = 2.5
    else:
        zslots = ((-3, -2.5), (-1.5, -1), (0, 0.5), (1.5, 2))
        m.box("body", -5, 3, -4, 5, 5.5, 4, "crab")
        for x0, z0 in ((-2.5, -2), (-0.5, -2), (1.5, -2), (-1.5, 0), (0.5, 0), (2.5, 0), (-2.5, 2), (-0.5, 2), (1.5, 2)):
            m.box("body", x0, 5.5, z0, x0 + 0.5, 6.5, z0 + 0.5, "crab_dk")  # 棘
        m.box("body", -2, 5.5, 0.5, -1, 6, 1.5, "kelp")     # 海藻の房
        m.box("body", 1, 5.5, -0.5, 2, 6, 0.5, "kelp")
        fbz = 4.0
        top_front = 3.5

    # 目の柄と目 (光る)
    top_y = 5.5 if opt == "c" else 6
    eye_base = 7.5 if opt == "c" else 8.5
    for sx in (-1, 1):
        sb(m, "body", sx, 1.5, 2.5, top_y, eye_base, top_front - 1, top_front, "crab")
        sb(m, "body", sx, 1.5, 2.5, eye_base, eye_base + 1, top_front - 1, top_front, "seaglass")

    # 脚 (膝で折れる)
    for sx in (-1, 1):
        lb = "leg_r" if sx < 0 else "leg_l"
        for z0, z1 in zslots:
            leg_knee(m, lb, sx, z0, z1)

    # はさみ (前の両角から前へ)
    for sx in (-1, 1):
        cb = "claw_r" if sx < 0 else "claw_l"
        claw_pincer(m, cb, sx, fbz)
    if opt == "b":
        m.glowfn = lambda X, Y, Z, f, mat: mat == "clay" and f == "up" and -0.5 <= X <= 0.5 and Z <= -6
    return m


# ---------------------------------------------------------------- 泡吹き

def puffer2(kind: str, s: float):
    """魚 1 匹。前は +z、目は前の面、ひれと尾は薄い板 (0.5 単位)。
    s = 1 が通常、s = 1.6 が膨らみ (体も棘も 1.6 倍まで膨らむ)。"""
    m = tm.Model(f"bubble_puffer_{kind}_{'x' if s > 1 else 'n'}")
    m.bone("body", (0, 0, 0))
    rx, ry, rz = (6.0 * s, 5.5 * s, 6.5 * s) if kind != "b" else (6.0 * s, 5.0 * s, 6.0 * s)
    cy = ry + 1.0
    c = (0.0, cy, 0.0)
    L = 1.0 if s <= 1.0 else 1.6   # 棘の長さ (通常は短く平ら、膨らみは約 1.6 倍)
    eye_z = rz
    eye_y = cy + 0.5 * s

    # ---- 体 (1 単位の格子)
    if kind == "a":        # まるい棘玉
        body_pred = ellip(c, (rx, ry, rz))
        body_mat = lambda p: "belly" if p[1] < cy - 1.5 * s else "skin"
    elif kind == "b":      # 箱型の鎧 (角の丸い箱: 指数 4 の超楕円)
        def body_pred(x, y, z):
            return (abs(x) / rx) ** 4 + (abs(y - cy) / ry) ** 4 + (abs(z) / rz) ** 4 <= 1.0
        body_mat = lambda p: "coral_o"
    else:                  # 月明かりのクラゲ (鐘形。下は裾が開く)
        body_pred = lambda x, y, z: ((x / rx) ** 2 + ((y - cy) / ry) ** 2 + (z / rz) ** 2 <= 1.0) and y >= cy - 1.5 * s
        body_mat = lambda p: "belly" if p[1] > cy + 0.3 * ry else "tendril"
    bbox = (-math.ceil(rx) - 1, 0, -math.ceil(rz) - 1, math.ceil(rx) + 1, math.ceil(cy + ry) + 1, math.ceil(rz) + 1)
    fill(m, "body", body_pred, None, bbox, sz=2, mat_fn=body_mat)

    # ---- 体の板目 (B は 0.5 の外側の六角の板を貼る)
    if kind == "b":
        def shell(x, y, z):
            def f(k):
                return (abs(x) / (rx * k)) ** 4 + (abs(y - cy) / (ry * k)) ** 4 + (abs(z) / (rz * k)) ** 4
            return f(1.0 + 0.5 / rx) <= 1.0 and f(1.0) > 1.0 and ((math.floor(x / 3.5) + math.floor((y - cy) / 3.5) + math.floor(z / 3.5)) % 2 == 0)
        fill(m, "body", shell, "coral_o", bbox, sz=1)
        # 板の縁の暗い線は絵で付ける
    # ---- 棘 (A のみ広く。B と C は少なめ)
    if kind in ("a",):
        dirs = fib(56)

        def spines(x, y, z):
            p = (x, y - cy, z)
            for d in dirs:
                rs = 1.0 / math.sqrt((d[0] / rx) ** 2 + (d[1] / ry) ** 2 + (d[2] / rz) ** 2)
                t = p[0] * d[0] + p[1] * d[1] + p[2] * d[2]
                perp = math.sqrt(max(0.0, sum(v * v for v in p) - t * t))
                if rs - 0.6 <= t <= rs + L and perp <= 0.4:
                    return True
            return False
        fill(m, "body", spines, "bone", bbox, sz=1)

    # ---- 背びれ (薄い板、x 方向は 1 単位の厚み)
    def dorsal(x, y, z):
        if abs(x) > 0.5:
            return False
        hf = (1.5 if z < 0 else 1.0) * s
        if not (-3.0 * s <= z <= 1.5 * s):
            return False
        return cy + ry - 1.0 <= y <= cy + ry + hf
    fill(m, "body", dorsal, "skin" if kind != "b" else "coral_o", (-1, cy, -3.5 * s, 1, cy + ry + 2 * s, 2 * s), sz=1)

    # ---- 胸びれ (横に出る薄い板。y 方向は 0.5)
    yc = math.floor(cy - 1.5 * s) + 0.25

    def pec(x, y, z):
        return 4.0 * s <= abs(x) <= rx + 1.5 * s and abs(y - yc) < 0.1 and -1.0 * s <= z <= 2.5 * s
    fill(m, "body", pec, "skin" if kind != "b" else "coral_o", (-rx - 2 * s, yc - 1, -2 * s, rx + 2 * s, yc + 1, 3 * s), sz=1)

    # ---- しっぽのひれ (薄い板。後ろへ広がる)
    def tail(x, y, z):
        if abs(x) > 0.5:
            return False
        zb = -rz + 0.5
        if not (zb - 3.5 * s <= z <= zb + 0.5):
            return False
        t = (zb - z) / (3.5 * s)
        return abs(y - cy) <= 1.5 * s + 2.0 * s * t
    fill(m, "body", tail, "skin" if kind != "b" else "coral_o", (-1, cy - 4 * s, -rz - 4 * s, 1, cy + 4 * s, -rz + 1), sz=1)

    # ---- 目・口 (前の面) と光る部分
    if kind == "a":
        eyes = ((-3.5 * s, -1.5 * s), (1.5 * s, 3.5 * s))
        eye_rect = [(a, b, eye_y - 1.5 * s, eye_y + 1.0 * s) for a, b in eyes]
        glint = [(a + 0.3 * s, a + 0.8 * s, eye_y + 0.2 * s, eye_y + 0.8 * s) for a, _ in eyes]
    elif kind == "b":
        eye_rect = [(-4.5 * s, -1.5 * s, eye_y - 1.0 * s, eye_y + 1.0 * s), (1.5 * s, 4.5 * s, eye_y - 1.0 * s, eye_y + 1.0 * s)]
        glint = []
    else:
        eye_rect = []
        glint = []

    def in_rects(rects, X, Y):
        return any(a <= X <= b and c_ <= Y <= d for a, b, c_, d in rects)

    def mouth(X, Y):
        return -1.0 <= X <= 1.0 and cy - 2.5 * s <= Y <= cy - 2.0 * s

    def extra(X, Y, Z, f, mat):
        if f != "south":
            return None
        if kind == "c":
            if abs(X) <= 1.5 * s and cy - 0.5 <= Y <= cy + 0.5 and Z > 0.5 * rz:
                # 三つの目 (C) は光る色で描く
                return RAMP["seaglass"][2]
        if in_rects(eye_rect, X, Y):
            if kind == "a" and in_rects(glint, X, Y):
                return RAMP["seaglass"][3]
            return RAMP["dark"][0]
        if mouth(X, Y):
            return RAMP["dark"][0]
        return None

    def glow(X, Y, Z, f, mat):
        if kind == "c":
            if mat == "belly" and f == "up":
                return True
            if f == "south" and abs(X) <= 1.5 * s and cy - 0.5 <= Y <= cy + 0.5 and Z > 0.5 * rz:
                return True
        if kind == "a" and f == "south" and in_rects(glint, X, Y):
            return True
        return False
    m.extra, m.glowfn = extra, glow

    # ---- 膨らみ B: 泡の袋 (下の腹の前、光る)
    if kind == "b":
        sc = (0.0, cy - 2.5 * s, rz - 0.5 * s)
        fill(m, "body", ellip(sc, (3.0 * s, 3.0 * s, 3.0 * s)), "seaglass", bb_of(sc, (3.0 * s, 3.0 * s, 3.0 * s)), sz=2)

    # ---- 膨らみ C: 触手 (底から垂れる。先が光る)
    if kind == "c":
        ybot = cy - 1.5 * s
        for ang in range(0, 360, 45):
            r = 3.5 * s
            tx = math.floor(r * math.cos(math.radians(ang)) * 2) / 2 + 0.25
            tz = math.floor(r * math.sin(math.radians(ang)) * 2) / 2 + 0.25
            Ln = (9 + (ang // 45) % 3 * 2) * s
            fill(m, "body", lambda x, y, z, tx=tx, tz=tz, Ln=Ln, ybot=ybot: abs(x - tx) < 0.1 and abs(z - tz) < 0.1 and ybot - Ln <= y <= ybot,
                 "tendril", (tx - 0.5, ybot - Ln - 0.5, tz - 0.5, tx + 0.5, ybot + 0.5, tz + 0.5), sz=1,
                 mat_fn=lambda p, ybot=ybot, Ln=Ln: "seaglass" if p[1] < ybot - Ln + 2.0 else "tendril")
    return m


def run_puffer():
    folder = MOBS / "bubble_puffer"
    cols = []
    labels = {"a": "A まるい棘玉", "b": "B 箱型の鎧と泡袋", "c": "C 月明かりのクラゲ混じり"}
    for kind in "abc":
        r = finish_export(puffer2(kind, 1.0), folder / kind)
        report(f"bubble_puffer/{kind} (通常)", r)
        ri = finish_export(puffer2(kind, 1.6), folder / kind / "inflated")
        report(f"bubble_puffer/{kind} (膨らみ)", ri)
        rows = []
        for name, yaw, pitch, rr, sub in (("front34", 205, 18, r, folder / kind),
                                          ("side", 270, 8, r, folder / kind),
                                          ("inflated_front34", 205, 18, ri, folder / kind / "inflated"),
                                          ("inflated_side", 270, 8, ri, folder / kind / "inflated")):
            img = tm.game_render(vx.load_geo(rr["geo"], rr["tex"], rr["gl"]), yaw, pitch)
            sub.mkdir(parents=True, exist_ok=True)
            img.save(sub / f"render_{name.replace('inflated_', '')}.png")
            rows.append(img)
        cols.append((labels[kind], rows))
    vx.sheet("bubble_puffer ラフ 第2版 (泡吹き: 通常と膨らみ)", cols,
             note="行: 通常の正面3/4 / 通常の横 / 膨らみ (体 1.6 倍、棘も 1.6 倍) の正面3/4 / 膨らみの横").save(REVIEW / "bubble_puffer.png")
    return cols


def run_scuttler():
    folder = MOBS / "reef_scuttler"
    cols = []
    labels = {"a": "A 瓦の殻", "b": "B 壺を背負う", "c": "C 棘のクモガニ"}
    for opt in "abc":
        r = finish_export(scuttler2(opt), folder / opt)
        report(f"reef_scuttler/{opt}", r)
        rows = []
        views = [("front34", 205, 18), ("side", 270, 8), ("back34", 25, 18), ("top", 205, 70)]
        for name, yaw, pitch in views:
            img = tm.game_render(vx.load_geo(r["geo"], r["tex"], r["gl"]), yaw, pitch)
            img.save(folder / opt / f"render_{name}.png")
            rows.append(img)
        if opt == "a":  # 裏返し (背中を下に、腹を上に)
            img = tm.game_render(vx.load_geo(r["geo"], r["tex"], r["gl"], pose={"body": (0, 0, 180)}), 205, 25)
            img.save(folder / opt / "render_flipped.png")
            rows.append(img)
        cols.append((labels[opt], rows))
    vx.sheet("reef_scuttler ラフ 第2版 (磯走り)", cols,
             note="行: 正面3/4 / 横 / 背面3/4 / 真上 / (A のみ) 裏返し。膝で折れる脚と、0.5 のすき間のあるはさみ").save(REVIEW / "reef_scuttler.png")
    return cols


def run_spear():
    folder = MOBS / "coral_spear"
    r = finish_export(build_spear(), folder, root=False)
    report("coral_spear", r)
    return r


def main():
    spear_r = run_spear()
    run_thrall(spear_r)
    run_scuttler()
    run_puffer()


if __name__ == "__main__":
    main()
