"""High-poly toolkit (docs/design/15_high_poly.md): round the boxes of an existing GeckoLib geo and add small trim.

Everything works on the *finished* geo + texture, so the bone tree, pivots and animations are untouched.

  import sys; sys.path.insert(0, "tools/rough"); import hpkit

  m = hpkit.Model.load("bone_sentinel")            # geo + texture + glowmask from the game assets
  m.round_all(step=0.5, R=1, min_dim=2)             # stepped chamfer on every box that is big enough
  m.add_box("head", (-1, 30, -5), (2, 1, 0.5), rgb=(200, 190, 160))   # a new small box (0.25 units are fine)
  m.save("out_dir")                                 # geo.json + tex.png + glow.png
  hpkit.stats(m.geo)                                # cubes / quads / smallest box

Key idea: a rounded box is the original box cut into disjoint sub-boxes.  Every sub-face samples the SAME direction
face of the original box at the same place, so the texture never changes (no new art needed).  Faces that end up
fully inside the solid are dropped.  Sub-boxes are disjoint, so tools/rough/voxel.check_overlaps stays clean.
"""
from __future__ import annotations

import copy
import itertools
import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
sys.path.insert(0, str(ROOT / "tools/rough"))
import voxel  # noqa: E402
from PIL import Image  # noqa: E402

