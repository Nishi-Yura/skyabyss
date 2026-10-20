"""番兵長の鉾 (岩鯨・作り直し)。石の太い柄に縄と護符、幅のある牙の穂先 (A の量塊 + C の使い込み)。
階級で輪郭が変わる: 通常 = 牙の穂先と石の襟 / レア = 下へ巻く大きな肋の鉤 2 本 / 伝説 = 鉤 + 穂の背の背びれの冠 + 襟から開く石の翼。
握り y 3 (変えない)。y -3..20。"""
from __future__ import annotations

from wh_kit import *  # noqa: F401,F403
from wh_kit import rag, charm, tooth
from wt_common import blade_inlay, cx_interp

TITAN = "whale"
GRIP = (0.0, 3.0, 0.0)
NAME = "番兵長の鉾"
ID = "captain_pike"
EDGE = ("edge",)
CX = cx_interp([12.2, 14, 16.6, 19.5], [0, 0.2, 0.8, 1.7])


def faces(r="common"):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    f = []
    f += prism("grip", (0, -2.0, 0), (0, 0.4, 0), 1.1, 1.1, sides=6, caps=(False, False))
    f += prism("wrap", (0, 0.4, 0), (0, 5.2, 0), 1.3, 1.3, sides=6, caps=(False, False))
    f += prism("grip", (0, 5.2, 0), (0, 11.0, 0), 1.1, 1.1, sides=6, caps=(False, True))
    f += block("guard", (0, -2.5, 0), (2.8, 1.0, 2.8), taper=(0.6, 0.6), skip=("u",))
    # 襟: 石の段と苔のこぶ
    f += block("guard", (0, 11.2, 0), (4.4, 2.0, 3.6), taper=(0.85, 0.85))
    f += block("gem", (0, 12.4, -0.4), (1.5, 1.2, 1.6), taper=(0.8, 0.8))
    # 穂先: 幅のある反った牙
    st = [(12.2, -2.2, 2.2, 1.0, 1.0, 0.0), (14.0, -2.7, 2.5, 1.1, 1.1, 0.2), (16.6, -1.9, 1.5, 0.8, 0.8, 0.8),
          (18.4, -0.9, 0.8, 0.5, 0.5, 1.3), (19.6, 0, 0, 0, 0, 1.7)]
    f += blade("main", st)
    f += rag((1.6, 10.2, -1.2), 5.4, 1.3, sign=1, tilt=0.8)
    f += charm(-1.6, 9.6, 2.4)
    if t >= 1:
        # 肋の鉤: 2 本、下へ大きく巻く (輪郭の主役)
        for s in (1, -1):
            f += curve("edge", [(s * 1.6, 12.4, 0), (s * 4.6, 12.8, 0), (s * 6.4, 11.0, 0), (s * 6.2, 7.8, 0)],
                       [1.6, 1.4, 1.0, 0.0], [1.7, 1.5, 1.05, 0.0], sides=4)
        f += block("trim", (0, 12.6, 0), (5.4, 0.9, 3.8), taper=(0.9, 0.9))
        f += block("trim", (0, 2.2, 0), (2.9, 1.0, 2.9))
        f += charm(1.8, 8.0, 2.0, "main")
    if t == 2:
        # 背びれの冠: 穂先の背に並ぶ骨の板 2 枚と、襟から開く石の翼
        for k, (y, h) in enumerate(((13.4, 3.8), (16.4, 2.8))):
            f += at(extrude("edge", [(0, 0), (1.6, h * 0.25), (0.6, h), (-1.0, h * 0.5)], 0.8), None, (-2.8 + 0.3 * k, y, 0))
        for s in (1, -1):
            f += block("spine", (s * 3.0, 10.0, 0), (1.7, 3.4, 1.7), rot=rot_z(-s * 18), taper=(0.5, 0.6))
    return f


inlay = blade_inlay(["main"], CX, (12.4, 14.0), (14.4, 17.2), 12.4, 18.6, 0.5,
                    extra_spots=[(["gem"], (0, 12.4, -0.4), 1.6)])
