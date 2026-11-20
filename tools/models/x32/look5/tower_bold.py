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




# ==== 崩れた塔から考え直した 5 本（2026-11-20 夜。ユーザー「このモチーフを元に考え直して」）====
# 武器そのものが「崩れた塔の一部」: 石積みの段・胸壁（凹凸の壁）・尖塔・アーチ。割れた石は謎の力（紫の糸）で浮いてつながる。
# 先（天）が白の大理石、根元（淵）が黒い石、境に天秤の印（金の台と光る菱）。乱数は模型ごとに決まる（不規則に崩す）。

def _rng(m, tag=''):
    import random
    return random.Random(m.name + tag)


def _stone(m, name, mat, t, cx, cy, w, h, d, rot=0.0, paint=True):
    """中心 (cx, cy)、幅 w・高さ h・奥行き ±d の石の塊（z 回りに rot 度）"""
    from paintbox import skin
    pt = (_bl_white if mat == WM else _bl_course) if paint else None
    skin(m, name, mat, t, (cx - w / 2, cy - h / 2, -d), (cx + w / 2, cy + h / 2, d), pt, rot=rot, pivot=(cx, cy))


def _force(m, name, a, b, w=1.0):
    """謎の力の糸は描かない（2026-11-20 ユーザー「中の線とかもいらずに完全に浮いている感じでいいよ」）"""
    return


def _rgrip(m, y0, y1, pom):
    """夜の紫の握りに金の輪二つ、黒い石の柄頭"""
    from paintbox import skin
    skin(m, 'grip', WN, 2, [-1.7, y0, -1.7], [1.7, y1, 1.7], paint=_bs_bands)
    for k, y in enumerate((y0 + 1.2, y1 - 2.2)):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.3, y, -2.3], [2.3, y + 1.6, 2.3])
    _stone(m, 'pommel', WB, 3, 0, y0 - pom / 2 + 0.2, 4.6, pom, 2.4)


def _libra(m, cx, cy, s=4.6, zc=3.0):
    """天秤の印: 金の台と光る菱"""
    from paintbox import skin
    skin(m, 'libra_plate', WG, 4, [cx - s * 0.7, cy - s * 0.55, zc - 1.6], [cx + s * 0.7, cy + s * 0.55, zc + 0.6])
    diamond(m, 'libra_gem', WL, (cx, cy), s * 0.8, 1.0, 4, 5, zc=zc + 0.8)


def tower_balance_sword():
    """あいだの大剣（崩れた塔）: 刃は崩れかけた尖塔そのもの。根元の黒い石積みから先の白い石積みへ、段が細りながら積み上がり、
    上ほど段と段のあいだが開いて浮く。段を一本の紫の力の芯が貫いてつなぐ。鍔は欠けた胸壁（凹凸の壁）で、真ん中に天秤の印。"""
    m = _wM('tower_balance_sword')
    r = _rng(m)
    _rgrip(m, -7.0, 8.0, 3.0)
    _stone(m, 'wall', WB, 3, 0, 10.0, 18.0, 4.4, 3.0)
    for k, x in enumerate((-7.6, -2.6, 2.6)):
        _stone(m, f'merlon_{k}', WB, 4, x, 13.6, 3.2, 3.4, 2.6, rot=r.uniform(-4, 4))
    _stone(m, 'float_merlon_3', WB, 4, 8.6, 15.8, 3.0, 3.0, 2.4, rot=r.uniform(14, 24))
    _force(m, 'float_f_merlon', (7.6, 12.2), (8.4, 14.6), 0.8)
    _libra(m, 0, 10.2, 4.6, 3.0)
    y, n = 12.0, 11
    pts = []
    for i in range(n):
        f = i / (n - 1)
        h = 5.8 - 2.4 * f
        w = 8.6 - 5.2 * f
        gap = (0.0 if i == 0 else 0.3) + 2.0 * f ** 1.4 + r.uniform(0, 0.5) * f
        cy = y + gap + h / 2
        cx = r.uniform(-0.5, 0.5) * (0.4 + 2.2 * f)
        rot = r.uniform(-1, 1) * (1.5 + 13 * f)
        mat, t = (WB, 3) if i < 4 else (WM, 6)
        name = 'course_0' if i == 0 else f'float_course_{i}'
        _stone(m, name, mat, t, cx, cy, w, h, 2.6 - 1.0 * f, rot=rot)
        if i >= 2 and r.random() < 0.6:
            sx = cx + r.choice((-1, 1)) * (w / 2 + r.uniform(0.6, 1.6))
            diamond(m, f'float_chip_{i}', mat, (sx, cy + r.uniform(-1, 1)), r.uniform(1.2, 2.0), 1.0, t, t + 1)
        pts.append((cx, cy))
        y = cy + h / 2
    tx = pts[-1][0] + r.uniform(-0.6, 0.6)
    m.seg('float_tip', WM, (tx, y + 1.6), (tx + 0.4, y + 8.0), 3.0, 1.4, 6, 8, tip=True)
    m.chain('float_spire', WG, [(tx + 0.4, y + 9.6), (tx + 0.6, y + 13.0), (tx + 0.7, y + 16.0)], [1.8, 1.0], [1.4, 0.9], 4, 5, tip=True)
    for k in range(len(pts) - 1):
        _force(m, f'float_core_{k}', pts[k], pts[k + 1], 1.3)
    _force(m, 'float_core_tip', pts[-1], (tx, y + 4.0), 1.0)
    _rockify(m, per=(1, 2))
    from divine_bold import big
    return big(m, 5.0, 0.8, 0.8)


