# -*- coding: utf-8 -*-
"""潮のシリーズ（look5・個性版）。シリーズの生き物・題材のモチーフで一品ずつ形を立てる。
色: 珊瑚（深い赤紫 → 珊瑚 → 桃 → 泡の白）、差し色は貝（クリーム）、光は真珠（桃白）。

  骨貝の法螺の鎚・枝珊瑚の礁の鎚・開いた二枚貝の杖・大小の海月の真珠杖・三つの巻貝の鈴杖・波に乗る海馬の鈴杖・
  縞の反ったエイの棘・鰻の顎の牙・棘の蟹の鋏・枝分かれの円錐貝の三叉・錨の銛・海亀の盾・珊瑚の枝の弓・マンタの翼の弓

2026-11-19: 弓以外を階級に合わせて作り直した（エピック = 飾り 2〜3 か所、ヒロイック = 輪郭が変わる大物の飾り）。
形は鯨から借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 握りはまっすぐな珊瑚の茎に巻き、その上は節ごとに少し折れる珊瑚の枝（節にポリプの芽）。下はタケノコ貝の尖り。輪や柄頭の玉は付けない。
- 光（真珠）は品ごとに場所を変える: 殻口の奥・枝先のポリプ・二枚貝の真珠・海月の芯・海馬の目と波の泡・エイの毒の先・鰻の喉・
  鋏が挟む真珠・錨の腕の付け根。巻貝の鈴杖・三叉・盾は光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

C, S, P = 'coral', 'shell', 'pearl'


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    """45 度に回した四角（菱）"""
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


def M(name):
    return Model(name, accent=S, glowmat=P)


def rbox(m, name, mat, c, L, w, d, ang, t0, t1=None, tip=False, zc=0.0):
    """中心 c・長さ L（重ねしろ込み）・太さ w・奥行き ±d・角度 ang の回した節（Blockbench で目で直した値を写すため）"""
    u = (math.cos(math.radians(ang)), math.sin(math.radians(ang)))
    h = L / 2 - 0.5
    m.seg(name, mat, (c[0] - u[0] * h, c[1] - u[1] * h), (c[0] + u[0] * h, c[1] + u[1] * h), w, d, t0, t1, tip=tip, zc=zc)


def stalk(m, y0, grip_to, y1, knuckles=(), butt=True, nubs=True):
    """潮の柄: 握り（y0..grip_to）はまっすぐな珊瑚の茎に巻き。その上は節ごとに少し折れる珊瑚の枝で y1 まで、節にポリプの芽。下にタケノコ貝"""
    m.box('grip', C, [-1.5, y0, -1.5], [1.5, grip_to, 1.5], grad=(y0, 1.5, grip_to, 3), wrap=(y0, grip_to))
    if y1 > grip_to:
        pts = [(0, grip_to - 0.5)] + [(x, y) for x, y in knuckles] + [(0, y1)]
        n = len(pts) - 1
        for k in range(n):
            m.seg(f'stem_{k}', C, pts[k], pts[k + 1], 3, 1.5, 3 + 2 * k / n, 3 + 2 * (k + 1) / n)
        if nubs:
            for k, (x, y) in enumerate(knuckles):
                sd = 1 if x <= 0 else -1                                   # 折れた側の反対へ芽を出す
                m.seg(f'polyp_{k}', C, (x + sd * 1, y), (x + sd * 3.5, y + 2), 2, 1, 5, 7, tip=True)
    if butt:                                                               # タケノコ貝（細く長い巻貝）の尖り
        m.chain('auger', S, [(0, y0 + 0.5), (0.25, y0 - 2.5), (0.5, y0 - 5), (0.25, y0 - 7.5)],
                [3.5, 2.75, 2], [1.75, 1.5, 1.1], 3, 5)


def whelk(m, prefix, cx, top, s=1.0):
    """巻貝の鈴: とがった殻頂（上）、ふくらんだ胴、珊瑚の口（舌は付けない）"""
    m.seg(f'{prefix}_spire', S, (cx, top - 3 * s), (cx, top + 1.5 * s), 3 * s, 1.5 * s, 2.5, 5, tip=True)
    m.box(f'{prefix}_body', S, [cx - 2.75 * s, top - 8 * s, -2.75 * s], [cx + 2.75 * s, top - 3 * s, 2.75 * s],
          grad=(top - 8 * s, 2, top - 3 * s, 4), rim=s > 1.2)
    m.box(f'{prefix}_lip', C, [cx - 3.5 * s, top - 10 * s, -3.25 * s], [cx + 3.5 * s, top - 8 * s, 3.25 * s], 5)


# ---------------------------------------------------------------- 形の部品（2026-11-19 の作り直し）

def spikes(m, prefix, mat, base_pts, ang, L, w, d, t0=3, t1=5):
    """根元の点の列から角度 ang・長さ L の尖りを生やす（角度・長さは列か一つの数）"""
    for k, c in enumerate(base_pts):
        a = ang[k] if isinstance(ang, (list, tuple)) else ang
        u = (math.cos(math.radians(a)), math.sin(math.radians(a)))
        ln = L[k] if isinstance(L, (list, tuple)) else L
        m.seg(f'{prefix}_{k}', mat, c, (c[0] + u[0] * ln, c[1] + u[1] * ln), w, d, t0, t1, tip=True)


def conch(m, cx=0, cy=32, s=1.0, knobs=True):
    """横に寝かせた法螺貝: +x の殻口（唇と光る真珠層）、-x へ細る殻頂、継ぎ目の貝の帯"""
    m.box('whorl', C, [cx - 6 * s, cy - 7 * s, -5 * s], [cx + 7 * s, cy + 7 * s, 5 * s], 4, rim=True)
    m.box('whorl_round', C, [cx - 5 * s, cy - 8.5 * s, -4 * s], [cx + 6 * s, cy + 8.5 * s, 4 * s], grad=(cy - 8.5 * s, 3, cy + 8.5 * s, 5))
    m.chain('spire', C, [(cx - 6 * s, cy), (cx - 11 * s, cy + 1), (cx - 15.5 * s, cy + 1.5), (cx - 19 * s, cy + 1.75)],
            [11 * s, 7.5 * s, 4 * s], [4.75 * s, 3.25 * s, 1.75 * s], 4, 7.5)
    m.box('band_0', S, [cx - 9.5 * s, cy - 4.5 * s, -4.9 * s], [cx - 7.5 * s, cy + 5 * s, 4.9 * s], 4)
    m.box('band_1', S, [cx - 14 * s, cy - 2.5 * s, -3.4 * s], [cx - 12.5 * s, cy + 4 * s, 3.4 * s], 4)
    m.box('lip', S, [cx + 7 * s, cy - 10.5 * s, -6 * s], [cx + 9.5 * s, cy + 10 * s, 6 * s], grad=(cy - 10.5 * s, 3, cy + 10 * s, 5))
    m.box('aperture', P, [cx + 8.5 * s, cy - 6 * s, -4 * s], [cx + 10.5 * s, cy + 6 * s, 4 * s], grad=(cy - 6, 2, cy + 6, 4))
    if knobs:
        spikes(m, 'knob', S, [(cx - 4 * s, cy + 7.5 * s), (cx, cy + 7.5 * s), (cx + 4 * s, cy + 7.5 * s)], [115, 100, 80], 5 * s, 2.6, 1.6)


def reef_head(m):
    m.box('reef', C, [-8, 24, -4.5], [9, 37, 4.5], grad=(24, 3, 37, 5), rim=True)
    m.box('brain_face', C, [9, 24.5, -4.25], [14, 36.5, 4.25], grad=(24.5, 5, 36.5, 7))
    m.box('brain_rim', S, [13.5, 23.5, -4.75], [15, 37.5, 4.75], 4)


def jelly(m, cx, cy, r, prefix='', core=True):
    m.arc(f'{prefix}dome', C, (cx, cy), r, 180, 0, 8, r * 0.9, r * 0.55, 4, 8.5)
    if core:
        m.box(f'{prefix}core', P, [cx - r * 0.7, cy + 0.5, -r * 0.55], [cx + r * 0.7, cy + r * 0.75, r * 0.55], gem=True)
    m.box(f'{prefix}frill', S, [cx - r * 1.2, cy - 2, -r * 0.5], [cx + r * 1.2, cy, r * 0.5], 4)


def seahorse(m, ox=0, flip=1, prefix=''):
    f = lambda x: ox + flip * x
    m.arc(f'{prefix}neck', C, (f(6), 32), 6, 180 if flip > 0 else 0, 25 if flip > 0 else 155, 7, [4, 4, 4, 3.5, 3.5, 3.5, 3.5], 2.2, 3.5, 6.5)
    m.box(f'{prefix}head', C, [min(f(9), f(15)), 33, -2.75], [max(f(9), f(15)), 40, 2.75], grad=(33, 5, 40, 7))
    a = -37.87 if flip > 0 else 180 + 37.87
    rbox(m, f'{prefix}snout', C, (f(16.5), 33), 8, 3.5, 1.5, a, 6.5, 8.5, tip=True)
    for k, (c, an) in enumerate((((-1.6, 36.5), 150), ((2.3, 40.5), 115), ((7.5, 41.5), 80), ((11.5, 41.5), 95))):
        ang = an if flip > 0 else 180 - an
        rbox(m, f'{prefix}crest_{k}', S, (f(c[0]), c[1]), 5, 2.4, 1.2, ang, 3, 5, tip=True)
    m.box(f'{prefix}eye_f_glow', P, [min(f(11.5), f(13.5)), 36, -3.25], [max(f(11.5), f(13.5)), 38, -2.75], 4)
    m.box(f'{prefix}eye_b_glow', P, [min(f(11.5), f(13.5)), 36, 2.75], [max(f(11.5), f(13.5)), 38, 3.25], 4)


def eel_head(m, cx=0, cy=4, s=1.0):
    m.box('head', C, [cx - 3.5 * s, cy, -3 * s], [cx + 3.5 * s, cy + 6 * s, 3 * s], grad=(cy, 3, cy + 6, 5), rim=True)
    m.box('throat_glow', P, [cx + 2.5 * s, cy + 1.5 * s, -1.5 * s], [cx + 6 * s, cy + 4.5 * s, 1.5 * s], 4)
    rbox(m, 'jaw', C, (cx + 5 * s, cy + 0.25 * s), 7 * s, 3.4 * s, 2 * s, -22, 5, 7, tip=True)
    rbox(m, 'snout', C, (cx + 5.25 * s, cy + 5.5 * s), 7.5 * s, 3.4 * s, 2 * s, 18, 5, 7, tip=True)
    for k, (dx, dy) in enumerate(((3, 1.5), (5, 2), (7, 2.5))):
        m.seg(f'tooth_u{k}', S, (cx + dx * s, cy + (4.5 + dy * 0.3) * s), (cx + dx * s, cy + 3.2 * s), 0.9, 1.2, 4, 5, tip=True)
    m.box('eye_f', S, [cx - 0.5, cy + 3 * s, -3.5 * s], [cx + 1.5, cy + 5 * s, -3 * s], 5)
    m.box('eye_b', S, [cx - 0.5, cy + 3 * s, 3 * s], [cx + 1.5, cy + 5 * s, 3.5 * s], 5)


def claw(m, s=1.0, ox=0, oy=0, prefix=''):
    X = lambda x: ox + x * s
    Y = lambda y: oy + y * s
    m.box(f'{prefix}palm', C, [X(-4.5), Y(5), -3 * s], [X(7), Y(14), 3 * s], grad=(Y(5), 3, Y(14), 5), rim=True)
    for k, (c, a, L, w, d) in enumerate((((6.5, 15.5), 63.43, 8, 5.5, 2.4), ((8, 21.5), 90, 7.5, 5, 2.1),
                                         ((6.75, 27), 119.74, 6.5, 3.8, 1.8), ((3.75, 30.25), 149.04, 5, 2.6, 1.2))):
        rbox(m, f'{prefix}fix_{k}', C, (X(c[0]), Y(c[1])), L * s, w * s, d * s, a, 5 + k, 6 + k, tip=k == 3)
    for k, (c, a, L, w, d) in enumerate((((-3.25, 15.5), 101.31, 7.5, 4.4, 2.1), ((-3.25, 21.5), 77.47, 7, 3.8, 1.8),
                                         ((-1, 26), 45, 5.6, 2.6, 1.2))):
        rbox(m, f'{prefix}mov_{k}', C, (X(c[0]), Y(c[1])), L * s, w * s, d * s, a, 5 + k, 6 + k, tip=k == 2)


def cone(m, name, cx, y0, L, w):
    m.box(f'{name}_ring', C, [cx - w * 0.6, y0 - 0.75, -w * 0.45], [cx + w * 0.6, y0 + 0.75, w * 0.45], 5)
    for h, hn in ((-1, 'a'), (1, 'b')):
        m.seg(f'{name}{hn}', S, (cx + h * w * 0.4, y0), (cx, y0 + L), w * 0.55, w * 0.35, 3, 5, tip=True)


def harpoon_shaft(m):
    stalk(m, -16, 0, 38.5, knuckles=[(0.75, 14), (-0.75, 26)], nubs=False)
    m.box('socket', C, [-2.4, 37.5, -2.1], [2.4, 42, 2.1], grad=(37.5, 4, 42, 6))
    m.box('band_d', S, [-2.8, 37, -2.5], [2.8, 38.5, 2.5], 4)
    m.box('band_u', S, [-2.8, 40.75, -2.5], [2.8, 42.25, 2.5], 5)


def turtle(m):
    m.box('marginal', S, [-12, -3.5, -2], [12, 27.5, 0], grad=(-3.5, 2, 27.5, 4))
    m.box('shell_w', C, [-10, -6.5, -2.5], [10, 30.5, -0.5], grad=(-6.5, 2, 30.5, 4))
    m.box('shell_m', C, [-8, -9.5, -2.5], [8, 33.5, -0.5], grad=(-9.5, 2, 33.5, 4))
    m.box('shell_n', C, [-4.5, -11, -2.5], [4.5, 35, -0.5], grad=(-11, 1, 35, 3))
    m.box('handle', S, [-1.5, -3, 0], [1.5, 6, 2.5], grad=(-3, 2, 6, 4))
    m.box('brace_0', S, [-9, 8, 0], [9, 10, 1.25], 3)
    m.box('brace_1', S, [-9, 18, 0], [9, 20, 1.25], 3)
    m.box('brace_v', S, [-1, 6, 0], [1, 22, 1], 2)


# ---------------------------------------------------------------- 鎚

def tide_hammer():
    """法螺貝の鎚（エピック）: 横に寝かせた骨貝（ホネガイ）風の法螺貝。殻頂と胴から長い棘が上下に並び、輪郭がとげとげになる。殻口は珊瑚の唇"""
    m = M('tide_hammer')
    stalk(m, -12, 8, 24.5, knuckles=[(0.75, 16)])
    conch(m, knobs=False)
    spikes(m, 'spine_u', S, [(-11, 35), (-6, 39), (-1, 40), (4, 40)], [125, 108, 95, 80], [4.5, 7, 8.5, 6.5], 2.4, 1.4)
    spikes(m, 'spine_d', S, [(-11, 29), (-6, 25), (-1, 24)], [-125, -110, -95], [4, 6, 5], 2.2, 1.3)
    spikes(m, 'spine_x', S, [(-16, 35), (-16, 30)], [140, -140], 4, 1.8, 1.1)
    for k, y in enumerate((24, 40)):
        m.seg(f'lip_point_{k}', C, (8, y), (12, y + (4 if k else -4)), 3, 2, 5, 7, tip=True)
    return m


def tide_reef_hammer():
    """脳珊瑚の礁の鎚（ヒロイック）: +x は脳珊瑚の打つ面。礁の上に枝珊瑚の木が高く立ち（y55 まで）、枝先のポリプ三つが光る"""
    m = M('tide_reef_hammer')
    stalk(m, -12, 8, 24.5, knuckles=[(-0.75, 16)])
    reef_head(m)
    m.chain('trunk', C, [(-3, 36), (-4, 42), (-2, 48), (-3, 54)], [4, 3.2, 2.5], [2, 1.6, 1.25], 5, 8)
    m.chain('arm_l', C, [(-4, 42), (-9, 45), (-11, 50)], [2.6, 2], [1.4, 1.1], 6, 8)
    m.chain('arm_r', C, [(-2.5, 46), (3, 49), (4.5, 54)], [2.4, 2], [1.25, 1], 6, 8)
    m.chain('arm_b', C, [(-7, 36), (-12, 39), (-15, 38)], [2.6, 2], [1.4, 1.1], 6, 8)
    for k, (x, y) in enumerate(((-3, 55.5), (-11, 51.5), (4.5, 55.5), (-16, 39))):
        m.box(f'polyp_{k}_glow', P, [x - 1.4, y - 1.2, -1.4], [x + 1.4, y + 1.6, 1.4], 4)
    return m


# ---------------------------------------------------------------- 杖

def tide_staff():
    """帆立の杖（エピック）: 開いた二枚貝。下の殻は下向き、上の殻は上向きに開き、間に大きな真珠が光る"""
    m = M('tide_staff')
    stalk(m, -14, 2, 30.5, knuckles=[(0.75, 12), (-0.75, 21)])
    m.box('hinge', S, [-3, 30, -2.5], [3, 33, 2.5], 4)
    for k, (y, h) in enumerate(((33, 1.5), (34.5, 1.5), (36, 1.5))):
        w = 6 + k * 3
        m.box(f'low_{k}', C, [-w, y, -2.5], [w, y + h, 2.5], 4 + k)
    m.box('low_rim', S, [-12, 37, -2.7], [12, 38, 2.7], 6)
    m.box('pearl_glow', P, [-4, 37.5, -3.5], [4, 45.5, 3.5], gem=True)
    for k, (y, w) in enumerate(((37.5, 6), (40, 9), (42.5, 11.5), (45, 12.5), (47.5, 12), (50, 10), (52, 6.5))):
        m.box(f'upper_{k}', C, [-w, y, 2.5], [w, y + 2.75, 4.5], 4 + k * 0.6)   # 上の殻: 真珠の後ろに立つ扇
    for k, x in enumerate((-7, -2.3, 2.3, 7)):
        m.box(f'upper_rib_{k}', S, [x - 0.6, 40.5, 4.4], [x + 0.6, 52 - abs(x) * 0.5, 4.9], 5)  # 背の肋
    m.box('upper_rim', S, [-12.75, 46.5, 2.3], [12.75, 48.5, 4.7], 6)
    return m


def tide_pearl_staff():
    """海月の真珠杖（ヒロイック）: 大小の海月。大きい傘の中に口腕が二本。大きな傘の横に小さな海月が浮かび（浮く部品）、どちらの芯も光る"""
    m = M('tide_pearl_staff')
    stalk(m, -14, 2, 38.5, knuckles=[(-0.75, 14), (0.75, 26)])
    jelly(m, 0, 40, 9)
    for k, x in enumerate((-9, -4, 4, 9)):
        m.chain(f'tent_{k}', C, [(x, 38), (x - 1, 33), (x + 0.5, 28), (x - 0.5, 22 + abs(x) * 0.5)], 1.8, 0.9, 6, 3, tip=True)
    for sd, n in ((-1, 'l'), (1, 'r')):                                     # ひだのある口腕
        m.chain(f'oral_{n}', S, [(sd * 1.5, 38), (sd * 3, 33), (sd * 1.5, 28), (sd * 3, 23)], [3, 2.6, 2], 1.4, 5, 3, tip=True)
    jelly(m, 14, 52, 4, prefix='float_small_')
    for k, x in enumerate((11.5, 14, 16.5)):
        m.chain(f'float_tent_{k}', C, [(x, 50), (x + 0.5, 47), (x - 0.5, 44)], 1.2, 0.6, 6, 3, tip=True)
    return m


# ---------------------------------------------------------------- 鈴杖

def tide_bell_staff():
    """巻貝の鈴杖（エピック）: 珊瑚の枝を広げ、巻貝の鈴を三つ（中央は大きく）。鈴の口に珊瑚の縁、枝の節に芽"""
    m = M('tide_bell_staff')
    stalk(m, -14, 2, 35.5, knuckles=[(0.75, 13), (-0.75, 24)])
    m.chain('branch_l', C, [(0, 35), (-5, 39), (-10.5, 42), (-13, 47)], [3, 2.6, 2.2], [1.5, 1.3, 1.1], 5, 8.5)
    m.chain('branch_r', C, [(0, 35), (5, 40), (10, 43), (12.5, 48.5)], [3, 2.6, 2.2], [1.5, 1.3, 1.1], 5, 8.5)
    m.chain('branch_c', C, [(0, 35), (0.5, 42), (-0.5, 50)], [3, 2.4], [1.5, 1.2], 5, 8.5)
    m.seg('bud_l', C, (-6, 39.5), (-6.5, 44), 2, 1, 6, 8, tip=True)
    m.seg('bud_r', C, (7, 41.5), (7, 46), 2, 1, 6, 8, tip=True)
    m.box('cord_l', S, [-13.5, 37, -0.8], [-12, 45, 0.8], 3)
    whelk(m, 'bell_l', -12.75, 37, 1.0)
    m.box('cord_r', S, [11.5, 38, -0.8], [13, 46.5, 0.8], 3)
    whelk(m, 'bell_r', 12.25, 38, 1.0)
    m.box('cord_c', S, [-0.75, 48, -0.75], [0.75, 52, 0.75], 3)
    m.seg('arch_c', C, (-0.5, 49.5), (-0.5, 53), 3, 1.5, 6, 8)
    whelk(m, 'bell_c', -0.5, 59.5, 1.3)
    return m


def tide_surf_bell_staff():
    """海馬の波鈴杖（ヒロイック）: 海馬が波に乗る。背びれ、柄に巻く尾。海馬の背の後ろに大きく巻く波頭（珊瑚から泡の白へ）、波の先の泡が光る"""
    m = M('tide_surf_bell_staff')
    stalk(m, -16, 2, 32.5, knuckles=[(0.75, 12), (-0.75, 22)])
    seahorse(m)
    m.seg('fin', S, (-1.5, 29), (-5.5, 33), 4.5, 0.8, 4, 6)                 # 背びれ
    for k in range(4):                                                     # 柄に巻く尾
        y = 28 - k * 2.4
        m.box(f'tail_coil_{k}', C, [-2.25, y - 1.1, -2.25], [2.25, y + 1.1, 2.25], 4 + k % 2)
    m.chain('wave', C, [(-1, 31), (-7, 34), (-11, 40), (-10, 47), (-5, 50), (-1.5, 47.5)], [5, 5, 4.5, 3.5, 2.5], 1.6, 5, 9, tip=False, zc=-1)
    m.chain('wave_crest', S, [(-10.5, 47), (-5, 50.5), (-1, 48)], [2, 1.5], 1.8, 7, 9, tip=True, zc=-1)
    m.box('foam_glow', P, [-2.5, 45.5, -2.5], [0, 48, 0.5], 4)
    m.box('cord', S, [18.5, 25.5, -0.8], [20, 31, 0.8], 3)
    whelk(m, 'bell', 19.25, 25.5, 1.1)
    return m


# ---------------------------------------------------------------- 短剣・牙・爪

def tide_dagger():
    """エイの棘（エピック）: 縞の反り刃。エイの尾のように少し反る刃に貝の色の縞が四本、鍔の翼は上へ反り上がる。毒の先が光る"""
    m = M('tide_dagger')
    stalk(m, -7, 5, 5)
    m.box('guard', C, [-3, 5, -2.5], [3, 8.5, 2.5], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', C, [(sd * 2.5, 7), (sd * 7, 9), (sd * 9, 14)], [4, 2.6], 1.3, 4, 7.5, tip=True)
    pts = [(0, 8), (0.5, 16), (1.5, 24), (3, 32), (5, 38)]
    m.chain('blade', C, pts, [5, 4.4, 3.6, 2.8], 1.4, 4, 7)
    for k in range(4):
        a, b = pts[k], pts[k + 1]
        c = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        m.box(f'stripe_{k}', S, [c[0] - 2.6 + k * 0.3, c[1] - 0.6, -1.5], [c[0] + 2.6 - k * 0.3, c[1] + 0.6, 1.5], 5)
    m.seg('venom_glow', P, (5, 38), (7, 44), 2, 1, 2, 5, tip=True)
    return m


def waterway_fang():
    """鰻の顎の牙（ヒロイック）: 開いた顎の喉が光る鰻の頭から、長く反る牙（y37 まで）。背に冠のとげ。鰻の体が握りに巻きつく"""
    m = M('waterway_fang')
    stalk(m, -10, 4, 4)
    eel_head(m, s=1.2)
    for k, (c, a, L) in enumerate((((-4.5, 12), 135, 6.5), ((-6, 8.5), 165, 6.5), ((-5.5, 4.5), -164, 5.5))):
        rbox(m, f'crest_{k}', S, c, L, 2.4, 1.2, a, 3, 5, tip=True)
    fang = [(1, 11.5), (2.5, 19), (2.5, 26), (0.5, 32), (-2.5, 37)]
    m.chain('fang', S, fang, [5, 4.2, 3.4, 2.4], [1.7, 1.4, 1.2, 1], 2.5, 5, tip=True)
    for k in range(4):
        y = 2 - k * 3
        m.box(f'coil_{k}', C, [-2.1, y - 1.2, -2.1], [2.1, y + 1.2, 2.1], 4 + k % 2)
    return m


def tidecaller_claw():
    """蟹の鋏（ヒロイック）: 指の外側に棘の列、掌の背に瘤三つ、挟んだ真珠が光る。袖口に甲羅のとげ"""
    m = M('tidecaller_claw')
    stalk(m, -8, 3, 3)
    m.box('cuff', S, [-3, 2.5, -3], [3, 5.5, 3], 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'cuff_spike_{n}', S, (sd * 3, 4), (sd * 6, 2), 2, 1.4, 4, 6, tip=True)
    claw(m)
    spikes(m, 'spike_fix', S, [(10.5, 19), (10.5, 24), (8, 27.5)], [10, 20, 45], 3, 1.6, 1, 4, 6)
    spikes(m, 'spike_mov', S, [(-5.5, 17), (-5.5, 22)], [170, 160], 3, 1.6, 1, 4, 6)
    for k, x in enumerate((-2.5, 1, 4.5)):
        m.box(f'knob_{k}', S, [x - 1.1, 14, -1.6], [x + 1.1, 15.8, 1.6], 4)
    m.box('held_pearl', P, [-1.75, 19.5, -2], [5.75, 25.5, 2], gem=True)
    return m


# ---------------------------------------------------------------- 弓（握りの中心 (12, 15) だけを残し、形は潮で立て直す）

def strings(m, top, bot):
    for key, pull in (('0', 0), ('p0', 2.4), ('p1', 6.8), ('p2', 11.6)):
        if pull == 0:
            m.box(f'string_{key}', 'string', [top[0] - 0.5, bot[1], -0.5], [top[0] + 0.5, top[1], 0.5], 3)
        else:
            mid = (top[0] - pull, 15)
            m.seg(f'string_{key}_u', 'string', mid, top, 1, 0.5, 3, 3, over=0.2)
            m.seg(f'string_{key}_d', 'string', bot, mid, 1, 0.5, 3, 3, over=0.2)


def mirror(p, sign):
    return (p[0], 15 + sign * (p[1] - 15))


def kelp_grip(m, x0, x1, d):
    """細い珊瑚の握りに、昆布（貝の色）を斜めに巻く。表と裏で向きを逆にして螺旋に見せる"""
    m.box('grip', C, [x0, 9, -d], [x1, 21, d], grad=(9, 2, 21, 4))
    for k, y in enumerate((10.5, 14.5, 18.5)):
        m.seg(f'kelp_f{k}', S, (x0 - 0.25, y), (x1 + 0.25, y + 2), 2, 0.3, 3, 5, over=0, zc=-d - 0.2)
        m.seg(f'kelp_b{k}', S, (x0 - 0.25, y + 2), (x1 + 0.25, y), 2, 0.3, 3, 5, over=0, zc=d + 0.2)


def tide_bow():
    """潮殻の弓（エピック）: 弓腕そのものが、珊瑚と貝の縞の部屋を連ねたオウム貝の殻。弓の先で殻が渦に巻き込み、
    渦の芯に真珠が光る。弦は渦の内の巻きが受ける。弦には貝の留めが二つ"""
    m = M('tide_bow')
    kelp_grip(m, 10.5, 13.5, 2)
    arm = [(12, 20.5), (13, 26.5), (12.5, 32.5), (10.5, 37.5), (7.5, 41)]
    c = (6.5, 46.5)
    top, bot = (4.25, 45.25), (4.25, -15.25)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        for k in range(len(arm) - 1):
            m.seg(f'chamber_{n}{k}', S if k % 2 else C, P(arm[k]), P(arm[k + 1]), 4.6 - k * 0.3, 1.8, 4, 6, over=0.15)
        pts = []
        for i in range(10):
            a = math.radians(-60 + i * 40)
            r = 5.6 - i * 0.45
            pts.append((c[0] + r * math.cos(a), c[1] + r * math.sin(a)))
        m.chain(f'whorl_{n}', S, [P(q) for q in [(7.5, 41)] + pts], [3.4 - i * 0.18 for i in range(10)], 1.7, 5, 8, tip=False, over=0.3)
        q = P(c)
        m.box(f'pearl_{n}_glow', 'pearl', [q[0] - 1, q[1] - 1, -1.7], [q[0] + 1, q[1] + 1, 1.7], 4)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((38, -8)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_shell{k}', S, [q[0] - 1, q[1] - 1.2, -1], [q[0] + 1, q[1] + 1.2, 1], 5)
            m.box(f'string_{key}_shell{k}_glow', 'pearl', [q[0] - 0.5, q[1] - 0.5, -1.15], [q[0] + 0.5, q[1] + 0.5, 1.15], 5)
    return m


def bubble_trail(m, name, bubbles, mirror15):
    """泡の列（板の絵）: 輪郭だけの丸い泡、左上に小さな光の点"""
    cv = lib.Canvas(17, 26, 30, 48)

    def f(x, y, cur):
        for bx, by, r in bubbles:
            d = math.hypot(x - bx, y - by)
            if abs(d - r) < 0.3:
                return (P, 4)
            if math.hypot(x - (bx - r * 0.4), y - (by + r * 0.4)) < 0.3:
                return (P, 5)
        return cur
    cv.paint(f)
    cv.put(m, name, mirror15=mirror15)


def tide_coral_bow():
    """マンタの翼の弓（伝説）: 幅広く平たいマンタの翼が弓腕になって外へ大きく張り出す。翼の面には貝の白の斑紋が並び、
    翼の縁は左右（奥行き）へ反り返る。翼の後ろ縁が真珠の光で光り、そのあとを光る泡（板の絵の輪）が離れて追う。
    握りの前には貝殻の扇の冠、下の先は細い尾が弦より下へ長く垂れて先に棘。弦は貝の玉を連ねた数珠で、ところどころ真珠が光る"""
    m = M('tide_coral_bow')
    m.box('grip', C, [10.5, 9, -2.25], [13.5, 21, 2.25], grad=(9, 2, 21, 4), wrap=(9, 21))
    for k, (e, w) in enumerate((((19, 15), 2.5), ((18, 19), 2), ((18, 11), 2), ((16.25, 21.5), 1.5), ((16.25, 8.5), 1.5))):
        m.seg(f'crest_{k}', S, (13.25, 15), e, w, 0.7, 3 + k * 0.3, 5, tip=True)
    diamond(m, 'crest_pearl_glow', P, (14.25, 15), 1.75, 1.2, 4, 5)
    wing = [(12, 20.5), (15, 25), (18.5, 29.5), (20, 34), (18.5, 38.5), (14.5, 42.5), (9, 46), (4, 48.5), (1, 49.5)]
    wd = [5.5, 7, 7, 6.5, 5.5, 4.25, 3, 2]
    edge = [(13.5, 26.5), (15.75, 30.5), (16.5, 34.5), (15, 38), (11.5, 41.5), (7, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P_ = lambda q, s=sign: mirror(q, s)
        m.chain(f'wing_{n}', C, [P_(q) for q in wing], wd, 1.3, 2.5, 8, tip=n == 'u')
        m.chain(f'edge_{n}', P, [P_(q) for q in edge], 2, 1.55, 2, 4, tip=True)
        m.seg(f'horn_{n}', C, P_((10, 19.5)), P_((6, 23)), 2.75, 1.5, 4, 7, tip=True)
        for k, (x, y, s) in enumerate(((17, 28.5, 1.75), (19, 33, 2), (17.75, 37.5, 1.75), (14.5, 40.5, 1.5), (10.5, 44, 1.25))):
            for zs in (1, -1):
                q = P_((x, y))
                m.box(f'spot_{n}{k}{"f" if zs > 0 else "b"}', S, [q[0] - s / 2, q[1] - s / 2, zs * 1.3 - 0.15 if zs > 0 else -1.45],
                      [q[0] + s / 2, q[1] + s / 2, 1.45 if zs > 0 else -1.15], 4 + k % 2)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            for k, (x, y) in enumerate(((20, 31.5), (19.75, 36.5))):
                q = P_((x, y))
                z0, z1 = (1.3, 4.25) if zs > 0 else (-4.25, -1.3)
                m.box(f'flap_{n}{zn}{k}', C, [q[0] - 1.25, q[1] - 2, z0], [q[0] + 0.75, q[1] + 2, z1], 6 + k)
        bubble_trail(m, f'bubble_{n}_glow', [(22, 30, 1.6), (24.25, 35, 1.1), (22.25, 40, 1.35), (26.5, 32.5, 0.8), (19.5, 44, 0.9), (27, 38.5, 0.6), (24.5, 44.5, 0.55)], sign < 0)
    m.chain('tail', C, [(1, -19.5), (-1, -23), (-2, -28), (-1.5, -33), (0.25, -37)], [2.75, 2.25, 2, 1.5], 1.1, 5, 8.5, tip=False)
    m.seg('tail_barb', S, (0.25, -37), (1.25, -40.5), 1.75, 1, 4, 5, tip=True)
    m.seg('tail_barb_l', S, (0, -36.5), (-2, -38), 1, 0.7, 4, 5, tip=True)
    top, bot = (0.5, 49.5), (0.5, -19.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        ys = (45, 41.5, 38, 34.5, 31, 27.5, 24, 6, 2.5, -1, -4.5, -8, -11.5, -15)
        for k, y in enumerate(ys):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            if k % 3 == 1:
                diamond(m, f'string_{key}_pearl{k}_glow', P, q, 1.5, 0.75, 4, 5)
            else:
                m.box(f'string_{key}_shell{k}', S, [q[0] - 0.65, q[1] - 0.65, -0.65], [q[0] + 0.65, q[1] + 0.65, 0.65], 4 + k % 2)
    return m


# ---------------------------------------------------------------- 槍・盾

def tide_shell_trident():
    """円錐貝の三叉（エピック）: 珊瑚の三叉。外の二本が鹿角のように枝分かれし、三本の先に円錐貝。枝分かれの小枝の先は珊瑚の色のまま"""
    m = M('tide_shell_trident')
    stalk(m, -16, 0, 32.5, knuckles=[(0.75, 12), (-0.75, 22)])
    m.box('collar', S, [-3, 31, -2.5], [3, 34, 2.5], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'prong_{n}', C, [(sd * 2, 33), (sd * 6, 36), (sd * 7, 41), (sd * 6.5, 45)], [3.4, 3, 2.6], 1.4, 4, 6.5)
        m.chain(f'tine_{n}', C, [(sd * 6.5, 37), (sd * 11, 39), (sd * 12.5, 43)], [2.4, 2], 1.1, 5, 8, tip=True)
        m.seg(f'tine2_{n}', C, (sd * 7, 33.5), (sd * 10.5, 31.5), 2, 1, 5, 7, tip=True)
        cone(m, f'cone_{n}', sd * 6.5, 44.5, 9, 3.6)
    m.seg('prong_m', C, (0, 33.5), (0, 46), 3.4, 1.6, 4, 6.5)
    cone(m, 'cone_m', 0, 45.5, 11, 4.6)
    return m


def coral_harpoon():
    """珊瑚の銛（ヒロイック）: 錨の銛。穂先の下に左右へ大きく反る二本の鉤（錨の腕）が付き、鉤の先が上を向く。浮きは無く、鉤の付け根の真珠が光る"""
    m = M('coral_harpoon')
    harpoon_shaft(m)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'point_{n}', S, (sd * 1.8, 41.5), (0, 57), 3.2, 1.2, 3, 5)
    h = 3.6 / (2 * math.sqrt(2))
    m.seg('tip', S, (-h, 57 - h), (h, 57 + h), 3.6, 1, 4, 5, over=0)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'fluke_{n}', C, [(sd * 2, 39), (sd * 7, 38), (sd * 10.5, 41), (sd * 11.5, 46)], [3.2, 3, 2.6], 1.5, 5, 7)
        m.seg(f'fluke_tip_{n}', S, (sd * 11.5, 45), (sd * 11, 50), 2.6, 1.2, 3, 5, tip=True)
    m.box('heart_glow', P, [-1.6, 38, -2.6], [1.6, 41, 2.6], gem=True)
    return m


def coral_shield():
    """海亀の甲羅の盾（エピック）: 背の甲板は二段に盛り、縁甲板の歯が五対。四肢と頭、甲羅の縁から珊瑚の小枝"""
    m = M('coral_shield')
    turtle(m)
    for k, y in enumerate((1, 11.5, 22)):
        diamond(m, f'scute_m{k}', C, (0, y), 8, 0.9, 4, 6, zc=-3.25)
        diamond(m, f'scute_m{k}_top', C, (0, y), 4.5, 0.6, 6, 8, zc=-4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        for k, y in enumerate((6, 17)):
            diamond(m, f'scute_{n}{k}', C, (sd * 7, y), 5.5, 0.75, 3, 5, zc=-3)
        for k, y in enumerate((-1, 5, 11, 17, 23)):
            diamond(m, f'rim_{n}{k}', S, (sd * 12.25, y), 3.2, 1, 4, 6, zc=-1)
        m.chain(f'twig_{n}', C, [(sd * 9, 30), (sd * 12, 34.5), (sd * 11.5, 39)], [2.8, 2.2], 1.4, 5, 8, tip=True)
        m.chain(f'flipper_{n}', C, [(sd * 10, 26), (sd * 15, 29), (sd * 18.5, 27)], [4.5, 3], 0.8, 3, 5, tip=True, zc=-1.25)
        m.chain(f'hind_{n}', C, [(sd * 9, -4.5), (sd * 13, -8), (sd * 14.5, -11)], [3.5, 2.5], 0.8, 3, 5, tip=True, zc=-1.25)
    m.box('head', S, [-3, 34.5, -2.5], [3, 40, -0.25], grad=(34.5, 3, 40, 5))
    m.box('snout', S, [-2, 40, -2.2], [2, 41.5, -0.5], 5)
    m.seg('tail', S, (0, -10.5), (0, -14.5), 2.6, 0.75, 3, 5, tip=True, zc=-1.25)
    return m


ALL = [tide_hammer, tide_reef_hammer, tide_staff, tide_pearl_staff, tide_bell_staff, tide_surf_bell_staff,
       tide_dagger, waterway_fang, tidecaller_claw, tide_bow, tide_coral_bow, tide_shell_trident, coral_harpoon, coral_shield]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
