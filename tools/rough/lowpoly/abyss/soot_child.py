"""煤の子 (soot_child) 低ポリ ラフ 3 案。灰の層の群れ (数で囲む小物)。小さく、熱い目が二つ。
A 煤の団子 (丸い煤の体に熾の目、短い腕二本) / B 煤の人形 (細い胴の小さな人形、頭に紙の白さ = 骨の白) / C 煤の影の子 (床に伏せた平たい煤、背に熾の点)
骨: root > body > head / arm_l / arm_r (A は head なし)。湧きは 4〜8 匹。三角形は 200〜300 が目安。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 2.0, 0)), "head": ("body", (0, 3.4, 0)),
         "arm_l": ("body", (1.6, 2.6, 0)), "arm_r": ("body", (-1.6, 2.6, 0)),
         # 2026-10-23 (A を作り直し): 顎・足・背の煙の尾を足した (骨 10)
         "jaw": ("body", (0, 2.3, -1.6)), "leg_l": ("root", (1.1, 1.0, -0.2)), "leg_r": ("root", (-1.1, 1.0, -0.2)),
         "wisp": ("head", (0, 4.2, 1.2)), "wisp_2": ("wisp", (0, 5.4, 2.6))}
PAL = {"mats": {
    "soot": mat(SOOT, 0.55, crack=0.0, noise=0.2),
    "char": mat(CHAR, 0.5, crack=0.0, noise=0.12),
    "ember": mat(EMBER, 0.75, crack=0.0, noise=0.05, min=2),
    "bone": mat(BONEW, 0.6, crack=0.0, noise=0.12),
}, "glow_mats": ["ember"]}


def build_a(k):
    """A 煤の団子 (2026-10-23 作り直し。前は丸い塊に腕二本で、頭の骨が空だった):
    下の腹 (body) と上の頭 (head: 熾の目二つ・眉の煤の庇・頭の煤の毛) に分け、前に口 (jaw の下顎、開くと奥の熾が見える)。
    短い腕の先に炭の爪三本、短い足二本 (歩くと交互に出る)、頭の後ろから煙の尾 (wisp > wisp_2) がたなびく。"""
    F = []
    # 腹 (body): 下ぶくれの煤の団子。腹の煤の粒
    F += part("belly", ellipsoid("body", "soot", (0, 1.9, 0.1), (2.4, 1.9, 2.35), segs=12, rings=4))
    F += part("lumps", lumps("body", "char", [((-1.7, 1.4, 1.2), 0.7), ((1.8, 1.6, 0.9), 0.65), ((0.6, 0.9, 1.9), 0.6), ((-0.9, 2.4, 1.9), 0.55)]))
    # 口: 奥の熾 (喉) と上唇は腹、下顎は jaw
    F += part("throat", ellipsoid("body", "ember", (0, 2.15, -1.95), (0.9, 0.42, 0.4), segs=8, rings=2))
    F += part("lip", ellipsoid("body", "char", (0, 2.55, -2.05), (1.15, 0.32, 0.45), segs=8, rings=1))
    F += part("jaw", ellipsoid("jaw", "char", (0, 1.8, -1.95), (1.05, 0.45, 0.55), segs=8, rings=2))
    for j, x in enumerate((-0.55, 0.0, 0.55)):
        F += part(f"tooth{j}", cone("jaw", "ember", (x, 1.95, -2.2), (0, 1, -0.2), 0.45, 0.16, sides=3, rings=1))
    # 頭 (head): 腹に半ば沈んだ上の丸み。眉の庇の下に熾の目
    F += part("crown", ellipsoid("head", "soot", (0, 3.35, -0.05), (2.05, 1.45, 2.0), segs=12, rings=3))
    F += part("brow", tube("head", "char", [(-1.5, 3.55, -1.55), (-0.5, 3.95, -1.95), (0.5, 3.95, -1.95), (1.5, 3.55, -1.55)],
                           ra=[0.22, 0.32, 0.32, 0.22], sides=4, hint=(0, 0, 1), caps=(True, True)))
    F += part("eye_l", ellipsoid("head", "ember", (-0.8, 3.25, -1.85), (0.42, 0.38, 0.3), segs=8, rings=2))
    F += part("eye_r", ellipsoid("head", "ember", (0.8, 3.25, -1.85), (0.42, 0.38, 0.3), segs=8, rings=2))
    for j, (x, z, dx, dz, ln) in enumerate(((-0.9, -0.3, -0.5, -0.3, 1.2), (0.0, -0.6, 0.1, -0.5, 1.4), (0.9, -0.2, 0.5, -0.2, 1.1),
                                            (-0.4, 0.6, -0.3, 0.4, 1.0), (0.5, 0.7, 0.4, 0.5, 1.0))):
        F += part(f"tuft{j}", cone("head", "soot", (x, 4.45, z), (dx, 1.0, dz), ln, 0.42, sides=4, rings=2))
    # 煙の尾 (wisp > wisp_2): 頭の後ろから上へ流れて先が細る
    F += part("wisp", tube("wisp", "char", [(0, 3.9, 1.0), (0, 4.7, 1.8), (0, 5.4, 2.6)], ra=[0.6, 0.48, 0.38], sides=5, hint=(1, 0, 0), caps=(True, True)))
    F += part("wisp_2", tube("wisp_2", "soot", [(0, 5.25, 2.45), (0, 6.0, 3.3), (0, 6.2, 4.3)], ra=[0.4, 0.26, 0.0], sides=5, hint=(1, 0, 0), caps=(True, False)))
    # 腕 (短い) と炭の爪
    for s_, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(b, tube(b, "char", [(s_ * 1.5, 2.6, 0), (s_ * 2.8, 2.25, -0.3), (s_ * 3.4, 1.5, -0.7)], ra=[0.75, 0.6, 0.5], sides=6, caps=(True, True)))
        F += part(b + "_hand", ellipsoid(b, "char", (s_ * 3.5, 1.25, -0.8), (0.55, 0.5, 0.55), segs=6, rings=2))
        for j, (dx, dz) in enumerate(((0.35, -0.5), (0.0, -0.65), (-0.35, -0.5))):
            F += part(f"{b}_claw{j}", cone(b, "ember" if j == 1 else "char", (s_ * 3.5 + dx * 0.5, 1.0, -0.9 + dz * 0.4), (dx * 0.4, -0.8, dz), 0.8, 0.17, sides=3, rings=1))
    # 足 (短い炭の足)
    for s_, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(b, tube(b, "char", [(s_ * 1.1, 1.1, -0.2), (s_ * 1.2, 0.55, -0.5)], ra=[0.6, 0.55], sides=6, caps=(True, True)))
        F += part(b + "_foot", ellipsoid(b, "char", (s_ * 1.2, 0.38, -0.85), (0.65, 0.38, 0.85), segs=6, rings=2))
    return F, [eye(-0.8, 3.25, -2.1, 0.3), eye(0.8, 3.25, -2.1, 0.3)]


def build_b(k):
    """B 煤の人形: 細い胴の小さな人形。頭は紙の白 (骨の白)、目の熾は一つ。"""
    F = []
    R = [ring(0.5, 1.4, -1.0, 1.0, n=8), ring(2.5, 1.0, -0.7, 0.7, n=8), ring(4.0, 0.9, -0.6, 0.6, n=8)]
    F += part("torso", loft("body", "soot", R, caps=("char", "soot")))
    F += part("head", ellipsoid("head", "bone", (0, 5.4, 0), (1.1, 1.2, 1.0), segs=10, rings=4))
    F += part("face", ellipsoid("head", "ember", (0, 5.4, -1.0), (0.4, 0.4, 0.2), segs=8, rings=3))
    F += part("arm_l", tube("arm_l", "soot", [(1.2, 3.2, 0), (2.4, 2.0, -0.3), (3.0, 0.6, -0.4)], ra=[0.7, 0.55, 0.3], sides=6, caps=(True, True)))
    F += part("arm_r", tube("arm_r", "soot", [(-1.2, 3.2, 0), (-2.4, 2.0, -0.3), (-3.0, 0.6, -0.4)], ra=[0.7, 0.55, 0.3], sides=6, caps=(True, True)))
    return F, [eye(0, 5.4, -1.3, 0.25)]


def build_c(k):
    """C 煤の影の子: 床に伏せた平たい煤。背に熾の点が三つ、四隅から細い指が出る。"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 0.9, 0), (2.8, 0.9, 2.2), segs=10, rings=3))
    for i, (x, z) in enumerate(((-1.0, -0.6), (0.2, 0.4), (1.2, -0.4))):
        F += part(f"spot{i}", ellipsoid("body", "ember", (x, 1.0, z), (0.3, 0.25, 0.3), segs=8, rings=3))
    for i, (x, z) in enumerate(((-2.2, -1.4), (2.2, -1.4), (-2.2, 1.6), (2.2, 1.6))):
        F += part(f"claw{i}", cone("body", "char", (x * 0.6, 0.5, z * 0.6), (x, 0.2, z), 1.8, 0.35, sides=3, rings=2))
    return F, [eye(-0.5, 1.4, -1.9, 0.2), eye(0.5, 1.4, -1.9, 0.2)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "wave": {"arm_l": (0, 0, 40), "arm_r": (0, 0, -40)}, "bite": {"head": (-20, 0, 0), "jaw": (35, 0, 0)}}

