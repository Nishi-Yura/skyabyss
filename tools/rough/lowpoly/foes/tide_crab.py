"""潮招きの大蟹 (tide_crab)。潮の浜の名のある強敵。Lv 18、まれに出る。
一体一案: 幅 2.5 ブロックの大きな潮招き (片方のはさみだけが巨大な蟹)。暗い青緑の平たい甲羅に、フジツボと白い珊瑚の枝、垂れた海藻。
右のはさみは体ほどもある朱色の大ばさみで、表に珊瑚とフジツボがこびりつく。左は小さなはさみ。
細い眼柄の先の目が海の青緑に光る。脚は四対、先が尖る。
動き: 大ばさみを振り上げて (予告 1.0 秒、前の帯) 叩きつける。泡を吹く (予告 1.2 秒、前の扇) と、扇の中の人は押し流され鈍る。
"""
import math

import fk
from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

fk.GLOW["glow_sea"] = (120, 240, 210)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 14.0, 0)),
         "claw_big": ("body", (-11.0, 14.0, -6.0)), "pincer": ("claw_big", (-21.0, 20.0, -27.0)),
         "claw_small": ("body", (10.0, 13.0, -7.0)),
         "eye_l": ("body", (4.0, 18.0, -9.0)), "eye_r": ("body", (-4.0, 18.0, -9.0)),
         "leg_la": ("body", (12.0, 13.0, 0)), "leg_lb": ("body", (12.0, 13.0, 0)),
         "leg_ra": ("body", (-12.0, 13.0, 0)), "leg_rb": ("body", (-12.0, 13.0, 0))}
SHELL = [(14, 30, 36), (22, 46, 54), (34, 66, 74), (52, 90, 96), (76, 116, 118)]
CLAW = [(90, 26, 18), (140, 46, 26), (186, 74, 38), (218, 112, 60), (240, 152, 96)]
BARN = [(150, 146, 132), (196, 192, 178), (230, 226, 214)]
CORAL = [(170, 120, 110), (214, 162, 146), (240, 204, 186)]
WEED = [(14, 36, 30), (24, 58, 44), (40, 84, 60)]
PAL = {"mats": {"shell": mat(SHELL, 0.6, noise=0.22, crack=0.05), "claw": mat(CLAW, 0.62, noise=0.18), "barn": mat(BARN, 0.62, crack=0.12),
                "coral": mat(CORAL, 0.64, noise=0.2), "weed": mat(WEED, 0.58, noise=0.3), "leg": mat(SHELL[1:], 0.6, noise=0.15),
                "dark": mat([(6, 10, 12), (14, 20, 24), (26, 34, 38)], 0.5, min=0)},
       "skull_bone": "body", "face_mat": "shell"}


def barnacle(F, pid, bone, c, r):
    x, y, z = c
    F += part(pid, tube(bone, "barn", [(x, y, z), (x, y + r * 1.1, z)], ra=[r, r * 0.55], sides=6, hint=(1, 0, 0)))
    F += part(pid + "h", ellipsoid(bone, "dark", (x, y + r * 1.12, z), (r * 0.4, 0.15, r * 0.4), segs=5, rings=1))


