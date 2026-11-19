# -*- coding: utf-8 -*-
"""塔（look5・個性版）。天と淵のあいだの塔の主たち「側」の武器 6 品（docs/design/03 の塔の装備）。
色: 白の大理石と金（天）と、黒い石と紫の割れ目（淵）。つなぎ目に天秤の印の光（2026-11-20 に白黒へ統一。全品これ）。
天秤・目・落ちる月・引力の環・追う印・合わせた掌から一品ずつ形を立てる。
- 柄: 夜の紫の握りに金の輪、柄頭は黒い石の四角錐。
- 光: あいだの大剣 = 刃の真ん中の境の線、側の目の弓 = 目、落ちる月の杖 = 月、引き合いの鎚 = 環の芯、追う印の短剣 = 印、合掌の鈴杖 = 掌のあいだ。
"""
import math
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


def tower_balance_sword():
    """あいだの大剣（伝説・神器）: 根元の金の天秤の梁（幅を詰めた）に白と黒の小さな皿、刃は白（天）と黒（淵）の半分ずつ。
    金の帯は四か所（根元・天秤の台の上下・切っ先の手前）。中ほどの天秤の印の台（見せ所の光る菱）に金の小さな腕と腕の先の皿。切っ先の金の尖塔"""
    from paintbox import skin
    m = _wM('tower_balance_sword')
    # 柄と柄頭
    skin(m, 'grip', WN, 2, [-2, -7, -2], [2, 6, 2], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-6.2, -4.4), (3.6, 5.4))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.5, y0, -2.5], [2.5, y1, 2.5])
    skin(m, 'pommel', WB, 3, [-2.5, -9.5, -2.5], [2.5, -7, 2.5])
    skin(m, 'pommel_rim', WG, 4, [-2.9, -7.6, -2.9], [2.9, -6.5, 2.9])
    m.seg('pommel_tip', WB, (0, -9.5), (0, -12), 2.5, 1.25, 2, 3, tip=True)
    # 鍔: 首、幅を詰めた金の天秤の梁（±14）と両端の柱
    skin(m, 'neck', WN, 4, [-2.4, 5.5, -2.4], [2.4, 11.6, 2.4])
    skin(m, 'beam', WG, 4, [-14, 7.6, -3], [14, 12, 3])
    skin(m, 'pivot', WM, 7, [-3.2, 11, -3.2], [3.2, 15, 3.2])
    for sd, n in ((-1, 'l'), (1, 'r')):
        skin(m, f'beam_cap_{n}', WG, 6, [sd * 13.2 - 1.6, 6.4, -3.6], [sd * 13.2 + 1.6, 13.2, 3.6])
        skin(m, f'beam_hook_{n}', WG, 4, [sd * 13 - 1, 2.4, -1.4], [sd * 13 + 1, 6.8, 1.4])
        x = sd * 9
        skin(m, f'chain_{n}', WG, 3, [x - 0.9, 5.2, -0.9], [x + 0.9, 7.8, 0.9])
        skin(m, f'chain_b_{n}', WG, 3, [x + 2.2 * sd - 0.9, 5.2, -0.9], [x + 2.2 * sd + 0.9, 7.8, 0.9])
        wm, wt = (WM, 8) if sd < 0 else (WB, 2)
        skin(m, f'pan_{n}', WG, 4, [x - 5, 1.2, -3.4], [x + 5, 4.8, 3.4])
        skin(m, f'pan_rim_{n}', WG, 6, [x - 5.8, 3.8, -4.0], [x + 5.8, 5.6, 4.0])
        for k, dx in enumerate((-2.6, 2.6)):
            skin(m, f'pan_stud_{n}{k}', WG, 5, [x + dx - 0.9, 2.2, 3.2], [x + dx + 0.9, 3.6, 4.4])
        skin(m, f'weight_{n}', wm, wt, [x - 3, 4.8, -2.4], [x + 3, 10.2, 2.4])
        skin(m, f'weight_cap_{n}', WG, 4, [x - 2, 10.2, -2], [x + 2, 11.6, 2])
    # 刃: 五段。(y0, y1, 半幅, 半厚)。白は -x、黒は +x
    segs = [(11.5, 20, 5.3, 3.0), (20, 34, 3.3, 2.8), (34, 46, 4.6, 3.0), (46, 52, 3.3, 2.7), (52, 58, 2.5, 2.4)]
    for k, (y0, y1, hw, d) in enumerate(segs):
        skin(m, f'b{k}_w', WM, 6, [-hw, y0, -d], [0, y1, d], paint=_bs_white)
        skin(m, f'b{k}_b', WB, 2, [0, y0, -d], [hw, y1, d], paint=_bs_black)
        if k < 4:
            skin(m, f'lip_{k}', WG, 5, [-hw - 0.6, y1 - 1.4, -d - 0.5], [hw + 0.6, y1, d + 0.5])
    # 中ほどの金の襟と、天秤の印の台（白と黒の境の正面に光る菱）
    skin(m, 'collar', WG, 4, [-5.4, 38.4, -3.6], [5.4, 41.6, 3.6])
    skin(m, 'plinth', WG, 4, [-4.6, 35.4, 2.8], [4.6, 44.6, 5.0])
    skin(m, 'plinth_top', WG, 6, [-5.2, 43.6, 2.6], [5.2, 45.2, 5.6])
    diamond(m, 'libra_gem', WL, (0, 40), 5.4, 1.6, 3, 4, zc=5.4)
    # 天秤の腕: 台の左右に金の小さな腕と、腕の先に下がる皿
    for sd, n in ((-1, 'l'), (1, 'r')):
        skin(m, f'libra_arm_{n}', WG, 4, [min(sd * 4.2, sd * 7.2), 39.4, -1.2], [max(sd * 4.2, sd * 7.2), 40.8, 1.2])
        x = sd * 6.8
        skin(m, f'libra_cup_{n}', WG, 4, [x - 1.2, 36.8, -1.6], [x + 1.2, 39.4, 1.6])
        skin(m, f'libra_cup_rim_{n}', WG, 6, [x - 1.8, 36.6, -2.2], [x + 1.8, 37.4, 2.2])
    # 切っ先: 白と黒の二つの先、金の尖塔（三節）と上の印の菱。両脇に金の角
    m.seg('tip_w', WM, (-1.4, 58), (-0.2, 63.5), 2.6, 2.2, 7, 8, tip=True)
    m.seg('tip_b', WB, (1.4, 58), (0.2, 63.5), 2.6, 2.2, 2, 3, tip=True)
    m.chain('spire', WG, [(0, 58), (0, 62), (0, 65.2), (0, 67)], [2.6, 2.0, 1.4], [2.0, 1.6, 1.2], 4, 5, tip=True)
    diamond(m, 'spire_gem', WL, (0, 66.4), 3.0, 1.4, 4, 5, zc=0.0)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'horn_{n}', WG, [(sd * 2.2, 50), (sd * 4.4, 54), (sd * 4.8, 58.5)], [1.8, 1.4], [1.4, 1.2], 4, 5, tip=True)
    return m


