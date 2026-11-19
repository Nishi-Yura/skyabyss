# -*- coding: utf-8 -*-
"""火（余燼）のシリーズ（look5・個性版）。四章の巨神「余燼」（地の中を泳ぐ火蜥蜴）、灰の鍛え手、炉と香炉から一品ずつ形を立てる。
色: 灰をかぶった炭鉄（cinder）の 1 系統、差し色は焼き物の土（clay）、光は熾火の橙（ember）。

形は他のシリーズから借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 革を巻いた鉄の握り、上に焼き物の帯、柄頭は火蜥蜴の尾が横へ巻く。
- 弓: 握りの両端に焼き物の帯。灰の弓は背に火蜥蜴の背骨のとげ、火の粉の弓は焼けた枝。
2026-11-19: 弓以外を階級に合わせて作り直した（エピック = 飾り 2〜3 か所、ヒロイック = 輪郭が変わる大物の飾り）。
- 光は品ごとに場所を変える（同じ種類の場所は 2 回まで、光なしもある）:
  熾火の杖 = 火箸が挟む熾と火の粉、炉心の杖 = 窯の焚き口と天辺の炎、灰の香炉 = 蓋の窓と炎、香炉の鈴杖 = 香炉の穴と火蜥蜴の目、
  戦斧 = 焼けた刃と炎の歯、熾火の大剣 = 刃元と刃の割れ目、熾の大刃 = 炉の口と炎の舌の内側、火の粉の弓 = 枝の裂け目。
  熾の短剣・灰の小刀・灰の弓は光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'cinder': ['#141012', '#221c1c', '#322a28', '#463b36', '#5c4f48', '#75665c', '#928272', '#b2a290', '#d4c6b4', '#f2ead8'],
    'clay': ['#3e1a10', '#6a2e1a', '#94442a', '#bc6040', '#dc8a62', '#f4b894'],
    'ember': ['#7a1e04', '#c04008', '#f07018', '#ffa040', '#ffd890', '#fff6e0'],
})
I, K, E = 'cinder', 'clay', 'ember'


def M(name):
    return Model(name, accent=K, glowmat=E)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """一辺 s の正方形を 45 度回した菱形"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def haft(m, y0, grip_to, y1, tail=True, bands=()):
    """火の柄: 革巻きの鉄の握り、上に焼き物の帯と鋲。その上は鉄の棒で、bands の高さに鉄の輪。
    柄頭は火蜥蜴の尾が横へ巻き、尾の背に小さなとげが二つ"""
    m.box('grip', I, [-1.5, y0, -1.5], [1.5, grip_to, 1.5], grad=(y0, 1, grip_to, 3), wrap=(y0, grip_to))
    m.box('collar', K, [-2.25, grip_to, -2.25], [2.25, grip_to + 2.5, 2.25], grad=(grip_to, 3, grip_to + 2.5, 5))
    m.box('rivet_f', I, [-0.75, grip_to + 0.5, -2.75], [0.75, grip_to + 2, -2.25], 7)
    m.box('rivet_b', I, [-0.75, grip_to + 0.5, 2.25], [0.75, grip_to + 2, 2.75], 7)
    if y1 > grip_to + 2.5:
        m.box('rod', I, [-1.25, grip_to + 2.5, -1.25], [1.25, y1, 1.25], grad=(grip_to + 2.5, 3, y1, 6))
        for k, y in enumerate(bands):
            m.box(f'band_{k}', I, [-1.75, y, -1.75], [1.75, y + 2, 1.75], grad=(y, 2, y + 2, 4))
    if tail:
        m.chain('tail', I, [(0, y0 + 0.5), (0.75, y0 - 2.5), (2.5, y0 - 4.75), (5, y0 - 5.25), (6.5, y0 - 4)],
                [3, 2.75, 2.25, 2], [1.5, 1.25, 1.1, 1], 3, 6.5)
        m.seg('tail_spine_0', K, (-0.75, y0 - 1.5), (-2.75, y0 - 2.5), 2, 0.75, 4, 5, tip=True)
        m.seg('tail_spine_1', K, (1.25, y0 - 4.5), (0.5, y0 - 6.5), 2, 0.75, 4, 5, tip=True)


