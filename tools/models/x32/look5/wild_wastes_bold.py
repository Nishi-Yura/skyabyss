# -*- coding: utf-8 -*-
"""野・荒れ地（look5・個性版）。灰の降る火口の荒れ地で拾う 10 品（英・叙）。
色: 焼けた鉱滓と煤の鉄（slag 赤みの焦げ茶の 1 系統）、差し色は降り積もった灰（ash 冷たい灰白）、光は熔岩（magma 橙赤）を品ごとに一か所。
灰の民の道具と、火口の獣の骨。表面は割れ目と灰の積もりで、光は割れ目の奥から覗かせる。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'slag': ['#140c0a', '#20140e', '#2e1c14', '#3e261a', '#503222', '#64402c', '#7a5038', '#946448', '#b07c5c', '#cc9a78'],
    'cinder': ['#3a3634', '#5a5450', '#7c7570', '#a09892', '#c4bcb6', '#e6e0dc'],
    'magma': ['#6a1004', '#a82006', '#e04010', '#ff7a20', '#ffb850', '#fff0b0'],
})
S, A, G = 'slag', 'cinder', 'magma'


def M(name):
    return Model(name, accent=A, glowmat=G)


def wrap_grip(m, y0, y1, r=1.5):
    """灰色の布を巻いた握り。上下に鉱滓の鉄の輪"""
    m.box('grip', A, [-r, y0, -r], [r, y1, r], grad=(y0, 1, y1, 3), wrap=(y0, y1))
    m.box('ring_d', S, [-r - 0.4, y0 - 1.25, -r - 0.4], [r + 0.4, y0, r + 0.4], 6)
    m.box('ring_u', S, [-r - 0.4, y1, -r - 0.4], [r + 0.4, y1 + 1.25, r + 0.4], 7)


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


# ---------------------------------------------------------------- 弓

def wild_ashhook_bow():
    """灰ひきずりの鉤弓（エピック）: 弓腕の先がそれぞれ内側（弦の方）へ鋭く大きく曲がる鉤。腕の腹にも小さな鉤が二本ずつ並ぶ。
    弓腕の中ほどに灰の布が巻かれ、布の端が左右（奥行き）へ垂れてたなびく。握りの上の割れ目から熔岩が大きく覗き、
    火の粉が二つ立つ。弦には鉄の小さな鉤が上下に一つずつ掛かる"""
    m = M('wild_ashhook_bow')
    m.box('grip', A, [10.25, 9.5, -1.75], [13.75, 20.5, 1.75], grad=(9.5, 1, 20.5, 3), wrap=(9.5, 20.5))
    m.box('crack_lip', S, [13.25, 19.75, -1.6], [15.25, 23, 1.6], 3)
    m.box('crack_glow', G, [14.75, 20.25, -0.9], [15.5, 22.5, 0.9], 4)
    diamond(m, 'ember_a_glow', G, (16.75, 24.5), 1, 0.45, 4, 5)
    diamond(m, 'ember_b_glow', G, (15.75, 26.75), 0.75, 0.4, 4, 5)
    arm = [(12, 19.5), (11.75, 27), (10, 34), (7.5, 39.5), (5.5, 42.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'arm_{n}', S, [P(q) for q in arm], [3.5, 3.25, 2.75, 2.25], 1.4, 4, 7, tip=False)
        m.chain(f'hook_{n}', S, [P(q) for q in ((5.5, 42.5), (3, 45.25), (0, 44.25), (-1, 40.75), (0.25, 38.5))], [2.5, 2.25, 1.75, 1.25], 1.15, 7, 9, tip=True)
        for k, (x, y) in enumerate(((10.5, 27.5), (8.5, 35))):
            q = P((x, y))
            m.chain(f'barb_{n}{k}', S, [q, (q[0] - 2, q[1] + sign * 0.5), (q[0] - 2.5, q[1] - sign * 1)], [1.25, 0.9], 0.8, 6, 8, tip=True)
        y = P((10.5, 31))[1]
        m.box(f'rag_{n}', A, [8.75, y - 2, -1.75], [12.75, y + 2, 1.75], grad=(y - 2, 2, y + 2, 3))
        for zs, zn in ((1, 'f'), (-1, 'b')):
            z0 = 1.75 if zs > 0 else -1.75
            for j, (dx, dy, l) in enumerate(((0.5, -1.5, 2.5), (1.5, -4, 2.25), (2.75, -6.25, 2))):
                za = z0 + zs * j * 1.4
                m.box(f'tail_{n}{zn}{j}', A, [10 + dx, y + sign * dy - 1.5, min(za, za + zs * 1.6)], [10 + dx + 1.3, y + sign * dy + 1.5, max(za, za + zs * 1.6)], 2 + j * 0.5)
    top, bot = (3.5, 44), (3.5, -14)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((36, -6)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.chain(f'string_{key}_hook{k}', S, [(q[0] - 0.25, q[1]), (q[0] + 1.5, q[1] - 1.25), (q[0] + 1, q[1] - 2.75)], [0.8, 0.7], 0.45, 6, 8, tip=True)
    return m


def wild_crater_longbow():
    """火口の大弓（ヒロイック）: 背丈ほどの重い弓。弓腕は冷えた溶岩の塊を継いだように節くれ、継ぎ目の割れ目はどれも熔岩の光で光る。
    継ぎ目の下からは熔岩のしずくが弓の懐へ垂れ、左右（奥行き）には黒い溶岩の棘が突き出す。握りの前には小さな火口の鉢が抱かれ、
    中に熔岩がたまって煙の石が浮く。弦受けは灰の骨、弦には灰の骨の玉が二つずつ"""
    m = M('wild_crater_longbow')
    m.box('grip', A, [10.25, 9, -1.9], [13.75, 21, 1.9], grad=(9, 1, 21, 3), wrap=(9, 21))
    m.box('crater', S, [13.5, 10.5, -2.75], [21, 15.5, 2.75], grad=(10.5, 2, 15.5, 5))
    m.box('crater_rim_l', S, [13.25, 15.5, -2.6], [14.75, 17, 2.6], 6)
    m.box('crater_rim_r', S, [19.5, 15.5, -2.9], [21.25, 17.75, 2.9], 6)
    m.box('crater_pool_glow', G, [14.75, 15, -2.4], [19.5, 15.9, 2.4], 4)
    for k, (a1, b1, w1) in enumerate((((17, 15.75), (17.5, 21), 1.6), ((15.75, 15.75), (15, 19), 1.1), ((18.75, 15.75), (19.75, 19.5), 1.1))):
        m.seg(f'spout_{k}_glow', G, a1, b1, w1, 0.8, 3, 5, tip=True)
    for k, (x, y, s) in enumerate(((16, 23, 1.75), (18.75, 24.75, 1.4), (16.75, 27, 1.1), (19.5, 28.5, 0.8))):
        diamond(m, f'smoke_{k}', A, (x, y), s, 0.7, 3, 5)
    m.box('crater_foot', S, [15, 10.5, -1.6], [18, 11.5, 1.6], 3)
    nodes = [(12, 20.5), (11.5, 28), (10, 36), (7.75, 43.5), (5, 50), (3.5, 53)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        for k in range(len(nodes) - 1):
            a, b = P(nodes[k]), P(nodes[k + 1])
            m.seg(f'lump_{n}{k}', S, a, b, 4.25 - k * 0.45, 1.8 - k * 0.15, 3 + k, 4 + k)
        for k, p in enumerate(nodes[1:5]):
            q = P(p)
            w = 2.2 - k * 0.25
            m.box(f'seam_{n}{k}_glow', G, [q[0] - w, q[1] - 0.3, -1.85 + k * 0.15], [q[0] + w, q[1] + 0.3, 1.85 - k * 0.15], 4)
        for k, (x, y, ln) in enumerate(((10.25, 27, 3.5), (8.75, 35, 3), (6.5, 42.5, 2.25))):
            q = P((x, y))
            m.seg(f'drip_{n}{k}_glow', G, q, (q[0] - 0.4, q[1] - sign * ln), 1, 0.6, 3, 5, tip=True)
            diamond(m, f'drop_{n}{k}_glow', G, (q[0] - 0.5, q[1] - sign * (ln + 1.4)), 0.9, 0.45, 4, 5)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            for k, p in enumerate(nodes[1:3]):
                q = P(p)
                z0 = 1.6 if zs > 0 else -1.6
                for j, (w, ln) in enumerate(((1.75, 2), (1.25, 1.75), (0.75, 1.25))):
                    za = z0 + zs * (j * 1.7)
                    m.box(f'spike_{n}{zn}{k}{j}', S, [q[0] - w / 2 + 0.3, q[1] - w / 2, min(za, za + zs * ln)], [q[0] + w / 2 + 0.3, q[1] + w / 2, max(za, za + zs * ln)], 2 + j)
        for k, (bx, by, h, w) in enumerate(((12.75, 25, 6.5, 3.25), (11.75, 32, 7.5, 3.5), (10, 39.5, 6.5, 3), (7.75, 46.5, 4.75, 2.5))):
            q = P((bx, by))
            m.seg(f'ridge_{n}{k}', S, q, (q[0] + h, q[1] + sign * h * 0.35), w, 1.2, 5 + k * 0.5, 8, tip=True)
            m.seg(f'ridge_vein_{n}{k}_glow', G, (q[0] + 0.5, q[1]), (q[0] + h * 0.7, q[1] + sign * h * 0.25), 0.6, 1.25, 3, 5, over=0)
        m.chain(f'vein_{n}_glow', G, [P((q0[0] - 1.6, q0[1])) for q0 in nodes[1:5]], [0.7, 0.65, 0.6], 1.7, 3, 5, tip=False)
        m.seg(f'nock_{n}', A, P((3.5, 53)), P((1, 53.5)), 2, 1, 3, 5)
    top, bot = (1.5, 53.5), (1.5, -23.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((47, 44, -14, -17)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_bone{k}', A, [q[0] - 0.8, q[1] - 0.8, -0.8], [q[0] + 0.8, q[1] + 0.8, 0.8], 4 + k % 2)
    return m


def wh(m, top, r=1.3):
    """荒野の長柄: 灰布巻きの握り、煤けた鉄の柄、鉱滓の石突き"""
    wrap_grip(m, -12, 2, 1.6)
    m.box('shaft', S, [-r, 2, -r], [r, top, r], grad=(2, 3, top, 6))
    m.box('butt', S, [-2.0, -15.6, -2.0], [2.0, -13.2, 2.0], grad=(-15.6, 2, -13.2, 5))


def whilt(m, top=7, r=1.5):
    wrap_grip(m, -7, top, r)
    m.box('pommel', S, [-2.1, -10.4, -1.7], [2.1, -8.2, 1.7], grad=(-10.4, 3, -8.2, 6))


def crack(m, name, pts, w=0.7, d=None):
    m.chain(name, G, pts, [w] * (len(pts) - 1), d if d is not None else 1.0, 3, 5, tip=False)


# ---------------------------------------------------------------- 鎚

def wild_ashrite_maul():
    """（ヒロイック、2026-11-21 に作り直し・案 C）葬列の門柱: 頭は左右に張り出す二本の石柱と上に渡した梁（鳥居のような葬列の門）。梁の真ん中に吊った鐘が熔岩で光り、柱の表裏に印
    ※ 2026-11-21 に Blockbench で手を入れた（四角い鐘を六角の釣鐘のメッシュに替え、石柱の四面にひびを描いた）。正本は tools/models/x32/wild_ashrite_maul.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ashrite_maul')
    wh(m, 28, 1.5)
    m.box('sill', A, [-7.6, 27, -2.6], [7.6, 29, 2.6], 3)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'pillar_{n}', A, [sd * 6.2 - 1.4, 29, -2.2], [sd * 6.2 + 1.4, 42, 2.2], grad=(29, 1, 42, 3))
        for zs, zn in ((1, 'f'), (-1, 'b')):
            z = 2.2 if zs > 0 else -2.5
            m.box(f'sigil_{n}{zn}', S, [sd * 6.2 - 0.5, 33, z], [sd * 6.2 + 0.5, 38, z + 0.3], 3)
    m.box('lintel', S, [-9.4, 42, -2.8], [9.4, 44.4, 2.8], grad=(42, 4, 44.4, 7))
    m.box('hang', A, [-0.3, 39.6, -0.3], [0.3, 42, 0.3], 3)
    m.box('bell_glow', G, [-1.6, 35.4, -1.6], [1.6, 39.8, 1.6], 4)
    return m


