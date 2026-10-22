"""骨の層の主「肋の主」(rib_lord) ラフ 3 案。灯り (灯台・光の術) を教える主。四つの灯台を吹き消しに来る。
A 肋の檻の灯守 (胸の檻に消えた灯りをしまった背の高い人型。吹き消した灯りが檻に増える) / B 吹き消す頭蓋 (肋の脚で歩く巨大な頭蓋。顎が開いて灯りを吹く)
C 肋のふいご (四つ足の這い。胴が肋の輪を重ねたふいごで、吸って吹く)
骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r (B・C は脚の数が違う)。身長は人の 2 倍強 (A 60 単位)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ab_common import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 26, 0)), "head": ("body", (0, 50, -2)),
         "jaw": ("head", (0, 49, -5)), "arm_l": ("body", (-10, 47, 0)), "arm_r": ("body", (10, 47, 0)),
         "leg_l": ("body", (-5, 27, 0)), "leg_r": ("body", (5, 27, 0)),
         "leg_a": ("body", (-9, 24, -8)), "leg_b": ("body", (9, 24, -8)), "leg_c": ("body", (-9, 24, 8)), "leg_d": ("body", (9, 24, 8))}
PAL = PALX({"lampg": mat(CALC, 0.5, crack=0.0, noise=0.1)}, glow=("red",))


def skull(F, z0=-2.0, y0=51.0, s=1.0, jaw_open=False, bone="bone"):
    F += part("skull", ellipsoid("head", bone, (0, y0, z0), (5.2 * s, 5.6 * s, 5.6 * s), segs=9, rings=4))
    F += part("jaw", ellipsoid("jaw", "calc", (0, y0 - 4.4 * s, z0 - 1.6 * s), (3.6 * s, 1.8 * s, 3.6 * s), segs=8, rings=2))
    F += part("sock_l", ellipsoid("head", "hollow", (-2.1 * s, y0 + 0.6 * s, z0 - 5.2 * s), (1.5 * s, 1.5 * s, 0.7 * s), segs=6, rings=2))
    F += part("sock_r", ellipsoid("head", "hollow", (2.1 * s, y0 + 0.6 * s, z0 - 5.2 * s), (1.5 * s, 1.5 * s, 0.7 * s), segs=6, rings=2))
    return F, [eye_at(-2.1 * s, y0 + 0.6 * s, z0 - 5.45 * s, 0.7), eye_at(2.1 * s, y0 + 0.6 * s, z0 - 5.45 * s, 0.7)]


def leg_pair(F, m="bone", top=28.0, w=5.0, r=2.6):
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part("thigh" + b, tube(b, m, [(s * w, top, 0), (s * w, 15.0, 1.5)], ra=[r, r * 0.85], sides=6, caps=(True, True)))
        F += part("shin" + b, tube(b, m, [(s * w, 15.0, 1.5), (s * w, 2.0, -1.0)], ra=[r * 0.85, r * 0.6], sides=6, caps=(True, True)))
        F += part("foot" + b, ellipsoid(b, "calc", (s * w, 1.2, -3.0), (2.4, 1.3, 4.6), segs=7, rings=3))


def build_a(k):
    """A 肋の檻の灯守: 胸の肋の檻に、吹き消した灯りがぶら下がる。右手に灯りを引っかける鉤の杖。"""
    F = []
    leg_pair(F)
    F += part("pelvis", ellipsoid("body", "bone", (0, 27.5, 0), (7.0, 3.0, 4.2), segs=8, rings=3))
    F += part("spine", rod("body", "calc", (0, 28, 5.8), (0, 49, 5.2), 1.7, 5))
    for i, (y, rx, rz) in enumerate(((31, 6.6, 5.4), (34.5, 8.0, 6.4), (38, 8.6, 6.8), (41.5, 8.2, 6.4), (45, 7.0, 5.6))):
        F += part(f"rib{i}", loop("body", "bone", 0, y, -0.6, rx, rz, r=1.05, n=8, gap=4))
    F += part("sternum", rod("body", "calc", (0, 30.5, -6.0), (0, 46, -5.2), 0.9, 4))
    for i, (x, y) in enumerate(((-3.4, 38.0), (3.4, 38.0), (0.0, 34.0), (0.0, 42.0))):
        F += part(f"lampcord{i}", rod("body", "calc", (x, 46.5, 1.0), (x, y + 2.6, 0.2), 0.3, 4))
        F += part(f"lamp{i}", ellipsoid("body", "hollow", (x, y, 0.2), (1.9, 2.6, 1.9), segs=6, rings=2))
        F += part(f"lampcap{i}", ellipsoid("body", "calc", (x, y + 2.7, 0.2), (1.3, 0.7, 1.3), segs=5, rings=1))
    F += part("collar", ellipsoid("body", "calc", (0, 48.2, 0.6), (8.4, 1.8, 4.2), segs=8, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part("shoulder" + b, ellipsoid(b, "bone", (s * 10.2, 47.0, 0.4), (3.0, 3.0, 3.0), segs=7, rings=3))
        F += part("upper" + b, tube(b, "bone", [(s * 10.2, 47, 0.4), (s * 11.4, 36, -1.0)], ra=[2.1, 1.7], sides=6, caps=(True, True)))
        F += part("fore" + b, tube(b, "bone", [(s * 11.4, 36, -1.0), (s * 11.8, 24.0, -6.0)], ra=[1.7, 1.3], sides=6, caps=(True, True)))
        F += part("hand" + b, ellipsoid(b, "calc", (s * 11.8, 22.6, -6.6), (1.9, 2.1, 1.7), segs=6, rings=2))
    F += part("staff", rod("arm_r", "calc", (11.8, 7.0, -6.6), (11.8, 60.0, -6.6), 0.8, 5))
    F += part("hook", arc("arm_r", "calc", (11.8, 60.0, -6.6), (11.8, 66.0, -6.6), (6.0, 64.0, -6.6), r=0.8, taper=(0.8, 0.5)))
    F += part("hook_lamp", ellipsoid("arm_r", "hollow", (6.0, 61.0, -6.6), (1.5, 2.2, 1.5), segs=6, rings=2))
    F += part("lantern", ellipsoid("arm_l", "hollow", (-11.8, 18.6, -6.6), (2.6, 3.4, 2.6), segs=6, rings=2))
    F += part("lantern_top", ellipsoid("arm_l", "calc", (-11.8, 22.2, -6.6), (1.6, 0.9, 1.6), segs=5, rings=1))
    F, e = skull(F)
    for i, x in enumerate((-3.4, 0.0, 3.4)):
        F += part(f"crown{i}", cone("head", "calc", (x, 56.0, -1.5), (x * 0.1, 1, 0.1), 4.0 + (2.0 if x == 0 else 0), 0.9, sides=4, rings=2))
    F += part("neck", rod("body", "bone", (0, 45.5, 1.8), (0, 48.5, 1.0), 1.8, 5))
    return F, e


def build_b(k):
    """B 吹き消す頭蓋: 巨大な頭蓋が肋の弧の脚四本で歩く。顎が大きく開いて灯りを吹く。"""
    F = []
    F += part("body", ellipsoid("body", "bone", (0, 34, 4.0), (9.0, 7.0, 8.0), segs=9, rings=4))
    for i, y in enumerate((30.5, 34.0, 37.5)):
        F += part(f"rib{i}", loop("body", "calc", 0, y, 4.0, 9.6, 8.6, r=0.9, n=8))
    for b, (sx, sz) in (("leg_a", (-1, -1)), ("leg_b", (1, -1)), ("leg_c", (-1, 1)), ("leg_d", (1, 1))):
        p0 = (sx * 8.0, 33.0, 4.0 + sz * 5.0)
        p1 = (sx * 17.0, 46.0, 4.0 + sz * 9.0)
        p2 = (sx * 21.0, 1.0, 4.0 + sz * 12.0)
        F += part("leg" + b, arc(b, "bone", p0, p1, p2, r=1.2, n=8, taper=(2.2, 1.0), sides=5))
        F += part("foot" + b, ellipsoid(b, "calc", (sx * 21.0, 0.8, 4.0 + sz * 12.0 - 1.2), (1.8, 0.9, 2.8), segs=6, rings=2))
    F += part("skull", ellipsoid("head", "bone", (0, 46.0, -9.0), (11.0, 9.5, 11.0), segs=10, rings=4))
    F += part("jaw", ellipsoid("jaw", "calc", (0, 38.0, -11.5), (8.0, 3.0, 8.8), segs=9, rings=3))
    F += part("throat", ellipsoid("head", "hollow", (0, 41.0, -17.6), (5.6, 3.6, 2.0), segs=8, rings=3))
    for i, x in enumerate((-6, -3, 0, 3, 6)):
        F += part(f"tooth{i}", cone("head", "calc", (x * 1.1, 41.4, -15.0 - (1.6 if x == 0 else 0)), (0, -1, -0.2), 3.0, 0.9, sides=4, rings=2))
    F += part("sock_l", ellipsoid("head", "hollow", (-4.6, 49.0, -18.4), (2.8, 2.8, 1.0), segs=6, rings=2))
    F += part("sock_r", ellipsoid("head", "hollow", (4.6, 49.0, -18.4), (2.8, 2.8, 1.0), segs=6, rings=2))
    F += part("neckrib", rod("body", "bone", (0, 36, -2.0), (0, 40, -5.0), 3.4, 6, ))
    for i, x in enumerate((-8.0, 8.0)):
        F += part(f"horn{i}", cone("head", "calc", (x * 0.9, 50.0, -7.0), (x * 0.07, 1, 0.1), 9.0, 1.6, sides=5, rings=3))
    return F, [eye_at(-4.6, 49.0, -19.0, 1.2), eye_at(4.6, 49.0, -19.0, 1.2)]


def build_c(k):
    """C 肋のふいご: 胴が肋の輪を重ねたふいご (吸うと開き、吹くと閉じる)。四つ足で這い、小さな頭が前へ伸びる。"""
    F = []
    rings_ = [(-12, 4.6), (-8, 7.4), (-3.5, 9.0), (1.0, 9.6), (5.5, 9.0), (10, 7.2), (13.5, 4.4)]
    for i, (z, r) in enumerate(rings_):
        F += part(f"hoop{i}", hoop("body", "bone", 0, 33, z, r, r * 0.95, r=1.1, n=8))
    F += part("spine", tube("body", "calc", [(0, 33 + r * 0.95 - 0.4, z) for z, r in rings_], ra=1.6, sides=5, caps=(True, True)))
    F += part("skin", ellipsoid("body", "flesh", (0, 33, 0.5), (7.2, 7.2, 12.0), segs=8, rings=3))
    for b, (sx, sz) in (("leg_a", (-1, -1)), ("leg_b", (1, -1)), ("leg_c", (-1, 1)), ("leg_d", (1, 1))):
        p0 = (sx * 7.0, 36.0, sz * 9.0)
        p1 = (sx * 17.0, 30.0, sz * 12.0)
        p2 = (sx * 15.0, 1.0, sz * 14.0)
        F += part("arm" + b, arc(b, "bone", p0, p1, p2, r=1.3, n=8, taper=(2.6, 1.0), sides=5))
        for j in range(3):
            F += part(f"claw{b}{j}", cone(b, "calc", (sx * 15.0 + (j - 1) * 1.4, 1.2, sz * 14.0 - 0.3), (0, 0.1, -1), 3.2, 0.6, sides=4, rings=2))
    F += part("neck", tube("body", "bone", [(0, 34, -11.0), (0, 38, -18.0), (0, 40, -24.0)], ra=[3.2, 2.4, 2.0], sides=6, caps=(True, True)))
    F += part("skull", ellipsoid("head", "bone", (0, 41.0, -27.0), (4.4, 4.8, 5.2), segs=8, rings=3))
    F += part("jaw", ellipsoid("jaw", "calc", (0, 37.4, -29.0), (3.0, 1.4, 3.4), segs=7, rings=2))
    F += part("sock_l", ellipsoid("head", "hollow", (-1.8, 42.0, -31.8), (1.3, 1.3, 0.6), segs=6, rings=2))
    F += part("sock_r", ellipsoid("head", "hollow", (1.8, 42.0, -31.8), (1.3, 1.3, 0.6), segs=6, rings=2))
    F += part("mouth", ellipsoid("jaw", "hollow", (0, 38.6, -31.6), (2.2, 0.9, 0.7), segs=6, rings=2))
    F += part("tail", arc("body", "calc", (0, 33, 13.0), (0, 40, 22.0), (0, 52, 24.0), r=1.0, taper=(2.2, 0.8)))
    F += part("tail_lamp", ellipsoid("body", "hollow", (0, 51.6, 24.5), (1.8, 2.6, 1.8), segs=6, rings=2))
    return F, [eye_at(-1.8, 42.0, -32.1, 0.6), eye_at(1.8, 42.0, -32.1, 0.6)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "blow": {"head": (-14, 0, 0), "jaw": (30, 0, 0), "body": (6, 0, 0)}}

SPEC = dict(
    id="lord_rib", short="肋の主", kind="骨の層の主 3案",
    title="骨の層の主「肋の主」— 灯りの決まりを教える",
    sub=["四つの灯台を吹き消しに来る。灯りの中でだけ刃が通る。いまは「肋の番」を大きくしただけ。",
         "左の目盛りは人 (1.8 ブロック)。右下の「吹く」は灯りを吹き消す構え。"],
    labels=["A 肋の檻の灯守", "B 吹き消す頭蓋", "C 肋のふいご"],
    poses=POSES,
    options={
        "a": dict(name="A 肋の檻の灯守", desc="胸の肋の檻に、消えた灯りをぶら下げた背高の人型。右手の鉤の杖で灯台の火を落とす。"),
        "b": dict(name="B 吹き消す頭蓋", desc="肋の弧の四本脚で歩く巨大な頭蓋。顎が開いて灯りを吹く。灯台より大きい。"),
        "c": dict(name="C 肋のふいご", desc="胴が肋の輪を重ねたふいごの四つ這い。長い首の小さな頭で灯台へ這い寄る。"),
    },
    extra="骨の言葉 (骨・方解石・灰・洞の黒)。",
    budget="主 800〜1500", pw=380,
)
ROWS = [("前", "idle", 180, 8, 400, (0, 33, 0), 6.2, None),
        ("3/4", "idle", 212, 14, 400, (0, 33, 0), 6.2, None),
        ("横", "idle", 270, 6, 400, (0, 33, 0), 6.2, None),
        ("吹く", "blow", 212, 10, 400, (0, 33, 0), 6.2, None)]

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(200, 60, 60), (255, 200, 200)])
