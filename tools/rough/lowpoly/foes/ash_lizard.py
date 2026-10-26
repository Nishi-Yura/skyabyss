"""灰被りの大蜥蜴 (ash_lizard)。灰の荒野の名のある強敵。Lv 30、まれに出る。
一体一案: 尾まで 3.5 ブロックの低く長い大蜥蜴。灰をかぶった黒い肌はひび割れ、ひびの奥で熾火が赤く光る。
背には冷えた溶岩の塊のような棘が並び、平たく幅の広い頭の口の奥が燃える。脚は横へ張り出し、爪は黒曜石。尾は太く長い。
動き: 頭を持ち上げて口の奥を燃え立たせ (予告 1.2 秒、前の扇) 灰と熾火を吐く。体をひねって (予告 0.8 秒、まわりの輪) 尾で薙ぐ。
"""
import math

from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8.0, 0)), "head": ("body", (0, 9.0, -14.0)), "jaw": ("head", (0, 7.6, -17.0)),
         "fore_l": ("body", (6.0, 8.0, -9.0)), "fore_r": ("body", (-6.0, 8.0, -9.0)),
         "hind_l": ("body", (6.0, 8.0, 9.0)), "hind_r": ("body", (-6.0, 8.0, 9.0)),
         "tail": ("body", (0, 8.6, 13.0)), "tail2": ("tail", (0, 7.4, 26.0))}
SKIN = [(14, 12, 12), (28, 24, 22), (44, 40, 36), (66, 60, 54), (92, 86, 78)]
ASH = [(70, 68, 64), (104, 100, 94), (140, 136, 128), (176, 172, 164)]
ROCK = [(20, 14, 12), (38, 28, 22), (60, 44, 32), (86, 64, 44)]
OBS = [(6, 4, 10), (18, 14, 26), (36, 30, 50)]
PAL = {"mats": {"skin": mat(SKIN, 0.55, crack=0.14, noise=0.2), "ash": mat(ASH, 0.6, noise=0.45), "rock": mat(ROCK, 0.56, crack=0.16, noise=0.25),
                "obs": mat(OBS, 0.6), "belly": mat(SKIN[1:], 0.58, noise=0.15)},
       "skull_bone": "head", "face_mat": "skin"}


def crack(F, pid, bone, pts, r=0.35):
    F += part(pid, tube(bone, "glow_red", pts, ra=[r] * len(pts), sides=4, hint=(0, 1, 0)))


