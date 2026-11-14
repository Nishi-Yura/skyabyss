# -*- coding: utf-8 -*-
# 新しい UI の基盤デザインと HUD 合体案のラフ。GUI の 480x270 (1920x1080 の GUI 4) を 2 倍で描く。
import os, random, sys
from PIL import Image, ImageDraw, ImageFont, ImageEnhance

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
TEX = os.path.join(REPO, "src/main/resources/assets/skyabyss/textures")
OUT = os.path.join(REPO, "run", "ui_mock")
os.makedirs(OUT, exist_ok=True)
S = 2
W, H = 480, 270
BODY = ImageFont.truetype(os.path.join(REPO, "tools/sources/fonts/DotGothic16-Regular.ttf"), 16)
TITLE = ImageFont.truetype(os.path.join(REPO, "tools/sources/fonts/ZenAntique-Regular.ttf"), 22)


class C:
    def __init__(self, bg, dark=0.0):
        im = Image.open(os.path.join(REPO, "site/img/shot", bg)).convert("RGB").resize((W * S, H * S), Image.LANCZOS)
        if dark:
            im = ImageEnhance.Brightness(im).enhance(1 - dark)
        self.im = im.convert("RGBA")

    def fill(self, x, y, w, h, col):
        if w <= 0 or h <= 0:
            return
        box = (int(round(x * S)), int(round(y * S)), int(round((x + w) * S)), int(round((y + h) * S)))
        if len(col) == 3 or col[3] == 255:
            self.im.paste(tuple(col[:3]) + (255,), box)
            return
        reg = self.im.crop(box)
        reg = Image.alpha_composite(reg, Image.new("RGBA", reg.size, tuple(col)))
        self.im.paste(reg, box)

    def grad(self, x, y, w, h, c1, c2):
        n = max(1, int(h * S))
        for i in range(n):
            t = i / max(1, n - 1)
            col = tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(len(c1)))
            self.fill(x, y + i / S, w, 1 / S, col)

    def box(self, x, y, w, h, col, t=1):
        self.fill(x, y, w, t, col)
        self.fill(x, y + h - t, w, t, col)
        self.fill(x, y, t, h, col)
        self.fill(x + w - t, y, t, h, col)

    def diamond(self, cx, cy, r, col):
        for dy in range(-r, r + 1):
            half = r - abs(dy)
            self.fill(cx - half, cy + dy, 2 * half + 1, 1, col)

    def tw(self, s, font=BODY):
        return font.getlength(s) / S

    def text(self, x, y, s, col, font=BODY, shadow=None, anchor="l"):
        d = ImageDraw.Draw(self.im)
        d.fontmode = "1" if font is BODY else "L"
        w = font.getlength(s)
        px = x * S - (w if anchor == "r" else w / 2 if anchor == "c" else 0)
        py = y * S - (3 if font is BODY else 6)
        if shadow:
            d.text((px + S, py + S), s, font=font, fill=tuple(shadow))
        d.text((px, py), s, font=font, fill=tuple(col))

    def icon(self, name, x, y, size=16, sheet=None, cell=None, alpha=1.0):
        if sheet:
            im = Image.open(os.path.join(TEX, sheet)).convert("RGBA").crop((cell[0] * 32, cell[1] * 32, cell[0] * 32 + 32, cell[1] * 32 + 32))
        else:
            im = Image.open(os.path.join(TEX, "item", name + ".png")).convert("RGBA")
        im = im.resize((size * S, size * S), Image.NEAREST)
        if alpha < 1:
            im.putalpha(im.getchannel("A").point(lambda a: int(a * alpha)))
        self.im.alpha_composite(im, (int(x * S), int(y * S)))

    def circle(self, cx, cy, r, col):
        d = ImageDraw.Draw(self.im, "RGBA")
        d.ellipse((int((cx - r) * S), int((cy - r) * S), int((cx + r) * S), int((cy + r) * S)), fill=tuple(col))

    def save(self, name):
        self.im.convert("RGB").save(os.path.join(OUT, name + ".png"), optimize=True)


