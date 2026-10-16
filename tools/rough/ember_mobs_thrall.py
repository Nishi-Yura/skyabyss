"""灰被り (ash_thrall, はいかぶり): 消えた灰の民 (鍛冶の民) の名残。身の丈約 2.2 ブロック (35 単位)。

冷えた灰・鉱滓の厚い殻をまとい、殴られると殻が割れて「燃え上がる」(光るひびが開く)。
- 殻は crust_* の骨 (殻のある姿だけ表示)。燃え上がった姿では隠し、下の体 (炭の肌) のひびが光る。
- 炎の舌は flare_* の骨 (燃え上がった姿だけ表示)。どちらの骨も、体の立方体とは重ならない。
- テクスチャは同じ uv の配置で 2 枚: texture.png (殻) / texture_flared.png (燃え上がり)。glowmask も 2 枚。
- 武器は別のモデル (weapon/ 以下)。右の拳は穴の開いた握り (1x1 の穴) で、武器の柄がその穴を通る。
  受け口 socket_main の pivot = 穴の中心。武器の geo の原点 = 握りの中心。
"""
from __future__ import annotations

import copy
import json
import math

import ember_mobs_common as E

R = E.EMBER_RAMP

PARAM = {
    #    幅の倍率, 前腕の x (= 穴の中心の x の大きさ), 穴の z
    "a": dict(w=0.85, fx=6.0, hz=2.0),
    "b": dict(w=1.15, fx=7.5, hz=2.0),
    "c": dict(w=1.0, fx=6.5, hz=2.0),
}
HAND_Y = 12.5


def surface_layer(sc, src_bones, region, mat, bone, zdir=1, th=0.5, bbox=(-8, 0, -6, 8, 36, 6), default=None, over=False):
    """src_bones のセルの前 (zdir=+1) か後ろ (-1) の面に、厚み th の層を貼る (region(x, y) が真の列だけ)。"""
    cols = {}
    for (i, j, k), (b, m) in sc.cells.items():
        if b in src_bones:
            key = (i, j)
            cols[key] = max(cols.get(key, -999), k) if zdir > 0 else min(cols.get(key, 999), k)
    X0, Y0, _, X1, Y1, _ = (int(round(v * 2)) for v in bbox)
    n = 0
    for i in range(X0, X1):
        for j in range(Y0, Y1):
            x, y = (i + .5) / 2, (j + .5) / 2
            if not region(x, y):
                continue
            if (i, j) in cols:
                k0 = cols[(i, j)] + 1 if zdir > 0 else cols[(i, j)] - 1
            elif default is not None:
                k0 = int(round(default * 2))
            else:
                continue
            for t in range(int(th * 2)):
                q = (i, j, k0 + t * zdir)
                if q not in sc.cells:
                    sc.cells[q] = (bone, mat)
                    n += 1
    return n


