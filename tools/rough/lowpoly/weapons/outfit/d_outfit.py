"""市の武具屋と道具屋の品 4 点 x 3 案の形 (2026-10-27 の声「ブロック以外はすべて mod で追加したアイテムで」)。
バニラの盾・斧・つるはし・シャベルの代わり。どれも町の職人の素朴な品なので、祠の始めの武器と同じ素の材料で揃える:
木の柄 (grip)、素の鉄 (main / edge / spine / guard)、真鍮の縁と鋲 (trim)、革と布 (wrap)、麻縄 (cord)、鯨の骨 (bone)、暗 (dark)。
都の紋 (鐘と四つの星) だけは真鍮で入れる (都の品の印)。階級はふつう (common)。光る所は無い。
座標は他の武器と同じ (1 単位 = 1/16 ブロック、+Y が先、x = 0・z = 0 が握り、広い面は ±Z、正面 = -Z)。
部品は親へ沈めて継ぐ (継ぎ目で中が見えない)。

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
from wkit import block, prism, curve, extrude, V, rot_x, rot_y, rot_z  # noqa: E402

WEAPONS = {}


def ring(cy, R, mat, n=12, w=0.9, d=1.6, z=0.0):
    """xy 平面の輪 (縁取り)。中心 (0, cy)、半径 R"""
    f = []
    for k in range(n):
        a0 = math.radians(360.0 * k / n)
        a1 = math.radians(360.0 * (k + 1) / n)
        f += curve(mat, [(R * math.cos(a0), cy + R * math.sin(a0), z), (R * math.cos(a1), cy + R * math.sin(a1), z)], [w, w], [d, d], sides=4, caps=(True, True))
    return f


def disc(cy, R, n=12):
    return [(R * math.cos(math.radians(360.0 * k / n + 15)), cy + R * math.sin(math.radians(360.0 * k / n + 15))) for k in range(n)]


def bell_mark(c, s=1.0):
    """都の紋: 鐘 (台形) と、まわりの四つの星 (小さな鋲)。真鍮"""
    x, y, z = c
    f = block("trim", (x, y - 0.3 * s, z), (2.2 * s, 2.2 * s, 0.5), taper=(0.55, 1.0))
    f += block("trim", (x, y + 1.1 * s, z), (0.6 * s, 0.6 * s, 0.5))
    for dx, dy in ((-2.0, 1.4), (2.0, 1.4), (-2.0, -1.6), (2.0, -1.6)):
        f += block("trim", (x + dx * s, y + dy * s, z), (0.6 * s, 0.6 * s, 0.45), rot=rot_z(45))
    return f


def haft(top, bot=-3.0, r=0.95, wrap=(0.0, 3.6)):
    """木の柄 (6 角)。握りに革の巻き、石突きに鉄の輪"""
    f = prism("grip", (0, bot, 0), (0, top, 0), r, r * 0.92, sides=6, caps=(True, True), phase=30)
    f += prism("wrap", (0, wrap[0], 0), (0, wrap[1], 0), r + 0.18, r + 0.18, sides=6, caps=(False, False), phase=30)
    f += prism("spine", (0, bot - 0.4, 0), (0, bot + 0.8, 0), r + 0.2, r + 0.15, sides=6, caps=(True, False), phase=30)
    return f


# ================================================================== 旅の盾 (武具屋。左手に構える)
SH_CY = 8.0


def shield_back(w=5.2):
    """裏: 握りの横木と腕の革帯 (どの案も同じ位置)"""
    f = block("grip", (0, SH_CY, 1.8), (w, 1.0, 1.0))
    f += block("wrap", (0, SH_CY + 3.4, 1.6), (1.4, 4.4, 0.7))
    return f


def sh_a():
    """A 板の丸盾: 縦の板を並べた木の丸盾、鉄の縁と真ん中の鉄の瘤、板を留める横の鉄帯 2 本。いちばん「盾」らしい。"""
    f = extrude("guard", disc(SH_CY, 7.2, 16), 1.8)
    f += extrude("grip", disc(SH_CY, 6.3, 16), 2.0, mats=["guard"] * 16)
    for k in range(-2, 3):
        f += block("dark", (k * 2.6, SH_CY, -0.92), (0.25, 12.6 - abs(k) * 1.6, 0.2))
    for dy in (-3.2, 3.2):
        f += block("spine", (0, SH_CY + dy, -1.0), (12.0 - abs(dy) * 0.9, 1.0, 0.5))
        for sx in (-1, 1):
            f += block("trim", (sx * (5.2 - abs(dy) * 0.4), SH_CY + dy, -1.3), (0.6, 0.6, 0.4))
    f += block("main", (0, SH_CY, -1.4), (3.2, 3.2, 1.4), taper=(0.55, 0.55), rot=rot_x(90))
    f += block("edge", (0, SH_CY, -2.2), (1.2, 1.2, 0.6), taper=(0.6, 0.6), rot=rot_x(90))
    return f + shield_back()


KITE = [(0, -2.6), (4.6, 3.0), (5.8, 9.0), (5.4, 13.6), (0, 15.4), (-5.4, 13.6), (-5.8, 9.0), (-4.6, 3.0)]


def sh_b():
    """B 都の凧形盾: 上が広く下が尖る鉄張りの盾。縁は鉄、面は藍の布張り、真ん中に真鍮の都の紋 (鐘と四つの星)。都の衛兵と同じ形。"""
    f = extrude("guard", KITE, 2.0)
    inner = [(x * 0.84, SH_CY + (y - SH_CY) * 0.86) for x, y in KITE]
    f += extrude("wrap", inner, 2.6, mats=["guard"] * len(KITE))
    f += bell_mark((0, SH_CY + 1.4, -1.45), 1.3)
    for x, y in ((0, 14.2), (4.6, 12.6), (-4.6, 12.6), (4.4, 5.0), (-4.4, 5.0), (0, -1.0)):
        f += block("trim", (x, y, -1.15), (0.7, 0.7, 0.5))
    return f + shield_back(4.8)


def sh_c():
    """C 鯨骨の枠の革盾: 鯨の肋骨を曲げた楕円の枠に、なめし革を張って麻縄で縫いとめる。骨の筋が十字に走る。漁と鯨追いの町の盾。"""
    pts = [(6.2 * math.cos(math.radians(a)), SH_CY + 7.6 * math.sin(math.radians(a))) for a in range(15, 375, 30)]
    f = extrude("wrap", pts, 1.2)
    f += curve("bone", [(6.2 * math.cos(math.radians(a)), SH_CY + 7.6 * math.sin(math.radians(a)), 0) for a in range(0, 361, 30)],
               [0.9] * 13, [1.6] * 13, sides=4, caps=(False, False))
    f += curve("bone", [(0, SH_CY - 7.2, -0.6), (0, SH_CY, -1.0), (0, SH_CY + 7.2, -0.6)], [0.7, 0.8, 0.7], [0.7, 0.8, 0.7], sides=4, caps=(True, True))
    f += curve("bone", [(-5.8, SH_CY, -0.6), (0, SH_CY, -1.0), (5.8, SH_CY, -0.6)], [0.7, 0.8, 0.7], [0.7, 0.8, 0.7], sides=4, caps=(True, True))
    # 縫い目 (縁の内側の縄の点)
    for a in range(0, 360, 24):
        x, y = 5.0 * math.cos(math.radians(a)), SH_CY + 6.2 * math.sin(math.radians(a))
        f += block("cord", (x, y, -0.7), (0.5, 0.9, 0.3), rot=rot_z(a))
    f += block("bone", (0, SH_CY, -1.6), (2.0, 2.0, 1.0), taper=(0.6, 0.6))
    return f + shield_back(5.0)


WEAPONS["travel_shield"] = dict(
    jp="旅の盾", tier="common", glow="none", pose="tide", grip=(0.0, SH_CY, 1.8), cen=(0.0, 7.0, 0.0), scale=12.0,
    opts=dict(
        a=(sh_a, None, (), "板の丸盾。木の板を鉄の縁と帯で留めた丸盾、真ん中に鉄の瘤。素朴な旅の盾。"),
        b=(sh_b, None, (), "都の凧形盾。鉄の縁に藍の布張り、真鍮の都の紋 (鐘と四つの星)。都の衛兵と同じ形。"),
        c=(sh_c, None, (), "鯨骨の枠の革盾。肋骨の枠に革を張り縄で縫う、骨の十字。鯨追いの町の盾。")))


# ================================================================== 伐り斧 (道具屋。丸太を採る)
AX_TOP = 17.0


def ax_a():
    """A 木こりの片刃斧: 幅の広い片刃と四角い峰。柄は少し反った木、刃の付け根に麻縄の巻き。"""
    f = haft(AX_TOP, bot=-3.0)
    head = [(0.6, AX_TOP - 4.0), (4.4, AX_TOP - 5.4), (6.4, AX_TOP - 6.0), (6.6, AX_TOP + 1.6), (4.6, AX_TOP + 0.4), (0.6, AX_TOP - 0.2)]
    f += extrude("main", head, 1.3, mats=["main"] * 6)
    f += extrude("edge", [(5.8, AX_TOP - 6.0), (6.9, AX_TOP - 6.1), (7.0, AX_TOP + 1.8), (5.9, AX_TOP + 1.5)], 0.7)
    f += block("spine", (-0.2, AX_TOP - 2.0, 0), (2.6, 4.2, 1.9))
    f += prism("cord", (0, AX_TOP - 5.4, 0), (0, AX_TOP - 4.2, 0), 1.15, 1.15, sides=6, caps=(True, True))
    return f


def ax_b():
    """B 鉞 (まさかり): 刃の縁が大きく弧を描く広刃。頭は厚い鉄の楔で、真鍮の鋲が二つ。柄は真っすぐで長め。"""
    f = haft(AX_TOP + 0.5, bot=-3.6)
    f += block("spine", (0.6, AX_TOP - 1.6, 0), (3.2, 4.6, 2.0))
    f += extrude("main", [(1.6, AX_TOP - 3.6), (4.8, AX_TOP - 6.4), (7.2, AX_TOP - 5.0), (7.8, AX_TOP - 1.6), (7.2, AX_TOP + 1.8), (4.8, AX_TOP + 3.0), (1.6, AX_TOP + 0.4)], 1.2)
    arc = [(7.0 + 1.0 * math.cos(math.radians(a)), AX_TOP - 1.6 + 4.2 * math.sin(math.radians(a)), 0) for a in range(-80, 81, 32)]
    f += curve("edge", arc, [0.5] * len(arc), [0.6] * len(arc), sides=4, caps=(True, True))
    for dy in (-2.6, 0.4):
        f += block("trim", (0.6, AX_TOP + dy, -1.05), (0.7, 0.7, 0.4))
    return f


def ax_c():
    """C 鉈斧: 短い片刃と、反対に鉤 (枝を引き寄せる)。柄は短めで革の握り、柄頭に紐の輪。山の木こりの道具。"""
    f = haft(AX_TOP - 1.0, bot=-2.6, wrap=(-1.6, 3.0))
    t = AX_TOP - 1.0
    f += extrude("main", [(0.8, t - 3.6), (4.6, t - 4.4), (5.4, t - 3.2), (5.4, t + 0.6), (3.8, t + 1.4), (0.8, t + 0.6)], 1.3)
    f += extrude("edge", [(4.8, t - 4.4), (5.9, t - 3.4), (5.9, t + 0.8), (5.0, t + 1.0)], 0.7)
    f += block("spine", (-0.4, t - 1.4, 0), (2.4, 3.8, 1.9))
    f += curve("spine", [(-1.4, t - 0.6, 0), (-3.6, t - 0.2, 0), (-4.6, t - 1.8, 0), (-4.0, t - 3.2, 0)], [0.8, 0.7, 0.5, 0.25], [0.9, 0.8, 0.6, 0.3], sides=4, caps=(False, True))
    f += curve("cord", [(0, -2.8, 0), (0.9, -4.0, 0), (0, -5.0, 0), (-0.9, -4.0, 0), (0, -2.8, 0)], [0.35] * 5, [0.35] * 5, sides=4, caps=(False, False))
    return f


WEAPONS["gather_axe"] = dict(
    jp="伐り斧", tier="common", glow="none", pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.0, 0.0), scale=11.0,
    opts=dict(
        a=(ax_a, None, (), "木こりの片刃斧。広い片刃と四角い峰、付け根に麻縄。"),
        b=(ax_b, None, (), "鉞 (まさかり)。大きく弧を描く広刃と厚い楔の頭、真鍮の鋲。"),
        c=(ax_c, None, (), "鉈斧。短い片刃と反対の鉤、短い柄に紐の輪。山の道具。")))


# ================================================================== 鶴嘴 (道具屋。石と鉱石を採る)
PK_TOP = 17.0


def pk_a():
    """A 両口の鶴嘴: 鶴の翼のように両側へ反る細い嘴。頭の真ん中は四角い鉄の受け、楔で留める。"""
    f = haft(PK_TOP + 0.6, bot=-3.0)
    f += block("spine", (0, PK_TOP - 0.2, 0), (2.4, 2.6, 2.2))
    for s in (1, -1):
        f += curve("main", [(s * 1.0, PK_TOP, 0), (s * 4.0, PK_TOP - 0.4, 0), (s * 6.6, PK_TOP - 1.8, 0), (s * 8.2, PK_TOP - 3.8, 0)],
                   [1.0, 0.85, 0.6, 0.15], [1.0, 0.85, 0.6, 0.15], sides=4, caps=(False, True))
    f += block("trim", (0, PK_TOP + 1.3, 0), (0.8, 0.6, 1.0))
    return f


def pk_b():
    """B 鶴嘴と鍬: 片側は尖った嘴、反対は平たい鍬の刃 (土も掘れる)。鉱夫の両用。柄に布の巻きが二か所。"""
    f = haft(PK_TOP + 0.6, bot=-3.0, wrap=(0.0, 3.0))
    f += prism("wrap", (0, 9.0, 0), (0, 10.6, 0), 1.1, 1.1, sides=6, caps=(False, False), phase=30)
    f += block("spine", (0, PK_TOP - 0.2, 0), (2.6, 2.6, 2.2))
    f += curve("main", [(1.0, PK_TOP, 0), (4.2, PK_TOP - 0.6, 0), (7.4, PK_TOP - 2.8, 0)], [1.0, 0.8, 0.15], [1.0, 0.8, 0.15], sides=4, caps=(False, True))
    f += block("main", (-2.6, PK_TOP - 0.3, 0), (3.6, 1.6, 2.2), taper=(1.0, 1.0))
    f += block("main", (-5.4, PK_TOP - 1.0, 0), (2.4, 1.0, 3.4), rot=rot_z(-14))
    f += block("edge", (-6.7, PK_TOP - 1.4, 0), (0.6, 0.8, 3.6), rot=rot_z(-14))
    return f


def pk_c():
    """C 片口の細い鶴嘴: 一本の長い嘴が下へ反る。峰は小さな鎚の頭 (石を割る)。柄頭に鉄の輪、真鍮の帯。"""
    f = haft(PK_TOP + 0.4, bot=-3.4)
    f += block("spine", (0, PK_TOP - 0.2, 0), (2.4, 2.6, 2.2))
    f += curve("main", [(1.0, PK_TOP, 0), (4.4, PK_TOP + 0.2, 0), (7.6, PK_TOP - 1.4, 0), (9.6, PK_TOP - 4.2, 0)],
               [1.1, 0.95, 0.65, 0.15], [1.1, 0.95, 0.65, 0.15], sides=4, caps=(False, True))
    f += block("guard", (-2.2, PK_TOP - 0.2, 0), (2.4, 2.2, 2.2))
    f += block("trim", (0, PK_TOP - 2.0, 0), (2.4, 0.6, 2.4))
    return f


WEAPONS["gather_pickaxe"] = dict(
    jp="鶴嘴", tier="common", glow="none", pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=11.0,
    opts=dict(
        a=(pk_a, None, (), "両口の鶴嘴。鶴の翼のように両側へ反る細い嘴。"),
        b=(pk_b, None, (), "鶴嘴と鍬。片側は嘴、反対は平たい鍬の刃。鉱夫の両用。"),
        c=(pk_c, None, (), "片口の細い鶴嘴。長い一本の嘴と、峰の小さな鎚頭。")))


# ================================================================== 土掘り (道具屋。土・砂・砂利・粘土を採る)
SV_TOP = 13.0


def sv_a():
    """A 丸先の鋤: 先の丸い鉄の匙形、付け根の筒で柄に差す。柄頭は D の取っ手。"""
    f = prism("grip", (0, -1.0, 0), (0, SV_TOP, 0), 0.9, 0.85, sides=6, caps=(True, True), phase=30)
    f += prism("wrap", (0, 3.0, 0), (0, 6.0, 0), 1.05, 1.05, sides=6, caps=(False, False), phase=30)
    f += curve("grip", [(-1.8, -1.0, 0), (-1.8, -3.4, 0), (0, -4.2, 0), (1.8, -3.4, 0), (1.8, -1.0, 0)], [0.6] * 5, [0.6] * 5, sides=4, caps=(True, True))
    f += block("spine", (0, -1.0, 0), (4.0, 0.9, 1.4))
    f += prism("guard", (0, SV_TOP - 1.0, 0), (0, SV_TOP + 1.6, 0), 1.0, 1.25, sides=6, caps=(False, False))
    f += extrude("main", [(-3.4, SV_TOP + 1.6), (3.4, SV_TOP + 1.6), (3.6, SV_TOP + 5.4), (2.4, SV_TOP + 7.8), (0, SV_TOP + 8.8), (-2.4, SV_TOP + 7.8), (-3.6, SV_TOP + 5.4)], 0.9)
    f += curve("edge", [(-2.4, SV_TOP + 7.8, 0), (0, SV_TOP + 8.9, 0), (2.4, SV_TOP + 7.8, 0)], [0.4] * 3, [0.5] * 3, sides=4, caps=(True, True))
    return f


def sv_b():
    """B 角の踏み鋤: 四角い鉄の刃と、上の縁に足を掛ける折り返し。柄は真っすぐで、柄頭に横木の取っ手。"""
    f = prism("grip", (0, -2.0, 0), (0, SV_TOP, 0), 0.9, 0.85, sides=6, caps=(True, True), phase=30)
    f += prism("wrap", (0, 2.0, 0), (0, 5.0, 0), 1.05, 1.05, sides=6, caps=(False, False), phase=30)
    f += prism("grip", (-2.4, -2.4, 0), (2.4, -2.4, 0), 0.7, 0.7, sides=6, caps=(True, True), hint=(0, 0, 1))
    f += block("guard", (0, SV_TOP + 0.6, 0), (2.2, 2.4, 1.8))
    f += block("main", (0, SV_TOP + 5.0, 0), (6.8, 7.2, 0.8))
    f += block("edge", (0, SV_TOP + 8.8, 0), (6.8, 0.6, 0.6))
    f += block("spine", (0, SV_TOP + 1.6, -0.6), (7.0, 0.8, 1.4))
    for sx in (-2.6, 2.6):
        f += block("trim", (sx, SV_TOP + 3.0, -0.5), (0.6, 0.6, 0.4))
    return f


def sv_c():
    """C 木の鋤に鉄の刃先: 柄と一体の木の板の鋤に、刃先だけ鉄を被せる (昔の農具)。柄に麻縄の巻き。"""
    f = prism("grip", (0, -2.0, 0), (0, SV_TOP + 1.0, 0), 0.95, 0.9, sides=6, caps=(True, True), phase=30)
    f += prism("cord", (0, 3.0, 0), (0, 4.4, 0), 1.1, 1.1, sides=6, caps=(True, True))
    f += curve("grip", [(0, -2.0, 0), (0.0, -3.6, 0), (1.6, -4.4, 0)], [0.8, 0.7, 0.6], [0.8, 0.7, 0.6], sides=4, caps=(False, True))
    f += extrude("grip", [(-2.8, SV_TOP), (2.8, SV_TOP), (3.0, SV_TOP + 6.0), (-3.0, SV_TOP + 6.0)], 1.2, mats=["grip"] * 4)
    f += extrude("main", [(-3.2, SV_TOP + 5.4), (3.2, SV_TOP + 5.4), (3.0, SV_TOP + 8.0), (0, SV_TOP + 8.8), (-3.0, SV_TOP + 8.0)], 1.5)
    f += block("dark", (0, SV_TOP + 3.0, -0.62), (0.3, 4.6, 0.2))
    return f


WEAPONS["gather_shovel"] = dict(
    jp="土掘り", tier="common", glow="none", pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 8.0, 0.0), scale=11.0,
    opts=dict(
        a=(sv_a, None, (), "丸先の鋤。先の丸い鉄の匙形と、D の取っ手。"),
        b=(sv_b, None, (), "角の踏み鋤。四角い鉄の刃と足を掛ける折り返し、横木の取っ手。"),
        c=(sv_c, None, (), "木の鋤に鉄の刃先。木の板の鋤に、刃先だけ鉄。昔の農具。")))
