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
    """顎の骨（太く）: 反った幅広の顎骨の刃、背に歯の列、歯の根元が灯る。鍔は椎骨＋左右の胸びれ、刃の根元にフジツボ"""
    m = Model('whalebone_greatsword')
    grip(m, -8, 6, -8, bands=(-1,), wrap_to=6, butt='none')
    m.box('guard', 'sea', [-5.5, 6, -3.5], [5.5, 9.5, 3.5], 4, rim=True)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'fin_{n}', 'sea', (s * 5, 8), (s * 11, 5), 3.5, 1.6, 4, 7, tip=True)
    pts = [(0, 9), (1.5, 22), (3, 36), (3.2, 48), (2, 58), (0, 64)]
    m.chain('jaw', 'bone', pts, [8, 8, 7, 5.5, 3.5], [1.6, 1.6, 1.5, 1.3, 1.1], 2.4, 5)
    m.chain('jaw_edge', 'sea', [(q[0] + 3.6 - k * 0.3, q[1]) for k, q in enumerate(pts[:5])], [1, 1, 0.9, 0.8], 1.65, 6, 8, tip=False)
    for k, y in enumerate((14, 22, 30, 38, 46)):
        cx = 0.4 + 2.6 * (y - 9) / 37
        m.seg(f'tooth_{k}', 'bone', (cx - 3.2, y), (cx - 7.5, y + 2.5), 2.6, 1.2, 4, 6, tip=True)
        m.box(f'gum_{k}_glow', 'glow', [cx - 4, y - 1, -1.8], [cx - 2, y + 1, 1.8], 3)
    for k, (x, y, s) in enumerate(((2.5, 12, 2.4), (-1.5, 15, 1.8))):
        m.box(f'barnacle_{k}', 'sea', [x - s / 2, y - s / 2, -1.9], [x + s / 2, y + s / 2, 1.9], 6)
    return m


def whale_dagger():
    """一角の牙（ねじれ）: 牙の節を少しずつ回して交互の明暗でねじれを見せる。鍔は尾びれ、柄頭に小さな光"""
    m = Model('whale_dagger')
    grip(m, -6, 5, -6, bands=(-1,), wrap_to=5, butt='glow')
    m.box('guard', 'sea', [-3.5, 5, -2.5], [3.5, 8, 2.5], 4)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'fluke_{n}', 'sea', (s * 3, 6.5), (s * 9, 10.5), 3.5, 1.3, 4, 8, tip=True)
    y = 8
    for k in range(8):
        w = 5 - k * 0.45
        m.seg(f'twist_{k}', 'bone', (0.3 * (-1) ** k, y), (-0.3 * (-1) ** k, y + 3.6), w, 1.9 - k * 0.12, 4 + (k % 2) * 2, 5 + (k % 2) * 2, over=0.3)
        y += 3.6
    m.seg('tip_glow', 'glow', (0, y), (0, y + 5), 2, 1, 3, 5, tip=True)
    return m


def whale_staff():
    """潮吹き（大きく）: 紺の鯨の頭が上を向き、噴き口から光る水柱が高く立ち、左右に大きなしぶきの弧と粒"""
    m = Model('whale_staff')
    grip(m, -14, 30, -14, bands=(10, 22), wrap_to=0, butt='spike')
    m.seg('head', 'sea', (0, 29), (0, 38), 8, 3.4, 3, 6)
    m.box('head_lip', 'bone', [-4.5, 37, -3], [4.5, 38.5, 3], 4)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.box(f'eye_{n}_glow', 'glow', [s * 3.9 - 0.5, 32, -1], [s * 3.9 + 0.5, 33.5, 1], 4)
    m.chain('spout_glow', 'glow', [(0, 38.5), (0, 45), (0, 52), (0, 57)], [4, 3.2, 2.2], [2, 1.6, 1.1], 3, 5)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'splash_{n}_glow', 'glow', [(s * 1, 55), (s * 6, 57.5), (s * 10.5, 55), (s * 12, 49)], [3, 2.5, 2], 1.2, 5, 3, tip=True)
        for k, (x, y) in enumerate(((12.5, 44), (8.5, 47.5), (11, 40))):
            m.box(f'drop_{n}{k}_glow', 'glow', [s * x - 0.9, y - 0.9, -0.9], [s * x + 0.9, y + 0.9, 0.9], 4)
    return m