def build(opt: str):
    P = PARAM[opt]
    w, fx, hz = P["w"], P["fx"], P["hz"]
    sc = E.Sculpt(f"ash_thrall_{opt}")
    sc.bone("body", (0, 15, 0))
    sc.bone("head", (0, 28, 1.0), "body")
    sc.bone("leg_r", (-2.5, 15, 0))
    sc.bone("leg_l", (2.5, 15, 0))
    for s, side in ((-1, "r"), (1, "l")):
        sc.bone(f"arm_{side}", (s * (fx - 1.0), 25.5, 0.75), "body")
        sc.bone(f"forearm_{side}", (s * fx, 19.0, 1.0), f"arm_{side}")
    sc.bone("socket_main", (-fx, HAND_Y, hz), "forearm_r")
    for nm, par in (("crust_body", "body"), ("crust_head", "head"), ("crust_arm_r", "arm_r"), ("crust_arm_l", "arm_l"),
                    ("crust_fore_r", "forearm_r"), ("crust_fore_l", "forearm_l"), ("crust_leg_r", "leg_r"), ("crust_leg_l", "leg_l")):
        piv = next(b["pivot"] for b in sc.bones if b["name"] == par)
        sc.bone(nm, piv, par, hideable=True)
    sc.bone("flare_body", (0, 15, 0), "body", hideable=True)
    sc.bone("flare_head", (0, 28, 1.0), "head", hideable=True)

    def add(bone, c, r, mat, p=2.5):
        sc.add(bone, E.ell(c, r, p), mat, E.bb(c, r, pad=1.0), sz=2)

    # ---- 脚
    for s, side in ((-1, "r"), (1, "l")):
        lb = f"leg_{side}"
        add(lb, (s * 2.5, 0.75, 0.75), (1.5 * w + 0.25, 0.75, 2.25), "char", p=3)          # 足
        add(lb, (s * 2.5, 4.75, 0.0), (1.25 * w, 3.5, 1.25), "char")                     # すね
        add(lb, (s * 2.5, 8.75, 0.5), (1.5 * w, 1.25, 1.5), "char", p=2)                 # 膝
        add(lb, (s * 2.5, 12.25, 0.0), (1.75 * w, 3.25, 1.75), "char")                   # 腿
    # ---- 胴 (前かがみ: 胸は前へ 0.75)
    add("body", (0, 15.75, 0.0), (3.5 * w, 1.75, 2.25), "char")
    add("body", (0, 18.5, 0.25), (3.25 * w, 2.0, 2.25), "char")
    add("body", (0, 22.75, 0.75), (4.5 * w, 3.5, 2.75), "char")
    add("body", (0, 27.0, 1.25), (1.5, 1.25, 1.5), "char", p=2)                          # 首
    # ---- 腕
    for s, side in ((-1, "r"), (1, "l")):
        ab, fb = f"arm_{side}", f"forearm_{side}"
        ax = fx - 0.5
        add(ab, (s * (ax - 0.5), 25.0, 0.75), (1.75, 1.75, 1.75), "char", p=2)          # 肩
        add(ab, (s * ax, 22.0, 0.75), (1.25, 3.0, 1.25), "char")                         # 二の腕
        add(fb, (s * fx, 16.5, 1.0), (1.25, 2.75, 1.35), "char")                         # 前腕
    # 右の拳: 穴の開いた握り (武器の柄が穴を通る)
    hx = -fx

    def fist(x, y, z):
        if abs(x - hx) < 0.5 and abs(z - hz) < 0.5:
            return False
        return E.ell((hx, HAND_Y + 0.25, hz), (1.5, 1.75, 1.5), 3)(x, y, z)
    sc.add("forearm_r", fist, "char", E.bb((hx, HAND_Y + 0.25, hz), (1.5, 1.75, 1.5)))
    sc.box("forearm_r", hx + 0.5, HAND_Y + 0.5, hz + 1.5, hx + 1.5, HAND_Y + 1.5, hz + 2.0, "char")  # 親指
    # 左の手: 開いた鉤爪
    add("forearm_l", (fx, 12.75, 1.25), (1.25, 1.25, 1.25), "char", p=3)
    for dz in (0.0, 1.0, 2.0):
        sc.box("forearm_l", fx - 0.5, 10.5, dz, fx, 11.5, dz + 0.5, "char")
        sc.box("forearm_l", fx - 0.5, 10.0, dz + 0.5, fx, 10.5, dz + 1.0, "char")

    # ---- 頭 (案ごと)
    if opt == "a":
        build_head_a(sc)
    elif opt == "b":
        build_head_b(sc)
    else:
        build_head_c(sc)

    # ---- 殻と装い (案ごと)
    {"a": dress_a, "b": dress_b, "c": dress_c}[opt](sc, P)
    return sc


# ---------------------------------------------------------------- 頭

def eyes(sc, y0, y1, xs, zmin, bone="head"):
    for a, b in xs:
        sc.recolor(lambda x, y, z, a=a, b=b: a <= x <= b and y0 <= y <= y1 and z >= zmin, "eye",
                   (a - .5, y0 - .5, zmin - .5, b + .5, y1 + .5, zmin + 4), bone=bone)


def front_z(sc, bone, x, y):
    i, j = int(math.floor(x * 2)), int(math.floor(y * 2))
    ks = [k for (a, b, k), (bn, m) in sc.cells.items() if a == i and b == j and bn == bone]
    return (max(ks) + 0.5) / 2 if ks else None


def build_head_a(sc):
    """灰の衣のフード: 顔は暗く奥まり、目だけ光る。フードの先が後ろへ尖る。"""
    c = (0, 31.0, 1.5)
    sc.add("head", E.ell(c, (2.25, 2.5, 2.0), 2.2), "face", E.bb(c, (2.25, 2.5, 2.0), pad=1.0), sz=2)
    sc.box("head", -1.25, 28.25, 2.0, 1.25, 28.75, 3.0, "face")          # 顎
    hc = (0, 31.5, 1.0)

    def hood(x, y, z):
        if E.ell(hc, (2.75, 3.0, 2.75), 2.2)(x, y, z):
            return False
        if not E.ell(hc, (3.75, 4.0, 3.75), 2.2)(x, y, z):
            return False
        if z > 1.25 and abs(x) < 2.5 and 28.0 < y < 33.25:   # 顔の開き
            return False
        return y > 28.0 or z < 0
    sc.add("head", hood, "hood", E.bb(hc, (3.75, 4.0, 3.75)), sz=2)
    sc.add("head", E.ell((0, 34.75, -1.75), (1.25, 1.25, 1.5), 2), "hood", E.bb((0, 34.75, -1.75), (1.25, 1.25, 1.5)))
    sc.add("head", E.ell((0, 35.5, -2.75), (0.75, 0.75, 1.0), 2), "hood", E.bb((0, 35.5, -2.75), (0.75, 0.75, 1.0)))
    eyes(sc, 30.75, 31.5, ((-1.75, -0.5), (0.5, 1.75)), 2.75)
    # 燃え上がり: フードの頂から炎の舌
    for x0, z0, h in ((-0.5, -2.5, 3.0), (0.5, -1.5, 2.0), (-1.5, -1.0, 1.5), (1.0, -3.0, 1.5)):
        sc.box("flare_head", x0, 36.0, z0, x0 + 0.5, 36.0 + h, z0 + 0.5, "flame")
    sc.add("flare_head", lambda x, y, z: E.ell((0, 36.5, -2.0), (1.0, 0.75, 1.0), 2)(x, y, z), "flame", E.bb((0, 36.5, -2.0), (1, 1, 1)))