def _hm_lip(sp, face, w, h):
    """石の段の前と後ろの面に、上の縁の金の一画素（大剣の縁金と同じ）"""
    if face in ('south', 'north'):
        sp.rect(0, h - 1, w, h, WG, 5, shade=False)


def _hm_eye(x0, w0, y0, cy):
    """頭の芯の前と後ろの面の印の目: 外の二単位の金の輪、黒い瞳の輪。中心の台は別の板（天秤の印の台と同じ作り）。
    x0 は面の左端の x、w0 は面の幅。north は左右が反対（絵の向きが反対）"""
    def paint(sp, face, w, h):
        if face not in ('south', 'north'):
            return
        for i in range(w):
            for j in range(h):
                x = x0 + i + 0.5 if face == 'south' else x0 + w0 - i - 0.5
                y = y0 + j + 0.5
                r = math.hypot(x, y - cy)
                if 3.0 <= r < 5.0:
                    sp.rect(i, j, i + 1, j + 1, WG, 5, shade=False)
                elif 2.0 <= r < 3.0:
                    sp.rect(i, j, i + 1, j + 1, WB, 1, shade=False)
    return paint


def tower_pull_hammer():
    """引き合いの鎚（伝説・神器）: 柄の上に横長の大きな頭。頭は四段ずつ（頭の上下に計八段）の白い大理石（天）と黒い石（淵）の石積みが
    左右へ大きく張り出し、両端の平らな面が打つ面。前と後ろの中央に印の目（金の二重の輪）と、大剣と同じ天秤の印の台（金の四角い台に光る菱）。
    頭の上と下に太く短い金の角が反り、先に光る菱。"""
    from paintbox import skin
    LAY = [(27, 31.5, 4, 9, 5.5), (31.5, 36, 5, 10, 6.0), (36, 40.5, 5, 10, 6.0), (40.5, 45, 4, 9, 5.2)]
    m = _wM('tower_pull_hammer')
    # 柄と柄頭（大剣の柄と同じ夜の紫の帯、金の輪二つ、黒い石の柄頭）
    skin(m, 'grip', WN, 2, [-1.5, -6, -1.5], [1.5, 5, 1.5], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-5.6, -3.6), (2.6, 4.6))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.1, y0, -2.1], [2.1, y1, 2.1])
    skin(m, 'pommel', WB, 3, [-2.5, -8.5, -2.5], [2.5, -6, 2.5])
    skin(m, 'pommel_rim', WG, 4, [-2.9, -6.8, -2.9], [2.9, -5.7, 2.9])
    m.seg('pommel_tip', WB, (0, -8.5), (0, -11), 2.5, 1.25, 2, 3, tip=True)
    # 柄の上（金の帯二つ）
    skin(m, 'rod', WN, 2, [-1.25, 5, -1.25], [1.25, 27, 1.25], paint=_bs_bands)
    for k, y in enumerate((12, 20)):
        skin(m, f'rod_band_{k}', WG, 5, [-1.7, y, -1.7], [1.7, y + 2, 1.7])
    # 頭の芯: 左（天）は白の大理石、右（淵）は黒い石。前と後ろに印の目
    skin(m, 'core_l', WM, 4, [-6, 27, -4.5], [0, 45, 4.5], paint=_hm_eye(-6, 6, 27, 36))
    skin(m, 'core_r', WB, 2, [0, 27, -4.5], [6, 45, 4.5], paint=_hm_eye(0, 6, 27, 36))
    # 天秤の印の台（大剣の台と同じ作り）: 金の四角い台に光る菱（前と後ろ）
    for sd, n in ((1, 'f'), (-1, 'b')):
        z0, z1 = (4.5, 5.4) if sd > 0 else (-5.4, -4.5)
        skin(m, f'plinth_{n}', WG, 4, [-2.4, 33.6, z0], [2.4, 38.4, z1])
        skin(m, f'plinth_top_{n}', WG, 6, [-2.8, 38.2, z0 - 0.2], [2.8, 39.0, z1 + 0.2])
        skin(m, f'plinth_bot_{n}', WG, 6, [-2.8, 33.0, z0 - 0.2], [2.8, 33.8, z1 + 0.2])
        diamond(m, f'eye_gem_{n}_glow', WL, (0, 36), 3.6, 1.2, 4, 5, zc=sd * 5.6)
    # 四段の石積みを左右へ大きく張り出す（外の面が打つ面）。段ごとに上の金の縁と、内側の金の縁、前後の金の帯
    for sd, n in ((-1, 'l'), (1, 'r')):
        mat, t = (WM, 6) if sd < 0 else (WB, 2)
        for k, (y0, y1, xi, xo, z) in enumerate(LAY):
            a, b = sorted((sd * xi, sd * xo))
            skin(m, f'lay_{n}{k}', mat, t, [a, y0, -z], [b, y1, z], paint=_hm_lip)
            lo, hi = (a, a + 0.8) if sd < 0 else (b - 0.8, b)
            m.box(f'lay_rim_{n}{k}', WG, [lo, y0 + 0.6, -z - 0.3], [hi, y1 - 0.6, z + 0.3])
            mid = (y0 + y1) / 2
            m.box(f'band_{n}{k}', WG, [min(a, b) + 1, mid - 0.5, z], [max(a, b) - 1, mid + 0.5, z + 0.6], 3)
            m.box(f'bandb_{n}{k}', WG, [min(a, b) + 1, mid - 0.5, -z - 0.6], [max(a, b) - 1, mid + 0.5, -z], 3)
        a, b = sorted((sd * 6, sd * 9))
        skin(m, f'cap_top_{n}', WG, 5, [a, 45, -3], [b, 46, 3])
        a, b = sorted((sd * 4, sd * 9))
        skin(m, f'cap_bot_{n}', WG, 5, [a, 26, -4.6], [b, 27, 4.6])
        # 太く短い金の角（上と下）。先に光る菱（大剣の尖塔の先と同じ WL の菱）
        m.chain(f'horn_up_{n}', WG, [(sd * 6, 45), (sd * 8.6, 48.6), (sd * 8.2, 52.2)], [3.6, 2.6], [2.6, 2.0], 5, 3, tip=True)
        diamond(m, f'horn_up_gem_{n}_glow', WL, (sd * 8.2, 54.0), 2.6, 1.2, 4, 5)
        m.chain(f'horn_dn_{n}', WG, [(sd * 6, 27), (sd * 8.6, 23.4), (sd * 8.2, 19.8)], [3.6, 2.6], [2.6, 2.0], 5, 3, tip=True)
        diamond(m, f'horn_dn_gem_{n}_glow', WL, (sd * 8.2, 18.0), 2.2, 1.0, 4, 5)
    return m


