# -*- coding: utf-8 -*-
"""嵐のシリーズ（look5・個性版）。三章の巨神「雷翼」（嵐を纏う鳥）、避雷の塔、風見の丘から一品ずつ形を立てる。
色: 灰紫の嵐雲（storm）の 1 系統、差し色は銅（copper）、光は稲妻の黄白（bolt）。

形は他のシリーズから借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 細い鉄の避雷針。握りは革の巻き、握りの上から羽根の房が二本垂れる。下は二股の接地の叉。
2026-11-19: 弓以外を階級に合わせて作り直した（エピック = 飾り 2〜3 か所、ヒロイック = 輪郭が変わる大物の飾り）。
- 光は品ごとに場所を変える（ユーザー: 「本当にそこだけなの？」）:
  鉄床雲の鎚 = 雲の下から落ちる二本の稲妻、雲籠の杖 = 球電と浮く火花、雷鳴りの鈴杖 = 稲妻の短冊、短剣 = 裂け目の火花の玉、
  雷獣の爪 = 爪の間の電弧、剣 = 雷を受けた刃の先、風弓 = 弦。雷石の鎚・雷羽の杖・風見の鈴杖・雷翼の弓は光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'storm': ['#16141f', '#24203a', '#342e52', '#48406c', '#5e5786', '#7a74a2', '#9a96bc', '#bebcd6', '#e0e0ee', '#f8f8ff'],
    'copper': ['#3a1c10', '#6a3418', '#9a5424', '#c87838', '#e8a060', '#fcd0a0'],
    'bolt': ['#6a5a10', '#b09a20', '#e8d040', '#fff070', '#fffbc0', '#ffffff'],
})
S, K, B = 'storm', 'copper', 'bolt'


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """45 度に回した四角（菱）"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def M(name):
    return Model(name, accent=K, glowmat=B)


def rod(m, y0, grip_to, y1, tassel=True, fork=True):
    """嵐の柄: 握り（革の巻き）と、その上の細い鉄の避雷針。握りの上から羽根の房、下に二股の接地の叉"""
    m.box('grip', S, [-1.5, y0, -1.5], [1.5, grip_to, 1.5], grad=(y0, 1, grip_to, 3), wrap=(y0, grip_to))
    if y1 > grip_to:
        m.box('rod', S, [-1.25, grip_to, -1.25], [1.25, y1, 1.25], grad=(grip_to, 3, y1, 6))
    if tassel:
        m.box('tassel_knot', K, [1.25, grip_to - 2, -1], [3.25, grip_to, 1], 4)
        m.seg('feather_0', S, (2.5, grip_to - 1.5), (4.5, grip_to - 7), 2, 0.5, 8, 6, tip=False)
        m.seg('feather_1', S, (3, grip_to - 1.5), (6, grip_to - 5.5), 2, 0.5, 8, 6, tip=False, zc=0.6)
    if fork:
        m.seg('fork_l', K, (-0.5, y0 + 0.5), (-2, y0 - 4), 2, 1, 3, 5, tip=True)
        m.seg('fork_r', K, (0.5, y0 + 0.5), (2, y0 - 4), 2, 1, 3, 5, tip=True)


def suzu(m, prefix, cx, cy, s=2.0):
    """嵐の鈴: 丸い鈴（二枚の板を 45 度ずらして八角に見せる）。下に口の切れ目（筆で描く）"""
    m.box(f'{prefix}_a', K, [cx - s, cy - s, -s * 0.75], [cx + s, cy + s, s * 0.75], grad=(cy - s, 3, cy + s, 5))
    m.seg(f'{prefix}_b', K, (cx - s * 0.7, cy - s * 0.7), (cx + s * 0.7, cy + s * 0.7), 2 * s * 0.95, s * 0.7, 3, 5, over=s * 0.3)


def mirror(p, sign):
    return (p[0], 15 + sign * (p[1] - 15))


