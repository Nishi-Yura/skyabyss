# -*- coding: utf-8 -*-
"""嵐のシリーズ（look5・個性版）。三章の巨神「雷翼」（嵐を纏う鳥）、避雷の塔、風見の丘から一品ずつ形を立てる。
色: 灰紫の嵐雲（storm）の 1 系統、差し色は銅（copper）、光は稲妻の黄白（bolt）。

形は他のシリーズから借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 細い鉄の避雷針。握りは革の巻き、握りの上から羽根の房が二本垂れる。下は二股の接地の叉。
- 光は品ごとに場所を変える（ユーザー: 「本当にそこだけなの？」）:
  鉄床の鎚 = 雲の下から落ちる稲妻、雲籠の杖 = 球電、雷鳴りの鈴杖 = 稲妻の短冊、短剣 = 折れ角の火花、
  雷獣の爪 = 爪の間の電弧、風弓 = 弦。雷の鎚・羽根の杖・風見の鈴杖・雷翼の弓は光なし。
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


# ---------------------------------------------------------------- 鎚

def storm_hammer():
    """雷石の鎚: 雷に打たれた黒い石（傷は筆）を、銅の線で避雷針の先に縛る。光なし"""
    m = M('storm_hammer')
    rod(m, -12, 8, 25)
    m.box('stone', S, [-4.5, 24, -2.75], [4, 33, 2.75], grad=(24, 2, 33, 4), rim=False)
    m.seg('stone_face', S, (4, 28.5), (8.5, 28.5), 7, 2.25, 4, 6, tip=True)          # +x へ細る打つ刃
    m.seg('stone_back', S, (-4.5, 28.5), (-7.5, 29.5), 5, 2.25, 3, 5, tip=True)       # -x の重り
    for k, (a, b) in enumerate((((-4.75, 25.5), (4.25, 31.5)), ((-4.75, 31.5), (4.25, 25.5)))):
        m.seg(f'lash_{k}', K, a, b, 2, 3, 3, 5, over=0)                               # 銅の線の縛り（表と裏で×）
    m.box('rod_tip', K, [-1, 33, -1], [1, 36, 1], 5)
    return m


def storm_anvil_hammer():
    """鉄床雲の鎚: 頂が平らに広がる雷雲の頭。避雷針が雲を突き抜け、雲の下の +x から稲妻が落ちる"""
    m = M('storm_anvil_hammer')
    rod(m, -12, 8, 40)
    m.box('anvil_top', S, [-10, 34, -2.5], [10, 37, 2.5], grad=(34, 6, 37, 8))       # 平らな頂
    m.box('tower_l', S, [-6.5, 27, -3], [0.5, 34.5, 3], grad=(27, 3, 34.5, 6))
    m.box('tower_r', S, [-0.5, 25.5, -3], [6.5, 34.5, 3], grad=(25.5, 3, 34.5, 6))
    m.box('puff_l', S, [-9, 29, -2.25], [-5.5, 33, 2.25], grad=(29, 4, 33, 6))
    m.box('puff_r', S, [6, 28, -2.25], [9.5, 33.5, 2.25], grad=(28, 4, 33.5, 6))
    m.seg('rod_spike', K, (0, 39), (0, 44), 2, 1, 4, 5, tip=True)                     # 避雷針の先
    m.chain('bolt', B, [(5, 26), (8, 22.5), (6, 20), (9.5, 16)], [2.5, 2, 2], 0.75, 3, 5, tip=True)
    return m


# ---------------------------------------------------------------- 杖

def storm_staff():
    """雷羽の杖: 避雷針の先の銅の筒に、一枚の大きな羽根が差してある。羽根の先は黒（筆）。光なし"""
    m = M('storm_staff')
    rod(m, -14, 2, 31)
    m.box('quill_cap', K, [-1.5, 30, -1.5], [1.5, 34, 1.5], grad=(30, 3, 34, 5))
    shaft = [(0, 33.5), (0.75, 40), (2, 47), (3.5, 53), (4.5, 57)]
    m.chain('rachis', S, shaft, 2, 0.75, 7, 9)
    for k in range(4):
        (x0, y0), (x1, y1) = shaft[k], shaft[k + 1]
        for t in (0.25, 0.75):
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            L = 5.5 - 0.6 * (k * 2 + (t > 0.5))
            m.seg(f'vane_l{k}{int(t * 4)}', S, (x - 0.5, y), (x - L, y + L * 0.55), 3, 0.5, 6, 8, tip=True, zc=-0.2)
            m.seg(f'vane_r{k}{int(t * 4)}', S, (x + 0.5, y), (x + L * 0.85, y + L * 0.7), 3, 0.5, 6, 8, tip=True, zc=0.2)
    return m


def storm_cloud_staff():
    """雲籠の杖: 雲の筋を曲げた籠の中に、球電が浮く"""
    m = M('storm_cloud_staff')
    rod(m, -14, 2, 33)
    m.box('cup', K, [-3, 32, -2], [3, 34.5, 2], grad=(32, 3, 34.5, 5))
    m.arc('wisp_l', S, (0, 40.5), 7, 265, 95, 6, 2.5, 1, 5, 8)
    m.arc('wisp_r', S, (0, 40.5), 7, -85, 85, 6, 2.5, 1, 5, 8)
    m.arc('wisp_f', S, (0, 40.5), 5, 250, 110, 5, 2, 0.5, 5, 8, zc=-3)
    m.arc('wisp_b', S, (0, 40.5), 5, -70, 70, 5, 2, 0.5, 5, 8, zc=3)
    m.box('knot', S, [-2, 46.5, -2], [2, 49.5, 2], grad=(46.5, 6, 49.5, 8))
    m.seg('knot_curl', S, (1.5, 48.5), (4, 51), 2, 1, 7, 9, tip=True)
    m.box('ball', B, [-2.5, 38, -2.5], [2.5, 43, 2.5], gem=True)
    return m


# ---------------------------------------------------------------- 鈴杖

def storm_bell_staff():
    """風見の鈴杖: 避雷針の先に風見の矢（銅の頭と羽根の尾）、その下の横木の端から丸い鈴が下がる。光なし"""
    m = M('storm_bell_staff')
    rod(m, -14, 2, 40)
    m.seg('arrow', K, (-7, 39), (7.5, 39), 2, 1, 3, 5)
    m.seg('arrow_head_u', K, (8.5, 39), (5.5, 41.5), 2, 1, 4, 5, tip=True)
    m.seg('arrow_head_d', K, (8.5, 39), (5.5, 36.5), 2, 1, 4, 5, tip=True)
    m.seg('tail_u', S, (-6.5, 39.5), (-10, 42.5), 2.5, 0.5, 7, 9, tip=True)
    m.seg('tail_d', S, (-6.5, 38.5), (-10, 35.5), 2.5, 0.5, 7, 9, tip=True)
    m.seg('bar', S, (-6, 33), (6, 33), 2, 1, 4, 6)
    m.box('bar_tip_l', K, [-7.5, 32, -1.25], [-5.5, 34, 1.25], 4)
    m.box('cord', K, [-7.5, 28, -0.75], [-5.5, 32, 0.75], 3)
    suzu(m, 'bell', -6.5, 26, 2.25)
    return m


def storm_thunder_bell_staff():
    """雷鳴りの鈴杖: 避雷針の先で翼が左右に広がり、翼の下に銅の管の風鈴。真ん中に稲妻の短冊が下がって光る"""
    m = M('storm_thunder_bell_staff')
    rod(m, -16, 2, 36)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', S, [(0, 35), (s * 5, 38), (s * 10, 39), (s * 14, 37)], [3.5, 3, 2.5], 1, 4, 8, tip=True)
        for k, (x, L) in enumerate(((5, 5), (9.5, 7))):
            top = 37 if k == 0 else 37.5
            m.box(f'cord_{n}{k}', K, [s * x - 0.5, top - 2.5, -0.5], [s * x + 0.5, top, 0.5], 4)
            m.box(f'tube_{n}{k}', K, [s * x - 1, top - 2.5 - L, -1], [s * x + 1, top - 2.5, 1], grad=(top - 2.5 - L, 2, top - 2.5, 5))
    m.box('crown', K, [-1.5, 35, -1.5], [1.5, 37.5, 1.5], 4)
    m.chain('tanzaku', B, [(0, 34.5), (1.5, 31), (-1, 28), (1, 24.5)], [2.5, 2.5, 2], 0.5, 4, 2, tip=False)
    return m


# ---------------------------------------------------------------- 短剣・爪

def storm_dagger():
    """稲妻の短剣: 刃そのものがジグザグの稲妻。折れ角に小さな火花が散る"""
    m = M('storm_dagger')
    rod(m, -6, 5, 5, tassel=True)
    m.seg('guard', K, (-4, 6), (4, 6), 2.5, 1.5, 3, 5, tip=False)
    pts = [(0, 6.5), (2.25, 11.5), (-1.75, 16.5), (2, 21.5), (0, 27)]
    m.chain('blade', S, pts, [3.5, 3, 2.5, 2], 0.9, 4, 9, tip=True)
    for k, (x, y) in enumerate(((3.5, 11.5), (-3, 16.5), (3.25, 21.5))):
        m.box(f'spark_{k}', B, [x - 1, y - 1, -0.6], [x + 1, y + 1, 0.6], 4)
    return m


def raiju_claw():
    """雷獣の爪: 毛の掌から三本の爪が扇に開き、爪と爪の間に電弧が走る"""
    m = M('raiju_claw')
    rod(m, -8, 3, 3, tassel=False)
    m.box('cuff', K, [-2.5, 2.5, -2.25], [2.5, 4.5, 2.25], 4)
    m.box('paw', S, [-3.5, 4.5, -2.25], [4.5, 10.5, 2.25], grad=(4.5, 2, 10.5, 5))
    m.seg('fur_l', S, (-3.5, 7), (-6, 9.5), 2, 1, 4, 6, tip=True)
    m.seg('fur_r', S, (4.5, 7), (7, 9), 2, 1, 4, 6, tip=True)
    claws = {
        'a': [(-2, 10), (-4, 15), (-4.5, 20), (-3, 24)],
        'b': [(0.5, 10), (0.5, 16), (1.5, 22), (3, 26)],
        'c': [(3, 10), (5.5, 15), (7, 19.5), (7, 23.5)],
    }
    for n, pts in claws.items():
        m.chain(f'claw_{n}', S, pts, [3, 2.5, 2], 1.1, 6, 9, tip=True)
    m.chain('arc_ab', B, [(-4, 18), (-2, 19.5), (-1.5, 17.5), (0.75, 19)], 2, 0.4, 3, 5, tip=False)
    m.chain('arc_bc', B, [(1.5, 21), (3.5, 19.5), (4.5, 21.5), (6.5, 20)], 2, 0.4, 3, 5, tip=False)
    return m


# ---------------------------------------------------------------- 弓

def thunder_wing_bow():
    """雷翼の弓: 弓腕が鳥の翼の骨で、背の側に羽根が段に並ぶ（先は黒、筆）。光なし"""
    m = M('thunder_wing_bow')
    bow_grip(m)
    arm = [(12, 20.5), (12.5, 27), (12, 33), (10.5, 38.5), (8, 43), (5, 46.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', S, [mirror(p, sign) for p in arm], [3, 3, 2.5, 2.5, 2], 1.25, 3, 7, tip=True)
        for k, L in enumerate((6, 5.5, 5, 4)):
            x, y = arm[k + 1]
            a, b = mirror((x + 1, y), sign), mirror((x + 1 + L * 0.8, y - L * 0.6), sign)
            m.seg(f'plume_{n}{k}', S, a, b, 2.5, 0.5, 7, 9, tip=True)
        m.seg(f'nock_{n}', K, mirror((6, 46), sign), mirror((3.5, 46), sign), 2, 1, 4, 5)
    strings(m, (4, 46), (4, -16))
    for sign, n in ((1, 'u'), (-1, 'd')):
        q = mirror((11.25, 33), sign)
        m.chain(f'claw_{n}', K, [q, (q[0] - 2.25, q[1] + sign * 0.75), (q[0] - 3.25, q[1] - sign * 0.75)], [1.4, 1], 0.8, 3, 5, tip=True)
    return m


def wind_streaks(m, name, lines, box, at, mirror15):
    """風の筋（板の絵）: 細い線が流れて先で小さく巻く。線は根元が太く先が細い"""
    cv = lib.Canvas(*box)
    curves = []
    for pts, curl in lines:
        ex, ey = pts[-1]
        c = [(ex + abs(curl) * math.sin(a / 6 * math.pi * 1.6), ey + curl * (1 - math.cos(a / 6 * math.pi * 1.6))) for a in range(1, 7)]
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
    """嵐の風弓（伝説）: 太い嵐雲色の弓腕の先が大きな風の渦に巻き込む。弓腕のまわりを風の輪が二重に取り巻いて
    （奥行きにも回る）、腕からは長い風の筋（板の絵、先で小さく巻く）が後ろへたなびく。握りの前には銅の風見の羽根が三枚開き、
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
        for zs, zn in ((1, 'f'), (-1, 'b')):
            wind_streaks(m, f'wind_{n}{zn}', [([(15.5, 29.5), (19, 31.5), (23, 30.5), (27, 32)], 1.6 * zs),
                                               ([(15, 35), (18.5, 37.5), (22, 36.75), (25, 38.25)], -1.2 * zs),
                                               ([(14, 41), (17, 43.5), (20.5, 43), (22.5, 45)], 1.1 * zs)],
                         (13, 26, 31, 50), zs * 1.55, sign < 0)
        # 腕を取り巻く風の輪（奥行きにも回る）
        for r, (cx, cy) in enumerate(((14.6, 31), (12, 41.5))):
            c = P((cx, cy))
            R = 4.25 - r * 0.6
            for j in range(8):
                a = math.radians(j * 360 / 8 + r * 20)
                x, z = c[0] + R * math.cos(a), R * math.sin(a)
                m.box(f'ring_{n}{r}{j}', S if j % 2 else K, [x - 0.9, c[1] - 0.45 + j * 0.15 * sign, z - 0.9], [x + 0.9, c[1] + 0.45 + j * 0.15 * sign, z + 0.9], 7 if j % 2 else 4)
    top, bot = (5, 47), (5, -17)
    strings(m, top, bot, mat=B)
    for key, pull in lib.PULLS:
        for k, y in enumerate((39, 35, -5, -9)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_chime{k}', K, [q[0] - 0.9, q[1] - 2.25, -0.25], [q[0] + 0.9, q[1], 0.25], 3 + k % 2)
    return m


# ---------------------------------------------------------------- 剣

def storm_sword():
    """雷翼の剣: 鍔は上へ反る銅の翼、刃は細長い避雷の鉄。刃の付け根を銅の爪が噛み、雷を受けた先だけが光る"""
    m = M('storm_sword')
    rod(m, -10, 3, 3)
    m.box('grip_band_0', K, [-1.75, -6.5, -1.75], [1.75, -5, 1.75], 4)               # 握りを締める銅の輪
    m.box('grip_band_1', K, [-1.75, -1, -1.75], [1.75, 0.5, 1.75], 4)
    m.box('guard', S, [-3, 3, -2], [3, 6, 2], grad=(3, 3, 6, 5))
    m.box('guard_cap', S, [-2.25, 6, -1.5], [2.25, 7, 1.5], 6)                         # 鍔の上の小さな座
    m.box('guard_rivet_f', K, [-0.75, 3.75, -2.4], [0.75, 5.25, -2], 5)
    m.box('guard_rivet_b', K, [-0.75, 3.75, 2], [0.75, 5.25, 2.4], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', K, [(sd * 2.5, 4.5), (sd * 7, 5.5), (sd * 10.5, 7.5), (sd * 12.5, 10.5)], [3, 2.5, 2], 1.25, 3, 5.5, tip=True)
        # 翼の下に垂れる風切り羽（嵐雲の色、外ほど短い）
        m.seg(f'wing_{n}_f0', S, (sd * 6, 4.75), (sd * 7.5, 1.5), 1.75, 0.6, 5, 7, tip=True)
        m.seg(f'wing_{n}_f1', S, (sd * 9.5, 6.5), (sd * 11.25, 4), 1.5, 0.6, 5, 7, tip=True)
        # 刃の付け根を噛む銅の爪
        m.seg(f'claw_{n}', K, (sd * 3, 6.5), (sd * 1.25, 10.5), 1.75, 1.15, 3, 5, tip=True)
        # 刃は左右の半分を少し傾けて重ね、段を作らずに先へ細らせる
        m.seg(f'blade_{n}', S, (sd * 2.25, 5.5), (sd * 0.25, 44), 4.5, 0.9, 3, 6.5)
        m.seg(f'edge_{n}', S, (sd * 4.25, 6), (sd * 2.35, 44), 2, 1.0, 6, 8.5)         # 刃の縁（明るい筋）
        m.seg(f'tip_{n}', B, (sd * 2, 43), (0, 51.5), 2.5, 0.8, 2, 5, tip=True)        # 雷を受けた先
    m.box('fuller', S, [-0.5, 9, -1.05], [0.5, 36, 1.05], grad=(9, 1, 36, 3))            # 刃の中の暗い樋
    # 樋に沿って走る銅の稲妻（三つ折れ）
    m.chain('bolt_inlay', K, [(0, 13), (1.25, 18), (-1, 22.5), (0.75, 28), (0, 32)], 1, 1.15, 4, 5.5, tip=False, over=0.3)
    return m


ALL = [storm_hammer, storm_anvil_hammer, storm_staff, storm_cloud_staff, storm_bell_staff, storm_thunder_bell_staff,
       storm_dagger, raiju_claw, thunder_wing_bow, storm_gale_bow, storm_sword]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
