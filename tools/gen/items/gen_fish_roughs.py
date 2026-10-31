# -*- coding: utf-8 -*-
"""釣りの魚の絵のラフ (docs/design/54 の 6.2.4。作るかどうかは朝の確認で決める)。32×32、一品一案。
方向を見てもらうための 3 匹: 小鮒 (都のまわりの川。ふつう)、真珠鯛 (汐守の海)、巨神の夢を見た魚 (まれ)。
体は左向きの横姿、斜め上からの光 (左上が明るい)、外の縁は濃い一色の線。8 倍に広げた見本と、並べた一枚を docs/review/fish_roughs/ へ。
    python tools/gen/items/gen_fish_roughs.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/review/fish_roughs"
OUTLINE = (28, 26, 34, 255)


def put(px, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        px[x, y] = c if len(c) == 4 else c + (255,)


def outline(im):
    px = im.load()
    src = im.copy().load()
    for y in range(32):
        for x in range(32):
            if src[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and src[x + dx, y + dy][3] > 0
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE


def body(px, cx, cy, rx, ry, ramp, belly, hump=0.0):
    """紡錘形の体 (左が頭)。ramp = 背から腹への 4 段、belly = 腹の色。hump = 背の盛り上がり"""
    for y in range(32):
        for x in range(32):
            u = (x + 0.5 - cx) / rx
            if abs(u) >= 1:
                continue
            # 横から見た紡錘: 頭 (左) は楕円で丸く、尾 (右) へは細る。背は前寄りで盛り上がる
            h = math.sqrt(1 - u * u) ** (0.8 if u < 0 else 1.25)
            top = cy - ry * h * (1 + hump * max(0.0, 1 - abs(u + 0.25) * 1.6))
            bot = cy + ry * h * 0.9
            if not (top <= y + 0.5 <= bot):
                continue
            t = (y + 0.5 - top) / max(0.01, bot - top)
            light = t - 0.12 * (1 - abs(u))
            if t > 0.72:
                col = belly
            else:
                col = ramp[min(3, max(0, int(light * 4.4)))]
            put(px, x, y, col)


def tail(px, x0, cy, w, h, col, dark):
    for y in range(32):
        for x in range(32):
            dx = x + 0.5 - x0
            if 0 <= dx <= w:
                half = h * (0.25 + 0.75 * dx / w)
                notch = h * 0.55 * (dx / w) ** 2
                dy = abs(y + 0.5 - cy)
                if dy <= half and not (dx > w * 0.55 and dy < notch * 0.6):
                    put(px, x, y, dark if dy > half - 1.2 or dx > w - 1.2 else col)


def fin(px, pts, col):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        for x in range(int(min(xs)), int(max(xs)) + 1):
            c = False
            for i in range(len(pts)):
                (x0, y0), (x1, y1) = pts[i], pts[i - 1]
                if (y0 > y + 0.5) != (y1 > y + 0.5) and x + 0.5 < (x1 - x0) * (y + 0.5 - y0) / (y1 - y0) + x0:
                    c = not c
            if c:
                put(px, x, y, col)


def eye(px, x, y, ring=(236, 232, 214), pupil=(18, 16, 22)):
    put(px, x, y, ring)
    put(px, x + 1, y, ring)
    put(px, x, y + 1, pupil)
    put(px, x + 1, y + 1, ring)


def crucian():
    """小鮒: 都のまわりの川の、いちばんよく釣れる魚。くすんだ金茶の背、淡い腹、鱗の目の粗い横筋"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    ramp = [(92, 78, 40), (128, 108, 56), (160, 138, 74), (186, 166, 100)]
    tail(px, 23, 16, 7, 7, (140, 118, 62), (96, 80, 42))
    fin(px, [(12, 10), (19, 8), (21, 11), (13, 12)], (112, 94, 50))
    body(px, 14.5, 16, 10.5, 6.2, ramp, (214, 202, 160), hump=0.12)
    fin(px, [(12, 21), (16, 21), (14, 25)], (150, 128, 70))
    # 鱗の粗い格子 (体の中ほどに斜めの点)
    for y in range(13, 21):
        for x in range(9, 22):
            if (x + y) % 4 == 0 and px[x, y][3] and px[x, y][:3] != (214, 202, 160):
                put(px, x, y, (106, 90, 46))
    # 鰓の弧
    for y, x in ((13, 9), (14, 8), (15, 8), (16, 8), (17, 8), (18, 9)):
        put(px, x, y, (90, 74, 38))
    eye(px, 6, 14)
    outline(im)
    return im


