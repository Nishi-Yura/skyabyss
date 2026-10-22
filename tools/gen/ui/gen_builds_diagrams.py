# -*- coding: utf-8 -*-
"""27 ビルドと職の案の図（SVG）を docs/review/builds/ に書く。 python tools/gen/ui/gen_builds_diagrams.py"""
import math, os
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'docs', 'review', 'builds')
os.makedirs(OUT, exist_ok=True)
FONT = "font-family=\"'Yu Gothic','Hiragino Sans','Zen Kaku Gothic New',sans-serif\""
BG = '#f4f5f7'; INK = '#1b2129'; MUT = '#5b6672'; LINE = '#9aa5b0'
COL = {'岩守り': '#a0693a', '風読み': '#2f8fb8', '潮歌い': '#2aa198', '灰の鍛え人': '#c0502d', '骨の射手': '#6b6fb8', '旅人': '#6c7a89'}
GOLD = '#c9a227'


def svg(w, h, body):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" %s>'
            '<rect width="%d" height="%d" fill="%s"/>%s</svg>' % (w, h, w, h, FONT, w, h, BG, body))


def text(x, y, s, size=13, fill=INK, anchor='middle', weight='normal'):
    return '<text x="%s" y="%s" font-size="%s" fill="%s" text-anchor="%s" font-weight="%s">%s</text>' % (x, y, size, fill, anchor, weight, s)


def box(x, y, w, h, fill, stroke=None, rx=6, dash=None, sw=1.5, op=None):
    d = ' stroke-dasharray="%s"' % dash if dash else ''
    o = ' opacity="%s"' % op if op is not None else ''
    return '<rect x="%s" y="%s" width="%s" height="%s" rx="%s" fill="%s" stroke="%s" stroke-width="%s"%s%s/>' % (x, y, w, h, rx, fill, stroke or 'none', sw, d, o)


def line(x1, y1, x2, y2, c=LINE, w=2, dash=None):
    d = ' stroke-dasharray="%s"' % dash if dash else ''
    return '<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s"%s/>' % (x1, y1, x2, y2, c, w, d)


def node(cx, cy, label, kind, col, r=26):
    """kind: 使 / 常 / 要"""
    s = ''
    if kind == '使':
        s += '<circle cx="%s" cy="%s" r="%s" fill="#ffffff" stroke="%s" stroke-width="2.5"/>' % (cx, cy, r, col)
        s += '<circle cx="%s" cy="%s" r="%s" fill="%s" opacity=".18"/>' % (cx, cy, r - 3, col)
    elif kind == '常':
        s += '<rect x="%s" y="%s" width="%s" height="%s" rx="8" fill="#ffffff" stroke="%s" stroke-width="2.5"/>' % (cx - r + 2, cy - r + 2, 2 * r - 4, 2 * r - 4, col)
    else:
        pts = [(cx, cy - r - 2), (cx + r + 2, cy), (cx, cy + r + 2), (cx - r - 2, cy)]
        s += '<polygon points="%s" fill="#fff7da" stroke="%s" stroke-width="3"/>' % (' '.join('%s,%s' % p for p in pts), GOLD)
    s += text(cx, cy + 4, label, 10 if len(label) > 4 else 12, INK)
    return s


