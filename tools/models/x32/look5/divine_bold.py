# -*- coding: utf-8 -*-
"""神器（look5・個性版）。終章の神器 12 品（docs/design/50_finale.md の 4.5）。巡礼者の翼・鐘・灯・瞼・時計から一品ずつ形を立てる。
色: 白い石（stone）の 1 系統、差し色は金（gold）、光は朝の光（dawn）、黒い硝子（glass）を差し色に。

形は他のシリーズから借りない。三回目（2026-11-19）は今の形を捨て、モチーフだけ残して考え直した（神器は参照並みに頭が武器の半分以上）。
- 柄: 白い石の握りに金の細い帯を三本、柄頭は小さな金の輪（ループ）。
- 振る武器は握りの真ん中を原点にする。杖・鈴杖は握り -14..2。弓は握りの中心 (12, 15)。
- 形と光: 天穹の剣 = 片翼の刃（羽の間の朝の光、浮く欠片）、羽の番人 = 翼で包む光の刃（面頬の目）、七つ鐘 = 七つの鐘の車輪（軸の芯）、
  灰打ち = 金床の上で火挟みが掴む朝日、暁の灯杖 = 三つの輪の天球儀（朝日の芯）、雲の瞼 = 六翼の瞳（虹彩）、
  約束 = 鈴の樹（三日月の角から鈴、浮く灯）、鐘守 = 割れた台の上に浮かぶ鐘楼（光なし）、巻き戻り = 時計の長針の刃（文字盤の芯・窓の光・秒針）、
  灯蛾 = 光の刃に止まる蛾（翅の紋）、巡礼の羽弓 = 弓腕と握りの宝石の芯、霞鳥の弓 = 光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'stone': ['#2a2a32', '#45454f', '#62626c', '#80808a', '#9e9ea6', '#bbbbc2', '#d4d4d9', '#e8e8ec', '#f6f6f8', '#ffffff'],
    'gold': ['#4a3410', '#7a5618', '#a87a22', '#d4a432', '#f0c858', '#fce69a'],
    'dawn': ['#a85a3a', '#d8805a', '#f4a878', '#fcd0a8', '#fff0dc', '#fffaf2'],
    'rose': ['#4a2026', '#6e2e34', '#94403f', '#b8584c', '#d47862', '#ea9c80'],
    'glass': ['#0c0c14', '#18182a', '#26263e', '#383856', '#4e4e70', '#6a6a8c'],
})
S, G, D, K = 'stone', 'gold', 'dawn', 'glass'


def M(name):
    return Model(name, accent=G, glowmat=D)


def grip(m, y0, y1, loop=True):
    """白い石の握りに金の細い帯三本、上に金の鍔元、柄頭は金の輪"""
    m.box('grip', S, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 4, y1, 6))
    for k in range(3):
        y = y0 + (y1 - y0) * (k + 0.5) / 3 - 0.5
        m.box(f'grip_band_{k}', G, [-1.75, y, -1.75], [1.75, y + 1, 1.75], 3)
    m.box('grip_cap', G, [-2.25, y1, -2.25], [2.25, y1 + 1.5, 2.25], grad=(y1, 2, y1 + 1.5, 4))
    if loop:
        m.box('pommel_neck', G, [-1.25, y0 - 1.5, -1.25], [1.25, y0, 1.25], 3)
        m.arc('pommel', G, (0, y0 - 4), 2.5, 90, 450, 6, 1.25, 0.8, 3, 5)


def rod(m, y0, y1, bands=()):
    m.box('rod', S, [-1.25, y0, -1.25], [1.25, y1, 1.25], grad=(y0, 4, y1, 7))
    for k, y in enumerate(bands):
        m.box(f'band_{k}', G, [-1.75, y, -1.75], [1.75, y + 1.25, 1.75], 3)


def grow(m, ymin, f, pivot_x=0.0, also=()):
    """下端が ymin より上の部品（と also で始まる名の部品）を (pivot_x, ymin, 0) を中心に f 倍（神器の頭を一回り大きく）"""
    for p in m.parts:
        if p['frm'][1] < ymin - 1e-6 and not p['name'].startswith(tuple(also)):
            continue
        for key in ('frm', 'to', 'origin'):
            v = p[key]
            p[key] = [round(pivot_x + (v[0] - pivot_x) * f, 3), round(ymin + (v[1] - ymin) * f, 3), round(v[2] * f, 3)]
    return m


def thick_ring(m, name, c, r, w=2.6, d=1.2, n=14, zc=0.0, core=True):
    """厚い金の光輪（芯が光る）"""
    m.arc(name, G, c, r, 0, 360, n, w, d, 3, 5, zc=zc)
    if core:
        m.box(f'{name}_core', D, [c[0] - 1.6, c[1] - 1.6, zc - 0.9], [c[0] + 1.6, c[1] + 1.6, zc + 0.9], 5)


def thick_rays(m, name, c, r0, r1, angs, w=2.8, d=1.1, zc=0.0):
    """厚い金の光の筋"""
    for k, a in enumerate(angs):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        m.seg(f'{name}_{k}', G, (c[0] + ca * r0, c[1] + sa * r0), (c[0] + ca * r1, c[1] + sa * r1), w, d, 4, 5, tip=True, zc=zc)


def wing(m, name, sd, base, n, L, ang0, step, w, d, mat=S, t0=5, t1=8, zc=0.0):
    """畳んだ翼: 根元から外上へ扇に開く風切羽 n 枚（sd = -1 左、1 右）"""
    for k in range(n):
        a = math.radians(ang0 + step * k)
        bx, by = base[0] + sd * k * 0.6, base[1] - k * 0.4
        m.seg(f'{name}_{k}', mat, (bx, by), (bx + sd * math.cos(a) * (L - k * 1.2), by + math.sin(a) * (L - k * 1.2)),
              w, d, t0, t1, tip=True, zc=zc)


def float_shard(m, name, c, s):
    """浮く欠片: 金の菱に朝の光の芯"""
    diamond(m, f'float_{name}', G, c, s, 1.1, 3, 5)
    diamond(m, f'float_{name}_core', D, c, s * 0.5, 1.3, 4, 5)


# ---------------------------------------------------------------- 三回目（2026-11-19）: 原型を捨ててモチーフから考え直し、頭を参照並みに大きく

def ellipse(m, name, mat, c, rx, ry, rot, n, w, d, t0, t1, zc=0.0):
    """傾けた楕円の輪（奥へ傾けた輪を正面から見た形）"""
    cr, sr = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    pts = []
    for k in range(n + 1):
        a = 2 * math.pi * k / n
        x, y = rx * math.cos(a), ry * math.sin(a)
        pts.append((c[0] + x * cr - y * sr, c[1] + x * sr + y * cr))
    m.chain(name, mat, pts, w, d, t0, t1, tip=False, zc=zc)
    return pts


def bell(m, name, c, s, mouth=-1, mat=G):
    """口を mouth（-1 下・1 上）へ向けた鐘。s = 口の半幅"""
    y = c[1]
    m.box(f'{name}_top', mat, [c[0] - s * 0.45, y - mouth * s * 0.9, -s * 0.45], [c[0] + s * 0.45, y - mouth * s * 0.5, s * 0.45], 4)
    lo, hi = sorted((y - mouth * s * 0.55, y + mouth * s * 0.4))
    m.box(f'{name}_body', mat, [c[0] - s * 0.75, lo, -s * 0.75], [c[0] + s * 0.75, hi, s * 0.75], grad=(lo, 3, hi, 5))
    lo, hi = sorted((y + mouth * s * 0.35, y + mouth * s * 0.75))
    m.box(f'{name}_lip', mat, [c[0] - s, lo, -s], [c[0] + s, hi, s], 5)


def eye(m, name, c, r):
    """白い石の目に黒い硝子の瞳と光る虹彩"""
    m.box(f'{name}_white', S, [c[0] - r, c[1] - r * 0.55, -r * 0.35], [c[0] + r, c[1] + r * 0.55, r * 0.35], 9)
    m.box(f'{name}_iris_glow', D, [c[0] - r * 0.45, c[1] - r * 0.45, -r * 0.4], [c[0] + r * 0.45, c[1] + r * 0.45, r * 0.4], 4)
    m.box(f'{name}_pupil', K, [c[0] - r * 0.15, c[1] - r * 0.3, -r * 0.45], [c[0] + r * 0.15, c[1] + r * 0.3, r * 0.45], 1)


def moth_wing(m, name, sd, base, pts, w, spot=None):
    """蛾の翅: 根元 base から pts（sd で左右）を厚い石の板でたどる。spot に光る眼状紋"""
    P = [(base[0] + sd * x, base[1] + y) for x, y in pts]
    m.chain(f'{name}', S, [base] + P, w, 0.6, 6, 8, tip=True)
    if spot:
        x, y, s = spot
        m.box(f'{name}_spot_glow', D, [base[0] + sd * x - s, base[1] + y - s, -0.7], [base[0] + sd * x + s, base[1] + y + s, 0.7], 4)
        m.box(f'{name}_spot_ring', G, [base[0] + sd * x - s * 1.5, base[1] + y - s * 1.5, -0.65], [base[0] + sd * x + s * 1.5, base[1] + y + s * 1.5, 0.65], 3)


def big(m, ymin, fx, fy, fz=None):
    """下端が ymin より上の部品を (0, ymin, 0) を中心に横 fx・縦 fy・奥 fz 倍。回した節は向きと太さも合わせて直す"""
    fz = fx if fz is None else fz
    for p in m.parts:
        if p['frm'][1] < ymin - 1e-6:
            continue
        o = p['origin']
        no = [o[0] * fx, ymin + (o[1] - ymin) * fy, o[2] * fz]
        hx, hy, hz = [(p['to'][i] - p['frm'][i]) / 2 for i in range(3)]
        th = math.radians(p.get('rot') or 0)
        if th:
            ls = math.hypot(fx * math.cos(th), fy * math.sin(th))
            ws = fx * fy / ls
            p['rot'] = round(math.degrees(math.atan2(fy * math.sin(th), fx * math.cos(th))), 2)
            hx, hy = hx * ls, hy * ws
        else:
            hx, hy = hx * fx, hy * fy
        hz *= fz
        p['origin'] = [round(v, 3) for v in no]
        p['frm'] = [round(no[0] - hx, 3), round(no[1] - hy, 3), round(no[2] - hz, 3)]
        p['to'] = [round(no[0] + hx, 3), round(no[1] + hy, 3), round(no[2] + hz, 3)]
    return m


def divine_sky_sword():
    """天穹の剣（太い箱 + 面の絵 + 平面の板）: 刃は柄の軸の真上にまっすぐ立つ白い大剣で、真ん中に金の尾根（盛り上がった樋）。
    鍔の左の付け根から片翼が刃に沿って斜め上へ開く（手前の風切羽 5 枚と雨覆い、奥に暗い翼）。羽の根元は刃の左の縁の金の受けに留まる。
    鍔の真ん中に爪で留めた光の宝石。翼の後ろに光の筋、左上に光の欠片と十字の粒"""
    from paintbox import skin
    from pix3d import Sprite, sprite_planes
    m = M('divine_sky_sword')

    import math as _m

    def face_mark(fn):
        """south・north だけに描く"""
        return lambda sp, f, w, h: fn(sp, f, w, h) if f in ('south', 'north') else None

    def bar(name, mat, t, a, b, w, d, paint=None, glow=None):
        """a から b へ伸びる、幅 w・奥行き ±d の回した板（面に絵を描ける）"""
        L = _m.dist(a, b)
        cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        ang = _m.degrees(_m.atan2(b[1] - a[1], b[0] - a[0]))
        skin(m, name, mat, t, (cx - L / 2, cy - w / 2, -d), (cx + L / 2, cy + w / 2, d), paint, rot=ang, pivot=(cx, cy), glow=glow)

    def arc(name, mat, t, c, r, a0, a1, n, w0, w1, d0, d1, paint=None):
        """円弧を n 本の回した板でたどる（太さ・奥行きが先へ細る）"""
        pts = [(c[0] + r * _m.cos(_m.radians(a0 + (a1 - a0) * k / n)), c[1] + r * _m.sin(_m.radians(a0 + (a1 - a0) * k / n)))
               for k in range(n + 1)]
        for k in range(n):
            f = k / max(1, n - 1)
            a, b = pts[k], pts[k + 1]
            ext = 0.6
            L = _m.dist(a, b)
            ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            bar(f'{name}_{k}', mat, t + f * 1.2, (a[0] - ux * ext, a[1] - uy * ext), (b[0] + ux * ext, b[1] + uy * ext),
                w0 + (w1 - w0) * f, d0 + (d1 - d0) * f, paint)

    def bevel_line(sp, f, w, h):
        sp.rect(0, h - 1, w, h, G, 5.5)

    # ---- 柄: 白い芯に、金の帯が斜めに三本巻く
    skin(m, 'grip', S, 3, (-1.6, -6, -1.6), (1.6, 6.5, 1.6))
    for k, y in enumerate((-3.5, 0.5, 4.5)):
        bar(f'grip_strap_{k}', G, 3.5, (-2.1, y - 1.2), (2.1, y + 1.2), 1.1, 1.85)
    # ---- 柄頭: 菱の金の台に、光る宝石の菱
    skin(m, 'pommel', G, 3.5, (-2.4, -11.4, -2.2), (2.4, -6.6, 2.2), face_mark(lambda sp, f, w, h: sp.rect(1, 1, w - 1, h - 1, G, 2.3)),
         rot=45, pivot=(0, -9))
    skin(m, 'pommel_gem', D, 3, (-1.2, -10.2, -2.8), (1.2, -7.8, 2.8), rot=45, pivot=(0, -9), glow=True)
    # ---- 鍔: 三日月のように上へ反る翼の形。中は菱の宝石の台
    skin(m, 'guard_core', G, 3.2, (-4.5, 7, -2.6), (4.5, 11, 2.6), face_mark(lambda sp, f, w, h: sp.rect(1, 1, w - 1, h - 1, G, 2.2)))
    for sd, n in ((-1, 'l'), (1, 'r')):
        a0, a1 = (270 + 15, 270 + 85) if sd > 0 else (270 - 15, 270 - 85)
        arc(f'guard_arm_{n}', G, 3.4, (0, 21), 13, a0, a1, 4, 4.4, 1.8, 2.4, 1.2, face_mark(bevel_line))
    skin(m, 'gem_seat', G, 2.6, (-3.6, 5.4, -3.2), (3.6, 12.6, 3.2), face_mark(lambda sp, f, w, h: sp.rect(1, 1, w - 1, h - 1, G, 1.6)),
         rot=45, pivot=(0, 9))

    def gem_face(sp, f, w, h):
        sp.diamond((w / 2, h / 2), min(w, h) / 2, D, 3)
        sp.diamond((w / 2, h / 2), min(w, h) / 4, D, 5)
    skin(m, 'gem', D, 2, (-2.4, 6.6, -4.4), (2.4, 11.4, 4.4), gem_face, rot=45, pivot=(0, 9), glow=True)
    # ---- 刃: 真ん中に縦長の「空の窓」が抜けた二本の刃の柱。窓の中は朝の光と星。
    #      右の縁は光る刃先。切っ先は二股（それぞれ根元が太く先が細い、右が長い）で、間に光の星が浮く
    def rail_paint(inner_low):
        """回した柱の面: 長さ（u）に沿って先ほど明るく、窓の側の縁（低い行か高い行）に金の線"""
        def paint(sp, f, w, h):
            if f not in ('south', 'north'):
                return
            for jj in range(h):
                for ii in range(w):
                    u = ii if f == 'south' else w - 1 - ii
                    sp.cell[(ii, jj)]['t'] += u / w * 2.2
            row = 0 if inner_low else h - 1
            sp.rect(0, row, w, row + 1, G, 4.5)
        return paint
    # 柱は二節ずつ、先へ細りながら内へ寄る（刃の外形が切っ先へ絞られる）
    for sd, n, segs in ((-1, 'l', (((-3.2, 12), (-2.9, 35.5), 4.2), ((-2.9, 34.8), (-2.3, 57.5), 3.4))),
                        (1, 'r', (((3.4, 12), (3.1, 35.5), 4.6), ((3.1, 34.8), (2.5, 57.5), 3.8)))):
        for k, (a, b, w) in enumerate(segs):
            bar(f'rail_{n}{k}', S, 5 + k * 0.6 + (0.4 if sd > 0 else 0), a, b, w, 1.4, rail_paint(sd < 0))
    skin(m, 'bridge_low', G, 3.4, (-5.4, 11.2, -1.8), (6, 15.5, 1.8), face_mark(lambda sp, f, w, h: sp.rect(1, 1, w - 1, h - 1, G, 2.2)))
    skin(m, 'bridge_high', G, 3.8, (-4.4, 54.5, -1.7), (4.6, 57.5, 1.7))
    bar('edge_r', S, 8, (5.5, 13), (4.4, 57), 1.2, 0.75, face_mark(lambda sp, f, w, h: sp.rect(0, 0, w, h, S, 9.5)))
    win = Sprite(-1.3, 15, 1.3, 55)
    win.poly([(-1.2, 15), (1.2, 15), (0.8, 55), (-0.8, 55)], D, 1.5, shade=False, tone=lambda x, y: 1.2 + (y - 15) / 40 * 2.5)
    for y in (21, 29, 36, 44, 50):
        win.dot(0, y, D, 5)
    sprite_planes(win, m, 'window', 0.0)
    # 切っ先: 二股（それぞれ根元が太く先が細い、右が長い）と、間に浮く光の星
    edge9 = face_mark(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, S, 9.5))
    bar('prong_r', S, 7.5, (3.2, 56.5), (2.6, 66), 4.2, 1.2, edge9)
    bar('prong_r_tip', S, 8.5, (2.7, 65.4), (1.2, 73), 2.4, 0.8, edge9)
    bar('prong_l', S, 7, (-2.8, 56.5), (-2.2, 64), 3.6, 1.1, edge9)
    bar('prong_l_tip', S, 8, (-2.2, 63.4), (-0.9, 69), 2.0, 0.75, edge9)
    skin(m, 'float_star', D, 3.5, (-1.3, 65.7, -1.0), (1.3, 68.3, 1.0), rot=45, pivot=(0, 67), glow=True)

    # 片翼（平面の板）: 根元は刃の左の縁。下の羽ほど長く寝て、上の羽ほど短く立つ（翼が斜め上へ開く）
    import math as _m
    feathers = []
    for k in range(5):
        y = 14 + k * 7
        ln = 26 - k * 3.2
        ang = _m.radians(160 - k * 14)
        feathers.append(((-4, y + 2), (-4 + _m.cos(ang) * ln, y + 2 + _m.sin(ang) * ln)))
    wg = Sprite(-32, 6, -2, 66)
    for k, (a, b) in enumerate(feathers):
        wg.line(a, b, 7, S, 5.5, w1=3, tg=8.5, edge=(G, 4.5))
        wg.disc(b, 1.6, S, 8.5)
        mid = (a[0] + (b[0] - a[0]) * 0.4, a[1] + (b[1] - a[1]) * 0.4 - 2)
        wg.line((a[0] - 0.5, a[1] - 2.2), mid, 1.1, D, 2, shade=False)
    for k in range(4):
        y = 16 + k * 7.5
        ang = _m.radians(150 - k * 12)
        tip = (-4 + _m.cos(ang) * 8, y + 2 + _m.sin(ang) * 8)
        wg.line((-4, y + 2), tip, 6, S, 6.5, w1=4, tg=8, edge=(G, 5))
        wg.disc(tip, 2, S, 8)
    wg.shade(light=1.0, dark=1.3, outline=(G, 0))
    sprite_planes(wg, m, 'wing', 0.0)
    wb = Sprite(-36, 8, -2, 70)
    for k in range(3):
        y = 20 + k * 10
        ln = 30 - k * 4
        ang = _m.radians(150 - k * 15)
        tip = (-4 + _m.cos(ang) * ln, y + _m.sin(ang) * ln)
        wb.line((-4, y), tip, 8, S, 3.5, w1=3, tg=5.5, edge=(G, 2.5))
        wb.disc(tip, 1.8, S, 5.5)
    wb.shade(light=0.8, dark=1.0, outline=(G, 0))
    sprite_planes(wb, m, 'wing_back', -1.5)
    # 羽の付け根: 刃の左下に沿う金の肩当て（弧の三枚）。羽はここから生える
    arc('wing_root', G, 3.6, (2, 26), 9, 190, 120, 3, 3.2, 2.2, 1.9, 1.5, face_mark(bevel_line))
    # 光の筋（翼の後ろ）と光の粒、浮く欠片
    ws = Sprite(-30, 8, 0, 70)
    for k, (a, b, c) in enumerate((((-5, 12), (-14, 34), (-10, 58)), ((-5, 11), (-20, 28), (-26, 52)))):
        pts = [(a[0] + (b[0] - a[0]) * t + (c[0] - b[0]) * t * t, a[1] + (c[1] - a[1]) * t) for t in [i / 30 for i in range(31)]]
        for q0, q1 in zip(pts, pts[1:]):
            ws.line(q0, q1, 3 - k * 0.6, D, 1.5, shade=False)
        for q0, q1 in zip(pts, pts[1:]):
            ws.line(q0, q1, 1.1, D, 4.5, shade=False)
    sprite_planes(ws, m, 'streak', -1.4)
    sk = Sprite(-30, 54, 12, 76)
    for x, y in ((-24, 68), (-12, 72), (9, 66), (-28, 58)):
        for dx, dy in ((-2, 0), (-1, 0), (1, 0), (2, 0), (0, -2), (0, -1), (0, 1), (0, 2)):
            sk.dot(x + dx + 0.5, y + dy + 0.5, D, 2.5)
        sk.dot(x + 0.5, y + 0.5, D, 5)
    sprite_planes(sk, m, 'float_sparkle', 0.0)
    float_shard(m, 'shard', (-26, 66), 5)
    return big(m, 5, 0.8, 0.8)   # 柄から上を 8 割に（2026-11-20）


def divine_warden_blade():
    """羽の番人の大剣（五回目）: 番人の面頬の鍔（目の細い窓が光る）から、光の刃を左右の白い翼の刃が包むように立ち上がり、
    切っ先で合わさる。刃の後ろには左右に大きな翼（平面、長い風切羽が上へ扇に開く）"""
    from divine_kit import fm, frame_face, edge_top, bar, path, strapped_grip, pommel, feather_plane, fan, skin
    m = M('divine_warden_blade')
    strapped_grip(m, -8, 7.5)
    pommel(m, -10.6)
    skin(m, 'visor', G, 3.2, (-6, 8, -3), (6, 14, 3), frame_face())
    skin(m, 'visor_eye', D, 4, (-4.2, 10.3, -3.3), (4.2, 11.7, 3.3), glow=True)
    skin(m, 'visor_brow', G, 4.2, (-7.2, 13.4, -3.4), (7.2, 15, 3.4))
    skin(m, 'core', D, 2, (-1.7, 14.5, -0.7), (1.7, 58, 0.7), fm(lambda sp, f, w, h: sp.rect(w / 2 - 0.5, 0, w / 2 + 0.5, h, D, 5)), glow=True)
    bar(m, 'core_tip', D, 3, (0, 57.5), (0, 64), 2.6, 0.6, glow=True)
    feathers = []
    for sd, n in ((-1, 'l'), (1, 'r')):
        pts = [(sd * 4.6, 14.5), (sd * 8.6, 26), (sd * 8.4, 40), (sd * 5.4, 52), (sd * 1.6, 62), (0, 67)]
        path(m, f'wing_{n}', S, 5.5, pts, [4.6, 4.4, 3.8, 3.2, 2.4], 1.4, edge_top(G, 5) if sd < 0 else fm(lambda sp, f, w, h: sp.rect(0, 0, w, 1, G, 5)), dt=0.5)
        skin(m, f'clasp_{n}', G, 4, (min(sd * 3.6, sd * 6.4), 13.4, -2.2), (max(sd * 3.6, sd * 6.4), 17.4, 2.2), frame_face())
        # 翼: 鍔の脇から、長い羽（上ほど長い）が刃に沿って上へ、外ほど寝て開く
        a0 = 84 if sd > 0 else 96
        feathers += fan((sd * 7, 17), 5, 36, -6, a0, -11 * sd, 6, 3)
    feather_plane(m, 'float_wings', feathers, -1.7, t0=5)
    return big(m, 8, 1.1, 1.0)


def divine_bell_hammer():
    """七つ鐘の大鎚（五回目）: 頭は太い金の車輪。七本の輻が車輪の外へ腕として突き出し、その先に大きさの違う鐘が口を下に下がる
    （鐘は車輪の外にあって隠れない）。軸に菱の光の宝石。車輪の外に光の音の輪（平面）"""
    from divine_kit import arc, bar, gem, strapped_grip, pommel, rod, bell3d, glow_plane, edge_top, skin
    m = M('divine_bell_hammer')
    strapped_grip(m, -6, 5.5)
    pommel(m, -8.6)
    rod(m, 6, 31, bands=(14, 22))
    c = (0, 41)
    arc(m, 'wheel', G, 3.4, c, 9, 0, 360, 12, 3.2, 3.2, 2.2, 2.2, edge_top(G, 5.2))
    gem(m, 'hub', c, 4.2, 2.6)
    for k in range(7):
        a = math.radians(90 + 360 / 7 * k)
        ca, sa = math.cos(a), math.sin(a)
        bar(m, f'spoke_{k}', G, 3, (c[0] + ca * 2.6, c[1] + sa * 2.6), (c[0] + ca * 13.2, c[1] + sa * 13.2), 1.3, 1.1)
        skin(m, f'knob_{k}', G, 5, (c[0] + ca * 13 - 1, c[1] + sa * 13 - 1, -1.2), (c[0] + ca * 13 + 1, c[1] + sa * 13 + 1, 1.2))
        s = 2.3 + (k % 3) * 0.4
        q = (c[0] + ca * 13, c[1] + sa * 13)
        if k == 0:
            # 真上の鐘は腕の先に立つ（口を下に、腕の上に乗る）
            bell3d(m, f'bell_{k}', (q[0], q[1] + 0.9 + s * 0.9), s)
            continue
        bar(m, f'cord_{k}', G, 4, (q[0], q[1] + 0.5), (q[0], q[1] - 1.8), 0.7, 0.4)
        bell3d(m, f'bell_{k}', (q[0], q[1] - 1.6 - s * 0.95), s)

    big(m, 29, 1.35, 1.35)
    c = (0, 29 + (41 - 29) * 1.35)

    def waves(sp):
        for r in (17.5 * 1.35, 20 * 1.35):
            for a0 in (25, 145):
                pts = [(c[0] + r * math.cos(math.radians(a0 + i * 4)), c[1] + r * math.sin(math.radians(a0 + i * 4))) for i in range(9)]
                for p, q in zip(pts, pts[1:]):
                    sp.line(p, q, 1.5, D, 2.5 if r < 25 else 1.5, shade=False)
    glow_plane(m, 'float_wave', waves, (c[0] - 29, c[1] - 29, c[0] + 29, c[1] + 29), -2.4)
    return m


def divine_ash_hammer():
    """灰打ちの大鎚（四回目）: 白い石の金床の頭（左へ反る角、右は金の打つ面）。頭の上で金の火挟みが朝日の塊を掴み、
    朝日の背には平面の光の筋と火の粉"""
    from divine_kit import fm, frame_face, bar, path, strapped_grip, pommel, rod, glow_plane, skin
    m = M('divine_ash_hammer')
    strapped_grip(m, -6, 5.5)
    pommel(m, -8.6)
    rod(m, 6, 27, bands=(14,))
    skin(m, 'waist', S, 4.5, (-3.2, 26, -3), (3.2, 30.5, 3), fm(lambda sp, f, w, h: sp.rect(0, 1.5, w, 2.5, D, 3)))
    skin(m, 'seam_glow', D, 3, (-3.3, 27.4, -3.1), (3.3, 28.2, 3.1), glow=True)
    skin(m, 'top', S, 5.5, (-9, 30.5, -3.6), (9, 35, 3.6), frame_face(4.2, S))
    path(m, 'horn', S, 6, [(-8.6, 33), (-14, 34), (-18, 37)], [3.6, 2.2], [2.6, 1.6], fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, S, 8.5)), dt=0.8)
    skin(m, 'face', G, 3.8, (9, 30.8, -3.8), (11, 34.7, 3.8), frame_face())
    for sd, n in ((-1, 'l'), (1, 'r')):
        path(m, f'tong_{n}', G, 3.5, [(sd * 1.5, 34.5), (sd * 6.4, 40), (sd * 5.8, 48), (sd * 2.8, 51)], [2.6, 2.4, 2.0], 1.5,
             fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, G, 5.5)))
    c = (0, 45)
    skin(m, 'sun', D, 3, (c[0] - 4.2, c[1] - 4.2, -3.4), (c[0] + 4.2, c[1] + 4.2, 3.4),
         fm(lambda sp, f, w, h: sp.diamond((w / 2, h / 2), w / 3, D, 5)), rot=45, pivot=c, glow=True)
    skin(m, 'sun_core', D, 5, (c[0] - 2, c[1] - 2, -4), (c[0] + 2, c[1] + 2, 4), glow=True)

    def rays(sp):
        for k in range(12):
            a = math.radians(k * 30 + 15)
            r1 = 13 if k % 2 == 0 else 10
            sp.line((c[0] + math.cos(a) * 6, c[1] + math.sin(a) * 6), (c[0] + math.cos(a) * r1, c[1] + math.sin(a) * r1), 1.6 if k % 2 == 0 else 1.1, D, 2.5, shade=False)
        for x, y in ((-9, 55), (7, 58), (11, 50), (-12, 47)):
            sp.dot(x, y, D, 4)
    glow_plane(m, 'flare', rays, (c[0] - 15, c[1] - 15, c[0] + 15, c[1] + 15), -1.5)
    return big(m, 25, 1.55, 1.55)


def divine_dawn_staff():
    """暁の灯杖（四回目）: 天球儀。菱の朝日の芯を、金の輪が三つ（正円と左右に傾いた楕円）囲み、輪の上に星の玉が乗る。
    まわりに平面の星の粒"""
    from divine_kit import arc, bar, strapped_grip, pommel, rod, sparkles, skin, gem
    m = M('divine_dawn_staff')
    strapped_grip(m, -14, 1.5)
    pommel(m, -16.6)
    rod(m, 2, 36, bands=(12, 24))
    skin(m, 'stem', G, 3.8, (-1.4, 35.5, -1.4), (1.4, 39.5, 1.4))
    c = (0, 47)
    gem(m, 'sun', c, 7, 3.4, seat=False)
    skin(m, 'sun_core', D, 5, (c[0] - 1.8, c[1] - 1.8, -4), (c[0] + 1.8, c[1] + 1.8, 4), glow=True)
    arc(m, 'ring_0', G, 3.4, c, 9, 0, 360, 14, 1.8, 1.8, 1.5, 1.5)
    planets = []
    for k, rot in enumerate((35, -35)):
        pts = arc(m, f'ring_{k + 1}', G, 3.8, c, 0, 0, 360, 14, 1.4, 1.4, 1.2, 1.2, rx=12, ry=4.5, rot=rot)
        planets.append(pts[3 if k == 0 else 10])
    planets.append((0, c[1] + 9))
    for k, p in enumerate(planets):
        skin(m, f'planet_{k}', S, 7, (p[0] - 1.6, p[1] - 1.6, -1.6), (p[0] + 1.6, p[1] + 1.6, 1.6), rot=45, pivot=p)
    sparkles(m, 'float_stars', [(-13, 58), (12, 59), (-15, 40), (15, 38)], -2)
    return big(m, 37, 1.6, 1.6)


def divine_eyelid_staff():
    """雲の瞼の杖（四回目）: 六枚の翼に包まれた大きな目。白い目に光る虹彩と黒い硝子の瞳、上下の瞼、金の縁の輪。
    翼は上・横・下に二枚ずつ（平面の絵）"""
    from divine_kit import arc, path, strapped_grip, pommel, rod, feather_plane, fan, skin, fm
    m = M('divine_eyelid_staff')
    strapped_grip(m, -14, 1.5)
    pommel(m, -16.6)
    rod(m, 2, 36, bands=(12, 24))
    skin(m, 'stem', G, 3.8, (-1.4, 35.5, -1.4), (1.4, 41.5, 1.4))
    c = (0, 48)
    skin(m, 'eye_white', S, 8.5, (-5.2, 45, -2), (5.2, 51, 2))
    skin(m, 'iris', D, 3, (-2.6, 45.4, -2.3), (2.6, 50.6, 2.3), fm(lambda sp, f, w, h: sp.rect(1, 1, w - 1, h - 1, D, 4.5)), glow=True)
    skin(m, 'pupil', K, 1, (-0.8, 46.4, -2.6), (0.8, 49.6, 2.6))
    for sd, n in ((1, 'up'), (-1, 'down')):
        path(m, f'lid_{n}', S, 6.5 if sd > 0 else 4.5, [(-6.4, c[1] + sd * 1.8), (0, c[1] + sd * 4.4), (6.4, c[1] + sd * 1.8)], [2.6, 2.6], 2.4,
             fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, G, 5)))
    arc(m, 'rim', G, 3.6, c, 8, 0, 360, 14, 1.6, 1.6, 1.4, 1.4)
    feathers = []
    for sd in (-1, 1):
        def ang(a):
            return a if sd > 0 else 180 - a
        feathers += fan((sd * 4, c[1] + 7), 3, 13, -2, ang(70), sd * -14 if sd > 0 else 14, 5, 2.2)
        feathers += fan((sd * 7.5, c[1] + 1), 3, 15, -2, ang(10), -12 if sd > 0 else 12, 5.5, 2.4)
        feathers += fan((sd * 4, c[1] - 7), 3, 11, -2, ang(-60), 12 if sd > 0 else -12, 4.5, 2)
    feather_plane(m, 'wings', feathers, 0.0)
    return big(m, 40, 1.55, 1.55)


def divine_promise_bell_staff():
    """約束の鈴杖（四回目）: 白い石の厚い三日月の両角が上へ伸び、外に金の鎖で鈴が三つずつ連なって下がる。
    三日月の上に金の菱の籠に入った約束の灯が浮く"""
    from divine_kit import arc, path, bar, strapped_grip, pommel, rod, bell3d, sparkles, skin, fm
    m = M('divine_promise_bell_staff')
    strapped_grip(m, -14, 1.5)
    pommel(m, -16.6)
    rod(m, 2, 39, bands=(12, 24))
    c = (0, 48)
    pts = arc(m, 'crescent', S, 6, c, 10, 205, 335, 6, 3.6, 3.6, 1.8, 1.8, fm(lambda sp, f, w, h: sp.rect(0, 0, w, 1, G, 4.5)))
    for sd, n in ((-1, 'l'), (1, 'r')):
        e = pts[0] if sd < 0 else pts[-1]
        path(m, f'horn_{n}', S, 7, [e, (sd * 11.4, 52), (sd * 9.6, 58)], [3.2, 2.4], [1.6, 1.3], fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, S, 9)))
        bar(m, f'arm_{n}', G, 4, (sd * 10.6, 54), (sd * 14, 54), 1.2, 0.8)
        bar(m, f'chain_{n}', G, 4, (sd * 13.6, 54.5), (sd * 13.6, 37), 0.7, 0.45)
        for k, y in enumerate((50, 44.5, 39)):
            bell3d(m, f'bell_{n}{k}', (sd * 13.6, y), 1.5 + k * 0.25)
    lc = (0, 57)
    for k, (a, b) in enumerate((((-4, 57), (0, 61)), ((0, 61), (4, 57)), ((4, 57), (0, 53)), ((0, 53), (-4, 57)))):
        bar(m, f'float_lamp_frame_{k}', G, 4, a, b, 1.2, 1.0)
    skin(m, 'float_lamp', D, 4, (lc[0] - 1.6, lc[1] - 1.6, -1.8), (lc[0] + 1.6, lc[1] + 1.6, 1.8), rot=45, pivot=lc, glow=True)
    sparkles(m, 'float_motes', [(-6, 64), (7, 66), (0, 69)], -1.5)
    return big(m, 36.5, 1.35, 1.35)


def divine_keeper_bell_staff():
    """鐘守の鈴杖（四回目）: 杖の先は割れた白い石の台。その上に鐘楼がまるごと浮かび（床・柱・二段の金の屋根・尖塔・中の鐘）、
    台と鐘楼の間を光の柱（平面）がつなぐ。まわりに石の欠片が浮く"""
    from divine_kit import strapped_grip, pommel, rod, bell3d, bar, glow_plane, skin, frame_face, fm
    m = M('divine_keeper_bell_staff')
    strapped_grip(m, -14, 1.5)
    pommel(m, -16.6)
    rod(m, 2, 32, bands=(12, 24))
    skin(m, 'base', S, 5, (-5.5, 31, -4.5), (5.5, 34, 4.5), frame_face(4, S))
    for k, (x, h, w) in enumerate(((-3.6, 2.6, 2.6), (0.4, 1.4, 3.2), (3.8, 2.2, 2.2))):
        skin(m, f'broken_{k}', S, 6, (x - w / 2, 34, -2), (x + w / 2, 34 + h, 2))
    y0 = 41
    skin(m, 'float_belfry_floor', S, 5.5, (-6, y0, -4.5), (6, y0 + 2.2, 4.5), frame_face(4, S))
    for k, (x, z) in enumerate(((-4.6, -3.2), (4.6, -3.2), (-4.6, 3.2), (4.6, 3.2))):
        skin(m, f'float_belfry_pillar_{k}', S, 6.5, (x - 0.9, y0 + 2.2, z - 0.9), (x + 0.9, y0 + 11, z + 0.9))
    skin(m, 'float_belfry_lintel', S, 6, (-6, y0 + 11, -4.5), (6, y0 + 12.4, 4.5))
    skin(m, 'float_belfry_roof_0', G, 3.6, (-7.6, y0 + 12.4, -5.6), (7.6, y0 + 14.2, 5.6), fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, G, 5.5)))
    skin(m, 'float_belfry_roof_1', G, 4.2, (-5, y0 + 14.2, -3.8), (5, y0 + 16, 3.8))
    bar(m, 'float_belfry_spire', G, 4.6, (0, y0 + 15.6), (0, y0 + 22), 2, 1)
    skin(m, 'float_belfry_hanger', G, 4, (-0.4, y0 + 9.4, -0.4), (0.4, y0 + 11.2, 0.4))
    bell3d(m, 'float_belfry_bell', (0, y0 + 6.6), 2.8)

    def beam(sp):
        sp.poly([(-2.4, 34), (2.4, 34), (1.6, 41.5), (-1.6, 41.5)], D, 1.5, shade=False, tone=lambda x, y: 1.5 + (y - 34) / 7 * 2)
        sp.line((0, 34), (0, 41.5), 0.9, D, 5, shade=False)
    glow_plane(m, 'float_beam', beam, (-3, 33, 3, 42), 0.0)
    for k, (x, y, s) in enumerate(((-9, 38, 2.4), (8.5, 40, 2), (-8, 48, 1.6))):
        skin(m, f'float_chip_{k}', S, 6, (x - s / 2, y - s / 2, -s / 2), (x + s / 2, y + s / 2, s / 2), rot=45, pivot=(x, y))
    return big(m, 30, 1.45, 1.45)


def divine_rewind_dagger():
    """巻き戻りの短剣（五回目）: 鍔は金の文字盤。刃は時計の長針を剣にしたもの: 両刃の白い刃が根元で張り出してから長く鋭く絞られ、
    真ん中に黒い硝子の窓（光の筋）。根元の両脇に針の返しのような鋭い棘。光る秒針が刃を斜めに貫いて前へ突き出す"""
    from divine_kit import arc, bar, path, strapped_grip, pommel, skin, fm
    m = M('divine_rewind_dagger')
    strapped_grip(m, -4, 1.6)
    pommel(m, -6.6)
    c = (0, 8)

    def dial(sp, f, w, h):
        if f not in ('south', 'north'):
            return
        for k in range(12):
            a = math.radians(k * 30)
            r = min(w, h) / 2 - 1.2
            sp.dot(w / 2 + math.cos(a) * r, h / 2 + math.sin(a) * r, G, 2 if k % 3 else 1)
    skin(m, 'dial_face', S, 7.5, (c[0] - 5, c[1] - 5, -1), (c[0] + 5, c[1] + 5, 1), dial)
    arc(m, 'dial_rim', G, 3.4, c, 6.2, 0, 360, 12, 1.8, 1.8, 1.5, 1.5)
    skin(m, 'hub', D, 4, (c[0] - 1.3, c[1] - 1.3, -1.8), (c[0] + 1.3, c[1] + 1.3, 1.8), rot=45, pivot=c, glow=True)
    edge = fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, S, 9.5))
    # 刃の身: 根元が張り出し、先へ長く絞る（左右それぞれ二節の回した板）
    for sd, n in ((-1, 'l'), (1, 'r')):
        path(m, f'blade_{n}', S, 6.5, [(sd * 2.2, 13), (sd * 4.2, 19), (sd * 2.4, 36), (0, 50)], [3.0, 2.6, 2.0], [0.9, 0.85, 0.7], edge, dt=0.6)
        bar(m, f'rim_{n}', G, 4, (sd * 1.5, 14), (sd * 1.2, 34), 0.9, 1.05)
        bar(m, f'barb_{n}', G, 4.2, (sd * 3.8, 21), (sd * 7.6, 17), 1.8, 0.8, fm(lambda sp, f, w, h: sp.rect(0, h - 1, w, h, G, 6)))
        bar(m, f'barb_{n}_tip', G, 5, (sd * 7.2, 17.4), (sd * 9, 14.8), 0.9, 0.6)
    skin(m, 'body', S, 6, (-2.6, 12.4, -0.6), (2.6, 34, 0.6))
    bar(m, 'body_tip', S, 7, (0, 33.5), (0, 47), 2.6, 0.6)
    skin(m, 'window', K, 2.5, (-0.9, 15, -0.8), (0.9, 33, 0.8), fm(lambda sp, f, w, h: sp.rect(w / 2 - 0.5, 1, w / 2 + 0.5, h - 1, D, 3.5)))
    skin(m, 'base_bar', G, 3.4, (-4, 11.8, -1.3), (4, 13.6, 1.3))
    # 秒針: 文字盤の軸から刃を斜めに貫き、鋭く前へ
    bar(m, 'second_hand', D, 3.5, (-2.5, 4), (5.5, 30), 0.8, 1.9, glow=True)
    bar(m, 'second_hand_tip', D, 5, (5.3, 29.4), (6.6, 34), 0.45, 1.9, glow=True)
    return big(m, 1.5, 1.3, 1.3)


def divine_moth_dagger():
    """灯蛾の短剣（五回目）: 虫の体は描かず、灯に寄る蛾の「羽だけ」を光の刃で表す。光の芯の刃の根元から、
    鋭い葉の形の前翅が刃に沿って上へ、短い後翅が下へ開く（白から先へ朝の光に溶ける、金の縁）。根元に菱の宝石、刃の先に灯の炎"""
    from divine_kit import bar, strapped_grip, pommel, sparkles, skin, fm, gem
    from pix3d import Sprite, sprite_planes
    m = M('divine_moth_dagger')
    strapped_grip(m, -4, 4)
    pommel(m, -6.6)
    skin(m, 'guard', G, 3.4, (-4.4, 5.2, -1.6), (4.4, 6.8, 1.6))
    skin(m, 'blade', S, 6.5, (-1.8, 7, -0.7), (1.8, 30, 0.7), fm(lambda sp, f, w, h: sp.rect(w / 2 - 0.5, 0, w / 2 + 0.5, h, D, 4.5)))
    for sd, n in ((-1, 'l'), (1, 'r')):
        bar(m, f'tip_{n}', S, 8, (sd * 1.8, 29.5), (0, 37), 1.6, 0.6)
    skin(m, 'tip_core', S, 8, (-1.2, 29, -0.6), (1.2, 33, 0.6))
    gem(m, 'heart', (0, 8.5), 3.6, 2.2)
    big(m, 4.9, 1.35, 1.35)

    def T(pts):
        return [(x * 1.35, 4.9 + (y - 4.9) * 1.35) for x, y in pts]
    sp = Sprite(-19, 1, 19, 40)

    def wing_tone(y0, y1):
        return lambda x, y: 8.5 - max(0.0, min(1.0, (abs(x) - 2) / 9)) * 3
    for sd in (-1, 1):
        fore = [(sd * 2.2, 9), (sd * 7, 10.5), (sd * 11.5, 16.5), (sd * 13, 25), (sd * 11.5, 29), (sd * 8, 25), (sd * 4.6, 19.5), (sd * 2.4, 15)]
        sp.poly(T(fore), S, 7, tone=wing_tone(9, 27), edge=(G, 4.5))
        sp.line(*T([(sd * 2.8, 11), (sd * 11.4, 26)]), 1.3, G, 5, shade=False)
        sp.poly(T([(sd * 11, 23.5), (sd * 13, 25), (sd * 11.5, 29), (sd * 10.2, 26.5)]), D, 4, shade=False)
        hind = [(sd * 2.2, 8.4), (sd * 6.4, 7.4), (sd * 9.6, 2), (sd * 6.4, 2.6), (sd * 2.2, 5.4)]
        sp.poly(T(hind), S, 6, tone=wing_tone(1, 8), edge=(G, 4))
    sp.shade(light=1.0, dark=1.2, outline=(G, 0))
    sprite_planes(sp, m, 'wings', 0.0)
    skin(m, 'flame', D, 4, (-1.2, 44.8, -1.2), (1.2, 47.2, 1.2), rot=45, pivot=(0, 46), glow=True)
    bar(m, 'flame_tip', D, 5, (0, 46.6), (0, 52), 1.3, 0.6, glow=True)
    sparkles(m, 'float_dust', [(-11, 47), (9.5, 51)], -1)
    return m


# ---------------------------------------------------------------- 弓（握りの中心 (12, 15)）

def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """45 度に回した四角（菱）"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


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
    m.box('grip', S, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 4, 20, 6))
    for n, y in (('u', 19.5), ('d', 9)):
        m.box(f'band_{n}', G, [10, y, -2.1], [14, y + 1.5, 2.1], 4)


