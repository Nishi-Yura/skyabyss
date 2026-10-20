"""巨神シリーズの防具 (B 祭具の発展)。4 巨神で同じ言葉を使い、巨神ごとに素材と飾りが変わる。

共通の言葉 (4 巨神・3 案とも)
  兜  : 顔を開けた頭巾 + 額の環と宝石 + 頭の弧に沿った冠の刃の列 + 耳の上の側翼
  胸  : 襟 + 面取りの胸板 (中央に光の筋) + 巨神色の帯と留め具 + 腰の垂れ + 背の外套 (裾の形が巨神の素材)
  肩  : 丸い段の肩 (右が大きい) と、右肩の大きな飾り 1 本
  腕  : 袖 + 縁の環 + 外側の籠手
  脚  : 前垂れ + 面取りの草摺 + 膝の半球 + 縁の環
  靴  : 筒 + 縁の環 + 甲の板 + 足首の飾り
巨神ごとの素材と飾り
  岩鯨 = 鯨骨の反った刃と青灰の石 / 潮殻 = 珊瑚と貝の扇と真珠 / 雷翼 = 白い羽と銅 / 余燼 = 割れた玄武岩の角と溶岩の筋

  S1 凛  : 飾りを絞った細身 (冠 1 本、肩 1 段、外套は短い)
  S2 祭  : 標準 (冠 3 本 + 側翼、肩 2 段、帯の垂れ 2 本、外套は膝まで)
  S3 盛  : 布と飾りが多い (冠 5 本 + 側翼 2 対、肩 3 段、長い外套、垂れ 3 本、房、鈴口の袖)
階級: 通常 = 灰の縁 / レア = 青みの縁 + 部品 +1 + 光の筋 / 伝説 = 金の縁 + 背の環 + 肩 +1 + 光の筋が増える。"""
from __future__ import annotations

import math

from sk import *  # noqa: F401,F403
from sk import (V, tag, spk, feather, crv, nrm, rot_x, rot_y, rot_z, at, bx, ext, sring, dloft, bplate, ngon, slab, drape,
                lume, gem, band, ring_tube, mk)
from akit import ARM, LEG, BOOT
from lpkit import ellipsoid

VARS = {
    "S1": dict(name="S1 凛", cowl="cap", crest=1, fins=1, paul=1, cape=0.42, tabard=0, tails=1, tassel=0, bell=0, plates=1, n=10),
    "S2": dict(name="S2 祭", cowl="hood", crest=3, fins=1, paul=2, cape=0.80, tabard=1, tails=2, tassel=0, bell=1, plates=2, n=10),
    "S3": dict(name="S3 盛", cowl="hood", crest=7, fins=1, paul=3, cape=1.0, tabard=2, tails=3, tassel=0, bell=2, plates=3, n=10),
}


def hemp(T, cols):
    pat = {"whale": [0.0], "tide": [-0.2, -1.2], "thunder": [-0.1, -2.6], "ember": [-1.8, 0.0, -0.8, -2.8, -0.2, -1.4, 0.0]}[T]
    return [pat[i % len(pat)] for i in range(cols)]


def outline(T, w, L, sweep=0.0):
    """刃 (冠・翼・肩の飾り) の凸の輪郭。局所 y が伸びる向き。"""
    if T == "whale":      # 反った鯨骨の刃
        return [(-w * 0.5, 0), (w * 0.5, 0), (w * 0.3 + sweep * 0.5, L * 0.55), (sweep, L), (-w * 0.28 + sweep * 0.4, L * 0.5)]
    if T == "tide":       # 貝の扇
        return [(-w * 0.22, 0), (w * 0.22, 0), (w * 0.52, L * 0.42), (w * 0.4 + sweep * 0.3, L * 0.8), (sweep, L),
                (-w * 0.4 + sweep * 0.3, L * 0.8), (-w * 0.52, L * 0.42)]
    if T == "thunder":    # 羽
        return [(0, 0), (w * 0.55, L * 0.25), (w * 0.5, L * 0.66), (sweep, L), (-w * 0.5, L * 0.66), (-w * 0.55, L * 0.25)]
    return [(-w * 0.5, 0), (w * 0.5, 0), (w * 0.14 + sweep * 0.4, L * 0.6), (sweep, L), (-w * 0.38, L * 0.42)]   # 余燼の割れた角


