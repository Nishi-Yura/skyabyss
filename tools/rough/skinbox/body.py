"""全スキン共通の素体と髪の組み方 (docs/design/55)。

素体: 背はバニラと同じ 32px、頭 8、細身で約 4 頭身。付け根はバニラの体と同じ (腕 ±5,22 / 脚 ±1.9,12)。
  顔を貼るスキン (own=True) は、頭・首・手を着ている人のマイクラのスキンの絵で描く (own_ の骨)。
髪: 下地 (頭の上と後ろ +0.5)・前髪 3 層・横髪 2 層・後ろ髪 3 層・結んだ髪 (3 節の鎖)・アホ毛。房の先は本当の段で細らせる。
  髪の絵は、箱ごとではなく頭の高さと横の位置で塗る (重ねた箱の筋と陰がつながる)。
"""
from __future__ import annotations

import math

import numpy as np

from kit import T, Model
from paint import C, mix, blank, out, shade_rows, cloth

# ---------------------------------------------------------------- 着ている人のスキンの絵の場所 (64x64、面ごと)
HEAD_UV = {"w": (0, 8, 8, 16), "n": (8, 8, 16, 16), "e": (16, 8, 24, 16), "s": (24, 8, 32, 16), "u": (8, 0, 16, 8), "d": (16, 0, 24, 8)}
NECK_UV = {f: (19, 3, 21, 5) for f in "nsewud"}
HAND_R_UV = {f: (44, 30, 47, 32) for f in "nsewud"}
HAND_L_UV = {f: (36, 62, 39, 64) for f in "nsewud"}

# ---------------------------------------------------------------- 素体の寸法 (geo、px)
ARM_X = 5.5  # 腕の真ん中 (バニラの細い腕と同じ)
LEG_X = 1.9


def base(m: Model, own=True, under="#2a2530", skin="#f6dccb", face=None, hands=True, neck=True, torso=True, arms=True, legs=True):
    """素体。服の下地は under の色 (たいてい服で隠れる)。own=False なら face (頭の前の面の描き方) と skin の色で描く。"""
    u = cloth(under, edges=False)
    sk = cloth(skin, edges=False)
    if own:
        m.bone("own_head", "head", (0, 24, 0))
        m.box("own_head", (-4, 24, -4), (8, 8, 8), None, skinmap=HEAD_UV)
        if neck:
            m.bone("own_neck", "body", (0, 24, 0))
            m.box("own_neck", (-1, 23, -1), (2, 1.5, 2), None, skinmap=NECK_UV)
        if hands:
            m.bone("own_hand_R", "armR", (-5, 22, 0))
            m.box("own_hand_R", (-ARM_X - 1.0, 10.5, -0.75), (2.0, 2.6, 1.5), None, skinmap=HAND_R_UV)
            m.box("own_hand_R", (-ARM_X + 0.75, 11.3, -0.75), (0.5, 1.2, 0.6), None, skinmap=HAND_R_UV)
            m.bone("own_hand_L", "armL", (5, 22, 0))
            m.box("own_hand_L", (ARM_X - 1.0, 10.5, -0.75), (2.0, 2.6, 1.5), None, skinmap=HAND_L_UV)
            m.box("own_hand_L", (ARM_X - 1.25, 11.3, -0.75), (0.5, 1.2, 0.6), None, skinmap=HAND_L_UV)
    else:
        m.box("head", (-4, 24, -4), (8, 8, 8), face or sk)
        if neck:
            m.box("body", (-1, 23, -1), (2, 1.5, 2), sk)
        if hands:
            m.pair("armR", (-ARM_X - 1.0, 10.5, -0.75), (2.0, 2.6, 1.5), sk)
            m.pair("armR", (-ARM_X + 0.75, 11.3, -0.75), (0.5, 1.2, 0.6), sk)
    if torso:
        m.box("body", (-3.25, 18.5, -1.75), (6.5, 5.0, 3.5), u)
        m.box("body", (-2.75, 15.5, -1.5), (5.5, 3.0, 3.0), u)
        m.box("body", (-3.25, 12.0, -1.75), (6.5, 3.5, 3.5), u)
    if arms:
        m.pair("armR", (-ARM_X - 1.25, 17.5, -1.25), (2.5, 5.5, 2.5), u)
        m.pair("armR", (-ARM_X - 1.125, 13.0, -1.125), (2.25, 4.5, 2.25), u)
    if legs:
        m.pair("legR", (-LEG_X - 1.5, 6.0, -1.6), (3.0, 6.0, 3.2), u)
        m.pair("legR", (-LEG_X - 1.375, 1.5, -1.375), (2.75, 4.5, 2.75), u)
        m.pair("legR", (-LEG_X - 1.375, 0.0, -2.4), (2.75, 1.5, 4.0), u)


