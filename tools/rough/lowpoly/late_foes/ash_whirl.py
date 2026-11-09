"""灰の渦 (ash_whirl)。灰の荒野の敵 (issue #20)。役: まわりに効く。Lv 26〜30。
人に寄ると回り始め (予告 1.2 秒: 灰の輪が速くなり、足もとに半径 4.5 の円)、円の中の人を中へ少し引き寄せて灰を吹き上げる (傷 + 鈍足 2 秒 + 目くらみ 1 秒)。
吹いたあと 2 秒は芯が出て止まる (芯を叩くと傷 +50%)。骨: root > spin > core / ring0 / ring1 / ring2 / mask。
A 灰の竜巻の精: 灰の輪が三段に重なった竜巻、中に熾の芯、上に素焼きの面
B 灰被りの舞い手: 長い袖の灰の衣の亡霊が回る。顔は煤けた面、袖の先が熾
C 灰の壺の精: 宙に浮く割れた素焼きの壺から灰の渦が立つ。壺の割れ目から熾
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, rows_std, storm, lumps

storm(False)
BONES = {"root": (None, (0, 0, 0)), "spin": ("root", (0, 10.0, 0)), "core": ("spin", (0, 12.0, 0)),
         "ring0": ("spin", (0, 4.0, 0)), "ring1": ("spin", (0, 11.0, 0)), "ring2": ("spin", (0, 18.0, 0)), "mask": ("spin", (0, 24.0, 0))}
CLAY = [(80, 50, 36), (120, 80, 56), (160, 116, 84), (196, 154, 118)]
PAL = {"mats": {"ash": mat(ASH, 0.62, noise=0.25), "soot": mat(CHAR, 0.5, noise=0.2), "ember": mat(EMBER, 0.8, min=1),
                "clay": mat(CLAY, 0.6, noise=0.15, crack=0.1), "cloth": mat([(60, 56, 58), (92, 86, 86), (128, 120, 116), (164, 156, 148)], 0.58, noise=0.2)},
       "skull_bone": "mask", "face_mat": "clay", "glow_mats": ["ember"]}


def swirl(F, bone, y, r, h, n=8, mat_="ash", tw=25.0):
    """竜巻の一段: 斜めに傾いた灰の帯を輪に並べる"""
    for i in range(n):
        a = math.radians(i * 360 / n)
        a2 = math.radians(i * 360 / n + tw)
        p0 = (r * math.cos(a), y - h / 2, r * math.sin(a))
        p1 = (r * 1.15 * math.cos(a2), y + h / 2, r * 1.15 * math.sin(a2))
        F += part(f"{bone}_s{i}", tube(bone, mat_, [p0, p1], ra=[r * 0.32, r * 0.22], sides=4, hint=(0, 1, 0)))


def build_a(k):
    """A 灰の竜巻の精"""
    F = []
    F += part("funnel", tube("spin", "soot", [(0, 1.0, 0), (0, 10.0, 0), (0, 21.0, 0)], ra=[1.2, 3.0, 4.6], sides=8, hint=(1, 0, 0)))
    swirl(F, "ring0", 4.0, 2.2, 4.0)
    swirl(F, "ring1", 11.0, 3.6, 4.4)
    swirl(F, "ring2", 18.0, 5.0, 4.4, n=10)
    F += part("core", ellipsoid("core", "ember", (0, 12.0, -2.4), (1.8, 1.8, 1.4), segs=7, rings=2))
    F += part("mask", ellipsoid("mask", "clay", (0, 23.6, -3.2), (2.4, 2.8, 1.2), segs=8, rings=2))
    F += lumps("ring2", "ember", [((4.0, 20.0, 2.0), 0.4), ((-4.6, 17.0, -1.4), 0.4), ((1.4, 21.4, -4.6), 0.35)])
    return F, [eye_at(-0.9, 24.4, -4.2, 0.5), eye_at(0.9, 24.4, -4.2, 0.5)]


def build_b(k):
    """B 灰被りの舞い手"""
    F = []
    # 衣 (下へ広がる裾は灰の渦にほどける)
    F += part("robe", tube("spin", "cloth", [(0, 1.6, 0), (0, 10.0, 0), (0, 19.0, 0)], ra=[6.0, 3.6, 2.6], sides=9, hint=(1, 0, 0)))
    swirl(F, "ring0", 2.4, 6.0, 2.6, n=10, mat_="ash", tw=40.0)
    F += part("chest", ellipsoid("ring1", "cloth", (0, 18.6, 0), (3.4, 3.2, 2.4), segs=8, rings=2))
    F += part("hood", ellipsoid("mask", "cloth", (0, 23.4, 0.4), (2.8, 3.0, 2.8), segs=8, rings=2))
    F += part("mask", ellipsoid("mask", "soot", (0, 23.0, -2.0), (1.8, 2.2, 0.8), segs=7, rings=2))
    # 長い袖 (水平に広げる) と熾の袖口
    for sx in (1, -1):
        F += part(f"sleeve{sx}", tube("ring2", "cloth", [(sx * 3.0, 19.6, 0), (sx * 7.0, 19.0, -0.6), (sx * 11.0, 17.4, 0.6)], ra=[1.4, 1.6, 2.2], sides=6, hint=(0, 1, 0)))
        F += part(f"cuff{sx}", ellipsoid("ring2", "ember", (sx * 11.6, 17.2, 0.6), (0.8, 1.6, 1.6), segs=6, rings=1))
    F += part("core", ellipsoid("core", "ember", (0, 13.0, -3.4), (1.2, 1.2, 0.8), segs=6, rings=1))
    return F, [eye_at(-0.7, 23.6, -2.7, 0.45), eye_at(0.7, 23.6, -2.7, 0.45)]


def build_c(k):
    """C 灰の壺の精"""
    F = []
    F += part("pot", ellipsoid("ring0", "clay", (0, 6.0, 0), (4.2, 4.0, 4.2), segs=9, rings=3))
    F += part("neck", tube("ring0", "clay", [(0, 9.4, 0), (0, 11.0, 0)], ra=[2.2, 2.6], sides=8, hint=(1, 0, 0)))
    for i, (x, y, z) in enumerate(((2.6, 6.4, -3.0), (-3.0, 5.0, -2.4), (0.4, 3.6, 3.8))):
        F += part(f"crack{i}", box("ring0", "ember", (x, y, z), (0.6, 2.4, 0.5)))
    F += part("funnel", tube("spin", "soot", [(0, 10.6, 0), (0, 16.0, 0), (0, 22.0, 0)], ra=[1.6, 2.8, 4.4], sides=8, hint=(1, 0, 0)))
    swirl(F, "ring1", 14.0, 3.0, 4.0)
    swirl(F, "ring2", 20.0, 4.4, 4.0, n=10)
    F += part("core", ellipsoid("core", "ember", (0, 11.6, 0), (1.6, 1.0, 1.6), segs=7, rings=1))
    F += part("mask", ellipsoid("mask", "clay", (0, 24.0, -2.4), (2.0, 2.2, 1.0), segs=7, rings=2))
    return F, [eye_at(-0.8, 24.6, -3.3, 0.45), eye_at(0.8, 24.6, -3.3, 0.45)]


def detail(F):
    # 細部: 壺の口縁と取っ手・帯の模様・灰の粒・面の割れ目の熾
    F += part("rim", tube("ring0", "clay", [(0, 10.9, 0), (0, 11.4, 0)], ra=[2.9, 2.9], sides=10, hint=(1, 0, 0)))
    for s in (1, -1):
        F += part(f"handle{s}", tube("ring0", "clay", [(s * 3.6, 8.4, 0), (s * 4.8, 7.4, 0), (s * 4.4, 5.6, 0), (s * 3.8, 5.0, 0)], ra=[0.4, 0.4, 0.4, 0.4], sides=5, hint=(0, 0, 1)))
    for i, y in enumerate((4.2, 7.6)):
        F += part(f"band{i}", tube("ring0", "soot", [(0, y, 0), (0, y + 0.4, 0)], ra=[4.25 - abs(y - 6) * 0.1, 4.25 - abs(y - 6) * 0.1], sides=10, hint=(1, 0, 0)))
    for i in range(10):
        a = math.radians(i * 37)
        F += part(f"mote{i}", ellipsoid("ring2", "ash", (5.6 * math.cos(a), 16 + (i % 4) * 2.4, 5.6 * math.sin(a)), (0.4, 0.4, 0.4), segs=4, rings=1))
    F += part("maskcrack", box("mask", "ember", (0.6, 24.4, -3.35), (0.3, 1.8, 0.2)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "c":
        F = detail(F)
    return F, eyes


POSES = {
    "idle": {},
    "spin": {"spin": (0, 45, 0), "ring0": (0, 40, 0), "ring1": (0, -30, 0), "ring2": (0, 50, 0)},
    "burst": {"spin": (0, 90, 0), "ring0": (-10, 0, 0), "ring2": (14, 0, 0), "mask": (-14, 0, 0)},
}

SPEC = dict(
    id="ash_whirl", short="灰の渦", kind="灰の敵 3案 (まわりに効く)", role="まわりに効く", land="灰",
    title="灰の渦 (ash_whirl) — 灰の荒野・まわりに効く・低ポリ 3 案",
    sub=["寄ると回り始め (予告 1.2 秒: 灰の輪が速くなり、足もとに半径 4.5 の円)、円の中の人を引き寄せて灰を吹き上げる (傷 + 鈍足 2 秒 + 目くらみ 1 秒)。",
         "吹いたあと 2 秒は芯が出て止まる (芯を叩くと傷 +50%)。予告を見て円の外へ出て、止まったところを叩く役。"],
    labels=["A 灰の竜巻の精", "B 灰被りの舞い手", "C 灰の壺の精"],
    rows=rows_std(12, 10.0, h=360, extra=(("予告: 回り始める", "spin"), ("吹き上げ", "burst"))),
    options={
        "a": dict(name="A 灰の竜巻の精", desc="灰の輪が三段に重なった竜巻、中に熾の芯、上に素焼きの面。回る速さがそのまま予告になる。形は一番抽象的。"),
        "b": dict(name="B 灰被りの舞い手", desc="長い袖の灰の衣の亡霊が回る。袖の先が熾、裾は灰の渦にほどける。人の形なので、灰の荒野の『昔の人』の物語に寄せられる。"),
        "c": dict(name="C 灰の壺の精", desc="宙に浮く割れた素焼きの壺から灰の渦が立つ。止まると壺が落ちて芯が見える (叩く所が分かりやすい)。")},
    extra="骨 7 本 (root > spin > core / ring0..2 / mask)。輪ごとに違う速さで回す。",
    budget="地上の敵 400〜700", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
