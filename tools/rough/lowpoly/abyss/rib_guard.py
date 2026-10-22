"""肋の番 (rib_guard) 低ポリ ラフ 3 案。骨の層の人型。肋骨の檻を着て、左腕の盾で道を塞ぐ。
A 肋の檻 (胴を囲む肋の弧と、左腕の円い骨の盾) / B 骨盤の盾 (大きな骨盤の鉢を盾に、肋は細い) / C 板の肋鎧 (肋を平たい板にし、棘の盾)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。盾は arm_l に付く (受け口 arm_l)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12, 0)), "head": ("body", (0, 23, 0)),
         "arm_l": ("body", (-4.2, 21, 0)), "arm_r": ("body", (4.2, 21, 0)),
         "leg_l": ("body", (-1.8, 12, 0)), "leg_r": ("body", (1.8, 12, 0))}
PAL = make_pal({
    "bone": (BONEC, 0.6, dict(crack=0.06, noise=0.12)),
    "calc": (CALC, 0.6, dict(crack=0.08, noise=0.14)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "ash": (ASHY, 0.55, dict(crack=0.1, noise=0.2)),
    "ember": ([(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)], 0.7, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["ember"])


def rib_arcs(F, mat_, z_list, w=3.6, top=20.5, bot=13.0, r=0.3, bone="body"):
    """胴を囲む肋の弧 (前後に開いた U 字の管)。弧の両端は背骨 (胴の中心) に沈める。"""
    for i, z in enumerate(z_list):
        y_mid = (top + bot) / 2 - i * 1.4
        F += part(f"rib{i}", tube(bone, mat_, [(-w * 0.8, y_mid + 1.2, z + 0.6), (0.0, y_mid - 0.4, z - 1.2 if z < 0 else z - 1.0), (w * 0.8, y_mid + 1.2, z + 0.6)],
                                  ra=[r, r * 1.2, r], sides=4, hint=(0, 1, 0)))


def limbs(F, arm_mat="bone", leg_mat="bone"):
    """手足は作図空間の絶対座標 (肩・股の付け根から下へ)。"""
    F += part("upper_l", tube("arm_l", arm_mat, [(-4.2, 21.0, 0.0), (-4.6, 17.0, 0.2)], ra=0.8, sides=4, hint=(1, 0, 0)))
    F += part("fore_l", tube("arm_l", arm_mat, [(-4.6, 17.0, 0.2), (-4.8, 13.0, -0.3)], ra=[0.7, 0.55], sides=4, hint=(1, 0, 0)))
    F += part("upper_r", tube("arm_r", arm_mat, [(4.2, 21.0, 0.0), (4.6, 17.0, 0.2)], ra=0.8, sides=4, hint=(1, 0, 0)))
    F += part("fore_r", tube("arm_r", arm_mat, [(4.6, 17.0, 0.2), (4.8, 13.0, -0.3)], ra=[0.7, 0.55], sides=4, hint=(1, 0, 0)))
    for x, nm in ((-1.8, "l"), (1.8, "r")):
        F += part(f"thigh_{nm}", tube(f"leg_{nm}", leg_mat, [(x, 12.0, 0.0), (x, 6.0, 0.2)], ra=1.0, sides=4, hint=(1, 0, 0)))
        F += part(f"shin_{nm}", tube(f"leg_{nm}", leg_mat, [(x, 6.0, 0.2), (x, 0.5, -0.2)], ra=[0.85, 0.6], sides=4, hint=(1, 0, 0)))


def head(F, mat_="bone"):
    F += part("skull", ellipsoid("head", mat_, (0, 24.2, 0), (1.5, 1.7, 1.4), segs=6, rings=2))
    F += part("jaw", ellipsoid("head", "calc", (0, 22.6, -0.4), (1.1, 0.5, 0.9), segs=5, rings=1))
    return F


def build_a(k):
    """A 肋の檻: 胴を囲む肋の弧が三段。左腕の円い骨の盾 (胴から離れた盾は肩に沈める)。胸の中は黒い洞。"""
    F = []
    F += part("chest", ellipsoid("body", "hollow", (0, 17.0, 0.0), (2.7, 4.5, 2.2), segs=7, rings=2))
    rib_arcs(F, "bone", (-1.3, 0.8, -0.1 + 0.0), top=21.0, bot=14.0)
    F += part("spine", tube("body", "calc", [(0, 13.5, 1.0), (0, 22.0, 1.0)], ra=0.3, sides=4, hint=(1, 0, 0)))
    F += part("shield", ellipsoid("arm_l", "calc", (-4.8, 13.0, -0.6), (3.0, 3.0, 0.6), segs=8, rings=2))
    F += part("shield_boss", ellipsoid("arm_l", "bone", (-4.8, 13.0, -1.0), (0.7, 0.7, 0.7), segs=4, rings=1))
    limbs(F, "bone", "bone")
    head(F)
    return F, [eye_at(-0.55, 24.3, -1.2, 0.3), eye_at(0.55, 24.3, -1.2, 0.3)]


def build_b(k):
    """B 骨盤の盾: 骨盤の鉢を盾にする (大きな円い鉢が左腕に、縁は内側へ沈む)。肋は細い線だけ。"""
    F = []
    F += part("chest", ellipsoid("body", "bone", (0, 16.5, 0.0), (2.2, 3.4, 1.8), segs=6, rings=2))
    rib_arcs(F, "calc", (-1.0, 0.5), top=20.0, bot=15.0, r=0.2)
    F += part("pelvis", loft("body", "calc", [ring(11.5, 2.4, -1.0, 1.0, n=8), ring(12.8, 2.8, -1.4, 1.4, n=8)], caps=("calc", "calc")))
    F += part("shield", ellipsoid("arm_l", "calc", (-4.8, 13.0, -0.7), (3.4, 3.6, 0.8), segs=10, rings=2))
    F += part("shield_rim", tube("arm_l", "bone", [(-8.2, 14.5, -0.3), (-4.8, 9.0, -0.8), (-1.4, 14.5, -0.3)], ra=0.4, sides=4, hint=(0, 0, 1)))
    limbs(F, "bone", "calc")
    head(F)
    return F, [eye_at(-0.55, 24.3, -1.2, 0.3), eye_at(0.55, 24.3, -1.2, 0.3)]


def build_c(k):
    """C 板の肋鎧: 肋を平たい板にして胴に重ねる。盾は小さく、棘を前へ三本。"""
    F = []
    F += part("chest", ellipsoid("body", "ash", (0, 17.0, 0.0), (2.6, 4.2, 2.1), segs=7, rings=2))
    for i in range(4):
        y = 14.0 + i * 2.0
        F += part(f"plate{i}", loft("body", "bone", [ring(y, 2.9, -1.9, 1.2, n=8), ring(y + 1.2, 3.2, -2.1, 1.3, n=8), ring(y + 1.6, 2.9, -1.9, 1.2, n=8)], caps=("bone", "bone")))
    F += part("shield", ellipsoid("arm_l", "calc", (-4.8, 13.4, -0.4), (2.0, 2.0, 0.4), segs=6, rings=2))
    for i, x in enumerate((-0.9, 0.0, 0.9)):
        F += part(f"spike{i}", spike("arm_l", "bone", (-4.8 + x, 13.4, -0.2), (x * 0.2, 0.0, -1), 1.4, 0.22, sides=3, rings=1))
    limbs(F, "calc", "ash")
    head(F, "bone")
    return F, [eye_at(-0.55, 24.3, -1.2, 0.3), eye_at(0.55, 24.3, -1.2, 0.3)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "guard": {"arm_l": (-30, 0, 0), "arm_r": (-20, 0, 0)},
    "lunge": {"body": (14, 0, 0), "arm_r": (-70, 0, 0), "leg_l": (-25, 0, 0), "leg_r": (20, 0, 0)},
}

SPEC = dict(
    id="rib_guard", short="肋の番", kind="骨の層の敵 3案 (A3)",
    title="肋の番 (rib_guard) — 肋骨の檻を着た人型・盾つき・低ポリ + ドット絵 3 案",
    sub=["骨の層の人型。肋の檻を胸に着て、左腕の盾で道を塞ぐ。盾の裏からだけ突いてくる (盾を向けた正面は通らない)。",
         "左の赤の目盛りは人 (1.8 ブロック)。盾を割ると肋が落ちて、中の骨が見える。"],
    labels=["A 肋の檻", "B 骨盤の盾", "C 板の肋鎧"],
    rows=[("前 3/4", "idle", 212, 12, 340, (0, 14, 0), 8.8),
          ("横", "idle", 270, 4, 340, (0, 14, 0), 8.8),
          ("構え (盾を前へ)", "guard", 200, 8, 340, (0, 14, 0), 8.8),
          ("突き (踏み込み)", "lunge", 230, 6, 340, (0, 14, 0), 8.8),
          ("後ろ 3/4", "idle", 32, 12, 340, (0, 14, 0), 8.8)],
    options={
        "a": dict(name="A 肋の檻", desc="胴を囲む肋の弧が三段。胸の中は黒い洞。左腕の円い骨の盾は、構えると肋の檻と並ぶ。肋の数で格を見せる一番素直な形。"),
        "b": dict(name="B 骨盤の盾", desc="骨盤の鉢を大きな盾にして左腕に持つ。肋は細い線だけで、盾が一番大きく見える。正面は鉢で固く、横と後ろから殴ると割れる。"),
        "c": dict(name="C 板の肋鎧", desc="肋を平たい板にして胴に重ねた鎧。盾は小さいが、棘が前へ三本出る。突きの形が一番鋭い。")},
    extra="骨の言葉 (骨・方解石・灰・洞の黒)。骨 7 本 (root > body > head / arm_l arm_r / leg_l leg_r)。盾は arm_l の子。",
    budget="人型 400〜600", atlas_h=170, pw=420,
    later="歪み = 肋が増えて檻が胴を越え、盾の骨が割れ目から糸を引く。崩れ = 檻が半分剥がれ、洞の中の骨が露出する。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=128)