def pearl_bream():
    """真珠鯛: 汐守の海。薄紅の体に、背から腹へ真珠の粒の列。背びれの棘、高い背"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    ramp = [(176, 82, 96), (210, 112, 120), (232, 150, 150), (244, 186, 180)]
    tail(px, 23, 16, 7, 8, (206, 104, 116), (150, 64, 78))
    # 背びれ (棘の並び)
    for i, x in enumerate(range(10, 21, 2)):
        h = 4 - abs(i - 2) * 0.6
        for y in range(int(9 - h), 10):
            put(px, x, y, (226, 136, 140) if y > 9 - h + 1 else (150, 64, 78))
        put(px, x + 1, 9, (200, 100, 110))
    body(px, 14.5, 16, 10.5, 7.0, ramp, (250, 222, 210), hump=0.22)
    fin(px, [(11, 22), (15, 22), (12, 26)], (220, 120, 128))
    # 真珠の粒の列 (三本、斜め)
    for row, (y0, n) in enumerate(((12, 5), (15, 6), (18, 5))):
        for i in range(n):
            x = 10 + i * 2 + row % 2
            y = y0 + (i % 2)
            if px[x, y][3]:
                put(px, x, y, (250, 246, 240))
                put(px, x + 1, y + 1, (210, 196, 200))
    for y, x in ((12, 9), (13, 8), (14, 8), (15, 8), (16, 8), (17, 8), (18, 9)):
        put(px, x, y, (150, 64, 78))
    eye(px, 6, 14)
    outline(im)
    return im


def dream_fish():
    """巨神の夢を見た魚: どの土地でもごくまれ。深い藍の体に、巨神の背の稜線のような淡い金の筋と、星の粒。尾は光の帯にほどける"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    ramp = [(28, 34, 78), (40, 52, 110), (60, 78, 146), (90, 112, 180)]
    # 光にほどける尾 (半透明の帯)
    for x in range(23, 31):
        for y in range(8, 25):
            dy = abs(y + 0.5 - 16)
            w = 2 + (x - 23) * 0.95
            if dy <= w:
                edge = dy > w - 1.2
                put(px, x, y, (200, 225, 255, 200 - (x - 23) * 20) if edge else (150, 190, 250, 170 - (x - 23) * 18))
    tail(px, 22, 16, 5, 6, (70, 96, 170), (40, 52, 110))
    fin(px, [(11, 10), (20, 7), (21, 11), (13, 12)], (60, 78, 146))
    body(px, 14.0, 16, 10.0, 6.4, ramp, (110, 130, 190), hump=0.15)
    fin(px, [(11, 21), (15, 21), (13, 25)], (70, 96, 170))
    # 背の稜線の金の筋 (山の連なり)
    ridge = [(8, 13), (10, 12), (12, 13), (14, 11), (16, 12), (18, 11), (20, 13)]
    for (x0, y0), (x1, y1) in zip(ridge, ridge[1:]):
        for t in range(4):
            x = round(x0 + (x1 - x0) * t / 3)
            y = round(y0 + (y1 - y0) * t / 3)
            put(px, x, y, (240, 208, 120))
    # 星の粒
    for x, y in ((11, 16), (15, 18), (18, 15), (13, 19), (20, 17), (9, 18)):
        put(px, x, y, (240, 244, 255))
    for y, x in ((13, 8), (14, 7), (15, 7), (16, 7), (17, 7), (18, 8)):
        put(px, x, y, (28, 34, 78))
    eye(px, 5, 14, ring=(255, 236, 170), pupil=(28, 20, 60))
    outline(im)
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    fish = [("crucian", "小鮒", "都のまわり・ふつう", crucian()), ("pearl_bream", "真珠鯛", "汐守の海", pearl_bream()),
            ("dream_fish", "巨神の夢を見た魚", "まれ", dream_fish())]
    k = 8
    font = None
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            font = ImageFont.truetype(f, 20)
            break
        except OSError:
            pass
    sheet = Image.new("RGBA", (len(fish) * (32 * k + 40) + 40, 32 * k + 140), (24, 26, 34, 255))
    d = ImageDraw.Draw(sheet)
    for i, (fid, name, note, im) in enumerate(fish):
        im.save(OUT / f"{fid}.png")
        big = im.resize((32 * k, 32 * k), Image.NEAREST)
        x = 40 + i * (32 * k + 40)
        sheet.paste(big, (x, 40), big)
        # 実寸 (32 と 64) も添える
        sheet.paste(im, (x, 40 + 32 * k + 10), im)
        sheet.paste(im.resize((64, 64), Image.NEAREST), (x + 44, 40 + 32 * k + 10), im.resize((64, 64), Image.NEAREST))
        d.text((x + 120, 40 + 32 * k + 18), name, fill=(240, 232, 210), font=font)
        d.text((x + 120, 40 + 32 * k + 46), note, fill=(170, 176, 190), font=font)
    d.text((40, 8), "釣りの魚のラフ (32×32。左向きの横姿・左上から光・濃い縁)", fill=(242, 193, 78), font=font)
    sheet.save(OUT / "fish_roughs.png")
    print("wrote", OUT / "fish_roughs.png")


if __name__ == "__main__":
    main()
