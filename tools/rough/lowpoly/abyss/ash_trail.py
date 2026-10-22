"""灰の這い跡 (ash_trail) 低ポリ ラフ 3 案。見えない這うもの。見えている時は足跡と灰の埃だけ。灯りが当たると形が出る。
A 細い這う体 (長い指の四足、顔は穴) / B 灰の皮 (薄く広がる灰の膜が縁を反らせて這う、中央に裂け目の口) / C 手だけの這い (長い手が二本、背は丸く屈む)
見えない時の姿: 部品 prints (床の足跡・引きずり跡・灰の埃) だけを描く。ROWS の「見えない時」行がそれ。
骨: root > prints (床の痕、別骨) / body > head / leg_l1..2 / leg_r1..2 (A)、body のみ (B)、body > arm_l / arm_r (C)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "prints": ("root", (0, 0, 0)), "body": ("root", (0, 2.2, 0)),
         "head": ("body", (0, 2.6, -4.0)), "leg_l1": ("body", (1.6, 1.6, -2.0)), "leg_r1": ("body", (-1.6, 1.6, -2.0)),
         "leg_l2": ("body", (1.6, 1.6, 2.0)), "leg_r2": ("body", (-1.6, 1.6, 2.0)),
         "arm_l": ("body", (2.0, 2.4, -2.0)), "arm_r": ("body", (-2.0, 2.4, -2.0))}
PAL = {"mats": {
    "ash": mat(ASH, 0.55, crack=0.0, noise=0.22),
    "basalt": mat(BASALT, 0.55, crack=0.08, noise=0.18),
    "char": mat(CHAR, 0.5, crack=0.0, noise=0.1),
    "dust": mat([(26, 24, 26), (46, 44, 46), (68, 64, 64), (90, 86, 84)], 0.5, crack=0.0, noise=0.3, streak=0.0),
    "ember": mat(EMBER, 0.75, crack=0.0, noise=0.05, min=2),
}, "glow_mats": ["ember"]}


def prints(k):
    """床の足跡 (平たい楕円体) と引きずり跡・埃。見えていない時はこれだけが残る。"""
    F = []
    for i, (x, z) in enumerate(((-3.4, -4.0), (3.2, -2.4), (-3.2, 0.4), (3.4, 2.4), (-3.2, 6.2), (3.2, 7.0))):
        F += part("prints", ellipsoid("prints", "dust", (x, 0.12, z), (0.9, 0.14, 1.3), segs=6, rings=1))
    for i, (x, z) in enumerate(((-1.0, -8.0), (1.6, -10.2), (-0.8, -12.6))):
        F += part("prints", ellipsoid("prints", "dust", (x, 0.1, z), (1.7, 0.1, 0.5), segs=6, rings=1))
    return F


def build_a(k):
    """A 細い這う体: 長い四足。頭は顔がなく、穴だけ。灯りで見えた時は背が低く、指が長い。"""
    F = prints(k)
    F += part("body", ellipsoid("body", "ash", (0, 2.4, 0), (1.8, 1.0, 3.4), segs=8, rings=2))
    F += part("head", ellipsoid("head", "char", (0, 2.5, -4.2), (1.0, 0.8, 1.1), segs=6, rings=2))
    F += part("mouth", ellipsoid("head", "char", (0, 2.0, -5.0), (0.5, 0.2, 0.4), segs=4, rings=1))
    for nm, s, z in (("l1", 1, -2.0), ("r1", -1, -2.0), ("l2", 1, 2.0), ("r2", -1, 2.0)):
        b = f"leg_{nm}"
        F += part(b, tube(b, "basalt", [(s * 1.6, 1.6, z), (s * 3.4, 1.0, z - 0.8), (s * 4.4, 0.1, z - 1.6)],
                          ra=[0.9, 0.72, 0.32], sides=8, caps=(True, True)))
    F += part("ember_l", ellipsoid("head", "ember", (-0.4, 2.8, -5.0), (0.25, 0.2, 0.2), segs=4, rings=1))
    F += part("ember_r", ellipsoid("head", "ember", (0.4, 2.8, -5.0), (0.25, 0.2, 0.2), segs=4, rings=1))
    return F, [eye(-0.4, 2.8, -5.2, 0.2), eye(0.4, 2.8, -5.2, 0.2)]


def build_b(k):
    """B 灰の皮: 薄い灰の膜。縁が反って持ち上がり、中央に裂け目の口。這うと縁が波打つ。"""
    F = prints(k)
    R = [ring(0.4, 3.4, -4.6, 4.6, n=10), ring(0.9, 3.8, -4.2, 4.2, n=10), ring(1.5, 2.8, -3.6, 3.6, n=10)]
    F += part("body", loft("body", "ash", R, caps=("char", "ash")))
    F += part("rim", tube("body", "ash", [(-3.6, 1.4, -4.2), (-3.8, 2.6, -1.0), (-3.6, 2.2, 2.4), (-3.2, 0.6, 4.4)], ra=[0.5, 0.45, 0.4, 0.2], sides=8, caps=(True, True)))
    F += part("rim_r", tube("body", "ash", [(3.6, 1.4, -4.2), (3.8, 2.6, -1.0), (3.6, 2.2, 2.4), (3.2, 0.6, 4.4)], ra=[0.5, 0.45, 0.4, 0.2], sides=8, caps=(True, True)))
    F += part("maw", ellipsoid("body", "char", (0, 1.7, -4.4), (1.0, 0.2, 0.4), segs=5, rings=1))
    F += part("ember", ellipsoid("body", "ember", (0, 1.8, -4.6), (0.4, 0.1, 0.1), segs=4, rings=1))
    return F, []


def build_c(k):
    """C 手だけの這い: 背の丸い屈み、長い手が二本、手の指は床をつかむ。"""
    F = prints(k)
    F += part("body", ellipsoid("body", "ash", (0, 3.0, 0.8), (2.6, 2.2, 2.6), segs=10, rings=4))
    F += part("head", ellipsoid("head", "char", (0, 3.6, -2.4), (1.2, 1.0, 1.2), segs=6, rings=2))
    for s, nm in ((1, "l"), (-1, "r")):
        b = f"arm_{nm}"
        F += part(b, tube(b, "basalt", [(s * 2.2, 2.2, -1.8), (s * 4.6, 1.2, -4.0), (s * 6.4, 0.1, -6.2)],
                          ra=[0.6, 0.45, 0.18], sides=8, caps=(True, True)))
        for j, dz in enumerate((-0.6, 0.0, 0.6)):
            tip = (s * 6.4, 0.1, -6.2 + dz * 2.0)
            F += part(b, cone(b, "char", (s * 6.2, 0.15, -6.0 + dz), (s * 0.6, 0.0, -0.3), 1.0, 0.22, sides=3, rings=1))
    F += part("ember", ellipsoid("head", "ember", (0, 3.8, -3.6), (0.3, 0.2, 0.2), segs=4, rings=1))
    return F, [eye(-0.4, 3.8, -3.6, 0.2), eye(0.4, 3.8, -3.6, 0.2)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "crawl": {"body": (0, 0, 6), "head": (-10, 0, 0)}}

LATER = {'a': '歪み: 足が一本ずつ長くなり、顔の穴が口に開く / 崩れ: 体が灰に溶けて足跡だけが動く。', 'b': '歪み: 皮の縁が割れて指のように立つ / 崩れ: 膜が床いっぱいに広がり、踏むと痕が追ってくる。', 'c': '歪み: 手が三本に増える / 崩れ: 手だけになり、背の影が壁を這う。'}

SPEC = dict(
    exempt=("prints",),
    id="ash_trail", short="灰の這い跡", kind="灰の層の敵 3案 (見えない)",
    title="灰の這い跡 (ash_trail) — 見えない這うもの。痕と、灯りで出る形・低ポリ 3 案",
    sub=["灰の層の敵。姿は見えず、足跡・引きずり跡・灰の埃だけが床に残る (「見えない時」の行)。灯りが当たった時だけ形が出る。",
         "左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 細い這う体", "B 灰の皮", "C 手だけの這い"],
    poses=POSES,
    options={
        "a": dict(name="A 細い這う体", desc="灰色の細い背に長い四本の脚、顔は穴だけで目は熾の点が二つ。灯りで出ると、脚の指が床をつかんで進むのが見える。足跡は細い指の形が残る。"),
        "b": dict(name="B 灰の皮", desc="薄い灰の膜が縁を反らせて這う。中央の裂け目が口で、灯りの下では縁の波打ちだけが見える。痕は引きずった縁の筋で、灰の皮が通った道として読める。"),
        "c": dict(name="C 手だけの這い", desc="丸く屈んだ背と長い二本の手。灯りで出るのは手と背だけで、頭は影の中。床の痕は手のひらの跡 (指の形) で、手が先に来るのが分かる。"),
    },
    extra="灰の層 (余燼) の見えない敵。見えない時の痕は部品 prints (床の足跡・引きずり跡・灰の埃)。",
    budget="地上の敵 400〜600 (足跡も含む)",
    later=LATER,
    pw=400, atlas_h=160,
)
ROWS = [("前 3/4 (灯りの中)", "idle", 212, 18, 230, (0, 2.6, -1), 28.0, None),
        ("横 (灯りの中)", "idle", 270, 8, 230, (0, 2.6, -1), 28.0, None),
        ("見えない時 (痕だけ・上から)", "idle", 200, 62, 230, (0, 0, -2), 24.0, {"prints"}),
        ("這う (灯りの中)", "crawl", 240, 14, 230, (0, 2.6, -1), 28.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=96)