def _build():
    F = []
    # 胴: 低く長い。灰の積もった背と、熾火のひび
    F += part("torso", ellipsoid("body", "skin", (0, 8.6, 0.0), (7.0, 4.6, 14.0), segs=10, rings=4))
    F += part("ashback", ellipsoid("body", "ash", (0, 11.6, -1.0), (5.2, 1.8, 11.0), segs=9, rings=2))
    F += part("belly", ellipsoid("body", "belly", (0, 5.6, 0.0), (5.6, 1.8, 11.6), segs=9, rings=2))
    for sx in (1, -1):
        crack(F, f"crk_a{sx}", "body", [(sx * 6.4, 9.6, -9.0), (sx * 6.9, 8.4, -5.0), (sx * 6.6, 9.8, -1.0), (sx * 7.0, 8.2, 3.0)])
        crack(F, f"crk_b{sx}", "body", [(sx * 6.0, 7.6, 4.0), (sx * 6.4, 9.0, 7.6), (sx * 5.6, 7.8, 10.6)])
    # 背の溶岩の棘 (前ほど高い)
    for i, (z, h) in enumerate(((-10.0, 4.6), (-6.0, 5.4), (-2.0, 5.0), (2.0, 4.4), (6.0, 3.6), (10.0, 2.8))):
        F += part(f"spine{i}", tube("body", "rock", [(0, 11.8, z), (0, 11.8 + h, z + 1.6)], ra=[1.9, 0.2], rb=[1.2, 0.15], sides=5, hint=(1, 0, 0)))
        F += part(f"spine_glow{i}", ellipsoid("body", "glow_red", (0, 12.2, z + 0.4), (0.7, 0.4, 0.8), segs=5, rings=1))
    # 頭: 平たく幅広い。口の奥が燃え、目は熾火
    F += part("neck", tube("head", "skin", [(0, 9.0, -12.0), (0, 9.4, -15.0)], ra=[4.6, 4.0], rb=[3.4, 3.0], sides=8, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "skin", (0, 9.6, -19.0), (5.0, 2.8, 5.6), segs=9, rings=3))
    F += part("snout", ellipsoid("head", "skin", (0, 9.0, -23.4), (3.8, 2.0, 2.6), segs=8, rings=2))
    F += part("crown", ellipsoid("head", "ash", (0, 11.8, -18.4), (3.6, 1.0, 3.6), segs=7, rings=2))
    F += part("maw", ellipsoid("head", "glow_red", (0, 7.6, -20.4), (3.4, 0.6, 3.6), segs=8, rings=2))
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_amber", (sx * 3.8, 10.8, -20.6), (0.9, 0.5, 1.2)))
        F += part(f"horn{sx}", tube("head", "rock", [(sx * 3.6, 11.2, -16.8), (sx * 5.4, 12.6, -14.0), (sx * 6.2, 13.2, -12.0)], ra=[1.0, 0.6, 0.15], sides=5, hint=(0, 1, 0)))
        crack(F, f"crk_h{sx}", "head", [(sx * 4.2, 9.6, -22.0), (sx * 4.8, 10.0, -19.0), (sx * 4.6, 9.2, -16.4)], r=0.3)
    F += part("jaw", ellipsoid("jaw", "skin", (0, 6.8, -20.6), (4.4, 1.2, 4.6), segs=8, rings=2))
    # 脚: 横へ張り出す。黒曜石の爪
    for sx, fb, hb in ((1, "fore_l", "hind_l"), (-1, "fore_r", "hind_r")):
        for b, z in ((fb, -9.0), (hb, 9.0)):
            F += part(f"up{b}", tube(b, "skin", [(sx * 6.0, 8.0, z), (sx * 11.0, 8.6, z - 1.0)], ra=[2.6, 2.0], sides=7, hint=(0, 1, 0)))
            F += part(f"elbow{b}", ellipsoid(b, "skin", (sx * 11.0, 8.6, z - 1.0), (2.1, 2.1, 2.1), segs=6, rings=2))
            F += part(f"lo{b}", tube(b, "skin", [(sx * 11.0, 8.6, z - 1.0), (sx * 12.0, 1.4, z - 1.6)], ra=[1.8, 1.5], sides=6, hint=(1, 0, 0)))
            F += part(f"foot{b}", ellipsoid(b, "skin", (sx * 12.2, 0.9, z - 2.6), (2.2, 0.9, 2.6), segs=7, rings=2))
            for c in (-1, 0, 1):
                F += part(f"claw{b}{c}", tube(b, "obs", [(sx * 12.2 + c * 1.1, 0.8, z - 4.6), (sx * 12.2 + c * 1.4, 0.3, z - 6.2)], ra=[0.45, 0.1], sides=4, hint=(0, 1, 0)))
    # 尾: 太く長い。二節
    F += part("tail1", tube("tail", "skin", [(0, 8.6, 12.0), (0, 8.0, 19.0), (0, 7.4, 26.0)], ra=[5.0, 3.6, 2.6], rb=[3.8, 2.8, 2.0], sides=8, hint=(1, 0, 0)))
    F += part("tail2", tube("tail2", "skin", [(0, 7.4, 26.0), (0, 6.6, 33.0), (0, 6.0, 39.0)], ra=[2.6, 1.6, 0.3], rb=[2.0, 1.2, 0.3], sides=7, hint=(1, 0, 0)))
    crack(F, "crk_t", "tail", [(0, 11.4, 14.0), (0.8, 10.6, 18.0), (-0.6, 10.0, 22.0), (0.4, 9.4, 25.0)])
    for i, z in enumerate((16.0, 22.0)):
        F += part(f"tspine{i}", tube("tail", "rock", [(0, 10.4 - i * 1.0, z), (0, 12.6 - i * 1.2, z + 1.4)], ra=[1.2, 0.15], sides=5, hint=(1, 0, 0)))
    return F


# 名のある強敵の大きさ: 部品は 1 倍で組み、最後に全体を K 倍する (骨の pivot も同じ倍率)
K = 1.6
BONES = {b: (p, tuple(c * K for c in v)) for b, (p, v) in BONES.items()}


def build(k):
    F = _build()
    for f in F:
        f.verts = f.verts * K
        f.ref = f.ref * K
        if f.loc is not None:
            f.loc = f.loc * K
    return F, []


POSES = {
    "idle": {"head": (4, 6, 0), "tail": (0, 10, 0), "tail2": (0, 16, 0)},
    # 吐く予告: 頭を持ち上げて口を開け、口の奥を燃え立たせる
    "telegraph": {"body": (10, 0, 0), "head": (26, 0, 0), "jaw": (-28, 0, 0), "fore_l": (-14, 0, 0), "fore_r": (-14, 0, 0), "tail": (-8, 0, 0)},
    # 尾の薙ぎの予告: 体をひねり、尾を振りかぶる
    "coil": {"body": (0, 30, 0), "head": (0, 20, 0), "tail": (0, 40, 0), "tail2": (0, 40, 0)},
}

SPEC = dict(id="ash_lizard", short="灰被りの大蜥蜴", title="灰被りの大蜥蜴 (ash_lizard) — 灰の荒野の名のある強敵",
            sub=["尾まで 3.5 ブロックの低く長い大蜥蜴。灰をかぶったひび割れた黒い肌、ひびの奥と口の奥で熾火が光る。背の冷えた溶岩の棘、平たい頭の岩の角、横へ張り出した脚と黒曜石の爪。",
                 "頭を持ち上げて (予告 1.2 秒、前の扇) 灰と熾火を吐く。体をひねって (予告 0.8 秒、まわりの輪) 尾で薙ぐ。"],
            rows=[("前 3/4", "idle", 210, 14, 600, (0, 13, 6), 4.0), ("横", "idle", 270, 8, 600, (0, 13, 6), 4.0),
                  ("後ろ 3/4", "idle", 30, 14, 600, (0, 13, 6), 4.0), ("予告: 吐く", "telegraph", 210, 10, 600, (0, 15, 4), 4.0),
                  ("予告: 尾の薙ぎ", "coil", 180, 30, 600, (0, 13, 6), 4.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
