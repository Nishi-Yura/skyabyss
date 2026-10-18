"""Smooth-style rough kit: voxelised implicit shapes -> non-overlapping boxes -> geo.json + baked-shading texture.

Idea (for the "smooth like the reference image" request):
  * Silhouettes are built from implicit shapes (ellipsoids, rounded boxes, capsules) sampled on a 0.5 (or 0.25) cell grid,
    so a head becomes a stepped sphere instead of a flat slab.  Voxels of one bone are merged into big boxes (greedy,
    best of 6 axis orders).  Boxes never overlap (every cell has exactly one owner bone), hidden faces get no uv.
  * The texture is *baked*: every face texel is lit from the 3D scene (smoothed surface normal, hemisphere light, soft
    ambient occlusion from a blurred occupancy field, shadow tint), so gradients run across the stepped surface.
    The result is divided by Minecraft's per-face brightness (partially) so the in-game flat face shading does not
    fight the baked light.
  * Face details (eyes, brows, mouth, moss spots...) are painted by a per-model `paint` callback in 3D, so they follow
    the stepped surface and cannot "float" as separate planes.
Everything is a rough: nothing here is installed into src/.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).parent))
import voxel  # noqa: E402
from PIL import Image  # noqa: E402

FINE = 0.25            # fine grid (units)
SX = 4                 # png pixels per geo-uv unit
MC_SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def clamp8(a):
    return np.clip(np.rint(a), 0, 255).astype(np.uint8)


class Model:
    def __init__(self, ident, lo=(-16, -2, -16), hi=(16, 36, 16)):
        self.ident = ident
        self.lo = np.array(lo, float)
        self.n = tuple(int(round((h - l) / FINE)) for l, h in zip(lo, hi))
        self.occ = np.zeros(self.n, bool)
        self.owner = np.full(self.n, -1, np.int16)
        self.mat = np.zeros(self.n, np.int16)
        self.bones = []          # dict(name,parent,pivot,cell)
        self.mats = []           # dict(name, albedo, shadow, ...)
        self.paint = None        # callable(model, p, n, matname, bone) -> (rgb | None, glow)
        self.light_dir = np.array([0.45, 0.75, 0.55])
        self.light_dir /= np.linalg.norm(self.light_dir)

    # ------------------------------------------------------------------ registry
    def add_mat(self, name, albedo, shadow=None, light=(1.0, 0.97, 0.92), **kw):
        a = np.array(albedo, float)
        sh = np.array(shadow, float) if shadow is not None else a * np.array([0.55, 0.55, 0.72])
        self.mats.append(dict(name=name, albedo=a, shadow=sh, light=np.array(light, float), **kw))
        return len(self.mats) - 1

    def mat_id(self, name):
        for i, m in enumerate(self.mats):
            if m["name"] == name:
                return i
        raise KeyError(name)

    def add_bone(self, name, parent=None, pivot=(0, 0, 0), cell=0.5):
        self.bones.append(dict(name=name, parent=parent, pivot=list(pivot), cell=cell))
        return len(self.bones) - 1

    def bone_id(self, name):
        for i, b in enumerate(self.bones):
            if b["name"] == name:
                return i
        raise KeyError(name)

    # ------------------------------------------------------------------ rasterising
    def _cell_centers(self, cell, bbox):
        """coordinate arrays (cell-centre) for the cells of size `cell` that overlap bbox (lo, hi)."""
        lo = np.maximum(np.floor((np.array(bbox[0]) - self.lo) / cell), 0).astype(int)
        hi = np.minimum(np.ceil((np.array(bbox[1]) - self.lo) / cell), np.array(self.n) * FINE / cell).astype(int)
        ax = [self.lo[i] + (np.arange(lo[i], hi[i]) + 0.5) * cell for i in range(3)]
        X, Y, Z = np.meshgrid(*ax, indexing="ij")
        return lo, hi, X, Y, Z

    def fill(self, bone, shape, mat, bbox):
        """set (or erase if mat is None) the cells of `bone` where shape(X,Y,Z) is true. bbox limits the work."""
        b = self.bone_id(bone)
        cell = self.bones[b]["cell"]
        k = int(round(cell / FINE))
        lo, hi, X, Y, Z = self._cell_centers(cell, bbox)
        m = shape(X, Y, Z)
        if m.ndim == 0 or not m.any():
            return
        mm = np.repeat(np.repeat(np.repeat(m, k, 0), k, 1), k, 2)
        sl = tuple(slice(lo[i] * k, lo[i] * k + mm.shape[i]) for i in range(3))
        if mat is None:
            self.occ[sl] &= ~mm
            self.owner[sl][mm] = -1
        else:
            mid = self.mat_id(mat) if isinstance(mat, str) else mat
            self.occ[sl] |= mm
            self.owner[sl][mm] = b
            self.mat[sl][mm] = mid

    # shape helpers: return a function(X,Y,Z)->bool plus a bbox
    @staticmethod
    def ellipsoid(c, r, power=2.0):
        c = np.array(c, float)
        r = np.array(r, float)

        def f(X, Y, Z):
            d = (np.abs((X - c[0]) / r[0]) ** power + np.abs((Y - c[1]) / r[1]) ** power + np.abs((Z - c[2]) / r[2]) ** power)
            return d <= 1.0
        return f, (c - r - 0.5, c + r + 0.5)

    @staticmethod
    def rbox(c, h, rad=1.0):
        """rounded box: centre c, half sizes h, corner radius rad."""
        c = np.array(c, float)
        h = np.array(h, float)

        def f(X, Y, Z):
            q = np.stack([np.abs(X - c[0]) - (h[0] - rad), np.abs(Y - c[1]) - (h[1] - rad), np.abs(Z - c[2]) - (h[2] - rad)])
            out = np.sqrt((np.maximum(q, 0) ** 2).sum(0))
            inside = np.minimum(np.maximum(np.maximum(q[0], q[1]), q[2]), 0)
            return (out + inside) <= rad
        return f, (c - h - 0.5, c + h + 0.5)

    @staticmethod
    def capsule(a, b, r0, r1=None):
        a = np.array(a, float)
        b = np.array(b, float)
        r1 = r0 if r1 is None else r1
        ab = b - a
        L2 = float(ab @ ab)

        def f(X, Y, Z):
            P = np.stack([X - a[0], Y - a[1], Z - a[2]])
            t = np.clip((P[0] * ab[0] + P[1] * ab[1] + P[2] * ab[2]) / max(L2, 1e-9), 0, 1)
            d2 = ((P[0] - t * ab[0]) ** 2 + (P[1] - t * ab[1]) ** 2 + (P[2] - t * ab[2]) ** 2)
            r = r0 + (r1 - r0) * t
            return d2 <= r * r
        mr = max(r0, r1)
        return f, (np.minimum(a, b) - mr - 0.5, np.maximum(a, b) + mr + 0.5)

    @staticmethod
    def cone_y(cx, cz, y0, y1, r0, r1, sz=1.0):
        """vertical truncated cone (robes, hair tails); sz stretches z."""
        def f(X, Y, Z):
            t = np.clip((Y - y0) / max(y1 - y0, 1e-9), 0, 1)
            r = r0 + (r1 - r0) * t
            return (((X - cx) / r) ** 2 + ((Z - cz) / (r * sz)) ** 2 <= 1.0) & (Y >= y0) & (Y <= y1)
        m = max(r0, r1) * max(1, sz)
        return f, (np.array([cx - m, y0, cz - m]) - 0.5, np.array([cx + m, y1, cz + m]) + 0.5)

    def add(self, bone, shape, mat):
        f, bb = shape
        self.fill(bone, f, mat, bb)

    def sub(self, bone, shape):
        f, bb = shape
        self.fill(bone, f, None, bb)

    # ------------------------------------------------------------------ merging
    def _boxes_for(self, b):
        cell = self.bones[b]["cell"]
        k = int(round(cell / FINE))
        own = (self.owner == b)
        if not own.any():
            return []
        nx, ny, nz = (s // k for s in own.shape)
        co = own[:nx * k, :ny * k, :nz * k].reshape(nx, k, ny, k, nz, k).all(axis=(1, 3, 5))
        best = None
        for perm in [(0, 1, 2), (0, 2, 1), (1, 0, 2), (1, 2, 0), (2, 0, 1), (2, 1, 0)]:
            g = np.transpose(co, perm).copy()
            used = np.zeros_like(g)
            boxes = []
            idx = np.argwhere(g)
            for i, j, kk in idx:
                if used[i, j, kk]:
                    continue
                e = 1
                while kk + e < g.shape[2] and g[i, j, kk + e] and not used[i, j, kk + e]:
                    e += 1
                d = 1
                while j + d < g.shape[1] and g[i, j + d, kk:kk + e].all() and not used[i, j + d, kk:kk + e].any():
                    d += 1
                h = 1
                while i + h < g.shape[0] and g[i + h, j:j + d, kk:kk + e].all() and not used[i + h, j:j + d, kk:kk + e].any():
                    h += 1
                used[i:i + h, j:j + d, kk:kk + e] = True
                lo = np.zeros(3, int)
                sz = np.zeros(3, int)
                lo[list(perm)] = (i, j, kk)
                sz[list(perm)] = (h, d, e)
                boxes.append((lo, sz))
            if best is None or len(boxes) < len(best):
                best = boxes
        out = []
        for lo, sz in best:
            o = self.lo + lo * cell
            out.append((o, sz * cell, b))
        return out

    # ------------------------------------------------------------------ lighting field
    def _prepare_light(self):
        d = self.occ.astype(np.float32)
        self.d1 = ndimage.gaussian_filter(d, 2.2)    # for normals
        self.d2 = ndimage.gaussian_filter(d, 5.0)    # for ambient occlusion
        self.gx, self.gy, self.gz = np.gradient(self.d1)

    def _idx(self, p):
        return tuple(np.clip(((np.asarray(p) - self.lo) / FINE).astype(int), 0, np.array(self.n) - 1))

    def shade_point(self, p, nrm, mat):
        i = self._idx(p)
        g = -np.array([self.gx[i], self.gy[i], self.gz[i]])
        gl = np.linalg.norm(g)
        sn = g / gl if gl > 1e-5 else nrm
        n = sn * 1.0 + nrm * 0.45
        n /= np.linalg.norm(n)
        diff = max(0.0, float(n @ self.light_dir))
        wrap = 0.5 + 0.5 * float(n @ self.light_dir)
        lit = 0.55 * diff + 0.45 * wrap
        hemi = 0.5 + 0.5 * n[1]
        lit = 0.75 * lit + 0.25 * hemi
        po = p + nrm * 1.2
        d2 = self.d2[self._idx(po)]
        ao = 1.0 - np.clip((d2 - 0.12) * 2.1, 0.0, 0.62)
        lit = lit * ao
        lit = float(np.clip((lit - 0.12) * 1.25, 0.0, 1.0))
        return lit

    # ------------------------------------------------------------------ build
    def build(self, tex_w=512, shade_comp=0.85, name=None):
        self._prepare_light()
        boxes = []
        for b in range(len(self.bones)):
            boxes += self._boxes_for(b)
        faces_all = []     # (box idx, face name, texel w, texel h)
        for bi, (o, s, b) in enumerate(boxes):
            cell = self.bones[b]["cell"]
            n = np.rint(s / cell).astype(int)
            for fn, (ax, sgn) in {"north": (2, -1), "south": (2, 1), "east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1)}.items():
                if not self._face_visible(o, s, ax, sgn):
                    continue
                if fn in ("north", "south"):
                    w, h = n[0], n[1]
                elif fn in ("east", "west"):
                    w, h = n[2], n[1]
                else:
                    w, h = n[0], n[2]
                faces_all.append((bi, fn, int(w), int(h)))
        # shelf pack
        order = sorted(range(len(faces_all)), key=lambda i: -faces_all[i][3])
        pos = {}
        x = y = rowh = 0
        for i in order:
            _, _, w, h = faces_all[i]
            if x + w + 1 > tex_w:
                x = 0
                y += rowh + 1
                rowh = 0
            pos[i] = (x, y)
            x += w + 1
            rowh = max(rowh, h)
        th = y + rowh + 1
        th = (th + 3) // 4 * 4
        tex = np.zeros((th, tex_w, 4), np.uint8)
        glow = np.zeros((th, tex_w, 4), np.uint8)
        # per-bone cubes
        cubes = {b: [] for b in range(len(self.bones))}
        for bi, (o, s, b) in enumerate(boxes):
            cubes[b].append(dict(origin=[float(v) for v in o], size=[float(v) for v in s], uv={}))
        counts = {}
        for fi, (bi, fn, w, h) in enumerate(faces_all):
            o, s, b = boxes[bi]
            cell = self.bones[b]["cell"]
            u0, v0 = pos[fi]
            self._paint_face(tex, glow, o, s, b, fn, w, h, u0, v0, cell, shade_comp)
            ci = sum(1 for k in range(bi) if boxes[k][2] == b)
            cubes[b][ci]["uv"][fn] = {"uv": [u0 / SX, v0 / SX], "uv_size": [w / SX, h / SX]}
        geo_bones = []
        for b, bd in enumerate(self.bones):
            gb = {"name": bd["name"], "pivot": bd["pivot"]}
            if bd["parent"]:
                gb["parent"] = bd["parent"]
            if bd["name"] == "root":
                gb["rotation"] = [0, 180, 0]
            if cubes[b]:
                gb["cubes"] = cubes[b]
            geo_bones.append(gb)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": tex_w // SX, "texture_height": th // SX,
                            "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
            "bones": geo_bones}]}
        self.stats = dict(boxes=len(boxes), faces=len(faces_all), tex=(tex_w, th))
        return geo, tex, glow

    def _face_visible(self, o, s, ax, sgn):
        lo = np.rint((o - self.lo) / FINE).astype(int)
        hi = lo + np.rint(s / FINE).astype(int)
        if sgn > 0:
            plane = hi[ax]
        else:
            plane = lo[ax] - 1
        if plane < 0 or plane >= self.n[ax]:
            return True
        sl = [slice(lo[i], hi[i]) for i in range(3)]
        sl[ax] = slice(plane, plane + 1)
        return not self.occ[tuple(sl)].all()

    def _paint_face(self, tex, glow, o, s, b, fn, w, h, u0, v0, cell, comp):
        x0, y0, z0 = o
        x1, y1, z1 = o + s
        nrm = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}[fn]
        nrm = np.array(nrm, float)
        bname = self.bones[b]["name"]
        for bb in range(h):
            for a in range(w):
                ta, tb = (a + 0.5) * cell, (bb + 0.5) * cell
                if fn == "north":
                    p = (x1 - ta, y1 - tb, z0)
                elif fn == "south":
                    p = (x0 + ta, y1 - tb, z1)
                elif fn == "east":
                    p = (x1, y1 - tb, z1 - ta)
                elif fn == "west":
                    p = (x0, y1 - tb, z0 + ta)
                elif fn == "up":
                    p = (x0 + ta, y1, z0 + tb)
                else:
                    p = (x0 + ta, y0, z0 + tb)
                p = np.array(p, float)
                inner = p - nrm * (FINE * 0.5)
                mid = int(self.mat[self._idx(inner)])
                m = self.mats[mid]
                rgb = None
                gl = False
                if self.paint is not None:
                    rgb, gl = self.paint(self, p, nrm, m, bname)
                if rgb is None:
                    rgb = m["albedo"]
                    gl = False
                if gl:
                    col = np.array(rgb, float)
                    tex[v0 + bb, u0 + a] = [*clamp8(col), 255]
                    glow[v0 + bb, u0 + a] = [*clamp8(col), 255]
                    continue
                lit = self.shade_point(p, nrm, m)
                mat_sh = m["shadow"] * (np.array(rgb, float) / np.maximum(m["albedo"], 1.0))
                col = mat_sh + (np.array(rgb, float) * m["light"] - mat_sh) * lit
                col = col / (MC_SHADE[fn] ** comp)
                tex[v0 + bb, u0 + a] = [*clamp8(col), 255]


def save(model, out_dir, name, geo, tex, glow):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{name}.geo.json").write_text(json.dumps(geo, indent=1))
    Image.fromarray(tex).save(out / f"{name}.png")
    Image.fromarray(glow).save(out / f"{name}_glowmask.png")


def faces_of(geo, tex, glow):
    return voxel.load_geo(geo, tex, glowmask=glow)
