# -*- coding: utf-8 -*-
"""望の灯守 (full_keeper) 低ポリ 一品一案。満月の野の主 (docs/design/46 の 4.5)。
胸の銀の肋の籠に満月の珠を抱いた、背の高い灯台守。頭は角灯の籠 (中に星の火、三日月の鎧戸が目)、
右手に長い灯の杖 (先の鉤に小さな角灯)、裾の広がる月の砂色の長衣は地を擦り、脚は見せない (滑るように動く)。
光溜め (満ちのあいだ珠が光を溜める) → 光放ち (腕を広げて胸を開く)。
骨: root > body (腰) > robe / chest > orb / head / arm_l > fore_l / arm_r > fore_r > staff。高さ 5 ブロックほど。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 30, 0)), "robe": ("body", (0, 30, 0)), "chest": ("body", (0, 34, 0)),
         "orb": ("chest", (0, 43, -1)), "head": ("chest", (0, 56, -1)),
         "arm_l": ("chest", (-11, 52, 0)), "fore_l": ("arm_l", (-15, 38, -2)),
         "arm_r": ("chest", (11, 52, 0)), "fore_r": ("arm_r", (15, 38, -2)), "staff": ("fore_r", (16, 28, -7))}
ROBE = [(60, 64, 76), (104, 108, 120), (148, 150, 160), (190, 190, 196), (222, 220, 222)]
PAL = make_pal({
    "robe": (ROBE, 0.64, dict(crack=0.04, noise=0.22)),
    "tuff": (TUFFM, 0.62, dict(crack=0.14, noise=0.18)),
    "basalt": (BASALT, 0.6, dict(crack=0.16, noise=0.2)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "shadow": (SHADOW, 0.5, dict(crack=0.0, noise=0.08, min=0)),
    "lampglass": ([(16, 24, 48), (34, 52, 92), (60, 90, 146), (100, 140, 200)], 0.55, dict(crack=0.0, noise=0.08, min=1)),
    "moon": ([(150, 170, 200), (200, 216, 240), (234, 242, 255), (255, 255, 255)], 0.8, dict(crack=0.06, noise=0.1, min=1)),
    "star": (STAR, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["star", "moon", "lampglass"])


def P(*a):
    return np.array(a, float)


def build(k):
    F = []
    # 長衣: 裾 (半径 15、裂けた 12 の尖り) から腰 (半径 7.5) へ。月の砂の色、裾に銀の帯
    rings = []
    for y, r, jag in [(0.6, 15.0, 2.4), (8, 12.4, 1.0), (20, 9.4, 0.4), (32, 7.4, 0.1), (38, 6.2, 0.0)]:
        pts = []
        for i in range(24):
            a = 2 * math.pi * i / 24
            rr = r + (jag if i % 2 == 0 else -jag * 0.3)
            pts.append((math.sin(a) * rr, y + (0 if i % 2 == 0 else jag * 0.8), -math.cos(a) * rr * 0.92))
        rings.append(pts)
    F += part("robe", loft("robe", "robe", rings, caps=(True, True)))
    band = []
    for i in range(25):
        a = 2 * math.pi * i / 24
        band.append((math.sin(a) * 13.6, 8.6, -math.cos(a) * 13.6 * 0.92))
    F += part("band", tube("robe", "silver", band, ra=0.7, rb=0.9, sides=4, hint=(0, 1, 0)))
    F += part("sash", ellipsoid("body", "silver", (0, 35.5, 0), (7.4, 1.6, 6.8), segs=12, rings=2))
    F += part("belly", ellipsoid("body", "tuff", (0, 39.0, 1.5), (6.6, 5.0, 5.6), segs=10, rings=3))
    # 胴: 後ろは凝灰の背板、前は銀の肋 6 本が満月の珠を籠のように抱く
    F += part("back", ellipsoid("chest", "robe", (0, 44, 2.6), (9.4, 11.0, 5.0), segs=12, rings=4))
    F += part("collar", ellipsoid("chest", "silver", (0, 54.0, 0.5), (8.2, 2.2, 6.2), segs=12, rings=2))
    # 頭巾: 角灯の後ろと横を包む影の頭巾 (外套の上の続き。上で尖る)
    hood = []
    for y, r, d in [(53, 8.4, 7.0), (60, 7.6, 7.4), (67, 6.6, 6.6), (72, 4.2, 4.6), (75.5, 0.8, 1.2)]:
        o, n = [], []
        for i in range(9):
            a = math.radians(-125 + i * 250 / 8)
            o.append((math.sin(a) * r, y, -1 + math.cos(a) * d))
            n.append((math.sin(a) * max(0.3, r - 1.0), y, -1 + math.cos(a) * max(0.3, d - 1.0)))
        hood.append(o + n[::-1])
    F += part("hood", loft("chest", "shadow", hood, caps=(True, True)))
    F += part("orb", ellipsoid("orb", "moon", (0, 43, -1), (7.0, 7.0, 7.0), segs=12, rings=6))
    for i in range(6):
        sy = 36.5 + i * 2.6
        span = math.sqrt(max(0.0, 1 - ((sy - 43) / 9.0) ** 2))
        w = 9.4 * span + 1.5
        for sx in (-1, 1):
            F += part(f"rib{i}{sx}", tube("chest", "silver", [(sx * 0.6, sy, -8.6 * span - 0.4), (sx * w * 0.75, sy + 0.4, -6.6 * span), (sx * w, sy + 0.8, 0.5)],
                                         ra=[0.45, 0.6, 0.7], sides=4, hint=(0, 1, 0)))
    F += part("sternum", tube("chest", "silver", [(0, 35, -6.6), (0, 43, -8.6), (0, 51, -6.0)], ra=[0.8, 1.0, 0.8], sides=5, hint=(1, 0, 0), caps=(True, True)))
    # 肩: 銀の板を三枚重ねた肩当て (上ほど小さく、外へ垂れる)
    for sx in (-1, 1):
        for j, (y, r) in enumerate([(54.2, 5.2), (52.0, 5.8), (49.8, 6.2)]):
            F += part(f"pad{sx}{j}", ellipsoid("chest", "silver", (sx * (10.6 + j * 0.9), y, 0.5), (r * 0.82, 1.5, r), segs=10, rings=2))
        F += part(f"padgem{sx}", ellipsoid("chest", "moon", (sx * 11.2, 55.6, -1.0), (1.0, 1.0, 1.0), segs=6, rings=3))
    # 背の影の外套: 肩から裾の近くまで、後ろへ広がる厚みのある板 (閉じた形 = 内と外の輪を loft)
    outer, inner = [], []
    for y, r, back in [(54, 9.6, 6.5), (40, 11.6, 8.0), (24, 13.4, 9.5), (6, 15.6, 11.0)]:
        o, n = [], []
        for i in range(9):
            a = math.radians(-100 + i * 200 / 8)
            o.append((math.sin(a) * r, y, back * 0.2 + math.cos(a) * back * 0.8 + 1.0))
            n.append((math.sin(a) * (r - 0.9), y, back * 0.2 + math.cos(a) * (back * 0.8 - 0.9) + 1.0))
        outer.append(o)
        inner.append(n)
    rings2 = [o + n[::-1] for o, n in zip(outer, inner)]
    F += part("cloak", loft("chest", "shadow", rings2, caps=(True, True)))
    # 頭: 六角の角灯の籠 (銀の枠、黒い硝子)、中に星の火、前に三日月の鎧戸 (目)、上に提げ輪
    hx = []
    for y in (56.5, 58, 67, 68.5):
        r = 5.0 if y in (58, 67) else 3.4
        hx.append([(math.sin(math.radians(30 + 60 * i)) * r, y, -1 - math.cos(math.radians(30 + 60 * i)) * r) for i in range(6)])
    F += part("lamp", loft("head", "lampglass", hx, caps=(True, True)))
    for i in range(6):
        a = math.radians(30 + 60 * i)
        F += part(f"post{i}", tube("head", "silver", [(math.sin(a) * 5.1, 57.6, -1 - math.cos(a) * 5.1), (math.sin(a) * 5.1, 67.4, -1 - math.cos(a) * 5.1)],
                                   ra=0.4, sides=4, hint=(0, 0, 1), caps=(True, True)))
    F += part("roof", spike("head", "silver", (0, 68.3, -1), (0, 1, 0), 4.2, 4.0, sides=6, rings=2))
    # 目: 前の面に一つ、上を向く三日月の鎧戸 (星の火が漏れる)
    pts = []
    for i in range(9):
        a = math.radians(-80 + i * 160 / 8)
        pts.append((math.sin(a) * 3.3, 62.4 - math.cos(a) * 2.4, -5.5))
    F += part("eye", tube("head", "star", pts, ra=[0.4, 0.8, 1.15, 1.35, 1.4, 1.35, 1.15, 0.8, 0.4], rb=0.4, sides=4, hint=(0, 0, 1), caps=(True, True)))
    # 腕: 長く細い。左は珠の下で手のひらを上に、右は灯の杖を握る
    for sx, nm in ((-1, "l"), (1, "r")):
        sh, el = P(sx * 12.5, 51, 0), P(sx * 15, 38, -2)
        wr = P(sx * 13.5, 29, -6) if sx < 0 else P(sx * 16, 28, -7)
        F += part("ua" + nm, tube("arm_" + nm, "robe", [sh, el], ra=[2.8, 2.3], sides=7, hint=(0, 0, 1)))
        F += part("el" + nm, ellipsoid("arm_" + nm, "robe", el, (2.3, 2.3, 2.3), segs=7, rings=3))
        # 袖: 肘から手首へ鐘のように広がり、手首の先で銀の縁。中から細い銀の指が三本
        F += part("fa" + nm, tube("fore_" + nm, "robe", [el, el + (wr - el) * 0.5, el + (wr - el) * 0.95], ra=[2.1, 3.0, 4.6], sides=8, hint=(0, 0, 1), caps=(False, True)))
        F += part("cuff" + nm, tube("fore_" + nm, "silver", [el + (wr - el) * 0.86, el + (wr - el) * 0.97], ra=4.8, sides=8, hint=(0, 0, 1), caps=(True, True)))
        F += part("palm" + nm, ellipsoid("fore_" + nm, "basalt", (wr[0], wr[1] - 0.6, wr[2]), (1.8, 1.6, 1.8), segs=7, rings=3))
        d = (wr - el) / np.linalg.norm(wr - el)
        for j, off in enumerate((-1.1, 0.0, 1.1)):
            base = wr + P(off, -1.2, 0)
            tip = base + d * 3.2 + P(off * 0.6, -0.6, -0.4)
            F += part(f"fin{nm}{j}", tube("fore_" + nm, "silver", [base, base + (tip - base) * 0.55, tip], ra=[0.55, 0.45, 0.0], sides=4, hint=(0, 0, 1)))
    # 灯の杖: 地から頭の上まで (長さ 76)、先は三日月の鉤に小さな角灯
    sx0 = 16.0
    F += part("shaft", tube("staff", "basalt", [(sx0, 1.0, -7), (sx0, 40, -7), (sx0, 76, -7)], ra=[0.9, 1.0, 0.9], sides=6, hint=(0, 0, 1), caps=(True, False)))
    F += part("grip", tube("staff", "silver", [(sx0, 24, -7), (sx0, 31, -7)], ra=1.3, sides=6, hint=(0, 0, 1), caps=(True, True)))
    hook = []
    for i in range(10):
        a = math.radians(i * 200 / 9)
        hook.append((sx0 - 4.0 + math.cos(a) * 4.0, 76 + math.sin(a) * 4.0, -7))
    F += part("hook", tube("staff", "silver", hook, ra=[0.9] * 7 + [0.7, 0.5, 0.3], sides=4, hint=(0, 0, 1)))
    F += part("chain", tube("staff", "silver", [(sx0 - 8.0, 76.2, -7), (sx0 - 8.0, 71.5, -7)], ra=0.25, sides=4, hint=(1, 0, 0)))
    small = []
    for y, r in [(65.5, 1.4), (66.5, 2.0), (70, 2.0), (71, 1.4)]:
        small.append([(sx0 - 8.0 + math.sin(math.radians(45 + 90 * i)) * r, y, -7 - math.cos(math.radians(45 + 90 * i)) * r) for i in range(4)])
    F += part("slamp", loft("staff", "star", small, caps=(True, True)))
    return F, []


POSES = {
    "idle": {},
    "charge": {"arm_l": (-30, 0, 18), "fore_l": (-40, 0, 30), "arm_r": (-10, 0, -6), "head": (-14, 0, 0), "chest": (-6, 0, 0)},
    "release": {"arm_l": (0, 0, -55), "fore_l": (0, 0, -20), "arm_r": (0, 0, 40), "fore_r": (0, 0, 10), "chest": (10, 0, 0), "head": (16, 0, 0)},
    "sweep": {"arm_r": (-60, -40, 20), "fore_r": (-30, 0, 0), "chest": (0, 25, 0), "head": (0, 10, 0)},
}

SPEC = dict(
    id="full_keeper", short="望の灯守", kind="満月の野の主 一品一案 (46 の 4.5)",
    title="望の灯守 (full_keeper) — 胸に満月の珠を抱く灯台守・低ポリ + ドット絵 一案",
    sub=["満ちのあいだ胸の珠に光を溜め (傷 ×0.35)、溜まりきると腕を広げて胸を開き、主の間じゅうに光の波 (予告 2 秒)。影の柱の陰か、予告中に一瞬止めで崩れる。",
         "高さ 5 ブロック。角灯の籠の頭 (星の火、三日月の鎧戸の目)、銀の肋の籠の満月の珠、背に影の外套、右手に灯の杖。脚は見せず長衣で滑る。"],
    labels=["望の灯守"],
    rows=[("前 3/4", "idle", 212, 10, 480, (0, 38, 0), 5.0),
          ("横", "idle", 270, 4, 480, (0, 38, 0), 5.0),
          ("光溜め", "charge", 200, 8, 480, (0, 38, 0), 5.0),
          ("光放ち", "release", 180, 6, 480, (0, 38, 0), 5.0),
          ("灯の薙ぎ", "sweep", 230, 10, 480, (0, 38, 0), 5.0),
          ("後ろ 3/4", "idle", 32, 14, 480, (0, 38, 0), 5.0)],
    options={"a": dict(name="珠を抱く灯台守", desc="角灯の頭、銀の肋の籠に満月の珠、月の砂色の長衣と影の外套、三日月の鉤の灯の杖。")},
    extra="月の言葉 (月の砂の衣・凝灰・銀・黒い硝子・満月の光)。骨 12 本。",
    budget="区画の主 1200〜2400", pw=560,
    later="段 2 = 珠にひびが入り、外套の裾が光に焼けて裂ける。段 3 = 籠の肋が開いたまま、頭の火が満月の白に。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
