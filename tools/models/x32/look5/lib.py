# -*- coding: utf-8 -*-
"""look5 の道具（手本: 鯨の鎚 v5、HANDOFF の「look5」）。

品ごとの設計ファイル（tools/models/x32/look5/<id>.py）が Model に箱・回した節・円弧を足し、build() を呼ぶ。
build() は作業フォルダに <id>.json（Blockbench の place_cube に渡す箱と UV）と <id>_base.png（下塗り）を書く。
Blockbench で create_texture(<id>_base.png) → 箱を一つずつ place_cube → 筆で描き足す → 書き出し → bb_import。

下塗りの規則（ユーザーが決めた見た目）:
  - 面の向きで明暗: 上 +1、表と裏 0、横 -1、下 -2。上と左の縁は明るく、下と右の縁は暗く、左上の角に光。
  - 長さ方向の色の流れ: 箱は世界の y で、節は根元 (a) から先 (b) へ。
  - rim: 大きな表の面の 1 画素内側に骨の縁取りの線。wrap: 2 ドットの骨の輪と影の帯を交互に巻く。
  - gem: 表と裏に菱形の切子。tip: 節の先の 1 列を一番明るく（泡・骨の先）。
  - 部品は 2 ドット以上（弦だけは 1）。光る箱は名前を _glow で終える（素材 glow なら自動）。
"""
import json
import math
from pathlib import Path

from PIL import Image

RAMPS = {
    'sea': ['#0e1430', '#16224a', '#1c3466', '#244a82', '#2c649e', '#3c82b8', '#5ca4cc', '#90cce0', '#c8eef6', '#f0fdff'],
    'bone': ['#4a3430', '#7a5c4a', '#a88a6a', '#ccb08a', '#e8d4ae', '#fff6e2'],
    'glow': ['#0e4a98', '#1a78d0', '#3cb0f0', '#86e0ff', '#c8f6ff', '#ffffff'],
    'string': ['#3a4a5a', '#6a7e90', '#9cb2c4', '#cfe2ee', '#f2fbff'],
    # 潮: 深い珊瑚の赤紫 → 珊瑚 → 桃 → 泡の白、差し色の貝（クリーム）、光る真珠（桃白）
    'coral': ['#2a0e22', '#4a1630', '#6e1e3a', '#962a44', '#bc3c50', '#de5a62', '#f08478', '#f8b0a0', '#fcd8cc', '#fff4ee'],
    'shell': ['#4a3a3a', '#7a6458', '#a89282', '#d0bea8', '#ece0cc', '#fffaf0'],
    'pearl': ['#8a4a70', '#c070a0', '#e8a0c8', '#f8d0e8', '#fff0f8', '#ffffff'],
}
OFF = {'up': 1, 'south': 0, 'north': 0, 'east': -1, 'west': -1, 'down': -2}


