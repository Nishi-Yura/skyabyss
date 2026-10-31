"""あいだの塔の武器 6 本 (6 職に一つずつ。docs/design/54 の 2.6) の一品一案。どれも「天と淵のあいだ」: 半分が天 (白の大理石と金)、
半分が淵 (黒い石と紫に光る割れ目) で、つなぎ目に主の天秤の印 (金の輪と朝の光の珠) を置く。
あいだの大剣 = 縦に割れた刃と天秤の鍔・引き合いの鎚 = 金の日輪の面と鎖を巻いた黒い拳の面・落ちる月の杖 = 白と黒の角が抱く小さな月・
側の目の弓 = 上は羽、下は鎖の石の腕、握りに目・追う印の短剣 = 紫の縁の黒い反り刃と赤い印の輪・合掌の鈴杖 = 金と黒の半分の鐘が合わさる手。
塗りは塔の塗り (paint_tower.py): main = 白の大理石、edge/spine/acc/guard = 金、dark = 黒い石と紫の割れ目、void = 紫の光、grip/wrap = 夜の紫、gem = 朝の光か紫。
豪華さ: 神器と同じ段 (~800 面)。
"""
from __future__ import annotations

import math

from kit_one import *  # noqa: F401,F403
from kit_one import ell, ringy, gbar, gem, arc, bend, string_and_arrow, inlay_line, CY
from st_kit import torus


def night_pole(y0, y1, r=0.9):
    """夜の紫の柄: 握りの巻き、金の輪、尻は半分金・半分黒い石の突き"""
    f = prism("grip", (0, y0, 0), (0, y1, 0), r, r * 0.95, sides=8, caps=(True, True))
    f += prism("wrap", (0, -1.6, 0), (0, 3.0, 0), r + 0.16, r + 0.16, sides=8, caps=(False, False))
    for y in (-1.8, 3.2):
        f += ringy("trim", y, r + 0.3, 0.45)
    f += ringy("spine", 7.0, r + 0.22, 0.55, sides=8)
    f += ringy("dark", 10.4, r + 0.22, 0.55, sides=8)
    f += spike(BONE, "spine", (-0.25, y0, 0), (-0.2, -1, 0), 1.6, r * 0.7, sides=6, rings=1, hint=(0, 0, 1))
    f += spike(BONE, "dark", (0.25, y0, 0), (0.2, -1, 0), 1.6, r * 0.7, sides=6, rings=1, hint=(0, 0, 1))
    return f


def sigil(c, R=1.4, ax="z", mat="trim"):
    """天秤の印: 金の輪と、真ん中の光の珠と、輪を横切る斜めの棒"""
    f = torus(mat, c, R, 0.2, 0.3, n=14, k=4, ax=ax)
    f += ell("gem", c, (R * 0.45, R * 0.45, R * 0.3), segs=6, rings=2)
    x, y, z = c
    f += prism(mat, (x - R * 0.9, y + R * 0.3, z - 0.05), (x + R * 0.9, y - R * 0.3, z - 0.05), 0.12, 0.12, sides=4, caps=(True, True))
    return f


