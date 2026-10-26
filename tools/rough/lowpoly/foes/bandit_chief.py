"""野盗の頭目 (bandit_chief)。島の峠の「峠の頭目」と、野盗の野営の頭。Lv 3〜4、頭。
一体一案: 野盗より一回り大きく (1.2 倍) 肩幅が広い。拾い集めた継ぎはぎの鎧 (右肩だけ大きな岩殻の板、胸に打ち付けた鉄板)、
狼の毛皮の肩掛け、片目の眼帯、角の折れた古い兜。赤い腰帯 (頭目の印)。両手で持つ大きな手斧。
動き: 手斧を横に薙ぐ (予告 0.8 秒)。叫ぶと (予告 1 秒、斧を掲げる) 周りの野盗が速くなる。
"""
from fk import *  # noqa: F401,F403
from fk import HUMAN_BONES, box, human, run1, part, mat
import bandit as B

S = 1.2
BONES = {k: (p, [c[0] * 1.1, c[1] * S, c[2]]) for k, (p, c) in HUMAN_BONES.items()}
FUR = [(48, 40, 34), (80, 68, 56), (116, 100, 82), (150, 134, 110), (186, 170, 142)]
ROCK = [(48, 52, 60), (70, 76, 86), (96, 102, 112), (124, 130, 138)]
SASH = [(80, 20, 20), (130, 34, 30), (176, 54, 44), (210, 84, 66)]
PAL = {"mats": dict(B.PAL["mats"], fur=mat(FUR, 0.6, noise=0.3), rock=mat(ROCK, 0.6, crack=0.06), sash=mat(SASH, 0.6, noise=0.15)),
       "skull_bone": "head", "face_mat": "skin"}


def build(k):
    F = []
    human(F, skin="skin", shirt="cloth", pants="pants", boots="leather", s=S, bulk=1.18)
    F += part("head", ellipsoid("head", "skin", (0, 27.6 * S, 0), (3.2, 3.4, 3.3), segs=8, rings=3))
    # 古い兜 (片方の角が折れている) と眼帯
    F += part("helm", ellipsoid("head", "iron", (0, 29.0 * S, 0.2), (3.6, 2.8, 3.7), segs=8, rings=2))
    F += part("helm_rim", tube("head", "iron", [(-3.7, 28.2 * S, 0), (0, 28.0 * S, -3.8), (3.7, 28.2 * S, 0)], ra=[0.45] * 3, sides=4, hint=(0, 1, 0)))
    F += part("horn_l", spike("head", "rock", (3.0, 30.4 * S, 0.4), (0.8, 0.9, 0.2), 4.6, 0.9, sides=5, rings=2, bend=(0.2, 0.6, 0.6)))
    F += part("horn_r", spike("head", "rock", (-3.0, 30.4 * S, 0.4), (-0.8, 0.7, 0.2), 1.6, 0.9, sides=5, rings=1))
    F += part("patch", box("head", "dark", (1.2, 28.0 * S, -3.15), (1.6, 1.2, 0.4)))
    F += part("patch_strap", tube("head", "dark", [(-3.2, 28.6 * S, -1.0), (0, 28.4 * S, -3.3), (3.2, 27.8 * S, -1.0)], ra=[0.15] * 3, sides=3, hint=(0, 1, 0)))
    F += part("eye", box("head", "dark", (-1.2, 28.0 * S, -3.1), (1.1, 0.45, 0.4)))
    F += part("beard", ellipsoid("head", "fur", (0, 25.4 * S, -2.0), (2.4, 1.8, 1.4), segs=6, rings=2))
    # 胴: 打ち付けた鉄板、毛皮の肩掛け、赤い腰帯
    F += part("chest", box("body", "iron", (0, 19.0 * S, -2.6), (7.0, 6.4, 1.2), taper=(1.0, 1.0)))
    for x, y in ((-2.6, 21.4), (2.6, 21.4), (-2.6, 16.6), (2.6, 16.6)):
        F += part(f"rivet{x}{y}", box("body", "dark", (x, y * S, -3.3), (0.5, 0.5, 0.3)))
    F += part("mantle", ellipsoid("body", "fur", (0, 22.4 * S, 0.4), (6.4, 2.6, 4.0), segs=9, rings=2))
    F += part("sash", tube("body", "sash", [(-5.0, 14.0 * S, -1.0), (0, 13.6 * S, -3.3), (5.0, 14.0 * S, -1.0), (0, 14.4 * S, 3.3), (-5.0, 14.0 * S, -1.0)],
                                [0.8] * 5, sides=4, hint=(0, 1, 0)))
    F += part("sash_tail", tube("body", "sash", [(3.4, 13.6 * S, -2.4), (4.2, 10.0 * S, -2.6)], ra=[0.8, 0.4], sides=4, hint=(0, 0, 1)))
    # 右肩の大きな岩殻の板 (二段)
    F += part("pauldron", ellipsoid("right_arm", "rock", (-6.8, 22.8 * S, 0), (3.2, 2.0, 3.2), segs=7, rings=2))
    F += part("pauldron2", ellipsoid("right_arm", "rock", (-7.6, 21.0 * S, 0), (2.6, 1.4, 2.8), segs=6, rings=2))
    # 両手の大きな手斧 (右手の骨に)
    F += part("haft", tube("weapon", "wood", [(-6.6, 13.0 * S, -0.6), (-6.6, 1.0 * S, -0.6)], ra=[0.55, 0.55], sides=5, hint=(0, 0, 1)))
    F += part("axe", loft("weapon", "iron", [
        [V(-6.9, 3.6 * S, -1.2), V(-6.3, 3.6 * S, -1.2), V(-6.3, 7.4 * S, -1.2), V(-6.9, 7.4 * S, -1.2)],
        [V(-7.0, 1.6 * S, -6.0), V(-6.2, 1.6 * S, -6.0), V(-6.2, 9.4 * S, -6.0), V(-7.0, 9.4 * S, -6.0)]], caps=("iron", "iron")))
    F += part("axe_back", spike("weapon", "iron", (-6.6, 5.6 * S, 0.0), (0, 0, 1), 2.4, 0.8, sides=4, rings=1))
    return F, []