def lift(m, dy):
    """模型ごと上下に動かす。原点を握りの真ん中に合わせるため（docs/design/60 の 1.2。2026-11-18「持ち手がおかしい」）"""
    for p in m.parts:
        for k in ('frm', 'to', 'origin'):
            p[k][1] += dy
        if p['grad']:
            g = p['grad']
            p['grad'] = (g[0] + dy, g[1], g[2] + dy, g[3])
        if p['wrap']:
            p['wrap'] = (p['wrap'][0] + dy, p['wrap'][1] + dy)
    return m


# ---------------------------------------------------------------- 形の部品（2026-11-19 の作り直し）

def salamander_head(m, p, c, s=1.0, facing=1, eyes=True):
    """火蜥蜴の頭: 平たい頭と鼻先、背のとげ、光る目"""
    x, y = c
    m.box(f'{p}_skull', I, [x - 2.5 * s, y - 1.5 * s, -2 * s], [x + 2.5 * s, y + 1.5 * s, 2 * s], grad=(y - 1.5 * s, 3, y + 1.5 * s, 5))
    m.seg(f'{p}_snout', I, (x + facing * 2 * s, y), (x + facing * 5.5 * s, y - 0.5 * s), 2.6 * s, 1.6 * s, 4, 6, tip=True)
    m.seg(f'{p}_crest', K, (x - facing * 1 * s, y + 1.2 * s), (x - facing * 3.5 * s, y + 3 * s), 1.6 * s, 1 * s, 4, 6, tip=True)
    if eyes:
        for zs, zn in ((1, 'f'), (-1, 'b')):
            z0, z1 = sorted((zs * 2 * s, zs * 2.4 * s))
            m.box(f'{p}_eye_{zn}', E, [x + facing * 0.3 * s - 0.6, y, z0], [x + facing * 0.3 * s + 0.6, y + 1, z1], 5)


def tongs(m, open_=5.5, top=47):
    m.box('hinge', K, [-3, 31, -2.5], [3, 34.5, 2.5], grad=(31, 3, 34.5, 5))
    m.box('hinge_pin', I, [-3.75, 31.75, -0.75], [3.75, 33.75, 0.75], 6)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'tong_{n}', I, [(sd * 1.5, 34), (sd * open_, 38), (sd * (open_ + 0.75), 43.5), (sd * (open_ - 1.75), top - 0.5), (sd * 1.75, top)],
                [2.8, 2.8, 2.4, 2.2], 1.2, 4, 7.5, tip=True)


def censer_bowl(m, s=1.0, y0=33):
    m.box('cup', I, [-2.5, 30.5, -2], [2.5, 32.5, 2], 4)
    for sd, n in ((-1, 'l'), (0, 'm'), (1, 'r')):
        m.seg(f'leg_{n}', K, (sd * 4.5 * s, y0 + 1), (sd * 2.5, 31), 2.2, 1.1, 3, 4)
    m.box('bowl_low', K, [-5 * s, y0, -3.4 * s], [5 * s, y0 + 2, 3.4 * s], grad=(y0, 2, y0 + 2, 3))
    m.box('bowl', K, [-7 * s, y0 + 2, -4.4 * s], [7 * s, y0 + 6, 4.4 * s], grad=(y0 + 2, 3, y0 + 6, 5))
    m.box('lip', K, [-7.75 * s, y0 + 5.5, -5 * s], [7.75 * s, y0 + 7, 5 * s], 5)
    m.box('coals', E, [-5.5 * s, y0 + 6.5, -3.6 * s], [5.5 * s, y0 + 8, 3.6 * s], 2)


