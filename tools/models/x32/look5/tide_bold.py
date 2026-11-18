# -*- coding: utf-8 -*-
"""潮のシリーズ（look5・個性版）。鯨と同じく、シリーズの生き物・題材のモチーフで一品ずつ形を立てる。
色: 珊瑚（深い赤紫 → 珊瑚 → 桃 → 泡の白）、差し色は貝（クリーム）、光は真珠（桃白）。

  法螺貝の鎚・脳珊瑚の礁の鎚・帆立の杖・海月の真珠杖・巻貝の鈴杖・海馬の波鈴杖・エイの棘の短剣・鰻の顎の牙・蟹の鋏の爪
  珊瑚の枝の弓・マンタの翼の弓
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lib import Model  # noqa: E402

C, S, P = 'coral', 'shell', 'pearl'


def M(name):
    return Model(name, accent=S, glowmat=P)


def grip(m, y0, y1, pommel_y, bands=(), wrap_to=None, butt='pearl'):
    """柄: 珊瑚の柄（根元が暗い）、貝の輪、貝の柄頭、真珠の雫か貝の棘"""
    m.box('shaft', C, [-1.5, y0, -1.5], [1.5, y1, 1.5], grad=(y0, 1.5, y1, 5), wrap=(y0, wrap_to) if wrap_to else None)
    for k, by in enumerate(bands):
        m.box(f'band_{k}', S, [-2, by, -2], [2, by + 2, 2], 3)
    m.box('pommel', S, [-2, pommel_y - 3, -2], [2, pommel_y, 2], 3)
    m.box('pommel_flare', C, [-2.5, pommel_y, -2.5], [2.5, pommel_y + 2, 2.5], 3)
    if butt == 'pearl':
        m.box('pommel_pearl', P, [-1.25, pommel_y - 5.5, -1.25], [1.25, pommel_y - 3, 1.25], gem=True)
    else:
        m.seg('butt_tip', S, (0, pommel_y - 3), (0, pommel_y - 8), 2, 1, 3, 5, tip=True)


def whelk(m, prefix, cx, top, s=1.0):
    """巻貝の鈴: とがった殻頂（上）、ふくらんだ胴、珊瑚の口、真珠の舌"""
    m.seg(f'{prefix}_spire', S, (cx, top - 3 * s), (cx, top + 1.5 * s), 3 * s, 3 * s, 2.5, 5, tip=True)
    m.box(f'{prefix}_body', S, [cx - 2.75 * s, top - 8 * s, -2.75 * s], [cx + 2.75 * s, top - 3 * s, 2.75 * s],
          grad=(top - 8 * s, 2, top - 3 * s, 4), rim=s > 1.2)
    m.box(f'{prefix}_lip', C, [cx - 3.5 * s, top - 10 * s, -3.25 * s], [cx + 3.5 * s, top - 8 * s, 3.25 * s], 5)
    m.box(f'{prefix}_tongue', P, [cx - 1, top - 10 * s - 2.5, -1], [cx + 1, top - 10 * s, 1], 3)


# ---------------------------------------------------------------- 鎚

def tide_hammer():
    """法螺貝の鎚: 横に寝かせた法螺貝。+x の殻口が打つ面（中が真珠に光る）、-x へ巻きの殻頂、上に貝の瘤"""
    m = M('tide_hammer')
    grip(m, -12, 22, -12, bands=(-1, 15), wrap_to=12)
    m.box('collar', S, [-2.5, 20, -2.5], [2.5, 24, 2.5], 3)
    m.box('whorl', C, [-4, 24, -4], [5, 35, 4], 4, rim=True)                 # 体層（いちばん太い巻き）
    m.chain('spire', C, [(-4, 29.5), (-8, 30.5), (-11.5, 31), (-14, 31.25)], [8, 5.5, 3], [3.5, 2.5, 1.5], 4, 7.5)
    m.box('spire_band_0', S, [-7, 27, -3.75], [-5.5, 33.5, 3.75], 4)          # 巻きの継ぎ目
    m.box('spire_band_1', S, [-10.5, 28.5, -2.75], [-9, 33, 2.75], 4)
    m.box('lip', S, [5, 23, -4.5], [8, 36, 4.5], grad=(23, 3, 36, 5))        # 殻口の縁（打つ面）
    m.box('aperture', P, [6, 25, -3], [8.5, 34, 3], grad=(25, 2, 34, 4))     # 殻口の奥の真珠層
    for k, x in enumerate((-2.5, 0.5, 3.5)):
        m.seg(f'knob_{k}', S, (x, 34.5), (x - 0.5, 38.5 - k * 0.5), 2.5, 2, 3, 5, tip=True)
    return m


def tide_reef_hammer():
    """脳珊瑚の礁の鎚: 丸い岩礁の頭に、+x は脳珊瑚の打つ面、-x と上へ枝珊瑚が伸び、正面に真珠の帯"""
    m = M('tide_reef_hammer')
    grip(m, -12, 22, -12, bands=(-1, 15), wrap_to=12)
    m.box('collar', S, [-2.5, 20, -2.5], [2.5, 24, 2.5], 3)
    m.box('reef', C, [-6, 24, -4.5], [7, 36, 4.5], grad=(24, 3, 36, 5), rim=True)
    m.box('brain_face', C, [7, 25, -4], [10, 35, 4], grad=(25, 5, 35, 7))     # 脳珊瑚（溝は筆で描く）
    m.box('pearl_band', S, [-6.5, 28.5, -5], [7.5, 31.5, 5], 4)             # 元の「真珠の帯」
    m.box('pearl_f', P, [-1, 28, -5.75], [3, 32, -4.75], gem=True)
    m.box('pearl_b', P, [-1, 28, 4.75], [3, 32, 5.75], gem=True)
    m.chain('branch_l', C, [(-5.5, 33), (-9.5, 37), (-11, 42), (-10.5, 46)], [3.5, 3, 2], [3, 2.5, 2], 5, 8.5)
    m.seg('twig_l', C, (-10, 38), (-14, 40.5), 2.5, 2, 6, 8.5, tip=True)
    m.chain('branch_t', C, [(2, 35.5), (3, 40), (5, 43)], [3, 2.5], [2.5, 2], 5, 8.5)
    m.box('tip_pearl_l', P, [-11.75, 45.5, -1.25], [-9.25, 48, 1.25], 4)
    m.box('tip_pearl_t', P, [4.25, 42.5, -1.25], [6.75, 45, 1.25], 4)
    return m


# ---------------------------------------------------------------- 杖

def tide_staff():
    """帆立の杖: 柄の先で扇の貝が上へ開く。放射の肋が先へ明るく、蝶番に耳、中に真珠"""
    m = M('tide_staff')
    grip(m, -14, 30, -14, bands=(10,), wrap_to=0, butt='spike')
    m.box('hinge', S, [-4.5, 30, -2], [4.5, 33, 2], 4)                       # 蝶番の耳
    cx, cy = 0, 32
    for k, a in enumerate((22, 45, 68, 90, 112, 135, 158)):                # 扇の板（重ねて面にする。肋の筋は筆で描く）
        r1 = 12.5 if 45 <= a <= 135 else 11
        ra = math.radians(a)
        p0 = (cx + 1.5 * math.cos(ra), cy + 1.5 * math.sin(ra))
        p1 = (cx + r1 * math.cos(ra), cy + r1 * math.sin(ra))
        m.seg(f'rib_{k}', C, p0, p1, 5, 2, 3, 8.5, tip=True)
    m.box('pearl', P, [-2.25, 33, -2.25], [2.25, 37.5, 2.25], gem=True)
    return m


def tide_pearl_staff():
    """海月の真珠杖: 柄の先に海月の傘（回した節の弧）、中に光る真珠、縁のひだ、下へ揺れる触手"""
    m = M('tide_pearl_staff')
    grip(m, -14, 38, -14, bands=(10, 24), wrap_to=0)
    m.arc('dome', C, (0, 39), 7.5, 180, 0, 7, 3, 7, 4, 8.5)                  # 傘
    m.box('core', P, [-3, 39, -2.5], [3, 45, 2.5], gem=True)
    m.box('frill', S, [-9, 37.5, -3.5], [9, 39.5, 3.5], 4)                   # 傘の縁のひだ
    tent = {
        'a': [(-7.5, 37.5), (-8.5, 33), (-7, 28.5), (-8.5, 23.5), (-7.5, 19)],
        'b': [(-3.5, 37.5), (-4.5, 33.5), (-3.25, 30)],
        'c': [(3.5, 37.5), (4.5, 33.5), (3.25, 30)],
        'd': [(7.5, 37.5), (8.5, 32.5), (7, 27.5), (8.5, 22.5)],
    }
    for n, pts in tent.items():
        m.chain(f'tent_{n}', C, pts, 2, 1.5, 6, 3, tip=False)
        x, y = pts[-1]
        m.box(f'tent_{n}_bead', P, [x - 1, y - 2, -1], [x + 1, y, 1], 3)
    return m


# ---------------------------------------------------------------- 鈴杖

def tide_bell_staff():
    """巻貝の鈴杖: 柄の先が珊瑚の枝に分かれ、左右の枝から巻貝の鈴が下がる"""
    m = M('tide_bell_staff')
    grip(m, -14, 36, -14, bands=(8, 24), wrap_to=0)
    m.chain('branch_l', C, [(0, 35), (-4, 39), (-8.5, 42), (-10.5, 46)], [3, 2.5, 2], 2, 5, 8.5)
    m.chain('branch_r', C, [(0, 35), (4, 40), (7.5, 44), (8.5, 48.5)], [3, 2.5, 2], 2, 5, 8.5)
    m.chain('branch_c', C, [(0, 35), (0.5, 41), (-0.5, 46.5)], [3, 2.5], 2, 5, 8.5)
    m.box('cord_l', S, [-7.5, 35, -1], [-5.5, 40.5, 1], 3)
    whelk(m, 'bell_l', -6.5, 35, 0.9)
    m.box('cord_r', S, [4.5, 36, -1], [6.5, 42, 1], 3)
    whelk(m, 'bell_r', 5.5, 36, 0.9)
    return m


def tide_surf_bell_staff():
    """海馬の波鈴杖: 柄の先がタツノオトシゴの首として巻き、頭は +x へ俯き、吻の先から巻貝の鈴が下がる"""
    m = M('tide_surf_bell_staff')
    grip(m, -16, 32, -16, bands=(8, 20), wrap_to=2)
    m.arc('neck', C, (6, 32), 6, 180, 25, 7, [3.5, 3.5, 3.5, 3, 3, 3, 3], 2.5, 3.5, 6.5)
    m.box('head', C, [9.5, 33, -2.25], [14, 38.5, 2.25], grad=(33, 5, 38.5, 7), rim=False)
    m.seg('snout', C, (13, 35), (17.5, 31.5), 3, 1.75, 6.5, 8.5, tip=True)
    for k, a in enumerate((150, 115, 80)):                                   # 冠（背のとげ）
        ra = math.radians(a)
        p0 = (6 + 7 * math.cos(ra), 32 + 7 * math.sin(ra))
        p1 = (6 + 10.5 * math.cos(ra), 32 + 10.5 * math.sin(ra))
        m.seg(f'crest_{k}', S, p0, p1, 2.5, 1.5, 3, 5, tip=True)
    m.seg('crown', S, (11.5, 38), (11, 42), 2.5, 2, 3, 5, tip=True)
    m.box('eye_f', P, [11, 35.5, -2.75], [13, 37.5, -2.25], 4)
    m.box('eye_b', P, [11, 35.5, 2.25], [13, 37.5, 2.75], 4)
    m.box('cord', S, [16, 27.5, -1], [18, 31, 1], 3)
    whelk(m, 'bell', 17, 28, 1.3)
    return m


# ---------------------------------------------------------------- 短剣・牙・爪

def tide_dagger():
    """エイの棘: まっすぐ細く尖る珊瑚の棘に、手元へ向く返しの鋸歯。鍔はエイの小さな翼"""
    m = M('tide_dagger')
    grip(m, -6, 5, -6, bands=(-1,), wrap_to=5)
    m.box('guard', C, [-2.5, 5, -2], [2.5, 7.5, 2], 4)
    m.seg('guard_wing_l', C, (-2, 6.5), (-7, 5), 3.5, 1.25, 4, 7.5, tip=True)
    m.seg('guard_wing_r', C, (2, 6.5), (7, 5), 3.5, 1.25, 4, 7.5, tip=True)
    m.chain('barb', C, [(0, 7), (0, 17), (0, 27), (0, 33), (0, 38)], [4, 3.5, 2.5, 2], [1.75, 1.5, 1.25, 1], 3.5, 8.5)
    m.box('spine', P, [-1, 9, -2], [1, 25, 2], grad=(9, 2, 25, 4))
    for k, y in enumerate((12, 17, 22, 27)):
        hw = 2 if y < 17 else (1.75 if y < 27 else 1.25)
        for s, n in ((-1, 'l'), (1, 'r')):
            m.seg(f'tooth_{n}{k}', S, (s * (hw - 0.5), y + 1), (s * (hw + 2.25), y - 1.5), 2, 1, 3, 5, tip=True)
    return m


def waterway_fang():
    """鰻の顎の牙: 柄頭の上が鰻の頭（開いた顎、真珠の目、背の冠のとげ）で、口から淡い牙が反って伸びる"""
    m = M('waterway_fang')
    grip(m, -10, 4, -10, bands=(-4,), wrap_to=4)
    m.box('head', C, [-3, 4, -2.5], [3, 9, 2.5], grad=(4, 3, 9, 5), rim=True)
    m.seg('jaw', C, (2, 5), (6, 3.5), 3, 2.5, 5, 7, tip=True)                 # 下顎（前へ開く）
    m.seg('snout', C, (2, 8), (6.5, 9.5), 3, 2.5, 5, 7, tip=True)            # 上顎
    m.box('eye_f', P, [-0.5, 6, -3], [1.5, 8, -2.5], 4)
    m.box('eye_b', P, [-0.5, 6, 2.5], [1.5, 8, 3], 4)
    for k, (a, b) in enumerate((((-2.5, 8.5), (-5.5, 11.5)), ((-3, 6.5), (-7, 7.5)), ((-3, 4.5), (-6.5, 3.5)))):
        m.seg(f'crest_{k}', S, a, b, 2, 1.5, 3, 5, tip=True)                 # 元の「とげの冠」
    m.chain('fang', S, [(0.5, 8.5), (2, 15), (2.5, 21), (1.5, 26), (-0.5, 30)], [4, 3.5, 3, 2], [2, 1.75, 1.5, 1], 2.5, 5)
    m.chain('fang_groove', P, [(1.5, 11), (2.4, 17), (2.4, 22)], 2, 2.25, 2, 4, tip=False)
    return m


def tidecaller_claw():
    """蟹の鋏: 握りの上にふくらんだ鋏の掌、+x に反った大きな不動指、-x から小さな可動指。内側に貝の歯、掌に真珠"""
    m = M('tidecaller_claw')
    grip(m, -8, 3, -8, bands=(-4,), wrap_to=3)
    m.box('cuff', S, [-2.5, 2.5, -2.5], [2.5, 5, 2.5], 3)
    m.box('palm', C, [-3.5, 5, -3], [5.5, 12, 3], grad=(5, 3, 12, 5), rim=True)
    m.chain('finger_fix', C, [(4, 11), (6.5, 16), (6.5, 21), (4.5, 24.5), (2, 26)], [4.5, 4, 3, 2], [2.5, 2.25, 2, 1.5], 5, 8.5)
    m.chain('finger_mov', C, [(-2, 11), (-3, 16), (-2, 20.5), (0.5, 23)], [3.5, 3, 2], [2.25, 2, 1.5], 5, 8.5)
    for k, (a, b) in enumerate((((4.5, 15.5), (2.5, 16)), ((4.75, 19.5), (2.75, 19.75)))):
        m.seg(f'tooth_fix_{k}', S, a, b, 2, 1.5, 3, 5, tip=True)
    m.seg('tooth_mov_0', S, (-1.5, 16), (0.5, 16.5), 2, 1.5, 3, 5, tip=True)
    for k, x in enumerate((-2, 1, 4)):
        m.box(f'knob_{k}', S, [x - 0.75, 12, -1.5], [x + 0.75, 13.5, 1.5], 4)  # 掌の背のこぶ
    m.box('pearl', P, [-0.5, 6.5, -3.75], [2.5, 9.5, -2.75], gem=True)
    m.box('pearl_b', P, [-0.5, 6.5, 2.75], [2.5, 9.5, 3.75], gem=True)
    return m


# ---------------------------------------------------------------- 弓

def bow_grip(m):
    m.box('grip', C, [9, 9, -3], [15, 21, 3], grad=(9, 2, 21, 4), wrap=(9, 21))
    m.box('grip_ring_t', S, [8.5, 21, -3.5], [15.5, 23, 3.5], 4)
    m.box('grip_ring_b', S, [8.5, 7, -3.5], [15.5, 9, 3.5], 3)
    m.box('socket_f', S, [10, 12, -4], [14, 18, -3], 4)
    m.box('pearl_f', P, [10.5, 12.5, -4.75], [13.5, 17.5, -3.75], gem=True)
    m.box('socket_b', S, [10, 12, 3], [14, 18, 4], 4)
    m.box('pearl_b', P, [10.5, 12.5, 3.75], [13.5, 17.5, 4.75], gem=True)


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


def tide_bow():
    """珊瑚の枝の弓: 珊瑚の弓腕から、外へ小枝が二本ずつ伸びる（先が泡の白）"""
    m = M('tide_bow')
    body = [(12, 21), (13.5, 26), (14, 31), (13, 35)]
    tips = [(13, 35), (10, 39.5), (5, 42.5), (0, 44), (-3, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', C, [mirror(p, sign) for p in body], 3, 2, 2.5, 5, tip=False)
        m.chain(f'tip_{n}', C, [mirror(p, sign) for p in tips], [3, 2.5, 2, 2], 1.5, 5, 8.5, tip=True)
        m.chain(f'twig_{n}0', C, [mirror(p, sign) for p in ((13.5, 25), (17, 27.5), (18, 31))], [2.5, 2], 1.5, 4, 8.5)
        m.chain(f'twig_{n}1', C, [mirror(p, sign) for p in ((13.5, 32), (17, 34.5), (17.5, 38))], [2.5, 2], 1.5, 4, 8.5)
    bow_grip(m)
    m.seg('nock_u', S, (-1, 44.3), (-3.5, 44.6), 2, 1.25, 4, 5)
    m.seg('nock_d', S, (-3.5, -14.6), (-1, -14.3), 2, 1.25, 4, 5)
    strings(m, (-3, 44.5), (-3, -14.5))
    return m


def tide_coral_bow():
    """マンタの翼の弓: 幅広く平たい翼の弓腕が先へ細り、握りの前に頭びれが二本、先に真珠"""
    m = M('tide_coral_bow')
    body = [(12, 21), (14.5, 28), (15.5, 35), (14.5, 41), (12, 45)]
    tips = [(12, 45), (8, 48), (3, 49.5), (-1, 50)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'wing_{n}', C, [mirror(p, sign) for p in body], [6, 5.5, 4.5, 3.5], [1.5, 1.5, 1.25, 1.25], 2.5, 5.5, tip=False)
        m.chain(f'tip_{n}', C, [mirror(p, sign) for p in tips], [3, 2.5, 2], 1.25, 5.5, 8.5, tip=True)
        m.chain(f'edge_{n}', S, [mirror(p, sign) for p in ((15.5, 24), (18, 30), (18.5, 36), (16.5, 42))], [2, 2, 2], 1, 3, 5, tip=True)
        m.seg(f'horn_{n}', C, mirror((9, 19.5), sign), mirror((5.5, 22.5), sign), 2.5, 1.5, 4, 7, tip=True)
    bow_grip(m)
    m.box('tip_pearl_u', P, [-3, 50, -1.5], [0, 53, 1.5], gem=True)
    m.box('tip_pearl_d', P, [-3, -23, -1.5], [0, -20, 1.5], gem=True)
    strings(m, (-1, 50), (-1, -20))
    return m


ALL = [tide_hammer, tide_reef_hammer, tide_staff, tide_pearl_staff, tide_bell_staff, tide_surf_bell_staff,
       tide_dagger, waterway_fang, tidecaller_claw, tide_bow, tide_coral_bow]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
