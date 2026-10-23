"""塩の層の主「白殻の主」(shell_lord) ラフ 3 案。重さを教える主。足もとに赤い輪を落とし、輪の中は押しつぶされる。
A 巻貝の巨殻 (赤い畝の巻貝。殻の口から青白い頭がのぞき、這うたび足もとに輪) / B 重りの巨人 (貝殻を背負った太い人型。四肢の赤い輪が重り)
C 二枚貝の顎 (脚で立つ巨大な二枚貝。殻が開いて閉じ、中の赤い真珠が重さの目)
骨: root > body > head / arm_l / arm_r / shell (A) / valve (C)。人の 2 倍強。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ab_common import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 20, 0)), "head": ("body", (0, 40, -8)),
         "arm_l": ("body", (-16, 46, 0)), "arm_r": ("body", (16, 46, 0)), "shell": ("body", (0, 24, 6)),
         "valve": ("body", (0, 26, 12)), "leg_l": ("body", (-8, 20, 0)), "leg_r": ("body", (8, 20, 0))}
PAL = PALX({"pale": mat(SALT, 0.7, crack=0.04, noise=0.08)}, glow=("red",))


def build_a(k):
    """A 巻貝の巨殻: 渦の殻 (三つの輪を重ね、先は尖る)。畝は赤く光る輪。殻の口から青白い頭が首を伸ばす。"""
    F = []
    F += part("foot", ell("body", "sea", 0, 7, -2, 14, 7, 18, segs=9, rings=3))
    F += part("whorl1", ell("shell", "salt", 0, 25, 6, 13.5, 13.5, 14, segs=10, rings=4))
    F += part("whorl2", ell("shell", "salt", 0, 41, 9, 9.5, 9.5, 9.5, segs=9, rings=3))
    F += part("whorl3", ell("shell", "saltc", 0, 52, 11, 6.5, 6.5, 6.5, segs=8, rings=3))
    F += part("apex", cone("shell", "salt", (0, 55, 11), (0, 1, 0.4), 9.0, 3.4, sides=6, rings=3))
    for i, z in enumerate((-1, 6, 13)):
        r = (13.5 ** 2 - (z - 6) ** 2) ** 0.5 + 0.2
        F += part(f"ridge{i}", hoop("shell", "red", 0, 25, z, r, r, r=1.0, n=10, gap=30))
    F += part("lip", ell("shell", "saltc", 0, 20, -9, 9, 8.5, 3.5, segs=8, rings=2))
    F += part("neck", tube("head", "sea", [(0, 14, -12), (0, 26, -20), (0, 30, -25)], ra=[5.4, 4.2, 3.6], sides=7, caps=(True, True)))
    F += part("head", ell("head", "pale", 0, 31, -26, 5.2, 4.6, 5.4, segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"stalk{s}", tube("head", "sea", [(s * 2.4, 34, -25), (s * 4.0, 41, -24)], ra=[1.2, 0.9], sides=5, caps=(True, True)))
        F += part(f"eyeb{s}", ball("head", "pale", (s * 4.0, 41.6, -24), 1.6, segs=6, rings=2))
    for i, x in enumerate((-9, -4.5, 4.5, 9)):
        F += part(f"crys{i}", cone("shell", "salt", (x * 0.8, 21, -9.0), (x * 0.07, 0.1, -1), 6.0 + (i % 2) * 2, 1.4, sides=4, rings=2))
    return F, [eye_at(-4.0, 41.6, -25.4, 0.9), eye_at(4.0, 41.6, -25.4, 0.9)]


def build_b(k):
    """B 重りの巨人: 貝殻を背負った太い人型。短い柱の脚、岩の拳。両手首と両足首に赤い輪 (重り)。"""
    F = []
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part("leg" + b, tube(b, "salt", [(s * 8, 22, 0), (s * 9, 6, -1)], ra=[6.0, 5.4], sides=7, caps=(True, True)))
        F += part("foot" + b, ell(b, "saltc", s * 9, 3.6, -3.2, 6.6, 2.8, 8.5, segs=7, rings=2))
        F += part("anklet" + b, loop(b, "red", s * 9, 8.5, -1.0, 6.8, 6.4, r=1.3, n=9, gap=18, sides=5))
    F += part("belly", ell("body", "saltc", 0, 28, 0, 14, 9, 9.5, segs=9, rings=3))
    F += part("chest", ell("body", "salt", 0, 40, -0.5, 16.5, 11, 10, segs=9, rings=3))
    F += part("shellback", ell("shell", "salt", 0, 40, 12, 12.5, 15, 8, segs=9, rings=3))
    for i, y in enumerate((31, 37, 43, 49)):
        F += part(f"sridge{i}", loop("shell", "saltc", 0, y, 12.0, 12.8 * (1 - ((y - 40) / 15.5) ** 2) ** 0.5, 8.4 * (1 - ((y - 40) / 15.5) ** 2) ** 0.5, r=0.9, n=8, gap=200, at=0))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part("shoulder" + b, ell(b, "salt", s * 17.0, 46.0, 0, 6.0, 5.4, 6.0, segs=8, rings=3))
        F += part("arm" + b, tube(b, "salt", [(s * 17.0, 46, 0), (s * 21.0, 32, -2.0), (s * 21.0, 21, -4.0)], ra=[5.0, 4.6, 4.2], sides=7, caps=(True, True)))
        F += part("fist" + b, ell(b, "saltc", s * 21.0, 15.0, -4.0, 6.2, 6.6, 6.0, segs=8, rings=3))
        F += part("wrist" + b, loop(b, "red", s * 21.0, 21.0, -4.0, 5.6, 5.4, r=1.3, n=9, gap=18, sides=5))
    F += part("head", ell("head", "pale", 0, 55.0, -8.0, 5.0, 4.4, 5.0, segs=8, rings=3))
    F += part("neck", rod("head", "sea", (0, 49.5, -4.0), (0, 54.0, -7.5), 3.4, 7))
    F += part("brow", ell("head", "saltc", 0, 58.2, -9.0, 5.4, 1.5, 4.2, segs=8, rings=2))
    return F, [eye_at(-1.9, 55.6, -12.7, 0.7), eye_at(1.9, 55.6, -12.7, 0.7)]


def build_c(k):
    """C 二枚貝の顎: 太い脚で立つ巨大な二枚貝。上の殻が開閉する。中の赤い真珠が重さの目。前に岩の鋏の腕が二本。"""
    F = []
    F += part("lower", ell("body", "salt", 0, 22, 0, 22, 8, 17, segs=10, rings=3))
    F += part("upper", ell("valve", "salt", 0, 36, 0, 22, 8, 17, segs=10, rings=3))
    F += part("hinge", ell("body", "saltc", 0, 30, 14, 10, 6, 5, segs=7, rings=2))
    for i in range(5):
        xe = -10 + i * 5
        for sgn, nm, bn, cy in ((1, "u", "valve", 36.0), (-1, "l", "body", 22.0)):
            pts = []
            for j in range(7):
                tt = j / 6
                x, z = xe * tt, 11.0 - 25.0 * tt
                q = max(0.03, 1 - (x / 22) ** 2 - (z / 17) ** 2)
                pts.append((x, cy + sgn * (8 * q ** 0.5 + 0.1), z))
            F += part(f"{nm}rib{i}", tube(bn, "saltc", pts, ra=0.9, sides=4, caps=(True, True)))
    F += part("mantle", ell("body", "sea", 0, 29, -1, 17.5, 3.5, 14, segs=9, rings=2))
    F += part("pearl", ball("body", "red", (0, 30, -7), 5.4, segs=8, rings=3))
    for i, x in enumerate((-14, -9, -4, 4, 9, 14)):
        F += part(f"tooth{i}", cone("body", "pale", (x, 29.4, -13.0), (0, 1, -0.4), 4.6, 1.4, sides=4, rings=2))
        F += part(f"utooth{i}", cone("valve", "pale", (x, 33.6, -13.0), (0, -1, -0.4), 4.6, 1.4, sides=4, rings=2))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        for j, z in enumerate((-9, 9)):
            F += part(f"leg{b}{j}", tube(b, "salt", [(s * 12, 22, z), (s * 16, 12, z * 1.3), (s * 17, 1, z * 1.5)], ra=[5.0, 3.8, 3.2], sides=6, caps=(True, True)))
            F += part(f"foot{b}{j}", ell(b, "saltc", s * 17, 1.0, z * 1.5 - 1, 4.0, 1.2, 5.0, segs=6, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part("arm" + b, tube(b, "salt", [(s * 18, 26, -12), (s * 26, 22, -22), (s * 22, 14, -32)], ra=[4.2, 3.6, 3.2], sides=6, caps=(True, True)))
        F += part("claw" + b, ell(b, "saltc", s * 22, 12, -34, 5.0, 5.0, 6.0, segs=7, rings=2))
        F += part("clawt" + b, cone(b, "pale", (s * 22 - s * 2.0, 12, -38), (-s * 0.5, 0, -1), 6.0, 1.6, sides=4, rings=2))
        F += part("clawt2" + b, cone(b, "pale", (s * 22 + s * 2.0, 12, -38), (s * 0.5, 0, -1), 6.0, 1.6, sides=4, rings=2))
    F += part("eyestalks", rod("head", "sea", (0, 38, -9), (0, 46, -10), 1.4, 5))
    F += part("eye", ball("head", "pale", (0, 47, -10), 2.2, segs=7, rings=2))
    return F, [eye_at(0, 47.0, -12.0, 1.1)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "slam": {"body": (8, 0, 0), "arm_l": (-40, 0, 0), "arm_r": (-40, 0, 0), "valve": (-26, 0, 0), "head": (14, 0, 0)}}

SPEC = dict(
    id="lord_salt", short="白殻の主", kind="塩の層の主 3案",
    title="塩の層の主「白殻の主」— 重さの決まりを教える",
    sub=["一人ずつの足もとに赤い輪を落とす。輪の中は押しつぶされる (重さの術なら耐える)。",
         "左の目盛りは人 (1.8 ブロック)。赤く光る所は重さの輪に関わる部分。右下は押しつぶす構え。"],
    labels=["A 巻貝の巨殻", "B 重りの巨人", "C 二枚貝の顎"],
    poses=POSES,
    options={
        "a": dict(name="A 巻貝の巨殻", desc="赤い畝の渦の巨殻を背負って這う。口から青白い頭がのぞく。元の貝守に一番近い。"),
        "b": dict(name="B 重りの巨人", desc="貝を背負った太い人型。両手足の赤い輪が重り。拳を落とすと輪が広がる。"),
        "c": dict(name="C 二枚貝の顎", desc="脚で立つ巨大な二枚貝。殻を開いて閉じ、中の赤い真珠が輪を落とす目になる。"),
    },
    extra="塩の言葉 (白・塩の皮・海の緑)。赤は重さの輪 (光る)。",
    budget="主 800〜1500", pw=380,
)
ROWS = [("前", "idle", 180, 8, 400, (0, 28, 0), 6.0, None),
        ("3/4", "idle", 212, 14, 400, (0, 28, 0), 6.0, None),
        ("横", "idle", 270, 6, 400, (0, 28, 0), 6.0, None),
        ("押しつぶす", "slam", 212, 10, 400, (0, 28, 0), 6.0, None)]

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(200, 230, 240), (255, 255, 255)])