def hanging_censer(m, p, cx, top, s=1.0):
    m.box(f'{p}_link0', I, [cx - 0.75, top - 3.5, -0.5], [cx + 0.75, top, 0.5], 4)
    m.box(f'{p}_link1', I, [cx - 0.5, top - 6.5, -1], [cx + 0.5, top - 3, 1], 5)
    y = top - 6.5
    m.box(f'{p}_lid_top', K, [cx - 2 * s, y - 2, -1.75 * s], [cx + 2 * s, y, 1.75 * s], 5)
    m.box(f'{p}_lid', K, [cx - 3 * s, y - 4, -2.5 * s], [cx + 3 * s, y - 2, 2.5 * s], grad=(y - 4, 3, y - 2, 5))
    m.box(f'{p}_pot', K, [cx - 3.75 * s, y - 4 - 7 * s, -3 * s], [cx + 3.75 * s, y - 4, 3 * s], grad=(y - 4 - 7 * s, 2, y - 4, 4))
    m.box(f'{p}_band', I, [cx - 4 * s, y - 7.5 * s, -3.25 * s], [cx + 4 * s, y - 6.5 * s, 3.25 * s], 3)
    for k, (dx, dy) in enumerate(((-1.5, -6), (1.5, -6), (0, -9.5))):
        for zs, zn in ((1, 'f'), (-1, 'b')):
            z0, z1 = sorted((zs * 3 * s, zs * 3.4 * s))
            m.box(f'{p}_vent_{zn}{k}', E, [cx + dx * s - 0.9, y + dy * s, z0], [cx + dx * s + 0.9, y + dy * s + 1.8, z1], 3)


def axe_head(m, r1=6.5, r2=9.25, cy=27):
    m.box('cheek', I, [-2, cy - 5.5, -2], [3.5, cy + 5.5, 2], grad=(cy - 5.5, 3, cy + 5.5, 5))
    for sd, n in ((-1, 'f'), (1, 'b')):
        m.box(f'langet_{n}', I, [-1, 15, sd * 1.7 - 0.4], [1, cy - 4.5, sd * 1.7 + 0.4], 4)
    m.box('web', I, [3, cy - 5.5, -0.9], [8.5, cy + 5.5, 0.9], grad=(cy - 5.5, 3, cy + 5.5, 5))
    m.arc('blade', I, (3.5, cy), r1, -72, 72, 7, 5, 0.95, 4, 6)
    m.arc('edge', E, (3.5, cy), r2, -64, 64, 7, 2.2, 0.75, 3, 5)


def dagger_blade(m, s=1.0):
    haft(m, -5, 3, 3)
    m.chain('spine', I, [(-0.75, 7), (-1, 15 * s), (-1.75, 22 * s), (-3.25, 28 * s), (-5, 31 * s)], [2.5, 2.5, 2, 2], 1.1, 3, 6.5, tip=True)
    m.chain('edge', I, [(1.5, 7.5), (1, 15 * s), (0.5, 21.5 * s), (-0.25, 26 * s)], [3, 2.5, 2], 0.7, 6, 8.5, tip=True)


def knife_blade(m):
    haft(m, -5, 3, 3)
    m.box('socket', I, [-2.25, 5.5, -1.75], [2.75, 7.5, 1.75], grad=(5.5, 3, 7.5, 5))
    m.box('habaki', K, [-1.75, 7.5, -1.1], [2.25, 9, 1.1], grad=(7.5, 3, 9, 5))
    m.box('blade', I, [-1.25, 7.5, -0.8], [1.75, 27, 0.8], grad=(7.5, 3, 27, 5))
    m.box('bevel', I, [1.75, 7.5, -0.55], [2.5, 27, 0.55], 6)
    m.box('edge_hi', I, [2.5, 9, -0.4], [3, 26.5, 0.4], 8)
    m.box('choil', I, [2.5, 7.5, -0.4], [3, 9, 0.4], 1)
    m.box('spine_line', K, [-1.5, 9, -0.45], [-1, 27, 0.45], grad=(9, 2, 27, 4))
    m.box('fuller', I, [-0.5, 10, -0.85], [0.25, 22, 0.85], 2)
    for k, y in enumerate((12, 16, 20)):
        m.box(f'notch_{k}', K, [-2, y, -0.6], [-1.5, y + 1, 0.6], 4)
    for k, x1 in enumerate((2.25, 1.5, 0.5, -0.25)):
        m.box(f'tip_{k}', I, [-1.25, 27 + k * 1.5, -0.75 + k * 0.05], [x1, 28.5 + k * 1.5, 0.75 - k * 0.05], 5 + k * 0.6)


