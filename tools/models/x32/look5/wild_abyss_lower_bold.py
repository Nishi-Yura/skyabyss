# -*- coding: utf-8 -*-
"""野・深淵下（look5・個性版）。深淵の下層（雷の落ちる黒い底・古い機械の墓場）で拾う 18 品（叙）。
色: 底の黒鉄（umbra 紫がかった黒の 1 系統）、差し色は錆びた真鍮（brass 緑青の金）、光は底の雷（plasma 淡い紫白）を品ごとに一か所。
古い機械（歯車・砂時計・望遠筒）の部品と、雷に焼かれた硝子。光は割れ目・芯・珠など、品ごとに場所を変える。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lib  # noqa: E402
from lib import Model  # noqa: E402

lib.RAMPS.update({
    'umbra': ['#0c0812', '#140e1c', '#1e1628', '#2a1f36', '#382946', '#473558', '#58436c', '#6c5482', '#82689a', '#9c80b4'],
    'brass': ['#3c3a1e', '#5e5a2c', '#82783c', '#a89a52', '#c8bc74', '#e6dca2'],
    'plasma': ['#4a1e8a', '#7034c4', '#9a5ef0', '#c49aff', '#e4ccff', '#f8f0ff'],
})
U, B, P = 'umbra', 'brass', 'plasma'


def M(name):
    return Model(name, accent=B, glowmat=P)


def coil_grip(m, y0, y1, r=1.5):
    """黒い革を巻いた握り。上下は真鍮の太い輪（機械の継ぎ手）"""
    m.box('grip', U, [-r, y0, -r], [r, y1, r], grad=(y0, 3, y1, 5), wrap=(y0, y1))
    m.box('joint_d', B, [-r - 0.45, y0 - 1.25, -r - 0.45], [r + 0.45, y0, r + 0.45], 3)
    m.box('joint_u', B, [-r - 0.45, y1, -r - 0.45], [r + 0.45, y1 + 1.25, r + 0.45], 4)


def staff(m, top, r=1.1):
    """杖の柄: 黒い革の握りと黒鉄の軸。軸の上の端に真鍮の継ぎ手"""
    coil_grip(m, -14, 2, 1.3)
    m.box('shaft', U, [-r, 3.25, -r], [r, top, r], grad=(3.25, 3, top, 5))
    m.box('collar', B, [-r - 0.4, top - 1.25, -r - 0.4], [r + 0.4, top, r + 0.4], 4)


def haft(m, top, r=1.2):
    """鎚・斧の柄"""
    coil_grip(m, -6, 6)
    m.box('shaft', U, [-r, 7.25, -r], [r, top, r], grad=(7.25, 3, top, 5))


def gear(m, name, cx, cy, r, d, n, mat=B, t=3):
    """柄と同じ面に立つ歯車（縦横の二枚の板と、縁の歯）"""
    m.box(f'{name}', mat, [cx - r, cy - r + r * 0.3, -d], [cx + r, cy + r - r * 0.3, d], grad=(cy - r, t - 1, cy + r, t + 1))
    m.box(f'{name}_v', mat, [cx - r + r * 0.3, cy - r, -d], [cx + r - r * 0.3, cy + r, d], grad=(cy - r, t - 1, cy + r, t + 1))
    for k in range(n):
        a = math.radians(k * 360 / n + 180 / n)
        x, y = cx + math.cos(a) * (r + 0.35), cy + math.sin(a) * (r + 0.35)
        m.box(f'{name}_tooth_{k}', mat, [x - 0.8, y - 0.8, -d + 0.1], [x + 0.8, y + 0.8, d - 0.1], t)


def spear_haft(m, top):
    m.box('grip', U, [-1.5, -16, -1.5], [1.5, 0, 1.5], grad=(-16, 2, 0, 4), wrap=(-16, 0))
    m.box('butt', B, [-1.9, -18.5, -1.9], [1.9, -16, 1.9], 3)
    m.seg('butt_tip', B, (0, -18.5), (0, -20.5), 2, 1.1, 3, 5, tip=True)
    m.box('haft', U, [-1.25, 0, -1.25], [1.25, top, 1.25], grad=(0, 3, top, 5))


def drop_bell(m, name, x, y, z, mat=B):
    """雨粒の形の鈴（下が丸く上が尖る）。y は鈴の下の端"""
    m.box(name, mat, [x - 0.75, y, z - 0.75], [x + 0.75, y + 2, z + 0.75], grad=(y, 3, y + 2, 5) if mat == B else None)
    m.seg(f'{name}_top', mat, (x, y + 2), (x, y + 3), 1, 1, 4, 5, tip=True, zc=z)


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


# ================================================================ 弓

def wild_calling_bow():
    """呼び声の弓（エピック）: 弓腕そのものが真鍮の角笛。握りから先へ太りながら反り、管の継ぎ目に濃い帯。
    弓の先で口が開いて奥が雷の色に光り、口の上に横に寝た雷の色の光の輪が三段、上ほど大きく淡く重なって、音が広がる"""
    m = M('wild_calling_bow')
    m.box('grip', U, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 3, 20.5, 5), wrap=(9.5, 20.5))
    arm = [(12, 21), (13.5, 27), (13.5, 33), (11.5, 38.5), (8, 42), (4.5, 44)]
    c0 = (4, 44.75)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P_ = lambda q, s=sign: mirror(q, s)
        m.chain(f'horn_{n}', B, [P_(q) for q in arm], [2.25, 2.75, 3.25, 4, 4.75], 1.4, 2, 5, tip=False)
        for k in range(1, 5):
            q = P_(arm[k])
            w = 1.6 + k * 0.45
            m.box(f'ring_{n}{k}', B, [q[0] - w, q[1] - 0.35, -1.65 - k * 0.1], [q[0] + w, q[1] + 0.35, 1.65 + k * 0.1], 1)
        c = P_(c0)
        m.box(f'mouth_{n}', B, [c[0] - 3, c[1] - 1, -2.5], [c[0] + 3, c[1] + 1, 2.5], 5)
        m.box(f'mouth_in_{n}_glow', P, [c[0] - 2, c[1] - 1.05 if sign < 0 else c[1] + 0.2, -1.7], [c[0] + 2, c[1] - 0.2 if sign < 0 else c[1] + 1.05, 1.7], 4)
        # 音の輪: 横に寝た四角い輪。上ほど大きく、細く、淡く。角は一段欠いて丸みを出す
        for k in range(3):
            y = P_((0, 46.5 + k * 1.75))[1]
            h = 2.75 + k * 1.4
            t = 0.55 - k * 0.05
            cx, tone = c0[0], 4 - k
            cut = 0.5 + k * 0.25
            for sd, (a, b) in (('a', (-h, -h + t)), ('b', (h - t, h))):
                m.box(f'wave_{n}{k}{sd}_glow', P, [cx - h + cut, y - t / 2, a], [cx + h - cut, y + t / 2, b], tone)
            for sd, (a, b) in (('c', (cx - h, cx - h + t)), ('d', (cx + h - t, cx + h))):
                m.box(f'wave_{n}{k}{sd}_glow', P, [a, y - t / 2, -h + cut], [b, y + t / 2, h - cut], tone)
            for j, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
                x0, z0 = cx + sx * (h - cut), sz * (h - cut)
                m.box(f'wave_{n}{k}k{j}_glow', P, [min(x0, x0 + sx * (cut - t / 2)) - t / 2 * (sx < 0), y - t / 2, min(z0, z0 + sz * (cut - t / 2)) - t / 2 * (sz < 0)],
                      [max(x0, x0 + sx * (cut - t / 2)) + t / 2 * (sx > 0), y + t / 2, max(z0, z0 + sz * (cut - t / 2)) + t / 2 * (sz > 0)], tone)
    strings(m, (3.25, 43.75), (3.25, -13.75))
    return m

def wild_flash_bow():
    """稲光の弓（レア）: 弓腕そのものが太く折れ曲がる黒鉄の稲妻。二つの折れ目を真鍮の留め金が締め、
    上の弓腕の先だけが雷の色に光る"""
    m = M('wild_flash_bow')
    m.box('grip', U, [10.25, 9.5, -1.9], [13.75, 20.5, 1.9], grad=(9.5, 2, 20.5, 4), wrap=(9.5, 20.5))
    for n, y in (('u', 20.25), ('d', 8.25)):
        m.box(f'band_{n}', B, [9.75, y, -2.3], [14.25, y + 1.5, 2.3], 4)
    arm = [(12, 21), (13, 27), (12.5, 33), (10.5, 38.5), (7.5, 42.5), (4.5, 44.5)]
    zz = [(12, 21), (15, 26), (11, 30), (14.5, 35.5), (9, 39), (10.5, 43.5), (5, 46)]
    for sign, n in ((1, 'u'), (-1, 'd')):
        P_ = lambda q, s=sign: mirror(q, s)
        m.chain(f'bolt_{n}', U, [P_(q) for q in zz], [3.6, 3.4, 3.2, 3, 2.6, 2.2], 1.4, 3, 6, tip=False, over=0.9)
        for k in (2, 4):
            q = P_(zz[k])
            m.box(f'clasp_{n}{k}', B, [q[0] - 1.6, q[1] - 0.8, -1.55], [q[0] + 1.6, q[1] + 0.8, 1.55], 4)
    m.seg('tip_glow', P, (6.5, 45.25), (4.5, 46.5), 2.2, 1.45, 4, 5, tip=True)
    strings(m, (5, 46), (5, -16))
    return m


def wild_scope_longbow():
    """遠見の大弓（エピック）: 弓腕そのものが伸び縮みする望遠筒。握りから先へ一段ずつ細る黒鉄の筒が重なり、
    継ぎ目ごとに真鍮の襟。先のレンズが雷の色に光る。握りの前には真鍮の腕で横向きの照準の輪（板の絵、十字と目盛り）が立ち、射手がのぞく窓になる"""
    m = M('wild_scope_longbow')
    m.box('grip', U, [10.25, 9, -1.9], [13.75, 21, 1.9], grad=(9, 3, 21, 5), wrap=(9, 21))
    arm = [(12, 21), (12.5, 31), (11, 40), (8, 48), (4.5, 53)]
    cv = lib.Canvas(-6, 9, 6, 21)

    def f(z, y, cur):
        dz, dy = z, y - 15
        r = math.hypot(dz, dy)
        if 3.6 < r < 4.2:
            return (P, 4)
        if r < 3.6 and (abs(dz) < 0.3 or abs(dy) < 0.3) and r > 1.26:
            return (P, 3)
        if 4.2 <= r < 5.1 and (abs(dz) < 0.3 or abs(dy) < 0.3):
            return (B, 5)
        return None
    cv.paint(f)
    cv.put(m, 'sight_glow', at=17.5, plane='zy')
    m.box('sight_arm', B, [13.75, 14.5, -0.5], [17.5, 15.5, 0.5], 4)
    for sign, n in ((1, 'u'), (-1, 'd')):
        P_ = lambda q, s=sign: mirror(q, s)
        for k in range(len(arm) - 1):
            a, b = arm[k], arm[k + 1]
            w = 4 - k * 0.6
            m.seg(f'tube_{n}{k}', U, P_(a), P_(b), w, w / 2.2, 4 + k * 0.6, 6 + k * 0.5, over=0.2)
            m.seg(f'collar_{n}{k}', B, P_(b), P_((b[0] + (a[0] - b[0]) * 0.12, b[1] + (a[1] - b[1]) * 0.12)), w + 0.8, w / 2.2 + 0.4, 4, 5, over=0)
        q = P_((4.5, 53))
        m.box(f'lens_{n}_glow', P, [q[0] - 1, q[1] - 0.5, -1], [q[0] + 1, q[1] + 0.5, 1], 4)
    strings(m, (4.25, 53), (4.25, -23))
    return m


# ================================================================ 剣・大剣・短剣

def wild_fulgurite_sword():
    """（レア、2026-11-21 に作り直し・案 A）閃電岩の剣: 雷に焼かれて固まった砂の管が、少しずつ左右へ振れながら伸びる刃。根のような短い枝が三本、
    刃の芯の割れ目だけが雷の色に光る（表裏に通る）
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ硝子の管を、左右へ振れながら先へ細る一本の帯のメッシュに替え、三本の枝を前後へ互い違いに傾けた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_fulgurite_sword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_fulgurite_sword')
    coil_grip(m, -6, 6)
    m.box('pommel', B, [-1.5, -9, -1.5], [1.5, -7.25, 1.5], 4)
    m.box('guard', B, [-3, 7.25, -1.6], [3, 8.75, 1.6], grad=(7.25, 3, 8.75, 5))
    m.chain('blade', U, [(0, 8.5), (0.6, 14), (-0.5, 20), (0.5, 26), (0, 32)], [3.4, 3.0, 2.6, 2.0], 1.8, 5, 8, tip=True)
    for k, (a, b) in enumerate((((1.2, 12.5), (3.6, 15.75)), ((-1.2, 18), (-3.5, 21.25)), ((1.1, 23.5), (3.1, 26.25)))):
        m.seg(f'branch_{k}', U, a, b, 1.1, 0.8, 6, 8, tip=True)
    m.box('vein_glow', P, [-0.3, 13, -0.95], [0.3, 24, 0.95], grad=(13, 2, 24, 4))
    return m