# ---------------------------------------------------------------- 髪の絵 (頭の高さと横の位置で塗る)
class HairPaint:
    """色: base / dark / light / hi (天使の輪)。y_hi..y_lo で上から下へ暗くなる。ring_y に光の帯。"""

    def __init__(self, base, dark, light, hi, line=None, y_hi=34.0, y_lo=8.0, ring_y=31.3, seed=1):
        self.b, self.d, self.l, self.h = C(base), C(dark), C(light), C(hi)
        self.line = C(line) if line else self.d * 0.7
        self.y_hi, self.y_lo, self.ring_y = y_hi, y_lo, ring_y
        rng = np.random.default_rng(seed)
        # 横の位置 (1/8 px) ごとの筋の明暗: -1..1
        n = 64 * T
        tone = np.zeros(n)
        x = 0
        while x < n:
            w = int(rng.integers(2, 6))
            tone[x:x + w] = rng.choice([-0.8, -0.4, 0.0, 0.0, 0.0, 0.5, 0.9])
            x += w
        self.tone = tone

    def col_at(self, y):
        t = np.clip((self.y_hi - np.asarray(y, float)) / (self.y_hi - self.y_lo), 0, 1)
        lo = mix(self.l, self.b, np.clip(t / 0.2, 0, 1)[..., None])
        hi = mix(self.b, self.d, (np.clip((t - 0.2) / 0.8, 0, 1) ** 1.5)[..., None] * 0.8)
        c = np.where(t[..., None] < 0.2, lo, hi)
        return c

    def __call__(self, face, W, H, box):
        x0, y0, z0 = box.frm
        w, h, d = box.size
        img = blank(W, H, self.b)
        if face in "nsew":
            # 面の左上の geo の位置と、横の向き
            if face == "n":
                ax, a0, sgn = 0, x0, 1
            elif face == "s":
                ax, a0, sgn = 0, x0 + w, -1
            elif face == "e":
                ax, a0, sgn = 2, z0, 1
            else:
                ax, a0, sgn = 2, z0 + d, -1
            ys = y0 + h - (np.arange(H) + 0.5) / T
            col = self.col_at(ys)  # (H, 3)
            img[..., :3] = col[:, None, :]
            idx = ((a0 + sgn * (np.arange(W) + 0.5) / T + 32) * T).astype(int) % len(self.tone)
            tn = self.tone[idx]
            img[..., :3] = np.where(tn[None, :, None] > 0, mix(img[..., :3], self.l, 0.28 * tn[None, :, None]),
                                    mix(img[..., :3], self.d, 0.32 * -np.minimum(tn, 0)[None, :, None]))
            # 筋の境の細い線
            edge_cols = np.r_[False, tn[1:] != tn[:-1]]
            img[:, edge_cols, :3] = mix(img[:, edge_cols, :3], self.line, 0.35)
            # 天使の輪: ring_y のあたりに明るいぎざぎざの帯
            if y0 <= self.ring_y + 0.6 and y0 + h >= self.ring_y - 0.6:
                xs = np.arange(W)
                band_y = self.ring_y + np.sin(xs * 0.9 + a0 * 3) * 0.12 + np.where(tn > 0, 0.08, 0)
                for i in range(W):
                    if tn[i] < 0:
                        continue
                    rows = np.where(np.abs(ys - band_y[i]) < 0.13)[0]
                    img[rows, i, :3] = mix(img[rows, i, :3], self.h, 0.75)
                    rows2 = np.where(np.abs(ys - (band_y[i] + 0.25)) < 0.07)[0]
                    img[rows2, i, :3] = mix(img[rows2, i, :3], self.l, 0.5)
            # 左右の縁を締める
            if W >= 4:
                img[:, 0, :3] = mix(img[:, 0, :3], self.line, 0.5)
                img[:, -1, :3] = mix(img[:, -1, :3], self.line, 0.5)
            if H >= 4:
                img[-1, :, :3] = mix(img[-1, :, :3], self.line, 0.45)
        elif face == "u":
            img[..., :3] = mix(self.col_at(np.array([y0 + h]))[0], self.l, 0.3)
            for x in range(0, W, 3):
                img[:, x, :3] = mix(img[:, x, :3], self.d, 0.2)
        else:
            img[..., :3] = self.d * 0.85
        return out(img)


