"""B 案「焼け紙の人」: プレイヤーの輪郭を切り抜いた焼け紙。薄い板 (厚み 2〜2.4) を面取りした低ポリで、縁は千切れて欠け、
ところどころ焼け抜けた穴 (テクスチャの alpha 0) が前から後ろへ貫く。縁と穴のふちに冷たい青白い火の粉。横から見ると細い線になる。"""
from __future__ import annotations

from common import *  # noqa
from common import _mk  # noqa

CHAR = [(16, 14, 18), (36, 32, 36), (68, 62, 62), (108, 100, 94), (150, 140, 128)]
SOOT = [(8, 7, 9), (16, 14, 17), (28, 24, 27), (46, 41, 44)]
FIRE = [(110, 150, 200), (170, 205, 240), (230, 245, 255)]
EYE = [(228, 243, 255), (228, 243, 255), (235, 247, 255)]

PAL = dict(mats={
    "paper": dict(ramp=CHAR, base=0.5, lit=0.3, noise=0.12, min=0, up=0.04),
    "edge": dict(ramp=SOOT, base=0.5, lit=0.3, noise=0.1, min=0),
    "eye": dict(ramp=EYE, base=0.5, lit=0.2, noise=0.0),
    "mist": dict(ramp=FIRE, base=0.6, lit=0.3, noise=0.05),
}, glow_mats=["eye", "mist"])


def outline(cx, cy, hw, hh, n, seed, jag=0.18, bites=(), tears=(), expo=0.5, phase=0.0):
    """星形の輪郭 (中心から見た半径 r(θ))。角が立った四角 (スーパー楕円) を基に、ぎざぎざ jag と
    欠け (bites: 頂点番号 → 半径の倍率) と千切れの突起 (tears) を足す。θ = 0 は上 (+Y)、時計回りに x が正。"""
    pts = []
    for k in range(n):
        a = math.radians(phase + 360.0 * k / n)
        s, c = math.sin(a), math.cos(a)
        x = hw * math.copysign(abs(s) ** expo, s)
        y = hh * math.copysign(abs(c) ** expo, c)
        m = 1.0 + jag * (hash1(k * 3 + seed, seed) - 0.5) * 2.0
        if k in bites:
            m *= bites[k]
        if k in tears:
            m *= tears[k]
        pts.append((cx + x * m, cy + y * m))
    return pts


def paper(bone, mat, pts, zc, t, dome=0.12, edge_mat="edge"):
    """輪郭 pts [(x, y)] の薄い板。前・中・後ろの 3 つの輪 (前後は一回り小さい面取り) と、
    前後の面は中心から扇の形。z は前 = -Z。輪郭は中心から星形であること。"""
    c = np.mean(pts, axis=0)
    n = len(pts)
    ctr = V(c[0], c[1], zc)

    def ring(sc, z):
        return [V(c[0] + (p[0] - c[0]) * sc, c[1] + (p[1] - c[1]) * sc, z) for p in pts]

    rf, rm, rb = ring(0.84, zc - t), ring(1.0, zc), ring(0.84, zc + t)
    faces = []
    for ra, rbk, mt in ((rf, rm, edge_mat), (rm, rb, edge_mat)):
        for j in range(n):
            quad = [ra[j], ra[(j + 1) % n], rbk[(j + 1) % n], rbk[j]]
            fc = np.mean(quad, axis=0)
            e = rm[(j + 1) % n] - rm[j]
            no = V(e[1], -e[0], 0.0)           # 時計回りの輪郭の外向き (xy)
            if np.linalg.norm(no) < 1e-9:
                continue
            no = no / np.linalg.norm(no)
            # 輪郭の向きは θ が時計回り (上から右へ) なので、外向きは (dy, -dx) の符号が合うか中心との比較で決める
            if np.dot(no[:2], fc[:2] - np.array(c)) < 0:
                no = -no
            f = _mk(quad, bone, mt, fc - no * 1.0)
            if f:
                faces.append(f)
    for ring_, z, sgn in ((rf, zc - t - dome, -1), (rb, zc + t + dome, 1)):
        top = V(c[0], c[1], z)
        for j in range(n):
            f = _mk([top, ring_[j], ring_[(j + 1) % n]], bone, mat, ctr)
            if f:
                faces.append(f)
    return faces


