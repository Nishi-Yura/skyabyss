"""装飾品の低ポリのラフ共通。武器 (../weapons) の土台と材質の言葉をそのまま使う。
座標: 1 単位 = 1/16 ブロック、y 上、正面 -Z。品は原点まわり (おおよそ y -8..8)。"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
WEAP = HERE.parent / "weapons"
sys.path.insert(0, str(WEAP))
from scene import *  # noqa: F401,F403,E402
from paint import paint  # noqa: E402
from wkit import (block, prism, curve, extrude, feather, mirror_x, remat, at, V, rot_x, rot_y, rot_z,  # noqa: E402,F401
                  BONE, loft, spike, grid, ROOT, ASSETS, export_mesh, save_img, tri_count, build)
import lpkit  # noqa: E402

OUT = ROOT / "docs/review/lowpoly_roughs/accessories"


def torus(mat, c, R, ra, rb, n=8, k=4, ax="y", mats=None, phase=45.0):
    """輪 (断面 k 角形)。ax='y' は水平 (平らに置いた輪)、'z' は xy 平面 (正面を向く輪)。
    ra = 輪の半径方向の半幅、rb = 軸方向の半幅。mats は区間ごとの材質。"""
    c = V(*c)
    rings = []
    for i in range(n + 1):
        th = 2 * math.pi * (i % n) / n
        if ax == "y":
            d = V(math.cos(th), 0, math.sin(th))
            a = V(0, 1, 0)
        else:
            d = V(math.cos(th), math.sin(th), 0)
            a = V(0, 0, 1)
        pts = []
        for j in range(k):
            ph = math.radians(phase + 360.0 * j / k)
            pts.append(c + d * (R + ra * math.cos(ph)) + a * (rb * math.sin(ph)))
        rings.append(pts)
    return loft(BONE, mat, rings, mats=mats)


def ell(mat, c, r, segs=6, rings=2, rot=None):
    return lpkit.ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot)


def fan(mat, base, n, length, spread, w, th=0.5, down=True, big=False, z=0.0, mid=0.0, dz=0.12):
    """xy 平面の羽の扇 (広い面は ±Z)。mid は扇の向きの中心 (度。0 = 真下 / 真上)。"""
    out = []
    for i in range(n):
        a = math.radians(mid + (i - (n - 1) / 2) * spread)
        sg = -1 if down else 1
        zz = z + dz * (i % 2)
        tip = (base[0] + math.sin(a) * length, base[1] + sg * math.cos(a) * length, zz)
        out += feather(mat, (base[0], base[1], zz), tip, w, th, big=big)
    return out


def hexdisc(mat, c, r, t, sides=6, phase=0.0, axis="z"):
    c = V(*c)
    if axis == "z":
        return prism(mat, (c[0], c[1], c[2] - t / 2), (c[0], c[1], c[2] + t / 2), r, r, sides=sides, caps=(True, True),
                     phase=phase, hint=(1, 0, 0))
    return prism(mat, (c[0], c[1] - t / 2, c[2]), (c[0], c[1] + t / 2, c[2]), r, r, sides=sides, caps=(True, True),
                 phase=phase, hint=(1, 0, 0))


def inlay(env, kind):
    """伝説だけ、縁の金属・鉄・鋼の稜がまだらに光る。"""
    if kind == "legend":
        return (np.isin(env["mats"], ["edge", "spine", "acc"]) & env["convex"]
                & (((env["Y"] * 0.5 + env["X"] * 0.5) % 1.0) < 0.5))
    return env["lit"] < -9


def make(fn, titan, rarity="common"):
    faces = fn(rarity)
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, titan, rarity, 0, inlay, ())
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def save(r, d, stem):
    d.mkdir(parents=True, exist_ok=True)
    save_img(r["atlas"], d / f"{stem}_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    save_img(gm, d / f"{stem}_glowmask.png")
    export_mesh(r["faces"], r["size"], d / f"{stem}_mesh.json", stem, f"{stem}_atlas.png", f"{stem}_glowmask.png")
