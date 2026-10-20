"""様式 A「量塊」(Carved Mass): 岩鯨の武器 5 点。
モンハン・ダークソウル系。太く大きな 1 つの輪郭、面は大きく、材質は骨と石の 2 つが主役、光は 1 か所だけ。
大剣 = 厚い断ち切りの板、弓 = 太く短い骨弓、槍 = 広い葉の穂、杖 = 骨の環に抱かれた核、短剣 = 肉厚の鉈。
座標は wkit と同じ (1 単位 = 1/16 ブロック、+Y が先、広い面は ±Z)。"""
from __future__ import annotations

import math

from wkit import *  # noqa: F401,F403
from wkit import block, blade, prism, curve, extrude, feather, remat, at, V, rot_x, rot_y, rot_z
import d_thunder as T
from lpkit import ellipsoid

GRIP = dict(greatsword=(0, 1.8, 0), spear=(0, 3.0, 0), staff=(0, 3.0, 0), dagger=(0, 1.0, 0), bow=(5.0, 8.0, 0))
STAGES = T.STAGES


def hex_grip(y0, y1, r=1.3, wrap=(0.2, 1.9)):
    f = prism("grip", (0, y0, 0), (0, wrap[0], 0), r, r * 0.92, sides=6, caps=(False, False))
    f += prism("wrap", (0, wrap[0], 0), (0, wrap[1], 0), r * 1.1, r * 1.0, sides=6, caps=(False, False))
    f += prism("grip", (0, wrap[1], 0), (0, y1, 0), r, r * 0.92, sides=6, caps=(False, False))
    return f


def greatsword(tier=1):
    f = hex_grip(-1.6, 3.6, 1.35)
    # 柄頭: 石の大塊
    f += block("spine", (0, -2.5, 0), (4.4, 2.2, 3.6), taper=(0.8, 0.8), skip=("u",))
    f += block("trim", (0, -1.3, 0), (3.4, 0.6, 3.0))
    # 鍔: 石の太い梁 + 下へ曲がる骨の鉤
    f += block("guard", (0, 4.4, 0), (10.0, 2.2, 4.2), taper=(0.96, 0.96))
    for s in (1, -1):
        f += block("edge", (s * 5.4, 3.5, 0), (2.0, 3.6, 2.8), rot=rot_z(-s * 16), taper=(0.6, 0.7))
    f += block("gem", (0, 4.5, -2.15), (1.8, 1.3, 0.5), taper=(0.8, 0.8))
    # 刃: 幅の広い断ち切りの板。背は直線、先は斜めに断つ
    st = [(5.8, -3.1, 3.7, 1.7, 1.7), (13.0, -3.1, 3.7, 1.6, 1.6), (17.0, -3.1, 3.5, 1.3, 1.3),
          (19.4, -3.1, 2.2, 0.8, 0.8), (21.4, -3.1, -3.1, 0.0, 0.0)]
    f += blade("main", st)
    # 背の石の帯 (背を重くする)
    if tier >= 1:
        f += block("spine", (-3.6, 12.4, 0), (1.4, 13.6, 3.0), taper=(0.9, 0.9))
    # 樋: 付け根の短い光の筋 (光はここだけ)
    f += block("vein", (0.7, 8.6, -1.72), (0.7, 4.2, 0.2))
    if tier >= 1:
        f += block("trim", (0, 6.4, 0), (7.6, 0.9, 3.6))
        for s in (1, -1):
            f += block("trim", (s * 4.4, 4.5, -2.2), (1.0, 1.0, 0.5))
        f += block("trim", (0, 11.0, 0), (7.6, 0.8, 3.6))
    if tier >= 2:
        # 背びれの冠: 刃の背に高く立つ骨の板 3 枚 (輪郭が変わる)
        for k, (y, h) in enumerate(((8.2, 8.0), (13.2, 8.4), (17.6, 6.0))):
            f += at(extrude("edge", [(0, 0), (2.6, h * 0.25), (1.0, h), (-2.0, h * 0.5)], 1.4), None, (-4.4 + 0.3 * k, y, 0))
        # 鍔の上の骨の角 (広がる)
        for s in (1, -1):
            f += spike(BONE, "edge", (s * 3.6, 5.2, 0), (s * 0.6, 1, 0), 6.0, 1.3, sides=4, rings=3, hint=(0, 0, 1))
        f += block("vein", (0.7, 12.6, -1.62), (0.7, 3.8, 0.2))
        f += block("trim", (0, -0.4, 0), (3.2, 0.7, 3.0))
    return f


