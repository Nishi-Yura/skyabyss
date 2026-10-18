"""今のモデル (箱) の描画用: geo の本体と鉤槍を voxel.load_geo で面にする。"""
import copy
import json

import numpy as np

from common import ASSETS, SOCKET, voxel


def old_faces(pose=None, only_head=False, sign=-1):
    pose = {k: tuple(sign * a for a in v) for k, v in (pose or {}).items()}
    eg = json.loads((ASSETS / "geo/entity/feather_warden.geo.json").read_text(encoding="utf-8"))
    gg = json.loads((ASSETS / "geo/gear/hooked_glaive.geo.json").read_text(encoding="utf-8"))
    for b in eg["minecraft:geometry"][0]["bones"]:
        if b["name"] == "socket_main":
            b["rotation"] = [sign * a for a in b["rotation"]]
    ent = copy.deepcopy(eg)
    bones = ent["minecraft:geometry"][0]["bones"]
    n_ent = sum(len(b.get("cubes", [])) for b in bones)
    n_gear = sum(len(b.get("cubes", [])) for b in gg["minecraft:geometry"][0]["bones"])
    if only_head:
        for b in bones:
            if b["name"] != "head":
                b["cubes"] = []
    faces = voxel.load_geo(ent, ASSETS / "textures/entity/feather_warden.png",
                           ASSETS / "textures/entity/feather_warden_glowmask.png", pose)
    if only_head:
        return faces, n_ent, n_gear
    g2 = copy.deepcopy(eg)
    g2["minecraft:geometry"][0]["description"] = gg["minecraft:geometry"][0]["description"]
    bones2 = g2["minecraft:geometry"][0]["bones"]
    for b in bones2:
        b["cubes"] = []
    off = np.array(SOCKET)
    for b in gg["minecraft:geometry"][0]["bones"]:
        nb = copy.deepcopy(b)
        nb["parent"] = "socket_main"
        nb["pivot"] = [float(x) for x in (np.array(b.get("pivot", [0, 0, 0])) + off)]
        for c in nb.get("cubes", []):
            c["origin"] = [float(x) for x in (np.array(c["origin"]) + off)]
        bones2.append(nb)
    gear = voxel.load_geo(g2, ASSETS / "textures/gear/hooked_glaive.png", ASSETS / "textures/gear/hooked_glaive_glowmask.png", pose)
    return faces + gear, n_ent, n_gear