def build_head_b(sc):
    """鉱滓の兜: 重い眉庇の下に深い目、突き出た顎。殻 (兜) が割れると炭の頭と炎の鬣。"""
    c = (0, 31.0, 1.5)
    sc.add("head", E.ell(c, (2.5, 2.75, 2.5), 2.3), "face", E.bb(c, (2.5, 2.75, 2.5), pad=1.0), sz=2)
    sc.add("head", E.ell((0, 28.75, 2.75), (2.0, 1.0, 1.5), 3), "face", E.bb((0, 28.75, 2.75), (2.0, 1.0, 1.5)))  # 顎
    eyes(sc, 30.5, 31.0, ((-1.75, -0.75), (0.75, 1.75)), 3.0)
    # 兜 (殻): 上と横を覆い、前は眉庇だけ
    hc = (0, 31.5, 1.25)

    def helm(x, y, z):
        if not E.ell(hc, (3.5, 3.75, 3.5), 2.6)(x, y, z):
            return False
        if z > 2.0 and y < 31.75 and abs(x) < 2.25:
            return False
        return y > 29.0
    sc.add("crust_head", helm, "slag", E.bb(hc, (3.5, 3.75, 3.5)), sz=2)
    sc.box("crust_head", -3.0, 31.75, 4.0, 3.0, 32.75, 5.0, "slag")       # 眉庇 (前へ張り出す)
    sc.box("crust_head", -2.0, 35.0, -1.0, 2.0, 35.5, 2.0, "slag")        # 頭頂の盛り
    sc.box("crust_head", -1.0, 35.5, -0.5, 1.0, 36.0, 1.0, "slag")
    # 燃え上がり: 頭の後ろの炎の鬣
    for x0, h in ((-2.0, 2.0), (-1.0, 3.0), (0.0, 3.5), (1.0, 2.5), (2.0, 1.5)):
        sc.box("flare_head", x0 - 0.5, 33.5, -1.75, x0, 33.5 + h, -1.25, "flame")
        sc.box("flare_head", x0 - 0.5, 32.5, -2.25, x0, 32.5 + h * 0.6, -1.75, "flame")


def build_head_c(sc):
    """炉の面: 窯のような半球の面。横一文字の覗き穴が光る。頂に小さな煙突。"""
    c = (0, 31.0, 1.0)
    sc.add("head", E.ell(c, (2.25, 2.5, 2.0), 2.2), "face", E.bb(c, (2.25, 2.5, 2.0), pad=1.0), sz=2)
    mc = (0, 31.25, 1.0)

    def mask(x, y, z):
        if E.ell(mc, (2.5, 2.75, 2.25), 2.4)(x, y, z):
            return False
        if not E.ell(mc, (3.25, 3.75, 3.25), 2.4)(x, y, z):
            return False
        if 30.5 <= y <= 31.5 and abs(x) <= 2.25 and z > 1.0:   # 覗き穴 (横一文字)
            return False
        return y > 27.75
    sc.add("head", mask, "kiln", E.bb(mc, (3.25, 3.75, 3.25)), sz=2)
    # 覗き穴の奥: 顔の前に溶けた光の層
    sc.add("head", lambda x, y, z: 30.5 <= y <= 31.5 and abs(x) <= 2.25 and 1.0 < z <= 3.5, "slit", (-2.5, 30.25, 1.0, 2.5, 31.75, 3.75))
    sc.box("head", -1.0, 34.75, -0.5, 1.0, 36.5, 1.5, "kiln")              # 煙突
    sc.carve(lambda x, y, z: abs(x) < 0.5 and 36.0 <= y and 0 <= z <= 1.0, (-1, 36, -1, 1, 37, 2), bone="head")
    sc.box("head", -0.5, 35.5, 0.0, 0.5, 36.0, 1.0, "slit")                 # 煙突の奥の火
    # 頬の通気孔 (2 つずつ)
    sc.recolor(lambda x, y, z: abs(x) >= 2.25 and 28.75 <= y <= 29.75 and z > 1.5 and (math.floor(y * 2) % 2 == 0),
               "vent", (-4, 28, 0, 4, 30, 5), bone="head")
    # 燃え上がり: 煙突から炎
    sc.add("flare_head", lambda x, y, z: E.ell((0, 37.5, 0.5), (0.75, 1.25, 0.75), 2)(x, y, z), "flame", (-1.5, 36.5, -1, 1.5, 39.5, 2))
    sc.box("flare_head", -0.5, 38.5, 0.5, 0.0, 40.0, 1.0, "flame")


