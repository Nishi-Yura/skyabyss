# -*- coding: utf-8 -*-
"""留め袋 (docs/design/22_mod_pouch.md) の画面ラフ。GUI の 1px = 画像の 2px。
出力: docs/review/_gallery_pouch/ (個別 PNG と比較シート)
  python tools/gen_pouch_mockups.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/skyabyss/textures/item"
OUT = ROOT / "docs/review/_gallery_pouch"
S = 2

BG = (16, 18, 22)
PANEL = (26, 30, 39)
SLOT = (12, 14, 18)
SLOT_HI = (44, 52, 64)
BONE = (226, 217, 194)
BONE_D = (150, 140, 118)
TEAL = (42, 94, 106)
TEAL_L = (95, 220, 239)
GOLD = (230, 180, 60)
INK = (60, 44, 24)
PARCH = (222, 208, 176)
PARCH_D = (196, 180, 144)
TXT = (214, 222, 230)
DIM = (120, 132, 144)
RED = (170, 70, 110)


def font(sz):
    for p in ("C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/BIZ-UDGothicR.ttc", "C:/Windows/Fonts/YuGothM.ttc",
              "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf"):
        try:
            return ImageFont.truetype(p, sz)
        except OSError:
            pass
    return ImageFont.load_default()


F = font(16)
FS = font(14)


class Canvas:
    def __init__(self, w, h, bg=BG):
        self.im = Image.new("RGB", (w * S, h * S), bg)
        self.d = ImageDraw.Draw(self.im)
        self.d.fontmode = "1"

    def rect(self, x, y, w, h, fill, outline=None):
        self.d.rectangle([x * S, y * S, (x + w) * S - 1, (y + h) * S - 1], fill=fill, outline=outline)

    def text(self, x, y, s, color=TXT, f=F, anchor="la"):
        self.d.text((x * S, y * S), s, fill=color, font=f, anchor=anchor)

    def icon(self, x, y, name, dark=False, alpha=255):
        """16x16 の GUI 品を (x, y) へ。元は 32px の絵なので 16 へ縮めて 2 倍で描く"""
        p = ITEMS / f"{name}.png"
        if not p.exists():
            self.text(x + 5, y + 3, "?", DIM)
            return
        im = Image.open(p).convert("RGBA").resize((16, 16), Image.BOX).resize((16 * S, 16 * S), Image.NEAREST)
        if dark:
            r, g, b, a = im.split()
            sil = Image.new("RGBA", im.size, (38, 52, 64, 255))
            sil.putalpha(a.point(lambda v: 150 if v > 40 else 0))
            im = sil
        elif alpha < 255:
            im.putalpha(im.getchannel("A").point(lambda v: v * alpha // 255))
        self.im.paste(im, (x * S, y * S), im)

    def slot(self, x, y, hi=False, gold=False):
        self.rect(x, y, 18, 18, SLOT_HI if hi else (60, 68, 80))
        self.rect(x + 1, y + 1, 16, 16, SLOT)
        if gold:
            self.d.rectangle([x * S, y * S, (x + 18) * S - 1, (y + 18) * S - 1], outline=GOLD, width=S)

    def count(self, x, y, n):
        s = str(n)
        self.text(x + 18 - 1, y + 18 - 8, s, (0, 0, 0), FS, "rm")
        self.text(x + 18 - 2, y + 18 - 9, s, (255, 255, 255), FS, "rm")

    def frame(self, x, y, w, h, fill=PANEL):
        """骨の枠 + 水色の角飾り (装飾の画面と同じ作り)"""
        self.rect(x, y, w, h, BONE_D)
        self.rect(x + 1, y + 1, w - 2, h - 2, BONE)
        self.rect(x + 3, y + 3, w - 6, h - 6, TEAL)
        self.rect(x + 4, y + 4, w - 8, h - 8, fill)
        for cx, cy in ((x + 1, y + 1), (x + w - 5, y + 1), (x + 1, y + h - 5), (x + w - 5, y + h - 5)):
            self.rect(cx, cy, 4, 4, TEAL_L)

    def tab(self, x, y, label, on):
        w = 44
        self.rect(x, y, w, 16 if on else 13, BONE if on else BONE_D)
        self.rect(x + 1, y + 1, w - 2, 15 if on else 12, PANEL if on else (34, 40, 50))
        self.text(x + w // 2, y + 8, label, GOLD if on else DIM, FS, "mm")

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.im.save(path)
        return self.im


# ------------------------------------------------------------------ 中身のデータ
# (icon, 名前, 個数, 状態)  状態: have / empty(輪郭だけ・出た章) / lock(？・まだ)
TOOLS = [("stone_whale_call", 1, "have"), ("tide_shell_call", 1, "have"), ("tide_shell_call", 0, "lock"),
         ("tide_shell_call", 0, "lock"), ("tide_watching_shell", 1, "have"), ("gale_feather", 0, "empty")]
KEYS = [("sky_key_fragment", 3, "have"), ("sky_key", 0, "empty"), ("fire_key", 0, "empty"),
        ("stone_whale_core", 1, "have"), ("tide_shell_core", 1, "have"), ("thunder_wing_core", 0, "lock"),
        ("ember_core", 0, "lock")]
RES = [("resonance_crystal", 24, "have"), ("resonance_shard_stone_whale", 12, "have"), ("resonance_shard_tide_shell", 5, "have"),
       ("resonance_shard_thunder_wing", 0, "lock"), ("resonance_shard_ember", 0, "lock")]
BOOK = [("lore_book", 1, "have"), ("lore_page", 2, "have")]
MAT1 = [("stone_whale_shard", 148, "have"), ("tide_shell_shard", 37, "have"), ("thunder_wing_shard", 0, "lock"), ("ember_shard", 0, "lock")]
MAT2 = [("moth_dust", 0, "lock"), ("bubble_sac", 9, "have"), ("spark_dust", 0, "lock"), ("cinder_husk", 0, "lock"),
        ("tide_dried_shell", 14, "have"), ("tide_pearl", 2, "have")]


def put(c, x, y, ent, hi=False, usable=False):
    name, n, st = ent
    c.slot(x, y, hi, gold=usable and st == "have")
    if st == "have":
        c.icon(x + 1, y + 1, name)
        if n > 1:
            c.count(x, y, n)
    elif st == "empty":
        c.icon(x + 1, y + 1, name, dark=True)
    else:
        c.text(x + 9, y + 9, "？", (70, 82, 96), FS, "mm")


def row(c, x, y, ents, **kw):
    for i, e in enumerate(ents):
        put(c, x + i * 18, y, e, **kw)


# ------------------------------------------------------------------ レイアウト A: つまみ (専用の枠に名札)
def layout_a():
    W, H = 176, 188
    c = Canvas(W, H + 18)
    c.frame(0, 18, W, H)
    c.tab(6, 2, "持ち物", False)
    c.tab(52, 2, "装飾", False)
    c.tab(98, 2, "留め袋", True)
    c.text(10, 26, "留め袋", GOLD)
    c.text(W - 9, 27, "潮殻の呼び貝 ・ 輪で使う", TXT, FS, "ra")
    x0 = 10
    y = 40
    c.text(x0, y, "使う道具", DIM, FS)
    c.text(x0 + 6 * 18 + 6, y + 10, "長押し [Y]", GOLD, FS)
    row(c, x0, y + 9, TOOLS, usable=True)
    y += 28
    c.text(x0, y, "鍵と心核", DIM, FS)
    row(c, x0, y + 9, KEYS)
    y += 28
    c.text(x0, y, "共鳴", DIM, FS)
    c.text(x0 + 5 * 18 + 6, y, "書", DIM, FS)
    row(c, x0, y + 9, RES)
    row(c, x0 + 5 * 18 + 6, y + 9, BOOK)
    y += 28
    c.text(x0, y, "巨神の素材", DIM, FS)
    row(c, x0, y + 9, MAT1)
    y += 28
    c.text(x0, y, "小さな素材", DIM, FS)
    row(c, x0, y + 9, MAT2)
    c.text(W // 2, H + 18 - 12, "ここの品は、倒れても残り、落とせない", DIM, FS, "mm")
    return c


# ------------------------------------------------------------------ レイアウト B: 持ち物に付ける脇のパネル
def layout_b():
    LW, W2 = 176, 104
    H = 166
    c = Canvas(LW + 3 + W2, H)
    c.frame(0, 0, LW, H)
    # 本物の持ち物の画面の代わり (人形・防具・作業台は輪郭だけ)
    c.text(8, 7, "持ち物", DIM, FS)
    for i in range(4):
        c.slot(8, 18 + i * 18)
    c.rect(30, 18, 52, 70, (20, 24, 30))
    c.rect(48, 28, 16, 16, (60, 68, 84))
    c.rect(44, 46, 24, 30, (60, 68, 84))
    c.slot(84, 62)
    c.text(108, 7, "作業", DIM, FS)
    for i in range(4):
        c.slot(108 + (i % 2) * 18, 18 + (i // 2) * 18)
    c.slot(150, 27)
    for r in range(3):
        for k in range(9):
            c.slot(8 + k * 18, 84 + r * 18)
    for k in range(9):
        c.slot(8 + k * 18, 142)
    # つまみの代わりに脇のパネル。右に付く (レシピ本は左へ開くので重ならない)
    px = LW + 3
    c.rect(px - 3, 12, 3, 40, BONE)
    c.frame(px, 0, W2, H)
    c.text(px + 8, 7, "留め袋", GOLD, FS)
    groups = [TOOLS, KEYS, RES + BOOK, MAT1 + MAT2]
    cols = [GOLD, TEAL_L, (170, 130, 220), (150, 150, 120)]
    flat = []
    for gi, g in enumerate(groups):
        for e in g:
            flat.append((e, gi))
    for i, (e, gi) in enumerate(flat[:32]):
        cx = px + 7 + (i % 5) * 18
        cy = 19 + (i // 5) * 18
        put(c, cx, cy, e, usable=(gi == 0))
        c.rect(px + 3, cy + 2, 2, 14, cols[gi]) if i % 5 == 0 else None
    c.text(px + 8, H - 14, "30 種・色の線は区分", DIM, FS)
    return c


# ------------------------------------------------------------------ レイアウト C: 別の画面 (キー I)
def layout_c():
    W, H = 256, 190
    c = Canvas(W, H)
    c.frame(0, 0, W, H, fill=PARCH)
    c.text(12, 9, "留め袋", INK)
    c.text(W - 12, 10, "[I] 閉じる   [Y] 長押しで輪", (110, 90, 60), FS, "ra")
    c.rect(10, 24, W - 20, 1, PARCH_D)
    cats = ["使う道具", "鍵と心核", "共鳴", "書", "巨神の素材", "小さな素材"]
    for i, n in enumerate(cats):
        y = 30 + i * 22
        on = i == 0
        c.rect(10, y, 62, 19, (198, 176, 120) if on else PARCH_D)
        if on:
            c.rect(10, y, 3, 19, GOLD)
        c.text(18, y + 4, n, INK)
    # 真ん中の枠
    gx, gy = 80, 30
    c.text(gx, gy - 2 + 0, "", INK)
    for i, e in enumerate(TOOLS):
        put(c, gx + (i % 3) * 20, gy + 2 + (i // 3) * 20, e, hi=(i == 1), usable=True)
    c.rect(gx, gy + 48, 60, 1, PARCH_D)
    c.text(gx, gy + 54, "倒れても残る品", (110, 90, 60), FS)
    c.text(gx, gy + 66, "落とせない", (110, 90, 60), FS)
    c.text(gx, gy + 78, "箱に入らない", (110, 90, 60), FS)
    # 右の説明
    rx = 148
    c.rect(rx, 30, 98, 150, (236, 224, 196))
    c.d.rectangle([rx * S, 30 * S, (rx + 98) * S - 1, 180 * S - 1], outline=PARCH_D, width=S)
    c.slot(rx + 8, 38, gold=True)
    c.icon(rx + 9, 39, "tide_shell_call")
    c.text(rx + 32, 39, "潮殻の呼び貝", INK, FS)
    c.text(rx + 32, 50, "手もと ×1", (110, 90, 60), FS)
    for i, s in enumerate(["珊瑚色の巻貝。", "吹くと潮殻が", "海から迎えに来る。", "", "使う: [Y] を長押し", "→ 輪から選んで離す", "", "戦いの最中は", "音が届かない。"]):
        c.text(rx + 8, 66 + i * 11, s, INK if i < 4 else (90, 60, 30), FS)
    # 下に今の状態の帯
    c.rect(80, 124, 60, 14, (180, 220, 200))
    c.text(110, 131, "使える", INK, FS, "mm")
    c.text(gx, 142, "旅のようす", (110, 90, 60), FS)
    c.text(gx, 154, "呼べるまで  0 秒", (110, 90, 60), FS)
    c.text(gx, 166, "運ばれた回数  4", (110, 90, 60), FS)
    return c


# ------------------------------------------------------------------ 使う画面: 背景の HUD
def scene(w=440, h=225, dim=False):
    c = Canvas(w, h)
    # 空 -> 地面
    for y in range(h):
        t = y / h
        col = (int(70 + 60 * t), int(100 + 70 * t), int(130 + 60 * t)) if y < h * 0.62 else (int(60 - 20 * (t - .62)), int(88 - 25 * (t - .62)), int(48 - 10 * (t - .62)))
        c.rect(0, y, w, 1, col)
    # 遠い岩鯨の影
    c.rect(210, 70, 120, 18, (96, 108, 124))
    c.rect(300, 62, 28, 12, (96, 108, 124))
    for i in range(7):
        c.rect(40 + i * 55, 140 - (i % 3) * 6, 40, 22, (50, 76, 44))
    # 左上の目的の帯
    c.rect(6, 6, 118, 14, (14, 16, 20))
    c.text(10, 8, "目的: 岩鯨の頭へ向かう", TXT, FS)
    # 右上の札
    c.rect(w - 106, 6, 100, 24, (14, 16, 20))
    c.text(w - 100, 9, "獲得", GOLD, FS)
    c.text(w - 100, 19, "岩鯨の鱗片 ×3", TXT, FS)
    # 体力・ホットバー
    hx = w // 2 - 91
    hy = h - 24
    for k in range(10):
        c.rect(hx + k * 8, hy - 12, 7, 7, (190, 40, 50))
        c.rect(hx + 101 + k * 8, hy - 12, 7, 7, (190, 120, 50))
    c.rect(hx, hy, 182, 22, (30, 30, 30))
    c.d.rectangle([hx * S, hy * S, (hx + 182) * S - 1, (hy + 22) * S - 1], outline=(10, 10, 10), width=S)
    for k in range(9):
        c.rect(hx + 3 + k * 20, hy + 3, 16, 16, (60, 60, 60))
    c.rect(hx - 1 + 0 * 20, hy - 1, 24, 24, (255, 255, 255)) if False else None
    c.d.rectangle([(hx - 1) * S, (hy - 1) * S, (hx + 23) * S - 1, (hy + 23) * S - 1], outline=(240, 240, 240), width=S)
    c.icon(hx + 3, hy + 3, "stone_whale_plating")
    c.icon(hx + 23, hy + 3, "cloud_jerky")
    c.count(hx + 22, hy + 3, 12)
    # 十字
    cx, cy = w // 2, h // 2 - 10
    c.rect(cx - 5, cy, 11, 1, (240, 240, 240))
    c.rect(cx, cy - 5, 1, 11, (240, 240, 240))
    if dim:
        ov = Image.new("RGB", c.im.size, (6, 8, 12))
        c.im = Image.blend(c.im, ov, 0.55)
        c.d = ImageDraw.Draw(c.im)
        c.d.fontmode = "1"
    return c, hx, hy


def use_bar():
    c, hx, hy = scene()
    # 道具帯: ホットバーの右に 6 枠。選んでいる 1 枠だけ明るく
    bx = hx + 190
    by = hy + 2
    c.rect(bx - 2, by - 2, 6 * 18 + 4, 22, (30, 30, 30))
    for i, e in enumerate(TOOLS):
        x = bx + i * 18
        st = e[2]
        c.rect(x, by, 18, 18, (70, 70, 70) if st != "have" else (84, 84, 84))
        c.rect(x + 1, by + 1, 16, 16, (36, 36, 36))
        if st == "have":
            c.icon(x + 1, by + 1, e[0])
        elif st == "empty":
            c.icon(x + 1, by + 1, e[0], dark=True)
    sel = 1
    c.d.rectangle([(bx + sel * 18 - 1) * S, (by - 1) * S, (bx + sel * 18 + 18) * S, (by + 18) * S], outline=GOLD, width=S)
    # 名札と案内 (切り替えた直後の 2 秒だけ)
    c.rect(bx - 2, by - 38, 112, 32, (14, 16, 22))
    c.text(bx + 2, by - 35, "潮殻の呼び貝", TXT, FS)
    c.text(bx + 2, by - 24, "[Y] 使う   [ ] ] 切り替え", GOLD, FS)
    c.text(bx + 2, by - 14, "休み: なし", DIM, FS)
    # 数字ではなく左右キーで回す: 余った枠に小さな矢印
    c.text(bx - 8, by + 9, "<", DIM, FS, "mm")
    c.text(bx + 6 * 18 + 8, by + 9, ">", DIM, FS, "mm")
    return c


def use_wheel():
    c, hx, hy = scene(dim=True)
    w, h = 440, 225
    cx, cy = w // 2, h // 2 - 30
    R, r = 70, 26
    n = len(TOOLS)
    sel = 1
    for i in range(n):
        a0 = -90 + i * 360 / n - 360 / n / 2
        a1 = a0 + 360 / n
        on = i == sel
        st = TOOLS[i][2]
        col = (60, 52, 22) if on else (28, 34, 44) if st == "have" else (20, 24, 30)
        c.d.pieslice([(cx - R) * S, (cy - R) * S, (cx + R) * S, (cy + R) * S], a0, a1, fill=col, outline=GOLD if on else (70, 80, 92), width=S)
    c.d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=(10, 12, 16), outline=(70, 80, 92), width=S)
    for i, e in enumerate(TOOLS):
        a = math.radians(-90 + i * 360 / n)
        ix = cx + math.cos(a) * (R + r) / 2 - 8
        iy = cy + math.sin(a) * (R + r) / 2 - 8
        if e[2] == "have":
            c.icon(int(ix), int(iy), e[0], alpha=255)
        elif e[2] == "empty":
            c.icon(int(ix), int(iy), e[0], dark=True)
        else:
            c.text(int(ix) + 8, int(iy) + 8, "？", (80, 92, 106), FS, "mm")
    c.text(cx, cy - 6, "潮殻の呼び貝", TXT, FS, "mm")
    c.text(cx, cy + 7, "使える", (120, 220, 180), FS, "mm")
    c.text(cx, cy + R + 14, "[Y] を離すと使う   Esc で取りやめ", GOLD, FS, "mm")
    c.text(cx, cy + R + 26, "マウスの向きで選ぶ。そのまま離すと前回の道具", DIM, FS, "mm")
    return c


def sheet(parts, out, title_h=18, gap=14, vertical=False):
    ims = [(lab, im) for lab, im in parts]
    if vertical:
        W = max(i.width for _, i in ims) + 2 * gap * S
        H = sum(i.height + title_h * S for _, i in ims) + gap * S * (len(ims) + 1)
    else:
        W = sum(i.width for _, i in ims) + gap * S * (len(ims) + 1)
        H = max(i.height for _, i in ims) + title_h * S + 2 * gap * S
    sh = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sh)
    d.fontmode = "1"
    x = gap * S
    y = gap * S
    for lab, im in ims:
        d.text((x, y), lab, fill=GOLD, font=F)
        sh.paste(im, (x, y + title_h * S))
        if vertical:
            y += im.height + title_h * S + gap * S
        else:
            x += im.width + gap * S
    sh.save(out)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    a = layout_a().save(OUT / "layout_a_tab.png")
    b = layout_b().save(OUT / "layout_b_sidepanel.png")
    cc = layout_c().save(OUT / "layout_c_screen.png")
    sheet([("A  つまみ「留め袋」(専用の枠に名札)", a), ("B  持ち物の脇のパネル", b), ("C  別の画面 (キー I)", cc)], OUT / "pouch_layout_32.png")
    u1 = use_bar().save(OUT / "use_a_bar.png")
    u2 = use_wheel().save(OUT / "use_b_wheel.png")
    sheet([("A  道具帯 (ホットバーの右・[Y] で使う・[ ] ] で切り替え)", u1), ("B  輪 ([Y] を長押し → 向きで選ぶ → 離して使う)", u2)],
          OUT / "pouch_use_32.png", vertical=True)
    print("ok")


if __name__ == "__main__":
    main()