def gs_blade(m, L=47, w=5):
    haft(m, -14, 1, 1)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'blade_{n}', I, (sd * 2.5, 8.5), (sd * 0.5, L), w, 1.25 if sd < 0 else 1.15, 2, 5)
        m.seg(f'column_{n}', I, (sd * 4.25, 9.5), (sd * 2.25, L - 2), 2, 1.35, 4, 7)
        m.seg(f'facet_{n}', I, (sd * 1.5, 10), (sd * 0.75, L - 7), 2, 1.3, 1, 3)
        m.seg(f'tip_{n}', I, (sd * 2.75, L - 1), (0, L + 8), 3.5, 1.15, 4, 7, tip=True)


def brick_guard(m, w=7.5):
    m.box('guard', K, [-w, 3.5, -2.6], [w, 6.5, 2.6], grad=(3.5, 3, 6.5, 5))
    m.box('guard_top', K, [-w + 2, 6.5, -2.3], [w - 2, 7.5, 2.3], 5)
    for k, x in enumerate((-w + 2, w - 2)):
        m.box(f'stud_f{k}', I, [x - 1, 4, -3], [x + 1, 6, -2.6], 6)
        m.box(f'stud_b{k}', I, [x - 1, 4, 2.6], [x + 1, 6, 3], 6)
    m.box('seam', E, [-4.5, 7.25, -1.5], [4.5, 9.25, 1.5], 3)


def flame_blade(m, n=11, amp=2.25, w0=7):
    pts = [(0, 10.5)]
    for k in range(1, n + 1):
        pts.append((math.sin(k * 0.95) * amp * (1 - k / (n + 3)), 10.5 + k * 4))
    widths = [w0 - 0.4 * k for k in range(n)]
    m.chain('blade', I, pts, widths, 1.1, 3, 7.5, tip=True)
    return pts


def kiln_guard(m, w=6):
    m.box('kiln', K, [-w, 2.5, -3], [w, 9.5, 3], grad=(2.5, 2, 9.5, 4))
    m.box('kiln_top', K, [-w + 1.5, 9.5, -2.5], [w - 1.5, 11, 2.5], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'chimney_{n}', K, [sd * (w - 1) - 1, 9.5, -1], [sd * (w - 1) + 1, 13, 1], grad=(9.5, 3, 13, 5))
    m.box('mouth_f', E, [-3, 3.5, -3.4], [3, 7.5, -3], grad=(3.5, 1, 7.5, 4))
    m.box('mouth_b', E, [-3, 3.5, 3], [3, 7.5, 3.4], grad=(3.5, 1, 7.5, 4))


# ---------------------------------------------------------------- 杖・香炉

def ember_staff():
    """熾火の杖（エピック）: 鉄の棒の先が火箸に割れ、内へ曲がる先で大きな熾（菱の二重）を挟む。火箸に焼き物の輪を二つ、火の粉を四つ"""
    m = M('ember_staff')
    haft(m, -14, 2, 32, bands=(12, 22))
    tongs(m, 6.5, 49)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'ring_{n}', K, [sd * 7 - 1.6, 40, -1.6], [sd * 7 + 1.6, 42, 1.6], 5)
    diamond(m, 'coal', E, (0, 42), 8, 2, 2, 4)
    diamond(m, 'coal_core', E, (0, 42), 4, 2.3, 4, 5)
    for k, (x, y) in enumerate(((-5, 52), (3.5, 53.5), (-0.5, 56.5), (6, 49.5))):
        diamond(m, f'spark_{k}', E, (x, y), 2, 0.6, 4, 5)
    return m


