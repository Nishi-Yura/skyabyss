# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) 作り直し 案 A「天秤の巨人」。今の形の考え (上 = 天の衣と光輪、下 = 淵の黒い石と鎖、白い布の顔) を彫り直す。
  衣      ひだのある白い衣、胸に金の肋の胸当て、肩に三枚重ねの肩当て (右は金、左は黒い石)
  顔      白い布で覆い、口の所が裂けて開く (中に朝の光)。角は節のある金と黒、角のあいだに天秤の印
  光輪    金の輪二重と、長短 24 枚の羽
  下      黒い石の脚、膝から紫の結晶、腰から淵へ鎖
  背      あいだの門 (黒い石の欠片の輪・金の縁・紫の渦)
共通の骨と合図の姿は tower_master_kit.py。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tower_master_kit import *  # noqa


def build(k):
    F = []
    # ---- 下半身 (黒い石)
    hips = [oring(-30, 150, 110, 110, n=18), oring(20, 162, 122, 116, n=18), oring(70, 144, 102, 100, n=18)]
    F += part("hips", loft("legs", "stone", hips, caps=("stone", "stone")))
    for s in (-1, 1):
        F += part(f"thigh{s}", tube("legs", "stone", [(s * 92, 30, -40), (s * 112, 62, -120), (s * 120, 58, -205)], ra=[72, 70, 58], sides=10, hint=(1, 0, 0)))
        F += part(f"knee{s}", ellipsoid("legs", "stone", (s * 120, 60, -212), (60, 58, 56), segs=10, rings=5))
        F += part(f"shin{s}", tube("legs", "stone", [(s * 120, 40, -232), (s * 120, -30, -244), (s * 116, -100, -248), (s * 112, -150, -250)],
                                   ra=[54, 50, 44, 38], sides=10, hint=(1, 0, 0)))
        F += part(f"foot{s}", ellipsoid("legs", "stone", (s * 112, -162, -276), (46, 22, 62), segs=10, rings=4))
        # 膝の結晶
        for j, (dx, dy, dz, ln) in enumerate(((20, 40, -20, 70), (40, 10, -30, 55), (-10, 46, 10, 48))):
            F += part(f"kc{s}{j}", spike("legs", "void", (s * (120 + dx * 1.1), 60 + dy * 1.1, -212 + dz * 1.1 - 20), (s * dx, dy + 30, dz - 20), ln, 14, sides=5, rings=3))
        # 割れ目
        F += part(f"crack{s}", tube("legs", "void", [(s * 172, 86, -196), (s * 186, 70, -160), (s * 176, 88, -112), (s * 184, 62, -70)], ra=4.5, sides=4, hint=(0, 1, 0)))
        F += part(f"crackb{s}", tube("legs", "void", [(s * 168, 20, -250), (s * 170, -40, -254), (s * 160, -100, -262)], ra=4, sides=4, hint=(0, 1, 0)))
        F += chain("legs", s * 132, -40, -420, 20)
        F += chain("legs", s * 62, -40, -380, 90)
    # 腰布 (前に垂れる白い布、ひだ)
    apron = [oring(70, 120, 108, 0, n=16, zc=-10, fold=0.06, fn=7, front_only=True),
             oring(10, 110, 140, 0, n=16, zc=-30, fold=0.08, fn=7, front_only=True),
             oring(-60, 96, 160, 0, n=16, zc=-60, fold=0.10, fn=7, front_only=True)]
    for r in apron:
        for p in r:
            if p[2] > -20:
                p[2] = -20 + (p[2] + 20) * 0.1
    F += part("apron", loft("legs", "robe", apron, caps=("robe", "robe")))
    F += part("apronhem", hoop("legs", "gold", -58, 94, 40, zc=-118, r=6, n=12))

    # ---- 上半身 (衣)
    torso = [oring(60, 150, 100, 100, n=24, fold=0.03, fn=10), oring(120, 138, 98, 96, n=24, fold=0.02, fn=10),
             oring(190, 150, 104, 98, n=24), oring(250, 164, 110, 100, n=24), oring(305, 172, 98, 96, n=24),
             oring(338, 124, 74, 70, n=24), oring(356, 54, 42, 40, n=24)]
    F += part("torso", loft("body", "robe", torso, caps=("robe", "robe")))
    F += part("belt", hoop("body", "gold", 68, 154, 104, r=9))
    F += part("collar", hoop("body", "gold", 332, 150, 90, r=10))
    # 胸当て: 金の肋 (前の弧) と縦の背骨
    for j, y in enumerate((214, 238, 262, 286)):
        w = 128 + j * 8
        arc = [(math.sin(a) * w, y + 6 * math.cos(a * 2), -math.cos(a) * (112 + j * 2) - 4) for a in np.linspace(-1.25, 1.25, 11)]
        F += part(f"rib{j}", tube("body", "gold", arc, ra=6, rb=4, sides=4, hint=(0, 1, 0)))
    F += part("sternum", tube("body", "gold", [(0, 200, -108), (0, 300, -104)], ra=8, rb=5, sides=4, hint=(1, 0, 0)))
    F += part("seam", tube("body", "gold", [(0, 78, -103), (0, 140, -99), (0, 196, -106)], ra=5, sides=4, hint=(1, 0, 0)))
    # 衣の縦のひだ (前の裾)
    for j, x in enumerate((-110, -70, 70, 110)):
        F += part(f"pleat{j}", tube("body", "robe", [(x, 72, -96 - abs(x) * 0.0), (x * 1.05, 130, -92), (x * 1.08, 180, -94)], ra=[8, 6, 2], sides=4, hint=(0, 0, 1)))
    # 左の胸から肩へ、黒い石が衣を食う
    F += part("stonepatch", ellipsoid("body", "stone", (-128, 286, -48), (72, 62, 84), segs=10, rings=5,
                                      deform=lambda p: p * (1 + 0.12 * math.sin(p[0] * 0.09) * math.cos(p[2] * 0.07))))
    F += part("stonecrack", tube("body", "void", [(-60, 302, -96), (-90, 262, -102), (-70, 222, -104), (-100, 192, -100)], ra=4, sides=4, hint=(0, 0, 1)))
    # 胸の核と天秤の飾り
    F += part("core", ellipsoid("core", "dawn", (0, 250, -116), (34, 34, 20), segs=14, rings=7))
    F += part("corering", vhoop("core", "gold", 0, 250, -116, 46, r=6, n=20))
    F += part("corering2", vhoop("core", "gold", 0, 250, -112, 60, r=3.5, n=24))
    F += part("beam", tube("core", "gold", [(-92, 306, -124), (92, 306, -124)], ra=5, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        F += part(f"panline{s}", tube("core", "gold", [(s * 92, 306, -124), (s * 92, 276, -124)], ra=2.5, sides=4, hint=(1, 0, 0)))
        F += part(f"pan{s}", ellipsoid("core", "gold", (s * 92, 272, -124), (22, 5, 14), segs=10, rings=3))

    # ---- 腕
    for s, mat, trim in ((1, "robe", "gold"), (-1, "stone", "iron")):
        a, f_ = ("arm_r", "fore_r") if s > 0 else ("arm_l", "fore_l")
        F += arm_parts(s, mat)
        # 三枚重ねの肩当て
        plate = "gold" if s > 0 else "stone"
        for j in range(3):
            F += part(f"pauld{s}{j}", ellipsoid(a, plate, (s * (166 + j * 16), 380 - j * 26, -10), (86 - j * 6, 22, 84 - j * 4), segs=12, rings=4,
                                                rot=rot_z(s * (24 + j * 8))))
        F += part(f"cuff{s}", hoop(f_, trim, -40, 50, 50, cx=s * 209, zc=-262, r=8, n=14))
        F += part(f"cuff2{s}", hoop(f_, trim, -70, 46, 46, cx=s * 210, zc=-290, r=6, n=14))
        F += hand_parts(s, mat, nail="gold" if s > 0 else "void", claws=(s < 0), ring_mat="gold" if s > 0 else None)
        if s < 0:
            F += part("shackle", hoop(f_, "iron", -95, 48, 48, cx=-210, zc=-325, r=10, n=14))
            F += chain(f_, -210, -110, -300, -300, step=22, r=7)
            F += part("armcrack", tube(f_, "void", [(-244, 60, -170), (-254, 0, -230), (-240, -50, -280)], ra=4, sides=4, hint=(0, 1, 0)))
            F += part("armcrack2", tube(a, "void", [(-232, 250, -40), (-250, 200, -70), (-240, 160, -96)], ra=4, sides=4, hint=(0, 1, 0)))
        else:
            F += part("armband", hoop(a, "gold", 230, 64, 64, cx=182, zc=-60, r=7, n=14))
            F += part("armband2", hoop(a, "gold", 210, 62, 62, cx=186, zc=-70, r=4, n=14))
    F += palm_sigil(-1)

    # ---- 頭: 白い布で覆った顔 (口の所で裂けて開く)、節のある角、角のあいだの天秤の印
    F += part("skull", ellipsoid("head", "veil", (0, 418, -20), (56, 60, 58), segs=14, rings=7))
    veil = [oring(462, 62, 64, 42, n=20), oring(430, 66, 72, 48, n=20), oring(400, 64, 72, 48, n=20)]
    F += part("veil", loft("head", "robe", veil, caps=("robe", "robe")))
    F += part("veilband", hoop("head", "gold", 460, 62, 63, zc=-11, r=5))
    F += part("veilband2", hoop("head", "gold", 448, 63, 64, zc=-11, r=3))
    # 口 (中の光は頭、下の布は顎)
    F += part("mouthglow", ellipsoid("head", "dawn", (0, 386, -66), (32, 9, 10), segs=10, rings=3))
    jaw = [oring(394, 62, 70, 44, n=20, zc=-4), oring(370, 56, 66, 40, n=20, zc=-6, fold=0.05, fn=6, front_only=True),
           oring(340, 44, 56, 30, n=20, zc=-8, fold=0.08, fn=6, front_only=True)]
    F += part("jawveil", loft("jaw", "robe", jaw, caps=("robe", "robe")))
    F += part("jawhem", hoop("jaw", "gold", 344, 46, 58, zc=-10, r=4, n=14))
    for s, mat, rid in ((1, "gold", "robe"), (-1, "stone", "void")):
        F += horn("head", "horn", mat, s, [(s * 48, 460, -10), (s * 92, 506, 0), (s * 112, 566, 18), (s * 100, 620, 42), (s * 78, 652, 64)],
                  [20, 16, 12, 7, 0], ridge=rid)
    F += sigil_mark("head", "hsig", (0, 548, -24), 24, r=3.5, spokes=0)
    F += sigil_mark("sigil", "stoke", (0, 650, -16), 70, r=5.0, spokes=8)

    # ---- 光輪
    F += part("haloring", vhoop("halo", "gold", 0, 410, 112, 222, r=9, n=40))
    F += part("haloring2", vhoop("halo", "gold", 0, 410, 116, 184, r=5, n=36))
    for i in range(24):
        a = 2 * math.pi * i / 24
        longf = i % 2 == 0
        r0, r1 = 222, (330 if longf else 280)
        cx, cy = math.sin(a) * r0, 410 + math.cos(a) * r0
        ox, oy = math.sin(a) * r1, 410 + math.cos(a) * r1
        tang = (math.cos(a), -math.sin(a), 0.0)
        F += part(f"feather{i}", blade("halo", "gold" if longf else "robe",
                                       [(cx, cy, 114), ((cx * 2 + ox) / 3, (cy * 2 + oy) / 3, 117), ((cx + ox * 2) / 3, (cy + oy * 2) / 3, 119), (ox, oy, 121)],
                                       [10, 16, 12, 0], [4, 5, 4, 0], hint=(0, 0, 1)))
    # ---- 背の門
    F += rift_gate("stone")
    return finish(F)


SPEC = dict(
    id="tower_master_a", short="あいだに座すもの A", kind="超大型レイドの主 作り直し (54 の 2.5)",
    title="あいだに座すもの 案 A「天秤の巨人」— 今の考えを彫り直す",
    sub=["上は天 (ひだのある白い衣・金の肋の胸当て・三枚の肩当て・二重の光輪と羽 24)、下と左腕は淵 (黒い石・膝の紫の結晶・鎖)。白い布の顔は口の所で裂けて開く。",
         "Annihilation の合図: 指を立てる (星屑)・掌の印 (群れ)・角の上の印 (高まり)・口を開く (月)・伏す (側の目)・倒れて門へ引き戻される。背にあいだの門。"],
    labels=["天秤の巨人"], rows=ROWS,
    options={"a": dict(name="天秤の巨人", desc="今の考えのまま彫り直す。ひだの衣・金の肋・肩当て・羽 24 の光輪・膝の結晶・背の門。")},
    extra="骨 19 本 (指・指の束・掌の印・角の上の印・口・門を足した)。光るのは胸の核・口の中・印・紫の割れ目と結晶・門の渦。",
    budget="主 9000〜15000", pw=600, later="段が進むと石の割れ目が広がり、光輪が赤くなる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
