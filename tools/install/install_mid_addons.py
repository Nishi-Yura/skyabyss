#!/usr/bin/env python3
"""残りのボス 7 体 (ダンジョンの中ボス 4 体・肋/白殻/鼓動まねの主) の作り込みのラフで選んだ案を入れる (2026-11-11 の選び。
ラフは tools/rough/lowpoly/mid_lord_bosses/build.py)。入れ方は終章と同じ (tools/install/install_finale_addons.py の install)。
元は tools/sources/mid_addons_base/。層の主は install_boss_split.py の塗り直しのあとの姿が元: あちらを流し直したら、
このボスの元 (mid_addons_base/lord_*) を消してからこれを流し直す。

    python -X utf8 -I tools/install/install_mid_addons.py
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
ml = load("mid_lord_bosses", ROOT / "tools/rough/lowpoly/mid_lord_bosses/build.py")

# 選んだ案 (2026-11-11): 0 = A, 1 = B, 2 = C
PICKS = {"pit_foreman": 1, "bell_hermit": 1, "geode_golem": 0, "ox_smith": 1, "lord_rib": 0, "lord_salt": 2, "lord_storm": 0}

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    fi.bs.PAL.update(ml.bs.PAL)
    for name, (title, glow, opts) in ml.BOSSES.items():
        fi.fb.BOSSES[name] = (title, "", "", glow, opts)
    for name, k in PICKS.items():
        glow = ml.BOSSES[name][1]
        label, n, nb, na = fi.install(name, k, glow_ramp=glow, base_dir=ROOT / "tools/sources/mid_addons_base")
        print(name, label, "tris", n, "motion bones", nb, "anims", na, flush=True)
