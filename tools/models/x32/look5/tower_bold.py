# -*- coding: utf-8 -*-
"""塔（look5・個性版）。天と淵のあいだの塔の主たち「側」の武器 6 品（docs/design/03 の塔の装備）。
色: 塔の石（slate 青灰）の 1 系統、差し色は古い真鍮（brass）、光は印の青白（sigil）。
天秤・目・落ちる月・引力の環・追う印・合わせた掌から一品ずつ形を立てる。
- 柄: 石の握りに真鍮の四角い輪を二つ、柄頭は四角錐の石。
- 光: あいだの大剣 = 刃の真ん中の境の線、側の目の弓 = 目、落ちる月の杖 = 月、引き合いの鎚 = 環の芯、追う印の短剣 = 印、合掌の鈴杖 = 掌のあいだ。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'slate': ['#14181e', '#222a32', '#323c46', '#46525e', '#5c6a78', '#768694', '#92a2ae', '#b0bec8', '#ccd8e0', '#e8f0f4'],
    'brass': ['#3a2a12', '#5e4620', '#86682e', '#ae8e42', '#d0b264', '#ecd69a'],
    'sigil': ['#2a4a8a', '#3e6ec0', '#5a9ae6', '#8cc4f8', '#c4e4ff', '#f0f8ff'],
    'abyssal': ['#0a0812', '#14101e', '#201a2e', '#2e2640', '#3e3454', '#524668'],
})
T, B, G, A = 'slate', 'brass', 'sigil', 'abyssal'


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """45 度に回した四角（菱）"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def M(name):
    return Model(name, accent=B, glowmat=G)


def grip(m, y0, y1):
    m.box('grip', T, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 2, y1, 4), wrap=(y0, y1))
    for k, y in enumerate((y0 + 0.5, y1 - 2)):
        m.box(f'ring_{k}', B, [-2, y, -2], [2, y + 1.5, 2], grad=(y, 2, y + 1.5, 4))
    m.box('pommel', T, [-2.25, y0 - 2.5, -2.25], [2.25, y0, 2.25], grad=(y0 - 2.5, 3, y0, 5))
    m.seg('pommel_tip', T, (0, y0 - 2.5), (0, y0 - 5), 2.5, 1.25, 5, 7, tip=True)


def rod(m, y0, y1, bands=()):
    m.box('rod', T, [-1.25, y0, -1.25], [1.25, y1, 1.25], grad=(y0, 3, y1, 6))
    for k, y in enumerate(bands):
        m.box(f'band_{k}', B, [-1.75, y, -1.75], [1.75, y + 1.5, 1.75], grad=(y, 2, y + 1.5, 4))


def towered(fn):
    """塔の色（石 = slate、金具 = brass、光 = sigil、暗 = abyssal）で神器の道具を使う"""
    def run():
        import divine_kit
        with divine_kit.pal(T, B, G, A):
            return fn()
    run.__name__ = fn.__name__
    run.__doc__ = fn.__doc__
    return run


def tgrip(m, y0, y1):
    """塔の柄: 石の握りに真鍮の四角い輪を二つ、柄頭は真鍮の枠の付いた石の四角錐"""
    from divine_kit import fm, frame_face, bar, skin
    skin(m, 'grip', T, 3.5, (-1.6, y0, -1.6), (1.6, y1, 1.6),
         fm(lambda sp, f, w, h: [sp.rect(0, j, w, j + 1, T, 1.5) for j in range(1, int(h), 2)]))
    for k, y in enumerate((y0 + 0.4, y1 - 1.9)):
        skin(m, f'grip_ring_{k}', B, 3.6, (-2.3, y, -2.3), (2.3, y + 1.7, 2.3), fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)))
    skin(m, 'pommel', T, 5, (-2.5, y0 - 3, -2.5), (2.5, y0 + 0.2, 2.5), frame_face(2.6, B))
    bar(m, 'pommel_tip', T, 7, (0, y0 - 2.8), (0, y0 - 6.4), 2.6, 1.3)
    skin(m, 'pommel_sigil', G, 4, (-0.8, y0 - 2.2, -2.7), (0.8, y0 - 0.6, 2.7), rot=45, pivot=(0, y0 - 1.4), glow=True)


