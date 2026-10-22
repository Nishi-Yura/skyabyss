"""肋の番 (rib_guard) 低ポリ ラフ 3 案。骨の層の人型。肋骨の檻を着て、盾で道を塞ぐ。
A 肋の檻 (胴を囲む肋の弧が四段、左腕の円い盾) / B 骨盤の鉢の盾 (大きな骨盤の鉢が左腕の盾、胸は細い肋だけ) / C 板の肋鎧 (胴に重ねた四枚の板、小さな盾と三本の棘)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。盾と手は arm_l の子 (盾の中心は手首に沈める)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from human5 import *  # noqa

BONES = HUMAN_BONES
PAL = human_humanoid_pal({
    "saltc": (SALTC, 0.6, dict(crack=0.12, noise=0.18)),
})


def rib_arc(F, y, name, m="calc", r=0.42):
    """胴を囲む肋の弧 (前と横を回る U 字の管)。胴の表面から 0.6 単位出る。両端は胴に沈める。"""
    pts = [(-3.6, y, 0.4), (-2.6, y, -2.2), (0.0, y, -3.0), (2.6, y, -2.2), (3.6, y, 0.4)]
    F += part(name, tube("body", m, pts, ra=r, sides=5, hint=(0, 1, 0)))
    return F


def build_a(k):
    """A 肋の檻: 胴の前を回る肋の弧が四段。胸の中は黒い洞 (弧の隙間からのぞく)。左腕の円い盾は手首に沈める。"""
    F = []
    torso(F, "bone")
    F += part("chest_hollow", ellipsoid("body", "hollow", (0, 17.0, -1.3), (2.4, 2.8, 0.9), segs=7, rings=3))
    for i, y in enumerate((13.8, 15.8, 17.8, 19.8)):
        rib_arc(F, y, f"rib{i}", "calc")
    F += part("spine", tube("body", "calc", [(0, 12.5, 1.5), (0, 23.0, 1.5)], ra=0.5, sides=5, hint=(1, 0, 0)))
    F += part("shield", ellipsoid("arm_l", "calc", (-5.8, 13.6, -1.0), (3.4, 3.7, 0.9), segs=10, rings=4))
    F += part("shield_boss", ellipsoid("arm_l", "bone", (-5.8, 13.6, -2.0), (0.9, 0.9, 0.7), segs=5, rings=2))
    arm(F, -1, "bone", "calc")
    arm(F, 1, "bone", "bone")
    leg(F, -1, "bone", "calc")
    leg(F, 1, "bone", "calc")
    head(F, "bone", "calc")
    return F, eyes_pos()


def build_b(k):
    """B 骨盤の鉢の盾: 骨盤の大きな鉢を左腕の盾にする (縁が前へ張る)。胸は細い肋だけ、背骨が見える。"""
    F = []
    torso(F, "bone", waist=2.2)
    F += part("chest_hollow", ellipsoid("body", "hollow", (0, 16.6, -1.2), (2.2, 2.6, 0.8), segs=7, rings=3))
    for i, y in enumerate((14.0, 16.0, 18.0)):
        rib_arc(F, y, f"rib{i}", "bone", r=0.3)
    F += part("pelvis_bowl", loft("body", "calc", [ring(10.2, 2.2, -1.2, 1.2, n=8), ring(11.8, 3.2, -1.8, 1.6, n=8), ring(13.0, 3.6, -1.4, 1.0, n=8)], caps=("calc", "calc")))
    F += part("shield", ellipsoid("arm_l", "calc", (-5.9, 12.6, -0.9), (4.0, 4.6, 1.0), segs=10, rings=4))
    F += part("shield_rim", tube("arm_l", "bone", [(-9.2, 16.2, -0.2), (-5.9, 7.6, -1.6), (-2.6, 16.2, -0.2)], ra=0.5, sides=5, hint=(0, 0, 1)))
    arm(F, -1, "bone", "calc")
    arm(F, 1, "bone", "bone")
    leg(F, -1, "bone", "calc")
    leg(F, 1, "bone", "calc")
    head(F, "bone", "calc")
    return F, eyes_pos()


def build_c(k):
    """C 板の肋鎧: 胴に重ねた四枚の板 (胴の外に 0.6 単位出る)。小さな盾と、盾の縁から前へ三本の棘。"""
    F = []
    torso(F, "ash", waist=2.8)
    for i, y in enumerate((13.0, 15.4, 17.8, 20.2)):
        F += part(f"plate{i}", loft("body", "bone", [ring8(y, 3.3, 2.5, 0.0), ring8(y + 1.3, 3.6, 2.6, -0.1), ring8(y + 2.0, 3.3, 2.4, 0.0)], caps=("bone", "bone")))
    F += part("shield", ellipsoid("arm_l", "calc", (-5.6, 13.2, -0.6), (2.5, 2.7, 0.6), segs=8, rings=3))
    for i, x in enumerate((-1.0, 0.0, 1.0)):
        F += part(f"spike{i}", spike("arm_l", "bone", (-5.6 + x, 13.2, -1.0), (x * 0.25, 0.0, -1), 2.0, 0.28, sides=4, rings=2))
    arm(F, -1, "ash", "calc")
    arm(F, 1, "ash", "bone")
    leg(F, -1, "ash", "ash")
    leg(F, 1, "ash", "ash")
    head(F, "ash", "calc")
    return F, eyes_pos()


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
    labels=["A 肋の檻", "B 骨盤の鉢の盾", "C 板の肋鎧"],
    rows=[("前 3/4", "idle", 212, 12, 340, (0, 14.4, 0), 7.9),
          ("横", "idle", 270, 4, 340, (0, 14.4, 0), 7.9),
          ("構え (盾を前へ)", "guard", 200, 8, 340, (0, 14.4, 0), 7.9),
          ("突き (踏み込み)", "lunge", 230, 6, 340, (0, 14.4, 0), 7.9),
          ("後ろ 3/4", "idle", 32, 12, 340, (0, 14.4, 0), 7.9)],
    options={
        "a": dict(name="A 肋の檻", desc="胴の前を回る肋の弧が四段。胸の中は黒い洞がのぞく。左腕の円い盾は、構えると肋の檻と並ぶ。肋の数で格を見せる一番素直な形。"),
        "b": dict(name="B 骨盤の鉢の盾", desc="骨盤の大きな鉢を左腕の盾にする。縁が前へ張り、胸は細い肋だけ。正面は鉢で固く、遠くから見ても盾が一番大きく見える。"),
        "c": dict(name="C 板の肋鎧", desc="胴に重ねた四枚の板の鎧。盾は小さいが、縁から前へ三本の棘が出る。突きの形が一番鋭い。")},
    extra="骨の言葉 (骨・方解石・灰・洞の黒)。骨 7 本 (root > body > head / arm_l arm_r / leg_l leg_r)。胴は輪の積み重ね、手足は太い筒。盾と手は arm_l の子。",
    budget="人型 400〜600", atlas_h=170, pw=420,
    later="歪み = 肋が増えて檻が胴を越え、盾の骨が割れ目から糸を引く。崩れ = 檻が半分剥がれ、洞の中の骨が露出する。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
