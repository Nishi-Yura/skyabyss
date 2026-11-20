# -*- coding: utf-8 -*-
"""塔（look5・個性版）。天と淵のあいだの塔の主たち「側」の武器 6 品（docs/design/03 の塔の装備）。
色: 白の大理石と金（天）と、黒い石と紫の割れ目（淵）。つなぎ目に天秤の印の光（2026-11-20 に白黒へ統一。全品これ）。
天秤・目・落ちる月・引力の環・追う印・合わせた掌から一品ずつ形を立てる。
- 柄: 夜の紫の握りに金の輪、柄頭は黒い石の四角錐。
- 光: あいだの大剣 = 刃の真ん中の境の線、側の目の弓 = 目、落ちる月の杖 = 月、引き合いの鎚 = 環の芯、追う印の短剣 = 印、合掌の鈴杖 = 掌のあいだ。
"""
import math
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

# 塔の装備の色（書類 54 の 2 章: 半分が白の大理石と金、半分が黒い石と紫の割れ目、つなぎ目に天秤の印。2026-11-20）
# 塔の品はこの段階だけで塗る。白は -x、黒は +x（左右で割る）
lib.RAMPS.update({
    'tw_marble': ['#605c58', '#78746f', '#908c85', '#a8a49c', '#bcb8b0', '#cecac2', '#dedad2', '#eae6de', '#f4f1e9', '#fcfaf4'],
    'tw_gold': ['#483216', '#6e5024', '#967034', '#c09448', '#e2ba68', '#f6da92', '#fff2c4'],
    'tw_black': ['#100e16', '#181620', '#211f2a', '#2c2a37', '#3a3846', '#484656'],
    'tw_night': ['#120c1e', '#1e1632', '#2e224a', '#423264', '#5a4680', '#76609c'],
    'tw_crack': ['#6e3cc8', '#8654dd', '#9f70ef', '#ba94fb', '#d2b6ff', '#e8d6ff'],
    'tw_libra': ['#dca05a', '#f6c882', '#ffe8b4', '#fff8e6'],
})
WM, WG, WB, WN, WC, WL = 'tw_marble', 'tw_gold', 'tw_black', 'tw_night', 'tw_crack', 'tw_libra'


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """45 度に回した四角（菱）"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def _wM(name):
    """塔の白と黒の品の道具（素材の色は WM〜WL）。光は天秤の印の光（WL）"""
    return Model(name, accent=WG, glowmat=WL)


def _bs_bands(sp, face, w, h):
    """柄: 夜の紫の帯を三画素おきに"""
    for j in range(2, int(h) - 1, 3):
        sp.rect(0, j, w, j + 1, WN, 4, shade=False)


def _bs_white(sp, face, w, h):
    """白い半分（天）: 大理石の太い筋を二本（二画素）"""
    if face in ('south', 'north'):
        sp.line((w * 0.25, 0), (w * 0.55, h), 2.0, WM, 9, shade=False)
        sp.line((w * 0.72, h * 0.2), (w * 0.98, h * 0.9), 1.6, WM, 8, shade=False)


def _bs_black(sp, face, w, h):
    """黒い半分（淵）: 紫の太い稲妻を一本（折れ線、二画素）"""
    if face in ('south', 'north'):
        pts = [(w * 0.15, h * 0.95), (w * 0.5, h * 0.6), (w * 0.3, h * 0.42), (w * 0.8, h * 0.05)]
        for a, b in zip(pts, pts[1:]):
            sp.line(a, b, 1.8, WC, 4, shade=False)


# ---- 崩れて浮く塔（2026-11-20 夜。ユーザー「崩壊しているけど、謎の力で浮いてつながっている」。塔の 6 本の共通のモチーフ）
def _part_center(p):
    """部品の中心（z 回りの回転を origin のまわりで当てた位置）"""
    o = p['origin']
    c = [(p['frm'][i] + p['to'][i]) / 2 for i in range(3)]
    a = math.radians(p.get('rot') or 0)
    dx, dy = c[0] - o[0], c[1] - o[1]
    return (o[0] + dx * math.cos(a) - dy * math.sin(a), o[1] + dx * math.sin(a) + dy * math.cos(a))


def _xf(pt, piv, deg, dx, dy):
    a = math.radians(deg)
    x, y = pt[0] - piv[0], pt[1] - piv[1]
    return (piv[0] + x * math.cos(a) - y * math.sin(a) + dx, piv[1] + x * math.sin(a) + y * math.cos(a) + dy)


def _shatter(m, frags):
    """組んだ模型を割って浮かせる。frags は (sel, dx, dy, deg, pivot, anchors) の並び。
    sel(name, cx, cy) に当たる部品を、pivot のまわりに deg 回してから (dx, dy) ずらし、名前を float_ にする。
    anchors の各点 P から、動いた先 P' へ紫の力の糸（光る細い板）を張る。弦（string_）は動かさない"""
    import random
    rng = random.Random(m.name)
    jit = []
    for _f in frags:
        a, b = rng.uniform(0.45, 1.6), rng.uniform(0.45, 1.6)
        jit.append((a, b, rng.uniform(-9, 9), rng.uniform(-1.2, 1.2), rng.uniform(-1.0, 1.0)))
    frags = [(sel, dx * a + ex, dy * b + ey, deg * a + eg, piv, anc) for (sel, dx, dy, deg, piv, anc), (a, b, eg, ex, ey) in zip(frags, jit)]
    for p in m.parts:
        n = p['name']
        if n.startswith('string_'):
            continue
        cx, cy = _part_center(p)
        for sel, dx, dy, deg, piv, _ in frags:
            if not sel(n, cx, cy):
                continue
            o = p['origin']
            no = _xf((o[0], o[1]), piv, deg, dx, dy)
            sx, sy = no[0] - o[0], no[1] - o[1]
            p['origin'] = [round(no[0], 3), round(no[1], 3), o[2]]
            p['frm'] = [round(p['frm'][0] + sx, 3), round(p['frm'][1] + sy, 3), p['frm'][2]]
            p['to'] = [round(p['to'][0] + sx, 3), round(p['to'][1] + sy, 3), p['to'][2]]
            p['rot'] = round((p.get('rot') or 0) + deg, 2)
            if not n.startswith('float_'):
                p['name'] = 'float_' + n
            break
    k = 0
    for sel, dx, dy, deg, piv, anchors in frags:
        for P in anchors or ():
            Q = _xf(P, piv, deg, dx, dy)
            if math.dist(P, Q) < 0.4:
                continue
            _bl_bar(m, f'float_force_{k}', WC, 5, P, Q, 1.1, 0.6, glow=True)
            mx, my = (P[0] + Q[0]) / 2, (P[1] + Q[1]) / 2
            diamond(m, f'float_dust_{k}', WB if k % 2 else WM, (mx + 1.4 * (1 if k % 2 else -1), my + 0.6), 1.3, 0.6, 3, 5)
            k += 1


ROCK_SKIP = ('grip', 'pommel', 'band', 'ring', 'plinth', 'libra', 'glow', 'force', 'dust', 'spire', 'tip', 'gem', 'beam',
             'collar', 'lip', 'tongue', 'cord', 'crown', 'neck', 'link', 'eye', 'shaft', 'finger', 'core')


def _rockify(m, per=(1, 2), scale=1.0):
    """石の部品（白の大理石・黒い石）に、大きさも向きも不揃いな岩の塊を一つか二つずつ付けて、ごろごろした輪郭にする。
    塊は親の部品に半分めり込ませる（継ぎ目は見せない）。親が浮く欠片なら塊も float_。乱数は模型の名前で決まる"""
    import random
    from paintbox import skin
    rng = random.Random(m.name + '_rock')
    for p in list(m.parts):
        n = p['name']
        if p['mat'] not in (WM, WB) or any(k in n for k in ROCK_SKIP):
            continue
        L = p['to'][0] - p['frm'][0]
        W = p['to'][1] - p['frm'][1]
        D = p['to'][2] - p['frm'][2]
        if min(L, W) < 1.6:
            continue
        o = p['origin']
        a = math.radians(p.get('rot') or 0)
        ca, sa = math.cos(a), math.sin(a)
        for j in range(rng.randint(*per)):
            s0 = min(W, L) * rng.uniform(0.45, 0.8) * scale
            sx, sy = s0 * rng.uniform(0.8, 1.5), s0 * rng.uniform(0.7, 1.1)
            lx = rng.uniform(p['frm'][0] + sx * 0.3, p['to'][0] - sx * 0.3) - o[0]
            side = rng.choice((-1, 1))
            ly = (W / 2 + p['frm'][1] + p['to'][1] - 2 * o[1]) / 2 if False else ((p['frm'][1] + p['to'][1]) / 2 - o[1]) + side * (W / 2 - sy * rng.uniform(0.15, 0.4))
            cx = o[0] + lx * ca - ly * sa
            cy = o[1] + lx * sa + ly * ca
            dz = min(D / 2 + rng.uniform(0.2, 0.9), D / 2 + 1.0)
            zc = (p['frm'][2] + p['to'][2]) / 2 + rng.uniform(-0.4, 0.4)
            t = (p.get('base') or 4) + rng.choice((-1, 0, 1))
            skin(m, f'{n}_rk{j}', p['mat'], t, (cx - sx / 2, cy - sy / 2, zc - dz), (cx + sx / 2, cy + sy / 2, zc + dz),
                 _bl_course if p['mat'] == WB else _bl_white, rot=(p.get('rot') or 0) + rng.uniform(-28, 28), pivot=(cx, cy))


