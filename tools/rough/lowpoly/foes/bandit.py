"""野盗 (bandit)。島の峠と、見どころの野盗の野営の荒くれ。Lv 1〜3、近接。
一体一案: 先の垂れた継ぎはぎの頭巾と、口を覆う布 (目だけ出す)。革の胴着に縄の帯、巻いた前腕、裾の破れた脚衣。
右手に刃こぼれした鉈。左の腰に小さな袋と水筒。背は少し丸く、前のめりの構え。
動き: 鉈を振りかぶって斜めに斬り下ろす (予告 0.6 秒)。体力が半分を切ると、身を低くして後ずさる。
"""
import math

from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, human, eye, run1, part, mat

BONES = dict(HUMAN_BONES)
SKIN = [(70, 46, 34), (112, 76, 56), (150, 106, 80), (186, 140, 108)]
CLOTH = [(40, 30, 26), (64, 48, 38), (92, 70, 52), (120, 94, 70), (146, 118, 88)]
HOOD = [(48, 36, 30), (78, 58, 44), (110, 82, 60), (140, 108, 80)]
MASK = [(70, 34, 30), (110, 52, 42), (150, 74, 58), (180, 100, 80)]
LEATHER = [(34, 24, 20), (58, 40, 30), (86, 60, 42), (112, 82, 58)]
PANTS = [(34, 34, 38), (52, 52, 58), (74, 74, 80), (96, 96, 102)]
IRON = [(40, 40, 46), (70, 70, 78), (104, 104, 112), (140, 140, 148), (180, 180, 186)]
ROPE = [(90, 74, 50), (126, 106, 74), (160, 138, 98)]
WOOD = [(46, 32, 22), (72, 50, 34), (98, 70, 46)]
PAL = {"mats": {
    "skin": mat(SKIN, 0.62, noise=0.1), "cloth": mat(CLOTH, 0.6, noise=0.2), "hood": mat(HOOD, 0.6, noise=0.2), "mask": mat(MASK, 0.6, noise=0.15),
    "leather": mat(LEATHER, 0.6, noise=0.15), "pants": mat(PANTS, 0.58, noise=0.15), "iron": mat(IRON, 0.62, crack=0.04),
    "rope": mat(ROPE, 0.62), "wood": mat(WOOD, 0.6), "dark": mat([(8, 8, 12), (18, 16, 20), (30, 28, 32)], 0.5, min=0),
}, "skull_bone": "head", "face_mat": "skin"}


