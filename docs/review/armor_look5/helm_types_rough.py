# 兜の型 9 つの place_cube 用 JSON を helms/<型>.json に書く（数値を出すだけ。置くのは Blockbench）
# 頭の箱は x,z ±8・y 48〜64（角まで 11.3）。壁は「縦横 ra・斜め rd」の八角で頭を必ず覆い、鉢は y64 より上で閉じる。
import json, math, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
r2 = lambda v: round(v, 2)
SQ = math.sqrt(2)
RA, RD = 10.8, 13.0   # 標準の壁（内側 9.2 / 11.4）


def widths(ra, rd, pad=0.4):
    return 2 * (rd * SQ - ra) + pad, SQ * (2 * ra - rd * SQ) + pad


def plate(name, c, R, w, t, ry, L, th, down=False):
    """前（-z）に置いた板を X に t 傾け、Y に ry 回す。down なら原点から下へ垂れる"""
    cx, cy, cz = c
    ry = ry if ry <= 180 else ry - 360
    tr = math.radians(t)
    yb, zb = cy - R * math.sin(tr), cz - R * math.cos(tr)   # 回したあと根元が (cy, -R) に来るよう前もってずらす
    y0, y1 = (yb - L, yb) if down else (yb, yb + L)
    return {"name": name, "from": [r2(cx - w / 2), r2(y0), r2(zb)], "to": [r2(cx + w / 2), r2(y1), r2(zb + th)],
            "rotation": [t, ry, 0], "origin": [cx, cy, cz]}


def oct8(name, c, ra, rd, t, L, th, ks=range(8), down=False, Ld=None, wpad=0.4):
    """八角の輪。t>0 は上がすぼまる（down なら下が開く）"""
    wa, wd = widths(ra, rd, wpad)
    out = []
    for k in ks:
        if k % 2 == 0:
            out.append(plate(f"{name}{k}", c, ra, wa, t, 45 * k, L, th, down))
        else:
            out.append(plate(f"{name}{k}", c, rd, wd, t, 45 * k, Ld if Ld is not None else L, th, down))
    return out


def reg8(name, c, R, t, L, th, w=None, ks=range(8), down=False):
    """正八角の輪（つば・飾りの帯・冠の歯）"""
    w = w if w is not None else 2 * R * math.tan(math.pi / 8) + 0.3
    return [plate(f"{name}{k}", c, R, w, t, 45 * k, L, th, down) for k in ks]


def step(c, ra, rd, t, L, down=False):
    """輪の先の原点と半径（次の段を続けるため）"""
    s, co = math.sin(math.radians(t)), math.cos(math.radians(t))
    cx, cy, cz = c
    if down:
        return (cx, r2(cy - L * co), cz), r2(ra + L * s), r2(rd + L * s)
    return (cx, r2(cy + L * co), cz), r2(ra - L * s), r2(rd - L * s)


def dome(name, c, ra, rd, steps, th=1.4):
    """steps = [(t, L), ...] で上へすぼめ、最後に八角の蓋"""
    out = []
    for i, (t, L) in enumerate(steps):
        out += oct8(f"{name}{i}_", c, ra, rd, t, L, th)
        c, ra, rd = step(c, ra, rd, t, L)
    a = max(ra, 1.0)
    out += [box(f"{name}cap", [-a, c[1] - 1.4, -a], [a, c[1] + 0.2, a]),
            box(f"{name}cap45", [-a, c[1] - 1.4, -a], [a, c[1] + 0.2, a], [0, 45, 0], [0, c[1], 0])]
    return out, c, ra, rd


def box(name, f, t, rot=None, org=None):
    e = {"name": name, "from": [r2(v) for v in f], "to": [r2(v) for v in t]}
    if rot:
        e["rotation"] = rot
        e["origin"] = org
    return e


def front_mark(name, c, R, t, x0, x1, y0, y1, d=0.3):
    """前の板（原点 c、半径 R、傾き t）の表に貼る細い箱（のぞき穴・縁）"""
    return box(name, [x0, y0, -R - d], [x1, y1, -R + 0.1], [t, 0, 0], list(c))


W0 = (0, 47.2, 0)
H = {}

# A 円錐の尖り兜（鼻当て・リブ・頂の玉）
d, top, _, _ = dome('ac', (0, 64.6, 0), RA, RD, [(26, 11), (52, 9)])
H['A_cone'] = (oct8('aw', W0, RA, RD, 0, 17.6, 1.6) + d +
               reg8('b_ar', (0, 64.6, 0), RD + 0.4, 26, 13.4, 0.7, w=0.9, ks=[1, 3, 5, 7]) +
               oct8('b_ab', (0, 47.0, 0), RA + 0.4, RD + 0.4, 0, 1.4, 0.8) +
               [box('b_nasal', [-0.9, 48.4, -11.8], [0.9, 63, -10.6]), box('b_atop', [-1, top[1] - 0.6, -1], [1, top[1] + 2.6, 1])])