def _ms_white(sp, face, w, h):
    """白い石（天）: 大理石の筋を一本（二画素）"""
    if face in ('south', 'north'):
        sp.line((w * 0.3, 0), (w * 0.55, h), 2.0, WM, 9, shade=False)


def _ms_black(sp, face, w, h):
    """黒い石（淵）: 紫の割れ目を一本（二画素）"""
    if face in ('south', 'north'):
        sp.line((w * 0.2, h * 0.9), (w * 0.6, h * 0.35), 1.8, WC, 4, shade=False)
        sp.line((w * 0.6, h * 0.35), (w * 0.85, h * 0.05), 1.8, WC, 4, shade=False)


def _ms_claw(m, name, pts, widths, depth, sd):
    """爪（太い節の点の列）。白は -x、黒は +x"""
    mat = WM if sd < 0 else WB
    n = len(pts) - 1
    for k in range(n):
        a, b = pts[k], pts[k + 1]
        m.seg(f'{name}_{k}', mat, a, b, widths[k], depth, 3 + 3 * k / n, 3 + 3 * (k + 1) / n, over=0.5,
              tip=(k == n - 1))


def tower_moon_staff():
    """落ちる月の杖（伝説・神器）: 白と黒の石の軸の上で、金の襟から白と黒の太い爪が左右へ大きく張り出し、
    頂で反って月の籠を作る。籠の中に紫の割れ目の月（大きな菱）が浮き、金の四角い台に天秤の印の光る菱を前に置く。
    籠の外に月の欠片が六つ落ちる（この職だけ、float_）。頂は金の尖塔と天秤の印の光る菱"""
    from paintbox import skin
    m = _wM('tower_moon_staff')
    # 柄と柄頭（大剣と同じ夜の紫の帯、金の輪二つ、黒い石の柄頭）
    skin(m, 'grip', WN, 2, [-1.6, -14, -1.6], [1.6, 1.5, 1.6], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-12.6, -10.6), (-0.8, 1.2))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.3, y0, -2.3], [2.3, y1, 2.3])
    skin(m, 'pommel', WB, 3, [-2.6, -16.5, -2.6], [2.6, -14, 2.6])
    skin(m, 'pommel_rim', WG, 4, [-3.0, -14.8, -3.0], [3.0, -13.7, 3.0])
    m.seg('pommel_tip', WB, (0, -16.5), (0, -19), 2.5, 1.25, 2, 3, tip=True)
    # 石の軸（段を重ね、白は -x、黒は +x。黒の側に紫の割れ目）。段の継ぎ目に金の縁
    spans = [(1.5, 10, 1.6), (10, 14, 1.9), (14, 26, 1.5), (26, 30, 1.9), (30, 38, 1.6)]
    for k, (y0, y1, hw) in enumerate(spans):
        skin(m, f'shaft_{k}_w', WM, 6, [-hw, y0, -hw], [0, y1, hw], paint=_ms_white)
        skin(m, f'shaft_{k}_b', WB, 2, [0, y0, -hw], [hw, y1, hw], paint=_ms_black)
        if k in (1, 3):
            skin(m, f'shaft_rim_{k}', WG, 5, [-hw - 0.7, y0 - 0.8, -hw - 0.7], [hw + 0.7, y0 + 0.8, hw + 0.7])
    # 金の襟（爪の根元）
    skin(m, 'collar', WG, 4, [-3.6, 38, -3.6], [3.6, 41, 3.6])
    # 白と黒の太い爪（左右へ張り出し、頂で反る）
    left = [(-2.4, 40.5), (-8.2, 43.4), (-13.2, 48.0), (-14.8, 54.0), (-12.4, 60.0), (-6.4, 64.0), (-1.6, 65.6)]
    right = [(-x, y) for x, y in left]
    widths = [6.2, 5.8, 5.2, 4.6, 4.0, 3.4]
    _ms_claw(m, 'claw_l', left, widths, 3.6, -1)
    _ms_claw(m, 'claw_r', right, widths, 3.6, 1)
    # 籠の中の月（紫の割れ目の大きな菱）と印の目（淵の黒の瞳）
    diamond(m, 'moon', WC, (0, 50), 12.5, 3.6, 2, 4, zc=0.0)
    diamond(m, 'moon_pupil', WN, (0, 50), 5.0, 1.8, 2, 4, zc=2.8)
    # 天秤の印の台: 金の四角い台に天秤の印の光る菱（籠の中の前に一つ）
    skin(m, 'plinth', WG, 4, [-3.4, 46.4, 3.0], [3.4, 53.6, 5.2])
    skin(m, 'plinth_top', WG, 6, [-3.8, 53.2, 2.8], [3.8, 54.2, 5.4])
    diamond(m, 'libra_gem_glow', WL, (0, 50), 5.0, 1.6, 4, 5, zc=5.2)
    m.seg('moon_chain', WG, (0, 64.6), (0, 56.6), 1.4, 0.9, 4, 4, over=0.5)
    # 尖塔と頂の光る菱（天秤の印の光）
    m.chain('spire', WG, [(0, 64), (0, 68.5), (0, 72)], [2.6, 1.6], [2.0, 1.2], 4, 5, tip=True)
    diamond(m, 'spire_gem_glow', WL, (0, 73.8), 3.6, 1.5, 4, 5, zc=0.0)
    # 籠の外へ落ちる月の欠片（この職だけ浮く部品）
    shards = [(-21.0, 40.0, 3.6, WM), (-23.6, 33.0, 2.8, WM), (-19.6, 27.6, 2.2, WC),
              (21.0, 40.0, 3.6, WB), (23.6, 33.0, 2.8, WB), (19.6, 27.6, 2.2, WC)]
    for k, (x, y, s, mat) in enumerate(shards):
        diamond(m, f'float_shard_{k}', mat, (x, y), s, 1.4, 2, 4, zc=0.0)
    return m


