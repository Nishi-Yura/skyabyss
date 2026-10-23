#!/usr/bin/env python3
"""Compare the entity-side anchor points of the four titans (boarding seats, chains, gate, mouth, perch) with the low-poly
near meshes and with the old cube geos (docs/design/15_high_poly.md 4.1).

    python tools/audit/titan_align_check.py

Coordinates: geo units (1/16 block, entity scale 8 -> 1 geo unit = 0.5 block), +X = the titan's left, front = -Z, y up.
For each point it prints the surface height of the mesh / cubes below and above it (top = highest surface at (x, z), under = highest
surface below the given y), so a seat that floats or sinks shows up as a difference between the wanted y and the surface.
"""
import json
import os

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")


def mesh_tris(name):
    m = json.load(open(os.path.join(A, "lowpoly", name + ".json"), encoding="utf-8"))
    out = []
    for b in m["bones"].values():
        v = np.array(b["vertices"], dtype=float)
        for t in b["triangles"]:
            out.append(v[list(t)])
    return np.array(out)


def cube_tris(name):
    g = json.load(open(os.path.join(ROOT, "tools", "sources", "geo_cube", "geo", "entity", name + ".geo.json"), encoding="utf-8"))["minecraft:geometry"][0]
    out = []
    for b in g["bones"]:
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            x0, y0, z0 = o
            x1, y1, z1 = x0 + s[0], y0 + s[1], z0 + s[2]
            P = lambda x, y, z: (x, y, z)
            quads = [
                [P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)],  # top
                [P(x0, y0, z0), P(x1, y0, z0), P(x1, y0, z1), P(x0, y0, z1)],  # bottom
                [P(x0, y0, z0), P(x0, y1, z0), P(x0, y1, z1), P(x0, y0, z1)],  # -x
                [P(x1, y0, z0), P(x1, y1, z0), P(x1, y1, z1), P(x1, y0, z1)],  # +x
                [P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)],  # -z
                [P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)],  # +z
            ]
            for q in quads:
                out.append(np.array([q[0], q[1], q[2]], dtype=float))
                out.append(np.array([q[0], q[2], q[3]], dtype=float))
    return np.array(out)


def ray_hits(tris, origin, direction):
    """parameters t of all hits of the ray origin + t*direction with the triangles (double sided)."""
    o = np.array(origin, dtype=float)
    d = np.array(direction, dtype=float)
    v0, v1, v2 = tris[:, 0], tris[:, 1], tris[:, 2]
    e1, e2 = v1 - v0, v2 - v0
    p = np.cross(d, e2)
    det = np.einsum("ij,ij->i", e1, p)
    ok = np.abs(det) > 1e-9
    inv = np.where(ok, 1.0 / np.where(ok, det, 1.0), 0.0)
    tv = o - v0
    u = np.einsum("ij,ij->i", tv, p) * inv
    q = np.cross(tv, e1)
    v = (q @ d) * inv
    t = np.einsum("ij,ij->i", e2, q) * inv
    hit = ok & (u >= -1e-6) & (v >= -1e-6) & (u + v <= 1 + 1e-6)
    return np.sort(t[hit])


def column(tris, x, z):
    """sorted y values where the vertical line (x, z) crosses a surface."""
    ts = ray_hits(tris, (x, -50, z), (0, 1, 0))
    return [round(-50 + t, 1) for t in ts]


def row_x(tris, y, z):
    ts = ray_hits(tris, (-400, y, z), (1, 0, 0))
    return [round(-400 + t, 1) for t in ts]


def row_z(tris, x, y):
    ts = ray_hits(tris, (x, y, -400), (0, 0, 1))
    return [round(-400 + t, 1) for t in ts]


def under(ys, y):
    below = [v for v in ys if v <= y + 0.51]
    return max(below) if below else None


def seat_report(title, name, seats, scale=2.0):
    """seats: (side, up, forward) in blocks (as the entity code has them). Reports the surface just under the seat in both models."""
    mesh, cube = mesh_tris(name), cube_tris(name)
    print("== %s seats (side, up, fwd blocks | wanted y geo | mesh surface under | cube surface under | mesh top)" % title)
    for s in seats:
        x, y, z = s[0] * scale, s[1] * scale, -s[2] * scale
        cm, cc = column(mesh, x, z), column(cube, x, z)
        print("  %-22s want %6.1f | mesh %6s | cube %6s | mesh top %6s  (blocks: mesh %s cube %s)" % (
            s, y, under(cm, y + 3), under(cc, y + 3), max(cm) if cm else None,
            None if under(cm, y + 3) is None else round(under(cm, y + 3) / scale, 2),
            None if under(cc, y + 3) is None else round(under(cc, y + 3) / scale, 2)))


