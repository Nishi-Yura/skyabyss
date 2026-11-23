# UI の作り直しのラフ 2 回目 (11/23)。四角い板をやめて、この世界の「もの」から形を決めた 3 案。
#   D 祠の得物棚: 梁から縄で六つの得物を吊る。情報は絵馬、始めるのは鈴
#   E 巨神の肋: 巨大な肋骨が画面を横切る。骨に得物と札を結ぶ
#   F 切り絵: ボスの切り絵 (黒・生成り・桃色・浮かぶ菱形) の紙を重ねる
# 形は GUI の画素 (480x270) で描いて 4 倍し、字は ui_rough_1123 の Font (マイクラの字) で上に描く。
# python -I tools/rough/ui_rough_1123b.py --font <字のフォルダ> --tex <ブロックの絵のフォルダ>
import argparse
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui_rough_1123 as base  # noqa: E402
from ui_rough_1123 import G, L, SHOTS, Canvas, Font, icon, item, load_skills, keyed, first_sentence, _dim  # noqa: E402

OUT = base.OUT
GW, GH = 480, 270
TEX = {}
JOB = base.SEL_JOB
JOBS = base.JOBS


def tex(name, k=1.0):
    key = (name, k)
    if key not in TEX:
        t = Image.open(os.path.join(ARGS.tex, name + ".png")).convert("RGBA")
        if k != 1.0:
            r, g, b, a = t.split()
            r, g, b = [c.point(lambda v: min(255, int(v * k))) for c in (r, g, b)]
            t = Image.merge("RGBA", (r, g, b, a))
        big = Image.new("RGBA", (GW, GH))
        for y in range(0, GH, t.height):
            for x in range(0, GW, t.width):
                big.paste(t, (x, y))
        TEX[key] = big
    return TEX[key]