# ---------------------------------------------------------------- 髪の組み方
def clump(m: Model, bone, x, ytop, z, w, L, d, paint, steps=3, tip=0.45, lean=0.0, rot=(0, 0, 0), pivot=None, back=False):
    """下へ垂れる房一つ。根元の箱 + steps 段で細る先 (段ごとに幅が減り、lean の向きへずれる)。
    x = 真ん中、z = 前の面 (back=True なら後ろの面)、tip = 先の長さの割合。"""
    piv = pivot or (x, ytop, z + (d / 2 if not back else -d / 2))
    Lb = L * (1 - tip)
    zf = z if not back else z - d
    m.box(bone, (x - w / 2, ytop - Lb, zf), (w, Lb, d), paint, rot=rot, pivot=piv)
    st = L * tip / steps
    cx = x
    for k in range(steps):
        ww = w * (1 - (k + 1) / (steps + 1))
        cx += lean * (w - ww) / 2
        dd = d * (1 - 0.12 * (k + 1))
        y = ytop - Lb - st * (k + 1)
        zz = zf + (d - dd) / 2
        m.box(bone, (cx - ww / 2, y, zz), (ww, st + 0.02, dd), paint, rot=rot, pivot=piv)


def hair_cap(m: Model, paint, back_bottom=24.5, side_bottom=26.0):
    """頭の下地: 上 (丸み 2 段)・後ろ・横。頭 (8) より 0.5 外。"""
    m.box("head", (-4.5, 31.6, -4.5), (9.0, 1.2, 9.1), paint)
    m.box("head", (-3.9, 32.7, -3.9), (7.8, 0.5, 7.8), paint)
    m.box("head", (-4.5, back_bottom, 3.95), (9.0, 32.2 - back_bottom, 0.65), paint)
    m.pair("head", (-4.6, side_bottom, -4.25), (0.65, 32.0 - side_bottom, 8.4), paint)


def bangs(m: Model, paint, length=4.6, layers=3):
    """前髪 3 層: 奥 (平たく長い)・中 (房 5 つ)・手前 (大きな房 3 つ)。頭の前 (z = -4) の前に 0.25 ずつ重ねる。"""
    top = 32.4
    # 奥: 額を覆う一枚と、短い房
    m.box("head", (-4.4, top - 2.2, -4.45), (8.8, 2.2, 0.45), paint)
    for i, x in enumerate((-3.2, -1.05, 1.05, 3.2)):
        clump(m, "head", x, top - 2.0, -4.45, 2.3, length * 0.6, 0.4, paint, steps=2, lean=(-1 if x < 0 else 1) * 0.6)
    if layers >= 2:
        for i, (x, L, ln) in enumerate(((-2.7, 0.85, -0.8), (-0.5, 1.0, -0.3), (1.6, 0.92, 0.5), (3.4, 0.75, 0.9))):
            clump(m, "head", x, top - 0.2, -4.75, 2.4, length * L, 0.4, paint, steps=3, lean=ln)
    if layers >= 3:
        for (x, L, ln, w) in ((-3.3, 0.95, -0.9, 2.8), (0.2, 1.05, 0.5, 3.2), (3.3, 0.8, 0.9, 2.4)):
            clump(m, "head", x, top + 0.2, -5.05, w, length * L, 0.45, paint, steps=3, lean=ln, tip=0.5)
        # 上から前へかかる丸み
        m.box("head", (-4.5, top - 0.4, -5.05), (9.0, 0.9, 0.95), paint)


