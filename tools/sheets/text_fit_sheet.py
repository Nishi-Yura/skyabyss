# 文字のはみ出し点検の絵 (tools/shots/text_fit.txt の撮影) を一枚にまとめる。行 = 画面、列 = GUI 2・3・4。
# 使い方: python -I tools/sheets/text_fit_sheet.py  → docs/review/previews/text_fit_sheet.png
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "run/shots/text_fit/shots/text_fit")
OUT = os.path.join(ROOT, "docs/review/previews/text_fit_sheet.png")
SCREENS = ["skills_signal", "skills_cleave", "skills_build", "calling", "growth", "quests", "lore", "party", "grave", "notices", "record"]
CW, CH, K = 1300, 760, 0.5  # 画面の中央を切り出して半分に


def main():
    tw, th = int(CW * K), int(CH * K)
    sheet = Image.new("RGB", (tw * 3 + 4 * 6, (th + 14) * len(SCREENS) + 6), (20, 22, 28))
    d = ImageDraw.Draw(sheet)
    for r, name in enumerate(SCREENS):
        for c, g in enumerate((2, 3, 4)):
            p = os.path.join(SRC, "tf%d_%s.png" % (g, name))
            if not os.path.exists(p):
                continue
            im = Image.open(p).convert("RGB")
            x0, y0 = (im.width - CW) // 2, (im.height - CH) // 2
            im = im.crop((x0, y0, x0 + CW, y0 + CH)).resize((tw, th), Image.LANCZOS)
            x, y = 6 + c * (tw + 6), 6 + r * (th + 14)
            sheet.paste(im, (x, y + 12))
            d.text((x, y), "GUI %d  %s" % (g, name), fill=(200, 210, 220))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    print(OUT, sheet.size)


if __name__ == "__main__":
    sys.exit(main())