def bow(tier=1, stage=0):
    CY = T.CY
    f = []
    # 握り: 石の塊 (太い)
    f += block("guard", (5.0, CY, 0), (3.4, 5.6, 3.4), taper=(0.9, 0.9))
    f += block("wrap", (5.0, CY, 0), (3.7, 2.4, 3.7))
    f += block("gem", (6.8, CY, 0), (0.8, 1.3, 1.6), taper=(0.8, 0.8))
    up = [(5.0, 10.8, 0), (4.8, 12.6, 0), (3.2, 14.9, 0), (0.6 - T.TIP_PULL[stage] * 0.5, 16.6, 0), (-2.2 - T.TIP_PULL[stage], 17.0, 0)]
    lo = [(x, 2 * CY - y, z) for x, y, z in up]
    for path in (up, lo):
        sg = 1 if path is up else -1
        f += curve("main", path, [1.5, 1.4, 1.1, 0.8, 0.5], [1.7, 1.6, 1.3, 1.0, 0.7], sides=4, caps=(False, False), hint=(0, 0, 1))
        # 肢の外側の石の当て (厚みを作る)
        px, py, _ = path[1]
        f += block("spine", (px - 0.2, py, 0), (2.2, 2.2, 2.4), rot=rot_z(sg * 14), taper=(0.7, 0.7))
        tx, ty, _ = path[-1]
        f += block("edge", (tx - 0.2, ty + sg * 0.2, 0), (1.6, 1.6, 1.8), taper=(0.7, 0.7))
    if tier >= 2:
        for sg in (1, -1):
            f += at(extrude("edge", [(0, 0), (1.8, 1.4), (1.0, 4.6), (-0.8, 2.0)], 0.9), rot_z(0 if sg > 0 else 180), (7.0, CY + sg * 2.2, 0))
    f += T.string_and_arrow(stage, "rare", ((up[-1][0], up[-1][1]), (lo[-1][0], lo[-1][1])))
    return remat_cord(f)


def remat_cord(f):
    return f


def spear(tier=1):
    f = []
    f += prism("grip", (0, -2.4, 0), (0, 0.6, 0), 1.2, 1.2, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.6, 0), (0, 5.4, 0), 1.35, 1.35, sides=6, caps=(False, False))
    f += prism("grip", (0, 5.4, 0), (0, 12.0, 0), 1.2, 1.2, sides=6, caps=(False, True))
    f += spike(BONE, "spine", (0, -2.2, 0), (0, -1, 0), 2.6, 1.0, sides=4, rings=1, hint=(0, 0, 1))
    f += block("spine", (0, 8.4, 0), (3.4, 1.2, 3.4), taper=(0.9, 0.9))
    f += block("spine", (0, 12.2, 0), (4.4, 2.0, 3.8), taper=(0.85, 0.85))
    # 穂: 広い葉
    st = [(13.2, -2.2, 2.2, 1.3, 1.3), (15.0, -3.5, 3.5, 1.5, 1.5), (17.4, -2.6, 2.6, 1.1, 1.1), (19.4, -1.0, 1.0, 0.5, 0.5), (20.8, 0, 0, 0, 0)]
    f += blade("main", st)
    for s in (1, -1):   # 下がり羽根 (穂の根の短い翼)
        f += block("edge", (s * 3.6, 12.6, 0), (3.2, 1.2, 2.2), rot=rot_z(-s * 22), taper=(0.7, 0.8))
    f += block("vein", (0, 14.6, -1.55), (0.7, 2.4, 0.2))
    if tier >= 1:
        f += block("trim", (0, 10.4, 0), (3.2, 0.8, 3.2))
        f += block("trim", (0, 3.4, 0), (2.8, 0.8, 2.8))
    return f


