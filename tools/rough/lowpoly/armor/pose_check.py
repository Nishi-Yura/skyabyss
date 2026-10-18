#!/usr/bin/env python3
"""Pose clipping check for the low-poly armor (option C) meshes (docs/design/16_armor_design.md section 6).

Poses the bones like the vanilla player (arm raise/side/back, crouch, sit) and measures how far the surface of one bone's
mesh runs into the surface of another bone's mesh, compared with the rest pose.  Surface samples -> 0.5 unit voxels; the
report is the count of voxels shared by two different bones beyond the rest pose (extra), and the triangles involved.

  python tools/rough/lowpoly/armor/pose_check.py [set] [tier]      (tier: '' | _rare | _legend; default all)
"""
import json
import math
import os
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))))
MESH = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'lowpoly', 'armor')
SETS = ['stone_whale', 'tide_shell', 'thunder_wing', 'ember']
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']
VOX = 0.5
PIV = {
    'armorHead': (0, 24, 0), 'armorBody': (0, 24, 0), 'armorRightArm': (-5, 22, 0), 'armorLeftArm': (5, 22, 0),
    'armorRightLeg': (-1.9, 12, 0), 'armorLeftLeg': (1.9, 12, 0), 'armorRightBoot': (-1.9, 12, 0), 'armorLeftBoot': (1.9, 12, 0),
}


def rot(rx, ry=0.0, rz=0.0):
    """rotation matrix, angles in degrees, x then z then y (geo space, y up, front = -z)"""
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    mx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    my = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    mz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return my @ mz @ mx


# pose: bone -> (rx, ry, rz, dx, dy, dz).  Vanilla values; arm pitch: forward = positive here
def side(bone):
    return 1 if 'Left' in bone else -1


def poses():
    P = {'rest': {}}
    for name, a in (('arms_fwd_90', 90), ('arms_up_150', 150), ('arms_back_45', -45)):
        P[name] = {b: (a, 0, 0, 0, 0, 0) for b in ('armorRightArm', 'armorLeftArm')}
    for name, z in (('arms_side_45', 45), ('arms_side_90', 90)):
        P[name] = {b: (0, 0, -side(b) * z * -1, 0, 0, 0) for b in ('armorRightArm', 'armorLeftArm')}
    # walk: arms swing opposite to legs, 35 degrees
    P['walk_a'] = {'armorRightArm': (-35, 0, 0, 0, 0, 0), 'armorLeftArm': (35, 0, 0, 0, 0, 0),
                   'armorRightLeg': (35, 0, 0, 0, 0, 0), 'armorLeftLeg': (-35, 0, 0, 0, 0, 0),
                   'armorRightBoot': (35, 0, 0, 0, 0, 0), 'armorLeftBoot': (-35, 0, 0, 0, 0, 0)}
    P['walk_b'] = {'armorRightArm': (35, 0, 0, 0, 0, 0), 'armorLeftArm': (-35, 0, 0, 0, 0, 0),
                   'armorRightLeg': (-35, 0, 0, 0, 0, 0), 'armorLeftLeg': (35, 0, 0, 0, 0, 0),
                   'armorRightBoot': (-35, 0, 0, 0, 0, 0), 'armorLeftBoot': (35, 0, 0, 0, 0, 0)}
    # crouch (vanilla: body xRot 0.5 about the neck, body y +3.2, head y +4.2, arms xRot +0.4 and y +3.2, legs z +4)
    cr = {'armorBody': (-28.6, 0, 0, 0, -3.2, 0), 'armorHead': (0, 0, 0, 0, -4.2, 0),
          'armorRightArm': (-22.9, 0, 0, 0, -3.2, 0), 'armorLeftArm': (-22.9, 0, 0, 0, -3.2, 0),
          'armorRightLeg': (0, 0, 0, 0, 0.2, 4), 'armorLeftLeg': (0, 0, 0, 0, 0.2, 4),
          'armorRightBoot': (0, 0, 0, 0, 0.2, 4), 'armorLeftBoot': (0, 0, 0, 0, 0.2, 4)}
    P['crouch'] = cr
    c2 = dict(cr)
    c2['armorRightArm'] = (60, 0, 0, 0, -3.2, 0)
    c2['armorLeftArm'] = (60, 0, 0, 0, -3.2, 0)
    P['crouch_arms_fwd'] = c2
    # sit (riding): legs forward 90, arms forward 36
    P['sit'] = {'armorRightLeg': (90, 0, 0, 0, 0, 0), 'armorLeftLeg': (90, 0, 0, 0, 0, 0),
                'armorRightBoot': (90, 0, 0, 0, 0, 0), 'armorLeftBoot': (90, 0, 0, 0, 0, 0),
                'armorRightArm': (36, 0, 0, 0, 0, 0), 'armorLeftArm': (36, 0, 0, 0, 0, 0)}
    # head turn / look (yaw 70, pitch up 40, pitch down 45)
    P['head_yaw70'] = {'armorHead': (0, 70, 0, 0, 0, 0)}
    P['head_up40'] = {'armorHead': (-40, 0, 0, 0, 0, 0)}
    P['head_down45'] = {'armorHead': (45, 0, 0, 0, 0, 0)}
    # attack swing: right arm forward 100 with body twist 20
    P['swing'] = {'armorRightArm': (100, 20, 0, 0, 0, 0), 'armorBody': (0, -20, 0, 0, 0, 0), 'armorLeftArm': (-20, 0, 0, 0, 0, 0)}
    # elytra / gliding: whole body pitched 90 forward (arms back, legs up) -- only head, legs and boots can be worn with elytra
    P['glide'] = {'armorBody': (90, 0, 0, 0, 0, 0)}
    return P