def _dg_at(pts, vals, y):
    """折れ線の各点の値 vals を、高さ y で直線に読む（pts の y は増える順）"""
    for k in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[k], pts[k + 1]
        if y0 <= y <= y1:
            s = (y - y0) / (y1 - y0)
            return vals[k] + (vals[k + 1] - vals[k]) * s
    return vals[-1]


def tower_sigil_dagger():
    """追う印の短剣（塔・暗殺者の清書 二回目）: 一本の曲線の刃（節をつないで滑らかに反る）が根元の幅 7 から先へ細り、
    黒（淵）の背の側へ大きく鉤に返る。白（天）と黒（淵）を左右で割る。黒い背に石の鰭を四枚（縁の金つき）、
    背の割れ目に紫の太い線が一本。鍔は真鍮の小さな輪、刃元の金の台に天秤の印の光る菱。柄は夜の紫の帯。先まで 36 以下"""
    from paintbox import skin
    m = _wM('tower_sigil_dagger')
    # 柄と柄頭
    skin(m, 'grip', WN, 2, [-1.8, -4, -1.8], [1.8, 3.6, 1.8], paint=_bs_bands)
    skin(m, 'grip_ring', WG, 4, [-2.2, -1.6, -2.2], [2.2, 0.0, 2.2])
    skin(m, 'pommel', WB, 3, [-2.4, -6.4, -2.4], [2.4, -4, 2.4])
    skin(m, 'pommel_rim', WG, 4, [-2.8, -4.6, -2.8], [2.8, -3.6, 2.8])
    m.seg('pommel_tip', WB, (0, -6.4), (0, -8.8), 2.4, 1.2, 2, 3, tip=True)
    # 鍔: 小さな真鍮の四角い輪
    gx, gy, h, t = 0.0, 4.6, 2.6, 1.0
    skin(m, 'guard_l', WG, 4, [gx - h - t, gy - h - t, -1.2], [gx - h, gy + h + t, 1.2])
    skin(m, 'guard_r', WG, 4, [gx + h, gy - h - t, -1.2], [gx + h + t, gy + h + t, 1.2])
    skin(m, 'guard_b', WG, 4, [gx - h, gy - h - t, -1.2], [gx + h, gy - h, 1.2])
    skin(m, 'guard_t', WG, 4, [gx - h, gy + h, -1.2], [gx + h, gy + h + t, 1.2])
    # 刃の中心線（節の点）と幅・奥行き。根元 7 から先へ細る。先は背の側へ返る鉤
    P = [(0, 6), (0.3, 12), (1.0, 18), (2.6, 23.5), (4.8, 27.5), (6.8, 30.6), (7.6, 33.2), (6.8, 35.2), (4.6, 35.8), (2.6, 34.8)]
    W = [7.0, 6.4, 5.8, 5.0, 4.2, 3.4, 2.6, 2.0, 1.5, 1.2]
    D = [1.5, 1.4, 1.4, 1.3, 1.2, 1.1, 1.0, 0.9, 0.8, 0.7]
    pw = [(x - w / 4, y) for (x, y), w in zip(P, W)]
    pk = [(x + w / 4, y) for (x, y), w in zip(P, W)]
    m.chain('blade_w', WM, pw, [w / 2 for w in W], D, 6, 8, tip=True, zc=0.0)
    m.chain('blade_k', WB, pk, [w / 2 for w in W], D, 2, 3, tip=True, zc=0.0)
    # 背の割れ目: 紫の太い線が一本（黒い側の中ほど）
    pc = [(x + w * 0.2, y) for (x, y), w in zip(P[2:8], W[2:8])]
    m.chain('crack', WC, pc, 0.9, 0.6, 4, 5, tip=False, zc=0.0)
    # 黒い背の石の鰭: 四枚（一段明るい黒。縁に金）
    ys = (11.0, 16.5, 22.0, 27.5)
    for k, (y, ph, pwd) in enumerate(zip(ys, (5.2, 5.4, 5.2, 4.8), (4.0, 4.0, 3.6, 3.2))):
        xc = _dg_at(P, [x for x, _ in P], y)
        hw = _dg_at(P, [w / 2 for w in W], y)
        x0 = xc + hw - 0.6
        skin(m, f'fin_{k}', WB, 5, [x0, y - ph / 2, -1.0], [x0 + pwd, y + ph / 2, 1.0],
             paint=lambda sp, face, w, h: sp.rect(0, h - 1, w, h, WC, 3, shade=False) if face in ('south', 'north') else None)
        skin(m, f'fin_rim_{k}', WG, 4, [x0 + pwd - 0.4, y - ph / 2 - 0.3, -1.2], [x0 + pwd + 0.3, y + ph / 2 + 0.3, 1.2])
        skin(m, f'fin_tooth_{k}', WB, 5, [x0 + pwd * 0.5, y + ph / 2, -0.7], [x0 + pwd * 0.5 + 1.2, y + ph / 2 + 1.4, 0.7])
    # 白い側: 小さな縁の段が二枚
    for k, y in enumerate((14.0, 25.0)):
        xc = _dg_at(P, [x for x, _ in P], y)
        hw = _dg_at(P, [w / 2 for w in W], y)
        x1 = xc - hw + 0.6
        skin(m, f'scale_{k}', WM, 8, [x1 - 2.0, y - 1.5, -1.0], [x1, y + 1.5, 1.0])
    # 刃元の金の台と、天秤の印の光る菱（白黒の境）
    skin(m, 'libra_seat', WG, 4, [-2.8, 7.4, 1.0], [2.8, 12.6, 2.2])
    diamond(m, 'libra_gem_glow', WL, (0, 10.0), 4.4, 1.3, 4, 5, zc=2.4)
    return m


