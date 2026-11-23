"""NPC の持ち物 v3 (2026-11-12。全モデルの確かめで、持ち物が色 6〜15・単純な立体のラフ止まりと分かった。
直し方は案 B「持ち主の印」: tools/gen/mobs/attack_props/rough_gear_props.py / docs/review/gear_props/rough_b.png)。

前の関数 (props / props2 / props3 / cast_folk / cast_moon) と同じ名前・同じ置き場 (手の位置・受け口) で、
  - 目立って粗いもの (瓶・箱・鐘・人形・綱・角灯・鉱石・盆・天秤・桶・財布・槌・帳面・赤子・手鏡・砂時計・腸詰め) は作り直す
  - ほかは前の形に部品を足す (巻き・金具・留め具)
どれにも持ち主の印を添える: 持ち主の服の色 (人の pal の sash / scarf / trim / coat) のリボン・帯・布・紋。
絵は 128 (前は 64) で描く。書き出しと入れは tools/rough/lowpoly/npc2/build_props_v3.py。
"""
from __future__ import annotations

import math

import numpy as np

import kit as K
from kit import V, part, box, rod, disc, ellipsoid, tube, loft, ringe, spike
import props as PR
import props2 as P2
import props3 as P3
from props import hand

V3_MATS = {
    "glass3": dict(ramp=[(26, 60, 54), (50, 104, 90), (96, 160, 140), (170, 220, 200), (232, 250, 244)], base=0.5, lit=0.5, noise=0.0, crack=0, min=0),
    "glassa": dict(ramp=[(70, 40, 16), (120, 74, 30), (170, 116, 52), (220, 170, 96), (248, 222, 170)], base=0.5, lit=0.5, noise=0.0, crack=0, min=0),
    "potion": dict(ramp=[(30, 90, 50), (60, 150, 80), (120, 210, 120), (200, 250, 190)], base=0.7, lit=0.2, noise=0.02, crack=0, min=1),
    "wax": dict(ramp=[(90, 16, 20), (140, 30, 32), (186, 54, 50), (226, 100, 90)], base=0.56, lit=0.36, noise=0.04, crack=0, min=0),
    "iron3": dict(ramp=[(30, 32, 36), (56, 60, 66), (92, 98, 106), (140, 146, 154), (196, 200, 206)], base=0.56, lit=0.44, noise=0.04, crack=0, min=0),
    "oak": dict(ramp=[(46, 30, 18), (78, 52, 32), (114, 80, 50), (150, 110, 72), (184, 144, 100)], base=0.55, lit=0.32, noise=0.1, crack=0.03, min=0,
                stripe=3, axis="v", streak=0.14),
    "bronze3": dict(ramp=[(70, 44, 18), (120, 82, 34), (176, 128, 60), (220, 176, 100), (248, 222, 160)], base=0.6, lit=0.46, noise=0.02, crack=0, min=0),
    "ink": dict(ramp=[(16, 14, 20), (34, 30, 40)], base=0.6, lit=0.0, noise=0.0, crack=0, min=0),
    "shell3": dict(ramp=[(150, 120, 110), (200, 176, 164), (232, 214, 204), (250, 240, 232)], base=0.6, lit=0.34, noise=0.04, crack=0, min=0, stripe=2, axis="u"),
    "cloth3": dict(ramp=[(150, 140, 120), (190, 180, 160), (220, 212, 194), (240, 236, 222)], base=0.6, lit=0.3, noise=0.08, crack=0, min=0, stripe=2, axis="u"),
    "flame3": dict(ramp=[(220, 110, 30), (255, 170, 50), (255, 220, 120), (255, 248, 210)], base=0.75, lit=0.15, noise=0.04, crack=0, min=1),
    "glowgem": dict(ramp=[(40, 120, 150), (80, 190, 210), (160, 240, 250), (230, 255, 255)], base=0.75, lit=0.15, noise=0.0, crack=0, min=1),
    "starlit": dict(ramp=[(120, 170, 240), (180, 214, 255), (226, 240, 255), (255, 255, 255)], base=0.75, lit=0.15, noise=0.04, crack=0, min=1),
}
V3_GLOW = ["flame3", "glowgem", "starlit", "potion"]


def owner(c, k):
    """持ち主の色の材の名 (人の pal に無ければ近いものへ)"""
    p = c["pal"]
    if k == "ribbon":
        return "sash" if "sash" in p else "scarf" if "scarf" in p else "trim"
    if k == "band":
        return "trim"
    if k == "cloth":
        return "coat"
    return k


def ribbon(c, bone, at, d=(0.4, -1.0, -0.2), ln=1.8, w=0.38):
    """持ち主の色の結び目と二本の垂れ"""
    at = np.asarray(at, float)
    d = np.asarray(d, float) / np.linalg.norm(d)
    m = owner(c, "ribbon")
    F = part("rb_knot", ellipsoid(bone, m, at, (0.42, 0.32, 0.32), segs=6, rings=2))
    for s in (-1, 1):
        side = np.array([s * 0.35, 0, 0])
        F += part(f"rb_tail{s}", tube(bone, m, [at + side * 0.4, at + side + d * ln * 0.5, at + side * 1.3 + d * ln], ra=[w, w * 0.9, w * 0.7], rb=0.08, sides=4,
                                      caps=(True, True), hint=(0, 0, 1)))
    return F


def tag(c, bone, at, R=None, s=1.0):
    """持ち主の色の縁の小札 (紐付き)"""
    at = np.asarray(at, float)
    R = np.eye(3) if R is None else R
    F = part("tg_plate", box(bone, "paper", at, (0.42 * s, 0.55 * s, 0.05), R=R))
    F += part("tg_edge", box(bone, owner(c, "band"), at + R @ V(0, 0.55 * s, 0), (0.45 * s, 0.1 * s, 0.07), R=R))
    F += part("tg_mark", box(bone, owner(c, "ribbon"), at + R @ V(0, -0.05, -0.06), (0.18 * s, 0.18 * s, 0.03), R=R))
    return F


def wrap_rod(bone, m, a, b, r, n=4):
    a, b = np.asarray(a, float), np.asarray(b, float)
    F = []
    for k in range(n):
        t0, t1 = (k + 0.1) / n, (k + 0.8) / n
        F += part(f"wr{k}", rod(bone, m, a + (b - a) * t0, a + (b - a) * t1, r, sides=8))
    return F


