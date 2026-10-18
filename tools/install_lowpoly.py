#!/usr/bin/env python3
"""Install a low-poly mesh model (docs/design/15_high_poly.md, section 9).

    python tools/install_lowpoly.py <name> [--mesh M.json] [--atlas A.png] [--glow G.png]
                                    [--geo-name N] [--split BONE:SOCKET:GEARNAME]
                                    [--gear MESH.json:ATLAS.png:NAME:SOCKET]

<name> is the mob's model name (geo/entity/<name>.geo.json, animations use the same name).
Defaults: docs/review/lowpoly_roughs/<name>_lowpoly_mesh.json and <name>_lowpoly_atlas.png.
A multi-phase model (the hearts) is installed once per phase: name = stone_whale_heart_p1 ... with --mesh/--atlas/--glow.

Writes
  assets/skyabyss/lowpoly/<name>.json            mesh (the "texture" field is rewritten)
  assets/skyabyss/textures/lowpoly/<name>.png    atlas (+ <name>_glowmask.png when it has lit pixels; an empty mask is never installed)
  assets/skyabyss/geo/entity/<name>_lowpoly.geo.json         skeleton: same bone names/parents/pivots/REST ROTATIONS as the original geo, no cubes
  assets/skyabyss/geo/entity/<name>_lowpoly_debug.geo.json   one bounding box cube per bone (alignment check)
  assets/skyabyss/textures/lowpoly/debug.png                 translucent red for the debug boxes
The original cube geo (geo/entity/<name>.geo.json) and its animations are never touched, so reverting is one flag.

Bone data: the mesh JSON wins where it states parent / rotation; otherwise the original geo does. Rest rotations
(a root yawed 180, the heart's plates...) must be in the skeleton: GeckoLib applies them to the pose, and the mesh
vertices are in the pre-rotation space of the geo cubes (exactly like a cube under a rotated bone).

--split BONE:SOCKET:GEARNAME moves BONE out of the mesh into its own mesh lowpoly/GEARNAME.json
(coordinates relative to the pivot = the socket's origin) and puts a cube-less bone SOCKET at its pivot
in the skeleton, so an animation that moves SOCKET (e.g. "block") still moves it. Draw it with
GearLayer.lowPoly(renderer, SOCKET, GEARNAME). Repeat for several bones.

--gear MESH.json:ATLAS.png:NAME:SOCKET installs a separate socket mesh (vertices relative to the socket's pivot) with its
own atlas. <atlas stem>_glowmask.png next to the atlas (white = lit) becomes textures/lowpoly/NAME_glow.png (atlas colours of
the lit pixels). A gear mesh with several bones (a bow with strings) also gets geo/gear/NAME_lowpoly.geo.json (bones only)
so animations/gear/NAME.animation.json moves them (GearLayer.lowPolyAnimated).
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


def bake_glow(atlas_path, mask_path):
    """Atlas colours where the mask is lit (alpha > 0), transparent elsewhere. Returns (image, lit pixel count)."""
    from PIL import Image
    a = Image.open(atlas_path).convert("RGBA")
    m = Image.open(mask_path).convert("RGBA")
    out = Image.new("RGBA", a.size, (0, 0, 0, 0))
    lit = 0
    for y in range(a.size[1]):
        for x in range(a.size[0]):
            if m.getpixel((x, y))[3] > 0:
                out.putpixel((x, y), a.getpixel((x, y)))
                lit += 1
    return out, lit


def bbox(vertices):
    lo = [min(v[i] for v in vertices) for i in range(3)]
    hi = [max(v[i] for v in vertices) for i in range(3)]
    return lo, hi


def rest_rot(rot):
    return list(rot) if rot and any(abs(x) > 1e-9 for x in rot) else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--mesh")
    ap.add_argument("--atlas")
    ap.add_argument("--glow")
    ap.add_argument("--geo-name")
    ap.add_argument("--split", action="append", default=[])
    ap.add_argument("--gear", action="append", default=[],
                    help="MESH.json:ATLAS.png:NAME:SOCKET - a separate socket mesh with its own atlas (vertices relative to the socket pivot)")
    a = ap.parse_args()

    rough = os.path.join(ROOT, "docs", "review", "lowpoly_roughs")
    mesh_path = a.mesh or os.path.join(rough, a.name + "_lowpoly_mesh.json")
    atlas_path = a.atlas or os.path.join(rough, a.name + "_lowpoly_atlas.png")
    mesh = json.load(open(mesh_path, encoding="utf-8"))
    geo_name = a.geo_name or a.name

    # split bones -> gear meshes
    gear_rel = {}
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
    tex_dir = os.path.join(ASSETS, "textures", "lowpoly")
    os.makedirs(tex_dir, exist_ok=True)
    for spec in a.gear:
        gm_path, ga_path, gname, socket = spec.split(":", 3)
        gm = json.load(open(gm_path, encoding="utf-8"))
        bone = next(iter(gm["bones"].values()))
        gm["socket"] = True
        gm["name"] = gname
        gm["texture"] = gname
        gm["triangle_count"] = sum(len(b["triangles"]) for b in gm["bones"].values())
        write_json(os.path.join(ASSETS, "lowpoly", gname + ".json"), gm)
        shutil.copyfile(ga_path, os.path.join(tex_dir, gname + ".png"))
        stem = ga_path[:-len("_atlas.png")] if ga_path.endswith("_atlas.png") else ga_path[:-4]
        gmask = stem + "_glowmask.png"
        glow_path = os.path.join(tex_dir, gname + "_glow.png")
        if os.path.exists(glow_path):
            os.remove(glow_path)
        if os.path.exists(gmask):
            baked, lit = bake_glow(ga_path, gmask)
            if lit:
                baked.save(glow_path)
                print("gear glow baked: %d lit pixels" % lit)
        gear_rel[socket] = bone
        if len(gm["bones"]) > 1:
            gbones = []
            for n, b in gm["bones"].items():
                e = {"name": n, "pivot": b["pivot"]}
                if b.get("parent"):
                    e["parent"] = b["parent"]
                if rest_rot(b.get("rotation")):
                    e["rotation"] = b["rotation"]
                gbones.append(e)
            gdesc = {"identifier": "geometry." + gname + "_lowpoly", "texture_width": 16, "texture_height": 16,
                     "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0, 0]}
            write_json(os.path.join(ASSETS, "geo", "gear", gname + "_lowpoly.geo.json"),
                       {"format_version": "1.12.0", "minecraft:geometry": [{"description": gdesc, "bones": gbones}]})
            print("gear skeleton geo/gear/%s_lowpoly.geo.json (%d bones)" % (gname, len(gbones)))
        print("gear %s -> lowpoly/%s.json (socket %s, %d tris)" % (gm_path, gname, socket, gm["triangle_count"]))
    mesh["triangle_count"] = sum(len(b["triangles"]) for b in mesh["bones"].values())
    mesh["name"] = a.name
    mesh["texture"] = a.name
    write_json(os.path.join(ASSETS, "lowpoly", a.name + ".json"), mesh)

    shutil.copyfile(atlas_path, os.path.join(tex_dir, a.name + ".png"))
    glow = a.glow or os.path.join(rough, a.name + "_lowpoly_glowmask.png")
    mask_dst = os.path.join(tex_dir, a.name + "_glowmask.png")
    if os.path.exists(mask_dst):
        os.remove(mask_dst)
    if os.path.exists(glow):
        from PIL import Image
        lit = sum(1 for px in Image.open(glow).convert("RGBA").getdata() if px[3] > 0)
        if lit:
            shutil.copyfile(glow, mask_dst)
            print("glowmask installed (%d lit pixels)" % lit)
        else:
            print("glowmask has no lit pixel -> not installed (GeckoLib crashes on an empty mask)")

    # Bones: name / parent / pivot / rest rotation. Mesh JSON wins where it states them, otherwise the original geo.
    # Bones the mesh lacks (sockets...) are carried over from the original geo, rotation included.
    orig_path = os.path.join(ASSETS, "geo", "entity", a.name + ".geo.json")
    desc = {"identifier": "geometry." + geo_name + "_lowpoly", "texture_width": 16, "texture_height": 16,
            "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]}
    orig_bones = {}
    if os.path.exists(orig_path):
        orig = json.load(open(orig_path, encoding="utf-8"))["minecraft:geometry"][0]
        d = orig["description"]
        for k in ("visible_bounds_width", "visible_bounds_height", "visible_bounds_offset"):
            if k in d:
                desc[k] = d[k]
        orig_bones = {b["name"]: b for b in orig["bones"]}
    extra = {}
    for n, b in orig_bones.items():
        if n not in mesh["bones"]:
            extra[n] = (b.get("parent"), b["pivot"], b.get("rotation"))
    for s_, (parent, pivot, _) in sockets.items():
        extra[s_] = (parent, pivot, orig_bones.get(s_, {}).get("rotation"))

    bones = []
    for n, b in mesh["bones"].items():
        o = orig_bones.get(n, {})
        parent = b["parent"] if "parent" in b else o.get("parent")
        rot = rest_rot(b["rotation"] if "rotation" in b else o.get("rotation"))
        if o:
            if (o.get("parent") or None) != (parent or None):
                print("WARNING parent differs from the original geo: %s %s vs %s" % (n, o.get("parent"), parent))
            if any(abs(o["pivot"][i] - b["pivot"][i]) > 1e-4 for i in range(3)):
                print("WARNING pivot differs from the original geo: %s" % n)
            if rest_rot(o.get("rotation")) != rot:
                print("WARNING rotation differs from the original geo: %s %s vs %s" % (n, o.get("rotation"), rot))
        e = {"name": n, "pivot": b["pivot"]}
        if parent:
            e["parent"] = parent
        if rot:
            e["rotation"] = rot
        bones.append(e)
    for n, (parent, pivot, rot) in extra.items():
        e = {"name": n, "pivot": pivot}
        if parent:
            e["parent"] = parent
        if rest_rot(rot):
            e["rotation"] = rest_rot(rot)
        bones.append(e)
    skeleton = {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": bones}]}
    out_geo = os.path.join(ASSETS, "geo", "entity", geo_name + "_lowpoly.geo.json")
    write_json(out_geo, skeleton)

    # debug geo: one box cube per bone (the bounding box of its mesh), same pivots and rotations
    dbg_bones = []
    for e in bones:
        b = mesh["bones"].get(e["name"])
        verts = None
        if b and b["vertices"]:
            verts = b["vertices"]
        elif e["name"] in gear_rel:
            p = e["pivot"]
            verts = [[v[i] + p[i] for i in range(3)] for v in gear_rel[e["name"]]["vertices"]]
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
