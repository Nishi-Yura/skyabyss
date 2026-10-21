# -*- coding: utf-8 -*-
"""都の施設の置き方のラフ 3 案 (docs/design/35。上から見た図)。

python -I tools/capital/layout_roughs.py → docs/review/town_roughs/fac_<a|b|c>.png
座標は設計図と同じ (中心 0,0、+z が南、壁の内 -97..97)。北の段 (z -97..-42) と大通り (十字、幅 8)、広場 (中心 0,-16、半径 19) は今のまま。
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'docs', 'review', 'town_roughs')
S = 4
FONT = 'C:/Windows/Fonts/meiryo.ttc'

# 色 (種類): 施設 = 金、章で開く = 青緑、裏通り = 紫、国の建物 = 灰、入れない家 = 濃い灰、庭 = 緑
COL = {'fac': (226, 182, 82), 'open': (95, 199, 192), 'back': (168, 120, 210), 'state': (150, 150, 158), 'house': (70, 72, 78),
       'green': (86, 128, 72), 'lot': (120, 150, 100)}

FIXED = [  # 今のまま残す国の建物 (北の段)
    ('大鐘楼', 'state', -34, -80, -26, -72), ('語り部の家', 'state', -22, -82, -12, -72), ('空見の砦', 'open', 10, -92, 54, -52),
    ('空見の館', 'open', -58, -96, -40, -84),
]


def plan_a():
    """A 四つの通りの役割: 東 = 交易、西 = 祈りと旅、南 = 職と集い"""
    return FIXED + [
        ('道具屋', 'fac', 8, -30, 22, -18), ('薬師の店', 'fac', 8, -14, 22, -6), ('預かり所', 'fac', 26, -14, 40, -6),
        ('鯨追いの組合', 'fac', 44, -34, 84, -10),
        ('四巨神の社', 'open', -78, -36, -50, -8), ('子の止まり木', 'fac', -90, 6, -70, 26), ('宿 鐘の宿', 'fac', -40, 6, -10, 26),
        ('鍛冶場', 'fac', 8, 8, 34, 28), ('訓練場', 'open', 8, 36, 34, 56),
        ('闇市', 'back', 52, 40, 64, 50), ('古井戸', 'back', -60, 50, -52, 58), ('地図屋', 'back', 60, 66, 74, 78),
        ('酒場の地下', 'back', -36, 30, -24, 36), ('墓守の小屋', 'back', -128, -30, -118, -22),
    ]


def plan_b():
    """B 広場を囲む: よく使う施設を全部、広場から 30 以内の輪に (ラグニの泉の広場のように)"""
    return FIXED + [
        ('鯨追いの組合', 'fac', 8, -40, 32, -26), ('道具屋', 'fac', 22, -22, 36, -12), ('薬師の店', 'fac', 22, -8, 36, 2),
        ('預かり所', 'fac', 8, 6, 22, 16), ('宿 鐘の宿', 'fac', -24, 6, -8, 20), ('鍛冶場', 'fac', -38, -8, -22, 6),
        ('子の止まり木', 'fac', -38, -26, -22, -12), ('四巨神の社', 'open', -36, -42, -10, -28),
        ('訓練場', 'open', 40, -50, 56, -40),
        ('闇市', 'back', 52, 40, 64, 50), ('古井戸', 'back', -60, 50, -52, 58), ('地図屋', 'back', 60, 66, 74, 78),
        ('酒場の地下', 'back', -22, 22, -12, 28), ('墓守の小屋', 'back', -128, -30, -118, -22),
    ]


def plan_c():
    """C 十字の通りと門: 店は広場の近く、旅の施設は門の近く (止まり木は東門、宿は南門、社は西門、組合は東の通り)"""
    return FIXED + [
        ('道具屋', 'fac', 8, 8, 22, 20), ('薬師の店', 'fac', -22, 8, -8, 20), ('預かり所', 'fac', 8, -36, 22, -24),
        ('鍛冶場', 'fac', -36, 8, -26, 30), ('鯨追いの組合', 'fac', 30, -34, 70, -10),
        ('子の止まり木', 'fac', 74, 6, 92, 24), ('宿 鐘の宿', 'fac', -10, 70, 10, 90), ('四巨神の社', 'open', -92, -24, -64, 4),
        ('訓練場', 'open', 30, 30, 50, 50),
        ('闇市', 'back', 56, 50, 68, 60), ('古井戸', 'back', -60, 50, -52, 58), ('地図屋', 'back', -74, 66, -60, 78),
        ('酒場の地下', 'back', -6, 92, 6, 96), ('墓守の小屋', 'back', -128, -30, -118, -22),
    ]


def draw(name, items, title, note):
    W = 290 * S
    H = 260 * S
    im = Image.new('RGB', (W, H + 90), (30, 32, 36))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(FONT, 13)
    fb = ImageFont.truetype(FONT, 22)
    fs = ImageFont.truetype(FONT, 15)

    def X(x):
        return (x + 145) * S

    def Z(z):
        return (z + 105) * S + 90

    # 壁と地面
    d.rectangle([X(-100), Z(-100), X(100), Z(100)], fill=(58, 60, 64), outline=(200, 190, 160), width=6)
    # 北の段
    d.rectangle([X(-60), Z(-97), X(60), Z(-42)], fill=(72, 76, 80), outline=(120, 120, 120), width=2)
    # 大通り
    d.rectangle([X(-4), Z(-97), X(3), Z(97)], fill=(150, 146, 138))
    d.rectangle([X(-97), Z(-4), X(97), Z(3)], fill=(150, 146, 138))
    # 広場
    d.ellipse([X(-19), Z(-35), X(19), Z(3)], fill=(170, 166, 156), outline=(220, 210, 180), width=3)
    d.text((X(-8), Z(-20)), '広場・市', fill=(30, 30, 30), font=fs)
    # 入れない家 (2 列目より奥)。施設の所を空けて、残りを薄く
    for (x0, z0, x1, z1) in ((-94, 30, -54, 94), (54, 60, 94, 94), (-94, -94, -64, -45), (64, -94, 94, -45), (-50, 60, -10, 94), (10, 60, 50, 94)):
        d.rectangle([X(x0), Z(z0), X(x1), Z(z1)], fill=COL['house'])
    for (x0, z0, x1, z1) in ((-80, -80, -64, -64), (64, -80, 80, -64), (-80, 76, -64, 92), (19, 75, 33, 89), (64, 76, 80, 92)):
        d.rectangle([X(x0), Z(z0), X(x1), Z(z1)], fill=COL['lot'])
    # 墓地
    d.rectangle([X(-136), Z(-22), X(-108), Z(18)], fill=(70, 86, 62), outline=(120, 120, 110))
    d.text((X(-134), Z(-4)), '墓地', fill=(220, 220, 210), font=f)
    for (label, kind, x0, z0, x1, z1) in items:
        d.rectangle([X(x0), Z(z0), X(x1), Z(z1)], fill=COL[kind], outline=(20, 20, 20), width=2)
        tw = d.textlength(label, font=f)
        cx = (X(x0) + X(x1)) / 2 - tw / 2
        cz = (Z(z0) + Z(z1)) / 2 - 8
        d.text((cx, cz), label, fill=(20, 20, 20) if kind != 'house' else (230, 230, 230), font=f)
    # 門
    for (gx, gz, lab) in ((0, -100, '鯨門'), (0, 100, '灰門'), (-100, 0, '風門'), (100, 0, '潮門')):
        d.rectangle([X(gx - 5), Z(gz - 3), X(gx + 5), Z(gz + 3)], fill=(220, 210, 180))
        d.text((X(gx) - 14, Z(gz) - 30 if gz < 0 else Z(gz) + 8), lab, fill=(240, 230, 210), font=f)
    d.text((16, 12), title, fill=(240, 236, 220), font=fb)
    d.text((16, 50), note, fill=(200, 196, 186), font=fs)
    # 凡例
    lx = W - 520
    for i, (k, lab) in enumerate((('fac', 'よく使う施設'), ('open', '章で開く'), ('back', '裏通り'), ('house', '入れない家'), ('lot', '空き区画'))):
        d.rectangle([lx + i * 100, H + 60, lx + i * 100 + 14, H + 74], fill=COL[k])
        d.text((lx + i * 100 + 18, H + 58), lab, fill=(220, 220, 210), font=f)
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, 'fac_%s.png' % name))


def main():
    draw('a', plan_a(), 'A 四つの通りの役割', '東の通り = 交易（道具屋・薬師・預かり所・組合）、西 = 祈りと旅（社・止まり木・宿）、南 = 職（鍛冶場・訓練場）')
    draw('b', plan_b(), 'B 広場を囲む', 'よく使う施設を全部、広場から 30 以内の輪に。迷わない・歩かない（ラグニの泉の広場の形）')
    draw('c', plan_c(), 'C 十字の通りと門', '店は広場の近く、旅の施設は門の近く（止まり木は東の潮門、宿は南の灰門のすぐ内、社は西の風門）')
    print('ok')


if __name__ == '__main__':
    main()
