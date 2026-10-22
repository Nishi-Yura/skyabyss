# -*- coding: utf-8 -*-
"""tools/shots/trials_flow3.txt が撮った絵から、確認用の一枚を docs/review/previews/trials_flow3_sheet.png に組む (試練の流れ。版 28)。

使い方: ./gradlew runShots -Pscript=tools/shots/trials_flow3.txt のあと、python -I tools/sheets/trials_flow3_sheet.py
出力: docs/review/previews/trials_flow3_sheet.png (日本語の見出しつき。落ち着いた色。音なし)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/trials_flow3/shots/trials_flow3"
OUT = ROOT / "docs/review/previews/trials_flow3_sheet.png"

TILES = [
    ("goal_mark", "ゴール: 石は無い。光る印に着けば越える"),
    ("hub_tablets", "鼓動の間の碑: 越えた試練だけ光る (封印は金のまま)"),
    ("weekly_path", "今週の試練: 灯りの道と金の光る床 (休み場のすぐそば)"),
    ("weekly_sign", "立て札: 今週の試練の名前。立てば入口へ飛べる"),
    ("room_library", "入って左 = 骨の書庫: 入ると題が出る (任意)"),
    ("room_bell", "アスレの途中 = 数え歌の鐘楼: 入ると題が出る"),
]
NOTES = [
    "・石は全巨神で無くなった。試練の終わりの光る印に着くと越え、中にいる仲間は 4 秒後に鼓動の間へ戻る。",
    "・金の「試練済みの封印」は残す。ゴールの横の紫の封印 (抜け道の戸・出口の戸。開かなかった) は無くした。",
    "・今週の試練は個人: 光る床に立った人だけが入口へ移り、越えたらその人だけが鼓動の間へ戻る。",
]
W, H, COLS = 520, 292, 3
BG = (30, 32, 36)
FG = (200, 206, 210)


def font(size):
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


f = font(15)
rows = (len(TILES) + COLS - 1) // COLS
foot = 24 * len(NOTES) + 12
sheet = Image.new("RGB", (W * COLS, (H + 26) * rows + foot), BG)
d = ImageDraw.Draw(sheet)
for i, (name, title) in enumerate(TILES):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 26)
    p = SHOTS / f"{name}.png"
    if p.exists():
        sheet.paste(Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS), (x, y + 26))
    d.text((x + 6, y + 4), title, fill=FG, font=f)
y0 = (H + 26) * rows + 8
for k, line in enumerate(NOTES):
    d.text((8, y0 + 24 * k), line, fill=FG, font=f)
OUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUT)
print(OUT)
