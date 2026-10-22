"""深淵 (docs/design/31 の B04) の確認シート (控えめな色・日本語)。

  python tools/sheets/abyss_sheet.py   -> docs/review/previews/abyss_sheet.png

 1. 修飾の段のはしご (常 / 稀 / 極 / 深淵): 試練の数字と素材
 2. 深淵の心臓の四つの技 (部屋を上から見た図。赤 = 当たり、青緑 = 安全)
 3. 専用の伝説 4 点と書き換え、落とし表
数字は Abyss / TrialDraw / 試練の式と同じもの (自己テスト abyss が保証する)。
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "docs", "review", "previews", "abyss_sheet.png")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "item")

BG = (22, 21, 28)
PANEL = (32, 30, 40)
LINE = (62, 58, 78)
TEXT = (214, 210, 224)
DIM = (140, 134, 156)
GOLD = (196, 164, 96)
RED = (186, 84, 92)
TEAL = (96, 168, 164)
VIOLET = (130, 100, 190)
RARE = (196, 164, 96)
EXT = (170, 100, 190)
ABY = (200, 60, 70)

FONTS = [r"C:\Windows\Fonts\YuGothM.ttc", r"C:\Windows\Fonts\meiryo.ttc", r"C:\Windows\Fonts\msgothic.ttc"]


def font(size):
    for f in FONTS:
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


F_T = font(26)
F_H = font(19)
F_B = font(15)
F_S = font(13)

W, H = 1500, 978
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def wrap(text, width):
    out, cur = [], ""
    for ch in text:
        if d.textlength(cur + ch, font=F_S) > width:
            out.append(cur)
            cur = ch
        else:
            cur += ch
    out.append(cur)
    return chr(10).join(out)


def panel(x, y, w, h, title):
    d.rectangle([x, y, x + w, y + h], fill=PANEL, outline=LINE)
    d.text((x + 14, y + 10), title, font=F_H, fill=GOLD)


d.text((24, 16), "深淵 (B04)  転生 3 回以上が板のつまみで選ぶ。数字の標準だけが上がる", font=F_T, fill=TEXT)

# ------------------------------------------------------------------ 1. はしご
panel(20, 64, 700, 366, "1. 修飾の段のはしご (試練の数字 = 岩鯨の四つ)")
cols = [("常", DIM), ("稀", RARE), ("極", EXT), ("深淵", ABY)]
x0, y0 = 40, 108
d.text((x0, y0), "", font=F_B)
rows = [
    ("梁の周期 (tick)", ["110", "85〜140", "85", "74"]),
    ("梁の止まる割合", ["0.65", "0.5〜0.8", "0.50", "0.43 (下限 0.35)"]),
    ("水門の猶予 (tick)", ["220", "160〜280", "160", "139 (下限 60)"]),
    ("水門の保つ時間", ["40", "20〜60", "60", "69"]),
    ("音を並べる数", ["4", "3〜6", "6", "7"]),
    ("砲手 / 波 (一人)", ["3 / 2", "2〜4 / 1〜3", "4 / 3", "5 / 4"]),
    ("障害", ["なし", "0〜2", "二つ", "二つ (できる限り)"]),
    ("任意の目標", ["なし", "0〜1", "あり", "必ずあり"]),
    ("素材 (基本 2 × 段)", ["+0", "+1", "+2", "+3"]),
    ("共鳴の欠片", ["-", "-", "8%", "12%"]),
]
cw = [170, 90, 130, 130, 160]
xs = [x0]
for w_ in cw[:-1]:
    xs.append(xs[-1] + w_)
for i, (name, col) in enumerate(cols):
    d.text((xs[i + 1] + 4, y0), name, font=F_B, fill=col)
for r, (label, vals) in enumerate(rows):
    y = y0 + 28 + r * 25
    d.line([x0, y - 3, x0 + sum(cw) - 20, y - 3], fill=LINE)
    d.text((x0, y), label, font=F_S, fill=DIM)
    for i, v in enumerate(vals):
        d.text((xs[i + 1] + 4, y), v, font=F_S, fill=cols[i][1] if i == 3 else TEXT)
d.text((x0, y0 + 28 + len(rows) * 25 + 2), "極 = 数字がすべて「きびしい」。深淵はその上に 15% の底上げ (待ちは短く・数は 1 つ増える)。", font=F_S, fill=DIM)
d.text((x0, y0 + 28 + len(rows) * 25 + 22), "解ける線の下へは行かない (下限つき)。", font=F_S, fill=DIM)

# ------------------------------------------------------------------ 2. 心臓の技
panel(740, 64, 740, 366, "2. 深淵の心臓の技 (部屋を上から。赤 = 当たり、青緑 = 安全。予告 34 tick)")
names = [("岩鯨 骨の放射", 0), ("潮殻 渦の輪", 1), ("雷翼 雷の柱", 2), ("余燼 熾の雨", 3)]
for k, (nm, kind) in enumerate(names):
    cx = 740 + 100 + k * 175
    cy = 230
    R = 70
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(26, 25, 34), outline=LINE)
    if kind == 0:
        for i in range(8):
            a = i * math.pi / 4 + 0.2
            x1, y1 = cx + math.cos(a) * 20, cy + math.sin(a) * 20
            x2, y2 = cx + math.cos(a) * R, cy + math.sin(a) * R
            d.line([x1, y1, x2, y2], fill=RED, width=9)
        d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=(26, 40, 40), outline=TEAL)
    elif kind == 1:
        d.ellipse([cx - R * 0.5, cy - R * 0.5, cx + R * 0.5, cy + R * 0.5], outline=RED, width=1)
        for rr in (0.30, 0.50):
            pass
        # 内の輪 (30〜50%)、外の輪 (62〜82%)
        for a_in, a_out in ((0.30, 0.50), (0.62, 0.82)):
            d.ellipse([cx - R * a_out, cy - R * a_out, cx + R * a_out, cy + R * a_out], fill=(120, 54, 62))
            d.ellipse([cx - R * a_in, cy - R * a_in, cx + R * a_in, cy + R * a_in], fill=(26, 25, 34))
        d.ellipse([cx - R * 0.3, cy - R * 0.3, cx + R * 0.3, cy + R * 0.3], outline=TEAL)
    elif kind == 2:
        pts = [(-28, 20), (22, -32), (35, 28), (-10, -10)]
        for i, (px, py) in enumerate(pts):
            rr = 16
            d.ellipse([cx + px - rr, cy + py - rr, cx + px + rr, cy + py + rr], fill=(120, 54, 62), outline=RED)
            d.text((cx + px - 4, cy + py - 8), str(i % 3 + 1), font=F_S, fill=TEXT)
    else:
        import random
        rnd = random.Random(7)
        spots = []
        while len(spots) < 10:
            a = rnd.random() * math.tau
            r_ = math.sqrt(rnd.random()) * R * 0.85
            p = (cx + math.cos(a) * r_, cy + math.sin(a) * r_)
            if all(math.hypot(p[0] - q[0], p[1] - q[1]) > 24 for q in spots):
                spots.append(p)
        for p in spots:
            d.ellipse([p[0] - 11, p[1] - 11, p[0] + 11, p[1] + 11], fill=(120, 54, 62), outline=RED)
    d.text((cx - 60, cy + R + 14), nm, font=F_B, fill=TEXT)
d.text((760, 366), "体力 実質 ×1.2・傷 ×1.12・技の間 ×0.85・領域の周期と猶予 ÷1.3", font=F_S, fill=DIM)
d.text((760, 386), "予告の長さは変えない。暗い紫の染め・題字「深淵の心臓」・内へ吸い込まれる紫の粒", font=F_S, fill=DIM)

# ------------------------------------------------------------------ 3. 伝説
panel(20, 442, 1460, 524, "3. 専用の伝説 4 点 (深淵の心臓だけ。27 の書き換えと同格)")
items = [
    ("abyss_greatsword", "底なしの大剣 (岩鯨)", "巨神斬り → 奈落断ち", "振った扇が 1.2 秒後にもう一度裂け、\n残った敵へ ×0.6 の遅れた刃"),
    ("abyss_bell_staff", "沈み鐘の杖 (潮殻)", "守りの唄 → 沈む唄", "割られずに残った守りが唄の終わりに\n体力へ沈み、傷ついた仲間へも半分"),
    ("abyss_storm_bow", "雷淵の弓 (雷翼)", "雲穿ち → 天落とし", "矢の止まった所へ 0.8 秒後に\n雷の柱 (半径 2.5・×0.9)"),
    ("abyss_ash_scythe", "灰喰いの鎌 (余燼)", "灰散らし → 這う灰", "灰かぶりが 2 秒おきに 4 マス先の\n敵へ移る (3 度まで)"),
]
for k, (iid, nm, rune, eff) in enumerate(items):
    x = 40 + k * 360
    t = Image.open(os.path.join(TEX, iid + ".png")).convert("RGBA").resize((160, 160), Image.NEAREST)
    d.rectangle([x, 482, x + 172, 482 + 172], fill=(26, 25, 34), outline=LINE)
    img.paste(t, (x + 6, 488), t)
    d.text((x + 184, 490), nm, font=F_B, fill=GOLD)
    d.text((x + 184, 518), rune, font=F_S, fill=VIOLET)
    d.multiline_text((x + 184, 550), wrap(eff.replace(chr(10), ""), 160), font=F_S, fill=TEXT, spacing=5)
d.text((40, 680), "落とし表 (倒した人それぞれ。巨神ごとの記録、通常の天井とは別)", font=F_H, fill=GOLD)
lines = [
    "共鳴の欠片 ×2 (必ず) ＋ 専用伝説が 25%。4 回続けて出なければ次で保証。持っていれば共鳴の欠片 ×10 に替わる。",
    "期待: 1 回の心臓で約 36.6% (自己テスト 20000 回 = 36.7%・最大の空き 4)。4 点そろうまで約 11 回。",
    "書き換えは 8 → 12 (同じ技に二つは付かない)。通常の図鑑・共鳴の欠片での交換の対象外。",
]
for i, ln in enumerate(lines):
    d.text((40, 714 + i * 26), ln, font=F_B, fill=TEXT)
d.text((40, 810), "確かめたこと (SELFTEST abyss / heart bots)", font=F_H, fill=GOLD)
checks = [
    "転生 0・2 回は閉じ、3 回以上で開く。つまみはグループごと。切るのは誰でも。未転生の仲間も入れる",
    "全試練 × 160 の乱数で段の数式 (数字・障害・目標・往復・冪等・素材 +3)。つまみを戻せば引きが元に戻る",
    "心臓は深淵のグループにだけ深淵で湧く (同じ傷で 1/1.2 しか削れない)。予告を読む人形は 60 秒を生き残る",
    "四つの書き換えが生きた敵に効く (奈落断ちの二撃目・沈む唄の回復・天落とし・這う灰の伝染)",
]
for i, ln in enumerate(checks):
    d.text((40, 842 + i * 24), "・" + ln, font=F_S, fill=DIM)
d.text((40, 944), "絵は平らな 32×32 の仮 (立体の武器モデルは未作成)", font=F_S, fill=DIM)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print(OUT)