def _name_has(*keys):
    return lambda n: any(k in n for k in keys)


def _bl_bar(m, name, mat, t, a, b, w, d, paint=None, glow=None):
    """a から b へ伸びる回した板（幅 w、奥行き ±d。面に絵を描ける）"""
    from paintbox import skin
    L = math.dist(a, b)
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
    skin(m, name, mat, t, (cx - L / 2, cy - w / 2, -d), (cx + L / 2, cy + w / 2, d), paint, rot=ang, pivot=(cx, cy), glow=glow)


def _bl_white(sp, face, w, h):
    """白い刃の面: 大理石の筋を斜めに一本（暗い段で二画素）"""
    if face in ('south', 'north'):
        sp.line((w * 0.3, 0), (w * 0.6, h), 1.6, WM, 4, shade=False)


def _bl_course(sp, face, w, h):
    """石の面: 目地を横に一本（黒い石の段）"""
    if face in ('south', 'north') and h >= 4:
        sp.rect(0, int(h * 0.5), w, int(h * 0.5) + 1, WB, 0, shade=False)


def _bl_black(sp, face, w, h):
    """黒い刃の面: 目地を一本。正面（south）だけ紫の割れ目を斜めに一本（二画素）"""
    _bl_course(sp, face, w, h)
    if face == 'south' and w >= 4 and h >= 6:
        sp.line((w * 0.25, h * 0.1), (w * 0.7, h * 0.9), 1.6, WC, 3, shade=False)


def _bl_core(sp, face, w, h):
    """光の芯の面: 縁は濃く、中央を明るく（単色にしない）"""
    if face in ('south', 'north') and w >= 3:
        sp.rect(1, 0, w - 1, h, WC, 5, shade=False)


def _bl_blade(m, pre, mat, t0, t1, spine, sw, sd, side, edge, ew, ed, paint, edge_paint):
    """刃（回した板の節）。芯の折れ線を節でつなぎ、各節に厚みの板（plate）と、内側へ寄る縦の段（ridge）を重ねる。
    edge は外の縁の折れ線（刃の稜を太くする）。先は石の尖り（tip）で閉じる。side は刃のある側（-1 左、+1 右）"""
    n = len(spine) - 1
    for k in range(n):
        f = k / max(1, n - 1)
        t = int(round(t0 + (t1 - t0) * f))
        a, b = spine[k], spine[k + 1]
        _bl_bar(m, f'{pre}_c{k}', mat, t, a, b, sw[k], sd[k], paint=paint)
        _bl_bar(m, f'{pre}_p{k}', mat, t, a, b, sw[k] * 0.72, sd[k] + 0.8, paint=paint)
        off = -side * sw[k] * 0.22
        _bl_bar(m, f'{pre}_r{k}', mat, t, (a[0] + off, a[1]), (b[0] + off, b[1]), sw[k] * 0.3, sd[k] + 0.5, paint=paint)
    for k in range(len(edge) - 1):
        _bl_bar(m, f'{pre}_e{k}', mat, t1, edge[k], edge[k + 1], ew[k], ed[k], paint=edge_paint)
        ix = edge[k][0] - side * ew[k] * 0.9
        jx = edge[k + 1][0] - side * ew[k] * 0.9
        _bl_bar(m, f'{pre}_b{k}', mat, t1, (ix, edge[k][1]), (jx, edge[k + 1][1]), ew[k] * 0.5, ed[k] - 0.8, paint=edge_paint)


def tower_balance_sword():
    """あいだの大剣（伝説・神器。2026-11-20 清書。ラフ B「二股の刃」）: 鍔から白の刃と黒の刃が V 字に開き、
    間を紫の光の芯が渡る。刃は厚く広く、外の縁が刃の稜。
    色: 白の刃（-x）と黒の刃（+x）は左右で分ける（選ばれた案の対比をそのまま残す。色の統一の上下割りとの食い違いは報告に書く）。
    鍔・柄頭・角は黒い石。金は刃の帯三か所（左右に分けた）、先の尖塔、天秤の台（鍔の中央）だけ。
    光は天秤の菱（WL）一つと、紫の光の芯（WC）一つだけ。"""
    from paintbox import skin
    m = _wM('tower_balance_sword')
    # 柄: 夜の紫の握りに金の輪、柄頭は黒い石、首
    skin(m, 'grip', WN, 2, [-2, -7, -2], [2, 6, 2], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-6.2, -4.4), (3.6, 5.4))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.5, y0, -2.5], [2.5, y1, 2.5])
    skin(m, 'pommel', WB, 3, [-2.5, -9.5, -2.5], [2.5, -7, 2.5], paint=_bl_course)
    skin(m, 'pommel_rim', WB, 4, [-2.9, -7.6, -2.9], [2.9, -6.5, 2.9])
    m.seg('pommel_tip', WB, (0, -9.5), (0, -12), 2.5, 1.25, 2, 3, tip=True)
    skin(m, 'neck', WN, 4, [-2.2, 4.0, -2.2], [2.2, 12.4, 2.2])
    # 鍔: 黒い石の梁（幅 ±11）。両端は上へ反る角
    skin(m, 'guard', WB, 3, [-4.6, 12.0, -2.8], [4.6, 17.4, 2.8], paint=_bl_course)
    skin(m, 'guard_lip', WB, 5, [-5.0, 16.4, -3.1], [5.0, 18.0, 3.1])
    for sd, n in ((-1, 'l'), (1, 'r')):
        a, b = sorted((sd * 4.8, sd * 10.6))
        skin(m, f'guard_wing_{n}', WB, 3, [a, 12.0, -2.8], [b, 17.4, 2.8], paint=_bl_course)
        a, b = sorted((sd * 4.8, sd * 11.2))
        skin(m, f'guard_wing_lip_{n}', WB, 5, [a, 16.4, -3.1], [b, 18.0, 3.1])
    for sd, n in ((-1, 'l'), (1, 'r')):
        skin(m, f'guard_face_{n}', WB, 4, [min(sd * 4.2, sd * 8.4), 13.2, -3.4], [max(sd * 4.2, sd * 8.4), 16.2, 3.4], paint=_bl_course)
    m.chain('horn_l', WB, [(-10.0, 14.0), (-11.8, 18.0), (-11.4, 23.0)], [3.4, 2.0], [2.4, 1.6], 2, 3, tip=True)
    m.chain('horn_r', WB, [(10.0, 14.0), (11.8, 18.0), (11.4, 23.0)], [3.4, 2.0], [2.4, 1.6], 2, 3, tip=True)
    # 天秤の台（鍔の中央の金の台と、光る菱）
    skin(m, 'plinth', WG, 4, [-3.4, 13.2, -3.0], [3.4, 17.6, 3.0])
    skin(m, 'plinth_top', WG, 6, [-4.0, 17.0, -3.4], [4.0, 18.4, 3.4])
    diamond(m, 'libra_gem', WL, (0, 20.6), 4.4, 1.4, 2, 3, zc=2.8)
    # 刃: 白（左）と黒（右）が細く V に開く。芯の節は根元で幅 5.2・奥行き ±2.2、先へ細る
    base_spine = [(1.9, 16), (3.2, 38), (4.2, 57), (4.0, 72)]
    base_edge = [(4.6, 18), (5.6, 38), (6.0, 57), (5.2, 70)]
    for side, mat, t0, t1, paint, epaint, pre in (
            (-1, WM, 6, 8, _bl_white, None, 'fork_w'),
            (1, WB, 2, 3, _bl_black, None, 'fork_b')):
        spine = [(side * abs(x), y) for x, y in base_spine]
        edge = [(side * abs(x), y) for x, y in base_edge]
        _bl_blade(m, pre, mat, t0, t1, spine, [5.2, 4.4, 3.0], [2.2, 2.0, 1.5], side,
                  edge, [1.4, 1.2, 1.0], [2.4, 2.2, 1.8], paint, epaint)
        tx = side * 4.0
        m.seg(f'{pre}_tip', mat, (tx, 71), (tx + side * 0.3, 79), 2.6, 1.6, t0, t0, tip=True)
    # 刃の帯: 三か所、左右の刃にそれぞれ（光の芯のところは空ける）
    for y, hw in ((25.0, 5.4), (46.0, 6.2), (63.0, 5.6)):
        for side in (-1, 1):
            _bl_bar(m, f'band_{int(y)}_{side}', WG, 5, (side * 1.4, y), (side * hw, y), 1.6, 2.9)
    # 紫の光の芯: 根元が太く、先へ細る光の刃
    _bl_bar(m, 'core_a_glow', WC, 4, (0, 18), (0, 38), 2.2, 2.8, paint=_bl_core)
    _bl_bar(m, 'core_b_glow', WC, 5, (0, 38), (0, 57), 2.0, 2.6, paint=_bl_core)
    _bl_bar(m, 'core_c_glow', WC, 5, (0, 57), (0, 70), 1.4, 2.0, paint=_bl_core)
    # 先の金の尖塔（三節）
    m.chain('spire', WG, [(0, 67), (0, 72), (0, 76), (0, 81)], [2.4, 1.8, 1.0], [2.0, 1.5, 1.0], 4, 5, tip=True)
    # 崩れて浮く: 鍔の両翼と、二枚の刃の上の三つの欠片が割れて離れ、紫の力の糸でつながる
    def side_frag(side, y0, y1):
        return lambda n, cx, cy: (cx * side > 0.6) and y0 <= cy < y1 and (n.startswith(('fork_', 'band_')))
    frags = []
    for side in (-1, 1):
        frags.append((lambda n, cx, cy, sd=side: ('guard_wing' in n or 'guard_face' in n or 'horn' in n) and cx * sd > 0,
                      side * 2.2, 1.0, side * 12, (side * 4.6, 15.0), [(side * 4.6, 16.0)]))
        for (y0, y1), (dx, dy, dg) in zip(((31.0, 45.0), (45.0, 58.5), (58.5, 99.0)), ((1.2, 1.8, 6), (2.4, 3.8, 11), (3.4, 6.0, 16))):
            frags.append((side_frag(side, y0, y1), side * dx, dy, -side * dg, (side * 3.0, y0), [(side * 3.0, y0)]))
    frags.append((lambda n, cx, cy: n.startswith('spire'), 0.0, 3.0, 0, (0, 70), [(0, 70.5)]))
    _shatter(m, frags)
    _rockify(m)
    # 全体（握りより上）を 8 割に: ほかのシリーズの剣（天穹の剣も 8 割）と長さをそろえる
    from divine_bold import big
    return big(m, 5.0, 0.8, 0.8)


