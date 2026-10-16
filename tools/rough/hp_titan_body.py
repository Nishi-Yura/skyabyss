"""High-poly roughs for the titan bodies, near LOD only (docs/design/15_high_poly.md section 8, assignment 6a).

  stone_whale  tide_shell        3 options each vs now
  A  face / mouth / head work only
  B  A + fins, fluke / flippers (the long edges)
  C  B + back ruins / tower rims and the rest

python tools/rough/hp_titan_body.py [name ...]        -> docs/review/high_poly_roughs/<name>_titan.png (+ assets/<name>_<A|B|C>/)
python tools/rough/hp_titan_body.py --install name V  -> writes the chosen near geo / texture / glowmask into src/main/resources/assets/skyabyss
python tools/rough/hp_titan_body.py --stats           -> quads / cubes of the installed near geo vs the limit

The outline of the body is NOT changed: the back-dimension body (gen_titan_back_whale.py / gen_tide_back_turtle.py) is built from the voxel
grid of install_stone_whale.build() / install_tide_shell.build_grid(), not from the geo, so nothing here touches that input.
Trim is laid on the real surface (hp_occ.Occ.surface), at most 0.75 units proud, flat colours (0.25 / 0.5 units), the texture only grows
by a few swatch rows at the bottom. Buried faces of the original boxes are dropped first to pay for the trim (limit 12,000 quads).
The original near geo / texture / glowmask are kept in tools/sources/hp_base/entity/ (the rough always starts from them).
"""
import sys

sys.path.insert(0, "tools/rough")
import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

import hp_occ
import hp_view
import hpkit
import voxel

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/high_poly_roughs"
BASE = ROOT / "tools/sources/hp_base/entity"
GAME = ROOT / "src/main/resources/assets/skyabyss"
QUAD_LIMIT = 12000

NAMES = ["stone_whale", "tide_shell"]
BOUNDS = {"stone_whale": ((-100, 0, -170), (100, 110, 170)), "tide_shell": ((-160, -5, -190), (160, 125, 140))}


def load(name):
    return hpkit.Model.load(name, geo_path=BASE / f"{name}.geo.json", tex_path=BASE / f"{name}.png",
                            glow_path=BASE / f"{name}_glowmask.png")


def shade(rgb, k):
    out = [max(0, min(255, v * k)) for v in rgb]
    if k < 1.0:
        out = [255.0 * (v / 255.0) ** 0.78 * 1.08 + 5 for v in out]
    return tuple(max(0, min(255, int(v))) for v in out)


SKIPPED = []
NEW = []        # cubes added by trim (their hidden faces are dropped by cull_new)


def put(m, bone, origin, size, **kw):
    r = m.add_box(bone, origin, size, **kw)
    if r is None:
        SKIPPED.append((bone, tuple(origin), tuple(size)))
    else:
        NEW.append(r)
    return r


def cull_new(m, step=0.25):
    """drop the uv of faces of the added boxes that are completely covered by other (unrotated) boxes; returns the number dropped"""
    allc = [c for b in m.g["bones"] for c in b.get("cubes", []) if "rotation" not in c]
    O = np.array([c["origin"] for c in allc], float)
    S = np.array([c["size"] for c in allc], float)
    E = O + S
    dropped = 0
    for c in NEW:
        if "rotation" in c:
            continue
        o, s = np.array(c["origin"], float), np.array(c["size"], float)
        near = ((E > o - 0.3).all(1)) & ((O < o + s + 0.3).all(1))
        no, ne = O[near], E[near]
        for d in list(c["uv"]):
            ax, sg = hpkit.NORMAL[d]
            oth = [a for a in range(3) if a != ax]
            pts = []
            ga = np.arange(o[oth[0]] + step / 2, o[oth[0]] + s[oth[0]], step) if s[oth[0]] > step / 2 else [o[oth[0]] + s[oth[0]] / 2]
            gb = np.arange(o[oth[1]] + step / 2, o[oth[1]] + s[oth[1]], step) if s[oth[1]] > step / 2 else [o[oth[1]] + s[oth[1]] / 2]
            plane = o[ax] + (s[ax] if sg > 0 else 0) + sg * 0.05
            allcov = True
            for a in ga:
                for b in gb:
                    p = np.zeros(3)
                    p[oth[0]], p[oth[1]], p[ax] = a, b, plane
                    # other boxes only: the box itself ends at the face plane, so the point (0.05 outside) is never inside it
                    if not ((no <= p).all(1) & (p < ne).all(1)).any():
                        allcov = False
                        break
                if not allcov:
                    break
            if allcov:
                del c["uv"][d]
                dropped += 1
    for b in m.g["bones"]:
        if "cubes" in b:
            b["cubes"] = [c for c in b["cubes"] if c["uv"]]
    return dropped