# ---------------------------------------------------------------- 殻と装い

def crust_patch(sc, src, bone, region, mat, seed, th=1.0, thr=0.38, s=3.0, zdir=None):
    """src の骨の体の外側に、1 単位の立方体で殻の塊を貼る (s 単位の塊ごとの雑音で欠けを作る)。
    th=1 なら 1 層、th=2 なら 2 層 (外へ)。"""
    occ = set(sc.cells)
    src_cells = {q for q, (b, m) in sc.cells.items() if b == src}
    layer = {(i - i % 2, j - j % 2, k - k % 2) for (i, j, k) in src_cells}
    n = 0
    for _ in range(int(th)):
        cand = set()
        for (i, j, k) in layer:
            for di, dj, dk in E.DIRS6:
                cand.add((i + 2 * di, j + 2 * dj, k + 2 * dk))
        new = set()
        for q in cand:
            sub = [(q[0] + a, q[1] + b, q[2] + c) for a in (0, 1) for b in (0, 1) for c in (0, 1)]
            if any(t in occ for t in sub):
                continue
            x, y, z = q[0] / 2 + 0.5, q[1] / 2 + 0.5, q[2] / 2 + 0.5
            if not region(x, y, z) or E.blob_noise(x, y, z, s=s, seed=seed) < thr:
                continue
            for t in sub:
                sc.cells[t] = (bone, mat)
                occ.add(t)
            new.add(q)
            n += 1
        layer = new
    return n


def dress_a(sc, P):
    """灰の衣: 細身。肩を覆う灰の布のケープ、背の裂けた布。殻は胸・前腕・すねの灰の塊。"""
    # ケープ (肩の上を覆う): 体の骨
    def cape(x, y, z):
        return (E.ell((0, 25.5, 0.75), (5.75, 2.75, 3.75), 2.3)(x, y, z) and y > 24.0
                and not E.ell((0, 25.5, 0.75), (4.75, 2.0, 2.75), 2.3)(x, y, z))
    sc.add("body", cape, "cloth", (-6.5, 22, -4, 6.5, 29, 5), sz=2)
    # 背の裂けた布 (後ろの面に貼る)
    for x0, x1, yb in ((-3.0, -1.5, 9.0), (-1.5, 0.0, 12.0), (0.0, 1.5, 8.0), (1.5, 3.0, 11.0)):
        surface_layer(sc, {"body"}, lambda x, y, x0=x0, x1=x1, yb=yb: x0 <= x < x1 and yb <= y < 24.0, "cloth", "body",
                      zdir=-1, default=-2.25, bbox=(-4, 6, -6, 4, 26, 6))
    # 腰帯
    sc.add("body", lambda x, y, z: 16.5 <= y <= 17.5 and E.ell((0, 17, 0.25), (3.5 * P["w"] + 0.6, 1.0, 2.85), 2.5)(x, y, z),
           "leather", (-4, 16, -3.5, 4, 18, 3.5))
    # 殻: 胸の前、肩、前腕、すねの前と外
    crust_patch(sc, "body", "crust_body", lambda x, y, z: 14.5 < y < 24.5, "crust", 1, th=1.0, thr=0.3)
    for s, side in ((-1, "r"), (1, "l")):
        crust_patch(sc, f"forearm_{side}", f"crust_fore_{side}", lambda x, y, z, s=s: 14.5 < y < 19.0 and (s * x > P["fx"] - 0.5 or z > 1.5),
                    "crust", 2 + (s > 0), th=1.0, thr=0.3)
        crust_patch(sc, f"arm_{side}", f"crust_arm_{side}", lambda x, y, z: 19.5 < y < 23.5, "crust", 4 + (s > 0), th=1, thr=0.35)
        crust_patch(sc, f"leg_{side}", f"crust_leg_{side}", lambda x, y, z: 2.0 < y < 13.5, "crust", 6 + (s > 0), th=1.0, thr=0.3)
    # 燃え上がり: 肩から炎の舌
    for s in (-1, 1):
        for x0, z0, h in ((4.0, -0.5, 1.5), (4.5, 1.0, 2.5), (3.5, 0.5, 2.0)):
            a, b = sorted((s * x0, s * (x0 + 0.5)))
            sc.box("flare_body", a, 28.25, z0, b, 28.25 + h, z0 + 0.5, "flame")


