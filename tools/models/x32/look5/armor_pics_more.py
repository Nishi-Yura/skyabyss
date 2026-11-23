# -*- coding: utf-8 -*-
"""防具の 2D の絵の板（armor_pics.py の続き。PICS は armor_pics.PICS に足される）。
レア度の段（2026-11-24）: エピック = 背に小さな紋、ヒロイック = 背の大きな絵、神器 = 背の絵＋頭の後ろの大きな後光（光る aura を重ねる）。

  神器の足元の紋の輪（2026-11-24、頭の後ろの後光は「邪魔」で外した）: sigil<組>（_glow、Body に X 90° で寝かせて y0.3）。
  （旧）後光: aura（_glow で光る光線の輪。どの神器にも構造の板の後ろに重ねる）・peal（鐘守）・moon（霞渡り。鶴 crane は頭上の別の板）・libra（天秤）・arch（渡りの装束）
  神器の背: bell（鐘守の鐘と波紋）・kasumi（霞渡りの金縁のすやり霞の帯）
  ヒロイックの背: astrolabe（星詠みの天球儀）・cloudwing（雲上の騎士の翼と棘の日輪）・keep / blaze（焼け砦の塔の紋と炎、炎は _glow）・ropering（縄の人の縄の輪）
  エピックの背の紋: ramhorn（羊毛）・embers（灰革）・salt（塩板）・lampsign（灯守）・smoke（煤）・gate（深みの番兵）
画素の向き: north の面を見る人から見たまま（i が右、j が下）。前向きの板は前から、Y 180° の板は後ろから見たまま。
"""
import math

from armor_pics import _line, _outline, _diamond, _h

CRANE_RED = '#c8283a'


def _shape(px, cx, cy, ang, size, fn):
    """(cx, cy) を原点に ang 度（0 = 右、反時計回り）へ向けた局所座標 (u: 向きの方向, v: 横) で fn(u, v) -> 色 or None"""
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    for j in range(int(cy - size) - 1, int(cy + size) + 2):
        for i in range(int(cx - size) - 1, int(cx + size) + 2):
            x, y = i + 0.5 - cx, cy - (j + 0.5)
            u, v = x * ca + y * sa, -x * sa + y * ca
            c = fn(u, v)
            if c:
                px[(i, j)] = c


def _poly(px, pts, col):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    for j in range(int(min(ys)) - 1, int(max(ys)) + 2):
        for i in range(int(min(xs)) - 1, int(max(xs)) + 2):
            x, y, ins = i + 0.5, j + 0.5, False
            for k in range(len(pts)):
                (x0, y0), (x1, y1) = pts[k], pts[k - 1]
                if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
                    ins = not ins
            if ins:
                px[(i, j)] = col(i, j) if callable(col) else col


def _arc(px, cx, cy, r, a0, a1, col, width, n=None):
    n = n or int(abs(a1 - a0) * r / 20) + 8
    pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * s / n)), cy - r * math.sin(math.radians(a0 + (a1 - a0) * s / n))) for s in range(n + 1)]
    for p, q in zip(pts, pts[1:]):
        _line(px, p, q, col(p) if callable(col) else col, width)


def _band(px, w, h, cx, cy, r0, r1, colfn):
    for j in range(h):
        for i in range(w):
            x, y = i + 0.5 - cx, cy - (j + 0.5)
            d = math.hypot(x, y)
            if r0 <= d < r1:
                c = colfn(x, y, d, math.degrees(math.atan2(y, x)) % 360)
                if c:
                    px[(i, j)] = c