# 引き合いの鎚（伝説・神器。2026-11-20 夜の清書。ラフ C「二つの石」）: 頭の幅いっぱいの重い石塊が二つ。
# 各々九段の石積み（上が白の大理石、下が黒い石）。段ごとに外の角を 45 度に面取りし、外へ張り出す段と引っ込む段を交互に、
# 奥行きは根元で厚く先で薄く。間に太い金の環（天秤の台）が二つの石を引き合わせ、環の芯に天秤の光る菱。
# 石から環へ引かれる欠片の列を、手前と奥の二列に（float_）。根元の台と欠けた破片で重さを出す。
# 金は帯 3 か所（段の境）＋先の尖塔＋環（台）。光は天秤の菱（WL）一つと、左の石の紫の光（WC）一つ。
PH_YS = (21.0, 24.0, 27.0, 30.0, 33.0, 35.0, 38.0, 42.0, 46.0, 49.0)   # 九段の境（35 が白と黒の境）
PH_OUT = (18.0, 17.2, 18.0, 16.8, 17.6, 17.4, 18.0, 17.0, 16.0)        # 段ごとの外の端（張り出しと引っ込み）
PH_DEP = (7.0, 6.7, 6.6, 6.4, 6.3, 6.1, 6.0, 5.8, 5.5)                 # 段ごとの奥行き（根元が厚い）


def _ph_black(sp, face, w, h):
    """黒い石の段の面: 紫の割れ目を二本（斜めの線。光らない紫の線）"""
    if face in ('south', 'north') and w >= 3:
        sp.line((w * 0.15, h * 0.15), (w * 0.85, h * 0.85), 1.2, WC, 3, shade=False)
        if h >= 3:
            sp.line((w * 0.1, h * 0.85), (w * 0.6, h * 0.1), 1.0, WC, 3, shade=False)


def _ph_strike(sp, face, w, h):
    """外へ向いた打つ面（平ら）: 白の大理石の筋を一本だけ。打つ面はほかより明るく"""
    if face in ('east', 'west') and h >= 3:
        sp.line((w * 0.3, 0), (w * 0.6, h), 1.0, WM, 9, shade=False)


def tower_pull_hammer():
    """引き合いの鎚（伝説・神器）: 頭は幅 36・高さ 28 の二つの石塊（九段ずつ）。外の端が平らな打つ面。
    白（天）と黒（淵）の境は y 35。金の環（天秤の台）と光る菱が、両の石を引き合わせる。石の欠片は三つずつの太い列。"""
    from paintbox import skin
    m = _wM('tower_pull_hammer')
    # 柄: 夜の紫の握りに金の輪二つ、黒い石の柄頭（長さは今のまま。上端は環の下に届く）
    skin(m, 'grip', WN, 2, [-1.5, -9.0, -1.5], [1.5, 30.0, 1.5], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-6.6, -4.6), (2.4, 4.4))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.1, y0, -2.1], [2.1, y1, 2.1])
    skin(m, 'pommel', WB, 3, [-2.5, -11.5, -2.5], [2.5, -9.0, 2.5])
    m.seg('pommel_tip', WB, (0, -11.5), (0, -14.0), 2.5, 1.25, 2, 3, tip=True)
    # 中の太い金の環（天秤の台）と、芯の光る菱（環の穴にはまる）
    m.arc('ring', WG, (0.0, 35.0), 6.0, 0, 360, 12, 3.6, 4.4, 4, 4)
    diamond(m, 'libra_glow', WL, (0.0, 35.0), 5.6, 1.2, 4, 4, zc=2.8)
    # 石塊二つ: 九段。段ごとに外の上の角を一単位落とす（階段状の面取り）
    for sd, n in ((-1, 'l'), (1, 'r')):
        for k in range(9):
            y0, y1 = PH_YS[k], PH_YS[k + 1]
            white = y0 >= 35.0
            mat, t = (WM, 6) if white else (WB, 2)
            out, z = PH_OUT[k], PH_DEP[k]
            a, b = sorted((sd * 3.0, sd * (out - 1.0)))
            skin(m, f'stone_{n}{k}', mat, t, [a, y0, -z], [b, y1, z],
                 paint=_bs_white if white else _ph_black)
            a, b = sorted((sd * 3.0, sd * out))
            skin(m, f'stone_low_{n}{k}', mat, t, [a, y0, -z + 0.3], [b, y1 - 1.0, z - 0.3],
                 paint=_bs_white if white else _ph_black)
        # 根元の台（石より少し広い黒い台）
        a, b = sorted((sd * 4.0, sd * 17.2))
        skin(m, f'foot_{n}', WB, 2, [a, 19.0, -6.0], [b, 21.0, 6.0], paint=_ph_strike)
        # 欠けた破片（頭の上の角と、根元の下に一つずつ）
        a, b = sorted((sd * 13.0, sd * 15.8))
        skin(m, f'chip_top_{n}', WM, 7, [a, 49.0, -2.6], [b, 50.8, 2.6], paint=_bs_white)
        a, b = sorted((sd * 13.4, sd * 16.4))
        skin(m, f'chip_low_{n}', WB, 2, [a, 17.4, -2.8], [b, 19.0, 2.8])
        # 金の帯（段の境に三か所。両の石をまたぐ）
        for y, zb in ((27.0, 6.9), (35.0, 6.3), (42.0, 5.9)):
            a, b = sorted((sd * 3.0, sd * 18.0))
            skin(m, f'band_{n}{int(y)}', WG, 5, [a, y - 0.5, -zb], [b, y + 0.5, zb])
    # 頭の上の金の尖塔（二つの石のあいだから立つ）
    m.chain('spire', WG, [(0, 41.0), (0, 47.0), (0, 52.5), (0, 57.0)], [3.2, 2.4, 1.5], [2.6, 2.0, 1.2], 4, 6, tip=True)
    # 紫の光（一つ）: 左の石の正面、段 2 の黒い面
    skin(m, 'crack_glow', WC, 6, [-15.6, 28.0, 6.0], [-12.2, 29.2, 6.9], glow=True)
    # 石から環へ引かれる欠片の列（手前一列、三つずつ。環へ近いほど小さい）。浮く部品
    pulls = ((15.0, 29.5, 3.0), (11.6, 32.4, 2.6), (8.8, 34.6, 2.2))
    for sd, n in ((-1, 'l'), (1, 'r')):
        for j, (x, y, s) in enumerate(pulls):
            mat, t = (WM, 6) if y > 35.0 else (WB, 2)
            a, b = sorted((sd * x - s / 2, sd * x + s / 2))
            skin(m, f'float_pull_{n}{j}', mat, t, [a, y - s / 2, 9.8 - s / 2], [b, y + s / 2, 9.8 + s / 2])
    # 崩れて浮く: 両の石塊が三段ずつの塊に割れ、外へずれて傾き、紫の力の糸で環へ引かれる
    frags = []
    for sd in (-1, 1):
        for (y0, y1), (dx, dy, dg) in zip(((16.0, 30.0), (30.0, 38.0), (38.0, 60.0)), ((3.0, -3.2, -10), (4.8, 0.0, 6), (3.8, 3.6, 14))):
            frags.append((lambda n, cx, cy, s0=sd, a=y0, b=y1: n.startswith(('stone', 'foot', 'chip', 'band', 'crack')) and cx * s0 > 0 and a <= cy < b,
                          sd * dx, dy, sd * dg, (sd * 4.0, (y0 + y1) / 2), [(sd * 6.0, (max(y0, 21.0) + min(y1, 49.0)) / 2)]))
    _shatter(m, frags)
    _rockify(m)
    # 頭（y 17 より上）を横 0.62・縦 0.72・奥 0.7 に: ほかのシリーズの鎚と頭の大きさをそろえる
    from divine_bold import big
    return big(m, 17.0, 0.7, 0.78, 0.75)


