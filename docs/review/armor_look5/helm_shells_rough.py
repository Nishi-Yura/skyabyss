# 兜の土台（鉢）の形を 3 案。16 方向の面の距離 R と、高さごとの内への寄せ d で回転体のように組む。数値を出すだけ。
import json, math, os, sys
_d = os.path.dirname(os.path.abspath(__file__))
_src = open(os.path.join(_d, 'helms.py'), encoding='utf8').read()
_src = _src[:_src.index("W0 = ")]
_argv = sys.argv; sys.argv = [_argv[0], _argv[1]]
exec(_src)
OUT = _argv[1]
TH = 1.4


def need(theta):
    """頭の箱（±8）を覆うのに要る、その向きの面の内側の距離"""
    t = math.radians(theta)
    return 8 * (abs(math.cos(t)) + abs(math.sin(t)))


def span(th, R, k):
    """面 k の横の範囲 [u0,u1]（隣の面との交点）"""
    n = len(th)
    us = []
    for j in ((k - 1) % n, (k + 1) % n):
        dt = math.radians(th[k] - th[j])
        us.append((R[j] - R[k] * math.cos(dt)) / math.sin(dt))
    return min(us), max(us)


def lathe(name, th, R0, prof, check=True):
    """th: 面の向き（度、0 = 前）、R0: 面の距離、prof: [(y, d), ...] 下から上。d は内への寄せ"""
    out = []
    for i in range(len(prof) - 1):
        (y0, d0), (y1, d1) = prof[i], prof[i + 1]
        dy, dd = y1 - y0, d1 - d0
        L = math.hypot(dy, dd)
        t = math.degrees(math.atan2(dd, dy))
        for k, a in enumerate(th):
            # 根元と先の両方の横幅を測って広い方を使う
            spans = [span(th, [r - d for r in R0], k) for d in (d0, d1)]
            u0 = min(s[0] for s in spans); u1 = max(s[1] for s in spans)
            if u1 - u0 < 0.4:
                continue
            R = R0[k] - d0
            if check and y1 > 48.2 and y0 < 63.8:
                dm = max(d0 + dd * (yy - y0) / dy for yy in (max(y0, 48.2), min(y1, 63.8)))
                assert R0[k] - dm - TH >= need(a) - 0.05, (name, k, a, y0, R0[k] - dm - TH, need(a))
            p = plate(f"{name}{i}_{k}", (0, y0, 0), R, u1 - u0 + 0.3, t, a % 360, L, TH)
            off = (u0 + u1) / 2
            p["from"][0] = r2(p["from"][0] + off); p["to"][0] = r2(p["to"][0] + off)
            out.append(p)
    # 蓋
    y, d = prof[-1]
    a = max(1.2, min(R0) - d)
    out += [box(f"{name}cap", [-a, y - 1.4, -a], [a, y + 0.2, a]), box(f"{name}cap45", [-a, y - 1.4, -a], [a, y + 0.2, a], [0, 45, 0], [0, y, 0])]
    return out


TH16 = [22.5 * k for k in range(16)]
S = {}
# 0 今の土台（くらべる用）
d, _, _, _ = dome('od', (0, 64.6, 0), RA, RD, [(40, 7.4), (72, 3.4)])
S['0_now'] = oct8('ow', (0, 47.2, 0), RA, RD, 0, 17.6, 1.6) + d

# A 角の丸い鉢（前と横は平らに近く、角は大きく丸め、顎の下へすぼまる）
RA_ = [10.2, 12.4, 13.2, 12.4, 10.2, 12.4, 13.2, 12.4, 10.6, 12.4, 13.2, 12.4, 10.2, 12.4, 13.2, 12.4]
S['A_round'] = lathe('a', TH16, RA_, [(44.6, 2.0), (47.6, 0), (64.4, 0), (67.4, 1.4), (70.2, 3.6), (72.2, 6.4), (73.2, 8.6)])

# B 舳先（前が縦の稜で尖り、横は締まり、後ろは丸い。頂はゴシックの尖頭）
RB_ = [14.2, 12.6, 13.2, 12.0, 10.2, 12.0, 13.0, 12.4, 11.0, 12.4, 13.0, 12.0, 10.2, 12.0, 13.2, 12.6]
th_b = [0, 26, 45, 67.5, 90, 112.5, 135, 157.5, 180, 202.5, 225, 247.5, 270, 292.5, 315, 334]
S['B_prow'] = lathe('b', th_b, RB_, [(45.2, 1.4), (47.6, 0), (64.4, 0), (68, 1.2), (71.6, 3.2), (74.8, 6.0), (77.2, 9.2), (78.4, 11.0)])

# C 卵（ほぼ丸い 16 角。胴が少しふくらみ、上へ高く丸く、下は顎の下へ深くすぼむ）
RC_ = [12.6 if k % 4 else 12.4 for k in range(16)]
RC_ = [r if k % 2 == 0 else 13.0 for k, r in enumerate(RC_)]
RC_ = [13.2 if k % 4 == 2 else r for k, r in enumerate(RC_)]
S['C_egg'] = lathe('c', TH16, RC_, [(43.8, 3.2), (46.4, 0.8), (49, -0.4), (60, -0.4), (64.6, 0.2), (68.4, 1.6), (71.8, 3.8), (74.4, 6.6), (75.8, 9.0)])

# 見分けやすいよう、どの案にも同じ のぞき穴と眉の帯を付ける
for k, v in S.items():
    Rf = {'0_now': RA, 'A_round': 10.2, 'B_prow': 14.2, 'C_egg': 12.4}[k]
    if k == 'B_prow':
        v += [box('d_slit_r', [-0.1, 55.4, -14.5], [6.0, 56.8, -14.1], [0, -27, 0], [0, 56, -14.2]),
              box('d_slit_l', [-6.0, 55.4, -14.5], [0.1, 56.8, -14.1], [0, 27, 0], [0, 56, -14.2]),
              box('b_ridge', [-0.6, 47.6, -14.6], [0.6, 66, -13.6])]
    else:
        w = 5.0 if k != '0_now' else 6
        v += [box('d_slit', [-w, 55.4, -Rf - 0.3], [w, 56.8, -Rf + 0.1]), box('b_brow', [-w - 1, 56.8, -Rf - 0.4], [w + 1, 57.8, -Rf + 0.1])]
    os.makedirs(OUT, exist_ok=True)
    names = [e['name'] for e in v]
    assert len(names) == len(set(names)), k
    json.dump(v, open(f'{OUT}/{k}.json', 'w'), separators=(',', ':'))
    print(k, len(v))
