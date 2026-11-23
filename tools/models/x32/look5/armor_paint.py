# -*- coding: utf-8 -*-
"""防具の下塗り（2026-11-21、参照の測った違いから）。Blockbench で手で組んで書き出した .bbmodel の箱に、
面ごとの UV を詰め直し、参照（Reforged Armors 等）の塗り方で下塗りした絵を貼った .bbmodel を書く。

参照で測ったこと:
  - 画素の細かさは 1 画素 = 1/16 ブロック（バニラと同じ）。模型の単位は x32 のままなので 1 画素 = 2 単位。
  - 面は 3〜4 段の色のモザイク（隣り合う 2 画素ほどのまだらの塊）。暗い縁取りで面を囲まない。
  - 明暗: 面の向き（上が明るく、下が暗い）、面の中で上から下へ暗く、左上の縁を 1 段明るく、下の縁を 1 段暗く。

箱の名前の頭で素材を決める: b_ 骨 / d_ 影 / g_ 光（光る箱は名前を _glow で終える） / それ以外は岩の青。
名前に _t が入る箱は、面の外周 1 画素に縁の色（骨の明るい色。参照の金・白の縁の線）を回す（下の縁は 1 段暗く）。
筆での描き足し（ひび・年輪・鋲）は、この下塗りのあと Blockbench で行う。

  python -I tools/models/x32/look5/armor_paint.py <入力.bbmodel> <出力.bbmodel> [<組>]
組の色は armor_palettes.py（主 = 名前の頭なし / b_ 縁 / c_ 布・革 / k_ 二つ目の布 / d_ 影 / g_ 光）。
"""
import base64
import io
import json
import math
import re
import sys
import uuid
from pathlib import Path

from PIL import Image

RAMPS = {
    'sea': ['#0f1a3a', '#17295a', '#1f3b7a', '#284f96', '#3266ae', '#4382c4', '#62a2d6', '#93c6e6'],
    'bone': ['#4a3424', '#6e5034', '#94704a', '#b8945e', '#d4b67a', '#e8d29c', '#f6e8c4', '#fffaea'],
    'dark': ['#05080f', '#0a1020', '#0f1a32', '#15244a', '#1c3060', '#243c74', '#2c4a88', '#36589c'],
    'glow': ['#3cb0f0', '#5cc8f8', '#86e0ff', '#a8ecff', '#c8f6ff', '#e4fcff', '#ffffff', '#ffffff'],
    'cloth': ['#1e120a', '#2e1c10', '#45291a', '#5e3a24', '#7a5232', '#a07048', '#c4926a', '#e0b890'],
    'cloth2': ['#1e120a', '#2e1c10', '#45291a', '#5e3a24', '#7a5232', '#a07048', '#c4926a', '#e0b890'],
}
DIR = {'up': 5.4, 'north': 3.8, 'south': 3.4, 'east': 2.9, 'west': 2.9, 'down': 1.0}
TEX = 2.0  # 単位 / 画素


def mat_of(name):
    for p, m in (('b_', 'bone'), ('d_', 'dark'), ('g_', 'glow'), ('c_', 'cloth'), ('k_', 'cloth2')):
        if name.startswith(p):
            return m
    return 'glow' if name.endswith('_glow') else 'sea'


def ramp3(c0, c1, c2):
    """暗い色・中の色・明るい色から 8 段 (0..7) を作る (中の色が 4 段目)"""
    a, b, c = (tuple(int(x[q:q + 2], 16) for q in (1, 3, 5)) for x in (c0, c1, c2))
    out = []
    for k in range(8):
        if k <= 4:
            t = k / 4; u, v = a, b
        else:
            t = (k - 4) / 3; u, v = b, c
        out.append('#%02x%02x%02x' % tuple(round(u[q] + (v[q] - u[q]) * t) for q in range(3)))
    return out


def use_palette(setname):
    """組の色 (armor_palettes.py の PAL) を RAMPS に入れる。無い組は岩鯨の色のまま"""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from armor_palettes import PAL
    pal = PAL.get(setname)
    if not pal:
        return False
    for k, v in pal.items():
        RAMPS[k] = list(v) if len(v) == 8 else ramp3(*v)
    return True


def h(x, y, s):
    v = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    v = (v ^ (v >> 13)) * 1274126177 & 0xffffffff
    return (v ^ (v >> 16)) / 0xffffffff


