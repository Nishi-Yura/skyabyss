"""武器の細かさ 3 段階 (D1 今のまま / D2 整え / D3 細密) のラフ。防具 S2 のマネキンと並べて見る。
実行: python wdetail.py [weapon_id ...]  -> docs/review/weapon_detail/img/"""
from __future__ import annotations

import importlib
import json
import math
import sys

from PIL import Image

HERE = __import__("pathlib").Path(__file__).resolve().parent
sys.path.append(str(HERE.parent / "armor"))
from scene import *  # noqa: F401,F403
import wkit
from wkit import block, prism, curve, extrude, blade, V, rot_x, rot_y, rot_z, BONE
from paint import paint
import mock
from mock import xf
from td_kit import pearl
from st_kit import plume, band as sband
from wh_kit import tooth
from em_kit import ridge
import aview, spaint, sstyle  # armor
from akit import mannequin, pose_faces, PIV
from common import TITANS
aview.apaint = spaint
aview.SS = 2

OUT = ROOT / "docs/review/weapon_detail/img"
BG = (28, 32, 40)
LEVELS = ["D1", "D2", "D3"]
W = [  # id, module, 巨神, 持ち方, 日本語, 型
    ("whalebone_greatsword", "item_whalebone_greatsword", "whale", "whale", "鯨骨の大剣", "大剣"),
    ("tide_shell_trident", "item_tide_shell_trident", "tide", "tide", "潮殻の三叉槍", "槍"),
    ("thunder_wing_bow", "item_thunder_wing_bow", "thunder", "thunder", "雷翼の弓", "弓"),
    ("waterway_fang", "item_waterway_fang", "whale", "fangw", "水路の牙", "短剣"),
    ("ember_war_axe", "item_ember_war_axe", "ember", "ember", "余燼の戦斧", "斧"),
    ("coral_shield", "item_coral_shield", "tide", "shield", "珊瑚の盾", "盾"),
]
mock.POSES["fangw"] = dict(arm=58, pitch=44, roll=90, s3=0.85, s1=0.6)
mock.POSES["shield"] = dict(arm=62, pitch=0, roll=0, s3=1.0, s1=1.0)
CH = 0.38


# ------------------------------------------------------------------ 追加の部品
def gbar(pts, r=0.27, mat="gem"):
    """光る細い線 (表面に半分沈める)。"""
    return curve(mat, pts, [r] * len(pts), [r] * len(pts), sides=3, caps=(True, True), hint=(0, 0, 1), phase=90.0)


def ringb(mat, c, r, h, sides=6, axis="y"):
    c = V(*c)
    if axis == "y":
        return prism(mat, tuple(c - V(0, h / 2, 0)), tuple(c + V(0, h / 2, 0)), r, r, sides=sides, caps=(False, False), hint=(0, 0, 1))
    return prism(mat, tuple(c - V(0, 0, h / 2)), tuple(c + V(0, 0, h / 2)), r, r, sides=sides, caps=(True, True), hint=(1, 0, 0))


def chev(mat, x, y, z, w=1.7, t=0.5, up=1, ang=34):
    """山形の浮き彫り (山形の 2 本)。"""
    f = []
    for s in (1, -1):
        f += block(mat, (x - s * w * 0.42, y - up * w * 0.17, z), (w, 0.5, t), rot=rot_z(-s * up * ang))
    return f


def interp(xs, ys):
    return lambda y: float(np.interp(y, xs, ys))


def ex_gs(L):
    cx = interp([5.2, 10, 14.2, 17.4, 20], [0.0, 0.5, 1.1, 1.8, 2.6])
    tf = interp([5.2, 10, 14.2, 17.4, 19.6], [1.3, 1.5, 1.3, 1.0, 0.8])
    f = gbar([(cx(y), y, -tf(y) + 0.05) for y in (6.4, 9.5, 13.0, 16.4)])
    f += ringb("trim", (0, 3.0, 0), 1.75, 0.5)
    f += ringb("trim", (0, -0.9, 0), 1.6, 0.45)
    f += block("gem", (0, -2.5, -1.6), (1.1, 1.1, 0.5))
    if L >= 3:
        for y in (0.5, 1.7):
            f += ringb("wrap", (0, y, 0), 1.5, 0.4)
        for y in (7.8, 10.2):
            f += chev("spine", cx(y), y, -tf(y) + 0.1, w=2.2)
        f += ringb("spine", (0, 2.5, 0), 2.7, 0.6, sides=6)
        f += ringb("trim", (0, 5.0, 0), 3.85, 0.4, sides=6)
        for s in (1, -1):
            f += tooth(s * 3.7, 4.8, (s * 0.5, 1, 0), 3.2, 0.75)
            f += block("gem", (s * 2.3, 4.2, -1.55), (0.7, 0.7, 0.4))
    return f