def _cb_vein(sp, face, w, h):
    """白い石（天）: 灰の筋を二画素で一本（斜め）"""
    if face in ('south', 'north'):
        sp.line((w * 0.2, h * 0.1), (w * 0.8, h * 0.9), 2.0, WM, 2, shade=False)


def _cb_crack(sp, face, w, h):
    """黒い石（淵）: 紫の割れ目を二画素で一本（折れ線）"""
    if face in ('south', 'north'):
        pts = [(w * 0.2, h * 0.95), (w * 0.55, h * 0.55), (w * 0.35, h * 0.3), (w * 0.85, h * 0.05)]
        for a, b in zip(pts, pts[1:]):
            sp.line(a, b, 2.0, WC, 4, shade=False)


def _cb_palm(mat, vein, t):
    """合わせた掌の正面: 指の境を一本（二画素。中ほど）と、石の筋を一本"""
    def paint(sp, face, w, h):
        if face in ('south', 'north') and w >= 4 and h >= 4:
            j = int(h * 0.5)
            sp.rect(0, j, w, j + 2, mat, t, shade=False)
            sp.line((w * 0.25, h * 0.85), (w * 0.7, h * 0.15), 1.6, vein, t + 3, shade=False)
    return paint


def _cb_bell(m, name, cx, top, lip, s=1.0):
    """鈴: 真鍮の冠と縁、側の色（白か黒）の胴、下がる舌（天秤の光）。top は冠の上の面"""
    from paintbox import skin

    def w(v):
        return v * s
    skin(m, f'{name}_crown', WG, 5, [cx - w(1.1), top - 1.6, -w(1.1)], [cx + w(1.1), top, w(1.1)])
    skin(m, f'{name}_shoulder', lip, 4, [cx - w(2.0), top - 3.8, -w(1.8)], [cx + w(2.0), top - 1.6, w(1.8)], _cb_crack if lip == WB else _cb_vein)
    skin(m, f'{name}_body', lip, 4, [cx - w(2.8), top - 7.0, -w(2.4)], [cx + w(2.8), top - 3.8, w(2.4)])
    skin(m, f'{name}_lip', WG, 5, [cx - w(3.4), top - 7.8, -w(2.9)], [cx + w(3.4), top - 7.0, w(2.9)])
    skin(m, f'{name}_tongue', WL, 5, [cx - 0.8, top - 9.8, -0.8], [cx + 0.8, top - 7.6, 0.8])