def whale_star_staff():
    """持ち上げた尾びれ（大物）: 大きな尾びれが左右へ高く反り、縁は骨、間に大きな星の結晶。星のまわりに小さな星が三つ浮く"""
    m = Model('whale_star_staff')
    grip(m, -14, 32, -14, bands=(10, 22), wrap_to=0, butt='spike')
    m.seg('peduncle', 'sea', (0, 31), (0, 38), 5, 2.4, 4, 6)
    for s, n in ((-1, 'l'), (1, 'r')):
        pts = [(0, 37), (s * 5, 40), (s * 10, 45), (s * 14, 51), (s * 15, 58)]
        m.chain(f'fluke_{n}', 'sea', pts, [6, 5, 4, 2.5], [1.8, 1.6, 1.4, 1.2], 4, 8.5)
        m.chain(f'fluke_rim_{n}', 'bone', [(q[0] + s * 1.6, q[1] - 1.2) for q in pts[1:4]], 0.9, 1.85, 5, 6, tip=False)
    for k, (x, y, sz) in enumerate(((0, 49, 5), (-6, 58, 1.8), (6.5, 60, 1.6), (0, 63, 1.4))):
        h = sz / 2
        m.seg(f'mote_star_{k}_glow', 'glow', (x - h, y - h), (x + h, y + h), sz, 1.4 if k == 0 else 0.8, 4, 5, over=0)
        if k == 0:
            m.seg('mote_star_x_glow', 'glow', (x - h, y + h), (x + h, y - h), sz, 1.4, 4, 5, over=0)
    return m


def whale_bell_staff():
    """ひげ板の簾（大きく）: 反った骨の横木から長さの違うひげ板が六枚垂れ、先が光る。右の端に大きな鐘"""
    m = Model('whale_bell_staff')
    grip(m, -14, 38, -14, bands=(8, 24), wrap_to=0, butt='none')
    m.arc('yoke', 'bone', (0, 54), 15, 230, 310, 6, 3, 1.6, 4, 4)
    for k, (x, L) in enumerate(((-10, 13), (-7.5, 10), (-5, 8), (-2.5, 6))):
        top = 54 - math.sqrt(15 ** 2 - x ** 2) + 0.5
        m.box(f'baleen_{k}', 'sea', [x - 1.1, top - L, -1.8], [x + 1.1, top, 1.8], grad=(top - L, 3, top, 6))
        m.box(f'baleen_{k}_glow', 'glow', [x - 1.1, top - L - 1.6, -1.2], [x + 1.1, top - L, 1.2], 4)
    m.box('cord_r', 'bone', [8.5, 35.5, -1], [10.5, 41, 1], 3)
    bell(m, 'bell', 9.5, 35.5, 1.4, clapper=True)
    return m


def whale_deep_bell_staff():
    """鯨の背（大物）: 柄の先が鯨の背の大きな弧（背びれ付き、発光の点が並ぶ）、尾びれに開いて大きな鐘と小さな鐘が下がる"""
    m = Model('whale_deep_bell_staff')
    grip(m, -16, 32, -16, bands=(8, 20), wrap_to=2, butt='none')
    m.arc('back', 'sea', (9, 32), 9, 180, 15, 8, [4.5, 4.5, 4.5, 4, 4, 3.5, 3, 3], 2.0, 3, 6)
    m.seg('dorsal', 'sea', (7, 40.5), (11, 47), 3.5, 1.4, 5, 7.5, tip=True)
    m.seg('tail_l', 'sea', (17.5, 34), (14.5, 38.5), 3, 1.4, 6, 8.5, tip=True)
    m.seg('tail_r', 'sea', (17.5, 34), (22.5, 37.5), 3, 1.4, 6, 8.5, tip=True)
    m.box('tail_cap', 'bone', [16, 30, -2.2], [19, 34, 2.2], 4)
    m.box('cord', 'bone', [17, 26, -0.6], [18, 30, 0.6], 3)
    bell(m, 'bell', 17.5, 26, 1.6, clapper=True)
    m.box('cord2', 'bone', [3.5, 30, -0.5], [4.5, 33, 0.5], 3)
    bell(m, 'bell_s', 4, 30, 0.8, clapper=True)
    for k, a in enumerate((165, 135, 105, 75, 45)):
        x, y = 9 + 8.6 * math.cos(math.radians(a)), 32 + 8.6 * math.sin(math.radians(a))
        m.box(f'spot_{k}_glow', 'glow', [x - 0.9, y - 0.9, -2.3], [x + 0.9, y + 0.9, 2.3], 4)
    return m