def ember_kiln_staff():
    """炉心の杖（ヒロイック）: 蜂の巣型の窯。焼き物の段を重ねた丸い窯で、前後の焚き口が開いて中の火が光り、天辺の口から炎が立つ"""
    m = M('ember_kiln_staff')
    haft(m, -14, 2, 31, bands=(13, 23))
    m.box('foot', K, [-5, 31, -4], [5, 33, 4], grad=(31, 2, 33, 4))
    for k, (y, w) in enumerate(((33, 7), (35.5, 7.5), (38, 7), (40.5, 6), (43, 4.5), (45.5, 3))):
        m.box(f'tier_{k}', K, [-w, y, -w * 0.7], [w, y + 2.5, w * 0.7], 3 + (k % 2) * 1.5)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        z0, z1 = sorted((zs * 4.9, zs * 5.4))
        m.box(f'door_{zn}', E, [-2.5, 34, z0], [2.5, 39.5, z1], grad=(34, 1, 39.5, 4))
        m.box(f'door_arch_{zn}', I, [-3.25, 39.5, z0 - 0.1], [3.25, 40.5, z1 + 0.1], 3)
    m.box('mouth', E, [-1.75, 47.5, -1.5], [1.75, 48.5, 1.5], 4)
    for k, (x, h, w) in enumerate(((-1.2, 5, 2), (0.3, 7.5, 2.4), (1.5, 4.5, 1.8))):
        m.seg(f'flame_{k}', E, (x, 48), (x * 1.4, 48 + h), w, 0.9, 3, 5, tip=True)
    return m


def ember_censer():
    """灰の香炉（エピック）: 三本足の焼き物の鉢に、段のある透かし蓋をかぶせる。蓋の窓から熾が光り、蓋のてっぺんから炎が一枚"""
    m = M('ember_censer')
    haft(m, -14, 2, 31, bands=(13, 23))
    censer_bowl(m)
    for k, (y, w) in enumerate(((40, 6.5), (42.5, 5), (45, 3.25))):
        m.box(f'lid_{k}', I, [-w, y, -w * 0.65], [w, y + 2.5, w * 0.65], 3 + k)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        for k, x in enumerate((-3.5, 0, 3.5)):
            z = zs * 4.0                                              # 窓は蓋（半幅 4.2）に少し埋める
            m.box(f'win_{zn}{k}', E, [x - 0.8, 40.5, min(z, z + zs * 0.4)], [x + 0.8, 42, max(z, z + zs * 0.4)], 4)
    m.seg('flame', E, (0, 47.5), (0.5, 54), 2.8, 1, 3, 5, tip=True)
    return m


def ember_censer_bell_staff():
    """香炉の鈴杖（ヒロイック）: 鉤の両側に二つの香炉（大小）。鉤は左右へ分かれた火蜥蜴の二股の尾"""
    m = M('ember_censer_bell_staff')
    haft(m, -14, 2, 40, bands=(14, 26))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'hook_{n}', I, [(0, 39.5), (sd * 3, 45), (sd * 7.5, 47.5), (sd * 10.5, 45.5), (sd * 11, 42.5)], [2.8, 2.6, 2.4, 2.2], 1.4, 4, 7)
        m.seg(f'tip_{n}', K, (sd * 11, 42.5), (sd * 11, 40.5), 2, 1.4, 5, 6)
    hanging_censer(m, 'l', -11, 41, 1.0)
    hanging_censer(m, 'r', 11, 41, 1.2)
    salamander_head(m, 'head', (0, 41.5), 1.0, facing=1)
    return m


# ---------------------------------------------------------------- 短剣・小刀・斧

def ember_dagger():
    """熾の短剣（エピック）: 火箸を打ち延ばした刃。刃は背（-x）の側へ反る（2026-11-18「剣の向きがおかしい」）。刃の背に火蜥蜴の背骨のとげが並ぶ。鍔は火蜥蜴の前足"""
    m = M('ember_dagger')
    dagger_blade(m, 1.1)
    m.box('guard', K, [-3, 5.5, -1.9], [3.5, 7.5, 1.9], grad=(5.5, 3, 7.5, 5))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'paw_{n}', I, [(sd * 3, 6.5), (sd * 5.5, 7), (sd * 6.5, 5)], [2, 1.6], 1, 4, 6, tip=True)
    spine = [(-1.5, 11), (-2, 15.5), (-2.4, 20), (-3.2, 24.5), (-4.4, 28.5)]
    for k, (x, y) in enumerate(spine):
        m.seg(f'spike_{k}', K, (x, y), (x - 2.2 + k * 0.2, y + 1.5), 1.6 - k * 0.1, 1, 4, 6, tip=True)
    return lift(m, 1)


