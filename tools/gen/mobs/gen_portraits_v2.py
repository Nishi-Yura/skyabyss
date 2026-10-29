#!/usr/bin/env python3
"""対話画面の肖像 v2: NPC v2 の低ポリの人 (tools/rough/lowpoly/npc2/cast.py の 35 人) から、128x128 の肖像を描き直す。

  python -I tools/gen/mobs/gen_portraits_v2.py [名 ...]          -> build/portraits_v2/new/<肖像名>_<表情>.png
  python -I tools/gen/mobs/gen_portraits_v2.py --install [名 ...] -> さらに assets/skyabyss/textures/gui/npc/face/ へ入れる
  python -I tools/gen/mobs/gen_portraits_v2.py --sheet            -> docs/review/portraits_v2/ に 前 | 後 の一覧

描き方 (語り部の肖像 = tools/gen/mobs/gen_portraits.py の案 A と同じ絵の決まり):
  1. 下絵: 人のメッシュ (体 + 持ち物) を 4 倍の大きさで前 3/4 の胸から上に写す。画素ごとに 材質・滑らかな法線・奥行き・絵の色 を取る。
     顔の目・眉・口・鼻の下の影のドットは描かない (ここで描き直すため)。形のある眉と眼鏡の針金も写さない (描き直す)。
  2. 塗り直し: 4x4 の多数決で材質を決めて 128 に落とし、材質ごとに案 A の 5 段の色 (gen_portraits.make_ramp) を作る。
     明るさ = 左上手前の光と法線 (案 A と同じ式) + 絵の明暗の差 + 光の向きの落ち影、ディザ (4x4 Bayer)。右の縁に逆光。
  3. 顔: 目・眉・口・鼻の穴・眼鏡・眼帯・覆面を、案 A の描き方 (gen_portraits の eye / mouth と同じ線と段) で、
     メッシュの目と口の位置 (3/4 で奥の目は細く) に重ねる。表情 neutral / smile / grave はここで変える。
  4. 背景: 案 A の暗い上下の階調 + 光の暈 (人ごとの色は前の肖像の色の表のまま。生き残り 5 人は新しく決めた)。
"""
from __future__ import annotations

import argparse
import copy
import math
import os
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/npc2"))
import gen_portraits as GP  # noqa: E402
from gen_portraits import ell, poly, rrect, bez, stroke, taper  # noqa: E402
import gen_attendant_portraits as GAP  # noqa: E402
import kit as K  # noqa: E402
import cast as C  # noqa: E402
import cast_folk  # noqa: E402,F401  (土地の町の 8 人の選んだ案)
import cast_moon  # noqa: E402,F401  (六章の人の選んだ案)
import build as B  # noqa: E402
from lpkit import rot_x, rot_y  # noqa: E402

N = GP.N
SS = 4
FACES = GP.FACES
FACE_DIR = Path(GP.FACE_DIR)
OUT = ROOT / "docs/review/portraits_v2"          # 前 | 後 の一覧
WORK = ROOT / "build/portraits_v2"                 # 前 (old/) と新しい絵 (new/) の置き場 (git に入れない)
YAW, PITCH = 200.0, 4.0        # 前 3/4 (少し左を向く = 光の側へ)、少し上から
SCALE = 7.4                    # 128 の絵で 1 単位あたりの画素
FS = SCALE / 6.0               # 顔の部品の大きさの倍率
EYE_AT = (64.0, 60.0)          # 両目の中ほどを置く画素

# 肖像の名 (DialogueScreen: textures/gui/npc/face/<役のパス>_<表情>.png)
GUESTS = {"guest_apprentice", "guest_bellkeeper", "guest_fisher", "guest_bard", "guest_child", "guest_weaver", "guest_ash_apprentice",
          "guest_herbalist", "guest_sailor", "guest_mother", "guest_baker",
          # 土地の町の 8 人 (2026-10-28 から自分の体。cast_folk)
          "guest_island_ferryman", "guest_tide_ferryman", "guest_inner_ferryman", "guest_tide_lightkeeper", "guest_storm_elder",
          "guest_storm_hutkeeper", "guest_ember_survivor", "guest_ember_watch",
          # 六章の人 (cast_moon)
          "guest_moon_past1", "guest_moon_past2", "guest_moon_past3"}


def file_stem(name):
    if name == "artisan" or name in GUESTS:
        return name
    return "attendant_" + name


# 背景の色 (前の肖像の表 bg と同じ。生き残り 5 人と荷運びは新しく)
def _guest_bg():
    try:
        import gen_town_guests as GT
        return {"guest_" + k: v["bg"] for k, v in GT.G.items()} if hasattr(GT, "G") else {}
    except Exception:
        return {}


BG_EXTRA = {
    "survivor_bone": (236, 170, 80),     # 灯り番ミオ: 角灯の琥珀
    "survivor_salt": (110, 180, 200),    # 測量士ハヤセ: 塩の青白
    "survivor_storm": (120, 150, 200),   # 薬師シズ: 嵐の青
    "survivor_ash": (210, 96, 56),       # 隊長トウヤ: 灰の熾火
    "abyss_porter": (170, 130, 80),      # 荷運びロク: 荷の革
    "artisan": (240, 130, 60),           # 旅の職人 (前の肖像と同じ炉の色)
    "food_vendor": (210, 120, 80),       # 食料屋: 腸詰めの赤み
    "outfitter": (150, 160, 176),        # 武具屋: 鉄の灰青
    "material_trader": (90, 170, 190),   # 素材屋: 鉱石の青い筋
    "guest_apprentice": (90, 150, 180), "guest_bellkeeper": (200, 170, 90), "guest_fisher": (80, 140, 170), "guest_bard": (190, 90, 120),
    "guest_child": (110, 120, 170), "guest_weaver": (150, 110, 180), "guest_ash_apprentice": (230, 110, 50), "guest_herbalist": (120, 170, 100),
    "guest_sailor": (70, 120, 170), "guest_mother": (200, 140, 110), "guest_baker": (220, 170, 100),
    "guest_island_ferryman": (170, 150, 100), "guest_tide_ferryman": (90, 150, 190), "guest_inner_ferryman": (90, 150, 150),
    "guest_tide_lightkeeper": (230, 200, 110), "guest_storm_elder": (170, 110, 120), "guest_storm_hutkeeper": (170, 110, 80),
    "guest_ember_survivor": (220, 110, 50), "guest_ember_watch": (200, 80, 60),
    "guest_moon_past1": (130, 170, 230),     # 前のループの自分: 星の角灯の青白
    "guest_moon_past2": (150, 160, 180),     # 前のループの自分 (二人目): 色あせた鉄の灰青
    "guest_moon_past3": (160, 190, 235),     # 前のループの自分 (三人目): 月の砂時計の青白
}


