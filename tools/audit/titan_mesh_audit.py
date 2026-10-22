"""Audit the titan low-poly meshes: open edges (holes) per bone and tiny-alpha pixels in the atlases.  python tools/audit/titan_mesh_audit.py [--v]"""
import json
import os
import sys
from collections import Counter

import numpy as np
from PIL import Image

R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'skyabyss')
for name in ['stone_whale', 'tide_shell', 'thunder_wing', 'ember_titan']:
    for suf in ['', '_mid', '_far']:
        n = name + suf
        m = json.load(open(os.path.join(R, 'lowpoly', n + '.json')))
        tot_tri = 0
        edges = Counter()
        for bn, b in m['bones'].items():
            v = b['vertices']

            def key(i):
                return tuple(round(c, 2) for c in v[i])
            for t in b['triangles']:
                tot_tri += 1
                for a, c in ((0, 1), (1, 2), (2, 0)):
                    p, q = key(t[a]), key(t[c])
                    edges[(bn,) + tuple(sorted((p, q)))] += 1
        per = Counter()
        for k, c in edges.items():
            if c == 1:
                per[k[0]] += 1
        img = np.array(Image.open(os.path.join(R, 'textures', 'lowpoly', n + '.png')).convert('RGBA'))
        a = img[..., 3]
        print(f"{n}: tris={tot_tri} bones={len(m['bones'])} open-edge bones={len(per)} alpha<255 px={(a < 255).sum()} alpha==0 px={(a == 0).sum()} size={img.shape[1]}x{img.shape[0]}")
        if '--v' in sys.argv:
            for bn, c in per.most_common(12):
                print('   ', bn, c)
