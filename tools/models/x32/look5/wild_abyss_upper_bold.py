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
    'rag': ['#2a2420', '#3c342c', '#504538', '#665846', '#7e6e58', '#98876e'],
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


R = 'rag'


def bell(m, name, x, y, w, h, mat=T):
    """吊り鈴（上が細い 2 段の箱と口の縁）。y は口の下端"""
    m.box(f'{name}', mat, [x - w / 2, y + 0.5, -w / 2], [x + w / 2, y + h, w / 2], grad=(y, 2, y + h, 4))
    m.box(f'{name}_lip', mat, [x - w / 2 - 0.3, y, -w / 2 - 0.3], [x + w / 2 + 0.3, y + 0.6, w / 2 + 0.3], 3)


def staff(m, top, r=1.1):
    tar_grip(m, -14, 2, 1.3)
    m.box('shaft', H, [-r, 3, -r], [r, top, r], grad=(3, 3, top, 5))


def haft(m, top, r=1.25):
    tar_grip(m, -6, 6)
    m.box('shaft', H, [-r, 7, -r], [r, top, r], grad=(7, 3, top, 5))


# ================================================================ 弓

def wild_bonestring_bow():
    """背骨の弓（エピック）: 弓腕が獣の背骨の節（椎骨）を連ねたもの。節ごとに棘が背へ長く立ち、左右（奥行き）には
    小さな肋骨が一対ずつ張り出す。握りの真上の一番大きな椎骨の穴の奥に灯が点る。弦は干した腱で、
    指の骨の玉が上下に二つずつ"""
    m = M('wild_bonestring_bow')
    m.box('grip', H, [10.25, 9.5, -1.75], [13.75, 20.5, 1.75], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    m.box('core_vert', T, [9.75, 19.5, -2.25], [14.5, 22.5, 2.25], 4)
    m.box('core_glow', L, [14.4, 20.25, -1], [14.8, 21.75, 1], 4)
    m.box('core_vert_d', T, [9.75, 7.5, -2.25], [14.5, 10.5, 2.25], 4)
    pts = [(12, 22), (11.25, 27), (10, 32), (8.25, 36.5), (6.25, 40.5), (4.25, 43.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        for k in range(len(pts) - 1):
            a, b = P(pts[k]), P(pts[k + 1])
            m.seg(f'vert_{n}{k}', T, a, b, 3 - k * 0.25, 1.75 - k * 0.15, 3, 4, over=0.1)
            mx = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
            ln = 3.75 - k * 0.5
            m.seg(f'spine_{n}{k}', T, mx, (mx[0] + ln, mx[1] + sign * 1.25), 1.1, 0.55, 4, 5, tip=True)
            if k < 3:
                for zs, zn in ((1, 'f'), (-1, 'b')):
                    z0 = (1.6 - k * 0.15) * zs
                    m.box(f'rib_{n}{k}{zn}', T, [mx[0] - 0.5, mx[1] - 0.45, min(z0, z0 + zs * 2.25)], [mx[0] + 0.5, mx[1] + 0.45, max(z0, z0 + zs * 2.25)], 4)
                    z1 = z0 + zs * 2.25
                    m.box(f'rib_tip_{n}{k}{zn}', T, [mx[0] - 1.5, mx[1] - 0.4, min(z1, z1 + zs * 0.8)], [mx[0] + 0.5, mx[1] + 0.4, max(z1, z1 + zs * 0.8)], 5)
        for k, p in enumerate(pts[1:5]):
            q = P(p)
            m.box(f'disc_{n}{k}', H, [q[0] - 1.6, q[1] - 0.35, -1.25], [q[0] + 1.6, q[1] + 0.35, 1.25], 3)
    top, bot = (4, 43.5), (4, -13.5)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((36, 32.5, -2.5, -6)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_knuckle{k}', T, [q[0] - 0.75, q[1] - 1, -0.75], [q[0] + 0.75, q[1] + 1, 0.75], 4)
    return m


def wild_lantern_bow():
    """提灯の弓（エピック）: 弓腕そのものが坑道の鉄の枠。外と内の二本の縁のあいだに灯の窓が並び、窓は鉄の格子で区切られる。
    窓の一つずつの中で小さな灯の炎が揺れる（板の絵、前後に一枚ずつ）。格子の頭に塩の鋲、弓の先は鉄の笠"""
    m = M('wild_lantern_bow')
    m.box('grip', H, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    arm = [(12, 21), (13.5, 27), (13.25, 33), (11.25, 38.5), (8, 42), (4.5, 43.25)]
    outer, inner = lib.offset_line(arm, 2.1), lib.offset_line(arm, -2.1)
    centers = [((arm[k][0] + arm[k + 1][0]) / 2, (arm[k][1] + arm[k + 1][1]) / 2) for k in range(4)]
    cv = lib.Canvas(5, 20, 18, 44)

    def f(x, y, cur):
        for k, c in enumerate(centers):
            u, v = x - c[0], y - c[1] + 1.2
            h = 2.6 - k * 0.3
            if 0 <= v <= h:
                w = 0.9 * (1 - v / h) ** 0.7 * (1 if k < 3 else 0.8)
                if abs(u - 0.2 * math.sin(v * 2)) < w:
                    return (L, 5) if abs(u) < w * 0.45 and v < h * 0.6 else (L, 3)
        return None
    cv.paint(f)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'pane_{n}_glow', L, [P(q) for q in arm], [3, 3, 2.75, 2.25, 1.75], 0.9, 1, 2, tip=False)
        m.chain(f'rim_out_{n}', H, [P(q) for q in outer], [1.1, 1.1, 1, 0.9, 0.8], 1.3, 3, 6, tip=False)
        m.chain(f'rim_in_{n}', H, [P(q) for q in inner], [1.1, 1.1, 1, 0.9, 0.8], 1.3, 3, 6, tip=False)
        for k in range(1, 5):
            m.seg(f'bar_{n}{k}', H, P(inner[k]), P(outer[k]), 0.8, 1.3, 5, 6, over=0.3)
            q = P(outer[k])
            m.box(f'rivet_{n}{k}', T, [q[0] - 0.5, q[1] - 0.5, -1.45], [q[0] + 0.5, q[1] + 0.5, 1.45], 4)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            cv.put(m, f'flame_{n}{zn}_glow', at=zs * 0.95, mirror15=sign < 0)
        c = P((4.5, 43.25))
        m.box(f'cap_{n}', H, [c[0] - 1.5, c[1] - 1, -1.5], [c[0] + 1.5, c[1] + 1, 1.5], 6)
    strings(m, (4.5, 43.25), (4.5, -13.25))
    return m


def wild_saltcrow_bow():
    """塩鴉の弓（レア）: 弓腕そのものが細く長い鴉の首。先は塩で白くなった鴉の頭蓋で、嘴が弦をくわえる。
    上の頭蓋の目だけに灯が宿る"""
    m = M('wild_saltcrow_bow')
    m.box('grip', H, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', T, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        m.chain(f'neck_{n}', H, [P(q) for q in arm[:5]], [2.6, 2.6, 2.4, 2.2], 1.3, 2, 4, tip=False)
        m.seg(f'skull_{n}', T, P((8, 42)), P((5, 44.75)), 3.4, 1.8, 6, 7)
        m.seg(f'beak_{n}', T, P((5.5, 44.5)), P((1.5, 45.5)), 2.0, 1.2, 7, 8, tip=True)
    for zs, zn in ((1, 'f'), (-1, 'b')):
        z0, z1 = sorted((zs * 1.8, zs * 1.95))
        m.box(f'eye_{zn}_glow', L, [6.25, 43.25, z0], [7.25, 44.25, z1], 4)
    strings(m, (2.5, 45.25), (2.5, -15.25))
    return m


def wild_hanged_longbow():
    """吊り人の大弓（ヒロイック）: 背丈ほどの長弓。弓腕には綱がぐるぐると巻きつき、継ぎ目ごとに塩の白。
    上の弓腕の腹には輪にした綱が掛かってだらりと下がり、端に結んだ札が灯色に光る。下の弓腕の腹からは鎖が二本垂れ、
    骨の鍵と塩の結晶の飾りが揺れる。左右（奥行き）にはぼろぼろの布切れがたなびく。弦は結び目の瘤が並ぶ綱"""
    m = M('wild_hanged_longbow')
    m.box('grip', H, [10.25, 9, -1.9], [13.75, 21, 1.9], grad=(9, 2, 21, 4), wrap=(9, 21))
    arm = [(12, 20.5), (11.25, 30), (9.5, 39), (7, 46.5), (4.25, 52)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P = lambda q, s=sign: mirror(q, s)
        pts = [P(q) for q in arm]
        m.chain(f'arm_{n}', H, pts, [3.75, 3.25, 2.75, 2.25], 1.5, 4, 7, tip=False)
        for k, p in enumerate(arm[1:4]):
            q = P(p)
            m.box(f'joint_{n}{k}', T, [q[0] - 2, q[1] - 0.5, -1.75], [q[0] + 2, q[1] + 0.5, 1.75], 3)
        for k in range(6):
            y = 23 + k * 3.25
            x = 11.6 - (y - 21) * 0.13
            q = P((x, y))
            m.seg(f'coil_{n}{k}', T, (q[0] - 2.25, q[1] - sign * 0.6), (q[0] + 2.25, q[1] + sign * 0.6), 0.9, 1.85, 2, 3, over=0)
        m.seg(f'nock_{n}', T, P((4.25, 52)), P((2.25, 54.5)), 1.75, 1.1, 3, 5, tip=True)
        for zs, zn in ((1, 'f'), (-1, 'b')):
            q = P((10.25, 35))
            pts_z = [(q[0], q[1], 1.6), (q[0] + 1.25, q[1] - sign * 1.5, 4), (q[0] + 0.5, q[1] - sign * 3.5, 6.25), (q[0] + 1.5, q[1] - sign * 5.75, 7.75)]
            for j in range(len(pts_z) - 1):
                (x0, y0, z0), (x1, y1, z1) = pts_z[j], pts_z[j + 1]
                m.box(f'rag_{n}{zn}{j}', 'rag', [min(x0, x1) - 0.9, min(y0, y1) - 0.4, zs * min(z0, z1) if zs > 0 else -max(z0, z1)],
                      [max(x0, x1) + 0.9, max(y0, y1) + 0.4, zs * max(z0, z1) if zs > 0 else -min(z0, z1)], 2 + j)
    # 背の旗: 上の弓腕の背から下がるぼろの旗（塩の印）
    m.box('banner_pole', T, [10.5, 36.5, -0.45], [24, 37.4, 0.45], 4)
    diamond(m, 'banner_pole_end', T, (24.25, 36.95), 1.6, 0.6, 4, 5)
    for k, (x0, x1, ylo) in enumerate(((11.5, 14.25, 22), (14.25, 17, 24.5), (17, 19.75, 20.5), (19.75, 22.25, 23.5), (22.25, 23.5, 27.5))):
        m.box(f'banner_{k}', 'rag', [x0, ylo, -0.3], [x1, 36.5, 0.3], grad=(ylo, 1, 36.5, 4))
    m.seg('banner_sigil', T, (14.5, 27.5), (19.5, 33.5), 1.2, 0.4, 4, 5, over=0)
    m.seg('banner_sigil_b', T, (14.5, 33.5), (19.5, 27.5), 1.2, 0.4, 4, 5, over=0)
    diamond(m, 'banner_eye_glow', L, (17, 30.5), 1.75, 0.45, 4, 5)
    for k, (x, ln, top) in enumerate(((12.75, 3, 22.5), (15.5, 5, 25), (18.25, 2, 21), (21, 4, 24))):
        m.box(f'chime_cord_{k}', T, [x - 0.15, 20.5 - ln, -0.15], [x + 0.15, top, 0.15], 3)
        m.box(f'chime_{k}', T, [x - 0.55, 17.5 - ln, -0.55], [x + 0.55, 20.5 - ln + 0.5, 0.55], 5)
    # 上の腹: 輪にした綱と札
    m.box('noose_top', T, [7.5, 41.5, -1.6], [10.75, 42.5, 1.6], 3)
    m.chain('noose', T, [(8.25, 42), (6.75, 38), (6.5, 33), (7.25, 29.5)], [0.9, 0.9, 0.9], 0.8, 3, 4, tip=False)
    m.chain('noose_loop', T, [(7.25, 29.5), (8.75, 28.25), (9, 30.75), (7.75, 31.5)], [0.75, 0.75, 0.75], 0.75, 3, 4, tip=False)
    m.box('tag_glow', L, [6.25, 25.25, -0.45], [8.5, 28.25, 0.45], 4)
    m.box('tag_tie', T, [7, 28.25, -0.3], [7.5, 29.75, 0.3], 3)
    # 下の腹: 鎖二本と骨の鍵・塩の結晶
    for k, (x, ln) in enumerate(((9.25, 6), (7.85, 9))):
        y0 = -9 + k * -2.5
        for j in range(int(ln / 1.5)):
            yy = y0 - j * 1.5
            if j % 2:
                m.box(f'chain_{k}{j}', H, [x - 0.25, yy - 0.8, -0.55], [x + 0.25, yy + 0.8, 0.55], 6)
            else:
                m.box(f'chain_{k}{j}', H, [x - 0.55, yy - 0.8, -0.25], [x + 0.55, yy + 0.8, 0.25], 5)
        yb = y0 - int(ln / 1.5) * 1.5
        if k == 0:
            m.box('key_bow', T, [x - 1.1, yb - 1.75, -0.4], [x + 1.1, yb, 0.4], 4)
            m.box('key_shaft', T, [x - 0.3, yb - 5, -0.3], [x + 0.3, yb - 1.75, 0.3], 4)
            m.box('key_bit', T, [x + 0.3, yb - 5, -0.3], [x + 1.4, yb - 3.75, 0.3], 4)
        else:
            diamond(m, 'charm_salt', T, (x, yb - 0.6), 2.5, 1, 4, 5)
    top, bot = (2.5, 54.25), (2.5, -24.25)
    strings(m, top, bot)
    for key, pull in lib.PULLS:
        for k, y in enumerate((48, 42, 36, -6, -12, -18)):
            q = lib.string_at(top, bot, pull, lib.string_t(top, bot, y))
            m.box(f'string_{key}_knot{k}', T, [q[0] - 0.85, q[1] - 0.7, -0.85], [q[0] + 0.85, q[1] + 0.7, 0.85], 2 + k % 2)
    return m


# ================================================================ 剣・大剣

def wild_delver_sword():
    """（レア、2026-11-21 に作り直し・案 B）鉤先の剣: 刃の先が登攀の鉤のように後ろへ巻き返る。柄頭が小さな灯籠で光る
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ鉤を、先へ細る一枚の帯のメッシュに替え、刃に塩の白い筋を描いた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_delver_sword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_delver_sword')
    tar_grip(m, -6, 6)
    m.box('pommel_cage', H, [-1.6, -9.4, -1.6], [1.6, -7, 1.6], 6)
    m.box('pommel_glow', L, [-1.1, -9, -1.7], [1.1, -7.4, 1.7], 4)
    m.box('guard', H, [-3.2, 7, -1.8], [3.2, 8.8, 1.8], 5)
    m.box('blade', H, [-1.8, 8.8, -0.8], [1.8, 27, 0.8], grad=(8.8, 5, 27, 8))
    m.chain('hook', H, [(0, 26.5), (0.8, 30), (-1.2, 32.5), (-3.6, 31.2), (-3.8, 28.6)], [3.4, 2.6, 1.9, 1.3], 0.8, 6, 9, tip=True)
    m.box('salt_line', T, [1.2, 10, -0.85], [1.8, 25, 0.85], 3)
    return m


def wild_inverted_greatsword():
    """（ヒロイック、2026-11-21 に作り直し・案 B）逆さの塔: 広がる刃の上の両角から塩の結晶が大きく突き出し、平たい先の中央が光の窓
    ※ 2026-11-21 に Blockbench で手を入れた（逆さの刃を、根元が細く先が広い錐台のメッシュに替え、塩の結晶を y に少しずつ違う角度で傾けた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_inverted_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_inverted_greatsword')
    tar_grip(m, -8, 8, 1.6)
    m.box('pommel', T, [-2, -11, -2], [2, -9, 2], 4)
    m.box('guard', H, [-3, 8.6, -2], [3, 11, 2], 5)
    for k, (y, w) in enumerate(((11, 2.4), (18, 4), (25, 5.8), (32, 7.6))):
        m.box(f'blade_{k}', H, [-w / 2, y, -1.2], [w / 2, y + 7, 1.2], grad=(y, 4, y + 7, 6))
    m.box('crown', H, [-4.6, 39, -1.4], [4.6, 42, 1.4], 7)
    m.box('window_glow', L, [-1.4, 39.6, -1.5], [1.4, 41.4, 1.5], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'crystal_{n}', T, (sd * 4, 40), (sd * 8.5, 46), 2.6, 1.8, 3, 5, tip=True)
        m.seg(f'crystal_{n}2', T, (sd * 3.5, 41.5), (sd * 4.5, 47.5), 1.8, 1.4, 3, 5, tip=True)
    return m


def wild_saltcrystal_greatsword():
    """（エピック、2026-11-21 に作り直し・案 A）塩晶の束: 中央の長い結晶の柱を左右の短い柱が斜めに挟む。中央の芯が光る
    ※ 2026-11-21 に Blockbench で手を入れた（刃先を四角錐のメッシュに替え、刃から出る塩の欠片をそれぞれ違う向きに傾けた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_saltcrystal_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_saltcrystal_greatsword')
    tar_grip(m, -8, 8, 1.6)
    m.box('pommel', H, [-2, -11, -2], [2, -9, 2], 5)
    m.box('guard', H, [-4.5, 9, -2], [4.5, 11, 2], grad=(9, 3, 11, 5))
    m.box('main', T, [-2, 11, -1.4], [2, 40, 1.4], grad=(11, 2, 40, 5))
    m.seg('main_tip', T, (0, 40), (0, 45), 4, 2.8, 4, 5, tip=True)
    m.box('core_glow', L, [-0.5, 15, -1.5], [0.5, 34, 1.5], grad=(15, 2, 34, 4))
    for k, (x, y0, y1, a) in enumerate(((-2.5, 12, 24, -1), (2.5, 15, 28, 1), (-2.3, 26, 34, -1), (2.3, 30, 37, 1))):
        m.seg(f'shard_{k}', T, (x, y0), (x + a * 2, y1), 2.5, 2, 3, 5, tip=True)
    return m


def wild_bone_needle_dagger():
    """（エピック、2026-11-21 に作り直し・案 B）三本針: 長さの違う骨の針を三本、光る糸でひと括り。括りの下に骨の節
    ※ 2026-11-21 に Blockbench で手を入れた（骨の針を x に少しずつ違う角度で傾け、柄に骨の輪を描いた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_bone_needle_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_bone_needle_dagger')
    tar_grip(m, -4, 4, 1.25)
    m.box('knuckle', T, [-2.2, 5, -1.5], [2.2, 7, 1.5], grad=(5, 3, 7, 4))
    for k, (x, top) in enumerate(((-1.4, 18), (0, 23), (1.4, 16))):
        m.seg(f'needle_{k}', T, (x * 1.1, 6.5), (x * 1.6, top), 1.4, 0.9, 4, 5, tip=True)
    m.box('bind_glow', L, [-2.3, 9.5, -1.1], [2.3, 10.7, 1.1], 4)
    m.box('bind', R, [-2.1, 11.2, -1], [2.1, 12, 1], 3)
    return m


def wild_ropecut_knife():
    """（レア、2026-11-21 に作り直し・案 C）二股の刃: 真っ直ぐな刃の先が V に割れ、割れ目の奥が光る（綱を掛けて切る）
    ※ 2026-11-21 に Blockbench で手を入れた（二股の先を y に開くように傾け、刃の縁を明るく描いた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_ropecut_knife.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ropecut_knife')
    tar_grip(m, -4, 4, 1.3)
    m.box('bolster', H, [-1.8, 4.6, -1.3], [1.8, 6.4, 1.3], 5)
    m.box('blade', H, [-1.4, 6.4, -0.7], [1.4, 15, 0.7], grad=(6.4, 5, 15, 8))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'prong_{n}', H, (sd * 0.7, 14.5), (sd * 2.6, 19.5), 1.6, 0.65, 7, 9, tip=True)
    m.box('fork_glow', L, [-0.5, 14.4, -0.75], [0.5, 15.6, 0.75], 4)
    return m


def wild_snuffer_dagger():
    """（ヒロイック、2026-11-21 に作り直し・案 C）二つの帽: 刃の左右へ腕が伸び、それぞれに火消し帽。片方は残り火が光り、片方は煤で黒い
    ※ 2026-11-21 に Blockbench で手を入れた（火消しの笠を六角のろくろ形（円錐）のメッシュに替えた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_snuffer_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_snuffer_dagger')
    tar_grip(m, -4, 4, 1.25)
    m.box('guard', H, [-2.4, 4.6, -1.2], [2.4, 6, 1.2], 5)
    m.seg('blade', H, (0, 6), (0, 22), 2.2, 1, 6, 9, tip=True)
    for sd, n in ((-1, 'l'), (1, 'r')):
        x = sd * 5
        m.seg(f'arm_{n}', H, (sd * 2, 5.4), (x, 9), 0.9, 0.9, 6, 7)
        m.box(f'cone_rim_{n}', H, [x - 2, 9, -1.8], [x + 2, 10, 1.8], 6)
        m.box(f'cone_mid_{n}', H, [x - 1.4, 10, -1.3], [x + 1.4, 12, 1.3], 7)
        m.seg(f'cone_tip_{n}', H, (x, 12), (x, 14), 1.1, 0.8, 8, 9, tip=True)
    m.box('ember_glow', L, [3.4, 8.4, -1.2], [6.6, 9.1, 1.2], 3)
    return m


# ================================================================ 爪


def wild_ceiling_claw():
    """（エピック、2026-11-21 に作り直し・案 C）錨爪: 籠手の先が錨のような四本の爪（左右と前後）に開き、上へ反る。中央の軸の頭が光る
    ※ 2026-11-21 に Blockbench で手を入れた（錨の爪を、先へ細る帯のメッシュに替えた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_ceiling_claw.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ceiling_claw')
    tar_grip(m, -7, 3, 1.4)
    m.box('gauntlet', H, [-2.6, 3, -2.4], [2.6, 8.5, 2.4], grad=(3, 3, 8.5, 5))
    m.box('shank', H, [-0.9, 8.5, -0.9], [0.9, 16, 0.9], grad=(8.5, 4, 16, 6))
    m.box('cap_glow', L, [-1.1, 16, -1.1], [1.1, 17.4, 1.1], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'fluke_{n}', H, [(0, 10), (sd * 4, 10.6), (sd * 6.2, 13.4), (sd * 6.2, 17)], [1.6, 1.3, 1], 0.7, 5, 8, tip=True)
    for sd, n in ((-1, 'b'), (1, 'f')):
        m.chain(f'fluke_{n}', H, [(0, 10), (sd * 1.2, 11.5), (sd * 2, 15), (sd * 1.6, 18.5)], [1.4, 1.1, 0.8], 0.7, 5, 8, tip=True, zc=sd * 1.3)
    m.box('shell', T, [-3, 4, -2.7], [3, 7.6, 2.7], 3)
    return m


# ================================================================ 鎚・斧


def wild_pick_hammer():
    """（レア、2026-11-21 に作り直し・案 B）双頭の鶴嘴: 両側とも鶴嘴（片方は長く反り、片方は短く真っ直ぐ）。頭の上に光る塩の結晶
    ※ 2026-11-21 に Blockbench で手を入れた（左右のつるはしの先を、先へ細る帯のメッシュ二本に替えた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_pick_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_pick_hammer')
    haft(m, 22, 1.2)
    m.box('eye', H, [-2, 20, -2], [2, 25, 2], grad=(20, 4, 25, 6))
    m.chain('pick_l', H, [(-2, 23), (-6, 23), (-9.5, 21), (-11.5, 17.5)], [2.5, 2, 1.4], 1.4, 6, 8, tip=True)
    m.chain('pick_r', H, [(2, 23), (5, 23.2), (7.5, 22.4)], [2.4, 1.8], 1.4, 6, 8, tip=True)
    diamond(m, 'salt_glow', L, (0, 26), 1.6, 1.2, 4, 5)
    return m


def wild_lampeater_hammer():
    """（ヒロイック、2026-11-21 に作り直し・案 B）大顎: 頭が大きく開いた獣の顎。上顎と下顎に歯が並び、口の奥で灯を噛んで光る。顎の後ろが打つ面
    ※ 2026-11-21 に Blockbench で手を入れた（下の歯を少しずつ違う角度で傾け、歯ぐきを描いた（上あごと上の歯は傾けると離れるので動かしていない））。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_lampeater_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_lampeater_hammer')
    haft(m, 21)
    m.box('hinge', H, [-5, 20, -2.6], [-1, 28, 2.6], grad=(20, 4, 28, 6))
    m.box('face', H, [-6.4, 19.5, -3], [-5, 28.5, 3], 8)
    m.seg('jaw_low', H, (-1.5, 21), (6, 19), 2.4, 2.4, 5, 7, tip=False)
    m.seg('jaw_high', H, (-1.5, 27), (6, 30), 2.4, 2.4, 6, 8, tip=False)
    for k, f in enumerate((0.3, 0.6, 0.9)):
        x = -1.5 + 7.5 * f
        m.seg(f'tooth_d{k}', T, (x, 21 - 2 * f + 1), (x + 0.3, 23 - 2 * f + 1), 0.9, 1.6, 3, 4, tip=True)
        m.seg(f'tooth_u{k}', T, (x, 27 + 3 * f - 1), (x + 0.3, 25 + 3 * f - 1), 0.9, 1.6, 3, 4, tip=True)
    m.box('lamp_glow', L, [-1, 22, -1.4], [1.6, 25.6, 1.4], 4)
    return m


def wild_blasting_axe():
    """（エピック、2026-11-21 に作り直し・案 A）発破の斧: 厚い楔の斧。背に布巻きの発破の筒が二本、導火線の先が燃えて光る
    ※ 2026-11-21 に Blockbench で手を入れた（刃をくさび形の一枚のメッシュに替え、火薬の包みを ±6 度傾けた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_blasting_axe.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_blasting_axe')
    haft(m, 25)
    m.box('head', H, [-1.8, 18.5, -1.8], [2.6, 25.5, 1.8], grad=(18.5, 4, 25.5, 6))
    for k, (y, w) in enumerate(((17, 3), (18.5, 4.6), (20.5, 5.6), (22.5, 5.6), (24.5, 4.6), (26, 3))):
        m.box(f'wedge_{k}', H, [2.6, y, -1.4], [2.6 + w, y + 1.8, 1.4], 6 + (1 if k in (2, 3) else 0))
    m.box('edge', H, [7.9, 19, -0.6], [8.6, 26, 0.6], 8)
    for k, z in enumerate((-1, 1)):
        m.box(f'charge_{k}', R, [-4.8, 19, z - 1], [-2, 25, z + 0.9], grad=(19, 2, 25, 4))
    m.box('strap', H, [-5.1, 21.5, -2.2], [-1.8, 22.5, 2.2], 4)
    m.chain('fuse', R, [(-3.5, 25), (-4, 27), (-5.5, 28)], [0.5, 0.5], 0.5, 3, 3, tip=False)
    m.box('spark_glow', L, [-6.3, 27.5, -0.4], [-5.3, 28.5, 0.4], 4)
    return m


def wild_saltmill_maul():
    """（エピック、2026-11-21 に作り直し・案 B）縦の臼車: 臼石を横に寝かせ、車輪のように柄の先に立てる。縁に挽き溝、軸の穴が光る、上に塩の山
    ※ 2026-11-21 に Blockbench で手を入れた（石臼の車を八角の円盤のメッシュに替え、上の塩の山を臼に乗るよう 33.4–34.6 と 34.6–35.6 に下げた（この関数の数値より下にある））。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_saltmill_maul.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_saltmill_maul')
    tar_grip(m, -7, 7, 1.6)
    m.box('shaft', H, [-1.3, 8, -1.3], [1.3, 21, 1.3], grad=(8, 3, 21, 5))
    m.box('fork', H, [-2, 20, -3.4], [2, 22, 3.4], 5)
    m.box('wheel_a', H, [-6, 21, -2.6], [6, 33, 2.6], grad=(21, 4, 33, 6))
    m.box('wheel_b', H, [-4.4, 19.4, -2.4], [4.4, 34.6, 2.4], grad=(19.4, 4, 34.6, 6))
    m.box('axle_glow', L, [-1.2, 25.8, -2.8], [1.2, 28.2, 2.8], 4)
    m.box('salt_heap', T, [-3, 34.6, -1.6], [3, 35.8, 1.6], 4)
    m.box('salt_top', T, [-1.6, 35.8, -1], [1.6, 36.8, 1], 5)
    return m


def wild_rope_halberd():
    """（エピック、2026-11-21 に作り直し・案 B）引っ掛け鉤の鉾: 穂の下に三方へ反る登攀の鉤爪。綱が柄から鉤へ伸びて巻き、結び目が光る
    ※ 2026-11-21 に Blockbench で手を入れた（左右の爪を、先へ細って曲がる帯のメッシュに、穂先を四角錐のメッシュに替えた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_rope_halberd.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_rope_halberd')
    m.box('grip', H, [-1.5, -16, -1.5], [1.5, 0, 1.5], grad=(-16, 2, 0, 4), wrap=(-16, 0))
    m.box('butt', T, [-1.9, -18.5, -1.9], [1.9, -16, 1.9], 3)
    m.box('haft', H, [-1.25, 0, -1.25], [1.25, 37, 1.25], grad=(0, 3, 37, 5))
    m.box('rope', R, [-1.6, 24, -1.6], [1.6, 33, 1.6], grad=(24, 2, 33, 4), wrap=(24, 33))
    m.box('knot_glow', L, [-2.4, 28, -0.8], [-1.4, 29.6, 0.8], 4)
    m.box('socket', H, [-2, 35, -2], [2, 39, 2], grad=(35, 4, 39, 6))
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'claw_{n}', H, [(sd * 2, 37), (sd * 5.5, 37.5), (sd * 7, 40.5), (sd * 6, 43)], [1.8, 1.4, 1], 0.9, 6, 8, tip=True)
    m.seg('spike', H, (0, 39), (0, 49), 2.3, 1.4, 6, 8, tip=True)
    return m


def wild_compass_staff():
    """（エピック、2026-11-21 に作り直し・案 A）羅針: 塩で曇った羅針盤の輪が立ち、中で針が斜め。北の先が光り、外に方角の突起
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ輪を、閉じた一本の帯のメッシュの輪に替えた（下の継ぎ目が開かないよう 14 段・26 度刻み））。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_compass_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_compass_staff')
    staff(m, 29)
    m.box('mount', H, [-1.6, 28.5, -1.6], [1.6, 30, 1.6], 5)
    m.arc('ring', H, (0, 34.4), 4.2, 0, 360, 12, 1.1, 1.2, 6, 7)
    m.box('face', T, [-3, 31.4, -0.4], [3, 37.4, 0.4], 4)
    m.seg('needle_s', H, (0, 34.4), (-1.8, 32), 0.8, 1, 5, 6, tip=True)
    m.seg('needle_glow', L, (0, 34.4), (1.8, 36.8), 0.8, 1, 4, 5, tip=True)
    for k, (a, b) in enumerate((((0, 38.8), (0, 40.6)), ((4.4, 34.4), (6.2, 34.4)), ((-4.4, 34.4), (-6.2, 34.4)))):
        m.seg(f'point_{k}', H, a, b, 1.1, 0.9, 6, 7, tip=True)
    return m


def wild_bonepipe_staff():
    """（レア、2026-11-21 に作り直し・案 B）骨の笙: 五本の骨の管が扇に開いて立つ（真ん中が最も長い）。真ん中の管の口が光る
    ※ 2026-11-21 に Blockbench で手を入れた（骨の管を x に少しずつ違う角度で傾け、指穴を描いた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_bonepipe_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_bonepipe_staff')
    staff(m, 28)
    m.box('cup', H, [-3, 27, -1.6], [3, 30, 1.6], grad=(27, 4, 30, 6))
    for k, (ang, ln) in enumerate(((120, 7), (105, 9), (90, 11), (75, 9), (60, 7))):
        a = math.radians(ang)
        b = (ln * math.cos(a), 29 + ln * math.sin(a))
        m.seg(f'pipe_{k}', T, (0.6 * math.cos(a) * 3, 29), b, 1.5, 0.75, 3, 5)
    m.box('mouth_glow', L, [-0.5, 39.6, -0.5], [0.5, 40.4, 0.5], 4)
    return m


def wild_mimic_heart_staff():
    """（ヒロイック、2026-11-21 に作り直し・案 B）大口の箱: 蓋が大きく後ろへ跳ね上がり、口いっぱいに歯。舌が杖に巻き付き、口の奥で心臓が光る
    ※ 2026-11-21 に Blockbench で手を入れた（舌を、先へ細って垂れる帯のメッシュに替えた（ふたは傾けると離れるので動かしていない））。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_mimic_heart_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_mimic_heart_staff')
    staff(m, 29)
    m.box('box', H, [-4, 29, -3], [4, 33.5, 3], grad=(29, 4, 33.5, 6))
    m.seg('lid', H, (-3.5, 33.5), (-6, 40), 7, 3, 5, 7, over=0.3)
    m.box('heart_glow', L, [-1.4, 32, -1.4], [1.4, 34.6, 1.4], 4)
    for k, x in enumerate((-2.8, -1.4, 0, 1.4, 2.8)):
        m.seg(f'tooth_{k}', T, (x, 33.5), (x, 35.3), 0.8, 0.6, 4, 5, tip=True, zc=-2.6)
        m.seg(f'tooth_b{k}', T, (x, 33.5), (x, 35.3), 0.8, 0.6, 4, 5, tip=True, zc=2.6)
    m.chain('tongue', R, [(0, 30), (2.2, 27.6), (1.2, 25), (-1.4, 23.4)], [1.4, 1.2, 1], 1.2, 2, 4, tip=True, zc=0)
    return m


def wild_salttear_staff():
    """（エピック、2026-11-21 に作り直し・案 B）三粒の涙: 杖の先の横木から大中小三粒の塩の涙が吊られる。真ん中の大粒の芯が光る
    ※ 2026-11-21 に Blockbench で手を入れた（三つの塩の涙を六角のろくろ形（しずく）のメッシュに替えた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_salttear_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_salttear_staff')
    staff(m, 36)
    m.box('bar', H, [-6, 35.4, -0.8], [6, 36.6, 0.8], 6)
    for k, (x, s, drop) in enumerate(((-4.6, 1.2, 4), (0, 1.8, 6), (4.6, 1.2, 3))):
        m.box(f'thread_{k}', H, [x - 0.15, 35.4 - drop, -0.15], [x + 0.15, 35.4, 0.15], 5)
        y = 35.4 - drop
        m.box(f'tear_{k}', T, [x - s, y - 2 * s, -s * 0.8], [x + s, y - 0.3, s * 0.8], 4)
        m.seg(f'tear_top_{k}', T, (x, y - 0.5), (x, y + 0.2), s * 1.2, s * 0.8, 4, 5, tip=True)
    m.box('tear_glow', L, [-0.8, 26.5, -1.5], [0.8, 28.6, 1.5], 4)
    return m


def wild_knotted_bell_staff():
    """（エピック、2026-11-21 に作り直し・案 B）綱の梯子: 杖の先の横木から短い綱の梯子が垂れ、横桟ごとに鈴。いちばん上の結び目が光る
    ※ 2026-11-21 に Blockbench で手を入れた（三つの鈴を六角のろくろ形のメッシュに替え、はしごの段を +4/−3/+5 度傾けた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_knotted_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_knotted_bell_staff')
    tar_grip(m, -14, 2)
    m.box('shaft', H, [-1.2, 3, -1.2], [1.2, 36, 1.2], grad=(3, 3, 36, 5))
    m.box('bar', H, [-1.2, 34.4, -0.9], [7, 35.8, 0.9], 6)
    m.box('knot_glow', L, [-1.6, 33.8, -1.6], [1.6, 36.4, 1.6], 4)
    for sd, x in (('a', 3), ('b', 6.2)):
        m.box(f'side_{sd}', R, [x - 0.3, 24, -0.3], [x + 0.3, 34.4, 0.3], 3)
    for k, y in enumerate((31, 27.6, 24.2)):
        m.box(f'rung_{k}', R, [2.6, y, -0.3], [6.6, y + 0.6, 0.3], 3)
        bell(m, f'bell_{k}', 4.6, y - 1.9, 1.6, 2, T)
    return m


def wild_lullaby_bell_staff():
    """（ヒロイック、2026-11-21 に作り直し・案 B）吊り籠: 杖の頭が大きく曲がり、鎖で小さな籠が吊られる。籠の下に鈴が三つ、籠の中の灯が光る
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ曲がりを一本の帯のメッシュに、三つの鈴を六角のろくろ形のメッシュに替えた）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_lullaby_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_lullaby_bell_staff')
    tar_grip(m, -14, 2)
    m.box('shaft', H, [-1.2, 3, -1.2], [1.2, 35, 1.2], grad=(3, 3, 35, 5))
    m.chain('crook', H, [(0, 34.5), (1.5, 38.5), (5, 39.5), (7.5, 37.5)], [2.4, 2.2, 1.8], 1.6, 5, 7, tip=False)
    m.box('chain', T, [7.2, 32, -0.2], [7.8, 37, 0.2], 3)
    m.box('basket', H, [4.4, 27, -2.6], [10.6, 32, 2.6], grad=(27, 4, 32, 6))
    m.box('basket_rim', R, [4, 31.4, -3], [11, 32.4, 3], 3)
    m.box('sleep_glow', L, [6.4, 32.2, -1], [8.6, 33.2, 1], 4)
    for k, x in enumerate((5.2, 7.5, 9.8)):
        m.box(f'cord_{k}', T, [x - 0.15, 24.6, -0.15], [x + 0.15, 27, 0.15], 3)
        bell(m, f'bell_{k}', x, 22.6, 1.6, 2, T)
    return m


def wild_mourning_bell_staff():
    """（レア、2026-11-21 に作り直し・案 A）弔いの鐘: 杖の頭が下へ曲がって大きな鐘を一つ吊る。鐘に黒い喪の布、口の内側だけ光る
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ曲がりを一本の帯のメッシュに、鈴を六角のろくろ形のメッシュに替え、喪の布を y に 10 度ひねった）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_mourning_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_mourning_bell_staff')
    tar_grip(m, -14, 2)
    m.box('shaft', H, [-1.2, 3, -1.2], [1.2, 34, 1.2], grad=(3, 3, 34, 5))
    m.chain('crook', H, [(0, 33.5), (1.5, 36.5), (4, 37), (5.5, 35)], [2.3, 2, 1.8], 1.6, 5, 7, tip=False)
    bell(m, 'bell', 5.5, 28.8, 4.4, 5.6, T)
    m.box('mouth_glow', L, [4, 28.4, -1.5], [7, 28.9, 1.5], 3)
    m.box('veil', R, [3.1, 27, -2.8], [7.9, 34.6, -2.3], grad=(27, 0, 34.6, 2))
    return m


def wild_saltpillar_totem():
    """（エピック、2026-11-21 に作り直し・案 B）塩の樹: 柱の上で塩の結晶が枝のように左右へ分かれ、枝先が尖る。幹の芯の割れ目が光る
    ※ 2026-11-21 に Blockbench で手を入れた（幹を下へ太る六角のろくろ形のメッシュに替え、四本の枝を y に ±25/±30 度ひねった）。メッシュの形と UV はスクリプト（au_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_saltpillar_totem.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_saltpillar_totem')
    tar_grip(m, -14, 2, 1.6)
    m.box('trunk', T, [-1.8, 2.8, -1.8], [1.8, 30, 1.8], grad=(2.8, 2, 30, 4))
    m.box('crack_glow', L, [-0.4, 18, -1.9], [0.4, 24, 1.9], 4)
    for k, (a, b, w) in enumerate((((0, 26), (-5, 32), 2.2), ((0, 27), (5, 34), 2.2), ((-3.5, 30.5), (-6.5, 37), 1.6), ((3.5, 31.5), (5.5, 39), 1.6), ((0, 30), (0.6, 39.5), 2.4))):
        m.seg(f'branch_{k}', T, a, b, w, 1.6, 3, 5, tip=True)
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
