# -*- coding: utf-8 -*-
"""ドット絵を描いて厚みのある立体に起こす道具（神器の四回目、2026-11-19）。

参照（refs_1118/1119 と 11/19 に届いた鎌・武器セットの絵）は「1 ドットずつ色を塗り分けたドット絵を、厚みのある板に起こした」作り。
ここでは次の順で作る:
  1. Sprite に形（多角形・線・円・点）を素材と段（明るさ）で塗る。後に描いたものが上に乗る。
  2. shade(): 形ごとに上・左の縁を明るく、下・右の縁を暗く、形の境目に影の線、外側に 1 ドットの暗い縁取りを足す。
  3. build(): 塗った絵から、行ごとの横長の箱（厚み = 層の深さ）で立体の芯を作り、表と裏に絵の板（decal）を貼る。
     光る素材の点は別の板（名前 _glow）に分けて少し手前に貼る。本体から離れた塊は float_ の名で浮かせる。
1 ドット = 模型の 1 単位（px で変えられる）。
"""
import math
from collections import deque

from lib import Model


class Sprite:
    def __init__(self, x0, y0, x1, y1, px=1.0):
        """模型の座標で (x0, y0)〜(x1, y1) の範囲を px 角のドットに割る"""
        self.px = px
        self.x0, self.y0 = x0, y0
        self.W = int(math.ceil((x1 - x0) / px))
        self.H = int(math.ceil((y1 - y0) / px))
        self.cell = {}          # (i, j) -> dict(mat, t, sid, depth, float, bevel, zc)
        self.nshape = 0
        self._opt = {}

    # ---------------------------------------------------------------- 座標
    def ij(self, x, y):
        return int(math.floor((x - self.x0) / self.px)), int(math.floor((y - self.y0) / self.px))

    def xy(self, i, j):
        return self.x0 + (i + 0.5) * self.px, self.y0 + (j + 0.5) * self.px

    # ---------------------------------------------------------------- 塗る
    def _paint(self, test, bbox, mat, t, depth, flt, sid=None, shade=True, tone=None, edge=None):
        bevel, zc = self._opt.pop('bevel', None), self._opt.pop('zc', 0.0)
        if sid is None:
            self.nshape += 1
            sid = self.nshape
        x0, y0, x1, y1 = bbox
        i0, j0 = self.ij(x0, y0)
        i1, j1 = self.ij(x1, y1)
        for i in range(max(0, i0 - 1), min(self.W, i1 + 2)):
            for j in range(max(0, j0 - 1), min(self.H, j1 + 2)):
                x, y = self.xy(i, j)
                if test(x, y):
                    self.cell[(i, j)] = dict(mat=mat, t=tone(x, y) if tone else t, sid=sid, depth=depth, float=flt, shade=shade, edge=edge,
                                             bevel=bevel, zc=zc)
        return sid

    def at(self, bevel=None, zc=0.0):
        """次に描く形の厚みの流れ bevel=(縁の厚み, 中まで何ドットで厚くなるか) と奥行きの中心 zc"""
        self._opt = dict(bevel=bevel, zc=zc)
        return self

    def poly(self, pts, mat, t, depth=1.0, flt=False, shade=True, tone=None, edge=None):
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]

        def inside(x, y):
            c = False
            n = len(pts)
            for k in range(n):
                (ax, ay), (bx, by) = pts[k], pts[(k + 1) % n]
                if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                    c = not c
            return c
        return self._paint(inside, (min(xs), min(ys), max(xs), max(ys)), mat, t, depth, flt, shade=shade, tone=tone, edge=edge)

    def rect(self, x0, y0, x1, y1, mat, t, depth=1.0, flt=False, shade=True):
        return self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], mat, t, depth, flt, shade)

    def line(self, a, b, w, mat, t, depth=1.0, flt=False, shade=True, w1=None, tg=None, edge=None):
        """a から b への太さ w（先で w1 に細る）の帯。tg を渡すと段が a の t から b の tg へ流れる"""
        w1 = w if w1 is None else w1
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        pts = [(a[0] + nx * w / 2, a[1] + ny * w / 2), (b[0] + nx * w1 / 2, b[1] + ny * w1 / 2),
               (b[0] - nx * w1 / 2, b[1] - ny * w1 / 2), (a[0] - nx * w / 2, a[1] - ny * w / 2)]
        tone = None
        if tg is not None:
            def tone(x, y):
                f = max(0.0, min(1.0, ((x - a[0]) * dx + (y - a[1]) * dy) / (L * L)))
                return t + (tg - t) * f
        return self.poly(pts, mat, t, depth, flt, shade, tone=tone, edge=edge)

    def disc(self, c, r, mat, t, depth=1.0, flt=False, shade=True):
        return self._paint(lambda x, y: (x - c[0]) ** 2 + (y - c[1]) ** 2 <= r * r,
                           (c[0] - r, c[1] - r, c[0] + r, c[1] + r), mat, t, depth, flt, shade=shade)

    def ring(self, c, r0, r1, mat, t, depth=1.0, flt=False, shade=True):
        return self._paint(lambda x, y: r0 * r0 <= (x - c[0]) ** 2 + (y - c[1]) ** 2 <= r1 * r1,
                           (c[0] - r1, c[1] - r1, c[0] + r1, c[1] + r1), mat, t, depth, flt, shade=shade)

    def diamond(self, c, r, mat, t, depth=1.0, flt=False, shade=True):
        return self._paint(lambda x, y: abs(x - c[0]) + abs(y - c[1]) <= r,
                           (c[0] - r, c[1] - r, c[0] + r, c[1] + r), mat, t, depth, flt, shade=shade)

    def dot(self, x, y, mat, t, depth=1.0, flt=False):
        i, j = self.ij(x, y)
        self.nshape += 1
        zc = self._opt.pop('zc', 0.0)
        self._opt.pop('bevel', None)
        self.cell[(i, j)] = dict(mat=mat, t=t, sid=self.nshape, depth=depth, float=flt, shade=False, bevel=None, zc=zc)

    # ---------------------------------------------------------------- 陰と縁取り
    def shade(self, light=1.4, dark=1.3, seam=1.6, outline=('glass', 0)):
        """形ごとの厚みの流れ・明暗・境目の影・外側の縁取り"""
        dist = {}
        for k, c in self.cell.items():
            if c.get('bevel'):
                dist[k] = None
        frontier = deque()
        for (i, j) in dist:
            c = self.cell[(i, j)]
            for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = self.cell.get((i + di, j + dj))
                if n is None or n['sid'] != c['sid']:
                    dist[(i, j)] = 0
                    frontier.append((i, j))
                    break
        while frontier:
            i, j = frontier.popleft()
            for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                k = (i + di, j + dj)
                if k in dist and dist[k] is None and self.cell[k]['sid'] == self.cell[(i, j)]['sid']:
                    dist[k] = dist[(i, j)] + 1
                    frontier.append(k)
        for k, dd in dist.items():
            c = self.cell[k]
            dmin, ramp = c['bevel']
            f = min(1.0, (dd or 0) / max(ramp, 1e-6))
            c['depth'] = round((dmin + (c['depth'] - dmin) * f) * 4) / 4
            c['lit'] = (dd or 0) < ramp
        out = {}
        for (i, j), c in self.cell.items():
            t = c['t']
            if c['shade']:
                def other(di, dj):
                    n = self.cell.get((i + di, j + dj))
                    return n is None or n['sid'] != c['sid']
                if other(0, 1) or other(-1, 0):       # 上と左（j は上へ増える）
                    if c.get('edge'):
                        out[(i, j)] = dict(c, mat=c['edge'][0], t=c['edge'][1])
                        continue
                    t += light
                if other(0, -1) or other(1, 0):       # 下と右
                    t -= dark
                n = self.cell.get((i, j - 1))
                if n is not None and n['sid'] != c['sid'] and n['depth'] <= c['depth']:
                    pass
                up = self.cell.get((i, j + 1))
                if up is not None and up['sid'] != c['sid'] and up['depth'] > c['depth']:
                    t -= seam                          # 一段高い形の下に落ちる影
            out[(i, j)] = dict(c, t=t)
        if outline:
            for (i, j), c in list(self.cell.items()):
                for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    k = (i + di, j + dj)
                    if k not in self.cell and k not in out and 0 <= k[0] < self.W and 0 <= k[1] < self.H:
                        om = outline if outline != 'own' else (c['mat'], 0)
                        out[k] = dict(mat=om[0], t=om[1], sid=-1, depth=max(0.25, min(0.5, c['depth'] * 0.5)), float=c['float'],
                                      shade=False, zc=c.get('zc', 0.0))
        self.cell = out

    # ---------------------------------------------------------------- 立体に起こす
    def components(self):
        """つながった塊（4 近傍）"""
        seen, comps = set(), []
        for k in self.cell:
            if k in seen:
                continue
            q, comp = deque([k]), []
            seen.add(k)
            while q:
                i, j = q.popleft()
                comp.append((i, j))
                for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = (i + di, j + dj)
                    if n in self.cell and n not in seen:
                        seen.add(n)
                        q.append(n)
            comps.append(comp)
        return comps

    def build(self, m, name='px', glowmats=('dawn',)):
        """芯の箱（行ごと・奥行きと厚みの同じ並びごと）と、前と裏の面ごとの絵の板を Model m に足す"""
        p = self.px
        comps = sorted(self.components(), key=len, reverse=True)
        main = set(comps[0]) if comps else set()
        fl = 0
        nb = 0
        for ci, comp in enumerate(comps):
            cs = set(comp)
            flt = ci > 0 and (any(self.cell[k]['float'] for k in comp) or not (cs & main))
            tag = f'{name}_c{ci}' if not flt else f'float_{name}_{fl}'
            fl += flt
            rows = {}
            for (i, j) in comp:
                rows.setdefault(j, []).append(i)
            for j, xs in rows.items():
                xs.sort()
                key = lambda i: (self.cell[(i, j)].get('zc', 0.0), self.cell[(i, j)]['depth'])
                run = [xs[0], xs[0]]
                runs = []
                for x in xs[1:]:
                    if x == run[1] + 1 and key(x) == key(run[0]):
                        run[1] = x
                    else:
                        runs.append(run)
                        run = [x, x]
                runs.append(run)
                for a, b in runs:
                    zc, d = key(a)
                    c = self.cell[(a, j)]
                    x0, y0 = self.x0 + a * p, self.y0 + j * p
                    nb += 1
                    m.box(f'{tag}_b{nb}', c['mat'], [x0, y0 - 0.01, zc - d], [x0 + (b - a + 1) * p, y0 + p + 0.01, zc + d],
                          round(max(0, c['t'] - 1.5)), solid=True)
        groups = {}
        for k, c in self.cell.items():
            glow = c['mat'] in glowmats
            zc = c.get('zc', 0.0)
            groups.setdefault((round(zc + c['depth'], 3), glow, 'f'), []).append(k)
            groups.setdefault((round(zc - c['depth'], 3), glow, 'b'), []).append(k)
        for gi, ((z, glow, zn), pts) in enumerate(sorted(groups.items())):
            i0, i1 = min(k[0] for k in pts), max(k[0] for k in pts)
            j0, j1 = min(k[1] for k in pts), max(k[1] for k in pts)
            ps = set(pts)
            rows = [[(self.cell[(i, j)]['mat'], self.cell[(i, j)]['t']) if (i, j) in ps else None for i in range(i0, i1 + 1)]
                    for j in range(j1, j0 - 1, -1)]
            zz = z + (1 if zn == 'f' else -1) * (0.03 if glow else 0.015)
            nm = f'{name}_skin{gi}_{zn}' + ('_glow' if glow else '')
            m.decal(nm, rows, [self.x0 + i0 * p, self.y0 + j0 * p, zz], [self.x0 + (i1 + 1) * p, self.y0 + (j1 + 1) * p, zz])
        return m


def sprite_planes(sp, m, name, z, glowmats=('dawn',), zgap=0.03):
    """描いた絵を厚みのない板（表裏とも絵）として z の位置に貼る。光る点は名前 _glow の板に分けて少し手前に。
    立体の芯（箱）に重ねて、羽・光の筋・粒などの 2D の部分にする"""
    p = sp.px
    for glow in (False, True):
        pts = [k for k, c in sp.cell.items() if (c['mat'] in glowmats) == glow]
        if not pts:
            continue
        i0, i1 = min(k[0] for k in pts), max(k[0] for k in pts)
        j0, j1 = min(k[1] for k in pts), max(k[1] for k in pts)
        ps = set(pts)
        rows = [[(sp.cell[(i, j)]['mat'], sp.cell[(i, j)]['t']) if (i, j) in ps else None for i in range(i0, i1 + 1)]
                for j in range(j1, j0 - 1, -1)]
        zz = z + (zgap if glow else 0)
        nm = f'{name}' + ('_glow' if glow else '')
        m.decal(nm, rows, [sp.x0 + i0 * p, sp.y0 + j0 * p, zz], [sp.x0 + (i1 + 1) * p, sp.y0 + (j1 + 1) * p, zz])
    return m