def bg_color(name):
    if name in BG_EXTRA:
        return BG_EXTRA[name]
    if name in GAP.R:
        return GAP.R[name]["bg"]
    return (120, 140, 170)


def background(name):
    b = np.array(bg_color(name), float)
    GP.BG[name] = dict(top=tuple((b * 0.12).astype(int)), bot=tuple((b * 0.32).astype(int)), glow=tuple(b.astype(int)),
                       b=tuple((b * 0.5).astype(int)), b2=tuple((b * 0.65).astype(int)), c=tuple(b.astype(int)))
    return GP.background("a", name)


def rim_color(name):
    b = np.array(bg_color(name), float)
    return tuple(int(x) for x in np.clip(b * 0.4 + np.array([255, 236, 210]) * 0.6, 0, 255))


# ====================================================================== 1. 下絵 (メッシュを写す)

DROP_PARTS = {"brow-1", "brow1", "spec-1", "spec1", "bridge", "kasa_cord-1", "kasa_cord1"}


def no_face_dots(c):
    """顔のドット: 目・眉・口・鼻の影は描かず、印 (傷・そばかす・しわ・眼帯) だけ残す。眼帯は顔の絵で描き直すので外す。"""
    f = c.get("face", {})
    out = []
    for m in f.get("marks", []):
        rows = m[0]
        if any("E" in r for r in rows):
            continue
        out.append(m)
    return out


LIFT = {"r": {"armR": (34.0, 0.0, 6.0), "foreR": (38.0, 0.0, 0.0)}, "l": {"armL": (34.0, 0.0, -6.0), "foreL": (38.0, 0.0, 0.0)}}
# 肖像だけの姿勢 (胸から下に持つ役の道具を、胸の高さへ持ち上げて見せる)。名 -> 持ち上げる手 ("r" / "l" / "rl")
LIFT_SIDE = {}


def portrait_pose(c, props):
    """役の道具が胸より下 (絵の外) にあるとき、その手の腕と肘を前へ上げる。"""
    side = LIFT_SIDE.get(c["name"])
    if side is None:
        side = ""
        for sock, pf in props.items():
            if sock == "prop_back":
                continue
            v = np.concatenate([f.verts for f in pf])
            if v[:, 1].max() < 19.5 or (v[:, 1].max() - v[:, 1].min() < 12 and v[:, 1].mean() < 16.5):
                if c.get("anim") == "hold2":
                    side += "rl"
                else:
                    side += "r" if sock == "prop_r" else "l"
    pose = {}
    ymax = max([np.concatenate([f.verts for f in pf])[:, 1].max() for s_, pf in props.items() if s_ != "prop_back"] or [99])
    if c.get("anim") == "hold2":
        k = 0.42          # 両手で抱える物は顔を隠さないよう少しだけ
    elif ymax < 14.0:
        k = 1.35          # 提げた物 (槌・瓶・桶) は胸の前へ
    else:
        k = 1.0
    for s in set(side):
        for b, r in LIFT[s].items():
            pose[b] = tuple(x * k for x in r)
    return pose


def build_faces(name):
    c, body, bones, props = B.build(name)
    c["name"] = name
    faces = []
    for f in body:
        pt = getattr(f, "part", None)
        if pt in DROP_PARTS:
            continue
        if f.bone == "head" and f.mat == "metal" and pt in ("arm-1", "arm1"):
            continue
        faces.append(f)
    for sock, pf in props.items():
        faces += pf
    faces = [copy.copy(f) for f in faces]
    for f in faces:
        f.verts = f.verts.copy()
    real = K.face_pixels
    K.face_pixels = no_face_dots
    try:
        size, atlas, glow = B.paint(c, faces, 256)
    finally:
        K.face_pixels = real
    faces = B.grow(c, faces)
    pose = portrait_pose(c, props)
    if pose:
        # 道具が顔 (顎より上・頭の幅) にかからない所まで持ち上げを弱める
        cam = Cam(c)
        gb = B.grow_bones(c, bones)
        hx, _ = cam.proj(grow_pt(c, (0.0, 25.0, -3.0)))
        chin, _ = cam.proj(grow_pt(c, (0.0, 21.0, -3.0)))
        half = 4.6 * c.get("head_k", B.HEAD_K) * SCALE
        pf = [f for f in faces if f.bone.startswith("prop_") and f.bone != "prop_back"]
        for k in (1.0, 0.8, 0.6, 0.45, 0.3, 0.15, 0.0):
            p2 = {b: tuple(x * k for x in r) for b, r in pose.items()}
            moved = B.CK.pose_faces(pf, p2, gb) if k > 0 else pf
            v = np.concatenate([f.verts for f in moved])
            q = np.array([cam.proj(p)[0] for p in v])
            hit = (q[:, 1] < chin[1] + 4) & (np.abs(q[:, 0] - hx[0]) < half + 3)
            if not hit.any():
                break
        if k > 0:
            faces = B.CK.pose_faces(faces, {b: tuple(x * k for x in r) for b, r in pose.items()}, gb)
    return c, faces, atlas, glow


def grow_pt(c, p):
    k = c.get("head_k", B.HEAD_K)
    return B.HEAD_PIVOT + (np.asarray(p, float) - B.HEAD_PIVOT) * k


class Cam:
    def __init__(self, c, yaw=YAW, pitch=PITCH, scale=SCALE):
        self.V = rot_x(pitch) @ rot_y(yaw)
        self.sc = scale
        hs = K.head_spec(c.get("head", "avg"), c.get("head_dy", 0.0))
        zf = K.prof_at(hs, K.EYE_Y)[1]
        mid = grow_pt(c, (0.0, K.EYE_Y, zf))
        self.cen = self.V @ mid

    def proj(self, p, ss=1):
        q = self.V @ np.asarray(p, float)
        x = (q[0] - self.cen[0]) * self.sc + EYE_AT[0]
        y = -(q[1] - self.cen[1]) * self.sc + EYE_AT[1]
        return np.array([x * ss, y * ss]), q[2]


