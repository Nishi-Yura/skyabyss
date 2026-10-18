"""3 案共通の組み立て。案ごとの違いは cfg (板の寸法・色・飾り) だけ。"""
from __future__ import annotations

from parts import *  # noqa

ASH = np.array([(92, 88, 88), (134, 130, 126), (172, 168, 162), (206, 202, 194), (234, 230, 222)])
HEART = np.array([(46, 14, 14), (84, 26, 20), (130, 44, 24), (176, 70, 30), (222, 108, 44), (250, 160, 70)])
HEART3 = np.array([(34, 28, 30), (62, 40, 38), (96, 50, 44), (132, 62, 48), (168, 84, 60)])
VESS = np.array([(40, 20, 18), (70, 32, 24), (104, 46, 28), (140, 64, 32), (176, 88, 40)])
VESS3 = np.array([(50, 44, 44), (84, 74, 70), (120, 108, 100), (160, 148, 138), (196, 186, 174)])


def ang(s, d):
    return math.degrees(math.acos(max(-1, min(1, float(np.dot(s, d))))))


def make_opt(oid, cfg):
    Df = cfg["Df"]
    SH = {k: dict(v, floor_depth=Df) for k, v in cfg["SH"].items()}
    seeds = cfg["seeds"]
    salt = cfg.get("salt", 1)

    def sel2(i, s, r):
        cut = cfg.get("cut2", 0.20) + (r - 0.5) * 0.16
        if s[1] > cut or ang(s, V(0, 0, -1)) < cfg.get("win2", 31):
            return None
        if s[1] > cut - 0.30:
            return dict(top=-1.0 - 0.9 * (s[1] - (cut - 0.3)) / 0.3, dome=cfg["SH"][2]["dome"] * 0.6,
                        tilt=cfg.get("rim_tilt", 0.12), jit_h=0.3)
        return {}

    def sel3(i, s, r):
        if ang(s, V(0, 0, -1)) < cfg.get("win3", 34) or ang(s, V(0, 1, 0)) < 30 or r < cfg.get("hole3", 0.10):
            return None
        return {}

    def build(phase, pres):
        F = []
        hs = cfg.get("heart", (10, 4))
        if phase == 1:
            F += shell(1, SH[1], "crust", "crust_glow", seeds, lambda i, s, r: {}, salt=salt)
            F += stalactites("drips", [(-2.9, -1.3, 7.0, 3.3), (0.5, -3.0, 7.0, 3.9), (2.6, 1.1, 7.0, 3.5)], 3, 1.0)
            F += cinders(1)
        elif phase == 2:
            F += shell(2, SH[2], "crust", "crust_glow", seeds, sel2, salt=salt)
            F += heart_ball("heart", "heart", segs=hs[0], rings=hs[1])
            F += veins("heart_vein", "vein", 5, seed=2)
            F += vessel(2)
            F += core_sphere(2, segs=10, rings=4)
            F += core_ring(2, r=3.6, prongs=cfg.get("prongs", 4))
            F += strands_p2(thick=cfg.get("strand_thick", 1.0))
            F += drops_p2()
            F += cinders(2)
        else:
            F += shell(3, SH[3], "ash_shell", "ash_shell", seeds, sel3, salt=salt)
            F += heart_ball("heart", "heart", segs=hs[0], rings=hs[1])
            F += veins("heart_vein", "vein", 3, seed=3, r=0.3)
            F += vessel(3)
            F += core_sphere(3, segs=8, rings=3)
            F += core_ring(3, r=2.3, prongs=2, plen=1.6, gaps=(1, 4), w=0.6)
            F += stalactites("ash_drips", [(-3.3, -1.5, 7.0, 3.6), (-1.3, 3.1, 7.0, 3.6), (0.2, -0.2, 6.0, 2.4), (2.0, -2.8, 7.0, 4.8), (3.5, 1.0, 7.0, 3.8)],
                             3, 0.9, mat="plate")
            F += cinders(3)
        return F

    ctx = dict(
        floor_depth=Df, plate=[cfg["plate"], cfg["plate"], cfg.get("ash", ASH)], plate_base=cfg["plate_base"], pit=cfg["pit"],
        glow_w=cfg["glow_w"], heat_w=cfg["heat_w"], floor_v=cfg["floor_v"], floor_cool=cfg["floor_cool"],
        heart=[HEART, HEART, HEART3], heart_warm=[0.0, 1.0, 0.0],
        vessel=[VESS, VESS, VESS3], vessel_band=[0.0, 0.3, 0.0],
        core_v=[0.9, 1.0, 0.7], drip_cool_y=[0, 4.5, 99], stalac_tip_y=[4.6, 4.6, -99],
        sparkle=cfg.get("sparkle", 0.0),
    )
    return dict(id=oid, build=build, ctx=ctx)
