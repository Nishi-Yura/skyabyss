"""雷獣（らいじゅう・中ボス）のラフ 3 案。雷翼の巨神の右の手首の雷の節に住む、四足の獣。

実行: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/rough/thunder_raiju.py [a|b|c]
出力:
  docs/review/thunder_wing_roughs/raiju/<a|b|c>/ (model.geo.json, model.tex.png, model.glow.png, 確認の絵 *.png)
  docs/review/thunder_wing_roughs/raiju/stats.json
  docs/review/thunder_wing_roughs/raiju.png (3 案の比較シート。引数なしの実行で作る)

座標 (ラフと同じ決まり): y が上、床は y=0、前は +Z、1 単位 = 1/16 ブロック。
体長は鼻先から尾の付け根まで約 72 単位、肩の高さ約 34 単位。
+X 側の脚は名前が _l (ゲームの左)、-X 側は _r (ゲームの右) (潮招きと同じ決まり)。
骨は根 (root) > 胴 (body) > 胸 (chest) > 頭 (head) > 顎 (jaw) と、四本の脚 (上の節 + 下の節 _lo)、尾 (tail, tail_2, tail_3)。
ポーズ (跳躍) は書き出しに入れず、描画だけで骨を回す (load_geo の pose)。
乱数は seed 固定。同じ入力は同じ箱になる。

方針 (第 1 周の見直し):
- 大きな体積 (胴・胸・腿・太い脚) は 2 単位 (4 セル) か 1 単位 (2 セル) の段に揃えて箱を減らす。
- 0.5 単位のセルは、顔の細部 (眉・瞳・鼻・牙・耳の先)、爪、鬣の先、尾の光る芯だけに使う。
- 頭は共通の部品 (head) で作り、頭の形だけ案ごとに変える。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import thunder_common as tc  # noqa: E402
import voxel as vx  # noqa: E402
from thunder_common import (  # noqa: E402
    ASH_W, ASH_W_D, BONE, BOLT, BOLT_B, BOLT_Y, COPPER, COPPER_D, HORN, REVIEW, SLATE, STORM, STORM_D, STORM_L,
    Grid, by_height, draw, ell, export, faces_of, night, player_ref, ring, save, tube, write_stats,
)

OUT = REVIEW / "raiju"
SHEET = REVIEW / "raiju.png"
OPTIONS = ("a", "b", "c")
OPTION_NAME = {"a": "A 狼型", "b": "B 鼬型", "c": "C 猫型と雷太鼓"}
LO = (-30.0, 0.0, -72.0)
HI = (30.0, 64.0, 50.0)
S = (1.0, -1.0)            # +1 = _l (+X), -1 = _r (-X)
FLIP = Image.FLIP_LEFT_RIGHT
VIEW34 = (-205, 18)
VIEW_SIDE = (-270, 8)
VIEW_FRONT = (-180, 6)
VIEW_POUNCE = (-205, 14)
ROWS = ("3/4 view", "side", "front", "pounce / leap", "night")
PLAYER_AT = (-46.0, -6.0)  # 大きさの目安 (夜の絵だけ)


# ---------------------------------------------------------------- 書く部品 (格子へ)

def fur(g):
    """体の毛色: 腹は白っぽく、背は嵐の灰青、背の上は少し明るい"""
    return by_height(g, [(14, ASH_W_D), (24, STORM), (36, STORM_L)])


def coarsen(g, m, k=2):
    """k セル (k/2 単位) の段に揃える。セルの多数決で塊を塗る。端は足して切る。"""
    nx, ny, nz = g.n
    M = np.pad(m, ((0, (-nx) % k), (0, (-ny) % k), (0, (-nz) % k)))
    Nx, Ny, Nz = M.shape
    B = M.reshape(Nx // k, k, Ny // k, k, Nz // k, k)
    on = B.mean(axis=(1, 3, 5)) >= 0.5
    out = np.repeat(np.repeat(np.repeat(on, k, 0), k, 1), k, 2)
    return out[:nx, :ny, :nz]


def blob(g, m, r):
    """半径 r (単位) の塊の段: 4.5 以上は 2 単位、1.5 以上は 1 単位、細い物は 0.5 単位のまま"""
    rm = float(np.min(r))
    if rm >= 4.5:
        return coarsen(g, m, 4)
    if rm >= 1.5:
        return coarsen(g, m, 2)
    return m


def E(g, name, c, r, col, glow=False, over=False):
    return g.put(name, blob(g, ell(g, c, r), r), col, glow=glow, over=over)


def T(g, name, pts, r, col, glow=False, over=False):
    return g.put(name, blob(g, tube(g, pts, r), r), col, glow=glow, over=over)


def BX(g, name, x0, y0, z0, x1, y1, z1, col, glow=False, over=False):
    lo = (min(x0, x1), min(y0, y1), min(z0, z1))
    hi = (max(x0, x1), max(y0, y1), max(z0, z1))
    return g.put(name, tc.box_m(g, lo, hi), col, glow=glow, over=over)


def stripe(g, base, hi, period, w):
    """縞 (稲妻の帯): z と x を混ぜた斜めの帯。帯の中は hi、その他は base"""
    base = np.array(base, float)
    hi = np.array(hi, float)

    def f(m):
        z = g.Z[m] + 0.8 * g.X[m]
        sel = np.mod(z, period) < w
        out = np.where(sel[:, None], hi[None], base[None])
        return out * g.noise[m][:, None]
    return f


def top_at(z, ells):
    """z の所で、楕円体の列の一番上の高さ (背の面)"""
    best = 0.0
    for (cx, cy, cz), (rx, ry, rz) in ells:
        t = (z - cz) / rz
        if abs(t) < 1:
            best = max(best, cy + ry * math.sqrt(1 - t * t))
    return best


def spikes(g, name, zs, ells, base_col, tip_col, glow_every=2, hw=1.4, hgt=2.2):
    """背の鬣 (棘)。先端を光らせる (夜の目印)。根元は体の中に埋まる"""
    for i, z in enumerate(zs):
        top = top_at(z, ells)
        BX(g, name, -0.7, top + 2.0, z - 0.7, 0.7, top + 2.0 + hgt, z + 0.7, tip_col,
           glow=(i % glow_every == 0))
        BX(g, name, -hw, top - 1.0, z - hw, hw, top + 2.0, z + hw, base_col)


def head(g, cy, cz, sc=1.0, muzzle=9.0, ear_h=7.0, col=None, dark=STORM_D, jaw_drop=2.6):
    """頭 (共通部品)。頭の中心 (cy, cz)、鼻先へ +Z。
    顔: 太い頬骨の額、眉の張り出し、窪みの中に光る瞳、短い鼻、顎と牙。"""
    fu = col
    E(g, "head", (0, cy, cz), (5.2 * sc, 4.8 * sc, 5.2 * sc), fu)                     # 頭蓋
    E(g, "head", (0, cy - 0.5, cz + 2.8 * sc), (3.6 * sc, 3.4 * sc, 3.2 * sc), fu)    # 頬
    # 鼻づら (上の顎): 1 単位の段
    BX(g, "head", -2.4 * sc, cy - 2.6 * sc, cz + 3.0 * sc, 2.4 * sc, cy + 1.6 * sc, cz + 3.0 * sc + muzzle, fu, over=True)
    # 眉の張り出し (額の骨)
    BX(g, "head", -4.8 * sc, cy + 2.6 * sc, cz + 0.2 * sc, 4.8 * sc, cy + 3.8 * sc, cz + 3.6 * sc, dark, over=True)
    # 鼻 (黒い先)
    BX(g, "head", -1.0, cy - 0.5, cz + 3.0 * sc + muzzle, 1.0, cy + 1.0, cz + 3.8 * sc + muzzle, HORN, over=True)
    for s in S:
        # 窪みと瞳 (前の面に彫る。瞳は光る)
        BX(g, "head", s * 1.0 * sc, cy + 0.6 * sc, cz + 4.3 * sc, s * 4.0 * sc, cy + 2.0 * sc, cz + 5.2 * sc, SLATE, over=True)
        BX(g, "head", s * 2.0 * sc, cy + 0.9 * sc, cz + 4.6 * sc, s * 3.2 * sc, cy + 1.7 * sc, cz + 5.4 * sc, BOLT_Y, glow=True, over=True)
        # 耳 (先は青白く光る)
        T(g, "head", [(s * 3.2 * sc, cy + 3.8 * sc, cz - 0.6), (s * 3.8 * sc, cy + ear_h, cz - 1.2)], [2.2, 0.9], dark)
        BX(g, "head", s * 3.4 * sc - 0.5, cy + ear_h, cz - 1.7, s * 3.4 * sc + 0.5, cy + ear_h + 0.5, cz - 0.7, BOLT_B, glow=True)
    # 顎 (下顎の箱、牙は 0.5 単位で、先が外へ出る)
    BX(g, "jaw", -2.0 * sc, cy - 2.6 * sc - jaw_drop, cz + 3.0 * sc, 2.0 * sc, cy - 1.0 * sc - jaw_drop, cz + 3.0 * sc + muzzle - 1.0, dark, over=True)
    for s in S:
        BX(g, "jaw", s * 1.2 - 0.5, cy - 1.0 * sc - jaw_drop - 0.5, cz + 3.0 * sc + muzzle - 3.5,
           s * 1.2 + 0.5, cy - 1.0 * sc - jaw_drop + 0.5, cz + 3.0 * sc + muzzle - 2.0, BONE, over=True)  # 牙
        BX(g, "jaw", s * 0.6 - 0.5, cy - 2.2 * sc, cz + 3.0 * sc + muzzle - 1.5,
           s * 0.6 + 0.5, cy - 1.2 * sc, cz + 3.0 * sc + muzzle - 0.5, BONE, over=True)  # 上の牙 (下顎の内側)


def paw(g, name, x, z, col=SLATE):
    """足先: 厚い掌、三本の指、爪 (先は光る)"""
    E(g, name, (x, 2.8, z), (4.2, 2.6, 4.2), col)
    for dx in (-1.6, 0.0, 1.6):
        BX(g, name, x + dx - 0.6, 0.0, z + 2.6, x + dx + 0.6, 1.6, z + 4.4, col, over=True)     # 指
        BX(g, name, x + dx - 0.5, 0.0, z + 4.4, x + dx + 0.5, 1.0, z + 5.0, BOLT_B, glow=True, over=True)  # 爪 (光る)


def tail_seg(g, name, pts, rs, shell_col):
    """尾の一区間。稲妻の芯 (光る) を先に置き、その外を毛が包む"""
    core_r = [max(0.6, min(0.9, r * 0.4)) for r in rs]
    T(g, name, pts, core_r, BOLT, glow=True)
    T(g, name, pts, rs, shell_col)


def pounce_pose():
    """跳躍の姿勢 (描画だけ): 体は前上がり、前脚は前へ伸ばし、後脚は後ろへ蹴り、尾は上へ。"""
    return {
        "body": (-16, 0, 0), "chest": (-6, 0, 0), "head": (14, 0, 0), "jaw": (-8, 0, 0),
        "leg_fl": (-40, 0, 0), "leg_fl_lo": (-20, 0, 0), "leg_fr": (-40, 0, 0), "leg_fr_lo": (-20, 0, 0),
        "leg_bl": (51, 0, 0), "leg_bl_lo": (25, 0, 0), "leg_br": (51, 0, 0), "leg_br_lo": (25, 0, 0),
        "tail": (51, 0, 0), "tail_2": (-10, 0, 0), "tail_3": (-10, 0, 0),
    }


def common_parents():
    par = {"body": "root", "chest": "body", "head": "chest", "jaw": "head", "mane": "body",
           "plates": "body", "drum": "body",
           "tail": "body", "tail_2": "tail", "tail_3": "tail_2"}
    for s in ("fl", "fr", "bl", "br"):
        par[f"leg_{s}"] = "body"
        par[f"leg_{s}_lo"] = f"leg_{s}"
    return par


def legs_front(g, fu, dark, hipx, hip_y, knee_y, ank_y, z_hip, z_knee, z_paw):
    """前脚: 肩 (太い上腕)、肘 (張る)、手首、掌と指。肘は少し外へ張る。"""
    for s in S:
        f = "l" if s > 0 else "r"
        hip, knee, ank = (s * hipx, hip_y, z_hip), (s * hipx, knee_y, z_knee), (s * hipx, ank_y, z_knee + 2.5)
        T(g, f"leg_f{f}", [hip, knee], [5.0, 4.0], dark)
        E(g, f"leg_f{f}_lo", knee, (4.2, 3.8, 4.2), dark)
        T(g, f"leg_f{f}_lo", [knee, ank], [3.6, 3.0], dark)
        paw(g, f"leg_f{f}_lo", s * hipx, z_paw)


def legs_back(g, fu, dark, hipx, hip_y, knee_y, ank_y, z_hip, z_knee, z_paw):
    """後脚: 大きな腿 (fu)、膝は後ろへ折れる、踵、掌"""
    for s in S:
        f = "l" if s > 0 else "r"
        E(g, f"leg_b{f}", (s * hipx, hip_y, z_hip), (5.6, 7.0, 6.2), fu)
        T(g, f"leg_b{f}_lo", [(s * hipx, hip_y - 2, z_hip + 2), (s * hipx, knee_y, z_knee)], [4.4, 3.6], dark)
        T(g, f"leg_b{f}_lo", [(s * hipx, knee_y, z_knee), (s * hipx, ank_y, z_paw + 2.0)], [3.4, 2.8], dark)
        paw(g, f"leg_b{f}_lo", s * hipx, z_paw)


# ---------------------------------------------------------------- A 狼型

def build_a():
    g = Grid(LO, HI, seed=21)
    g.lid("root")
    fu = fur(g)
    dark = STORM_D
    chest = ((0, 27, 14), (9.5, 9.5, 9))
    spine = [((0, 25, 0), (8.5, 8.5, 10)), ((0, 23, -16), (8, 8, 9)), ((0, 24, -24), (7.5, 7.5, 6.5))]
    neck = ((0, 30, 24), (6.5, 6.5, 5.5))
    E(g, "chest", *chest, fu)
    for c, r in spine:
        E(g, "body", c, r, fu)
    T(g, "chest", [(0, 28, 18), (0, 32, 27)], [6.5, 5.8], fu)
    ells = [chest] + spine + [neck]

    head(g, cy=38, cz=34, sc=1.0, muzzle=8.0, ear_h=8.0, col=fu, dark=dark, jaw_drop=2.0)
    spikes(g, "mane", [22, 18, 14, 10, 6, 2, -2, -6, -10, -14, -18, -22], ells, dark, BOLT_B, glow_every=2)

    legs_front(g, fu, dark, hipx=6.5, hip_y=27, knee_y=13, ank_y=5.0, z_hip=15, z_knee=16, z_paw=20.5)
    legs_back(g, fu, dark, hipx=6.5, hip_y=19.5, knee_y=5.0, ank_y=2.5, z_hip=-12.5, z_knee=-10, z_paw=-13.0)

    P = [(0, 26, -30), (2, 31, -38), (-2, 24, -46), (3, 31, -54), (0, 25, -62), (-1, 31, -69)]
    R = [3.6, 3.0, 2.2, 1.7, 1.2, 1.0]
    tail_seg(g, "tail", P[0:3], R[0:3], STORM)
    tail_seg(g, "tail_2", P[2:5], R[2:5], STORM)
    tail_seg(g, "tail_3", P[4:6], R[4:6], STORM_D)

    pv = {"root": (0, 0, 0), "body": (0, 24, 0), "chest": (0, 26, 14), "head": (0, 36, 30),
          "jaw": (0, 34, 36), "mane": (0, 24, 0),
          "tail": P[0], "tail_2": P[2], "tail_3": P[4]}
    for s in S:
        f = "l" if s > 0 else "r"
        pv[f"leg_f{f}"] = (s * 6.5, 27, 15)
        pv[f"leg_f{f}_lo"] = (s * 6.5, 13, 16)
        pv[f"leg_b{f}"] = (s * 6.5, 19.5, -12.5)
        pv[f"leg_b{f}_lo"] = (s * 6.5, 5.0, -10)
    return g, pv, common_parents()


# ---------------------------------------------------------------- B 鼬型 (長く低い、背骨がうねる)

def build_b():
    g = Grid(LO, HI, seed=22)
    g.lid("root")
    fu = fur(g)
    dark = STORM_D
    spine = [((0, 25, -30), (5.5, 5.5, 5.5)), ((1.5, 24.5, -20), (6.5, 6.0, 6.0)),
             ((-1, 24, -10), (7, 6.2, 6.5)), ((-2, 25, 0), (7.5, 6.5, 7)),
             ((0.5, 26.5, 10), (7.5, 6.8, 7)), ((2, 27, 19), (6.8, 6.5, 6.5))]
    for i, (c, r) in enumerate(spine):
        E(g, "body" if i < 4 else "chest", c, r, fu)

    head(g, cy=30, cz=28, sc=0.9, muzzle=10.0, ear_h=6.5, col=fu, dark=dark, jaw_drop=2.0)
    ells = [c_r for c_r in spine]
    for z in np.arange(-27.0, 24.0, 3.5):
        top = top_at(z, ells)
        BX(g, "plates", -0.7, top - 1.0, z - 0.8, 0.7, top + 2.0, z + 0.8, ASH_W)

    legs_front(g, fu, dark, hipx=5.5, hip_y=27, knee_y=15, ank_y=4.5, z_hip=15, z_knee=16, z_paw=21.0)
    legs_back(g, fu, dark, hipx=5.5, hip_y=24, knee_y=4.5, ank_y=2.5, z_hip=-14, z_knee=-10, z_paw=-14.0)

    P = [(0, 25, -28), (0, 24, -40), (3, 22, -50), (0, 24, -58), (-2, 27, -66)]
    R = [4.6, 4.2, 3.4, 2.6, 1.9]
    E(g, "tail_3", P[4], (1.6, 1.6, 1.6), BOLT_B, glow=True)
    bolt = stripe(g, STORM_D, BOLT_Y, period=6.0, w=1.6)
    T(g, "tail", P[0:3], R[0:3], bolt)
    T(g, "tail_2", P[2:5], R[2:5], bolt)

    pv = {"root": (0, 0, 0), "body": (0, 24, 0), "chest": (0, 26, 12), "head": (0, 29, 26),
          "jaw": (0, 27, 36), "plates": (0, 24, 0), "tail": P[0], "tail_2": P[2], "tail_3": P[4]}
    for s in S:
        f = "l" if s > 0 else "r"
        pv[f"leg_f{f}"] = (s * 5.5, 27, 15)
        pv[f"leg_f{f}_lo"] = (s * 5.5, 15, 16)
        pv[f"leg_b{f}"] = (s * 5.5, 24, -14)
        pv[f"leg_b{f}_lo"] = (s * 5.5, 4.5, -10)
    return g, pv, common_parents()


# ---------------------------------------------------------------- C 猫型と雷太鼓 (しゃがみ、背の上の傾いた太鼓の輪)

def build_c():
    g = Grid(LO, HI, seed=23)
    g.lid("root")
    fu = fur(g)
    dark = STORM_D
    chest = ((0, 25, 12), (9.5, 9.5, 9))
    spine = [((0, 22, -4), (8.5, 8.5, 12)), ((0, 21, -19), (8, 8, 8)), ((0, 22, -26), (6.8, 7.2, 6))]
    E(g, "chest", *chest, fu)
    for c, r in spine:
        E(g, "body", c, r, fu)
    T(g, "chest", [(0, 27, 16), (0, 31, 24)], [6.5, 6.0], fu)

    head(g, cy=32, cz=27, sc=0.95, muzzle=5.0, ear_h=7.5, col=fu, dark=dark, jaw_drop=1.6)

    # 背の太鼓の輪: 水平よりも傾けた輪 (法線 = (0.45, 0.89, 0))。背の上に浮き、輪の中を尾が抜ける。
    C = np.array([0.0, 42.0, -14.0])
    n = np.array([0.45, 0.89, 0.0])
    n = n / np.linalg.norm(n)
    u = np.array([0.0, 0.0, 1.0])
    v = np.cross(n, u)
    v = v / np.linalg.norm(v)
    RR = 11.0
    for k in range(8):
        t = math.radians(k * 45)
        p = C + RR * (math.cos(t) * u + math.sin(t) * v)
        BX(g, "drum", p[0] - 1.6, p[1] - 1.6, p[2] - 1.6, p[0] + 1.6, p[1] + 1.6, p[2] + 1.6, ASH_W)
        BX(g, "drum", p[0] - 0.8, p[1] + 1.6, p[2] - 0.8, p[0] + 0.8, p[1] + 2.4, p[2] + 0.8, BOLT_Y, glow=True)  # 撥 (光る)
    g.put("drum", ring(g, C, n, RR, 2.4, 2.6), COPPER)
    for z in (-25.0, -3.0):
        BX(g, "drum", -1.0, 26.0, z - 1.0, 1.0, 40.0, z + 1.0, COPPER_D)
    E(g, "drum", (0, 35, -14), (1.8, 2.2, 1.8), dark)

    legs_front(g, fu, dark, hipx=6.5, hip_y=26, knee_y=13, ank_y=4.0, z_hip=14, z_knee=15, z_paw=20.5)
    legs_back(g, fu, dark, hipx=6.5, hip_y=20, knee_y=3.6, ank_y=2.0, z_hip=-12, z_knee=-8, z_paw=-12.5)

    # 尾: 芯の後ろから上へ、輪の中心を抜けて、背を回る。白黒の縞。
    P = [(0, 24, -30), (0, 31, -24), (0, 42, -14), (2, 50, -8), (1, 47, 1), (-3, 39, 8), (-4, 33, 12)]
    R = [3.2, 2.8, 2.2, 1.9, 1.6, 1.5, 1.4]
    band = stripe(g, STORM_D, ASH_W, period=4.0, w=1.4)
    E(g, "tail_3", P[6], (1.2, 1.2, 1.2), BOLT_B, glow=True)
    T(g, "tail", P[0:3], R[0:3], band)
    T(g, "tail_2", P[2:5], R[2:5], band)
    T(g, "tail_3", P[4:7], R[4:7], band)

    pv = {"root": (0, 0, 0), "body": (0, 24, 0), "chest": (0, 25, 12), "head": (0, 32, 27),
          "jaw": (0, 30, 34), "drum": (0, 24, 0), "tail": P[0], "tail_2": P[2], "tail_3": P[4]}
    for s in S:
        f = "l" if s > 0 else "r"
        pv[f"leg_f{f}"] = (s * 6.5, 26, 14)
        pv[f"leg_f{f}_lo"] = (s * 6.5, 13, 15)
        pv[f"leg_b{f}"] = (s * 6.5, 20, -12)
        pv[f"leg_b{f}_lo"] = (s * 6.5, 3.6, -8)
    return g, pv, common_parents()


BUILDERS = {"a": build_a, "b": build_b, "c": build_c}


# ---------------------------------------------------------------- 書き出しと描画

def build(opt):
    g, piv, par = BUILDERS[opt]()
    geo, tex, gtex, st = export(g, f"raiju_{opt}", piv, par)
    folder = OUT / opt
    stats = save(folder, "model", geo, tex, gtex, st)
    stats["option"] = opt
    stats["name"] = OPTION_NAME[opt]
    return geo, tex, gtex, stats


def view(faces, yaw_pitch):
    yaw, pitch = yaw_pitch
    return draw(faces, yaw=yaw, pitch=pitch, size=420).transpose(FLIP)


def render_all(opt, geo, tex, gtex, stats):
    folder = OUT / opt
    faces = faces_of(geo, tex, gtex)
    pose = faces_of(geo, tex, gtex, pose=pounce_pose())
    imgs = {
        "view34": view(faces, VIEW34),
        "side": view(faces, VIEW_SIDE),
        "front": view(faces, VIEW_FRONT),
        "pounce": view(pose, VIEW_POUNCE),
    }
    ref = player_ref(x=PLAYER_AT[0], z=PLAYER_AT[1])
    imgs["night"] = night(view(faces + ref, VIEW34))
    for k, im in imgs.items():
        im.save(folder / f"{k}.png")
    return [imgs["view34"], imgs["side"], imgs["front"], imgs["pounce"], imgs["night"]]


def main(argv):
    opts = [a for a in argv if a in OPTIONS] or list(OPTIONS)
    stats_all = {}
    stats_path = OUT / "stats.json"
    if stats_path.exists():
        stats_all = json.loads(stats_path.read_text(encoding="utf-8"))
    cols = {}
    for opt in opts:
        geo, tex, gtex, stats = build(opt)
        assert stats["overlaps"] == 0, (opt, stats["overlaps"])
        imgs = render_all(opt, geo, tex, gtex, stats)
        stats_all[opt] = stats
        cols[opt] = imgs
        print(f"{opt}: 箱 {stats['cubes']}、光る画素 {stats['glow_px']}、重なり {stats['overlaps']}、"
              f"骨 {stats['bones']}、範囲 {stats['size_blocks']}")
    OUT.mkdir(parents=True, exist_ok=True)
    write_stats(stats_path, stats_all)
    if all(o in cols for o in OPTIONS):
        columns = [(f"{OPTION_NAME[o]}  箱 {stats_all[o]['cubes']}", cols[o]) for o in OPTIONS]
        sheet = vx.sheet("雷獣（中ボス）ラフ 3案", columns,
                         note="左から A・B・C。上から 3/4 正面、横、正面、跳躍 (描画だけの姿勢)、夜 (灰色の人は 2 ブロック)。")
        sheet.save(SHEET)
        print(f"sheet: {SHEET}")


if __name__ == "__main__":
    main(sys.argv[1:])
