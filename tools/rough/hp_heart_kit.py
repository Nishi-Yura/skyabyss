"""High-poly heart kit (docs/design/15_high_poly.md, assignment 3): re-sculpt a baked heart at 0.25 units.

The hearts were built on a 0.5-unit voxel grid (1 cell = 1 texel).  Chamfering their boxes changes nothing (15.7), so this kit
reads the *finished* source (geo + texture + glow) of a phase, turns it back into cells, upsamples to 0.25-unit cells and then:

  smooth()   rounds the stair steps of the bulky parts with a blurred-occupancy threshold (thin parts are protected)
  carve()/add()/relief()/groove()  sculpt new relief, cracks, ridges ... (hearts add their own detail with these)
  shade()    re-bakes the face-direction shade from *blurred* normals + a soft ambient occlusion, so round things look round
  export()   greedy-meshes every bone into boxes, one texel per 0.25 face (64 px/unit on the heart only), same bone tree

Bones with a rotation (the stone whale's plates) keep their own grid in bone-local space and are never grown (only trimmed),
so rotated neighbours cannot collide.  Everything else shares one world grid, so every cell has exactly one owner -> no overlaps.
Glow cells are flat-coloured and never smoothed away (the glowmask must never be empty).
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import voxel  # noqa: E402

H = 0.25
SRC_H = 0.5
AW = 512
LIGHT = np.array([-0.45, 0.75, 0.5])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

FACES = {
    "north": (lambda b: (b[3], b[1], b[2]), lambda s: (-s[0], 0, 0), lambda s: (0, s[1], 0), (0, 0, -1)),
    "south": (lambda b: (b[0], b[1], b[5]), lambda s: (s[0], 0, 0), lambda s: (0, s[1], 0), (0, 0, 1)),
    "east": (lambda b: (b[3], b[1], b[5]), lambda s: (0, 0, -s[2]), lambda s: (0, s[1], 0), (1, 0, 0)),
    "west": (lambda b: (b[0], b[1], b[2]), lambda s: (0, 0, s[2]), lambda s: (0, s[1], 0), (-1, 0, 0)),
    "up": (lambda b: (b[0], b[4], b[5]), lambda s: (s[0], 0, 0), lambda s: (0, 0, -s[2]), (0, 1, 0)),
    "down": (lambda b: (b[0], b[1], b[2]), lambda s: (s[0], 0, 0), lambda s: (0, 0, s[2]), (0, -1, 0)),
}


# ------------------------------------------------------------------ one grid
class Vox:
    """A 0.25-unit cell grid. lab 0 = empty, otherwise the id of the owning bone (names[bone] = id)."""

    def __init__(self, lo, n, R=None, names=None):
        self.lo = np.array(lo, float)
        self.n = tuple(int(v) for v in n)
        self.R = np.eye(3) if R is None else R
        self.lab = np.zeros(self.n, np.int16)
        self.col = np.zeros(self.n + (3,), np.float32)      # albedo (no shade)
        self.glow = np.zeros(self.n, bool)
        self.names = dict(names or {})
        self._coords = None

    # -- coordinates
    def axes(self):
        return [self.lo[i] + (np.arange(self.n[i]) + 0.5) * H for i in range(3)]

    def coords(self):
        """X, Y, Z centre of every cell (broadcastable open grids, cheap)."""
        a = self.axes()
        return a[0][:, None, None], a[1][None, :, None], a[2][None, None, :]

    def full_coords(self):
        a = self.axes()
        return np.meshgrid(*a, indexing="ij")

    def lid(self, name):
        if name not in self.names:
            self.names[name] = max(self.names.values(), default=0) + 1
        return self.names[name]

    @property
    def occ(self):
        return self.lab > 0

    def cell_of(self, p):
        return np.floor((np.asarray(p, float) - self.lo) / H).astype(int)

    # -- basic editing
    def put(self, name, mask, color, glow=False, over=False):
        """Fill the cells of mask (empty ones only unless over) as part of bone `name`."""
        if not over:
            mask = mask & (self.lab == 0)
        if not mask.any():
            return mask
        self.lab[mask] = self.lid(name)
        if callable(color):
            self.col[mask] = color(mask)
        else:
            self.col[mask] = np.array(color, np.float32)
        self.glow[mask] = glow
        return mask

    def paint(self, mask, color, glow=None):
        mask = mask & (self.lab > 0)
        if not mask.any():
            return mask
        self.col[mask] = color(mask) if callable(color) else np.array(color, np.float32)
        if glow is not None:
            self.glow[mask] = glow
        return mask

    def carve(self, mask, keep_glow=True):
        m = mask & (self.lab > 0)
        if keep_glow:
            m &= ~self.glow
        self.lab[m] = 0
        return m

    def surface(self, occ=None):
        o = self.occ if occ is None else occ
        p = np.pad(o, 1)
        inner = (p[2:, 1:-1, 1:-1] & p[:-2, 1:-1, 1:-1] & p[1:-1, 2:, 1:-1] & p[1:-1, :-2, 1:-1]
                 & p[1:-1, 1:-1, 2:] & p[1:-1, 1:-1, :-2])
        return o & ~inner

    def nearest_fill(self, src_mask, dst_mask):
        """label and albedo/glow of the nearest src cell for every dst cell."""
        if not dst_mask.any() or not src_mask.any():
            return
        idx = ndi.distance_transform_edt(~src_mask, return_distances=False, return_indices=True)
        ii = tuple(a[dst_mask] for a in idx)
        self.lab[dst_mask] = self.lab[ii]
        self.col[dst_mask] = self.col[ii]
        self.glow[dst_mask] = self.glow[ii]


# ------------------------------------------------------------------ noise
def smooth_noise(vox, scale, seed, octaves=1):
    """Smooth 3D noise in [-1, 1] with feature size `scale` (units), deterministic."""
    rng = np.random.default_rng(seed)
    out = np.zeros(vox.n, np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        sc = max(scale / (2 ** o), H * 1.5)
        small = tuple(max(2, int(np.ceil(n * H / sc)) + 2) for n in vox.n)
        r = rng.standard_normal(small).astype(np.float32)
        r = ndi.gaussian_filter(r, 0.8)
        r /= max(float(np.abs(r).max()), 1e-6)
        z = ndi.zoom(r, [vox.n[i] / (small[i] - 0.0) for i in range(3)], order=1)
        z = z[:vox.n[0], :vox.n[1], :vox.n[2]]
        if z.shape != vox.n:
            z = np.pad(z, [(0, vox.n[i] - z.shape[i]) for i in range(3)], mode="edge")
        out += amp * z
        tot += amp
        amp *= 0.5
    out /= tot
    m = float(np.abs(out).max())
    return out / max(m, 1e-6)


# ------------------------------------------------------------------ loading a baked source
def _bone_world(g, name, cache):
    bones = {b["name"]: b for b in g["bones"]}
    if name in cache:
        return cache[name]
    b = bones[name]
    rot = np.array(b.get("rotation", [0, 0, 0]), float)
    R = voxel._rot(*rot)
    cache[name] = R
    if "parent" in b and "rotation" in bones[b["parent"]]:
        cache[name] = voxel._rot(*np.array(bones[b["parent"]].get("rotation", [0, 0, 0]), float)) @ R
    return cache[name]


def _stamp_cubes(cubes, tex, glow, lo, n05, lab_id, lab, col, glw):
    """Write the cubes (local coordinates, 0.5-unit cells) into the 0.5-unit arrays."""
    for c in cubes:
        o = np.array(c["origin"], float)
        s = np.array(c["size"], float)
        i0 = np.round((o - lo) / SRC_H).astype(int)
        i1 = np.round((o + s - lo) / SRC_H).astype(int)
        sl = tuple(slice(i0[a], i1[a]) for a in range(3))
        lab[sl] = lab_id
        bx = (i0[0], i0[1], i0[2], i1[0], i1[1], i1[2])
        sz = tuple(i1 - i0)
        for fname, d in c["uv"].items():
            cof, euf, evf, nrm = FACES[fname]
            W, Hh = (int(round(abs(v))) for v in d["uv_size"])
            u0, v0 = d["uv"]
            eu = np.array(euf(sz), float)
            ev = np.array(evf(sz), float)
            co = np.array(cof(bx), float)
            rr, cc = np.meshgrid(np.arange(Hh), np.arange(W), indexing="ij")
            tt = (rr + 0.5) / Hh if fname == "down" else 1 - (rr + 0.5) / Hh
            pos = co[None, None, :] + ((cc + 0.5) / W)[..., None] * eu + tt[..., None] * ev
            cell = np.floor(pos - 0.5 * np.array(nrm)).astype(int)
            xi, yi, zi = cell[..., 0], cell[..., 1], cell[..., 2]
            ty, tx = int(round(v0)), int(round(u0))
            patch = tex[ty:ty + Hh, tx:tx + W, :3]
            gp = glow[ty:ty + Hh, tx:tx + W, 3] > 0
            col[xi, yi, zi] = patch
            glw[xi, yi, zi] = gp


def unshade_estimate(lab, col, glw):
    """albedo = baked colour / estimated baked shade (the old exporters used lum ~ 0.68 + 0.38 * lambert)."""
    occ = lab > 0
    e = ~np.pad(occ, 1)
    nx = e[2:, 1:-1, 1:-1].astype(float) - e[:-2, 1:-1, 1:-1]
    ny = e[1:-1, 2:, 1:-1].astype(float) - e[1:-1, :-2, 1:-1]
    nz = e[1:-1, 1:-1, 2:].astype(float) - e[1:-1, 1:-1, :-2]
    n = np.stack([nx, ny, nz], -1)
    nl = np.linalg.norm(n, axis=-1)
    lamb = np.where(nl > 0, (n @ LIGHT) / np.maximum(nl, 1e-9), 0.0)
    lum = 0.69 + 0.38 * np.clip(lamb, 0, 1)
    m = occ & ~glw
    out = col.copy()
    out[m] = np.clip(col[m] / lum[m][:, None], 0, 255)
    return out


class Source:
    """A baked phase: the geo/tex/glow, split into grids (world + one per rotated bone)."""

    def __init__(self, folder, phase):
        folder = Path(folder)
        self.geo = json.loads((folder / f"phase{phase}.geo.json").read_text(encoding="utf-8"))
        self.tex = np.array(Image.open(folder / f"phase{phase}.tex.png").convert("RGBA"))
        self.glow = np.array(Image.open(folder / f"phase{phase}.glow.png").convert("RGBA"))
        self.g = self.geo["minecraft:geometry"][0]
        self.bones = self.g["bones"]
        self.world = None           # Vox with every unrotated bone
        self.parts = {}             # bone name -> Vox of a rotated bone
        self._load()

    def _load(self):
        rotated = [b for b in self.bones if "rotation" in b and any(abs(v) > 1e-6 for v in b["rotation"]) and b.get("cubes")]
        rot_names = {b["name"] for b in rotated}
        plain = [b for b in self.bones if b["name"] not in rot_names and b.get("cubes")]
        self.world = self._grid(plain, np.eye(3), margin=1.0)
        for b in rotated:
            R = _bone_world(self.g, b["name"], {})
            self.parts[b["name"]] = self._grid([b], R, margin=1.0)

    def _grid(self, bones, R, margin):
        los, his = [], []
        for b in bones:
            for c in b["cubes"]:
                los.append(c["origin"])
                his.append([c["origin"][i] + c["size"][i] for i in range(3)])
        if not los:
            return Vox([0, 0, 0], (1, 1, 1), R)
        lo = np.floor((np.min(los, 0) - margin) / SRC_H) * SRC_H
        hi = np.ceil((np.max(his, 0) + margin) / SRC_H) * SRC_H
        n05 = np.round((hi - lo) / SRC_H).astype(int)
        lab = np.zeros(tuple(n05), np.int16)
        col = np.zeros(tuple(n05) + (3,), np.float32)
        glw = np.zeros(tuple(n05), bool)
        names = {}
        for b in bones:
            names[b["name"]] = len(names) + 1
            _stamp_cubes(b["cubes"], self.tex, self.glow, lo, n05, names[b["name"]], lab, col, glw)
        # interior cells have no texel: take the nearest textured surface cell
        has = np.zeros(lab.shape, bool)
        has[:] = col.sum(-1) > 0
        occ = lab > 0
        miss = occ & ~has
        if miss.any() and has.any():
            idx = ndi.distance_transform_edt(~(has & occ), return_distances=False, return_indices=True)
            ii = tuple(a[miss] for a in idx)
            col[miss] = col[ii]
            glw[miss] = glw[ii]
        col = unshade_estimate(lab, col, glw)
        v = Vox(lo, np.array(lab.shape) * 2, R, names)
        up = lambda a: np.repeat(np.repeat(np.repeat(a, 2, 0), 2, 1), 2, 2)
        v.lab = up(lab)
        v.col = up(col)
        v.glow = up(glw)
        return v

    def grids(self):
        yield self.world
        for v in self.parts.values():
            yield v


# ------------------------------------------------------------------ smoothing
def ball(r):
    k = int(np.ceil(r))
    z, y, x = np.mgrid[-k:k + 1, -k:k + 1, -k:k + 1]
    return (x * x + y * y + z * z) <= r * r + 1e-9


def smooth(vox, labels=None, sigma=1.6, thr=0.5, grow=2, thin_r=2.2, keep=None, allow_grow=True, where=None):
    """Round the stair steps of the bulky parts (cells whose owner is in `labels`, default all).

    grow:   cells may be added at most this many cells (0.25 units each) outside the old shape
    thin_r: parts thinner than ~2*thin_r cells are left alone (veins, rims, feathers)
    keep:   extra mask of cells that must stay as they are
    where:  only the cells inside this mask are smoothed (e.g. the visible front half: 0.25 only where it can be seen)
    """
    ids = None if labels is None else [vox.names[l] for l in labels if l in vox.names]
    region = vox.occ if ids is None else np.isin(vox.lab, ids)
    if where is not None:
        region = region & where
    if not region.any():
        return
    d = ndi.gaussian_filter(region.astype(np.float32), sigma)
    new = d > thr
    bulk = ndi.binary_opening(region, structure=ball(thin_r))
    thin = region & ~ndi.binary_dilation(bulk, structure=ball(thin_r + 0.3))
    new |= thin
    new |= region & vox.glow
    if keep is not None:
        new |= region & keep
    removed = region & ~new
    added = new & ~region & (vox.lab == 0)
    if allow_grow:
        added &= ndi.binary_dilation(region, structure=ball(max(grow, 1)))
    else:
        added[:] = False
    vox.lab[removed] = 0
    if added.any():
        vox.nearest_fill(region & ~removed, added)


def relief(vox, mask, amp=1.0, scale=0.9, seed=1, bias=0.0, octaves=2, protect_glow=True, grow=True):
    """Roughen the surface of `mask`: noise > t removes surface cells, noise < -t adds a cell on the surface (amp in cells)."""
    n = smooth_noise(vox, scale, seed, octaves) + bias
    surf = vox.surface() & mask
    cut = surf & (n > 0.28 / amp)
    if protect_glow:
        cut &= ~vox.glow
    vox.lab[cut] = 0
    if grow:
        occ = vox.occ
        shell = ndi.binary_dilation(occ, structure=ball(1.0)) & ~occ
        src = occ & mask
        # only add near cells of the target
        add = shell & (n < -0.38 / amp) & ndi.binary_dilation(src, structure=ball(1.0))
        vox.nearest_fill(src, add)


def ao_shade(vox, glow_flat=True, k_ao=0.9, sigma_n=1.3, sigma_ao=3.0, lo=0.66, span=0.40, ao_min=0.72, extra=None):
    """Return the baked (shaded) colour of every cell: albedo * (lo + span * lambert(blurred normal)) * ao."""
    occ = vox.occ.astype(np.float32)
    d = ndi.gaussian_filter(occ, sigma_n)
    gx, gy, gz = np.gradient(d)
    n = -np.stack([gx, gy, gz], -1)
    n = n @ vox.R.T                         # bone-local -> world
    nl = np.linalg.norm(n, axis=-1)
    lamb = np.where(nl > 1e-5, (n @ LIGHT) / np.maximum(nl, 1e-5), 0.3)
    lum = lo + span * np.clip(lamb, -0.25, 1)
    d2 = ndi.gaussian_filter(occ, sigma_ao)
    ao = np.clip(1.0 - k_ao * (d2 - 0.5), ao_min, 1.06)
    f = (lum * ao)[..., None]
    out = vox.col * f
    if extra is not None:
        out = out * extra[..., None]
    flat = vox.glow if glow_flat else np.zeros(vox.n, bool)
    out[flat] = vox.col[flat]
    return np.clip(out, 0, 255)


# ------------------------------------------------------------------ box export
def greedy(occ):
    best = None
    for perm in ((0, 1, 2), (0, 2, 1), (1, 0, 2), (1, 2, 0), (2, 0, 1), (2, 1, 0)):
        A = np.transpose(occ, perm).copy()
        used = np.zeros_like(A)
        n0, n1, n2 = A.shape
        boxes = []
        for i, j, k in np.argwhere(A):
            if used[i, j, k]:
                continue
            k1 = k + 1
            while k1 < n2 and A[i, j, k1] and not used[i, j, k1]:
                k1 += 1
            j1 = j + 1
            while j1 < n1 and A[i, j1, k:k1].all() and not used[i, j1, k:k1].any():
                j1 += 1
            i1 = i + 1
            while i1 < n0 and A[i1, j:j1, k:k1].all() and not used[i1, j:j1, k:k1].any():
                i1 += 1
            used[i:i1, j:j1, k:k1] = True
            lo, hi = [0, 0, 0], [0, 0, 0]
            for a, (l, h) in enumerate(((i, i1), (j, j1), (k, k1))):
                lo[perm[a]], hi[perm[a]] = l, h
            boxes.append((*lo, *hi))
        if best is None or len(boxes) < len(best):
            best = boxes
    return best


def export(src_geo, grids_shaded):
    """grids_shaded: list of (Vox, shaded colour array). Returns (geo, tex, glow, stats)."""
    recs = []
    cubes_by_bone = {}
    for vox, shaded in grids_shaded:
        occ_all = vox.lab > 0
        pad = np.pad(occ_all, 1)
        for name, lid in vox.names.items():
            m = vox.lab == lid
            if not m.any():
                cubes_by_bone[name] = []
                continue
            cubes = []
            for bx in greedy(m):
                x0, y0, z0, x1, y1, z1 = bx
                s = (x1 - x0, y1 - y0, z1 - z0)
                cube = {"origin": [round(float(vox.lo[0] + x0 * H), 4), round(float(vox.lo[1] + y0 * H), 4), round(float(vox.lo[2] + z0 * H), 4)],
                        "size": [s[0] * H, s[1] * H, s[2] * H], "uv": {}}
                for fname, (cof, euf, evf, nrm) in FACES.items():
                    co = np.array(cof(bx), float)
                    eu = np.array(euf(s), float)
                    ev = np.array(evf(s), float)
                    W = int(round(np.abs(eu).sum()))
                    Hh = int(round(np.abs(ev).sum()))
                    rr, cc = np.meshgrid(np.arange(Hh), np.arange(W), indexing="ij")
                    tt = (rr + 0.5) / Hh if fname == "down" else 1 - (rr + 0.5) / Hh
                    pos = co[None, None, :] + ((cc + 0.5) / W)[..., None] * eu + tt[..., None] * ev
                    cell = np.floor(pos - 0.5 * np.array(nrm)).astype(int)
                    xi, yi, zi = cell[..., 0], cell[..., 1], cell[..., 2]
                    nb = pad[xi + 1 + nrm[0], yi + 1 + nrm[1], zi + 1 + nrm[2]]
                    if nb.all():
                        continue
                    tex = np.zeros((Hh, W, 4), np.uint8)
                    tex[..., :3] = np.clip(shaded[xi, yi, zi], 0, 255).astype(np.uint8)
                    tex[..., 3] = 255
                    recs.append({"W": W, "H": Hh, "tex": tex, "gl": vox.glow[xi, yi, zi], "cube": cube, "face": fname, "bone": name})
                cubes.append(cube)
            cubes_by_bone[name] = cubes
    recs.sort(key=lambda r: (-r["H"], -r["W"]))
    x = y = shelf = 0
    for r in recs:
        if x + r["W"] > AW:
            x, y, shelf = 0, y + shelf, 0
        r["u"], r["v"] = x, y
        x += r["W"]
        shelf = max(shelf, r["H"])
    AH = max(y + shelf, 1)
    tex = np.zeros((AH, AW, 4), np.uint8)
    gtex = np.zeros((AH, AW, 4), np.uint8)
    for r in recs:
        u, v, W, Hh = r["u"], r["v"], r["W"], r["H"]
        tex[v:v + Hh, u:u + W] = r["tex"]
        gtex[v:v + Hh, u:u + W, :3] = r["tex"][..., :3] * r["gl"][..., None]
        gtex[v:v + Hh, u:u + W, 3] = np.where(r["gl"], 255, 0)
        r["cube"]["uv"][r["face"]] = {"uv": [u, v], "uv_size": [W, Hh]}
    geo = copy.deepcopy(src_geo)
    g = geo["minecraft:geometry"][0]
    g["description"]["texture_width"] = AW
    g["description"]["texture_height"] = AH
    for b in g["bones"]:
        b["cubes"] = cubes_by_bone.get(b["name"], []) if b["name"] in cubes_by_bone else []
        if not b["cubes"]:
            b.pop("cubes")
            b["cubes"] = []
    per = {}
    for r in recs:
        per[r["bone"]] = per.get(r["bone"], 0) + 1
    return geo, tex, gtex, {"faces": len(recs), "atlas": [AW, AH], "per_bone": per}


def sat_check(geo):
    """voxel.check_overlaps (axis-aligned boxes inside a bone)."""
    return voxel.check_overlaps(geo)


def count(geo):
    n = q = 0
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            n += 1
            q += len(c["uv"])
    return n, q


def save(folder, phase, geo, tex, gtex):
    folder = Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"phase{phase}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex).save(folder / f"phase{phase}.tex.png")
    Image.fromarray(gtex).save(folder / f"phase{phase}.glow.png")


def render_geo(geo, tex, gtex, yaw=35, pitch=22, size=(420, 420)):
    faces = voxel.load_geo(geo, tex, gtex)
    return voxel.render(faces, yaw, pitch, size)


# ------------------------------------------------------------------ sculpting helpers
def depth_inside(vox, occ=None):
    """distance (in cells) of every filled cell to the nearest empty cell (1 = surface cell)."""
    o = vox.occ if occ is None else occ
    return ndi.distance_transform_cdt(o, metric="chessboard")


def height_outside(vox, occ=None):
    """distance (in cells) of every empty cell to the nearest filled cell (1 = touching the surface)."""
    o = vox.occ if occ is None else occ
    return ndi.distance_transform_cdt(~o, metric="chessboard")


def groove(vox, mask, cond, depth=1, color=None, glow=False, only=None):
    """Carve a groove `depth` cells deep under the surface cells marked by `cond` (a path, a rib line ...).
    The cells under a path cell are found through the nearest empty cell, so the groove keeps the width of the path and never
    hollows out the walls. color: recolour the new floor; glow: make the floor a glow pixel (a light leak)."""
    d = depth_inside(vox)
    own = np.ones(vox.n, bool) if only is None else np.isin(vox.lab, [vox.names[n] for n in only if n in vox.names])
    P = vox.occ & mask & cond & own & (d == 1)
    if not P.any():
        return P
    E = ndi.binary_dilation(P, structure=ndi.generate_binary_structure(3, 1)) & ~vox.occ
    idx = ndi.distance_transform_edt(vox.occ, return_distances=False, return_indices=True)
    under = E[idx[0], idx[1], idx[2]]
    m = vox.occ & mask & own & (d <= depth) & under
    floor = vox.occ & mask & own & (d == depth + 1) & under
    vox.carve(m)
    if color is not None or glow:
        vox.paint(floor, color if color is not None else vox.col[floor].mean(0), glow=glow)
    return m


def raise_pattern(vox, mask, cond, height=1, color=None, only=None, glow=False, bone=None, anchored=False):
    """Add cells on top of the surface where `cond` holds (ridges, studs, pearls). The new cells join the nearest owner
    (or `bone`)."""
    h = height_outside(vox)
    if anchored:      # cond marks surface cells: grow the pattern outwards from them
        cond = ndi.binary_dilation(cond, structure=ball(1.3)) & ~vox.occ
    src = vox.occ
    if only is not None:
        src = src & np.isin(vox.lab, [vox.names[n] for n in only if n in vox.names])
    reach = ndi.binary_dilation(src, structure=ball(height + 0.2)) & ~vox.occ
    add = reach & cond & mask & (h <= height)
    if not add.any():
        return add
    vox.nearest_fill(src, add)
    if bone is not None:
        vox.lab[add] = vox.lid(bone)
    if color is not None:
        vox.col[add] = color(add) if callable(color) else np.array(color, np.float32)
    vox.glow[add] = glow
    return add


def tint(vox, mask, colors, scale=1.0, seed=3, octaves=2, mix=1.0):
    """Recolour `mask` with a smooth noise gradient between `colors` (list of rgb)."""
    n = (smooth_noise(vox, scale, seed, octaves) * 0.5 + 0.5)
    cs = np.array(colors, np.float32)
    k = len(cs) - 1
    t = np.clip(n, 0, 0.9999) * k
    i = t.astype(int)
    f = (t - i)[..., None]
    c = cs[i] * (1 - f) + cs[np.minimum(i + 1, k)] * f
    m = mask & vox.occ
    vox.col[m] = vox.col[m] * (1 - mix) + c[m] * mix
    return m


def surface_walk(vox, mask, starts, steps, seed, branch=0.04, persist=0.82, width=1, dirs=None, max_paths=60, jitter=0.45):
    """Branching random walks that hug the surface of `mask` (organic veins and cracks). Returns the bool mask of the paths,
    thickened by `width` cells. starts: list of cells (ix, iy, iz); dirs: optional start directions."""
    rng = np.random.default_rng(seed)
    surf = vox.surface() & mask
    S = surf
    out = np.zeros(vox.n, bool)
    nb = np.array([(a, b, c) for a in (-1, 0, 1) for b in (-1, 0, 1) for c in (-1, 0, 1) if (a, b, c) != (0, 0, 0)])
    nbn = nb / np.linalg.norm(nb, axis=1)[:, None]
    shp = np.array(vox.n)
    walkers = []
    for i, st in enumerate(starts):
        d = np.array(dirs[i], float) if dirs is not None else rng.normal(size=3)
        walkers.append((np.array(st), d / max(np.linalg.norm(d), 1e-6), steps))
    visited = set()
    done = 0
    while walkers and done < max_paths * 8:
        c, d, left = walkers.pop()
        done += 1
        for _ in range(left):
            out[tuple(c)] = True
            visited.add(tuple(c))
            cand = c + nb
            ok = np.all((cand >= 0) & (cand < shp), axis=1)
            cand, nn = cand[ok], nbn[ok]
            ok2 = S[cand[:, 0], cand[:, 1], cand[:, 2]]
            cand, nn = cand[ok2], nn[ok2]
            if len(cand) == 0:
                break
            sc = nn @ d + rng.normal(scale=jitter, size=len(cand))
            sc -= np.array([2.0 if tuple(x) in visited else 0.0 for x in cand])
            j = int(np.argmax(sc))
            mv = cand[j] - c
            d = persist * d + (1 - persist) * mv / np.linalg.norm(mv)
            d /= max(np.linalg.norm(d), 1e-6)
            c = cand[j]
            if len(walkers) < max_paths and rng.random() < branch:
                a = rng.normal(size=3)
                walkers.append((c.copy(), d + 0.8 * a / np.linalg.norm(a), left // 2))
    if width > 1:
        out = ndi.binary_dilation(out, structure=ball(width - 0.5)) & vox.occ & mask
    return out


def nearest_surface_cell(vox, mask, p):
    """cell index of the surface cell of `mask` nearest to world point p (local coordinates)."""
    surf = np.argwhere(vox.surface() & mask)
    if not len(surf):
        return None
    pts = vox.lo + (surf + 0.5) * H
    j = int(np.argmin(((pts - np.asarray(p, float)) ** 2).sum(1)))
    return surf[j]


# ------------------------------------------------------------------ the shared driver (rough sheet, build, budget)
FACE_BUDGET = 6000


def finish(src):
    """shade every grid of a Source and mesh it: (geo, tex, glow, stats)."""
    gs = [(g, ao_shade(g)) for g in src.grids()]
    return export(src.geo, gs)


def render_crop(geo, tex, gtex, yaw, pitch, crop, out=(330, 330), size=(1000, 1000)):
    im = render_geo(geo, tex, gtex, yaw, pitch, size)
    w, h = im.size
    return im.crop((int(w * crop[0]), int(h * crop[1]), int(w * crop[2]), int(h * crop[3]))).resize(out, Image.LANCZOS)


def rough_sheet(name, title, build, variants=("A", "B", "C"), phase=1, views=None, crops=None, src_dir=None, out_dir=None):
    """The comparison sheet of one phase: `now` (the baked source as it is) next to every variant.
    build(variant, phase) -> Source (already sculpted; variant 'now' must return the untouched Source).
    views: list of (yaw, pitch) full views; crops: list of (yaw, pitch, (x0, y0, x1, y1)) close-ups."""
    import time
    views = views or [(35, 22), (0, 8)]
    crops = crops or []
    cols, notes = [], []
    for variant in ("now",) + tuple(variants):
        t = time.time()
        src = build(variant, phase)
        if variant == "now":
            geo, tex, gl = src.geo, src.tex, src.glow
            st = {}
        else:
            geo, tex, gl, st = finish(src)
        nb, nq = count(geo)
        ov = sat_check(geo)
        lit = int((gl[..., 3] > 0).sum())
        print(f"{name} {variant}: {nb} boxes / {nq} quads, overlaps {len(ov)}, glow px {lit}, {time.time() - t:.1f}s", st.get("per_bone", ""))
        notes.append(f"{variant}: {nb} boxes / {nq} quads")
        row = [render_geo(geo, tex, gl, y, p, (330, 330)) for y, p in views]
        row += [render_crop(geo, tex, gl, y, p, c) for y, p, c in crops]
        cols.append((f"{variant} ({nb}/{nq})", row))
    sh = voxel.sheet(title, cols, note="; ".join(notes))
    dst = Path(out_dir or ROOT / "docs/review/high_poly_roughs")
    dst.mkdir(parents=True, exist_ok=True)
    sh.save(dst / f"{name}_hp.png")
    return dst / f"{name}_hp.png"


def build_phases(name, build, variant, out_folder, phases=(1, 2, 3)):
    """Sculpt every phase with `variant` and write tools/sources/<x>_hp/phaseN.(geo.json|tex.png|glow.png). Prints the checks."""
    ok = True
    for ph in phases:
        src = build(variant, ph)
        geo, tex, gl, st = finish(src)
        nb, nq = count(geo)
        ov = sat_check(geo)
        lit = int((gl[..., 3] > 0).sum())
        flag = "" if (nq <= FACE_BUDGET and not ov and lit > 0) else "  <-- CHECK"
        ok &= not flag
        print(f"{name} phase {ph}: {nb} boxes / {nq} quads (budget {FACE_BUDGET}), overlaps {len(ov)}, glow px {lit}{flag}")
        save(out_folder, ph, geo, tex, gl)
    return ok
