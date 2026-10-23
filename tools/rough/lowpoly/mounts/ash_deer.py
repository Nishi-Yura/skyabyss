"""灰色の鹿 (60 鐘銭) ラフ 3 案。灰の世界になじむ、速くて細い脚の鹿。
A 枝角の牡鹿 (高く枝分かれした角、胸の白い毛) / B 大角の鹿 (肩のこぶ、垂れた鼻先、手のひら形の広い角)
C 灰たなびく牝鹿 (角なし、長い耳、首から流れる灰の房、背の熾の斑)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def deer_legs(F, y_top, ys, zf, zh, w, mat="ashf", r=(3.2, 2.0, 1.4), hoof="cinder"):
    for i, (x, z) in enumerate(((-w, zf), (w, zf), (-w, zh), (w, zh))):
        hind = z > 0
        kx = 2.5 if hind else -1.0
        F += leg(f"leg{i}", mat, (x, y_top, z), (x, ys[0], z + kx), (x, ys[1], z + (1.0 if hind else -0.5)), (x, 0, z - 1.0),
                 r[0], r[1], r[2], foot_m=hoof, foot=(2.2, 1.5, 3.0))


def a(k):
    F = []
    F += part("body", E("ashf", (0, 22, 2), (6.6, 6.6, 12.5), segs=10, rings=4))
    F += part("rump", E("ashf", (0, 21.4, 12), (6.0, 6.0, 5.5), segs=8, rings=3))
    F += part("ruff", E("cream", (0, 27, -9), (5.0, 5.0, 4.4), segs=8, rings=3))
    deer_legs(F, 20, (11.5, 5.0), -8, 11, 3.8)
    F += part("neck", T("ashf", [(0, 25, -8), (0, 33, -15)], [5.0, 3.5], sides=7))
    F += part("head", ER("ashf", (0, 36, -18.5), (3.4, 3.6, 6), rx=-18, segs=8, rings=3))
    F += part("muzzle", ER("cream", (0, 33.4, -24), (2.4, 2.2, 2.8), rx=-18, segs=7, rings=2))
    F += part("nose", Bl("cinder", (0, 32.8, -26.4), 1.3, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", ER("ashf", (s * 4.4, 38.4, -16.6), (1.2, 2.8, 2.0), rz=s * 55, segs=6, rings=2))
        beam = [(s * 2.2, 38.5, -17), (s * 4.6, 43, -16), (s * 7.2, 49, -18.5), (s * 8.0, 56, -22)]
        F += part(f"beam{s}", Ar4("cream", beam, (1.5, 0.3), sides=5))
        for j, (p, d) in enumerate(((beam[1], (s * 0.7, 0.4, -0.7)), (beam[2], (s * 0.8, 0.5, 0.2)), (beam[2], (-s * 0.2, 0.5, -1.0)))):
            F += part(f"tine{s}{j}", C("cream", p, d, 5.5, 1.0, sides=4, rings=2))
        F += part(f"ptine{s}", C("cream", beam[0], (s * 0.3, 0.3, -1.0), 7.0, 1.1, sides=4, rings=2))
    F += part("tail", E("cream", (0, 23, 17.4), (2.6, 3.4, 2.6), segs=6, rings=2))
    for i, (x, y, z, rr) in enumerate(((-5.4, 23.4, 4, 2.6), (5.2, 24.4, 9, 2.4), (-4.6, 25.2, 12, 2.0), (3.8, 20.4, -1, 2.4))):
        F += part(f"soot{i}", E("cinder", (x, y, z), (rr * 0.5, rr, rr), segs=6, rings=2))
    eyes = [eye_at(s * 3.2, 37.2, -21.2, 0.8) for s in (-1, 1)]
    return finish(F, (0, 28.4, 3), girth=gq(22, 6.6, 6.6, 12.5, 2, 3), drop=14.0, w=5.6), eyes


def b(k):
    F = []
    F += part("body", E("ashf", (0, 26, 1), (7.6, 7.2, 13), segs=10, rings=4))
    F += part("hump", E("ashf", (0, 31, -6), (6.2, 5.4, 6.2), segs=8, rings=3))
    F += part("rump", E("ashf", (0, 24.6, 12), (6.6, 6.4, 5.5), segs=8, rings=3))
    F += part("dewlap", E("cinder", (0, 24, -14), (2.4, 5.0, 2.4), segs=6, rings=2))
    deer_legs(F, 24, (14, 6.5), -9, 11, 4.4, r=(3.8, 2.4, 1.7))
    F += part("neck", T("ashf", [(0, 30, -9), (0, 31, -18)], [6.0, 4.4], sides=7))
    F += part("head", ER("ashf", (0, 29.6, -23), (4.4, 5.0, 9), rx=22, segs=9, rings=3))
    F += part("muzzle", ER("cream", (0, 25.4, -30.5), (3.6, 3.2, 3.4), rx=22, segs=7, rings=2))
    F += part("nose", Bl("cinder", (0, 24.2, -33.4), 1.8, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", ER("ashf", (s * 5.4, 33.4, -20), (1.4, 3.0, 2.2), rz=s * 62, segs=6, rings=2))
        F += part(f"stem{s}", T("cream", [(s * 3.4, 33.5, -21), (s * 7.6, 37.2, -21)], [1.8, 1.8], sides=5))
        F += part(f"palm{s}", ER("cream", (s * 12.4, 38.6, -22), (6.6, 1.3, 5.6), rz=s * 14, ry=s * 10, segs=8, rings=2))
        for j, zz in enumerate((-26, -22.6, -19.4)):
            F += part(f"finger{s}{j}", C("cream", (s * 14.6, 39.2, zz), (s * 0.3, 0.9, -0.5 if j < 2 else 0.2), 6.0, 1.2, sides=4, rings=2))
    F += part("tail", E("cream", (0, 25.4, 17.6), (2.4, 3.0, 2.6), segs=6, rings=2))
    eyes = [eye_at(s * 4.0, 32.4, -22.8, 0.9) for s in (-1, 1)]
    return finish(F, (0, 32.6, 6), girth=gq(26, 7.6, 7.2, 13, 1, 6), drop=16.0, w=6.0), eyes


def c(k):
    F = []
    F += part("body", E("ashf", (0, 20, 2), (6.0, 6.4, 11.5), segs=10, rings=4))
    F += part("rump", E("ashf", (0, 19.6, 11), (5.4, 5.6, 5), segs=8, rings=3))
    F += part("belly", E("cream", (0, 16, 1), (4.2, 3.4, 9), segs=8, rings=2))
    deer_legs(F, 18, (10.5, 4.6), -8, 11, 3.4, r=(3.0, 1.9, 1.3))
    F += part("neck", T("ashf", [(0, 23, -8), (0, 31, -13)], [4.6, 3.2], sides=7))
    F += part("head", ER("ashf", (0, 34, -16.4), (3.2, 3.4, 5.6), rx=-20, segs=8, rings=3))
    F += part("muzzle", ER("cream", (0, 31.6, -21.4), (2.2, 2.0, 2.6), rx=-20, segs=7, rings=2))
    F += part("nose", Bl("cinder", (0, 31.0, -23.6), 1.2, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ear{s}", ER("ashf", (s * 5.8, 37.6, -14.8), (1.3, 5.0, 2.0), rz=s * 62, segs=7, rings=2))
        F += part(f"earin{s}", ER("cinder", (s * 5.0, 37.2, -15.4), (0.5, 3.6, 1.0), rz=s * 62, segs=5, rings=1))
    plumes = [(0, 28, -9, 6, 24, 10), (-2.4, 28, -8, 9, 19, 14), (2.4, 28, -8, 9, 19, 14)]
    for j, (x, y, z, dx, dy, dz) in enumerate(plumes):
        F += part(f"plume{j}", Ar("cinder", (x, y, z), (x, y + 6, z + dz * 0.4), (x + (dx if x else 0) * 0.3, y + 3, z + dz), (2.2, 0.0), sides=4))
    F += part("tailp", Ar("cream", (0, 21, 14), (0, 25, 20), (0, 31, 23), (3.0, 0.0), sides=5))
    for i, (x, y, z) in enumerate(((-4.6, 23.6, 7), (4.8, 24, 11), (-4, 24, 14), (0, 26.2, 15))):
        F += part(f"ember{i}", Bl("ember", (x, y, z), 1.5, segs=6, rings=2))
    eyes = [eye_at(s * 3.0, 35, -19.2, 0.8) for s in (-1, 1)]
    return finish(F, (0, 26.4, 2), girth=gq(20, 6, 6.4, 11.5, 2, 2), drop=13.0, w=5.2), eyes


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="ash_deer", short="灰色の鹿", kind="乗り物 3案 (60 鐘銭)",
    title="乗り物「灰色の鹿」— 速くて細い脚の灰の鹿",
    sub=["灰の降る世界の鹿。脚が長く、跳びが得意そうな姿。左の黄色い目盛りが人 (1.8 ブロック)。",
         "一番下の行は、鞍に人が座った姿。"],
    options={
        "a": dict(name="A 枝角の牡鹿", desc="高く枝分かれした白い角、胸の白い毛。すらりとした王道の鹿。"),
        "b": dict(name="B 大角の鹿", desc="肩のこぶ、垂れた鼻先、手のひら形の広い角。背が高く力強い。"),
        "c": dict(name="C 灰たなびく牝鹿", desc="角なし。大きな耳と、首から流れる灰の房。背に熾の斑が散る。"),
    },
    pw=380, rows=rows_for((0, 28, -2), 5.5),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(40, 30, 24), (220, 214, 200)])
