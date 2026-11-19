# -*- coding: utf-8 -*-
"""野・丘（look5・個性版）。岩と苔の丘で拾う 5 品（稀）。
色: 苔むした岩と古木（loam 緑がかった焦げ茶の 1 系統）、差し色は獣の骨と牙（tusk 象牙）、光は苔の胞子（spore 淡い青緑）を品ごとに一か所。
丘の獣（猪・岩殻の甲虫・骨鳥）から取った素材を、紐と樹脂で縛った手作りの道具。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'loam': ['#14160e', '#1e2214', '#2a2e1c', '#383c26', '#484c30', '#5a5e3c', '#6e724a', '#868a5c', '#a2a474', '#c2c294'],
    'tusk': ['#4a4232', '#766a50', '#a29474', '#c8bc9a', '#e6dcc0', '#faf4e4'],
    'spore': ['#0e5244', '#16806a', '#28ac90', '#5ad4b6', '#a0f0da', '#e4fff6'],
})
L, T, S = 'loam', 'tusk', 'spore'


def M(name):
    return Model(name, accent=T, glowmat=S)


def bound_grip(m, y0, y1, r=1.5):
    """樹脂で固めた古木の握り。上下を骨の輪で締める"""
    m.box('grip', L, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('bone_ring_d', T, [-r - 0.35, y0 - 1, -r - 0.35], [r + 0.35, y0, r + 0.35], 3)
    m.box('bone_ring_u', T, [-r - 0.35, y1, -r - 0.35], [r + 0.35, y1 + 1, r + 0.35], 4)


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


def wild_bonebird_totem():
    """骨鳥の祈り柱: 古木の柱の上に、骨で組んだ鳥が翼を広げて止まる。柱には三段の刻み目と骨の輪、
    鳥の胸（くちばしの下）に胞子の灯が一つ"""
    m = M('wild_bonebird_totem')
    bound_grip(m, -14, 2, 1.6)
    m.box('pillar', L, [-1.75, 3, -1.75], [1.75, 30, 1.75], grad=(3, 3, 30, 5))
    for k, y in enumerate((9, 16, 23)):
        m.box(f'notch_{k}', L, [-2, y, -2], [2, y + 1, 2], 2)
        m.box(f'ring_{k}', T, [-2.1, y + 1, -2.1], [2.1, y + 1.75, 2.1], 4)
    m.box('perch', L, [-3, 30, -2], [3, 31.5, 2], 4)
    m.box('bird_body', T, [-1.75, 31.5, -2.25], [1.75, 36, 2.25], grad=(31.5, 3, 36, 5))
    m.box('bird_head', T, [-1.25, 36, -1.5], [1.25, 38.5, 1.5], 5)
    m.seg('beak', L, (1.25, 37.25), (3.75, 36.5), 1.25, 0.6, 5, 6, tip=True)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'wing_{n}', T, [(sd * 1.5, 34.5), (sd * 5, 37), (sd * 8.5, 38.5)], [2, 1.5], 0.6, 3, 5, tip=True)
        for k, (x, y) in enumerate(((3.5, 35.75), (6, 37.25))):
            m.seg(f'quill_{n}{k}', T, (sd * x, y), (sd * (x + 0.5), y - 3), 1, 0.4, 3, 4, tip=True)
    m.seg('tail', T, (-1.25, 32), (-3.5, 29.5), 1.5, 0.5, 3, 4, tip=True)
    m.box('heart_glow', S, [1.75, 33.5, -0.75], [2.25, 35, 0.75], 4)
    return m


def wild_bonespike_bow():
    """骨棘の弓: 古木の弓腕の背（外側）に、骨の棘が三本ずつ外へ突き出る。先端は骨の鉤で弦を受ける。
    握りの上の樹脂の塊に胞子が一粒光る"""
    m = M('wild_bonespike_bow')
    m.box('grip', L, [10.5, 10, -1.5], [13.5, 20, 1.5], grad=(10, 2, 20, 4), wrap=(10, 20))
    m.box('resin_glow', S, [13.4, 19.25, -0.75], [14.25, 20.75, 0.75], 4)
    arm = [(12, 19.5), (10.75, 27), (8.75, 34), (6.25, 39.5), (4, 43)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', L, [mirror(p, sign) for p in arm], [3, 2.75, 2.25, 2], 1.25, 4, 7, tip=False)
        for k, (x, y) in enumerate(((11.5, 26), (9.75, 32), (7.5, 37.5))):
            m.seg(f'spike_{n}{k}', T, mirror((x, y), sign), mirror((x + 3.25, y + 1.5), sign), 1.5, 0.6, 3, 5, tip=True)
        m.chain(f'tip_{n}', T, [mirror(p, sign) for p in ((4, 43), (2.75, 45), (3.75, 46.5))], [1.5, 1.25], 0.8, 3, 5, tip=True)
    strings(m, (3, 44.5), (3, -14.5))
    for sign, n in ((1, 'u'), (-1, 'd')):
        for zs, zn in ((1, 'f'), (-1, 'b')):
            q = mirror((9.75, 31), sign)
            z0 = 1.25 if zs > 0 else -1.25
            for j, (w, l) in enumerate(((1.2, 1.75), (0.8, 1.5))):
                za = z0 + zs * j * 1.75
                m.box(f'zspike_{n}{zn}{j}', T, [q[0] - w / 2, q[1] - w / 2, min(za, za + zs * l)], [q[0] + w / 2, q[1] + w / 2, max(za, za + zs * l)], 3 + j)
    return m


def wild_moss_staff():
    """苔灯りの杖: ねじれた古木の杖。先が二股に裂け、その間に苔の玉が挟まって胞子が光る。
    杖の上半分に苔が垂れ下がり、根元に小さな茸が二つ"""
    m = M('wild_moss_staff')
    bound_grip(m, -14, 2, 1.3)
    m.chain('shaft', L, [(0, 2), (0.75, 12), (-0.5, 22), (0.5, 31)], [2.5, 2.5, 2.25], 1.2, 3, 6, tip=False)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'fork_{n}', L, [(0.5, 30.5), (sd * 2.5 + 0.5, 34), (sd * 2 + 0.5, 38.5)], [2, 1.5], 1, 5, 7, tip=True)
    m.box('moss_ball', L, [-1.5, 33, -1.75], [2.5, 36.5, 1.75], grad=(33, 6, 36.5, 8))
    m.box('spore_glow', S, [-0.5, 34, -1.9], [1.5, 35.5, 1.9], 4)
    for k, (x, y, h) in enumerate(((1.4, 26, 4), (-1.6, 22, 3), (1.1, 17, 2.5))):
        m.box(f'moss_drip_{k}', L, [x - 0.6, y - h, -0.6], [x + 0.6, y, 0.6], grad=(y - h, 6, y, 8))
    for k, (x, y) in enumerate(((1.6, 4.5), (-1.5, 6.5))):
        m.box(f'shroom_stem_{k}', T, [x - 0.3, y, -0.3], [x + 0.3, y + 1.25, 0.3], 4)
        m.box(f'shroom_cap_{k}', T, [x - 1, y + 1.25, -1], [x + 1, y + 2, 1], 3)
    return m


def wild_shell_claw():
    """岩殻の爪: 岩殻の甲虫の前脚を籠手にした爪。握りの上に甲虫の殻の籠、そこから三本の鉤爪が前（上）へ反る。
    殻の継ぎ目に胞子の光が一筋"""
    m = M('wild_shell_claw')
    bound_grip(m, -7, 3, 1.4)
    m.box('cuff', L, [-2.75, 3, -2.5], [2.75, 6, 2.5], grad=(3, 4, 6, 6))
    m.box('carapace', L, [-3.25, 6, -2.75], [3.75, 11, 2.75], grad=(6, 5, 11, 7))
    m.box('carapace_seam', S, [-3.4, 8.25, -2.9], [3.9, 8.75, 2.9], 3)
    for k, (x, z) in enumerate(((-2, 0), (0.5, -1), (2.75, 0.75))):
        m.chain(f'talon_{k}', T, [(x, 11), (x + 0.75, 15), (x + 2.25, 18.5 - k * 0.5), (x + 3.75, 20 - k)], [1.75, 1.5, 1.1], 0.8, 3, 6, tip=True, zc=z)
    m.seg('spur', T, (-3, 9), (-5.5, 11), 1.5, 0.6, 3, 5, tip=True)
    return m


def wild_tusk_axe():
    """猪牙の斧: 古木の柄の先に、大猪の牙を二本背中合わせに縛って刃にした斧。+x に反った大きな牙、
    -x に小さな牙。縛った紐の結び目に胞子の光、柄頭に猪の蹄"""
    m = M('wild_tusk_axe')
    bound_grip(m, -6, 6)
    m.box('hoof', L, [-1.9, -9, -1.9], [1.9, -7, 1.9], 3)
    m.box('hoof_split', T, [-0.25, -9, -2], [0.25, -7.5, 2], 2)
    m.box('shaft', L, [-1.3, 7, -1.3], [1.3, 25, 1.3], grad=(7, 4, 25, 6))
    m.box('lashing', T, [-1.7, 18, -1.7], [1.7, 23, 1.7], grad=(18, 2, 23, 4), wrap=(18, 23))
    m.chain('tusk_big', T, [(1, 20.5), (5, 19), (8.5, 20.5), (10.5, 24.5), (10, 28.5)], [4, 4, 3.25, 2.25], 1.25, 3, 6, tip=True)
    m.chain('tusk_edge', T, [(5.5, 17.25), (9.5, 19), (11.75, 23.5)], [1, 1], 0.9, 5, 6, tip=False)
    m.chain('tusk_small', T, [(-1, 21.5), (-4, 21), (-6, 23)], [2.5, 1.75], 1, 3, 5, tip=True)
    m.box('knot_glow', S, [-1.9, 22, -0.5], [-1.5, 23, 0.5], 4)
    m.box('cap', L, [-1.6, 25, -1.6], [1.6, 26.5, 1.6], 5)
    return m


ALL = [wild_bonebird_totem, wild_bonespike_bow, wild_moss_staff, wild_shell_claw, wild_tusk_axe]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
