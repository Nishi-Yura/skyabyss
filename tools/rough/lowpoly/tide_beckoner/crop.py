"""シートの一部を切り出して確認する (開発用)。python crop.py in.png out.png y0 y1 [x0 x1]"""
import sys

from PIL import Image

im = Image.open(sys.argv[1])
y0, y1 = int(sys.argv[3]), int(sys.argv[4])
x0 = int(sys.argv[5]) if len(sys.argv) > 5 else 0
x1 = int(sys.argv[6]) if len(sys.argv) > 6 else im.width
im.crop((x0, y0, x1, min(y1, im.height))).save(sys.argv[2])