def tower_balance_sword(stage=0):
    """あいだの大剣: まっすぐな大剣。刃は縦に割れ、左が白の大理石 (金の縁)、右が黒い石 (紫に光る縁)。鍔は天秤の竿 (左に金の皿、右に黒い石の皿)、
    真ん中に天秤の印。柄は夜の紫、柄頭は半分金・半分黒の珠"""
    f = []
    f += prism("grip", (0, -4.6, 0), (0, 3.2, 0), 0.9, 0.92, sides=8, caps=(True, True))
    f += prism("wrap", (0, -3.8, 0), (0, 2.6, 0), 1.04, 1.04, sides=8, caps=(False, False))
    f += ringy("trim", -4.1, 1.1, 0.4)
    f += ell("acc", (-0.3, -5.6, 0), (0.75, 1.0, 1.0), segs=6, rings=3)
    f += ell("dark", (0.3, -5.6, 0), (0.75, 1.0, 1.0), segs=6, rings=3)
    # 鍔: 天秤の竿と皿
    f += block("spine", (0, 3.8, 0), (2.6, 1.1, 1.6), taper=(0.9, 0.9))
    f += prism("acc", (-5.4, 4.4, 0), (5.4, 4.4, 0), 0.32, 0.32, sides=6, caps=(True, True), hint=(0, 1, 0))
    for s, mat in ((-1, "acc"), (1, "dark")):
        x = s * 5.0
        f += prism("cord", (x, 4.2, 0), (x, 2.4, 0), 0.07, 0.07, sides=3, caps=(False, False))
        f += ell(mat, (x, 2.1, 0), (1.6, 0.45, 1.2), segs=8, rings=2)
        f += spike(BONE, mat, (x, 4.6, 0), (0, 1, 0), 0.9, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    f += sigil((0, 5.6, -0.7), R=1.5)
    # 刃: 左は天、右は淵 (真ん中で割れる)
    prof = [(5.8, 2.2, 0.62), (11.0, 2.1, 0.6), (17.0, 1.9, 0.55), (22.0, 1.45, 0.46), (25.0, 0.8, 0.32), (26.8, 0.0, 0.0)]
    f += blade("main", [(y, -w, 0.0, t, t) for y, w, t in prof], colmats=["edge", "main", "main", "main", "main", "edge"])
    f += blade("dark", [(y, 0.0, w, t, t) for y, w, t in prof], colmats=["dark", "dark", "void", "void", "dark", "dark"])
    f += gbar([(-0.45, 7.0, -0.56), (-0.4, 14.0, -0.55), (-0.32, 21.0, -0.5)], r=0.15)
    return f


def tower_pull_hammer(stage=0):
    """引き合いの鎚: 頭は横に寝かせた円柱。+x の打つ面は白の大理石に金の日輪 (朝の光の珠)、-x の面は黒い石の拳に鎖が巻き、紫の割れ目。
    胴の真ん中に金の帯と天秤の印"""
    f = night_pole(-4.2, 10.4, r=1.0)
    f += prism("spine", (0, 10.0, 0), (0, 11.8, 0), 1.1, 1.6, sides=8, caps=(True, True))
    cy = 14.0
    # 天の半分 (+x)
    f += prism("main", (0.0, cy, 0), (3.6, cy, 0), 2.7, 2.9, sides=10, caps=(False, True), hint=(0, 1, 0))
    f += torus("edge", (3.7, cy, 0), 2.9, 0.3, 0.3, n=18, k=4, ax="x")
    f += ell("gem", (3.9, cy, 0), (0.4, 1.0, 1.0), segs=8, rings=3)
    for k in range(10):
        a = math.radians(36 * k)
        d = V(0, math.cos(a), math.sin(a))
        f += spike(BONE, "acc", tuple(V(3.85, cy, 0) + d * 1.3), tuple(V(0.25, 0, 0) + d), 1.1, 0.28, sides=4, rings=1, hint=(1, 0, 0))
    # 淵の半分 (-x): 拳のような角ばった黒い石と鎖
    f += block("dark", (-1.9, cy, 0), (3.8, 5.4, 5.2), taper=(1.0, 1.0))
    for z in (-1.9, -0.65, 0.65, 1.9):
        f += block("dark", (-4.0, cy + 0.6, z), (0.8, 3.8, 1.1), rot=(0, 0, 8))
    f += prism("void", (-3.85, cy - 2.2, -2.62), (-0.4, cy + 1.6, -2.62), 0.12, 0.12, sides=4, caps=(True, True))
    for k in range(5):
        x = -0.6 - k * 0.75
        f += torus("chain", (x, cy, 0), 2.95 if k % 2 == 0 else 2.85, 0.16, 0.16, n=14, k=4, ax="x") if k in (1, 3) else []
    for z in (-1.6, 1.6):
        pts = [(-2.6, cy - 2.7, z), (-2.7, cy - 4.0, z * 1.05), (-2.5, cy - 5.2, z * 1.1)]
        f += curve("chain", pts, [0.22, 0.2, 0.18], [0.12, 0.12, 0.1], sides=4, caps=(True, True), hint=(0, 0, 1))
    # 真ん中の帯と印
    f += torus("trim", (0.0, cy, 0), 2.95, 0.35, 0.45, n=18, k=4, ax="x")
    f += sigil((0.0, cy + 3.3, 0), R=0.9)
    return f


def tower_moon_staff(stage=0):
    """落ちる月の杖: 柄の先で、白い角 (天) と黒い角 (淵) が上へ曲がり、真ん中に浮かぶ小さな月を抱く。月のまわりを金の細い輪が斜めに回り、
    角の下から紫の珠の鎖が垂れる"""
    f = night_pole(-4.0, 13.6)
    f += prism("spine", (0, 13.4, 0), (0, 14.8, 0), 0.9, 1.4, sides=8, caps=(True, True))
    f += torus("trim", (0, 15.0, 0), 1.5, 0.25, 0.4, n=12, k=4, ax="y")
    for s, mat in ((-1, "main"), (1, "dark")):
        pts = [(s * 0.8, 15.2, 0), (s * 2.6, 16.6, 0), (s * 3.3, 18.8, 0), (s * 2.6, 21.0, 0), (s * 1.2, 22.4, 0)]
        f += curve(mat, pts, [0.75, 0.7, 0.55, 0.38, 0.1], [0.65, 0.6, 0.48, 0.32, 0.1], sides=6, caps=(True, True), hint=(0, 0, 1))
        f += curve("edge" if s < 0 else "void", [(s * 2.75, 16.7, -0.5), (s * 3.45, 18.8, -0.42), (s * 2.75, 20.9, -0.32)], [0.12, 0.12, 0.1],
                   [0.12, 0.12, 0.1], sides=4, caps=(True, True), hint=(0, 0, 1))
    # 小さな月 (光る) と斜めの輪
    f += ell("gem", (0, 19.0, 0), (1.6, 1.6, 1.6), segs=10, rings=5)
    f += torus("acc", (0, 19.0, 0), 2.3, 0.14, 0.14, n=18, k=3, ax="x")
    # 紫の珠の鎖
    for s in (-1, 1):
        x = s * 2.2
        f += curve("chain", [(x, 16.0, 0.3), (x * 1.05, 14.6, 0.35), (x, 13.3, 0.3)], [0.12, 0.12, 0.12], [0.08, 0.08, 0.08], sides=3, caps=(True, True),
                   hint=(0, 0, 1))
        f += ell("void", (x, 12.9, 0.3), (0.38, 0.38, 0.38), segs=6, rings=3)
    return f


def tower_eye_bow(stage=0):
    """側の目の弓: 上の腕は天 (金の軸に白い羽)、下の腕は淵 (鎖でつないだ黒い石の節、紫の割れ目)。握りの外に、金の輪の中の大きな目 (光る瞳)"""
    f = []
    f += prism("grip", (5.0, CY - 2.8, 0), (5.0, CY + 2.8, 0), 1.0, 1.0, sides=8, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (5.0, CY - 1.6, 0), (5.0, CY + 1.6, 0), 1.12, 1.12, sides=8, caps=(False, False), hint=(0, 0, 1))
    # 目: 金の輪・白い眼・光る瞳・黒いまぶた
    f += torus("trim", (6.6, CY, 0), 1.7, 0.22, 0.6, n=14, k=4, ax="z")
    f += ell("main", (6.6, CY, -0.2), (1.35, 1.0, 0.5), segs=8, rings=3)
    f += ell("gem", (6.6, CY, -0.62), (0.55, 0.55, 0.2), segs=6, rings=2)
    f += curve("dark", arc(6.6, CY - 0.2, 1.55, 25, 155, 6, z=-0.45), [0.22] * 6, [0.3] * 6, sides=4, caps=(True, True), hint=(0, 0, 1))
    # 上の腕: 金の軸と白い羽 (天)
    up = bend(stage, [(5.0, CY + 2.4, 0), (4.6, CY + 5.6, 0), (3.2, CY + 8.6, 0), (0.8, CY + 10.8, 0), (-2.0, CY + 11.8, 0)])
    f += curve("acc", up, [0.6, 0.55, 0.48, 0.38, 0.24], [0.7, 0.62, 0.52, 0.4, 0.26], sides=5, caps=(False, True), hint=(0, 0, 1))
    for i in range(len(up) - 1):
        (x0, y0, _), (x1, y1, _) = up[i], up[i + 1]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        dx, dy = dx / ln, dy / ln
        nx, ny = dy, -dx
        L = 3.0 - 0.35 * i
        tip = (x0 + dx * L * 0.85 + nx * 1.4, y0 + dy * L * 0.85 + ny * 1.4, 0.08 * i)
        f += feather("cloth", (x0 + nx * 0.3, y0 + ny * 0.3, 0.08 * i), tip, 0.7 - 0.08 * i, th=0.22)
    f += ell("gem", (up[-1][0] - 0.3, up[-1][1] + 0.3, 0), (0.35, 0.35, 0.35), segs=5, rings=2)
    # 下の腕: 黒い石の節を鎖でつなぐ (淵)
    lo = bend(stage, [(5.0, CY - 2.4, 0), (4.6, CY - 5.6, 0), (3.2, CY - 8.6, 0), (0.8, CY - 10.8, 0), (-2.0, CY - 11.8, 0)])
    for i in range(len(lo) - 1):
        a, b = V(*lo[i]), V(*lo[i + 1])
        m0 = a + (b - a) * 0.12
        m1 = a + (b - a) * 0.88
        r = 0.72 - 0.1 * i
        f += prism("dark", tuple(m0), tuple(m1), r, r * 0.85, sides=6, caps=(True, True), hint=(0, 0, 1))
        f += torus("chain", tuple(a + (b - a) * 0.5), r + 0.12, 0.1, 0.1, n=10, k=3, ax="z") if i % 2 else []
    f += spike(BONE, "void", lo[-1], (-0.8, -0.4, 0), 1.0, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    return f + string_and_arrow(stage, "legend", up, lo)


def tower_sigil_dagger(stage=0):
    """追う印の短剣: 少し反った黒い石の刃、縁は紫に光る。刃の根元を赤みの金の輪 (追う印) が囲む。鍔は左が金の小さな翼、右が黒い石の棘"""
    f = []
    f += prism("grip", (0, -3.2, 0), (0, 3.0, 0), 0.8, 0.76, sides=8, caps=(True, True))
    f += prism("wrap", (0, -2.6, 0), (0, 2.4, 0), 0.92, 0.92, sides=8, caps=(False, False))
    f += ringy("trim", -2.8, 1.05, 0.4)
    f += ell("acc", (-0.25, -4.1, 0), (0.6, 0.8, 0.8), segs=6, rings=3)
    f += ell("dark", (0.25, -4.1, 0), (0.6, 0.8, 0.8), segs=6, rings=3)
    # 鍔
    f += block("spine", (0, 3.6, 0), (2.2, 0.9, 1.3), taper=(0.9, 0.9))
    for i in range(3):
        f += feather("cloth", (-1.0, 3.7 - i * 0.3, 0.05 * i), (-3.4 + i * 0.5, 4.8 - i * 0.9, 0.05 * i), 0.55, th=0.2)
    f += curve("acc", [(-0.9, 3.8, 0), (-2.4, 4.4, 0), (-3.4, 4.9, 0)], [0.25, 0.2, 0.1], [0.25, 0.2, 0.1], sides=4, caps=(True, True), hint=(0, 0, 1))
    f += spike(BONE, "dark", (1.0, 3.6, 0), (1, 0.35, 0), 2.6, 0.45, sides=5, rings=1, hint=(0, 0, 1))
    f += spike(BONE, "dark", (1.0, 3.3, 0), (1, -0.5, 0), 1.6, 0.3, sides=4, rings=1, hint=(0, 0, 1))
    # 刃 (反り)
    f += blade("dark", [(4.2, -1.0, 1.0, 0.36, 0.36, 0.0), (7.0, -1.45, 1.35, 0.42, 0.42, 0.25), (10.0, -1.35, 1.2, 0.4, 0.4, 0.7),
                        (13.0, -0.85, 0.8, 0.32, 0.32, 1.3), (15.2, 0, 0, 0, 0, 2.0)], colmats=["void", "dark", "dark", "dark", "dark", "void"])
    # 追う印の輪
    f += torus("trim", (0.05, 5.4, 0), 1.85, 0.16, 0.16, n=16, k=3, ax="y")
    f += ell("gem", (0, 4.4, -0.42), (0.38, 0.38, 0.2), segs=6, rings=2)
    return f


def tower_clap_bell_staff(stage=0):
    """合掌の鈴杖: 柄の先で、金の半分の鐘 (天) と黒い石の半分の鐘 (淵) が掌のように合わさる。合わせ目に金の縫い、下の口から朝の光の舌。
    頂に天秤の印、両脇に小さな鈴"""
    f = night_pole(-4.0, 14.0)
    f += prism("spine", (0, 13.8, 0), (0, 15.2, 0), 0.9, 1.3, sides=8, caps=(True, True))
    # 半分の鐘 (x < 0 は金、x > 0 は黒い石)。真ん中の合わせ目
    prof = ((15.2, 2.7), (15.7, 2.8), (16.6, 2.35), (18.2, 1.95), (19.8, 1.75), (20.9, 1.35), (21.6, 0.7), (21.9, 0.15))
    for s, mat in ((-1, "acc"), (1, "dark")):
        rings = []
        for y, r in prof:
            # 閉じた D の形の断面 (外は丸く、合わせ目は平ら)
            rings.append([V(s * abs(math.cos(math.radians(30 * j))) * r, y, math.sin(math.radians(30 * j)) * r) for j in range(12)])
        f += loft(BONE, mat, rings, caps=(mat, mat))
    f += prism("trim", (0, 15.6, -0.05), (0, 21.6, -0.05), 0.16, 0.16, sides=4, caps=(True, True))
    f += torus("edge", (0, 15.3, 0), 2.85, 0.24, 0.24, n=18, k=4, ax="y")
    f += ell("gem", (0, 14.8, 0), (0.6, 0.6, 0.6), segs=6, rings=3)
    # 頂の印と、両脇の小さな鈴
    f += sigil((0, 23.4, 0), R=1.1)
    for s in (-1, 1):
        x = s * 2.9
        f += prism("cord", (s * 2.2, 17.4, 0), (x, 16.4, 0), 0.07, 0.07, sides=3, caps=(False, False))
        brings = [[V(x + rr * math.cos(math.radians(30 * j)), yy, rr * math.sin(math.radians(30 * j))) for j in range(12)]
                  for yy, rr in ((14.6, 0.5), (14.75, 0.48), (15.4, 0.36), (15.9, 0.22), (16.0, 0.08))]
        f += loft(BONE, "spine" if s < 0 else "dark", brings, caps=("gem", "spine" if s < 0 else "dark"))
    return f


def _w(fn, jp, tier, glow, pose, grip, cen, scale, inlay, stages=1):
    e = dict(jp=jp, tier=tier, glow=glow, pose=pose, grip=grip, cen=cen, scale=scale,
             opts={"a": ((lambda stage=0: fn(stage)), inlay, ("edge",), (fn.__doc__ or "").split(":", 1)[-1].strip())})
    if stages > 1:
        e["stages"] = stages
    return e


ST = dict(pose="whale", grip=(0.0, 1.5, 0.0), cen=(0.0, 9.5, 0.0), scale=9.0)
HM = dict(pose="ember", grip=(0.0, 0.0, 0.0), cen=(0.0, 7.5, 0.0), scale=10.0)
BW = dict(pose="thunder", grip=(5.0, 8.0, 0.0), cen=(2.0, 8.0, 0.0), scale=11.5, stages=4)
DG = dict(pose="whale", grip=(0.0, 0.6, 0.0), cen=(0.0, 4.8, 0.0), scale=15.0)
SW = dict(pose="whale", grip=(0.0, 1.0, 0.0), cen=(0.0, 11.0, 0.0), scale=8.2)
WEAPONS = {
    "tower_balance_sword": _w(tower_balance_sword, "あいだの大剣", "legend", "dawn", inlay=inlay_line(["main"], 99.0, 99.5), **SW),
    "tower_pull_hammer": _w(tower_pull_hammer, "引き合いの鎚", "legend", "void", inlay=inlay_line(["main"], 99.0, 99.5), **HM),
    "tower_moon_staff": _w(tower_moon_staff, "落ちる月の杖", "legend", "dawn", inlay=inlay_line(["grip"], 99.0, 99.5), **ST),
    "tower_eye_bow": _w(tower_eye_bow, "側の目の弓", "legend", "dawn", inlay=inlay_line(["acc"], 99.0, 99.5), **BW),
    "tower_sigil_dagger": _w(tower_sigil_dagger, "追う印の短剣", "legend", "void", inlay=inlay_line(["dark"], 99.0, 99.5), **DG),
    "tower_clap_bell_staff": _w(tower_clap_bell_staff, "合掌の鈴杖", "legend", "dawn", inlay=inlay_line(["grip"], 99.0, 99.5), **ST),
}