def smooth_normals(faces):
    """同じ骨・材質・位置の頂点の法線を面積で平均 (角が 75 度より立つ所は面の法線のまま)。"""
    acc = {}
    keys = []
    for f in faces:
        n = f.normal / (np.linalg.norm(f.normal) + 1e-12)
        e = np.cross(f.verts[1] - f.verts[0], f.verts[-1] - f.verts[0])
        a = max(np.linalg.norm(e), 1e-6)
        ks = []
        for p in f.verts:
            k = (f.bone, f.mat, tuple(np.round(p, 2)))
            acc[k] = acc.get(k, 0) + n * a
            ks.append(k)
        keys.append(ks)
    out = []
    for f, ks in zip(faces, keys):
        n = f.normal / (np.linalg.norm(f.normal) + 1e-12)
        vn = []
        for k in ks:
            s = acc[k] / (np.linalg.norm(acc[k]) + 1e-12)
            vn.append(s if np.dot(s, n) > 0.26 else n)
        out.append(np.array(vn))
    return out


def raster(faces, atlas, glow, cam, size=N * SS):
    """4 倍の下絵: 面の番号・奥行き・カメラ空間の滑らかな法線・絵の色・光る印。"""
    W = H = size
    zbuf = np.full((H, W), -1e9)
    fid = np.full((H, W), -1, int)
    nrm = np.zeros((H, W, 3))
    tex = np.zeros((H, W, 3))
    gl = np.zeros((H, W), bool)
    S = atlas.shape[0]
    V = cam.V
    vns = smooth_normals(faces)
    cam_dir = V.T @ np.array([0, 0, 1.0])
    for fi, (f, vn) in enumerate(zip(faces, vns)):
        cv = f.verts @ V.T
        sx = (cv[:, 0] - cam.cen[0]) * cam.sc * SS + EYE_AT[0] * SS
        sy = -(cv[:, 1] - cam.cen[1]) * cam.sc * SS + EYE_AT[1] * SS
        sz = cv[:, 2]
        cn = vn @ V.T
        flip = np.dot(f.normal, cam_dir) < 0
        if flip:
            cn = -cn
        for (a, b, c) in f.tris():
            xs, ys = sx[[a, b, c]], sy[[a, b, c]]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-9) & (l1 >= -1e-9) & (l2 >= -1e-9)
            if not inside.any():
                continue
            z = l0 * sz[a] + l1 * sz[b] + l2 * sz[c]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = inside & (z > sub)
            if not upd.any():
                continue
            u = l0 * f.uv[a, 0] + l1 * f.uv[b, 0] + l2 * f.uv[c, 0]
            v = l0 * f.uv[a, 1] + l1 * f.uv[b, 1] + l2 * f.uv[c, 1]
            ui = np.clip(np.floor(u).astype(int), 0, S - 1)
            vi = np.clip(np.floor(v).astype(int), 0, S - 1)
            n3 = l0[..., None] * cn[a] + l1[..., None] * cn[b] + l2[..., None] * cn[c]
            sub[upd] = z[upd]
            fid[y0:y1 + 1, x0:x1 + 1][upd] = fi
            nrm[y0:y1 + 1, x0:x1 + 1][upd] = n3[upd]
            tex[y0:y1 + 1, x0:x1 + 1][upd] = atlas[vi, ui, :3][upd]
            gl[y0:y1 + 1, x0:x1 + 1][upd] = glow[vi, ui][upd]
    return fid, zbuf, nrm, tex, gl


def downsample(faces, fid, zbuf, nrm, tex, gl):
    """4x4 の多数決で材質を決め、その材質の画素だけで法線・色・奥行きを平均する。"""
    mats = sorted({f.mat for f in faces})
    mid = {m: i for i, m in enumerate(mats)}
    fm = np.array([mid[f.mat] for f in faces] + [-1])
    lab = fm[fid]                               # -1 = 背景
    L = len(mats)
    blk = lambda a: a.reshape(N, SS, N, SS, *a.shape[2:]).sum((1, 3))
    cnt = np.stack([blk((lab == i).astype(np.int32)) for i in range(L)])      # (L, N, N)
    cov = blk((lab >= 0).astype(np.int32))
    best = cnt.argmax(0)
    figure = cov >= (SS * SS) // 2
    full = np.repeat(np.repeat(best, SS, 0), SS, 1)
    sel = (lab == full) & (lab >= 0)
    w = blk(sel.astype(float))
    w1 = np.maximum(w, 1)
    n = blk(nrm * sel[..., None]) / w1[..., None]
    n /= np.linalg.norm(n, axis=2, keepdims=True) + 1e-9
    t = blk(tex * sel[..., None]) / w1[..., None]
    z = np.where(sel, zbuf, -1e9).reshape(N, SS, N, SS).max((1, 3))
    g = blk((gl & sel).astype(np.int32)) >= 4
    label = np.where(figure, best, -1)
    return mats, label, n, t, z, g


# ====================================================================== 2. 塗り直し

LIGHT = GP.LIGHT          # 画面の (右, 下, 手前)


def blur_within(a, m, sigma):
    """m の中だけでぼかす (外の値を混ぜない)。"""
    ws = _gblur(m.astype(float), sigma)
    if a.ndim == 2:
        return np.where(m, _gblur(a * m, sigma) / np.maximum(ws, 1e-4), a)
    return np.stack([np.where(m, _gblur(a[..., k] * m, sigma) / np.maximum(ws, 1e-4), a[..., k]) for k in range(a.shape[2])], -1)


def _gblur(a, sigma):
    r = max(1, int(math.ceil(sigma * 3)))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    p = np.pad(a, r, mode="edge")
    p = sum(k[i] * p[:, i:i + a.shape[1]] for i in range(2 * r + 1))
    return sum(k[i] * p[i:i + a.shape[0], :] for i in range(2 * r + 1))


