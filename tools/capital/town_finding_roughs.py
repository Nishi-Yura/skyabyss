# -*- coding: utf-8 -*-
"""都の施設を見つけやすくする 3 案のラフ (提案のみ。ゲームも capital.json も変えない)。

python -I tools/capital/town_finding_roughs.py → docs/review/town_finding/{A,B,C}_plan.png と {A,B,C}_street.png
  平面図 = 本物の capital.json の上面図 + Town の足もと (parts) に提案を重ねる。
  通りの絵 = capital.json の広場の東 (道具屋・預かり所・組合) の等角ボクセル図に、提案の物を足して描く。
座標は設計図と同じ (中心 0,0、+z が南)。
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
JSON = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss', 'opening', 'capital.json')
OUT = os.path.join(ROOT, 'docs', 'review', 'town_finding')
FONT = 'C:/Windows/Fonts/meiryo.ttc'

# 種類の色
TYPE = {'shop': ((232, 184, 60), '買う・預ける'), 'rest': ((70, 140, 220), '休む・旅'), 'work': ((222, 90, 60), '鍛える・戦う'),
        'story': ((160, 100, 210), '物語・祈り'), 'dark': ((120, 124, 132), '裏通り')}
# key, 名前, 字, 種類, 戸の位置 (x,z), 足もと (parts の名前)
FAC = [
    ('tool_shop', '道具屋', '道', 'shop', (32, -32), 'tool_shop'), ('storage', '預かり所', '倉', 'shop', (36, -6), 'storage'),
    ('trade', '交易所', '商', 'shop', (88, -22), 'trade'), ('apothecary', '薬師の店', '薬', 'shop', (60, 5), 'apothecary'),
    ('inn', '鐘の宿', '宿', 'rest', (-24, 5), 'inn'), ('stable', '厩舎', '馬', 'rest', (89, 5), 'stable'),
    ('perch', '子の止まり木', '木', 'rest', (-88, 5), 'perch_lodge'),
    ('guild', '鯨追いの組合', '組', 'work', (51, -22), 'guild'), ('smithy', '鍛冶場', '鍛', 'work', (12, 12), 'smithy'),
    ('training', '訓練場', '訓', 'work', (7, 48), 'training_yard'),
    ('shrine', '四巨神の社', '社', 'story', (-50, -21), 'shrine'), ('keep', '空見の砦', '砦', 'story', (32, -52), 'keep'),
    ('hall', '空見の館', '館', 'story', (-48, -85), 'hall'),
    ('tavern', '酒場', '酒', 'dark', (-32, 35), 'tavern'), ('black_market', '闇市', '闇', 'dark', (52, 46), 'black_market'),
    ('mapmaker', '地図屋', '地', 'dark', (90, 68), 'mapmaker'),
]
GATES = [('鯨門', 0, -97), ('灰門', 0, 97), ('風門', -97, 0), ('潮門', 97, 0)]
PLAZA = (0, -16)
TYPE_ORDER = ['shop', 'rest', 'work', 'story', 'dark']


def font(n):
    return ImageFont.truetype(FONT, n)


def name_col(n):
    n = n.split('[')[0].replace('minecraft:', '')
    for k, c in (('deepslate_tile', (60, 64, 74)), ('red_nether', (150, 40, 44)), ('nether_brick', (110, 40, 44)), ('stone_brick', (138, 138, 138)),
                 ('mud_brick', (140, 110, 80)), ('tuff', (110, 112, 104)), ('brick', (160, 80, 60)), ('calcite', (225, 225, 220)),
                 ('quartz', (235, 232, 225)), ('diorite', (205, 205, 200)), ('sandstone', (220, 205, 160)), ('copper', (90, 160, 140)),
                 ('leaves', (50, 110, 50)), ('grass', (80, 130, 60)), ('water', (60, 100, 200)), ('wool', (220, 220, 220)),
                 ('glass', (150, 190, 210)), ('lantern', (255, 210, 110)), ('planks', (140, 100, 60)), ('log', (100, 75, 50)), ('wood', (100, 75, 50)),
                 ('andesite', (140, 142, 142)), ('blackstone', (50, 45, 55)), ('deepslate', (75, 75, 80)), ('gravel', (130, 125, 120)),
                 ('dirt', (110, 80, 55)), ('mud', (100, 80, 70)), ('cobble', (115, 115, 115)), ('iron', (170, 170, 175)), ('stone', (128, 128, 128))):
        if k in n:
            return c
    return (150, 150, 150)


def load():
    d = json.load(open(JSON, encoding='utf-8'))
    pal = [name_col(p) if 'air' not in p else None for p in d['palette']]
    vox = {}
    for r in d['runs']:
        y, z, x = r[0], r[1], r[2]
        for i in range(3, len(r), 2):
            idx, n = r[i], r[i + 1]
            if idx >= 0 and pal[idx] is not None:
                for k in range(n):
                    vox[(x + k, y, z)] = pal[idx]
            x += n
    return vox, d['marks']


def get_parts():
    sys.path.insert(0, HERE)
    import town
    import vox as V
    t = town.Town(V.STYLES['b'])
    t.build()
    return {p[0]: p for p in t.parts}


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


# ------------------------------------------------------------------ 平面図
X0, X1, Z0, Z1 = -140, 120, -120, 120
S = 5


def PX(x):
    return (x - X0) * S


def PZ(z):
    return (z - Z0) * S + 70


def base_plan(vox, title, note):
    top = {}
    for (x, y, z), c in vox.items():
        if y < -1:
            continue
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, c)
    W, H = (X1 - X0 + 1) * S, (Z1 - Z0 + 1) * S + 70
    im = Image.new('RGB', (W, H), (40, 60, 38))
    px = im.load()
    for (x, z), (y, c) in top.items():
        f = 0.7 + min(0.5, max(0, y) / 60.0)
        c = tuple(min(255, int(v * f * 0.8 + 30)) for v in c)
        for i in range(S):
            for j in range(S):
                px[(x - X0) * S + i, (z - Z0) * S + j + 70] = c
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W, 69], fill=(28, 30, 34))
    d.text((14, 8), title, fill=(245, 240, 225), font=font(24))
    d.text((14, 40), note, fill=(205, 200, 190), font=font(15))
    return im, d


def footprint(d, p, col, w=3):
    d.rectangle([PX(p[1]), PZ(p[2]), PX(p[3] + 1), PZ(p[4] + 1)], outline=col, width=w)


def tint(im, p, col, t=0.55):
    box = (PX(p[1]), PZ(p[2]), PX(p[3] + 1), PZ(p[4] + 1))
    reg = im.crop(box)
    im.paste(Image.blend(reg, Image.new('RGB', reg.size, col), t), box)


def tag(d, x, z, ch, col, r=13):
    cx, cz = PX(x), PZ(z)
    d.rounded_rectangle([cx - r, cz - r, cx + r, cz + r], radius=5, fill=col, outline=(20, 20, 20), width=2)
    f = font(int(r * 1.5))
    tw = d.textlength(ch, font=f)
    d.text((cx - tw / 2, cz - r * 0.95), ch, fill=(20, 20, 20), font=f)


def label(d, x, z, s, size=14, fg=(255, 255, 255), dy=0):
    f = font(size)
    tw = d.textlength(s, font=f)
    cx, cz = PX(x) - tw / 2, PZ(z) + dy
    d.rectangle([cx - 3, cz - 1, cx + tw + 3, cz + size + 3], fill=(20, 20, 24))
    d.text((cx, cz), s, fill=fg, font=f)


def legend(d, W, H, extra=()):
    x = 14
    for k in TYPE_ORDER:
        d.rectangle([x, H - 28, x + 16, H - 12], fill=TYPE[k][0], outline=(0, 0, 0))
        d.text((x + 22, H - 31), TYPE[k][1], fill=(240, 240, 230), font=font(15))
        x += 22 + d.textlength(TYPE[k][1], font=font(15)) + 22
    for s in extra:
        d.text((x, H - 31), s, fill=(240, 240, 230), font=font(15))


def finish(im, name):
    if im.width > 1600:
        im = im.resize((1600, int(im.height * 1600 / im.width)), Image.LANCZOS)
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name), optimize=True)


def plan_A(vox, parts):
    im, d = base_plan(vox, 'A 吊り大看板 + 種類ごとの屋根の色', '施設の戸の前に大きな吊り看板 (種類色・字・絵)、屋根は種類色に。遠くからは「色」、近くでは「看板」で分かる')
    for k, nm, ch, ty, door, pn in FAC:
        tint(im, parts[pn], TYPE[ty][0], 0.6)
    d = ImageDraw.Draw(im)
    for k, nm, ch, ty, door, pn in FAC:
        p = parts[pn]
        footprint(d, p, TYPE[ty][0], w=3)
        tag(d, door[0], door[1], ch, TYPE[ty][0], 14)
        label(d, (p[1] + p[3]) / 2, (p[2] + p[4]) / 2, nm, 14)
    door = FAC[0][4]
    d.ellipse([PX(door[0] - 30), PZ(door[1] - 30), PX(door[0] + 30), PZ(door[1] + 30)], outline=(255, 255, 255), width=2)
    d.text((PX(door[0] + 20), PZ(door[1] - 36)), '大看板が読める目安 30 マス', fill=(255, 255, 255), font=font(13))
    legend(d, im.width, im.height, ('  角丸の札 = 戸の前の大看板',))
    finish(im, 'A_plan.png')


def route(door):
    """戸から一番近い大通りの芯まで (大通りの芯は x=0 と z=0)。直角に折れる道 (点列)。"""
    x, z = door
    if abs(z) <= abs(x):
        return [(0, -16), (0, 0), (x, 0), (x, z)]
    return [(0, -16), (0, z), (x, z)]


def plan_B(vox, parts):
    im, d = base_plan(vox, 'B 区画の案内: 広場の案内板 + 通りの名の標識 + 地図の印', '広場に大きな案内板 (一覧と方角)。交差点ごとに「通りの名 + そこにある施設」の標識。地図/HUD に施設の印')
    zones = [((4, -35, 100, 2), (232, 184, 60), '商いの通り (東)'), ((-100, -40, -4, 2), (160, 100, 210), '祈りと旅の通り (西)'),
             ((-45, 4, 45, 56), (222, 90, 60), '職の通り (南)')]
    for (x0, z0, x1, z1), col, nm in zones:
        reg = im.crop((PX(x0), PZ(z0), PX(x1), PZ(z1)))
        im.paste(Image.blend(reg, Image.new('RGB', reg.size, col), 0.28), (PX(x0), PZ(z0)))
    d = ImageDraw.Draw(im)
    for (x0, z0, x1, z1), col, nm in zones:
        d.rectangle([PX(x0), PZ(z0), PX(x1), PZ(z1)], outline=col, width=3)
        d.text((PX(x0) + 8, PZ(z0) + 6), nm, fill=(255, 255, 255), font=font(18))
    for k, nm, ch, ty, door, pn in FAC:
        footprint(d, parts[pn], TYPE[ty][0], w=2)
        label(d, door[0], door[1] - 3, nm, 13)
    for (nm, x, z) in (('鯨通り', 0, -70), ('灰通り', 0, 70), ('風通り', -60, 0), ('潮通り', 62, 0)):
        label(d, x, z, nm, 17, fg=(255, 230, 150))
    pts = []
    for k, nm, ch, ty, door, pn in FAC:
        r = route(door)
        j = r[-2]
        if abs(j[0]) < 6 and abs(j[1]) < 6:
            continue
        if all(abs(j[0] - q[0]) + abs(j[1] - q[1]) > 16 for q in pts):
            pts.append(j)
    pts.append((0, 0))
    for (x, z) in pts:
        cx, cz = PX(x), PZ(z)
        d.rectangle([cx - 6, cz - 6, cx + 6, cz + 6], fill=(250, 250, 250), outline=(20, 20, 20), width=2)
        d.line([cx, cz, cx, cz - 24], fill=(250, 250, 250), width=3)
        d.rectangle([cx - 8, cz - 40, cx + 8, cz - 24], fill=(60, 80, 110), outline=(255, 255, 255), width=2)
    bx, bz = PX(PLAZA[0]), PZ(PLAZA[1])
    d.rectangle([bx - 30, bz - 14, bx + 30, bz + 14], fill=(235, 225, 190), outline=(20, 20, 20), width=3)
    d.text((bx - 24, bz - 12), '案内板', fill=(20, 20, 20), font=font(18))
    mw, mh, mx, mz = 190, 190, 6, im.height - 270
    d.rectangle([mx, mz, mx + mw, mz + mh], fill=(18, 22, 30), outline=(255, 255, 255), width=3)
    d.text((mx + 8, mz + 4), '地図 (M) の都の欄', fill=(255, 255, 255), font=font(15))
    s = mw / 270.0 * 1.0
    for k, nm, ch, ty, door, pn in FAC:
        qx, qz = mx + (door[0] + 135) * s, mz + 22 + (door[1] + 100) * s * 0.8
        d.ellipse([qx - 8, qz - 8, qx + 8, qz + 8], fill=TYPE[ty][0], outline=(0, 0, 0))
        d.text((qx - 6, qz - 8), ch, fill=(20, 20, 20), font=font(11))
    d.ellipse([mx + 100, mz + 100, mx + 108, mz + 108], fill=(255, 80, 80))
    d.text((mx + 110, mz + 98), '自分', fill=(255, 120, 120), font=font(12))
    legend(d, im.width, im.height, ('  白い柱 = 交差点の標識',))
    finish(im, 'B_plan.png')


def plan_C(vox, parts):
    im, d = base_plan(vox, 'C 案内人 + 色つきランタンの道しるべ + 屋根の上の旗柱', '門と広場に案内人。話すと施設を選べて、その施設まで色のランタンの列が地面に灯る。施設の屋根に旗柱 (遠くからも見える)')
    for i, (k, nm, ch, ty, door, pn) in enumerate(FAC):
        col = TYPE[ty][0]
        r = route(door)
        off = (i % 4 - 1.5) * 1.1
        pts = []
        for (x, z) in r:
            pts.append((PX(x + (off if abs(x) < 5 else 0)), PZ(z + (off if abs(z) < 5 else 0))))
        d.line(pts, fill=col, width=4)
        for a, b in zip(r, r[1:]):
            n = max(1, int(math.dist(a, b) // 4))
            for t in range(n + 1):
                x = a[0] + (b[0] - a[0]) * t / n
                z = a[1] + (b[1] - a[1]) * t / n
                d.ellipse([PX(x) - 4, PZ(z) - 4, PX(x) + 4, PZ(z) + 4], fill=(255, 235, 150), outline=col, width=2)
        footprint(d, parts[pn], col, w=2)
        p = parts[pn]
        cx, cz = PX((p[1] + p[3]) / 2), PZ((p[2] + p[4]) / 2)
        d.line([cx, cz, cx, cz - 22], fill=(240, 240, 240), width=3)
        d.polygon([(cx, cz - 22), (cx + 16, cz - 17), (cx, cz - 11)], fill=col, outline=(20, 20, 20))
        label(d, (p[1] + p[3]) / 2, (p[2] + p[4]) / 2, nm, 13, dy=2)
    for (nm, x, z) in GATES + [('広場', PLAZA[0] + 8, PLAZA[1] + 6)]:
        cx, cz = PX(x), PZ(z)
        d.ellipse([cx - 13, cz - 13, cx + 13, cz + 13], fill=(255, 255, 255), outline=(20, 20, 20), width=3)
        d.text((cx - 8, cz - 12), '案', fill=(20, 20, 20), font=font(18))
        d.text((cx + 16, cz - 8), '案内人 (広場)' if nm == '広場' else '案内人 ' + nm, fill=(255, 255, 255), font=font(13))
    legend(d, im.width, im.height, ('  点 = 色のランタン列、旗 = 旗柱',))
    finish(im, 'C_plan.png')


# ------------------------------------------------------------------ 通りの絵 (等角)
def adds(vox, parts, prop):
    """提案の物を voxel に足す (描くためだけ。capital.json には書かない)"""
    out = dict(vox)

    def put(x, y, z, c):
        out[(x, y, z)] = c

    def box(x0, y0, z0, x1, y1, z1, c):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    put(x, y, z, c)

    def roof_tint(pn, col, t=0.62):
        p = parts[pn]
        top = {}
        for (x, y, z), c in list(out.items()):
            if p[1] <= x <= p[3] and p[2] <= z <= p[4] and y >= 3 and c[0] > c[1] + 35 and c[0] > c[2] + 35:
                out[(x, y, z)] = mix(c[:3], col, 0.9) + (1,)

    def wsign(x, z, y, col, along='z', w=5, h=3):
        """壁から出た大看板 (腕木 + 板 + 絵の印)"""
        dark = mix(col, (20, 20, 20), 0.55)
        cream = (250, 245, 230)
        if along == 'z':
            box(x, y + h + 1, z - 1, x, y + h + 1, z + w, (70, 50, 35))
            for dz in range(w):
                for dy in range(h):
                    edge = dz in (0, w - 1) or dy in (0, h - 1)
                    put(x, y + 1 + dy, z + dz, (dark if edge else col) + (1,))
            box(x, y + 2, z + 1, x, y + h - 1, z + w - 2, cream + (1,))
            put(x, y + 2, z + w // 2, dark + (1,))
        else:
            box(x - 1, y + h + 1, z, x + w, y + h + 1, z, (70, 50, 35))
            for dx in range(w):
                for dy in range(h):
                    edge = dx in (0, w - 1) or dy in (0, h - 1)
                    put(x + dx, y + 1 + dy, z, (dark if edge else col) + (1,))
            box(x + 1, y + 2, z, x + w - 2, y + h - 1, z, cream + (1,))
            put(x + w // 2, y + 2, z, dark + (1,))

    def post(x, z, h, c=(80, 60, 45)):
        box(x, 0, z, x, h, z, c)

    def banner_pole(x, z, h, col):
        post(x, z, h, (230, 230, 230))
        for dy in range(4):
            for dx in range(1, 5 - dy // 2):
                put(x + dx, h - dy, z, col + (1,))
        put(x, h + 1, z, (255, 230, 120))

    T = {k: TYPE[k][0] for k in TYPE}
    if prop == 'A':
        roof_tint('tool_shop', T['shop'])
        roof_tint('storage', T['shop'])
        roof_tint('guild', T['work'])
        post(29, -33, 7)
        wsign(29, -36, 2, T['shop'], 'z', 6, 4)
        post(48, -23, 8)
        wsign(48, -26, 3, T['work'], 'z', 6, 4)
        post(33, -3, 7)
        wsign(34, -3, 2, T['shop'], 'x', 6, 4)
    elif prop == 'B':
        bx, bz = 2, 3
        post(bx, bz, 10)
        post(bx + 15, bz, 10)
        box(bx, 10, bz, bx + 15, 10, bz, (70, 50, 35))
        for row, ty in enumerate(['shop', 'rest', 'work', 'story']):
            for dz in range(2):
                y = 9 - row * 2 - dz
                for dx in range(1, 15):
                    put(bx + dx, y, bz, (236, 228, 196, 1))
                for dx in range(1, 5):
                    put(bx + dx, y, bz, T[ty] + (1,))
                for dx in range(7, 14, 2):
                    put(bx + dx, y, bz, (60, 55, 50, 1))
        for (x, z, col) in ((20, 4, T['shop']), (36, 4, T['shop']), (-6, 4, T['story'])):
            post(x, z, 6)
            for dx in range(-3, 4):
                for y in (6, 7):
                    put(x + dx, y, z, (60, 85, 120, 1))
            for y in (6, 7):
                put(x - 3, y, z, col + (1,))
                put(x - 2, y, z, col + (1,))
        wsign(31, -34, 3, T['shop'], 'z', 3, 2)
        wsign(50, -23, 3, T['work'], 'z', 3, 2)
    else:
        def trail(pts, col):
            for a, b in zip(pts, pts[1:]):
                n = int(max(abs(a[0] - b[0]), abs(a[1] - b[1])))
                for t in range(n + 1):
                    x = int(round(a[0] + (b[0] - a[0]) * t / max(1, n)))
                    z = int(round(a[1] + (b[1] - a[1]) * t / max(1, n)))
                    for y in range(-1, 1):
                        if (x, y, z) in out:
                            out[(x, y, z)] = col + (1,)
                    if t % 3 == 0:
                        lx, lz = x + (1 if a[0] == b[0] else 0), z + (1 if a[1] == b[1] else 0)
                        put(lx, 0, lz, mix((255, 220, 120), col, 0.45) + (1,))
                        put(lx, 1, lz, mix((255, 200, 80), col, 0.35) + (1,))
        trail([(8, -16), (28, -16), (28, -32), (31, -32)], T['shop'])
        trail([(8, -14), (8, -2), (36, -2), (36, -5)], T['shop'])
        trail([(8, -18), (28, -18), (28, -24), (50, -24)], T['work'])
        banner_pole(33, -28, 28, T['shop'])
        banner_pole(32, -7, 24, T['shop'])
        banner_pole(50, -10, 28, T['work'])
        px_, pz_ = 6, -12
        box(px_, 0, pz_, px_, 1, pz_, (240, 240, 240))
        box(px_ - 1, 2, pz_, px_ + 1, 2, pz_, (240, 240, 240))
        put(px_, 3, pz_, (240, 205, 160))
        put(px_, 4, pz_, (255, 230, 120))
    return out


def iso(vox, region, title, notes, scale=11):
    (rx0, rx1, rz0, rz1) = region
    sub = {k: c for k, c in vox.items() if rx0 <= k[0] <= rx1 and rz0 <= k[2] <= rz1 and k[1] >= -1}
    vis = [(x, y, z, c) for (x, y, z), c in sub.items() if (x, y + 1, z) not in sub or (x - 1, y, z) not in sub or (x, y, z + 1) not in sub]
    A, B, Hh = scale * 0.866, scale * 0.5, scale

    def proj(x, y, z):
        return ((x + z) * A, -(x - z) * B - y * Hh)

    pts = [proj(x, y, z) for (x, y, z, c) in vis]
    minu, maxu = min(p[0] for p in pts) - 10, max(p[0] for p in pts) + 30
    minv, maxv = min(p[1] for p in pts) - 90, max(p[1] for p in pts) + 40
    W, H = int(maxu - minu), int(maxv - minv)
    im = Image.new('RGB', (W, H), (150, 185, 215))
    d = ImageDraw.Draw(im)
    vis.sort(key=lambda t: (-(t[0] - t[2]), t[1]))

    def P(x, y, z):
        u, v = proj(x, y, z)
        return (u - minu, v - minv)
    for (x, y, z, c) in vis:
        br = len(c) == 4
        c = c[:3]
        if (x, y + 1, z) not in sub:
            d.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=tuple(min(255, int(v * 1.08)) for v in c))
        if (x - 1, y, z) not in sub:
            d.polygon([P(x, y, z), P(x, y, z + 1), P(x, y + 1, z + 1), P(x, y + 1, z)], fill=tuple(int(v * (1.0 if br else 0.8)) for v in c))
        if (x, y, z + 1) not in sub:
            d.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=tuple(int(v * (1.0 if br else 0.62)) for v in c))
    d.rectangle([0, 0, W, 54], fill=(28, 30, 34))
    d.text((14, 6), title, fill=(245, 240, 225), font=font(22))
    d.text((14, 32), notes, fill=(205, 200, 190), font=font(14))
    return im, d, P


def call(d, P, x, y, z, text, dx=0, dy=-50, col=(255, 255, 255)):
    u, v = P(x, y, z)
    f = font(15)
    tw = d.textlength(text, font=f)
    d.line([u, v, u + dx, v + dy], fill=col, width=2)
    d.rectangle([u + dx - tw / 2 - 4, v + dy - 22, u + dx + tw / 2 + 4, v + dy], fill=(20, 20, 24), outline=col)
    d.text((u + dx - tw / 2, v + dy - 20), text, fill=col, font=f)


def street(vox, parts, prop):
    v2 = adds(vox, parts, prop)
    region = (-8, 66, -42, 8)
    ttl = {'A': 'A 吊り大看板 + 種類色の屋根 (広場の東の通り)', 'B': 'B 案内板 + 通りの名の標識 (広場の南東)', 'C': 'C 道しるべのランタン列 + 旗柱 + 案内人 (広場の東)'}[prop]
    nt = {'A': '種類色 (金 = 店、赤 = 戦い) の屋根が遠くから見える。戸の前の大看板は 6x4 マス、字と絵つき',
          'B': '広場に一覧の案内板 (色の行 = 種類)。交差点の標識に通りの名と色',
          'C': '広場の案内人 (白い外套) と色のランタン列が施設の戸まで。屋根の上に旗柱'}[prop]
    im, d, P = iso(v2, region, ttl, nt)
    if prop == 'A':
        call(d, P, 29, 7, -33, '道具屋 (金)', -90, -70)
        call(d, P, 48, 8, -23, '組合 (赤)', -60, -80)
        call(d, P, 34, 7, -3, '預かり所 (金)', -20, -80)
    elif prop == 'B':
        call(d, P, 9, 10, 3, '広場の案内板', -90, -60)
        call(d, P, 20, 7, 4, '通りの名の標識', 60, -70)
        call(d, P, 36, 7, 4, '標識', 40, -60)
    else:
        call(d, P, 6, 4, -12, '案内人', -80, -70)
        call(d, P, 33, 28, -28, '旗柱 (金)', -70, -30)
        call(d, P, 50, 28, -10, '旗柱 (赤)', 70, -30)
        call(d, P, 28, 0, -24, '色のランタン列', 60, 70)
    finish(im, prop + '_street.png')


def main():
    vox, marks = load()
    parts = get_parts()
    for fn in (plan_A, plan_B, plan_C):
        fn(vox, parts)
        print('plan ok', fn.__name__)
    for p in 'ABC':
        street(vox, parts, p)
        print('street ok', p)


if __name__ == '__main__':
    main()
