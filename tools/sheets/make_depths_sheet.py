import glob
import os
import sys
from PIL import Image

d = sys.argv[1]
out = sys.argv[2]
SC = os.path.dirname(os.path.abspath(__file__))
names = sorted(os.path.basename(f)[:-4] for f in glob.glob(d + '/v2_*.png'))


def sheet(sel, path, cols=4, w=480):
    ims = [Image.open(f'{d}/{n}.png').convert('RGB') for n in sel]
    h = int(ims[0].height * w / ims[0].width)
    rows = (len(ims) + cols - 1) // cols
    s = Image.new('RGB', (cols * w, rows * h))
    for i, im in enumerate(ims):
        s.paste(im.resize((w, h)), ((i % cols) * w, (i // cols) * h))
    s.save(path)


kinds = [n for n in names if n.endswith('_a') or n.endswith('_b')]
rest = [n for n in names if n not in kinds]
sheet(kinds, os.path.join(out, 'sheet_kinds.png'))
sheet(rest, os.path.join(out, 'sheet_rooms_kings.png'))
print(len(kinds), len(rest))
