# -*- coding: utf-8 -*-
"""UI まとめ直しの確認シート (docs/review/previews/ui_pass_sheet.png)。
使い方: ./gradlew runShots -Pscript=tools/shots/ui_pass.txt -PshotsName=ui_pass のあとで python tools/ui_pass_sheet.py
入力: run/shots/ui_pass/shots/ui_pass/*.png と、NPC の印・ボスバーの印のテクスチャ
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "run/shots/ui_pass/shots/ui_pass"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/gui"
OUT = ROOT / "docs/review/previews/ui_pass_sheet.png"
FONT = "C:/Windows/Fonts/meiryo.ttc"

W, H = 800, 450
BG = (14, 18, 24)
font = ImageFont.truetype(FONT, 17)
small = ImageFont.truetype(FONT, 14)


def panel(name, caption):
    img = Image.open(SHOTS / name).convert("RGB").resize((W, H), Image.LANCZOS)
    out = Image.new("RGB", (W, H + 26), BG)
    out.paste(img, (0, 26))
    ImageDraw.Draw(out).text((8, 2), caption, font=font, fill=(242, 193, 78))
    return out


def icons_panel():
    out = Image.new("RGB", (W, H + 26), BG)
    d = ImageDraw.Draw(out)
    d.text((8, 2), "NPC の頭の印 (サブは巻物) と、ボスバーの印 (16px)", font=font, fill=(242, 193, 78))
    marks = [("npc_marker/bang_gold.png", "メイン 新しい話"), ("npc_marker/question_gold.png", "メイン 報告"),
             ("npc_marker/scroll_teal.png", "サブ 新しい頼み"), ("npc_marker/scroll_check_teal.png", "サブ 報告"), ("npc_marker/chat.png", "雑談")]
    x = 16
    for path, cap in marks:
        im = Image.open(TEX / path).convert("RGBA")
        im = im.resize((im.width * 5, im.height * 5), Image.NEAREST)
        out.paste(im, (x, 40), im)
        d.text((x, 40 + im.height + 4), cap, font=small, fill=(230, 226, 214))
        x += 150
    bosses = [("boss/stone_whale_heart_icon_p1.png", "岩鯨の心臓"), ("boss/tide_heart_icon_p1.png", "潮殻の心臓"), ("boss/storm_heart_icon_p1.png", "雷翼の心臓"),
              ("boss/ember_heart_icon_p1.png", "余燼の心臓"), ("boss/raiju_icon.png", "雷獣"), ("boss/ash_smith_icon.png", "灰の鍛冶"),
              ("boss/captain_icon.png", "鯨骨の隊長"), ("boss/elder_sentry_icon.png", "古い見張り"), ("boss/pearl_keeper_icon.png", "真珠の番人"),
              ("boss/tide_beckoner_icon.png", "潮呼び")]
    for i, (path, cap) in enumerate(bosses):
        im = Image.open(TEX / path).convert("RGBA")
        im = im.resize((64, 64), Image.NEAREST)
        bx = 16 + (i % 5) * 150
        by = 230 + (i // 5) * 100
        out.paste(im, (bx, by), im)
        d.text((bx, by + 66), cap, font=small, fill=(230, 226, 214))
    return out


panels = [
    panel("ui_gui4.png", "GUI 4: 目的の帯は折り返し (画面の約 1/4 幅)・体力の帯・目的の標"),
    panel("ui_gui2.png", "GUI 2: 同じ場面 (経験値は成長の直下・空の枠は出ない)"),
    panel("ui_chapter_a.png", "章の題字の札: レターボックス・飾り枠・番号と題名の現れ"),
    panel("ui_boss.png", "中ボスのボスバーに 16px の印 (真珠の番人・潮呼び)"),
    panel("ui_reward.png", "遺物の選択: 開いている間は傷つかない・矢印をいつも出す"),
    icons_panel(),
]
sheet = Image.new("RGB", (W * 2 + 12, (H + 26) * 3 + 24), BG)
for i, p in enumerate(panels):
    sheet.paste(p, (4 + (i % 2) * (W + 4), 4 + (i // 2) * (H + 34)))
OUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUT)
print(OUT, sheet.size)