class Model:
    def __init__(self, name, accent='bone', glowmat='glow'):
        self.name = name
        self.parts = []
        self.accent = accent      # 巻き・縁取りの線の素材
        self.glowmat = glowmat    # 光る素材（名前を _glow にする）

    def box(self, name, mat, frm, to, base=3, grad=None, rim=False, wrap=None, gem=False, glow=None):
        """grad=(y0, t0, y1, t1): 世界の y で色の段を流す。wrap=(y0, y1): その高さに巻きを描く"""
        self.parts.append(dict(name=self._nm(name, mat, glow), mat=mat, frm=list(frm), to=list(to),
                               origin=[(frm[0] + to[0]) / 2, (frm[1] + to[1]) / 2, (frm[2] + to[2]) / 2], rot=0,
                               base=base, grad=grad, rim=rim, wrap=wrap, gem=gem, seg=None, tip=False, accent=self.accent))

    def decal(self, name, pixels, frm, to, plane='xy'):
        """絵の板（厚みなし）。pixels は上の行から並べた (mat, tone) か None（透明）の格子。
        plane='xy' は z = frm[2] の板（表 south にそのまま、裏 north は左右を返す）、
        plane='zy' は x = frm[0] の板（横の u は z。-x から見て z が右へ増える向き＝west にそのまま、east は左右を返す）。板の大きさと格子の数で絵の細かさが決まる"""
        o = [(frm[i] + to[i]) / 2 for i in range(3)]
        self.parts.append(dict(name=name, mat='decal', frm=list(frm), to=list(to), origin=o, rot=0, base=0, grad=None,
                               rim=False, wrap=None, gem=False, seg=None, tip=False, accent=self.accent, pix=pixels, plane=plane))

    def picture(self, name, art, pal, origin, px=0.5, plane='xy', at=0.0, flip_y=False):
        """文字で描いたドット絵（上の行から、'.' は透明）を板にする。pal = 文字 → (素材, 段)。
        origin = 絵の左下（xy なら (x, y)、zy なら (z, y)）、at = 板の z（xy）か x（zy）"""
        rows = [[pal[ch] if ch != '.' else None for ch in r] for r in art]
        if flip_y:
            rows = rows[::-1]
        W, H = max(len(r) for r in rows), len(rows)
        rows = [r + [None] * (W - len(r)) for r in rows]
        if plane == 'xy':
            frm, to = [origin[0], origin[1], at], [origin[0] + W * px, origin[1] + H * px, at]
        else:
            frm, to = [at, origin[1], origin[0]], [at, origin[1] + H * px, origin[0] + W * px]
        self.decal(name, rows, frm, to, plane)

    def seg(self, name, mat, a, b, w, d, t0=3, t1=None, over=0.5, zc=0.0, tip=False, glow=None):
        """a から b へ伸びる回した節（幅 w、奥行き ±d）。色の段は a で t0、b で t1"""
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) + 2 * over
        ang = math.degrees(math.atan2(dy, dx))
        cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        self.parts.append(dict(name=self._nm(name, mat, glow), mat=mat,
                               frm=[r2(cx - L / 2), r2(cy - w / 2), r2(zc - d)], to=[r2(cx + L / 2), r2(cy + w / 2), r2(zc + d)],
                               origin=[r2(cx), r2(cy), r2(zc)], rot=r2(ang), base=t0, grad=None, rim=False, wrap=None,
                               gem=False, seg=(t0, t0 if t1 is None else t1), tip=tip, accent=self.accent))

    def chain(self, prefix, mat, pts, widths, depths, t0, t1, tip=True, zc=0.0, over=0.5):
        """点の列を節でつなぐ。幅・奥行きは各節（列か一つの数）"""
        n = len(pts) - 1
        for k in range(n):
            w = widths[k] if isinstance(widths, (list, tuple)) else widths
            d = depths[k] if isinstance(depths, (list, tuple)) else depths
            self.seg(f'{prefix}_{k}', mat, pts[k], pts[k + 1], w, d, t0 + (t1 - t0) * k / n, t0 + (t1 - t0) * (k + 1) / n,
                     over=over, zc=zc, tip=tip and k == n - 1)

    def arc(self, prefix, mat, center, r, a0, a1, n, widths, depths, t0, t1, tip=False, zc=0.0):
        """中心 center・半径 r の円弧（角度は度、a0 から a1 へ）を n 節で"""
        pts = [(center[0] + r * math.cos(math.radians(a0 + (a1 - a0) * k / n)),
                center[1] + r * math.sin(math.radians(a0 + (a1 - a0) * k / n))) for k in range(n + 1)]
        self.chain(prefix, mat, pts, widths, depths, t0, t1, tip=tip, zc=zc)

    def _nm(self, name, mat, glow):
        g = (mat in ('glow', self.glowmat)) if glow is None else glow
        return name + '_glow' if g and not name.endswith('_glow') else name

    def build(self, out_dir, width=128):
        out_dir = Path(out_dir)
        rects = []
        for e in self.parts:
            if e.get('pix'):
                ph, pw = len(e['pix']), len(e['pix'][0])
                fs = ('south', 'north') if e.get('plane', 'xy') == 'xy' else ('east', 'west')
                rects += [(ph, pw, e['name'], fs[0]), (ph, pw, e['name'], fs[1])]
                continue
            dx, dy, dz = (max(1, math.ceil(e['to'][i] - e['frm'][i] - 1e-6)) for i in range(3))
            for face, (w, h) in {'south': (dx, dy), 'north': (dx, dy), 'east': (dz, dy), 'west': (dz, dy),
                                 'up': (dx, dz), 'down': (dx, dz)}.items():
                rects.append((h, w, e['name'], face))
        rects.sort(key=lambda r: (-r[0], -r[1]))
        x = y = rowh = 0
        uv = {}
        for h, w, n, face in rects:
            if x + w > width:
                x, y, rowh = 0, y + rowh, 0
            uv.setdefault(n, {})[face] = [x, y, x + w, y + h]
            x += w
            rowh = max(rowh, h)
        H = 16
        while H < y + rowh:
            H *= 2
        img = Image.new('RGBA', (width, H), (0, 0, 0, 0))
        for e in self.parts:
            for face, r in uv[e['name']].items():
                paint_face(img, e, face, r)
        img.save(out_dir / f'{self.name}_base.png')
        cubes = [{'name': e['name'], 'from': e['frm'], 'to': e['to'], 'origin': e['origin'], 'rotation': [0, 0, e['rot']],
                  'faces': [{'face': f, 'uv': v} for f, v in uv[e['name']].items()]} for e in self.parts]
        (out_dir / f'{self.name}.json').write_text(json.dumps(cubes), encoding='utf-8')
        print(self.name, 'texture', width, H, 'cubes', len(cubes))
        return cubes


