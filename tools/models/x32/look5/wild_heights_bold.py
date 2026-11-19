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


# ---------------------------------------------------------------- 杖

def wild_cloudreader_staff():
    """雲読みの杖: 杖の先に風見の矢羽根が横に回り、その上に雲の形の薄い鉄板（三つの丸の段）。
    矢羽根の軸の先に雷の玉"""
    m = M('wild_cloudreader_staff')
    fur_grip(m, -14, 2)
    shaft(m, 3.25, 32, rings=(13, 23))
    m.box('vane_axle', R, [-0.5, 31, -0.5], [0.5, 36, 0.5], 6)
    m.box('vane_arrow', R, [-6, 33, -0.4], [6, 34, 0.4], grad=(33, 5, 34, 7))
    m.seg('vane_head', R, (6, 33.5), (8.5, 33.5), 2.5, 0.5, 7, 8, tip=True)
    m.box('vane_tail', H, [-7.5, 32, -0.3], [-5, 35.5, 0.3], 4)
    for k, (x, y, w) in enumerate(((-2.5, 36, 2.5), (0.5, 37, 3), (3, 36, 2.25))):
        m.box(f'cloud_{k}', R, [x - w / 2, y, -0.6], [x + w / 2, y + w * 0.8, 0.6], grad=(y, 7, y + w * 0.8, 9))
    m.box('cloud_base', R, [-4, 36, -0.6], [4.25, 37, 0.6], 7)
    m.box('orb_glow', Z, [8.5, 32.75, -0.75], [10, 34.25, 0.75], 4)
    return m


def wild_fluff_staff():
    """綿雷の杖: 杖の先に、綿毛のような雷雲の塊（ふくらんだ箱を三つ重ねる）。雲の下に小さな雷が二本垂れる。
    雲の芯が光る"""
    m = M('wild_fluff_staff')
    fur_grip(m, -14, 2, 1.3)
    shaft(m, 3.25, 31, r=1.1, rings=(16,))
    m.box('cloud_core_glow', Z, [-1.5, 32, -1.5], [1.5, 35, 1.5], grad=(32, 2, 35, 4))
    for k, (x, y, s) in enumerate(((-2.5, 31.5, 4), (2.25, 31.25, 4.5), (0, 33.5, 5), (-0.5, 30, 3.5))):
        m.box(f'puff_{k}', R, [x - s / 2, y, -s / 2.2], [x + s / 2, y + s * 0.85, s / 2.2], grad=(y, 7, y + s * 0.85, 9))
    for k, x in enumerate((-2.5, 2.5)):
        m.chain(f'bolt_{k}', Z, [(x, 30.5), (x + 0.75, 28.5), (x - 0.25, 27.5), (x + 0.5, 25.5)], [0.9, 0.9, 0.9], 0.45, 3, 5, tip=True)
    return m


def wild_warden_crown_staff():
    """羽守の焼け冠杖: 杖の先に、焼け焦げた羽の冠（五本の羽が王冠のように立つ）。冠の中に雷が溜まって光る。
    杖には焦げ跡の段"""
    m = M('wild_warden_crown_staff')
    fur_grip(m, -14, 2)
    shaft(m, 3.25, 30, rings=(10, 18, 25))
    m.box('crown_band', H, [-3, 29.5, -3], [3, 31.5, 3], grad=(29.5, 3, 31.5, 5))
    m.box('crown_glow', Z, [-1.75, 31.5, -1.75], [1.75, 34, 1.75], grad=(31.5, 2, 34, 4))
    for k, (x, z, lean) in enumerate(((-2.5, 0, -1.5), (-1, -2, -0.5), (1, -2, 0.5), (2.5, 0, 1.5), (0, 2.25, 0))):
        m.seg(f'plume_{k}', R if k % 2 else H, (x, 31), (x + lean, 38.5 - abs(lean)), 1.5, 0.5, 4, 1, tip=True, zc=z)
    return m


# ---------------------------------------------------------------- 斧