POSES = {
    "idle": {"body": (-4, 0, 0), "right_arm": (30, 0, -8), "right_forearm": (-40, 0, 0), "left_arm": (6, 0, 10)},
    # 予告: 斧を後ろへ大きく引く
    "telegraph": {"body": (4, 40, 0), "right_arm": (60, 60, -30), "right_forearm": (-30, 0, 0), "left_arm": (60, 30, 0), "leg_l": (-14, 0, 0), "leg_r": (16, 0, 0)},
    # 叫ぶ: 斧を掲げる
    "shout": {"head": (16, 0, 0), "right_arm": (170, 0, -10), "right_forearm": (-10, 0, 0), "left_arm": (20, 0, 30), "body": (6, 0, 0)},
}

SPEC = dict(id="bandit_chief", short="野盗の頭目", title="野盗の頭目 (bandit_chief) — 峠の頭目",
            sub=["一回り大きく、継ぎはぎの鎧・右肩の岩殻・毛皮の肩掛け・片目の眼帯・角の折れた兜・赤い腰帯。両手の大きな手斧。",
                 "予告: 斧を大きく引いて横に薙ぐ (0.8 秒)。斧を掲げて叫ぶと、まわりの野盗が速くなる。"],
            rows=[("前 3/4", "idle", 210, 10, 560, (0, 19, 0), 12.0), ("横", "idle", 270, 6, 560, (0, 19, 0), 12.0),
                  ("後ろ 3/4", "idle", 30, 10, 560, (0, 19, 0), 12.0), ("予告: 斧を引く", "telegraph", 210, 8, 560, (0, 19, 0), 12.0),
                  ("叫ぶ", "shout", 210, 8, 560, (0, 20, 0), 12.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
