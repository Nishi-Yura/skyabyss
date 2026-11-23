# armor_skin_cover.py <set.bbmodel> : standing pose; casts rays out of the player skin (x32 units) and lists skin spots no armor cube covers (0 = nothing pokes out)
import json, sys, math
from collections import defaultdict
d = json.load(open(sys.argv[1], encoding='utf8'))
cubes = []
for e in d['elements']:
    if e.get('type', 'cube') != 'cube':
        continue
    cubes.append((e['from'], e['to'], e.get('origin', [0, 0, 0]), [math.radians(a) for a in e.get('rotation', [0, 0, 0])], e['name']))

def inv(p, o, r):
    # undo R = Rz*Ry*Rx  ->  apply Rx^-1 * Ry^-1 * Rz^-1
    x, y, z = (p[i] - o[i] for i in range(3))
    ax, ay, az = r
    x, y = x*math.cos(-az) - y*math.sin(-az), x*math.sin(-az) + y*math.cos(-az)
    x, z = x*math.cos(-ay) + z*math.sin(-ay), -x*math.sin(-ay) + z*math.cos(-ay)
    y, z = y*math.cos(-ax) - z*math.sin(-ax), y*math.sin(-ax) + z*math.cos(-ax)
    return [x + o[0], y + o[1], z + o[2]]

def covered(p):
    for f, t, o, r, n in cubes:
        q = inv(p, o, r)
        if all(f[i] - 1e-6 <= q[i] <= t[i] + 1e-6 for i in range(3)):
            return True
    return False

# skin boxes: name, lo, hi, faces to skip
boxes = [('head', (-8, 48, -8), (8, 64, 8), {'-y'}),
         ('body', (-8, 24, -4), (8, 48, 4), {'+y', '-y'}),
         ('Rarm', (8, 24, -4), (16, 48, 4), {'-x', '-y'}),
         ('Larm', (-16, 24, -4), (-8, 48, 4), {'+x', '-y'}),
         ('Rleg', (-0.2, 0, -4), (7.8, 24, 4), {'-x', '+y', '-y'}),
         ('Lleg', (-7.8, 0, -4), (0.2, 24, 4), {'+x', '+y', '-y'})]
S = 0.5
E = 0.05
bad = defaultdict(list)
total = 0
for name, lo, hi, skip in boxes:
    for ax in range(3):
        for sgn, face in ((-1, '-'), (1, '+')):
            key = face + 'xyz'[ax]
            if key in skip:
                continue
            u, v = [i for i in range(3) if i != ax]
            a = lo[u] + S / 2
            while a < hi[u]:
                b = lo[v] + S / 2
                while b < hi[v]:
                    p = [0, 0, 0]
                    p[ax] = (hi[ax] + E) if sgn > 0 else (lo[ax] - E)
                    p[u], p[v] = a, b
                    total += 1
                    hit = False
                    for k in range(41):  # ray outward along the face normal, 0..4 units
                        q = list(p)
                        q[ax] += sgn * 0.1 * k
                        if covered(q):
                            hit = True
                            break
                    if not hit:
                        bad[(name, key)].append(p)
                    b += S
                a += S
print('skin samples', total, 'uncovered', sum(len(v) for v in bad.values()))
for k, pts in sorted(bad.items(), key=lambda kv: -len(kv[1])):
    lo = [round(min(p[i] for p in pts), 1) for i in range(3)]
    hi = [round(max(p[i] for p in pts), 1) for i in range(3)]
    print(f'  {k[0]:5s} {k[1]} n={len(pts):4d}  x {lo[0]}..{hi[0]}  y {lo[1]}..{hi[1]}  z {lo[2]}..{hi[2]}')