# ----------------------------------------------------------------------------------------------------------------------
class Skin:
    """Lays flat-colour relief on the real surface of the near model.

    cells(): the relief is described as a set of (a, b) cells of 0.5 x 0.5 on a plane (the other two axes of `ax`); every cell gets a box
    from the current surface to `thick` above it; neighbours in a row with the same surface height are merged into one box."""

    def __init__(self, m, name):
        self.m = m
        lo, hi = BOUNDS[name]
        self.occ = hp_occ.Occ(m, lo, hi, 0.5)
        self.col = hp_occ.Colors(m)
        self.lo, self.hi = np.array(lo, float), np.array(hi, float)

    def surf(self, ax, sg, a, b):
        p = [0.0, 0.0, 0.0]
        o = [i for i in range(3) if i != ax]
        p[o[0]], p[o[1]] = a, b
        return self.occ.surface(ax, sg, p)

    def color(self, ax, sg, a, b, k=1.0):
        h = self.surf(ax, sg, a, b)
        if h is None:
            return None
        o = [i for i in range(3) if i != ax]
        p = [0.0] * 3
        p[ax], p[o[0]], p[o[1]] = h, a + 0.25, b + 0.25
        d = {(0, 1): "east", (0, -1): "west", (1, 1): "up", (1, -1): "down", (2, 1): "south", (2, -1): "north"}[(ax, sg)]
        c = self.col.at(p, d)
        if c is None:
            return None
        return shade(c, k) if k != 1.0 else c

    def relief(self, bone, ax, sg, cells, thick=0.25, rgb=None, k=1.0, glow=False, lift=0.0, sink=0.0, merge=True):
        """cells: iterable of (a, b) lower corners (0.5 aligned) in the plane perpendicular to ax (axes in increasing order).
        rgb None -> colour of the surface under each cell * k. lift: start this far off the surface (a gap), sink: start inside it."""
        o = [i for i in range(3) if i != ax]
        byh = {}
        for a, b in cells:
            h = self.surf(ax, sg, a + 0.25, b + 0.25)
            if h is None:
                continue
            byh.setdefault(h, set()).add((round(a * 2) / 2, round(b * 2) / 2))
        n = 0
        for h, cs in sorted(byh.items()):
            # greedy rectangles: runs along a in each row b, then identical runs of adjacent rows merge along b
            rows = {}
            for a, b in sorted(cs, key=lambda t: (t[1], t[0])):
                r = rows.setdefault(b, [])
                if merge and r and abs(r[-1][1] - a) < 1e-6:
                    r[-1][1] = a + 0.5
                else:
                    r.append([a, a + 0.5])
            rects = []   # [a0, a1, b0, b1]
            open_ = {}
            for b in sorted(rows):
                cur = {}
                for a0, a1 in rows[b]:
                    key = (a0, a1)
                    if merge and key in open_ and abs(open_[key][3] - b) < 1e-6:
                        r_ = open_[key]
                        r_[3] = b + 0.5
                        cur[key] = r_
                    else:
                        r_ = [a0, a1, b, b + 0.5]
                        rects.append(r_)
                        cur[key] = r_
                open_ = cur
            for a0, a1, b0, b1 in rects:
                org, size = [0.0] * 3, [0.0] * 3
                org[o[0]], size[o[0]] = a0, a1 - a0
                org[o[1]], size[o[1]] = b0, b1 - b0
                if sg > 0:
                    org[ax], size[ax] = h + lift - sink, thick + sink
                else:
                    org[ax], size[ax] = h - lift - thick, thick + sink
                col = rgb
                if col is None:
                    col = self.color(ax, sg, a0, b0, k) or (90, 90, 100)
                inward = {(0, 1): "west", (0, -1): "east", (1, 1): "down", (1, -1): "up", (2, 1): "north", (2, -1): "south"}[(ax, sg)]
                faces = tuple(d for d in hpkit.DIRS if d != inward) if lift == 0 and sink == 0 else hpkit.DIRS
                if put(self.m, bone, org, size, rgb=col, glow=glow, faces=faces, safe=True):
                    n += 1
        return n


