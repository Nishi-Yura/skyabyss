# -*- coding: utf-8 -*-
"""野・高地（look5・個性版）。雪と風の高地で拾う 14 品（稀〜英）。
色: 霜の降りた岩と高地の鉄（rime 青みの灰の 1 系統）、差し色は山の獣の毛と角（horn 暖かい灰茶）、
光は高地の雷（zap 薄紫の白）を品ごとに一か所。風と雷の高地なので、形は尖りと鉤、旗や羽のなびきを多めに。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'rime': ['#12161e', '#1c222c', '#28303c', '#36404e', '#465262', '#5a6678', '#727e90', '#909aaa', '#b4bcc8', '#dce2ea'],
    'horn': ['#3a2e26', '#5e4c3c', '#86705a', '#ac967c', '#ccbaa0', '#ece0cc'],
    'zap': ['#4a2a8a', '#6a44c0', '#9070e8', '#b8a0ff', '#e0d4ff', '#faf6ff'],
})
R, H, Z = 'rime', 'horn', 'zap'


def M(name):
    return Model(name, accent=H, glowmat=Z)


def fur_grip(m, y0, y1, r=1.5):
    """山羊の毛皮を巻いた握り。下に角の石突き"""
    m.box('grip', H, [-r, y0, -r], [r, y1, r], grad=(y0, 2, y1, 4), wrap=(y0, y1))
    m.box('cap', R, [-r - 0.4, y0 - 1.25, -r - 0.4], [r + 0.4, y0, r + 0.4], 6)
    m.box('collar', R, [-r - 0.4, y1, -r - 0.4], [r + 0.4, y1 + 1.25, r + 0.4], 7)


def diamond(m, name, mat, c, s, d, t0, t1, zc=0.0):
    h = s / (2 * math.sqrt(2))
    m.seg(name, mat, (c[0] - h, c[1] - h), (c[0] + h, c[1] + h), s, d, t0, t1, over=0, zc=zc)


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


def shaft(m, y0, y1, r=1.2, rings=()):
    m.box('shaft', R, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 6))
    for k, y in enumerate(rings):
        m.box(f'ring_{k}', H, [-r - 0.45, y, -r - 0.45], [r + 0.45, y + 1, r + 0.45], 4)


def hh(m, top, r=1.25):
    """高地の長柄: 毛皮巻きの握り、霜の鉄の柄、角の石突き"""
    fur_grip(m, -12, 2, 1.6)
    m.box('shaft', R, [-r, 2, -r], [r, top, r], grad=(2, 3, top, 6))
    m.seg('butt', H, (0, -13.2), (0, -17), 2.6, 2.0, 3, 5, tip=True)


def hilt(m, top=7, r=1.5):
    fur_grip(m, -7, top, r)
    m.box('pommel', H, [-2.0, -10.2, -1.6], [2.0, -8.2, 1.6], grad=(-10.2, 2, -8.2, 4))


def bolt(m, name, pts, w=1.0, d=1.0):
    m.chain(name, Z, pts, [w] * (len(pts) - 1), d, 3, 5, tip=True)


# ---------------------------------------------------------------- 杖

def wild_cloudreader_staff():
    """（レア、2026-11-21 に作り直し・案 B）雲の輪郭の輪: 鉄の帯で雲の形（上が三つ山）の枠を作り、枠の中に小さな雷の玉が一つ吊られる
    ※ 2026-11-21 に Blockbench で手を入れた（雲の枠の継ぎ目を詰めてなめらかにし、吊った雷の玉を風に揺れるように傾け、台に霜を描いた）。正本は tools/models/x32/wild_cloudreader_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_cloudreader_staff')
    hh(m, 38)
    m.box('base_bar', R, [-6, 37.5, -0.8], [6, 39, 0.8], 5)
    m.arc('lobe_l', R, (-3.6, 41.5), 2.6, 90, 270, 6, 1.0, 1.6, 5, 7)
    m.arc('lobe_m', R, (0, 43.6), 3.2, 0, 180, 7, 1.0, 1.6, 6, 8)
    m.arc('lobe_r', R, (3.6, 41.5), 2.6, -90, 90, 6, 1.0, 1.6, 5, 7)
    m.box('side_l', R, [-6.7, 37.6, -0.8], [-5.5, 41.6, 0.8], 5)
    m.box('side_r', R, [5.5, 37.6, -0.8], [6.7, 41.6, 0.8], 5)
    m.box('hang', H, [-0.25, 42.0, -0.25], [0.25, 46.8, 0.25], 3)
    m.box('core_glow', Z, [-1.1, 40.4, -1.1], [1.1, 42.4, 1.1], 4)
    return m


