"""歪んだ王 (灰の型) ラフ 3 案。体は「燠の番人」(灰の僧) の崩れの姿を大きくしたもの。技: 叩きつけ・熾の雷・手下 (煤の子)・突進。
A 灰の僧の巨像 (高い頭巾と地を引く長い衣。鎖に巨大な熾の灯し。衣の裂け目に熾) / B 燃え殻の司祭 (腰から下が灰の山に崩れ、四本の腕が立つ。体じゅうに熾のひび)
C 灯火の冠の僧 (頭のまわりに熾の灯しが輪になって吊られ、両腕を広げる。衣は細く長い)
骨: root > body > head > jaw / arm_l / arm_r / leg_l / leg_r。人の 1.8 倍。下の二行は周が進むほど歪んだ姿。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hum import *  # noqa

S = 1.8
h = H(S)
BONES = bones(S)
PAL = PALX({"robe": mat(ASHY, 0.5, crack=0.0, noise=0.2), "robed": mat(SOOT, 0.5, crack=0.0, noise=0.14)}, glow=("ember",))


def robe(F, lo=0.4, wide=5.2, m="robe", hem="robed"):
    rings = [h.ring(lo, wide, wide * 0.78), h.ring(6, wide * 0.8, wide * 0.66), h.ring(12, 3.7, 2.7), h.ring(17, 4.3, 2.7), h.ring(21.5, 3.6, 2.3), h.ring(23.2, 1.9, 1.7)]
    F += part("robe", loft("body", m, rings, caps=(m, m)))
    F += part("hem", loft("body", hem, [h.ring(lo - 0.2, wide + 0.1, wide * 0.78 + 0.1), h.ring(lo + 1.2, wide * 0.97, wide * 0.76)], caps=(hem, hem)))


def hood(F, peak=31.0, m="robe"):
    rings = [h.ring(21.8, 3.0, 3.0, 0.2), h.ring(24.6, 3.2, 3.2, 0.3), h.ring(27.8, 2.4, 2.4, 0.4), h.ring(peak, 0.5, 0.5, 0.6)]
    F += part("hood", loft("head", m, rings, caps=(m, m)))
    F += part("face", ellipsoid("head", "hollow", h.P(0, 24.8, -2.5), (1.7 * S, 2.1 * S, 0.9 * S), segs=7, rings=3))
    return [eye_at(sx * 0.75 * S, 25.2 * S, -3.35 * S, 0.34 * S) for sx in (-1, 1)]


def lantern(F, bone, x, y, z, s=1.0, tag="lan"):
    """熾の灯し (鎖 + 笠 + 炎の玉)。y は玉の中心。"""
    F += part(f"{tag}_cap", ellipsoid(bone, "basalt", h.P(x, y + 1.7 * s, z), (1.5 * S * s, 0.7 * S * s, 1.5 * S * s), segs=6, rings=1))
    F += part(f"{tag}_fire", ellipsoid(bone, "ember", h.P(x, y, z), (1.2 * S * s, 1.5 * S * s, 1.2 * S * s), segs=7, rings=2))
    F += part(f"{tag}_base", ellipsoid(bone, "basalt", h.P(x, y - 1.7 * s, z), (1.0 * S * s, 0.5 * S * s, 1.0 * S * s), segs=6, rings=1))


def build_a(k):
    """A 灰の僧の巨像: 長い衣が地を引き、高い頭巾。右手の鎖に巨大な熾の灯し。衣の裂け目から熾。"""
    F = []
    robe(F)
    for sd, nm in ((-1, "l"), (1, "r")):
        F += part(f"foot_{nm}", ellipsoid(f"leg_{nm}", "basalt", h.P(sd * 2.2, 0.9, -4.4), (1.2 * S, 0.8 * S, 2.0 * S), segs=7, rings=2))
    h.arm(F, -1, "robe", "basalt", ln=1.1, r=1.6, fwd=0.6)
    h.arm(F, 1, "robe", "basalt", ln=1.1, r=1.6, fwd=0.6)
    eyes = hood(F, peak=32.0)
    F += part("tear", ellipsoid("body", "ember", h.P(1.0, 11.0, -2.8), (0.7 * S, 3.4 * S, 0.4 * S), segs=5, rings=2))
    F += part("tear2", ellipsoid("body", "ember", h.P(-1.6, 6.0, -3.3), (0.5 * S, 2.2 * S, 0.4 * S), segs=5, rings=2))
    F += part("chain", tube("arm_r", "basalt", [h.P(5.4, 11.2, -1.2), h.P(5.6, 5.0, -3.4)], ra=0.28 * S, sides=4, caps=(True, True)))
    lantern(F, "arm_r", 5.6, 3.0, -3.4, s=1.5, tag="big")
    return F, eyes


def build_b(k):
    """B 燃え殻の司祭: 腰から下は崩れた灰の山。肩から四本の腕が立ち、体にも山にも熾のひび。吊った香炉を一つ下げる。"""
    F = []
    F += part("mound", loft("body", "robed", [h.ring(0.2, 7.2, 6.0, 0.2), h.ring(3.0, 6.0, 5.0, 0.2), h.ring(7.0, 4.2, 3.4), h.ring(11.0, 3.4, 2.4)], caps=("robed", "robed")))
    h.torso(F, "robe", waist=2.6, chest=4.0, depth=2.4, hunch=0.5)
    F += part("belt_ash", loft("body", "robed", [h.ring(10.2, 3.4, 2.4), h.ring(12.0, 3.0, 2.0)], caps=("robed", "robed"))) if False else []
    for sd in (-1, 1):
        nm = "l" if sd < 0 else "r"
    h.arm(F, -1, "robe", "basalt", ln=1.2, r=1.6, out=0.8)
    h.arm(F, 1, "robe", "basalt", ln=1.2, r=1.6, out=0.8)
    for sd, b in ((-1, "arm_l"), (1, "arm_r")):
        p0, p1, p2 = h.P(sd * 3.6, 17.0, -1.0), h.P(sd * 7.4, 22.0, -2.6), h.P(sd * 8.6, 28.0, -4.0)
        F += part(f"up{b}", tube(b, "robe", [p0, p1, p2], ra=[1.3 * S, 1.1 * S, 0.8 * S], sides=6, caps=(True, True)))
        F += part(f"uphand{b}", ellipsoid(b, "ember", (p2[0], p2[1] + 1.2 * S, p2[2]), (1.1 * S, 1.3 * S, 1.0 * S), segs=6, rings=2))
    eyes = hood(F, peak=30.0)
    for i, (x, y, dz) in enumerate(((-2.2, 17.0, 0.0), (2.0, 14.0, 0.0), (0.0, 20.0, 0.0))):
        F += part(f"crackb{i}", ellipsoid("body", "ember", h.P(x, y, -2.2 - dz), (0.5 * S, 1.8 * S, 0.3 * S), segs=5, rings=2))
    for i, (x, z) in enumerate(((-5.0, -3.0), (4.8, -4.0), (0.0, -4.9))):
        F += part(f"mcrack{i}", ellipsoid("body", "ember", h.P(x, 2.2, z), (1.2 * S, 0.5 * S, 0.7 * S), segs=5, rings=2))
    return F, eyes


def build_c(k):
    """C 灯火の冠の僧: 細く長い衣。頭のまわりに六つの熾の灯しが輪になって吊られ、両腕は鎖を引いて広がる。"""
    F = []
    robe(F, wide=4.2)
    for sd, nm in ((-1, "l"), (1, "r")):
        F += part(f"foot_{nm}", ellipsoid(f"leg_{nm}", "basalt", h.P(sd * 2.0, 0.9, -3.6), (1.1 * S, 0.8 * S, 1.8 * S), segs=7, rings=2))
    h.arm(F, -1, "robe", "basalt", ln=1.0, r=1.5, out=1.8, fwd=0.4)
    h.arm(F, 1, "robe", "basalt", ln=1.0, r=1.5, out=1.8, fwd=0.4)
    eyes = hood(F, peak=29.0)
    for i in range(6):
        a = 2 * math.pi * i / 6
        dx, dz = math.sin(a), -math.cos(a)
        # 頭巾の肩から鎖が斜めに上がり、灯しは頭の高さの輪の上に吊られる
        F += part(f"cchain{i}", tube("head", "basalt", [h.P(dx * 2.4, 25.0, dz * 2.4), h.P(dx * 6.0, 29.0, dz * 6.0)], ra=0.25 * S, sides=4, caps=(True, True)))
        lantern(F, "head", dx * 6.0, 27.4, dz * 6.0, s=0.8, tag=f"cl{i}")
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}}

SPEC = dict(
    id="king_ash", short="歪んだ王 (灰)", kind="歪んだ王 3案",
    title="歪んだ王「灰」— 燠の番人 (灰の僧) の崩れを大きく",
    sub=["20 階・45 階…の王。叩きつけ・熾の雷・手下 (煤の子)・突進。周が進むほど大きく歪む。",
         "橙に光る所が熾。下の二行は周が進んだ姿の見本。左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 灰の僧の巨像", "B 燃え殻の司祭", "C 灯火の冠の僧"],
    poses=POSES,
    options={
        "a": dict(name="A 灰の僧の巨像", desc="高い頭巾と地を引く長い衣。鎖に巨大な熾の灯し。一番「灰の僧」のまま。"),
        "b": dict(name="B 燃え殻の司祭", desc="腰から下が灰の山に崩れ、四本の腕が立つ。体じゅうの熾のひびが崩れの印。"),
        "c": dict(name="C 灯火の冠の僧", desc="頭のまわりに熾の灯しが六つ輪になる。灯しを落とす熾の雷の形が読める。"),
    },
    extra="灰の言葉 (黒石・玄武・灰・熾の橙)。", budget="王 900〜1600", pw=380, king_h=52.0,
)
ROWS = king_rows((0, 29, 0), 5.2)

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(255, 150, 60), (255, 220, 150)])
