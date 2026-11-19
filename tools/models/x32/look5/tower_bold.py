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
    """あいだの大剣（伝説・神器）。設定画 A（tools/sources/tower_balance_sword_concept_A.png、2026-11-20）から立てる。
    座標は設定画の画素で読む: 列 c は X = c - 24（刃の中心の線が X = 0）、画の行 r は描く高さ y = 87 - r（下が 0）、
    Y = y - 12（握りの高さが Y = 0）。刃は 2 単位ごとの段の箱を縦に積む（幅は設定画の行ごとの刃の輪郭）。
    芯は厚さ 3、縁取りは厚さ 2 で芯から 0.5 引っ込む。前と後ろの面は設定画の画素をそのまま貼る（黒い石は一段明るく）。
    横と上下の面は縁取りの色（白い側は天秤の光の段、黒い側は紫）。白は先（天）、黒は根元（淵）。境は天秤の台（厚さ 5）。
    光るのは天秤の光の菱と、黒い側の樋の紫だけ。"""
    from paintbox import skin
    from pathlib import Path
    from PIL import Image

    src = Path(__file__).resolve().parents[3] / 'sources' / 'tower_balance_sword_concept_A.png'
    im = Image.open(src).convert('RGBA')
    CW, CH = im.size
    cpx = im.load()
    rev = {}
    for mat in (WM, WG, WB, WN, WC, WL):
        for k, hx in enumerate(lib.RAMPS[mat]):
            rev.setdefault(hx.lower(), (mat, k))
    BORDER = 47   # 設定画の白と黒の境（描く高さ）

    def cp(X, y):
        """設定画の画素（X、描く高さ y）→ (素材, 段)。透明は None。黒い石は一段明るくして暗い背景で沈まないようにする"""
        c, r = X + 24, CH - 1 - y
        if not (0 <= c < CW and 0 <= r < CH):
            return None
        rr, gg, bb, aa = cpx[c, r]
        if aa == 0:
            return None
        mat, t = rev['#%02x%02x%02x' % (rr, gg, bb)]
        if mat == WB:
            t = max(3, min(5, t + 2))
        return mat, t

    def paint(x0, y0, edge=None, edge_t=2):
        """箱の正面と裏に設定画の画素を貼る（裏は左右を返す）。edge があれば横と上下の面をその色で塗る。
        x0 は箱の左端の X、y0 は箱の下端の描く高さ"""
        def f(sp, face, w, h):
            for i in range(w):
                for j in range(h):
                    cell = sp.cell[(i, j)]
                    if face in ('south', 'north'):
                        ii = i if face == 'south' else w - 1 - i
                        v = cp(x0 + ii, y0 + j)
                        if v:
                            cell['mat'], cell['t'] = v
                    elif edge and face in ('east', 'west', 'up', 'down'):
                        cell['mat'], cell['t'] = edge, edge_t
        return f

    def box(name, mat, X0, X1, y0, y1, d, t, pic=None, glow=None):
        """箱: X0〜X1、描く高さ y0〜y1（設定画の単位）、奥行きは ±d（厚さ 2d）"""
        skin(m, name, mat, t, [X0, y0 - 12, -d], [X1, y1 - 12, d], paint=pic, glow=glow)

    def dia_row(y, c, r):
        """菱（|X+0.5| + |y-c| <= r）の行 y の片側の幅"""
        return max((max(-a, a + 1) for a in range(-12, 12) if abs(a + 0.5) + abs(y - c) <= r), default=0)

    def ext(y):
        """刃の行 y の片側の幅。台や鍔に触れない行から読み、左右が揃っているか確かめる"""
        xs = [X for X in range(-9, 9) if cp(X, y)]
        if not xs:
            return 0
        lo, hi = min(xs), max(xs) + 1
        assert lo == -hi, ('blade row is not symmetric', y, lo, hi)
        return hi

    def blade_ext(y):
        if y in (24, 25):      # 首（台）に隠れる行は上の行の幅
            return ext(26)
        if y == 46:            # 天秤の腕の行は、上下の刃の幅
            return ext(45)
        if y == 47:
            return ext(48)
        return ext(y)

    m = _wM('tower_balance_sword')

    # ---- 柄・柄頭（柄は夜の紫、金の輪が二つ。柄頭は黒い石の菱を三段）
    for k, (a, b) in enumerate(((0, 2), (2, 4), (4, 7))):
        E = max(dia_row(y, 3, 3.6) for y in range(a, b))
        box(f'pommel_{k}', WB, -E, E, a, b, 2, 3, paint(-E, a))
    box('grip', WN, -2, 2, 7, 19, 1.5, 2, paint(-2, 7))
    box('grip_ring_a', WG, -3, 3, 7, 9, 2, 4, paint(-3, 7))
    box('grip_ring_b', WG, -3, 3, 16, 18, 2, 4, paint(-3, 16))

    # ---- 首と鍔の梁（梁は高さ 5、厚さ 5）。端の鉤は二単位、下の皿は金
    box('neck', WB, -8, 8, 24, 26, 2, 2, paint(-8, 24))
    box('beam', WB, -15, 15, 19, 24, 2.5, 2, paint(-15, 19))
    for sd, n in ((1, 'r'), (-1, 'l')):
        X0, X1 = (13, 15) if sd > 0 else (-15, -13)
        box(f'hook_{n}', WB, X0, X1, 15, 19, 1.5, 3, paint(X0, 15, WC, 3))
        X0, X1 = (9, 16) if sd > 0 else (-16, -9)
        box(f'pan_{n}', WG, X0, X1, 13, 15, 2, 4, paint(X0, 13))

    # ---- 刃: 2 単位の段の箱を積む。根元の一段（3 単位）、以降 2 単位ずつ、先の一段（3 単位）。
    #      縁取り（外の 1 単位）は厚さ 2、芯（残り）は厚さ 3。白い側（境より上）は金の樋まで PNG で塗り、黒い側は紫の樋を光る箱にする
    bands = [(24, 27)] + [(a, a + 2) for a in range(27, 79, 2)] + [(79, 82)]
    for a, b in bands:
        E = max(blade_ext(y) for y in range(a, b))
        white = a >= BORDER
        emat, eg, et = (WL, False, 2) if white else (WC, None, 3)   # 縁: 白い側は天秤の光の段（光らせない）、黒い側は紫
        cmat, ct = (WM, 6) if white else (WB, 2)
        nm = f'b{a}'
        if E <= 1:
            box(f'{nm}_edge', emat, -E, E, a, b, 1, et, paint(-E, a, emat, et), glow=eg)
            continue
        box(f'{nm}_edl', emat, -E, -E + 1, a, b, 1, et, paint(-E, a, emat, et), glow=eg)
        box(f'{nm}_edr', emat, E - 1, E, a, b, 1, et, paint(E - 1, a, emat, et), glow=eg)
        groove = (not white) and 29 <= a < BORDER and E >= 3
        if groove:
            box(f'{nm}_cl', cmat, -E + 1, -1, a, b, 1.5, ct, paint(-E + 1, a, emat, et))
            box(f'{nm}_groove_glow', WC, -1, 1, a, b, 1.5, 3, paint(-1, a, emat, et), glow=True)
            box(f'{nm}_cr', cmat, 1, E - 1, a, b, 1.5, ct, paint(1, a, emat, et))
        else:
            box(f'{nm}_core', cmat, -E + 1, E - 1, a, b, 1.5, ct, paint(-E + 1, a, emat, et))

    # ---- 天秤の台（厚さ 5。設定画の菱。刃より前後に張り出す）と、光る菱（三段の箱）、腕
    for k, (a, b) in enumerate(((42, 44), (44, 46), (46, 48), (48, 50), (50, 53))):
        E = max(dia_row(y, 47, 5.6) for y in range(a, b))
        box(f'plinth_{k}', WG, -E, E, a, b, 2.5, 4, paint(-E, a))
    box('libra_a', WL, -2, 2, 45, 47, 3, 5)     # 光る菱（名前は _glow になる）
    box('libra_b', WL, -3, 3, 47, 49, 3, 5)
    for sd, n in ((1, 'r'), (-1, 'l')):
        X0, X1 = (6, 9) if sd > 0 else (-9, -6)
        box(f'libra_arm_{n}', WG, X0, X1, 46, 48, 1.5, 4, paint(X0, 46))

    # ---- 切っ先: 尖塔（金の二単位）。刃の先の行（80・81）は刃の先の段で埋める
    box('spire', WG, -1, 1, 82, 84, 1, 5, paint(-1, 82))
    return m