def wild_chain_greatsword():
    """（エピック、2026-11-21 に作り直し・案 B）鎖巻きの大剣: 刃の背筋に鎖の輪が縦に連なって刃先まで走る（表裏とも）。鍔の真ん中に真鍮の錠前が下がり、
    鍵穴が雷の色。鍔の両端から短い鎖の端が垂れる
    ※ 2026-11-21 に Blockbench で手を入れた（段の刃先を四角錐のメッシュに替え、錠前（と鍵穴の光）を鍔から 8 度傾けて吊り、縦の鎖の輪を一つおきに ±6 度ひねった）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_chain_greatsword.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_chain_greatsword')
    coil_grip(m, -8, 8, 1.6)
    m.box('guard', U, [-5.5, 9, -2], [5.5, 11.5, 2], grad=(9, 4, 11.5, 6))
    m.box('blade', U, [-3.5, 11.5, -1.25], [3.5, 40, 1.25], grad=(11.5, 4, 40, 7))
    for k, w in enumerate((2.75, 1.75, 0.75)):
        m.box(f'tip_{k}', U, [-w, 40 + k * 1.75, -1.2 + k * 0.15], [w, 41.75 + k * 1.75, 1.2 - k * 0.15], 7 + k)
    for k in range(7):
        y = 13 + k * 3.75
        if k % 2 == 0:
            m.box(f'link_{k}', B, [-1, y, -1.6], [1, y + 3, 1.6], 4)
        else:
            m.box(f'link_{k}', B, [-0.45, y - 0.25, -1.75], [0.45, y + 3.25, 1.75], 3)
    m.box('lock', B, [-1.75, 5.75, -1.25], [1.75, 9, 1.25], grad=(5.75, 3, 9, 5))
    m.box('lock_glow', P, [-0.4, 6.75, -1.35], [0.4, 8, 1.35], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.chain(f'hang_{n}', B, [(sd * 5, 9), (sd * 5.5, 6.5), (sd * 5, 4)], [0.8, 0.8], 0.8, 3, 4, tip=False)
    return m


def wild_fishhook_knife():
    """（エピック、2026-11-21 に作り直し・案 A）釣り針の小刀: 刃の先が大きな釣り針のように後ろへ返り、返しが一つ立つ。返しの付け根が雷の色。
    柄頭には糸を通す真鍮の輪、握りに真鍮の糸が二巻き
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ曲がりを、内の縁が薄い刃で先へ細る一本の帯のメッシュに替え、握りの糸の巻きを ±6 度、返しを少し傾けた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_fishhook_knife.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_fishhook_knife')
    coil_grip(m, -4, 4, 1.25)
    m.box('eye', B, [-1, -7, -0.35], [1, -5.25, 0.35], 4)
    for k, y in enumerate((-2, 1)):
        m.box(f'line_{k}', B, [-1.45, y, -1.45], [1.45, y + 0.6, 1.45], 4)
    m.box('bolster', U, [-1.6, 5.25, -1.1], [1.6, 6.5, 1.1], 5)
    m.box('shank', U, [-0.8, 6.5, -0.7], [0.8, 15, 0.7], grad=(6.5, 5, 15, 7))
    m.chain('bend', U, [(0, 15), (-0.75, 17.5), (-2.75, 18.5), (-4.5, 17), (-4.75, 14.5)], [1.5, 1.4, 1.25, 1.1], 0.7, 6, 8, tip=True)
    m.seg('barb', U, (-4.5, 15.5), (-3.25, 13.5), 0.8, 0.6, 7, 8, tip=True)
    m.box('barb_glow', P, [-5, 15.25, -0.75], [-4, 16, 0.75], 4)
    return m


