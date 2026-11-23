"""鯨の鎚（look5 の手本）。

  cd <作業フォルダ> && python -I tools/models/x32/look5/whale_hammer.py
作業フォルダに v5.json（箱と UV）と v5_base.png（下塗り）を書く。Blockbench で create_texture(v5_base.png) → 箱を place_cube で一つずつ置く → 筆で描き足す → 書き出して bb_import。

whale hammer v5: slim, one hue family (deep sea -> foam), bone accents, one glow.
Writes v5.json (cubes with UVs) and v5_base.png (base paint: per-face light/shadow, gradient along length, rim lines)."""
import json, math
from PIL import Image

SEA = ['#0e1430', '#16224a', '#1c3466', '#244a82', '#2c649e', '#3c82b8', '#5ca4cc', '#90cce0', '#c8eef6', '#f0fdff']
BONE = ['#4a3430', '#7a5c4a', '#a88a6a', '#ccb08a', '#e8d4ae', '#fff6e2']
GLOW = ['#0e4a98', '#1a78d0', '#3cb0f0', '#86e0ff', '#c8f6ff', '#ffffff']
RAMP = {'sea': SEA, 'bone': BONE, 'glow': GLOW}

C = []  # name, mat, from, to, origin, rot_z, base index fn kind
def c(n, m, f, t, base, origin=None, rot=0, kind=None):
    C.append(dict(name=n, mat=m, frm=f, to=t, base=base, origin=origin or [(f[0] + t[0]) / 2, (f[1] + t[1]) / 2, 0], rot=rot, kind=kind))

# grip and shaft (slim, gradient dark -> light toward the head)
c('pommel_tip_glow', 'glow', [-1, -17, -1], [1, -14, 1], 3)
c('pommel', 'bone', [-2, -14, -2], [2, -11, 2], 3)
c('pommel_flare', 'sea', [-2.5, -11, -2.5], [2.5, -9, 2.5], 2)
c('shaft', 'sea', [-1.5, -9, -1.5], [1.5, 22, 1.5], None, kind='shaft')
c('band_low', 'bone', [-2, -1, -2], [2, 1, 2], 3)
c('band_high', 'bone', [-2, 15, -2], [2, 17, 2], 3)
c('collar', 'bone', [-2.5, 20, -2.5], [2.5, 24, 2.5], 3, kind='collar')
# head core with the crystal
c('core', 'sea', [-4, 24, -3], [4, 35, 3], 4, kind='core')
c('gem_glow', 'glow', [-2, 26, -3.5], [2, 33, 3.5], 3, kind='gem')
c('crown', 'bone', [-3, 35, -2], [3, 37, 2], 4)
c('crown_tip', 'bone', [-1, 37, -1], [1, 40, 1], 4)
# striking side: broad face with bone rim lines
c('neck', 'sea', [4, 27, -2.5], [6, 32, 2.5], 3)
c('strike_block', 'sea', [6, 23, -4], [12, 36, 4], 4, kind='strike')
c('strike_face', 'bone', [12, 24, -3.5], [14, 35, 3.5], 4, kind='face')
# back: whale fluke, two lobes of rotated flat segments, deep sea at the root -> foam at the tips
c('fluke_stem', 'sea', [-7, 28.5, -2], [-4, 31.5, 2], 4)
for lobe, sgn in (('up', 1), ('dn', -1)):
    p = [-6.5, 30 + sgn * 1.0]
    segs = [(155, 5, 4, 1.5), (130, 5, 3, 1), (105, 5, 2, 1)]
    for k, (a, L, W, D) in enumerate(segs):
        ang = a if sgn > 0 else -a
        r = math.radians(ang)
        q = [p[0] + L * math.cos(r), p[1] + L * math.sin(r)]
        cx, cy = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
        c(f'fluke_{lobe}_{k}', 'sea', [round(cx - L / 2 - 0.5, 2), round(cy - W / 2, 2), -D], [round(cx + L / 2 + 0.5, 2), round(cy + W / 2, 2), D],
          None, origin=[round(cx, 2), round(cy, 2), 0], rot=round(ang, 1), kind=('fluke', k, 3))
        p = q

# UV packing
def dims(e):
    return [max(1, math.ceil(e['to'][i] - e['frm'][i] - 1e-6)) for i in range(3)]
rects = []
for e in C:
    dx, dy, dz = dims(e)
    for face, (w, h) in {'south': (dx, dy), 'north': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}.items():
        rects.append((h, w, e['name'], face))
rects.sort(key=lambda r: (-r[0], -r[1]))
W = 128
x = y = rowh = 0
uv = {}
for h, w, n, face in rects:
    if x + w > W:
        x, y, rowh = 0, y + rowh, 0
    uv.setdefault(n, {})[face] = [x, y, x + w, y + h]
    x += w; rowh = max(rowh, h)