def r2(v):
    return round(v, 2)


def put(img, x, y, mat, t):
    ramp = RAMPS[mat]
    t = max(0, min(len(ramp) - 1, int(round(t))))
    h = ramp[t]
    img.putpixel((x, y), tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,))


def paint_face(img, e, face, rect):
    u0, v0, u1, v1 = rect
    w, h = u1 - u0, v1 - v0
    if e.get('pix'):
        for j, row in enumerate(e['pix']):
            for i, px in enumerate(row):
                if px:
                    put(img, u0 + (i if face in ('south', 'west') else w - 1 - i), v0 + j, px[0], px[1])
        return
    side = face in ('south', 'north', 'east', 'west')
    for i in range(w):
        for j in range(h):
            mat = e['mat']
            if e['seg']:
                t0, t1 = e['seg']
                if face in ('south', 'north', 'up', 'down'):
                    tt = i / max(1, w - 1)
                    if face == 'north':
                        tt = 1 - tt
                else:
                    tt = 1.0 if face == 'east' else 0.0
                b = t0 + (t1 - t0) * tt
            elif e['grad']:
                y0, ta, y1, tb = e['grad']
                wy = (e['to'][1] - j - 0.5) if side else (e['to'][1] if face == 'up' else e['frm'][1])
                f = max(0.0, min(1.0, (wy - y0) / (y1 - y0)))
                b = ta + (tb - ta) * f
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
            if e['wrap'] and side:
                wy = e['to'][1] - j - 1
                y0, y1 = e['wrap']
                if y0 <= wy < y1:
                    k = int(y1 - 1 - wy) % 5
                    lit = face in ('south', 'north')
                    if k in (0, 1):
                        mat, t = e.get('accent', 'bone'), ((4 if k == 0 else 3) if lit else (3 if k == 0 else 2))
                        if i == w - 1:
                            t -= 1
                    elif k == 2:
                        mat, t = e.get('accent', 'bone'), 0
                    else:
                        t = b + OFF[face] + (0.6 if i == 0 else 0)
            if e['rim'] and face in ('south', 'north', 'east', 'west') and w >= 5 and h >= 5:
                if (i in (1, w - 2) and 1 <= j <= h - 2) or (j in (1, h - 2) and 1 <= i <= w - 2):
                    mat, t = e.get('accent', 'bone'), (4 if (j == 1 or i == 1) else 2.6)
            if e['gem'] and face in ('south', 'north'):
                cx, cy = (w - 1) / 2, (h - 1) / 2
                d = abs(i - cx) / (w / 2) + abs(j - cy) / (h / 2)
                t = 5 if d < 0.35 else (4 if d < 0.7 else (2.5 if d < 1.0 else 1))
                if i < cx and j < cy and 0.35 <= d < 0.7:
                    t = 5
            if e['tip'] and face in ('south', 'north', 'up', 'down'):
                at_tip = (i == w - 1) if face != 'north' else (i == 0)
                if at_tip:
                    t = len(RAMPS[mat]) - 1
            put(img, u0 + i, v0 + j, mat, t)


