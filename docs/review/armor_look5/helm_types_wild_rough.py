# 頭の形から大きく外れる兜の型 8 つ（J〜Q）。helms.py の道具を借りる。数値を出すだけ。
import json, math, os, sys
_src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'helms.py'), encoding='utf8').read()
_src = _src[:_src.index("W0 = ")]
_argv = sys.argv; sys.argv = [_argv[0], _argv[1]]
exec(_src)
W0 = (0, 47.2, 0)


def bar(name, p0, p1, w, h=None):
    """p0 から p1 へ伸びる角棒（断面 w×h）"""
    h = h if h is not None else w
    d = [p1[i] - p0[i] for i in range(3)]
    L = math.sqrt(sum(v * v for v in d))
    u = [v / L for v in d]
    ry = -math.degrees(math.asin(max(-1, min(1, u[2]))))
    rz = math.degrees(math.atan2(u[1], u[0]))
    return {"name": name, "from": [r2(p0[0]), r2(p0[1] - h / 2), r2(p0[2] - w / 2)],
            "to": [r2(p0[0] + L + 0.3), r2(p0[1] + h / 2), r2(p0[2] + w / 2)], "rotation": [0, r2(ry), r2(rz)], "origin": [r2(v) for v in p0]}


def chain(name, pts, w0, w1):
    n = len(pts) - 1
    return [bar(f"{name}{i}", pts[i], pts[i + 1], w0 + (w1 - w0) * i / max(1, n - 1)) for i in range(n)]


def mirror(es):
    out = []
    for e in es:
        m = json.loads(json.dumps(e))
        m["name"] = e["name"] + "_m"
        f, t = e["from"], e["to"]
        m["from"] = [r2(-t[0]), f[1], f[2]]
        m["to"] = [r2(-f[0]), t[1], t[2]]
        if "rotation" in e:
            rx, ry, rz = e["rotation"]
            m["rotation"] = [rx, r2(-ry), r2(-rz)]
            m["origin"] = [r2(-e["origin"][0]), e["origin"][1], e["origin"][2]]
        out.append(m)
    return out


def shell(name='w', ra=RA, rd=RD, ks=range(8), steps=((40, 7.4), (72, 3.4))):
    d, top, _, _ = dome(name + 'd', (0, 64.6, 0), ra, rd, list(steps))
    return oct8(name, W0, ra, rd, 0, 17.6, 1.6, ks=ks) + d, top


H = {}

# J 獣の頭骨（前へ突き出た鼻づら・開いた顎の牙・後ろへ巻く大角）
s, top = shell('jw')
horn = chain('jh', [(9.6, 62, -2), (15, 66, 1), (19, 71, 6), (19.5, 76, 12), (16.5, 78, 16), (13, 76, 17.5)], 3.6, 1.4)
H['J_skull'] = (s + [box('jsn', [-6, 54, -22], [6, 61, -10.4]), box('jsn2', [-4.6, 52.4, -26], [4.6, 59.4, -21.6]),
                     box('b_jnose', [-3.6, 59.4, -25.6], [3.6, 60.4, -14]),
                     box('d_jeye_r', [2.6, 58, -11.2], [6.4, 61, -10.6]), box('d_jeye_l', [-6.4, 58, -11.2], [-2.6, 61, -10.6]),
                     box('jjaw', [-6.4, 45.4, -19], [6.4, 48.4, -10.4], [12, 0, 0], [0, 48, -10.4])] +
               [box(f'b_jt{i}', [x - 0.6, 50.6, -24.6], [x + 0.6, 53, -23.4]) for i, x in enumerate([-3.4, 3.4])] +
               [box(f'b_jt{i + 2}', [x - 0.5, 51.4, -21.0 + 0.0 * i], [x + 0.5, 54, -20.0]) for i, x in enumerate([-5.4, 5.4])] +
               horn + mirror(horn))

# K 鳥の嘴（前へ長い嘴・頭の後ろに扇の羽根の冠）
s, top = shell('kw', steps=((34, 7), (70, 3.6)))
fe = []
for i, (a, L) in enumerate([(-60, 13), (-35, 16), (-12, 18), (12, 18), (35, 16), (60, 13)]):
    r = math.radians(a)
    p0 = (math.sin(r) * 3, 66, 7)
    p1 = (math.sin(r) * L, 66 + math.cos(r) * L * 0.9, 7 + L * 0.55)
    fe.append(bar(f'kf{i}', p0, p1, 1.0, 3.4))
    fe.append(bar(f'b_kfe{i}', p1, (p1[0] * 1.08, p1[1] + 1.6, p1[2] + 1.0), 1.2))
