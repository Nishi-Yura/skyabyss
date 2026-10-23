"""角の荷獣 (はじめから持つ乗り物) ラフ 3 案。世界になじむ、丈夫で素朴な荷運びの獣。
A 毛むくじゃらの牛 (肩のこぶ、前へ張る大きな角、荷袋) / B 鎧の猪 (低い楔形、骨の板を背に、鼻の大きな一本角)
C 渦角の山羊 (背が高くしなやか、巻いた角、たてがみ、荷を巻いた縄)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def a(k):
    F = []
    F += part("body", E("hide", (0, 20, 2), (9, 8.5, 15), segs=10, rings=4))
    F += part("hump", E("hide", (0, 25, -6), (7.5, 6, 7.5), segs=9, rings=3))
    F += part("skirt", E("hided", (0, 12, 2), (10, 4.4, 13), segs=10, rings=2))
    for i, (x, z) in enumerate(((-6, -8), (6, -8), (-6, 10), (6, 10))):
        F += pillar(f"leg{i}", "hide", x, 16, z, 4.2, 3.0, foot_m="hided", foot=(3.8, 1.8, 4.4))
        F += part(f"cuff{i}", E("hided", (x, 13, z), (4.8, 3.4, 4.8), segs=7, rings=2))
    F += part("neck", T("hide", [(0, 26, -13), (0, 20, -22)], [6.4, 5.2], sides=7))
    F += part("head", E("hide", (0, 19, -26), (5, 5, 7.5), segs=8, rings=3))
    F += part("muzzle", E("hided", (0, 17.5, -32), (3.8, 3.4, 3.2), segs=7, rings=2))
    for s in (-1, 1):
        F += part(f"horn{s}", Ar4("cream", [(s * 4, 24, -24), (s * 9, 23.6, -26.5), (s * 13, 26, -29.5), (s * 14, 32, -33.5)], (2.2, 0.0), sides=5))
        F += part(f"ear{s}", ER("hided", (s * 6.4, 23, -23), (1.6, 2.6, 3.0), rz=s * 30, segs=6, rings=2))
    F += part("tail", Ar("hided", (0, 24, 16), (0, 20, 22), (0, 9, 24), (1.8, 1.2)))
    F += part("tuft", E("hided", (0, 7.5, 24), (2.6, 4.4, 2.6), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"bag{s}", E("leath", (s * 11, 19, 8), (3.2, 5.6, 6), segs=7, rings=2))
        F += part(f"bagt{s}", T("leath", [(s * 9, 24, 8), (s * 11, 22, 8)], [0.9, 0.9], sides=4))
    F += part("bell_strap", T("leath", [(-4.5, 22, -19.2), (0, 16.6, -19.2), (4.5, 22, -19.2)], [1.0, 1.0, 1.0], sides=4))
    F += part("bell", Bl("brass", (0, 15.6, -19.4), 1.9, segs=6, rings=2))
    eyes = [eye_at(s * 4.4, 21, -28.2, 0.8) for s in (-1, 1)]
    return finish(F, (0, 28.2, 6), girth=gq(20, 9, 8.5, 15, 2, 6)), eyes


def b(k):
    F = []
    F += part("chest", E("hide", (0, 19, -5), (10, 10, 12), segs=10, rings=4))
    F += part("rear", E("hide", (0, 18, 9), (8.6, 8.6, 11), segs=10, rings=3))
    for i in range(4):
        z = -8 + i * 6
        F += part(f"plate{i}", E("cream", (0, 27.6 - (0.6 if i in (1, 2) else 1.4), z), (6.4 - i * 0.2, 2.2, 3.0), segs=7, rings=2))
    F += part("shield", E("cream", (0, 22, 19.4), (6.0, 6.0, 1.8), segs=8, rings=2))
    for i, (x, z, r) in enumerate(((-6.5, -9, 4.6), (6.5, -9, 4.6), (-5.6, 11, 4.2), (5.6, 11, 4.2))):
        F += pillar(f"leg{i}", "hided", x, 14, z, r, r * 0.8, foot_m="cream", foot=(4.2, 1.8, 4.6))
    F += part("head", ER("hide", (0, 14, -24), (6.4, 6.0, 10), rx=-8, segs=9, rings=3))
    F += part("snout", ER("hided", (0, 11.2, -32), (4.6, 3.6, 4.2), rx=-8, segs=7, rings=2))
    F += part("nhorn", Ar4("cream", [(0, 15.5, -31), (0, 19, -35), (0, 24, -37.5)], (3.2, 0.0), sides=6))
    for s in (-1, 1):
        F += part(f"tusk{s}", Ar4("cream", [(s * 2.6, 10.8, -31.0), (s * 5.4, 8.4, -34.5), (s * 5.8, 12, -38)], (1.6, 0.0), sides=4))
        F += part(f"ear{s}", ER("hided", (s * 3.8, 18.6, -21), (1.4, 3.0, 2.0), rz=s * 24, segs=6, rings=2))
        F += part(f"brow{s}", E("cream", (s * 4.4, 17.4, -26.6), (2.2, 1.2, 2.6), segs=6, rings=2))
    F += part("tail", Ar("hided", (0, 20, 19), (0, 16, 24), (0, 7, 25), (1.2, 0.8)))
    F += part("tuft", E("hided", (0, 5.5, 25), (2.0, 3.6, 2.0), segs=6, rings=2))
    F += part("roll", T("leath", [(-9.4, 26.5, 14), (9.4, 26.5, 14)], [3.6, 3.6], sides=7, hint=(0, 1, 0)))
    eyes = [eye_at(s * 5.2, 15.6, -27.4, 0.8) for s in (-1, 1)]
    return finish(F, (0, 26.4, 4), girth=gq(19, 10, 10, 12, -5, 4)), eyes


def c(k):
    F = []
    F += part("body", E("ashf", (0, 24, 1), (7.5, 8, 14), segs=10, rings=4))
    F += part("mane", E("cream", (0, 29, -11), (8.2, 7.2, 6.4), segs=9, rings=3))
    F += part("rumpw", E("cream", (0, 20, 12), (7.4, 6, 5), segs=8, rings=2))
    for i, (x, z) in enumerate(((-4.6, -9), (4.6, -9), (-4.6, 11), (4.6, 11))):
        hind = z > 0
        F += leg(f"leg{i}", "ashf", (x, 20, z), (x, 11.5, z + (3 if hind else -1)), (x, 4.6, z + (1 if hind else -1)), (x, 0, z - 1.4),
                 3.4, 2.2, 1.6, foot_m="hided", foot=(2.6, 1.6, 3.6))
    F += part("neck", T("ashf", [(0, 28, -11), (0, 33, -17)], [5.4, 4.2], sides=7))
    F += part("head", ER("ashf", (0, 33.5, -21), (4.0, 4.6, 6.4), rx=-14, segs=8, rings=3))
    F += part("muzzle", ER("cream", (0, 31, -26), (3.0, 2.8, 2.8), rx=-14, segs=7, rings=2))
    for s in (-1, 1):
        pts = []
        for j in range(9):
            t = j / 8.0
            a_ = 1.6 * math.pi * t
            R = 6.0 * (1 - 0.45 * t)
            pts.append((s * (3.6 + R * math.sin(a_)), 36 + R * 0.8 * (1 - math.cos(a_)) - 2.5 * t, -21 + R * math.cos(a_) - R + 3.2))
        F += part(f"horn{s}", Ar4("cream", pts, (2.0, 0.0), sides=5))
        F += part(f"ear{s}", ER("ashf", (s * 5.4, 36.4, -22), (1.2, 2.8, 1.8), rz=s * 55, segs=6, rings=2))
    F += part("tail", E("cream", (0, 22, 17), (1.8, 2.4, 3.0), segs=6, rings=2))
    F += part("roll", T("leath", [(-8.2, 33.2, 15), (8.2, 33.2, 15)], [3.0, 3.0], sides=7))
    F += part("collar", T("brass", [(-4.4, 30, -14.6), (0, 27, -14.6), (4.4, 30, -14.6)], [1.0, 1.0, 1.0], sides=4))
    eyes = [eye_at(s * 3.8, 34.6, -23.4, 0.8) for s in (-1, 1)]
    return finish(F, (0, 31.6, 4), girth=gq(24, 7.5, 8, 14, 1, 4), drop=17.0), eyes


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="horn_beast", short="角の荷獣", kind="乗り物 3案 (はじめから)",
    title="乗り物「角の荷獣」— はじめから持つ丈夫な荷運びの獣",
    sub=["世界になじむ素朴な獣。性能は同じで、姿と座る高さだけが違う。左の黄色い目盛りが人 (1.8 ブロック)。",
         "一番下の行は、鞍に人が座った姿。座面は背の上に半分沈めてある。"],
    options={
        "a": dict(name="A 毛むくじゃらの牛", desc="肩にこぶのある長毛の牛。前へ張る太い角と、脇に荷袋。胸の鈴が鳴る。"),
        "b": dict(name="B 鎧の猪", desc="低い楔形の体。骨の板を背に並べ、鼻に大きな一本角と牙。頑丈さ重視。"),
        "c": dict(name="C 渦角の山羊", desc="背の高いしなやかな山羊。巻いた角と首のたてがみ。背に荷を巻いた縄。"),
    },
    pw=380, rows=rows_for((0, 20, -2), 6.2),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(60, 40, 20), (230, 220, 190)])