def _ms_black(sp, face, w, h):
    """黒い石（淵）: 紫の割れ目を一本（二画素）"""
    if face in ('south', 'north'):
        sp.line((w * 0.2, h * 0.9), (w * 0.6, h * 0.35), 1.8, WC, 4, shade=False)
        sp.line((w * 0.6, h * 0.35), (w * 0.85, h * 0.05), 1.8, WC, 4, shade=False)


def _mst_pt(c, r, a):
    return (c[0] + r * math.cos(math.radians(a)), c[1] + r * math.sin(math.radians(a)))


def _mst_grip(m):
    """柄と柄頭（夜の紫の帯、金の輪二つは数えない。柄頭は黒い石）"""
    from paintbox import skin
    skin(m, 'grip', WN, 2, [-1.6, -14, -1.6], [1.6, 1.5, 1.6], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-12.6, -10.6), (-0.8, 1.2))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.3, y0, -2.3], [2.3, y1, 2.3])
    skin(m, 'pommel', WB, 3, [-2.6, -16.5, -2.6], [2.6, -14, 2.6])
    skin(m, 'pommel_rim', WB, 4, [-3.0, -14.8, -3.0], [3.0, -13.7, 3.0])
    m.seg('pommel_tip', WB, (0, -16.5), (0, -19), 2.5, 1.25, 2, 3, tip=True)


def _mst_shaft(m, top):
    """黒い石の柄（紫の割れ目を一本）と金の帯三本（帯の数は柄の上で三か所）"""
    from paintbox import skin
    skin(m, 'shaft', WB, 2, [-1.8, 1.5, -1.8], [1.8, top, 1.8], paint=_ms_black)
    for k, y in enumerate((7.2, 13.5, 18.5)):
        skin(m, f'band_{k}', WG, 5, [-2.6, y, -2.6], [2.6, y + 1.6, 2.6])


def _mst_moon_paint(sp, face, w, h):
    """紫の月の面: 縁は最も明るい段、芯は濃い段（表と裏とも。中に穴は開けない）"""
    if face in ('south', 'north'):
        sp.rect(0, 0, w, h, WC, 5, shade=False)
        sp.rect(1, 1, w - 1, h - 1, WC, 0, shade=False)


def _mst_board_paint(outer_up, white, vein=True):
    """三日月の板の面: 外の縁は明るい一列、内の縁は一段暗い一列、大理石（白）か紫の割れ目（黒）の筋を一本。
    outer_up は板の +y が外（弧の外側）を向くか。外の縁・内の縁の画素の行は、外向きの向きで決める"""
    def paint(sp, face, w, h):
        if face not in ('south', 'north') or h < 2:
            return
        top_t, bot_t = (9, 4) if white else (4, 1)
        top_m, bot_m = (WM, WM) if white else (WB, WB)
        hi_row, lo_row = (h - 1, 0) if outer_up else (0, h - 1)
        sp.rect(0, hi_row, w, hi_row + 1, top_m, top_t, shade=False)
        sp.rect(0, lo_row, w, lo_row + 1, bot_m, bot_t, shade=False)
        if not vein:
            return
        if white:
            sp.line((w * 0.3, 0), (w * 0.6, h), 1.0, WM, 6, shade=False)
        elif w >= 3:
            sp.line((w * 0.25, h * 0.1), (w * 0.7, h * 0.9), 1.4, WC, 3, shade=False)
    return paint


# 三日月の定数（2026-11-20 仕上げ）: 外の円の半径・中心の高さ・底の厚み・角の先の角度・弧の節数
MST_R, MST_YC, MST_T, MST_PHI, MST_N = 19.0, 52.0, 9.5, 25.0, 28


def _mst_crescent():
    """三日月の点列。外の円（中心 (0, MST_YC)、半径 MST_R）の上の点 P と、内の円（中心を上へ s ずらし、
    半径 s+R-T、角の点で外の円と交わる）の上で最も近い点 Q の組。P と Q の中点が中心線、|PQ| が厚み。
    角（P=Q）は角の先が月の上端の高さ（約 Yc+8）に来る"""
    R, Yc, K = MST_R, MST_YC, MST_R - MST_T
    x_t, y_t = R * math.cos(math.radians(MST_PHI)), R * math.sin(math.radians(MST_PHI))
    s = (R * R - K * K) / (2 * K + 2 * y_t)
    Ri = s + K
    C = (0.0, Yc + s)
    th0, th1 = 180 - MST_PHI, 360 + MST_PHI
    out = []
    for k in range(MST_N + 1):
        th = math.radians(th0 + (th1 - th0) * k / MST_N)
        P = (R * math.cos(th), Yc + R * math.sin(th))
        dx, dy = P[0] - C[0], P[1] - C[1]
        L = math.hypot(dx, dy)
        Q = (C[0] + Ri * dx / L, C[1] + Ri * dy / L)
        out.append((P, Q))
    return out, C, Ri, (th0, th1)


def _mst_bar(m, name, mat, t, a, b, w, d, paint=None, zc=0.0):
    """a から b へ伸びる回した板（幅 w、奥行き ±d、中心の奥行き zc）。両端を少し伸ばして節どうしを重ねる"""
    from paintbox import skin
    L = math.dist(a, b) + 0.8
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
    skin(m, name, mat, t, (cx - L / 2, cy - w / 2, zc - d), (cx + L / 2, cy + w / 2, zc + d), paint, rot=ang, pivot=(cx, cy))


def _mst_stone(sp, face, w, h):
    """台座の石: 目地を一本（黒い石の段）"""
    _bl_course(sp, face, w, h)


