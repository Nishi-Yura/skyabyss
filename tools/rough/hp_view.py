"""Shared view helpers for the high-poly roughs."""
import copy
import sys

sys.path.insert(0, "tools/rough")
from PIL import Image

import hpkit  # noqa: F401
import voxel

VIEWS = [("front", 180, 10), ("3/4 left", 215, 20), ("side", 270, 8), ("back 3/4", 35, 20)]


def gear_faces(base_geo, gear_geo, gear_tex, socket, pose=None, gear_glow=None):
    """Faces of a gear model attached to bone `socket` of base_geo (like tools/rough/sentinel_v4.py)."""
    g = copy.deepcopy(base_geo)
    gb = g["minecraft:geometry"][0]
    gg = gear_geo["minecraft:geometry"][0]
    gb["description"].update({k: gg["description"][k] for k in ("texture_width", "texture_height")})
    sock = next(b for b in gb["bones"] if b["name"] == socket)
    p = sock["pivot"]
    for b in gb["bones"]:
        b.pop("cubes", None)
    for b in gg["bones"]:
        nb = copy.deepcopy(b)
        nb["name"] = "gear_" + b["name"]
        nb["parent"] = socket if "parent" not in b else "gear_" + b["parent"]
        nb["pivot"] = [a + c for a, c in zip(b.get("pivot", [0, 0, 0]), p)]
        for c in nb.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
            if "pivot" in c:
                c["pivot"] = [a + q for a, q in zip(c["pivot"], p)]
        gb["bones"].append(nb)
    return voxel.load_geo(g, gear_tex, glowmask=gear_glow, pose=pose)


def shot(faces, yaw, pitch, size=(380, 380)):
    # game view = mirror of the file coordinates (tools/rough/sentinel_v4.py)
    im = voxel.render(faces, yaw=-yaw, pitch=pitch, size=size)
    return im.transpose(Image.FLIP_LEFT_RIGHT)


def triptych(faces_by_label, views=VIEWS, size=(380, 380)):
    """columns = models (label), rows = views"""
    return [(label, [shot(faces, y, p, size) for _, y, p in views]) for label, faces in faces_by_label]
