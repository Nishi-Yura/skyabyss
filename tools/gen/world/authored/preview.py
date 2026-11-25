# -*- coding: utf-8 -*-
"""前もって組んだ土地 (.sbd) を真上から見た絵 (色はブロックの名前から、高さで陰)。ゲームを開かずに形を見る用。

    python -I tools/gen/world/authored/preview.py <.sbd> <出力.png> [縮尺]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import alib  # noqa: E402

COLORS = [
    ("water", (60, 100, 200)), ("snow", (240, 244, 250)), ("leaves", (52, 92, 50)), ("_log", (90, 64, 40)),
    ("grass_block", (98, 140, 70)), ("moss", (88, 120, 50)), ("podzol", (110, 80, 45)), ("dirt_path", (170, 140, 90)),
    ("coarse_dirt", (120, 90, 60)), ("gravel", (130, 126, 122)), ("packed_mud", (140, 106, 80)), ("dirt", (120, 86, 60)),
    ("calcite", (220, 220, 214)), ("tuff", (108, 108, 98)), ("andesite", (136, 136, 136)), ("stone_brick", (122, 122, 122)),
    ("cobble", (112, 112, 112)), ("stone", (125, 125, 125)), ("lantern", (250, 200, 90)), ("fence", (110, 80, 50)),
    ("planks", (150, 110, 70)), ("bone", (230, 226, 210)), ("short_grass", (110, 160, 80)), ("fern", (80, 130, 70)),
    ("tall_grass", (110, 160, 80)), ("poppy", (200, 40, 40)), ("dandelion", (240, 220, 60)), ("cornflower", (80, 110, 220)),
    ("allium", (180, 110, 220)), ("bluet", (220, 230, 240)), ("daisy", (240, 240, 230)), ("valley", (240, 240, 240)),
]


def color(s):
    for k, c in COLORS:
        if k in s:
            return c
    return (170, 150, 130)


def main():
    src, out = sys.argv[1], sys.argv[2]
    scale = int(sys.argv[3]) if len(sys.argv) > 3 else 2
    d = alib.sbd.load(src)
    names = [e["state"] for e in d.palette]
    air = np.array([n.endswith(":air") for n in names])
    solid = ~air[d.ids]
    sy = d.ids.shape[0]
    rev = solid[::-1]
    top = sy - 1 - rev.argmax(axis=0)
    sz, sx = top.shape
    tid = d.ids[top, np.arange(sz)[:, None], np.arange(sx)[None, :]]
    pal = np.array([color(n.split("[")[0]) for n in names], dtype=float)
    img = pal[tid]
    gz, gx = np.gradient(top.astype(float))
    shade = np.clip(1.0 + (gx * -0.6 + gz * -0.6) * 0.18, 0.55, 1.35)
    img = np.clip(img * shade[..., None], 0, 255).astype(np.uint8)
    Image.fromarray(img).resize((sx * scale, sz * scale), Image.NEAREST).save(out)
    print(out, top.min() + d.origin[1], top.max() + d.origin[1])


if __name__ == "__main__":
    main()
