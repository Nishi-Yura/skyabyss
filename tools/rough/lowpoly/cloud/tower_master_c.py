# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) 作り直し 案 C「門を背負う者」。背の「あいだの門」を鎖で背負い、淵から生えた巨人。Annihilation にいちばん近い、角と口の怪物の形。
  頭      目のない白い面。下に縦に裂ける大きな口 (金の歯)。前へ反る長い角 (右 金、左 黒)、角のあいだに天秤の印
  胸      黒い石の胸に、金の肋が籠のように開き、その奥で核が光る。鎖が胴を斜めに巻く
  肩と腕  ぼろの白い衣が肩から垂れる。右腕は白い衣と金の籠手、左腕は黒い石に紫の筋。前腕に棘
  下      脚はなく、黒い石の根と折れた柱と鎖の束が淵から伸びて胴を支える
  背      あいだの門を鎖で背負う。門の縁に羽が並ぶ (光輪の代わり)
共通の骨と合図の姿は tower_master_kit.py。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tower_master_kit import *  # noqa
from tower_master_kit import _mk  # noqa


def tatter(bone, name, mat, top, down, width, length, n=4, twist=0.0):
    """ぼろの布の帯 (上から垂れ、先がぎざぎざ)"""
    f = []
    for i in range(n):
        x0 = top[0] + (i - (n - 1) / 2) * width
        L = length * (0.75 + 0.25 * math.sin(i * 2.7 + twist))
        p0 = np.array([x0, top[1], top[2]])
        pts = [p0, p0 + np.array(down) * L * 0.5 + (0, 0, 6 * math.sin(i)), p0 + np.array(down) * L]
        f += part(f"{name}{i}", blade(bone, mat, pts, [width * 0.55, width * 0.5, 0], [4, 4, 0], hint=(0, 0, 1)))
    return f