def cull_buried(m, q=0.5):
    """drop the uv of faces that are completely covered by other boxes (the originals are all on the 0.5 grid)"""
    lo, hi = BOUNDS[m.name]
    occ = hp_occ.Occ(m, lo, hi, q)
    dropped = 0
    for b in m.g["bones"]:
        for c in b.get("cubes", []):
            vals = list(c["origin"]) + list(c["size"])
            if any(abs(v / q - round(v / q)) > 1e-6 for v in vals) or "rotation" in c:
                continue
            for d in list(c["uv"]):
                ax, sg = hpkit.NORMAL[d]
                a = occ.idx(c["origin"])
                e = occ.idx(np.array(c["origin"]) + c["size"] - q / 2) + 1
                sl = [slice(a[i], e[i]) for i in range(3)]
                k = e[ax] if sg > 0 else a[ax] - 1
                if k < 0 or k >= occ.n[ax]:
                    continue
                sl[ax] = slice(k, k + 1)
                if occ.g[tuple(sl)].all():
                    del c["uv"][d]
                    dropped += 1
    m.g["bones"] = [dict(b, cubes=[c for c in b["cubes"] if c["uv"]]) if "cubes" in b else b for b in m.g["bones"]]
    return dropped


def rect(a0, a1, b0, b1, step=0.5):
    """cells of the rectangle [a0,a1) x [b0,b1)"""
    return [(a, b) for a in np.arange(a0, a1 - 1e-6, step) for b in np.arange(b0, b1 - 1e-6, step)]


def ring(a0, a1, b0, b1, t=0.5, cut=1):
    """cells of a rectangular ring (thickness t) with `cut` corner cells removed"""
    out = []
    for a, b in rect(a0 - t, a1 + t, b0 - t, b1 + t):
        inner = a0 <= a < a1 and b0 <= b < b1
        if inner:
            continue
        ca = (a < a0 and a >= a0 - t) or (a >= a1)
        cb = (b < b0 and b >= b0 - t) or (b >= b1)
        if cut and ca and cb:
            continue
        out.append((a, b))
    return out


# ======================================================================================================== stone whale
XC = -2.0
STONE = (84, 88, 100)
STONE_HI = (96, 100, 110)
DARK = (40, 42, 52)
SAND = (190, 182, 164)
TOOTH = (226, 220, 196)
MOSS = (74, 104, 52)
MOSS_D = (52, 78, 40)


def sand_top(sk, ax, sg, t):
    """height of the highest sand-coloured cell on the side (ax=0, t = z) or the front (ax=2, t = x); sand below, slate above"""
    y = 46.0
    while y > 16.0:
        c = sk.color(0, sg, y, t) if ax == 0 else sk.color(2, sg, t, y)
        if c is not None and c[0] > 105 and 10 < c[0] - c[2] < 60:
            return y + 0.5
        y -= 0.5
    return None


