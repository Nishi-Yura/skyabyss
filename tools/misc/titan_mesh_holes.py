"""See-through audit of the titan meshes (rest pose): shoots rays from outside at the mesh; a ray whose first hit is a back face (or that
enters a triangle whose atlas pixel is transparent) means you can see into the body.  python tools/misc/titan_mesh_holes.py [name ...] [--n 20000]"""
import json
import os
import sys

import numpy as np
from PIL import Image

R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'skyabyss')


def load(n):
    m = json.load(open(os.path.join(R, 'lowpoly', n + '.json')))
    ts = m.get('texture_size', [256, 256])
    img = np.array(Image.open(os.path.join(R, 'textures', 'lowpoly', n + '.png')).convert('RGBA'))
    tris, bones, alpha0 = [], [], 0
    for bn, b in m['bones'].items():
        v = np.array(b['vertices'], float)
        if not b['triangles']:
            continue
        uv = np.array(b['uvs'], float)
        px = (uv.max() > 1.0001)
        for t in b['triangles']:
            tris.append(v[t])
            bones.append(bn)
            c = uv[t].mean(axis=0)
            if not px:
                c = c * np.array(ts)
            x = int(np.clip(c[0], 0, img.shape[1] - 1))
            y = int(np.clip(c[1], 0, img.shape[0] - 1))
            if img[y, x, 3] < 128:
                alpha0 += 1
    return np.array(tris), bones, alpha0


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    N = 6000
    if '--n' in sys.argv:
        N = int(sys.argv[sys.argv.index('--n') + 1])
        args = [a for a in args if a != str(N)]
    names = args or ['stone_whale', 'tide_shell', 'thunder_wing', 'ember_titan']
    rng = np.random.default_rng(1)
    for name in names:
        for suf in ['', '_mid', '_far']:
            tris, bones, alpha0 = load(name + suf)
            lo, hi = tris.reshape(-1, 3).min(0), tris.reshape(-1, 3).max(0)
            c = (lo + hi) / 2
            rad = np.linalg.norm(hi - lo) / 2 * 1.2
            # rays from points on a big sphere aimed at random points inside the bbox
            d = rng.normal(size=(N, 3))
            d /= np.linalg.norm(d, axis=1, keepdims=True)
            org = c + d * rad
            tgt = lo + rng.random((N, 3)) * (hi - lo)
            dirs = tgt - org
            dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
            a, b, cc = tris[:, 0], tris[:, 1], tris[:, 2]
            e1, e2 = b - a, cc - a
            nrm = np.cross(e1, e2)
            bad = 0
            hit = 0
            badbones = {}
            for i0 in range(0, N, 200):
                o = org[i0:i0 + 200]
                dd = dirs[i0:i0 + 200]
                t_best = np.full(len(o), np.inf)
                idx = np.full(len(o), -1)
                for j in range(0, len(tris), 2000):
                    A, E1, E2 = a[j:j + 2000], e1[j:j + 2000], e2[j:j + 2000]
                    h = np.cross(dd[:, None, :], E2[None])
                    det = np.einsum('rtk,tk->rt', h, E1)
                    ok = np.abs(det) > 1e-9
                    inv = np.where(ok, 1.0 / np.where(ok, det, 1), 0)
                    s = o[:, None, :] - A[None]
                    u = np.einsum('rtk,rtk->rt', s, h) * inv
                    q = np.cross(s, E1[None])
                    v = np.einsum('rk,rtk->rt', dd, q) * inv
                    t = np.einsum('tk,rtk->rt', E2, q) * inv
                    good = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 1e-4)
                    t = np.where(good, t, np.inf)
                    k = t.argmin(1)
                    tm = t[np.arange(len(o)), k]
                    better = tm < t_best
                    t_best = np.where(better, tm, t_best)
                    idx = np.where(better, k + j, idx)
                for r in range(len(o)):
                    if idx[r] >= 0:
                        hit += 1
                        # front face: normal opposes the ray (the engine's winding: counter-clockwise outward in geo space)
                        if np.dot(nrm[idx[r]], dd[r]) > 0:
                            bad += 1
                            badbones[bones[idx[r]]] = badbones.get(bones[idx[r]], 0) + 1
            top = sorted(badbones.items(), key=lambda kv: -kv[1])[:5]
            print(f"{name + suf}: hit {hit}/{N}, first-hit-is-back-face {bad} ({100.0 * bad / max(hit, 1):.2f}%), tris sampling transparent pixels {alpha0}/{len(tris)}  {top}")


main()
