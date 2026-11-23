# UI の作り直しのラフ (11/23)。ジョブ選び・スキルの木の 2 画面を、部品一式 3 案 (A 天の書 / B 淵の石板 / C 夜空の展示) で描く。
# ゲームと同じ大きさ (1920x1080・GUI 倍率 4・板 L 356x220) で、字はマイクラの字 (ascii.png と unifont) をそのまま使う。
# 素材: run/shots/ui_rough_1123/ (tools/shots/ui_rough_1123.txt で撮る)。字は引数 --font <unifont の .hex と ascii.png のあるフォルダ>
# 出力: docs/review/ui_rough_1123/<案>_<画面>.png と sheet.png
import argparse
import json
import math
import os
import random
import re

from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skyabyss")
SHOTS = os.path.join(ROOT, "run/shots/ui_rough_1123/shots/ui_rough_1123")
OUT = os.path.join(ROOT, "docs/review/ui_rough_1123")
G = 4  # GUI 倍率
SW, SH = 1920, 1080
PW, PH = 356, 220
PX, PY = (SW // G - PW) // 2, (SH // G - PH) // 2

LANG = json.load(open(os.path.join(ASSETS, "lang/ja_jp.json"), encoding="utf-8"))


def L(k, *a):
    s = LANG.get(k, k)
    for i, v in enumerate(a):
        s = s.replace("%%%d$s" % (i + 1), str(v)).replace("%s", str(v), 1)
    return s


# ---------- 字 ----------
class Font:
    def __init__(self, d):
        self.uni = {}
        hexf = [f for f in os.listdir(d) if f.endswith(".hex")][0]
        with open(os.path.join(d, hexf)) as f:
            for line in f:
                cp, h = line.strip().split(":")
                self.uni[int(cp, 16)] = h
        self.asc = Image.open(os.path.join(d, "ascii.png")).convert("RGBA")
        self.cache = {}

    # 半画素 (GUI の 0.5) の点の列と送り幅 (半画素)
    def glyph(self, ch):
        if ch in self.cache:
            return self.cache[ch]
        cp = ord(ch)
        pts = []
        if ch == " ":
            r = ([], 8)
        elif cp < 127:
            cx, cy = (cp % 16) * 8, (cp // 16) * 8
            w = 0
            for y in range(8):
                for x in range(8):
                    if self.asc.getpixel((cx + x, cy + y))[3] > 0:
                        w = max(w, x + 1)
                        for dy in range(2):
                            for dx in range(2):
                                pts.append((x * 2 + dx, y * 2 + dy))
            r = (pts, (w + 1) * 2)
        else:
            h = self.uni.get(cp, self.uni[0x25A1])
            bw = len(h) * 4 // 16
            xs = []
            for y in range(16):
                row = int(h[y * bw // 4:(y + 1) * bw // 4], 16)
                for x in range(bw):
                    if row >> (bw - 1 - x) & 1:
                        pts.append((x, y))
                        xs.append(x)
            if xs:
                lo, hi = min(xs), max(xs)
                pts = [(x - lo, y) for x, y in pts]
                r = (pts, hi - lo + 1 + 2)
            else:
                r = ([], 8)
        self.cache[ch] = r
        return r

    def width(self, s):
        return sum(self.glyph(c)[1] for c in s) / 2.0


# ---------- 描く道具 ----------
class Canvas:
    def __init__(self, bg, font):
        self.im = bg.convert("RGBA")
        self.font = font
        self.d = ImageDraw.Draw(self.im)

    def fill(self, x, y, w, h, c):
        if w <= 0 or h <= 0:
            return
        box = (int(round(x * G)), int(round(y * G)), int(round((x + w) * G)) - 1, int(round((y + h) * G)) - 1)
        if len(c) == 3 or c[3] == 255:
            self.d.rectangle(box, fill=c[:3])
        else:
            patch = Image.new("RGBA", (box[2] - box[0] + 1, box[3] - box[1] + 1), c)
            self.im.alpha_composite(patch, (box[0], box[1]))

    def px(self, x, y, c):
        self.fill(x, y, 1, 1, c)

    def border(self, x, y, w, h, c, t=1):
        self.fill(x, y, w, t, c)
        self.fill(x, y + h - t, w, t, c)
        self.fill(x, y + t, t, h - 2 * t, c)
        self.fill(x + w - t, y + t, t, h - 2 * t, c)

    def vgrad(self, x, y, w, h, c0, c1):
        for i in range(h):
            t = i / max(1, h - 1)
            self.fill(x, y + i, w, 1, tuple(int(c0[k] + (c1[k] - c0[k]) * t) for k in range(len(c0))))

    def hgrad(self, x, y, w, h, c0, c1):
        for i in range(w):
            t = i / max(1, w - 1)
            self.fill(x + i, y, 1, h, tuple(int(c0[k] + (c1[k] - c0[k]) * t) for k in range(len(c0))))

    def line(self, x0, y0, x1, y1, c, t=1, dash=0):
        n = int(max(abs(x1 - x0), abs(y1 - y0)))
        for i in range(n + 1):
            if dash and (i // dash) % 2:
                continue
            f = i / max(1, n)
            self.fill(round(x0 + (x1 - x0) * f) - t // 2, round(y0 + (y1 - y0) * f) - t // 2, t, t, c)

    def disc(self, cx, cy, r, c):
        for dy in range(-int(r), int(r) + 1):
            hw = int(math.sqrt(max(0, r * r - dy * dy)))
            self.fill(cx - hw, cy + dy, 2 * hw, 1, c)

    def ring(self, cx, cy, r, c, dash=0):
        n = int(r * 7)
        for i in range(n):
            if dash and (i // dash) % 2:
                continue
            a = 2 * math.pi * i / n
            self.px(round(cx + r * math.cos(a)), round(cy + r * math.sin(a)), c)

    def blit(self, img, x, y, w, h, smooth=False):
        im = img.convert("RGBA").resize((int(w * G), int(h * G)), Image.LANCZOS if smooth else Image.NEAREST)
        self.im.alpha_composite(im, (int(x * G), int(y * G)))

    def text(self, s, x, y, c, shadow=True, sc=None):
        hx, hy = int(round(x * 2)), int(round(y * 2))
        for ch in s:
            pts, adv = self.font.glyph(ch)
            for layer in ([1] if not shadow else [0, 1]):
                col = c if layer else (sc or tuple(int(v * 0.25) for v in c[:3]))
                off = 0 if layer else 2
                for (gx, gy) in pts:
                    X = (hx + gx + off) * 2
                    Y = (hy + gy + off) * 2
                    self.d.rectangle((X, Y, X + 1, Y + 1), fill=col[:3])
            hx += adv
        return x + self.font.width(s)

    def wrap(self, s, w):
        out, cur = [], ""
        for ch in s:
            if self.font.width(cur + ch) > w and cur:
                if ch in "、。）」』，．・…":
                    cur += ch
                    continue
                out.append(cur)
                cur = ch
            else:
                cur += ch
        if cur:
            out.append(cur)
        return out

    def para(self, s, x, y, w, c, lh=13, maxl=99, shadow=True):
        for i, ln in enumerate(self.wrap(s, w)[:maxl]):
            self.text(ln, x, y + i * lh, c, shadow)
        return y + min(maxl, len(self.wrap(s, w))) * lh


# ---------- データ (スキルとジョブ) ----------
def load_skills():
    src = open(os.path.join(ROOT, "src/main/java/jp/revati/skyabyss/rpg/skill/Skill.java"), encoding="utf-8").read()
    body = src[src.index("enum Skill"):]
    body = body[:body.index(";\n")]
    out = []
    for m in re.finditer(r'^\s{4}([A-Z_0-9]+)\("([a-z_0-9]+)",\s*SkillTree\.([A-Z]+),\s*Kind\.([A-Z]+),\s*(\d+),\s*(\d+),\s*(\d+)(.*?)\)[,;]?\s*$', body, re.M):
        pre = re.findall(r'"([a-z_0-9]+)"', m.group(8))
        out.append(dict(id=m.group(2), tree=m.group(3), kind=m.group(4), max=int(m.group(5)), tier=int(m.group(6)), col=int(m.group(7)), pre=pre))
    names = [s["id"] for s in out]
    tb = names.index("thunder_bolt")
    for o, s in enumerate(out):
        s["icon"] = o if o < 30 else (105 + o - 30 if o < tb else 125 + o - tb)
    return {s["id"]: s for s in out}


ICONS = {}


def icon(idx):
    sheet = "icons.png" if idx < 100 else "icons2.png"
    n = idx if idx < 100 else idx - 100
    if sheet not in ICONS:
        ICONS[sheet] = Image.open(os.path.join(ASSETS, "textures/gui/skill", sheet)).convert("RGBA")
    return ICONS[sheet].crop(((n % 8) * 32, (n // 8) * 32, (n % 8) * 32 + 32, (n // 8) * 32 + 32))


def item(name):
    return Image.open(os.path.join(ASSETS, "textures/item", name + ".png")).convert("RGBA")


def keyed(img):
    # 空の色を抜く (行ごとに左右の端の色を空とみなす)
    import numpy as np
    a = np.asarray(img.convert("RGB")).astype(np.int32)
    bgc = (a[:, :120].mean(axis=1) + a[:, -120:].mean(axis=1)) / 2
    d = np.sqrt(((a - bgc[:, None, :]) ** 2).sum(axis=2))
    alpha = np.clip((d - 14) * 18, 0, 255).astype(np.uint8)
    out = img.convert("RGBA")
    out.putalpha(Image.fromarray(alpha))
    return out


def lock(cv, x, y):
    # 小さい錠前 (5x6)
    for yy, row in enumerate([".XXX.", "X...X", "XXXXX", "XX.XX", "XXXXX"]):
        for xx, ch in enumerate(row):
            if ch == "X":
                cv.px(x + xx, y + yy, (230, 200, 120))
    cv.border(x - 1, y - 1, 7, 7, (0, 0, 0, 0))


def first_sentence(s):
    s = re.sub(r"（[^）]*）", "", s)
    return s.split("。")[0] + "。"


def _dim(im, k):
    r, g, b, a = im.split()
    r, g, b = [ch.point(lambda v: int(v * k)) for ch in (r, g, b)]
    return Image.merge("RGBA", (r, g, b, a))


JOBS = [("warrior", "rock", "shrine_sword"), ("smith", "ash", "shrine_hammer"), ("mage", "star", "shrine_staff"),
        ("archer", "bone", "shrine_bow"), ("assassin", "wind", "shrine_dagger"), ("shaman", "tide", "shrine_bell_staff")]
# 攻め・守り・間合い・難しさ (5 段)。ラフの仮の値 (今のデータに無い)
STATS = {"warrior": (3, 5, 1, 2), "smith": (5, 3, 1, 3), "mage": (5, 1, 5, 4), "archer": (4, 2, 5, 3), "assassin": (4, 1, 2, 5), "shaman": (2, 3, 4, 3)}
TAGS = {"archer": [("遠距離", 0), ("物理", 1), ("罠", 2)]}
TREE_OF = {"warrior": "ROCK", "smith": "ASH", "mage": "STAR", "archer": "BONE", "assassin": "WIND", "shaman": "TIDE"}

SEL_JOB = "archer"
TAKEN = {"cloud_pierce", "far_shot", "hawk_eye"}
SEL_SKILL = "cloud_pierce"


def showcase(job):
    return keyed(Image.open(os.path.join(SHOTS, "job_%s.png" % job)))


def cover(img, w, h, cx=960, cy=560, zoom=1.0):
    # 人の真ん中 (cx, cy) を中心に、w:h の形で切り抜く
    ih = img.height / zoom
    iw = ih * w / h
    if iw > img.width:
        iw = img.width
        ih = iw * h / w
    x0 = min(max(0, cx - iw / 2), img.width - iw)
    y0 = min(max(0, cy - ih / 2), img.height - ih)
    return img.crop((int(x0), int(y0), int(x0 + iw), int(y0 + ih)))


# ---------- 部品一式の共通 ----------
class Kit:
    name = ""
    split_golden = True

    def __init__(self, cv):
        self.cv = cv

    # 板の中の座標 (左上が板の左上)
    def X(self, x):
        return PX + x

    def Y(self, y):
        return PY + y


# ===== A 天の書 =====
class KitA(Kit):
    name = "A"
    title = "A 天の書"
    split_golden = False
    COVER, COVER_HI, COVER_LO = (62, 36, 24), (104, 66, 40), (30, 16, 10)
    GOLD, GOLD_LO, GOLD_HI = (214, 172, 86), (138, 98, 40), (250, 222, 150)
    PAGE, PAGE_SH, PAGE_ED = (238, 225, 190), (214, 197, 156), (192, 170, 128)
    INK, DIM, RED, BLUE = (54, 38, 26), (122, 100, 74), (156, 46, 30), (40, 74, 124)
    TEXT, HEAD, ACC, SUB = INK, RED, BLUE, DIM
    SHADOW = False

    def panel(self):
        cv = self.cv
        x, y, w, h = PX, PY, PW, PH
        cv.fill(x + 3, y + 4, w, h, (0, 0, 0, 90))  # 影
        cv.fill(x, y, w, h, self.COVER)
        cv.border(x, y, w, h, self.COVER_LO)
        cv.fill(x + 1, y + 1, w - 2, 1, self.COVER_HI)
        # 革の型押しの線
        cv.border(x + 3, y + 3, w - 6, h - 6, self.COVER_LO)
        # 頁の厚み (下と外側)
        for i, c in enumerate([self.PAGE_ED, self.PAGE_SH, self.PAGE_ED]):
            cv.fill(x + 6, y + h - 8 + i, w - 12, 1, c)
        # 見開き
        mid = x + w // 2
        cv.fill(x + 6, y + 6, w - 12, h - 14, self.PAGE)
        cv.hgrad(mid - 14, y + 6, 12, h - 14, self.PAGE, self.PAGE_SH)
        cv.hgrad(mid + 2, y + 6, 12, h - 14, self.PAGE_SH, self.PAGE)
        cv.fill(mid - 2, y + 6, 4, h - 14, (176, 152, 110))
        cv.fill(mid - 1, y + 4, 2, h - 6, self.COVER_LO)
        # 紙のしみ (固定の乱数)
        rnd = random.Random(3)
        for _ in range(120):
            cv.px(x + 8 + rnd.randrange(w - 16), y + 8 + rnd.randrange(h - 18), (226, 210, 170))
        # 四隅の金具
        for cx, cy, sx, sy in [(x, y, 1, 1), (x + w, y, -1, 1), (x, y + h, 1, -1), (x + w, y + h, -1, -1)]:
            self.corner(cx, cy, sx, sy)
        # 背の金の帯
        for yy in (y + 18, y + h - 22):
            cv.fill(mid - 3, yy, 6, 3, self.GOLD)
            cv.fill(mid - 3, yy + 2, 6, 1, self.GOLD_LO)

    def corner(self, cx, cy, sx, sy):
        cv = self.cv
        pat = ["XXXXXXXXXX",
               "XhhhhhhhX.",
               "XhGGGGGX..",
               "XhGXXXX...",
               "XhGX......",
               "XhGX......",
               "XhX.......",
               "XX........",
               "X.........",
               "X........."]
        for j, row in enumerate(pat):
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                c = {"X": self.GOLD_LO, "h": self.GOLD_HI, "G": self.GOLD}[ch]
                cv.px(cx + (i - 1) * sx - (1 if sx < 0 else 0), cy + (j - 1) * sy - (1 if sy < 0 else 0), c)
        # 角の鋲
        bx, by = cx + 3 * sx - (1 if sx < 0 else 0), cy + 3 * sy - (1 if sy < 0 else 0)
        cv.px(bx, by, self.GOLD_HI)

    def columns(self):
        # 見開きの左右の頁
        return (10, 26, PW // 2 - 18, PH - 62), (PW // 2 + 8, 26, PW // 2 - 18, PH - 62)

    def heading(self, s, x, y, w):
        cv = self.cv
        cv.text(s, x, y, self.RED, False)
        ly = y + 11
        cv.line(x, ly, x + w, ly, self.DIM)
        cv.fill(x + w // 2 - 1, ly - 1, 3, 3, self.RED)
        cv.px(x + w // 2, ly - 2, self.RED)
        cv.px(x + w // 2, ly + 2, self.RED)

    def tabs(self, items, active):
        # 上の縁から垂れる栞の紐
        cv = self.cv
        colors = [(150, 46, 30), (40, 74, 124), (64, 110, 62)]
        x = PX + 20
        for i, s in enumerate(items):
            w = int(cv.font.width(s)) + 10
            c = colors[i % 3]
            top = PY - (12 if i == active else 7)
            hh = (PY + 9) - top
            cv.fill(x, top, w, hh, c)
            cv.fill(x, top, w, 1, tuple(min(255, v + 50) for v in c))
            cv.fill(x + w - 1, top, 1, hh, tuple(v // 2 for v in c))
            # 先の切れ込み
            cv.px(x + w // 2, PY + 8, self.PAGE if False else self.COVER)
            cv.fill(x + w // 2 - 1, PY + 9, 3, 1, (0, 0, 0, 0))
            cv.text(s, x + 5, top + 2, (250, 238, 210) if i == active else (220, 200, 170), True)
            x += w + 3

    def subtabs(self, items, active, right, y):
        cv = self.cv
        x = right
        for i, s in reversed(list(enumerate(items))):
            w = cv.font.width(s)
            x -= w
            cv.text(s, x, y, self.RED if i == active else self.DIM, False)
            if i == active:
                cv.fill(x, y + 10, w, 1, self.RED)
            x -= 8

    def recess(self, x, y, w, h):
        self.cv.fill(x, y, w, h, (228, 212, 172))
        self.cv.border(x, y, w, h, self.PAGE_ED)

    def slot(self, x, y, s, state="normal"):
        cv = self.cv
        cv.fill(x, y, s, s, (226, 210, 168))
        cv.border(x, y, s, s, self.INK if state != "empty" else self.PAGE_ED)
        if state == "sel":
            cv.border(x - 2, y - 2, s + 4, s + 4, self.RED)
            cv.border(x - 1, y - 1, s + 2, s + 2, self.PAGE)

    def button(self, x, y, w, s, primary=False, disabled=False):
        cv = self.cv
        h = 13
        if primary:
            cv.fill(x, y, w, h, self.RED)
            cv.fill(x, y, w, 1, (196, 86, 60))
            cv.fill(x, y + h - 1, w, 1, (96, 24, 14))
            # 封蝋の端
            for k in range(3):
                cv.px(x - 1, y + 3 + k * 4, self.RED)
                cv.px(x + w, y + 3 + k * 4, self.RED)
            cv.text(s, x + (w - cv.font.width(s)) / 2, y + 3, (252, 240, 214), True)
        else:
            cv.border(x, y, w, h, self.DIM if disabled else self.INK)
            cv.text(s, x + (w - cv.font.width(s)) / 2, y + 3, self.DIM if disabled else self.INK, False)

    def chip(self, x, y, s, kind=0):
        cv = self.cv
        col = [self.BLUE, self.RED, (64, 110, 62), self.DIM][kind % 4]
        w = int(cv.font.width(s)) + 6
        cv.border(x, y, w, 11, col)
        cv.px(x, y, self.PAGE)
        cv.px(x + w - 1, y, self.PAGE)
        cv.px(x, y + 10, self.PAGE)
        cv.px(x + w - 1, y + 10, self.PAGE)
        cv.text(s, x + 3, y + 2, col, False)
        return x + w + 3

    def bar(self, x, y, label, v, mx=5, hard=False):
        cv = self.cv
        cv.text(label, x, y, self.DIM, False)
        bx = x + 30
        for i in range(mx):
            cv.border(bx + i * 7, y + 2, 6, 5, self.INK)
            if i < v:
                cv.fill(bx + i * 7 + 1, y + 3, 4, 3, self.RED if hard else self.BLUE)

    def showcase_frame(self, img, x, y, w, h):
        cv = self.cv
        # 頁に貼った挿絵: 白い縁・墨の枠・写真の角
        cv.fill(x + 2, y + 2, w, h, (0, 0, 0, 40))
        cv.fill(x - 3, y - 3, w + 6, h + 6, (248, 240, 220))
        cv.border(x - 3, y - 3, w + 6, h + 6, self.PAGE_ED)
        pic = cover(img, w * G, h * G, zoom=1.25, cy=640)
        # 挿絵の地: 紙に墨の円と放射の線
        cv.vgrad(x, y, w, h, (240, 226, 192), (214, 192, 148))
        cx, cy = x + w / 2, y + h * 0.42
        for r in (38, 46):
            cv.ring(cx, cy, r, (150, 120, 84))
        cv.ring(cx, cy, 52, (170, 140, 100), dash=3)
        for i in range(16):
            a = 2 * math.pi * i / 16
            cv.line(cx + 54 * math.cos(a), cy + 54 * math.sin(a), cx + 66 * math.cos(a), cy + 66 * math.sin(a), (176, 148, 108), 1, dash=2 if i % 2 else 0)
        cv.fill(cx - 22, y + h - 12, 44, 3, (150, 120, 84, 110))
        cv.fill(cx - 14, y + h - 11, 28, 1, (110, 84, 56, 120))
        cv.blit(pic, x, y, w, h, smooth=True)
        cv.border(x, y, w, h, self.INK)
        for cx, cy, sx, sy in [(x - 3, y - 3, 1, 1), (x + w + 2, y - 3, -1, 1), (x - 3, y + h + 2, 1, -1), (x + w + 2, y + h + 2, -1, -1)]:
            for k in range(6):
                for j in range(6 - k):
                    cv.px(cx + j * sx, cy + k * sy, (150, 110, 70))

    def board(self, x, y, w, h):
        pass

    def edge(self, x0, y0, x1, y1, lit):
        if lit:
            self.cv.line(x0, y0, x1, y1, self.RED, 2)
        else:
            self.cv.line(x0, y0, x1, y1, self.DIM, 1, dash=2)

    def node(self, x, y, s, img, state, kind):
        cv = self.cv
        im = img if state != "locked" else _dim(ImageOps.grayscale(img.convert("RGB")).convert("RGBA"), 0.9)
        if state == "locked":
            im.putalpha(img.getchannel("A").point(lambda a: a // 2))
        if kind == "KEYSTONE":
            r = s // 2 + 4
            for k in range(r + 1):
                cv.fill(x + s / 2 - k, y + s / 2 - r + k, 2 * k, 1, self.GOLD if state == "taken" else self.PAGE_ED)
                cv.fill(x + s / 2 - k, y + s / 2 + r - k, 2 * k, 1, self.GOLD if state == "taken" else self.PAGE_ED)
        if state == "taken":
            cv.border(x - 2, y - 2, s + 4, s + 4, self.GOLD)
        elif state == "open":
            cv.border(x - 2, y - 2, s + 4, s + 4, self.INK)
        cv.blit(im, x, y, s, s)

    def minor(self, x, y, state):
        cv = self.cv
        c = self.RED if state == "taken" else (self.INK if state == "open" else self.PAGE_ED)
        cv.fill(x - 2, y - 3, 5, 7, c)
        cv.fill(x - 3, y - 2, 7, 5, c)
        cv.fill(x - 1, y - 1, 3, 3, self.PAGE if state != "taken" else (240, 190, 120))


# ===== B 淵の石板 =====
class KitB(Kit):
    name = "B"
    title = "B 淵の石板"
    S1, S2, S3 = (34, 30, 40), (42, 37, 50), (27, 24, 32)
    RIM_HI, RIM_LO = (92, 84, 106), (12, 10, 16)
    CRACK, GLOW = (146, 90, 226), (206, 168, 255)
    TEXT, HEAD, ACC, SUB = (234, 226, 210), (224, 204, 255), (226, 188, 100), (140, 132, 156)
    DIM = SUB

    def notch(self):
        # 縁の欠け (固定)
        rnd = random.Random(7)
        cuts = []
        for _ in range(16):
            side = rnd.randrange(4)
            p = rnd.randrange(20, (PW if side < 2 else PH) - 20)
            cuts.append((side, p, rnd.randrange(3, 9), rnd.randrange(1, 3)))
        return cuts

    def panel(self):
        cv = self.cv
        x, y, w, h = PX, PY, PW, PH
        cv.fill(x + 3, y + 4, w, h, (0, 0, 0, 110))
        # 石の地 (2x2 のまだら)
        rnd = random.Random(11)
        cv.fill(x, y, w, h, self.S1)
        for yy in range(0, h, 2):
            for xx in range(0, w, 2):
                r = rnd.random()
                if r < 0.18:
                    cv.fill(x + xx, y + yy, 2, 2, self.S2)
                elif r < 0.30:
                    cv.fill(x + xx, y + yy, 2, 2, self.S3)
        # 石の継ぎ目と、彫った内の縁・見出しの帯
        for jy, xs in ((62, (0, 120, 250)), (128, (60, 190, 300)), (180, (0, 140, 270))):
            cv.fill(x + 2, y + jy, w - 4, 1, (14, 12, 18))
            cv.fill(x + 2, y + jy + 1, w - 4, 1, (50, 45, 60))
            for i, jx in enumerate(xs):
                y0 = y + jy + 2
                y1 = y + (jy + 60 if jy + 60 < h else h - 2)
                cv.fill(x + jx + 40, y0, 1, max(0, min(y1 - y0, 30)), (14, 12, 18))
        cv.vgrad(x, y, w, 23, (10, 8, 14, 120), (10, 8, 14, 60))
        cv.fill(x + 2, y + 23, w - 4, 1, (8, 6, 10))
        cv.fill(x + 2, y + 24, w - 4, 1, (70, 62, 84))
        cv.border(x + 4, y + 4, w - 8, h - 8, (14, 12, 18))
        cv.border(x + 5, y + 5, w - 10, h - 10, (58, 52, 68))
        # 上へ明るく、下へ暗く
        cv.vgrad(x, y, w, 30, (255, 255, 255, 14), (255, 255, 255, 0))
        cv.vgrad(x, y + h - 40, w, 40, (0, 0, 0, 0), (0, 0, 0, 60))
        # 縁の段 (面取り)
        cv.fill(x, y, w, 2, self.RIM_HI)
        cv.fill(x, y, 2, h, self.RIM_HI)
        cv.fill(x, y + h - 2, w, 2, self.RIM_LO)
        cv.fill(x + w - 2, y, 2, h, self.RIM_LO)
        cv.border(x - 1, y - 1, w + 2, h + 2, (8, 6, 10))
        # 欠けた角 (階段)
        for (cx, cy, sx, sy) in [(x - 1, y - 1, 1, 1), (x + w, y - 1, -1, 1), (x - 1, y + h, 1, -1), (x + w, y + h, -1, -1)]:
            for k, n in enumerate([7, 5, 4, 2, 1]):
                for j in range(n):
                    cv.px(cx + j * sx, cy + k * sy, (0, 0, 0, 0)) if False else None
            self._chip_corner(cx, cy, sx, sy)
        for side, p, ln, dp in self.notch():
            if side == 0:
                cv.fill(x + p, y - 1, ln, dp + 1, (0, 0, 0, 0)) if False else self._cut(x + p, y - 1, ln, dp + 1)
            elif side == 1:
                self._cut(x + p, y + h - dp, ln, dp + 1)
            elif side == 2:
                self._cut(x - 1, y + p, dp + 1, ln)
            else:
                self._cut(x + w - dp, y + p, dp + 1, ln)
        # 上の飾り石 (真ん中の宝石)
        cx = x + w // 2
        for k, ww in enumerate([12, 18, 24, 28, 30]):
            cv.fill(cx - ww // 2, y - 6 + k, ww, 1, self.S2 if k else self.RIM_HI)
        cv.fill(cx - 15, y - 1, 30, 1, self.RIM_HI)
        self.gem(cx, y - 2, 4)
        # 紫のひび
        self.crack([(x + 2, y + 40), (x + 9, y + 44), (x + 13, y + 52), (x + 22, y + 55)])
        self.crack([(x + w - 3, y + h - 50), (x + w - 12, y + h - 46), (x + w - 16, y + h - 38), (x + w - 28, y + h - 34)])
        self.crack([(x + w // 2 + 60, y + 2), (x + w // 2 + 64, y + 9), (x + w // 2 + 72, y + 12)])
        # 鉄の留め金 (左右)
        for yy in (y + 30, y + h - 40):
            for xx, sx in ((x - 3, 1), (x + w - 2, -1)):
                cv.fill(xx, yy, 5, 10, (70, 66, 74))
                cv.fill(xx, yy, 5, 1, (120, 114, 126))
                cv.px(xx + 2, yy + 3, (30, 28, 34))
                cv.px(xx + 2, yy + 7, (30, 28, 34))

    def _cut(self, x, y, w, h):
        # 欠け = 背景を見せる (元の景色から戻す)
        box = (x * G, y * G, (x + w) * G, (y + h) * G)
        self.cv.im.paste(self.bg.crop(box), (box[0], box[1]))

    def _chip_corner(self, cx, cy, sx, sy):
        for k, n in enumerate([6, 4, 3, 2, 1]):
            x0 = cx if sx > 0 else cx - n + 1
            self._cut(x0, cy + k * sy, n, 1)
        # 欠けの縁に明かり
        for k, n in enumerate([6, 4, 3, 2, 1]):
            xx = cx + n * sx if sx > 0 else cx - n
            self.cv.px(xx, cy + k * sy, self.RIM_HI if sy > 0 else self.RIM_LO)

    def gem(self, cx, cy, r):
        cv = self.cv
        for k in range(r + 2):
            cv.fill(cx - k, cy - r - 1 + k, 2 * k, 1, (206, 168, 255, 70))
            cv.fill(cx - k, cy + r + 1 - k, 2 * k, 1, (206, 168, 255, 70))
        for k in range(r):
            cv.fill(cx - k, cy - r + k, 2 * k, 1, self.CRACK)
            cv.fill(cx - k, cy + r - k, 2 * k, 1, (100, 56, 170))
        cv.px(cx - 1, cy - 2, self.GLOW)
        cv.px(cx - 2, cy - 1, (255, 240, 255))

    def crack(self, pts):
        cv = self.cv
        for (a, b) in zip(pts, pts[1:]):
            cv.line(a[0], a[1], b[0], b[1], (146, 90, 226, 60), 3)
        for (a, b) in zip(pts, pts[1:]):
            cv.line(a[0], a[1], b[0], b[1], self.CRACK, 1)
        cv.px(pts[1][0], pts[1][1], self.GLOW)

    def columns(self):
        return (8, 26, 128, PH - 62), (8 + 128 + 5, 26, PW - 16 - 128 - 5, PH - 62)

    def heading(self, s, x, y, w):
        cv = self.cv
        cv.text(s, x, y, self.HEAD)
        cv.fill(x, y + 11, w, 1, self.RIM_LO)
        cv.fill(x, y + 12, w, 1, (60, 54, 70))
        cv.fill(x, y + 11, 14, 1, self.CRACK)

    def tabs(self, items, active):
        cv = self.cv
        x = PX + 8
        for i, s in enumerate(items):
            w = int(cv.font.width(s)) + 12
            on = i == active
            y = PY + 6
            cv.fill(x, y, w, 14, (52, 46, 62) if on else (26, 23, 31))
            cv.fill(x, y, w, 1, self.RIM_HI if on else (44, 40, 52))
            cv.fill(x, y + 13, w, 1, self.RIM_LO)
            if on:
                cv.fill(x + 2, y + 13, w - 4, 1, self.CRACK)
                cv.fill(x + 2, y + 14, w - 4, 1, (146, 90, 226, 90))
            cv.text(s, x + 6, y + 3, self.TEXT if on else self.SUB)
            x += w + 2

    def subtabs(self, items, active, right, y):
        cv = self.cv
        x = right
        for i, s in reversed(list(enumerate(items))):
            w = int(cv.font.width(s)) + 10
            x -= w
            on = i == active
            cv.fill(x, y - 3, w, 13, (52, 46, 62) if on else (22, 20, 27))
            cv.border(x, y - 3, w, 13, self.RIM_LO)
            if on:
                cv.fill(x + 1, y - 3, w - 2, 1, self.RIM_HI)
            cv.text(s, x + 5, y, self.ACC if on else self.SUB)
            x -= 2

    def recess(self, x, y, w, h):
        cv = self.cv
        cv.fill(x, y, w, h, (18, 16, 23, 235))
        cv.fill(x, y, w, 1, (6, 5, 8))
        cv.fill(x, y, 1, h, (6, 5, 8))
        cv.fill(x, y + h - 1, w, 1, (62, 56, 72))
        cv.fill(x + w - 1, y, 1, h, (62, 56, 72))

    def slot(self, x, y, s, state="normal"):
        cv = self.cv
        cv.fill(x, y, s, s, (22, 20, 28))
        cv.fill(x, y, s, 1, self.RIM_LO)
        cv.fill(x, y, 1, s, self.RIM_LO)
        cv.fill(x, y + s - 1, s, 1, (70, 64, 82))
        cv.fill(x + s - 1, y, 1, s, (70, 64, 82))
        if state == "sel":
            cv.border(x - 1, y - 1, s + 2, s + 2, self.CRACK)
            cv.border(x - 2, y - 2, s + 4, s + 4, (146, 90, 226, 80))
            cv.vgrad(x + 1, y + s // 2, s - 2, s // 2 - 1, (146, 90, 226, 0), (146, 90, 226, 90))

    def button(self, x, y, w, s, primary=False, disabled=False):
        cv = self.cv
        h = 13
        if primary:
            cv.fill(x - 1, y - 1, w + 2, h + 2, (146, 90, 226, 70))
            cv.vgrad(x, y, w, h, (120, 70, 190), (74, 40, 128))
            cv.fill(x, y, w, 1, (190, 150, 250))
            cv.fill(x, y + h - 1, w, 1, (40, 20, 70))
            cv.text(s, x + (w - cv.font.width(s)) / 2, y + 3, (252, 246, 255))
        else:
            cv.fill(x, y, w, h, (40, 36, 48))
            cv.fill(x, y, w, 1, self.RIM_HI if not disabled else (52, 48, 60))
            cv.fill(x, y + h - 1, w, 1, self.RIM_LO)
            cv.text(s, x + (w - cv.font.width(s)) / 2, y + 3, self.SUB if disabled else self.TEXT)

    def chip(self, x, y, s, kind=0):
        cv = self.cv
        col = [(110, 190, 230), (230, 150, 90), (130, 200, 120), (180, 170, 200)][kind % 4]
        w = int(cv.font.width(s)) + 8
        cv.fill(x, y, w, 11, (46, 40, 56))
        cv.fill(x, y, 2, 11, col)
        cv.fill(x, y + 10, w, 1, self.RIM_LO)
        cv.text(s, x + 5, y + 2, self.TEXT)
        return x + w + 3

    def bar(self, x, y, label, v, mx=5, hard=False):
        cv = self.cv
        cv.text(label, x, y, self.SUB)
        bx = x + 30
        for i in range(mx):
            cx, cy = bx + i * 8 + 3, y + 4
            on = i < v
            c = (226, 150, 90) if hard else self.CRACK
            for k in range(4):
                cv.fill(cx - k, cy - 3 + k, 2 * k, 1, c if on else (20, 18, 24))
                cv.fill(cx - k, cy + 3 - k, 2 * k, 1, c if on else (20, 18, 24))
            if on:
                cv.px(cx - 1, cy - 1, (255, 230, 255) if not hard else (255, 230, 190))
            else:
                cv.px(cx - 1, cy - 2, (70, 64, 82))

    def showcase_frame(self, img, x, y, w, h):
        cv = self.cv
        pic = cover(img, w * G, h * G, zoom=1.2, cy=640)
        # 淵の地: 紫の闇・後ろの光・浮かぶ欠片
        cv.vgrad(x, y, w, h, (58, 30, 96), (12, 8, 20))
        cx, cy = x + w / 2, y + h * 0.48
        for r, al in ((60, 30), (46, 40), (32, 50)):
            cv.disc(cx, cy, r, (170, 110, 255, al))
        rnd = random.Random(4)
        for _ in range(14):
            sx, sy, r = x + 6 + rnd.randrange(w - 12), y + 8 + rnd.randrange(h - 30), rnd.choice([1, 2, 2, 3])
            for k in range(r + 1):
                cv.fill(sx - k, sy - r + k, 2 * k + 1, 1, (150, 96, 230))
                cv.fill(sx - k, sy + r - k, 2 * k + 1, 1, (90, 50, 150))
            cv.px(sx, sy - r + 1, self.GLOW)
        # 足もとの割れた岩
        cv.fill(cx - 30, y + h - 16, 60, 16, (24, 20, 30))
        cv.fill(cx - 34, y + h - 10, 68, 10, (20, 17, 26))
        cv.fill(cx - 30, y + h - 16, 60, 1, (70, 62, 84))
        self.crack([(cx - 20, y + h - 15), (cx - 12, y + h - 9), (cx - 4, y + h - 6)])
        cv.blit(pic, x, y, w, h - 12, smooth=True)
        # 下へ紫の影
        cv.vgrad(x, y + h - 40, w, 40, (30, 14, 50, 0), (30, 14, 50, 200))
        cv.vgrad(x, y, w, 20, (10, 8, 14, 120), (10, 8, 14, 0))
        # 石のアーチ (上の角を階段で丸める)
        for k, n in enumerate([14, 10, 7, 5, 4, 3, 2, 2, 1, 1]):
            cv.fill(x, y + k, n, 1, self.S1)
            cv.fill(x + w - n, y + k, n, 1, self.S1)
            cv.px(x + n, y + k, self.RIM_LO)
            cv.px(x + w - n - 1, y + k, self.RIM_LO)
        cv.fill(x, y + h - 1, w, 1, (62, 56, 72))
        cv.fill(x - 1, y + 10, 1, h - 10, self.RIM_LO)
        cv.fill(x + w, y + 10, 1, h - 10, (62, 56, 72))
        cv.fill(x + 14, y - 1, w - 28, 1, self.RIM_LO)
        # 要石の宝石
        self.gem(x + w // 2, y - 1, 3)

    def board(self, x, y, w, h):
        cv = self.cv
        rnd = random.Random(5)
        for _ in range(40):
            cv.px(x + 2 + rnd.randrange(w - 4), y + 2 + rnd.randrange(h - 4), (40, 34, 52))

    def edge(self, x0, y0, x1, y1, lit):
        cv = self.cv
        if lit:
            cv.line(x0, y0, x1, y1, (146, 90, 226, 70), 4)
            cv.line(x0, y0, x1, y1, self.CRACK, 2)
        else:
            cv.line(x0, y0, x1, y1, (8, 7, 10), 2)
            cv.line(x0 + 1, y0 + 1, x1 + 1, y1 + 1, (48, 44, 58), 1)

    def node(self, x, y, s, img, state, kind):
        cv = self.cv
        im = img if state != "locked" else _dim(img, 0.35)
        if kind == "KEYSTONE":
            r = s // 2 + 5
            for k in range(r + 1):
                c = self.CRACK if state == "taken" else (60, 54, 72)
                cv.fill(x + s / 2 - k, y + s / 2 - r + k, 2 * k, 1, c)
                cv.fill(x + s / 2 - k, y + s / 2 + r - k, 2 * k, 1, c)
        if state == "taken":
            cv.fill(x - 3, y - 3, s + 6, s + 6, (146, 90, 226, 60))
            cv.border(x - 2, y - 2, s + 4, s + 4, self.CRACK)
        elif state == "open":
            cv.border(x - 2, y - 2, s + 4, s + 4, self.RIM_HI)
        else:
            cv.border(x - 2, y - 2, s + 4, s + 4, (30, 27, 36))
        cv.blit(im, x, y, s, s)

    def minor(self, x, y, state):
        cv = self.cv
        c = self.CRACK if state == "taken" else ((92, 84, 106) if state == "open" else (40, 36, 48))
        for k in range(4):
            cv.fill(x - k, y - 3 + k, 2 * k + 1, 1, c)
            cv.fill(x - k, y + 3 - k, 2 * k + 1, 1, c)
        if state == "taken":
            cv.px(x, y - 1, self.GLOW)


# ===== C 夜空の展示 =====
class KitC(Kit):
    name = "C"
    title = "C 夜空の展示"
    GOLD, GOLD_HI, GOLD_LO = (216, 180, 98), (255, 230, 156), (120, 92, 40)
    CYAN = (120, 204, 244)
    TEXT, HEAD, ACC, SUB = (238, 234, 222), (255, 226, 146), (132, 208, 246), (142, 152, 184)
    DIM = SUB

    def panel(self):
        cv = self.cv
        x, y, w, h = PX, PY, PW, PH
        cv.fill(x + 3, y + 4, w, h, (0, 0, 0, 100))
        cv.vgrad(x, y, w, h, (24, 32, 62, 238), (10, 14, 32, 246))
        rnd = random.Random(2)
        for _ in range(70):
            xx, yy = rnd.randrange(w), rnd.randrange(h // 2)
            cv.px(x + xx, y + yy, (200, 210, 255, rnd.choice([40, 70, 120])))
        cv.border(x, y, w, h, (6, 8, 18))
        cv.border(x + 2, y + 2, w - 4, h - 4, self.GOLD_LO)
        cv.border(x + 3, y + 3, w - 6, h - 6, self.GOLD)
        for (cx, cy, sx, sy) in [(x + 3, y + 3, 1, 1), (x + w - 4, y + 3, -1, 1), (x + 3, y + h - 4, 1, -1), (x + w - 4, y + h - 4, -1, -1)]:
            self.corner(cx, cy, sx, sy)

    def corner(self, cx, cy, sx, sy):
        cv = self.cv
        pat = ["GGGGGGGGGGG",
               "GhhhG......",
               "GhDhG......",
               "GhhhGGG....",
               "GGGG..G....",
               "G..G...G...",
               "G..G.......",
               "G...G......",
               "G..........",
               "G.........."]
        for j, row in enumerate(pat):
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                c = {"G": self.GOLD, "h": self.GOLD_HI, "D": (255, 255, 255)}[ch]
                cv.px(cx + i * sx, cy + j * sy, c)

    def columns(self):
        return (10, 28, 132, PH - 64), (10 + 132 + 5, 28, PW - 20 - 132 - 5, PH - 64)

    def title_plate(self, s):
        cv = self.cv
        w = int(cv.font.width(s)) + 34
        x = PX + (PW - w) // 2
        y = PY - 5
        cv.fill(x, y, w, 15, (14, 20, 42))
        cv.border(x, y, w, 15, self.GOLD)
        cv.border(x + 1, y + 1, w - 2, 13, self.GOLD_LO)
        for sx in (x + 6, x + w - 9):
            cv.px(sx + 1, y + 5, self.GOLD_HI)
            cv.fill(sx, y + 6, 3, 1, self.GOLD_HI)
            cv.px(sx + 1, y + 7, self.GOLD_HI)
        cv.text(s, x + 17, y + 4, self.HEAD)

    def heading(self, s, x, y, w):
        cv = self.cv
        cv.text(s, x, y, self.HEAD)
        cv.hgrad(x, y + 11, w, 1, self.GOLD + (255,), self.GOLD + (0,))

    def tabs(self, items, active):
        cv = self.cv
        x = PX + 12
        for i, s in enumerate(items):
            on = i == active
            w = int(cv.font.width(s)) + 10
            if on:
                cv.vgrad(x, PY + 8, w, 13, (216, 180, 98, 0), (216, 180, 98, 60))
                cv.fill(x, PY + 20, w, 1, self.GOLD)
            cv.text(s, x + 5, PY + 10, self.HEAD if on else self.SUB)
            x += w + 4

    def subtabs(self, items, active, right, y):
        cv = self.cv
        x = right
        for i, s in reversed(list(enumerate(items))):
            w = int(cv.font.width(s)) + 12
            x -= w
            on = i == active
            if on:
                cv.fill(x, y - 3, w, 13, (216, 180, 98, 50))
                cv.border(x, y - 3, w, 13, self.GOLD)
            else:
                cv.border(x, y - 3, w, 13, (60, 70, 110))
            cv.text(s, x + 6, y, self.HEAD if on else self.SUB)
            x -= 3

    def recess(self, x, y, w, h):
        self.cv.fill(x, y, w, h, (6, 10, 24, 190))
        self.cv.border(x, y, w, h, (54, 66, 108))

    def slot(self, x, y, s, state="normal"):
        cv = self.cv
        cv.fill(x, y, s, s, (10, 14, 30, 220))
        cv.border(x, y, s, s, (64, 78, 122))
        if state == "sel":
            cv.border(x - 1, y - 1, s + 2, s + 2, self.GOLD)
            cv.border(x, y, s, s, self.GOLD_HI)
            cv.vgrad(x + 1, y + 1, s - 2, s - 2, (120, 204, 244, 0), (120, 204, 244, 70))

    def button(self, x, y, w, s, primary=False, disabled=False):
        cv = self.cv
        h = 13
        if primary:
            cv.vgrad(x, y, w, h, self.GOLD_HI, self.GOLD)
            cv.fill(x, y + h - 1, w, 1, self.GOLD_LO)
            cv.text(s, x + (w - cv.font.width(s)) / 2, y + 3, (30, 24, 14), False)
        else:
            cv.fill(x, y, w, h, (12, 18, 38, 200))
            cv.border(x, y, w, h, (70, 80, 120) if disabled else self.GOLD)
            cv.text(s, x + (w - cv.font.width(s)) / 2, y + 3, self.SUB if disabled else self.TEXT)

    def chip(self, x, y, s, kind=0):
        cv = self.cv
        col = [(96, 178, 226), (226, 140, 80), (120, 190, 110), (150, 140, 200)][kind % 4]
        w = int(cv.font.width(s)) + 6
        cv.fill(x, y, w, 11, col)
        cv.fill(x, y, w, 1, tuple(min(255, v + 40) for v in col))
        cv.text(s, x + 3, y + 2, (14, 16, 26), False)
        return x + w + 3

    def bar(self, x, y, label, v, mx=5, hard=False):
        cv = self.cv
        cv.text(label, x, y, self.SUB)
        bx = x + 30
        c = (240, 170, 80) if hard else self.CYAN
        for i in range(mx):
            cv.fill(bx + i * 8, y + 3, 7, 3, c if i < v else (36, 46, 80))
            if i < v:
                cv.fill(bx + i * 8, y + 3, 7, 1, tuple(min(255, k + 60) for k in c))

    def showcase_full(self, img):
        # 右の 6 割を絵が埋めて、左へ板の色に溶ける
        cv = self.cv
        x0 = PX + 4 + 120
        w = PW - 8 - 120
        y0, h = PY + 4, PH - 8
        pic = cover(img, w * G, h * G, zoom=1.15, cx=900, cy=640)
        # 夜空の地: 藍の階調・星・後ろの光の輪
        cv.vgrad(x0, y0, w, h, (34, 50, 100), (12, 16, 36))
        rnd = random.Random(8)
        for _ in range(90):
            cv.px(x0 + rnd.randrange(w), y0 + rnd.randrange(h), (210, 220, 255, rnd.choice([50, 90, 160])))
        cx, cy = x0 + w * 0.42, y0 + h * 0.46
        for r, al in ((74, 18), (58, 26), (44, 34)):
            cv.disc(cx, cy, r, (255, 214, 140, al))
        cv.ring(cx, cy, 62, (255, 226, 150, 120))
        cv.ring(cx, cy, 66, (255, 226, 150, 60), dash=2)
        cv.blit(pic, x0, y0, w, h, smooth=True)
        cv.hgrad(x0, y0, 70, h, (16, 22, 46, 255), (16, 22, 46, 0))
        cv.vgrad(x0, y0 + h - 60, w, 60, (10, 14, 32, 0), (10, 14, 32, 230))
        cv.vgrad(x0, y0, w, 24, (24, 32, 62, 200), (24, 32, 62, 0))
        # 額の金の線を引き直す
        cv.fill(PX + 3, PY + 3, PW - 6, 1, self.GOLD)
        cv.fill(PX + 3, PY + PH - 4, PW - 6, 1, self.GOLD)
        cv.fill(PX + PW - 4, PY + 3, 1, PH - 6, self.GOLD)
        for (cx, cy, sx, sy) in [(PX + PW - 4, PY + 3, -1, 1), (PX + PW - 4, PY + PH - 4, -1, -1)]:
            self.corner(cx, cy, sx, sy)

    def board(self, x, y, w, h):
        cv = self.cv
        rnd = random.Random(9)
        for _ in range(60):
            cv.px(x + 2 + rnd.randrange(w - 4), y + 2 + rnd.randrange(h - 4), (190, 205, 255, rnd.choice([30, 60, 110])))

    def edge(self, x0, y0, x1, y1, lit):
        cv = self.cv
        if lit:
            cv.line(x0, y0, x1, y1, (120, 204, 244, 60), 3)
            cv.line(x0, y0, x1, y1, (190, 236, 255), 1)
        else:
            cv.line(x0, y0, x1, y1, (70, 84, 130), 1, dash=2)

    def node(self, x, y, s, img, state, kind):
        cv = self.cv
        im = img if state != "locked" else _dim(img, 0.4)
        if kind == "KEYSTONE":
            r = s // 2 + 5
            for k in range(r + 1):
                c = self.GOLD if state == "taken" else (40, 50, 86)
                cv.fill(x + s / 2 - k, y + s / 2 - r + k, 2 * k, 1, c)
                cv.fill(x + s / 2 - k, y + s / 2 + r - k, 2 * k, 1, c)
        if state == "taken":
            cv.fill(x - 3, y - 3, s + 6, s + 6, (255, 220, 140, 40))
            cv.border(x - 2, y - 2, s + 4, s + 4, self.GOLD)
            # 四方の光
            cx, cy = x + s // 2, y + s // 2
            for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                for k in range(3):
                    cv.px(cx + dx * (s // 2 + 3 + k), cy + dy * (s // 2 + 3 + k), (255, 236, 170, 200 - k * 60))
        elif state == "open":
            cv.border(x - 2, y - 2, s + 4, s + 4, (96, 150, 200))
        cv.blit(im, x, y, s, s)

    def minor(self, x, y, state):
        cv = self.cv
        c = (255, 236, 170) if state == "taken" else ((120, 170, 220) if state == "open" else (56, 66, 104))
        cv.fill(x - 1, y - 2, 3, 5, c)
        cv.fill(x - 2, y - 1, 5, 3, c)
        if state == "taken":
            cv.px(x, y - 3, c)
            cv.px(x, y + 3, c)
            cv.px(x - 3, y, c)
            cv.px(x + 3, y, c)


# ---------- 画面: ジョブ選び ----------
def job_screen(kit, skills):
    cv = kit.cv
    kit.panel()
    job = SEL_JOB
    left, right = kit.columns()
    lx, ly, lw, lh = kit.X(left[0]), kit.Y(left[1]), left[2], left[3]
    title = L("gui.skyabyss.job_choice.title")
    lead = L("gui.skyabyss.job_choice.lead")
    if kit.name == "C":
        kit.showcase_full(showcase(job))
        kit.title_plate(title)
    elif kit.name == "B":
        kit.heading(title, kit.X(8), kit.Y(9), PW - 16)
        cv.text(lead, kit.X(PW - 8) - cv.font.width(lead), kit.Y(9), kit.SUB)
    else:
        kit.heading(title, kit.X(10), kit.Y(10), PW // 2 - 18)
        cv.text(lead, kit.X(PW // 2 + 8), kit.Y(10), kit.DIM, False)
    sh = kit.SHADOW if hasattr(kit, "SHADOW") else True
    # 名前
    y = ly + (2 if kit.name != "C" else 0)
    weapon = dict((j, w) for j, _, w in JOBS)[job]
    kit.slot(lx, y, 20, "sel" if kit.name == "B" else "normal")
    cv.blit(item(weapon), lx + 2, y + 2, 16, 16)
    cv.text(L("job.skyabyss.%s" % job), lx + 25, y + 1, kit.HEAD, sh)
    sub = L("skilltree.skyabyss.%s" % dict((j, t) for j, t, _ in JOBS)[job])
    cv.text(sub, lx + 25 + cv.font.width(L("job.skyabyss.%s" % job)) + 5, y + 1, kit.SUB, sh)
    cv.text(L("job.skyabyss.%s.role" % job), lx + 25, y + 11, kit.TEXT, sh)
    # 4 つの棒
    y += 27
    st = STATS[job]
    for i, (lab, v) in enumerate(zip(["攻め", "守り", "間合い", "難しさ"], st)):
        kit.bar(lx, y + i * 9, lab, v, hard=(i == 3))
    # 始めのスキル
    y += 40
    cv.text("覚えるスキル", lx, y, kit.SUB, sh)
    y += 11
    tree = TREE_OF[job]
    first = [s for s in skills.values() if s["tree"] == tree and s["tier"] <= 1][:5]
    for i, s in enumerate(first):
        st_ = "sel" if s["id"] == SEL_SKILL else "normal"
        kit.slot(lx + i * 22, y, 20, st_)
        cv.blit(icon(s["icon"]) if s["tier"] == 0 else _dim(icon(s["icon"]), 0.45), lx + i * 22 + 2, y + 2, 16, 16)
        if s["tier"] > 0:
            lock(cv, lx + i * 22 + 14, y + 13)
    y += 27
    sk = skills[SEL_SKILL]
    nx = cv.text(L("skill.skyabyss.%s" % SEL_SKILL), lx, y, kit.ACC, sh)
    kit.chip(nx + 5, y - 1, "発動" if sk["kind"] == "ACTIVE" else "常時", 3)
    y += 13
    eff = first_sentence(L("skill.skyabyss.%s.effect" % SEL_SKILL))
    y = cv.para(eff, lx, y, lw, kit.TEXT, maxl=2, shadow=sh)
    # 札
    y += 3
    x = lx
    for t, k in TAGS[job]:
        x = kit.chip(x, y, t, k)
    # 下の帯: ジョブを送る列と決める釦
    by = kit.Y(PH - 30)
    bx = kit.X(10)
    cv.text("<", bx, by + 6, kit.SUB, sh)
    for i, (j, t, w) in enumerate(JOBS):
        sx = bx + 9 + i * 23
        kit.slot(sx, by, 20, "sel" if j == job else "normal")
        cv.blit(item(w) if j == job else _dim(item(w), 0.6), sx + 2, by + 2, 16, 16)
    cv.text(">", bx + 9 + 6 * 23 + 2, by + 6, kit.SUB, sh)
    bw = 96
    kit.button(kit.X(PW - 10 - bw), by + 3, bw, L("gui.skyabyss.job_choice.start"), primary=True)
    # 見せ絵 (A と B)
    if kit.name in ("A", "B"):
        rx, ry, rw, rh = kit.X(right[0]), kit.Y(right[1]), right[2], right[3]
        kit.showcase_frame(showcase(job), rx + 3, ry + 3, rw - 6, rh - 6 + 22 - 26)
        if kit.name == "A":
            cap = "%s ― %s" % (sub, L("job.skyabyss.%s.role" % job))
            cv.text(cap, rx + (rw - cv.font.width(cap)) / 2, ry + rh - 2 - 4, kit.DIM, False)
        if kit.name == "B":
            cv.text("熟練  0 / 50", rx + 6, ry + rh - 26, kit.TEXT)


# ---------- 画面: スキルの木 ----------
def skill_screen(kit, skills):
    cv = kit.cv
    kit.panel()
    sh = kit.SHADOW if hasattr(kit, "SHADOW") else True
    job = SEL_JOB
    tree = TREE_OF[job]
    tabs = ["記録", "スキル", "旅"]
    if kit.name == "A":
        kit.tabs(tabs, 1)
        kit.subtabs(["ツリー", "名残"], 0, kit.X(PW - 12), kit.Y(10))
        board = (kit.X(10), kit.Y(24), PW // 2 - 18, PH - 62)
        side = (kit.X(PW // 2 + 8), kit.Y(24), PW // 2 - 18, PH - 62)
    else:
        if kit.name == "C":
            kit.title_plate("スキル")
        kit.tabs(tabs, 1)
        kit.subtabs(["ツリー", "名残"], 0, kit.X(PW - 10), kit.Y(10))
        board = (kit.X(8 if kit.name == "B" else 10), kit.Y(26), 207, PH - 62)
        side = (board[0] + 207 + 5, kit.Y(26), PW - 16 - 207 - 5 - (4 if kit.name == "C" else 0), PH - 62)
    bx, by, bw, bh = board
    if kit.name != "A":
        kit.recess(bx, by, bw, bh)
    kit.board(bx, by, bw, bh)
    # 木の頭 (ジョブの印)
    name = L("skilltree.skyabyss.%s" % tree.lower())
    cv.text("%s（%s）" % (L("job.skyabyss.%s" % job), name), bx + 5, by + 4, kit.HEAD, sh)
    sp = "SP 2"
    cv.text(sp, bx + bw - 6 - cv.font.width(sp), by + 4, kit.ACC, sh)
    nodes = [s for s in skills.values() if s["tree"] == tree]
    pos = {}
    top = by + 26
    for s in nodes:
        cx = bx + bw // 2 + (s["col"] - 1) * 62
        cy = top + s["tier"] * 33
        pos[s["id"]] = (cx, cy)

    def state(s):
        if s["id"] in TAKEN:
            return "taken"
        if all(p in TAKEN for p in s["pre"]):
            return "open"
        return "locked"
    # 線と小さい節 (線の途中)
    for s in nodes:
        for p in s["pre"]:
            if p not in pos:
                continue
            (x0, y0), (x1, y1) = pos[p], pos[s["id"]]
            lit = p in TAKEN and s["id"] in TAKEN
            kit.edge(x0, y0, x1, y1, lit)
            fs = (1 / 3, 2 / 3) if math.hypot(x1 - x0, y1 - y0) > 50 else (0.5,)
            for f in fs:
                kit.minor(round(x0 + (x1 - x0) * f), round(y0 + (y1 - y0) * f), "taken" if lit else ("open" if p in TAKEN and f < 0.5 else "locked"))
    # 根から一段目へ
    rx, ry = bx + bw // 2, by + 16
    for s in nodes:
        if s["tier"] == 0:
            kit.edge(rx, ry, pos[s["id"]][0], pos[s["id"]][1], s["id"] in TAKEN)
    kit.minor(rx, ry, "taken")
    for s in nodes:
        cx, cy = pos[s["id"]]
        size = 20 if s["kind"] == "ACTIVE" else (16 if s["kind"] == "PASSIVE" else 20)
        kit.node(cx - size // 2, cy - size // 2, size, icon(s["icon"]), state(s), s["kind"])
        if s["id"] == SEL_SKILL:
            cv.border(cx - size // 2 - 4, cy - size // 2 - 4, size + 8, size + 8, kit.ACC)
    hint = "ドラッグ・ホイール・右で戻す"
    cv.text(hint, bx + (bw - cv.font.width(hint)) / 2, by + bh - 11, kit.SUB, sh)
    # 横の札: 選んだスキル
    sx, sy, sw, shh = side
    if kit.name != "A":
        kit.recess(sx, sy, sw, shh)
    sk = skills[SEL_SKILL]
    kit.slot(sx + 4, sy + 4, 24, "sel")
    cv.blit(icon(sk["icon"]), sx + 6, sy + 6, 20, 20)
    cv.text(L("skill.skyabyss.%s" % SEL_SKILL), sx + 32, sy + 5, kit.HEAD, sh)
    x = kit.chip(sx + 32, sy + 16, L("gui.skyabyss.skill.kind_active")[:2], 3)
    kit.chip(x, sy + 16, "段 1/3", 0)
    y = sy + 32
    cv.text("マナ 12", sx + 5, y, kit.ACC, sh)
    cv.text("再使用 3.9 秒", sx + 52, y, kit.TEXT, sh)
    y += 11
    cv.text(L("skill.skyabyss.%s.type" % SEL_SKILL), sx + 5, y, kit.TEXT, sh)
    y += 13
    cv.fill(sx + 5, y - 2, sw - 10, 1, kit.SUB + (90,))
    y = cv.para(L("skill.skyabyss.%s.flavor" % SEL_SKILL), sx + 5, y + 1, sw - 10, kit.ACC if kit.name != "A" else kit.BLUE, maxl=2, shadow=sh)
    eff = re.sub(r"（[^）]*）", "", L("skill.skyabyss.%s.effect" % SEL_SKILL, "4.5", "3"))
    lines = cv.wrap(eff, sw - 10)
    while len(lines) > 3 and "。" in eff[:-1]:
        eff = eff[:eff[:-1].rindex("。") + 1]
        lines = cv.wrap(eff, sw - 10)
    y = cv.para(eff, sx + 5, y + 2, sw - 10, kit.TEXT, maxl=3, shadow=sh)
    cv.text("次の段  威力 4.5 → 5.6", sx + 5, y + 2, kit.SUB, sh)
    kit.button(sx + 5, sy + shh - 19, sw - 10, "取得済み", disabled=True)
    # 下の帯
    yb = kit.Y(PH - 32)
    x0 = bx
    OFF, STEP = (56, 24) if kit.name == "A" else (70, 24)
    if kit.name != "A":
        kit.recess(x0, yb - 2, 207, 26)
    cv.text("SP 2", x0 + 5, yb + 2, kit.ACC, sh)
    cv.text("マナ 20/20", x0 + 5, yb + 12, kit.TEXT, sh)
    for i, k in enumerate("RGVZ"):
        xx = x0 + OFF + i * STEP
        kit.slot(xx, yb, 22, "sel" if i == 0 else "empty" if i else "normal")
        if i == 0:
            cv.blit(icon(sk["icon"]), xx + 3, yb + 3, 16, 16)
        cv.text(k, xx + 15, yb + 13, kit.TEXT, True)
    if kit.name != "A":
        cv.text("秘伝", x0 + OFF + 4 * STEP + 4, yb + 7, kit.SUB, sh)
    # 右: 型の保存と釦
    x1 = side[0]
    if kit.name != "A":
        kit.recess(x1, yb - 2, side[2], 26)
    for i, s in enumerate(["1", "2"]):
        kit.button(x1 + 4 + i * 18, yb + 3, 16, s, primary=(i == 0))
    kit.button(x1 + 42, yb + 3, 28, "保存")
    kit.button(x1 + 73, yb + 3, side[2] - 77, "試着")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--font", required=True)
    a = ap.parse_args()
    font = Font(a.font)
    skills = load_skills()
    bg = Image.open(os.path.join(SHOTS, "bg.png")).convert("RGBA").filter(ImageFilter.GaussianBlur(10))
    os.makedirs(OUT, exist_ok=True)
    outs = []
    for K in (KitA, KitB, KitC):
        for scr, fn in (("job", job_screen), ("skill", skill_screen)):
            cv = Canvas(bg.copy(), font)
            kit = K(cv)
            kit.bg = bg
            fn(kit, skills)
            p = os.path.join(OUT, "%s_%s.png" % (K.name, scr))
            cv.im.convert("RGB").save(p)
            outs.append((K.title, scr, p))
    # 一覧 (今 / A / B / C × 2 画面)
    now = os.path.join(ROOT, "run/shots/overlap_audit/shots/overlap_audit/job_choice_1920_g4.png")
    now2 = os.path.join(ROOT, "run/shots/tree_taken_points/shots/tree_taken_points/taken_no_points.png")
    rows = [("今", now, now2)] + [(t, os.path.join(OUT, "%s_job.png" % t[0]), os.path.join(OUT, "%s_skill.png" % t[0])) for t in (KitA.title, KitB.title, KitC.title)]
    cw, ch, lab = 960, 540, 40
    sheet = Image.new("RGB", (cw * 2, (ch + lab) * len(rows)), (16, 16, 20))
    scv = Canvas(sheet, font)
    for r, (t, p1, p2) in enumerate(rows):
        y = r * (ch + lab)
        for c, p in enumerate((p1, p2)):
            if os.path.exists(p):
                sheet.paste(Image.open(p).convert("RGB").resize((cw, ch), Image.LANCZOS), (c * cw, y + lab))
        scv.im = sheet
        scv.d = ImageDraw.Draw(sheet)
        scv.text(t, 3, y / G + 2, (240, 230, 200))
    sheet.save(os.path.join(OUT, "sheet.png"))
    print("wrote", len(outs) + 1, "files to", OUT)


if __name__ == "__main__":
    main()
