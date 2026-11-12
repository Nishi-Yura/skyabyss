# -*- coding: utf-8 -*-
"""淵の人形の敵 (案 A 作り込み): 塩の抜け殻・肋の守り・雷の像。前 = -Z、左腕 (arm_l) は -x。
三段の姿 (abyss_<id>_s1 / _s2 / _s3) は同じ形を、段 2・3 で骨ごとに付け根から上下へ伸ばし (1.08 / 1.18)、絵を暗くして割れ目を光らせる
(tools/gen/abyss/gen_abyss_mobs_a.py の決まりと同じ)。"""
from __future__ import annotations

from fb import *  # noqa: F401,F403
from foes_human import taper_limb

# 段: (接尾辞, 伸び, 暗さ, 割れ目の太さ)
STAGES = (("_s1", 1.0, 1.0, 0.0), ("_s2", 1.08, 0.86, 0.05), ("_s3", 1.18, 0.72, 0.1))


def husk_body(m, mat, joint_mat=None, chest=True, waist=2.6, hand_mat=None, fingers_=True):
    """淵の人形の体: 胴 (胸が張る)、腕 (肩・肘・手と指)、脚 (膝・足)、首"""
    jm = joint_mat or mat
    m.add("torso", lof("body", mat, [fsec(10.4, 2.6, 1.7, 1.7, n=16), fsec(12.5, waist, 1.6, 1.6, n=16), fsec(15.0, 3.2, 2.2, 1.9, n=16, zc=-0.1),
                                      fsec(17.5, 3.6, 2.5, 2.1, n=16, zc=-0.25), fsec(20.2, 3.8, 2.2, 2.0, n=16), fsec(22.2, 2.2, 1.5, 1.5, n=16),
                                      fsec(23.2, 1.3, 1.2, 1.2, n=16)]), core=True)
    if chest:
        for s in (-1, 1):
            m.add(f"pec{s}", ell("body", mat, (s * 1.7, 19.0, -1.7), (1.7, 1.3, 0.8), segs=10, rings=3))
            m.add(f"shoulder{s}", ell("body", mat, (s * 3.9, 21.3, 0), (1.5, 1.3, 1.6), segs=10, rings=3))
    m.add("pelvis", ell("body", jm, (0, 11.2, 0), (2.9, 1.4, 1.9), segs=12, rings=3))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        x = s * 4.6
        taper_limb(m, f"{b}_up", b, mat, [(x, 21.0, 0.0), (x + s * 0.2, 18.8, 0.15), (x + s * 0.4, 16.8, 0.3)], [1.45, 1.35, 1.25])
        m.add(f"{b}_elbow", ell(b, jm, (x + s * 0.4, 16.8, 0.3), (1.22, 1.22, 1.22), segs=10, rings=3))
        taper_limb(m, f"{b}_fore", b, mat, [(x + s * 0.4, 16.8, 0.3), (x + s * 0.5, 14.6, 0.05), (x + s * 0.6, 12.8, -0.2)], [1.2, 1.1, 0.95])
        if fingers_:
            hand(m, b, hand_mat or mat, (x + s * 0.6, 12.4, -0.25), (0, -1, -0.12), (0, 0, 1), size=1.0, n=4, length=1.3)
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        x = s * 2.0
        taper_limb(m, f"{b}_thigh", b, mat, [(x, 12.0, 0.0), (x, 9.2, 0.1), (x, 6.6, 0.2)], [1.9, 1.75, 1.5])
        m.add(f"{b}_knee", ell(b, jm, (x, 6.6, -0.2), (1.35, 1.3, 1.25), segs=10, rings=3))
        taper_limb(m, f"{b}_shin", b, mat, [(x, 6.6, 0.2), (x, 4.0, 0.1), (x, 1.6, -0.2)], [1.5, 1.3, 1.05])
        m.add(f"{b}_foot", ell(b, mat, (x, 0.9, -0.9), (1.25, 0.85, 2.05), segs=10, rings=3))
        for k in range(3):
            m.add(f"{b}_toe{k}", ell(b, mat, (x + (k - 1) * 0.65, 0.5, -2.7), (0.38, 0.4, 0.55), segs=6, rings=2))
    m.add("neck", tub("head", mat, [P(0, 22.4, 0.1), P(0, 23.8, 0.1)], [0.95, 0.9], None, sides=10))