def wild_fluff_staff():
    """（エピック、2026-11-21 に作り直し・案 A）綿の雷雲: 杖の先にふくらんだ雷雲（大小の塊を五つ）。雲の下に雷が二本垂れ、雲の芯が光る
    ※ 2026-11-21 に Blockbench で手を入れた（雲の塊をそれぞれ違う向きに回してふくらみを崩し、雲の底に雷の照り返し、上に霜を描いた）。正本は tools/models/x32/wild_fluff_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_fluff_staff')
    hh(m, 41)
    for k, (x, y, s) in enumerate(((0, 43, 3.6), (-3.6, 41.6, 2.6), (3.6, 41.8, 2.8), (-1.6, 46, 2.4), (2.0, 45.6, 2.2))):
        m.box(f'puff_{k}', R, [x - s, y - s * 0.8, -s * 0.8], [x + s, y + s * 0.8, s * 0.8], grad=(y - s, 6, y + s, 9))
    bolt(m, 'bolt_l_glow', [(-3.0, 39.2), (-4.2, 36.4), (-2.8, 34.6), (-3.8, 32)])
    bolt(m, 'bolt_r_glow', [(3.4, 39.2), (4.4, 36.8), (3.0, 35.2), (4.2, 33)])
    m.box('core_glow', Z, [-1.6, 42, -3.0], [1.6, 44.4, 3.0], 4)
    return m


def wild_warden_crown_staff():
    """（ヒロイック、2026-11-21 に作り直し・案 B）雷鷲の頭と翼: 杖の先に鷲の頭（前へ嘴）、左右へ大きく翼を広げる。額の冠羽と目が雷
    ※ 2026-11-21 に Blockbench で手を入れた（翼を根元が厚く先へ羽ばたく帯のメッシュに替えて下の縁を広げ、灰色の風切羽の棒は外し、羽の筋と焼け焦げを描いた）。正本は tools/models/x32/wild_warden_crown_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_warden_crown_staff')
    hh(m, 38)
    m.box('neck', H, [-2.0, 37, -2.0], [2.0, 42, 2.0], grad=(37, 3, 42, 5))
    m.box('head', H, [-2.6, 42, -2.4], [2.6, 46.4, 2.4], grad=(42, 3, 46.4, 5))
    m.seg('beak', R, (2.4, 44.4), (5.6, 43.0), 2.0, 1.6, 7, 9, tip=True)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', H, [(sd * 1.8, 40), (sd * 6, 43.5), (sd * 11, 47), (sd * 14, 45)], [4.0, 3.6, 2.4], 1.0, 2, 5, tip=True)
        for j in range(4):
            x = sd * (5 + j * 2.4)
            m.seg(f'quill_{n}{j}', R, (x, 42 + j * 0.9), (x + sd * 0.8, 37 + j * 0.6), 1.4, 0.7, 6, 8, tip=True)
        m.box(f'eye_{n}_glow', Z, [0.6, 44.6, sd * 2.4 - 0.3], [1.8, 45.6, sd * 2.4 + 0.3], 4)
    m.chain('crest_glow', Z, [(-1, 46.2), (-2.4, 49), (-1.0, 51)], [1.2, 0.8], 1.0, 3, 5, tip=True)
    return m


# ---------------------------------------------------------------- 斧