def wild_spark_dagger():
    """（レア、2026-11-21 に作り直し・案 A）火花の短剣: 細い三角の刃の根元に、真鍮の二本の電極が角のように立ち、その間に雷の火花が一筋渡る
    ※ 2026-11-21 に Blockbench で手を入れた（刃を菱形の断面で尖る一枚のメッシュに替え、二本の電極を前後へ開いた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_spark_dagger.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_spark_dagger')
    coil_grip(m, -4, 4, 1.25)
    m.box('guard', U, [-2.5, 5.25, -1], [2.5, 6.5, 1], 5)
    m.seg('blade', U, (0, 6.5), (0, 19), 2.5, 1, 6, 9, tip=True)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'electrode_{n}', B, (sd * 2, 6), (sd * 3.25, 10), 0.9, 0.9, 3, 5)
    m.box('arc_glow', P, [-3.1, 9.5, -0.3], [3.1, 10.1, 0.3], 4)
    return m


def wild_ash_sickle_claw():
    """（エピック、2026-11-21 に作り直し・案 B）灰鎌の爪: 籠手から一枚の大きな鎌の刃が拳の上を大きく回り込んで前（-x）へ垂れ、内にもう一枚小さな刃。
    拳の真鍮の当て板に雷の色の細い窓（表裏）
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ大小の鎌を、内の縁が刃で先へ細る帯のメッシュ二枚に替えた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_ash_sickle_claw.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ash_sickle_claw')
    coil_grip(m, -7, 3, 1.4)
    m.box('gauntlet', U, [-2.75, 3.25, -2.5], [2.75, 9, 2.5], grad=(3.25, 4, 9, 6))
    m.box('plate', B, [-2.25, 4, -2.8], [2.25, 8, 2.8], grad=(4, 3, 8, 5))
    m.box('plate_glow', P, [-1.5, 5.6, -2.9], [1.5, 6.4, 2.9], 4)
    m.seg('root', U, (0.5, 9), (1, 13), 2, 1, 5, 6)
    m.arc('sickle', U, (-3, 12.5), 5, 0, 170, 7, [2, 2, 1.8, 1.6, 1.4, 1.1, 0.8], 0.8, 6, 9, tip=True)
    m.arc('inner', U, (-2.25, 12.5), 2.5, 20, 150, 4, [1.1, 1, 0.9, 0.7], 0.6, 6, 8, tip=True)
    return m


