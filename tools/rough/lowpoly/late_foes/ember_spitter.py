"""熾吐き (ember_spitter)。灰の荒野の敵 (issue #20)。役: 遠くから撃つ。Lv 25〜30。
10〜18 の距離を保ち、熾の塊を山なりに吐く (予告 1.0 秒: 喉 / 袋が光って膨らみ、狙った人の足もとに橙の円)。落ちた所に 3 秒の燃える溜まり (半径 1.6)。
寄られると灰を吹いて (目くらみ 1.5 秒) 下がる。骨: root > body > head > jaw / sac / leg_fl / leg_fr / leg_bl / leg_br / tail。
A 熾のヒキガエル: 煤色の大きな蛙。頬の袋が熾で光り、背にいぼの熾
B 煤の火吹き蜥蜴: 首の長い蜥蜴。喉の袋が光り、背に黒石の鱗の列
C 火口の甲虫: 尻に煙突のような火口を背負った甲虫。火口から熾を撃ち上げる
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, rows_std, storm, lumps

storm(False)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 6.0, 0)), "head": ("body", (0, 8.0, -6.0)), "jaw": ("head", (0, 6.8, -8.0)),
         "sac": ("head", (0, 6.0, -7.0)), "leg_fl": ("body", (4.0, 5.0, -4.0)), "leg_fr": ("body", (-4.0, 5.0, -4.0)),
         "leg_bl": ("body", (4.0, 5.0, 4.0)), "leg_br": ("body", (-4.0, 5.0, 4.0)), "tail": ("body", (0, 6.0, 6.0))}
PAL = {"mats": {"soot": mat(CHAR, 0.5, noise=0.2), "ash": mat(ASH, 0.6, noise=0.22), "basalt": mat(BASALT, 0.58, noise=0.2),
                "ember": mat(EMBER, 0.8, min=1), "rust": mat(RUST, 0.58, noise=0.15), "obsid": mat(OBSID, 0.62, noise=0.1)},
       "skull_bone": "head", "face_mat": "soot", "glow_mats": ["ember"]}


def build_a(k):
    """A 熾のヒキガエル"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 6.6, 0.6), (5.6, 4.4, 6.0), segs=10, rings=3))
    F += part("belly", ellipsoid("body", "rust", (0, 4.4, -0.6), (4.2, 2.4, 4.6), segs=8, rings=2))
    F += part("head", ellipsoid("head", "soot", (0, 8.6, -5.0), (4.8, 2.8, 3.6), segs=10, rings=2))
    F += part("jaw", ellipsoid("jaw", "rust", (0, 7.0, -5.6), (4.2, 1.2, 3.0), segs=8, rings=1))
    for sx in (1, -1):
        F += part(f"cheek{sx}", ellipsoid("sac", "ember", (sx * 4.0, 7.4, -4.4), (1.8, 1.6, 1.8), segs=7, rings=2))
        F += part(f"brow{sx}", ellipsoid("head", "ash", (sx * 2.0, 11.0, -5.4), (1.4, 1.1, 1.3), segs=6, rings=1))
    F += lumps("body", "ember", [((2.4, 10.4, 1.0), 0.6), ((-2.0, 10.6, 2.4), 0.7), ((0.6, 10.8, 4.0), 0.5), ((-3.4, 9.0, -0.6), 0.55), ((3.6, 9.2, 3.0), 0.6)])
    for b, sx, sz, big in (("leg_fl", 1, -1, False), ("leg_fr", -1, -1, False), ("leg_bl", 1, 1, True), ("leg_br", -1, 1, True)):
        if big:
            F += part(b, tube(b, "soot", [(sx * 4.4, 5.4, sz * 3.6), (sx * 7.0, 4.6, sz * 2.6), (sx * 6.4, 0.8, sz * 5.4)], ra=[2.0, 1.4, 0.9], sides=6, hint=(1, 0, 0)))
        else:
            F += part(b, tube(b, "soot", [(sx * 3.6, 5.2, sz * 3.6), (sx * 4.6, 2.6, sz * 4.6), (sx * 4.6, 0.6, sz * 5.0)], ra=[1.2, 0.9, 0.8], sides=6, hint=(1, 0, 0)))
        F += part(b + "_foot", ellipsoid(b, "ash", (sx * (6.4 if big else 4.8), 0.5, sz * (5.6 if big else 5.4)), (1.6, 0.5, 1.6), segs=6, rings=1))
    return F, [eye_at(-2.0, 11.6, -6.0, 0.7), eye_at(2.0, 11.6, -6.0, 0.7)]


