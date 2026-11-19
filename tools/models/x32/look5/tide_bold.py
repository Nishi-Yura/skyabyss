# -*- coding: utf-8 -*-
"""潮のシリーズ（look5・個性版）。シリーズの生き物・題材のモチーフで一品ずつ形を立てる。
色: 珊瑚（深い赤紫 → 珊瑚 → 桃 → 泡の白）、差し色は貝（クリーム）、光は真珠（桃白）。

  法螺貝の鎚・脳珊瑚の礁の鎚・帆立の杖・海月の真珠杖・巻貝の鈴杖・海馬の波鈴杖・エイの棘の短剣・鰻の顎の牙・蟹の鋏の爪
  珊瑚の枝の弓・マンタの翼の弓

形は鯨から借りない（ユーザー: 「形自体はシリーズごとに毎回作り直してね」）。
- 柄: 握りはまっすぐな珊瑚の茎に巻き、その上は節ごとに少し折れる珊瑚の枝（節にポリプの芽）。下はタケノコ貝の尖り。輪や柄頭の玉は付けない。
- 弓: 細い握りに昆布を斜めに巻く。珊瑚の弓は先が外へ跳ね返る反り弓、マンタの弓は幅広の翼で下の先が尾になる。
- 光（真珠）は品ごとに場所を変える: 殻口の奥・ポリプの先・帆立の目・海月の芯・海馬の目・エイの毒の先・鰻の喉・鋏が挟む真珠・
  小枝の先のポリプ・翼の後ろ縁。巻貝の鈴杖は光なし。
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


# ---------------------------------------------------------------- 鎚

def tide_hammer():
    """法螺貝の鎚: 横に寝かせた法螺貝。+x の殻口が打つ面で、奥の真珠層だけが光る。-x へ巻きの殻頂、上に瘤、下に水管"""
    m = M('tide_hammer')
    stalk(m, -12, 8, 24.5, knuckles=[(0.75, 16)])
    m.box('whorl', C, [-4, 24, -3.5], [5, 35, 3.5], 4, rim=True)              # 体層（いちばん太い巻き）
    m.chain('spire', C, [(-4, 29.5), (-8, 30.5), (-11.5, 31), (-14, 31.25)], [8, 5.5, 3], [3.5, 2.5, 1.5], 4, 7.5)
    m.box('spire_band_0', S, [-7, 27, -3.75], [-5.5, 33.5, 3.75], 4)          # 巻きの継ぎ目
    m.box('spire_band_1', S, [-10.5, 28.5, -2.75], [-9, 33, 2.75], 4)
    m.box('lip', S, [5, 22.5, -4.5], [7, 36.5, 4.5], grad=(22.5, 3, 36.5, 5))  # 殻口の縁（打つ面）
    m.box('aperture', P, [6, 25, -3], [8, 34, 3], grad=(25, 2, 34, 4))       # 殻口の奥の真珠層
    for k, (c, L) in enumerate((((-3.4, 36.5), 4), ((-0.4, 37), 5), ((2.5, 36.3), 3.2))):
        rbox(m, f'knob_{k}', S, c, L, 2, 1.25, 120, 3, 5, tip=True)
    rbox(m, 'canal', S, (4.5, 22.25), 6.8, 2.5, 1.5, -70, 3, 5, tip=True)     # 水管
    return m


def tide_reef_hammer():
    """脳珊瑚の礁の鎚: 平たい岩礁の頭、+x は脳珊瑚の打つ面（溝は筆）、-x と上へ枝珊瑚。枝先のポリプが光る"""
    m = M('tide_reef_hammer')
    stalk(m, -12, 8, 25.5, knuckles=[(-0.75, 16)])
    m.box('reef', C, [-7, 25, -3.5], [7, 34, 3.5], grad=(25, 3, 34, 5), rim=True)
    m.box('brain_face', C, [7, 25.5, -3.25], [11, 33.5, 3.25], grad=(25.5, 5, 33.5, 7))
    rbox(m, 'branch_l_0', C, (-7.5, 35), 6.66, 2.5, 2, 135, 5, 6)
    rbox(m, 'branch_l_1', C, (-10.25, 39.5), 6.22, 2, 1.5, 106.7, 6, 7)
    rbox(m, 'branch_l_2', C, (-10.75, 44), 5.04, 2, 1.25, 82.87, 7, 8)
    rbox(m, 'twig_l', C, (-12, 39.25), 5.72, 2, 1.25, 147.99, 6, 8.5, tip=True)
    rbox(m, 'branch_t_0', C, (2.5, 35.75), 5.6, 2.5, 1.5, 77.47, 5, 6.5)
    rbox(m, 'branch_t_1', C, (4, 39.5), 4.6, 2, 1.25, 56.31, 6.5, 8)
    m.box('polyp_l', P, [-11.75, 45.5, -1.25], [-9.25, 48, 1.25], 4)
    m.box('polyp_t', P, [4.25, 40.5, -1.25], [6.75, 43, 1.25], 4)
    return m


# ---------------------------------------------------------------- 杖

def tide_staff():
    """帆立の杖: 珊瑚の茎の先で扇の貝が上へ開く。外套膜の縁に並ぶ帆立の目が光る"""
    m = M('tide_staff')
    stalk(m, -14, 2, 30.5, knuckles=[(0.75, 12), (-0.75, 21)])
    m.box('hinge', S, [-4.5, 30, -2], [4.5, 33, 2], 4)                       # 蝶番の耳
    cx, cy = 0, 32
    for k, a in enumerate((22, 45, 68, 90, 112, 135, 158)):                # 扇の板（重ねて面にする。肋の溝は筆）
        r1 = 12.5 if 45 <= a <= 135 else 11
        ra = math.radians(a)
        m.seg(f'rib_{k}', C, (cx + 1.5 * math.cos(ra), cy + 1.5 * math.sin(ra)), (cx + r1 * math.cos(ra), cy + r1 * math.sin(ra)),
              5, 2, 3, 8.5, tip=True)
    for k, a in enumerate((45, 68, 90, 112, 135)):                          # 帆立の目（縁の内側に並ぶ小さな点）
        x, y = cx + 10 * math.cos(math.radians(a)), cy + 10 * math.sin(math.radians(a))
        m.box(f'eye_{k}', P, [x - 1, y - 1, -2.4], [x + 1, y + 1, 2.4], 4)
    return m


def tide_pearl_staff():
    """海月の真珠杖: 珊瑚の茎の先に海月の傘、中に光る芯（真珠）、縁のひだ、下へ揺れる触手"""
    m = M('tide_pearl_staff')
    stalk(m, -14, 2, 38.5, knuckles=[(-0.75, 14), (0.75, 26)])
    m.arc('dome', C, (0, 39), 7.5, 180, 0, 7, 3, 5, 4, 8.5)                  # 傘
    m.box('core', P, [-5.5, 39.5, -4.25], [5.5, 45.5, 4.25], gem=True)
    m.box('frill', S, [-9, 37.5, -3.5], [9, 39.5, 3.5], 4)                   # 傘の縁のひだ
    tent = {
        'a': [(-7.5, 37.5), (-8.5, 33), (-7, 28.5), (-8.5, 23.5), (-7.5, 18)],
        'b': [(-3.5, 37.5), (-4.5, 33.5), (-3.25, 28.5)],
        'c': [(3.5, 37.5), (4.5, 33.5), (3.25, 28.5)],
        'd': [(7.5, 37.5), (8.5, 32.5), (7, 27.5), (8.5, 21.5)],
    }
    for n, pts in tent.items():
        m.chain(f'tent_{n}', C, pts, 2, 1, 6, 3, tip=True)
    return m


# ---------------------------------------------------------------- 鈴杖

def tide_bell_staff():
    """巻貝の鈴杖: 茎の先が珊瑚の枝に分かれ、左右の枝先から巻貝の鈴が下がる。光は入れない"""
    m = M('tide_bell_staff')
    stalk(m, -14, 2, 35.5, knuckles=[(0.75, 13), (-0.75, 24)])
    m.chain('branch_l', C, [(0, 35), (-4, 39), (-8.5, 42), (-10.5, 46)], [2.5, 2.5, 2], [1.25, 1.25, 1], 5, 8.5)
    m.chain('branch_r', C, [(0, 35), (4, 40), (7.5, 44), (8.5, 48.5)], [2.5, 2.5, 2], [1.25, 1.25, 1], 5, 8.5)
    m.chain('branch_c', C, [(0, 35), (0.5, 41), (-0.5, 46.5)], [2.5, 2], [1.25, 1], 5, 8.5)
    m.box('cord_l', S, [-10.5, 39.5, -1], [-8.5, 43.5, 1], 3)
    whelk(m, 'bell_l', -9.5, 39, 0.9)
    m.box('cord_r', S, [7.5, 40.5, -1], [9.5, 45, 1], 3)
    whelk(m, 'bell_r', 8.5, 40, 0.9)
    return m


def tide_surf_bell_staff():
    """海馬の波鈴杖: 茎の先がタツノオトシゴの首として巻き、頭は +x へ俯き、吻の先から巻貝の鈴。光るのは目だけ"""
    m = M('tide_surf_bell_staff')
    stalk(m, -16, 2, 32.5, knuckles=[(0.75, 12), (-0.75, 22)])
    m.arc('neck', C, (6, 32), 6, 180, 25, 7, [3.5, 3.5, 3.5, 3, 3, 3, 3], 1.75, 3.5, 6.5)
    m.box('head', C, [9.5, 33, -2.25], [14, 38.5, 2.25], grad=(33, 5, 38.5, 7))
    rbox(m, 'snout', C, (15.25, 33.25), 6.7, 3, 1.25, -37.87, 6.5, 8.5, tip=True)
    for k, (c, a) in enumerate((((-1.58, 36.38), 150), ((2.3, 39.93), 115), ((7.52, 40.62), 80))):
        rbox(m, f'crest_{k}', S, c, 3.5, 2, 1, a, 3, 5, tip=True)            # 冠（背のとげ）
    rbox(m, 'crown', S, (11.25, 40), 4.04, 2, 1, 97.13, 3, 5, tip=True)
    m.box('eye_f', P, [11, 35.5, -2.75], [13, 37.5, -2.25], 4)
    m.box('eye_b', P, [11, 35.5, 2.25], [13, 37.5, 2.75], 4)
    m.box('cord', S, [16, 28, -1], [18, 31.5, 1], 3)
    rbox(m, 'bell_spire', S, (17, 26), 5, 2.8, 1.4, 90, 2.5, 5, tip=True)
    m.box('bell_body', S, [14.5, 19.5, -2.75], [19.5, 24, 2.75], grad=(19.5, 2, 24, 4))
    m.box('bell_lip', C, [13.5, 17.5, -3.25], [20.5, 19.5, 3.25], 5)
    return m


# ---------------------------------------------------------------- 短剣・牙・爪

def tide_dagger():
    """エイの棘: まっすぐ細く尖る珊瑚の棘に、手元へ向く返しの鋸歯。毒のある先だけが光る。鍔はエイの小さな翼"""
    m = M('tide_dagger')
    stalk(m, -6, 5, 5)
    m.box('guard', C, [-2.5, 5, -2], [2.5, 7.5, 2], 4)
    m.seg('guard_wing_l', C, (-2, 6.5), (-7, 5), 3.5, 1.25, 4, 7.5, tip=True)
    m.seg('guard_wing_r', C, (2, 6.5), (7, 5), 3.5, 1.25, 4, 7.5, tip=True)
    rbox(m, 'barb_0', C, (0, 12), 11, 4, 1.25, 90, 3.5, 5)
    rbox(m, 'barb_1', C, (0, 22), 11, 3.5, 1.1, 90, 5, 6.5)
    rbox(m, 'barb_2', C, (0, 30), 7, 2.5, 1.25, 90, 6.5, 8)
    m.seg('venom_tip', P, (0, 32.5), (0, 38), 2, 1, 2, 5, tip=True)           # 毒の先
    for k, y in enumerate((11.75, 16.75, 21.75)):
        cx = 2.88 if k == 0 else 2.62
        rbox(m, f'tooth_l{k}', S, (-cx, y), 3.51, 2, 0.6, -137.73, 3, 5, tip=True)
        rbox(m, f'tooth_r{k}', S, (cx, y), 3.51, 2, 0.6, -42.27, 3, 5, tip=True)
    return m


def waterway_fang():
    """鰻の顎の牙: 握りの上が鰻の頭（開いた顎、背の冠のとげ）。開いた喉の奥が光り、頭から淡い牙が反って伸びる"""
    m = M('waterway_fang')
    stalk(m, -10, 4, 4)
    m.box('head', C, [-3, 4, -2.5], [3, 9, 2.5], grad=(4, 3, 9, 5), rim=True)
    m.box('throat', P, [2, 5.25, -1.25], [5, 7.75, 1.25], 4)                 # 開いた喉の奥
    rbox(m, 'jaw', C, (4, 4.25), 5.28, 3, 1.75, -20.56, 5, 7, tip=True)        # 下顎
    rbox(m, 'snout', C, (4.25, 8.75), 5.74, 3, 1.75, 18.43, 5, 7, tip=True)   # 上顎
    m.box('eye_f', S, [-0.5, 6, -3], [1.5, 8, -2.5], 5)
    m.box('eye_b', S, [-0.5, 6, 2.5], [1.5, 8, 3], 5)
    for k, (c, a, L) in enumerate((((-4, 10), 135, 5.24), ((-5, 7), 165.96, 5.12), ((-4.75, 4), -164.05, 4.64))):
        rbox(m, f'crest_{k}', S, c, L, 2, 1, a, 3, 5, tip=True)
    for k, (c, a, L, w, d) in enumerate((((1.25, 11.75), 77.01, 7.68, 4, 1.5), ((2.25, 18), 85.24, 7.02, 3.5, 1.25),
                                         ((2, 23.5), 101.31, 6.1, 3, 1), ((0.5, 28), 116.57, 5.48, 2, 1))):
        rbox(m, f'fang_{k}', S, c, L, w, d, a, 2.5 + k * 0.6, 3.1 + k * 0.6, tip=k == 3)
    return m


def tidecaller_claw():
    """蟹の鋏: 握りの上に鋏の掌、+x に反った不動指、-x に可動指。二本の指の間に真珠を挟む"""
    m = M('tidecaller_claw')
    stalk(m, -8, 3, 3)
    m.box('cuff', S, [-2.5, 2.5, -2.5], [2.5, 5, 2.5], 3)
    m.box('palm', C, [-3.5, 5, -2.5], [5.5, 12, 2.5], grad=(5, 3, 12, 5), rim=True)
    for k, (c, a, L, w, d) in enumerate((((5.25, 13.5), 63.43, 6.6, 4.5, 2), ((6.5, 18.5), 90, 6, 4, 1.75),
                                         ((5.5, 22.75), 119.74, 5.04, 3, 1.5), ((3.25, 25.25), 149.04, 3.92, 2, 1))):
        rbox(m, f'finger_fix_{k}', C, c, L, w, d, a, 5 + k, 6 + k, tip=k == 3)
    for k, (c, a, L, w, d) in enumerate((((-2.5, 13.5), 101.31, 6.1, 3.5, 1.75), ((-2.5, 18.25), 77.47, 5.6, 3, 1.5),
                                         ((-0.75, 21.75), 45, 4.54, 2, 1))):
        rbox(m, f'finger_mov_{k}', C, c, L, w, d, a, 5 + k, 6 + k, tip=k == 2)
    rbox(m, 'tooth_fix_0', S, (3.5, 15.75), 3.06, 2, 1, 165.96, 3, 5, tip=True)
    rbox(m, 'tooth_mov_0', S, (-0.5, 16.25), 3.06, 2, 1, 14.04, 3, 5, tip=True)
    m.box('knob_0', S, [-3, 12, -1.5], [-1, 13.5, 1.5], 4)                   # 掌の背のこぶ
    m.box('knob_1', S, [0, 12, -1.5], [2, 13.5, 1.5], 4)
    m.box('held_pearl', P, [0.25, 17.25, -1.5], [3.75, 20.75, 1.5], gem=True)  # 挟んだ真珠
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
    """珊瑚の枝の反り弓: 細い握りから珊瑚の弓腕がまっすぐ伸び、先で外へ跳ね返る。背の小枝の先のポリプが光る"""
    m = M('tide_bow')
    kelp_grip(m, 10.5, 13.5, 2)
    arm = [(12, 20.5), (11, 27), (9, 33), (6.5, 38), (4, 42)]
    curl = [(4, 42), (3.25, 45.5), (4.75, 48.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', C, [mirror(p, sign) for p in arm], [3, 3, 2.5, 2.5], 1.5, 3, 6, tip=False)
        m.chain(f'curl_{n}', C, [mirror(p, sign) for p in curl], [2.5, 2], 1.25, 6, 8.5, tip=True)
        m.chain(f'twig_{n}0', C, [mirror(p, sign) for p in ((10.25, 29), (13.5, 31.5), (14.5, 35))], [2.5, 2], 1.25, 4, 7)
        m.chain(f'twig_{n}1', C, [mirror(p, sign) for p in ((7.5, 36), (10.5, 39), (11, 42.5))], [2, 2], 1, 5, 7.5)
        for k, (x, y) in enumerate(((14.5, 35.5), (11, 43))):
            yy = mirror((x, y), sign)[1]
            m.box(f'polyp_{n}{k}', P, [x - 1, yy - 1, -1.1], [x + 1, yy + 1, 1.1], 4)
        m.seg(f'nock_{n}', S, mirror((4.5, 44.75), sign), mirror((1.5, 44.75), sign), 2, 1, 4, 5)
    strings(m, (2, 44.75), (2, -14.75))
    return m


def tide_coral_bow():
    """マンタの翼の弓（伝説）: 幅広く平たいマンタの翼が弓腕になって外へ大きく張り出す。翼の面には貝の白の斑紋が並び、
    翼の縁は左右（奥行き）へ反り返る。翼の後ろ縁が真珠の光で光り、そのあとを光る泡が離れて追う。
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
        for k, (x, y, s) in enumerate(((22, 30, 1.4), (23.5, 35, 1.1), (21.75, 39.75, 1.2), (25, 32.5, 0.8), (19, 43.5, 0.9))):
            diamond(m, f'bubble_{n}{k}_glow', P, P_((x, y)), s, s * 0.45, 4, 5)
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
    """円錐貝の三叉: 珊瑚の茎の先で、外の二本が上へ反る珊瑚の歯。三本の先に細い円錐貝をかぶせる。光は入れない"""
    m = M('tide_shell_trident')
    stalk(m, -16, 0, 32.5, knuckles=[(0.75, 12), (-0.75, 22)])
    m.box('collar', S, [-2.5, 31, -2], [2.5, 34, 2], 4)
    m.box('collar_lip', C, [-3, 30, -2.5], [3, 31, 2.5], 3)                       # 襟の下の珊瑚の縁
    m.box('barnacle_f', S, [-1.25, 31.5, -2.75], [0.75, 33.25, -2], 6)            # 襟に付いたフジツボ
    m.box('barnacle_b', S, [0, 32, 2], [1.75, 33.5, 2.6], 6)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'prong_{n}', C, [(sd * 1.5, 33), (sd * 5, 34.5), (sd * 6.5, 38.5), (sd * 6.5, 43)], [3, 2.5, 2.5], 1.25, 4, 6.5)
        m.seg(f'spur_{n}', C, (sd * 5.75, 37), (sd * 3.5, 40.5), 1.75, 0.9, 5, 7, tip=True)   # 内へ向く小さな歯
        m.box(f'cone_{n}_ring', C, [sd * 6.5 - 2, 41.25, -1.5], [sd * 6.5 + 2, 42.75, 1.5], 5)  # 円錐貝の口
        for h, hn in ((-1, 'a'), (1, 'b')):                                   # 円錐貝: 左右の半分を傾けて先で合わせる
            m.seg(f'cone_{n}{hn}', S, (sd * 6.5 + h * 1.75, 42), (sd * 6.5, 50), 2, 1.25, 3, 5, tip=True)
    m.seg('prong_m', C, (0, 33.5), (0, 45), 3, 1.5, 4, 6.5)
    m.box('cone_m_ring', C, [-2.5, 43.25, -1.75], [2.5, 44.75, 1.75], 5)
    for h, hn in ((-1, 'a'), (1, 'b')):
        m.seg(f'cone_m{hn}', S, (h * 2, 44), (0, 54), 2.5, 1.4, 3, 5, tip=True)
    m.chain('weed', C, [(-2.25, 31), (-3.75, 27), (-3, 23), (-4.25, 19.5)], [1.5, 1.25, 1], 0.5, 2, 4, tip=True)  # 襟から垂れる海藻
    return m


