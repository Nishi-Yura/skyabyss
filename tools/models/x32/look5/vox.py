# -*- coding: utf-8 -*-
"""ボクセルで組む道具（神器の作り直し、2026-11-19）。

参照の武器は「1 ドット大の小さな立方体を積んだ立体（ボクセルアート）に、1 ドットずつ色を塗り、まわりを 1 ドットの線や粒が漂う」作り。
  1. Vox に 3D の形（箱・カプセル・押し出し・八面体・1 ドットの線）を素材と段で積む。後に積んだものが上書き。
  2. shade(): 上と左が空いている点を明るく、下と右が空いている点を暗く。
  3. mesh(): 組（光る/光らない、本体/浮く塊）ごとに隙間のない直方体へまとめ（貪欲法）、
     外に見える面だけ、点ごとの色をそのまま面の絵にする。
1 ボクセル = 模型の 1 単位。座標は整数（ボクセルの左下手前の角）。
"""
import math
from collections import deque

OFFF = {'up': 1, 'south': 0, 'north': 0, 'east': -1, 'west': -1, 'down': -2}
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


class Vox:
    def __init__(self, glowmats=('dawn',)):
        self.v = {}
        self.glowmats = glowmats

    # ---------------------------------------------------------------- 積む
    def put(self, x, y, z, mat, t, flt=False):
        self.v[(int(math.floor(x)), int(math.floor(y)), int(math.floor(z)))] = dict(mat=mat, t=t, flt=flt)

    def fill(self, bbox, fn, flt=False):
        """bbox=(x0,y0,z0,x1,y1,z1) の中の各ボクセルの中心で fn(x,y,z) -> (mat, t) か None"""
        x0, y0, z0, x1, y1, z1 = bbox
        for x in range(math.floor(x0), math.ceil(x1)):
            for y in range(math.floor(y0), math.ceil(y1)):
                for z in range(math.floor(z0), math.ceil(z1)):
                    r = fn(x + 0.5, y + 0.5, z + 0.5)
                    if r:
                        self.put(x, y, z, r[0], r[1], flt)

    def box(self, a, b, mat, t, flt=False, tone=None):
        self.fill((*a, *b), lambda x, y, z: (mat, tone(x, y, z) if tone else t), flt)

    def capsule(self, a, b, r0, r1, mat, t, flt=False, tone=None, squash=1.0):
        """a から b への、太さが r0 から r1 へ変わる棒（z の太さは squash 倍）。tone(x, y, z, f) で段を流せる"""
        ax, ay, az = a
        bx, by, bz = b
        d = (bx - ax, by - ay, bz - az)
        L2 = sum(c * c for c in d) or 1
        R = max(r0, r1) + 1

        def fn(x, y, z):
            f = max(0.0, min(1.0, ((x - ax) * d[0] + (y - ay) * d[1] + (z - az) * d[2]) / L2))
            px, py, pz = ax + d[0] * f, ay + d[1] * f, az + d[2] * f
            r = r0 + (r1 - r0) * f
            if (x - px) ** 2 + (y - py) ** 2 + ((z - pz) / squash) ** 2 <= r * r:
                return mat, (tone(x, y, z, f) if tone else t)
        self.fill((min(ax, bx) - R, min(ay, by) - R, min(az, bz) - R, max(ax, bx) + R, max(ay, by) + R, max(az, bz) + R), fn, flt)

    def slab(self, pts, zrange, mat, t, flt=False, tone=None):
        """多角形 pts（xy）を zrange(x, y) -> (z0, z1) の厚みで押し出す"""
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]

        def inside(x, y):
            c = False
            for k in range(len(pts)):
                (px, py), (qx, qy) = pts[k], pts[(k + 1) % len(pts)]
                if (py > y) != (qy > y) and x < (qx - px) * (y - py) / (qy - py) + px:
                    c = not c
            return c

        def fn(x, y, z):
            if inside(x, y):
                z0, z1 = zrange(x, y)
                if z0 <= z <= z1:
                    return mat, (tone(x, y, z) if tone else t)
        self.fill((min(xs), min(ys), -14, max(xs), max(ys), 14), fn, flt)

    def octa(self, c, r, mat, t, flt=False, rz=None):
        rz = r if rz is None else rz
        self.fill((c[0] - r, c[1] - r, c[2] - rz, c[0] + r, c[1] + r, c[2] + rz),
                  lambda x, y, z: (mat, t) if abs(x - c[0]) / r + abs(y - c[1]) / r + abs(z - c[2]) / rz <= 1 else None, flt)

    def wire(self, pts, mat, t, flt=True, step=0.3):
        """1 ドットの線（3D の折れ線）"""
        for p, q in zip(pts, pts[1:]):
            n = max(1, int(math.dist(p, q) / step))
            for k in range(n + 1):
                f = k / n
                self.put(p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f, p[2] + (q[2] - p[2]) * f, mat, t, flt)

    # ---------------------------------------------------------------- 陰
    def shade(self, light=1.0, dark=1.0):
        out = {}
        V = self.v
        for (x, y, z), c in V.items():
            t = c['t']

            def same(dx, dy):
                n = V.get((x + dx, y + dy, z))
                return n is not None and n['mat'] == c['mat']
            if not same(0, 1) or not same(-1, 0):
                t += light
            if not same(0, -1) or not same(1, 0):
                t -= dark
            out[(x, y, z)] = dict(c, t=t)
        self.v = out

    # ---------------------------------------------------------------- 箱へまとめる
    def _groups(self):
        """光る/光らない × 本体/浮く塊"""
        seen, comps = set(), []
        for k in self.v:
            if k in seen:
                continue
            q, comp = deque([k]), []
            seen.add(k)
            while q:
                p = q.popleft()
                comp.append(p)
                for d in N6:
                    n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                    if n in self.v and n not in seen:
                        seen.add(n)
                        q.append(n)
            comps.append(comp)
        comps.sort(key=len, reverse=True)
        groups = {}
        for ci, comp in enumerate(comps):
            flt = ci > 0
            for k in comp:
                glow = self.v[k]['mat'] in self.glowmats
                groups.setdefault((ci if flt else 0, glow, flt), set()).add(k)
        return groups

    def mesh(self, m, name):
        nb = 0
        for (ci, glow, flt), S in sorted(self._groups().items()):
            done = set()
            for k in sorted(S, key=lambda p: (p[1], p[2], p[0])):
                if k in done:
                    continue
                x0, y0, z0 = k
                x1 = x0
                while (x1 + 1, y0, z0) in S and (x1 + 1, y0, z0) not in done:
                    x1 += 1
                z1 = z0
                while all((x, y0, z1 + 1) in S and (x, y0, z1 + 1) not in done for x in range(x0, x1 + 1)):
                    z1 += 1
                y1 = y0
                while all((x, y1 + 1, z) in S and (x, y1 + 1, z) not in done for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)):
                    y1 += 1
                for x in range(x0, x1 + 1):
                    for y in range(y0, y1 + 1):
                        for z in range(z0, z1 + 1):
                            done.add((x, y, z))
                fp = self._faces(x0, y0, z0, x1, y1, z1)
                if not fp:
                    continue
                nb += 1
                tag = f'float_{name}{ci}' if flt else name
                nm = f'{tag}_v{nb}' + ('_glow' if glow else '')
                m.box(nm, 'stone', [x0, y0, z0], [x1 + 1, y1 + 1, z1 + 1], facepix=fp)
        return m

    def _faces(self, x0, y0, z0, x1, y1, z1):
        """外に見える面の絵（上の行から、外から見て左から）。全部ふさがった面は外す"""
        V = self.v
        X, Y, Z = range(x0, x1 + 1), range(y0, y1 + 1), range(z0, z1 + 1)
        spec = {
            'south': ([[(x, y, z1) for x in X] for y in reversed(Y)], (0, 0, 1)),
            'north': ([[(x, y, z0) for x in reversed(X)] for y in reversed(Y)], (0, 0, -1)),
            'east': ([[(x1, y, z) for z in reversed(Z)] for y in reversed(Y)], (1, 0, 0)),
            'west': ([[(x0, y, z) for z in Z] for y in reversed(Y)], (-1, 0, 0)),
            'up': ([[(x, y1, z) for x in X] for z in Z], (0, 1, 0)),
            'down': ([[(x, y0, z) for x in X] for z in reversed(Z)], (0, -1, 0)),
        }
        out = {}
        for face, (grid, d) in spec.items():
            if all((p[0] + d[0], p[1] + d[1], p[2] + d[2]) in V for row in grid for p in row):
                continue
            out[face] = [[(V[p]['mat'], V[p]['t'] + OFFF[face]) for p in row] for row in grid]
        return out
