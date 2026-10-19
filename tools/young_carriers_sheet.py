"""docs/review/previews/young_carriers_sheet.png : 巨神の子 (docs/design/28) の一覧シート (静かな色、日本語)。
入力: docs/review/young_roughs/<titan>_compare.png の A 案 (今ゲームに入っている案)。python tools/young_carriers_sheet.py"""
import os
from PIL import Image, ImageDraw, ImageFont
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
R = os.path.join(ROOT, "docs/review/young_roughs")
OUT = os.path.join(ROOT, "docs/review/previews/young_carriers_sheet.png")
FONT = "C:/Windows/Fonts/meiryo.ttc"
BG, PANEL, INK, MUTE, LINE, ACC = (24, 26, 32), (32, 35, 43), (214, 218, 224), (138, 146, 158), (58, 63, 74), (120, 168, 176)
F = lambda s: ImageFont.truetype(FONT, s)
ROWS = [
    ("stone_whale", "岩鯨の仔", "空を泳ぐ（地形の 20 上）", "どこでも・最大 2400", 1020 // 3),
    ("tide_shell", "潮殻の稚貝", "海を滑る（陸は短く転がる）", "海のある所・最大 3200", 400),
    ("thunder_wing", "雷翼の雛", "低く飛び稜線を渡る", "陸だけ・広い海は不可・最大 2600", 560),
    ("ember", "余燼の仔", "地を駆け溶岩を渡る", "陸の道が続く所・最大 1800", 400),
]
W, CELL_W, CELL_H = 1280, 420, 200
img = Image.new("RGB", (W, 100 + 4 * (CELL_H + 12) + 196), BG)
d = ImageDraw.Draw(img)
d.text((24, 16), "巨神の子（歌のかけら）— 笛で現れ、パーティ全員 6 人を運び、降りると溶ける", font=F(22), fill=INK)
d.text((24, 52), "成体は動かない。子は乗っている間だけ存在し、親が眠れば子も眠る。絵は今ゲームに入っている A 案（3 案から選定中）", font=F(14), fill=MUTE)
y = 90
for t, name, gait, rng, cw in ROWS:
    d.rectangle([16, y, W - 16, y + CELL_H], fill=PANEL, outline=LINE)
    im = Image.open(os.path.join(R, t + "_compare.png")).convert("RGB")
    w, h = im.size
    third = w // 3
    crop = im.crop((0, 36, third, h - 30)).resize((CELL_W, int((h - 66) * CELL_W / third)))
    crop = crop.crop((0, max(0, (crop.height - CELL_H + 8) // 2), CELL_W, max(0, (crop.height - CELL_H + 8) // 2) + CELL_H - 8))
    img.paste(crop, (22, y + 4))
    x = 22 + CELL_W + 24
    d.text((x, y + 18), name, font=F(24), fill=INK)
    d.text((x, y + 60), "運び方　" + gait, font=F(16), fill=MUTE)
    d.text((x, y + 90), "行ける所　" + rng + "（ブロック）", font=F(16), fill=MUTE)
    d.text((x, y + 120), "乗れる人数　6（2 列 × 3 行。持ち主が左前）", font=F(16), fill=MUTE)
    d.text((x, y + 150), "眠っている巨神・会っていない巨神へは行けない", font=F(16), fill=MUTE)
    y += CELL_H + 12
d.text((24, y + 4), "流れ", font=F(18), fill=ACC)
steps = ["笛を吹く", "子が現れる", "乗る（仲間も）", "海図で行き先", "3〜30 秒待つ", "運ばれる", "着く・降りる", "歌に溶ける"]
x = 24
for i, s in enumerate(steps):
    d.rectangle([x, y + 36, x + 142, y + 76], fill=PANEL, outline=LINE)
    d.text((x + 8, y + 45), s, font=F(15), fill=INK)
    if i < len(steps) - 1:
        d.text((x + 146, y + 45), ">", font=F(15), fill=MUTE)
    x += 158
d.text((24, y + 96), "行き先: 会った巨神のいる場所（降りると乗り込み口の輪が開く）／はじまりの地／立てた碑。旅のあと 5 秒は笛を吹けない。", font=F(14), fill=MUTE)
d.text((24, y + 122), "ログアウトした人は安全な地面に降り、ほかの人は運ばれ続ける。サーバー再起動で子は消え、笛を吹き直す。", font=F(14), fill=MUTE)
d.text((24, y + 148), "自己テスト: 6 人の同乗・7 人目の拒み・眠る巨神の拒み・飛行中は降りられない・到着・溶ける・ログアウト・得意の判定が通過。", font=F(14), fill=MUTE)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print(OUT, img.size)
