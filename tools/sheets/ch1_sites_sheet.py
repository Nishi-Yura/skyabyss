"""tools/shots/ch1_sites.txt で撮った一章の新しい場所の絵を、一枚の比べる表にまとめる。

使い方: python tools/sheets/ch1_sites_sheet.py [撮った絵のフォルダ] [出力フォルダ (既定 docs/review/previews)]
出力: <出力>/ch1_sites_sheet.png (3 列 x 4 行。石標・祠・骨の集め場・荷車の傍ら / 洞の口・階段・入口の間・棘の間 / 格子・奥の間 / 歌の間の戸・壁画)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/ch1_sites/shots/ch1_sites"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews"
CELLS = [
    ("01_road_cairn", "road cairn"), ("02_road_shrine", "road shrine"), ("03_bone_cache", "bone cache"), ("04_cart_track", "lost cart + cart track"),
    ("05_cave_mouth", "cleft shell: mouth"), ("06_cave_stairs", "cleft shell: stairs"), ("07_cave_room1", "room 1: entry"), ("08_cave_room2_plates", "room 2: spines + ringing stones"),
    ("09_cave_gate", "gate (iron bars)"), ("10_cave_room3", "room 3: elder"), ("11_song_chamber_door", "song chamber: door (opened)"), ("12_song_chamber_mural", "song chamber: mural"),
]
W, H = 560, 315
COLS = 3


def main():
    rows = (len(CELLS) + COLS - 1) // COLS
    sheet = Image.new("RGB", (W * COLS, (H + 22) * rows), (20, 20, 24))
    d = ImageDraw.Draw(sheet)
    for i, (name, label) in enumerate(CELLS):
        x, y = (i % COLS) * W, (i // COLS) * (H + 22)
        f = SRC / (name + ".png")
        if f.exists():
            im = Image.open(f).convert("RGB").resize((W, H), Image.LANCZOS)
        else:
            im = Image.new("RGB", (W, H), (60, 20, 20))
        sheet.paste(im, (x, y + 22))
        d.text((x + 6, y + 5), "%s  %s" % (name[:2], label), fill=(230, 230, 230))
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / "ch1_sites_sheet.png"
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
