# -*- coding: utf-8 -*-
"""防具の 2D の絵の板（名前に pic_<絵の名前> が入る箱）。armor_paint.py が north の面にこの絵を塗り、
ほかの面と絵の外は透明にする（防具は cutout・裏も描くので、透明の画素は抜け、後ろからも見える）。
絵の板だけ画素が細かい（PIC_TEX = 0.5 単位で 1 画素。武器の板の絵と同じ）。

draw(key, w, h, face, ramps) -> {(i, j): '#rrggbb'}（j=0 が上の行。無い画素は透明）
  banner : 塔の旗（天秤の紋・下が裂けて垂れる）。i の小さい側（north の面を前から見て左 = 体の右）が白い石、反対が暗い石。
  wisp   : 紫の力の筋（太い弧 2 本、先で巻く、暗い縁取り）。名前を _glow で終えると光る。
  ring   : 背中に浮く石の輪（胸壁の歯・金の縁・左の暗い側が割れて欠片が浮く）。orbit : その内側の光る紫の弧と菱形。
  chime  : 鐘守の背の音の波紋（金の弧 3 本・端に小鐘）。wing : 霞渡りの背の羽の翼。radiance : 灯守の灯籠の光芒（_glow）。halo : 光輪の衣の頭の後ろの割れた光輪。
"""
import math

PIC_TEX = 0.5


def _h(x, y, s):
    v = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xffffffff
    v = (v ^ (v >> 13)) * 1274126177 & 0xffffffff
    return (v ^ (v >> 16)) / 0xffffffff


def _line(px, p0, p1, col, width=1.0):
    (x0, y0), (x1, y1) = p0, p1
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for s in range(n + 1):
        t = s / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        r = width / 2
        for i in range(int(math.floor(x - r)), int(math.ceil(x + r)) + 1):
            for j in range(int(math.floor(y - r)), int(math.ceil(y + r)) + 1):
                if (i + 0.5 - x) ** 2 + (j + 0.5 - y) ** 2 <= max(0.3, r * r):
                    px[(i, j)] = col


