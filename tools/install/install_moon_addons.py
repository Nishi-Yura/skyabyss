#!/usr/bin/env python3
"""月の 5 体の作り込みのラフで選んだ案を入れる (2026-11-11 の選び。ラフは tools/rough/lowpoly/moon_bosses/build.py)。
入れ方は終章と同じ (tools/install/install_finale_addons.py の install。部品の絵は段ごとに暗く)。元は tools/sources/moon_addons_base/。
月の核の ring_c は 11/11 に輪の内を塞ぐ大きな三角形 (入れるときの口塞ぎの誤り) を外したあとの姿を元にする。

    python -X utf8 -I tools/install/install_moon_addons.py
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


fi = load("install_finale_addons", ROOT / "tools/install/install_finale_addons.py")
mb = load("moon_bosses", ROOT / "tools/rough/lowpoly/moon_bosses/build.py")

# 選んだ案 (2026-11-11): 0 = A, 1 = B, 2 = C
PICKS = {"moon_core": 2, "moon_scale": 1, "full_keeper": 2, "waning_blade": 1, "shadow_embracer": 1}

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    fi.bs.PAL.update(mb.bs.PAL)
    for name, (title, opts) in mb.BOSSES.items():
        fi.fb.BOSSES[name] = (title, "", "", "g_moon", opts)
    for name, k in PICKS.items():
        label, n, nb, na = fi.install(name, k, glow_ramp="g_moon", base_dir=ROOT / "tools/sources/moon_addons_base")
        print(name, label, "tris", n, "motion bones", nb, "anims", na, flush=True)