def build(k):
    F = []
    human(F, skin="skin", shirt="cloth", pants="pants", boots="leather")
    # 頭: 顔、口を覆う布、先が垂れた頭巾 (後ろへ流れる)
    F += part("head", ellipsoid("head", "skin", (0, 27.6, 0), (3.1, 3.4, 3.2), segs=8, rings=3))
    F += part("mask", ellipsoid("head", "mask", (0, 26.0, -0.4), (3.35, 1.8, 3.3), segs=8, rings=2))
    F += part("hood", ellipsoid("head", "hood", (0, 28.4, 0.5), (3.7, 3.6, 3.7), segs=8, rings=3))
    F += part("hood_tail", tube("head", "hood", [(0, 30.6, 2.6), (0, 29.6, 5.4), (0.4, 26.6, 7.2), (0.8, 24.0, 7.6)], ra=[1.8, 1.4, 0.9, 0.3], sides=5, hint=(1, 0, 0)))
    F += part("hood_brim", tube("head", "hood", [(-3.2, 28.4, -2.8), (0, 30.4, -3.6), (3.2, 28.4, -2.8)], ra=[0.6, 0.7, 0.6], sides=4, hint=(0, 0, 1)))
    # 胴着と縄の帯、斜めの革の帯
    F += part("vest", ellipsoid("body", "leather", (0, 17.6, 0), (4.75, 5.0, 2.95), segs=8, rings=3))
    F += part("vest_open", box("body", "cloth", (0, 18.0, -2.85), (1.6, 7.6, 0.5)))
    for sx in (1, -1):
        F += part(f"shoulder{sx}", ellipsoid("body", "leather", (sx * 4.2, 21.6, 0), (1.9, 1.3, 2.1), segs=6, rings=2))
    F += part("belt", tube("body", "rope", [(-4.4, 13.8, -0.8), (0, 13.6, -2.9), (4.4, 13.8, -0.8), (0, 14.0, 2.9), (-4.4, 13.8, -0.8)], ra=[0.5] * 5, sides=4, hint=(0, 1, 0)))
    F += part("sash", tube("body", "leather", [(4.0, 22.0, -2.6), (0, 18.4, -3.2), (-4.0, 14.6, -2.6)], ra=[0.7, 0.7, 0.7], sides=4, hint=(0, 0, 1)))
    F += part("pouch", box("body", "leather", (3.6, 12.6, -2.0), (2.0, 2.2, 1.6)))
    F += part("flask", ellipsoid("body", "wood", (4.4, 12.0, 0.8), (1.0, 1.6, 1.0), segs=6, rings=2))
    # 巻いた前腕 (布の帯)
    for sx, fore in ((1, "left_forearm"), (-1, "right_forearm")):
        for y in (15.0, 13.4):
            F += part(f"{fore}_wrap{y}", tube(fore, "cloth", [(sx * 5.9, y, -0.2), (sx * 5.95, y - 0.9, -0.3)], ra=[1.35, 1.3], sides=6, hint=(0, 0, 1)))
    # 脚衣の破れた裾
    for sx, leg in ((1, "leg_l"), (-1, "leg_r")):
        F += part(leg + "_rag", box(leg, "pants", (sx * 2.2, 4.4, -0.2), (3.2, 1.2, 3.2), taper=(1.1, 1.1)))
    # 鉈 (刃こぼれの刻み、木の柄)
    F += part("handle", tube("weapon", "wood", [(-6.0, 11.4, -0.5), (-6.0, 7.8, -0.5)], ra=[0.45, 0.45], sides=5, hint=(0, 0, 1)))
    blade = [(-6.0, 7.8, -0.5), (-6.0, 1.0, -1.2)]
    F += part("cleaver", box("weapon", "iron", (-6.0, 4.4, -1.4), (0.35, 6.8, 2.8), taper=(1.0, 1.1)))
    for y in (6.0, 3.6):
        F += part(f"nick{y}", box("weapon", "dark", (-6.0, y, -2.85), (0.45, 0.6, 0.3)))
    for sx in (1, -1):   # 人の目 (暗い切れ込み。光らない)
        F += part(f"eye{sx}", box("head", "dark", (sx * 1.2, 27.8, -3.05), (1.2, 0.5, 0.4)))
    return F, []


POSES = {
    "idle": {"body": (-6, 0, 0), "head": (4, 0, 0), "right_arm": (20, 0, -6), "right_forearm": (-30, 0, 0), "left_arm": (8, 0, 8)},
    # 予告: 鉈を頭の後ろへ振りかぶる
    "telegraph": {"body": (6, 10, 0), "right_arm": (165, -20, 0), "right_forearm": (-40, 0, 0), "left_arm": (-30, 0, 20), "leg_r": (-18, 0, 0), "leg_l": (14, 0, 0)},
    # 斬り下ろし
    "attack": {"body": (-18, -16, 0), "right_arm": (-30, 30, 0), "right_forearm": (-10, 0, 0), "leg_r": (24, 0, 0), "leg_l": (-16, 0, 0)},
}

SPEC = dict(id="bandit", short="野盗", title="野盗 (bandit) — 島の峠と野盗の野営の荒くれ",
            sub=["先の垂れた頭巾と口を覆う布、革の胴着と縄の帯、刃こぼれした鉈。前のめりの構え。",
                 "予告: 鉈を頭の後ろへ振りかぶる (0.6 秒) → 斜めに斬り下ろす。左の黄の目盛りは人 (1.8 ブロック)。"],
            rows=[("前 3/4", "idle", 210, 10, 520, (0, 16, 0), 15.0), ("横", "idle", 270, 6, 520, (0, 16, 0), 15.0),
                  ("後ろ 3/4", "idle", 30, 10, 520, (0, 16, 0), 15.0), ("予告: 振りかぶる", "telegraph", 210, 8, 520, (0, 17, 0), 15.0),
                  ("斬り下ろし", "attack", 210, 8, 520, (0, 16, 0), 15.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
