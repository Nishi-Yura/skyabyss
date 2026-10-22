"""雷翼の背の拡大 (docs/design/26 の 3.3、版 20) の確認シート。tools/shots/storm_scale.txt の絵を、地帯の流れの順に 1 枚へ。
使い方: python tools/sheets/storm_scale_sheet.py → docs/review/previews/storm_scale_sheet.png
入力: run/shots/storm_scale/shots/storm_scale/<名前>.png (./gradlew runShots -Pscript=tools/shots/storm_scale.txt)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent
SRC = ROOT / "run/shots/storm_scale/shots/storm_scale"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/storm_scale_sheet.png"
FONT = next(p for p in ["C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"] if Path(p).exists())

BG = (24, 27, 34)
LINE = (58, 66, 80)
TEXT = (200, 205, 214)
DIM = (128, 136, 150)

# (名前, 地帯の色, 一行)
CELLS = [
    ("arrival", "羽", "尾羽の上の到着。北へ羽軸の道が一本"),
    ("road", "羽", "羽軸の道。矢じるし・灯りの柱・門が見える"),
    ("plaza", "羽", "羽の広場。休み場と左右の見晴らし"),
    ("gate", "羽", "羽根の門。くぐると体の中"),
    ("hub_south", "羽", "風の広間 45×46。床の縞は北向きの矢じり"),
    ("hub_floor", "羽", "上の回廊から見た広間。色の線が三つの出口へ"),
    ("west_pass", "骨", "西の肩の階段 17 段。真ん中に水色の線"),
    ("valley1", "骨", "風の谷 I。割れた床・縁の灯り・骨の翼桁"),
    ("island", "骨", "島。休み場 wm と、北の骨の書庫の戸口"),
    ("vault_w", "骨", "骨の書庫。碑と宝箱 (必須ではない)"),
    ("elbow", "骨", "肘の洞 (半径 14)。休み場 w1"),
    ("rib_low", "骨", "肋の間 22×23 を見上げる。昇り風の格子"),
    ("e0", "雷", "東の肩の部屋。暗い雷の石に銅の筋"),
    ("circuit1", "雷", "回路 1。接地のレバーと金の床 (教える)"),
    ("circuit4", "雷", "回路 4。橙の筋が先へ。レバーがずれている (ひねり)"),
    ("vault_e", "雷", "雷の炉。碑と宝箱 (必須ではない)"),
    ("nest", "雷", "雷獣の巣 (半径 13。広さは変えていない)"),
    ("crown", "冠", "嵐の冠の間。七本の銅の避雷針"),
    ("eye", "冠", "眼の間 23×23。鏡の台は間隔 4"),
    ("overview", "全", "尾羽の側から見た全景"),
]
ZONE = {"羽": (170, 176, 188), "骨": (196, 188, 160), "雷": (214, 168, 120), "冠": (176, 196, 214), "全": (150, 160, 170)}


def font(size):
    return ImageFont.truetype(FONT, size)


W, H, COLS = 400, 225, 4
rows = (len(CELLS) + COLS - 1) // COLS
HEAD = 64
img = Image.new("RGB", (W * COLS, HEAD + (H + 26) * rows), BG)
d = ImageDraw.Draw(img)
d.text((16, 10), "雷翼の背 x1.6 (版 20)", font=font(22), fill=TEXT)
d.text((16, 40), "羽 = 尾・広間 / 骨 = 西の翼桁 / 雷 = 東の稲妻の筋 / 冠 = 頭。docs/design/12 の 3.3・25 の 11・26 の 9", font=font(12), fill=DIM)
for i, (name, zone, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = HEAD + (i // COLS) * (H + 26)
    f = SRC / f"{name}.png"
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W - 4, H - 2), Image.LANCZOS)
        img.paste(im, (x + 2, y + 26))
    d.rectangle((x + 2, y + 2, x + 14, y + 20), fill=ZONE[zone])
    d.text((x + 20, y + 4), label, font=font(13), fill=TEXT)
    d.rectangle((x + 1, y + 25, x + W - 2, y + 26 + H - 1), outline=LINE)
OUT.parent.mkdir(parents=True, exist_ok=True)
img.convert("P", palette=Image.ADAPTIVE, colors=256).save(OUT, optimize=True)
print(OUT, img.size)