def blade(T, bone, base, d, L, w, axis=(1, 0, 0), sweep=0.0, h=0.9, mat="motif"):
    """面取りした刃。base から d の向きへ L 伸びる。axis = 厚みの向き (おおよそ)。"""
    ey = nrm(V(*d))
    az = V(*axis)
    ez = nrm(az - ey * float(np.dot(az, ey)))
    ex = np.cross(ey, ez)
    R = np.stack([ex, ey, ez], 1)
    return bplate(bone, mat, "bevel" if mat == "plate" else mat, outline(T, w, L, sweep), c=base, R=R, h=h, ins=0.35, ch=0.3, dbl=True)


def horn(T, bone, base, d, L, w, bend=(0, 0, 0), n=(0, 0, 1)):
    """肩・足首の飾り (細長い 1 本)。"""
    base, d = V(*base), nrm(d)
    if T == "whale":
        return spk(bone, "motif", base, d, L, w * 0.55, sides=5, rings=3, bend=bend)
    if T == "tide":
        f = spk(bone, "motif", base, d, L, w * 0.5, sides=5, rings=2, bend=bend)
        if L > 3.2:
            side = nrm(np.cross(d, V(0, 0, 1)) if abs(d[2]) < 0.9 else np.cross(d, V(1, 0, 0)))
            mid = base + d * L * 0.5 + np.array(bend) * 0.25
            f += spk(bone, "motif2", mid, nrm(d + side * 0.9), L * 0.45, w * 0.3, sides=4, rings=1)
            f += spk(bone, "motif2", mid, nrm(d - side * 0.9), L * 0.38, w * 0.28, sides=4, rings=1)
        return f
    if T == "thunder":
        return feather(bone, "motif", base, base + d * L + np.array(bend), w, th=0.5, n=n)
    return spk(bone, "motif", base, d, L, w * 0.62, sides=3, rings=3, bend=bend, phase=30)


# ------------------------------------------------------------------ 兜