def wild_abyss_mace():
    """（エピック、2026-11-21 に作り直し・案 B）深淵の鎚矛: 頭が真鍮の格子の籠。四本の縦の桟の間に雷の球が閉じ込められ、籠の外へ黒鉄の棘が
    左右・前後・上に突き出る
    ※ 2026-11-21 に Blockbench で手を入れた（棘を四角錐のメッシュ五本に替え、籠の四本の桟を互い違いに ±6 度ひねった）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_abyss_mace.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_abyss_mace')
    haft(m, 19)
    m.box('base', U, [-2.75, 19, -2.75], [2.75, 20.25, 2.75], 5)
    m.box('top', U, [-2.75, 26, -2.75], [2.75, 27.25, 2.75], 6)
    for k, (x, z) in enumerate(((-2.4, -2.4), (2.4, -2.4), (-2.4, 2.4), (2.4, 2.4))):
        m.box(f'bar_{k}', B, [x - 0.4, 20.25, z - 0.4], [x + 0.4, 26, z + 0.4], 4)
    m.box('ball_glow', P, [-1.9, 20.25, -1.9], [1.9, 26, 1.9], grad=(20.25, 3, 26, 5))
    for sd in (-1, 1):
        m.seg(f'spike_x{sd + 1}', U, (sd * 1.8, 23.1), (sd * 5.5, 23.1), 1.5, 1.5, 6, 8, tip=True)
        m.box(f'spike_z{sd + 1}', U, [-0.6, 22.5, min(sd * 1.8, sd * 4.75)], [0.6, 23.75, max(sd * 1.8, sd * 4.75)], 7)
    m.seg('spike_top', U, (0, 27.25), (0, 30.5), 1.6, 1.6, 6, 8, tip=True)
    return m


def wild_cog_hammer():
    """（エピック、2026-11-21 に作り直し・案 C）歯車の大鎚: 大きな歯車と、その斜め上でかみ合う小さな歯車の二枚の頭。小さな歯車の軸が雷の色、
    大きな歯車の軸は黒鉄の帽子
    ※ 2026-11-21 に Blockbench で手を入れた（大小の歯車の縦横二枚の板を、十六角と十二角の円盤のメッシュに替えた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_cog_hammer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_cog_hammer')
    coil_grip(m, -7, 7, 1.6)
    m.box('shaft', U, [-1.3, 8.25, -1.3], [1.3, 21, 1.3], grad=(8.25, 3, 21, 5))
    gear(m, 'big', 0, 25.5, 4.5, 1.5, 8)
    m.box('big_hub', U, [-1.5, 24, -1.7], [1.5, 27, 1.7], 6)
    gear(m, 'small', 4, 31.25, 2.5, 1.2, 6, t=4)
    m.box('small_glow', P, [3.25, 30.5, -1.3], [4.75, 32, 1.3], 4)
    return m