@towered
def tower_balance_sword():
    """あいだの大剣（伝説、三回目）: 刃は真ん中の光る印の線で二つに分かれ、左は天の白い石、右は淵の黒。
    鍔は真鍮の天秤（竿・支点の菱の宝石・鎖で下がる二つの皿。左の皿に白い錘、右の皿に黒い錘）。切っ先の上に白と黒の欠片が浮く"""
    from divine_kit import fm, frame_face, edge_top, bar, gem, sparkles, skin
    m = M('tower_balance_sword')
    tgrip(m, -7, 6)
    skin(m, 'pivot_post', B, 3.6, (-1.3, 5.5, -1.3), (1.3, 11.5, 1.3))
    bar(m, 'beam', B, 3.6, (-12.5, 9.2), (12.5, 9.2), 2.2, 1.6, edge_top(B, 5.5))
    gem(m, 'pivot_gem', (0, 9.2), 4.4, 2.6)
    for sd, n, wm, wt in ((-1, 'l', T, 9), (1, 'r', A, 2)):
        x = sd * 11.5
        bar(m, f'chain_{n}', B, 4.5, (x, 8.4), (x, 4.6), 0.7, 0.45)
        skin(m, f'pan_{n}', B, 3.6, (x - 3.2, 3.4, -2.4), (x + 3.2, 4.8, 2.4), fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)))
        skin(m, f'weight_{n}', wm, wt, (x - 1.2, 4.6, -1.2), (x + 1.2, 7, 1.2))
    skin(m, 'collar', B, 3.4, (-5.6, 11, -1.9), (5.6, 13.6, 1.9), frame_face(2.2, B))

    def marks(mat, t):
        return fm(lambda sp, f, w, h: [sp.diamond((w / 2, y), 1.2, mat, t) for y in (h * 0.25, h * 0.55, h * 0.85)])
    skin(m, 'blade_sky', T, 7, (-4.8, 13, -1.2), (0, 52, 1.2), marks(T, 4))
    skin(m, 'blade_abyss', A, 4, (0, 13, -1.2), (4.8, 52, 1.2), marks(A, 5))
    bar(m, 'edge_sky', T, 9, (-4.9, 13.4), (-4.9, 51.6), 1.0, 0.75)
    bar(m, 'edge_abyss', G, 1.5, (4.9, 13.4), (4.9, 51.6), 1.0, 0.75)
    bar(m, 'tip_sky', T, 8, (-4.9, 51.2), (-0.1, 65), 3.0, 1.0)
    bar(m, 'tip_abyss', A, 4.5, (4.9, 51.2), (0.1, 65), 3.0, 1.0)
    skin(m, 'tip_core_sky', T, 7.5, (-2.6, 51, -1.1), (0, 58, 1.1))
    skin(m, 'tip_core_abyss', A, 4, (0, 51, -1.1), (2.6, 58, 1.1))
    skin(m, 'seam', G, 3, (-0.5, 13.4, -1.45), (0.5, 62, 1.45), fm(lambda sp, f, w, h: sp.rect(0, 0, w, h, G, 4.5)), glow=True)
    skin(m, 'float_shard_sky', T, 9, (-6.6, 67.4, -1.1), (-4.4, 69.6, 1.1), rot=45, pivot=(-5.5, 68.5))
    skin(m, 'float_shard_abyss', A, 3, (4.4, 65.4, -1.1), (6.6, 67.6, 1.1), rot=45, pivot=(5.5, 66.5))
    sparkles(m, 'float_sigil', [(0, 71)], -0.6)
    return m


