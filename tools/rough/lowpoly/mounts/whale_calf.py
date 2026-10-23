"""岩の子鯨 (120 鐘銭、一章を越えたら) ラフ 3 案。石の鯨 (一章の巨神) の小さな子。背にフジツボの石。
A 四つ足の子鯨 (丸い体、四枚のひれ脚、背の鞍の周りにフジツボの石、尾びれ) / B ひれで歩く子鯨 (前ひれで体を起こし、後ろは尾を引く。鞍はフジツボの輪の中)
C 角の子鯨 (一角鯨の子。長い巻き角、柱の脚にフジツボの輪、鞍の脇にフジツボのかご)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def barn_cluster(F, name, x, y, z, n, h, spread, m="barn"):
    for i in range(n):
        a_ = i * 2.4
        dx, dz = math.sin(a_) * spread * (0.4 + 0.6 * ((i * 37) % 10) / 10), math.cos(a_) * spread * (0.4 + 0.6 * ((i * 53) % 10) / 10)
        hh = h * (0.6 + 0.4 * ((i * 29) % 10) / 10)
        F += part(f"{name}{i}", C(m, (x + dx, y - 1.0, z + dz), (dx * 0.04, 1, dz * 0.04), hh, 1.5 + 0.4 * (i % 2), sides=5, rings=2))


def flukes(F, base, m="stonew", y_off=0.0):
    x, y, z = base
    for s in (-1, 1):
        F += part(f"fluke{s}", ER(m, (s * 4.6, y + y_off, z + 2.4), (7.4, 0.9, 3.8), ry=s * 18, segs=8, rings=2))


def a(k):
    F = []
    F += part("body", E("stonew", (0, 22, 0), (11, 10, 18), segs=12, rings=4))
    F += part("head", E("stonew", (0, 23, -15.5), (9.6, 10, 9.6), segs=10, rings=3))
    F += part("belly", E("barn", (0, 15.6, -3), (8, 4.6, 16), segs=10, rings=2))
    F += part("jaw", E("barn", (0, 15.0, -20), (6.6, 2.6, 7.4), segs=8, rings=2))
    F += part("blow", T("cinder", [(0, 31.4, -14), (0, 33.4, -14)], [2.4, 1.6], sides=6))
    for s in (-1, 1):
        F += part(f"eye{s}", E("barn", (s * 8.4, 22.6, -18), (1.7, 2.1, 2.1), segs=6, rings=2))
    for i, (x, z) in enumerate(((-7, -10), (7, -10), (-7, 11), (7, 11))):
        F += part(f"fl{i}_up", T("stonew", [(x, 17, z), (x * 1.5, 3.0, z - 1.0)], [4.4, 3.0], sides=6))
        F += part(f"fl{i}_ft", ER("barn", (x * 1.5, 1.8, z - 1.5), (4.4, 1.6, 5.8), segs=7, rings=2))
    F += part("tail", Ar4("stonew", [(0, 20, 16), (0, 18, 24), (0, 17.5, 31)], (6.0, 2.0), sides=7))
    flukes(F, (0, 17.5, 31))
    zs = [(-9, 6), (8, 5), (12, 11), (-8, 12), (7, -6), (-6, -9)]
    for i, (x, z) in enumerate(((-8.4, -7), (8.4, -6), (-9, 12), (9, 12), (-6.6, -12), (6.4, -12))):
        yb = 22 + 10 * max(0.1, 1 - (x / 11) ** 2 - (z / 18) ** 2) ** 0.5
        barn_cluster(F, f"bc{i}", x, yb, z, 3, 6, 3.0)
    for i, (x, z) in enumerate(((-3, -14), (3, -14), (0, -11))):
        yb = 21 + 8.6 * max(0.1, 1 - (z + 16) ** 2 / 81) ** 0.5
        F += part(f"hb{i}", C("barn", (x, yb - 1.0, z), (0, 1, 0), 3.4, 1.4, sides=5, rings=2))
    eyes = [eye_at(s * 9.6, 22.6, -18.2, 0.9) for s in (-1, 1)]
    return finish(F, (0, 31.8, 3), girth=gq(22, 11, 10, 18, 0, 3), drop=18.0, w=6.4), eyes


def b(k):
    F = []
    F += part("body", ER("stonew", (0, 23, 2), (10, 9, 19), rx=10, segs=12, rings=4))
    F += part("head", ER("stonew", (0, 28, -17), (9, 8, 8.4), rx=10, segs=10, rings=3))
    F += part("belly", ER("barn", (0, 19, 0), (7, 5, 16), rx=10, segs=10, rings=2))
    F += part("jaw", ER("barn", (0, 23.4, -22), (6, 2.4, 7), rx=10, segs=8, rings=2))
    F += part("blow", T("cinder", [(0, 36, -14), (0, 38, -14)], [2.2, 1.4], sides=6))
    for s in (-1, 1):
        F += part(f"eye{s}", E("barn", (s * 8.2, 29, -19), (1.7, 2.0, 2.0), segs=6, rings=2))
        pts = [(s * 8, 20, -10), (s * 13, 13, -16), (s * 13.6, 3.2, -22)]
        F += part(f"arm{s}", Ar4("stonew", pts, (4.2, 3.0), sides=6))
        F += part(f"hand{s}", ER("barn", (s * 13.6, 2.0, -23.6), (3.8, 1.6, 6.6), ry=s * 8, segs=7, rings=2))
        F += part(f"hindfl{s}", ER("stonew", (s * 7, 4.4, 14), (3.8, 1.5, 5.8), ry=s * 18, segs=7, rings=2))
        F += part(f"hindup{s}", T("stonew", [(s * 6, 15, 12), (s * 7, 5, 14)], [4.6, 3.2], sides=6))
    F += part("tail", Ar4("stonew", [(0, 18, 18), (0, 10, 25), (0, 3.6, 32), (0, 3, 38)], (6.2, 2.2), sides=7))
    for s in (-1, 1):
        F += part(f"fluke{s}", ER("stonew", (s * 5.0, 3.0, 40.5), (7.6, 1.0, 3.8), ry=s * 18, segs=8, rings=2))
    for i in range(10):
        a_ = i * 36.0
        x, z = 9.4 * math.sin(math.radians(a_)), 7 + 9.0 * math.cos(math.radians(a_))
        yb = 23 + 9 * max(0.0, 1 - (x / 10) ** 2 - ((z - 2) / 19) ** 2) ** 0.5
        F += part(f"ring{i}", C("barn", (x * 0.85, yb - 3.0, z), (0, 1, 0), 5.0 + (i % 3), 1.8, sides=5, rings=2))
    eyes = [eye_at(s * 9.4, 29, -19.2, 0.9) for s in (-1, 1)]
    return finish(F, (0, 31.2, 7), girth=gq(23, 10, 9, 19, 2, 7), drop=17.0, w=6.0), eyes


def c(k):
    F = []
    F += part("body", E("stonew", (0, 22, 3), (9, 9, 20), segs=12, rings=4))
    F += part("head", E("stonew", (0, 22, -19), (8, 7.6, 9), segs=10, rings=3))
    F += part("belly", E("barn", (0, 16.4, 0), (6.6, 4.2, 17), segs=10, rings=2))
    for s in (-1, 1):
        F += part(f"eye{s}", E("barn", (s * 7.2, 23, -21), (1.6, 2.0, 2.0), segs=6, rings=2))
        F += part(f"cheek{s}", E("barn", (s * 6, 19, -22), (2.6, 2.6, 3.4), segs=6, rings=2))
    F += part("tusk", T("barn", [(0, 21.4, -26), (0, 24, -38), (0, 27.6, -52)], [2.6, 1.8, 0.0], sides=6))
    for i, z in enumerate((-31, -37, -43)):
        yy = 21.4 + (z + 26) * -0.27
        F += part(f"tband{i}", loop("body", "stonew", 0, yy + 0.2, z, 1.9 - i * 0.35, 1.9 - i * 0.35, r=0.5, n=6, gap=60))
    for i, (x, z) in enumerate(((-6.6, -11), (6.6, -11), (-6.6, 13), (6.6, 13))):
        F += part(f"leg{i}", T("stonew", [(x, 17, z), (x * 1.12, 2.4, z)], [4.6, 3.8], sides=6))
        F += part(f"legring{i}", T("barn", [(x * 1.06, 10, z), (x * 1.06, 10.4, z)], [5.2, 5.2], sides=6))
        F += part(f"ft{i}", E("barn", (x * 1.12, 1.4, z - 0.4), (5.0, 1.6, 5.4), segs=7, rings=2))
    F += part("tail", Ar4("stonew", [(0, 20, 21), (0, 17, 28), (0, 18, 34)], (5.0, 1.8), sides=7))
    for s in (-1, 1):
        F += part(f"fluke{s}", ER("stonew", (s * 4.4, 18, 35.6), (7.2, 0.9, 3.8), ry=s * 16, segs=8, rings=2))
    for s in (-1, 1):
        for j, z in enumerate((-4, 12)):
            yb = 22 + 9 * max(0.1, 1 - (8.0 / 9) ** 2 - ((z - 3) / 20) ** 2) ** 0.5
            barn_cluster(F, f"basket{s}{j}", s * 8.2, yb - 0.6, z, 4, 5.4, 2.2)
    eyes = [eye_at(s * 8.4, 23, -21.2, 0.9) for s in (-1, 1)]
    return finish(F, (0, 31.0, 4), girth=gq(22, 9, 9, 20, 3, 4), drop=16.0, w=5.4), eyes


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="whale_calf", short="岩の子鯨", kind="乗り物 3案 (120 鐘銭)",
    title="乗り物「岩の子鯨」— 石の鯨の子。背にフジツボの石",
    sub=["一章の石の鯨の小さな親戚。石の肌と、背のフジツボの石 (鞍の周りに生える) が目印。左の黄色い目盛りが人 (1.8 ブロック)。",
         "一番下の行は、鞍に人が座った姿。"],
    options={
        "a": dict(name="A 四つ足の子鯨", desc="丸い体にひれの脚が四枚。背の鞍の周りにフジツボの石が生え、尾に尾びれ。"),
        "b": dict(name="B ひれ歩きの子鯨", desc="前ひれで体を起こして歩き、後ろは尾を引く。鞍はフジツボの輪の中。"),
        "c": dict(name="C 角の子鯨", desc="一角鯨の子。長い巻き角、柱の脚にフジツボの輪。鞍の脇にフジツボのかご。"),
    },
    pw=380, rows=rows_for((0, 22, -4), 4.6),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(20, 20, 24), (230, 236, 240)])