def strings(m, top, bot, mat='string'):
    for key, pull in (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)):
        if pull == 0:
            m.box(f'string_{key}', mat, [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', mat, mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', mat, bot, mid, 1, 0.5, 3, 3, over=0.2)


def bow_grip(m):
    """嵐の弓の握り: 革を巻いた細い握りの下から、羽根の房が二本垂れる"""
    m.box('grip', S, [10.5, 9, -1.75], [13.5, 21, 1.75], grad=(9, 2, 21, 4), wrap=(9, 21))
    m.box('tassel_knot', K, [13.5, 9.5, -1], [15.5, 11.5, 1], 4)
    m.seg('feather_0', S, (14.5, 10), (16, 4.5), 2, 0.5, 8, 6)
    m.seg('feather_1', S, (15, 10), (18, 6), 2, 0.5, 8, 6, zc=0.6)


# ---------------------------------------------------------------- 形の部品（2026-11-19 の作り直し）

def zig(m, name, mat, pts, w, d, t0, t1, tip=True):
    m.chain(name, mat, pts, w, d, t0, t1, tip=tip)


def stone_head(m, s=1.0):
    m.box('stone', S, [-6 * s, 24, -3.5 * s], [6 * s, 24 + 12 * s, 3.5 * s], grad=(24, 2, 24 + 12 * s, 4))
    m.box('stone_round', S, [-5 * s, 23, -2.75 * s], [5 * s, 25 + 12 * s, 2.75 * s], grad=(23, 2, 25 + 12 * s, 4))
    cy = 24 + 6 * s
    m.seg('stone_face', S, (6 * s, cy), (12 * s, cy), 9 * s, 3 * s, 4, 6, tip=True)
    m.seg('stone_back', S, (-6 * s, cy), (-10 * s, cy + 1), 7 * s, 3 * s, 3, 5, tip=True)
    for k, (a, b) in enumerate((((-6.25, 25), (5.75, 35)), ((-6.25, 35), (5.75, 25)))):
        m.seg(f'lash_{k}', K, a, b, 2.2, 3.8 * s, 3, 5, over=0)


def anvil_cloud(m, w=13):
    m.box('anvil_top', S, [-w, 37, -3], [w, 40.5, 3], grad=(37, 6, 40.5, 8), rim=True)
    m.box('anvil_lip_l', S, [-w - 2, 37.5, -2.5], [-w + 1, 39.5, 2.5], 7)
    m.box('anvil_lip_r', S, [w - 1, 37.5, -2.5], [w + 2, 39.5, 2.5], 7)
    m.box('tower_l', S, [-8, 28, -3.75], [0.5, 37.5, 3.75], grad=(28, 3, 37.5, 6))
    m.box('tower_r', S, [-0.5, 26, -3.75], [8, 37.5, 3.75], grad=(26, 3, 37.5, 6))
    m.box('puff_l', S, [-11, 30, -3], [-7, 35, 3], grad=(30, 4, 35, 6))
    m.box('puff_r', S, [7.5, 29, -3], [11.5, 35.5, 3], grad=(29, 4, 35.5, 6))
    m.box('base', S, [-6, 24.5, -3.25], [6, 27, 3.25], grad=(24.5, 2, 27, 4))


def feather(m, prefix, shaft, L0=6.5, zc=0.0):
    m.chain(f'{prefix}_rachis', S, shaft, 2.2, 0.8, 7, 9, zc=zc)
    n = len(shaft) - 1
    for k in range(n):
        (x0, y0), (x1, y1) = shaft[k], shaft[k + 1]
        for t in (0.25, 0.75):
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            L = L0 - 0.55 * (k * 2 + (t > 0.5)) * 6.5 / L0
            m.seg(f'{prefix}_vl{k}{int(t * 4)}', S, (x - 0.5, y), (x - L, y + L * 0.55), 3.4, 0.5, 6, 8, tip=True, zc=zc - 0.2)
            m.seg(f'{prefix}_vr{k}{int(t * 4)}', S, (x + 0.5, y), (x + L * 0.85, y + L * 0.7), 3.4, 0.5, 6, 8, tip=True, zc=zc + 0.2)


def wings(m, span=18, up=0, prefix='wing'):
    for s, n in ((-1, 'l'), (1, 'r')):
        pts = [(0, 37), (s * span * 0.3, 40 + up * 0.3), (s * span * 0.65, 42 + up * 0.7), (s * span, 40 + up)]
        m.chain(f'{prefix}_{n}', S, pts, [4.5, 4, 3.2], 1.2, 4, 8, tip=True)
        for k in range(3):
            f = 0.35 + k * 0.25                                            # 翼の線の上の位置（付け根 0 → 先 1）から高さを取り、羽を翼に差す
            x = s * span * f
            y = (40 + up * 0.3 + (2 + up * 0.4) * (f - 0.3) / 0.35) if f < 0.65 else (42 + up * 0.7 - (2 - up * 0.3) * (f - 0.65) / 0.35)
            y -= 0.5
            m.seg(f'{prefix}_{n}_f{k}', S, (x, y), (x + s * 1.5, y - 4.5 + k * 0.8), 2.4, 0.6, 5, 7, tip=True)


def tubes(m, xs, top_fn):
    for s, n in ((-1, 'l'), (1, 'r')):
        for k, (x, L) in enumerate(xs):
            top = top_fn(x)
            m.box(f'cord_{n}{k}', K, [s * x - 0.5, top - 2.5, -0.5], [s * x + 0.5, top, 0.5], 4)
            m.box(f'tube_{n}{k}', K, [s * x - 1.1, top - 2.5 - L, -1.1], [s * x + 1.1, top - 2.5, 1.1], grad=(top - 2.5 - L, 2, top - 2.5, 5))


def dagger_hilt(m):
    rod(m, -6, 5, 5, tassel=True)
    m.box('guard', K, [-3, 5, -2], [3, 7.5, 2], grad=(5, 3, 7.5, 5))
    for s, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'guard_wing_{n}', K, [(s * 2.5, 6.5), (s * 6, 7.5), (s * 8, 10)], [2.4, 1.8], 1.1, 4, 6, tip=True)