def face_dims(e, face, tex=TEX):
    d = [e['to'][i] - e['from'][i] for i in range(3)]
    w, hh = {'north': (d[0], d[1]), 'south': (d[0], d[1]), 'east': (d[2], d[1]), 'west': (d[2], d[1]),
             'up': (d[0], d[2]), 'down': (d[0], d[2])}[face]
    return max(1, round(abs(w) / tex)), max(1, round(abs(hh) / tex))


def paint_stone(img, x0, y0, w, hh, mat, face, seed, rim=True):
    """塔の重の石（名前に st_ が入る箱だけ）: まだらを減らし、面の中を 3 段の陰影、上の縁を明るい線、
    角の欠け（暗い画素）、細いひびの線（暗い 1 画素＋右に明るい 1 画素）で描く。"""
    ramp = RAMPS[mat]
    n = len(ramp) - 1
    base = DIR[face]
    side = face not in ('up', 'down')
    px = {}
    for j in range(hh):
        for i in range(w):
            t = base
            if side and hh >= 3:   # 3 段の陰影（上・中・下）
                t += (0.8, 0.0, -0.8)[min(2, j * 3 // hh)]
            if h(i // 3, j // 3, seed) < 0.06:   # まだらは 3x3 の塊でまれに
                t -= 0.5
            if rim:
                if j == 0:
                    t += 1.6
                elif i == 0 and w > 2:
                    t += 0.5
                if j == hh - 1 and hh > 2:
                    t -= 1.2
                elif i == w - 1 and w > 2:
                    t -= 0.5
            px[(i, j)] = max(0, min(n, int(round(t))))
    if w >= 3 and hh >= 3:   # 角の欠け: 1〜2 か所、L 字の 2〜3 画素を暗く
        for c in range(1 + (h(1, 7, seed) > 0.5)):
            r = h(c, 3, seed)
            ci, cj = (0 if r < 0.5 else w - 1), (0 if (r * 4) % 1 < 0.5 else hh - 1)
            di, dj = (1 if ci == 0 else -1), (1 if cj == 0 else -1)
            for (a, b) in ((0, 0), (di, 0), (0, dj)):
                px[(ci + a, cj + b)] = 2
    if side and w >= 5 and hh >= 5 and h(5, 5, seed) < 0.75:   # 細いひび: 上の縁から下へ折れ曲がる 1 画素の線
        i = 1 + int(h(2, 9, seed) * (w - 2))
        for j in range(0, max(3, int(hh * (0.45 + 0.35 * h(4, 4, seed))))):
            if 0 <= i < w and j < hh:
                px[(i, j)] = 2
                if i + 1 < w and px.get((i + 1, j), 0) > 1:
                    px[(i + 1, j)] = min(n, px[(i + 1, j)] + 1)
            s = h(i, j, seed + 11)
            i += -1 if s < 0.3 else (1 if s > 0.7 else 0)
    for (i, j), k in px.items():
        c = ramp[k]
        img.putpixel((x0 + i, y0 + j), tuple(int(c[q:q + 2], 16) for q in (1, 3, 5)) + (255,))


def paint(img, x0, y0, w, hh, mat, face, seed, trim=False, wave=False, panel=False, rim=True, stone=False):
    if stone and mat in ('sea', 'cloth2'):
        return paint_stone(img, x0, y0, w, hh, mat, face, seed, rim=rim)
    ramp = RAMPS[mat]
    n = len(ramp) - 1
    base = DIR[face] if mat in ('sea', 'bone', 'dark', 'cloth', 'cloth2') else 5.0
    if mat == 'dark':
        base -= 1.5
    for j in range(hh):
        for i in range(w):
            t = base
            if face not in ('up', 'down') and hh > 1:
                t += 0.7 - 1.6 * j / (hh - 1)
            # まだら: 2x2 の塊で控えめに揺らす（参照は塊が大きく、ばらつきが小さい）
            b = h(i // 2, j // 2, seed)
            t += -0.6 if b < 0.2 else (0.5 if b > 0.85 else 0)
            if rim:   # 縁の明暗（塔の重の石の継ぎ足し箱 st_…xN は縁を描かず、一つの岩に見せる）
                if j == 0:
                    t += 1.0
                if i == 0 and w > 2:
                    t += 0.5
                if j == hh - 1 and hh > 2:
                    t -= 0.9
                if i == w - 1 and w > 2:
                    t -= 0.4
            if mat == 'glow':
                t = n - (1 if (i + j) % 3 else 0)
            # 彫りの波（岩鯨の波紋）: 縁取りのない大きな面に、暗い溝 1 画素＋上に明るい 1 画素
            if wave and mat == 'sea' and face not in ('up', 'down') and w >= 6 and hh >= 5:
                wy = int(round(hh * 0.5 + math.sin(i * 0.95 + seed) * 1.0))
                if j == wy:
                    t = 0
                elif j == wy - 1:
                    t = base + 2.2
            k = max(0, min(n, int(round(t))))
            c = ramp[k]
            # 板の中の枠（参照の「板の中にもう一つ枠」）: 外周から 1 画素内側に、上・左は明るく、下・右は暗い 1 画素の線
            if panel and face not in ('down',) and w >= 6 and hh >= 5:
                if (i == 1 or j == 1) and 1 <= i <= w - 2 and 1 <= j <= hh - 2:
                    c = ramp[min(n, 6)]
                elif (i == w - 2 or j == hh - 2) and 1 <= i <= w - 2 and 1 <= j <= hh - 2:
                    c = ramp[1]
            if trim and face != 'down' and min(w, hh) >= 4 and (i == 0 or j == 0 or i == w - 1 or j == hh - 1):
                tr = RAMPS['bone']
                c = tr[6] if j == 0 else (tr[3] if j == hh - 1 else (tr[5] if i == 0 else tr[4]))
            img.putpixel((x0 + i, y0 + j), tuple(int(c[q:q + 2], 16) for q in (1, 3, 5)) + (255,))


def main(src, dst, setname=None):
    use_palette(setname or Path(src).stem.replace('_painted', '').replace('raw_', ''))
    bb = json.loads(Path(src).read_text(encoding='utf-8'))
    rects = []
    for ei, e in enumerate(bb['elements']):
        if e.get('type', 'cube') != 'cube':
            continue
        ptex = 0.5 if re.search(r'(?:^|_)pic_[a-z]', e['name']) else TEX   # 2D の絵の板は画素が細かい (armor_pics.PIC_TEX)
        for face in e['faces']:
            w, hh = face_dims(e, face, ptex)
            rects.append((hh, w, ei, face))
    rects.sort(key=lambda r: (-r[0], -r[1]))
    W = 64
    while True:
        x = y = rowh = 0
        pos = {}
        for hh, w, ei, face in rects:
            if x + w > W:
                x, y, rowh = 0, y + rowh, 0
            pos[(ei, face)] = (x, y, w, hh)
            x += w
            rowh = max(rowh, hh)
        H = y + rowh
        if H <= W:
            break
        W *= 2
    H = W
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for ei, e in enumerate(bb['elements']):
        if e.get('type', 'cube') != 'cube':
            continue
        mat = mat_of(e['name'])
        pic = re.search(r'(?:^|_)pic_([a-z]+)', e['name'])
        for k, (face, fd) in enumerate(e['faces'].items()):
            x0, y0, w, hh = pos[(ei, face)]
            if pic:   # 2D の絵の板: north/south に絵、ほかは透明（armor_pics.py）
                import armor_pics
                for (i, j), c in armor_pics.draw(pic.group(1), w, hh, face, RAMPS).items():
                    img.putpixel((x0 + i, y0 + j), tuple(int(c[q:q + 2], 16) for q in (1, 3, 5)) + (255,))
                fd['uv'] = [x0 * TEX, y0 * TEX, (x0 + w) * TEX, (y0 + hh) * TEX]   # UV は絵の画素 x TEX（面の大きさとは別）
                fd['texture'] = 0
                fd.pop('rotation', None)
                continue
            paint(img, x0, y0, w, hh, mat, face, ei * 13 + k, trim='t' in e['name'].split('_'), wave=False, panel='p' in e['name'].split('_'),
                  rim=not re.search(r'(^|_)st_\w*x\d+$', e['name']), stone=bool(re.search(r'(^|_)st_', e['name'])))
            fd['uv'] = [x0 * TEX, y0 * TEX, (x0 + w) * TEX, (y0 + hh) * TEX]
            fd['texture'] = 0
            fd.pop('rotation', None)
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    src64 = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    bb['textures'] = [{'name': Path(dst).stem, 'path': '', 'id': '0', 'width': W, 'height': H,
                       'uv_width': W * TEX, 'uv_height': H * TEX, 'render_mode': 'default', 'render_sides': 'auto',
                       'internal': True, 'uuid': str(uuid.uuid4()), 'source': src64}]
    bb.setdefault('resolution', {})
    bb['resolution'] = {'width': int(W * TEX), 'height': int(H * TEX)}
    Path(dst).write_text(json.dumps(bb, ensure_ascii=False), encoding='utf-8')
    img.save(Path(dst).with_suffix('.png'))
    print(dst, W, H, len(rects), 'faces')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
