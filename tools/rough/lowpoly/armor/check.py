"""出力の検査: 骨の名前・三角形の数・光る画素 (0 のマスクは置かない)・頭上/横の大きさ。python check.py"""
import json

import numpy as np
from PIL import Image

from akit import OUT, PIECES

bad = 0
for opt in "abc":
    for sd in sorted((OUT / opt).iterdir()):
        tot = 0
        ext = np.array([1e9, -1e9, 1e9, -1e9, 1e9, -1e9])
        for piece, bones in PIECES.items():
            m = json.loads((sd / f"{piece}_mesh.json").read_text(encoding="utf-8"))
            assert set(m["bones"]) <= set(bones), (sd, piece, m["bones"].keys())
            tot += m["triangle_count"]
            for b in m["bones"].values():
                v = np.array(b["vertices"])
                ext[[0, 2, 4]] = np.minimum(ext[[0, 2, 4]], v.min(0))
                ext[[1, 3, 5]] = np.maximum(ext[[1, 3, 5]], v.max(0))
            g = np.array(Image.open(sd / f"{piece}_glowmask.png"))[..., 3] > 0
            if g.sum() == 0:
                bad += 1
                print("NO GLOW", sd, piece)
        print(opt, sd.name, "tris", tot, "x %.1f..%.1f y %.1f..%.1f z %.1f..%.1f" % tuple(ext))
print("glow-less pieces:", bad)
