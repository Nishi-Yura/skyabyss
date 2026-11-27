# -*- coding: utf-8 -*-
"""技の床の印: 真ん中から枝分かれして走る光る割れ目 (溶岩・雷・氷など) の絵を作る。

床の印は AttackPropRenderer が絵の幅に合わせて広げる (32px = 半径 1.7、64px 以上 = 半径 3.4。128px は 64px と同じ広さで細かさが 2 倍。どれも技の大きさ倍)。
  python -I tools/gen/pixel/decal_cracks.py <出力.png> [--size 64] [--seed 3] [--palette ember|frost] [--reach 1.0]
"""
import argparse
import math
import random

from PIL import Image

PALETTES = {
    # 芯 → 縁 → 外の縁 → 焦げ
    "ember": [(255, 244, 170), (255, 186, 52), (226, 92, 20), (60, 22, 12)],
    "frost": [(244, 253, 255), (150, 225, 255), (60, 150, 230), (205, 235, 250)],
    "thunder": [(255, 250, 222), (250, 226, 120), (150, 110, 240), (40, 30, 62)],
    "star": [(255, 250, 226), (255, 212, 110), (96, 96, 214), (28, 26, 64)],
    "titan": [(236, 255, 252), (110, 236, 222), (36, 136, 150), (30, 38, 46)],
    "earth": [(255, 240, 196), (244, 184, 84), (168, 100, 44), (58, 44, 32)],
    "tide": [(240, 252, 255), (130, 206, 244), (44, 118, 184), (30, 52, 76)],
    "rush": [(255, 246, 214), (255, 200, 90), (196, 70, 52), (62, 40, 30)],
    "wind": [(244, 255, 250), (160, 244, 206), (40, 160, 130), (28, 54, 48)],
    "bone": [(252, 244, 255), (204, 156, 255), (130, 80, 210), (52, 40, 64)],
    "bash": [(255, 252, 240), (255, 160, 64), (200, 64, 30), (44, 42, 50)],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--size", type=int, default=128)
    ap.add_argument("--seed", type=int, default=3)
    ap.add_argument("--palette", default="ember")
    ap.add_argument("--reach", type=float, default=1.0, help="ひびの届く広さ (絵の半径の割合)。大きな技で線を細く保つとき 0.5 など")
    ap.add_argument("--shape", default="burst", help="burst = 真ん中から 8 本・輪 / fissure = 前 (絵の下、模型の -z) へ一直線の地割れ / rings = 割れた同心の輪と、輪をつなぐ短いひび / swirl = 真ん中から外へ巻く渦の腕 3 本 (ぬれた跡) / skid = 前 (絵の下) へ並んで走る 2 本の擦り跡と、そのあいだの焦げ")
    ap.add_argument("--core", type=float, default=1.7, help="fissure の主な割れ目の芯の太さ (画素)")
    ap.add_argument("--points", help="主な割れ目の通り道 (画素の x y) を JSON に書く。模型の炎をひびに沿って置くとき用")
    a = ap.parse_args()
    n, rnd = a.size, random.Random(a.seed)
    core, mid, rim, scorch = PALETTES[a.palette]
    c = (n - 1) / 2
    cr = c * a.reach  # ひびの届く半径 (画素)
    level = [[0] * n for _ in range(n)]  # 0 なし, 1 焦げ, 2 外の縁, 3 縁, 4 芯

    def put(x, y, v):
        if 0 <= x < n and 0 <= y < n and level[y][x] < v:
            level[y][x] = v

    paths = []

    def crack(x, y, ang, length, core_r, depth, wander=0.38, branch=0.03, taper=0.0):
        """割れ目 1 本。芯 (半径 core_r) のまわりに縁 1px・外の縁 1px。taper = 先ほど細る割合"""
        base = ang
        path = []
        paths.append(path)
        r0 = core_r
        for i in range(int(length)):
            core_r = r0 * (1 - taper * i / max(1, length))
            # ジグザグに折れつつ、元の向き (外向き) へ戻る
            ang = ang * 0.8 + base * 0.2 + rnd.uniform(-wander, wander)
            x += math.cos(ang)
            y += math.sin(ang)
            path.append([round(x, 1), round(y, 1)])
            for dx in range(-4, 5):
                for dy in range(-4, 5):
                    d = math.hypot(int(round(x)) + dx - x, int(round(y)) + dy - y)
                    v = 4 if d <= core_r else (3 if d <= core_r + 1 else (2 if d <= core_r + 2 else 0))
                    if v:
                        put(int(round(x)) + dx, int(round(y)) + dy, v)
            if depth < 2 and rnd.random() < branch:
                crack(x, y, ang + rnd.choice([-1, 1]) * rnd.uniform(0.5, 1.0), length * 0.4, max(0.0, core_r - 0.6), depth + 1)
            if math.hypot(x - c, y - c) > cr - 2:
                break

    if a.shape == "fissure":
        fissure(n, c, cr, rnd, put, crack, paths, a.core)
    elif a.shape == "rings":
        rings(n, c, cr, rnd, put, crack)
    elif a.shape == "swirl":
        swirl(n, c, cr, rnd, put)
    elif a.shape == "skid":
        skid(n, c, cr, rnd, put)
    elif a.shape == "cuts":
        cuts(n, c, cr, rnd, put)
    elif a.shape == "arc":
        arc(n, c, cr, rnd, put, crack)
    elif a.shape == "wings":
        wings(n, c, cr, rnd, put, crack)
    elif a.shape == "fan":
        fan(n, c, cr, rnd, put, crack)
    else:
        burst(n, c, cr, rnd, put, crack, a)
    finish(n, level, core, mid, rim, scorch, a, paths)


def fissure(n, c, cr, rnd, put, crack, paths, core=1.7):
    """前へ一直線の地割れ (巨神斬り)。最初の道が主な割れ目。刺さった所 (真ん中から前へ 0.35) に短いひびの星"""
    y0 = c + n * 0.09
    crack(c, y0, math.pi / 2, (n - 1 - y0) - 4, core, 0, wander=0.2, branch=0.07, taper=0.55)
    main = [p[:] for p in paths[0]]
    iy = c + n * 0.17
    for i in range(7):
        ang = i * math.tau / 7 + rnd.uniform(-0.3, 0.3)
        crack(c, iy, ang, n * rnd.uniform(0.06, 0.1), 0.6, 2, wander=0.3, branch=0.0)
    # 焦げ: 割れ目に沿って細く、刺さった所のまわりは丸く
    for y in range(n):
        for x in range(n):
            d = min(math.hypot(x - px, y - py) for px, py in main[::3]) if y > y0 - 12 else 99
            f = max(0.0, (y - y0) / (n - y0))
            if d < 9 - 5 * f + rnd.uniform(-1.5, 1.5) or math.hypot(x - c, y - iy) < n * 0.07 + rnd.uniform(-2, 2):
                put(x, y, 1)


def cuts(n, c, cr, rnd, put):
    """回し斬りの跡: 半径 cr の 0.58 の輪に沿う接線の斬り跡 14 本 (両端ほど細い刃の形、半径を少しずつずらす)。輪の内に薄い焦げ"""
    rr = cr * 0.58
    for i in range(14):
        a0 = i * 360 / 14 + rnd.uniform(-6, 6)
        span = rnd.uniform(26, 40)
        r0 = rr * rnd.uniform(0.82, 1.18)
        steps = int(span * 2)
        for j in range(steps):
            f = j / steps
            a = math.radians(a0 + span * f)
            r = r0 + (f - 0.5) * rr * 0.12
            x, y = c + r * math.sin(a), c + r * math.cos(a)
            w = 0.4 + 3.4 * math.sin(math.pi * f)
            for dx in range(-4, 5):
                for dy in range(-4, 5):
                    d = math.hypot(dx, dy)
                    v = 4 if d <= w * 0.5 else 3 if d <= w * 0.5 + 1 else 2 if d <= w * 0.5 + 1.8 else 0
                    if v:
                        put(int(round(x)) + dx, int(round(y)) + dy, v)
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / rr
            if 0.75 < d < 1.25 and rnd.random() < 0.25:
                put(x, y, 1)


def arc(n, c, cr, rnd, put, crack):
    """鯨骨の振りの跡: 体の前を左後ろから右前へ半周する擦り溝 3 本 (半径 cr の 0.55、頭の側ほど太い)。
    頭骨が打ち込まれた所 (右前) にひびの星。角 φ は前 (絵の下) から +x へ"""
    rr = cr * 0.55  # 擦り溝の半径 (ひびは cr まで届く)

    def at(ph, r):
        a = math.radians(ph)
        return c - r * math.sin(a), c + r * math.cos(a)  # 床は模型と 180 度回るので、模型の +x = 絵の左
    for off, wmax in ((0, 2.6), (-rr * 0.12, 1.2), (rr * 0.12, 1.0)):
        ph = -110.0
        while ph < 54:
            f = (ph + 110) / 164
            x, y = at(ph, rr + off)
            w = 0.4 + wmax * f
            if rnd.random() > 0.04:
                for dx in range(-4, 5):
                    for dy in range(-4, 5):
                        d = math.hypot(dx, dy)
                        v = 4 if d <= w * 0.5 else 3 if d <= w * 0.5 + 1 else 2 if d <= w * 0.5 + 1.8 else 0
                        if v:
                            put(int(round(x)) + dx, int(round(y)) + dy, v)
            ph += 0.6
    hx, hy = at(54, rr)
    for i in range(8):
        crack(hx, hy, i * math.tau / 8 + rnd.uniform(-0.3, 0.3), rr * rnd.uniform(0.3, 0.55), 0.9, 1, wander=0.35, branch=0.05, taper=0.6)
    for y in range(n):
        for x in range(n):
            if math.hypot(x - hx, y - hy) < rr * 0.22 + rnd.uniform(-1.5, 1.5):
                put(x, y, 1)


def wings(n, c, cr, rnd, put, crack):
    """盾殴りの当たり (盾の踏み込みの擦り跡と重ねない): 真ん中の帯 (左右 0.25 cr) を空けて、左右の一点から外へ扇 (前寄り、左右 35 度) に開くひび 5 本ずつ。
    起点のまわりは丸く焦げる"""
    for s in (1, -1):
        x0, y0 = c + s * cr * 0.3, c + cr * 0.12
        base = 0.35 if s > 0 else math.pi - 0.35
        for i in range(5):
            ang = base + math.radians(-35 + i * 70 / 4 + rnd.uniform(-5, 5))
            crack(x0, y0, ang, cr * rnd.uniform(0.45, 0.7), 1.0 if i % 2 == 0 else 0.6, 1, wander=0.3, branch=0.04, taper=0.5)
        for y in range(n):
            for x in range(n):
                if math.hypot(x - x0, y - y0) < cr * 0.13 + rnd.uniform(-1.2, 1.2):
                    put(x, y, 1)


def fan(n, c, cr, rnd, put, crack):
    """盾殴りの当たり: 少し前 (絵の下) の一点から、前へ扇 (左右 40 度) に開くひび 7 本。当たりのまわりは丸く焦げる"""
    y0 = c + cr * 0.15
    for i in range(7):
        ang = math.pi / 2 + math.radians(-40 + i * 80 / 6 + rnd.uniform(-5, 5))
        crack(c, y0, ang, cr * rnd.uniform(0.6, 0.95), 1.1 if i % 2 == 0 else 0.6, 1, wander=0.3, branch=0.04, taper=0.5)
    for y in range(n):
        for x in range(n):
            if math.hypot(x - c, y - y0) < cr * 0.18 + rnd.uniform(-1.5, 1.5):
                put(x, y, 1)


def skid(n, c, cr, rnd, put):
    """踏み込みの擦り跡: 真ん中の少し前から前 (絵の下) の端まで、左右 2 本。前ほど太く、途中で跳ねて切れる。あいだは焦げ"""
    y0 = c + cr * 0.08
    for side in (-1, 1):
        x = c + side * cr * 0.1
        y = y0
        while y < n - 2:
            f = (y - y0) / (n - 2 - y0)
            x += rnd.uniform(-0.25, 0.25) + side * 0.02
            w = 0.8 + 2.2 * f
            if rnd.random() > 0.03:  # 跳ねて少し切れる
                for dx in range(-4, 5):
                    d = abs(dx)
                    v = 4 if d <= w * 0.5 else 3 if d <= w * 0.5 + 1 else 2 if d <= w * 0.5 + 2 else 0
                    if v:
                        put(int(round(x)) + dx, int(y), v)
            y += 1
    for y in range(int(y0), n):
        for x in range(n):
            if abs(x - c) < cr * 0.1 + 2 and rnd.random() < 0.5:
                put(x, y, 1)


def swirl(n, c, cr, rnd, put):
    """渦: 腕 3 本が真ん中から外へ巻く (外ほど太い)。腕のあいだにぬれた跡のまだら"""
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / cr
            if 0.2 < d < 0.9 and rnd.random() < 0.35 * (1 - abs(d - 0.55) / 0.35):
                put(x, y, 1)
    for arm in range(3):
        base = arm * math.tau / 3 + rnd.uniform(-0.2, 0.2)
        steps = int(cr * 6)
        for i in range(steps):
            f = i / steps
            rad = cr * (0.12 + 0.8 * f)
            ang = base + f * 2.6
            w = 0.6 + 2.4 * f
            # 途中で切れる (しぶきの跡)
            if 0.55 < f < 0.6 or 0.8 < f < 0.84:
                continue
            x, y = c + math.cos(ang) * rad, c + math.sin(ang) * rad
            for dx in range(-5, 6):
                for dy in range(-5, 6):
                    dd = math.hypot(dx, dy)
                    v = 4 if dd <= w * 0.5 else 3 if dd <= w * 0.5 + 1 else 2 if dd <= w * 0.5 + 2 else 0
                    if v:
                        put(int(round(x)) + dx, int(round(y)) + dy, v)


def rings(n, c, cr, rnd, put, crack):
    """盛り上がった地面の割れた輪 3 重 (内ほど太い) と、輪から輪へ外向きに走る短いひび。焦げは輪のあいだにまだら"""
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / cr
            if d < 0.95 and (0.3 < d < 0.5 or 0.62 < d < 0.75) and rnd.random() < 0.55:
                put(x, y, 1)
    for k, (f, w) in enumerate(((0.28, 2.2), (0.56, 1.6), (0.86, 1.0))):
        r0 = cr * f
        steps = int(math.tau * r0 * 2)
        phase = rnd.uniform(0, math.tau)
        for i in range(steps):
            t = phase + i / (r0 * 2)
            # ところどころ切れる (割れて持ち上がった縁)
            if (i // max(6, int(r0 * 0.5))) % 4 == 3:
                continue
            wob = r0 + math.sin(t * 5 + k) * 1.2
            for dr in range(-3, 4):
                v = 4 if abs(dr) <= w * 0.5 else 3 if abs(dr) <= w * 0.5 + 1 else 2 if abs(dr) <= w * 0.5 + 2 else 0
                if v:
                    put(int(round(c + math.cos(t) * (wob + dr))), int(round(c + math.sin(t) * (wob + dr))), v)
    for i in range(10):
        ang = i * math.tau / 10 + rnd.uniform(-0.2, 0.2)
        f0 = 0.28 if i % 2 == 0 else 0.56
        crack(c + math.cos(ang) * cr * f0, c + math.sin(ang) * cr * f0, ang, cr * 0.3, 0.7, 2, wander=0.3, branch=0.0)


def burst(n, c, cr, rnd, put, crack, a):
    # 焦げ: 真ん中ほど濃い、ふちはまだらに
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / cr
            if d < 0.55 + rnd.uniform(-0.08, 0.08):
                put(x, y, 1)
    # 主な割れ目 8 本
    k = 8
    for i in range(k):
        ang = i * math.tau / k + rnd.uniform(-0.25, 0.25)
        crack(c + math.cos(ang) * n * a.reach * 0.05, c + math.sin(ang) * n * a.reach * 0.05, ang, cr * rnd.uniform(0.75, 1.0), 0.8, 0)
    # 真ん中の溶けた円
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c)
            if d < n * a.reach * 0.07:
                put(x, y, 4)
            elif d < n * a.reach * 0.10:
                put(x, y, 3)
    # 切れた輪 (外のふち)
    r0 = cr * 0.86
    seg = 0
    for i in range(int(math.tau * r0 * 1.5)):
        t = i / (r0 * 1.5)
        if i % 9 == 0:
            seg = rnd.random() < 0.72
        if seg:
            for dr in (-1, 0, 1):
                put(int(round(c + math.cos(t) * (r0 + dr))), int(round(c + math.sin(t) * (r0 + dr))), 3 if dr == 0 else 2)


def finish(n, level, core, mid, rim, scorch, a, paths):
    img =Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    cols = {1: scorch + (150,), 2: rim + (235,), 3: mid + (255,), 4: core + (255,)}
    for y in range(n):
        for x in range(n):
            if level[y][x]:
                px[x, y] = cols[level[y][x]]
    img.save(a.out)
    if a.points:
        import json
        with open(a.points, "w") as f:
            json.dump({"size": n, "paths": paths}, f)
    print(a.out, n)


if __name__ == "__main__":
    main()
