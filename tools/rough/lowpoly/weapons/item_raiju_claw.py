"""雷獣の爪 (作り直し)。同じ向きに反る 3 本の爪 (中央が長く、左右は短い)、鋼の指節の台と銅の輪、手首の焦げた羽の房。
握り y 2.5 (変えない)、y -0.5..17、x ±5。階級: 通常 = 爪 3 + 羽 2 / レア = 親指の副爪 + 銅の輪 + 羽 4 / 伝説 = 台の後ろに風切羽の扇 + 爪の間の稲妻 + 大きな爪。"""
from __future__ import annotations

from st_kit import *  # noqa: F401,F403
from st_kit import plume, torus, band, bolt, vein_x
from paint import line_mask, spot_mask

TITAN = "thunder"
GRIP = (0.0, 2.5, 0.0)
NAME = "雷獣の爪"
ID = "raiju_claw"
EDGE = ("edge",)


def cur(y0, k):
    return lambda y: k * np.maximum(0.0, y - y0) ** 2 / 2.0


def talon(x0, y0, y1, k, w, mat="main"):
    c = cur(y0, k)
    ys = [y0, y0 + (y1 - y0) * 0.38, y0 + (y1 - y0) * 0.72, y1]
    st = [(ys[0], -w, w, 0.9, 0.9, x0 + c(ys[0])), (ys[1], -w * 1.15, w * 1.15, 0.9, 0.9, x0 + c(ys[1])),
          (ys[2], -w * 0.8, w * 0.8, 0.7, 0.7, x0 + c(ys[2])), (ys[3], 0, 0, 0, 0, x0 + c(ys[3]) + 0.2)]
    return blade(mat, st)


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    f += prism("grip", (0, 0.2, 0), (0, 5.0, 0), 0.9, 0.9, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (0, 1.2, 0), (0, 3.8, 0), 1.05, 1.05, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += block("spine", (0, -0.1, 0), (2.4, 0.9, 2.2), taper=(0.7, 0.7), skip=("u",))
    # 台: 鋼の指節の板 + 光る石
    f += block("guard", (0, 5.6, 0), (7.0, 1.4, 2.4), taper=(0.92, 0.9))
    f += block("gem", (0, 5.7, -0.4), (1.2, 1.0, 2.4), taper=(0.8, 0.8))
    # 3 本の爪: 同じ向き (+x) へ反る。中央が長い
    f += talon(0.0, 6.2, 17.0 + 1.0 * (t == 2), 0.05, 1.6)
    f += talon(-3.0, 6.0, 13.0 + (t == 2), 0.05, 1.1)
    f += talon(3.0, 6.0, 12.2 + (t == 2), 0.05, 1.1)
    # 手首の羽の房: 台から下へ流れる焦げた風切羽
    f += plume((-1.6, 5.0, 0.7), (-3.6, 0.6, 0.7), 1.2, 0.4, bend=-0.4, scorch=1)
    f += plume((1.6, 5.0, -0.7), (3.6, 0.6, -0.7), 1.2, 0.4, bend=0.4, scorch=1)
    if t >= 1:
        f += block("trim", (0, 4.8, 0), (3.4, 0.7, 2.6))
        f += block("trim", (0, 0.9, 0), (2.6, 0.7, 2.4))
        f += plume((-0.6, 5.0, 1.1), (-1.8, -0.8, 1.1), 1.0, 0.4, bend=-0.3, big=True, scorch=1)
        f += plume((0.6, 5.0, -1.1), (1.8, -0.8, -1.1), 1.0, 0.4, bend=0.3, scorch=1)
        # 親指の副爪 (外へ短く)
        f += spike(BONE, "edge", (-3.8, 6.4, 0), (-0.8, 1, 0), 3.6, 0.8, sides=4, rings=2, hint=(0, 0, 1))
    if t == 2:
        # 台の後ろの風切羽の扇 (広がる翼) と爪の間の稲妻
        for sg in (-1, 1):
            f += plume((sg * 1.6, 6.2, 0.0), (sg * 6.2, 11.0, 0.0), 1.3, 0.45, bend=-sg * 0.7, big=True, scorch=1)
            f += plume((sg * 1.6, 6.0, 0.0), (sg * 7.0, 7.6, 0.0), 1.1, 0.45, bend=-sg * 0.4, scorch=1)
        f += bolt([(-1.4, 11.2, 0.0), (-0.4, 9.6, 0.0), (-1.8, 8.8, 0.0), (-0.6, 7.2, 0.0)], 0.3, "gem")
    return f


def inlay(env, kind):
    c0 = cur(6.2, 0.05)
    zz = lambda y: c0(y) + vein_x(y, 6.2, 0.3, 3.0)
    if kind == "rare":
        return line_mask(env, ["main"], zz, 6.8, 10.4, 0.42)
    if kind == "legend":
        return line_mask(env, ["main"], zz, 6.8, 15.0, 0.42) | spot_mask(env, ["gem"], (0, 5.7, -0.4), 1.6)
    if kind == "p5":
        return line_mask(env, ["main"], zz, 6.8, 16.0, 0.42, dash=(2.0, 1.2))
    if kind == "p10":
        return line_mask(env, ["main"], zz, 6.4, 16.6, 0.5) | (np.isin(env["mats"], ["edge"]) & env["convex"])
    return env["lit"] < -9


import wd2  # noqa: E402
wd2.install(ID, __name__)   # D2 (整え): 面取り + 輪 + 宝石 + 光る線