def tower_pull_hammer():
    """引き合いの鎚（崩れた塔）: 頭は横に倒れた塔。真ん中で折れて左右へ引き合い、左（天）は白、右（淵）は黒の石積み。
    両端は胸壁の冠（凸の石が外を向く打つ面）。折れ目のあいだを紫の力の糸が渡り、天秤の印の環が真ん中に浮く。"""
    from paintbox import skin
    m = _wM('tower_pull_hammer')
    r = _rng(m)
    _rgrip(m, -9.0, 26.0, 2.6)
    _stone(m, 'collar', WB, 3, 0, 27.0, 6.0, 3.4, 3.0)
    yc = 33.0
    m.arc('ring', WG, (0.0, yc), 3.6, 0, 360, 10, 1.6, 1.4, 4, 4)
    diamond(m, 'float_libra_gem', WL, (0.0, yc), 3.4, 1.0, 4, 5, zc=1.4)
    skin(m, 'ring_post', WG, 4, [-0.9, 28.4, -0.9], [0.9, 30.0, 0.9])
    for sd, mat, t in ((-1, WM, 6), (1, WB, 3)):
        sn = 'lr'[sd > 0]
        x = 4.6
        for j in range(4):
            wj = r.uniform(2.6, 3.6)
            gap = r.uniform(0.6, 1.8) + 0.25 * j
            x += gap
            cx = sd * (x + wj / 2)
            h = 11.0 + j * 1.0 + r.uniform(-0.6, 0.6)
            cy = yc + r.uniform(-1.2, 1.2) * (0.5 + j * 0.4)
            _stone(m, f'float_blk_{sn}{j}', mat, t, cx, cy, wj, h, 5.0 + j * 0.3, rot=r.uniform(-1, 1) * (3 + 3 * j))
            _force(m, f'float_f_{sn}{j}', (sd * (x - gap - 0.2), cy + r.uniform(-2, 2)), (sd * (x + 0.3), cy + r.uniform(-2, 2)), 0.9)
            x += wj
        cx = sd * (x + 1.6)
        _stone(m, f'float_crown_{sn}', mat, t + 1, cx, yc, 3.2, 15.0, 5.8, rot=r.uniform(-4, 4))
        for k, yy in enumerate((yc - 5.0, yc, yc + 5.0)):
            if r.random() < 0.85:
                _stone(m, f'float_crown_{sn}m{k}', mat, t + 1, cx + sd * 2.2, yy + r.uniform(-0.5, 0.5), 1.8, 3.2, 4.6,
                       rot=r.uniform(-8, 8))
        _force(m, f'float_f_{sn}c', (sd * (x - 0.6), yc), (sd * (x + 0.6), yc), 1.0)
    _force(m, 'float_f_mid', (-4.6, yc), (4.6, yc), 1.2)
    _rockify(m, per=(1, 2))
    # 頭（y 27 より上）を横 0.72・縦 0.85 に: ほかのシリーズの鎚と頭の幅をそろえる
    from divine_bold import big
    return big(m, 27.0, 0.72, 0.85)


def _staff_base(m, top):
    from paintbox import skin
    _rgrip(m, -15.0, 2.0, 3.0)
    skin(m, 'shaft', WB, 2, [-1.6, 1.6, -1.6], [1.6, top, 1.6], paint=_ms_black)
    for k, y in enumerate((9.0, 17.0, 25.0)):
        skin(m, f'band_{k}', WG, 5, [-2.3, y, -2.3], [2.3, y + 1.4, 2.3])


