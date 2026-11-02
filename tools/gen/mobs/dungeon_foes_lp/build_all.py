#!/usr/bin/env python3
"""地下のダンジョン専用の敵 12 体の低ポリモデルを作って入れる (docs/design/55 の 4 の 7、00 の 372〜383)。

    python -X utf8 -I tools/gen/mobs/dungeon_foes_lp/build_all.py [id ...] [--no-install]

一体ごとに: foes.py の <id>() で形・骨・動きを作り、アトラスを描き、assets へ入れ (blp.install)、
確かめの絵 docs/review/dungeon_foes/<id>_sheet.png (四方と、動きの姿勢) を書く。
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import foes  # noqa: E402
import blp  # noqa: E402

blp.REVIEW = blp.ROOT / "docs/review/dungeon_foes"


def build_one(mid, install=True):
    t0 = time.time()
    m = foes.ALL[mid]()
    size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, getattr(m, "hint", 512))
    tris = sum(len(f.verts) - 2 for f in m.F)
    info = dict(tris=tris, atlas=size, bones=len(m.bones), glow=int(glow.sum()))
    print(mid, info, f"{time.time() - t0:.1f}s", flush=True)
    if install:
        blp.install(m, mid, size, atlas, glow, m.gaits, foes.BOUNDS.get(mid, (3, 3)))
    blp.sheet(m, atlas, glow, m.gaits, mid, info, getattr(m, "poses", ()))
    return info


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    for mid in (args or list(foes.ALL)):
        build_one(mid, "--no-install" not in sys.argv)