# ------------------------------------------------------------------ 絵柄
ST = {
    "A": dict(text=(236, 232, 220), soft=(164, 172, 200), dim=(112, 122, 152), head=(233, 196, 106), acc=(143, 208, 255),
              line=(201, 168, 92), hp1=(222, 92, 104), hp2=(160, 48, 68), mp1=(104, 168, 244), mp2=(52, 96, 190), sel=(233, 196, 106)),
    "B": dict(text=(238, 230, 210), soft=(176, 166, 160), dim=(122, 112, 112), head=(238, 230, 210), acc=(232, 124, 154),
              line=(238, 230, 210), hp1=(232, 124, 154), hp2=(232, 124, 154), mp1=(150, 196, 232), mp2=(150, 196, 232), sel=(232, 124, 154)),
    "C": dict(text=(54, 42, 32), soft=(110, 92, 72), dim=(150, 132, 106), head=(34, 110, 108), acc=(186, 70, 48),
              line=(78, 58, 40), hp1=(196, 76, 54), hp2=(160, 52, 40), mp1=(58, 124, 160), mp2=(40, 92, 130), sel=(186, 70, 48)),
}
INK = (20, 18, 22)
CREAM = (238, 230, 210)
PAPER = (232, 220, 190)


def paper(c, x, y, w, h, base=PAPER, seed=1):
    c.fill(x, y, w, h, base)
    rnd = random.Random(seed)
    for _ in range(int(w * h / 14)):
        px, py = x + rnd.random() * (w - 1), y + rnd.random() * (h - 1)
        c.fill(px, py, 1, 0.5, (120, 96, 60, 28))


