#!/usr/bin/env python3
"""Install the low-poly armor sets (option C "framework") into the mod assets.

docs/design/16_armor_design.md, 15 section 4.6.  Input: docs/review/lowpoly_roughs/armor/c/<set>/<piece>[_rare|_legend][_plus5|_plus10]_*.
Writes (deterministic, safe to rerun):
  assets/skyabyss/lowpoly/armor/<set>/<piece>[_rare|_legend].json           mesh (3 tiers per piece)
  assets/skyabyss/textures/lowpoly/armor/<set>/<piece>[tier][_plusN].png    atlas
  assets/skyabyss/textures/lowpoly/armor/<set>/<piece>[tier][_plusN]_glowmask.png   glow pixels (GeckoLib AutoGlowingTexture)
  assets/skyabyss/geo/armor/lowpoly_armor.geo.json                           8 bones only (names/pivots as the cube geo)
The tier-less (common) mesh has no +5/+10 atlas in the roughs; the renderer falls back to the base atlas.
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_close  # noqa: E402  (書き出す前に水密にする)

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'docs', 'review', 'lowpoly_roughs', 'armor', 'c')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss')
SETS = ['stone_whale', 'tide_shell', 'thunder_wing', 'ember']
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']
TIERS = ['', '_rare', '_legend']
LEVELS = ['', '_plus5', '_plus10']
BONES = [
    ('armorHead', [0, 24, 0]), ('armorBody', [0, 24, 0]),
    ('armorRightArm', [-5, 22, 0]), ('armorLeftArm', [5, 22, 0]),
    ('armorRightLeg', [-1.9, 12, 0]), ('armorLeftLeg', [1.9, 12, 0]),
    ('armorRightBoot', [-1.9, 12, 0]), ('armorLeftBoot', [1.9, 12, 0]),
]


def main():
    mesh_dir = os.path.join(ASSETS, 'lowpoly', 'armor')
    tex_dir = os.path.join(ASSETS, 'textures', 'lowpoly', 'armor')
    for d in (mesh_dir, tex_dir):
        if os.path.isdir(d):
            shutil.rmtree(d)
    n_mesh = n_tex = 0
    tris = {}
    for s in SETS:
        os.makedirs(os.path.join(mesh_dir, s))
        os.makedirs(os.path.join(tex_dir, s))
        for p in PIECES:
            for t in TIERS:
                src = os.path.join(SRC, s, f'{p}{t}_mesh.json')
                d = json.load(open(src, encoding='utf-8'))
                d.pop('triangle_count', None)
                d.pop('glowmask', None)
                d['texture'] = f'armor/{s}/{p}{t}'
                d['socket'] = False
                for b in d['bones'].values():
                    b.pop('triangle_count', None)
                tris[(s, p, t)] = sum(len(b['triangles']) for b in d['bones'].values())
                d, _ = mesh_close.close_mesh(d)
                with open(os.path.join(mesh_dir, s, f'{p}{t}.json'), 'w', encoding='utf-8') as f:
                    json.dump(d, f, separators=(',', ':'))
                n_mesh += 1
                for lv in LEVELS:
                    a = os.path.join(SRC, s, f'{p}{t}{lv}_atlas.png')
                    g = os.path.join(SRC, s, f'{p}{t}{lv}_glowmask.png')
                    if not os.path.exists(a):
                        continue
                    img = Image.open(a).convert('RGBA')
                    mask = np.array(Image.open(g).convert('RGBA'))[..., 3] > 0
                    if not mask.any():
                        sys.exit(f'{a}: no glow pixels (GeckoLib crashes on an empty glowmask)')
                    # glowmask: atlas colour on the glowing pixels only (GeckoLib AutoGlowingTexture takes the colour from here)
                    glow = np.array(img)
                    glow[~mask] = 0
                    name = f'{p}{t}{lv}'
                    img.save(os.path.join(tex_dir, s, name + '.png'), optimize=True)
                    Image.fromarray(glow, 'RGBA').save(os.path.join(tex_dir, s, name + '_glowmask.png'), optimize=True)
                    n_tex += 1
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {'identifier': 'geometry.lowpoly_armor', 'texture_width': 64, 'texture_height': 64,
                            'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
            'bones': [{'name': n, 'pivot': pv} for n, pv in BONES],
        }],
    }
    with open(os.path.join(ASSETS, 'geo', 'armor', 'lowpoly_armor.geo.json'), 'w', encoding='utf-8') as f:
        json.dump(geo, f, indent=2)
    print(f'{n_mesh} meshes, {n_tex} atlas+glowmask pairs')
    for s in SETS:
        row = []
        for t in TIERS:
            row.append(sum(tris[(s, p, t)] for p in PIECES))
        print(s, 'tris (common/rare/legend, 4 pieces):', row)


if __name__ == '__main__':
    main()
