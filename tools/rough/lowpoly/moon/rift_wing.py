# -*- coding: utf-8 -*-
"""淵の羽影 (rift_wing) 低ポリ 一品一案。下弦の淵の敵 (docs/design/46 の 4.6)。
欠けた月の翼の影の飛ぶもの。小さな影の胴の前に下弦の月の銀の仮面 (左が欠けた輪、中に星の目一つ)、
左右に三日月の形の大きな影の翼 (前の縁に銀の骨、後ろの縁は三つに裂ける)、胴の後ろから細く薄れていく影の尾。
骨: root > body > head / wing_l / wing_r / tail。宙に浮く (胴の高さ 1.4 ブロック)。翼の幅 3 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

CY = 22.0
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, CY, 0)), "head": ("body", (0, CY + 0.5, -5.0)),
         "wing_l": ("body", (-3.6, CY + 1.4, -0.5)), "wing_r": ("body", (3.6, CY + 1.4, -0.5)), "tail": ("body", (0, CY, 5.0))}
MEMB = [(10, 10, 22), (22, 22, 42), (38, 38, 66), (58, 60, 92)]
PAL = make_pal({
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.08, min=0)),
    "memb": (MEMB, 0.52, dict(crack=0.02, noise=0.12)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "star": (STAR, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["star"])


def slab(bone, m, front, back, th):
    """前の縁と後ろの縁の点の並びの間を、厚み th の閉じた板にする (翼の膜)"""
    rings = []
    for f, b in zip(front, back):
        f, b = np.asarray(f, float), np.asarray(b, float)
        up = np.array((0, th, 0))
        rings.append([tuple(f + up), tuple(b + up), tuple(b - up), tuple(f - up)])
    return loft(bone, m, rings, caps=(True, True))


def build(k):
    F = []
    # 胴: 細長い影の玉、背に銀の背骨の筋
    F += part("torso", ellipsoid("body", "shadow", (0, CY, 0), (3.8, 3.2, 6.0), segs=12, rings=5))
    F += part("spine", tube("body", "silver", [(0, CY + 2.8, -3.8), (0, CY + 3.3, 0), (0, CY + 2.6, 4.2)], ra=[0.6, 0.85, 0.5], sides=4, hint=(1, 0, 0),
                            caps=(True, True)))
    # 仮面: 下弦の月の輪 (左が欠けた銀の輪を顔の前に立てる)、中は影、星の目一つ
    F += part("face", ellipsoid("head", "shadow", (0, CY + 0.5, -6.4), (3.0, 3.0, 2.4), segs=10, rings=4))
    pts = []
    for i in range(10):
        a = math.radians(-10 + i * 230 / 9)
        pts.append((math.sin(a) * 3.6, CY + 0.5 + math.cos(a) * 3.6, -8.2))
    F += part("mask", tube("head", "silver", pts, ra=[0.5, 0.9, 1.15, 1.3, 1.35, 1.35, 1.3, 1.15, 0.9, 0.5], rb=0.9, sides=5, hint=(0, 0, 1),
                           caps=(True, True)))
    F += part("eye", ellipsoid("head", "star", (0.8, CY + 0.9, -8.6), (1.1, 1.1, 0.6), segs=7, rings=3))
    # 翼: 三日月の形。前の縁は外へ弧を描いて先が前へ尖り、後ろの縁は三つの裂けで波打つ
    for sx, b in ((-1, "wing_l"), (1, "wing_r")):
        n = 9
        front, back = [], []
        for i in range(n):
            t = i / (n - 1)
            x = sx * (3.4 + t * 24.0)
            fz = -1.2 - math.sin(t * math.pi) * 2.6 - t ** 3 * 3.0
            fy = CY + 1.0 + math.sin(t * math.pi * 0.9) * 3.4 - t * 1.2
            chord = 12.0 * (1 - t) ** 0.7 + 0.8
            notch = 2.6 * abs(math.sin(t * math.pi * 3)) * (1 - t)
            front.append((x, fy, fz))
            back.append((x, fy - 0.5, fz + chord - notch))
        F += part("memb" + b, slab(b, "memb", front, back, 0.45))
        F += part("bone" + b, tube(b, "silver", [(p[0], p[1] + 0.2, p[2] - 0.2) for p in front],
                                   ra=[1.0, 0.95, 0.9, 0.8, 0.7, 0.6, 0.48, 0.34, 0.12], sides=4, hint=(0, 1, 0)))
        # 翼の指 (骨から後ろの縁へ二本)
        for j, t in enumerate((0.38, 0.66)):
            i = int(t * (n - 1))
            F += part(f"fin{b}{j}", tube(b, "silver", [(front[i][0], front[i][1] + 0.15, front[i][2]), (back[i][0], back[i][1] + 0.15, back[i][2] - 0.4)],
                                          ra=[0.32, 0.12], sides=4, hint=(0, 1, 0)))
    # 爪: 胴の下に銀の鉤爪二つ (急降下で前へ出す)
    for sx in (-1, 1):
        F += part(f"claw{sx}", tube("body", "silver", [(sx * 1.6, CY - 2.6, -1.0), (sx * 1.8, CY - 4.6, -1.8), (sx * 1.7, CY - 5.4, -3.4)],
                                    ra=[0.6, 0.45, 0.0], sides=4, hint=(1, 0, 0)))
    # 尾: 胴の後ろから細く薄れる影の帯 (先は星の粒)
    tail = [(0, CY - 0.2, 4.6), (0, CY - 1.0, 9.5), (0, CY - 0.4, 14.0), (0, CY + 0.6, 18.0)]
    F += part("tailb", tube("tail", "shadow", tail, ra=[2.4, 1.7, 1.0, 0.3], rb=[0.8, 0.55, 0.35, 0.12], sides=4, hint=(0, 1, 0)))
    F += part("tailstar", ellipsoid("tail", "star", (0, CY + 0.7, 18.6), (0.7, 0.7, 0.7), segs=5, rings=3))
    return F, []


POSES = {
    "idle": {},
    "glide": {"wing_l": (0, 0, -18), "wing_r": (0, 0, 18), "tail": (8, 0, 0)},
    "dive": {"body": (-35, 0, 0), "wing_l": (0, -55, -10), "wing_r": (0, 55, 10), "head": (20, 0, 0), "tail": (-15, 0, 0)},
}

SPEC = dict(
    id="rift_wing", short="淵の羽影", kind="下弦の淵の敵 一品一案 (46 の 4.6)",
    title="淵の羽影 (rift_wing) — 欠けた月の翼の影の飛ぶもの・低ポリ + ドット絵 一案",
    sub=["獲物の上を回り、線の予告 0.7 秒のあと急降下して斬る。倒れると 3 秒前の所から残像で一度立ち上がり、同じ急降下をなぞる。",
         "胴の高さ 1.4・翼の幅 3 ブロック。影の胴に下弦の銀の仮面 (星の目一つ)、三日月の影の翼 (前の縁に銀の骨、後ろは三つに裂ける)、薄れる尾。"],
    labels=["淵の羽影"],
    rows=[("前 3/4", "idle", 212, 12, 400, (0, 22, 0), 7.0),
          ("上から", "glide", 200, 55, 400, (0, 22, 0), 7.0),
          ("急降下", "dive", 240, 6, 400, (0, 22, 0), 7.0),
          ("後ろ 3/4", "idle", 32, 16, 400, (0, 22, 0), 7.0)],
    options={"a": dict(name="下弦の仮面の羽影", desc="影の胴に左の欠けた銀の輪の仮面と星の目一つ。三日月の影の翼に銀の骨、薄れる尾。")},
    extra="月の言葉 (影・銀・星の光)。骨 6 本。",
    budget="小さい敵 400〜800", pw=560,
    later="二段目 = 翼の膜に星の割れ目。三段目 = 翼が二対になり、仮面の輪が満ちる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