class Layer:
    def __init__(self):
        self.im = Image.new("RGBA", (GW, GH), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def P(self, pts):
        return [(int(round(x)), int(round(y))) for x, y in pts]

    def poly(self, pts, c, outline=None):
        self.d.polygon(self.P(pts), fill=c)
        if outline:
            self.d.line(self.P(pts) + [self.P(pts)[0]], fill=outline, width=1)

    def tpoly(self, pts, t, outline=None):
        m = Image.new("L", (GW, GH), 0)
        ImageDraw.Draw(m).polygon(self.P(pts), fill=255)
        self.im.paste(t, (0, 0), m)
        if outline:
            self.d.line(self.P(pts) + [self.P(pts)[0]], fill=outline, width=1)

    def rect(self, x, y, w, h, c):
        self.d.rectangle((int(x), int(y), int(x + w - 1), int(y + h - 1)), fill=c)

    def line(self, pts, c, w=1):
        self.d.line(self.P(pts), fill=c, width=w)

    def px(self, x, y, c):
        self.d.point((int(x), int(y)), fill=c)

    def blob(self, pts, r, c):
        # 太い線 (円を並べる)
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
            for i in range(n):
                f = i / n
                x, y = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f
                self.d.ellipse((x - r, y - r, x + r, y + r), fill=c)

    def flush(self, cv):
        cv.im.alpha_composite(self.im.resize((GW * G, GH * G), Image.NEAREST))
        self.im = Image.new("RGBA", (GW, GH), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)


def jag(pts, step, amp, seed, closed=True):
    # 辺をちぎった形に (はさみ・破れ)
    rnd = random.Random(seed)
    out = []
    seq = pts + ([pts[0]] if closed else [])
    for (x0, y0), (x1, y1) in zip(seq, seq[1:]):
        ln = math.hypot(x1 - x0, y1 - y0)
        if ln < 0.5:
            continue
        n = max(1, int(ln / step))
        nx, ny = -(y1 - y0) / ln, (x1 - x0) / ln
        for i in range(n):
            f = i / n
            a = rnd.uniform(-amp, amp)
            out.append((x0 + (x1 - x0) * f + nx * a, y0 + (y1 - y0) * f + ny * a))
    return out


def rot(cx, cy, w, h, deg):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    pts = [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]
    return [(cx + x * ca - y * sa, cy + x * sa + y * ca) for x, y in pts]


def figure(job):
    im = keyed(Image.open(os.path.join(SHOTS, "job_%s.png" % job)))
    a = im.getchannel("A").point(lambda v: 255 if v > 128 else 0)
    im.putalpha(a)
    x0, y0, x1, y1 = a.getbbox()
    return im.crop((x0 - 60, y0 - 60, x1 + 60, y1 + 60))


def put_figure(cv, im, cx, bottom, height):
    w = int(im.width * height * G / im.height)
    f = im.resize((w, int(height * G)), Image.LANCZOS)
    cv.im.alpha_composite(f, (int(cx * G - w / 2), int(bottom * G - height * G)))


def silhouette(im, fill, rim, rim_px):
    a = im.getchannel("A")
    big = a.filter(ImageFilter.MaxFilter(rim_px * 2 + 1))
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(Image.new("RGBA", im.size, rim), (0, 0), big)
    out.paste(Image.new("RGBA", im.size, fill), (0, 0), a)
    return out


def flat_item(name, c):
    im = item(name)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(Image.new("RGBA", im.size, c), (0, 0), im.getchannel("A"))
    return out


def texts(job):
    t = dict(
        title=L("gui.skyabyss.job_choice.title"),
        lead=L("gui.skyabyss.job_choice.lead"),
        name=L("job.skyabyss.%s" % job),
        sub=L("skilltree.skyabyss.%s" % dict((j, t) for j, t, _ in JOBS)[job]),
        role=L("job.skyabyss.%s.role" % job),
        weapon=L("gui.skyabyss.job_choice.weapon", L("item.skyabyss.%s" % dict((j, w) for j, _, w in JOBS)[job])),
        first=L("gui.skyabyss.job_choice.first", L("skill.skyabyss.%s" % base.SEL_SKILL)),
        eff=first_sentence(L("skill.skyabyss.%s.effect" % base.SEL_SKILL)),
        start=L("gui.skyabyss.job_choice.start"),
    )
    return t


def darken(cv, a):
    cv.im.alpha_composite(Image.new("RGBA", cv.im.size, (0, 0, 0, a)))


# ===================== D 祠の得物棚 =====================
WOOD_D = (52, 34, 22)
INK = (48, 30, 18)
RED = (176, 40, 30)
PAPER = (244, 238, 222)


def rope(ly, x0, x1, y0, sag, seed=0):
    # しめ縄: 撚りの斜めの縞
    pts = []
    for i in range(int(x1 - x0) + 1):
        f = i / (x1 - x0)
        pts.append((x0 + i, y0 + sag * 4 * f * (1 - f)))
    for k, (x, y) in enumerate(pts):
        for j in range(5):
            c = (212, 186, 118) if (k + j) % 4 < 2 else (164, 132, 72)
            ly.px(x, y + j, c)
        ly.px(x, y - 1, (96, 72, 36))
        ly.px(x, y + 5, (96, 72, 36))
    return pts


def shide(ly, x, y, n=3):
    # 紙垂 (かくかくの白い紙)
    for k in range(n):
        dx = 3 if k % 2 else 0
        ly.rect(x + dx, y + k * 5, 4, 5, PAPER)
        ly.rect(x + dx + 3, y + k * 5, 1, 5, (200, 192, 176))


def ema(ly, x, y, w, h, seed):
    # 絵馬: 屋根の形の板・白木
    roof = 12
    pts = [(x, y + roof), (x + w / 2, y), (x + w, y + roof), (x + w, y + h), (x, y + h)]
    ly.poly([(px + 2, py + 3) for px, py in pts], (0, 0, 0, 120))
    ly.poly(pts, (110, 78, 46))
    inner = [(x + 3, y + roof + 2), (x + w / 2, y + 4), (x + w - 3, y + roof + 2), (x + w - 3, y + h - 3), (x + 3, y + h - 3)]
    ly.poly(inner, (232, 206, 160))
    rnd = random.Random(seed)
    for k in range(9):
        gy = y + roof + 8 + k * (h - roof - 12) / 9 + rnd.uniform(-1, 1)
        x0 = x + 4 + rnd.randrange(0, 30)
        ly.line([(x0, gy), (x0 + rnd.randrange(30, w - 40), gy + rnd.choice([0, 0, 1]))], (214, 184, 136))
    ly.line([(x + 4, y + roof + 2), (x + w / 2, y + 5)], (248, 228, 186))
    # 屋根の小さい庇
    ly.line([(x - 2, y + roof + 1), (x + w / 2, y - 2), (x + w + 2, y + roof + 1)], (90, 60, 36), 2)
    # 紐の穴
    ly.rect(x + w / 2 - 1, y + 6, 3, 3, (60, 40, 24))
    return pts


def brush_ring(ly, cx, cy, r, c, seed):
    # 筆で描いた丸 (閉じきらない)
    rnd = random.Random(seed)
    a0 = rnd.uniform(0, 6)
    for i in range(int(r * 6.6)):
        a = a0 + i / (r * 6.6) * 2 * math.pi * 0.92
        rr = r + math.sin(a * 3) * 0.8
        w = 1 if i % 7 else 2
        ly.d.ellipse((cx + rr * math.cos(a) - w / 2, cy + rr * math.sin(a) - w / 2, cx + rr * math.cos(a) + w / 2, cy + rr * math.sin(a) + w / 2), fill=c)


def beam(ly):
    # 笠木: 少し傾いた太い梁、両端が反り上がる
    top = [(8, 30), (40, 34), (440, 32), (472, 26)]
    pts = [(4, 24), (40, 31), (440, 29), (476, 20), (476, 30), (440, 41), (40, 43), (4, 36)]
    ly.tpoly(pts, tex("stripped_dark_oak_log", 0.9), outline=(24, 14, 8))
    ly.line([(6, 25), (40, 32), (440, 30), (474, 21)], (120, 84, 56))
    # 貫 (下の細い梁)
    ly.tpoly([(60, 56), (420, 55), (420, 61), (60, 62)], tex("dark_oak_planks", 0.85), outline=(24, 14, 8))
    # 額束 (真ん中の札)
    ly.tpoly([(204, 36), (276, 36), (276, 64), (204, 64)], tex("dark_oak_planks", 0.7), outline=(20, 12, 6))
    ly.rect(207, 39, 66, 22, (30, 20, 12))
    ly.d.rectangle((207, 39, 272, 60), outline=(200, 160, 80))
    ly.d.rectangle((209, 41, 270, 58), outline=(120, 90, 40))


def job_D(cv, skills):
    t = texts(JOB)
    darken(cv, 60)
    ly = Layer()
    beam(ly)
    rp = rope(ly, 20, 460, 46, 10)
    # 吊るす位置
    xs = [62, 118, 174, 306, 362, 418]
    for i, ((j, _, w), x) in enumerate(zip(JOBS, xs)):
        ry = 46 + 10 * 4 * ((x - 20) / 440) * (1 - (x - 20) / 440)
        if i in (1, 4):
            shide(ly, (x + xs[i + 1 if i + 1 < 6 else i]) // 2, ry + 5)
        sel = j == JOB
        drop = 36 if sel else 18
        ly.line([(x, ry + 4), (x, ry + drop)], (60, 40, 20))
        if sel:
            # 赤い房
            ly.rect(x - 1, ry + drop - 2, 3, 3, RED)
            for k in range(5):
                ly.line([(x - 2 + k, ry + drop + 1), (x - 3 + k * 1.5, ry + drop + 9)], RED)
    ly.flush(cv)
    for i, ((j, _, w), x) in enumerate(zip(JOBS, xs)):
        ry = 46 + 10 * 4 * ((x - 20) / 440) * (1 - (x - 20) / 440)
        sel = j == JOB
        s = 32 if sel else 24
        drop = 36 if sel else 18
        im = item(w) if sel else _dim(item(w), 0.55)
        cv.blit(im, x - s / 2, ry + drop - 4, s, s)
        if not sel:
            cv.text(L("job.skyabyss.%s" % j), x - cv.font.width(L("job.skyabyss.%s" % j)) / 2, ry + drop + 22, (230, 220, 200))
    # 人 (真ん中、得物の下)
    put_figure(cv, figure(JOB), 240, 268, 150)
    ly = Layer()
    # 絵馬 (左、紐で梁から)
    ex, ey, ew, eh = 22, 112, 150, 132
    ly.line([(70, 43), (ex + ew / 2, ey + 7)], RED)
    ly.line([(71, 43), (ex + ew / 2 + 1, ey + 7)], (120, 24, 18))
    ema(ly, ex, ey, ew, eh, 1)
    # 鈴と鈴緒 (右)
    bx = 420
    for k in range(int(GH - 96)):
        y = 62 + k
        if y > 200:
            break
        ly.px(bx + (1 if (k // 3) % 2 else 0), y, RED if (k // 3) % 2 else PAPER)
        ly.px(bx + 1 + (0 if (k // 3) % 2 else 1), y, RED if (k // 3) % 2 else PAPER)
        ly.px(bx + 2, y, (120, 24, 18))
        ly.px(bx - 1, y, RED if (k // 3) % 2 == 0 else PAPER)
        ly.px(bx + 3, y, (90, 18, 12))
    ly.d.ellipse((bx - 10, 62, bx + 13, 84), fill=(214, 172, 70), outline=(110, 78, 24))
    ly.line([(bx - 6, 76), (bx + 9, 76)], (90, 60, 20))
    ly.d.ellipse((bx - 4, 68, bx + 1, 72), fill=(255, 236, 160))
    # 始める札 (鈴緒の先)
    pts = [(bx - 46, 200), (bx + 48, 200), (bx + 48, 218), (bx - 46, 218)]
    ly.tpoly(pts, tex("birch_planks", 1.0), outline=(110, 84, 54))
    ly.rect(bx - 46, 200, 94, 2, RED)
    ly.flush(cv)
    # 字
    cv.text(t["title"], 240 - cv.font.width(t["title"]) / 2, 46, (236, 200, 120))
    cv.text(t["lead"], 470 - cv.font.width(t["lead"]), 6, (236, 228, 210))
    tx = ex + 10
    y = ey + 18
    cv.text(t["name"], tx, y, RED, False)
    cv.text(t["sub"], tx + cv.font.width(t["name"]) + 6, y, (120, 92, 60), False)
    y += 13
    y = cv.para(t["role"], tx, y, ew - 20, INK, shadow=False)
    y += 4
    cv.text(t["weapon"], tx, y, INK, False)
    y += 13
    cv.text(t["first"], tx, y, INK, False)
    y += 15
    first = [s for s in skills.values() if s["tree"] == base.TREE_OF[JOB] and s["tier"] == 0]
    for i, s in enumerate(first):
        cv.blit(icon(s["icon"]), tx + i * 24, y, 18, 18)
    ly = Layer()
    brush_ring(ly, tx + 9, y + 9, 12, RED, 3)
    ly.flush(cv)
    cv.para(t["eff"], tx + 50, y + 1, ew - 66, INK, shadow=False, maxl=2)
    cv.text(t["start"], bx + 1 - cv.font.width(t["start"]) / 2, 205, INK, False)


# ===================== E 巨神の肋 =====================
BONE = (226, 214, 188)
BONE_D = (160, 140, 110)
BONE_L = (250, 244, 226)
FLESH = (40, 18, 22)


def bone_arc(ly, pts, r, seed):
    # 太さが波打つ骨 (節のふくらみ)
    def thick(i):
        return r + 2.5 * math.sin(i / 9.0) + (4 if i % 37 < 5 else 0)
    for i, (x, y) in enumerate(pts):
        rr = thick(i)
        ly.d.ellipse((x - rr - 1, y - rr - 1, x + rr + 1, y + rr + 1), fill=(40, 30, 24))
    for i, (x, y) in enumerate(pts):
        rr = thick(i)
        ly.d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=BONE_D)
    for i, (x, y) in enumerate(pts):
        rr = thick(i) - 3
        ly.d.ellipse((x - 1 - rr, y - 2 - rr, x - 1 + rr, y - 2 + rr), fill=BONE)
    for i, (x, y) in enumerate(pts):
        rr = max(1, thick(i) - 9)
        ly.d.ellipse((x - 2 - rr, y - 4 - rr, x - 2 + rr, y - 4 + rr), fill=BONE_L)
    rnd = random.Random(seed)
    for _ in range(len(pts)):
        x, y = rnd.choice(pts)
        ly.px(x + rnd.randint(-r + 3, r - 3), y + rnd.randint(-2, r - 3), (196, 180, 150))
    for _ in range(len(pts) // 3):
        x, y = rnd.choice(pts)
        ly.line([(x, y), (x + rnd.randint(-4, 4), y + rnd.randint(2, 6))], BONE_D)


def curve(p0, p1, p2, n=80):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in [i / n for i in range(n + 1)]]


def job_E(cv, skills):
    t = texts(JOB)
    darken(cv, 90)
    ly = Layer()
    # 奥の肋 (暗い)
    for k, (a, b, c) in enumerate([((300, 290), (330, 60), (500, 40)), ((380, 290), (410, 120), (500, 110))]):
        pts = curve(a, b, c)
        ly.blob(pts, 9, (70, 58, 50))
        ly.blob([(x - 1, y - 2) for x, y in pts], 6, (96, 82, 70))
    ly.flush(cv)
    put_figure(cv, figure(JOB), 352, 262, 140)
    ly = Layer()
    big = curve((-30, 300), (40, -10), (500, 34), 140)
    bone_arc(ly, big, 15, 2)
    # 背骨の節 (右上から入る)
    for i in range(5):
        cx, cy = 470 - i * 2, 6 + i * 18
    ly.flush(cv)
    # 得物を骨に結ぶ
    ts = [0.30, 0.38, 0.46, 0.56, 0.66, 0.76]
    for i, ((j, _, w), f) in enumerate(zip(JOBS, ts)):
        x, y = big[int(f * (len(big) - 1))]
        sel = j == JOB
        ly = Layer()
        L2 = 34 if sel else 20
        ly.line([(x, y + 10), (x + 2, y + 10 + L2)], (70, 40, 30))
        ly.line([(x - 3, y + 6), (x + 4, y + 12)], (120, 40, 36), 2)  # 結び目
        if sel:
            ly.poly(rot(x + 2, y + 14 + L2 + 26, 40, 12, 6), (120, 40, 36))
        ly.flush(cv)
        s = 36 if sel else 26
        im = item(w) if sel else _dim(item(w), 0.6)
        cv.blit(im, x + 2 - s / 2, y + 8 + L2, s, s)
        if sel:
            cv.text(t["name"], x + 2 - cv.font.width(t["name"]) / 2 + 1, y + L2 + 37, BONE_L)
    # 肩甲骨の板 (左下、画面の外へはみ出す)
    ly = Layer()
    sh = [(-6, 120), (40, 104), (150, 112), (196, 140), (182, 230), (120, 268), (-6, 276)]
    sh = jag(sh, 6, 1.2, 5)
    ly.poly([(x + 2, y + 3) for x, y in sh], (20, 10, 10, 160))
    ly.poly(sh, BONE_D)
    ly.poly([(x + 1.5, y + 1.5) for x, y in jag([(-6, 124), (40, 108), (148, 116), (190, 142), (178, 226), (118, 262), (-6, 270)], 6, 1.0, 6)], BONE)
    # 背の稜線と、穴の点
    ly.blob([(-6, 132), (60, 118), (150, 122), (186, 144)], 2, BONE_L)
    rnd = random.Random(12)
    for _ in range(60):
        ly.px(rnd.randrange(4, 176), rnd.randrange(150, 260), (204, 190, 162))
    ly.line(sh + [sh[0]], (60, 46, 36))
    ly.flush(cv)
    tx, y = 16, 126
    cv.text(t["title"], tx, 108 - 14, BONE_L)
    cv.text(t["sub"], tx, y, (120, 40, 36), False)
    y += 13
    y = cv.para(t["role"], tx, y, 160, (52, 40, 30), shadow=False)
    y += 3
    cv.text(t["weapon"], tx, y, (52, 40, 30), False)
    y += 13
    cv.text(t["first"], tx, y, (52, 40, 30), False)
    y += 14
    first = [s for s in skills.values() if s["tree"] == base.TREE_OF[JOB] and s["tier"] == 0]
    for i, s in enumerate(first):
        cv.blit(icon(s["icon"]), tx + i * 22, y, 18, 18)
    cv.para(t["eff"], tx + 48, y + 1, 120, (52, 40, 30), shadow=False, maxl=2)
    # 始める: 椎骨の塊 (右下)
    ly = Layer()
    vx, vy = 404, 222
    ly.d.ellipse((vx - 46, vy - 10, vx + 46, vy + 18), fill=BONE_D, outline=(40, 30, 24))
    ly.d.ellipse((vx - 44, vy - 12, vx + 44, vy + 14), fill=BONE, outline=(40, 30, 24))
    ly.poly([(vx - 6, vy - 12), (vx, vy - 24), (vx + 6, vy - 12)], BONE, outline=(40, 30, 24))
    ly.flush(cv)
    cv.text(t["start"], vx - cv.font.width(t["start"]) / 2, vy - 3, (90, 30, 26), False)
    cv.text(t["lead"], 470 - cv.font.width(t["lead"]), 254, (220, 206, 186))


# ===================== F 切り絵 =====================
BLACK = (20, 14, 20)
CREAM = (238, 228, 206)
PINK = (232, 116, 150)
PINK_D = (146, 46, 82)


def diamond(ly, x, y, r, c, hi=None):
    ly.poly([(x, y - r), (x + r, y), (x, y + r), (x - r, y)], c)
    if hi:
        ly.poly([(x, y - r), (x + r, y), (x, y)], hi)


def job_F(cv, skills):
    t = texts(JOB)
    ly = Layer()
    # 一番奥: 黒い紙 (端ははさみで切った形、角から景色が見える)
    back = jag([(14, 10), (470, 4), (476, 262), (6, 266)], 7, 2.5, 1)
    ly.poly(back, BLACK)
    # 桃色の山 (下)
    hill = [(0, 270), (0, 214)] + [(x, 214 - 26 * abs(math.sin(x / 37.0)) - (x % 23) / 4) for x in range(0, 481, 8)] + [(480, 270)]
    ly.poly(jag(hill, 5, 1.6, 2, closed=False) + [(480, 270)], PINK_D)
    # 生成りの破れた帯 (左上から斜め)
    strip = jag([(-4, 22), (236, 8), (246, 128), (-4, 142)], 6, 2.2, 3)
    ly.poly([(x + 3, y + 3) for x, y in strip], (0, 0, 0, 255))
    ly.poly(strip, CREAM)
    rnd = random.Random(6)
    for _ in range(14):
        diamond(ly, rnd.randrange(250, 470), rnd.randrange(14, 200), rnd.choice([2, 3, 4, 6]), PINK, (255, 170, 196))
    ly.flush(cv)
    # 人: 黒の切り抜き + 桃色の縁 (大きく、右に、下ははみ出す)
    fig = figure(JOB)
    put_figure(cv, silhouette(fig, (0, 0, 0, 255), (0, 0, 0, 255), 14), 356, 268, 196)
    stk = silhouette(fig, (0, 0, 0, 0), CREAM + (255,), 12)
    put_figure(cv, stk, 350, 262, 196)
    put_figure(cv, fig, 350, 262 - 0.0, 196)
    # 札: 六枚の紙の札を扇に
    ly = Layer()
    cards = []
    for i, (j, _, w) in enumerate(JOBS):
        a = -24 + i * 10
        cx = 64 + i * 28
        cy = 214 + abs(i - 2.5) * 6
        sel = j == JOB
        if sel:
            cy -= 22
        pts = rot(cx, cy, 30, 42, a)
        ly.poly([(x + 2, y + 2) for x, y in pts], (0, 0, 0, 255))
        ly.poly(jag(pts, 5, 0.8, 10 + i), PINK if sel else CREAM)
        cards.append((cx, cy, w, sel, j))
    ly.flush(cv)
    for cx, cy, w, sel, j in cards:
        cv.blit(flat_item(w, BLACK + (255,)), cx - 9, cy - 13, 18, 18)
        if sel:
            nm = L("job.skyabyss.%s" % j)
            cv.text(nm, cx - cv.font.width(nm) / 2, cy + 6, BLACK, False)
    # 字 (帯の上)
    tx, y = 14, 24
    cv.text(t["title"], tx, y, PINK_D, False)
    y += 15
    cv.text(t["name"], tx, y, BLACK, False)
    cv.text(t["sub"], tx + cv.font.width(t["name"]) + 6, y, PINK_D, False)
    y += 13
    y = cv.para(t["role"], tx, y, 220, BLACK, shadow=False)
    cv.text(t["weapon"], tx, y, BLACK, False)
    y += 13
    first = [s for s in skills.values() if s["tree"] == base.TREE_OF[JOB] and s["tier"] == 0]
    for i, s in enumerate(first):
        cv.blit(flat_item("shrine_bow", BLACK + (0,)) if False else icon(s["icon"]), tx + i * 22, y, 18, 18)
    cv.para(t["first"] + "。" + t["eff"], tx + 48, y + 1, 180, BLACK, shadow=False, maxl=2)
    # 始める: 桃色の切れ端に黒い菱
    ly = Layer()
    tag = jag(rot(410, 236, 112, 20, -4), 5, 1.2, 9)
    ly.poly([(x + 2, y + 2) for x, y in tag], (0, 0, 0, 255))
    ly.poly(tag, PINK)
    diamond(ly, 360, 238, 4, BLACK)
    ly.flush(cv)
    cv.text(t["start"], 370, 232, BLACK, False)
    cv.text(t["lead"], 236 - cv.font.width(t["lead"]), 116, PINK_D, False)


def main():
    global ARGS
    ap = argparse.ArgumentParser()
    ap.add_argument("--font", required=True)
    ap.add_argument("--tex", required=True)
    ap.add_argument("--only", default="")
    ARGS = ap.parse_args()
    font = Font(ARGS.font)
    skills = load_skills()
    bg = Image.open(os.path.join(SHOTS, "bg.png")).convert("RGBA").filter(ImageFilter.GaussianBlur(10))
    for name, fn in (("D_job", job_D), ("E_job", job_E), ("F_job", job_F)) + tuple(EXTRA):
        if ARGS.only and ARGS.only not in name:
            continue
        cv = Canvas(bg.copy(), font)
        fn(cv, skills)
        cv.im.convert("RGB").save(os.path.join(OUT, name + ".png"))
    print("ok")


# ===================== スキルの木 (3 案で同じ中身) =====================
def graph(skills):
    tree = base.TREE_OF[JOB]
    sk = [x for x in skills.values() if x["tree"] == tree]
    nodes, edges = {}, []
    root = (3, 0)
    nodes[root] = dict(kind="root", state="taken")
    at = {}
    for x in sk:
        p = (1 + x["col"] * 2, 2 + x["tier"] * 2)
        at[x["id"]] = p
        st = "taken" if x["id"] in base.TAKEN else ("open" if all(q in base.TAKEN for q in x["pre"]) else "locked")
        nodes[p] = dict(kind=x["kind"], id=x["id"], icon=x["icon"], state=st)

    def path(a, b, lit, opened):
        pts = [a]
        c, r = a
        while r < b[1] - 1:
            r += 1
            pts.append((c, r))
        while c != b[0]:
            c += 1 if b[0] > c else -1
            pts.append((c, r))
        pts.append(b)
        for i, (u, v) in enumerate(zip(pts, pts[1:])):
            edges.append((u, v, lit))
            if v != b and v not in nodes:
                nodes[v] = dict(kind="minor", state="taken" if lit else ("open" if opened and i == 0 else "locked"))
    for x in sk:
        b = at[x["id"]]
        pres = [at[q] for q in x["pre"] if q in at] or [root]
        for a in pres:
            pid = nodes[a].get("id")
            lit = x["id"] in base.TAKEN and (pid in base.TAKEN or a == root)
            opened = pid in base.TAKEN or a == root
            path(a, b, lit, opened)
    # 盤の続き (下へ。見えない所まで続く)
    ks = at.get("still_string", (5, 8))
    for end in ((ks[0] - 2, ks[1] + 3), (ks[0], ks[1] + 3)):
        nodes.setdefault(end, dict(kind="ACTIVE", icon=0, state="locked", unknown=True))
        path(ks, end, False, False)
    return nodes, edges


def info_texts(skills):
    import re
    s = skills[base.SEL_SKILL]
    eff = L("skill.skyabyss.%s.effect" % base.SEL_SKILL, "4.5", "3")
    eff = re.sub(r"（[^）]*）", "", eff)
    eff = "。".join(eff.split("。")[:2]) + "。"
    return dict(name=L("skill.skyabyss.%s" % base.SEL_SKILL), kind=L("gui.skyabyss.skill.kind_active") + "・段 1 / 3",
                cost="マナ 12  再使用まで 3.9 秒", flavor=L("skill.skyabyss.%s.flavor" % base.SEL_SKILL), eff=eff,
                icon=s["icon"], tree="%s（%s）" % (L("job.skyabyss.%s" % JOB), L("skilltree.skyabyss.%s" % base.TREE_OF[JOB].lower())))


def clip(ly, x, y, w, h, shape=None):
    from PIL import ImageChops
    m = Image.new("L", (GW, GH), 0)
    if shape:
        ImageDraw.Draw(m).polygon([(int(a), int(b)) for a, b in shape], fill=255)
    else:
        ImageDraw.Draw(m).rectangle((x, y, x + w - 1, y + h - 1), fill=255)
    ly.im.putalpha(ImageChops.multiply(ly.im.getchannel("A"), m))


def draw_info(cv, it, x, y, w, ink, head, acc, sh=False):
    cv.blit(icon(it["icon"]), x, y, 20, 20)
    cv.text(it["name"], x + 25, y + 1, head, sh)
    cv.text(it["kind"], x + 25, y + 12, ink, sh)
    y += 26
    cv.text(it["cost"], x, y, ink, sh)
    y += 15
    y = cv.para(it["flavor"], x, y, w, acc, shadow=sh, maxl=2)
    y = cv.para(it["eff"], x, y + 3, w, ink, shadow=sh, maxl=4)
    return y


def buttons_row(cv, x, y, col, hi):
    for s_ in ("1", "2", "保存", "振り直す", "試着"):
        cv.text(s_, x, y, hi if s_ == "1" else col)
        x += cv.font.width(s_) + 10


def tree_D(cv, skills):
    darken(cv, 60)
    ly = Layer()
    beam(ly)
    rope(ly, 20, 460, 46, 10)
    tabs = [("記録", 60), ("スキル", 104), ("旅", 156)]
    for i, (s_, x) in enumerate(tabs):
        h = 22 if i == 1 else 12
        ly.line([(x + 12, 50), (x + 12, 50 + h)], RED if i == 1 else (90, 70, 40))
    # 絵馬掛け: 柱と横木
    bx0, by0, bx1, by1 = 16, 70, 300, 238
    for x in (bx0, bx1 - 6):
        ly.tpoly([(x, 62), (x + 6, 62), (x + 6, by1 + 6), (x, by1 + 6)], tex("dark_oak_planks", 0.8), outline=(20, 12, 6))
    for ry in (86, 122, 158, 194, 230):
        ly.tpoly([(bx0 + 6, ry), (bx1 - 6, ry), (bx1 - 6, ry + 3), (bx0 + 6, ry + 3)], tex("dark_oak_planks", 0.9))
        ly.line([(bx0 + 6, ry + 3), (bx1 - 6, ry + 3)], (24, 14, 8))
    ly.flush(cv)
    nodes, edges = graph(skills)
    ox, oy, dx, dy = 40, 78, 36, 18

    def P(c, r):
        return ox + c * dx, oy + r * dy
    ly = Layer()
    for a, b, lit in edges:
        (x0, y0), (x1, y1) = P(*a), P(*b)
        ly.line([(x0, y0), (x1, y1)], RED if lit else (196, 170, 120), 2 if lit else 1)
    for (c, r), n in nodes.items():
        x, y = P(c, r)
        if n["kind"] in ("minor", "root"):
            col = RED if n["state"] == "taken" else ((230, 210, 170) if n["state"] == "open" else (120, 96, 64))
            ly.rect(x - 2, y - 2, 5, 5, (30, 18, 10))
            ly.rect(x - 1, y - 1, 3, 3, col)
        else:
            w = h = 24
            pts = [(x - w / 2, y - h / 2 + 5), (x, y - h / 2), (x + w / 2, y - h / 2 + 5), (x + w / 2, y + h / 2), (x - w / 2, y + h / 2)]
            ly.poly([(a + 1, b + 2) for a, b in pts], (0, 0, 0, 120))
            ly.poly(pts, (110, 78, 46))
            ly.poly([(x - w / 2 + 2, y - h / 2 + 6), (x, y - h / 2 + 2), (x + w / 2 - 2, y - h / 2 + 6), (x + w / 2 - 2, y + h / 2 - 2), (x - w / 2 + 2, y + h / 2 - 2)],
                    (232, 206, 160) if n["state"] != "locked" else (150, 126, 92))
    clip(ly, bx0 + 6, 64, bx1 - bx0 - 12, by1 - 62)
    ly.flush(cv)
    for (c, r), n in nodes.items():
        x, y = P(c, r)
        if n["kind"] not in ("minor", "root") and y < by1 - 8 and not n.get("unknown"):
            im = icon(n["icon"]) if n["state"] != "locked" else _dim(icon(n["icon"]), 0.45)
            cv.blit(im, x - 8, y - 6, 16, 16)
        if n.get("id") == base.SEL_SKILL:
            lr = Layer()
            brush_ring(lr, x, y + 2, 15, RED, 4)
            lr.flush(cv)
    it = info_texts(skills)
    ly = Layer()
    ex, ey, ew, eh = 312, 84, 156, 150
    ly.line([(390, 44), (ex + ew / 2, ey + 7)], RED)
    ema(ly, ex, ey, ew, eh, 2)
    ly.tpoly([(8, 244), (472, 244), (472, 250), (8, 250)], tex("dark_oak_planks", 0.9), outline=(20, 12, 6))
    for i in range(4):
        x = 92 + i * 30
        ly.poly([(x, 226), (x + 12, 220), (x + 24, 226), (x + 24, 244), (x, 244)], (110, 78, 46))
        ly.poly([(x + 2, 227), (x + 12, 222), (x + 22, 227), (x + 22, 242), (x + 2, 242)], (232, 206, 160) if i == 0 else (170, 146, 108))
    for i, (s_, x) in enumerate(tabs):
        h = 22 if i == 1 else 12
        ww = cv.font.width(s_) + 8
        ly.rect(x + 12 - ww / 2, 50 + h, ww, 13, (110, 78, 46))
        ly.rect(x + 13 - ww / 2, 51 + h, ww - 2, 11, (232, 206, 160) if i == 1 else (170, 146, 108))
    ly.flush(cv)
    for i, (s_, x) in enumerate(tabs):
        h = 22 if i == 1 else 12
        ww = cv.font.width(s_) + 8
        cv.text(s_, x + 12 - ww / 2 + 4, 53 + h, RED if i == 1 else INK, False)
    cv.text(it["tree"], 196, 66, (236, 220, 190))
    cv.text("ツリー", 316, 66, (236, 200, 120))
    cv.text("名残", 352, 66, (200, 190, 170))
    draw_info(cv, it, ex + 10, ey + 18, ew - 20, INK, RED, (60, 90, 130))
    cv.text("取得済み", ex + ew / 2 - cv.font.width("取得済み") / 2, ey + eh - 14, (140, 110, 80), False)
    cv.text("SP 0", 14, 222, (236, 220, 190))
    cv.text("マナ 20/20", 14, 233, (160, 200, 240))
    cv.blit(icon(it["icon"]), 96, 226, 16, 16)
    for i, k in enumerate("RGVZ"):
        cv.text(k, 92 + i * 30 + 16, 234, INK, False)
    buttons_row(cv, 230, 254, (236, 220, 190), (236, 160, 120))


def tree_E(cv, skills):
    darken(cv, 110)
    cv.im.alpha_composite(Image.new("RGBA", cv.im.size, (60, 14, 20, 90)))
    nodes, edges = graph(skills)
    ox, oy, dx, dy = 40, 26, 36, 22

    def P(c, r):
        return ox + c * dx, oy + r * dy
    sx = P(3, 0)[0]
    ly = Layer()
    for r in range(1, 12):
        y = oy + r * dy
        for side in (-1, 1):
            pts = curve((sx, y), (sx + side * 120, y - 14), (sx + side * 200, y + 30), 60)
            ly.blob(pts, 2, (120, 100, 84))
            ly.blob([(a - 1, b - 1) for a, b in pts], 1, (170, 150, 124))
    for k in range(-1, 14):
        y = 10 + k * 20
        ly.d.ellipse((sx - 13, y - 6, sx + 13, y + 7), fill=(40, 30, 24))
        ly.d.ellipse((sx - 12, y - 6, sx + 12, y + 5), fill=BONE_D)
        ly.d.ellipse((sx - 10, y - 6, sx + 10, y + 3), fill=BONE)
        ly.rect(sx - 2, y + 5, 5, 6, (120, 100, 84))
    clip(ly, 0, 0, 300, 236)
    ly.flush(cv)
    ly = Layer()
    for a, b, lit in edges:
        (x0, y0), (x1, y1) = P(*a), P(*b)
        if lit:
            ly.line([(x0, y0), (x1, y1)], (200, 50, 50), 3)
            ly.line([(x0, y0), (x1, y1)], (250, 120, 90), 1)
        else:
            ly.line([(x0, y0), (x1, y1)], (90, 70, 60), 1)
    for (c, r), n in nodes.items():
        x, y = P(c, r)
        if n["kind"] in ("minor", "root"):
            ly.d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(40, 30, 24))
            ly.d.ellipse((x - 2, y - 3, x + 2, y + 1), fill=BONE if n["state"] != "locked" else (120, 104, 88))
            if n["state"] == "taken":
                ly.rect(x - 1, y - 1, 2, 2, (230, 70, 50))
        else:
            ly.d.ellipse((x - 13, y - 13, x + 13, y + 13), fill=(40, 30, 24))
            ly.d.ellipse((x - 12, y - 12, x + 12, y + 12), fill=BONE_D if n["state"] != "locked" else (90, 76, 64))
            ly.d.ellipse((x - 11, y - 12, x + 10, y + 9), fill=BONE if n["state"] != "locked" else (120, 104, 88))
            if n["state"] == "taken":
                ly.d.ellipse((x - 10, y - 10, x + 10, y + 10), outline=(230, 70, 50), width=2)
    clip(ly, 0, 0, 300, 236)
    ly.flush(cv)
    for (c, r), n in nodes.items():
        x, y = P(c, r)
        if n["kind"] not in ("minor", "root") and y < 226 and not n.get("unknown"):
            im = icon(n["icon"]) if n["state"] != "locked" else _dim(icon(n["icon"]), 0.4)
            cv.blit(im, x - 8, y - 8, 16, 16)
    it = info_texts(skills)
    ly = Layer()
    sh = jag([(300, 30), (360, 18), (486, 24), (486, 236), (420, 230), (322, 206), (296, 120)], 6, 1.0, 8)
    ly.poly([(x + 2, y + 3) for x, y in sh], (10, 4, 6, 180))
    ly.poly(sh, BONE_D)
    ly.poly([(x + 1.5, y + 1.5) for x, y in jag([(304, 34), (360, 22), (486, 28), (486, 232), (420, 226), (326, 202), (300, 120)], 6, 1.0, 9)], BONE)
    ly.blob([(306, 50), (380, 30), (486, 34)], 2, BONE_L)
    rnd = random.Random(3)
    for _ in range(50):
        ly.px(rnd.randrange(320, 476), rnd.randrange(150, 220), (204, 190, 162))
    ly.line(sh + [sh[0]], (60, 46, 36))
    for i in range(4):
        x = 70 + i * 30
        ly.d.ellipse((x - 1, 242, x + 25, 268), fill=(40, 30, 24))
        ly.d.ellipse((x, 242, x + 24, 266), fill=BONE if i == 0 else BONE_D)
        ly.d.ellipse((x + 4, 246, x + 20, 262), fill=(60, 40, 34))
    ly.flush(cv)
    draw_info(cv, it, 326, 46, 146, (52, 40, 30), (120, 40, 36), (90, 60, 50))
    cv.text("取得済み", 360, 214, (120, 100, 80), False)
    cv.text(it["tree"], 8, 6, BONE_L)
    for i, s_ in enumerate(["記録", "スキル", "旅"]):
        cv.text(s_, 312 + i * 40, 4, (250, 120, 90) if i == 1 else (200, 186, 166))
    cv.text("ツリー", 420, 244, BONE_L)
    cv.text("名残", 452, 244, (160, 146, 126))
    cv.blit(icon(it["icon"]), 74, 246, 16, 16)
    for i, k in enumerate("RGVZ"):
        cv.text(k, 70 + i * 30 + 18, 258, BONE_L)
    cv.text("SP 0", 8, 244, BONE_L)
    cv.text("マナ 20/20", 8, 256, (160, 200, 240))
    buttons_row(cv, 196, 254, BONE_L, (250, 120, 90))


def tree_F(cv, skills):
    ly = Layer()
    ly.poly(jag([(10, 14), (470, 6), (476, 264), (4, 266)], 7, 2.5, 21), BLACK)
    hill = [(0, 270), (0, 240)] + [(x, 240 - 14 * abs(math.sin(x / 29.0))) for x in range(0, 481, 8)] + [(480, 270)]
    ly.poly(jag(hill, 5, 1.6, 22, closed=False) + [(480, 270)], PINK_D)
    rnd = random.Random(16)
    for _ in range(10):
        diamond(ly, rnd.randrange(20, 290), rnd.randrange(20, 230), rnd.choice([1, 2, 3]), (90, 40, 60))
    ly.flush(cv)
    nodes, edges = graph(skills)
    ox, oy, dx, dy = 30, 36, 38, 21

    def P(c, r):
        return ox + c * dx, oy + r * dy
    ly = Layer()
    for a, b, lit in edges:
        (x0, y0), (x1, y1) = P(*a), P(*b)
        if lit:
            ly.line([(x0, y0), (x1, y1)], PINK, 1)
        else:
            n = int(max(abs(x1 - x0), abs(y1 - y0)))
            for i in range(0, n, 3):
                ly.px(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, (150, 136, 120))
    for (c, r), n in nodes.items():
        x, y = P(c, r)
        col = PINK if n["state"] == "taken" else (CREAM if n["state"] == "open" else (90, 80, 76))
        if n["kind"] in ("minor", "root"):
            diamond(ly, x, y, 3, col)
        else:
            circ = jag([(x + 12 * math.cos(a), y + 12 * math.sin(a)) for a in [i * math.pi / 8 for i in range(16)]], 4, 0.7, c * 7 + r)
            ly.poly([(a + 2, b + 2) for a, b in circ], (0, 0, 0, 255))
            ly.poly(circ, col)
    clip(ly, 0, 0, 0, 0, shape=jag([(10, 28), (300, 22), (290, 236), (10, 240)], 8, 3, 30))
    ly.flush(cv)
    for (c, r), n in nodes.items():
        x, y = P(c, r)
        if n["kind"] not in ("minor", "root") and y < 226 and x < 286 and not n.get("unknown"):
            im = icon(n["icon"]) if n["state"] != "locked" else _dim(icon(n["icon"]), 0.4)
            cv.blit(im, x - 8, y - 8, 16, 16)
    it = info_texts(skills)
    ly = Layer()
    strip = jag(rot(388, 128, 168, 196, 3), 6, 2.0, 31)
    ly.poly([(x + 3, y + 3) for x, y in strip], (0, 0, 0, 255))
    ly.poly(strip, CREAM)
    diamond(ly, 384, 34, 5, PINK, (255, 170, 196))
    tabs = [("記録", 16), ("スキル", 54), ("旅", 104)]
    for i, (s_, x) in enumerate(tabs):
        tab = jag(rot(x + 18, 12, 38, 16, -4 + i * 3), 4, 0.8, 40 + i)
        ly.poly([(a + 2, b + 2) for a, b in tab], (0, 0, 0, 255))
        ly.poly(tab, PINK if i == 1 else CREAM)
    for i in range(4):
        pts = rot(90 + i * 28, 252, 22, 26, -8 + i * 5)
        ly.poly([(a + 2, b + 2) for a, b in pts], (0, 0, 0, 255))
        ly.poly(jag(pts, 4, 0.6, 50 + i), PINK if i == 0 else CREAM)
    ly.flush(cv)
    for i, (s_, x) in enumerate(tabs):
        cv.text(s_, x + 18 - cv.font.width(s_) / 2, 8, BLACK, False)
    draw_info(cv, it, 314, 46, 146, BLACK, PINK_D, PINK_D)
    cv.text("取得済み", 350, 212, (120, 100, 100), False)
    cv.text(it["tree"], 152, 8, CREAM)
    cv.text("ツリー", 404, 8, PINK)
    cv.text("名残", 440, 8, (150, 136, 120))
    cv.blit(icon(it["icon"]), 82, 243, 16, 16)
    for i, k in enumerate("RGVZ"):
        cv.text(k, 90 + i * 28 + 4, 254, BLACK, False)
    cv.text("SP 0", 14, 244, CREAM)
    cv.text("マナ 20/20", 14, 255, CREAM)
    buttons_row(cv, 210, 254, CREAM, PINK)


EXTRA = [("D_skill", tree_D), ("E_skill", tree_E), ("F_skill", tree_F)]

if __name__ == "__main__":
    main()