# B 鉄帽（低い丸い鉢＋広く垂れるつば）
d, top, _, _ = dome('bd', (0, 64.6, 0), RA, RD, [(56, 7.6)])
H['B_kettle'] = (oct8('bw', W0, RA, RD, 0, 17.6, 1.6) + d +
                 reg8('bbr', (0, 57.6, 0), 19.4, 80, 8.6, 1.4) + reg8('b_bbe', (0, 56.0, 0), 19.6, 0, 1.6, 0.6) +
                 oct8('b_bband', (0, 57.8, 0), RA + 0.4, RD + 0.4, 0, 1.8, 0.8) +
                 [box('b_btop', [-1.4, top[1] - 0.4, -1.4], [1.4, top[1] + 1.6, 1.4])])

# C 裾の広がる大兜（鐘形・のぞき穴・縦の筋）
cw = (0, 44.6, 0)
d, top, _, _ = dome('cd', (0, 64.4, 0), 10.3, 13.3, [(52, 8.4)])
H['C_bell'] = (oct8('cw', cw, 13.4, 16.4, 9, 20, 1.6, Ld=20) + d +
               oct8('b_cb', (0, 44.4, 0), 13.8, 16.8, 9, 1.4, 0.8) +
               [front_mark('d_cslit', cw, 13.4, 9, -6.4, 6.4, 55.2, 56.8), front_mark('d_cslit2', cw, 13.4, 9, -0.7, 0.7, 50, 55.2),
                front_mark('b_cvert', cw, 13.4, 9, -0.6, 0.6, 57, 64.2)])

# D サレット（丸い鉢・後ろへ長く垂れる尾・のぞき穴・稜）
d, top, _, _ = dome('dd', (0, 64.6, 0), RA, RD, [(42, 7.4), (70, 3.4)])
o2, a2, b2 = step((0, 51.0, 0), RA + 0.2, RD + 0.2, 38, 8, down=True)
H['D_sallet'] = (oct8('dw', W0, RA, RD, 0, 17.6, 1.6) + d +
                 oct8('dt1', (0, 51.0, 0), RA + 0.2, RD + 0.2, 38, 8, 1.4, ks=[3, 4, 5], down=True) +
                 oct8('dt2', o2, a2, b2, 58, 7.4, 1.4, ks=[3, 4, 5], down=True) +
                 [front_mark('d_dslit', W0, RA, 0, -6, 6, 55.2, 56.6), box('b_dbrow', [-7.6, 56.6, -11.4], [7.6, 57.8, -10.6]),
                  box('b_dcomb', [-0.7, 69.4, -6], [0.7, 72.2, 6])])

# E モリオン（高い鉢・稜・前後に反るつば・頬当て・首当て）
we = (0, 57.4, 0)
d, top, _, _ = dome('ed', (0, 64.6, 0), RA, RD, [(20, 8.6), (58, 5.4)])
o2, a2, b2 = step(we, RA, RD, 0, 0)
H['E_morion'] = (oct8('ew', we, RA, RD, 0, 7.4, 1.6) + d +
                 [box('b_ecomb', [-0.6, 68, -8.6], [0.6, 76, 8.6]), box('b_ecomb2', [-0.6, 75.6, -5], [0.6, 79.6, 5]),
                  box('ebf', [-9.4, 57.4, -20], [9.4, 58.6, -10.4], [-20, 0, 0], [0, 58, -10.4]),
                  box('ebb', [-9.4, 57.4, 10.4], [9.4, 58.6, 20], [20, 0, 0], [0, 58, 10.4]),
                  box('ebr', [10.4, 57.4, -8], [15, 58.6, 8]), box('ebl', [-15, 57.4, -8], [-10.4, 58.6, 8]),
                  box('b_ebfe', [-9.6, 57.2, -20.4], [9.6, 58.8, -19.4], [-20, 0, 0], [0, 58, -10.4]),
                  box('b_ebbe', [-9.6, 57.2, 19.4], [9.6, 58.8, 20.4], [20, 0, 0], [0, 58, 10.4])] +
                 oct8('c_ech', we, RA, RD, 6, 9.6, 1.4, ks=[1, 2, 6, 7], down=True) +
                 oct8('ena', we, RA, RD, 10, 9.4, 1.4, ks=[3, 4, 5], down=True))

# F 兜（張り出したしころ・吹き返し・鍬形）
d, top, _, _ = dome('fb', (0, 64.6, 0), RA, RD, [(38, 7.6), (68, 4)])
s = []
c, a, b = (0, 57.0, 0), RA + 0.3, RD + 0.3
for i, t in enumerate([38, 46, 54]):
    s += oct8(f'fs{i}', c, a, b, t, 5.2, 1.2, ks=[2, 3, 4, 5, 6], down=True)
    c, a, b = step(c, a, b, t, 4.6, down=True)
