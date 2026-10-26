"""霧の漁り火 (mist_angler)。潮の浜と沼の夜。Lv 13〜17、遠距離。
一体一案: 沖で沈んだ漁師の霊。脚はなく、腰から下は霧の尾になって水面すれすれを漂う。
濡れて垂れた蓑 (藁の雨具) を重ね着し、頭は破れた網を頬かむりにして浮き (コルクの玉) がぶら下がる。顔は暗く、青白い目だけ。
右手に長い竿、竿の先に提げた漁り火 (鉄の籠の灯) が青緑に燃える。左肩に網の束。蓑の裾から海藻が垂れる。
動き: 竿を振って灯を揺らすと霧が湧く (まわりが見えにくい)。網を投げる (予告 1.0 秒、床の円) と、円の中の人は 3 秒鈍る。
"""
import math

import fk
from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

fk.GLOW["glow_sea"] = (120, 240, 210)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 14.0, 0)), "head": ("body", (0, 25.0, 0)),
         "left_arm": ("body", (5.0, 23.0, 0)), "left_forearm": ("left_arm", (5.6, 17.6, 0)),
         "right_arm": ("body", (-5.0, 23.0, 0)), "right_forearm": ("right_arm", (-5.6, 17.6, 0)), "weapon": ("right_forearm", (-6.0, 12.4, -1.0)),
         "lantern": ("weapon", (-6.0, 30.0, -9.0)), "tail": ("body", (0, 12.0, 0))}