# ====================================================================== 作り直し
def lantern(c, center, s=1.0, bone="prop_r", hook=None, star=False):
    """角灯 v3: 八角の台と笠、四本の枠、硝子、芯の台と炎 (星の灯は星の光)、提げ手に持ち主のリボン、持ち主の色の紋札"""
    x, y, z = center
    cc = V(x, y, z)
    met = "bronze3"
    F = part("l_base", disc(bone, met, cc + V(0, -1.75 * s, 0), 1.35 * s, 0.32 * s, n=8))
    F += part("l_foot", disc(bone, "iron3", cc + V(0, -2.2 * s, 0), 1.05 * s, 0.16 * s, n=8))
    F += part("l_glass", disc(bone, "lglass", cc, 1.0 * s, 1.42 * s, n=8))
    F += part("l_cup", disc(bone, "iron3", cc + V(0, -1.2 * s, 0), 0.45 * s, 0.2 * s, n=8))
    F += part("l_fire", ellipsoid(bone, "starlit" if star else "flame3", (x, y - 0.25 * s, z), (0.4 * s, 0.8 * s, 0.4 * s), segs=8, rings=3))
    for i in range(4):
        a = i / 4 * 2 * math.pi + math.pi / 4
        dx, dz = math.cos(a) * 1.08 * s, math.sin(a) * 1.08 * s
        F += part(f"l_bar{i}", rod(bone, met, V(x + dx, y - 1.5 * s, z + dz), V(x + dx, y + 1.5 * s, z + dz), 0.16 * s, sides=5))
    F += part("l_ring", disc(bone, met, cc + V(0, 1.55 * s, 0), 1.25 * s, 0.14 * s, n=8))
    F += part("l_cap", disc(bone, met, cc + V(0, 1.95 * s, 0), 1.3 * s, 0.32 * s, n=8, r_top=0.6 * s))
    F += part("l_vent", disc(bone, "iron3", cc + V(0, 2.45 * s, 0), 0.5 * s, 0.22 * s, n=8, r_top=0.3 * s))
    F += part("l_top", ellipsoid(bone, met, (x, y + 2.8 * s, z), (0.3 * s, 0.3 * s, 0.3 * s), segs=6, rings=2))
    top = hook if hook is not None else V(x, y + 3.6 * s, z)
    F += part("l_hand", tube(bone, met, [V(x - 0.9 * s, y + 2.2 * s, z), V(x - 0.7 * s, (y + 2.2 * s + top[1]) / 2 + 0.3 * s, z),
                                         V(x - 0.4 * s, top[1], top[2]), V(x + 0.4 * s, top[1], top[2]),
                                         V(x + 0.7 * s, (y + 2.2 * s + top[1]) / 2 + 0.3 * s, z), V(x + 0.9 * s, y + 2.2 * s, z)],
                             ra=0.13 * s, sides=4, caps=(True, True)))
    F += part("l_crest", disc(bone, owner(c, "ribbon"), V(x, y + 0.1 * s, z - 1.02 * s), 0.5 * s, 0.05, n=8, axis="z"))
    F += part("l_crest2", disc(bone, "bronze3", V(x, y + 0.1 * s, z - 1.08 * s), 0.22 * s, 0.04, n=6, axis="z"))
    F += ribbon(c, bone, V(x + 0.75 * s, y + 2.6 * s, z), d=(0.3, -1, -0.2), ln=1.4 * s, w=0.3 * s)
    return F


def mio_lantern(c):
    h = hand(c, -1)
    return lantern(c, V(0.0, h[1] - 3.2, h[2] - 0.4), s=1.45, hook=V(0, h[1] + 0.2, h[2] - 0.4))


def lantern_l(c):
    h = hand(c, 1)
    return lantern(c, V(0.0, h[1] - 3.2, h[2] - 0.4), s=1.3, bone="prop_l", hook=V(0, h[1] + 0.2, h[2] - 0.4))


def star_lantern_l(c):
    h = hand(c, 1)
    return lantern(c, V(0.0, h[1] - 3.2, h[2] - 0.4), s=1.3, bone="prop_l", hook=V(0, h[1] + 0.2, h[2] - 0.4), star=True)


def ledger(c):
    """組合の帳面: 革の表紙に角金と持ち主の色の紋、厚い紙の束、栞のリボン、留め帯、羽ペン"""
    h = hand(c, 1)
    R = K.rot_y(-15) @ K.rot_x(-12)
    cen = h + V(-1.4, 1.2, -0.2)
    F = part("cover", box("prop_l", "book", cen, (1.9, 2.5, 0.55), R=R))
    F += part("pages", box("prop_l", "paper", cen + R @ V(0.14, 0, 0), (1.76, 2.36, 0.47), R=R))
    F += part("spine", box("prop_l", "book2", cen + R @ V(-1.86, 0, 0), (0.26, 2.56, 0.62), R=R))
    for k, dy in enumerate((-1.6, 0, 1.6)):
        F += part(f"rib{k}", box("prop_l", "bronze3", cen + R @ V(-1.95, dy, 0), (0.1, 0.18, 0.64), R=R))
    for k, (sx, sy) in enumerate(((1, 1), (1, -1))):
        F += part(f"corner{k}", box("prop_l", "bronze3", cen + R @ V(1.65 * sx, 2.25 * sy, 0), (0.3, 0.3, 0.6), R=R))
    F += part("crest", disc("prop_l", owner(c, "band"), cen + R @ V(0.2, 0.3, -0.58), 0.75, 0.05, n=10, axis="z"))
    F += part("crest_in", disc("prop_l", "bronze3", cen + R @ V(0.2, 0.3, -0.62), 0.35, 0.04, n=6, axis="z"))
    F += part("strap", box("prop_l", "leather", cen + R @ V(1.0, -0.6, 0), (0.95, 0.2, 0.62), R=R))
    F += part("clasp", box("prop_l", "bronze3", cen + R @ V(1.92, -0.6, 0), (0.18, 0.32, 0.66), R=R))
    F += part("marker", tube("prop_l", owner(c, "ribbon"), [cen + R @ V(0.6, -2.3, 0.1), cen + R @ V(0.7, -3.2, 0.2), cen + R @ V(0.5, -3.9, 0.1)],
                             ra=0.2, rb=0.05, sides=4, caps=(True, True), hint=(0, 0, 1)))
    F += part("quill", spike("prop_l", "feather", cen + R @ V(1.0, 2.2, -0.7), (0.3, 1.0, -0.15), 3.4, 0.45, sides=4, rings=3, bend=(0.4, 0, 0)))
    F += part("quill_nib", spike("prop_l", "ink", cen + R @ V(1.0, 2.25, -0.7), (-0.3, -1.0, 0.15), 0.7, 0.12, sides=3, rings=1))
    return F


def hammer(c):
    """槌: 八角の柄に革巻き、面取りした頭と打ち面、楔、柄に持ち主の色の帯"""
    h = hand(c, -1)
    a, b = h + V(0, 1.6, 0.2), h + V(0, -5.6, -0.6)
    F = part("h_handle", rod("prop_r", "oak", a, b, 0.42, 0.36, sides=8))
    F += wrap_rod("prop_r", "leather", h + V(0, 1.2, 0.15), h + V(0, -1.4, -0.12), 0.48, n=4)
    F += part("h_band", rod("prop_r", owner(c, "band"), h + V(0, -2.0, -0.17), h + V(0, -2.5, -0.22), 0.46, sides=8))
    hd = h + V(0, -6.2, -0.65)
    F += part("h_head", disc("prop_r", "iron3", hd, 0.95, 1.6, n=8, axis="z"))
    for s in (-1, 1):
        F += part(f"h_face{s}", disc("prop_r", "steel", hd + V(0, 0, s * 1.85), 1.05, 0.25, n=8, axis="z", r_top=0.9))
        F += part(f"h_ring{s}", disc("prop_r", "iron3", hd + V(0, 0, s * 1.0), 1.02, 0.14, n=8, axis="z"))
    F += part("h_wedge", box("prop_r", "oak", hd + V(0, 0.98, 0), (0.25, 0.12, 0.5)))
    F += ribbon(c, "prop_r", h + V(0, -3.0, -0.3), d=(0.4, -1, 0), ln=1.2, w=0.25)
    return F