def tower_moon_staff():
    """落ちる月の杖（伝説・神器。2026-11-20 仕上げ）: 石の柄の頭に三日月。
    三日月は回した板 16 節の弧（外の円 R19、底の厚み 9.5、両の角は月の上端の高さまで反る）。
    弧の外の縁は明るく、内の縁は一段暗い。外と内の縁に一枚の薄い縁を重ね、輪郭を滑らかに見せる。
    弧の下に石の台座を 2 段（塔の言葉）。弧の中で紫の光の菱の月（一辺 11）が落ちる途中で止まり、
    上へ石の欠片 3 つ、先に太い金の尖塔。
    色（上下で割る）: 境の天秤の台（y≈40）より上は白の大理石、下は黒い石と紫の割れ目。
    金は帯三か所・先の尖塔・天秤の台だけ。光は天秤の菱（WL）一つと、紫の月（WC）一つ。
    欠片・尖塔・月は浮く部品（float_）。"""
    from paintbox import skin
    m = _wM('tower_moon_staff')
    _mst_grip(m)
    _mst_shaft(m, 37.0)
    pr, C, Ri, (th0, th1) = _mst_crescent()
    mids = [((P[0] + Q[0]) / 2, (P[1] + Q[1]) / 2) for P, Q in pr]
    th = [math.dist(P, Q) for P, Q in pr]
    for k in range(MST_N):
        M0, M1 = mids[k], mids[k + 1]
        Om = ((pr[k][0][0] + pr[k + 1][0][0]) / 2, (pr[k][0][1] + pr[k + 1][0][1]) / 2)
        Im = ((pr[k][1][0] + pr[k + 1][1][0]) / 2, (pr[k][1][1] + pr[k + 1][1][1]) / 2)
        ang = math.atan2(M1[1] - M0[1], M1[0] - M0[0])
        nx, ny = -math.sin(ang), math.cos(ang)
        outer_up = (Om[0] - Im[0]) * nx + (Om[1] - Im[1]) * ny > 0
        ymid = (M0[1] + M1[1]) / 2
        white = ymid >= 40.0
        mat, tt = (WM, 6) if white else (WB, 4)
        tav = (th[k] + th[k + 1]) / 2
        w = max(0.6, tav)
        d = 1.5 + 2.5 * min(1.0, tav / MST_T)
        _mst_bar(m, f'crest_{k}', mat, tt, M0, M1, w, d, paint=_mst_board_paint(outer_up, white, k % 5 == 2))

    # 台座（底の外に石の段を二つ。上の段は弧の底に入る）
    skin(m, 'stand_0', WB, 3, [-6.0, 22.0, -4.2], [6.0, 25.0, 4.2], paint=_mst_stone)
    skin(m, 'stand_1', WB, 4, [-4.4, 24.6, -3.4], [4.4, 29.5, 3.4], paint=_ms_black)
    skin(m, 'stand_2', WB, 5, [-3.0, 29.0, -2.6], [3.0, 35.0, 2.6])
    # 天秤の台（弧の底の前。境）と光る菱
    skin(m, 'plinth', WG, 4, [-4.6, 34.0, 3.0], [4.6, 41.0, 6.0])
    skin(m, 'plinth_top', WG, 6, [-5.2, 39.6, 2.6], [5.2, 41.2, 6.6])
    diamond(m, 'libra_gem_glow', WL, (0, 37.5), 5.0, 1.8, 4, 5, zc=6.4)
    # 落ちる途中で止まった光の菱の月（一辺 11。浮く部品）
    h = 11.0 / (2 * math.sqrt(2))
    _bl_bar(m, 'float_moon_glow', WC, 3, (-h, MST_YC - h), (h, MST_YC + h), 11.0, 3.6, paint=_mst_moon_paint)
    # 月の上の石の欠片 3 つ（白・黒・白）と、先の太い金の尖塔
    diamond(m, 'float_shard_0', WM, (-5.2, 65.0), 3.6, 2.0, 3, 4)
    diamond(m, 'float_shard_1', WB, (5.0, 66.6), 2.6, 1.6, 2, 3)
    diamond(m, 'float_shard_2', WM, (0.2, 70.0), 3.0, 1.8, 3, 4)
    m.chain('float_spire', WG, [(0.4, 70.5), (0.9, 74.5), (1.7, 78.5)], [3.2, 2.2], [2.0, 1.4], 2, 5, tip=True)
    # 崩れて浮く: 三日月が七つの欠片に割れ、底の欠片から外へ離れて傾く（紫の力の糸でつながる）
    frags = []
    for g in range(7):
        if g == 3:
            continue
        th = math.radians(th0 + (th1 - th0) * (g * 4 + 2) / MST_N)
        far = abs(g - 3)
        rr = 1.6 * far
        dg = (6 + 2 * far) * (1 if g > 3 else -1)
        ks = set(range(g * 4, g * 4 + 4))
        bk = g * 4 if g > 3 else g * 4 + 4
        tb = math.radians(th0 + (th1 - th0) * bk / MST_N)
        anchor = (MST_R * 0.75 * math.cos(tb), MST_YC + MST_R * 0.75 * math.sin(tb))
        frags.append((lambda n, cx, cy, kk=ks: n.startswith('crest_') and int(n.split('_')[1]) in kk,
                      math.cos(th) * rr, math.sin(th) * rr, dg, (MST_R * math.cos(th), MST_YC + MST_R * math.sin(th)), [anchor]))
    _shatter(m, frags)
    _rockify(m)
    return m


# 追う印の短剣（2026-11-20 清書。ラフ A「逆鉤」）: 根元から上へ反り、上で内へ返る鉤の刃。
# 刃は回した板の節で組む（段の箱で積まない）。天秤の台（y=22）が白と黒の境: 上は天の白い大理石、下は淵の黒い石。
# 鉤の内側（-x 側）が切る縁で、紫の縁。背（+x 側）に鰭が三枚。柄は夜の紫に金の輪、柄の下に逆手の黒い石の指輪。
# 金は刃の帯三か所・先の尖塔・天秤の台と柄の輪だけ。光は天秤の菱（WL）一つと、黒い側の紫の光（WC）の芯一つ。

SD_BY = 22.0   # 天秤の台の高さ（白と黒の境）
# 刃の芯の節 (x, y, 幅, 奥行き)。根元は幅 7 で、上へ細り、先で内へ返る
SD_NODES = [
    (0.0, 7.0, 7.0, 2.6), (0.8, 12.5, 7.6, 2.5), (2.0, 18.0, 7.4, 2.4), (2.53, SD_BY, 7.0, 2.3),
    (2.8, 24.0, 6.2, 2.2), (2.7, 30.0, 5.0, 2.0), (1.4, 35.5, 3.8, 1.7), (-0.8, 39.2, 3.0, 1.4),
    (-3.6, 40.6, 2.4, 1.2), (-6.2, 39.0, 2.0, 1.1), (-7.6, 35.8, 1.8, 1.0),
]


def _sd_bar(m, name, mat, t, a, b, w, d, paint=None, glow=None):
    """a から b へ伸びる回した板（幅 w、奥行き ±d。面に絵を描ける）。両端を少し伸ばして節どうしを重ねる"""
    from paintbox import skin
    L = math.dist(a, b) + 1.2
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
    skin(m, name, mat, t, (cx - L / 2, cy - w / 2, -d), (cx + L / 2, cy + w / 2, d), paint, rot=ang, pivot=(cx, cy),
         glow=glow)


def _sd_at(nodes, y, k):
    """節の列で高さ y の値（k: 0 x、2 幅、3 奥行き）を直線に読む（y は節の並びの順に当たる所）"""
    for i in range(len(nodes) - 1):
        y0, y1 = nodes[i][1], nodes[i + 1][1]
        if y0 <= y <= y1:
            s = (y - y0) / (y1 - y0)
            return nodes[i][k] + (nodes[i + 1][k] - nodes[i][k]) * s
    return nodes[-1][k]


def _sd_white(sp, face, w, h):
    """白い大理石の面（天）: 横の筋を四画素おき"""
    if face in ('south', 'north'):
        for j in range(2, int(h) - 1, 4):
            sp.rect(0, j, w, j + 1, WM, 3, shade=False)


def _sd_black(sp, face, w, h):
    """黒い石の面（淵）: 横の暗い筋と、紫の割れ目の斜めの一本（黒い側に一本だけ）"""
    if face in ('south', 'north'):
        for j in range(3, int(h) - 1, 5):
            sp.rect(0, j, w, j + 1, WB, 1, shade=False)
        sp.line((w * 0.3, h * 0.1), (w * 0.7, h * 0.9), 1.6, WC, 4, shade=False)


def _sd_glow(sp, face, w, h):
    """光の芯（黒い側の紫の光）: 縁を濃く、中央を明るく"""
    if face in ('south', 'north') and w >= 3:
        sp.rect(1, 0, w - 1, h, WC, 5, shade=False)