def _hm_eye(x0, w0, y0, cy, outer, outer_t, crack=False):
    """頭の芯の前と後ろの面の印の目: 外の輪は半分ごとに反対の明るさ（白の半分は黒い輪、黒の半分は白い輪）、内は黒い輪。
    x0 は面の左端の x、w0 は面の幅。north は左右が反対（絵の向きが反対）。crack は黒い半分の下の方に紫の割れ目（一本）"""
    def paint(sp, face, w, h):
        if face not in ('south', 'north'):
            return
        for i in range(w):
            for j in range(h):
                x = x0 + i + 0.5 if face == 'south' else x0 + w0 - i - 0.5
                y = y0 + j + 0.5
                r = math.hypot(x, y - cy)
                if 3.0 <= r < 5.0:
                    sp.rect(i, j, i + 1, j + 1, outer, outer_t, shade=False)
                elif 2.0 <= r < 3.0:
                    sp.rect(i, j, i + 1, j + 1, WB, 1, shade=False)
        if crack:
            sp.line((w * 0.22, h * 0.05), (w * 0.4, h * 0.4), 1.6, WC, 4, shade=False)
    return paint


def tower_pull_hammer():
    """引き合いの鎚（伝説・神器）: 柄の上に横長の大きな頭。頭は四段（計八段）の石積みが左右へ大きく張り出し、両端の平らな面が打つ面。
    頭の上半分（天）が白の大理石、下半分（淵）が黒い石。境に天秤の印の台（金の四角い台と光る菱）。
    前と後ろの中央に印の目。頭の上と下に太く短い角が反り、角の先は石の色。
    金は柄の輪二つ、頭の帯三か所（段の 0・1・3 の前と後ろ）、天秤の台だけ。光は天秤の印の菱（WL）一つと、黒い半分の紫の割れ目（WC）一つ"""
    from paintbox import skin
    LAY = [(27, 31.5, 4, 9, 5.5), (31.5, 36, 5, 10, 6.0), (36, 40.5, 5, 10, 6.0), (40.5, 45, 4, 9, 5.2)]
    m = _wM('tower_pull_hammer')
    # 柄と柄頭（夜の紫の柄に金の輪二つ、黒い石の柄頭）
    skin(m, 'grip', WN, 2, [-1.5, -6, -1.5], [1.5, 5, 1.5], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-5.6, -3.6), (2.6, 4.6))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.1, y0, -2.1], [2.1, y1, 2.1])
    skin(m, 'pommel', WB, 3, [-2.5, -8.5, -2.5], [2.5, -6, 2.5])
    skin(m, 'pommel_rim', WB, 4, [-2.9, -6.8, -2.9], [2.9, -5.7, 2.9])
    m.seg('pommel_tip', WB, (0, -8.5), (0, -11), 2.5, 1.25, 2, 3, tip=True)
    # 柄の上（夜の紫に、石の色の二本の帯）
    skin(m, 'rod', WN, 2, [-1.25, 5, -1.25], [1.25, 27, 1.25], paint=_bs_bands)
    for k, y in enumerate((12, 20)):
        m.box(f'rod_band_{k}', WB, [-1.7, y, -1.7], [1.7, y + 2, 1.7], 5)
    # 頭の芯: 上半分は白の大理石（天）、下半分は黒い石（淵）。前と後ろに印の目。黒い半分に紫の割れ目一本
    skin(m, 'core_top', WM, 4, [-6, 36, -4.5], [6, 45, 4.5], paint=_hm_eye(-6, 12, 36, 36, WB, 3))
    skin(m, 'core_bot', WB, 2, [-6, 27, -4.5], [6, 36, 4.5], paint=_hm_eye(-6, 12, 27, 36, WM, 6, crack=True))
    # 天秤の印の台（大剣の台と同じ作り）: 金の四角い台。前の菱だけ光る（WL）、後ろの菱は台の金
    for sd, n in ((1, 'f'), (-1, 'b')):
        z0, z1 = (4.5, 5.4) if sd > 0 else (-5.4, -4.5)
        skin(m, f'plinth_{n}', WG, 4, [-2.4, 33.6, z0], [2.4, 38.4, z1])
        skin(m, f'plinth_top_{n}', WG, 6, [-2.8, 38.2, z0 - 0.2], [2.8, 39.0, z1 + 0.2])
        skin(m, f'plinth_bot_{n}', WG, 6, [-2.8, 33.0, z0 - 0.2], [2.8, 33.8, z1 + 0.2])
        diamond(m, f'eye_gem_{n}_glow', WL if sd > 0 else WG, (0, 36), 3.6, 1.2, 4, 5, zc=sd * 5.6)
    # 四段の石積み（左右へ張り出す）。段 0・1 は黒い石、段 2・3 は白の大理石。帯は段 0・1・3 の前と後ろ（金の三か所）
    for sd, n in ((-1, 'l'), (1, 'r')):
        for k, (y0, y1, xi, xo, z) in enumerate(LAY):
            a, b = sorted((sd * xi, sd * xo))
            top = k >= 2
            mat, t = (WM, 6) if top else (WB, 2)
            skin(m, f'lay_{n}{k}', mat, t, [a, y0, -z], [b, y1, z], paint=_bs_white if top else None)
            lo, hi = (a, a + 0.8) if sd < 0 else (b - 0.8, b)
            m.box(f'lay_rim_{n}{k}', mat, [lo, y0 + 0.6, -z - 0.3], [hi, y1 - 0.6, z + 0.3], t)
            if k in (0, 1, 3):
                mid = (y0 + y1) / 2
                m.box(f'band_{n}{k}', WG, [min(a, b) + 1, mid - 0.5, z], [max(a, b) - 1, mid + 0.5, z + 0.6], 3)
                m.box(f'bandb_{n}{k}', WG, [min(a, b) + 1, mid - 0.5, -z - 0.6], [max(a, b) - 1, mid + 0.5, -z], 3)
        a, b = sorted((sd * 6, sd * 9))
        skin(m, f'cap_top_{n}', WM, 5, [a, 45, -3], [b, 46, 3])
        a, b = sorted((sd * 4, sd * 9))
        skin(m, f'cap_bot_{n}', WB, 5, [a, 26, -4.6], [b, 27, 4.6])
        # 太く短い角（上は白、下は黒）。角の先は石の色の菱（光らない）
        m.chain(f'horn_up_{n}', WM, [(sd * 6, 45), (sd * 8.6, 48.6), (sd * 8.2, 52.2)], [3.6, 2.6], [2.6, 2.0], 5, 3, tip=True)
        diamond(m, f'horn_up_gem_{n}_glow', WM, (sd * 8.2, 54.0), 2.6, 1.2, 4, 5)
        m.chain(f'horn_dn_{n}', WB, [(sd * 6, 27), (sd * 8.6, 23.4), (sd * 8.2, 19.8)], [3.6, 2.6], [2.6, 2.0], 5, 3, tip=True)
        diamond(m, f'horn_dn_gem_{n}_glow', WB, (sd * 8.2, 18.0), 2.2, 1.0, 4, 5)
    return m


