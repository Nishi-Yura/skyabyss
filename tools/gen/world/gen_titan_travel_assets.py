"""巨神での移動 (M3) の素材を作る。

使い方:
    python tools/gen/world/gen_titan_travel_assets.py            ラフ 3 案 (docs/review/titan_travel_roughs/) と、採用案の素材を src へ書く
    python tools/gen/world/gen_titan_travel_assets.py --roughs   ラフだけ (src へは書かない)

出力 (src/main/resources/assets/skyabyss/ 以下):
    - textures/item/stone_whale_call.png               岩鯨の呼び笛 (32x32)。案 A/B/C から ADOPTED_CALL
    - textures/block/titan_waymark_{front,side,base,cap}.png  巨神の碑 (32x32)。碑文は案 A/B/C から ADOPTED_MARK
    - textures/gui/travel/chart.png                    空の海図の画面 (1024x512、2 倍密度)。いまは一覧の行・つまみ・小さな印だけを旅の地図 (client/map/TravelMapScreen) が使う
    - sounds/titan/travel/{call,depart,veil,waymark}.ogg  既存の音は使わず、ここで合成する

色は共鳴の炉の画面 (tools/gen/items/gen_forge_assets.py) の石と骨、碑文の青緑は伝承の書と同じ。
何度実行しても同じ物ができる (乱数の種を固定。ogg は符号化の通し番号でバイト列だけ毎回変わるが、音は同じ)。
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../items',)]
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_forge_assets import (OUTLINE, SLATE, SLATE_DARK, RECESS, RECESS_EDGE, BONE_LIGHT, BONE, BONE_MID,  # noqa: E402
                              BONE_DARK, TEAL, TEAL_DIM, PALE, bone_frame, gem, recess)

REPO = Path(__file__).resolve().parent.parent.parent.parent
ROOT = REPO / "src/main/resources/assets/skyabyss"
ROUGHS = REPO / "docs/review/titan_travel_roughs"
SR = 44100

# 採用した案 (ラフの比較は docs/review/titan_travel_roughs/compare_*.png と 09_titan_travel.md)
ADOPTED_CALL = "a"
ADOPTED_MARK = "c"

GLOW = (63, 192, 232, 255)      # 炉や共鳴石と同じ光る青
GLOW_HI = (170, 238, 255, 255)
STONE_HI = (150, 156, 166, 255)
STONE = (104, 110, 122, 255)
STONE_LO = (66, 70, 82, 255)
DEEP = (44, 48, 58, 255)
DEEP_LO = (30, 33, 41, 255)
HOLLOW = (26, 22, 24, 255)


# ---------------------------------------------------------------- 小物

def outline(img):
    """不透明な画素のまわりに、暗い縁を付ける"""
    a = np.array(img)
    alpha = a[..., 3] > 0
    grown = alpha.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown |= np.roll(np.roll(alpha, dx, axis=1), dy, axis=0)
    edge = grown & ~alpha
    a[edge] = OUTLINE
    return Image.fromarray(a, "RGBA")


def tube(img, points, radii, shade, bands=(), band_color=TEAL, light=(-0.7, -0.7)):
    """折れ線 points に沿った管を描く。radii は各点の太さ。shade(t, lit) -> 色"""
    px = img.load()
    pts = np.array(points, dtype=float)
    seg = np.diff(pts, axis=0)
    lens = np.hypot(seg[:, 0], seg[:, 1])
    total = lens.sum()
    acc = np.concatenate([[0], np.cumsum(lens)])
    for y in range(img.height):
        for x in range(img.width):
            best = None
            for i in range(len(seg)):
                d = seg[i]
                L2 = d @ d
                u = 0 if L2 == 0 else max(0, min(1, ((x + 0.5 - pts[i][0]) * d[0] + (y + 0.5 - pts[i][1]) * d[1]) / L2))
                cx, cy = pts[i] + d * u
                dist = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                t = (acc[i] + lens[i] * u) / total
                if best is None or dist < best[0]:
                    best = (dist, t, x + 0.5 - cx, y + 0.5 - cy)
            dist, t, nx, ny = best
            r = np.interp(t, np.linspace(0, 1, len(radii)), radii)
            if dist <= r:
                n = math.hypot(nx, ny) or 1
                lit = -(nx / n * light[0] + ny / n * light[1]) * (dist / r)
                c = shade(t, lit)
                for (b0, b1) in bands:
                    if b0 <= t <= b1:
                        c = GLOW_HI if lit > 0.35 else band_color
                px[x, y] = c


def bone_shade(t, lit):
    if lit > 0.45:
        return BONE_LIGHT
    if lit > 0.0:
        return BONE
    if lit > -0.5:
        return BONE_MID
    return BONE_DARK


def stone_shade(t, lit):
    if lit > 0.4:
        return STONE_HI
    if lit > -0.3:
        return STONE
    return STONE_LO


def bezier(p0, p1, p2, n=12):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


# ---------------------------------------------------------------- 岩鯨の呼び笛 (3 案)

def call_a():
    """A: 反った鯨骨の角笛。青緑の碑文の帯が 2 本、吹き口は石の輪"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    path = bezier((25, 25), (10, 23), (7, 6), 14)
    tube(img, path, [5.2, 4.2, 3.2, 2.4, 1.8], bone_shade, bands=((0.30, 0.36), (0.58, 0.63)))
    d = ImageDraw.Draw(img)
    # 開いた口 (鈴口) の奥
    d.ellipse([22, 21, 28, 28], fill=BONE_MID)
    d.ellipse([23, 22, 27, 27], fill=HOLLOW)
    d.point((24, 23), fill=(60, 40, 44, 255))
    # 吹き口の石の輪
    d.rectangle([6, 4, 8, 6], fill=STONE)
    d.point((6, 4), fill=STONE_HI)
    d.point((8, 6), fill=STONE_LO)
    # 碑文の点
    for (x, y) in ((12, 22), (9, 16), (10, 12)):
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), GLOW)
    return outline(img)