def dress_b(sc, P):
    """鉱滓の鎧: 重い。胸と背の鉱滓の板、巨大な肩当て、腿当て、籠手。燃え上がると背から炎の鬣。"""
    crust_patch(sc, "body", "crust_body", lambda x, y, z: 15.5 < y < 26.0, "slag", 11, th=2, thr=0.22)
    for s, side in ((-1, "r"), (1, "l")):
        # 肩当て: 肩の上と外に厚く
        crust_patch(sc, f"arm_{side}", f"crust_arm_{side}", lambda x, y, z, s=s: y > 22.5 and s * x > P["fx"] - 2.5, "slag", 12 + (s > 0),
                    th=2, thr=0.1, s=4.0)
        crust_patch(sc, f"forearm_{side}", f"crust_fore_{side}", lambda x, y, z: 14.0 < y < 19.0, "slag", 14 + (s > 0), th=1.0, thr=0.2)
        crust_patch(sc, f"leg_{side}", f"crust_leg_{side}", lambda x, y, z: 9.5 < y < 14.5 or (2.0 < y < 7.5 and z > 0), "slag",
                    16 + (s > 0), th=1.0, thr=0.25)
    # 腰の鎖垂れ (鉄の板 3 枚、前)
    for x0 in (-2.5, -0.75, 1.0):
        surface_layer(sc, {"body", "crust_body"}, lambda x, y, x0=x0: x0 <= x < x0 + 1.5 and 10.5 <= y < 14.5, "iron", "body",
                      zdir=1, default=2.5, bbox=(-4, 10, -6, 4, 15, 6))
    # 燃え上がり: 背中の炎の鬣 (背骨に沿って)
    surface_layer(sc, {"body", "crust_body"}, lambda x, y: abs(x) <= 1.25 and 18.0 <= y < 26.5 and (math.floor((x + 2) * 2) % 2 == 0),
                  "flame", "flare_body", zdir=-1, th=1.5, bbox=(-2, 17, -8, 2, 27, 6))
    for s in (-1, 1):
        for x0, h in ((3.0, 2.0), (4.0, 3.0), (5.0, 2.0)):
            a, b = sorted((s * x0, s * (x0 + 0.5)))
            sc.box("flare_body", a, 28.5, -1.5, b, 28.5 + h, -1.0, "flame")


def dress_c(sc, P):
    """炉の面: 鍛冶の革の前掛け (胸から膝)。殻は肩・腕・背・すねの灰。"""
    surface_layer(sc, {"body"}, lambda x, y: abs(x) <= (2.5 if y > 21 else 3.25) and 9.5 <= y < 24.5, "leather", "body",
                  zdir=1, default=2.5, bbox=(-4, 9, -6, 4, 25, 6))
    # 前掛けの紐 (首の後ろへ)
    sc.add("body", lambda x, y, z: 2.0 <= abs(x) <= 2.5 and 24.5 <= y <= 26.5 and E.ell((0, 22.75, 0.75), (4.5 + 0.6, 3.6, 2.75 + 0.6), 2.5)(x, y, z),
           "leather", (-3, 24, -4, 3, 27, 4))
    crust_patch(sc, "body", "crust_body", lambda x, y, z: z < 0.0 and 16.0 < y < 26.5, "crust", 21, th=1.0, thr=0.25)
    for s, side in ((-1, "r"), (1, "l")):
        crust_patch(sc, f"arm_{side}", f"crust_arm_{side}", lambda x, y, z: y > 20.5, "crust", 22 + (s > 0), th=1.0, thr=0.25)
        crust_patch(sc, f"forearm_{side}", f"crust_fore_{side}", lambda x, y, z: 14.5 < y < 18.5, "crust", 24 + (s > 0), th=1, thr=0.3)
        crust_patch(sc, f"leg_{side}", f"crust_leg_{side}", lambda x, y, z: 1.5 < y < 8.0, "crust", 26 + (s > 0), th=1.0, thr=0.3)
    # 燃え上がり: 背の殻の割れ目から炎
    for x0, y0, h in ((-2.5, 22.0, 2.0), (-1.0, 24.0, 2.5), (0.5, 21.0, 2.0), (2.0, 23.5, 1.5)):
        sc.add("flare_body", lambda x, y, z, x0=x0, y0=y0, h=h: x0 <= x < x0 + 0.5 and y0 <= y < y0 + h and -4.5 <= z < -3.0,
               "flame", (x0 - .5, y0 - .5, -5, x0 + 1, y0 + h + .5, -2.5))


# ---------------------------------------------------------------- 塗り

MATS = {
    "char": "char", "face": "char", "eye": "ember", "crust": "crust", "slag": "slag", "hood": "ash_cloth", "cloth": "ash_cloth",
    "leather": "leather", "iron": "iron", "kiln": "kiln", "slit": "molten", "vent": "char", "flame": "ember",
}


def set_ramps():
    for nm, rp in MATS.items():
        E.tm.RAMP[nm] = R[rp]


