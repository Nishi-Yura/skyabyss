# -*- coding: utf-8 -*-
"""塔の側の目 (tower_eye) 低ポリ 一品一案 案 B「黒石の眼」。あいだの塔の側の目 (docs/design/54 の 2.5 の Eyes of War)。
黒い石の籠が割れて、金の継ぎ目で繋がっている (天の塔の欠片)。中は黒い石の球に紫に光る大きな虹彩。
鎧戸は黒い石の板。閉じる時は前へ回って紫の虹彩を隠す。下に紫の欠片が垂れる。
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
STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VIOLET = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
PAL = make_pal({
    "stone": (STONE, 0.5, dict(crack=0.12, noise=0.22)),
    "stone2": (STONE, 0.42, dict(crack=0.2, noise=0.26)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "violet": (VIOLET, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
}, glow=["violet"])

R = 9.6  # 籠の半径


def hoop(bone, mat, y, r, w=0.6, n=16):
    return tube(bone, mat, [(math.cos(a) * r, y, math.sin(a) * r) for a in [i / n * 2 * math.pi for i in range(n + 1)]], ra=w, sides=4, hint=(0, 1, 0))


def build(k):
    F = []
    # 黒い石の台と金の継ぎ目
    dish = [ring(6.0, 3.0, -3.0, 3.0, n=16), ring(7.2, 7.4, -7.4, 7.4, n=16), ring(8.6, R + 0.8, -R - 0.8, R + 0.8, n=16), ring(9.6, R + 0.9, -R - 0.9, R + 0.9, n=16)]
    F += part("base", loft("cage", "stone", dish, caps=("stone", "stone2")))
    F += part("baserim", hoop("cage", "gold", 9.6, R + 1.1, 0.5))
    # 割れた屋根: 輪の幅を揃えず、先が欠けた塔の頂 (上は開いて紫の芯が見える)
    roof = [ring(34.6, R + 1.0, -R - 1.0, R + 1.0, n=12), ring(36.0, R + 0.2, -R + 0.8, R + 1.4, n=12),
            ring(38.6, 6.4, -7.6, 5.2, n=12), ring(41.0, 3.6, -2.0, 3.2, n=12), ring(42.0, 1.4, -1.0, 1.8, n=12)]
    F += part("roof", loft("cage", "stone2", roof, caps=("stone2", "violet")))
    F += part("crackgold1", tube("cage", "gold", [(-5.0, 36.0, -6.0), (-3.0, 38.0, -8.2), (-1.0, 40.2, -7.0), (0.5, 42.0, -5.0)], ra=0.45, sides=4, hint=(1, 0, 0)))
    F += part("crackgold2", tube("cage", "gold", [(5.5, 35.0, 3.0), (4.0, 37.2, 6.0), (6.2, 39.0, 5.5)], ra=0.4, sides=4, hint=(1, 0, 0)))
    F += part("roofrim", hoop("cage", "gold", 35.0, R + 1.1, 0.55))
    # 柱 6 本 (黒い石。紫の割れ目の筋を一本ずつ)
    for i in range(6):
        a = i / 6 * 2 * math.pi + math.pi / 6
        x, z = math.cos(a) * R, math.sin(a) * R
        F += part(f"post{i}", tube("cage", "stone", [(x, 9.4, z), (x * 1.03, 22.0, z * 1.03), (x, 34.8, z)], ra=[0.95, 0.7, 0.95], sides=4, hint=(0, 1, 0)))
        F += part(f"vein{i}", tube("cage", "violet", [(x * 1.02, 13.0, z * 1.02), (x * 1.06, 19.0, z * 1.06), (x * 1.02, 26.0, z * 1.02)], ra=0.3, sides=3, hint=(0, 1, 0)))
    F += part("midhoop", hoop("cage", "gold", 22.0, R + 0.35, 0.5))
    # 目 (黒い石の球。紫の虹彩が前へ張り出し、瞳は洞の黒)
    F += part("ball", ellipsoid("eye", "stone", (0, 22.0, 0), (6.8, 6.8, 6.8), segs=10, rings=5))
    F += part("iris", ellipsoid("eye", "violet", (0, 22.0, -6.1), (4.4, 4.4, 1.3), segs=12, rings=3))
    F += part("pupil", ellipsoid("eye", "hollow", (0, 22.0, -7.2), (1.4, 2.6, 0.6), segs=8, rings=2))
    F += part("ring", tube("eye", "gold", [(math.cos(a) * 4.9, 22.0 + math.sin(a) * 4.9, -6.6) for a in [i / 12 * 2 * math.pi for i in range(13)]],
                           ra=0.4, sides=3, hint=(0, 0, 1)))
    # 鎧戸 2 枚 (黒い石の板。金の継ぎ目)
    for side, bone_name, mid in ((-1, "shutter_l", math.radians(150)), (1, "shutter_r", math.radians(30))):
        arc = [(-0.95 + 1.9 * t / 8) for t in range(9)]
        pts = [(math.cos(mid + d) * (R - 1.6), 22.0, math.sin(mid + d) * (R - 1.6)) for d in arc]
        F += part(f"shutter{side}", tube(bone_name, "stone", pts, ra=6.0, rb=0.3, sides=4, hint=(0, 1, 0)))
        F += part(f"seam{side}", tube(bone_name, "gold", [(math.cos(mid + d) * (R - 1.25), 22.0 + 2.0 * math.sin(d * 3.0), math.sin(mid + d) * (R - 1.25)) for d in arc],
                                       ra=0.4, rb=0.25, sides=4, hint=(0, 1, 0)))
        F += part(f"handle{side}", ellipsoid(bone_name, "gold", (math.cos(mid) * (R - 1.1), 22.0, math.sin(mid) * (R - 1.1)), (0.7, 0.7, 0.7), segs=5, rings=2))
    # 下へ垂れる紫の欠片 (三つ。尖りは下へ)
    for i in range(3):
        a = i / 3 * 2 * math.pi + 0.4
        x, z = math.cos(a) * 3.0, math.sin(a) * 3.0
        F += part(f"shard{i}", spike("cage", "stone2", (x, 6.2, z), (x * 0.1, -1.0, z * 0.1), 7.0 - i, 1.6, sides=4, rings=2, hint=(1, 0, 0)))
        F += part(f"shardtip{i}", ellipsoid("cage", "violet", (x * 0.9, 0.2 - i, z * 0.9), (1.0, 1.2, 1.0), segs=6, rings=3))
    return F, []


POSES = {
    "idle": {},
    "closed": {"shutter_l": (0, -100, 0), "shutter_r": (0, 100, 0)},
    "stare": {"eye": (8, 0, 0), "shutter_l": (0, 15, 0), "shutter_r": (0, -15, 0)},
}

SPEC = dict(
    id="tower_eye_b", short="塔の目 B", kind="あいだの塔 側の目 一品一案 (54 の 2.5)",
    title="塔の側の目 案 B 黒石の眼 (tower_eye) — 割れた黒い石の籠と紫の虹彩・低ポリ + ドット絵",
    sub=["橋の左右の台に据わる。小 (1 倍) 4 つと大 (1.8 倍) 2 つは同じ形。凝視・閉じる・被弾・死。",
         "黒い石の柱と割れた屋根を金の継ぎ目で繋ぐ。中は黒い石の球に紫に光る大きな虹彩。鎧戸は黒い石に金の筋。"],
    labels=["塔の側の目 案 B"],
    rows=[("前 3/4", "idle", 205, 8, 420, (0, 24, 0), 6.6),
          ("閉じる", "closed", 190, 6, 420, (0, 24, 0), 6.6),
          ("凝視", "stare", 225, -8, 420, (0, 24, 0), 6.6),
          ("横", "idle", 270, 4, 420, (0, 24, 0), 6.6)],
    options={"a": dict(name="黒石の眼", desc="割れた黒い石の柱と屋根、金の継ぎ目、中は紫に光る大きな虹彩。鎧戸は黒い石に金の筋。")},
    extra="闇の側 (黒い石・紫の割れ目・金の継ぎ目)。骨 5 本 (灯守の目と同じ名前)。光るのは虹彩と紫の筋。",
    budget="目 1400〜1800", pw=440,
    later="二段目 = 継ぎ目が広がり紫が漏れる。三段目 = 屋根が開いて紫の芯が見える。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
