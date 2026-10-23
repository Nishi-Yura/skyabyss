#!/usr/bin/env python3
"""Before/after sheet for the 256 repaint of the tide heart p2/p3 and storm heart p2 atlases.

    python tools/sheets/heart_atlas_256_sheet.py [BEFORE_REV]   (default HEAD)

Each row: the installed mesh rendered orthographically (front) with the BEFORE atlas and the current one, the phase-1 atlas of the
same heart for reference, then a 128x128 crop of the before/after atlases (same normalized area, nearest-neighbour).
Output: docs/review/boss_leftovers/heart_atlas_256_before_after.png
"""
import io
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import boss_quality_sheet as B  # noqa: E402  (load_mesh / render / font)

OUT = os.path.join(B.ROOT, "docs", "review", "boss_leftovers", "heart_atlas_256_before_after.png")
ROWS = ["tide_heart_p2", "tide_heart_p3", "storm_heart_p2"]
CELL = 400


def atlas_crop(name, rev, box=(0.0, 0.0, 0.5, 0.5)):
    raw = B.git_bytes(rev, B.A + "textures/lowpoly/%s.png" % name) if rev else open(os.path.join(B.ROOT, B.A, "textures/lowpoly", name + ".png"), "rb").read()
    im = Image.open(io.BytesIO(raw)).convert("RGBA")
    w, h = im.size
    c = im.crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
    bg = Image.new("RGBA", c.size, (30, 32, 40, 255))
    bg.alpha_composite(c)
    return bg.resize((CELL, CELL), Image.NEAREST), im.size


def main():
    rev = sys.argv[1] if len(sys.argv) > 1 else "HEAD"
    cols = ["before (%s)" % rev, "after", "phase 1 (reference)", "atlas before (top-left quarter)", "atlas after (same area)"]
    sheet = Image.new("RGB", (len(cols) * (CELL + 8) + 8, len(ROWS) * (CELL + 34) + 60), (18, 18, 24))
    d = ImageDraw.Draw(sheet)
    d.text((10, 10), "Heart atlases 128 -> 256 (front ortho of the installed mesh, lit pixels unshaded)", font=B.font(22), fill=(236, 232, 218))
    for r, name in enumerate(ROWS):
        p1 = name[:-1] + "1"
        before_a, bs = atlas_crop(name, rev)
        after_a, as_ = atlas_crop(name, None)
        tiles = [
            ("%s %dx%d" % (name, *bs), B.render(B.load_mesh(name, rev), 180, 8, CELL, CELL)),
            ("%s %dx%d" % (name, *as_), B.render(B.load_mesh(name), 180, 8, CELL, CELL)),
            (p1, B.render(B.load_mesh(p1), 180, 8, CELL, CELL)),
            ("before", before_a),
            ("after", after_a),
        ]
        for c, (lab, im) in enumerate(tiles):
            x, y = 8 + c * (CELL + 8), 50 + r * (CELL + 34)
            sheet.paste(im.convert("RGB"), (x, y))
            d.text((x + 2, y + CELL + 4), (cols[c] + ": " if r == 0 else "") + lab, font=B.font(15), fill=(150, 232, 255))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
