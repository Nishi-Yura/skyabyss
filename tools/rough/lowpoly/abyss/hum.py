"""巨大な人型 (歪んだ王・生き残りでない大型) の共通部品。human5 と同じ比率 (人 28.8 単位) を S 倍する。

座標は「人の単位」(人 28.8) で書き、P() で S 倍する。骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r。
body (0,12,0)・head (0,23.5,0)・arm (±4.6,21,0)・leg (±2,12,0) を S 倍。
"""
import math

import numpy as np

from ab_common import *  # noqa
import ab_common as AC


def bones(S):
    p = lambda x, y, z: (x * S, y * S, z * S)  # noqa: E731
    return {"root": (None, (0, 0, 0)), "body": ("root", p(0, 12, 0)), "head": ("body", p(0, 23.5, 0)),
            "jaw": ("head", p(0, 22.7, -0.7)),
            "arm_l": ("body", p(-4.6, 21, 0)), "arm_r": ("body", p(4.6, 21, 0)),
            "leg_l": ("body", p(-2.0, 12, 0)), "leg_r": ("body", p(2.0, 12, 0))}


class H:
    """S 倍の座標の道具。"""

    def __init__(self, S):
        self.S = S

    def P(self, x, y, z):
        return (x * self.S, y * self.S, z * self.S)

    def ring(self, y, rx, rz, cz=0.0, n=8, cx=0.0):
        S = self.S
        return [np.array([cx * S + rx * S * math.cos(2 * math.pi * k / n), y * S, cz * S + rz * S * math.sin(2 * math.pi * k / n)]) for k in range(n)]

    def torso(self, F, m="bone", waist=2.6, chest=3.8, depth=2.3, hunch=0.0):
        """胴。hunch は胸を前へ出す量 (人の単位)。"""
        rings = [self.ring(10.4, 2.6, 1.7, 0.0), self.ring(12.5, waist, 1.6, 0.0), self.ring(15.0, chest * 0.85, depth * 0.87, -0.2 - hunch * 0.3),
                 self.ring(17.5, chest * 0.95, depth, -0.4 - hunch * 0.6), self.ring(20.2, chest, depth * 0.92, -hunch), self.ring(22.2, 2.0, 1.5, -hunch * 0.6),
                 self.ring(23.2, 1.3, 1.2, -hunch * 0.4)]
        F += part("torso", loft("body", m, rings, caps=(m, m)))
        return F

    def arm(self, F, side, m="bone", hand_m="bone", r=1.45, ln=1.0, out=0.0, fwd=0.0, tag=""):
        """腕。ln = 前腕の長さの倍率 (手の先が下へ伸びる)。out = 外への開き、fwd = 前へ出す量。"""
        S = self.S
        b = "arm_l" if side < 0 else "arm_r"
        x = side * 4.6
        e = (x + side * (0.4 + out * 0.5), 16.8, 0.3 - fwd * 0.4)
        wy = 16.8 - 4.2 * ln
        w = (x + side * (0.6 + out), wy, -0.2 - fwd)
        F += part(f"upper{tag}{side}", tube(b, m, [self.P(x, 21.0, 0.0), self.P(*e)], ra=r * S, sides=7, caps=(True, True)))
        F += part(f"fore{tag}{side}", tube(b, m, [self.P(*e), self.P(*w)], ra=[1.25 * S, 1.0 * S], sides=7, caps=(True, True)))
        F += part(f"hand{tag}{side}", ellipsoid(b, hand_m, self.P(w[0], w[1] - 1.1, w[2] - 0.1), (1.15 * S, 1.4 * S, 1.0 * S), segs=7, rings=3))
        return w

    def leg(self, F, side, m="bone", foot_m="bone", ln=1.0):
        S = self.S
        nm = "l" if side < 0 else "r"
        x = side * 2.0
        F += part(f"thigh_{nm}", tube(f"leg_{nm}", m, [self.P(x, 12.0, 0), self.P(x, 6.6, 0.2)], ra=1.9 * S, sides=7, caps=(True, True)))
        F += part(f"shin_{nm}", tube(f"leg_{nm}", m, [self.P(x, 6.6, 0.2), self.P(x, 1.6, -0.2)], ra=[1.5 * S, 1.05 * S], sides=7, caps=(True, True)))
        F += part(f"foot_{nm}", ellipsoid(f"leg_{nm}", foot_m, self.P(x, 0.9, -0.9), (1.2 * S, 0.8 * S, 2.0 * S), segs=7, rings=3))

    def head(self, F, m="bone", jaw_m="calc", s=1.0, dy=0.0, dz=0.0, hollow=True):
        S = self.S
        F += part("skull", ellipsoid("head", m, self.P(0, 24.6 + dy, dz), (2.0 * S * s, 2.4 * S * s, 2.1 * S * s), segs=9, rings=4))
        F += part("jaw", ellipsoid("jaw", jaw_m, self.P(0, 22.7 + dy, -0.7 * s + dz), (1.4 * S * s, 0.8 * S * s, 1.3 * S * s), segs=7, rings=2))
        if hollow:
            for sx in (-1, 1):
                F += part(f"sock{sx}", ellipsoid("head", "hollow", self.P(sx * 0.7 * s, 24.9 + dy, -1.85 * s + dz), (0.55 * S * s, 0.6 * S * s, 0.5 * S * s), segs=6, rings=2))
        return [eye_at(sx * 0.7 * S * s, (24.9 + dy) * S, (-2.0 * s + dz) * S, 0.34 * S) for sx in (-1, 1)]

    def base(self, F, m="bone", hand_m="calc", foot_m="calc", jaw_m="calc", ln=1.0, hunch=0.0, chest=3.8, depth=2.3, waist=2.6, r_arm=1.45, hs=1.0, hollow=True):
        self.torso(F, m, waist=waist, chest=chest, depth=depth, hunch=hunch)
        self.arm(F, -1, m, hand_m, r=r_arm, ln=ln)
        self.arm(F, 1, m, hand_m, r=r_arm, ln=ln)
        self.leg(F, -1, m, foot_m)
        self.leg(F, 1, m, foot_m)
        return self.head(F, m, jaw_m, s=hs, hollow=hollow)


def king_rows(cen, sc, ph=360, ws=((0.5, "周が進んだ姿 (中)"), (1.0, "周が進んだ姿 (深)"))):
    r = [("前", "idle", 180, 8, ph, cen, sc, None),
         ("3/4", "idle", 212, 14, ph, cen, sc, None),
         ("横", "idle", 270, 6, ph, cen, sc, None)]
    for w, h in ws:
        r.append((h, "idle", 212, 12, ph, cen, sc, {"warp": w}))
    return r