def side_locks(m: Model, paint, length=7.0, bone="hair_side_R", second=True):
    """横髪: 頬の横に 2 段 (前の長い房と、後ろの短い房)。揺れる。"""
    m.pair_bone(bone, "head", (-4.5, 31.0, -2.5))
    clump(m, bone, -4.8, 31.0, -4.55, 1.9, length, 1.4, paint, steps=3, lean=0.6)
    if second:
        clump(m, bone, -5.0, 30.5, -2.7, 1.7, length * 0.7, 1.6, paint, steps=3, lean=0.4)
    # 左側は映す
    for b in list(m.boxes):
        if b.bone == bone and not b.mirror:
            x0 = -(b.frm[0] + b.size[0])
            piv = None if b.pivot is None else (-b.pivot[0], b.pivot[1], b.pivot[2])
            m.box(bone.replace("_R", "_L"), (x0, b.frm[1], b.frm[2]), b.size, b.paint, (b.rot[0], -b.rot[1], -b.rot[2]), piv, mirror=True)


def back_hair(m: Model, paint, bottom=13.0, bone="hair_back", width=9.0, layers=3, split=True):
    """後ろ髪 3 層 (内・中・外) を体に付けて揺らす。長いほど房に分かれる。"""
    top = 30.5
    piv = (0, 29.0, 4.6)
    m.bone(bone, "body", piv)
    L = top - bottom
    for k in range(layers):
        z = 4.45 + 0.5 * k
        w = width - 0.9 * k
        n = 4 - k
        cw = w / n
        for i in range(n):
            x = -w / 2 + cw * (i + 0.5)
            Lk = L * (1 - 0.08 * k) * (1 - 0.06 * abs(i - (n - 1) / 2))
            if split and k == layers - 1 and i == n // 2:
                Lk *= 0.85
            clump(m, bone, x, top - 0.6 * k, z, cw + 0.4, Lk, 0.5, paint, steps=3, tip=0.3, lean=(x / (w / 2)) * 0.5, pivot=piv)


def hair_tail(m: Model, paint, root, length=12.0, width=2.6, segs=3, bone="hair_tail", parent="head", side=0.0, curl=8.0):
    """結んだ髪 (一つ結び・二つ結び): segs 節の鎖。節ごとに遅れて揺れる。root = 結び目の geo の位置。"""
    x, y, z = root
    seg = length / segs
    par = parent
    for k in range(segs):
        bn = f"{bone}_{k + 1}"
        m.bone(bn, par, (x, y, z))
        w = width * (1 - 0.18 * k)
        last = k == segs - 1
        if last:
            clump(m, bn, x, y + 0.2, z - w / 2, w, seg + 0.6, w, paint, steps=3, tip=0.6, lean=side, rot=(curl * (k + 1), 0, 0), pivot=(x, y, z))
        else:
            m.box(bn, (x - w / 2, y - seg - 0.3, z - w / 2), (w, seg + 0.6, w), paint, rot=(curl * (k + 1), 0, 0), pivot=(x, y, z))
        a = math.radians(curl * (k + 1))
        y, z = y - seg * math.cos(a), z + seg * math.sin(a)
        x += side * 0.3
        par = bn


def ahoge(m: Model, paint, x=0.4, z=-1.0):
    m.bone("hair_ahoge", "head", (x, 33.1, z))
    m.box("hair_ahoge", (x - 0.25, 33.0, z - 0.25), (0.5, 2.2, 0.5), paint, rot=(-25, 0, 20), pivot=(x, 33.1, z))
    m.box("hair_ahoge", (x - 0.2, 34.7, z - 1.1), (0.4, 1.4, 0.4), paint, rot=(-70, 0, 30), pivot=(x, 33.1, z))