@towered
def tower_pull_hammer():
    """引き合いの鎚（伝説、三回目）: 頭は内へ傾き合う二つの重い石の塊（外は平ら、上に真鍮の冠、前に光る印の目）。
    塊の内面の真鍮の板のあいだに真鍮の環がかかり、環の芯の光の菱が二つを引き合う。まわりに引かれた石の欠片が浮く"""
    from divine_kit import fm, frame_face, arc, bar, gem, skin, glow_plane
    m = M('tower_pull_hammer')
    tgrip(m, -6, 5)
    skin(m, 'rod', T, 3.5, (-1.3, 5, -1.3), (1.3, 32.5, 1.3))
    for k, y in enumerate((14, 22)):
        skin(m, f'rod_band_{k}', B, 3.8, (-1.8, y, -1.8), (1.8, y + 1.2, 1.8))
    c = (0, 37)
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = sorted((sd * 5, sd * 15))
        cx = (x0 + x1) / 2
        rot = -sd * 7
        skin(m, f'block_{n}', T, 5, (x0, 30, -5), (x1, 44, 5), frame_face(3.2, T), rot=rot, pivot=(cx, 37))
        skin(m, f'crown_{n}', B, 3.8, (x0 - 0.5, 43.6, -5.5), (x1 + 0.5, 45.6, 5.5), fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)),
             rot=rot, pivot=(cx, 37))
        skin(m, f'foot_{n}', B, 3.4, (x0 - 0.5, 28.6, -5.5), (x1 + 0.5, 30.4, 5.5), rot=rot, pivot=(cx, 37))
        skin(m, f'eye_{n}', G, 4, (cx - 1.4, 36, -5.4), (cx + 1.4, 38.4, 5.4), rot=rot, pivot=(cx, 37), glow=True)
        xi = sd * 5
        skin(m, f'plate_{n}', B, 4, (min(xi, xi - sd * 1.2), 31.5, -4), (max(xi, xi - sd * 1.2), 42.5, 4), frame_face(2.4, B))
    arc(m, 'ring', B, 3.6, c, 4.2, 0, 360, 10, 1.8, 1.8, 1.5, 1.5, fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)))
    gem(m, 'core', c, 4, 2.2, seat=False)

    def pull(sp):
        for y in (33.5, 40.5):
            for x0, x1 in ((-15.5, -18.5), (15.5, 18.5)):
                sp.line((x0, y), (x1, y + (1 if y > 37 else -1)), 1.0, G, 2.5, shade=False)
    glow_plane(m, 'float_pull', pull, (-20, 30, 20, 44), 0.0)
    for k, (x, y, s) in enumerate(((-9, 50, 2.2), (8.5, 52, 1.8), (-17.5, 27, 1.6), (17, 25.5, 2))):
        skin(m, f'float_chip_{k}', T, 6.5, (x - s / 2, y - s / 2, -s / 2), (x + s / 2, y + s / 2, s / 2), rot=45, pivot=(x, y))
    return m


@towered
def tower_moon_staff():
    """落ちる月の杖（伝説、三回目）: 杖の先の真鍮の冠から二本の大きな爪が反り上がって頂で結ばれ、その籠の中に
    光る月（大きな菱の光）が鎖で吊られる。籠の外へ月の欠片が落ちていき、後ろに落ちる光の筋"""
    from divine_kit import fm, frame_face, path, bar, skin, glow_plane, sparkles
    m = M('tower_moon_staff')
    tgrip(m, -14, 1.5)
    skin(m, 'rod', T, 3.5, (-1.3, 1.5, -1.3), (1.3, 41, 1.3))
    for k, y in enumerate((12, 24)):
        skin(m, f'rod_band_{k}', B, 3.8, (-1.8, y, -1.8), (1.8, y + 1.2, 1.8))
    skin(m, 'crown', B, 3.6, (-3.4, 39, -3.4), (3.4, 41.8, 3.4), frame_face(2.4, B))
    for sd, n in ((-1, 'l'), (1, 'r')):
        path(m, f'claw_{n}', B, 3.4, [(sd * 2.6, 41), (sd * 8, 45), (sd * 9, 52), (sd * 6.4, 58), (sd * 1.4, 61.2)],
             [2.6, 2.4, 2.1, 1.8], [1.6, 1.5, 1.4, 1.2], fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)), dt=0.4)
        bar(m, f'talon_{n}', B, 5, (sd * 8.6, 47), (sd * 6.2, 44.2), 1.2, 0.8)
    skin(m, 'knot', B, 4, (-1.8, 60, -1.8), (1.8, 63.4, 1.8), frame_face(2.4, B))
    bar(m, 'finial', B, 4.6, (0, 63), (0, 67.5), 1.6, 0.8)
    skin(m, 'finial_gem', G, 4, (-1, 66.6, -1), (1, 68.6, 1), rot=45, pivot=(0, 67.6), glow=True)
    bar(m, 'chain', B, 4.5, (0, 60.4), (0, 55.4), 0.7, 0.45)
    c = (0, 50.5)
    skin(m, 'moon', G, 3, (c[0] - 3.8, c[1] - 3.8, -3.8), (c[0] + 3.8, c[1] + 3.8, 3.8),
         fm(lambda sp, f, w, h: sp.diamond((w / 2, h / 2), w / 3, G, 5)), rot=45, pivot=c, glow=True)
    skin(m, 'moon_shadow', A, 3, (c[0] + 0.6, c[1] - 3.2, -4), (c[0] + 3.4, c[1] + 3.2, 4), rot=45, pivot=c)
    frags = [(-12, 42, 2.2), (11.5, 38, 1.8), (-9.5, 33, 1.4)]
    for k, (x, y, s) in enumerate(frags):
        skin(m, f'float_frag_{k}', G, 4, (x - s / 2, y - s / 2, -s / 2), (x + s / 2, y + s / 2, s / 2), rot=45, pivot=(x, y), glow=True)

    def trails(sp):
        for x, y, s in frags:
            sp.line((x, y + s * 0.6), (x * 0.75, y + 7), 1.2, G, 2, shade=False)
    glow_plane(m, 'float_trail', trails, (-14, 31, 14, 51), -1.2)
    return m


