# -*- coding: utf-8 -*-
"""残月の剣守 (waning_blade) 低ポリ 一品一案。下弦の淵の主 (docs/design/46 の 4.6)。
細く背の高い剣士。藍の黒の鎧に銀の縁、兜は顔のない滑らかな面で、頭の左に下弦の月の欠けた前立て (右半分の輪だけ)。
左の肩から背へ長い外套が裂けて流れ、裂けた端は薄れて星の粒になる (残像の名残)。右手に反りの深い三日月の長い刀、
左の腰に空の鞘。胸の真ん中に欠けた月の印 (光る)。3 秒後ろを自分の残像がついてくる (残像は同じ模型を透かして描く)。
骨: root > body (腰) > chest > head / arm_l > fore_l / arm_r > fore_r > sword、cape、leg_l > shin_l / leg_r > shin_r。高さ 3.5 ブロック。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 27, 0)), "chest": ("body", (0, 31, 0)), "head": ("chest", (0, 46, 0)),
         "arm_l": ("chest", (-8.4, 43, 0)), "fore_l": ("arm_l", (-10.4, 33.5, 0.5)),
         "arm_r": ("chest", (8.4, 43, 0)), "fore_r": ("arm_r", (10.4, 33.5, -0.5)), "sword": ("fore_r", (11.0, 25.0, -3.0)),
         "cape": ("chest", (-3, 44, 3.5)),
         "leg_l": ("body", (-3.4, 26, 0)), "shin_l": ("leg_l", (-3.8, 13.5, -0.6)),
         "leg_r": ("body", (3.4, 26, 0)), "shin_r": ("leg_r", (3.8, 13.5, -0.6))}
NIGHT = [(10, 12, 22), (22, 26, 42), (38, 44, 66), (60, 68, 94), (90, 100, 128)]
PAL = make_pal({
    "night": (NIGHT, 0.6, dict(crack=0.06, noise=0.12)),
    "silver": (SILVER, 0.68, dict(crack=0.04, noise=0.12)),
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.06, min=0)),
    "cloth": ([(14, 16, 30), (28, 30, 54), (46, 50, 84), (70, 76, 116)], 0.56, dict(crack=0.0, noise=0.1)),
    "star": (STAR, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "blade": ([(120, 136, 168), (176, 192, 220), (220, 232, 248), (250, 252, 255)], 0.72, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star", "blade"])


def P(*a):
    return np.array(a, float)


def build(k):
    F = []
    # 腰: 細い藍の胴に銀の帯、左の腰に空の鞘
    F += part("waist", ellipsoid("body", "night", (0, 27.5, 0), (5.0, 3.2, 3.6), segs=12, rings=4))
    F += part("belt", ellipsoid("body", "silver", (0, 29.6, 0), (5.3, 0.9, 3.9), segs=12, rings=2))
    F += part("sheath", tube("body", "night", [(-5.6, 29.0, -3.0), (-6.6, 24.0, 1.0), (-7.0, 17.0, 6.0)], ra=[1.1, 1.0, 0.8], sides=6, hint=(1, 0, 0),
                             caps=(True, True)))
    F += part("sheathtip", ellipsoid("body", "silver", (-7.0, 16.8, 6.2), (0.9, 0.9, 0.9), segs=6, rings=3))
    # 腰の垂れ: 前後に細い藍の布の垂れ (前は二枚に裂ける)
    for sx in (-1, 1):
        F += part(f"fauld{sx}", loft("body", "cloth", [
            [(sx * 0.4, 27.0, -3.4), (sx * 4.2, 27.0, -3.0), (sx * 4.2, 27.0, -2.2), (sx * 0.4, 27.0, -2.6)],
            [(sx * 0.6, 15.0, -4.4), (sx * 4.0, 16.0, -3.8), (sx * 4.0, 16.0, -3.0), (sx * 0.6, 15.0, -3.6)]], caps=(True, True)))
    # 後ろと脇の長い垂れ (藍の布、膝の下まで。裾は裂ける)
    sk = []
    for y, r, d in [(28.0, 5.4, 4.0), (20.0, 6.6, 5.0), (11.0, 7.6, 5.8)]:
        o, n = [], []
        for i in range(9):
            a = math.radians(-120 + i * 240 / 8)
            jag = 1.2 if (y < 12 and i % 2) else 0.0
            o.append((math.sin(a) * r, y + jag, math.cos(a) * d))
            n.append((math.sin(a) * (r - 0.6), y + jag, math.cos(a) * (d - 0.6)))
        sk.append(o + n[::-1])
    F += part("skirt", loft("body", "cloth", sk, caps=(True, True)))
    # 胸: 細く高い胸当て (前へ張る)、胸の真ん中に欠けた月の印、肩は小さな銀の肩当て
    F += part("cuirass", ellipsoid("chest", "night", (0, 37.5, -0.3), (6.4, 7.4, 4.4), segs=12, rings=5))
    F += part("ridge", tube("chest", "silver", [(0, 31.0, -4.2), (0, 37.5, -4.9), (0, 43.0, -3.8)], ra=[0.5, 0.7, 0.5], sides=5, hint=(1, 0, 0),
                            caps=(True, True)))
    pts = []
    for i in range(9):
        a = math.radians(30 + i * 210 / 8)
        pts.append((math.sin(a) * 2.2, 38.5 + math.cos(a) * 2.2, -5.0))
    F += part("sigil", tube("chest", "star", pts, ra=[0.2, 0.45, 0.6, 0.7, 0.72, 0.7, 0.6, 0.45, 0.2], rb=0.35, sides=4, hint=(0, 0, 1),
                            caps=(True, True)))
    F += part("collar", tube("chest", "silver", [(0, 44.0, 0), (0, 45.6, 0)], ra=3.2, sides=10, hint=(0, 0, 1), caps=(True, True)))
    for sx in (-1, 1):
        # 肩当て: 三枚の板が外へ垂れる (右は大きく、左は外套の下で小さく)
        big = 1.0 if sx > 0 else 0.75
        for j, (y, r) in enumerate([(44.4, 3.4), (42.6, 3.9), (40.8, 4.2)]):
            F += part(f"pau{sx}{j}", ellipsoid("chest", "night" if j < 2 else "silver", (sx * (8.0 + j * 0.9) * (0.9 + 0.1 * big), y, 0),
                                               (r * 0.85 * big, 1.3, r * big), segs=10, rings=2))
    # 兜: 顔のない滑らかな銀の面 (少し前へ尖る)、後ろは藍の兜、頭の左に下弦の前立て (右へ開いた半輪)
    F += part("helm", ellipsoid("head", "night", (0, 50.0, 0.6), (3.6, 4.4, 3.8), segs=12, rings=5))
    F += part("face", ellipsoid("head", "silver", (0, 49.6, -2.0), (3.0, 3.8, 2.2), segs=10, rings=4))
    F += part("slit", tube("head", "shadow", [(-1.8, 50.4, -4.0), (1.8, 50.4, -4.0)], ra=0.25, sides=4, hint=(0, 1, 0), caps=(True, True)))
    # 下弦の前立て: 頭の左上に立つ大きな半輪 (右へ開く三日月)
    pts = []
    for i in range(12):
        a = math.radians(70 + i * 200 / 11)
        pts.append((-2.0 + math.cos(a) * 4.4, 53.6 + math.sin(a) * 4.4, 0.4))
    F += part("crest", tube("head", "silver", pts, ra=[0.15, 0.5, 0.9, 1.2, 1.4, 1.5, 1.5, 1.4, 1.2, 0.9, 0.5, 0.15], rb=0.55, sides=4,
                            hint=(0, 0, 1), caps=(True, True)))
    # 腕: 細長い藍の籠手、肘に銀
    for sx, nm in ((-1, "l"), (1, "r")):
        sh, el = P(sx * 8.6, 42.6, 0), P(sx * 10.4, 33.5, 0.5 * -sx)
        wr = P(sx * 11.0, 25.6, -2.2) if sx > 0 else P(sx * 9.0, 25.4, 0.6)
        F += part("ua" + nm, tube("arm_" + nm, "night", [sh, el], ra=[2.1, 1.7], sides=7, hint=(0, 0, 1)))
        F += part("elb" + nm, ellipsoid("arm_" + nm, "silver", tuple(el), (1.8, 1.8, 1.8), segs=7, rings=3))
        F += part("fa" + nm, tube("fore_" + nm, "night", [el, wr], ra=[1.7, 2.0], sides=7, hint=(0, 0, 1)))
        F += part("cuff" + nm, tube("fore_" + nm, "silver", [el + (wr - el) * 0.78, el + (wr - el) * 0.92], ra=2.2, sides=7, hint=(0, 0, 1),
                                    caps=(True, True)))
        F += part("hand" + nm, ellipsoid("fore_" + nm, "night", tuple(wr + (wr - el) * 0.12), (1.6, 1.8, 1.6), segs=7, rings=3))
    # 三日月の長い刀: 右手から前下へ。柄は藍に銀の鍔、刀身は反りの深い光る三日月 (長さ 34)
    g0 = P(11.0, 26.4, -1.8)
    F += part("grip", tube("sword", "night", [g0 + P(0, 2.4, 1.0), g0 + P(0, -3.2, -1.2)], ra=0.75, sides=6, hint=(1, 0, 0), caps=(True, True)))
    F += part("pommel", ellipsoid("sword", "silver", tuple(g0 + P(0, 2.9, 1.2)), (0.9, 0.9, 0.9), segs=6, rings=3))
    F += part("guard", ellipsoid("sword", "silver", tuple(g0 + P(0, -3.6, -1.4)), (2.2, 0.5, 2.2), segs=10, rings=2))
    bl = []
    for i in range(12):
        t = i / 11
        bl.append(tuple(g0 + P(0, -4.0 - t * 16.0 + t * t * 8.0, -1.6 - t * 26.0)))
    F += part("blade", tube("sword", "blade", bl, ra=[0.35] + [0.5] * 8 + [0.4, 0.25, 0.05], rb=[1.4] * 9 + [1.0, 0.6, 0.1], sides=4, hint=(1, 0, 0)))
    F += part("edge", tube("sword", "silver", [(p[0], p[1], p[2] - 1.5) for p in bl[1:-1]], ra=0.15, rb=0.15, sides=4, hint=(1, 0, 0), caps=(True, True)))
    # 外套: 左の肩から背へ、裂けて三本の帯に流れ、裾が薄れて星の粒
    for j, (x0, ln, sw) in enumerate([(-7.0, 34.0, -4.0), (-3.5, 38.0, -1.0), (0.5, 32.0, 2.5)]):
        pts = [(x0, 44.0, 3.4), (x0 + sw * 0.3, 32.0, 5.6), (x0 + sw * 0.7, 20.0, 7.6), (x0 + sw, 44.0 - ln, 9.8)]
        F += part(f"strip{j}", tube("cape", "cloth", pts, ra=[3.6, 3.4, 2.8, 0.8], rb=[0.45, 0.4, 0.35, 0.15], sides=4, hint=(0, 0, 1)))
        F += part(f"spark{j}", ellipsoid("cape", "star", (x0 + sw + 0.2, 44.0 - ln - 1.4, 10.2), (0.5, 0.5, 0.5), segs=5, rings=3))
    F += part("mantle", ellipsoid("cape", "cloth", (-3.4, 44.6, 3.2), (6.0, 1.6, 3.0), segs=10, rings=2))
    # 脚: 長い藍の腿、銀の膝、細い脛当て、尖った沓
    for sx, nm in ((-1, "l"), (1, "r")):
        hp, kn, an = P(sx * 3.4, 26, 0), P(sx * 3.8, 13.5, -0.6), P(sx * 3.9, 2.2, 0)
        F += part("th" + nm, tube("leg_" + nm, "night", [hp, kn], ra=[2.5, 1.9], sides=8, hint=(0, 0, 1)))
        F += part("kn" + nm, ellipsoid("leg_" + nm, "silver", tuple(kn + P(0, 0, -1.0)), (1.9, 1.7, 1.4), segs=8, rings=3))
        F += part("sh" + nm, tube("shin_" + nm, "night", [kn, an], ra=[1.9, 1.5], sides=8, hint=(0, 0, 1)))
        F += part("ft" + nm, spike("shin_" + nm, "night", (sx * 3.9, 1.2, 0.6), (0, -0.1, -1), 4.6, 1.6, sides=6, rings=2))
    return F, []


POSES = {
    "idle": {},
    "slash_ready": {"chest": (0, -35, 0), "arm_r": (-30, 0, 60), "fore_r": (-60, 0, 0), "head": (0, 20, 0)},
    "slash": {"chest": (0, 45, 0), "arm_r": (-80, 0, -30), "fore_r": (-10, 0, 0), "head": (0, -20, 0), "cape": (0, -20, 15)},
    "thrust": {"chest": (0, 20, 0), "arm_r": (-90, 0, 0), "fore_r": (0, 0, 0), "sword": (-90, 0, 0), "leg_r": (-30, 0, 0), "shin_r": (20, 0, 0),
               "leg_l": (25, 0, 0)},
}

SPEC = dict(
    id="waning_blade", short="残月の剣守", kind="下弦の淵の主 一品一案 (46 の 4.6)",
    title="残月の剣守 (waning_blade) — 残像と二人で戦う下弦の剣士・低ポリ + ドット絵 一案",
    sub=["3 秒後ろを自分の残像がついてきて同じ技を出す。残像の技が本体に当たると本体が崩れる。三日月の薙ぎ・踏み込みの突き・飛ぶ三日月の刃。",
         "高さ 3.5 ブロック。藍の黒の細い鎧に銀の縁、顔のない銀の面、頭の左に下弦の半輪の前立て。裂けて星に薄れる外套、反りの深い光る刀。"],
    labels=["残月の剣守"],
    rows=[("前 3/4", "idle", 212, 10, 480, (0, 30, 0), 6.0),
          ("横", "idle", 270, 4, 480, (0, 30, 0), 6.0),
          ("薙ぎの構え", "slash_ready", 200, 8, 480, (0, 30, 0), 6.0),
          ("薙ぎ", "slash", 230, 8, 480, (0, 30, 0), 6.0),
          ("突き", "thrust", 250, 6, 480, (0, 30, 0), 6.0),
          ("後ろ 3/4", "idle", 32, 14, 480, (0, 30, 0), 6.0)],
    options={"a": dict(name="下弦の剣士", desc="藍の黒の細い鎧、顔のない銀の面、下弦の半輪の前立て、胸に欠けた月の印。左肩から裂けて星に薄れる外套、反りの深い光る三日月の刀、空の鞘。")},
    extra="月の言葉 (藍の黒・銀・星の光)。骨 15 本。残像は同じ模型を青白く透かして描く。",
    budget="区画の主 1600〜2600", pw=560,
    later="段 2 = 残像が二つ。外套の帯が一本落ちる。段 3 = 面に一筋の割れ目、刀身が二つに分かれて見える。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
