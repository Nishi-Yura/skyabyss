# -*- coding: utf-8 -*-
"""はじまりの町 (docs/design/33 の 2 章。約 200×200) の見取り図のラフ 3 案を描く。

python -I tools/gen_town_roughs.py  →  docs/review/town_roughs/town_{a,b,c}.png と town_sheet.png
1 ブロック = 3 px。北が上。門は章の跡地の方位 (北 岩鯨・東 潮殻・西 雷翼・南 余燼) を向く。
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs', 'review', 'town_roughs')
S = 3  # px / block
N = 200
PAD = 120

C = {
    'ground': (214, 206, 180), 'wall': (72, 66, 60), 'gate': (210, 170, 60), 'street': (168, 160, 146), 'plaza': (236, 228, 204),
    'core': (150, 92, 58), 'house': (118, 92, 70), 'shop': (196, 120, 60), 'lot': (120, 170, 110), 'farm': (176, 184, 92),
    'water': (96, 150, 190), 'bell': (240, 196, 80), 'green': (138, 166, 110), 'camp': (220, 110, 70), 'hill': (170, 150, 120),
}
LEGEND = [('bell', '鐘楼と語り部の家（芯）'), ('core', '広場の建物（板・井戸・歌の間の口）'), ('shop', '工房の通り（炉・職人）'),
          ('house', '空き家（中は空。拠点にしてよい）'), ('lot', '空き区画（自由に建ててよい／将来の施設）'), ('farm', '畑と囲い'),
          ('street', '通り'), ('plaza', '広場'), ('wall', '壁'), ('gate', '門（方位の跡地へ）'), ('water', '水'), ('camp', 'イサナの焚き火（門の外）')]


def font(sz):
    for f in ('YuGothM.ttc', 'YuGothB.ttc', 'BIZ-UDGothicR.ttc', 'meiryo.ttc'):
        p = os.path.join('C:/Windows/Fonts', f)
        if os.path.exists(p):
            return ImageFont.truetype(p, sz)
    return ImageFont.load_default()


class Town:
    def __init__(self, title, sub):
        self.im = Image.new('RGB', (N * S + PAD * 2 + 120, N * S + PAD * 2 + 150), (248, 246, 240))
        self.d = ImageDraw.Draw(self.im)
        self.title = title
        self.sub = sub
        self.rect(0, 0, N, N, 'ground')

    def rect(self, x, z, w, h, c, outline=None):
        x0, y0 = PAD + x * S, PAD + z * S
        self.d.rectangle([x0, y0, x0 + w * S - 1, y0 + h * S - 1], fill=C[c], outline=outline)

    def ring(self, x, z, w, h, t, c):
        self.rect(x, z, w, t, c)
        self.rect(x, z + h - t, w, t, c)
        self.rect(x, z, t, h, c)
        self.rect(x + w - t, z, t, h, c)

    def label(self, x, z, text, sz=13, col=(30, 30, 30)):
        self.d.text((PAD + x * S, PAD + z * S), text, fill=col, font=font(sz))

    def houses(self, x, z, w, h, cols, rows, hw=7, hh=7, c='house'):
        gx = (w - cols * hw) / max(1, cols - 1) if cols > 1 else 0
        gz = (h - rows * hh) / max(1, rows - 1) if rows > 1 else 0
        for i in range(cols):
            for j in range(rows):
                self.rect(int(x + i * (hw + gx)), int(z + j * (hh + gz)), hw, hh, c)

    def finish(self, path):
        d = self.d
        d.text((PAD, 14), self.title, fill=(20, 20, 20), font=font(26))
        d.text((PAD, 46), self.sub, fill=(80, 80, 80), font=font(13))
        # 方位
        cx, cy = PAD + N * S // 2, PAD + N * S // 2
        wall = N * S
        d.text((cx - 40, PAD - 26), '北 岩鯨の丘', fill=(60, 60, 60), font=font(13))
        d.text((cx - 40, PAD + wall + 8), '南 余燼の灰', fill=(60, 60, 60), font=font(13))
        d.text((6, cy - 8), '西 雷翼の峰', fill=(60, 60, 60), font=font(13))
        d.text((PAD + wall + 8, cy - 8), '東 潮殻の海', fill=(60, 60, 60), font=font(13))
        # 凡例
        y = PAD + N * S + 48
        x = PAD
        for i, (k, t) in enumerate(LEGEND):
            if i in (4, 8):
                y += 24
                x = PAD
            d.rectangle([x, y, x + 14, y + 14], fill=C[k], outline=(60, 60, 60))
            d.text((x + 20, y - 2), t, fill=(40, 40, 40), font=font(12))
            x += 22 + len(t) * 13 + 18
        d.text((PAD, PAD + N * S + 126), '1 マス = 1 ブロック（3 px）。壁の中は約 200×200。壁の外は普通の地形。', fill=(90, 90, 90), font=font(12))
        self.im.save(path)


def town_a():
    t = Town('案 A「鐘の広場」', '中心の広場から四つの門へ十字の大通り。東が工房、西が住まい、四隅が空き区画。囲いは四角。')
    t.ring(0, 0, N, N, 3, 'wall')
    for x, z, w, h in ((96, 0, 8, 3), (96, N - 3, 8, 3), (0, 96, 3, 8), (N - 3, 96, 3, 8)):
        t.rect(x, z, w, h, 'gate')
    t.rect(96, 3, 8, N - 6, 'street')
    t.rect(3, 96, N - 6, 8, 'street')
    t.ring(30, 30, 140, 140, 5, 'street')
    t.rect(76, 76, 48, 48, 'plaza')
    t.rect(92, 84, 16, 16, 'bell')
    t.rect(80, 110, 10, 8, 'core')
    t.rect(110, 110, 10, 8, 'core')
    t.rect(80, 80, 8, 8, 'core')
    t.label(76, 66, '広場・鐘楼・語り部の家', 12)
    # 東: 工房の通り
    t.houses(130, 40, 36, 50, 2, 3, 12, 10, 'shop')
    t.label(128, 30, '工房の通り（炉・職人）', 12)
    # 西: 住まい
    t.houses(38, 40, 32, 50, 2, 3, 9, 9, 'house')
    t.houses(38, 112, 32, 50, 2, 3, 9, 9, 'house')
    t.label(38, 30, '住まい（空き家）', 12)
    # 南東: 畑
    t.rect(130, 112, 36, 50, 'farm')
    t.label(130, 166, '畑と囲い', 12)
    # 四隅: 空き区画
    for x, z in ((6, 6), (N - 38, 6), (6, N - 38), (N - 38, N - 38)):
        t.rect(x, z, 32, 32, 'lot')
    t.label(8, 8, '空き区画 32×32', 11)
    t.rect(120, 20, 6, 6, 'water')
    t.rect(118, 128, 14, 8, 'water')
    # 外: 焚き火
    t.rect(116, N + 10, 4, 4, 'camp')
    t.finish(os.path.join(OUT, 'town_a.png'))


def town_b():
    t = Town('案 B「川沿いの段々」', '川が町を南北に流れ、橋でつなぐ。鐘楼は北の高い段、工房は川の東、住まいは西の段々。空き区画は壁ぎわ。')
    t.ring(0, 0, N, N, 3, 'wall')
    for x, z, w, h in ((96, 0, 8, 3), (96, N - 3, 8, 3), (0, 96, 3, 8), (N - 3, 96, 3, 8)):
        t.rect(x, z, w, h, 'gate')
    t.rect(88, 3, 12, N - 6, 'water')
    t.rect(70, 96, 60, 8, 'street')
    t.rect(70, 40, 60, 6, 'street')
    t.rect(70, 150, 60, 6, 'street')
    t.rect(60, 3, 6, N - 6, 'street')
    t.rect(130, 3, 6, N - 6, 'street')
    t.rect(3, 96, 57, 8, 'street')
    t.rect(136, 96, 61, 8, 'street')
    # 北の段: 鐘楼と広場
    t.rect(100, 10, 60, 30, 'plaza')
    t.rect(118, 14, 16, 16, 'bell')
    t.rect(104, 30, 10, 8, 'core')
    t.rect(140, 30, 10, 8, 'core')
    t.label(100, 2, '北の段: 広場・鐘楼', 12)
    # 東: 工房の通り (川沿い)
    t.houses(104, 50, 24, 40, 2, 3, 10, 10, 'shop')
    t.label(138, 50, '工房の通り（川の東）', 12)
    # 東の畑
    t.rect(140, 110, 52, 40, 'farm')
    # 西の段々: 住まい
    for z in (10, 50, 110, 150):
        t.houses(12, z, 44, 34, 3, 2, 10, 10, 'house')
    t.label(10, 2, '西の段々: 住まい', 12)
    # 空き区画: 壁ぎわの南と東
    for x, z in ((104, 160), (140, 160), (160, 10), (160, 50)):
        t.rect(x, z, 32, 32, 'lot')
    t.label(104, 193, '空き区画 ×4', 10)
    t.rect(88, 100, 12, 4, 'street')
    t.rect(88, 42, 12, 4, 'street')
    t.rect(88, 152, 12, 4, 'street')
    t.rect(116, N + 10, 4, 4, 'camp')
    t.finish(os.path.join(OUT, 'town_b.png'))


def town_c():
    t = Town('案 C「二重の輪」', '古い芯（鐘楼・語り部の家・歌の間）を内の輪で囲い、外の輪に新しい町。内と外のあいだが市（工房と板）。空き区画は外の輪の四つ。')
    t.ring(0, 0, N, N, 3, 'wall')
    for x, z, w, h in ((96, 0, 8, 3), (96, N - 3, 8, 3), (0, 96, 3, 8), (N - 3, 96, 3, 8)):
        t.rect(x, z, w, h, 'gate')
    # 内の輪
    t.ring(64, 64, 72, 72, 3, 'wall')
    for x, z, w, h in ((96, 64, 8, 3), (96, 133, 8, 3), (64, 96, 3, 8), (133, 96, 3, 8)):
        t.rect(x, z, w, h, 'gate')
    t.rect(70, 70, 60, 60, 'plaza')
    t.rect(92, 84, 16, 16, 'bell')
    t.rect(74, 74, 10, 8, 'core')
    t.rect(116, 74, 10, 8, 'core')
    t.rect(74, 116, 10, 8, 'core')
    t.label(70, 58, '内の輪: 古い芯（鐘楼・語り部の家・歌の間）', 11)
    # 大通り
    t.rect(96, 3, 8, 61, 'street')
    t.rect(96, 136, 8, 61, 'street')
    t.rect(3, 96, 61, 8, 'street')
    t.rect(136, 96, 61, 8, 'street')
    t.ring(44, 44, 112, 112, 6, 'street')
    # 市 (内と外のあいだ)
    t.houses(52, 52, 40, 10, 3, 1, 10, 8, 'shop')
    t.houses(108, 52, 40, 10, 3, 1, 10, 8, 'shop')
    t.houses(52, 138, 40, 10, 3, 1, 10, 8, 'core')
    t.houses(108, 138, 40, 10, 3, 1, 10, 8, 'shop')
    t.label(52, 36, '市: 工房・炉・板', 12)
    # 外の輪: 住まい (北西・南東)、畑 (南西)、空き区画 (四つ)
    t.houses(8, 8, 32, 30, 3, 3, 8, 8, 'house')
    t.houses(160, 8, 32, 30, 3, 3, 8, 8, 'house')
    t.houses(8, 160, 32, 30, 3, 3, 8, 8, 'house')
    t.rect(160, 160, 32, 32, 'farm')
    t.label(8, 40, '住まい', 11)
    for x, z in ((8, 60), (160, 60), (8, 112), (160, 112)):
        t.rect(x, z, 32, 32, 'lot')
    t.label(8, 94, '空き区画 32×32', 11)
    t.rect(120, 120, 8, 8, 'water')
    t.rect(116, N + 10, 4, 4, 'camp')
    t.finish(os.path.join(OUT, 'town_c.png'))


def sheet():
    ims = [Image.open(os.path.join(OUT, f)) for f in ('town_a.png', 'town_b.png', 'town_c.png')]
    w = sum(i.width for i in ims) + 40
    h = max(i.height for i in ims) + 20
    s = Image.new('RGB', (w, h), (248, 246, 240))
    x = 10
    for i in ims:
        s.paste(i, (x, 10))
        x += i.width + 10
    s.save(os.path.join(OUT, 'town_sheet.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    town_a()
    town_b()
    town_c()
    sheet()
    print('wrote', OUT)