def _ms_black(sp, face, w, h):
    """黒い石（淵）: 紫の割れ目を一本（二画素）"""
    if face in ('south', 'north'):
        sp.line((w * 0.2, h * 0.9), (w * 0.6, h * 0.35), 1.8, WC, 4, shade=False)
        sp.line((w * 0.6, h * 0.35), (w * 0.85, h * 0.05), 1.8, WC, 4, shade=False)


def _ms_claw(m, name, pts, widths, depth, sd):
    """爪（太い節の点の列）。節の中点が境（y=50、天秤の台の高さ）より上は白、下は黒（2026-11-20 上下割り）"""
    n = len(pts) - 1
    for k in range(n):
        a, b = pts[k], pts[k + 1]
        mat = WM if (a[1] + b[1]) / 2 > 50.0 else WB
        m.seg(f'{name}_{k}', mat, a, b, widths[k], depth, 3 + 3 * k / n, 3 + 3 * (k + 1) / n, over=0.5,
              tip=(k == n - 1))


def tower_moon_staff():
    """落ちる月の杖（伝説・神器）: 石の軸の上で、金の襟から白と黒の太い爪が左右へ張り出し、頂で反って月の籠を作る。
    色は上下で割る: 籠の上半分（境より上）と先は白の大理石（天）、軸と籠の下半分は黒い石（淵）。境は天秤の印の台（金の台と光る菱）。
    金は三つの帯（軸の二段の縁と襟）、先の尖塔、天秤の台だけ。光は天秤の印の菱（WL）一つと、籠の中の紫の月（WC）一つだけ。
    紫の月は今より小さく。落ちる欠片は白と黒の石の色で光らせない（この職だけ浮く）"""
    from paintbox import skin
    m = _wM('tower_moon_staff')
    # 柄と柄頭（夜の紫の帯、金の輪二つは数えない、柄頭は黒い石）
    skin(m, 'grip', WN, 2, [-1.6, -14, -1.6], [1.6, 1.5, 1.6], paint=_bs_bands)
    for k, (y0, y1) in enumerate(((-12.6, -10.6), (-0.8, 1.2))):
        skin(m, f'grip_ring_{k}', WG, 4, [-2.3, y0, -2.3], [2.3, y1, 2.3])
    skin(m, 'pommel', WB, 3, [-2.6, -16.5, -2.6], [2.6, -14, 2.6])
    skin(m, 'pommel_rim', WB, 4, [-3.0, -14.8, -3.0], [3.0, -13.7, 3.0])
    m.seg('pommel_tip', WB, (0, -16.5), (0, -19), 2.5, 1.25, 2, 3, tip=True)
    # 石の軸（段を重ね、軸は根元の側なので全部黒い石。黒の段に紫の割れ目を一か所）。段の継ぎ目に金の縁
    spans = [(1.5, 10, 1.6), (10, 14, 1.9), (14, 26, 1.5), (26, 30, 1.9), (30, 38, 1.6)]
    for k, (y0, y1, hw) in enumerate(spans):
        skin(m, f'shaft_{k}_w', WB, 6, [-hw, y0, -hw], [0, y1, hw], paint=_ms_black if k == 2 else None)
        skin(m, f'shaft_{k}_b', WB, 2, [0, y0, -hw], [hw, y1, hw])
        if k in (1, 3):
            skin(m, f'shaft_rim_{k}', WG, 5, [-hw - 0.7, y0 - 0.8, -hw - 0.7], [hw + 0.7, y0 + 0.8, hw + 0.7])
    # 金の襟（爪の根元。三つ目の帯）
    skin(m, 'collar', WG, 4, [-3.6, 38, -3.6], [3.6, 41, 3.6])
    # 白と黒の太い爪（左右へ張り出し、頂で反る）。境（y=50）の上は白、下は黒
    left = [(-2.4, 40.5), (-8.2, 43.4), (-13.2, 48.0), (-14.8, 54.0), (-12.4, 60.0), (-6.4, 64.0), (-1.6, 65.6)]
    right = [(-x, y) for x, y in left]
    widths = [6.2, 5.8, 5.2, 4.6, 4.0, 3.4]
    _ms_claw(m, 'claw_l', left, widths, 3.6, -1)
    _ms_claw(m, 'claw_r', right, widths, 3.6, 1)
    # 籠の中の月（紫の割れ目の光。今より小さく）と印の目（淵の黒の瞳）
    diamond(m, 'moon_glow', WC, (0, 50), 8.5, 2.6, 2, 4, zc=0.0)
    diamond(m, 'moon_pupil', WN, (0, 50), 3.4, 1.8, 2, 4, zc=2.8)
    # 天秤の印の台: 金の四角い台に天秤の印の光る菱（籠の中の前に一つ）。境の台
    skin(m, 'plinth', WG, 4, [-3.4, 46.4, 3.0], [3.4, 53.6, 5.2])
    skin(m, 'plinth_top', WG, 6, [-3.8, 53.2, 2.8], [3.8, 54.2, 5.4])
    diamond(m, 'libra_gem_glow', WL, (0, 50), 5.0, 1.6, 4, 5, zc=5.2)
    m.seg('moon_chain', WM, (0, 64.6), (0, 55.6), 1.4, 0.9, 4, 4, over=0.5)
    # 尖塔（金）と先の菱（金。光らせない）
    m.chain('spire', WG, [(0, 64), (0, 68.5), (0, 72)], [2.6, 1.6], [2.0, 1.2], 4, 5, tip=True)
    diamond(m, 'spire_gem', WG, (0, 73.8), 3.6, 1.5, 4, 5, zc=0.0)
    # 籠の外へ落ちる月の欠片（この職だけ浮く部品。石の色で、光らせない）
    shards = [(-21.0, 40.0, 3.6, WM), (-23.6, 33.0, 2.8, WM), (-19.6, 27.6, 2.2, WM),
              (21.0, 40.0, 3.6, WB), (23.6, 33.0, 2.8, WB), (19.6, 27.6, 2.2, WB)]
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


