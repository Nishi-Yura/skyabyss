"""Occupancy grid of a hpkit Model's boxes (0.5-unit cells) for placing trim on the real surface (hp_titan_body.py)."""
import numpy as np

Q = 0.25


class Occ:
    def __init__(self, geo_or_model, lo, hi, q=Q):
        self.q = q
        self.lo = np.array(lo, float)
        self.n = np.ceil((np.array(hi, float) - self.lo) / q).astype(int)
        self.g = np.zeros(self.n, bool)
        g = geo_or_model.g if hasattr(geo_or_model, "g") else geo_or_model["minecraft:geometry"][0]
        for b in g["bones"]:
            for c in b.get("cubes", []):
                self.fill(c["origin"], c["size"])

    def idx(self, p):
        return np.floor((np.array(p, float) - self.lo) / self.q + 1e-6).astype(int)

    def fill(self, o, s, val=True):
        a = self.idx(o)
        b = np.ceil((np.array(o, float) + s - self.lo) / self.q - 1e-6).astype(int)
        a = np.maximum(a, 0)
        b = np.minimum(b, self.n)
        if (b > a).all():
            self.g[a[0]:b[0], a[1]:b[1], a[2]:b[2]] = val

    def solid(self, p):
        i = self.idx(p)
        if ((i < 0) | (i >= self.n)).any():
            return False
        return bool(self.g[tuple(i)])

    def free_box(self, o, s):
        a = self.idx(o)
        b = np.ceil((np.array(o, float) + s - self.lo) / self.q - 1e-6).astype(int)
        if (a < 0).any() or (b > self.n).any():
            return True
        return not self.g[a[0]:b[0], a[1]:b[1], a[2]:b[2]].any()

    def surface(self, axis, sign, p):
        """coordinate (along `axis`) of the outer face of the first solid cell met walking inward from outside along -sign.
        p: 3 coordinates (the value at `axis` is ignored). None if no hit."""
        p = np.array(p, float)
        ax = axis
        n = self.n[ax]
        i = self.idx(p)
        rng = range(n - 1, -1, -1) if sign > 0 else range(n)
        for k in rng:
            i[ax] = k
            if ((i < 0) | (i >= self.n)).any():
                return None
            if self.g[tuple(i)]:
                return self.lo[ax] + (k + 1) * self.q if sign > 0 else self.lo[ax] + k * self.q
        return None


class Colors:
    """texture colour of the outer face of the box that owns the surface at a point (to match trim colours to the neighbours)"""

    def __init__(self, model):
        self.m = model
        o, s, b, ci = [], [], [], []
        for bone in model.g["bones"]:
            for i, c in enumerate(bone.get("cubes", [])):
                o.append(c["origin"])
                s.append(c["size"])
                b.append(bone["name"])
                ci.append(i)
        self.o, self.s, self.b, self.ci = np.array(o, float), np.array(s, float), b, ci

    def at(self, p, d, tol=1e-3):
        """colour of face d (north/south/east/west/up/down) of the box that has that face plane at p (3 coords)"""
        import hpkit
        ax, sg = hpkit.NORMAL[d]
        p = np.array(p, float)
        plane = self.o[:, ax] + (self.s[:, ax] if sg > 0 else 0)
        ok = np.abs(plane - p[ax]) < tol
        for a in range(3):
            if a != ax:
                ok &= (self.o[:, a] - tol <= p[a]) & (p[a] <= self.o[:, a] + self.s[:, a] + tol)
        idx = np.flatnonzero(ok)
        for k in idx:
            c = self.m.bone(self.b[k])["cubes"][self.ci[k]]
            f = c["uv"].get(d)
            if f is None:
                continue
            u, v = f["uv"]
            w, h = f["uv_size"]
            if isinstance(u, str):
                continue
            # position inside the face -> texel (approximate: centre of the face texel nearest)
            fo, eu, ev = (np.array(a, float) for a in hpkit.face_frame(c["origin"], c["size"], d))
            def frac(vec):
                n = float(vec @ vec)
                return float((p - fo) @ vec) / n if n > 0 else 0.0
            a_, b_ = min(max(frac(eu), 0), 0.999), min(max(frac(ev), 0), 0.999)
            x = u + a_ * w
            y = (v + (1 - b_) * h) if d != "down" else (v + b_ * h)
            if h < 0 or w < 0:
                pass
            return tuple(int(t) for t in self.m.tex[int(min(max(y, 0), self.m.tex.shape[0] - 1)), int(min(max(x, 0), self.m.tex.shape[1] - 1)), :3])
        return None
