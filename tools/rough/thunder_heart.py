"""雷翼の心臓 (第3の巨人 = 雷翼の巨神 の最終ボス) ラフ: 案A 雷の籠 / 案B 嵐の繭 / 案C 翼の心臓 x 3段階。

使い方 (ワークツリー直下で): python -B tools/rough/thunder_heart.py [a b c]
出力: docs/review/thunder_wing_roughs/heart/<a|b|c>/phaseN.(geo.json|tex.png|glow.png|render.png|front.png)
      docs/review/thunder_wing_roughs/heart/stats.json
      docs/review/thunder_wing_roughs/heart.png (比較シート), heart/bars.png (ボスバーの色案)

作り方:
- thunder_common の格子 (0.5 単位 = 1 画素) に 1 段階ずつ彫る。箱は骨ごとに貪欲法でまとめる。
- 心臓は全案で共通の形 (2 つの房 + 3 本の大動脈)。色だけこの巨神 (嵐の灰青・白銀・磨かれた銅) に替える。
- 段階の移り変わり: 第1段階 閉じている / 第2段階 開いて心臓が上がる (上昇気流) / 第3段階 形が崩れて閃光を放つ。
- 乱数は seed 固定。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import thunder_common as tc  # noqa: E402

OUT = tc.REVIEW / "heart"
SHEET = tc.REVIEW / "heart.png"
LO = (-24.0, -4.0, -24.0)
HI = (24.0, 44.0, 24.0)
CY = 18.0                       # 心臓の基準の高さ (籠・繭・翼の中心)
REND = 360
VIEW_34 = (35, 22)
VIEW_FRONT = (0, 6)
NAMES = {"a": "A 雷の籠", "b": "B 嵐の繭", "c": "C 翼の心臓"}
PHASE_NAMES = {1: "第1段階", 2: "第2段階", 3: "第3段階"}

# 心臓の色 (この巨神: 白銀の房、磨かれた銅の血管、稲妻の核)
LOBE = (176, 186, 212)
LOBE_D = (112, 122, 150)
LOBE_L = (216, 224, 238)
DULL_CU = (128, 88, 66)         # くすんだ銅 (第1段階の籠の棒)
BOLT_CORE = tc.BOLT
BOLT_RING = tc.BOLT_B


# ---------------------------------------------------------------- 形の部品 (共通の格子で使う)

def ellc(g, c, r, mod=None, coarse=False):
    """楕円体。coarse=True は 1 単位の格子の中心で測る (箱の数を減らす)。tide_heart と同じ考え。"""
    c = np.asarray(c, float)
    r = np.asarray(r, float)
    out = np.zeros(g.n, bool)
    s = tc.sl_of(g, c - r - 0.2, c + r + 0.2)
    X, Y, Z = g.X[s], g.Y[s], g.Z[s]
    if coarse:
        X, Y, Z = (np.floor(a) + 0.5 for a in (X, Y, Z))
    d = ((X - c[0]) / r[0]) ** 2 + ((Y - c[1]) / r[1]) ** 2 + ((Z - c[2]) / r[2]) ** 2
    if mod is not None:
        d = d / np.maximum(mod(X, Y, Z), 1e-3) ** 2
    out[s] = d <= 1.0
    return out


def meridian(phi, Rh, Ry, t0=0.0, t1=math.pi, n=12, cy=CY):
    """籠の骨 (経線): 下の極 (θ=0) から上の極 (θ=π) へ、楕円の上を通る点列"""
    pts = []
    for k in range(n + 1):
        th = t0 + (t1 - t0) * k / n
        pts.append((Rh * math.sin(th) * math.cos(phi), cy - Ry * math.cos(th), Rh * math.sin(th) * math.sin(phi)))
    return pts


def torus(c, R, n=40):
    """輪の中心線 (水平、中心 c、半径 R)。tube で太らせる"""
    pts = []
    for k in range(n + 1):
        a = 2 * math.pi * k / n
        pts.append((c[0] + R * math.cos(a), c[1], c[2] + R * math.sin(a)))
    return pts


def polyline_samples(pts, n):
    """折れ線の上を弧長で等間隔に n 点 (位置, 接線) を取る"""
    P = np.asarray(pts, float)
    seg = np.diff(P, axis=0)
    L = np.linalg.norm(seg, axis=1)
    cum = np.concatenate([[0.0], np.cumsum(L)])
    out = []
    for s in np.linspace(0, cum[-1], n):
        i = int(min(max(np.searchsorted(cum, s, side="right") - 1, 0), len(L) - 1))
        t = (s - cum[i]) / max(L[i], 1e-9)
        out.append((P[i] + seg[i] * t, seg[i] / max(L[i], 1e-9)))
    return out


def feather_row(g, name, pts, n, center, rad, lift, body=(tc.STORM, tc.STORM_L), tip=tc.ASH_W):
    """腕に沿って羽の板を並べる。板の向きは中心から外向き (羽は外を向く)。
    rad = (根元の半径, 先の半径)、lift = 腕から外へ浮かす量。tip は先 2 枚の色。"""
    center = np.asarray(center, float)
    for k, (p, _) in enumerate(polyline_samples(pts, n)):
        f = p - center
        f = f / max(float(np.linalg.norm(f)), 1e-9)
        r = rad[0] + (rad[1] - rad[0]) * k / max(n - 1, 1)
        m = tc.plate(g, p + f * lift, f, r, 1.0)
        col = tip if k >= n - 2 else body[k % 2]
        g.put(name, m, col)


def rod_at(phi, y, cy=CY):
    """第1,2段階の籠の棒 (縦、少し膨らむ) の、高さ y の位置"""
    dy = abs(y - cy) / 14.5
    R = 12.0 + (1.0 - min(dy, 1.0)) * 1.0
    return (R * math.cos(phi), y, R * math.sin(phi))


# ---------------------------------------------------------------- 心臓 (全案で共通の形、色はこの巨神)

def heart(g, cy, ph, dz=0.0):
    """2 つの房 + 上の切れ込み + 前の核の座 + 3 本の大動脈。段階ごとに核の大きさと色を変える。"""
    X, Y, Z = g.X, g.Y, g.Z
    lc = {1: LOBE, 2: LOBE, 3: LOBE_L}[ph]
    ld = {1: LOBE_D, 2: LOBE_D, 3: LOBE}[ph]
    rc = {1: 2.0, 2: 2.5, 3: 2.8}[ph]
    lobes = (ellc(g, (-2.4, cy, dz), (4.8, 5.2, 4.6), coarse=True)
             | ellc(g, (2.4, cy, dz), (4.8, 5.2, 4.6), coarse=True)
             | ellc(g, (0, cy - 3.6, dz), (3.4, 3.8, 3.4), coarse=True))
    lobes &= ~((np.abs(X) < 0.6) & (Y > cy + 3.6))
    lobes &= ~ellc(g, (0, cy, dz + 4.6), (2.4, 2.4, 2.4))
    Xc, Yc, Zc = (np.floor(a) + 0.5 for a in (X, Y, Z))
    vein = lobes & (np.sin(Xc * 1.7 + Yc * 1.1) * np.cos(Zc * 1.3) > 0.94)
    g.put("heart", lobes & ~vein, tc.tint(g, lc, ld, 0.25))
    g.put("heart_vein", vein, tc.COPPER)
    base = (0.0, cy + 4.0, dz)
    arts = [
        [base, (-0.8, cy + 6.0, dz + 0.6), (-2.2, cy + 7.6, dz + 0.4)],
        [base, (0.9, cy + 6.4, dz - 0.4), (2.6, cy + 8.2, dz - 0.8)],
        [base, (0.2, cy + 7.0, dz + 1.0), (0.4, cy + 9.0, dz + 2.2)],
    ]
    for pts in arts:
        g.put("vessel", tc.tube(g, pts, [0.8, 0.6, 0.45]), tc.tint(g, tc.COPPER, tc.COPPER_L, 0.3))
    core = ellc(g, (0, cy, dz + 3.5), (rc, rc, rc))
    eye = ellc(g, (0, cy, dz + 3.5), (rc - 0.9, rc - 0.9, rc - 0.9))
    g.put("core", core & ~eye, BOLT_RING, glow=True, over=True)
    g.put("core_eye", core & eye, BOLT_CORE, glow=True, over=True)


# ---------------------------------------------------------------- 案A 雷の籠

def build_a(g, ph):
    hy = CY + (4.0 if ph == 2 else 0.0)
    heart(g, hy, ph)
    rib_phis = [math.radians(a) for a in (0, 60, 120, 180, 240, 300)]
    rod_phis = [math.radians(a) for a in (45, 135, 225, 315)]
    rib_col = tc.tint(g, tc.BONE, tc.BONE_D, 0.3)
    if ph == 1:
        # 閉じた籠: 6 本の肋が全部つながり、赤道に輪、下に竜骨。棒は くすんだ銅 で、先だけ少し光る
        for a in rib_phis:
            g.put("rib", tc.tube(g, meridian(a, 11.0, 12.0), 0.75), rib_col)
        g.put("girdle", tc.tube(g, torus((0, CY, 0), 11.0), 0.6), tc.BONE_D)
        g.put("keel", tc.tube(g, [(0, CY - 12, -9), (0, CY - 11.5, 0), (0, CY - 12, 9)], 0.9), tc.BONE_D)
        for a in rod_phis:
            g.put("rod", tc.tube(g, [rod_at(a, CY - 14.5), rod_at(a, CY), rod_at(a, CY + 14.5)], 0.85), DULL_CU)
        for a in rod_phis:
            for y in (CY - 14.5, CY + 14.5):
                p = np.array(rod_at(a, y))
                g.put("rod_tip", tc.box_m(g, p - 0.6, p + 0.6), BOLT_RING, glow=True)
    elif ph == 2:
        # 半分開いた籠: 前の 2 本の肋が外れ、上の半分がなくなる (碗の形)。心臓は上へ。
        # 棒は磨かれた銅に戻り、棒と棒のあいだに光の弧が渡る
        for i, a in enumerate(rib_phis):
            if i in (1, 2):
                continue
            g.put("rib", tc.tube(g, meridian(a, 11.0, 12.0, t1=0.62 * math.pi), 0.75), rib_col)
        g.put("keel", tc.tube(g, [(0, CY - 12, -9), (0, CY - 11.5, 0), (0, CY - 12, 9)], 0.9), tc.BONE_D)
        for a in rod_phis:
            g.put("rod", tc.tube(g, [rod_at(a, CY - 14.5), rod_at(a, CY), rod_at(a, CY + 14.5)], 0.85), tc.COPPER_L)
            for y in (CY - 14.5, CY + 14.5):
                p = np.array(rod_at(a, y))
                g.put("rod_tip", tc.box_m(g, p - 0.6, p + 0.6), BOLT_RING, glow=True)
        pairs = [(45, 135), (135, 225), (225, 315), (315, 45)]
        for k, (a0, a1) in enumerate(pairs):
            p0 = np.array(rod_at(math.radians(a0), CY + 11.0))
            p1 = np.array(rod_at(math.radians(a1), CY + 11.0))
            m = tc.zigzag(g, p0, p1, 1.6, 5, 0.45, seed=k)
            g.put("arc", m, BOLT_RING, glow=True)
    else:
        # 砕けた籠: 肋は短い切れ端だけ (交互)。棒は外れて軌道を回る。中に稲妻の球。
        for i, a in enumerate(rib_phis):
            if i % 2 == 0:
                pts = meridian(a, 11.0, 12.0, t0=0.0, t1=0.28 * math.pi)
            else:
                pts = meridian(a, 11.0, 12.0, t0=0.72 * math.pi, t1=math.pi)
            g.put("rib", tc.tube(g, pts, 0.7), rib_col)
        g.put("keel", tc.tube(g, [(0, CY - 12, -9), (0, CY - 11.5, -2)], 0.9), tc.BONE_D)
        # 稲妻の球: 殻 (暗い青灰) に、まばらな光の粒
        ball = ellc(g, (0, CY, 0), (8.5, 8.5, 8.5)) & ~ellc(g, (0, CY, 0), (7.4, 7.4, 7.4))

        def sparkle(mm):
            return (np.sin(g.X[mm] * 2.3) + np.cos(g.Y[mm] * 1.9) + np.sin(g.Z[mm] * 2.7)) > 0.8

        g.put("ball", ball, tc.tint(g, tc.STORM_L, tc.STORM, 0.4), glow=sparkle)
        # 球から出る稲妻の舌 (球の外へ)
        for k in range(6):
            a = 2 * math.pi * k / 6 + 0.3
            d = np.array([math.cos(a), 0.35 * math.sin(2 * a), math.sin(a)])
            m = tc.zigzag(g, d * 8.0 + [0, 0, 0] + np.array([0, CY, 0]),
                          d * 12.0 + np.array([0, CY, 0]), 1.2, 4, 0.45, seed=k)
            g.put("tongue", m, BOLT_RING, glow=True)
        # 軌道を回る銅の棒の欠片 (6 個、交互に太さ)
        for k in range(6):
            a = math.radians(60 * k + 15)
            p = np.array([16.0 * math.cos(a), CY + 4.0 * math.sin(2 * a), 16.0 * math.sin(a)])
            hh = 1.8 if k % 2 == 0 else 1.2
            g.put("orbit", tc.box_m(g, p - [0.6, hh, 0.6], p + [0.6, hh, 0.6]), tc.COPPER_L)
    return {"rib": (0, CY, 0)}


# ---------------------------------------------------------------- 案B 嵐の繭

BAND_Y = [-10.0, -6.0, -2.0, 2.0, 6.0, 10.0]     # 雲の層 (中心からの高さ)
BAND_R = [12.5, 12.2, 11.8, 11.2, 10.2, 8.4]


def cloud_mod(X, Y, Z):
    a = np.arctan2(Z, X)
    return 1 + 0.06 * np.cos(9 * a + 0.7 * Y) + 0.04 * np.sin(5 * a - 1.3 * Y)


def build_b(g, ph):
    hy = CY + (4.0 if ph == 2 else 0.0)
    heart(g, hy, ph)
    if ph in (1, 2):
        for k, (dy, R) in enumerate(zip(BAND_Y, BAND_R)):
            c = (0.0, CY + dy, 0.0)
            band = ellc(g, c, (R, 1.0, R), mod=cloud_mod, coarse=True) & ~ellc(g, c, (R - 3.0, 9.0, R - 3.0))
            if ph == 2:
                # 上の層を前から裂く (扇形に広がる切れ目)
                wedge = (g.Z > 0.5) & (np.abs(g.X) < 3.0 + np.maximum(g.Y - (CY - 8), 0) * 0.55) & (g.Y > CY - 8)
                band &= ~wedge
            g.put(f"band{k}", band, tc.tint(g, tc.STORM_D if k % 2 else tc.STORM, tc.STORM_L, 0.3))
        if ph == 1:
            # 上と下のふた (繭が閉じている)
            g.put("cap", tc.plate(g, (0, CY + 12.5, 0), (0, 1, 0), 7.0, 1.0), tc.STORM_L)
            g.put("cap", tc.plate(g, (0, CY - 12.5, 0), (0, 1, 0), 6.5, 1.0), tc.STORM)
        # 層のあいだの空間に稲妻の弧 (第1段階は 5 つの隙間、第2段階は下の 2 つ)
        gaps = range(5) if ph == 1 else range(2)
        for k in gaps:
            y0 = CY + BAND_Y[k] + 1.2
            y1 = CY + BAND_Y[k + 1] - 1.2
            for j in range(4):
                a = 2 * math.pi * j / 4 + 0.4 * k
                R0 = BAND_R[k] - 1.6
                R1 = BAND_R[k + 1] - 1.6
                p0 = (R0 * math.cos(a), y0, R0 * math.sin(a))
                p1 = (R1 * math.cos(a + 0.25), y1, R1 * math.sin(a + 0.25))
                g.put("arc", tc.zigzag(g, p0, p1, 0.9, 3, 0.45, seed=k * 4 + j), BOLT_RING, glow=True)
        if ph == 2:
            # 上の層の風見 (羽根の板が、切れた縁から外へ立つ)
            for j in range(6):
                a = 2 * math.pi * j / 6 + 0.5
                if math.sin(a) > 0.2:
                    continue
                R = 10.6
                p = (R * math.cos(a), CY + 12.0, R * math.sin(a))
                tan = (-math.sin(a), 0.0, math.cos(a))
                g.put("vane", tc.plate(g, p, tan, 2.2, 0.5), tc.ASH_W_D)
    else:
        # 眼: 層はなくなり、眼の壁 (輪) と渦の腕だけ。中心は晴れて心臓が見える
        wall = tc.tube(g, torus((0, CY, 0), 10.5), 2.0)

        def spark(mm):
            return (np.sin(g.X[mm] * 1.9 + g.Z[mm] * 2.3) + np.cos(g.Y[mm] * 2.7)) > 1.5

        g.put("eyewall", wall, tc.tint(g, tc.STORM_L, tc.STORM_D, 0.3), glow=spark)
        for a0 in (0.0, 2 * math.pi / 3, 4 * math.pi / 3):
            ts = np.linspace(0, 1, 10)
            pts = [(( 10.5 + 7.0 * t) * math.cos(a0 + 2.2 * t), CY + 3.0 * t - 1.5,
                    (10.5 + 7.0 * t) * math.sin(a0 + 2.2 * t)) for t in ts]
            g.put("arm", tc.tube(g, pts, [1.8 - 0.9 * t for t in ts]), tc.tint(g, tc.STORM, tc.STORM_L, 0.3))
        # 閃光: 眼から外へ 6 本の光の筋 (第3段階の見せ場)
        for j in range(6):
            a = 2 * math.pi * j / 6 + 0.2
            pts = [(4.5 * math.cos(a), CY, 4.5 * math.sin(a)), (14.0 * math.cos(a), CY + 0.5, 14.0 * math.sin(a))]
            g.put("flash", tc.tube(g, pts, [0.8, 0.3]), tc.BOLT, glow=True)
    return {}


# ---------------------------------------------------------------- 案C 翼の心臓

def build_c(g, ph):
    hy = CY + (4.0 if ph == 2 else 0.0)
    heart(g, hy, ph)
    center = (0.0, hy, 0.0)
    piv = {}
    feather_body = (tc.STORM, tc.STORM_L)
    if ph == 1:
        # 畳んだ翼: 2 枚の翼が心臓を外から包み、前の下で重なる
        for s, name in ((-1, "wing_l"), (1, "wing_r")):
            arm = [(s * x, CY + y, z) for x, y, z in [(5, 6, -2), (9.5, 3, 0), (10.5, -2, 3), (6, -8, 6), (1.5, -10, 7.5)]]
            g.put(name, tc.tube(g, arm, [0.9, 0.8, 0.7, 0.6, 0.5]), tc.BONE_D)
            feather_row(g, name, arm, 11, center, (3.0, 1.8), 2.2, body=feather_body)
            piv[name] = (s * 5.0, CY + 6.0, -2.0)
    elif ph == 2:
        # 開いた翼: 大きな翼が 2 枚 + 上の小さな翼が 2 枚 (計 4 枚)。心臓は翼の中で上がる
        for s, name in ((-1, "wing_l"), (1, "wing_r")):
            arm = [(s * x, CY + y, z) for x, y, z in [(5, 7, -1), (12, 11, -2), (19, 15, -2), (22, 9, 1), (17, 3, 3)]]
            g.put(name, tc.tube(g, arm, [1.0, 0.9, 0.8, 0.7, 0.6]), tc.BONE_D)
            feather_row(g, name, arm, 10, center, (3.2, 2.0), 2.2, body=feather_body)
            piv[name] = (s * 5.0, CY + 7.0, -1.0)
            crest = [(s * x, CY + y, z) for x, y, z in [(3, 10, -3), (8, 19, -3), (6, 24, -2)]]
            cname = "crest_l" if s < 0 else "crest_r"
            g.put(cname, tc.tube(g, crest, [0.7, 0.6, 0.5]), tc.BONE_D)
            feather_row(g, cname, crest, 6, center, (2.4, 1.4), 2.0, body=feather_body)
            piv[cname] = (s * 3.0, CY + 10.0, -3.0)
    else:
        # 羽の抜けた翼: 骨だけ (腕と 3 本の指の骨、肘の関節は銅)。骨のあいだを稲妻が渡る
        for s, name in ((-1, "wing_l"), (1, "wing_r")):
            arm = [(s * x, CY + y, z) for x, y, z in [(5, 6, -2), (11, 8, -1), (16, 5, 0)]]
            g.put(name, tc.tube(g, arm, [0.9, 0.8, 0.7]), tc.tint(g, tc.BONE, tc.BONE_D, 0.3))
            B = np.array(arm[-1], float)
            for end in [(22, 10, -1), (21, 0, -3), (15, -6, -2)]:
                e = np.array([s * end[0], CY + end[1], end[2]], float)
                g.put("finger", tc.tube(g, [B, e], [0.7, 0.4]), tc.BONE_D)
            g.put("joint", ellc(g, B, (1.2, 1.2, 1.2)), tc.COPPER)
            piv[name] = (s * 5.0, CY + 6.0, -2.0)
        rng = np.random.default_rng(9)
        for j in range(6):
            a = 2 * math.pi * j / 6 + rng.uniform(-0.3, 0.3)
            p = np.array([14.0 * math.cos(a), CY + rng.uniform(-6, 10), 14.0 * math.sin(a)])
            f = p / np.linalg.norm(p)
            g.put("shed", tc.plate(g, p, f, 2.2, 0.8), tc.STORM_D)
        # 稲妻の網: 指の先どうし、心臓の上と下、後ろを渡る
        for k, (a, b, amp, n, seed) in enumerate([
            ((-21, CY + 10, -1), (21, CY + 10, -1), 2.5, 10, 1),
            ((-15, CY - 6, -2), (15, CY - 6, -2), 2.0, 9, 2),
            ((-20, CY, -5), (20, CY, -5), 2.0, 9, 3),
        ]):
            g.put("web", tc.zigzag(g, a, b, amp, n, 0.5, seed=seed), BOLT_RING, glow=True)
    return piv


BUILDERS = {"a": build_a, "b": build_b, "c": build_c}


# ---------------------------------------------------------------- 書き出し・描画・シート

def render_pair(d, ph):
    im34 = Image.open(d / f"phase{ph}.render.png")
    imf = Image.open(d / f"phase{ph}.front.png")
    return im34, imf


def build_one(opt, ph):
    g = tc.Grid(LO, HI, seed=100 * ph + ord(opt))
    piv = BUILDERS[opt](g, ph) or {}
    hy = CY + (4.0 if ph == 2 else 0.0)
    pivots = {n: (0.0, hy, 0.0) for n in g.names}
    pivots.update(piv)
    return g, pivots


def run(opts):
    OUT.mkdir(parents=True, exist_ok=True)
    stats_path = OUT / "stats.json"
    stats = json.loads(stats_path.read_text(encoding="utf-8")) if stats_path.exists() else {}
    for opt in opts:
        d = OUT / opt
        d.mkdir(parents=True, exist_ok=True)
        for ph in (1, 2, 3):
            g, pivots = build_one(opt, ph)
            geo, tex, gtex, st = tc.export(g, f"thunder_heart_{opt}{ph}", pivots=pivots)
            st = tc.save(d, f"phase{ph}", geo, tex, gtex, st)
            faces = tc.faces_of(geo, tex, gtex)
            tc.draw(faces, VIEW_34[0], VIEW_34[1], size=REND).save(d / f"phase{ph}.render.png")
            tc.draw(faces, VIEW_FRONT[0], VIEW_FRONT[1], size=REND).save(d / f"phase{ph}.front.png")
            stats[f"{opt}{ph}"] = st
            print(opt, ph, st, flush=True)
    tc.write_stats(stats_path, stats)
    return stats


def sheet_from_disk(stats):
    cols = []
    for opt in "abc":
        d = OUT / opt
        ims = []
        for ph in (1, 2, 3):
            im34, _ = render_pair(d, ph)
            ims.append(tc.label(im34, f"{PHASE_NAMES[ph]}  {stats[f'{opt}{ph}']['cubes']} 箱"))
        for ph in (1, 2, 3):
            _, imf = render_pair(d, ph)
            ims.append(tc.label(imf, f"{PHASE_NAMES[ph]} 正面"))
        im34_3, _ = render_pair(d, 3)
        ims.append(tc.label(tc.night(im34_3), "第3段階 暗い部屋 (発光の読みやすさ)"))
        cols.append((NAMES[opt], ims))
    sh = tc.sheet("雷翼の心臓 ラフ (3案 x 3段階)", cols,
                  note="上3段: 斜めからの絵 / 中3段: 正面 / 最下段: 第3段階を暗い部屋で見た絵。すべて回さない箱 (重なりなし)")
    sh.save(SHEET)


def bars():
    """ボスバーの色案 (第1〜第3段階)"""
    W, Hh = 720, 260
    im = Image.new("RGB", (W, Hh), (18, 18, 26))
    d = ImageDraw.Draw(im)
    d.text((20, 14), "ボスバーの色案 (段階ごと)", font=tc.font(24), fill=(235, 235, 245))
    spec = [(1, "第1段階 閉じた籠・繭・翼で包む", (150, 200, 255), 1.0),
            (2, "第2段階 開いた籠・上昇する心臓", (255, 226, 120), 0.75),
            (3, "第3段階 閃光の眼・稲妻の球", (250, 246, 210), 0.5)]
    for i, (ph, label, col, frac) in enumerate(spec):
        y = 64 + i * 62
        d.text((20, y), label, font=tc.font(18), fill=(200, 200, 215))
        d.rectangle((20, y + 26, 700, y + 46), fill=(40, 40, 52))
        d.rectangle((20, y + 26, 20 + int(680 * frac), y + 46), fill=col)
        d.rectangle((20, y + 26, 700, y + 46), outline=(90, 90, 110))
    return im


def main():
    opts = sys.argv[1:] or ["a", "b", "c"]
    stats = run(opts)
    if all((OUT / o / "phase3.render.png").exists() for o in "abc") and all(f"{o}3" in stats for o in "abc"):
        sheet_from_disk(stats)
        bars().save(OUT / "bars.png")


if __name__ == "__main__":
    main()
