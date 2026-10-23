"""五章「深淵」の防具「淵潜りの鎧」(4 部位、レア) の作り直しのラフ 3 案 (v2)。前の 3 案 (armor_abyss.py の箱と板) は弱かったので、
巨神シリーズの防具 (armor/sstyle.py: 角の丸い輪・面取りの板・閉じた布) と同じ部品で作り、深淵の言葉 (錨鉄・古い真鍮・紫黒の布・灯りの琥珀) で塗る。
調べたこと: docs/review/abyss_gear_v2/README.md の「鎧」。

  A 潜水兜と錨鉄の潜水服  古い潜水服 (真鍮の丸兜・覗き窓 3 つと格子・首の鋲の輪)、肩を覆う錨鉄の襟板、帆布の服、
                         背の空気筒 2 本と兜へ渡る管、鉛の重りの帯、膝当て、鉛底の潜水靴 (真鍮の爪先・革の留め帯)
  B 灯り番の外套          深い頭巾と口元の錨鉄の息よけ (格子と小さな筒)、額の灯、高い立ち襟の長い外套 (背の裾は二つに割れて膝下まで)、
                         胸を交差する革帯と腰の袋、背から左肩越しに灯を吊る竿、袖の折り返しと籠手、長靴 (折り返しと鉄の爪先)
  C 沈んだ騎士           閉じた兜 (覗き穴の横の一筋が紫に光る、頭頂の藤壺と後ろの海藻の房)、藤壺の付いた胸甲と腹の小札、
                         肩の段の大袖、袈裟に掛けた錨の鎖、裂けた前垂れ、草摺の小札、膝の円盤と翼、段の鉄靴
共通: 縁 (trim) はレアの青い鋼。A・B の光は灯りの琥珀、C は淵の紫。どの部位にも光る画素がある (GeckoLib は空の glowmask で落ちる)。

実行: python -I tools/rough/lowpoly/gear_gaps/armor_abyss_v2.py [a b c]
出力: docs/review/roughs_gear_gaps/raw/armor_abyss_v2/<案>/<部位>_{mesh.json,atlas.png,glowmask.png}・icons/<案>_<部位>_32.png
      docs/review/roughs_gear_gaps/abyss_diver_armor_v2/opt_<案>.png (前・横・後ろ・3/4・歩き・一人称・兜の拡大・部位ごと・アイコン) と compare.png
入れ方: A 案が選ばれた (2026-10-24)。python -I tools/install/install_abyss_gear.py (既定 v2a = raw/armor_abyss_v2/a。強化 +5/+10 の絵 <部位>_plus5/_plus10 も入れる)
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/armor"))
sys.path.insert(0, str(HERE))

import akit  # noqa: E402
from akit import (V, bx, ext, crv, spk, rot_x, rot_y, rot_z, nrm, ARM, LEG, BOOT, mannequin, cull_inside, pose_faces,  # noqa: E402
                  export_mesh, save_img, tri_count, build_atlas, edge_fields, fill_outside, hash1, vnoise, BAYER, tag)
from sk import sring, dloft, bplate, ngon, slab, drape, lume, gem, band, ring_tube, M_SIDE, M_TOP  # noqa: E402
import lpkit  # noqa: E402
from lpkit import ellipsoid  # noqa: E402
from wkit import pick, ramp, LIGHT  # noqa: E402
import scene  # noqa: E402
import armor_abyss as OLD  # noqa: E402  (アイコンの作り方を借りる)

OUT = ROOT / "docs/review/roughs_gear_gaps/abyss_diver_armor_v2"
RAW = ROOT / "docs/review/roughs_gear_gaps/raw/armor_abyss_v2"
PIECES = ["helmet", "chestplate", "leggings", "boots"]
PJ = dict(helmet="兜", chestplate="胸と腕", leggings="脚", boots="靴")
NAMES = {"a": "A 潜水兜と錨鉄の潜水服", "b": "B 灯り番の外套", "c": "C 沈んだ騎士"}
GLOWK = {"a": "lamp", "b": "lamp", "c": "void"}
N = 12


def E(bone, mat, c, r, segs=8, rings=2, rot=None):
    return tag(ellipsoid(bone, mat, c, r, segs=segs, rings=rings, rot=rot), bone)


def disc_xy(bone, mat, bev, c, d, h=0.5, k=12):
    """正面を向く円盤 (覗き窓・鋲の頭)"""
    return bplate(bone, mat, bev, ngon(d, d, k), c=c, h=h, ins=min(0.5, d * 0.12), ch=min(0.2, h * 0.4))


def ring_xy(bone, mat, c, R, r=0.4, n=12):
    return ring_tube(bone, mat, c, R, r, n=n, sides=4, plane="xy")


def lantern_s(bone, c, w=1.6, h=2.0, mat="brass"):
    """小さな角灯 (額・胸・竿の先): 硝子 (光る)・上下の枠・屋根・環"""
    x, y, z = c
    f = bx(bone, "gem", (x, y, z), (w * 0.8, h, w * 0.8))
    f += bx(bone, mat, (x, y - h / 2, z), (w + 0.3, 0.45, w + 0.3))
    f += bx(bone, mat, (x, y + h / 2, z), (w + 0.3, 0.45, w + 0.3))
    for sx in (-1, 1):
        for sz in (-1, 1):
            f += bx(bone, mat, (x + sx * w * 0.45, y, z + sz * w * 0.45), (0.3, h, 0.3))
    f += bx(bone, mat, (x, y + h / 2 + 0.55, z), (w * 0.7, 0.7, w * 0.7), taper=(0.4, 0.4))
    return f


def chain_diag(bone, p0, p1, n, mat="chain", w=0.9, t=0.4):
    """鎖: 交互に 90 度回した平たい輪 (箱) を p0 から p1 へ"""
    p0, p1 = V(*p0), V(*p1)
    d = p1 - p0
    L = float(np.linalg.norm(d)) / n * 1.3
    ang = math.degrees(math.atan2(d[0], d[1]))
    f = []
    for i in range(n):
        c = p0 + d * ((i + 0.5) / n)
        R = rot_z(-ang) if i % 2 == 0 else rot_z(-ang) @ rot_y(90)
        f += bx(bone, mat, tuple(c), (w, L, t), rot=R)
    return f


def rivets_row(bone, pts, n=(0, 0, -1), s=0.45, mat="bevel"):
    f = []
    for p in pts:
        f += bx(bone, mat, p, (s, s, s), taper=(0.6, 0.6), rot=None)
    return f


# ====================================================================== A 潜水兜と錨鉄の潜水服
def a_helmet():
    b = "armorHead"
    f = []
    lv = [(23.6, 6.0, -6.0, 6.0), (25.2, 6.45, -6.45, 6.45), (27.6, 6.7, -6.7, 6.7), (30.2, 6.55, -6.55, 6.55),
          (32.4, 5.8, -5.8, 5.8), (34.0, 4.2, -4.2, 4.2), (35.0, 2.0, -2.0, 2.0)]
    f += dloft(b, "brass", [sring(y, hx, zf, zb, n=16, p=2.4) for y, hx, zf, zb in lv], caps=(False, "brass"))
    # 首の輪 (錨鉄) と鋲 8
    f += dloft(b, "plate", [sring(23.3, 6.5, -6.5, 6.5, n=16, p=2.4), sring(24.4, 6.75, -6.75, 6.75, n=16, p=2.4),
                            sring(24.9, 6.55, -6.55, 6.55, n=16, p=2.4)], mats=["bevel", "plate"])
    for k in range(8):
        a = 2 * math.pi * (k + 0.5) / 8
        f += bx(b, "bevel", (6.8 * math.sin(a), 24.1, -6.8 * math.cos(a)), (0.8, 0.8, 0.8), taper=(0.6, 0.6))
    # 正面の大きな覗き窓: 暗い硝子・真鍮の縁・格子 (十字 + 縦 2)
    f += disc_xy(b, "glassd", "brass", (0, 28.2, -6.55), 5.6, h=0.6, k=12)
    f += ring_xy(b, "brass", (0, 28.2, -6.95), 2.85, 0.5, n=12)
    for k in range(6):   # 覗き窓の縁の蝶ねじ
        a = 2 * math.pi * (k + 0.5) / 6
        f += bx(b, "bevel", (3.25 * math.sin(a), 28.2 + 3.25 * math.cos(a), -7.0), (0.7, 0.7, 0.7), taper=(0.6, 0.6))
    # 兜の継ぎ目の帯 (頭頂を前から後ろへ)
    f += crv(b, "brass", [(0, 31.5, -6.0), (0, 34.2, -3.6), (0, 35.2, 0.0), (0, 34.2, 3.6), (0, 31.5, 6.0)], [0.5] * 5, sides=4, caps=(True, True))
    for x in (-1.3, 1.3):
        f += bx(b, "plate", (x, 28.2, -7.35), (0.4, 5.4, 0.4))
    f += bx(b, "plate", (0, 28.2, -7.4), (5.4, 0.4, 0.4))
    # 横の小窓 (左右) と上の窓
    for s in (-1, 1):
        # 横の覗き窓: 硝子・真鍮の環 (横を向く輪)・格子 2 本・蝶ねじ 4
        f += bplate(b, "glassd", "brass", ngon(3.0, 3.0, 10), c=(s * 6.7, 28.4, 0.4), R=M_SIDE(s), h=0.6, ins=0.3, ch=0.2)
        pts = [(s * 7.0, 28.4 + 1.65 * math.cos(2 * math.pi * k / 10), 0.4 + 1.65 * math.sin(2 * math.pi * k / 10)) for k in range(11)]
        f += crv(b, "brass", pts, [0.38] * 11, sides=4, hint=(1, 0, 0))
        for dz in (-0.55, 0.55):
            f += bx(b, "plate", (s * 7.15, 28.4, 0.4 + dz), (0.35, 2.9, 0.35))
        for k in range(4):
            a = 2 * math.pi * (k + 0.5) / 4
            f += bx(b, "bevel", (s * 7.05, 28.4 + 2.05 * math.cos(a), 0.4 + 2.05 * math.sin(a)), (0.6, 0.6, 0.6), taper=(0.6, 0.6))
    # 右前の排気弁 (真鍮の筒と小さな把手)
    f += crv(b, "brass", [(4.6, 25.9, -4.4), (5.5, 26.0, -4.9), (6.1, 26.2, -5.1)], [0.75, 0.75, 0.7], sides=6, caps=(False, True))
    f += bx(b, "brass", (6.25, 26.25, -5.15), (0.9, 1.8, 0.9), taper=(0.8, 0.8))
    f += bx(b, "plate", (6.25, 27.45, -5.15), (1.6, 0.35, 0.35))
    # 額の灯 (覗き窓の上) と後ろの空気の弁
    f += lantern_s(b, (0, 32.3, -5.6), w=1.5, h=1.5)
    f += bx(b, "brass", (0, 31.4, -5.3), (1.8, 0.6, 1.6))
    f += crv(b, "brass", [(0, 30.0, 6.2), (0, 30.4, 7.6), (0, 29.6, 8.4)], [0.75, 0.7, 0.65], sides=6, caps=(False, True))
    f += bx(b, "plate", (0, 29.4, 8.5), (1.6, 1.2, 1.2))
    return {b: f}


def a_chest():
    B = "armorBody"
    f = []
    f += dloft(B, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0) for y, hx, zf, zb in
                            [(12.2, 4.7, -2.75, 2.7), (15.5, 4.6, -2.7, 2.65), (19.0, 4.7, -2.8, 2.7), (22.6, 4.85, -2.9, 2.8), (24.3, 4.4, -2.7, 2.6)]])
    # 肩を覆う錨鉄の襟板 (前後に鋲の列)
    f += dloft(B, "plate", [sring(20.6, 4.9, -3.0, 2.9, n=N, p=2.6), sring(22.6, 5.2, -3.35, 3.25, n=N, p=2.6),
                            sring(24.2, 4.9, -3.2, 3.1, n=N, p=2.6), sring(24.9, 3.8, -2.8, 2.7, n=N, p=2.6)], mats=["bevel", "plate", "trim"])
    f += bplate(B, "plate", "bevel", [(-3.6, 1.6), (3.6, 1.6), (3.2, -1.4), (0, -2.4), (-3.2, -1.4)], c=(0, 21.6, -3.35), h=0.8, ins=0.6, bow=0.8)
    f += rivets_row(B, [(x, 22.6, -3.85) for x in (-3.0, -1.5, 0, 1.5, 3.0)])
    f += rivets_row(B, [(x, 22.6, 3.65) for x in (-3.0, -1.5, 0, 1.5, 3.0)])
    f += lantern_s(B, (2.4, 18.6, -3.6), w=1.2, h=1.4)
    f += bx(B, "sash", (2.4, 20.2, -3.0), (0.6, 1.6, 0.6))
    # 鉛の重りの帯 (前に 3 つ・後ろに 2 つ)、真鍮の留め具
    f += dloft(B, "sash", [sring(12.3, 4.95, -3.0, 2.95, n=N, p=3.0), sring(14.2, 4.9, -3.0, 2.9, n=N, p=3.0)], mats=["sash"])
    for x in (-3.0, 3.0):
        f += bx(B, "lead", (x, 12.8, -3.4), (1.8, 2.6, 1.0), taper=(0.9, 1.0))
    for x in (-1.8, 1.8):
        f += bx(B, "lead", (x, 12.8, 3.3), (2.0, 2.6, 1.0), taper=(0.9, 1.0))
    f += bplate(B, "brass", "bevel", ngon(1.9, 1.7, 8), c=(0, 13.3, -3.25), h=0.6, ins=0.3)
    # 背の空気筒 2 本と兜へ渡る管
    for s in (-1, 1):
        f += dloft(B, "plate", [sring(y, 1.25, 2.6, 5.1, n=10, p=2.0, cx=s * 1.55) for y in (14.6, 15.0, 21.6, 22.2)],
                   caps=("plate", False), mats=["bevel", "plate", "bevel"])
        f += dloft(B, "brass", [sring(22.1, 1.05, 2.8, 4.9, n=10, p=2.0, cx=s * 1.55), sring(23.0, 0.6, 3.3, 4.4, n=10, p=2.0, cx=s * 1.55)],
                   caps=(False, "brass"))
        f += band(B, "trim", (s * 1.55, 18.2, 3.85), (2.7, 0.7, 2.7))
    f += crv(B, "hose", [(0, 23.4, 3.9), (0.4, 24.6, 4.8), (0.2, 25.6, 5.6)], [0.55, 0.55, 0.5], sides=6, caps=(True, True))
    f += crv(B, "hose", [(-1.55, 23.0, 3.85), (-0.8, 23.6, 3.9), (0, 23.4, 3.9)], [0.5, 0.5, 0.55], sides=6, caps=(True, True))
    f += crv(B, "hose", [(1.55, 23.0, 3.85), (0.8, 23.6, 3.9), (0, 23.4, 3.9)], [0.5, 0.5, 0.55], sides=6, caps=(True, True))
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        cx = s * 6.0
        g = dloft(ab, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                                [(24.0, 2.75, -2.8, 2.8), (18.0, 2.5, -2.65, 2.65), (14.6, 2.55, -2.65, 2.65), (12.0, 2.6, -2.7, 2.7)]])
        # 肩の丸い錨鉄 (襟板の続き)
        # 肩の丸い錨鉄 (襟板の続き) と肩の縁は胴の骨に付ける (腕と一緒に回らない。腕を上げても兜の横を通らない)
        out[B] += dloft(B, "plate", [sring(y, hx, -hz, hz, n=N, p=2.4, cx=s * 6.0 + s * dx) for y, hx, hz, dx in
                                     [(21.4, 3.3, 3.2, 0.2), (23.2, 3.4, 3.3, 0.1), (24.6, 2.8, 2.8, -0.2), (25.2, 1.6, 1.6, -0.5)]],
                        caps=(False, "plate"), mats=["bevel", "plate", "plate"])
        out[B] += dloft(B, "trim", [sring(21.3, 3.42, -3.32, 3.32, n=N, p=2.4, cx=s * 6.0 + s * 0.2), sring(21.9, 3.42, -3.32, 3.32, n=N, p=2.4, cx=s * 6.0 + s * 0.2)])
        # 肘当てと革の袖口 (真鍮の輪)
        g += E(ab, "plate", (cx + s * 0.4, 17.6, 1.2), (2.1, 1.5, 2.0), segs=8, rings=2)
        g += dloft(ab, "sash", [sring(12.4, 2.85, -2.95, 2.95, n=N, p=3.0, cx=cx), sring(14.4, 2.8, -2.9, 2.9, n=N, p=3.0, cx=cx)])
        g += dloft(ab, "brass", [sring(14.3, 2.95, -3.0, 3.0, n=N, p=3.0, cx=cx), sring(14.8, 2.9, -2.95, 2.95, n=N, p=3.0, cx=cx)])
        if s == 1:
            g += lume(ab, (cx + 2.9, 13.4, 0), (0.35, 1.6, 0.6))
        out[ab] = g
    return out


def a_legs():
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.05
        g = dloft(lb, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                                [(12.0, 2.6, -2.65, 2.6), (8.0, 2.45, -2.6, 2.55), (4.4, 2.4, -2.55, 2.5)]])
        # 腰の継ぎ目の厚い帆布の折り返し (単体で穿いても服の継ぎ目に見える)
        g += dloft(lb, "cloth", [sring(10.6, 2.62, -2.72, 2.66, n=N, p=3.0, cx=cx), sring(11.2, 2.74, -2.84, 2.78, n=N, p=3.0, cx=cx),
                                 sring(12.2, 2.74, -2.84, 2.78, n=N, p=3.0, cx=cx)], caps=(False, "cloth"), mats=["under", "cloth"])
        # 太ももの前の当て布 (革) と縫い目の鋲
        g += bplate(lb, "sash", "sash", [(-1.5, 1.0), (1.5, 1.0), (1.7, -0.6), (0, -1.1), (-1.7, -0.6)], c=(cx, 9.6, -2.62), h=0.45, ins=0.4, ch=0.15, bow=0.4)
        for dx in (-1.2, 1.2):
            g += bx(lb, "brass", (cx + dx, 10.3, -3.0), (0.4, 0.4, 0.35), taper=(0.6, 0.6))
        # 外側の縫い目 (レアの青い鋼の玉縁)
        g += bx(lb, "trim", (cx + s * 2.5, 8.2, 0.0), (0.45, 6.6, 0.7))
        # 膝の当て (錨鉄の円盤) と、ふくらはぎの後ろの締め紐 (実物の潜水服も脚の後ろを紐で締めて空気の溜まりを防ぐ)
        g += E(lb, "plate", (cx, 6.4, -2.4), (2.2, 1.9, 1.2), segs=8, rings=2)
        g += bx(lb, "bevel", (cx, 6.4, -3.6), (0.9, 0.9, 0.5), taper=(0.6, 0.6))
        g += bx(lb, "sash", (cx, 8.0, 2.55), (1.5, 4.4, 0.35))
        for k, y in enumerate((6.1, 7.1, 9.0, 9.9)):
            for dx in (-0.75, 0.75):
                g += bx(lb, "brass", (cx + dx, y, 2.8), (0.38, 0.38, 0.3), taper=(0.7, 0.7))
            g += bx(lb, "hose", (cx, y + 0.5, 2.82), (1.5, 0.28, 0.25), rot=rot_z(28 if k % 2 else -28))
        for y in (5.0, 7.8):
            g += dloft(lb, "sash", [sring(y, 2.62, -2.75, 2.7, n=N, p=3.0, cx=cx), sring(y + 0.7, 2.62, -2.75, 2.7, n=N, p=3.0, cx=cx)])
        g += bx(lb, "brass", (cx + s * 2.6, 8.15, 0), (0.4, 0.9, 0.9))
        if s == 1:      # 太ももの小さな鉛の重りと光る留め
            g += bx(lb, "lead", (cx + 2.65, 10.4, 0), (0.9, 2.2, 1.8), taper=(1.0, 0.9))
        g += lume(lb, (cx, 8.15, -2.95), (0.8, 0.5, 0.3))
        out[lb] = g
    return out


def a_boots():
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.05
        g = dloft(bb, "plate", [sring(y, hx, zf, zb, n=10, p=2.6, cx=cx) for y, hx, zf, zb in
                                [(5.2, 2.55, -2.65, 2.6), (3.6, 2.6, -3.0, 2.7), (1.8, 2.72, -3.9, 2.8), (1.1, 2.84, -4.4, 2.95)]], caps=(False, False))
        g += dloft(bb, "lead", [sring(1.15, 2.88, -4.5, 3.0, n=10, p=2.6, cx=cx), sring(0.0, 2.92, -4.6, 3.05, n=10, p=2.6, cx=cx)], caps=(False, "lead"))
        g += bplate(bb, "brass", "bevel", ngon(3.6, 2.6, 8, taper=0.1), c=(cx, 1.9, -3.95), R=rot_x(-40), h=0.7, ins=0.4, bow=0.5)
        for y in (3.0, 4.4):
            g += dloft(bb, "sash", [sring(y, 2.78, -2.95, 2.8, n=10, p=2.6, cx=cx), sring(y + 0.55, 2.74, -2.9, 2.76, n=10, p=2.6, cx=cx)])
            g += bx(bb, "brass", (cx + s * 2.85, y + 0.27, -0.6), (0.4, 0.8, 0.9))
        g += lume(bb, (cx, 3.3, -3.05), (0.7, 0.5, 0.3))
        # 鉛底の縁の鋲 (前 3・横 2) と踵の真鍮の当て
        for dx in (-1.7, 0.0, 1.7):
            g += bx(bb, "bevel", (cx + dx, 0.58, -4.62 + abs(dx) * 0.08), (0.5, 0.5, 0.4), taper=(0.6, 0.6))
        for dz in (-1.8, 0.8):
            g += bx(bb, "bevel", (cx + s * 2.97, 0.58, dz), (0.4, 0.5, 0.5), taper=(0.6, 0.6))
        g += bplate(bb, "brass", "bevel", ngon(3.4, 1.8, 8), c=(cx, 2.1, 2.72), R=rot_y(180), h=0.5, ins=0.3, ch=0.15, bow=0.5)
        out[bb] = g
    return out


# ====================================================================== B 灯り番の外套
def b_helmet():
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
    # 頭巾の縁の折り返し (額から頬へ)
    f += bx(b, "cape", (0, 30.9, -5.95), (8.6, 1.0, 0.8), taper=(0.96, 1.0))
    for s in (-1, 1):
        f += bx(b, "cape", (s * 4.6, 27.4, -5.75), (0.9, 7.0, 0.8))
    # 後ろの垂れ
    f += drape(b, ["cloth", "cloth", "cape"], 0.0, 26.4, 21.4, 5.6, 4.2, 5.0, rows=4, cols=6, fold=0.4, flare_z=1.1, t=0.5)
    # 額の灯 (革の帯に留める)
    f += bx(b, "sash", (0, 31.9, -5.85), (7.6, 0.8, 0.6))
    f += lantern_s(b, (0, 32.6, -6.35), w=1.2, h=1.2)
    # 口元の息よけ: 錨鉄の板 (格子の溝は暗い板) と両側の小さな筒
    f += bplate(b, "plate", "bevel", [(-3.6, 1.4), (3.6, 1.4), (3.8, -0.6), (1.6, -2.2), (-1.6, -2.2), (-3.8, -0.6)],
                c=(0, 25.9, -4.95), h=0.8, ins=0.5, bow=1.1)
    for y in (25.5, 26.3):
        f += bx(b, "dark", (0, y, -5.4), (3.2, 0.32, 0.3))
    for s in (-1, 1):
        f += crv(b, "plate", [(s * 3.0, 25.4, -5.3), (s * 3.9, 25.0, -5.6), (s * 4.5, 24.6, -5.2)], [0.62, 0.62, 0.55], sides=6, caps=(True, True))
        f += bx(b, "brass", (s * 4.6, 24.55, -5.15), (0.9, 0.6, 0.9))
    return {b: f}


def b_chest():
    B = "armorBody"
    f = []
    f += dloft(B, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0) for y, hx, zf, zb in
                            [(12.0, 4.75, -2.85, 2.8), (15.5, 4.6, -2.75, 2.7), (19.0, 4.7, -2.85, 2.75), (22.6, 4.85, -2.95, 2.85), (24.3, 4.4, -2.75, 2.65)]])
    # 外套の前立て (縁) と打ち合わせの留め 3 つ
    f += bx(B, "trim", (0.8, 18.0, -3.05), (1.0, 11.4, 0.5))
    for y in (15.0, 18.0, 21.0):
        f += bx(B, "brass", (0.8, y, -3.45), (0.8, 0.8, 0.5), taper=(0.6, 0.6))
    # 高い立ち襟 (前が開く)
    for fc in dloft(B, "cape", [sring(24.0, 4.5, -3.0, 2.9, n=N, p=2.8), sring(25.6, 4.9, -3.4, 3.3, n=N, p=2.8),
                                sring(27.2, 5.3, -3.7, 3.7, n=N, p=2.8)], mats=["cape", "cape"]):
        c = fc.verts.mean(0)
        if fc.normal[2] < -0.5 and abs(c[0]) < 2.0:
            continue
        f.append(fc)
    f += dloft(B, "trim", [sring(27.1, 5.36, -3.76, 3.76, n=N, p=2.8), sring(27.6, 5.3, -3.7, 3.7, n=N, p=2.8)])
    # 胸を交差する革帯と腰の袋、帯
    for s in (-1, 1):
        f += bx(B, "sash", (0, 18.6, -3.25), (1.0, 13.0, 0.4), rot=rot_z(s * 28))
        f += bx(B, "sash", (0, 18.6, 3.15), (1.0, 13.0, 0.4), rot=rot_z(s * 28))
    f += bplate(B, "brass", "bevel", ngon(1.6, 1.6, 8), c=(0, 18.6, -3.55), h=0.5, ins=0.3)
    f += dloft(B, "sash", [sring(12.4, 4.95, -3.05, 3.0, n=N, p=3.0), sring(13.8, 4.9, -3.0, 2.95, n=N, p=3.0)])
    for x in (-3.2, -1.4):
        f += bx(B, "sash", (x, 12.0, -3.5), (1.6, 2.0, 1.2), taper=(0.9, 0.9))
        f += bx(B, "brass", (x, 12.7, -4.15), (0.5, 0.5, 0.3))
    # 背の裾: 腰から膝下まで、真ん中で二つに割れる
    for s in (-1, 1):
        f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 13.6, 3.0, 2.95, 2.3, 2.9, rows=5, cols=4, fold=0.5,
                   flare_z=1.6, t=0.5, hem=[0.0, -0.6, -0.2, -0.9][::s])
    # 前の裾 (腰から太ももの中ほど)
    for s in (-1, 1):
        f += drape(B, ["cloth", "cloth", "cape"], s * 2.5, 12.6, 6.4, -2.95, 2.1, 2.5, rows=4, cols=4, fold=0.3,
                   flare_z=0.7, t=0.5, out=-1.0, ov=(0, 0, -1))
    # 灯の竿: 背の左の腰から左肩の後ろを通って前へ曲がり、肩の上に灯を吊る
    f += crv(B, "plate", [(2.8, 13.0, 3.5), (3.2, 20.0, 3.9), (3.6, 26.6, 3.7), (4.4, 29.2, 1.4), (5.0, 29.4, -1.6)],
             [0.42, 0.42, 0.42, 0.4, 0.36], sides=6, caps=(True, True))
    f += bx(B, "brass", (2.9, 15.0, 3.6), (1.2, 1.0, 1.2))
    f += chain_diag(B, (5.0, 29.0, -1.6), (5.0, 27.2, -1.6), 2, w=0.6, t=0.25)
    f += lantern_s(B, (5.0, 26.0, -1.6), w=1.5, h=1.8)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        cx = s * 6.0
        g = dloft(ab, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                                [(24.0, 2.8, -2.85, 2.85), (18.0, 2.55, -2.7, 2.7), (14.8, 2.6, -2.7, 2.7)]])
        # 袖の折り返し (外套の裏地) と籠手
        g += dloft(ab, "cape", [sring(15.2, 2.95, -3.0, 3.0, n=N, p=3.0, cx=cx), sring(13.6, 3.2, -3.25, 3.25, n=N, p=3.0, cx=cx)], mats=["cape"])
        g += dloft(ab, "sash", [sring(13.7, 2.75, -2.8, 2.8, n=N, p=3.0, cx=cx), sring(11.9, 2.7, -2.75, 2.75, n=N, p=3.0, cx=cx)])
        g += bplate(ab, "plate", "bevel", ngon(2.2, 2.6, 8, taper=0.2), c=(cx + s * 2.95, 12.9, 0), R=M_SIDE(s), h=0.6, ins=0.4)
        # 肩: 左だけ錨鉄の小さな肩 (竿を支える)、右は外套の肩の重ね
        if s == 1:
            g += dloft(ab, "plate", [sring(y, hx, -hz, hz, n=N, p=2.4, cx=cx + s * dx) for y, hx, hz, dx in
                                     [(21.8, 3.2, 3.1, 0.2), (23.6, 3.25, 3.15, 0.1), (24.8, 2.4, 2.4, -0.2), (25.2, 1.2, 1.2, -0.5)]],
                       caps=(False, "plate"), mats=["bevel", "plate", "plate"])
            g += lume(ab, (cx + 3.15, 13.0, 0), (0.3, 1.4, 0.5))
        else:
            g += drape(ab, ["cape", "cape"], cx, 24.6, 20.0, 0.0, 3.0, 3.6, rows=3, cols=5, fold=0.2, flare_z=0.6, t=0.45,
                       out=1.0, ov=(-1, 0, 0)) if False else []
            g += dloft(ab, "cape", [sring(y, hx, -hz, hz, n=N, p=2.6, cx=cx + s * dx) for y, hx, hz, dx in
                                    [(20.4, 3.15, 3.05, 0.25), (23.4, 3.1, 3.05, 0.1), (24.6, 2.4, 2.4, -0.2), (25.0, 1.2, 1.2, -0.4)]],
                       caps=(False, "cape"))
        out[ab] = g
    return out


def b_legs():
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.05
        g = dloft(lb, "cloth", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in
                                [(12.0, 2.55, -2.6, 2.55), (8.0, 2.4, -2.55, 2.5), (4.6, 2.35, -2.5, 2.45)]])
        for y in (6.2, 9.4):    # 膝下と腿の巻き革
            g += dloft(lb, "sash", [sring(y, 2.56, -2.68, 2.62, n=N, p=3.0, cx=cx), sring(y + 0.8, 2.54, -2.66, 2.6, n=N, p=3.0, cx=cx)])
        g += bx(lb, "brass", (cx + s * 2.55, 6.6, -0.8), (0.4, 0.7, 0.8))
        g += bx(lb, "lume", (cx, 9.8, -2.85), (0.7, 0.45, 0.3))
        if s == 1:
            g += bx(lb, "sash", (cx + 2.7, 10.0, 0.4), (1.0, 2.6, 2.2), taper=(1.0, 0.9))
        out[lb] = g
    return out


def b_boots():
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.05
        g = dloft(bb, "sash", [sring(y, hx, zf, zb, n=10, p=2.8, cx=cx) for y, hx, zf, zb in
                               [(6.6, 2.55, -2.65, 2.6), (3.6, 2.55, -2.9, 2.6), (1.8, 2.6, -3.7, 2.65), (0.3, 2.55, -4.2, 2.6)]], caps=(False, "sash"))
        # 折り返し (長靴の口) と鉄の爪先、甲の留め
        g += dloft(bb, "cape", [sring(5.6, 2.75, -2.85, 2.8, n=10, p=2.8, cx=cx), sring(7.0, 2.95, -3.05, 3.0, n=10, p=2.8, cx=cx)], mats=["cape"])
        g += bplate(bb, "plate", "bevel", ngon(3.4, 2.2, 8, taper=0.1), c=(cx, 1.3, -3.8), R=rot_x(-45), h=0.6, ins=0.4, bow=0.4)
        g += dloft(bb, "trim", [sring(3.2, 2.7, -3.15, 2.72, n=10, p=2.8, cx=cx), sring(3.7, 2.68, -3.05, 2.7, n=10, p=2.8, cx=cx)])
        g += bx(bb, "lume", (cx, 3.45, -3.3), (0.7, 0.45, 0.3))
        out[bb] = g
    return out


# ====================================================================== C 沈んだ騎士
def barn(bone, c, n=3, r=0.45, spread=0.8, d=(0, 0, -1)):
    f = []
    d = nrm(V(*d))
    side = nrm(np.cross(d, V(0, 1, 0))) if abs(d[1]) < 0.9 else V(1, 0, 0)
    up = nrm(np.cross(side, d))
    for i in range(n):
        a = 2.4 * i + 0.6
        p = V(*c) + side * math.cos(a) * spread + up * math.sin(a * 1.3) * spread
        f += spk(bone, "barn", tuple(p), tuple(d), 0.7 + 0.15 * (i % 2), r * (1 - 0.15 * i), sides=5, rings=1)
    return f


def c_helmet():
    b = "armorHead"
    f = []
    lv = [(23.6, 5.4, -5.6, 5.5), (25.6, 5.6, -5.9, 5.7), (28.4, 5.7, -6.0, 5.8), (31.2, 5.55, -5.8, 5.65),
          (33.2, 4.9, -5.0, 5.0), (34.4, 3.2, -3.3, 3.3), (34.9, 1.2, -1.3, 1.3)]
    f += dloft(b, "plate", [sring(y, hx, zf, zb, n=16, p=3.2) for y, hx, zf, zb in lv], caps=(False, "plate"))
    # 正面の鼻筋 (縦の稜) と覗き穴の段 (暗い溝 + 紫の一筋)
    f += bplate(b, "plate", "bevel", [(-0.7, 4.2), (0.7, 4.2), (0.9, -4.0), (-0.9, -4.0)], c=(0, 28.4, -6.05), h=0.7, ins=0.3, bow=0.0)
    f += bx(b, "dark", (0, 29.2, -5.95), (9.2, 0.9, 0.5))
    f += bx(b, "lume", (0, 29.2, -6.15), (7.4, 0.35, 0.3))
    for s in (-1, 1):       # 息の穴 (暗い点 3)
        for k in range(3):
            f += bx(b, "dark", (s * (2.0 + 0.8 * k), 26.0 - 0.3 * k, -5.95), (0.45, 0.45, 0.4))
    # 縁の帯 (額・首)
    f += dloft(b, "trim", [sring(23.5, 5.55, -5.75, 5.65, n=16, p=3.2), sring(24.3, 5.62, -5.85, 5.72, n=16, p=3.2)])
    f += dloft(b, "trim", [sring(31.0, 5.63, -5.88, 5.73, n=16, p=3.2), sring(31.7, 5.55, -5.78, 5.63, n=16, p=3.2)])
    # 頭頂の藤壺と、後ろの海藻の房
    f += barn(b, (1.8, 34.0, -1.0), n=3, d=(0.3, 1, -0.2))
    f += barn(b, (-2.4, 33.4, 1.6), n=2, d=(-0.4, 1, 0.3))
    f += barn(b, (4.6, 30.4, -3.4), n=2, d=(1, 0.3, -0.5))
    for k, x in enumerate((-1.2, 0.0, 1.2)):
        f += drape(b, ["kelp", "kelp"], x, 32.6, 26.0 - k * 1.2, 5.6, 0.45, 0.7, rows=3, cols=3, fold=0.2, flare_z=0.9, t=0.35)
    return {b: f}


def c_pauldron(ab, s, cx):
    g = []
    g += dloft(ab, "plate", [sring(y, hx, -hz, hz, n=N, p=2.4, cx=cx + s * dx) for y, hx, hz, dx in
                             [(22.2, 3.5, 3.35, 0.3), (23.8, 3.55, 3.4, 0.15), (25.0, 2.8, 2.8, -0.2), (25.5, 1.4, 1.4, -0.5)]],
               caps=(False, "plate"), mats=["bevel", "plate", "plate"])
    for i in range(3):      # 段の大袖
        top = 22.3 - 1.25 * i
        g += dloft(ab, "plate", [sring(top, 3.7 + 0.25 * i, -3.5 - 0.2 * i, 3.5 + 0.2 * i, n=N, p=2.6, cx=cx + s * (0.4 + 0.35 * i)),
                                 sring(top - 1.4, 3.95 + 0.3 * i, -3.7 - 0.25 * i, 3.7 + 0.25 * i, n=N, p=2.6, cx=cx + s * (0.7 + 0.35 * i))],
                   mats=["plate"])
        g += dloft(ab, "trim" if i == 2 else "bevel", [sring(top - 1.4, 3.95 + 0.3 * i, -3.7 - 0.25 * i, 3.7 + 0.25 * i, n=N, p=2.6, cx=cx + s * (0.7 + 0.35 * i)),
                                                      sring(top - 1.75, 3.85 + 0.3 * i, -3.6 - 0.25 * i, 3.6 + 0.25 * i, n=N, p=2.6, cx=cx + s * (0.8 + 0.35 * i))])
    g += barn(ab, (cx + s * 2.6, 24.6, -1.0), n=3, d=(s * 0.6, 1, -0.3))
    return g


def c_chest():
    B = "armorBody"
    f = []
    f += dloft(B, "chainm", [sring(y, hx, zf, zb, n=N, p=3.0) for y, hx, zf, zb in
                             [(12.0, 4.7, -2.75, 2.7), (15.0, 4.6, -2.7, 2.65), (18.0, 4.65, -2.75, 2.7), (24.3, 4.4, -2.7, 2.6)]])
    # 胸甲 (前後) と喉当て
    f += dloft(B, "plate", [sring(17.6, 4.85, -3.0, 2.95, n=N, p=2.7), sring(18.4, 5.05, -3.35, 3.1, n=N, p=2.7),
                            sring(21.2, 5.15, -3.75, 3.2, n=N, p=2.7), sring(23.6, 4.95, -3.45, 3.1, n=N, p=2.7),
                            sring(24.6, 4.2, -3.0, 2.8, n=N, p=2.7)], mats=["bevel", "plate", "plate", "trim"])
    f += dloft(B, "plate", [sring(24.4, 3.9, -2.9, 2.8, n=N, p=2.7), sring(25.6, 3.7, -2.9, 2.75, n=N, p=2.7), sring(26.0, 3.3, -2.6, 2.5, n=N, p=2.7)],
               mats=["plate", "bevel"])
    f += bplate(B, "plate", "bevel", [(-0.6, 2.4), (0.6, 2.4), (0.8, -2.4), (-0.8, -2.4)], c=(0, 21.0, -3.85), h=0.6, ins=0.25)
    f += gem(B, (0, 20.2, -4.2), (1.2, 1.4, 0.5))
    # 腹の小札 3 段
    for i, y in enumerate((16.6, 15.2, 13.8)):
        f += dloft(B, "plate", [sring(y + 0.6, 4.85 - 0.05 * i, -3.05, 2.95, n=N, p=2.8), sring(y - 0.6, 4.95 - 0.05 * i, -3.2, 3.05, n=N, p=2.8)],
                   mats=["plate"])
        f += dloft(B, "bevel", [sring(y - 0.6, 4.95 - 0.05 * i, -3.2, 3.05, n=N, p=2.8), sring(y - 0.85, 4.85 - 0.05 * i, -3.1, 2.95, n=N, p=2.8)])
    f += dloft(B, "trim", [sring(12.4, 5.0, -3.15, 3.05, n=N, p=3.0), sring(13.2, 5.0, -3.15, 3.05, n=N, p=3.0)])
    # 袈裟の錨の鎖 (右肩から左腰)
    f += chain_diag(B, (-3.8, 24.6, -3.9), (3.6, 13.0, -3.6), 8, w=1.0, t=0.45)
    f += chain_diag(B, (-3.8, 24.6, 3.5), (3.6, 13.0, 3.4), 7, w=1.0, t=0.45)
    # 藤壺 (胸と背)
    f += barn(B, (3.0, 22.6, -3.8), n=3)
    f += barn(B, (-2.6, 17.4, 3.4), n=3, d=(0, 0, 1))
    # 裂けた前垂れ (腰から)
    f += drape(B, ["kelp", "kelp", "kelp"], 0.0, 12.6, 4.4, -3.25, 1.6, 2.0, rows=4, cols=4, fold=0.25, flare_z=0.6, t=0.4,
               out=-1.0, ov=(0, 0, -1), hem=[0.0, -1.4, -0.3, -1.0])
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        cx = s * 6.0
        g = dloft(ab, "chainm", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in [(24.0, 2.7, -2.75, 2.75), (12.2, 2.5, -2.6, 2.6)]])
        g += c_pauldron(ab, s, cx)
        g += dloft(ab, "plate", [sring(y, hx, -hz, hz, n=N, p=2.6, cx=cx) for y, hx, hz in [(17.0, 2.75, 2.8), (13.6, 2.95, 3.0), (12.0, 3.15, 3.2)]],
                   mats=["plate", "bevel"])
        g += E(ab, "plate", (cx + s * 0.5, 17.6, 1.6), (2.2, 1.6, 1.8), segs=8, rings=2)
        g += dloft(ab, "trim", [sring(12.1, 3.22, -3.27, 3.27, n=N, p=2.6, cx=cx), sring(12.7, 3.15, -3.2, 3.2, n=N, p=2.6, cx=cx)])
        if s == 1:
            g += lume(ab, (cx + 3.1, 15.0, 0), (0.3, 2.2, 0.5))
        out[ab] = g
    return out


def c_legs():
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.05
        g = dloft(lb, "chainm", [sring(y, hx, zf, zb, n=N, p=3.0, cx=cx) for y, hx, zf, zb in [(12.0, 2.55, -2.6, 2.55), (4.4, 2.35, -2.5, 2.45)]])
        g += dloft(lb, "plate", [sring(y, hx, zf, zb, n=N, p=2.7, cx=cx) for y, hx, zf, zb in [(11.6, 2.75, -2.9, 2.75), (7.4, 2.6, -2.85, 2.65)]],
                   mats=["plate"])
        # 草摺 (腰から垂れる 2 枚の小札)
        for i in range(2):
            y = 12.4 - 1.3 * i
            g += bplate(lb, "plate", "bevel", [(-2.2, 0.7), (2.2, 0.7), (2.4, -0.7), (-2.4, -0.7)], c=(cx, y, -3.2 - 0.15 * i), h=0.6, ins=0.25, bow=0.8)
        # 膝の円盤と小さな翼
        g += E(lb, "plate", (cx, 6.6, -2.5), (2.1, 1.8, 1.2), segs=8, rings=2)
        g += bplate(lb, "plate", "bevel", [(0, 1.4), (1.6, 0.2), (1.2, -1.4), (-0.2, -0.6)], c=(cx + s * 2.3, 6.8, -1.6), R=M_SIDE(s), h=0.5, ins=0.25)
        g += gem(lb, (cx, 6.6, -3.75), (0.9, 0.9, 0.45))
        g += dloft(lb, "trim", [sring(7.3, 2.66, -2.92, 2.7, n=N, p=2.7, cx=cx), sring(7.8, 2.64, -2.9, 2.68, n=N, p=2.7, cx=cx)])
        if s == -1:
            g += barn(lb, (cx - 1.8, 9.6, -2.4), n=2, d=(-0.6, 0.2, -1))
        out[lb] = g
    return out


def c_boots():
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.05
        g = dloft(bb, "plate", [sring(y, hx, zf, zb, n=10, p=2.7, cx=cx) for y, hx, zf, zb in
                                [(5.4, 2.6, -2.75, 2.65), (3.4, 2.6, -2.85, 2.65), (1.6, 2.65, -3.0, 2.7), (0.0, 2.6, -3.0, 2.65)]], caps=(False, "plate"))
        # 段の甲 (爪先へ 3 枚)
        for i in range(3):
            g += bplate(bb, "plate", "bevel", [(-2.2, 0.6), (2.2, 0.6), (2.3, -0.6), (-2.3, -0.6)], c=(cx, 1.9 - 0.55 * i, -3.1 - 0.7 * i),
                        R=rot_x(-35 - 8 * i), h=0.55, ins=0.2, bow=0.6)
        g += dloft(bb, "trim", [sring(5.0, 2.72, -2.88, 2.76, n=10, p=2.7, cx=cx), sring(5.6, 2.68, -2.84, 2.72, n=10, p=2.7, cx=cx)])
        g += gem(bb, (cx, 3.6, -3.1), (0.8, 0.8, 0.4))
        g += bx(bb, "bevel", (cx + s * 2.6, 2.6, 0.2), (0.6, 1.4, 1.4), taper=(0.6, 0.6))
        out[bb] = g
    return out


BUILD = {"a": (a_helmet, a_chest, a_legs, a_boots), "b": (b_helmet, b_chest, b_legs, b_boots), "c": (c_helmet, c_chest, c_legs, c_boots)}


def build(o):
    fns = BUILD[o]
    out = {}
    for piece, fn in zip(PIECES, fns):
        d = fn()
        out[piece] = [f for fs in d.values() for f in fs if f is not None]
    return out


# ====================================================================== 絵
A_ = np.array
PALS = dict(
    plate=A_([(22, 24, 34), (36, 40, 54), (54, 60, 78), (76, 84, 104), (102, 112, 132), (134, 144, 162), (170, 178, 194)]),
    brass=A_([(40, 30, 20), (66, 50, 30), (96, 74, 42), (128, 100, 56), (160, 128, 72), (192, 160, 96), (220, 194, 132)]),
    verd=A_([(30, 66, 62), (46, 96, 84), (70, 128, 106), (100, 158, 130)]),
    rust=A_([(54, 30, 24), (84, 46, 30), (118, 66, 38), (150, 92, 52)]),
    canvas=A_([(34, 30, 40), (50, 44, 56), (68, 60, 74), (88, 80, 94), (110, 100, 114), (132, 122, 134)]),
    vcloth=A_([(16, 12, 26), (26, 20, 42), (38, 30, 60), (52, 42, 80), (70, 58, 104), (92, 78, 128)]),
    vcape=A_([(30, 14, 30), (46, 22, 46), (66, 34, 64), (88, 48, 84), (112, 66, 106)]),
    leather=A_([(26, 16, 14), (42, 26, 22), (60, 38, 30), (82, 54, 40), (106, 72, 52)]),
    lead=A_([(30, 30, 36), (44, 44, 52), (60, 60, 70), (80, 80, 90), (102, 102, 112)]),
    hose=A_([(14, 12, 16), (24, 22, 28), (38, 34, 42), (54, 50, 58)]),
    glassd=A_([(10, 14, 22), (16, 24, 36), (24, 36, 52), (40, 58, 78), (90, 120, 140)]),
    barn=A_([(70, 78, 84), (104, 112, 114), (140, 148, 146), (178, 184, 178), (212, 216, 206)]),
    kelp=A_([(16, 30, 26), (24, 44, 36), (34, 60, 46), (48, 78, 58), (64, 98, 72)]),
    chain=A_([(26, 28, 38), (42, 46, 60), (62, 68, 86), (88, 96, 116), (120, 128, 148)]),
    trim=A_([(30, 46, 72), (48, 74, 112), (72, 108, 156), (106, 148, 200), (152, 194, 238), (210, 230, 252)]),
    dark=A_([(5, 3, 10), (12, 8, 20), (22, 16, 34)]),
    G_lamp=A_([(196, 100, 26), (236, 146, 44), (255, 192, 92), (255, 226, 160), (255, 246, 222)]),
    G_void=A_([(112, 56, 210), (150, 96, 246), (196, 150, 255), (228, 204, 255), (246, 236, 255)]),
)
CLOTH_OF = {"a": "canvas", "b": "vcloth", "c": "vcloth"}


def paint(faces, size, P, N_, M, L, o, lv=0):
    """lv: 強化の段 (0 = +0〜4、1 = +5〜9、2 = +10)。メッシュは同じで、縁の光の破線がつながり (+5)、縁の角まで光る (+10)"""
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N_ @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    X, Y, Z = L[..., 0], L[..., 1], L[..., 2]
    n_lo = vnoise(L * 0.09 + 3, 1)
    n_mid = vnoise(L * 0.3 + 11, 2)
    n_hi = vnoise(L * 0.9 + 5, 7)
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    up = N_[..., 1]
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(p, base, lw=0.30, nw=0.06, extra=0.0, ed=True):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.04 * (n_mid - 0.5) + 0.045 * bay + extra
        idx = np.floor(v * len(p)).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    m = lambda n: mats == n
    gp = PALS["G_" + GLOWK[o]]
    thread = (LU % 3 == 0) & (n_mid > 0.55)
    fold = np.sin(X * 1.7 + Z * 1.3 + 2.2 * n_lo) > 0.55
    cp = PALS[CLOTH_OF[o]]
    put(m("cloth"), pick(cp, shaded(cp, 0.50, 0.36) - thread - fold * (n_mid > 0.45)))
    put(m("cape"), pick(PALS["vcape"], shaded(PALS["vcape"], 0.5, 0.36) - thread))
    put(m("under"), pick(cp, shaded(cp, 0.22, 0.2, ed=False)) * 0.6)
    seam = (np.abs(((X * 0.4 + Y * 0.9 + Z * 0.4) % 1.8) - 0.9) < 0.12)
    put(m("sash"), pick(PALS["leather"], shaded(PALS["leather"], 0.52, 0.32) - seam * (n_mid > 0.4) + (n_hi > 0.88)))
    # 錨鉄の板: 打ち跡・縁の摩耗・錆の垂れ (C は濡れた黒っぽい鉄と藤壺の白い粉)
    hammer = hash1(np.floor(X * 0.8) * 7 + np.floor(Y * 0.8) * 3 + np.floor(Z * 0.8) * 11 + fi, 4) * 0.10 - 0.05
    pl = PALS["plate"]
    pidx = shaded(pl, 0.50 if o != "c" else 0.44, 0.34, extra=hammer) - (pit < 0.02)
    put(m("plate"), pick(pl, pidx))
    put(m("plate") & convex & (lit > -0.2), pick(pl, pidx + 2))
    put(m("bevel"), pick(pl, shaded(pl, 0.74, 0.26)))
    streak = (np.abs(np.sin(X * 2.3 + Z * 1.7 + 3 * n_lo)) > 0.94) & (n_mid > 0.55)
    rust = m("plate") & ((streak & (D > 0.8)) | (concave & (n_mid > 0.72)) | ((n_mid > 0.84) & (np.abs(up) < 0.6)))
    put(rust, pick(PALS["rust"], np.floor(1.2 + 1.4 * (0.5 + 0.5 * lit) + bay).astype(int)))
    if o == "c":
        salt = m("plate") & (up > 0.45) & ((n_mid + 0.6 * n_lo + 0.4 * up) > 1.42)
        put(salt, pick(PALS["barn"], np.floor(1.6 + 1.4 * lit + bay).astype(int)))
    br = PALS["brass"]
    bidx = shaded(br, 0.56, 0.40)
    put(m("brass"), pick(br, bidx))
    put(m("brass") & convex & (lit > 0), pick(br, bidx + 1))
    put(m("brass") & ((concave & (n_mid > 0.45)) | ((n_mid + 0.5 * n_lo - 0.4 * up) > 1.3)), pick(PALS["verd"], np.floor(1.0 + 1.6 * (0.5 + 0.5 * lit) + bay).astype(int)))
    put(m("lead"), pick(PALS["lead"], shaded(PALS["lead"], 0.5, 0.3) - (pit < 0.05)))
    put(m("hose"), pick(PALS["hose"], shaded(PALS["hose"], 0.5, 0.36) - ((np.floor(Y * 2.4 + X * 1.2 + Z * 1.2) % 2) == 0)))
    gd = PALS["glassd"]
    hl = (np.abs((LU - LV * 0.8) % 9 - 2) < 1.0) & (D > 1.0)
    put(m("glassd"), pick(gd, shaded(gd, 0.42, 0.2, ed=False) + hl * 2))
    put(m("barn"), pick(PALS["barn"], shaded(PALS["barn"], 0.56, 0.30) - (pit < 0.12) * 2))
    put(m("kelp"), pick(PALS["kelp"], shaded(PALS["kelp"], 0.5, 0.34) - thread))
    ch = PALS["chain"]
    rings = ((LU // 2 + LV // 2) % 2) == 0
    put(m("chainm"), pick(ch, shaded(ch, 0.48, 0.3) - rings + (n_hi > 0.8)))
    put(m("chain"), pick(ch, shaded(ch, 0.5, 0.32) - (pit < 0.08)))
    put(m("dark"), pick(PALS["dark"], np.floor(0.8 + 1.0 * (0.5 + 0.5 * lit) + 0.6 * bay).astype(int)))
    # 光
    put(m("gem"), ramp(gp, np.clip(0.25 + 0.5 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.06 * bay + 0.2 * (D > 1.5), 0, 0.92)))
    put(m("lume"), ramp(gp, 0.45 + 0.3 * (0.5 + 0.5 * lit) + 0.06 * bay))
    glow |= np.isin(mats, ["gem", "lume"])
    if o == "c":      # 鉄の割れ目の紫 (淵の光)
        rn = np.abs(vnoise(L * 0.2 + 7, 5) - 0.5)
        crk = m("plate") & (rn < 0.007) & INS & (D > 1.0) & (n_lo > 0.45)
        put(crk, ramp(gp, 0.35 + 0.5 * hash1(xx + yy * 3, 1)))
        glow |= crk
    # 縁 (レアの青い鋼): 打ち出しの点と、真ん中の光る破線
    tp = PALS["trim"]
    tidx = shaded(tp, 0.56, 0.30)
    chase = ((LU + LV) % 4 == 0) & (np.minimum(CW, CH) >= 2) & (D > 0.9)
    put(m("trim"), pick(tp, tidx - chase))
    put(m("trim") & convex, pick(tp, tidx + 1))
    along_u = CW >= CH
    dash = (np.where(along_u, LU, LV) % 8) < (5 if lv == 0 else 8)
    line = np.where(along_u, (LV == CH // 2) & (CH >= 3), (LU == CW // 2) & (CW >= 3)) & INS & dash
    tl = m("trim") & line & (np.maximum(CW, CH) >= 6)
    put(tl, ramp(gp, 0.5 + 0.4 * hash1(LU + LV * 3, 2)))
    glow |= tl
    if lv >= 2:      # +10: 縁の角も光り、真鍮の凸の縁にかすかな灯り
        te = m("trim") & convex & INS
        put(te, ramp(gp, 0.55 + 0.3 * hash1(LU * 5 + LV, 3)))
        glow |= te
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


def build_piece(faces, o):
    faces = cull_inside([f for f in faces if f is not None])
    lpkit.TEX = 2.0
    size, pos, N_, M, L = build_atlas(faces, 32, 0)
    atlas, glow = paint(faces, size, pos, N_, M, L, o)
    lv = {k: paint(faces, size, pos, N_, M, L, o, lv=i) for i, k in ((1, "_plus5"), (2, "_plus10"))}
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces), levels=lv)


# ====================================================================== 描く
for _k, _rgb in (("lamp", (255, 180, 80)), ("void", (190, 140, 255))):
    scene.GLOW_RGB["ad2_" + _k] = _rgb
BG = (26, 24, 36)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def render(res, o, yaw, pitch, size, scale, center=(0, 18.5, 0), pose=None, only=None, mann=True):
    parts = [(r["faces"], r["atlas"], r["glow"]) for k, r in res.items() if only is None or k in only]
    flat = mannequin() if mann else []
    faces, atlas, glow = scene.compose(parts, flat)
    if pose:
        faces = pose_faces(faces, pose)
    im = scene.view(faces, atlas, glow, "ad2_" + GLOWK[o], yaw, pitch, size, center, scale, ss=2)
    bg = Image.new("RGBA", size, BG + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB").transpose(Image.FLIP_LEFT_RIGHT)


def walk(a=22):
    return {"armorRightArm": (a, 0, -3), "armorLeftArm": (-a, 0, 3), "armorRightLeg": (-a, 0, 0), "armorLeftLeg": (a, 0, 0),
            "armorRightBoot": (-a, 0, 0), "armorLeftBoot": (a, 0, 0)}


def first_person(res, o, size):
    parts = [(r["faces"], r["atlas"], r["glow"]) for k, r in res.items() if k != "helmet"]
    flat = [(fs, rgb) for fs, rgb in mannequin() if not any(getattr(f, "bone", "") == "armorHead" for f in fs)]
    faces, atlas, glow = scene.compose(parts, flat)
    faces = pose_faces(faces, {"armorRightArm": (44, 0, 3), "armorLeftArm": (28, 0, -3)})
    im = scene.view(faces, atlas, glow, "ad2_" + GLOWK[o], 0, 50, size, (0, 18.0, -4.0), 8.6, ss=2)
    bg = Image.new("RGBA", size, (46, 60, 78, 255))
    bg.alpha_composite(im)
    return bg.convert("RGB").transpose(Image.FLIP_LEFT_RIGHT)


def option_sheet(o, res, icons):
    TW, TH, SC = 230, 350, 6.9
    cen = (0, 19.5, 0)
    row1 = [("前", render(res, o, 180, 6, (TW, TH), SC, cen)), ("横", render(res, o, 90, 6, (TW, TH), SC, cen)),
            ("後ろ", render(res, o, 0, 6, (TW, TH), SC, cen)), ("3/4", render(res, o, 215, 14, (TW, TH), SC, cen)),
            ("歩き (後ろ 3/4)", render(res, o, 330, 10, (TW, TH), SC, cen, pose=walk(22))),
            ("兜と胸 (拡大)", render(res, o, 205, 8, (TW, TH), 13.0, (0, 26.0, 0)))]
    TW2, TH2 = 230, 260
    row2 = [(f"{PJ[p]} 単体 ({res[p]['tris']} 面)", render(res, o, 205, 12, (TW2, TH2), 8.0 if p != "helmet" else 13.0,
                                                    {"helmet": (0, 29.0, 0), "chestplate": (0, 18.5, 0), "leggings": (0, 7.0, 0), "boots": (0, 3.0, 0)}[p],
                                                    only=[p], mann=False)) for p in PIECES]
    row2.append(("一人称 (腕と袖)", first_person(res, o, (TW2 * 2, TH2))))
    W = TW * 6
    H = 60 + TH + 30 + TH2 + 30 + 170
    S = Image.new("RGB", (W, H), BG)
    dr = ImageDraw.Draw(S)
    total = sum(r["tris"] for r in res.values())
    dr.text((10, 8), f"淵潜りの鎧 v2  {NAMES[o]}   一式 {total} 面 (兜 {res['helmet']['tris']} / 胸と腕 {res['chestplate']['tris']} / 脚 {res['leggings']['tris']} / 靴 {res['boots']['tris']})",
            font=font(20), fill=(255, 232, 160))
    dr.text((10, 36), DESC[o], font=font(13), fill=(210, 206, 226))
    y = 60
    for i, (n, im) in enumerate(row1):
        S.paste(im, (i * TW, y))
        dr.text((i * TW + 6, y + TH + 6), n, font=font(14), fill=(220, 216, 236))
    y += TH + 30
    x = 0
    for n, im in row2:
        S.paste(im, (x, y))
        dr.text((x + 6, y + TH2 + 6), n, font=font(14), fill=(220, 216, 236))
        x += im.size[0]
    y += TH2 + 34
    dr.text((10, y), "インベントリのアイコン (32x32 の原寸と 4 倍)", font=font(14), fill=(220, 216, 236))
    for k, p in enumerate(PIECES):
        ic = Image.fromarray(icons[p])
        S.paste(ic, (10 + k * 44, y + 26), ic)
        big = ic.resize((128, 128), Image.NEAREST)
        S.paste(big, (220 + k * 140, y + 24), big)
    return S


DESC = {
    "a": "古い潜水服: 真鍮の丸兜 (覗き窓 3 つと格子、首の鋲の輪、額の灯、後ろの弁)、肩を覆う錨鉄の襟板、帆布の服、背の空気筒 2 本と兜へ渡る管、鉛の重りの帯、鉛底の潜水靴",
    "b": "灯り番の外套: 深い頭巾と口元の息よけ、額の灯、高い立ち襟の長い外套 (背の裾は二つに割れる)、交差する革帯と腰の袋、背から左肩越しに灯を吊る竿、長靴",
    "c": "沈んだ騎士: 閉じた兜 (覗き穴の一筋が紫に光る・頭頂の藤壺・後ろの海藻)、藤壺の胸甲と腹の小札、段の大袖、袈裟の錨の鎖、裂けた前垂れ、膝の円盤と翼、段の鉄靴",
}


# アイコンの角度は巨神シリーズ (armor/build_series_icons.py) と同じ: 脚は 2 本並べて正面寄り、靴は 2 足を 3/4 の上から
ICON_VIEW = {"helmet": (205, 14), "chestplate": (195, 6), "leggings": (190, 6), "boots": (215, 18)}


def icon_of(res, piece, o):
    import aview
    faces, atlas, glow = aview.scene_of({piece: res[piece]}, mann=False)
    yaw, pitch = ICON_VIEW[piece]
    allv = np.concatenate([np.asarray(f.verts, float) for f in faces])
    lo, hi = allv.min(0), allv.max(0)
    im = scene.view(faces, atlas, glow, "ad2_" + GLOWK[o], yaw, pitch, (512, 512), tuple(float(v) for v in (lo + hi) / 2),
                    440.0 / float(np.linalg.norm(hi - lo)), ss=1)
    return OLD.to_icon(np.array(im.transpose(Image.FLIP_LEFT_RIGHT)).astype(float))


def main():
    opts = [a for a in sys.argv[1:] if a in "abc"] or list("abc")
    OUT.mkdir(parents=True, exist_ok=True)
    thumbs = {}
    for o in opts:
        res = {p: build_piece(fs, o) for p, fs in build(o).items()}
        d = RAW / o
        d.mkdir(parents=True, exist_ok=True)
        (RAW / "icons").mkdir(parents=True, exist_ok=True)
        icons = {}
        for p, r in res.items():
            save_img(r["atlas"], d / f"{p}_atlas.png")
            gm = np.zeros((r["size"], r["size"], 4), np.uint8)
            gm[r["glow"]] = (255, 255, 255, 255)
            assert r["glow"].any(), f"{o} {p}: no glow pixels"
            save_img(gm, d / f"{p}_glowmask.png")
            for k, (la, lg) in r["levels"].items():
                save_img(la, d / f"{p}{k}_atlas.png")
                gm = np.zeros((r["size"], r["size"], 4), np.uint8)
                gm[lg] = (255, 255, 255, 255)
                save_img(gm, d / f"{p}{k}_glowmask.png")
            export_mesh(r["faces"], r["size"], d / f"{p}_mesh.json", f"abyss_diver_v2_{o}_{p}", f"{p}_atlas.png", f"{p}_glowmask.png")
            icons[p] = icon_of(res, p, o)
            Image.fromarray(icons[p]).save(RAW / "icons" / f"{o}_{p}_32.png")
        option_sheet(o, res, icons).save(OUT / f"opt_{o}.png", optimize=True)
        thumbs[o] = [render(res, o, 180, 6, (230, 350), 6.9, (0, 19.5, 0)), render(res, o, 0, 6, (230, 350), 6.9, (0, 19.5, 0)),
                     render(res, o, 215, 14, (230, 350), 6.9, (0, 19.5, 0))]
        print(o, {p: (r["tris"], r["size"]) for p, r in res.items()}, flush=True)
    if len(thumbs) == 3:
        S = Image.new("RGB", (230 * 9, 350 + 40), BG)
        dr = ImageDraw.Draw(S)
        for i, o in enumerate("abc"):
            for j, im in enumerate(thumbs[o]):
                S.paste(im, ((i * 3 + j) * 230, 36))
            dr.text((i * 690 + 10, 8), NAMES[o], font=font(18), fill=(255, 232, 160))
        S.save(OUT / "compare.png", optimize=True)


if __name__ == "__main__":
    main()
