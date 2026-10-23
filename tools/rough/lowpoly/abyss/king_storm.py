"""歪んだ王 (雷の型) ラフ 3 案。体は「避雷の像」の崩れの姿を大きくしたもの。広間の真ん中から動かない王。技: 雷の矢・手下 (帯電の羽虫)・薙ぎ払い・叩きつけ。
A 三段の角柱の王 (大きさの違う四角い柱を三段積み、最上段に顔。中段から避雷の銅の腕) / B 避雷針の林の王 (太い八角の柱の頭に銅の針が冠のように立ち、輪が宙に浮く)
C 割れた巨像の王 (縦に割れた柱が左右に離れ、間に雷の芯。銅の帯でつながる)
骨: root > body > head / arm_l / arm_r。歩かない (台座に根を下ろす)。高さは人の 2 倍 (52 単位)。下の二行は周が進むほど歪んだ姿。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hum import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8, 0)), "head": ("body", (0, 42, 0)),
         "arm_l": ("body", (-9, 34, 0)), "arm_r": ("body", (9, 34, 0))}
PAL = PALX({"tuffd": mat(TUFF, 0.45, crack=0.2, noise=0.26)}, glow=("bolt",))


def sq(y, hx, hz, cx=0.0, cz=0.0):
    return [np.array(p, float) for p in ((cx - hx, y, cz - hz), (cx + hx, y, cz - hz), (cx + hx, y, cz + hz), (cx - hx, y, cz + hz))]


def block(bone, m, y0, y1, hx, hz, taper=0.9, cx=0.0, cz=0.0):
    return loft(bone, m, [sq(y0, hx, hz, cx, cz), sq(y1, hx * taper, hz * taper, cx, cz)], caps=(m, m))


def rod_arm(F, s, bone, y, tag, m="oxcu", ln=24.0, up=0.9):
    p0 = (s * 9.0, y, 0.0)
    p1 = (s * (9.0 + ln * 0.5), y + ln * 0.2, -2.0)
    p2 = (s * (12.0 + ln * 0.6), y + ln * up, -3.0)
    F += part(f"rod{tag}", tube(bone, m, [p0, p1, p2], ra=[2.0, 1.4, 0.9], sides=5, caps=(True, True)))
    F += part(f"rodtip{tag}", ball(bone, "bolt", p2, 1.7, segs=6, rings=2))


def build_a(k):
    """A 三段の角柱の王: 台座 → 太い柱 → 中くらい → 細い柱 (最上段が顔)。中段の両側から銅の腕が斜めに立つ。割れ目に雷。"""
    F = []
    F += part("plinth", block("body", "tuffd", 0, 7, 17, 17, taper=0.86))
    F += part("b1", block("body", "tuff", 7, 24, 12.5, 11.0, taper=0.88))
    F += part("b2", block("body", "tuffd", 24, 38, 10.0, 9.0, taper=0.9))
    F += part("b3", block("head", "tuff", 38, 54, 7.0, 7.0, taper=0.92))
    F += part("cap", block("head", "oxcu", 54, 58, 7.4, 7.4, taper=0.5))
    for i, y in enumerate((15, 31)):
        w = 12.6 if y == 15 else 10.1
        F += part(f"band{i}", loft("body", "oxcu", [sq(y - 1.0, w, w * 0.9), sq(y + 1.0, w, w * 0.9)], caps=("oxcu", "oxcu")))
    F += part("crack", ellipsoid("body", "bolt", (3.0, 18.0, -10.2), (0.7, 5.0, 0.4), segs=5, rings=2))
    F += part("crack2", ellipsoid("body", "bolt", (-2.0, 31.0, -8.7), (0.6, 3.6, 0.4), segs=5, rings=2))
    F += part("brow", loft("head", "tuffd", [sq(48.5, 7.4, 7.4), sq(50.0, 7.8, 7.8)], caps=("tuffd", "tuffd")))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        rod_arm(F, s, b, 33.0, b)
    return F, [eye_at(-3.0, 46.0, -7.2, 1.3), eye_at(3.0, 46.0, -7.2, 1.3)]


def build_b(k):
    """B 避雷針の林の王: 太い八角の柱。頭に銅の針の冠 (十二本)。宙に浮く銅の輪が針の先でつながる。"""
    F = []
    F += part("base", loft("body", "tuffd", [ring(0, 18, -14, 14, n=8), ring(8, 15, -12, 12, n=8)], caps=("tuffd", "tuffd")))
    F += part("shaft", loft("body", "tuff", [ring(8, 13, -11, 11, n=8), ring(24, 11, -9.5, 9.5, n=8), ring(40, 9.5, -8.5, 8.5, n=8)], caps=("tuff", "tuff")))
    F += part("shaft_band", loft("body", "oxcu", [ring(22, 11.4, -9.9, 9.9, n=8), ring(25, 11.2, -9.7, 9.7, n=8)], caps=("oxcu", "oxcu")))
    F += part("head", ellipsoid("head", "tuff", (0, 46, 0), (9.0, 8.0, 8.5), segs=8, rings=3))
    for i in range(12):
        a = 2 * math.pi * i / 12
        dx, dz = math.sin(a), -math.cos(a)
        F += part(f"needle{i}", cone("head", "oxcu", (dx * 6.0, 50.0, dz * 5.8), (dx * 0.28, 1, dz * 0.28), 14.0 + (i % 2) * 5.0, 1.0, sides=4, rings=3))
    for i in range(6):
        a = 2 * math.pi * i / 6 + 0.2
        dx, dz = math.sin(a), -math.cos(a)
        F += part(f"tipb{i}", ball("head", "bolt", (dx * 14.6, 50 + 14.0 + 4 + 0.0, dz * 14.6 + 0.0), 1.2, segs=5, rings=1)) if False else []
    F += part("halo", loop("head", "oxcu", 0, 68.0, 0, 11.3, 11.3, r=1.0, n=12, gap=0.1, sides=4))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part("arm" + b, tube(b, "tuff", [(s * 9.5, 36, 0), (s * 14.0, 26, -3.0), (s * 14.5, 14, -6.0)], ra=[3.4, 3.0, 2.6], sides=6, caps=(True, True)))
        F += part("fist" + b, ellipsoid(b, "oxcu", (s * 14.5, 11.0, -6.4), (3.6, 3.4, 3.4), segs=7, rings=2))
        F += part("fistbolt" + b, ball(b, "bolt", (s * 14.5, 11.0, -9.6), 1.5, segs=5, rings=1))
    return F, [eye_at(-3.2, 47.0, -8.2, 1.4), eye_at(3.2, 47.0, -8.2, 1.4)]


def build_c(k):
    """C 割れた巨像の王: 柱が縦に二つに割れ、半分ずつが離れる。間に雷の芯の柱。銅の帯が二つの半分をつなぐ。"""
    F = []
    F += part("plinth", block("body", "tuffd", 0, 7, 17, 13, taper=0.86))
    for s, nm in ((-1, "l"), (1, "r")):
        cx = s * 7.0
        F += part(f"half_{nm}", loft("body", "tuff", [sq(7, 6.5, 12.0, cx), sq(26, 5.8, 11.0, cx * 1.15), sq(46, 5.2, 9.0, cx * 1.35)], caps=("tuff", "tuff")))
        F += part(f"halfcap_{nm}", block("head", "oxcu", 46, 51, 5.6, 9.4, taper=0.5, cx=cx * 1.35))
    F += part("core", loft("body", "bolt", [sq(7, 1.6, 4.0), sq(48, 1.2, 3.0)], caps=("bolt", "bolt")))
    for i, y in enumerate((14, 26, 38)):
        w = 6.0 + (46 - y) * 0.02
        F += part(f"strap{i}", loft("body", "oxcu", [sq(y, 14.5 + (y - 14) * 0.1, 12.0 - (y - 14) * 0.05), sq(y + 2.2, 14.5 + (y - 14) * 0.1, 12.0 - (y - 14) * 0.05)], caps=("oxcu", "oxcu")))
    F += part("face", ellipsoid("head", "tuffd", (-8.5, 40.0, -9.0), (3.6, 4.0, 1.6), segs=6, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        rod_arm(F, s, b, 36.0, b, ln=20.0, up=0.7)
    return F, [eye_at(-10.0, 41.0, -10.4, 1.0), eye_at(-7.0, 41.0, -10.4, 1.0)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}}

SPEC = dict(
    id="king_storm", short="歪んだ王 (雷)", kind="歪んだ王 3案",
    title="歪んだ王「雷」— 避雷の像の崩れを大きく",
    sub=["15 階・40 階…の王。広間の真ん中から動かない。雷の矢・手下 (帯電の羽虫)・薙ぎ払い・叩きつけ。",
         "青白く光る所が雷。下の二行は周が進んだ姿の見本。左の目盛りは人 (1.8 ブロック)。"],
    labels=["A 三段の角柱の王", "B 避雷針の林の王", "C 割れた巨像の王"],
    poses=POSES,
    options={
        "a": dict(name="A 三段の角柱の王", desc="大きさの違う角柱を三段積み、最上段が顔。銅の腕が空へ立つ。一番「角柱の像」のまま。"),
        "b": dict(name="B 避雷針の林の王", desc="八角の太い柱の頭に十二本の銅の針の冠。宙に輪が浮き、雷を集める形が一目で分かる。"),
        "c": dict(name="C 割れた巨像の王", desc="縦に割れた柱の間に雷の芯が走る。割れ目から技の雷が出る形。"),
    },
    extra="雷の言葉 (凝灰岩・酸化した銅・白い雷)。", budget="王 600〜1400", pw=380, king_h=58.0,
)
ROWS = king_rows((0, 32, 0), 4.6)

if __name__ == "__main__":
    go(SPEC, build, PAL, BONES, ROWS, hint=192, pupil=[(140, 200, 255), (230, 245, 255)])
