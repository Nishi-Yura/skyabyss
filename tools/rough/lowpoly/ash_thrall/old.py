"""今のモデル (箱): geo の本体と火掻き棒を voxel.load_geo で面にする。state: n = 殻の姿 / f = 燃え上がり。"""
import copy
import json

import numpy as np

from common import ASSETS, SOCKET, voxel


def old_faces(pose=None, weapon=True, body=True, state="n"):
    pose = {k: tuple(-a for a in v) for k, v in (pose or {}).items()}
    eg = json.loads((ASSETS / "geo/entity/ash_thrall.geo.json").read_text(encoding="utf-8"))
    bg = json.loads((ASSETS / "geo/gear/ash_poker.geo.json").read_text(encoding="utf-8"))
    hide = "flare_" if state == "n" else "crust_"
    n_ent = 0
    for b in eg["minecraft:geometry"][0]["bones"]:
        if b["name"] == "socket_main":
            b["rotation"] = [-a for a in b["rotation"]]
        if b["name"].startswith(hide) or not body:
            b["cubes"] = []
        n_ent += len(b.get("cubes", []))
    n_w = sum(len(b.get("cubes", [])) for b in bg["minecraft:geometry"][0]["bones"])
    tn = "ash_thrall" if state == "n" else "ash_thrall_flared"
    faces = voxel.load_geo(eg, ASSETS / f"textures/entity/{tn}.png", ASSETS / f"textures/entity/{tn}_glowmask.png", pose)
    if not weapon:
        return faces, n_ent, n_w
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
    st = voxel.load_geo(g2, ASSETS / "textures/gear/ash_poker.png", ASSETS / "textures/gear/ash_poker_glowmask.png", pose)
    return (faces if body else []) + st, n_ent, n_w
