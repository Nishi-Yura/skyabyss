#!/usr/bin/env python3
"""足した部品を「仕上げの形」で入れ直す (2026-11-11 の声「ラフレベルの見た目じゃない？」)。
部品の作り (管・棘・球) の分け目を増やしてから (install_finale_addons.py の FINE と同じ)、前に入れた道具をそのまま流し直す:
  install_boss_addons.py  心臓の番人の鍵の後光・灯り喰いの主の燠の腹
  install_heart_eyes.py   四つの心臓の目
灯り喰いの主は、このあと install_boss_split.py の元 (tools/sources/boss_split_base/lord_ash.*) を消して流し直す (塗り直しの元を新しい腹に)。

    python -X utf8 -I tools/install/fine_rerun.py
"""
from __future__ import annotations

import importlib.util
import runpy
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "tools/gen/mobs/dungeon_bosses_lp"), str(ROOT / "tools/gen/mobs/mounts_lp")]
import blp  # noqa: E402
import mlp  # noqa: E402
import mlp_extra  # noqa: E402

spec = importlib.util.spec_from_file_location("install_finale_addons", ROOT / "tools/install/install_finale_addons.py")
fi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fi)
(_, tub0, cone0, ell0), (tub, cone, ell) = fi._fine()
for mod in (blp, mlp, mlp_extra):
    mod.tub, mod.cone, mod.ell = tub, cone, ell

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for tool in ("install_boss_addons.py", "install_heart_eyes.py"):
        print("==", tool, flush=True)
        runpy.run_path(str(ROOT / "tools/install" / tool), run_name="__main__")