def helmet(T, rar, v):
    V_ = VARS[v]
    b = "armorHead"
    f = []
    cap = V_["cowl"] == "cap"
    lv = [(24.5, 4.8, -4.7, 4.8), (26.6, 5.1, -5.0, 5.3), (28.7, 5.1, -5.0, 5.3), (30.6, 4.7, -4.8, 4.9),
          (32.3, 3.7, -3.9, 4.0), (33.4, 1.9, -2.1, 2.3)]
    if cap:
        lv = [(25.8, 4.6, -4.6, 4.6), (27.4, 4.9, -4.9, 5.0), (29.2, 4.9, -4.9, 5.0), (31.0, 4.4, -4.5, 4.6),
              (32.5, 3.3, -3.6, 3.8), (33.4, 1.7, -1.9, 2.2)]
    rings = [sring(y, hx, zf, zb, n=12, p=2.9) for (y, hx, zf, zb) in lv]
    sh = dloft(b, "cloth", rings, caps=(False, "cloth"))
    ymax = 29.9 if not cap else 29.6
    for fc in sh:
        c = fc.verts.mean(0)
        if fc.verts[:, 1].max() < ymax + 1e-6 and fc.normal[2] < -0.35 and abs(c[0]) < 3.6:
            continue
        f.append(fc)
    y0 = 29.2     # 額の環
    f += dloft(b, "trim", [sring(y0, 5.12, -5.12, 5.5, n=12, p=2.9), sring(y0 + 0.35, 5.3, -5.3, 5.7, n=12, p=2.9),
                           sring(y0 + 1.15, 5.3, -5.3, 5.7, n=12, p=2.9), sring(y0 + 1.5, 5.06, -5.06, 5.44, n=12, p=2.9)],
               mats=["trim", "trim", "trim"])
    f += gem(b, (0, 30.0, -5.6), (1.4, 1.1, 0.55))
    if not cap:
        for s in (-1, 1):
            f += bplate(b, "plate", "bevel", ngon(3.0, 4.2, 8, taper=0.3), c=(s * 5.1, 26.6, -1.6), R=np.array([[0, 0, -1.0 * s], [0, 1.0, 0], [1.0 * s, 0, 0]]), h=0.8, ins=0.5, ch=0.35, bow=0.5)
    # 冠: S1 = 太い 1 枚の流れる刃 / S2 = 頭の弧に沿った刃の列 (額 → 頭頂 → うなじ) / S3 = 後頭の放射状の刃 (光背)
    n = V_["crest"]
    if v == "S1":
        f += blade(T, b, (0, 30.6, -3.6), (0, 0.86, 0.34), 6.4, 3.8, axis=(0, 0, 1), sweep=0.0, h=1.0)
    elif v == "S2":
        for i in range(n):
            t = (i + 0.5) / n
            a = math.radians(-62 + 140 * t)
            z, y = 4.9 * math.sin(a), 29.0 + 4.5 * math.cos(a)
            nz, ny = z / 5.0 ** 2, (y - 29.0) / 4.6 ** 2
            nn = math.hypot(nz, ny)
            d = (0, ny / nn * 0.8 + 0.3, nz / nn * 0.9 + 0.35)
            Lh = 3.4 + 3.0 * math.sin(math.pi * t)
            f += blade(T, b, (0, y - 0.7, z), d, Lh, 3.6 if T != "tide" else 4.0, axis=(1, 0, 0), sweep=0.9 + 0.7 * t)
    else:
        for i in range(n):
            a = -68 + 136.0 * i / (n - 1)
            ar = math.radians(a)
            L_ = 7.4 - 2.6 * abs(a) / 68.0 + (0.8 if i == n // 2 else 0)
            f += blade(T, b, (math.sin(ar) * 3.4, 31.0 + math.cos(ar) * 1.6, 3.0 + 0.4 * abs(a) / 68), (math.sin(ar), math.cos(ar), 0.14), L_,
                       3.4, axis=(0, 0, 1), sweep=-0.4 * math.sin(ar), h=0.9)
    # 側翼 (耳の上): 冠と同じ形の刃を左右へ
    nf = V_["fins"] + (1 if (rar != "common" and V_["fins"] == 0) else 0)
    for s in (-1, 1):
        for k in range(nf):
            f += blade(T, b, (s * 5.0, 29.8 - 1.3 * k, 0.4 - 1.6 * k), (s * 0.8, 0.45 - 0.1 * k, 0.5 + 0.4 * k),
                       (5.0 if k == 0 else 3.6) * (1.12 if rar == "legend" else 1.0), 2.4 if k == 0 else 2.0,
                       axis=(0, 0, 1), sweep=-s * 0.5 * 0)
    return {b: f}


# ------------------------------------------------------------------ 胸・肩・腕

def pauldron(T, ab, s, i, rar, big):
    """丸い段の肩。i = 0 が一番上の半球、1 以降は外へ広がる帯。"""
    g = []
    n = 8
    if i == 0:
        rr_ = [(22.9, 3.95, 3.5, 0.0), (23.8, 3.75, 3.3, -0.1), (24.5, 2.9, 2.5, -0.3), (24.85, 1.2, 1.1, -0.5)]
        rings = [sring(y, hx * big, -zf * big, zf * big, n=n, p=2.6, cx=s * (6.3 + 0.2 + dx * -s * 0)) for (y, hx, zf, dx) in rr_]
        for k, ring_ in enumerate(rings):
            sh = s * (0.5 * (24.85 - rr_[k][0]) / 2.0)
            rings[k] = [p + V(sh, 0, 0) for p in ring_]
        g += dloft(ab, "plate", rings, caps=(False, "plate"), mats=["plate", "plate", "plate"])
        g += dloft(ab, "trim", [[p + V(s * 0.55, -0.3, 0) * 1 for p in sring(22.7, 3.75 * big, -3.45 * big, 3.45 * big, n=n, p=2.6, cx=s * 6.5)],
                                [p for p in sring(23.15, 3.7 * big, -3.4 * big, 3.4 * big, n=n, p=2.6, cx=s * 6.5)]], mats=["trim"])
    else:
        top = 22.7 - 1.3 * (i - 1)
        bot = top - 1.5
        cx0, cx1 = s * (6.5 + 0.5 * (i - 1)), s * (7.1 + 0.5 * (i - 1))
        rt, rb_ = (3.85 + 0.35 * (i - 1)) * big, (4.35 + 0.4 * (i - 1)) * big
        g += dloft(ab, "plate", [sring(top, rt, -rt * 0.92, rt * 0.92, n=n, p=2.6, cx=cx0),
                                 sring(bot, rb_, -rb_ * 0.9, rb_ * 0.9, n=n, p=2.6, cx=cx1)], mats=["plate"])
        g += dloft(ab, "bevel", [sring(bot, rb_, -rb_ * 0.9, rb_ * 0.9, n=n, p=2.6, cx=cx1),
                                 sring(bot - 0.35, rb_ * 0.96, -rb_ * 0.86, rb_ * 0.86, n=n, p=2.6, cx=cx1 + s * 0.15)], mats=["bevel"])
    return g


def chest(T, rar, v):
    V_ = VARS[v]
    N = V_["n"]
    B = "armorBody"
    f = []
    ry = [(12.3, 4.55, -2.65, 2.6), (15.5, 4.45, -2.6, 2.5), (19.0, 4.5, -2.7, 2.55), (22.4, 4.75, -2.85, 2.7), (24.3, 4.3, -2.6, 2.5)]
    f += dloft(B, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0) for y, hx, zf, zb in ry])
    hc = 25.6 if v == "S3" else 25.0
    f += dloft(B, "plate", [sring(24.0, 3.75, -3.0, 2.8, n=N, p=2.7), sring(24.7, 4.1, -3.3, 3.0, n=N, p=2.7),
                            sring(hc + 0.2, 3.7, -3.0, 2.8, n=N, p=2.7), sring(hc + 0.5, 3.4, -2.75, 2.6, n=N, p=2.7)],
               mats=["bevel", "plate", "trim"])
    pts = [(-3.1, 2.7), (3.1, 2.7), (3.4, -0.4), (1.5, -3.0), (0, -3.6), (-1.5, -3.0), (-3.4, -0.4)]
    f += bplate(B, "plate", "bevel", pts, c=(0, 20.4, -3.0), h=1.0, ins=0.95, ch=0.5, bow=0.9)
    f += lume(B, (0, 20.7, -3.62), (0.55, 4.6, 0.35))
    if rar != "common":
        for s in (-1, 1):
            f += lume(B, (s * 1.9, 20.9, -3.3), (0.5, 3.0, 0.35), rot=rot_z(-s * 12))
    f += gem(B, (0, 23.2, -3.4), (1.4, 0.9, 0.55))
    if v == "S3":
        f += bplate(B, "plate", "bevel", ngon(3.4, 1.8, 6), c=(0, 17.6, -3.05), h=0.9, ins=0.5, bow=0.4)
    # 帯と留め具
    f += dloft(B, "sash", [sring(12.5, 4.78, -2.95, 2.85, n=N, p=3.0), sring(14.7, 4.7, -2.9, 2.8, n=N, p=3.0)], mats=["sash"])
    f += dloft(B, "trim", [sring(14.7, 4.72, -2.92, 2.82, n=N, p=3.0), sring(15.3, 4.66, -2.88, 2.78, n=N, p=3.0)], mats=["trim"])
    f += bplate(B, "trim", "bevel", ngon(3.0, 3.0, 8), c=(0, 13.7, -3.1), h=0.9, ins=0.5)
    f += gem(B, (0, 13.7, -3.6), (1.0, 1.0, 0.45))
    # 腰の垂れ (左の脇): 体の外側へ薄く垂れる
    for k in range(V_["tails"]):
        zc = -1.4 + 1.5 * k
        L = 5.4 + 1.5 * k
        G = []
        for r in range(4):
            vv = r / 3
            row = []
            for c_ in range(3):
                u = c_ - 1
                row.append((4.95 + 0.9 * vv * vv + 0.1 * k, 13.4 - L * vv + (-1.0 * (1 - abs(u)) if r == 3 else 0.0), zc + u * 0.85 + 0.25 * vv))
            G.append(row)
        f += slab(B, ["sash", "sash", "sash"], G, (1, 0, 0), 0.45)
    # 外套 (背): 肩から広がって膝まで
    ln = V_["cape"]
    ybot = 24.2 - 20.0 * ln
    rows = 4 if ln < 0.6 else 5
    f += drape(B, ["cape"] * (rows - 2) + ["sash"], 0.0, 24.2, ybot, 3.0, 4.0, 7.6 if ln > 0.6 else 5.6, rows=rows, cols=7,
               hem=hemp(T, 7), fold=0.9, flare_z=4.6 * (ln + 0.1))
    f += band(B, "trim", (0, 24.0, 2.9), (8.6, 0.8, 1.2))
    if V_["cape"] >= 0.8 or rar != "common":     # 外套の背の飾り帯
        Gr = []
        for r in range(5):
            vv = r / 4
            zz = 3.0 + 0.35 + 4.6 * (ln + 0.1) * vv * vv
            Gr.append([(-0.7, 23.6 + (ybot + 1.2 - 23.6) * vv, zz), (0.0, 23.6 + (ybot + 1.2 - 23.6) * vv + (-0.8 if r == 4 else 0), zz), (0.7, 23.6 + (ybot + 1.2 - 23.6) * vv, zz)])
        f += slab(B, ["trim"] * 4, Gr, (0, 0, 1), 0.3)
    if rar == "legend":     # 背の環
        R1 = 6.4 if v == "S3" else 5.6
        f += ring_tube(B, "trim", (0, 28.6, 6.4), R1, 0.62, n=14, sides=4, span=280, start=-140)
        f += ring_tube(B, "trim", (0, 28.6, 6.2), R1 - 1.9, 0.45, n=12, sides=4, span=280, start=-140)
        f += blade(T, B, (0, 28.6 + R1 - 0.3, 6.4), (0, 1, 0.12), 4.2, 2.6, axis=(1, 0, 0), sweep=0.5)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        cx = s * 6.1
        sl = [(23.6, 2.7, -2.75, 2.75), (17.5, 2.45, -2.6, 2.6), (14.0, 2.45, -2.55, 2.55)]
        if V_["bell"] >= 1:    # 鈴口の袖
            sl += [(13.0, 2.7 + 0.25 * V_["bell"], -2.8, 2.8), (11.6, 3.0 + 0.35 * V_["bell"], -3.1 - 0.1 * V_["bell"], 3.1 + 0.1 * V_["bell"])]
        else:
            sl += [(12.2, 2.5, -2.6, 2.6)]
        g += dloft(ab, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in sl])
        g += dloft(ab, "trim", [sring(15.5, 2.62, -2.67, 2.67, n=N, p=3.0, cx=cx), sring(16.3, 2.58, -2.64, 2.64, n=N, p=3.0, cx=cx)])
        if V_["bell"] >= 1:
            yb = 12.2
            g += dloft(ab, "trim", [sring(yb - 0.05, 3.3, -3.35, 3.35, n=N, p=3.0, cx=cx), sring(yb + 0.8, 2.95, -3.0, 3.0, n=N, p=3.0, cx=cx)])
        # 外側の籠手
        R = np.array([[0, 0, -1.0 * s], [0, 1.0, 0], [1.0 * s, 0, 0]])
        g += bplate(ab, "plate", "bevel", ngon(2.4, 5.0, 8, taper=0.25), c=(s * 8.35, 17.8 if V_["bell"] else 14.6, 0), R=R, h=0.8, ins=0.6, ch=0.4)
        if rar != "common":
            g += lume(ab, (s * 8.85, 17.8 if V_["bell"] else 14.6, 0), (0.35, 2.8, 0.45))
        if V_["bell"] >= 2:
            g += dloft(ab, "sash", [sring(19.8, 2.7, -2.78, 2.78, n=N, p=3.0, cx=cx), sring(20.8, 2.78, -2.84, 2.84, n=N, p=3.0, cx=cx)])
        nt = V_["paul"] + (1 if rar == "legend" else 0)
        big = 1.1 if s == -1 else 0.93
        for i in range(nt):
            g += pauldron(T, ab, s, i, rar, big)
        if s == -1 or V_["paul"] >= 2:
            g += blade(T, ab, (s * 7.4, 24.3, 0.4), (s * 0.7, 0.9, -0.15), 6.2 if s == -1 else 4.2, 3.4 if s == -1 else 2.6,
                       axis=(0, 0, 1), sweep=s * -0.6)
        if V_["tassel"] and s == 1:
            for k in (0, 1):
                g += spk(ab, "sash", V(cx + 0.9 * k - 0.4, 14.0 - 0.4 * k, -2.9), (0.05, -1, -0.1), 3.8 - 0.8 * k, 0.5, sides=4, rings=2)
        out[ab] = g
    return out