def _sd_blade(m, nodes):
    """刃の芯（節ごとに板を三枚: 芯・表の板・縁の稜）。色は節の中ほどの高さで白と黒を分ける"""
    pts = [(x, y) for x, y, _, _ in nodes]
    ws = [w for _, _, w, _ in nodes]
    ds = [d for _, _, _, d in nodes]
    ns = lib.line_normals(pts)
    for k in range(len(nodes) - 1):
        a, b = pts[k], pts[k + 1]
        w = (ws[k] + ws[k + 1]) / 2
        d = (ds[k] + ds[k + 1]) / 2
        black = (a[1] + b[1]) / 2 < SD_BY
        mat, t, paint = (WB, 3, _sd_black) if black else (WM, 6, _sd_white)
        _sd_bar(m, f'core_{k}', mat, t, a, b, w, d, paint=paint)
        _sd_bar(m, f'plate_{k}', mat, t, a, b, w * 0.6, d + 0.6, paint=paint)
        # 外の縁の稜（幅 2 の板を縁の内側に重ねる）: -x 側（鉤の内側）は切る縁、+x 側は背の稜
        for sgn, nm in ((-1, 'edge'), (1, 'ridge')):
            if black:
                rmat, rt = (WN, 4) if sgn < 0 else (WB, 3)
            else:
                rmat, rt = WM, 7
            ea = (a[0] + ns[k][0] * sgn * (ws[k] / 2 - 0.3), a[1] + ns[k][1] * sgn * (ws[k] / 2 - 0.3))
            eb = (b[0] + ns[k + 1][0] * sgn * (ws[k + 1] / 2 - 0.3), b[1] + ns[k + 1][1] * sgn * (ws[k + 1] / 2 - 0.3))
            _sd_bar(m, f'{nm}_{k}', rmat, rt, ea, eb, 2.0, d + 0.4)


def tower_sigil_dagger():
    """追う印の短剣（伝説・神器。2026-11-20 清書。ラフ A「逆鉤」）: 根元から上へ反り、上で内へ返る鉤。
    柄の下に逆手の黒い石の指輪。鍔はない。刃の上の半分は天の白い大理石、下の半分は淵の黒い石（境は天秤の台）。
    金は帯三か所・先の尖塔・天秤の台・柄の輪だけ。光は天秤の菱（WL）一つと、黒い側の紫の光（WC）の芯一つ。"""
    from paintbox import skin
    m = _wM('tower_sigil_dagger')
    # 柄: 夜の紫の握りに金の輪、柄頭は黒い石。柄の下に逆手の黒い石の輪（指輪）
    skin(m, 'grip', WN, 2, [-2.2, -2.0, -2.0], [2.2, 7.0, 2.0], paint=_bs_bands)
    skin(m, 'grip_ring', WG, 4, [-2.6, 0.6, -2.4], [2.6, 2.2, 2.4])
    skin(m, 'pommel', WB, 3, [-2.6, -4.2, -2.4], [2.6, -2.0, 2.4], paint=_sd_black)
    cx, cy, r, n = 0.0, -8.2, 3.4, 12
    for k in range(n):
        a0 = 2 * math.pi * k / n
        a1 = 2 * math.pi * (k + 1) / n
        pa = (cx + r * math.cos(a0), cy + r * math.sin(a0))
        pb = (cx + r * math.cos(a1), cy + r * math.sin(a1))
        _sd_bar(m, f'ring_{k}', WB, 3, pa, pb, 1.8, 1.2, paint=_sd_black)
    # 刃（回した板の節）
    _sd_blade(m, SD_NODES)
    # 背の鰭: 三枚（境より下は黒い石、上は白い大理石）
    for y in (12.5, 18.0, 27.0):
        xc = _sd_at(SD_NODES, y, 0)
        hw = _sd_at(SD_NODES, y, 2) / 2
        mat, t, paint = (WB, 3, _sd_black) if y < SD_BY else (WM, 6, _sd_white)
        _sd_bar(m, f'fin_{int(y)}', mat, t, (xc + hw - 1.2, y), (xc + hw + 2.8, y + 1.4), 1.6, 1.0, paint=paint)
    # 天秤の台（金の襟が刃を横切る）と、台の光る菱（白黒の境）
    xB = _sd_at(SD_NODES, SD_BY, 0)
    hB = _sd_at(SD_NODES, SD_BY, 2) / 2
    _sd_bar(m, 'collar', WG, 4, (xB - hB - 0.6, SD_BY), (xB + hB + 0.6, SD_BY), 2.4, 2.6)
    diamond(m, 'libra', WL, (xB, SD_BY + 0.6), 3.6, 1.0, 3, 3, zc=2.4)
    # 帯（金）三か所
    for y in (12.5, 30.0, 38.0):
        xc = _sd_at(SD_NODES, y, 0)
        hw = _sd_at(SD_NODES, y, 2) / 2
        _sd_bar(m, f'band_{int(y)}', WG, 5, (xc - hw - 0.5, y), (xc + hw + 0.5, y), 1.8, 2.0)
    # 先の尖塔（金）
    _sd_bar(m, 'tip', WG, 4, SD_NODES[9][:2], SD_NODES[10][:2], 2.0, 1.2)
    # 黒い側の紫の光の芯（一つ。小さく、表の板の前へ少し出す）
    gx = _sd_at(SD_NODES, 15.0, 0)
    _sd_bar(m, 'crack_glow', WC, 4, (gx, 13.5), (gx + 0.2, 16.5), 2.2, 3.0, paint=_sd_glow, glow=True)
    # 崩れて浮く: 刃の上の三つが割れて浮き、鉤の先ほど大きく離れる（紫の力の糸）
    def kidx(n):
        m2 = re.match(r'(?:core|plate|edge|ridge)_(\d+)$', n)
        return int(m2.group(1)) if m2 else None
    groups = (((4, 5), (24.0, 35.5), (1.4, 2.2, -8)), ((6, 7), (35.5, 41.0), (1.2, 4.6, -14)), ((8, 9), (99.0, 99.0), (-2.6, 6.4, -20)))
    frags = []
    for ks, (y0, y1), (dx, dy, dg) in groups:
        x0, yy = SD_NODES[ks[0]][0], SD_NODES[ks[0]][1]
        def sel(n, cx, cy, kk=ks, a=y0, b=y1):
            k = kidx(n)
            if k is not None:
                return k in kk
            if kk == (8, 9):
                return n == 'tip'
            return n.startswith(('fin_', 'band_')) and a <= cy < b
        frags.append((sel, dx, dy, dg, (x0, yy), [(x0, yy)]))
    _shatter(m, frags)
    _rockify(m)
    return m


def _cs_split(y):
    """掌の色: 上（先の半分）は白の大理石、下（根元の半分）は黒い石。境は天秤の台の高さ 50"""
    return WM if y >= 50 else WB


def _cs_white(sp, face, w, h):
    """白い掌（天）: 大理石の筋を斜めに一本（暗い段で二画素）"""
    if face in ('south', 'north') and w >= 3 and h >= 4:
        sp.line((w * 0.2, h * 0.15), (w * 0.8, h * 0.85), 1.2, WM, 8, shade=False)


def _cs_black(sp, face, w, h):
    """黒い掌（淵）: 紫の割れ目を斜めに一本（光らない）"""
    if face in ('south', 'north') and w >= 3 and h >= 4:
        sp.line((w * 0.25, h * 0.85), (w * 0.75, h * 0.15), 1.2, WC, 3, shade=False)


def _cs_grip(sp, face, w, h):
    """柄: 夜の紫の帯を三画素おきに"""
    for j in range(2, int(h) - 1, 3):
        sp.rect(0, j, w, j + 1, WN, 4, shade=False)


def _cs_bar(m, name, mat, t, a, b, w, d, zc=0.0, paint=None, glow=None):
    """a から b へ伸びる回した板（幅 w・奥行き ±d・前後へ zc ずらす）。面に絵を描ける"""
    from paintbox import skin
    L = math.dist(a, b) + 1.0
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
    skin(m, name, mat, t, (cx - L / 2, cy - w / 2, zc - d), (cx + L / 2, cy + w / 2, zc + d), paint, rot=ang,
         pivot=(cx, cy), glow=glow)


def _cs_palm(m, pre, pts, ws, ds):
    """掌（回した板の節）: 各節に芯の板と厚みの板（plate）を一枚。色は節の高さで白と黒を分け、筋を描く"""
    for k in range(len(pts) - 1):
        a, b = pts[k], pts[k + 1]
        white = (a[1] + b[1]) / 2 >= 50
        mat, t, paint = (WM, 6, _cs_white) if white else (WB, 3, _cs_black)
        _cs_bar(m, f'{pre}_c{k}', mat, t, a, b, ws[k], ds[k], paint=paint)
        _cs_bar(m, f'{pre}_p{k}', mat, t, a, b, ws[k] * 0.6, ds[k] + 0.9, paint=paint)