def tree_anatomy():
    b = text(380, 30, '1 つの木の形（岩守りを例に）', 18, INK, weight='bold')
    col = COL['岩守り']
    rows = [(90, '行 1  Lv 1'), (190, '行 2  Lv 5'), (290, '行 3  Lv 12'), (390, '行 4  Lv 20')]
    for y, s in rows:
        b += text(40, y + 4, s, 12, MUT, 'start')
        b += line(130, y, 700, y, '#dfe3e7', 1)
    n = {'巨神斬り': (300, 90, '使'), '返し刃': (460, 90, '常'), '岩呼び': (300, 190, '使'), '岩の構え': (460, 190, '常'),
         '鯨尾薙ぎ': (380, 290, '使'), '守り石の約束': (380, 390, '要')}
    for a, c in (('巨神斬り', '岩呼び'), ('返し刃', '岩の構え'), ('岩呼び', '鯨尾薙ぎ'), ('岩の構え', '鯨尾薙ぎ'), ('鯨尾薙ぎ', '守り石の約束')):
        b += line(n[a][0], n[a][1], n[c][0], n[c][1], col, 3)
    for k, (x, y, kind) in n.items():
        b += node(x, y, k, kind, col)
    for i, s in enumerate(['広刃', '深刃', '地走り']):
        yy = 70 + i * 28
        b += box(560, yy, 110, 22, '#ffffff', col, 11)
        b += text(615, yy + 15, s, 12, INK)
    b += line(326, 90, 560, 81, col, 1.5, '4 3')
    b += text(615, 56, '刻印（3 択、1 点）', 12, MUT)
    b += text(615, 168, '使う技が段 3 で開く', 11, MUT)
    b += node(150, 470, '使う技', '使', col, 20) + text(185, 474, '枠に置く', 12, MUT, 'start')
    b += node(300, 470, '常に', '常', col, 20) + text(335, 474, '覚えただけで効く', 12, MUT, 'start')
    b += node(500, 470, '要', '要', col, 20) + text(535, 474, '癖のある常時の技（得と引き換え）', 12, MUT, 'start')
    b += text(380, 510, '1 木 = 使う 3 ＋ 常 2 ＋ 要 1。点: 使う技 8 段 ＋ 常 6 段 ＋ 要 1 ＋ 刻印 3 ＝ 18', 13, INK)
    return svg(760, 530, b)


def points_curve():
    W, H = 760, 420
    x0, y0, x1, y1 = 70, 360, 720, 60

    def px(l): return x0 + (x1 - x0) * (l - 1) / 49

    def py(p): return y0 - (y0 - y1) * p / 50

    cur = lambda l: (l + 1) // 2
    new = lambda l: (l + 1) // 2 if l <= 10 else l - 5
    b = text(380, 28, '技の点の曲線（今と提案）', 18, INK, weight='bold')
    for p in range(0, 51, 10):
        b += line(x0, py(p), x1, py(p), '#dfe3e7', 1) + text(x0 - 8, py(p) + 4, str(p), 11, MUT, 'end')
    for l in (15, 21, 27, 33, 40, 50):
        b += line(px(l), y0, px(l), y1, '#cfd5db', 1, '3 3') + text(px(l), y0 + 18, 'Lv ' + str(l), 11, MUT)
    b += text(px(33), y1 - 8, '四章の上限', 11, MUT)
    for fn, c in ((cur, MUT), (new, '#0a8fa6')):
        pts = ' '.join('%.1f,%.1f' % (px(l), py(fn(l))) for l in range(1, 51))
        b += '<polyline points="%s" fill="none" stroke="%s" stroke-width="3"/>' % (pts, c)
    for l in (15, 33, 50):
        b += '<circle cx="%.1f" cy="%.1f" r="4" fill="#0a8fa6"/>' % (px(l), py(new(l))) + text(px(l) - 6, py(new(l)) - 8, '%d' % new(l), 12, '#0a8fa6', 'end', 'bold')
        b += '<circle cx="%.1f" cy="%.1f" r="4" fill="%s"/>' % (px(l), py(cur(l)), MUT) + text(px(l) + 6, py(cur(l)) + 16, '%d' % cur(l), 12, MUT, 'start')
    b += text(100, 90, '提案 Lv50 で 45（約 2.5 本）', 13, '#0a8fa6', 'start', 'bold')
    b += text(100, 110, '今 Lv50 で 25（全 26 段のほぼ全部）', 13, MUT, 'start')
    b += text(380, 405, 'Lv 10 までは今と同じ。木 1 本 ≒ 18 点', 12, MUT)
    return svg(W, H, b)