if __name__ == "__main__":
    # --- stone whale (SEATS on the head, head chain, anchor stone chain)
    whale_seats = [(-1.5, 32.3, 46.0), (1.5, 32.3, 46.0), (-1.5, 32.2, 42.0), (1.5, 32.2, 42.0),
                   (-4.5, 31.7, 46.0), (4.5, 31.7, 46.0), (-4.5, 31.5, 42.0), (4.5, 31.5, 42.0)]
    seat_report("stone_whale", "stone_whale", whale_seats)
    mesh, cube = mesh_tris("stone_whale"), cube_tris("stone_whale")
    print("-- whale head chain (fwd 44 -> z=-88): path (side, up) -> mesh / cube side extent at that height")
    for side, up in [(6.0, 31.6), (13.0, 25.2), (14.5, 22.5)]:
        for nm, tr in (("mesh", mesh), ("cube", cube)):
            xs = row_x(tr, up * 2, -88)
            print("  %s (%4.1f, %4.1f) wanted x=%5.1f  surface crossings x: %s" % (nm, side, up, side * 2, xs))
    print("-- whale anchor stone (back 30.5 -> z=61): chain top (10.5, 14), anchor (10.5, 19.5)")
    for nm, tr in (("mesh", mesh), ("cube", cube)):
        for up in (14.0, 19.5):
            print("  %s up %4.1f: x crossings at z=61: %s" % (nm, up, row_x(tr, up * 2, 61)))
        print("  %s column x=21,z=61 (y crossings): %s" % (nm, column(tr, 21, 61)))
    # --- tide shell
    tide_seats = [(2.0, 34.4, 11.0), (-2.0, 34.4, 11.0), (6.0, 34.4, 11.0), (-6.0, 34.4, 11.0),
                  (2.0, 36.4, 17.0), (-2.0, 36.4, 17.0), (6.0, 36.4, 17.0), (-6.0, 36.4, 17.0)]
    seat_report("tide_shell", "tide_shell", tide_seats)
    mesh, cube = mesh_tris("tide_shell"), cube_tris("tide_shell")
    print("-- tide gate: mouth geo x 58..74, y 9..31, z 55..73 (blocks side 29..37, back 27.5..36.5, up 4.5..15.5)")
    for nm, tr in (("mesh", mesh), ("cube", cube)):
        for z in (56, 64, 72):
            print("  %s z=%d, y=20: x crossings %s" % (nm, z, row_x(tr, 20, z)))
        for x in (60, 66, 72):
            print("  %s x=%d: column y %s  | z crossings at y=20: %s" % (nm, x, column(tr, x, 64), row_z(tr, x, 20)))
    # --- thunder wing
    mesh, cube = mesh_tris("thunder_wing"), cube_tris("thunder_wing")
    print("-- thunder wing: left foot (side 6, fwd -4 -> x=12, z=8), gate at up 25 (y=50), head forward 60 (z=-120)")
    for nm, tr in (("mesh", mesh), ("cube", cube)):
        print("  %s column x=12,z=8: %s | column x=16,z=8 %s | z crossings x=0,y=50: %s | z crossings x=0 y=80: %s" % (
            nm, column(tr, 12, 8), column(tr, 16, 8), row_z(tr, 0, 50), row_z(tr, 0, 80)))
    # --- ember titan
    mesh, cube = mesh_tris("ember_titan"), cube_tris("ember_titan")
    print("-- ember mouth: fwd 81..85 -> z -170..-162, side +-2.5 -> x +-5, tongue top up 3 (y 6), mouth 4 high (y 6..14)")
    for nm, tr in (("mesh", mesh), ("cube", cube)):
        for z in (-172, -168, -166, -164, -160):
            print("  %s z=%d x=0: y crossings %s | x crossings at y=10: %s" % (nm, z, column(tr, 0, z), row_x(tr, 10, z)))
        print("  %s z crossings x=0 y=10: %s | x=0 y=7: %s | x=0 y=13: %s" % (nm, row_z(tr, 0, 10), row_z(tr, 0, 7), row_z(tr, 0, 13)))
    print("-- ember ridge (fwd 20 -> z=-40) crater rim should be up 68 (y=136)")
    for nm, tr in (("mesh", mesh), ("cube", cube)):
        print("  %s column x=0,z=-40: %s | column x=40,z=-40: %s | x=-60: %s" % (nm, column(tr, 0, -40), column(tr, 40, -40), column(tr, -60, -40)))
