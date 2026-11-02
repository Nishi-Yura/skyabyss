"""殻背の兎 (shell_hare) 低ポリ ラフ 3 案。新月の谷の、岩の殻を背負う月の兎 (docs/design/46 の 4.3)。
軽い重さで高く跳び、上から落ちて殻で押し潰す (落ちる所に影の円の予告)。群れで「落ちた星の穴」の巣を守る。
A 丸い殻の兎 (背に半球の玄武岩の殻、殻に月の砂のクレーターの模様。長い耳は殻の前に倒す)
B 欠けた三日月の兎 (背の殻が三日月の形の板で、縁が鋭い。耳は立って、先が星の光)
C 甲冑の兎 (重なった岩の板の鎧を背と頭に着る。前足が太く、落ちる時に殻を下に向ける)
骨: root > body > head > ear_l / ear_r / arm_l / arm_r / leg_l / leg_r / shell。高さ 1 ブロック弱。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 4.0, 0)), "head": ("body", (0, 6.0, -3.4)),
         "ear_l": ("head", (-0.8, 7.2, -3.4)), "ear_r": ("head", (0.8, 7.2, -3.4)), "shell": ("body", (0, 6.0, 1.0)),
         "arm_l": ("body", (-1.4, 3.2, -2.8)), "arm_r": ("body", (1.4, 3.2, -2.8)), "leg_l": ("body", (-2.2, 3.4, 2.2)), "leg_r": ("body", (2.2, 3.4, 2.2))}
PAL = make_pal({
    "fur": (DUST, 0.68, dict(crack=0.04, noise=0.3)),
    "basalt": (BASALT, 0.6, dict(crack=0.18, noise=0.22)),
    "tuff": (TUFFM, 0.6, dict(crack=0.16, noise=0.24)),
    "silver": (SILVER, 0.66, dict(crack=0.06, noise=0.16)),
    "hollow": (SHADOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def body(F, ear_up=False, ear_tip=None):
    """うずくまった兎: 横に長い胴、大きく畳んだ後ろ脚と長い足、短い前足、低く前に出た頭"""
    F += part("torso", ellipsoid("body", "fur", (0, 4.0, 0.6), (2.5, 2.4, 3.8), segs=10, rings=5))
    F += part("chest", ellipsoid("body", "fur", (0, 3.6, -2.4), (1.9, 2.0, 1.8), segs=8, rings=4))
    F += part("head", ellipsoid("head", "fur", (0, 6.0, -4.0), (1.7, 1.6, 2.0), segs=9, rings=4))
    F += part("cheek", ellipsoid("head", "fur", (0, 5.3, -5.0), (1.2, 0.9, 1.0), segs=7, rings=3))
    F += part("nose", ellipsoid("head", "silver", (0, 5.6, -5.95), (0.42, 0.32, 0.28), segs=5, rings=2))
    for s, b in ((-1, "ear_l"), (1, "ear_r")):
        if ear_up:
            pts = [(s * 0.8, 7.2, -3.6), (s * 1.2, 10.2, -3.4), (s * 1.4, 12.8, -2.8)]
        else:
            pts = [(s * 0.8, 7.2, -3.4), (s * 1.3, 8.2, -1.0), (s * 1.5, 7.8, 1.4)]
        F += part(f"ear{s}", tube(b, "fur", pts, ra=[0.55, 0.75, 0.35], sides=5, hint=(1, 0, 0)))
        F += part(f"earin{s}", tube(b, "silver", [(p_[0], p_[1], p_[2]) for p_ in pts[:2]], ra=[0.3, 0.42], sides=4, hint=(1, 0, 0)))
        if ear_tip:
            F += part(f"tip{s}", ellipsoid(b, ear_tip, pts[-1], (0.42, 0.55, 0.42), segs=5, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"arm{s}", tube(b, "fur", [(s * 1.4, 3.2, -2.8), (s * 1.5, 1.4, -3.2), (s * 1.5, 0.4, -3.6)], ra=[0.6, 0.45, 0.42], sides=5, hint=(0, 0, 1)))
        F += part(f"paw{s}", ellipsoid(b, "fur", (s * 1.5, 0.35, -3.9), (0.55, 0.35, 0.75), segs=5, rings=2))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"thigh{s}", ellipsoid(b, "fur", (s * 2.2, 2.8, 2.2), (1.4, 2.0, 2.4), segs=8, rings=4))
        F += part(f"foot{s}", ellipsoid(b, "fur", (s * 2.1, 0.55, 0.9), (0.8, 0.55, 2.7), segs=6, rings=2))
    F += part("tail", ellipsoid("body", "silver", (0, 4.4, 4.3), (0.9, 0.9, 0.8), segs=6, rings=2))


def eyes():
    return [eye_at(-1.25, 6.45, -5.3, 0.38), eye_at(1.25, 6.45, -5.3, 0.38)]


def build_a(k):
    """A 丸い殻の兎: 背に半球の玄武岩の殻。殻の上に月の砂のクレーターの窪み三つ。耳は殻の前に倒す。"""
    F = []
    body(F)
    R = [ring(4.6, 3.4, -2.6, 4.4, n=12), ring(6.6, 3.3, -2.5, 4.3, n=12), ring(8.2, 2.4, -1.6, 3.4, n=12), ring(9.1, 1.0, -0.4, 2.0, n=12)]
    F += part("shell", loft("shell", "basalt", R, caps=("basalt", "basalt")))
    for i, (x, y, z, r) in enumerate(((-1.3, 8.0, 0.0, 0.7), (1.5, 7.4, 2.0, 0.55), (0.2, 8.7, 1.8, 0.45))):
        F += part(f"crater{i}", ellipsoid("shell", "tuff", (x, y, z), (r, 0.25, r), segs=6, rings=2))
    F += part("skirt", tube("shell", "tuff", [(-3.3, 4.6, 1.0), (0, 4.4, 4.8), (3.3, 4.6, 1.0)], ra=0.4, sides=4, hint=(0, 1, 0)))
    return F, eyes()


def build_b(k):
    """B 欠けた三日月の兎: 背の殻は三日月の形の厚い板 (縁が鋭い)。耳は立ち、先が星の光。"""
    F = []
    body(F, ear_up=True, ear_tip="star")
    # 三日月の板: 背の上に弧を描く厚い板 (前後に尖る)
    pts = [(0, 5.0, -2.4), (0, 8.2, -0.6), (0, 9.2, 2.0), (0, 7.8, 4.6), (0, 5.2, 5.6)]
    F += part("crescent", tube("shell", "basalt", pts, ra=[0.4, 1.6, 2.0, 1.4, 0.35], sides=6, hint=(1, 0, 0)))
    F += part("edge", tube("shell", "silver", [(0, 8.9, -0.4), (0, 10.4, 2.0), (0, 8.7, 4.6)], ra=[0.25, 0.45, 0.25], sides=4, hint=(1, 0, 0)))
    for s in (-1, 1):
        F += part(f"wing{s}", ellipsoid("shell", "basalt", (s * 1.8, 6.8, 1.6), (1.3, 1.6, 3.2), segs=7, rings=3))
    return F, eyes()


def build_c(k):
    """C 甲冑の兎: 重なった岩の板 (四枚) の鎧を背に、額に岩の兜。前足が太く、爪が出る。"""
    F = []
    body(F)
    for i, (z, y, w) in enumerate(((-1.6, 6.8, 3.0), (0.4, 7.4, 3.3), (2.4, 7.0, 3.1), (4.0, 5.8, 2.6))):
        # 背を横切って弓なりに覆う板 (前の板の上に後ろの板が少し重なる)
        F += part(f"plate{i}", tube("shell", "tuff" if i % 2 else "basalt", [(-w, y - 2.6, z), (-w * 0.6, y - 0.2, z), (0, y + 0.5, z), (w * 0.6, y - 0.2, z), (w, y - 2.6, z)],
                                    ra=[0.7, 1.05, 1.15, 1.05, 0.7], sides=6, hint=(0, 0, 1)))
        F += part(f"rivet{i}", ellipsoid("shell", "silver", (0, y + 1.6, z), (0.35, 0.3, 0.35), segs=5, rings=2))
    F += part("helm", ellipsoid("head", "basalt", (0, 7.3, -4.0), (1.8, 0.9, 1.9), segs=8, rings=3))
    F += part("crest", spike("head", "silver", (0, 7.9, -3.4), (0, 0.7, 0.7), 1.6, 0.4, sides=4, rings=2))
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"gaunt{s}", ellipsoid(b, "basalt", (s * 1.5, 0.9, -3.6), (0.8, 0.8, 0.9), segs=6, rings=2))
        for j in (-1, 0, 1):
            F += part(f"claw{s}{j}", spike(b, "silver", (s * 1.5 + j * 0.3, 0.4, -4.4), (0, -0.4, -1), 0.8, 0.18, sides=4, rings=1))
    return F, eyes()


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "drop": {"body": (160, 0, 0), "arm_l": (-40, 0, 0), "arm_r": (-40, 0, 0), "leg_l": (40, 0, 0), "leg_r": (40, 0, 0)},
}

SPEC = dict(
    id="shell_hare", short="殻背の兎", kind="新月の谷の敵 3案 (46 の 4.3)",
    title="殻背の兎 (shell_hare) — 高く跳び、殻で押し潰す月の兎・低ポリ + ドット絵 3 案",
    sub=["軽い重さで高く跳び、上で裏返って殻を下にし、落ちて押し潰す (落ちる所に影の円の予告)。群れで落ちた星の穴の巣を守る。",
         "高さ 1 ブロック弱。二段目は宙で殻を下に向けた姿 (この敵の決め絵)。"],
    labels=["A 丸い殻の兎", "B 欠けた三日月の兎", "C 甲冑の兎"],
    rows=[("前 3/4", "idle", 212, 18, 300, (0, 6, 0), 20.0),
          ("横", "idle", 270, 6, 300, (0, 6, 0), 20.0),
          ("殻で落ちる (宙)", "drop", 230, 12, 300, (0, 6, 0), 20.0),
          ("上から", "idle", 200, 60, 300, (0, 6, 0), 20.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 6, 0), 20.0)],
    options={
        "a": dict(name="A 丸い殻の兎", desc="背に半球の玄武岩の殻。殻の上に小さなクレーター。長い耳は殻の前に倒す。殻が「小さな月」に見える。"),
        "b": dict(name="B 欠けた三日月の兎", desc="背の殻が三日月の形の厚い板。縁が銀に光り、耳が立って先に星の光。落ちる時は三日月の刃が下になる。"),
        "c": dict(name="C 甲冑の兎", desc="重なった岩の板の鎧と額の兜、太い前足の爪。いちばん「押し潰す」重さが伝わる。")},
    extra="月の言葉 (月の砂の毛・玄武岩の殻・凝灰岩・銀・星の光)。骨 11 本 (root > body > head > ear_l/r / shell / 四肢)。",
    budget="中の敵 500〜900", atlas_h=150, pw=420,
    later="二段目 = 殻に星の光の割れ目。三段目 = 殻が大きくなり、縁に月の欠片の棘。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
