"""雷翼の背の任意の翼 (docs/design/26 の 9.5、版 21) の確認シート。tools/shots/storm_scale2.txt の絵を、道の順に 1 枚へ (落ち着いた色・小さめ)。
使い方: python tools/sheets/storm_scale2_sheet.py → docs/review/previews/storm_scale2_sheet.png
入力: run/shots/storm_scale2/shots/storm_scale2/<名前>.png (./gradlew runShots -Pscript=tools/shots/storm_scale2.txt)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent
SRC = ROOT / "run/shots/storm_scale2/shots/storm_scale2"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/storm_scale2_sheet.png"
FONT = next(p for p in ["C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"] if Path(p).exists())

BG = (24, 27, 34)
LINE = (58, 66, 80)
TEXT = (200, 205, 214)
DIM = (128, 136, 150)

# (名前, 種類, 一行)
CELLS = [
    ("hatch", "道", "尾の間の北西の戸。螺旋で背へ上る (任意)"),
    ("turret_top", "道", "螺旋の頂。戸の先が背の広場"),
    ("plaza", "道", "背の広場 49×24。休み場と二つの塔の関所"),
    ("plaza_rose", "道", "真上から。風見の円盤と、塔への色の線"),
    ("vane_face", "風", "風見の塔の面。外の台の昇り風が一階ずつ運ぶ"),
    ("vane_lobby", "風", "風見の塔 一階。軸の円盤と奥の戸 (外の廊へ)"),
    ("vane_tier2", "風", "風見の塔 三階。階段は奥と手前で交互"),
    ("vane_top", "風", "風見の頂 (高さ 60)。北を指す風見と宝箱"),
    ("rod_face", "雷", "避雷の塔の面。深い石と銅の帯"),
    ("rod_lobby", "雷", "避雷の塔 一階。避雷の柱と軸"),
    ("rod_tier3", "雷", "避雷の塔 四階。嵐は足もとを狙う輪、柱が受ける"),
    ("rod_top", "雷", "避雷の頂。大きな柱と宝箱"),
    ("out_w", "風", "風の外廊 (西)。風上の柱の陰で横風を待つ"),
    ("out_e", "雷", "雷の外廊 (東)。嵐は放電の線 (跳ぶ・潜る)"),
    ("tip_w", "道", "翼の先の見張り台。休み場と宝箱"),
    ("garden_w", "風", "風の庭。昇り風の見本 (凪 4 の鐘・嵐 13 の見張り台)"),
    ("garden_e", "雷", "雷の庭。柱と見本の輪 (傷なし)"),
    ("hub_vault", "影", "風の広間の肋のある丸天井 (縁は低く、肋の間は高く)"),
    ("hub_wall", "影", "羽の壁。白い羽軸と斜めの羽枝"),
    ("e0_ribs", "影", "東の肩の部屋。肋と銅の筋"),
    ("eye_dome", "影", "眼の間の円蓋 (広さは同じ)"),
    ("overview", "全", "尾羽の側から見た全景。背の上に二つの塔"),
    ("overview_side", "全", "西の翼の外の廊と先の見張り台"),
]
ZONE = {"道": (170, 176, 188), "風": (150, 184, 204), "雷": (214, 176, 120), "影": (186, 178, 150), "全": (150, 160, 170)}


def font(size):
    return ImageFont.truetype(FONT, size)


W, H, COLS = 340, 191, 5
rows = (len(CELLS) + COLS - 1) // COLS
HEAD = 58
img = Image.new("RGB", (W * COLS, HEAD + (H + 24) * rows), BG)
d = ImageDraw.Draw(img)
d.text((14, 8), "雷翼の背 任意の翼 (版 21)", font=font(20), fill=TEXT)
d.text((14, 36), "道・風 = 風見の塔と西 / 雷 = 避雷の塔と東 / 影 = 広間の天井と壁。歩ける床 約 13,800 → 約 29,300 m²。docs/design/26 の 9.5", font=font(11), fill=DIM)
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
