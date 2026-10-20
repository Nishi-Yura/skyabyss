"""python pv_w.py <A|B|C> <out.png> [tier]  : 武器 5 点の確認 (正面・3/4・横)"""
import importlib
import sys
from rend import *

st = sys.argv[1]
mod = importlib.import_module("w_" + st.lower())
tier = int(sys.argv[3]) if len(sys.argv) > 3 else 1
rows = []
for name in ("greatsword", "bow", "spear", "staff", "dagger"):
    fn = getattr(mod, name)
    faces = fn(tier)
    r = make(faces, st)
    cen = (1.0, 9, 0) if name == "bow" else (0, 9, 0)
    sc = 9.0
    rows.append(turntable(r, cen, sc, yaws=(180, 150, 90)))
    print(name, r["tris"], flush=True)
S = Image.new("RGB", (5 * 170, 3 * 280))
for i, row in enumerate(rows):
    for j, im in enumerate(row):
        S.paste(im, (i * 170, j * 280))
S.save(sys.argv[2])
