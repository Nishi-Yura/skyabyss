# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) 作り直し 案 B「割れた神像」。座った巨大な神像が、真ん中で縦に割れている。
  右半分   白い大理石と金 (天)。穏やかな仮面の顔、金の日輪の冠、肩から大理石の翼 (羽 5 枚)、金の腕輪と指輪
  左半分   黒い石と紫 (淵)。髑髏のような顔の半分に紫の目、折れた黒い角、肩と腕から紫の結晶、枷と鎖
  割れ目   顔から胸、腰まで縦に光る (上は朝の光、下は紫)。胸で菱形に開き、中に核と天秤
  下       衣のひだが膝から玉座の段へ流れる (右は大理石、左は黒い石)
  光輪     右は金の日輪の半円、左は砕けた石の欠片が輪の形に浮く
  背       あいだの門 (右の欠片は大理石、左は黒い石)
共通の骨と合図の姿は tower_master_kit.py。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tower_master_kit import *  # noqa
from tower_master_kit import _mk  # noqa


def split_loft(bone, rings, right, left, caps=(True, True)):
    """輪をつなぎ、面の真ん中の x で材質を分ける (x > 0 = 右 = right)"""
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    faces = []
    for i in range(len(rings) - 1):
        ref = (cen[i] + cen[i + 1]) / 2
        for j in range(k):
            quad = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            m = right if np.mean([q[0] for q in quad]) > 0 else left
            f = _mk(quad, bone, m, ref)
            if f is not None:
                faces.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap:
            other = cen[1] if idx == 0 else cen[-2]
            f = _mk(list(rings[idx]), bone, right, other)
            if f is not None:
                faces.append(f)
    return faces


def seam(bone, name, rings, idx, mat, out=3.0, r=4.0):
    """輪の idx 番の頂点を縦に結ぶ光る割れ目 (表面から out だけ浮かせる)"""
    pts = []
    for rg in rings:
        p = np.array(rg[idx], float)
        c = np.array(rg, float).mean(0)
        d = p - c
        d[1] = 0
        n = np.linalg.norm(d)
        pts.append(p + (d / n * out if n > 1e-6 else 0))
    return part(name, tube(bone, mat, pts, ra=r, sides=4, hint=(1, 0, 0)))


