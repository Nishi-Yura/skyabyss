"""歪んだ王 (塩の型) ラフ 3 案。体は「塩の抜け殻」の崩れの姿を大きくしたもの。技: 突進・叩きつけ・手下 (潮だまりの手)・雷の矢。
A 割れ目の王 (胸に縦の大きな割れ目。奥で青緑が光り、肩に結晶の房。左腕は折れている) / B 塩柱の王 (下半身が固まった塩の柱。腕が床まで届き、大きな平手)
C 結晶の冠の王 (頭に高い結晶の冠、背に結晶の列。胸の窪みに青緑の海。脚は長い)
骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r。人の 1.8 倍。下の二行は周が進むほど歪んだ姿。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hum import *  # noqa

S = 1.8
h = H(S)
BONES = bones(S)
PAL = PALX(glow=("teal",))


def crystals(F, bone, pts, tag, mat_="salt", ln=3.0, r=0.8):
    """結晶の房。pts = (x, y, z, 向き) の列 (人の単位)。"""
    for i, (x, y, z, d) in enumerate(pts):
        F += part(f"{tag}{i}", cone(bone, mat_, h.P(x, y, z), d, (ln + (i % 3) * 0.8) * S, r * S, sides=4, rings=2))


def build_a(k):
    """A 割れ目の王: 胸の縦の割れ目の奥に青緑。肩に結晶の房。左腕は折れて結晶の切り口。"""
    F = []
    h.torso(F, "salt", chest=4.0, depth=2.5)
    h.arm(F, 1, "salt", "saltc", ln=1.1)
    h.leg(F, -1, "salt", "saltc")
    h.leg(F, 1, "salt", "saltc")
    eyes = h.head(F, "salt", "saltc", s=1.1)
    F += part("crack", ellipsoid("body", "hollow", h.P(0.4, 17.4, -2.4), (0.9 * S, 4.6 * S, 0.8 * S), segs=6, rings=3))
    F += part("crack_glow", ellipsoid("body", "teal", h.P(0.4, 17.4, -2.9), (0.45 * S, 3.6 * S, 0.3 * S), segs=6, rings=2))
    for i, (dx, dy) in enumerate(((1.6, 3.0), (-1.4, 1.0), (2.0, -2.2))):
        F += part(f"cracklet{i}", tube("body", "saltc", [h.P(0.4 + dx * 0.4, 17.4 + dy * 0.9, -2.5), h.P(0.4 + dx, 17.4 + dy, -2.4)], ra=[0.3 * S, 0.12 * S], sides=4, caps=(True, True)))
    F += part("stump_u", tube("arm_l", "salt", [h.P(-4.6, 21.0, 0), h.P(-5.0, 16.8, 0.3)], ra=1.45 * S, sides=7, caps=(True, True)))
    F += part("stump_f", tube("arm_l", "saltc", [h.P(-5.0, 16.8, 0.3), h.P(-5.4, 14.0, 0.0)], ra=[1.25 * S, 1.05 * S], sides=7, caps=(True, True)))
    crystals(F, "arm_l", [(-5.4, 14.0, 0.0, (-0.1, -1, -0.2)), (-5.0, 14.4, 0.6, (0.3, -1, 0.3)), (-5.9, 14.2, -0.3, (-0.5, -1, -0.2))], "stump_c", ln=3.6, r=0.7)
    crystals(F, "body", [(-3.0, 20.4, 0.0, (-0.4, 1, 0.0)), (-3.4, 19.4, 0.8, (-0.8, 1, 0.3)), (3.0, 20.4, 0.0, (0.4, 1, 0.0)), (3.4, 19.4, 0.8, (0.8, 1, 0.3)), (0.0, 21.0, 1.2, (0, 1, 0.5))], "sh", ln=4.0, r=1.0)
    return F, eyes


def build_b(k):
    """B 塩柱の王: 腰から下は固まった塩の柱 (裾が広がる)。足は裾からのぞく。腕は床まで届き、平らな大きな手。"""
    F = []
    h.torso(F, "salt", waist=3.2, chest=4.2, depth=2.5, hunch=0.5)
    F += part("column", loft("body", "saltc", [h.ring(0.6, 5.6, 4.2), h.ring(4.0, 4.6, 3.6), h.ring(8.0, 4.0, 3.2), h.ring(11.2, 3.2, 2.2)], caps=("saltc", "saltc")))
    F += part("column_rim", loft("body", "salt", [h.ring(0.2, 5.8, 4.4), h.ring(1.6, 5.7, 4.3)], caps=("salt", "salt")))
    for sd, nm in ((-1, "l"), (1, "r")):
        F += part(f"foot_{nm}", ellipsoid(f"leg_{nm}", "saltc", h.P(sd * 2.6, 0.9, -4.6), (1.4 * S, 0.8 * S, 1.9 * S), segs=7, rings=3))
        F += part(f"shin_{nm}", tube(f"leg_{nm}", "salt", [h.P(sd * 2.6, 6.0, -2.6), h.P(sd * 2.6, 1.6, -4.4)], ra=1.3 * S, sides=6, caps=(True, True)))
        F += part(f"thigh_{nm}", tube(f"leg_{nm}", "salt", [h.P(sd * 2.2, 12.0, 0), h.P(sd * 2.6, 6.0, -2.6)], ra=1.7 * S, sides=6, caps=(True, True)))
    eyes = h.head(F, "salt", "saltc", s=1.0, dz=-0.5)
    for sd, b in ((-1, "arm_l"), (1, "arm_r")):
        h.arm(F, sd, "salt", "saltc", ln=1.8, out=0.6, r=1.7)
        F += part(f"palm{sd}", ellipsoid(b, "saltc", h.P(sd * 5.2, 3.6, -0.6), (2.6 * S, 0.7 * S, 2.4 * S), segs=8, rings=2))
        for j in range(4):
            F += part(f"finger{sd}{j}", tube(b, "salt", [h.P(sd * (3.6 + j * 0.9), 3.6, -1.6), h.P(sd * (3.6 + j * 0.9), 2.4, -4.6)], ra=[0.5 * S, 0.3 * S], sides=4, caps=(True, True)))
    crystals(F, "body", [(-3.0, 20.6, -0.2, (-0.3, 1, 0)), (3.0, 20.6, -0.2, (0.3, 1, 0)), (0, 20.6, 1.6, (0, 1, 0.6))], "sh", ln=3.2, r=1.1)
    return F, eyes


def build_c(k):
    """C 結晶の冠の王: 頭に高い結晶の冠。背に結晶の列。胸に窪みがあり、青緑の海が光る。脚は長く細い。"""
    F = []
    h.torso(F, "salt", waist=2.4, chest=3.8, depth=2.4)
    h.arm(F, -1, "salt", "saltc", ln=1.2)
    h.arm(F, 1, "salt", "saltc", ln=1.2)
    for sd, nm in ((-1, "l"), (1, "r")):
        x = sd * 2.0
        F += part(f"thigh_{nm}", tube(f"leg_{nm}", "salt", [h.P(x, 12.0, 0), h.P(x * 1.4, 6.6, 0.4)], ra=1.7 * S, sides=7, caps=(True, True)))
        F += part(f"shin_{nm}", tube(f"leg_{nm}", "salt", [h.P(x * 1.4, 6.6, 0.4), h.P(x * 1.1, 0.9, -0.4)], ra=[1.3 * S, 0.8 * S], sides=7, caps=(True, True)))
        F += part(f"foot_{nm}", ellipsoid(f"leg_{nm}", "saltc", h.P(x * 1.1, 0.7, -1.4), (1.1 * S, 0.7 * S, 2.2 * S), segs=7, rings=3))
    eyes = h.head(F, "salt", "saltc", s=1.05)
    F += part("pool", ellipsoid("body", "teal", h.P(0, 17.6, -2.3), (2.2 * S, 2.6 * S, 0.6 * S), segs=8, rings=3))
    F += part("pool_rim", hoop("body", "saltc", 0, 17.6 * S, -2.3 * S, 2.8 * S, 3.2 * S, r=0.5 * S, n=10, gap=20))
    crystals(F, "head", [(x * 1.0, 26.6, 0.0, (x * 0.28, 1, 0.0)) for x in (-1.6, -0.8, 0.0, 0.8, 1.6)], "crown", ln=3.8, r=0.6)
    F += part("crown_mid", cone("head", "salt", h.P(0, 26.8, 0.0), (0, 1, 0), 9.0 * S, 0.9 * S, sides=5, rings=3))
    for i, y in enumerate((14.0, 16.6, 19.2, 21.6)):
        F += part(f"spine{i}", cone("body", "salt", h.P(0, y - (0.8 if i == 3 else 0), 1.4), (0, 0.3, 1), (3.4 - i * 0.2) * S, 0.8 * S, sides=4, rings=2))
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}}

SPEC = dict(
    id="king_salt", short="歪んだ王 (塩)", kind="歪んだ王 3案",
    title="歪んだ王「塩」— 塩の抜け殻の崩れを大きく",
    sub=["10 階・35 階…の王。突進・叩きつけ・手下 (潮だまりの手)・雷の矢。周が進むほど大きく歪む。",
         "下の二行は周が進んだ姿の見本。青緑は割れ目の奥の海 (光る)。左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 割れ目の王", "B 塩柱の王", "C 結晶の冠の王"],
    poses=POSES,
    options={
        "a": dict(name="A 割れ目の王", desc="胸の縦の割れ目に青緑が光る。左腕は折れ、肩に結晶の房。一番「抜け殻」のまま。"),
        "b": dict(name="B 塩柱の王", desc="腰から下が固まった塩の柱。腕が床まで届き、平手で叩く。手下の手と並ぶ。"),
        "c": dict(name="C 結晶の冠の王", desc="高い結晶の冠と背の列。胸の窪みに青緑の海。細長く立つ王。"),
    },
    extra="塩の言葉 (白・塩の皮・海の緑)。", budget="王 900〜1600", pw=380, king_h=52.0,
)
ROWS = king_rows((0, 29, 0), 5.2)

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(200, 230, 240), (255, 255, 255)])
