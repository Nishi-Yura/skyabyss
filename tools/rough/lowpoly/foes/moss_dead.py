"""苔の亡者 (moss_dead)。岩鯨の丘陵の夜。Lv 6〜10、重い近接。
一体一案: 岩鯨の丘で行き倒れた旅人の骸が、苔に呑まれて立ち上がったもの。前へ大きく屈んだ背に、朽ちた背負子 (木の枠と荷) を負い、
荷と肩と背から苔の塊と茸が盛り上がる。破れた編み笠が頭に傾いて残り、垂れた苔の房が顔を隠す。胸の苔の裂け目から肋が覗く。
苔の中に、淡い緑に光る胞子の袋がいくつも膨らむ。右腕は苔に包まれて太く長く、地面に届きそう。
動き: 遅いが硬い。太い右腕を振りかぶって叩きつける (予告 1.0 秒)。背の胞子の袋が膨らんで (予告 1.2 秒) まわりに胞子を撒く (毒・鈍足)。
"""
import math

import fk
from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, skeleton, skull, run1, part, mat

fk.GLOW["glow_moss"] = (170, 240, 130)
S = 1.12
BONES = {k: (p, [c[0] * 1.08, c[1] * S, c[2]]) for k, (p, c) in HUMAN_BONES.items()}
BONES["pack"] = ("body", [0.0, 20.0 * S, 3.2])
MOSS = [(24, 40, 20), (40, 64, 30), (62, 90, 40), (90, 118, 52), (124, 146, 70)]
BONEC = [(70, 66, 54), (112, 106, 86), (150, 142, 116), (186, 178, 150)]
STRAW = [(70, 56, 30), (110, 90, 50), (150, 126, 74), (184, 160, 104)]
WOOD = [(34, 26, 20), (56, 42, 30), (80, 62, 42), (104, 84, 58)]
CLOTH = [(36, 30, 26), (58, 48, 40), (82, 70, 56), (104, 92, 74)]
CAP = [(70, 30, 22), (110, 52, 34), (150, 80, 50), (190, 120, 80)]
PAL = {"mats": {"moss": mat(MOSS, 0.58, noise=0.4), "bone": mat(BONEC, 0.62, crack=0.08), "straw": mat(STRAW, 0.6, noise=0.3),
                "wood": mat(WOOD, 0.58, crack=0.06), "cloth": mat(CLOTH, 0.55, noise=0.3), "cap": mat(CAP, 0.6, noise=0.15),
                "stem": mat([(150, 140, 110), (190, 180, 150), (220, 212, 186)], 0.65), "dark": mat([(6, 8, 6), (14, 18, 14), (26, 30, 24)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "bone"}


def clump(F, pid, bone, c, r, segs=7):
    F += part(pid, ellipsoid(bone, "moss", c, r, segs=segs, rings=3))


def build(k):
    F = []
    skeleton(F, "bone", s=S)
    skull(F, "bone", s=S)
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_moss", (sx * 1.15, 27.2 * S, -3.2), (0.55, 0.55, 0.3)))
    # 朽ちた旅の着物 (腰から下の裾と、肩の破れ布)
    F += part("robe", loft("body", "cloth", [[V(3.9 * math.cos(math.radians(a)), 7.8 * S, 0.2 + 3.0 * math.sin(math.radians(a))) for a in range(0, 360, 40)],
                                              [V(3.3 * math.cos(math.radians(a)), 14.0 * S, 0.2 + 2.4 * math.sin(math.radians(a))) for a in range(0, 360, 40)]],
                       caps=("cloth", "cloth")))
    for i, x in enumerate((-2.4, -0.4, 1.8)):
        F += part(f"rag{i}", box("body", "cloth", (x, 7.4 * S, -1.8 + i * 0.4), (1.4, 2.6, 0.4), taper=(0.6, 1.0)))
    # 胸と背を呑む苔 (正面は裂けて肋が覗く)
    clump(F, "moss_back", "body", (0, 19.4 * S, 1.8), (4.6, 4.4, 2.6), segs=9)
    clump(F, "moss_side_l", "body", (3.4, 17.0 * S, -0.6), (1.6, 3.4, 2.2))
    clump(F, "moss_side_r", "body", (-3.4, 17.6 * S, -0.4), (1.8, 3.6, 2.4))
    clump(F, "moss_belly", "body", (0, 14.4 * S, -0.8), (3.6, 2.0, 2.6))
    # 肩の苔の塊と茸
    clump(F, "moss_sh_l", "left_arm", (5.6, 23.0 * S, 0.4), (2.4, 1.8, 2.6))
    clump(F, "moss_sh_r", "right_arm", (-5.8, 23.4 * S, 0.2), (3.0, 2.2, 3.0))
    # 太い右腕 (苔に包まれて長い) と細い左腕
    clump(F, "moss_arm_r", "right_arm", (-6.2, 19.2 * S, 0), (2.0, 3.4, 2.0))
    clump(F, "moss_fore_r", "right_forearm", (-6.6, 13.2 * S, -0.3), (2.3, 3.8, 2.3))
    clump(F, "moss_fist_r", "right_forearm", (-6.8, 8.6 * S, -0.6), (2.4, 2.0, 2.4))
    clump(F, "moss_fore_l", "left_forearm", (6.0, 14.0 * S, -0.2), (1.2, 2.0, 1.2), segs=6)
    clump(F, "moss_thigh_l", "leg_l", (2.4, 8.6 * S, 0), (1.6, 2.4, 1.7), segs=6)
    clump(F, "moss_shin_r", "leg_r", (-2.4, 4.0 * S, 0), (1.4, 2.0, 1.5), segs=6)
    # 破れた編み笠 (傾いて頭に残る) と顔を隠す苔の房
    hat = [[V(5.4 * math.cos(math.radians(a)), 29.6 * S, 5.4 * math.sin(math.radians(a))) for a in range(0, 360, 30)],
           [V(0.8 * math.cos(math.radians(a)), 31.8 * S, 0.8 * math.sin(math.radians(a))) for a in range(0, 360, 30)]]
    rot = fk.ck.rot_xyz(-12, 0, 14)
    piv = np.array([0, 29.6 * S, 0])
    hat = [[V(*(rot @ (np.array(p) - piv) + piv)) for p in ring] for ring in hat]
    F += part("hat", loft("head", "straw", hat, caps=("straw", "straw")))
    F += part("hat_tear", box("head", "dark", (3.2, 30.2 * S, -2.4), (2.0, 0.5, 1.6), rot=rot))
    for i, x in enumerate((-1.6, 0.2, 1.9)):
        F += part(f"veil{i}", tube("head", "moss", [(x, 28.8 * S, -3.4), (x * 1.1, 26.4 * S, -3.8), (x * 1.2, 24.8 * S, -3.5)], ra=[0.6, 0.45, 0.15], sides=4, hint=(1, 0, 0)))
    # 朽ちた背負子: 木の枠と荷、荷から苔と茸、光る胞子の袋
    for sx in (1, -1):
        F += part(f"frame{sx}", tube("pack", "wood", [(sx * 3.0, 12.0 * S, 3.6), (sx * 3.2, 30.0 * S, 4.2)], ra=[0.45, 0.4], sides=4, hint=(0, 0, 1)))
    for y in (15.0, 21.0, 27.0):
        F += part(f"rung{y}", tube("pack", "wood", [(-3.2, y * S, 4.0), (3.2, y * S, 4.0)], ra=[0.35, 0.35], sides=4, hint=(0, 1, 0)))
    F += part("load", ellipsoid("pack", "cloth", (0, 22.0 * S, 6.2), (3.4, 4.6, 2.4), segs=8, rings=3))
    clump(F, "moss_load", "pack", (0.6, 26.0 * S, 6.4), (3.6, 2.4, 2.6), segs=8)
    clump(F, "moss_load2", "pack", (-1.6, 19.0 * S, 7.6), (2.0, 2.6, 1.4))
    for i, (x, y, z, r) in enumerate(((1.8, 27.8, 7.4, 1.0), (-1.4, 23.4, 8.4, 0.85), (2.4, 20.2, 8.0, 0.7), (-2.6, 27.0, 5.8, 0.75))):
        F += part(f"spore{i}", ellipsoid("pack", "glow_moss", (x, y * S, z), (r, r, r), segs=6, rings=2))
    for i, (x, y, z, r) in enumerate(((3.4, 18.6, -1.6, 0.6), (-4.6, 24.0, -0.8, 0.7), (-6.6, 15.0, -2.4, 0.55))):
        F += part(f"spore_b{i}", ellipsoid("body" if i == 0 else "right_arm" if i == 1 else "right_forearm", "glow_moss", (x, y * S, z), (r, r, r), segs=5, rings=2))
    # 茸 (荷の上と右肩)
    for i, (bone, x, y, z, r) in enumerate((("pack", -0.8, 28.6, 5.6, 1.6), ("pack", 2.6, 25.0, 8.6, 1.1), ("right_arm", -6.4, 25.4, 0.8, 1.3))):
        F += part(f"stem{i}", tube(bone, "stem", [(x, (y - 1.4) * S, z), (x, y * S, z)], ra=[0.35, 0.3], sides=5, hint=(1, 0, 0)))
        F += part(f"cap{i}", ellipsoid(bone, "cap", (x, y * S + 0.3, z), (r, r * 0.45, r), segs=7, rings=2))
    return F, []


POSES = {
    # 前へ大きく屈み、太い右腕を垂らす
    "idle": {"body": (-22, 0, 0), "head": (14, 0, 0), "right_arm": (14, 0, -4), "right_forearm": (-6, 0, 0), "left_arm": (10, 0, 10), "left_forearm": (-24, 0, 0),
             "leg_l": (14, 0, 0), "leg_r": (8, 0, 0)},
    # 叩きつけの振りかぶり
    "telegraph": {"body": (-4, 20, 0), "head": (6, 0, 0), "right_arm": (170, 0, 10), "right_forearm": (-30, 0, 0), "left_arm": (30, 0, 20),
                  "leg_l": (-6, 0, 0), "leg_r": (22, 0, 0)},
    # 胞子を撒く: 背を丸めて荷を突き出す
    "spores": {"body": (-48, 0, 0), "head": (30, 0, 0), "pack": (-10, 0, 0), "right_arm": (-10, 0, -20), "left_arm": (-10, 0, 20), "leg_l": (30, 0, 0), "leg_r": (30, 0, 0)},
}

SPEC = dict(id="moss_dead", short="苔の亡者", title="苔の亡者 (moss_dead) — 苔に呑まれた行き倒れの旅人",
            sub=["前へ大きく屈んだ骸。朽ちた背負子の荷と肩から苔と茸、破れた編み笠と顔を隠す苔の房、胸の裂け目から肋。淡い緑に光る胞子の袋。",
                 "太い右腕を振りかぶって叩きつける (予告 1.0 秒)。背の胞子の袋が膨らんで (予告 1.2 秒) まわりに胞子を撒く。"],
            rows=[("前 3/4", "idle", 210, 10, 600, (0, 18, 0), 11.0), ("横", "idle", 270, 6, 600, (0, 18, 0), 11.0),
                  ("後ろ 3/4", "idle", 30, 10, 600, (0, 18, 0), 11.0), ("予告: 叩きつけ", "telegraph", 210, 8, 600, (0, 20, 0), 11.0),
                  ("予告: 胞子", "spores", 150, 10, 600, (0, 16, 0), 11.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
