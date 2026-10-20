"""雷翼の武器の共通部品。雷鳥の同一性: B の軽さ (輪・長い羽) + 嵐に焼けた傷み (羽の先の焦げ・銅のひび)。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、y 上、正面 -Z、握りは各 item の GRIP)。
材質: acc = 白い風切羽 / soot = 焦げた羽先 / main・edge = 銅 / spine・guard・grip = 紺の鋼 / wrap = 銅の巻き / trim = 階級の縁 / gem = 光る稲妻。"""
from __future__ import annotations

import math

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, V, rot_x, rot_y, rot_z, nrm
import lpkit


def plume(base, tip, w, th=0.45, bend=0.0, big=False, mat="acc", tipmat="soot", n=None, scorch=1):
    """風切羽: 先が焦げた (soot) 細長い板。base から tip へ。広い面は ±Z (n で変更)。bend = 先の反り (羽の面の中での横ずれ)。
    scorch = 0 焦げなし / 1 先の 2 辺 / 2 先の 4 辺。"""
    base, tip = V(*base), V(*tip)
    d = tip - base
    L = float(np.linalg.norm(d))
    if big:
        raw = [(0, 0), (w * 0.8, L * 0.25), (w, L * 0.6), (0, L), (-w, L * 0.6), (-w * 0.8, L * 0.25)]
        tipidx = {1, 2, 3, 4} if scorch == 2 else {2, 3}
    else:
        raw = [(0, 0), (w, L * 0.4), (0, L), (-w, L * 0.4)]
        tipidx = {0, 1, 2, 3} if scorch == 2 else {1, 2}
    pts = [(x + bend * (y / L) ** 2, y) for x, y in raw]
    mats = [tipmat if (scorch and i in tipidx) else mat for i in range(len(pts))]
    up = V(0, 1, 0)
    ax = np.cross(up, d)
    if np.linalg.norm(ax) > 1e-9:
        a = nrm(ax)
        ang = math.atan2(np.linalg.norm(ax), np.dot(up, d))
        K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
        R = np.eye(3) + math.sin(ang) * K + (1 - math.cos(ang)) * (K @ K)
    else:
        R = np.eye(3) if d[1] > 0 else rot_z(180)
    return extrude(mat, pts, th, c=base, rot=R, mats=mats)


def torus(mat, c, R, ra, rb, n=8, k=4, ax="y", mats=None, phase=45.0):
    """輪 (断面 k 角形)。ax = 'y' 水平 / 'z' xy 平面 (正面を向く輪)。"""
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
    return lpkit.loft(BONE, mat, rings, mats=mats)


def band(path_pt, tangent, r=1.3, h=0.8, mat="trim", sides=4):
    """肢・柄に巻く銅の輪: 点 path_pt で tangent の向きの短い筒。"""
    t = nrm(V(*tangent))
    p = V(*path_pt)
    return prism(mat, tuple(p - t * h / 2), tuple(p + t * h / 2), r, r, sides=sides, caps=(False, False), hint=(0, 0, 1))


def bolt(pts, r=0.32, mat="gem"):
    """稲妻の線 (折れ線の細い筒)。"""
    n = len(pts)
    return curve(mat, pts, [r] * n, [r * 0.8] * n, sides=4, caps=(True, True), hint=(0, 0, 1))


def vein_x(y, y0, amp=0.55, per=3.2):
    """銅の稲妻の筋 (光る画素の中心線): y に沿ってジグザグ。"""
    ph = ((y - y0) / per) % 1.0
    return amp * (np.abs(ph * 4 - 2) - 1) * 0.5
