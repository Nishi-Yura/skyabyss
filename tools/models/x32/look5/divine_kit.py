# -*- coding: utf-8 -*-
"""神器（四回目、2026-11-20）の共通の道具: 太い箱の面にドット絵（paintbox.skin）、回した板・弧・鐘・宝石・平面の板。
天穹の剣で決めた作り（立体の芯 + 面の絵 + 平面の板、細かい四角を並べず形として意味のある少数の部品）を残り 9 本で使う"""
import math

from paintbox import skin
from pix3d import Sprite, sprite_planes

S, G, D, K = 'stone', 'gold', 'dawn', 'glass'


def fm(fn):
    """south・north だけに描く"""
    return lambda sp, f, w, h: fn(sp, f, w, h) if f in ('south', 'north') else None


def frame_face(t=2.2, mat=G):
    return fm(lambda sp, f, w, h: sp.rect(1, 1, w - 1, h - 1, mat, t) if w > 2 and h > 2 else None)


def edge_top(mat=G, t=5.5):
    """回した板の上の縁（v の高い行）に一本の線"""
    return fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, mat, t))


def bar(m, name, mat, t, a, b, w, d, paint=None, glow=None, zc=0.0):
    """a から b へ伸びる、幅 w・奥行き ±d の回した板"""
    L = math.dist(a, b)
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
    skin(m, name, mat, t, (cx - L / 2, cy - w / 2, zc - d), (cx + L / 2, cy + w / 2, zc + d), paint, rot=ang, pivot=(cx, cy), glow=glow)


def path(m, name, mat, t, pts, ws, d, paint=None, glow=None, ext=0.5, dt=0.0):
    """折れ線を回した板でつなぐ（各節の幅 ws、つなぎ目は ext だけ重ねる）"""
    for k, (a, b) in enumerate(zip(pts, pts[1:])):
        L = math.dist(a, b)
        ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        w = ws[k] if isinstance(ws, (list, tuple)) else ws
        dd = d[k] if isinstance(d, (list, tuple)) else d
        bar(m, f'{name}_{k}', mat, t + dt * k, (a[0] - ux * ext, a[1] - uy * ext), (b[0] + ux * ext, b[1] + uy * ext), w, dd, paint, glow)


def arc(m, name, mat, t, c, r, a0, a1, n, w0, w1, d0, d1, paint=None, rx=None, ry=None, rot=0.0):
    """円弧（rx・ry で楕円、rot で傾ける）を n 本の回した板でたどる"""
    rx = r if rx is None else rx
    ry = r if ry is None else ry
    cr, sr = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    pts = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        x, y = rx * math.cos(a), ry * math.sin(a)
        pts.append((c[0] + x * cr - y * sr, c[1] + x * sr + y * cr))
    path(m, name, mat, t, pts, [w0 + (w1 - w0) * k / max(1, n - 1) for k in range(n)],
         [d0 + (d1 - d0) * k / max(1, n - 1) for k in range(n)], paint, ext=0.6)
    return pts


def gem_face(sp, f, w, h):
    sp.diamond((w / 2, h / 2), min(w, h) / 2, D, 3)
    sp.diamond((w / 2, h / 2), min(w, h) / 4, D, 5)


def gem(m, name, c, s, d, seat=True):
    """菱（45 度）の光る宝石と、一回り大きい金の台"""
    if seat:
        skin(m, f'{name}_seat', G, 2.6, (c[0] - s * 0.75, c[1] - s * 0.75, -d * 0.8), (c[0] + s * 0.75, c[1] + s * 0.75, d * 0.8),
             frame_face(1.6), rot=45, pivot=c)
    skin(m, f'{name}', D, 2, (c[0] - s / 2, c[1] - s / 2, -d), (c[0] + s / 2, c[1] + s / 2, d), gem_face, rot=45, pivot=c, glow=True)


def strapped_grip(m, y0, y1):
    """白い芯に金の帯が斜めに巻く柄と、上の金の襟"""
    skin(m, 'grip', S, 3, (-1.6, y0, -1.6), (1.6, y1, 1.6))
    y = y0 + 2
    k = 0
    while y < y1 - 1:
        bar(m, f'grip_strap_{k}', G, 3.5, (-2.1, y - 1.2), (2.1, y + 1.2), 1.1, 1.85)
        y += 4
        k += 1
    skin(m, 'grip_collar', G, 4, (-2.6, y1 - 0.4, -2.6), (2.6, y1 + 1, 2.6))