def call_b():
    """B: まっすぐな骨の縦笛。光る指穴が 3 つ、下端に短い鎖"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    tube(img, [(9, 23), (25, 7)], [3.3, 2.6], bone_shade)
    d = ImageDraw.Draw(img)
    for t in (0.3, 0.5, 0.7):
        x = 9 + (25 - 9) * t
        y = 23 + (7 - 23) * t
        d.rectangle([x - 1, y - 1, x, y], fill=GLOW)
        img.putpixel((int(x - 1), int(y - 1)), GLOW_HI)
    d.rectangle([24, 5, 27, 8], fill=STONE)
    d.point((24, 5), fill=STONE_HI)
    # 鎖
    for i, (x, y) in enumerate(((7, 25), (6, 27), (5, 29))):
        d.rectangle([x, y, x + 1, y + 1], outline=STONE_LO if i % 2 else STONE)
    return outline(img)


def call_c():
    """C: 鯨の背骨 (椎骨) の円盤に吹き管を付けた笛。円盤に青緑の碑文の輪"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    tube(img, [(15, 16), (25, 6)], [2.6, 2.0], bone_shade, bands=((0.55, 0.65),))
    d = ImageDraw.Draw(img)
    cx, cy, r = 12, 19, 9
    for y in range(32):
        for x in range(32):
            dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if dd <= r:
                lit = -((x + 0.5 - cx) * -0.7 + (y + 0.5 - cy) * -0.7) / r
                img.putpixel((x, y), bone_shade(0, lit))
            if 5.0 <= dd <= 6.0:
                img.putpixel((x, y), TEAL if (x + y) % 3 else GLOW)
            if dd <= 2.4:
                img.putpixel((x, y), HOLLOW)
    d.rectangle([24, 4, 27, 7], fill=STONE)
    return outline(img)


CALLS = {"a": call_a, "b": call_b, "c": call_c}


# ---------------------------------------------------------------- 巨神の碑 (3 案の碑文)

def stone_tex(seed, base=DEEP, lo=DEEP_LO, hi=(58, 63, 75, 255)):
    rng = np.random.default_rng(seed)
    img = Image.new("RGBA", (32, 32), base)
    px = img.load()
    for y in range(32):
        for x in range(32):
            v = rng.random()
            if v < 0.12:
                px[x, y] = lo
            elif v > 0.9:
                px[x, y] = hi
    # 石の継ぎ目 (横に 2 本)
    for y in (10, 21):
        for x in range(32):
            if (x + y) % 9:
                px[x, y] = lo
    return img