def wild_bellows_hammer():
    """（エピック、2026-11-21 に作り直し・案 B）炉の鎚: 鎚の頭が小さな炉そのもの（四角い炉の口から火が見える）。上に煙突、横の取っ手に鞴の革袋がぶら下がる
    ※ 2026-11-21 に Blockbench で手を入れた（炉の四面に煉瓦の目地を描き、鞴の革袋を炉にもたれるよう傾けて革のひだを描いた）。正本は tools/models/x32/wild_bellows_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_bellows_hammer')
    wh(m, 28)
    m.box('furnace', S, [-4, 26, -3.4], [4.6, 34, 3.4], grad=(26, 3, 34, 6))
    m.box('face', S, [4.6, 25.6, -3.0], [5.8, 34.4, 3.0], 8)
    for sd, n in ((1, 'f'), (-1, 'b')):
        z = 3.4 if sd > 0 else -3.7
        m.box(f'mouth_{n}_glow', G, [-1.6, 27.6, z], [1.6, 30.4, z + 0.3], 4)
    m.box('chimney', S, [-2.6, 34, -1.2], [-0.6, 38.6, 1.2], 5)
    m.box('chimney_cap', A, [-3.0, 38.6, -1.6], [-0.2, 39.4, 1.6], 3)
    m.box('handle', S, [-6, 31, -0.5], [-4, 32, 0.5], 4)
    m.box('bag', A, [-7.4, 26.6, -1.8], [-4.8, 31, 1.8], grad=(26.6, 1, 31, 3))
    return m


# ---------------------------------------------------------------- 杖・鈴杖

def wild_emberbug_staff():
    """（エピック、2026-11-21 に作り直し・案 C）吊り灯の蟲: 杖の先が鉤に曲がり、鎖で吊った灯籠（四角い枠に灰の紙）。中の熾り蟲の光が紙越しに透ける。鉤の先に蟲が一匹
    ※ 2026-11-21 に Blockbench で手を入れた（灯籠の上に四角錐の屋根を足し、紙に障子の格子を描き、蟲を黒地に熾りの点で塗った）。正本は tools/models/x32/wild_emberbug_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_emberbug_staff')
    wh(m, 40)
    m.chain('hook', S, [(0, 39.6), (0.6, 45), (4, 48), (7.4, 46.4)], [2.2, 2.0, 1.6], 1.6, 4, 6, tip=False)
    m.box('chain', A, [7.0, 41.6, -0.3], [7.6, 46, 0.3], 3)
    m.box('lantern_top', S, [4.8, 40.6, -2.6], [9.8, 41.6, 2.6], 5)
    m.box('paper_glow', G, [5.4, 35.4, -2.0], [9.2, 40.6, 2.0], 4)
    m.box('lantern_bot', S, [4.8, 34.4, -2.6], [9.8, 35.4, 2.6], 5)
    for k, (dx, dz) in enumerate(((-2.4, -2.4), (2.0, -2.4), (-2.4, 2.0), (2.0, 2.0))):
        m.box(f'post_{k}', S, [7.3 + dx, 35.4, dz], [7.3 + dx + 0.4, 40.6, dz + 0.4], 3)
    m.box('beetle', A, [6.6, 46.8, -0.8], [8.0, 48.2, 0.8], 4)
    return m


