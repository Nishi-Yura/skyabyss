"""影抱き (shadow_embracer) 低ポリ ラフ 3 案。新月の谷の区画の主 (docs/design/46 の 4.3)。
月の岩と影でできた大きなもの。影に潜って移り (床の影が走る予告)、暗がりから腕で掴む・影を投げる・影の波。
段 2 から自分の分身を影に置く。星灯りの中に引き出すと体が固まり (岩の殻が閉じる)、斬れる。
どの案も「岩 (玄武岩・凝灰岩) の殻」と「影の腕」の二つでできていて、星の光の割れ目が弱点の印。
A 岩の抱擁 (うずくまった巨きな岩の殻。殻の前が割れて開き、中の暗がりから影の腕が何本も伸びる。殻の上に星の割れ目)
B 影の母 (背の高い頭巾の影。顔に月の岩の仮面、胸の前で四本の長い腕が黒い珠 = 抱いた影を抱える)
C 影の獣 (低く這う四つ足の影。背に月の岩の板が並び、頭は三日月の形の岩の顎。肩から影の腕が二本)
骨: root > body > head / arm_l / arm_r / arm_l2 / arm_r2 / leg_l / leg_r (+ C は leg_l2 / leg_r2)。高さ 4〜5 ブロック。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 26, 0)), "head": ("body", (0, 52, -6)),
         "arm_l": ("body", (-11, 46, -4)), "arm_r": ("body", (11, 46, -4)),
         "arm_l2": ("body", (-9, 36, -6)), "arm_r2": ("body", (9, 36, -6)),
         "leg_l": ("body", (-7, 22, 2)), "leg_r": ("body", (7, 22, 2)),
         "leg_l2": ("body", (-7, 18, 14)), "leg_r2": ("body", (7, 18, 14))}
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.2, noise=0.22)),
    "tuff": ([(40, 46, 60), (66, 74, 90), (96, 104, 120), (128, 136, 150)], 0.62, dict(crack=0.14, noise=0.2)),
    "shadow": (SHADOW, 0.5, dict(crack=0.04, noise=0.18)),
    "shade2": (INDIGO_F, 0.5, dict(crack=0.06, noise=0.2)),
    "hollow": (SHADOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dust": (DUST, 0.66, dict(crack=0.06, noise=0.3)),
    "star": (STAR, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["star"])


def shadow_arm(F, bone, sh, el, wr, m="shadow", r=(2.0, 1.5, 1.1), fingers=4, flen=4.5):
    """影の腕: 肩 → 肘 → 手首、手は平たい掌に長い指 (先が細る)"""
    F += part(bone + "u", tube(bone, m, [sh, el], ra=[r[0], r[1]], sides=7, hint=(0, 0, 1)))
    F += part(bone + "f", tube(bone, m, [el, wr], ra=[r[1], r[2]], sides=7, hint=(0, 0, 1)))
    d = [wr[i] - el[i] for i in range(3)]
    n = math.sqrt(sum(c * c for c in d)) or 1
    d = [c / n for c in d]
    palm = [wr[i] + d[i] * 1.4 for i in range(3)]
    F += part(bone + "p", ellipsoid(bone, m, palm, (r[2] * 1.8, r[2] * 1.2, r[2] * 1.8), segs=7, rings=3))
    for j in range(fingers):
        o = (j - (fingers - 1) / 2) * 0.9
        base = (palm[0] + o * (0.3 if abs(d[0]) < 0.5 else 0), palm[1] + o * (0.3 if abs(d[0]) >= 0.5 else 0), palm[2] + o * 0.3)
        F += part(f"{bone}fi{j}", spike(bone, m, base, (d[0] + o * 0.15, d[1] - 0.3, d[2]), flen, 0.45, sides=5, rings=3))


def cracks(F, bone, pts_list, r=0.35):
    for i, pts in enumerate(pts_list):
        F += part(f"{bone}cr{i}", tube(bone, "star", pts, ra=r, sides=4, hint=(0, 1, 0)))


def build_a(k):
    """A 岩の抱擁: うずくまった巨きな岩の塊 (背が丸く、肩に岩が乗る)。胸が裂けて暗がりが開き、そこから影の腕 4 本と顔が覗く。"""
    F = []
    F += part("mass", ellipsoid("body", "basalt", (0, 24.0, 5.0), (17.0, 18.0, 14.0), segs=12, rings=6))
    for i, (x, y, z, rx, ry, rz, m) in enumerate(((-9.0, 42.0, 4.0, 7.0, 6.0, 7.5, "tuff"), (9.5, 41.0, 5.0, 7.5, 6.5, 7.0, "tuff"),
                                                  (0, 46.0, 9.0, 8.0, 5.5, 7.0, "basalt"), (-16.0, 22.0, 9.0, 5.0, 8.0, 7.0, "tuff"),
                                                  (16.5, 18.0, 8.0, 5.0, 7.0, 7.0, "basalt"), (0, 30.0, 16.0, 8.0, 10.0, 4.5, "tuff"))):
        F += part(f"boulder{i}", ellipsoid("body", m, (x, y, z), (rx, ry, rz), segs=8, rings=4))
    # 胸の裂け目: 暗がりの穴と、縁の岩の唇
    F += part("rift", ellipsoid("body", "hollow", (0, 27.0, -7.0), (7.0, 13.0, 3.5), segs=10, rings=5))
    for s in (-1, 1):
        F += part(f"lip{s}", tube("body", "tuff", [(s * 2.0, 41.0, -6.5), (s * 7.5, 34.0, -8.0), (s * 8.0, 22.0, -8.2), (s * 3.0, 13.0, -6.5)],
                                  ra=[1.0, 1.8, 1.8, 1.0], sides=6, hint=(0, 0, 1)))
    F += part("head", ellipsoid("head", "shadow", (0, 35.0, -9.0), (3.6, 4.2, 3.0), segs=8, rings=4))
    for s, b, b2 in ((-1, "arm_l", "arm_l2"), (1, "arm_r", "arm_r2")):
        shadow_arm(F, b, (s * 8.0, 38.0, -9.0), (s * 18.0, 30.0, -17.0), (s * 15.0, 12.0, -23.0), r=(3.6, 3.0, 2.2), flen=7.5)
        shadow_arm(F, b2, (s * 7.0, 30.0, -10.0), (s * 12.0, 16.0, -17.0), (s * 7.0, 3.0, -20.0), r=(2.8, 2.3, 1.7), flen=6.0)
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"foot{s}", ellipsoid(b, "basalt", (s * 9.0, 3.5, 2.0), (6.0, 4.0, 8.0), segs=8, rings=3))
    cracks(F, "body", [[(-3.0, 52.0, 9.0), (1.0, 46.0, 15.5), (-1.5, 38.0, 19.6), (2.0, 28.0, 20.4)],
                       [(1.0, 46.0, 15.5), (7.0, 42.0, 11.8)], [(-1.5, 38.0, 19.6), (-7.0, 34.0, 15.8)]])
    return F, [eye_at(-1.5, 36.0, -11.95, 0.85), eye_at(1.5, 36.0, -11.95, 0.85)]


def build_b(k):
    """B 影の母: 背の高い頭巾の影 (裾は床に溶ける)。月の岩の仮面、胸の前で四本の腕が黒い珠 (抱いた影) を抱える。"""
    F = []
    rings = [ring(0.5, 15.0, -13.0, 13.0, n=12), ring(10.0, 11.0, -9.0, 9.5, n=12), ring(26.0, 9.0, -7.0, 7.5, n=12),
             ring(40.0, 9.5, -7.5, 7.0, n=12), ring(48.0, 7.5, -7.0, 5.5, n=12), ring(53.0, 4.0, -5.0, 3.5, n=12)]
    F += part("robe", loft("body", "shade2", rings, caps=("shadow", "shade2")))
    for i in range(8):
        a = i / 8 * 2 * math.pi
        F += part(f"pool{i}", ellipsoid("body", "shadow", (math.cos(a) * 14.0, 0.6, math.sin(a) * 12.5), (4.5, 0.9, 4.0), segs=6, rings=2))
    hood = [ring(51.0, 7.0, -8.0, 6.0, n=10), ring(57.0, 7.0, -8.0, 6.5, n=10), ring(63.0, 5.0, -5.0, 6.0, n=10), ring(67.0, 2.0, -1.0, 3.5, n=10)]
    F += part("hood", loft("head", "shade2", hood, caps=("shadow", "shade2")))
    F += part("mask", ellipsoid("head", "tuff", (0, 57.0, -8.2), (4.2, 5.4, 1.6), segs=9, rings=4))
    F += part("brow", tube("head", "basalt", [(-4.0, 59.4, -8.6), (0, 60.4, -9.4), (4.0, 59.4, -8.6)], ra=0.7, sides=5, hint=(0, 1, 0)))
    cracks(F, "head", [[(0, 61.6, -9.3), (0.6, 58.0, -9.7), (-0.4, 54.0, -9.5), (0.3, 52.6, -9.0)]], r=0.28)
    F += part("orb", ellipsoid("body", "hollow", (0, 32.0, -13.0), (6.0, 6.0, 6.0), segs=10, rings=5))
    F += part("orbglow", tube("body", "star", [(-4.0, 34.0, -18.2), (0, 37.0, -18.6), (4.0, 33.0, -18.2)], ra=0.3, sides=4, hint=(0, 1, 0)))
    for s, b, b2 in ((-1, "arm_l", "arm_l2"), (1, "arm_r", "arm_r2")):
        shadow_arm(F, b, (s * 8.0, 46.0, -4.0), (s * 13.0, 38.0, -10.0), (s * 6.0, 37.0, -18.0), r=(2.0, 1.6, 1.1), flen=5.0)
        shadow_arm(F, b2, (s * 7.5, 38.0, -6.0), (s * 12.0, 30.0, -12.0), (s * 5.0, 26.0, -17.0), r=(1.8, 1.4, 1.0), flen=4.5)
    return F, [eye_at(-1.6, 57.8, -9.75, 0.75), eye_at(1.6, 57.8, -9.75, 0.75)]


def build_c(k):
    """C 影の獣: 低く這う四つ足の影。背に月の岩の板が並び、頭は三日月の形の岩の顎。肩から影の腕が二本伸びる。"""
    F = []
    F += part("torso", ellipsoid("body", "shadow", (0, 24.0, 6.0), (11.0, 9.0, 18.0), segs=11, rings=5))
    F += part("chest", ellipsoid("body", "shadow", (0, 26.0, -8.0), (9.0, 9.5, 8.0), segs=10, rings=4))
    for j in range(5):
        z = -3.0 + j * 5.5
        F += part(f"plate{j}", spike("body", "basalt", (0, 31.0 - abs(j - 2) * 0.8, z), (0, 1, 0.35), 9.0 - abs(j - 2) * 1.6, 4.0, sides=5, rings=2))
    for s in (-1, 1):
        for j in range(3):
            F += part(f"side{s}{j}", ellipsoid("body", "basalt", (s * 9.5, 27.0, j * 7.0), (2.5, 4.0, 4.0), segs=6, rings=2))
    # 三日月の顎: 上下二枚の弧の岩
    F += part("neck", tube("head", "shadow", [(0, 27.0, -12.0), (0, 30.0, -18.0)], ra=[6.0, 4.5], sides=8, hint=(1, 0, 0)))
    F += part("jaw_up", tube("head", "basalt", [(0, 36.0, -16.0), (0, 37.0, -22.0), (0, 33.0, -28.0), (0, 28.0, -29.0)], ra=[4.5, 3.8, 2.5, 0.6], sides=7, hint=(1, 0, 0)))
    F += part("jaw_lo", tube("head", "basalt", [(0, 25.0, -17.0), (0, 23.0, -23.0), (0, 25.5, -28.0), (0, 28.5, -28.6)], ra=[3.8, 3.2, 2.0, 0.5], sides=7, hint=(1, 0, 0)))
    F += part("maw", ellipsoid("head", "hollow", (0, 29.5, -21.0), (3.2, 3.6, 4.0), segs=8, rings=3))
    for s, b, b2 in ((-1, "leg_l", "leg_l2"), (1, "leg_r", "leg_r2")):
        F += part(f"fl{s}", tube(b, "shadow", [(s * 7.0, 22.0, -6.0), (s * 10.0, 11.0, -9.0), (s * 9.5, 1.5, -11.0)], ra=[3.5, 2.6, 2.2], sides=7, hint=(1, 0, 0)))
        F += part(f"fp{s}", ellipsoid(b, "basalt", (s * 9.5, 1.5, -12.5), (3.0, 1.6, 3.6), segs=6, rings=2))
        F += part(f"hl{s}", tube(b2, "shadow", [(s * 7.0, 20.0, 16.0), (s * 9.0, 10.0, 20.0), (s * 8.5, 1.5, 17.0)], ra=[4.0, 2.6, 2.0], sides=7, hint=(1, 0, 0)))
        F += part(f"hp{s}", ellipsoid(b2, "basalt", (s * 8.5, 1.4, 16.0), (2.8, 1.5, 3.4), segs=6, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        shadow_arm(F, b, (s * 8.0, 30.0, -6.0), (s * 18.0, 36.0, -12.0), (s * 22.0, 24.0, -22.0), r=(2.2, 1.7, 1.2), flen=6.0)
    F += part("tail", tube("body", "shadow", [(0, 24.0, 22.0), (0, 18.0, 30.0), (0, 8.0, 34.0), (0, 1.0, 40.0)], ra=[4.0, 2.8, 1.6, 0.4], sides=6, hint=(1, 0, 0)))
    cracks(F, "body", [[(-3.0, 33.0, -2.0), (0.0, 32.4, 4.0), (3.0, 33.0, 10.0), (0.0, 32.0, 16.0)]])
    return F, [eye_at(-3.0, 34.0, -21.0, 0.8), eye_at(3.0, 34.0, -21.0, 0.8), eye_at(-2.4, 32.0, -24.0, 0.6), eye_at(2.4, 32.0, -24.0, 0.6)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "grab": {"arm_r": (-50, 0, 0), "arm_l": (-35, 0, 0), "arm_r2": (-30, 0, 0), "arm_l2": (-20, 0, 0), "body": (-10, 0, 0), "head": (-8, 0, 0)},
}

SPEC = dict(
    id="shadow_embracer", short="影抱き", kind="新月の谷の主 3案 (46 の 4.3)",
    title="影抱き (shadow_embracer) — 新月の谷の主。月の岩と影でできた大きなもの・低ポリ + ドット絵 3 案",
    sub=["影に潜って移り (床の影が走る予告)、暗がりから腕で掴む・影を投げる・影の波。段 2 から分身を影に置く。星灯りの中に引き出すと体が固まり、斬れる。",
         "高さ 4〜5 ブロック。三段目は腕を伸ばして掴みにくる姿。光る星の割れ目が弱点の印。"],
    labels=["A 岩の抱擁", "B 影の母", "C 影の獣"],
    rows=[("前 3/4", "idle", 212, 12, 300, (0, 30, -4), 3.4),
          ("横", "idle", 270, 4, 300, (0, 30, -4), 3.4),
          ("腕で掴む", "grab", 220, 10, 300, (0, 30, -4), 3.4),
          ("後ろ 3/4", "idle", 32, 14, 300, (0, 30, -4), 3.4)],
    options={
        "a": dict(name="A 岩の抱擁", desc="うずくまった巨きな岩の殻。前が裂けて開き、中の暗がりから影の腕が四本伸びる。星灯りで殻が閉じて固まる、が絵で分かる。"),
        "b": dict(name="B 影の母", desc="背の高い頭巾の影に月の岩の仮面。四本の長い腕が胸の前で黒い珠 (抱いた影) を抱える。名前の「抱き」がいちばん直に見える。"),
        "c": dict(name="C 影の獣", desc="低く這う四つ足の影に、背の岩の板と三日月の岩の顎。肩から影の腕が二本。影に潜って床を走る動きがいちばん似合う。")},
    extra="月の言葉 (玄武岩・凝灰岩の殻・影の藍の黒・星の光の割れ目)。骨 9〜11 本。光るのは割れ目と目だけ。",
    budget="区画の主 1200〜2400", atlas_h=150, pw=420,
    later="段 2 = 割れ目が広がって光が強まり、分身が影から出る。段 3 = 殻 (仮面・板) が割れ落ち、影の腕が倍に増える。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
