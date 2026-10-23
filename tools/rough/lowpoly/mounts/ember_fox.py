"""炉の狐 (360 鐘銭、四章) ラフ 3 案。熾の巨神 (四章) の小さな子。尾がくすぶり、体に熾の割れ目。
A しなやかな炉の狐 (細い脚、大きな耳、燃えて先が赤い大きな尾を高く掲げる) / B 竈の狐 (どっしり低い体、背に煙突、腹の火口、丸太のような尾)
C 三尾の熾狐 (細くて素早い、三本の尾が扇に開く、首から背へ灰の房と熾の筋)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def fox_legs(F, y_top, ys, zf, zh, w, r=(3.4, 2.2, 1.5), m="fox", paw="cinder"):
    for i, (x, z) in enumerate(((-w, zf), (w, zf), (-w, zh), (w, zh))):
        hind = z > 0
        F += leg(f"leg{i}", m, (x, y_top, z), (x, ys[0], z + (2.5 if hind else -0.5)), (x, ys[1], z + (1.4 if hind else 0.0)), (x, 0, z - 1.0),
                 r[0], r[1], r[2], foot_m=paw, foot=(2.4, 1.5, 3.4))


def tail_chain(F, name, pts, radii, m="fox", tip_m="ember", tip_n=2):
    """尾: 点列を通る太さの変わる管 + 先の光る玉 (くすぶる先)。"""
    n = len(pts) - 1
    F += part(name, tube(B, m, pts, ra=radii, sides=6, caps=(True, True)))
    F += part(name + "_tip", E(tip_m, pts[-1], (radii[-1] * 1.05, radii[-1] * 1.05, radii[-1] * 1.4), segs=7, rings=2))


def a(k):
    F = []
    F += part("body", E("fox", (0, 22, 1), (7.0, 7.0, 13), segs=10, rings=4))
    F += part("chest", E("cream", (0, 20, -8), (5.4, 5.6, 5), segs=8, rings=3))
    F += part("rump", E("fox", (0, 21.4, 12), (6.2, 6.0, 5.4), segs=8, rings=3))
    fox_legs(F, 20, (11.0, 5.0), -8.5, 11, 3.8)
    F += part("neck", T("fox", [(0, 24, -9), (0, 28, -14)], [5.0, 4.0], sides=7))
    F += part("head", ER("fox", (0, 29.4, -18), (4.0, 3.8, 5.4), rx=-8, segs=8, rings=3))
    F += part("snout", ER("cream", (0, 27.6, -23.2), (2.4, 2.0, 3.6), rx=-8, segs=7, rings=2))
    F += part("nose", Bl("cinder", (0, 27.6, -26.4), 1.3, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", C("fox", (s * 2.8, 32, -16.4), (s * 0.35, 1, 0.1), 6.6, 2.2, sides=4, rings=2))
        F += part(f"earin{s}", C("cinder", (s * 2.8, 32.4, -17.2), (s * 0.35, 1, 0.1), 4.4, 1.2, sides=4, rings=1))
        F += part(f"sock{s}", E("cinder", (s * 3.8, 8, -8.5), (2.4, 4.2, 2.4), segs=6, rings=2))
    pts = [(0, 22, 14), (0, 22, 22), (0, 27, 28), (0, 35, 30), (0, 43, 28)]
    tail_chain(F, "tail", pts, [3.0, 4.4, 5.2, 4.6, 3.0])
    for i, (x, y, z) in enumerate(((-5.4, 25, -2), (5, 26, 9), (-3, 18, -10), (4.6, 17, 4))):
        F += part(f"crack{i}", E("ember", (x, y, z), (1.0, 2.6, 1.0), segs=6, rings=2))
    eyes = [eye_at(s * 3.2, 30.4, -21.4, 0.8) for s in (-1, 1)]
    return finish(F, (0, 28.8, 2), girth=gq(22, 7.0, 7.0, 13, 1, 2), drop=14.0, w=5.4), eyes


def b(k):
    F = []
    F += part("body", E("fox", (0, 19, 0), (9.6, 8.8, 12), segs=10, rings=4))
    F += part("belly", E("cream", (0, 13.6, -1), (7, 4, 10), segs=8, rings=2))
    F += part("vent", E("ember", (0, 13.2, -9.6), (3.8, 2.4, 1.6), segs=7, rings=2))
    F += part("vent2", E("cinder", (0, 12.6, -10.4), (4.8, 3.2, 1.2), segs=7, rings=2))
    fox_legs(F, 16, (9.6, 4.6), -7, 9, 5.6, r=(4.6, 3.4, 2.4))
    F += part("head", ER("fox", (0, 22, -16.4), (5.8, 5.2, 6.4), segs=9, rings=3))
    F += part("snout", ER("cream", (0, 19.6, -22), (3.4, 2.6, 3.8), segs=7, rings=2))
    F += part("nose", Bl("cinder", (0, 19.6, -25.4), 1.5, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", C("fox", (s * 4.2, 25.6, -14.6), (s * 0.35, 1, 0.1), 5.0, 2.8, sides=4, rings=2))
        F += part(f"earin{s}", C("ember", (s * 4.2, 25.8, -15.2), (s * 0.35, 1, 0.1), 3.0, 1.3, sides=4, rings=1))
    F += part("flue", T("brass", [(5.2, 24, 8), (5.2, 36, 8)], [3.0, 2.6], sides=6))
    F += part("flue_rim", T("cinder", [(5.2, 36, 8), (5.2, 37.4, 8)], [3.5, 3.4], sides=6))
    F += part("flue_band", T("cinder", [(5.2, 30, 8), (5.2, 31, 8)], [3.5, 3.5], sides=6))
    for i, (x, y, z, r) in enumerate(((5.2, 40, 8, 1.8), (6, 43.6, 7, 2.3), (7.2, 47.4, 6, 2.8))):
        F += part(f"smoke{i}", Bl("cinder", (x, y, z), r, segs=6, rings=2))
        if i:
            F += part(f"smoke_l{i}", T("cinder", [(5.2 + (x - 5.2) * 0.0 + (6 - 5.2) * (i - 1), 40 + 3.6 * (i - 1), 8 - (i - 1)), (x, y, z)], [1.0, 1.0], sides=4))
    F += part("smoke_l0", T("cinder", [(5.2, 37, 8), (5.2, 40, 8)], [1.0, 1.0], sides=4))
    tail_chain(F, "tail", [(0, 18, 11), (0, 15, 20), (0, 11, 28), (0, 9, 36)], [5.6, 5.4, 4.6, 3.4])
    for i, (x, y, z) in enumerate(((-7.6, 22, -3), (-8, 17, 6), (8, 14, -5))):
        F += part(f"crack{i}", E("ember", (x, y, z), (1.2, 2.8, 1.8), segs=6, rings=2))
    eyes = [eye_at(s * 4.8, 23.0, -20.0, 0.9) for s in (-1, 1)]
    return finish(F, (-1, 27.4, -3.5), girth=gq(19, 9.6, 8.8, 12, 0, -3.5), drop=13.0, w=5.8), eyes


def c(k):
    F = []
    F += part("body", ER("fox", (0, 21, 1), (6.0, 6.2, 13.5), rx=-4, segs=10, rings=4))
    F += part("chest", E("cream", (0, 19, -9), (4.8, 5.2, 4.6), segs=8, rings=3))
    fox_legs(F, 19, (10.0, 4.6), -9, 11, 3.4, r=(3.0, 2.0, 1.7), m="cinder", paw="cinder")
    F += part("neck", T("fox", [(0, 24, -9), (0, 29, -13)], [4.4, 3.6], sides=7))
    F += part("head", ER("fox", (0, 30.6, -17), (3.6, 3.6, 5.2), rx=-8, segs=8, rings=3))
    F += part("snout", ER("cream", (0, 28.8, -22), (2.2, 1.9, 3.4), rx=-8, segs=7, rings=2))
    F += part("nose", Bl("cinder", (0, 28.8, -25), 1.2, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", ER("fox", (s * 3.0, 35, -15.2), (1.6, 5.8, 2.4), rz=s * 24, segs=7, rings=2))
        F += part(f"earin{s}", ER("ember", (s * 3.8, 35.6, -15.4), (0.6, 3.6, 1.0), rz=s * 24, segs=5, rings=1))
        F += part(f"cheek{s}", C("cinder", (s * 3.4, 29, -15.6), (s * 1.0, -0.2, 0.4), 6.0, 1.6, sides=4, rings=2))
    for i in range(5):
        z = -10 + i * 3.6
        F += part(f"mane{i}", C("cinder", (0, 24.4 + 0.2 * i, z), (0, 1, 0.6), 7.0 - i * 0.6, 2.0 if i < 2 else 1.4, sides=4, rings=2))
    for i, z in enumerate((-6, 13, 17)):
        if i == 0:
            continue
    for i in range(3):
        z = 8 + i * 3.6
        F += part(f"stripe{i}", E("ember", (0, 26.2 - 0.4 * i + 1.0, z), (1.0, 1.0, 1.7), segs=6, rings=2))
    specs = ((0, ((0, 22, 14), (0, 26, 22), (0, 33, 27), (0, 41, 28)), [3.0, 4.2, 4.6, 3.0]),
             (-1, ((-2, 22, 14), (-6, 24, 22), (-11, 28, 28), (-15, 34, 31)), [2.6, 3.6, 3.8, 2.4]),
             (1, ((2, 22, 14), (6, 24, 22), (11, 28, 28), (15, 34, 31)), [2.6, 3.6, 3.8, 2.4]))
    for i, (s, pts, rr) in enumerate(specs):
        tail_chain(F, f"tail{i}", [tuple(p) for p in pts], rr)
    eyes = [eye_at(s * 3.0, 31.4, -20.0, 0.8) for s in (-1, 1)]
    return finish(F, (0, 27.2, 3), girth=gq(21, 6.0, 6.2, 13.5, 1, 3), drop=13.0, w=5.2), eyes


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="ember_fox", short="炉の狐", kind="乗り物 3案 (360 鐘銭、四章)",
    title="乗り物「炉の狐」— 熾の巨神の子。尾がくすぶる",
    sub=["四章の熾の巨神の小さな親戚。体に熾の割れ目、尾の先が赤く燃える (光る所が熾)。左の黄色い目盛りが人 (1.8 ブロック)。",
         "一番下の行は、鞍に人が座った姿。"],
    options={
        "a": dict(name="A しなやかな炉の狐", desc="細い脚と大きな耳。燃えて先の赤い大きな尾を高く掲げる。王道の狐。"),
        "b": dict(name="B 竈の狐", desc="どっしり低い体。背に煙突から煙、腹に火口、丸太のような尾。鞍は煙突の前。"),
        "c": dict(name="C 三尾の熾狐", desc="細く素早い体。三本の尾が扇に開き、首から背へ灰の房と熾の筋が走る。"),
    },
    pw=380, rows=rows_for((0, 24, 4), 4.4),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(255, 190, 60), (255, 240, 180)])
