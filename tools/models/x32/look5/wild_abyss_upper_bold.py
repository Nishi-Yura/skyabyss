# -*- coding: utf-8 -*-
"""野・深淵上（look5・個性版）。深淵の上層（塩の坑道・綱の降り口・骨の吊り場）で拾う 24 品（叙）。
色: 湿った頁岩と坑道の鉄（shale 青緑がかった暗灰の 1 系統）、差し色は塩の結晶（salt 灰白）、光は坑夫の灯（lamp 琥珀）を品ごとに一か所。
降りる者の道具（綱・灯・鶴嘴）と、塩と骨。灯の光は品ごとに場所を変える。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'shale': ['#0c1414', '#141e1e', '#1c2a2a', '#26383a', '#30464a', '#3c565a', '#4a686c', '#5c7c80', '#729296', '#8eaaae'],
    'salt': ['#6a6a64', '#8e8e86', '#b0b0a8', '#cecec6', '#e6e6e0', '#f8f8f4'],
    'lamp': ['#6a3008', '#a4520c', '#d87e1a', '#f6a834', '#ffd27a', '#fff2c8'],
})
H, T, L = 'shale', 'salt', 'lamp'


def M(name):
    return Model(name, accent=T, glowmat=L)


def tar_grip(m, y0, y1, r=1.5):
    """瀝青を染ませた綱を巻いた握り。上下は塩の吹いた鉄の輪"""
    m.box('grip', H, [-r, y0, -r], [r, y1, r], grad=(y0, 2, y1, 4), wrap=(y0, y1))
    m.box('crust_d', T, [-r - 0.35, y0 - 1, -r - 0.35], [r + 0.35, y0, r + 0.35], 2)
    m.box('crust_u', T, [-r - 0.35, y1, -r - 0.35], [r + 0.35, y1 + 1, r + 0.35], 3)


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


def bow_grip(m, y0=10, y1=20):
    m.box('grip', H, [10.5, y0, -1.5], [13.5, y1, 1.5], grad=(y0, 2, y1, 4), wrap=(y0, y1))


# ================================================================ 弓

def wild_bonestring_bow():
    """背骨の弓: 弓腕が獣の背骨の節（椎骨）を連ねたもの。節ごとに小さな棘が背へ立ち、
    握りの真上の一番大きな椎骨の穴の奥に灯が点る"""
    m = M('wild_bonestring_bow')
    bow_grip(m)
    m.box('core_vert', T, [10, 19.5, -2], [14, 22, 2], 4)
    m.box('core_glow', L, [13.9, 20.25, -0.75], [14.3, 21.25, 0.75], 4)
    pts = [(12, 22), (11.25, 27), (10, 32), (8.25, 36.5), (6.25, 40.5), (4.25, 43.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        for k in range(len(pts) - 1):
            a, b = mirror(pts[k], sign), mirror(pts[k + 1], sign)
            m.seg(f'vert_{n}{k}', T, a, b, 2.5 - k * 0.2, 1.6 - k * 0.15, 3, 4, over=0.1)
            mx = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
            m.seg(f'spine_{n}{k}', T, mx, (mx[0] + 1.75, mx[1] + sign * 0.5), 0.9, 0.5, 4, 5, tip=True)
        for k, p in enumerate(pts[1:5]):
            q = mirror(p, sign)
            m.box(f'disc_{n}{k}', H, [q[0] - 1.4, q[1] - 0.35, -1.1], [q[0] + 1.4, q[1] + 0.35, 1.1], 3)
    strings(m, (3.5, 44), (3.5, -14))
    return m


def wild_lantern_bow():
    """提灯の弓（エピック）: 坑道の鉄の太い弓。上の弓腕の内側（弦の側）から鎖で角灯が下がり、弓の懐で灯がともる。
    握りの前に塩の結晶の房が咲き、弓腕は節ごとに塩の帯で締め、背に結晶の棘が二本ずつ。
    弦には塩の結晶の玉が上下に一つずつ通り、下の弓腕の先は塩の結晶の房"""
    m = M('wild_lantern_bow')
    m.box('grip', H, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for k, y in enumerate((8.25, 20.5)):
        m.box(f'riser_{k}', T, [9.6, y, -2.3], [14.4, y + 1.25, 2.3], 3 + k)
    diamond(m, 'guard_salt', T, (15.25, 15), 4.25, 2.2, 3, 5)
    diamond(m, 'guard_glow', L, (15.25, 15), 2.25, 2.45, 3, 5)
    diamond(m, 'guard_salt_s', T, (16.75, 18.25), 2.5, 1.7, 4, 5)
    diamond(m, 'guard_salt_t', T, (16.5, 11.75), 2.25, 1.6, 4, 5)
    arm = [(12, 20), (11.5, 27), (10, 34), (7.75, 39.5), (5.5, 43)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'arm_{n}', H, [P(q) for q in arm], [3.75, 3.25, 2.75, 2.25], 1.5, 5, 8, tip=False)
        for k in (1, 2, 3):
            (x0, y0), (x1, y1) = arm[k - 1], arm[k + 1]
            L0 = math.hypot(x1 - x0, y1 - y0)
            px, py = -(y1 - y0) / L0, (x1 - x0) / L0
            c = arm[k]
            hw = (2.4, 2.1, 1.8)[k - 1]
            m.seg(f'band_{n}{k}', T, P((c[0] - px * hw, c[1] - py * hw)), P((c[0] + px * hw, c[1] + py * hw)), 1.25, 1.75, 3, 4, over=0)
        for k, (x, y, ln) in enumerate(((13.25, 25, 3.5), (11.75, 31.5, 3.25))):
            q = P((x, y))
            m.seg(f'spike_{n}{k}', T, q, (q[0] + ln, q[1] + sign * ln * 0.55), 1.6, 1.1, 4, 5, tip=True)
    m.chain('tip_u', H, [(5.5, 43), (4.5, 44.75)], [1.75], 1.2, 7, 8, tip=False)
    diamond(m, 'crown_u', T, (5, 45.25), 2.25, 1.5, 4, 5)
    # 懐の角灯（上の弓腕の腹から鎖で下がる）
    m.box('hanger', H, [6.8, 31.25, -0.3], [10.25, 31.85, 0.3], 6)
    m.box('chain', H, [7.05, 30, -0.25], [7.55, 31.5, 0.25], 5)
    m.box('cage_roof', T, [5.4, 29.25, -1.9], [9.2, 30, 1.9], 4)
    m.box('cage_peak', T, [6.3, 30, -1.1], [8.3, 30.5, 1.1], 5)
    m.box('cage', H, [5.75, 25.5, -1.5], [8.85, 29.25, 1.5], 6)
    m.box('light_glow', L, [6.1, 25.9, -1.75], [8.5, 28.9, 1.75], 4)
    m.box('cage_floor', H, [5.4, 24.75, -1.9], [9.2, 25.5, 1.9], 7)
    m.seg('cage_drip', T, (7.3, 24.75), (7.3, 23), 1, 0.8, 4, 5, tip=True)
    diamond(m, 'crust_d', T, (5.75, -13), 3.25, 1.8, 3, 5)
    diamond(m, 'crust_d2', T, (4.25, -11), 2, 1.4, 4, 5)
    m.seg('crust_spike', T, (6.5, -14), (8.25, -18.25), 1.6, 1.1, 4, 5, tip=True)
    top, bot = (4.5, 43.25), (4.5, -13.25)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((35.5, -4.5)):
            c = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            diamond(m, f'string_{key}_bead{k}', T, c, 1.9, 1.1, 4, 5)
    return m


def wild_saltcrow_bow():
    """塩鴉の弓（レア）: 鴉の黒い翼の太い弓腕。風切羽は弦の側（腹）へ垂れて三枚ずつ重なり、羽根先が塩で白い。
    握りの前に塩の白い鴉の頭蓋（嘴は前へ）、弓腕の先は塩の結晶の殻で、上の殻だけ灯を宿す"""
    m = M('wild_saltcrow_bow')
    m.box('grip', H, [10.25, 9.5, -1.75], [13.75, 20.5, 1.75], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    m.box('skull', T, [13.5, 16.25, -1.6], [16.25, 19.75, 1.6], 4)
    m.box('eye', H, [15.25, 18, -1.7], [16, 18.75, 1.7], 1)
    m.seg('beak', T, (16.25, 18.75), (19.5, 17), 1.6, 1, 4, 5, tip=True)
    m.seg('jaw', T, (16.25, 16.75), (18.25, 16.25), 0.9, 0.7, 3, 4, tip=True)
    arm = [(12, 19.5), (10.75, 27), (9, 33.5), (6.75, 39), (4.5, 42.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'wing_{n}', H, [P(q) for q in arm], [3.75, 3.25, 2.75, 2.25], 1.4, 2, 4, tip=False)
        for k, (x, y, ln) in enumerate(((10, 27.5, 4.5), (8.5, 32.5, 4.75), (6.75, 37, 4))):
            a, b = P((x, y)), P((x - ln * 0.55, y - ln * 0.8))
            m.seg(f'pinion_{n}{k}', H, a, b, 2, 0.6, 2, 4, over=0.3, zc=0.2)
            m.seg(f'pinion_tip_{n}{k}', T, b, P((x - ln * 0.7, y - ln * 1.05)), 1.6, 0.55, 4, 5, tip=True, zc=0.2)
        tip = P((4.5, 42.5))
        mat = L if sign > 0 else T
        diamond(m, f'crust_{n}' + ('_glow' if sign > 0 else ''), mat, (tip[0] - 0.25, tip[1] + sign * 0.75), 3, 1.6, 4, 5)
        diamond(m, f'crust_s_{n}', T, P((6.25, 41.25)), 1.75, 1.3, 4, 5)
    strings(m, (3.75, 42.75), (3.75, -12.75))
    return m


def wild_hanged_longbow():
    """吊り人の大弓: 背丈ほどの長弓。上の弓腕に輪にした綱が掛かってだらりと下がり、
    綱の端に結ばれた小さな札が灯色に光る。弓腕は継ぎ目ごとに塩の白"""
    m = M('wild_hanged_longbow')
    m.box('grip', H, [10.25, 9, -1.75], [13.75, 21, 1.75], grad=(9, 2, 21, 4), wrap=(9, 21))
    arm = [(12, 20.5), (11.25, 30), (9.5, 39), (7, 46.5), (4.25, 52)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        m.chain(f'arm_{n}', H, [mirror(p, sign) for p in arm], [3, 2.5, 2.25, 1.75], 1.3, 4, 7, tip=False)
        for k, p in enumerate(arm[1:4]):
            q = mirror(p, sign)
            m.box(f'joint_{n}{k}', T, [q[0] - 1.6, q[1] - 0.4, -1.45], [q[0] + 1.6, q[1] + 0.4, 1.45], 3)
        m.seg(f'nock_{n}', T, mirror((4.25, 52), sign), mirror((2.25, 54.5), sign), 1.5, 1, 3, 5, tip=True)
    m.box('noose_top', T, [9.75, 41.5, -1.6], [13, 42.5, 1.6], 3)
    m.chain('noose', T, [(12.5, 42), (14, 38), (14.25, 33), (13.5, 29.5)], [0.8, 0.8, 0.8], 0.8, 3, 4, tip=False)
    m.box('tag_glow', L, [12.75, 27, -0.4], [14.5, 29.5, 0.4], 4)
    strings(m, (2.5, 54.25), (2.5, -24.25))
    return m


# ================================================================ 剣・大剣

def wild_delver_sword():
    """降り手の剣: 坑道へ降りる者の実用の剣。真っ直ぐな片刃、峰（-x）は厚い。鍔の +x 端に灯を吊る鉤があり、
    そこに小さな灯が下がって光る。柄は綱巻き"""
    m = M('wild_delver_sword')
    tar_grip(m, -6, 6)
    m.box('pommel', H, [-1.75, -8.75, -1.75], [1.75, -7, 1.75], 5)
    m.box('guard', H, [-3.5, 7, -1.75], [3.5, 8.75, 1.75], 5)
    m.seg('lamp_hook', H, (3.5, 8), (5.5, 6.5), 0.9, 0.9, 6, 7)
    m.box('lamp_glow', L, [4.75, 3.75, -0.85], [6.5, 6.25, 0.85], grad=(3.75, 3, 6.25, 5))
    m.box('lamp_cap', H, [4.5, 6.25, -1.1], [6.75, 7, 1.1], 6)
    m.box('spine', H, [-2.25, 8.75, -1.1], [-0.75, 27, 1.1], grad=(8.75, 3, 27, 5))
    m.box('blade', H, [-0.75, 8.75, -0.8], [2.25, 27, 0.8], grad=(8.75, 5, 27, 8))
    m.seg('tip', H, (-2.25, 27), (2.25, 31), 2, 0.9, 7, 9, over=0)
    m.box('tip_cap', H, [-2.25, 27, -0.85], [1, 29.5, 0.85], 7)
    m.box('salt_line', T, [1.6, 10, -0.85], [2.25, 25, 0.85], 3)
    return m


def wild_inverted_greatsword():
    """逆さ剣: 根元が細く、先へ行くほど広がる逆さの大剣。先端は平たく四角に断ち切られ、
    その断面の縁が灯色に光る。鍔の代わりに塩の結晶の塊"""
    m = M('wild_inverted_greatsword')
    tar_grip(m, -8, 8, 1.6)
    m.box('pommel', T, [-2, -11, -2], [2, -9, 2], 4)
    diamond(m, 'guard_salt', T, (0, 10.5), 4.5, 3.5, 3, 5)
    for k, (y, w) in enumerate(((11, 2), (17, 3), (23, 4), (29, 5), (35, 6))):
        y1 = (17, 23, 29, 35, 41)[k]
        m.box(f'blade_{k}', H, [-w / 2 - 0.25, y, -1.1], [w / 2 + 0.25, y1, 1.1], grad=(y, 4 + k * 0.5, y1, 5 + k * 0.5))
    m.box('flat_tip', H, [-3.5, 41, -1.2], [3.5, 42.5, 1.2], 8)
    m.box('tip_glow', L, [-3.25, 42.5, -0.9], [3.25, 43, 0.9], 4)
    m.box('fuller', H, [-0.4, 13, -1.25], [0.4, 38, 1.25], 2)
    return m


def wild_saltcrystal_greatsword():
    """塩晶の大剣: 刃が塩の結晶の柱を束ねたもの。中央の長い柱を、左右の短い柱が斜めに挟む。
    中央の柱の芯に灯が透ける"""
    m = M('wild_saltcrystal_greatsword')
    tar_grip(m, -8, 8, 1.6)
    m.box('pommel', H, [-2, -11, -2], [2, -9, 2], 5)
    m.box('guard', H, [-4.5, 9, -2], [4.5, 11, 2], grad=(9, 3, 11, 5))
    m.box('main', T, [-2, 11, -1.4], [2, 40, 1.4], grad=(11, 2, 40, 5))
    m.seg('main_tip', T, (0, 40), (0, 45), 4, 2.8, 4, 5, tip=True)
    m.box('core_glow', L, [-0.5, 15, -1.5], [0.5, 34, 1.5], grad=(15, 2, 34, 4))
    for k, (x, y0, y1, ang) in enumerate(((-2.5, 12, 24, -1), (2.5, 15, 28, 1), (-2.25, 26, 34, -1), (2.25, 30, 37, 1))):
        m.seg(f'shard_{k}', T, (x, y0), (x + ang * 2, y1), 2.5, 2, 3, 5, tip=True)
    return m


# ================================================================ 短剣

def wild_bone_needle_dagger():
    """骨針の短剣: 大きな骨を削った長い針。根元に糸通しの穴（縦長の輪）が開き、穴の中に灯色の糸の結び目。
    鍔はなく、骨の節がそのまま握りの上にふくらむ"""
    m = M('wild_bone_needle_dagger')
    tar_grip(m, -4, 4, 1.25)
    m.box('knuckle', T, [-1.6, 5, -1.4], [1.6, 7, 1.4], grad=(5, 3, 7, 4))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'eye_{n}', T, [sd * 1.25 - 0.5, 7, -0.8], [sd * 1.25 + 0.5, 11, 0.8], 4)
    m.box('eye_top', T, [-1.75, 11, -0.8], [1.75, 12, 0.8], 4)
    m.box('thread_glow', L, [-0.6, 8.25, -0.5], [0.6, 9.75, 0.5], 4)
    m.seg('needle', T, (0, 12), (0, 22.5), 2, 1.3, 4, 5, tip=True)
    return m


def wild_ropecut_knife():
    """綱切りの小刀: 刃の背から鉤が前へ曲がり、鉤の内側が綱を切る刃になる。鉤の内刃の奥が灯色に焼けている。
    柄頭には綱を通す輪"""
    m = M('wild_ropecut_knife')
    tar_grip(m, -4, 4, 1.3)
    m.box('ring', H, [-1.25, -7, -0.4], [1.25, -5, 0.4], 5)
    m.box('bolster', H, [-1.75, 5, -1.25], [1.75, 6.5, 1.25], 5)
    m.box('blade', H, [-1, 6.5, -0.7], [1.5, 15, 0.7], grad=(6.5, 5, 15, 8))
    m.chain('hook', H, [(0.25, 15), (-1, 17.5), (-3, 18), (-4, 16)], [2.25, 1.75, 1.25], 0.7, 7, 9, tip=True)
    m.box('inner_glow', L, [-2.75, 15.6, -0.75], [-1.25, 16.4, 0.75], 4)
    return m


def wild_snuffer_dagger():
    """灯消しの短剣: 細い刺突の刃の根元から、横へ蝋燭消し（小さな円錐の帽子）が腕で突き出る。
    帽子の中にまだ残り火が赤く見える"""
    m = M('wild_snuffer_dagger')
    tar_grip(m, -4, 4, 1.25)
    m.box('guard', H, [-2.25, 4.75, -1.1], [2.25, 6, 1.1], 5)
    m.seg('blade', H, (0, 6), (0, 19.5), 2, 1, 6, 9, tip=True)
    m.seg('arm', H, (2, 5.5), (4.5, 8), 0.8, 0.8, 6, 7)
    m.box('cone_rim', H, [3.25, 8, -1.25], [5.75, 8.75, 1.25], 6)
    m.box('cone_mid', H, [3.6, 8.75, -0.9], [5.4, 10, 0.9], 7)
    m.seg('cone_tip', H, (4.5, 10), (4.5, 11.5), 1.25, 1.25, 8, 9, tip=True)
    m.box('ember_glow', L, [3.75, 7.6, -0.6], [5.25, 8.1, 0.6], 3)
    return m


# ================================================================ 爪

def wild_ceiling_claw():
    """天井這いの爪: 坑道の天井に掛かるための、下向きに大きく曲がる三本の鉤爪。籠手の甲は塩の殻で、
    甲の中央の継ぎ目から灯が漏れる"""
    m = M('wild_ceiling_claw')
    tar_grip(m, -7, 3, 1.4)
    m.box('gauntlet', H, [-2.75, 3, -2.5], [2.75, 8.5, 2.5], grad=(3, 3, 8.5, 5))
    m.box('shell_l', T, [-3.1, 4, -2.75], [-0.25, 8, 2.75], 3)
    m.box('shell_r', T, [0.25, 4, -2.75], [3.1, 8, 2.75], 3)
    m.box('seam_glow', L, [-0.25, 4.5, -2.8], [0.25, 7.5, 2.8], 4)
    for k, x in enumerate((-1.75, 0, 1.75)):
        m.chain(f'hook_{k}', H, [(x, 8.5), (x + 0.5, 13), (x + 2.5, 16.5), (x + 4.75, 16.25), (x + 5.25, 13.5)], [1.5, 1.25, 1, 0.75], 0.7, 5, 8, tip=True)
    return m


# ================================================================ 鎚・斧

def wild_pick_hammer():
    """坑夫の鶴嘴鎚: 頭の +x は四角い鎚、-x は長く下へ反る鶴嘴。鶴嘴の先近くに塩の結晶が噛み込み、
    その結晶の一粒だけ灯色"""
    m = M('wild_pick_hammer')
    tar_grip(m, -6, 6)
    m.box('shaft', H, [-1.2, 7, -1.2], [1.2, 22, 1.2], grad=(7, 3, 22, 5))
    m.box('eye', H, [-2, 20, -2], [2, 25, 2], grad=(20, 4, 25, 6))
    m.box('hammer', H, [2, 20.5, -1.75], [5.5, 24.5, 1.75], grad=(20.5, 5, 24.5, 7))
    m.box('face', H, [5.5, 20.25, -2], [6.25, 24.75, 2], 8)
    m.chain('pick', H, [(-2, 23), (-5.5, 23), (-8.5, 21.5), (-10.5, 18.5)], [2.5, 2, 1.5], 1.4, 6, 8, tip=True)
    diamond(m, 'salt_bite', T, (-8, 23), 1.75, 1.6, 4, 5)
    diamond(m, 'salt_glow', L, (-6.5, 23.75), 1.25, 1.5, 4, 5)
    return m


def wild_lampeater_hammer():
    """灯喰いの鎚: 鎚の頭が鉄格子の籠で、籠が顎のように灯を噛んでいる。籠の中の灯が光り、
    籠の上下の縁はぎざぎざの歯。打つ面は籠の外の分厚い鉄板"""
    m = M('wild_lampeater_hammer')
    tar_grip(m, -6, 6)
    m.box('shaft', H, [-1.25, 7, -1.25], [1.25, 20.5, 1.25], grad=(7, 3, 20.5, 5))
    m.box('jaw_low', H, [-3.5, 20, -2.5], [3.5, 21.25, 2.5], 5)
    m.box('jaw_high', H, [-3.5, 26, -2.5], [3.5, 27.25, 2.5], 6)
    for k, x in enumerate((-2.75, -0.9, 0.9, 2.75)):
        m.box(f'bar_{k}', H, [x - 0.3, 21.25, -2.3], [x + 0.3, 26, -1.7], 5)
        m.box(f'bar_b{k}', H, [x - 0.3, 21.25, 1.7], [x + 0.3, 26, 2.3], 5)
        m.seg(f'tooth_d{k}', T, (x, 21.25), (x, 22.5), 0.8, 0.6, 3, 4, tip=True, zc=-2)
        m.seg(f'tooth_u{k}', T, (x, 26), (x, 24.75), 0.8, 0.6, 3, 4, tip=True, zc=2)
    m.box('lamp_glow', L, [-1.5, 22, -1.25], [1.5, 25.25, 1.25], grad=(22, 3, 25.25, 5))
    m.box('plate', H, [3.5, 19.5, -2.75], [5, 27.75, 2.75], grad=(19.5, 6, 27.75, 8))
    m.box('back', H, [-5, 21.5, -1.75], [-3.5, 25.75, 1.75], 6)
    return m


def wild_blasting_axe():
    """発破の斧: 岩を割る楔形の斧。頭の背（-x）に布で巻いた発破の筒が二本縛られ、導火線の先が灯色に燃える。
    刃は厚い楔"""
    m = M('wild_blasting_axe')
    tar_grip(m, -6, 6)
    m.box('shaft', H, [-1.25, 7, -1.25], [1.25, 25, 1.25], grad=(7, 3, 25, 5))
    m.box('head', H, [-1.75, 18.5, -1.75], [2.5, 25.5, 1.75], grad=(18.5, 4, 25.5, 6))
    for k, (y, w) in enumerate(((17, 3), (18.5, 4.5), (20.5, 5.5), (22.5, 5.5), (24.5, 4.5), (26, 3))):
        m.box(f'wedge_{k}', H, [2.5, y, -1.4 + k * 0.05], [2.5 + w, y + 1.75, 1.4 - k * 0.05], 6 + (1 if k in (2, 3) else 0))
    m.box('edge', H, [7.75, 19, -0.6], [8.5, 26, 0.6], 8)
    for k, z in enumerate((-1, 1)):
        m.box(f'charge_{k}', T, [-4.75, 19, z - 1], [-2, 25, z + 0.9], grad=(19, 2, 25, 3))
    m.box('strap', H, [-5, 21.5, -2.2], [-1.75, 22.5, 2.2], 4)
    m.chain('fuse', H, [(-3.5, 25), (-4, 27), (-5.5, 28)], [0.5, 0.5], 0.5, 3, 3, tip=False)
    m.box('spark_glow', L, [-6.25, 27.5, -0.4], [-5.25, 28.5, 0.4], 4)
    return m


def wild_saltmill_maul():
    """塩臼の大鎚: 頭が石臼そのもの。二枚の丸い臼石が重なり、その間の溝に挽きかけの塩が白く溜まる。
    上の臼石の挽き穴から灯色の光"""
    m = M('wild_saltmill_maul')
    tar_grip(m, -7, 7, 1.6)
    m.box('shaft', H, [-1.3, 8, -1.3], [1.3, 21, 1.3], grad=(8, 3, 21, 5))
    for k, (y0, y1) in enumerate(((21, 25), (26, 30))):
        m.box(f'stone_{k}', H, [-6, y0, -3], [6, y1, 3], grad=(y0, 4 + k, y1, 6 + k))
        m.box(f'stone_{k}_z', H, [-4.5, y0, -4.5], [4.5, y1, 4.5], grad=(y0, 4 + k, y1, 6 + k))
    m.box('salt_groove', T, [-5.5, 25, -2.75], [5.5, 26, 2.75], grad=(25, 3, 26, 5))
    m.box('salt_groove_z', T, [-4, 25, -4], [4, 26, 4], 4)
    m.box('hole_glow', L, [-0.9, 29.6, -0.9], [0.9, 30.4, 0.9], 4)
    m.box('handle_peg', H, [4, 30, -0.6], [5.2, 32.5, 0.6], 7)
    return m


# ================================================================ 長柄

def wild_rope_halberd():
    """綱巻きの鉾: 長柄の上半分に綱が粗く巻かれ、その結び目が灯色に光る。先には斧刃（+x）と鉤（-x）と穂。
    斧刃は坑道の扉を叩き割る厚い四角"""
    m = M('wild_rope_halberd')
    m.box('grip', H, [-1.5, -16, -1.5], [1.5, 0, 1.5], grad=(-16, 2, 0, 4), wrap=(-16, 0))
    m.box('butt', T, [-1.9, -18.5, -1.9], [1.9, -16, 1.9], 3)
    m.box('haft', H, [-1.25, 0, -1.25], [1.25, 36, 1.25], grad=(0, 3, 36, 5))
    m.box('rope', T, [-1.6, 18, -1.6], [1.6, 30, 1.6], grad=(18, 1, 30, 3), wrap=(18, 30))
    m.box('knot_glow', L, [1.4, 23.5, -0.75], [2.25, 25, 0.75], 4)
    m.box('socket', H, [-1.9, 34, -1.9], [1.9, 38, 1.9], grad=(34, 4, 38, 6))
    m.box('axe', H, [1.9, 34, -0.8], [7, 41, 0.8], grad=(34, 5, 41, 7))
    m.box('axe_edge', H, [7, 33.5, -0.5], [7.75, 41.5, 0.5], 8)
    m.chain('beak', H, [(-1.9, 38), (-4.5, 38.5), (-6, 36.5)], [1.75, 1.25], 1, 5, 7, tip=True)
    m.seg('spike', H, (0, 38), (0, 48), 2.25, 1.4, 6, 8, tip=True)
    return m


# ================================================================ 杖

def wild_compass_staff():
    """羅針の杖: 杖の先に塩で白く曇った羅針盤の輪が縦に立ち、中で針が斜めを指す。針の北の先だけ灯色。
    輪の外には方角の四つの小さな突起"""
    m = M('wild_compass_staff')
    tar_grip(m, -14, 2, 1.3)
    m.box('shaft', H, [-1.1, 3, -1.1], [1.1, 29, 1.1], grad=(3, 3, 29, 5))
    m.box('mount', H, [-1.6, 28.5, -1.6], [1.6, 30, 1.6], 5)
    m.arc('ring', H, (0, 34.25), 4, 0, 360, 10, [1.1] * 10, 1.2, 6, 7)
    m.box('face', T, [-3, 31.25, -0.4], [3, 37.25, 0.4], grad=(31.25, 3, 37.25, 4))
    m.seg('needle_s', H, (0, 34.25), (-1.75, 32), 0.8, 1, 5, 6, tip=True)
    m.seg('needle_glow', L, (0, 34.25), (1.75, 36.5), 0.8, 1, 4, 5, tip=True)
    for k, (a, b) in enumerate((((0, 38.5), (0, 40)), ((4.25, 34.25), (5.75, 34.25)), ((-4.25, 34.25), (-5.75, 34.25)))):
        m.seg(f'point_{k}', H, a, b, 1.1, 0.9, 6, 7, tip=True)
    return m


def wild_bonepipe_staff():
    """骨笛の杖: 杖の先に長さの違う三本の骨の管が束ねられ、風が通ると鳴る。いちばん長い管の口の奥が灯色。
    束ねた所は綱の結び"""
    m = M('wild_bonepipe_staff')
    tar_grip(m, -14, 2, 1.3)
    m.box('shaft', H, [-1.1, 3, -1.1], [1.1, 28, 1.1], grad=(3, 3, 28, 5))
    m.box('binding', H, [-3.25, 27.5, -1.6], [3.25, 30, 1.6], grad=(27.5, 2, 30, 4), wrap=(27.5, 30))
    for k, (x, top) in enumerate(((-2, 35), (0, 39), (2, 33))):
        m.box(f'pipe_{k}', T, [x - 0.9, 26, -0.9], [x + 0.9, top, 0.9], grad=(26, 3, top, 5))
        m.box(f'pipe_lip_{k}', T, [x - 1.1, top - 0.6, -1.1], [x + 1.1, top, 1.1], 4)
        m.box(f'hole_{k}', H, [x - 0.4, top - 4, -0.95], [x + 0.4, top - 3, 0.95], 2)
    m.box('mouth_glow', L, [-0.5, 38.5, -0.5], [0.5, 39.1, 0.5], 4)
    return m


def wild_mimic_heart_staff():
    """擬き心臓の杖: 杖の先に宝箱のふりをした小さな箱の怪物。蓋がわずかに開き、縁に歯が並び、
    蓋と箱の隙間の奥で一つ目が灯色に光る。箱の角は塩の金具"""
    m = M('wild_mimic_heart_staff')
    tar_grip(m, -14, 2, 1.3)
    m.box('shaft', H, [-1.1, 3, -1.1], [1.1, 29, 1.1], grad=(3, 3, 29, 5))
    m.box('box', H, [-3.5, 29, -2.75], [3.5, 33, 2.75], grad=(29, 4, 33, 6))
    m.box('lid', H, [-3.5, 34, -2.75], [3.5, 36.5, 2.75], grad=(34, 5, 36.5, 7))
    m.box('eye_glow', L, [-0.75, 33.1, -2.5], [0.75, 33.9, -2.2], 4)
    m.box('eye_back', H, [-3.25, 33, -2.25], [3.25, 34, 2.25], 1)
    for k, x in enumerate((-2.5, -1.25, 1.25, 2.5)):
        m.seg(f'tooth_{k}', T, (x, 33), (x, 34), 0.6, 0.5, 4, 5, tip=True, zc=-2.5)
    for sd in (-1, 1):
        for k, y in enumerate((29, 34)):
            m.box(f'corner_{sd}{k}', T, [sd * 3.5 - 0.5, y, -2.95], [sd * 3.5 + 0.5, y + 2.5, 2.95], 3)
    return m


def wild_salttear_staff():
    """塩涙の杖: 杖の先が細い輪になって、その中に涙の形の塩の結晶が一粒吊られる。涙の芯が灯色に透ける。
    杖の首から短い鎖が二本垂れる"""
    m = M('wild_salttear_staff')
    tar_grip(m, -14, 2, 1.3)
    m.box('shaft', H, [-1.1, 3, -1.1], [1.1, 29, 1.1], grad=(3, 3, 29, 5))
    m.arc('hoop', H, (0, 34.5), 4.25, 0, 360, 10, [1] * 10, 1, 6, 7)
    m.box('thread', H, [-0.2, 36.75, -0.2], [0.2, 38.75, 0.2], 5)
    m.box('tear_low', T, [-1.75, 31.5, -1.4], [1.75, 34.5, 1.4], grad=(31.5, 3, 34.5, 5))
    m.seg('tear_top', T, (0, 34.5), (0, 37), 2.5, 2, 4, 5, tip=True)
    m.box('tear_glow', L, [-0.75, 32.25, -1.5], [0.75, 34.25, 1.5], 4)
    for k, x in enumerate((-1.75, 1.75)):
        m.box(f'chain_{k}', H, [x - 0.25, 25, -0.25], [x + 0.25, 29, 0.25], grad=(25, 4, 29, 6))
        m.box(f'chain_end_{k}', T, [x - 0.5, 24.25, -0.5], [x + 0.5, 25, 0.5], 4)
    return m


# ================================================================ 鈴杖・祈り柱

def wild_knotted_bell_staff():
    """結び目の鈴杖: 杖に綱がらせんに巻かれ、等間隔に大きな結び目が三つ。頭は綱の輪で、輪から小さな鈴が二つ下がる。
    いちばん上の結び目が灯色"""
    m = M('wild_knotted_bell_staff')
    tar_grip(m, -14, 2)
    m.box('shaft', H, [-1.2, 3, -1.2], [1.2, 31, 1.2], grad=(3, 3, 31, 5))
    for k, y in enumerate((10, 18, 26)):
        mat = L if k == 2 else T
        m.box(f'knot_{k}' + ('_glow' if k == 2 else ''), mat, [-1.9, y, -1.9], [1.9, y + 2, 1.9], 4 if k == 2 else 3)
    m.arc('loop', T, (0, 34), 3, 0, 360, 8, [0.9] * 8, 0.9, 3, 4)
    for k, x in enumerate((-2.75, 2.75)):
        m.box(f'cord_{k}', T, [x - 0.15, 29, -0.15], [x + 0.15, 33, 0.15], 3)
        m.box(f'bell_{k}', H, [x - 1, 27, -1], [x + 1, 29, 1], grad=(27, 5, 29, 7))
        m.box(f'bell_lip_{k}', H, [x - 1.3, 26.5, -1.3], [x + 1.3, 27.1, 1.3], 6)
    return m


def wild_lullaby_bell_staff():
    """子守の鈴杖: 杖の先に三日月形の揺り籠が横向きに載り、籠の両端に小さな鈴。
    籠の中に灯色の小さな光（眠る子の灯）"""
    m = M('wild_lullaby_bell_staff')
    tar_grip(m, -14, 2)
    m.box('shaft', H, [-1.2, 3, -1.2], [1.2, 30, 1.2], grad=(3, 3, 30, 5))
    m.arc('cradle', H, (0, 35), 5, 200, 340, 7, [1.5] * 7, 2.5, 5, 7)
    m.box('cradle_floor', H, [-3.5, 30, -1.25], [3.5, 31, 1.25], 5)
    m.box('sleep_glow', L, [-1, 31, -0.75], [1, 32, 0.75], 4)
    for k, x in enumerate((-5.25, 5.25)):
        m.box(f'cord_{k}', T, [x - 0.15, 33, -0.15], [x + 0.15, 34.5, 0.15], 3)
        m.box(f'bell_{k}', T, [x - 1, 31, -1], [x + 1, 33, 1], grad=(31, 3, 33, 5))
    return m


def wild_mourning_bell_staff():
    """弔いの鈴杖: 長い杖の頭が下へ曲がり、そこから大きな鐘が一つ吊られる。鐘には黒い喪の布が掛かって垂れ、
    鐘の口の内側だけがかすかに灯色"""
    m = M('wild_mourning_bell_staff')
    tar_grip(m, -14, 2)
    m.box('shaft', H, [-1.2, 3, -1.2], [1.2, 34, 1.2], grad=(3, 3, 34, 5))
    m.chain('crook', H, [(0, 33.5), (1.5, 36.5), (4, 37), (5.5, 35)], [2.25, 2, 1.75], 1.6, 5, 7, tip=False)
    m.box('bell', T, [3.5, 29.5, -2], [7.5, 34, 2], grad=(29.5, 2, 34, 4))
    m.box('bell_lip', T, [3, 28.75, -2.5], [8, 29.75, 2.5], 3)
    m.box('mouth_glow', L, [4, 28.5, -1.5], [7, 28.9, 1.5], 3)
    m.box('veil', H, [3.25, 27, -2.6], [7.75, 34.5, -2.1], grad=(27, 1, 34.5, 3))
    m.box('veil_tail', H, [4, 24, -2.6], [5.5, 27, -2.1], 1)
    return m


def wild_saltpillar_totem():
    """塩柱の祈り柱: 塩の結晶の塊を積み上げた柱。段ごとに少しずつ向きがずれ、角が欠ける。
    中ほどの段の割れ目から灯が漏れ、頂は尖った一本の結晶"""
    m = M('wild_saltpillar_totem')
    tar_grip(m, -14, 2, 1.6)
    m.box('base', H, [-2, 2.75, -2], [2, 6, 2], 5)
    for k, (y0, y1, w, dx) in enumerate(((6, 13, 3.5, 0), (13, 19.5, 4, 0.5), (19.5, 26, 3.75, -0.5), (26, 31.5, 3.25, 0.25))):
        m.box(f'block_{k}', T, [dx - w / 2, y0, -w / 2], [dx + w / 2, y1, w / 2], grad=(y0, 2 + k % 2, y1, 4 + k % 2))
        m.box(f'chip_{k}', H, [dx + w / 2 - 0.75, y1 - 1.5, -w / 2 - 0.05], [dx + w / 2 + 0.05, y1, -w / 2 + 0.75], 3)
    m.box('crack_glow', L, [-2.15, 16, -0.4], [-1.9, 18.5, 0.4], 4)
    m.seg('spire', T, (0.25, 31.5), (0.75, 37.5), 2.5, 2.25, 4, 5, tip=True)
    return m


ALL = [wild_bonestring_bow, wild_lantern_bow, wild_saltcrow_bow, wild_hanged_longbow,
       wild_delver_sword, wild_inverted_greatsword, wild_saltcrystal_greatsword,
       wild_bone_needle_dagger, wild_ropecut_knife, wild_snuffer_dagger, wild_ceiling_claw,
       wild_pick_hammer, wild_lampeater_hammer, wild_blasting_axe, wild_saltmill_maul, wild_rope_halberd,
       wild_compass_staff, wild_bonepipe_staff, wild_mimic_heart_staff, wild_salttear_staff,
       wild_knotted_bell_staff, wild_lullaby_bell_staff, wild_mourning_bell_staff, wild_saltpillar_totem]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
