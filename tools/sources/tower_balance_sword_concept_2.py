"""大剣の設定画 2 回目（refs_1118 の healong・bronze の作りに倣う）:
刃はまっすぐな厚い一枚板（幅 14）。形でなく、面の模様（内側の縁取りの線・真ん中の筋・山形の連なり・菱）で豪華にする。
色は一つの系統の段（白の側は大理石＋金、黒の側は黒い石＋紫）。鍔は太い塊の天秤（梁＋下がる金の皿）"""
import sys
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, r'C:/Users/nisimoto/Desktop/mod/tools/models/x32/look5')
import tower_bold  # noqa
import lib

R = lib.RAMPS
W, H = 48, 100
CX = 24          # 刃は x 17..30（幅 14）
X0, X1 = 17, 31
BY0, BTIP0, BTOP = 30, 84, 92   # 刃の根元・切っ先の始まり・先
BORDER = 58     # 白と黒の境


def c(mat, t):
    r = R[mat]
    t = max(0, min(len(r) - 1, int(round(t))))
    h = r[t]
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def draw():
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    p = im.load()

    def put(x, y, col):
        if 0 <= x < W and 0 <= y < H:
            p[x, H - 1 - y] = col

    def get(x, y):
        return p[x, H - 1 - y][3] > 0

    # ---- 刃の形（まっすぐな板、先は 45 度の切っ先）
    blade = set()
    for y in range(BY0, BTOP + 1):
        if y < BTIP0:
            xs = range(X0, X1)
        else:
            k = y - BTIP0
            xs = range(X0 + k, X1 - k)
        for x in xs:
            blade.add((x, y))

    def edge_dist(x, y):
        d = 0
        while all((x + a, y + b) in blade for a in (-d - 1, 0, d + 1) for b in (-d - 1, 0, d + 1)):
            d += 1
            if d > 7:
                break
        return d

    for (x, y) in blade:
        top = y >= BORDER
        d = edge_dist(x, y)
        left = x < CX
        if top:
            base = 7.6 if left else 6.2
            if d == 0:
                col = c('tw_marble', 9 if left else 8)          # 外の縁: 明るい刃先
            elif d == 1:
                col = c('tw_marble', 4)                          # 縁の内の影
            elif d == 2:
                col = c('tw_gold', 5 if left else 4)             # 内側の縁取りの金の線
            else:
                col = c('tw_marble', base)
        else:
            base = 4.4 if left else 3.2
            if d == 0:
                col = c('tw_crack', 4 if left else 3)            # 外の縁: 紫の刃先
            elif d == 1:
                col = c('tw_black', 1)
            elif d == 2:
                col = c('tw_crack', 2 if left else 1)            # 内側の縁取りの紫の線
            else:
                col = c('tw_black', base)
        put(x, y, col)
    # 真ん中の筋（2 画素）: 白の側は金、黒の側は紫の光
    for y in range(BY0 + 4, BTIP0 + 3):
        for x in (CX - 1, CX):
            if edge_dist(x, y) >= 3:
                if y >= BORDER:
                    put(x, y, c('tw_gold', 6 if x < CX else 4))
                else:
                    put(x, y, c('tw_crack', 3 if x < CX else 2))
    # 山形の連なり（上を向く）: 白の側は金、黒の側は紫。筋の両脇
    def chevron(cy, mat, hi, lo):
        for k in range(4):
            for sx in (-1, 1):
                x = (CX - 2 - k) if sx < 0 else (CX + 1 + k)
                put(x, cy - k, c(mat, hi if sx < 0 else lo))
    for cy in (66, 72, 78):
        chevron(cy, 'tw_gold', 5, 3)
    for cy in (36, 42, 48):
        chevron(cy, 'tw_crack', 2, 1)
    # ---- 境: 天秤の印の台（金の菱、刃から張り出す。中に光）
    for b in range(-7, 8):
        for a in range(-8, 8):
            dd = abs(a + 0.5) + abs(b)
            if dd <= 7.6:
                x, y = CX + a, BORDER + b
                if dd >= 6.1:
                    put(x, y, c('tw_gold', 6 if (a < 0 and b >= 0) else (2 if (a >= 0 and b < 0) else 4)))
                elif dd >= 4.6:
                    put(x, y, c('tw_gold', 2))
                elif dd >= 3.6:
                    put(x, y, c('tw_black', 3))
                else:
                    put(x, y, c('tw_libra', 3 if dd < 1.4 else 2 if dd < 2.6 else 1))
    # ---- 鍔: 太い梁（黒い石、上の縁に紫、下の縁に暗い線）
    gy0, gy1 = 23, 30
    half = 17
    for y in range(gy0, gy1):
        for x in range(CX - half, CX + half):
            left = x < CX
            t = 4.4 if left else 3.2
            if y == gy1 - 1:
                col = c('tw_crack', 3 if left else 2)
            elif y == gy0:
                col = c('tw_black', 1)
            elif y == gy1 - 2:
                col = c('tw_black', 5)
            else:
                col = c('tw_black', t)
            put(x, y, col)
        for x in (CX - half, CX + half - 1):
            put(x, y, c('tw_crack', 2))
    # 梁の真ん中の盛り上がり（刃を受ける台）
    for y in range(gy1, gy1 + 3):
        for x in range(CX - 9, CX + 9):
            put(x, y, c('tw_black', 5 if x < CX else 3.6) if y < gy1 + 2 else c('tw_crack', 3 if x < CX else 2))
    # 梁の真ん中の金の菱
    for b in range(-3, 4):
        for a in range(-4, 4):
            dd = abs(a + 0.5) + abs(b)
            if dd <= 3.6:
                put(CX + a, gy0 + 3 + b, c('tw_gold', 6 if dd < 1.6 else 4 if a < 0 else 3))
    # 両端: 梁が下へ折れる腕と、金の皿（天秤）
    for sx in (-1, 1):
        xe = CX - half if sx < 0 else CX + half - 1
        for y in range(gy0 - 7, gy0):
            for w in range(3):
                x = xe - sx * w
                put(x, y, c('tw_black', 4 if w == 1 else 2) if w != 0 else c('tw_crack', 3))
        cxp = xe - sx * 1
        for a in range(-5, 6):
            put(cxp + a, gy0 - 8, c('tw_gold', 5 if a < 0 else 3))
        for a in range(-4, 5):
            put(cxp + a, gy0 - 9, c('tw_gold', 3 if a < 0 else 2))
        for a in range(-3, 4):
            put(cxp + a, gy0 - 10, c('tw_gold', 1))
    # ---- 柄（夜の紫の帯の巻き）と金の輪、柄頭
    for y in range(8, gy0):
        for x in range(CX - 2, CX + 2):
            t = 4 if x < CX else 2.6
            if (y // 2) % 2 == 0:
                t -= 1.6
            put(x, y, c('tw_night', t))
    for y in (gy0 - 2, gy0 - 1, 8, 9):
        for x in range(CX - 3, CX + 3):
            put(x, y, c('tw_gold', 5 if x < CX else 3))
    for b in range(-4, 5):
        for a in range(-5, 5):
            dd = abs(a + 0.5) + abs(b)
            if dd <= 4.6:
                put(CX + a, 3 + b, c('tw_crack', 3) if dd >= 3.6 else (c('tw_libra', 2) if dd < 1.2 else c('tw_black', 4 if a < 0 else 2.4)))
    return im


if __name__ == '__main__':
    out = sys.argv[1]
    im = draw()
    im.save(out.replace('.png', '_raw.png'))
    sc = 7
    bg = Image.new('RGBA', im.size, (24, 24, 30, 255)); bg.alpha_composite(im)
    sh = Image.new('RGBA', im.size, (215, 215, 222, 255))
    for y in range(im.height):
        for x in range(im.width):
            if im.getpixel((x, y))[3]:
                sh.putpixel((x, y), (30, 30, 36, 255))
    ref = Image.open(r'C:/Users/nisimoto/Desktop/mod/docs/review/refs_1118/14_swords_healong.png').convert('RGBA')
    bronze = Image.open(r'C:/Users/nisimoto/Desktop/mod/docs/review/refs_1118/13_bronze_set.jpg').convert('RGBA').crop((20, 0, 150, 459))
    r4 = ref.crop((440, 100, 660, 760))
    Hh = H * sc
    tiles = [('設定画（2 回目）', bg.resize((W * sc, Hh), Image.NEAREST)), ('影', sh.resize((W * sc, Hh), Image.NEAREST)),
             ('参照 bronze の剣', bronze.resize((int(bronze.width * Hh / bronze.height), Hh))), ('参照 healong 4 本目', r4.resize((int(r4.width * Hh / r4.height), Hh)))]
    f = ImageFont.truetype('C:/Windows/Fonts/meiryo.ttc', 28)
    sheet = Image.new('RGB', (sum(t.width for _, t in tiles) + 30 * 5, Hh + 60), (238, 238, 242)); d = ImageDraw.Draw(sheet)
    x = 30
    for l, t in tiles:
        sheet.paste(t.convert('RGB'), (x, 50)); d.text((x, 10), l, fill=(30, 30, 30), font=f); x += t.width + 30
    sheet.save(out); print(sheet.size)