def build(k):
    F = []
    # 甲羅: 横に広く平たい。前の縁は少し尖り、下に腹
    F += part("carapace", ellipsoid("body", "shell", (0, 16.6, 1.0), (15.0, 7.4, 11.0), segs=12, rings=4))
    F += part("rim", ellipsoid("body", "shell", (0, 14.2, 1.0), (16.0, 2.2, 11.8), segs=12, rings=2))
    F += part("belly", ellipsoid("body", "leg", (0, 11.6, 1.0), (11.0, 2.4, 8.0), segs=10, rings=2))
    F += part("face", box("body", "dark", (0, 13.6, -10.4), (8.0, 2.4, 1.4), taper=(0.8, 1.0)))
    # 背のフジツボと珊瑚の枝、垂れた海藻
    for i, (x, z, r) in enumerate(((-6, 4, 1.4), (-3, 8, 1.1), (5, 6, 1.5), (8, 1, 1.0), (1, -4, 1.2), (-9, -1, 1.0), (10, 8, 0.9))):
        y = 16.6 + 7.4 * math.sqrt(max(0.0, 1 - (x / 15.0) ** 2 - ((z - 1) / 11.0) ** 2)) - 0.4
        barnacle(F, f"barn{i}", "body", (x, y, z), r)
    for i, (x, z, h, br) in enumerate(((3.0, 3.0, 7.0, 1), (-1.5, 5.0, 5.0, -1))):
        y0 = 23.4
        F += part(f"coral{i}", tube("body", "coral", [(x, y0, z), (x + br * 0.6, y0 + h * 0.6, z + 0.4), (x + br * 1.6, y0 + h, z + 1.2)], ra=[0.8, 0.6, 0.25], sides=5, hint=(1, 0, 0)))
        F += part(f"coralb{i}", tube("body", "coral", [(x + br * 0.6, y0 + h * 0.5, z + 0.3), (x - br * 1.8, y0 + h * 0.85, z - 0.6)], ra=[0.5, 0.2], sides=4, hint=(1, 0, 0)))
    for i, (x, z, L) in enumerate(((-12.0, 6.0, 5.0), (13.0, 3.0, 4.0), (-4.0, 11.4, 4.4), (6.0, 11.0, 3.6))):
        F += part(f"weed{i}", tube("body", "weed", [(x, 15.4, z), (x * 1.03, 15.4 - L * 0.5, z * 1.04), (x * 1.04, 15.4 - L, z * 1.02)], ra=[0.5, 0.35, 0.08], sides=4, hint=(1, 0, 0)))
    # 眼柄と光る目
    for sx, b in ((1, "eye_l"), (-1, "eye_r")):
        F += part(f"stalk{sx}", tube(b, "leg", [(sx * 4.0, 17.0, -9.0), (sx * 4.8, 23.0, -10.4)], ra=[0.6, 0.45], sides=5, hint=(1, 0, 0)))
        F += part(f"eye{sx}", ellipsoid(b, "glow_sea", (sx * 4.9, 23.8, -10.6), (1.0, 1.0, 1.0), segs=6, rings=2))
    # 大ばさみ (右): 腕・掌・動かぬ指・動く指 (pincer)。珊瑚とフジツボがこびりつく
    F += part("bigarm", tube("claw_big", "claw", [(-11.0, 14.0, -6.0), (-17.0, 15.0, -12.0), (-20.0, 16.0, -17.0)], ra=[3.0, 3.4, 3.8], sides=7, hint=(0, 1, 0)))
    F += part("palm", ellipsoid("claw_big", "claw", (-21.0, 17.4, -22.0), (6.4, 7.6, 8.6), segs=10, rings=4))
    F += part("finger", tube("claw_big", "claw", [(-20.6, 14.6, -28.0), (-20.0, 13.6, -33.0), (-19.0, 14.6, -37.0)], ra=[3.6, 2.4, 0.6], rb=[2.4, 1.6, 0.45], sides=6, hint=(1, 0, 0)))
    for i, z in enumerate((-30.0, -33.0)):
        F += part(f"tooth{i}", box("claw_big", "barn", (-20.2, 16.0, z), (1.2, 1.2, 1.0), taper=(0.2, 0.4)))
    F += part("dactyl", tube("pincer", "claw", [(-21.0, 20.0, -27.0), (-20.6, 20.4, -32.0), (-19.4, 18.4, -37.4)], ra=[3.2, 2.2, 0.5], rb=[2.2, 1.5, 0.4], sides=6, hint=(1, 0, 0)))
    for i, (x, y, z, r) in enumerate(((-26.8, 19.0, -21.0, 1.0), (-22.0, 24.6, -20.0, 0.9), (-27.2, 15.0, -24.0, 0.8))):
        barnacle(F, f"cbarn{i}", "claw_big", (x, y, z), r)
    F += part("claw_coral", tube("claw_big", "coral", [(-24.0, 23.6, -23.0), (-25.0, 26.6, -22.0), (-26.6, 28.0, -20.6)], ra=[0.7, 0.5, 0.2], sides=4, hint=(1, 0, 0)))
    # 小さなはさみ (左)
    F += part("smallarm", tube("claw_small", "claw", [(10.0, 13.0, -7.0), (13.0, 13.0, -11.0)], ra=[1.2, 1.1], sides=6, hint=(0, 1, 0)))
    F += part("smallpalm", ellipsoid("claw_small", "claw", (13.6, 13.2, -13.6), (1.8, 2.0, 2.6), segs=7, rings=2))
    F += part("smallfinger", tube("claw_small", "claw", [(13.6, 12.6, -15.6), (13.4, 12.6, -18.0)], ra=[1.0, 0.3], sides=5, hint=(1, 0, 0)))
    # 脚: 四対 (a = 前から 1・3 本目、b = 2・4 本目。歩きで交互に動かす)
    for sx, ba, bb in ((1, "leg_la", "leg_lb"), (-1, "leg_ra", "leg_rb")):
        for i, z in enumerate((-5.0, 0.0, 5.0, 9.6)):
            b = ba if i % 2 == 0 else bb
            zz = z * 1.25
            F += part(f"leg{sx}{i}", tube(b, "leg", [(sx * 12.6, 13.2, z), (sx * 18.0, 18.0, zz), (sx * 21.0, 9.0, zz * 1.1), (sx * 21.8, 0.6, zz * 1.15)],
                                          ra=[2.4, 2.1, 1.5, 0.45], sides=5, hint=(0, 0, 1)))
            F += part(f"knee{sx}{i}", ellipsoid(b, "shell", (sx * 18.0, 18.0, zz), (2.2, 2.2, 2.2), segs=5, rings=2))
    return F, []


