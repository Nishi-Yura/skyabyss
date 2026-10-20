"""羽守の鉤槍 (作り直し)。大きな反りの銅の刃が主役、羽は副: 継ぎ手の輪から垂れる焦げた風切羽の房、背の鉤。
握り y 7 (変えない)、y -2.5..21。階級: 通常 = 刃 + 鉤 + 輪 + 羽 2 / レア = 背の副鉤 + 羽 4 + 銅の巻き + 筋 / 伝説 = 刃の後ろに風切羽の翼の冠 + 大きな輪。"""
from __future__ import annotations

from st_kit import *  # noqa: F401,F403
from st_kit import plume, torus, band, vein_x
from paint import line_mask, spot_mask

TITAN = "thunder"
GRIP = (0.0, 7.0, 0.0)
NAME = "羽守の鉤槍"
ID = "warden_glaive"
EDGE = ("edge",)
HY = [13.0, 15.0, 17.2, 19.2, 21.0]
HX = [0.0, 0.4, 1.4, 3.0, 4.8]


def hcx(y):
    return np.interp(y, HY, HX)


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    f += prism("grip", (0, -2.2, 0), (0, 12.6, 0), 0.8, 0.8, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += prism("wrap", (0, 5.0, 0), (0, 9.0, 0), 0.95, 0.95, sides=6, caps=(False, False), hint=(0, 0, 1))
    f += spike(BONE, "edge", (0, -2.2, 0), (0, -1, 0), 1.4, 1.0, sides=6, rings=1, hint=(0, 0, 1))
    # 継ぎ手: 銅の輪 (正面を向く) と光る石
    f += torus("edge", (0, 12.6, 0), 1.55, 0.38, 0.55, n=6, k=4, ax="z")
    f += block("gem", (0, 12.6, 0), (0.9, 0.9, 1.6), taper=(0.9, 0.9))
    # 刃: 大きな反り (先が +x へ)
    head = [(13.0, -1.2, 1.2, 0.9, 0.9, 0.0), (15.0, -2.3, 2.3, 1.0, 1.0, 0.4), (17.2, -2.3, 2.0, 0.9, 0.9, 1.4),
            (19.2, -1.5, 1.2, 0.7, 0.7, 3.0), (21.0, 0, 0, 0, 0, 4.8)]
    f += blade("main", head)
    # 背の鉤 (-x): 薄い銅の反り
    f += curve("edge", [(-0.8, 14.6, 0), (-3.0, 15.4, 0), (-4.6, 14.0, 0), (-4.2, 11.4, 0)],
               [1.2, 1.1, 0.8, 0.2], [0.8, 0.75, 0.6, 0.25], sides=4, caps=(False, False), hint=(0, 0, 1))
    # 羽の房: 継ぎ手の下へ流れる焦げた風切羽
    f += plume((0.8, 12.0, 0.6), (3.6, 6.8, 0.6), 1.2, 0.4, bend=0.5, scorch=1)
    f += plume((-0.8, 12.0, -0.6), (-3.2, 7.4, -0.6), 1.1, 0.4, bend=-0.4, scorch=1)
    if t >= 1:
        f += plume((0.4, 11.6, 0.9), (1.6, 5.2, 0.9), 1.0, 0.4, bend=0.3, big=True, scorch=1)
        f += plume((-0.4, 11.6, -0.9), (-1.4, 5.0, -0.9), 0.9, 0.4, bend=-0.3, scorch=1)
        f += block("trim", (0, 11.7, 0), (2.6, 0.7, 2.4))
        f += block("trim", (0, 4.4, 0), (2.4, 0.7, 2.2))
        # 腹の副鉤 (+x、小さく)
        f += curve("edge", [(0.8, 14.0, 0), (2.6, 13.4, 0), (3.6, 11.8, 0), (3.2, 10.4, 0)],
                   [0.7, 0.55, 0.4, 0.12], [0.6, 0.5, 0.4, 0.15], sides=4, caps=(False, False), hint=(0, 0, 1))
    if t == 2:
        # 風切羽の翼の冠: 刃の後ろから 2 枚ずつ扇に
        for sg in (-1, 1):
            f += plume((sg * 0.8, 13.6, 0.0), (sg * 6.2, 18.8, 0.0), 1.3, 0.45, bend=-sg * 0.9, big=True, scorch=1)
        f += torus("trim", (0, 12.6, 0), 3.0, 0.3, 0.45, n=6, k=4, ax="z")
    return f


def inlay(env, kind):
    cx = lambda y: hcx(y) + vein_x(y, 13.0, 0.35, 3.0)
    if kind == "rare":
        return line_mask(env, ["main"], cx, 13.4, 17.0, 0.42)
    if kind == "legend":
        return line_mask(env, ["main"], cx, 13.4, 20.2, 0.42) | spot_mask(env, ["gem"], (0, 12.6, 0), 1.8)
    if kind == "p5":
        return line_mask(env, ["main"], cx, 13.4, 20.4, 0.42, dash=(2.0, 1.2))
    if kind == "p10":
        return line_mask(env, ["main"], cx, 13.0, 20.8, 0.5) | (np.isin(env["mats"], ["edge"]) & env["convex"] & (env["Y"] > 13.5))
    return env["lit"] < -9