def ember_ash_knife():
    """灰の小刀（ヒロイック）: 細く長い真っ直ぐな片刃（2026-11-19 にユーザーがラフ B を選んだ柳刃。刃は変えない）。
    鍔に大きな焼き物の翼（左右非対称）、柄頭から灰の布が長く垂れる"""
    m = M('ember_ash_knife')
    knife_blade(m)
    m.chain('wing_l', K, [(-2.25, 6.5), (-6, 7.5), (-8.5, 10.5)], [2.6, 2], 1.2, 4, 6, tip=True)
    m.chain('wing_r', K, [(2.75, 6.5), (5.5, 5.5), (7, 3)], [2.4, 1.8], 1.2, 4, 6, tip=True)
    m.chain('cloth', I, [(1, -9), (2.5, -13), (1.5, -17), (3, -21)], [2.4, 2.2, 1.8], 0.5, 7, 5, tip=True)
    return lift(m, 1)


def ember_war_axe():
    """灰の戦斧（エピック）: 鉄の頬から +x に三日月の刃、刃先は焼けたまま橙に光り、縁が炎の形に三つの歯で波打つ（歯の先が光る）。背は焼き物の重り"""
    m = M('ember_war_axe')
    haft(m, -14, 2, 33, bands=(11,))
    axe_head(m, 7.5, 10.25)
    for k, a in enumerate((-40, 0, 40)):
        ra = math.radians(a)
        c = (3.5 + 10 * math.cos(ra), 27 + 10 * math.sin(ra))
        m.seg(f'tongue_{k}', E, c, (c[0] + 3 * math.cos(ra + 0.5), c[1] + 3 * math.sin(ra + 0.5)), 2.2, 0.75, 3, 5, tip=True)
    m.seg('weight', K, (-1.75, 27), (-6, 27), 6, 2, 3, 5)
    return m


# ---------------------------------------------------------------- 大剣

def ember_greatsword():
    """熾火の大剣（エピック）: 玄武岩の厚い刃（左右の刃は真ん中で重ねる。2026-11-18「刃に間があるのおかしくない？」）。
    刃の柱の間を熾の割れ目が走る（左右一本ずつ、折れながら刃先へ）。鍔は鋲を打った焼き物の煉瓦で、刃元の継ぎ目に熾火"""
    m = M('ember_greatsword')
    gs_blade(m, 49)
    brick_guard(m)
    for sd, n in ((-1, 'l'), (1, 'r')):
        pts = [(sd * 3.2, 11), (sd * 2.6, 18), (sd * 3.2, 25), (sd * 2.3, 32), (sd * 2.6, 38)]
        m.chain(f'crack_{n}', E, pts, 0.7, 1.45, 4, 3, tip=True)
    return lift(m, 6.5)


def ember_cinder_blade():
    """熾の大刃（ヒロイック）: 焔のように左右へ揺れる大刃、鍔は炉の口（奥の熾が光る）と煙突二本。焔の刃の両脇から炎の舌が段に立ち上がる（左右交互に四枚）。舌の内側が光る"""
    m = M('ember_cinder_blade')
    haft(m, -14, 1, 1)
    kiln_guard(m)
    pts = flame_blade(m)
    for k, i in enumerate((2, 4, 6, 8)):
        sd = 1 if k % 2 == 0 else -1
        a = pts[i]
        w = 7 - 0.4 * i
        base = (a[0] + sd * w * 0.4, a[1])
        tip = (a[0] + sd * (w * 0.5 + 3.5), a[1] + 5)
        m.seg(f'tongue_{k}', I, base, tip, 2.6, 1, 5, 7, tip=True)
        m.seg(f'tongue_glow_{k}', E, base, ((base[0] + tip[0]) / 2, (base[1] + tip[1]) / 2), 1, 1.1, 3, 4)
    return lift(m, 6.5)


# ---------------------------------------------------------------- 弓（握りの中心 (12, 15) だけを残し、形は火で立て直す）

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


def bow_grip(m):
    """火の弓の握り: 革巻きの鉄の握り、両端に鋲を打った焼き物の帯"""
    m.box('grip', I, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 1, 20, 3), wrap=(10, 20))
    for n, y in (('u', 19.5), ('d', 8.5)):
        m.box(f'band_{n}', K, [10, y, -2.25], [14, y + 2, 2.25], 4)
        m.box(f'band_{n}_rivet', I, [11, y, -2.6], [13, y + 2, 2.6], 6)