def flask(c):
    """薬瓶: 丸い硝子に光る薬、首と口の輪、栓に封蝋、首に麻紐、薬草の小枝、札"""
    h = hand(c, 1)
    p = h + V(0, 1.6, -0.3)
    F = part("f_body", ellipsoid("prop_l", "glass3", p, (1.1, 1.3, 1.1), segs=12, rings=5))
    F += part("f_liquid", ellipsoid("prop_l", "potion", p + V(0, -0.3, 0), (0.92, 0.85, 0.92), segs=10, rings=3))
    F += part("f_neck", disc("prop_l", "glass3", p + V(0, 1.55, 0), 0.42, 0.5, n=8))
    F += part("f_lip", disc("prop_l", "glass3", p + V(0, 2.05, 0), 0.55, 0.12, n=8))
    F += part("f_cork", disc("prop_l", "wood", p + V(0, 2.4, 0), 0.42, 0.32, n=8, r_top=0.48))
    F += part("f_wax", ellipsoid("prop_l", "wax", p + V(0, 2.75, 0), (0.55, 0.22, 0.55), segs=8, rings=2))
    F += part("f_twine", disc("prop_l", "rope", p + V(0, 1.35, 0), 0.5, 0.1, n=8))
    F += part("f_label", box("prop_l", "paper", p + V(0, -0.1, -1.08), (0.7, 0.55, 0.06)))
    F += part("f_label_mark", box("prop_l", owner(c, "ribbon"), p + V(0, 0.05, -1.14), (0.45, 0.12, 0.03)))
    F += part("f_sprig", tube("prop_l", "leaf", [p + V(0.25, 1.4, -0.3), p + V(0.8, 2.4, -0.6), p + V(1.1, 3.2, -0.5)], ra=[0.12, 0.1, 0.05], sides=4))
    for k in range(3):
        F += part(f"f_leaf{k}", ellipsoid("prop_l", "leaf2", (p[0] + 0.7 + k * 0.18, p[1] + 2.1 + k * 0.45, p[2] - 0.6), (0.32, 0.12, 0.16), segs=6, rings=1,
                                          rot=K.rot_z(-40)))
    return F


def bottle(c):
    """酒瓶: 琥珀の硝子の胴と肩、首、栓に垂れた封蝋、首の縄、札"""
    h = hand(c, -1)
    p = h + V(0, -1.7, -0.3)
    F = part("bt_body", disc("prop_r", "glassa", p, 0.98, 1.4, n=10))
    F += part("bt_base", disc("prop_r", "glassa", p + V(0, -1.45, 0), 0.92, 0.08, n=10))
    F += part("bt_shoulder", disc("prop_r", "glassa", p + V(0, 1.7, 0), 0.98, 0.32, n=10, r_top=0.44))
    F += part("bt_neck", disc("prop_r", "glassa", p + V(0, 2.5, 0), 0.38, 0.62, n=8))
    F += part("bt_lip", disc("prop_r", "glassa", p + V(0, 3.05, 0), 0.47, 0.1, n=8))
    F += part("bt_cork", disc("prop_r", "wood", p + V(0, 3.3, 0), 0.4, 0.22, n=8))
    F += part("bt_wax", disc("prop_r", "wax", p + V(0, 3.0, 0), 0.5, 0.3, n=8))
    F += part("bt_drip", spike("prop_r", "wax", p + V(0.4, 2.75, -0.2), (0.1, -1, -0.1), 0.6, 0.18, sides=4, rings=1))
    F += part("bt_rope", disc("prop_r", "rope", p + V(0, 2.25, 0), 0.47, 0.12, n=8))
    F += part("bt_label", box("prop_r", "paper", p + V(0, 0.1, -1.0), (0.62, 0.68, 0.05)))
    F += part("bt_label_mark", box("prop_r", owner(c, "ribbon"), p + V(0, 0.5, -1.06), (0.62, 0.12, 0.03)))
    return F


def crate(c):
    """木箱: 三枚の板、角の鉄、縄の取っ手、持ち主の色の焼き印"""
    hr, hl = hand(c, -1), hand(c, 1)
    cen = (hr + hl) / 2 + V(0, 0.4, -0.6)
    F = part("crate", box("prop_r", "oak", cen, (3.0, 2.0, 2.0)))
    for k, dy in enumerate((-1.3, 0.0, 1.3)):
        F += part(f"slat{k}", box("prop_r", "wood2", cen + V(0, dy, -2.03), (2.85, 0.55, 0.06)))
        F += part(f"slatb{k}", box("prop_r", "wood2", cen + V(0, dy, 2.03), (2.85, 0.55, 0.06)))
    for k, (sx, sz) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1))):
        F += part(f"edge{k}", box("prop_r", "iron3", cen + V(sx * 2.95, 0, sz * 1.95), (0.14, 2.05, 0.14)))
    for i, dy in enumerate((-1.9, 1.9)):
        for j, dz in enumerate((-2.04, 2.04)):
            F += part(f"crate_band{i}{j}", box("prop_r", "iron3", cen + V(0, dy, dz), (3.08, 0.16, 0.08)))
    for s in (-1, 1):
        F += part(f"handle{s}", tube("prop_r", "rope", [cen + V(s * 3.0, 0.6, -0.7), cen + V(s * 3.5, 0.3, 0), cen + V(s * 3.0, 0.6, 0.7)], ra=0.2, sides=4))
    F += part("brand", disc("prop_r", owner(c, "band"), cen + V(0.6, 0.1, -2.1), 0.6, 0.04, n=8, axis="z"))
    F += tag(c, "prop_r", cen + V(-1.7, 0.7, -2.15))
    return F


