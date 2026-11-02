"""野盗の弓手 (bandit_archer)。島と野盗の野営の射手。Lv 1〜3、遠距離。
一体一案: 野盗と同じ荒くれの仲間だが、狩人の装い。つばの広い編み笠 (日除け) と首巻き、草色の短い外套 (肩から胸に掛けた片掛け)、
背に羽根の矢を差した矢筒、左手に短い反り弓。脚は脚絆。身軽で細い。
動き: 離れて弓を引き (予告 0.8 秒、引いた弦と光る鏃)、射ったら後ろへ跳ぶ。
"""
from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, human, run1, part, mat
import bandit as B

BONES = dict(HUMAN_BONES)
CLOAK = [(34, 44, 30), (52, 66, 42), (74, 92, 56), (98, 118, 72), (124, 142, 92)]
STRAW = [(110, 86, 46), (146, 118, 66), (184, 154, 92), (214, 188, 124)]
FEATHER = [(150, 150, 150), (196, 196, 190), (232, 230, 222)]
PAL = {"mats": dict(B.PAL["mats"], cloak=mat(CLOAK, 0.58, noise=0.2), straw=mat(STRAW, 0.62, noise=0.2), feather=mat(FEATHER, 0.66)),
       "skull_bone": "head", "face_mat": "skin"}


def build(k):
    F = []
    human(F, skin="skin", shirt="cloth", pants="pants", boots="leather", bulk=0.92)
    F += part("head", ellipsoid("head", "skin", (0, 27.6, 0), (3.0, 3.3, 3.1), segs=8, rings=3))
    F += part("scarf", ellipsoid("head", "mask", (0, 25.4, 0), (3.2, 1.5, 3.2), segs=8, rings=2))
    F += part("scarf_tail", tube("head", "mask", [(1.6, 25.0, 2.6), (2.4, 22.6, 3.6), (2.0, 20.2, 3.8)], ra=[0.7, 0.6, 0.3], sides=4, hint=(1, 0, 0)))
    # 編み笠 (つばが広く平たい円錐) と顎紐
    F += part("hat", loft("head", "straw", [ck_ring(29.6, 7.0), ck_ring(30.2, 6.6), ck_ring(31.8, 3.0), ck_ring(32.8, 0.5)], caps=("straw", "straw")))
    for sx in (1, -1):
        F += part(f"strap{sx}", tube("head", "rope", [(sx * 3.0, 29.4, -0.4), (sx * 2.2, 25.6, -1.6)], ra=[0.15, 0.15], sides=3, hint=(0, 0, 1)))
        F += part(f"eye{sx}", box("head", "dark", (sx * 1.15, 27.9, -2.95), (1.1, 0.45, 0.4)))
    # 片掛けの外套 (左肩から胸へ)、帯
    F += part("cloak", ellipsoid("body", "cloak", (0.8, 19.0, 0.3), (4.9, 4.6, 3.1), segs=8, rings=3))
    F += part("cloak_hem", box("body", "cloak", (0.6, 14.8, 1.4), (8.4, 2.6, 2.8), taper=(1.1, 1.1)))
    F += part("belt", tube("body", "leather", [(-4.2, 13.6, -0.6), (0, 13.4, -2.7), (4.2, 13.6, -0.6), (0, 13.8, 2.7), (-4.2, 13.6, -0.6)], ra=[0.45] * 5, sides=4, hint=(0, 1, 0)))
    # 背の矢筒と羽根の矢
    F += part("quiver", tube("body", "leather", [(-1.6, 13.6, 3.2), (1.2, 21.8, 3.6)], ra=[1.3, 1.4], sides=6, hint=(1, 0, 0)))
    for i, dx in enumerate((-0.4, 0.4, 1.1)):
        F += part(f"arrow{i}", tube("body", "wood", [(1.0 + dx, 21.4, 3.6), (1.4 + dx, 24.2, 3.7)], ra=[0.15, 0.15], sides=3, hint=(1, 0, 0)))
        F += part(f"fletch{i}", box("body", "feather", (1.45 + dx, 24.4, 3.7), (0.7, 1.4, 0.2)))
    # 左手の短い反り弓
    F += part("bow", tube("offhand", "wood", [(6.2, 17.2, -1.2), (6.6, 14.6, -1.8), (6.0, 10.6, -1.6), (6.6, 6.6, -1.8), (6.2, 4.0, -1.2)], ra=[0.25, 0.4, 0.5, 0.4, 0.25], sides=4, hint=(0, 0, 1)))
    F += part("string", tube("offhand", "rope", [(6.2, 17.0, -1.0), (6.2, 4.2, -1.0)], ra=[0.08, 0.08], sides=3, hint=(0, 0, 1)))
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        for y in (4.6, 3.0):
            F += part(f"{leg}_wrap{y}", tube(leg, "cloth", [(sx * 2.2, y, -0.2), (sx * 2.2, y - 1.0, -0.2)], ra=[1.35, 1.3], sides=6, hint=(1, 0, 0)))
    return F, []


def ck_ring(y, r, n=10):
    import math
    return [V(r * math.cos(math.radians(360.0 * k / n)), y, r * math.sin(math.radians(360.0 * k / n))) for k in range(n)]


POSES = {
    "idle": {"body": (-3, 0, 0), "left_arm": (10, 0, 6), "right_arm": (6, 0, -6)},
    # 予告: 左手の弓を前へ、右手で弦を引く
    "telegraph": {"body": (0, 70, 0), "head": (0, -60, 0), "left_arm": (90, 0, -10), "left_forearm": (0, 0, 0),
                  "right_arm": (90, 0, 20), "right_forearm": (0, 90, 0), "leg_l": (-10, 0, 0), "leg_r": (12, 0, 0)},
}

SPEC = dict(id="bandit_archer", short="野盗の弓手", title="野盗の弓手 (bandit_archer) — 編み笠の射手",
            sub=["つばの広い編み笠と首巻き、草色の片掛けの外套、背の矢筒、短い反り弓。", "予告: 弓を引く (0.8 秒) → 射って後ろへ跳ぶ。"],
            rows=[("前 3/4", "idle", 210, 10, 520, (0, 16, 0), 13.0), ("横", "idle", 270, 6, 520, (0, 16, 0), 13.0),
                  ("後ろ 3/4", "idle", 30, 10, 520, (0, 16, 0), 13.0), ("予告: 弓を引く", "telegraph", 210, 8, 520, (0, 17, 0), 13.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