def head_skull(m, mat, jaw_mat, socket_mat="hollow"):
    m.add("skull", ell("head", mat, (0, 24.8, 0.0), (2.0, 2.4, 2.1), segs=14, rings=5))
    m.add("jaw", ell("head", jaw_mat, (0, 22.9, -0.75), (1.4, 0.85, 1.3), segs=10, rings=3))
    m.add("brow", tub("head", mat, [P(-1.6, 25.3, -1.6), P(-0.6, 25.5, -2.0), P(0.6, 25.5, -2.0), P(1.6, 25.3, -1.6)], [0.35, 0.4, 0.4, 0.35], None, sides=6))
    for s in (-1, 1):
        m.add(f"socket{s}", ell("head", socket_mat, (s * 0.72, 24.6, -1.82), (0.55, 0.5, 0.35), segs=8, rings=2))
        m.add(f"cheek{s}", ell("head", mat, (s * 1.3, 23.8, -1.4), (0.6, 0.5, 0.5), segs=8, rings=2))
    m.add("nose_hole", ell("head", socket_mat, (0, 23.9, -2.0), (0.25, 0.35, 0.2), segs=6, rings=1))


# ================================================================ 塩の抜け殻 (salt_husk): 割れた人形、塩の殻と中の黒い洞
def salt_husk():
    m = start("abyss_salt_husk_s1", "塩の抜け殻")
    husk_body(m, "salt", "saltd")
    m.add("skull", ell("head", "salt", (0, 24.9, 0.0), (1.95, 2.35, 2.05), segs=14, rings=5))
    m.add("jaw", ell("head", "salt", (0, 23.1, -0.6), (1.45, 0.9, 1.35), segs=10, rings=3))
    m.add("face_crack", tub("head", "hollow", [P(0.4, 26.7, -1.45), P(-0.2, 25.6, -1.85), P(0.3, 24.4, -1.9), P(-0.1, 23.3, -1.65)], [0.07, 0.11, 0.09, 0.06], None, sides=5))
    for s in (-1, 1):
        m.add(f"socket{s}", ell("head", "hollow", (s * 0.7, 24.7, -1.85), (0.5, 0.32, 0.3), segs=8, rings=2))
        eye(m, "head", (s * 0.7, 24.7, -1.95), 0.18, (0, 0, -1), (160, 220, 255), glow=False)
    # 胸の割れ目: 中の黒い洞と、割れた殻の縁
    m.add("chest_hole", ell("body", "hollow", (0.4, 17.6, -2.15), (1.4, 2.1, 0.55), segs=10, rings=3))
    rim = [P(-0.6, 19.8, -2.45), P(0.6, 19.4, -2.6), P(1.6, 18.0, -2.55), P(1.5, 16.2, -2.5), P(0.4, 15.4, -2.5), P(-0.8, 16.4, -2.5), P(-1.0, 18.4, -2.5), P(-0.6, 19.8, -2.45)]
    m.add("hole_rim", tub("body", "saltc", rim, [0.32] * len(rim), None, sides=5))
    for k, (p0, p1, p2) in enumerate((((1.6, 18.0), (2.8, 19.4), (3.4, 21.0)), ((0.4, 15.4), (-0.6, 13.6), (-1.6, 12.4)), ((-1.0, 18.4), (-2.4, 19.0), (-3.2, 20.4)))):
        pts = [P(x, y, -2.15 - 0.08 * (2 - i)) for i, (x, y) in enumerate((p0, p1, p2))]
        m.add(f"crack{k}", tub("body", "hollow", pts, [0.13, 0.09, 0.05], None, sides=4))
    for b, s in (("arm_l", -1), ("arm_r", 1)):
        m.add(f"{b}_crack", tub(b, "hollow", [P(s * 5.5, 19.8, -1.1), P(s * 5.0, 18.2, -1.4), P(s * 5.4, 16.4, -1.1)], [0.07, 0.1, 0.05], None, sides=4))
    # 塩の殻のかさぶた (肩・腰・膝) と小さな結晶
    rng = np.random.default_rng(12)
    for k, (b, c) in enumerate((("body", (3.3, 22.0, 0.8)), ("body", (-2.8, 21.4, 1.2)), ("body", (2.4, 13.2, -1.4)), ("body", (-1.6, 12.0, 1.6)),
                                ("leg_l", (-2.4, 7.2, -1.0)), ("leg_r", (2.6, 5.0, 0.6)), ("arm_r", (5.2, 18.6, 1.0)), ("arm_l", (-5.0, 20.4, 0.6)))):
        m.add(f"crust{k}", ell(b, "saltc", c, (1.1, 0.75, 1.0), segs=8, rings=2, rx=rng.uniform(-30, 30), rz=rng.uniform(-30, 30)))
        for j in range(2):
            d = rng.normal(size=3) * 0.4 + P(0, 1, 0)
            m.add(f"cry{k}{j}", cone(b, "crystal", tuple(P(*c) + rng.normal(size=3) * 0.4), tuple(d), 1.0 + j * 0.4, 0.32, sides=5, rings=1))
    m.add("salt_drip", tub("body", "saltc", [P(-1.6, 10.6, -1.6), P(-1.7, 9.6, -1.7), P(-1.6, 8.8, -1.6)], [0.4, 0.3, 0.18], None, sides=6))
    m.pal = mats(salt=M_(SALT, lit=0.3), saltd=M_(SALTD), saltc=M_([(176, 186, 194), (206, 214, 220), (230, 236, 240), (248, 250, 252)], lit=0.36),
                 crystal=M_([(150, 196, 220), (190, 226, 244), (226, 244, 252), (250, 254, 255)], lit=0.5), hollow=M_(HOLLOW, min=0))
    def det(c, col, glow):
        col, glow = cell_cracks(c, col, glow, ["salt", "saltc"], 6.0, 0.035, (108, 125, 140), seed=11)
        return cell_cracks(c, col, glow, ["salt"], 9.0, 0.025, (70, 80, 92), seed=12)
    m.detail = det
    m.stage_rgb = (159, 216, 255)
    return m


