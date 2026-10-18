"""今のモデル (箱) の描画用: geo の本体と弓を voxel.load_geo で面にする。"""
import copy
import json

import numpy as np

from common import ASSETS, voxel


def old_faces(pose=None, string_pose=None, only_skull=False, sign=-1):
    """pose は {骨: (rx, ry, rz)} (アニメの値)。sign=-1 でアニメの回転を反転して voxel に渡す。"""
    pose = {k: tuple(sign * a for a in v) for k, v in (pose or {}).items()}
    sp = {k: tuple(sign * a for a in v) for k, v in (string_pose or {}).items()}
    eg = json.loads((ASSETS / "geo/entity/rib_archer.geo.json").read_text(encoding="utf-8"))
    bg = json.loads((ASSETS / "geo/gear/rib_archer_bow.geo.json").read_text(encoding="utf-8"))
    for g in (eg,):
        for b in g["minecraft:geometry"][0]["bones"]:
            if b["name"] == "socket_off":
                b["rotation"] = [sign * a for a in b["rotation"]]
    ent = copy.deepcopy(eg)
    bones = ent["minecraft:geometry"][0]["bones"]
    if only_skull:
        for b in bones:
            if b["name"] != "skull":
                b["cubes"] = []
    n_ent = sum(len(b.get("cubes", [])) for b in eg["minecraft:geometry"][0]["bones"])
    n_bow = sum(len(b.get("cubes", [])) for b in bg["minecraft:geometry"][0]["bones"])
    faces = voxel.load_geo(ent, ASSETS / "textures/entity/rib_archer.png", ASSETS / "textures/entity/rib_archer_glowmask.png", pose)
    if only_skull:
        return faces, n_ent, n_bow
    g2 = copy.deepcopy(eg)
    g2["minecraft:geometry"][0]["description"] = bg["minecraft:geometry"][0]["description"]
    bones2 = g2["minecraft:geometry"][0]["bones"]
    for b in bones2:
        b["cubes"] = []
    off = np.array([3.0, 11.5, 0.0])
    for b in bg["minecraft:geometry"][0]["bones"]:
        nb = copy.deepcopy(b)
        nb["parent"] = "socket_off" if b["name"] == "bow" else b.get("parent", "bow")
        nb["pivot"] = [float(x) for x in (np.array(b.get("pivot", [0, 0, 0])) + off)]
        for c in nb.get("cubes", []):
            c["origin"] = [float(x) for x in (np.array(c["origin"]) + off)]
        if nb["name"] in sp:
            nb["rotation"] = [float(x) for x in sp[nb["name"]]]
        bones2.append(nb)
    bow = voxel.load_geo(g2, ASSETS / "textures/gear/rib_archer_bow.png", None, pose)
    return faces + bow, n_ent, n_bow
