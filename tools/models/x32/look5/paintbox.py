# -*- coding: utf-8 -*-
"""太い箱の各面にドット絵を描く道具（神器、2026-11-19）。

立体は太い箱で組み、その面に 1 単位 = 1 ドットの絵（縁の明暗・縁取り・模様・刻み）を描く。
羽や光の筋のような薄いものは pix3d.sprite_planes の平面の板で重ねる。
  skin(m, name, mat, t, frm, to, paint=None): 箱を置き、6 面を描く。
    まず全面に下地（面の向きの明暗 + 上と左の縁を明るく、下と右の縁を暗く + 外周 1 ドットの濃い縁取り）を塗り、
    paint(sp, face, w, h) が渡されたら、その面の Sprite（左下が (0, 0)、右へ u・上へ v）に上描きする。
"""
import math

from pix3d import Sprite

OFFF = {'up': 1.0, 'south': 0.0, 'north': 0.0, 'east': -1.0, 'west': -1.0, 'down': -2.0}


def face_dims(frm, to):
    dx, dy, dz = (max(1, int(round(to[i] - frm[i]))) for i in range(3))
    return {'south': (dx, dy), 'north': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}


def base_face(sp, w, h, mat, t, rim=True):
    """下地: 面の明暗の上に、上と左の縁を明るく・下と右の縁を暗く、外周に濃い縁取り"""
    for i in range(w):
        for j in range(h):
            tt = t
            if j == h - 1:
                tt += 1.6
            elif i == 0:
                tt += 0.8
            if j == 0:
                tt -= 1.8
            elif i == w - 1:
                tt -= 1.0
            if rim and w >= 4 and h >= 4 and (i in (0, w - 1) or j in (0, h - 1)) and not (j == h - 1):
                tt -= 0.6
            sp.cell[(i, j)] = dict(mat=mat, t=tt, sid=0, depth=1.0, float=False, shade=False, bevel=None, zc=0.0)


def skin(m, name, mat, t, frm, to, paint=None, faces=None, glow=None, rot=0.0, pivot=None):
    """箱を置いて面を描く。faces で描く面を絞る（他は描かない＝見えない面）"""
    dims = face_dims(frm, to)
    fp = {}
    for face, (w, h) in dims.items():
        if faces and face not in faces:
            continue
        sp = Sprite(0, 0, w, h)
        base_face(sp, w, h, mat, t + OFFF[face])
        if paint:
            paint(sp, face, w, h)
        rows = [[(sp.cell[(i, j)]['mat'], sp.cell[(i, j)]['t']) if (i, j) in sp.cell else (mat, t) for i in range(w)]
                for j in range(h - 1, -1, -1)]
        fp[face] = rows
    m.box(name, mat, list(frm), list(to), t, facepix=fp, glow=glow)
    if rot:
        part = m.parts[-1]
        part['rot'] = rot
        if pivot is not None:
            part['origin'] = [pivot[0], pivot[1], part['origin'][2]]
    return m


def flip_u(face):
    """面の絵の左右が模型の +x と逆になる面（north・east）"""
    return face in ('north', 'east')