def spike_acc(s):
    return wkit.spike(BONE, "acc", (s * 1.8, 12.0, 0), (s * 1.0, 0.5, 0), 2.8, 0.6, sides=4, rings=1, hint=(0, 0, 1))


def ex_tr(L):
    f = gbar([(0, 14.3, -0.98), (0, 17.0, -0.98), (0, 19.6, -0.5)])
    f += ringb("trim", (0, 4.9, 0), 1.4, 0.5)
    f += ringb("trim", (0, 8.8, 0), 1.4, 0.45)
    for s in (1, -1):
        f += block("acc", (s * 1.7, 12.3, -2.0), (0.9, 0.9, 0.7), taper=(0.6, 0.6))
    if L >= 3:
        for y in (6.0, 7.2, 10.2):
            f += ringb("trim", (0, y, 0), 1.3, 0.3)
        for y in (15.4, 16.8):
            f += chev("edge", 0, y, -0.95, w=1.5)
        for y in (-0.2, 2.2):
            f += pearl((0.0, y, -1.15), 0.55)
        for s in (1, -1):
            f += spike_acc(s)
        f += ringb("trim", (0, 13.4, 0), 2.9, 0.4, sides=8)
    return f


def ex_bow(L):
    CYv = 8.0
    up = [(5.0, 10.6), (4.3, 13.0), (1.8, 15.8), (-2.4, 17.2)]
    zs = [-0.95, -0.85, -0.65, -0.35]
    f = []
    for sg in (1, -1):
        my = (lambda p: p) if sg > 0 else (lambda p: (p[0], 2 * CYv - p[1]))
        f += gbar([(*my(up[i]), zs[i]) for i in range(3)], r=0.25)
        f += sband((*my(up[1]), 0), (1, sg * 0.6, 0), 1.25, 0.6, "trim", sides=6)
        f += sband((*my(up[3]), 0), (1, sg * 0.3, 0), 0.7, 0.5, "trim", sides=6)
    for dy in (-0.55, 0.55):
        f += ringb("wrap", (5.0, CYv + dy, 0), 1.4, 0.35)
    if L >= 3:
        for sg in (1, -1):
            my = (lambda p: p) if sg > 0 else (lambda p: (p[0], 2 * CYv - p[1]))
            f += sband((*my(up[0]), 0), (1, sg * 0.2, 0), 1.2, 0.5, "trim", sides=6)
            f += plume((*my((3.0, 12.4)), 0.7), (*my((7.4, 13.4)), 0.7), 0.7, 0.3, bend=-0.3 * sg, scorch=1)
            f += block("gem", (*my((4.6, 12.0)), -0.95), (0.7, 0.7, 0.4))
            f += plume((6.0, CYv + sg * 1.8, -0.2), (9.4, CYv + sg * 4.6, -0.2), 0.8, 0.3, bend=-0.6 * sg, scorch=1)
        f += ringb("spine", (5.0, CYv + 1.9, 0), 1.35, 0.4)
        f += ringb("spine", (5.0, CYv - 1.9, 0), 1.35, 0.4)
    return f


def wh_charm():
    from wh_kit import charm
    return charm(2.0, 4.4, 2.0, z=-1.4)


def ex_fang(L):
    cx = interp([5.8, 8, 11, 14], [0, 0.4, 1.3, 2.5])
    tf = interp([5.8, 8, 11, 14], [1.1, 1.2, 1.0, 0.6])
    f = gbar([(cx(y), y, -tf(y) + 0.05) for y in (6.8, 9.0, 11.4, 13.0)], r=0.25)
    for y in (1.8, 3.0):
        f += ringb("wrap", (0, y, 0), 1.35, 0.4)
    f += block("gem", (0, 0.2, -1.55), (0.9, 0.8, 0.4))
    f += ringb("trim", (0, 3.9, 0), 1.5, 0.4)
    if L >= 3:
        for y in (7.4, 9.0):
            f += chev("spine", cx(y), y, -tf(y) + 0.1, w=1.8)
        f += ringb("spine", (0, 4.6, 0), 1.75, 0.4)
        for y in (8.6, 10.6, 12.4):
            f += tooth(-2.4 + cx(y) * 0.2, y, (-1, 0.35, 0), 1.9, 0.5)
        f += wh_charm()
    return f


