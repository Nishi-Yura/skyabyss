# -*- coding: utf-8 -*-
"""鯨のシリーズ（look5・個性版）。ユーザー: 「祠の武器に寄せすぎ」「シリーズごとにもっと個性や奇抜な物に」「剣でか」。
祠の組み方（段の刃・箱の頭）を使わず、鯨の体のモチーフで一品ずつ形を立てる。色は鯨の鎚 v5 と同じ（紺 → 泡の白、骨、光る空色）。

  顎の大剣・一角の短剣・潮吹きの杖・尾びれの星杖・ひげ板の鈴杖・鯨の背の深い鈴杖・頭骨の合唱の鎚・フジツボの弓・肋骨の籠の長弓
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402
from whale_series import grip, bell, bow_grip, strings  # noqa: E402


def whalebone_greatsword():
    """鯨の顎の骨: 少し反った長い骨の刃、背（-x）に歯が並ぶ、光る溝。前より小さく（全長 64）"""
    m = Model('whalebone_greatsword')
    grip(m, -6, 6, -6, bands=(0,), wrap_to=6, butt='none')
    m.box('guard', 'sea', [-4.5, 6, -3], [4.5, 9, 3], 4)           # 背骨の一節（椎骨）
    m.seg('guard_fin_l', 'sea', (-4, 7.5), (-8, 6), 2.5, 1.5, 4, 6, tip=True)
    m.seg('guard_fin_r', 'sea', (4, 7.5), (8, 6), 2.5, 1.5, 4, 6, tip=True)
    pts = [(0, 8.5), (1, 20), (2.4, 32), (2.8, 42), (2, 50), (0.2, 56)]
    m.chain('jaw', 'bone', pts, [6, 6, 5.5, 4.5, 3], [1.4, 1.4, 1.3, 1.2, 1], 2.4, 5)
    for k, y in enumerate((14, 21, 28, 35, 42)):
        cx = 0.3 + 2.4 * (y - 9) / 33
        m.seg(f'tooth_{k}', 'bone', (cx - 2.5, y), (cx - 5.5, y + 2), 2, 1, 3.5, 5, tip=True)
        m.box(f'gum_{k}', 'glow', [cx - 3.25, y - 1, -1.6], [cx - 1.25, y + 1, 1.6], 3)   # 歯の根元（歯茎）が灯る
    return m


def whale_dagger():
    """一角鯨の牙: 真っ直ぐ細く尖る牙に、斜めのねじれの筋。鍔は小さな尾びれ"""
    m = Model('whale_dagger')
    grip(m, -6, 5, -6, bands=(-1,), wrap_to=5, butt='none')
    m.box('guard', 'sea', [-3, 5, -2.5], [3, 7.5, 2.5], 4)
    m.seg('guard_fluke_l', 'sea', (-2.5, 6), (-7.5, 9), 3, 1.25, 4, 7, tip=True)
    m.seg('guard_fluke_r', 'sea', (2.5, 6), (7.5, 9), 3, 1.25, 4, 7, tip=True)
    m.chain('tusk', 'bone', [(0, 7), (0, 17), (0, 27), (0, 33)], [5, 4, 3], [2, 1.75, 1.25], 3, 5, tip=False)
    m.seg('tusk_tip', 'glow', (0, 33), (0, 40), 2.5, 1, 2, 5, tip=True)               # 牙の先が光に溶ける
    # ねじれの溝は Blockbench の筆で牙の面に斜めに描いた（板を足すと牙から浮いて見えたので外した）
    return m


def whale_staff():
    """潮吹き: 噴き口から光る水柱が立ち、左右へしぶきの弧が落ちる"""
    m = Model('whale_staff')
    grip(m, -14, 30, -14, bands=(10,), wrap_to=0, butt='spike')
    m.box('blowhole', 'sea', [-3.5, 30, -3.5], [3.5, 34, 3.5], 4, rim=True)
    m.box('blowhole_lip', 'bone', [-3, 34, -3], [3, 35, 3], 4)
    m.chain('spout', 'glow', [(0, 34.5), (0, 40), (0, 46), (0, 50)], [3.5, 3, 2], [1.75, 1.5, 1], 3, 5)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'splash_{n}', 'glow', [(s * 1, 48.5), (s * 4.5, 50.5), (s * 7.5, 49), (s * 9, 45.5)], [2.5, 2, 2], 1, 5, 3, tip=False)
        m.box(f'drop_{n}', 'glow', [s * 9.5 - 1, 40.5, -1], [s * 9.5 + 1, 42.5, 1], 3)
        m.box(f'drop2_{n}', 'glow', [s * 6 - 1, 44, -1], [s * 6 + 1, 46, 1], 4)
    return m


def whale_star_staff():
    """持ち上げた尾びれ: 柄の先から二枚の葉が左右へ反り上がり、その間に星の結晶が浮く"""
    m = Model('whale_star_staff')
    grip(m, -14, 32, -14, bands=(10, 22), wrap_to=0, butt='spike')
    m.box('peduncle', 'sea', [-2.5, 32, -2], [2.5, 36, 2], 4)
    for s, n in ((-1, 'l'), (1, 'r')):
        pts = [(0, 35), (s * 4, 37.5), (s * 8, 41), (s * 11.5, 46), (s * 12.5, 51)]
        m.chain(f'fluke_{n}', 'sea', pts, [4.5, 4, 3, 2], [1.5, 1.25, 1, 1], 4, 8.5)
    m.box('star_l_glow', 'glow', [-7, 49.5, -1], [-5, 51.5, 1], 4)   # Blockbench で散らした位置
    m.box('star_r_glow', 'glow', [4.5, 52.5, 0], [6.5, 54.5, 2], 4)
    m.box('star_t_glow', 'glow', [-1.5, 56, -1], [0.5, 58, 1], 4)
    return m


def whale_bell_staff():
    """ひげ板の風鈴: 反った骨の横木の左に、長さの違う鯨のひげ板が垂れ、右の端に鐘"""
    m = Model('whale_bell_staff')
    grip(m, -14, 38, -14, bands=(8, 24), wrap_to=0, butt='none')
    m.arc('yoke', 'bone', (0, 52), 14, 235, 305, 5, 2.5, 1.5, 4, 4)
    m.seg('yoke_tip_l', 'bone', (-8, 40.5), (-10.5, 43.5), 2, 1, 4, 5, tip=True)
    m.seg('yoke_tip_r', 'bone', (8, 40.5), (10.5, 43.5), 2, 1, 4, 5, tip=True)
    for k, (x, L) in enumerate(((-8, 11), (-5.5, 8), (-3, 5))):
        top = 52 - math.sqrt(14 ** 2 - x ** 2) + 0.5
        m.box(f'baleen_{k}', 'sea', [x - 1, top - L, -1.5], [x + 1, top, 1.5], grad=(top - L, 3, top, 6))
        m.box(f'baleen_{k}_glow', 'glow', [x - 1, top - L - 2, -1], [x + 1, top - L, 1], 4)
    m.box('cord_r', 'bone', [7, 36, -1], [9, 40, 1], 3)
    bell(m, 'bell', 8, 36, 1.2, clapper=False)
    return m


def whale_deep_bell_staff():
    """鯨の背: 柄の先が大きく弧を描き（背びれ付き）、先が尾びれに開いて、そこから鐘が下がる"""
    m = Model('whale_deep_bell_staff')
    grip(m, -16, 32, -16, bands=(8, 20), wrap_to=2, butt='none')
    m.arc('back', 'sea', (8, 32), 8, 180, 15, 8, [3.5, 3.5, 3.5, 3, 3, 3, 2.5, 2.5], 1.75, 3, 6)
    m.seg('dorsal', 'sea', (6, 39.5), (9, 44.5), 3, 1.25, 5, 7.5, tip=True)
    m.seg('tail_fluke_l', 'sea', (15.5, 34), (13, 37.5), 2.5, 1.25, 6, 8.5, tip=True)
    m.seg('tail_fluke_r', 'sea', (15.5, 34), (19.5, 36.5), 2.5, 1.25, 6, 8.5, tip=True)
    m.box('tail_cap', 'bone', [14, 30, -2], [17, 34, 2], 4)
    m.box('cord', 'bone', [14.5, 27, -1], [16.5, 30, 1], 3)
    bell(m, 'bell', 15.5, 27, 1.4, clapper=False)
    for k, a in enumerate((160, 125, 90, 55)):                                # 背の弧の脇に並ぶ発光の点（深海の鯨）
        x, y = 8 + 7.5 * math.cos(math.radians(a)), 32 + 7.5 * math.sin(math.radians(a))
        m.box(f'spot_{k}', 'glow', [x - 1, y - 1, -2.1], [x + 1, y + 1, 2.1], 4)
    return m


def whale_choir_hammer():
    """鯨の頭骨: 長い吻（打つ面）、口を開けた下顎、口にかかるひげ、光る目、後ろへ反る骨の角"""
    m = Model('whale_choir_hammer')
    grip(m, -12, 22, -12, bands=(-1, 15), wrap_to=12, butt='none')
    m.box('collar', 'bone', [-2.5, 20, -2.5], [2.5, 24, 2.5], 3)
    m.box('cranium', 'sea', [-5, 24, -4.5], [6, 37, 4.5], 4, rim=True)
    m.box('snout', 'sea', [6, 27, -3.5], [16, 35, 3.5], grad=(27, 4, 35, 6))
    m.box('snout_face', 'bone', [16, 28, -3], [18, 34, 3], 4)
    m.box('jaw', 'bone', [4, 22, -3], [16, 25, 3], grad=(22, 3, 25, 4))
    for k, x in enumerate((8, 11, 14)):
        m.box(f'baleen_{k}', 'bone', [x - 1, 25, -2], [x + 1, 27, 2], 4)
    m.box('eye_f_glow', 'glow', [0, 30, -4.9], [3, 33, -4.4], 4)
    m.box('eye_b_glow', 'glow', [0, 30, 4.4], [3, 33, 4.9], 4)
    m.chain('horn', 'bone', [(-4.5, 32), (-9, 35), (-12, 40), (-12.5, 45)], [3.5, 3, 2], [1.75, 1.5, 1], 3, 5)
    return m


def whale_bow():
    """フジツボの肋骨弓: 骨の弓腕（回した節で反る）に、フジツボの塊がつく"""
    from whale_series import bow_body
    m = Model('whale_bow')
    bow_body(m, [(12, 21), (13.5, 26), (14, 31), (13, 35)], [(13, 35), (10, 39.5), (5, 42.5), (0, 44), (-3, 44.5)],
             3, [3, 2.5, 2, 2], thorns=False)
    for k, (x, y, s) in enumerate(((16, 25, 4), (16.5, 30, 3), (15.5, 34, 2.5), (16, 5, 4), (16.5, 0, 3), (15.5, -4, 2.5))):
        m.box(f'barnacle_{k}', 'sea', [x - s / 2, y - s / 2, -s / 2 - 0.5], [x + s / 2, y + s / 2, s / 2 + 0.5], grad=(y - s / 2, 5, y + s / 2, 7))
        if s >= 3:                                                            # 大きいフジツボの口（外向きの面）が灯る
            m.box(f'barnacle_{k}_mouth', 'glow', [x + s / 2 - 0.5, y - 1, -1], [x + s / 2 + 0.25, y + 1, 1], 4)
    bow_grip(m, gems=False)
    m.seg('nock_u', 'bone', (-1, 44.3), (-3.5, 44.6), 2, 1.25, 4, 5)
    m.seg('nock_d', 'bone', (-3.5, -14.6), (-1, -14.3), 2, 1.25, 4, 5)
    strings(m, (-3, 44.5), (-3, -14.5), [('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)])
    for sign, n in ((1, 'u'), (-1, 'd')):
        y = 15 + sign * 16
        for zs, zn in ((1, 'f'), (-1, 'b')):
            z0 = 1.75 if zs > 0 else -1.75
            m.box(f'zbarnacle_{n}{zn}', 'sea', [12.25, y - 1.5, min(z0, z0 + zs * 2.5)], [15.25, y + 1.5, max(z0, z0 + zs * 2.5)], 6)
            z1 = z0 + zs * 2.5
            m.box(f'zbarnacle_{n}{zn}_mouth', 'glow', [13.25, y - 0.6, min(z1, z1 + zs * 0.3)], [14.25, y + 0.6, max(z1, z1 + zs * 0.3)], 4)
    return m


def rib_net(m, name, ribs, mirror15):
    """肋骨の外へ垂れる網（板の絵）: 肋骨の中ほどから外へ掛けて垂らした斜めの目の網、結び目は骨の白、糸は深い海の色"""
    poly = [a for a, _ in ribs] + [b for _, b in ribs][::-1]
    cv = lib.Canvas(18, 18, 34, 48)

    def f(x, y, cur):
        if not lib.inside((x, y), poly):
            return None
        u, v = (x + y) / 1.6, (x - y) / 1.6
        fu, fv = u - math.floor(u), v - math.floor(v)
        if fu < 0.34 and fv < 0.34:
            return ('bone', 6)
        if fu < 0.34 or fv < 0.34:
            return ('sea', 7)
        return None
    cv.paint(f)
    cv.put(m, name, mirror15=mirror15)


def whale_rib_longbow():
    """肋骨の籠（伝説）: 長い骨の背骨の弓腕から、外へ肋骨が何本も弧を描いて出て籠をなす。肋骨は背だけでなく
    左右（奥行き）にも張り出し、肋骨の外へは深い海の色の網（板の絵、結び目は骨の白）が掛けて垂らされ、籠の中には鯨の心臓の光の塊が浮かぶ。肋骨の先は深海の光で光り、
    握りの左右には深海の光の結晶。下の先からは骨の尾びれが垂れる。弦には椎骨の玉が連なる"""
    from whale_series import bow_body
    m = Model('whale_rib_longbow')
    bow_body(m, [(12, 21), (14, 28), (15, 35), (14.5, 41), (12, 45)], [(12, 45), (8, 48), (3, 49.5), (-1, 50)],
             [3.75, 3.5, 3, 2.75], [3, 2.5, 2], thorns=False)
    ribs = [((x + L * 0.6, y + L * 0.35), (x + L * 0.6 + 9, y + L * 0.35 - 3.5)) for x, y, L in ((14.5, 25, 8), (15.5, 31, 9.5), (15.5, 37, 8.5), (14.25, 42.5, 6.5))]
    for sign, n in ((1, 'u'), (-1, 'd')):
        rib_net(m, f'net_{n}', ribs, sign < 0)
        for k, (x, y, L) in enumerate(((14.5, 25, 8), (15.5, 31, 9.5), (15.5, 37, 8.5), (14.25, 42.5, 6.5))):
            yy = 15 + sign * (y - 15)
            a = (x, yy)
            mid = (x + L * 0.6, yy + sign * L * 0.35)
            b = (x + L * 0.75, yy + sign * L * 0.95)
            m.seg(f'rib_{n}{k}_0', 'bone', a, mid, 2, 1, 3, 4.5)
            m.seg(f'rib_{n}{k}_tip', 'glow', mid, b, 1.9, 0.95, 3, 5, tip=True)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            for k, (x, y) in enumerate(((14.75, 28), (15.25, 34), (14.75, 40))):
                yy = 15 + sign * (y - 15)
                z0 = 1.75 if zs > 0 else -1.75
                for j in range(3):
                    zz = z0 + zs * j * 1.75
                    xo = x + j * 1.1
                    m.box(f'zrib_{n}{zn}{k}{j}', 'bone', [xo - 0.75, yy - 0.8, min(zz, zz + zs * 1.9)], [xo + 0.75, yy + 0.8, max(zz, zz + zs * 1.9)], 3.5 + j * 0.4)
        c = (24, 15 + sign * 14)
        m.box(f'heart_{n}_glow', 'glow', [c[0] - 1.75, c[1] - 2, -1.75], [c[0] + 1.75, c[1] + 2, 1.75], grad=(c[1] - 2, 3, c[1] + 2, 5))
        m.box(f'heart_vein_{n}', 'sea', [c[0] - 2.1, c[1] - 0.4, -2.1], [c[0] + 2.1, c[1] + 0.4, 2.1], 5)
    bow_grip(m, legend=True, gems=True)
    m.seg('fluke_l', 'bone', (-1, -20), (-4.5, -23.5), 2.5, 1.1, 4, 5, tip=True)
    m.seg('fluke_r', 'bone', (-1, -20), (2.5, -23.75), 2.5, 1.1, 4, 5, tip=True)
    top, bot = (-1, 50), (-1, -20)
    strings(m, top, bot, list(lib.PULLS))
    for key, pull in lib.PULLS:
        for k, y in enumerate((45, 41, 37, 33, -3, -7, -11, -15)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_vert{k}', 'bone', [q[0] - 0.9, q[1] - 0.75, -0.9], [q[0] + 0.9, q[1] + 0.75, 0.9], 4 + k % 2)
    return m


ALL = [whalebone_greatsword, whale_dagger, whale_staff, whale_star_staff, whale_bell_staff, whale_deep_bell_staff,
       whale_choir_hammer, whale_bow, whale_rib_longbow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
