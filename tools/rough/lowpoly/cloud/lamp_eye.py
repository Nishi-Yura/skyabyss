# -*- coding: utf-8 -*-
"""灯守の目 (lamp_eye) 低ポリ 一品一案。灯の島の主 (docs/design/50 の 4.2.2)。
灯台の灯の籠に宿った一つ目。真鍮の籠 (縦の格子 8 本・上の丸屋根と煙出しの頂・下の受け皿) の中に大きな目 (白目・朝の光の虹彩・黒い瞳)。
目の左右と後ろを二枚の鎧戸 (湾曲した真鍮の板) が囲み、閉じる時は前へ回って目を隠す (凝視のあと 3 秒)。受け皿の下に切れた鎖と海藻。高さ 2.8 ブロック。宙に浮く。
骨: root > cage > eye / shutter_l / shutter_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "cage": ("root", (0, 22, 0)), "eye": ("cage", (0, 22, 0)),
         "shutter_l": ("cage", (0, 22, 0)), "shutter_r": ("cage", (0, 22, 0))}
PAL = make_pal({
    "brass": (BRASS, 0.72, dict(crack=0.06, noise=0.14)),
    "verdi": (VERDI, 0.55, dict(crack=0.05, noise=0.2)),
    "sclera": (SCLERA, 0.6, dict(crack=0.04, noise=0.08)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "weed": (SEAWEED, 0.5, dict(crack=0.0, noise=0.25)),
    "oil": (OILSKIN, 0.55, dict(crack=0.04, noise=0.2)),
}, glow=["dawn"])

R = 9.6  # 籠の半径


def hoop(bone, mat, y, r, w=0.6, n=16):
    return tube(bone, mat, [(math.cos(a) * r, y, math.sin(a) * r) for a in [i / n * 2 * math.pi for i in range(n + 1)]], ra=w, sides=4, hint=(0, 1, 0))


def build(k):
    F = []
    # 受け皿 (下) と丸屋根 (上)
    dish = [ring(6.0, 3.0, -3.0, 3.0, n=16), ring(7.2, 7.4, -7.4, 7.4, n=16), ring(8.6, R + 0.8, -R - 0.8, R + 0.8, n=16), ring(9.6, R + 0.9, -R - 0.9, R + 0.9, n=16)]
    F += part("dish", loft("cage", "brass", dish, caps=("brass", "oil")))
    roof = [ring(34.6, R + 1.0, -R - 1.0, R + 1.0, n=16), ring(35.6, R + 0.6, -R - 0.6, R + 0.6, n=16), ring(38.6, 7.0, -7.0, 7.0, n=16),
            ring(41.4, 3.2, -3.2, 3.2, n=16), ring(42.4, 1.6, -1.6, 1.6, n=16)]
    F += part("roof", loft("cage", "verdi", roof, caps=("oil", "verdi")))
    F += part("roofrim", hoop("cage", "brass", 35.0, R + 1.1, 0.55))
    F += part("vent", tube("cage", "brass", [(0, 42.0, 0), (0, 45.0, 0)], ra=[1.2, 0.9], sides=6, hint=(1, 0, 0)))
    F += part("ventcap", ellipsoid("cage", "brass", (0, 45.6, 0), (1.6, 0.8, 1.6), segs=8, rings=2))
    F += part("finial", spike("cage", "brass", (0, 46.2, 0), (0, 1, 0), 2.4, 0.5, sides=4, rings=1))
    # 縦の格子 8 本と中ほどの輪
    for i in range(8):
        a = i / 8 * 2 * math.pi + math.pi / 8
        F += part(f"bar{i}", tube("cage", "brass", [(math.cos(a) * R, 9.4, math.sin(a) * R), (math.cos(a) * (R + 0.3), 22.0, math.sin(a) * (R + 0.3)),
                                                     (math.cos(a) * R, 34.8, math.sin(a) * R)], ra=0.55, sides=4, hint=(0, 1, 0)))
    F += part("midhoop", hoop("cage", "brass", 22.0, R + 0.35, 0.5))
    # 目 (白目・虹彩・瞳。前を向く)
    F += part("ball", ellipsoid("eye", "sclera", (0, 22.0, 0), (6.8, 6.8, 6.8), segs=12, rings=6))
    F += part("iris", ellipsoid("eye", "dawn", (0, 22.0, -6.1), (4.0, 4.0, 1.2), segs=12, rings=3))
    F += part("pupil", ellipsoid("eye", "hollow", (0, 22.0, -7.1), (1.1, 2.5, 0.6), segs=8, rings=2))
    # 白目の赤筋のかわりに、海の青緑の細い筋
    for i, (a0, a1) in enumerate(((0.3, 0.9), (2.0, 2.6), (3.6, 4.1), (5.0, 5.6))):
        pts = []
        for t in range(4):
            a = a0 + (a1 - a0) * t / 3
            pts.append((math.cos(a) * 5.2 * (1 - 0.15 * t), 22.0 + math.sin(a) * 5.2 * (1 - 0.15 * t), -3.3 - 0.6 * t))
        F += part(f"vein{i}", tube("eye", "verdi", pts, ra=0.14, sides=3, hint=(0, 0, 1)))
    # 鎧戸 2 枚 (湾曲した真鍮の板。ふだんは左右と後ろを囲み、前は開いている。閉じる時に前へ回って目を隠す)
    for side, bone_name, mid in ((-1, "shutter_l", math.radians(150)), (1, "shutter_r", math.radians(30))):
        arc = [(-0.95 + 1.9 * t / 8) for t in range(9)]
        pts = [(math.cos(mid + d) * (R - 1.6), 22.0, math.sin(mid + d) * (R - 1.6)) for d in arc]
        F += part(f"shutter{side}", tube(bone_name, "brass", pts, ra=6.0, rb=0.3, sides=4, hint=(0, 1, 0)))
        for y in (17.0, 27.0):
            F += part(f"shband{side}{int(y)}", tube(bone_name, "verdi", [(math.cos(mid + d) * (R - 1.25), y, math.sin(mid + d) * (R - 1.25)) for d in arc],
                                                     ra=0.35, rb=0.2, sides=4, hint=(0, 1, 0)))
        F += part(f"handle{side}", ellipsoid(bone_name, "verdi", (math.cos(mid) * (R - 1.1), 22.0, math.sin(mid) * (R - 1.1)), (0.7, 0.7, 0.7), segs=5, rings=2))
    # 受け皿の下の切れた鎖と海藻
    for i in range(3):
        a = i / 3 * 2 * math.pi + 0.4
        x, z = math.cos(a) * 3.0, math.sin(a) * 3.0
        F += part(f"chain{i}", tube("cage", "verdi", [(x, 6.2, z), (x * 1.1, 3.0, z * 1.1), (x * 1.15, 0.4 + i, z * 1.15)], ra=0.35, sides=4, hint=(1, 0, 0)))
        F += part(f"weed{i}", tube("cage", "weed", [(x * 0.6, 6.0, z * 0.6), (x * 0.7, 2.5, z * 0.5), (x * 0.5, 0.0, z * 0.6)], ra=0.2, rb=[0.8, 0.6, 0.2], sides=4, hint=(0, 1, 0)))
    return F, []


POSES = {
    "idle": {},
    "closed": {"shutter_l": (0, -100, 0), "shutter_r": (0, 100, 0)},
    "stare": {"eye": (8, 0, 0), "shutter_l": (0, 15, 0), "shutter_r": (0, -15, 0)},
}

SPEC = dict(
    id="lamp_eye", short="灯守の目", kind="灯の島の主 一品一案 (50 の 4.2.2)",
    title="灯守の目 (lamp_eye) — 灯台の灯の籠に宿った一つ目・低ポリ + ドット絵 一案",
    sub=["回る光の筋・凝視 (1.5 秒 → 光の柱。石柱の陰で避ける)・灯の雨・灯蛾を呼ぶ。凝視のあと 3 秒 目を閉じる (傷 1.5 倍)。",
         "真鍮の籠と緑青の丸屋根、中に白目と朝の光の虹彩の大きな目。二枚の鎧戸が閉じる。高さ 2.8 ブロック。宙に浮く。"],
    labels=["灯守の目"],
    rows=[("前 3/4", "idle", 205, 8, 420, (0, 24, 0), 6.6),
          ("目を閉じる", "closed", 190, 6, 420, (0, 24, 0), 6.6),
          ("凝視", "stare", 225, -8, 420, (0, 24, 0), 6.6),
          ("横", "idle", 270, 4, 420, (0, 24, 0), 6.6)],
    options={"a": dict(name="籠に宿る一つ目", desc="真鍮の籠 (格子 8・緑青の丸屋根・受け皿)、大きな目、閉じる鎧戸 2、切れた鎖と海藻。")},
    extra="灯の島の言葉 (真鍮・緑青・目の白・朝の光)。骨 6 本。光るのは虹彩。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = 鎧戸の縁がともる。三段目 = 虹彩が二重に。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