def make_paint(opt):
    def crack(X, Y, Z):
        return E.voronoi_edge(X, Y, Z, s=2.6, w=0.30, seed=9)

    def paint(X, Y, Z, f, mat, st):
        fl = st == "flared"
        h = E.h01(X, Y, Z)
        if mat in ("char", "face"):
            if crack(X, Y, Z):
                if fl:
                    return R["molten"][2] if h > 0.4 else R["ember"][2]
                return R["deepred"][0]
            if mat == "face" and not fl:
                return R["char"][0] if h > 0.3 else R["char"][1]
            return (R["char"][2] if h < 0.15 else None) if not fl else (R["deepred"][0] if h < 0.25 else None)
        if mat == "eye":
            return R["molten"][3] if fl else R["ember"][2]
        if mat == "crust":
            if h < 0.18:
                return R["ash"][3]
            if h < 0.35:
                return R["crust"][2]
            if h > 0.88:
                return R["crust"][0]
            if E.voronoi_edge(X, Y, Z, s=1.6, w=0.18, seed=4):
                return R["crust"][0]
            return None
        if mat == "slag":
            if E.voronoi_edge(X, Y, Z, s=2.2, w=0.2, seed=6):
                return R["slag_dk"][0]
            if h < 0.15:
                return R["rust"][2]
            if h < 0.3:
                return R["slag"][2]
            if h > 0.9:
                return R["slag_dk"][1]
            return None
        if mat in ("hood", "cloth"):
            if fl and Y < 26.0 and mat == "cloth" and (Y % 3.0) < 0.5 and h > 0.5:
                return R["ember"][2]
            if math.floor((X + Y + Z) * 2) % 5 == 0:
                return R["ash_cloth"][0]
            return R["ash_cloth"][3] if h < 0.1 else None
        if mat == "leather":
            if abs((X * 2) % 3) < 0.6 and f == "south" and h > 0.4:
                return R["leather"][0]
            return R["leather"][2] if h < 0.12 else None
        if mat == "kiln":
            row = math.floor(Y * 2) % 3 == 0
            col = math.floor((X + Z + (0.75 if math.floor(Y / 1.5) % 2 else 0)) * 2) % 4 == 0
            if row or col:
                return R["slag_dk"][1]
            return R["kiln"][2] if h < 0.2 else (R["rust"][1] if h > 0.85 else None)
        if mat == "slit":
            return R["molten"][3] if fl else R["molten"][1]
        if mat == "vent":
            return R["ember"][2] if fl else R["char"][0]
        if mat == "flame":
            return R["molten"][3] if h < 0.3 else (R["molten"][2] if h < 0.7 else R["ember"][2])
        if mat == "iron":
            return R["rust"][1] if h < 0.15 else None
        return None

    def lit(X, Y, Z, f, mat, st):
        fl = st == "flared"
        if mat in ("eye", "flame"):
            return True
        if mat == "slit":
            return True
        if mat == "vent":
            return fl
        if fl and mat in ("char", "face"):
            return crack(X, Y, Z)
        if fl and mat == "cloth":
            return Y < 26.0 and (Y % 3.0) < 0.5 and E.h01(X, Y, Z) > 0.5
        return False
    return paint, lit


# ---------------------------------------------------------------- 武器 (別のモデル)

def ring(sc, bone, y0, y1, mat, r=1.0):
    sc.add(bone, lambda x, y, z: max(abs(x), abs(z)) <= r and not (abs(x) < 0.5 and abs(z) < 0.5), mat, (-r, y0, -r, r, y1, r))


def weapon_poker():
    sc = E.Sculpt("ash_poker")
    sc.bone("weapon", (0, 0, 0))
    sc.box("weapon", -0.5, -7.0, -0.5, 0.5, -2.5, 0.5, "iron")
    sc.box("weapon", -0.5, -2.5, -0.5, 0.5, 2.5, 0.5, "leather")          # 握り (拳の穴の中)
    sc.box("weapon", -0.5, 2.5, -0.5, 0.5, 15.0, 0.5, "iron")
    ring(sc, "weapon", 3.0, 3.5, "bronze")
    ring(sc, "weapon", 10.0, 10.5, "bronze")
    # 柄の先の輪 (吊るす輪)
    sc.add("weapon", lambda x, y, z: abs(z) < 0.5 and max(abs(x), abs(y + 8.5)) <= 1.5 and not (abs(x) < 0.5 and abs(y + 8.5) < 0.5),
           "iron", (-1.5, -10, -0.5, 1.5, -7, 0.5))
    # 焼けた先 (赤 -> 橙 -> 黄)
    sc.box("weapon", -0.5, 15.0, -0.5, 0.5, 17.0, 0.5, "hot1")
    sc.box("weapon", -0.5, 17.0, -0.5, 0.5, 19.0, 0.5, "hot2")
    sc.box("weapon", -0.5, 19.0, -0.5, 0.5, 21.0, 0.5, "hot3")
    sc.box("weapon", 0.5, 18.5, -0.5, 1.5, 19.5, 0.5, "hot2")              # 鉤
    sc.box("weapon", 1.5, 19.0, -0.5, 2.0, 20.5, 0.5, "hot3")
    sc.box("weapon", -0.25 - 0.25, 21.0, -0.5, 0.0, 22.0, 0.0, "hot3")     # 尖り
    return sc


