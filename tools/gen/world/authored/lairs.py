# -*- coding: utf-8 -*-
"""巨神の居場所と祠の広場 (world/continent/ContinentLairs と同じ読み方)。land.py と check_land.py から使う。

- 体の柱 (TitanVolume.coreColumns): 居場所の向きで回した世界の (x, z) と、体の底の高さ (base + lo)
- 体の下となじませの幅 (ContinentLairs.LIP = 40) と祠の広場 (PAD_R 7 + PAD_BLEND 16) は、書き出した世界の地面のまま守る
"""
import json
import math
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
RES = ROOT / "src" / "main" / "resources" / "data" / "skyabyss"
LIP = 40
PAD_R = 7
PAD_BLEND = 16


def _lairs_json():
    return json.loads((RES / "continent" / "lairs.json").read_text(encoding="utf-8"))


def shrines():
    return {k: tuple(v) for k, v in _lairs_json().get("shrines", {}).items()}


def body_columns():
    """[(titan, x, z, bottom), ...] 地に降りた居場所の体の柱 (ContinentLairsSelfTest と同じ x, z と底)"""
    out = []
    for name, l in _lairs_json().items():
        if not isinstance(l, dict) or name == "shrines" or "base" not in l or "volume" not in l:
            continue
        vol = json.loads((RES / "titan_volumes" / (l["volume"] + ".json")).read_text(encoding="utf-8"))
        yaw = math.radians(l["yaw"])
        fx, fz = -math.sin(yaw), math.cos(yaw)
        nz = vol["nz"]
        for i in range(vol["nx"]):
            for j in range(nz):
                sp = vol["core"][i * nz + j]
                if len(sp) < 2:
                    continue
                side = vol["x0"] + (i + 0.5) * vol["cell"]
                fwd = vol["z0"] + (j + 0.5) * vol["cell"]
                x = math.floor(l["x"] + fx * fwd + fz * side)
                z = math.floor(l["z"] + fz * fwd - fx * side)
                out.append((name, x, z, l["base"] + sp[0] / 2.0))
    return out


def zone(X0, Z0, W, D):
    """(D, W) の bool: 体の下となじませの幅 (体の柱から LIP + 3 以内) と祠の広場 (半径 PAD_R + PAD_BLEND + 2)"""
    from scipy import ndimage
    body = np.zeros((D, W), dtype=bool)
    for _, x, z, _ in body_columns():
        i, k = z - Z0, x - X0
        if 0 <= i < D and 0 <= k < W:
            body[max(0, i - 1):i + 2, max(0, k - 1):k + 2] = True
    out = np.zeros((D, W), dtype=bool)
    if body.any():
        out |= ndimage.distance_transform_edt(~body) <= LIP + 3
    zs, xs = np.mgrid[0:D, 0:W]
    for (sx, sz) in shrines().values():
        out |= (xs + X0 - sx) ** 2 + (zs + Z0 - sz) ** 2 <= (PAD_R + PAD_BLEND + 2) ** 2
    return out