def staff(tier=1):
    f = []
    f += prism("grip", (0, -2.6, 0), (0, 0.8, 0), 1.25, 1.25, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.8, 0), (0, 5.0, 0), 1.4, 1.4, sides=6, caps=(False, False))
    f += prism("grip", (0, 5.0, 0), (0, 13.0, 0), 1.25, 1.1, sides=6, caps=(False, False))
    f += spike(BONE, "spine", (0, -2.4, 0), (0, -1, 0), 2.4, 1.0, sides=4, rings=1, hint=(0, 0, 1))
    f += block("spine", (0, 13.2, 0), (4.2, 1.8, 3.8), taper=(0.8, 0.8))
    # 骨の環 (椎骨の輪): 3.8 の半径
    cy, R = 17.0, 3.7
    path = [(R * math.sin(math.radians(a)), cy + R * math.cos(math.radians(a)) , 0) for a in range(0, 361, 30)]
    f += curve("main", path, 1.0, 1.0, sides=4, caps=(False, False), hint=(0, 0, 1))
    for a in (0, 90, 180, 270):   # 環の 4 つの節
        x, y = R * math.sin(math.radians(a)), cy + R * math.cos(math.radians(a))
        f += block("edge", (x, y, 0), (2.0, 2.0, 2.4), rot=rot_z(-a), taper=(0.8, 0.8))
    f += ellipsoid(BONE, "gem", (0, cy, 0), (2.0, 2.0, 2.0), segs=8, rings=3)
    # 環を受ける石の爪 2 本
    for s in (1, -1):
        f += block("guard", (s * 1.6, 14.4, 0), (1.4, 2.6, 2.0), rot=rot_z(-s * 22), taper=(0.7, 0.8))
    if tier >= 1:
        f += block("trim", (0, 9.0, 0), (3.0, 0.8, 3.0))
    if tier >= 2:
        for s in (1, -1):
            f += at(extrude("edge", [(0, 0), (1.4, 1.4), (0.6, 4.0), (-0.8, 1.6)], 0.9), rot_z(0 if s > 0 else 180), (s * 3.8, cy + 2.4, 0))
    return f


def dagger(tier=1):
    f = hex_grip(-1.8, 2.2, 1.3, wrap=(-0.8, 1.0))
    f += block("spine", (0, -2.5, 0), (3.6, 1.8, 3.2), taper=(0.8, 0.8), skip=("u",))
    f += block("guard", (0, 2.8, 0), (6.6, 1.6, 3.4), taper=(0.95, 0.95))
    for s in (1, -1):
        f += block("edge", (s * 3.4, 2.3, 0), (1.6, 2.4, 2.2), rot=rot_z(-s * 14), taper=(0.7, 0.8))
    f += block("gem", (0, 2.9, -1.75), (1.4, 1.0, 0.4), taper=(0.8, 0.8))
    # 肉厚の鉈の刃: 背が直線、腹がふくらむ
    st = [(3.8, -2.0, 2.4, 1.4, 1.4), (7.0, -2.0, 3.4, 1.5, 1.5), (10.2, -2.0, 3.2, 1.2, 1.2), (12.8, -2.0, 1.6, 0.8, 0.8), (14.6, -2.0, -2.0, 0, 0)]
    f += blade("main", st)
    f += block("spine", (-2.4, 8.6, 0), (1.0, 8.0, 2.6), taper=(0.9, 0.9))
    f += block("vein", (0.3, 6.0, -1.55), (0.6, 2.6, 0.2))
    if tier >= 1:
        f += block("trim", (0, 4.2, 0), (4.6, 0.7, 3.0))
    return f