class Art2(GP.Art):
    """gen_portraits.Art に、下絵の材質の層と、人の影の縁の逆光を足したもの。"""

    def __init__(self, name):
        super().__init__("a", name)
        self.sil = np.zeros((N, N), bool)
        self.sil_rim = None
        self.glowpx = np.zeros((N, N), bool)

    def render(self, bg):
        out = super().render(bg)
        if self.sil_rim is not None:
            m = self.sil & (self.rid >= 0) & (self.rid < self.n_mesh)
            sil = self.sil
            e = m & ~np.roll(sil, -2, axis=1)
            e2 = m & ~np.roll(sil, -3, axis=1) & ~e
            top = m & ~np.roll(sil, 2, axis=0) & ~e
            L = self.cfg["levels"]
            v = self.val + np.tile(GP.BAYER, (N // 4, N // 4)) * self.cfg["dither"] / L * 1.6
            idx = np.clip((v * L).astype(int), 0, L - 1)
            col = np.array(self.sil_rim, float)
            for sel, k in ((e & (idx >= 1), 0.62), (e2 & (idx >= 2), 0.38), (top & (idx >= 2), 0.3)):
                out[sel] = (out[sel] * (1 - k) + col * k).astype(int)
        if self.glowpx.any():
            out[self.glowpx] = np.clip(out[self.glowpx] * 0.6 + np.array([255, 236, 170]) * 0.4, 0, 255).astype(int)
        return out


def lay_mesh(a, mats, label, n, t, z, g, name, c_face):
    """下絵を a (Art2) の層として置く。"""
    sil = label >= 0
    a.sil = sil
    # 法線: 同じ材質の中で少しぼかす (低ポリの面の角をならす)
    ns = n.copy()
    for i in range(len(mats)):
        m = label == i
        if m.sum() > 6:
            ns[m] = blur_within(n, m, 1.1)[m]
    ns /= np.linalg.norm(ns, axis=2, keepdims=True) + 1e-9
    # カメラ空間 (右, 上, 手前) -> 絵の空間 (右, 下, 手前)
    nx, ny, nz = ns[..., 0], -ns[..., 1], ns[..., 2]
    ndl = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
    val = 0.5 + np.clip((ndl - LIGHT[2]) * 0.8, -0.44, 0.24)
    # 落ち影: 光の側 (左上) の近い画素がこちらより手前にあれば影
    d = np.array([-LIGHT[0], -LIGHT[1]])
    d /= np.linalg.norm(d)
    shadow = np.zeros((N, N))
    zz = np.where(sil, z, -1e9)
    for k in range(1, 9):
        dx, dy = int(round(d[0] * k)), int(round(d[1] * k))
        occ = np.full((N, N), -1e9)
        # 画素 (y, x) から光の方向 (左上) へ k 進んだ所 = (y - |dy|, x - |dx|)
        ady, adx = abs(dy), abs(dx)
        occ[ady:, adx:] = zz[: N - ady, : N - adx]
        dz = occ - zz
        hit = sil & (dz > 0.55 + 0.06 * k)
        shadow = np.maximum(shadow, hit * (1.0 - k / 11.0))
    shadow = GP.blur(shadow, 0.8) * sil
    val = val - 0.34 * shadow
    # 奥行きの段差 (手前の物の縁の奥側を一段暗く)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        nb = np.roll(np.roll(zz, dy, 0), dx, 1)
        jump = sil & (nb - zz > 1.3)
        val = val - 0.16 * jump
    # 絵の明暗の差 (縞・継ぎ目・ボタン・髪の筋・そばかす)
    lum = t @ np.array([0.3, 0.55, 0.15])
    for i, m_ in enumerate(mats):
        m = label == i
        if not m.any():
            continue
        med = np.median(t[m], axis=0)
        ml = float(med @ np.array([0.3, 0.55, 0.15]))
        lm = blur_within(lum, m, 0.6) if m.sum() > 6 else lum
        dl = (lm - ml) / max(ml, 40.0)
        if m_ == "skin":
            kf = 0.5 if (c_face.get("stubble") or c_face.get("mask") is not None) else 0.0
            dl = np.minimum(dl, 0.0)
        elif m_ in ("hair", "beard", "brow"):
            kf = 0.32
        else:
            kf = 0.42
        val = np.where(m, val + np.clip(dl, -0.5, 0.5) * kf, val)
        key = "m_" + m_
        base = tuple(int(x) for x in np.clip(med * (0.96 if m_ == "skin" else 1.04), 0, 255))
        a.ramps[key] = GP.make_ramp(base, "a")
        pid = len(a.names)
        a.names.append(key)
        a.rid[m] = pid
        a.own[m] = pid
    a.n_mesh = len(a.names)
    a.val[sil] = val[sil]
    # 色の大きく違う画素 (頬の赤み・覆面・酔いの鼻・傷): 材質の色との差を色ごと残す
    for i, m_ in enumerate(mats):
        m = label == i
        if m_ != "skin" or not m.any() or c_face.get("mask") is None:
            continue
        med = np.median(t[m], axis=0)
        dc = t - med
        chrom = np.linalg.norm(dc - dc.mean(axis=2, keepdims=True), axis=2)
        odd = m & (chrom > 14)
        if odd.any():
            pid = len(a.names)
            key = "m_skin_tint"
            tint = np.median(t[odd], axis=0)
            a.ramps[key] = GP.make_ramp(tuple(int(x) for x in np.clip(tint * 1.04, 0, 255)), "a")
            a.names.append(key)
            a.rid[odd] = pid
            a.own[odd] = pid
            a.n_mesh = len(a.names)
    a.sil_rim = rim_color(name)
    a.glowpx = g & sil
    return a


# ====================================================================== 3. 顔

def skin_key(a):
    return "m_skin"


def ensure(a, key, rgb):
    if key not in a.ramps:
        a.ramps[key] = GP.make_ramp(tuple(int(x) for x in rgb), "a")
    return key


def face_points(c, cam):
    hs = K.head_spec(c.get("head", "avg"), c.get("head_dy", 0.0))
    pz = lambda y, dz=0.0: K.prof_at(hs, y)[1] + dz
    P = {}
    ex = 1.5
    for s in (-1, 1):
        y = K.EYE_Y + 0.1
        z = pz(y, 0.25)
        inner, _ = cam.proj(grow_pt(c, (s * (ex - 0.95), y, z + 0.1)))
        outer, _ = cam.proj(grow_pt(c, (s * (ex + 0.95), y, z + 0.45)))
        cen, _ = cam.proj(grow_pt(c, (s * ex, y, z + 0.18)))
        P[s] = dict(c=cen, w=abs(outer[0] - inner[0]), inner=inner, outer=outer)
    P["mouth"], _ = cam.proj(grow_pt(c, (0.0, 23.25, pz(23.25, 0.1))))
    P["nose"], _ = cam.proj(grow_pt(c, (0.0, 24.45, pz(24.45, -0.55))))
    mw = []
    for s in (-1, 1):
        q, _ = cam.proj(grow_pt(c, (s * 0.95, 23.3, pz(23.3, 0.2))))
        mw.append(q)
    P["mouth_w"] = abs(mw[1][0] - mw[0][0]) / 2
    P["mouth_l"], P["mouth_r"] = mw[0], mw[1]
    return P


EYE_KIND = {
    # 縦の大きさ, 瞳の大きさ, 上まぶたの傾き (外が上 +), 上まぶたの重さ (0..1), まつげ, 下のしわ
    "calm": (1.0, 1.0, 0.0, 0.15, False, False), "round": (1.15, 1.1, 0.0, 0.0, False, False),
    "narrow": (0.72, 0.9, -0.2, 0.25, False, False), "sleepy": (0.9, 0.95, -0.4, 0.55, False, False),
    "kind": (0.95, 1.0, -0.5, 0.1, False, False), "sharp": (0.8, 0.9, 0.9, 0.2, False, False),
    "lady": (1.05, 1.05, 0.3, 0.05, True, False), "child": (1.3, 1.3, 0.0, 0.0, False, False),
    "old": (0.85, 0.9, -0.6, 0.35, False, True), "closed": (0.6, 0.8, 0.0, 1.0, False, False),
    "glint": (1.0, 1.0, 0.4, 0.1, False, False), "patch": (1.0, 1.0, 0.0, 0.15, False, False),
}


def ek(pt):
    return FS * max(0.62, min(1.05, pt["w"] / (10.6 * FS)))


def eye2(a, c, pt, side, expr, kind, skin, iris, near):
    """gen_portraits.eye と同じ線・段で、目の型と 3/4 の細さを入れた目。side: 画面上の左 -1 / 右 +1。"""
    x, y = pt["c"]
    k = ek(pt)      # 奥の目は細い
    ry_k, ir_k, tilt, heavy, lash, bag = EYE_KIND.get(kind, EYE_KIND["calm"])
    if c["face"].get("drunk") and expr == "neutral":
        heavy = max(heavy, 0.6)
    rx, ry = 5.0 * k, 3.4 * ry_k * FS
    outer = side          # 外の端の向き (画面)
    a.paint(ell(x, y, rx, ry), "sclera", 1.5, v=0.5)
    ir = ell(x - 0.35 * side * 0 + (0.4 if near else -0.4) * -1, y + 0.2, 2.6 * ir_k * k, 2.9 * ir_k * FS) & ell(x, y, rx, ry)
    a.flatpaint(ir, tuple(int(v) for v in iris))
    a.flatpaint(ell(x + (0.4 if near else -0.4) * -1, y + 0.3, 1.35 * ir_k * k, 1.5 * ir_k * FS) & ell(x, y, rx, ry), (14, 12, 18))
    a.flatpaint(ell(x - 1.0 * k, y - 1.0, 0.9, 0.9), (255, 255, 255))
    if kind == "glint":
        a.flatpaint(ell(x + 1.2 * k, y + 1.1, 0.5, 0.5), (230, 250, 240))
    # 上まぶた (型の重さ)
    if heavy > 0.05 and expr != "smile":
        hh = ry * 2 * heavy
        a.paint(ell(x, y - ry - 0.6 + hh * 0.9 - ry, rx + 1.2, ry + 0.4) & ell(x, y, rx + 1.6, ry + 1.6), skin, 1.5, v=0.42)
    # 表情
    lo = (x - rx * 1.1 * 1, y + tilt * -outer * 0)
    if expr == "smile":
        a.paint(ell(x, y + 3.6 * ry_k, rx * 1.25, 2.6), skin, 1.5, v=0.52)
        a.paint(stroke(bez((x - rx * 1.05, y + 2.4), (x, y - 0.6 - 0.4 * ry_k), (x + rx * 1.05, y + 2.4), 10), 0.9), "line", 0.5, v=0.2)
        a.paint(stroke(bez((x - rx * 1.1, y - 0.2 - tilt * outer * 0.0), (x, y - ry - 1.8), (x + rx * 1.1, y - 0.2), 10), 1.1), "line", 0.5, v=0.15)
    elif expr == "grave":
        a.paint(ell(x, y - ry - 0.2, rx * 1.3, 3.0), skin, 1.5, v=0.42)
        y0 = y - 0.5
        a.paint(stroke([(x - rx * 1.15, y0 - (0.9 * side if side < 0 else 0.0) * 0.6), (x, y0 - 0.5), (x + rx * 1.15, y0 + (0.0 if side < 0 else -0.5))], 1.1),
                "line", 0.5, v=0.12)
    else:
        # 上まぶたの線 (外の端を tilt だけ上げ下げ)
        ax0, ay0 = x - rx * 1.15, y + 0.2 - (tilt if side < 0 else 0) * 1.4
        ax1, ay1 = x + rx * 1.15, y + 0.2 - (tilt if side > 0 else 0) * 1.4
        top = y - ry * 1.55 - 0.6 + heavy * ry * 1.2
        a.paint(stroke(bez((ax0, ay0), (x, top), (ax1, ay1), 10), 1.1), "line", 0.5, v=0.15)
    if lash:
        ox = x + side * rx * 1.05
        a.paint(taper([(ox, y - 1.2), (ox + side * 2.4, y - 2.8)], 1.3, 0.6), "line", 0.5, v=0.1)
    if bag:
        a.paint(stroke(bez((x - rx * 0.9, y + ry + 1.4), (x, y + ry + 2.4), (x + rx * 0.9, y + ry + 1.4), 8), 0.8), "line", 0.5, v=0.42)


BROW_KIND = {
    # 内の高さ, 山, 外の高さ (上 = -), 太さ
    "soft": (0.4, -2.0, 0.4, 1.0), "flat": (0.0, -0.8, 0.0, 1.05), "stern": (1.4, -0.8, -1.2, 1.15),
    "worried": (-1.6, -1.8, 1.2, 0.95), "arched": (0.4, -3.4, 0.8, 0.9), "thick": (0.2, -1.6, 0.4, 1.5),
    "none": (0.2, -1.8, 0.8, 1.6), "high": (-0.8, -3.0, -0.2, 0.9),
}


def brows2(a, c, P, expr, color, cam):
    f = c["face"]
    kind = f.get("brow", "soft")
    b3 = c.get("brow3d")
    if kind == "none" and not b3:
        return
    yin, ymid, yout, wk = BROW_KIND.get(kind, BROW_KIND["soft"])
    if b3:
        wk = max(wk, 1.55)
    if expr == "smile":
        yin, ymid, yout = yin - 0.9, ymid - 1.6, yout - 0.3
    elif expr == "grave":
        yin, ymid, yout = yin + 1.6 + (0 if kind != "worried" else -2.6), ymid + 1.0, yout + 1.2 + (0 if kind != "worried" else 1.0)
    for s in (-1, 1):
        pt = P[s]
        x, y = pt["c"]
        sd = -1 if pt["c"][0] < P["nose"][0] else 1      # 画面の向き
        k = ek(pt)
        cy = y - 6.4 * FS
        xin = x - sd * 5.0 * k
        xout = x + sd * 6.4 * k
        pts = bez((xin, cy + yin), ((xin + xout) / 2, cy + ymid), (xout, cy + yout), 10)
        w0 = (2.8 if not c.get("build") == "lady" else 2.1) * wk
        w1 = (1.5 if not c.get("build") == "lady" else 1.0) * wk
        a.paint(taper(pts, w0, w1), color, 0.8)
        if b3 or kind == "none":
            # 年寄りの房 (外へはねる)
            a.paint(taper([(xout - sd * 1.0, cy + yout + 0.4), (xout + sd * 3.2, cy + yout + 2.2)], 1.6, 0.7), color, 0.5)


def mouth2(a, c, P, expr, skin):
    f = c["face"]
    kind = f.get("mouth", "neutral")
    if f.get("mask") is not None or kind == "none":
        return
    cx, cy = P["mouth"]
    w = max(3.2, P["mouth_w"] * 1.05)
    if c.get("build") == "lady":
        w *= 0.88
    lip = ensure(a, "lip_" + c["name"], f.get("lip", GP.MATS["lip"]))
    beard = c.get("beard") in ("full", "long")
    if expr == "smile" or (expr == "neutral" and kind in ("grin",)):
        open_ = expr == "smile" and kind in ("grin", "smile", "o", "wide") or kind == "grin"
        a.paint(stroke(bez((cx - w, cy - 1.8), (cx, cy + 3.8), (cx + w, cy - 1.8), 14), 1.7), lip, 0.6, v=0.22)
        a.paint(stroke([(cx - w - 1.0, cy - 3.0), (cx - w - 0.2, cy - 1.8)], 0.9), "line", 0.5, v=0.2)
        a.paint(stroke([(cx + w + 1.0, cy - 3.0), (cx + w + 0.2, cy - 1.8)], 0.9), "line", 0.5, v=0.2)
        if open_:
            a.paint(ell(cx, cy + 0.6, w * 0.55, 0.85), "sclera", 0.6, v=0.6)
    elif expr == "grave" or kind == "frown":
        dn = 1.8 if expr == "grave" else 1.2
        a.paint(stroke(bez((cx - w, cy + dn), (cx, cy - dn), (cx + w, cy + dn), 14), 1.6), lip, 0.6, v=0.18)
        if not beard:
            a.paint(ell(cx, cy + 3.0, 2.8, 1.0), skin, 1, v=0.3)
    elif kind == "o":
        a.paint(ell(cx, cy + 0.6, 1.9, 1.7), lip, 0.6, v=0.15)
        a.paint(ell(cx, cy + 0.9, 1.0, 0.9), "line", 0.5, v=0.05)
    elif kind in ("smile", "smirk"):
        r = 1.3 if kind == "smile" else 0.0
        a.paint(stroke(bez((cx - w + 0.8, cy - 0.4 * (1 if kind == "smile" else 0) ), (cx, cy + 1.3), (cx + w - 0.6, cy - 1.0 - r * 0.2), 14), 1.6), lip, 0.6, v=0.22)
        if not beard:
            a.paint(ell(cx, cy + 3.0, 3.0, 1.0), lip, 0.8, v=0.55)
    else:
        ww = w * (0.6 if kind == "small" else 0.9 if kind == "lips" else 1.0)
        a.paint(stroke(bez((cx - ww + 0.8, cy + 0.2), (cx, cy + 1.0), (cx + ww - 0.8, cy + 0.2), 14), 1.6), lip, 0.6, v=0.22)
        if kind == "lips" or not beard:
            a.paint(ell(cx, cy + 2.8, 2.8 if kind != "lips" else 3.2, 1.0), lip, 0.8, v=0.55 if kind != "lips" else 0.4)


def nostrils(a, c, P):
    if c["face"].get("mask") is not None:
        return
    x, y = P["nose"]
    a.paint(ell(x - 1.4, y, 0.9, 0.7), "line", 0.5, v=0.12)
    a.paint(ell(x + 1.6, y, 0.9, 0.7), "line", 0.5, v=0.12)


def specs2(a, c, P):
    if not c.get("specs"):
        return
    metal = ensure(a, "specs_" + c["name"], c["pal"].get("metal", (190, 160, 80)))
    lens = ensure(a, "lens", GP.MATS["lens"])
    cs = []
    for s in (-1, 1):
        pt = P[s]
        x, y = pt["c"]
        k = ek(pt)
        rx, ry = 7.0 * k, 5.4
        ring = ell(x, y + 0.2, rx, ry) & ~ell(x, y + 0.2, rx - 1.6, ry - 1.5)
        a.paint(ring, metal, 0.8, v=0.68)
        a.flatpaint(poly([(x - rx * 0.55, y - 2.4), (x - rx * 0.25, y - 3.4), (x - rx * 0.45, y - 1.0)]), (236, 248, 252))
        cs.append((x, y, rx))
    (x0, y0, r0), (x1, y1, r1) = sorted(cs)
    a.paint(stroke([(x0 + r0 - 0.6, y0 - 0.8), (x1 - r1 + 0.6, y1 - 0.8)], 1.1), metal, 0.5, v=0.6)
    # つる (耳へ)
    xo, yo, ro = cs[1] if cs[1][0] > cs[0][0] else cs[0]
    a.paint(stroke([(xo + ro - 0.4, yo - 0.4), (xo + ro + 4.5, yo + 0.4)], 1.0), metal, 0.5, v=0.45)


def patch2(a, c, P, side_model):
    pt = P[side_model]
    x, y = pt["c"]
    k = ek(pt)
    e = ensure(a, "patch", (34, 28, 30))
    a.paint(ell(x, y + 0.4, 5.8 * k, 4.6), e, 1.5)
    a.paint(stroke([(x - 9 * k, y - 6.5), (x + 9 * k, y + 4.0)], 1.2), e, 0.5, v=0.3)


def mask2(a, c, P):
    m = c["face"].get("mask")
    if m is None:
        return
    key = ensure(a, "mask_" + c["name"], m)
    x0, y0 = P[-1]["c"]
    x1, y1 = P[1]["c"]
    if x0 > x1:
        x0, x1 = x1, x0
    cx, cy = P["nose"]
    top = cy - 1.5
    cover = poly([(x0 - 7.5, top), (x1 + 7.5, top), (x1 + 8.0, top + 12), (cx + 8, top + 19), (cx - 8, top + 19), (x0 - 8.0, top + 12)])
    a.shade(cover, 0, -3, 3, 0.35)
    a.paint(cover, key, 4, tex=GP.knit)
    a.paint(rrect(x0 - 7, top - 0.5, x1 + 7, top + 2.5, 1.5), key, 1.2, v=0.72)


def wrinkles(a, c, P, expr):
    f = c["face"]
    old = c.get("build") == "old" or f.get("eye") == "old"
    if expr == "grave":
        nx = P["nose"][0]
        y = min(P[-1]["c"][1], P[1]["c"][1]) - 8.5
        a.paint(stroke([(nx - 2.4, y), (nx - 2.0, y + 3.2)], 1.0), "line", 0.5, v=0.25)
        a.paint(stroke([(nx + 2.6, y), (nx + 2.2, y + 3.2)], 1.0), "line", 0.5, v=0.25)
    if old:
        for s in (-1, 1):
            pt = P[s]
            x, y = pt["c"]
            sd = -1 if x < P["nose"][0] else 1
            ox = x + sd * pt["w"] * 0.62
            a.paint(stroke([(ox, y + 1.2), (ox + sd * 2.8, y + 2.2)], 0.8), "line", 0.5, v=0.4)
        if c.get("beard") not in ("full", "long"):
            mx, my = P["mouth"]
            w = P["mouth_w"]
            for sd in (-1, 1):
                a.paint(stroke(bez((mx + sd * (w + 1.2), my - 5.5), (mx + sd * (w + 2.4), my - 2.0), (mx + sd * (w + 1.6), my + 1.5), 8), 0.9),
                        "line", 0.5, v=0.42)


def paint_face(a, c, cam, expr):
    P = face_points(c, cam)
    f = c["face"]
    skin = skin_key(a)
    iris = f.get("iris", (92, 64, 44))
    hair = np.array(c["pal"].get("beard", c["pal"]["hair"]), float)
    brow_rgb = f.get("brow_col", hair * (0.62 if hair.mean() < 150 else 0.92))
    if c.get("brow3d"):
        brow_rgb = hair * 0.95
    brow = ensure(a, "brow_" + c["name"], brow_rgb)
    patch_side = None
    for m in f.get("marks", []):
        if any("E" in r for r in m[0]):
            patch_side = m[3] if len(m) > 3 else 1
    near_side = 1 if YAW < 180 else -1
    for s in (-1, 1):
        if patch_side is not None and s == patch_side:
            continue
        kind = f.get("eye", "calm")
        # model +x (人の左) は画面の右
        sd = -1 if P[s]["c"][0] < P["nose"][0] else 1
        eye2(a, c, P[s], sd, expr, kind, skin, iris, near=(P[s]["w"] >= P[-s]["w"]))
    bl = f.get("blush", 0.0)
    if bl >= 0.12 and f.get("mask") is None:
        sk = np.array(c["pal"]["skin"], float)
        tint = np.array(f.get("blush_col", (226, 116, 108)), float)
        key = ensure(a, "blush_" + c["name"], sk * (1 - min(0.6, bl + 0.15)) + tint * min(0.6, bl + 0.15))
        for s in (-1, 1):
            x, y = P[s]["c"]
            sd = -1 if x < P["nose"][0] else 1
            k = ek(P[s])
            a.paint(ell(x + sd * 1.2 * k, y + 5.6 * FS, 3.6 * k, 1.9 * FS), key, 1.0, v=0.5 if s == -1 else 0.42)
    marks = f.get("marks", [])
    if any("f" in r for m in marks for r in m[0]):
        sk = np.array(c["pal"]["skin"], float)
        key = ensure(a, "freckle_" + c["name"], sk * 0.7 + np.array([120, 60, 30]) * 0.3)
        for s in (-1, 1):
            x, y = P[s]["c"]
            sd = -1 if x < P["nose"][0] else 1
            k = ek(P[s])
            for dx, dy in ((-2.2, 4.6), (0.6, 5.4), (2.8, 4.4), (-0.8, 7.0), (1.8, 7.2)):
                a.paint(ell(x + sd * dx * k * 0.8 - sd * 1.0, y + dy * FS, 0.6, 0.6), key, 0.4, v=0.35)
    for m in marks:
        if any("S" in r for r in m[0]):
            sm = m[3] if len(m) > 3 else 1
            x, y = P[sm]["c"]
            sd = -1 if x < P["nose"][0] else 1
            k = ek(P[sm])
            key = ensure(a, "scar_" + c["name"], (206, 132, 120))
            a.paint(stroke([(x + sd * 2.2 * k, y - 9.0 * FS), (x + sd * 1.0 * k, y - 2.0 * FS)], 1.2), key, 0.5, v=0.62)
            a.paint(stroke([(x + sd * 0.6 * k, y + 2.6 * FS), (x - sd * 0.6 * k, y + 8.0 * FS)], 1.2), key, 0.5, v=0.62)
    if f.get("drunk"):
        key = ensure(a, "drunk_nose", (206, 96, 90))
        x, y = P["nose"]
        a.paint(ell(x + 0.4, y - 2.0 * FS, 2.8 * FS, 2.6 * FS), key, 1.2, v=0.5)
    if f.get("mask") is None:
        nostrils(a, c, P)
    brows2(a, c, P, expr, brow, cam)
    mouth2(a, c, P, expr, skin)
    wrinkles(a, c, P, expr)
    mask2(a, c, P)
    if patch_side is not None:
        patch2(a, c, P, patch_side)
    specs2(a, c, P)
    if (c.get("hat") or {}).get("kind") == "kasa":
        # 笠の顎紐 (メッシュの紐は 128 では太すぎるので細い線で描き直す)
        cord = ensure(a, "cord_" + c["name"], c["pal"].get("cord", (140, 110, 70)))
        for s in (-1, 1):
            pts = [cam.proj(grow_pt(c, p))[0] for p in ((s * 3.2, 26.8, -0.6), (s * 3.0, 23.0, -2.6), (s * 1.0, 21.4, -2.6))]
            a.paint(stroke([tuple(p) for p in pts], 0.9), cord, 0.5, v=0.45)
    return P


# ====================================================================== 肖像

def prepare(name):
    c, faces, atlas, glow = build_faces(name)
    c["name"] = name
    cam = Cam(c)
    buf = raster(faces, atlas, glow, cam)
    ds = downsample(faces, *buf)
    return c, cam, ds


def portrait(name, expr, prep=None):
    c, cam, ds = prep or prepare(name)
    a = Art2(name)
    lay_mesh(a, *ds, name, c["face"])
    a.ramps.setdefault("m_skin", GP.make_ramp(c["pal"]["skin"], "a"))
    paint_face(a, c, cam, expr)
    bg = background(name)
    img = a.render(bg)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def names_all():
    return list(C.CAST)


def sheet(names):
    """前 | 後 の一覧 (2 倍。前 = いまの 3 表情、後 = 新しい 3 表情)。"""
    import voxel  # noqa: E402
    S = 192
    gap = 6
    cell_w = 6 * S + 5 * gap + 40
    cols = 2
    rows = (len(names) + cols - 1) // cols
    W = cols * cell_w + 40
    H = rows * (S + 40) + 80
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), "対話の肖像 — 前 (左 3 枚: neutral / smile / grave) | 後 (右 3 枚)。1.5 倍・最近傍", font=voxel.font(24), fill=(236, 232, 218))
    for i, n in enumerate(names):
        x0 = 20 + (i % cols) * cell_w
        y0 = 60 + (i // cols) * (S + 40)
        st = file_stem(n)
        for j, f in enumerate(FACES):
            op = WORK / "old" / f"{st}_{f}.png"
            npth = WORK / "new" / f"{st}_{f}.png"
            if op.exists():
                cv.paste(Image.open(op).convert("RGB").resize((S, S), Image.NEAREST), (x0 + j * (S + gap), y0))
            cv.paste(Image.open(npth).convert("RGB").resize((S, S), Image.NEAREST), (x0 + 3 * (S + gap) + 28 + j * (S + gap), y0))
        d.line([(x0 + 3 * (S + gap) + 11, y0), (x0 + 3 * (S + gap) + 11, y0 + S)], fill=(150, 232, 255), width=2)
        d.text((x0, y0 + S + 6), f"{C.CAST[n].get('ja', n)}  ({st})", font=voxel.font(16), fill=(150, 232, 255))
    OUT.mkdir(parents=True, exist_ok=True)
    cv.save(OUT / "sheet_old_new.png")
    # 小さめの分割 (読みやすさのため 3 枚に分ける)
    groups = {"attendants": [n for n in names if file_stem(n).startswith("attendant_") and not n.startswith(("survivor", "abyss"))],
              "abyss": [n for n in names if n.startswith(("survivor", "abyss"))] + (["artisan"] if "artisan" in names else []),
              "guests": [n for n in names if n in GUESTS]}
    return groups


def sheet_group(tag, names):
    import voxel  # noqa: E402
    S = 256
    gap = 6
    cell_w = 6 * S + 5 * gap + 40
    cols = 1 if len(names) <= 6 else 2
    rows = (len(names) + cols - 1) // cols
    cv = Image.new("RGB", (cols * cell_w + 40, rows * (S + 40) + 70), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), f"対話の肖像 ({tag}) — 前 | 後  (各 neutral / smile / grave、2 倍)", font=voxel.font(24), fill=(236, 232, 218))
    for i, n in enumerate(names):
        x0 = 20 + (i % cols) * cell_w
        y0 = 54 + (i // cols) * (S + 40)
        st = file_stem(n)
        for j, f in enumerate(FACES):
            op = WORK / "old" / f"{st}_{f}.png"
            if op.exists():
                cv.paste(Image.open(op).convert("RGB").resize((S, S), Image.NEAREST), (x0 + j * (S + gap), y0))
            cv.paste(Image.open(WORK / "new" / f"{st}_{f}.png").convert("RGB").resize((S, S), Image.NEAREST),
                     (x0 + 3 * (S + gap) + 28 + j * (S + gap), y0))
        d.line([(x0 + 3 * (S + gap) + 11, y0), (x0 + 3 * (S + gap) + 11, y0 + S)], fill=(150, 232, 255), width=2)
        d.text((x0, y0 + S + 6), f"{C.CAST[n].get('ja', n)}  ({st})", font=voxel.font(18), fill=(150, 232, 255))
    cv.save(OUT / f"sheet_{tag}.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--install", action="store_true")
    ap.add_argument("--sheet", action="store_true")
    ap.add_argument("--keep-old", action="store_true", help="old/ に今の肖像を写す (初回だけ)")
    args = ap.parse_args()
    names = args.names or names_all()
    (WORK / "new").mkdir(parents=True, exist_ok=True)
    (WORK / "old").mkdir(parents=True, exist_ok=True)
    if args.keep_old:
        for n in names_all():
            for f in FACES:
                src = FACE_DIR / f"{file_stem(n)}_{f}.png"
                dst = WORK / "old" / src.name
                if src.exists() and not dst.exists():
                    shutil.copyfile(src, dst)
    if not args.sheet or args.names:
        for n in names:
            prep = prepare(n)
            for f in FACES:
                im = portrait(n, f, prep)
                p = WORK / "new" / f"{file_stem(n)}_{f}.png"
                im.save(p, optimize=True)
                if args.install:
                    im.save(FACE_DIR / p.name, optimize=True)
            print(n, "->", file_stem(n))
    if args.sheet:
        allnames = names_all()
        groups = sheet(allnames)
        for tag, ns in groups.items():
            if ns:
                sheet_group(tag, ns)
        print("sheets ->", OUT)


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT / "tools" / "rough"))
    main()