def whale_choir_hammer():
    """鯨の頭骨（大物）: 太い吻（打つ面）、開いた下顎とひげ板、光る目、後ろへ大きく反る骨の角が二本、額にフジツボ"""
    m = Model('whale_choir_hammer')
    grip(m, -12, 22, -12, bands=(-1, 15), wrap_to=12, butt='none')
    m.box('collar', 'bone', [-3, 20, -3], [3, 24, 3], 3)
    m.box('cranium', 'sea', [-6, 24, -5.5], [7, 40, 5.5], 4, rim=True)
    m.box('snout', 'sea', [7, 27, -4.5], [19, 37, 4.5], grad=(27, 4, 37, 6))
    m.box('snout_face', 'bone', [19, 28, -4], [21.5, 36, 4], 4)
    m.box('jaw', 'bone', [5, 21.5, -4], [19, 25, 4], grad=(21.5, 3, 25, 4))
    for k, x in enumerate((8, 11, 14, 17)):
        m.box(f'baleen_{k}', 'bone', [x - 1, 25, -3], [x + 1, 27.5, 3], 4)
    for s, n in ((-1, 'l'), (1, 'r')):
        m.box(f'eye_{n}_glow', 'glow', [0, 32, s * 5.5 - 0.25], [3.5, 35.5, s * 5.5 + 0.25], 4)
    for k, z in enumerate((-2.5, 2.5)):
        m.chain(f'horn_{k}', 'bone', [(-5.5, 36), (-10, 40), (-13.5, 46), (-14, 52)], [4, 3.2, 2.2], [1.6, 1.4, 1.1], 3, 5, zc=z)
    for k, (x, y, s) in enumerate(((1, 41, 2.6), (4.5, 40.5, 1.8))):
        m.box(f'barnacle_{k}', 'sea', [x - s / 2, y - 1.2, -s / 2], [x + s / 2, y + s - 1.2, s / 2], 7)
    return m