def wild_conductor_axe():
    """（エピック、2026-11-21 に作り直し・案 A）避雷針の斧: 片刃の斧のてっぺんから避雷針が伸び、先の三叉に雷。銅（角）の線が刃までらせんに巻く。刃の縁に雷の筋
    ※ 2026-11-21 に Blockbench で手を入れた（輪郭だけだった刃を背が厚く刃先の薄い一枚のメッシュにし、銅線の輪を斜めに巻き、刃の明暗と雷の焦げを描いた）。正本は tools/models/x32/wild_conductor_axe.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_conductor_axe')
    hh(m, 32)
    m.chain('blade', R, [(1, 30), (5, 27.5), (8.6, 29), (9.4, 33), (8.6, 37), (5, 38.4), (1, 36)], [4, 4.6, 5, 5, 4.6, 4], 1.0, 5, 8, tip=False)
    m.box('socket', H, [-1.8, 29, -1.8], [1.8, 37, 1.8], 4)
    m.box('web', R, [1, 29, -0.4], [8.6, 37, 0.4], 6)
    m.box('rod', R, [-0.4, 37, -0.4], [0.4, 46, 0.4], 7)
    for k, x in enumerate((-1.6, 0, 1.6)):
        m.seg(f'prong_{k}', R, (x * 0.4, 45.6), (x, 48.4), 0.6, 0.6, 7, 9, tip=True)
    m.box('prong_glow', Z, [-1.0, 47.4, -0.6], [1.0, 49.2, 0.6], 4)
    for k in range(4):
        m.box(f'wire_{k}', H, [-0.9, 38 + k * 2, -0.9], [0.9, 38.6 + k * 2, 0.9], 5)
    m.chain('edge_glow', Z, [(9.6, 29.4), (10.2, 33), (9.6, 36.6)], [0.6, 0.6], 1.2, 3, 4, tip=False)
    return m


def wild_snowsplitter_axe():
    """（レア、2026-11-21 に作り直し・案 A）三日月と氷柱: 幅広の三日月の刃。刃の背から氷柱の鉤が三つ下がる。柄頭に霜の結晶が光る
    ※ 2026-11-21 に Blockbench で手を入れた（刃を一枚の三日月のメッシュにし、氷柱を尖った四角錐にし、霜と刃先の白を描いた）。正本は tools/models/x32/wild_snowsplitter_axe.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_snowsplitter_axe')
    hh(m, 32)
    m.chain('blade', R, [(1, 28), (6, 25.5), (9.6, 28), (10.6, 33), (9.6, 38), (6, 40.4), (1, 38)], [4, 5, 6, 6, 5, 4], 1.0, 5, 8, tip=False)
    m.box('socket', H, [-1.8, 27, -1.8], [1.8, 39, 1.8], 4)
    m.box('web', R, [1, 27.6, -0.4], [9.4, 38.4, 0.4], 6)
    for k, y in enumerate((29, 33, 37)):
        m.seg(f'icicle_{k}', R, (-1.8, y), (-4.6, y - 2.4), 1.4, 1.2, 8, 9, tip=True)
    m.box('crystal_glow', Z, [-1.2, 39, -1.2], [1.2, 41.6, 1.2], 4)
    return m


# ---------------------------------------------------------------- 弓