def build():
    F = []
    T = 1.15
    # 輪郭は「頭 8x8 / 胴 8x12 / 腕 4x12 / 脚 4x12」の人の形。各板は少しずつ前後にずらして重ならない
    # ---- 頭 (冠が千切れ、片側が欠ける)
    head = outline(0.0, 28.2, 3.9, 4.3, 16, 11, jag=0.12, bites={3: 0.62, 4: 0.5, 5: 0.7, 11: 0.74, 12: 0.58}, tears={0: 1.3, 1: 1.55, 2: 1.15, 15: 1.28, 8: 1.12}, phase=0.0)
    F += paper("head", "paper", head, -0.2, 1.1)
    # 目の切れ目 (焼け抜けた縦の穴の奥が光る。片目、真ん中からずれる)
    ez = -0.2 - 1.1 - 0.12 - 0.4
    eye = [V(-1.3, 26.6, ez), V(-0.85, 27.6, ez), V(-0.85, 29.4, ez), V(-1.3, 30.4, ez), V(-1.75, 29.4, ez), V(-1.75, 27.6, ez)]
    F += poly("eyes", "eye", eye, V(-1.3, 28.5, ez + 3.0))
    # ---- 胴 (肩の片方が千切れ、裾がぎざぎざ)
    torso = outline(0.0, 18.0, 4.2, 6.2, 22, 21, jag=0.12, bites={1: 0.84, 2: 0.55, 3: 0.5, 4: 0.78, 17: 0.8, 18: 0.52, 19: 0.6, 20: 0.88}, tears={8: 1.12, 9: 1.32, 10: 1.1, 11: 1.45, 12: 1.15, 13: 1.38}, phase=0.0)
    F += paper("body", "paper", torso, 0.0, 0.95)
    # ---- 腕 (片方は肘から先が千切れて短い)
    armr = outline(5.5, 18.0, 1.9, 6.4, 12, 31, jag=0.12, bites={4: 0.62, 7: 0.7}, tears={5: 1.4, 6: 1.55, 8: 1.3}, phase=0.0)
    F += paper("mantle", "paper", armr, 0.2, 0.95)
    arml = outline(-5.6, 21.0, 1.9, 3.8, 12, 41, jag=0.14, bites={2: 0.62, 9: 0.6, 10: 0.7}, tears={5: 1.7, 6: 2.2, 7: 1.6}, phase=0.0)
    F += paper("mantle", "paper", arml, -0.1, 0.95)
    # ---- 脚 (裾が焼け落ちて長さがまちまち)
    legr = outline(2.0, 6.0, 2.0, 6.6, 12, 51, jag=0.12, bites={3: 0.8, 4: 0.7}, tears={5: 1.3, 6: 1.55}, phase=0.0)
    F += paper("hem", "paper", legr, 0.3, 1.0)
    legl = outline(-2.1, 6.4, 2.0, 6.2, 12, 61, jag=0.14, bites={7: 0.78}, tears={5: 1.7, 6: 1.35, 7: 1.0}, phase=0.0)
    F += paper("hem", "paper", legl, -0.2, 1.0)
    # ---- 欠片 (剥がれ落ちる紙片と火の粉)
    for i, (x, y, z, r) in enumerate([(-6.5, 27.0, -1.0, 0.6), (5.2, 31.0, 1.0, 0.5), (-2.0, 37.0, 0.5, 0.45),
                                      (7.5, 12.0, -2.0, 0.45), (-7.5, 9.0, 1.0, 0.4), (2.8, 1.0, -4.5, 0.4), (-4.0, 3.0, -3.0, 0.35)]):
        F += tetra("mist", "mist", V(x, y, z), r, rot=i * 40.0, stretch=1.6)
    return dict(faces=F, pal=PAL, extra=extra)


def extra(ctx, col, glow, alpha):
    """焼け抜けた穴 (前後の面で同じ位置、alpha 0) とそのふちの火の粉、縁のほつれ、焼けこげのむら。"""
    col = col.copy()
    P, N = ctx["P"], ctx["N"]
    owned, INS = ctx["owned"], ctx["INS"]
    mats = ctx["mats"]
    paperm = (mats == "paper") & owned & INS
    cap = paperm & (np.abs(N[..., 2]) > 0.5)
    # 穴: 位置 (x, y) と板ごとの違い (bone) で決める連続な雑音。前後の面で同じ場所に空く
    seed = np.array([f.bone for f in ctx["faces"]])  # noqa
    bone_id = np.array([sum(ord(ch) for ch in f.bone) for f in ctx["faces"]])[ctx["fi"]]
    n1 = vnoise2(P[..., 0] * 0.34 + bone_id * 0.7, P[..., 1] * 0.34, 7)
    n2 = vnoise2(P[..., 0] * 0.83 + 5 + bone_id, P[..., 1] * 0.83, 3)
    hole = cap & (((n1 > 0.70) & (n2 > 0.32)) | ((n1 < 0.20) & (n2 < 0.55)))
    rim = cap & ~hole & (((n1 > 0.66) & (n2 > 0.28)) | ((n1 < 0.23) & (n2 < 0.6)))
    alpha = np.where(hole, 0, alpha).astype(np.uint8)
    col[rim] = (14, 12, 15)
    rim_glow = rim & (hash2(P[..., 0] * 3.0, P[..., 1] * 3.0, 5) < 0.07)
    col[rim_glow] = (170, 205, 240)
    glow = glow | rim_glow
    # 縁のほつれ: 面の縁 (出っ張る折れ目) の画素を、面ごとにまばらに抜く / 火の粉にする
    fr = hash1(ctx["LU"] * 7 + ctx["LV"] * 13 + ctx["fi"] * 29, 9)
    on_edge = (paperm | ((mats == "edge") & owned & INS)) & ctx["convex"]
    drop = on_edge & (fr < 0.30)
    alpha = np.where(drop, 0, alpha).astype(np.uint8)
    spark = on_edge & ~drop & (fr > 0.985)
    col[spark] = (170, 205, 240)
    glow = glow | spark
    # 縁の側面 (厚みの帯) に、冷たい火が這う暗い光の線
    side = (mats == "edge") & owned & INS
    ember = side & (hash1(ctx["LU"] * 3 + ctx["fi"] * 11, 14) < 0.34) & (ctx["LV"] % 2 == 0)
    col[ember] = (64, 92, 132)
    glow = glow | ember
    # 縁の焼け焦げ (輪郭に沿った暗い帯)
    burnt = paperm & (ctx["D"] < 2.6) & (ctx["K"] > 24)
    col[burnt] = np.array(SOOT)[np.clip((ctx["D"][burnt] * 1.2 + ctx["bay"][burnt] * 2).astype(int), 0, 3)]
    # 焼け色のむら (紙の茶と灰)
    soot = paperm & (vnoise2(P[..., 0] * 0.5, P[..., 1] * 0.5, 11) > 0.62) & (ctx["bay"] > -0.1)
    col[soot] = col[soot] * 0.7
    return col, glow, alpha
