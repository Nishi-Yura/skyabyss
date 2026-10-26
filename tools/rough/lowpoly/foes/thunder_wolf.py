"""雷狼 (thunder_wolf)。嵐の高地の夜に群れで出る。Lv 19〜23、群れ。
一体一案: 帯電した毛の大きな狼。野犬と違い、前が重い: 盛り上がった肩と太い前脚、低く構えた頭、細く締まった腰。
首まわりの毛は静電気で放射状に逆立ち (襟巻きのような毛の輪)、背筋には青白く光る雷の節 (帯電した毛の房) が並ぶ。
嵐雲の灰青の毛に、肩から脇腹へ走る稲妻の白い筋。尾は折れ曲がった稲妻の形で、先が光る。目は青白く光る。
動き: 群れで走り回り、群れの一頭が吠える (予告 1.2 秒) と、狙った人の足もとに雷が落ちる (床の円)。噛みつきは素早い跳びかかり (予告 0.4 秒)。
"""
import math

from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12.0, 0)), "head": ("body", (0, 14.6, -8.2)), "jaw": ("head", (0, 13.4, -12.0)),
         "ear_l": ("head", (1.8, 16.4, -10.4)), "ear_r": ("head", (-1.8, 16.4, -10.4)),
         "fore_l": ("body", (2.8, 12.0, -5.6)), "fore_r": ("body", (-2.8, 12.0, -5.6)),
         "hind_l": ("body", (2.3, 12.0, 5.6)), "hind_r": ("body", (-2.3, 12.0, 5.6)), "tail": ("body", (0, 13.6, 8.0))}
COAT = [(40, 48, 66), (62, 72, 94), (88, 100, 124), (118, 130, 154), (150, 162, 184)]
DARK = [(10, 12, 18), (20, 24, 32), (34, 38, 48), (50, 56, 68)]
STREAK = [(170, 186, 204), (200, 214, 228), (230, 238, 246)]
PAL = {"mats": {"coat": mat(COAT, 0.6, noise=0.2), "dark": mat(DARK, 0.55, noise=0.2), "streak": mat(STREAK, 0.68),
                "tooth": mat([(196, 196, 186), (232, 232, 222)], 0.7), "nose": mat([(8, 8, 12), (20, 20, 26), (34, 34, 40)], 0.5, min=0)},
       "skull_bone": "head", "face_mat": "coat"}