POSES = {
    "idle": {"claw_big": (6, 0, 0), "pincer": (-10, 0, 0)},
    # 大ばさみを振り上げる (叩きつけの予告)
    "telegraph": {"body": (8, 0, 0), "claw_big": (60, -10, -20), "pincer": (-30, 0, 0), "claw_small": (20, 0, 0), "eye_l": (-20, 0, 0), "eye_r": (-20, 0, 0)},
    # 泡を吹く: 体を前へ傾けて口を開く
    "spray": {"body": (-10, 0, 0), "claw_big": (-10, 20, 10), "claw_small": (-20, 0, 0), "eye_l": (20, 0, 10), "eye_r": (20, 0, -10)},
}

SPEC = dict(id="tide_crab", short="潮招きの大蟹", title="潮招きの大蟹 (tide_crab) — 潮の浜の名のある強敵",
            sub=["幅 2.5 ブロックの潮招き。暗い青緑の平たい甲羅にフジツボと珊瑚の枝、垂れた海藻。体ほどもある朱色の大ばさみ (珊瑚とフジツボ付き)、小さな左のはさみ、光る眼柄。",
                 "大ばさみを振り上げて (予告 1.0 秒、前の帯) 叩きつける。泡を吹く (予告 1.2 秒、前の扇) と扇の中の人は押し流され鈍る。"],
            rows=[("前 3/4", "idle", 210, 14, 600, (-4, 14, -8), 6.0), ("横", "idle", 270, 8, 600, (-4, 14, -8), 6.0),
                  ("後ろ 3/4", "idle", 30, 14, 600, (-4, 14, -8), 6.0), ("予告: 振り上げ", "telegraph", 210, 10, 600, (-4, 16, -8), 6.0),
                  ("予告: 泡", "spray", 200, 10, 600, (-4, 14, -8), 6.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
