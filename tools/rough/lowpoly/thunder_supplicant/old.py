"""今のモデル (箱): geo の本体と杖を voxel.load_geo で面にする。"""
import copy
import json

import numpy as np

from common import ASSETS, SOCKET, voxel


def old_faces(pose=None, staff=True, body=True):
    """pose は {骨: (rx, ry, rz)} (アニメの値、REST は含めない)。voxel にはアニメの回転を反転して渡す。"""
    pose = {k: tuple(-a for a in v) for k, v in (pose or {}).items()}
    eg = json.loads((ASSETS / "geo/entity/thunder_supplicant.geo.json").read_text(encoding="utf-8"))
    bg = json.loads((ASSETS / "geo/gear/supplicant_staff.geo.json").read_text(encoding="utf-8"))
    for b in eg["minecraft:geometry"][0]["bones"]:
        if b["name"] == "socket_main":
            b["rotation"] = [-a for a in b["rotation"]]
    if not body:
        for b in eg["minecraft:geometry"][0]["bones"]:
            b["cubes"] = []
    n_ent = sum(len(b.get("cubes", [])) for b in eg["minecraft:geometry"][0]["bones"])
    n_st = sum(len(b.get("cubes", [])) for b in bg["minecraft:geometry"][0]["bones"])
    faces = voxel.load_geo(eg, ASSETS / "textures/entity/thunder_supplicant.png",
                           ASSETS / "textures/entity/thunder_supplicant_glowmask.png", pose)
    if not staff:
        return faces, n_ent, n_st
    g2 = copy.deepcopy(eg)
    g2["minecraft:geometry"][0]["description"] = bg["minecraft:geometry"][0]["description"]
    bones2 = g2["minecraft:geometry"][0]["bones"]
    for b in bones2:
        b["cubes"] = []
    for b in bg["minecraft:geometry"][0]["bones"]:
        nb = copy.deepcopy(b)
        nb["parent"] = "socket_main"
        nb["pivot"] = [float(x) for x in (np.array(b.get("pivot", [0, 0, 0])) + SOCKET)]
        for c in nb.get("cubes", []):
            c["origin"] = [float(x) for x in (np.array(c["origin"]) + SOCKET)]
        bones2.append(nb)
    st = voxel.load_geo(g2, ASSETS / "textures/gear/supplicant_staff.png", None, pose)
    return (faces if body else []) + st, n_ent, n_st
