"""tools/shots/back_blocks.txt が撮った絵から、確認用の絵を docs/review/previews/ に組む。

使い方: ./gradlew runShots -Pscript=tools/shots/back_blocks.txt のあと、python tools/back_blocks_sheet.py
出力: docs/review/previews/blocks_storm_rod.png (避雷の柱) / blocks_reset_mirror.png (戻しの鏡) / blocks_cinder_lamp.png (燠の灯)
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "run/shots/back_blocks/shots/back_blocks"
OUT = ROOT / "docs/review/previews"

SHEETS = {
    "blocks_storm_rod": ["rod_wide", "rod_close", "rod_top", "rod_night"],
    "blocks_reset_mirror": ["mirror_south", "mirror_east", "mirror_back"],
    "blocks_cinder_lamp": ["lamp_day", "lamp_close", "lamp_night"],
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, shots in SHEETS.items():
        imgs = [Image.open(SHOTS / f"{s}.png").convert("RGB").resize((640, 360), Image.LANCZOS) for s in shots]
        cols = 2
        rows = (len(imgs) + cols - 1) // cols
        sheet = Image.new("RGB", (640 * cols, 360 * rows), (20, 20, 24))
        for i, im in enumerate(imgs):
            sheet.paste(im, ((i % cols) * 640, (i // cols) * 360))
        sheet.save(OUT / f"{name}.png")
        print("wrote", OUT / f"{name}.png")


if __name__ == "__main__":
    main()
