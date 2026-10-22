"""深淵の人型の共通の骨組み (ラフ用)。ash_husk / elder_thrall と同じ比率: 身長 28.8 単位 (人 1.8 ブロック)、胴は厚い輪の積み重ね、腕と脚は太い筒。

座標は作図空間の絶対値 (y が上、前 = -Z)。骨の pivot は肩・股・首の付け根。
  body 胴の根 (0,12,0) / head (0,23.5,0) / arm_l (-4.6,21,0) / arm_r (4.6,21,0) / leg_l (-2.0,12,0) / leg_r (2.0,12,0)
"""
import math

import numpy as np

from ck5 import *  # noqa

HUMAN_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12, 0)), "head": ("body", (0, 23.5, 0)),
               "arm_l": ("body", (-4.6, 21, 0)), "arm_r": ("body", (4.6, 21, 0)),
               "leg_l": ("body", (-2.0, 12, 0)), "leg_r": ("body", (2.0, 12, 0))}


def ring8(y, rx, rz, cz=0.0, n=8):
    return [np.array([rx * math.cos(2 * math.pi * k / n), y, cz + rz * math.sin(2 * math.pi * k / n)]) for k in range(n)]


def torso(F, m="bone", waist=2.6):
    """胴: 骨盤 (y 10.5) から首 (y 23) まで、輪を積んだ厚い胴。胸は前に少し張る。"""
    rings = [ring8(10.4, 2.6, 1.7, 0.0), ring8(12.5, waist, 1.6, 0.0), ring8(15.0, 3.2, 2.0, -0.2),
             ring8(17.5, 3.6, 2.3, -0.4), ring8(20.2, 3.8, 2.1, 0.0), ring8(22.2, 2.0, 1.5, 0.0), ring8(23.2, 1.3, 1.2, 0.0)]
    F += part("torso", loft("body", m, rings, caps=(m, m)))
    return F


def arm(F, side, m="bone", hand_m="bone", r=1.45):
    """腕 (side = -1 左 / 1 右)。肩 → 肘 → 手首 → 手。太さは上腕 1.5、前腕 1.2 (ash_husk と同じ比率)。"""
    x = side * 4.6
    F += part(f"upper_{side}", tube(f"arm_{'l' if side < 0 else 'r'}", m, [(x, 21.0, 0.0), (x + side * 0.4, 16.8, 0.3)], ra=r, rb=r, sides=7, hint=(1, 0, 0)))
    F += part(f"fore_{side}", tube(f"arm_{'l' if side < 0 else 'r'}", m, [(x + side * 0.4, 16.8, 0.3), (x + side * 0.6, 12.6, -0.2)], ra=[1.25, 1.0], sides=7, hint=(1, 0, 0)))
    F += part(f"hand_{side}", ellipsoid(f"arm_{'l' if side < 0 else 'r'}", hand_m, (x + side * 0.6, 11.3, -0.3), (1.15, 1.4, 1.0), segs=7, rings=3))
    return F


def leg(F, side, m="bone", foot_m="bone"):
    x = side * 2.0
    nm = "l" if side < 0 else "r"
    F += part(f"thigh_{nm}", tube(f"leg_{nm}", m, [(x, 12.0, 0.0), (x, 6.6, 0.2)], ra=1.9, rb=1.9, sides=7, hint=(1, 0, 0)))
    F += part(f"shin_{nm}", tube(f"leg_{nm}", m, [(x, 6.6, 0.2), (x, 1.6, -0.2)], ra=[1.5, 1.05], sides=7, hint=(1, 0, 0)))
    F += part(f"foot_{nm}", ellipsoid(f"leg_{nm}", foot_m, (x, 0.9, -0.9), (1.2, 0.8, 2.0), segs=7, rings=3))
    return F


def head(F, m="bone", jaw_m="calc"):
    """頭: 頭蓋の楕円と顎。目の穴は別の材質 (ラフの見どころ)。"""
    F += part("skull", ellipsoid("head", m, (0, 24.6, 0.0), (2.0, 2.4, 2.1), segs=9, rings=4))
    F += part("jaw", ellipsoid("head", jaw_m, (0, 22.7, -0.7), (1.4, 0.8, 1.3), segs=7, rings=2))
    return F


def eyes_pos():
    return [eye_at(-0.7, 24.6, -2.05, 0.32), eye_at(0.7, 24.6, -2.05, 0.32)]


def human_base(F, m="bone", hand_m="bone", foot_m="bone", jaw_m="calc"):
    torso(F, m)
    arm(F, -1, m, hand_m)
    arm(F, 1, m, hand_m)
    leg(F, -1, m, foot_m)
    leg(F, 1, m, foot_m)
    head(F, m, jaw_m)
    return F


def human_humanoid_pal(extra=None):
    mats = {
        "bone": (BONEC, 0.62, dict(crack=0.14, noise=0.22)),
        "calc": (CALC, 0.6, dict(crack=0.12, noise=0.2)),
        "ash": (ASHY, 0.55, dict(crack=0.18, noise=0.26)),
        "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
        "glow": ([(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)], 0.7, dict(crack=0.0, noise=0.06, min=1)),
    }
    if extra:
        mats.update(extra)
    return make_pal(mats, glow=["glow"])


def scale_from_panel(h_units, panel_h, frac=0.66):
    """人の高さ (単位) がパネルの frac になる縮尺。render の scale は 1 単位あたりのピクセル。"""
    return panel_h * frac / h_units
