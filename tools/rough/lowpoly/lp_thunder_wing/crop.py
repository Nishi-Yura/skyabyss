"""python crop.py out_prefix [h]: シートを縦に切って見る"""
import sys
from PIL import Image

im = Image.open(r"C:\Users\nisimoto\Desktop\mod\.claude\worktrees\agent-aa6728683e6fda8c4\docs\review\lowpoly_roughs\lp_thunder_wing.png")
h = int(sys.argv[2]) if len(sys.argv) > 2 else 1500
n = 0
for y in range(0, im.height, h):
    im.crop((0, y, im.width, min(im.height, y + h))).save(f"{sys.argv[1]}_{n}.png")
    n += 1
print(n, im.size)
