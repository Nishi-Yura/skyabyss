"""歪んだ王 (骨の型) ラフ 3 案。体は「肋の番」の崩れの姿を大きくしたもの。技: 叩きつけ・手下 (骨拾い)・突進・薙ぎ払い。
A 肋の檻の王 (檻の半分が剥がれて肋が外へ開く。盾は割れた骨盤) / B 骨の積み上げの王 (片腕が太い大腿骨の束。背に骨拾いの寄せ集めの瘤)
C 四つ腕の王 (肋の下から二本目の腕が生え、顎が左右に裂ける)
骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r。人の 1.8 倍。下の二行は周が進むほど歪んだ姿 (高く伸び、傾き、うねる)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hum import *  # noqa

S = 1.8
h = H(S)
BONES = bones(S)
PAL = PALX(glow=("red",))


def rib_side(F, side, y, rx=4.1, rz=3.2, r=0.55, tag=""):
    """片側の肋 (前から後ろへ回る半円の管。背骨側は胴に沈める)。"""
    pts = []
    for k in range(7):
        a = math.radians(-20 + 200 * k / 6)  # 前の少し内側から後ろの背骨まで
        pts.append(h.P(side * rx * math.sin(a), y, -rz * math.cos(a) - 0.2))
    pts = [np.array(p) for p in pts]
    return part(f"rib{tag}{side}_{y}", tube("body", "calc", pts, ra=r * S, sides=4, hint=(0, 1, 0), caps=(True, True)))


def build_a(k):
    """A 肋の檻の王: 左は肋が四本そろい、右は檻が剥がれて肋が外へ反る。右胸は洞が見える。盾は割れた骨盤。"""
    F = []
    eyes = h.base(F, "bone", ln=1.15, chest=3.9, hs=1.1)
    F += part("chest_hollow", ellipsoid("body", "hollow", h.P(2.1, 17.4, -2.0), (1.7 * S, 2.8 * S, 0.9 * S), segs=7, rings=3))
    for y in (14.6, 16.6, 18.6, 20.4):
        F += rib_side(F, -1, y)
    for i, y in enumerate((14.6, 16.6, 18.6, 20.4)):
        pts = [np.array(h.P(4.0, y, -0.4)), np.array(h.P(6.4, y + 0.6 * i, -1.6)), np.array(h.P(8.2, y + 1.0 + 0.8 * i, -4.6 - i * 0.6))]
        F += part(f"ribout{i}", tube("body", "calc", pts, ra=[0.62 * S, 0.5 * S, 0.12 * S], sides=4, hint=(0, 1, 0), caps=(True, True)))
    F += part("spine", tube("body", "calc", [h.P(0, 11.6, 1.8), h.P(0, 22.0, 1.8)], ra=0.6 * S, sides=5, caps=(True, True)))
    F += part("shield", ellipsoid("arm_l", "calc", h.P(-6.6, 12.4, -1.2), (3.7 * S, 4.1 * S, 0.9 * S), segs=10, rings=4))
    F += part("shield_rim", tube("arm_l", "bone", [h.P(-10.0, 16.0, -0.6), h.P(-6.6, 7.6, -2.0), h.P(-3.2, 16.0, -0.6)], ra=0.5 * S, sides=5, hint=(0, 0, 1), caps=(True, True)))
    F += part("blade", cone("arm_r", "calc", h.P(5.6, 10.0, -0.4), (0, -1, -0.15), 9.0 * S, 0.9 * S, sides=4, rings=3))
    for i, x in enumerate((-1.6, 0.0, 1.6)):
        F += part(f"crown{i}", cone("head", "calc", h.P(x * 1.1, 26.4, 0.2), (x * 0.12, 1, 0.1), (3.2 + (1.6 if x == 0 else 0)) * S, 0.6 * S, sides=4, rings=2))
    return F, eyes


def build_b(k):
    """B 骨の積み上げの王: 左腕は太い大腿骨の束 (三本が地へ下りる)。背に骨拾いの寄せ集めの瘤。背骨の棘が並ぶ。"""
    F = []
    h.torso(F, "bone", chest=4.2, depth=2.6, hunch=0.4)
    h.arm(F, 1, "bone", "calc", ln=1.2)
    h.leg(F, -1, "bone", "calc")
    h.leg(F, 1, "bone", "calc")
    eyes = h.head(F, "bone", "calc", s=1.05, dz=-0.6)
    F += part("shoulder_l", ellipsoid("arm_l", "bone", h.P(-5.4, 21.0, 0), (2.4 * S, 2.0 * S, 2.2 * S), segs=7, rings=3))
    for i, (dx, dz) in enumerate(((0, 0), (1.6, 0.9), (-1.5, 0.8))):
        F += part(f"femur{i}", tube("arm_l", "calc", [h.P(-5.4 + dx * 0.4, 21.0, dz * 0.3), h.P(-6.4 + dx, 12.0, dz - 0.4), h.P(-6.6 + dx, 2.2, dz - 1.4)], ra=[1.3 * S, 1.0 * S, 1.0 * S], sides=6, caps=(True, True)))
        F += part(f"knob{i}", ellipsoid("arm_l", "bone", h.P(-6.6 + dx, 1.6, dz - 1.6), (1.3 * S, 1.0 * S, 1.2 * S), segs=6, rings=2))
    for i, (x, y, z) in enumerate(((-2.2, 19.0, 2.6), (2.2, 20.0, 2.8), (0.0, 22.0, 2.2), (-1.6, 16.0, 2.2), (1.8, 16.6, 2.2), (0.0, 18.0, 3.4))):
        F += part(f"lump{i}", ellipsoid("body", "bone" if i % 2 else "calc", h.P(x, y, z), (1.6 * S, 1.5 * S, 1.5 * S), segs=6, rings=2))
    for i, y in enumerate((14.0, 16.0, 18.0, 20.0, 22.0)):
        F += part(f"back_spike{i}", cone("body", "calc", h.P(0, y, 3.4), (0, 0.15, 1), (2.6 + i * 0.3) * S, 0.5 * S, sides=4, rings=2))
    F += part("skull_trophy", ellipsoid("body", "bone", h.P(3.6, 22.4, -0.4), (1.3 * S, 1.4 * S, 1.3 * S), segs=7, rings=2))
    F += part("sock_t", ellipsoid("body", "hollow", h.P(3.6, 22.6, -1.5), (0.8 * S, 0.7 * S, 0.4 * S), segs=5, rings=1))
    return F, eyes


def build_c(k):
    """C 四つ腕の王: 肋の下から二本目の腕が生える。顎が左右に裂け、裂けた顎に骨の牙。胸の肋は細い。"""
    F = []
    eyes = h.base(F, "bone", ln=1.1, waist=2.2, chest=3.7)
    for y in (15.0, 17.0, 19.0):
        for sd in (-1, 1):
            F += rib_side(F, sd, y, tag="c")
    F += part("spine", tube("body", "calc", [h.P(0, 11.6, 1.8), h.P(0, 22.0, 1.8)], ra=0.6 * S, sides=5, caps=(True, True)))
    for sd, b in ((-1, "arm_l"), (1, "arm_r")):
        p0 = h.P(sd * 3.5, 14.6, -0.6)
        p1 = h.P(sd * 6.6, 11.4, -2.6)
        p2 = h.P(sd * 6.2, 7.0, -6.4)
        F += part(f"arm2{b}", tube(b, "calc", [p0, p1, p2], ra=[1.2 * S, 1.0 * S, 0.8 * S], sides=6, caps=(True, True)))
        F += part(f"claw2{b}", cone(b, "calc", p2, (0, -0.4, -1), 5.5 * S, 0.9 * S, sides=4, rings=3))
        F += part(f"claw2b{b}", cone(b, "calc", (p2[0] + sd * 0.8 * S, p2[1], p2[2]), (sd * 0.4, -0.4, -1), 4.6 * S, 0.7 * S, sides=4, rings=3))
    for sd in (-1, 1):
        F += part(f"jawfang{sd}", cone("jaw", "calc", h.P(sd * 1.5, 22.5, -1.5), (sd * 0.9, -0.4, -0.6), 3.6 * S, 0.6 * S, sides=4, rings=2))
    for i, x in enumerate((-1.4, -0.5, 0.5, 1.4)):
        F += part(f"horn{i}", cone("head", "calc", h.P(x * 1.1, 26.6, 0.0), (x * 0.45, 1, -0.2), (2.4 + abs(x)) * S, 0.5 * S, sides=4, rings=2))
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}}

SPEC = dict(
    id="king_bone", short="歪んだ王 (骨)", kind="歪んだ王 3案",
    title="歪んだ王「骨」— 肋の番の崩れを大きく",
    sub=["5 階・30 階…の王。叩きつけ・手下 (骨拾い)・突進・薙ぎ払い。周が進むほど大きく (1.6〜2.4 倍) 歪む。",
         "下の二行は周が進んだ姿の見本 (高く伸び・傾き・うねる)。左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 肋の檻の王", "B 骨の積み上げの王", "C 四つ腕の王"],
    poses=POSES,
    options={
        "a": dict(name="A 肋の檻の王", desc="檻の右半分が剥がれ、肋が外へ反る。盾は割れた骨盤。一番「肋の番」のまま。"),
        "b": dict(name="B 骨の積み上げの王", desc="左腕が大腿骨の束。背に骨拾いの瘤。手下の骨を寄せ集めた体。"),
        "c": dict(name="C 四つ腕の王", desc="肋の下から二本目の腕。顎が裂ける。薙ぎ払いの手数が増えて見える。"),
    },
    extra="骨の言葉 (骨・方解石・灰・洞の黒)。", budget="王 900〜1600", pw=380, king_h=52.0,
)
ROWS = king_rows((0, 29, 0), 5.2)

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(200, 60, 60), (255, 200, 200)])
