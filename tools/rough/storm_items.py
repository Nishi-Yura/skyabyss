"""雷翼の装備 (弓・雷獣の爪・羽守の鉤槍) と、アイコンだけの品 (護符・足環・羽根・欠片・心核・干し果・鱗粉) のラフ 3 案ずつを作る。

使い方:
    python tools/rough/storm_items.py

出力 (docs/review/storm_gear_roughs/):
    compare_<item>.png    案ごとの比較 (アイコン・3D の正面と斜め。弓は引いた絵も)
    compare_icons.png     アイコンだけの品の比較
    <item>/<a|b|c>/icon.png        32x32 のアイコン (武器は斜め、右上が刃先。弓はバニラの弓と同じ向き)
    <item>/<a|b|c>/upright.png     3D の元になる立てた向きの 32x32 (確認用)
    <item>/<a|b|c>/voxels.npz      3D の彫刻ボクセル。弓は引き絵の 3 枚 (pull_0..2) も同じファイルに入れる
    <item>/<a|b|c>/icon_pull_<n>.png   弓の引き絵 (32x32)
    <item>/<a|b|c>/front.png, hand.png 3D の確認画像

作り方は潮殻のラフ (tools/rough/tide_gear.py) の部品をそのまま使う: 32px の絵を層 (図形) で描き、
アイコンは 32px に縮めて影と輪郭、3D は 1 画素 = 3 ボクセルで縁からの距離で断面を丸め、盛り上がりの層を足す
(2D を厚くしただけにしない)。色は雷翼の巨神 (採用案 A 嵐の大鷲) と同じ: 灰青の羽、白い羽に灰の横縞、銅の冠、稲妻の白金と青白。
赤・橙の炎の色は使わない (余燼と分ける)。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import tide_gear as tg  # noqa: E402
from tide_gear import Pic, arc, compose, ell, line, mirror, poly, rect, taper, voxelize_hi, preview_3d  # noqa: E402
from voxel import icon_big, sheet  # noqa: E402

OUT_DIR = ROOT / "docs/review/storm_gear_roughs"
ROT_DEG = tg.ROT_DEG
OPTS = ("a", "b", "c")

# 雷翼の色 (tide_gear の層は tg.PAL を名前で引くので、そこへ足す)
tg.PAL.update({
    "plume": (78, 96, 128), "plume_deep": (48, 60, 86), "plume_lite": (132, 152, 184),
    "down": (232, 236, 242), "bar": (160, 168, 184), "rear": (40, 44, 56),
    "copper": (198, 112, 60), "copper_deep": (140, 74, 40), "copper_lite": (234, 160, 100),
    "gold": (238, 198, 96), "bolt": (226, 238, 255), "bolt_blue": (150, 212, 255),
    "bone_w": (220, 212, 194), "bone_d": (170, 160, 142), "leather": (92, 68, 56), "leather_d": (62, 46, 40),
    "fruit": (138, 104, 170), "fruit_d": (96, 70, 126), "fruit_lite": (186, 160, 214),
    "apricot": (226, 178, 96), "apricot_d": (176, 128, 60),
    "stone_s": (96, 104, 118), "stone_sd": (62, 68, 80), "cloth": (120, 116, 132), "cloth_d": (84, 80, 96),
    "glass": (200, 224, 240), "string": (210, 222, 236),
})


# ---------------------------------------------------------------- 弓 (立てて描く: 弦は右、弓幹は左へ張り出す。-45 度に傾けるとバニラの弓の向き)
STRING_X = 22.0
BOW_TOP, BOW_BOT = 3.0, 29.0
PULLS = (0.0, 4.0, 6.0, 7.5)   # 待機・引き 0・1・2 の、弦の中央の引き幅 (右へ)


def bow_limb(pull):
    """弓幹の中心線。引くと先がわずかに内へ寄る。"""
    flex = pull * 0.12
    pts = []
    for t in np.linspace(0, 1, 28):
        y = BOW_TOP + flex + (BOW_BOT - BOW_TOP - 2 * flex) * t
        x = STRING_X - (12.0 - flex * 0.5) * math.sin(math.pi * t) ** 0.85
        pts.append((x, y))
    return pts


def bow(opt, pull=0.0, with_arrow=False):
    p = Pic()
    limb = bow_limb(pull)
    top, bot = limb[0], limb[-1]
    nock = (STRING_X + pull, 16.0)
    # 弦 (細い)。光る青白は A・C、白は B
    sc = "bolt_blue" if opt != "b" else "string"
    d = p.layer(sc, glow=(opt != "b"))
    line(d, [top, nock, bot], 0.95)
    if opt == "a":   # 風切羽の弓: 灰青の羽軸、外側に白い羽枝、銅の握りと先、稲妻の筋が光る
        d = p.layer("down", bump=1)
        for i in range(3, 25, 2):
            if 11 <= i <= 16:
                continue
            x, y = limb[i]
            sgn = -1 if i < 14 else 1
            poly(d, taper([(x - 1.0, y), (x - 2.6, y + sgn * 1.4), (x - 3.4, y + sgn * 3.2)], 1.6, 0.5))
        d = p.layer("plume"); poly(d, taper(limb[:14], 2.4, 3.6)); poly(d, taper(limb[13:], 3.6, 2.4))
        d = p.layer("plume_deep")
        line(d, limb[2:26], 0.5)
        d = p.layer("bolt", glow=True)
        zz = [limb[i] for i in (4, 7, 10)]
        line(d, [(zz[0][0] + 0.5, zz[0][1]), (zz[1][0] - 0.4, zz[1][1]), (zz[2][0] + 0.4, zz[2][1])], 0.45)
        zz = [limb[i] for i in (17, 20, 23)]
        line(d, [(zz[0][0] + 0.4, zz[0][1]), (zz[1][0] - 0.4, zz[1][1]), (zz[2][0] + 0.5, zz[2][1])], 0.45)
        d = p.layer("copper", bump=1)
        cx, cy = limb[14]
        rect(d, cx - 1.6, cy - 3.2, cx + 1.4, cy + 3.2)
        d = p.layer("copper_deep")
        for yy in (cy - 1.8, cy, cy + 1.8):
            rect(d, cx - 1.6, yy - 0.3, cx + 1.4, yy + 0.3)
        d = p.layer("copper_lite", bump=1)
        ell(d, top[0], top[1] + 0.3, 1.1); ell(d, bot[0], bot[1] - 0.3, 1.1)
    elif opt == "b":  # 銅の避雷弓: 銅の弓幹、先に避雷針の小さな冠、灰青の握り、握りに金の光
        d = p.layer("copper"); poly(d, taper(limb[:14], 2.4, 3.6)); poly(d, taper(limb[13:], 3.6, 2.4))
        d = p.layer("copper_deep"); line(d, limb[1:27], 0.5)
        d = p.layer("copper_lite", bump=1)
        for tip, sgn in ((top, -1), (bot, 1)):
            for k in (-1.2, 0, 1.2):
                line(d, [(tip[0] + k, tip[1]), (tip[0] + k * 1.3, tip[1] + sgn * 2.2)], 0.6)
        d = p.layer("plume", bump=1)
        cx, cy = limb[14]
        rect(d, cx - 1.7, cy - 3, cx + 1.5, cy + 3)
        d = p.layer("gold", glow=True); ell(d, cx - 0.1, cy, 0.9)
    else:            # 骨の大弓: 白い骨の弓幹に灰の横縞 (巨神の腹の羽と同じ)、銅の弭、青白い弦
        d = p.layer("bone_w"); poly(d, taper(limb[:14], 2.6, 3.8)); poly(d, taper(limb[13:], 3.8, 2.6))
        d = p.layer("bar", bump=1)
        for i in range(2, 27, 3):
            if 12 <= i <= 16:
                continue
            x, y = limb[i]
            line(d, [(x - 1.3, y), (x + 1.3, y)], 0.6)
        d = p.layer("leather", bump=1)
        cx, cy = limb[14]
        rect(d, cx - 1.8, cy - 3, cx + 1.6, cy + 3)
        d = p.layer("copper", bump=1)
        ell(d, top[0], top[1] + 0.4, 1.2); ell(d, bot[0], bot[1] - 0.4, 1.2)
        d = p.layer("bolt_blue", glow=True); ell(d, cx, cy, 0.6)
    if with_arrow:
        nx = nock[0]
        head = nx - 23.5
        d = p.layer("bone_d"); line(d, [(nx, 16), (head + 1.5, 16)], 0.9)
        d = p.layer("down", bump=0)
        poly(d, [(nx - 0.2, 16), (nx - 3.2, 14.6), (nx - 3.6, 15.6)]); poly(d, [(nx - 0.2, 16), (nx - 3.2, 17.4), (nx - 3.6, 16.4)])
        d = p.layer("copper" if opt != "b" else "plume_lite")
        poly(d, [(head, 16), (head + 2.4, 14.6), (head + 2.0, 16), (head + 2.4, 17.4)])
    return p


# ---------------------------------------------------------------- 雷獣の爪 (立てて描く。刃先が上)
def claw(opt):
    """握り (下) → 毛の籠手と青白い鬣 (中) → 三本の爪 (上)。爪は少し右へ反る。"""
    p = Pic()
    d = p.layer("leather_d"); rect(d, 14.5, 22, 17.5, 31)
    d = p.layer("copper", bump=1)
    for y in (24.5, 27.5):
        rect(d, 14, y, 18, y + 0.9)
    rect(d, 14.5, 30, 17.5, 31)
    fur = {"a": "plume", "b": "plume_deep", "c": "plume"}[opt]
    blade = {"a": "bone_w", "b": "plume_lite", "c": "bone_w"}[opt]
    d = p.layer(fur, bump=1); ell(d, 16, 20.5, 6.8, 3.0)
    d = p.layer("plume_deep")
    for x in (11.5, 14, 16.5, 19):
        line(d, [(x, 19), (x + 0.8, 22.5)], 0.5)
    if opt == "c":   # 一本の大きな鉤爪と、脇の小さな二本
        d = p.layer(blade, bump=1)
        poly(d, taper([(16, 19), (16.5, 13), (18.5, 7), (21.5, 2.5)], 5.0, 1.0))
        poly(d, taper([(11.5, 19), (11, 15), (11.8, 11.5)], 2.8, 0.8))
        poly(d, taper([(20.5, 19), (21.5, 16), (23.2, 13.5)], 2.8, 0.8))
        d = p.layer("bone_d"); line(d, [(16.6, 18), (17.3, 12), (19, 7.5)], 0.5)
        d = p.layer("bolt_blue", glow=True); ell(d, 21.2, 3.0, 0.9); ell(d, 11.8, 11.8, 0.6); ell(d, 23, 13.8, 0.6)
    else:
        d = p.layer(blade, bump=1)
        for x0, lean, top in ((11.8, 1.2, 5.5), (16, 2.0, 2.2), (20.2, 2.6, 5.5)):
            poly(d, taper([(x0, 19), (x0 + lean * 0.3, 13), (x0 + lean, top + 2.5), (x0 + lean * 1.5, top)], 3.4, 0.8))
        d = p.layer("bone_d" if opt == "a" else "plume")
        for x0, lean, top in ((11.8, 1.2, 5.5), (16, 2.0, 2.2), (20.2, 2.6, 5.5)):
            line(d, [(x0 + 0.5, 18), (x0 + lean * 0.3 + 0.5, 13), (x0 + lean + 0.3, top + 3)], 0.45)
        if opt == "a":
            d = p.layer("bolt_blue", glow=True)
            for x0, lean, top in ((11.8, 1.2, 5.5), (16, 2.0, 2.2), (20.2, 2.6, 5.5)):
                ell(d, x0 + lean * 1.45, top + 0.4, 0.7)
        else:   # B 金の稲妻の筋
            d = p.layer("gold", glow=True)
            line(d, [(12.3, 16), (13.6, 12.5), (12.6, 10.5), (13.6, 8)], 0.45)
            line(d, [(16.6, 16), (18, 11), (17, 8.5), (18.2, 4.5)], 0.45)
            line(d, [(20.8, 16), (22.2, 12.5), (21.2, 10.5), (22.4, 8)], 0.45)
    # 鬣の青白い棘 (雷獣の背と同じ光)
    d = p.layer("bolt_blue", glow=True)
    for x in (12, 16, 20):
        poly(d, [(x - 0.7, 18.8), (x + 0.7, 18.8), (x + 0.1, 17.2)])
    return p


# ---------------------------------------------------------------- 羽守の鉤槍 (立てて描く。敵の gear/hooked_glaive と同じ色と形)
def glaive(opt):
    """骨色の柄、暗い革の巻き、石突きの銅。上は銅の穂先と、左へ巻いた銅の鉤 (鉤の内に青白い目)。"""
    p = Pic()
    d = p.layer("bone_w"); rect(d, 15.2, 8, 16.8, 31)
    d = p.layer("leather_d", bump=1)
    for y in (17, 23.5):
        rect(d, 14.8, y, 17.2, y + 1.8)
    d = p.layer("copper", bump=1); rect(d, 14.9, 29.5, 17.1, 31)
    d = p.layer("copper"); poly(d, taper([(16, 9), (16, 4), (16, 0.6)], 3.0, 0.6))
    d = p.layer("copper_deep", bump=1); rect(d, 14.6, 8, 17.4, 9.6)
    if opt == "a":   # 片側の鉤 (敵の鉤槍と同じ)
        d = p.layer("copper", bump=1)
        hook = [(15.4, 9)] + arc(11.2, 7.5, 4.4, 10, -200, 22)[1:] + [(9.4, 11.2)]
        poly(d, taper(hook, 3.2, 1.8))
        d = p.layer("copper_lite"); line(d, arc(11.2, 7.5, 4.6, -20, -170, 14), 0.4)
        d = p.layer("bolt_blue", glow=True); ell(d, 9.0, 7.6, 0.85)
    elif opt == "b":  # 両側の三日月の鉤 (斧に近い)
        d = p.layer("copper", bump=1)
        poly(d, taper([(15.4, 10)] + arc(11.5, 9.0, 4.0, 15, -190, 18)[1:], 3.0, 1.6))
        poly(d, taper(mirror([(15.4, 10)] + arc(11.5, 9.0, 4.0, 15, -190, 18)[1:]), 3.0, 1.6))
        d = p.layer("bolt_blue", glow=True); ell(d, 9.2, 9.0, 0.7); ell(d, 22.8, 9.0, 0.7)
    else:             # 鉤から灰青の羽が垂れる
        d = p.layer("copper", bump=1)
        poly(d, taper([(15.4, 9)] + arc(11.2, 7.5, 4.4, 10, -200, 22)[1:], 3.0, 1.6))
        d = p.layer("plume", bump=1)
        for x, y in ((8.5, 10), (10, 10.5), (11.5, 11)):
            poly(d, taper([(x, y), (x - 0.6, y + 3.5), (x - 1.2, y + 6)], 1.6, 0.4))
        d = p.layer("down")
        for x, y in ((8.5, 10), (10, 10.5), (11.5, 11)):
            line(d, [(x - 0.2, y + 2), (x - 0.7, y + 4.5)], 0.3)
        d = p.layer("bolt_blue", glow=True); ell(d, 9.0, 7.6, 0.75)
    return p


# ---------------------------------------------------------------- アイコンだけ
def charm(opt):
    p = Pic()
    d = p.layer("leather"); line(d, [(12, 8), (16, 3.5), (20, 8)], 1.1)
    if opt == "a":   # 銅の避雷針の小さな束を革で縛り、針のあいだに稲妻
        d = p.layer("copper")
        for x, top in ((12.5, 9.5), (16, 7), (19.5, 9.5)):
            poly(d, taper([(x, 27), (x, top + 2), (x, top)], 2.2, 0.6))
        d = p.layer("copper_lite")
        for x, top in ((12.5, 9.5), (16, 7), (19.5, 9.5)):
            line(d, [(x - 0.4, 25), (x - 0.4, top + 2.5)], 0.4)
        d = p.layer("leather"); rect(d, 10.5, 19, 21.5, 22)
        d = p.layer("leather_d"); line(d, [(10.5, 20.5), (21.5, 20.5)], 0.4)
        d = p.layer("bolt", glow=True)
        line(d, [(13.2, 11), (15.2, 13.5), (13.8, 15), (15.4, 17.5)], 0.5)
        line(d, [(16.8, 12), (18.8, 14.5), (17.4, 16), (19, 18)], 0.5)
    elif opt == "b":  # 銅の円盤に稲妻の印
        d = p.layer("copper_deep"); ell(d, 16, 18, 9.5)
        d = p.layer("copper"); ell(d, 16, 18, 7.8)
        d = p.layer("plume"); ell(d, 16, 18, 5.6)
        d = p.layer("bolt", glow=True); poly(d, [(17.5, 12), (13, 19), (16, 19), (14.5, 24), (19, 16.5), (16, 16.5)])
    else:             # 灰青の羽に金の稲妻
        d = p.layer("plume"); poly(d, taper([(16, 27), (15.5, 18), (17, 10), (16, 6)], 1.0, 0.8))
        poly(d, taper([(16, 26), (12, 20), (12.5, 12), (15.5, 7)], 4.5, 1.2))
        d = p.layer("plume_lite"); line(d, [(16, 27), (15.6, 18), (16.6, 10)], 0.5)
        d = p.layer("gold", glow=True); line(d, [(13, 11), (15, 15), (13.2, 17), (15.4, 21)], 0.6)
    return p


def anklet(opt):
    p = Pic()
    if opt == "a":   # 銅の輪に白い羽が三枚、青白い珠
        d = p.layer("copper_deep"); ell(d, 16, 13, 10, 5.2)
        d = p.layer("copper"); ell(d, 16, 13, 9, 4.2)
        d = p.layer("copper_deep"); ell(d, 16, 13, 7.0, 2.6)
        d = p.layer("down")
        for x in (10.5, 16, 21.5):
            poly(d, taper([(x, 17), (x - 0.4, 22), (x + 0.4, 27.5)], 2.6, 0.6))
        d = p.layer("bar")
        for x in (10.5, 16, 21.5):
            for y in (20, 23, 26):
                line(d, [(x - 0.9, y), (x + 0.9, y - 0.4)], 0.35)
        d = p.layer("bolt_blue", glow=True)
        for x in (10.5, 16, 21.5):
            ell(d, x, 16.8, 0.9)
    elif opt == "b":  # 灰青の組紐の輪と、銅の風見の飾り
        d = p.layer("plume_deep"); ell(d, 16, 12, 10, 5.2)
        d = p.layer("plume"); ell(d, 16, 12, 9, 4.2)
        d = p.layer("plume_deep"); ell(d, 16, 12, 7.0, 2.6)
        d = p.layer("plume_lite")
        for a in range(0, 360, 30):
            x = 16 + 8 * math.cos(math.radians(a)); y = 12 + 3.6 * math.sin(math.radians(a))
            ell(d, x, y, 0.5)
        d = p.layer("copper"); line(d, [(16, 16.5), (16, 21)], 0.6)
        poly(d, [(16, 21), (21, 23), (16, 25)]); poly(d, [(16, 21.5), (11.5, 23), (16, 24.5)])
        d = p.layer("bolt", glow=True); ell(d, 16, 23, 0.8)
    else:             # 銀の輪に稲妻の刻み
        d = p.layer("bar"); ell(d, 16, 16, 10.5, 6)
        d = p.layer("down"); ell(d, 16, 16, 9.5, 5)
        d = p.layer("bar"); ell(d, 16, 16, 7.4, 3.2)
        d = p.layer("bolt_blue", glow=True)
        line(d, [(8, 15), (10.5, 18), (12.5, 15.5), (15, 19.5), (17.5, 15.5), (20, 19.5), (22, 15.5), (24, 17)], 0.5)
    return p


def eye_feather(opt):
    p = Pic()
    if opt == "a":   # 白い大きな羽に灰の横縞、根元に光る嵐の目の輪
        d = p.layer("bone_d"); line(d, [(9, 28), (13, 21), (19, 11), (23.5, 4)], 0.8)
        d = p.layer("down"); poly(d, taper([(12, 23), (15, 17), (19, 11), (22.5, 5.5)], 6.8, 1.8))
        d = p.layer("bar")
        for t in (0.25, 0.45, 0.65, 0.85):
            x = 12 + 10.5 * t; y = 23 - 17.5 * t
            line(d, [(x - 2.6, y - 1.4), (x + 2.6, y + 1.4)], 0.6)
        d = p.layer("plume_deep"); ell(d, 13.2, 21.2, 2.6)
        d = p.layer("bolt_blue", glow=True); ell(d, 13.2, 21.2, 1.8)
        d = p.layer("plume_deep"); ell(d, 13.2, 21.2, 0.8)
    elif opt == "b":  # 灰青の羽に金の目玉の斑
        d = p.layer("bone_d"); line(d, [(9, 28), (13, 21), (19, 11), (23.5, 4)], 0.8)
        d = p.layer("plume"); poly(d, taper([(12, 23), (15, 17), (19, 11), (22.5, 5.5)], 6.8, 1.8))
        d = p.layer("plume_lite"); line(d, [(12, 23), (19, 11), (22.5, 5.5)], 0.5)
        d = p.layer("gold"); ell(d, 19, 11.5, 2.6, 2.0)
        d = p.layer("plume_deep"); ell(d, 19, 11.5, 1.6, 1.2)
        d = p.layer("bolt", glow=True); ell(d, 18.6, 11.2, 0.6)
    else:             # 渦 (嵐の目) を羽に描く
        d = p.layer("bone_d"); line(d, [(9, 28), (13, 21), (19, 11), (23.5, 4)], 0.8)
        d = p.layer("plume_lite"); poly(d, taper([(12, 23), (15, 17), (19, 11), (22.5, 5.5)], 6.8, 1.8))
        d = p.layer("bolt_blue", glow=True)
        pts = [(17 + 0.35 * t * math.cos(t), 14 + 0.35 * t * math.sin(t)) for t in np.linspace(0, 10, 40)]
        line(d, pts, 0.5)
    return p


def shard(opt):
    p = Pic()
    if opt == "a":   # 灰青の羽軸のかけら、白い羽枝、稲妻の筋
        d = p.layer("plume"); poly(d, [(9, 26), (13, 9), (19, 4), (23, 8), (20, 22), (13, 28)])
        d = p.layer("plume_lite"); poly(d, [(13, 9), (19, 4), (17, 12), (12, 18)])
        d = p.layer("down")
        for y in (12, 16, 20, 24):
            line(d, [(20.5, y - 4), (23, y - 6)], 0.6)
        d = p.layer("bolt", glow=True); line(d, [(14, 25), (17, 18), (14.5, 15), (17.5, 8)], 0.7)
    elif opt == "b":  # 青白い結晶
        d = p.layer("plume_lite"); poly(d, [(16, 3), (23, 12), (20, 28), (12, 28), (9, 12)])
        d = p.layer("glass"); poly(d, [(16, 3), (19, 12), (16, 28), (12, 28), (9, 12)])
        d = p.layer("bolt_blue", glow=True); poly(d, [(16, 9), (18, 15), (16, 23), (14, 15)])
    else:             # 銅の先の付いた羽のかけら
        d = p.layer("plume_deep"); poly(d, [(10, 27), (12, 12), (18, 6), (22, 10), (20, 24), (14, 28)])
        d = p.layer("plume"); poly(d, [(12, 12), (18, 6), (17, 14), (12, 20)])
        d = p.layer("copper"); poly(d, [(18, 6), (21, 2.5), (23, 4), (22, 10)])
        d = p.layer("bolt_blue", glow=True); line(d, [(14, 24), (16.5, 18), (14.5, 15), (17, 10)], 0.6)
    return p


def core(opt):
    p = Pic()
    frame = {"a": "stone_s", "b": "copper", "c": "plume"}[opt]
    deep = {"a": "stone_sd", "b": "copper_deep", "c": "plume_deep"}[opt]
    d = p.layer(frame)
    poly(d, [(11, 3.5), (21, 3.5), (27.5, 10), (27.5, 22), (21, 28.5), (11, 28.5), (4.5, 22), (4.5, 10)])
    d = p.layer(deep)
    poly(d, [(11, 5.5), (21, 5.5), (25.5, 10), (25.5, 22), (21, 26.5), (11, 26.5), (6.5, 22), (6.5, 10)])
    d = p.layer(frame)
    for yy in (9, 15, 21):
        line(d, [(9, yy), (23, yy + 1)], 0.7)
    if opt == "a":   # 岩鯨・潮殻の心核と同じ石の枠に、淡い金の結晶と稲妻
        d = p.layer("gold"); poly(d, [(16, 8.5), (21.5, 16), (16, 23.5), (10.5, 16)])
        d = p.layer("bolt", glow=True); poly(d, [(17, 11), (14, 16.5), (16.5, 16.5), (15, 21), (18.5, 15), (16, 15)])
    elif opt == "b":
        d = p.layer("plume_lite"); poly(d, [(16, 9), (21, 16), (16, 23), (11, 16)])
        d = p.layer("bolt_blue", glow=True); ell(d, 16, 16, 2.4)
    else:
        d = p.layer("down"); poly(d, [(16, 9), (21, 16), (16, 23), (11, 16)])
        d = p.layer("gold", glow=True); ell(d, 16, 16, 2.0)
    return p


def fruit(opt):
    p = Pic()
    if opt == "a":   # 干した紫の実と、白い雲の綿
        d = p.layer("fruit_d"); ell(d, 15, 18, 8.5, 8)
        d = p.layer("fruit"); ell(d, 14.4, 17.2, 7.0, 6.5)
        d = p.layer("fruit_d")
        for a in (200, 250, 300, 340):
            line(d, [(15, 18), (15 + 6 * math.cos(math.radians(a)), 18 + 6 * math.sin(math.radians(a)))], 0.5)
        d = p.layer("fruit_lite"); ell(d, 12, 14.5, 1.6)
        d = p.layer("leather"); line(d, [(15, 10.5), (16.5, 7), (19, 5.5)], 0.9)
        d = p.layer("down"); ell(d, 22, 22.5, 3.2, 2.4); ell(d, 25, 21, 2.4, 2); ell(d, 19.5, 24, 2.2, 1.6)
    elif opt == "b":  # 淡い金の干し杏の輪切り
        for cx, cy in ((12, 13), (19, 19)):
            d = p.layer("apricot_d"); ell(d, cx, cy, 6.5)
            d = p.layer("apricot"); ell(d, cx, cy, 5.2)
            d = p.layer("apricot_d"); ell(d, cx, cy, 1.4)
        d = p.layer("down"); ell(d, 24, 9, 2.6, 2.0)
    else:             # 薄紫の実の房
        d = p.layer("leather"); line(d, [(16, 5), (16, 11)], 0.8)
        d = p.layer("fruit_d")
        for x, y in ((12, 15), (20, 15), (16, 19), (11.5, 22), (20.5, 22), (16, 26)):
            ell(d, x, y, 3.6)
        d = p.layer("fruit_lite")
        for x, y in ((12, 15), (20, 15), (16, 19), (11.5, 22), (20.5, 22), (16, 26)):
            ell(d, x - 1, y - 1, 1.1)
    return p


def dust(opt):
    p = Pic()
    if opt == "a":   # 布の小袋から光る青白い粉がこぼれる
        d = p.layer("cloth_d"); poly(d, [(9, 26), (8, 16), (12, 11), (20, 11), (24, 16), (23, 26)])
        d = p.layer("cloth"); poly(d, [(10, 25), (9.5, 17), (13, 12.5), (19, 12.5), (22.5, 17), (22, 25)])
        d = p.layer("leather"); rect(d, 12, 10, 20, 12)
        d = p.layer("bolt_blue", glow=True)
        for x, y in ((14, 8), (17, 6.5), (19.5, 8.5), (16, 4.5), (21, 5.5), (12.5, 6)):
            ell(d, x, y, 0.7)
    elif opt == "b":  # ガラスの小瓶
        d = p.layer("glass"); poly(d, [(11, 27), (11, 15), (13.5, 12), (18.5, 12), (21, 15), (21, 27)])
        d = p.layer("bolt_blue", glow=True); rect(d, 12, 19, 20, 26)
        d = p.layer("leather"); rect(d, 13.5, 8, 18.5, 12)
    else:             # 光る粉の山
        d = p.layer("plume_lite"); poly(d, [(6, 26)] + arc(16, 26, 10, 180, 360, 16))
        d = p.layer("bolt_blue", glow=True)
        for x, y in ((12, 22), (16, 19), (20, 22), (14, 24.5), (18, 24.5), (16, 15), (22, 18), (10, 18)):
            ell(d, x, y, 0.8)
    return p


# ---------------------------------------------------------------- 項目
# kind: bow (弓。引き絵 3 枚つき) / weapon (-45 度に傾けて 3D) / icon (アイコンだけ)
# target: 立てたときの縦の長さ (単位)。thick: 断面の中心の厚み (片側、3D ボクセル)
ITEMS = {
    "thunder_wing_bow": {"kind": "bow", "draw": bow, "target": 18.0, "thick": 2, "name": "雷翼の弓"},
    "raiju_claw": {"kind": "weapon", "draw": claw, "target": 17.0, "thick": 3, "name": "雷獣の爪"},
    "warden_glaive": {"kind": "weapon", "draw": glaive, "target": 23.0, "thick": 3, "name": "羽守の鉤槍"},
    "storm_charm": {"kind": "icon", "draw": charm, "name": "嵐の護符"},
    "storm_anklet": {"kind": "icon", "draw": anklet, "name": "嵐纏の足環"},
    "storm_eye_feather": {"kind": "icon", "draw": eye_feather, "name": "嵐の目の羽根"},
    "thunder_wing_shard": {"kind": "icon", "draw": shard, "name": "雷翼の羽軸"},
    "thunder_wing_core": {"kind": "icon", "draw": core, "name": "雷翼の心核"},
    "cloudgap_fruit": {"kind": "icon", "draw": fruit, "name": "雲間の干し果"},
    "moth_dust": {"kind": "icon", "draw": dust, "name": "鱗粉"},
}


def build_option(item: str, opt: str):
    cfg = ITEMS[item]
    res = {"pulls": []}
    if cfg["kind"] == "bow":
        pics = [bow(opt, PULLS[0], False)] + [bow(opt, PULLS[i], True) for i in (1, 2, 3)]
        res["icon"] = compose(pics[0], ROT_DEG)[0]
        res["upright"] = compose(pics[0], 0.0)[0]
        for k, pic in enumerate(pics):
            g, gl = voxelize_hi(pic, cfg["thick"], 0.0)
            if k == 0:
                res["grid"], res["glow"] = g, gl
            else:
                res["pulls"].append({"icon": compose(pic, ROT_DEG)[0], "grid": g, "glow": gl})
        return res
    pic = cfg["draw"](opt)
    res["icon"] = compose(pic, ROT_DEG if cfg["kind"] == "weapon" else 0.0)[0]
    res["upright"] = compose(pic, 0.0)[0]
    res["grid"] = res["glow"] = None
    if cfg["kind"] == "weapon":
        res["grid"], res["glow"] = voxelize_hi(pic, cfg["thick"], 0.0)
    return res


def _pad_to(g, shape):
    out = np.zeros(shape + g.shape[3:], g.dtype)
    out[:g.shape[0], :g.shape[1], :g.shape[2]] = g
    return out


def write_option(item, opt, res, outdir: Path):
    outdir.mkdir(parents=True, exist_ok=True)
    cfg = ITEMS[item]
    Image.fromarray(res["icon"], "RGBA").save(outdir / "icon.png")
    Image.fromarray(res["upright"], "RGBA").save(outdir / "upright.png")
    if res["grid"] is None:
        return
    arrays = {"grid": res["grid"], "glow": res["glow"], "target": np.float64(cfg["target"]), "tilt": np.float64(ROT_DEG)}
    for k, pl in enumerate(res["pulls"]):
        Image.fromarray(pl["icon"], "RGBA").save(outdir / f"icon_pull_{k}.png")
        arrays[f"grid_pull_{k}"] = pl["grid"]
        arrays[f"glow_pull_{k}"] = pl["glow"]
    np.savez_compressed(outdir / "voxels.npz", **arrays)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    results = {}
    for item, cfg in ITEMS.items():
        cols = []
        for opt in OPTS:
            res = build_option(item, opt)
            results[(item, opt)] = res
            write_option(item, opt, res, OUT_DIR / item / opt)
            ims = [icon_big(res["icon"], 8)]
            if res["grid"] is not None:
                front, hand = preview_3d(res["grid"], res["glow"], ROT_DEG)
                front.save(OUT_DIR / item / opt / "front.png")
                hand.save(OUT_DIR / item / opt / "hand.png")
                ims += [front, hand]
                if res["pulls"]:
                    strip = np.hstack([np.array(icon_big(pl["icon"], 3).convert("RGB")) for pl in res["pulls"]])
                    ims.append(Image.fromarray(strip))
                    pf, _ = preview_3d(res["pulls"][-1]["grid"], res["pulls"][-1]["glow"], ROT_DEG)
                    ims.append(pf)
                print(item, opt, "voxels", int(res["grid"][..., 3].astype(bool).sum()))
            cols.append((f"{opt.upper()} 案", ims))
        if cfg["kind"] != "icon":
            note = "上: 32px アイコン / 3D 正面 / 3D 斜め" + (" / 引き絵 3 枚 / いっぱいに引いた 3D" if cfg["kind"] == "bow" else "")
            sheet(f"{cfg['name']} ({item}) ラフ 3 案", cols, note + "。1 画素 = 3 ボクセル、丸い断面と盛り上がり").save(
                OUT_DIR / f"compare_{item}.png")
    icons = []
    for item, cfg in ITEMS.items():
        if cfg["kind"] != "icon":
            continue
        icons.append((cfg["name"], [icon_big(results[(item, o)]["icon"], 6) for o in OPTS]))
    sheet("アイコンだけの品 3 案 (行: A / B / C)", icons, "32x32 / 6 倍表示 / 光る部分は明るい色").save(OUT_DIR / "compare_icons.png")


if __name__ == "__main__":
    main()
