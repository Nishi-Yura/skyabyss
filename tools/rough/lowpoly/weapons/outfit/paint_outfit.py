"""市の品の絵。祠の始めの武器の絵 (../starter/paint_starter.py) をそのまま使う。光る所は無いので、光の色は鈍い灰 (使わない)。"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "starter"))
import paint_starter as PS  # noqa: E402

PS.PAL["G_none"] = np.array([(70, 70, 74), (110, 110, 116), (150, 150, 156), (190, 190, 196)])
GLOW_RGB = dict(none=(150, 150, 156))
PAL = PS.PAL
paint = PS.paint
