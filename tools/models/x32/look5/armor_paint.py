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

  python -I tools/models/x32/look5/armor_paint.py <入力.bbmodel> <出力.bbmodel>
"""
import base64
import io
import json
import math
import sys
import uuid
from pathlib import Path

from PIL import Image

RAMPS = {
    'sea': ['#0f1a3a', '#17295a', '#1f3b7a', '#284f96', '#3266ae', '#4382c4', '#62a2d6', '#93c6e6'],
    'bone': ['#4a3424', '#6e5034', '#94704a', '#b8945e', '#d4b67a', '#e8d29c', '#f6e8c4', '#fffaea'],
    'dark': ['#05080f', '#0a1020', '#0f1a32', '#15244a', '#1c3060', '#243c74', '#2c4a88', '#36589c'],
    'glow': ['#3cb0f0', '#5cc8f8', '#86e0ff', '#a8ecff', '#c8f6ff', '#e4fcff', '#ffffff', '#ffffff'],
}
DIR = {'up': 5.4, 'north': 3.8, 'south': 3.4, 'east': 2.9, 'west': 2.9, 'down': 1.0}
TEX = 2.0  # 単位 / 画素


def mat_of(name):
    for p, m in (('b_', 'bone'), ('d_', 'dark'), ('g_', 'glow')):
        if name.startswith(p):
            return m
    return 'glow' if name.endswith('_glow') else 'sea'


def h(x, y, s):
    v = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    v = (v ^ (v >> 13)) * 1274126177 & 0xffffffff
    return (v ^ (v >> 16)) / 0xffffffff


def face_dims(e, face):
    d = [e['to'][i] - e['from'][i] for i in range(3)]
    w, hh = {'north': (d[0], d[1]), 'south': (d[0], d[1]), 'east': (d[2], d[1]), 'west': (d[2], d[1]),
             'up': (d[0], d[2]), 'down': (d[0], d[2])}[face]
    return max(1, round(abs(w) / TEX)), max(1, round(abs(hh) / TEX))


def paint(img, x0, y0, w, hh, mat, face, seed, trim=False, wave=False, panel=False):
    ramp = RAMPS[mat]
    n = len(ramp) - 1
    base = DIR[face] if mat in ('sea', 'bone', 'dark') else 5.0
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


def main(src, dst):
    bb = json.loads(Path(src).read_text(encoding='utf-8'))
    rects = []
    for ei, e in enumerate(bb['elements']):
        if e.get('type', 'cube') != 'cube':
            continue
        for face in e['faces']:
            w, hh = face_dims(e, face)
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
        for k, (face, fd) in enumerate(e['faces'].items()):
            x0, y0, w, hh = pos[(ei, face)]
            paint(img, x0, y0, w, hh, mat, face, ei * 13 + k, trim='_t' in e['name'], wave=False, panel='_p' in e['name'])
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
    main(sys.argv[1], sys.argv[2])
