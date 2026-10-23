"""確認用: 1 品を大きく写す (前 3/4 と後ろ 3/4)。python -I zoom_v2.py <id> <out.png> [plus]"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_v2 as B  # noqa: E402
from PIL import Image  # noqa: E402

iid, out = sys.argv[1], sys.argv[2]
plus = int(sys.argv[3]) if len(sys.argv) > 3 else 0
it = B.IT.ITEMS[iid]
r = B.make(iid, plus)
mid, ext = B.mid_of(r)
S = (560, 760)
sc = 700 / ext
ims = [B.on_plat(B.view(r["faces"], r["atlas"], r["glow"], B.key(it), yaw, pit, S, mid, sc)) for yaw, pit in ((165, -12), (345, 10))]
W = Image.new("RGB", (S[0] * 2, S[1]))
for i, im in enumerate(ims):
    W.paste(im, (S[0] * i, 0))
W.save(out)
print(r["tris"], r["size"])
