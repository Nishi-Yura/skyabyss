"""雷石投げ (bolt_slinger)。嵐の高地の敵 (issue #20)。役: 遠くから撃つ。Lv 19〜24。
8〜16 の距離を保ち、帯電した石を山なりに投げる (予告 1.0 秒: 狙った人の足もとに青い円、満ちると落ちて小さく弾ける。傷 + 鈍足 2 秒)。
寄られると後ろへ跳んで離れる。三案とも右手に光る雷石。骨: root > body > head / arm_l / arm_r > stone / leg_l / leg_r。
A 岩角の投石手: 角の巻いた山羊の頭の二足。灰の長い毛の外套、腰に石の袋、革の投石紐
B 雷雲の小鬼: 背の低い小鬼。胴が雷雲の塊で、細い手足。頭に稲妻の冠。石を頭の上に掲げる
C 帯電した猿: 前かがみの長い腕の山の猿。白い鬣、手首に水晶の腕輪、背に石を詰めた籠
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, rows_std, storm

storm(True)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12.0, 0)), "head": ("body", (0, 22.5, -0.5)),
         "arm_l": ("body", (4.6, 20.5, 0)), "arm_r": ("body", (-4.6, 20.5, 0)), "stone": ("arm_r", (-5.4, 11.0, -1.0)),
         "leg_l": ("body", (2.0, 11.0, 0)), "leg_r": ("body", (-2.0, 11.0, 0))}
PAL = {"mats": {"fur": mat(FUR, 0.6, noise=0.22), "cloud": mat(CLOUD, 0.62, noise=0.2), "horn": mat(HORN, 0.6, stripe=3),
                "skin": mat([(60, 58, 66), (92, 88, 98), (124, 120, 130)], 0.55, noise=0.12), "leather": mat([(46, 32, 24), (76, 54, 38), (108, 80, 56)], 0.55),
                "crag": mat(CRAG, 0.6, noise=0.2), "snow": mat(SNOW, 0.7), "volt": mat(VOLT, 0.75, min=1), "dark": mat([(14, 14, 20), (28, 28, 36), (44, 44, 54)], 0.5)},
       "skull_bone": "head", "face_mat": "skin", "glow_mats": ["volt"]}


def legs(F, m, foot, bulk=1.0, digi=False):
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        if digi:
            # 獣の脚: 前へ出た腿、後ろへ折れた脛
            F += part(f"{b}_up", tube(b, m, [(sx * 2.0, 11.6, 0), (sx * 2.3, 7.4, -1.6)], ra=[1.8 * bulk, 1.3 * bulk], sides=6, hint=(1, 0, 0)))
            F += part(f"{b}_lo", tube(b, m, [(sx * 2.3, 7.6, -1.5), (sx * 2.3, 3.6, 1.2), (sx * 2.3, 1.0, 0.2)], ra=[1.2 * bulk, 0.9 * bulk, 0.8 * bulk], sides=6, hint=(1, 0, 0)))
        else:
            F += part(f"{b}_up", tube(b, m, [(sx * 2.0, 11.6, 0), (sx * 2.2, 6.2, -0.2)], ra=[1.6 * bulk, 1.3 * bulk], sides=6, hint=(1, 0, 0)))
            F += part(f"{b}_lo", tube(b, m, [(sx * 2.2, 6.4, -0.2), (sx * 2.2, 1.4, 0)], ra=[1.3 * bulk, 1.1 * bulk], sides=6, hint=(1, 0, 0)))
        F += part(f"{b}_foot", box(b, foot, (sx * 2.2, 0.8, -0.6), (2.6 * bulk, 1.6, 3.6), taper=(0.9, 0.85)))


def arms(F, m, hand, reach=1.0, thick=1.0):
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_up", tube(b, m, [(sx * 4.6, 20.6, 0), (sx * 5.2, 15.6 * reach + 2.0 * (1 - reach), 0)], ra=[1.3 * thick, 1.1 * thick], sides=6, hint=(0, 0, 1)))
        lo = 10.6 - 5.0 * (reach - 1)
        F += part(f"{b}_lo", tube(b, m, [(sx * 5.2, 15.8 * reach + 2.0 * (1 - reach), 0), (sx * 5.4, lo, -0.8)], ra=[1.1 * thick, 0.95 * thick], sides=6, hint=(0, 0, 1)))
        F += part(f"{b}_hand", ellipsoid(b, hand, (sx * 5.4, lo - 0.6, -0.9), (1.1, 1.1, 1.1), segs=6, rings=2))


def stone(F, c=(-5.4, 9.4, -1.6), r=1.2):
    F += part("stone_core", ellipsoid("stone", "volt", c, (r, r * 0.9, r), segs=6, rings=2))
    F += part("stone_shell", ellipsoid("stone", "crag", (c[0] + 0.2, c[1] + 0.3, c[2] + 0.3), (r * 0.8, r * 0.7, r * 0.75), segs=5, rings=1))


def build_a(k):
    """A 岩角の投石手"""
    F = []
    F += part("torso", ellipsoid("body", "fur", (0, 17.6, 0.2), (4.4, 5.6, 3.0), segs=9, rings=3))
    F += part("hips", ellipsoid("body", "fur", (0, 12.4, 0.2), (3.8, 2.4, 2.6), segs=8, rings=2))
    # 毛の外套 (肩から背へ垂れる)
    F += part("cloak", ellipsoid("body", "cloud", (0, 18.6, 1.6), (5.0, 4.6, 2.6), segs=9, rings=2))
    F += part("belt", tube("body", "leather", [(-3.9, 13.4, -0.6), (0, 13.6, -2.7), (3.9, 13.4, -0.6)], ra=[0.5, 0.5, 0.5], sides=4, hint=(0, 1, 0)))
    F += part("pouch", box("body", "leather", (3.2, 12.4, -1.8), (2.2, 2.4, 1.6), taper=(0.9, 0.8)))
    F += part("neck", tube("head", "fur", [(0, 22.0, -0.4), (0, 24.0, -0.8)], ra=[1.6, 1.4], sides=6, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "skin", (0, 25.4, -1.4), (2.0, 2.2, 2.4), segs=8, rings=3))
    F += part("snout", ellipsoid("head", "skin", (0, 24.4, -3.8), (1.2, 1.2, 1.8), segs=6, rings=2))
    F += part("beard", ellipsoid("head", "fur", (0, 22.8, -3.0), (1.0, 1.6, 0.9), segs=6, rings=2))
    for sx in (1, -1):
        F += part(f"horn{sx}", tube("head", "horn", [(sx * 1.2, 26.8, -1.0), (sx * 2.8, 28.0, 0.2), (sx * 3.8, 26.6, 1.0), (sx * 3.4, 25.0, 0.2)],
                                     ra=[0.8, 0.7, 0.5, 0.15], sides=5, hint=(0, 0, 1)))
        F += part(f"ear{sx}", box("head", "skin", (sx * 2.4, 25.4, -0.8), (1.6, 0.5, 0.9), taper=(0.4, 0.6)))
    arms(F, "fur", "skin")
    legs(F, "fur", "horn", digi=True)
    # 投石紐 (右手から垂れる)
    F += part("sling", tube("stone", "leather", [(-5.4, 9.8, -1.0), (-5.6, 8.0, -1.4), (-5.4, 9.6, -1.8)], ra=[0.25, 0.25, 0.25], sides=3, hint=(1, 0, 0)))
    stone(F, (-5.5, 7.8, -1.5), 1.0)
    return F, [eye_at(-0.9, 25.6, -3.4), eye_at(0.9, 25.6, -3.4)]


def build_b(k):
    """B 雷雲の小鬼"""
    F = []
    # 背の低い体 (胴は雷雲の塊)
    for i, (c, r) in enumerate((((0, 15.4, 0), (4.2, 3.6, 3.4)), ((2.4, 17.0, 0.6), (2.6, 2.4, 2.4)), ((-2.4, 16.8, 0.4), (2.6, 2.4, 2.4)),
                                ((0, 18.2, 1.2), (3.0, 2.4, 2.4)), ((0, 13.0, 0.2), (3.0, 2.0, 2.4)))):
        F += part(f"cloud{i}", ellipsoid("body", "cloud", c, r, segs=8, rings=2))
    F += part("core", ellipsoid("body", "volt", (0, 15.6, -2.6), (1.2, 1.2, 0.8), segs=6, rings=1))
    F += part("neck", tube("head", "skin", [(0, 19.0, 0), (0, 20.6, -0.4)], ra=[1.0, 0.9], sides=6, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "skin", (0, 22.4, -0.8), (2.4, 2.2, 2.2), segs=8, rings=3))
    F += part("nose", box("head", "skin", (0, 22.0, -3.0), (0.8, 1.6, 0.9), taper=(0.4, 0.3)))
    for sx in (1, -1):
        F += part(f"ear{sx}", box("head", "skin", (sx * 2.8, 22.8, -0.6), (2.4, 0.6, 1.0), taper=(0.2, 0.4)))
    # 稲妻の冠
    for i, a in enumerate((-40, 0, 40)):
        ra_ = math.radians(a)
        F += part(f"crown{i}", tube("head", "volt", [(1.2 * math.sin(ra_), 24.0, -0.8 + 0.4 * abs(math.sin(ra_))), (2.0 * math.sin(ra_), 25.8, -1.2), (1.4 * math.sin(ra_), 26.4, -0.6),
                                                     (2.4 * math.sin(ra_), 28.0, -1.0)], ra=[0.45, 0.35, 0.3, 0.05], sides=4, hint=(0, 0, 1)))
    # 細い手足
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_up", tube(b, "skin", [(sx * 3.6, 17.4, 0), (sx * 5.2, 14.2, -0.4), (sx * 5.4, 10.8, -1.0)], ra=[0.8, 0.65, 0.6], sides=5, hint=(0, 0, 1)))
        F += part(f"{b}_hand", ellipsoid(b, "dark", (sx * 5.4, 10.2, -1.1), (0.9, 0.9, 0.9), segs=6, rings=2))
    for sx, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(f"{b}_leg", tube(b, "skin", [(sx * 1.8, 12.0, 0), (sx * 2.0, 6.0, -0.4), (sx * 2.0, 1.4, 0)], ra=[0.9, 0.7, 0.65], sides=5, hint=(1, 0, 0)))
        F += part(f"{b}_foot", box(b, "dark", (sx * 2.0, 0.6, -0.8), (1.8, 1.2, 3.0), taper=(0.8, 0.7)))
    stone(F, (-5.5, 8.6, -1.6), 1.4)
    return F, [eye_at(-0.9, 22.8, -2.7, 0.6), eye_at(0.9, 22.8, -2.7, 0.6)]


def build_c(k):
    """C 帯電した猿"""
    F = []
    F += part("chest", ellipsoid("body", "fur", (0, 17.2, -1.6), (4.6, 4.6, 3.6), segs=9, rings=3))
    F += part("belly", ellipsoid("body", "skin", (0, 14.6, -2.4), (3.0, 2.8, 2.2), segs=8, rings=2))
    F += part("hips", ellipsoid("body", "fur", (0, 12.4, 0.6), (3.6, 2.4, 2.8), segs=8, rings=2))
    # 白い鬣 (肩と首を覆う)
    F += part("mane", ellipsoid("body", "snow", (0, 20.2, -0.6), (4.8, 2.6, 3.6), segs=9, rings=2))
    F += part("skull", ellipsoid("head", "skin", (0, 22.4, -3.6), (2.2, 2.2, 2.2), segs=8, rings=3))
    F += part("brow", box("head", "dark", (0, 23.4, -5.4), (3.4, 0.8, 1.0)))
    F += part("muzzle", ellipsoid("head", "skin", (0, 21.4, -5.4), (1.4, 1.1, 1.0), segs=6, rings=2))
    F += part("crest", ellipsoid("head", "snow", (0, 24.4, -2.8), (1.4, 1.2, 2.0), segs=6, rings=2))
    # 長い腕 (地面に届く) と水晶の腕輪
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_up", tube(b, "fur", [(sx * 4.6, 20.4, -1.0), (sx * 6.0, 13.0, -2.0)], ra=[1.7, 1.4], sides=6, hint=(0, 0, 1)))
        F += part(f"{b}_lo", tube(b, "fur", [(sx * 6.0, 13.2, -2.0), (sx * 6.2, 4.6, -2.6)], ra=[1.4, 1.1], sides=6, hint=(0, 0, 1)))
        F += part(f"{b}_band", tube(b, "volt", [(sx * 6.1, 7.2, -2.5), (sx * 6.15, 5.8, -2.6)], ra=[1.35, 1.25], sides=6, hint=(0, 0, 1)))
        F += part(f"{b}_hand", ellipsoid(b, "dark", (sx * 6.2, 3.6, -2.8), (1.4, 1.2, 1.5), segs=6, rings=2))
    legs(F, "fur", "dark", bulk=1.15)
    # 背の石の籠
    F += part("basket", box("body", "leather", (0, 17.4, 3.6), (4.6, 5.0, 2.4), taper=(1.1, 1.1)))
    for i, (x, y) in enumerate(((-1.2, 20.2), (1.0, 20.4), (0.0, 20.8))):
        F += part(f"basket_stone{i}", ellipsoid("body", "crag" if i != 2 else "volt", (x, y, 3.6), (1.0, 0.9, 1.0), segs=5, rings=1))
    stone(F, (-6.3, 2.2, -2.9), 1.1)
    return F, [eye_at(-0.9, 22.6, -5.6, 0.5), eye_at(0.9, 22.6, -5.6, 0.5)]


def detail(F):
    # 細部 (2026-11-09 の質上げ): 肩の毛の房・角の節・腰の石の袋の紐と房・蹄の割れ目・鼻輪・外套の裾の房
    for sx in (1, -1):
        for i, (dy, dz) in enumerate(((0.6, -0.4), (0.0, 0.8), (-0.6, 1.6))):
            F += part(f"tuft{sx}{i}", spike("body", "cloud", (sx * 4.6, 21.2 + dy, dz), (sx * 0.8, 0.5, 0.3), 1.6, 0.6, sides=4, rings=1))
        for j in range(3):
            t = 0.25 + j * 0.22
            F += part(f"hornring{sx}{j}", tube("head", "horn", [(sx * (1.2 + 2.0 * t), 26.8 + 1.4 * t, -1.0 + 1.2 * t), (sx * (1.3 + 2.0 * t), 27.0 + 1.4 * t, -0.9 + 1.2 * t)],
                                             ra=[0.85 - 0.25 * t, 0.8 - 0.25 * t], sides=6, hint=(0, 0, 1)))
        F += part(f"hoofsplit{sx}", box(("leg_l" if sx > 0 else "leg_r"), "dark", (sx * 2.2, 0.8, -2.2), (0.3, 1.4, 0.6)))
    F += part("noseRing", tube("head", "volt", [(-0.6, 23.6, -5.2), (0, 23.2, -5.5), (0.6, 23.6, -5.2)], ra=[0.18, 0.18, 0.18], sides=4, hint=(0, 0, 1)))
    for i, x in enumerate((2.6, 3.2, 3.8)):
        F += part(f"pouchstone{i}", ellipsoid("body", "crag", (x, 13.8, -2.1), (0.45, 0.4, 0.4), segs=5, rings=1))
    for i in range(6):
        a = math.radians(200 + i * 28)
        F += part(f"hem{i}", spike("body", "fur", (4.6 * math.cos(a), 14.6, 1.6 + 2.2 * math.sin(a) * 0.5), (0, -1, 0.2), 1.8, 0.5, sides=4, rings=1))
    F += part("stonebelt", tube("body", "leather", [(-3.6, 21.0, -2.2), (0, 16.4, -3.2), (3.6, 13.0, -2.0)], ra=[0.3, 0.3, 0.3], sides=4, hint=(0, 0, 1)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        F = detail(F)
    return F, eyes


POSES = {
    "idle": {},
    # 投げの予告: 右腕を後ろへ振りかぶる
    "windup": {"body": (0, 20, 0), "arm_r": (-150, 0, -20), "arm_l": (40, 0, 20), "head": (-8, 0, 0)},
    # 跳び退き
    "hop": {"body": (14, 0, 0), "leg_l": (-30, 0, 0), "leg_r": (20, 0, 0), "arm_l": (30, 0, 30), "arm_r": (30, 0, -30)},
}

SPEC = dict(
    id="bolt_slinger", short="雷石投げ", kind="嵐の敵 3案 (遠くから撃つ)", role="遠くから撃つ", land="嵐",
    title="雷石投げ (bolt_slinger) — 嵐の高地・遠くから撃つ・低ポリ 3 案",
    sub=["8〜16 の距離を保ち、帯電した雷石を山なりに投げる (予告 1.0 秒: 狙った人の足もとに青い円 → 落ちて弾ける。傷 + 鈍足 2 秒)。寄られると後ろへ跳ぶ。",
         "群れの雷狼の後ろから撃つ役。嵐の色の言葉: 嵐雲の灰青・岩・雪・雷の青白。"],
    labels=["A 岩角の投石手", "B 雷雲の小鬼", "C 帯電した猿"],
    rows=rows_std(14, 11.0, extra=(("予告: 振りかぶる", "windup"), ("跳び退き", "hop"))),
    options={
        "a": dict(name="A 岩角の投石手", desc="巻いた角の山羊の頭の二足。灰の毛の外套と腰の石の袋、右手の投石紐に光る雷石。獣の脚で跳び退く。高地の狩人らしく、雷狼の群れの「後ろの射手」と読める。"),
        "b": dict(name="B 雷雲の小鬼", desc="胴が雷雲の塊の小鬼。細い手足、頭に稲妻の冠、胸に光る核。石を頭上に掲げて投げる。小さくて素早く、帯電毛玉と同じ『雲の生き物』の仲間に見える。"),
        "c": dict(name="C 帯電した猿", desc="前かがみで長い腕の山の猿。白い鬣、手首に光る水晶の腕輪、背に石を詰めた籠。一番大きく重い見た目で、投げのたびに籠から石を取る動きが作れる。")},
    extra="骨 8 本 (root > body > head / arm_l / arm_r > stone / leg_l / leg_r)。",
    budget="地上の敵 400〜700", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
