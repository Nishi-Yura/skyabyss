# -*- coding: utf-8 -*-
"""はじまりの都「鐘の宿り」(docs/design/33 の 2 章。約 200×200) の見取り図のラフ 3 案 (第 2 回)。

python -I tools/gen/world/gen_town_roughs.py  →  docs/review/town_roughs/town2_{a,b,c}.png と town2_sheet.png
1 ブロック = 3 px。北が上。門は章の跡地の方位 (北 岩鯨・東 潮殻・西 雷翼・南 余燼) を向く。
建物の種類は 33 の 2.2 (国家の都: 大鐘楼・空見の砦・空見の館・鯨追いの組合・四巨神の社・鍛冶場・市・住まい・墓地)。
"""
from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'docs', 'review', 'town_roughs')
S = 3
N = 200
PAD = 120

C = {
    'ground': (214, 206, 180), 'wall': (72, 66, 60), 'gate': (210, 170, 60), 'street': (168, 160, 146), 'plaza': (236, 228, 204),
    'bell': (240, 196, 80), 'keep': (110, 100, 120), 'hall': (150, 92, 58), 'guild': (60, 120, 150), 'temple': (160, 110, 170),
    'smith': (196, 120, 60), 'market': (210, 160, 110), 'house': (118, 92, 70), 'lot': (120, 170, 110), 'farm': (176, 184, 92),
    'grave': (140, 140, 130), 'water': (96, 150, 190), 'camp': (220, 110, 70), 'terrace': (200, 190, 160), 'green': (150, 176, 120),
}
LEGEND = [('bell', '大鐘楼（芯。語り部・歌の間）'), ('keep', '空見の砦（見張り塔・望遠鏡・記録の間）'), ('hall', '空見の館（議事・伝承の書庫・図鑑）'),
          ('guild', '鯨追いの組合（板・パーティ・順位の石）'), ('temple', '四巨神の社（転生の台座・鍵の間）'), ('smith', '鍛冶場（共鳴の炉・職人）'),
          ('market', '市（露店・井戸・村人）'), ('house', '住まい（空き家）'), ('lot', '空き区画（自由に建ててよい）'), ('farm', '畑と囲い'),
          ('grave', '鯨追いの墓地'), ('street', '通り'), ('plaza', '広場'), ('terrace', '段（高い所）'), ('wall', '壁'), ('gate', '門'),
          ('water', '水'), ('camp', 'イサナの焚き火（門の外）')]


def font(sz):
    for f in ('YuGothM.ttc', 'YuGothB.ttc', 'BIZ-UDGothicR.ttc', 'meiryo.ttc'):
        p = os.path.join('C:/Windows/Fonts', f)
        if os.path.exists(p):
            return ImageFont.truetype(p, sz)
    return ImageFont.load_default()


