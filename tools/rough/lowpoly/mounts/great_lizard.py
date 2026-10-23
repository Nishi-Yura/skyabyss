"""大トカゲ (60 鐘銭) ラフ 3 案。低く這うが力のある、荒れ地のトカゲ。
A 這うオオトカゲ (長い体と尾、広げた脚) / B 帆背のトカゲ (脚を立てて高く構える、首の襟、背の棘の帆)
C 鎧のトカゲ (幅広い平たい体、背の骨の粒、大きな顎)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def sprawl(F, m, y_top, xs, zf, zh, spread, lo, foot_m, r=(3.4, 2.4, 1.8), foot=(3.2, 1.2, 4.2)):
    for i, (s, z) in enumerate(((-1, zf), (1, zf), (-1, zh), (1, zh))):
        F += leg(f"leg{i}", m, (s * xs, y_top, z), (s * spread, y_top - 3, z - 2.0 + (4 if z > 0 else 0)),
                 (s * spread, lo + 2.4, z + (1 if z > 0 else -1)), (s * spread, 0, z - 3.0),
                 r[0], r[1], r[2], foot_m=foot_m, foot=foot)


def a(k):
    F = []
    F += part("body", E("liz", (0, 11, 0), (7.2, 5.6, 16), segs=10, rings=3))
    F += part("belly", E("lizb", (0, 8, 0), (5.4, 3.4, 14), segs=8, rings=2))
    sprawl(F, "liz", 10, 5.5, -9, 10, 13, 0, "lizb")
    F += part("neck", T("liz", [(0, 11.4, -13), (0, 12.6, -20)], [5.0, 4.0], sides=7))
    F += part("head", ER("liz", (0, 12, -25), (3.8, 3.2, 6.6), rx=4, segs=8, rings=3))
    F += part("snout", ER("lizb", (0, 11.2, -31), (2.6, 2.0, 3.2), rx=4, segs=7, rings=2))
    F += part("tail", Ar4("liz", [(0, 10.6, 13), (0, 9.4, 26), (5.4, 6.4, 38), (0, 3.2, 50)], (4.8, 0.3), sides=6))
    F += part("dewlap", E("lizb", (0, 8.2, -20), (2.6, 3.0, 4.0), segs=6, rings=2))
    for i in range(7):
        z = -10 + i * 4.6
        F += part(f"spine{i}", C("lizb", (0, 11 + 5.6 * max(0.0, 1 - (z / 16) ** 2) ** 0.5 - 0.8, z), (0, 1, 0.1), 3.0, 1.4, sides=4, rings=2))
    F += part("tonguec", T("ember", [(0, 11, -33.4), (0, 10.6, -37)], [0.7, 0.5], sides=4))
    eyes = [eye_at(s * 3.2, 13.4, -27, 0.8) for s in (-1, 1)]
    return finish(F, (0, 16.2, 2), girth=gq(11, 7.2, 5.6, 16, 0, 2), drop=9.0, w=5.6, stir=True), eyes


def b(k):
    F = []
    F += part("body", E("lizb", (0, 19, 0), (6.4, 5.6, 14), segs=10, rings=3))
    F += part("belly", E("liz", (0, 15.6, 0), (4.6, 3.4, 11), segs=8, rings=2))
    for i, (s, z) in enumerate(((-1, -8), (1, -8), (-1, 9), (1, 9))):
        F += leg(f"leg{i}", "lizb", (s * 4.4, 17, z), (s * 10.5, 11.5, z - 1.4), (s * 8.5, 5.0, z + (1.2 if z > 0 else -0.6)), (s * 8.5, 0, z - 2.4),
                 3.4, 2.4, 1.8, foot_m="liz", foot=(3.0, 1.2, 4.0))
    F += part("neck", T("lizb", [(0, 19.6, -12), (0, 22, -18)], [4.6, 3.8], sides=7))
    F += part("head", ER("lizb", (0, 22.6, -22.4), (3.6, 3.6, 5.4), rx=-8, segs=8, rings=3))
    F += part("jaw", ER("liz", (0, 20.6, -23.4), (2.8, 1.6, 4.8), rx=-8, segs=7, rings=2))
    F += part("pouch", E("coral", (0, 19.0, -20.6), (2.2, 3.4, 3.2), segs=7, rings=2))
    F += part("frill", E("coral", (0, 23, -17.6), (9.0, 8.4, 1.1), segs=10, rings=3))
    for i in range(7):
        a_ = math.radians(-66 + i * 22)
        F += part(f"fspike{i}", C("lizb", (9.2 * math.sin(a_) * 0.84, 23 + 8.4 * math.cos(a_) * 0.84, -17.6), (math.sin(a_), math.cos(a_), -0.1), 3.4, 1.0, sides=4, rings=2))
    for i in range(8):
        z = -8 + i * 3.2
        h = 8.0 - abs(i - 3.2) * 1.0
        F += part(f"sail{i}", C("coral", (0, 19 + 5.6 * max(0.0, 1 - (z / 14) ** 2) ** 0.5 - 0.8, z), (0, 1, 0.12), h, 1.5, sides=4, rings=2))
    F += part("tail", Ar4("lizb", [(0, 18.6, 12), (0, 16, 24), (0, 14, 34), (0, 18, 42), (0, 25, 44)], (4.2, 0.3), sides=6))
    eyes = [eye_at(s * 3.4, 23.8, -24.8, 0.8) for s in (-1, 1)]
    return finish(F, (0, 23.6, 6), girth=gq(19, 6.4, 5.6, 14, 0, 6), drop=14.0, w=5.0), eyes


def c(k):
    F = []
    F += part("body", E("liz", (0, 10, 0), (10.0, 5.6, 15), segs=10, rings=3))
    F += part("belly", E("lizb", (0, 7, 0), (7.6, 3.2, 13), segs=8, rings=2))
    for i, (s, z) in enumerate(((-1, -9), (1, -9), (-1, 10), (1, 10))):
        F += leg(f"leg{i}", "liz", (s * 7, 9, z), (s * 13.6, 7, z - 1.0), (s * 13.6, 3.0, z + 0.6), (s * 13.6, 0, z - 2.4),
                 4.4, 3.2, 2.6, foot_m="lizb", foot=(3.6, 1.4, 4.4))
    F += part("neck", T("liz", [(0, 10, -13), (0, 10.4, -19)], [6.4, 6.0], sides=7))
    F += part("head", ER("liz", (0, 9.4, -26), (5.6, 3.6, 8.4), segs=9, rings=3))
    F += part("jaw", E("lizb", (0, 6.6, -27), (4.8, 1.8, 7.8), segs=8, rings=2))
    for s in (-1, 1):
        F += part(f"nostril{s}", E("lizb", (s * 2.4, 11.6, -33), (1.4, 1.2, 1.4), segs=6, rings=2))
        F += part(f"brow{s}", E("barn", (s * 3.8, 13, -26), (2.0, 1.6, 2.6), segs=6, rings=2))
        for j in range(3):
            F += part(f"tooth{s}{j}", C("barn", (s * 3.6, 7.6, -23.0 - j * 3.4), (0, 1, 0), 2.6, 0.7, sides=4, rings=2))
    for r_i, x in enumerate((-5.4, 0, 5.4)):
        for i in range(6):
            z = -10 + i * 4.8
            if r_i == 1 and -6 < z < 12:
                continue
            F += part(f"stud{r_i}_{i}", E("barn", (x, 10 + 5.6 * max(0.0, 1 - (x / 10) ** 2 - (z / 15) ** 2) ** 0.5 - 0.2, z), (2.0, 1.5, 2.0), segs=6, rings=2))
    F += part("tail", Ar4("liz", [(0, 9.8, 13), (0, 8.4, 24), (4, 6, 34), (0, 3, 42)], (6.0, 0.3), sides=6))
    for i in range(4):
        F += part(f"tstud{i}", E("barn", (0, 13.6 - i * 2.3, 17 + i * 6.2), (1.5, 1.4, 1.7), segs=6, rings=2))
    eyes = [eye_at(s * 4.4, 12.2, -28, 0.9) for s in (-1, 1)]
    return finish(F, (0, 15.4, 3), girth=gq(10, 10, 5.6, 15, 0, 3), drop=8.0, w=7.0), eyes


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="great_lizard", short="大トカゲ", kind="乗り物 3案 (60 鐘銭)",
    title="乗り物「大トカゲ」— 低く這うが力のある荒れ地のトカゲ",
    sub=["背が低く、人は鞍の上で脚を垂らす。左の黄色い目盛りが人 (1.8 ブロック)。", "一番下の行は、鞍に人が座った姿。"],
    options={
        "a": dict(name="A 這うオオトカゲ", desc="長い体と長い尾、外へ広げた脚。背の小さな棘が並ぶ。最も低い。"),
        "b": dict(name="B 帆背のトカゲ", desc="脚を立てて高く構える。首の赤い襟と、背に並ぶ棘の帆。尾は反り上がる。"),
        "c": dict(name="C 鎧のトカゲ", desc="幅広く平たい体、背に骨の粒が並ぶ。大きな顎と牙。どっしり安定。"),
    },
    pw=380, rows=rows_for((0, 15, 6), 4.5),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(220, 180, 40), (20, 20, 10)])