def variant_bbmodel(base_path, out_path, name, model_parts, drop_prefix):
    """書き出した基の .bbmodel から drop_prefix で始まる箱を外し、model_parts（Model.build の箱）を足した派生（弓の引きの段）"""
    import copy
    import uuid
    bb = json.loads(Path(base_path).read_text(encoding='utf-8'))
    bb['name'] = name
    keep = [e for e in bb['elements'] if not e['name'].startswith(drop_prefix)]
    for c in model_parts:
        faces = {f['face']: {'uv': f['uv'], 'texture': 0} for f in c['faces']}
        keep.append({'name': c['name'], 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True,
                     'from': c['from'], 'to': c['to'], 'autouv': 0, 'color': 0, 'origin': c['origin'], 'rotation': c['rotation'],
                     'faces': faces, 'type': 'cube', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/' + c['name']))})
    bb['elements'] = keep
    bb['outliner'] = [e['uuid'] for e in keep]
    Path(out_path).write_text(json.dumps(bb, ensure_ascii=False), encoding='utf-8')


def write_bbmodel(cubes, png_path, out_path, name, only=None):
    """箱の一覧と下塗りの絵から .bbmodel を書く（Blockbench で組む前の形の下見用。only で箱を絞る）"""
    import base64
    import uuid
    im = Image.open(png_path)
    src = 'data:image/png;base64,' + base64.b64encode(Path(png_path).read_bytes()).decode()
    els = []
    for c in cubes:
        if only and not only(c['name']):
            continue
        els.append({'name': c['name'], 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True,
                    'from': c['from'], 'to': c['to'], 'autouv': 0, 'color': 0, 'origin': c['origin'], 'rotation': c['rotation'],
                    'faces': {f['face']: {'uv': f['uv'], 'texture': 0} for f in c['faces']}, 'type': 'cube',
                    'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/' + c['name']))})
    bb = {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False}, 'name': name,
          'resolution': {'width': im.width, 'height': im.height}, 'elements': els, 'groups': [],
          'outliner': [e['uuid'] for e in els],
          'textures': [{'name': name, 'path': '', 'id': '0', 'width': im.width, 'height': im.height, 'uv_width': im.width,
                        'uv_height': im.height, 'render_mode': 'default', 'render_sides': 'auto', 'internal': True,
                        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/tex')), 'source': src}]}
    Path(out_path).write_text(json.dumps(bb, ensure_ascii=False), encoding='utf-8')


# ---------------------------------------------------------------- 弓の弦の上の飾り（引きの段ごとに同じ場所へ付ける）
PULLS = (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6))


def string_at(top, bot, pull, t):
    """弦の上の点。t=0 が下の弦受け、t=1 が上の弦受け。pull は引きの量（弦の中ほど y=15 が -x へ下がる）"""
    if pull == 0:
        return (top[0], bot[1] + (top[1] - bot[1]) * t)
    mid = (top[0] - pull, 15)
    tm = (15 - bot[1]) / (top[1] - bot[1])
    if t <= tm:
        s = t / tm
        return (bot[0] + (mid[0] - bot[0]) * s, bot[1] + (mid[1] - bot[1]) * s)
    s = (t - tm) / (1 - tm)
    return (mid[0] + (top[0] - mid[0]) * s, mid[1] + (top[1] - mid[1]) * s)


def string_t(top, bot, y):
    """弦の上で高さ y にあたる t（まっすぐの弦で）"""
    return (y - bot[1]) / (top[1] - bot[1])


# ---------------------------------------------------------------- 絵の板の下地（塗りの規則だけ。形は各シリーズで描く）
class Canvas:
    """世界の座標で塗る格子（px ごとの一マス）。grid[r][c] は (素材, 段) か None。r=0 が下の行"""

    def __init__(self, x0, y0, x1, y1, px=0.5):
        self.x0, self.y0, self.px = x0, y0, px
        self.W, self.H = int(round((x1 - x0) / px)), int(round((y1 - y0) / px))
        self.grid = [[None] * self.W for _ in range(self.H)]

    def cells(self):
        for r in range(self.H):
            for c in range(self.W):
                yield c, r, self.x0 + (c + 0.5) * self.px, self.y0 + (r + 0.5) * self.px

    def paint(self, fn):
        """fn(x, y, 今の色) → 新しい色（None で透明、今の色を返せばそのまま）"""
        for c, r, x, y in self.cells():
            self.grid[r][c] = fn(x, y, self.grid[r][c])

    def finish(self):
        """ドット絵の仕上げ（一度だけ）: 孤立した点を消し、塗りの上の縁を明るく下の縁を暗くし、
        外側に一マスの濃い縁取りを付ける。細い線がちらつく点に見えず、模型の箱と同じ塊に見えるように"""
        if getattr(self, '_done', False):
            return
        self._done = True
        g, W, H = self.grid, self.W, self.H
        at = lambda c, r: g[r][c] if 0 <= c < W and 0 <= r < H else None
        n8 = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]
        g2 = [row[:] for row in g]
        for r in range(H):
            for c in range(W):
                if g[r][c] and sum(1 for dc, dr in n8 if at(c + dc, r + dr)) <= 1:
                    g2[r][c] = None
        g = self.grid = g2
        g2 = [row[:] for row in g]
        for r in range(H):
            for c in range(W):
                v = g[r][c]
                if not v:
                    continue
                if not at(c, r + 1):
                    g2[r][c] = (v[0], v[1] + 1)
                elif not at(c, r - 1):
                    g2[r][c] = (v[0], max(0, v[1] - 1))
        for r in range(H):
            for c in range(W):
                if g[r][c]:
                    continue
                nb = [at(c + dc, r + dr) for dc, dr in ((0, 1), (0, -1), (1, 0), (-1, 0))]
                nb = [v for v in nb if v]
                if nb:
                    v = min(nb, key=lambda q: q[1])
                    g2[r][c] = (v[0], max(0, v[1] - 3))
        self.grid = g2

    def put(self, m, name, at=0.0, plane='xy', mirror15=False):
        """板にする。mirror15 で y=15 の鏡（下の弓腕）。plane='zy' のときは x 座標の代わりに z として置く"""
        self.finish()
        rows = [list(r) for r in self.grid[::-1]]
        y0, y1 = self.y0, self.y0 + self.H * self.px
        if mirror15:
            rows = rows[::-1]
            y0, y1 = 30 - y1, 30 - y0
        rows = [r for r in rows]
        u0, u1 = self.x0, self.x0 + self.W * self.px
        if plane == 'xy':
            m.decal(name, rows, [u0, y0, at], [u1, y1, at], 'xy')
        else:
            m.decal(name, rows, [at, y0, u0], [at, y1, u1], 'zy')


