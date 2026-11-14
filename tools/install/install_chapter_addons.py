#!/usr/bin/env python3
"""章の道中のボス 8 体の作り込みのラフで選んだ案を入れる (2026-11-11 の選び。ラフは tools/rough/lowpoly/chapter_bosses/build.py)。
入れ方は終章と同じ (tools/install/install_finale_addons.py の install。同じ並びの絵 _flared・_glow なども揃えて広げる)。
元は tools/sources/chapter_addons_base/。

    python -X utf8 -I tools/install/install_chapter_addons.py
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
cb = load("chapter_bosses", ROOT / "tools/rough/lowpoly/chapter_bosses/build.py")

# 選んだ案 (2026-11-11): 0 = A, 1 = B, 2 = C
PICKS = {"elder_sentry": 0, "shore_warden": 0, "whalebone_captain": 1, "elder_thrall": 2, "ash_smith": 1, "scorched_warden": 0,
         "tide_beckoner": 2, "raiju": 0}

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    fi.bs.PAL.update(cb.bs.PAL)
    for name, (title, glow, opts) in cb.BOSSES.items():
        fi.fb.BOSSES[name] = (title, "", "", glow, opts)
    for name, k in PICKS.items():
        glow = cb.BOSSES[name][1]
        label, n, nb, na = fi.install(name, k, glow_ramp=glow, base_dir=ROOT / "tools/sources/chapter_addons_base")
        print(name, label, "tris", n, "motion bones", nb, "anims", na, flush=True)