def build(k):
    F = []
    # ---- 下半身: 腰・腿・脛 (右 大理石、左 黒い石) と、膝から段へ流れる衣
    hips = [oring(-30, 150, 112, 110, n=20), oring(20, 164, 124, 116, n=20), oring(70, 146, 104, 100, n=20)]
    F += part("hips", split_loft("legs", hips, "marble", "stone"))
    for s in (-1, 1):
        m = "marble" if s > 0 else "stone"
        F += part(f"thigh{s}", tube("legs", m, [(s * 92, 30, -40), (s * 112, 62, -120), (s * 120, 58, -205)], ra=[74, 72, 60], sides=10, hint=(1, 0, 0)))
        F += part(f"knee{s}", ellipsoid("legs", m, (s * 120, 60, -212), (62, 58, 56), segs=10, rings=5))
        F += part(f"foot{s}", ellipsoid("legs", m, (s * 112, -162, -278), (48, 22, 64), segs=10, rings=4))
        # 脛を包む衣のひだ (外側)
        drape = [oring(y, 66 - (40 - y) * 0.02, 62, 56, n=14, cx=s * 122, zc=-246, fold=0.12, fn=5, fph=s) for y in (30, -40, -110, -164)]
        F += part(f"drape{s}", loft("legs", m, drape, caps=(m, m)))
    # 膝のあいだに垂れる前の布 (割れ目で二色)
    front = []
    for y, w, z in ((60, 96, -216), (0, 104, -236), (-80, 112, -252), (-166, 120, -262)):
        front.append([np.array([w * math.sin(a), y, z - 18 * math.cos(a) + 6 * math.sin(5 * a)]) for a in np.linspace(-math.pi / 2, math.pi / 2, 13)]
                     + [np.array([w * math.sin(a), y, z + 22]) for a in np.linspace(math.pi / 2, -math.pi / 2, 5)][1:-1])
    F += part("lapcloth", split_loft("legs", front, "marble", "stone"))
    F += part("laphem", tube("legs", "gold", [(w * math.sin(a), -164, -262 - 18 * math.cos(a) - 5) for w, a in [(120, a) for a in np.linspace(0.15, math.pi / 2, 7)]],
                             ra=5, sides=4, hint=(0, 1, 0)))
    F += part("lapseam", tube("legs", "void", [(0, 70, -236), (0, 0, -258), (0, -80, -274), (0, -166, -284)], ra=5, sides=4, hint=(1, 0, 0)))
    for s in (-1, 1):
        F += chain("legs", s * 140, -40, -420, 30, mat="iron" if s < 0 else "gold")

    # ---- 胴 (割れ目で二色。胸で菱形に開く)
    torso = [oring(60, 152, 102, 100, n=24), oring(130, 140, 100, 96, n=24), oring(200, 152, 108, 98, n=24),
             oring(256, 168, 116, 100, n=24), oring(306, 174, 100, 96, n=24), oring(340, 126, 76, 70, n=24), oring(358, 56, 44, 40, n=24)]
    F += part("torso", split_loft("body", torso, "marble", "stone"))
    F += seam("body", "seamf", torso[:6], 0, "dawn", out=2.0, r=5)
    F += seam("body", "seamb", torso[:6], 12, "void", out=2.0, r=4)
    # 胸の菱形の開き (縁は金と紫)
    dia = [(0, 300, -112), (46, 250, -118), (0, 196, -114), (-46, 250, -118), (0, 300, -112)]
    F += part("diaR", tube("core", "gold", dia[:3], ra=6, sides=4, hint=(0, 0, 1)))
    F += part("diaL", tube("core", "void", dia[2:], ra=6, sides=4, hint=(0, 0, 1)))
    F += part("core", ellipsoid("core", "dawn", (0, 250, -122), (28, 34, 16), segs=12, rings=6))
    F += part("beam", tube("core", "gold", [(-70, 314, -122), (70, 314, -122)], ra=4.5, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        F += part(f"panline{s}", tube("core", "gold", [(s * 70, 314, -122), (s * 70, 288, -122)], ra=2.5, sides=4, hint=(1, 0, 0)))
        F += part(f"pan{s}", ellipsoid("core", "gold" if s > 0 else "void", (s * 70, 284, -122), (18, 4, 12), segs=10, rings=3))
    # 右の胸の金の帯、左の胸の紫の割れ目
    F += part("sash", tube("body", "gold", [(20, 330, -96), (90, 290, -110), (140, 220, -104), (150, 150, -96), (130, 80, -102)], ra=7, rb=4, sides=4, hint=(0, 0, 1)))
    for j, pts in enumerate(([(-30, 320, -100), (-70, 282, -112), (-60, 240, -118), (-96, 206, -110)],
                             [(-70, 282, -112), (-120, 268, -104), (-150, 236, -94)],
                             [(-60, 160, -98), (-100, 130, -96), (-120, 96, -100)])):
        F += part(f"lcrack{j}", tube("body", "void", pts, ra=4, sides=4, hint=(0, 0, 1)))
    F += part("belt", hoop("body", "gold", 68, 156, 106, r=8))
    F += part("collar", hoop("body", "gold", 334, 150, 92, r=9))

    # ---- 腕 (右 大理石・金、左 黒い石・結晶)
    for s in (1, -1):
        m = "marble" if s > 0 else "stone"
        a, f_ = ("arm_r", "fore_r") if s > 0 else ("arm_l", "fore_l")
        F += arm_parts(s, m)
        F += hand_parts(s, m, nail="gold" if s > 0 else "void", claws=(s < 0), ring_mat="gold" if s > 0 else None)
        if s > 0:
            for j, (y, cx, zc, r) in enumerate(((236, 182, -62, 66), (216, 186, -72, 64), (-30, 209, -252, 50), (-62, 210, -282, 46))):
                F += part(f"armlet{j}", hoop(a if j < 2 else f_, "gold", y, r, r, cx=cx, zc=zc, r=6 if j % 2 == 0 else 4, n=14))
            # 肩の大理石の翼 (羽 5 枚。体の骨に付けて腕の動きと離す)
            for i in range(5):
                ang = math.radians(20 + i * 17)
                L = 300 - i * 30
                base = np.array([170, 360, 40.0])
                tip = base + np.array([math.cos(ang) * L * 0.9, math.sin(ang) * L, 60 + i * 10])
                mid = (base + tip) / 2 + np.array([0, 20, 20])
                F += part(f"wing{i}", blade("body", "marble" if i % 2 == 0 else "gold", [base, mid, tip], [24, 30, 0], [7, 8, 0], hint=(0, 0, 1)))
        else:
            # 肩と前腕の結晶、枷と鎖
            for j, (base, d, ln) in enumerate((((-200, 360, -10), (-0.6, 1, 0.2), 120), ((-170, 380, 30), (-0.2, 1, 0.5), 90),
                                               ((-226, 330, -40), (-1, 0.6, -0.2), 80), ((-180, 386, -40), (0.1, 1, -0.4), 70))):
                F += part(f"lcrys{j}", spike(a, "void", base, d, ln, 22, sides=5, rings=3))
            for j, (base, d, ln) in enumerate((((-250, 40, -190), (-1, 0.3, 0.1), 60), ((-246, -20, -240), (-1, 0.5, -0.1), 48))):
                F += part(f"fcrys{j}", spike(f_, "void", base, d, ln, 14, sides=5, rings=3))
            F += part("shackle", hoop(f_, "iron", -95, 48, 48, cx=-210, zc=-325, r=10, n=14))
            F += chain(f_, -210, -110, -300, -300, step=22, r=7)
    F += palm_sigil(-1)

    # ---- 頭: 二つ割りの顔
    head = [oring(470, 40, 40, 44, n=20), oring(450, 58, 58, 60, n=20), oring(420, 62, 66, 62, n=20), oring(392, 58, 64, 56, n=20)]
    # 右 = 鼻筋と頬、左 = 眼窩のくぼみ (頂点をずらす)
    for rg in head:
        for i, p in enumerate(rg):
            a = 2 * math.pi * i / 20
            if 0 < i < 4 and rg is head[2]:
                p[2] -= 4
            if 16 < i < 20 and rg is head[2]:
                p[2] += 10
    F += part("skull", split_loft("head", head, "marble", "stone"))
    F += seam("head", "seamh", head, 0, "dawn", out=2.0, r=4)
    F += part("noseR", tube("head", "marble", [(6, 440, -62), (8, 410, -70), (4, 400, -68)], ra=[3, 7, 5], sides=4, hint=(1, 0, 0)))
    F += part("eyeR", tube("head", "gold", [(16, 428, -63), (30, 426, -60), (44, 429, -54)], ra=2.6, sides=4, hint=(0, 1, 0)))
    F += part("eyeL", ellipsoid("head", "void", (-28, 424, -56), (10, 8, 5), segs=8, rings=3))
    F += part("browL", tube("head", "stone", [(-10, 440, -62), (-30, 444, -60), (-50, 436, -50)], ra=6, sides=4, hint=(0, 1, 0)))
    jaw = [oring(394, 56, 62, 54, n=20, zc=-2), oring(372, 46, 58, 44, n=20, zc=-6), oring(354, 30, 46, 30, n=20, zc=-10)]
    F += part("jaw", split_loft("jaw", jaw, "marble", "stone"))
    F += seam("jaw", "seamj", jaw, 0, "dawn", out=2.0, r=4)
    for s in (-1, 1):
        F += part(f"teeth{s}", tube("jaw", "teeth", [(s * 6, 392, -60), (s * 22, 392, -58), (s * 38, 393, -50)], ra=4, rb=2.5, sides=4, hint=(0, 1, 0)))
    F += part("mouthglow", ellipsoid("head", "dawn", (0, 386, -48), (32, 6, 12), segs=10, rings=3))
    # 冠: 右に金の日輪の光、左に折れた黒い角
    for i in range(6):
        ang = math.radians(10 + i * 15)
        base = (math.cos(ang) * 46, 456 + math.sin(ang) * 20, -14)
        F += part(f"crown{i}", spike("head", "gold", base, (math.cos(ang), 1.2 + math.sin(ang), 0.1), 60 + (i % 2) * 34, 9, sides=5, rings=2))
    F += horn("head", "hornR", "gold", 1, [(42, 464, -8), (82, 516, 0), (98, 576, 20), (84, 630, 46), (62, 660, 68)], [18, 14, 10, 6, 0], ridge="marble")
    F += horn("head", "hornL", "stone", -1, [(-42, 464, -8), (-84, 512, 0), (-104, 560, 16), (-110, 586, 26)], [18, 15, 12, 4], ridge="void")
    F += part("hornshard", ellipsoid("head", "void", (-112, 600, 30), (10, 16, 8), segs=6, rings=3))
    F += sigil_mark("head", "hsig", (0, 552, -22), 22, r=3.4, spokes=0)
    F += sigil_mark("sigil", "stoke", (0, 650, -16), 70, r=5.0, spokes=8)

    # ---- 光輪: 右は金の日輪の半円、左は砕けた石の欠片
    F += part("haloR", vhoop("halo", "gold", 0, 410, 112, 224, r=9, n=40, a0=0, a1=180))
    F += part("haloR2", vhoop("halo", "gold", 0, 410, 116, 188, r=5, n=36, a0=6, a1=174))
    for i in range(12):
        a = math.radians(8 + i * 15)
        c0 = (math.sin(a) * 224, 410 + math.cos(a) * 224, 114)
        L = 110 if i % 2 == 0 else 70
        F += part(f"ray{i}", spike("halo", "gold" if i % 2 == 0 else "marble", c0, (math.sin(a), math.cos(a), 0.05), L, 13, sides=4, rings=2))
    for i in range(11):
        a = math.radians(-8 - i * 16)
        c0 = (math.sin(a) * (224 + 14 * math.sin(i * 2.1)), 410 + math.cos(a) * (224 + 14 * math.sin(i * 2.1)), 114)
        size = 26 + 8 * math.sin(i * 1.3)
        F += part(f"frag{i}", ellipsoid("halo", "stone", c0, (size, size * 0.7, 16), segs=6, rings=3, rot=rot_z(-a + i),
                                        deform=lambda p, i=i: p * (1 + 0.2 * math.sin(p[0] * 0.2 + i))))
        if i % 3 == 1:
            F += part(f"fragglow{i}", ellipsoid("halo", "void", (c0[0], c0[1], c0[2] - 14), (size * 0.35, size * 0.25, 4), segs=6, rings=2))
    F += rift_gate("split")
    return finish(F)


SPEC = dict(
    id="tower_master_b", short="あいだに座すもの B", kind="超大型レイドの主 作り直し (54 の 2.5)",
    title="あいだに座すもの 案 B「割れた神像」— 真ん中で縦に割れた巨大な神像",
    sub=["右半分は白い大理石と金 (穏やかな仮面・日輪の冠・大理石の翼)、左半分は黒い石と紫 (髑髏の顔・折れた角・結晶)。割れ目が顔から腰まで光り、胸で菱形に開いて核と天秤が見える。",
         "光輪も右は金の日輪、左は砕けた欠片。Annihilation の合図 (指・掌の印・角の上の印・口・伏す・門へ) は A と同じ骨。"],
    labels=["割れた神像"], rows=ROWS,
    options={"a": dict(name="割れた神像", desc="右は大理石と金、左は黒い石と紫。割れ目が縦に光る。右肩に翼、左肩に結晶。")},
    extra="骨 19 本 (A と同じ)。光るのは割れ目・胸の核・口・紫の目と結晶・印・門の渦。",
    budget="主 9000〜15000", pw=600, later="段が進むと割れ目が広がり (幅と光)、左の黒い石が右へ食い込む。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
