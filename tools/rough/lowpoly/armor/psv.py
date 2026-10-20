"""手早い確認: python psv.py <S1|S2|S3> out.png [rarity] [titans...]"""
import sys
from PIL import Image
import aview, spaint, sstyle
aview.apaint = spaint
aview.SS = 1
from common import TITANS
v, out = sys.argv[1], sys.argv[2]
rar = sys.argv[3] if len(sys.argv) > 3 else "common"
tit = sys.argv[4:] or TITANS
VIEWS = [(180, 6), (215, 14), (90, 6), (0, 6)]
W, H = 330, 480
im = Image.new("RGB", (W * len(VIEWS), H * len(tit)))
for r, T in enumerate(tit):
    res = aview.build_set(sstyle.build(T, rar, v), T, rar)
    print(T, {k: x["tris"] for k, x in res.items()}, aview.set_tris(res), {k: x["size"] for k, x in res.items()})
    for c, (yaw, pitch) in enumerate(VIEWS):
        im.paste(aview.render(res, T, yaw, pitch, (W, H), 10.8), (c * W, r * H))
im.save(out)
