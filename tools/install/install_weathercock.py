#!/usr/bin/env python3
"""Install the grandfather's weathercock held item (2026-10-28 pick: A, the brass rooster on a short staff).
Source: tools/rough/lowpoly/weapons/weathercock/build_weathercock.py -> docs/review/roughs_weathercock/raw/weathercock/a/
Held like a staff (the analog model is the ember war axe's item model, same as the gathering tools).
    python -I tools/install/install_weathercock.py
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "tools/install"), str(ROOT / "tools/lib"), str(ROOT / "tools/rough/lowpoly"), str(ROOT / "tools/rough/lowpoly/weapons")]

import install_job_weapons as IJ  # noqa: E402

if __name__ == "__main__":
    IJ.ITEMS["weathercock"] = ("roughs_weathercock", "common", "ember_war_axe", 1)
    IJ.install("weathercock", "a")
