"""灰被り 3 案で共有する形の道具と、中身 (殻の下の体) の作り方。"""
from __future__ import annotations

from core import *  # noqa
from kit import *  # noqa
from common import _mk  # noqa


def loft_skip(bone, mat, rings, skip=None, caps=(False, False), mats=None):
    """lpkit.loft と同じだが、skip(i, j) が真の四角を作らない (窓・切れ目)。"""
    rings = [np.array(r, float) for r in rings]
    cen = [r.mean(0) for r in rings]
    k = len(rings[0])
    faces = []
    for i in range(len(rings) - 1):
        ref = (cen[i] + cen[i + 1]) / 2
        for j in range(k):
            if skip and skip(i, j):
                continue
            quad = [rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]]
            f = _mk(quad, bone, (mats[i] if mats else mat), ref)
            if f is not None:
                faces.append(f)
    for idx, cap in zip((0, len(rings) - 1), caps):
        if cap:
            other = cen[1] if idx == 0 else cen[-2]
            f = _mk(list(rings[idx]), bone, cap if isinstance(cap, str) else mat, other)
            if f is not None:
                faces.append(f)
    return faces


def set_heat(faces, sides):
    """炎の舌の面に、根元 (1) から先 (0) への熱さを付ける (炎の絵の段になる)。"""
    nseg = max(1, int(math.ceil(len(faces) / sides)))
    for i, f in enumerate(faces):
        f.heat = 1.0 - (i // sides) / nseg
    return faces


def flame(bone, base, h, r, lean=(0, 0, 0), mat="flame", n=1):
    """炎の舌 (3 角の先細り)。"""
    return set_heat(spike(bone, mat, base, (lean[0], 1.0, lean[2]), h, r, sides=3, rings=3, bend=(lean[0] * h * 0.6, 0, lean[2] * h * 0.6)), 3)


def ribbon(bone, mat, pts, w, t=0.45, hint=(1, 0, 0), sides=4):
    """先へ行くほど細る平たい炎の舌 (幅 w、厚み t)。pts は点列 (先端は尖る)。"""
    n = len(pts)
    ra = [w * (1 - i / (n - 1)) ** 0.8 if i < n - 1 else 0.0 for i in range(n)]
    ra[0] = w
    rb = [t * (1 - i / (n - 1)) ** 0.8 if i < n - 1 else 0.0 for i in range(n)]
    rb[0] = t
    return set_heat(tube(bone, mat, pts, ra, rb, sides=sides, hint=hint, phase=0 if sides == 4 else 90, caps=(False, False)), sides)


# 骨の休めの位置 (作図の空間: 前 = -Z、右 (geo の _r) = +X)
SH = 25.5            # 肩の高さ


def limb(bone, mat, pts, ra, sides=5, hint=(0, 0, 1), phase=0.0, caps=(True, True)):
    return tube(bone, mat, pts, ra, None, sides=sides, hint=hint, phase=phase, caps=caps)


def inner_legs(F, x=2.5, hip=15.0, knee=8.2, ankle=1.8, w=1.7, mat="char", foot=True, sides=5, kz=-1.1):
    for s, nm in ((1, "r"), (-1, "l")):
        b = f"leg_{nm}"
        F += limb(b, mat, [(s * x, hip, -0.5), (s * (x + 0.1), knee, kz), (s * (x + 0.1), ankle, -0.4)],
                  [w, w * 0.88, w * 0.7], sides=sides, hint=(1, 0, 0), phase=0)
        if foot:
            F += fbox(b, mat, (s * (x + 0.1), 0.9, -1.6), (w * 2.0, 1.8, 5.2), taper=(0.9, 0.7), shift=(0, -0.5))
    return F
