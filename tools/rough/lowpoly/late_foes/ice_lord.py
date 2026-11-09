"""吹雪の氷穴の主 (ice_lord)。嵐の土地の奥のダンジョン「吹雪の氷穴」(霜の小島。滑る床と吹き抜け) の主 (issue #19 の ②)。Lv 27 前後。
技: 薙ぎ払い (0.8 秒、前の扇)・氷柱落とし (1.2 秒、狙った人の足もとの円 2.5 が五つ)・吹雪 (体力 50% から。床が滑り、視界が狭まる)。
骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r / back。
A 氷鎧の大熊: 背と肩に氷の板が生えた白い大熊。立ち上がって叩く
B 雪の巨人: 長い白い毛の巨人。氷の棍棒、顔は古い仮面
C 氷柱の大蝙蝠: 翼の膜が氷、天井から逆さに吊るされる。吹き抜けを飛ぶ
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, storm, grow, rows_std, lumps

storm(True)
K = 1.6
BONES = grow([], {"root": (None, (0, 0, 0)), "body": ("root", (0, 10.0, 0)), "head": ("body", (0, 18.0, -8.0)), "jaw": ("head", (0, 16.0, -10.0)),
                  "arm_l": ("body", (5.0, 15.0, -4.0)), "arm_r": ("body", (-5.0, 15.0, -4.0)), "leg_l": ("body", (4.0, 9.0, 4.0)),
                  "leg_r": ("body", (-4.0, 9.0, 4.0)), "back": ("body", (0, 18.0, 0))}, K)
ICE = [(120, 160, 200), (160, 198, 230), (200, 228, 250), (236, 248, 255)]
WHITE = [(150, 156, 166), (190, 196, 204), (220, 226, 232), (244, 246, 250)]
PAL = {"mats": {"ice": mat(ICE, 0.72, noise=0.1), "fur": mat(WHITE, 0.62, noise=0.22), "dark": mat([(20, 22, 30), (36, 40, 52), (56, 60, 74)], 0.5),
                "volt": mat(VOLT, 0.8, min=1), "crag": mat(CRAG, 0.6, noise=0.2), "bone": mat([(170, 160, 140), (210, 200, 180)], 0.65),
                "membrane": mat([(90, 130, 170), (130, 170, 210), (170, 206, 240)], 0.6, noise=0.12)},
       "skull_bone": "head", "face_mat": "fur", "glow_mats": ["volt"]}


def build_a(k):
    F = []
    F += part("body", ellipsoid("body", "fur", (0, 11.0, 0), (6.0, 6.0, 9.0), segs=10, rings=4))
    F += part("hump", ellipsoid("back", "fur", (0, 15.6, -3.0), (5.0, 3.6, 4.4), segs=9, rings=2))
    for i, (x, z, ln) in enumerate(((0, -3.0, 5.0), (2.4, -1.0, 4.0), (-2.4, -1.4, 4.2), (1.4, 2.4, 3.4), (-1.6, 3.0, 3.2), (0, 5.6, 2.6))):
        F += part(f"plate{i}", spike("back", "ice", (x, 17.0 - abs(z) * 0.3, z), (x * 0.15, 1, -0.1), ln, 1.4, sides=4, rings=1))
    F += part("head", ellipsoid("head", "fur", (0, 15.8, -10.6), (3.4, 3.0, 3.4), segs=8, rings=3))
    F += part("muzzle", ellipsoid("head", "fur", (0, 14.8, -13.8), (1.8, 1.6, 2.0), segs=7, rings=2))
    F += part("nose", ellipsoid("head", "dark", (0, 15.4, -15.6), (0.8, 0.6, 0.5), segs=5, rings=1))
    F += part("jaw", box("jaw", "fur", (0, 13.4, -13.4), (2.6, 0.9, 3.0), taper=(0.8, 0.7)))
    for sx in (1, -1):
        F += part(f"ear{sx}", ellipsoid("head", "fur", (sx * 2.4, 18.6, -10.0), (1.0, 1.0, 0.6), segs=6, rings=1))
        F += part(f"cheek_ice{sx}", spike("head", "ice", (sx * 3.0, 16.0, -9.6), (sx, 0.3, 0.4), 2.2, 0.7, sides=4, rings=1))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_leg", tube(b, "fur", [(sx * 5.0, 14.6, -4.6), (sx * 5.4, 6.0, -5.6), (sx * 5.2, 1.2, -5.8)], ra=[2.6, 2.2, 2.0], sides=7, hint=(1, 0, 0)))
        F += part(f"{b}_paw", ellipsoid(b, "fur", (sx * 5.2, 1.0, -6.6), (2.2, 1.0, 2.6), segs=7, rings=1))
        for j in range(3):
            F += part(f"{b}_claw{j}", spike(b, "ice", (sx * 5.2 + (j - 1) * 1.0, 0.8, -9.0), (0, -0.3, -1), 1.4, 0.4, sides=3, rings=1))
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(f"{b}_leg", tube(b, "fur", [(sx * 4.4, 9.6, 4.6), (sx * 4.8, 4.0, 5.6), (sx * 4.6, 1.0, 5.0)], ra=[2.8, 2.2, 2.0], sides=7, hint=(1, 0, 0)))
        F += part(f"{b}_paw", ellipsoid(b, "fur", (sx * 4.6, 0.9, 4.2), (2.0, 0.9, 2.4), segs=7, rings=1))
    grow(F, {}, K)
    return F, [eye_at(-1.4 * K, 17.0 * K, -13.4 * K, 0.5 * K), eye_at(1.4 * K, 17.0 * K, -13.4 * K, 0.5 * K)]


def build_b(k):
    F = []
    # 立った巨人 (前かがみ)
    F += part("torso", ellipsoid("body", "fur", (0, 17.0, -2.0), (6.0, 6.8, 4.6), segs=9, rings=3))
    F += part("belly", ellipsoid("body", "fur", (0, 11.0, -0.6), (5.0, 3.4, 4.0), segs=8, rings=2))
    F += part("mane", ellipsoid("back", "fur", (0, 21.6, -2.4), (6.4, 3.0, 5.0), segs=9, rings=2))
    F += part("head", ellipsoid("head", "fur", (0, 22.4, -7.2), (2.8, 3.0, 2.8), segs=8, rings=3))
    F += part("mask", box("head", "bone", (0, 22.6, -9.8), (3.6, 4.0, 0.8), taper=(0.8, 0.9)))
    F += part("mask_eyes", box("head", "volt", (0, 23.4, -10.3), (2.6, 0.5, 0.3)))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_up", tube(b, "fur", [(sx * 6.0, 20.0, -2.6), (sx * 8.0, 12.0, -4.0)], ra=[2.4, 2.0], sides=7, hint=(0, 0, 1)))
        F += part(f"{b}_lo", tube(b, "fur", [(sx * 8.0, 12.2, -4.0), (sx * 8.4, 4.6, -5.0)], ra=[2.0, 1.8], sides=7, hint=(0, 0, 1)))
        F += part(f"{b}_hand", ellipsoid(b, "dark", (sx * 8.4, 3.4, -5.2), (1.8, 1.8, 1.8), segs=6, rings=2))
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(f"{b}_leg", tube(b, "fur", [(sx * 3.0, 9.6, 0), (sx * 3.4, 4.0, -0.4), (sx * 3.4, 1.0, 0)], ra=[2.4, 2.0, 1.9], sides=7, hint=(1, 0, 0)))
        F += part(f"{b}_foot", box(b, "dark", (sx * 3.4, 0.6, -1.2), (3.2, 1.2, 4.6), taper=(0.9, 0.8)))
    F += part("club", tube("arm_r", "ice", [(-8.4, 3.0, -5.2), (-8.6, 3.0, -16.0)], ra=[1.2, 2.6], sides=6, hint=(0, 1, 0)))
    F += lumps("arm_r", "ice", [((-8.6, 4.6, -15.0), 1.0), ((-7.2, 2.2, -14.0), 0.9), ((-9.8, 2.4, -13.6), 0.9)])
    grow(F, {}, K)
    return F, []


def build_c(k):
    F = []
    F += part("body", ellipsoid("body", "fur", (0, 14.0, 0), (3.6, 5.6, 3.2), segs=8, rings=3))
    F += part("head", ellipsoid("head", "fur", (0, 21.0, -1.0), (2.8, 2.6, 2.6), segs=8, rings=3))
    for sx in (1, -1):
        F += part(f"ear{sx}", spike("head", "fur", (sx * 1.6, 22.8, -1.0), (sx * 0.4, 1, 0), 3.4, 1.0, sides=4, rings=1))
        F += part(f"fang{sx}", spike("jaw", "ice", (sx * 0.8, 19.4, -3.2), (0, -1, -0.2), 1.4, 0.35, sides=3, rings=1))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        # 翼の骨と氷の膜
        bones_ = [(sx * 3.0, 17.0, 0), (sx * 9.0, 20.0, 1.0), (sx * 15.0, 16.0, 1.0)]
        F += part(f"{b}_wing_bone", tube(b, "dark", bones_, ra=[0.8, 0.6, 0.3], sides=4, hint=(0, 0, 1)))
        for j, tip in enumerate(((sx * 15.0, 8.0, 1.0), (sx * 11.0, 6.0, 1.0), (sx * 6.0, 7.0, 0.6))):
            F += part(f"{b}_finger{j}", tube(b, "dark", [(sx * 9.0, 20.0, 1.0), tip], ra=[0.4, 0.15], sides=3, hint=(0, 0, 1)))
            F += part(f"{b}_membrane{j}", grid(b, "membrane" if j else "ice", [[(sx * 9.0, 20.0, 1.0), (sx * 15.0, 16.0, 1.0) if j == 0 else (sx * 9.0, 20.0, 1.0)],
                                                                            [tip, tip]], (0, 14.0, 6.0)))
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(f"{b}_leg", tube(b, "dark", [(sx * 1.6, 9.0, 0.4), (sx * 1.8, 5.6, 0.4)], ra=[0.6, 0.4], sides=4, hint=(1, 0, 0)))
    F += lumps("body", "ice", [((0, 12.0, -2.6), 0.8), ((1.4, 15.6, -2.4), 0.6), ((-1.2, 10.4, -2.0), 0.6)])
    grow(F, {}, K)
    return F, [eye_at(-1.0 * K, 21.6 * K, -3.4 * K, 0.5 * K), eye_at(1.0 * K, 21.6 * K, -3.4 * K, 0.5 * K)]


def detail(F):
    # 細部: 首まわりの毛の房・肩の氷の小板・爪の数・背の氷の筋
    for i in range(10):
        a = math.radians(180 + i * 18)
        F += part(f"ruff{i}", spike("head", "fur", (3.2 * math.cos(a), 14.6 + 1.6 * math.sin(a), -7.6), (math.cos(a), math.sin(a) * 0.6, 0.4), 1.6, 0.6, sides=4, rings=1))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        for j in range(2):
            F += part(f"{b}_ice{j}", spike(b, "ice", (sx * 5.6, 12.6 - j * 2.4, -4.4), (sx * 0.8, 0.6, -0.2), 1.6, 0.5, sides=4, rings=1))
    for i in range(5):
        F += part(f"backvein{i}", box("back", "volt", (0, 16.6 - i * 0.2, -4.0 + i * 2.0), (0.4, 0.3, 1.2)))
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        for j in range(3):
            F += part(f"{b}_claw{j}", spike(b, "ice", (sx * 4.6 + (j - 1) * 0.8, 0.7, 2.0), (0, -0.3, -1), 1.0, 0.3, sides=3, rings=1))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        extra = detail([])
        grow(extra, {}, K)
        F += extra
    return F, eyes


POSES = {"idle": {}, "sweep": {"body": (0, 25, 0), "arm_r": (-60, 0, -40), "head": (-10, 0, 0)},
         "rear": {"body": (-35, 0, 0), "arm_l": (-60, 0, 0), "arm_r": (-60, 0, 0), "head": (20, 0, 0), "jaw": (30, 0, 0)}}

SPEC = dict(
    id="ice_lord", short="氷穴の主", kind="奥のダンジョンの主 3案", role="主", land="嵐",
    title="吹雪の氷穴の主 (ice_lord) — 嵐の奥のダンジョン・低ポリ 3 案",
    sub=["薙ぎ払い (0.8 秒、前の扇)・氷柱落とし (1.2 秒、足もとの円 2.5 が五つ)・吹雪 (体力 50% から。床が滑り、視界が狭まる)。",
         "大きさは人の 1.6 倍 (A は四つ足で長さ 3 ブロックほど)。"],
    labels=["A 氷鎧の大熊", "B 雪の巨人", "C 氷柱の大蝙蝠"],
    rows=rows_std(16, 5.5, h=420, extra=(("薙ぎ払いの予告", "sweep"), ("立ち上がり / 咆哮", "rear"))),
    options={
        "a": dict(name="A 氷鎧の大熊", desc="背と肩に氷の板が生えた白い大熊。立ち上がって前脚で叩く。滑る床の上の突進が作りやすく、雷翼の土地の獣の頂点に見える。"),
        "b": dict(name="B 雪の巨人", desc="長い白い毛の巨人、氷の棍棒、顔は古い骨の仮面 (目が青白く光る)。人型なので技の予告の読みやすさは一番。"),
        "c": dict(name="C 氷柱の大蝙蝠", desc="翼の膜が氷の大蝙蝠。天井から逆さに吊るされて待ち、吹き抜けを飛ぶ。空を飛ぶ主は今までにないが、近接の職は戦いにくい。")},
    extra="骨 10 本。", budget="主 600〜1200", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
