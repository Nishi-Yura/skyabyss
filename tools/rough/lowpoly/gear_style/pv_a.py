"""python pv_a.py <A|B|C> <out.png> : 防具 4 部位の確認 (正面・3/4・横・背中)"""
import importlib
import sys
from arend import *

st = sys.argv[1]
mod = importlib.import_module("ar_" + st.lower())
res = build_set(mod.build(), st)
print("tris", tris(res), {k: v["tris"] for k, v in res.items()})
ims = [render(res, y) for y in (180, 135, 90, 0)]
S = Image.new("RGB", (1200, 400))
for i, im in enumerate(ims):
    S.paste(im, (i * 300, 0))
S.save(sys.argv[2])
