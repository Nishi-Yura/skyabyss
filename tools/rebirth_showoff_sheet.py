"""転生の見せ方の一覧の絵 (docs/review/previews/rebirth_showoff_sheet.png)。
実機の撮影ではなく、実際の色の計算 (RebirthTier と同じ値)・実際の画像・実際の配置寸法から描いた見本。
  python tools/rebirth_showoff_sheet.py
"""
import os
import io
import math

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures")
OUT = os.path.join(ROOT, "docs", "review", "previews", "rebirth_showoff_sheet.png")

BG = (24, 28, 33)
PANEL = (33, 39, 45)
INK = (214, 220, 224)
SOFT = (140, 152, 160)
LINE = (64, 74, 82)

WHITE = (0xE9, 0xED, 0xF0)
TEAL = (0x4F, 0xD0, 0xC4)
TEAL_T = (0x9F, 0xF3, 0xEA)
GOLD = (0xE6, 0xB8, 0x4A)
GOLD_T = (0xFF, 0xE0, 0x8A)
PRISM = [(0xFF, 0x7A, 0xA8), (0xFF, 0xD4, 0x5A), (0x6D, 0xF2, 0xB5), (0x6C, 0xB7, 0xFF), (0xC1, 0x8C, 0xFF)]


def font(name, size):
    for base in ("C:/Windows/Fonts/",):
        try:
            return ImageFont.truetype(base + name, size)
        except OSError:
            continue
    return ImageFont.load_default()


JP = font("meiryo.ttc", 15)
JP_S = font("meiryo.ttc", 12)
JP_B = font("meiryob.ttc", 17)
JP_L = font("meiryob.ttc", 26)
SYM = font("seguisym.ttf", 18)
SYM_L = font("seguisym.ttf", 28)


def prism(t, off=0.0):
    x = ((t % 1.0) + off) % 1.0 * len(PRISM)
    i = int(x) % len(PRISM)
    f = x - int(x)
    a, b = PRISM[i], PRISM[(i + 1) % len(PRISM)]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def symbols(n):
    return "✺" * (n // 10) + "✧" * ((n % 10) // 5) + "✦" * (n % 5)


def tier(n):
    return 3 if n >= 15 else 2 if n >= 5 else 1 if n >= 1 else 0


def tag_colors(n):
    return [WHITE, TEAL_T, GOLD_T, prism(0.1)][tier(n)], [WHITE, TEAL, GOLD, prism(0.1)][tier(n)]


def text_w(d, s, f):
    return d.textlength(s, font=f)


def draw_tag(d, x, y, n, name="Yura", near=True):
    """名札: 記号 + 名前 (+ Lv 50+n)。近く (5 マス以内) だけ縁の点・Lv。"""
    text_c, acc = tag_colors(n)
    sym = symbols(n)
    lv = "Lv 50" if n == 0 else "Lv 50+%d" % n
    parts = []
    if sym:
        parts.append((sym, SYM, acc))
    parts.append((name, JP, WHITE))
    if near:
        parts.append((lv, JP_S, text_c if n else WHITE))
    w = sum(text_w(d, p[0], p[1]) + 6 for p in parts) + 10
    h = 24
    x0 = x - w / 2
    d.rectangle([x0, y, x0 + w, y + h], fill=(10, 14, 18))
    st = [0, 1, 2, 3][tier(n)]
    col = acc
    if st >= 1:
        d.rectangle([x0, y, x0 + w, y + h], outline=col, width=1)
    if st >= 2:
        d.rectangle([x0 - 2, y - 2, x0 + w + 2, y + h + 2], outline=tuple(int(c * 0.6) for c in col), width=1)
    if st >= 3:
        for cx, cy in ((x0 - 3, y - 3), (x0 + w, y - 3), (x0 - 3, y + h), (x0 + w, y + h)):
            d.rectangle([cx, cy, cx + 3, cy + 3], fill=prism(0.3 + 0.1 * (cx % 3)))
    cx = x0 + 6
    for s, f, c in parts:
        d.text((cx, y + 3), s, font=f, fill=c)
        cx += text_w(d, s, f) + 6
    return w


def player(d, x, y, scale=3, n=0, decor=True, side=False):
    """プレイヤーの後ろ姿 (箱)。n >= 5 で紋章、10 で布、20 で羽 (寸法は BackDecorLayer と同じ比)。"""
    s = scale
    skin, cloth, leg = (176, 140, 116), (62, 96, 128), (52, 58, 86)
    d.rectangle([x - 4 * s, y, x + 4 * s, y + 8 * s], fill=skin)  # 頭
    d.rectangle([x - 4 * s, y + 8 * s, x + 4 * s, y + 20 * s], fill=cloth)  # 体
    d.rectangle([x - 8 * s, y + 8 * s, x - 4 * s, y + 20 * s], fill=cloth)
    d.rectangle([x + 4 * s, y + 8 * s, x + 8 * s, y + 20 * s], fill=cloth)
    d.rectangle([x - 4 * s, y + 20 * s, x, y + 32 * s], fill=leg)
    d.rectangle([x, y + 20 * s, x + 4 * s, y + 32 * s], fill=leg)
    if not decor or n < 5:
        return
    acc = [WHITE, TEAL, GOLD, prism(0.15)][tier(n)]
    if n >= 20:
        for sd in (-1, 1):
            for L, ang in ((10, 20), (12, 46), (11, 70), (8, 92)):
                a = math.radians(ang)
                px, py = x + sd * 2.4 * s, y + 11 * s
                ex, ey = px + sd * math.sin(a) * L * s, py - math.cos(a) * L * s
                d.line([px, py, ex, ey], fill=tuple(min(255, int(c * 0.9 + 25)) for c in acc), width=max(2, int(1.6 * s)))
    if n >= 10:
        d.rectangle([x - 4 * s, y + 8.5 * s, x + 4 * s, y + 19 * s], fill=tuple(int(c * 0.6) for c in acc))
        d.rectangle([x - 4 * s, y + 17.5 * s, x + 4 * s, y + 19 * s], fill=(235, 235, 235))
    cx, cy, r = x, y + 13 * s, 3 * s
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=acc, outline=(40, 44, 50))