def paw(m):
    rod(m, -8, 3, 3, tassel=False)
    m.box('cuff', K, [-3, 2.5, -2.75], [3, 5, 2.75], 4)
    m.box('paw', S, [-4.5, 5, -2.75], [5.5, 12, 2.75], grad=(5, 2, 12, 5), rim=True)


def sword_base(m, blade_w=5, edge=True):
    rod(m, -10, 3, 3)
    m.box('grip_band_0', K, [-1.75, -6.5, -1.75], [1.75, -5, 1.75], 4)
    m.box('grip_band_1', K, [-1.75, -1, -1.75], [1.75, 0.5, 1.75], 4)
    m.box('guard', S, [-3.5, 3, -2.2], [3.5, 6.5, 2.2], grad=(3, 3, 6.5, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'blade_{n}', S, (sd * 2.5, 6), (sd * 0.25, 46), blade_w, 1, 3, 6.5)
        if edge:
            m.seg(f'edge_{n}', S, (sd * 4.5, 6.5), (sd * 2.4, 46), 2, 1.1, 6, 8.5)
        m.seg(f'tip_{n}', B, (sd * 2.2, 45), (0, 54), 2.6, 0.85, 2, 5, tip=True)
    m.box('fuller', S, [-0.5, 9, -1.15], [0.5, 38, 1.15], grad=(9, 1, 38, 3))


# ---------------------------------------------------------------- 鎚

def storm_hammer():
    """雷石の鎚（エピック）: 銅の×で縛った雷石。裏から雷の通り道の白いガラス管（フルグライト）が枝分かれして後ろへ伸びる"""
    m = M('storm_hammer')
    rod(m, -12, 8, 25)
    stone_head(m)
    m.chain('fulgur', S, [(-9, 31), (-13, 34), (-15, 39), (-18, 42)], [2.6, 2.2, 1.6], 1.4, 7, 9, tip=True)
    m.chain('fulgur_b', S, [(-13, 34), (-17, 33), (-20, 35)], [1.8, 1.4], 1.1, 7, 9, tip=True)
    m.chain('fulgur_c', S, [(-15, 39), (-13.5, 43), (-14.5, 46)], [1.6, 1.2], 1, 7, 9, tip=True)
    m.box('rod_tip', K, [-1, 35.5, -1], [1, 39, 1], 5)
    return m


def storm_anvil_hammer():
    """鉄床雲の鎚（ヒロイック）: 頂が平らに広がる雷雲。左右の雲の下から一本ずつ稲妻（左右で形を変える）。頂の両端は庇のように張り出す"""
    m = M('storm_anvil_hammer')
    rod(m, -12, 8, 43)
    anvil_cloud(m, w=15)
    m.seg('rod_spike', K, (0, 42), (0, 48), 2.2, 1.1, 4, 5, tip=True)
    zig(m, 'bolt_r', B, [(8, 28.5), (12, 24), (9.5, 20.5), (13, 16)], [3, 2.4, 2], 0.9, 3, 5)
    zig(m, 'bolt_l', B, [(-8, 29.5), (-11, 26), (-9, 23.5), (-12, 20)], [2.6, 2.2, 1.8], 0.9, 3, 5)
    return m


# ---------------------------------------------------------------- 杖

def storm_staff():
    """雷羽の杖（エピック）: 二枚の羽根が × に交わり、交わりを銅の線で縛る"""
    m = M('storm_staff')
    rod(m, -14, 2, 31)
    m.box('quill_cap', K, [-1.8, 30, -1.8], [1.8, 34, 1.8], grad=(30, 3, 34, 5))
    feather(m, 'fa', [(0, 33.5), (2, 40), (4.5, 47), (7.5, 54), (10, 58)], L0=5.5, zc=-0.8)
    feather(m, 'fb', [(0, 33.5), (-2, 40), (-4.5, 47), (-7.5, 54), (-10, 58)], L0=5.5, zc=0.8)
    m.box('tie', K, [-2.5, 36, -2], [2.5, 38, 2], 5)
    return m


def storm_cloud_staff():
    """雲籠の杖（ヒロイック）: 半径 9 の雲の筋六本の籠。球電を大きく、周りに火花が二つ浮く"""
    m = M('storm_cloud_staff')
    rod(m, -14, 2, 33)
    m.box('cup', K, [-4.5, 32, -4.25], [4.5, 36.5, 4.25], grad=(32, 3, 36.5, 5))           # 籠の筋を全部受ける広い銅の受け
    for k, (a0, a1, r, zc) in enumerate(((265, 95, 9, 0), (-85, 85, 9, 0), (250, 110, 7, -3.5), (-70, 70, 7, 3.5), (250, 110, 7, 3.5), (-70, 70, 7, -3.5))):
        m.arc(f'wisp_{k}', S, (0, 43), r, a0, a1, 6, 2.6, 1, 5, 8, zc=zc)
    m.box('knot', S, [-2.5, 49.5, -4.25], [2.5, 54.5, 4.25], grad=(49.5, 6, 54.5, 8))      # 筋を上でまとめる結び
    m.seg('knot_curl', S, (2, 53.5), (5.5, 57), 2.4, 1.2, 7, 9, tip=True)
    m.box('float_ball', B, [-3.5, 39.5, -3.5], [3.5, 46.5, 3.5], gem=True)                  # 籠の中に浮く球電
    for k, (x, y) in enumerate(((-12.5, 46), (12, 39))):
        diamond(m, f'spark_{k}', B, (x, y), 2.2, 1, 4, 5)
    return m


# ---------------------------------------------------------------- 鈴杖

def storm_bell_staff():
    """風見の鈴杖（エピック）: 風見の矢、横木の両端から鈴（左右で大きさを変える）。方角の銅の四つの小さな腕"""
    m = M('storm_bell_staff')
    rod(m, -14, 2, 44)
    m.seg('arrow', K, (-9, 43), (10, 43), 2.4, 1.2, 3, 5)
    m.seg('arrow_head_u', K, (11.5, 43), (7.5, 46.5), 2.4, 1.2, 4, 5, tip=True)
    m.seg('arrow_head_d', K, (11.5, 43), (7.5, 39.5), 2.4, 1.2, 4, 5, tip=True)
    m.seg('tail_u', S, (-8.5, 43.5), (-13, 47.5), 3.2, 0.6, 7, 9, tip=True)
    m.seg('tail_d', S, (-8.5, 42.5), (-13, 38.5), 3.2, 0.6, 7, 9, tip=True)
    m.seg('bar', S, (-8, 35), (8, 35), 2.2, 1.2, 4, 6)
    for sd, n, s in ((-1, 'l', 2.4), (1, 'r', 1.8)):
        m.box(f'bar_tip_{n}', K, [sd * 8 - 1, 34, -1.4], [sd * 8 + 1, 36, 1.4], 4)
        m.box(f'cord_{n}', K, [sd * 8 - 0.6, 30, -0.6], [sd * 8 + 0.6, 34, 0.6], 3)
        suzu(m, f'bell_{n}', sd * 8, 30 - s, s)
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        m.box(f'point_{k}', K, [min(0, dx * 3.5) - 0.5, 38.5, min(0, dz * 3.5) - 0.5], [max(0, dx * 3.5) + 0.5, 39.5, max(0, dz * 3.5) + 0.5], 5)
    return m


def storm_thunder_bell_staff():
    """雷鳴りの鈴杖（ヒロイック）: 翼の上に銅の光輪（太鼓の輪）が立ち、輪の周りに小さな太鼓が六つ（雷神の連鼓）。管は翼の下に二本ずつ"""
    m = M('storm_thunder_bell_staff')
    rod(m, -16, 2, 38)
    wings(m, span=14)
    m.arc('halo', K, (0, 49), 9, 0, 360, 14, 1.6, 1.2, 3, 5)
    for k in range(6):
        a = math.radians(90 + k * 60)
        x, y = 9 * math.cos(a), 49 + 9 * math.sin(a)
        m.box(f'drum_{k}', K, [x - 1.8, y - 1.8, -1.6], [x + 1.8, y + 1.8, 1.6], grad=(y - 1.8, 3, y + 1.8, 5))
    m.box('halo_post', K, [-0.8, 38, -0.8], [0.8, 41, 0.8], 4)
    tubes(m, ((5, 5), (10, 7)), lambda x: 40 + x * 0.16)                   # 紐の上の端を翼の中に差す
    zig(m, 'tanzaku', B, [(0, 36.5), (2, 32), (-1.5, 28), (1, 24.5)], [2.6, 2.4, 2], 0.5, 4, 2, tip=False)
    return m


# ---------------------------------------------------------------- 短剣・爪

def storm_dagger():
    """稲妻の短剣（エピック）: 先で二股に裂ける稲妻の刃。裂け目の付け根に光る火花の玉"""
    m = M('storm_dagger')
    dagger_hilt(m)
    zig(m, 'blade', S, [(0, 7), (2.5, 12.5), (-1.5, 18), (1, 23)], [4.2, 3.6, 3.2], 1.1, 4, 7, tip=False)
    zig(m, 'fork_a', S, [(1, 23), (3.5, 27.5), (2, 31), (4, 35)], [2.6, 2.2, 1.8], 1, 7, 9)
    zig(m, 'fork_b', S, [(1, 23), (-1.5, 27), (-0.5, 30)], [2.4, 2], 0.9, 7, 9)
    diamond(m, 'node', B, (1, 23), 3, 1.4, 4, 5)
    return m


def raiju_claw():
    """雷獣の爪（ヒロイック）: 毛の掌から爪が四本（長さを変える）。爪の間に電弧が三本、袖口に毛のたてがみ"""
    m = M('raiju_claw')
    paw(m)
    claws = {
        'a': [(-3.5, 11.5), (-6, 16.5), (-7, 21.5), (-6, 25)],
        'b': [(-1, 11.5), (-1.5, 18), (-1, 24), (0.5, 28.5)],
        'c': [(2, 11.5), (3, 18), (4.5, 23.5), (6.5, 27)],
        'd': [(4.5, 11.5), (7.5, 15.5), (9.5, 19.5), (10, 22.5)],
    }
    for n, pts in claws.items():
        m.chain(f'claw_{n}', S, pts, [3.4, 2.8, 2.2], 1.3, 6, 9, tip=True)
    m.chain('arc_ab', B, [(-6, 19), (-4, 20.5), (-3, 18.5), (-1, 20)], 2, 0.4, 3, 5, tip=False)
    m.chain('arc_bc', B, [(-1, 22), (1, 20.5), (2, 22.5), (4, 21)], 2, 0.4, 3, 5, tip=False)
    m.chain('arc_cd', B, [(4, 19), (5.5, 17.5), (6.5, 19), (8.5, 17.5)], 2, 0.4, 3, 5, tip=False)
    for k, (a, b) in enumerate((((-3, 4), (-7.5, 2)), ((3, 4), (7.5, 2)), ((-3, 6), (-7, 6.5)), ((3, 6), (7.5, 6.5)))):
        m.seg(f'mane_{k}', S, a, b, 2.4, 1.4, 5, 7, tip=True)
    return m


# ---------------------------------------------------------------- 弓

def wing_plate(m, name, arm, mirror15):
    """雷翼の弓の翼の面（板の絵）: 弓腕（前縁）から弦の側へ、先の尖った風切羽が握りの方へ流れて重なる。
    羽の長さは弓腕の中ほどで長く両端で短い。羽と羽の境に暗い線、羽軸に明るい線。弦（x 4.5）の手前で止める"""
    back = lib.offset_line(arm, 2.0)
    n_f = 6

    def f(x, y, cur):
        d, tt = lib.near_line((x, y), arm)
        if lib.near_line((x, y), back)[0] < d or x < 5.6:              # 背の側と弦より外は塗らない
            return cur
        if tt < 0.06 or tt > 0.94:
            return cur
        u = (tt - 0.06) / 0.88 * n_f
        k, v = int(u), u - int(u)                                       # k 枚目の羽、羽の中の位置 v（0 = 先側の端）
        tc = 0.06 + (k + 0.5) / n_f * 0.88
        L = 2.5 + 5.0 * math.sin(math.pi * tc)                          # 羽の長さ
        depth = L * (0.35 + 0.65 * v)                                   # 握りの側ほど長く、先は尖る
        if d > depth:
            return cur
        if v < 0.14:
            return (S, 4)                                               # 羽と羽の境
        if abs(v - 0.6) < 0.09 and d < depth - 0.6:
            return (S, 9)                                               # 羽軸
        if d > depth - 0.6:
            return (S, 5)                                               # 羽の縁
        return (S, 8 if d < 1.6 else 7)
    cv = lib.Canvas(5, 21, 16, 46)
    cv.paint(f)
    cv.put(m, name, at=0.0, mirror15=mirror15)


def thunder_wing_bow():
    """雷翼の弓（エピック）: 弓腕そのものが一枚の翼。背の縁は銅の骨、腹（弦の側）へ翼の面（板の絵）が広がり、
    先の尖った風切羽が握りの方へ流れて重なる（弓腕と弦の間に収め、輪郭の外へ出さない）。羽の付け根に銅の縁。翼の先の風切羽は長く外へ伸び、その縁が雷の色に光る。弦には銅の留めが二つ"""
    m = M('thunder_wing_bow')
    bow_grip(m)
    arm = [(12, 21), (14.5, 27), (15, 33), (13, 38.5), (9.5, 42.5), (5.5, 45)]
    top, bot = (4.5, 45.5), (4.5, -15.5)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'bone_{n}', K, [P(q) for q in arm], [2, 2, 1.75, 1.5, 1.25], 1.3, 3, 5, tip=False)
        wing_plate(m, f'wing_{n}', arm, sign < 0)                       # 翼の面（板の絵）: 弓腕と弦の間に先の尖った羽が重なる
        m.seg(f'primary_{n}', S, P((6.5, 44.5)), P((2, 53)), 3.6, 1.1, 7, 9, tip=True)
        m.seg(f'primary_edge_{n}_glow', B, P((5.5, 46.5)), P((2.6, 51.5)), 1.0, 1.15, 4, 5, tip=True)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((38, -8)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_clasp{k}', K, [q[0] - 0.9, q[1] - 1.1, -0.9], [q[0] + 0.9, q[1] + 1.1, 0.9], 4)
            m.box(f'string_{key}_clasp{k}_glow', B, [q[0] - 0.45, q[1] - 0.45, -1.05], [q[0] + 0.45, q[1] + 0.45, 1.05], 5)
    return m


def wind_streaks(m, name, lines, box, at, mirror15, dirx=1):
    """風の筋（板の絵）: 細い線が流れて先で小さく巻く。線は根元が太く先が細い。dirx=-1 で -x へ流れて巻く"""
    cv = lib.Canvas(*box)
    curves = []
    for pts, curl in lines:
        ex, ey = pts[-1]
        c = [(ex + dirx * abs(curl) * math.sin(a / 6 * math.pi * 1.6), ey + curl * (1 - math.cos(a / 6 * math.pi * 1.6))) for a in range(1, 7)]
        curves.append(pts + c)

    def f(x, y, cur):
        for k, pts in enumerate(curves):
            d, t = lib.near_line((x, y), pts)
            if d < 0.55 - 0.3 * t:
                return (S, 9 if t < 0.5 else 8) if k % 2 == 0 else (S, 8 if t < 0.5 else 7)
        return cur
    cv.paint(f)
    cv.put(m, name, at=at, mirror15=mirror15)


def storm_gale_bow():
    """嵐の風弓（伝説）: 太い嵐雲色の弓腕の先が大きな風の渦に巻き込む。弓腕のまわりを風の輪が二重に取り巻いて（奥行きにも回る）、弦からは風の筋（板の絵）が射手の側（矢と逆の向き）へ流れて先で小さく巻き、弦と一緒に引かれる。握りの前には銅の風見の羽根が三枚開き、
    弦そのものは稲妻で光り、銅の風鈴の板が上下に二枚ずつ下がる"""
    m = M('storm_gale_bow')
    m.box('grip', S, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', K, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    m.box('vane_hub', K, [13.75, 13.75, -1], [15.75, 16.25, 1], 4)
    for k, (e, w) in enumerate((((21.5, 15), 2.5), ((19.5, 20.5), 2), ((19.5, 9.5), 2))):
        m.seg(f'vane_{k}', K, (15.5, 15), e, w, 0.55, 3, 5, tip=True)
    diamond(m, 'vane_eye_glow', B, (14.75, 15), 1.25, 1.1, 4, 5)
    arm = [(12, 20.5), (14.25, 27), (14.75, 33), (13.25, 39), (10.25, 44), (6.5, 47)]
    curl = [(6.5, 47), (2.5, 48.25), (-0.5, 46), (0, 42.5), (3, 41.25), (5, 43)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'arm_{n}', S, [P(q) for q in arm], [4, 3.5, 3.25, 2.75, 2.5], 1.45, 3, 6, tip=False)
        m.chain(f'curl_{n}', S, [P(q) for q in curl], [3, 2.75, 2.5, 2, 1.75], 1.2, 6, 9, tip=True)
        m.chain(f'curl_in_{n}_glow', B, [P(q) for q in ((1.25, 45.5), (1.5, 43.5), (3, 43))], [0.8, 0.7], 0.6, 4, 5, tip=True)
        # 腕を取り巻く風の輪（奥行きにも回る）
        for r, (cx, cy) in enumerate(((14.6, 31), (12, 41.5))):
            c = P((cx, cy))
            R = 4.25 - r * 0.6
            t, cut = 0.9, 0.9
            for sd, (z0, z1) in (('a', (-R, -R + t)), ('b', (R - t, R))):
                m.box(f'ring_{n}{r}{sd}', K, [c[0] - R + cut, c[1] - 0.45, z0], [c[0] + R - cut, c[1] + 0.45, z1], 4)
            for sd, (x0, x1) in (('c', (c[0] - R, c[0] - R + t)), ('d', (c[0] + R - t, c[0] + R))):
                m.box(f'ring_{n}{r}{sd}', S, [x0, c[1] - 0.45, -R + cut], [x1, c[1] + 0.45, R - cut], 7)
            for sd, (x, z) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
                cx0, cz0 = c[0] + x * (R - cut), z * (R - cut)
                m.box(f'ring_{n}{r}k{sd}', K, [min(cx0, cx0 - x * t), c[1] - 0.45, min(cz0, cz0 - z * t)],
                      [max(cx0, cx0 - x * t), c[1] + 0.45, max(cz0, cz0 - z * t)], 5)
            for sd, zz in (('f', 1), ('b', -1)):   # 輪を弓腕へつなぐ細い留め
                m.box(f'ring_{n}{r}tie{sd}', K, [c[0] - 0.4, c[1] - 0.35, min(zz * 1.2, zz * (R - t))], [c[0] + 0.4, c[1] + 0.35, max(zz * 1.2, zz * (R - t))], 3)
    top, bot = (5, 47), (5, -17)
    strings(m, top, bot, mat=B)
    for key, pull in lib.PULLS:
        for k, y in enumerate((39, 35, -5, -9)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_chime{k}', K, [q[0] - 0.9, q[1] - 2.25, -0.25], [q[0] + 0.9, q[1], 0.25], 3 + k % 2)
        # 弦の風の筋（板の絵）: 弦の上から射手の側（-x、矢の飛ぶ向きと逆）へ流れ、先で小さく巻く。弦と一緒に引かれる。引きの通り道（y 8〜22）は空ける
        # 前の弓腕の風の筋（表と裏の二枚、巻きの向きは表裏で逆）を左右逆にして、そのまま弦に付けた形。二枚で弦をはさむ
        q0 = [lib.string_at(top, bot, pull, lib.string_t(top, bot, y)) for y in (29.5, 35, 41)]
        for zs, zn in ((1, 'f'), (-1, 'b')):
            lines = []
            for (qx, qy), rel, curl in zip(q0, ([(0, 0), (-3.5, 2), (-7.5, 1), (-11.5, 2.5)],
                                                 [(0, 0), (-3.5, 2.5), (-7, 1.75), (-10, 3.25)],
                                                 [(0, 0), (-3, 2.5), (-6.5, 2), (-8.5, 4)]), (1.6, -1.2, 1.1)):
                lines.append(([(qx + 0.4 + x, qy + y) for x, y in rel], curl * zs))
            xs = [p[0] for pts, _ in lines for p in pts]
            box = (min(xs) - 4, 26, max(xs) + 1, 50)
            for sign, n in ((1, 'u'), (-1, 'd')):
                wind_streaks(m, f'string_{key}_wind_{n}{zn}', lines, box, zs * 0.3, sign < 0, dirx=-1)
    return m


# ---------------------------------------------------------------- 剣

def storm_sword():
    """雷翼の剣（エピック）: 鍔は上へ反る銅の翼（三段の羽）、細長い避雷の鉄の刃。翼を長く三段の羽、刃の樋に銅の稲妻、付け根を噛む銅の爪"""
    m = M('storm_sword')
    sword_base(m)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', K, [(sd * 3, 4.5), (sd * 8, 5.5), (sd * 12, 8), (sd * 14.5, 12)], [3.4, 2.8, 2.2], 1.3, 3, 5.5, tip=True)
        for k, (x, y, L) in enumerate(((6.5, 4.5, 3.5), (9.5, 6, 3), (12.5, 8.5, 2.5))):
            m.seg(f'wing_{n}_f{k}', S, (sd * x, y), (sd * (x + 1.5), y - L), 1.9, 0.6, 5, 7, tip=True)
        m.seg(f'claw_{n}', K, (sd * 3.5, 6.5), (sd * 1.4, 11), 1.9, 1.25, 3, 5, tip=True)
    zig(m, 'bolt_inlay', K, [(0, 13), (1.25, 18), (-1, 23), (0.75, 29), (0, 34)], 1, 1.25, 4, 5.5, tip=False)
    return m


ALL = [storm_hammer, storm_anvil_hammer, storm_staff, storm_cloud_staff, storm_bell_staff, storm_thunder_bell_staff,
       storm_dagger, raiju_claw, thunder_wing_bow, storm_gale_bow, storm_sword]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