def _cs_bell(m, pre, cx, top, s):
    """鈴（大きく・裾が広がる形）: 冠（夜の紫）、胴（黒い石。首から裾へ外へ広がる二本の板と中の芯）、白い口の縁、紫の舌。
    top は冠の上の面。s は大きさの倍率"""
    from paintbox import skin
    skin(m, f'{pre}_crown', WN, 5, [cx - 0.9 * s, top - 1.4 * s, -0.9 * s], [cx + 0.9 * s, top, 0.9 * s])
    skin(m, f'{pre}_neck', WB, 4, [cx - 1.3 * s, top - 3.2 * s, -1.2 * s], [cx + 1.3 * s, top - 1.4 * s, 1.2 * s])
    skin(m, f'{pre}_core', WB, 5, [cx - 2.2 * s, top - 9.4 * s, -1.8 * s], [cx + 2.2 * s, top - 3.0 * s, 1.8 * s], paint=_cs_black)
    for sg, n in ((-1, 'l'), (1, 'r')):
        pts = [(cx + sg * 1.6 * s, top - 2.6 * s), (cx + sg * 3.0 * s, top - 6.4 * s), (cx + sg * 3.8 * s, top - 10.0 * s)]
        for k in range(2):
            _cs_bar(m, f'{pre}_flare_{n}{k}', WB, 4, pts[k], pts[k + 1], [2.0 * s, 2.4 * s][k], 2.0 * s)
    skin(m, f'{pre}_lip', WM, 8, [cx - 4.1 * s, top - 11.0 * s, -2.9 * s], [cx + 4.1 * s, top - 9.4 * s, 2.9 * s])
    skin(m, f'{pre}_tongue', WN, 4, [cx - 0.7 * s, top - 12.0 * s, -0.7 * s], [cx + 0.7 * s, top - 9.0 * s, 0.7 * s])


def tower_clap_bell_staff():
    """合掌の鈴杖（伝説・神器。2026-11-20 清書。ラフ A「掌の杯」）: 柄の先で合わせた二つの石の掌が杯になって開く。
    指は回した板で三本ずつ、先が外へ開く。掌の外の関節（金の帯）から大きな鈴が二つこぼれて吊る。
    杯の中を金の天秤の台が渡り、台の下に光る菱。
    色: 指と掌の上半分は白の大理石、掌の下半分と鈴は黒い石、境は天秤の台。柄は夜の紫に金の輪二つ。
    金は帯三か所（手首と両の関節）と天秤の台だけ。光は天秤の菱（WL）一つと、黒い側の掌の外の紫の光（WC）一つ。"""
    from paintbox import skin
    m = _wM('tower_clap_bell_staff')
    # 石突き（黒い石）・柄（夜の紫に金の輪）・手首の金の帯
    skin(m, 'pommel', WB, 3, [-2.4, -11.0, -2.4], [2.4, -8.0, 2.4])
    m.seg('pommel_tip', WB, (0, -11.0), (0, -13.4), 2.4, 1.2, 2, 3, tip=True)
    skin(m, 'grip', WN, 3, [-1.9, -8.0, -1.9], [1.9, 4.0, 1.9], paint=_cs_grip)
    for k, (y0, y1) in enumerate(((-6.0, -4.4), (0.6, 2.2))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.4, y0, -2.4], [2.4, y1, 2.4])
    skin(m, 'rod', WN, 3, [-1.2, 3.0, -1.2], [1.2, 29.5, 1.2])
    skin(m, 'wrist', WG, 5, [-3.4, 28.4, -3.0], [3.4, 32.6, 3.0])
    # 掌（二つ）: 根元で合わさり、外へ張って先で開く。指は三本ずつ、回した板の先が細る
    for sg, n in ((-1, 'l'), (1, 'r')):
        pts = [(sg * 2.0, 31.5), (sg * 8.2, 37.5), (sg * 11.6, 46.0), (sg * 11.2, 55.0), (sg * 8.8, 63.5), (sg * 7.4, 71.0)]
        _cs_palm(m, f'palm_{n}', [(x, y) for x, y in pts], [6.4, 7.0, 7.4, 6.8, 5.6], [3.2, 3.6, 3.8, 3.6, 3.0])
        for k, (bx, by, tx, ty) in enumerate(((12.8, 55.5, 13.6, 67.0), (10.0, 57.0, 10.4, 70.0), (7.4, 57.5, 7.0, 72.4))):
            m.seg(f'finger_{n}{k}', WM, (sg * bx, by), (sg * tx, ty), 2.8, 1.9, 5, 6, over=0.3, zc=2.4, tip=True)
        # 掌の外の関節の金の帯（手の外へ張り出す）と、そこから吊る鈴
        lo, hi = sorted((sg * 7.2, sg * 15.8))
        skin(m, f'band_{n}', WG, 5, [lo, 45.0, -4.2], [hi, 47.0, 4.2])
        m.seg(f'cord_{n}', WN, (sg * 15.4, 45.2), (sg * 16.2, 39.6), 2.0, 1.2, 5, 4, over=0.2)
        _cs_bell(m, f'bell_{n}', sg * 16.3, 39.6, 1.0)
        # 掌の外の割れ目の光（黒い側・外の縁に一つ）
        lo, hi = sorted((sg * 12.4, sg * 14.3))
        skin(m, f'crack_glow_{n}', WC, 5, [lo, 39.2, -1.2], [hi, 41.6, 1.2], glow=True)
    # 杯の中の天秤の台（金の梁）と、台の下の光る菱
    skin(m, 'beam', WG, 5, [-9.6, 48.7, -2.2], [9.6, 51.3, 2.2])
    diamond(m, 'libra_gem', WL, (0, 47.6), 5.0, 1.6, 4, 5, zc=2.6)
    # 崩れて浮く: 掌が三つに割れて外へ離れ、指先ほど大きく離れる。鈴は関節の欠片と一緒に浮く（紫の力の糸）
    frags = []
    for sg, n in ((-1, 'l'), (1, 'r')):
        def palm(kk, n=n):
            return lambda nm, cx, cy: nm.startswith((f'palm_{n}_c', f'palm_{n}_p')) and int(nm[-1]) in kk
        frags.append((lambda nm, cx, cy, n=n, pk=palm((2,)): pk(nm, cx, cy) or nm.startswith((f'band_{n}', f'cord_{n}', f'bell_{n}', f'crack_glow_{n}')),
                      sg * 2.6, 1.4, -sg * 7, (sg * 11.4, 46.0), [(sg * 10.0, 46.0)]))
        frags.append((palm((3, 4)), sg * 4.4, 3.8, -sg * 12, (sg * 11.0, 55.0), [(sg * 10.6, 55.0)]))
        frags.append((lambda nm, cx, cy, n=n: nm.startswith(f'finger_{n}'), sg * 5.6, 6.6, -sg * 16, (sg * 9.0, 57.0), [(sg * 9.0, 63.0)]))
    frags.append((lambda nm, cx, cy: nm == 'beam' or nm.startswith('libra_gem'), 0.0, 1.2, 0, (0, 50), [(0, 47.0)]))
    _shatter(m, frags)
    _rockify(m)
    return m



def mirror(p, sign):
    return (p[0], 15 + sign * (p[1] - 15))