def coral_harpoon():
    """珊瑚の銛: 長い珊瑚の茎の先に、返しの付いた貝の穂先。茎に巻いた綱が垂れ、綱の先の真珠の浮きが光る"""
    m = M('coral_harpoon')
    stalk(m, -16, 0, 38.5, knuckles=[(0.75, 14), (-0.75, 26)], nubs=False)
    m.box('socket', C, [-2, 37.5, -1.75], [2, 41.5, 1.75], grad=(37.5, 4, 41.5, 6))
    m.box('socket_band_d', S, [-2.4, 37, -2.15], [2.4, 38.25, 2.15], 4)                     # 受け口を締める貝の輪
    m.box('socket_band_u', S, [-2.4, 40.5, -2.15], [2.4, 41.75, 2.15], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):                                                     # 穂先: 左右の半分を傾けて先へ細らせる
        m.seg(f'point_{n}', S, (sd * 1.5, 41), (0, 55), 3, 1.1, 3, 5)
    m.seg('point_rib', S, (0, 42), (0, 52.5), 1, 1.25, 4, 6)                                 # 穂先の背の筋（貝の色。Blockbench で珊瑚→貝に塗り直した）
    h = 3.5 / (2 * math.sqrt(2))
    m.seg('point_tip', S, (-h, 55 - h), (h, 55 + h), 3.5, 1, 4, 5, over=0)                 # 先の菱形
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'barb_{n}', S, (sd * 1.5, 45.5), (sd * 4.75, 40.5), 2, 0.75, 3, 5, tip=True)   # 手元へ向く返し
        m.seg(f'barb2_{n}', S, (sd * 1, 50), (sd * 3.25, 46.75), 1.5, 0.7, 3, 5, tip=True)    # 先寄りの小さな返し
    for k, y in enumerate((14, 26)):                                                         # 節を縛る貝の紐
        x = 0.75 if k == 0 else -0.75
        m.box(f'lash_{k}', S, [x - 1.9, y - 0.75, -1.9], [x + 1.9, y + 0.75, 1.9], 4)
    m.box('line_coil', S, [-2, 29, -2], [2, 32, 2], 4)                                       # 茎に巻いた綱
    m.box('line_coil_b', S, [-2, 33, -2], [2, 34.5, 2], 3)                                   # 上の一巻き
    m.chain('line', S, [(1.75, 30), (4.5, 26.5), (5.5, 22.5), (5.5, 21.5)], [2, 2, 1.5], 0.5, 3, 4)
    f = 4.5 / (2 * math.sqrt(2))
    m.seg('float', P, (5.5 - f, 18.25 - f), (5.5 + f, 18.25 + f), 6, 1, 3, 5, over=0)         # 真珠の浮き（菱形。Blockbench で薄くし、上の金具に寄せた）
    m.box('float_cap_u', S, [4.5, 21, -1], [6.5, 22.5, 1], 4)                                # 浮きを留める貝の金具
    return m


