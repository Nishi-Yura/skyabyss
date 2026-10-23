"""雷の層の主「鼓動まねの主」(mimic_lord) ラフ 3 案。鼓動を教える主。本物と三つの写しに散る。鼓動の音は本物だけ。
A 心臓むき出しの袋 (大きな肉の袋。胸の窓から青白い心臓がのぞき、背に写しの芽が三つ生える) / B 二つ顔の人まね (人の形をまねた細長い影。前と後ろに顔があり、胸の窓に心臓)
C 鼓動の花 (根の脚で立つ肉の球。上へ六枚の花弁の腕が開き、中の心臓が脈を打つ。写しは垂れた蕾)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。青白い光る所 = 本物の鼓動。写しは同じ形で光らない。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ab_common import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 28, 0)), "head": ("body", (0, 46, -2)),
         "arm_l": ("body", (-8, 46, 0)), "arm_r": ("body", (8, 46, 0)),
         "leg_l": ("body", (-4, 26, 0)), "leg_r": ("body", (4, 26, 0))}
PAL = PALX({"vein": mat(BLOODL, 0.6, crack=0.0, noise=0.1)}, glow=("bolt",))


def build_a(k):
    """A 心臓むき出しの袋: 肉の大袋。胸の窓の枠から心臓が半分はみ出す。背に写しの芽 (光らない) が三つ。下の口に歯。"""
    F = []
    F += part("sac", ell("body", "flesh", 0, 30, 0, 19, 22, 16, segs=10, rings=4))
    for i, y in enumerate((20, 28, 36, 44)):
        q = (1 - ((y - 30) / 22.5) ** 2) ** 0.5
        F += part(f"band{i}", loop("body", "tuff", 0, y, 0, 19.3 * q, 16.3 * q, r=1.1, n=10, gap=120, at=0))
    F += part("frame", hoop("body", "tuff", 0, 34, -15.2, 9.4, 9.4, r=1.5, n=10, gap=16))
    F += part("heart", ball("body", "bolt", (0, 34, -14.0), 7.0, segs=8, rings=3))
    F += part("heartv", tube("body", "bolt", [(0, 41, -14.0), (0, 48, -11.0)], ra=[2.0, 1.4], sides=5, caps=(True, True)))
    F += part("mouth", ell("body", "hollow", 0, 17, -15.5, 10, 3.2, 2.6, segs=8, rings=2))
    for i, x in enumerate((-8, -4, 0, 4, 8)):
        F += part(f"tooth{i}", cone("body", "bone", (x, 19.0, -17.4), (0, -1, -0.3), 3.6, 1.0, sides=4, rings=2))
    for i, (x, z) in enumerate(((-12, -8), (0, -12), (12, -8), (-12, 8), (0, 12), (12, 8))):
        b = "leg_l" if x <= 0 else "leg_r"
        F += part(f"leg{i}", tube(b, "vein", [(x * 0.5, 15, z * 0.5), (x * 1.2, 4, z * 1.2)], ra=[4.4, 3.0], sides=6, caps=(True, True)))
        F += part(f"pad{i}", ell(b, "tuff", x * 1.2, 2.4, z * 1.2, 4.0, 1.6, 4.0, segs=6, rings=2))
    for i, (x, y, z) in enumerate(((-10, 47, 6), (0, 51, 8), (10, 47, 6))):
        F += part(f"bud{i}", ell("body", "flesh", x, y, z, 5.0, 5.6, 5.0, segs=7, rings=3))
    eyes = ((-8, 44), (0, 47), (8, 44))
    for i, (x, y) in enumerate(eyes):
        F += part(f"eye{i}", ell("body", "tuff", x, y, -9.6, 3.0, 3.0, 2.0, segs=6, rings=2))
    return F, [eye_at(x, y, -11.7, 1.1) for x, y in eyes]


def build_b(k):
    """B 二つ顔の人まね: 細長い人の形。前と後ろの両方に顔。腕は膝まで届き、指は三本の長い爪。胸の窓に心臓。"""
    F = []
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part("thigh" + b, tube(b, "tuff", [(s * 4.6, 27, 0), (s * 5.0, 15, 1.6)], ra=[3.0, 2.4], sides=6, caps=(True, True)))
        F += part("shin" + b, tube(b, "tuff", [(s * 5.0, 15, 1.6), (s * 5.0, 2.4, -0.8)], ra=[2.4, 1.6], sides=6, caps=(True, True)))
        F += part("foot" + b, ell(b, "oxcu", s * 5.0, 1.0, -2.6, 2.4, 1.2, 5.0, segs=6, rings=2))
    F += part("hip", ell("body", "tuff", 0, 28, 0, 7.4, 3.8, 4.4, segs=8, rings=2))
    F += part("torso", ell("body", "flesh", 0, 39, 0, 8.0, 12.5, 5.2, segs=9, rings=3))
    F += part("frame", hoop("body", "oxcu", 0, 40, -4.8, 5.2, 5.2, r=1.1, n=8, gap=16))
    F += part("heart", ball("body", "bolt", (0, 40, -3.8), 3.8, segs=7, rings=3))
    F += part("collar", ell("body", "oxcu", 0, 50.5, 0, 9.2, 1.6, 4.8, segs=8, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part("upper" + b, tube(b, "flesh", [(s * 9.0, 49, 0), (s * 12.0, 36, -1.0)], ra=[2.6, 2.0], sides=6, caps=(True, True)))
        F += part("fore" + b, tube(b, "flesh", [(s * 12.0, 36, -1.0), (s * 12.5, 16, -3.0)], ra=[2.0, 1.5], sides=6, caps=(True, True)))
        F += part("palm" + b, ell(b, "oxcu", s * 12.5, 14.0, -3.2, 2.6, 2.4, 2.0, segs=6, rings=2))
        for j in (-1, 0, 1):
            F += part(f"claw{b}{j}", cone(b, "bone", (s * 12.5 + j * 1.4, 12.5, -3.2), (j * 0.15, -1, -0.2), 7.0, 0.7, sides=4, rings=3))
    F += part("neck", rod("head", "flesh", (0, 50, 0), (0, 54, 0), 2.6, 6))
    F += part("head", ell("head", "tuff", 0, 58.0, 0, 5.2, 6.4, 5.4, segs=8, rings=4))
    F += part("mouth", ell("head", "hollow", 0, 54.6, -3.6, 2.8, 0.7, 0.5, segs=6, rings=1))
    F += part("mouth2", ell("head", "hollow", 0, 54.6, 3.6, 2.8, 0.7, 0.5, segs=6, rings=1))
    for i, x in enumerate((-2.2, 2.2)):
        F += part(f"face_f{i}", ell("head", "hollow", x, 59.4, -5.1, 1.4, 1.5, 0.6, segs=6, rings=2))
        F += part(f"face_b{i}", ell("head", "hollow", x, 59.4, 5.1, 1.4, 1.5, 0.6, segs=6, rings=2))
    return F, [eye_at(x, 59.4, -5.5, 0.7) for x in (-2.2, 2.2)] + [eye_at(x, 59.4, 5.5, 0.7) for x in (-2.2, 2.2)]


def build_c(k):
    """C 鼓動の花: 根の脚で立つ肉の球。六枚の花弁の腕が上へ開き、先に光る芽。中の心臓がむき出しで脈を打つ。写しは垂れた蕾。"""
    F = []
    F += part("bulb", ell("body", "flesh", 0, 24, 0, 12, 14, 12, segs=10, rings=4))
    F += part("heart", ball("body", "bolt", (0, 35, 0), 6.4, segs=8, rings=3))
    for i in range(6):
        a = math.radians(i * 60 + 15)
        dx, dz = math.sin(a), -math.cos(a)
        p0 = (dx * 6, 34, dz * 6)
        p1 = (dx * 22, 40, dz * 22)
        p2 = (dx * 24, 62, dz * 24)
        F += part(f"petal{i}", arc("body", "vein", p0, p1, p2, r=1.0, n=8, taper=(3.2, 1.2), sides=5))
        F += part(f"tip{i}", ball("body", "bolt", p2, 1.9, segs=6, rings=2))
    for i in range(5):
        a = math.radians(i * 72)
        dx, dz = math.sin(a), -math.cos(a)
        F += part(f"root{i}", arc("body", "tuff", (dx * 6, 14, dz * 6), (dx * 20, 10, dz * 20), (dx * 24, 0.8, dz * 26), r=1.0, n=7, taper=(4.0, 1.6), sides=5))
    for i, (x, z, hh) in enumerate(((-14, 8, 16), (14, 10, 12), (0, -14, 20))):
        F += part(f"bud_stalk{i}", arc("body", "vein", (x * 0.6, 28, z * 0.6), (x * 1.0, 38, z * 1.0), (x * 1.3, hh - 0.6, z * 1.3), r=1.0, n=6, taper=(1.6, 1.0), sides=4))
        F += part(f"bud{i}", ell("body", "flesh", x * 1.3, hh - 3.2, z * 1.3, 4.6, 5.4, 4.6, segs=7, rings=3))
    for i, x in enumerate((-4, 4)):
        F += part(f"eye{i}", ell("body", "tuff", x, 26, -11.4, 2.4, 2.4, 1.6, segs=6, rings=2))
    F += part("mouth", ell("body", "hollow", 0, 19, -11.6, 5.0, 1.8, 1.4, segs=7, rings=2))
    return F, [eye_at(x, 26.0, -12.6, 0.9) for x in (-4, 4)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "split": {"body": (0, 0, 8), "head": (0, 0, -10), "arm_l": (0, 0, 22), "arm_r": (0, 0, -22)}}

SPEC = dict(
    id="lord_storm", short="鼓動まねの主", kind="雷の層の主 3案",
    title="雷の層の主「鼓動まねの主」— 鼓動の決まりを教える",
    sub=["本物と三つの写しに散る。鼓動の音は本物だけ (鼓動の術で本物の輪郭)。写しは叩くと弾けて赤い輪。",
         "青白く光る所が本物の鼓動。写しは同じ形で光らない。左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 心臓むき出しの袋", "B 二つ顔の人まね", "C 鼓動の花"],
    poses=POSES,
    options={
        "a": dict(name="A 心臓むき出しの袋", desc="胸の窓から心臓がはみ出た肉の大袋。背の三つの芽が、散って写しになる。"),
        "b": dict(name="B 二つ顔の人まね", desc="前後に顔のある細長い人型。膝まで届く腕と三本の爪。写しは同じ背丈で並ぶ。"),
        "c": dict(name="C 鼓動の花", desc="根の脚の球に六枚の花弁の腕。花の中で心臓が脈打つ。垂れた蕾が写しになる。"),
    },
    extra="雷の言葉 (凝灰岩・酸化した銅・肉・白い雷)。",
    budget="主 800〜1500", pw=380,
)
ROWS = [("前", "idle", 180, 8, 400, (0, 33, 0), 5.5, None),
        ("3/4", "idle", 212, 14, 400, (0, 33, 0), 5.5, None),
        ("横", "idle", 270, 6, 400, (0, 33, 0), 5.5, None)]

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(140, 200, 255), (230, 245, 255)])