PLUME_PAL = {'W': (S, 9), 'w': (S, 7), 's': (S, 5), 'G': (G, 4), 'g': (G, 3)}


def plume_along(pts, t):
    """折れ線の上の点（t = 0..1）"""
    L = [math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]) for i in range(len(pts) - 1)]
    d = t * sum(L)
    for i, l in enumerate(L):
        if d <= l or i == len(L) - 1:
            f = min(d / l, 1)
            return (pts[i][0] + (pts[i + 1][0] - pts[i][0]) * f, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * f)
        d -= l


def plume_grid(arm, feathers, coverts, px=0.5):
    """翼のドット絵を XY の格子に塗る。羽 = (t, 角度, 長さ, 幅)。羽は根元から先へ少し太り、先は尖って金。
    先に並べた羽（弓の先の側）を、後の羽と雨覆いが上から重ねる"""
    quads = []
    for kind, lst in (('f', feathers), ('c', coverts)):
        for i, (t, ang, ln, w) in enumerate(lst):
            b = plume_along(arm, t)
            d = (math.cos(math.radians(ang)), math.sin(math.radians(ang)))
            quads.append((kind, i, (b[0] - d[0], b[1] - d[1]), d, ln + 1.0, w))
    xs, ys = [], []
    for _, _, b, d, ln, w in quads:
        for s in (0, ln):
            for p in (-w * 1.1, w * 1.1):
                xs.append(b[0] + d[0] * s - d[1] * p)
                ys.append(b[1] + d[1] * s + d[0] * p)
    x0, y0 = math.floor(min(xs) / px) * px, math.floor(min(ys) / px) * px
    W, H = int((max(xs) - x0) / px) + 2, int((max(ys) - y0) / px) + 2
    grid = [['.'] * W for _ in range(H)]
    for kind, i, b, d, ln, w in quads:
        for r in range(H):
            for c in range(W):
                vx, vy = x0 + (c + 0.5) * px - b[0], y0 + (r + 0.5) * px - b[1]
                s = vx * d[0] + vy * d[1]
                p = -vx * d[1] + vy * d[0]
                ww = w * (0.9 + 0.6 * max(s, 0) / ln)
                if s < 0 or abs(p) > ww / 2 or s > ln - w * 1.6 * (abs(p) / (ww / 2)) ** 1.3:
                    continue
                if kind == 'c':
                    ch = 'W' if p < ww / 2 - px else 's'
                elif s / ln > 0.86:
                    ch = 'g' if p > ww / 2 - px * 1.2 else 'G'
                elif p > ww / 2 - px * 1.2:
                    ch = 's'
                else:
                    ch = 'Ww'[i % 2]
                grid[r][c] = ch
    return grid, (x0, y0), px


