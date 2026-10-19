"""雷翼の試練の甲板 (docs/design/29 の P2、版 22) の確認シート。tools/shots/trial_p2_storm.txt の絵を、道の順に 1 枚へ (落ち着いた色・小さめ)。
使い方: python tools/trial_p2_storm_sheet.py → docs/review/previews/trial_p2_storm_sheet.png
入力: run/shots/trial_p2_storm/shots/trial_p2_storm/<名前>.png (./gradlew runShots --no-daemon -Pscript=tools/shots/trial_p2_storm.txt -PshotsName=trial_p2_storm)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "run/shots/trial_p2_storm/shots/trial_p2_storm"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/trial_p2_storm_sheet.png"
FONT = next(p for p in ["C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"] if Path(p).exists())

BG = (24, 27, 34)
LINE = (58, 66, 80)
TEXT = (200, 205, 214)
DIM = (128, 136, 150)

CELLS = [
    ("bridge", "道", "橋。背の広場の南の縁から尾の上の空へ"),
    ("yard", "道", "試練の渡り場。休み場と板、四つの戸"),
    ("yard_gates", "道", "西の戸 (気嚢) と色の線。引いた戸だけ開く"),
    ("s02_start", "風", "気嚢の滑空: 始めの台。光る気嚢が見本"),
    ("s02_chain", "風", "気嚢から気嚢へ滑空 (しゃがみ)"),
    ("s02_mid", "風", "中ほどの台 (休み場)。ここから気嚢は小さく"),
    ("s03_start", "雷", "雷柱の渡り: 始めの台。黒い石の柱の列"),
    ("s03_chain", "雷", "柱の天板 5×5。真ん中は金の床"),
    ("s03_mid", "雷", "休み場の台。ここから雷は向こうから来る"),
    ("s03_top", "雷", "真上から。柱の格子と台"),
    ("s10_start", "風", "羽守の見張り台: 始めの台と眠った風の穴"),
    ("s10_chain", "風", "台と台のあいだは空気 9 マス (穴と滑空で渡る)"),
    ("s09_foyer", "雷", "雷乞いの巣: 控えと巣への橋"),
    ("s09_arena", "雷", "巣。縁に避雷の柱 6 本、奥に出口の橋"),
    ("overview", "全", "真上から。橋・渡り場・四つの島"),
]
ZONE = {"道": (170, 176, 188), "風": (150, 184, 204), "雷": (214, 176, 120), "全": (150, 160, 170)}


def font(size):
    return ImageFont.truetype(FONT, size)


W, H, COLS = 340, 191, 5
rows = (len(CELLS) + COLS - 1) // COLS
HEAD = 58
img = Image.new("RGB", (W * COLS, HEAD + (H + 24) * rows), BG)
d = ImageDraw.Draw(img)
d.text((14, 8), "雷翼の試練の甲板 P2 (版 22)", font=font(20), fill=TEXT)
d.text((14, 36), "風 = 気嚢の滑空・羽守の見張り台 / 雷 = 雷柱の渡り・雷乞いの巣。歩・闘の枠が二つ以上になり、雷翼のプールが動く。docs/design/29", font=font(11), fill=DIM)
for i, (name, zone, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = HEAD + (i // COLS) * (H + 24)
    f = SRC / f"{name}.png"
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W - 4, H - 2), Image.LANCZOS)
        img.paste(im, (x + 2, y + 24))
    d.rectangle((x + 2, y + 2, x + 12, y + 18), fill=ZONE[zone])
    d.text((x + 18, y + 3), label, font=font(11), fill=TEXT)
    d.rectangle((x + 1, y + 23, x + W - 2, y + 24 + H - 1), outline=LINE)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.convert("P", palette=Image.ADAPTIVE, colors=256).save(OUT, optimize=True)
print(OUT, img.size)
