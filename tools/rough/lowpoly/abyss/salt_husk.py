"""塩の抜け殻 (salt_husk) 低ポリ ラフ 3 案。塩の層の固まった人の形。近づくと殻が割れて動く (待ち伏せ)。
A 割れた人形 (胸と腕に割れ目、中の黒い洞がのぞく) / B 結晶の殻 (肩と背に塩の結晶の棘が生える) / C 砂の立像 (角の取れた立像、裾が塩の皮で垂れる)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。割れ目と棘は殻に沈めた管・棘。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from human5 import *  # noqa

BONES = HUMAN_BONES
PAL = human_humanoid_pal({
    "salt": (SALT, 0.7, dict(crack=0.16, noise=0.2)),
    "saltc": (SALTC, 0.62, dict(crack=0.22, noise=0.26)),
    "sea": (SEA, 0.55, dict(crack=0.1, noise=0.2)),
})


def build_a(k):
    """A 割れた人形: 胸と左腕に割れ目。割れ目の奥は黒い洞 (中は空。近づくと殻が開く)。"""
    F = []
    human_base(F, "salt", "salt", "salt", "saltc")
    F += part("chest_hollow", ellipsoid("body", "hollow", (0.4, 17.4, -2.1), (1.5, 2.3, 0.6), segs=6, rings=2))
    F += part("crack_arm", tube("arm_l", "hollow", [(-4.6, 20.0, -1.9), (-4.9, 16.0, -2.0)], ra=0.3, sides=4, hint=(0, 0, 1)))
    F += part("crack_leg", tube("leg_r", "hollow", [(2.0, 9.0, -1.8), (2.1, 5.0, -2.0)], ra=0.3, sides=4, hint=(0, 0, 1)))
    F += part("shard", spike("arm_r", "saltc", (5.2, 13.0, -0.8), (0.2, -0.5, -1), 2.0, 0.6, sides=5, rings=2))
    return F, eyes_pos()


def build_b(k):
    """B 結晶の殻: 肩と背から塩の結晶の棘が生える。棘の根は肩と背に沈め、尖りが外へ出る。"""
    F = []
    human_base(F, "salt", "salt", "salt", "saltc")
    for s in (-1, 1):
        F += part(f"shoulder{s}", spike("body", "saltc", (s * 3.6, 21.0, 0.0), (s * 0.8, 0.5, 0.0), 3.8, 0.9, sides=5, rings=3))
    F += part("back_crystal", spike("body", "salt", (0.0, 19.6, 2.0), (0, 0.6, 1.0), 4.4, 0.8, sides=5, rings=3))
    F += part("back_crystal2", spike("body", "saltc", (1.2, 15.0, 2.0), (0.6, 0.2, 1.0), 3.0, 0.6, sides=5, rings=3))
    F += part("crown", spike("head", "saltc", (0.0, 26.3, 0.0), (0, 1.0, 0.0), 2.6, 0.5, sides=5, rings=2))
    return F, eyes_pos()


def build_c(k):
    """C 砂の立像: 角の取れた立像。皮の流れが胸から裾へ垂れ、裾は広い塩の皮。足元に海の緑の溜まり。"""
    F = []
    human_base(F, "salt", "salt", "salt", "saltc")
    F += part("skirt", loft("body", "saltc", [ring8(10.0, 3.2, 2.2, 0.0), ring8(7.0, 3.9, 2.8, -0.2), ring8(3.8, 4.2, 3.0, -0.4)], caps=("saltc", "saltc")))
    for i, x in enumerate((-1.8, 0.0, 1.8)):
        F += part(f"drip{i}", tube("body", "saltc", [(x, 16.0, -2.5), (x * 0.9, 13.5, -2.8), (x * 0.9, 10.5, -2.6)], ra=[0.42, 0.36, 0.1], sides=5, hint=(0, 0, 1)))
    F += part("hem_pool", ellipsoid("body", "sea", (0, 1.4, 0.0), (5.0, 0.8, 4.2), segs=9, rings=2))
    return F, eyes_pos()


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "wake": {"body": (-12, 0, 0), "arm_l": (-40, 0, 0), "arm_r": (-30, 0, 0), "leg_l": (-15, 0, 0)},
}

SPEC = dict(
    id="salt_husk", short="塩の抜け殻", kind="塩の層の敵 3案 (A3)",
    title="塩の抜け殻 (salt_husk) — 固まった人の形・近づくと割れて動く・低ポリ + ドット絵 3 案",
    sub=["塩の層の待ち伏せ。動かずに人の形で立ち、近づくと殻が割れて動き出す。割れた殻は足止めの塩になって床に残る。",
         "左の赤の目盛りは人 (1.8 ブロック)。動く前は人形と見分けがつきにくい。"],
    labels=["A 割れた人形", "B 結晶の殻", "C 砂の立像"],
    rows=[("前 3/4 (動く前)", "idle", 212, 12, 340, (0, 14.4, 0), 8.4),
          ("横", "idle", 270, 4, 340, (0, 14.4, 0), 8.4),
          ("割れて動く (近づいた瞬間)", "wake", 230, 8, 340, (0, 14.4, 0), 8.4),
          ("上から", "idle", 200, 60, 340, (0, 14.4, 0), 8.4),
          ("後ろ 3/4", "idle", 32, 12, 340, (0, 14.4, 0), 8.4)],
    options={
        "a": dict(name="A 割れた人形", desc="胸と左腕に割れ目があり、奥は黒い洞。割れ目が見えると、殻の中が空だと分かる。ひびを一番素直に読ませる形。"),
        "b": dict(name="B 結晶の殻", desc="肩と背に塩の結晶の棘が生え、頭に冠の棘。遠目には結晶の人形で、格が一番出る。動くと棘が鳴るように割れる。"),
        "c": dict(name="C 砂の立像", desc="角の取れた立像で、裾が広い塩の皮になって垂れる。皮の流れが胸から裾へ続き、足元に海の緑の溜まり。待ち伏せの地味さが一番合う。")},
    extra="塩の言葉 (白・塩の皮・海の緑・洞の黒)。骨 7 本 (root > body > head / arm_l arm_r / leg_l leg_r)。胴は輪の積み重ね、手足は太い筒。割れ目と棘は殻に沈めた管・棘。",
    budget="人型 400〜600", atlas_h=170, pw=420,
    later="歪み = 割れ目が増え、洞が広がって殻が薄くなる (海の緑が中から透ける)。崩れ = 殻が大半抜けて、骨の影だけ残る (骨の層との接点)。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