def build(k):
    F = []
    # 前が重い胴: 盛り上がった肩、深い胸、細く締まった腰
    F += part("shoulders", ellipsoid("body", "coat", (0, 14.2, -4.4), (4.0, 4.6, 4.6), segs=10, rings=4))
    F += part("chest", ellipsoid("body", "coat", (0, 11.0, -4.6), (3.4, 3.6, 3.8), segs=8, rings=3))
    F += part("waist", ellipsoid("body", "coat", (0, 12.8, 1.6), (2.5, 2.7, 4.6), segs=8, rings=3))
    F += part("haunch", ellipsoid("body", "coat", (0, 12.6, 5.6), (2.8, 3.0, 2.7), segs=8, rings=3))
    # 肩から脇腹へ走る稲妻の白い筋 (左右)
    for sx in (1, -1):
        pts = [(sx * 3.9, 16.0, -6.4), (sx * 4.1, 13.4, -3.8), (sx * 3.6, 14.4, -2.2), (sx * 2.9, 11.6, 1.8)]
        F += part(f"bolt{sx}", tube("body", "streak", pts, ra=[0.35, 0.35, 0.3, 0.15], sides=4, hint=(0, 1, 0)))
    # 首の毛の輪 (静電気で放射状に逆立つ)
    for i in range(12):
        a = math.radians(-90 + i * 30)
        cx, cy = 3.9 * math.cos(a), 15.0 + 3.9 * math.sin(a)
        if cy < 11.4:
            continue
        L = 2.6 if cy > 14 else 1.8
        F += part(f"ruff{i}", tube("body", "dark", [(cx * 0.8, 15.0 + (cy - 15.0) * 0.8, -6.8), (cx * (1 + L / 3.9), 15.0 + (cy - 15.0) * (1 + L / 3.9), -5.2)],
                                    ra=[1.0, 0.05], sides=4, hint=(0, 0, 1)))
    # 背筋の雷の節 (光る毛の房)
    for i, (z, h) in enumerate(((-3.0, 2.2), (-0.4, 1.8), (2.2, 1.5), (4.6, 1.2))):
        F += part(f"node{i}", tube("body", "glow_blue", [(0, 15.6 - i * 0.6 + (0.8 if i == 0 else 0), z), (0, 15.6 - i * 0.6 + h + (0.8 if i == 0 else 0), z + 1.0)],
                                    ra=[0.75, 0.05], sides=4, hint=(1, 0, 0)))
    # 頭: 低く構えた広い頭、短く尖った耳、光る目、牙
    F += part("neck", tube("head", "coat", [(0, 14.4, -6.4), (0, 14.6, -9.2)], ra=[2.8, 2.4], sides=7, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "coat", (0, 15.2, -10.8), (2.8, 2.4, 2.8), segs=8, rings=3))
    F += part("cheek_ruff", ellipsoid("head", "dark", (0, 14.0, -10.2), (3.3, 1.6, 2.2), segs=8, rings=2))
    F += part("muzzle", ellipsoid("head", "coat", (0, 14.2, -14.0), (1.5, 1.3, 2.4), segs=7, rings=2))
    F += part("nose", ellipsoid("head", "nose", (0, 14.7, -16.2), (0.7, 0.5, 0.45), segs=5, rings=1))
    F += part("brow", box("head", "dark", (0, 16.4, -12.2), (3.6, 0.6, 1.2)))
    for sx in (1, -1):
        F += part(f"eye{sx}", box("head", "glow_pale", (sx * 1.35, 15.6, -13.0), (0.75, 0.35, 0.3)))
        F += part(f"fang{sx}", box("head", "tooth", (sx * 0.8, 13.0, -15.4), (0.32, 1.0, 0.32), taper=(0.2, 0.2)))
    F += part("jaw", ellipsoid("jaw", "coat", (0, 12.9, -13.8), (1.3, 0.6, 2.2), segs=6, rings=2))
    F += part("ear_l", box("ear_l", "dark", (1.9, 17.6, -10.4), (1.4, 2.2, 0.6), taper=(0.1, 0.6)))
    F += part("ear_r", box("ear_r", "dark", (-1.9, 17.6, -10.4), (1.4, 2.2, 0.6), taper=(0.1, 0.6)))
    # 脚: 太い前脚、細い後ろ脚。足先は暗い
    for sx, fb, hb in ((1, "fore_l", "hind_l"), (-1, "fore_r", "hind_r")):
        F += part(f"foreup{sx}", tube(fb, "coat", [(sx * 2.9, 12.4, -5.6), (sx * 3.0, 6.0, -6.2)], ra=[1.9, 1.2], sides=7, hint=(1, 0, 0)))
        F += part(f"forelo{sx}", tube(fb, "coat", [(sx * 3.0, 6.2, -6.2), (sx * 2.9, 1.2, -5.8)], ra=[1.1, 0.9], sides=6, hint=(1, 0, 0)))
        F += part(f"forepaw{sx}", ellipsoid(fb, "dark", (sx * 2.9, 0.7, -6.6), (1.2, 0.7, 1.4), segs=6, rings=2))
        F += part(f"thigh{sx}", ellipsoid(hb, "coat", (sx * 2.0, 11.4, 5.6), (1.3, 2.6, 2.2), segs=8, rings=3))
        F += part(f"hindlo{sx}", tube(hb, "coat", [(sx * 2.2, 9.0, 6.6), (sx * 2.3, 4.6, 8.0), (sx * 2.2, 1.0, 6.8)], ra=[0.9, 0.65, 0.6], sides=5, hint=(1, 0, 0)))
        F += part(f"hindpaw{sx}", ellipsoid(hb, "dark", (sx * 2.2, 0.6, 6.2), (0.9, 0.6, 1.2), segs=6, rings=2))
    # 稲妻の形に折れた尾 (先が光る)
    F += part("tail", tube("tail", "coat", [(0, 13.6, 7.8), (0, 15.0, 10.4), (0, 12.6, 11.6), (0, 14.2, 14.2)], ra=[1.1, 0.9, 0.8, 0.5], sides=6, hint=(1, 0, 0)))
    F += part("tail_tip", tube("tail", "glow_blue", [(0, 14.1, 14.0), (0, 15.6, 16.0)], ra=[0.55, 0.05], sides=5, hint=(1, 0, 0)))
    return F, []


POSES = {
    "idle": {"head": (-6, 0, 0), "tail": (0, 0, 0)},
    # 吠える (雷の合図): 前脚を突っ張り、天へ向かって口を開く
    "howl": {"body": (14, 0, 0), "head": (42, 0, 0), "jaw": (-28, 0, 0), "fore_l": (-14, 0, 0), "fore_r": (-14, 0, 0), "hind_l": (-16, 0, 0), "hind_r": (-16, 0, 0),
             "tail": (20, 0, 0), "ear_l": (-20, 0, 0), "ear_r": (-20, 0, 0)},
    # 跳びかかりの溜め
    "telegraph": {"body": (-8, 0, 0), "head": (-12, 0, 0), "jaw": (-20, 0, 0), "fore_l": (24, 0, 0), "fore_r": (24, 0, 0), "hind_l": (-32, 0, 0), "hind_r": (-32, 0, 0),
                  "tail": (-10, 0, 0), "ear_l": (-30, 0, 0), "ear_r": (-30, 0, 0)},
}

SPEC = dict(id="thunder_wolf", short="雷狼", title="雷狼 (thunder_wolf) — 帯電した毛の大きな狼",
            sub=["前が重い大きな狼。盛り上がった肩と太い前脚、静電気で逆立った首の毛の輪、背筋の光る雷の節、肩から脇腹への稲妻の白い筋、稲妻の形の尾。",
                 "群れの一頭が吠える (予告 1.2 秒) と、狙った人の足もとに雷が落ちる。噛みつきは素早い跳びかかり (予告 0.4 秒)。"],
            rows=[("前 3/4", "idle", 210, 12, 440, (0, 11, -2), 16.0), ("横", "idle", 270, 6, 440, (0, 11, -2), 16.0),
                  ("後ろ 3/4", "idle", 30, 12, 440, (0, 11, -2), 16.0), ("予告: 吠える", "howl", 230, 8, 440, (0, 12, -2), 16.0),
                  ("跳びかかりの溜め", "telegraph", 210, 8, 440, (0, 11, -2), 16.0)], atlas_h=220)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
