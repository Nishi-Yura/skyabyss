# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) 作り直し 案 C「門を背負う者」。背の「あいだの門」を鎖で背負い、淵から生えた巨人。Annihilation にいちばん近い、角と口の怪物の形。
  頭      目のない白い面。下に縦に裂ける大きな口 (金の歯)。前へ反る長い角 (右 金、左 黒)、角のあいだに天秤の印
  胸      黒い石の胸に、金の肋が籠のように開き、その奥で核が光る。鎖が胴を斜めに巻く
  肩と腕  ぼろの白い衣が肩から垂れる。右腕は白い衣と金の籠手、左腕は黒い石に紫の筋。前腕に棘
  下      脚はなく、黒い石の根と折れた柱と鎖の束が淵から伸びて胴を支える
  背      あいだの門を鎖で背負う。門の縁に羽が並ぶ (光輪の代わり)
共通の骨と合図の姿は tower_master_kit.py。
2026-11-08 の選定「A 星の傷」(docs/review/roughs_boss_looks/tower.png) と「細部のモデルもう少しこだわって」で足したもの:
  星の傷  黒い石の胴・腕・根に、割れ目から星空がのぞく青白い筋 (star) と、筋の曲がり目に星の粒。胸と肩に星座の線
  細部    角の金の輪の飾り、頭のまわりの細い金の棘の冠、前腕の金の腕輪、右手の指輪、肩当ての縁の金の縫い、
          胴を巻く二本目の鎖、門の内側の星の粒と、二列目の羽
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tower_master_kit import *  # noqa
from tower_master_kit import _mk  # noqa

# 星の傷の材質 (光る。割れ目の奥の星空: 深い青から白)
STARC = [(70, 100, 190), (130, 170, 240), (200, 225, 255), (246, 250, 255)]
PAL = make_pal(dict(BASE_MATS, star=(STARC, 0.9, dict(crack=0.0, noise=0.04, min=1))), glow=["void", "dawn", "star"])


def scar(bone, name, pts, r=5.0, nodes=True):
    """星の傷: 折れ曲がる細い光の筋。曲がり目に星の粒"""
    f = part(name, tube(bone, "star", pts, ra=r, sides=4, hint=(0, 0, 1)))
    if nodes:
        for i, q in enumerate(pts[1:-1]):
            f += part(f"{name}_n{i}", ellipsoid(bone, "star", tuple(q), (r * 2.0, r * 2.0, r * 1.4), segs=6, rings=3))
    return f


def constellation(bone, name, pts, r=2.6):
    """星座: 星の粒を細い線でつなぐ"""
    f = []
    for i, q in enumerate(pts):
        f += part(f"{name}_s{i}", ellipsoid(bone, "star", tuple(q), (r * 2.4, r * 2.4, r * 1.6), segs=6, rings=3))
    for i in range(len(pts) - 1):
        f += part(f"{name}_l{i}", tube(bone, "star", [pts[i], pts[i + 1]], ra=r * 0.55, sides=4, hint=(0, 0, 1)))
    return f


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
    # ---- 星の傷 (A): 胴・根・左腕の黒い石の割れ目から星空がのぞく
    F += scar("body", "scar_t0", [(-140, 330, -128), (-112, 290, -150), (-128, 246, -150), (-96, 200, -160), (-118, 150, -146)])
    F += scar("body", "scar_t1", [(120, 320, -132), (146, 276, -136), (118, 236, -152), (136, 180, -146)])
    F += scar("body", "scar_t2", [(-60, 140, -150), (-20, 110, -150), (24, 124, -152), (60, 92, -148)])
    F += scar("body", "scar_b0", [(-110, 330, 104), (-70, 260, 116), (-100, 190, 120), (-60, 120, 114)])
    F += scar("body", "scar_b1", [(90, 300, 108), (130, 230, 114), (100, 160, 118)])
    F += scar("legs", "scar_r0", [(-100, 60, -128), (-70, 10, -146), (-110, -60, -134), (-80, -130, -124)])
    F += scar("legs", "scar_r1", [(80, 70, -132), (120, 0, -140), (90, -80, -132)])
    F += constellation("body", "const_chest", [(-40, 330, -126), (-12, 312, -150), (30, 322, -142), (52, 298, -150), (34, 276, -156)])
    # 左の肩当て (黒い石) の上の面に星座 (肩当ての楕円 中心 (-168, 366, -10)、半径 (92, 40, 90) の上の面に置く)
    sh = []
    for x, z in ((-120, -40), (-150, -10), (-184, 14), (-206, 40), (-196, -50)):
        u = ((x + 168) / 92.0) ** 2 + ((z + 10) / 90.0) ** 2
        sh.append((x, 366 + 40 * math.sqrt(max(0.0, 1 - u)) + 3, z))
    F += constellation("arm_l", "const_sh", sh)
    # ---- 細部: 角の金の輪、頭の金の棘の冠、腕輪、肩の縫い、二本目の鎖
    for s, pts in ((1, [(150, 500, -20), (226, 540, -50), (262, 580, -120)]), (-1, [(-150, 500, -20), (-226, 540, -50), (-262, 580, -120)])):
        for j, q in enumerate(pts):
            F += part(f"hornring{s}{j}", ellipsoid("head", "gold", q, (30 - j * 6, 12, 30 - j * 6), segs=10, rings=3))
    for i in range(9):
        a = math.radians(-64 + i * 16)
        base = (math.sin(a) * 86, 470, -30 - math.cos(a) * 92)
        F += part(f"crownspk{i}", spike("head", "gold", base, (math.sin(a) * 0.35, 1, -math.cos(a) * 0.35), 46 + (14 if i % 2 == 0 else 0), 6, sides=4, rings=2))
        if i % 2 == 0:
            F += part(f"crowngem{i}", ellipsoid("head", "star", (base[0] * 1.02, 478, base[2] * 1.02), (7, 7, 6), segs=6, rings=3))
    for s, fb in ((1, "fore_r"), (-1, "fore_l")):
        for j, t in enumerate((0.15, 0.55)):
            px = s * (205 + 5 * t)
            py = 118 - 214 * t
            pz = -122 - 194 * t
            F += part(f"bracelet{s}{j}", ellipsoid(fb, "gold", (px, py, pz), (66, 18, 66), segs=14, rings=3, rot=rot_x(-40)))
        F += part(f"spaulrim{s}", hoop("arm_r" if s > 0 else "arm_l", "gold", 352, 94, 92, cx=s * 168, zc=-10, r=6, n=18))
    F += chain_path("body", "wrap2", [(150, 356, -146), (80, 290, -190), (-40, 200, -192), (-150, 110, -160)], step=26, r=8, mat="gold")
    # ---- 門の内側の星の粒と、二列目の羽
    for i in range(36):
        a = 2 * math.pi * i / 36 + 0.3
        rr = 60 + (i * 47 % 220)
        F += part(f"gatestar{i}", ellipsoid("rift", "star", (math.sin(a) * rr, 430 + math.cos(a) * rr, 296), (5 + i % 3 * 2, 5 + i % 3 * 2, 3), segs=6, rings=2))
    for i in range(16):
        a = 2 * math.pi * i / 16
        c = np.array([math.sin(a) * 330, 430 + math.cos(a) * 330, 296])
        d = np.array([math.sin(a), math.cos(a), 0.0])
        F += part(f"feather2_{i}", blade("rift", "robe" if i % 2 == 0 else "gold", [c, c + d * 50 + (0, 0, 6), c + d * 92 + (0, 0, 10)],
                                         [10, 13, 0], [3, 4, 0], hint=(0, 0, 1)))
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
