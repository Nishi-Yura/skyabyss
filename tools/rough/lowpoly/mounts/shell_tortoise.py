"""殻の亀 (200 鐘銭、二章) ラフ 3 案。潮の殻の巨神 (二章) の小さな子。殻に珊瑚が生える。
A 珊瑚の丸甲亀 (高い丸い甲羅、側面に珊瑚の茂み、頭の上は鞍) / B 海亀 (平たく広い甲羅、大きなひれ脚、背の後ろに珊瑚の扇の庭)
C 巻き貝亀 (背に渦巻きの殻の塔。人は殻の前、体の上に座る。大きなはさみのような前脚)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def a(k):
    F = []
    F += part("shell", E("shellc", (0, 20, 0), (13, 10, 17), segs=12, rings=4))
    F += part("rim", E("barn", (0, 12.4, 0), (13.6, 2.4, 17.6), segs=12, rings=2))
    F += part("plast", E("skin", (0, 10.4, 0), (10, 3.2, 14), segs=10, rings=2))
    for i, (z, rr) in enumerate(((-7.5, 11.6), (0, 12.6), (7.5, 11.0))):
        F += part(f"scute{i}", E("stonew", (0, 28.4 - abs(z) * 0.1, z), (7.0 - abs(z) * 0.2, 2.2, 3.6), segs=7, rings=2))
    for i, (x, z) in enumerate(((-8.6, -10), (8.6, -10), (-8.6, 10), (8.6, 10))):
        F += part(f"leg{i}", T("skin", [(x, 13, z), (x * 1.04, 2.8, z)], [4.6, 3.8], sides=6))
        F += part(f"ft{i}", E("barn", (x * 1.04, 1.6, z - 0.8), (4.4, 1.7, 5.2), segs=7, rings=2))
    F += part("neck", T("skin", [(0, 15, -14), (0, 17, -22)], [4.4, 3.6], sides=6))
    F += part("head", ER("skin", (0, 17.4, -26), (4.2, 3.8, 5.6), segs=8, rings=3))
    F += part("beak", ER("barn", (0, 15.6, -31), (2.6, 1.6, 2.6), segs=6, rings=2))
    F += part("tail", T("skin", [(0, 14, 15), (0, 11, 22)], [2.6, 0.6], sides=5, caps=(True, True)))
    for i, (x, z, h) in enumerate(((-10.4, -4, 11), (10.4, 5, 12), (-9.6, 8, 9), (10, -8, 8))):
        yb = 20 + 10 * max(0.1, 1 - (x / 13) ** 2 - (z / 17) ** 2) ** 0.5
        F += coral(f"cor{i}", "coral" if i % 2 == 0 else "coralb", x * 0.86, yb - 2.5, z, h, tip_m="coral", lean=(math.copysign(2.0, x), 0), n=3, r=1.5)
    F += coral("cor_r", "coralb", 0, 17, 15.4, 7, n=3, r=1.3, lean=(0, 1))
    eyes = [eye_at(s * 3.4, 18.4, -29.0, 0.8) for s in (-1, 1)]
    return finish(F, (0, 29.8, 0), girth=gq(20, 13, 10, 17, 0, 0), drop=17.0, w=6.0), eyes


def b(k):
    F = []
    F += part("shell", E("shellc", (0, 14.5, 0), (14, 6, 19), segs=12, rings=3))
    F += part("rim", E("barn", (0, 10.4, 0), (14.8, 2.0, 19.8), segs=12, rings=2))
    F += part("plast", E("skin", (0, 8.4, 0), (11, 3.0, 15), segs=10, rings=2))
    for s in (-1, 1):
        F += part(f"fflip{s}", Ar4("skin", [(s * 11, 10, -8), (s * 19, 7, -13), (s * 26, 2.8, -16)], (4.4, 3.0), sides=6))
        F += part(f"fpad{s}", ER("barn", (s * 27, 1.4, -17.4), (3.2, 1.4, 6.0), ry=s * 24, segs=7, rings=2))
        F += part(f"hflip{s}", Ar4("skin", [(s * 9, 9, 12), (s * 14, 6, 16), (s * 18, 2.8, 17)], (3.8, 2.8), sides=6))
        F += part(f"hpad{s}", ER("barn", (s * 19, 1.4, 16.4), (2.8, 1.4, 4.4), ry=s * 24, segs=7, rings=2))
    F += part("neck", T("skin", [(0, 11, -16), (0, 13, -25)], [4.6, 3.8], sides=6))
    F += part("head", ER("skin", (0, 13.4, -29), (4.2, 3.6, 6.6), segs=8, rings=3))
    F += part("beak", ER("barn", (0, 11.8, -35), (2.6, 1.4, 2.8), segs=6, rings=2))
    F += part("tail", T("skin", [(0, 11, 15), (0, 7, 25)], [3.0, 0.6], sides=5))
    for i in range(6):
        z = 3 + i * 3.4
        x = (-1 if i % 2 == 0 else 1) * (4.2 + (i % 3) * 1.8)
        yb = 14.5 + 6 * max(0.1, 1 - (x / 14) ** 2 - (z / 19) ** 2) ** 0.5
        F += coral(f"fan{i}", "coral" if i % 2 else "coralb", x, yb - 2.2, z + 3, 8 + (i % 3), tip_m="coral", n=3, r=1.3, lean=(0, 0.5))
    for i, (x, z) in enumerate(((-9, -6), (9, -4), (-8, -12), (8, -12))):
        yb = 14.5 + 6 * max(0.1, 1 - (x / 14) ** 2 - (z / 19) ** 2) ** 0.5
        F += part(f"barn{i}", C("barn", (x, yb - 1.4, z), (0, 1, 0), 3.4, 1.6, sides=5, rings=2))
    eyes = [eye_at(s * 3.4, 14.4, -32.4, 0.8) for s in (-1, 1)]
    return finish(F, (0, 19.6, -5), girth=gq(14.5, 14, 6, 19, 0, -5), drop=12.0, w=6.4), eyes


def c(k):
    F = []
    F += part("trunk", E("skin", (0, 12, -2), (8.6, 6.0, 14), segs=10, rings=3))
    F += part("whorl1", ER("shellc", (0, 24, 8), (11, 11, 12.5), rx=-10, segs=11, rings=4))
    F += part("whorl2", ER("shellc", (1.5, 34.6, 9.4), (8.2, 8.2, 9.2), rx=-14, segs=10, rings=3))
    F += part("whorl3", ER("shellc", (2.6, 43, 10.4), (5.6, 5.6, 6.2), rx=-18, segs=9, rings=3))
    F += part("spire", C("barn", (3.2, 47.4, 10.8), (0.1, 1, 0.2), 9, 3.2, sides=6, rings=3))
    for i, (yc, rr, zc, xc) in enumerate(((24, 11, 8, 0), (34.6, 8.2, 9.4, 1.5))):
        F += part(f"ridge{i}", E("barn", (xc, yc + rr * 0.98, zc), (3.0, 1.4, 5.8), segs=7, rings=2))
        F += part(f"ridgef{i}", E("barn", (xc, yc, zc - rr * 0.98 * 1.1), (rr * 0.5, rr * 0.4, 2.4), segs=7, rings=2))
    for i, (x, z) in enumerate(((-7.2, -10), (7.2, -10), (-7.2, 8), (7.2, 8))):
        F += part(f"leg{i}", T("skin", [(x, 10, z), (x * 1.08, 2.6, z)], [4.0, 3.2], sides=6))
        F += part(f"ft{i}", E("barn", (x * 1.08, 1.6, z - 0.8), (4.0, 1.7, 4.8), segs=7, rings=2))
    F += part("neck", T("skin", [(0, 14, -13), (0, 17, -21)], [4.6, 3.6], sides=6))
    F += part("head", ER("skin", (0, 18, -25), (4.2, 3.8, 5.4), segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"stalk{s}", T("skin", [(s * 2.4, 20.4, -24), (s * 3.4, 24.6, -26)], [1.1, 0.9], sides=5))
        F += part(f"eyeb{s}", Bl("barn", (s * 3.4, 25.2, -26.2), 1.9, segs=6, rings=2))
        F += part(f"claw{s}", T("coralb", [(s * 7, 11, -11), (s * 11, 8, -19), (s * 10, 5, -26)], [3.4, 3.0, 2.6], sides=6))
        F += part(f"pincer{s}", ER("coral", (s * 9.8, 4.4, -29), (3.4, 2.6, 4.6), segs=7, rings=2))
    for i, (x, y, z, h) in enumerate(((-10.0, 32, 8, 8), (9.8, 28, 4, 9), (9, 40, 11, 6))):
        F += coral(f"cor{i}", "coral" if i % 2 == 0 else "coralb", x * 0.84, y - 1.4, z, h, n=3, r=1.3, lean=(math.copysign(2.0, x), 0))
    F += part("tail", T("skin", [(0, 10, 11), (0, 7, 16)], [3.0, 0.6], sides=5))
    eyes = []
    return finish(F, (0, 17.4, -6), girth=gq(12, 8.6, 6.0, 14, -2, -6), drop=12.0, w=5.4, lean=1.0), [eye_at(s * 3.6, 19, -28.0, 0.8) for s in (-1, 1)]


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="shell_tortoise", short="殻の亀", kind="乗り物 3案 (200 鐘銭、二章)",
    title="乗り物「殻の亀」— 潮の殻の巨神の子。殻に珊瑚が生える",
    sub=["二章の潮の殻の小さな親戚。甲羅に珊瑚が生え、足は水かきのよう。左の黄色い目盛りが人 (1.8 ブロック)。",
         "一番下の行は、鞍に人が座った姿。"],
    options={
        "a": dict(name="A 珊瑚の丸甲亀", desc="高く丸い甲羅。側面に珊瑚の茂み、頂に鞍。四本の太い柱の脚。"),
        "b": dict(name="B 海亀", desc="平たく広い甲羅と、大きなひれの脚。背の後ろに珊瑚の扇が並ぶ庭。"),
        "c": dict(name="C 巻き貝亀", desc="背に渦巻きの殻の塔。人は殻の前に座る。はさみのような大きな前脚。"),
    },
    pw=380, rows=rows_for((0, 26, 0), 4.2),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(20, 30, 30), (230, 240, 230)])