def tray(c):
    """盆: 縁のある丸盆、帯を締めた二つのジョッキ (泡・取っ手)、パン、持ち主の色の布巾"""
    h = hand(c, 1)
    top = h + V(-1.2, 0.7, -0.3)
    F = part("tray", disc("prop_l", "oak", top, 3.0, 0.2, n=14))
    F += part("tray_rim", disc("prop_l", "wood2", top + V(0, 0.32, 0), 3.05, 0.14, n=14, r_top=2.95))
    F += part("cloth", box("prop_l", owner(c, "cloth"), top + V(1.4, 0.26, 0.9), (1.0, 0.05, 0.9), R=K.rot_y(20)))
    for i, (dx, dz) in enumerate(((-1.2, -0.6), (1.1, 0.4))):
        cp = top + V(dx, 1.3, dz)
        F += part(f"mug{i}", disc("prop_l", "wood2", cp, 0.82, 1.0, n=10))
        for j, dy in enumerate((-0.6, 0.6)):
            F += part(f"mug_band{i}{j}", disc("prop_l", "iron3", cp + V(0, dy, 0), 0.86, 0.1, n=10))
        F += part(f"foam{i}", ellipsoid("prop_l", "foam", cp + V(0, 1.05, 0), (0.86, 0.42, 0.86), segs=10, rings=2))
        F += part(f"mug_h{i}", tube("prop_l", "wood2", [cp + V(0.78, 0.6, 0), cp + V(1.4, 0.4, 0), cp + V(1.4, -0.3, 0), cp + V(0.78, -0.5, 0)], ra=0.2, sides=4,
                                    caps=(True, True)))
    F += part("bread", ellipsoid("prop_l", "bread", (top[0] + 0.3, top[1] + 0.75, top[2] - 1.6), (1.15, 0.6, 0.72), segs=9, rings=3))
    for k in range(3):
        F += part(f"score{k}", box("prop_l", "bread2" if False else "paper2", (top[0] - 0.3 + k * 0.5, top[1] + 1.3, top[2] - 1.6), (0.08, 0.05, 0.4)))
    return F


def coin_pouch(c):
    """財布: 絞った天鵞絨の袋、口の紐と房、持ち主の色の刺繍、こぼれた硬貨"""
    h = hand(c, 1)
    p = h + V(0, -1.4, -0.2)
    F = part("cp_bag", ellipsoid("prop_l", "velvet", p, (1.35, 1.5, 1.2), segs=10, rings=4))
    F += part("cp_neck", disc("prop_l", "velvet", p + V(0, 1.55, 0), 0.5, 0.35, n=8, r_top=0.8))
    F += part("cp_frill", disc("prop_l", "velvet", p + V(0, 2.0, 0), 0.82, 0.14, n=8, r_top=0.95))
    F += part("cp_tie", disc("prop_l", "bronze3", p + V(0, 1.42, 0), 0.6, 0.14, n=8))
    F += part("cp_cord", tube("prop_l", "cord", [p + V(0.55, 1.4, 0), p + V(1.0, 0.6, -0.3), p + V(0.9, -0.3, -0.4)], ra=0.1, sides=4))
    F += part("cp_tassel", spike("prop_l", owner(c, "ribbon"), p + V(0.9, -0.3, -0.4), (0, -1, 0), 0.8, 0.22, sides=4, rings=1))
    F += part("cp_emb", disc("prop_l", owner(c, "band"), p + V(0, -0.1, -1.2), 0.5, 0.05, n=8, axis="z"))
    F += part("cp_emb2", disc("prop_l", "bronze3", p + V(0, -0.1, -1.25), 0.22, 0.04, n=6, axis="z"))
    for k, (dx, dy) in enumerate(((-0.4, 1.5), (0.3, 1.8))):
        F += part(f"cp_coin{k}", disc("prop_l", "bronze3", h + V(dx, dy, -1.1), 0.58, 0.1, n=10, axis="z"))
    return F


def scales(c):
    """天秤: 細工の柱と竿、上の珠、鎖の吊り、深い皿、片皿に硬貨、柱に持ち主のリボン"""
    h = hand(c, 1)
    top = h + V(0, 2.6, -0.2)
    F = part("sc_post", rod("prop_l", "bronze3", h + V(0, -0.4, -0.2), top, 0.22, 0.16, sides=6))
    F += part("sc_foot", disc("prop_l", "bronze3", h + V(0, -0.5, -0.2), 0.55, 0.14, n=8))
    F += part("sc_beam", tube("prop_l", "bronze3", [top + V(-2.6, -0.2, 0), top + V(0, 0.1, 0), top + V(2.6, -0.2, 0)], ra=0.16, sides=5))
    F += part("sc_knob", ellipsoid("prop_l", "bronze3", top + V(0, 0.45, 0), (0.35, 0.35, 0.35), segs=8, rings=2))
    for s in (-1, 1):
        q = top + V(s * 2.5, -0.2, 0)
        F += part(f"sc_end{s}", ellipsoid("prop_l", "bronze3", q, (0.2, 0.2, 0.2), segs=6, rings=1))
        for j, dz in enumerate((-0.5, 0.5)):
            F += part(f"sc_cord{s}{j}", rod("prop_l", "iron3", q, q + V(0, -2.3, dz), 0.06, sides=3))
        F += part(f"sc_pan{s}", disc("prop_l", "bronze3", q + V(0, -2.5, 0), 0.95, 0.2, n=12, r_top=1.15))
    F += part("sc_coin", disc("prop_l", "brass2", top + V(-2.5, -2.2, 0), 0.4, 0.14, n=8))
    F += ribbon(c, "prop_l", top + V(0, -0.6, -0.25), d=(0.2, -1, -0.1), ln=1.3, w=0.24)
    return F


def handbell(c):
    """手鐘: 握りと頭、真鍮の首、鐘の身、刻みの帯と六つの小さな星、内側と舌、持ち主のリボン"""
    h = hand(c, 1)
    F = part("hb_grip", rod("prop_l", "oak", h + V(0, 1.4, -0.2), h + V(0, -0.6, -0.2), 0.34, 0.38, sides=8))
    F += part("hb_knob", ellipsoid("prop_l", "oak", h + V(0, 1.7, -0.2), (0.48, 0.4, 0.48), segs=8, rings=2))
    b = h + V(0, -1.4, -0.2)
    F += part("hb_collar", disc("prop_l", "bronze3", b + V(0, 0.65, 0), 0.5, 0.18, n=10))
    F += part("hb_bell", loft("prop_l", "bronze3", [ringe(b[1] + 0.4, 0.5, -0.5, 0.5, n=12, cx=b[0], zc=b[2]), ringe(b[1] - 0.6, 1.0, -1.0, 1.0, n=12, cx=b[0], zc=b[2]),
                                                    ringe(b[1] - 1.6, 1.45, -1.45, 1.45, n=12, cx=b[0], zc=b[2]), ringe(b[1] - 2.2, 1.75, -1.75, 1.75, n=12, cx=b[0], zc=b[2])],
                                caps=("bronze3", "dark")))
    F += part("hb_lip", disc("prop_l", "brass2", b + V(0, -2.25, 0), 1.82, 0.14, n=12))
    F += part("hb_band", disc("prop_l", owner(c, "band"), b + V(0, -1.2, 0), 1.28, 0.12, n=12))
    for k in range(6):
        a = k / 6 * 2 * math.pi
        F += part(f"hb_star{k}", ellipsoid("prop_l", "brass2", (b[0] + math.cos(a) * 1.36, b[1] - 1.2, b[2] + math.sin(a) * 1.36), (0.16, 0.16, 0.16), segs=4,
                                           rings=1))
    F += part("hb_clap", ellipsoid("prop_l", "iron3", b + V(0, -2.35, 0), (0.32, 0.32, 0.32), segs=6, rings=2))
    F += ribbon(c, "prop_l", b + V(0.45, 0.7, -0.3), d=(0.5, -1, -0.3), ln=2.0, w=0.3)
    return F