H['K_beak'] = (s + [bar('kb1', (0, 56, -10.6), (0, 52, -28), 7.0, 6.4), bar('kb2', (0, 54.6, -26), (0, 49.4, -36), 4.0, 3.6), bar('kb0', (0, 57, -10.6), (0, 55, -18), 9, 8),
                    bar('b_kbl', (0, 52.6, -12), (0, 50.4, -28), 7.4, 1.0),
                    box('g_keye_r', [3.4, 57.6, -11.2], [6.4, 59.4, -10.6]), box('g_keye_l', [-6.4, 57.6, -11.2], [-3.4, 59.4, -10.6])] + fe)

# L 塔の兜（頭の 2.5 倍の高さ・段ごとの小さな屋根・窓の光）
s, top = shell('lw', steps=((60, 4.4),))
tw = []
c, ra, rd = (0, top[1] - 0.6, 0), 7.2, 8.8
for i, h in enumerate([8, 7, 6]):
    tw += oct8(f'lt{i}_', c, ra, rd, 0, h, 1.4)
    tw += reg8(f'b_lr{i}_', (0, c[1] + h, 0), rd + 2.6, -58, 4.6, 0.9)
    tw.append(box(f'g_lwin{i}', [-1, c[1] + h * 0.35, -ra - 0.3], [1, c[1] + h * 0.75, -ra + 0.2]))
    tw.append(box(f'g_lwinb{i}', [-1, c[1] + h * 0.35, ra - 0.2], [1, c[1] + h * 0.75, ra + 0.3]))
    c = (0, c[1] + h, 0); ra, rd = ra - 1.6, rd - 1.8
tw += [box('lsp', [-1, c[1], -1], [1, c[1] + 7, 1]), box('g_ltop', [-1.6, c[1] + 6, -1.6], [1.6, c[1] + 9.2, 1.6], [45, 45, 0], [0, c[1] + 7.6, 0])]
H['L_tower'] = s + tw + [front_mark('d_lslit', W0, RA, 0, -5, 5, 55.4, 56.8)]

# M 片側の結晶（右は滑らかな兜、左肩から頭より高い結晶の群れ・左右非対称）
s, top = shell('mw')
cr = []
for i, (w, L, tx, tz) in enumerate([(6.0, 24, -8, 18), (4.6, 17, 14, 36), (4.0, 14, -24, 8), (3.4, 11, 4, 58), (3.0, 9, 30, 24), (3.6, 12, -10, -14), (2.6, 8, 20, 70)]):
    cr.append(box(f'mc{i}', [-11 - w / 2, 54, -1 - w / 2], [-11 + w / 2, 55 + L, -1 + w / 2], [tx, 45, tz], [-11, 55, -1]))
cr += [box('g_mcore', [-12.6, 56.6, -2.6], [-9.4, 59.8, 0.6], [0, 45, 0], [-11, 58, -1])]
H['M_crystal'] = s + cr + [front_mark('d_mslit', W0, RA, 0, 1, 7, 55.4, 56.8), box('b_mbrow', [-8, 57, -11.4], [8, 58.2, -10.6])]

# N 獣の口の中（大きな上顎が顔の上に被さり、牙が顔の前に垂れる・頭頂に目玉の瘤）
s, top = shell('nw', steps=((30, 6), (66, 4)))
fang = [box(f'b_nf{i}', [x - 0.7, 48.6 + abs(x) * 0.3, -18.2], [x + 0.7, 55.4, -16.8]) for i, x in enumerate([-8, -4.8, -1.6, 1.6, 4.8, 8])]
H['N_maw'] = (s + [box('nup', [-11.6, 55.4, -20], [11.6, 62, -8], [-10, 0, 0], [0, 62, -8]),
                   box('nup2', [-12.4, 60, -14], [12.4, 66, 4], [-6, 0, 0], [0, 64, 0]),
                   box('b_nlip', [-11.8, 55, -20.4], [11.8, 56.2, -16.4], [-10, 0, 0], [0, 62, -8])] + fang +
          [box('neye_r', [5, 66, -10], [10, 70, -5]), box('neye_l', [-10, 66, -10], [-5, 70, -5]),
           box('g_npup_r', [6.4, 67.2, -10.4], [8.6, 68.8, -9.8]), box('g_npup_l', [-8.6, 67.2, -10.4], [-6.4, 68.8, -9.8])])

