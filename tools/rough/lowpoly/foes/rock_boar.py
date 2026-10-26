"""岩背の大猪 (rock_boar)。岩鯨の丘陵の名のある強敵。Lv 12、まれに出る。
一体一案: 背丈 2 ブロック近い大猪。肩が山のように盛り上がり、背には岩鯨の背と同じ灰青の岩の板が瓦のように重なって生え、
板の継ぎ目に苔。黒茶の剛毛のたてがみが首から肩へ。顔は低く、欠けた白い大きな牙が上へ反る。小さな目が琥珀に光る。
脚は短く太く、蹄は黒い。尾は短い房。片方の牙には古い銛の折れ先が刺さったまま (昔、誰かが仕留め損ねた)。
動き: 前脚で地を掻いて (予告 1.2 秒、前の長い帯) 突進する。突進のあと岩の背を震わせて (予告 1.0 秒、まわりの円) 岩の破片を降らせる。
"""
import math

from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 18.0, 0)), "head": ("body", (0, 20.0, -13.0)),
         "fore_l": ("body", (4.6, 16.0, -9.0)), "fore_r": ("body", (-4.6, 16.0, -9.0)),
         "hind_l": ("body", (4.2, 17.0, 10.0)), "hind_r": ("body", (-4.2, 17.0, 10.0)), "tail": ("body", (0, 21.0, 15.4))}
HIDE = [(30, 24, 20), (50, 40, 32), (72, 58, 44), (96, 80, 60), (122, 104, 80)]
BRISTLE = [(12, 10, 10), (24, 20, 18), (40, 34, 28), (58, 50, 40)]
ROCK = [(46, 52, 60), (70, 78, 88), (98, 106, 116), (128, 136, 144), (156, 162, 168)]
MOSS = [(30, 48, 24), (48, 72, 32), (70, 98, 44)]
IVORY = [(150, 140, 112), (196, 186, 156), (230, 222, 196)]
PAL = {"mats": {"hide": mat(HIDE, 0.6, noise=0.25), "bristle": mat(BRISTLE, 0.55, noise=0.3), "rock": mat(ROCK, 0.6, crack=0.1, noise=0.2),
                "moss": mat(MOSS, 0.58, noise=0.4), "ivory": mat(IVORY, 0.66, crack=0.06), "hoof": mat([(10, 10, 10), (22, 20, 20), (38, 34, 32)], 0.5, min=0),
                "iron": mat([(30, 26, 24), (62, 52, 44), (96, 80, 62)], 0.55, crack=0.1), "snout": mat([(70, 50, 44), (104, 76, 66), (138, 104, 90)], 0.6)},
       "skull_bone": "head", "face_mat": "hide"}


