# -*- coding: utf-8 -*-
"""土地の町の並べ方 (2026-10-27「建物同士の幅やレイアウト、配置等はまだまだ学ばないといけない」)。

中世の町割りと名作の街から取った決まり:
  - 道に格: 大通り (幅 7)・通り (5)・小路 (3)。大通りは門から市場へ、曲がって先を閉じる (真っ直ぐ抜けない)。突き当たりに目印の建物
  - 区画: 通りに面した間口は狭く (6〜11)、奥に長い。家は通りに沿って並び、裏は庭・畑・物置 (間口の線はそろえ、奥は区画ごとに違う)
  - 密度の勾配: 市場のまわりは隙間なく高く (3〜4 階・商家)、外ほど低く (1〜2 階・庭・小屋)、隙間も広く
  - 市場は四角でなく、通りが太って広がる形 (くさび)。いちばん良い建物 (宿・社) が市場を囲む
  - 囲いは地形なりの多角形 (角の塔)、門の外にも家と畑 (町の外れから野へ、徐々に)
  - 屋根の線: 棟の向き・高さ・形・色を区画ごとに変える

python -I tools/capital/town_plans.py rough [whale]   → src/main/resources/data/skyabyss/opening/town_rough_<a|b|c>.json と docs/review/town_roughs/plan_<a|b|c>.png
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town as T  # noqa: E402
import massing as M  # noqa: E402
from vox import Vox, Mat, DX, OPP, tower, flowerbed, lamp_post, tree, bench, planter  # noqa: E402
from region_town import RegionTown, STYLES, W, H, _check_blocks  # noqa: E402

GATES = {'north': (0, -W), 'south': (0, W), 'west': (-W, 0), 'east': (W, 0)}
EDGE = W + 17   # 設計図の縁 (門の外の家と畑まで)
ROLES = ['innkeeper', 'tool_merchant', 'food_vendor', 'outfitter', 'material_trader', 'smith', 'apothecary', 'guild_clerk', 'trade_keeper', 'storage_keeper', 'stable_keeper', 'perch_keeper',
         'mapmaker', 'training_master', 'keep_veteran', 'black_marketeer', 'shrine_priest', 'archivist']   # 土地の町の係の人 (都と同じ施設)
AIR = {'core': 1, 'mid': 1, 'edge': 2}   # 家と家の間に必ずあける空気のマス (軒の先から測る)
EAVE = 1        # 壁の線から軒の先まで


class PlanTown(RegionTown):
    def __init__(self, region, plan, seed=0):
        super().__init__(region)
        self.plan = plan
        self.v = Vox(seed=list(STYLES).index(region) * 1000 + ord(plan) * 7 + seed)
        self.rng = self.v.rng
        self.occ = {}          # (x, z) → 'road' | 'plaza' | 'bld' | 'yard' | 'wall' | 'keep'
        self.roadw = {}        # 道のマス → 幅
        self.plots = []
        self.quests = 0
        self.lm = (30, -26)
        self.lm2 = (-30, -26)
        self.wall_poly = []
        self.upper = set()     # 上の町 (段の上) の柱。建てる前に決め、区画は境をまたがない
        self.dy = 3
        self.h = {}
        self.owner = {}        # (x, z) → 建物の番号 (実際にブロックのある柱)
        self.n_bld = 0

    # ---------------------------------------------------------------- 地面と道
    def grass(self):
        v = self.v
        for x in range(-EDGE, EDGE + 1):
            for z in range(-EDGE, EDGE + 1):
                v.set(x, -1, z, 'grass_block[snowy=false]')

    def street(self, pts, w, kind='street'):
        """折れ線の道。w = 幅 (奇数)。kind = main | street | lane"""
        h = (w - 1) / 2.0
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            L = max(1.0, math.hypot(bx - ax, bz - az))
            n = int(L * 3) + 1
            for i in range(n + 1):
                t = i / n
                px, pz = ax + (bx - ax) * t, az + (bz - az) * t
                for x in range(int(math.floor(px - h - 0.5)), int(math.ceil(px + h + 0.5)) + 1):
                    for z in range(int(math.floor(pz - h - 0.5)), int(math.ceil(pz + h + 0.5)) + 1):
                        if abs(x - px) <= h + 0.25 and abs(z - pz) <= h + 0.25:
                            if self.occ.get((x, z)) not in ('plaza',):
                                self.occ[(x, z)] = 'road'
                            self.roadw[(x, z)] = max(self.roadw.get((x, z), 0), w)
        self.streets.append((pts, w, kind))

    def paint_roads(self):
        st, v = self.st, self.v
        lane_mix = [(st.pave[0][0], 3)] + [(b, 2) for b in st.base2] + [('coarse_dirt', 1), ('gravel', 1)]
        for (x, z), t in self.occ.items():
            if t not in ('road', 'plaza'):
                continue
            w = self.roadw.get((x, z), 5)
            edge = any(self.occ.get((x + a, z + b)) not in ('road', 'plaza') for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if t == 'road' and edge and w >= 5:
                v.set(x, -1, z, st.pave_trim)
            elif w <= 3 and t == 'road':
                v.set(x, -1, z, v.pick(lane_mix))
            else:
                v.set(x, -1, z, v.pick(st.pave))

    def plaza_poly(self, poly):
        """市場 (多角形) を広場として取る"""
        xs = [p[0] for p in poly]
        zs = [p[1] for p in poly]
        for x in range(int(min(xs)), int(max(xs)) + 1):
            for z in range(int(min(zs)), int(max(zs)) + 1):
                if _inside(poly, x + 0.5, z + 0.5):
                    self.occ[(x, z)] = 'plaza'
                    self.roadw[(x, z)] = 9

    def keep_out(self, x0, z0, x1, z1, tag='keep'):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.occ.setdefault((x, z), tag)

    def rect_free(self, x0, z0, x1, z1, gap):
        for x in range(x0 - gap, x1 + gap + 1):
            for z in range(z0 - gap, z1 + gap + 1):
                t = self.occ.get((x, z))
                inside = x0 <= x <= x1 and z0 <= z <= z1
                near = x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1
                if t in ('bld', 'keep') or (t == 'wall' and near) or (t in ('road', 'plaza') and inside):
                    return False
        if self.upper and not self.level(x0 - max(gap, 2), z0 - max(gap, 2), x1 + max(gap, 2), z1 + max(gap, 2)):
            return False
        return self.inside_wall(x0, z0, x1, z1)

    def level(self, x0, z0, x1, z1):
        """四角の中がみな同じ段か"""
        cells = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if self.occ.get((x, z)) != 'wall']
        if not cells:
            return True
        u = cells[0] in self.upper
        return all((c in self.upper) == u for c in cells)

    def set_upper(self, want, gate_r=10):
        """上の町を決める: 壁の内 (壁から 3 より内)、門のまわり gate_r はあけ、want(x, z) の所"""
        import terrace
        R = set()
        for x in range(-W, W + 1):
            for z in range(-W, W + 1):
                if not want(x, z) or any(math.hypot(x - gx, z - gz) < gate_r for (gx, gz) in GATES.values()):
                    continue
                if self.occ.get((x, z)) == 'wall' or (self.wall_poly and not _inside(self.wall_poly, x, z)):
                    continue
                R.add((x, z))
        self.upper = terrace._drop_islands(R, 60)

    def take(self, x0, z0, x1, z1, tag='bld'):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.occ[(x, z)] = tag

    def inside_wall(self, x0, z0, x1, z1, margin=3):
        if not self.wall_poly:
            return max(abs(x0), abs(x1), abs(z0), abs(z1)) <= W - margin
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            if not _inside(self.wall_poly, x, z) or _dist_poly(self.wall_poly, x, z) < margin:
                return False
        return True

    # ---------------------------------------------------------------- 囲い (多角形の壁と角の塔)
    def poly_wall(self, poly):
        st, v = self.st, self.v
        self.wall_poly = poly
        loop = poly + [poly[0]]
        cells = set()
        for (ax, az), (bx, bz) in zip(loop, loop[1:]):
            L = max(1.0, math.hypot(bx - ax, bz - az))
            for i in range(int(L * 3) + 1):
                t = i / (L * 3)
                px, pz = ax + (bx - ax) * t, az + (bz - az) * t
                for x in range(int(px) - 1, int(px) + 2):
                    for z in range(int(pz) - 1, int(pz) + 2):
                        if math.hypot(x - px, z - pz) <= 1.3:
                            cells.add((x, z))
        gate_cut = set()
        for side, (gx, gz) in GATES.items():
            for a in range(-9, 10):
                for b in range(-6, 7):
                    gate_cut.add((gx + a, gz + b) if side in ('north', 'south') else (gx + b, gz + a))
        for (x, z) in cells:
            if (x, z) in gate_cut:
                continue
            self.occ[(x, z)] = 'wall'
            v.set(x, -1, z, st.base.full)
            for y in range(0, H):
                v.set(x, y, z, st.base.full if y < 2 else (st.wall_main if self.rng.random() > 0.14 else self.rng.choice(st.wall_mix2)))
        # 胸壁 (外の縁のマスだけ一つおき) と歩廊
        for (x, z) in cells:
            if (x, z) in gate_cut:
                continue
            outer = not all((x + a, z + b) in cells or not _inside(poly, x + a + 0.5, z + b + 0.5) or _inside(poly, x + a, z + b)
                            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            dist_out = _dist_poly(poly, x, z) if _inside(poly, x, z) else -_dist_poly(poly, x, z)
            if dist_out < 0.2:
                v.set(x, H, z, st.trim.wall() if (x + z) % 2 == 0 else st.trim.slab('bottom'))
            else:
                v.set(x, H, z, st.trim.slab('bottom') if dist_out < 1.2 else 'air')
        # 角の塔 (門のそばは外す)
        far = [p_ for p_ in poly if all(math.hypot(p_[0] - gx, p_[1] - gz) >= 14 for (gx, gz) in GATES.values())]
        for i, (x, z) in enumerate(far):
            if i % 2:
                continue
            self.wall_tower(int(round(x)), int(round(z)))
            self.keep_out(int(x) - 4, int(z) - 4, int(x) + 4, int(z) + 4, 'wall')
        for side in GATES:
            self.region_gate(side)
            gx, gz = GATES[side]
            self.keep_out(gx - 8, gz - 4, gx + 8, gz + 4, 'wall') if side in ('north', 'south') else self.keep_out(gx - 4, gz - 8, gx + 4, gz + 8, 'wall')

    def wall_tower(self, cx, cz, half=3):
        """壁の塔: 平らな頂の胸壁と持ち送り、矢狭間。尖り屋根は付けない (屋根の線は町の中の塔と社に任せる)"""
        st, v = self.st, self.v
        top = H + 4
        for x in range(cx - half, cx + half + 1):
            for z in range(cz - half, cz + half + 1):
                edge = max(abs(x - cx), abs(z - cz)) == half
                v.set(x, -1, z, st.base.full)
                for y in range(0, top):
                    if edge:
                        v.set(x, y, z, st.base.full if y < 2 else (st.wall_main if self.rng.random() > 0.15 else self.rng.choice(st.wall_mix2)))
                    else:
                        v.set(x, y, z, 'black_concrete')
                v.set(x, top, z, st.base.full if edge else v.pick(st.pave))
        for side in DX:
            dx, dz = DX[side]
            for t in range(-half, half + 1):
                x = cx + dx * (half + 1) + (t if dz else 0)
                z = cz + dz * (half + 1) + (t if dx else 0)
                v.set(x, top - 1, z, st.trim.stairs(OPP[side], 'top'))
                v.set(x, top, z, st.trim.full)
                v.set(x, top + 1, z, st.trim.wall() if (t + half) % 2 == 0 else 'air')
            for y in (5, 9):
                x, z = cx + dx * half, cz + dz * half
                v.set(x, y, z, 'black_concrete')
                v.set(x, y + 1, z, 'black_concrete')
        for (x, z) in ((cx - half - 1, cz - half - 1), (cx + half + 1, cz - half - 1), (cx - half - 1, cz + half + 1), (cx + half + 1, cz + half + 1)):
            v.set(x, top, z, st.trim.full)
            v.set(x, top + 1, z, st.trim.wall())
        v.set(cx, top + 1, cz, 'lantern[hanging=false,waterlogged=false]')

    # ---------------------------------------------------------------- 区画と家
    def district(self, x, z):
        d = math.hypot(x - self.core[0], z - self.core[1])
        return 'core' if d < self.core_r else 'mid' if d < self.mid_r else 'edge'

    def frontage(self, pts, w, sides=(1, -1), depth=(10, 14), lot=(5, 9), setback=0, kinds=None, quest_every=0, start_skip=0):
        """道の片側ずつに区画を並べ、家を建てる。家は道の側に寄せ、裏は庭。
        向きの同じ (東西か南北か) 続きの区間は一本の線として、間口を途切れずに並べる"""
        h = (w - 1) // 2
        runs = []
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            ew = abs(bx - ax) >= abs(bz - az)
            mono = runs and runs[-1][0] == ew and ((bx - ax) * (runs[-1][1][-1][0] - runs[-1][1][-2][0]) >= 0 if ew else
                                                    (bz - az) * (runs[-1][1][-1][1] - runs[-1][1][-2][1]) >= 0)
            if mono:
                runs[-1][1].append((bx, bz))
            else:
                runs.append([ew, [(ax, az), (bx, bz)]])
        for ew, rp in runs:
            axis = 'x' if ew else 'z'
            k = 0 if ew else 1
            us = [p_[k] for p_ in rp]
            lo, hi = int(min(us)), int(max(us))
            for sgn in sides:
                u = lo + start_skip
                while u + 4 <= hi:
                    u = self._try_plot(u, hi, rp, h, sgn, axis, depth, lot, setback, kinds, quest_every)

    def _line_at(self, rp, axis, u):
        """区間の線の、u (axis の座標) での横の座標"""
        k, j = (0, 1) if axis == 'x' else (1, 0)
        pts = sorted(rp, key=lambda p_: p_[k])
        if u <= pts[0][k]:
            return pts[0][j]
        for (a_, b_) in zip(pts, pts[1:]):
            if a_[k] <= u <= b_[k]:
                if b_[k] == a_[k]:
                    return max(a_[j], b_[j])
                return a_[j] + (b_[j] - a_[j]) * (u - a_[k]) / (b_[k] - a_[k])
        return pts[-1][j]

    def _try_plot(self, u, hi, rp, h, sgn, axis, depth, lot, setback, kinds, quest_every):
        rng = self.rng
        fw = rng.randint(*lot)
        u1 = min(u + fw - 1, hi)
        if u1 - u + 1 < 5:
            return hi + 1
        vals = [self._line_at(rp, axis, t) for t in range(u - h - 1, u1 + h + 2)]
        if sgn > 0:
            edge = int(math.floor(max(vals) + h)) + 1 + setback
        else:
            edge = int(math.ceil(min(vals) - h)) - 1 - setback
        cx, cz = ((u + u1) / 2, edge) if axis == 'x' else (edge, (u + u1) / 2)
        dist = self.district(cx, cz)
        gap = AIR[dist] + EAVE
        hd = {'core': (7, 10), 'mid': (6, 9), 'edge': (6, 8)}[dist]
        for tries in range(3):
            house_d = rng.randint(*hd)
            plot_d = house_d + rng.randint(depth[0] - 7, depth[1] - 7) if dist != 'core' else house_d + rng.randint(1, 4)
            if axis == 'x':
                if sgn > 0:
                    hr = (u, edge, u1, edge + house_d - 1)
                    pr = (u, edge, u1, edge + plot_d - 1)
                    front = 'north'
                else:
                    hr = (u, edge - house_d + 1, u1, edge)
                    pr = (u, edge - plot_d + 1, u1, edge)
                    front = 'south'
            else:
                if sgn > 0:
                    hr = (edge, u, edge + house_d - 1, u1)
                    pr = (edge, u, edge + plot_d - 1, u1)
                    front = 'west'
                else:
                    hr = (edge - house_d + 1, u, edge, u1)
                    pr = (edge - plot_d + 1, u, edge, u1)
                    front = 'east'
            if self.rect_free(*hr, gap=gap):
                # 庭は空いている所だけ (他の家にかからない深さまで)
                pr = self._fit_yard(hr, pr, front)
                self.place_house(hr, pr, front, dist, kinds, quest_every)
                return u1 + 1 + gap
            hd = (max(5, hd[0] - 1), max(5, hd[1] - 2))
        return u + 1

    def _fit_yard(self, hr, pr, front):
        x0, z0, x1, z1 = pr
        back = OPP[front]
        hx0, hz0, hx1, hz1 = hr
        # 裏へ一列ずつ伸ばし、他の物に当たったら止める
        if back == 'south':
            z = hz1
            while z < z1 and all(self.occ.get((x, z + 1)) is None for x in range(x0, x1 + 1)):
                z += 1
            return x0, hz0, x1, z
        if back == 'north':
            z = hz0
            while z > z0 and all(self.occ.get((x, z - 1)) is None for x in range(x0, x1 + 1)):
                z -= 1
            return x0, z, x1, hz1
        if back == 'east':
            x = hx1
            while x < x1 and all(self.occ.get((x + 1, z)) is None for z in range(z0, z1 + 1)):
                x += 1
            return hx0, z0, x, z1
        x = hx0
        while x > x0 and all(self.occ.get((x - 1, z)) is None for z in range(z0, z1 + 1)):
            x -= 1
        return x, z0, hx1, z1

    def place_house(self, hr, pr, front, dist, kinds=None, quest_every=0):
        rng = self.rng
        st = self.st
        x0, z0, x1, z1 = hr
        frontw = (x1 - x0 + 1) if front in ('north', 'south') else (z1 - z0 + 1)
        deep = (z1 - z0 + 1) if front in ('north', 'south') else (x1 - x0 + 1)
        quest = bool(quest_every) and self.quests < 8 and frontw >= 7 and deep >= 7 and rng.random() < 0.6
        if kinds:
            kind = rng.choice(kinds)
        elif dist == 'core':
            kind = rng.choice(['gable', 'gable', 'merchant', 'jetty', 'row'])
        elif dist == 'mid':
            kind = rng.choice(['gable', 'row', 'jetty', 'ell', 'row'])
        else:
            kind = rng.choice(['cottage', 'cottage', 'row', 'ell'])
        if frontw <= 6 and kind in ('ell', 'merchant'):
            kind = 'gable'
        if quest:
            kind = rng.choice(['row', 'gable'])
            self.quests += 1
        floors = {'core': rng.choice((2, 3, 3, 4)), 'mid': rng.choice((1, 2, 2, 3)), 'edge': rng.choice((1, 1, 1, 2))}[dist]
        if kind == 'cottage':
            floors = 1
        if kind == 'merchant':
            floors = max(3, floors)
        rk = {'core': ['steep', 'bell', 'stepped', 'steep'], 'mid': ['steep', 'bell', 'half_hip', 'kick', 'steep'],
              'edge': ['low', 'half_hip', 'kick', 'hip_low']}[dist]
        roof_kind = 'stepped' if kind == 'merchant' else rng.choice(rk)
        if kind == 'cottage' and roof_kind == 'stepped':
            roof_kind = 'half_hip'
        roofs = [st.roof] * 3 + list(st.roof_alts) * 2
        mat = rng.choice(roofs)
        sides = (front, OPP[front]) if dist == 'core' else None
        extras = tuple(e for e in ('dormer', 'chimney') if rng.random() < 0.45)
        # 張り出しは前へ一つ: 区画の前の線を一つ下げて空ける
        if kind == 'jetty':
            hr = M.shrink(hr, front, 1)
            if (hr[2] - hr[0]) < 4 or (hr[3] - hr[1]) < 4:
                kind = 'gable'
                hr = M.grow(hr, front, 1)
        # L 字の袖は庭に伸ばす: 庭が浅ければ平入りに
        if kind == 'ell':
            yd = (pr[3] - pr[1]) - (hr[3] - hr[1]) if front in ('north', 'south') else (pr[2] - pr[0]) - (hr[2] - hr[0])
            if yd < 8:
                kind = 'row'
        self.take(*M.grow(hr, front, 1) if kind == 'jetty' else hr)
        # 建てたら隣の建物と接していないか確かめる。接していれば元に戻し、袖のない形で建て直す (それでもだめなら建てない)
        for attempt in range(2):
            snap = dict(self.v.b)
            marks = dict(self.v.marks)
            nparts = len(self.parts)
            nfake = len(self.v.fake_doors)
            qn = self.quest_n
            before = set(self.v.b)
            bodies, ridge = M.build_house(self, kind, hr, front, floors, roof_kind, mat=mat, sides=sides, quest=quest, extras=extras)
            new_cols = {(x, z) for (x, y, z) in set(self.v.b) - before if y >= 0}
            if not any(self.owner.get((x + a_, z + b_)) is not None for (x, z) in new_cols for a_ in (-1, 0, 1) for b_ in (-1, 0, 1)
                       if (x + a_, z + b_) not in new_cols):
                self.own_new(before)
                break
            self.v.b = snap
            self.v.marks.clear()
            self.v.marks.update(marks)
            del self.parts[nparts:]
            del self.v.fake_doors[nfake:]
            self.quest_n = qn
            if attempt == 0 and kind in ('ell', 'jetty', 'tower', 'merchant'):
                kind = 'row'
                if quest:
                    quest = False
                    self.quests -= 1
                continue
            for c in [c for c, t_ in self.occ.items() if t_ == 'bld' and hr[0] <= c[0] <= hr[2] and hr[1] <= c[1] <= hr[3]]:
                del self.occ[c]
            return
        if kind == 'ell':
            self.take(*pr)
        self.plots.append((hr, pr, front, dist, kind))
        if not quest:
            self.porch(hr, front, dist)

    def own_new(self, before, ymin=0):
        """新しく置いたブロックの柱を一つの建物として覚え、'bld' にする (道の上の軒も持ち主は付けるが、道のまま)"""
        self.n_bld += 1
        for (x, y, z) in set(self.v.b) - before:
            if y < ymin or self.v.b[(x, y, z)] == 'air':
                continue
            self.owner.setdefault((x, z), self.n_bld)
            if self.occ.get((x, z)) not in ('road', 'plaza'):
                self.occ[(x, z)] = 'bld'

    def check_gaps(self):
        """違う建物のブロックの柱が、AIR より近くないか (斜めも数える)"""
        bad = set()
        for (x, z), o in self.owner.items():
            need = 1
            for a in range(-need, need + 1):
                for b in range(-need, need + 1):
                    p = self.owner.get((x + a, z + b))
                    if p is not None and p != o:
                        bad.add(tuple(sorted((o, p))))
        return bad

    def porch(self, hr, front, dist):
        """戸の脇の小物 (樽・鉢・薪・腰掛け)"""
        v, st = self.v, self.st
        x0, z0, x1, z1 = hr
        dx, dz = DX[front]
        if front in ('north', 'south'):
            zf = (z0 if front == 'north' else z1) + dz
            spots = [(x0 + 1, zf), (x1 - 1, zf)]
        else:
            xf = (x0 if front == 'west' else x1) + dx
            spots = [(xf, z0 + 1), (xf, z1 - 1)]
        for (x, z) in spots:
            if self.rng.random() < 0.5 and self.occ.get((x, z)) in (None, 'road') and v.get(x, 0, z) in (None, 'air'):
                v.set(x, 0, z, self.rng.choice(['barrel[facing=up,open=false]', 'potted_%s' % self.rng.choice(st.pot_flowers),
                                                'composter[level=4]', '%s_log[axis=y]' % st.wood if 'log' not in st.wood else 'hay_block[axis=y]']))

    # ---------------------------------------------------------------- 庭・畑・空き地
    def yards(self):
        """家の裏の庭: 区画の境に柵、中は畑か木か物置か井戸"""
        v, st = self.v, self.st
        for (hr, pr, front, dist, kind) in self.plots:
            if kind == 'ell':
                continue
            hx0, hz0, hx1, hz1 = hr
            x0, z0, x1, z1 = pr
            if (x1 - x0) * (z1 - z0) <= (hx1 - hx0) * (hz1 - hz0) + 6:
                continue
            back = OPP[front]
            # 庭の範囲 = 区画から家と一列の隙間を除いたもの
            if back == 'south':
                yr = (x0, hz1 + 2, x1, z1)
            elif back == 'north':
                yr = (x0, z0, x1, hz0 - 2)
            elif back == 'east':
                yr = (hx1 + 2, z0, x1, z1)
            else:
                yr = (x0, z0, hx0 - 2, z1)
            if yr[2] - yr[0] < 2 or yr[3] - yr[1] < 2:
                continue
            self.yard(yr, dist)

    def yard(self, r, dist):
        v, st = self.v, self.st
        x0, z0, x1, z1 = r
        free = [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if self.occ.get((x, z)) is None]
        if len(free) < 6:
            return
        for (x, z) in free:
            self.occ[(x, z)] = 'yard'
        # 柵 (区画の境。中の町は低い石の塀)
        fence = st.fence if dist != 'core' else st.base.wall()
        for (x, z) in free:
            if x in (x0, x1) or z in (z0, z1):
                if self.rng.random() < 0.92:
                    v.set(x, 0, z, fence)
        w, d = x1 - x0, z1 - z0
        r_ = self.rng.random()
        if r_ < 0.35 and w >= 3 and d >= 3:
            # 畑の筋
            crops = ['wheat[age=7]', 'carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]']
            c = self.rng.choice(crops)
            for x in range(x0 + 1, x1):
                for z in range(z0 + 1, z1):
                    if self.occ.get((x, z)) == 'yard':
                        if (x + z) % 4 == 0 and w > d or (z % 4 == 0 and w <= d):
                            v.set(x, -1, z, 'water')
                        else:
                            v.set(x, -1, z, 'farmland[moisture=7]')
                            v.set(x, 0, z, c)
        elif r_ < 0.7:
            # 庭の木と花
            cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
            if w >= 4 and d >= 4 and self._no_bld(cx - 3, cz - 3, cx + 3, cz + 3) and self.level(cx - 3, cz - 3, cx + 3, cz + 3):
                tree(v, cx, 0, cz, st.trunk2 if self.rng.random() < 0.5 else st.trunk, st.leaves2 if self.rng.random() < 0.5 else st.leaves,
                     h=self.rng.choice((4, 5, 6)), r=2)
            for (x, z) in free:
                if x0 < x < x1 and z0 < z < z1 and self.rng.random() < 0.22 and v.get(x, 0, z) in (None, 'air'):
                    v.set(x, 0, z, self.rng.choice(st.flowers))
        else:
            # 物置と薪・樽
            sx, sz = x0 + 1, z0 + 1
            if w >= 4 and d >= 4 and self._no_bld(sx - 2, sz - 2, sx + 4, sz + 3):
                for x in range(sx, sx + 3):
                    for z in range(sz, sz + 2):
                        v.set(x, 0, z, '%s_planks' % st.wood)
                        v.set(x, 1, z, '%s_planks' % st.wood if (x, z) != (sx + 1, sz + 1) else 'air')
                        v.set(x, 2, z, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
            for k in range(3):
                x, z = self.rng.randint(x0 + 1, max(x0 + 1, x1 - 1)), self.rng.randint(z0 + 1, max(z0 + 1, z1 - 1))
                if v.get(x, 0, z) in (None, 'air'):
                    v.set(x, 0, z, self.rng.choice(['barrel[facing=up,open=false]', 'hay_block[axis=y]', 'composter[level=7]',
                                                    '%s_log[axis=x]' % (st.trunk.replace('_log', '') if st.trunk.endswith('_log') else 'oak')]))

    def _no_bld(self, x0, z0, x1, z1):
        return all(self.occ.get((x, z)) != 'bld' for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))

    def open_ground(self):
        """家にも庭にも道にもならなかった壁の内: 草地に木と茂み、ときどき石の腰掛け"""
        v, st = self.v, self.st
        trees = []
        for x in range(-W + 3, W - 2):
            for z in range(-W + 3, W - 2):
                if self.occ.get((x, z)) is not None:
                    continue
                if self.wall_poly and not _inside(self.wall_poly, x, z):
                    continue
                if self.rng.random() < 0.012 and all(math.hypot(x - a, z - b) > 6 for (a, b) in trees) and self._clear(x, z, 2) and self._no_bld(x - 4, z - 4, x + 4, z + 4) and self.level(x - 3, z - 3, x + 3, z + 3):
                    trees.append((x, z))
                    tree(v, x, 0, z, st.trunk if self.rng.random() < 0.6 else st.trunk2, st.leaves if self.rng.random() < 0.6 else st.leaves2,
                         h=self.rng.choice((5, 6, 7)), r=2)
                elif self.rng.random() < 0.05 and v.get(x, 0, z) in (None, 'air'):
                    v.set(x, 0, z, self.rng.choice(st.flowers + ['short_grass', 'short_grass', 'fern']))

    def _clear(self, x, z, r):
        return all(self.occ.get((x + a, z + b)) is None for a in range(-r, r + 1) for b in range(-r, r + 1))

    def auto_lanes(self, rounds=14, need=6):
        """大きく空いた所に小路を通す: いちばん道や家から遠い空き地を見つけ、近い道からそこを抜ける小路 (幅 3) を引いて、両側に家を並べる"""
        from collections import deque
        made = 0
        for _ in range(rounds):
            dist = {}
            dq = deque()
            for x in range(-W, W + 1):
                for z in range(-W, W + 1):
                    t = self.occ.get((x, z))
                    if t is not None or (self.wall_poly and not _inside(self.wall_poly, x, z)):
                        dist[(x, z)] = 0
                        dq.append((x, z))
            while dq:
                c = dq.popleft()
                for a_, b_ in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n_ = (c[0] + a_, c[1] + b_)
                    if abs(n_[0]) <= W and abs(n_[1]) <= W and n_ not in dist:
                        dist[n_] = dist[c] + 1
                        dq.append(n_)
            far = max(dist.items(), key=lambda kv: kv[1])
            if far[1] < need + getattr(self, 'lane_need_add', 1):
                break
            (fx, fz), _d = far
            # 近い道: 四方へまっすぐ見て、いちばん近い道か広場 (間に家や壁がないこと)
            best = None
            for (a_, b_) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for k in range(1, 50):
                    c = (fx + a_ * k, fz + b_ * k)
                    t = self.occ.get(c)
                    if t in ('road', 'plaza'):
                        if best is None or k < best[0]:
                            best = (k, a_, b_)
                        break
                    if t is not None:
                        break
            if not best:
                self.occ[(fx, fz)] = 'keep'
                continue
            k, a_, b_ = best
            start = (fx + a_ * k, fz + b_ * k)
            # 空き地を抜けて反対側へ、空いている限り (最大 16)
            end = (fx, fz)
            for j in range(1, 16):
                c = (fx - a_ * j, fz - b_ * j)
                if self.occ.get(c) is not None or (self.wall_poly and _dist_poly(self.wall_poly, *c) < 5):
                    break
                end = c
            pts = [start, end]
            self.street(pts, 3, 'lane')
            self.frontage(pts, 3, depth=(8, 11), lot=(5, 8))
            made += 1
        return made

    def infill(self):
        """表の通りから外れた奥の空き地に、路地の付いた家 (中庭・裏の家)。戸から真っ直ぐ道まで一本の路地を引く"""
        rng = self.rng
        cells = [(x, z) for x in range(-W + 4, W - 4, 3) for z in range(-W + 4, W - 4, 3)]
        rng.shuffle(cells)
        n = 0
        for (x0, z0) in cells:
            w, d = rng.randint(6, 9), rng.randint(6, 9)
            x1, z1 = x0 + w - 1, z0 + d - 1
            if n >= getattr(self, 'infill_max', 6):
                break
            if not self.rect_free(x0, z0, x1, z1, 3 + EAVE):
                continue
            if any(self.occ.get((x, z)) not in (None,) for x in range(x0 - 3, x1 + 4) for z in range(z0 - 3, z1 + 4)):
                continue
            best = None
            for front in self.TURN:
                dx, dz = DX[front]
                sx = (x0 + x1) // 2 if front in ('north', 'south') else (x0 if front == 'west' else x1)
                sz = (z0 + z1) // 2 if front in ('east', 'west') else (z0 if front == 'north' else z1)
                path = []
                for k in range(1, 14):
                    c = (sx + dx * k, sz + dz * k)
                    t = self.occ.get(c)
                    if t in ('road', 'plaza'):
                        if best is None or len(path) < len(best[1]):
                            best = (front, path)
                        break
                    if t not in (None, 'yard'):
                        break
                    path.append(c)
            if not best:
                continue
            front, path = best
            for c in path:
                self.occ[c] = 'road'
                self.roadw[c] = 1
                for y in range(0, 3):
                    self.v.set(c[0], y, c[1], 'air')
            dist = self.district((x0 + x1) / 2, (z0 + z1) / 2)
            self.place_house((x0, z0, x1, z1), (x0, z0, x1, z1), front, 'edge' if dist == 'edge' else 'mid',
                             kinds=['cottage', 'row', 'gable'] if dist != 'core' else ['gable', 'row', 'jetty'])
            n += 1
        print('  infill', n)
        return n

    def outskirts(self):
        """門の外: 道の両わきに小さな家、畑の帯、干し草"""
        v, st = self.v, self.st
        for side, (gx, gz) in GATES.items():
            dx, dz = DX[side]
            for k, off in enumerate((-9, 9)):
                # 畑 (門の外、道から離して)
                a0 = 20 if k == 0 else 20
                if side in ('north', 'south'):
                    zs = sorted((gz + dz * 6, gz + dz * 15))
                    xs = (off - (11 if off < 0 else -1), off + (1 if off < 0 else 11))
                    x0, x1 = sorted(xs)
                    z0, z1 = zs
                else:
                    xs = sorted((gx + dx * 6, gx + dx * 15))
                    zs = (off - (11 if off < 0 else -1), off + (1 if off < 0 else 11))
                    x0, x1 = xs
                    z0, z1 = sorted(zs)
                if any(self.occ.get((x, z)) is not None for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)):
                    continue
                if (self.region_ix + k + len(side)) % 3 == 0:
                    M.build_house(self, 'cottage', (x0 + 1, z0 + 1, min(x1 - 2, x0 + 7), min(z1 - 2, z0 + 6)), _face_road(side, off), 1,
                                  self.rng.choice(['low', 'half_hip', 'hip_low']), mat=self.rng.choice([st.roof] + list(st.roof_alts)))
                else:
                    self.field(x0, z0, x1, z1)

    def field(self, x0, z0, x1, z1):
        v, st = self.v, self.st
        crops = ['wheat[age=7]', 'carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]']
        c = self.rng.choice(crops)
        long_x = (x1 - x0) >= (z1 - z0)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    v.set(x, 0, z, st.fence)
                    continue
                k = (z - z0) if long_x else (x - x0)
                if k % 4 == 0:
                    v.set(x, -1, z, 'water')
                else:
                    v.set(x, -1, z, 'farmland[moisture=7]')
                    v.set(x, 0, z, c if k % 8 < 4 else self.rng.choice(crops))
        v.set(x0 + 1, 0, z0 + 1, 'hay_block[axis=y]')
        v.set(x0 + 1, 1, z0 + 1, 'hay_block[axis=y]')

    def street_lights(self):
        """道ぞいの灯り (道の縁の外、12 ごと。戸の前は避ける)"""
        v, st = self.v, self.st
        placed = []
        fronts = {(x, z) for (x, z, _o) in self.openings}
        for (pts, w, kind) in self.streets:
            if kind == 'lane':
                continue
            h = (w - 1) // 2 + 1
            for (ax, az), (bx, bz) in zip(pts, pts[1:]):
                L = math.hypot(bx - ax, bz - az)
                n = int(L // 12)
                for i in range(1, n + 1):
                    t = i / (n + 1)
                    px, pz = ax + (bx - ax) * t, az + (bz - az) * t
                    nx, nz = -(bz - az) / L, (bx - ax) / L
                    s = 1 if i % 2 else -1
                    x, z = int(round(px + nx * h * s)), int(round(pz + nz * h * s))
                    if self.occ.get((x, z)) in ('bld', 'wall', 'keep') or v.get(x, 0, z) not in (None, 'air'):
                        continue
                    if any(abs(x - a) <= 2 and abs(z - b) <= 2 for (a, b) in fronts) or any(math.hypot(x - a, z - b) < 7 for (a, b) in placed):
                        continue
                    lamp_post(v, st, x, 0, z, h=4)
                    placed.append((x, z))

    # ---------------------------------------------------------------- 施設 (市場のまわり)
    FAC = {'inn': ((20, 14), 'south'), 'smithy': ((14, 16), 'south'), 'tool_shop': ((12, 14), 'north'),
           'apothecary': ((12, 14), 'north'), 'chapel': ((16, 14), 'north'), 'guild': ((20, 13), 'north'),
           'stall': ((8, 9), 'north'), 'market': ((24, 13), 'north'), 'travel': ((14, 11), 'north'), 'training': ((12, 11), 'north')}
    TURN = ['north', 'east', 'south', 'west']

    def facilities_at(self, spots):
        """spots: 名前 → 置きたい所 (x, z)。そこに近く、戸の前が道か広場に面する所と向きを探して、回して建てる"""
        if getattr(self, 'no_fac', False):
            return
        for name, (nx, nz) in spots.items():
            (w, d), f0 = self.FAC[name]
            best = None
            for ring in (2 + EAVE, 1 + EAVE):
                best = self._fac_spot(name, nx, nz, w, d, f0, ring)
                if best:
                    break
            if not best:
                print('  facility', name, 'no spot near', (nx, nz))
                continue
            path = self._fac_path
            self.build_fac(name, *best)
            for c in path:
                self.occ[c] = 'road'
                self.roadw[c] = 3

    def landmark(self):
        """町ごとの目印 (landmarks.TOWN) を上の町に建て、広場の飾りは土地の町のまま"""
        import landmarks as LM
        fn, kw = LM.TOWN[self.region]
        cx, cz = self.lm
        r = fn(self.v, self.st, cx, cz, **kw)
        self.claim(cx - r, cz - r, cx + r, cz + r, 'landmark')
        if self.region == 'whale':
            self.mark('npc_chaser', cx, 0, cz + r + 2)
        getattr(self, 'plaza_' + self.region)()

    def outside_fac(self, name, side, s):
        """門の外、門から出る道の脇 (s = 道のどちら側) に、道へ戸を向けて建てる。戸から道まで小道"""
        (w, d), f0 = self.FAC[name]
        L, B = w, d          # 道に沿う長さ = 間口、道から離れる向き = 奥行き
        a0, b0 = 2, 11       # 壁から 2、道の中心から 11 (設計図の縁 EDGE の内に収める)
        if side in ('north', 'south'):
            sz = 1 if side == 'south' else -1
            zs = sorted((sz * (W + a0), sz * (W + a0 + L - 1)))
            xs = sorted((s * b0, s * (b0 + B - 1)))
            front = 'east' if s < 0 else 'west'
        else:
            sx = 1 if side == 'east' else -1
            xs = sorted((sx * (W + a0), sx * (W + a0 + L - 1)))
            zs = sorted((s * b0, s * (b0 + B - 1)))
            front = 'south' if s < 0 else 'north'
        k = (self.TURN.index(front) - self.TURN.index(f0)) % 4
        self.build_fac(name, xs[0], zs[0], front, k)
        # 小道: 戸の前から道 (中心から 3) まで、幅 3
        if side in ('north', 'south'):
            cz = (zs[0] + zs[1]) // 2
            xf = xs[1] + 1 if s < 0 else xs[0] - 1
            for x in range(min(xf, 0), max(xf, 0) + 1):
                if abs(x) >= 3:
                    for zz in (cz - 1, cz, cz + 1):
                        self.occ[(x, zz)] = 'road'
                        self.roadw[(x, zz)] = 3
        else:
            cx = (xs[0] + xs[1]) // 2
            zf = zs[1] + 1 if s < 0 else zs[0] - 1
            for z in range(min(zf, 0), max(zf, 0) + 1):
                if abs(z) >= 3:
                    for xx in (cx - 1, cx, cx + 1):
                        self.occ[(xx, z)] = 'road'
                        self.roadw[(xx, z)] = 3

    def _fac_spot(self, name, nx, nz, w, d, f0, ring):
        best = None
        if True:
            for r in range(0, 70):
                for x0 in range(nx - r, nx + r + 1):
                    for z0 in range(nz - r, nz + r + 1):
                        if max(abs(x0 - nx), abs(z0 - nz)) != r:
                            continue
                        for front in self.TURN:
                            k = (self.TURN.index(front) - self.TURN.index(f0)) % 4
                            ww, dd = (w, d) if k % 2 == 0 else (d, w)
                            x1, z1 = x0 + ww, z0 + dd
                            # 先に角と真ん中だけ見る (速くするため)
                            if any(self.occ.get(c) is not None for c in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, (z0 + z1) // 2))):
                                continue
                            if abs(x0) > W or abs(z0) > W or abs(x1) > W or abs(z1) > W:
                                continue
                            if not self.rect_free(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 0) or not self.rect_free(x0, z0, x1, z1, ring):
                                continue
                            if self._faces_road(x0, z0, x1, z1, front):
                                best = (x0, z0, front, k)
                                break
                        if best:
                            break
                    if best:
                        break
                if best:
                    break
        return best

    def _faces_road(self, x0, z0, x1, z1, front):
        """戸の前 (中ほどの幅 3) が、2〜7 先で道か広場に当たるか。間は空き地 (あとで小道にする: self._fac_path)"""
        dx, dz = DX[front]
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        path = []
        for k in range(2, 8):
            if front in ('north', 'south'):
                zf = (z0 if front == 'north' else z1) + dz * k
                cells = [(cx + a, zf) for a in (-1, 0, 1)]
            else:
                xf = (x0 if front == 'west' else x1) + dx * k
                cells = [(xf, cz + a) for a in (-1, 0, 1)]
            if all(self.occ.get(c) in ('road', 'plaza') for c in cells):
                self._fac_path = path
                return True
            if any(self.occ.get(c) is not None for c in cells):
                return False
            path += cells
        return False

    def build_fac(self, name, x0, z0, front, k):
        """施設を原点で元の向きのまま建て、k 回 (時計回り) 回して (x0, z0) へ"""
        (w, d), f0 = self.FAC[name]
        real = self.v
        tmp = Vox(seed=1)
        tmp.rng = self.rng
        self.v = tmp
        n_open, n_parts = len(self.openings), len(self.parts)
        getattr(self, 'town_' + name)(0, 0, w, d)
        self.v = real
        pts = [(x, z) for (x, y, z) in tmp.b]
        rx = [T._rot(x, z, None, k)[:2] for (x, z) in ((0, 0), (w, d))]
        ox = x0 - min(p[0] for p in rx)
        oz = z0 - min(p[1] for p in rx)
        before = set(real.b)
        for (x, y, z), s_ in tmp.b.items():
            X, Z, S = T._rot(x, z, s_, k)
            real.set(X + ox, y, Z + oz, S)
        self.own_new(before)
        for m, (x, y, z) in tmp.marks.items():
            X, Z, _ = T._rot(x, z, None, k)
            real.marks[m] = [X + ox, y, Z + oz]
        real.fake_doors.extend(tmp.fake_doors)
        for i in range(n_open, len(self.openings)):
            x, z, o = self.openings[i]
            X, Z, S = T._rot(x, z, 'facing=%s' % o, k)
            self.openings[i] = (X + ox, Z + oz, S.split('=')[1])
        for i in range(n_parts, len(self.parts)):
            nm, a0, b0, a1, b1 = self.parts[i]
            c = [T._rot(x, z, None, k)[:2] for (x, z) in ((a0, b0), (a1, b1))]
            self.parts[i] = (nm, min(p[0] for p in c) + ox, min(p[1] for p in c) + oz, max(p[0] for p in c) + ox, max(p[1] for p in c) + oz)
        ww, dd = (w, d) if k % 2 == 0 else (d, w)
        self.take(x0 - 1, z0 - 1, x0 + ww + 1, z0 + dd + 1)
        if name == 'chapel':
            # 鐘の塔 (元の向きで北東の角) が外へはみ出す分
            X, Z, _ = T._rot(w, 0, None, k)
            self.take(X + ox - 3, Z + oz - 3, X + ox + 3, Z + oz + 3)
        self.fac_rects = getattr(self, 'fac_rects', []) + [(name, x0, z0, x0 + ww, z0 + dd, front)]

    # ---------------------------------------------------------------- 組み立て
    def finish(self):
        self.yards()
        self.open_ground()
        self.paint_roads()
        self.street_lights()
        self.tidy_for_walkers()
        self.drop_floaters()
        self.fix_stairs()
        self.house_lights()
        self.drop_unsupported()
        if self.upper:
            import terrace
            print('  terrace', terrace.lift(self, self.upper, self.dy))
        if getattr(self, 'dress', True):
            import dress
            dress.dress(self)
        self.v.connect()
        return self.v

    def build(self):
        self.streets = []
        self.region_ix = list(STYLES).index(self.region)
        self.grass()
        getattr(self, 'plan_' + self.plan)()
        return self.finish()

    # ================================================================ A 街道と市場
    def plan_a(self):
        """一本の大通り (南の門 → 曲がりながら北の門)。真ん中でくさびの市場に太る。東西の通りは市場から、少しずれて二つの門へ。
        大通りの裏に平行な小路。区画は細長く、裏に長い庭 (中世の街道町)"""
        self.core, self.core_r, self.mid_r = (0, 0), 18, 32
        fx, fz = [(1, 1), (-1, 1), (1, -1), (-1, -1)][self.region_ix % 4]
        m = lambda pts: [(x * fx, z * fz) for (x, z) in pts]
        mp = lambda p: (p[0] * fx, p[1] * fz)
        poly = _jitter_poly(m([(0, -W), (22, -49), (40, -38), (W, 0), (44, 34), (20, 50), (0, W), (-24, 48), (-44, 30), (-W, 0), (-42, -36), (-20, -50)]))
        self.poly_wall(poly)
        LMC = mp((14, -37))
        self.set_upper(lambda x, z: z * fz < -27 + 4 * math.sin(x / 7.0) + 2 * math.sin(x / 3.1 + 1) or math.hypot(x - LMC[0], z - LMC[1]) < 15)
        main = m([(0, W - 4), (0, 40), (5, 24), (5, 12), (0, 0), (-4, -14), (-4, -30), (0, -40), (0, -W + 4)])
        market = m([(-8, -14), (8, -14), (13, 0), (12, 14), (5, 20), (-5, 20), (-12, 12), (-12, -2)])
        self.plaza_poly(market)
        self.plaza()
        self.street(main, 7, 'main')
        self.street(m([(-W + 4, 0), (-36, 0), (-24, 4), (-12, 4)]), 5)
        self.street(m([(12, -3), (24, -3), (36, 0), (W - 4, 0)]), 5)
        # 裏の小路 (大通りに平行、区画の奥の線)
        self.street(m([(-22, 12), (-22, 40)]), 3, 'lane')
        self.street(m([(22, -40), (22, -10)]), 3, 'lane')
        self.street(m([(24, 12), (24, 40)]), 3, 'lane')
        self.street(m([(4, 30), (24, 30)]), 3, 'lane')
        # 目印 (見張りの塔 = 北の大通りの曲がりの先を閉じる)
        self.lm = LMC
        self.lm2 = mp((-30, 20))
        self.keep_out(self.lm[0] - 12, self.lm[1] - 12, self.lm[0] + 12, self.lm[1] + 12)
        self.landmark()
        # 施設: 市場を囲む
        self.facilities_at({k_: mp(p_) for k_, p_ in {'inn': (-42, -20), 'market': (14, 6), 'guild': (20, 14), 'chapel': (-6, 24), 'stall': (-30, 30)}.items()})
        if getattr(self, 'no_fac', False):
            return
        # 門の外: 旅の館 (上の町と反対の門) と訓練所 (東か西の門)
        self.outside_fac('travel', 'south' if fz == 1 else 'north', -1)
        self.outside_fac('training', 'east' if fx == 1 else 'west', 1)
        self.outside_fac('smithy', 'west' if fx == 1 else 'east', -1)
        self.frontage(main, 7, quest_every=1)
        self.frontage(m([(-W + 4, 0), (-36, 0), (-24, 4), (-12, 4)]), 5, quest_every=1)
        self.frontage(m([(12, -3), (24, -3), (36, 0), (W - 4, 0)]), 5, quest_every=1)
        for pts in map(m, ([(-22, 12), (-22, 40)], [(22, -40), (22, -10)], [(24, 12), (24, 40)])):
            self.frontage(pts, 3, sides=(1, -1), depth=(9, 13))
        self.auto_lanes()
        self.infill()
        self.outskirts()

    # ================================================================ B 環の道と丘の上
    def plan_b(self):
        """真ん中の高台 (三段の石垣、上に名物の塔と社)。高台を巻く内の環 (幅 5) と、外の環の小路 (幅 3)。
        四つの門からの大通りは環で折れて高台の階段へ (真っ直ぐ抜けない)。斜めに抜ける小路が四本"""
        self.core, self.core_r, self.mid_r = (0, 0), 22, 34
        poly = [(round(52 * math.cos(a) * (1 + 0.05 * math.sin(3 * a))), round(52 * math.sin(a) * (1 + 0.05 * math.sin(3 * a))))
                for a in [k * math.pi / 8 for k in range(16)]]
        poly = _snap_gates(poly)
        self.poly_wall(poly)
        self.mound(0, 0, 11)
        ring_in = _ring(17, 12)
        self.street(ring_in + [ring_in[0]], 5)
        for side, (gx, gz) in GATES.items():
            dx, dz = DX[side]
            # 門から外の環まで真っ直ぐ、そこから少しずれて内の環へ
            j = 0 if side in ('north', 'west') else (4 if side == 'east' else -4)
            if side in ('north', 'south'):
                self.street([(0, gz - dz * 4), (0, dz * 44), (j, dz * 30), (j, dz * 18)], 7, 'main')
            else:
                self.street([(gx - dx * 4, 0), (dx * 44, 0), (dx * 30, j), (dx * 18, j)], 7, 'main')
        for a in (math.pi / 4, 7 * math.pi / 4):
            self.street([(round(18 * math.cos(a)), round(18 * math.sin(a))), (round(43 * math.cos(a)), round(43 * math.sin(a)))], 3, 'lane')
        self.lm = (0, -4)
        self.lm2 = (-30, -26)
        self.landmark_on_mound()
        self.plaza_on_mound()
        self.facilities_at({'inn': (-20, -40), 'smithy': (-40, 8), 'tool_shop': (24, 22), 'apothecary': (-36, -26), 'chapel': (26, -26)})
        if getattr(self, 'no_fac', False):
            return
        self.frontage(ring_in + [ring_in[0]], 5, sides=(1, -1), quest_every=1)
        for (pts, w, kind) in list(self.streets):
            if kind == 'main':
                self.frontage(pts, w)
        self.auto_lanes()
        self.infill()
        self.outskirts()

    def mound(self, cx, cz, r):
        """高台: 石垣で囲った平らな台 (上の面 y=2)。四方に三段の階段"""
        st, v = self.st, self.v
        self.mound_top = 3
        for x in range(cx - r - 4, cx + r + 5):
            for z in range(cz - r - 4, cz + r + 5):
                d = max(abs(x - cx), abs(z - cz)) * 0.5 + math.hypot(x - cx, z - cz) * 0.5
                if d <= r:
                    for y in range(-1, 2):
                        v.set(x, y, z, st.base.full if self.rng.random() > 0.2 else self.rng.choice(st.base2))
                    v.set(x, 2, z, v.pick(st.pave))
                    if d > r - 1:
                        v.set(x, 3, z, st.base.wall() if (x + z) % 3 else 'air')
                if d <= r + 3:
                    self.occ[(x, z)] = 'plaza'
                    self.roadw[(x, z)] = 9
        for side in DX:
            dx, dz = DX[side]
            for i in range(4):
                for t in range(-2, 3):
                    dd = r + 3 - i
                    x = cx + dx * dd + (t if dz else 0)
                    z = cz + dz * dd + (t if dx else 0)
                    for y in range(-1, i - 1):
                        v.set(x, y, z, st.base.full)
                    v.set(x, i - 1, z, st.base.stairs(OPP[side]) if i < 3 else v.pick(st.pave))
                    for y in range(i, 5):
                        v.set(x, y, z, 'air')

    def plaza_on_mound(self):
        """広場の印と飾り (依頼の板・札・腰掛け) を高台の上へ"""
        real = self.v
        sub = Vox(seed=2)
        sub.rng = self.rng
        self.v = sub
        self.plaza()
        self.v = real
        for (x, y, z), s_ in sub.b.items():
            if max(abs(x), abs(z)) * 0.5 + math.hypot(x, z) * 0.5 <= 11:
                real.set(x, y + self.mound_top, z, s_, keep=y < 0)
            elif y >= 0:
                real.set(x, y, z, s_)
        for k, (x, y, z) in sub.marks.items():
            real.marks[k] = [x, y + self.mound_top, z]

    def landmark_on_mound(self):
        # 高台の上に名物 (鯨見 = 見張りの塔)。塔と井戸・骨の弧は高台の上の高さで
        st, v = self.st, self.v
        tmp = self.v
        sub = Vox(seed=1)
        sub.rng = self.rng
        self.v = sub
        self.landmark()
        self.v = tmp
        for (x, y, z), s in sub.b.items():
            if abs(x) <= 12 and abs(z) <= 12:
                v.set(x, y + self.mound_top, z, s)
            else:
                v.set(x, y, z, s)
        for k in list(sub.marks):
            p = sub.marks[k]
            self.v.marks[k] = [p[0], p[1] + (self.mound_top if abs(p[0]) <= 12 and abs(p[2]) <= 12 else 0), p[2]]

    # ================================================================ C 小路の旧市街
    def plan_c(self):
        """ゆがんだ碁盤の小さな街区 (一辺 18〜26)。大通りは十字だが、街区ごとに折れる。小路の交わりに小さな広場三つ。
        街区は隙間なく町家で囲み、中は中庭 (Novigrad のような詰まった町)"""
        self.core, self.core_r, self.mid_r = (0, 0), 24, 38
        poly = _jitter_poly([(0, -W), (30, -48), (48, -30), (W, 0), (48, 28), (28, 48), (0, W), (-28, 48), (-50, 26), (-W, 0), (-46, -30), (-26, -48)], k=3)
        self.poly_wall(poly)
        self.plaza_poly([(-11, -11), (11, -11), (11, 11), (-11, 11)])
        self.plaza()
        xs = [-W + 4, -34, -14, 14, 32, W - 4]
        zs = [-W + 4, -32, -14, 14, 34, W - 4]
        jit = lambda: self.rng.choice((-2, -1, 0, 1, 2))  # noqa: E731
        # 縦の通り (大通り x=0 は折れる)
        self.street([(0, -W + 4), (0, -30), (3, -22), (3, -12)], 7, 'main')
        self.street([(0, 12), (-3, 22), (-3, 34), (0, W - 4)], 7, 'main')
        self.street([(-W + 4, 0), (-30, 0), (-22, -3), (-12, -3)], 7, 'main')
        self.street([(12, 0), (22, 3), (34, 3), (W - 4, 0)], 7, 'main')
        self.street([(-30 + jit(), 16), (-30 + jit(), 30), (-30 + jit(), 44)], 3, 'lane')
        self.street([(28 + jit(), -44), (28 + jit(), -16), (28 + jit(), 16), (28 + jit(), 44)], 3, 'lane')
        for z in (-30, 28):
            pts = [(-44 if z > 0 else -20, z + jit()), (-16, z + jit()), (16, z + jit()), (44, z + jit())]
            self.street(pts, 3, 'lane')
        self.street([(16, 28), (16, 14)], 3, 'lane')
        # 小さな広場 (小路の交わり)
        for (px, pz) in ((28, 28), (-30, 28), (28, -30)):
            self.plaza_poly([(px - 4, pz - 4), (px + 4, pz - 4), (px + 4, pz + 4), (px - 4, pz + 4)])
            self.piazzetta(px, pz)
        self.lm = (36, -40)
        self.lm2 = (-30, -10)
        self.keep_out(31, -45, 41, -35)
        self.landmark()
        self.facilities_at({'inn': (-40, -24), 'smithy': (14, -30), 'tool_shop': (14, 14), 'apothecary': (-28, 14), 'chapel': (34, 10)})
        if getattr(self, 'no_fac', False):
            return
        for (pts, w, kind) in list(self.streets):
            self.frontage(pts, w, depth=(8, 11) if kind == 'lane' else (9, 13), quest_every=1 if kind == 'main' else 0,
                          lot=(6, 9) if kind == 'lane' else (7, 11))
        self.auto_lanes()
        self.infill()
        self.outskirts()

    def piazzetta(self, cx, cz):
        st, v = self.st, self.v
        # 真ん中に井戸か木、角に腰掛けと灯り
        if (cx + cz) % 2:
            for x in range(cx - 1, cx + 2):
                for z in range(cz - 1, cz + 2):
                    v.set(x, 0, z, st.base.full if (x, z) != (cx, cz) else 'water')
            v.set(cx, -1, cz, 'water')
            for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
                v.fill(x, 1, z, x, 2, z, st.fence)
            v.fill(cx - 1, 3, cz - 1, cx + 1, 3, cz + 1, '%s_slab[type=bottom,waterlogged=false]' % st.wood)
        else:
            flowerbed(v, st, cx - 1, cz - 1, cx + 1, cz + 1, y=0, edge=True, density=0.6)
            tree(v, cx, 0, cz, st.trunk, st.leaves, h=6, r=2)
        bench(v, st, cx - 3, 0, cz + 3, 'north', length=2)
        lamp_post(v, st, cx + 3, 0, cz - 3, h=4)


def _face_road(side, off):
    """門の外の小屋の戸: 道 (x=0 か z=0) の側"""
    if side in ('north', 'south'):
        return 'east' if off < 0 else 'west'
    return 'south' if off < 0 else 'north'


def _inside(poly, x, z):
    c = False
    n = len(poly)
    for i in range(n):
        x1, z1 = poly[i]
        x2, z2 = poly[(i + 1) % n]
        if (z1 > z) != (z2 > z):
            xi = x1 + (z - z1) * (x2 - x1) / (z2 - z1)
            if x < xi:
                c = not c
    return c


def _dist_poly(poly, x, z):
    best = 1e9
    n = len(poly)
    for i in range(n):
        ax, az = poly[i]
        bx, bz = poly[(i + 1) % n]
        dx, dz = bx - ax, bz - az
        L2 = dx * dx + dz * dz or 1
        t = max(0, min(1, ((x - ax) * dx + (z - az) * dz) / L2))
        best = min(best, math.hypot(x - (ax + dx * t), z - (az + dz * t)))
    return best


def _jitter_poly(poly, k=2):
    import random
    r = random.Random(len(poly) * 31 + k)
    out = []
    for (x, z) in poly:
        if (x, z) in GATES.values():
            out.append((x, z))
        else:
            out.append((x + r.randint(-k, k), z + r.randint(-k, k)))
    return _snap_gates(out)


def _snap_gates(poly):
    """門の前後 10 は壁を門の線にそろえる (門楼がまっすぐ収まる)"""
    out = []
    for p in poly:
        if any(math.hypot(p[0] - gx, p[1] - gz) < 12 for (gx, gz) in GATES.values()):
            continue
        out.append(p)
    pts = out
    for side, (gx, gz) in GATES.items():
        if side in ('north', 'south'):
            pts += [(-10, gz), (0, gz), (10, gz)]
        else:
            pts += [(gx, -10), (gx, 0), (gx, 10)]
    pts.sort(key=lambda p: math.atan2(p[1], p[0]))
    return pts


def _ring(r, n, phase=0.0):
    return [(round(r * math.cos(phase + k * 2 * math.pi / n)), round(r * math.sin(phase + k * 2 * math.pi / n))) for k in range(n)]


def plan_png(t: PlanTown, path):
    """上から見た図 (道・家・庭・壁を色で)"""
    from PIL import Image
    S = 4
    N = 2 * EDGE + 1
    im = Image.new('RGB', (N * S, N * S), (98, 140, 72))
    px = im.load()
    col = {'road': (196, 186, 160), 'plaza': (226, 214, 176), 'bld': (90, 70, 66), 'yard': (128, 160, 92), 'wall': (70, 70, 76), 'keep': (150, 120, 90)}
    top = {}
    for (x, y, z), s in t.v.b.items():
        if s != 'air' and (top.get((x, z), (-99,))[0] < y):
            top[(x, z)] = (y, s)
    for x in range(-EDGE, EDGE + 1):
        for z in range(-EDGE, EDGE + 1):
            k = t.occ.get((x, z))
            c = col.get(k, (98, 140, 72))
            yt = top.get((x, z), (0, ''))
            if k == 'bld' and yt[0] > 0:
                c = T_col(yt[1], yt[0])
            elif yt[0] > 1 and 'leaves' in yt[1]:
                c = (52, 100, 50)
            for i in range(S):
                for j in range(S):
                    px[(x + EDGE) * S + i, (z + EDGE) * S + j] = c
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def T_col(s, y):
    n = s.split('[')[0]
    base = (120, 110, 105)
    for k, c in (('deepslate', (62, 64, 72)), ('mossy_cobblestone', (90, 110, 80)), ('dark_oak', (70, 50, 30)), ('prismarine', (60, 120, 110)),
                 ('copper', (80, 150, 130)), ('blackstone', (50, 45, 55)), ('mud', (140, 108, 80)), ('stone_brick', (125, 125, 125))):
        if k in n:
            base = c
            break
    f = 0.7 + min(0.6, y / 30.0)
    return tuple(min(255, int(c * f)) for c in base)


def main():
    args = sys.argv[1:]
    if args and args[0] == 'towns':
        # 本番: 四つの土地の町を A 案 (街道と市場) で。town_<region>.json
        for region in [a_ for a_ in args[1:] if a_ in STYLES] or list(STYLES):
            t = PlanTown(region, 'a')
            v = t.build()
            meta = {'rect': [-W - 18, -W - 18, W + 18, W + 18], 'level': True, 'ring': 8, 'fillWater': True, 'height': 40}
            out = [(x, z) for (x, y, z), b_ in v.b.items() if b_ != 'air' and max(abs(x), abs(z)) > W + 18]
            if out:
                raise SystemExit('town_%s: %d blocks outside the site rect, e.g. %s' % (region, len(out), out[:3]))
            data = v.runs(meta)
            data['site'] = 'town_' + region
            _check_blocks(data, region)
            path = os.path.join(T.DATA, 'town_%s.json' % region)
            vox.save(path, data)
            need = {'board', 'inn', 'shop', 'stand', 'sign', 'rest', 'inn_bed', 'folk_a', 'folk_b'} | {'npc_' + r_ for r_ in ROLES}
            print('wrote', region, os.path.getsize(path) // 1024, 'KB', 'houses', len(t.plots), 'quests', t.quests,
                  'missing', sorted(need - set(v.marks)), 'touching', len(t.check_gaps()))
            plan_png(t, os.path.join(T.ROOT, 'docs', 'review', 'town_roughs', 'town_%s_plan.png' % region))
        return
    if not args or args[0] != 'rough':
        print(__doc__)
        return
    region = args[1] if len(args) > 1 else 'whale'
    for plan in (args[2] if len(args) > 2 else 'abc'):
        t = PlanTown(region, plan)
        v = t.build()
        meta = {'rect': [-EDGE, -EDGE, EDGE, EDGE], 'level': True, 'ring': 8, 'fillWater': True, 'height': 40}
        data = v.runs(meta)
        data['site'] = 'town_rough_' + plan
        _check_blocks(data, region)
        path = os.path.join(T.DATA, 'town_rough_%s.json' % plan)
        vox.save(path, data)
        n = sum(1 for s in v.b.values() if s != 'air')
        kinds = {}
        for p in t.plots:
            kinds[p[4]] = kinds.get(p[4], 0) + 1
        print('plan', plan, os.path.getsize(path) // 1024, 'KB', n, 'blocks', 'houses', len(t.plots), kinds, 'quests', t.quests,
              'missing marks', sorted({'board', 'inn', 'shop', 'stand', 'sign', 'rest', 'inn_bed', 'npc_innkeeper', 'npc_tool_merchant'} - set(v.marks)))
        print('  touching pairs', len(t.check_gaps()))
        plan_png(t, os.path.join(T.ROOT, 'docs', 'review', 'town_roughs', 'plan_%s.png' % plan))


if __name__ == '__main__':
    main()
