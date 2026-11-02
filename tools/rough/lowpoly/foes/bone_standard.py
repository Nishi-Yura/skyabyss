"""骸の旗手 (bone_standard)。亡者の野営の頭、草原の名のある強敵。Lv 4、頭。
一体一案: 他の骸より背が高い (1.15 倍) 将の骸。羽飾りの立った兜、肩の大きな錆びた肩当てと、膝まで垂れる破れた外套。
背に長い竿を負い、先に破れた都の軍旗 (色の抜けた紺に金の鐘の紋) がはためく。右手に長い片手剣。目と胸の穴に強い琥珀の灯。
動き: 剣を横に薙ぐ (予告 0.7 秒)。旗を地に突き立てる (予告 1.2 秒) と、旗のそばの骸は倒れても一度だけ起き上がる。
"""
import math

from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, skeleton, skull, run1, part, mat
import bone_footman as BF

S = 1.15
BONES = {k: (p, [c[0] * 1.05, c[1] * S, c[2]]) for k, (p, c) in HUMAN_BONES.items()}
BONES["banner"] = ("body", [0.0, 14.0 * S, 3.2])
PLUME = [(90, 20, 20), (140, 34, 30), (186, 60, 46), (220, 96, 74)]
PAL = {"mats": dict(BF.PAL["mats"], plume=mat(PLUME, 0.6, noise=0.2)), "skull_bone": "head", "face_mat": "bone"}


def build(k):
    F = []
    skeleton(F, "bone", s=S)
    skull(F, "bone", s=S)
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_amber", (sx * 1.15, 27.2 * S, -3.2), (0.7, 0.7, 0.3)))
    F += part("heart", ellipsoid("body", "glow_amber", (0, 18.6 * S, -3.0), (1.0, 1.2, 0.6), segs=6, rings=2))
    # 羽飾りの兜
    F += part("helm", ellipsoid("head", "rust", (0, 29.0 * S, 0), (3.6, 2.8, 3.7), segs=8, rings=2))
    F += part("visor", box("head", "rust", (0, 27.0 * S, -3.0), (4.4, 1.0, 0.8)))
    F += part("plume", tube("head", "plume", [(0, 31.4 * S, -1.0), (0, 34.0 * S, 0.6), (0, 33.2 * S, 3.4), (0, 30.0 * S, 5.4)], ra=[0.9, 1.1, 0.9, 0.3], rb=[0.4] * 4, sides=4, hint=(1, 0, 0)))
    # 大きな肩当てと外套
    for sx, arm in ((1, "left_arm"), (-1, "right_arm")):
        F += part(f"pauldron{sx}", ellipsoid(arm, "rust", (sx * 5.8, 23.0 * S, 0), (2.6, 1.6, 2.6), segs=7, rings=2))
        F += part(f"pauldron2{sx}", ellipsoid(arm, "rust", (sx * 6.4, 21.6 * S, 0), (2.2, 1.0, 2.4), segs=6, rings=2))
    F += part("cuirass", ellipsoid("body", "rust", (0, 18.8 * S, -0.4), (4.4, 4.2, 2.8), segs=8, rings=3))
    F += part("cloak", box("body", "tabard", (0, 14.0 * S, 2.9), (8.6, 15.0, 0.5), taper=(0.8, 1.0)))
    for x in (-3.0, -0.6, 2.2):
        F += part(f"tear{x}", box("body", "dark", (x, 7.4 * S, 3.2), (1.0, 2.0, 0.3)))
    # 背の軍旗 (竿 + 横木 + 破れた布 + 金の鐘の紋)
    F += part("pole", tube("banner", "wood", [(0, 8.0 * S, 3.6), (0, 46.0 * S, 3.6)], ra=[0.4, 0.35], sides=5, hint=(1, 0, 0)))
    F += part("crossbar", tube("banner", "rust", [(-6.4, 45.0 * S, 3.6), (6.4, 45.0 * S, 3.6)], ra=[0.3, 0.3], sides=4, hint=(0, 1, 0)))
    F += part("flag", loft("banner", "tabard", [
        [V(-6.2, 44.6 * S, 3.4), V(6.2, 44.6 * S, 3.4), V(6.2, 44.6 * S, 3.8), V(-6.2, 44.6 * S, 3.8)],
        [V(-5.6, 31.0 * S, 3.4), V(4.2, 28.4 * S, 3.4), V(4.2, 28.4 * S, 3.8), V(-5.6, 31.0 * S, 3.8)]], caps=("tabard", "tabard")))
    F += part("flag_tear", box("banner", "dark", (2.2, 30.0 * S, 3.6), (1.6, 3.0, 0.5)))
    F += part("emblem", box("banner", "gold", (0, 38.4 * S, 3.2), (2.8, 3.4, 0.3), taper=(0.6, 1.0)))
    # 長い片手剣 (右手)
    F += part("hilt", tube("weapon", "rust", [(-6.3, 11.6 * S, -0.5), (-6.3, 9.4 * S, -0.5)], ra=[0.4, 0.4], sides=4, hint=(0, 0, 1)))
    F += part("guard", box("weapon", "gold", (-6.3, 9.2 * S, -0.5), (0.5, 0.5, 3.4)))
    F += part("blade", box("weapon", "rust", (-6.3, 3.6 * S, -0.5), (0.35, 10.6, 1.4), taper=(1.0, 0.5)))
    return F, []


POSES = {
    "idle": {"body": (-2, 0, 0), "head": (4, 0, 0), "right_arm": (14, 0, -8), "right_forearm": (-24, 0, 0), "left_arm": (6, 0, 10)},
    "telegraph": {"body": (2, 40, 0), "right_arm": (70, 70, -20), "right_forearm": (-20, 0, 0), "left_arm": (40, -20, 20), "leg_l": (-12, 0, 0), "leg_r": (14, 0, 0)},
    "plant": {"body": (-12, 0, 0), "head": (-10, 0, 0), "left_arm": (120, 0, -20), "right_arm": (120, 0, 20), "leg_l": (30, 0, 0), "leg_r": (-20, 0, 0)},
}

SPEC = dict(id="bone_standard", short="骸の旗手", title="骸の旗手 (bone_standard) — 破れた軍旗を負う将の骸",
            sub=["背の高い将の骸。羽飾りの兜、大きな肩当て、破れた外套、背の竿に破れた都の軍旗 (金の鐘の紋)、長い片手剣。胸の穴に琥珀の灯。",
                 "剣を横に薙ぐ (予告 0.7 秒)。旗を地に突き立てる (予告 1.2 秒) と、旗のそばの骸は一度だけ起き上がる。"],
            rows=[("前 3/4", "idle", 210, 10, 600, (0, 20, 0), 11.0), ("横", "idle", 270, 6, 600, (0, 20, 0), 11.0),
                  ("後ろ 3/4", "idle", 30, 10, 600, (0, 20, 0), 11.0), ("予告: 薙ぎの溜め", "telegraph", 210, 8, 600, (0, 20, 0), 11.0),
                  ("旗を突き立てる", "plant", 210, 8, 600, (0, 20, 0), 11.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