def _ds_root(sp, face, w, h):
    """黒い根元（淵）: 紫の割れ目を一本（二画素の線。黒い側に一本だけ）"""
    if face in ('south', 'north'):
        sp.line((w * 0.3, 0), (w * 0.6, h), 1.6, WC, 4, shade=False)


def _ds_vein(sp, face, w, h):
    """白い石（天）: 大理石の暗い筋を二画素で一本"""
    if face in ('south', 'north'):
        sp.line((w * 0.2, h * 0.9), (w * 0.8, h * 0.1), 1.6, WM, 3, shade=False)


def tower_sigil_dagger():
    """追う印の短剣: 形（輪郭・部品の配置）は前の版のまま。天秤の台だけ刃の中ほど（y=21）へ動かし、そこを白黒の境にする（2026-11-20 夜、主の指示）。
    刃の上（台より先）は天の白い大理石（WM）、下（台より根元）は淵の黒い石（WB）。鰭は境より上が白・下が黒。鍔・柄頭は黒い石。
    金は柄の輪と刃先の帯二か所と天秤の台だけ。光は天秤の菱（WL）一つと、黒い刃の下半分の紫の割れ目（WC）一本だけ。"""
    from paintbox import skin
    m = _wM('tower_sigil_dagger')
    # 柄と柄頭（柄は夜の紫に金の輪。柄頭は黒い石）
    skin(m, 'grip', WN, 2, [-1.8, -4, -1.8], [1.8, 3.6, 1.8], paint=_bs_bands)
    skin(m, 'grip_ring', WG, 4, [-2.2, -1.6, -2.2], [2.2, 0.0, 2.2])
    skin(m, 'pommel', WB, 3, [-2.4, -6.4, -2.4], [2.4, -4, 2.4])
    skin(m, 'pommel_rim', WB, 4, [-2.8, -4.6, -2.8], [2.8, -3.6, 2.8])
    m.seg('pommel_tip', WB, (0, -6.4), (0, -8.8), 2.4, 1.2, 2, 3, tip=True)
    # 鍔: 四角い輪（黒い石）
    gx, gy, h, t = 0.0, 4.6, 2.6, 1.0
    skin(m, 'guard_l', WB, 4, [gx - h - t, gy - h - t, -1.2], [gx - h, gy + h + t, 1.2])
    skin(m, 'guard_r', WB, 4, [gx + h, gy - h - t, -1.2], [gx + h + t, gy + h + t, 1.2])
    skin(m, 'guard_b', WB, 4, [gx - h, gy - h - t, -1.2], [gx + h, gy - h, 1.2])
    skin(m, 'guard_t', WB, 4, [gx - h, gy + h, -1.2], [gx + h, gy + h + t, 1.2])
    # 刃の中心線（節の点）と幅・奥行き。根元 7 から先へ細る。先は背の側へ返る鉤
    P = [(0, 6), (0.3, 12), (1.0, 18), (2.6, 23.5), (4.8, 27.5), (6.8, 30.6), (7.6, 33.2), (6.8, 35.2), (4.6, 35.8), (2.6, 34.8)]
    W = [7.0, 6.4, 5.8, 5.0, 4.2, 3.4, 2.6, 2.0, 1.5, 1.2]
    D = [1.5, 1.4, 1.4, 1.3, 1.2, 1.1, 1.0, 0.9, 0.8, 0.7]
    # 境（天秤の台の高さ y=21 = 刃の中ほど）で刃を二つに切る: 下は黒い石、上は白い大理石（形は同じ）
    BY = 21.0
    k = max(i for i in range(len(P) - 1) if P[i][1] <= BY)
    xB = _dg_at(P, [x for x, _ in P], BY)
    wB = _dg_at(P, W, BY)
    dB = _dg_at(P, D, BY)
    lowP, lowW, lowD = P[:k + 1] + [(xB, BY)], W[:k + 1] + [wB], D[:k + 1] + [dB]
    upP, upW, upD = [(xB, BY)] + P[k + 1:], [wB] + W[k + 1:], [dB] + D[k + 1:]

    def halves(pts, ws):
        pw = [(x - w / 4, y) for (x, y), w in zip(pts, ws)]
        pk = [(x + w / 4, y) for (x, y), w in zip(pts, ws)]
        return pw, pk, [w / 2 for w in ws]
    lw_, lk_, lhw = halves(lowP, lowW)
    m.chain('lower_w', WB, lw_, lhw, lowD, 2, 2, tip=False, zc=0.0)
    m.chain('lower_k', WB, lk_, lhw, lowD, 3, 3, tip=False, zc=0.0)
    uw_, uk_, uhw = halves(upP, upW)
    m.chain('blade_w', WM, uw_, uhw, upD, 6, 8, tip=True, zc=0.0)
    m.chain('blade_k', WM, uk_, uhw, upD, 4, 5, tip=True, zc=0.0)
    # 黒い刃の下半分の紫の割れ目（一本）: 薄い板を正面に置き、その面に線を描く
    skin(m, 'crack_plate', WB, 2, [-1.0, 8.5, 1.2], [1.0, 19.0, 1.6], paint=_ds_root)
    # 背の鰭: 四枚。境より下は黒い石、上は白い大理石
    ys = (11.0, 16.5, 22.0, 27.5)
    for k2, (y, ph, pwd) in enumerate(zip(ys, (5.2, 5.4, 5.2, 4.8), (4.0, 4.0, 3.6, 3.2))):
        xc = _dg_at(P, [x for x, _ in P], y)
        hw = _dg_at(P, [w / 2 for w in W], y)
        x0 = xc + hw - 0.6
        if y < BY:
            skin(m, f'fin_{k2}', WB, 3, [x0, y - ph / 2, -1.0], [x0 + pwd, y + ph / 2, 1.0])
            skin(m, f'fin_rim_{k2}', WB, 6, [x0 + pwd - 0.4, y - ph / 2 - 0.3, -1.2], [x0 + pwd + 0.3, y + ph / 2 + 0.3, 1.2])
            skin(m, f'fin_tooth_{k2}', WB, 4, [x0 + pwd * 0.5, y + ph / 2, -0.7], [x0 + pwd * 0.5 + 1.2, y + ph / 2 + 1.4, 0.7])
        else:
            skin(m, f'fin_{k2}', WM, 2, [x0, y - ph / 2, -1.0], [x0 + pwd, y + ph / 2, 1.0], paint=_ds_vein)
            skin(m, f'fin_rim_{k2}', WM, 8, [x0 + pwd - 0.4, y - ph / 2 - 0.3, -1.2], [x0 + pwd + 0.3, y + ph / 2 + 0.3, 1.2])
            skin(m, f'fin_tooth_{k2}', WM, 5, [x0 + pwd * 0.5, y + ph / 2, -0.7], [x0 + pwd * 0.5 + 1.2, y + ph / 2 + 1.4, 0.7])
    # 縁の段が二枚（境より下は黒、上は白）
    for k2, y in enumerate((14.0, 25.0)):
        xc = _dg_at(P, [x for x, _ in P], y)
        hw = _dg_at(P, [w / 2 for w in W], y)
        x1 = xc - hw + 0.6
        if y < BY:
            skin(m, f'scale_{k2}', WB, 4, [x1 - 2.0, y - 1.5, -1.0], [x1, y + 1.5, 1.0])
        else:
            skin(m, f'scale_{k2}', WM, 8, [x1 - 2.0, y - 1.5, -1.0], [x1, y + 1.5, 1.0], paint=_ds_vein)
    # 金の帯二か所（刃先の反りの上。金は帯二か所だけ）
    for k2, y in enumerate((30.6, 33.4)):
        xc = _dg_at(P, [x for x, _ in P], y)
        hw = _dg_at(P, [w / 2 for w in W], y)
        d = _dg_at(P, D, y)
        skin(m, f'band_{k2}', WG, 5, [xc - hw - 0.5, y - 0.7, -d - 0.4], [xc + hw + 0.5, y + 0.7, d + 0.4])
    # 天秤の印の台（金）と光る菱（白黒の境＝刃の中ほど）
    skin(m, 'libra_seat', WG, 4, [-3.4, BY - 2.6, 1.0], [3.4, BY + 2.6, 2.2])
    diamond(m, 'libra_gem_glow', WL, (0, BY), 4.4, 1.3, 4, 5, zc=2.4)
    return m