def mark_front(design):
    img = stone_tex(11)
    d = ImageDraw.Draw(img)
    # 碑の縁 (面の端に骨の色の細い枠。UV は x 6..26、y 4..26 を使う)
    d.rectangle([6, 4, 25, 25], outline=(70, 76, 90, 255))
    d.line([7, 5, 24, 5], fill=(88, 95, 110, 255))
    cx, cy = 16, 15
    if design == "a":
        # A: 輪の中の鯨の尾びれ
        for y in range(32):
            for x in range(32):
                dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if 6.2 <= dd <= 7.2:
                    img.putpixel((x, y), TEAL)
        d.line([cx, cy + 4, cx, cy - 1], fill=GLOW)
        d.line([cx, cy - 1, cx - 4, cy - 4], fill=GLOW)
        d.line([cx, cy - 1, cx + 3, cy - 4], fill=GLOW)
        d.point((cx - 4, cy - 5), fill=GLOW_HI)
        d.point((cx + 3, cy - 5), fill=GLOW_HI)
        d.point((cx, cy + 5), fill=GLOW_HI)
        for (x, y) in ((cx - 7, cy + 7), (cx + 7, cy + 7), (cx, cy - 9)):
            d.point((x, y), fill=TEAL)
    elif design == "b":
        # B: 縦に並ぶ三つの山形と点 (到着の印)
        for i, yy in enumerate((cy - 6, cy - 1, cy + 4)):
            d.line([cx - 4, yy + 3, cx, yy, cx + 4, yy + 3], fill=GLOW if i == 1 else TEAL)
        for yy in (cy - 9, cy + 9):
            d.point((cx, yy), fill=GLOW_HI)
    else:
        # C: 伝承の書と同じ菱形の星と、下に尾びれ
        d.polygon([(cx, cy - 7), (cx + 5, cy - 2), (cx, cy + 3), (cx - 5, cy - 2)], outline=TEAL)
        d.polygon([(cx, cy - 4), (cx + 2, cy - 2), (cx, cy), (cx - 2, cy - 2)], fill=GLOW)
        d.line([cx - 3, cy + 7, cx, cy + 5, cx + 3, cy + 7], fill=TEAL)
    return img


def mark_side():
    img = stone_tex(12)
    d = ImageDraw.Draw(img)
    for y in range(6, 26, 4):
        d.point((16, y), fill=TEAL_DIM)
    return img


def mark_base():
    img = stone_tex(13, base=(78, 82, 92, 255), lo=(56, 60, 70, 255), hi=(98, 104, 116, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 29, 29], outline=(60, 64, 74, 255))
    for x in range(4, 28, 6):
        d.point((x, 3), fill=TEAL_DIM)
        d.point((x, 28), fill=TEAL_DIM)
    return img


def mark_cap():
    img = Image.new("RGBA", (32, 32), BONE)
    rng = np.random.default_rng(14)
    px = img.load()
    for y in range(32):
        for x in range(32):
            v = rng.random()
            px[x, y] = BONE_LIGHT if v > 0.85 else BONE_MID if v < 0.15 else BONE
    d = ImageDraw.Draw(img)
    for x in range(0, 32, 7):
        d.line([x, 0, x, 31], fill=BONE_DARK)
    return img


# ---------------------------------------------------------------- ラフの比較 (前から見た碑の見本も作る)

def preview_mark(design):
    """前から見た碑 (台座・碑・笠) を、テクスチャを貼って 8 倍で描く"""
    front = mark_front(design)
    base = mark_base()
    cap = mark_cap()
    S = 8
    out = Image.new("RGBA", (16 * S * 2, 16 * S * 2), (40, 44, 52, 255))
    canvas = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # 単位 → 画素は 2 倍。画像の y は上が 0 (ブロックの y=16)
    canvas.paste(base.crop((2, 26, 30, 32)), (2, 26))          # 台座 x1..15 y0..3
    canvas.paste(front.crop((6, 4, 26, 26)), (6, 4))           # 碑 x3..13 y3..14
    canvas.paste(cap.crop((4, 0, 28, 4)), (4, 0))              # 笠 x2..14 y14..16
    canvas = outline(canvas)
    out.alpha_composite(canvas.resize((32 * S, 32 * S), Image.NEAREST), (0, 0))
    return out