def _star(px, x, y, r, ramp):
    """4 つ角の星のきらめき"""
    for k in range(-r, r + 1):
        px[(int(x) + k, int(y))] = ramp[7 - abs(k) * 2 // max(1, r)]
        px[(int(x), int(y) + k)] = ramp[7 - abs(k) * 2 // max(1, r)]


def _clip(px, w, h):
    return {(i, j): c for (i, j), c in px.items() if 0 <= i < w and 0 <= j < h}


# ---------------------------------------------------------------- 後光（神器）

def aura(w, h, R):
    """神器の後光（_glow で光る）: 長短交互の細い光線 24 本と、光の粒の輪。構造の板（peal など）の少し後ろに重ねる。"""
    px = {}
    g, gold = R['glow'], R['bone']
    cx, cy = w / 2, h / 2
    rm = min(w, h) / 2 - 1.5
    for k in range(24):
        a = math.radians(k * 15 + 7.5)
        L = rm if k % 2 == 0 else rm * 0.78
        r0 = rm * 0.5
        for s in range(30):
            u = s / 29
            r = r0 + (L - r0) * u
            p = (cx + r * math.cos(a), cy - r * math.sin(a))
            _line(px, p, p, g[7] if u < 0.35 else (g[6] if u < 0.7 else g[5]), 2.2 - 1.4 * u)
    for k in range(48):
        a = math.radians(k * 7.5)
        _line(px, (cx + rm * 0.86 * math.cos(a), cy - rm * 0.86 * math.sin(a)),
              (cx + rm * 0.86 * math.cos(a), cy - rm * 0.86 * math.sin(a)), g[7] if k % 2 else g[5], 1.2)
    _arc(px, cx, cy, rm * 0.5, 0, 360, g[6], 1.6)
    return _clip(_outline(px, gold[3]), w, h)


def peal(w, h, R):
    """鐘守の後光: 金の太い輪（縁に暗い金）と内の細い輪を 12 本の輻でつなぎ、外へ向けて小鐘 8 つ。四方に藍の菱形。"""
    px = {}
    gold, sea, dk = R['bone'], R['sea'], R['dark']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 6
    ri, rin = ro - 4, ro * 0.58
    for k in range(12):
        a = math.radians(k * 30 + 15)
        _line(px, (cx + rin * math.cos(a), cy - rin * math.sin(a)), (cx + ri * math.cos(a), cy - ri * math.sin(a)), gold[4], 1.2)
    _band(px, w, h, cx, cy, ri, ro, lambda x, y, d, a: gold[2] if (d < ri + 0.8 or d >= ro - 0.8) else gold[min(7, 5 + (1 if y - x > 0 else 0))])
    _arc(px, cx, cy, rin, 0, 360, gold[6], 1.4)
    for k in range(8):                                     # 外へ吊った小鐘（局所 u = 外向き）
        ang = k * 45 + 22.5
        bx, by = cx + ro * math.cos(math.radians(ang)), cy - ro * math.sin(math.radians(ang))

        def bellf(u, v):
            if 0 <= u < 1.5 and abs(v) < 0.7:
                return gold[3]
            if 1.5 <= u < 6:
                hw = 1.2 + (u - 1.5) * 0.55
                if abs(v) < hw:
                    return gold[7] if v > hw * 0.3 else (gold[5] if v > -hw * 0.5 else gold[4])
            if 6 <= u < 7 and abs(v) < 1:
                return sea[6]
            return None
        _shape(px, bx, by, ang, 8, bellf)
    for ang in (0, 90, 180, 270):
        _diamond(px, cx + (ro - 2) * math.cos(math.radians(ang)), cy - (ro - 2) * math.sin(math.radians(ang)), 3, sea)
    return _clip(_outline(px, dk[3]), w, h)


def moon(w, h, R):
    """霞渡りの後光: 藍の月輪（金の縁・外に短い光の刻み 16）と、下を横切る金縁の霞の帯。鶴は別の板（crane）で頭の上に。"""
    px = {}
    gold, sea, cloth, dk = R['bone'], R['sea'], R['cloth'], R['dark']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 4
    _band(px, w, h, cx, cy, 0, ro - 2.5, lambda x, y, d, a: cloth[max(2, min(6, 5 - int(d / ro * 3) + (1 if y - x > ro * 0.4 else 0)))])
    _band(px, w, h, cx, cy, ro - 2.5, ro, lambda x, y, d, a: gold[2] if d >= ro - 0.7 else gold[6 if y > 0 else 5])
    for k in range(16):
        a = math.radians(k * 22.5 + 11.25)
        _line(px, (cx + (ro + 0.5) * math.cos(a), cy - (ro + 0.5) * math.sin(a)), (cx + (ro + 3) * math.cos(a), cy - (ro + 3) * math.sin(a)), gold[6], 1.2)
    _kasumi_band(px, cx - 1.05 * ro, cx + 0.8 * ro, cy + 0.55 * ro, 0.13 * ro * 2, gold, sea, sea)
    _kasumi_band(px, cx - 0.7 * ro, cx + 1.1 * ro, cy - 0.62 * ro, 0.1 * ro * 2, gold, cloth, sea)
    return _clip(_outline(px, dk[3]), w, h)


def crane(w, h, R):
    """霞渡りの頭上の鶴（板いっぱいに横向きに飛ぶ）: 白い胴と翼（先が黒い風切り）、長い首の先に赤い冠、金のくちばし、後ろへ流れる黒い脚。"""
    px = {}
    gold, sea, dk, c2 = R['bone'], R['sea'], R['dark'], R['cloth2']
    cx, cy, s = w * 0.42, h * 0.55, w * 0.5
    white = sea[7]
    for tip, base in (((cx - 0.02 * s, cy + 0.42 * s), ((cx - 0.22 * s, cy + 0.02 * s), (cx + 0.12 * s, cy + 0.02 * s))),
                      ((cx - 0.34 * s, cy - 0.66 * s), ((cx - 0.26 * s, cy - 0.06 * s), (cx + 0.14 * s, cy - 0.08 * s)))):
        L = math.hypot(tip[0] - base[0][0], tip[1] - base[0][1])
        _poly(px, [base[0], (tip[0] - 0.08 * s, tip[1] + (0.06 * s if tip[1] < cy else -0.06 * s)), tip, base[1]],
              lambda i, j, tip=tip, L=L: dk[5] if math.hypot(i + 0.5 - tip[0], j + 0.5 - tip[1]) < L * 0.3 else white)
        for f in range(1, 6):
            t = f / 6
            p0 = (base[0][0] + (base[1][0] - base[0][0]) * t, base[0][1] + (base[1][1] - base[0][1]) * t)
            _line(px, p0, (p0[0] + (tip[0] - p0[0]) * 0.78, p0[1] + (tip[1] - p0[1]) * 0.78), c2[5], 0.8)
    _shape(px, cx - 0.04 * s, cy - 0.03 * s, 8, s * 0.4, lambda u, v: white if (u / (0.34 * s)) ** 2 + (v / (0.12 * s)) ** 2 < 1 else None)
    _line(px, (cx + 0.26 * s, cy - 0.08 * s), (cx + 0.6 * s, cy - 0.3 * s), white, 2.4)
    _line(px, (cx + 0.58 * s, cy - 0.29 * s), (cx + 0.66 * s, cy - 0.33 * s), dk[4], 2.4)
    _line(px, (cx + 0.67 * s, cy - 0.33 * s), (cx + 0.92 * s, cy - 0.28 * s), gold[5], 1.2)
    _shape(px, cx + 0.64 * s, cy - 0.38 * s, 0, 2, lambda u, v: CRANE_RED if u * u + v * v < 1.6 else None)
    _line(px, (cx - 0.34 * s, cy + 0.02 * s), (cx - 0.78 * s, cy + 0.16 * s), dk[4], 1.0)
    _line(px, (cx - 0.34 * s, cy + 0.0 * s), (cx - 0.52 * s, cy + 0.05 * s), dk[5], 2.0)
    return _clip(_outline(px, dk[3]), w, h)


def libra(w, h, R):
    """天秤の甲冑の後光: 白い石（体の右 = 前から見て左）と暗い石（体の左）に分かれた輪、金の縁。上に傾いた金の竿秤と吊った 2 つの皿
    （天の白い皿が高く、淵の黒い皿が低い）、支点に紫の菱形。暗い側に紫の割れ目。"""
    px = {}
    white, dark, gold, g, dk = R['sea'], R['cloth2'], R['bone'], R['glow'], R['dark']
    cx, cy = w / 2, h / 2 + 5
    ro = min(w, h) / 2 - 7
    ri = ro - 4

    def ringc(x, y, d, a):
        if 28 <= a < 36 or 332 <= a < 340:
            return g[6]
        if d < ri + 0.8 or d >= ro - 0.8:
            return gold[5]
        return (white if x < 0 else dark)[5 + (1 if y > 0 else 0) - (1 if _h(int(a // 9), 0, 3) < 0.3 else 0)]
    _band(px, w, h, cx, cy, ri, ro, ringc)
    tilt = math.radians(9)                                  # 竿は左（天）が上
    beam_y = cy - ro - 3
    L = ro * 0.82
    p0 = (cx - L * math.cos(tilt), beam_y - L * math.sin(tilt))
    p1 = (cx + L * math.cos(tilt), beam_y + L * math.sin(tilt))
    _line(px, p0, p1, gold[6], 1.8)
    _line(px, (cx, beam_y), (cx, cy - ro + 1), gold[4], 1.4)
    for (ex, ey), ramp in ((p0, white), (p1, dark)):
        pan_y = ey + ro * 0.42
        _line(px, (ex, ey), (ex - 4, pan_y), gold[4], 0.8)
        _line(px, (ex, ey), (ex + 4, pan_y), gold[4], 0.8)
        _shape(px, ex, pan_y, 0, 7, lambda u, v, r=ramp: (r[6] if v > -1 else r[4]) if (v <= 0 and u * u / 36 + v * v / 9 < 1) else None)
        _line(px, (ex - 6, pan_y), (ex + 6, pan_y), gold[6], 1.0)
    _diamond(px, cx, beam_y - 1, 4, g)
    _diamond(px, cx, cy + ro - 2, 3, gold)
    return _clip(_outline(px, dk[3]), w, h)


def arch(w, h, R):
    """渡りの装束の後光: 頭の後ろに架かる橋の迫石 11 個のアーチ（体の右 = 前から見て左が白い石、反対が暗い石）。暗い側の 3 個は外へずれて浮く。
    頂の要石は大きく、紫の菱形がはまる。内に途切れる紫の弧。"""
    px = {}
    white, dark, gold, g, dk = R['sea'], R['cloth2'], R['bone'], R['glow'], R['dark']
    cx, cy = w / 2, h * 0.58
    ro = min(w / 2, h * 0.58) - 6
    ri = ro - 7
    n = 11
    span0, span1 = 200, -20                                 # 左下から頂を通って右下へ
    step = (span0 - span1) / n
    for k in range(n):
        a0 = span0 - k * step + 2.6
        a1 = span0 - (k + 1) * step - 2.6
        mid = (a0 + a1) / 2
        left = mid > 90                                     # 前から見て左 = 白
        key = k == n // 2
        off = (1.5 + 1.2 * (k - 7)) if k >= 8 else 0
        r0, r1 = (ri - 2, ro + 2) if key else (ri, ro)
        ramp = gold if key else (white if left else dark)

        def c(x, y, d, a, a0=a0, a1=a1, off=off, ramp=ramp, r0=r0, r1=r1, k=k):
            aa = a if a < 270 else a - 360
            if not (a1 <= aa < a0):
                return None
            dd = d - off
            if not (r0 <= dd < r1):
                return None
            if dd < r0 + 0.9 or dd >= r1 - 0.9:
                return ramp[3]
            return ramp[5 + (1 if y - x > 0 else 0) - (1 if _h(int(dd), k, 7) < 0.2 else 0)]
        _band(px, w, h, cx, cy, r0 - 0.5, r1 + 4, c)
    _diamond(px, cx, cy - (ri + ro) / 2, 3, g)
    for a0, a1 in ((185, 140), (120, 60), (40, -5)):
        _arc(px, cx, cy, ri - 3, a0, a1, g[6], 1.6)
    for a, rr in ((205, ro + 4), (-28, ro + 6)):           # 両端の下の浮く小石
        x, y = cx + rr * math.cos(math.radians(a)), cy - rr * math.sin(math.radians(a))
        _shape(px, x, y, 30, 4, lambda u, v, r=(white if a > 90 else dark): r[5] if abs(u) + abs(v) < 3 else None)
    return _clip(_outline(px, dk[3]), w, h)


# ---------------------------------------------------------------- 神器の背

def bell(w, h, R):
    """鐘守の背の大きな鐘: 上の吊り環・肩・裾へ広がる胴（左上が明るい金）・藍の帯 2 本（金の縁と粒の銘）・厚い口縁・下に藍の舌。
    両脇に鐘の音の金の弧 3 本ずつ。"""
    px = {}
    gold, sea, dk, cl = R['bone'], R['sea'], R['dark'], R['cloth']
    cx = w / 2
    top, bot = h * 0.14, h * 0.86
    Hb = bot - top
    hwmax = min(w * 0.3, Hb * 0.5)
    for k in range(3):                                      # 音の弧
        r = hwmax + 4 + 5 * k
        for sd in (1, -1):
            a0, a1 = (-38 + 6 * k, 38 - 6 * k) if sd > 0 else (142 + 6 * k, 218 - 6 * k)
            _arc(px, cx, top + Hb * 0.55, r, a0, a1, gold[6 - k], 2.2 - 0.4 * k)
    for j in range(int(top), int(bot) + 1):
        t = (j + 0.5 - top) / Hb
        if t < 0 or t > 1:
            continue
        hw = hwmax * (0.42 + 0.2 * t + 0.38 * t ** 4) if t > 0.06 else hwmax * 0.42 * math.sqrt(t / 0.06)
        for i in range(int(cx - hw), int(cx + hw) + 1):
            x = i + 0.5 - cx
            if abs(x) > hw:
                continue
            q = x / hw
            if 0.2 < t < 0.27 or 0.74 < t < 0.81:
                c = sea[5 - (1 if q > 0.3 else 0)]
                if (0.2 < t < 0.21) or (0.26 < t < 0.27) or (0.74 < t < 0.75) or (0.8 < t < 0.81):
                    c = gold[6]
                elif int(x) % 3 == 0 and 0.22 < t < 0.25:
                    c = gold[5]
            elif t > 0.92:
                c = gold[7] if t < 0.95 else gold[3]
            else:
                c = gold[7 if q < -0.45 else (6 if q < 0.1 else (5 if q < 0.6 else 4))]
            px[(i, j)] = c
    _arc(px, cx, top - 2.2, 2.6, 0, 360, gold[5], 1.4)       # 吊り環
    _shape(px, cx, bot + 2.5, 0, 4, lambda u, v: sea[6] if u * u + (v * 0.8) ** 2 < 6 else None)
    _diamond(px, cx, top + Hb * 0.5, 3, cl)
    return _clip(_outline(px, dk[3]), w, h)


def _kasumi_band(px, x0, x1, yc, bh, gold, fill, light):
    """金の縁のすやり霞の帯 1 本（両端が丸く、右端に段の切れ込み）"""
    r = bh / 2
    for j in range(int(yc - r) - 1, int(yc + r) + 2):
        for i in range(int(x0) - 1, int(x1) + 2):
            x, y = i + 0.5, j + 0.5
            cxk = min(max(x, x0 + r), x1 - r)
            d = math.hypot(x - cxk, y - yc)
            notch = x > x1 - r * 2.2 and y > yc + r * 0.1 and x < x1 - r * 0.9
            if d < r and not notch:
                if d > r - 1.1:
                    px[(i, j)] = gold[6] if y < yc else gold[4]
                elif abs(y - (yc - r * 0.35)) < 0.5 and x0 + r < x < x1 - r * 1.6:
                    px[(i, j)] = light[7]
                else:
                    px[(i, j)] = fill[5 if y < yc else 4]


def kasumi(w, h, R):
    """霞渡りの背と裳裾の霞: 金の縁のすやり霞の帯を段違いに重ねる（藍と白が交互）。板の高さで帯の数が決まる（約 9 画素に 1 本）。"""
    px = {}
    gold, sea, cloth, dk = R['bone'], R['sea'], R['cloth'], R['dark']
    n = max(2, round(h / 11))
    bh = h / n * 0.78
    for k in range(n):
        yc = (k + 0.5) * h / n
        sh = (0.0, 0.16, 0.06, 0.2, 0.02)[k % 5]
        ln = (0.84, 0.7, 0.92, 0.66, 0.8)[k % 5]
        x0 = w * sh
        x1 = min(w - 1, x0 + w * ln)
        if k % 2:
            _kasumi_band(px, x0, x1, yc, bh, gold, cloth, sea)
        else:
            _kasumi_band(px, x0, x1, yc, bh, gold, sea, sea)
    return _clip(_outline(px, dk[3]), w, h)


# ---------------------------------------------------------------- ヒロイックの背

def astrolabe(w, h, R):
    """星詠みの背の天球儀: 目盛りの付いた金の外輪・傾いた 2 本の黄道の楕円と子午の楕円・内輪・真ん中の藍の星の珠（光の点）。
    楕円の上に 4 つ角の星 6 つ、星を結ぶ白い細線。上に指針の菱形。"""
    px = {}
    gold, sea, cl, g, dk = R['bone'], R['sea'], R['cloth'], R['glow'], R['dark']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 4
    _band(px, w, h, cx, cy, ro - 2.6, ro, lambda x, y, d, a: gold[6 if y - x > 0 else 5] if d > ro - 2.2 else gold[3])
    for m in range(24):
        a = math.radians(m * 15)
        _line(px, (cx + (ro - 4.2) * math.cos(a), cy - (ro - 4.2) * math.sin(a)), (cx + (ro - 2.6) * math.cos(a), cy - (ro - 2.6) * math.sin(a)), gold[4], 0.8)
    stars = []
    for tilt, rx, ry, col in ((32, 0.95, 0.34, gold[5]), (-32, 0.95, 0.34, gold[5]), (0, 0.3, 0.95, gold[4])):
        t = math.radians(tilt)
        pts = []
        for s in range(97):
            a = math.radians(s * 3.75)
            x, y = ro * rx * math.cos(a), ro * ry * math.sin(a)
            pts.append((cx + x * math.cos(t) - y * math.sin(t), cy - (x * math.sin(t) + y * math.cos(t))))
        for p, q in zip(pts, pts[1:]):
            _line(px, p, q, col, 1.4)
        stars += [pts[10], pts[52]]
    _arc(px, cx, cy, ro * 0.55, 0, 360, gold[6], 1.2)
    _band(px, w, h, cx, cy, 0, ro * 0.24, lambda x, y, d, a: sea[min(7, 6 - int(d / (ro * 0.24) * 3) + (1 if y - x > 0 else 0))])
    for p, q in zip(stars, stars[1:]):
        _line(px, p, q, cl[6], 0.6)
    for p in stars:
        _star(px, p[0], p[1], 3, g)
    _diamond(px, cx, cy - ro - 1, 3, g)
    px[(int(cx - ro * 0.06), int(cy - ro * 0.08))] = g[7]
    return _clip(_outline(px, dk[3]), w, h)


def cloudwing(w, h, R):
    """雲上の騎士の背: 肩の高さの棘の金の日輪（24 本の棘）と、その前に左右へ開く刃のような羽 7 枚ずつ（銀白・先が空色・上の縁に金）。
    羽の根元に雲の巻き。"""
    px = {}
    sea, gold, cl, c2, dk = R['sea'], R['bone'], R['cloth'], R['cloth2'], R['dark']
    cx, cy = w / 2, h * 0.44
    rr = h * 0.24                                          # 頭の後ろの後光は神器だけなので、日輪は背の真ん中に小さめ
    for k in range(24):                                     # 日輪の棘
        a = math.radians(k * 15)
        L = rr + (4.5 if k % 2 == 0 else 2.5)
        _line(px, (cx + rr * math.cos(a), cy - rr * math.sin(a)), (cx + L * math.cos(a), cy - L * math.sin(a)), gold[6 if k % 2 == 0 else 5], 1.6 if k % 2 == 0 else 1.1)
    _band(px, w, h, cx, cy, rr - 2.2, rr + 0.4, lambda x, y, d, a: gold[6 if y > 0 else 5] if d > rr - 1.6 else gold[3])
    for sd in (-1, 1):
        rx, ry = cx + sd * 3.5, h * 0.5
        n = 7
        for f in range(n):
            t = f / (n - 1)
            ang = math.radians(32 - 62 * t)
            L = (w / 2 - 4) * (1.0 - 0.42 * t)
            ex, ey = rx + sd * L * math.cos(ang), ry - L * math.sin(ang)
            nx, ny = -math.sin(ang) * sd, -math.cos(ang)       # 上の縁の向き
            wd = 5.2 - 1.6 * t
            pts = [(rx + nx * wd, ry + ny * wd), (ex, ey), (rx - nx * wd, ry - ny * wd)]

            def col(i, j, ex=ex, ey=ey, L=L):
                u = 1 - math.hypot(i + 0.5 - ex, j + 0.5 - ey) / L
                return cl[6] if u < 0.22 else (sea[7] if u < 0.6 else sea[6])
            _poly(px, pts, col)
            _line(px, pts[0], (ex, ey), gold[6], 0.8)
            _line(px, (rx, ry), (ex, ey), c2[5], 0.5)
        for k, (ox, oy, r) in enumerate(((0, 0, 3.2), (sd * 3.5, 1.2, 2.4))):  # 根元の雲
            _arc(px, rx + ox, ry + oy, r, 0, 360, sea[7], 1.6)
            _arc(px, rx + ox, ry + oy, r * 0.45, 0, 360, cl[5], 1.0)
    return _clip(_outline(px, dk[3]), w, h)


def keep(w, h, R):
    """焼け砦の背の紋: 煤けた鉄の塔（裾が広い）・上に胸壁の歯 3 つ・アーチの門（奥は熾の赤）・鋲の列・斜めの割れ目（熾）・金の縁。"""
    px = {}
    iron, gold, g, dk = R['sea'], R['bone'], R['glow'], R['dark']
    cx = w / 2
    top, bot = h * 0.18, h - 1
    for j in range(int(top), int(bot)):
        t = (j + 0.5 - top) / (bot - top)
        hw = w * (0.3 + 0.12 * t)
        for i in range(int(cx - hw), int(cx + hw) + 1):
            x = i + 0.5 - cx
            if abs(x) > hw:
                continue
            edge = abs(x) > hw - 1.2
            px[(i, j)] = gold[4] if edge else iron[4 - (1 if x > hw * 0.3 else 0) + (1 if x < -hw * 0.4 else 0) - (1 if _h(i // 3, j // 3, 9) < 0.15 else 0)]
    tw = w * 0.6 / 5
    for k in range(5):                                      # 胸壁
        if k % 2 == 0:
            x0 = cx - w * 0.3 + k * tw
            for j in range(int(top - 5), int(top)):
                for i in range(int(x0), int(x0 + tw)):
                    px[(i, j)] = gold[4] if j == int(top - 5) else iron[5]
    gw, gt = w * 0.13, h * 0.62                              # 門
    for j in range(int(gt), int(bot)):
        for i in range(int(cx - gw) - 1, int(cx + gw) + 2):
            x, y = i + 0.5 - cx, j + 0.5
            inside = abs(x) < gw and (y > gt + gw or math.hypot(x, y - (gt + gw)) < gw)
            rim = abs(x) < gw + 1.2 and (y > gt + gw or math.hypot(x, y - (gt + gw)) < gw + 1.2)
            if inside:
                px[(i, j)] = g[5] if y > bot - 4 else g[3]
            elif rim:
                px[(i, j)] = gold[5]
    for row in (0.32, 0.48):                                 # 鋲
        y = top + (bot - top) * row
        for k in range(-3, 4):
            px[(int(cx + k * w * 0.075), int(y))] = gold[6]
    _line(px, (cx + w * 0.22, top + 3), (cx + w * 0.05, top + (bot - top) * 0.4), g[5], 1.0)   # 割れ目の熾
    _line(px, (cx + w * 0.05, top + (bot - top) * 0.4), (cx + w * 0.12, top + (bot - top) * 0.55), g[5], 1.0)
    return _clip(_outline(px, dk[3]), w, h)


def blaze(w, h, R):
    """焼け砦の背の炎（_glow で光る）: 下から立ちのぼる 7 枚の炎の舌（真ん中が高い・外へ反る）と火の粉。"""
    px = {}
    g, gold = R['glow'], R['bone']
    cx = w / 2
    for k in range(7):
        o = (k - 3) / 3
        bx = cx + o * w * 0.36
        ht = h * (0.95 - 0.32 * abs(o) - 0.06 * (k % 2))
        bw = w * 0.085
        pts = []
        for s in range(21):
            t = s / 20
            x = bx + o * w * 0.12 * t ** 1.5 + math.sin(t * 5 + k) * 1.2
            pts.append((x - bw * (1 - t) ** 0.8, h - 1 - ht * t))
        for s in range(20, -1, -1):
            t = s / 20
            x = bx + o * w * 0.12 * t ** 1.5 + math.sin(t * 5 + k) * 1.2
            pts.append((x + bw * (1 - t) ** 0.8, h - 1 - ht * t))

        def col(i, j, bx=bx, ht=ht):
            t = (h - 1 - (j + 0.5)) / ht
            return g[7] if t < 0.35 else (g[6] if t < 0.7 else g[5])
        _poly(px, pts, col)
    for k in range(9):
        px[(int(w * (0.1 + 0.8 * _h(k, 1, 4))), int(h * (0.05 + 0.4 * _h(k, 2, 4))))] = g[7]
    return _clip(_outline(px, gold[3]), w, h)


def ropering(w, h, R):
    """縄の人の背の縄の輪: 撚った縄の大きな輪（斜めの撚り目）・4 か所の結び目・中に逆さの三角に張った縄と紫の目・下に垂れる 3 本の結んだ縄（先がほつれる）。"""
    px = {}
    rope, c2, g, dk = R['bone'], R['cloth2'], R['glow'], R['dark']
    cx, cy = w / 2, w / 2
    ro = w / 2 - 3
    ri = ro - 3.6
    for k, (ox, L) in enumerate(((-0.42, 0.62), (0.0, 0.8), (0.42, 0.55))):      # 垂れる縄
        x = cx + ox * ro
        y0 = cy + math.sqrt(max(0, ro * ro - (ox * ro) ** 2)) - 2
        y1 = min(h - 3, y0 + (h - y0) * L)
        _line(px, (x, y0), (x + (k - 1) * 0.8, y1), rope[5], 1.8)
        _arc(px, x + (k - 1) * 0.4, y0 + (y1 - y0) * 0.45, 1.6, 0, 360, rope[6], 1.4)
        for f in (-1, 0, 1):
            _line(px, (x + (k - 1) * 0.8, y1), (x + (k - 1) * 0.8 + f * 1.4, y1 + 2.5), c2[6], 0.6)
    tri = [(cx + ri * math.cos(math.radians(a)), cy - ri * math.sin(math.radians(a))) for a in (150, 30, 270)]
    for p, q in zip(tri, tri[1:] + tri[:1]):
        _line(px, p, q, rope[4], 1.1)
    _band(px, w, h, cx, cy, ri, ro, lambda x, y, d, a: rope[6 if (int(a / 6 + d) % 3) == 0 else (5 if y - x > 0 else 4)] if ri + 0.7 < d < ro - 0.7 else rope[2])
    for a in (45, 135, 225, 315):
        x, y = cx + (ri + ro) / 2 * math.cos(math.radians(a)), cy - (ri + ro) / 2 * math.sin(math.radians(a))
        _shape(px, x, y, a, 5, lambda u, v: rope[7] if u * u / 9 + v * v / 4 < 1 else (rope[3] if u * u / 12 + v * v / 6 < 1 else None))
    _diamond(px, cx, cy, 3, g)
    return _clip(_outline(px, dk[3]), w, h)


# ---------------------------------------------------------------- エピックの背の紋

def ramhorn(w, h, R):
    """雷角の毛皮鎧の背の紋: 外へ巻く 2 本の巻き角（節の明暗）と、間を走る黄の稲妻、真ん中に銅の留め。"""
    px = {}
    horn, cu, g, dk = R['cloth2'], R['bone'], R['glow'], R['dark']
    cx, cy = w / 2, h * 0.45
    for sd in (-1, 1):
        pts = []
        for s in range(60):
            t = s / 59
            a = math.radians(100 - 400 * t)
            r = w * 0.3 * (1 - 0.72 * t)
            ox = cx + sd * w * 0.2
            pts.append((ox + sd * r * math.cos(a) * -1, cy - r * math.sin(a) * 0.95))
        for n, (p, q) in enumerate(zip(pts, pts[1:])):
            t = n / 59
            _line(px, p, q, horn[6 if (n // 5) % 2 == 0 else 5], 5.0 - 3.6 * t)
    bolt = [(cx + 2, h * 0.08), (cx - 3, h * 0.48), (cx + 1.5, h * 0.5), (cx - 2.5, h * 0.95)]
    for p, q in zip(bolt, bolt[1:]):
        _line(px, p, q, g[6], 2.0)
    _shape(px, cx, cy + 1, 0, 4, lambda u, v: cu[6] if u * u + v * v < 7 else (cu[4] if u * u + v * v < 11 else None))
    return _clip(_outline(px, dk[3]), w, h)


def embers(w, h, R):
    """灰炉の焼き革の背の紋: 熾の数珠の輪（熾の玉 12 と灰の玉が交互）と、真ん中に立つ炎のしずく。"""
    px = {}
    sea, cu, g, dk = R['sea'], R['bone'], R['glow'], R['dark']
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2 - 4
    _arc(px, cx, cy, r, 0, 360, sea[3], 0.8)
    for k in range(12):
        a = math.radians(k * 30 + 15)
        x, y = cx + r * math.cos(a), cy - r * math.sin(a)
        ramp = g if k % 2 == 0 else sea
        _shape(px, x, y, 0, 3, lambda u, v, ramp=ramp: ramp[7 if u + v > 0.5 else 5] if u * u + v * v < 5 else None)
    _shape(px, cx, cy, 90, 10, lambda u, v: (g[7] if abs(v) < 1 and u < 2 else cu[6]) if (u > -4 and abs(v) < (4 - (u + 4) * 0.42 if u > -1 else math.sqrt(max(0, 9 - (u + 1) ** 2))) ) else None)
    return _clip(_outline(px, dk[3]), w, h)


def salt(w, h, R):
    """塩晶の鎧の背の紋: 下の真ん中から扇に伸びる 5 本の六角の結晶柱（真ん中が長い、光る面の筋、尖った先）。"""
    px = {}
    sea, bone, g, dk = R['sea'], R['bone'], R['glow'], R['dark']
    cx, by = w / 2, h - 2
    for k, (ang, L) in enumerate(((140, 0.6), (115, 0.8), (90, 0.95), (65, 0.78), (40, 0.58))):
        Lp = (h - 3) * L
        wd = 3.2 if k == 2 else 2.6

        def f(u, v, Lp=Lp, wd=wd, k=k):
            if u < 0 or u > Lp:
                return None
            hw = wd if u < Lp - wd * 1.6 else wd * (Lp - u) / (wd * 1.6)
            if abs(v) > hw:
                return None
            if abs(v) < 0.6:
                return g[6] if k == 2 else bone[7]
            return bone[6] if v > 0 else sea[6]
        _shape(px, cx, by, ang, h, f)
    return _clip(_outline(px, dk[3]), w, h)


def lampsign(w, h, R):
    """灯守の法衣の背の紋: 尖ったアーチの灯の窓（金の枠・紫の奥）に光る炎の菱形、上へ放つ短い光 7 本。"""
    px = {}
    gold, c2, g, dk = R['bone'], R['cloth2'], R['glow'], R['dark']
    cx = w / 2
    top, bot = h * 0.3, h - 2
    hw = w * 0.26
    for j in range(int(top), int(bot)):
        for i in range(int(cx - hw) - 2, int(cx + hw) + 3):
            x, y = i + 0.5 - cx, j + 0.5
            # 尖ったアーチ: 2 つの円の交わり
            arch_ok = lambda m: (y > top + hw * 1.2) or (math.hypot(x + hw * 0.6 + m, y - (top + hw * 1.2)) < hw * 1.6 + m and math.hypot(x - hw * 0.6 - m, y - (top + hw * 1.2)) < hw * 1.6 + m)
            if abs(x) < hw - 1.4 and arch_ok(-1.4):
                px[(i, j)] = c2[3]
            elif abs(x) < hw and arch_ok(0):
                px[(i, j)] = gold[6 if x < 0 else 5]
    _diamond(px, cx, (top + bot) / 2 + 2, 4, g)
    for k in range(7):
        a = math.radians(30 + k * 20)
        _line(px, (cx + hw * 1.2 * math.cos(a), top + 2 - hw * 0.6 * math.sin(a)), (cx + hw * 1.9 * math.cos(a), top + 2 - hw * 1.3 * math.sin(a)), g[6], 1.0)
    _line(px, (cx - hw, bot - 1), (cx + hw, bot - 1), gold[4], 1.2)
    return _clip(_outline(px, dk[3]), w, h)


def smoke(w, h, R):
    """煤の外套の背の紋: 下の熾から立ちのぼって巻く煙 3 筋（灰）と火の粉。"""
    px = {}
    c2, sea, g, dk = R['cloth2'], R['sea'], R['glow'], R['dark']
    for k, ox in enumerate((-0.22, 0.0, 0.22)):
        x0 = w / 2 + ox * w
        pts = []
        for s in range(50):
            t = s / 49
            pts.append((x0 + math.sin(t * 5.5 + k * 2) * w * 0.12 * t, h - 4 - (h * (0.75 + 0.15 * (k == 1)) - 4) * t))
        ex, ey = pts[-1]
        for s in range(1, 16):
            a = s / 15 * math.pi * 1.4
            pts.append((ex + 3 * math.sin(a) * (1 if k != 0 else -1), ey - 3 * (1 - math.cos(a))))
        for n, (p, q) in enumerate(zip(pts, pts[1:])):
            t = n / len(pts)
            _line(px, p, q, c2[6] if t < 0.5 else c2[5], 3.2 - 1.8 * t)
    _shape(px, w / 2, h - 3, 0, 6, lambda u, v: g[6] if u * u / 25 + v * v / 4 < 1 else None)
    for k in range(6):
        px[(int(w * (0.15 + 0.7 * _h(k, 3, 8))), int(h * (0.1 + 0.6 * _h(k, 4, 8))))] = g[7]
    return _clip(_outline(px, dk[3]), w, h)


def gate(w, h, R):
    """深みの番兵鎧の背の紋: 銀の枠の尖ったアーチの門、緑青の扉 2 枚の間に光る隙間、頂に光る要石、扉に銀の帯 2 本。"""
    px = {}
    silver, door, g, dk = R['bone'], R['cloth'], R['glow'], R['dark']
    cx = w / 2
    top, bot = 2, h - 1
    hw = w * 0.34
    yc = top + hw * 1.3
    for j in range(int(top), int(bot)):
        for i in range(int(cx - hw) - 1, int(cx + hw) + 2):
            x, y = i + 0.5 - cx, j + 0.5

            def inside(m):
                return abs(x) < hw + m and (y > yc or (math.hypot(x + hw * 0.55, y - yc) < hw * 1.55 + m and math.hypot(x - hw * 0.55, y - yc) < hw * 1.55 + m))
            if inside(-1.6):
                if abs(x) < 0.8:
                    px[(i, j)] = g[6]
                elif abs(y - (yc + 3)) < 0.7 or abs(y - (bot - 5)) < 0.7:
                    px[(i, j)] = silver[5]
                else:
                    px[(i, j)] = door[5 if x < 0 else 4]
            elif inside(0):
                px[(i, j)] = silver[6 if x < 0 else 5]
    _diamond(px, cx, top + 2.5, 3, g)
    return _clip(_outline(px, dk[3]), w, h)


PICS = {'aura': aura, 'peal': peal, 'moon': moon, 'crane': crane, 'libra': libra, 'arch': arch,
        'bell': bell, 'kasumi': kasumi,
        'astrolabe': astrolabe, 'cloudwing': cloudwing, 'keep': keep, 'blaze': blaze, 'ropering': ropering,
        'ramhorn': ramhorn, 'embers': embers, 'salt': salt, 'lampsign': lampsign, 'smoke': smoke, 'gate': gate}


# ---------------------------------------------------------------- 神器の共通の特徴の案（2026-11-24、後光の代わり）

def sigil(w, h, R):
    """足元に寝かせて浮かぶ紋の輪（_glow）: 二重の輪・間に刻み 24・内に 8 つ角の星・外に 4 つの菱形。上から見た絵。"""
    px = {}
    g, gold = R['glow'], R['bone']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 2
    _arc(px, cx, cy, ro, 0, 360, g[7], 1.6)
    _arc(px, cx, cy, ro * 0.78, 0, 360, g[6], 1.2)
    for k in range(24):
        a = math.radians(k * 15)
        _line(px, (cx + ro * 0.8 * math.cos(a), cy - ro * 0.8 * math.sin(a)), (cx + ro * 0.97 * math.cos(a), cy - ro * 0.97 * math.sin(a)), g[5 + k % 2], 0.8)
    pts = [(cx + ro * (0.66 if k % 2 == 0 else 0.3) * math.cos(math.radians(k * 22.5)), cy - ro * (0.66 if k % 2 == 0 else 0.3) * math.sin(math.radians(k * 22.5))) for k in range(16)]
    for p0, p1 in zip(pts, pts[1:] + pts[:1]):
        _line(px, p0, p1, g[6], 1.0)
    for a in (0, 90, 180, 270):
        _diamond(px, cx + (ro + 0.5) * math.cos(math.radians(a)), cy - (ro + 0.5) * math.sin(math.radians(a)), 2, g)
    return _clip(_outline(px, gold[3]), w, h)


def shard(w, h, R):
    """体のまわりに浮く神器の欠片（_glow）: 縦長の菱形の結晶、明るい芯と金の縁。"""
    px = {}
    g, gold = R['glow'], R['bone']
    cx, cy = w / 2, h / 2
    for j in range(h):
        for i in range(w):
            x, y = abs(i + 0.5 - cx) / (w / 2 - 1), abs(j + 0.5 - cy) / (h / 2 - 1)
            d = x + y
            if d < 1:
                px[(i, j)] = g[7] if d < 0.35 else (g[6] if d < 0.7 else g[5])
    return _clip(_outline(px, gold[4]), w, h)


def motes(w, h, R):
    """背の絵の縁を回る光の粒（_glow）: 大小の粒 28 個を楕円に並べ、粒の間に細い光の線。"""
    px = {}
    g, gold = R['glow'], R['bone']
    cx, cy = w / 2, h / 2
    rx, ry = w / 2 - 3, h / 2 - 3
    n = 28
    pts = [(cx + rx * math.cos(2 * math.pi * k / n), cy - ry * math.sin(2 * math.pi * k / n)) for k in range(n)]
    for k, (x, y) in enumerate(pts):
        if k % 3 != 2:
            q = pts[(k + 1) % n]
            _line(px, (x, y), q, g[5], 0.6)
        r = 2 if k % 4 == 0 else 1
        _diamond(px, x, y, r, g) if r > 1 else px.__setitem__((int(x), int(y)), g[7])
    return _clip(_outline(px, gold[3]), w, h)


PICS.update({'sigil': sigil, 'shard': shard, 'motes': motes})


def _sigil_v(w, h, R, star, motif):
    """足元の紋の輪の組ごとの違い: 内の星の角の数と、輪の間の小さな印（bell 鐘・wave 霞・ray 光・split 白黒・stone 迫石・spoke 輻）"""
    px = {}
    g, gold, sea, dark = R['glow'], R['bone'], R['sea'], R['cloth2']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 2
    _arc(px, cx, cy, ro, 0, 360, g[7], 1.6)
    _arc(px, cx, cy, ro * 0.74, 0, 360, g[6], 1.2)
    n = star * 2
    pts = [(cx + ro * (0.62 if k % 2 == 0 else 0.3) * math.cos(math.pi * k / star), cy - ro * (0.62 if k % 2 == 0 else 0.3) * math.sin(math.pi * k / star)) for k in range(n)]
    for p0, p1 in zip(pts, pts[1:] + pts[:1]):
        _line(px, p0, p1, g[6], 1.0)
    rm = ro * 0.87
    for k in range(12):
        a = math.radians(k * 30 + 15)
        x, y = cx + rm * math.cos(a), cy - rm * math.sin(a)
        if motif == 'bell':
            _shape(px, x, y, math.degrees(a) - 90, 3, lambda u, v: g[7] if (-2 < u < 2 and abs(v) < 0.8 + (u + 2) * 0.35) else None)
        elif motif == 'wave':
            _arc(px, x, y, 1.8, 0, 180, g[6], 0.8)
        elif motif == 'ray':
            _line(px, (cx + ro * 0.78 * math.cos(a), cy - ro * 0.78 * math.sin(a)), (cx + ro * 0.97 * math.cos(a), cy - ro * 0.97 * math.sin(a)), g[7], 1.2)
        elif motif == 'split':
            _diamond(px, x, y, 1, g)
        elif motif == 'stone':
            if k % 4 != 3:
                _arc(px, cx, cy, rm, k * 30 + 5, k * 30 + 25, g[6], 2.0)
        elif motif == 'spoke':
            _line(px, (cx + ro * 0.3 * math.cos(a), cy - ro * 0.3 * math.sin(a)), (cx + ro * 0.74 * math.cos(a), cy - ro * 0.74 * math.sin(a)), g[5], 0.8)
    if motif == 'split':                                   # 天秤: 左右で白と黒の半月
        _arc(px, cx, cy, ro * 0.5, 90, 270, sea[7], 1.4)
        _arc(px, cx, cy, ro * 0.5, -90, 90, dark[3], 1.4)
    for a in (0, 90, 180, 270):
        _diamond(px, cx + (ro + 0.5) * math.cos(math.radians(a)), cy - (ro + 0.5) * math.sin(math.radians(a)), 2, g)
    return _clip(_outline(px, gold[3]), w, h)


for _k, _st, _m in (('sigilbell', 8, 'bell'), ('sigilmist', 6, 'wave'), ('sigillamp', 12, 'ray'),
                    ('sigilscale', 4, 'split'), ('sigilbridge', 5, 'stone'), ('sigilhalo', 8, 'spoke')):
    PICS[_k] = (lambda st, m: (lambda w, h, R: _sigil_v(w, h, R, st, m)))(_st, _m)