def _cb_palm(mat, vein, t):
    """合わせた掌の正面: 指の境を一本（二画素。中ほど）と、石の筋を一本"""
    def paint(sp, face, w, h):
        if face in ('south', 'north') and w >= 4 and h >= 4:
            j = int(h * 0.5)
            sp.rect(0, j, w, j + 2, mat, t, shade=False)
            sp.line((w * 0.25, h * 0.85), (w * 0.7, h * 0.15), 1.6, vein, t + 3, shade=False)
    return paint


def _cb_bell(m, name, cx, top, body, s=1.0, tongue=WB):
    """鈴: 冠と胴は黒い石（境より下）、口の縁だけ白（形を読ませる）。舌は tongue（一つだけ紫）。top は冠の上の面"""
    from paintbox import skin

    def w(v):
        return v * s
    skin(m, f'{name}_crown', WB, 5, [cx - w(1.1), top - 1.6, -w(1.1)], [cx + w(1.1), top, w(1.1)])
    skin(m, f'{name}_shoulder', body, 4, [cx - w(2.0), top - 3.8, -w(1.8)], [cx + w(2.0), top - 1.6, w(1.8)])
    skin(m, f'{name}_body', body, 4, [cx - w(2.8), top - 7.0, -w(2.4)], [cx + w(2.8), top - 3.8, w(2.4)])
    skin(m, f'{name}_lip', WM, 5, [cx - w(3.4), top - 7.8, -w(2.9)], [cx + w(3.4), top - 7.0, w(2.9)])
    skin(m, f'{name}_tongue', tongue, 5, [cx - 0.8, top - 9.8, -0.8], [cx + 0.8, top - 7.6, 0.8])


