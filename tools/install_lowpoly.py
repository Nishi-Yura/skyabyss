#!/usr/bin/env python3
"""Install a low-poly mesh model (docs/design/15_high_poly.md, section 9).

    python tools/install_lowpoly.py <name> [--mesh M.json] [--atlas A.png] [--glow G.png]
                                    [--geo-name N] [--split BONE:SOCKET:GEARNAME]

<name> is the mob's model name (geo/entity/<name>.geo.json, animations use the same name).
Defaults: docs/review/lowpoly_roughs/<name>_lowpoly_mesh.json and <name>_lowpoly_atlas.png.

Writes
  assets/skyabyss/lowpoly/<name>.json            mesh (the "texture" field is rewritten)
  assets/skyabyss/textures/lowpoly/<name>.png    atlas (+ <name>_glow.png: glow pixels only, transparent elsewhere)
  assets/skyabyss/geo/entity/<name>_lowpoly.geo.json         skeleton: same bone names/pivots as the mesh, no cubes
  assets/skyabyss/geo/entity/<name>_lowpoly_debug.geo.json   one bounding box cube per bone (alignment check)
  assets/skyabyss/textures/lowpoly/debug.png                 translucent red for the debug boxes
The original cube geo (geo/entity/<name>.geo.json) and its animations are never touched, so reverting is one flag.

--split BONE:SOCKET:GEARNAME moves BONE out of the mesh into its own mesh lowpoly/GEARNAME.json
(coordinates relative to the pivot = the socket's origin) and puts a cube-less bone SOCKET at its pivot
in the skeleton, so an animation that moves SOCKET (e.g. "block") still moves it. Draw it with
GearLayer.lowPoly(renderer, SOCKET, GEARNAME). Repeat for several bones.
"""
import argparse
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"))


def bbox(vertices):
    lo = [min(v[i] for v in vertices) for i in range(3)]
    hi = [max(v[i] for v in vertices) for i in range(3)]
    return lo, hi


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--mesh")
    ap.add_argument("--atlas")
    ap.add_argument("--glow")
    ap.add_argument("--geo-name")
    ap.add_argument("--split", action="append", default=[])
    a = ap.parse_args()

    rough = os.path.join(ROOT, "docs", "review", "lowpoly_roughs")
    mesh_path = a.mesh or os.path.join(rough, a.name + "_lowpoly_mesh.json")
    atlas_path = a.atlas or os.path.join(rough, a.name + "_lowpoly_atlas.png")
    mesh = json.load(open(mesh_path, encoding="utf-8"))
    geo_name = a.geo_name or a.name

    # split bones -> gear meshes
    sockets = {}  # socket bone name -> (parent, pivot)
    for spec in a.split:
        bone, socket, gear = spec.split(":")
        b = mesh["bones"].pop(bone)
        p = b["pivot"]
        rel = dict(b)
        rel["parent"] = None
        rel["vertices"] = [[round(v[i] - p[i], 5) for i in range(3)] for v in b["vertices"]]
        gear_mesh = {k: v for k, v in mesh.items() if k != "bones"}
        gear_mesh["name"] = gear
        gear_mesh["socket"] = True  # vertices are relative to the socket pivot
        gear_mesh["bones"] = {socket: rel}
        gear_mesh["triangle_count"] = len(b["triangles"])
        gear_mesh["texture"] = a.name  # the gear shares the owner atlas
        write_json(os.path.join(ASSETS, "lowpoly", gear + ".json"), gear_mesh)
        sockets[socket] = (b["parent"], p, rel)
        print("split %s -> lowpoly/%s.json (socket %s, %d tris)" % (bone, gear, socket, len(b["triangles"])))
    mesh["triangle_count"] = sum(len(b["triangles"]) for b in mesh["bones"].values())
    mesh["name"] = a.name
    mesh["texture"] = a.name
    write_json(os.path.join(ASSETS, "lowpoly", a.name + ".json"), mesh)

    tex_dir = os.path.join(ASSETS, "textures", "lowpoly")
    os.makedirs(tex_dir, exist_ok=True)
    shutil.copyfile(atlas_path, os.path.join(tex_dir, a.name + ".png"))
    if a.glow:
        shutil.copyfile(a.glow, os.path.join(tex_dir, a.name + "_glow.png"))

    # bones the mesh lacks but the original geo has (sockets...) keep their original place unless split
    orig_path = os.path.join(ASSETS, "geo", "entity", a.name + ".geo.json")
    desc = {"identifier": "geometry." + geo_name + "_lowpoly", "texture_width": 16, "texture_height": 16,
            "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]}
    extra = {}
    if os.path.exists(orig_path):
        orig = json.load(open(orig_path, encoding="utf-8"))["minecraft:geometry"][0]
        d = orig["description"]
        for k in ("visible_bounds_width", "visible_bounds_height", "visible_bounds_offset"):
            if k in d:
                desc[k] = d[k]
        for b in orig["bones"]:
            if b["name"] not in mesh["bones"]:
                extra[b["name"]] = (b.get("parent"), b["pivot"])
    for s, (parent, pivot, _) in sockets.items():
        extra[s] = (parent, pivot)

    bones = []
    for n, b in mesh["bones"].items():
        e = {"name": n, "pivot": b["pivot"]}
        if b.get("parent"):
            e["parent"] = b["parent"]
        bones.append(e)
    for n, (parent, pivot) in extra.items():
        e = {"name": n, "pivot": pivot}
        if parent:
            e["parent"] = parent
        bones.append(e)
    skeleton = {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": bones}]}
    out_geo = os.path.join(ASSETS, "geo", "entity", geo_name + "_lowpoly.geo.json")
    write_json(out_geo, skeleton)

    # debug geo: one box cube per bone (the bounding box of its mesh), same pivots
    dbg_bones = []
    for e in bones:
        b = mesh["bones"].get(e["name"])
        verts = None
        if b:
            verts = b["vertices"]
        elif e["name"] in sockets:
            rel = sockets[e["name"]][2]
            p = sockets[e["name"]][1]
            verts = [[v[i] + p[i] for i in range(3)] for v in rel["vertices"]]
        d = dict(e)
        if verts:
            lo, hi = bbox(verts)
            d["cubes"] = [{"origin": [round(x, 4) for x in lo], "size": [round(hi[i] - lo[i], 4) for i in range(3)],
                           "uv": [0, 0]}]
        dbg_bones.append(d)
    ddesc = dict(desc)
    ddesc["identifier"] = "geometry." + geo_name + "_lowpoly_debug"
    ddesc["texture_width"] = 64
    ddesc["texture_height"] = 64
    write_json(os.path.join(ASSETS, "geo", "entity", geo_name + "_lowpoly_debug.geo.json"),
               {"format_version": "1.12.0", "minecraft:geometry": [{"description": ddesc, "bones": dbg_bones}]})

    dbg_png = os.path.join(tex_dir, "debug.png")
    if not os.path.exists(dbg_png):
        from PIL import Image
        Image.new("RGBA", (64, 64), (255, 40, 40, 90)).save(dbg_png)

    print("installed %s: %d bones, %d triangles -> %s" % (a.name, len(bones), mesh["triangle_count"], os.path.relpath(out_geo, ROOT)))


if __name__ == "__main__":
    sys.exit(main())
