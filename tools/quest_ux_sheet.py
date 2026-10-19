# -*- coding: utf-8 -*-
"""tools/shots/quest_ux.txt の絵を 1 枚の表にまとめる (NPC の頭の上の印と、左上の章の札)。

使い方: python tools/quest_ux_sheet.py [出力ファイル]
入力: run/shots/quest_ux/shots/quest_ux/<名前>.png
出力: docs/review/previews/quest_ux_sheet.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/quest_ux_sheet.png"
CELLS = [
    ("near_chat", "Chat bubbles: new talk available (nothing urgent pending)"),
    ("near_new", "Gold ! over the storyteller (next main step), chat bubble over the chaser; chapter label top-left"),
    ("near_ready", "Gold ? over both (report stage)"),
    ("mid_15", "15 blocks: bobbing icon with a soft glow"),
    ("far_40", "About 40 blocks: drawn larger (capped), still crisp"),
    ("wall_near", "Behind a wall at 10 blocks: still visible"),
    ("wall_far", "Behind a wall at 32 blocks: hidden"),
    ("pouch_hint", "New pouch item: small NEW hint beside the hotbar"),
    ("pouch_new", "Pouch screen: NEW badge on slots not yet looked at"),
]
W, H = 640, 360
COLS = 2
rows = (len(CELLS) + COLS - 1) // COLS
img = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
d = ImageDraw.Draw(img)
for i, (name, label) in enumerate(CELLS):
    x = (i % COLS) * W
    y = (i // COLS) * (H + 22)
    f = ROOT / f"run/shots/quest_ux/shots/quest_ux/{name}.png"
    if f.exists():
        im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        img.paste(im, (x, y + 22))
    d.text((x + 6, y + 5), label, fill=(230, 230, 230))
# 絵の見本 (4 倍)。左から ! 金・! 青緑・? 金・? 青緑・吹き出し
strip = Image.new("RGB", (W * COLS, 120), (120, 160, 220))
icons = ["bang_gold", "bang_teal", "question_gold", "question_teal", "chat"]
for i, n in enumerate(icons):
    ic = Image.open(ROOT / f"src/main/resources/assets/skyabyss/textures/gui/npc_marker/{n}.png").convert("RGBA")
    ic = ic.resize((ic.width * 4, ic.height * 4), Image.NEAREST)
    strip.paste(ic, (20 + i * 90, 12), ic)
d2 = ImageDraw.Draw(strip)
d2.text((W * COLS - 330, 50), "Icons x4: ! gold/teal, ? gold/teal, chat bubble", fill=(255, 255, 255))
img2 = Image.new("RGB", (img.width, img.height + strip.height), (20, 20, 24))
img2.paste(img, (0, 0))
img2.paste(strip, (0, img.height))
img = img2
OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(OUT)