def build_b(k):
    """B 煤の火吹き蜥蜴"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 6.6, 1.0), (3.8, 3.0, 6.4), segs=9, rings=3))
    F += part("neck", tube("head", "soot", [(0, 7.4, -4.4), (0, 10.0, -7.4), (0, 12.4, -8.8)], ra=[2.4, 1.9, 1.6], sides=7, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "soot", (0, 13.2, -10.4), (1.9, 1.6, 2.8), segs=8, rings=2))
    F += part("jaw", box("jaw", "rust", (0, 12.0, -11.6), (2.6, 0.8, 3.6), taper=(0.7, 0.6)))
    F += part("sac", ellipsoid("sac", "ember", (0, 9.6, -7.8), (1.8, 2.2, 1.6), segs=7, rings=2))
    for i in range(6):
        z = -3.0 + i * 2.0
        F += part(f"scale{i}", spike("body", "obsid", (0, 9.2 - 0.2 * abs(i - 2), z), (0, 1, 0.3), 2.2 - 0.2 * i, 0.9, sides=4, rings=1))
    for b, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
        F += part(b, tube(b, "soot", [(sx * 3.2, 6.0, sz * 3.6), (sx * 5.4, 4.0, sz * 3.8), (sx * 5.2, 0.6, sz * 4.4)], ra=[1.3, 1.0, 0.8], sides=6, hint=(1, 0, 0)))
        F += part(b + "_foot", ellipsoid(b, "ash", (sx * 5.4, 0.5, sz * 4.4 - 0.8), (1.2, 0.5, 1.6), segs=6, rings=1))
    F += part("tail", tube("tail", "soot", [(0, 6.4, 6.6), (0, 5.0, 11.0), (0.8, 2.6, 15.0)], ra=[2.0, 1.2, 0.2], sides=6, hint=(1, 0, 0)))
    return F, [eye_at(-1.3, 14.0, -11.6, 0.5), eye_at(1.3, 14.0, -11.6, 0.5)]


def build_c(k):
    """C 火口の甲虫"""
    F = []
    F += part("carapace", ellipsoid("body", "basalt", (0, 7.2, 0.8), (5.0, 3.6, 6.4), segs=10, rings=3))
    F += part("seam", box("body", "ember", (0, 10.6, 0.8), (0.6, 0.5, 10.0)))
    F += part("belly", ellipsoid("body", "rust", (0, 4.4, 0.6), (4.0, 1.6, 5.4), segs=8, rings=1))
    # 尻の火口 (煙突)
    F += part("vent", tube("tail", "basalt", [(0, 8.6, 5.2), (0, 12.6, 8.0), (0, 15.6, 9.0)], ra=[2.2, 1.8, 1.9], sides=7, hint=(1, 0, 0)))
    F += part("vent_mouth", ellipsoid("tail", "ember", (0, 15.8, 9.0), (1.5, 0.5, 1.5), segs=7, rings=1))
    F += part("head", ellipsoid("head", "obsid", (0, 6.6, -6.2), (2.6, 2.0, 2.0), segs=8, rings=2))
    for sx in (1, -1):
        F += part(f"mand{sx}", tube("jaw", "obsid", [(sx * 1.2, 6.0, -7.6), (sx * 2.0, 5.6, -9.4), (sx * 0.8, 5.4, -10.4)], ra=[0.6, 0.4, 0.1], sides=4, hint=(0, 1, 0)))
    for b, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
        F += part(b, tube(b, "obsid", [(sx * 4.0, 5.4, sz * 3.2), (sx * 6.4, 4.6, sz * 3.8), (sx * 6.2, 0.4, sz * 4.4)], ra=[0.8, 0.6, 0.35], sides=4, hint=(1, 0, 0)))
    F += part("leg_mid_l", tube("body", "obsid", [(4.2, 5.4, 0.6), (6.8, 4.4, 0.6), (6.6, 0.4, 0.6)], ra=[0.8, 0.6, 0.35], sides=4, hint=(1, 0, 0)))
    F += part("leg_mid_r", tube("body", "obsid", [(-4.2, 5.4, 0.6), (-6.8, 4.4, 0.6), (-6.6, 0.4, 0.6)], ra=[0.8, 0.6, 0.35], sides=4, hint=(1, 0, 0)))
    return F, [eye_at(-1.4, 7.4, -7.8, 0.5), eye_at(1.4, 7.4, -7.8, 0.5)]


def detail(F):
    # 細部: 背のいぼ (煤) の群れ・指の爪・喉のしわ・目の上の瘤
    for i in range(10):
        a = math.radians(i * 36)
        F += part(f"wart{i}", ellipsoid("body", "ash", (3.6 * math.cos(a), 9.8 + 0.6 * math.sin(a * 2), 0.6 + 3.8 * math.sin(a)), (0.5, 0.35, 0.5), segs=5, rings=1))
    for b, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
        x0 = sx * (6.4 if sz > 0 else 4.8)
        for j in range(3):
            F += part(f"{b}_toe{j}", spike(b, "soot", (x0 + (j - 1) * 0.6, 0.4, sz * (5.6 if sz > 0 else 5.4) - 1.2), (0, -0.1, -1), 1.0, 0.25, sides=3, rings=1))
    for i in range(3):
        F += part(f"throat{i}", tube("jaw", "rust", [(-2.6, 6.2 - i * 0.3, -4.2 - i * 0.6), (0, 5.8 - i * 0.3, -4.8 - i * 0.6), (2.6, 6.2 - i * 0.3, -4.2 - i * 0.6)],
                                   ra=[0.2, 0.25, 0.2], sides=4, hint=(0, 0, 1)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        F = detail(F)
    return F, eyes


POSES = {
    "idle": {},
    "windup": {"head": (-22, 0, 0), "jaw": (20, 0, 0), "body": (-8, 0, 0), "tail": (-30, 0, 0)},
    "spit": {"head": (-30, 0, 0), "jaw": (40, 0, 0), "body": (-6, 0, 0)},
}

SPEC = dict(
    id="ember_spitter", short="熾吐き", kind="灰の敵 3案 (遠くから撃つ)", role="遠くから撃つ", land="灰",
    title="熾吐き (ember_spitter) — 灰の荒野・遠くから撃つ・低ポリ 3 案",
    sub=["10〜18 の距離を保ち、熾の塊を山なりに吐く (予告 1.0 秒: 袋が光って膨らみ、足もとに橙の円)。落ちた所に 3 秒の燃える溜まり。",
         "寄られると灰を吹いて (目くらみ 1.5 秒) 下がる。熾の山犬の群れの後ろから撃つ役。"],
    labels=["A 熾のヒキガエル", "B 煤の火吹き蜥蜴", "C 火口の甲虫"],
    rows=rows_std(7, 11.0, h=320, extra=(("予告: 膨らむ", "windup"), ("吐く", "spit"))),
    options={
        "a": dict(name="A 熾のヒキガエル", desc="煤色の大きな蛙。頬の袋が熾で光って膨らむのが予告。背にいぼの熾。ずんぐりして遠くからでも形が読める。"),
        "b": dict(name="B 煤の火吹き蜥蜴", desc="首の長い蜥蜴。喉の袋が光り、背に黒石の鱗の列。首を上げて吐く姿が分かりやすい。灰の蜥蜴 (名のある強敵) と形が近い。"),
        "c": dict(name="C 火口の甲虫", desc="尻に煙突のような火口を背負った甲虫。火口から熾を撃ち上げる (砲台)。背の割れ目が熾で光る。燠虫・熾り蟲と同じ虫の仲間。")},
    extra="骨 10 本 (root > body > head > jaw / sac / 脚 4 / tail)。",
    budget="地上の敵 400〜700", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
