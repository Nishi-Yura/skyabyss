"""潮だまり貝 (tidepool_clam) 低ポリ ラフ 3 案。待ち伏せ・像の予習。HP 20、挟む 5。
閉じた貝にしか見えない。人が近づくと 0.8 秒かけて殻を開き (予告)、挟んで足を止める。前の合わせ目に小真珠がのぞく (餌)。
A 縁飾りの帆立 (放射の筋・珊瑚の縁・外套に並ぶ青い目) / B 岩の牡蠣 (いびつで平たい・フジツボと塩の殻・昆布) / C 大シャコ (厚い波の襞・青緑の外套の唇)
骨: root > body (下の殻) > lid (上の殻。蝶番は後ろ) / pearl (下の殻の前の縁)。開く = lid を前が上がる向きへ回す。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck2 import *  # noqa

HINGE = (0, 3.5, 5.6)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 2.0, 0)), "lid": ("body", HINGE), "pearl": ("body", (0, 3.4, -6.9))}
PAL = {"mats": {
    "shell": mat(CORAL, 0.62, crack=0.08, noise=0.18, stain=dict(ramp=SALT, thr=0.94, up=0.4, conc=0.2)),
    "rib": mat(CORAL, 0.74, crack=0.0, noise=0.1),
    "nacre": mat(NACRE, 0.7, crack=0.0, noise=0.2, facevar=0.08),
    "flesh": mat([(70, 30, 44), (120, 56, 70), (170, 92, 98), (214, 136, 128)], 0.6, crack=0.0, noise=0.2),
    "rock": mat(TEAL, 0.5, crack=0.18, noise=0.2, stain=dict(ramp=SALT, thr=0.9, up=0.5, conc=0.3)),
    "barn": mat(SALT, 0.6, crack=0.0, dots=0.2, min=0),
    "salt": mat(SALT, 0.76, crack=0.0, noise=0.12),
    "kelp": mat(KELP, 0.55, crack=0.0, stripe=3, axis="v", min=0),
    "mantle": mat(TEAL, 0.62, crack=0.0, noise=0.2, dots=0.12),
    "eye": mat([(40, 120, 150), (90, 200, 220), (180, 248, 255)], 0.7, crack=0.0, min=1),
    "pearl": mat(PEARL, 0.72, crack=0.0, min=1, noise=0.08),
    "dark": mat(DEEP, 0.5, crack=0.0, min=0),
}, "skull_bone": "none", "face_mat": "none", "glow_mats": ["eye", "pearl"]}


def valves(F, lo_mat, lid_mat, w=7.2, f=7.8, b=5.6, n=10, lid_h=7.6, jag=0.0, seed=0):
    """下の殻 (body) と上の殻 (lid)。合わせ目は y 3.3 / 3.7 (0.4 の隙間)。内側の面は真珠層。jag = 縁のいびつさ。"""
    rnd = np.random.RandomState(seed)

    def jr(r):
        if jag:
            for p in r:
                p += V(rnd.uniform(-jag, jag), rnd.uniform(-jag * 0.4, jag * 0.4), rnd.uniform(-jag, jag))
        return r
    lo = [ring(0.4, w * 0.42, -f * 0.42, b * 0.42, n=n), jr(ring(1.9, w * 0.86, -f * 0.86, b * 0.86, n=n)), jr(ring(3.3, w, -f, b, n=n))]
    F += part("lower", loft("body", lo_mat, lo, caps=(lo_mat, "nacre")))
    up = [jr(ring(3.7, w, -f, b, n=n)), jr(ring(5.4, w * 0.86, -f * 0.84, b * 0.86, n=n)), ring(lid_h - 0.6, w * 0.55, -f * 0.5, b * 0.55, n=n),
          ring(lid_h, w * 0.2, -f * 0.16, b * 0.2, n=n)]
    F += part("upper", loft("lid", lid_mat, up, caps=("nacre", lid_mat)))
    # 蝶番の出っ張り (後ろ。両方の殻をつなぐ小さなこぶ)
    F += part("hinge", ellipsoid("body", lid_mat, (0, 3.5, b + 0.2), (2.0, 1.0, 0.9), segs=6, rings=1))


def pearl(F, z=-6.9, r=1.35):
    # 前の合わせ目に半分沈んだ小真珠 (閉じていても、合わせ目からのぞく)
    F += part("pearl", ellipsoid("pearl", "pearl", (0, 3.4, z), (r, r, r), segs=6, rings=2))
    # 外套の舌 (開くと見える肉の面。下の殻の内側の面に沈む)
    F += part("tongue", ellipsoid("body", "flesh", (0, 3.2, -0.8), (4.6, 0.32, 4.4), segs=8, rings=1))


def ribs(F, bone, mat_, count, w, f, b, y0, y1, r=0.45):
    """上の殻の放射の筋 (殻の表面に沈めた細い管)。"""
    for i in range(count):
        a = math.radians(-70 + 140 * i / (count - 1))
        s, c = math.sin(a), math.cos(a)
        p0 = V(s * w * 0.18, y1 - 0.2, -c * f * 0.12)
        p1 = V(s * w * 0.62, (y0 + y1) / 2 + 0.6, -c * f * 0.66)
        p2 = V(s * w * 0.96, y0 + 0.5, -c * f * 0.98)
        F += part(f"rib{i}", tube(bone, mat_, [p0, p1, p2], ra=[r * 0.6, r, r * 0.8], sides=3, hint=(0, 1, 0)))


def build_a(k):
    """A 縁飾りの帆立: 放射の筋の通った珊瑚色の扇の殻。外套の縁に青い目が並ぶ (開くと光る)。"""
    F = []
    valves(F, "shell", "shell", w=7.4, f=7.6, b=5.2)
    ribs(F, "lid", "rib", 7, 7.4, 7.6, 5.2, 3.7, 7.6)
    # 蝶番の両脇の耳 (帆立の三角の耳)
    for s in (-1, 1):
        F += part(f"ear{s}", spike("lid", "rib", (s * 2.2, 4.4, 5.0), (s * 1.0, 0.1, 0.5), 3.2, 1.1, sides=3, rings=1))
    pearl(F)
    # 外套の縁の目 (下の殻の前の縁に半分沈む小さな玉。開くと見える)
    for i in range(6):
        a = math.radians(-62 + 124 * i / 5)
        if abs(a) < 0.25:
            continue
        F += part(f"eye{i}", ellipsoid("body", "eye", (math.sin(a) * 6.3, 3.35, -math.cos(a) * 6.6), (0.5, 0.42, 0.5), segs=4, rings=1))
    return F, []


def build_b(k):
    """B 岩の牡蠣: いびつで平たい灰青の殻。上にフジツボと白い塩の殻、昆布の切れ端。岩に紛れる。"""
    F = []
    valves(F, "rock", "rock", w=8.0, f=8.6, b=5.4, lid_h=6.4, jag=0.7, seed=3, n=10)
    F += part("barn", lumps("lid", "barn", [((-3.0, 5.6, -1.0), 1.1), ((2.6, 5.4, 1.4), 1.0), ((0.4, 6.1, -3.6), 0.9), ((3.4, 4.9, -3.8), 0.8)], segs=5))
    F += part("crust", ellipsoid("lid", "salt", (-1.2, 6.1, 1.6), (2.6, 0.5, 2.2), segs=6, rings=1))
    F += part("kelp", tube("lid", "kelp", [(4.6, 4.6, 2.6), (6.6, 4.8, 4.4), (8.2, 3.6, 6.4), (9.0, 1.2, 7.6)], ra=[0.9, 0.8, 0.6, 0.3], rb=[0.3, 0.3, 0.25, 0.1], sides=4))
    # 下の殻の脇の珊瑚の小枝 (岩にしがみつく)
    F += part("twig", spike("body", "shell", (-6.8, 1.8, 1.2), (-0.8, 0.6, 0.3), 3.0, 0.7, sides=3, rings=1))
    pearl(F, z=-7.6)
    return F, []


def build_c(k):
    """C 大シャコ: 厚い波の襞の白い殻。合わせ目から青緑の外套の唇がはみ出し、青い点が光る。"""
    F = []
    valves(F, "salt", "salt", w=7.6, f=7.0, b=5.4, lid_h=7.2)
    # 襞: 殻の上を前から後ろへ走る太い畝 (上の殻に沈める)
    for i, x in enumerate((-4.6, -1.6, 1.6, 4.6)):
        F += part(f"fold{i}", tube("lid", "rib", [(x * 0.3, 7.0, 1.2), (x * 0.8, 5.8, -3.8), (x, 4.1, -6.4)], ra=[0.5, 1.1, 0.9], rb=[0.4, 0.8, 0.7], sides=4))
    # 外套の唇 (合わせ目に挟まり、縁から少しはみ出す)
    F += part("lip", ellipsoid("body", "mantle", (0, 3.5, -0.6), (7.6, 0.32, 7.2), segs=10, rings=1))
    for i in range(5):
        a = math.radians(-55 + 110 * i / 4)
        F += part(f"spot{i}", ellipsoid("body", "eye", (math.sin(a) * 7.1, 3.55, -0.6 - math.cos(a) * 6.7), (0.4, 0.3, 0.4), segs=4, rings=1))
    pearl(F, z=-6.4, r=1.3)
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "open": {"lid": (58, 0, 0)},
    "half": {"lid": (26, 0, 0)},
}

SPEC = dict(
    id="tidepool_clam", short="潮だまり貝", kind="地上の敵 3案 (P12)",
    title="潮だまり貝 (tidepool_clam) — 待ち伏せの二枚貝・低ポリ + ドット絵 3 案",
    sub=["二章の地上の敵。閉じた貝にしか見えない。近づくと 0.8 秒かけて殻を開き (予告)、挟んで足を止める (鈍足 II 1.5 秒)。HP 20、挟む 5。",
         "前の合わせ目に小真珠がのぞく (餌)。離れれば閉じる。閉じた殻は硬く、開いた中が弱い。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 縁飾りの帆立", "B 岩の牡蠣", "C 大シャコ"],
    rows=[("前 3/4 (閉じた姿。合わせ目の小真珠)", "idle", 212, 18, 300, (0, 4, 0), 16.0),
          ("横", "idle", 270, 8, 300, (0, 4, 0), 16.0),
          ("予告: 開きかけ (0.4 秒)", "half", 200, 22, 300, (0, 5, 0), 16.0),
          ("予告: 開ききった (0.8 秒。挟む直前)", "open", 196, 26, 340, (0, 6, 0), 15.0),
          ("開いた姿 (横)", "open", 270, 10, 340, (0, 6, 0), 15.0),
          ("後ろ 3/4 (蝶番)", "idle", 32, 20, 300, (0, 4, 0), 16.0)],
    options={
        "a": dict(name="A 縁飾りの帆立", desc="珊瑚色の扇の殻に放射の筋、蝶番の両脇に三角の耳。開くと外套の縁に青い目が並んで光る (本物の帆立の目)。閉じているとただのきれいな貝で、目が見えた瞬間に「これは生き物だ」と分かる。前の合わせ目に小真珠。"),
        "b": dict(name="B 岩の牡蠣", desc="いびつで平たい灰青の岩の殻。上にフジツボと白い塩の殻、昆布の切れ端。潮だまりの岩に一番紛れる (待ち伏せらしい)。開くと珊瑚色の舌と真珠層が見え、色の差で開いたと読める。"),
        "c": dict(name="C 大シャコ", desc="白い厚い殻に波の襞。合わせ目から青緑の外套の唇がはみ出し、青い点が光る。閉じていても生き物らしさがあり、塩の白と青緑で二章の言葉に一番近い。輪郭は丸く重い。")},
    extra="二章の言葉 (珊瑚・真珠層・青緑・白い塩)。骨 4 本 (root > body > lid / pearl)。開く・閉じるは lid の回転だけ。小真珠は下の殻の前の縁に沈めて置き、閉じていても半分のぞく。内側の面は真珠層の色で、閉じると隙間から真珠層の線が見える。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
