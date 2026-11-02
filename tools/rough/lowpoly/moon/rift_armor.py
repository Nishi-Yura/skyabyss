# -*- coding: utf-8 -*-
"""残り鎧 (rift_armor) 低ポリ 一品一案。下弦の淵の敵 (docs/design/46 の 4.6)。
前の巡礼の空の鎧に月の砂が詰まって歩くもの。中身はない: 兜の目の隙間と鎧の継ぎ目から青白い月の砂がこぼれ、
首と手首の奥は暗い。色あせた巡礼の陣羽織 (前の時代の布) を鎧の上に掛け、両手で三日月の刃の長柄を構える。
骨: root > body (腰) > chest > head / arm_l > fore_l / arm_r > fore_r > glaive、leg_l > shin_l / leg_r > shin_r。高さ 2.3 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 18, 0)), "chest": ("body", (0, 21, 0)), "head": ("chest", (0, 31, 0)),
         "arm_l": ("chest", (-6.4, 29, 0)), "fore_l": ("arm_l", (-7.4, 22.5, -0.5)),
         "arm_r": ("chest", (6.4, 29, 0)), "fore_r": ("arm_r", (7.4, 22.5, -0.5)), "glaive": ("fore_r", (5.0, 17.0, -4.5)),
         "leg_l": ("body", (-2.6, 17, 0)), "shin_l": ("leg_l", (-2.8, 9, -0.4)),
         "leg_r": ("body", (2.6, 17, 0)), "shin_r": ("leg_r", (2.8, 9, -0.4))}
IRON = [(40, 44, 54), (72, 78, 92), (108, 116, 130), (150, 158, 170), (188, 194, 204)]
PAL = make_pal({
    "iron": (IRON, 0.62, dict(crack=0.1, noise=0.16)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "faded": (FADED, 0.62, dict(crack=0.02, noise=0.12)),
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.06, min=0)),
    "sand": ([(120, 140, 176), (170, 190, 222), (214, 228, 248), (244, 248, 255)], 0.72, dict(crack=0.0, noise=0.18, min=1)),
    "wood": ([(34, 30, 30), (58, 50, 48), (84, 74, 68)], 0.6, dict(crack=0.06, noise=0.14)),
}, glow=["sand"])


def P(*a):
    return np.array(a, float)


def build(k):
    F = []
    # 腰と草摺: 鉄の帯に色あせた布の前垂れ二枚
    F += part("hips", ellipsoid("body", "iron", (0, 18, 0), (4.6, 2.6, 3.2), segs=10, rings=3))
    for sx in (-1, 1):
        F += part(f"tasset{sx}", loft("body", "iron", [
            [(sx * 1.0, 18.5, -3.4), (sx * 5.0, 18.5, -2.6), (sx * 5.2, 18.5, 2.6), (sx * 1.0, 18.5, 3.4)],
            [(sx * 1.2, 13.0, -3.8), (sx * 5.8, 13.0, -3.0), (sx * 6.0, 13.0, 3.0), (sx * 1.2, 13.0, 3.8)]], caps=(True, True)))
    # 胸当て: 前へ張った鉄の胴、上に色あせた巡礼の陣羽織 (肩から膝まで、前は開く)
    F += part("cuirass", ellipsoid("chest", "iron", (0, 25.5, -0.2), (5.4, 5.6, 3.8), segs=12, rings=5))
    F += part("gorget", tube("chest", "silver", [(0, 29.8, 0), (0, 31.0, 0)], ra=3.0, sides=8, hint=(0, 0, 1), caps=(True, True)))
    F += part("neckhole", ellipsoid("chest", "shadow", (0, 31.1, 0), (2.2, 0.6, 2.0), segs=8, rings=2))
    tab = []
    for y, r, d in [(30, 6.2, 4.2), (24, 6.4, 4.6), (16, 6.6, 4.8), (11, 6.9, 5.0)]:
        o, n = [], []
        for i in range(9):
            a = math.radians(-105 + i * 210 / 8)
            o.append((math.sin(a) * r, y, math.cos(a) * d))
            n.append((math.sin(a) * (r - 0.6), y, math.cos(a) * (d - 0.6)))
        tab.append(o + n[::-1])
    F += part("tabard", loft("chest", "faded", tab, caps=(True, True)))
    # 胸の継ぎ目から砂がこぼれる (光る砂の筋)
    F += part("seam", tube("chest", "sand", [(0.8, 28.6, -4.0), (1.3, 25.0, -4.4), (0.9, 21.6, -4.0)], ra=[0.35, 0.5, 0.3], sides=4, hint=(1, 0, 0),
                           caps=(True, True)))
    # 兜: 丸い鉄の兜に細い目の隙間 (中で砂が光る)、頂に折れた三日月の前立て
    F += part("helm", ellipsoid("head", "iron", (0, 34.6, 0), (3.4, 3.8, 3.6), segs=12, rings=5))
    F += part("visor", tube("head", "sand", [(-2.0, 34.8, -3.3), (2.0, 34.8, -3.3)], ra=0.38, rb=0.22, sides=4, hint=(0, 1, 0), caps=(True, True)))
    F += part("visorrim", tube("head", "silver", [(-2.6, 34.0, -3.2), (2.6, 34.0, -3.2)], ra=0.35, sides=4, hint=(0, 1, 0), caps=(True, True)))
    pts = [(-2.6, 38.0, -0.4), (-1.6, 39.4, -0.4), (0, 40.0, -0.4), (1.0, 39.8, -0.4)]
    F += part("crest", tube("head", "silver", pts, ra=[0.55, 0.65, 0.6, 0.15], rb=0.25, sides=4, hint=(0, 0, 1)))
    F += part("spill", ellipsoid("head", "sand", (0.4, 32.8, -3.0), (0.5, 0.9, 0.4), segs=5, rings=3))
    # 肩当てと腕: 鉄の肩当て、腕は細い鉄の筒、手首の奥は暗く、鉄の籠手
    for sx, nm in ((-1, "l"), (1, "r")):
        F += part("pau" + nm, ellipsoid("chest", "iron", (sx * 6.2, 29.2, 0), (2.8, 2.2, 3.2), segs=9, rings=3))
        F += part("paurim" + nm, ellipsoid("chest", "silver", (sx * 6.6, 28.0, 0), (2.9, 0.7, 3.3), segs=9, rings=2))
        sh, el = P(sx * 6.6, 28.4, 0), P(sx * 7.4, 22.5, -0.5)
        wr = P(sx * 6.0, 17.6, -4.2) if sx > 0 else P(sx * 2.0, 18.4, -5.2)
        F += part("ua" + nm, tube("arm_" + nm, "iron", [sh, el], ra=[1.6, 1.4], sides=6, hint=(0, 0, 1)))
        F += part("elb" + nm, ellipsoid("arm_" + nm, "silver", el, (1.6, 1.6, 1.6), segs=7, rings=3))
        F += part("fa" + nm, tube("fore_" + nm, "iron", [el, wr], ra=[1.4, 1.6], sides=6, hint=(0, 0, 1)))
        F += part("gaunt" + nm, ellipsoid("fore_" + nm, "iron", tuple(wr + (wr - el) * 0.12), (1.5, 1.6, 1.5), segs=7, rings=3))
    # 三日月の長柄: 黒い柄 (左手で中ほど、右手で下を握る)、上に三日月の刃、下に石突き
    base, tip = P(4.0, 8.0, 2.4), P(-3.6, 40.0, -10.6)
    d = (tip - base) / np.linalg.norm(tip - base)
    F += part("shaft", tube("glaive", "wood", [base, (base + tip) / 2, tip], ra=[0.6, 0.7, 0.6], sides=6, hint=(1, 0, 0), caps=(True, True)))
    F += part("ferrule", spike("glaive", "silver", tuple(base), tuple(-d), 2.0, 0.7, sides=5, rings=1))
    F += part("collar", tube("glaive", "silver", [tip - d * 1.2, tip + d * 0.4], ra=0.95, sides=6, hint=(1, 0, 0), caps=(True, True)))
    blade = []
    for i in range(9):
        a = math.radians(-20 + i * 160 / 8)
        blade.append(tuple(tip + P(math.sin(a) * 5.0 - 3.2, math.cos(a) * 5.0 - 2.0, 0) * 1.0 + P(0, 0, -1.0)))
    F += part("blade", tube("glaive", "silver", blade, ra=[0.2, 0.7, 1.1, 1.3, 1.35, 1.3, 1.1, 0.7, 0.15], rb=0.3, sides=4, hint=(0, 0, 1)))
    # 脚: 鉄の腿と脛当て、足は鉄の沓
    for sx, nm in ((-1, "l"), (1, "r")):
        hp, kn, an = P(sx * 2.6, 17, 0), P(sx * 2.8, 9, -0.4), P(sx * 2.9, 1.6, 0)
        F += part("th" + nm, tube("leg_" + nm, "iron", [hp, kn], ra=[2.0, 1.6], sides=7, hint=(0, 0, 1)))
        F += part("kn" + nm, ellipsoid("leg_" + nm, "silver", tuple(kn + P(0, 0, -0.8)), (1.6, 1.5, 1.2), segs=7, rings=3))
        F += part("sh" + nm, tube("shin_" + nm, "iron", [kn, an], ra=[1.6, 1.4], sides=7, hint=(0, 0, 1)))
        F += part("ft" + nm, ellipsoid("shin_" + nm, "iron", (sx * 2.9, 1.0, -1.2), (1.8, 1.0, 3.0), segs=8, rings=2))
    return F, []


POSES = {
    "idle": {},
    "windup": {"chest": (0, -30, 0), "arm_r": (-60, 0, 30), "fore_r": (-40, 0, 0), "arm_l": (-40, 0, -10), "head": (0, 15, 0)},
    "swing": {"chest": (0, 40, 0), "arm_r": (-70, 0, -20), "fore_r": (-10, 0, 0), "arm_l": (-50, 0, 10), "head": (0, -15, 0)},
}

SPEC = dict(
    id="rift_armor", short="残り鎧", kind="下弦の淵の敵 一品一案 (46 の 4.6)",
    title="残り鎧 (rift_armor) — 月の砂が詰まった前の巡礼の空の鎧・低ポリ + ドット絵 一案",
    sub=["予告つきの大振り (前 120 度、0.9 秒)。前からの傷 ×0.5。倒れると 3 秒前の所から残像で一度立ち上がる。",
         "高さ 2.3 ブロック。中身のない鉄の鎧、兜の目の隙間と胸の継ぎ目から光る月の砂。色あせた巡礼の陣羽織、三日月の刃の長柄。"],
    labels=["残り鎧"],
    rows=[("前 3/4", "idle", 212, 10, 440, (0, 21, 0), 9.0),
          ("横", "idle", 270, 4, 440, (0, 21, 0), 9.0),
          ("構え", "windup", 200, 8, 440, (0, 21, 0), 9.0),
          ("大振り", "swing", 230, 8, 440, (0, 21, 0), 9.0),
          ("後ろ 3/4", "idle", 32, 14, 440, (0, 21, 0), 9.0)],
    options={"a": dict(name="砂の詰まった巡礼の鎧", desc="中身のない鉄の鎧に色あせた陣羽織。兜の隙間と継ぎ目から光る月の砂、折れた三日月の前立て、三日月の長柄。")},
    extra="月の言葉 (鉄・銀・前の時代の布・光る月の砂)。骨 14 本。",
    budget="中の敵 800〜1400", pw=560,
    later="二段目 = 陣羽織が裂け、砂のこぼれが増える。三段目 = 兜が割れて中の砂が星の光に。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