def wild_conductor_axe():
    """雷拾いの避雷斧: 片刃の斧の頭のてっぺんから、避雷針の細い鉄の棒が真上へ伸びる。
    棒の先の三叉に雷が光り、銅の線（角色）が刃まで巻いて降りる"""
    m = M('wild_conductor_axe')
    fur_grip(m, -6, 6)
    shaft(m, 7.25, 24, rings=(15,))
    m.box('eye', R, [-1.9, 20, -1.75], [1.9, 24.5, 1.75], 6)
    pts = [(1.5, 21), (5, 19), (8.5, 18.5)]
    m.chain('blade_top', R, [(1.5, 24), (5, 25.5), (8.5, 27)], [3, 3], 1, 6, 8, tip=False)
    m.box('blade', R, [1.5, 17.5, -0.8], [8, 25.5, 0.8], grad=(17.5, 5, 25.5, 8))
    m.seg('beard', R, (6.5, 18), (8.5, 15), 2.5, 0.75, 7, 8, tip=True)
    m.box('edge', R, [8, 16.5, -0.6], [9.25, 27, 0.6], 9)
    m.box('rod', R, [-0.4, 24.5, -0.4], [0.4, 36, 0.4], 6)
    for k, x in enumerate((-1.5, 0, 1.5)):
        m.seg(f'tine_{k}', R, (x * 0.4, 35.5), (x, 39 - abs(x) * 0.5), 0.8, 0.4, 7, 9, tip=True)
    m.box('tip_glow', Z, [-0.75, 35.5, -0.75], [0.75, 37, 0.75], 4)
    m.chain('wire', H, [(0.5, 35), (1.25, 30), (0.5, 26), (3, 23)], [0.6, 0.6, 0.6], 0.5, 3, 4, tip=False, zc=0.6)
    return m