def wild_ashsow_bell_staff():
    """（エピック、2026-11-21 に作り直し・案 A）逆さ柄杓: 杖の先から横へ腕、そこに灰の柄杓が下を向き、柄杓の中の舌が熔岩で光る。腕の反対に小さな鈴が二つ
    ※ 2026-11-21 に Blockbench で手を入れた（柄杓を口の広い六角の碗のメッシュに替え（縁の板は外した）、熔岩の舌を碗に差し込み、二つの鈴も六角の小鈴にして紐ごと風に傾けた）。正本は tools/models/x32/wild_ashsow_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ashsow_bell_staff')
    wh(m, 42)
    m.box('arm', S, [-6, 40.4, -1.2], [8, 42.4, 1.2], grad=(40.4, 4, 42.4, 6))
    m.box('cord', A, [5.6, 37.6, -0.3], [6.2, 40.4, 0.3], 3)
    m.box('ladle', A, [3.4, 33, -2.6], [8.4, 37.6, 2.6], grad=(33, 1, 37.6, 3))
    m.box('ladle_lip', S, [3.0, 32.4, -3.0], [8.8, 33.4, 3.0], 5)
    m.box('tongue_glow', G, [5.3, 30.6, -0.6], [6.5, 32.6, 0.6], 4)
    for k, x in enumerate((-5.2, -3.0)):
        m.box(f'cord_b{k}', A, [x - 0.2, 38, -0.2], [x + 0.2, 40.4, 0.2], 3)
        m.box(f'bell_{k}', S, [x - 0.9, 36.2, -0.9], [x + 0.9, 38, 0.9], 6)
    m.box('cap_glow', G, [-0.8, 42.4, -0.8], [0.8, 43.6, 0.8], 4)
    return m


# ---------------------------------------------------------------- 大剣

def wild_fortress_greatsword():
    """（エピック、2026-11-21 に作り直し・案 A）閂の剣: 厚く四角い門の閂の刃。鋲が二列に並び、先は斜めに断ち切り。鍔は左右へ張る門の蝶番。熔けた鋲二つが光り、断ち口は熾り色
    ※ 2026-11-21 に Blockbench で手を入れた（断ち口を斜めのくさびのメッシュに替えて切り口の面を熾り色に塗り、閂の面に焦げと木目を描いた）。正本は tools/models/x32/wild_fortress_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_fortress_greatsword')
    whilt(m, 8, 1.6)
    m.box('hinge', S, [-7, 7.6, -2.2], [7, 10.4, 2.2], grad=(7.6, 3, 10.4, 6))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'pin_{n}', A, [sd * 6.4 - 1.0, 6.8, -1.0], [sd * 6.4 + 1.0, 11.2, 1.0], 3)
    m.box('bar', S, [-3.6, 10.4, -1.6], [3.6, 44, 1.6], grad=(10.4, 4, 44, 7))
    m.seg('cut', S, (-3.6, 44), (3.6, 48), 2.0, 3.2, 6, 8)
    for k, y in enumerate((14, 20, 26, 32, 38)):
        for sd, n in ((-1, 'l'), (1, 'r')):
            mat, nm = (G, f'rivet_{n}{k}_glow') if (k, sd) in ((1, 1), (3, -1)) else (A, f'rivet_{n}{k}')
            m.box(nm, mat, [sd * 2.0 - 0.6, y, -1.9], [sd * 2.0 + 0.6, y + 1.2, 1.9], 4)
    return m


