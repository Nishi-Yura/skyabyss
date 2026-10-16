"""High-poly rough 4: NPCs and the watcher (storyteller, whale_chaser, watcher). 3 options vs now each.

  A  chamfer only      : one-step chamfer (0.5) on every box, nothing else (automatic)
  B  round + relief    : A + two-step round on the big masses + face relief, hands, folds, trim (0.25 units, flat colours)
  C  full sculpt       : B + fingers, hair strands, extra folds / wrinkles / stitches (still flat 0.25 colours, texture stays 32px)

The watcher keeps its "shadow that looks like you" quality: no nose / brow / fingers, only a smoother silhouette,
cloak folds, a frayed hem and drifting wisps.

python tools/rough/hp_npc.py [name ...]   -> docs/review/high_poly_roughs/<name>.png (+ assets/<name>_<A|B|C>/)
Bone names, pivots and rotations are never touched (animations keep working).
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import hp_view
import hpkit
import voxel
from PIL import Image

OUT = Path("docs/review/high_poly_roughs")
NAMES = ("storyteller", "whale_chaser", "watcher")


# ------------------------------------------------------------------ small helpers (kept here, hpkit.py is not touched)
def shade(rgb, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in rgb)


def mix(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


class Kit:
    """Wraps a hpkit.Model with the decoration helpers used by all three NPCs. `dropped` counts add_box calls that
    were skipped because they would overlap an existing box (printed so that nothing is lost silently)."""

    def __init__(self, m):
        self.m = m
        self.dropped = 0
        self.added = 0

    def box(self, bone, o, s, rgb, **kw):
        c = self.m.add_box(bone, o, s, rgb=rgb, safe=True, **kw)
        if c is None:
            self.dropped += 1
        else:
            self.added += 1
        return c

    def band(self, bone, o, s, y0, y1, t, rgb, rgb2=None):
        """a belt/cuff wrapped around the box (o, s) between y0 and y1, thickness t (4 boxes)."""
        x0, z0 = o[0], o[2]
        sx, sz = s[0], s[2]
        h = y1 - y0
        c2 = rgb2 or rgb
        self.box(bone, (x0 - t, y0, z0 + sz), (sx + 2 * t, h, t), rgb)
        self.box(bone, (x0 - t, y0, z0 - t), (sx + 2 * t, h, t), c2)
        self.box(bone, (x0 + sx, y0, z0), (t, h, sz), rgb)
        self.box(bone, (x0 - t, y0, z0), (t, h, sz), c2)

    def vstrips(self, bone, o, s, face, pitch, w, t, rgbs, y0=None, y1=None, m0=0.5, taper=None, ridge=None):
        """raised vertical strips (cloth folds) on a vertical face of the box (o, s).
        pitch: spacing along the face; w: strip width; t: thickness (0.25); rgbs: colours used in turn.
        m0: margin from the face edges; y0/y1: vertical range (default: the box height); taper(k)->(dy0, dy1) trims a strip."""
        y0 = o[1] + 0.5 if y0 is None else y0
        y1 = o[1] + s[1] - 0.5 if y1 is None else y1
        n = 0
        if face in ("south", "north"):
            a0, a1 = o[0] + m0, o[0] + s[0] - m0 - w
        else:
            a0, a1 = o[2] + m0, o[2] + s[2] - m0 - w
        k = 0
        a = a0
        while a <= a1 + 1e-9:
            d0, d1 = taper(k) if taper else (0.0, 0.0)
            yy0, yy1 = y0 + d0, y1 - d1
            if yy1 - yy0 >= 0.5:
                rgb = rgbs[k % len(rgbs)]
                if face == "south":
                    self.box(bone, (a, yy0, o[2] + s[2]), (w, yy1 - yy0, t), rgb)
                elif face == "north":
                    self.box(bone, (a, yy0, o[2] - t), (w, yy1 - yy0, t), rgb)
                elif face == "east":
                    self.box(bone, (o[0] + s[0], yy0, a), (t, yy1 - yy0, w), rgb)
                else:
                    self.box(bone, (o[0] - t, yy0, a), (t, yy1 - yy0, w), rgb)
                n += 1
                if ridge is not None and w >= 0.5 and yy1 - yy0 >= 1.0:
                    # a second, narrower step on top of the fold (round ridge): colour `ridge`, one more t out
                    ra = a + w / 4
                    rw = w / 2
                    ry0, ry1 = yy0 + 0.25, yy1 - 0.25
                    if face == "south":
                        self.box(bone, (ra, ry0, o[2] + s[2] + t), (rw, ry1 - ry0, t), ridge)
                    elif face == "north":
                        self.box(bone, (ra, ry0, o[2] - 2 * t), (rw, ry1 - ry0, t), ridge)
                    elif face == "east":
                        self.box(bone, (o[0] + s[0] + t, ry0, ra), (t, ry1 - ry0, rw), ridge)
                    else:
                        self.box(bone, (o[0] - 2 * t, ry0, ra), (t, ry1 - ry0, rw), ridge)
            a += pitch
            k += 1
        return n

    def hstrips(self, bone, o, s, face, pitch, h, t, rgbs, a0=None, a1=None, m0=0.5, y0=None, y1=None):
        """raised horizontal strips (stitch lines, ribs) on a vertical face."""
        y = (o[1] + m0) if y0 is None else y0
        ytop = (o[1] + s[1] - m0 - h) if y1 is None else y1 - h
        if face in ("south", "north"):
            a0 = o[0] + m0 if a0 is None else a0
            a1 = o[0] + s[0] - m0 if a1 is None else a1
        else:
            a0 = o[2] + m0 if a0 is None else a0
            a1 = o[2] + s[2] - m0 if a1 is None else a1
        k = 0
        n = 0
        while y <= ytop + 1e-9:
            rgb = rgbs[k % len(rgbs)]
            L = a1 - a0
            if face == "south":
                self.box(bone, (a0, y, o[2] + s[2]), (L, h, t), rgb)
            elif face == "north":
                self.box(bone, (a0, y, o[2] - t), (L, h, t), rgb)
            elif face == "east":
                self.box(bone, (o[0] + s[0], y, a0), (t, h, L), rgb)
            else:
                self.box(bone, (o[0] - t, y, a0), (t, h, L), rgb)
            n += 1
            y += pitch
            k += 1
        return n

    def round_one(self, bone, pred, step=0.25, R=1):
        """chamfer only the boxes of `bone` for which pred(cube) is true (small decorative boxes, 0.25 steps)."""
        b = self.m.bone(bone)
        new = []
        for c in b["cubes"]:
            if pred(c):
                new.extend(hpkit.round_cube(c, step=step, R=R, min_dim=None))
            else:
                new.append(c)
        b["cubes"] = new

    def fingers(self, bone, x0, n, w, z, ytop, lengths, depth, rgbs, dz=0.0):
        """n finger boxes side by side starting at x0 (width w each) in front of z, hanging down from ytop.
        lengths: finger length per finger."""
        for i in range(n):
            L = lengths[i % len(lengths)]
            self.box(bone, (x0 + i * w, ytop - L, z + dz), (w, L, depth), rgbs[i % len(rgbs)])


BASE = Path("tools/sources/hp_base/entity")   # untouched originals (the game assets are overwritten by the install)


def load(name):
    return hpkit.Model.load(name, geo_path=BASE / f"{name}.geo.json", tex_path=BASE / f"{name}.png",
                            glow_path=BASE / f"{name}_glowmask.png")


def sample(base, bone, idx, face="south"):
    c = base.bone(bone)["cubes"][idx]
    f = c["uv"][face]
    u, v = f["uv"]
    w, h = f["uv_size"]
    return base.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))


def finish(m, kit, name, variant):
    geo = hpkit.finish_geo(m)
    n, q, mn, bn = hpkit.stats(geo)
    ov = hpkit.overlaps(geo, detail=True)
    print(f"{name} {variant}: {n} boxes / {q} quads / min {mn} / bones {bn} / overlaps {len(ov)} / dropped {kit.dropped if kit else 0}")
    for o in ov[:8]:
        print("   ", o)
    return geo, (n, q, mn, ov)


# ================================================================== storyteller
def storyteller(variant):
    m = load("storyteller")
    base = m.copy()
    k = Kit(m)
    if variant == "now":
        return m, base, k
    if variant == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m, base, k
    C = variant == "C"

    skin = (154, 114, 87)
    skin_l = (179, 139, 109)
    skin_d = (126, 94, 73)
    skin_dd = (110, 78, 59)
    lip = (140, 92, 72)
    robe = (88, 82, 74)
    robe_d = (62, 58, 51)
    robe_l = (111, 104, 94)
    navy = (31, 43, 64)
    navy_m = (44, 59, 87)
    navy_l = (59, 77, 110)
    belt = (107, 74, 44)
    belt_l = (134, 96, 59)
    belt_d = (80, 54, 32)
    hair = (196, 193, 185)
    hair_d = (167, 164, 157)
    hair_l = (220, 217, 209)
    cord = (126, 100, 64)
    cord_l = (154, 125, 85)

    def rule(b, c):
        s = sorted(c["size"])
        if b["name"] in ("legL", "legR", "cord"):
            return None
        if C and s[1] >= 5 and s[0] >= 3.5:
            return dict(R=3 if s[0] >= 5 else 2)
        if s[1] >= 4 and s[0] >= 3:
            return dict(R=2)
        if s[1] >= 2 and s[0] >= 1:
            return dict(R=1)
        return None

    m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule)
    H = "head"

    # ---- face (front is +z, the face plane is z=5; the eyes / mouth are painted in the texture)
    for sx in (-1, 1):
        xl = 0.75 if sx > 0 else -2.0
        k.box(H, (xl, 21.0, 5.0), (1.25, 0.5, 0.25), hair_d)                                   # bushy grey brow
        k.box(H, (xl + (0.0 if sx > 0 else 0.25), 20.0, 5.0), (1.0, 0.5, 0.25), skin_d)         # eye bag
        xe = 3.0 if sx > 0 else -3.25
        k.box(H, (xe, 19.75, 1.5), (0.25, 1.25, 0.25), skin_d)                                  # ear rim
        k.box(H, (xe, 19.5, 1.75), (0.25, 0.5, 0.5), skin_l)                                    # ear lobe
        k.box(H, (xe, 20.25, 1.75), (0.25, 0.75, 0.5), skin_dd)                                 # ear hollow
        k.box(H, ((0.5 if sx > 0 else -0.75), 19.5, 5.0), (0.25, 0.5, 0.25), skin_l)            # nostril wing
        k.box(H, ((1.25 if sx > 0 else -1.75), 19.0, 5.0), (0.5, 0.5, 0.25), skin_d)            # mouth corner crease
    k.box(H, (-0.75, 19.0, 5.0), (1.5, 0.25, 0.25), lip)                                        # upper lip
    k.box(H, (-0.75, 18.25, 5.0), (1.5, 0.25, 0.25), lip)                                       # lower lip
    k.box(H, (-0.75, 18.0, 5.0), (1.5, 0.25, 0.5), skin_l)                                      # chin
    # fringe locks over the forehead
    for x0, y0 in ((-2.5, 22.0), (-1.5, 22.5), (-0.5, 22.25), (0.5, 22.5), (1.5, 22.0)):
        k.box(H, (x0, y0, 5.0), (1.0, 23.0 - y0, 0.25), hair if y0 < 22.4 else hair_d)
    # hair: lock behind each ear, back locks (S-curved), bun coils and the pin
    for x0 in (2.5, -3.0):
        k.box("hair", (x0, 19.5, 0.0), (0.5, 1.5, 1.5), hair_d)
    for x0 in (-2.5, -0.75, 1.0):
        k.box("hair", (x0, 22.0, -0.75), (0.75, 1.5, 0.25), hair)
        k.box("hair", (x0 + 0.25, 20.5, -0.75), (0.75, 1.5, 0.25), hair_d)
        k.box("hair", (x0, 19.0, -0.75), (0.75, 1.5, 0.25), hair)
    k.band("hair", (-1.5, 23.5, -0.5), (3.0, 1.5, 2.5), 24.0, 24.25, 0.25, hair_l)
    k.band("hair", (-1.5, 23.5, -0.5), (3.0, 1.5, 2.5), 24.75, 25.0, 0.25, hair_d) if False else None
    k.box("hair", (3.0, 24.0, 0.5), (0.25, 0.5, 0.5), (150, 104, 62))                           # pin cap
    k.box("hair", (-1.0, 25.5, 0.25), (2.0, 0.25, 0.5), hair_l)

    # ---- shawl: collar roll around the neck, pleats on the front trims, ribbing on the cape
    k.band("body", (-1.0, 17.5, 1.5), (2.0, 0.5, 2.0), 17.5, 18.0, 0.5, navy_l, navy_m)
    for sx in (-1, 1):
        xa = 1.0 if sx > 0 else -3.5
        k.vstrips("shawl", (xa, 14.0, 3.5), (2.5, 1.0, 0.5), "south", 0.5, 0.25, 0.25, [navy_l, navy_m], y0=14.0, y1=15.0, m0=0.25)
    k.vstrips("shawl", (-4.0, 16.0, -1.5), (8.0, 1.5, 5.5), "south", 0.5, 0.25, 0.25, [navy_l, navy_m], y0=16.25, y1=17.25, m0=0.5)
    k.vstrips("shawl", (-4.0, 16.0, -1.5), (8.0, 1.5, 5.5), "north", 0.5, 0.25, 0.25, [navy_l, navy_m], y0=16.25, y1=17.25, m0=0.5)
    k.vstrips("shawl", (-4.0, 16.0, -1.5), (8.0, 1.5, 5.5), "east", 0.5, 0.25, 0.25, [navy_l, navy_m], y0=16.25, y1=17.25, m0=0.5)
    k.vstrips("shawl", (-4.0, 16.0, -1.5), (8.0, 1.5, 5.5), "west", 0.5, 0.25, 0.25, [navy_l, navy_m], y0=16.25, y1=17.25, m0=0.5)
    k.vstrips("shawl", (-2.5, 13.5, -4.0), (5.0, 3.5, 1.0), "north", 0.5, 0.25, 0.25, [navy_m, navy_l], y0=14.0, y1=17.0, m0=0.5)
    # ---- belt: stitching, front sash knot with tails, buckle plate at the back, cord beads
    k.box("body", (-0.5, 10.75, 3.5), (1.0, 1.0, 0.5), belt_l)
    k.box("body", (-1.25, 11.0, 3.5), (0.75, 0.75, 0.25), belt)
    k.box("body", (0.5, 11.0, 3.5), (0.75, 0.75, 0.25), belt)
    k.box("body", (-0.75, 8.0, 3.0), (0.5, 2.75, 0.5), belt)
    k.box("body", (0.25, 8.5, 3.0), (0.5, 2.25, 0.5), belt_d)
    k.box("body", (-1.0, 10.5, -4.25), (2.0, 1.5, 0.25), belt_l)                                 # back buckle plate
    for face in ("south", "north"):
        k.hstrips("body", (-4.0, 10.5, -3.0), (8.0, 1.5, 6.5), face, 99, 0.25, 0.25, [belt_l], y0=11.25, y1=11.5, m0=0.75)
    for yy in (3.25, 5.25, 7.25, 9.25):
        k.box("cord", (1.75, yy, 4.5), (1.0, 0.25, 0.25), cord_l)
    # ---- skirt folds on all four sides of the two tiers (round ridges in C), hem border
    tiers = [((-4.0, 2.5, -3.0), (8.0, 3.5, 6.5)), ((-3.5, 6.0, -2.5), (7.0, 4.5, 5.5))]
    for (o, s) in tiers:
        for face in ("south", "north"):
            k.vstrips("body", o, s, face, 1.0, 0.5, 0.25, [robe_l, robe_d, robe], y0=o[1] + 0.5, y1=o[1] + s[1] - 0.5, m0=1.0,
                      ridge=robe_l if C else None)
        for face in ("east", "west"):
            k.vstrips("body", o, s, face, 1.0, 0.5, 0.25, [robe_d, robe_l], y0=o[1] + 0.5, y1=o[1] + s[1] - 0.5, m0=1.0,
                      ridge=robe_l if C else None)
    for face in ("south", "north", "east", "west"):
        k.hstrips("body", (-4.0, 2.5, -3.0), (8.0, 3.5, 6.5), face, 99, 0.25, 0.25, [robe_d], y0=2.5, y1=2.75, m0=0.0)
    # ---- sleeves: cuffs, elbow creases, seams; hands (clasped in front)
    for sx in (-1, 1):
        bone = "armL" if sx > 0 else "armR"
        ox = 4.0 if sx > 0 else -5.5
        face = "east" if sx > 0 else "west"
        k.band(bone, (ox, 11.0, 0.5), (1.5, 0.5, 2.5), 11.0, 11.5, 0.25, robe_l, robe_d)
        k.band(bone, (ox, 13.5, 0.5), (1.5, 0.5, 3.0), 13.5, 14.0, 0.25, robe_d, robe_l)
        k.hstrips(bone, (ox, 13.5, 0.5), (1.5, 3.0, 3.0), face, 0.5, 0.25, 0.25, [robe_d, robe_l], y0=14.5, y1=16.0, m0=0.5)
        k.vstrips(bone, (ox, 13.5, 0.5), (1.5, 3.0, 3.0), "south", 0.5, 0.25, 0.25, [robe_l, robe_d], y0=14.0, y1=16.0, m0=0.25)
        hx = 0.0 if sx > 0 else -2.5
        if not C:
            for i in range(5):
                k.box(bone, (hx + i * 0.5, 12.0, 5.0), (0.5, 1.0, 0.25), shade(skin_l, 1.0 if i % 2 else 0.9))
        else:
            lens = (1.5, 1.75, 1.75, 1.5, 1.0) if sx > 0 else (1.0, 1.5, 1.75, 1.75, 1.5)
            for i in range(5):
                L = lens[i]
                c = shade(skin_l, 1.0 if i % 2 else 0.9)
                k.box(bone, (hx + i * 0.5, 13.0 - L, 5.0), (0.5, L, 0.5), c)
                k.box(bone, (hx + i * 0.5, 13.0 - L - 0.25, 5.125), (0.5, 0.25, 0.25), shade(skin_d, 1.15))   # fingertip
            k.box(bone, (hx + (2.5 if sx > 0 else -0.25), 12.25, 4.0), (0.25, 0.75, 0.75), skin_l)           # thumb (outer side)
    # ---- feet
    for bone, x0 in (("legL", 0.5), ("legR", -2.5)):
        k.hstrips(bone, (x0, 0.0, -1.0), (2.0, 0.5, 3.5), "south", 99, 0.25, 0.25, [(110, 98, 52)], y0=0.0, y1=0.25, m0=0.0)

    if C:
        # wrinkles and age: forehead lines (under the fringe), crow's feet, nasolabial folds, jowls
        for sx in (-1, 1):
            k.box(H, ((2.0 if sx > 0 else -2.5), 20.5, 5.0), (0.5, 0.25, 0.25), skin_dd)
            k.box(H, ((2.0 if sx > 0 else -2.5), 21.0, 5.0), (0.5, 0.25, 0.25), skin_dd)
            k.box(H, ((0.75 if sx > 0 else -1.0), 19.5, 5.25), (0.25, 0.5, 0.25), skin_d) if False else None
            k.box(H, ((1.5 if sx > 0 else -2.0), 18.5, 5.0), (0.5, 0.25, 0.25), skin_d)             # jowl
        k.box(H, (-1.5, 21.75, 5.0), (3.0, 0.25, 0.25), skin_d)                                      # forehead line
        # shawl fringe along the cape front edge and the back panel
        for i in range(14):
            x = -3.5 + i * 0.5
            k.box("shawl", (x, 15.25 + (0.25 if i % 2 else 0.0), 4.0), (0.25, 0.75 - (0.25 if i % 2 else 0.0), 0.25), navy_l)
        for i in range(10):
            x = -2.75 + i * 0.5
            k.box("shawl", (x, 11.25 if i % 2 else 11.0, -3.25), (0.25, 0.75 if i % 2 else 1.0, 0.25), navy_m) if False else None
        # bodice: buttons in a row on the shawl front, thread ends
        for yy in (14.0, 15.0):
            k.box("shawl", (-0.25, yy, 4.0), (0.5, 0.5, 0.25), belt_l)
        # hair: finer hairline wisps at the temples
        for sx in (-1, 1):
            k.box(H, ((2.25 if sx > 0 else -2.5), 21.5, 5.0), (0.25, 1.0, 0.25), hair_d)
    return m, base, k


# ================================================================== whale_chaser
def whale_chaser(variant):
    m = load("whale_chaser")
    base = m.copy()
    k = Kit(m)
    if variant == "now":
        return m, base, k
    if variant == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m, base, k
    C = variant == "C"

    skin = (132, 89, 62)
    skin_l = (163, 113, 80)
    skin_d = (94, 63, 44)
    skin_dd = (74, 47, 37)
    coat = (79, 76, 60)
    coat_d = (53, 51, 40)
    coat_l = (107, 103, 81)
    glove = (56, 40, 26)
    glove_l = (76, 56, 35)
    scarf = (138, 74, 52)
    scarf_d = (112, 56, 40)
    scarf_l = (160, 92, 66)
    gold = (160, 132, 67)
    strap = (76, 56, 35)
    felt = (76, 56, 35)
    felt_d = (56, 40, 26)
    leather = (110, 100, 80)
    pack_l = (138, 127, 102)

    def rule(b, c):
        s = sorted(c["size"])
        n = b["name"]
        if n == "hat":
            return dict(R=2) if (s[1] >= 4 and s[0] >= 2) else None
        if n in ("legL", "legR"):
            return dict(R=1) if s[0] >= 1.5 else None
        if n == "body" and c["size"][1] >= 14 and s[0] < 1:
            return None            # thin coat panels stay flat (folds are added as relief)
        if C and s[1] >= 5 and s[0] >= 3:
            return dict(R=2)
        if s[1] >= 4 and s[0] >= 2:
            return dict(R=2) if s[0] >= 3 else dict(R=1)
        if s[1] >= 2 and s[0] >= 1:
            return dict(R=1)
        return None

    m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule)
    H = "head"
    # nose: chamfer it at 0.25 so it reads as a nose and not a block
    k.round_one(H, lambda c: abs(c["size"][0] - 1.0) < 1e-6 and abs(c["size"][2] - 1.0) < 1e-6 and c["origin"][1] == 23.0, 0.25, 1)

    # ---- face (front is +z, plane z=3). eyes y 24..24.5 (x +-1..2), brows painted y 25..25.5, mouth y 21.5..22
    for sx in (-1, 1):
        x0, x1 = (1.0, 2.5) if sx > 0 else (-2.5, -1.0)
        k.box(H, (x0, 25.0, 3.0), (x1 - x0, 0.5, 0.25), skin_d)                                  # heavy brow
        k.box(H, (x0 + 0.25, 23.5, 3.0), (1.0, 0.5, 0.25), skin_d)                               # eye bag
        k.box(H, (x0 + (0.25 if sx > 0 else -0.0), 22.5, 3.0), (1.25, 1.0, 0.25), skin_l)        # cheek mound
        xe = 3.0 if sx > 0 else -3.5
        k.box(H, (xe + (0.5 if sx > 0 else -0.25), 23.25, -0.25), (0.25, 1.0, 0.5), skin_d)      # ear rim
        k.box(H, (xe + (0.5 if sx > 0 else -0.25), 22.75, 0.0), (0.25, 0.5, 0.5), skin_l)        # ear lobe
        k.box(H, ((0.5 if sx > 0 else -0.75), 23.0, 3.0), (0.25, 0.5, 0.25), skin_l)             # nostril wing
        k.box(H, ((1.5 if sx > 0 else -2.0), 21.5, 3.0), (0.5, 0.5, 0.25), skin_d)               # mouth corner / laugh line
    k.box(H, (-0.25, 24.5, 3.0), (0.5, 0.5, 0.25), skin_l)                                       # nose bridge
    k.box(H, (-1.0, 21.5, 3.0), (2.0, 0.25, 0.25), shade(skin_dd, 1.3))                           # lower lip
    k.box(H, (-1.0, 22.0, 3.0), (2.0, 0.25, 0.25), shade(skin_dd, 1.6))                           # upper lip
    k.box(H, (-1.0, 21.0, 3.0), (2.0, 0.5, 0.5), skin_l)                                          # chin
    # ---- hat: brim edge lip, pinched crown, buckle band, feather
    bo, bs = (-5.5, 27.0, -5.5), (11.0, 0.5, 12.0)
    k.box("hat", (bo[0] + 0.5, 27.5, bo[2] + 0.25), (bs[0] - 1.0, 0.25, 0.25), felt_d)
    k.box("hat", (bo[0] + 0.5, 27.5, bo[2] + bs[2] - 0.5), (bs[0] - 1.0, 0.25, 0.25), felt_d)
    k.box("hat", (bo[0] + 0.25, 27.5, bo[2] + 0.5), (0.25, 0.25, bs[2] - 1.0), felt_d)
    k.box("hat", (bo[0] + bs[0] - 0.5, 27.5, bo[2] + 0.5), (0.25, 0.25, bs[2] - 1.0), felt_d)
    k.box("hat", (-1.0, 29.5, -0.5), (2.0, 0.25, 3.0), felt_d)                                    # pinched crown crease
    k.box("hat", (-0.5, 27.5, 3.0), (1.0, 0.5, 0.25), gold)                                        # buckle
    k.band("hat", (-2.5, 27.5, -2.5), (5.0, 2.0, 5.0), 27.5, 28.0, 0.25, shade(felt, 0.8))
    k.box("hat", (3.5, 28.0, -0.5), (0.5, 0.5, 1.0), (170, 160, 135))                             # feather quill
    k.box("hat", (4.0, 28.5, -1.0), (0.5, 0.5, 1.0), (170, 160, 135))
    k.box("hat", (4.5, 29.0, -1.5), (0.5, 0.5, 1.0), (150, 142, 120))
    # ---- scarf: wraps and tail folds
    k.hstrips("body", (-1.5, 17.0, 3.0), (1.0, 3.5, 0.5), "south", 0.75, 0.25, 0.25, [scarf_d, scarf_l], y0=17.25, y1=20.0, m0=0.0)
    k.box("body", (-1.5, 16.5, 3.0), (1.0, 0.5, 0.5), scarf_d)                                    # fringe ends
    k.box("body", (-1.25, 16.25, 3.0), (0.25, 0.25, 0.5), scarf)
    k.box("body", (-0.5, 16.25, 3.0), (0.25, 0.25, 0.5), scarf)
    k.band("body", (-2.0, 20.5, -2.0), (4.0, 0.5, 5.5), 20.5, 21.0, 0.25, scarf_d, scarf_l)
    k.box("body", (-0.5, 19.5, 3.5), (1.0, 1.0, 0.25), scarf_l)                                   # knot
    # ---- coat: edge piping, seams, buttons, lapels, belt
    for sx in (-1, 1):
        xa = 0.5 if sx > 0 else -3.5
        k.vstrips("body", (xa, 6.0, 2.0), (3.0, 14.0, 0.5), "south", 1.0, 0.25, 0.25, [coat_d, coat_l], y0=6.5, y1=19.0, m0=0.5)
    k.vstrips("body", (-3.5, 6.0, -2.5), (7.0, 14.0, 0.5), "north", 1.0, 0.5, 0.25, [coat_d, coat_l, coat], y0=6.5, y1=19.5, m0=0.5,
              ridge=coat_l if C else None)
    for yy in (7.0, 9.0, 11.0):
        k.box("body", (-0.25, yy, 2.5), (0.5, 0.5, 0.25), gold)
    for sx in (-1, 1):                                                                            # lapel points
        k.box("body", ((1.5 if sx > 0 else -2.0), 17.5, 2.5), (0.5, 1.5, 0.5), coat_l)
        k.box("body", ((2.0 if sx > 0 else -2.5), 18.5, 2.5), (0.5, 0.5, 0.5), coat_l)
    for face in ("south", "north"):
        k.hstrips("body", (-3.5, 6.0, -2.5), (7.0, 14.0, 5.0), face, 99, 0.25, 0.25, [coat_d], y0=6.0, y1=6.25, m0=0.0)
    k.band("body", (-3.5, 6.0, -2.5), (7.0, 14.0, 5.0), 11.0, 11.5, 0.25, strap, strap)            # belt
    k.box("body", (-0.5, 11.0, 2.75), (1.0, 0.5, 0.25), gold)                                      # buckle
    # shoulders: seams
    for sx in (-1, 1):
        o = (3.5, 18.5, -2.0) if sx > 0 else (-5.0, 18.5, -2.0)
        face = "east" if sx > 0 else "west"
        k.vstrips("body", o, (1.5, 1.5, 4.0), face, 0.5, 0.25, 0.25, [coat_l, coat_d], y0=18.75, y1=19.75, m0=0.5)
    # ---- sleeves and gloves
    for sx in (-1, 1):
        bone = "armL" if sx > 0 else "armR"
        ox = 3.5 if sx > 0 else -6.0
        face = "east" if sx > 0 else "west"
        k.vstrips(bone, (ox, 7.5, -1.5), (2.5, 11.0, 3.0), "south", 1.0, 0.25, 0.25, [coat_d], y0=8.0, y1=18.0, m0=1.0)
        k.vstrips(bone, (ox, 7.5, -1.5), (2.5, 11.0, 3.0), face, 1.0, 0.25, 0.25, [coat_l, coat_d], y0=8.0, y1=18.0, m0=0.5)
        k.hstrips(bone, (ox, 7.5, -1.5), (2.5, 11.0, 3.0), "south", 99, 0.25, 0.25, [coat_d], y0=13.0, y1=13.25, m0=0.25)
        k.band(bone, (ox, 5.5, -2.0), (3.0 if sx > 0 else 3.0, 2.0, 4.0), 7.0, 7.25, 0.25, coat_d, coat_d) if False else None
        gx = 3.5 if sx > 0 else -6.0
        if not C:
            for i in range(5):
                k.box(bone, (gx + i * 0.5, 4.0, 1.0), (0.5, 1.5, 0.25), shade(glove, 1.25 if i % 2 else 1.0))
            k.box(bone, (gx, 3.5, -1.0), (2.5, 0.5, 2.0), glove_l)
        else:
            lens = (1.5, 1.75, 2.0, 1.75, 1.0) if sx > 0 else (1.0, 1.75, 2.0, 1.75, 1.5)
            for i in range(5):
                L = lens[i]
                k.box(bone, (gx + i * 0.5, 5.5 - L, 1.0), (0.5, L, 0.5), shade(glove, 1.25 if i % 2 else 1.0))
                k.box(bone, (gx + i * 0.5, 5.5 - L - 0.25, 1.125), (0.5, 0.25, 0.25), shade(glove_l, 1.2))
            k.box(bone, (gx + (2.5 if sx > 0 else -0.5), 4.5, -0.5), (0.5, 1.0, 1.0), shade(glove, 1.15))   # thumb
            k.box(bone, (gx, 3.5, -1.0), (2.5, 0.5, 2.0), glove_l)
    # ---- legs / boots
    for bone, x0 in (("legL", 0.5), ("legR", -2.5)):
        k.hstrips(bone, (x0, 0.0, -1.5), (2.0, 1.5, 3.0), "south", 0.5, 0.25, 0.25, [leather, felt_d], y0=0.5, y1=1.5, m0=0.25)
        k.hstrips(bone, (x0, 2.0, -1.0), (2.0, 4.0, 2.0), "south", 1.0, 0.25, 0.25, [coat_d, coat_l], y0=2.5, y1=5.5, m0=0.0)
    # ---- backpack: straps, buckles, stitches
    po, ps = (-3.0, 6.0, -5.0), (6.0, 11.0, 1.5)
    k.vstrips("pack", po, ps, "north", 1.0, 0.5, 0.25, [pack_l, leather], y0=6.5, y1=16.5, m0=0.5, ridge=pack_l if C else None)
    k.vstrips("pack", po, ps, "east", 1.0, 0.25, 0.25, [pack_l], y0=6.5, y1=16.5, m0=0.25)
    k.vstrips("pack", po, ps, "west", 1.0, 0.25, 0.25, [pack_l], y0=6.5, y1=16.5, m0=0.25)
    k.hstrips("pack", po, ps, "north", 2.5, 0.5, 0.25, [strap], y0=9.0, y1=14.5, m0=0.0)
    k.box("pack", (-0.75, 9.0, -5.25), (1.5, 0.5, 0.25), gold)
    k.box("pack", (-0.75, 14.0, -5.25), (1.5, 0.5, 0.25), gold)
    if C:
        # face: lids, crow's feet, stubble dots, scar
        for sx in (-1, 1):
            k.box(H, ((1.0 if sx > 0 else -2.5), 24.5, 3.0), (1.5, 0.25, 0.25), skin_dd)           # upper lid
            k.box(H, ((2.5 if sx > 0 else -3.0), 24.0, 3.0), (0.5, 0.25, 0.25), skin_dd)           # crow's feet
        k.box(H, (1.0, 25.5, 3.0), (0.25, 1.0, 0.25), (176, 130, 104))                              # old scar through the brow
        for i, (x, y) in enumerate(((-2.0, 21.25), (-1.5, 21.5), (-1.0, 21.0), (1.0, 21.0), (1.5, 21.5), (2.0, 21.25), (-2.0, 22.5), (2.0, 22.5),
                                    (-1.5, 22.75), (1.5, 22.75), (-2.25, 23.0), (2.0, 23.25))):
            k.box(H, (x, y, 3.0), (0.25, 0.25, 0.25), skin_dd)                                      # stubble
        # sideburns under the hat
        for sx in (-1, 1):
            k.box(H, ((3.0 if sx > 0 else -3.25), 24.0, -0.5), (0.25, 3.0, 2.5), shade(felt_d, 1.2))
        # nape hair
        k.hstrips(H, (-3.0, 21.0, -3.0), (6.0, 6.0, 6.0), "north", 0.5, 0.25, 0.25, [skin_d, skin], y0=24.0, y1=27.0, m0=0.5)
        # coat: pocket flaps, stitched yoke, rope cord on the shoulder
        for sx in (-1, 1):
            k.box("body", ((1.5 if sx > 0 else -3.0), 14.5, 2.5), (1.5, 0.25, 0.25), coat_d)
        k.vstrips("body", (-4.0, 20.0, -2.5), (8.0, 0.5, 5.0), "south", 0.5, 0.25, 0.25, [coat_l, coat_d], y0=20.0, y1=20.5, m0=0.25)
        k.hstrips("body", (-4.5, 18.5, -3.5), (9.0, 1.5, 1.0), "north", 0.5, 0.25, 0.25, [coat_d, coat_l], y0=19.0, y1=20.0, m0=0.5)
    return m, base, k


# ================================================================== watcher
def watcher(variant):
    m = load("watcher")
    base = m.copy()
    k = Kit(m)
    if variant == "now":
        return m, base, k
    if variant == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m, base, k
    C = variant == "C"

    d0 = (37, 45, 56)
    d1 = (50, 60, 73)
    d2 = (65, 76, 90)
    d3 = (76, 88, 104)
    d4 = (86, 97, 110)

    def rule(b, c):
        s = sorted(c["size"])
        if b["name"] in ("hem", "mist", "eyes"):
            return None
        if b["name"] == "head":
            return dict(R=3)
        if b["name"] == "mantle" and s[0] < 1:
            return None
        if s[1] >= 4 and s[0] >= 1.5:
            return dict(R=2)
        if s[1] >= 2:
            return dict(R=1)
        return None

    m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule)

    # ---- cloak folds: long raised folds down the back panel, the body, the skirt and both sleeves
    k.vstrips("mantle", (-6.0, 9.0, -3.0), (12.0, 13.0, 0.5), "north", 1.0, 0.5, 0.25, [d3, d2, d3, d2], y0=11.0, y1=21.5, m0=0.5,
              taper=lambda i: (0.0, 0.5 * (i % 3)), ridge=d4 if C else None)
    k.vstrips("body", (-4.5, 16.0, -2.5), (9.0, 6.0, 5.0), "south", 1.0, 0.5, 0.25, [d3, d2], y0=16.5, y1=21.5, m0=0.5, ridge=d4 if C else None)
    k.vstrips("hem", (-5.0, 1.5, -3.0), (10.0, 4.5, 6.0), "south", 1.0, 0.5, 0.25, [d3, d2, d3, d2], y0=2.0, y1=5.5, m0=0.5, ridge=d4 if C else None)
    k.vstrips("hem", (-4.5, 6.0, -2.5), (9.0, 6.0, 5.0), "south", 1.0, 0.5, 0.25, [d2, d3], y0=6.5, y1=11.5, m0=0.5, ridge=d4 if C else None)
    for o, s, face in (((4.5, 15.0, -2.5), (2.0, 7.0, 5.0), "east"), ((-6.5, 11.0, -2.5), (2.0, 11.0, 5.0), "west")):
        k.vstrips("mantle", o, s, face, 1.0, 0.5, 0.25, [d3, d2], y0=o[1] + 0.5, y1=o[1] + s[1] - 0.5, m0=0.5, ridge=d4 if C else None)
    k.hstrips("body", (-6.5, 22.0, -2.5), (13.0, 2.0, 5.0), "south", 99, 0.25, 0.25, [d4], y0=23.5, y1=23.75, m0=0.5)
    k.hstrips("body", (-6.5, 22.0, -2.5), (13.0, 2.0, 5.0), "north", 99, 0.25, 0.25, [d4], y0=23.5, y1=23.75, m0=0.5)
    # ---- the face stays blank: a dark frame around the slit so it reads as cut into the head (eyes bone untouched)
    k.box("head", (2.25, 29.75, 3.5), (1.0, 0.25, 0.25), d0)
    k.box("head", (2.25, 31.5, 3.5), (1.0, 0.25, 0.25), d0)
    k.box("head", (2.25, 30.0, 3.5), (0.25, 1.5, 0.25), d0)
    k.box("head", (3.0, 30.0, 3.5), (0.25, 1.5, 0.25), d0)
    if C:
        # frayed hem: threads hang under the short hem strips down to the ground
        bottoms = [0.5, 0.0, 1.0, 0.0, 0.5, 1.0, 0.0, 0.5, 0.0, 1.0]
        for i, bt in enumerate(bottoms):
            if bt <= 0:
                continue
            x = -5.0 + i
            for j, zz in enumerate((-2.5, -1.0, 0.5, 2.0)):
                if (i + j) % 2:
                    continue
                k.box("hem", (x + 0.25 + 0.25 * (j % 2), 0.0, zz), (0.25, bt, 0.25), d2)
        # sleeve ends: ragged threads
        for o, s in (((4.5, 15.0, -2.5), (2.0, 7.0, 5.0)), ((-6.5, 11.0, -2.5), (2.0, 11.0, 5.0))):
            for j in range(4):
                z = o[2] + 0.5 + j * 1.25
                k.box("mantle", (o[0] + 0.5 + (j % 2) * 0.5, o[1] - 0.5 - (j % 3) * 0.25, z), (0.25, 0.5 + (j % 3) * 0.25, 0.25), d3)
        # drifting motes
        for (x, y, z) in ((5.5, 28.0, 1.0), (-5.0, 30.0, -1.5), (3.5, 33.0, 0.0), (-3.5, 36.5, 0.5), (6.5, 23.0, -1.0), (-7.5, 17.0, 0.0)):
            k.box("mist", (x, y, z), (0.25, 0.25, 0.25), d4)
        # crease lines across the chest and the back
        k.hstrips("body", (-4.5, 16.0, -2.5), (9.0, 6.0, 5.0), "south", 1.5, 0.25, 0.25, [d1, d3], y0=17.0, y1=21.0, m0=1.0)
        k.hstrips("mantle", (-6.0, 9.0, -3.0), (12.0, 13.0, 0.5), "north", 2.0, 0.25, 0.25, [d1], y0=12.0, y1=20.0, m0=1.0)
    return m, base, k


BUILD = {"storyteller": storyteller, "whale_chaser": whale_chaser, "watcher": watcher}


# ================================================================== sheet
# crops as fractions of a 1000x1000 render (x0, y0, x1, y1): the whole figure, the face, the hands / chest
CROP = {
    "storyteller": dict(body=(0.30, 0.07, 0.70, 0.93), face=(0.36, 0.11, 0.64, 0.33), mid=(0.30, 0.30, 0.70, 0.60)),
    "whale_chaser": dict(body=(0.30, 0.06, 0.70, 0.94), face=(0.34, 0.08, 0.66, 0.32), mid=(0.28, 0.30, 0.72, 0.60)),
    "watcher": dict(body=(0.27, 0.06, 0.73, 0.94), face=(0.34, 0.08, 0.66, 0.32), mid=(0.27, 0.28, 0.73, 0.58)),
}


def crop(im, box, size):
    w, h = im.size
    return im.crop((int(w * box[0]), int(h * box[1]), int(w * box[2]), int(h * box[3]))).resize(size, Image.LANCZOS)


def render_one(name, save_assets=True):
    cols = []
    notes = []
    cr = CROP[name]
    for v, label in (("now", "now"), ("A", "A chamfer"), ("B", "B round+relief"), ("C", "C full sculpt")):
        m, base, k = BUILD[name](v)
        faces = m.faces()
        geo, (n, q, mn, ov) = finish(m, k, name, v)
        notes.append(f"{v}: {n} boxes / {q} quads")
        big = {y: hp_view.shot(faces, y, p, (1000, 1000)) for y, p in ((180, 8), (215, 18), (35, 18), (200, 6))}
        row = [crop(big[180], cr["body"], (250, 500)), crop(big[215], cr["body"], (250, 500)), crop(big[35], cr["body"], (250, 500)),
               crop(big[200], cr["face"], (330, 250)), crop(big[215], cr["face"], (330, 250)), crop(big[180], cr["mid"], (330, 250))]
        cols.append((f"{label} ({n}/{q})", row))
        if save_assets and v != "now":
            m.save(OUT / "assets" / f"{name}_{v}")
    OUT.mkdir(parents=True, exist_ok=True)
    voxel.sheet(f"{name} high-poly options", cols, note="; ".join(notes)).save(OUT / f"{name}.png")




def install(name, variant):
    """write the chosen option into the game assets (geo + texture; the watcher also keeps its glowmask)."""
    import json
    m, _, k = BUILD[name](variant)
    geo, tex, glow = m.finalize()
    assert not hpkit.overlaps(geo), name
    a = hpkit.ASSETS
    (a / "geo/entity" / f"{name}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex).save(a / "textures/entity" / f"{name}.png")
    if name == "watcher":
        assert (glow[..., 3] > 0).any(), "glowmask has no lit pixel"
        Image.fromarray(glow).save(a / "textures/entity/watcher_glowmask.png")
    print("installed", name, variant, hpkit.stats(geo))


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--install":          # python tools/rough/hp_npc.py --install C [name ...]
        for n in (args[2:] or NAMES):
            install(n, args[1])
    else:
        for n in (args or NAMES):
            render_one(n)