def pommel(m, y):
    """菱の金の柄頭に光る宝石"""
    skin(m, 'pommel', G, 3.5, (-2.2, y - 2.2, -2), (2.2, y + 2.2, 2), frame_face(2.3), rot=45, pivot=(0, y))
    skin(m, 'pommel_gem', D, 3, (-1.1, y - 1.1, -2.6), (1.1, y + 1.1, 2.6), rot=45, pivot=(0, y), glow=True)


def rod(m, y0, y1, bands=()):
    skin(m, 'rod', S, 3.5, (-1.3, y0, -1.3), (1.3, y1, 1.3))
    for k, y in enumerate(bands):
        skin(m, f'rod_band_{k}', G, 3.8, (-1.8, y, -1.8), (1.8, y + 1.2, 1.8))


def bell3d(m, name, c, s, mat=G):
    """口を下にした鐘（つまみ・胴・口の縁）"""
    x, y = c
    skin(m, f'{name}_top', mat, 4.5, (x - s * 0.35, y + s * 0.55, -s * 0.35), (x + s * 0.35, y + s * 0.95, s * 0.35))
    skin(m, f'{name}_body', mat, 3.5, (x - s * 0.7, y - s * 0.45, -s * 0.7), (x + s * 0.7, y + s * 0.6, s * 0.7),
         fm(lambda sp, f, w, h: sp.rect(0, 1, w, 2, mat, 5)))
    skin(m, f'{name}_lip', mat, 4, (x - s, y - s * 0.9, -s), (x + s, y - s * 0.4, s))


def sparkles(m, name, pts, z=0.0, t=2.5):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    sp = Sprite(min(xs) - 4, min(ys) - 4, max(xs) + 4, max(ys) + 4)
    for x, y in pts:
        for dx, dy in ((-2, 0), (-1, 0), (1, 0), (2, 0), (0, -2), (0, -1), (0, 1), (0, 2)):
            sp.dot(x + dx + 0.5, y + dy + 0.5, D, t)
        sp.dot(x + 0.5, y + 0.5, D, 5)
    sprite_planes(sp, m, name, z)


def feather_plane(m, name, feathers, z, t0=5.5, rim=4.5, glowline=True, bounds=None):
    """羽（(根元, 先, 根元の幅, 先の幅) の並び）を一枚の絵の板に。先は丸く、上の縁は金、根元の下に光"""
    xs = [p[0] for f in feathers for p in f[:2]]
    ys = [p[1] for f in feathers for p in f[:2]]
    b = bounds or (min(xs) - 6, min(ys) - 6, max(xs) + 6, max(ys) + 6)
    sp = Sprite(*b)
    for a, tip, w0, w1 in feathers:
        sp.line(a, tip, w0, S, t0, w1=w1, tg=t0 + 3, edge=(G, rim))
        sp.disc(tip, w1 * 0.55, S, t0 + 3)
        if glowline:
            mid = (a[0] + (tip[0] - a[0]) * 0.4, a[1] + (tip[1] - a[1]) * 0.4)
            dx, dy = tip[0] - a[0], tip[1] - a[1]
            L = math.hypot(dx, dy) or 1
            nx, ny = dy / L, -dx / L
            off = w0 * 0.32
            sp.line((a[0] + nx * off, a[1] + ny * off), (mid[0] + nx * off * 0.7, mid[1] + ny * off * 0.7), 1.1, D, 2, shade=False)
    sp.shade(light=1.0, dark=1.3, outline=(G, 0))
    sprite_planes(sp, m, name, z)


def fan(root, n, ln0, dln, ang0, dang, w0=6, w1=2.6, step=(0, 0)):
    """根元から扇に開く羽の並び"""
    out = []
    for k in range(n):
        a = (root[0] + step[0] * k, root[1] + step[1] * k)
        ang = math.radians(ang0 + dang * k)
        ln = ln0 + dln * k
        out.append((a, (a[0] + math.cos(ang) * ln, a[1] + math.sin(ang) * ln), w0, w1))
    return out


def glow_plane(m, name, draw, bounds, z):
    sp = Sprite(*bounds)
    draw(sp)
    sprite_planes(sp, m, name, z)
