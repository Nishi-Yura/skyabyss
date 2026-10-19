#!/usr/bin/env python3
"""雷翼の背の試練の見直し (docs/design/25) の見取り図を、ドット絵の格子で描く。
出力: docs/review/storm_review/{base_top,eye_init,eye_solved,a_west,b_east,c_dial}.png  (名前は _ で始めない)
座標は StormBackLayout の値 (西・東・前・広間・竜骨)。案の絵は概念図。
使い方: python tools/gen_storm_review.py   (リポジトリ直下から)
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "review", "storm_review")
os.makedirs(OUT, exist_ok=True)

COL = {
    "bg": (0x10, 0x14, 0x1c), "body": (0x1c, 0x24, 0x30), "hub": (0x5c, 0x6a, 0x80), "hall": (0x3c, 0x4a, 0x5e),
    "west": (0x4f, 0x8f, 0xd0), "east": (0xd0, 0xa0, 0x40), "front": (0x9a, 0x6f, 0xd0), "keel": (0x40, 0x7a, 0x6a),
    "gap": (0xe0, 0x4a, 0x3c), "vent": (0xf0, 0xe0, 0x70), "rod": (0x6f, 0xe0, 0xff), "door": (0xf0, 0x80, 0x30),
    "stone": (0xff, 0xff, 0xff), "beam": (0xff, 0xf0, 0x90), "mirror": (0xe8, 0xa8, 0x60), "mirror_back": (0x60, 0x40, 0x30),
    "recv": (0xf4, 0xf4, 0xf4), "recv_lit": (0xff, 0xd0, 0x40), "stop": (0xe0, 0x30, 0x30), "grid": (0x2c, 0x38, 0x48),
    "ok": (0x60, 0xd0, 0x80), "warn": (0xf0, 0x60, 0x50), "ink": (0xe0, 0xe8, 0xf0),
}


class Canvas:
    def __init__(self, x0, x1, z0, z1, s):
        self.x0, self.z0, self.s = x0, z0, s
        self.img = Image.new("RGB", ((x1 - x0) * s, (z1 - z0) * s), COL["bg"])
        self.d = ImageDraw.Draw(self.img)

    def px(self, x, z):
        return ((x - self.x0) * self.s, (z - self.z0) * self.s)

    def rect(self, x1, x2, z1, z2, c, outline=None):
        a = self.px(x1, z1)
        b = self.px(x2 + 1, z2 + 1)
        self.d.rectangle([a[0], a[1], b[0] - 1, b[1] - 1], fill=COL[c] if isinstance(c, str) else c, outline=COL[outline] if outline else None)

    def circle(self, cx, cz, r, c):
        a = self.px(cx - r, cz - r)
        b = self.px(cx + r + 1, cz + r + 1)
        self.d.ellipse([a[0], a[1], b[0] - 1, b[1] - 1], fill=COL[c] if isinstance(c, str) else c)

    def line(self, x1, z1, x2, z2, c, w=1):
        a = self.px(x1, z1)
        b = self.px(x2, z2)
        self.d.line([a, b], fill=COL[c] if isinstance(c, str) else c, width=w)

    def dot(self, x, z, r, c):
        p = self.px(x + 0.5, z + 0.5)
        self.d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=COL[c] if isinstance(c, str) else c)

    def save(self, name):
        self.img.save(os.path.join(OUT, name))


# ---------------------------------------------------------------- 全体 (いまの形)
def base_top():
    c = Canvas(-150, 150, -100, 105, 3)
    # 広間・尾の間・門・到着
    c.rect(-14, 14, -18, 10, "hub")
    c.rect(-10, 10, 20, 32, "hall")
    c.rect(-2, 2, 11, 19, "hall")
    c.rect(-6, 6, 40, 42, "hall")
    c.rect(-4, 4, 90, 98, "hall")
    c.rect(-1, 1, 43, 89, "hall")
    # 西
    c.rect(-22, -15, -7, -5, "hall")
    c.rect(-30, -23, -12, 0, "west")
    c.rect(-64, -31, -24, -10, "west")
    c.circle(-71, -17, 7, "west")
    c.rect(-115, -78, -24, -10, "west")
    c.circle(-128, -17, 12, "west")
    c.rect(-139, -134, -21, -13, (0x8a, 0xc0, 0xf0))
    for g0, g1 in [(-44, -39), (-59, -54), (-92, -87), (-108, -103)]:
        c.rect(g0, g1, -24, -10, "gap")
    for vx in (-37, -85):
        c.rect(vx - 1, vx + 1, -21, -19, "vent")
    c.rect(-133, -131, -18, -16, "vent")
    # 東
    c.rect(15, 22, -7, -5, "hall")
    c.rect(23, 30, -12, 0, "east")
    c.rect(31, 115, -24, -10, "east")
    c.circle(71, -17, 7, "east")
    c.circle(128, -17, 13, "east")
    c.rect(130, 134, -19, -15, (0xf0, 0xd0, 0x80))
    for x in (48, 62, 95):
        c.rect(x, x, -18, -16, "door")
    for rx, rz in [(43, -21), (57, -13), (90, -21)]:
        c.dot(rx, rz, 4, "rod")
    c.rect(126, 128, -18, -16, "vent")
    # 前
    c.rect(-2, 2, -43, -19, "hall")
    c.rect(-4, 4, -56, -44, "front")
    c.rect(-8, 8, -86, -70, "front")
    c.rect(-3, 3, -95, -89, (0xc0, 0x9a, 0xf0))
    for gx in range(5):
        for gz in range(5):
            c.dot(-6 + 3 * gx, -84 + 3 * gz, 2, "stone")
    # 竜骨
    c.rect(-2, 2, -6, -2, "keel")
    c.rect(-16, -2, -5, -3, "keel")
    c.rect(-21, -17, -6, -2, "keel")
    c.rect(-3, 3, -36, -31, "keel")
    c.circle(0, -14, 15, "keel")
    # 凪の塔
    c.rect(8, 12, 3, 7, "vent")
    c.save("base_top.png")


# ---------------------------------------------------------------- 眼の間 (配置 0)
STEP = [(0, -1), (1, 0), (0, 1), (-1, 0)]
PAIRS = [(0, 1), (1, 2), (2, 3), (3, 0)]
CFG0 = dict(entry=0, mirrors=[(0, 2), (2, 0), (1, 2), (1, 0), (1, 2), (3, 0), (1, 1)], init=[2, 0, 1, 1, 1, 0, 2],
            recv=[(0, 0), (3, 1), (2, 1)])
# cells(0,0, 2,2, 0,1, 2,1, 2,3, 0,3, 1,1) = (0,0)(2,2)(0,1)(2,1)(2,3)(0,3)(1,1)
CFG0["mirrors"] = [(0, 0), (2, 2), (0, 1), (2, 1), (2, 3), (0, 3), (1, 1)]


def trace(cfg, rots):
    at = {m: r for m, r in zip(cfg["mirrors"], rots)}
    path = []
    c, r, d = -1, cfg["entry"], 1
    seen = set()
    while True:
        c += STEP[d][0]
        r += STEP[d][1]
        if c < 0 or c > 4 or r < 0 or r > 4:
            return path, (d, r if d in (1, 3) else c)
        if (c, r, d) in seen:
            return path, None
        seen.add((c, r, d))
        path.append((c, r))
        k = at.get((c, r), -1)
        if k >= 0:
            s = (d + 2) % 4
            p = PAIRS[k]
            if s == p[0]:
                d = p[1]
            elif s == p[1]:
                d = p[0]
            else:
                return path, None


def eye(rots, name, show_stop, all_lit=False):
    cfg = CFG0
    S = 24
    c = Canvas(-12, 12, -92, -66, S)
    c.rect(-8, 8, -86, -70, "grid")
    cx = lambda gx: -6 + 3 * gx
    cz = lambda gz: -84 + 3 * gz
    for gx in range(5):
        for gz in range(5):
            c.rect(cx(gx), cx(gx), cz(gz), cz(gz), (0x38, 0x46, 0x58))
    # 受け羽 (壁の位置)
    wall = {0: lambda i: (cx(i), -87), 1: lambda i: (9, cz(i)), 2: lambda i: (cx(i), -69), 3: lambda i: (-9, cz(i))}
    path, ex = trace(cfg, rots)
    lit = set()
    for i, (side, idx) in enumerate(cfg["recv"]):
        x, z = wall[side](idx)
        hit = all_lit or (ex is not None and ex == (side, idx))
        c.rect(x - 1 if side in (0, 2) else x, x + 1 if side in (0, 2) else x, z - 1 if side in (1, 3) else z, z + 1 if side in (1, 3) else z, "mirror_back")
        c.rect(x, x, z, z, "recv_lit" if hit else "recv")
    # 入口の窓
    ez = cz(cfg["entry"])
    c.rect(-9, -9, ez, ez, "vent")
    # 光
    pts = [(-9 + 0.5, ez + 0.5)] + [(cx(p[0]) + 0.5, cz(p[1]) + 0.5) for p in path]
    if ex is not None:
        side, idx = ex
        x, z = wall[side](idx)
        pts.append((x + 0.5, z + 0.5))
    for a, b in zip(pts, pts[1:]):
        c.line(a[0], a[1], b[0], b[1], "beam", max(3, S // 5))
    # 鏡
    for (gx, gz), k in zip(cfg["mirrors"], rots):
        x0, z0 = cx(gx), cz(gz)
        mid = c.px(x0 + 0.5, z0 + 0.5)
        h = S * 0.42
        n = [(0, -1), (1, 0), (0, 1), (-1, 0)]
        a, b = PAIRS[k]
        nx, nz = n[a][0] + n[b][0], n[a][1] + n[b][1]
        # 面の線は法線に垂直
        dx, dz = -nz, nx
        p1 = (mid[0] - dx * h, mid[1] - dz * h)
        p2 = (mid[0] + dx * h, mid[1] + dz * h)
        c.d.line([p1, p2], fill=COL["mirror_back"], width=5)
        off = 3
        q1 = (p1[0] + nx * off, p1[1] + nz * off)
        q2 = (p2[0] + nx * off, p2[1] + nz * off)
        c.d.line([q1, q2], fill=COL["mirror"], width=3)
    if show_stop and ex is None and path:
        x, z = path[-1]
        p = c.px(cx(x) + 0.5, cz(z) + 0.5)
        c.d.ellipse([p[0] - 7, p[1] - 7, p[0] + 7, p[1] + 7], fill=COL["stop"])
    c.save(name)


# ---------------------------------------------------------------- 作り直しの案 (概念図)
def a_west():
    # 横から (x と高さ): 縦の肋の登り
    S = 5
    c = Canvas(-80, 0, 0, 100, S)
    c.rect(-70, -10, 2, 98, (0x1a, 0x28, 0x3a))
    levels = [(-66, 90), (-40, 72), (-62, 54), (-34, 36), (-60, 18)]
    for x, y in levels:
        c.rect(x, x + 18, y, y + 1, "west")
    for x, y in [(-48, 82), (-36, 64), (-50, 46), (-30, 28)]:
        c.rect(x - 1, x + 1, y, y + 1, "vent")
        c.line(x + 0.5, y, x + 0.5, y - 8, "vent", 2)
    for x, y in [(-30, 78), (-45, 60), (-26, 42)]:
        c.circle(x, y, 2, "rod")
    c.rect(-24, -14, 4, 5, "stone")
    c.save("a_west.png")


def b_east():
    S = 6
    c = Canvas(20, 140, -30, 4, S)
    c.rect(31, 115, -24, -10, (0x4a, 0x3c, 0x20))
    c.circle(128, -17, 12, (0x4a, 0x3c, 0x20))
    rods = [(43, -21), (57, -13), (75, -20), (90, -21)]
    for x, z in rods:
        c.dot(x, z, 6, "rod")
    for a, b in zip(rods, rods[1:]):
        c.line(a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5, "vent", 2)
    # 接地のレバー
    for x, z in [(46, -12), (60, -21), (82, -12)]:
        c.rect(x, x, z, z, "door")
    for x in (48, 62, 95):
        c.rect(x, x, -18, -16, "door")
    c.dot(128, -17, 8, "gap")
    c.save("b_east.png")


def c_dial():
    S = 5
    c = Canvas(-150, 150, -100, 20, S)
    for (x1, x2, z1, z2), col in [((-64, -31, -24, -10), "west"), ((31, 64, -24, -10), "east"), ((-8, 8, -86, -70), "front")]:
        c.rect(x1, x2, z1, z2, col)
    for x, z in [(-28, -6), (28, -6), (0, -50)]:
        c.circle(x, z, 3, "vent")
        c.circle(x, z, 1, "bg")
    c.rect(-14, 14, -18, 10, "hub")
    c.save("c_dial.png")


if __name__ == "__main__":
    base_top()
    eye(CFG0["init"], "eye_init.png", True)
    solved = None
    # 解き方 (最短) を BFS で求めて描く
    from collections import deque
    n = len(CFG0["mirrors"])
    full = (1 << 3) - 1

    def lit(rots, mask):
        p, e = trace(CFG0, rots)
        if e is not None and e in CFG0["recv"]:
            mask |= 1 << CFG0["recv"].index(e)
        return mask

    start = tuple(CFG0["init"])
    q = deque([(start, lit(start, 0))])
    seen = {(start, lit(start, 0))}
    best = None
    while q:
        rots, mask = q.popleft()
        if mask == full:
            best = rots
            break
        for i in range(n):
            nr = list(rots)
            nr[i] = (nr[i] + 1) % 4
            nr = tuple(nr)
            st = (nr, lit(nr, mask))
            if st not in seen:
                seen.add(st)
                q.append(st)
    # 最後の状態では受け羽は 1 つ分しか光らない (光った印は残る) ので、解の並びは 3 枚を一枚ずつ重ねて見せる
    eye(list(best) if best else CFG0["init"], "eye_solved.png", False, True)
    a_west()
    b_east()
    c_dial()
    print("ok")