def wild_lavajaw_greatsword():
    """（ヒロイック、2026-11-21 に作り直し・案 A）熔け顎: 刃の両縁がぎざぎざの顎の歯。刃の中央に喉の割れ目が縦に走り熔岩が流れる。鍔は顎の蝶番の骨。先は牙
    ※ 2026-11-21 に Blockbench で手を入れた（先の牙を四角錐のメッシュにして骨の色で塗り分け、刃の両面に焦げと熾りの点を描いた）。正本は tools/models/x32/wild_lavajaw_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_lavajaw_greatsword')
    whilt(m, 8, 1.6)
    m.box('hinge', A, [-6.4, 7.6, -2.2], [6.4, 10.6, 2.2], grad=(7.6, 2, 10.6, 4))
    m.box('blade', S, [-3.8, 10.6, -1.2], [3.8, 46, 1.2], grad=(10.6, 3, 46, 6))
    for k in range(6):
        y = 13 + k * 5.6
        for sd, n in ((-1, 'l'), (1, 'r')):
            m.seg(f'tooth_{n}{k}', A, (sd * 3.6, y), (sd * 6.2, y + 2.2), 2.4, 1.6, 3, 5, tip=True)
    crack(m, 'throat_glow', [(0, 13), (0.6, 22), (-0.6, 31), (0.4, 40)], 1.0, 2.6)
    m.seg('tip', A, (0, 45.6), (0, 53), 6.2, 1.8, 3, 5, tip=True)
    return m


# ---------------------------------------------------------------- 爪・牙

def wild_cinder_claw():
    """（エピック、2026-11-21 に作り直し・案 A）双爪: 籠手の甲から、二枚の湾刀状の爪が平行に上へ。根元の横棒と爪の背寄りに熔岩の筋が赤く走る
    ※ 2026-11-21 に Blockbench で手を入れた（角材を継いだ爪を、内の刃が薄く背が厚い一枚の湾刀のメッシュに替え、熔岩の筋も両方の爪の背寄りに沿う細い帯にして表裏に出した）。正本は tools/models/x32/wild_cinder_claw.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_cinder_claw')
    whilt(m, 4, 1.4)
    m.box('gauntlet', S, [-3.2, 3.4, -2.4], [3.2, 9, 2.4], grad=(3.4, 3, 9, 6))
    m.box('bar_glow', G, [-3.0, 8.6, -0.8], [3.0, 9.8, 0.8], 4)
    for k, x in enumerate((-1.6, 1.6)):
        m.chain(f'claw_{k}', S, [(x, 9), (x + 0.6, 16), (x - 0.4, 22), (x - 2.4, 27)], [2.4, 2.0, 1.4], 0.9, 6, 8, tip=True)
    crack(m, 'edge_glow', [(2.4, 11), (2.8, 17), (2.0, 22)], 0.5, 1.1)
    return m


