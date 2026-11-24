# 撮ったコマを 1 枚に並べる (確かめ用)。python -I tools/shots/sheet.py <出力.png> <列の数> <幅> <絵...>
import sys
from PIL import Image

out, cols, w = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
paths = sys.argv[4:]
ims = [Image.open(p).convert("RGB") for p in paths]
h = round(ims[0].height * w / ims[0].width)
rows = (len(ims) + cols - 1) // cols
sheet = Image.new("RGB", (cols * w + (cols - 1) * 6, rows * h + (rows - 1) * 6), (20, 20, 28))
for i, im in enumerate(ims):
    sheet.paste(im.resize((w, h), Image.NEAREST if w % im.width == 0 else Image.LANCZOS), ((i % cols) * (w + 6), (i // cols) * (h + 6)))
sheet.save(out)
print(out, sheet.size)