H = 1
while H < y + rowh:
    H *= 2
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
OFF = {'up': 1, 'south': 0, 'north': 0, 'east': -1, 'west': -1, 'down': -2}

def put(px, py, ramp, t):
    t = max(0, min(len(ramp) - 1, int(round(t))))
    h = ramp[t]
    img.putpixel((px, py), tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,))

for e in C:
    ramp = RAMP[e['mat']]
    for face, (u0, v0, u1, v1) in uv[e['name']].items():
        w, h = u1 - u0, v1 - v0
        for i in range(w):
            for j in range(h):
                kind = e['kind']
                if kind == 'shaft':
                    wy = e['to'][1] - j if face not in ('up', 'down') else (e['to'][1] if face == 'up' else e['frm'][1])
                    b = 1 + 3.5 * (wy - e['frm'][1]) / (e['to'][1] - e['frm'][1])
                elif isinstance(kind, tuple):  # fluke: gradient along the segment, root dark -> tip foam
                    k, n = kind[1], kind[2]
                    if face in ('south', 'north', 'up', 'down'):
                        tt = i / max(1, w - 1)
                        if face == 'north':
                            tt = 1 - tt
                    else:
                        tt = 1.0 if face == 'east' else 0.0
                    s = (k + tt) / n
                    b = 3 + 5.5 * s
                else:
                    b = e['base']
                t = b + OFF[face]
                if face != 'down':
                    if j == 0 and h >= 2:
                        t += 1
                    elif j == h - 1 and h >= 3:
                        t -= 1
                    if w >= 3 and i == 0 and 0 < j < h - 1:
                        t += 0.6
                    elif w >= 3 and i == w - 1 and 0 < j < h - 1:
                        t -= 0.6
                    if i == 0 and j == 0 and w >= 2 and h >= 2:
                        t += 1
                ramp2 = ramp
                # grip wrap: bone strips across the lower shaft
                if kind == 'shaft' and face in ('south', 'north', 'east', 'west'):
                    wy = e['to'][1] - j
                    if wy < 12 and ((i + j) % 5) in (0, 1):
                        ramp2, t = BONE, (3.4 if (i + j) % 5 == 0 else 2.2) + OFF[face] * 0.5
                # rim lines: bone inset line one pixel inside the border on the big faces
                if kind in ('strike', 'core', 'collar') and face in ('south', 'north') and w >= 5 and h >= 5:
                    if (i in (1, w - 2) and 1 <= j <= h - 2) or (j in (1, h - 2) and 1 <= i <= w - 2):
                        ramp2, t = BONE, 4 if (j == 1 or i == 1) else 2.6
                if kind == 'strike' and face in ('east', 'west') and w >= 5 and h >= 5:
                    if (i in (1, w - 2) and 1 <= j <= h - 2) or (j in (1, h - 2) and 1 <= i <= w - 2):
                        ramp2, t = BONE, 3.2
                if kind == 'face' and face == 'east':   # the striking face: polished bone with a sea seam
                    t = 5 if j < 2 else (3 if j >= h - 2 else 4)
                    if i in (0, w - 1) or j in (0, h - 1):
                        t = 2.5
                if kind == 'gem' and face in ('south', 'north'):   # faceted diamond
                    cxp, cyp = (w - 1) / 2, (h - 1) / 2
                    d = abs(i - cxp) / (w / 2) + abs(j - cyp) / (h / 2)
                    t = 5 if d < 0.35 else (4 if d < 0.7 else (2.5 if d < 1.0 else 1))
                    if i < cxp and j < cyp and 0.35 <= d < 0.7:
                        t = 5
                # foam tip: the last pixel column of the last fluke segment is near white
                if isinstance(kind, tuple) and kind[1] == kind[2] - 1 and face in ('south', 'north'):
                    edge = (i == w - 1) if face == 'south' else (i == 0)
                    if edge:
                        t = 9
                put(u0 + i, v0 + j, ramp2, t)
img.save('v5_base.png')
out = []
for e in C:
    out.append({'name': e['name'], 'from': e['frm'], 'to': e['to'], 'origin': e['origin'], 'rotation': [0, 0, e['rot']],
                'faces': [{'face': f, 'uv': v} for f, v in uv[e['name']].items()]})
json.dump(out, open('v5.json', 'w'))
print('texture', W, H, 'cubes', len(C))
for o in out:
    print(json.dumps({k: o[k] for k in ('name', 'from', 'to', 'origin', 'rotation')}), json.dumps(o['faces']))