def build(k):
    F = []
    # 胴: 山のような肩、太い胴、下がった尻
    F += part("hump", ellipsoid("body", "hide", (0, 21.0, -6.0), (8.6, 8.8, 9.0), segs=10, rings=4))
    F += part("barrel", ellipsoid("body", "hide", (0, 18.4, 3.0), (7.8, 7.4, 10.4), segs=10, rings=4))
    F += part("rump", ellipsoid("body", "hide", (0, 18.4, 11.0), (6.8, 6.6, 5.4), segs=9, rings=3))
    F += part("belly", ellipsoid("body", "bristle", (0, 12.2, 0.0), (6.0, 2.6, 9.6), segs=8, rings=2))
    # 岩の板 (瓦のように重なる。前ほど大きく高い) と継ぎ目の苔
    plates = [(-8.0, 29.0, 5.6, 3.6, 10), (-3.4, 28.4, 5.4, 3.4, -8), (1.4, 26.6, 5.0, 3.0, 12), (6.0, 25.4, 4.4, 2.6, -10), (10.2, 24.0, 3.6, 2.2, 6)]
    for i, (z, y, w, h, tilt) in enumerate(plates):
        rot = __import__("ck").rot_xyz(-18, 0, tilt * 0.4)
        F += part(f"plate{i}", box("body", "rock", (0, y + 1.6, z), (w * 2.3, h * 1.5, 4.8), taper=(0.5, 0.65), rot=rot))
        F += part(f"moss{i}", ellipsoid("body", "moss", (0, y + 0.4, z + 2.4), (w * 1.0, 0.9, 1.2), segs=6, rings=2))
    for sx in (1, -1):
        for i, (z, y) in enumerate(((-6.0, 25.6), (3.0, 24.0))):
            F += part(f"side_plate{sx}{i}", box("body", "rock", (sx * 7.0, y - 1.0, z), (3.2, 4.0, 6.0), taper=(0.5, 0.8),
                                                rot=__import__("ck").rot_xyz(0, 0, sx * 38)))
    # 首から肩への剛毛のたてがみ
    for i, z in enumerate((-12.0, -10.4, -8.8)):
        F += part(f"mane{i}", tube("body", "bristle", [(0, 25.6 - i * 0.4, z), (0, 30.0 - i * 0.6, z + 2.0)], ra=[2.8, 0.1], rb=[1.1, 0.1], sides=4, hint=(1, 0, 0)))
    # 頭: 低く長い頭、平たい鼻、反った牙 (片方に折れた銛の先)、琥珀の小さな目、小さな耳
    F += part("skull", ellipsoid("head", "hide", (0, 18.4, -16.4), (5.2, 5.4, 6.0), segs=9, rings=3))
    F += part("snout", tube("head", "hide", [(0, 17.0, -19.6), (0, 15.6, -23.6)], ra=[3.6, 3.0], rb=[3.2, 2.6], sides=7, hint=(1, 0, 0)))
    F += part("nose", ellipsoid("head", "snout", (0, 15.4, -24.0), (3.0, 2.6, 0.9), segs=8, rings=2))
    for sx in (1, -1):
        F += part(f"nostril{sx}", box("head", "hoof", (sx * 0.9, 15.4, -24.7), (0.6, 0.8, 0.2)))
        F += part(f"tusk{sx}", tube("head", "ivory", [(sx * 2.2, 14.8, -21.6), (sx * 3.6, 15.4, -23.4), (sx * 4.2, 18.6, -23.8), (sx * 3.4, 21.0, -22.4)],
                                    ra=[1.2, 1.05, 0.7, 0.18], sides=6, hint=(0, 0, 1)))
        F += part(f"eye{sx}", box("head", "glow_amber", (sx * 3.7, 20.4, -19.6), (0.6, 0.6, 0.6)))
        F += part(f"ear{sx}", box("head", "bristle", (sx * 4.0, 23.6, -14.4), (2.2, 3.0, 0.9), taper=(0.3, 0.6)))
    F += part("chip", box("head", "hoof", (-4.0, 20.0, -23.2), (0.8, 0.6, 0.8)))
    F += part("harpoon", tube("head", "iron", [(3.8, 18.4, -23.6), (6.8, 20.4, -22.2)], ra=[0.35, 0.3], sides=4, hint=(0, 1, 0)))
    F += part("harpoon_barb", box("head", "iron", (7.2, 20.6, -22.0), (1.2, 0.4, 0.8), taper=(0.2, 1.0)))
    F += part("jaw", ellipsoid("head", "hide", (0, 13.6, -18.4), (3.0, 1.6, 3.8), segs=7, rings=2))
    # 脚: 短く太い。黒い蹄
    for sx, fb, hb in ((1, "fore_l", "hind_l"), (-1, "fore_r", "hind_r")):
        F += part(f"foreup{sx}", tube(fb, "hide", [(sx * 5.4, 17.0, -9.0), (sx * 5.6, 7.0, -9.6)], ra=[3.8, 2.6], sides=7, hint=(1, 0, 0)))
        F += part(f"forelo{sx}", tube(fb, "bristle", [(sx * 5.6, 7.4, -9.6), (sx * 5.6, 2.0, -9.4)], ra=[2.3, 2.0], sides=6, hint=(1, 0, 0)))
        F += part(f"forehoof{sx}", box(fb, "hoof", (sx * 5.6, 1.0, -10.0), (3.6, 2.0, 3.8), taper=(0.85, 0.85)))
        F += part(f"thigh{sx}", ellipsoid(hb, "hide", (sx * 4.8, 15.0, 10.6), (3.4, 5.6, 4.2), segs=8, rings=3))
        F += part(f"hindlo{sx}", tube(hb, "bristle", [(sx * 5.0, 10.0, 12.0), (sx * 5.0, 2.0, 11.0)], ra=[2.4, 2.0], sides=6, hint=(1, 0, 0)))
        F += part(f"hindhoof{sx}", box(hb, "hoof", (sx * 5.0, 1.0, 10.6), (3.6, 2.0, 3.8), taper=(0.85, 0.85)))
    F += part("tail", tube("tail", "hide", [(0, 21.0, 15.2), (0, 18.4, 17.0)], ra=[0.8, 0.5], sides=5, hint=(1, 0, 0)))
    F += part("tail_tuft", ellipsoid("tail", "bristle", (0, 17.4, 17.4), (1.0, 1.6, 1.0), segs=6, rings=2))
    return F, []


POSES = {
    "idle": {"head": (-16, 0, 0), "body": (-3, 0, 0)},
    # 地を掻く (突進の予告): 頭を下げ、右の前脚を持ち上げる
    "telegraph": {"body": (-8, 0, 0), "head": (-26, 0, 0), "fore_r": (40, 0, 0), "hind_l": (-10, 0, 0), "hind_r": (-10, 0, 0), "tail": (30, 0, 0)},
    # 背を震わせる: 前を持ち上げて岩の背を揺する
    "quake": {"body": (16, 0, 0), "head": (24, 0, 0), "fore_l": (-30, 0, 0), "fore_r": (-30, 0, 0), "hind_l": (-16, 0, 0), "hind_r": (-16, 0, 0)},
}

SPEC = dict(id="rock_boar", short="岩背の大猪", title="岩背の大猪 (rock_boar) — 岩鯨の丘の名のある強敵",
            sub=["山のような肩の大猪。背に岩鯨と同じ灰青の岩の板が瓦のように重なり、継ぎ目に苔。剛毛のたてがみ、反った欠けた牙 (片方に折れた銛の先)、琥珀の目。",
                 "地を掻いて (予告 1.2 秒、前の長い帯) 突進。岩の背を震わせて (予告 1.0 秒、まわりの円) 岩の破片を降らせる。"],
            rows=[("前 3/4", "idle", 210, 10, 600, (0, 18, -2), 7.0), ("横", "idle", 270, 6, 600, (0, 18, -2), 7.0),
                  ("後ろ 3/4", "idle", 30, 10, 600, (0, 18, -2), 7.0), ("予告: 地を掻く", "telegraph", 210, 8, 600, (0, 18, -2), 7.0),
                  ("予告: 背を震わせる", "quake", 230, 8, 600, (0, 20, -2), 7.0)], atlas_h=260)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
