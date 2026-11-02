"""イサナの祖父の風見鶏 (skyabyss:weathercock、docs/design/12 の 2.6) の形 3 案。いまはバニラの避雷針の絵を借りている。
手に持って雷翼の方を指す道具。古い家の品なので、木の柄・真鍮・銅 (trim)・鉄 (main/spine)・革と麻縄で作る。鶏の羽の先だけ淡く光らせない (光る所は無い)。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z)。部品は親へ沈めて継ぐ。

  WEAPONS[id] = dict(jp, tier, glow, pose, grip, cen, scale, opts={"a": (形の関数, inlay, edge_mats, 一言), ...})
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent.parent))
from wkit import *  # noqa: F401,F403,E402
from wkit import block, prism, curve, extrude, rot_x, rot_y, rot_z  # noqa: E402

WEAPONS = {}


def rooster(cy, s=1.0, mat="trim"):
    """鶏の横顔の板 (xy 平面。胸が -x、尾が +x)。厚み 0.7"""
    pts = [(-2.6, 1.4), (-2.0, 0.6), (-0.6, 0.2), (1.0, 0.4), (2.2, 1.2), (2.6, 2.2), (3.8, 5.8), (3.0, 5.6), (2.4, 4.6), (2.2, 5.8), (1.4, 5.2),
           (1.2, 3.4), (0.2, 2.6), (-1.0, 2.8), (-1.2, 4.0), (-1.0, 4.8), (-1.5, 5.6), (-1.9, 5.0), (-2.4, 5.5), (-2.6, 4.6), (-3.6, 4.2),
           (-2.5, 3.8), (-2.4, 2.8)]
    pts = [(x * s, cy + y * s) for x, y in pts]
    return extrude(mat, pts, 0.7)


def arrow(cy, L=9.0, mat="main", tail="trim"):
    """風を受ける矢 (x 方向。矢じりが -x、羽が +x)"""
    f = prism(mat, (-L / 2, cy, 0), (L / 2, cy, 0), 0.32, 0.32, sides=6, caps=(True, True))
    f += extrude(mat, [(-L / 2 - 1.8, cy), (-L / 2 + 0.2, cy + 1.1), (-L / 2 + 0.2, cy - 1.1)], 0.5)
    f += extrude(tail, [(L / 2 - 2.4, cy), (L / 2 + 0.6, cy + 2.2), (L / 2 + 1.2, cy + 2.2), (L / 2 + 0.4, cy), (L / 2 + 1.2, cy - 2.2), (L / 2 + 0.6, cy - 2.2)], 0.4)
    return f


def compass_arms(cy, r=3.4, mat="spine", tip="trim"):
    """四方の腕と、先の小さな玉 (東西南北)"""
    f = block(mat, (0, cy, 0), (r * 2, 0.5, 0.5))
    f += block(mat, (0, cy, 0), (0.5, 0.5, r * 2))
    for dx, dz in ((r, 0), (-r, 0), (0, r), (0, -r)):
        f += block(tip, (dx, cy, dz), (0.9, 0.9, 0.9), rot=rot_y(45))
    return f


def haft(top, bot=-3.0, r=0.85):
    f = prism("grip", (0, bot, 0), (0, top, 0), r, r * 0.9, sides=6, caps=(True, True), phase=30)
    f += prism("wrap", (0, 0.0, 0), (0, 3.4, 0), r + 0.18, r + 0.18, sides=6, caps=(False, False), phase=30)
    f += prism("spine", (0, bot - 0.4, 0), (0, bot + 0.7, 0), r + 0.2, r + 0.15, sides=6, caps=(True, False), phase=30)
    return f


def wc_a():
    """A 杖の先の真鍮の鶏: 短い木の杖の先に銅の輪、四方の腕、その上で矢と鶏が回る。屋根の上の風見鶏をそのまま手に持てる大きさにした形。"""
    f = haft(12.0)
    f += prism("trim", (0, 11.6, 0), (0, 12.6, 0), 1.2, 1.2, sides=8, caps=(True, True))
    f += prism("spine", (0, 12.4, 0), (0, 19.0, 0), 0.3, 0.25, sides=6, caps=(True, True))
    f += compass_arms(14.0)
    f += arrow(16.0, 8.0)
    f += rooster(17.0, 0.8)
    f += block("trim", (0, 19.2, 0), (0.8, 0.8, 0.8), rot=rot_y(45))
    return f


def wc_b():
    """B 手持ちの風見盤: 吊り手の輪で下げる、真鍮の縁の羅針の盤。盤の上の細い針の先に小さな鶏が乗り、雷翼の方を向く。"""
    f = block("grip", (0, 0.0, 0), (1.0, 3.0, 1.0))
    f += curve("wrap", [(0, 1.0, 0), (-1.6, 2.8, 0), (0, 4.4, 0), (1.6, 2.8, 0), (0, 1.0, 0)], [0.45] * 5, [0.45] * 5, sides=4, caps=(True, True))
    f += prism("trim", (0, 4.4, 0), (0, 5.4, 0), 0.5, 0.5, sides=6, caps=(True, True))
    f += prism("trim", (0, 5.2, 0), (0, 6.4, 0), 4.4, 4.4, sides=12, caps=(True, True))
    f += prism("dark", (0, 6.3, 0), (0, 6.6, 0), 3.8, 3.8, sides=12, caps=(True, False))
    for k in range(8):
        a = math.radians(k * 45)
        f += block("bone" if k % 2 else "trim", (math.cos(a) * 3.0, 6.7, math.sin(a) * 3.0), (0.5, 0.3, 1.2 if k % 2 == 0 else 0.7), rot=rot_y(-k * 45))
    f += prism("spine", (0, 6.5, 0), (0, 9.6, 0), 0.22, 0.2, sides=6, caps=(True, True))
    f += arrow(9.6, 6.4)
    f += rooster(10.4, 0.62)
    return f


def wc_c():
    """C 骨の羽の風見: 鯨の骨の柄の先に、雷翼の羽をかたどった銅の羽根板。羽根の付け根に小さな真鍮の鶏が止まる。家に伝わる細工物。"""
    f = prism("bone", (0, -3.0, 0), (0, 13.0, 0), 0.9, 0.7, sides=6, caps=(True, True), phase=30)
    for y in (1.0, 2.4, 3.8):
        f += prism("cord", (0, y, 0), (0, y + 0.7, 0), 1.05, 1.05, sides=6, caps=(False, False), phase=30)
    f += prism("trim", (0, 12.6, 0), (0, 13.6, 0), 1.3, 1.1, sides=8, caps=(True, True))
    f += prism("spine", (0, 13.4, 0), (0, 15.4, 0), 0.3, 0.3, sides=6, caps=(True, True))
    # 羽根板 (先が細る羽の形。軸に沿って筋)
    pts = [(-1.0, 15.0), (2.0, 15.6), (6.0, 17.0), (9.6, 19.4), (11.2, 21.6), (8.6, 20.2), (5.0, 18.6), (1.6, 17.4), (-1.0, 16.6)]
    f += extrude("main", pts, 0.5)
    f += curve("trim", [(-0.6, 15.8, -0.3), (5.0, 17.6, -0.3), (10.6, 21.2, -0.3)], [0.25, 0.25, 0.2], [0.25, 0.25, 0.2], sides=4, caps=(True, True))
    f += extrude("main", [(-1.0, 15.2), (-4.0, 16.4), (-4.4, 15.6), (-1.6, 14.6)], 0.5)
    f += rooster(15.8, 0.55)
    return f


WEAPONS["weathercock"] = dict(
    jp="祖父の風見鶏", tier="common", glow="none", pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 9.0, 0.0), scale=10.0,
    opts=dict(
        a=(wc_a, None, (), "杖の先の真鍮の鶏。屋根の上の風見鶏を、手に持てる大きさにした形。四方の腕と、矢と鶏。"),
        b=(wc_b, None, (), "手持ちの風見盤。吊り手で下げる真鍮の羅針の盤に、細い針と小さな鶏。"),
        c=(wc_c, None, (), "骨の羽の風見。鯨の骨の柄の先に、雷翼の羽をかたどった銅の羽根板と小さな鶏。")))
