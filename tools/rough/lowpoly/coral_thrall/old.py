"""今のモデル (箱) の描画用: geo の本体と銛 (socket_main) を voxel.load_geo で面にする。"""
import copy
import json

import numpy as np

from common import ASSETS, voxel

SOCK = np.array([-3.75, 11.5, 2.5])


def old_faces(pose=None, only_head=False, statue=False, sign=-1):
    pose = {k: tuple(sign * a for a in v) for k, v in (pose or {}).items()}
    eg = json.loads((ASSETS / "geo/entity/coral_thrall.geo.json").read_text(encoding="utf-8"))
    sp = json.loads((ASSETS / "geo/gear/coral_spear.geo.json").read_text(encoding="utf-8"))
    bones = eg["minecraft:geometry"][0]["bones"]
    for b in bones:
        if b.get("rotation") and b["name"] != "root":
            b["rotation"] = [sign * a for a in b["rotation"]]
        if b["name"] == "statue_coral" and not statue:
            b["cubes"] = []
        if only_head and b["name"] != "head":
            b["cubes"] = []
    n_ent = sum(len(b.get("cubes", [])) for b in bones)
    n_sp = sum(len(b.get("cubes", [])) for b in sp["minecraft:geometry"][0]["bones"])
    tex = "textures/entity/coral_thrall_statue.png" if statue else "textures/entity/coral_thrall.png"
    gm = "textures/entity/coral_thrall_statue_glowmask.png" if statue else "textures/entity/coral_thrall_glowmask.png"
    faces = voxel.load_geo(eg, ASSETS / tex, ASSETS / gm, pose)
    if only_head:
        return faces, n_ent, n_sp
    g2 = copy.deepcopy(eg)
    g2["minecraft:geometry"][0]["description"] = sp["minecraft:geometry"][0]["description"]
    b2 = g2["minecraft:geometry"][0]["bones"]
    for b in b2:
        b["cubes"] = []
    for b in sp["minecraft:geometry"][0]["bones"]:
        nb = copy.deepcopy(b)
        nb["parent"] = "socket_main"
        nb["pivot"] = [float(x) for x in (np.array(b.get("pivot", [0, 0, 0])) + SOCK)]
        for c in nb.get("cubes", []):
            c["origin"] = [float(x) for x in (np.array(c["origin"]) + SOCK)]
        b2.append(nb)
    stex = "textures/gear/coral_spear_statue.png" if statue else "textures/gear/coral_spear.png"
    spear = voxel.load_geo(g2, ASSETS / stex, None, pose)
    return faces + spear, n_ent, n_sp