def write_roughs():
    ROUGHS.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (3 * 296, 330), (34, 37, 44, 255))
    d = ImageDraw.Draw(sheet)
    for i, (k, fn) in enumerate(CALLS.items()):
        icon = fn()
        icon.save(ROUGHS / f"call_{k}.png")
        sheet.paste(icon.resize((256, 256), Image.NEAREST), (i * 296 + 20, 20), icon.resize((256, 256), Image.NEAREST))
        # ゲーム内の大きさ (1 倍と 2 倍)
        sheet.paste(icon, (i * 296 + 20, 290), icon)
        sheet.paste(icon.resize((64, 64), Image.NEAREST), (i * 296 + 60, 262), icon.resize((64, 64), Image.NEAREST))
        d.text((i * 296 + 240, 296), k.upper(), fill=(226, 217, 194, 255))
    sheet.save(ROUGHS / "compare_call.png")
    sheet = Image.new("RGBA", (3 * 296, 300), (34, 37, 44, 255))
    d = ImageDraw.Draw(sheet)
    for i, k in enumerate(("a", "b", "c")):
        mark_front(k).save(ROUGHS / f"waymark_front_{k}.png")
        p = preview_mark(k).resize((256, 256), Image.NEAREST)
        sheet.paste(p, (i * 296 + 20, 20))
        d.text((i * 296 + 20, 282), k.upper(), fill=(226, 217, 194, 255))
    sheet.save(ROUGHS / "compare_waymark.png")
    print("roughs ->", ROUGHS)


# ---------------------------------------------------------------- 空の海図の画面