class Town:
    def __init__(self, title, sub):
        self.im = Image.new('RGB', (N * S + PAD * 2 + 120, N * S + PAD * 2 + 200), (248, 246, 240))
        self.d = ImageDraw.Draw(self.im)
        self.title = title
        self.sub = sub
        self.rect(0, 0, N, N, 'ground')

    def px(self, x, z):
        return PAD + x * S, PAD + z * S

    def rect(self, x, z, w, h, c, outline=None):
        x0, y0 = self.px(x, z)
        self.d.rectangle([x0, y0, x0 + w * S - 1, y0 + h * S - 1], fill=C[c], outline=outline)

    def ring(self, x, z, w, h, t, c):
        self.rect(x, z, w, t, c)
        self.rect(x, z + h - t, w, t, c)
        self.rect(x, z, t, h, c)
        self.rect(x + w - t, z, t, h, c)

    def circle(self, x, z, r, c):
        x0, y0 = self.px(x, z)
        self.d.ellipse([x0 - r * S, y0 - r * S, x0 + r * S, y0 + r * S], fill=C[c], outline=(60, 60, 60))

    def tower(self, x, z, r, c='bell'):
        self.circle(x, z, r, c)
        self.circle(x, z, max(1, r // 2), 'plaza')

    def keep(self, x, z, w, h):
        self.rect(x, z, w, h, 'keep', outline=(40, 40, 50))
        for cx, cz in ((x, z), (x + w - 6, z), (x, z + h - 6), (x + w - 6, z + h - 6)):
            self.rect(cx, cz, 6, 6, 'wall')
        self.rect(x + w // 2 - 4, z + h // 2 - 4, 8, 8, 'terrace')

    def temple(self, x, z, r):
        x0, y0 = self.px(x, z)
        pts = [(x0 + r * S * math.cos(math.radians(a)), y0 + r * S * math.sin(math.radians(a))) for a in range(30, 390, 60)]
        self.d.polygon(pts, fill=C['temple'], outline=(60, 60, 60))

    def hall(self, x, z, w, h, c='hall'):
        self.rect(x, z, w, h, c, outline=(50, 40, 30))

    def houses(self, x, z, w, h, cols, rows, hw=7, hh=7, c='house'):
        gx = (w - cols * hw) / max(1, cols - 1) if cols > 1 else 0
        gz = (h - rows * hh) / max(1, rows - 1) if rows > 1 else 0
        for i in range(cols):
            for j in range(rows):
                self.rect(int(x + i * (hw + gx)), int(z + j * (hh + gz)), hw, hh, c)

    def stalls(self, x, z, w, h):
        for i in range(0, w, 6):
            for j in range(0, h, 10):
                self.rect(x + i, z + j, 4, 4, 'market')

    def graves(self, x, z, w, h):
        self.rect(x, z, w, h, 'green')
        for i in range(2, w - 2, 5):
            for j in range(2, h - 2, 6):
                self.rect(x + i, z + j, 2, 3, 'grave')

    def gates(self):
        for x, z, w, h in ((96, 0, 8, 3), (96, N - 3, 8, 3), (0, 96, 3, 8), (N - 3, 96, 3, 8)):
            self.rect(x, z, w, h, 'gate')

    def label(self, x, z, text, sz=12, col=(30, 30, 30)):
        x0, y0 = self.px(x, z)
        self.d.text((x0, y0), text, fill=col, font=font(sz))

    def finish(self, path):
        d = self.d
        d.text((PAD, 14), self.title, fill=(20, 20, 20), font=font(26))
        d.text((PAD, 46), self.sub, fill=(80, 80, 80), font=font(13))
        cx, cy = PAD + N * S // 2, PAD + N * S // 2
        wall = N * S
        d.text((cx - 50, PAD - 26), '北門「鯨門」→ 岩鯨の丘', fill=(60, 60, 60), font=font(13))
        d.text((cx - 50, PAD + wall + 8), '南門「灰門」→ 余燼の灰の地', fill=(60, 60, 60), font=font(13))
        d.text((4, cy - 8), '西門「風門」', fill=(60, 60, 60), font=font(12))
        d.text((4, cy + 8), '→ 雷翼の峰', fill=(60, 60, 60), font=font(12))
        d.text((PAD + wall + 8, cy - 8), '東門「潮門」', fill=(60, 60, 60), font=font(12))
        d.text((PAD + wall + 8, cy + 8), '→ 潮殻の海', fill=(60, 60, 60), font=font(12))
        y = PAD + N * S + 48
        x = PAD
        for i, (k, t) in enumerate(LEGEND):
            if i in (3, 6, 10, 14):
                y += 24
                x = PAD
            d.rectangle([x, y, x + 14, y + 14], fill=C[k], outline=(60, 60, 60))
            d.text((x + 20, y - 2), t, fill=(40, 40, 40), font=font(12))
            x += 22 + len(t) * 13 + 18
        d.text((PAD, PAD + N * S + 176), '1 マス = 1 ブロック（3 px）。壁の中は約 200×200。壁の外は普通の地形。村人（バニラ）は市と住まいに少し。', fill=(90, 90, 90), font=font(12))
        self.im.save(path)


def town_a():
    t = Town('案 A「鐘の都」', '北の段に大鐘楼と空見の砦が並び、都を見下ろす。段の下に広場と市。東に組合、西に社、南に鍛冶と住まい。墓地は西門の外。')
    t.ring(0, 0, N, N, 3, 'wall')
    t.gates()
    t.rect(40, 3, 120, 56, 'terrace')
    t.keep(108, 8, 44, 40)
    t.label(108, 50, '空見の砦（見張り塔・望遠鏡）', 11)
    t.tower(76, 28, 11, 'bell')
    t.label(50, 42, '大鐘楼・語り部の家', 11)
    t.hall(46, 8, 24, 16, 'hall')
    t.label(44, 2, '空見の館', 11)
    t.rect(96, 3, 8, N - 6, 'street')
    t.rect(3, 96, N - 6, 8, 'street')
    t.rect(40, 59, 120, 5, 'street')
    t.rect(64, 64, 72, 40, 'plaza')
    t.stalls(68, 68, 28, 30)
    t.stalls(106, 68, 28, 30)
    t.rect(98, 82, 4, 4, 'water')
    t.label(66, 106, '広場と市（井戸・露店・村人）', 11)
    t.hall(140, 68, 44, 22, 'guild')
    t.label(140, 60, '鯨追いの組合（板・順位の石）', 11)
    t.rect(140, 104, 32, 32, 'lot')
    t.rect(140, 150, 44, 40, 'farm')
    t.label(140, 192, '畑と囲い', 10)
    t.temple(36, 84, 16)
    t.label(10, 104, '四巨神の社（転生の台座）', 11)
    t.rect(8, 112, 32, 32, 'lot')
    t.rect(8, 150, 32, 32, 'lot')
    t.hall(106, 112, 28, 18, 'smith')
    t.label(106, 132, '鍛冶場（炉・職人）', 11)
    t.houses(50, 112, 40, 80, 3, 5, 8, 8, 'house')
    t.label(50, 194, '住まい（空き家）', 10)
    t.rect(106, 140, 28, 50, 'lot')
    t.label(106, 192, '空き区画', 10)
    t.graves(-34, 70, 28, 40)
    t.label(-36, 112, '鯨追いの墓地（門の外）', 10)
    t.rect(116, N + 10, 4, 4, 'camp')
    t.finish(os.path.join(OUT, 'town2_a.png'))


def town_b():
    t = Town('案 B「砦の都」', '北東の丘の砦（国の座。壁で二重に囲う）から、坂の大通りが南西の市へ下る。鐘楼は砦の門の前。社と墓地は西、組合は東門のそば。')
    t.ring(0, 0, N, N, 3, 'wall')
    t.gates()
    t.rect(112, 3, 85, 70, 'terrace')
    t.ring(118, 6, 76, 62, 4, 'wall')
    t.keep(140, 14, 44, 40)
    t.label(118, 58, '空見の砦（国の座・記録の間・見張り塔）', 11)
    t.hall(124, 14, 12, 30, 'hall')
    t.tower(108, 76, 10, 'bell')
    t.label(84, 88, '大鐘楼（砦の門の前）', 11)
    for i in range(0, 100, 4):
        t.rect(104 - i, 80 + i, 10, 6, 'street')
    t.rect(96, 3, 8, N - 6, 'street')
    t.rect(3, 96, N - 6, 8, 'street')
    t.rect(16, 150, 64, 44, 'plaza')
    t.stalls(20, 154, 28, 36)
    t.stalls(52, 154, 24, 36)
    t.rect(46, 170, 4, 4, 'water')
    t.label(16, 140, '市（露店・井戸・村人）', 11)
    t.hall(150, 80, 44, 22, 'guild')
    t.label(150, 104, '鯨追いの組合', 11)
    t.hall(120, 108, 30, 18, 'smith')
    t.label(120, 128, '鍛冶場', 11)
    t.temple(36, 60, 16)
    t.label(12, 80, '四巨神の社', 11)
    t.graves(10, 10, 40, 30)
    t.label(10, 42, '鯨追いの墓地', 10)
    t.houses(60, 20, 36, 50, 3, 4, 8, 8, 'house')
    t.houses(120, 140, 40, 50, 3, 4, 8, 8, 'house')
    t.label(120, 192, '住まい', 10)
    t.rect(8, 104, 32, 32, 'lot')
    t.rect(160, 140, 32, 32, 'lot')
    t.rect(64, 110, 28, 28, 'lot')
    t.rect(96, 150, 20, 40, 'farm')
    t.label(8, 136, '空き区画', 10)
    t.rect(116, N + 10, 4, 4, 'camp')
    t.finish(os.path.join(OUT, 'town2_b.png'))


def town_c():
    t = Town('案 C「二つの丘」', '東の丘に砦と空見の館（国の務め）、西の丘に大鐘楼と社（祈り）。谷の大通りに市・組合・鍛冶。住まいは坂に段々。墓地は社の裏。')
    t.ring(0, 0, N, N, 3, 'wall')
    t.gates()
    t.circle(150, 60, 40, 'terrace')
    t.circle(50, 60, 40, 'terrace')
    t.keep(132, 30, 40, 36)
    t.label(118, 70, '空見の砦（見張り塔）', 11)
    t.hall(126, 72, 44, 16, 'hall')
    t.label(126, 90, '空見の館（議事・書庫）', 11)
    t.tower(50, 44, 12, 'bell')
    t.label(26, 60, '大鐘楼・語り部の家', 11)
    t.temple(50, 82, 14)
    t.label(30, 98, '四巨神の社', 11)
    t.graves(14, 10, 30, 22)
    t.label(14, 2, '鯨追いの墓地', 10)
    t.rect(3, 108, N - 6, 10, 'street')
    t.rect(96, 3, 8, N - 6, 'street')
    t.rect(3, 96, N - 6, 8, 'street')
    t.rect(60, 120, 80, 30, 'plaza')
    t.stalls(64, 124, 30, 22)
    t.stalls(104, 124, 30, 22)
    t.rect(98, 134, 4, 4, 'water')
    t.label(60, 152, '市（井戸・露店・村人）', 11)
    t.hall(146, 122, 44, 22, 'guild')
    t.label(146, 146, '鯨追いの組合', 11)
    t.hall(14, 122, 40, 18, 'smith')
    t.label(14, 142, '鍛冶場', 11)
    t.houses(14, 158, 60, 34, 4, 3, 8, 8, 'house')
    t.houses(126, 158, 64, 34, 4, 3, 8, 8, 'house')
    t.label(14, 194, '住まい（段々）', 10)
    t.rect(80, 158, 36, 34, 'lot')
    t.rect(100, 10, 24, 30, 'lot')
    t.rect(76, 10, 20, 30, 'farm')
    t.label(80, 194, '空き区画', 10)
    t.rect(116, N + 10, 4, 4, 'camp')
    t.finish(os.path.join(OUT, 'town2_c.png'))


def sheet():
    ims = [Image.open(os.path.join(OUT, f)) for f in ('town2_a.png', 'town2_b.png', 'town2_c.png')]
    w = sum(i.width for i in ims) + 40
    h = max(i.height for i in ims) + 20
    s = Image.new('RGB', (w, h), (248, 246, 240))
    x = 10
    for i in ims:
        s.paste(i, (x, 10))
        x += i.width + 10
    s.save(os.path.join(OUT, 'town2_sheet.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    town_a()
    town_b()
    town_c()
    sheet()
    print('wrote', OUT)
