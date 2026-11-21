# -*- coding: utf-8 -*-
"""防具の look5 の道具。lib.Model に「骨」と「左右の写し」を足す。

座標は Blockbench (x32)。プレイヤーは -Z を向き、右腕は +X 側。人の箱:
  頭 x±8 y48..64 z±8 / 胴 x±8 y24..48 z±4 / 右腕 x8..16 y24..48 / 左腕 x-16..-8 / 右脚 x0..8 y0..24 / 左脚 x-8..0
骨: H 頭 / B 胴 / RA 右腕 / LA 左腕 / RL 右脚 / LL 左脚 / RB 右靴 / LB 左靴（靴は y 0..9.2、脚は 9.2..24）
限度（16 の 2.9 を 2 倍）: 横 ±26、背の飾り ±21、頭上 y 75、前 z -13、後ろ z +24。顔（|x|<8, y48..59, z-8 の前）に何も置かない。

  a = Armor('stone_whale')
  a.on('B'); a.box(...)
  a.pair('RA', lambda s: a.box(..., frm=(s*9, ...)))   # s = +1 で右 (+X)、-1 で左。名前に R_/L_ が付く
  a.write(work)  → <name>.json（箱 + 骨）、<name>_base.png、<name>.bbmodel（群つき。下見用）
"""
import base64
import json
import uuid
from pathlib import Path

from PIL import Image

import lib
from lib import Model, r2

BONES = {'H': 'armorHead', 'B': 'armorBody', 'RA': 'armorRightArm', 'LA': 'armorLeftArm',
         'RL': 'armorRightLeg', 'LL': 'armorLeftLeg', 'RB': 'armorRightBoot', 'LB': 'armorLeftBoot'}
ORIG = {'H': (0, 48, 0), 'B': (0, 48, 0), 'RA': (10, 44, 0), 'LA': (-10, 44, 0),
        'RL': (3.8, 24, 0), 'LL': (-3.8, 24, 0), 'RB': (3.8, 24, 0), 'LB': (-3.8, 24, 0)}
MIRROR = {'RA': 'LA', 'LA': 'RA', 'RL': 'LL', 'LL': 'RL', 'RB': 'LB', 'LB': 'RB', 'H': 'H', 'B': 'B'}


class Armor(Model):
    def __init__(self, name, accent='bone', glowmat='glow'):
        super().__init__(name, accent, glowmat)
        self.cur = 'B'
        self.prefix = ''

    def on(self, bone):
        self.cur = bone
        return self

    def _tag(self, n0):
        for e in self.parts[n0:]:
            e['bone'] = e.get('bone', self.cur)
            if self.prefix and not e['name'].startswith(self.prefix):
                e['name'] = self.prefix + e['name']

    def box(self, name, mat, frm, to, rot=None, origin=None, edge=None, **kw):
        """rot=(rx, ry, rz) で傾けた板（origin は回す中心。省くと箱の中心）。
        edge: 面の外周 1 画素に明るい縁取り（参照の防具の板の縁）。True なら accent、素材名ならその素材"""
        n0 = len(self.parts)
        frm, to = [min(frm[i], to[i]) for i in range(3)], [max(frm[i], to[i]) for i in range(3)]
        super().box(name, mat, [r2(v) for v in frm], [r2(v) for v in to], **kw)
        if rot is not None:
            self.parts[-1]['rot'] = [r2(v) for v in rot]
        if origin is not None:
            self.parts[-1]['origin'] = [r2(v) for v in origin]
        if edge:
            self.parts[-1]['edge'] = self.accent if edge is True else edge
        self._tag(n0)

    def seg(self, *a, **kw):
        n0 = len(self.parts)
        super().seg(*a, **kw)
        self._tag(n0)

    def decal(self, *a, **kw):
        n0 = len(self.parts)
        super().decal(*a, **kw)
        self._tag(n0)

    def pair(self, bone, fn):
        """右 (s=+1, 骨 bone) と左 (s=-1, 写しの骨) に同じ部品を置く。fn(s) の中で box などを呼ぶ"""
        for s, b, p in ((1, bone, 'R_'), (-1, MIRROR[bone], 'L_')):
            keep_cur, keep_pre = self.cur, self.prefix
            self.cur, self.prefix = b, p
            fn(s)
            self.cur, self.prefix = keep_cur, keep_pre

    def write(self, out_dir):
        out_dir = Path(out_dir)
        cubes = self.build(out_dir)
        bone_of = {e['name']: e['bone'] for e in self.parts}
        edge_of = {e['name']: e['edge'] for e in self.parts if e.get('edge')}
        if edge_of:
            img = Image.open(out_dir / f'{self.name}_base.png').convert('RGBA')
            for c in cubes:
                m = edge_of.get(c['name'])
                if not m:
                    continue
                for f in c['faces']:
                    if f['face'] == 'down':
                        continue
                    u0, v0, u1, v1 = f['uv']
                    w, h = u1 - u0, v1 - v0
                    if w < 3 or h < 3:
                        continue
                    n = len(lib.RAMPS[m]) - 1
                    for i in range(w):
                        for j in range(h):
                            if 0 < i < w - 1 and 0 < j < h - 1:
                                continue
                            t = n - 1.6 if (j == h - 1 or i == w - 1) else n - 0.6
                            if i == 0 and j == 0:
                                t = n
                            lib.put(img, u0 + i, v0 + j, m, t)
            img.save(out_dir / f'{self.name}_base.png')
        for c in cubes:
            c['bone'] = bone_of[c['name']]
        (out_dir / f'{self.name}.json').write_text(json.dumps(cubes), encoding='utf-8')
        write_armor_bbmodel(cubes, out_dir / f'{self.name}_base.png', out_dir / f'{self.name}.bbmodel', self.name)
        return cubes


def write_armor_bbmodel(cubes, png_path, out_path, name):
    im = Image.open(png_path)
    src = 'data:image/png;base64,' + base64.b64encode(Path(png_path).read_bytes()).decode()
    els, groups, outl = [], [], []
    by_bone = {}
    for c in cubes:
        u = str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/' + c['name']))
        els.append({'name': c['name'], 'box_uv': False, 'render_order': 'default', 'locked': False, 'export': True,
                    'from': c['from'], 'to': c['to'], 'autouv': 0, 'color': 0, 'origin': c['origin'],
                    'rotation': c['rotation'], 'faces': {f['face']: {'uv': f['uv'], 'texture': 0} for f in c['faces']},
                    'type': 'cube', 'uuid': u})
        by_bone.setdefault(c['bone'], []).append(u)
    for b, kids in by_bone.items():
        gu = str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/g/' + b))
        groups.append({'name': BONES[b], 'origin': list(ORIG[b]), 'rotation': [0, 0, 0], 'uuid': gu, 'export': True,
                       'visibility': True, 'autouv': 0})
        outl.append({'uuid': gu, 'name': BONES[b], 'children': kids, 'origin': list(ORIG[b])})
    bb = {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False}, 'name': name,
          'resolution': {'width': im.width, 'height': im.height}, 'elements': els, 'groups': groups, 'outliner': outl,
          'textures': [{'name': name, 'path': '', 'id': '0', 'width': im.width, 'height': im.height, 'uv_width': im.width,
                        'uv_height': im.height, 'render_mode': 'default', 'render_sides': 'auto', 'internal': True,
                        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name + '/tex')), 'source': src}]}
    Path(out_path).write_text(json.dumps(bb, ensure_ascii=False), encoding='utf-8')