# ================================================================ 熔け斧（レア）


def wild_molten_axe():
    """（レア、2026-11-21 に作り直し・案 A）熔け斧: 黒鉄の半月の刃が熔けかけて、刃の下から雫が三つ垂れる。刃の縁だけが雷の色の熱の筋
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ半月の刃を、外の縁が薄い刃の帯のメッシュに替え、三つの雫を違う向きに揺らした）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_molten_axe.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_molten_axe')
    haft(m, 25)
    m.box('cheek', U, [1, 18, -1.3], [4.5, 25, 1.3], grad=(18, 4, 25, 6))
    m.arc('blade', U, (3, 21.5), 5, -80, 80, 5, [2.5, 3, 3.25, 3, 2.5], 1.2, 5, 7)
    m.arc('edge_glow', P, (3, 21.5), 5.9, -60, 60, 4, [0.7, 0.7, 0.7, 0.7], 1.3, 3, 4)
    for k, (x, y, h) in enumerate(((5.5, 17.25, 2.5), (7.25, 18.5, 1.75), (3.75, 17.5, 1.5))):
        m.box(f'drip_{k}', U, [x - 0.5, y - h, -0.6], [x + 0.5, y, 0.6], grad=(y - h, 4, y, 6))
    m.box('cap', B, [-1.6, 25, -1.6], [1.6, 26.5, 1.6], 4)
    return m


def wild_twin_spear():
    """（エピック、2026-11-21 に作り直し・案 B）双子の槍: 二本の細い穂が互いに巻き付くようにねじれ合って登り、先で一つの穂先に合わさる。
    分かれ目の真鍮の継ぎ手の芯が雷の色、継ぎ手の左右に小さな真鍮の耳
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ二本の穂を、本当にねじれ合う二本のらせんの管のメッシュに替え、穂先を四角錐に、耳を前後へ開いた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_twin_spear.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_twin_spear')
    spear_haft(m, 33)
    m.box('yoke', B, [-2.25, 33, -1.75], [2.25, 35, 1.75], grad=(33, 3, 35, 5))
    m.box('yoke_glow', P, [-0.6, 33.5, -1.85], [0.6, 34.5, 1.85], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'ear_{n}', B, (sd * 2.25, 34), (sd * 3.75, 35.5), 0.9, 0.9, 3, 5, tip=True)
        m.chain(f'strand_{n}', U, [(sd * 1.25, 35), (-sd * 1.5, 38.5), (sd * 1.5, 42), (-sd * 1, 45.5), (0, 47)],
                [1.2, 1.1, 1.0, 0.9], 1.0, 5, 7, tip=False)
    m.seg('point', U, (0, 46.5), (0, 51.5), 2.4, 1.2, 7, 9, tip=True)
    return m


