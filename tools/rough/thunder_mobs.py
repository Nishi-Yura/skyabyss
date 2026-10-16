"""雷翼の巨神の背の敵 3 種のラフ (案 A/B/C) と、その武器 (別モデル)。

- 羽守 (feather_warden): 近接・突撃。鳥の頭骨 / 銅の兜 / 布を巻いた風見の人。武器は鉤槍 (hooked_glaive)。
- 雷蛾 (static_moth): 小型の飛行。灰の大蛾 / 透けた翅 / 毛深い蚕蛾。平常と帯電 (光の強い状態) の 2 状態。
- 雷乞い (thunder_supplicant): 遠隔・詠唱。頭巾 / 避雷針の老人 / 鳥籠の頭。武器は雷の杖 (supplicant_staff、短い変種あり)。

実行 (リポジトリ直下):
    PYTHONIOENCODING=utf-8 python -B tools/rough/thunder_mobs.py [warden|moth|supplicant|weapons|all]

座標: 1 単位 = 1/16 ブロック、y が上、+Z が前。武器は持ち手 (受け口) を原点にした別の格子。
受け口 (socket_main) の傾きは mob の geo の骨の rotation に入れる。武器の geo 自体は直立。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import thunder_common as tc  # noqa: E402
import voxel as vx  # noqa: E402

from thunder_common import (  # noqa: E402
    ASH_W, ASH_W_D, BOLT, BOLT_B, BOLT_Y, BONE, BONE_D, CLOTH, CLOTH_D, COPPER, COPPER_D,
    COPPER_L, EYE_DARK, HORN, SLATE, STORM, STORM_D, STORM_L, Grid, box_m, ell, tube, plate, ring,
)

OUT = tc.REVIEW
MOBS = OUT / "mobs"
WEAP = MOBS / "weapons"
SZ = 380  # 1 枚の絵の大きさ (px)
SAFE = {"3/4": "three_quarter", "dive (突撃)": "dive", "casting (杖を掲げる)": "casting", "charged": "charged"}


# ---------------------------------------------------------------- 小さな部品の書き方

def E(g, name, c, r, color, glow=False, mod=None, over=False):
    return g.put(name, ell(g, c, r, mod), color, glow=glow, over=over)


def T(g, name, pts, r, color, glow=False):
    return g.put(name, tube(g, pts, r), color, glow=glow)


def BX(g, name, xr, yr, zr, color, glow=False, over=False):
    return g.put(name, box_m(g, (xr[0], yr[0], zr[0]), (xr[1], yr[1], zr[1])), color, glow=glow, over=over)


def MX(s, a, b):
    """s=-1 は体の右 (-X) 側、s=+1 は左 (+X) 側。a, b は正の値で書く。"""
    return (min(s * a, s * b), max(s * a, s * b))


def flare(k, top):
    """下へ行くほど広がる (ell の mod)。top は広がり始める高さ"""
    return lambda X, Y, Z: 1.0 + k * np.clip((top - Y) / top, 0, 1)


def fib(n, R=1.0):
    pts = []
    ga = math.pi * (3 - math.sqrt(5))
    for i in range(n):
        y = 1 - 2 * (i + 0.5) / n
        r = math.sqrt(max(0.0, 1 - y * y))
        t = ga * i
        pts.append((R * math.cos(t) * r, R * y, R * math.sin(t) * r))
    return pts


def mask_box(g, xr, yr, zr):
    return box_m(g, (xr[0], yr[0], zr[0]), (xr[1], yr[1], zr[1]))


def export_all(g, ident, pivots, parents, rotations=None):
    """export してから、親子と初期回転を骨に入れる (export は空の骨の親を書かないため)。"""
    geo, tex, gtex, stats = tc.export(g, ident, pivots=pivots, parents=parents)
    bones = geo["minecraft:geometry"][0]["bones"]
    for b in bones:
        if b["name"] in parents:
            b["parent"] = parents[b["name"]]
        if rotations and b["name"] in rotations:
            b["rotation"] = [float(v) for v in rotations[b["name"]]]
    return geo, tex, gtex, stats


def charged_gtex(g_copy, ident, pivots, parents, charge_mask, charge_color, geo_ref):
    """帯電の状態の光の層だけを作る (同じ形なので、アトラスの配置は平常と同じ)。"""
    g2 = copy.deepcopy(g_copy)
    g2.glow[charge_mask] = True
    g2.col[charge_mask] = np.array(charge_color, float)
    _, _, gtex2, _ = tc.export(g2, ident, pivots=pivots, parents=parents)
    return gtex2


def attach_geo(mob_geo, wgeo, socket="socket_main"):
    """武器の骨を、受け口の骨の子として、mob の geo にのせる (mob の箱は空にする)。
    武器の面だけを描くための geo を返す。テクスチャは武器のものを別に渡す。"""
    g = copy.deepcopy(mob_geo)
    gb = g["minecraft:geometry"][0]
    wd = wgeo["minecraft:geometry"][0]
    p = next(b["pivot"] for b in gb["bones"] if b["name"] == socket)
    for b in gb["bones"]:
        b["cubes"] = []
    gb["description"]["texture_width"] = wd["description"]["texture_width"]
    gb["description"]["texture_height"] = wd["description"]["texture_height"]
    for wb in wd["bones"]:
        nb = copy.deepcopy(wb)
        nb["name"] = "weapon_" + wb["name"]
        nb["parent"] = socket if "parent" not in wb else "weapon_" + wb["parent"]
        nb["pivot"] = [a + c for a, c in zip(wb["pivot"], p)]
        for c in nb["cubes"]:
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
        gb["bones"].append(nb)
    return g


def mob_faces(mob, pose=None, gl=None, weapon=None):
    """mob (geo, tex, glow) と武器 (geo, tex, glow) から、描く面の一覧を作る。pose は描画だけの回転。"""
    geo, tex, glow = mob
    gl = glow if gl is None else gl
    fs = vx.load_geo(geo, tex, gl, pose=pose)
    if weapon is not None:
        wgeo, wtex, wgl = weapon
        fs += vx.load_geo(attach_geo(geo, wgeo), wtex, wgl, pose=pose)
    return fs


def views(name_list, faces_fn, ref_x):
    """名前・視点・面の組から画像を作る。night は暗い部屋の見え方 (人の目安を並べる)。"""
    out = []
    for name, yaw, pitch, faces, night in name_list:
        fs = faces_fn(faces)
        if night:
            fs = fs + tc.player_ref(x=ref_x)
            im = tc.night(tc.draw(fs, yaw, pitch, size=SZ))
        else:
            im = tc.draw(fs, yaw, pitch, size=SZ)
        out.append((name, im))
    return out


# ================================================================ 羽守 (feather_warden)

WARDEN_LO, WARDEN_HI = (-14.0, 0.0, -12.0), (14.0, 40.0, 12.0)
WARDEN_BONES = ["body", "head", "cloak_l", "cloak_r", "arm_r", "forearm_r", "socket_main", "arm_l",
                "forearm_l", "leg_r", "leg_l"]
WARDEN_PIV = {
    "body": (0, 13, 0), "head": (0, 25, 0), "cloak_l": (0.5, 24.5, -3.0), "cloak_r": (-0.5, 24.5, -3.0),
    "arm_r": (-4.5, 24, 0), "forearm_r": (-6.6, 17.5, 1.5), "socket_main": (-6.6, 12.4, 4.3),
    "arm_l": (4.5, 24, 0), "forearm_l": (6.6, 17.5, 0.5), "leg_r": (-2.25, 13, 0), "leg_l": (2.25, 13, 0),
}
WARDEN_PAR = {
    "head": "body", "cloak_l": "body", "cloak_r": "body", "arm_r": "body", "arm_l": "body",
    "forearm_r": "arm_r", "forearm_l": "arm_l", "socket_main": "forearm_r", "leg_r": "body", "leg_l": "body",
}
WARDEN_ROT = {"socket_main": (15, 0, 0)}  # 鉤槍を少し前へ傾けて持つ
WARDEN_CALM = None
WARDEN_DIVE = {"body": (12, 0, 0), "arm_r": (-90, 0, 0), "arm_l": (-90, 0, 0),
               "socket_main": (165, 0, 0), "cloak_r": (0, 0, -80), "cloak_l": (0, 0, 80)}
WARDEN_SHAPE = {"a": dict(body=(4.2, 6.8, 2.8), cloak_w=2.2, gap=3),
                "b": dict(body=(5.0, 7.0, 3.4), cloak_w=3.0, gap=4),
                "c": dict(body=(4.4, 6.8, 3.0), cloak_w=2.4, gap=2)}


def _warden_cloak(g, nm, s, width, gap, tip):
    """羽板の重ね: 2 単位ごとの段 (間に 0.5 のみぞ)、段ごとに前後へ 0.5 ずらし、裾の段は外へ張る"""
    x = MX(s, 0.5, 4.4 + width)  # 背骨から外へ (体の幅より外へ張る)
    m = np.zeros(g.n, bool)
    for i in range(10):  # 膝の高さ (6) から肩の下まで
        y0 = 6.0 + 2.0 * i
        step = 0.5 * (i % 2)
        ext = 0.5 if y0 < 12 else 0.0
        xs = (x[0] - ext, x[1]) if s < 0 else (x[0], x[1] + ext)
        m |= box_m(g, (xs[0], y0, -4.6 + step), (xs[1], y0 + 1.5, -2.0))
    idx = np.floor((g.X - x[0]) / 0.5).astype(int)
    m &= ~((g.Y < 12.5) & (idx % gap == 0))  # 裾の羽のすき間
    g.put(nm, m, tc.by_height(g, [(8.0, STORM_D), (16.0, STORM), (25.0, STORM_L)]))
    g.recolor(m & (g.Y < 10.5), tip)  # 羽の先は白灰
    # 羽の列 (帯) を 1.5 単位ごとに薄く
    band = m & (np.mod(g.Y, 3.0) < 0.5)
    g.recolor(band, STORM_D)


def _warden_head_a(g):
    """鳥の頭骨の面: 細身、嘴、目の穴 (青白い光)、後頭の羽の冠"""
    E(g, "head", (0, 30.2, 0), (2.9, 3.3, 2.7), ASH_W)
    T(g, "head", [(0, 29.4, 2.2), (0, 28.6, 5.4), (0, 27.9, 7.2)], [1.4, 0.9, 0.25], BONE)
    for sx in (-1, 1):
        g.recolor(ell(g, (sx * 1.1, 30.6, 2.6), (0.9, 0.9, 0.7)) & (g.lab == g.lid("head")), EYE_DARK)
        g.recolor(ell(g, (sx * 1.1, 30.6, 2.9), (0.4, 0.4, 0.4)) & (g.lab == g.lid("head")), BOLT_B, glow=True)
    for x, col in ((-1.0, STORM_L), (0.0, ASH_W), (1.0, STORM_L)):
        T(g, "head", [(x * 0.8, 33.4, -0.6), (x * 1.4, 35.6, -2.4), (x * 1.2, 35.0, -5.6)],
          [0.9, 0.7, 0.25], col)


def _warden_head_b(g):
    """銅の兜: 厚い鉄兜の代わりの銅の殻、目の横の細い光、頬の当て、冠の稜線"""
    E(g, "head", (0, 30, 0), (3.3, 3.6, 3.1), ASH_W_D)
    g.put("head", ell(g, (0, 30.3, 0), (3.8, 4.3, 3.5)), COPPER)  # 殻 (中の頭は残る)
    slit = mask_box(g, (-2.2, 2.2), (29.8, 31.0), (2.2, 4.6)) & (g.lab == g.lid("head"))
    g.recolor(slit, BOLT_B, glow=True)
    T(g, "head", [(0, 34.8, 2.8), (0, 35.6, 0.0), (0, 34.8, -3.2)], [0.7, 0.8, 0.7], COPPER_L)
    side = (np.abs(g.X) > 2.6) & (g.Y < 30.4) & (g.Z > 0.0) & (g.lab == g.lid("head"))
    g.recolor(side, COPPER_D)


def _warden_head_c(g):
    """布を巻いた頭: 布の帯、目の細い隙間 (黄白の光)、頭の上の風見 (銅の矢と鶏)"""
    E(g, "head", (0, 30, 0), (3.2, 3.6, 3.1), ASH_W_D)
    g.put("head", ell(g, (0, 30, 0), (4.0, 4.5, 3.9)), CLOTH)
    hid = g.lid("head")
    band = (g.lab == hid) & (np.mod(g.Y, 1.5) < 0.75)
    g.recolor(band, CLOTH_D)
    slit = mask_box(g, (-1.6, 1.6), (29.6, 30.8), (2.2, 4.4)) & (g.lab == hid)
    g.recolor(slit, BOLT_Y, glow=True)
    T(g, "head", [(0, 33.0, 0), (0, 38.6, 0)], 0.35, COPPER)  # 布の中から立てる (頭に付く)
    T(g, "head", [(-2.4, 38.6, 0), (2.8, 38.6, 0)], 0.3, COPPER)
    E(g, "head", (2.6, 38.8, 0), (0.7, 0.6, 0.5), COPPER_L)
    E(g, "head", (0, 39.2, 0), (0.45, 0.45, 0.45), BOLT_Y, glow=True)


def warden_geo(opt):
    sh = WARDEN_SHAPE[opt]
    g = Grid(WARDEN_LO, WARDEN_HI, seed=10 + "abc".index(opt))
    for n in WARDEN_BONES:
        g.lid(n)
    # 脚 (膝の段、黒い靴)
    for s, nm in ((-1, "leg_r"), (1, "leg_l")):
        BX(g, nm, MX(s, 0.5, 4.0), (6.5, 13.5), (-2.0, 2.0), STORM)
        BX(g, nm, MX(s, 1.0, 3.5), (0.5, 6.5), (-1.5, 1.5), STORM_D)
        BX(g, nm, MX(s, 0.5, 4.0), (0.0, 2.5), (-1.5, 2.5), SLATE)
    # 胴 (灰の肌が襟から覗く、帯は銅)
    E(g, "body", (0, 19, 0), sh["body"], STORM)
    g.recolor(mask_box(g, (-6, 6), (13.0, 14.5), (-4, 4)) & (g.lab == g.lid("body")), COPPER)
    BX(g, "body", (-1.8, 1.8), (24.0, 26.0), (-1.5, 1.5), ASH_W_D)
    if opt == "b":  # 重装の胸当て (銅)
        BX(g, "body", (-3.2, 3.2), (15.5, 22.0), (2.3, 4.0), COPPER)
    # 腕 (右 = -X が鉤槍の手)
    for s, arm, fore, hand in ((-1, "arm_r", "forearm_r", (-6.6, 12.4, 4.3)),
                               (1, "arm_l", "forearm_l", (6.6, 12.2, 2.0))):
        BX(g, arm, MX(s, 4.5, 8.0), (16.5, 25.0), (-2.0, 2.0), STORM)
        BX(g, fore, MX(s, 5.0, 8.0), (12.5, 17.5), (0.5, 4.0) if s < 0 else (-0.5, 2.0), STORM_D)
        E(g, fore, hand, (1.2, 1.2, 1.2), ASH_W_D)
        if opt == "b":  # 肩の重ね羽板
            for i, (y0, y1) in enumerate(((21.5, 25.5), (18.0, 21.5), (14.5, 18.0))):
                BX(g, arm, MX(s, 7.8, 9.8), (y0, y1), (-2.6, 2.6), ASH_W_D if i % 2 == 0 else STORM_L)
    # 外套の羽 (背中の両側、裾は羽の先)
    for s, nm in ((-1, "cloak_r"), (1, "cloak_l")):
        _warden_cloak(g, nm, s, sh["cloak_w"], sh["gap"], ASH_W_D if opt != "c" else CLOTH)
    # 頭
    {"a": _warden_head_a, "b": _warden_head_b, "c": _warden_head_c}[opt](g)
    # 目印の光: 肩の帯の端に小さな稲妻の白
    g.put("cloak_l", ell(g, (6.2, 9.0, -3.2), (0.4, 0.4, 0.4), None), BOLT, glow=True)
    g.put("cloak_r", ell(g, (-6.2, 9.0, -3.2), (0.4, 0.4, 0.4), None), BOLT, glow=True)
    geo, tex, gtex, stats = export_all(g, "feather_warden", WARDEN_PIV, WARDEN_PAR, WARDEN_ROT)
    return geo, tex, gtex, stats


def hooked_glaive():
    """鉤槍 (武器の別モデル): 持ち手が原点、柄は +Y (上)、鉤は -X (体から離れる側) へ。全長 約 40 単位。"""
    g = Grid((-11.0, -11.0, -4.0), (5.0, 36.0, 4.0), seed=5)
    g.lid("glaive")
    T(g, "glaive", [(0, -9, 0), (0, 31, 0)], 0.6, BONE_D)           # 柄
    for y in (-1.5, 1.0):                                              # 持ち手の巻き布
        g.put("glaive", ring(g, (0, y, 0), (0, 1, 0), 0.9, 0.5, 0.7), CLOTH_D)
    g.put("glaive", ring(g, (0, 24.5, 0), (0, 1, 0), 0.9, 0.5, 1.5), COPPER)  # 鉤の根元の輪
    E(g, "glaive", (0, -9.5, 0), (0.8, 0.8, 0.8), COPPER_D)           # 石突き
    T(g, "glaive", [(0.0, 25.0, 0), (-1.5, 28.5, 0), (-4.2, 31.0, 0), (-7.2, 30.5, 0),
                    (-8.6, 27.8, 0), (-7.6, 25.4, 0)], [0.9, 1.0, 1.0, 1.0, 0.9, 0.7], COPPER)  # 鉤
    T(g, "glaive", [(0.0, 30.5, 0), (0.0, 35.0, 0)], [0.95, 0.15], COPPER_L)  # 穂先
    E(g, "glaive", (-5.4, 27.0, 0), (0.45, 0.45, 0.45), BOLT_B, glow=True)     # 鉤の内の稲妻
    g.recolor(tube(g, [(-7.6, 27.0, 0), (-8.2, 28.0, 0)], 0.35) & (g.lab > 0), BOLT_B, glow=True)
    geo, tex, gtex, stats = tc.export(g, "hooked_glaive")
    return geo, tex, gtex, stats


# ================================================================ 雷蛾 (static_moth)

MOTH_LO, MOTH_HI = (-11.0, 0.0, -7.0), (11.0, 13.0, 8.0)
MOTH_BONES = ["body", "wing_l", "wing_r", "antennae"]
MOTH_PIV = {"body": (0, 6, 0), "wing_l": (1.0, 7.2, 0), "wing_r": (-1.0, 7.2, 0), "antennae": (0, 8.2, 5)}
MOTH_PAR = {"wing_l": "body", "wing_r": "body", "antennae": "body"}
MOTH_CALM = {"wing_l": (0, 0, 14), "wing_r": (0, 0, -14)}   # 翅を少し上げて描く (描画だけ)
MOTH_TOP = {"wing_l": (0, 0, 14), "wing_r": (0, 0, -14)}
MOTH_PAL = {
    "a": dict(body=(112, 110, 122), body_d=(84, 82, 96), head=(120, 118, 130), wing=STORM_L, wing2=STORM,
              eye=EYE_DARK, ring=COPPER_D, spot=ASH_W, ante=STORM_L, fur=None),
    "b": dict(body=(150, 160, 184), body_d=(120, 130, 156), head=(176, 186, 210), wing=(196, 206, 226),
              wing2=(176, 188, 214), eye=EYE_DARK, ring=(176, 186, 210), spot=(214, 224, 240),
              ante=BONE, fur=None),
    "c": dict(body=(236, 234, 226), body_d=(214, 212, 204), head=(240, 238, 230), wing=(226, 224, 216),
              wing2=(210, 208, 200), eye=EYE_DARK, ring=(246, 244, 236), spot=(250, 248, 240),
              ante=ASH_W_D, fur=(246, 244, 236)),
}


def moth_geo(opt):
    P = MOTH_PAL[opt]
    g = Grid(MOTH_LO, MOTH_HI, seed=30 + "abc".index(opt))
    for n in MOTH_BONES:
        g.lid(n)
    charge = np.zeros(g.n, bool)
    # 胴: 胸、腹、頭
    fuzz = opt == "c"
    E(g, "body", (0, 6.2, 1.0), (1.9, 2.0, 2.5), P["body"])
    T(g, "body", [(0, 6.0, 0.0), (0, 5.4, -3.8), (0, 5.0, -7.0)], [1.7, 1.4, 0.9], P["body_d"])
    E(g, "body", (0, 7.0, 4.4), (1.5, 1.4, 1.3), P["head"])
    if fuzz:  # 毛の塊 (胸の表面に丸い毛)
        for p in fib(26, 1.0):
            x, y, z = p[0] * 1.9, 6.2 + p[1] * 2.0, 1.0 + p[2] * 2.5
            E(g, "body", (x, y, z), (0.55, 0.55, 0.55), P["fur"])
    for sx in (-1, 1):  # 目 (黒、白い光の点)
        E(g, "body", (sx * 0.9, 7.4, 5.4), (0.55, 0.55, 0.45), P["eye"])
        E(g, "body", (sx * 0.9, 7.6, 5.85), (0.3, 0.3, 0.25), P["spot"], glow=True)
    # 翅 (左右)
    for s in (-1, 1):
        nm = "wing_l" if s > 0 else "wing_r"
        cx = s * 5.2
        if opt == "b":
            base = ell(g, (cx, 7.3, 0), (5.0, 1.0, 4.2), mod=lambda X, Y, Z: 1.0 - 0.25 * np.clip(np.abs(X - cx) / 5.0, 0, 1))
        else:
            base = ell(g, (cx, 7.3, 0), (4.9, 0.9, 3.7))
        g.put(nm, base, tc.rows(g, 0, 1.5, P["wing"], P["wing2"], frac=0.5))
        if opt == "a":  # 目玉模様 (平常は暗い、帯電で光る)
            c = (s * 6.3, 7.5, 0.8)
            outer = ell(g, c, (1.6, 1.2, 1.6)) & (g.lab == g.lid(nm))
            inner = ell(g, c, (0.9, 1.3, 0.9)) & (g.lab == g.lid(nm))
            g.recolor(outer, P["ring"])
            g.recolor(inner & ~outer, P["eye"])
            g.recolor(ell(g, c, (0.4, 1.3, 0.4)) & (g.lab == g.lid(nm)), P["spot"])
            charge |= ell(g, c, (1.6, 1.2, 1.6)) & (g.lab == g.lid(nm))
        if opt == "b":  # 稲妻の形の翅脈 (平常でもほのかに光る)
            for k, (z0, z1) in enumerate(((0.8, 1.2), (-0.8, -2.6))):
                pts_a = (s * 1.4, 7.4, 0.0)
                pts_b = (s * 8.6, 7.6, z1 + 1.2 * (1 if k == 0 else -1))
                vein = tc.zigzag(g, pts_a, pts_b, 0.9, 5, 0.3, seed=k)
                g.recolor(vein & (g.lab == g.lid(nm)), BOLT_B, glow=True)
            rim = (g.lab == g.lid(nm)) & (np.abs(g.Z) > 2.8)
            charge |= rim
        if opt == "c":  # 翅の縁の毛 (丸い毛の房)
            for k in range(22):
                t = 2 * math.pi * k / 22
                p = (cx + 4.9 * math.cos(t), 7.3, 3.7 * math.sin(t))
                E(g, nm, p, (0.6, 0.6, 0.6), P["fur"])
            charge |= (g.lab == g.lid(nm)) & (np.abs(g.Z) > 2.9)
    # 触角 (羽の櫛)
    for s in (-1, 1):
        pts = [(s * 0.9, 8.2, 5.4), (s * 1.9, 9.8, 6.6), (s * 2.6, 10.8, 7.4)]
        T(g, "antennae", pts, [0.35, 0.3, 0.2], P["ante"])
        for k in range(1, 4):
            t = k / 4
            p = np.array(pts[0]) * (1 - t) + np.array(pts[1]) * t
            E(g, "antennae", tuple(p), (0.55, 0.45, 0.5) if opt != "c" else (0.7, 0.6, 0.6), P["ante"])
        E(g, "antennae", pts[-1], (0.45, 0.45, 0.45), BOLT_B, glow=True)
        charge |= (g.lab == g.lid("antennae")) & (np.hypot(np.hypot(g.X - s * 2.6, g.Y - 10.8), g.Z - 7.4) < 0.5)
    pivots, parents = MOTH_PIV, MOTH_PAR
    # 帯電 (光の強い状態): 目玉模様と翅の縁と触角の先が、平常より強く光る (export の前に写す)
    g_copy = copy.deepcopy(g)
    geo, tex, gtex, stats = export_all(g, "static_moth", pivots, parents)
    gtex_c = charged_gtex(g_copy, "static_moth", pivots, parents, charge | g.glow, BOLT_Y, geo)
    stats["charged_glow_px"] = int((gtex_c[..., 3] > 0).sum())
    return geo, tex, gtex, gtex_c, stats


# ================================================================ 雷乞い (thunder_supplicant)

SUP_LO, SUP_HI = (-9.0, -1.0, -8.0), (9.0, 38.0, 8.0)
SUP_BONES = ["body", "head", "robe", "arm_r", "forearm_r", "socket_main", "arm_l", "forearm_l"]
SUP_PIV = {"body": (0, 16, 0), "head": (0, 21, 2), "robe": (0, 12, 0), "arm_r": (-4.8, 18.5, 0),
           "forearm_r": (-4.8, 13.0, 2.0), "socket_main": (-4.8, 10.5, 5.2), "arm_l": (4.8, 18.5, 0),
           "forearm_l": (4.8, 13.0, 2.0)}
SUP_PAR = {"head": "body", "robe": "body", "arm_r": "body", "arm_l": "body", "forearm_r": "arm_r",
           "forearm_l": "arm_l", "socket_main": "forearm_r"}
SUP_ROT = {"socket_main": (25, 0, 0)}  # 杖を前へ傾けて、石突きを地面へ
SUP_CAST = {"arm_r": (-135, 0, 0), "socket_main": (110, 0, 0), "arm_l": (-70, 0, 0)}  # 杖を頭上へ
SUP_SHAPE = {"a": dict(robe_r=(5.4, 9.6, 4.6), hump=(4.4, 3.6, 3.2), body=(3.0, 3.4, 2.4)),
             "b": dict(robe_r=(5.8, 9.4, 5.0), hump=(5.4, 4.6, 4.0), body=(3.2, 3.6, 2.6)),
             "c": dict(robe_r=(5.0, 9.6, 4.2), hump=(4.0, 3.4, 3.0), body=(2.8, 3.2, 2.2))}


def supplicant_geo(opt):
    sh = SUP_SHAPE[opt]
    g = Grid(SUP_LO, SUP_HI, seed=50 + "abc".index(opt))
    for n in SUP_BONES:
        g.lid(n)
    # 胴 (猫背の首だけが外へ出る)
    E(g, "body", (0, 17, 0), sh["body"], ASH_W_D)
    # 腕 (袖は布、手は灰色)
    for s, arm, fore, hand in ((-1, "arm_r", "forearm_r", (-4.8, 10.4, 5.4)),
                               (1, "arm_l", "forearm_l", (4.8, 10.2, 4.6))):
        BX(g, arm, MX(s, 3.5, 6.0), (13.0, 19.0), (-1.5, 1.5), CLOTH_D)
        BX(g, fore, MX(s, 3.5, 6.0), (10.0, 14.0), (0.5, 5.5) if s < 0 else (0.5, 4.5), CLOTH)
        E(g, fore, hand, (0.9, 0.9, 0.9), ASH_W_D)
    # 頭 (猫背で前へ出る)
    {"a": _sup_head_a, "b": _sup_head_b, "c": _sup_head_c}[opt](g)
    # 長衣 (裾へ広がる、背中の瘤、裾は歯のようにすき間)
    robe_c = (0, 9.6, 0)
    robe = ell(g, robe_c, sh["robe_r"], flare(0.32, 9.6))
    robe |= ell(g, (0, 16.2, -2.4), sh["hump"])
    hem = (g.Y < 1.0) & (np.floor((g.X + 9) * 2).astype(int) % 3 == 0)
    robe &= ~hem
    g.put("robe", robe, tc.by_height(g, [(0.0, CLOTH_D), (8.0, CLOTH), (19.0, CLOTH_D)]))
    if opt == "b":  # 避雷針は背中に縛る (体の骨に付ける)
        T(g, "body", [(0, 15, -3.6), (0, 24, -3.8), (0, 33.8, -3.8)], [0.5, 0.45, 0.25], COPPER)
        T(g, "body", [(0, 33.8, -3.8), (-0.9, 35.2, -3.8)], 0.3, COPPER)
        T(g, "body", [(0, 33.8, -3.8), (0.9, 35.2, -3.8)], 0.3, COPPER)
        g.recolor((g.lab == g.lid("body")) & (g.Y > 34.5) & (np.abs(g.Z + 3.8) < 0.6), BOLT_B, glow=True)
        E(g, "body", (0, 35.4, -3.8), (0.9, 0.9, 0.9), BOLT, glow=True)  # 避雷針の先の光
        E(g, "body", (0, 26.0, -3.8), (0.5, 0.5, 0.5), BOLT_B, glow=True)  # 中ほどの小さな光
    # 布の帯 (胴の襟)
    g.recolor(mask_box(g, (-3, 3), (17.8, 19.5), (-3, 3)) & (g.lab == g.lid("body")), CLOTH_D)
    geo, tex, gtex, stats = export_all(g, "thunder_supplicant", SUP_PIV, SUP_PAR, SUP_ROT)
    return geo, tex, gtex, stats


def _sup_head_a(g):
    """頭巾: 細長く尖った頭巾、目は頭巾の隙間から青白く光る"""
    E(g, "head", (0, 23.6, 2.1), (2.2, 2.5, 2.1), ASH_W_D)
    hid = g.lid("head")
    g.put("head", ell(g, (0, 23.8, 2.0), (3.2, 3.9, 3.3)), CLOTH_D)
    g.carve(mask_box(g, (-1.7, 1.7), (22.8, 25.0), (4.4, 6.0)) & (g.lab == hid))  # 頭巾だけを抜く
    g.recolor(mask_box(g, (-1.5, 1.5), (22.9, 25.0), (3.9, 4.4)) & (g.lab == hid), BOLT_B, glow=True)  # 面の光
    for sx in (-1, 1):
        g.recolor(ell(g, (sx * 0.9, 23.8, 3.9), (0.55, 0.55, 0.55)) & (g.lab == hid), BOLT_B, glow=True)
    T(g, "head", [(0, 25.5, 0.5), (0, 28.5, -0.4), (0, 30.6, -1.0)], [2.2, 1.3, 0.3], CLOTH_D)
    g.recolor((g.lab == hid) & (np.abs(g.X) < 0.5) & (g.Y > 28.5), COPPER)  # 頭巾の上の銅の留め


def _sup_head_b(g):
    """避雷針の老人: 白い髭、帽子の布、皺の段"""
    E(g, "head", (0, 23.8, 2.2), (2.4, 2.6, 2.3), ASH_W_D)
    hid = g.lid("head")
    g.put("head", ell(g, (0, 24.6, 1.8), (2.8, 2.6, 2.6)), CLOTH)  # 帽子
    E(g, "head", (0, 20.9, 3.6), (1.8, 2.1, 1.2), ASH_W)           # 髭
    for sx in (-1, 1):
        g.recolor(ell(g, (sx * 0.9, 23.8, 4.1), (0.55, 0.55, 0.55)) & (g.lab == hid), BOLT_B, glow=True)
    g.recolor((g.lab == hid) & (np.abs(g.Y - 21.0) < 0.3) & (g.Z > 2.0), STORM_L)  # 皺


def _sup_head_c(g):
    """鳥籠の頭: 銅の輪と棒の籠、中に小さな稲妻が閉じ込められている"""
    E(g, "head", (0, 25.2, 2.0), (1.9, 2.3, 1.9), ASH_W_D)
    hid = g.lid("head")
    g.carve(mask_box(g, (-1.0, 1.0), (23.8, 26.6), (2.6, 4.5)) & (g.lab == hid))  # 籠の前の窓
    E(g, "head", (0, 25.2, 2.2), (1.1, 1.1, 1.1), BOLT, glow=True)  # 中の稲妻
    g.put("head", ring(g, (0, 22.2, 2.0), (0, 1, 0), 2.8, 0.5, 0.7), COPPER_D)
    g.put("head", ring(g, (0, 28.2, 2.0), (0, 1, 0), 2.8, 0.5, 0.7), COPPER_D)
    g.put("head", plate(g, (0, 28.4, 2.0), (0, 1, 0), 2.8, 0.5), COPPER_D)
    T(g, "head", [(0, 18.6, 1.6), (0, 22.4, 2.0)], [0.9, 0.8], ASH_W_D)  # 首 (体に埋まる所は残らない)
    g.put("head", ring(g, (0, 20.2, 1.8), (0, 1, 0), 1.6, 0.5, 0.9), COPPER_D)  # 首の輪 (襟)
    for k in range(6):
        t = 2 * math.pi * k / 6
        x, z = 2.8 * math.cos(t), 2.0 + 2.8 * math.sin(t)
        T(g, "head", [(x, 22.2, z), (x, 28.2, z)], 0.3, COPPER)
    g.recolor((g.lab == hid) & (g.Y > 28.0) & (np.hypot(g.X, g.Z - 2.0) > 2.3), BOLT_Y, glow=True)  # 籠の上の輪の光


def staff_geo(short=False):
    """雷の杖 (武器の別モデル): 持ち手が原点。石突きは -12、穂先は +28 (短いものは +20)。直立。"""
    top = 20.0 if short else 28.0
    g = Grid((-3.0, -14.0, -3.0), (3.0, 35.0, 3.0), seed=7)
    g.lid("staff")
    T(g, "staff", [(0, -12, 0), (0, top, 0)], 0.55, BONE_D)
    for y in (-1.5, 1.0):
        g.put("staff", ring(g, (0, y, 0), (0, 1, 0), 0.85, 0.5, 0.7), CLOTH_D)
    E(g, "staff", (0, -12.5, 0), (0.6, 0.6, 0.6), COPPER_D)
    rg = ring(g, (0, top - 2.0, 0), (0, 1, 0), 1.6, 0.6, 0.8)
    g.put("staff", rg, COPPER)
    g.recolor(rg, BOLT_Y, glow=True)  # 穂の輪は稲妻の光
    T(g, "staff", [(0, top, 0), (0, top + 3.5, 0)], 0.45, COPPER)
    E(g, "staff", (0, top + 4.6, 0), (0.9, 0.9, 0.9), BOLT_Y, glow=True)
    geo, tex, gtex, stats = tc.export(g, "supplicant_staff_short" if short else "supplicant_staff")
    return geo, tex, gtex, stats


# ================================================================ 出力

def _write_weapon(folder, geo, tex, gtex, stats):
    st = tc.save(folder, "model", geo, tex, gtex, stats)
    return st


def render_option(subj, opt, mob_geo, mob_tex, mob_gl, weapon, special_pose, special_gl, ref_x, pose_calm,
                  special_label):
    folder = MOBS / subj / opt
    rd = folder / "renders"
    rd.mkdir(parents=True, exist_ok=True)
    mob = (mob_geo, mob_tex, mob_gl)
    spec = [
        ("3/4", 35, 16, mob_faces(mob, pose_calm, weapon=weapon), False),
        ("front", 0, 4, mob_faces(mob, pose_calm, weapon=weapon), False),
        ("side", 90, 4, mob_faces(mob, pose_calm, weapon=weapon), False),
        (special_label, 35, 12, mob_faces(mob, special_pose, gl=special_gl, weapon=weapon), False),
        ("night", 30, 10, mob_faces(mob, pose_calm, weapon=weapon), True),
    ]
    ims = []
    for name, yaw, pitch, faces, night in spec:
        fs = faces + (tc.player_ref(x=ref_x) if night else [])
        im = tc.night(tc.draw(fs, yaw, pitch, size=SZ)) if night else tc.draw(fs, yaw, pitch, size=SZ)
        im.save(rd / f"{SAFE.get(name, name)}.png")
        ims.append(tc.label(im, name, 15))
    return ims


def write_stats(subj, stats):
    (MOBS / subj).mkdir(parents=True, exist_ok=True)
    tc.write_stats(MOBS / subj / "stats.json", stats)


def run_weapons():
    res = {}
    gl = hooked_glaive()
    st = _write_weapon(WEAP / "hooked_glaive", gl[0], gl[1], gl[2], gl[3])
    res["hooked_glaive"] = st
    sg = staff_geo(False)
    res["supplicant_staff"] = _write_weapon(WEAP / "supplicant_staff", sg[0], sg[1], sg[2], sg[3])
    sg2 = staff_geo(True)
    res["supplicant_staff_short"] = _write_weapon(WEAP / "supplicant_staff" / "short", sg2[0], sg2[1], sg2[2], sg2[3])
    return res


def load_weapon(folder):
    geo = json.loads((folder / "model.geo.json").read_text(encoding="utf-8"))
    tex = vx.load_png(folder / "model.tex.png")
    gl = vx.load_png(folder / "model.glow.png")
    return geo, tex, gl


def run_warden(weapon_res=None):
    wg = load_weapon(WEAP / "hooked_glaive")
    weapon = wg
    stats = {}
    cols = []
    labels = {"a": "A 鳥の頭骨の面と羽の外套", "b": "B 銅の兜と鎧羽", "c": "C 布を巻いた風見の人"}
    for opt in "abc":
        geo, tex, gtex, st = warden_geo(opt)
        folder = MOBS / "feather_warden" / opt
        st = tc.save(folder, "model", geo, tex, gtex, st)
        stats[opt] = st
        ims = render_option("feather_warden", opt, geo, tex, gtex, weapon, WARDEN_DIVE, gtex, 24,
                            WARDEN_CALM, "dive (突撃)")
        cols.append((labels[opt], ims))
    stats["weapon_overlaps"] = len(vx.check_overlaps(wg[0]))
    stats["weapon_cubes"] = sum(len(b["cubes"]) for b in wg[0]["minecraft:geometry"][0]["bones"])
    write_stats("feather_warden", stats)
    sheet = vx.sheet("羽守 ラフ 3案", cols,
                     note="行: 3/4 (鉤槍つき) / 正面 / 横 / 突撃の姿勢 (外套を広げ、腕を前へ) / 夜 (人との比べ)。鉤槍は別モデル (受け口 socket_main)")
    sheet.save(OUT / "feather_warden.png")
    return stats


def run_moth():
    stats = {}
    cols = []
    labels = {"a": "A 灰色の大蛾と目玉模様", "b": "B 透けた翅の雷光蛾", "c": "C 毛深い蚕蛾"}
    for opt in "abc":
        geo, tex, gtex, gtex_c, st = moth_geo(opt)
        folder = MOBS / "static_moth" / opt
        st = tc.save(folder, "model", geo, tex, gtex, st)
        tc.Image.fromarray(gtex_c).save(folder / "glow_charged.png", optimize=True)
        stats[opt] = st
        mob = (geo, tex, gtex)
        rd = folder / "renders"
        rd.mkdir(parents=True, exist_ok=True)
        ims = []
        spec = [
            ("3/4", 35, 18, mob_faces(mob, MOTH_CALM), None, False),
            ("top", 0, 62, mob_faces(mob, MOTH_TOP), None, False),
            ("front", 0, 4, mob_faces(mob, MOTH_CALM), None, False),
            ("charged", 35, 18, mob_faces(mob, MOTH_CALM, gl=gtex_c), None, False),
            ("night", 30, 10, mob_faces(mob, MOTH_CALM), None, True),
        ]
        for name, yaw, pitch, faces, _, night in spec:
            fs = faces + (tc.player_ref(x=16) if night else [])
            im = tc.night(tc.draw(fs, yaw, pitch, size=SZ)) if night else tc.draw(fs, yaw, pitch, size=SZ)
            im.save(rd / f"{SAFE.get(name, name)}.png")
            ims.append(tc.label(im, name, 15))
        cols.append((labels[opt], ims))
    write_stats("static_moth", stats)
    sheet = vx.sheet("雷蛾 ラフ 3案", cols,
                     note="行: 3/4 / 真上 / 正面 / 帯電 (目玉模様と翅の縁が光る) / 夜 (人との比べ)。平常の翅は少し上げて描いている")
    sheet.save(OUT / "static_moth.png")
    return stats


def run_supplicant():
    sw = load_weapon(WEAP / "supplicant_staff")
    sw_short = load_weapon(WEAP / "supplicant_staff" / "short")
    stats = {}
    cols = []
    labels = {"a": "A 頭巾と銅の杖", "b": "B 避雷針を背負った老人", "c": "C 鳥籠の頭"}
    for opt in "abc":
        geo, tex, gtex, st = supplicant_geo(opt)
        folder = MOBS / "thunder_supplicant" / opt
        st = tc.save(folder, "model", geo, tex, gtex, st)
        stats[opt] = st
        weapon = sw_short if opt == "b" else sw
        ims = render_option("thunder_supplicant", opt, geo, tex, gtex, weapon, SUP_CAST, gtex, 20,
                            None, "casting (杖を掲げる)")
        cols.append((labels[opt], ims))
    stats["weapon_overlaps"] = len(vx.check_overlaps(sw[0]))
    stats["weapon_overlaps_short"] = len(vx.check_overlaps(sw_short[0]))
    write_stats("thunder_supplicant", stats)
    sheet = vx.sheet("雷乞い ラフ 3案", cols,
                     note="行: 3/4 (杖つき) / 正面 / 横 / 詠唱 (杖を頭上へ掲げる) / 夜 (人との比べ)。B は杖を短い変種にしている")
    sheet.save(OUT / "thunder_supplicant.png")
    return stats


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "all"
    WEAP.mkdir(parents=True, exist_ok=True)
    if cmd in ("weapons", "all"):
        print("weapons", json.dumps(run_weapons(), ensure_ascii=False, indent=1, default=str)[:600])
    if cmd in ("warden", "all"):
        print("warden", json.dumps(run_warden(), ensure_ascii=False, indent=1, default=str))
    if cmd in ("moth", "all"):
        print("moth", json.dumps(run_moth(), ensure_ascii=False, indent=1, default=str))
    if cmd in ("supplicant", "all"):
        print("supplicant", json.dumps(run_supplicant(), ensure_ascii=False, indent=1, default=str))


if __name__ == "__main__":
    main()