def load(set_, tier):
    """bone -> (triangles array (n,3,3) in geo coordinates)"""
    bones = {}
    for p in PIECES:
        d = json.load(open(os.path.join(MESH, set_, f'{p}{tier}.json'), encoding='utf-8'))
        for n, b in d['bones'].items():
            v = np.array(b['vertices'], dtype=float)
            t = np.array(b['triangles'], dtype=int)
            bones[n] = v[t]
    return bones


def sample(tris, step=0.25):
    pts = []
    for a, b, c in tris:
        ab, ac = b - a, c - a
        area = np.linalg.norm(np.cross(ab, ac)) / 2
        n = max(1, int(math.ceil(area / (step * step))))
        n = min(n, 4000)
        u = np.random.RandomState(1).rand(n, 2)
        flip = u.sum(1) > 1
        u[flip] = 1 - u[flip]
        pts.append(a + u[:, :1] * ab + u[:, 1:] * ac)
        pts.append(np.array([a, b, c, (a + b + c) / 3]))
    return np.vstack(pts)


def apply(pts, bone, pose):
    rx, ry, rz, dx, dy, dz = pose.get(bone, (0, 0, 0, 0, 0, 0))
    piv = np.array(PIV[bone], dtype=float)
    return (pts - piv) @ rot(rx, ry, rz).T + piv + np.array([dx, dy, dz])


def vox(pts):
    return set(map(tuple, np.floor(pts / VOX).astype(int)))


def pair_overlap(posed, ignore_pairs):
    names = list(posed)
    sets = {n: vox(posed[n]) for n in names}
    res = {}
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            if (a, b) in ignore_pairs or (b, a) in ignore_pairs:
                continue
            res[(a, b)] = len(sets[a] & sets[b])
    return res


def main():
    sets = [sys.argv[1]] if len(sys.argv) > 1 and sys.argv[1] in SETS else SETS
    tiers = [sys.argv[2]] if len(sys.argv) > 2 else ['', '_rare', '_legend']
    P = poses()
    # the same-slot joints (leg/boot, which touch by design) are skipped
    ignore = {('armorRightLeg', 'armorRightBoot'), ('armorLeftLeg', 'armorLeftBoot')}
    for s in sets:
        for t in tiers:
            bones = load(s, t)
            samples = {n: sample(tr) for n, tr in bones.items()}
            base = pair_overlap(samples, ignore)
            print(f'== {s}{t or " (common)"}  rest overlaps: ' + ', '.join(f'{a[5:]}-{b[5:]}:{v}' for (a, b), v in base.items() if v))
            for name, pose in P.items():
                if name == 'rest':
                    continue
                posed = {n: apply(samples[n], n, pose) for n in samples}
                cur = pair_overlap(posed, ignore)
                worst = sorted(((cur[k] - base.get(k, 0), k) for k in cur), reverse=True)[:3]
                msg = ', '.join(f'{k[0][5:]}-{k[1][5:]}:+{v}' for v, k in worst if v > 3)
                print(f'   {name:16s} {msg or "-"}')


if __name__ == '__main__':
    main()
