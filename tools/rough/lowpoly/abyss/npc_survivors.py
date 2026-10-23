"""探掘隊の生き残り (灯り番ミオ・測量士ハヤセ・薬師シズ・隊長トウヤ) と荷運びロクのラフ 3 案ずつ。
都の係の人 (tools/rough/lowpoly/fac/attendants.py の骨 7 本・同じピボット・同じ顔の描き方) の作りを借りて、役の道具を足す。
体の 3 案: A 上着と前掛け / B 長い衣と帯 / C 短い上着と肩掛け (係の人の 3 案と同じ)。案ごとに持ち物の形も替える。
  python -I tools/rough/lowpoly/abyss/npc_survivors.py [mio hayase shizu toya roku]
  -> docs/review/roughs_abyss/npc_<名>.png と npc_<名>/{a,b,c}/ (メッシュ・アトラス)
座標: 1 単位 = 1/16 ブロック、前 = -Z、y が上。人の背丈は約 29 単位。
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "fac"))
import attendants as AT  # noqa: E402

CK = AT.CK
part, mat, ring, loft, tube, ellipsoid = AT.part, AT.mat, AT.ring, AT.loft, AT.tube, AT.ellipsoid
ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/roughs_abyss"
BONES = AT.BONES
voxel = CK.voxel

# ---- 役の色 (生き残りは灰と煤をかぶった色)
AT.PAL.update({
    "mio": dict(coat=(104, 90, 74), role=(170, 132, 60), trim=(60, 44, 30), acc=(206, 170, 96), hair=(86, 62, 44), skin="pale", guard=(92, 74, 56), sash=(120, 52, 40), lady=True),
    "hayase": dict(coat=(66, 88, 98), role=(190, 168, 120), trim=(40, 52, 76), acc=(186, 146, 70), hair=(150, 96, 56), skin="mid", guard=(70, 96, 100), sash=(150, 70, 50)),
    "shizu": dict(coat=(86, 108, 98), role=(196, 200, 172), trim=(60, 86, 52), acc=(120, 150, 96), hair=(60, 56, 66), skin="pale", guard=(84, 100, 76), sash=(70, 104, 70), lady=True),
    "toya": dict(coat=(74, 70, 68), role=(124, 40, 38), trim=(150, 154, 160), acc=(170, 174, 180), hair=(110, 104, 100), skin="tan", guard=(84, 82, 84), sash=(90, 30, 30)),
    "roku": dict(coat=(98, 90, 66), role=(168, 148, 108), trim=(92, 64, 40), acc=(178, 142, 70), hair=(104, 74, 48), skin="brown", guard=(110, 82, 54), sash=(120, 90, 50)),
})
EXTRA = {
    "wood": mat([(24, 16, 12), (56, 40, 28), (92, 66, 44), (128, 96, 62)], 0.62, crack=0.06, noise=0.14),
    "iron": mat([(14, 14, 18), (34, 34, 40), (62, 62, 70), (96, 98, 108)], 0.6, crack=0.0, noise=0.1),
    "brass": mat([(70, 48, 16), (130, 94, 30), (186, 146, 62), (226, 192, 108)], 0.66, crack=0.0, noise=0.06),
    "paper": mat([(110, 98, 74), (160, 146, 112), (200, 188, 150), (226, 216, 182)], 0.66, crack=0.0, noise=0.1),
    "glass": mat([(60, 74, 80), (110, 130, 134), (170, 190, 190)], 0.6, crack=0.0),
    "flame": mat([(150, 56, 14), (230, 120, 28), (255, 188, 70), (255, 232, 150)], 0.8, crack=0.0, noise=0.05, min=2),
    "leaf": mat([(22, 40, 24), (44, 76, 40), (84, 120, 60), (130, 160, 92)], 0.62, crack=0.0, noise=0.12),
    "red": mat([(70, 14, 14), (130, 28, 26), (180, 50, 42)], 0.66, crack=0.0),
    "glassv": mat([(70, 110, 96), (120, 170, 150), (190, 226, 210)], 0.6, crack=0.0),
    "cloth2": mat([(60, 56, 50), (96, 90, 80), (140, 130, 114)], 0.6, crack=0.05, noise=0.18),
}


def pal_npc(role):
    p = AT.pal_for(role)
    p["mats"].update(EXTRA)
    p["glow_mats"] = ["flame"]
    return p


# ---- 形の部品
def sqr(y, hx, hz, cx=0.0, cz=0.0):
    return [np.array(p, float) for p in ((cx - hx, y, cz - hz), (cx + hx, y, cz - hz), (cx + hx, y, cz + hz), (cx - hx, y, cz + hz))]


def box(bone, m, cx, cy, cz, hx, hy, hz, taper=1.0):
    return loft(bone, m, [sqr(cy - hy, hx, hz, cx, cz), sqr(cy + hy, hx * taper, hz * taper, cx, cz)], caps=(m, m))


def rodp(bone, m, a, b, r=0.35, sides=4, r1=None):
    return tube(bone, m, [a, b], ra=[r, r if r1 is None else r1], sides=sides, caps=(True, True))


def lamp(F, bone, x, y, z, s=1.0, tag="lamp"):
    """角灯 (台 + 笠 + 炎 + 四本の枠)。y は炎の中心。"""
    F += part(f"{tag}_base", box(bone, "iron", x, y - 1.5 * s, z, 0.9 * s, 0.35 * s, 0.9 * s))
    F += part(f"{tag}_fire", ellipsoid(bone, "flame", (x, y, z), (0.55 * s, 0.9 * s, 0.55 * s), segs=6, rings=2))
    F += part(f"{tag}_cap", box(bone, "iron", x, y + 1.7 * s, z, 1.0 * s, 0.3 * s, 1.0 * s, taper=0.55))
    for i, (dx, dz) in enumerate(((-0.8, -0.8), (0.8, -0.8), (0.8, 0.8), (-0.8, 0.8))):
        F += part(f"{tag}_bar{i}", rodp(bone, "iron", (x + dx * s, y - 1.3 * s, z + dz * s), (x + dx * s * 0.9, y + 1.5 * s, z + dz * s * 0.9), 0.14 * s, 3))
    F += part(f"{tag}_ring", rodp(bone, "iron", (x, y + 2.0 * s, z), (x, y + 3.4 * s, z), 0.18 * s, 3))


def arms_for(look, right=None, left=None):
    """look に合う腕を作る。right / left = (肩, 肘, 手首) の座標 (省略は下げた腕)。bone armR は -x、armL は +x。"""
    kw = {"a": dict(cuff="trim"), "b": dict(up=1.7, fore=1.55, hand=1.15, m_up="role", m_fore="role", cuff="trim"),
          "c": dict(up=1.55, fore=1.4, hand=1.2, cuff="trim")}[look]
    F = []
    for side, pose in ((-1, right), (1, left)):
        extra = {}
        if pose:
            extra = dict(sh=pose[0], el=pose[1], wr=pose[2])
        F += AT.arm(side, **kw, **extra)
    return F


def body(look, right=None, left=None):
    F = AT.LOOKS[look]()
    F = [f for f in F if not getattr(f, "part", "").startswith(("armL", "armR"))]
    return F + arms_for(look, right, left)


HR = (-5.1, 11.6, -2.8)   # 右の手 (下げた腕) の位置。手の中心
HL = (5.1, 11.6, -2.8)


# ====================================================================== 灯り番ミオ
def mio(k):
    if k == "a":
        # 上着と前掛け: 右手に灯りの鉤の杖 (先に角灯)。腰に予備の灯りと油の小瓶。
        F = body("a", right=((-4.8, 20.6, 0), (-5.4, 16.0, -1.4), (-5.2, 12.4, -3.2)))
        F += part("pole", rodp("armR", "wood", (-5.2, 1.0, -3.0), (-5.2, 29.0, -3.0), 0.4, 5))
        F += part("hook", tube("armR", "wood", [(-5.2, 29.0, -3.0), (-5.2, 31.2, -3.0), (-7.4, 31.4, -3.0), (-8.2, 30.0, -3.0)], ra=[0.4, 0.38, 0.34, 0.3], sides=4, caps=(True, True)))
        lamp(F, "armR", -8.2, 27.3, -3.0, s=1.25, tag="pl")
        lamp(F, "body", 4.8, 8.6, -3.4, s=0.7, tag="belt")
        F += part("flask", ellipsoid("body", "glassv", (-4.9, 9.2, -3.0), (0.9, 1.4, 0.9), segs=6, rings=2))
        F += part("flask_cork", ellipsoid("body", "wood", (-4.9, 10.8, -3.0), (0.4, 0.4, 0.4), segs=4, rings=1))
        return F
    if k == "b":
        # 長い衣: 両手で胸の高さに大きな角灯を抱える。首に煤けた布。
        F = body("b", right=((-4.8, 20.6, 0), (-5.6, 16.0, -1.8), (-2.4, 16.0, -6.2)), left=((4.8, 20.6, 0), (5.6, 16.0, -1.8), (2.4, 16.0, -6.2)))
        lamp(F, "armR", 0.0, 16.6, -7.4, s=2.0, tag="big")
        F += part("scarf", loft("body", "role", [ring(21.6, 4.8, -2.9, 2.7), ring(23.2, 4.4, -2.8, 2.6)], caps=("role", "role")))
        F += part("scarf_tail", rodp("body", "role", (-2.6, 21.6, -3.0), (-3.0, 15.0, -3.8), 0.9, 4))
        return F
    # C 短い上着と肩掛け: 背に灯りの枠 (三つの角灯)。右手に小さな合図の灯り。
    F = body("c", right=((-4.8, 20.6, 0), (-5.4, 16.0, -1.4), (-5.2, 12.8, -3.4)))
    for x in (-2.6, 2.6):
        F += part(f"frame{x}", rodp("body", "wood", (x, 7.0, 4.0), (x, 29.0, 4.4), 0.4, 4))
    for i, y in enumerate((11.0, 17.0, 23.0)):
        F += part(f"cross{i}", rodp("body", "wood", (-3.0, y, 4.1), (3.0, y, 4.1), 0.34, 4))
    for i, y in enumerate((26.5, 20.5, 14.5)):
        F += part(f"hk{i}", rodp("body", "iron", (0.0, y + 2.9, 5.0), (0.0, y + 3.5, 5.0), 0.2, 3)) if False else []
        lamp(F, "body", 0.0, y, 5.8, s=1.0, tag=f"bk{i}")
    F += part("stick", rodp("armR", "wood", (-5.2, 9.0, -3.4), (-5.2, 16.0, -3.4), 0.34, 4))
    lamp(F, "armR", -5.2, 18.8, -3.4, s=0.8, tag="sig")
    return F


# ====================================================================== 測量士ハヤセ
def hayase(k):
    if k == "a":
        # 上着と前掛け: 右手に目盛りの杖 (ひとの背丈より高い)。腰に丸めた図を三本。胸に方位盤。
        F = body("a", right=((-4.8, 20.6, 0), (-5.4, 16.0, -1.4), (-5.2, 12.4, -3.2)))
        F += part("staff", rodp("armR", "wood", (-5.2, 0.5, -3.0), (-5.2, 30.0, -3.0), 0.42, 5))
        for i, y in enumerate((5.0, 10.0, 15.0, 20.0, 25.0)):
            F += part(f"notch{i}", loft("armR", "role" if i % 2 else "trim", [ring(y, 0.62, -0.62, 0.62, n=6, cx=-5.2, zc=-3.0), ring(y + 1.4, 0.62, -0.62, 0.62, n=6, cx=-5.2, zc=-3.0)], caps=("role", "role"))) if False else \
                part(f"notch{i}", loft("armR", "role" if i % 2 else "trim", [[np.array((-5.2 + 0.62 * np.cos(a), y, -3.0 + 0.62 * np.sin(a))) for a in np.linspace(0, 2 * np.pi, 6, endpoint=False)], [np.array((-5.2 + 0.62 * np.cos(a), y + 1.3, -3.0 + 0.62 * np.sin(a))) for a in np.linspace(0, 2 * np.pi, 6, endpoint=False)]], caps=("role", "role")))
        for i, (x, tilt) in enumerate(((-2.4, 0.0), (0.0, 0.5), (2.4, 1.0))):
            F += part(f"map{i}", rodp("body", "paper", (x, 8.0, 3.6), (x + tilt * 0.5, 17.0, 4.0 + tilt * 0.4), 0.95, 6))
            F += part(f"mapb{i}", rodp("body", "trim", (x, 12.0, 3.62), (x, 13.0, 3.64), 1.02, 6)) if False else []
        F += part("compass", ellipsoid("body", "brass", (2.2, 18.2, -3.9), (1.5, 1.5, 0.45), segs=8, rings=1))
        F += part("compass_n", ellipsoid("body", "red", (2.2, 18.2, -4.3), (0.2, 0.8, 0.12), segs=4, rings=1))
        return F
    if k == "b":
        # 長い衣: 三脚に据えた測量器 (右手で地に立てて支える)。左手に画板。
        F = body("b", right=((-4.8, 20.6, 0), (-6.0, 16.4, -2.0), (-5.6, 13.6, -4.0)), left=((4.8, 20.6, 0), (5.8, 16.0, -2.2), (3.6, 14.6, -5.4)))
        top = (-5.6, 18.0, -4.6)
        for i, (dx, dz) in enumerate(((-3.0, -3.0), (3.0, -3.0), (0.0, 3.4))):
            F += part(f"leg{i}", rodp("armR", "wood", top, (top[0] + dx * 1.7, 0.4, top[2] + dz * 1.7), 0.45, 4, r1=0.35))
        F += part("plate", box("armR", "iron", top[0], top[1] + 0.4, top[2], 1.5, 0.45, 1.5))
        F += part("scope", rodp("armR", "brass", (top[0], top[1] + 2.4, top[2] + 1.8), (top[0], top[1] + 2.6, top[2] - 3.4), 0.95, 6))
        F += part("scope_lens", ellipsoid("armR", "glass", (top[0], top[1] + 2.6, top[2] - 3.6), (0.95, 0.95, 0.4), segs=6, rings=1))
        F += part("scope_post", rodp("armR", "iron", (top[0], top[1] + 0.8, top[2]), (top[0], top[1] + 2.2, top[2]), 0.5, 4))
        F += part("board", box("armL", "wood", 3.6, 14.6, -6.2, 2.2, 3.0, 0.3))
        F += part("board_paper", box("armL", "paper", 3.6, 14.6, -6.6, 1.8, 2.6, 0.12))
        return F
    # C 短い上着と肩掛け: 背に図筒を斜めに背負い、腰に測りの縄の巻き枠。左手に磁石の板。
    F = body("c", right=((-4.8, 20.6, 0), (-5.4, 16.0, -1.4), (-5.2, 12.4, -3.2)))
    F += part("tube", rodp("body", "wood", (-3.4, 6.0, 4.4), (3.8, 28.6, 4.6), 1.3, 6))
    F += part("tube_cap1", ellipsoid("body", "brass", (3.8, 29.0, 4.6), (1.5, 0.6, 1.5), segs=6, rings=1))
    F += part("tube_cap0", ellipsoid("body", "brass", (-3.4, 5.6, 4.4), (1.5, 0.6, 1.5), segs=6, rings=1))
    F += part("strap", rodp("body", "leather", (-3.4, 11.0, 4.0), (3.8, 23.0, 4.0), 0.3, 4)) if False else []
    F += part("reel", rodp("body", "wood", (4.4, 10.0, -3.4), (4.4, 10.0, -1.4), 1.9, 8))
    F += part("reel_cord", rodp("body", "paper", (4.4, 10.0, -3.5), (4.4, 10.0, -1.5), 1.55, 8))
    F += part("pin", rodp("body", "iron", (4.4, 10.0, -3.8), (4.4, 10.0, -4.6), 0.4, 4))
    F += part("rodz", rodp("armL", "iron", (5.2, 8.4, -3.0), (5.2, 12.0, -3.0), 0.3, 4)) if False else []
    return F


# ====================================================================== 薬師シズ
def vials(F, bone, pts, tag):
    for i, (x, y, z) in enumerate(pts):
        F += part(f"{tag}v{i}", ellipsoid(bone, "glassv", (x, y, z), (0.55, 0.95, 0.55), segs=5, rings=2))
        F += part(f"{tag}c{i}", ellipsoid(bone, "wood", (x, y + 1.0, z), (0.3, 0.3, 0.3), segs=4, rings=1))


def sprays(F, bone, base, tag, n=5, ln=5.0):
    for i in range(n):
        a = (i - (n - 1) / 2) * 0.28
        d = (np.sin(a), 1.0, -0.25 + 0.1 * np.cos(i))
        F += part(f"{tag}{i}", CK.tube(bone, "leaf", [base, (base[0] + d[0] * ln * 0.5, base[1] + ln * 0.5, base[2] + d[2] * ln * 0.5), (base[0] + d[0] * ln, base[1] + ln, base[2] + d[2] * ln)], ra=[0.28, 0.4, 0.0], sides=4, caps=(True, False)))


def shizu(k):
    if k == "a":
        # 上着と前掛け: 薬瓶の弾帯を斜めに掛け、右手に薬草の束。
        F = body("a", right=((-4.8, 20.6, 0), (-5.4, 16.0, -1.6), (-4.8, 13.6, -4.2)))
        F += part("bandolier", rodp("body", "leather", (-4.4, 22.0, -3.2), (4.2, 12.8, -3.9), 0.5, 4)) if False else \
            part("bandolier", tube("body", "leather", [(-4.2, 21.8, -3.3), (0.0, 17.6, -3.9), (4.2, 13.0, -3.7)], ra=[0.5, 0.5, 0.5], sides=4, caps=(True, True)))
        vials(F, "body", [(-3.0, 19.6, -4.2), (-1.4, 18.0, -4.5), (0.2, 16.6, -4.5), (1.8, 15.2, -4.4), (3.2, 13.8, -4.2)], "bv")
        F += part("herb_tie", ellipsoid("armR", "cord", (-4.8, 14.2, -4.2), (0.9, 0.8, 0.9), segs=5, rings=1))
        sprays(F, "armR", (-4.8, 14.4, -4.2), "hb", n=6, ln=5.0)
        return F
    if k == "b":
        # 長い衣: 背に大きな薬草籠 (草が飛び出す)。広いつばの帽子。
        F = body("b")
        F += part("basket", loft("body", "wood", [ring(7.0, 3.6, -2.8, 3.0, zc=5.4, n=8), ring(14.0, 5.0, -3.8, 3.8, zc=6.0, n=8), ring(20.0, 5.4, -4.2, 4.2, zc=6.2, n=8)], caps=("wood", "wood")))
        F += part("basket_rim", loft("body", "leather", [ring(19.6, 5.5, -4.3, 4.3, zc=6.2, n=8), ring(20.8, 5.5, -4.3, 4.3, zc=6.2, n=8)], caps=("leather", "leather")))
        for i, x in enumerate((-2.5, 0.0, 2.5)):
            sprays(F, "body", (x, 20.4, 6.0), f"bs{i}", n=2, ln=6.0 + i)
        F += part("hat", ellipsoid("head", "role", (0, 30.0, -0.2), (6.6, 1.0, 6.6), segs=10, rings=1))
        F += part("hat_top", ellipsoid("head", "role", (0, 31.0, -0.2), (3.2, 1.6, 3.2), segs=8, rings=2))
        return F
    # C 短い上着と肩掛け: 両手で薬箱を胸に抱える。腰にすり鉢。
    F = body("c", right=((-4.8, 20.6, 0), (-5.6, 16.0, -1.8), (-3.0, 14.0, -6.0)), left=((4.8, 20.6, 0), (5.6, 16.0, -1.8), (3.0, 14.0, -6.0)))
    F += part("chest", box("armR", "wood", 0.0, 14.4, -7.0, 4.2, 2.6, 2.4))
    F += part("chest_lid", box("armR", "leather", 0.0, 17.4, -7.0, 4.4, 0.45, 2.6))
    F += part("chest_band", box("armR", "iron", 0.0, 14.4, -9.3, 0.55, 2.7, 0.2))
    F += part("chest_x", box("armR", "red", 0.0, 14.4, -9.55, 1.6, 0.4, 0.1))
    F += part("chest_x2", box("armR", "red", 0.0, 14.4, -9.55, 0.4, 1.6, 0.1))
    F += part("mortar", ellipsoid("body", "iron", (4.6, 9.0, -2.6), (1.5, 1.2, 1.5), segs=7, rings=2))
    F += part("pestle", rodp("body", "wood", (4.6, 9.4, -2.6), (5.4, 12.0, -3.0), 0.4, 4))
    return F


# ====================================================================== 隊長トウヤ
def toya(k):
    if k == "a":
        # 上着と前掛け: ツルハシを肩にかつぐ。胸に縄の巻き、左肩に鉄の肩当て。
        F = body("a", right=((-4.8, 20.6, 0), (-6.4, 17.6, -1.2), (-4.4, 22.0, -3.0)))
        F += part("handle", rodp("armR", "wood", (-5.0, 12.0, -3.4), (-3.0, 33.0, 2.6), 0.5, 5)) if False else \
            part("handle", rodp("armR", "wood", (-4.4, 15.6, -3.0), (-2.6, 31.0, 1.6), 0.5, 5))
        F += part("pick_head", rodp("armR", "iron", (-8.6, 31.4, 1.8), (3.2, 30.4, 1.2), 0.9, 5))
        F += part("pick_a", CK.tube("armR", "iron", [(-8.6, 31.4, 1.8), (-11.8, 30.4, 1.9)], ra=[0.9, 0.0], sides=5, caps=(True, False)))
        F += part("pick_b", CK.tube("armR", "iron", [(3.2, 30.4, 1.2), (6.4, 29.4, 1.0)], ra=[0.9, 0.0], sides=5, caps=(True, False)))
        pts = []
        for j in range(9):
            a = 2 * np.pi * j / 8
            pts.append(np.array((5.2 * np.sin(a) * 0.95, 18.0 + 2.0 * np.cos(a), -3.9 * np.cos(a * 0.0 + 0.0) + 0.0 * a)))
        F += part("rope", tube("body", "cord", [(-3.6, 22.0, -3.2), (0.0, 17.4, -4.2), (3.6, 13.0, -3.8)], ra=[0.8, 0.8, 0.8], sides=5, caps=(True, True)))
        F += part("rope2", tube("body", "cord", [(-3.2, 21.4, -3.3), (0.4, 17.0, -4.3), (4.0, 13.4, -3.9)], ra=[0.8, 0.8, 0.8], sides=5, caps=(True, True)))
        F += part("pauldron", ellipsoid("armL", "iron", (4.9, 21.8, 0.0), (2.6, 1.8, 2.8), segs=7, rings=2))
        return F
    if k == "b":
        # 長い衣: 背に丸い盾。腰に短剣。肩から裂けたマントが垂れる。
        F = body("b")
        F += part("shield", ellipsoid("body", "iron", (0.0, 16.0, 4.0), (5.6, 5.6, 0.9), segs=10, rings=3))
        F += part("shield_boss", ellipsoid("body", "brass", (0.0, 16.0, 4.8), (1.3, 1.3, 0.7), segs=6, rings=2))
        F += part("shield_rim", loft("body", "trim", [[np.array((5.8 * np.cos(a), 16.0 + 5.8 * np.sin(a), 3.7)) for a in np.linspace(0, 2 * np.pi, 8, endpoint=False)], [np.array((5.8 * np.cos(a), 16.0 + 5.8 * np.sin(a), 4.3)) for a in np.linspace(0, 2 * np.pi, 8, endpoint=False)]], caps=("trim", "trim")))
        F += part("cape", loft("body", "cloth2", [ring(22.8, 5.4, -1.4, 3.6, n=8), ring(14.0, 5.8, -0.8, 5.0, n=8), ring(5.0, 6.0, -0.4, 6.2, n=8)], caps=("cloth2", "cloth2"))) if False else \
            part("cape", loft("body", "cloth2", [sqr(22.8, 5.6, 0.6, 0, 3.4), sqr(14.0, 6.0, 0.7, 0, 4.4), sqr(5.5, 6.2, 0.7, 0, 5.4)], caps=("cloth2", "cloth2")))
        F += part("sheath", rodp("body", "leather", (-5.4, 9.4, -1.6), (-5.9, 0.8, -3.6), 0.7, 4))
        F += part("hilt", rodp("body", "iron", (-5.4, 9.6, -1.6), (-5.2, 12.0, -1.2), 0.45, 4))
        F += part("guard", box("body", "brass", -5.2, 9.7, -1.6, 1.4, 0.35, 0.4))
        return F
    # C 短い上着と肩掛け: 隊の旗 (裂けた旗) を肩にかつぎ、旗竿の先に灯り。腰に槌。
    F = body("c", right=((-4.8, 20.6, 0), (-5.4, 16.0, -1.4), (-5.2, 12.4, -3.2)))
    F += part("flagpole", rodp("armR", "wood", (-5.2, 0.6, -3.0), (-5.2, 34.0, -3.0), 0.42, 5))
    F += part("flag", loft("armR", "role", [[np.array((-5.2, 33.0, -3.0)), np.array((-5.2, 33.0, -3.0 + 0.5)), np.array((-5.2, 27.6, -3.0 + 0.5)), np.array((-5.2, 27.6, -3.0))],
                                            [np.array((-5.2 - 0.0, 33.0, -3.0)), np.array((-5.2, 33.0, -3.0 + 0.5)), np.array((-5.2, 27.6, -3.0 + 0.5)), np.array((-5.2, 27.6, -3.0))]], caps=("role", "role"))) if False else \
        part("flag", box("armR", "role", -5.2, 30.4, 1.6, 0.25, 2.8, 4.6)) + part("flag_cut", box("armR", "trim", -5.2, 27.2, 3.4, 0.28, 0.5, 1.6))
    lamp(F, "armR", -5.2, 36.6, -3.0, s=1.0, tag="top") if False else None
    F += part("hammer", rodp("body", "wood", (5.2, 5.0, -2.0), (5.4, 10.0, -2.6), 0.45, 4))
    F += part("hammer_head", box("body", "iron", 5.3, 4.2, -2.0, 1.4, 1.0, 1.0))
    return F


# ====================================================================== 荷運びロク
def roku(k):
    if k == "a":
        # 上着と前掛け: 背に高い背負い枠。木箱が三段、縄と鍋。
        F = body("a")
        for x in (-3.0, 3.0):
            F += part(f"fr{x}", rodp("body", "wood", (x, 6.0, 4.2), (x, 33.0, 4.6), 0.5, 4))
        for i, (y, w, hz) in enumerate(((10.4, 3.6, 2.4), (17.4, 3.2, 2.2), (24.0, 2.8, 2.0))):
            F += part(f"crate{i}", box("body", "wood" if i % 2 == 0 else "leather", 0.2 * (i - 1), y, 7.0, w, 2.8, hz))
            F += part(f"crate_b{i}", box("body", "iron", 0.2 * (i - 1), y, 7.0 + hz + 0.1, w + 0.1, 0.35, 0.12))
        F += part("coil", loft("body", "cord", [[np.array((4.6 + 1.8 * np.cos(a), 29.8, 5.8 + 1.8 * np.sin(a))) for a in np.linspace(0, 2 * np.pi, 8, endpoint=False)], [np.array((4.6 + 1.8 * np.cos(a), 31.4, 5.8 + 1.8 * np.sin(a))) for a in np.linspace(0, 2 * np.pi, 8, endpoint=False)]], caps=("cord", "cord")))
        F += part("pot", ellipsoid("body", "iron", (-4.4, 28.6, 6.0), (1.9, 1.7, 1.9), segs=7, rings=2))
        return F
    if k == "b":
        # 長い衣: 天秤棒を肩に。両端に籠と包み。
        F = body("b")
        F += part("yoke", rodp("body", "wood", (-13.0, 23.2, 0.4), (13.0, 23.2, 0.4), 0.8, 5))
        for sd in (-1, 1):
            x = sd * 12.4
            F += part(f"rope{sd}", rodp("body", "cord", (x, 23.2, 0.4), (x, 14.6, 0.4), 0.28, 3))
            F += part(f"rope2{sd}", rodp("body", "cord", (x - sd * 2.2, 15.4, 0.4), (x, 23.2, 0.4), 0.22, 3))
        F += part("basket", loft("body", "wood", [[np.array((-12.4 + 3.2 * np.cos(a), 11.4, 0.4 + 3.2 * np.sin(a))) for a in np.linspace(0, 2 * np.pi, 8, endpoint=False)], [np.array((-12.4 + 4.6 * np.cos(a), 15.6, 0.4 + 4.6 * np.sin(a))) for a in np.linspace(0, 2 * np.pi, 8, endpoint=False)]], caps=("wood", "wood")))
        F += part("bundle", ellipsoid("body", "paper", (12.4, 13.2, 0.4), (3.6, 3.4, 3.6), segs=8, rings=3))
        F += part("bundle_tie", box("body", "cord", 12.4, 13.2, 0.4, 3.8, 0.35, 3.8))
        return F
    # C 短い上着と肩掛け: 巨大な丸い荷袋に毛布の巻き。鍋と鉄瓶を吊る。額に鉢巻き。
    F = body("c")
    F += part("sack", ellipsoid("body", "cloth2", (0.0, 17.0, 7.6), (6.6, 8.2, 5.8), segs=9, rings=4))
    F += part("sack_top", ellipsoid("body", "leather", (0.0, 25.4, 7.6), (3.2, 1.6, 3.2), segs=7, rings=2))
    F += part("roll", rodp("body", "wood", (-7.8, 26.4, 6.6), (7.8, 26.4, 6.6), 2.2, 7))
    F += part("roll_band", rodp("body", "cord", (-2.2, 26.4, 6.6), (-1.0, 26.4, 6.6), 2.4, 7))
    F += part("roll_band2", rodp("body", "cord", (1.6, 26.4, 6.6), (2.8, 26.4, 6.6), 2.4, 7))
    F += part("kettle", ellipsoid("body", "iron", (-5.8, 9.0, 8.2), (1.9, 1.9, 1.9), segs=7, rings=2))
    F += part("kettle_rope", rodp("body", "cord", (-5.8, 10.4, 8.0), (-4.6, 14.6, 6.4), 0.22, 3))
    F += part("pan", ellipsoid("body", "iron", (5.4, 11.0, 9.4), (2.1, 0.5, 2.1), segs=8, rings=1))
    F += part("headband", loft("head", "role", [ring(29.0, 3.7, -3.6, 3.4, n=8), ring(30.2, 3.7, -3.6, 3.4, n=8)], caps=("role", "role"))) if False else []
    return F


SUBJECTS = {
    "mio": dict(fn=mio, ja="灯り番ミオ", role="骨の層の生き残り。探掘隊の灯りを預かる。", opts=(
        ("A 灯りの鉤の杖", "上着と前掛け。右手の長い鉤の杖に角灯を下げる。腰にも予備の灯りと油の小瓶。遠くからも灯りで見つかる。"),
        ("B 抱えた大角灯", "長い衣。両手で胸に大きな角灯を抱え、首に煤けた布。灯りが体の中心で、守る人に見える。"),
        ("C 背の灯りの枠", "短い上着。背の木枠に三つの角灯を吊り、右手に合図の小灯。後ろ姿でも灯り番と分かる。"))),
    "hayase": dict(fn=hayase, ja="測量士ハヤセ", role="塩の層の生き残り。地図と測りの道具を持つ。", opts=(
        ("A 目盛りの杖", "上着と前掛け。目盛りを刻んだ長い杖、腰に丸めた図三本、胸に方位盤。測る人の定番の形。"),
        ("B 三脚の測量器", "長い衣。右手で三脚の測量器を地に立て、左手に画板。道具が大きく役が一目で分かる。"),
        ("C 背の図筒", "短い上着。図筒を斜めに背負い、腰に縄の巻き枠。身軽で、道具は小さめ。"))),
    "shizu": dict(fn=shizu, ja="薬師シズ", role="雷の層の生き残り。薬と草を扱う。", opts=(
        ("A 薬瓶の弾帯", "上着と前掛け。薬瓶を斜めに並べた弾帯、右手に薬草の束。瓶の色が胸で目立つ。"),
        ("B 薬草籠と笠", "長い衣。背に大きな薬草籠 (草が飛び出す)、広いつばの帽子。山の薬師の形。"),
        ("C 抱えた薬箱", "短い上着。両手で薬箱を抱え、腰にすり鉢。赤い印で医者と読める。"))),
    "toya": dict(fn=toya, ja="隊長トウヤ", role="灰の層の生き残り。探掘隊をまとめる。", opts=(
        ("A 肩のツルハシ", "上着と前掛け。ツルハシを肩にかつぎ、胸に縄の巻き、左肩に鉄の肩当て。掘る隊の長。"),
        ("B 盾と短剣のマント", "長い衣。背に丸い盾、腰に短剣、裂けたマント。戦う隊長の形。"),
        ("C 隊の旗", "短い上着。裂けた隊の旗を右手に立て、腰に槌。旗が目印になる。"))),
    "roku": dict(fn=roku, ja="荷運びロク", role="塩の野営にいる。深淵の素材と食べ物・灯りを取り替える。", opts=(
        ("A 背負い枠の木箱", "上着と前掛け。高い背負い枠に木箱が三段、縄と鍋。荷が多い。"),
        ("B 天秤棒", "長い衣。天秤棒の両端に籠と包み。取り替える荷が両側に見える。"),
        ("C 巨大な荷袋", "短い上着。丸い荷袋に毛布の巻き、鉄瓶と鍋を吊る。重そうなシルエット。"))),
}


def build_one(sid, k):
    S = SUBJECTS[sid]
    faces = S["fn"](k)
    d = OUT / f"npc_{sid}" / k
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = CK.build_atlas(faces, 256, 0)
    pal = pal_npc(sid)
    img, glow = AT.RA_PAINT(faces, size, P, N, M, pal, ())
    img = AT.face_paint(img, faces, P, N, M, sid)
    Image.fromarray(img, "RGBA").save(d / f"npc_{sid}_{k}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"npc_{sid}_{k}_glowmask.png")
    data = CK.export_mesh(faces, size, d / f"npc_{sid}_{k}_mesh.json", f"npc_{sid}_{k}", f"npc_{sid}_{k}_atlas.png", BONES, {"glowmask": f"npc_{sid}_{k}_glowmask.png"})
    bad = CK.check_buried(faces)
    return dict(key=k, faces=faces, atlas=img, glow=glow, size=size, tris=data["triangle_count"], buried=bad)


def wrap(d, text, font, width):
    lines, cur = [], ""
    for ch in text:
        if d.textlength(cur + ch, font=font) > width and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


POSES = {"idle": {"head": (-2, 0, 0)}}


def sheet(sid, results):
    S = SUBJECTS[sid]
    F = voxel.font
    PW, GAP, X0, PH = 380, 14, 20, 420
    W = X0 * 2 + 3 * PW + 2 * GAP
    f_t, f_n, f_d, f_r = F(34), F(28), F(21), F(19)
    probe = ImageDraw.Draw(Image.new("RGB", (4, 4)))
    sub = wrap(probe, S["role"] + " 係の人 (都の施設の番人) と同じ骨・同じ顔の作り。左の目盛りは人 (1.8 ブロック)。", f_d, W - 48)
    caps = [wrap(probe, S["opts"][i][1], f_d, PW - 8) for i in range(3)]
    cap_h = 40 + 26 * max(len(c) for c in caps) + 12
    top = 24 + 46 + 26 * len(sub) + 12
    views = [("前", 180, 6), ("3/4", 212, 12), ("横", 270, 6), ("後ろ 3/4", 32, 12)]
    total = top + cap_h + len(views) * (PH + 42) + 20
    cv = Image.new("RGB", (W, total), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 18), f"{S['ja']} — 3 案", font=f_t, fill=(236, 232, 218))
    y = 70
    for line in sub:
        d.text((24, y), line, font=f_d, fill=(176, 176, 192))
        y += 26
    y = top
    for i in range(3):
        x = X0 + i * (PW + GAP)
        d.text((x + 2, y), S["opts"][i][0], font=f_n, fill=(150, 232, 255))
        yy = y + 40
        for line in caps[i]:
            d.text((x + 4, yy), line, font=f_d, fill=(226, 224, 214))
            yy += 26
    y += cap_h
    for head, yaw, pit in views:
        d.text((X0, y + 2), head, font=f_r, fill=(150, 232, 255))
        y += 30
        for i, r in enumerate(results):
            x = X0 + i * (PW + GAP)
            im = CK.render_view(r | {"faces": r["faces"]}, r["atlas"], r["glow"], POSES["idle"], BONES, yaw, pit, (PW, PH), (0, 15.2, 0), 11.6)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + PH], outline=(60, 60, 78))
        y += PH + 12
    cv.crop((0, 0, W, y + 6)).save(OUT / f"npc_{sid}.png")


def main(ids):
    OUT.mkdir(parents=True, exist_ok=True)
    for sid in ids:
        res = [build_one(sid, k) for k in "abc"]
        for r in res:
            print(sid, r["key"], "tris", r["tris"], "buried", r["buried"])
        sheet(sid, res)


if __name__ == "__main__":
    main(sys.argv[1:] or list(SUBJECTS))
