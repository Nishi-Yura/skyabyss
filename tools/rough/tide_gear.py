"""潮殻のゲア (三叉槍・珊瑚の盾・潮招きの大鋏・珊瑚憑きの銛) と、護符・真珠・心核のラフ 3案ずつを作る。

使い方:
    python tools/rough/tide_gear.py

出力 (docs/review/tide_gear_roughs/):
    compare_<item>.png    案ごとの比較 (アイコン・3D の正面と斜め)
    compare_icons.png     アイコンだけの品 (護符・真珠・心核) の比較
    <item>/<a|b|c>/icon.png      32x32 のアイコン (武器は斜め、右上が刃先)
    <item>/<a|b|c>/upright.png   3D の元になる立てた向きの 32x32 (確認用)
    <item>/<a|b|c>/voxels.npz    3D の彫刻ボクセル (grid[X,Y,Z,4], glow[X,Y,Z]) 。3D の品だけ
    <item>/<a|b|c>/front.png, hand.png   3D の確認画像 (採用ではないものも残す)

手順 (1案):
1. 32x32 の絵を、描く場 (48 px の四角) に重ねた図形 (層) として描く。武器は立てた向きで描き、
   回して斜めのアイコンにする (-45 度。刃先が右上)。層ごとに「盛り上がり (bump)」を付けられる。
2. アイコン: 層を回して 32px に縮め、影 (左上が明るい) と輪郭線 (濃い色) を付ける。
3. 3D: 立てた向きの絵を 3 倍 (1 画素 = 3 ボクセル) の解像度で作り、縁からの距離で断面を丸くする
   (中心が厚く、縁は薄い。細い部分は細く、軸は丸く)。盛り上がりの層は、その分だけ高くする。
4. 盾は立てた向きをそのまま使い、背面に持ち手 (横棒) を付ける (Z の低い側が背面)。
5. ボクセルは 3D 用の格子に入れ、install_tide_gear.py が要素 (直方体) に書き出す。

護符・真珠・心核はアイコンだけ。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from voxel import Face, icon_big, render, sheet, voxel_faces  # noqa: E402

OUT_DIR = ROOT / "docs/review/tide_gear_roughs"

SS = 8          # 1 アイコン px = 8 描画 px (輪郭を滑らかにするため大きく描く)
OFF = 8         # 32px の絵の左上は、描く場の (8,8) に置く。描く場は 48px
CANVAS = 48
UP = 3          # 3D は 1 画素 = UP ボクセル
ROT_DEG = -45   # 刃先を右上へ。PIL は反時計回りが正なので負で時計回り
OUTLINE = (27, 29, 42)

PAL = {
    "coral": (200, 80, 74), "coral_pink": (232, 138, 138), "coral_deep": (162, 58, 46),
    "teal": (42, 143, 147), "teal_deep": (26, 100, 106), "teal_lite": (96, 196, 194),
    "pearl": (239, 230, 214), "silver": (201, 211, 220), "silver_deep": (138, 150, 164),
    "barnacle": (122, 106, 92), "barnacle_deep": (84, 72, 62),
    "stone": (92, 107, 122), "stone_deep": (60, 72, 86),
    "glow": (127, 245, 255), "glow_pearl": (223, 240, 250),
    "bone": (214, 200, 176), "rope": (150, 118, 84),
    "purple": (142, 104, 178), "orange": (226, 130, 84),
}


def _c(name):
    return PAL[name] if isinstance(name, str) else name


# ---------------------------------------------------------------- 描画 (層)

_CUR = {"d": None}   # 直前に作った層 (描く先の省略時に使う)


class Pic:
    """描く図形の層。層は後から足したものが前に来る。bump は 3D で盛り上げるボクセル数。"""

    def __init__(self):
        self.layers = []   # (mask, 色, 光るか, 盛り上がり)

    def layer(self, col, glow=False, bump=0):
        m = Image.new("L", (CANVAS * SS, CANVAS * SS), 0)
        self.layers.append((m, _c(col), glow, bump))
        d = ImageDraw.Draw(m)
        _CUR["d"] = d
        return d


def _draw(args):
    """先頭が描画先なら、それを使う。省略されたら直前の層を使う。"""
    if args and isinstance(args[0], ImageDraw.ImageDraw):
        return args[0], args[1:]
    return _CUR["d"], args


def P(x, y):
    return ((x + OFF) * SS, (y + OFF) * SS)


def poly(*args):
    d, (pts,) = _draw(args)
    d.polygon([P(*p) for p in pts], fill=255)


def ell(*args):
    d, a = _draw(args)
    cx, cy, rx = a[0], a[1], a[2]
    ry = a[3] if len(a) > 3 else rx
    d.ellipse([P(cx - rx, cy - ry), P(cx + rx, cy + ry)], fill=255)


def rect(*args):
    d, (x0, y0, x1, y1) = _draw(args)
    d.rectangle([P(x0, y0), P(x1 - 1 / SS, y1 - 1 / SS)], fill=255)


def line(*args):
    d, a = _draw(args)
    pts, w = a[0], (a[1] if len(a) > 1 else 1.0)
    d.line([P(*p) for p in pts], fill=255, width=max(1, int(w * SS)), joint="curve")


def arc(cx, cy, r, a0, a1, n=14):
    """角度 (度, 0 は右、反時計回りは負) の円弧の点列。"""
    return [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
            for a in np.linspace(a0, a1, n)]


def taper(pts, w0, w1):
    """中心線 pts に沿って、幅 w0 から w1 へ細くなる帯の輪郭 (多角形の点列) を作る。"""
    p = np.array(pts, float)
    n = len(p)
    t = np.gradient(p, axis=0) if n > 1 else np.array([[0.0, 1.0]])
    nr = np.stack([-t[:, 1], t[:, 0]], -1)
    nr /= np.maximum(np.linalg.norm(nr, axis=1, keepdims=True), 1e-9)
    w = np.linspace(w0, w1, n)[:, None]
    left = p + nr * w / 2
    right = p - nr * w / 2
    return [tuple(v) for v in left] + [tuple(v) for v in right[::-1]]


def mirror(pts):
    return [(32 - x, y) for x, y in pts]


# ---------------------------------------------------------------- 合成 (32px: アイコン用)

def _shade_field(bm):
    """マスクの縁の向きから、左上を向く面は明るく、右下は暗くする値を返す。"""
    b = bm.astype(float)
    for _ in range(2):
        p = np.pad(b, 1, mode="edge")
        b = (p[:-2, 1:-1] + p[2:, 1:-1] + p[1:-1, :-2] + p[1:-1, 2:] + p[1:-1, 1:-1]) / 5.0
    gx = np.gradient(b, axis=1)
    gy = np.gradient(b, axis=0)
    return gx + gy


def _mix(c, t, target=(255, 255, 255)):
    return tuple(int(round(c[i] + (target[i] - c[i]) * t)) for i in range(3))


def _rot_mask(mask, angle):
    c = 24 * SS
    return mask.rotate(angle, resample=Image.Resampling.BILINEAR, center=(c, c)) if angle else mask


def compose(pic: Pic, angle: float = 0.0):
    """層を (回してから) 32px に縮め、影と輪郭を付ける。戻り値は (rgba (32,32,4), glow (32,32) bool, 輪郭前の色)。"""
    rgba = np.zeros((32, 32, 4), np.uint8)
    glow = np.zeros((32, 32), bool)
    for mask, col, is_glow, _bump in pic.layers:
        m = _rot_mask(mask, angle)
        m = m.crop((OFF * SS, OFF * SS, (OFF + 32) * SS, (OFF + 32) * SS)).resize((32, 32), Image.Resampling.BOX)
        bm = np.array(m) >= 128
        if not bm.any():
            continue
        if is_glow:
            rgba[bm, :3] = col
            rgba[bm, 3] = 255
            glow[bm] = True
            continue
        lit = _shade_field(bm)
        light = _mix(col, 0.42)
        dark = _mix(col, 0.38, (0, 0, 0))
        base_ok = bm & (np.abs(lit) <= 0.09)
        rgba[base_ok, :3] = col
        rgba[bm & (lit > 0.09), :3] = light
        rgba[bm & (lit < -0.09), :3] = dark
        rgba[bm, 3] = 255
        glow[bm] = False
    op = rgba[..., 3] > 0
    p = np.pad(op, 1, constant_values=False)
    inner = p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
    edge = op & ~inner
    base = rgba.copy()
    rgba[edge, :3] = OUTLINE
    glow[edge] = False
    return rgba, glow, base


# ---------------------------------------------------------------- 3D (丸い断面と盛り上がり)

def _erode_count(a):
    """縁からの距離 (削れる回数) を数える。"""
    d = np.zeros(a.shape, int)
    cur = a.copy()
    while cur.any():
        d += cur
        p = np.pad(cur, 1, constant_values=False)
        cur = p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:] & cur
    return d


def _layers_hi(pic: Pic, angle: float = 0.0):
    """層を UP 倍の解像度に縮めて、色・光・盛り上がりを重ねる (上の層が勝つ)。"""
    size = 32 * UP
    rgb = np.zeros((size, size, 3), np.uint8)
    A = np.zeros((size, size), bool)
    G = np.zeros((size, size), bool)
    B = np.zeros((size, size), int)
    for mask, col, is_glow, bump in pic.layers:
        m = _rot_mask(mask, angle)
        m = m.crop((OFF * SS, OFF * SS, (OFF + 32) * SS, (OFF + 32) * SS)).resize((size, size), Image.Resampling.BOX)
        bm = np.array(m) >= 128
        if not bm.any():
            continue
        rgb[bm] = col
        A[bm] = True
        G[bm] = is_glow
        B[bm] = bump
    return rgb, A, G, B


def voxelize_hi(pic: Pic, thick: int, angle: float = 0.0):
    """層から 3D の格子を作る。断面は縁からの距離で丸め、中心の厚さは thick ボクセル (片側)。
    盛り上がり (bump) の画素は、その分だけさらに高くする。"""
    rgb, A, G, B = _layers_hi(pic, angle)
    d = np.where(A, _erode_count(A) + 1, 0)
    R = max(int(d.max()), 1)
    prof = np.sqrt(np.clip(1 - (1 - np.minimum(d, R) / R) ** 2, 0, 1))
    h = np.where(A, np.maximum(1, np.round(thick * prof)), 0).astype(int)
    h = np.where(A, h + B, 0)
    H = int(h.max()) if h.size else 1
    size = A.shape[0]
    grid = np.zeros((size, size, 2 * H, 4), np.uint8)
    gl = np.zeros((size, size, 2 * H), bool)
    for row in range(size):
        for col in range(size):
            hh = h[row, col]
            if hh <= 0:
                continue
            x, y = col, size - 1 - row
            z0, z1 = H - hh, H + hh
            grid[x, y, z0:z1, :3] = rgb[row, col]
            grid[x, y, z0:z1, 3] = 255
            if G[row, col]:
                gl[x, y, z0:z1] = True
    return grid, gl


def shield_back_style(grid):
    """盾の裏 (Z の低い側) の見た目を、表と変える。珊瑚の面は暗い青緑と茶の貝の裏にし、肋 (すじ) を入れる。
    青緑の縁 (リム) はそのまま残す。表 (Z の高い側) は変えない。持ち手を付ける前に呼ぶ。"""
    X, Y, Z = grid.shape[:3]
    H = Z // 2
    solid = grid[..., 3] > 0
    r = grid[..., 0].astype(int)
    g = grid[..., 1].astype(int)
    back = np.zeros(solid.shape + (1,), bool)[..., 0]
    back[:, :, :H] = True
    coral = solid & back & (r > g + 40)
    under = np.array(PAL["teal_deep"], float) * 0.55 + grid[..., :3].astype(float) * 0.45
    ys = np.arange(Y)[None, :, None]
    ribs = np.broadcast_to(((ys // 4) % 3) == 0, solid.shape)
    newc = np.where(ribs[..., None], np.array(PAL["barnacle_deep"], float), under)
    grid[..., :3] = np.where(coral[..., None], newc, grid[..., :3]).astype(np.uint8)
    return grid


def add_back_handle(grid, gl):
    """盾の背面 (Z の低い側) に持ち手の横棒 (骨色) を付ける。両端は藤壺色の留め金で板に止める。
    板の厚みとは重ならないように、3 層ぶん後ろへ広げる。"""
    X, Y, Z = grid.shape[:3]
    solid = grid[..., 3] > 0
    xs, ys = np.nonzero(solid.any(axis=2))
    cx = (xs.min() + xs.max()) // 2
    cy = (ys.min() + ys.max()) // 2
    half_w = int((xs.max() - xs.min()) * 0.3)
    g2 = np.zeros((X, Y, Z + 3, 4), np.uint8)
    l2 = np.zeros((X, Y, Z + 3), bool)
    g2[:, :, 3:] = grid
    l2[:, :, 3:] = gl
    bar = np.array(PAL["bone"], np.uint8)
    strap = np.array(PAL["barnacle_deep"], np.uint8)
    for x in range(cx - half_w, cx + half_w + 1):
        for y in range(cy - 2, cy + 3):
            g2[x, y, 0:3, :3] = bar
            g2[x, y, 0:3, 3] = 255
    for x in (cx - half_w, cx + half_w):
        for y in range(cy - 3, cy + 4):
            g2[x, y, 0:3, :3] = strap
            g2[x, y, 0:3, 3] = 255
    return g2, l2


# ---------------------------------------------------------------- 武器 (立てた向きで描く)

def trident(opt):
    p = Pic()
    if opt == "a":
        d = p.layer("teal"); rect(d, 15, 11, 17, 31)
        d = p.layer("coral", bump=1); rect(d, 13, 10, 19, 13)
        d = p.layer("pearl"); poly(d, taper([(16, 11), (16, 6), (16, 1)], 2.8, 0.6))
        poly(d, taper([(14.5, 11), (12, 8), (9.5, 5), (7, 2.5)], 2.4, 0.5))
        poly(d, taper(mirror([(14.5, 11), (12, 8), (9.5, 5), (7, 2.5)]), 2.4, 0.5))
        d = p.layer("coral_pink"); poly(d, taper([(16, 4), (16, 1)], 1.6, 0.6))
        poly(d, taper([(9.5, 5), (7, 2.5)], 1.4, 0.4)); poly(d, taper([(22.5, 5), (25, 2.5)], 1.4, 0.4))
        d = p.layer("barnacle", bump=1); rect(13.5, 20, 18.5, 21.5); rect(13.5, 26, 18.5, 27.5)
        d = p.layer("glow_pearl", glow=True); ell(d, 16, 1.2, 0.7)
    elif opt == "b":
        d = p.layer("teal_deep"); rect(15, 11, 17, 31)
        d = p.layer("barnacle", bump=1)
        for y in (18, 23, 28):
            rect(13.5, y, 18.5, y + 1.5)
        d = p.layer("pearl", bump=1); rect(13, 10, 19, 13)
        d = p.layer("coral_pink"); poly(d, taper([(16, 11), (16, 6), (16, 1)], 3.0, 0.5))
        poly(d, taper([(14.5, 11), (11, 7), (8, 3)], 2.8, 0.4))
        poly(d, taper(mirror([(14.5, 11), (11, 7), (8, 3)]), 2.8, 0.4))
        d = p.layer("pearl"); poly(d, taper([(16, 9), (16, 4)], 1.0, 0.4))
        d = p.layer("glow", glow=True); ell(d, 16, 12.5, 0.9)
    else:  # c: 扇の鍔と銀の穂先
        d = p.layer("teal"); rect(15, 11, 17, 31)
        d = p.layer("silver_deep"); line(d, [(16, 13), (16, 30)], 0.7)
        d = p.layer("teal_lite", bump=1); poly(d, [(16, 12)] + arc(16, 12, 6.5, 180, 360, 16))
        d = p.layer("silver"); poly(d, taper([(16, 11), (16, 5), (16, 0.8)], 3.2, 0.4))
        poly(d, taper([(14.5, 11), (11, 7), (6.5, 2.5)], 3.0, 0.4))
        poly(d, taper(mirror([(14.5, 11), (11, 7), (6.5, 2.5)]), 3.0, 0.4))
        d = p.layer("barnacle", bump=1); rect(13.5, 22, 18.5, 23.5)
    return p


def shield(opt):
    """縦長 (幅:高さ ≒ 1:2)。盛り上がりは縁の珊瑚の瘤と、貝の筋・鋲。"""
    p = Pic()
    if opt == "a":   # 貝の扇: 縦長の丸みに放射状の筋と真珠の鋲、縁に珊瑚の瘤
        d = p.layer("teal", bump=1); ell(d, 16, 16, 8.5, 14)
        d = p.layer("coral"); ell(d, 16, 16, 7.2, 12.4)
        d = p.layer("coral_deep", bump=1)
        for a in range(0, 360, 45):
            c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
            line(d, [(16 + 1.2 * c, 16 + 1.2 * s), (16 + 6.6 * c, 16 + 11.4 * s)], 0.9)
        d = p.layer("pearl", bump=1); ell(d, 16, 16, 3.0)
        d = p.layer("silver"); ell(d, 15.2, 14.8, 1.0)
        d = p.layer("coral_pink", bump=1)
        for k in range(8):
            a = math.radians(k * 45 + 22)
            ell(d, 16 + 8.0 * math.cos(a), 16 + 13.0 * math.sin(a), 1.1)
    elif opt == "b":  # 凧型: 藤壺の縁 (盛り上がり) と珊瑚の面, 中央の帯
        d = p.layer("barnacle", bump=1); poly(d, [(16, 1.5), (24.5, 5), (23.5, 16), (16, 30.5), (8.5, 16), (7.5, 5)])
        d = p.layer("coral"); poly(d, [(16, 4.5), (22.6, 7.2), (21.8, 16), (16, 27.6), (10.2, 16), (9.4, 7.2)])
        d = p.layer("teal", bump=1); poly(d, [(14.6, 7.5), (17.4, 7.5), (17.4, 25.5), (14.6, 25.5)])
        d = p.layer("pearl", bump=1); ell(d, 16, 12, 1.8)
        d = p.layer("barnacle_deep", bump=1)
        for x, y in [(10.5, 9.5), (21.5, 9.5), (11.5, 22), (20.5, 22)]:
            ell(d, x, y, 1.0)
    else:             # 扇形の縁: 縦長の花びら状の縁、珊瑚の瘤
        d = p.layer("silver", bump=1)
        for k in range(6):
            a = math.radians(k * 60 - 90)
            ell(d, 16 + 6.4 * math.cos(a), 16 + 10.5 * math.sin(a), 3.6, 4.2)
        ell(d, 16, 16, 7.6, 12.2)
        d = p.layer("coral"); ell(d, 16, 16, 6.0, 10.4)
        d = p.layer("silver_deep", bump=1); line(d, arc(16, 16, 6.0, 200, 340, 20), 0.8)
        d = p.layer("teal", bump=1); poly(d, [(16 + 3.4 * math.cos(math.radians(k * 60)), 16 + 5.6 * math.sin(math.radians(k * 60))) for k in range(6)])
        d = p.layer("coral_pink", bump=1)
        for k in range(6):
            a = math.radians(k * 60 + 30)
            ell(d, 16 + 7.0 * math.cos(a), 16 + 11.0 * math.sin(a), 0.9)
        d = p.layer("pearl", bump=1); ell(d, 14.5, 13.5, 1.2)
    return p


def claw(opt):
    """潮招きの大鋏: 握り (下) → 掌と関節 (中) → 固定指と可動指 (上) の順に並ぶ、開いた鋏。
    指の先は光る。掌の上の瓦状の板は盛り上げる。"""
    p = Pic()
    palm = {"a": "coral_deep", "b": "coral", "c": "silver_deep"}[opt]
    finger = {"a": "coral", "b": "coral_pink", "c": "silver"}[opt]
    d = p.layer("barnacle_deep"); rect(14.5, 24, 17.5, 31)
    d = p.layer("rope", bump=0)
    for y in (26, 28.5):
        rect(14, y, 18, y + 0.8)
    d = p.layer(palm, bump=1); ell(d, 16, 18, 6.5, 4.2)
    d = p.layer(finger, bump=1); poly(d, taper([(12, 16), (10, 12), (9.5, 7), (10.5, 2.5)], 4.6, 1.6))
    poly(d, taper([(20, 16), (21.5, 12), (19.5, 7), (15.5, 3.5)], 4.0, 1.4))
    if opt != "c":   # 瓦状の板 (盛り上がり)
        d = p.layer(palm, bump=1)
        for row, y in enumerate((15.5, 18.5)):
            for x in (10.5, 13.5, 16.5, 19.5):
                rect(x + (1.2 if row else 0), y, x + 2.5 + (1.2 if row else 0), y + 2.2)
    d = p.layer("barnacle", bump=1)
    for x, y in [(10.5, 9.5), (19.5, 9.5), (15, 22.5)]:
        ell(d, x, y, 0.9)
    if opt == "c":
        d = p.layer("teal"); ell(d, 16, 14.5, 1.5)
    if opt == "a":
        d = p.layer("glow", glow=True); ell(d, 10.5, 2.2, 1.2); ell(d, 15.5, 3.0, 1.2)
    elif opt == "b":
        d = p.layer("pearl"); ell(d, 10.5, 2.2, 1.0); ell(d, 15.5, 3.0, 1.0)
    else:
        d = p.layer("glow", glow=True); ell(d, 10.5, 2.2, 1.0); ell(d, 15.5, 3.0, 1.0); ell(d, 16, 14.5, 0.8)
    return p


def harpoon(opt):
    """珊瑚憑きの銛: 既存の珊瑚の槍 (geo/gear/coral_spear) と同じ並び。
    骨の柄、珊瑚の輪 (桃・紫・橙)、青緑の握り、先端の光。穂先は珊瑚の返し付き。"""
    p = Pic()
    d = p.layer("bone"); rect(15, 7, 17, 31)
    d = p.layer("coral_pink", bump=1); rect(14.5, 9, 17.5, 10.5)
    d = p.layer("purple", bump=1); rect(14.5, 15, 17.5, 16.2)
    d = p.layer("orange", bump=1); rect(14.5, 21, 17.5, 22.2)
    d = p.layer("teal", bump=1); rect(14.5, 25, 17.5, 29)
    if opt == "a":
        d = p.layer("coral_pink", bump=1); poly(d, taper([(16, 8), (16, 3), (16, 0.6)], 3.6, 0.8))
        poly(d, taper([(13.5, 6.5), (10.5, 3.5)], 2.2, 0.6)); poly(d, taper(mirror([(13.5, 6.5), (10.5, 3.5)]), 2.2, 0.6))
        d = p.layer("glow", glow=True); ell(d, 16, 1.2, 0.7)
    elif opt == "b":
        d = p.layer("coral", bump=1); poly(d, taper([(16, 8), (16, 2.5), (16, 0.6)], 4.2, 0.6))
        poly(d, taper([(13.5, 7), (9.5, 4.5), (9, 2.5)], 2.6, 0.5)); poly(d, taper(mirror([(13.5, 7), (9.5, 4.5), (9, 2.5)]), 2.6, 0.5))
        d = p.layer("pearl"); poly(d, taper([(16, 7), (16, 3)], 1.2, 0.4))
        d = p.layer("glow", glow=True); ell(d, 16, 1.2, 0.7)
    else:
        d = p.layer("silver", bump=1); poly(d, taper([(16, 8), (16, 3), (16, 0.5)], 2.4, 0.4))
        d = p.layer("glow_pearl", glow=True); ell(d, 16, 1.2, 0.6)
    return p


# ---------------------------------------------------------------- アイコンだけ

def charm(opt):
    p = Pic()
    d = p.layer("barnacle"); line(d, [(12, 9), (16, 4), (20, 9)], 1.2)
    if opt == "a":   # 貝の扇の円盤
        d = p.layer("silver"); ell(d, 16, 18, 10)
        d = p.layer("pearl"); ell(d, 16, 18, 7.5)
        d = p.layer("coral"); poly(d, [(16, 24)] + arc(16, 24, 6, 200, 340, 14))
        d = p.layer("coral_deep")
        for a in (230, 270, 310):
            line(d, [(16, 24), (16 + 5.5 * math.cos(math.radians(a)), 24 + 5.5 * math.sin(math.radians(a)))], 0.7)
        d = p.layer("teal"); ell(d, 16, 9.5, 1.3)
    elif opt == "b":  # 鈴
        d = p.layer("silver"); poly(d, [(10, 11), (22, 11), (24.5, 22), (7.5, 22)])
        d = p.layer("barnacle"); rect(7, 21, 25, 23.5)
        d = p.layer("pearl"); poly(d, [(12, 13), (15, 13), (13.5, 19)])
        d = p.layer("teal"); ell(d, 16, 25, 1.6)
    else:            # 真珠と波
        d = p.layer("teal"); ell(d, 16, 18, 10)
        d = p.layer("teal_lite")
        for yy in (17, 20, 23):
            line(d, [(9, yy), (12, yy - 1.5), (16, yy), (20, yy - 1.5), (23, yy)], 0.8)
        d = p.layer("pearl"); ell(d, 16, 10.5, 2.8)
        d = p.layer("glow_pearl", glow=True); ell(d, 15, 9.5, 0.9)
    return p


def pearl(opt):
    p = Pic()
    if opt == "a":   # 真珠に銀の三日月 (光る)
        d = p.layer("pearl"); ell(d, 16, 16, 10.5)
        d = p.layer("silver"); ell(d, 14.5, 15.5, 1.5)
        d = p.layer("glow", glow=True); poly(d, arc(13, 16, 6.5, 115, 245, 16) + arc(16.5, 16, 5.2, 245, 115, 16))
    elif opt == "b":  # 藤壺の杯に真珠
        d = p.layer("barnacle"); poly(d, arc(16, 22, 9, 0, 180, 16) + [(7, 22)])
        d = p.layer("pearl"); ell(d, 16, 14, 8)
        d = p.layer("silver_deep"); ell(d, 15, 12, 4.8)
        d = p.layer("glow", glow=True); poly(d, arc(14, 14, 4, 120, 240, 14) + arc(16, 14, 3.4, 240, 120, 14))
    else:             # 波の線と三日月
        d = p.layer("pearl"); ell(d, 16, 16, 10.5)
        d = p.layer("teal_lite")
        for yy in (14, 18, 22):
            line(d, [(7, yy), (11, yy - 1.3), (15, yy), (19, yy - 1.3), (25, yy)], 0.9)
        d = p.layer("glow", glow=True); poly(d, arc(18, 11, 3.6, 120, 300, 14) + arc(19.2, 11, 2.9, 300, 120, 14))
    return p


def core(opt):
    p = Pic()
    frame = {"a": "stone", "b": "barnacle", "c": "silver"}[opt]
    d = p.layer(frame)
    poly(d, [(11, 3.5), (21, 3.5), (27.5, 10), (27.5, 22), (21, 28.5), (11, 28.5), (4.5, 22), (4.5, 10)])
    d = p.layer("stone_deep" if opt == "a" else ("barnacle_deep" if opt == "b" else "silver_deep"))
    poly(d, [(11, 5.5), (21, 5.5), (25.5, 10), (25.5, 22), (21, 26.5), (11, 26.5), (6.5, 22), (6.5, 10)])
    d = p.layer(frame)
    for yy in (9, 15, 21):
        line(d, [(9, yy), (23, yy + 1)], 0.7)
    if opt == "a":
        d = p.layer("teal"); poly(d, [(16, 9), (21, 16), (16, 23), (11, 16)])
        d = p.layer("glow", glow=True); ell(d, 16, 16, 2.4)
    elif opt == "b":
        d = p.layer("coral_pink"); poly(d, [(16, 9), (21, 16), (16, 23), (11, 16)])
        d = p.layer("glow_pearl", glow=True); ell(d, 15, 14.5, 1.6)
    else:
        d = p.layer("pearl"); poly(d, arc(16, 16, 7, 0, 300, 24))
        d = p.layer("coral"); ell(d, 16, 16, 2.5)
        d = p.layer("glow", glow=True); ell(d, 16, 16, 1.0)
    return p


# ---------------------------------------------------------------- 項目

# kind: weapon (立てて描き、-45 度に傾けて 3D にする) / shield (そのまま、背面に持ち手) / icon (アイコンだけ)
# target: 立てたときの長さ (単位)。盾は高さ。thick: 断面の中心の厚み (片側、3D ボクセル)
ITEMS = {
    "tide_shell_trident": {"kind": "weapon", "draw": trident, "target": 22.0, "thick": 4, "name": "潮殻の三叉槍"},
    "coral_shield": {"kind": "shield", "draw": shield, "target": 18.0, "thick": 4, "name": "珊瑚の盾"},
    "tidecaller_claw": {"kind": "weapon", "draw": claw, "target": 18.0, "thick": 3, "name": "潮招きの大鋏"},
    "coral_harpoon": {"kind": "weapon", "draw": harpoon, "target": 22.0, "thick": 4, "name": "珊瑚憑きの銛"},
    "tide_chime_charm": {"kind": "icon", "draw": charm, "name": "潮鳴りの護符"},
    "moontide_pearl": {"kind": "icon", "draw": pearl, "name": "月潮の真珠"},
    "tide_shell_core": {"kind": "icon", "draw": core, "name": "潮殻の心核"},
}
OPTS = ("a", "b", "c")


def build_option(item: str, opt: str):
    """1案を作る。icon: 採用の 32px アイコン。upright: 立てた向きの 32px (確認用)。
    grid/glow: 3D の格子 (3D の品だけ)。"""
    cfg = ITEMS[item]
    pic = cfg["draw"](opt)
    if cfg["kind"] == "weapon":
        icon, _, _ = compose(pic, ROT_DEG)
    else:
        icon, _, _ = compose(pic, 0.0)
    upright, _, _ = compose(pic, 0.0)
    grid = gl = None
    if cfg["kind"] in ("weapon", "shield"):
        grid, gl = voxelize_hi(pic, cfg["thick"], 0.0)
        if cfg["kind"] == "shield":
            grid = shield_back_style(grid)
            grid, gl = add_back_handle(grid, gl)
    return {"icon": icon, "upright": upright, "grid": grid, "glow": gl}


def tilt_faces(faces, grid_shape, deg):
    """プレビュー用: 面の一覧を、モデルの傾き (z 軸まわり deg) で回す。中心は格子の中央。"""
    a = math.radians(deg)
    R = np.array([[math.cos(a), -math.sin(a), 0.0], [math.sin(a), math.cos(a), 0.0], [0.0, 0.0, 1.0]])
    c = np.array(grid_shape, float) / 2
    out = []
    for f in faces:
        out.append(Face(R @ (f.origin - c) + c, R @ f.eu, R @ f.ev, f.tex, f.glow, f.shade))
    return out


def preview_3d(grid, glow, deg):
    faces = voxel_faces(grid, glow)
    if deg:
        faces = tilt_faces(faces, grid.shape[:3], deg)
    front = render(faces, yaw=0, pitch=0, size=(300, 300), margin=0.06)
    hand = render(faces, yaw=35, pitch=20, size=(300, 300), margin=0.06)
    return front, hand


def write_option(item, opt, res, outdir: Path):
    outdir.mkdir(parents=True, exist_ok=True)
    cfg = ITEMS[item]
    Image.fromarray(res["icon"], "RGBA").save(outdir / "icon.png")
    Image.fromarray(res["upright"], "RGBA").save(outdir / "upright.png")
    if res["grid"] is not None:
        np.savez_compressed(outdir / "voxels.npz", grid=res["grid"], glow=res["glow"],
                            target=np.float64(cfg["target"]),
                            tilt=np.float64(ROT_DEG if cfg["kind"] == "weapon" else 0.0))


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    results = {}
    for item, cfg in ITEMS.items():
        cols = []
        for opt in OPTS:
            res = build_option(item, opt)
            results[(item, opt)] = res
            write_option(item, opt, res, OUT_DIR / item / opt)
            ims = [icon_big(res["icon"], 8)]
            if res["grid"] is not None:
                deg = ROT_DEG if cfg["kind"] == "weapon" else 0
                front, hand = preview_3d(res["grid"], res["glow"], deg)
                front.save(OUT_DIR / item / opt / "front.png")
                hand.save(OUT_DIR / item / opt / "hand.png")
                ims += [front, hand]
                print(item, opt, "voxels", int(res["grid"][..., 3].astype(bool).sum()))
            cols.append((f"{opt.upper()} 案", ims))
        if cfg["kind"] != "icon":
            sheet(f"{cfg['name']} ({item}) ラフ 3 案", cols,
                  "上: 32px アイコン (刃先は右上) / 中: 3D 正面 / 下: 3D 斜め。1 画素 = 3 ボクセル、丸い断面と盛り上がり")\
                .save(OUT_DIR / f"compare_{item}.png")
    icons = []
    for item, cfg in ITEMS.items():
        if cfg["kind"] != "icon":
            continue
        for opt in OPTS:
            res = results[(item, opt)]
            icons.append((f"{opt.upper()} {cfg['name']}", [icon_big(res["icon"], 8)]))
    sheet("アイコンだけの品 3 案 (護符・真珠・心核)", icons, "32x32 / 8 倍表示 / 光る部分は明るい色").save(
        OUT_DIR / "compare_icons.png")


if __name__ == "__main__":
    main()