def _moon(m, c, s=9.0, name='float_moon_glow'):
    h = s / (2 * math.sqrt(2))
    _bl_bar(m, name, WC, 3, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, 3.2, paint=_mst_moon_paint)



def _bell2(m, pre, cx, top, s, body):
    """鈴（石の胴は body の色、口の縁は反対の色で太く、舌は紫の光）。top は冠の上の面"""
    from paintbox import skin
    lip = WB if body == WM else WM
    skin(m, f'{pre}_crown', WG, 5, [cx - 1.0 * s, top - 1.4 * s, -1.0 * s], [cx + 1.0 * s, top, 1.0 * s])
    skin(m, f'{pre}_neck', body, 6 if body == WM else 4, [cx - 1.5 * s, top - 3.4 * s, -1.4 * s], [cx + 1.5 * s, top - 1.4 * s, 1.4 * s])
    skin(m, f'{pre}_core', body, 6 if body == WM else 4, [cx - 2.6 * s, top - 9.6 * s, -2.2 * s], [cx + 2.6 * s, top - 3.2 * s, 2.2 * s],
         paint=_bl_white if body == WM else _cs_black)
    for sg, n in ((-1, 'l'), (1, 'r')):
        pts = [(cx + sg * 1.8 * s, top - 2.8 * s), (cx + sg * 3.2 * s, top - 6.6 * s), (cx + sg * 4.2 * s, top - 10.2 * s)]
        for k in range(2):
            _cs_bar(m, f'{pre}_flare_{n}{k}', body, 6 if body == WM else 4, pts[k], pts[k + 1], [2.2 * s, 2.6 * s][k], 2.2 * s)
    skin(m, f'{pre}_lip', lip, 8 if lip == WM else 4, [cx - 4.6 * s, top - 11.4 * s, -3.2 * s], [cx + 4.6 * s, top - 9.6 * s, 3.2 * s])
    skin(m, f'{pre}_tongue_glow', WC, 4, [cx - 0.8 * s, top - 12.6 * s, -0.8 * s], [cx + 0.8 * s, top - 9.4 * s, 0.8 * s])


def tower_moon_staff():
    """落ちる月の杖（崩れた塔。案 B、2026-11-20 のブラッシュアップ）: 柄の先に白黒の尖塔が段ごとに浮いて積み上がり、
    その真ん中を太い石の三日月が斜めに貫く。三日月は岩の塊を弧に並べ、真ん中が厚く両の角へ細って尖る。
    三日月の抱く所に紫の月が落ちて止まる。尖塔のてっぺんは欠けた胸壁と金の尖塔"""
    from paintbox import skin  # noqa: F401
    m = _wM('tower_moon_staff')
    r = _rng(m)
    _staff_base(m, 35.0)
    # 尖塔: 6 段。下 2 段は黒、上は白。上ほど離れて傾き、左右へずれる
    y = 34.2
    tops = []
    for i, (w, h) in enumerate(((8.4, 4.6), (7.6, 4.4), (6.8, 4.2), (6.0, 4.0), (5.2, 3.6), (4.4, 3.4))):
        y += 0.0 if i == 0 else r.uniform(0.9, 1.6) + 0.25 * i
        cx = 0.0 if i == 0 else r.uniform(-1, 1) * (0.5 + 0.35 * i)
        mat, t = (WB, 3) if i < 2 else (WM, 6)
        _stone(m, 'spire_0' if i == 0 else f'float_spire_{i}', mat, t, cx, y + h / 2, w, h, 3.2 - 0.2 * i,
               rot=0 if i == 0 else r.uniform(-1, 1) * (3 + 2.5 * i))
        tops.append((cx, y + h))
        y += h
    for k, x in enumerate((-1.6, 1.6)):
        _stone(m, f'float_merlon_{k}', WM, 7, tops[-1][0] + x + r.uniform(-0.3, 0.3), y + 1.8 + r.uniform(0, 1.0), 1.8, 2.4, 2.2,
               rot=r.uniform(-18, 18))
    m.chain('float_tipg', WG, [(tops[-1][0], y + 4.4), (tops[-1][0] + 0.3, y + 8.0), (tops[-1][0] + 0.4, y + 11.0)],
            [1.8, 1.0], [1.4, 0.9], 4, 5, tip=True)
    _libra(m, 0, 36.6, 4.4, 3.4)
    # 三日月: 岩の塊 9 つ。弧（125°→-125°、右がふくらみ左が開く）を 22° 傾け、真ん中が厚く角が細い
    c, R, tilt = (2.4, 53.0), 15.0, math.radians(18)
    N = 9
    for k in range(N):
        t0, t1 = k / N, (k + 1) / N
        tm = (t0 + t1) / 2
        a = math.radians(125 - 250 * tm)
        th = 1.4 + 4.0 * math.sin(math.pi * tm)
        rr = R - th / 2
        x, yy = rr * math.cos(a) * 0.82, rr * math.sin(a)
        px = c[0] + x * math.cos(tilt) - yy * math.sin(tilt)
        py = c[1] + x * math.sin(tilt) + yy * math.cos(tilt)
        L = 2 * R * math.sin(math.radians(250 / N) / 2) * 0.86
        ang = math.degrees(a + tilt) + 90 + r.uniform(-6, 6)
        mat, t = (WB, 3) if py < c[1] - 2 else (WM, 6)
        _stone(m, f'float_cres_{k}', mat, t, px + r.uniform(-0.3, 0.3), py + r.uniform(-0.3, 0.3), L, th, 2.6 - 0.6 * abs(tm - 0.5),
               rot=ang)
    # 月: 三日月の抱く所（弧の内側）
    # 三日月は尖塔の奥（z を下げる）に置き、尖塔の前後で重ならないようにする
    for p in m.parts:
        if p['name'].startswith('float_cres_'):
            p['frm'][2] -= 3.6
            p['to'][2] -= 3.6
            p['origin'][2] -= 3.6
    _moon(m, (c[0] - 7.6, c[1] + 1.0), 7.0)
    for k in range(3):
        a = r.uniform(0, 2 * math.pi)
        diamond(m, f'float_chip_{k}', WM if k % 2 else WB, (c[0] + math.cos(a) * 16.0, c[1] + math.sin(a) * 10.0),
                r.uniform(1.4, 2.2), 1.0, 5, 6)
    _rockify(m, per=(1, 1), scale=0.8)
    return m


