"""灰の層の主「灯り喰いの主」(lamp_lord) ラフ 3 案。闇を教える主。三度吠えて闇の波、灯りが全部消える。
A 灯の山を吊る黒い口 (浮かぶ巨大な黒い球。口の歯の輪、鎖に食った角灯をいくつも吊る) / B 灯の皮の幕 (翼のように開く黒い膜。中央の穴が口。骨組みの石板の肋が見える)
C 灯守の鳥 (長い首と長い嘴の背高の鳥。背に食った灯りを刺し、嘴が開くと奥に青緑)
骨: root > body > jaw / neck > head / wing_l / wing_r / leg_l / leg_r。青緑 = 食った灯りの残り (光る)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ab_common import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 34, 0)), "jaw": ("body", (0, 30, -10)),
         "neck": ("body", (0, 40, -6)), "head": ("neck", (0, 70, -18)),
         "wing_l": ("body", (-6, 40, 4)), "wing_r": ("body", (6, 40, 4)),
         "leg_l": ("body", (-5, 30, 0)), "leg_r": ("body", (5, 30, 0))}
PAL = PALX({"void": mat([(10, 12, 20), (24, 32, 46), (44, 56, 76), (70, 84, 108)], 0.55, crack=0.0, noise=0.14), "lamp": mat(CALC, 0.5, crack=0.0, noise=0.1), "iron": mat(BASALT, 0.55, crack=0.0, noise=0.1)}, glow=("teal",))


def build_a(k):
    """A 灯の山を吊る黒い口: 虚の黒の巨大な球。前面の口の縁に歯の輪、奥に青緑。下から鎖で食った角灯を吊る。"""
    F = []
    F += part("body", ell("body", "void", 0, 34, 0, 22, 22, 22, segs=14, rings=6))
    F += part("mouth", ell("body", "teal", 0, 34, -21.5, 11, 11, 1.2, segs=10, rings=2))
    for i in range(10):
        a = 2 * math.pi * i / 10
        x, y = 13.5 * math.cos(a), 13.5 * math.sin(a)
        F += part(f"tooth{i}", cone("body", "bone", (x, 34 + y, -15.6), (-x * 0.04, -y * 0.04, -1), 6.0, 1.5, sides=4, rings=2))
    for i, (x, z, l) in enumerate(((-12, -6, 20), (-4, 8, 28), (6, -10, 14), (13, 2, 24))):
        F += part(f"chain{i}", rod("body", "iron", (x, 16, z), (x, 14 - l * 0.3, z), 0.5, 4))
        F += part(f"lamp{i}", ell("body", "lamp", x, 12 - l * 0.3 - 2.2, z, 2.4, 3.2, 2.4, segs=6, rings=2))
        F += part(f"lampcap{i}", ell("body", "iron", x, 12 - l * 0.3 + 1.2, z, 1.6, 0.7, 1.6, segs=5, rings=1))
    for s in (-1, 1):
        for j in range(3):
            y = 30 + j * 6
            F += part(f"hook{s}{j}", arc("body", "slate", (s * 17.0, y, 4), (s * 30.0, y + 2.0, 0), (s * 32.0, y - 6.0, -6), r=0.9, n=6, taper=(2.0, 0.6), sides=5))
    return F, []


def build_b(k):
    """B 灯の皮の幕: 虚の黒の巨大な膜が翼のように開く。石板の肋が走り、中央の穴 (口) の奥に青緑。細い脚で立つ。"""
    F = []
    F += part("body", ell("body", "void", 0, 36, 0, 8.0, 12.0, 6.5, segs=10, rings=4))
    F += part("mouth", ell("body", "teal", 0, 36, -6.2, 4.6, 6.2, 1.0, segs=8, rings=2))
    for i, x in enumerate((-3.4, 0, 3.4)):
        F += part(f"lip{i}", cone("body", "bone", (x, 41.0, -5.2), (0, -1, -0.15), 4.2, 0.9, sides=4, rings=2))
        F += part(f"lipl{i}", cone("body", "bone", (x, 31.0, -5.2), (0, 1, -0.15), 4.2, 0.9, sides=4, rings=2))
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        F += part(b, ell(b, "void", s * 22.0, 40.0, 2.0, 17.0, 20.0, 2.6, segs=10, rings=4))
        for j, (dx, dy) in enumerate(((16, 14), (26, 4), (30, -12), (20, -22))):
            F += part(f"rib{b}{j}", tube(b, "slate", [(s * 6.0, 38.0, 0.6), (s * (6 + dx), 40 + dy, -1.8)], ra=[1.2, 0.5], sides=5, caps=(True, True)))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part("leg" + b, tube(b, "slate", [(s * 4, 28, 0), (s * 5, 15, 2), (s * 5, 1.2, -1.6)], ra=[2.2, 1.6, 1.2], sides=5, caps=(True, True)))
        F += part("foot" + b, ell(b, "iron", s * 5, 1.0, -3.0, 2.2, 1.2, 4.0, segs=6, rings=2))
    F += part("crest", cone("body", "slate", (0, 46.5, 0), (0, 1, -0.2), 9.0, 2.6, sides=5, rings=3))
    return F, []


def build_c(k):
    """C 灯守の鳥: 長い首と長い嘴の背高の鳥。背の骨の串に食った角灯を刺し、嘴の奥は青緑。"""
    F = []
    F += part("body", ell("body", "void", 0, 36, 4, 11, 9, 15, segs=10, rings=4))
    F += part("tail", cone("body", "slate", (0, 36, 16), (0, 0.2, 1), 16.0, 4.6, sides=5, rings=3))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part("thigh" + b, tube(b, "slate", [(s * 5, 33, 2), (s * 6, 20, 5)], ra=[3.0, 2.0], sides=5, caps=(True, True)))
        F += part("shin" + b, tube(b, "slate", [(s * 6, 20, 5), (s * 5, 1.4, -1)], ra=[1.8, 1.1], sides=5, caps=(True, True)))
        for j in (-1, 0, 1):
            F += part(f"toe{b}{j}", cone(b, "bone", (s * 5 + j * 1.2, 1.4, -1), (j * 0.35, 0, -1), 5.0, 0.8, sides=4, rings=2))
    F += part("neck", tube("neck", "slate", [(0, 40, -6), (0, 52, -4), (0, 62, -12), (0, 68, -18)], ra=[4.4, 3.0, 2.6, 2.4], sides=7, caps=(True, True)))
    F += part("head", ell("head", "void", 0, 71, -19, 4.4, 4.0, 5.2, segs=8, rings=3))
    F += part("beak_up", cone("head", "bone", (0, 72.0, -22.5), (0, 0.05, -1), 14.0, 2.4, sides=4, rings=4))
    F += part("beak_lo", cone("head", "bone", (0, 69.0, -22.5), (0, -0.12, -1), 11.0, 1.7, sides=4, rings=4))
    F += part("throat", ell("head", "teal", 0, 70.4, -23.4, 1.6, 0.9, 1.0, segs=6, rings=2))
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        F += part("wing" + b, ell(b, "void", s * 11.0, 40.0, 8.0, 3.2, 10.0, 12.0, segs=8, rings=3))
        for j in range(3):
            F += part(f"feather{b}{j}", cone(b, "slate", (s * 14.5, 34 - j * 3.0, 14 + j * 2.0), (s * 0.15, -0.5, 1), 10.0, 1.8, sides=4, rings=3))
    F += part("spit", rod("body", "iron", (0, 45, 8), (0, 62, 10), 0.8, 5))
    for i, (y, lz) in enumerate(((47, 8), (52, 9), (57, 10))):
        F += part(f"lamp{i}", ell("body", "lamp", 0, y, lz, 2.6, 3.2, 2.6, segs=6, rings=2))
    return F, [eye_at(-3.0, 72.0, -22.4, 0.6), eye_at(3.0, 72.0, -22.4, 0.6)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "howl": {"jaw": (26, 0, 0), "head": (-18, 0, 0), "neck": (-8, 0, 0), "body": (-6, 0, 0)}}

SPEC = dict(
    id="lord_ash", short="灯り喰いの主", kind="灰の層の主 3案",
    title="灰の層の主「灯り喰いの主」— 闇の決まりを教える",
    sub=["三度吠えて闇の波。灯りと光の術の灯りが全部消え、闇のあいだは光の中でしか刃が通らない。",
         "青緑は食った灯りの残り (光る)。左の目盛りは人 (1.8 ブロック)。右下は吠える構え。"],
    labels=["A 灯の山を吊る黒い口", "B 灯の皮の幕", "C 灯守の鳥"],
    poses=POSES,
    options={
        "a": dict(name="A 灯の山を吊る黒い口", desc="浮かぶ巨大な黒い球。歯の輪の口と、食った角灯を吊る鎖。吠えると口が開く。"),
        "b": dict(name="B 灯の皮の幕", desc="黒い膜が翼のように広がる幕。石板の肋が走り、中央の穴が口。広げて光を覆う。"),
        "c": dict(name="C 灯守の鳥", desc="長い首と嘴の背高の鳥。背の串に食った角灯が並ぶ。首を伸ばして灯りを抜く。"),
    },
    extra="闇の言葉 (虚の黒・石板・骨の白・青緑)。",
    budget="主 800〜1500", pw=380,
)
ROWS = [("前", "idle", 180, 8, 400, (0, 36, 0), 5.0, None),
        ("3/4", "idle", 212, 14, 400, (0, 36, 0), 5.0, None),
        ("横", "idle", 270, 6, 400, (0, 36, 0), 5.0, None),
        ("吠える", "howl", 212, 10, 400, (0, 36, 0), 5.0, None)]

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(90, 190, 176), (200, 236, 228)])
