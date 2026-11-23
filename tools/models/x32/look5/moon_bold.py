# -*- coding: utf-8 -*-
"""月のシリーズ（look5・個性版）。六章「月」（46）の装備 12 品。月の満ち欠け・三日月・暈・星・影から一品ずつ形を立てる。
色: 夜空の銀（silver）の 1 系統、差し色は夜の藍（night）、光は淡い金の月光（moonlight）。

形は他のシリーズから借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 藍の紐を巻いた銀の握り、上に藍の輪。柄頭は下へ開いた三日月（∩）。
- 振る武器（剣・太刀・短剣・鎚）は握りの真ん中を原点にする（docs/design/60 の 1.2。2026-11-19 の持ち方の直しで確かめた）。
- 弓: 握りの中心 (12, 15) だけ残す。握りは銀の台に藍の紐、上下に三日月の板。
2026-11-19: 弓以外を階級に合わせて作り直した（レア = 今の形に飾り 1 か所、ヒロイック = 輪郭が変わる大物の飾り）。
- 光は品ごとに場所を変える（同じ種類の場所は 2 回まで、光なしもある）:
  月光の長剣 = 鍔の満月、満ち引きの杖 = 珠の光る半分と月光の滴、朔の杖 = 珠の縁のコロナと光の舌、星灯りの鈴杖 = 輪の四方の星、
  月暈の鈴杖 = 満月を巡る暈と満ち欠けの円、星落ちの弓 = 弓先の星と尾、朔影の短剣 = 黒い刃の縁と影の翼の縁、
  残月の太刀 = 鍔の大きな残月、望月の大鎚 = 角の間の星、月秤の鎚 = 支点の星、欠け月の短剣 = 鍔の星。下弦の弓は光なし。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'silver': ['#141626', '#23263a', '#353a52', '#4c536e', '#66708c', '#8590aa', '#a6b0c6', '#c6cede', '#e2e8f2', '#f8faff'],
    'night': ['#120c26', '#21164a', '#33236e', '#4a3592', '#6650b4', '#8a74d0'],
    'moonlight': ['#7a6428', '#b8973e', '#e0c060', '#f2dc90', '#fdf0c4', '#fffbea'],
})
I, N, L = 'silver', 'night', 'moonlight'


def M(name):
    return Model(name, accent=N, glowmat=L)


def disc(m, name, mat, c, r, d, t, zc=0.0):
    """丸い板（十字に重ねた 2 枚で八角に見せる）。縦の板は奥へ 0.1 下げて面が重ならないように"""
    k = 0.55
    m.box(f'{name}_h', mat, [c[0] - r, c[1] - r * k, zc - d], [c[0] + r, c[1] + r * k, zc + d], t)
    m.box(f'{name}_v', mat, [c[0] - r * k, c[1] - r, zc - d + 0.1], [c[0] + r * k, c[1] + r, zc + d - 0.1], t - 0.5)


def ring(m, name, mat, c, r, n, w, d, t0, t1, a0=0, a1=360):
    m.arc(name, mat, c, r, a0, a1, n, w, d, t0, t1)


def star(m, name, mat, c, s, d, t):
    """四方へ尖る星: 縦と横の菱形の節"""
    m.seg(f'{name}_v', mat, (c[0], c[1] - s), (c[0], c[1] + s), s * 0.7, d, t, t + 1, over=0, tip=False)
    m.seg(f'{name}_h', mat, (c[0] - s, c[1]), (c[0] + s, c[1]), s * 0.7, d - 0.1, t, t + 1, over=0, tip=False)


def crescent(m, name, mat, c, r, w, d, t0, t1, a0=200, a1=520, n=6):
    """三日月の弧（開いた側は a0..a1 の外）"""
    ring(m, name, mat, c, r, n, w, d, t0, t1, a0=a0, a1=a1)


def grip(m, y0, y1, cap=True):
    """月の柄: 藍の紐を巻いた銀の握り、上に藍の輪と銀の薄い円盤。柄頭は下へ開いた三日月"""
    m.box('grip', I, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 2, y1, 4), wrap=(y0, y1))
    m.box('collar', N, [-2.25, y1, -2.25], [2.25, y1 + 1.5, 2.25], grad=(y1, 2, y1 + 1.5, 4))
    m.box('collar_plate', I, [-3, y1 + 0.25, -0.75], [3, y1 + 1.25, 0.75], 6)
    if cap:
        m.box('cap', N, [-2, y0 - 1.5, -2], [2, y0, 2], grad=(y0 - 1.5, 1, y0, 3))
        ring(m, 'pommel', I, (0.75, y0 - 4.25), 2.75, 4, 1.75, 1.0, 5, 7, a0=95, a1=265)
        m.seg('pommel_horn_u', I, (0.5, y0 - 1.75), (2.25, y0 - 1.25), 1.5, 0.9, 6, 8, tip=True)
        m.seg('pommel_horn_d', I, (0.5, y0 - 6.75), (2.5, y0 - 7.5), 1.5, 0.9, 6, 8, tip=True)


def rod(m, y0, y1, bands=()):
    m.box('rod', I, [-1.25, y0, -1.25], [1.25, y1, 1.25], grad=(y0, 3, y1, 6))
    for k, y in enumerate(bands):
        m.box(f'band_{k}', N, [-1.75, y, -1.75], [1.75, y + 1.5, 1.75], grad=(y, 2, y + 1.5, 4))
        m.box(f'band_{k}_plate', I, [-2.5, y + 0.25, -0.6], [2.5, y + 1.25, 0.6], 6)


# ---------------------------------------------------------------- 剣・太刀

def moon_light_sword():
    """月光の長剣: 鍔に満月を抱いた銀の長剣。満月は藍の縁の中で光る。刃はまっすぐ、真ん中に藍の樋、縁は明るい研ぎ
    レア（2026-11-19）: 鍔の両端が上へ反る三日月の角になる"""
    m = M('moon_light_sword')
    grip(m, -5, 5)
    disc(m, 'guard_rim', N, (0, 10), 5.5, 1.4, 3)
    disc(m, 'guard_moon', L, (0, 10), 4, 1.6, 4)
    m.box('quillon', I, [-8.5, 9, -1], [8.5, 11, 1], grad=(9, 4, 11, 6))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'quillon_tip_{n}', I, (sd * 8, 10), (sd * 10.5, 12), 2, 0.9, 6, 8, tip=True)
    m.box('ricasso', I, [-2.25, 15.5, -1.1], [2.25, 18, 1.1], grad=(15.5, 3, 18, 5))
    m.box('blade', I, [-2.5, 18, -0.9], [2.5, 42, 0.9], grad=(18, 4, 42, 6))
    m.box('fuller', N, [-0.5, 19, -1.0], [0.5, 36, 1.0], grad=(19, 1, 36, 3))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = (2.5, 3.25) if sd > 0 else (-3.25, -2.5)
        m.box(f'edge_{n}', I, [x0, 18.5, -0.6], [x1, 41, 0.6], 8)
        m.seg(f'tip_{n}', I, (sd * 2.5, 41.5), (0, 47), 2.25, 0.8, 6, 8, tip=True)
    m.box('tip_core', I, [-1.25, 41, -0.85], [1.25, 44, 0.85], 6)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'horn_{n}', I, [(sd * 9.5, 11.5), (sd * 11, 14), (sd * 10.5, 17), (sd * 8.5, 19)], [2, 1.75, 1.4], 0.9, 6, 8, tip=True)
    return m


def moon_waning_blade():
    """残月の太刀: 反りのある銀の太刀。長い柄に藍の菱巻き、鍔は丸い藍の板に残月（細い三日月）が光る。刃は背へ反り、刃先へ明るい刃文の帯
    ヒロイック（2026-11-19）: 鍔の残月を大きく（輪の外へ張り出す三日月）、柄頭から藍の下げ緒が長く垂れる"""
    m = M('moon_waning_blade')
    grip(m, -6, 6)
    for k, y in enumerate((-4, -0.75, 2.5)):
        m.box(f'diamond_{k}', N, [-1.75, y, -1.75], [1.75, y + 1.5, 1.75], 3)
    disc(m, 'tsuba', N, (0, 9.5), 4.5, 1.3, 2)
    ring(m, 'tsuba_moon', L, (0.5, 9.5), 3, 4, 1.25, 1.45, 3, 5, a0=120, a1=300)
    m.box('habaki', I, [-1.75, 10.5, -1.1], [2, 13, 1.1], grad=(10.5, 4, 13, 6))
    pts = [(0, 12.5)]
    for k in range(1, 9):
        y = 12.5 + k * 4.5
        pts.append((-0.006 * (y - 12.5) ** 2, y))
    widths = [4.25 - 0.15 * k for k in range(8)]
    m.chain('blade', I, pts, widths, 0.9, 4, 7, tip=False)
    edge = [(x + 2.1, y) for x, y in pts[:-1]]
    m.chain('hamon', I, edge, [1.25] * (len(edge) - 1), 0.6, 8, 9, tip=False)
    x, y = pts[-1]
    m.seg('kissaki', I, (x + 1.5, y - 1), (x - 1.5, y + 4.5), 3, 0.8, 7, 9, tip=True)
    m.chain('shinogi', N, [(px - 0.5, py) for px, py in pts[:6]], [0.75] * 5, 0.95, 2, 3, tip=False)
    crescent(m, 'big_moon', L, (0.5, 9.5), 6, 1.6, 1.0, 3, 5, a0=110, a1=310, n=7)
    m.chain('sageo', N, [(0, -10), (1.5, -14), (0.5, -18), (2, -22)], [1.6, 1.4, 1.2], 0.5, 4, 2, tip=True)
    return m


# ---------------------------------------------------------------- 短剣

def moon_crescent_dagger():
    """欠け月の短剣: 細い三日月の刃。背の側へ大きく反り、先は鋭い。鍔は小さな∪の三日月、刃の内に藍の筋
    レア（2026-11-19）: 鍔の∪の中に小さな星が光る"""
    m = M('moon_crescent_dagger')
    grip(m, -4, 4)
    ring(m, 'guard', N, (0, 8.25), 3.25, 4, 1.5, 1.1, 2, 4, a0=200, a1=340)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'guard_horn_{n}', N, (sd * 3, 7.25), (sd * 4, 10.25), 1.5, 1.0, 3, 5, tip=True)
    m.box('socket', I, [-1.75, 5.5, -1.25], [1.75, 8, 1.25], grad=(5.5, 4, 8, 6))
    pts = [(0.25, 7.5), (1, 13), (1, 18.5), (0, 23.5), (-2, 27.5), (-4.75, 30)]
    m.chain('blade', I, pts, [3.5, 3.25, 3, 2.5, 2], 0.85, 4, 7, tip=True)
    m.chain('edge', I, [(x + 1.6, y) for x, y in pts[:4]] + [(-0.5, 25.5)], [1.25, 1.25, 1.25, 1], 0.6, 8, 9, tip=False)
    m.chain('vein', N, [(x - 0.4, y) for x, y in pts[:4]], [0.75, 0.75, 0.75], 0.9, 2, 3, tip=False)
    star(m, 'guard_star', L, (0, 7.75), 1.4, 1.3, 3)
    return m


def moon_umbra_dagger():
    """朔影の短剣: 影の光を縁に走らせた黒い刃。刃は藍の黒、両の縁と先に月光が走る。鍔は後ろへ流れる影の棘が二本
    ヒロイック（2026-11-19）: 鍔の後ろに影の翼（藍の黒い羽が左右に三枚ずつ）、翼の縁が月光"""
    m = M('moon_umbra_dagger')
    grip(m, -4, 4)
    m.box('guard', N, [-3, 5.5, -1.5], [3, 7.5, 1.5], grad=(5.5, 1, 7.5, 3))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'thorn_{n}', N, [(sd * 2.75, 7), (sd * 5, 5.5), (sd * 6.5, 3)], [2, 1.75], 0.9, 1, 3, tip=True)
    m.box('blade', N, [-2, 7.5, -0.85], [2, 25, 0.85], grad=(7.5, 0, 25, 2))
    m.box('ridge', N, [-0.5, 8, -1.0], [0.5, 24, 1.0], grad=(8, 2, 24, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = (sd * 2, sd * 2.75) if sd > 0 else (sd * 2.75, sd * 2)
        m.box(f'edge_{n}', L, [x0, 9, -0.5], [x1, 24.5, 0.5], grad=(9, 2, 24.5, 4))
        m.seg(f'tip_{n}', N, (sd * 2, 24.5), (0, 31), 2.5, 0.8, 1, 2, tip=True)
        m.seg(f'tip_edge_{n}', L, (sd * 2.6, 24.5), (sd * 0.6, 30.5), 0.9, 0.85, 3, 5, tip=True)
    m.box('tip_core', N, [-1.25, 24.5, -0.8], [1.25, 27, 0.8], 2)
    for sd, n in ((-1, 'l'), (1, 'r')):
        for k, (x, y, ln) in enumerate(((3, 6.5, 8.5), (3, 5.5, 6.5), (3, 4.5, 4.5))):
            m.seg(f'wing_{n}{k}', N, (sd * x, y), (sd * (x + ln), y + 4 - k * 2.6), 2.6, 0.8, 1, 3, tip=True)
        m.seg(f'wing_edge_{n}', L, (sd * 3.5, 7.8), (sd * 11, 10.6), 0.8, 0.85, 3, 4, tip=True)
    return m


# ---------------------------------------------------------------- 鎚

def moon_scale_hammer():
    """月秤の鎚: 柄の先に天秤の竿。竿の真ん中に菱形の支点、両端から鎖で分銅が下がる。+x の分銅が打つ面（大きく平ら）
    レア（2026-11-19）: 支点のてっぺんに光る星"""
    m = M('moon_scale_hammer')
    grip(m, -6, 6)
    rod(m, 7.5, 27, bands=(15,))
    m.box('fulcrum_base', N, [-2.5, 26.5, -2], [2.5, 28.5, 2], grad=(26.5, 2, 28.5, 4))
    m.seg('fulcrum', I, (0, 27.5), (0, 33.5), 4, 1.5, 5, 7, over=0, tip=True)
    m.box('beam', I, [-11, 30, -1.25], [11, 32, 1.25], grad=(30, 4, 32, 6))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'beam_end_{n}', N, [sd * 11 - 1.5, 29.5, -1.6], [sd * 11 + 1.5, 32.5, 1.6], 3)
        m.box(f'link_{n}0', I, [sd * 11 - 0.6, 27, -0.5], [sd * 11 + 0.6, 30, 0.5], 5)
        m.box(f'link_{n}1', I, [sd * 11 - 0.5, 24.5, -1], [sd * 11 + 0.5, 27.5, 1], 6)
    # 打つ側（+x）の分銅: 上が細く下が太い釣鐘の形、底が打つ面
    m.box('weight_r_neck', N, [9.75, 23, -1.5], [12.25, 25, 1.5], 3)
    m.box('weight_r', I, [8, 16, -3.25], [14, 23.5, 3.25], grad=(16, 3, 23.5, 6))
    m.box('weight_r_band', N, [7.75, 19, -3.5], [14.25, 20.5, 3.5], 3)
    m.box('weight_r_face', I, [7.5, 14.5, -3.5], [14.5, 16, 3.5], 7)
    # 反対の分銅は小さい（天秤が傾く）
    m.box('weight_l_neck', N, [-12, 23, -1.25], [-10, 24.75, 1.25], 3)
    m.box('weight_l', I, [-13.25, 19, -2.25], [-8.75, 23.25, 2.25], grad=(19, 3, 23.25, 6))
    m.box('weight_l_band', N, [-13.5, 20.5, -2.5], [-8.5, 21.5, 2.5], 3)
    star(m, 'apex', L, (0, 35.5), 2.2, 1.1, 3)
    return m


def moon_full_hammer():
    """望月の大鎚: 満月の石を銀の帯で締めた大鎚。頭は横（x）に長い太い円柱、両の打つ面に満月の石の丸。帯は三本、真ん中に藍の座
    ヒロイック（2026-11-19）: 頭の上に三日月の角が左右から抱く（大きく反り上がる）"""
    m = M('moon_full_hammer')
    grip(m, -6, 6)
    rod(m, 7.5, 25, bands=(16,))
    m.box('socket', N, [-3, 24, -3], [3, 28.5, 3], grad=(24, 1, 28.5, 3))        # 頭の下の面（y 28）まで届かせる
    c = 33.5
    m.box('drum_h', I, [-8, c - 4, -4.5], [8, c + 4, 4.5], grad=(c - 4, 4, c + 4, 6))
    m.box('drum_v', I, [-7.9, c - 5.5, -3], [7.9, c + 5.5, 3], grad=(c - 5.5, 4, c + 5.5, 7))
    for k, x in enumerate((-5.5, 0, 5.5)):
        m.box(f'strap_{k}', I, [x - 0.9, c - 5.9, -3.4], [x + 0.9, c + 5.9, 3.4], 8)
        m.box(f'strap_{k}_side', I, [x - 0.9, c - 4.4, -4.9], [x + 0.9, c + 4.4, 4.9], 7)
    m.box('seat', N, [-2.5, c - 1.5, -5], [2.5, c + 1.5, 5], grad=(c - 1.5, 2, c + 1.5, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        x0, x1 = (8, 8.75) if sd > 0 else (-8.75, -8)
        m.box(f'face_{n}_h', I, [x0, c - 3.25, -3.5], [x1, c + 3.25, 3.5], 8)
        m.box(f'face_{n}_v', I, [x0 - sd * 0.05, c - 4.5, -2.25], [x1 - sd * 0.05, c + 4.5, 2.25], 9)
        m.box(f'crater_{n}', I, [x0 + sd * 0.1, c + 0.5, -1.75], [x1 + sd * 0.1, c + 2, 0], 6)
    m.box('crown', N, [-1.5, c + 5.5, -1.5], [1.5, c + 7.5, 1.5], 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'horn_{n}', I, [(sd * 5.5, 39), (sd * 8, 42.5), (sd * 7.5, 47), (sd * 4.5, 50)], [3, 2.6, 2], 1.4, 6, 8, tip=True)
    m.box('crown_stem', N, [-0.6, 40.5, -0.6], [0.6, 43.5, 0.6], 3)                 # 冠から星を立てる細い軸
    star(m, 'crown_star', L, (0, 44.5), 2, 1.3, 3)
    return m


# ---------------------------------------------------------------- 杖

def moon_tide_staff():
    """満ち引きの杖: 銀の棒の先に三日月の揺り籠（∪）。籠の中に半分だけ光る珠（左が月光、右が藍の影）。籠の角から雫が一つずつ下がる
    レア（2026-11-19）: 籠の角の雫を長い月光の滴に（左右で長さを変える）"""
    m = M('moon_tide_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 34, bands=(13, 23))
    m.box('socket', N, [-2.5, 33, -2.25], [2.5, 35.5, 2.25], grad=(33, 2, 35.5, 4))
    ring(m, 'cradle', I, (0, 42), 7, 6, 2.25, 1.25, 4, 7, a0=190, a1=350)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'horn_{n}', I, (sd * 6.9, 40.8), (sd * 6, 47), 2, 1.1, 6, 8, tip=True)
        m.box(f'drop_{n}', N, [sd * 6.5 - 0.6, 37, -0.6], [sd * 6.5 + 0.6, 39.5, 0.6], 4)
    m.box('orb_lit_h', L, [-4, 40, -2.25], [0, 45, 2.25], grad=(40, 3, 45, 5))
    m.box('orb_lit_v', L, [-2.75, 38.75, -2.15], [0, 46.25, 2.15], 4)
    m.box('orb_dark_h', N, [0, 40, -2.25], [4, 45, 2.25], grad=(40, 1, 45, 3))
    m.box('orb_dark_v', N, [0, 38.75, -2.15], [2.75, 46.25, 2.15], 2)
    m.box('terminator', I, [-0.4, 39, -2.4], [0.4, 46, 2.4], 7)
    m.box('orb_pin', I, [-0.4, 34.5, -0.4], [0.4, 39.5, 0.4], 6)                   # 籠の底から珠を支える銀の針
    for sd, n, ln in ((-1, 'l', 5), (1, 'r', 3.5)):
        m.seg(f'drip_{n}', L, (sd * 6.5, 37.5), (sd * 6.5, 37.5 - ln), 1.2, 0.8, 4, 3, tip=True)
    return m


def moon_new_staff():
    """朔の杖（ヒロイック）: 光をのんだ新月の珠（日食）。黒い珠の縁から光の舌（コロナの吹き出し）が八方へ長短に伸びる"""
    m = M('moon_new_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 35, bands=(13, 24))
    m.box('socket', N, [-2.5, 34, -2.25], [2.5, 36.5, 2.25], grad=(34, 2, 36.5, 4))
    c = (0, 44.5)
    disc(m, 'orb', N, c, 5, 2.4, 0)
    ring(m, 'corona', L, c, 5.75, 10, 1.2, 0.9, 3, 5)
    for k in range(8):
        a = math.radians(22.5 + k * 45)
        ln = 4.5 if k % 2 == 0 else 2.8
        p0 = (c[0] + 5.5 * math.cos(a), c[1] + 5.5 * math.sin(a))
        p1 = (c[0] + (5.5 + ln) * math.cos(a), c[1] + (5.5 + ln) * math.sin(a))
        m.seg(f'flare_{k}', L, p0, p1, 1.6, 0.7, 4, 2, tip=True)
    return m


def moon_starlight_bell_staff():
    """星灯りの鈴杖: 大きな銀の輪に四方へ光る星。輪の頂から銀の鈴が下がり、輪は藍の座で棒に留まる
    レア（2026-11-19）: 輪の下（四つ目の方角）にも星を足す"""
    m = M('moon_starlight_bell_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 33, bands=(13, 23))
    m.box('seat', N, [-3, 32, -2.5], [3, 34.5, 2.5], grad=(32, 2, 34.5, 4))
    c = (0, 42.5)
    ring(m, 'hoop', I, c, 8, 10, 2, 1.0, 5, 7)
    for k, (dx, dy) in enumerate(((0, 1), (1, 0), (-1, 0))):
        star(m, f'star_{k}', L, (c[0] + dx * 8, c[1] + dy * 8), 2.5, 1.3, 3)
    m.box('hanger', N, [-0.6, 46.5, -0.6], [0.6, 49.5, 0.6], 3)
    m.box('bell_top', I, [-1.75, 44.5, -1.75], [1.75, 46.5, 1.75], 6)
    m.box('bell', I, [-2.75, 40, -2.75], [2.75, 44.5, 2.75], grad=(40, 4, 44.5, 7))
    m.box('bell_lip', I, [-3.25, 39, -3.25], [3.25, 40.25, 3.25], 8)
    m.box('clapper', N, [-0.75, 37.5, -0.75], [0.75, 39.25, 0.75], 2)
    star(m, 'star_3', L, (0, 34.5), 2.2, 1.3, 3)
    return m


def moon_halo_bell_staff():
    """月暈の鈴杖: 満月の石を月暈の光の輪が巡る。満月の下に藍の紐で鈴が二つ下がる
    ヒロイック（2026-11-19）: 暈の輪の上に月の満ち欠けの小さな円が八つ並ぶ（新月から満月まで）"""
    m = M('moon_halo_bell_staff')
    grip(m, -14, 2)
    rod(m, 3.5, 34, bands=(13, 23))
    m.box('cup', N, [-3, 33, -2.5], [3, 35.5, 2.5], grad=(33, 2, 35.5, 4))
    c = (0, 42.5)
    m.box('moon_stem', N, [-0.8, 35, -0.8], [0.8, 38.5, 0.8], 3)                  # 受けから満月を立てる軸
    disc(m, 'moon', I, c, 5, 1.75, 7)
    m.box('crater_a', I, [-2.5, 43, -1.85], [-0.5, 45, -1.75], 5)
    m.box('crater_b', I, [1, 39.5, -1.85], [2.5, 41, -1.75], 5)
    ring(m, 'halo', L, c, 8, 10, 1.25, 0.8, 3, 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'cord_{n}', N, [sd * 4 - 0.5, 33.5, -0.5], [sd * 4 + 0.5, 38, 0.5], 3)
        m.box(f'bell_{n}', I, [sd * 4 - 1.75, 30, -1.75], [sd * 4 + 1.75, 33.5, 1.75], grad=(30, 4, 33.5, 7))
        m.box(f'bell_{n}_lip', I, [sd * 4 - 2.25, 29.25, -2.25], [sd * 4 + 2.25, 30.25, 2.25], 8)
    for k in range(8):
        a = math.radians(90 + k * 45)
        c = (9.5 * math.cos(a), 42.5 + 9.5 * math.sin(a))
        f = abs(math.cos(math.radians(k * 22.5)))
        disc(m, f'phase_{k}', I if k % 4 else N, c, 1.4, 0.9, 7 if k % 4 else 1)
        if k % 4:
            m.box(f'phase_lit_{k}', L, [c[0] - 1.2, c[1] - 1.2, -1.0], [c[0] - 1.2 + 2.4 * (1 - f * 0.6), c[1] + 1.2, 1.0], 4)
    return m


# ---------------------------------------------------------------- 弓（握りの中心 (12, 15)）

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
    """月の弓の握り: 藍の紐を巻いた銀の台、上下に外へ反る三日月の板"""
    m.box('grip', I, [10.5, 10, -1.75], [13.5, 20, 1.75], grad=(10, 2, 20, 4), wrap=(10, 20))
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'plate_{n}', N, [mirror(p, sign) for p in ((13.5, 19.5), (15.5, 21), (16, 23))], [1.75, 1.5], 1.9, 2, 4, tip=True)
        m.box(f'band_{n}', I, [10, mirror((0, 20), sign)[1] - (0 if sign > 0 else 1.5), -2.1],
              [14, mirror((0, 20), sign)[1] + (1.5 if sign > 0 else 0), 2.1], 6)


def moon_waning_bow():
    """下弦の弓（レア）: 弓腕そのものが細い三日月。中ほどが一番太く、両端は細く尖って弦を受ける。
    内（弦の側）の縁は夜の藍、背の縁に月光の筋が一本だけ光る"""
    m = M('moon_waning_bow')
    m.box('grip', N, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', I, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'moon_{n}', I, [P(q) for q in arm], [3, 4.6, 5.2, 4.2, 2.6], 1.4, 4, 7, tip=True)
        m.chain(f'inner_{n}', N, [P(q) for q in lib.offset_line(arm, -1.9)[1:5]], [1.2, 1.4, 1.1], 1.45, 3, 4, tip=False)
        m.chain(f'gleam_{n}_glow', L, [P(q) for q in lib.offset_line(arm, 2.0)[1:4]], [0.6, 0.6], 1.45, 4, 5, tip=False)
    strings(m, (4.5, 44.5), (4.5, -14.5))
    return m


def comet_tail(m, name, mirror15):
    """星から弓腕の背へ流れる彗星の尾（板の絵）。芯は明るい月光、縁は藍、先へ行くほど細る。尾のまわりに小さな星屑"""
    path = [(7.5, 50.5), (11, 49.5), (14.75, 46.5), (17.75, 42), (19.75, 36), (20.25, 29.5), (19.5, 23)]
    cv = lib.Canvas(5, 21, 25, 54)
    dust = {(22.75, 38), (23, 31.5), (13.25, 52), (17.25, 49), (22, 25.5), (16, 43.25), (17.5, 33)}

    def f(x, y, cur):
        d, t = lib.near_line((x, y), path)
        w = 2.1 * (1 - t) + 0.45
        if d < w:
            if d < w * 0.35:
                return (L, 5 if t < 0.5 else 4)
            if d < w * 0.7:
                return (L, 3) if t < 0.35 else (I, 7)
            return (N, 4) if t < 0.6 else (N, 3)
        for q in dust:
            if abs(x - q[0]) < 0.3 and abs(y - q[1]) < 0.8 or abs(y - q[1]) < 0.3 and abs(x - q[0]) < 0.8:
                return (L, 5)
        return None
    cv.paint(f)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        cv.put(m, f'{name}{zn}', at=zs * 1.6, mirror15=mirror15)



def moon_starfall_bow():
    """星落ちの弓（伝説）: 銀の弓腕の背に月光の筋が先へ向かって太りながら走り、両端に四方へ尖る大きな星が灯り、星から弓の背の外へ彗星の尾（板の絵）が大きく流れ、
    芯は月光、縁は藍で、先へ行くほど点々にほどけて星屑になる。尾は弓腕をはさんで前後に一枚ずつ。弓の懐には藍の三日月が弦の方を向いて抱かれ、
    握りの左右（奥行き）には藍の小さな三日月の鰭。弦には銀の玉が並び、上から小さな星の飾りが下がる"""
    m = M('moon_starfall_bow')
    bow_grip(m)
    arm = [(12, 21), (13.25, 27), (13, 33), (11.25, 38.5), (8.25, 43), (5, 45.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in arm]
        m.chain(f'arm_{n}', I, pts, [3.75, 3.5, 3.25, 2.75, 2.25], 1.45, 3, 7, tip=False)
        m.chain(f'streak_{n}', L, [P(q) for q in lib.offset_line(arm, 1.1)[1:]], [0.5, 0.6, 0.75, 0.9], 1.5, 2, 4, tip=False)
        comet_tail(m, f'tail_{n}', sign < 0)
        star(m, f'star_{n}', L, P((5, 47)), 5.5, 1.6, 3)
        star(m, f'star_core_{n}', I, P((5, 47)), 2.25, 1.8, 8)
        m.seg(f'nock_{n}', N, P((6, 45.25)), P((3, 45.25)), 2, 1.05, 2, 4)
    # 懐の三日月（上の弓腕の腹から、弦の方を向く）
    m.arc('crescent', N, (9.75, 30.5), 3.25, 110, 250, 5, [1.6, 1.9, 2.1, 1.9, 1.6], 0.9, 3, 5)
    m.seg('crescent_hold', I, (8.4, 33.5), (12, 34), 1, 0.7, 6, 7)
    m.arc('crescent_low', N, (9.75, -0.5), 3.25, 110, 250, 5, [1.6, 1.9, 2.1, 1.9, 1.6], 0.9, 3, 5)
    m.seg('crescent_low_hold', I, (8.4, -3.5), (12, -4), 1, 0.7, 6, 7)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        z0, z1 = (1.75, 5.25) if zs > 0 else (-5.25, -1.75)
        m.box(f'fin_{zn}', N, [11, 21.5, z0], [13, 23, z1], 3)
        z2 = (5.25, 6.5) if zs > 0 else (-6.5, -5.25)
        m.box(f'fin_tip_{zn}', N, [11.25, 23, z2[0]], [12.75, 24.75, z2[1]], 4)
        m.box(f'fin_d_{zn}', N, [11, 7, z0], [13, 8.5, z1], 3)
        m.box(f'fin_d_tip_{zn}', N, [11.25, 5.25, z2[0]], [12.75, 7, z2[1]], 4)
    top, bot = (3.5, 45.5), (3.5, -15.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((41, 37.5, -7.5, -11)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_bead{k}', I, [q[0] - 0.75, q[1] - 0.75, -0.75], [q[0] + 0.75, q[1] + 0.75, 0.75], 8)
        q = lib.string_at(top, bot, pull, lib.string_t(top, bot, 34))
        m.box(f'string_{key}_cord', N, [q[0] - 0.2, q[1] - 3, -0.2], [q[0] + 0.2, q[1], 0.2], 3)
        m.seg(f'string_{key}_charm_v_glow', L, (q[0], q[1] - 5.5), (q[0], q[1] - 2.75), 1.1, 0.5, 4, 5, over=0)
        m.seg(f'string_{key}_charm_h_glow', L, (q[0] - 1.4, q[1] - 4.1), (q[0] + 1.4, q[1] - 4.1), 1.1, 0.45, 4, 5, over=0)
    return m


ALL = [moon_light_sword, moon_waning_blade, moon_crescent_dagger, moon_umbra_dagger, moon_scale_hammer, moon_full_hammer,
       moon_tide_staff, moon_new_staff, moon_starlight_bell_staff, moon_halo_bell_staff, moon_waning_bow, moon_starfall_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
