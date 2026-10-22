"""避雷の像 (lightning_idol) 低ポリ ラフ 3 案。雷の層の像。両腕を上げて雷を集め、予告の光の後に落とす (予告つきの雷)。
A 角柱の像 (人型の像、胸に雷の筋、頭上に避雷の棒) / B 翼の像 (両腕が翼、掌に光の玉) / C 針の冠の像 (頭に三本の針の冠、胸に光の筋)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。台は根元に沈める。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from human5 import *  # noqa

BONES = HUMAN_BONES
PAL = human_humanoid_pal({
    "tuff": (TUFF, 0.6, dict(crack=0.16, noise=0.22)),
    "oxcu": (OXCU, 0.55, dict(crack=0.1, noise=0.18)),
    "light": (LIGHT, 0.85, dict(crack=0.0, noise=0.03, min=2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": (LIGHT, 0.85, dict(crack=0.0, noise=0.03, min=2)),
}, )
PAL["glow_mats"] = ["light", "glow"]


def pedestal(F):
    F += part("pedestal", loft("body", "tuff", [ring8(-0.2, 5.0, 4.6, 0.0), ring8(1.8, 4.4, 4.0, 0.0), ring8(2.6, 3.8, 3.4, 0.0)], caps=("tuff", "tuff")))
    return F


def build_a(k):
    """A 角柱の像: 人型の像。胸に雷の筋 (光る溝)、頭上に避雷の棒。棒の先の光が予告。"""
    F = []
    human_base(F, "tuff", "tuff", "tuff", "tuff")
    pedestal(F)
    F += part("chest_seam", tube("body", "light", [(-1.6, 15.5, -3.2), (0.0, 17.2, -3.3), (1.6, 15.5, -3.2)], ra=0.22, sides=4, hint=(0, 1, 0)))
    F += part("rod", tube("head", "oxcu", [(0.0, 26.0, 0.0), (0.0, 29.5, 0.0), (0.0, 31.0, 0.0)], ra=[0.36, 0.3, 0.14], sides=5, hint=(1, 0, 0)))
    F += part("rod_tip", ellipsoid("head", "light", (0.0, 31.6, 0.0), (0.55, 0.55, 0.55), segs=5, rings=2))
    return F, eyes_pos()


def build_b(k):
    """B 翼の像: 両腕が翼になり、掌の位置に光の玉。翼は肩から外へ広がる薄い板。"""
    F = []
    human_base(F, "tuff", "tuff", "tuff", "tuff")
    pedestal(F)
    for s in (-1, 1):
        F += part(f"wing_{s}", ellipsoid(f"arm_{'l' if s < 0 else 'r'}", "oxcu", (s * 9.0, 18.0, 0.0), (5.6, 0.7, 3.0), segs=9, rings=3))
        F += part(f"orb_{s}", ellipsoid(f"arm_{'l' if s < 0 else 'r'}", "light", (s * 7.2, 15.2, 0.6), (0.9, 0.9, 0.9), segs=5, rings=2))
    return F, eyes_pos()


def build_c(k):
    """C 針の冠の像: 頭に三本の避雷の針 (針の先が光る)。胸に光の筋。"""
    F = []
    human_base(F, "tuff", "tuff", "tuff", "tuff")
    pedestal(F)
    F += part("chest_seam", tube("body", "light", [(-1.6, 15.5, -3.2), (0.0, 17.2, -3.3), (1.6, 15.5, -3.2)], ra=0.22, sides=4, hint=(0, 1, 0)))
    for i, (x, z) in enumerate(((0.0, 0.0), (-1.2, 0.8), (1.2, 0.8))):
        F += part(f"pin{i}", spike("head", "light", (x, 25.8, z), (x * 0.5, 1.0, z * 0.5), 5.0, 0.4, sides=4, rings=3))
    return F, eyes_pos()


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "gather": {"arm_l": (-120, 0, 0), "arm_r": (-120, 0, 0)},
    "strike": {"body": (0, 0, 0), "arm_r": (-150, 0, 0)},
}

SPEC = dict(
    id="lightning_idol", short="避雷の像", kind="雷の層の敵 3案 (A3)",
    title="避雷の像 (lightning_idol) — 雷を集めて落とす像・予告つき・低ポリ + ドット絵 3 案",
    sub=["雷の層の像。動かずに立ち、両腕を上げて雷を集める (光の予告。約 2 秒)。集めた雷は予告の場所へ落ちる。",
         "予告は地面に光の輪が出る。左の赤の目盛りは人 (1.8 ブロック)。"],
    labels=["A 角柱の像", "B 翼の像", "C 針の冠の像"],
    rows=[("前 3/4", "idle", 212, 12, 380, (0, 16.0, 0), 7.6),
          ("横", "idle", 270, 4, 380, (0, 16.0, 0), 7.6),
          ("集める (予告)", "gather", 230, 8, 380, (0, 16.0, 0), 7.6),
          ("落とす (腕を振る)", "strike", 230, 8, 380, (0, 16.0, 0), 7.6),
          ("後ろ 3/4", "idle", 32, 12, 380, (0, 16.0, 0), 7.6)],
    options={
        "a": dict(name="A 角柱の像", desc="人型の像。胸に白い雷の筋、頭上に避雷の棒。棒の先の光が予告になり、像の形がそのまま雷の道になる。"),
        "b": dict(name="B 翼の像", desc="両腕が翼になり、掌に光の玉。集める動きが翼を広げる形で、予告が一番大きく見える。"),
        "c": dict(name="C 針の冠の像", desc="頭に三本の避雷の針。針の先が光り、どこから落ちるかが見える。胸の光の筋と揃って、動かない像らしさが出る。")},
    extra="雷の言葉 (凝灰岩・酸化した銅・白い雷の光)。骨 7 本 (root > body > head / arm_l arm_r / leg_l leg_r)。台は胴の子で根元に沈める。",
    budget="人型像 400〜600", atlas_h=170, pw=420,
    later="歪み = 針や棒が増え、光が濃くなる (予告の範囲が広がる)。崩れ = 像が割れて胴だけが残り、棒の先の光が落ち続ける。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
