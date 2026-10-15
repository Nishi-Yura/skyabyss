"""鯨骨の番兵長 v2 (第2回ラフ): option B の形を、0.5単位・2画素/単位で彫り直した 3案.

実行 (リポジトリ直下): python tools/rough/captain_v2.py
出力:
  docs/roughs/v2/whalebone_captain/<a|b|c>/geo.json, texture.png, glowmask.png
  docs/roughs/v2/whalebone_captain.png   (列: 前回の採用案 B / A / B / C。行: 3/4 前 / 3/4 後ろ / 動作)
  docs/roughs/v2/whalebone_captain_report.json (箱の数・面の数・重なり数)

作り方: 0.5単位の格子 (96 x 96 x 48) に形 (楕円体, 角の丸い箱, 先細りの柱) を描いて占める。
格子の1マスは1つの部品にしか属さないので、部品どうしは重ならず面で接する。
同じ部品・同じ材質の連なりを箱にまとめ、見えている面ごとに 2画素/単位 で塗る。
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from voxel import load_geo, render, sheet  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "roughs" / "v2"
PREV = ROOT / "docs" / "roughs" / "whalebone_captain" / "b"
SUBJECT = "whalebone_captain"

C = 0.5                       # 格子の1マス (単位)
X0, Y0, Z0 = -24.0, 0.0, -12.0
NX, NY, NZ = 96, 96, 48
TW = 1024                     # テクスチャの幅 (画素)
PXU = 2                       # 1単位 = 2画素
SEED = 7

xs = X0 + (np.arange(NX) + 0.5) * C
ys = Y0 + (np.arange(NY) + 0.5) * C
zs = Z0 + (np.arange(NZ) + 0.5) * C
X, Y, Z = np.meshgrid(xs, ys, zs, indexing="ij")
P3 = np.stack([X, Y, Z])

PAL_B = {
    "bone": "E2D6BA", "bone_hl": "F6EFDB", "bone_dk": "9C8F70",
    "iron": "6E7580", "iron_dk": "434956", "stone": "7A8186",
    "moss": "4F7A3E", "moss_hl": "6F9C52", "strap": "3A2E28",
    "dark": "1C2029", "glow": "8FFFEA", "eye": "C4FFFA", "gem": "4FE0F5",
}
PAL_C = dict(PAL_B, bone="D9D6C4", bone_hl="ECE9DA", bone_dk="8A866F", iron="5F6873",
             iron_dk="3B414C", stone="5E666E", moss="3F6E4A", moss_hl="62905A", glow="70F8FF")
GLOW = {"glow", "eye", "gem"}

# 3案。A: 忠実な高解像度化 / B: 装飾・彫刻を足す / C: 異形 (左右非対称の骨の成長)
OPTS = {
    "a": dict(pal=PAL_B, edge=0.70, eng=False, trophy=False, chain=False, spikes=2, asym=False),
    "b": dict(pal=PAL_B, edge=0.62, eng=True, trophy=True, chain=True, spikes=3, asym=False),
    "c": dict(pal=PAL_C, edge=0.66, eng=False, trophy=False, chain=False, spikes=2, asym=True),
}

BONES = [
    ("body", (0, 17, 0), None),
    ("head", (0, 33, 0), "body"),
    ("legR", (-4.5, 12, 0), None), ("legL", (4.5, 12, 0), None),
    ("armR", (-14.5, 24.5, 0), "body"), ("forearmR", (-14.5, 17.5, 0), "armR"),
    ("handR", (-14.5, 10.5, 0), "forearmR"),
    ("fR0a", (-15.7, 8, -2.5), "handR"), ("fR0b", (-15.7, 8, -4.5), "fR0a"),
    ("fR1a", (-14.5, 8, -2.5), "handR"), ("fR1b", (-14.5, 8, -4.5), "fR1a"),
    ("fR2a", (-13.3, 8, -2.5), "handR"), ("fR2b", (-13.3, 8, -4.5), "fR2a"),
    ("pike", (-14.5, 8.5, -4.5), "handR"),   # 柄の握りを回転の中心にする
    ("armL", (14.5, 24.5, 0), "body"), ("forearmL", (14.5, 17.5, 0), "armL"),
    ("handL", (14.5, 10.5, 0), "forearmL"),
] + [(f"spine{k}", (0, 18 + 2 * k, 7), "body") for k in range(8)]


# ---------------------------------------------------------------- 形 (格子のマスが中に入るか)

def rbox(lo, hi, r=0.0):
    """角を r で丸めた箱。lo と hi は対角の二点 (単位)。"""
    lo, hi = np.minimum(lo, hi).astype(float), np.maximum(lo, hi).astype(float)
    c = (lo + hi) / 2
    h = ((hi - lo) / 2 - r).reshape(3, 1, 1, 1)
    q = np.abs(P3 - c.reshape(3, 1, 1, 1)) - h
    d = np.linalg.norm(np.maximum(q, 0), axis=0) + np.minimum(q.max(axis=0), 0) - r
    return d <= 1e-9


def ell(c, r):
    c, r = np.asarray(c, float), np.asarray(r, float)
    return (((X - c[0]) / r[0]) ** 2 + ((Y - c[1]) / r[1]) ** 2 + ((Z - c[2]) / r[2]) ** 2) <= 1.0


def tcap(a, b, ra, rb):
    """先細りの柱 (a から b へ、半径 ra から rb)。棘・角・背骨の突起に使う。"""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = b - a
    pp = P3 - a.reshape(3, 1, 1, 1)
    t = np.clip(np.tensordot(d, pp, axes=1) / (d @ d), 0, 1)
    proj = pp - t[None] * d.reshape(3, 1, 1, 1)
    return np.linalg.norm(proj, axis=0) <= ra + (rb - ra) * t


def sx_range(sgn, a, b):
    return (min(sgn * a, sgn * b), max(sgn * a, sgn * b))


# ---------------------------------------------------------------- 格子の持ち主

class Model:
    def __init__(self):
        self.owner = np.full((NX, NY, NZ), -1, np.int16)
        self.mat = np.zeros((NX, NY, NZ), np.int16)
        self.bnames = [b[0] for b in BONES]
        self.mats = []

    def mid(self, m):
        if m not in self.mats:
            self.mats.append(m)
        return self.mats.index(m)

    def bid(self, name):
        return self.bnames.index(name)

    def add(self, mask, bone, mat, over=False):
        """空いているマスに部品を置く (over=True なら上書き)。先に置いた部品が優先される。"""
        m = mask & ((self.owner < 0) | over)
        self.owner[m] = self.bid(bone)
        self.mat[m] = self.mid(mat)

    def carve(self, mask, bone):
        self.owner[mask & (self.owner == self.bid(bone))] = -1

    def retex(self, mask, mat, bone=None):
        m = mask & (self.owner >= 0)
        if bone:
            m &= self.owner == self.bid(bone)
        self.mat[m] = self.mid(mat)


# ---------------------------------------------------------------- 部品

def torso_profile():
    # (y, 横の半径, 前後の半径): 腰は細く、胸と肩は広い
    return np.array([[17, 9.0, 6.0], [20, 8.5, 5.5], [24, 10.0, 6.5], [30, 10.5, 7.0], [33, 9.5, 6.5]])


def build(opt):
    o = OPTS[opt]
    m = Model()
    prof = torso_profile()
    wv = np.interp(Y, prof[:, 0], prof[:, 1])
    dv = np.interp(Y, prof[:, 0], prof[:, 2])
    torso = ((np.abs(X) / wv) ** 2.5 + (np.abs(Z) / dv) ** 2.5 <= 1) & (Y >= 17) & (Y <= 33)
    noise = np.sin(X * 0.9 + 1.3) * np.cos(Y * 0.6) * np.sin(Z * 0.8 + 0.4)

    # --- 胴 (body)
    m.add(torso, "body", "stone:sp+gv")
    front = (Z < -dv + 1.0) & (np.abs(X) <= 7) & (Y >= 20) & (Y <= 31)
    m.retex(front, "bone:sp+rv", "body")                     # 胸の鯨骨の板
    m.retex(torso & (noise > (0.3 if o["asym"] else 0.55)) & (Y >= 23), "moss:sp", "body")
    # 竜骨の隆起 (中央は高い段) と、はめ込みの宝石
    if o["asym"]:
        m.add(rbox((0.5, 20, -8.5), (1.5, 31, -6.0)), "body", "bone:gv")
        m.add(rbox((0.5, 31, -8.0), (1.5, 32.5, -6.5)), "body", "bone_hl")
        m.add(rbox((-3.5, 22, -7.5), (-2.5, 29, -6.0)), "body", "bone")
        m.add(tcap((-10, 24, 0), (-13.5, 28, -1), 1.3, 0.3), "body", "bone:sp")  # 肋からの突き出し
    else:
        m.add(rbox((-0.5, 20, -8.5), (0.5, 31, -6.0)), "body", "bone:gv")
        m.add(rbox((-0.5, 31, -8.0), (0.5, 32.5, -6.5)), "body", "bone_hl")
        m.add(rbox((2.5, 22, -7.5), (3.5, 29, -6.0)), "body", "bone")
        m.add(rbox((-3.5, 22, -7.5), (-2.5, 29, -6.0)), "body", "bone")
    gx = 1.0 if o["asym"] else 0.0
    gem = (np.abs(X - gx) + np.abs(Y - 26) + np.abs(Z + 9.0)) <= 1.6
    m.add(gem, "body", "gem", over=True)
    if o["eng"]:
        m.add(rbox((-1.5, 24.5, -8.6), (-0.5, 27.5, -7.5)), "body", "bone_dk")
        m.add(rbox((0.5, 24.5, -8.6), (1.5, 27.5, -7.5)), "body", "bone_dk")
    # 前の裾 (faulds): 段のある 3 層と、腰の帯
    m.add(rbox((-10.5, 12, -6), (10.5, 13.5, 6), 0.5), "body", "iron:gv")
    m.add(rbox((-10, 13.5, -6.5), (10, 15.5, 6.5), 0.5), "body", "iron:rv")
    m.add(rbox((-10, 15.5, -7), (10, 17, 7), 0.5), "body", "strap")
    m.add(rbox((-2, 15.5, -7.6), (2, 17, -7), 0.2), "body", "bone:sp")  # 留め金
    if o["trophy"]:
        for sx in (-1, 1):
            cx = 5 * sx
            m.add(ell((cx, 14.0, -7.0), (1.5, 1.5, 1.5)), "body", "bone:sp")
            m.add(rbox((cx - 0.9, 13.4, -8.6), (cx - 0.1, 14.2, -8.0)), "body", "dark", over=True)
            m.add(rbox((cx + 0.1, 13.4, -8.6), (cx + 0.9, 14.2, -8.0)), "body", "dark", over=True)

    # --- 首
    m.add(rbox((-3, 33, -3), (3, 35, 3), 0.5), "body", "iron_dk")

    # --- 肩当て (3層。下は小さく、中は一回り広く、上は丸い段)
    for sgn in (-1, 1):
        # 段のある 4 層: 下ほど小さく、上へ行くほど縁が内側へ引く。縁の上面は明るい帯
        layers = [((9, 18.5), 24, 25.5, (6.0, 6.5), 0.5, "bone:sp+rv"),
                  ((9, 19.5), 25.5, 28.5, (6.5, 7.5), 0.5, "bone:sp+gv"),
                  ((9.5, 19.0), 28.5, 31.0, (6.0, 7.0), 0.8, "bone:sp+gv"),
                  ((10, 18.0), 31.0, 33.5, (5.0, 6.5), 1.5, "bone:rv+sp")]
        for (a0, a1), y0_, y1_, (zf, zb), rr, mat in layers:
            x0, x1 = sx_range(sgn, a0, a1)
            blk = rbox((x0, y0_, -zf), (x1, y1_, zb), rr)
            m.add(blk, "body", mat)
            m.retex(blk & (Y >= y1_ - 0.5), "bone_hl", "body")   # 縁の明かり (上面の一段)
        m.retex(rbox((sx_range(sgn, 10, 18)[0], 32.5, -5), (sx_range(sgn, 10, 18)[1], 33.5, 5), 1.0),
                "moss:sp", "body")
        if opt == "c" and sgn == 1:
            # 左肩の大きな骨の成長 (3本)
            m.add(tcap((sgn * 19, 30, 0), (sgn * 22.5, 39, -1), 1.7, 0.3), "body", "bone:eng+sp")
            m.add(tcap((sgn * 17, 33, 3), (sgn * 18, 41, 2), 1.2, 0.2), "body", "bone_hl")
            m.add(tcap((sgn * 19, 28, -4), (sgn * 23.5, 30.5, -5), 1.0, 0.2), "body", "bone")
        elif opt == "c":
            m.add(tcap((sgn * 19, 31, 2), (sgn * 20.5, 33.5, 2.5), 1.0, 0.3), "body", "bone")
        else:
            spikes = [((sgn * 19, 28, -3), (sgn * 21.5, 31.5, -3), 1.2, 0.2),
                      ((sgn * 18, 32, 0), (sgn * 21, 36, 0), 1.3, 0.2)]
            if o["spikes"] >= 3:
                spikes.append(((sgn * 19, 28, 3), (sgn * 21.5, 31.5, 3), 1.2, 0.2))
            for a, b, ra, rb in spikes:
                m.add(tcap(a, b, ra, rb), "body", "bone_hl" if o["eng"] else "bone")
        if o["chain"]:
            # 鎖: 肩の下から1列の輪 (隣どうしは面で接する)
            x = sgn * 17.5
            for i in range(4):
                yt = 24 - 2 * i
                if i % 2 == 0:
                    m.add(rbox((x - 0.75, yt - 2, -3.25), (x + 0.75, yt, -2.75)), "body", "iron:rv")
                else:
                    m.add(rbox((x - 0.25, yt - 2, -3.75), (x + 0.25, yt, -2.25)), "body", "iron:rv")

    # --- 背骨 (8 節。節ごとに別の骨。棘と横突起)
    rng = np.random.default_rng(SEED)
    for k in range(8):
        y0 = 18 + 2 * k
        name = f"spine{k}"
        m.add(rbox((-2, y0, 5.0), (2, y0 + 2, 8.5), 0.5), name, "bone:gv")
        m.add(rbox((-3.5, y0 + 0.5, 6.0), (3.5, y0 + 1.5, 8.0), 0.5), name, "bone:sp")
        if o["asym"]:
            h = float(rng.choice([3.0, 4.0, 5.0, 6.0]))
            side = 1 if k % 3 == 0 else -1
            m.add(tcap((0, y0 + 1, 8.0), (0, y0 + h, 12.5 + (k % 2)), 1.6, 0.4), name, "bone_hl")
            if k in (2, 5):
                m.add(tcap((side * 2, y0 + 1, 7), (side * 5, y0 + 2.5, 10.0), 1.1, 0.3), name, "bone")
        else:
            # 背の棘: 根元が太く、後ろへ大きく張り出す (背面図で読めるように)
            m.add(tcap((0, y0 + 0.5, 8.0), (0, y0 + 4.5, 12.5), 1.6, 0.4), name,
                  "bone_hl" if o["eng"] else "bone")

    # --- 兜 (中央を凹ませたスリット。目は奥で光る)
    m.add(rbox((-6, 35, -6), (6, 43, 6), 1.0), "head", "iron:gv")
    win = (np.abs(X) <= 3) & (Y >= 37) & (Y <= 40.5) & (Z <= -3.0) & (Z >= -6.5)
    m.carve(win, "head")
    back = (np.abs(X) <= 3) & (Y >= 37) & (Y <= 40.5) & (Z > -3.0) & (Z < -2.0)
    m.retex(back, "dark", "head")
    m.add(rbox((-2.5, 37.5, -4.5), (-0.5, 39.0, -3.0)), "head", "eye")
    m.add(rbox((0.5, 37.5, -4.5), (2.5, 39.0, -3.0)), "head", "eye")
    m.add(rbox((-6.5, 39, -7), (-3.5, 40.5, -6), 0.5), "head", "iron_dk:gv")
    m.add(rbox((3.5, 39, -7), (6.5, 40.5, -6), 0.5), "head", "iron_dk:gv")
    m.add(rbox((-3.5, 40.5, -6.5), (3.5, 41.5, -5.5)), "head", "iron_dk")
    for s in (-1, 1):
        if o["asym"] and s == -1:
            # 右の角は途中で折れた短いもの
            m.add(tcap((s * 5.5, 40, -0.5), (s * 7.5, 41.5, -0.5), 2.0, 1.2), "head", "bone:sp")
            m.add(tcap((s * 7.5, 41.5, -0.5), (s * 8.5, 43.0, -1.0), 1.2, 0.5), "head", "bone")
        else:
            end = (s * 9.5, 44.5, -1.5) if o["asym"] else (s * 9.5, 44.0, -1.0)
            m.add(tcap((s * 5.5, 40, -0.5), (s * 8, 41.5, -0.5), 2.0, 1.4), "head", "bone:sp")
            m.add(tcap((s * 8, 41.5, -0.5), end, 1.4, 0.7), "head", "bone")

    # --- 脚 (greave と膝の当て板、足の甲)
    for sgn, leg in ((-1, "legR"), (1, "legL")):
        xc = sgn * 4.5
        # 太い脛 (幅 7.5)。膝の当て板は前へ張り出す
        m.add(rbox((xc - 3.75, 3.0, -4.5), (xc + 3.75, 12, 3.5), 1.0), leg, "iron:rv")
        m.add(ell((xc, 9.5, -5.0), (3.25, 2.5, 1.5)), leg, "bone:sp+gv")
        m.add(rbox((xc - 0.5, 3.5, -5.0), (xc + 0.5, 8, -4.5)), leg, "iron_dk")
        m.add(rbox((xc - 3.75, 0, -6.5), (xc + 3.75, 3.0, 3.5), 0.5), leg, "iron_dk:gv")

    # --- 腕・手甲 (右は鉾を持つ)
    for sgn, arm, fore, hand in ((-1, "armR", "forearmR", "handR"), (1, "armL", "forearmL", "handL")):
        xc = sgn * 14.5
        m.add(tcap((xc, 24.5, 0), (xc, 17.5, 0), 2.2, 1.9), arm, "iron:rv")
        m.add(rbox((xc - 2.4, 18.0, -2.4), (xc + 2.4, 19.0, 2.4), 0.5), arm, "bone:gv")
        m.add(rbox((xc - 2.2, 15.5, -2.6), (xc + 2.2, 17.0, 2.6), 0.5), fore, "strap")
        m.add(rbox((xc - 1.9, 10.5, -2.2), (xc + 1.9, 17.5, 2.2), 0.5), fore, "iron:gv")
        m.add(rbox((xc - 2.0, 7.0, -2.5), (xc + 2.0, 10.5, 2.0), 0.5), hand, "iron:rv")
    # 指 (右手の3本。2節ずつ、柄の前で曲がる)
    for i, dx in enumerate((-1.2, 0.0, 1.2)):
        xk = -14.5 + dx
        m.add(rbox((xk - 0.5, 7.0, -4.5), (xk + 0.5, 9.5, -2.5), 0.25), f"fR{i}a", "iron_dk:gv")
        m.add(rbox((xk - 0.5, 7.5, -6.0), (xk + 0.5, 9.0, -4.5), 0.25), f"fR{i}b", "iron_dk")

    # --- 鉾 (柄。先端は三日月の鉤と葉の刃)
    xc = -14.5
    m.add(rbox((xc - 0.5, 0, -5.5), (xc + 0.5, 42, -4.5)), "pike", "bone_dk:gv")
    m.add(rbox((xc - 1, 12, -6), (xc + 1, 13, -4)), "pike", "iron:rv")
    m.add(rbox((xc - 1, 30, -6), (xc + 1, 31, -4)), "pike", "iron:rv")
    leaf_y = (Y >= 42) & (Y <= 44.5) & (Z >= -5.5) & (Z <= -4.5)
    width = 0.5 + 1.5 * (1 - (Y - 42) / 2.5)
    m.add(leaf_y & (np.abs(X - xc) <= width), "pike", "bone_hl:eng" if o["eng"] else "bone_hl")
    # 三日月の鉤 (アイコンのように大きく、上の左へ張り出す)
    ring_r = np.hypot(X - (xc - 2.6), Y - 41.5)
    ang = np.degrees(np.arctan2(Y - 41.5, X - (xc - 2.6)))
    crescent = (np.abs(ring_r - 3.8) <= 0.65) & (ang >= -20) & (ang <= 170) & (Z >= -5.5) & (Z <= -4.5)
    m.add(crescent, "pike", "bone:sp+eng")
    m.add(tcap((xc, 43.5, -5), (xc, 44.5, -5), 0.5, 0.1), "pike", "gem")
    if o["asym"]:
        m.add(tcap((-12, 33, -5), (-12.5, 35.5, -6), 0.8, 0.2), "body", "gem")
    return m


# ---------------------------------------------------------------- 箱にまとめる

def merge(m):
    key = np.where(m.owner >= 0, m.owner.astype(np.int32) * 256 + m.mat.astype(np.int32), -1)
    done = np.zeros(key.shape, bool)
    out = []
    for i, j, k in np.argwhere(key >= 0):
        if done[i, j, k]:
            continue
        v = key[i, j, k]
        k1 = k
        while k1 + 1 < NZ and key[i, j, k1 + 1] == v and not done[i, j, k1 + 1]:
            k1 += 1
        j1 = j
        while j1 + 1 < NY and np.all(key[i, j1 + 1, k:k1 + 1] == v) and not done[i, j1 + 1, k:k1 + 1].any():
            j1 += 1
        i1 = i
        while i1 + 1 < NX and np.all(key[i1 + 1, j:j1 + 1, k:k1 + 1] == v) and not done[i1 + 1, j:j1 + 1, k:k1 + 1].any():
            i1 += 1
        done[i:i1 + 1, j:j1 + 1, k:k1 + 1] = True
        out.append((i, j, k, i1, j1, k1, int(v)))
    return out


# 面: (一隅, 横の辺, 縦の辺, 法線の軸, 側)。voxel.load_geo の面の向きと同じ
FACES = {
    "north": ((1, 0, 0), (-1, 0, 0), (0, 1, 0), 2, -1),
    "south": ((0, 0, 1), (1, 0, 0), (0, 1, 0), 2, 1),
    "east": ((1, 0, 1), (0, 0, -1), (0, 1, 0), 0, 1),
    "west": ((0, 0, 0), (0, 0, 1), (0, 1, 0), 0, -1),
    "up": ((0, 1, 1), (1, 0, 0), (0, 0, -1), 1, 1),
    "down": ((0, 0, 0), (1, 0, 0), (0, 0, 1), 1, -1),
}


def face_geom(lo, hi, name):
    corner, eu, ev, _, _ = FACES[name]
    org = np.where(np.array(corner) == 1, hi, lo).astype(float)
    s = hi - lo
    return org, np.array(eu, float) * s, np.array(ev, float) * s


def paint(mat, org, eu, ev, nax, W, H, pal, edge_k):
    """面の texel (H, W, 3) を塗る。表示の向き: 上の行が ev の端 (t=1)、左の列が eu の始点 (s=0)。"""
    s = (np.arange(W) + 0.5) / W
    t = 1 - (np.arange(H) + 0.5) / H
    pp = org + s[None, :, None] * eu + t[:, None, None] * ev
    ta, tb = [i for i in range(3) if i != nax]
    a, b = pp[..., ta], pp[..., tb]
    base, _, fs = mat.partition(":")
    fl = set(fs.split("+")) if fs else set()
    rgb = np.broadcast_to(np.array([int(pal[base][i:i + 2], 16) for i in (0, 2, 4)], float), (H, W, 3)).copy()
    glow = np.zeros((H, W), bool)
    if base in GLOW:
        glow[:] = True
        return rgb, glow
    if "sp" in fl:
        ia = np.floor(a * 2).astype(np.int64)
        ib = np.floor(b * 2).astype(np.int64)
        hsh = ((ia * 73856093) ^ (ib * 19349663)) % 7
        rgb[hsh == 0] *= 0.82
        rgb[hsh == 3] *= 1.07
    if "gv" in fl:
        rgb[np.mod(b, 2.0) < 0.5] *= 0.6
    if "eng" in fl:
        rgb[np.mod(a + b, 1.5) < 0.5] *= 0.68
    if "rv" in fl:
        da = np.mod(a - 1.25, 2.0)
        db = np.mod(b - 1.25, 2.0)
        rv = (np.minimum(da, 2 - da) < 0.1) & (np.minimum(db, 2 - db) < 0.1)
        rgb[rv] = np.minimum(rgb[rv] * 1.35 + 20, 255)
    rgb *= (0.9 + 0.2 * t)[:, None, None]          # 上が明るい
    if W >= 4 and H >= 4:
        ej = np.zeros((H, W), bool)
        ej[0, :] = ej[-1, :] = ej[:, 0] = ej[:, -1] = True
        rgb[ej] *= edge_k                           # 面の縁の段
    return np.clip(rgb, 0, 255), glow


def export(m, opt, ident):
    o = OPTS[opt]
    pal = o["pal"]
    boxes = merge(m)
    own = np.pad(m.owner, 1, constant_values=-1)

    faces = []
    for bi, (i0, j0, k0, i1, j1, k1, v) in enumerate(boxes):
        lo = np.array([X0 + i0 * C, Y0 + j0 * C, Z0 + k0 * C])
        hi = np.array([X0 + (i1 + 1) * C, Y0 + (j1 + 1) * C, Z0 + (k1 + 1) * C])
        lo_i, hi_i = [i0, j0, k0], [i1, j1, k1]
        for name, (_, _, _, ax, side) in FACES.items():
            sl = [slice(lo_i[d] + 1, hi_i[d] + 2) for d in range(3)]
            sl[ax] = hi_i[ax] + 2 if side > 0 else lo_i[ax]
            if np.all(own[tuple(sl)] >= 0):
                continue  # 隣の箱で隠れている面は作らない
            org, eu, ev = face_geom(lo, hi, name)
            W = int(round(np.linalg.norm(eu) * PXU))
            H = int(round(np.linalg.norm(ev) * PXU))
            faces.append(dict(bi=bi, name=name, org=org, eu=eu, ev=ev, nax=ax, W=W, H=H,
                              mat=m.mats[v % 256]))

    # 棚詰め (高い順)
    x = y = rowh = 0
    for f in sorted(faces, key=lambda f: -f["H"]):
        if x + f["W"] > TW:
            x, y, rowh = 0, y + rowh, 0
        f["u"], f["v"] = x, y
        x += f["W"] + 1
        rowh = max(rowh, f["H"] + 1)
    TH = ((y + rowh + 15) // 16) * 16
    tex = np.zeros((TH, TW, 4), np.uint8)
    gl = np.zeros((TH, TW, 4), np.uint8)
    for f in faces:
        rgb, glow = paint(f["mat"], f["org"], f["eu"], f["ev"], f["nax"], f["W"], f["H"], pal, o["edge"])
        if f["name"] == "down":  # voxel.load_geo と同じく、下の面は行を反転して置く
            rgb, glow = rgb[::-1], glow[::-1]
        u, v, W, H = f["u"], f["v"], f["W"], f["H"]
        tex[v:v + H, u:u + W, :3] = rgb.astype(np.uint8)
        tex[v:v + H, u:u + W, 3] = 255
        if glow.any():
            gl[v:v + H, u:u + W, :3] = rgb.astype(np.uint8)
            gl[v:v + H, u:u + W, 3] = np.where(glow, 255, 0)

    fmap = {}
    for f in faces:
        fmap.setdefault(f["bi"], {})[f["name"]] = {"uv": [f["u"], f["v"]], "uv_size": [f["W"], f["H"]]}
    per_bone = {n: [] for n in m.bnames}
    for bi, (i0, j0, k0, i1, j1, k1, v) in enumerate(boxes):
        if bi not in fmap:
            continue
        per_bone[m.bnames[v // 256]].append({
            "origin": [X0 + i0 * C, Y0 + j0 * C, Z0 + k0 * C],
            "size": [(i1 - i0 + 1) * C, (j1 - j0 + 1) * C, (k1 - k0 + 1) * C],
            "uv": fmap[bi],
        })
    bones_out = []
    for name, piv, par in BONES:
        b = {"name": name, "pivot": list(piv)}
        if par:
            b["parent"] = par
        b["cubes"] = per_bone[name]
        bones_out.append(b)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{ident}",
                "texture_width": TW,
                "texture_height": TH,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.2,
                "visible_bounds_offset": [0, 1.6, 0],
            },
            "bones": bones_out,
        }],
    }
    return geo, tex, gl, len(boxes), len(faces)


def overlaps(m):
    """箱どうしの重なり (体積のある交差) の組を数える。voxel.check_overlaps と同じ規則。"""
    boxes = merge(m)
    lo = np.array([[X0 + i * C, Y0 + j * C, Z0 + k * C] for i, j, k, _, _, _, _ in boxes])
    hi = np.array([[X0 + (i1 + 1) * C, Y0 + (j1 + 1) * C, Z0 + (k1 + 1) * C]
                   for _, _, _, i1, j1, k1, _ in boxes])
    bad = []
    for i in range(len(boxes)):
        ov = np.minimum(hi[i], hi[i + 1:]) - np.maximum(lo[i], lo[i + 1:])
        bad += [(i, i + 1 + int(h)) for h in np.where(np.all(ov > 1e-6, axis=1))[0]]
    return bad


def render_set(geo, tex, gl, action_pose):
    ims = []
    for yaw, pose, size, mg in ((145, None, (360, 480), 0.08), (35, None, (360, 480), 0.08),
                                (145, action_pose, (520, 680), 0.02)):
        faces = load_geo(geo, tex, gl, pose=pose)
        ims.append(render(faces, yaw=yaw, pitch=14, size=size, margin=mg))
    return ims


POSE = {"armR": (95, 0, 0), "forearmR": (-20, 0, 0)}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    report = {}
    cols = []
    prev_geo = json.loads((PREV / "geo.json").read_text(encoding="utf-8"))
    prev_tex = np.array(Image.open(PREV / "texture.png").convert("RGBA"))
    prev_gl = np.array(Image.open(PREV / "glowmask.png").convert("RGBA"))
    cols.append(("前回の採用案 B (第1回)", render_set(prev_geo, prev_tex, prev_gl, {"armR": (120, 0, 0)})))
    names = {"a": "A 忠実・高解像度", "b": "B 装飾・彫刻", "c": "C 異形・非対称"}
    for opt in ("a", "b", "c"):
        m = build(opt)
        geo, tex, gl, nbox, nface = export(m, opt, SUBJECT)
        ov = overlaps(m)
        d = OUT / SUBJECT / opt
        d.mkdir(parents=True, exist_ok=True)
        (d / "geo.json").write_text(json.dumps(geo, ensure_ascii=False), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / "texture.png")
        Image.fromarray(gl, "RGBA").save(d / "glowmask.png")
        cols.append((names[opt], render_set(geo, tex, gl, POSE)))
        report[opt] = dict(boxes=nbox, faces=nface, overlaps=len(ov), texture=[TW, int(tex.shape[0])])
        print(opt, report[opt], flush=True)
    sheet_img = sheet("鯨骨の番兵長 第2回ラフ (0.5単位・2画素/単位)", cols,
                      "行: 3/4 前 / 3/4 後ろ / 動作 (鉾を掲げる)")
    sheet_img.save(OUT / f"{SUBJECT}.png")
    (OUT / f"{SUBJECT}_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
