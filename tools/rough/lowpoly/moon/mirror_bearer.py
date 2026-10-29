# -*- coding: utf-8 -*-
"""鏡背負い (mirror_bearer) 低ポリ 一品一案。満月の野の敵 (docs/design/46 の 4.5)。
背に丸い銀の鏡 (光を集める皿) を斜めに負う月の亀。鏡の前の細い腕の先に光を集める星の結晶。
満ちでは鏡を前へ傾けて光を集め、線で放つ (背から叩くと止まる)。引きでは頭と脚を殻に引き、前からの傷 ×0.3。
骨: root > body > mirror (背の鏡、傾く) / head / leg_fl,leg_fr,leg_bl,leg_br / tail。長さ 1.7・高さ 1.8 ブロックほど (鏡の上まで)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8, 0)), "mirror": ("body", (0, 14, 3)), "head": ("body", (0, 8, -9)),
         "tail": ("body", (0, 7, 9)),
         "leg_fl": ("body", (-6, 6, -5)), "leg_fr": ("body", (6, 6, -5)), "leg_bl": ("body", (-6, 6, 5)), "leg_br": ("body", (6, 6, 5))}
MIRROR = [(120, 140, 170), (180, 200, 226), (220, 234, 250), (248, 252, 255)]
PAL = make_pal({
    "basalt": (BASALT, 0.6, dict(crack=0.16, noise=0.2)),
    "dust": (DUST, 0.66, dict(crack=0.05, noise=0.26)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "mirror": (MIRROR, 0.7, dict(crack=0.0, noise=0.05)),
    "hollow": (SHADOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])

TILT = math.radians(40)   # 鏡は前 (-Z) 上を向く


def Rm():
    c, s = math.cos(TILT), math.sin(TILT)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def build(k):
    F = []
    # 甲羅: 平たく広い玄武岩のドームに、縁の銀の帯と月の砂の積もり。腹は凝灰の灰
    F += part("shell", ellipsoid("body", "basalt", (0, 8.0, 0), (8.6, 6.4, 9.8), segs=14, rings=5))
    F += part("belly", ellipsoid("body", "dust", (0, 4.6, 0), (7.4, 2.0, 8.6), segs=10, rings=3))
    rim = []
    for i in range(16):
        a = 2 * math.pi * i / 16
        rim.append((math.sin(a) * 8.5, 5.6, math.cos(a) * 9.7))
    rim.append(rim[0])
    F += part("rim", tube("body", "silver", rim, ra=0.7, rb=0.9, sides=4, hint=(0, 1, 0)))
    for i, (x, z) in enumerate([(-4.5, -3), (4.5, -3), (-5, 4), (5, 4), (0, 7)]):
        F += part(f"knob{i}", ellipsoid("body", "basalt", (x, 12.6 - abs(x) * 0.45 - abs(z) * 0.2, z), (2.0, 1.2, 2.0), segs=6, rings=2))
    # 背の鏡: 傾いた丸い皿 (前は鏡、後ろは玄武岩の裏打ち)、縁に銀の環、支える柱
    R = Rm()
    c0 = np.array([0, 18.0, 3.0])
    F += part("mpost", tube("body", "silver", [(0, 13.0, 3.4), (0, 17.4, 3.4)], ra=1.4, sides=6, hint=(1, 0, 0)))
    disc = ellipsoid("mirror", "mirror", (0, 0, 0), (6.8, 6.8, 0.9), segs=14, rings=3, rot=None)
    F += part("mface", transform(disc, R, c0))
    back = ellipsoid("mirror", "basalt", (0, 0, 0.9), (6.4, 6.4, 1.2), segs=12, rings=3)
    F += part("mback", transform(back, R, c0))
    ring = []
    for i in range(18):
        a = 2 * math.pi * i / 18
        ring.append(R @ np.array([math.sin(a) * 6.9, math.cos(a) * 6.9, 0.0]) + c0)
    ring.append(ring[0])
    F += part("mring", tube("mirror", "silver", ring, ra=0.65, sides=4, hint=(0, 0, 1)))
    # 鏡の前の細い腕 (上と下から) と、光を集める星の結晶
    focus = R @ np.array([0, 0, -6.0]) + c0
    for sy in (1, -1):
        root = R @ np.array([0, sy * 6.6, -0.3]) + c0
        F += part(f"arm{sy}", tube("mirror", "silver", [root, (root + focus) / 2 + R @ np.array([0, sy * 0.8, 0]), focus], ra=[0.35, 0.3, 0.25], sides=4, hint=(1, 0, 0)))
    F += part("focus", ellipsoid("mirror", "star", tuple(focus), (1.1, 1.1, 1.1), segs=6, rings=3))
    # 頭: 短い首に、三日月の角の兜の頭。目は星
    F += part("neck", tube("head", "dust", [(0, 6.6, -8.4), (0, 7.4, -10.6)], ra=[2.0, 1.6], sides=6, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "dust", (0, 7.8, -12.0), (2.2, 1.9, 2.5), segs=8, rings=3))
    F += part("beak", spike("head", "basalt", (0, 7.4, -13.8), (0, -0.3, -1), 1.6, 1.0, sides=5, rings=2))
    pts = []
    for i in range(9):
        a = math.radians(-80 + i * 160 / 8)
        pts.append((math.sin(a) * 2.6, 9.4 + (1 - math.cos(a)) * 1.6, -11.6 + math.cos(a) * 0.6))
    F += part("crest", tube("head", "silver", pts, ra=[0.2, 0.4, 0.55, 0.6, 0.6, 0.6, 0.55, 0.4, 0.2], rb=0.5, sides=4, hint=(0, 0, 1), caps=(True, True)))
    # 脚: 太い柱の脚に、銀の爪
    for key, (sx, sz) in {"fl": (-1, -1), "fr": (1, -1), "bl": (-1, 1), "br": (1, 1)}.items():
        b = "leg_" + key
        top = (sx * 6.0, 5.4, sz * 5.0)
        ft = (sx * 7.4, 1.4, sz * 5.8)
        F += part(b, tube(b, "dust", [top, ft], ra=[2.4, 2.2], sides=6, hint=(0, 0, 1)))
        F += part(b + "f", ellipsoid(b, "basalt", (ft[0], 1.0, ft[2]), (2.0, 1.0, 2.0), segs=7, rings=2))
        for j in (-1, 0, 1):
            F += part(f"{b}c{j}", spike(b, "silver", (ft[0] + j * 0.9, 1.0, ft[2] - 1.4), (j * 0.3, -0.4, -1), 1.0, 0.35, sides=4, rings=1))
    F += part("tail", spike("tail", "dust", (0, 7.0, 8.6), (0, -0.4, 1), 3.0, 1.0, sides=5, rings=2))
    return F, [eye_at(-1.25, 8.4, -13.8, 0.42), eye_at(1.25, 8.4, -13.8, 0.42)]


POSES = {
    "idle": {},
    "aim": {"mirror": (-18, 0, 0), "head": (8, 0, 0)},
    "hide": {"head": (-20, 0, 0), "mirror": (-40, 0, 0)},
}

SPEC = dict(
    id="mirror_bearer", short="鏡背負い", kind="満月の野の敵 一品一案 (46 の 4.5)",
    title="鏡背負い (mirror_bearer) — 背に光を集める丸い鏡を負う月の亀・低ポリ + ドット絵 一案",
    sub=["満ちでは鏡を前へ傾けて光を集め、1 秒の線の予告のあと光の線を放つ (背から叩くと止まる)。引きでは頭と脚を殻に引き、前からの傷 ×0.3。",
         "長さ 1.7・鏡の上まで 1.8 ブロック。玄武岩の甲羅に銀の縁、傾いた銀の鏡の前に細い腕で支えた星の結晶 (光の集まる所)。"],
    labels=["鏡背負い"],
    rows=[("前 3/4", "idle", 212, 14, 360, (0, 10, 0), 11.0),
          ("横", "idle", 270, 4, 360, (0, 10, 0), 11.0),
          ("光を集める (満ち)", "aim", 222, 10, 360, (0, 10, 0), 11.0),
          ("殻にこもる (引き)・後ろ 3/4", "hide", 32, 14, 360, (0, 10, 0), 11.0)],
    options={"a": dict(name="鏡の皿の亀", desc="平たい玄武岩の甲羅に、前上へ傾いた丸い銀の鏡。鏡の前に細い腕で星の結晶。頭に三日月の銀の冠。")},
    extra="月の言葉 (玄武岩・月の砂・銀・鏡・星の光)。骨 10 本。",
    budget="中の敵 600〜1000", pw=560,
    later="二段目 = 甲羅に星の割れ目、鏡の縁に星の粒。三段目 = 鏡が二枚 (左右) になり、甲羅に月の欠片の棘。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