LATER = {'a': '歪み: 団子が割れ、中から小さな目が増える / 崩れ: 割れた煤が床に広がり、数だけが残る。', 'b': '歪み: 人形の頭が骨の白の仮面になり、首が長くなる / 崩れ: 人形がほどけ、紙の頭だけが転がる。', 'c': '歪み: 背の点が増えて光の筋になる / 崩れ: 床いっぱいの影になり、踏むと一斉に起きる。'}

SPEC = dict(
    id="soot_child", short="煤の子", kind="灰の層の敵 3案 (群れ)",
    title="煤の子 (soot_child) — 数で囲む小さな煤の子・低ポリ + ドット絵 3 案",
    sub=["灰の層の敵。4〜8 匹の群れで湧き、熱い目で人を見る。一匹は小さく (幅 0.5 ブロック)。",
         "灰の層 (黒石・玄武・熾) の色の言葉。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 煤の団子", "B 煤の人形", "C 煤の影の子"],
    poses=POSES,
    rows=None,
    options={
        "a": dict(name="A 煤の団子", desc="丸い煤の体に熾の目が二つ、短い腕。群れが転がって寄るように見え、数で囲まれても形が読みやすい。"),
        "b": dict(name="B 煤の人形", desc="細い胴の小さな人形。頭だけ骨の白で、紙の人形の名残。目の熾は一つだけ。人に似るぶん、近づかれた時の嫌さが出る。"),
        "c": dict(name="C 煤の影の子", desc="床に伏せた平たい煤。背の熾の点と四隅の細い指だけが立つ。影が床を這うように見え、数が多いほど床が黒く埋まる。"),
    },
    extra="灰の層 (余燼) の群れの敵。骨 5 本 (root > body > head / arm_l / arm_r)。鍵は熾の目・点。",
    budget="群れなので 200〜300",
    later=LATER,
    pw=380, atlas_h=160,
)
ROWS = [("前 3/4", "idle", 212, 18, 240, (0, 3, 0), 32.0, None),
        ("横", "idle", 270, 6, 240, (0, 3, 0), 32.0, None),
        ("上から (群れで見る面)", "idle", 200, 60, 240, (0, 2, 0), 32.0, None),
        ("手を伸ばす", "wave", 212, 18, 240, (0, 3, 0), 32.0, None),
        ("噛む (口が開く)", "bite", 212, 18, 240, (0, 3, 0), 32.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=128)