def tower_clap_bell_staff():
    """合掌の鈴杖（伝説・神器）: 柄の先で白と黒の二つの石の掌が合わさり、指先が上で合う。
    掌のあいだに金の天秤の印の台（光る菱を前に置く）。掌の下で柄が白い鉤に曲がり、鉤の先と外から鈴が三つ下へ吊る。先の尖りは光る菱"""
    from paintbox import skin
    m = _wM('tower_clap_bell_staff')
    # 柄頭（石・金の蓋・尖り）と握り（白と黒を左右で割る。紫の帯・金の輪）
    skin(m, 'pommel', WB, 4, [-2.2, -18.0, -2.2], [2.2, -14.0, 2.2])
    skin(m, 'pommel_cap', WG, 4, [-2.6, -18.8, -2.6], [2.6, -17.6, 2.6])
    m.seg('pommel_tip', WB, (0, -18.8), (0, -21.6), 2.4, 1.2, 4, 5, tip=True)
    skin(m, 'grip_l', WM, 4, [-1.8, -14.0, -1.8], [0, 1.5, 1.8], _cb_vein)
    skin(m, 'grip_r', WB, 4, [0, -14.0, -1.8], [1.8, 1.5, 1.8], _cb_crack)
    skin(m, 'grip_ring', WG, 4, [-2.2, -10.6, -2.2], [2.2, -8.6, 2.2])
    skin(m, 'grip_wrap_0', WN, 4, [-2.0, -6.0, -2.0], [2.0, -4.6, 2.0])
    skin(m, 'grip_wrap_1', WN, 4, [-2.0, -2.2, -2.0], [2.0, -0.8, 2.0])
    # 柄（白は -x、黒は +x）と、紫の帯、金の輪
    skin(m, 'rod_l', WM, 5, [-1.25, 1.5, -1.25], [0, 38.0, 1.25], _cb_vein)
    skin(m, 'rod_r', WB, 4, [0, 1.5, -1.25], [1.25, 38.0, 1.25], _cb_crack)
    skin(m, 'rod_band', WG, 4, [-1.9, 9.4, -1.9], [1.9, 11.0, 1.9])
    skin(m, 'rod_wrap', WN, 4, [-1.8, 24.0, -1.8], [1.8, 25.4, 1.8])
    # 手首の金の帯と、掌（合わせた石の掌。白は -x、黒は +x。厚く大きく）
    skin(m, 'wrist', WG, 4, [-2.4, 37.4, -2.4], [2.4, 39.0, 2.4])
    skin(m, 'palm_l', WM, 6, [-4.6, 39.0, -2.8], [0, 50.0, 2.8], _cb_palm(WM, WM, 2))
    skin(m, 'palm_r', WB, 4, [0, 39.0, -2.8], [4.6, 50.0, 2.8], _cb_palm(WB, WC, 4))
    skin(m, 'palm_top_l', WM, 6, [-4.2, 50.0, -2.4], [0, 58.0, 2.4], _cb_palm(WM, WM, 2))
    skin(m, 'palm_top_r', WB, 4, [0, 50.0, -2.4], [4.2, 58.0, 2.4], _cb_palm(WB, WC, 4))
    skin(m, 'tip_l', WM, 6, [-2.6, 58.0, -1.6], [0, 63.0, 1.6])
    skin(m, 'tip_r', WB, 4, [0, 58.0, -1.6], [2.6, 63.0, 1.6])
    skin(m, 'thumb_l', WM, 6, [-5.0, 41.0, 2.8], [-2.4, 47.0, 4.4])
    skin(m, 'thumb_r', WB, 4, [2.4, 41.0, 2.8], [5.0, 47.0, 4.4])
    skin(m, 'thumb_tip_l', WM, 6, [-5.4, 46.0, 2.4], [-4.4, 50.0, 3.4])
    skin(m, 'thumb_tip_r', WB, 4, [4.4, 46.0, 2.4], [5.4, 50.0, 3.4])
    # 天秤の印の台（掌のあいだ・前に出す）: 金の台と、光る菱
    skin(m, 'libra_plinth', WG, 4, [-2.2, 45.0, 2.6], [2.2, 52.0, 4.6])
    skin(m, 'libra_plinth_top', WG, 6, [-2.6, 52.0, 2.2], [2.6, 53.2, 5.0])
    diamond(m, 'libra_gem', WL, (0, 48.5), 3.6, 1.2, 4, 5, zc=4.8)
    # 先の尖り: 光る菱
    diamond(m, 'tip_gem', WL, (0, 64.8), 5.0, 1.4, 4, 5)
    # 柄の鉤（白い石、掌の下から曲がって下へ）。鉤の先と外から鈴が吊る
    c = (-5.2, 37.2)
    m.arc('hook', WM, c, 5.2, 0, 250, 8, [2.6, 2.5, 2.4, 2.3, 2.2, 2.1, 2.0, 2.0], [1.6] * 8, 4, 6, tip=True)
    tip = (c[0] + 5.2 * math.cos(math.radians(250)), c[1] + 5.2 * math.sin(math.radians(250)))
    outer = (c[0] + 5.2 * math.cos(math.radians(165)), c[1] + 5.2 * math.sin(math.radians(165)))
    # 鈴A: 鉤の先（白）。紐の先に玉。鉤の外に紫の結び目
    skin(m, 'hook_knot', WN, 4, [-10.6, 33.2, -2.0], [-8.4, 35.2, 2.0])
    m.seg('cord_a', WG, (tip[0], tip[1] - 0.3), (tip[0], 26.8), 1.6, 0.8, 5, 4)
    skin(m, 'bead_a', WG, 5, [tip[0] - 0.9, 30.2, -0.9], [tip[0] + 0.9, 32.0, 0.9])
    _cb_bell(m, 'bell_a', tip[0], 27.4, WM, s=1.0)
    # 鈴C: 鉤の外（白）。細い腕から下がる
    m.seg('arm_c', WG, (outer[0], outer[1] - 0.4), (-15.0, 32.6), 1.8, 0.9, 5, 4)
    m.seg('cord_c', WG, (-15.0, 32.9), (-15.0, 31.0), 1.6, 0.8, 5, 4)
    skin(m, 'bead_c', WG, 5, [-15.9, 33.6, -0.9], [-14.1, 35.4, 0.9])
    _cb_bell(m, 'bell_c', -15.0, 31.4, WM, s=0.9)
    # 鈴B: 柄の右の腕から下がる（黒）
    m.seg('arm_b', WG, (0.6, 33.2), (7.0, 29.6), 1.8, 0.9, 5, 4)
    m.seg('cord_b', WG, (7.0, 29.8), (7.0, 26.0), 1.6, 0.8, 5, 4)
    skin(m, 'bead_b', WG, 5, [6.1, 28.0, -0.9], [7.9, 29.8, 0.9])
    _cb_bell(m, 'bell_b', 7.0, 26.4, WB, s=0.9)
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


