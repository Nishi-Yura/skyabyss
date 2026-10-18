#!/usr/bin/env python3
"""Contact sheets for the low-poly titan bodies (docs/design/15_high_poly.md 4.1 / 9.6).
Reads run/shots/lp_titan_<name>/shots/lp_titan_<name>/ (tools/gen_lowpoly_titan_shots.py scripts) and writes
docs/review/previews/lp_titan_<name>_sheet.png (mesh above, cube model below, same view in the same column) and copies the
mesh shots to docs/review/previews/lp_titan_<name>_<view>.png.
    python tools/lowpoly_titan_sheets.py"""
import os
import shutil

from PIL import Image, ImageDraw

OUT = os.path.join("docs", "review", "previews")
RUN = os.path.join("run", "shots")
W, H = 426, 240
COLS = 4
TITANS = ["whale", "tide", "storm", "ember"]


def views(name):
    d = os.path.join(RUN, "lp_titan_" + name, "shots", "lp_titan_" + name)
    return d, sorted(f[len("lp_titan_%s_" % name):-4] for f in os.listdir(d) if f.startswith("lp_titan_%s_" % name) and f.endswith(".png"))


def tile(path, label):
    if os.path.exists(path):
        im = Image.open(path).convert("RGB").resize((W, H), Image.LANCZOS)
    else:
        im = Image.new("RGB", (W, H), (40, 40, 40))
    ImageDraw.Draw(im).text((6, 4), label, fill=(255, 255, 0))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in TITANS:
        try:
            d, vs = views(name)
        except FileNotFoundError:
            continue
        order = ["near_ground", "near_above"] + [v for v in vs if not v.startswith(("near", "mid", "far"))] + \
                ["mid_ground", "mid_above", "far_ground", "far_above"]
        order = [v for v in order if v in vs]
        if not order:
            continue
        rows = (len(order) + COLS - 1) // COLS
        sheet = Image.new("RGB", (W * COLS, H * 2 * rows))
        for i, v in enumerate(order):
            x, y = (i % COLS) * W, (i // COLS) * H * 2
            sheet.paste(tile(os.path.join(d, "lp_titan_%s_%s.png" % (name, v)), "mesh " + v), (x, y))
            sheet.paste(tile(os.path.join(d, "cubes_titan_%s_%s.png" % (name, v)), "cube " + v), (x, y + H))
            shutil.copyfile(os.path.join(d, "lp_titan_%s_%s.png" % (name, v)), os.path.join(OUT, "lp_titan_%s_%s.png" % (name, v)))
        path = os.path.join(OUT, "lp_titan_%s_sheet.png" % name)
        sheet.save(path)
        print("wrote", path, len(order), "views")


if __name__ == "__main__":
    main()
