"""第二章の小物 32x32 のドット絵 3 案ずつ (素材 4・砥石・塩漬け・粉薬・砂時計) と、塩の刃の効き目の絵 (18x18) とシート。
潮殻の色の言葉 (珊瑚・真珠層・青緑・白) で、既存のアイテム絵の作り (暗い輪郭 1 画素、左上の光、限られた色) に合わせる。

実行: python tools/rough/lowpoly/ch2/icons_ch2.py
出力: docs/review/ch2_roughs/items/icons/<品>_{a,b,c}_32.png、salt_whetted_18.png
      docs/review/ch2_roughs/items/icons_ch2.png (1x と 4x を暗い背景・明るい背景・インベントリの枠で。既存の潮の真珠と並べる)
入れ方: tools/install/install_ch2_gear_assets.py (選んだ案を assets へ、選ばなかった案を tools/sources/<品>/ へ)
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[4]
OUT = ROOT / "docs/review/ch2_roughs/items"
ICON_DIR = OUT / "icons"
OUTLINE = (26, 24, 30, 255)

FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/msgothic.ttc"]

# ------------------------------------------------------------------ 色
NACRE = [(104, 100, 120), (142, 138, 160), (182, 180, 200), (214, 214, 228), (238, 238, 246), (255, 255, 255)]
PINK = (232, 200, 214)
BLUE = (186, 220, 232)
SALT = [(120, 140, 148), (164, 182, 188), (204, 218, 222), (232, 240, 242), (250, 252, 252)]
CORAL = [(104, 36, 38), (156, 62, 56), (204, 98, 80), (238, 140, 112), (252, 186, 156)]
SEA = [(16, 48, 56), (28, 78, 84), (46, 110, 112), (72, 148, 142), (118, 186, 172)]
KELP = [(24, 46, 28), (40, 70, 38), (62, 98, 50), (92, 130, 66)]
WOOD = [(62, 46, 36), (92, 70, 52), (126, 98, 74), (160, 130, 100)]
STONE = [(54, 64, 74), (80, 94, 106), (112, 128, 140), (150, 166, 176)]
GLASS = [(96, 140, 152), (150, 196, 204), (200, 232, 236), (236, 250, 250)]
CLOTH = [(150, 136, 112), (186, 172, 146), (214, 204, 180), (236, 230, 212)]
GLOW = (150, 236, 220)


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def c(rgb):
    return tuple(rgb[:3]) + (255,)


def canvas(n=32):
    return Image.new("RGBA", (n, n), (0, 0, 0, 0))


def poly(im, pts, rgb):
    ImageDraw.Draw(im).polygon(pts, fill=c(rgb))


def rect(im, x0, y0, x1, y1, rgb):
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=c(rgb))


def px(im, x, y, rgb):
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.putpixel((int(x), int(y)), c(rgb))


def line(im, pts, rgb, w=1):
    ImageDraw.Draw(im).line(pts, fill=c(rgb), width=w)


def ellipse(im, box, rgb):
    ImageDraw.Draw(im).ellipse(box, fill=c(rgb))


def sphere(im, cx, cy, r, ramp, tint=None, spec=True):
    """左上から光の当たる球。ramp は暗い→明るい。tint = (色, 割合) で真珠層のにじみ"""
    n = im.size[0]
    for y in range(n):
        for x in range(n):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy) / r
            if d > 1:
                continue
            z = math.sqrt(max(0, 1 - d * d))
            lit = (-dx * 0.55 - dy * 0.65) / r + z * 0.75
            k = min(len(ramp) - 1, max(0, int((lit * 0.62 + 0.42) * len(ramp))))
            col = ramp[k]
            if tint is not None and d > 0.55 and (x + y) % 3 == 0:
                col = tint
            im.putpixel((x, y), c(col))
    if spec:
        px(im, round(cx - r * 0.4), round(cy - r * 0.45), (255, 255, 255))


def outline(im):
    a = np.array(im)
    op = a[..., 3] > 0
    pad = np.pad(op, 1)
    nb = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    ring = nb & ~op
    for y, x in zip(*np.nonzero(ring)):
        im.putpixel((int(x), int(y)), OUTLINE)
    return im


def light_edge(im, rgb):
    a = np.array(im)
    op = a[..., 3] > 0
    up = np.pad(op, ((1, 0), (0, 0)))[:-1, :]
    lf = np.pad(op, ((0, 0), (1, 0)))[:, :-1]
    edge = op & (~up | ~lf)
    for y, x in zip(*np.nonzero(edge)):
        if tuple(a[y, x, :3]) != tuple(OUTLINE[:3]):
            im.putpixel((int(x), int(y)), c(rgb))
    return im


def finish(im, hi=None):
    outline(im)
    return light_edge(im, hi) if hi else im


# ------------------------------------------------------------------ 小真珠
def small_pearl_a():
    """三つ寄りの小さな真珠 (潮の真珠より小さく、数で見せる)"""
    im = canvas()
    sphere(im, 11, 20, 6.2, NACRE, PINK)
    sphere(im, 21, 21, 5.4, NACRE, BLUE)
    sphere(im, 16, 11, 5.0, NACRE, PINK)
    return finish(im)


def small_pearl_b():
    """開いた半貝 (珊瑚色) に一粒"""
    im = canvas()
    ellipse(im, (3, 12, 29, 29), CORAL[1])
    ellipse(im, (5, 13, 27, 27), CORAL[3])
    ellipse(im, (7, 15, 25, 26), NACRE[3])
    ellipse(im, (9, 17, 23, 25), NACRE[2])
    for k in range(5):  # 貝の筋
        x = 6 + k * 5
        line(im, [(x, 27), (16 + (x - 16) // 3, 29)], CORAL[0])
    sphere(im, 16, 15, 5.5, NACRE, BLUE)
    return finish(im)


def small_pearl_c():
    """一粒の小真珠に、細い糸の通し穴 (左右の小さな影)"""
    im = canvas()
    sphere(im, 16, 16, 8.5, NACRE, PINK)
    px(im, 8, 17, NACRE[0])
    px(im, 24, 15, NACRE[0])
    line(im, [(2, 18), (7, 17)], SEA[2])
    line(im, [(25, 15), (30, 14)], SEA[2])
    return finish(im)


# ------------------------------------------------------------------ 塩結晶
def cube(im, x, y, s, ramp):
    """小さな斜めの立方体 (上・左・右の面)"""
    top = [(x, y), (x + s, y - s // 2), (x + 2 * s, y), (x + s, y + s // 2)]
    left = [(x, y), (x + s, y + s // 2), (x + s, y + s // 2 + s), (x, y + s)]
    right = [(x + s, y + s // 2), (x + 2 * s, y), (x + 2 * s, y + s), (x + s, y + s // 2 + s)]
    poly(im, left, ramp[1])
    poly(im, right, ramp[0])
    poly(im, top, ramp[3])


def salt_crystal_a():
    """白い立方体の塩の房 (大小 4 つ)"""
    im = canvas()
    cube(im, 9, 16, 7, SALT)
    cube(im, 4, 21, 5, SALT)
    cube(im, 17, 20, 5, SALT)
    cube(im, 13, 9, 5, SALT)
    for (x, y) in ((15, 15), (10, 22), (19, 8)):
        px(im, x, y, SALT[4])
    return finish(im)


def salt_crystal_b():
    """縦に伸びた一本の塩の柱 (塩の道の標の小さな写し)、根もとに白い粉"""
    im = canvas()
    poly(im, [(12, 26), (13, 8), (17, 3), (21, 8), (20, 26)], SALT[2])
    poly(im, [(17, 3), (21, 8), (20, 26), (17, 27)], SALT[1])
    poly(im, [(12, 26), (13, 8), (17, 3), (15, 10), (15, 26)], SALT[3])
    for y in (11, 16, 21):  # 刻み (干潮・満潮の目盛り)
        line(im, [(14, y), (19, y)], SEA[2])
    ellipse(im, (6, 24, 26, 30), SALT[1])
    ellipse(im, (8, 24, 24, 28), SALT[3])
    return finish(im)


def salt_crystal_c():
    """塩の山 (浜で乾いた塩) の上に透けた結晶 2 つ"""
    im = canvas()
    poly(im, [(3, 28), (10, 18), (16, 15), (23, 18), (29, 28)], SALT[2])
    poly(im, [(16, 15), (23, 18), (29, 28), (18, 28)], SALT[1])
    for (x, y) in ((8, 25), (12, 22), (21, 24), (25, 26), (15, 26)):
        px(im, x, y, SALT[4])
    poly(im, [(10, 17), (12, 8), (15, 6), (16, 16)], GLASS[2])
    poly(im, [(15, 6), (16, 16), (18, 15), (17, 8)], GLASS[1])
    poly(im, [(17, 16), (20, 9), (23, 10), (21, 17)], GLASS[2])
    poly(im, [(20, 9), (23, 10), (21, 17)], GLASS[1])
    px(im, 12, 9, GLASS[3])
    return finish(im)


# ------------------------------------------------------------------ 真珠の護り糸
def thread_a():
    """真珠色の糸のかせ (楕円の輪を重ねる) に、真珠の玉が一つ"""
    im = canvas()
    d = ImageDraw.Draw(im)
    for k, col in enumerate((NACRE[1], NACRE[3], NACRE[2], NACRE[4])):
        d.ellipse((5 + k, 7 + k, 26 - k, 25 - k), outline=c(col), width=2)
    line(im, [(16, 25), (20, 30)], NACRE[2])
    sphere(im, 21, 27, 3.2, NACRE, BLUE, spec=False)
    px(im, 20, 26, (255, 255, 255))
    return finish(im)


def thread_b():
    """一つの輪に結んだ護り結び。結び目に海硝子の玉、短い房が二本垂れる"""
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((6, 3, 26, 21), outline=c(NACRE[3]), width=2)
    d.arc((8, 5, 24, 19), start=200, end=320, fill=c(NACRE[4]), width=1)
    ellipse(im, (12, 17, 20, 25), NACRE[2])
    ellipse(im, (13, 18, 18, 23), SEA[3])
    ellipse(im, (14, 19, 17, 22), GLOW)
    for x0, dx in ((14, -1), (18, 1)):
        for k in range(6):
            px(im, x0 + dx * (k // 2), 25 + k, NACRE[3] if k % 2 else NACRE[2])
    return finish(im)


def thread_c():
    """貝の糸巻き (珊瑚色の二枚貝の殻に、真珠色の糸を巻いた)"""
    im = canvas()
    poly(im, [(8, 4), (24, 4), (21, 9), (11, 9)], CORAL[3])
    poly(im, [(8, 28), (24, 28), (21, 23), (11, 23)], CORAL[2])
    rect(im, 11, 9, 21, 23, NACRE[2])
    for y in range(10, 23, 2):
        line(im, [(11, y), (21, y)], NACRE[4] if y % 4 == 0 else NACRE[3])
    line(im, [(21, 16), (28, 20)], NACRE[3])
    line(im, [(28, 20), (29, 25)], NACRE[3])
    for x in (12, 16, 20):
        px(im, x, 6, CORAL[4])
    return finish(im)


# ------------------------------------------------------------------ 貝の欠け
def chip_a():
    """扇の貝の欠け (珊瑚色の筋が付け根から放射)"""
    im = canvas()
    shape = [(4, 24), (10, 9), (19, 5), (27, 11), (24, 21), (14, 27)]
    poly(im, shape, CORAL[2])
    poly(im, [(14, 27), (24, 21), (27, 11), (22, 16)], CORAL[1])
    poly(im, [(6, 23), (11, 11), (15, 9)], CORAL[3])
    ribs = canvas()
    for t in range(6):
        a = math.radians(-100 + t * 16)
        line(ribs, [(8, 26), (8 + 26 * math.cos(a) * 1.0, 26 + 26 * math.sin(a))], CORAL[0])
    mask = canvas()
    poly(mask, shape, (255, 255, 255))
    rb = np.array(ribs)
    keep = (np.array(mask)[..., 3] > 0) & (rb[..., 3] > 0)
    a_ = np.array(im)
    a_[keep] = rb[keep]
    im = Image.fromarray(a_)
    line(im, [(10, 9), (19, 5)], CORAL[4])
    return finish(im)


def chip_b():
    """割れた貝の破片 (外は珊瑚、割れ口に真珠層)"""
    im = canvas()
    poly(im, [(5, 20), (12, 6), (22, 4), (27, 14), (20, 22), (9, 27)], CORAL[2])
    poly(im, [(12, 12), (20, 9), (23, 14), (17, 19), (11, 19)], NACRE[3])
    poly(im, [(12, 12), (20, 9), (17, 12)], NACRE[4])
    line(im, [(11, 19), (17, 19), (23, 14)], NACRE[1])
    line(im, [(5, 20), (12, 6)], CORAL[3])
    line(im, [(20, 22), (9, 27)], CORAL[0])
    return finish(im)


def chip_c():
    """小さな欠け二つ (白い巻貝のかけらと珊瑚の欠け)"""
    im = canvas()
    poly(im, [(3, 18), (8, 9), (16, 8), (17, 17), (10, 22)], SALT[2])
    line(im, [(6, 15), (14, 11)], SALT[0])
    line(im, [(8, 19), (15, 15)], SALT[0])
    poly(im, [(14, 24), (19, 14), (28, 16), (26, 26), (19, 29)], CORAL[2])
    line(im, [(19, 14), (28, 16)], CORAL[4])
    for k in range(3):
        line(im, [(17 + k * 3, 27), (21 + k * 2, 17)], CORAL[1])
    return finish(im)


# ------------------------------------------------------------------ 塩の砥石
def whet_body(im, ramp, crust):
    x = np.arange(32)[None, :] + 0.5
    y = np.arange(32)[:, None] + 0.5
    u = (x - 16) * 0.8 + (y - 16) * -0.6
    v = (x - 16) * 0.6 + (y - 16) * 0.8
    body = (np.abs(u) <= 12.5) & (np.abs(v) <= 5.0)
    a = np.array(im)
    a[body] = c(ramp[1])
    a[body & (v < -1.6)] = c(ramp[2])
    a[body & (v > 2.6)] = c(ramp[0])
    a[body & (v < -3.6)] = c(crust)
    return Image.fromarray(a), u, v, body


def salt_whetstone_a():
    """青灰の平たい砥石の上の面に白い塩の皮、塩の粒が散る"""
    im = canvas()
    im, u, v, body = whet_body(im, STONE, SALT[3])
    for k in (-8, -2, 4, 9):
        px(im, 16 + k * 0.8 - 3.5 * 0.6, 16 - k * 0.6 - 3.5 * 0.8, SALT[4])
    px(im, 25, 7, SALT[4])
    px(im, 27, 9, SALT[3])
    px(im, 6, 25, GLOW)
    return finish(im)


def salt_whetstone_b():
    """白い塩の砥石 (塩の固まり) に珊瑚の帯を巻いた"""
    im = canvas()
    im, u, v, body = whet_body(im, SALT, SALT[3])
    a = np.array(im)
    a[body & (np.abs(u + 3) < 1.6)] = c(CORAL[2])
    a[body & (np.abs(u + 3) < 1.6) & (v < -1.6)] = c(CORAL[3])
    a[body & (np.abs(u - 6) < 0.7)] = c(SALT[0])
    im = Image.fromarray(a)
    px(im, 24, 6, GLOW)
    return finish(im)


def salt_whetstone_c():
    """海硝子色の結晶の棒 (塩の柱を研いだ)。刃に当てる面が光る"""
    im = canvas()
    im, u, v, body = whet_body(im, SEA[1:], GLASS[2])
    a = np.array(im)
    a[body & (np.abs(u) > 11.0)] = c(SEA[1])
    im = Image.fromarray(a)
    for k in (-6, 0, 6):
        px(im, 16 + k * 0.8, 16 - k * 0.6, GLASS[3])
    return finish(im)


# ------------------------------------------------------------------ 潮の塩漬け
def pickle_a():
    """ガラスの壺に漬けた昆布、布の蓋と紐"""
    im = canvas()
    rect(im, 8, 9, 23, 28, GLASS[1])
    rect(im, 9, 12, 22, 27, KELP[2])
    for x in (11, 14, 17, 20):
        line(im, [(x, 26), (x + (1 if x % 2 else -1), 13)], KELP[1])
    for (x, y) in ((12, 15), (18, 18), (15, 22), (20, 14)):
        px(im, x, y, SALT[3])
    rect(im, 9, 10, 10, 26, GLASS[3])
    rect(im, 7, 5, 24, 9, CLOTH[2])
    rect(im, 7, 8, 24, 9, CLOTH[0])
    line(im, [(7, 8), (24, 8)], CORAL[2])
    px(im, 6, 9, CORAL[2])
    px(im, 5, 10, CORAL[2])
    return finish(im)


def pickle_b():
    """塩をまぶして束ねた昆布の巻き (珊瑚色の紐)"""
    im = canvas()
    poly(im, [(4, 22), (22, 5), (28, 10), (10, 28)], KELP[2])
    poly(im, [(4, 22), (22, 5), (24, 7), (6, 24)], KELP[3])
    poly(im, [(10, 28), (28, 10), (27, 9), (9, 27)], KELP[0])
    for t in (0.35, 0.65):
        x = 4 + 18 * t + 3
        y = 22 - 17 * t + 3
        line(im, [(x - 3, y - 3), (x + 3, y + 3)], CORAL[2], 2)
    for (x, y) in ((9, 21), (14, 15), (19, 11), (13, 21), (21, 14)):
        px(im, x, y, SALT[4])
    return finish(im)


def pickle_c():
    """素焼きの小壺 (青緑の釉)、口から昆布の先と塩"""
    im = canvas()
    ellipse(im, (6, 11, 26, 29), SEA[2])
    ellipse(im, (8, 12, 22, 25), SEA[3])
    rect(im, 11, 7, 21, 12, SEA[1])
    rect(im, 10, 6, 22, 8, SEA[3])
    line(im, [(14, 6), (12, 1)], KELP[3], 2)
    line(im, [(18, 6), (20, 2)], KELP[2], 2)
    for (x, y) in ((13, 6), (17, 5), (20, 6)):
        px(im, x, y, SALT[4])
    line(im, [(8, 20), (24, 20)], CORAL[2])
    return finish(im)


# ------------------------------------------------------------------ 真珠の粉薬
def powder_a():
    """折った紙の包み (塩白の紙) に真珠の粉、珊瑚の封"""
    im = canvas()
    poly(im, [(4, 14), (16, 6), (28, 14), (16, 26)], CLOTH[2])
    poly(im, [(16, 6), (28, 14), (16, 26)], CLOTH[1])
    poly(im, [(4, 14), (16, 18), (28, 14), (16, 26)], CLOTH[3])
    line(im, [(4, 14), (16, 18), (28, 14)], CLOTH[0])
    ellipse(im, (13, 15, 19, 21), CORAL[2])
    px(im, 15, 16, CORAL[4])
    for (x, y) in ((22, 22), (25, 24), (23, 27), (27, 21)):
        px(im, x, y, NACRE[4])
    return finish(im)


def powder_b():
    """貝の皿に盛った白い粉 (真珠層の光の粒)"""
    im = canvas()
    ellipse(im, (3, 14, 29, 28), CORAL[1])
    ellipse(im, (5, 15, 27, 26), NACRE[2])
    poly(im, [(8, 21), (13, 14), (19, 12), (24, 20), (16, 23)], SALT[3])
    poly(im, [(13, 14), (19, 12), (17, 17)], SALT[4])
    for (x, y) in ((12, 18), (18, 16), (21, 19), (15, 20)):
        px(im, x, y, PINK if (x + y) % 2 else BLUE)
    for k in range(4):
        px(im, 6 + k * 6, 27, CORAL[0])
    return finish(im)


def powder_c():
    """小瓶 (海硝子) に真珠の粉、貝の栓"""
    im = canvas()
    ellipse(im, (8, 12, 24, 29), GLASS[1])
    rect(im, 13, 7, 19, 13, GLASS[1])
    ellipse(im, (9, 18, 23, 28), NACRE[3])
    poly(im, [(10, 20), (22, 20), (22, 22), (10, 22)], NACRE[4])
    for (x, y) in ((12, 23), (17, 25), (20, 22)):
        px(im, x, y, PINK)
    rect(im, 12, 4, 20, 7, CORAL[2])
    rect(im, 12, 4, 20, 4, CORAL[3])
    px(im, 10, 15, GLASS[3])
    px(im, 10, 16, GLASS[3])
    return finish(im)


# ------------------------------------------------------------------ 潮の刻みの砂時計
def glass_bulbs(im, x0, x1, y0, y1, sand):
    """上下の玉と、落ちる白い塩"""
    mid = (y0 + y1) // 2
    cx = (x0 + x1) / 2
    poly(im, [(x0, y0), (x1, y0), (cx + 1, mid), (cx - 1, mid)], GLASS[2])
    poly(im, [(cx - 1, mid), (cx + 1, mid), (x1, y1), (x0, y1)], GLASS[2])
    # 上に少し残り、下に積もる
    poly(im, [(x0 + 3, mid - 4), (x1 - 3, mid - 4), (cx, mid - 1)], sand)
    poly(im, [(x0 + 1, y1), (cx, y1 - 5), (x1 - 1, y1)], sand)
    line(im, [(cx, mid), (cx, y1 - 5)], sand)
    line(im, [(x0 + 1, y0 + 1), (cx - 2, mid - 1)], GLASS[3])


def hourglass_a():
    """流木の柱と板、海硝子の玉、白い塩が落ちる。板に珊瑚の刻み"""
    im = canvas()
    glass_bulbs(im, 9, 22, 6, 25, SALT[3])
    rect(im, 5, 2, 26, 5, WOOD[2])
    rect(im, 5, 26, 26, 29, WOOD[1])
    rect(im, 6, 5, 7, 26, WOOD[2])
    rect(im, 24, 5, 25, 26, WOOD[1])
    for x in (9, 13, 17, 21):
        px(im, x, 3, CORAL[3])
        px(im, x, 27, CORAL[3])
    return finish(im, WOOD[3])


def hourglass_b():
    """上下が真珠層の二枚貝の蓋、柱は珊瑚"""
    im = canvas()
    glass_bulbs(im, 9, 22, 7, 24, SALT[3])
    ellipse(im, (4, 1, 27, 9), NACRE[2])
    ellipse(im, (5, 2, 26, 6), NACRE[4])
    ellipse(im, (4, 23, 27, 31), NACRE[1])
    ellipse(im, (5, 23, 26, 27), NACRE[3])
    rect(im, 6, 7, 7, 25, CORAL[2])
    rect(im, 24, 7, 25, 25, CORAL[1])
    px(im, 15, 3, GLOW)
    return finish(im)


def hourglass_c():
    """青緑の金具の枠に波の刻み、塩は海硝子の光をうっすら帯びる"""
    im = canvas()
    glass_bulbs(im, 9, 22, 6, 25, GLASS[3])
    rect(im, 5, 2, 26, 5, SEA[2])
    rect(im, 5, 26, 26, 29, SEA[1])
    rect(im, 6, 5, 7, 26, SEA[2])
    rect(im, 24, 5, 25, 26, SEA[1])
    for x in range(7, 25, 4):  # 波の刻み
        px(im, x, 4, SEA[4])
        px(im, x + 1, 3, SEA[4])
        px(im, x + 2, 4, SEA[4])
    px(im, 15, 20, GLOW)
    return finish(im)


# ------------------------------------------------------------------ 塩の刃の効き目 (18x18)
def effect_icon():
    im = canvas(18)
    x = np.arange(18)[None, :] + 0.5
    y = np.arange(18)[:, None] + 0.5
    u = (x - 9) * 0.8 + (y - 9) * -0.6
    v = (x - 9) * 0.6 + (y - 9) * 0.8
    body = (np.abs(u) <= 7.0) & (np.abs(v) <= 3.0)
    a = np.array(im)
    a[body] = c(SALT[2])
    a[body & (v < -1.0)] = c(SALT[4])
    a[body & (v > 1.4)] = c(STONE[2])
    im = Image.fromarray(a)
    for (px_, py_) in ((14, 3), (3, 14), (15, 6)):
        px(im, px_, py_, GLOW)
    return finish(im)


ITEMS = {
    "small_pearl": (small_pearl_a, small_pearl_b, small_pearl_c),
    "salt_crystal": (salt_crystal_a, salt_crystal_b, salt_crystal_c),
    "pearl_ward_thread": (thread_a, thread_b, thread_c),
    "shell_chip": (chip_a, chip_b, chip_c),
    "salt_whetstone": (salt_whetstone_a, salt_whetstone_b, salt_whetstone_c),
    "tide_pickle": (pickle_a, pickle_b, pickle_c),
    "pearl_powder": (powder_a, powder_b, powder_c),
    "tide_hourglass": (hourglass_a, hourglass_b, hourglass_c),
}
NAMES_JP = {"small_pearl": "小真珠", "salt_crystal": "塩結晶", "pearl_ward_thread": "真珠の護り糸", "shell_chip": "貝の欠け",
            "salt_whetstone": "塩の砥石", "tide_pickle": "潮の塩漬け", "pearl_powder": "真珠の粉薬", "tide_hourglass": "潮の刻みの砂時計"}
NOTES_JP = {
    "small_pearl": ("三つ寄り", "半貝に一粒", "糸穴の一粒"),
    "salt_crystal": ("立方体の房", "刻みの塩柱", "塩の山と結晶"),
    "pearl_ward_thread": ("かせと玉", "護り結び", "貝の糸巻き"),
    "shell_chip": ("扇の欠け", "割れ口に真珠層", "小さな欠け二つ"),
    "salt_whetstone": ("青灰に塩の皮", "塩に珊瑚の帯", "海硝子の棒"),
    "tide_pickle": ("ガラスの壺", "昆布の巻き", "青緑の小壺"),
    "pearl_powder": ("紙の包み", "貝の皿", "海硝子の小瓶"),
    "tide_hourglass": ("流木の枠", "真珠層の蓋", "青緑の枠に波"),
}


def sheet(icons):
    cell = 150
    W = 200 + 3 * (cell * 2 + 16)
    H = 70 + len(ITEMS) * (cell + 30)
    S = Image.new("RGB", (W, H), (24, 28, 36))
    d = ImageDraw.Draw(S)
    d.text((10, 8), "二章の小物 3 案ずつ (32x32。左 = 暗い枠の 4x と 1x、右 = 明るい背景の 4x)。既存の「潮の真珠」は右端", font=font(18), fill=(255, 255, 255))
    d.text((10, 36), "潮殻の色: 珊瑚・真珠層・青緑・白 / 光る所は海硝子", font=font(13), fill=(200, 205, 215))
    ref = Image.open(ROOT / "src/main/resources/assets/skyabyss/textures/item/tide_pearl.png").convert("RGBA")
    y = 70
    for name, fns in ITEMS.items():
        d.text((10, y + 50), NAMES_JP[name], font=font(16), fill=(255, 232, 160))
        d.text((10, y + 74), name, font=font(11), fill=(170, 176, 188))
        for k, o in enumerate("abc"):
            ic = icons[(name, o)]
            x = 200 + k * (cell * 2 + 16)
            slot = Image.new("RGBA", (cell, cell), (46, 52, 62, 255))
            slot.alpha_composite(ic.resize((128, 128), Image.NEAREST), (4, 4))
            slot.alpha_composite(ic, (cell - 36, cell - 36))
            S.paste(slot.convert("RGB"), (x, y))
            light = Image.new("RGBA", (cell, cell), (198, 198, 198, 255))
            light.alpha_composite(ic.resize((128, 128), Image.NEAREST), (11, 11))
            S.paste(light.convert("RGB"), (x + cell + 4, y))
            d.text((x, y + cell + 4), "%s  %s" % (o.upper(), NOTES_JP[name][k]), font=font(13), fill=(220, 224, 232))
        y += cell + 30
    small = Image.new("RGBA", (40, 40), (46, 52, 62, 255))
    small.alpha_composite(ref, (4, 4))
    S.paste(small.convert("RGB"), (W - 50, 30))
    p = OUT / "icons_ch2.png"
    S.save(p)
    print("sheet", p, S.size)


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    icons = {}
    for name, fns in ITEMS.items():
        for o, fn in zip("abc", fns):
            im = fn()
            assert im.size == (32, 32), name
            im.save(ICON_DIR / f"{name}_{o}_32.png")
            icons[(name, o)] = im
    effect_icon().save(ICON_DIR / "salt_whetted_18.png")
    sheet(icons)


if __name__ == "__main__":
    main()