# ------------------------------------------------------------------ 脚

def legs(T, rar, v):
    V_ = VARS[v]
    N = V_["n"]
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        g = []
        cx = s * 2.0
        rl = [(12.0, 2.5, -2.55, 2.5), (7.6, 2.38, -2.5, 2.45), (4.4, 2.3, -2.45, 2.4)]
        g += dloft(lb, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in rl])
        pts = [(-1.9, 1.7), (1.9, 1.7), (2.0, -0.2), (0.0, -3.1), (-2.0, -0.2)]
        g += bplate(lb, "plate", "bevel", pts, c=(cx, 9.0, -2.95), h=0.9, ins=0.8, ch=0.45, bow=0.5)
        if rar != "common":
            g += lume(lb, (cx, 9.2, -3.5), (0.5, 3.2, 0.35))
        if V_["plates"] >= 2:
            g += bplate(lb, "plate", "bevel", ngon(3.4, 1.4, 6), c=(cx, 11.0, -2.9), h=0.8, ins=0.4, bow=0.3)
        g += ellipsoid(lb, "plate", (cx, 5.9, -2.45), (1.9, 1.5, 1.0), segs=8, rings=2, phase=22.5)
        for fc in g[-12:]:
            fc.bone = lb
        if s == -1:
            g += gem(lb, (cx, 5.9, -3.4), (1.0, 0.8, 0.5))
        g += dloft(lb, "trim", [sring(11.7, 2.62, -2.72, 2.65, n=N, p=3.0, cx=cx), sring(12.0, 2.62, -2.72, 2.65, n=N, p=3.0, cx=cx)])
        if V_["tabard"] >= 1:
            ln = 6.6 + (1.2 if V_["tabard"] >= 2 else 0)
            G = []
            for r in range(5):
                vv = r / 4
                row = []
                for c_ in range(4):
                    u = c_ / 3 * 2 - 1
                    yy = 11.4 - ln * vv + (hemp(T, 4)[c_] * 0.6 if r == 4 else 0.0)
                    row.append((cx + u * (1.75 + 0.1 * vv), yy, -3.15 - 0.3 * vv * vv - 0.12 * math.sin(vv * 3)))
                G.append(row)
            g += slab(lb, ["cloth", "cloth", "cloth", "sash"], G, (0, 0, -1), 0.5)
        out[lb] = g
    return out