def tower_sigil_dagger():
    """追う印の短剣（崩れた塔。2026-11-20 のブラッシュアップ）: 刃は塔から折れた尖塔の鉤。幅のある石の破片 7 つが弧に浮いて連なり、
    先で大きく鉤に返って尖る。根元の 2 つは黒、先は白。破片の脇に小さな欠片。根元に天秤の印、柄の下に逆手の黒い石の輪"""
    from paintbox import skin
    m = _wM('tower_sigil_dagger')
    r = _rng(m)
    skin(m, 'grip', WN, 2, [-2.0, -2.0, -1.9], [2.0, 7.0, 1.9], paint=_bs_bands)
    skin(m, 'grip_ring', WG, 4, [-2.5, 0.6, -2.3], [2.5, 2.0, 2.3])
    _stone(m, 'pommel', WB, 3, 0, -3.0, 5.2, 2.6, 2.4)
    for k in range(10):
        a0, a1 = 2 * math.pi * k / 10, 2 * math.pi * (k + 1) / 10
        _bl_bar(m, f'ring_{k}', WB, 3, (3.4 * math.cos(a0), -7.6 + 3.4 * math.sin(a0)),
                (3.4 * math.cos(a1), -7.6 + 3.4 * math.sin(a1)), 2.0, 1.3, paint=_bl_course)
    path = [(0.0, 6.0), (1.6, 13.0), (3.0, 19.6), (3.4, 25.8), (2.4, 31.4), (0.0, 35.6), (-3.4, 37.6), (-6.8, 36.0), (-8.6, 31.8)]
    ws = (8.4, 7.6, 6.8, 5.8, 4.8, 3.8, 3.0, 2.2)
    for i in range(len(path) - 1):
        a, b = path[i], path[i + 1]
        g = 0.0 if i == 0 else r.uniform(1.0, 1.9)
        L = math.dist(a, b)
        ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        nx, ny = -uy, ux
        off = 0.0 if i == 0 else r.uniform(-0.8, 0.8)
        A = (a[0] + ux * g / 2 + nx * off, a[1] + uy * g / 2 + ny * off)
        B = (b[0] - ux * g / 2 + nx * off, b[1] - uy * g / 2 + ny * off)
        mat, t = (WB, 3) if i < 2 else (WM, 6)
        cx, cy = (A[0] + B[0]) / 2, (A[1] + B[1]) / 2
        ang = math.degrees(math.atan2(B[1] - A[1], B[0] - A[0])) + (r.uniform(-8, 8) if i > 0 else 0)
        last = i == len(path) - 2
        if last:
            m.seg('float_tip', WM, A, (b[0] - ux * 0.5, b[1] - uy * 0.5), ws[i] + 0.6, 1.4, 6, 8, tip=True)
        else:
            _stone(m, 'shard_0' if i == 0 else f'float_shard_{i}', mat, t, cx, cy, math.dist(A, B), ws[i], 2.4 - 0.15 * i, rot=ang)
        if 1 <= i <= 5 and r.random() < 0.7:
            sd = r.choice((-1, 1))
            diamond(m, f'float_chip_{i}', mat, (cx + nx * sd * (ws[i] / 2 + 1.6), cy + ny * sd * (ws[i] / 2 + 1.6)),
                    r.uniform(1.2, 1.8), 0.9, t, t + 1)
    _libra(m, 0.6, 9.4, 4.0, 2.6)
    _rockify(m, per=(1, 1), scale=0.75)
    return m