# ================================================================ 髪の組み方 2 (大きな板を傾けて重ねる。docs/design/55 の 3 章)
class HairPaint2:
    """なめらかな髪の絵: 筋の縞は描かない。上が明るく下ほど暗い、天使の輪の光一本、縁は少し締める。"""

    def __init__(self, base, dark, light, hi, line=None, y_hi=35.0, y_lo=10.0, ring_y=32.1):
        self.b, self.d, self.l, self.h = C(base), C(dark), C(light), C(hi)
        self.line = C(line) if line else self.d * 0.8
        self.y_hi, self.y_lo, self.ring_y = y_hi, y_lo, ring_y

    def col_at(self, y):
        t = np.clip((self.y_hi - np.asarray(y, float)) / (self.y_hi - self.y_lo), 0, 1)
        up = mix(self.l, self.b, np.clip(t / 0.22, 0, 1)[..., None])
        dn = mix(self.b, self.d, (np.clip((t - 0.22) / 0.78, 0, 1) ** 1.2)[..., None] * 0.85)
        return np.where(t[..., None] < 0.22, up, dn)

    def __call__(self, face, W, H, box):
        x0, y0, z0 = box.frm
        w, h, d = box.size
        img = blank(W, H, self.b)
        if face in "nsew":
            ys = y0 + h - (np.arange(H) + 0.5) / T
            img[..., :3] = self.col_at(ys)[:, None, :]
            # 天使の輪: やわらかい光の帯 (面の中ほどを強く、端は弱く)
            if face != "s" and y0 <= self.ring_y + 1 and y0 + h >= self.ring_y - 1:
                xs = (np.arange(W) + 0.5) / W
                wave = np.sin(xs * math.pi * 3 + x0) * 0.12
                for i in range(W):
                    # 輪は途切れた弧に (面の幅の中で 3 つほど)
                    seg = max(0.0, np.sin(xs[i] * math.pi * 3.2 + 0.4))
                    k = np.exp(-((ys - (self.ring_y + wave[i])) / 0.2) ** 2) * seg * 0.9
                    img[:, i, :3] = mix(img[:, i, :3], self.h, np.clip(k, 0, 0.9)[:, None])
            # 2 本だけの薄い筋 (房の形が分かる程度)
            if W >= 8:
                for xf in (0.33, 0.7):
                    x = int(W * xf)
                    img[:, x, :3] = mix(img[:, x, :3], self.d, 0.18)
            # 縁を少し締める
            if W >= 4:
                img[:, 0, :3] = mix(img[:, 0, :3], self.line, 0.35)
                img[:, -1, :3] = mix(img[:, -1, :3], self.line, 0.35)
            if H >= 4:
                img[-1, :, :3] = mix(img[-1, :, :3], self.line, 0.4)
        elif face == "u":
            img[..., :3] = mix(self.col_at(np.array([y0 + h]))[0], self.l, 0.35)
        else:
            img[..., :3] = self.d * 0.8
        return out(img)


def sheet(m: Model, bone, x, ytop, z, w, L, d, paint, rz=0.0, rx=0.0, bend=1.6, tip=0.38, pivot=None):
    """垂れる髪の板一枚: 根元の板 + 先へ 2 枚 (同じ付け根のまわりでさらに傾け、細く)。z = 前の面。"""
    piv = pivot or (x, ytop, z + d / 2)
    Lb = L * (1 - tip)
    m.box(bone, (x - w / 2, ytop - Lb, z), (w, Lb + 0.3, d), paint, rot=(rx, 0, rz), pivot=piv)
    m.box(bone, (x - w * 0.32, ytop - L * (1 - tip * 0.35), z + d * 0.06), (w * 0.64, L * tip * 0.75, d * 0.88), paint,
          rot=(rx * 1.2, 0, rz * bend), pivot=piv)
    m.box(bone, (x - w * 0.14, ytop - L - 0.2, z + d * 0.12), (w * 0.28, L * tip * 0.55, d * 0.76), paint,
          rot=(rx * 1.4, 0, rz * bend * 1.35), pivot=piv)