def ember_bow():
    """灰の弓（エピック）: 弓腕そのものが一匹の火蜥蜴。握りの尾から胴が太り、弓の先で頭（光る目と顎）になって弦をくわえる。
    背には焼き物の脚が二対、腹（弦の側）に熾火の筋が通る。弦には熾火の玉が二つ"""
    m = M('ember_bow')
    bow_grip(m)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (5.5, 44.25)]
    top, bot = (1.5, 46.5), (1.5, -16.5)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'body_{n}', I, [P(q) for q in arm], [3.2, 4.2, 4.8, 4.4, 3.8], 1.8, 2, 5, tip=False)
        m.chain(f'belly_{n}_glow', E, [P(q) for q in lib.offset_line(arm, -1.9)[1:5]], [0.8, 0.9, 0.8], 1.6, 3, 5, tip=False)
        for k, c in enumerate(((13.6, 29.5), (12.9, 34.5), (11, 39))):
            q = P(c)
            m.box(f'spot_{n}{k}', K, [q[0] - 0.9, q[1] - 0.9, -1.95], [q[0] + 0.9, q[1] + 0.9, 1.95], 4)
        # 頭: 首から太る頭、上顎と下顎、目は左右の面で光る
        m.seg(f'neck_{n}', I, P((7.5, 42.5)), P((5.5, 44.75)), 3.6, 1.9, 4, 5)
        m.seg(f'head_{n}', I, P((6, 44.5)), P((2.5, 46.25)), 4.6, 2.3, 5, 6)
        m.seg(f'jaw_up_{n}', I, P((3, 46.75)), P((0, 47.75)), 2.0, 1.8, 6, 7, tip=True)
        m.seg(f'jaw_lo_{n}', K, P((3, 45.25)), P((0.25, 45.25)), 1.6, 1.6, 4, 5, tip=True)
        h = P((4.25, 46))
        for zs, zn in ((1, 'f'), (-1, 'b')):
            z0, z1 = sorted((zs * 2.3, zs * 2.5))
            m.box(f'eye_{n}{zn}_glow', E, [h[0] - 0.7, h[1] - 0.5, z0], [h[0] + 0.7, h[1] + 0.5, z1], 5)
        for k, (a, b) in enumerate((((14.5, 28.5), (18.5, 26.5)), ((12.5, 37), (16.5, 37.5)))):
            m.seg(f'leg_{n}{k}', K, P(a), P(b), 2.2, 1.4, 3, 5)
            m.seg(f'foot_{n}{k}', K, P(b), P((b[0] + 0.5, b[1] + 1.5)), 1.4, 1.4, 5, 6, tip=True)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((38, -8)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_bead{k}', K, [q[0] - 1, q[1] - 1.2, -1], [q[0] + 1, q[1] + 1.2, 1], 4)
            m.box(f'string_{key}_bead{k}_glow', E, [q[0] - 0.5, q[1] - 0.5, -1.15], [q[0] + 0.5, q[1] + 0.5, 1.15], 5)
    return m


def brazier_fire(m, name, base, at, plane, h=11.0, seed=0):
    """火鉢の炎（板の絵）: 舌が揺れて立ち昇る。芯は白に近く、外へ橙、縁は赤。炎の上に火の粉がちぎれる"""
    bx, by = base
    cv = lib.Canvas(bx - 7, by, bx + 7, by + h + 4)

    def f(x, y, cur):
        u, v = x - bx, (y - by) / h
        if v < 0:
            return None
        if v > 1:
            k = int(x * 2) * 5 + int(y * 2) * 3 + seed
            return (E, 4) if k % 23 == 0 and abs(u) < 2.5 else None
        sway = 1.4 * math.sin(v * 5 + seed) * v
        w = 4.6 * (1 - v) ** 0.75 * (0.75 + 0.25 * math.cos((u - sway) * 2.2 + seed))
        d = abs(u - sway)
        if d > w:
            return None
        r = d / max(w, 0.01)
        if r < 0.3 and v < 0.7:
            return (E, 5)
        if r < 0.6:
            return (E, 4)
        return (E, 3) if r < 0.85 else (E, 1)
    cv.paint(f)
    cv.put(m, name, at=at, plane=plane)


def ember_flare_bow():
    """火の粉の弓（伝説）: 弓腕そのものが炎。焦げた細い芯から炎の舌が左右へ交互に揺れて先へ燃え上がり、弓の先で一本の細い火になる。
    握りの前には焼き物の火鉢が抱かれ、中で大きな炎（板の絵を前と横に十字に立てる）が燃え上がる。まわりには火の粉が舞う。弓の先は焦げた芯が鉤に曲がって弦を受け、受け口の結び玉から細い火が立つ。弦は焼き物の結び玉で三か所留め、結び玉が熾火で光る"""
    m = M('ember_flare_bow')
    m.box('grip', I, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', K, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    # 火鉢と炎
    m.box('brazier', K, [13.75, 11.5, -2.25], [19.25, 15.25, 2.25], grad=(11.5, 1, 15.25, 4))
    m.box('brazier_rim', K, [13.25, 15.25, -2.75], [19.75, 16.25, 2.75], 5)
    m.box('brazier_foot', K, [15, 10.5, -1.5], [18, 11.5, 1.5], 2)
    brazier_fire(m, 'flame_glow', (17.5, 15.5), 0, 'xy', h=22)
    brazier_fire(m, 'flame_z_glow', (0, 15.5), 16.5, 'zy', h=14, seed=2)
    arm = [(12, 21), (12, 27), (11, 33), (9, 38.5), (6.5, 43), (3.5, 46)]
    nr = lib.line_normals(arm)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'core_{n}', I, [P(q) for q in arm], [3, 2.75, 2.5, 2, 1.5], 1.4, 1, 3, tip=False)
        for k in range(1, len(arm)):
            side = 1 if k % 2 else -1
            a, b = arm[k - 1], arm[k]
            mid = ((a[0] + b[0]) / 2 + nr[k][0] * side * 1.2, (a[1] + b[1]) / 2 + nr[k][1] * side * 1.2)
            tipp = (b[0] + nr[k][0] * side * 3.2, b[1] + nr[k][1] * side * 3.2 + 1.5)
            w = 3.2 - k * 0.35
            m.seg(f'flame_{n}{k}_glow', E, P(mid), P(tipp), w, 1.0, 3, 5, tip=True)
            m.seg(f'flame_in_{n}{k}_glow', E, P(mid), P(((mid[0] + tipp[0]) / 2, (mid[1] + tipp[1]) / 2)), w * 0.45, 1.05, 5, 5, tip=True)
        m.seg(f'hook_{n}', I, P((3.75, 45.75)), P((2.5, 46.75)), 1.5, 1.2, 2, 1, over=0.4)
        diamond(m, f'nock_{n}', K, P((2.5, 46.5)), 1.9, 1.1, 3, 4)
        diamond(m, f'nock_{n}_glow', E, P((2.5, 46.5)), 0.9, 1.15, 4, 5)
        m.seg(f'wick_{n}_glow', E, P((2.5, 47.25)), P((2.25, 50.5)), 1.3, 0.6, 4, 5, tip=True)
    for k, (x, y, s) in enumerate(((19.5, 30, 1.2), (21.5, 26.5, 0.9), (14.5, 29.5, 0.8), (18.5, 44, 1), (13, 50, 0.9), (20.5, 6, 1), (17, 0, 0.8), (22.5, 15, 0.8))):
        diamond(m, f'spark_{k}_glow', E, (x, y), s, 0.45, 4, 5)
    top, bot = (2.5, 46.5), (2.5, -16.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((37, -7)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            diamond(m, f'string_{key}_knot{k}', K, q, 2.25, 1, 3, 5)
            diamond(m, f'string_{key}_knot{k}_glow', E, q, 1.1, 1.1, 4, 5)
    return m


ALL = [ember_staff, ember_kiln_staff, ember_censer, ember_censer_bell_staff, ember_dagger, ember_ash_knife,
       ember_war_axe, ember_greatsword, ember_cinder_blade, ember_bow, ember_flare_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