@towered
def tower_sigil_dagger():
    """追う印の短剣（伝説、三回目）: 鍔は菱に立てた真鍮の四角い輪で、中に印の宝石。細い石の刃の真ん中を印の筋が切っ先まで走り、
    刃の右に印の菱が三つ、切っ先から鍔へ小さくなりながら追ってくる（平面の光）"""
    from divine_kit import fm, bar, path, gem, skin, glow_plane
    m = M('tower_sigil_dagger')
    tgrip(m, -4, 3)
    c = (0, 8)
    pts = [(0, 2.2), (5.8, 8), (0, 13.8), (-5.8, 8), (0, 2.2)]
    path(m, 'ring', B, 3.6, pts, 1.8, 1.6, fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)), ext=0.9)
    gem(m, 'heart', c, 6.6, 2.2, seat=False)
    skin(m, 'blade', T, 6, (-2.6, 13, -0.9), (2.6, 38, 0.9), fm(lambda sp, f, w, h: [
        sp.cell[(i, j)].__setitem__('t', sp.cell[(i, j)]['t'] + j / h * 2) for i in range(w) for j in range(h)]))
    for sd, n in ((-1, 'l'), (1, 'r')):
        bar(m, f'edge_{n}', T, 9, (sd * 2.7, 13.6), (sd * 2.7, 37.6), 0.9, 0.7)
        bar(m, f'tip_{n}', T, 8, (sd * 2.8, 37.4), (0, 49), 2.2, 0.8)
        bar(m, f'spur_{n}', B, 4, (sd * 2.6, 14.6), (sd * 5, 17.6), 1.4, 0.9)
    skin(m, 'tip_core', T, 7.5, (-1.8, 37, -0.8), (1.8, 42, 0.8))
    skin(m, 'channel', G, 3, (-0.5, 14, -1.1), (0.5, 45, 1.1), fm(lambda sp, f, w, h: sp.rect(0, 0, w, h, G, 4.5)), glow=True)

    def chase(sp):
        for k, (y, r) in enumerate(((42, 2.6), (33, 2.0), (25, 1.5))):
            x = 7 + k * 0.6
            sp.diamond((x, y), r, G, 4 - k * 0.6, shade=False)
            sp.diamond((x, y), r * 0.45, G, 5, shade=False)
        sp.line((7, 39), (7.6, 35.5), 0.8, G, 2, shade=False)
        sp.line((7.6, 30.5), (8.2, 27.2), 0.8, G, 1.6, shade=False)
    glow_plane(m, 'float_chase', chase, (3, 21, 12, 46), 0.0)
    return m