def panel(c, st, x, y, w, h, title=None):
    t = ST[st]
    if st == "A":
        c.grad(x, y, w, h, (12, 16, 34, 236), (22, 28, 56, 236))
        rnd = random.Random(7)
        for _ in range(26):
            c.fill(x + 6 + rnd.random() * (w - 12), y + 5 + rnd.random() * 18, 0.5, 0.5, (200, 214, 255, rnd.choice((90, 140, 200))))
        c.box(x, y, w, h, (5, 7, 16))
        c.box(x + 2, y + 2, w - 4, h - 4, t["line"])
        for cx, cy in ((x + 2, y + 2), (x + w - 3, y + 2), (x + 2, y + h - 3), (x + w - 3, y + h - 3)):
            c.diamond(cx, cy, 3, (5, 7, 16))
            c.diamond(cx, cy, 2, t["line"])
        c.fill(x + 8, y + 27, w - 16, 0.5, t["line"] + (150,))
        c.diamond(x + w // 2, y + 27, 2, t["line"])
    elif st == "B":
        c.fill(x + 3, y + 3, w, h, t["acc"])
        c.fill(x, y, w, h, CREAM)
        c.fill(x + 2, y + 2, w - 4, h - 4, INK)
        c.fill(x + 2, y + 2, w - 4, 24, CREAM)
        for i in range(x + 2, x + w - 2, 4):
            c.fill(i, y + 26, 2, 1, CREAM)
            c.fill(i + 0.5, y + 27, 1, 1, CREAM)
        for cx in (x + 10, x + w - 11):
            c.diamond(cx, y + 14, 3, t["acc"])
    else:
        c.fill(x + 2, y + 2, w, h, (30, 22, 14, 90))
        paper(c, x, y, w, h)
        c.box(x, y, w, h, t["line"])
        c.box(x + 3, y + 3, w - 6, h - 6, (160, 132, 96))
        for cx, cy, sx, sy in ((x + 1, y + 1, 1, 1), (x + w - 2, y + 1, -1, 1), (x + 1, y + h - 2, 1, -1), (x + w - 2, y + h - 2, -1, -1)):
            for i in range(7):
                c.fill(cx if sx > 0 else cx - (6 - i), cy + sy * i, 7 - i, 1, t["head"])
        c.fill(x + 8, y + 26, w - 16, 1, t["line"])
        c.fill(x + 8, y + 28, w - 16, 0.5, (160, 132, 96))
    if title:
        col = INK if st == "B" else t["head"] if st == "A" else t["text"]
        c.text(x + w - 12 - (8 if st == "B" else 0), y + 9, title, col, TITLE, anchor="r")


def tabs(c, st, x, y, names, on):
    t = ST[st]
    for i, n in enumerate(names):
        w = 34
        bx = x + i * (w + 3)
        if st == "A":
            if i == on:
                c.fill(bx, y, w, 13, (233, 196, 106, 40))
                c.fill(bx, y + 12, w, 1, t["head"])
            c.text(bx + w / 2, y + 3, n, t["head"] if i == on else t["soft"], anchor="c")
        elif st == "B":
            if i == on:
                c.fill(bx, y, w, 15, INK)
                c.text(bx + w / 2, y + 3, n, CREAM, anchor="c")
            else:
                c.box(bx, y, w, 13, INK)
                c.text(bx + w / 2, y + 3, n, INK, anchor="c")
        else:
            if i == on:
                c.fill(bx, y, w, 13, t["head"])
                c.text(bx + w / 2, y + 3, n, PAPER, anchor="c")
            else:
                c.box(bx, y, w, 13, (160, 132, 96))
                c.text(bx + w / 2, y + 3, n, t["soft"], anchor="c")


def recess(c, st, x, y, w, h):
    if st == "A":
        c.fill(x, y, w, h, (4, 6, 16, 150))
        c.box(x, y, w, h, (54, 64, 100))
    elif st == "B":
        c.fill(x, y, w, h, (36, 32, 38))
    else:
        paper(c, x, y, w, h, (216, 202, 168), 3)
        c.box(x, y, w, h, (160, 132, 96))


def select(c, st, x, y, w, h):
    t = ST[st]
    if st == "A":
        c.fill(x, y, w, h, (143, 208, 255, 38))
        c.fill(x, y, 2, h, t["head"])
    elif st == "B":
        c.fill(x, y, w, h, t["acc"])
    else:
        c.fill(x, y, w, h, (34, 110, 108, 50))
        c.fill(x, y, 2, h, t["acc"])


def button(c, st, x, y, w, label, primary=False):
    t = ST[st]
    if st == "A":
        if primary:
            c.grad(x, y, w, 13, (240, 208, 124), (198, 160, 80))
            c.text(x + w / 2, y + 3, label, (24, 20, 30), anchor="c")
        else:
            c.box(x, y, w, 13, t["line"])
            c.text(x + w / 2, y + 3, label, t["text"], anchor="c")
    elif st == "B":
        if primary:
            c.fill(x + 2, y + 2, w, 13, t["acc"])
            c.fill(x, y, w, 13, CREAM)
            c.text(x + w / 2, y + 3, label, INK, anchor="c")
        else:
            c.box(x, y, w, 13, CREAM)
            c.text(x + w / 2, y + 3, label, CREAM, anchor="c")
    else:
        if primary:
            c.fill(x, y, w, 13, t["head"])
            c.text(x + w / 2, y + 3, label, PAPER, anchor="c")
        else:
            c.box(x, y, w, 13, t["line"])
            c.text(x + w / 2, y + 3, label, t["text"], anchor="c")


def slot(c, st, x, y, size=18, sel=False, hud=False, rarity=None):
    t = ST[st]
    if st == "A":
        c.fill(x, y, size, size, (6, 8, 20, 200 if hud else 170))
        c.box(x, y, size, size, (62, 72, 110))
    elif st == "B":
        c.fill(x, y, size, size, INK if hud else (36, 32, 38))
        c.box(x, y, size, size, (110, 102, 100))
    else:
        if hud:
            c.fill(x, y, size, size, (226, 212, 180, 235))
        else:
            c.fill(x, y, size, size, (206, 190, 154))
        c.box(x, y, size, size, (150, 124, 90))
    if rarity:
        c.fill(x + 1, y + size - 2, size - 2, 1, rarity)
    if sel:
        c.box(x - 1, y - 1, size + 2, size + 2, t["sel"])
        if st != "C":
            c.box(x - 2, y - 2, size + 4, size + 4, (0, 0, 0, 120))


def bar(c, st, x, y, w, h, frac, kind, label=None, right=False):
    t = ST[st]
    c1, c2 = (t["hp1"], t["hp2"]) if kind == "hp" else (t["mp1"], t["mp2"])
    if st == "A":
        c.fill(x, y, w, h, (5, 7, 16, 215))
        c.box(x, y, w, h, (5, 7, 16))
    elif st == "B":
        c.fill(x, y, w, h, INK)
    else:
        c.fill(x, y, w, h, (226, 212, 180, 240))
        c.box(x, y, w, h, t["line"])
    fw = (w - 2) * frac
    fx = x + 1 if not right else x + w - 1 - fw
    c.grad(fx, y + 1, fw, h - 2, c1, c2)
    if st == "A":
        c.fill(fx, y + 1, fw, 1, (255, 255, 255, 70))
    if label:
        col = (250, 246, 238)
        sh = (0, 0, 0)
        if right:
            c.text(x + 3, y + (h - 8) / 2, label, col, shadow=sh)
        else:
            c.text(x + w - 3, y + (h - 8) / 2, label, col, shadow=sh, anchor="r")


RAR = {"r": (86, 156, 240), "e": (176, 110, 232), "l": (240, 190, 80), "u": (110, 200, 120)}
HOTBAR = ["storm_sword", "ember_dagger", "storm_staff", "ember_bow", None, "dried_fish", "antidote", None, "bell_coin"]
BAG = ["iron_lump", "copper_lump", "clay_lump", "bone_chip", "echo_shard", "ember_shard", "storm_crystal_shard", "crescent_shell", "cloud_jerky",
       "ash_baked_bread", "charcoal_lump", "copper_wire", "bubble_sac", None, None, None, None, None,
       "box_rare", "box_epic", None, None, None, None, None, None, None]
BAGR = {0: None, 4: "r", 5: "r", 6: "e", 18: "r", 19: "e"}


# ------------------------------------------------------------------ 画面: クエストログ
def scene_quest(st):
    t = ST[st]
    c = C("capital_dusk.webp", 0.55)
    x, y, w, h = 62, 25, 356, 220
    panel(c, st, x, y, w, h, "クエストログ")
    tabs(c, st, x + 10, y + 9, ["メイン", "サブ", "終えた"], 0)
    lx, ly, lw = x + 8, y + 34, 128
    recess(c, st, lx, ly, lw, 152)
    rows = [("序章　空の影", True, 0), ("　野営地の跡を調べる", False, 1), ("一章　岩鯨の歌", False, 0), ("　骨を負う獣", False, 2), ("　語り部の頼み", False, 2),
            ("今日・今週", False, 3), ("　言伝て：潮だまりの荷", False, 2)]
    for i, (s, head, k) in enumerate(rows):
        ry = ly + 3 + i * 13
        if i == 1:
            select(c, st, lx + 1, ry, lw - 2, 13)
        col = t["head"] if k == 0 else (INK if st == "B" and i == 1 else t["text"]) if k == 1 else t["soft"] if k == 2 else t["acc"]
        c.text(lx + 5, ry + 3, s, col)
    rx, rw = lx + lw + 5, w - 16 - lw - 5
    recess(c, st, rx, ly, rw, 152)
    c.text(rx + 8, ly + 6, "野営地の跡を調べる", t["head"] if st != "C" else t["head"], TITLE)
    c.fill(rx + 8, ly + 23, rw - 16, 0.5, t["dim"])
    body = ["浜に上がった夜、空を大きな影が渡った。", "朝、丘の上に焚き火の跡が残っている。", "誰かがここで何かを待っていたらしい。"]
    for i, s in enumerate(body):
        c.text(rx + 8, ly + 30 + i * 13, s, t["text"])
    c.text(rx + 8, ly + 76, "目的", t["acc"])
    for i, (s, done) in enumerate([("野営地の跡へ行く", True), ("焚き火の灰を調べる（0/1）", False), ("語り部に知らせる", False)]):
        yy = ly + 89 + i * 13
        c.diamond(rx + 12, yy + 4, 2, t["head"] if done else t["dim"])
        c.text(rx + 19, yy, s, t["dim"] if done else t["text"])
    c.text(rx + 8, ly + 132, "報酬", t["acc"])
    c.icon("bell_coin", rx + 34, ly + 128, 16)
    c.text(rx + 52, ly + 132, "120　経験値 80", t["text"])
    c.text(x + 10, y + h - 21, "[B] 伝承の書", t["dim"])
    button(c, st, x + w - 8 - 89 - 94, y + h - 23, 89, "地図で見る")
    button(c, st, x + w - 8 - 89, y + h - 23, 89, "追う", True)
    c.save("quest_" + st)


# ------------------------------------------------------------------ 画面: 持ち物 (体力・マナ・装備・袋を一枚に)
def scene_inv(st):
    t = ST[st]
    c = C("capital_dusk.webp", 0.55)
    x, y, w, h = 62, 25, 356, 220
    panel(c, st, x, y, w, h, "持ち物")
    tabs(c, st, x + 10, y + 9, ["持ち物", "装飾", "旅袋", "封の箱"], 0)
    # 左: わたし
    lx, ly, lw = x + 8, y + 34, 128
    recess(c, st, lx, ly, lw, 178)
    c.text(lx + 6, ly + 6, "ユラ", t["head"], TITLE)
    c.text(lx + lw - 6, ly + 9, "Lv 32 魔導士", t["soft"], anchor="r")
    # 装備 4 + 人形 + 装飾 4
    gear = ["ember_helmet", "ember_chestplate", "ember_leggings", "ember_boots"]
    acc = ["storm_charm", "tide_chime_charm", "storm_anklet", None]
    for i in range(4):
        slot(c, st, lx + 6, ly + 26 + i * 20, 18, rarity=RAR["e"] if i < 2 else RAR["r"])
        c.icon(gear[i], lx + 7, ly + 27 + i * 20)
        slot(c, st, lx + lw - 24, ly + 26 + i * 20, 18, rarity=RAR["r"] if acc[i] else None)
        if acc[i]:
            c.icon(acc[i], lx + lw - 23, ly + 27 + i * 20)
    fx, fy = lx + 29, ly + 26
    c.fill(fx, fy, 70, 78, (0, 0, 0, 90) if st != "C" else (120, 96, 60, 50))
    body = (70, 78, 100) if st != "C" else (120, 100, 76)
    for (bx, by, bw, bh) in ((27, 8, 16, 16), (25, 25, 20, 24), (17, 25, 7, 22), (46, 25, 7, 22), (26, 50, 8, 24), (36, 50, 8, 24)):
        c.fill(fx + bx, fy + by, bw, bh, body)
    # 体力・マナ・経験値
    by = ly + 110
    c.text(lx + 6, by + 1, "体力", t["soft"])
    bar(c, st, lx + 30, by, lw - 36, 10, 0.82, "hp", "2,610 / 3,200")
    c.text(lx + 6, by + 15, "マナ", t["soft"])
    bar(c, st, lx + 30, by + 14, lw - 36, 10, 0.6, "mp", "84 / 140")
    c.text(lx + 6, by + 29, "経験", t["soft"])
    c.fill(lx + 30, by + 31, lw - 36, 4, (0, 0, 0, 120) if st != "C" else (180, 160, 124))
    c.fill(lx + 30, by + 31, (lw - 36) * 0.44, 4, t["head"])
    stats = [("攻撃", "412"), ("守り", "186"), ("会心", "18%"), ("銭", "12,480")]
    for i, (k, v) in enumerate(stats):
        sx = lx + 6 + (i % 2) * 60
        sy = by + 42 + (i // 2) * 13
        c.text(sx, sy, k, t["soft"])
        c.text(sx + 54, sy, v, t["text"], anchor="r")
    # 右: 袋
    rx, rw = lx + lw + 5, w - 16 - lw - 5
    recess(c, st, rx, ly, rw, 178)
    c.text(rx + 8, ly + 7, "袋", t["acc"])
    c.text(rx + rw - 8, ly + 7, "13 / 27", t["dim"], anchor="r")
    gx = rx + (rw - 9 * 21 + 3) // 2
    for i in range(27):
        sx, sy = gx + (i % 9) * 21, ly + 21 + (i // 9) * 21
        r = BAGR.get(i)
        slot(c, st, sx, sy, 18, rarity=RAR[r] if r else None)
        if BAG[i]:
            c.icon(BAG[i], sx + 1, sy + 1)
    c.fill(rx + 8, ly + 89, rw - 16, 0.5, t["dim"])
    c.text(rx + 8, ly + 95, "手もと", t["acc"])
    for i in range(9):
        sx, sy = gx + i * 21, ly + 108
        slot(c, st, sx, sy, 18, sel=(i == 0), rarity=RAR["l"] if i == 0 else None)
        if HOTBAR[i]:
            c.icon(HOTBAR[i], sx + 1, sy + 1)
        c.text(sx + 17, sy + 10, str(i + 1), t["dim"], anchor="r")
    c.text(rx + 8, ly + 135, "技", t["acc"])
    for i in range(4):
        sx, sy = gx + i * 21, ly + 148
        slot(c, st, sx, sy, 18)
        c.icon(None, sx + 1, sy + 1, sheet="gui/skill/icons.png", cell=(i, 0))
        c.text(sx + 17, sy + 10, "RGVZ"[i], t["dim"], anchor="r")
    button(c, st, rx + rw - 8 - 55, ly + 153, 55, "整える")
    c.save("inv_" + st)


# ------------------------------------------------------------------ HUD
def hud_common(c, st):
    t = ST[st]
    # 左上: 目的 (どの案でも同じ置き場)
    if st == "A":
        c.fill(6, 8, 150, 30, (10, 14, 30, 170))
        c.fill(6, 8, 2, 30, t["head"])
    elif st == "B":
        c.fill(6, 8, 150, 30, INK)
        c.fill(6, 8, 3, 30, t["acc"])
    else:
        paper(c, 6, 8, 150, 30)
        c.fill(6, 8, 2, 30, t["acc"])
    c.text(13, 12, "序章　空の影", t["head"])
    c.text(13, 25, "野営地の跡を調べてみよう", t["text"])
    c.fill(239, 131, 3, 9, (255, 255, 255, 220))
    c.fill(236, 134, 9, 3, (255, 255, 255, 220))


def hud_slots(c, st, x, y, items, sel=0, size=20):
    for i, it in enumerate(items):
        sx = x + i * size
        slot(c, st, sx, y, size, sel=(i == sel), hud=True)
        if it:
            c.icon(it, sx + 2, y + 2)
            if it in ("dried_fish", "antidote"):
                c.text(sx + size - 1, y + size - 9, "12" if it == "dried_fish" else "3", (250, 246, 238), shadow=(0, 0, 0), anchor="r")


def hud_skills(c, st, x, y, size=20, cd=True):
    for i in range(4):
        sx = x + i * (size + 1)
        slot(c, st, sx, y, size, hud=True)
        c.icon(None, sx + 2, y + 2, sheet="gui/skill/icons.png", cell=(i, 0))
        if cd and i == 2:
            c.fill(sx + 2, y + 2, 16, 11, (10, 12, 20, 180))
            c.text(sx + size / 2, y + 6, "4", (250, 246, 238), shadow=(0, 0, 0), anchor="c")
        c.text(sx + size - 1, y + size - 9, "RGVZ"[i], (200, 200, 214), shadow=(0, 0, 0), anchor="r")


def frame(c, st, x, y, w, h):
    t = ST[st]
    if st == "A":
        c.fill(x, y, w, h, (10, 14, 30, 200))
        c.box(x, y, w, h, (5, 7, 16))
        c.box(x + 1, y + 1, w - 2, h - 2, t["line"])
    elif st == "B":
        c.fill(x + 2, y + 2, w, h, t["acc"])
        c.fill(x, y, w, h, CREAM)
        c.fill(x + 2, y + 2, w - 4, h - 4, INK)
    else:
        c.fill(x + 1, y + 1, w, h, (30, 22, 14, 90))
        paper(c, x, y, w, h)
        c.box(x, y, w, h, t["line"])


def lv(c, st, x, y):
    t = ST[st]
    c.fill(x, y, 17, 10, t["line"] if st != "B" else CREAM)
    c.fill(x + 1, y + 1, 15, 8, (10, 14, 30) if st == "A" else INK if st == "B" else PAPER)
    c.text(x + 8.5, y + 1, "32", t["head"] if st != "B" else CREAM, anchor="c")


def scene_hud(st, layout, name=None):
    t = ST[st]
    c = C("inland.webp")
    hud_common(c, st)
    if layout == 1:
        # 一枚の帯: 体力 | Lv | マナ の段がホットバーの上に載る。技は右に続く
        x, y = 135, 229
        frame(c, st, x, y, 286, 39)
        bar(c, st, x + 4, y + 4, 81, 10, 0.82, "hp", "2,610")
        lv(c, st, x + 86, y + 4)
        bar(c, st, x + 104, y + 4, 81, 10, 0.6, "mp", "84", right=True)
        hud_slots(c, st, x + 4, y + 16, HOTBAR)
        # 型の溜め (マナの上の点)
        for i in range(5):
            c.fill(x + 190 + i * 9, y + 6, 8, 3, t["acc"] if i < 3 else (60, 64, 80))
        c.text(x + 190, y + 9 - 8, "", t["dim"])
        hud_skills(c, st, x + 198, y + 16)
        c.fill(x + 190, y + 16, 0.5, 20, t["line"] + (160,))
        # 経験値: 帯の下の辺
        c.fill(x + 2, y + 37, 282, 1, (0, 0, 0, 160))
        c.fill(x + 2, y + 37, 282 * 0.44, 1, t["head"])
    elif layout == 2:
        # 両翼の玉: 体力の玉とマナの玉がホットバーをはさむ
        x, y = 149, 244
        frame(c, st, x - 2, y - 2, 184, 24)
        hud_slots(c, st, x, y, HOTBAR)
        for cx, kind, frac, num in ((x - 22, "hp", 0.82, "2,610"), (x + 202, "mp", 0.6, "84")):
            rim = t["line"] if st != "B" else CREAM
            c.circle(cx, 248, 20, (5, 7, 16) if st != "C" else t["line"])
            c.circle(cx, 248, 19, rim)
            c.circle(cx, 248, 17, (8, 10, 22) if st != "C" else (206, 190, 154))
            # 中身: 下から frac まで
            orb = Image.new("RGBA", c.im.size, (0, 0, 0, 0))
            d = ImageDraw.Draw(orb)
            c1 = t["hp1"] if kind == "hp" else t["mp1"]
            c2 = t["hp2"] if kind == "hp" else t["mp2"]
            d.ellipse(((cx - 16) * S, (248 - 16) * S, (cx + 16) * S, (248 + 16) * S), fill=c2 + (255,))
            d.ellipse(((cx - 14) * S, (248 - 15) * S, (cx + 10) * S, (248 + 9) * S), fill=c1 + (255,))
            top = int((248 + 16 - 32 * frac) * S)
            orb.paste((0, 0, 0, 0), (0, 0, orb.size[0], top))
            c.im.alpha_composite(orb)
            c.fill(cx - 9, 239, 5, 2, (255, 255, 255, 110))
            c.text(cx, 245, num, (250, 246, 238), shadow=(0, 0, 0), anchor="c")
        # Lv の菱形 (ホットバーの上の真ん中) と経験値
        lv(c, st, 232, 231)
        c.fill(x, y + 21, 180, 1, (0, 0, 0, 160))
        c.fill(x, y + 21, 180 * 0.44, 1, t["head"])
        # 技: マナの玉の右
        frame(c, st, x + 226, y - 2, 4 * 21 + 3, 24)
        hud_skills(c, st, x + 228, y)
        for i in range(5):
            c.fill(x + 228 + i * 9, y - 8, 8, 3, t["acc"] if i < 3 else (60, 64, 80))
    else:
        # 一列: 持ち物 9 と技 4 が一本。体力は持ち物の上、マナは技の上
        x, y = 104, 232
        frame(c, st, x, y, 272, 36)
        lv(c, st, x + 3, y + 3)
        bar(c, st, x + 22, y + 3, 161, 10, 0.82, "hp", "2,610 / 3,200")
        bar(c, st, x + 187, y + 3, 82, 10, 0.6, "mp", "84 / 140")
        hud_slots(c, st, x + 3, y + 14, HOTBAR)
        c.fill(x + 185, y + 14, 0.5, 20, t["line"] + (160,))
        hud_skills(c, st, x + 187, y + 14, cd=True)
        for i in range(5):
            c.fill(x + 187 + i * 9, y - 5, 8, 3, t["acc"] if i < 3 else (60, 64, 80))
        c.fill(x + 2, y + 34, 268, 1, (0, 0, 0, 160))
        c.fill(x + 2, y + 34, 268 * 0.44, 1, t["head"])
    c.save(name or "hud%d_%s" % (layout, st))


if __name__ == "__main__":
    for st in "ABC":
        scene_quest(st)
        scene_inv(st)
        scene_hud(st, 1)
    for lay in (2, 3):
        scene_hud("A", lay)
    print("ok")