# ================================================================ 肋の守り (rib_guard): 肋の檻を着た骨の人、左腕の円い盾
def rib_guard():
    m = start("abyss_rib_guard_s1", "肋の守り")
    husk_body(m, "bone", "calc", chest=False, waist=2.4)
    head_skull(m, "bone", "calc")
    for k in range(6):
        m.add(f"tooth{k}", ell("head", "calc", ((k - 2.5) * 0.42, 23.35, -1.88), (0.18, 0.3, 0.18), segs=6, rings=1))
    for s in (-1, 1):
        eye(m, "head", (s * 0.72, 24.6, -1.85), 0.2, (0, 0, -1), (255, 110, 70), glow=True)
    m.add("chest_hollow", ell("body", "hollow", (0, 17.0, -1.3), (2.4, 2.8, 0.9), segs=10, rings=3))
    # 肋の弧 (四段)、胸骨、背骨の椎
    for i, y in enumerate((13.8, 15.8, 17.8, 19.8)):
        w = 3.6 + (0.2 if i in (1, 2) else 0)
        pts = [P(-w, y + 0.4, 0.6), P(-w + 0.2, y, -1.4), P(-2.6, y - 0.4, -2.5), P(0.0, y - 0.7, -3.0), P(2.6, y - 0.4, -2.5), P(w - 0.2, y, -1.4), P(w, y + 0.4, 0.6)]
        m.add(f"rib{i}", tub("body", "calc", pts, [0.42, 0.45, 0.45, 0.4, 0.45, 0.45, 0.42], [0.3] * 7, sides=6, hint=(0, 1, 0)))
    m.add("sternum", tub("body", "bone", [P(0, 20.6, -2.6), P(0, 17.0, -3.1), P(0, 13.6, -2.7)], [0.55, 0.5, 0.4], [0.3] * 3, sides=6, hint=(0, 0, 1)))
    for k in range(9):
        y = 11.8 + k * 1.25
        m.add(f"vert{k}", ell("body", "calc", (0, y, 1.6), (0.75, 0.48, 0.65), segs=8, rings=2))
        m.add(f"vspine{k}", cone("body", "bone", P(0, y, 2.0), (0, -0.3, 1), 0.9, 0.3, sides=4, rings=1))
    for s in (-1, 1):
        m.add(f"clav{s}", tub("body", "calc", [P(s * 0.6, 22.4, -1.0), P(s * 2.4, 22.6, -0.9), P(s * 4.0, 21.8, -0.2)], [0.35, 0.3, 0.32], None, sides=6))
    # 盾: 円い骨の板、縁、中の輪の刻み、中心のこぶ、留め紐
    m.add("shield", ell("arm_l", "calc", (-5.8, 13.6, -1.0), (3.4, 3.7, 0.85), segs=16, rings=5))
    ring = [P(-5.8 + math.cos(a) * 3.35, 13.6 + math.sin(a) * 3.65, -1.0) for a in np.linspace(0, math.tau, 21)]
    m.add("shield_rim", tub("arm_l", "bone", ring, [0.42] * len(ring), None, sides=6, hint=(0, 0, 1)))
    ring2 = [P(-5.8 + math.cos(a) * 2.0, 13.6 + math.sin(a) * 2.2, -1.62) for a in np.linspace(0, math.tau, 17)]
    m.add("shield_ring", tub("arm_l", "bone", ring2, [0.2] * len(ring2), None, sides=5, hint=(0, 0, 1)))
    m.add("shield_boss", ell("arm_l", "bone", (-5.8, 13.6, -1.85), (0.95, 0.95, 0.7), segs=10, rings=3))
    for k in range(6):
        a = k / 6 * math.tau
        m.add(f"shield_peg{k}", ell("arm_l", "bone", (-5.8 + math.cos(a) * 2.8, 13.6 + math.sin(a) * 3.05, -1.62), (0.3, 0.3, 0.2), segs=6, rings=1))
    m.pal = mats(bone=M_(CALC, lit=0.32), calc=M_([(150, 136, 110), (182, 168, 140), (210, 198, 172), (232, 224, 204)], lit=0.34), hollow=M_(HOLLOW, min=0))
    def det(c, col, glow):
        col = bands_y(c, col, ["bone"], 2.2, 0.5, k=0.93, wobble=0.8)
        return cell_cracks(c, col, glow, ["bone", "calc"], 7.0, 0.03, (90, 76, 58), seed=8)
    m.detail = det
    m.stage_rgb = (255, 90, 58)
    return m


