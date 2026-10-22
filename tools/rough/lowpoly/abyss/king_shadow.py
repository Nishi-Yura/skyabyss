"""歪んだ王 (影の型) ラフ 3 案。体は「前の私の影」の崩れの姿を大きくしたもの。顔は作らない (プレイヤーの顔と似せない)。技: 突進・手下 (名のない形)・薙ぎ払い・雷の矢。
A 濡れた影の巨人 (腕が膝を越えて長い虚の黒の人影。顎と指から細い糸が垂れる) / B 三重の影 (首の上に顔のない頭が三つ重なり、腕は三対。前の私が重なった姿)
C 這いずる影 (四つ足で地を這い、頭を低く垂れる。背に影の棘、糸を引く)
骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r。人の 1.8 倍。下の二行は周が進むほど歪んだ姿。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hum import *  # noqa

S = 1.8
h = H(S)
BONES = bones(S)
PAL = PALX({"void": mat([(14, 18, 28), (30, 40, 58), (52, 66, 88), (84, 100, 124)], 0.55, crack=0.0, noise=0.14)}, glow=("teal",))


def drips(F, bone, pts, tag, ln=(6.0, 10.0)):
    """糸 (細い管。先に青緑の雫)。pts = 根元の座標 (絶対座標)。"""
    for i, p in enumerate(pts):
        L = ln[0] + (ln[1] - ln[0]) * ((i * 37) % 10) / 10
        F += part(f"{tag}{i}", tube(bone, "slate", [p, (p[0], p[1] - L * 0.5, p[2]), (p[0], p[1] - L, p[2])], ra=[0.5, 0.35, 0.25], sides=4, caps=(True, True)))
        F += part(f"{tag}d{i}", ball(bone, "teal", (p[0], p[1] - L - 0.3, p[2]), 0.7, segs=5, rings=1))


def build_a(k):
    """A 濡れた影の巨人: 虚の黒の人影。腕は膝を越えて長く、指は細い。頭は滑らかな卵形で顔がない (縦に青緑の筋が一本)。"""
    F = []
    h.torso(F, "void", waist=2.2, chest=3.4, depth=2.0, hunch=0.5)
    h.arm(F, -1, "void", "slate", ln=2.2, r=1.2, out=0.3)
    h.arm(F, 1, "void", "slate", ln=2.2, r=1.2, out=0.3)
    h.leg(F, -1, "void", "slate")
    h.leg(F, 1, "void", "slate")
    F += part("skull", ellipsoid("head", "void", h.P(0, 26.2, -0.6), (2.0 * S, 2.8 * S, 2.1 * S), segs=9, rings=4))
    F += part("jaw", ellipsoid("jaw", "slate", h.P(0, 23.6, -1.2), (1.2 * S, 0.8 * S, 1.2 * S), segs=7, rings=2))
    F += part("streak", ellipsoid("head", "teal", h.P(0, 26.2, -2.55), (0.18 * S, 1.8 * S, 0.3 * S), segs=5, rings=2))
    for sd, nm in ((-1, "l"), (1, "r")):
        for j in range(3):
            F += part(f"finger{nm}{j}", cone(f"arm_{nm}", "slate", h.P(sd * 5.2 + (j - 1) * 0.6, 5.6, -0.4), (0, -1, 0.1), 3.4 * S, 0.3 * S, sides=4, rings=2))
    drips(F, "jaw", [h.P(x, 23.0, -1.9) for x in (-0.7, 0.0, 0.7)], "jd", ln=(3.0, 6.0))
    return F, []


def build_b(k):
    """B 三重の影: 首が伸び、顔のない頭が三つ重なる (下ほど大きい)。腕は三対 (肩・脇・腰) で、前の私の重なり。"""
    F = []
    h.torso(F, "void", waist=2.4, chest=3.8, depth=2.2)
    h.leg(F, -1, "void", "slate")
    h.leg(F, 1, "void", "slate")
    h.arm(F, -1, "void", "slate", ln=1.4, r=1.3)
    h.arm(F, 1, "void", "slate", ln=1.4, r=1.3)
    F += part("neck", tube("head", "slate", [h.P(0, 22.2, 0), h.P(0, 32.0, 0)], ra=[1.3 * S, 0.9 * S], sides=6, caps=(True, True)))
    for i, (y, r) in enumerate(((24.4, 2.4), (28.4, 2.0), (32.4, 1.6))):
        F += part(f"head{i}", ellipsoid("head", "void", h.P(0, y, -0.2), (r * S * 0.9, r * S * 1.05, r * S), segs=8, rings=3))
        F += part(f"slit{i}", ellipsoid("head", "teal", h.P(0, y, -(r * 0.98)), (0.15 * S, r * 0.55 * S, 0.2 * S), segs=5, rings=2))
    for i, (yy, zz) in enumerate(((16.0, -0.8), (13.0, -1.0))):
        for sd, b in ((-1, "arm_l"), (1, "arm_r")):
            p0, p1, p2 = h.P(sd * 3.4, yy, zz), h.P(sd * 6.6, yy - 2.0, zz - 2.4), h.P(sd * 6.8, yy - 7.0, zz - 3.2)
            F += part(f"arm{i}{b}", tube(b, "void", [p0, p1, p2], ra=[1.1 * S, 0.95 * S, 0.7 * S], sides=6, caps=(True, True)))
            F += part(f"hand{i}{b}", cone(b, "slate", p2, (0, -1, -0.2), 3.0 * S, 0.8 * S, sides=4, rings=2))
    drips(F, "body", [h.P(x, 11.0, -2.0) for x in (-1.6, 0.0, 1.6)], "bd", ln=(5.0, 9.0))
    return F, []


def build_c(k):
    """C 這いずる影: 四つ足で地を這う。背は丸く、頭を低く垂らす。背に影の棘が並び、糸を引く。"""
    F = []
    F += part("trunk", ellipsoid("body", "void", (0, 27.0, 0), (8.5, 8.0, 17.0), segs=9, rings=4))
    for i, z in enumerate((-9, -4, 1, 6, 11)):
        F += part(f"ridge{i}", cone("body", "slate", (0, 32.0, z), (0, 1, 0.3), 8.0 - abs(z - 1) * 0.3, 1.4, sides=4, rings=2))
    for sd, b, zz in ((-1, "arm_l", -11.0), (1, "arm_r", -11.0)):
        p0, p1, p2 = (sd * 6.5, 30.0, zz), (sd * 15.0, 22.0, zz - 4.0), (sd * 12.0, 1.0, zz - 9.0)
        F += part(f"foreleg{b}", tube(b, "void", [p0, p1, p2], ra=[3.4, 2.6, 1.8], sides=6, caps=(True, True)))
        for j in range(3):
            F += part(f"claw{b}{j}", cone(b, "slate", (p2[0] + (j - 1) * 1.6, 1.4, p2[2] - 0.6), (0, -0.1, -1), 5.0, 0.6, sides=4, rings=2))
    for sd, b in ((-1, "leg_l"), (1, "leg_r")):
        p0, p1, p2 = (sd * 6.5, 28.0, 10.0), (sd * 13.0, 18.0, 17.0), (sd * 10.0, 1.0, 12.0)
        F += part(f"hindleg{b}", tube(b, "void", [p0, p1, p2], ra=[3.4, 2.6, 1.6], sides=6, caps=(True, True)))
        F += part(f"hindfoot{b}", ellipsoid(b, "slate", (p2[0], 1.0, p2[2] - 1.6), (2.2, 1.0, 3.2), segs=6, rings=2))
    F += part("neck", tube("head", "void", [(0, 29.0, -12.0), (0, 21.0, -22.0), (0, 14.0, -28.0)], ra=[4.6, 3.4, 3.0], sides=7, caps=(True, True)))
    F += part("skull", ellipsoid("head", "void", (0, 12.0, -30.0), (3.4, 4.6, 3.8), segs=8, rings=3))
    F += part("streak", ellipsoid("head", "teal", (0, 11.4, -33.4), (0.3, 3.0, 0.4), segs=5, rings=2))
    F += part("jaw", ellipsoid("jaw", "slate", (0, 7.4, -30.6), (2.2, 1.0, 2.4), segs=6, rings=2))
    drips(F, "jaw", [(x, 7.0, -31.0) for x in (-1.0, 0.0, 1.0)], "jd", ln=(3.0, 6.0))
    drips(F, "body", [(x, 19.4, z) for x, z in ((-5, 4), (5, 8), (0, -2))], "bd", ln=(9.0, 16.0))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}}

SPEC = dict(
    id="king_shadow", short="歪んだ王 (影)", kind="歪んだ王 3案",
    title="歪んだ王「影」— 前の私の影の崩れを大きく",
    sub=["25 階・50 階…の王。突進・手下 (名のない形)・薙ぎ払い・雷の矢。顔は作らない (似せない)。周が進むほど歪む。",
         "青緑は糸の雫・顔の筋。下の二行は周が進んだ姿の見本。左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 濡れた影の巨人", "B 三重の影", "C 這いずる影"],
    poses=POSES,
    options={
        "a": dict(name="A 濡れた影の巨人", desc="膝を越える長い腕の黒い人影。顎と指から糸が垂れる。一番「前の私の影」のまま。"),
        "b": dict(name="B 三重の影", desc="顔のない頭が三つ重なり、腕は三対。前の私が重なったように見える。"),
        "c": dict(name="C 這いずる影", desc="四つ足で低く這い、頭を垂らす。背の棘と糸。突進の形が最も読みやすい。"),
    },
    extra="影の言葉 (虚の黒・石板の青灰・スカルクの青緑)。", budget="王 900〜1600", pw=380, king_h=52.0,
)
ROWS = king_rows((0, 29, 0), 5.2)

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(90, 190, 176), (200, 236, 228)])