def sigil_wheel(m, name, c, at):
    """横向き（ZY）の印の輪（板の絵）: 金の二重の輪のあいだに印の文字が八つ。前から見ると目を貫く輪"""
    cv = lib.Canvas(-10, c[1] - 10, 10, c[1] + 10)
    glyphs = ['x.x/.x./x.x', 'xxx/x.x/xxx', '.x./xxx/.x.', 'x../xxx/..x', 'xx./.x./.xx', 'x.x/xxx/x.x', '.xx/x../.xx', 'xxx/.x./.x.']

    def f(z, y, cur):
        dz, dy = z, y - c[1]
        r = math.hypot(dz, dy)
        if 8.6 < r < 9.6:
            return (WG, 4 if dy > 0 else 3)
        if 5.9 < r < 6.5:
            return (WG, 3)
        if 6.5 <= r <= 8.6:
            a = math.degrees(math.atan2(dy, dz)) % 360
            k = int((a + 22.5) // 45) % 8
            ca = math.radians(k * 45)
            gu = (dz - math.cos(ca) * 7.55) / 0.5 + 1.5
            gv = (dy - math.sin(ca) * 7.55) / 0.5 + 1.5
            if 0 <= gu < 3 and 0 <= gv < 3 and glyphs[k].split('/')[2 - int(gv)][int(gu)] == 'x':
                return (WL, 4)
        return cur
    cv.paint(f)
    cv.put(m, name, at=at, plane='zy')


def _eb_crack(sp, face, w, h):
    """黒い札（淵）の前の面に紫の割れ目を一本（二画素の折れ線）"""
    if face == 'south':
        sp.line((w * 0.25, h * 0.9), (w * 0.7, h * 0.5), 1.8, WC, 4, shade=False)
        sp.line((w * 0.7, h * 0.5), (w * 0.45, h * 0.1), 1.8, WC, 4, shade=False)


def tower_eye_bow():
    """側の目の弓（伝説）: 握りの前に石の瞼に抱かれた大きな一つ目が光り、そのまわりを金の印の輪が二重に巡り、横向き（奥行きの面）にも印の文字を刻んだ輪（板の絵）が目を貫いて立つ。
    弓腕は角張った石の板を段に重ねた階段の形で、腹（弦の側）の継ぎ目ごとに小さな目が開いて弓の懐を見つめる。
    上の半分は白の大理石（天）、下の半分は黒い石（淵）。左右（奥行き）には印を刻んだ石の札が張り出し、両端は金の尖塔。弦は金の鎖の輪を連ねたもの"""
    from paintbox import skin
    m = _wM('tower_eye_bow')
    m.box('grip', WN, [10.25, 9.5, -2], [13.75, 20.5, 2], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    m.box('lid_u', WM, [13.5, 17, -2.5], [20, 19.75, 2.5], 6)
    m.box('lid_d', WB, [13.5, 10.25, -2.5], [20, 13, 2.5], 4)
    m.box('lash_u', WM, [19, 19.75, -2], [20.5, 20.75, 2], 7)
    m.box('eye_glow', WL, [13.75, 13, -2.25], [19.5, 17, 2.25], 4)
    m.box('pupil', WB, [19, 14, -1.1], [19.9, 16, 1.1], 1)
    c = (17.5, 15)
    m.arc('ring_in', WG, c, 6.25, -140, 140, 10, 0.9, 0.55, 3, 5)
    m.arc('ring_out', WG, c, 8.5, -150, 150, 14, 0.8, 0.5, 3, 5)
    for k, a in enumerate((-120, -60, 0, 60, 120)):
        dx, dy = math.cos(math.radians(a)), math.sin(math.radians(a))
        lg = WC if dy < -0.1 else WL   # 光: 白の側 WL・黒の側 WC
        diamond(m, f'sigil_{k}_glow', lg, (c[0] + dx * 7.4, c[1] + dy * 7.4), 1.25, 0.55, 4, 5)
    sigil_wheel(m, 'wheel', c, c[0])
    steps = [(12, 20), (13, 24.5), (12.5, 28.5), (13.25, 32.5), (11.5, 36.5), (10.25, 40), (7.75, 43), (5, 45.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in steps]
        hm = WM if sign > 0 else WB    # 上（+y）は白の大理石、下（-y）は黒い石
        lg = WL if sign > 0 else WC    # 光: 白の側 WL・黒の側 WC
        m.chain(f'arm_{n}', hm, pts, [4, 3.75, 3.75, 3.5, 3.25, 2.75, 2.25], 1.5, 3, 7, tip=False)
        for k in (1, 3, 5):
            x, y = pts[k]
            m.box(f'plate_{n}{k}', hm, [x - 0.5, y - 1, -1.8], [x + 3.25, y + 1, 1.8], 5 + k * 0.3)
            m.box(f'plate_rim_{n}{k}', WG, [x + 3.25, y - 1.25, -1.9], [x + 4, y + 1.25, 1.9], 4)
        for k in (2, 4):
            x, y = pts[k]
            m.box(f'belly_lid_{n}{k}', hm, [x - 3.25, y - 1.1, -1.5], [x - 1.25, y + 1.1, 1.5], 6)
            m.box(f'belly_eye_{n}{k}_glow', lg, [x - 3.6, y - 0.55, -0.9], [x - 2.9, y + 0.55, 0.9], 4)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            x, y = pts[3]
            z0, z1 = (1.5, 5.5) if zs > 0 else (-5.5, -1.5)
            if n == 'd' and zn == 'f':
                # 黒い札の前の面に紫の割れ目（一面に一本）
                skin(m, f'tablet_{n}{zn}', WB, 7, [x - 1.5, y - 2.25, z0], [x + 1.5, y + 2.25, z1], paint=_eb_crack)
            else:
                m.box(f'tablet_{n}{zn}', hm, [x - 1.5, y - 2.25, z0], [x + 1.5, y + 2.25, z1], 7)
            zg = (z1 - 0.1, z1 + 0.15) if zs > 0 else (z0 - 0.15, z0 + 0.1)
            m.box(f'tablet_rune_{n}{zn}_glow', lg, [x - 0.75, y - 1.25, zg[0]], [x + 0.75, y + 1.25, zg[1]], 4)
        m.chain(f'spire_{n}', WG, [P((5, 45.5)), P((3.5, 47.5)), P((4.25, 52))], [2.25, 1.5], 1.1, 3, 5, tip=True)
        diamond(m, f'spire_gem_{n}_glow', lg, P((3.6, 47.6)), 1.4, 1.2, 4, 5)
    top, bot = (3.5, 47.5), (3.5, -17.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((44, 40.5, 37, 33.5, 30, 26.5, 3.5, 0, -3.5, -7, -10.5, -14)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            if k % 2:
                m.box(f'string_{key}_link{k}', WG, [q[0] - 0.75, q[1] - 0.9, -0.3], [q[0] + 0.75, q[1] + 0.9, 0.3], 4)
            else:
                m.box(f'string_{key}_link{k}', WG, [q[0] - 0.3, q[1] - 0.9, -0.75], [q[0] + 0.3, q[1] + 0.9, 0.75], 3)
    return m


ALL = [tower_balance_sword, tower_pull_hammer, tower_moon_staff, tower_sigil_dagger, tower_clap_bell_staff, tower_eye_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