def plume_decal(m, name, grid, origin, px, sign, z):
    """翼の格子を一枚の絵の板にする（sign = -1 は y=15 で鏡）"""
    rows = [[PLUME_PAL[ch] if ch != '.' else None for ch in row] for row in grid[::-1]]
    H, W = len(grid), len(grid[0])
    y0, y1 = origin[1], origin[1] + H * px
    if sign < 0:
        rows = rows[::-1]
        y0, y1 = 30 - y1, 30 - y0
    m.decal(name, rows, [origin[0], y0, z], [origin[0] + W * px, y1, z])


def divine_plume_bow():
    """巡礼の羽弓（伝説）: 弓の先から天使の翼が上下へ大きく開き、弓の丈より高く飛び出す。翼は板のドット絵で、
    白い風切羽が先ほど長く、羽の境に灰の線、羽先だけが金で尖る。根元は白い雨覆いが一段覆う。
    翼の板は弓腕をはさんで前後（z±1.3）に一枚ずつ、奥行きのある二重の翼に見える。
    弓腕は白い骨の節を金の宝石の留めで継ぎ（宝石の芯が朝の光）、握りの背にも宝石。
    弦は暁の光の一本に石の数珠と聖印。矢の通り道（握りの前）は空ける"""
    m = M('divine_plume_bow')
    m.box('grip', G, [10, 9, -2.1], [14, 21, 2.1], grad=(9, 2, 21, 4), wrap=(9, 21))
    for n, y in (('u', 20.75), ('d', 7.5)):
        m.box(f'cap_{n}', S, [9.5, y, -2.5], [14.5, y + 1.75, 2.5], grad=(y, 6, y + 1.75, 8))
    diamond(m, 'riser_jewel', G, (15, 15), 3.5, 1.4, 3, 5)
    m.box('riser_jewel_z', G, [14.4, 14.4, -2.6], [15.6, 15.6, 2.6], 4)
    diamond(m, 'riser_jewel_glow', D, (15, 15), 1.75, 1.55, 4, 5)
    limb = [(12, 21), (13.5, 27.5), (13.25, 34), (11.25, 39.5), (8, 43.5), (4.5, 46.5)]
    arm = [(14, 30), (12.5, 38), (9, 44), (4.5, 47)]
    fe = [(1.0, 118, 18, 2.2), (0.9, 108, 20, 2.3), (0.8, 98, 19, 2.3), (0.7, 88, 17, 2.2), (0.6, 78, 15, 2.1),
          (0.5, 68, 12.5, 2.0), (0.4, 58, 10, 1.9), (0.3, 48, 7.5, 1.8), (0.2, 38, 5.5, 1.7)]
    cv = [(0.95, 105, 4.5, 3.4), (0.78, 88, 4.5, 3.4), (0.6, 70, 4, 3.2), (0.42, 54, 3.5, 3), (0.25, 40, 3, 2.8)]
    grid, org, px = plume_grid(arm, fe, cv)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in limb]
        for k in range(len(pts) - 1):
            a, b = pts[k], pts[k + 1]
            ux, uy = b[0] - a[0], b[1] - a[1]
            L = math.hypot(ux, uy)
            m.seg(f'bone_{n}{k}', S, (a[0] + ux / L * 1.4, a[1] + uy / L * 1.4), (b[0] - ux / L * 1.4, b[1] - uy / L * 1.4),
                  4 - k * 0.4, 1.7, 7, 9, over=0)
        for k in range(1, len(pts)):
            q = pts[k]
            s = 3.4 - k * 0.25
            diamond(m, f'jewel_{n}{k}', G, q, s, 1.2, 3, 5)
            m.box(f'jewel_z_{n}{k}', G, [q[0] - s * 0.3, q[1] - s * 0.3, -s * 0.75], [q[0] + s * 0.3, q[1] + s * 0.3, s * 0.75], 4)
            diamond(m, f'jewel_{n}{k}_glow', D, q, s * 0.45, 1.3, 4, 5)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            plume_decal(m, f'wing_{n}{zn}', grid, org, px, sign, zs * 1.3)
    top, bot = (3.5, 47.5), (3.5, -17.5)
    for key, pull in lib.PULLS:
        if pull == 0:
            m.box(f'string_{key}_glow', D, [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 4)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u_glow', D, mid, top, 1, 0.5, 4, 4, over=0.2)
            m.seg(f'string_{key}_d_glow', D, bot, mid, 1, 0.5, 4, 4, over=0.2)
        for k, y in enumerate((42, 38.5, 35, -5, -8.5, -12)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_bead{k}', S, [q[0] - 0.7, q[1] - 0.6, -0.7], [q[0] + 0.7, q[1] + 0.6, 0.7], 8)
        q = lib.string_at(top, bot, pull, lib.string_t(top, bot, 31))
        m.box(f'string_{key}_seal_cord', G, [q[0] - 0.2, q[1] - 2.25, -0.2], [q[0] + 0.2, q[1], 0.2], 4)
        diamond(m, f'string_{key}_seal', G, (q[0], q[1] - 3.75), 2.75, 0.65, 3, 5)
        diamond(m, f'string_{key}_seal_glow', D, (q[0], q[1] - 3.75), 1.25, 0.8, 4, 5)
    return m


def divine_mist_bow():
    """霞鳥の弓（伝説）: 弓腕そのものが二筋の霞の流れ。白い筋と灰の筋が前後（奥行き）に入れ替わりながらねじれ合い、
    根元は太く、先へ細る。筋が交わる三か所を金の帯が束ね、帯の正面に暁の光の珠。上の先で二筋は霞鳥の頭になり
    （金の嘴が前へ、冠羽、光る目）、下の先は三枚の長い尾羽にほどける（羽先は金）。握りは白い石に金の縁。
    弦には金の糸の結びが並び、小さな鈴が上下に一つずつ下がる"""
    m = M('divine_mist_bow')
    m.box('grip', S, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 5, 20.5, 7), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'cap_{n}', G, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (14.5, 27), (15, 33), (13.25, 38.5), (10, 43), (6, 46), (3.5, 47.5)]
    fine = []
    for i in range(len(arm) - 1):
        for t in (0, 0.5):
            a, b = arm[i], arm[i + 1]
            fine.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    fine.append(arm[-1])
    nr = lib.line_normals(fine)
    N = len(fine)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        for s_, sn, t0, t1 in ((1, 'a', 8, 9), (-1, 'b', 5, 7)):
            for k in range(N - 1):
                amp0 = 1.6 * (1 - k / N * 0.55)
                amp1 = 1.6 * (1 - (k + 1) / N * 0.55)
                w0 = amp0 * math.sin(k * 0.9) * s_
                w1 = amp1 * math.sin((k + 1) * 0.9) * s_
                a = (fine[k][0] + nr[k][0] * w0, fine[k][1] + nr[k][1] * w0)
                b = (fine[k + 1][0] + nr[k + 1][0] * w1, fine[k + 1][1] + nr[k + 1][1] * w1)
                m.seg(f'strand_{n}{sn}{k}', S, P(a), P(b), 2.1 - k * 0.11, 0.85 - k * 0.025, t0, t1,
                      over=0.35, zc=1.05 * math.cos(k * 0.9 + 0.45) * s_)
        for j, k in enumerate((2, 5, 9)):
            q = P(fine[k])
            r = 1.9 - j * 0.25
            m.box(f'band_{n}{j}', G, [q[0] - r, q[1] - 0.55, -r], [q[0] + r, q[1] + 0.55, r], 4)
            m.box(f'band_rim_{n}{j}', G, [q[0] - r - 0.2, q[1] - 0.2, -r - 0.2], [q[0] + r + 0.2, q[1] + 0.2, r + 0.2], 3)
            diamond(m, f'band_gem_{n}{j}_glow', D, (q[0] + r - 0.2, q[1]), 1.1 - j * 0.15, 0.6, 4, 5)
    # 上の先: 霞鳥の頭
    m.box('head', S, [1.25, 46.75, -1.6], [5.25, 50.25, 1.6], grad=(46.75, 6, 50.25, 8))
    m.seg('beak', G, (5.25, 48.75), (8.5, 47.75), 1.6, 0.9, 3, 5, tip=True)
    m.box('eye_glow', D, [3.75, 48.5, -1.7], [4.5, 49.25, 1.7], 4)
    m.chain('crest', S, [(1.75, 50.25), (0.25, 52.25), (-1.75, 53)], [1.25, 1], 0.6, 7, 9, tip=True)
    m.seg('crest_tip', G, (-1.75, 53), (-3, 53.25), 0.8, 0.5, 4, 5, tip=True)
    # 下の先: 三枚の尾羽
    for k, (e, w) in enumerate((((1.5, -27), 2.25), ((4.5, -28.5), 2.5), ((7.5, -26.5), 2.25))):
        m.seg(f'tail_{k}', S, (3.5 + (k - 1) * 0.75, -16.5), e, w, 0.55, 7, 9, tip=True)
        m.seg(f'tail_tip_{k}', G, e, (e[0] + (k - 1) * 0.5, e[1] - 1.5), w * 0.8, 0.5, 4, 5, tip=True)
    top, bot = (3, 47), (3, -16.75)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((40, 36, 32, -2, -6, -10)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_thread{k}', G, [q[0] - 0.65, q[1] - 0.3, -0.65], [q[0] + 0.65, q[1] + 0.3, 0.65], 4)
        for k, y in enumerate((28, 1.5)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_chime{k}', G, [q[0] - 0.75, q[1] - 1.75, -0.75], [q[0] + 0.75, q[1] - 0.25, 0.75], 4)
            m.box(f'string_{key}_chime{k}_glow', D, [q[0] - 0.3, q[1] - 2.25, -0.3], [q[0] + 0.3, q[1] - 1.75, 0.3], 4)
    return m


ALL = [divine_sky_sword, divine_warden_blade, divine_bell_hammer, divine_ash_hammer, divine_dawn_staff, divine_eyelid_staff,
       divine_promise_bell_staff, divine_keeper_bell_staff, divine_rewind_dagger, divine_moth_dagger, divine_plume_bow, divine_mist_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
