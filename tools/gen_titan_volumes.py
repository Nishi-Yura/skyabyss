"""Bakes the keep-out volumes of the four titans (docs/design/02 section on titan solidity) from their near low-poly meshes.

Each titan gets src/main/resources/data/skyabyss/titan_volumes/<name>.json: a column grid in the titan's local frame
(side = left +, up, forward), cell 2 blocks, every column a list of solid [lo, hi] spans (half-block ints) already grown by MARGIN blocks.
The Java side (titan/solid/TitanVolume) only looks columns up.  python tools/gen_titan_volumes.py
"""
import json
import math
import os

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
CELL = 2.0
MARGIN = 3.0
CORE_MARGIN = 0.5
# bones that do not make the keep-out volume (decoration that sticks out, or has its own corridor)
SKIP = ('smoke', 'chain')
# bones that move a lot: grown further so a flapping wing / swinging leg stays outside too (blocks)
EXTRA = {'wing_l': 12.0, 'wing_r': 12.0, 'hand_l': 18.0, 'hand_r': 18.0, 'fluke': 4.0, 'tail': 2.0,
         'fin_left': 3.0, 'fin_right': 3.0, 'fin_front_left': 3.0, 'fin_front_right': 3.0, 'fin_rear_left': 3.0, 'fin_rear_right': 3.0,
         'leg_fl': 2.0, 'leg_fr': 2.0, 'leg_rl': 2.0, 'leg_rr': 2.0, 'jaw': 2.0, 'head': 1.5}
TITANS = ['stone_whale', 'tide_shell', 'thunder_wing', 'ember_titan']


def bone_spans(verts, tris, x0, z0, nx, nz):
    """Vertical-ray parity per column for one bone. verts in local blocks (side, up, fwd). Returns {(i, j): [(lo, hi), ...]}."""
    hits = {}
    jx, jz = 0.0131, 0.0071
    for t in tris:
        a, b, c = verts[t[0]], verts[t[1]], verts[t[2]]
        minx, maxx = min(a[0], b[0], c[0]), max(a[0], b[0], c[0])
        minz, maxz = min(a[2], b[2], c[2]), max(a[2], b[2], c[2])
        i0 = max(0, int(math.floor((minx - x0) / CELL)))
        i1 = min(nx - 1, int(math.floor((maxx - x0) / CELL)))
        j0 = max(0, int(math.floor((minz - z0) / CELL)))
        j1 = min(nz - 1, int(math.floor((maxz - z0) / CELL)))
        # triangle plane in (x, z) -> y
        d = (b[0] - a[0]) * (c[2] - a[2]) - (c[0] - a[0]) * (b[2] - a[2])
        if abs(d) < 1e-9:
            continue
        for i in range(i0, i1 + 1):
            px = x0 + (i + 0.5) * CELL + jx
            for j in range(j0, j1 + 1):
                pz = z0 + (j + 0.5) * CELL + jz
                u = ((px - a[0]) * (c[2] - a[2]) - (c[0] - a[0]) * (pz - a[2])) / d
                v = ((b[0] - a[0]) * (pz - a[2]) - (px - a[0]) * (b[2] - a[2])) / d
                if u < 0 or v < 0 or u + v > 1:
                    continue
                y = a[1] + u * (b[1] - a[1]) + v * (c[1] - a[1])
                hits.setdefault((i, j), []).append(y)
    spans = {}
    for k, ys in hits.items():
        ys.sort()
        s = []
        for q in range(0, len(ys) - 1, 2):
            s.append((ys[q], ys[q + 1]))
        if s:
            spans[k] = s
    return spans


def merge(spans):
    spans = sorted(spans)
    out = []
    for lo, hi in spans:
        if out and lo <= out[-1][1] + 1e-6:
            out[-1][1] = max(out[-1][1], hi)
        else:
            out.append([lo, hi])
    return out


def build(name):
    m = json.load(open(os.path.join(ROOT, 'assets', 'skyabyss', 'lowpoly', name + '.json')))
    bones = {}
    allv = []
    for bn, b in m['bones'].items():
        if any(bn.startswith(s) for s in SKIP) or not b['triangles']:
            continue
        v = np.array(b['vertices'], float) / 2.0
        loc = np.stack([v[:, 0], v[:, 1], -v[:, 2]], axis=1)  # side (left +), up, forward
        bones[bn] = (loc, b['triangles'])
        allv.append(loc)
    allv = np.concatenate(allv)
    pad = MARGIN + 12.0
    x0 = math.floor((allv[:, 0].min() - pad) / CELL) * CELL
    z0 = math.floor((allv[:, 2].min() - pad) / CELL) * CELL
    nx = int(math.ceil((allv[:, 0].max() + pad - x0) / CELL))
    nz = int(math.ceil((allv[:, 2].max() + pad - z0) / CELL))
    base = {}
    base0 = {}
    for bn, (loc, tris) in bones.items():
        extra = EXTRA.get(bn, 0.0)
        for (i, j), s in bone_spans(loc, tris, x0, z0, nx, nz).items():
            for lo, hi in s:
                base.setdefault((i, j), []).append((lo - extra, hi + extra))
                base0.setdefault((i, j), []).append((lo, hi))
    # grow by a margin (a ball): MARGIN for the keep-out volume, CORE_MARGIN for the hull that projectiles and the hard push look at
    def grow(base, margin):
        reach = int(math.ceil(margin / CELL)) + 1
        grown = {}
        for (i, j), s in base.items():
            for di in range(-reach, reach + 1):
                for dj in range(-reach, reach + 1):
                    dist = math.hypot(di, dj) * CELL
                    if dist > margin + CELL * 0.5:
                        continue
                    rise = math.sqrt(max(0.0, margin * margin - min(dist, margin) ** 2))
                    ii, jj = i + di, j + dj
                    if 0 <= ii < nx and 0 <= jj < nz:
                        grown.setdefault((ii, jj), []).extend((lo - rise, hi + rise) for lo, hi in s)
        cols = []
        for i in range(nx):
            for j in range(nz):
                sp = merge(grown.get((i, j), []))
                flat = []
                for lo, hi in sp:
                    flat += [int(math.floor(lo * 2)), int(math.ceil(hi * 2))]
                cols.append(flat)
        return cols

    cols = grow(base, MARGIN)
    core = grow(base0, CORE_MARGIN)
    out = {'cell': CELL, 'x0': x0, 'z0': z0, 'nx': nx, 'nz': nz, 'margin': MARGIN, 'cols': cols, 'core': core}
    d = os.path.join(ROOT, 'data', 'skyabyss', 'titan_volumes')
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, name + '.json')
    with open(path, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    filled = sum(1 for c in cols if c)
    print(name, 'grid', nx, 'x', nz, 'filled columns', filled, 'bytes', os.path.getsize(path))


if __name__ == '__main__':
    for n in TITANS:
        build(n)
