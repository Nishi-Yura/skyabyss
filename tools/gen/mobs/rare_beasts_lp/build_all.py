#!/usr/bin/env python3
"""逃げる獣 5 体の低ポリモデルを作って入れる (2026-11-03 の選定。docs/design/44 の 7 章)。

    python -X utf8 -I tools/gen/mobs/rare_beasts_lp/build_all.py [id ...] [--no-install]

一体ごとに: beasts.py の <id>() で形・骨・動きを作り、アトラスを描き、assets へ入れ (blp.install)、
確かめの絵 docs/review/rare_beasts/<id>_sheet.png (四方と、動きの姿勢) を書く。
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import beasts  # noqa: E402
import blp  # noqa: E402

blp.REVIEW = blp.ROOT / "docs/review/rare_beasts"


def build_one(mid, install=True):
    t0 = time.time()
    m = beasts.ALL[mid]()
    size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, getattr(m, "hint", 512))
    tris = sum(len(f.verts) - 2 for f in m.F)
    info = dict(tris=tris, atlas=size, bones=len(m.bones), glow=int(glow.sum()))
    print(mid, info, f"{time.time() - t0:.1f}s", flush=True)
    if install:
        blp.install(m, mid, size, atlas, glow, m.gaits, beasts.BOUNDS.get(mid, (3, 3)))
    blp.sheet(m, atlas, glow, m.gaits, mid, info, getattr(m, "poses", ()))
    return info


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    for mid in (args or list(beasts.ALL)):
        build_one(mid, "--no-install" not in sys.argv)
