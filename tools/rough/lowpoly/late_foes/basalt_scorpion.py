"""黒石の甲殻 (basalt_scorpion)。灰の荒野の敵 (issue #20)。役: 守りを固める。Lv 26〜30。
人が前にいると構える (予告 0.6 秒: 前の殻 / 鋏を閉じる。構えているあいだ前からの傷 −70%)。構えたまま尾 / 角で突く (予告 0.9 秒、前の細い帯 4.5)。
突いたあと 1.5 秒は構えが解ける。骨: root > body > head / claw_l / claw_r / tail > sting / 脚 6 (leg_l1..3, leg_r1..3)。
A 黒石の蠍: 黒石の板の大きな蠍。鋏を盾のように閉じ、尾の針が熾
B 熔岩殻の鎧鼠: 熔岩が冷えた殻の大きな鎧鼠 (アルマジロ)。構えると前の殻を立てる。尾の棍
C 炭鎧の盾蟻: 頭が盾の形の大きな蟻。顎を閉じると頭で前を塞ぐ。腹の先が熾
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, rows_std, storm, lumps

storm(False)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 5.0, 0)), "head": ("body", (0, 5.0, -6.0)),
         "claw_l": ("body", (4.0, 5.0, -5.0)), "claw_r": ("body", (-4.0, 5.0, -5.0)), "tail": ("body", (0, 6.0, 5.0)), "sting": ("tail", (0, 14.0, 6.0))}
for i, z in ((1, -2.6), (2, 0.4), (3, 3.4)):
    BONES[f"leg_l{i}"] = ("body", (3.6, 4.6, z))
    BONES[f"leg_r{i}"] = ("body", (-3.6, 4.6, z))
PAL = {"mats": {"basalt": mat(BASALT, 0.58, noise=0.2, crack=0.08), "obsid": mat(OBSID, 0.65, noise=0.1), "ember": mat(EMBER, 0.8, min=1),
                "soot": mat(CHAR, 0.5, noise=0.2), "rust": mat(RUST, 0.58, noise=0.15), "ash": mat(ASH, 0.6, noise=0.2)},
       "skull_bone": "head", "face_mat": "basalt", "glow_mats": ["ember"]}


def legs6(F, m, spread=6.4, r=0.6):
    for i, z in ((1, -2.6), (2, 0.4), (3, 3.4)):
        for sx, side in ((1, "l"), (-1, "r")):
            b = f"leg_{side}{i}"
            F += part(b, tube(b, m, [(sx * 3.4, 4.6, z), (sx * spread, 5.6, z + (i - 2) * 0.8), (sx * (spread + 1.0), 0.3, z + (i - 2) * 1.6)],
                               ra=[r, r * 0.8, r * 0.4], sides=4, hint=(1, 0, 0)))


def build_a(k):
    """A 黒石の蠍"""
    F = []
    for i in range(4):
        z = -3.6 + i * 2.6
        F += part(f"plate{i}", ellipsoid("body", "basalt", (0, 5.6, z), (4.2 - 0.3 * i, 2.2, 1.7), segs=8, rings=2))
    F += part("belly", ellipsoid("body", "rust", (0, 4.0, 0.4), (3.2, 1.2, 5.6), segs=8, rings=1))
    F += part("head", ellipsoid("head", "obsid", (0, 5.4, -6.4), (3.0, 1.8, 2.0), segs=8, rings=2))
    for sx, b in ((1, "claw_l"), (-1, "claw_r")):
        F += part(f"{b}_arm", tube(b, "basalt", [(sx * 3.6, 5.0, -5.0), (sx * 6.0, 6.0, -7.4), (sx * 4.4, 6.0, -10.0)], ra=[1.0, 0.9, 1.0], sides=5, hint=(0, 1, 0)))
        F += part(f"{b}_pincer", box(b, "obsid", (sx * 3.4, 6.0, -11.6), (3.6, 3.0, 3.4), taper=(0.7, 0.8)))
        F += part(f"{b}_tip", spike(b, "obsid", (sx * 2.6, 6.2, -13.0), (-sx * 0.4, 0, -1), 2.0, 0.8, sides=4, rings=1))
    # 尾: 体節が上へ弧を描き、先に熾の針
    pts = [(0, 6.0, 4.6), (0, 8.6, 7.6), (0, 12.4, 8.6), (0, 15.0, 6.6)]
    F += part("tail", tube("tail", "basalt", pts, ra=[1.6, 1.3, 1.1, 0.9], sides=6, hint=(1, 0, 0)))
    F += part("sting_bulb", ellipsoid("sting", "obsid", (0, 15.2, 5.6), (1.2, 1.2, 1.4), segs=6, rings=2))
    F += part("sting", spike("sting", "ember", (0, 14.8, 4.6), (0, -0.6, -1), 2.4, 0.6, sides=4, rings=1))
    legs6(F, "obsid")
    return F, [eye_at(-1.0, 6.6, -8.0, 0.4), eye_at(1.0, 6.6, -8.0, 0.4)]


def build_b(k):
    """B 熔岩殻の鎧鼠"""
    F = []
    F += part("shell", ellipsoid("body", "basalt", (0, 7.0, 0.6), (5.0, 4.6, 7.0), segs=10, rings=3))
    for i in range(4):
        F += part(f"band{i}", ellipsoid("body", "ember", (0, 7.4, -3.0 + i * 2.4), (5.1, 4.7, 0.4), segs=10, rings=2))
    F += part("frontplate", box("claw_l", "obsid", (0, 7.6, -6.6), (8.6, 6.4, 1.0), taper=(0.8, 1.0)))
    F += part("head", ellipsoid("head", "rust", (0, 5.0, -7.8), (1.8, 1.6, 2.4), segs=7, rings=2))
    F += part("snout", ellipsoid("head", "soot", (0, 4.4, -10.0), (0.9, 0.8, 1.0), segs=6, rings=1))
    for sx in (1, -1):
        F += part(f"ear{sx}", box("head", "rust", (sx * 1.6, 6.8, -7.4), (1.0, 1.4, 0.5), taper=(0.4, 0.6)))
    F += part("tail", tube("tail", "basalt", [(0, 5.6, 6.8), (0, 4.4, 10.0)], ra=[1.4, 1.0], sides=6, hint=(1, 0, 0)))
    F += part("club", ellipsoid("sting", "obsid", (0, 4.2, 11.2), (1.8, 1.6, 1.8), segs=7, rings=2))
    for i, z in ((1, -2.6), (3, 3.4)):
        for sx, side in ((1, "l"), (-1, "r")):
            b = f"leg_{side}{i}"
            F += part(b, tube(b, "rust", [(sx * 3.4, 4.0, z), (sx * 3.8, 0.4, z)], ra=[1.2, 1.0], sides=6, hint=(1, 0, 0)))
            F += part(b + "_claw", box(b, "soot", (sx * 3.8, 0.4, z - 0.8), (1.6, 0.8, 1.6), taper=(0.8, 0.6)))
    return F, [eye_at(-1.0, 5.6, -9.0, 0.4), eye_at(1.0, 5.6, -9.0, 0.4)]


def build_c(k):
    """C 炭鎧の盾蟻"""
    F = []
    F += part("thorax", ellipsoid("body", "soot", (0, 5.6, -1.6), (2.4, 2.0, 2.8), segs=8, rings=2))
    F += part("waist", tube("body", "soot", [(0, 5.4, 0.6), (0, 5.8, 2.0)], ra=[1.0, 1.0], sides=5, hint=(1, 0, 0)))
    F += part("abdomen", ellipsoid("tail", "basalt", (0, 6.6, 5.4), (3.6, 3.2, 4.4), segs=9, rings=3))
    F += part("abd_tip", ellipsoid("sting", "ember", (0, 6.4, 9.4), (1.4, 1.4, 1.0), segs=6, rings=1))
    for i in range(3):
        F += part(f"abd_band{i}", ellipsoid("tail", "obsid", (0, 6.8, 3.6 + i * 1.8), (3.7, 3.3, 0.35), segs=9, rings=2))
    # 盾の頭
    F += part("head", ellipsoid("head", "obsid", (0, 6.6, -6.0), (4.4, 3.6, 1.8), segs=9, rings=2))
    F += part("head_rim", box("head", "ember", (0, 10.0, -6.2), (5.0, 0.5, 1.0)))
    for sx, b in ((1, "claw_l"), (-1, "claw_r")):
        F += part(f"{b}_mand", tube(b, "obsid", [(sx * 2.0, 4.2, -7.4), (sx * 2.8, 3.8, -9.6), (sx * 0.6, 3.6, -10.6)], ra=[0.8, 0.6, 0.1], sides=4, hint=(0, 1, 0)))
        F += part(f"ant{sx}", tube("head", "soot", [(sx * 1.6, 9.8, -6.8), (sx * 3.2, 12.8, -8.4), (sx * 4.6, 13.0, -10.6)], ra=[0.3, 0.25, 0.05], sides=4, hint=(0, 1, 0)))
    legs6(F, "soot", spread=5.6, r=0.55)
    return F, [eye_at(-2.6, 7.6, -7.4, 0.5), eye_at(2.6, 7.6, -7.4, 0.5)]


def detail(F):
    # 細部: 板の稜の棘・鋏の歯・脚の関節の棘・尾の節の輪
    for i in range(4):
        z = -3.6 + i * 2.6
        F += part(f"ridge{i}", spike("body", "obsid", (0, 7.6, z), (0, 1, 0.3), 1.4, 0.4, sides=4, rings=1))
        for s in (1, -1):
            F += part(f"edge{i}{s}", spike("body", "obsid", (s * (4.0 - 0.3 * i), 5.6, z), (s, 0.3, 0), 1.0, 0.3, sides=3, rings=1))
    for sx, b in ((1, "claw_l"), (-1, "claw_r")):
        for j in range(3):
            F += part(f"{b}_tooth{j}", spike(b, "basalt", (sx * (2.4 + 0.6 * j), 5.0, -12.6 + j * 0.3), (-sx, 0, 0), 0.8, 0.22, sides=3, rings=1))
    for i, (y, z) in enumerate(((7.6, 6.6), (10.4, 8.4), (13.2, 8.2))):
        F += part(f"tailring{i}", tube("tail", "obsid", [(0, y, z - 0.3), (0, y + 0.3, z + 0.3)], ra=[1.5 - 0.15 * i, 1.5 - 0.15 * i], sides=6, hint=(1, 0, 0)))
    for i, z in ((1, -2.6), (2, 0.4), (3, 3.4)):
        for sx, side in ((1, "l"), (-1, "r")):
            F += part(f"knee{side}{i}", spike(f"leg_{side}{i}", "obsid", (sx * 6.4, 5.6, z), (sx * 0.3, 1, 0), 0.9, 0.25, sides=3, rings=1))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        F = detail(F)
    return F, eyes


POSES = {
    "idle": {},
    "guard": {"claw_l": (0, 30, 0), "claw_r": (0, -30, 0), "head": (10, 0, 0), "body": (6, 0, 0)},
    "strike": {"tail": (-25, 0, 0), "body": (-8, 0, 0), "head": (-14, 0, 0)},
}

SPEC = dict(
    id="basalt_scorpion", short="黒石の甲殻", kind="灰の敵 3案 (守りを固める)", role="守りを固める", land="灰",
    title="黒石の甲殻 (basalt_scorpion) — 灰の荒野・守りを固める・低ポリ 3 案",
    sub=["人が前にいると構える (予告 0.6 秒。構えているあいだ前からの傷 −70%)。構えたまま突く (予告 0.9 秒、前の細い帯 4.5)。",
         "突いたあと 1.5 秒は構えが解ける。突きを避けて、解けた瞬間か横・後ろから叩く役。"],
    labels=["A 黒石の蠍", "B 熔岩殻の鎧鼠", "C 炭鎧の盾蟻"],
    rows=rows_std(6, 11.0, h=320, extra=(("構える (前からの傷 −70%)", "guard"), ("突き", "strike"))),
    options={
        "a": dict(name="A 黒石の蠍", desc="黒石の板の大きな蠍。鋏を閉じると前を塞ぐ盾になり、尾の熾の針が上から突く。構えと突きの両方が一番読みやすい。"),
        "b": dict(name="B 熔岩殻の鎧鼠", desc="熔岩が冷えた殻の鎧鼠。殻の継ぎ目が熾で光り、構えると前の殻板を立てる。尾の棍で叩く。かわいさと固さが半々。"),
        "c": dict(name="C 炭鎧の盾蟻", desc="頭が盾の形の大きな蟻。顎を閉じると頭で前を塞ぐ。腹の先が熾。群れ (蟻) の連想で、二体一組で出す形も作れる。")},
    extra="骨 13 本 (root > body > head / claw_l / claw_r / tail > sting / 脚 6)。",
    budget="地上の敵 400〜700", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
