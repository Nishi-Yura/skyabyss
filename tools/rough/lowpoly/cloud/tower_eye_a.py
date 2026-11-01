# -*- coding: utf-8 -*-
"""塔の側の目 (tower_eye) 低ポリ 一品一案 案 A「天秤の眼」。あいだの塔の側の目 (docs/design/54 の 2.5 の Eyes of War)。
白い大理石の小さな社 (籠) に、白い大理石の目。金の虹彩の真ん中に天秤の印 (金の梁と皿)、瞳は紫の光。
鎧戸 2 枚は大理石の板に金の帯。閉じる時は前へ回って目を隠す。下に金の鎖と紫の玉。
小 (1 倍) と大 (1.8 倍) は同じ形を拡大する。宙に浮く。
骨は灯守の目 (lamp_eye) と同じ: root > cage > eye / shutter_l / shutter_r (アニメ lamp_eye.animation.json がそのまま動く)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "cage": ("root", (0, 22, 0)), "eye": ("cage", (0, 22, 0)),
          "shutter_l": ("cage", (0, 22, 0)), "shutter_r": ("cage", (0, 22, 0))}
MARBLE = [(170, 166, 156), (206, 202, 192), (230, 228, 220), (246, 244, 238)]
STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VIOLET = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
PAL = make_pal({
    "marble": (MARBLE, 0.62, dict(crack=0.03, noise=0.14)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "stone": (STONE, 0.5, dict(crack=0.10, noise=0.2)),
    "violet": (VIOLET, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
}, glow=["violet"])

R = 9.6  # 籠の半径


def hoop(bone, mat, y, r, w=0.6, n=16):
    return tube(bone, mat, [(math.cos(a) * r, y, math.sin(a) * r) for a in [i / n * 2 * math.pi for i in range(n + 1)]], ra=w, sides=4, hint=(0, 1, 0))


def build(k):
    F = []
    # 台 (大理石) と金の縁
    dish = [ring(6.0, 3.0, -3.0, 3.0, n=16), ring(7.2, 7.4, -7.4, 7.4, n=16), ring(8.6, R + 0.8, -R - 0.8, R + 0.8, n=16), ring(9.6, R + 0.9, -R - 0.9, R + 0.9, n=16)]
    F += part("base", loft("cage", "marble", dish, caps=("marble", "gold")))
    F += part("baserim", hoop("cage", "gold", 9.6, R + 1.1, 0.5))
    # 屋根: 三段の大理石の塔の頂 (天の塔の段を小さく写す)
    roof = [ring(34.6, R + 1.0, -R - 1.0, R + 1.0, n=16), ring(35.6, R + 0.6, -R - 0.6, R + 0.6, n=16), ring(38.6, 7.0, -7.0, 7.0, n=16),
            ring(41.4, 3.2, -3.2, 3.2, n=16), ring(42.4, 1.6, -1.6, 1.6, n=16)]
    F += part("roof", loft("cage", "marble", roof, caps=("gold", "marble")))
    F += part("roofrim", hoop("cage", "gold", 35.0, R + 1.1, 0.55))
    F += part("finial", spike("cage", "gold", (0, 42.0, 0), (0, 1, 0), 5.0, 0.9, sides=4, rings=2, hint=(1, 0, 0)))
    # 柱 8 本 (大理石、根と頂に金の座)
    for i in range(6):
        a = i / 6 * 2 * math.pi + math.pi / 6
        x, z = math.cos(a) * R, math.sin(a) * R
        F += part(f"post{i}", tube("cage", "marble", [(x, 9.4, z), (x * 1.03, 22.0, z * 1.03), (x, 34.8, z)], ra=[0.9, 0.7, 0.9], sides=4, hint=(0, 1, 0)))
        F += part(f"capb{i}", ellipsoid("cage", "gold", (x, 9.6, z), (1.5, 1.0, 1.5), segs=6, rings=2))
    # 目 (白い大理石の球。虹彩は金、天秤の印を前に、瞳は紫)
    F += part("ball", ellipsoid("eye", "marble", (0, 22.0, 0), (6.8, 6.8, 6.8), segs=10, rings=5))
    F += part("iris", ellipsoid("eye", "gold", (0, 22.0, -6.1), (4.0, 4.0, 1.2), segs=12, rings=3))
    F += part("pupil", ellipsoid("eye", "violet", (0, 22.0, -7.1), (1.2, 2.4, 0.6), segs=8, rings=2))
    F += part("beam", tube("eye", "gold", [(-3.6, 22.0, -7.3), (3.6, 22.0, -7.3)], ra=0.45, sides=4, hint=(0, 1, 0)))
    F += part("panl", ellipsoid("eye", "gold", (-3.6, 19.6, -7.3), (1.1, 0.5, 1.1), segs=6, rings=2))
    F += part("panr", ellipsoid("eye", "gold", (3.6, 19.6, -7.3), (1.1, 0.5, 1.1), segs=6, rings=2))
    # 鎧戸 2 枚 (大理石の湾曲した板。金の帯二本)
    for side, bone_name, mid in ((-1, "shutter_l", math.radians(150)), (1, "shutter_r", math.radians(30))):
        arc = [(-0.95 + 1.9 * t / 8) for t in range(9)]
        pts = [(math.cos(mid + d) * (R - 1.6), 22.0, math.sin(mid + d) * (R - 1.6)) for d in arc]
        F += part(f"shutter{side}", tube(bone_name, "marble", pts, ra=6.0, rb=0.3, sides=4, hint=(0, 1, 0)))
        for y in (17.0, 27.0):
            F += part(f"shband{side}{int(y)}", tube(bone_name, "gold", [(math.cos(mid + d) * (R - 1.25), y, math.sin(mid + d) * (R - 1.25)) for d in arc],
                                                     ra=0.35, rb=0.2, sides=4, hint=(0, 1, 0)))
        F += part(f"handle{side}", ellipsoid(bone_name, "gold", (math.cos(mid) * (R - 1.1), 22.0, math.sin(mid) * (R - 1.1)), (0.7, 0.7, 0.7), segs=5, rings=2))
    # 台の下の金の鎖と紫の玉
    for i in range(3):
        a = i / 3 * 2 * math.pi + 0.4
        x, z = math.cos(a) * 3.0, math.sin(a) * 3.0
        F += part(f"chain{i}", tube("cage", "gold", [(x, 6.2, z), (x * 1.1, 3.0, z * 1.1), (x * 1.15, 0.8, z * 1.15)], ra=0.35, sides=4, hint=(1, 0, 0)))
        F += part(f"gem{i}", ellipsoid("cage", "violet", (x * 1.15, 0.0, z * 1.15), (1.0, 1.4, 1.0), segs=6, rings=3))
    return F, []


POSES = {
    "idle": {},
    "closed": {"shutter_l": (0, -100, 0), "shutter_r": (0, 100, 0)},
    "stare": {"eye": (8, 0, 0), "shutter_l": (0, 15, 0), "shutter_r": (0, -15, 0)},
}

SPEC = dict(
    id="tower_eye_a", short="塔の目 A", kind="あいだの塔 側の目 一品一案 (54 の 2.5)",
    title="塔の側の目 案 A 天秤の眼 (tower_eye) — 白い大理石の社と金の虹彩・低ポリ + ドット絵",
    sub=["橋の左右の台に据わる。小 (1 倍) 4 つと大 (1.8 倍) 2 つは同じ形。凝視 (光線の前ぶれ)・閉じる・被弾・死。",
         "白い大理石の柱と三段の屋根、金の虹彩の真ん中に天秤の印、瞳は紫。鎧戸は大理石に金の帯。骨は灯守の目と同じ。"],
    labels=["塔の側の目 案 A"],
    rows=[("前 3/4", "idle", 205, 8, 420, (0, 24, 0), 6.6),
          ("閉じる", "closed", 190, 6, 420, (0, 24, 0), 6.6),
          ("凝視", "stare", 225, -8, 420, (0, 24, 0), 6.6),
          ("横", "idle", 270, 4, 420, (0, 24, 0), 6.6)],
    options={"a": dict(name="天秤の眼", desc="白い大理石の柱と三段の屋根、金の虹彩に天秤の印、紫の瞳。鎧戸は大理石に金の帯。")},
    extra="天の側 (白い大理石と金)。骨 5 本 (灯守の目と同じ名前)。光るのは瞳の紫。",
    budget="目 1400〜1800", pw=440,
    later="二段目 = 瞳の紫が強まる。三段目 = 天秤の梁が光る。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