def wild_eaglet_longbow():
    """雛鷲の長弓（エピック）: 背丈ほどある長い弓。弓腕は角の色の薄板を重ね、上下の先が雛鷲の頭（嘴は前へ、頭に冠羽）。
    頭のうしろで畳んだ小さな翼が背へ張り出し、首には左右（奥行き）へ羽の房。上の鷲の目が雷の光。
    弦には鷲の爪の飾りが一つ下がる"""
    m = M('wild_eaglet_longbow')
    m.box('grip', H, [10.25, 9.5, -1.75], [13.75, 20.5, 1.75], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    arm = [(12, 20), (11.25, 30), (9.5, 40), (7, 48), (4.5, 53)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'arm_{n}', R, [P(q) for q in arm], [3.25, 3, 2.5, 2.25], 1.3, 4, 7, tip=False)
        m.chain(f'lam_{n}', H, [P(q) for q in ((13, 21), (12.25, 30), (10.5, 39), (8, 47))], [1.1, 1.1, 1], 1.4, 3, 5, tip=False)
        hy = P((4, 54.75))[1]
        m.box(f'head_{n}', H, [2, hy - 1.6, -1.4], [6, hy + 1.6, 1.4], 5)
        m.seg(f'beak_{n}', R, P((6, 55)), P((9, 53.5)), 1.6, 0.8, 7, 8, tip=True)
        for k, (a, b) in enumerate((((2.5, 56), (0, 58.5)), ((3.5, 56.25), (2, 59.5)), ((4.75, 56.25), (4.5, 59)))):
            m.seg(f'crest_{n}{k}', H, P(a), P(b), 1.1, 0.5, 4, 6, tip=True)
        for k, (a, b, w) in enumerate((((7.5, 47.5), (13.5, 50.5), 2.5), ((8.5, 45), (14, 46.5), 2), ((9.25, 42.5), (13.25, 42.75), 1.6))):
            m.seg(f'wing_{n}{k}', H, P(a), P(b), w, 0.6, 3 + k, 6, tip=True)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            q = P((6.25, 50.5))
            z0 = 1.3 if zs > 0 else -1.3
            for j in range(3):
                za = z0 + zs * j * 1.25
                m.box(f'tuft_{n}{zn}{j}', R, [q[0] - 0.8 + j * 0.4, q[1] - 0.9 - sign * j * 0.6, min(za, za + zs * 1.4)], [q[0] + 0.8 + j * 0.4, q[1] + 0.9 - sign * j * 0.6, max(za, za + zs * 1.4)], 6 + j)
    m.box('eye_glow', Z, [4.6, 55.25, -1.5], [5.4, 56, 1.5], 4)
    m.box('eye_d', R, [4.6, -26, -1.5], [5.4, -25.25, 1.5], 1)
    top, bot = (3, 53), (3, -23)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        q = lib.string_at(top, bot, pull, lib.string_t(top, bot, 38))
        m.box(f'string_{key}_cord', H, [q[0] - 0.2, q[1] - 2.5, -0.2], [q[0] + 0.2, q[1], 0.2], 3)
        m.chain(f'string_{key}_talon', R, [(q[0], q[1] - 2.5), (q[0] + 0.75, q[1] - 4.5), (q[0] - 0.25, q[1] - 6)], [1.1, 0.8], 0.55, 6, 8, tip=True)
    return m


def wild_windcutter_bow():
    """風切りの弓（レア）: 弓腕そのものが一枚の風切羽。角の羽軸の外側に霜の白の羽枝の板が段に並び、先ほど短い。
    握りの上の羽軸の付け根に雷の小玉"""
    m = M('wild_windcutter_bow')
    m.box('grip', H, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', R, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    nr = lib.line_normals(arm)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'shaft_{n}', H, [P(q) for q in arm], [1.6, 1.5, 1.4, 1.2, 1], 1.2, 4, 5, tip=True)
        for k in range(1, 5):
            a = arm[k]
            b = (a[0] + nr[k][0] * (4 - k * 0.5), a[1] + nr[k][1] * (4 - k * 0.5) - 1.8)
            m.seg(f'barb_{n}{k}', R, P(a), P(b), 3.6 - k * 0.4, 1.0, 6, 8, tip=True)
    m.box('zap_glow', Z, [13.9, 21, -0.8], [14.7, 22.6, 0.8], 4)
    strings(m, (4.5, 44.5), (4.5, -14.5))
    return m


def wild_frostfang_dagger():
    """（レア、2026-11-21 に作り直し・案 A）氷の層の刃: 半透明の層を重ねた細い刃（段で先へ細る）。鍔は霜の結晶の十字、刃の芯が光る
    ※ 2026-11-21 に Blockbench で手を入れた（重ねた層の刃を先へ細る菱形断面の一本の刃に替え、氷の色と結晶の筋を描いた）。正本は tools/models/x32/wild_frostfang_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_frostfang_dagger')
    hilt(m, 4, 1.3)
    m.box('guard_h', R, [-4.4, 4, -0.8], [4.4, 5.6, 0.8], 7)
    m.box('guard_v', R, [-0.8, 3.4, -2.2], [0.8, 6.2, 2.2], 7)
    y = 5.6
    for k in range(5):
        w = 3.4 - k * 0.55
        m.box(f'layer_{k}', R, [-w / 2, y, -0.6], [w / 2, y + 4.2, 0.6], 8 + (k % 2))
        y += 4.0
    m.seg('tip', R, (0, y - 0.2), (0, y + 3), 0.8, 0.8, 9, 9, tip=True)
    m.box('core_glow', Z, [-0.3, 7, -0.7], [0.3, 20, 0.7], 4)
    return m


def wild_stormwolf_fang():
    """（エピック、2026-11-21 に作り直し・案 A）雷狼の犬歯: 大きな犬歯を毛皮巻きの柄に差した。根元に狼の毛の房、牙の割れ目と房の留めが光る（2 か所）
    ※ 2026-11-21 に Blockbench で手を入れた（三つの箱の牙を反って先の尖る一本の牙のメッシュに替え、象牙色と根元の汚れを描いた）。正本は tools/models/x32/wild_stormwolf_fang.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_stormwolf_fang')
    hilt(m, 4, 1.3)
    m.box('collar', H, [-2.4, 4, -2.0], [2.4, 6.6, 2.0], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'tuft_{n}', H, (sd * 2.0, 5.4), (sd * 4.6, 2.6), 2.0, 1.4, 3, 5, tip=True)
    m.chain('fang', R, [(0, 6.2), (0.8, 14), (0.4, 21), (-1.4, 26)], [4.4, 3.4, 2.0], 2.0, 8, 9, tip=True)
    bolt(m, 'crack_glow', [(0.2, 8), (1.0, 11), (0.0, 13.6), (0.8, 16)], 0.6, 2.2)
    m.box('clasp_glow', Z, [-0.7, 4.2, -2.2], [0.7, 5.4, 2.2], 4)
    return m


# ---------------------------------------------------------------- 薙刀・大剣

def wild_gale_glaive():
    """（レア、2026-11-21 に作り直し・案 C）三日月の薙刀と長い吹き流し: 刃は外へ大きく張る三日月、刃の付け根から長い吹き流しが一本なびく。月の内側の縁が光る
    ※ 2026-11-21 に Blockbench で手を入れた（三日月の刃を中ほどが太く両端の細る一枚のメッシュに替え、刃の明暗を描いた）。正本は tools/models/x32/wild_gale_glaive.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_gale_glaive')
    hh(m, 34, 1.1)
    m.box('collar', H, [-1.7, 33, -1.7], [1.7, 35.4, 1.7], 4)
    m.arc('moon', R, (-2.0, 42), 7.2, -50, 80, 8, 2.6, 0.9, 6, 9)
    m.arc('moon_glow', Z, (-2.0, 42), 5.6, -36, 66, 6, 0.6, 1.1, 3, 4)
    m.seg('stem', R, (0, 34.6), (3.0, 37.2), 1.6, 1.0, 6, 7)
    m.chain('streamer', H, [(-1.6, 35), (-4.6, 34), (-8, 35.6), (-11, 33.6), (-13.4, 35)], [1.4, 1.2, 1.0, 0.8], 0.3, 3, 5, tip=True)
    return m


def wild_ramhorn_greatsword():
    """（エピック、2026-11-21 に作り直し・案 A）羊角の鍔: まっすぐな幅広の大剣。鍔が左右へ渦を巻く大きな羊の角。角の渦の芯（両方）と刃の稜線が光る
    ※ 2026-11-21 に Blockbench で手を入れた（刃を中央に稜線のある菱形断面で先の尖るメッシュに替え、鋼の明暗を描いた）。正本は tools/models/x32/wild_ramhorn_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ramhorn_greatsword')
    fur_grip(m, -9, 8, 1.6)
    m.box('pommel', H, [-2.2, -12.4, -2.0], [2.2, -10.2, 2.0], 4)
    m.box('blade', R, [-3.4, 9, -1.0], [3.4, 46, 1.0], grad=(9, 5, 46, 8))
    m.seg('tip', R, (0, 45.6), (0, 51), 6.8, 1.0, 8, 9, tip=True)
    m.box('ridge_glow', Z, [-0.3, 12, -1.1], [0.3, 40, 1.1], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        pts = []
        for k in range(8):
            a = math.radians(90 - sd * k * 45)
            r = 4.6 * (0.86 ** k)
            pts.append((sd * 5.4 + math.cos(a) * r * sd, 8.4 + math.sin(a) * r))
        m.chain(f'horn_{n}', H, pts, [2.8 * 0.88 ** k for k in range(7)], [2.4 * 0.9 ** k for k in range(7)], 2, 5)
        m.box(f'horn_{n}_glow', Z, [sd * 5.4 - 0.7, 7.6, -1.4], [sd * 5.4 + 0.7, 9.0, 1.4], 4)
    return m


# ---------------------------------------------------------------- 鈴杖・祭具

def wild_loom_bell_staff():
    """（エピック、2026-11-21 に作り直し・案 B）小さな機: 杖の先に二本の柱と上下の横木の機の枠、枠に縦糸が張られ、下の横木から鈴が二つ下がる。枠の上の飾り玉が光る
    ※ 2026-11-21 に Blockbench で手を入れた（縦糸に織りかけの布（雷色の縞）を足し、鈴を 45° 回した）。正本は tools/models/x32/wild_loom_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_loom_bell_staff')
    hh(m, 36)
    m.box('bar_lo', R, [-6, 36, -1.0], [6, 37.6, 1.0], 5)
    m.box('bar_hi', R, [-6, 46, -1.0], [6, 47.6, 1.0], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'post_{n}', H, [sd * 5.4 - 0.8, 36, -0.8], [sd * 5.4 + 0.8, 48.4, 0.8], 4)
        m.box(f'knob_{n}_glow', Z, [sd * 5.4 - 0.8, 48.4, -0.8], [sd * 5.4 + 0.8, 49.8, 0.8], 4)
    for k in range(6):
        x = -3.6 + k * 1.44
        m.box(f'warp_{k}', H, [x - 0.2, 37.6, -0.2], [x + 0.2, 46, 0.2], 5)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'cord_{n}', H, [sd * 3.6 - 0.25, 33.4, -0.25], [sd * 3.6 + 0.25, 36, 0.25], 4)
        m.box(f'bell_{n}', R, [sd * 3.6 - 1.4, 31, -1.4], [sd * 3.6 + 1.4, 33.6, 1.4], grad=(31, 5, 33.6, 7))
    return m


def wild_peak_totem():
    """（レア、2026-11-21 に作り直し・案 A）三つの峰: 尖った峰三つの石の柱頭（真ん中が高い）、峰に雪、峰の谷に祈りの旗の紐。柱の真ん中に雷の印
    ※ 2026-11-21 に Blockbench で手を入れた（三つの峰と雪の冠を尖った四角錐のメッシュに替え、岩肌と雪を描いた）。正本は tools/models/x32/wild_peak_totem.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_peak_totem')
    hh(m, 38, 1.6)
    m.box('plinth', R, [-6, 37, -2.2], [6, 39.4, 2.2], 5)
    for k, (x, h, w) in enumerate(((-3.8, 6, 4.4), (0, 10, 5.6), (3.8, 6.6, 4.4))):
        m.seg(f'peak_{k}', R, (x, 39), (x, 39 + h), w, 3.6, 4, 6, tip=True)
        m.seg(f'snow_{k}', H, (x, 39 + h * 0.62), (x, 39 + h + 0.2), w * 0.42, 3.8, 5, 6, tip=True)
    m.box('rope', H, [-6, 43.4, -0.15], [6, 43.8, 0.15], 4)
    for k, x in enumerate((-4.8, -2.2, 2.2, 4.8)):
        m.box(f'flag_{k}', H, [x - 0.6, 41.8, -0.2], [x + 0.6, 43.4, 0.2], 3 + k % 3)
    m.box('mark_glow', Z, [-0.8, 26, -1.7], [0.8, 28, 1.7], 4)
    return m


def wild_stormdrum_totem():
    """（ヒロイック、2026-11-21 に作り直し・案 C）大きな枠太鼓: 柱の先に大きな丸い枠の太鼓（縁に小さな鈴）。皮の真ん中に雷の渦、まわりに雷の玉が浮く
    ※ 2026-11-21 に Blockbench で手を入れた（枠の継ぎ目を詰めて丸くし、四角い皮を八角の円盤のメッシュに替えて枠からはみ出さないようにし、皮の表と裏に雷の模様を描いた）。正本は tools/models/x32/wild_stormdrum_totem.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_stormdrum_totem')
    hh(m, 36, 1.5)
    m.arc('frame', H, (0, 45), 8.4, 0, 360, 16, 1.6, 2.4, 2, 4)
    m.box('skin', R, [-7.4, 38, -0.4], [7.4, 52, 0.4], grad=(38, 7, 52, 9))
    m.arc('swirl_glow', Z, (0, 45), 2.6, 0, 300, 6, 0.8, 1.0, 3, 4)
    for k in range(6):
        a = math.radians(k * 60 + 30)
        x, y = math.cos(a) * 8.6, 45 + math.sin(a) * 8.6
        m.box(f'jingle_{k}', R, [x - 0.8, y - 0.8, -1.4], [x + 0.8, y + 0.8, 1.4], 6)
    for k, (x, y) in enumerate(((-11, 49), (11, 49), (0, 56))):
        m.box(f'float_orb_{k}_glow', Z, [x - 0.8, y - 0.8, -0.8], [x + 0.8, y + 0.8, 0.8], 4)
    return m


ALL = [wild_cloudreader_staff, wild_fluff_staff, wild_warden_crown_staff, wild_conductor_axe, wild_snowsplitter_axe,
       wild_eaglet_longbow, wild_windcutter_bow, wild_frostfang_dagger, wild_stormwolf_fang, wild_gale_glaive,
       wild_ramhorn_greatsword, wild_loom_bell_staff, wild_peak_totem, wild_stormdrum_totem]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