def coral_shield():
    """海亀の甲羅の盾: 前（-Z）に盛り上がる甲羅の板（甲板の縁は貝の色）、縁から珊瑚の小枝が生える。光は入れない"""
    m = M('coral_shield')
    # 楕円の甲羅: 幅の違う板を重ねて輪郭を丸く。いちばん外の板は貝の色で、縁甲板の白い縁に見せる
    m.box('marginal', S, [-11.5, -3, -2], [11.5, 27, 0], grad=(-3, 2, 27, 4))
    m.box('shell_w', C, [-9.5, -6, -2.5], [9.5, 30, -0.5], grad=(-6, 2, 30, 4))
    m.box('shell_m', C, [-7.5, -9, -2.5], [7.5, 33, -0.5], grad=(-9, 2, 33, 4))
    m.box('shell_n', C, [-4, -10.5, -2.5], [4, 34.5, -0.5], grad=(-10.5, 1, 34.5, 3))
    for k, y in enumerate((1, 11.5, 22)):                                                    # 背の甲板（菱形の列）
        h = 7 / (2 * math.sqrt(2))
        m.seg(f'scute_m{k}', C, (-h, y - h), (h, y + h), 7, 0.9, 4, 6, over=0, zc=-3.25)
    for sd, n in ((-1, 'l'), (1, 'r')):
        for k, y in enumerate((6, 17)):                                                      # 肋の甲板
            h = 5 / (2 * math.sqrt(2))
            m.seg(f'scute_{n}{k}', C, (sd * 6.5 - h, y - h), (sd * 6.5 + h, y + h), 5, 0.75, 3, 5, over=0, zc=-3)
        m.chain(f'twig_{n}', C, [(sd * 8.5, 30), (sd * 11, 34), (sd * 10.5, 38)], [2.5, 2], 1.25, 5, 8, tip=True)
        for k, y in enumerate((2, 12, 22)):                                                  # 縁甲板の歯（貝の菱形）
            t = 3 / (2 * math.sqrt(2))
            m.seg(f'rim_{n}{k}', S, (sd * 11.75 - t, y - t), (sd * 11.75 + t, y + t), 3, 1, 4, 6, over=0, zc=-1)
        m.chain(f'flipper_{n}', C, [(sd * 9.5, 26), (sd * 13.5, 28.5), (sd * 16, 27)], [3.5, 2.5], 0.75, 3, 5, tip=True, zc=-1.25)
        m.seg(f'hind_{n}', C, (sd * 8.5, -4.5), (sd * 12.5, -8.5), 3, 0.75, 3, 5, tip=True, zc=-1.25)
    m.box('head', S, [-2.5, 34, -2.25], [2.5, 38.5, -0.25], grad=(34, 3, 38.5, 5))           # 甲羅から出た亀の頭
    m.box('snout', S, [-1.75, 38.5, -2], [1.75, 40, -0.5], 5)
    m.seg('tail', S, (0, -10), (0, -13.5), 2.5, 0.75, 3, 5, tip=True, zc=-1.25)
    m.box('handle', S, [-1.5, -3, 0], [1.5, 6, 2.5], grad=(-3, 2, 6, 4))                    # 裏の握り
    m.box('brace_0', S, [-9, 8, 0], [9, 10, 1.25], 3)                                        # 裏の当て木（横二本と縦一本）
    m.box('brace_1', S, [-9, 18, 0], [9, 20, 1.25], 3)
    m.box('brace_v', S, [-1, 6, 0], [1, 22, 1], 2)
    return m


ALL = [tide_hammer, tide_reef_hammer, tide_staff, tide_pearl_staff, tide_bell_staff, tide_surf_bell_staff,
       tide_dagger, waterway_fang, tidecaller_claw, tide_bow, tide_coral_bow, tide_shell_trident, coral_harpoon, coral_shield]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