def chart_texture():
    img = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    W, H = 640, 380
    panel = Image.new("RGBA", (W, H), SLATE)
    rng = np.random.default_rng(20261018)
    arr = np.array(panel).astype(float)
    arr[..., :3] += rng.normal(0, 2.0, (H, W, 1))
    yy = np.linspace(0, 1, H)[:, None, None]
    arr[..., :3] = arr[..., :3] * (1 - yy * 0.2) + np.array(SLATE_DARK[:3]) * yy * 0.2
    panel = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(panel)
    bone_frame(d, 0, 0, W, H)
    for (x, y) in ((6, 6), (W - 7, 6), (6, H - 7), (W - 7, H - 7)):
        gem(d, x, y, 6)
    # 丸い海図 (中心 GUI (90, 98) = 画素 (180, 196)、半径 70 = 140)
    cx, cy, R = 180, 196, 140
    sky = np.zeros((H, W, 4), dtype=np.uint8)
    ys, xs = np.mgrid[0:H, 0:W]
    dist = np.hypot(xs + 0.5 - cx, ys + 0.5 - cy)
    inside = dist <= R
    t = np.clip(dist / R, 0, 1)
    noise = rng.normal(0, 1, (H, W))
    # 雲の海のまだら (大きな塊)
    blob = np.zeros((H, W))
    for _ in range(40):
        bx, by, br = rng.uniform(cx - R, cx + R), rng.uniform(cy - R, cy + R), rng.uniform(10, 30)
        blob += np.exp(-((xs - bx) ** 2 + (ys - by) ** 2) / (2 * br * br))
    blob = np.clip(blob * 0.45 - 0.15, 0, 1)
    base = np.array([18, 44, 56]) * (1 - t[..., None] * 0.5) + np.array([10, 22, 30]) * t[..., None] * 0.5
    col = base + blob[..., None] * np.array([20, 34, 36]) + noise[..., None] * 1.5
    sky[..., :3] = np.clip(col, 0, 255).astype(np.uint8)
    sky[..., 3] = np.where(inside, 255, 0)
    panel.alpha_composite(Image.fromarray(sky, "RGBA"))
    d = ImageDraw.Draw(panel)

    def ring(r, color, dash=0):
        steps = int(2 * math.pi * r)
        for i in range(steps):
            if dash and (i // dash) % 2:
                continue
            a = i / steps * 2 * math.pi
            panel.putpixel((int(cx + math.cos(a) * r), int(cy + math.sin(a) * r)), color)

    # 距離の輪 (250・1000・4000 ブロック。もとの海図の画面の対数の縮め方)
    for blocks in (250, 1000, 4000):
        r = R * math.log1p(blocks / 100) / math.log1p(6000 / 100)
        ring(r, TEAL_DIM, dash=4)
    for i in range(3):
        ring(R + 1 + i, (BONE_MID, BONE, BONE_DARK)[i])
    ring(R, TEAL_DIM)
    # 方位の刻み
    for k in range(32):
        a = k / 32 * 2 * math.pi
        l = 10 if k % 8 == 0 else 6 if k % 4 == 0 else 3
        for s in range(l):
            r = R - 2 - s
            panel.putpixel((int(cx + math.cos(a) * r), int(cy + math.sin(a) * r)), TEAL if k % 8 == 0 else TEAL_DIM)
    # 右の板 (一覧と見出し)
    recess(d, 344, 20, 624, 364)
    d.line([352, 76, 616, 76], fill=TEAL_DIM)
    gem(d, 484, 76, 4)
    d.line([352, 314, 616, 314], fill=RECESS_EDGE)
    img.alpha_composite(panel, (0, 0))

    # 一覧の行 (264x44): ふつう・重ねた・選んだ
    for i, (fill, edge, bar) in enumerate(((SLATE_DARK, RECESS_EDGE, None), ((36, 42, 54, 255), (70, 80, 96, 255), None),
                                           ((24, 52, 60, 255), TEAL, TEAL))):
        ox = i * 272
        d2 = ImageDraw.Draw(img)
        d2.rectangle([ox, 384, ox + 263, 427], fill=fill, outline=edge)
        d2.line([ox + 1, 384 + 1, ox + 262, 384 + 1], fill=tuple(min(255, c + 12) for c in fill[:3]) + (255,))
        if bar:
            d2.rectangle([ox + 2, 388, ox + 4, 423], fill=bar)
            d2.point((ox + 3, 389), fill=PALE)
    # つまみ (120x32): 押せる・重ねた・押せない
    for i, (fill, edge) in enumerate((((30, 60, 66, 255), TEAL), ((40, 84, 92, 255), PALE), ((30, 32, 38, 255), RECESS_EDGE))):
        ox = i * 128
        d2 = ImageDraw.Draw(img)
        d2.rectangle([ox, 432, ox + 119, 463], fill=OUTLINE)
        d2.rectangle([ox + 2, 434, ox + 117, 461], fill=fill, outline=edge)
        d2.line([ox + 4, 436, ox + 115, 436], fill=tuple(min(255, c + 20) for c in fill[:3]) + (255,))
        d2.line([ox + 4, 459, ox + 115, 459], fill=tuple(max(0, c - 10) for c in fill[:3]) + (255,))
    d2 = ImageDraw.Draw(img)

    def diamond(ox, oy, fill, light, r=6):
        c = (ox + 8, oy + 8)
        d2.polygon([(c[0], c[1] - r), (c[0] + r, c[1]), (c[0], c[1] + r), (c[0] - r, c[1])], fill=fill, outline=OUTLINE)
        d2.polygon([(c[0], c[1] - r + 2), (c[0] + r - 2, c[1]), (c[0], c[1])], fill=light)

    diamond(400, 432, TEAL_DIM, TEAL)                       # 碑
    diamond(416, 432, (60, 60, 66, 255), (96, 92, 96, 255))  # 失われた碑
    d2.line([419, 444, 428, 435], fill=(217, 119, 154, 255))
    diamond(432, 432, TEAL, PALE, r=7)                      # 選んだ
    d2.ellipse([433, 433, 446, 446], outline=PALE)
    # はじまりの地: 骨の色の小さな星
    d2.polygon([(456, 433), (458, 438), (463, 440), (458, 442), (456, 447), (454, 442), (449, 440), (454, 438)],
               fill=BONE_LIGHT, outline=OUTLINE)
    # 忘れる (×)
    d2.line([467, 435, 476, 444], fill=(217, 119, 154, 255), width=2)
    d2.line([476, 435, 467, 444], fill=(217, 119, 154, 255), width=2)
    # 自分 (岩鯨の頭を上から見た形、24x24。北が上)
    d2.polygon([(492, 433), (499, 440), (499, 452), (492, 455), (485, 452), (485, 440)], fill=BONE, outline=OUTLINE)
    d2.polygon([(492, 435), (497, 440), (492, 446)], fill=BONE_LIGHT)
    d2.point((489, 441), fill=GLOW)
    d2.point((495, 441), fill=GLOW)
    d2.line([492, 448, 492, 453], fill=TEAL)
    # 上下の矢印
    d2.polygon([(520, 435), (526, 443), (514, 443)], fill=TEAL, outline=OUTLINE)
    d2.polygon([(536, 445), (542, 437), (530, 437)], fill=TEAL, outline=OUTLINE)
    return img


# ---------------------------------------------------------------- 音

def env(n, attack, release):
    e = np.ones(n)
    a = int(attack * SR)
    r = int(release * SR)
    e[:a] = np.linspace(0, 1, a) ** 1.5
    e[n - r:] *= np.linspace(1, 0, r) ** 2
    return e


def bandnoise(n, lo, hi, rng):
    spec = np.fft.rfft(rng.normal(0, 1, n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[(f < lo) | (f > hi)] = 0
    out = np.fft.irfft(spec, n)
    return out / (np.abs(out).max() + 1e-9)


def sound_call(rng):
    """骨の笛: うつろな奇数倍音、息の音、終わりに少し上ずる"""
    dur = 2.8
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 196 * (1 + 0.04 * np.clip((t - 1.6) / 0.6, 0, 1)) * (1 + 0.006 * np.sin(2 * np.pi * 5.2 * t) * np.clip(t - 0.3, 0, 1))
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.35 * np.sin(3 * phase) + 0.12 * np.sin(5 * phase) + 0.05 * np.sin(7 * phase)
    breath = bandnoise(n, 900, 4200, rng) * 0.22
    sig = (tone * 0.6 + breath * (0.6 + 0.4 * np.sin(2 * np.pi * 1.3 * t) ** 2)) * env(n, 0.18, 0.9)
    # 遠い空の残響 (遅れた小さなこだま)
    echo = np.zeros(n)
    dly = int(0.32 * SR)
    echo[dly:] = sig[:-dly] * 0.35
    return (sig + echo) * 0.8


def sound_depart(rng):
    """巨神が身を起こす: 深いうなりと石のきしみ"""
    dur = 3.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 42 + 16 * np.clip(t / dur, 0, 1)
    phase = 2 * np.pi * np.cumsum(f) / SR
    groan = np.sin(phase) + 0.5 * np.sin(2 * phase + 0.4) + 0.25 * np.sin(3.01 * phase)
    rumble = bandnoise(n, 30, 180, rng) * 0.6
    creak = np.zeros(n)
    for _ in range(14):
        p = int(rng.uniform(0.3, dur - 0.4) * SR)
        L = int(0.05 * SR)
        k = np.arange(L) / SR
        creak[p:p + L] += np.sin(2 * np.pi * rng.uniform(300, 700) * k) * np.exp(-k * 60) * rng.uniform(0.2, 0.5)
    sig = (groan * 0.55 + rumble * 0.5 + creak) * env(n, 0.6, 1.2)
    return sig * 0.85


def sound_veil(rng):
    """雲の海へ潜る: 高い所から低い所へ下る、柔らかな風の渦"""
    dur = 3.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    chunks = 32
    size = n // chunks
    for i in range(chunks):
        fc = 2400 * (1 - i / chunks) + 300
        seg = bandnoise(size * 2, fc * 0.6, fc * 1.4, rng)[:size]
        out[i * size:(i + 1) * size] = seg * np.hanning(size) * 1.6
    sig = out * env(n, 0.8, 1.2) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.7 * t))
    return sig * 0.6


def sound_waymark(rng):
    """碑が応える: 青緑の水晶のような鈴"""
    dur = 1.8
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    for f, a, dk in ((784, 0.5, 2.6), (1176, 0.35, 3.4), (1568, 0.25, 4.2), (2093 * 1.007, 0.15, 6.0)):
        sig += a * np.sin(2 * np.pi * f * t) * np.exp(-t * dk)
    shimmer = bandnoise(n, 5000, 9000, rng) * np.exp(-t * 5) * 0.05
    return (sig + shimmer) * env(n, 0.005, 0.4) * 0.8


def write_sounds():
    import soundfile as sf
    out = ROOT / "sounds/titan/travel"
    out.mkdir(parents=True, exist_ok=True)
    for name, fn in (("call", sound_call), ("depart", sound_depart), ("veil", sound_veil), ("waymark", sound_waymark)):
        data = fn(np.random.default_rng(len(name) * 101 + 3))
        data = (data / max(1e-9, np.abs(data).max()) * 0.85).astype(np.float32)
        sf.write(out / f"{name}.ogg", data, SR, format="OGG", subtype="VORBIS")
        print(f"{name}.ogg  {len(data) / SR:.2f}s")


def install():
    CALLS[ADOPTED_CALL]().save(ROOT / "textures/item/stone_whale_call.png")
    mark_front(ADOPTED_MARK).save(ROOT / "textures/block/titan_waymark_front.png")
    mark_side().save(ROOT / "textures/block/titan_waymark_side.png")
    mark_base().save(ROOT / "textures/block/titan_waymark_base.png")
    mark_cap().save(ROOT / "textures/block/titan_waymark_cap.png")
    (ROOT / "textures/gui/travel").mkdir(parents=True, exist_ok=True)
    chart = chart_texture()
    chart.save(ROOT / "textures/gui/travel/chart.png")
    chart.crop((0, 0, 640, 470)).save(ROUGHS / "chart_texture.png")
    write_sounds()
    print("installed call", ADOPTED_CALL, "waymark", ADOPTED_MARK)


if __name__ == "__main__":
    write_roughs()
    if "--roughs" not in sys.argv:
        install()
