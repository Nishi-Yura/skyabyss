"""避雷の像 (lightning_idol) 低ポリ ラフ 3 案。雷の層の像。雷を集めて、予告の光の後に落とす (予告つきの雷)。
A 角柱の像 (角の取れた四角い柱に人の頭、先に避雷の棒) / B 翼の像 (両腕を広げた翼、掌に光の玉) / C 針の冠の像 (頭に三本の針の冠、足元は台)
骨: root > base / body / head / arm_l / arm_r / rod。予告 (集める) は両腕を上げる、落とす は rod を前へ倒す。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "base": ("root", (0, 0, 0)), "body": ("base", (0, 3.0, 0)),
         "head": ("body", (0, 17.0, 0)), "arm_l": ("body", (-3.6, 16.0, 0)), "arm_r": ("body", (3.6, 16.0, 0)),
         "rod": ("body", (0, 22.0, 0))}
PAL = make_pal({
    "tuff": (TUFF, 0.6, dict(crack=0.06, noise=0.12)),
    "oxcu": (OXCU, 0.55, dict(crack=0.0, noise=0.08)),
    "light": (LIGHT, 0.85, dict(crack=0.0, noise=0.03, min=2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
}, glow=["light"])


def build_a(k):
    """A 角柱の像: 角の取れた四角い柱に人の頭。先の避雷の棒は頭の上へ真っ直ぐ。棒の先の光が予告。"""
    F = []
    F += part("base", loft("base", "tuff", [ring(0.0, 4.0, -4.0, 4.0, n=4), ring(1.2, 3.8, -3.8, 3.8, n=4)], caps=("tuff", "tuff")))
    F += part("column", loft("body", "tuff", [ring(1.0, 2.6, -2.6, 2.6, n=4), ring(9.0, 2.2, -2.2, 2.2, n=4), ring(15.0, 2.6, -2.6, 2.6, n=4)], caps=("tuff", "tuff")))
    F += part("head", ellipsoid("head", "tuff", (0, 19.0, 0), (1.8, 2.0, 1.8), segs=5, rings=2))
    F += part("arm_l", tube("arm_l", "tuff", [(0, 0, 0), (-0.8, -2.0, 0)], ra=0.9, sides=4, hint=(1, 0, 0)))
    F += part("arm_r", tube("arm_r", "tuff", [(0, 0, 0), (0.8, -2.0, 0)], ra=0.9, sides=4, hint=(1, 0, 0)))
    F += part("neck", tube("body", "tuff", [(0, 14.0, 0), (0, 18.0, 0)], ra=1.0, sides=4, hint=(1, 0, 0)))
    F += part("rod", tube("head", "oxcu", [(0, 19.6, 0), (0, 22.8, 0), (0, 24.6, 0)], ra=[0.25, 0.25, 0.12], sides=4, hint=(1, 0, 0)))
    F += part("rod_tip", ellipsoid("head", "light", (0, 25.0, 0), (0.45, 0.45, 0.45), segs=4, rings=1))
    return F, [eye_at(-0.6, 19.2, -1.9, 0.25), eye_at(0.6, 19.2, -1.9, 0.25)]


def build_b(k):
    """B 翼の像: 両腕が翼になり、掌の位置に光の玉。翼の根は肩に沈め、翼の縁は薄い板。"""
    F = []
    F += part("base", loft("base", "tuff", [ring(0.0, 3.6, -3.6, 3.6, n=6), ring(1.0, 3.2, -3.2, 3.2, n=6)], caps=("tuff", "tuff")))
    F += part("body", ellipsoid("body", "tuff", (0, 11.0, 0), (2.4, 6.6, 2.0), segs=7, rings=3))
    F += part("head", ellipsoid("head", "tuff", (0, 19.0, 0), (1.7, 1.9, 1.7), segs=5, rings=2))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"wing_{nm}", ellipsoid(f"arm_{nm}", "oxcu", (s * 4.4, 15.0, 0.0), (3.2, 0.4, 1.8), segs=6, rings=1))
        F += part(f"orb_{nm}", ellipsoid(f"arm_{nm}", "light", (s * 4.0, 15.4, 0.4), (0.5, 0.5, 0.5), segs=4, rings=1))
    F += part("column", tube("body", "tuff", [(0, 1.0, 0), (0, 4.6, 0)], ra=2.2, sides=6, hint=(1, 0, 0)))
    F += part("neck", tube("body", "tuff", [(0, 14.0, 0), (0, 18.0, 0)], ra=1.0, sides=4, hint=(1, 0, 0)))
    F += part("rod", tube("head", "oxcu", [(0, 19.6, 0), (0, 24.0, 0)], ra=0.22, sides=4, hint=(1, 0, 0)))
    return F, [eye_at(-0.6, 19.2, -1.6, 0.25), eye_at(0.6, 19.2, -1.6, 0.25)]


def build_c(k):
    """C 針の冠の像: 頭に三本の避雷の針。足元は四角い台。針の先が光る。台と柱は一つの塊。"""
    F = []
    F += part("base", loft("base", "tuff", [ring(0.0, 4.4, -4.4, 4.4, n=4), ring(1.6, 3.8, -3.8, 3.8, n=4)], caps=("tuff", "tuff")))
    F += part("body", loft("body", "tuff", [ring(1.0, 2.0, -2.0, 2.0, n=6), ring(9.0, 2.0, -2.0, 2.0, n=6), ring(15.5, 2.4, -2.4, 2.4, n=6)], caps=("tuff", "tuff")))
    F += part("head", ellipsoid("head", "tuff", (0, 18.0, 0), (1.8, 1.9, 1.8), segs=5, rings=2))
    for i, (x, z) in enumerate(((0, 0), (-1.0, 0.6), (1.0, 0.6))):
        F += part(f"pin{i}", spike("head", "light", (x * 0.8, 19.0, z * 0.8), (x * 0.6, 0.8, z * 0.6), 4.2, 0.22, sides=3, rings=2))

    F += part("arm_l", tube("arm_l", "tuff", [(0, 0, 0), (-0.6, -3.0, 0)], ra=0.7, sides=4, hint=(1, 0, 0)))
    F += part("arm_r", tube("arm_r", "tuff", [(0, 0, 0), (0.6, -3.0, 0)], ra=0.7, sides=4, hint=(1, 0, 0)))
    return F, [eye_at(-0.6, 18.2, -1.7, 0.25), eye_at(0.6, 18.2, -1.7, 0.25)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "gather": {"arm_l": (-120, 0, 0), "arm_r": (-120, 0, 0)},
    "strike": {"body": (0, 0, 0), "rod": (-40, 0, 0)},
}

SPEC = dict(
    id="lightning_idol", short="避雷の像", kind="雷の層の敵 3案 (A3)",
    title="避雷の像 (lightning_idol) — 雷を集めて落とす像・予告つき・低ポリ + ドット絵 3 案",
    sub=["雷の層の像。動かずに立ち、両腕を上げて雷を集める (光の予告。約 2 秒)。集めた雷は予告の場所へ落ちる。",
         "予告は地面に光の輪が出る。左の赤の目盛りは人 (1.8 ブロック)。"],
    labels=["A 角柱の像", "B 翼の像", "C 針の冠の像"],
    rows=[("前 3/4", "idle", 212, 12, 380, (0, 14, 0), 9.2),
          ("横", "idle", 270, 4, 380, (0, 14, 0), 9.2),
          ("集める (予告)", "gather", 230, 8, 380, (0, 14, 0), 9.2),
          ("落とす (棒を倒す)", "strike", 230, 8, 380, (0, 14, 0), 9.2),
          ("後ろ 3/4", "idle", 32, 12, 380, (0, 14, 0), 9.2)],
    options={
        "a": dict(name="A 角柱の像", desc="角の取れた四角い柱に人の頭。先の避雷の棒の光が予告になる。一番単純で、像の形がそのまま雷の道になる。"),
        "b": dict(name="B 翼の像", desc="両腕が翼になり、掌に光の玉。集める動きが翼を広げる形で、予告が一番大きく見える。"),
        "c": dict(name="C 針の冠の像", desc="頭に三本の避雷の針。針の先が光り、どこから落ちるかが見える。台と柱は一つで、動かない像らしさが出る。")},
    extra="雷の言葉 (凝灰岩・酸化した銅・白い雷の光)。骨 7 本 (root > base / body > head / arm_l arm_r / rod)。台と柱は一つの塊、針と棒は頭・体の子。",
    budget="人型像 400〜600", atlas_h=170, pw=420,
    later="歪み = 針や棒が増え、光が濃くなる (予告の範囲が広がる)。崩れ = 像が割れて柱だけが残り、棒の先の光が落ち続ける。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=128)
