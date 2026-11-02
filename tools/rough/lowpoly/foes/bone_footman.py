"""骸の歩兵 (bone_footman)。草原の夜と、見どころの亡者の野営。Lv 2〜4、盾の近接。
一体一案: 昔の戦で倒れた都の兵の骸。錆びた鍋形の兜と胸当て、色の抜けた都の紋の陣羽織 (裾が破れる)、
左手に丸い木の盾 (鉄の縁、薄れた鐘の紋)、右手に欠けた短い剣。目の穴に琥珀の小さな灯。
動き: 盾を前に構えてにじり寄り (前からの攻撃を受け止める)、短く突く (予告 0.5 秒)。横と後ろが弱い。
"""
from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, skeleton, skull, run1, part, mat

BONES = dict(HUMAN_BONES)
BONEC = [(70, 64, 56), (116, 106, 90), (160, 148, 124), (196, 186, 160), (226, 218, 194)]
RUST = [(50, 32, 26), (86, 52, 34), (120, 76, 46), (150, 104, 64), (176, 132, 88)]
TABARD = [(42, 46, 62), (62, 68, 88), (86, 92, 112), (112, 118, 134)]
WOOD = [(46, 32, 22), (72, 50, 34), (98, 70, 46), (124, 92, 62)]
GOLD = [(96, 76, 30), (140, 112, 46), (184, 152, 70)]
PAL = {"mats": {"bone": mat(BONEC, 0.66, crack=0.06), "rust": mat(RUST, 0.58, crack=0.05, noise=0.25), "tabard": mat(TABARD, 0.56, noise=0.25),
                "wood": mat(WOOD, 0.6), "gold": mat(GOLD, 0.62), "dark": mat([(8, 8, 12), (18, 16, 20), (30, 28, 32)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "bone"}


def build(k):
    F = []
    skeleton(F, "bone")
    skull(F, "bone")
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_amber", (sx * 1.15, 27.2, -3.2), (0.6, 0.6, 0.3)))
    # 鍋形の兜 (縁の広い鉄の帽子)
    F += part("helm", ellipsoid("head", "rust", (0, 29.0, 0), (3.5, 2.6, 3.6), segs=8, rings=2))
    F += part("helm_brim", tube("head", "rust", [(-4.6, 28.0, 0), (0, 28.0, -4.6), (4.6, 28.0, 0), (0, 28.0, 4.6), (-4.6, 28.0, 0)], ra=[0.5] * 5, rb=[0.25] * 5, sides=4, hint=(0, 1, 0)))
    # 錆びた胸当てと陣羽織 (裾の破れ)
    F += part("cuirass", ellipsoid("body", "rust", (0, 18.8, -0.4), (4.4, 4.2, 2.8), segs=8, rings=3))
    F += part("tabard", box("body", "tabard", (0, 12.6, -2.4), (4.6, 6.0, 0.5), taper=(1.1, 1.0)))
    F += part("tabard_b", box("body", "tabard", (0, 12.6, 2.4), (4.6, 6.0, 0.5), taper=(1.1, 1.0)))
    for x in (-1.5, 0.6):
        F += part(f"tear{x}", box("body", "dark", (x, 9.8, -2.7), (0.6, 1.2, 0.3)))
    F += part("crest", box("body", "gold", (0, 13.4, -2.75), (1.4, 1.6, 0.3)))
    F += part("pauldron_l", ellipsoid("left_arm", "rust", (5.4, 22.4, 0), (2.0, 1.2, 2.0), segs=6, rings=2))
    F += part("pauldron_r", ellipsoid("right_arm", "rust", (-5.4, 22.4, 0), (2.0, 1.2, 2.0), segs=6, rings=2))
    # 丸い木の盾 (左手。正面 -Z を向く)
    F += part("shield", loft("offhand", "wood", [
        [V(6.6 + 4.2 * __import__('math').cos(__import__('math').radians(a)), 11.0 + 4.2 * __import__('math').sin(__import__('math').radians(a)), -1.6) for a in range(0, 360, 36)],
        [V(6.6 + 4.2 * __import__('math').cos(__import__('math').radians(a)), 11.0 + 4.2 * __import__('math').sin(__import__('math').radians(a)), -2.4) for a in range(0, 360, 36)]],
        caps=("wood", "wood")))
    F += part("shield_rim", tube("offhand", "rust", [(6.6 + 4.3 * __import__('math').cos(__import__('math').radians(a)), 11.0 + 4.3 * __import__('math').sin(__import__('math').radians(a)), -2.0)
                                                        for a in range(0, 361, 36)], ra=[0.35] * 11, sides=4, hint=(0, 0, 1)))
    F += part("shield_boss", ellipsoid("offhand", "rust", (6.6, 11.0, -2.6), (1.1, 1.1, 0.6), segs=6, rings=1))
    F += part("shield_bell", box("offhand", "gold", (6.6, 13.2, -2.5), (1.0, 1.4, 0.2), taper=(0.6, 1.0)))
    # 欠けた短い剣 (右手)
    F += part("hilt", tube("weapon", "rust", [(-6.0, 11.4, -0.5), (-6.0, 9.4, -0.5)], ra=[0.35, 0.35], sides=4, hint=(0, 0, 1)))
    F += part("guard", box("weapon", "rust", (-6.0, 9.2, -0.5), (0.5, 0.4, 2.6)))
    F += part("blade", box("weapon", "rust", (-6.0, 5.6, -0.5), (0.3, 6.8, 1.2), taper=(1.0, 0.6)))
    return F, []


POSES = {
    "idle": {"body": (-4, 0, 0), "head": (6, 0, 0), "left_arm": (40, 30, 0), "left_forearm": (-20, 0, 0), "right_arm": (20, 0, -6), "right_forearm": (-20, 0, 0)},
    "telegraph": {"body": (-10, 0, 0), "left_arm": (60, 20, 0), "left_forearm": (-30, 0, 0), "right_arm": (-30, 0, -10), "right_forearm": (-40, 0, 0),
                  "leg_l": (-16, 0, 0), "leg_r": (18, 0, 0)},
}

SPEC = dict(id="bone_footman", short="骸の歩兵", title="骸の歩兵 (bone_footman) — 盾を構える昔の兵",
            sub=["錆びた鍋形の兜と胸当て、色の抜けた陣羽織、丸い木の盾 (薄れた鐘の紋)、欠けた短い剣。目の穴に琥珀の灯。",
                 "盾を前に構えてにじり寄り、短く突く (予告 0.5 秒)。前からの攻撃は盾が受ける。"],
            rows=[("前 3/4", "idle", 210, 10, 520, (0, 16, 0), 13.0), ("横", "idle", 270, 6, 520, (0, 16, 0), 13.0),
                  ("後ろ 3/4", "idle", 30, 10, 520, (0, 16, 0), 13.0), ("予告: 突きの溜め", "telegraph", 210, 8, 520, (0, 16, 0), 13.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
