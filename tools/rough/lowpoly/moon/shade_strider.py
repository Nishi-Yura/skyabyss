# -*- coding: utf-8 -*-
"""影の竹馬 (shade_strider) 低ポリ 一品一案。満月の野の敵 (docs/design/46 の 4.5)。
小さな影の胴を、四本のとても長い竹馬の脚が高く持ち上げる歩き手。胴の下に垂れる影の布、胴の前に下を向く
三日月の仮面 (目は星の光の細い一筋)。脚は黒い硝子の節を銀の環が留める。引きでは歩いて高い所から踏みつけ、
満ちでは脚を畳んで影の石の像になる (胴が地面まで下がるのは動きで付ける)。
骨: root > body > head / cloth / leg_fl,leg_fr,leg_bl,leg_br (腿) > shin_* (脛)。高さ 3 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BY = 40.0   # 胴の高さ
LEGS = {"fl": (-1, -1), "fr": (1, -1), "bl": (-1, 1), "br": (1, 1)}


def hip(sx, sz):
    return (sx * 3.2, BY + 0.5, sz * 3.0)


def knee(sx, sz):
    return (sx * 9.5, BY + 9.0, sz * 7.5)


def foot(sx, sz):
    return (sx * 13.0, 0.4, sz * 11.0)


BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, BY, 0)), "head": ("body", (0, BY - 1, -3.5)), "cloth": ("body", (0, BY - 2.5, 0))}
for k, (sx, sz) in LEGS.items():
    BONES["leg_" + k] = ("body", hip(sx, sz))
    BONES["shin_" + k] = ("leg_" + k, knee(sx, sz))

GLASS = [(6, 6, 14), (16, 18, 34), (32, 36, 62), (58, 66, 104), (96, 108, 150)]
PAL = make_pal({
    "glass": (GLASS, 0.56, dict(crack=0.06, noise=0.1)),
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.08, min=0)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "dust": (DUST, 0.64, dict(crack=0.06, noise=0.22)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def build(k):
    F = []
    # 胴: 平たい影の玉に黒い硝子の甲。上に月の砂が薄く積もる
    F += part("torso", ellipsoid("body", "shadow", (0, BY, 0), (4.6, 3.2, 4.8), segs=10, rings=4))
    F += part("shell", ellipsoid("body", "glass", (0, BY + 1.6, 0.4), (4.1, 2.2, 4.3), segs=9, rings=3))
    F += part("dustcap", ellipsoid("body", "dust", (0, BY + 3.2, 0.8), (2.6, 1.0, 2.8), segs=7, rings=2))
    # 仮面: 下を向く三日月の面 (前へ突き出し、角が下へ)
    pts = []
    for i in range(9):
        a = math.radians(-70 + i * 140 / 8)
        pts.append((math.sin(a) * 3.8, BY - 0.8 - math.cos(a) * 3.2 + 2.2, -5.0 - 0.9 * math.cos(a)))
    F += part("mask", tube("head", "silver", pts, ra=[0.6, 1.0, 1.3, 1.45, 1.5, 1.45, 1.3, 1.0, 0.6], rb=0.8, sides=5, hint=(0, 0, 1), caps=(True, True)))
    F += part("face", ellipsoid("head", "shadow", (0, BY - 1.0, -4.0), (2.4, 2.0, 1.4), segs=8, rings=3))
    F += part("eyeline", tube("head", "star", [(-1.5, BY - 0.8, -5.2), (1.5, BY - 0.8, -5.2)], ra=0.32, rb=0.18, sides=4, hint=(0, 1, 0), caps=(True, True)))
    # 胴から垂れる影の布 (裂けた帯 6 本)
    for i in range(6):
        a = math.radians(i * 60 + 30)
        x, z = math.sin(a) * 3.4, math.cos(a) * 3.4
        ln = 9.0 + (i % 3) * 3.0
        F += part(f"tat{i}", tube("cloth", "shadow", [(x, BY - 1.2, z), (x * 1.15, BY - 1.2 - ln * 0.5, z * 1.15), (x * 1.1 + 0.3, BY - 1.2 - ln, z * 1.1)],
                                  ra=[1.3, 0.9, 0.0], rb=[0.35, 0.25, 0.0], sides=4, hint=(1, 0, 0)))
    # 脚: 腿は上へ外へ (膝が胴より高い)、脛は長く下へ。節ごとに銀の環、足先は尖る
    for key, (sx, sz) in LEGS.items():
        h, kn, ft = hip(sx, sz), knee(sx, sz), foot(sx, sz)
        F += part("th" + key, tube("leg_" + key, "glass", [h, lerp(h, kn, 0.5), kn], ra=[1.35, 1.1, 0.9], sides=5, hint=(0, 1, 0)))
        F += part("kn" + key, ellipsoid("leg_" + key, "silver", kn, (1.4, 1.4, 1.4), segs=6, rings=3))
        mid = lerp(kn, ft, 0.45)
        F += part("sh" + key, tube("shin_" + key, "glass", [kn, mid, ft], ra=[0.95, 0.75, 0.0], sides=5, hint=(0, 1, 0)))
        F += part("ring" + key, tube("shin_" + key, "silver", [lerp(kn, ft, 0.42), lerp(kn, ft, 0.48)], ra=0.95, sides=6, hint=(0, 1, 0),
                                     caps=(True, True)))
    return F, []


import os
FA, FB = [float(v) for v in os.environ.get("FOLD", "40,30").split(",")]
FOLD = {}
for key, (sx, sz) in LEGS.items():
    # 腿を立てて膝を胴の真上へ、脛は胴のまわりに縦に揃う (閉じた傘の柱)
    FOLD["leg_" + key] = (sz * FB, 0, sx * FA)
    FOLD["shin_" + key] = (-sz * FB * 1.6, 0, -sx * FA * 1.6)
FOLD["head"] = (25, 0, 0)
FOLD["cloth"] = (0, 0, 0)

STOMP = {"body": (8, 0, 0), "head": (-15, 0, 0), "leg_fl": (-45, 0, 0), "leg_fr": (-45, 0, 0), "shin_fl": (20, 0, 0), "shin_fr": (20, 0, 0)}

POSES = {"idle": {}, "stomp": STOMP, "fold": FOLD}

SPEC = dict(
    id="shade_strider", short="影の竹馬", kind="満月の野の敵 一品一案 (46 の 4.5)",
    title="影の竹馬 (shade_strider) — 長い四本脚の影の歩き手・低ポリ + ドット絵 一案",
    sub=["引きでは高い所から前脚で踏みつける (円の予告)。満ちでは胴を地面まで下ろし、脚を胴のまわりに縦に揃えて影の石の像になる (動きで付ける)。像は傷 ×0.2。",
         "高さ 3 ブロックほど。小さな影の胴に下を向く三日月の仮面 (星の一筋の目)、垂れる影の布。脚は黒い硝子を銀の環が留める。"],
    labels=["影の竹馬"],
    rows=[("前 3/4", "idle", 212, 10, 420, (0, 24, 0), 7.0),
          ("横", "idle", 270, 2, 420, (0, 24, 0), 7.0),
          ("踏みつけ (引き)", "stomp", 222, 10, 420, (0, 24, 0), 7.0),
          ("後ろ 3/4", "idle", 32, 14, 420, (0, 24, 0), 7.0)],
    options={"a": dict(name="竹馬の影", desc="小さな影の胴を四本のとても長い黒硝子の脚が持ち上げる。下を向く三日月の仮面に星の一筋、胴から裂けた影の布が垂れる。")},
    extra="月の言葉 (黒い硝子・影・銀・月の砂・星の光)。骨 12 本。",
    budget="中の敵 600〜1000", pw=560,
    later="二段目 = 脚の節に星の割れ目。三段目 = 脚が六本になり、仮面が満ちて丸くなる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
