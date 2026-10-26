"""野犬 (feral_dog)。都のまわりの草原の森に群れで出る。Lv 2〜4、群れ。
一体一案: 村を捨てられて野に戻った痩せ犬。あばらの浮いた細い胴と腰の吊り上がり、長い脚、低く下げた尾。
首に擦り切れた古い縄の首輪 (昔は誰かの犬だった)。逆立った背の毛 (首から肩へ黒い鬣のような毛の束)、片耳は破れて短い。
灰茶の毛に黒い背筋、白っぽい腹。鼻面に古い傷。牙をむいた口。
動き: 群れで囲み、一頭が天を仰いで吠える (予告 1.0 秒) と、まわりの野犬が一斉に跳びかかる。
"""
import math

from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 10.0, 0)), "head": ("body", (0, 13.0, -6.6)), "jaw": ("head", (0, 12.6, -10.4)),
         "ear_l": ("head", (1.6, 15.8, -9.0)), "ear_r": ("head", (-1.6, 15.8, -9.0)),
         "fore_l": ("body", (2.2, 9.6, -4.8)), "fore_r": ("body", (-2.2, 9.6, -4.8)),
         "hind_l": ("body", (2.3, 10.0, 5.2)), "hind_r": ("body", (-2.3, 10.0, 5.2)), "tail": ("body", (0, 11.6, 7.4))}