def doll(c):
    """人形: 持ち主の服の色の服、前掛け、毛糸の髪とお下げ、顔 (目・頬)、手足、髪のリボン"""
    h = hand(c, 1)
    p = h + V(-0.4, 0.8, -0.6)
    cloth = owner(c, "cloth")
    F = part("dl_body", loft("prop_l", cloth, [ringe(p[1] - 1.2, 1.0, -0.8, 0.8, n=10, cx=p[0], zc=p[2]), ringe(p[1] + 0.2, 0.75, -0.6, 0.6, n=10, cx=p[0], zc=p[2]),
                                               ringe(p[1] + 0.9, 0.55, -0.45, 0.45, n=10, cx=p[0], zc=p[2])], caps=(cloth, cloth)))
    F += part("dl_apron", box("prop_l", "cloth3", p + V(0, -0.3, -0.82), (0.55, 0.75, 0.04)))
    F += part("dl_head", ellipsoid("prop_l", "babyskin", p + V(0, 1.8, 0), (0.8, 0.8, 0.75), segs=10, rings=4))
    F += part("dl_hair", ellipsoid("prop_l", "yarn", p + V(0, 2.15, 0.15), (0.88, 0.58, 0.82), segs=10, rings=3))
    F += part("dl_bow", ellipsoid("prop_l", owner(c, "ribbon"), p + V(0.55, 2.55, 0.1), (0.4, 0.25, 0.18), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"dl_braid{s}", tube("prop_l", "yarn", [p + V(s * 0.75, 2.0, 0.3), p + V(s * 0.95, 1.4, 0.35), p + V(s * 0.9, 0.9, 0.3)], ra=[0.22, 0.2, 0.14],
                                       sides=5, caps=(True, True)))
        F += part(f"dl_eye{s}", ellipsoid("prop_l", "ink", p + V(s * 0.3, 1.85, -0.72), (0.1, 0.13, 0.05), segs=4, rings=1))
        F += part(f"dl_cheek{s}", ellipsoid("prop_l", "wax", p + V(s * 0.45, 1.62, -0.66), (0.14, 0.08, 0.04), segs=4, rings=1))
        F += part(f"dl_arm{s}", tube("prop_l", cloth, [p + V(s * 0.65, 0.7, 0), p + V(s * 1.25, 0.1, -0.15), p + V(s * 1.45, -0.5, -0.25)], ra=[0.3, 0.27, 0.25],
                                     sides=5, caps=(True, True)))
        F += part(f"dl_hand{s}", ellipsoid("prop_l", "babyskin", p + V(s * 1.5, -0.7, -0.27), (0.22, 0.22, 0.22), segs=6, rings=1))
        F += part(f"dl_leg{s}", rod("prop_l", "cloth3", p + V(s * 0.35, -1.1, 0), p + V(s * 0.45, -2.3, -0.2), 0.27, sides=5))
        F += part(f"dl_shoe{s}", ellipsoid("prop_l", "leather", p + V(s * 0.45, -2.45, -0.3), (0.3, 0.2, 0.38), segs=6, rings=1))
    return F


def rope_coil(c):
    """綱の束: 五巻きの綱、二か所の結び、垂れた端と房、貝のお守り、持ち主のリボンの札"""
    h = hand(c, 1)
    F = []
    cen = h + V(0, -1.2, -0.4)
    for k in range(5):
        r = 1.8 - abs(k - 2) * 0.08
        ring_pts = [cen + V(0.2 * k - 0.4, r * math.sin(a), r * math.cos(a) * 0.9) for a in np.linspace(0, 2 * math.pi, 15)]
        F += part(f"rc{k}", tube("prop_l", "rope", ring_pts, 0.3, sides=5, caps=(False, False), hint=(1, 0, 0)))
    for j, a in enumerate((0.0, math.pi)):
        F += part(f"rc_tie{j}", disc("prop_l", "cord", cen + V(0, 1.8 * math.cos(a), 1.62 * math.sin(a) * 0), 0.75, 0.28, n=8, axis="x"))
    F += part("rc_tail", tube("prop_l", "rope", [cen + V(0.5, -1.6, -0.6), cen + V(0.8, -2.6, -0.8), cen + V(0.6, -3.4, -0.6)], ra=[0.3, 0.28, 0.24], sides=5))
    F += part("rc_whip", spike("prop_l", "paper", cen + V(0.6, -3.4, -0.6), (0, -1, 0), 0.6, 0.26, sides=5, rings=1))
    F += part("rc_shell", ellipsoid("prop_l", "shell3", cen + V(-0.4, 2.5, -0.2), (0.45, 0.38, 0.2), segs=8, rings=2))
    F += ribbon(c, "prop_l", cen + V(0.6, 1.8, -0.1), d=(0.4, -1, -0.4), ln=1.4, w=0.26)
    return F


def baby(c):
    """赤子: 持ち主の色の産着の包み (縁取り)、頭巾、寝顔"""
    hr, hl = hand(c, -1), hand(c, 1)
    cen = (hr + hl) / 2 + V(0, 1.2, -0.4)
    R = K.rot_z(-22)
    F = part("by_wrap", ellipsoid("prop_r", "fabric", cen, (2.6, 1.4, 1.4), segs=12, rings=4, rot=R))
    F += part("by_fold", ellipsoid("prop_r", owner(c, "ribbon"), cen + R @ V(-0.3, 0.2, -0.5), (1.9, 1.0, 1.0), segs=10, rings=3, rot=R))
    F += part("by_band", disc("prop_r", owner(c, "band"), cen + R @ V(-0.2, 0, 0), 1.45, 0.2, n=12, axis="x"))
    F += part("by_head", ellipsoid("prop_r", "babyskin", cen + R @ V(2.2, 0.4, -0.2), (0.95, 0.95, 0.9), segs=10, rings=4))
    F += part("by_hood", ellipsoid("prop_r", "fabric2", cen + R @ V(2.55, 0.5, 0.3), (1.15, 1.15, 0.9), segs=10, rings=3))
    for s in (-1, 1):
        F += part(f"by_eye{s}", box("prop_r", "ink", cen + R @ V(2.2 + s * 0.32, 0.55, -1.08), (0.18, 0.04, 0.03)))
    F += part("by_cheek", ellipsoid("prop_r", "wax", cen + R @ V(2.0, 0.2, -1.0), (0.2, 0.12, 0.05), segs=4, rings=1))
    return F


def bucket(c):
    """桶: 板目の胴、二本の鉄の箍、取っ手、灰の山、縁に持ち主の色の布"""
    h = hand(c, -1)
    b = h + V(0, -4.4, -0.3)
    F = part("bk_body", loft("prop_r", "oak", [ringe(b[1] - 1.6, 1.4, -1.4, 1.4, n=12, cx=b[0], zc=b[2]), ringe(b[1] + 1.6, 1.8, -1.8, 1.8, n=12, cx=b[0], zc=b[2])],
                             caps=("oak", "oak")))
    for i, dy in enumerate((-0.9, 0.9)):
        F += part(f"bk_hoop{i}", disc("prop_r", "iron3", b + V(0, dy, 0), 1.6 + dy * 0.12 + 0.1, 0.16, n=12))
    F += part("bk_ash", ellipsoid("prop_r", "ash", b + V(0, 1.7, 0), (1.6, 0.45, 1.6), segs=10, rings=2))
    F += part("bk_ember", ellipsoid("prop_r", "flame3", b + V(0.4, 1.95, -0.3), (0.3, 0.15, 0.3), segs=6, rings=1))
    F += part("bk_bail", tube("prop_r", "iron3", [b + V(-1.8, 1.5, 0), b + V(-0.8, 3.9, 0), h + V(0, 0.2, 0), b + V(0.8, 3.9, 0), b + V(1.8, 1.5, 0)],
                              ra=0.14, sides=4, caps=(True, True)))
    F += part("bk_rag", tube("prop_r", owner(c, "ribbon"), [b + V(1.7, 1.7, -0.6), b + V(2.1, 0.8, -0.8), b + V(2.0, -0.4, -0.6)], ra=[0.45, 0.4, 0.3], rb=0.08,
                             sides=4, caps=(True, True), hint=(0, 0, 1)))
    return F


def ore_lump(c):
    """鉱石: 角ばった母岩、光る晶の房、下に持ち主の色の布"""
    h = hand(c, 1)
    p = h + V(0, 2.0, -0.8)
    F = part("or_cloth", disc("prop_l", owner(c, "cloth"), p + V(0, -1.45, 0), 2.0, 0.1, n=10))
    F += part("or_rock", ellipsoid("prop_l", "ore", p, (1.9, 1.5, 1.7), segs=9, rings=4))
    for i, (dx, dy, dz, ln) in enumerate(((0.9, 0.8, -1.2, 1.4), (-0.8, 1.1, -0.9, 1.1), (0.3, -0.3, -1.6, 0.9), (-1.2, -0.2, -0.6, 0.8), (0.2, 1.4, 0.2, 1.2))):
        d = np.array([dx, dy + 0.6, dz], float)
        F += part(f"or_gem{i}", spike("prop_l", "glowgem", p + V(dx * 0.7, dy * 0.7, dz * 0.7), d, ln, 0.3, sides=6, rings=1, taper=0.9))
    return F


def sealed_box(c):
    """封の箱: 樫の胴と弓なりの蓋、真鍮の帯 (蓋を越える)、角金、錠と鍵穴、赤い封蝋と紐、持ち主の色の紋"""
    h = hand(c, 1)
    cen = h + V(-1.0, 1.5, -0.7)
    F = part("sb_body", box("prop_l", "oak", cen, (1.9, 1.05, 1.35)))
    lid = []
    for k in range(7):
        a = -math.pi / 2 + math.pi * k / 6
        lid.append(a)
    rings = []
    for x in (-1.95, 1.95):
        rings.append([cen + V(x, 1.05 + 0.75 * math.cos(a), 1.38 * math.sin(a)) for a in lid] + [cen + V(x, 1.05, 1.38)])
    F += part("sb_lid", loft("prop_l", "oak", rings, caps=("oak", "oak")))
    for i, dx in enumerate((-1.25, 1.25)):
        pts = [cen + V(dx, -1.05, -1.4)] + [cen + V(dx, 1.05 + 0.82 * math.cos(a), 1.45 * math.sin(a)) for a in lid] + [cen + V(dx, -1.05, 1.4)]
        F += part(f"sb_band{i}", tube("prop_l", "bronze3", pts, ra=0.16, rb=0.06, sides=4, hint=(1, 0, 0)))
    for k, (sx, sz) in enumerate(((-1, -1), (1, -1))):
        F += part(f"sb_corner{k}", box("prop_l", "bronze3", cen + V(sx * 1.85, -0.8, sz * 1.3), (0.15, 0.3, 0.15)))
    F += part("sb_lock", box("prop_l", "bronze3", cen + V(0, 0.8, -1.42), (0.34, 0.36, 0.08)))
    F += part("sb_hole", box("prop_l", "ink", cen + V(0, 0.75, -1.5), (0.07, 0.14, 0.02)))
    F += part("sb_cord", box("prop_l", "wax", cen + V(0, -0.1, -1.4), (0.08, 0.95, 0.05)))
    F += part("sb_seal", disc("prop_l", "wax", cen + V(0, -0.35, -1.48), 0.5, 0.14, n=10, axis="z"))
    F += part("sb_crest", disc("prop_l", owner(c, "band"), cen + V(1.2, 0.0, -1.38), 0.36, 0.04, n=8, axis="z"))
    return F


def tower_balance(c):
    """塔守の天秤: 金の柱と竿 (端に珠)、光る珠、金の皿と黒い石の皿、鎖、柱に持ち主のリボン"""
    h = hand(c, 1)
    top = h + V(0, -0.6, 0)
    F = part("tb_post", rod("prop_l", "metal", top, top + V(0, -3.2, 0), 0.16, 0.12, sides=6))
    F += part("tb_finial", spike("prop_l", "metal", top + V(0, -3.2, 0), (0, -1, 0), 0.5, 0.2, sides=6, rings=1))
    F += part("tb_beam", tube("prop_l", "metal", [top + V(-2.2, -1.0, 0), top + V(0, -0.8, 0), top + V(2.2, -1.0, 0)], ra=0.12, sides=5))
    F += part("tb_gem", ellipsoid("prop_l", "glowgem", top + V(0, -0.3, 0), (0.34, 0.34, 0.34), segs=8, rings=3))
    F += part("tb_cage", disc("prop_l", "metal", top + V(0, -0.65, 0), 0.3, 0.1, n=8))
    for i, (dx, m) in enumerate(((-2.0, "metal"), (2.0, "stone"))):
        F += part(f"tb_end{i}", ellipsoid("prop_l", "metal", top + V(dx * 1.1, -1.0, 0), (0.14, 0.14, 0.14), segs=6, rings=1))
        for j, dz in enumerate((-0.35, 0.35)):
            F += part(f"tb_cord{i}{j}", rod("prop_l", "metal", top + V(dx, -1.0, 0), top + V(dx, -2.5, dz), 0.04, sides=3))
        F += part(f"tb_pan{i}", disc("prop_l", m, top + V(dx, -2.65, 0), 0.85, 0.16, n=12, r_top=1.0))
    F += ribbon(c, "prop_l", top + V(0, -1.6, -0.2), d=(0.2, -1, -0.2), ln=1.0, w=0.2)
    return F


def hourglass_l(c):
    """月の砂時計: 銀の上下の板 (縁取り)、三本のねじれた柱、光る上の玉と砂の溜まった下の玉、鎖、持ち主のリボン"""
    h = hand(c, 1)
    cx, cy, cz = h[0], h[1] - 3.4, h[2] - 0.4
    F = []
    for s in (-1, 1):
        F += part(f"hg_plate{s}", disc("prop_l", "metal", V(cx, cy + s * 2.5, cz), 1.25, 0.3, n=10))
        F += part(f"hg_rim{s}", disc("prop_l", "metal2", V(cx, cy + s * 2.5, cz), 1.32, 0.1, n=10))
        F += part(f"hg_knob{s}", ellipsoid("prop_l", "metal", (cx, cy + s * 2.85, cz), (0.3, 0.2, 0.3), segs=6, rings=1))
    for i in range(3):
        a = math.radians(90 + i * 120)
        pts = [V(cx + math.cos(a + t * 0.6) * 1.0, cy - 2.3 + t * 4.6, cz + math.sin(a + t * 0.6) * 1.0) for t in np.linspace(0, 1, 5)]
        F += part(f"hg_post{i}", tube("prop_l", "metal2", pts, ra=0.13, sides=5))
    F += part("hg_up", ellipsoid("prop_l", "sandglass", (cx, cy + 1.15, cz), (0.75, 1.05, 0.75), segs=10, rings=4))
    F += part("hg_low", ellipsoid("prop_l", "sandglass", (cx, cy - 1.15, cz), (0.8, 1.05, 0.8), segs=10, rings=4))
    F += part("hg_sand", ellipsoid("prop_l", "dust", (cx, cy - 1.6, cz), (0.65, 0.5, 0.65), segs=8, rings=2))
    F += part("hg_hook", rod("prop_l", "cord", V(cx, cy + 2.9, cz), V(h[0], h[1] - 0.2, h[2] - 0.4), 0.1, sides=4))
    F += ribbon(c, "prop_l", V(cx + 0.6, cy + 2.7, cz), d=(0.4, -1, 0), ln=1.2, w=0.22)
    return F


def hand_mirror_l(c):
    """割れた手鏡: 銀の枠 (飾りの粒)、光る面、斜めの割れ、巻いた柄、柄に持ち主のリボン"""
    h = PR.hand(c, 1)
    cx, cy, cz = h[0] - 0.6, h[1] + 3.0, h[2] - 0.9
    F = part("mr_frame", disc("prop_l", "metal", V(cx, cy, cz), 2.1, 0.4, n=14, axis="z"))
    F += part("mr_glass", disc("prop_l", "mirror", V(cx, cy, cz - 0.3), 1.75, 0.18, n=14, axis="z"))
    for k in range(8):
        a = k / 8 * 2 * math.pi
        F += part(f"mr_bead{k}", ellipsoid("prop_l", "metal2", (cx + math.cos(a) * 2.1, cy + math.sin(a) * 2.1, cz - 0.3), (0.2, 0.2, 0.15), segs=6, rings=1))
    F += part("mr_crack", tube("prop_l", "trim", [V(cx - 1.2, cy + 1.0, cz - 0.5), V(cx - 0.2, cy + 0.1, cz - 0.5), V(cx + 0.1, cy - 0.3, cz - 0.5),
                                                 V(cx + 0.9, cy - 1.1, cz - 0.5)], ra=0.08, sides=3))
    F += part("mr_handle", rod("prop_l", "metal2", V(cx, cy - 2.0, cz), V(h[0], h[1] - 0.6, h[2] - 0.4), 0.3, 0.24, sides=6))
    F += wrap_rod("prop_l", "cord", V(cx, cy - 2.2, cz), V(h[0], h[1] - 0.2, h[2] - 0.4), 0.33, n=3)
    F += ribbon(c, "prop_l", V(cx * 0.5 + h[0] * 0.5, cy - 2.4, cz), d=(0.4, -1, -0.2), ln=1.2, w=0.22)
    return F


def sausages(c):
    """腸詰め: 紐の輪、五つの腸詰め (くびれた結び)、下に持ち主の色の札"""
    h = hand(c, 1)
    F = part("ss_loop", tube("prop_l", "sack2", [h + V(0, 0.6, 0.2), h + V(0.3, 0.9, -0.2), h + V(0.2, -0.6, -0.3)], ra=0.12, sides=4))
    p = h + V(0.2, -0.8, -0.3)
    for i in range(5):
        a = -0.35 + i * 0.18
        q = p + V(math.sin(a) * 0.7 * i, -1.55 * i - 0.9, -0.2 * i)
        F += part(f"ss{i}", ellipsoid("prop_l", "meat", q, (0.66, 0.9, 0.66), segs=10, rings=4))
        F += part(f"ss_tie{i}", disc("prop_l", "sack2", q + V(0, 0.96, 0), 0.24, 0.16, n=6))
    F += tag(c, "prop_l", p + V(0.9, -1.4, -0.6), s=0.9)
    return F


# ====================================================================== 前の形に足す
def _plus(fn, extra):
    def f(c):
        return fn(c) + extra(c)
    f.__name__ = fn.__name__
    return f


def _rib_at(sock, off, d=(0.3, -1, -0.2), ln=1.5, w=0.28, base="hand"):
    def ex(c):
        side = -1 if sock == "prop_r" else 1
        at = (hand(c, side) if base == "hand" else V(0, 0, 0)) + V(*off)
        return ribbon(c, sock, at, d=d, ln=ln, w=w)
    return ex


EXTRA = {
    # 杖・槍・銛・鋤など手で立てるもの: 握りの上の巻きと持ち主のリボン
    "bell_staff": lambda c: wrap_rod("prop_r", "leather", hand(c, -1) + V(0, 1.4, 0.0), hand(c, -1) + V(0, -1.4, -0.1), 0.48) + _rib_at("prop_r", (0, 11.8, 0.1))(c),
    "spear": lambda c: wrap_rod("prop_r", "leather", hand(c, -1) + V(0, 1.4, 0.0), hand(c, -1) + V(0, -1.4, -0.1), 0.46)
    + part("sp_flag2", box("prop_r", owner(c, "ribbon"), hand(c, -1) + V(0, 12.9, 1.45), (0.1, 1.1, 1.2)))
    + part("sp_bind", rod("prop_r", "cord", hand(c, -1) + V(0, 13.6, 0.1), hand(c, -1) + V(0, 14.4, 0.1), 0.48, sides=6)),
    "harpoon": lambda c: wrap_rod("prop_r", "rope", hand(c, -1) + V(0, 1.4, 0.0), hand(c, -1) + V(0, -1.4, -0.1), 0.44) + _rib_at("prop_r", (0, 9.6, 0.1))(c)
    + part("hp_float", ellipsoid("prop_r", "shell3", hand(c, -1) + V(0.7, 2.0, 0.4), (0.5, 0.6, 0.5), segs=8, rings=2)),
    "shovel": lambda c: wrap_rod("prop_r", "leather", hand(c, -1) + V(0, 1.2, 0.0), hand(c, -1) + V(0, -1.2, -0.1), 0.46) + tag(c, "prop_r", hand(c, -1) + V(0.6, -6.0, -0.9)),
    "fishing_rod": lambda c: wrap_rod("prop_r", "cord", hand(c, -1) + V(0, -2.2, 0.25), hand(c, -1) + V(0, 0.4, 0.1), 0.48)
    + part("fr_hook", spike("prop_r", "iron3", hand(c, -1) + V(0, 9.2, -6.4), (0, -1, 0.3), 0.7, 0.1, sides=3, rings=1))
    + _rib_at("prop_r", (0, 3.0, -0.2))(c),
    "guide_staff": lambda c: wrap_rod("prop_r", "leather", hand(c, -1) + V(0, 1.4, 0.0), hand(c, -1) + V(0, -1.4, -0.1), 0.46),
    "bokken": lambda c: part("bk_cord", tube("prop_r", owner(c, "ribbon"), [hand(c, -1) + V(0, 1.7, 0.4), hand(c, -1) + V(0.4, 1.2, 0.9),
                                                                           hand(c, -1) + V(0.5, -0.2, 1.1)], ra=0.12, sides=4))
    + part("bk_tsuba", disc("prop_r", "iron3", hand(c, -1) + V(0, -1.5, -0.35), 1.05, 0.12, n=10)),
    "toya_pick": lambda c: wrap_rod("prop_r", "leather", hand(c, -1) + V(-0.15, -2.4, -0.45), hand(c, -1) + V(0.1, 1.2, 1.1), 0.56) + _rib_at("prop_r", (1.0, 10.6, 5.6))(c),
    "hammer_": None,
    # 背の荷: 持ち主のリボンの札と留め金
    "tool_pack": lambda c: part("tp_buckle", box("prop_back", "bronze3", V(0, 19.6, 6.85), (0.5, 0.4, 0.1))) + tag(c, "prop_back", V(2.0, 16.0, 6.9)),
    "roku_sack": lambda c: tag(c, "prop_back", V(-2.6, 22.0, 13.0), s=1.3) + ribbon(c, "prop_back", V(0, 25.2, 7.2), d=(0.3, -1, -0.5), ln=2.0, w=0.4),
    "shizu_basket": lambda c: ribbon(c, "prop_back", V(0, 19.6, 3.0), d=(0.2, -1, -0.5), ln=2.2, w=0.4),
    "map_tube": lambda c: tag(c, "prop_back", V(0, 16.0, 5.1)) + part("mt_strap", tube("prop_back", "leather", [V(-3.4, 9.2, 3.0), V(-2.6, 21.6, -2.4),
                                                                                                                V(3.4, 23.0, 3.2)], ra=0.3, rb=0.1, sides=4, hint=(0, 0, 1))),
    "lute": lambda c: part("lt_strings", tube("prop_back", "cord", [V(-0.4, 14.6, 5.4), V(2.6, 21.6, 4.8)], ra=0.06, sides=3))
    + ribbon(c, "prop_back", V(2.8, 21.4, 4.6), d=(0.3, -1, 0.2), ln=1.8, w=0.32),
    "quiver": lambda c: tag(c, "prop_back", V(0.2, 15.4, 6.0), s=1.2),
    # 手に持つもの
    "hayase_tripod": lambda c: _rib_at("prop_r", (0, 1.0, -0.4))(c)
    + part("plumb", ellipsoid("prop_r", "bronze3", hand(c, -1) + V(0, -2.4, -0.4), (0.3, 0.45, 0.3), segs=6, rings=2))
    + part("plumb_line", rod("prop_r", "cord", hand(c, -1) + V(0, 1.0, -0.4), hand(c, -1) + V(0, -2.0, -0.4), 0.04, sides=3)),
    "hayase_board": lambda c: part("hb_lines", box("prop_l", "ink", hand(c, 1) + V(-1.6, 0.9, -0.9), (1.4, 0.03, 0.05), R=K.rot_x(-35) @ K.rot_y(-20)))
    + part("hb_pencil", rod("prop_l", "wood", hand(c, 1) + V(-0.4, 1.3, -1.8), hand(c, 1) + V(-2.6, 1.6, -0.9), 0.12, sides=6))
    + part("hb_strap", box("prop_l", owner(c, "band"), hand(c, 1) + V(-1.6, 0.5, -0.9), (2.5, 0.08, 0.3), R=K.rot_x(-35) @ K.rot_y(-20))),
    "bird": lambda c: part("bd_jess", tube("prop_l", "leather", [hand(c, 1) + V(0, 1.3, -0.2), hand(c, 1) + V(0.3, 0.2, -0.9), hand(c, 1) + V(0.2, -0.8, -1.0)],
                                           ra=0.1, sides=3))
    + part("bd_tassel", spike("prop_l", owner(c, "ribbon"), hand(c, 1) + V(0.9, -0.5, 0.0), (0.2, -1, 0), 1.2, 0.3, sides=4, rings=1)),
    "cloth_bolt": lambda c: part("cb_layer", tube("prop_l", owner(c, "cloth"), [hand(c, 1) + V(-2.5, 0.9, 0.55), hand(c, 1) + V(1.5, 0.9, -1.35)], ra=1.4, sides=10,
                                                  caps=(False, False)))
    + part("cb_pin", disc("prop_l", "wax", hand(c, 1) + V(0.4, 2.0, -0.2), 0.5, 0.25, n=8)),
    "herb_basket_arm": lambda c: part("hba_cloth", disc("prop_l", owner(c, "ribbon"), hand(c, 1) + V(-0.4, -0.65, -1.4), 2.15, 0.1, n=10)),
    "bread_basket": lambda c: part("bb_cloth2", box("prop_l", owner(c, "ribbon"), hand(c, 1) + V(-0.8, 1.6, -0.6), (2.5, 0.08, 1.7))),
    "map_rolls": lambda c: part("mr_seal", disc("prop_l", "wax", hand(c, 1) + V(-0.4, 0.3, -0.3), 0.32, 0.12, n=8, axis="z"))
    + _rib_at("prop_l", (-0.4, 0.4, -0.3))(c),
}
EXTRA.pop("hammer_")

REBUILT = {f.__name__: f for f in (mio_lantern, lantern_l, star_lantern_l, ledger, hammer, flask, bottle, crate, tray, coin_pouch, scales, handbell, doll, rope_coil,
                                   baby, bucket, ore_lump, sealed_box, tower_balance, hourglass_l, hand_mirror_l, sausages)}


def new_fn(fn):
    """前の関数 fn に対応する v3 の関数"""
    n = fn.__name__
    if n in REBUILT:
        return REBUILT[n]
    if n in EXTRA:
        return _plus(fn, EXTRA[n])
    return None


def patch_lantern():
    """前の props.lantern を使う関数 (案内人の杖) も新しい角灯になるように"""
    PR.lantern = lantern