def whale_face(sk, v):
    m = sk.m
    # ---- eyes: stepped lids, brow, a dark slit pupil on the glowing eye (both sides). eye: y 38..40, z -113..-110
    for sg in (1, -1):
        ex = sk.surf(0, sg, 39.0, -111.5)
        sk.relief("head", 0, sg, ring(38.0, 40.0, -113.0, -110.0, t=0.5), thick=1.0, k=1.25)
        sk.relief("head", 0, sg, ring(37.5, 40.5, -113.5, -109.5, t=0.5), thick=0.5, k=0.85)
        brow = rect(41.0, 41.5, -118.0, -106.0) + rect(41.5, 42.0, -117.0, -107.0) + rect(42.0, 42.5, -115.0, -109.0)
        sk.relief("head", 0, sg, brow, thick=1.0, k=1.3)
        sk.relief("head", 0, sg, rect(35.5, 36.0, -117.0, -107.0), thick=0.5, k=1.2)
        put(m, "head", (ex if sg > 0 else ex - 0.25, 38.25, -112.0), (0.25, 1.5, 0.5), rgb=(30, 20, 12))
    # ---- lip line: a ridge along the top of the sand belly, round the front and along both sides
    front = [(x, sand_top(sk, 2, -1, x)) for x in np.arange(-24.0, 20.0, 0.5)]
    sk.relief("head", 2, -1, [(x, t) for x, t in front if t is not None], thick=0.75, k=1.35)
    for sg in (1, -1):
        cells = [(sand_top(sk, 0, sg, z), z) for z in np.arange(-126.0, -90.0, 0.5)]
        sk.relief("head", 0, sg, [(t, z) for t, z in cells if t is not None], thick=0.75, k=1.35)
    # ---- teeth: cream pegs hanging from the lip over the sand, round the front and the first part of both sides
    cells = []
    for x in np.arange(-22.0, 18.0, 2.0):
        t = sand_top(sk, 2, -1, x)
        if t is not None:
            cells += rect(x, x + 1.0, t - 2.0, t - 0.5)
    sk.relief("head", 2, -1, cells, thick=0.75, rgb=TOOTH)
    for sg in (1, -1):
        cells = []
        for z in np.arange(-124.0, -96.0, 2.5):
            t = sand_top(sk, 0, sg, z)
            if t is not None:
                cells += rect(t - 2.0, t - 0.5, z, z + 1.0)
        sk.relief("head", 0, sg, cells, thick=0.75, rgb=TOOTH)


def edge_coord(sk, axis, x_axis, x, y_rng, z_rng, which):
    """min / max coordinate along `axis` of solid cells in the slab at x (axis x_axis), searched in the given ranges"""
    occ = sk.occ
    i = occ.idx([x if x_axis == 0 else 0, 0, x if x_axis == 2 else 0])
    lo_i = occ.idx([z_rng[0] if x_axis == 0 else x, y_rng[0], z_rng[0] if x_axis == 0 else z_rng[0]])
    return None


def row_extent(sk, x, z_lo, z_hi, y_lo, y_hi):
    """(zmin, zmax) of solid cells in the column at x (any y in range); None if empty"""
    occ = sk.occ
    xi = int(np.floor((x - occ.lo[0]) / occ.q))
    y0, y1 = int((y_lo - occ.lo[1]) / occ.q), int((y_hi - occ.lo[1]) / occ.q)
    z0, z1 = int((z_lo - occ.lo[2]) / occ.q), int((z_hi - occ.lo[2]) / occ.q)
    sl = occ.g[xi, y0:y1, z0:z1].any(0)
    nz = np.flatnonzero(sl)
    if len(nz) == 0:
        return None
    return occ.lo[2] + (z0 + nz[0]) * occ.q, occ.lo[2] + (z0 + nz[-1] + 1) * occ.q