# O 横に張る翼（左右へ頭幅の 2 倍の金属の翼・中央の稜）
s, top = shell('ow')
wg = []
for i, (y, L, up, w) in enumerate([(62, 18, 8, 3.0), (59.4, 15, 3, 2.6), (57, 11, -1, 2.2)]):
    wg.append(bar(f'owg{i}', (11, y, 1), (11 + L, y + up, 5 + i), 1.0, w + 1.4))
wg.append(bar('b_owe', (11, 63.4, 1), (29, 71.4, 5), 1.0, 1.0))
H['O_wings'] = s + wg + mirror(wg) + [box('b_ocomb', [-0.7, 66, -8], [0.7, 71, 8]), front_mark('d_oslit', W0, RA, 0, -6, 6, 55.4, 56.8)]

# P 浮く破片の輪（頭巾の上を、離れた破片が斜めの輪になって回る）
s, top = shell('pw', ks=range(1, 8), steps=((26, 6.4), (64, 4)))
sh = []
for k in range(10):
    a = math.radians(36 * k)
    x, z = 21 * math.sin(a), -21 * math.cos(a)
    y = 62 + 5 * math.cos(a)
    sz = 3.0 if k % 2 else 4.4
    sh.append(box(f'ps{k}', [x - sz / 2, y - sz, z - sz / 2], [x + sz / 2, y + sz, z + sz / 2], [20, r2(36 * k + 45), 15], [r2(x), r2(y), r2(z)]))
sh += [box(f'g_pg{k}', [21 * math.sin(math.radians(72 * k + 18)) - 0.6, 61.4 + 5 * math.cos(math.radians(72 * k)), -21 * math.cos(math.radians(72 * k + 18)) - 0.6],
           [21 * math.sin(math.radians(72 * k + 18)) + 0.6, 62.6 + 5 * math.cos(math.radians(72 * k)), -21 * math.cos(math.radians(72 * k + 18)) + 0.6]) for k in range(5)]
H['P_shards'] = s + sh + [oct8('pwf', (0, 59.6, 0), RA, RD, 0, 5.2, 1.6, ks=[0])[0],
                          box('c_prim_r', [7.4, 47.2, -11.6], [9.6, 61, -10.4]), box('c_prim_l', [-9.6, 47.2, -11.6], [-7.4, 61, -10.4])]

# Q 背の鰭（額から後頭部へ、頭の高さほどの大きな鰭と骨の筋）
s, top = shell('qw')
fin = []
prof = [(-9, 66, 2), (-5, 72, 6), (-1, 77, 9), (3, 80, 11), (7, 80, 12), (11, 76, 12), (14, 68, 10), (15, 58, 8)]
for i, (z, yt, _) in enumerate(prof):
    fin.append(box(f'qf{i}', [-1.0, 64, z], [1.0, yt + 4, z + 4.4]))
for i, (z, yt, _) in enumerate(prof[:-1]):
    fin.append(bar(f'b_qs{i}', (0, 64.5, z + 2), (0, yt + 6, z + 3.2), 1.4, 1.4))
qs = [bar('qside', (10.4, 58, 2), (16, 64, 9), 0.9, 4.2), bar('b_qside', (10.6, 60, 2), (17, 66.6, 9.6), 0.9, 0.9)]
H['Q_fin'] = (s + fin + qs + mirror(qs) + [box('qfb', [-1.0, 50, 10.4], [1.0, 64.6, 15.6]), front_mark('d_qslit', W0, RA, 0, -6, 6, 55.4, 56.8),
                         box('b_qbrow', [-8, 57, -11.4], [8, 58.2, -10.6])])

OUT = _argv[1]
os.makedirs(OUT, exist_ok=True)
for k, v in H.items():
    names = [e['name'] for e in v]
    assert len(names) == len(set(names)), k
    json.dump(v, open(f'{OUT}/{k}.json', 'w'), separators=(',', ':'))
    print(k, len(v))