def option_a():
    names = ['岩守り', '灰の鍛え人', '風読み', '骨の射手', '潮歌い']
    cx, cy, R = 380, 290, 190
    pos = {}
    for i, n in enumerate(names):
        a = -math.pi / 2 + 2 * math.pi * i / 5
        pos[n] = (cx + R * math.cos(a), cy + R * math.sin(a))
    b = text(380, 30, '案 A 職なし: 木の輪と橋', 18, INK, weight='bold')
    bridges = ['鍛え石', '火走り', '射手の足', '歌う矢', '寄せ波の壁']
    for i, n in enumerate(names):
        m = names[(i + 1) % 5]
        (x1, y1), (x2, y2) = pos[n], pos[m]
        b += line(x1, y1, x2, y2, LINE, 3)
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        b += '<polygon points="%s,%s %s,%s %s,%s %s,%s" fill="#fff7da" stroke="%s" stroke-width="2"/>' % (mx, my - 11, mx + 11, my, mx, my + 11, mx - 11, my, GOLD)
        dx, dy = (mx - cx), (my - cy)
        d = math.hypot(dx, dy)
        b += text(mx + dx / d * 46, my + dy / d * 46 + 4, bridges[i], 12, INK)
    for n in names:
        x, y = pos[n]
        b += '<circle cx="%.1f" cy="%.1f" r="46" fill="#ffffff" stroke="%s" stroke-width="4"/>' % (x, y, COL[n])
        b += '<circle cx="%.1f" cy="%.1f" r="42" fill="%s" opacity=".15"/>' % (x, y, COL[n])
        b += text(x, y - 2, n, 14, INK, weight='bold') + text(x, y + 16, '6 本', 11, MUT)
    b += '<circle cx="%s" cy="%s" r="52" fill="#ffffff" stroke="%s" stroke-width="4" stroke-dasharray="6 4"/>' % (cx, cy, COL['旅人'])
    b += text(cx, cy - 2, '旅人', 15, INK, weight='bold') + text(cx, cy + 16, '共通 6 本', 11, MUT)
    b += text(380, 525, '全部が最初から開いている。橋（菱形）は隣り合う 2 本に 3 点ずつで開く（5 本）。技 36 ＋ 橋 5 ＝ 41', 12, MUT)
    return svg(760, 545, b)


def option_b():
    names = ['風読み', '岩守り', '潮歌い', '灰の鍛え人', '骨の射手']
    b = text(380, 30, '案 B 始まりの職: 形見を選び、専用の木を持つ', 18, INK, weight='bold')
    b += box(30, 50, 700, 70, '#ffffff', LINE, 8)
    b += text(380, 76, '序盤（語り部のあと）: 台に五つの形見 → 1 つ手に取る → 職が決まる', 14, INK, weight='bold')
    b += text(380, 100, 'Lv 3 までに選ぶ。決めるまで技の点は使えない', 12, MUT)
    for i, n in enumerate(names):
        x = 40 + i * 140
        b += line(x + 62, 120, x + 62, 160, COL[n], 3)
        b += box(x, 160, 124, 120, '#ffffff', COL[n], 8, None, 3)
        b += text(x + 62, 186, n, 15, INK, weight='bold')
        b += text(x + 62, 206, '専用の木 6 本', 11, MUT)
        b += text(x + 62, 224, '＋ 天性 1（点なし）', 11, MUT)
        b += text(x + 62, 246, '主ステータス +3', 11, MUT)
        b += text(x + 62, 266, '形見の品を持って始まる', 11, MUT)
    b += box(30, 310, 330, 78, '#eef2f5', COL['旅人'], 8, '6 4', 2.5)
    b += text(195, 336, '共通の木 旅人（全員が取れる 6 本）', 13, INK, weight='bold')
    b += text(195, 358, '合図・備えの報い・野営の手・目利き', 11, MUT) + text(195, 376, '逃げ足・旅の手帖（要）', 11, MUT)
    b += box(400, 310, 330, 78, '#fff7da', GOLD, 8, '6 4', 2.5)
    b += text(565, 336, '縁（ゆかり） Lv 20 から', 13, INK, weight='bold')
    b += text(565, 358, '他の職の木から 2 つまで、段 2 まで', 11, MUT) + text(565, 376, '要・3 行目は取れない', 11, MUT)
    b += text(380, 420, '職を変える: 語り部に形見を返す（結晶 5 ＋ 全振り直し）', 12, MUT)
    b += text(380, 442, '技 36 ＋ 天性 5 ＝ 41。五つの形見の台は 17 の序盤に置く', 12, MUT)
    return svg(760, 465, b)