def whale_fins(sk, v):
    for sg, (xa, xb) in ((1, (30.0, 65.5)), (-1, (-71.0, -31.5))):
        xs = np.arange(xa, xb, 0.5)
        lead, rib1, rib2, trail = [], [], [], []
        for x in xs:
            e = row_extent(sk, x, -62.0, -28.0, 20.0, 40.0)
            if e is None:
                continue
            zmin, zmax = e
            lead.append((x, zmin))
            if zmax - zmin > 5:
                rib1.append((x, zmin + 1.5))
            if zmax - zmin > 9:
                rib2.append((x, zmin + 4.5))
        bone = "fin_left" if sg > 0 else "fin_right"
        sk.relief(bone, 1, 1, lead, thick=0.5, k=1.3)
        sk.relief(bone, 1, 1, rib1, thick=0.5, k=1.2)
        sk.relief(bone, 1, 1, rib2, thick=0.5, k=1.2)


def whale_fluke(sk, v):
    for xc in np.arange(-56.0, 58.0, 4.0):
        if abs(xc) < 7:
            continue
        cells = []
        for z in np.arange(139.0, 162.0, 0.5):
            cells.append((xc, z))
        sk.relief("fluke", 1, 1, cells, thick=0.5, k=1.2)
    # leading edge ridge
    lead = []
    for x in np.arange(-58.0, 58.0, 0.5):
        e = row_extent(sk, x, 136.0, 165.0, 30.0, 45.0)
        if e is not None:
            lead.append((x, e[0]))
    sk.relief("fluke", 1, 1, lead, thick=0.5, k=1.3)


def whale_rest(sk, v):
    m = sk.m
    # back ruins: thin ribs over the tops of the walls and steps
    m.ridges(["ruins"], face="up", min_len=4, spacing=2.0, width=0.5, height=0.25, k=1.3, min_w=2.0)


def build_whale(v):
    m = load("stone_whale")
    m.name = "stone_whale"
    if v == "now":
        return m
    NEW.clear()
    SKIPPED.clear()
    cull_buried(m)
    sk = Skin(m, "stone_whale")
    whale_face(sk, v)
    if v in ("B", "C"):
        whale_fins(sk, v)
        whale_fluke(sk, v)
    if v == "C":
        whale_rest(sk, v)
    cull_new(m)
    return m


# ======================================================================================================== tide shell
CREAM = (210, 202, 170)
SHELL_HI = (92, 136, 128)


def fin_ribs(sk, bone, xs, zr, yr, ribs=(1.5, 4.5), lead=True, thick=0.5, k=1.25):
    """a leading-edge ridge and rib lines (parallel to the leading edge) over the top of a fin that runs along x"""
    zl, zh = zr
    yl, yh = yr
    lines = {r: [] for r in ribs}
    leadc = []
    for x in xs:
        e = row_extent(sk, x, zl, zh, yl, yh)
        if e is None:
            continue
        zmin, zmax = e
        leadc.append((x, zmin))
        for r in ribs:
            if zmax - zmin > r + 3:
                lines[r].append((x, zmin + r))
    if lead:
        sk.relief(bone, 1, 1, leadc, thick=thick, k=1.3)
    for r in ribs:
        sk.relief(bone, 1, 1, lines[r], thick=thick, k=k)


def tide_head(sk, v):
    m = sk.m
    # eyes: glow slit y 22..24, z -132..-124 on both sides of the head
    for sg in (1, -1):
        ex = sk.surf(0, sg, 23.0, -128.0)
        sk.relief("head", 0, sg, ring(22.0, 24.0, -132.0, -124.0, t=0.5), thick=1.0, k=1.25)
        sk.relief("head", 0, sg, ring(21.5, 24.5, -132.5, -123.5, t=0.5), thick=0.5, k=0.85)
        brow = rect(25.0, 25.5, -134.0, -122.0) + rect(25.5, 26.0, -133.0, -123.0) + rect(26.0, 26.5, -131.0, -125.0)
        sk.relief("head", 0, sg, brow, thick=1.0, k=1.3)
        sk.relief("head", 0, sg, rect(19.0, 19.5, -133.0, -123.0), thick=0.5, k=1.15)
        put(m, "head", (ex if sg > 0 else ex - 0.25, 22.25, -129.0), (0.25, 1.5, 0.5), rgb=(24, 30, 28))
    # beak: keel along the top, nostrils, lip ridge and plate seams on the cream front
    keel = [(x, z) for x in (-0.5,) for z in np.arange(-170.0, -136.0, 0.5)]
    sk.relief("head", 1, 1, keel, thick=0.5, rgb=shade(CREAM, 1.08))
    for xn in (-4.0, 3.0):
        sk.relief("head", 2, -1, rect(xn, xn + 1.0, 33.0, 34.5), thick=0.25, rgb=(34, 42, 40))
    sk.relief("head", 2, -1, rect(-14.0, 14.0, 18.0, 18.5), thick=0.5, rgb=shade(CREAM, 1.1))
    for y in (24.0, 28.0, 32.0):
        sk.relief("head", 2, -1, rect(-11.0, 11.0, y, y + 0.5), thick=0.25, rgb=shade(CREAM, 0.84))
    # chin: a short row of pegs along the lip
    sk.relief("head", 2, -1, [c for x in np.arange(-10.0, 11.0, 2.0) for c in rect(x, x + 1.0, 16.0, 17.5)], thick=0.5, rgb=shade(CREAM, 0.95))