def whale_bow():
    """鯨肋の弓（エピック）: 弓腕そのものが鯨の下顎の骨。弦の側（腹）に歯が並び、歯の根元に紺の歯茎の筋。
    先は顎の関節のこぶで弦を受け、顎の付け根の小さな目が灯る。弦には骨の留めが二つ"""
    m = Model('whale_bow')
    bow_grip(m, gems=False)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.25, 38.5), (6.75, 42.5), (3.5, 44.5)]
    top, bot = (2.5, 45), (2.5, -15)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: (q[0], 15 + s * (q[1] - 15))
        m.chain(f'jaw_{n}', 'bone', [P(q) for q in arm], [4.6, 4.4, 4.0, 3.4, 2.8], 1.8, 3, 5, tip=False)
        belly = lib.offset_line(arm, -1.7)
        nr = lib.line_normals(arm)
        m.chain(f'gum_{n}', 'sea', [P(q) for q in belly[1:5]], [0.9, 0.9, 0.8], 1.3, 4, 5, tip=False)
        for k in range(1, 5):
            a = belly[k]
            b = (a[0] - nr[k][0] * 3.4, a[1] - nr[k][1] * 3.4 - 0.6)
            m.seg(f'tooth_{n}{k}', 'bone', P(a), P(b), 2.0, 1.1, 6, 9, tip=True)
        q = P((3.5, 44.75))
        m.box(f'condyle_{n}', 'bone', [q[0] - 1.9, q[1] - 1.5, -1.8], [q[0] + 1.9, q[1] + 1.5, 1.8], 6)
        g = P((12.75, 30))
        m.box(f'eye_{n}_glow', 'glow', [g[0] + 1.2, g[1] - 0.7, -0.7], [g[0] + 1.8, g[1] + 0.7, 0.7], 4)
    strings(m, top, bot, list(lib.PULLS))
    for key, pull in lib.PULLS:
        for k, y in enumerate((36, -6)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_tooth{k}', 'bone', [q[0] - 1.1, q[1] - 1.4, -1.1], [q[0] + 1.1, q[1] + 1.4, 1.1], 5)
            m.box(f'string_{key}_tooth{k}_glow', 'glow', [q[0] - 0.5, q[1] - 0.5, -1.25], [q[0] + 0.5, q[1] + 0.5, 1.25], 4)
    return m


def whale_rib_longbow():
    """肋骨の籠（伝説）: 上下の弓腕がそれぞれ一頭の鯨。握りの近くが太い胴で、先へ細って弓の先で尾びれが横（奥行き）に開く。
    背は深い海の色、腹は骨の白で、腹には畝の筋が二本。背には深海の光の斑点が並び、目も光る。胴の脇から胸びれが一枚ずつ。
    握りは骨に深海の光の結晶。弦には椎骨の玉が連なる"""
    m = Model('whale_rib_longbow')
    body = [(12, 21), (15, 27.5), (16, 34), (14.5, 40), (11, 44.5), (6, 47.5), (1.5, 49)]
    back = lib.offset_line(body, 2.6)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: (q[0], 15 + s * (q[1] - 15))
        m.chain(f'body_{n}', 'sea', [P(q) for q in body], [7, 7.5, 6.5, 5, 3.5, 2], 2.0, 2, 4, tip=False)
        m.chain(f'belly_{n}', 'bone', [P(q) for q in lib.offset_line(body, -2.6)[0:5]], [2.2, 2.4, 2, 1.5], 2.05, 4, 5, tip=False)
        for j, d in enumerate((-2.0, -3.0)):
            m.chain(f'groove_{n}{j}', 'sea', [P(q) for q in lib.offset_line(body, d)[0:5]], [0.35] * 4, 2.1, 2, 3, tip=False)
        for k, t in enumerate((1, 2, 3, 4)):
            q = P(back[t])
            m.box(f'spot_{n}{k}_glow', 'glow', [q[0] - 0.45, q[1] - 0.45, -2.05], [q[0] + 0.45, q[1] + 0.45, 2.05], 4 + k % 2)
        e = P((16.25, 25.5))
        m.box(f'eye_{n}_glow', 'glow', [e[0] - 0.6, e[1] - 0.6, -2.1], [e[0] + 0.6, e[1] + 0.6, 2.1], 4)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            q = P((13.5, 29))
            m.box(f'flipper_{n}{zn}', 'sea', [q[0] - 1, q[1] - 2.25, min(zs * 1.9, zs * 4.25)], [q[0] + 1, q[1] + 1, max(zs * 1.9, zs * 4.25)], 3)
            m.box(f'flipper_edge_{n}{zn}', 'bone', [q[0] - 0.75, q[1] - 2.25, min(zs * 4.25, zs * 4.75)], [q[0] + 0.75, q[1] + 0.25, max(zs * 4.25, zs * 4.75)], 5)
        f = P((1.5, 49))
        m.seg(f'fluke_{n}c', 'sea', f, (f[0] - 1, f[1] + sign * 0.6), 2.2, 1.0, 4, 5, over=0.3)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            m.box(f'fluke_{n}{zn}', 'sea', [f[0] - 1.75, f[1] - 0.6, min(0, zs * 5)], [f[0] + 0.75, f[1] + 0.6, max(0, zs * 5)], 4)
            m.box(f'fluke_tip_{n}{zn}', 'bone', [f[0] - 1.25, f[1] - 0.5, min(zs * 5, zs * 6.25)], [f[0] + 0.25, f[1] + 0.5, max(zs * 5, zs * 6.25)], 5)
    bow_grip(m, legend=True, gems=True)
    top, bot = (1, 48.5), (1, -18.5)
    strings(m, top, bot, list(lib.PULLS))
    for key, pull in lib.PULLS:
        for k, y in enumerate((44, 40, 36, 32, -2, -6, -10, -14)):
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
