"""塩の抜け殻 (salt_husk) 低ポリ ラフ 3 案。塩の層の固まった人の形。近づくと殻が割れて動く (待ち伏せ)。
A 割れた人形 (胸と腕に割れ目、中の黒い洞がのぞく) / B 結晶の殻 (肩と背に塩の結晶の棘が生える) / C 砂の立像 (角の取れた立像、皮の流れが垂れる)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。割れ目は殻に沈めた細い管 (殻から出ない)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12, 0)), "head": ("body", (0, 23, 0)),
         "arm_l": ("body", (-4.0, 21, 0)), "arm_r": ("body", (4.0, 21, 0)),
         "leg_l": ("body", (-1.8, 12, 0)), "leg_r": ("body", (1.8, 12, 0))}
PAL = make_pal({
    "salt": (SALT, 0.7, dict(crack=0.05, noise=0.1)),
    "saltc": (SALTC, 0.6, dict(crack=0.12, noise=0.16)),
    "sea": (SEA, 0.55, dict(crack=0.0, noise=0.1)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": ([(70, 120, 130), (120, 176, 180), (180, 220, 220), (230, 250, 248)], 0.8, dict(crack=0.0, noise=0.04, min=2)),
}, glow=["glow"])


def limbs(F, m="salt"):
    """手足は作図空間の絶対座標 (肩・股の付け根から下へ)。"""
    F += part("upper_l", tube("arm_l", m, [(-4.0, 21.0, 0.0), (-4.4, 17.0, 0.2)], ra=0.9, sides=5, hint=(1, 0, 0)))
    F += part("fore_l", tube("arm_l", m, [(-4.4, 17.0, 0.2), (-4.6, 13.0, -0.3)], ra=[0.8, 0.6], sides=5, hint=(1, 0, 0)))
    F += part("upper_r", tube("arm_r", m, [(4.0, 21.0, 0.0), (4.4, 17.0, 0.2)], ra=0.9, sides=5, hint=(1, 0, 0)))
    F += part("fore_r", tube("arm_r", m, [(4.4, 17.0, 0.2), (4.6, 13.0, -0.3)], ra=[0.8, 0.6], sides=5, hint=(1, 0, 0)))
    for x, nm in ((-1.8, "l"), (1.8, "r")):
        F += part(f"thigh_{nm}", tube(f"leg_{nm}", m, [(x, 12.0, 0.0), (x, 6.0, 0.2)], ra=1.1, sides=5, hint=(1, 0, 0)))
        F += part(f"shin_{nm}", tube(f"leg_{nm}", m, [(x, 6.0, 0.2), (x, 0.5, -0.2)], ra=[1.0, 0.7], sides=5, hint=(1, 0, 0)))


def torso(F, m="salt"):
    F += part("torso", ellipsoid("body", m, (0, 17.0, 0.0), (3.0, 5.0, 2.4), segs=8, rings=3))
    F += part("head", ellipsoid("head", m, (0, 24.0, 0), (1.5, 1.9, 1.5), segs=7, rings=2))


def build_a(k):
    """A 割れた人形: 胸と左腕に割れ目。割れ目の奥は黒い洞 (中は空。近づくと殻が開く)。"""
    F = []
    torso(F)
    F += part("hollow_chest", ellipsoid("body", "hollow", (0.0, 17.5, -2.2), (1.4, 2.2, 0.5), segs=5, rings=1))
    F += part("crack_arm", tube("arm_l", "hollow", [(-0.2, -1.0, -1.1), (-0.4, -4.4, -1.3)], ra=[0.25, 0.2], sides=3, hint=(0, 0, 1)))
    F += part("shard", spike("arm_r", "saltc", (0.2, -6.5, -0.7), (0.2, -0.4, -1), 1.6, 0.4, sides=4, rings=1))
    F += part("crack_leg", tube("leg_r", "hollow", [(0.0, -2.0, -1.2), (0.0, -3.8, -1.4)], ra=0.2, sides=3, hint=(0, 0, 1)))
    limbs(F)
    return F, [eye_at(-0.5, 24.0, -1.5, 0.22), eye_at(0.5, 24.0, -1.5, 0.22)]


def build_b(k):
    """B 結晶の殻: 肩と背から塩の結晶の棘が生える。棘の根は殻に沈め、尖りが外へ出る。"""
    F = []
    torso(F)
    for s, x in ((-1, -2.0), (1, 2.0)):
        F += part(f"shoulder{s}", spike("body", "saltc", (x, 21.0, 0.0), (s * 0.7, 0.4, 0.0), 3.0, 0.7, sides=4, rings=2))
    F += part("back_crystal", spike("body", "salt", (0.0, 19.0, 1.8), (0, 0.6, 1.0), 3.4, 0.6, sides=4, rings=2))
    F += part("back_crystal2", spike("body", "saltc", (0.9, 14.0, 1.8), (0.5, 0.2, 1.0), 2.4, 0.5, sides=4, rings=2))
    limbs(F, "salt")
    return F, [eye_at(-0.5, 24.0, -1.5, 0.22), eye_at(0.5, 24.0, -1.5, 0.22)]


def build_c(k):
    """C 砂の立像: 角の取れた立像。皮の流れが胸から裾へ垂れ、足元に乾いた溜まり (海の緑の塩)。"""
    F = []
    torso(F, "salt")
    for i, x in enumerate((-1.6, 0.0, 1.6)):
        F += part(f"drip{i}", tube("body", "saltc", [(x, 14.0, -2.0), (x * 0.9, 11.0, -2.3), (x * 0.9, 9.0, -2.1)], ra=[0.3, 0.25, 0.05], sides=4, hint=(0, 0, 1)))
    F += part("hem", ellipsoid("body", "sea", (0, 7.2, 0.0), (3.4, 1.0, 2.6), segs=7, rings=1))
    F += part("skirt", loft("body", "saltc", [ring(9.0, 3.0, -1.6, 1.6, n=8), ring(7.2, 3.6, -1.9, 1.9, n=8)], caps=("saltc", "saltc")))
    limbs(F, "salt")
    return F, [eye_at(-0.5, 24.0, -1.5, 0.22), eye_at(0.5, 24.0, -1.5, 0.22)]


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
    rows=[("前 3/4 (動く前)", "idle", 212, 12, 340, (0, 14, 0), 8.8),
          ("横", "idle", 270, 4, 340, (0, 14, 0), 8.8),
          ("割れて動く (近づいた瞬間)", "wake", 230, 8, 340, (0, 14, 0), 8.8),
          ("上から", "idle", 200, 60, 340, (0, 14, 0), 8.8),
          ("後ろ 3/4", "idle", 32, 12, 340, (0, 14, 0), 8.8)],
    options={
        "a": dict(name="A 割れた人形", desc="胸と左腕に割れ目があり、奥は黒い洞。割れ目が見えると、殻の中が空だと分かる。ひびを一番素直に読ませる形。"),
        "b": dict(name="B 結晶の殻", desc="肩と背に塩の結晶の棘が生える。遠目には結晶の人形で、近づくと棘が鳴るように割れる。見た目の格が一番出る。"),
        "c": dict(name="C 砂の立像", desc="角の取れた立像で、皮の流れが胸から裾へ垂れる。足元に海の緑の塩の溜まり。待ち伏せの地味さが一番合う。")},
    extra="塩の言葉 (白・塩の皮・海の緑・洞の黒)。骨 7 本 (root > body > head / arm_l arm_r / leg_l leg_r)。割れ目と棘は殻に沈めた管・棘。",
    budget="人型 400〜600", atlas_h=170, pw=420,
    later="歪み = 割れ目が増え、洞が広がって殻が薄くなる (海の緑が中から透ける)。崩れ = 殻が大半抜けて、骨の影だけ残る (骨の層との接点)。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=128)