def wild_venom_fang():
    """（ヒロイック、2026-11-21 に作り直し・案 A）大蜥蜴の毒牙: 大きく反った毒牙を灰の布で柄に括る。牙の内側に毒の溝が走り熔岩色、根元に毒の雫。根元の左右に蜥蜴の鱗の飾り板
    ※ 2026-11-21 に Blockbench で手を入れた（角材を継いだ牙を根元が太く先へ細る一枚の反った帯のメッシュに替え、毒の溝も牙の内側に沿う帯にして表裏に出し、象牙のぼかしと筋を描いた。毒の雫は括り布の下に吊るした）。正本は tools/models/x32/wild_venom_fang.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_venom_fang')
    whilt(m, 4, 1.4)
    m.box('bind', A, [-2.6, 4, -2.2], [2.6, 7.4, 2.2], grad=(4, 1, 7.4, 3))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'scale_{n}', S, (sd * 2.4, 6), (sd * 6.4, 9.4), 3.4, 0.8, 4, 6, tip=True)
    m.chain('fang', A, [(0, 7), (1.6, 15), (1.4, 23), (-1.2, 30), (-5, 34)], [4.8, 3.8, 2.6, 1.4], 2.2, 3, 5, tip=True)
    crack(m, 'groove_glow', [(-0.8, 9), (-0.2, 16), (-0.6, 23), (-2.6, 28)], 0.7, 2.4)
    m.box('drop_glow', G, [-2.6, 5.8, -1.0], [-1.4, 7.4, 1.0], 4)
    return m


ALL = [wild_ashhook_bow, wild_crater_longbow, wild_ashrite_maul, wild_bellows_hammer, wild_emberbug_staff,
       wild_ashsow_bell_staff, wild_fortress_greatsword, wild_lavajaw_greatsword, wild_cinder_claw, wild_venom_fang]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