def tower_clap_bell_staff():
    """合掌の鈴杖（崩れた塔。2026-11-20 のブラッシュアップ）: 杖の頭は崩れた鐘楼の尖りアーチ。左右のアーチの半分が大きな岩の塊に割れて浮き、
    合わせた掌のように頂で向き合う（頂は触れない）。アーチの中に白い大きな鈴、外に黒い小さな鈴が二つ浮いて下がる。
    アーチの根元は黒い石、上は白。根元の割れた梁に天秤の印"""
    from paintbox import skin
    m = _wM('tower_clap_bell_staff')
    r = _rng(m)
    skin(m, 'pommel', WB, 3, [-2.4, -11.0, -2.4], [2.4, -8.0, 2.4])
    skin(m, 'grip', WN, 3, [-1.9, -8.0, -1.9], [1.9, 4.0, 1.9], paint=_cs_grip)
    for k, (y0, y1) in enumerate(((-6.0, -4.4), (0.6, 2.2))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.4, y0, -2.4], [2.4, y1, 2.4])
    skin(m, 'rod', WN, 3, [-1.2, 3.0, -1.2], [1.2, 30.0, 1.2])
    # 割れた梁: 真ん中だけが柄に付き、左右の欠片は浮いて少し下がる
    _stone(m, 'lintel', WB, 3, 0, 31.2, 8.0, 3.6, 3.4)
    _libra(m, 0, 31.2, 4.4, 3.4)
    for sd in (-1, 1):
        _stone(m, f'float_lintel_{"lr"[sd > 0]}', WB, 4, sd * 8.4 + r.uniform(-0.3, 0.3), 30.6 + r.uniform(-0.8, 0.4), 6.4, 3.4, 3.2,
               rot=r.uniform(-1, 1) * 8)
    for sd in (-1, 1):
        sn = 'lr'[sd > 0]
        P = [(sd * 10.0, 34.0), (sd * 12.0, 43.0), (sd * 11.4, 51.0), (sd * 8.8, 58.0), (sd * 5.2, 63.4), (sd * 1.8, 67.0)]
        for i in range(5):
            a, b = P[i], P[i + 1]
            g = r.uniform(1.0, 1.8)
            L = math.dist(a, b)
            ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            off = sd * r.uniform(0.2, 1.0) * (i + 1) * 0.6
            A = (a[0] + ux * g / 2 + off, a[1] + uy * g / 2)
            B = (b[0] - ux * g / 2 + off, b[1] - uy * g / 2)
            cx, cy = (A[0] + B[0]) / 2, (A[1] + B[1]) / 2
            mat, t = (WB, 3) if i < 2 else (WM, 6)
            ang = math.degrees(math.atan2(B[1] - A[1], B[0] - A[0])) + r.uniform(-9, 9)
            _stone(m, f'float_arch_{sn}{i}', mat, t, cx, cy, math.dist(A, B) + 0.6, 5.4 - 0.5 * i, 3.4 - 0.25 * i, rot=ang)
        # 外の小さな鈴（黒、白い口）
        _bell2(m, f'float_bell_{sn}', sd * 16.0 + r.uniform(-0.5, 0.5), 41.0 + r.uniform(-1, 1), 0.72, WB)
    # 中の大きな鈴（白、黒い口）。頂の下に浮く
    _bell2(m, 'float_bell_c', r.uniform(-0.4, 0.4), 58.0, 1.1, WM)
    _rockify(m, per=(1, 2))
    return m

ALL = [tower_balance_sword, tower_pull_hammer, tower_moon_staff, tower_sigil_dagger, tower_clap_bell_staff, tower_eye_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
