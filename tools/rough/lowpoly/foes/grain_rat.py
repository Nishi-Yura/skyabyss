"""穂かじり鼠 (grain_rat)。島の草地と都のまわりの平原の群れの小物。Lv 1〜3。
一体一案: 麦畑を荒らす大きな野鼠 (体長 0.8 ブロック)。丸い胴に頬袋のふくらみ、大きな前歯、丸い耳、長い裸の尾。
口に麦の穂をくわえ、背の毛は金茶。すばしこく、群れで足もとに噛みつく。
動き: 走り寄って跳びかかり噛む (予告 0.3 秒、身を縮める)。噛んだらすぐ離れる。
"""
from fk import *  # noqa: F401,F403
from fk import box, eye, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 4.6, 0)), "head": ("body", (0, 5.2, -5.4)),
         "ear_l": ("head", (1.6, 7.4, -5.6)), "ear_r": ("head", (-1.6, 7.4, -5.6)),
         "fore_l": ("body", (1.8, 3.6, -3.4)), "fore_r": ("body", (-1.8, 3.6, -3.4)),
         "hind_l": ("body", (2.2, 3.8, 2.8)), "hind_r": ("body", (-2.2, 3.8, 2.8)), "tail": ("body", (0, 4.6, 5.6))}
FUR = [(56, 40, 26), (92, 68, 42), (132, 100, 60), (170, 136, 84), (204, 172, 114)]
BELLY = [(120, 104, 84), (160, 142, 116), (196, 180, 152)]
PINK = [(120, 70, 70), (164, 104, 100), (200, 140, 132)]
WHEAT = [(150, 118, 56), (196, 160, 82), (230, 202, 120)]
PAL = {"mats": {"fur": mat(FUR, 0.6, noise=0.3), "belly": mat(BELLY, 0.62), "pink": mat(PINK, 0.62), "wheat": mat(WHEAT, 0.62),
                "tooth": mat([(200, 190, 150), (236, 226, 190)], 0.7), "dark": mat([(8, 8, 12), (18, 16, 20), (30, 28, 32)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "fur"}


def build(k):
    F = []
    F += part("body", ellipsoid("body", "fur", (0, 4.4, 0.4), (3.4, 3.0, 5.0), segs=9, rings=4))
    F += part("belly", ellipsoid("body", "belly", (0, 3.4, 0.0), (2.6, 2.0, 3.8), segs=7, rings=2))
    F += part("head", ellipsoid("head", "fur", (0, 5.4, -5.6), (2.4, 2.2, 2.6), segs=8, rings=3))
    for sx in (1, -1):
        F += part(f"cheek{sx}", ellipsoid("head", "fur", (sx * 1.9, 4.6, -6.0), (1.3, 1.2, 1.3), segs=6, rings=2))
        F += part(f"ear{sx}", ellipsoid("ear_l" if sx > 0 else "ear_r", "pink", (sx * 1.7, 7.6, -5.4), (1.2, 1.3, 0.4), segs=7, rings=2))
        F += part(f"eye{sx}", box("head", "dark", (sx * 1.3, 6.2, -7.5), (0.7, 0.7, 0.4)))
        F += part(f"fore{sx}", tube("fore_l" if sx > 0 else "fore_r", "pink", [(sx * 1.8, 3.6, -3.4), (sx * 1.9, 0.4, -4.0)], ra=[0.6, 0.45], sides=4, hint=(1, 0, 0)))
        F += part(f"thigh{sx}", ellipsoid("hind_l" if sx > 0 else "hind_r", "fur", (sx * 2.4, 3.6, 2.6), (1.6, 2.0, 2.2), segs=6, rings=2))
        F += part(f"hindf{sx}", tube("hind_l" if sx > 0 else "hind_r", "pink", [(sx * 2.4, 1.8, 2.8), (sx * 2.5, 0.3, 1.4)], ra=[0.55, 0.45], sides=4, hint=(1, 0, 0)))
        for k2 in range(3):
            F += part(f"whisk{sx}{k2}", tube("head", "belly", [(sx * 1.2, 5.0 - 0.3 * k2, -7.6), (sx * 3.6, 5.4 - 0.6 * k2, -8.0)], ra=[0.06, 0.04], sides=3, hint=(0, 1, 0)))
    F += part("nose", ellipsoid("head", "pink", (0, 5.6, -8.0), (0.7, 0.6, 0.6), segs=5, rings=1))
    F += part("teeth", box("head", "tooth", (0, 4.2, -7.7), (0.9, 1.0, 0.4)))
    F += part("tail", tube("tail", "pink", [(0, 4.6, 5.2), (0, 3.8, 8.6), (0.8, 2.6, 11.6), (1.8, 2.0, 13.8)], ra=[0.6, 0.45, 0.3, 0.1], sides=5, hint=(1, 0, 0)))
    # くわえた麦の穂 (口の横へ突き出る)
    F += part("stalk", tube("head", "wheat", [(-2.8, 4.6, -7.4), (2.6, 4.4, -7.6)], ra=[0.12, 0.12], sides=3, hint=(0, 1, 0)))
    F += part("ear_of_wheat", ellipsoid("head", "wheat", (3.6, 4.6, -7.6), (1.4, 0.6, 0.6), segs=6, rings=2))
    return F, []


POSES = {"idle": {}, "telegraph": {"body": (-10, 0, 0), "head": (-12, 0, 0), "hind_l": (-30, 0, 0), "hind_r": (-30, 0, 0), "fore_l": (20, 0, 0), "fore_r": (20, 0, 0)}}

SPEC = dict(id="grain_rat", short="穂かじり鼠", title="穂かじり鼠 (grain_rat) — 麦畑の大きな野鼠",
            sub=["丸い胴と頬袋、大きな前歯、丸い耳、長い裸の尾。口に麦の穂をくわえる。群れで走り寄って足もとを噛む。",
                 "予告: 身を縮める (0.3 秒) → 跳びかかって噛み、すぐ離れる。"],
            rows=[("前 3/4", "idle", 210, 14, 360, (0, 5, -1), 26.0), ("横", "idle", 270, 6, 360, (0, 5, -1), 26.0),
                  ("後ろ 3/4", "idle", 30, 14, 360, (0, 5, -1), 26.0), ("予告: 身を縮める", "telegraph", 210, 10, 360, (0, 5, -1), 26.0)], atlas_h=200)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
