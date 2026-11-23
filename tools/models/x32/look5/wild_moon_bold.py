# -*- coding: utf-8 -*-
"""野・月（look5・個性版）。月の見える岬と星見の丘で拾う 6 品（叙）。
色: 夜の鉄（gloam 紺がかった灰の 1 系統）、差し色は月の真珠色（pearl）、光は星の淡い黄（star）を品ごとに一か所。
灯台守と星読みの道具。三日月の切り欠きと細い輪を形の芯にする。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'gloam': ['#0e1020', '#16192e', '#20243e', '#2c3150', '#3a4064', '#4a5178', '#5c648e', '#7078a4', '#8890ba', '#a4acd0'],
    'pearl': ['#5e5a62', '#86828c', '#aaa6b2', '#ccc8d4', '#e6e2ec', '#faf8fe'],
    'star': ['#7a5a10', '#b08a1c', '#dcb834', '#f6dc64', '#fff0a8', '#fffce6'],
})
N, P, S = 'gloam', 'pearl', 'star'


def M(name):
    return Model(name, accent=P, glowmat=S)


def cord_grip(m, y0, y1, r=1.5):
    """紺の紐を固く巻いた握り。上下は真珠色の細い輪"""
    m.box('grip', N, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('pearl_d', P, [-r - 0.3, y0 - 0.75, -r - 0.3], [r + 0.3, y0, r + 0.3], 3)
    m.box('pearl_u', P, [-r - 0.3, y1, -r - 0.3], [r + 0.3, y1 + 0.75, r + 0.3], 4)


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


def star4(m, name, mat, c, r, d, t=5, zc=0.0):
    """四方へ尖る星（上下左右の尖りと芯）"""
    x, y = c
    m.box(f'{name}_core', mat, [x - r * 0.3, y - r * 0.3, zc - d], [x + r * 0.3, y + r * 0.3, zc + d], t)
    for k, (dx, dy) in enumerate(((0, 1), (0, -1), (1, 0), (-1, 0))):
        m.seg(f'{name}_ray_{k}', mat, (x + dx * r * 0.2, y + dy * r * 0.2), (x + dx * r, y + dy * r), r * 0.3, d * 0.8, t, t + 1, tip=True, zc=zc)
    for k, (dx, dy) in enumerate(((0.7, 0.7), (-0.7, 0.7), (0.7, -0.7), (-0.7, -0.7))):
        m.seg(f'{name}_ray_d{k}', mat, (x + dx * r * 0.2, y + dy * r * 0.2), (x + dx * r * 0.55, y + dy * r * 0.55), r * 0.2, d * 0.6, t, t + 1, tip=True, zc=zc)


def wild_lighthouse_longbow():
    """灯台の大弓（ヒロイック）: 上と下で姿の違う長弓。上の弓腕は細く高い灯台の塔で、真珠色の帯を巻き、先に手すりの付いた
    灯室が載る。灯室の窓が星色に光り、そこから光の筋が二本、背の外へ長く伸びる。下の弓腕は岬の岩で、先に真珠色の波頭が
    砕ける。握りの左右（奥行き）には灯の鎧戸が開き、弦には真珠の玉と、上から小さな星の飾りが下がる"""
    m = M('wild_lighthouse_longbow')
    m.box('grip', N, [10.25, 9, -1.9], [13.75, 21, 1.9], grad=(9, 3, 21, 5), wrap=(9, 21))
    for k, y in enumerate((8.25, 20.75)):
        m.box(f'grip_band_{k}', P, [9.9, y, -2.25], [14.1, y + 1, 2.25], 4)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        z0, z1 = (1.9, 5) if zs > 0 else (-5, -1.9)
        m.box(f'shutter_{zn}', N, [11.25, 11.5, z0], [12.75, 18.5, z1], grad=(11.5, 4, 18.5, 6))
        zg = (z0 + 0.6, z1 - 0.6)
        m.box(f'shutter_slit_{zn}_glow', S, [11.1, 13.5, zg[0]], [12.9, 16.5, zg[1]], 4)
    # 上: 灯台の塔
    tower = [(12, 20.5), (11.25, 30), (9.75, 39), (8, 46)]
    m.chain('tower', N, tower, [3.25, 2.75, 2.5], 1.4, 4, 7, tip=False)
    for k, y in enumerate((26, 33, 40)):
        x = 11.6 - (y - 21) * 0.17
        m.box(f'band_{k}', P, [x - 1.9, y - 0.5, -1.6], [x + 1.9, y + 0.5, 1.6], 4)
    m.box('gallery', N, [4.5, 45.5, -3.25], [12.5, 46.75, 3.25], 6)
    for k, x in enumerate((4.75, 12.25)):
        m.box(f'rail_{k}', P, [x - 0.25, 46.75, -3.1], [x + 0.25, 48.5, 3.1], 4)
    m.box('rail_top', P, [4.5, 48.25, -3.1], [12.5, 48.75, 3.1], 5)
    m.box('lamp_room', N, [5.75, 46.75, -2.25], [11.25, 52.5, 2.25], 6)
    m.box('lamp_glow', S, [5.5, 47.75, -2], [11.5, 51.5, 2], grad=(47.75, 3, 51.5, 5))
    m.box('lamp_roof', N, [5.25, 52.5, -2.75], [11.75, 53.5, 2.75], 7)
    m.box('lamp_cap', N, [6.75, 53.5, -1.6], [10.25, 54.5, 1.6], 8)
    m.seg('lamp_spire', P, (8.5, 54.5), (8.5, 58.5), 1.1, 0.6, 4, 5, tip=True)
    for k, (e, w) in enumerate((((26, 58), 2.25), ((27, 49.5), 1.75), ((24.5, 42), 1.25))):
        m.seg(f'beam_{k}_glow', S, (11.25, 49.75), e, w, 0.4, 3, 5, tip=False, over=0)
    m.seg('nock_u', P, (5.25, 47), (4.5, 51.25), 1.25, 0.8, 4, 5)
    # 下: 岬の岩と波頭
    cliff = [(12, 9.5), (11, 1.5), (9.5, -6.5), (7, -13.5), (4.5, -19)]
    m.chain('cliff', N, cliff, [4.5, 4.25, 3.75, 3], 1.8, 2, 4, tip=False)
    for k, (x, y, w) in enumerate(((12.75, 2.5, 3.5), (11, -6, 3.25), (8.75, -12.5, 2.75))):
        m.box(f'ledge_{k}', N, [x, y - 0.9, -1.3], [x + w, y + 0.9, 1.3], 3 + k)
    for k, (a, b, w) in enumerate((((4.5, -19), (7.5, -21.5), 2), ((4.25, -19.5), (1, -22.25), 1.75), ((5.5, -21), (6, -24.5), 1.5), ((3, -21.25), (2.25, -24.75), 1.25))):
        m.seg(f'wave_{k}', P, a, b, w, 1, 4 + k * 0.3, 5, tip=True)
    for k, (x, y, sz) in enumerate(((9.5, -23.5, 1.2), (0, -24, 1), (7.25, -26.5, 0.8), (-0.75, -21, 0.8), (10.5, -20.5, 0.9))):
        m.box(f'spray_{k}', P, [x - sz / 2, y - sz / 2, -sz / 2], [x + sz / 2, y + sz / 2, sz / 2], 5)
    for k, (a, b, w) in enumerate((((7.5, -21.5), (11, -20.25), 1.75), ((1, -22.25), (-1.75, -20.5), 1.5))):
        m.seg(f'crest_{k}', P, a, b, w, 0.9, 5, 5, tip=True)
    top, bot = (4.5, 51), (4.5, -19.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((44, 40, -8, -12)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_pearl{k}', P, [q[0] - 0.75, q[1] - 0.75, -0.75], [q[0] + 0.75, q[1] + 0.75, 0.75], 5)
        q = lib.string_at(top, bot, pull, lib.string_t(top, bot, 36))
        m.box(f'string_{key}_cord', N, [q[0] - 0.2, q[1] - 2.5, -0.2], [q[0] + 0.2, q[1], 0.2], 4)
        m.seg(f'string_{key}_star_v_glow', S, (q[0], q[1] - 4.75), (q[0], q[1] - 2.25), 1, 0.45, 4, 5, over=0)
        m.seg(f'string_{key}_star_h_glow', S, (q[0] - 1.25, q[1] - 3.5), (q[0] + 1.25, q[1] - 3.5), 1, 0.4, 4, 5, over=0)
    return m


def wild_meteor_greatsword():
    """（ヒロイック、2026-11-21 に作り直し・案 B）落ちる星: 刃の先の上に、尾を引いて落ちてくる隕石の塊が浮く（芯が星色）。鍔は流れ星の尾のように大きく後ろへ流れる
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ流れ星の尾を、下の縁が薄く先へ細る一枚の帯のメッシュに替え、刃先を四角錐にし、浮く隕石の三つの塊を一緒に斜めに傾け、火花を菱形に、刃に隕鉄の斑を描いた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_meteor_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_meteor_greatsword')
    cord_grip(m, -8, 8, 1.6)
    m.box('pommel', N, [-2.2, -11, -2.2], [2.2, -8.75, 2.2], 6)
    m.box('guard', P, [-3.6, 8.6, -2], [3.6, 11, 2], grad=(8.6, 2, 11, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'trail_{n}', P, [(sd * 3, 10), (sd * 6.5, 8.5), (sd * 9.5, 5.5), (sd * 11, 2)], [2.2, 1.6, 1.1], 1.2, 3, 5, tip=True)
    m.box('blade', N, [-3.4, 11, -1.3], [3.4, 40, 1.3], grad=(11, 4, 40, 7))
    m.box('fuller', N, [-0.6, 15, -1.4], [0.6, 36, 1.4], grad=(15, 2, 36, 3))
    m.seg('tip', N, (0, 39.5), (0, 45), 6.8, 1.2, 7, 8, tip=True, over=0)
    m.box('float_meteor', N, [-2.6, 48, -2.4], [2.6, 53, 2.4], grad=(48, 4, 53, 7))
    m.box('float_meteor_b', N, [-2, 47.2, -1.8], [2, 53.8, 1.8], 5)
    m.box('float_meteor_core_glow', S, [-1.2, 49.4, -2.6], [1.2, 51.6, 2.6], 4)
    for k, (x, y, s) in enumerate(((3.5, 54.5, 1.6), (5.5, 57, 1.2), (7, 59.5, 0.9))):
        m.box(f'spark_{k}_glow', S, [x - s / 2, y - s / 2, -s / 2], [x + s / 2, y + s / 2, s / 2], 4)
    return m


def wild_mirror_dagger():
    """（ヒロイック、2026-11-21 に作り直し・案 B）三日月の刃: 刃そのものが大きく反った三日月。内の縁は真珠色の刃、背は紺の鉄。柄頭の星と、刃の根元に小さな満月
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ三日月の刃と縁を、内の刃が薄く背が厚い一枚の三日月のメッシュに替え、内の縁を真珠色、背を紺に塗った）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_mirror_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_mirror_dagger')
    cord_grip(m, -4, 4, 1.3)
    m.box('pommel_glow', S, [-1, -6.4, -1], [1, -4.75, 1], 4)
    m.box('guard', P, [-2.6, 4, -1.4], [2.6, 5.4, 1.4], 4)
    m.arc('blade', N, (-4, 12), 7.5, -60, 70, 7, [2.6, 2.6, 2.4, 2.2, 1.8, 1.4, 1], 0.8, 6, 8, tip=True)
    m.arc('edge', P, (-4, 12), 6.1, -55, 66, 6, 0.7, 0.85, 4, 5, tip=True)
    m.box('fullmoon', P, [-1.2, 5.2, -1.2], [1.2, 7.4, 1.2], 5)
    return m


def wild_starbell_totem():
    """（ヒロイック、2026-11-21 に作り直し・案 B）月と星の天秤: 柱の頂に横木、一方の端に三日月、もう一方に星が下がり、その下にそれぞれ鈴。真ん中の頂の珠が星色
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ三日月を両端が細る一枚の三日月のメッシュに、二つの鈴を六角の鈴に替え、真珠色に塗った）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_starbell_totem.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_starbell_totem')
    cord_grip(m, -14, 2, 1.6)
    m.box('pillar', N, [-1.75, 2.75, -1.75], [1.75, 32, 1.75], grad=(2.75, 3, 32, 5))
    m.box('ring_0', P, [-2.1, 14, -2.1], [2.1, 15, 2.1], 4)
    m.box('beam', N, [-9, 31, -1], [9, 32.6, 1], 6)
    m.box('top_glow', S, [-1.2, 32.6, -1.2], [1.2, 35, 1.2], 4)
    m.box('cord_l', P, [-7.8, 27.5, -0.2], [-7.2, 31, 0.2], 3)
    m.arc('moon', P, (-7.5, 25), 3, 100, 340, 6, 1.2, 0.9, 4, 5)
    m.box('cord_r', P, [7.2, 28, -0.2], [7.8, 31, 0.2], 3)
    star4(m, 'star', P, (7.5, 26), 2.8, 0.8, 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        x = sd * 7.5
        m.box(f'bell_cord_{n}', P, [x - 0.2, 19, -0.2], [x + 0.2, 23.8, 0.2], 3)
        m.box(f'bell_{n}', P, [x - 1.5, 16.6, -1.5], [x + 1.5, 19, 1.5], grad=(16.6, 2, 19, 4))
    return m


def wild_starchart_staff():
    """（ヒロイック、2026-11-21 に作り直し・案 C）三日月の揺りかご: 杖の先に上が開いた大きな三日月の輪、その中に星色の四方の星が浮く。三日月の角に真珠の玉
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ三日月と内の縁を、両端が細る一枚の三日月のメッシュに替え（内の縁は塗りにした）、角の真珠の玉を菱形に傾けた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_starchart_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_starchart_staff')
    cord_grip(m, -14, 2, 1.3)
    m.box('shaft', N, [-1.1, 2.75, -1.1], [1.1, 30, 1.1], grad=(2.75, 3, 30, 5))
    m.box('collar', P, [-1.6, 28.5, -1.6], [1.6, 30, 1.6], 4)
    m.arc('moon', N, (0, 36), 6.5, 200, 340, 10, [2.2, 2.6, 3, 3.2, 3.2, 3.2, 3.2, 3, 2.6, 2.2], 1.1, 6, 8)
    m.arc('moon_in', P, (0, 36), 5.1, 210, 330, 8, 0.7, 1.15, 4, 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        a = math.radians(200 if sd < 0 else 340)
        m.box(f'bead_{n}', P, [6.5 * math.cos(a) - 0.9, 36 + 6.5 * math.sin(a) - 0.9, -0.9], [6.5 * math.cos(a) + 0.9, 36 + 6.5 * math.sin(a) + 0.9, 0.9], 5)
    star4(m, 'float_star', S, (0, 36.5), 2.8, 0.7, 3)
    return m


# ---------------------------------------------------------------- 鎚


def wild_starforge_hammer():
    """（ヒロイック、2026-11-21 に作り直し・案 A）大金床: 頭が大きな金床。打つ面は広く平ら、反対は長く尖る角。上面の窪みに落ちた星が光り、火花が三つ跳ねる
    ※ 2026-11-21 に Blockbench で手を入れた（角を根元が太く先へ細って少し下がる金床の角のメッシュに替え、火花をそれぞれ違う向きに傾け、頭に真珠の線と星の点を描いた）。メッシュの UV はスクリプトで書き出した .bbmodel に入れた。正本は tools/models/x32/wild_starforge_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_starforge_hammer')
    cord_grip(m, -6, 6)
    m.box('shaft', N, [-1.25, 6.75, -1.25], [1.25, 21, 1.25], grad=(6.75, 3, 21, 5))
    m.box('waist', N, [-2.4, 19.5, -2.2], [2.4, 21.5, 2.2], 5)
    m.box('head', N, [-3.5, 21.5, -3], [5.5, 27, 3], grad=(21.5, 4, 27, 6))
    m.box('face', P, [5.5, 20.8, -3.3], [7, 27.7, 3.3], 4)
    m.seg('horn', N, (-3.5, 25.5), (-11, 24), 3.8, 2.8, 5, 7, tip=True)
    m.box('top_plate', N, [-3, 27, -2.7], [5, 27.9, 2.7], 7)
    m.box('cradle_glow', S, [0, 27.4, -1.2], [2.4, 28.2, 1.2], 4)
    for k, (x, y) in enumerate(((-0.5, 30.5), (2.5, 31.8), (4.2, 30))):
        m.box(f'spark_{k}_glow', S, [x - 0.4, y - 0.4, -0.4], [x + 0.4, y + 0.4, 0.4], 4)
    return m


ALL = [wild_lighthouse_longbow, wild_meteor_greatsword, wild_mirror_dagger, wild_starbell_totem,
       wild_starchart_staff, wild_starforge_hammer]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