STRAW = [(36, 34, 22), (58, 54, 34), (84, 78, 50), (112, 104, 70), (140, 132, 94)]
NET = [(40, 46, 44), (64, 72, 68), (92, 100, 94), (120, 128, 120)]
CORK = [(90, 60, 34), (130, 92, 52), (168, 126, 76)]
WEED = [(14, 36, 30), (24, 58, 44), (40, 84, 60), (60, 110, 76)]
MIST = [(70, 96, 104), (100, 130, 136), (136, 164, 166), (176, 200, 198)]
SKIN = [(80, 96, 98), (112, 130, 128), (146, 162, 156)]
IRON = [(18, 20, 22), (34, 36, 40), (54, 56, 62), (76, 78, 84)]
POLE = [(40, 34, 28), (64, 54, 42), (90, 78, 60)]
PAL = {"mats": {"straw": mat(STRAW, 0.56, noise=0.42), "net": mat(NET, 0.55, noise=0.3, crack=0.22), "cork": mat(CORK, 0.6), "weed": mat(WEED, 0.58, noise=0.3),
                "mist": mat(MIST, 0.6, noise=0.3), "skin": mat(SKIN, 0.6, crack=0.05), "iron": mat(IRON, 0.55, crack=0.05), "pole": mat(POLE, 0.6),
                "dark": mat([(4, 6, 8), (10, 14, 18), (20, 26, 30)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "dark"}


def ring(r_x, r_z, y, z0=0.0, n=10, jag=0.0, phase=0, x0=0.0):
    out = []
    for i in range(n):
        a = math.radians(i * 360 / n)
        k = 1.0 + (jag if (i + phase) % 2 else -jag)
        out.append(V(x0 + r_x * k * math.cos(a), y - (abs(jag) * 6 if (i + phase) % 2 else 0), z0 + r_z * k * math.sin(a)))
    return out


def build(k):
    F = []
    # 蓑: 三段に重ねた藁の雨具 (裾はぎざぎざ、下へ広がる)
    F += part("mino1", loft("body", "straw", [ring(4.6, 3.4, 12.6, jag=0.12), ring(3.6, 2.6, 18.0)], caps=("straw", "straw")))
    F += part("mino2", loft("body", "straw", [ring(4.4, 3.2, 17.0, jag=0.1, phase=1), ring(3.4, 2.4, 22.0)], caps=("straw", "straw")))
    F += part("mino3", loft("body", "straw", [ring(5.4, 3.8, 21.0, jag=0.14), ring(1.8, 1.6, 24.6)], caps=("straw", "straw")))
    F += part("chest", ellipsoid("body", "dark", (0, 19.0, -0.4), (3.0, 4.0, 2.0), segs=7, rings=2))
    # 蓑の裾から垂れる海藻
    for i, (x, z, L) in enumerate(((-3.6, -1.2, 4.0), (2.8, -2.2, 5.0), (4.0, 1.4, 3.4), (-2.0, 2.8, 4.6), (0.6, -3.2, 3.0))):
        F += part(f"weed{i}", tube("body", "weed", [(x, 12.2, z), (x * 1.1, 12.2 - L * 0.5, z * 1.1), (x * 0.9, 12.2 - L, z * 1.2)], ra=[0.45, 0.35, 0.1], sides=4, hint=(1, 0, 0)))
    # 霧の尾 (脚の代わり)
    for i, (dx, dz, L) in enumerate(((0.0, 0.8, 10.0), (-1.6, -0.6, 8.0), (1.8, -0.4, 7.2))):
        F += part(f"mist{i}", tube("tail", "mist", [(dx, 12.0, dz), (dx * 1.3, 7.6, dz * 1.4 + 1.0), (dx * 0.8, 12.0 - L, dz * 1.6 + 2.6)], ra=[2.2, 1.3, 0.15], sides=6, hint=(1, 0, 0)))
    # 頭: 暗い顔、青白い目、網の頬かむりと垂れた浮き
    F += part("face", ellipsoid("head", "dark", (0, 27.0, -0.2), (2.6, 2.9, 2.7), segs=8, rings=3))
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_sea", (sx * 0.95, 27.2, -2.75), (0.6, 0.35, 0.3)))
    F += part("hood", ellipsoid("head", "net", (0, 28.0, 0.5), (3.2, 3.2, 3.2), segs=8, rings=3))
    F += part("hood_knot", ellipsoid("head", "net", (0, 25.4, 2.8), (1.2, 1.0, 1.0), segs=5, rings=1))
    for i, (x, z) in enumerate(((2.6, -1.4), (-2.8, -0.6), (1.2, 2.6))):
        F += part(f"line{i}", tube("head", "net", [(x, 27.0, z), (x * 1.15, 24.4, z)], ra=[0.12, 0.12], sides=3, hint=(1, 0, 0)))
        F += part(f"float{i}", ellipsoid("head", "cork", (x * 1.15, 23.8, z), (0.6, 0.75, 0.6), segs=6, rings=2))
    # 腕: 細い青白い腕、左肩に網の束
    for sx, arm, fore in ((1, "left_arm", "left_forearm"), (-1, "right_arm", "right_forearm")):
        F += part(arm, tube(arm, "straw", [(sx * 4.8, 23.0, 0), (sx * 5.6, 17.6, 0)], ra=[1.3, 1.1], sides=6, hint=(0, 0, 1)))
        F += part(fore, tube(fore, "skin", [(sx * 5.6, 17.8, 0), (sx * 6.0, 12.8, -0.8)], ra=[0.55, 0.45], sides=5, hint=(0, 0, 1)))
        F += part(fore + "_hand", ellipsoid(fore, "skin", (sx * 6.0, 12.2, -1.0), (0.8, 1.0, 0.8), segs=5, rings=1))
    F += part("netbundle", ellipsoid("body", "net", (3.6, 22.4, 1.8), (2.4, 2.8, 2.0), segs=7, rings=3))
    F += part("netdrape", box("body", "net", (4.2, 18.4, 2.4), (2.6, 5.0, 0.4), taper=(0.6, 1.0)))
    for i, y in enumerate((20.6, 17.0)):
        F += part(f"nfloat{i}", ellipsoid("body", "cork", (5.0, y, 2.8), (0.6, 0.75, 0.6), segs=6, rings=2))
    # 竿 (右手から前上へ) と、先に提げた鉄の籠の漁り火
    F += part("pole", tube("weapon", "pole", [(-6.0, 9.6, 0.4), (-6.0, 20.0, -4.0), (-6.0, 30.6, -9.4)], ra=[0.45, 0.4, 0.3], sides=5, hint=(1, 0, 0)))
    F += part("cord", tube("lantern", "net", [(-6.0, 30.4, -9.4), (-6.0, 27.6, -9.6)], ra=[0.12, 0.12], sides=3, hint=(1, 0, 0)))
    F += part("cage_top", loft("lantern", "iron", [ring(1.7, 1.7, 27.2, z0=-9.6, n=6, x0=-6.0), ring(0.5, 0.5, 28.0, z0=-9.6, n=6, x0=-6.0)], caps=("iron", "iron")))
    F += part("flame", ellipsoid("lantern", "glow_sea", (-6.0, 25.2, -9.6), (1.2, 1.7, 1.2), segs=7, rings=3))
    for i in range(4):
        a = math.radians(45 + i * 90)
        x, z = -6.0 + 1.55 * math.cos(a), -9.6 + 1.55 * math.sin(a)
        F += part(f"bar{i}", tube("lantern", "iron", [(x, 27.2, z), (x, 23.2, z)], ra=[0.16, 0.16], sides=3, hint=(1, 0, 0)))
    F += part("cage_base", box("lantern", "iron", (-6.0, 23.0, -9.6), (2.8, 0.4, 2.8)))
    return F, []


POSES = {
    # 前へ少し屈み、竿を前へ差し出す
    "idle": {"body": (-10, 0, 0), "head": (6, 0, 0), "right_arm": (34, 0, -6), "right_forearm": (-14, 0, 0), "weapon": (-12, 0, 0), "lantern": (2, 0, 0),
             "left_arm": (8, 0, 6), "left_forearm": (-20, 0, 0), "tail": (-14, 0, 0)},
    # 網を投げる構え: 左手で網を後ろへ振りかぶる
    "telegraph": {"body": (-4, 30, 0), "head": (2, -20, 0), "left_arm": (150, -30, 20), "left_forearm": (-20, 0, 0), "right_arm": (20, 0, -10), "weapon": (-30, 0, 0),
                  "lantern": (14, 0, 0), "tail": (-20, 0, 10)},
}

SPEC = dict(id="mist_angler", short="霧の漁り火", title="霧の漁り火 (mist_angler) — 漁り火を提げた沈んだ漁師の霊",
            sub=["脚はなく腰から下は霧の尾。濡れた蓑の三段重ね、網の頬かむりと浮き、暗い顔に青白い目、左肩の網の束、長い竿の先の鉄籠の漁り火 (青緑)。",
                 "灯を揺らして霧を呼び、網を投げる (予告 1.0 秒、床の円) と円の中の人が 3 秒鈍る。"],
            rows=[("前 3/4", "idle", 210, 10, 600, (0, 18, -2), 11.0), ("横", "idle", 270, 6, 600, (0, 18, -2), 11.0),
                  ("後ろ 3/4", "idle", 30, 10, 600, (0, 18, -2), 11.0), ("予告: 網を投げる", "telegraph", 210, 8, 600, (0, 18, -2), 11.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
