"""骸の弓兵 (bone_bowman)。草原の夜と亡者の野営の射手。Lv 2〜4、遠距離。
一体一案: 面頬の割れた兜 (片側の面が落ちて頭蓋がのぞく) とぼろの頭巾の肩掛け。胸当ては無く、肋に巻いた古い旗の布 (都の色の抜けた紺)。
左手に背丈ほどの長弓、背に矢羽の代わりに旗の布切れを結んだ矢の束。目の穴に琥珀の灯。
動き: 長弓を引いて (予告 0.8 秒) 弧を描く矢を射る。寄られると弓で殴って下がる。
"""
from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, skeleton, skull, run1, part, mat
import bone_footman as BF

BONES = dict(HUMAN_BONES)
PAL = {"mats": dict(BF.PAL["mats"]), "skull_bone": "head", "face_mat": "bone"}


def build(k):
    F = []
    skeleton(F, "bone")
    skull(F, "bone")
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_amber", (sx * 1.15, 27.2, -3.2), (0.6, 0.6, 0.3)))
    # 面頬の割れた兜: 頭頂の鉢と、右の頬当てだけ残る
    F += part("helm", ellipsoid("head", "rust", (0, 29.2, 0.2), (3.5, 2.5, 3.6), segs=8, rings=2))
    F += part("cheek", box("head", "rust", (-2.9, 26.6, -1.4), (1.0, 3.6, 3.6), taper=(0.9, 0.8)))
    F += part("crack", box("head", "dark", (1.4, 29.6, -3.3), (0.4, 1.6, 0.3)))
    # ぼろの頭巾の肩掛け (背へ垂れる)
    F += part("cowl", ellipsoid("body", "tabard", (0, 22.4, 0.6), (5.2, 2.0, 3.6), segs=9, rings=2))
    F += part("cape", box("body", "tabard", (0, 17.2, 2.6), (7.0, 9.0, 0.5), taper=(0.85, 1.0)))
    for x in (-2.0, 0.6, 2.4):
        F += part(f"cape_tear{x}", box("body", "dark", (x, 13.0, 2.9), (0.8, 1.4, 0.3)))
    # 肋に巻いた旗の布 (斜め)
    F += part("wrap", tube("body", "tabard", [(-3.8, 21.0, -1.6), (0, 18.6, -2.5), (3.8, 16.2, -1.6), (0, 15.6, 1.4), (-3.8, 21.0, -1.6)], ra=[0.6] * 5, sides=4, hint=(0, 1, 0)))
    # 長弓 (左手。背丈ほど)
    F += part("bow", tube("offhand", "wood", [(6.2, 25.0, -1.0), (6.8, 18.0, -2.2), (6.2, 10.6, -2.0), (6.8, 3.0, -2.2), (6.2, -3.0, -1.0)],
                         ra=[0.25, 0.45, 0.55, 0.45, 0.25], sides=4, hint=(0, 0, 1)))
    F += part("string", tube("offhand", "dark", [(6.2, 24.8, -0.8), (6.2, -2.8, -0.8)], ra=[0.08, 0.08], sides=3, hint=(0, 0, 1)))
    # 背の矢の束と旗の布切れ
    F += part("bundle", tube("body", "rust", [(-1.2, 14.6, 3.2), (1.2, 22.4, 3.4)], ra=[0.9, 0.9], sides=6, hint=(1, 0, 0)))
    for i, dx in enumerate((-0.4, 0.5)):
        F += part(f"arrow{i}", tube("body", "wood", [(1.0 + dx, 22.0, 3.4), (1.4 + dx, 25.2, 3.5)], ra=[0.15, 0.15], sides=3, hint=(1, 0, 0)))
        F += part(f"rag{i}", box("body", "tabard", (1.6 + dx, 25.0, 3.6), (0.9, 1.8, 0.2), rot=None))
    return F, []


POSES = {
    "idle": {"body": (-3, 0, 0), "head": (8, 0, 0), "left_arm": (14, 0, 6), "right_arm": (8, 0, -6)},
    "telegraph": {"body": (0, 70, 0), "head": (0, -60, 0), "left_arm": (90, 0, -10), "right_arm": (90, 0, 20), "right_forearm": (0, 90, 0),
                  "leg_l": (-10, 0, 0), "leg_r": (12, 0, 0)},
}

SPEC = dict(id="bone_bowman", short="骸の弓兵", title="骸の弓兵 (bone_bowman) — 長弓の骸",
            sub=["面頬の割れた兜、ぼろの頭巾の肩掛け、肋に巻いた古い旗の布、背丈ほどの長弓、旗の布を結んだ矢の束。",
                 "長弓を引いて (予告 0.8 秒) 弧を描く矢。寄られると弓で殴って下がる。"],
            rows=[("前 3/4", "idle", 210, 10, 520, (0, 16, 0), 13.0), ("横", "idle", 270, 6, 520, (0, 16, 0), 13.0),
                  ("後ろ 3/4", "idle", 30, 10, 520, (0, 16, 0), 13.0), ("予告: 長弓を引く", "telegraph", 210, 8, 520, (0, 16, 0), 13.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