def tower_clap_bell_staff():
    """合掌の鈴杖（伝説・神器）: 柄の先で白と黒の二つの石の掌が合わさり、指先が上で合う。
    掌のあいだに金の天秤の印の台（光る菱を前に置く）。掌の下で柄が黒い鉤に曲がり、鉤の先と外から黒い鈴が三つ下へ吊る。先の尖りは金の菱。
    色: 上（合わせた掌の上半分・指先）は白の大理石、下（掌の下半分・鉤・鈴・紐）は黒い石、境は天秤の印の台。柄は夜の紫に金の輪。
    金は柄の輪二つを除いて帯三か所（柄頭の蓋・柄の中の帯・手首）、先の尖り、天秤の台だけ。光は天秤の菱（WL）一つと、鈴の舌の紫（WC）一つだけ"""
    from paintbox import skin
    m = _wM('tower_clap_bell_staff')
    # 柄頭（黒い石・金の蓋・尖り）と握り（夜の紫に金の輪）
    skin(m, 'pommel', WB, 4, [-2.2, -18.0, -2.2], [2.2, -14.0, 2.2])
    skin(m, 'pommel_cap', WG, 4, [-2.6, -18.8, -2.6], [2.6, -17.6, 2.6])
    m.seg('pommel_tip', WB, (0, -18.8), (0, -21.6), 2.4, 1.2, 4, 5, tip=True)
    skin(m, 'grip_l', WN, 4, [-1.8, -14.0, -1.8], [0, 1.5, 1.8])
    skin(m, 'grip_r', WN, 4, [0, -14.0, -1.8], [1.8, 1.5, 1.8])
    skin(m, 'grip_ring', WG, 4, [-2.2, -10.6, -2.2], [2.2, -8.6, 2.2])
    skin(m, 'grip_wrap_0', WN, 4, [-2.0, -6.0, -2.0], [2.0, -4.6, 2.0])
    skin(m, 'grip_wrap_1', WN, 4, [-2.0, -2.2, -2.0], [2.0, -0.8, 2.0])
    # 柄（夜の紫）と、金の輪、金の帯
    skin(m, 'rod_l', WN, 5, [-1.25, 1.5, -1.25], [0, 38.0, 1.25])
    skin(m, 'rod_r', WN, 4, [0, 1.5, -1.25], [1.25, 38.0, 1.25])
    skin(m, 'rod_band', WG, 4, [-1.9, 9.4, -1.9], [1.9, 11.0, 1.9])
    skin(m, 'rod_wrap', WG, 4, [-1.8, 24.0, -1.8], [1.8, 25.4, 1.8])
    # 手首の金の帯と、掌（上の半分は白、下の半分は黒。左右では割らない。厚く大きく）
    skin(m, 'wrist', WG, 4, [-2.4, 37.4, -2.4], [2.4, 39.0, 2.4])
    skin(m, 'palm_l', WB, 4, [-4.6, 39.0, -2.8], [0, 50.0, 2.8], _cb_palm(WB, WB, 2))
    skin(m, 'palm_r', WB, 4, [0, 39.0, -2.8], [4.6, 50.0, 2.8], _cb_palm(WB, WB, 2))
    skin(m, 'palm_top_l', WM, 6, [-4.2, 50.0, -2.4], [0, 58.0, 2.4], _cb_palm(WM, WM, 2))
    skin(m, 'palm_top_r', WM, 4, [0, 50.0, -2.4], [4.2, 58.0, 2.4], _cb_palm(WM, WM, 2))
    skin(m, 'tip_l', WM, 6, [-2.6, 58.0, -1.6], [0, 63.0, 1.6])
    skin(m, 'tip_r', WM, 4, [0, 58.0, -1.6], [2.6, 63.0, 1.6])
    skin(m, 'thumb_l', WB, 4, [-5.0, 41.0, 2.8], [-2.4, 47.0, 4.4])
    skin(m, 'thumb_r', WB, 4, [2.4, 41.0, 2.8], [5.0, 47.0, 4.4])
    skin(m, 'thumb_tip_l', WB, 4, [-5.4, 46.0, 2.4], [-4.4, 50.0, 3.4])
    skin(m, 'thumb_tip_r', WB, 4, [4.4, 46.0, 2.4], [5.4, 50.0, 3.4])
    # 天秤の印の台（掌のあいだ・前に出す）: 金の台と、光る菱（境の上に置く）
    skin(m, 'libra_plinth', WG, 4, [-2.2, 45.0, 2.6], [2.2, 52.0, 4.6])
    skin(m, 'libra_plinth_top', WG, 6, [-2.6, 52.0, 2.2], [2.6, 53.2, 5.0])
    diamond(m, 'libra_gem', WL, (0, 48.5), 3.6, 1.2, 4, 5, zc=4.8)
    # 先の尖り: 金の菱（光らせない）
    diamond(m, 'tip_gem', WG, (0, 64.8), 5.0, 1.4, 4, 5)
    # 柄の鉤（黒い石、掌の下から曲がって下へ）。鉤の先と外から鈴が吊る
    c = (-5.2, 37.2)
    m.arc('hook', WB, c, 5.2, 0, 250, 8, [2.6, 2.5, 2.4, 2.3, 2.2, 2.1, 2.0, 2.0], [1.6] * 8, 4, 5, tip=True)
    tip = (c[0] + 5.2 * math.cos(math.radians(250)), c[1] + 5.2 * math.sin(math.radians(250)))
    outer = (c[0] + 5.2 * math.cos(math.radians(165)), c[1] + 5.2 * math.sin(math.radians(165)))
    # 鈴A: 鉤の先。紐の先に玉。鉤の外に紫の結び目
    skin(m, 'hook_knot', WN, 4, [-10.6, 33.2, -2.0], [-8.4, 35.2, 2.0])
    m.seg('cord_a', WB, (tip[0], tip[1] - 0.3), (tip[0], 26.8), 1.6, 0.8, 5, 4)
    skin(m, 'bead_a', WB, 5, [tip[0] - 0.9, 30.2, -0.9], [tip[0] + 0.9, 32.0, 0.9])
    _cb_bell(m, 'bell_a', tip[0], 27.4, WB, s=1.0, tongue=WC)
    # 鈴C: 鉤の外。細い腕から下がる
    m.seg('arm_c', WB, (outer[0], outer[1] - 0.4), (-15.0, 32.6), 1.8, 0.9, 5, 4)
    m.seg('cord_c', WB, (-15.0, 32.9), (-15.0, 31.0), 1.6, 0.8, 5, 4)
    skin(m, 'bead_c', WB, 5, [-15.9, 33.6, -0.9], [-14.1, 35.4, 0.9])
    _cb_bell(m, 'bell_c', -15.0, 31.4, WB, s=0.9)
    # 鈴B: 柄の右の腕から下がる
    m.seg('arm_b', WB, (0.6, 33.2), (7.0, 29.6), 1.8, 0.9, 5, 4)
    m.seg('cord_b', WB, (7.0, 29.8), (7.0, 26.0), 1.6, 0.8, 5, 4)
    skin(m, 'bead_b', WB, 5, [6.1, 28.0, -0.9], [7.9, 29.8, 0.9])
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
