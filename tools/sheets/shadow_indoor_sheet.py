# 屋内の空の影の before / after を一枚にまとめる。
# python tools/sheets/shadow_indoor_sheet.py <before のフォルダ> <after のフォルダ> [出力]
import sys
from PIL import Image, ImageDraw, ImageFont

NAMES = ["open_up", "open_out", "roof_up", "roof_out", "tree_up", "tree_out", "cave_up", "cave_out"]
LABELS = ["open / up", "open / out", "roof / up", "roof / out", "tree / up", "tree / out", "cave / up", "cave / out"]


def main():
    before, after = sys.argv[1], sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else "docs/review/previews/shadow_indoor_sheet.png"
    w, h = 480, 270
    pad = 6
    top = 26
    cols = 4
    rows_per = 2
    sheet = Image.new("RGB", (cols * (w + pad) + pad, 2 * (rows_per * (h + pad) + top) + pad), (20, 20, 24))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arial.ttf", 16)
    except OSError:
        font = ImageFont.load_default()
    y = pad
    for title, folder in (("BEFORE", before), ("AFTER", after)):
        d.text((pad, y + 4), title, fill=(255, 255, 255), font=font)
        y += top
        for i, n in enumerate(NAMES):
            im = Image.open(f"{folder}/{n}.png").convert("RGB").resize((w, h), Image.LANCZOS)
            x = pad + (i % cols) * (w + pad)
            yy = y + (i // cols) * (h + pad)
            sheet.paste(im, (x, yy))
            d.text((x + 6, yy + 4), LABELS[i], fill=(255, 255, 160), font=font)
        y += rows_per * (h + pad)
    sheet.save(out)
    print(out, sheet.size)


main()