def main():
    W, H = 1400, 1100
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((28, 20), "転生の見せ方 (C 案の実装)", font=JP_L, fill=INK)
    d.text((28, 56), "色の段・記号・名札・背中の飾り・順位の石・飾り板・墓の飾り。実機の撮影ではなく、実際の色の計算と画像から描いた見本", font=JP_S, fill=SOFT)

    # --- 1 段目: 名札の段
    y0 = 92
    d.rounded_rectangle([20, y0, W - 20, y0 + 250], radius=6, fill=PANEL)
    d.text((36, y0 + 10), "色の段と名札 (C01・C02・C03)", font=JP_B, fill=INK)
    ns = [(0, "白・なし"), (1, "青緑 1"), (4, "青緑 4"), (5, "金 5"), (12, "金 12"), (15, "プリズム 15"), (20, "プリズム 20 (上限)")]
    cw = (W - 80) // len(ns)
    for i, (n, label) in enumerate(ns):
        cx = 40 + cw * i + cw // 2
        sky = Image.new("RGB", (cw - 10, 150), (40, 62, 86))
        sd = ImageDraw.Draw(sky)
        for yy in range(150):
            f = yy / 150
            sd.line([0, yy, cw, yy], fill=(int(46 + 50 * f), int(72 + 60 * f), int(104 + 40 * f)))
        sd.rectangle([0, 118, cw, 150], fill=(70, 92, 66))
        img.paste(sky, (cx - cw // 2 + 5, y0 + 44))
        draw_tag(d, cx, y0 + 58, n)
        player(d, cx, y0 + 96, 2, n, decor=False)
        d.text((cx - text_w(d, label, JP_S) / 2, y0 + 202), label, font=JP_S, fill=INK)
        if n:
            d.text((cx - text_w(d, "チャット ✦%d" % n, JP_S) / 2, y0 + 222), "チャット ✦%d" % n, font=JP_S, fill=tag_colors(n)[1])
    d.text((W - 640, y0 + 12), "5 マスより遠くは記号と色だけ。飾りの点・Lv・称号は近くだけ。縁の形も段で変わる", font=JP_S, fill=SOFT)

    # --- 2 段目: 背中の飾り + 画像
    y1 = y0 + 266
    d.rounded_rectangle([20, y1, 700, y1 + 250], radius=6, fill=PANEL)
    d.text((36, y1 + 10), "背中の飾り (C08): 5 紋章 / 10 布 / 20 光の羽", font=JP_B, fill=INK)
    for i, (n, label) in enumerate([(0, "なし"), (5, "5 回"), (10, "10 回"), (20, "20 回")]):
        cx = 100 + i * 150
        player(d, cx, y1 + 56, 4, n)
        d.text((cx - text_w(d, label, JP_S) / 2, y1 + 222), label, font=JP_S, fill=INK)
    back = Image.open(os.path.join(TEX, "entity", "rebirth_back.png")).convert("RGBA")
    big = back.resize((128, 128), Image.NEAREST)
    d.rounded_rectangle([716, y1, W - 20, y1 + 250], radius=6, fill=PANEL)
    d.text((732, y1 + 10), "素材 (32×32・灰色に段の色を掛ける)", font=JP_B, fill=INK)
    bg = Image.new("RGBA", big.size, (50, 56, 62, 255))
    bg.alpha_composite(big)
    img.paste(bg.convert("RGB"), (732, y1 + 44))
    for k, (name, f) in enumerate((("block/rebirth_stone.png", 128), ("block/record_board.png", 128), ("block/record_board_fresh.png", 128))):
        t = Image.open(os.path.join(TEX, name)).convert("RGB").resize((f, f), Image.NEAREST)
        img.paste(t, (732 + 150 * (k + 1), y1 + 44))
    labels = ["背の飾り 4 区画", "順位の石", "飾り板", "飾り板 (更新で光る)"]
    for k, lab in enumerate(labels):
        d.text((732 + 150 * k, y1 + 182), lab, font=JP_S, fill=SOFT)

    # --- 3 段目: 順位の石・飾り板・一覧
    y2 = y1 + 266
    d.rounded_rectangle([20, y2, 700, y2 + 270], radius=6, fill=PANEL)
    d.text((36, y2 + 10), "順位の石 (C11): 上位 3 人だけ大きく", font=JP_B, fill=INK)
    cols = [("転生の数", [("Yura", 12, "12 回"), ("Isana", 9, "9 回"), ("Kai", 5, "5 回"), ("Mio", 3, "3 回"), ("Ren", 2, "2 回")]),
            ("最速の人生", [("Kai", 5, "42:10"), ("Yura", 12, "51:33"), ("Ren", 2, "1:02:08"), ("Mio", 3, "1:11:40"), ("Isana", 9, "1:40:02")]),
            ("最少の倒れ", [("Yura", 12, "倒れ 0"), ("Kai", 5, "倒れ 0"), ("Isana", 9, "倒れ 1"), ("Ren", 2, "倒れ 3"), ("Mio", 3, "倒れ 4")])]
    pod = [(255, 212, 90), (214, 221, 226), (211, 154, 100)]
    for c, (title, rows) in enumerate(cols):
        x = 40 + c * 218
        d.text((x, y2 + 40), title, font=JP_S, fill=SOFT)
        yy = y2 + 62
        for i, (nm, n, val) in enumerate(rows):
            if i < 3:
                d.text((x, yy), "%d %s" % (i + 1, nm), font=font("meiryob.ttc", 20), fill=pod[i])
                d.text((x + 10, yy + 26), symbols(n), font=SYM, fill=tag_colors(n)[1])
                d.text((x + 10 + text_w(d, symbols(n), SYM) + 8, yy + 28), val, font=JP_S, fill=INK)
                yy += 48
            else:
                d.text((x, yy), "%d. %s" % (i + 1, nm), font=JP_S, fill=SOFT)
                d.text((x + 180 - text_w(d, val, JP_S), yy), val, font=JP_S, fill=SOFT)
                yy += 16
    d.rounded_rectangle([716, y2, W - 20, y2 + 270], radius=6, fill=PANEL)
    d.text((732, y2 + 10), "飾り板 (C21): 巨神ごとの最速。更新されると光る", font=JP_B, fill=INK)
    recs = [("岩鯨", "Kai", "12:41", True), ("潮殻", "Yura", "18:05", False), ("雷翼", "—", "まだ記録がない", False), ("余燼", "—", "まだ記録がない", False)]
    for i, (t, who, tm, fresh) in enumerate(recs):
        yy = y2 + 44 + i * 54
        if fresh:
            d.rectangle([732, yy, W - 36, yy + 44], outline=GOLD, width=2)
            d.text((W - 36 - 40, yy + 4), "更新", font=JP_S, fill=GOLD)
        d.text((744, yy + 4), t, font=JP, fill=GOLD if fresh else INK)
        d.text((744, yy + 24), tm, font=JP_S, fill=INK if who != "—" else (95, 106, 112))
        if who != "—":
            d.text((830, yy + 24), "by " + who, font=JP_S, fill=SOFT)

    # --- 4 段目: 枠・墓・光
    y3 = y2 + 286
    d.rounded_rectangle([20, y3, W - 20, y3 + 150], radius=6, fill=PANEL)
    d.text((36, y3 + 8), "パーティ一覧・記録画面の枠 (C15) / 墓の飾り (C12) / 倒れの光 (C13)", font=JP_B, fill=INK)
    members = [("Yura", 15), ("Isana", 6), ("Kai", 2), ("Mio", 0)]
    for i, (nm, n) in enumerate(members):
        x, yy = 40, y3 + 40 + i * 26
        acc = tag_colors(n)[1]
        if n:
            d.rectangle([x - 3, yy - 3, x + 62, yy + 16], outline=acc, width=1)
            if tier(n) >= 2:
                d.rectangle([x - 5, yy - 5, x + 64, yy + 18], outline=tuple(int(c * 0.6) for c in acc), width=1)
        d.text((x, yy), "・" + nm, font=JP_S, fill=INK)
        if n:
            d.text((x + 70, yy - 2), symbols(n), font=SYM, fill=acc)
            d.text((x + 70 + text_w(d, symbols(n), SYM) + 6, yy), "Lv 50+%d" % n, font=JP_S, fill=tag_colors(n)[0])
        else:
            d.text((x + 70, yy), "Lv 32", font=JP_S, fill=SOFT)
    # 墓
    gx, gy = 560, y3 + 100
    d.rectangle([gx - 60, gy + 12, gx + 60, gy + 30], fill=(70, 100, 62))
    d.rectangle([gx - 22, gy - 4, gx + 22, gy + 16], fill=(88, 112, 104))
    d.rectangle([gx - 14, gy - 12, gx + 14, gy - 2], fill=(98, 122, 112))
    for fx, c in ((-42, GOLD), (-30, GOLD), (36, GOLD)):
        d.line([gx + fx, gy + 14, gx + fx, gy + 4], fill=(70, 130, 70), width=2)
        d.ellipse([gx + fx - 4, gy - 2, gx + fx + 4, gy + 6], fill=c)
    d.text((gx - text_w(d, "✧✦✦ Lv 50+7", SYM) / 2, gy - 38), "✧✦✦ Lv 50+7", font=SYM, fill=GOLD_T)
    d.text((gx - 64, y3 + 128), "墓: 記号・Lv・花 (金 = タンポポ 3)", font=JP_S, fill=SOFT)
    # 光
    bx, by = 960, y3 + 92
    for k in range(26):
        a = k * 0.62
        r = 8 + (k * 7) % 46
        col = prism((k * 0.071) % 1.0)
        px, py = bx + math.cos(a) * r, by - 6 + math.sin(a) * r * 0.7
        d.ellipse([px - 3, py - 3, px + 3, py + 3], fill=col)
    d.rectangle([bx - 6, by - 4, bx + 6, by + 22], fill=(62, 96, 128))
    d.text((bx - 80, y3 + 128), "倒れ・立ち上がり: 段の色の粒、1 秒以内", font=JP_S, fill=SOFT)
    d.text((1120, y3 + 40), "設定 (C23)", font=JP_S, fill=SOFT)
    for i, t in enumerate(["名札の飾り  ON", "名札の称号  ON", "自分の背の飾り  ON", "他人の背の飾り  ON", "倒れの光  ON", "プリズムを止める  OFF"]):
        d.text((1120, y3 + 58 + i * 14), t, font=JP_S, fill=INK)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