def build(k):
    F = []
    # ---- 淵から伸びる根 (脚の代わり): 黒い石の塊・折れた柱・鎖の束
    root = [oring(-200, 120, 110, 100, n=16, fold=0.18, fn=5), oring(-80, 150, 130, 120, n=16, fold=0.14, fn=5),
            oring(20, 168, 130, 120, n=16, fold=0.08, fn=6), oring(80, 150, 108, 104, n=16)]
    F += part("root", loft("legs", "stone", root, caps=("stone", "stone")))
    for i, (x, z, h, tilt) in enumerate(((-150, -120, 260, -0.3), (140, -100, 300, 0.25), (-60, -170, 200, -0.1), (90, 110, 240, 0.2), (-130, 90, 220, -0.2))):
        base = np.array([x, -240, z])
        top = base + np.array([tilt * h, h, -abs(tilt) * 40])
        F += part(f"pillar{i}", tube("legs", "stone", [base, (base + top) / 2, top], ra=[30, 28, 24], sides=6, hint=(1, 0, 0)))
        F += part(f"pcap{i}", tube("legs", "gold" if x > 0 else "void", [top + (0, -14, 0), top + (0, -4, 0)], ra=30, sides=6, hint=(1, 0, 0)))
    for i in range(6):
        a = 2 * math.pi * (i + 0.5) / 6
        F += chain("legs", math.sin(a) * 120, -214, -480, -math.cos(a) * 100, step=26, r=8, mat="iron" if math.sin(a) < 0.2 else "gold")
    for j, pts in enumerate(([(-140, -20, -120), (-110, -100, -130), (-130, -180, -110)], [(120, 0, -126), (150, -90, -120), (130, -170, -100)])):
        F += part(f"rootcrack{j}", tube("legs", "void", pts, ra=5, sides=4, hint=(0, 0, 1)))

    # ---- 胴: 前へかがむ。黒い石の胸と金の肋の籠
    torso = [oring(60, 160, 116, 110, n=24, fold=0.03, fn=9), oring(140, 154, 110, 104, n=24), oring(210, 176, 120, 110, n=24),
             oring(270, 196, 126, 112, n=24), oring(318, 200, 108, 104, n=24), oring(350, 140, 80, 78, n=24), oring(366, 60, 46, 44, n=24)]
    for i, rg in enumerate(torso):  # 上ほど前へ (かがむ)
        for p in rg:
            p[2] -= i * 6
    F += part("torso", loft("body", "stone", torso, caps=("stone", "stone")))
    # 胸の洞 (暗い) と、その奥の核
    F += part("cavity", ellipsoid("body", "hollow", (0, 250, -128), (76, 72, 18), segs=14, rings=6))
    F += part("core", ellipsoid("core", "dawn", (0, 250, -150), (30, 30, 16), segs=12, rings=6))
    # 金の肋 (左右から開いた籠)
    for j, y in enumerate((300, 272, 244, 216)):
        for s in (-1, 1):
            arc = [(s * (100 - j * 4), y + 10, -122 - j * 2), (s * 70, y + 4, -150), (s * 36, y - 6, -156), (s * 18, y - 14, -150)]
            F += part(f"rib{j}{s}", tube("body", "gold", arc, ra=[9, 8, 6, 3], rb=[6, 6, 4, 2], sides=4, hint=(0, 1, 0)))
    F += part("sternum", tube("body", "gold", [(0, 330, -120), (0, 300, -146), (0, 286, -150)], ra=[10, 8, 4], sides=4, hint=(1, 0, 0)))
    # 鎖が胴を斜めに巻く
    F += chain_path("body", "wrap", [(-150, 356, -150), (-70, 296, -186), (50, 196, -188), (150, 104, -156)], step=26, r=9)
    # 胴の紫の筋
    for j, pts in enumerate(([(-120, 200, -150), (-150, 150, -130), (-140, 100, -122)], [(130, 210, -146), (150, 160, -128)], [(-60, 120, -134), (-30, 80, -126)])):
        F += part(f"vein{j}", tube("body", "void", pts, ra=4, sides=4, hint=(0, 0, 1)))
    # 肩からのぼろの衣 (前と後ろ)
    for s in (-1, 1):
        F += tatter("body", f"ragf{s}", "robe", (s * 150, 360, -96), (0.05 * s, -1, -0.15), 34, 250, n=3, twist=s)
        F += tatter("body", f"ragb{s}", "robe", (s * 130, 360, 98), (0.1 * s, -1, 0.25), 40, 330, n=4, twist=2 * s)
    F += part("mantleg", hoop("body", "gold", 352, 150, 96, zc=-40, r=7, n=20))

    # ---- 腕 (長く太い。右 白い衣と金の籠手、左 黒い石と紫の筋)。前腕の棘
    for s in (1, -1):
        m = "robe" if s > 0 else "stone"
        a, f_ = ("arm_r", "fore_r") if s > 0 else ("arm_l", "fore_l")
        F += arm_parts(s, m, upper_r=(74, 76, 62, 50), fore_r=(52, 60, 52, 38), sides=10)
        F += part(f"spaul{s}", ellipsoid(a, "gold" if s > 0 else "stone", (s * 168, 366, -10), (92, 40, 90), segs=12, rings=5, rot=rot_z(s * 18)))
        for j in range(4):
            F += part(f"sspk{s}{j}", spike(a, "gold" if s > 0 else "void", (s * (150 + j * 26), 404 - j * 6, -40 + j * 22),
                                           (s * 0.4, 1, 0.1 * j), 70 - j * 10, 15, sides=5, rings=2))
        for j in range(3):
            t = 0.25 + j * 0.25
            px = s * (205 + 5 * t)
            py = 118 - 214 * t
            pz = -122 - 194 * t
            F += part(f"fspk{s}{j}", spike(f_, "gold" if s > 0 else "void", (px + s * 44, py + 10, pz + 10), (s * 1, 0.5, 0.4), 60, 14, sides=5, rings=2))
        if s > 0:
            F += part("gaunt", tube(f_, "gold", [(209, -20, -240), (210, -60, -278), (210, -96, -314)], ra=[58, 56, 46], sides=10, hint=(1, 0, 0)))
        else:
            F += part("lvein", tube(a, "void", [(-226, 300, -60), (-254, 220, -80), (-274, 160, -110)], ra=4, sides=4, hint=(0, 1, 0)))
            F += part("lvein2", tube(f_, "void", [(-252, 60, -170), (-262, -20, -240), (-250, -70, -290)], ra=4, sides=4, hint=(0, 1, 0)))
            F += part("shackle", hoop(f_, "iron", -95, 54, 54, cx=-210, zc=-325, r=11, n=14))
            F += chain(f_, -210, -110, -300, -300, step=22, r=7)
        F += hand_parts(s, m if s < 0 else "gold", nail="gold" if s > 0 else "void", claws=True, fat=1.12)
    F += palm_sigil(-1)

    # ---- 頭: 目のない白い面、縦に裂ける口、前へ反る長い角
    mask = [oring(488, 56, 58, 62, n=20, zc=-30), oring(456, 80, 94, 78, n=20, zc=-30), oring(416, 84, 108, 78, n=20, zc=-36), oring(388, 72, 104, 68, n=20, zc=-40)]
    F += part("mask", loft("head", "veil", mask, caps=("veil", "veil")))
    F += part("maskline", tube("head", "gold", [(0, 488, -90), (0, 456, -126), (0, 410, -146)], ra=4, sides=4, hint=(1, 0, 0)))
    for s in (-1, 1):
        F += part(f"cheek{s}", tube("head", "gold", [(s * 40, 404, -136), (s * 68, 424, -114), (s * 82, 456, -84)], ra=4, sides=4, hint=(0, 0, 1)))
    F += part("mouthglow", ellipsoid("head", "dawn", (0, 386, -120), (48, 12, 18), segs=10, rings=3))
    jaw = [oring(390, 70, 102, 66, n=20, zc=-40), oring(358, 60, 98, 56, n=20, zc=-50), oring(330, 40, 76, 40, n=20, zc=-56)]
    F += part("jaw", loft("jaw", "stone", jaw, caps=("stone", "stone")))
    for i in range(7):
        a = math.radians(-60 + i * 20)
        x, z = math.sin(a) * 64, -42 - math.cos(a) * 104
        F += part(f"tooth_u{i}", spike("head", "teeth", (x, 392, z), (0, -1, -0.15), 30 - abs(i - 3) * 3, 8, sides=4, rings=2))
        F += part(f"tooth_l{i}", spike("jaw", "teeth", (x * 0.95, 388, z - 2), (0, 1, -0.15), 24 - abs(i - 3) * 3, 7, sides=4, rings=2))
    for s, mat, rid in ((1, "gold", "robe"), (-1, "stone", "void")):
        F += horn("head", "horn", mat, s, [(s * 70, 462, -30), (s * 150, 500, -20), (s * 226, 540, -50), (s * 262, 580, -120), (s * 250, 616, -200), (s * 206, 640, -250)],
                  [34, 30, 24, 17, 9, 0], ridge=rid)
    F += sigil_mark("head", "hsig", (0, 540, -70), 30, r=3.6, spokes=0)
    F += sigil_mark("sigil", "stoke", (0, 640, -40), 74, r=5.0, spokes=8)

    # ---- 背の門を鎖で背負う。門の縁に羽 (光輪の代わり)
    F += rift_gate("stone", R=320, cy=430, z=290)
    for s in (-1, 1):
        F += chain_path("body", f"carry{s}", [(s * 150, 420, 70), (s * 200, 540, 200), (s * 230, 660, 280)], step=26, r=9)
    for i in range(16):
        a = 2 * math.pi * (i + 0.5) / 16
        c = np.array([math.sin(a) * 340, 430 + math.cos(a) * 340, 292])
        d = np.array([math.sin(a), math.cos(a), 0.0])
        F += part(f"feather{i}", blade("rift", "gold" if i % 2 == 0 else "robe", [c, c + d * 70 + (0, 0, 4), c + d * 130 + (0, 0, 8)],
                                       [14, 18, 0], [4, 5, 0], hint=(0, 0, 1)))
    return finish(F)


SPEC = dict(
    id="tower_master_c", short="あいだに座すもの C", kind="超大型レイドの主 作り直し (54 の 2.5)",
    title="あいだに座すもの 案 C「門を背負う者」— 淵から生え、あいだの門を背負う角の巨人",
    sub=["目のない白い面に縦に裂ける口 (金の歯)、前へ反る長い角。黒い石の胸に金の肋が籠のように開き、奥で核が光る。鎖が胴を巻き、ぼろの白い衣が肩から垂れる。",
         "脚はなく、黒い石の根と折れた柱と鎖の束が淵から伸びる。背の門を鎖で背負い、門の縁に羽。いちばん Annihilation に近い怪物の形。"],
    labels=["門を背負う者"], rows=ROWS,
    options={"a": dict(name="門を背負う者", desc="角と口の怪物。金の肋の籠の奥の核、胴を巻く鎖、淵から伸びる根と柱、背負った門に羽。")},
    extra="骨 19 本 (A と同じ)。光るのは胸の核・口の中・印・紫の筋・門の渦。",
    budget="主 9000〜15000", pw=600, later="段が進むと肋が開き、根の柱が崩れて鎖が切れていく。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
