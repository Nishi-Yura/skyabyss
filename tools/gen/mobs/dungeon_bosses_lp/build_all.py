#!/usr/bin/env python3
"""地下のダンジョンの主の低ポリモデルを作って入れる (docs/design/55 の 1 章の形)。

    python -X utf8 -I tools/gen/mobs/dungeon_bosses_lp/build_all.py [id ...] [--no-install]

一体ごとに: <id>.py の build() で形・骨・動きを作り、アトラスを描き、assets へ入れ (blp.install)、
確かめの絵 docs/review/dungeon_bosses/<id>_sheet.png (四方と、動きの姿勢) を書く。
"""
from __future__ import annotations

import importlib
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import blp  # noqa: E402

ORDER = ["bone_centipede", "shell_octopus", "crystal_lizard", "forge_giant"]


def build_one(mid, install=True):
    t0 = time.time()
    mod = importlib.import_module(mid)
    m = mod.build()
    size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, getattr(m, "hint", 512))
    tris = sum(len(f.verts) - 2 for f in m.F)
    info = dict(tris=tris, atlas=size, bones=len(m.bones), glow=int(glow.sum()))
    print(mid, info, f"{time.time() - t0:.1f}s", flush=True)
    if install:
        blp.install(m, mid, size, atlas, glow, m.gaits, getattr(mod, "BOUNDS", (8, 6)))
    blp.sheet(m, atlas, glow, m.gaits, mid, info, getattr(mod, "POSES", ()))
    return info


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    for mid in (args or ORDER):
        if (HERE / f"{mid}.py").exists():
            build_one(mid, "--no-install" not in sys.argv)