@towered
def tower_clap_bell_staff():
    """合掌の鈴杖（伝説、三回目）: 杖の先で石の両の掌が合わさって指を上へ立て、掌のあいだから印の光が漏れる。
    手首に真鍮の腕輪が二重、腕輪から鈴が二つ下がる。手の後ろに真鍮の輪（光の印を四つ嵌める）"""
    from divine_kit import fm, frame_face, arc, bar, bell3d, skin
    m = M('tower_clap_bell_staff')
    tgrip(m, -14, 1.5)
    skin(m, 'rod', T, 3.5, (-1.3, 1.5, -1.3), (1.3, 40, 1.3))
    for k, y in enumerate((12, 24)):
        skin(m, f'rod_band_{k}', B, 3.8, (-1.8, y, -1.8), (1.8, y + 1.2, 1.8))
    c = (0, 53)
    arc(m, 'halo', B, 3.4, c, 11, 0, 360, 16, 1.5, 1.5, 0.7, 0.7, fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, B, 5.5)))
    for k in range(4):
        a = math.radians(45 + k * 90)
        p = (c[0] + math.cos(a) * 11, c[1] + math.sin(a) * 11)
        skin(m, f'halo_sigil_{k}', G, 4, (p[0] - 1.2, p[1] - 1.2, -1), (p[0] + 1.2, p[1] + 1.2, 1), rot=45, pivot=p, glow=True)
    for k, y in enumerate((36, 37.9)):
        skin(m, f'bracelet_{k}', B, 3.6 + k * 0.4, (-4.8, y, -3.2), (4.8, y + 2.2, 3.2), frame_face(2.4, B))
    skin(m, 'wrist', T, 5, (-4, 39.9, -2.6), (4, 43.4, 2.6))
    def fingers(sp, f, w, h):
        # 手の外の側面（回した板の up・down の面）に、指の境の線を三本
        if f in ('up', 'down'):
            for q in (0.25, 0.5, 0.75):
                sp.rect(0, h * q - 0.5, w, h * q + 0.5, T, 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        # 合わせた手を横から見た形: 手首から掌でふくらみ、指先へ細って頂で合わさる
        pts = [(sd * 2.25, 42.2), (sd * 2.7, 50), (sd * 2.05, 57.4), (sd * 0.95, 63.6)]
        for k, (pa, pb, w, t) in enumerate(zip(pts, pts[1:], (4.5, 4.1, 2.9), (6, 6.6, 7.4))):
            L = math.dist(pa, pb)
            ux, uy = (pb[0] - pa[0]) / L, (pb[1] - pa[1]) / L
            bar(m, f'hand_{n}{k}', T, t, (pa[0] - ux * 0.5, pa[1] - uy * 0.5), (pb[0] + ux * 0.5, pb[1] + uy * 0.5), w, 2.4,
                fingers if k else None)
        bar(m, f'tip_{n}', T, 8.5, (sd * 1.2, 62.6), (sd * 0.3, 65.2), 1.4, 2.0)
        bar(m, f'thumb_{n}', T, 7.5, (sd * 2.7, 45), (sd * 1.1, 53.5), 1.9, 0.75, zc=2.6)
        bar(m, f'thumb_tip_{n}', T, 8.5, (sd * 1.25, 52.6), (sd * 0.7, 55), 1.3, 0.65, zc=2.6)
        bar(m, f'arm_{n}', B, 4, (sd * 4.6, 37.2), (sd * 7.6, 37.2), 1.0, 0.7)
        bar(m, f'cord_{n}', B, 4.5, (sd * 7.4, 37.4), (sd * 7.4, 33.6), 0.6, 0.4)
        bell3d(m, f'bell_{n}', (sd * 7.4, 32), 1.9, B)
    skin(m, 'light', G, 3, (-0.45, 43, -2), (0.45, 64.5, 2), fm(lambda sp, f, w, h: sp.rect(0, 0, w, h, G, 4.5)), glow=True)
    from divine_bold import big
    return big(m, 30, 1.3, 1.3)


def mirror(p, sign):
    return (p[0], 15 + sign * (p[1] - 15))


def strings(m, top, bot):
    for key, pull in (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)):
        if pull == 0:
            m.box(f'string_{key}', 'string', [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', 'string', mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', 'string', bot, mid, 1, 0.5, 3, 3, over=0.2)


def sigil_wheel(m, name, c, at):
    """横向き（ZY）の印の輪（板の絵）: 真鍮の二重の輪のあいだに印の文字が八つ。前から見ると目を貫く輪"""
    cv = lib.Canvas(-10, c[1] - 10, 10, c[1] + 10)
    glyphs = ['x.x/.x./x.x', 'xxx/x.x/xxx', '.x./xxx/.x.', 'x../xxx/..x', 'xx./.x./.xx', 'x.x/xxx/x.x', '.xx/x../.xx', 'xxx/.x./.x.']

    def f(z, y, cur):
        dz, dy = z, y - c[1]
        r = math.hypot(dz, dy)
        if 8.6 < r < 9.6:
            return (B, 4 if dy > 0 else 3)
        if 5.9 < r < 6.5:
            return (B, 3)
        if 6.5 <= r <= 8.6:
            a = math.degrees(math.atan2(dy, dz)) % 360
            k = int((a + 22.5) // 45) % 8
            ca = math.radians(k * 45)
            gu = (dz - math.cos(ca) * 7.55) / 0.5 + 1.5
            gv = (dy - math.sin(ca) * 7.55) / 0.5 + 1.5
            if 0 <= gu < 3 and 0 <= gv < 3 and glyphs[k].split('/')[2 - int(gv)][int(gu)] == 'x':
                return (G, 4)
        return cur
    cv.paint(f)
    cv.put(m, name, at=at, plane='zy')


def tower_eye_bow():
    """側の目の弓（伝説）: 握りの前に石の瞼に抱かれた大きな一つ目が光り、そのまわりを真鍮の印の輪が二重に巡り、横向き（奥行きの面）にも印の文字を刻んだ輪（板の絵）が目を貫いて立つ。
    弓腕は角張った石の板を段に重ねた階段の形で、腹（弦の側）の継ぎ目ごとに小さな目が開いて弓の懐を見つめる。
    左右（奥行き）には印を刻んだ石の札が張り出し、両端は真鍮の尖塔。弦は真鍮の鎖の輪を連ねたもの"""
    m = M('tower_eye_bow')
    m.box('grip', T, [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    m.box('lid_u', T, [13.5, 17, -2.5], [20, 19.75, 2.5], 6)
    m.box('lid_d', T, [13.5, 10.25, -2.5], [20, 13, 2.5], 4)
    m.box('lash_u', T, [19, 19.75, -2], [20.5, 20.75, 2], 7)
    m.box('eye_glow', G, [13.75, 13, -2.25], [19.5, 17, 2.25], 4)
    m.box('pupil', A, [19, 14, -1.1], [19.9, 16, 1.1], 1)
    c = (17.5, 15)
    m.arc('ring_in', B, c, 6.25, -140, 140, 10, 0.9, 0.55, 3, 5)
    m.arc('ring_out', B, c, 8.5, -150, 150, 14, 0.8, 0.5, 3, 5)
    for k, a in enumerate((-120, -60, 0, 60, 120)):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        diamond(m, f'sigil_{k}_glow', G, (c[0] + dx * 7.4, c[1] + dy * 7.4), 1.25, 0.55, 4, 5)
    sigil_wheel(m, 'wheel', c, c[0])
    steps = [(12, 20), (13, 24.5), (12.5, 28.5), (13.25, 32.5), (11.5, 36.5), (10.25, 40), (7.75, 43), (5, 45.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in steps]
        m.chain(f'arm_{n}', T, pts, [4, 3.75, 3.75, 3.5, 3.25, 2.75, 2.25], 1.5, 3, 7, tip=False)
        for k in (1, 3, 5):
            x, y = pts[k]
            m.box(f'plate_{n}{k}', T, [x - 0.5, y - 1, -1.8], [x + 3.25, y + 1, 1.8], 5 + k * 0.3)
            m.box(f'plate_rim_{n}{k}', B, [x + 3.25, y - 1.25, -1.9], [x + 4, y + 1.25, 1.9], 4)
        for k in (2, 4):
            x, y = pts[k]
            m.box(f'belly_lid_{n}{k}', T, [x - 3.25, y - 1.1, -1.5], [x - 1.25, y + 1.1, 1.5], 6)
            m.box(f'belly_eye_{n}{k}_glow', G, [x - 3.6, y - 0.55, -0.9], [x - 2.9, y + 0.55, 0.9], 4)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            x, y = pts[3]
            z0, z1 = (1.5, 5.5) if zs > 0 else (-5.5, -1.5)
            m.box(f'tablet_{n}{zn}', T, [x - 1.5, y - 2.25, z0], [x + 1.5, y + 2.25, z1], 7)
            zg = (z1 - 0.1, z1 + 0.15) if zs > 0 else (z0 - 0.15, z0 + 0.1)
            m.box(f'tablet_rune_{n}{zn}_glow', G, [x - 0.75, y - 1.25, zg[0]], [x + 0.75, y + 1.25, zg[1]], 4)
        m.chain(f'spire_{n}', B, [P((5, 45.5)), P((3.5, 47.5)), P((4.25, 52))], [2.25, 1.5], 1.1, 3, 5, tip=True)
        diamond(m, f'spire_gem_{n}_glow', G, P((3.6, 47.6)), 1.4, 1.2, 4, 5)
    top, bot = (3.5, 47.5), (3.5, -17.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((44, 40.5, 37, 33.5, 30, 26.5, 3.5, 0, -3.5, -7, -10.5, -14)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            if k % 2:
                m.box(f'string_{key}_link{k}', B, [q[0] - 0.75, q[1] - 0.9, -0.3], [q[0] + 0.75, q[1] + 0.9, 0.3], 4)
            else:
                m.box(f'string_{key}_link{k}', B, [q[0] - 0.3, q[1] - 0.9, -0.75], [q[0] + 0.3, q[1] + 0.9, 0.75], 3)
    return m


ALL = [tower_balance_sword, tower_pull_hammer, tower_moon_staff, tower_sigil_dagger, tower_clap_bell_staff, tower_eye_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
