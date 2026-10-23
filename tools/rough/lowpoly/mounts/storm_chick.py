"""雷の雛 (280 鐘銭、三章) ラフ 3 案。雷の翼の巨神 (三章) の小さな子。羽の冠がぱちぱち光る。
A 背高の雛 (長い脚と首、大きな足、羽の冠と尾羽が雷を帯びる) / B まるまる雛 (大きな綿毛の玉、短い脚、広げた小さな翼、とがった冠)
C 脚長の雛 (サギのような細く高い体、稲妻の形に折れた冠と尾の飾り羽、ふさふさの脚)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa


def toes(F, name, x, z, m="brass", ln=6.5, y=1.0, r=1.1):
    for j, ang in enumerate((-28, 0, 28)):
        dx, dz = math.sin(math.radians(ang)), -math.cos(math.radians(ang))
        F += part(f"{name}t{j}", T(m, [(x, y + 0.4, z), (x + dx * ln, y, z + dz * ln)], [r * 1.2, r * 0.8], sides=4))
    F += part(f"{name}tb", T(m, [(x, y + 0.4, z), (x, y + 0.4, z + 3.6)], [r * 1.2, r * 0.8], sides=4))


def bolt_feather(F, name, p0, d, length, r=1.6, m="stormb", tip_m="bolt", zig=2.4, n=3):
    """折れ線の羽 (稲妻形)。先の三分の一が光る。"""
    d = np.asarray(d, float)
    d = d / np.linalg.norm(d)
    side = np.cross(d, (0, 0, 1.0))
    if np.linalg.norm(side) < 1e-6:
        side = np.array([1.0, 0, 0])
    side = side / np.linalg.norm(side)
    pts = [np.asarray(p0, float)]
    for i in range(1, n + 1):
        pts.append(pts[0] + d * length * i / n + side * zig * (1 if i % 2 else -1) * (1 if i < n else 0.3))
    F += part(name, tube(B, m, pts[:-1], ra=[r * (1 - 0.45 * i / max(1, len(pts) - 2)) for i in range(len(pts) - 1)], sides=4, caps=(True, True)))
    F += part(name + "_tip", tube(B, tip_m, pts[-2:], ra=[r * 0.55, 0.0], sides=4, caps=(True, False)))


def a(k):
    F = []
    F += part("body", E("feath", (0, 28, 2), (8.2, 8.4, 11), segs=10, rings=4))
    F += part("breast", E("cream", (0, 25, -5), (6.6, 6.4, 6), segs=8, rings=3))
    for i, s in enumerate((-1, 1)):
        F += leg(f"leg{i}", "brass", (s * 4.6, 25, 2), (s * 5.2, 17, -3), (s * 5.0, 9, 3), (s * 5.0, 0, 0), 3.0, 2.0, 1.5, foot=(2.0, 1.0, 2.0))
        F += part(f"thigh{i}", E("feath", (s * 5.6, 23, 1), (3.8, 5.6, 5.4), segs=7, rings=2))
        toes(F, f"foot{i}", s * 5.0, 1.0)
        F += part(f"wing{i}", ER("stormb", (s * 8.6, 29, 4), (1.6, 5.8, 8.0), rz=s * -8, segs=8, rings=2))
    F += part("neck", T("feath", [(0, 31, -6), (0, 38, -9), (0, 44, -11)], [5.0, 3.8, 3.2], sides=7))
    F += part("head", E("feath", (0, 47, -13), (4.2, 4.0, 5.0), segs=8, rings=3))
    F += part("beak", C("brass", (0, 46.4, -16.4), (0, -0.2, -1), 6.0, 2.0, sides=4, rings=2))
    for j, (dx, dy, dz, L) in enumerate(((-0.9, 1, 0.5, 8), (-0.45, 1, 0.9, 10), (0, 1, 1.2, 11), (0.45, 1, 0.9, 10), (0.9, 1, 0.5, 8))):
        bolt_feather(F, f"crest{j}", (dx * 1.8, 50, -11.5), (dx, dy, dz), L, r=1.2)
    for j, (dx, L) in enumerate(((-0.7, 14), (0, 18), (0.7, 14))):
        bolt_feather(F, f"tailf{j}", (dx * 2.0, 31, 11), (dx, 0.7, 1.0), L, r=1.8, zig=3.0)
    eyes = [eye_at(s * 3.8, 47.8, -15.4, 0.9) for s in (-1, 1)]
    return finish(F, (0, 36.0, 3), girth=gq(28, 8.2, 8.4, 11, 2, 3), drop=17.0, w=5.4, pommel=True), eyes


def b(k):
    F = []
    F += part("body", E("feath", (0, 21, 0), (11.4, 11, 11.4), segs=12, rings=4))
    for i, (x, y, z) in enumerate(((-8, 24, -4), (8, 24, -4), (-9, 17, 4), (9, 17, 4), (0, 12, -8), (0, 14, 9))):
        F += part(f"fluff{i}", E("cream" if i > 3 else "feath", (x, y, z), (4.8, 4.6, 4.8), segs=7, rings=2))
    F += part("head", E("feath", (0, 29, -10), (7.4, 6.6, 6.4), segs=10, rings=3))
    F += part("beak", C("brass", (0, 28, -15.0), (0, -0.2, -1), 5.0, 2.8, sides=4, rings=2))
    for s in (-1, 1):
        F += part(f"cheek{s}", E("coral", (s * 5.6, 26.4, -13.4), (1.6, 1.6, 1.0), segs=6, rings=2))
        F += part(f"wing{s}", ER("stormb", (s * 11.6, 22, 0), (2.2, 6.4, 7.6), rz=s * 28, segs=8, rings=2))
        bolt_feather(F, f"wingf{s}", (s * 13.6, 18, 2), (s * 0.6, -0.3, 1), 9.0, r=1.4, zig=2.0)
        F += part(f"leg{s}", T("brass", [(s * 5, 12, 0), (s * 5.4, 2.4, -1)], [3.0, 2.2], sides=5))
        toes(F, f"foot{s}", s * 5.4, -1.0, ln=5.6)
    for j, (dx, dz, L) in enumerate(((-1.2, 0.2, 9), (-0.6, -0.1, 11), (0, -0.3, 12), (0.6, -0.1, 11), (1.2, 0.2, 9))):
        bolt_feather(F, f"crest{j}", (dx * 2.2, 34.2, -8 + dz * 2), (dx * 0.8, 1, dz), L, r=1.4, zig=1.8)
    for j, dx in enumerate((-0.5, 0.5)):
        bolt_feather(F, f"tailf{j}", (dx * 3, 22, 10), (dx, 0.5, 1.0), 9, r=1.8, zig=2.4)
    eyes = [eye_at(s * 6.2, 30.0, -13.6, 1.5) for s in (-1, 1)]
    return finish(F, (0, 31.6, 4), girth=gq(21, 11.4, 11, 11.4, 0, 4), drop=17.0, w=5.8, pommel=True), eyes


def c(k):
    F = []
    F += part("body", ER("feath", (0, 33, 0), (6.4, 6.4, 13), rx=-6, segs=10, rings=3))
    F += part("breast", E("cream", (0, 31, -8), (5.0, 5.0, 5.4), segs=8, rings=2))
    for i, s in enumerate((-1, 1)):
        F += part(f"puff{i}", E("feath", (s * 5.0, 29, 2), (4.0, 5.0, 5.4), segs=7, rings=2))
        F += leg(f"leg{i}", "brass", (s * 4.4, 28, 2), (s * 4.8, 19, -1), (s * 4.8, 9, 3), (s * 4.8, 0, 0), 2.6, 1.7, 1.3, foot=(1.8, 1.0, 1.8))
        toes(F, f"foot{i}", s * 4.8, 0.6, ln=6.0, r=0.9)
        F += part(f"wing{i}", ER("stormb", (s * 7.0, 33, 4), (1.4, 5.0, 11.0), rz=s * -6, segs=8, rings=2))
        F += part(f"wtip{i}", Ar("stormb", (s * 7.0, 33, 12), (s * 7.4, 33, 18), (s * 7.4, 31, 24), (1.8, 0.6), sides=4))
    F += part("neck", Ar4("feath", [(0, 36, -9), (0, 41, -14), (0, 47, -13), (0, 53, -15)], (3.6, 2.4), sides=6))
    F += part("head", E("feath", (0, 55, -17), (3.4, 3.2, 4.4), segs=8, rings=3))
    F += part("beak", C("brass", (0, 54.4, -20), (0, -0.2, -1), 7.0, 1.6, sides=4, rings=2))
    for j, (s, y) in enumerate(((-1, 1), (1, 1), (-1, 0), (1, 0))):
        bolt_feather(F, f"crest{j}", (s * 1.4, 57.4 - 1.4 * y, -14.4), (s * 0.3, 0.5 + y * 0.2, 1), 14 - 3 * (1 - y), r=1.0, zig=2.6, n=4)
    for j, dx in enumerate((-0.6, 0, 0.6)):
        bolt_feather(F, f"tailf{j}", (dx * 2.0, 35, 12), (dx, -0.2, 1.0), 22, r=1.6, zig=3.4, n=4)
    eyes = [eye_at(s * 3.0, 56, -19.0, 0.8) for s in (-1, 1)]
    return finish(F, (0, 38.4, 3), girth=gq(33, 6.4, 6.4, 13, 0, 3), drop=17.0, w=5.0, pommel=True), eyes


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="storm_chick", short="雷の雛", kind="乗り物 3案 (280 鐘銭、三章)",
    title="乗り物「雷の雛」— 雷の翼の巨神の子。羽の冠がぱちぱち光る",
    sub=["三章の雷の翼の小さな親戚。羽の先が青白く光る (光る所は雷)。左の黄色い目盛りが人 (1.8 ブロック)。",
         "一番下の行は、鞍に人が座った姿。"],
    options={
        "a": dict(name="A 背高の雛", desc="長い脚と首の、駆ける雛。羽の冠と尾羽の先が雷を帯びる。王道の騎鳥。"),
        "b": dict(name="B まるまる雛", desc="大きな綿毛の玉に短い脚。広げた小さな翼と、とがった冠。愛嬌と安定。"),
        "c": dict(name="C 脚長の雛", desc="サギのように細く高い。稲妻形に折れた冠と尾の飾り羽、ふさふさの脚。"),
    },
    pw=380, rows=rows_for((0, 30, 0), 3.9),
)

if __name__ == "__main__":
    run(SPEC, build, PALM(), hint=160, pupil=[(30, 30, 60), (240, 248, 255)])
