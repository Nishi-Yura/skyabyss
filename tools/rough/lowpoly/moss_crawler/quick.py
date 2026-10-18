"""確認用: python quick.py out.png [yaw pitch]"""
import copy, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from lp import *  # noqa
import lpkit

def display(faces):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = f.verts @ lpkit.rot_y(180).T
        g.normal = lpkit.rot_y(180) @ f.normal
        out.append(g)
    return out

def main():
    sets = [(215, 18), (35, 22), (270, 6), (180, 70)]
    cen = (0, 6, 0)
    ims = []
    for s in "abc":
        r = build_option(s)
        df = display(r["faces"])
        for yaw, pitch in sets:
            ims.append(render(df, r["atlas"], r["glow"], yaw, pitch, (440, 300), cen, 12))
    w = Image.new("RGB", (440 * 4, 900), (26, 26, 34))
    for i, im in enumerate(ims):
        w.paste(im, ((i % 4) * 440, (i // 4) * 300), im)
    w.save(sys.argv[1])
main()