def option_c():
    b = text(380, 30, '案 C 天職: 一章の最後に選び、三章で 2 つ目', 18, INK, weight='bold')
    stages = [(30, '序盤〜一章', '入口だけ試す', '5 つの木の 1 行目|（段 3 まで、刻印なし）|＋ 旅人の 1 行目'),
              (270, '一章の最後（Lv 12〜15）', '天職を選ぶ', '心臓が止まり巨神が眠る場面のあと。|天性が付き、選んだ木が全部開く。|他の木の技は段 2 まで残る'),
              (510, '三章（雷翼のあと）', '2 つ目の天職', '別の木を 3 行目まで。|要と天性はなし。|5 つ目の枠も開く')]
    for i, (x, t1, t2, body) in enumerate(stages):
        w = 220
        b += box(x, 60, w, 40, COL['旅人'] if i == 0 else '#0a8fa6', None, 8)
        b += text(x + w / 2, 85, t1, 13, '#ffffff', weight='bold')
        b += box(x, 108, w, 130, '#ffffff', LINE, 8)
        b += text(x + w / 2, 136, t2, 15, INK, weight='bold')
        for j, ln in enumerate(body.split('|')):
            b += text(x + w / 2, 164 + j * 20, ln, 11, MUT)
        if i < 2:
            b += '<polygon points="%s,164 %s,154 %s,174" fill="%s"/>' % (x + w + 18, x + w + 4, x + w + 4, LINE)
    b += text(380, 280, '木の開き方', 14, INK, weight='bold')
    ns = ['岩守り', '風読み', '潮歌い', '灰の鍛え人', '骨の射手']
    labels = ['序盤〜一章', '天職のあと', '2 つ目のあと']
    for i, n in enumerate(ns):
        b += text(180 + i * 118, 306, n, 11, MUT)
    for r, lab in enumerate(labels):
        y = 316 + r * 62
        b += text(110, y + 30, lab, 12, MUT, 'end')
        for i, n in enumerate(ns):
            x = 130 + i * 118
            if r == 0:
                h, op = 14, .35
            elif r == 1:
                h, op = (46, 1) if i == 0 else (14, .35)
            else:
                h, op = (46, 1) if i == 0 else ((34, .75) if i == 2 else (14, .35))
            b += box(x, y + (46 - h), 100, h, COL[n], None, 4, op=op)
    b += text(380, 520, '二つの状態（天職の前と後）が要る。技 36 ＋ 天性 5 ＝ 41。A の上に載せる作り', 12, MUT)
    b += text(380, 540, '濃い塗りは全部開いた木、中間は 3 行目まで、薄い塗りは 1 行目のみ（例: 岩守りが天職、潮歌いが 2 つ目）', 11, MUT)
    return svg(760, 560, b)


for name, fn in (('tree_anatomy', tree_anatomy), ('points_curve', points_curve), ('option_a_ring', option_a),
                 ('option_b_class', option_b), ('option_c_calling', option_c)):
    with open(os.path.join(OUT, name + '.svg'), 'w', encoding='utf-8') as f:
        f.write(fn())
print('ok')
