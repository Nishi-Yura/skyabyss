"""煤の子 (soot_child) 低ポリ ラフ 3 案。灰の層の群れ (数で囲む小物)。小さく、熱い目が二つ。
A 煤の団子 (丸い煤の体に熾の目、短い腕二本) / B 煤の人形 (細い胴の小さな人形、頭に紙の白さ = 骨の白) / C 煤の影の子 (床に伏せた平たい煤、背に熾の点)
骨: root > body > head / arm_l / arm_r (A は head なし)。湧きは 4〜8 匹。三角形は 200〜300 が目安。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 2.0, 0)), "head": ("body", (0, 3.4, 0)),
         "arm_l": ("body", (1.6, 2.6, 0)), "arm_r": ("body", (-1.6, 2.6, 0))}
PAL = {"mats": {
    "soot": mat(SOOT, 0.55, crack=0.0, noise=0.2),
    "char": mat(CHAR, 0.5, crack=0.0, noise=0.12),
    "ember": mat(EMBER, 0.75, crack=0.0, noise=0.05, min=2),
    "bone": mat(BONEW, 0.6, crack=0.0, noise=0.12),
}, "glow_mats": ["ember"]}


def build_a(k):
    """A 煤の団子: 丸い煤の体、熾の目二つ、短い腕。"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 1.6, 0), (2.4, 2.1, 2.4), segs=10, rings=4))
    F += part("eye_l", ellipsoid("body", "ember", (-0.9, 2.6, -2.2), (0.45, 0.45, 0.3), segs=8, rings=3))
    F += part("eye_r", ellipsoid("body", "ember", (0.9, 2.6, -2.2), (0.45, 0.45, 0.3), segs=8, rings=3))
    F += part("arm_l", tube("arm_l", "char", [(1.6, 2.6, 0), (3.2, 2.2, -0.2), (4.0, 1.3, -0.6)], ra=[0.9, 0.72, 0.42], sides=6, caps=(True, True)))
    F += part("arm_r", tube("arm_r", "char", [(-1.6, 2.6, 0), (-3.2, 2.2, -0.2), (-4.0, 1.3, -0.6)], ra=[0.9, 0.72, 0.42], sides=6, caps=(True, True)))
    return F, [eye(-0.9, 2.6, -2.5, 0.35), eye(0.9, 2.6, -2.5, 0.35)]


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


POSES = {"idle": {}, "wave": {"arm_l": (0, 0, 40), "arm_r": (0, 0, -40)}}

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
        ("手を伸ばす", "wave", 212, 18, 240, (0, 3, 0), 32.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=64)