H['F_kabuto'] = (oct8('fw', W0, RA, RD, 0, 17.6, 1.6, ks=range(1, 8)) + oct8('fwf', (0, 58.6, 0), RA, RD, 0, 6.2, 1.6, ks=[0]) + d + s +
                 oct8('b_fse', (c[0], c[1] + 4.6 * math.cos(math.radians(54)), 0), a - 4.6 * math.sin(math.radians(54)) + 0.4,
                      b - 4.6 * math.sin(math.radians(54)) + 0.4, 54, 1.0, 0.8, ks=[2, 3, 4, 5, 6], down=True) +
                 [box('ffk_r', [10.0, 53, -13.4], [11.8, 61, -8.2], [0, -38, 0], [10.9, 57, -10.8]),
                  box('ffk_l', [-11.8, 53, -13.4], [-10.0, 61, -8.2], [0, 38, 0], [-10.9, 57, -10.8]),
                  box('b_fkw_r', [0.4, 60, -12.2], [1.6, 76, -11.2], [0, 0, -24], [0, 60, -11.6]),
                  box('b_fkw_l', [-1.6, 60, -12.2], [-0.4, 76, -11.2], [0, 0, 24], [0, 60, -11.6]),
                  box('b_fbrow', [-9, 58.4, -11.6], [9, 59.6, -10.6]), box('g_fstar', [-1.2, 60.4, -12.4], [1.2, 62.8, -11.2], [0, 0, 45], [0, 61.6, -11.8])])

# G 高い冠（閉じた兜＋冠の歯＋中央の尖塔）
d, top, _, _ = dome('gd', (0, 64.6, 0), RA, RD, [(80, 6.2)])
H['G_crown'] = (oct8('gw', W0, RA, RD, 0, 17.6, 1.6) + d +
                [front_mark('d_gslit', W0, RA, 0, -4.6, 4.6, 55.4, 57), front_mark('d_gslit2', W0, RA, 0, -0.6, 0.6, 49, 55.4)] +
                reg8('b_gcr', (0, 64.6, 0), 13.6, -12, 10, 1.0, w=3.4) + reg8('b_gband', (0, 63.8, 0), 13.8, 0, 2.2, 0.8) +
                [box('b_gsp', [-1.1, 65, -1.1], [1.1, 82, 1.1]), box('g_ggem', [-1.6, 80.6, -1.6], [1.6, 83.8, 1.6], [45, 45, 0], [0, 82.2, 0])])

# H 背の高い頭巾（前が開き、頂が後ろへ長く尖って垂れる）
d, top, _, _ = dome('ht', (0, 64.6, 0), RA, RD, [(28, 6.4)])
H['H_hood'] = (oct8('hw', W0, RA, RD, 0, 17.6, 1.6, ks=range(1, 8)) + oct8('hwf', (0, 59.6, 0), RA, RD, 0, 5.2, 1.6, ks=[0]) + d +
               [box('htp1', [-5, 68, 0], [5, 76, 8], [-34, 0, 0], [0, 68, 4]),
                box('htp2', [-3.2, 73.6, 6], [3.2, 81, 11.6], [-56, 0, 0], [0, 73.6, 8.8]),
                box('htp3', [-1.6, 77, 11.6], [1.6, 82, 15], [-78, 0, 0], [0, 77, 13.3]),
                box('c_hrim_r', [7.4, 47.2, -11.6], [9.6, 61, -10.4]), box('c_hrim_l', [-9.6, 47.2, -11.6], [-7.4, 61, -10.4]),
                box('c_hrim_t', [-9.6, 59.4, -11.8], [9.6, 61.8, -10.4])])

# I 仮面の兜と低い冠（丸く大きな鉢・前に仮面）
RI, RID = 12.0, 13.6
d, top, _, _ = dome('id', (0, 64.6, 0), RI, RID, [(36, 6.2), (72, 3.2)])
H['I_mask'] = (oct8('iw', W0, RI, RID, 0, 17.6, 1.6) + d +
               [box('imask', [-7.6, 49, -13.8], [7.6, 62.4, -12]), box('inose', [-1.6, 51, -15.4], [1.6, 59, -12.4], [0, 45, 0], [0, 55, -13.6]),
                box('g_ieye_r', [1.8, 57, -14.1], [5.6, 58.2, -13.7]), box('g_ieye_l', [-5.6, 57, -14.1], [-1.8, 58.2, -13.7]),
                box('b_imr', [-7.8, 48.8, -14.2], [7.8, 49.6, -13.6])] +
               reg8('b_icr', (0, top[1] - 1.0, 0), 8.6, 0, 1.8, 1.0) + reg8('b_icp', (0, top[1] + 0.8, 0), 8.6, -10, 4.4, 1.0, w=2.2) +
               [box('g_igem', [-1.4, top[1] + 0.6, -10.2], [1.4, top[1] + 3.4, -9.2], [0, 0, 45], [0, top[1] + 2, -9.7])])

for k, v in H.items():
    names = [e['name'] for e in v]
    assert len(names) == len(set(names)), k
    json.dump(v, open(f'{OUT}/{k}.json', 'w'), separators=(',', ':'))
    print(k, len(v))