def weapon_hammer():
    sc = E.Sculpt("ash_hammer")
    sc.bone("weapon", (0, 0, 0))
    sc.box("weapon", -0.75 - 0.25, -7.0, -1.0, 1.0, -6.0, 1.0, "bronze")   # 石突き
    sc.box("weapon", -0.5, -6.0, -0.5, 0.5, -2.5, 0.5, "wood")
    sc.box("weapon", -0.5, -2.5, -0.5, 0.5, 2.5, 0.5, "leather")
    sc.box("weapon", -0.5, 2.5, -0.5, 0.5, 13.5, 0.5, "wood")
    ring(sc, "weapon", 6.0, 6.5, "bronze")
    ring(sc, "weapon", 12.5, 13.5, "bronze")
    hc = (0, 16.0, 0)
    sc.add("weapon", E.ell(hc, (4.25, 2.5, 2.25), 4), None, E.bb(hc, (4.25, 2.5, 2.25)),
           mat_fn=lambda c: "hot3" if c[0] > 3.5 else ("hot1" if c[0] > 2.75 else ("bronze" if abs(c[0]) < 0.75 else "iron")))
    sc.box("weapon", -0.5, 18.5, -0.5, 0.5, 19.0, 0.5, "bronze")           # 頭の楔
    return sc


def weapon_tongs():
    sc = E.Sculpt("ash_tongs")
    sc.bone("weapon", (0, 0, 0))
    sc.box("weapon", -0.5, -3.5, -0.5, 0.5, 2.5, 0.5, "leather")           # 握り (2 本の柄を握り込む)
    sc.box("weapon", -1.0, 2.5, -0.5, 1.0, 3.5, 0.5, "iron")
    for x0 in (-1.0, 0.5):
        sc.box("weapon", x0, 3.5, -0.5, x0 + 0.5, 8.5, 0.5, "iron")         # 2 本の柄
    sc.box("weapon", -1.25 - 0.25, 8.5, -0.5, -0.5, 9.0, 0.5, "iron")
    sc.box("weapon", 0.5, 8.5, -0.5, 1.5, 9.0, 0.5, "iron")
    sc.box("weapon", -1.0, -5.0, -1.0, 1.0, -3.5, 1.0, "bronze")           # かしめの節
    sc.box("weapon", -0.5, -4.75, 1.0, 0.5, -3.75, 1.5, "iron")            # 鋲
    for s in (-1, 1):
        a, b = sorted((s * 0.5, s * 1.0))
        sc.box("weapon", a, -11.0, -0.5, b, -5.0, 0.5, "iron")              # 顎
        a2, b2 = sorted((s * 1.0, s * 1.5))
        sc.box("weapon", a2, -12.5, -0.5, b2, -11.0, 0.5, "iron")
        a3, b3 = sorted((s * 0.5, s * 1.0))
        sc.box("weapon", a3, -13.5, -1.0, b3, -12.5, 1.0, "hot1")           # 赤い顎先
    # 挟んだ燠 (光る)
    sc.add("weapon", E.ell((0, -13.25, 0), (0.75, 1.25, 1.25), 2), "hot3", (-0.5, -14.75, -1.5, 0.5, -11.5, 1.5))
    sc.box("weapon", -0.5, -11.5, -0.5, 0.5, -11.0, 0.5, "hot2")
    return sc


WEAPON_MATS = {"iron": "iron", "leather": "leather", "bronze": "bronze", "wood": "wood", "hot1": "deepred", "hot2": "ember", "hot3": "molten"}


def weapon_paint(X, Y, Z, f, mat, st):
    h = E.h01(X, Y, Z)
    if mat == "leather":
        return R["leather"][0] if (math.floor(Y * 2) % 2 == 0) else R["leather"][2]
    if mat == "iron":
        return R["rust"][1] if h < 0.12 else (R["iron"][3] if h > 0.92 else None)
    if mat == "wood":
        return R["wood"][0] if math.floor(Y * 2) % 3 == 0 and h > 0.4 else None
    if mat == "hot3":
        return R["molten"][3] if h < 0.4 else R["molten"][2]
    if mat == "hot2":
        return R["ember"][2]
    if mat == "hot1":
        return R["deepred"][3]
    return None


def weapon_lit(X, Y, Z, f, mat, st):
    return mat in ("hot2", "hot3") or (mat == "hot1" and E.h01(X, Y, Z) > 0.5)


WEAPONS = {"a": ("poker", weapon_poker, (0, 0, 8)), "b": ("hammer", weapon_hammer, (0, 0, 10)), "c": ("tongs", weapon_tongs, (-25, 0, 0))}
LABELS = {"a": "A 灰の衣 (フード + 火掻き棒)", "b": "B 鉱滓の鎧 (兜 + 鍛冶の鎚)", "c": "C 炉の面 (窯の面 + 火箸)"}


def filtered(geo, keep):
    g = copy.deepcopy(geo)
    for b in g["minecraft:geometry"][0]["bones"]:
        if b["name"] not in keep:
            b["cubes"] = []
    return g