def ex_axe(L):
    cy = 14.0
    f = gbar([(-1.0, cy - 2.5, -1.5), (-2.4, cy - 0.8, -1.5), (-3.4, cy - 1.9, -1.5), (-4.6, cy + 0.5, -1.45)])
    f += ringb("edge", (0, 9.6, 0), 1.45, 0.4)
    f += ringb("edge", (0, 11.2, 0), 1.45, 0.4)
    f += block("gem", (0, -2.7, -1.3), (0.9, 0.8, 0.4))
    if L >= 3:
        for y in (1.2, 2.6, 4.0, 6.4, 8.0):
            f += ringb("wrap", (0, y, 0), 1.3, 0.35)
        pl = [(-1.2, -2.6), (-3.8, -3.4), (-4.9, -0.6), (-4.2, 2.6), (-1.2, 2.8)]
        for z in (-1.3, 1.3):
            f += extrude("spine", pl, 0.7, c=(0, cy, z))
        f += gbar([(-1.2, cy + 1.2, -1.66), (-2.4, cy - 0.2, -1.66), (-3.6, cy + 0.9, -1.66)], r=0.2)
        f += ridge((1.0, cy + 2.4, 0), (0.1, 1, 0), 3, 2.4, (0.0, 0.0, 0.0), 0.6)
        f += ringb("trim", (0, cy - 4.2, 0), 1.6, 0.45)
        f += ringb("trim", (0, cy + 3.6, 0), 1.6, 0.45)
    return f


def ex_shield(L):
    OUT_ = [(0, -1.0), (3.8, 2.4), (5.6, 7.4), (4.8, 12.4), (2.6, 15.8), (0, 17.0), (-2.6, 15.8), (-4.8, 12.4), (-5.6, 7.4), (-3.8, 2.4)]

    def sh(k):
        return [(x * k, 8.0 + (y - 8.0) * k) for x, y in OUT_]
    ring_ = sh(0.66)
    f = gbar([(x, y, -1.62) for x, y in ring_ + [ring_[0]]], r=0.22)
    f += ringb("trim", (0, 8.4, -1.6), 2.1, 0.5, axis="z", sides=8)
    if L >= 3:
        r2 = sh(0.5)
        f += gbar([(x, y, -1.62) for x, y in r2 + [r2[0]]], r=0.2, mat="trim")
        for x, y in sh(0.93)[::2]:
            f += block("acc", (x, y, -1.2), (0.8, 0.8, 0.6), taper=(0.6, 0.6))
        for dx in (-3.0, 3.0):
            f += wkit.spike(BONE, "acc", (dx, 15.0 - abs(dx) * 0.4, -0.3), (dx * 0.12, 1, 0), 2.6, 0.7, sides=4, rings=1, hint=(0, 0, 1))
        f += wkit.spike(BONE, "acc", (0, 16.4, -0.3), (0, 1, 0), 3.0, 0.8, sides=4, rings=1, hint=(0, 0, 1))
    return f


EX = dict(whalebone_greatsword=ex_gs, tide_shell_trident=ex_tr, thunder_wing_bow=ex_bow, waterway_fang=ex_fang,
          ember_war_axe=ex_axe, coral_shield=ex_shield)


# ------------------------------------------------------------------ 構築
def make(wid, mod, level):
    L = LEVELS.index(level) + 1
    wkit.CHAMFER[0] = CH if L >= 2 else 0.0
    fs = mod.faces("rare") if wid != "thunder_wing_bow" else mod.faces("rare", 0)
    if L >= 2:
        fs = fs + EX[wid](L)
    wkit.CHAMFER[0] = 0.0
    faces, size, P, N, M, Lc = build(fs)
    atlas, glow = paint(faces, size, P, N, M, Lc, mod.TITAN, "rare", 0, mod.inlay, mod.EDGE)
    return dict(faces=faces, atlas=atlas, glow=glow, tris=tri_count(faces), mod=mod)


ARMOR = {}


