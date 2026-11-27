# -*- coding: utf-8 -*-
"""技の床の印: 真ん中から枝分かれして走る光る割れ目 (溶岩・雷・氷など) の絵を作る。

床の印は AttackPropRenderer が絵の幅に合わせて広げる (32px = 半径 1.7、64px 以上 = 半径 3.4。128px は 64px と同じ広さで細かさが 2 倍。どれも技の大きさ倍)。
  python -I tools/gen/pixel/decal_cracks.py <出力.png> [--size 64] [--seed 3] [--palette ember]
"""
import argparse
import math
import random

from PIL import Image

PALETTES = {
    # 芯 → 縁 → 外の縁 → 焦げ
    "ember": [(255, 244, 170), (255, 186, 52), (226, 92, 20), (60, 22, 12)],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--size", type=int, default=128)
    ap.add_argument("--seed", type=int, default=3)
    ap.add_argument("--palette", default="ember")
    ap.add_argument("--points", help="主な割れ目の通り道 (画素の x y) を JSON に書く。模型の炎をひびに沿って置くとき用")
    a = ap.parse_args()
    n, rnd = a.size, random.Random(a.seed)
    core, mid, rim, scorch = PALETTES[a.palette]
    c = (n - 1) / 2
    level = [[0] * n for _ in range(n)]  # 0 なし, 1 焦げ, 2 外の縁, 3 縁, 4 芯

    def put(x, y, v):
        if 0 <= x < n and 0 <= y < n and level[y][x] < v:
            level[y][x] = v

    paths = []

    def crack(x, y, ang, length, core_r, depth):
        """割れ目 1 本。芯 (半径 core_r) のまわりに縁 1px・外の縁 1px"""
        base = ang
        path = []
        paths.append(path)
        for _ in range(int(length)):
            # ジグザグに折れつつ、元の向き (外向き) へ戻る
            ang = ang * 0.8 + base * 0.2 + rnd.uniform(-0.38, 0.38)
            x += math.cos(ang)
            y += math.sin(ang)
            path.append([round(x, 1), round(y, 1)])
            for dx in range(-4, 5):
                for dy in range(-4, 5):
                    d = math.hypot(int(round(x)) + dx - x, int(round(y)) + dy - y)
                    v = 4 if d <= core_r else (3 if d <= core_r + 1 else (2 if d <= core_r + 2 else 0))
                    if v:
                        put(int(round(x)) + dx, int(round(y)) + dy, v)
            if depth < 2 and rnd.random() < 0.03:
                crack(x, y, ang + rnd.choice([-1, 1]) * rnd.uniform(0.5, 1.0), length * 0.4, max(0.0, core_r - 0.6), depth + 1)
            if math.hypot(x - c, y - c) > c - 2:
                break

    # 焦げ: 真ん中ほど濃い、ふちはまだらに
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / c
            if d < 0.55 + rnd.uniform(-0.08, 0.08):
                put(x, y, 1)
    # 主な割れ目 8 本
    k = 8
    for i in range(k):
        ang = i * math.tau / k + rnd.uniform(-0.25, 0.25)
        crack(c + math.cos(ang) * n * 0.05, c + math.sin(ang) * n * 0.05, ang, c * rnd.uniform(0.75, 1.0), 0.8, 0)
    # 真ん中の溶けた円
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c)
            if d < n * 0.07:
                put(x, y, 4)
            elif d < n * 0.10:
                put(x, y, 3)
    # 切れた輪 (外のふち)
    r0 = c * 0.86
    seg = 0
    for i in range(int(math.tau * r0 * 1.5)):
        t = i / (r0 * 1.5)
        if i % 9 == 0:
            seg = rnd.random() < 0.72
        if seg:
            for dr in (-1, 0, 1):
                put(int(round(c + math.cos(t) * (r0 + dr))), int(round(c + math.sin(t) * (r0 + dr))), 3 if dr == 0 else 2)

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
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