def apron_edge(sk, spacing=5.0):
    """cells on the outer rim of the shell apron (low flat lip around the dome), thinned to roughly `spacing` units apart"""
    occ = sk.occ
    nx, ny, nz = occ.g.shape
    top = np.where(occ.g.any(1), ny - 1 - np.argmax(occ.g[:, ::-1, :], axis=1), -1)
    ymax = occ.lo[1] + (top + 1) * occ.q
    solid = top >= 0
    low = solid & (ymax <= 14.0)
    pad = np.pad(~solid, 1, constant_values=True)
    nb = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    edge = low & nb
    # keep only the shell body area (head / flippers / tail are separate bones at other places): within the oval of the shell
    pts = np.argwhere(edge)
    out = []
    taken = []
    cx, cz = 0.0, -3.0
    for i, k in pts[np.argsort([np.arctan2((j * occ.q + occ.lo[2]) - cz, (i * occ.q + occ.lo[0]) - cx) for i, j in pts])]:
        x, z = occ.lo[0] + i * occ.q, occ.lo[2] + k * occ.q
        if (x / 94.0) ** 2 + ((z - cz) / 104.0) ** 2 < 0.80 or (x / 94.0) ** 2 + ((z - cz) / 104.0) ** 2 > 1.15:
            continue
        if any((x - tx) ** 2 + (z - tz) ** 2 < spacing ** 2 for tx, tz in taken):
            continue
        taken.append((x, z))
        out.append((x, z))
    return out


def tide_rim(sk, v):
    cells = []
    for x, z in apron_edge(sk, 5.0):
        cells += rect(x - 0.5, x + 0.5, z - 0.5, z + 0.5)
    n = sk.relief("body", 1, 1, cells, thick=2.0, rgb=(196, 214, 200))
    return n


def tide_ribs(sk, v):
    """scute seams: radial ribs over the dome"""
    cx, cz = 0.0, -3.0
    for deg in range(0, 360, 90):
        th = np.radians(deg + 22.5)
        cells = []
        r = 28.0
        while r < 84.0:
            cells.append((round((cx + r * np.cos(th)) * 2) / 2, round((cz + r * np.sin(th)) * 2) / 2))
            r += 0.5
        cells = sorted(set(cells))
        sk.relief("body", 1, 1, cells, thick=0.5, k=1.22)


def build_tide(v):
    m = load("tide_shell")
    m.name = "tide_shell"
    if v == "now":
        return m
    NEW.clear()
    SKIPPED.clear()
    cull_buried(m)
    sk = Skin(m, "tide_shell")
    tide_head(sk, v)
    if v in ("B", "C"):
        for bone, xs in (("fin_front_left", np.arange(76.0, 150.0, 0.5)), ("fin_front_right", np.arange(-150.0, -74.0, 0.5))):
            fin_ribs(sk, bone, xs, (-50.0, 10.0), (0.0, 20.0), ribs=(2.0, 6.0))
        for bone, xs in (("fin_rear_left", np.arange(64.0, 110.0, 0.5)), ("fin_rear_right", np.arange(-110.0, -72.0, 0.5))):
            fin_ribs(sk, bone, xs, (52.0, 106.0), (0.0, 20.0), ribs=(3.0,))
        tide_rim(sk, v)
    if v == "C":
        tide_ribs(sk, v)
        m.ridges(["ruins"], face="up", min_len=10, spacing=3.0, width=0.5, height=0.25, k=1.3, min_w=2.0)
    cull_new(m)
    return m