def wild_ballspark_staff():
    """（レア、2026-11-21 に作り直し・案 B）球雷の杖: 杖の先に真鍮の巻き線が六段（雷の柱）、上に黒い平たい冠、冠の上に雷の球が浮かぶ
    ※ 2026-11-21 に Blockbench で手を入れた（六段の巻き線の輪を、五巻き半のらせんの管のメッシュに替え、冠を八角の皿のメッシュにした）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_ballspark_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_ballspark_staff')
    staff(m, 26)
    for k in range(6):
        y = 26 + k * 1.1
        m.box(f'coil_{k}', B, [-1.75, y, -1.75], [1.75, y + 0.7, 1.75], 3 + k % 2)
    m.box('core', U, [-1.2, 26, -1.2], [1.2, 32.6, 1.2], 5)
    m.box('crown', U, [-3.25, 32.6, -3.25], [3.25, 33.75, 3.25], grad=(32.6, 5, 33.75, 7))
    m.box('float_ball_glow', P, [-1.5, 35, -1.5], [1.5, 38, 1.5], grad=(35, 3, 38, 5))
    return m


def wild_deep_eye_staff():
    """（エピック、2026-11-21 に作り直し・案 B）深淵の眼の杖: 望遠鏡の絞りのような真鍮の輪の中に眼が一つ。輪の内に黒い羽根が六枚重なって瞳を囲み、
    瞳が雷の色。輪は二本の腕で杖に留まる
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ絞りの輪を一本の輪の帯のメッシュに替え、六枚の羽根を互い違いに前後へ傾けた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_deep_eye_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_deep_eye_staff')
    staff(m, 28)
    m.arc('ring', B, (0, 34), 4, 0, 360, 12, [1.1] * 12, 1.6, 3, 5)
    for k in range(6):
        a = math.radians(k * 60 + 15)
        m.seg(f'vane_{k}', U, (math.cos(a) * 3.6, 34 + math.sin(a) * 3.6), (math.cos(a + 1.2) * 1.4, 34 + math.sin(a + 1.2) * 1.4), 1.3, 1.2, 5, 7)
    m.box('pupil_glow', P, [-1.1, 32.9, -0.75], [1.1, 35.1, 0.75], 4)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.seg(f'arm_{n}', B, (sd * 0.5, 27.5), (sd * 2.6, 31), 0.9, 0.9, 3, 4)
    return m


