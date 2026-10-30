"""野の装備の武器 (docs/design/51) の共通の部品と品の表の作り方。形は品ごとの構想から o_wild1〜5.py が作る。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z。弓は握り (5, CY))。部品は親へ沈めて継ぐ。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
for p in (HERE, HERE.parent / "one", HERE.parent, HERE.parent.parent):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))
from kit_one import *  # noqa: F401,F403,E402
from kit_one import ell, lump, bone, strand, ringy, gbar, gem, arc, bend, mirror_faces_y, string_and_arrow, inlay_line, CY  # noqa: E402,F401
from st_kit import torus, plume  # noqa: E402,F401
import paint_wild  # noqa: E402

# 種類ごとの持ち方と絵の枠 (o_moon・o_remake と同じ値)
KINDS = {
    "sword": dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 10.0, 0.0), scale=9.5),
    "greatsword": dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 11.0, 0.0), scale=8.5),
    "spear": dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 10.0, 0.0), scale=7.5),
    "glaive": dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 10.0, 0.0), scale=7.5),
    "hammer": dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.0),
    "axe": dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.0),
    "maul": dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 8.0, 0.0), scale=9.0),
    "mace": dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=9.5),
    "staff": dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5),
    "bell": dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5),
    "totem": dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5),
    "censer": dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.0, 0.0), scale=9.5),
    "bow": dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=12.0, stages=4),
    "longbow": dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=11.0, stages=4),
    "dagger": dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=16.0),
    "fang": dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 5.0, 0.0), scale=15.0),
    "claw": dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 7.0, 0.0), scale=13.0),
}
# 品の階級 → 絵を作るときの段 (縁と光る筋の型。ヒロイックだけ伝説の筋)
BUILD_TIER = {"uncommon": "rare", "rare": "rare", "epic": "rare", "heroic": "legend"}


def W(fn, jp, rarity, kind, glow, mats, inlay=None):
    """品の表の一行。mats は材質名 → 素材 (paint_wild.S の名前)"""
    wid = fn.__name__
    paint_wild.register(wid, mats, glow, rarity)
    k = dict(KINDS[kind])
    stages = k.pop("stages", 1)
    e = dict(jp=jp, tier=BUILD_TIER[rarity], rarity=rarity, kind=kind, glow=wid, **k,
             opts={"a": ((lambda stage=0: fn(stage)), inlay or inlay_line(["main"], 99.0, 99.5), ("edge",),
                         (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return wid, e


# ---------------------------------------------------------------- 共通の部品
def haft(y0, y1, r=0.85, mat="grip", sides=8, wrap=None, wmat="wrap", butt=None):
    """柄: y0 → y1 の筒。wrap = (y0, y1) の巻き、butt = 石突きの材質 (尖り)"""
    f = prism(mat, (0, y0, 0), (0, y1, 0), r, r * 0.96, sides=sides, caps=(True, True))
    if wrap:
        f += prism(wmat, (0, wrap[0], 0), (0, wrap[1], 0), r + 0.14, r + 0.14, sides=sides, caps=(False, False))
    if butt:
        f += spike(BONE, butt, (0, y0 + 0.3, 0), (0, -1, 0), 1.4, r * 0.95, sides=sides, rings=1, hint=(0, 0, 1))
    return f


def bow_grip(mat="grip", wmat="wrap", r=1.0):
    f = prism(mat, (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), r, r, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism(wmat, (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), r + 0.12, r + 0.12, sides=8, caps=(False, False), hint=(0, 0, 1))
    return f


def bell(c, r=0.7, h=1.2, mat="acc", mouth="dark", sides=8):
    """小さな鈴 (口は下)"""
    x, y, z = c
    rings_ = [[V(x + rr * math.cos(math.radians(360 * j / sides)), yy, z + rr * math.sin(math.radians(360 * j / sides))) for j in range(sides)]
              for yy, rr in ((y - h, r), (y - h * 0.85, r * 0.95), (y - h * 0.4, r * 0.7), (y, r * 0.3))]
    f = loft(BONE, mat, rings_, caps=(mouth, mat))
    f += ell(mat, (x, y + 0.1, z), (r * 0.3, r * 0.3, r * 0.3), segs=5, rings=1)
    return f


def lash(y, r, n=2, mat="cord", h=0.32, gap=0.45):
    """紐の巻き (細い輪を n 本)"""
    f = []
    for i in range(n):
        f += ringy(mat, y + i * gap, r, h)
    return f


def flag(top, w, h, z=0.0, mat="cloth", tears=3, wave=0.5, th=0.18):
    """垂れた布 (上辺 top の左端から右へ w、下へ h。下の縁は裂ける)。波打たせる"""
    x0, y0 = top
    pts = [(x0, y0), (x0 + w, y0)]
    for i in range(tears * 2 + 1):
        t = 1 - i / (tears * 2)
        dy = h * (0.75 if i % 2 else 1.0) * (1 - 0.15 * math.sin(i * 1.7))
        pts.append((x0 + w * t, y0 - dy))
    f = []
    # 帯に分けて波打たせる (凸の板を並べる)
    cols = 4
    for c in range(cols):
        xa, xb = x0 + w * c / cols, x0 + w * (c + 1) / cols
        za, zb = z + wave * math.sin(c * 1.4), z + wave * math.sin((c + 1) * 1.4)
        ya = y0 - h * (0.92 - 0.12 * (c % 2))
        yb = y0 - h * (0.82 + 0.1 * (c % 2))
        quad = [V(xa, y0, za), V(xb, y0, zb), V(xb, yb, zb), V(xa, ya, za)]
        f += _slab(mat, quad, th)
    return f


def _slab(mat, quad, th):
    """四角い板 (quad は前の面の 4 点、厚み th を +Z へ)"""
    import numpy as np
    q = [np.asarray(p, float) for p in quad]
    back = [p + np.array([0, 0, th]) for p in q]
    rings_ = [q, back]
    return loft(BONE, mat, rings_, caps=(mat, mat))


def leaf_blade(y0, y1, w, t, mat="main", belly=0.45, colmats=None, cx=0.0):
    """木の葉の形の穂・刃 (根 y0 → 先 y1、いちばん広い所は belly)"""
    L = y1 - y0
    st = [(y0, -w * 0.45, w * 0.45, t * 0.8, t * 0.8, cx),
          (y0 + L * belly * 0.5, -w * 0.85, w * 0.85, t, t, cx),
          (y0 + L * belly, -w, w, t, t, cx),
          (y0 + L * 0.75, -w * 0.6, w * 0.6, t * 0.8, t * 0.8, cx),
          (y1, 0, 0, 0, 0, cx)]
    return blade(mat, st, colmats=colmats or ["edge", mat, "edge", "edge", mat, "edge"])


def claw_hand(y0=-0.9, y1=4.0, r=0.85):
    """爪の握り (短い柄と拳の甲の台)"""
    return haft(y0, y1, r=r, wrap=(y0 + 0.5, y1 - 0.6))