BUILD = {"stone_whale": build_whale, "tide_shell": build_tide}


def quads(m):
    return hpkit.stats(hpkit.finish_geo(m))[1]


def install(name, v, rebase=False):
    """write option v of the near LOD into the game assets. rebase=True: first keep the freshly generated near geo / texture /
    glowmask (the install_*.py output) as the new originals in tools/sources/hp_base/entity/ (they are all the 'now' of this rough)."""
    gd, td = GAME / "geo/entity", GAME / "textures/entity"
    if rebase:
        shutil.copy(gd / f"{name}.geo.json", BASE / f"{name}.geo.json")
        shutil.copy(td / f"{name}.png", BASE / f"{name}.png")
        shutil.copy(td / f"{name}_glowmask.png", BASE / f"{name}_glowmask.png")
    m = BUILD[name](v)
    geo = hpkit.finish_geo(m)
    assert not hpkit.overlaps(geo), "overlaps"
    n, q, mn, bn = hpkit.stats(geo)
    assert q <= QUAD_LIMIT, f"{q} quads over the limit {QUAD_LIMIT}"
    _, tex, glow = m.finalize()
    assert (glow[..., 3] > 0).any(), "no glow pixels"
    d = OUT / "assets" / f"{name}_{v}"
    m.save(d)
    shutil.copy(d / "model.geo.json", gd / f"{name}.geo.json")
    shutil.copy(d / "tex.png", td / f"{name}.png")
    shutil.copy(d / "glow.png", td / f"{name}_glowmask.png")
    H = geo["minecraft:geometry"][0]["description"]["texture_height"]
    for lod in ("mid", "far"):      # the three LODs share one texture: only the declared height follows (the uv are in pixels)
        p = gd / f"{name}_{lod}.geo.json"
        g = json.loads(p.read_text(encoding="utf-8"))
        g["minecraft:geometry"][0]["description"]["texture_height"] = H
        p.write_text(json.dumps(g, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("installed", name, v, "boxes", n, "quads", q, "texture", tex.shape[1], "x", tex.shape[0])


def clip_faces(faces, lo, hi):
    """faces clipped to the box lo..hi (the faces are axis-aligned rectangles; the texture is cropped to match)"""
    lo, hi = np.array(lo, float), np.array(hi, float)
    out = []
    for f in faces:
        ia = int(np.argmax(np.abs(f.eu)))
        ib = int(np.argmax(np.abs(f.ev)))
        i3 = 3 - ia - ib
        if not (lo[i3] - 1e-6 <= f.origin[i3] <= hi[i3] + 1e-6):
            continue
        rng = []
        for i, vec in ((ia, f.eu), (ib, f.ev)):
            c0, c1 = f.origin[i], f.origin[i] + vec[i]
            s0, s1 = (lo[i] - c0) / vec[i], (hi[i] - c0) / vec[i]
            if s0 > s1:
                s0, s1 = s1, s0
            s0, s1 = max(0.0, s0), min(1.0, s1)
            rng.append((s0, s1))
        (s0, s1), (t0, t1) = rng
        if s1 - s0 < 1e-6 or t1 - t0 < 1e-6:
            continue
        th, tw = f.tex.shape[:2]
        c0, c1 = int(np.floor(s0 * tw + 1e-6)), max(int(np.ceil(s1 * tw - 1e-6)), int(np.floor(s0 * tw + 1e-6)) + 1)
        r0, r1 = int(np.floor((1 - t1) * th + 1e-6)), max(int(np.ceil((1 - t0) * th - 1e-6)), int(np.floor((1 - t1) * th + 1e-6)) + 1)
        tex = f.tex[r0:r1, c0:c1]
        glow = None if f.glow is None else f.glow[r0:r1, c0:c1]
        out.append(voxel.Face(origin=f.origin + s0 * f.eu + t0 * f.ev, eu=(s1 - s0) * f.eu, ev=(t1 - t0) * f.ev, tex=tex, glow=glow, shade=f.shade))
    return out


def region_shot(faces, lo, hi, yaw, pitch, size=(560, 560)):
    return hp_view.shot(clip_faces(faces, lo, hi), yaw, pitch, size)


def view_sets(name):
    """(label, region lo, hi, yaw, pitch)"""
    if name == "stone_whale":
        return [("eye + lip 3/4", (8, 22, -140), (40, 56, -92), 120, 10),
                ("face front", (-40, 10, -140), (40, 70, -118), 180, 5),
                ("fin", (24, 14, -62), (72, 40, -28), 130, 40),
                ("fluke", (-62, 30, 134), (62, 46, 165), 160, 35),
                ("ruins", (-18, 55, -50), (18, 95, 42), 215, 25),
                ("whole", (-100, 0, -170), (100, 110, 170), 215, 15)]
    return [("head 3/4", (-30, 0, -180), (30, 50, -95), 200, 18),
            ("head side", (-30, 0, -180), (30, 50, -95), 110, 8),
            ("flipper", (60, 0, -50), (155, 20, 10), 140, 35),
            ("shell + rim", (-100, 0, -120), (10, 70, 20), 225, 30),
            ("tower", (-30, 60, -45), (62, 110, 30), 210, 25),
            ("whole", (-160, 0, -180), (160, 125, 130), 215, 28)]


def sheet_for(name, variants, views, size=(560, 560)):
    cols = []
    for v in variants:
        m = BUILD[name](v)
        fs = m.faces()
        n, q, mn, bn = hpkit.stats(hpkit.finish_geo(m))
        cols.append((f"{v}  {n} boxes / {q} quads", [region_shot(fs, lo, hi, yaw, pit, size) for _, lo, hi, yaw, pit in views]))
    return cols


def compose(name, variants, out, views=None, size=(560, 560)):
    views = views or view_sets(name)
    cols = sheet_for(name, variants, views, size)
    from PIL import ImageDraw
    W = size[0] * len(cols)
    H = size[1] * len(views) + 24
    im = Image.new("RGB", (W, H), (16, 16, 24))
    d = ImageDraw.Draw(im)
    for ci, (label, shots) in enumerate(cols):
        d.text((ci * size[0] + 6, 6), label, fill=(230, 230, 230))
        for ri, sh in enumerate(shots):
            im.paste(sh.convert("RGB"), (ci * size[0], 24 + ri * size[1]))
    im.save(out)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--install":      # --install name V [--rebase]
        install(args[1], args[2], rebase="--rebase" in args)
    elif args and args[0] == "--stats":
        for nme in NAMES:
            g = json.loads((GAME / "geo/entity" / f"{nme}.geo.json").read_text(encoding="utf-8"))
            print(nme, hpkit.stats(g), "limit", QUAD_LIMIT)
    elif args and args[0] == "--sheet":   # --sheet name out variant,variant view,view  (view indices into view_sets)
        name, out = args[1], args[2]
        variants = args[3].split(",")
        vs = view_sets(name)
        views = [vs[int(i)] for i in args[4].split(",")] if len(args) > 4 else vs
        compose(name, variants, out, views)
    else:
        for v in ("now", "A", "B", "C"):
            m = BUILD[sys.argv[1] if len(sys.argv) > 1 else "stone_whale"](v)
            geo = hpkit.finish_geo(m)
            print(v, hpkit.stats(geo), "overlaps", len(hpkit.overlaps(geo)), "skipped", len(SKIPPED))