def wild_hourglass_staff():
    """（エピック、2026-11-21 に作り直し・案 B）砂時計の杖: 杖の先の真鍮の縦の輪の中で、砂時計が横に寝ている（今まさに返される途中）。
    左の玉に雷の色の砂、輪の上に小さな歯車
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ輪を一本の輪の帯のメッシュに、二つの玉を横向きの回転体のメッシュに替え、玉・首・砂を 20 度傾けた（返される途中）。砂の光は玉の表裏から覗く大きさに縮めたので、この関数の数値とは違う）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_hourglass_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_hourglass_staff')
    staff(m, 28)
    m.arc('ring', B, (0, 33.5), 5, 0, 360, 12, [1] * 12, 1.2, 3, 5)
    m.box('axle', B, [-0.5, 33, -1.6], [0.5, 34, 1.6], 4)
    m.box('bulb_l', U, [-4.25, 32, -1.4], [-0.75, 35, 1.4], 6)
    m.box('sand_glow', P, [-4.3, 32, -1.45], [-2.5, 35, 1.45], 4)
    m.box('neck', U, [-0.75, 33.1, -0.45], [0.75, 33.9, 0.45], 6)
    m.box('bulb_r', U, [0.75, 32, -1.4], [4.25, 35, 1.4], 7)
    gear(m, 'gear', 0, 40, 1.6, 0.6, 6, t=4)
    return m


def wild_calming_censer():
    """（エピック、2026-11-21 に作り直し・案 A）鎮めの香炉: 杖の先が下へ鉤のように曲がり、短い鎖で丸い真鍮の香炉を吊る。香炉の格子窓から
    雷の色の煙火が漏れ、蓋に黒い摘み
    ※ 2026-11-21 に Blockbench で手を入れた（節をつないだ鉤を一本の帯のメッシュに、壺を八角の回転体のメッシュに替えた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_calming_censer.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_calming_censer')
    staff(m, 32)
    m.chain('crook', U, [(0, 31.5), (1.25, 34.5), (3.75, 35.25), (5.25, 33.5)], [2, 1.75, 1.5], 1.4, 5, 7, tip=False)
    m.box('chain', B, [5, 29.5, -0.2], [5.4, 33.25, 0.2], 3)
    m.box('lid', U, [3.5, 28.5, -1.75], [7, 29.5, 1.75], 6)
    m.box('knob', U, [4.85, 29.5, -0.4], [5.65, 30, 0.4], 7)
    m.box('pot', B, [3.25, 24.5, -2], [7.25, 28.5, 2], grad=(24.5, 2, 28.5, 4))
    m.box('window_glow', P, [3.15, 25.75, -1], [7.35, 27.25, 1], 4)
    m.box('foot', B, [4.25, 23.75, -1.25], [6.25, 24.5, 1.25], 2)
    return m