FUR = [(46, 40, 34), (78, 68, 56), (112, 98, 80), (146, 128, 104), (176, 158, 130)]
DARK = [(18, 16, 16), (34, 30, 28), (54, 48, 42), (74, 66, 58)]
BELLY = [(120, 110, 94), (156, 144, 124), (190, 178, 156)]
ROPE = [(82, 62, 38), (122, 96, 60), (160, 130, 84), (190, 162, 110)]
PINK = [(96, 46, 46), (140, 74, 70), (176, 108, 100)]
PAL = {"mats": {"fur": mat(FUR, 0.6, noise=0.18), "shade": mat(FUR[:3], 0.45, noise=0.1), "dark": mat(DARK, 0.55, noise=0.25), "belly": mat(BELLY, 0.62, noise=0.2), "rope": mat(ROPE, 0.6, noise=0.3),
                "pink": mat(PINK, 0.62), "tooth": mat([(196, 186, 150), (232, 222, 186)], 0.7), "eye": mat([(150, 110, 30), (210, 168, 60), (236, 204, 100)], 0.7),
                "nose": mat([(10, 10, 12), (24, 22, 24), (40, 36, 36)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "fur"}


def build(k):
    F = []
    # 胴: 深い胸と吊り上がった細い腰、腰骨
    F += part("chest", ellipsoid("body", "fur", (0, 10.8, -3.0), (3.2, 3.9, 4.4), segs=9, rings=4))
    F += part("waist", ellipsoid("body", "fur", (0, 11.4, 2.4), (2.3, 2.6, 4.2), segs=8, rings=3))
    F += part("haunch", ellipsoid("body", "fur", (0, 10.8, 5.2), (3.0, 3.2, 2.9), segs=9, rings=4))
    F += part("belly", ellipsoid("body", "belly", (0, 8.6, -3.2), (2.4, 2.0, 3.4), segs=7, rings=2))
    # 浮いたあばら (胸の横の暗い筋)
    for sx in (1, -1):
        for i, z in enumerate((-3.6, -2.2, -0.8)):
            F += part(f"rib{sx}{i}", box("body", "shade", (sx * 2.85, 10.2, z), (0.25, 2.6 - i * 0.4, 0.4)))
    # 背筋の黒い毛と、首から肩へ逆立った毛の束
    F += part("spine_stripe", tube("body", "dark", [(0, 14.4, -3.6), (0, 13.6, 1.0), (0, 13.4, 5.8)], ra=[1.3, 0.9, 1.1], rb=[0.5, 0.4, 0.5], sides=5, hint=(1, 0, 0)))
    for i, (z, h) in enumerate(((-6.6, 1.3), (-5.6, 1.6), (-4.6, 1.5), (-3.6, 1.3), (-2.6, 1.0), (-1.6, 0.7))):
        for sx in (0.6, -0.6):
            F += part(f"hackle{i}{sx}", tube("body", "dark", [(sx, 14.0 - i * 0.1, z), (sx * 1.5, 14.0 + h, z + 1.8)], ra=[0.65, 0.05], sides=3, hint=(1, 0, 0)))
    # 首と擦り切れた縄の首輪 (垂れた端)
    F += part("neck", tube("head", "fur", [(0, 12.4, -5.2), (0, 13.4, -7.4), (0, 14.0, -8.6)], ra=[2.6, 2.3, 2.1], sides=7, hint=(1, 0, 0)))
    ring = [(2.5 * math.cos(math.radians(a)), 13.2 + 2.5 * math.sin(math.radians(a)) * 0.95, -7.0 - 0.35 * math.sin(math.radians(a))) for a in range(-90, 271, 45)]
    F += part("collar", tube("head", "rope", ring, ra=[0.45] * len(ring), sides=4, hint=(0, 0, 1)))
    F += part("collar_end", tube("head", "rope", [(0.6, 10.8, -7.4), (1.0, 9.4, -7.8), (0.7, 8.4, -7.6)], ra=[0.4, 0.35, 0.15], sides=4, hint=(1, 0, 0)))
    # 頭: 細長い頭骨と鼻面、黒い鼻、牙、黄色い目、鼻面の古い傷
    F += part("skull", ellipsoid("head", "fur", (0, 14.6, -9.6), (2.5, 2.3, 2.7), segs=8, rings=3))
    F += part("muzzle", ellipsoid("head", "fur", (0, 13.6, -12.6), (1.35, 1.25, 2.5), segs=7, rings=2))
    F += part("brow", ellipsoid("head", "dark", (0, 15.9, -9.8), (2.0, 0.7, 1.8), segs=6, rings=2))
    F += part("nose", ellipsoid("head", "nose", (0, 14.1, -14.9), (0.65, 0.5, 0.45), segs=5, rings=1))
    F += part("lip", box("head", "pink", (0, 12.75, -12.8), (2.0, 0.4, 3.2), taper=(0.8, 0.9)))
    for sx in (1, -1):
        F += part(f"fang{sx}", box("head", "tooth", (sx * 0.75, 12.4, -14.0), (0.3, 0.9, 0.3), taper=(0.2, 0.2)))
        F += part(f"eye{sx}", box("head", "eye", (sx * 1.25, 15.0, -11.6), (0.6, 0.45, 0.3)))
        F += part(f"pupil{sx}", box("head", "nose", (sx * 1.25, 15.0, -11.78), (0.22, 0.4, 0.1)))
    F += part("scar", box("head", "pink", (0.6, 14.4, -13.0), (0.25, 0.25, 2.2)))
    F += part("jaw", ellipsoid("jaw", "fur", (0, 12.1, -12.4), (1.15, 0.6, 2.3), segs=6, rings=2))
    F += part("jaw_teeth", box("jaw", "tooth", (0, 12.55, -13.6), (1.5, 0.3, 0.9)))
    # 耳: 左は立ち耳、右は破れて短い
    F += part("ear_l", box("ear_l", "dark", (1.7, 17.0, -9.0), (1.5, 2.8, 0.5), taper=(0.15, 1.0)))
    F += part("ear_r", box("ear_r", "dark", (-1.6, 16.4, -9.0), (1.5, 1.6, 0.5), taper=(0.55, 1.0)))
    # 脚: 細長い前脚、くの字の後ろ脚、黒い足先
    for sx, fb, hb in ((1, "fore_l", "hind_l"), (-1, "fore_r", "hind_r")):
        F += part(f"foreup{sx}", tube(fb, "fur", [(sx * 2.2, 10.0, -4.6), (sx * 2.3, 5.2, -5.0)], ra=[1.35, 0.8], sides=6, hint=(1, 0, 0)))
        F += part(f"forelo{sx}", tube(fb, "fur", [(sx * 2.3, 5.4, -5.0), (sx * 2.2, 1.0, -4.8)], ra=[0.75, 0.6], sides=5, hint=(1, 0, 0)))
        F += part(f"forepaw{sx}", ellipsoid(fb, "dark", (sx * 2.2, 0.55, -5.4), (0.8, 0.55, 1.1), segs=6, rings=2))
        F += part(f"thigh{sx}", ellipsoid(hb, "fur", (sx * 2.1, 9.6, 5.2), (1.2, 2.4, 2.0), segs=8, rings=3))
        F += part(f"hindlo{sx}", tube(hb, "fur", [(sx * 2.5, 7.4, 6.2), (sx * 2.5, 3.8, 7.4), (sx * 2.4, 1.0, 6.4)], ra=[0.9, 0.6, 0.55], sides=5, hint=(1, 0, 0)))
        F += part(f"hindpaw{sx}", ellipsoid(hb, "dark", (sx * 2.4, 0.55, 5.8), (0.8, 0.55, 1.1), segs=6, rings=2))
    # 低く下げた尾 (先が黒い)
    F += part("tail", tube("tail", "fur", [(0, 11.8, 7.2), (0, 10.4, 9.6), (0, 7.8, 11.0)], ra=[1.0, 0.85, 0.6], sides=6, hint=(1, 0, 0)))
    F += part("tail_tip", tube("tail", "dark", [(0, 8.0, 10.9), (0, 6.0, 11.4)], ra=[0.65, 0.1], sides=5, hint=(1, 0, 0)))
    return F, []


POSES = {
    "idle": {"head": (-8, 0, 0), "tail": (-6, 0, 0)},
    # 吠える (群れへの合図): 前足を突っ張り、天を仰いで口を開ける
    "howl": {"body": (12, 0, 0), "head": (38, 0, 0), "jaw": (-26, 0, 0), "fore_l": (-12, 0, 0), "fore_r": (-12, 0, 0), "hind_l": (-14, 0, 0), "hind_r": (-14, 0, 0),
             "tail": (10, 0, 0), "ear_l": (-20, 0, 0), "ear_r": (-20, 0, 0)},
    # 跳びかかりの溜め: 身を沈め、頭を下げて牙をむく
    "telegraph": {"body": (-6, 0, 0), "head": (-10, 0, 0), "jaw": (-18, 0, 0), "fore_l": (22, 0, 0), "fore_r": (22, 0, 0), "hind_l": (-30, 0, 0), "hind_r": (-30, 0, 0),
                  "tail": (-14, 0, 0), "ear_l": (-30, 0, 0), "ear_r": (-30, 0, 0)},
}

SPEC = dict(id="feral_dog", short="野犬", title="野犬 (feral_dog) — 縄の首輪の残る痩せ犬",
            sub=["あばらの浮いた細い胴と長い脚、逆立った背の黒い毛、擦り切れた縄の首輪、破れた片耳、鼻面の古い傷、低く下げた尾。",
                 "群れで囲み、一頭が天を仰いで吠える (予告 1.0 秒) と、まわりの野犬が一斉に跳びかかる。"],
            rows=[("前 3/4", "idle", 210, 12, 420, (0, 10, -1), 18.0), ("横", "idle", 270, 6, 420, (0, 10, -1), 18.0),
                  ("後ろ 3/4", "idle", 30, 12, 420, (0, 10, -1), 18.0), ("予告: 吠える", "howl", 230, 8, 420, (0, 11, -1), 18.0),
                  ("跳びかかりの溜め", "telegraph", 210, 8, 420, (0, 10, -1), 18.0)], atlas_h=220)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