def armor(T):
    if T not in ARMOR:
        ARMOR[T] = aview.build_set(sstyle.build(T, "rare", "S2"), T, "rare")
    return ARMOR[T]


def bbox(r):
    v = np.concatenate([f.verts for f in r["faces"]])
    return v.min(0), v.max(0)


def put(im):
    b = Image.new("RGBA", im.size, BG + (255,))
    b.alpha_composite(im)
    return b.convert("RGB")


def tile(r, titan, yaw, pitch, size, cen, scale):
    return put(view(r["faces"], r["atlas"], r["glow"], titan, yaw, pitch, size, cen, scale, ss=2))


def held(r, wkey, T, size=(300, 380), yaw=205, pitch=-6):
    A = armor(T)
    parts = [(a["faces"], a["atlas"], a["glow"]) for a in A.values()]
    p = mock.POSES[wkey]
    arm = p["arm"]
    hand = V(*PIV["armorRightArm"]) + rot_x(arm) @ V(0, -11.0, 0)
    grip = V(*r["mod"].GRIP)
    if wkey == "shield":
        R = rot_y(180) @ rot_x(-12)
        hand = hand + V(0, 0, -3.5)
    else:
        R = rot_x(-p["pitch"]) @ rot_y(p["roll"])
    wf = xf(r["faces"], R, hand, p["s3"], grip)
    faces, atlas, glow = compose(parts + [(wf, r["atlas"], r["glow"])], mannequin())
    faces = pose_faces(faces, {"armorRightArm": (arm, 0, 0)})
    im = view(faces, atlas, glow, T, yaw, pitch, size, (0, 20, -8), 7.0, ss=2)
    return put(im).transpose(Image.FLIP_LEFT_RIGHT)


def run(ids):
    OUT.mkdir(parents=True, exist_ok=True)
    sp = OUT / "stats.json"
    stats = json.loads(sp.read_text(encoding="utf-8")) if sp.exists() else {}
    for wid, modn, T, wkey, jp, kind in W:
        if ids and wid not in ids:
            continue
        mod = importlib.import_module(modn)
        res = {lv: make(wid, mod, lv) for lv in LEVELS}
        lo, hi = bbox(res["D3"])
        lo1, hi1 = bbox(res["D1"])
        lo, hi = np.minimum(lo, lo1), np.maximum(hi, hi1)
        cen = tuple((lo + hi) / 2)
        TW, TH = 200, 280
        sc = min(TW * 0.82 / max(hi[0] - lo[0], 1), TH * 0.86 / max(hi[1] - lo[1], 1))
        sc = min(sc, 14.0)
        sheet = Image.new("RGB", (3 * 220 + 228, 3 * 290), BG)
        for i, lv in enumerate(LEVELS):
            r = res[lv]
            strip = Image.new("RGB", (12 * 150, 210))
            for k in range(12):
                strip.paste(tile(r, T, 180 + 30 * k, -8, (150, 210), cen, sc * 0.72), (k * 150, 0))
            strip.save(OUT / f"turn_{wid}_{lv}.png", optimize=True)
            x = 0
            for yaw, pitch, w in ((180, 0, 220), (215, -10, 220), (140, -10, 220)):
                sheet.paste(tile(r, T, yaw, pitch, (w, 290), cen, sc * 0.95), (x, i * 290))
                x += w
            hd = held(r, wkey, T)
            sheet.paste(hd.resize((228, 290)), (x, i * 290))
            hd.save(OUT / f"held_{wid}_{lv}.png", optimize=True)
            stats.setdefault(wid, {})[lv] = r["tris"]
            print(wid, lv, r["tris"], flush=True)
        sheet.save(OUT / f"sheet_{wid}.png", optimize=True)
        sp.write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
        for lv in LEVELS:
            tile(res[lv], T, 200, -6, (200, 280), cen, sc).save(OUT / f"one_{wid}_{lv}.png", optimize=True)


def family():
    for lv in LEVELS:
        im = Image.new("RGB", (6 * 200, 280), BG)
        for i, (wid, *_r) in enumerate(W):
            im.paste(Image.open(OUT / f"one_{wid}_{lv}.png"), (i * 200, 0))
        im.save(OUT / f"family_{lv}.png", optimize=True)


if __name__ == "__main__":
    a = sys.argv[1:]
    run([x for x in a if x != "family"])
    if not a or "family" in a:
        family()