# ================================================================ 雷の像 (lightning_idol): 角を落とした石の像、胸の雷の筋、頭上の避雷の棒、台
def lightning_idol():
    m = start("abyss_lightning_idol_s1", "雷の像")
    husk_body(m, "stone", "stoned", fingers_=False)
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        m.add(f"{b}_fist", ell(b, "stone", (s * 5.2, 11.6, -0.25), (1.15, 1.35, 1.1), segs=10, rings=3))
        for k in range(2):
            m.add(f"{b}_band{k}", tub(b, "brass", [P(s * (4.75 + k * 0.1), 15.0 - k * 1.4, 0.15), P(s * (4.8 + k * 0.1), 14.4 - k * 1.4, 0.05)], [1.28, 1.24], None, sides=10))
    # 台 (三段) と足を沈める
    for k, (y0, y1, w) in enumerate(((-0.2, 0.8, 9.8), (0.8, 1.6, 8.8), (1.6, 2.1, 8.0))):
        m.add(f"plinth{k}", lof("body", "stoned" if k == 1 else "stone", [ysec(y0, w, w * 0.92, cut=0.28), ysec(y1, w, w * 0.92, cut=0.28)]))
    for k, a in enumerate((0.4, 2.3, 4.0)):
        m.add(f"plinth_chip{k}", ell("body", "stoned", (math.cos(a) * 4.6, 0.3, math.sin(a) * 4.2), (0.8, 0.5, 0.6), segs=6, rings=2))
    # 刻み: 胸の雷の筋 (光る)、腹の帯、肩の板
    bolt = [P(-1.2, 21.2, -2.1), P(0.4, 19.4, -2.55), P(-0.6, 18.4, -2.6), P(1.0, 16.2, -2.45), P(0.0, 15.2, -2.25), P(0.9, 13.4, -1.75)]
    m.add("bolt", tub("body", "bolt", bolt, [0.22, 0.3, 0.3, 0.3, 0.25, 0.18], None, sides=5))
    m.add("bolt_groove", tub("body", "stoned", bolt, [0.38, 0.46, 0.46, 0.46, 0.4, 0.3], [0.1] * 6, sides=5, hint=(0, 0, 1)))
    m.add("waist_band", lof("body", "brass", [fsec(12.2, 2.75, 1.75, 1.75, n=16), fsec(13.0, 2.75, 1.75, 1.75, n=16)]))
    for s in (-1, 1):
        m.add(f"plate{s}", lof("body", "stoned", [fsec(20.4, 1.8, 1.9, 1.9, n=12, xc=s * 3.8), fsec(21.6, 1.9, 2.0, 2.0, n=12, xc=s * 3.9), fsec(22.4, 1.2, 1.3, 1.3, n=12, xc=s * 3.7)]))
    # 頭: 角を落とした兜の顔、目の切れ込み (光る)、避雷の棒 (輪と先の玉)
    m.add("helm", lof("head", "stone", [fsec(22.6, 1.9, 2.0, 2.0, n=16), fsec(24.0, 2.15, 2.25, 2.1, n=16), fsec(26.0, 2.05, 2.15, 2.0, n=16),
                                         fsec(27.0, 1.5, 1.6, 1.5, n=16), fsec(27.4, 0.8, 0.8, 0.8, n=16)]))
    m.add("visor", tub("head", "hollow", [P(-1.4, 24.9, -1.95), P(0, 24.7, -2.2), P(1.4, 24.9, -1.95)], [0.3, 0.32, 0.3], [0.14] * 3, sides=6, hint=(0, 1, 0)))
    for s in (-1, 1):
        m.add(f"eye{s}", ell("head", "bolt", (s * 0.75, 24.8, -2.15), (0.4, 0.14, 0.12), segs=6, rings=1))
    m.add("crest", tub("head", "stoned", [P(0, 26.9, -1.6), P(0, 27.6, 0), P(0, 26.9, 1.6)], [0.4] * 3, [0.2] * 3, sides=4, hint=(1, 0, 0)))
    m.add("rod", tub("head", "brass", [P(0, 27.0, 0), P(0, 32.0, 0)], [0.32, 0.22], None, sides=8))
    for k, y in enumerate((28.0, 29.6, 31.0)):
        m.add(f"rod_ring{k}", tub("head", "brass", [P(0, y - 0.15, 0), P(0, y + 0.15, 0)], [0.55 - k * 0.08, 0.55 - k * 0.08], None, sides=8))
    m.add("rod_tip", ell("head", "bolt", (0, 32.2, 0), (0.45, 0.45, 0.45), segs=8, rings=3))
    m.pal = mats(stone=M_(BASALT, lit=0.34), stoned=M_([(22, 24, 30), (34, 36, 44), (50, 52, 62), (70, 72, 84)]), brass=M_(BRASSY, lit=0.42),
                 bolt=M_(BOLT, base=0.8, lit=0.1, min=1), hollow=M_(HOLLOW, min=0), glow=["bolt"])
    def det(c, col, glow):
        col = speckle(c, col, ["stone"], (90, 96, 112), density=0.04, size=0.6)
        col, glow = cell_cracks(c, col, glow, ["stone"], 3.5, 0.06, (85, 89, 106), seed=23)
        return cell_cracks(c, col, glow, ["stone", "stoned"], 6.0, 0.035, (191, 246, 255), seed=24, lit=True)
    m.detail = det
    m.stage_rgb = (255, 210, 122)
    return m


ALL = {"abyss_salt_husk": salt_husk, "abyss_rib_guard": rib_guard, "abyss_lightning_idol": lightning_idol}
