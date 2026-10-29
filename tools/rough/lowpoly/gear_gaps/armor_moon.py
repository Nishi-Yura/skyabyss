"""六章「月」の防具「月渡りの装束」(4 部位、レア) の一品一案。前のループの自分たちが月を渡った旅装を、月の砂の布と銀で仕立て直した姿。
部品と塗りは淵潜りの鎧 v2 (armor_abyss_v2.py) を借り、月の言葉 (青白い月の砂の布・藍の裏地・銀・星の光) に塗り替える。

  兜      深い頭巾 (月の砂の布、縁は藍)、額に銀の細い冠と下弦の三日月の飾り (真ん中に星の光)、後ろへ長い垂れ
  胸と腕  高い立ち襟の長い外套 (背の裾は二つに割れて膝下まで、前は腿の中ほど)、胸の三日月の留め金 (星の光)、
          右肩から左腰への銀の飾り帯、両肩の銀の三枚の肩当て、袖の折り返しと銀の籠手
  脚      布を巻いた脚衣、膝の銀の円盤と、脛の巻き布
  靴      柔らかい長靴 (藍の折り返し)、爪先と踵の銀の三日月の覆い、甲の星の留め

実行: python -I tools/rough/lowpoly/gear_gaps/armor_moon.py
出力: docs/review/roughs_gear_gaps/raw/armor_moon/a/<部位>_{mesh.json,atlas.png,glowmask.png}・icons/a_<部位>_32.png
      docs/review/roughs_gear_gaps/moon_walker_armor/opt_a.png
入れ方: python -I tools/install/install_moon_gear.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import armor_abyss_v2 as A  # noqa: E402
from armor_abyss_v2 import (bx, crv, rot_x, rot_z, ARM, LEG, BOOT, sring, dloft, bplate, ngon, drape, lume, M_SIDE, E, N)  # noqa: E402

ROOT = A.ROOT
A.OUT = ROOT / "docs/review/roughs_gear_gaps/moon_walker_armor"
A.RAW = ROOT / "docs/review/roughs_gear_gaps/raw/armor_moon"
A.NAMES["a"] = "月渡りの装束"
A.GLOWK["a"] = "star"
A.CLOTH_OF["a"] = "mcloth"
A.DESC["a"] = ("月渡りの装束: 深い頭巾と額の銀の冠 (下弦の三日月に星)、高い立ち襟の長い外套 (背の裾は二つに割れる)、胸の三日月の留め金、"
               "銀の飾り帯と三枚の肩当て、膝の銀の円盤、爪先の銀の三日月の柔らかい長靴")
_ = np.array
A.PALS.update(
    # 月の砂の布 (青みの白)・藍の裏地・銀の板・明るい銀 (brass の代わり)・革の代わりに藍の帯
    mcloth=_([(104, 110, 130), (128, 134, 154), (152, 158, 178), (176, 182, 200), (198, 204, 218), (220, 224, 234)]),
    vcape=_([(14, 16, 34), (22, 26, 52), (34, 40, 74), (48, 56, 98), (66, 76, 124)]),
    plate=_([(46, 52, 66), (72, 80, 98), (104, 112, 132), (138, 146, 166), (172, 180, 198), (204, 210, 224), (232, 236, 244)]),
    brass=_([(70, 78, 96), (104, 112, 132), (140, 150, 170), (176, 184, 202), (206, 212, 226), (230, 234, 242), (248, 250, 254)]),
    verd=_([(140, 150, 170), (176, 184, 202), (206, 212, 226), (230, 234, 242)]),
    rust=_([(86, 92, 110), (104, 112, 132), (122, 130, 150), (140, 150, 170)]),
    leather=_([(18, 20, 40), (28, 32, 60), (40, 46, 82), (56, 64, 106), (76, 86, 132)]),
    G_star=_([(90, 140, 210), (150, 196, 250), (200, 226, 255), (232, 242, 255), (250, 252, 255)]),
)
A.scene.GLOW_RGB["ad2_star"] = (190, 220, 255)


def crescent(bone, c, r=1.3, t=0.42, mat="brass", face=(0, 0, -1)):
    """額や靴に付ける三日月 (欠けた輪を細い管で)"""
    import math
    pts = []
    for i in range(9):
        a = math.radians(-120 + i * 240 / 8)
        pts.append((c[0] + math.sin(a) * r, c[1] + math.cos(a) * r, c[2]))
    return crv(bone, mat, pts, [t * 0.4, t * 0.8, t, t, t, t, t, t * 0.8, t * 0.4], sides=5, caps=(True, True))


def m_helmet():
    b = "armorHead"
    f = []
    lv = [(24.2, 5.65, -5.7, 5.8), (26.4, 5.9, -5.9, 6.1), (28.8, 5.95, -5.95, 6.2), (31.0, 5.8, -5.8, 6.1),
          (32.8, 5.5, -5.4, 5.8), (34.1, 4.0, -3.6, 4.6), (34.9, 1.8, -1.2, 2.6)]
    rings = [sring(y, hx, zf, zb, n=16, p=4.0) for (y, hx, zf, zb) in lv]
    for fc in dloft(b, "cloth", rings, caps=(False, "cloth")):
        c = fc.verts.mean(0)
        if fc.verts[:, 1].max() < 30.6 and fc.normal[2] < -0.35 and abs(c[0]) < 4.2:
            continue          # 顔を開ける
        f.append(fc)
    # 頭巾の縁 (藍の折り返し) と、頬の垂れ
    f += bx(b, "cape", (0, 30.9, -5.95), (8.6, 1.0, 0.8), taper=(0.96, 1.0))
    for s in (-1, 1):
        f += bx(b, "cape", (s * 4.6, 27.4, -5.75), (0.9, 7.0, 0.8))
    # 後ろの長い垂れ
    f += drape(b, ["cloth", "cloth", "cloth", "cape"], 0.0, 26.4, 19.0, 5.6, 4.4, 5.2, rows=5, cols=6, fold=0.5, flare_z=1.3, t=0.5)
    # 額の銀の冠 (細い輪) と下弦の三日月、真ん中の星
    f += bx(b, "brass", (0, 31.75, -5.95), (8.0, 0.55, 0.5))
    for s in (-1, 1):
        f += bx(b, "brass", (s * 4.2, 31.6, -4.2), (0.5, 0.5, 3.2))
    f += crescent(b, (0, 33.3, -6.25), r=1.25, t=0.42)
    f += lume(b, (0, 33.3, -6.35), (0.5, 0.5, 0.3))
    return {b: f}


def m_chest():
    B = "armorBody"
    f = []
    f += dloft(B, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0) for y, hx, zf, zb in
                            [(12.0, 4.75, -2.85, 2.8), (15.5, 4.6, -2.75, 2.7), (19.0, 4.7, -2.85, 2.75), (22.6, 4.85, -2.95, 2.85), (24.3, 4.4, -2.75, 2.65)]])
    # 前立て (藍の縁) と、胸の三日月の留め金
    f += bx(B, "cape", (0.8, 18.0, -3.05), (1.0, 11.4, 0.5))
    f += crescent(B, (0.8, 21.4, -3.6), r=1.3, t=0.45)
    f += lume(B, (0.8, 21.4, -3.7), (0.5, 0.5, 0.3))
    for y in (16.0, 18.4):
        f += bx(B, "brass", (0.8, y, -3.45), (0.7, 0.7, 0.5), taper=(0.6, 0.6))
    # 高い立ち襟 (前が開く) と縁
    for fc in dloft(B, "cape", [sring(24.0, 4.5, -3.0, 2.9, n=N, p=2.8), sring(25.6, 4.9, -3.4, 3.3, n=N, p=2.8),
                                sring(27.2, 5.3, -3.7, 3.7, n=N, p=2.8)], mats=["cape", "cape"]):
        c = fc.verts.mean(0)
        if fc.normal[2] < -0.5 and abs(c[0]) < 2.0:
            continue
        f.append(fc)
    f += dloft(B, "trim", [sring(27.1, 5.36, -3.76, 3.76, n=N, p=2.8), sring(27.6, 5.3, -3.7, 3.7, n=N, p=2.8)])
    # 右肩から左腰への銀の飾り帯と、腰の藍の帯
    f += bx(B, "brass", (0, 18.4, -3.25), (0.9, 13.0, 0.4), rot=rot_z(-28))
    f += bx(B, "brass", (0, 18.4, 3.15), (0.9, 13.0, 0.4), rot=rot_z(-28))
    f += dloft(B, "sash", [sring(12.4, 4.95, -3.05, 3.0, n=N, p=3.0), sring(13.8, 4.9, -3.0, 2.95, n=N, p=3.0)])
    f += bplate(B, "plate", "bevel", ngon(1.6, 1.6, 8), c=(-2.6, 13.1, -3.4), h=0.5, ins=0.3)
    # 背の裾 (二つに割れて膝下まで) と前の裾
    for s in (-1, 1):
        f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.6, 3.0, 2.95, 2.3, 2.9, rows=5, cols=4, fold=0.5,
                   flare_z=1.6, t=0.5, hem=[0.0, -0.6, -0.2, -0.9][::s])
        f += drape(B, ["cloth", "cloth", "cape"], s * 2.5, 12.6, 6.4, -2.95, 2.1, 2.5, rows=4, cols=4, fold=0.3,
                   flare_z=0.7, t=0.5, out=-1.0, ov=(0, 0, -1))
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        cx = s * 6.0
        g = dloft(ab, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                                [(24.0, 2.8, -2.85, 2.85), (18.0, 2.55, -2.7, 2.7), (14.8, 2.6, -2.7, 2.7)]])
        # 袖の折り返し (藍の裏地) と銀の籠手
        g += dloft(ab, "cape", [sring(15.2, 2.95, -3.0, 3.0, n=N, p=3.0, cx=cx), sring(13.6, 3.2, -3.25, 3.25, n=N, p=3.0, cx=cx)], mats=["cape"])
        g += dloft(ab, "plate", [sring(13.7, 2.75, -2.8, 2.8, n=N, p=3.0, cx=cx), sring(11.9, 2.7, -2.75, 2.75, n=N, p=3.0, cx=cx)])
        g += lume(ab, (cx + s * 2.8, 12.8, 0), (0.3, 0.9, 0.5))
        # 三枚の銀の肩当て (外へ垂れる)
        for k, (y, hx, hz) in enumerate([(25.2, 2.6, 2.6), (23.9, 3.0, 2.95), (22.6, 3.25, 3.1)]):
            g += dloft(ab, "plate" if k < 2 else "brass", [sring(y, hx, -hz, hz, n=N, p=2.4, cx=cx + s * 0.3 * k),
                                                           sring(y - 1.0, hx + 0.25, -hz - 0.2, hz + 0.2, n=N, p=2.4, cx=cx + s * (0.3 * k + 0.25))],
                       caps=("plate", False))
        out[ab] = g
    return out


def m_legs():
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.05
        g = dloft(lb, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                                [(12.0, 2.55, -2.6, 2.55), (8.0, 2.4, -2.55, 2.5), (4.6, 2.35, -2.5, 2.45)]])
        for y in (5.0, 9.6):    # 脛と腿の藍の巻き布
            g += dloft(lb, "sash", [sring(y, 2.56, -2.68, 2.62, n=N, p=3.0, cx=cx), sring(y + 0.9, 2.54, -2.66, 2.6, n=N, p=3.0, cx=cx)])
        # 膝の銀の円盤と星
        g += bplate(lb, "plate", "bevel", ngon(2.2, 2.4, 10), c=(cx, 7.4, -2.75), h=0.6, ins=0.4, bow=0.4)
        g += lume(lb, (cx, 7.4, -3.2), (0.45, 0.45, 0.3))
        out[lb] = g
    return out


def m_boots():
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.05
        g = dloft(bb, "cloth", [sring(y, hx, zf, zb, n=10, p=2.8, cx=cx) for y, hx, zf, zb in
                                [(6.6, 2.55, -2.65, 2.6), (3.6, 2.55, -2.9, 2.6), (1.8, 2.6, -3.7, 2.65), (0.3, 2.55, -4.2, 2.6)]], caps=(False, "cloth"))
        # 藍の折り返しと、爪先と踵の銀の三日月の覆い、甲の星の留め
        g += dloft(bb, "cape", [sring(5.6, 2.75, -2.85, 2.8, n=10, p=2.8, cx=cx), sring(7.0, 2.95, -3.05, 3.0, n=10, p=2.8, cx=cx)], mats=["cape"])
        g += bplate(bb, "plate", "bevel", ngon(3.4, 2.2, 8, taper=0.1), c=(cx, 1.3, -3.8), R=rot_x(-45), h=0.6, ins=0.4, bow=0.4)
        g += bplate(bb, "plate", "bevel", ngon(3.0, 1.8, 8, taper=0.1), c=(cx, 1.2, 2.75), R=rot_x(45), h=0.5, ins=0.3, bow=0.3)
        g += dloft(bb, "trim", [sring(3.2, 2.7, -3.15, 2.72, n=10, p=2.8, cx=cx), sring(3.7, 2.68, -3.05, 2.7, n=10, p=2.8, cx=cx)])
        g += lume(bb, (cx, 3.45, -3.3), (0.6, 0.6, 0.3))
        out[bb] = g
    return out


A.BUILD["a"] = (m_helmet, m_chest, m_legs, m_boots)


def main():
    sys.argv = [sys.argv[0], "a"]
    A.main()


if __name__ == "__main__":
    main()