def wild_snowsplitter_axe():
    """雪割りの斧: 雪庇を割る幅広の三日月の刃（+x）。刃の背に氷柱のような鉤が三つ下がり、
    柄の上端に霜の結晶が雷の色で光る"""
    m = M('wild_snowsplitter_axe')
    fur_grip(m, -6, 6)
    shaft(m, 7.25, 25)
    m.box('eye', R, [-1.9, 19.5, -1.75], [1.9, 24.5, 1.75], 6)
    m.arc('moon', R, (3, 22), 6.5, -60, 60, 5, 3, 0.8, 6, 9)
    m.box('cheek', R, [1.75, 19, -0.9], [5, 25, 0.9], grad=(19, 5, 25, 7))
    for k, x in enumerate((-2, -3.25, -4.5)):
        m.seg(f'icicle_{k}', R, (x, 22 + k * 0.4), (x - 0.5, 18.5 - k), 1.25, 0.5, 8, 9, tip=True)
    m.box('back_bar', R, [-5, 21.5, -0.6], [-1.9, 23, 0.6], 6)
    diamond(m, 'frost_glow', Z, (0, 26.25), 2.25, 0.8, 3, 5)
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
    """風切りの弓: 鳥の風切羽をそのまま弓腕にしたような、片側に羽枝のぎざぎざが並ぶ弓。
    握りの上の羽軸の付け根に雷の小玉"""
    m = M('wild_windcutter_bow')
    m.box('grip', H, [10.5, 10, -1.4], [13.5, 20, 1.4], grad=(10, 2, 20, 4), wrap=(10, 20))
    arm = [(12, 19.5), (11, 27), (9, 34), (6.5, 40), (4, 44)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', R, [mirror(p, sign) for p in arm], [2.5, 2.25, 2, 1.75], 1, 5, 8, tip=False)
        for k, (x, y) in enumerate(((11.75, 24), (10.5, 29), (9, 34), (7, 38.5))):
            m.seg(f'barb_{n}{k}', R, mirror((x, y), sign), mirror((x + 3 - k * 0.4, y + 2.5), sign), 1.5, 0.4, 7, 9, tip=True)
        m.seg(f'nock_{n}', H, mirror((4.5, 44), sign), mirror((2, 44), sign), 1.75, 0.9, 4, 5)
    m.box('quill_glow', Z, [13.4, 20, -0.6], [14.4, 21.25, 0.6], 4)
    strings(m, (2.5, 44), (2.5, -14))
    for k, (x, y) in enumerate(((10.5, 5), (9, 0), (7.25, -4.5))):
        m.seg(f'belly_barb_{k}', R, (x, y), (x - 2.75 + k * 0.3, y - 2.25), 1.4, 0.4, 7, 9, tip=True)
    return m


# ---------------------------------------------------------------- 短剣・牙

def wild_frostfang_dagger():
    """霜牙の短剣: 氷の牙のように半透明の層を重ねた細い刃（段で先へ細る）。鍔は霜の結晶の十字、
    刃の芯に雷の細い光"""
    m = M('wild_frostfang_dagger')
    fur_grip(m, -4, 4, 1.3)
    m.box('guard_h', R, [-3.5, 5.25, -0.8], [3.5, 6.5, 0.8], 7)
    m.box('guard_v', R, [-0.8, 4.75, -2.5], [0.8, 7, 2.5], 7)
    for k, (y0, y1, w) in enumerate(((6.5, 11, 2), (11, 15.5, 1.6), (15.5, 19, 1.15), (19, 21.5, 0.7))):
        m.box(f'blade_{k}', R, [-w, y0, -0.55], [w, y1, 0.55], grad=(y0, 6 + k * 0.5, y1, 7 + k * 0.5))
    m.box('core_glow', Z, [-0.25, 7, -0.6], [0.25, 17, 0.6], grad=(7, 2, 17, 4))
    m.seg('tip', R, (0, 21.5), (0, 23.5), 1, 0.4, 8, 9, tip=True)
    return m


def wild_stormwolf_fang():
    """雷狼の牙: 雷狼の大きな犬歯を、毛皮を巻いた柄に差し込んだもの。牙は少し +x へ反り、根元に狼の毛の房。
    牙の根元の割れ目から雷が覗く"""
    m = M('wild_stormwolf_fang')
    fur_grip(m, -4, 4, 1.4)
    m.box('ruff', H, [-2.5, 4.5, -2.25], [2.5, 7, 2.25], grad=(4.5, 3, 7, 5))
    for k, (x, z) in enumerate(((-2.25, 0), (2.25, 0), (0, -2))):
        m.seg(f'tuft_{k}', H, (x, 6), (x * 1.3, 8.5), 1, 0.5, 4, 5, tip=True, zc=z)
    m.chain('fang', H, [(0, 6.5), (0.5, 12), (1.75, 17), (3.25, 21)], [3.25, 2.5, 1.5], 1.4, 4, 6, tip=True)
    m.box('crack_glow', Z, [-0.25, 7.5, -1.5], [0.5, 10.5, 1.5], 4)
    return m


# ---------------------------------------------------------------- 薙刀・大剣

def wild_gale_glaive():
    """風鳴りの薙刀: 長柄の先に細身の反った刃。刃の背に笛のような穴の列（風が鳴る）、
    柄と刃の境に旗のような布が二本なびく。刃の背の穴の一つが雷で光る"""
    m = M('wild_gale_glaive')
    fur_grip(m, -16, 0)
    shaft(m, 1.25, 32, r=1.15, rings=(12, 22))
    m.box('socket', R, [-1.75, 31, -1.6], [1.75, 35, 1.6], grad=(31, 5, 35, 7))
    pts = [(0, 35), (1.5, 41), (3, 47), (3.5, 52), (2.5, 56)]
    m.chain('blade', R, pts, [3.5, 3.25, 2.75, 1.75], 0.75, 6, 9, tip=True)
    for k, (x, y) in enumerate(((-0.25, 39), (1, 44), (2, 49))):
        m.box(f'hole_{k}', R, [x - 0.5, y, -0.85], [x + 0.5, y + 1, 0.85], 2)
    m.box('hole_glow', Z, [0.5, 44, -0.9], [1.5, 45, 0.9], 4)
    for k, (dx, ln) in enumerate(((-1, 7), (1, 5))):
        m.chain(f'streamer_{k}', H, [(dx * 1.5, 33), (dx * 4, 31.5), (dx * 6, 33 - ln * 0.4), (dx * 7.5, 31 - ln * 0.5)],
                [1.5, 1.25, 1], 0.25, 3, 5, tip=True)
    return m


def wild_ramhorn_greatsword():
    """羊角の大剣: 幅広でまっすぐな大剣の鍔が、左右に渦を巻く大きな羊の角。刃の中央に段の稜線、
    角の渦の芯の片方に雷"""
    m = M('wild_ramhorn_greatsword')
    fur_grip(m, -8, 8, 1.6)
    m.box('pommel', R, [-2, -11, -2], [2, -9.25, 2], 7)
    m.box('guard', H, [-3, 9, -1.75], [3, 11.5, 1.75], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.arc(f'horn_{n}', H, (sd * 5, 10), 3.25, (0 if sd > 0 else 180) + 90 * 0, (0 if sd > 0 else 180) + sd * 300, 6, 2.25, 1.1, 3, 5)
    m.box('core_glow', Z, [4.25, 9.25, -1.2], [5.75, 10.75, 1.2], 4)
    m.box('blade', R, [-4, 11.5, -1], [4, 45, 1], grad=(11.5, 4, 45, 7))
    m.box('ridge', R, [-0.6, 12, -1.15], [0.6, 43, 1.15], grad=(12, 7, 43, 9))
    for k, w in enumerate((3.25, 2.25, 1.25)):
        m.box(f'tip_{k}', R, [-w, 45 + k * 1.75, -0.95 + k * 0.1], [w, 46.75 + k * 1.75, 0.95 - k * 0.1], 6 + k)
    return m


# ---------------------------------------------------------------- 鈴杖・祭具

def wild_loom_bell_staff():
    """機織りの風鈴杖: 杖の先に機織りの杼（ひ、舟形の木）が横に渡り、そこから糸で風鈴の短冊と鈴が三つ下がる。
    真ん中の鈴の舌が雷で光る"""
    m = M('wild_loom_bell_staff')
    fur_grip(m, -14, 2)
    shaft(m, 3.25, 33, rings=(15,))
    m.chain('shuttle', H, [(-7.5, 32), (-4, 33), (4, 33), (7.5, 32)], [1.5, 2, 1.5], 1, 3, 5, tip=False)
    for k, x in enumerate((-5, 0, 5)):
        top = 32.5
        m.box(f'thread_{k}', H, [x - 0.15, top - 4, -0.15], [x + 0.15, top, 0.15], 4)
        m.box(f'bell_{k}', R, [x - 1.25, top - 6.5, -1.25], [x + 1.25, top - 4, 1.25], grad=(top - 6.5, 6, top - 4, 8))
        m.box(f'strip_{k}', H, [x - 0.6, top - 11, -0.15], [x + 0.6, top - 7.5, 0.15], 3)
    m.box('tongue_glow', Z, [-0.4, 25, -0.4], [0.4, 26.25, 0.4], 4)
    return m


def wild_peak_totem():
    """峰の祈り柱: 三つの尖った峰を象った石の柱頭。真ん中の峰が高く、雪の白（角色）を被る。
    峰の谷に祈りの旗の紐が渡り、柱の真ん中に雷の印"""
    m = M('wild_peak_totem')
    fur_grip(m, -14, 2, 1.6)
    m.box('pillar', R, [-1.9, 3, -1.9], [1.9, 28, 1.9], grad=(3, 3, 28, 5))
    diamond(m, 'mark_glow', Z, (0, 16), 2.5, 2, 3, 5)
    m.box('base', R, [-4.5, 27.5, -2.25], [4.5, 29.5, 2.25], 5)
    for k, (x, h, w) in enumerate(((-3, 6, 3), (0, 10, 3.5), (3, 7, 3))):
        m.seg(f'peak_{k}', R, (x, 29), (x, 29 + h), w, 2, 5, 8, tip=True)
        m.seg(f'snow_{k}', H, (x, 29 + h * 0.55), (x, 29 + h), w * 0.6, 2.1, 4, 5, tip=True)
    m.chain('prayer_line', H, [(-3, 33), (0, 31.5), (3, 33.5)], [0.5, 0.5], 2.3, 3, 4, tip=False)
    for k, x in enumerate((-1.75, 1.75)):
        m.box(f'flag_{k}', H, [x - 0.6, 30, -2.4], [x + 0.6, 32, -2.2], 2)
    return m


def wild_stormdrum_totem():
    """嵐呼びの太鼓柱: 柱の上に、横向きの太鼓（獣の皮を張った円筒）。太鼓の両側に撥（ばち）が二本差してあり、
    太鼓の皮の中心が雷で光る"""
    m = M('wild_stormdrum_totem')
    fur_grip(m, -14, 2, 1.5)
    m.box('pillar', R, [-1.5, 3, -1.5], [1.5, 26, 1.5], grad=(3, 3, 26, 6))
    m.box('cradle', R, [-3, 25.5, -2], [3, 27, 2], 6)
    m.box('drum_body', R, [-5, 27, -3.5], [5, 34, 3.5], grad=(27, 4, 34, 6))
    for k, x in enumerate((-5.75, 5)):
        m.box(f'hide_{k}', H, [x, 27.5, -3.25], [x + 0.75, 33.5, 3.25], grad=(27.5, 4, 33.5, 5))
    for k, x in enumerate((-3, 3)):
        m.box(f'hoop_{k}', H, [x - 0.4, 26.75, -3.75], [x + 0.4, 34.25, 3.75], 3)
    m.box('hide_glow', Z, [5.6, 29.5, -1], [5.9, 31.5, 1], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'stick_{n}', H, (sd * 2, 33.5), (sd * 4, 38.5), 0.9, 0.45, 4, 5)
        m.box(f'stick_head_{n}', H, [sd * 4 - 0.75, 38, -0.75], [sd * 4 + 0.75, 39.5, 0.75], 5)
    return m


ALL = [wild_cloudreader_staff, wild_fluff_staff, wild_warden_crown_staff, wild_conductor_axe, wild_snowsplitter_axe,
       wild_eaglet_longbow, wild_windcutter_bow, wild_frostfang_dagger, wild_stormwolf_fang, wild_gale_glaive,
       wild_ramhorn_greatsword, wild_loom_bell_staff, wild_peak_totem, wild_stormdrum_totem]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
