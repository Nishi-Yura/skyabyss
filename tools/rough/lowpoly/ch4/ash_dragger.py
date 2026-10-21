"""灰ひきずり (ash_dragger) 低ポリ ラフ 3 案。追いかけっこの相手 (敵意なし)。HP 20。
灰を引きずって歩く大きな虫。人に気づくと丸まって転がって逃げる。追うと煤の窯の跡へ導く (燠虫の転がりの小さな予習)。
A 灰袋の団子虫 (節の甲に灰の袋を引く) / B 灰毛の芋虫 (灰の毛の房、丸まると毛の輪) / C 煤板の鎧蟲 (煤けた板の甲、板の隙間に熾、尾の板が灰を掻く)
骨: root > roll (玉の中心) > body > head / tail / leg_l1..3 / leg_r1..3。丸まる = head と tail を下へ折る (脚は隠す)、転がる = roll を回す。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck4 import *  # noqa

PY = 4.2
LEGS = [("l", -1, 1), ("l", -1, 2), ("l", -1, 3), ("r", 1, 1), ("r", 1, 2), ("r", 1, 3)]
LEG_Z = {1: -3.0, 2: 0.0, 3: 3.0}
BONES = {"root": (None, (0, 0, 0)), "roll": ("root", (0, PY, 0)), "body": ("roll", (0, PY, 0)),
         "head": ("body", (0, 4.0, -5.0)), "tail": ("body", (0, 4.0, 5.0))}
for nm, s, i in LEGS:
    BONES[f"leg_{nm}{i}"] = ("body", (s * 3.6, 2.2, LEG_Z[i]))
PAL = {"mats": {
    "plate": mat(BASALT, 0.6, crack=0.1, noise=0.18, stain=dict(ramp=ASH, thr=0.9, up=0.6, conc=0.3)),
    "ash": mat(ASH, 0.62, crack=0.0, noise=0.25, dots=0.12),
    "soot": mat(SOOT, 0.5, crack=0.04, noise=0.16),
    "hemp": mat(HEMP, 0.6, crack=0.0, noise=0.2, stripe=3, axis="v"),
    "ember": mat(EMBER, 0.72, crack=0.0, noise=0.06, min=1),
    "belly": mat(RUST, 0.56, crack=0.0, noise=0.12),
    "leg": mat(SOOT, 0.5, crack=0.0, noise=0.1),
}, "skull_bone": "none", "face_mat": "none", "glow_mats": ["ember"]}


def eye_at(x, y, z, r=0.5):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.8, pr=(0.5, 0.4), tilt=0.0, zr=0.8)


def legs(F, r=0.4):
    for nm, s, i in LEGS:
        b = f"leg_{nm}{i}"
        z0 = LEG_Z[i]
        F += part(b, tube(b, "leg", [(s * 3.4, 2.4, z0), (s * 4.8, 1.8, z0 + (i - 2) * 0.6), (s * 5.2, 0.0, z0 + (i - 2) * 1.0)],
                          ra=[r, r * 0.8, 0.1], sides=3, hint=(0, 1, 0)))


def segments(F, mat_, n=5, w=4.0, h=3.0, z0=-4.4, z1=4.4, bone="body", ridge=None):
    """背の節の甲 (前後に並ぶ楕円体。節の間は少し重ねて沈める)。"""
    for i in range(n):
        t = i / (n - 1)
        z = z0 + (z1 - z0) * t
        k = 1.0 - 0.25 * abs(t - 0.5) * 2
        F += part(f"seg{i}", ellipsoid(bone, mat_, (0, 4.0, z), (w * k, h * k, 1.5), segs=8, rings=2))
        if ridge:
            F += part(f"ridge{i}", ellipsoid(bone, ridge, (0, 4.0 + h * k - 0.2, z), (w * k * 0.35, 0.35, 0.6), segs=4, rings=1))


def build_a(k):
    """A 灰袋の団子虫: 灰青の節の甲 (5 節)、腹は煤けた赤。尾に灰麻の袋を結び、袋から灰がこぼれて跡になる。丸まると玉、袋は後ろに残る。"""
    F = []
    segments(F, "plate", ridge="ash")
    F += part("belly", ellipsoid("body", "belly", (0, 2.6, 0.0), (3.2, 1.2, 4.8), segs=8, rings=1))
    F += part("head", ellipsoid("head", "soot", (0, 3.4, -6.0), (2.2, 1.7, 1.4), segs=6, rings=1))
    for s in (-1, 1):
        F += part(f"ant{s}", tube("head", "leg", [(s * 0.9, 4.2, -6.9), (s * 2.4, 5.8, -8.6), (s * 3.2, 6.4, -9.6)], ra=[0.25, 0.2, 0.05], sides=3))
    F += part("cord", tube("tail", "hemp", [(0, 3.6, 5.6), (0, 2.8, 7.2), (0, 2.2, 8.4)], ra=0.3, sides=3))
    F += part("sack", ellipsoid("tail", "hemp", (0, 2.2, 9.8), (2.2, 1.9, 2.0), segs=6, rings=2))
    F += part("spill", lumps("tail", "ash", [((0.6, 3.8, 9.6), 0.8), ((-0.7, 3.6, 10.6), 0.6)]))
    legs(F)
    return F, [eye_at(-1.2, 3.8, -7.2), eye_at(1.2, 3.8, -7.2)]


def build_b(k):
    """B 灰毛の芋虫: 煤色の太い芋虫の背に灰の毛の房が並ぶ。房が揺れて灰が落ちる。丸まると毛の輪になって転がる。"""
    F = []
    F += part("body", tube("body", "soot", [(0, 3.6, -5.0), (0, 3.9, -1.5), (0, 3.9, 2.0), (0, 3.4, 5.6)], ra=[2.4, 2.9, 2.8, 2.0], rb=[2.2, 2.7, 2.6, 1.9],
                           sides=6, hint=(1, 0, 0), caps=(False, False)))
    for i, z in enumerate((-3.6, -1.2, 1.2, 3.6)):
        for s in (-1, 0, 1):
            F += part(f"tuft{i}{s}", spike("body", "ash", (s * 1.4, 6.1 - abs(s) * 0.4, z), (s * 0.5, 1, 0.2), 2.6, 0.8, sides=4, rings=1))
    F += part("head", ellipsoid("head", "soot", (0, 3.4, -6.2), (2.0, 1.9, 1.6), segs=6, rings=1))
    F += part("mask", ellipsoid("head", "ember", (0, 3.2, -7.6), (0.9, 0.5, 0.3), segs=5, rings=1))
    F += part("tail", spike("tail", "soot", (0, 3.4, 5.4), (0, -0.3, 1), 2.6, 1.8, sides=5, rings=1))
    legs(F, r=0.45)
    return F, [eye_at(-1.0, 4.2, -7.4, 0.45), eye_at(1.0, 4.2, -7.4, 0.45)]


def build_c(k):
    """C 煤板の鎧蟲: 煤けた黒い板の甲 (6 枚)、板の隙間に熾がのぞく。尾の二枚の板が灰を掻いて跡を残す。燠虫を小さく、丸く穏やかにした形。"""
    F = []
    segments(F, "soot", n=6, w=4.2, h=3.2, ridge=None)
    for i in range(5):
        z = -3.6 + 1.8 * i
        F += part(f"gap{i}", ellipsoid("body", "ember", (0, 6.0 - 0.3 * abs(i - 2), z), (2.2, 0.3, 0.25), segs=5, rings=1))
    F += part("belly", ellipsoid("body", "belly", (0, 2.5, 0.0), (3.2, 1.1, 4.6), segs=8, rings=1))
    F += part("head", ellipsoid("head", "plate", (0, 3.2, -6.0), (2.4, 1.6, 1.3), segs=6, rings=1))
    for s in (-1, 1):
        F += part(f"rake{s}", spike("tail", "plate", (s * 1.4, 3.0, 5.8), (s * 0.4, -0.6, 1), 3.0, 0.8, sides=3, rings=1))
    legs(F)
    return F, [eye_at(-1.3, 3.6, -7.1), eye_at(1.3, 3.6, -7.1)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "curl": {"head": (80, 0, 0), "tail": (-80, 0, 0), "leg_l1": (0, 0, 60), "leg_l2": (0, 0, 60), "leg_l3": (0, 0, 60),
             "leg_r1": (0, 0, -60), "leg_r2": (0, 0, -60), "leg_r3": (0, 0, -60)},
    "roll": {"roll": (90, 0, 0), "head": (80, 0, 0), "tail": (-80, 0, 0)},
}

SPEC = dict(
    id="ash_dragger", short="灰ひきずり", kind="地上の敵 3案 (P20)",
    title="灰ひきずり (ash_dragger) — 追いかけっこの虫 (敵意なし)・低ポリ + ドット絵 3 案",
    sub=["四章の地上の生き物。灰を引きずって歩き、跡を残す。人に気づくと丸まって転がって逃げ、追うと煤の窯の跡へ導く (燠虫の転がりの小さな予習)。HP 20。",
         "叩くと灰麻か煤鉄を落とす。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 灰袋の団子虫", "B 灰毛の芋虫", "C 煤板の鎧蟲"],
    rows=[("前 3/4", "idle", 212, 16, 300, (0, 4, 0), 14.0),
          ("横 (引きずる跡は後ろ)", "idle", 270, 6, 300, (0, 4, 0), 14.0),
          ("丸まる (人に気づいた瞬間)", "curl", 220, 14, 300, (0, 4, 0), 14.0),
          ("転がる (玉になって逃げる)", "roll", 270, 8, 300, (0, 4, 0), 14.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 4, 0), 14.0)],
    options={
        "a": dict(name="A 灰袋の団子虫", desc="灰青の節の甲の団子虫が、尾に結んだ灰麻の袋を引きずる (名前どおり「灰を引きずる」)。袋から灰がこぼれて跡になり、追う道が地面に見える。丸まると玉、落とす灰麻と形がつながる。"),
        "b": dict(name="B 灰毛の芋虫", desc="煤色の太い芋虫の背に灰の毛の房が並び、歩くと灰が落ちる。顔に熾の仮面。丸まると毛の輪になって転がる。柔らかく、追いかけたくなる見た目。"),
        "c": dict(name="C 煤板の鎧蟲", desc="煤けた黒い板の甲 (6 枚) の隙間に熾がのぞき、尾の二枚の板が灰を掻いて跡を残す。燠虫を小さく丸く穏やかにした形で、背中の燠虫の予習が一番伝わる。")},
    extra="四章の言葉 (灰・煤・黒石・熾)。骨 11 本 (root > roll > body > head / tail / leg_l1..3 / leg_r1..3)。丸まると脚は隠す (燠虫と同じ描き方)。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