def banner(w, h, R):
    px = {}
    white, dark, gold, glow = R['sea'], R['cloth2'], R['bone'], R['glow']
    half = w // 2
    tail = max(6, h // 5)              # 下の裂けた部分の高さ
    for j in range(2, h):
        for i in range(w):
            ramp = white if i < half else dark
            k = 5 - (1 if (i % 8) in (6, 7) else 0) - (1 if j > h * 0.55 else 0)
            if i in (0, 1, w - 2, w - 1) or i in (half - 1, half):
                k -= 1                    # 縁と真ん中の縫い目
            px[(i, j)] = ramp[max(1, k)]
    for i in range(-1, w + 1):            # 上の金の竿（両端に玉）
        for j in (0, 1):
            px[(i, j)] = gold[6 if j == 0 else 4]
    for i in (-2, w + 1):
        for j in (-1, 0, 1, 2):
            px[(i, j)] = gold[5]
    # 天秤の紋（右＝i の小さい側が上がる）
    cx = w / 2
    top, post_bot = h * 0.18, h * 0.62
    _line(px, (cx - 0.5, top), (cx - 0.5, post_bot), gold[6], 2)
    _line(px, (cx - 6, post_bot + 1), (cx + 5, post_bot + 1), gold[4], 2)       # 台
    bl, br = (3, top + 4 - 3), (w - 4, top + 4 + 3)                              # 竿の両端（左が高い）
    _line(px, bl, br, gold[6], 1.6)
    for (bx, by), ramp in ((bl, white), (br, dark)):
        _line(px, (bx, by), (bx - 2.5, by + 7), gold[3], 1)
        _line(px, (bx, by), (bx + 2.5, by + 7), gold[3], 1)
        for i in range(int(bx - 4), int(bx + 5)):                             # 皿
            px[(i, int(by + 7))] = gold[6]
            if abs(i - bx) < 3:
                px[(i, int(by + 8))] = gold[4]
    for dj in range(-3, 4):                                                    # 支点の紫の菱形
        for di in range(-(3 - abs(dj)), 4 - abs(dj)):
            px[(int(cx - 0.5) + di, int(top) + dj)] = glow[7 - abs(dj) - abs(di) // 2]
    # 下の裂け目: V の尾 4 本と穴、ほつれ
    for j in range(h - tail, h):
        d = (j - (h - tail)) / tail
        for i in range(w):
            q = (i % (w // 4)) / (w // 4)
            inside = abs(q - 0.5) < 0.5 - 0.5 * d
            hole = _h(i // 2, j // 2, 3) < 0.08 + 0.2 * d
            if not inside or hole:
                px.pop((i, j), None)
    for i in range(w):                                                        # 縁のほつれ（端の 1 画素を暗く）
        for j in range(h - 1, 1, -1):
            if (i, j) in px:
                ramp = white if i < half else dark
                px[(i, j)] = ramp[2]
                break
    return px


def wisp(w, h, R):
    """紫の力の筋: 左下から右上へ流れて先で巻く太い弧 2 本（根元が太く明るい・暗い紫の縁取りで昼も読める）"""
    px = {}
    g, edge = R['glow'], R['cloth'][1]
    for k in range(2):
        y0 = h * (0.92 - 0.34 * k)
        pts = []
        for s in range(60):
            t = s / 59
            x = w * (0.06 + 0.74 * t)
            y = y0 - h * 0.32 * math.sin(t * math.pi * 0.9)
            pts.append((x, y))
        ex, ey = pts[-1]
        rr = min(w, h) * (0.09 + 0.03 * k)
        for s in range(1, 24):                                               # 先で巻く
            a = s / 23 * math.pi * 1.5
            pts.append((ex + rr * math.sin(a), ey - rr * (1 - math.cos(a))))
        for n in range(len(pts) - 1):
            t = n / len(pts)
            col = g[7] if t < 0.3 else (g[6] if t < 0.65 else g[5])
            _line(px, pts[n], pts[n + 1], col, 4.0 - 2.0 * t)
    for (i, j) in list(px):                                                  # 縁取り 1 画素
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + di, j + dj) not in px:
                px.setdefault(('e', i + di, j + dj), edge)
    out = {}
    for key, c in px.items():
        if key[0] == 'e':
            out.setdefault((key[1], key[2]), c)
        else:
            out[key] = c
    return {(i, j): c for (i, j), c in out.items() if 0 <= i < w and 0 <= j < h}


def ring(w, h, R):
    """背中に浮く石の輪（板は Y 180° で後ろ向き）: 外に胸壁の歯、内と外に金の縁。i の大きい側（体の右）は白い石で欠けなし、
    反対（体の左）は暗い石で 2 か所が割れ、欠片が外へずれて浮く。"""
    px = {}
    white, dark, gold = R['sea'], R['cloth2'], R['bone']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 3.5           # 輪の外の半径（歯はこの外）
    ri = ro - 6                        # 輪の内の半径
    gaps = ((150, 161), (204, 213))    # 左側の割れ目（度。0 = 右、反時計回り、j は下向きなので角度は -y で取る）
    drift = ((161, 182, 2.2), (213, 232, 1.6))   # 割れ目の先の欠片が外へずれる量
    for j in range(h):
        for i in range(w):
            x, y = i + 0.5 - cx, cy - (j + 0.5)
            d = math.hypot(x, y)
            a = math.degrees(math.atan2(y, x)) % 360
            off = 0.0
            for a0, a1, o in drift:
                if a0 <= a < a1:
                    off = o
            dd = d - off
            left = x < 0               # 板は Y に 180° 回して後ろへ向ける。後ろから見て左 = 体の左 = 暗い側
            if any(a0 <= a < a1 for a0, a1 in gaps):
                continue
            ramp = dark if left else white
            tooth = ro <= dd < ro + 3 and int(a // 18) % 2 == 0
            if ri <= dd < ro or tooth:
                lit = 1 if (y - x) > 0 else -1                    # 左上が明るい
                if tooth:                                         # 胸壁の歯 = 白い石 / 暗い石（金の帯の外で背中の色と分かれる）
                    k = 4 + lit - (1 if _h(int(a // 7), int(dd), 5) < 0.25 else 0)
                    px[(i, j)] = ramp[max(1, min(6, k))]
                elif dd < ri + 1 or dd >= ro - 1:
                    px[(i, j)] = gold[2]                          # 帯の縁は暗い金
                else:
                    k = 5 + lit - (1 if int(a) % 18 == 0 else 0)  # 金の帯（継ぎ目で 1 段暗く）
                    px[(i, j)] = gold[max(2, min(7, k))]
    # 四方の金の菱形（左右の真横は割れ目に重ならない）
    for ang in (90, 270, 0, 180):
        bx = cx + (ro - 3) * math.cos(math.radians(ang))
        by = cy - (ro - 3) * math.sin(math.radians(ang))
        for dj in range(-3, 4):
            for di in range(-(3 - abs(dj)), 4 - abs(dj)):
                px[(int(bx) + di, int(by) + dj)] = gold[7 - abs(dj) - abs(di)]
    return px


def orbit(w, h, R):
    """輪の内側を回る紫の力（光る）: 3 つに途切れる太い弧と、真ん中の菱形。暗い縁取り。"""
    px = {}
    g, edge = R['glow'], R['cloth'][1]
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2 - 3.5 - 9
    for a0, a1 in ((20, 120), (140, 250), (270, 350)):
        for s in range(90):
            t = s / 89
            a = math.radians(a0 + (a1 - a0) * t)
            wd = 1.2 + 2.4 * math.sin(t * math.pi)                # 弧の真ん中が太い
            _line(px, (cx + r * math.cos(a), cy - r * math.sin(a)),
                  (cx + r * math.cos(a), cy - r * math.sin(a)), g[7] if 0.3 < t < 0.7 else g[6], wd)
    for dj in range(-5, 6):                                       # 真ん中の菱形
        for di in range(-(5 - abs(dj)), 6 - abs(dj)):
            px[(int(cx) + di, int(cy) + dj)] = g[7 - (abs(dj) + abs(di)) // 2]
    for (i, j) in list(px):
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (i + di, j + dj) not in px:
                px[('e', i + di, j + dj)] = edge
    out = {}
    for key, c in px.items():
        if key[0] == 'e':
            out.setdefault((key[1], key[2]), c)
        else:
            out[key] = c
    return out


def _outline(px, edge):
    """絵の外側 1 画素に暗い縁取りを足す（昼でも空に溶けないように）"""
    out = dict(px)
    for (i, j) in list(px):
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            out.setdefault((i + di, j + dj), edge)
    return out


def _diamond(px, cx, cy, r, ramp):
    for dj in range(-r, r + 1):
        for di in range(-(r - abs(dj)), r - abs(dj) + 1):
            px[(int(cx) + di, int(cy) + dj)] = ramp[max(1, 7 - abs(dj) - abs(di) // 2)]


def chime(w, h, R):
    """鐘の音の波紋（鐘守の背）: 下の真ん中から上へ広がる金の弧 3 本、弧の両端に吊った小鐘、外の弧に目盛り、弧の間に音の粒（菱形）。"""
    px = {}
    gold, sea, dark = R['bone'], R['sea'], R['dark']
    cx, cy = w / 2, h - 1.5
    radii = (h * 0.42, h * 0.66, h * 0.9)
    for k, r in enumerate(radii):
        a0, a1 = 18 + 6 * k, 162 - 6 * k
        for s in range(160):
            t = s / 159
            a = math.radians(a0 + (a1 - a0) * t)
            x, y = cx + r * math.cos(a), cy - r * math.sin(a)
            lit = 7 if 0.35 < t < 0.65 else (6 if 0.15 < t < 0.85 else 5)
            _line(px, (x, y), (x, y), gold[lit - (1 if k == 0 else 0)], 4.4 - 0.6 * k)
        if k == 2:                                          # 外の弧の目盛り
            for m in range(1, 12):
                a = math.radians(a0 + (a1 - a0) * m / 12)
                _line(px, (cx + (r - 2.5) * math.cos(a), cy - (r - 2.5) * math.sin(a)),
                      (cx + (r + 2.2) * math.cos(a), cy - (r + 2.2) * math.sin(a)), gold[4], 1.0)
        for a in (a0, a1):                                  # 弧の端に吊った小鐘（紐 -> 鐘 -> 舌）
            bx, by = cx + r * math.cos(math.radians(a)), cy - r * math.sin(math.radians(a))
            _line(px, (bx, by), (bx, by + 3), gold[3], 1)
            for dj in range(0, 6):
                hw = 1 + dj * 0.6
                for di in range(int(-hw), int(hw) + 1):
                    px[(int(bx) + di, int(by + 3 + dj))] = gold[6 - (1 if abs(di) >= hw - 0.5 else 0) - (1 if dj > 3 else 0)]
            px[(int(bx), int(by + 9))] = sea[7]
    for k in range(2):                                      # 弧の間の音の粒
        r = (radii[k] + radii[k + 1]) / 2
        for a in (50, 90, 130):
            _diamond(px, cx + r * math.cos(math.radians(a)), cy - r * math.sin(math.radians(a)), 2, sea)
    _diamond(px, cx, cy - radii[0] * 0.45, 4, gold)       # 波紋の芯（鐘の口）
    return _outline(px, dark[3])


def wing(w, h, R):
    """霞渡りの背の翼（2D）: 真ん中の左右から外下へ広がる羽。長い風切り羽は白 -> 空色 -> 先が金、上に短い雨覆い（白・金の軸）。"""
    px = {}
    sea, cloth, gold, cloth2, dark = R['sea'], R['cloth'], R['bone'], R['cloth2'], R['dark']
    cx = w / 2
    for side in (-1, 1):
        rx, ry = cx + side * 3, h * 0.22
        n = 12
        for f in range(n):                                  # 風切り羽（外ほど長く上向き）
            t = f / (n - 1)
            ang = math.radians(8 - 78 * t)
            L = (w / 2 - 3) * (1.0 - 0.45 * t)
            ex, ey = rx + side * L * math.cos(ang), ry - L * math.sin(ang)
            for s in range(40):
                u = s / 39
                x, y = rx + (ex - rx) * u, ry + (ey - ry) * u
                col = (cloth[6] if u < 0.45 else sea[6] if u < 0.85 else gold[6])
                _line(px, (x, y), (x, y), col, 6.5 - 3.5 * u)
            _line(px, (rx + (ex - rx) * 0.15, ry + (ey - ry) * 0.15), (ex, ey), cloth2[4], 0.8)
        for f in range(9):                                  # 雨覆い（短い・上に重なる）
            t = f / 8
            ang = math.radians(14 - 70 * t)
            L = (w / 2 - 3) * (0.5 - 0.15 * t)
            ex, ey = rx + side * L * math.cos(ang), ry - L * math.sin(ang)
            for s in range(30):
                u = s / 29
                x, y = rx + (ex - rx) * u, ry + (ey - ry) * u
                _line(px, (x, y), (x, y), cloth[7] if u < 0.7 else cloth[5], 6.0 - 2.5 * u)
            _line(px, (rx, ry), (ex, ey), gold[6], 0.9)
        _diamond(px, rx, ry, 2, gold)
    for (i, j) in list(px):                                 # 体の真ん中は抜く
        if abs(i + 0.5 - cx) < 2.2:
            px.pop((i, j))
    return _outline(px, dark[3])


def radiance(w, h, R):
    """灯守の頭上の灯籠の後ろの光芒（_glow で光る）: 長短交互の光の筋 16 本と、真ん中の光の輪。金の縁取り。"""
    px = {}
    g, gold = R['glow'], R['bone']
    cx, cy = w / 2, h / 2
    rmax = min(w, h) / 2 - 1.5
    for k in range(16):
        a = math.radians(k * 22.5 + 11.25)
        L = rmax if k % 2 == 0 else rmax * 0.66
        r0 = rmax * 0.42
        for s in range(40):
            u = s / 39
            r = r0 + (L - r0) * u
            _line(px, (cx + r * math.cos(a), cy - r * math.sin(a)), (cx + r * math.cos(a), cy - r * math.sin(a)),
                  g[7] if u < 0.4 else (g[6] if u < 0.75 else g[5]), 3.0 - 2.2 * u)
    rr = rmax * 0.36
    for s in range(120):
        a = math.radians(s * 3)
        _line(px, (cx + rr * math.cos(a), cy - rr * math.sin(a)), (cx + rr * math.cos(a), cy - rr * math.sin(a)), g[6], 1.8)
    return _outline(px, gold[4])


def halo(w, h, R):
    """光輪の衣の頭の後ろの光輪（板は前向き。north の面を前から見て i の小さい側 = 体の右 = 天）:
    金の太い輪と内の細い輪を 12 本の輻でつなぐ。体の右は白い石の縁、体の左は暗い石で 2 か所割れて欠片がずれ、割れ目に紫。"""
    px = {}
    white, dark, gold, g, dk = R['sea'], R['cloth2'], R['bone'], R['glow'], R['dark']
    cx, cy = w / 2, h / 2
    ro = min(w, h) / 2 - 2.5
    ri, rin = ro - 4.5, (ro - 4.5) * 0.42
    gaps = ((20, 32), (318, 332))
    drift = ((32, 58, 1.8), (332, 352, 1.4))
    for j in range(h):
        for i in range(w):
            x, y = i + 0.5 - cx, cy - (j + 0.5)
            d = math.hypot(x, y)
            a = math.degrees(math.atan2(y, x)) % 360
            off = next((o for a0, a1, o in drift if a0 <= a < a1), 0.0)
            dd = d - off
            if any(a0 <= a < a1 for a0, a1 in gaps):
                if ri - 1 <= d < ro + 1:
                    px[(i, j)] = g[6]
                continue
            left = x > 0
            if ri <= dd < ro:
                if dd >= ro - 1.2:
                    px[(i, j)] = (dark if left else white)[5 if y > 0 else 4]
                else:
                    lit = 1 if (y - x) > 0 else 0
                    px[(i, j)] = gold[(5 if not left else 4) + lit]
            elif rin - 1 <= dd < rin + 0.6:
                px[(i, j)] = gold[6]
    for k in range(12):
        deg = (k * 30 + 15) % 360
        a = math.radians(deg)
        col = dark[5] if (deg <= 60 or deg >= 300) else gold[5]
        _line(px, (cx + rin * math.cos(a), cy - rin * math.sin(a)), (cx + ri * math.cos(a), cy - ri * math.sin(a)), col, 1.2)
    for ang in (90, 270, 180, 0):
        bx, by = cx + (ro - 2.2) * math.cos(math.radians(ang)), cy - (ro - 2.2) * math.sin(math.radians(ang))
        _diamond(px, bx, by, 3, g if ang == 0 else gold)
    return _outline(px, dk[3])


PICS = {'banner': banner, 'wisp': wisp, 'ring': ring, 'orbit': orbit,
        'chime': chime, 'wing': wing, 'radiance': radiance, 'halo': halo}

import armor_pics_more  # noqa: E402  後光・背の絵・エピックの紋（レア度の段）
PICS.update(armor_pics_more.PICS)


def draw(key, w, h, face, ramps):
    # 塗るのは north の面だけ（厚み 0 の板の north と south が重なってちらつくのを避ける）
    if face != 'north' or key not in PICS:
        return {}
    px = PICS[key](w, h, ramps)
    return {(i, j): c for (i, j), c in px.items() if 0 <= i < w and 0 <= j < h}