DIRS = ("north", "south", "east", "west", "up", "down")
# (axis, sign) of the outward normal of each face
NORMAL = {"north": (2, -1), "south": (2, 1), "east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1)}


# ------------------------------------------------------------------ geometry of one face (same as voxel.load_geo)
def face_frame(o, s, d):
    """(origin corner, eu, ev) of face d of the box o/s; texture u runs along eu, 'up' of the image is ev."""
    x0, y0, z0 = o
    x1, y1, z1 = (o[i] + s[i] for i in range(3))
    sx, sy, sz = s
    return {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
    }[d]


def sub_uv(po, ps, d, puv, so, ss):
    """uv / uv_size of face d of sub-box (so, ss) taken from the parent's (po, ps) face uv (u0, v0, uw, vh)."""
    pco, peu, pev = (np.array(a, float) for a in face_frame(po, ps, d))
    sco, seu, sev = (np.array(a, float) for a in face_frame(so, ss, d))
    u0, v0, uw, vh = puv

    def frac(p, axis_vec):
        n = float(axis_vec @ axis_vec)
        return float((p - pco) @ axis_vec) / n if n > 0 else 0.0

    a0 = frac(sco, peu)
    a1 = frac(sco + seu, peu)
    b0 = frac(sco, pev)
    b1 = frac(sco + sev, pev)
    # side/up: image top = ev end, so v grows toward ev start. down: image top = ev start.
    if d == "down":
        vt, vb = b0, b1
    else:
        vt, vb = b1, b0
    # when the parent axis has zero length (degenerate), keep the parent's full range
    u_start = u0 + a0 * uw
    u_size = (a1 - a0) * uw
    if d == "down":
        v_start = v0 + vt * vh
        v_size = (vb - vt) * vh
    else:
        v_start = v0 + (1 - vt) * vh
        v_size = (vt - vb) * vh
    # a face smaller than one texel must sample the texel under its centre only (no bleeding of neighbours / glow)
    import math
    if abs(u_size) < 1:
        cu = u_start + u_size / 2
        sg = 1 if u_size >= 0 else -1
        u_start, u_size = math.floor(cu) + (0.25 if sg > 0 else 0.75), 0.5 * sg
    if abs(v_size) < 1:
        cv = v_start + v_size / 2
        sg = 1 if v_size >= 0 else -1
        v_start, v_size = math.floor(cv) + (0.25 if sg > 0 else 0.75), 0.5 * sg
    return [round(u_start, 4), round(v_start, 4)], [round(u_size, 4), round(v_size, 4)]


def box_face_uvs(c):
    """cube dict -> {dir: (u0, v0, uw, vh)} (handles box-uv lists)."""
    uv = c.get("uv", [0, 0])
    if isinstance(uv, list):
        w, h, dd = (int(round(v)) for v in c["size"])
        return {k: v for k, v in voxel._box_uv(uv[0], uv[1], w, h, dd).items()}
    return {k: (*f["uv"], *f["uv_size"]) for k, f in uv.items()}


# ------------------------------------------------------------------ rounding / carving of one box
def round_cube(c, step=0.5, R=1, axes="xyz", sides=None, min_center=0.5, min_dim=None, holes=None, _keep=False):
    """Cut cube dict c into disjoint sub-boxes with stepped rounded edges (and optional holes).

    step:  size of one step (0.5 = one texel at 32 px/block, 0.25 = half a texel, flat-coloured)
    R:     steps per edge. R=1 chamfer of `step`; R=2 two-step round (radius 2*step); R=3 three steps
    axes:  which axes' edges get rounded; an edge is rounded between two outer axes (both in `axes`).
           "xyz" = all 12 edges; "xz" = only the 4 vertical edges (a rounded column); "xy"/"yz" likewise
    sides: optional set of (axis, sign) allowed to be rounded, e.g. {(1, 1)} = only edges touching the top face
    holes: list of (lo_xyz, hi_xyz) absolute boxes carved out of the cube (eye sockets, mouth ...)
    Returns a list of cube dicts (with uv dicts). A box too small on an axis is not rounded along it.
    """
    o = np.array(c["origin"], float)
    s = np.array(c["size"], float)
    inf = c.get("inflate", 0) or 0
    o, s = o - inf, s + 2 * inf
    rect = box_face_uvs(c)
    fallback = next(iter(rect.values()), None)
    holes = [(np.array(h[0], float), np.array(h[1], float)) for h in (holes or [])]
    L = []
    for a in range(3):
        n = int(np.floor((s[a] - min_center) / (2 * step) + 1e-9))
        L.append(max(0, min(R, n)))
    asel = ["xyz".index(ch) for ch in axes]
    rounding = sum(1 for a in asel if L[a] > 0) >= 2 and R > 0 and not (min_dim is not None and sorted(s)[1] < min_dim)
    if not rounding and not holes:
        return [_plain(c, o, s, rect, fallback)]
    cuts = []
    for a in range(3):
        pts = {float(o[a]), float(o[a] + s[a])}
        if rounding:
            for i in range(1, L[a] + 1):
                pts.add(float(o[a] + step * i))
                pts.add(float(o[a] + s[a] - step * i))
        for h in holes:
            for v in (h[0][a], h[1][a]):
                if o[a] < v < o[a] + s[a]:
                    pts.add(float(v))
        cuts.append(sorted(round(p, 4) for p in pts))
    n = [len(cu) - 1 for cu in cuts]
    filled = np.ones(n, bool)
    mids = [[(cuts[a][i] + cuts[a][i + 1]) / 2 for i in range(n[a])] for a in range(3)]
    oo = [float(v) for v in o]
    ee = [float(o[a] + s[a]) for a in range(3)]
    hl = [([float(v) for v in h[0]], [float(v) for v in h[1]]) for h in holes]
    lev = []
    for a in asel:
        row = []
        for i in range(n[a]):
            m = mids[a][i]
            dl, dh = m - oo[a], ee[a] - m
            if dl < L[a] * step and (sides is None or (a, -1) in sides):
                row.append(int(dl // step))
            elif dh < L[a] * step and (sides is None or (a, 1) in sides):
                row.append(int(dh // step))
            else:
                row.append(None)
        lev.append(row)
    for idx in itertools.product(*[range(m) for m in n]):
        mx, my, mz = mids[0][idx[0]], mids[1][idx[1]], mids[2][idx[2]]
        if hl and any(lo[0] < mx < hi[0] and lo[1] < my < hi[1] and lo[2] < mz < hi[2] for lo, hi in hl):
            filled[idx] = False
            continue
        if not rounding or _keep:
            continue
        lv = [lev[j][idx[a]] for j, a in enumerate(asel) if lev[j][idx[a]] is not None]
        if any(lv[p] + lv[q] < R for p in range(len(lv)) for q in range(p + 1, len(lv))):
            filled[idx] = False
    return _assemble(c, o, s, rect, fallback, cuts, filled, merge=not _keep)


def carve_cube(c, holes, **kw):
    return round_cube(c, R=0, holes=holes, **kw)


def _assemble(c, o, s, rect, fallback, cuts, filled, merge=True):
    n = list(filled.shape)
    used = np.zeros(n, bool)
    boxes = []
    for idx in itertools.product(*[range(m) for m in n]):
        if not filled[idx] or used[idx]:
            continue
        e = list(idx)
        if not merge:
            used[idx] = True
            boxes.append((idx, tuple(e)))
            continue
        while e[0] + 1 < n[0] and filled[e[0] + 1, idx[1], idx[2]] and not used[e[0] + 1, idx[1], idx[2]]:
            e[0] += 1
        while e[1] + 1 < n[1] and filled[idx[0]:e[0] + 1, e[1] + 1, idx[2]].all() and not used[idx[0]:e[0] + 1, e[1] + 1, idx[2]].any():
            e[1] += 1
        while e[2] + 1 < n[2] and filled[idx[0]:e[0] + 1, idx[1]:e[1] + 1, e[2] + 1].all() and not used[idx[0]:e[0] + 1, idx[1]:e[1] + 1, e[2] + 1].any():
            e[2] += 1
        used[idx[0]:e[0] + 1, idx[1]:e[1] + 1, idx[2]:e[2] + 1] = True
        boxes.append((idx, tuple(e)))
    out = []
    for lo_i, hi_i in boxes:
        so = np.array([cuts[a][lo_i[a]] for a in range(3)])
        se = np.array([cuts[a][hi_i[a] + 1] for a in range(3)])
        ss = se - so
        cube = {k: copy.deepcopy(v) for k, v in c.items() if k not in ("origin", "size", "uv", "inflate")}
        cube["origin"] = [round(float(v), 4) for v in so]
        cube["size"] = [round(float(v), 4) for v in ss]
        uvd = {}
        for d in DIRS:
            ax, sg = NORMAL[d]
            nb = hi_i[ax] + 1 if sg > 0 else lo_i[ax] - 1
            if nb < 0 or nb >= n[ax]:
                exposed = True
            else:
                sl = [slice(lo_i[a], hi_i[a] + 1) for a in range(3)]
                sl[ax] = nb
                exposed = not filled[tuple(sl)].all()
            if not exposed:
                continue
            if d in rect:
                uv, uvs = sub_uv(o, s, d, rect[d], so, ss)
            elif fallback is not None:
                uv, uvs = _texel_of(fallback)
            else:
                continue
            uvd[d] = {"uv": uv, "uv_size": uvs}
        cube["uv"] = uvd
        out.append(cube)
    return out


def _texel_of(rect):
    u0, v0, uw, vh = rect
    return [round(u0 + uw / 2 - 0.25 * np.sign(uw or 1), 4), round(v0 + vh / 2 - 0.25 * np.sign(vh or 1), 4)], [0.5 * float(np.sign(uw or 1)), 0.5 * float(np.sign(vh or 1))]


def _plain(c, o, s, rect, fallback):
    cube = {k: copy.deepcopy(v) for k, v in c.items() if k not in ("origin", "size", "uv", "inflate")}
    cube["origin"] = [float(v) for v in o]
    cube["size"] = [float(v) for v in s]
    cube["uv"] = {d: {"uv": [r[0], r[1]], "uv_size": [r[2], r[3]]} for d, r in rect.items()}
    return cube


# ------------------------------------------------------------------ the model
class Model:
    def __init__(self, geo, tex, glow, name="model"):
        self.geo = geo
        self.tex = tex          # (H, W, 4) uint8
        self.glow = glow        # (H, W, 4) uint8 or None
        self.name = name
        self._palette = {}      # (rgb, glow) -> (u, v)
        self._extra = []        # list of (rgb, glow) in order

    # -- io
    @classmethod
    def load(cls, name, kind="entity", geo_path=None, tex_path=None, glow_path=None):
        geo_path = geo_path or ASSETS / "geo" / kind / f"{name}.geo.json"
        tex_dir = {"entity": "entity", "gear": "gear", "armor": "armor"}[kind]
        tex_path = tex_path or ASSETS / "textures" / tex_dir / f"{name}.png"
        glow_path = glow_path or ASSETS / "textures" / tex_dir / f"{name}_glowmask.png"
        geo = json.loads(Path(geo_path).read_text(encoding="utf-8"))
        tex = np.array(Image.open(tex_path).convert("RGBA"))
        glow = np.array(Image.open(glow_path).convert("RGBA")) if Path(glow_path).exists() else None
        m = cls(geo, tex, glow, name)
        m.normalize()
        return m

    def copy(self):
        m = Model(copy.deepcopy(self.geo), self.tex.copy(), None if self.glow is None else self.glow.copy(), self.name)
        m._palette = dict(self._palette)
        m._extra = list(self._extra)
        return m

    @property
    def g(self):
        return self.geo["minecraft:geometry"][0]

    def bone(self, name):
        for b in self.g["bones"]:
            if b["name"] == name:
                return b
        raise KeyError(name)

    def normalize(self):
        """per-face uv dicts everywhere, inflate baked into the box."""
        for b in self.g["bones"]:
            new = []
            for c in b.get("cubes", []):
                new.extend(round_cube(c, step=0.5, R=0))  # R=0 -> _plain (normalized, no cutting)
            if "cubes" in b:
                b["cubes"] = new

    # -- bulk rounding
    def round_all(self, step=0.5, R=1, min_dim=2.0, axes="xyz", bones=None, skip=None, sides=None, min_center=0.5, rule=None):
        """Round every box whose middle dimension is >= min_dim. bones: only those bones; skip(bone, cube)->bool.
        rule(bone, cube) -> dict of round_cube kwargs (overrides the defaults) or None to leave the box alone."""
        for b in self.g["bones"]:
            if bones is not None and b["name"] not in bones:
                continue
            new = []
            for c in b.get("cubes", []):
                if skip and skip(b, c):
                    new.append(c)
                    continue
                kw = dict(step=step, R=R, axes=axes, sides=sides, min_center=min_center, min_dim=min_dim)
                if rule:
                    r = rule(b, c)
                    if r is None:
                        new.append(c)
                        continue
                    kw.update(r)
                new.extend(round_cube(c, **kw))
            if "cubes" in b:
                b["cubes"] = new

    def color_at(self, uv_xy):
        """rgb of the (original) texture at pixel (x, y)."""
        x, y = int(uv_xy[0]), int(uv_xy[1])
        return tuple(int(v) for v in self.tex[y, x, :3])

    # -- new small boxes
    def texel(self, rgb, glow=False):
        key = (tuple(int(v) for v in rgb), bool(glow))
        if key not in self._palette:
            self._extra.append(key)
            self._palette[key] = len(self._extra) - 1
        i = self._palette[key]
        W = self.tex.shape[1]
        per_row = W // 2
        # patches are placed under the original texture; the final y offset is applied in finalize()
        return ("palette", i, per_row)

    def collides(self, origin, size, tol=1e-6):
        """True if the box overlaps any existing (unrotated) box. Bone rotations are ignored (checked by check_overlaps)."""
        o = np.array(origin, float)
        e = o + np.array(size, float)
        for b in self.g["bones"]:
            for c in b.get("cubes", []):
                if "rotation" in c:
                    continue
                co = np.array(c["origin"], float)
                ce = co + np.array(c["size"], float)
                if ((np.minimum(e, ce) - np.maximum(o, co)) > tol).all():
                    return True
        return False

    def add_box(self, bone, origin, size, rgb=None, glow=False, rgb_by_face=None, faces=DIRS, rot=None, pivot=None, safe=False):
        """Add a box (flat colour, 1 texel per face). rgb_by_face: {dir: rgb} to shade faces differently.
        safe=True: skip (return None) when it would overlap an existing box."""
        if safe and self.collides(origin, size):
            return None
        uv = {}
        for d in faces:
            col = (rgb_by_face or {}).get(d, rgb)
            uv[d] = {"uv": self.texel(col, glow), "uv_size": [1, 1]}
        c = {"origin": [float(v) for v in origin], "size": [float(v) for v in size], "uv": uv}
        if rot:
            c["rotation"] = list(rot)
            c["pivot"] = list(pivot if pivot is not None else [origin[i] + size[i] / 2 for i in range(3)])
        self.bone(bone).setdefault("cubes", []).append(c)
        return c

    def ridges(self, bones, face="up", min_len=6, spacing=2.0, width=0.5, height=0.25, k=1.25, min_w=2.0):
        """Raised thin strips across the `face` of every big box (shell ribs, armour plates, fur ridges). Skips collisions.
        Returns the number added. Colour = the face colour * k."""
        n = 0
        ax, sg = NORMAL[face]
        for b in list(self.g["bones"]):
            if bones is not None and b["name"] not in bones:
                continue
            for c in list(b.get("cubes", [])):
                if face not in c["uv"] or "rotation" in c:
                    continue
                o, sz = c["origin"], c["size"]
                other = [a for a in range(3) if a != ax]
                lens = [sz[a] for a in other]
                if min(lens) < min_w or max(lens) < min_len:
                    continue
                run = other[lens.index(max(lens))]   # strips run across the short axis, repeated along the long one
                short = other[1 - lens.index(max(lens))]
                u, v = c["uv"][face]["uv"], c["uv"][face]["uv_size"]
                rgb = self.color_at((u[0] + v[0] / 2 - 0.01, u[1] + v[1] / 2 - 0.01)) if not isinstance(u, tuple) else (128, 128, 128)
                col = tuple(min(255, int(x * k)) for x in rgb)
                t = 1.0
                while t + width < sz[run] - 0.5:
                    org = [0, 0, 0]
                    size = [0, 0, 0]
                    org[run], size[run] = o[run] + t, width
                    org[short], size[short] = o[short] + 0.5, sz[short] - 1.0
                    org[ax] = o[ax] + sz[ax] if sg > 0 else o[ax] - height
                    size[ax] = height
                    if self.add_box(b["name"], org, size, rgb=col, safe=True):
                        n += 1
                    t += spacing
        return n

    def finalize(self):
        """Resolve palette placeholders: append rows to the texture / glowmask, return (geo, tex, glow)."""
        geo = copy.deepcopy(self.geo)
        tex = self.tex
        glow = self.glow
        H0, W = tex.shape[:2]
        n = len(self._extra)
        rows = (n + W // 2 - 1) // (W // 2) if n else 0
        H1 = H0 + rows * 2
        if rows:
            t2 = np.zeros((H1, W, 4), np.uint8)
            t2[:H0] = tex
            g2 = np.zeros((H1, W, 4), np.uint8)
            if glow is not None:
                g2[:H0] = glow
            for i, (rgb, gl) in enumerate(self._extra):
                x = (i % (W // 2)) * 2
                y = H0 + (i // (W // 2)) * 2
                t2[y:y + 2, x:x + 2] = (*rgb, 255)
                if gl:
                    g2[y:y + 2, x:x + 2] = (*rgb, 255)
            tex, glow = t2, g2
        g = geo["minecraft:geometry"][0]
        g["description"]["texture_height"] = H1
        for b in g["bones"]:
            for c in b.get("cubes", []):
                for d, f in c["uv"].items():
                    if isinstance(f["uv"], tuple) and f["uv"][0] == "palette":
                        _, i, per_row = f["uv"]
                        x = (i % per_row) * 2
                        y = H0 + (i // per_row) * 2
                        f["uv"] = [x + 0.5, y + 0.5]
                        f["uv_size"] = [1, 1]
        return geo, tex, glow

    def faces(self, pose=None):
        geo, tex, glow = self.finalize()
        gm = glow
        if gm is not None and not (gm[..., 3] > 0).any():
            gm = None
        return voxel.load_geo(geo, tex, glowmask=gm, pose=pose)

    def save(self, out_dir):
        geo, tex, glow = self.finalize()
        out = Path(out_dir)
        out.mkdir(parents=True, exist_ok=True)
        (out / "model.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex).save(out / "tex.png")
        if glow is not None:
            Image.fromarray(glow).save(out / "glow.png")
        return geo


# ------------------------------------------------------------------ numbers
def stats(geo):
    """(cubes, quads, smallest box edge, bones)."""
    g = geo["minecraft:geometry"][0]
    n = q = 0
    mn = 1e9
    for b in g["bones"]:
        for c in b.get("cubes", []):
            n += 1
            uv = c.get("uv")
            q += len(uv) if isinstance(uv, dict) else 6
            nz = [v for v in c["size"] if v > 0]
            if nz:
                mn = min(mn, min(nz))
    return n, q, (mn if n else 0), len(g["bones"])


def overlaps(geo, detail=False):
    """tools/rough/voxel.check_overlaps; detail=True appends the origin/size of both boxes to each message."""
    res = voxel.check_overlaps(geo)
    if not detail:
        return res
    import re
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    out = []
    for o in res:
        mm = re.match(r"overlap (\w+)\[(\d+)\] x (\w+)\[(\d+)\]", o)
        a = bones[mm.group(1)]["cubes"][int(mm.group(2))]
        b = bones[mm.group(3)]["cubes"][int(mm.group(4))]
        out.append(f"{o}  {a['origin']}{a['size']} | {b['origin']}{b['size']}")
    return out


def finish_geo(m):
    geo, _, _ = m.finalize()
    return geo