def run():
    folder = E.MOBS / "ash_thrall"
    cols, stats = [], {}
    for opt in "abc":
        set_ramps()
        sc = build(opt)
        paint, lit = make_paint(opt)
        m = E.build_model(sc, paint, lit)
        of = folder / opt
        r = E.export_states(m, of, ("crusted", "flared"))
        bones = [b["name"] for b in r["geo"]["minecraft:geometry"][0]["bones"]]
        crust = [b for b in bones if b.startswith("crust_")]
        flare = [b for b in bones if b.startswith("flare_")]
        (of / "states.json").write_text(json.dumps({
            "crusted": {"texture": "texture.png", "glowmask": "glowmask.png", "hide_bones": flare},
            "flared": {"texture": "texture_flared.png", "glowmask": "glowmask_flared.png", "hide_bones": crust},
        }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        # 武器
        wname, wfn, wpose = WEAPONS[opt]
        for nm, rp in WEAPON_MATS.items():
            E.tm.RAMP[nm] = R[rp]
        wm = E.build_model(wfn(), weapon_paint, weapon_lit)
        wf = of / f"weapon_{wname}"
        wr = E.export_states(wm, wf, ("base",), root=False)
        wtex, wgl, wgpx = wr["states"]["base"]
        weapon = (wr["geo"], wtex, wgl)
        for nm, rp in MATS.items():   # 武器の材質名 (iron, leather) を体の色に戻す
            E.tm.RAMP[nm] = R[rp]

        ct, cg, cpx = r["states"]["crusted"]
        ft, fg, fpx = r["states"]["flared"]
        allb = set(bones)
        st = dict(boxes=r["n"], overlaps=len(r["overlaps"]),
                  buried_crusted=E.buried_in_state(m._boxes, allb - set(flare)),
                  buried_flared=E.buried_in_state(m._boxes, allb - set(crust)),
                  dropped_hidden=r["dropped"], glow_px_crusted=cpx, glow_px_flared=fpx,
                  boxes_crust=sum(1 for b in m._boxes if b[0].startswith("crust_")),
                  boxes_flare=sum(1 for b in m._boxes if b[0].startswith("flare_")),
                  weapon=dict(name=wname, boxes=wr["n"], overlaps=len(wr["overlaps"]), glow_px=wgpx,
                              buried=E.buried_in_state(wm._boxes, {"weapon"})),
                  height_blocks=round(max(c["origin"][1] + c["size"][1] for b in r["geo"]["minecraft:geometry"][0]["bones"]
                                          if not b["name"].startswith("flare") for c in b.get("cubes", [])) / 16, 2))
        stats[opt] = st
        print("ash_thrall", opt, st)
        rows = []
        for name, yaw, pitch, state in (("front34", 205, 15, "crusted"), ("side", 270, 6, "crusted"), ("back34", 25, 15, "crusted"),
                                        ("flared_front34", 205, 15, "flared"), ("flared_back34", 25, 15, "flared")):
            tex, gl, hide = (ct, cg, flare) if state == "crusted" else (ft, fg, crust)
            img = E.render(E.faces_of(r["geo"], tex, gl, hide=hide, weapon=weapon, weapon_pose=wpose), yaw, pitch)
            img.save(of / f"render_{name}.png")
            rows.append(img)
        # 武器だけ / 握った手の拡大
        wimg = E.render(E.vx.load_geo(wr["geo"], wtex, wgl), 30, 12, size=(180, 360))
        wimg.save(wf / "render.png")
        hand_geo = filtered(r["geo"], {"forearm_r", "crust_fore_r"})
        wshort = copy.deepcopy(wr["geo"])
        for b in wshort["minecraft:geometry"][0]["bones"]:
            b["cubes"] = [c for c in b["cubes"] if -6 <= c["origin"][1] <= 6]
        himg = E.render(E.faces_of(hand_geo, ct, cg, weapon=(wshort, wtex, wgl), weapon_pose=wpose), 230, 20, size=(180, 360))
        both = E.pair(E.label(wimg, "武器だけ"), E.label(himg, "握った手 (拡大)"))
        both.save(of / "render_weapon.png")
        rows.append(both)
        d1 = E.render(E.faces_of(r["geo"], E.lava_light(ct), cg, hide=flare, weapon=(wr["geo"], E.lava_light(wtex), wgl), weapon_pose=wpose), 205, 15, size=(180, 360))
        d2 = E.render(E.faces_of(r["geo"], E.lava_light(ft), fg, hide=crust, weapon=(wr["geo"], E.lava_light(wtex), wgl), weapon_pose=wpose), 205, 15, size=(180, 360))
        dd = E.pair(E.label(d1, "殻"), E.label(d2, "燃え上がり"))
        dd.save(of / "render_lava_light.png")
        rows.append(dd)
        cols.append((LABELS[opt], rows))
    (folder / "stats.json").write_text(json.dumps(stats, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    E.vx.sheet("ash_thrall ラフ (灰被り: 殻の姿と燃え上がった姿 + 鍛冶の道具)", cols,
               note="行: 殻 正面3/4 / 殻 横 / 殻 背面3/4 / 燃え上がり 正面3/4 / 燃え上がり 背面3/4 / 武器だけ・握った手 / 溶岩の暗がり").save(E.REVIEW / "ash_thrall.png")
    return stats


if __name__ == "__main__":
    run()