def inside(p, poly):
    """点が多角形の内側か"""
    x, y = p
    n, ins = len(poly), False
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            ins = not ins
    return ins


def near_line(p, pts):
    """点から折れ線までの距離と、折れ線の上の位置 t（0..1）"""
    L = [math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]) for i in range(len(pts) - 1)]
    tot, acc, best = sum(L), 0, (1e9, 0)
    for i, l in enumerate(L):
        ux, uy = (pts[i + 1][0] - pts[i][0]) / l, (pts[i + 1][1] - pts[i][1]) / l
        s = max(0, min(l, (p[0] - pts[i][0]) * ux + (p[1] - pts[i][1]) * uy))
        d = math.hypot(p[0] - pts[i][0] - ux * s, p[1] - pts[i][1] - uy * s)
        if d < best[0]:
            best = (d, (acc + s) / tot)
        acc += l
    return best


def line_normals(pts):
    """折れ線の各点の法線（進む向きの右手側。上へ伸びる弓腕なら +x 側）"""
    out = []
    for i in range(len(pts)):
        a, b = pts[max(0, i - 1)], pts[min(len(pts) - 1, i + 1)]
        ux, uy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(ux, uy)
        out.append((uy / L, -ux / L))
    return out


def offset_line(pts, d):
    """折れ線を法線の向きへ d だけずらす（負なら弦の側）"""
    return [(p[0] + n[0] * d, p[1] + n[1] * d) for p, n in zip(pts, line_normals(pts))]