def wild_raincall_bell_staff():
    """（レア、2026-11-21 に作り直し・案 A）雨呼びの鈴杖: 杖の先に黒鉄の小さな傘が開き、傘の縁の四方から雨粒の形の鈴が下がる。
    手前の一粒だけが雷の色
    ※ 2026-11-21 に Blockbench で手を入れた（三段の傘を八角の円錐のメッシュに、四つの雨粒の鈴を六角の回転体（しずく）のメッシュに替えた）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_raincall_bell_staff.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_raincall_bell_staff')
    staff(m, 32)
    m.box('canopy_low', U, [-5, 32, -3], [5, 33, 3], 5)
    m.box('canopy_mid', U, [-3.5, 33, -2.25], [3.5, 34.25, 2.25], 6)
    m.box('canopy_top', U, [-1.75, 34.25, -1.25], [1.75, 35.25, 1.25], 7)
    m.seg('tip', B, (0, 35.25), (0, 37.5), 1.2, 1.2, 4, 5, tip=True)
    for k, (x, z) in enumerate(((-4.25, 0), (4.25, 0), (0, -2.4), (0, 2.4))):
        m.box(f'string_{k}', U, [x - 0.15, 30.5, z - 0.15], [x + 0.15, 32, z + 0.15], 3)
        drop_bell(m, f'drop_{k}' + ('_glow' if k == 2 else ''), x, 27.5, z, P if k == 2 else B)
    return m


def wild_effigy_totem():
    """（エピック、2026-11-21 に作り直し・案 B）人形の祈り柱: 機械の屑で組んだ案山子。頭が真鍮の歯車で軸が雷の色、腕は黒鉄の横木で両端からボルトが
    一本ずつ吊られ、胴に真鍮の板が括られる
    ※ 2026-11-21 に Blockbench で手を入れた（歯車の頭の縦横二枚の板を十二角の円盤のメッシュに替え、胸の板と鋲を斜めに 6 度傾けて括った）。メッシュの形と UV はスクリプト（al_gen.py）で作って MCP で置き、UV は書き出した .bbmodel にスクリプトで入れた。正本は tools/models/x32/wild_effigy_totem.bbmodel。この関数から prep し直すと手入れが消える"""
    m = M('wild_effigy_totem')
    coil_grip(m, -14, 2, 1.6)
    m.box('pillar', U, [-1.5, 3.25, -1.5], [1.5, 31, 1.5], grad=(3.25, 3, 31, 5))
    m.box('arms', U, [-6.5, 27, -0.9], [6.5, 28.5, 0.9], grad=(27, 5, 28.5, 7))
    m.box('chest', B, [-2.25, 22.5, -1.85], [2.25, 26.5, 1.85], grad=(22.5, 3, 26.5, 5))
    for k, y in enumerate((23.25, 25.25)):
        m.box(f'rivet_{k}', U, [-1.6, y, -1.95], [1.6, y + 0.6, 1.95], 6)
    for sd, n in ((-1, 'l'), (1, 'r')):
        m.box(f'cord_{n}', B, [sd * 5.75 - 0.15, 24.5, -0.15], [sd * 5.75 + 0.15, 27, 0.15], 3)
        m.box(f'bolt_{n}', U, [sd * 5.75 - 0.5, 21.5, -0.5], [sd * 5.75 + 0.5, 24.5, 0.5], 6)
        m.box(f'bolt_head_{n}', B, [sd * 5.75 - 0.9, 21, -0.9], [sd * 5.75 + 0.9, 21.75, 0.9], 4)
    gear(m, 'head', 0, 34, 2.75, 1, 8)
    m.box('head_glow', P, [-0.75, 33.25, -1.1], [0.75, 34.75, 1.1], 4)
    return m


ALL = [wild_calling_bow, wild_flash_bow, wild_scope_longbow, wild_fulgurite_sword, wild_chain_greatsword,
       wild_fishhook_knife, wild_spark_dagger, wild_ash_sickle_claw, wild_abyss_mace, wild_cog_hammer,
       wild_molten_axe, wild_twin_spear, wild_ballspark_staff, wild_deep_eye_staff, wild_hourglass_staff,
       wild_calming_censer, wild_raincall_bell_staff, wild_effigy_totem]

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for fn in ALL:
        if not want or fn.__name__ in want:
            fn().build('.')