def hair_mass(m: Model, paint, back_bottom=23.5):
    """かさ: 頭より 1px 外の大きな箱、上は 3 段で丸く、後ろの厚み、耳の上の横。"""
    m.box("head", (-5.0, 30.3, -4.95), (10.0, 2.8, 10.1), paint)
    m.box("head", (-4.7, 33.0, -4.6), (9.4, 0.75, 9.4), paint)
    m.box("head", (-4.2, 33.7, -4.0), (8.4, 0.6, 8.3), paint)
    m.box("head", (-3.3, 34.25, -3.1), (6.6, 0.45, 6.5), paint)
    m.box("head", (-5.0, back_bottom, 3.6), (10.0, 30.4 - back_bottom, 1.7), paint)
    m.pair("head", (-5.25, 25.6, -4.1), (1.15, 4.9, 7.9), paint)


def bangs2(m: Model, paint, spec=None):
    """前髪: 大きな房を斜めに垂らして重ねる。spec = [(真ん中の x, 幅, 長さ, 傾き rz, 層)]。"""
    spec = spec or [(-3.2, 3.2, 3.5, 8, 0), (3.2, 3.2, 3.3, -8, 0), (0.0, 3.4, 3.9, -4, 1), (-1.75, 2.0, 2.9, -12, 2), (1.75, 2.0, 2.7, 12, 2)]
    for (x, w, L, rz, layer) in spec:
        z = -5.0 - 0.35 * layer
        sheet(m, "head", x, 33.1, z, w, L + 1.7, 0.55, paint, rz=rz, rx=-5, bend=1.4)


def side_hair2(m: Model, paint, length=13.0, bone="hair_side_R"):
    """横髪: 頬の前の長い板と、耳の後ろの板。外へ少し開いて垂れる。左は映す。"""
    piv = (-4.9, 31.0, -2.2)
    m.pair_bone(bone, "head", piv)
    sheet(m, bone, -5.35, 31.0, -4.55, 1.2, length, 2.6, paint, rz=-4, rx=-3, bend=1.15, pivot=piv)
    sheet(m, bone, -5.6, 30.6, -2.1, 1.25, length * 0.8, 4.3, paint, rz=-7, rx=3, bend=1.15, pivot=piv)
    for b in [b for b in m.boxes if b.bone == bone and not b.mirror]:
        x0 = -(b.frm[0] + b.size[0])
        p = None if b.pivot is None else (-b.pivot[0], b.pivot[1], b.pivot[2])
        m.box(bone.replace("_R", "_L"), (x0, b.frm[1], b.frm[2]), b.size, b.paint, (b.rot[0], -b.rot[1], -b.rot[2]), p, mirror=True)


def back_hair2(m: Model, paint, bottom=12.0, bone="hair_back"):
    """後ろ髪: 内の広い板と、外へ扇に開く大きな板 3 枚 (体の骨に付けて揺らす)。"""
    top = 30.0
    piv = (0, 29.5, 4.8)
    m.bone(bone, "body", piv)
    L = top - bottom
    m.box(bone, (-4.7, top - L * 0.82, 4.0), (9.4, L * 0.82, 0.8), paint, rot=(4, 0, 0), pivot=piv)
    for (x, w, rz, k, z) in ((-3.1, 4.2, -9, 0.95, 4.6), (3.1, 4.2, 9, 0.95, 4.6), (0.0, 4.8, 0, 1.0, 5.15)):
        sheet(m, bone, x, top, z, w, L * k, 1.0, paint, rz=rz, rx=7, bend=1.4, tip=0.3, pivot=(x, top, z + 0.5))


def ahoge2(m: Model, paint, x=0.5, z=-0.8):
    """アホ毛: 先が丸まる 3 枚。"""
    m.bone("hair_ahoge", "head", (x, 34.3, z))
    m.box("hair_ahoge", (x - 0.3, 34.2, z - 0.3), (0.6, 1.8, 0.6), paint, rot=(-18, 0, 12), pivot=(x, 34.3, z))
    m.box("hair_ahoge", (x - 0.25, 35.6, z - 0.9), (0.5, 1.2, 0.5), paint, rot=(-60, 0, 20), pivot=(x, 34.3, z))
    m.box("hair_ahoge", (x - 0.2, 35.4, z - 2.0), (0.4, 0.9, 0.4), paint, rot=(-120, 0, 25), pivot=(x, 34.3, z))