def boots(T, rar, v):
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        g = []
        cx = s * 2.0
        rl = [(4.8, 2.35, -2.5, 2.5), (3.4, 2.4, -2.9, 2.55), (1.8, 2.45, -3.7, 2.55), (0.3, 2.3, -4.4, 2.4)]
        g += dloft(bb, "cloth", [sring(y, hx, zf, zb, n=8, p=3.0, cx=cx) for y, hx, zf, zb in rl], caps=(False, "plate"))
        g += dloft(bb, "trim", [sring(4.7, 2.5, -2.6, 2.6, n=8, p=3.0, cx=cx), sring(5.0, 2.46, -2.56, 2.56, n=8, p=3.0, cx=cx)])
        g += bplate(bb, "plate", "bevel", ngon(3.0, 2.8, 8, taper=0.2, bottom=0.8), c=(cx, 2.9, -2.85), R=rot_x(-12), h=0.8, ins=0.55, ch=0.4, bow=0.4)
        if s == -1:
            g += gem(bb, (cx, 3.5, -3.4), (0.9, 0.8, 0.45))
        g += horn(T, bb, (s * 4.3, 3.2, 0.2), (s * 1.0, 0.45, 0.25), 2.8 if T != "thunder" else 3.6, 1.1,
                  bend=(s * 0.3, 0.2, 0.5), n=(0, 1, 0))
        out[bb] = g
    return out


def build(T, rar="common", v="S2"):
    pieces = {"helmet": helmet(T, rar, v), "chestplate": chest(T, rar, v), "leggings": legs(T, rar, v), "boots": boots(T, rar, v)}
    return {k: [f for bone_faces in vv.values() for f in bone_faces] for k, vv in pieces.items()}