def strings(m, top, bot):
    for key, pull in (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)):
        if pull == 0:
            m.box(f'string_{key}', WM, [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', WM, mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', WM, bot, mid, 1, 0.5, 3, 3, over=0.2)


# ---------------------------------------------------------------- 側の目の弓（神器。清書 2026-11-20 夜。ラフ B「浮く塔の弓」）
EB_UP = [(12.5, 19.5), (15.5, 23.5), (17.0, 29.0), (15.5, 35.0), (11.5, 40.5), (6.5, 45.0), (3.5, 47.5)]   # 上の腕の芯（握りから先へ）
EB_W = (6.6, 6.2, 5.8, 5.0, 4.2, 3.4)                                                         # 段ごとの幅（根元で太く、先へ細る）
EB_S = ((0.0, 0.84), (0.14, 0.86), (0.14, 0.86), (0.14, 0.86), (0.14, 0.86), (0.14, 0.9))    # 段ごとの使う範囲（段のあいだは空く）


def _eb_lerp(a, b, s):
    return (a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s)


def _eb_bar(m, name, mat, t, a, b, w, d, paint=None, glow=None):
    """a から b へ伸びる回した板（幅 w、奥行き ±d。面に絵を描ける）。両端を少し伸ばして節どうしを重ねる"""
    from paintbox import skin
    L = math.dist(a, b) + 0.8
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
    skin(m, name, mat, t, (cx - L / 2, cy - w / 2, -d), (cx + L / 2, cy + w / 2, d), paint, rot=ang, pivot=(cx, cy), glow=glow)


def _eb_marble(sp, face, w, h):
    """白い石の段（天）: 暗い大理石の筋を一本（二画素の斜め）"""
    if face in ('south', 'north'):
        sp.line((w * 0.2, h * 0.05), (w * 0.6, h * 0.95), 1.8, WM, 4, shade=False)


def _eb_dark(sp, face, w, h):
    """黒い石の段（淵）: 紫の割れ目を折れ線で一本（光らない）"""
    if face in ('south', 'north') and h >= 4:
        sp.line((w * 0.25, h * 0.9), (w * 0.7, h * 0.5), 1.6, WC, 4, shade=False)
        sp.line((w * 0.7, h * 0.5), (w * 0.45, h * 0.1), 1.6, WC, 4, shade=False)


def _eb_gold(sp, face, w, h):
    """金の帯・台の面: 中ほどに細い明るい金の筋"""
    sp.rect(0, int(h * 0.5), w, int(h * 0.5) + 1, WG, 5, shade=False)


def _eb_grip(sp, face, w, h):
    """柄: 夜の紫の帯を三画素おきに"""
    for j in range(2, int(h) - 1, 3):
        sp.rect(0, j, w, j + 1, WN, 4, shade=False)


def _eb_piece(m, pre, mat, t, a, b, w, paint):
    """塔の破片一つ（a から b へ）: 石の板の本体、外（+x、弦の反対）へ一段張る段、先の端に面取りの角。
    本体の奥行きは ±3 で、ほかの 4 本の刃と同じ厚み"""
    from paintbox import skin  # noqa: F401
    L = math.dist(a, b)
    ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
    nx, ny = -uy, ux
    if nx < 0:
        nx, ny = -nx, -ny
    _eb_bar(m, pre, mat, t, a, b, w, 3.0, paint=paint)
    o = w * 0.4
    _eb_bar(m, pre + '_step', mat, t + 1, (a[0] + nx * o, a[1] + ny * o), (b[0] + nx * o, b[1] + ny * o), w * 0.5, 3.6, paint=paint)
    cap = 0.6
    _eb_bar(m, pre + '_cap', mat, t + 2, b, (b[0] + ux * cap, b[1] + uy * cap), w * 0.7, 2.6, paint=paint)


def _eb_pull_links(m, top, bot):
    """引きの 3 段の弦の飾り（string_<段>_link<k>）。弦と一緒に動く。黒い石"""
    for key, pull in lib.PULLS:
        for k, y in enumerate((44, 40.5, 37, 33.5, 30, 26.5, 3.5, 0, -3.5, -7, -10.5, -14)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            if k % 2:
                m.box(f'string_{key}_link{k}', WB, [q[0] - 0.75, q[1] - 0.9, -0.3], [q[0] + 0.75, q[1] + 0.9, 0.3], 4)
            else:
                m.box(f'string_{key}_link{k}', WB, [q[0] - 0.3, q[1] - 0.9, -0.75], [q[0] + 0.3, q[1] + 0.9, 0.75], 3)


def tower_eye_bow():
    """側の目の弓（神器。2026-11-20 夜の清書。ラフ B「浮く塔の弓」）: 弓腕は塔の破片の列で、段ごとに空中に浮く。
    握りに付くのは根元の一片だけ（arm_u0 / arm_d0）。ほかの破片は float_ で、弦の先の尖塔だけが弦を受ける。
    握りの前に天秤の光の瞳が浮き、その後ろに黒い枠。握りの金の台が白と黒の境（天秤の印）。
    色: 上の腕は白の大理石（天）、下の腕は黒い石（淵）。金は帯三か所（上に二、下に一）・先の尖塔・天秤の台。
    光は天秤の瞳（WL）一つと、黒い腕の前の割れ目の紫の光（WC）一つだけ。"""
    from paintbox import skin
    m = _wM('tower_eye_bow')
    # 握り（夜の紫、金の輪二つは数えない）と、白黒の境の天秤の台（金）
    skin(m, 'grip', WN, 3, [10.5, 9.5, -2], [13.5, 20.5, 2], paint=_eb_grip)
    for k, (y0, y1) in enumerate(((9.4, 10.6), (19.4, 20.6))):
        skin(m, f'grip_ring_{k}', WG, 4, [10.2, y0, -2.3], [13.8, y1, 2.3])
    skin(m, 'plinth', WG, 5, [9.4, 13.0, -2.8], [14.6, 17.0, 2.8], paint=_eb_gold)
    # 握りの前に浮く瞳: 黒い枠（後ろ）と、天秤の光る菱（前）。どちらも握りから離れて浮く
    diamond(m, 'float_eye_frame', WB, (12.0, 15), 6.8, 1.4, 3, 4, zc=2.2)
    diamond(m, 'float_eye_glow', WL, (12.0, 15), 4.6, 2.6, 5, 5, zc=3.6)
    # 上（白）と下（黒）の腕: 塔の破片の列
    for sg, pre, mat, t, paint in ((1, 'u', WM, 7, _eb_marble), (-1, 'd', WB, 3, _eb_dark)):
        P = [mirror(p, sg) for p in EB_UP]
        for k in range(6):
            a, b = P[k], P[k + 1]
            A, B = _eb_lerp(a, b, EB_S[k][0]), _eb_lerp(a, b, EB_S[k][1])
            name = f'arm_{pre}0' if k == 0 else f'float_{pre}{k}'
            _eb_piece(m, name, mat, t, A, B, EB_W[k], paint)
        # 金の帯（破片の上に一本ずつ。上の腕は二つ、下の腕は一つ）
        for k in ((1, 3) if sg > 0 else (2,)):
            a, b = P[k], P[k + 1]
            A, B = _eb_lerp(a, b, EB_S[k][0]), _eb_lerp(a, b, EB_S[k][1])
            c = _eb_lerp(A, B, 0.5)
            L = math.dist(A, B)
            u = ((B[0] - A[0]) / L, (B[1] - A[1]) / L)
            _eb_bar(m, f'float_band_{pre}{k}', WG, 5, (c[0] - u[0] * 0.6, c[1] - u[1] * 0.6), (c[0] + u[0] * 0.6, c[1] + u[1] * 0.6),
                    EB_W[k] + 1.0, 3.4, paint=_eb_gold)
        # 先の尖塔（金。菱は光らせない）
        pts = [mirror(q, sg) for q in ((4.6, 44.0), (3.5, 47.5), (4.3, 52.0))]
        m.chain(f'float_spire_{pre}', WG, pts, [2.25, 1.5], 1.1, 3, 5, tip=True)
        diamond(m, f'float_spire_gem_{pre}', WG, mirror((3.6, 47.6), sg), 1.4, 1.2, 4, 5)
    # 黒い腕（下）の前の割れ目の光（一つだけ。二つ目の破片の前の面）
    P = [mirror(p, -1) for p in EB_UP]
    A, B = _eb_lerp(P[2], P[3], EB_S[2][0]), _eb_lerp(P[2], P[3], EB_S[2][1])
    gx, gy = _eb_lerp(A, B, 0.5)
    skin(m, 'float_crack_glow', WC, 6, [gx - 0.7, gy - 0.7, 3.0], [gx + 0.7, gy + 0.7, 3.8], glow=True)
    # もっと崩す: 破片ごとに外へずれて傾き、割れ目に紫の力の糸（弦の先の尖塔と弦は動かさない）
    frags = []
    for sg, pre in ((1, 'u'), (-1, 'd')):
        P = [mirror(p, sg) for p in EB_UP]
        for k, (dx, da, dg) in zip(range(1, 6), ((1.8, 1.0, 10), (3.0, 1.8, -9), (3.4, 2.4, 13), (2.6, 2.4, -11), (1.0, 0.8, 7))):
            a, b = P[k], P[k + 1]
            L = math.dist(a, b)
            ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            c = _eb_lerp(a, b, 0.5)
            frags.append((lambda n, cx, cy, pr=pre, kk=k: n.startswith((f'float_{pr}{kk}', f'float_band_{pr}{kk}')),
                          dx + ux * da, uy * da, sg * dg, c, [_eb_lerp(a, b, EB_S[k][0])]))
    _shatter(m, frags)
    _rockify(m)
    top, bot = (3.5, 47.5), (3.5, -17.5)
    strings(m, top, bot)
    _eb_pull_links(m, top, bot)
    return m



ALL = [tower_balance_sword, tower_pull_hammer, tower_moon_staff, tower_sigil_dagger, tower_clap_bell_staff, tower_eye_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
