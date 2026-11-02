"""雷角の大羊 (storm_ram)。嵐の高地の名のある強敵。Lv 24、まれに出る。
一体一案: 雪の斜面に棲む肩の高さ 1.6 ブロックの大きな山羊 (大角の羊)。房になって垂れる厚い灰白の毛に霜、
黒い顔と長い顎ひげ。頭の両側に巻いた巨大な角は黒い石のようで、角の溝に青白い稲妻の筋が走って光る。
蹄は黒く、脚の下は毛が薄い。背に雷に焼けた黒い筋が一本。
動き: 頭を下げて (予告 1.2 秒、前の長い帯) 角で突進する。後ろ脚で立ち上がって蹄を打ち下ろす (予告 1.2 秒、まわりの輪) と、輪に雷が走る。
"""
import math

from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 20.0, 0)), "head": ("body", (0, 26.0, -11.0)),
         "fore_l": ("body", (4.0, 17.0, -8.0)), "fore_r": ("body", (-4.0, 17.0, -8.0)),
         "hind_l": ("body", (4.0, 18.0, 9.0)), "hind_r": ("body", (-4.0, 18.0, 9.0)), "tail": ("body", (0, 23.0, 12.6))}
WOOL = [(96, 100, 108), (132, 136, 144), (166, 170, 178), (196, 200, 208), (224, 228, 236)]
FACE = [(14, 14, 18), (28, 28, 34), (46, 46, 54), (66, 66, 76)]
HORN = [(20, 22, 28), (36, 38, 46), (56, 58, 68), (78, 80, 92)]
PAL = {"mats": {"wool": mat(WOOL, 0.62, noise=0.35), "face": mat(FACE, 0.55, noise=0.15), "horn": mat(HORN, 0.55, crack=0.12),
                "scorch": mat([(10, 10, 14), (24, 24, 30), (40, 40, 48)], 0.5, noise=0.3), "hoof": mat([(8, 8, 10), (20, 20, 24), (34, 34, 40)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "face"}


def clumps(F, pid, bone, center, radii, n, seed, size=2.4):
    """毛の房: 中心のまわりに小さな楕円をばらまく (房の凹凸)"""
    cx, cy, cz = center
    rx, ry, rz = radii
    for i in range(n):
        a = (i * 137.5 + seed) * math.pi / 180
        b = ((i * 61 + seed * 3) % 180 - 90) * math.pi / 180 * 0.8
        x = cx + rx * math.cos(b) * math.cos(a)
        z = cz + rz * math.cos(b) * math.sin(a)
        y = cy + ry * math.sin(b)
        r = size * (0.8 + 0.4 * ((i * 7) % 5) / 4)
        F += part(f"{pid}{i}", ellipsoid(bone, "wool", (x, y, z), (r, r * 1.3, r), segs=8, rings=3))


def horn(F, sx):
    """巻いた角: 頭の横から後ろ・下・前へ一巻き半。溝に光る稲妻の筋"""
    pts, ra = [], []
    n = 14
    for i in range(n):
        t = i / (n - 1)
        a = t * 1.45 * 2 * math.pi
        r = 7.0 * (1 - 0.5 * t)
        x = sx * (3.2 + 5.6 * t)
        y = 29.0 + r * math.cos(a) - 4.0
        z = -10.0 + r * math.sin(a) + 2.0
        pts.append((x, y, z))
        ra.append(3.0 * (1 - 0.8 * t) + 0.3)
    F += part(f"horn{sx}", tube("head", "horn", pts, ra=ra, sides=7, hint=(1, 0, 0)))
    # 稲妻の筋 (角の外側をなぞる細い光)
    vein = [(x + sx * r_ * 0.8, y, z) for (x, y, z), r_ in zip(pts[1:12], ra[1:12])]
    F += part(f"vein{sx}", tube("head", "glow_blue", vein, ra=[0.5] * len(vein), sides=4, hint=(0, 1, 0)))


def build(k):
    F = []
    # 胴: 厚い毛の塊 (房の凹凸)
    F += part("torso", ellipsoid("body", "wool", (0, 21.0, 0.0), (7.4, 7.6, 12.0), segs=10, rings=4))
    clumps(F, "tuft", "body", (0, 20.0, 0.0), (6.8, 7.0, 11.0), 16, 11, size=3.4)
    for sx in (1, -1):
        for i, z in enumerate((-7.0, -1.0, 5.0)):
            F += part(f"skirt{sx}{i}", ellipsoid("body", "wool", (sx * 6.0, 14.0, z), (2.6, 4.0, 3.4), segs=7, rings=3))
    F += part("scorch", tube("body", "scorch", [(0, 28.8, -6.0), (1.2, 29.0, 0.0), (-0.8, 28.4, 6.0), (0.4, 27.0, 10.0)], ra=[0.9, 0.9, 0.8, 0.5], sides=4, hint=(1, 0, 0)))
    # 首と頭: 黒い顔、長い顎ひげ、小さな耳、角
    F += part("neck", tube("head", "wool", [(0, 23.0, -8.0), (0, 26.0, -11.0)], ra=[4.4, 3.6], sides=8, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "face", (0, 27.6, -13.6), (3.2, 3.8, 4.4), segs=8, rings=3))
    F += part("muzzle", tube("head", "face", [(0, 26.4, -16.6), (0, 24.6, -19.6)], ra=[2.4, 1.7], rb=[2.0, 1.5], sides=7, hint=(1, 0, 0)))
    F += part("beard", tube("head", "wool", [(0, 23.4, -17.0), (0, 19.6, -16.6), (0, 17.0, -15.0)], ra=[1.6, 1.0, 0.2], rb=[1.0, 0.7, 0.2], sides=5, hint=(1, 0, 0)))
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_pale", (sx * 2.5, 28.4, -15.8), (0.5, 0.35, 0.5)))
        F += part(f"ear{sx}", box("head", "face", (sx * 3.6, 28.2, -12.0), (2.6, 0.8, 1.2), taper=(0.5, 0.5)))
        horn(F, sx)
    # 脚: 膝まで毛、下は黒く細い。黒い蹄
    for sx, fb, hb in ((1, "fore_l", "hind_l"), (-1, "fore_r", "hind_r")):
        F += part(f"foreup{sx}", tube(fb, "wool", [(sx * 4.0, 17.0, -8.0), (sx * 4.2, 9.0, -8.4)], ra=[3.0, 2.2], sides=7, hint=(1, 0, 0)))
        F += part(f"forelo{sx}", tube(fb, "face", [(sx * 4.2, 9.4, -8.4), (sx * 4.2, 1.8, -8.0)], ra=[1.2, 1.0], sides=6, hint=(1, 0, 0)))
        F += part(f"forehoof{sx}", box(fb, "hoof", (sx * 4.2, 0.9, -8.4), (2.2, 1.8, 2.6), taper=(0.85, 0.85)))
        F += part(f"thigh{sx}", ellipsoid(hb, "wool", (sx * 4.2, 15.6, 9.6), (3.0, 5.0, 3.6), segs=8, rings=3))
        F += part(f"hindlo{sx}", tube(hb, "face", [(sx * 4.2, 11.0, 11.0), (sx * 4.2, 1.8, 10.2)], ra=[1.2, 1.0], sides=6, hint=(1, 0, 0)))
        F += part(f"hindhoof{sx}", box(hb, "hoof", (sx * 4.2, 0.9, 9.8), (2.2, 1.8, 2.6), taper=(0.85, 0.85)))
    F += part("tail", ellipsoid("tail", "wool", (0, 22.0, 13.4), (1.8, 2.4, 1.6), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {"head": (4, 0, 0)},
    # 頭を下げて角を前へ (突進の予告)
    "telegraph": {"body": (-8, 0, 0), "head": (-34, 0, 0), "fore_l": (24, 0, 0), "fore_r": (-6, 0, 0), "hind_l": (-16, 0, 0), "hind_r": (-16, 0, 0)},
    # 後ろ脚で立ち上がる (蹄を打ち下ろす予告)
    "rear": {"body": (40, 0, 0), "head": (-16, 0, 0), "fore_l": (-60, 0, 0), "fore_r": (-40, 0, 0), "hind_l": (-40, 0, 0), "hind_r": (-40, 0, 0), "tail": (-20, 0, 0)},
}

SPEC = dict(id="storm_ram", short="雷角の大羊", title="雷角の大羊 (storm_ram) — 嵐の高地の名のある強敵",
            sub=["肩の高さ 1.6 ブロックの大角の羊。房の垂れた灰白の毛、黒い顔と顎ひげ、黒い石のような巻いた大角に光る稲妻の筋、背の焼けた筋。",
                 "頭を下げて (予告 1.2 秒、前の長い帯) 突進。後ろ脚で立ち上がって蹄を打ち下ろす (予告 1.2 秒、まわりの輪) と輪に雷が走る。"],
            rows=[("前 3/4", "idle", 210, 10, 600, (0, 20, -3), 7.0), ("横", "idle", 270, 6, 600, (0, 20, -3), 7.0),
                  ("後ろ 3/4", "idle", 30, 10, 600, (0, 20, -3), 7.0), ("予告: 角を下げる", "telegraph", 210, 8, 600, (0, 20, -3), 7.0),
                  ("予告: 立ち上がる", "rear", 250, 6, 600, (0, 24, -1), 7.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
