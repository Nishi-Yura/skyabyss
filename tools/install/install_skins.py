#!/usr/bin/env python3
"""スキン (docs/design/54 の 6.1。見た目だけの一式) を入れる。
入力: tools/rough/lowpoly/armor/skin_one.py install の出力 docs/review/skins/raw/<名前>/<部位>_{mesh.json,atlas.png,glowmask.png}
出力 (何度流しても同じ):
  assets/skyabyss/lowpoly/armor/skin_<名前>/<部位>.json と textures/lowpoly/armor/skin_<名前>/<部位>(_glowmask).png
使い方: python tools/install/install_skins.py [名前 ...]
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "tools/install")]
from install_armor_one import put_mesh, put_tex, ASSETS, PIECES  # noqa: E402

SRC = ROOT / "docs/review/skins/raw"


def main():
    names = sys.argv[1:] or sorted(p.name for p in SRC.iterdir() if p.is_dir())
    for key in names:
        total = 0
        for p in PIECES:
            mesh = SRC / key / f"{p}_mesh.json"
            if not mesh.exists():
                continue
            total += put_mesh(mesh, ASSETS / "lowpoly/armor" / f"skin_{key}" / f"{p}.json", f"armor/skin_{key}/{p}")
            put_tex(SRC / key / f"{p}_atlas.png", SRC / key / f"{p}_glowmask.png", ASSETS / "textures/lowpoly/armor" / f"skin_{key}" / p)
        print(key, total, "triangles")


if __name__ == "__main__":
    main()
