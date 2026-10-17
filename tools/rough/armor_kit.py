"""Armor kit v2 (docs/design/16_armor_design.md): build GeckoLib armor from rotated plates, with painted materials.

The v1 armor was axis-aligned boxes hugging the player's body.  This kit adds what the rules in 16 need:
  * oriented plates (cube rotation) -> tilted, fanned and flared pieces, so the silhouette is not the player's box
  * fan()      : plates around an axis, edges touching on the inside (curved shell, no overlap)
  * shingle()  : a row of tilted plates stepped down a surface (lamellae, scale mail, feathers)
  * a material painter (procedural: rock, bone, feather, cracked ember plate, nacre ...), with baked edge/AO only
    (the game already shades by face direction, so no directional light is baked in)
  * exact overlap check for oriented boxes (separating axis test), hidden-face removal for axis-aligned boxes
  * a mannequin (vanilla Steve box model) and a posed renderer for the comparison sheets

Coordinates are the vanilla humanoid's (1 unit = 1/16 block, y up, the front of the player is -z, the right arm is -x).
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import voxel  # noqa: E402

PIVOT = {
    "armorHead": (0, 24, 0), "armorBody": (0, 24, 0),
    "armorRightArm": (-5, 22, 0), "armorLeftArm": (5, 22, 0),
    "armorRightLeg": (-1.9, 12, 0), "armorLeftLeg": (1.9, 12, 0),
    "armorRightBoot": (-1.9, 12, 0), "armorLeftBoot": (1.9, 12, 0),
}
PIECE_BONES = {  # item slot -> bones
    "helmet": ["armorHead"],
    "chestplate": ["armorBody", "armorRightArm", "armorLeftArm"],
    "leggings": ["armorRightLeg", "armorLeftLeg"],
    "boots": ["armorRightBoot", "armorLeftBoot"],
}
FACES = ("north", "south", "east", "west", "up", "down")
K = 4  # texels per model unit


def rot_matrix(r):
    return voxel._rot(*r)


def euler_of(R):
    """inverse of rot_matrix (R = Rz Ry Rx), degrees"""
    ry = -math.asin(max(-1.0, min(1.0, R[2, 0])))
    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(R[2, 1], R[2, 2])
        rz = math.atan2(R[1, 0], R[0, 0])
    else:
        rx = math.atan2(-R[1, 2], R[1, 1])
        rz = 0.0
    return tuple(round(math.degrees(v), 4) for v in (rx, ry, rz))


class Group:
    """A local frame (origin + rotation) inside a bone: pieces added here keep their contact when the frame is tilted."""

    def __init__(self, armor, bone, origin, rot=(0, 0, 0)):
        self.armor, self.bone = armor, bone
        self.p = np.array(origin, float)
        self.R = rot_matrix(rot)

    def slab(self, c, s, mat, rot=(0, 0, 0), hide=()):
        wc = self.p + self.R @ np.array(c, float)
        Rw = self.R @ rot_matrix(rot)
        self.armor.slab(self.bone, wc, s, mat, rot=euler_of(Rw), hide=hide)

    def box(self, lo, hi, mat, hide=()):
        lo, hi = np.array(lo, float), np.array(hi, float)
        self.slab((lo + hi) / 2, hi - lo, mat, hide=hide)

    def point(self, c):
        return self.p + self.R @ np.array(c, float)


# ------------------------------------------------------------------ materials
def _ramp(stops):
    return np.array(stops, float)


class Mat:
    """A painted material.  ramp: 5 RGB stops dark->light.  pattern picks the surface detail."""

    def __init__(self, ramp, pattern="plain", glow=False, edge=0.5, ao=0.35, grain=0.12, crack=None, accent=None,
                 freq=1.0, contrast=1.0, panel=0, rivet=None):
        self.ramp = _ramp(ramp)
        self.pattern = pattern
        self.glow = glow
        self.edge = edge
        self.ao = ao
        self.grain = grain
        self.crack = crack  # RGB of the seam colour (glowing seams) or None
        self.accent = accent
        self.freq = freq
        self.contrast = contrast
        self.panel = panel  # inset (texels) of a chiselled panel border on big faces
        self.rivet = rivet  # RGB of corner rivets on panels


def _noise(rng, h, w, cells):
    """Smooth value noise in 0..1 (h, w) at the given number of cells along the longer side."""
    cy = max(2, int(round(cells * h / max(h, w))) + 1)
    cx = max(2, int(round(cells * w / max(h, w))) + 1)
    g = rng.random((cy, cx)).astype(np.float32)
    im = Image.fromarray(g, "F").resize((max(w, 1), max(h, 1)), Image.BICUBIC)
    return np.clip(np.array(im), 0, 1)


def _fbm(rng, h, w, base=3, octaves=3):
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * _noise(rng, h, w, base * (2 ** o))
        tot += amp
        amp *= 0.5
    return out / tot


def _sample_ramp(ramp, v):
    v = np.clip(v, 0, 1) * (len(ramp) - 1)
    i = np.minimum(v.astype(int), len(ramp) - 2)
    f = (v - i)[..., None]
    return ramp[i] * (1 - f) + ramp[i + 1] * f


def paint_face(mat: Mat, face: str, w: int, h: int, rng, u_len=None):
    """RGB float (h, w, 3) and a bool glow mask (h, w)."""
    glow = np.zeros((h, w), bool)
    fb = _fbm(rng, h, w, base=max(2, int(3 * mat.freq)))
    p = mat.pattern
    yy = np.arange(h)[:, None] * np.ones((1, w))
    xx = np.ones((h, 1)) * np.arange(w)[None, :]
    v = 0.15 + 0.7 * fb
    if p == "rock":
        v = 0.12 + 0.62 * fb + 0.22 * (rng.random((h, w)) < mat.grain) * rng.random((h, w))
        ridge = np.abs(_fbm(rng, h, w, base=4, octaves=2) - 0.5)
        v = np.where(ridge < 0.018, v * 0.45, v)  # hairline cracks
    elif p == "striate":  # growth lines along the long side (bone, baleen, horn)
        ph = (yy / max(h, 1)) * (6 * mat.freq) + fb * 2.2
        v = 0.5 + 0.28 * np.sin(ph * math.pi * 2) + 0.2 * (fb - 0.5)
    elif p == "ribbed":  # ridges across the plate (shell)
        ph = (xx / max(w, 1)) * (5 * mat.freq) + fb * 0.9
        v = 0.5 + 0.3 * np.sin(ph * math.pi * 2) + 0.15 * (fb - 0.5)
    elif p == "feather":  # central rachis and diagonal barbs
        ph = (yy + np.abs(xx - w / 2) * 0.8) / 2.2 * mat.freq
        v = 0.55 + 0.2 * np.sin(ph * math.pi * 2) + 0.18 * (fb - 0.5)
        rach = np.abs(xx - w / 2) < max(0.6, w * 0.04)
        v = np.where(rach, 0.92, v)
    elif p == "nacre":  # smooth iridescent
        v = 0.25 + 0.6 * _noise(rng, h, w, 2) + 0.15 * (yy / max(h, 1))
    elif p == "pit":  # coral: pitted
        pits = _fbm(rng, h, w, base=7, octaves=2)
        v = 0.5 + 0.35 * (fb - 0.5) - np.where(pits > 0.66, 0.28, 0.0) + np.where(pits < 0.26, 0.15, 0.0)
    elif p == "plate_cracked":  # ember: dark plate with glowing seams
        v = 0.12 + 0.5 * fb + 0.16 * (rng.random((h, w)) < mat.grain)
    elif p == "cloth":
        wv = 0.5 + 0.5 * np.sin((xx + yy * 0.0) * math.pi * 1.0)
        v = 0.4 + 0.3 * (fb - 0.5) + 0.06 * wv
    elif p == "metal":  # brushed
        v = 0.45 + 0.3 * (_noise(rng, h, w, 2) - 0.5) + 0.12 * (rng.random((h, 1)) - 0.5)
    v = (v - 0.5) * mat.contrast + 0.5
    rgb = _sample_ramp(mat.ramp, v)
    if p == "plate_cracked" and mat.crack is not None:
        r = np.abs(_fbm(rng, h, w, base=5, octaves=3) - 0.5)
        seam = r < 0.028
        rgb = np.where(seam[..., None], np.array(mat.crack, float), rgb)
        glow |= seam
    if p == "nacre" and mat.accent is not None:
        t = _noise(rng, h, w, 3)[..., None]
        rgb = rgb * (1 - 0.35 * t) + np.array(mat.accent, float) * 0.35 * t
    # baked ambient occlusion: darker toward the lower part of upright faces and toward all borders
    if face in ("north", "south", "east", "west"):
        rgb = rgb * (1 - mat.ao * (yy / max(h - 1, 1)) ** 1.5)[..., None]
    # bevel: light top/left row, dark bottom/right row (only when the face is large enough to show it)
    if min(h, w) >= 3 and mat.edge > 0:
        lit = np.zeros((h, w), bool)
        dark = np.zeros((h, w), bool)
        lit[0, :] = True
        lit[:, 0] = True
        dark[-1, :] = True
        dark[:, -1] = True
        rgb = np.where(lit[..., None], rgb * (1 + 0.45 * mat.edge) + 6, rgb)
        rgb = np.where(dark[..., None], rgb * (1 - 0.55 * mat.edge), rgb)
    if mat.panel and h >= 4 * mat.panel + 2 and w >= 4 * mat.panel + 2:
        q = mat.panel
        line = np.zeros((h, w), bool)
        line[q, q:w - q] = line[h - 1 - q, q:w - q] = True
        line[q:h - q, q] = line[q:h - q, w - 1 - q] = True
        hi = np.zeros((h, w), bool)
        hi[q + 1, q + 1:w - q - 1] = True
        hi[q + 1:h - q - 1, q + 1] = True
        rgb = np.where(line[..., None], rgb * 0.42, rgb)
        rgb = np.where(hi[..., None], rgb * 1.25 + 6, rgb)
        if mat.rivet is not None:
            for yy0, xx0 in ((q + 2, q + 2), (q + 2, w - q - 3), (h - q - 3, q + 2), (h - q - 3, w - q - 3)):
                rgb[yy0, xx0] = np.array(mat.rivet, float)
    if mat.glow:
        glow[:] = True
        rgb = np.maximum(rgb, mat.ramp[2])
    return np.clip(rgb, 0, 255), glow


# ------------------------------------------------------------------ the armor
class Cube:
    __slots__ = ("bone", "o", "s", "rot", "pivot", "mat", "hide", "tag", "faces")

    def __init__(self, bone, o, s, rot, pivot, mat, hide, tag):
        self.faces = {}
        self.bone, self.o, self.s, self.rot, self.pivot, self.mat, self.hide, self.tag = bone, np.array(o, float), np.array(s, float), rot, pivot, mat, set(hide), tag

    def center(self):
        return self.o + self.s / 2

    def R(self):
        return rot_matrix(self.rot) if self.rot else np.eye(3)

    def obb(self):
        """(center, half extents, R) in bone space (the cube rotates about its pivot)."""
        c = self.center()
        if self.rot:
            p = np.array(self.pivot, float)
            c = self.R() @ (c - p) + p
        return c, self.s / 2, self.R()


class Armor:
    def __init__(self, name, mats: dict):
        self.name = name
        self.mats = mats
        self.cubes: list[Cube] = []

    # --- primitives
    def box(self, bone, lo, hi, mat, hide=(), tag=""):
        lo, hi = np.array(lo, float), np.array(hi, float)
        self.cubes.append(Cube(bone, lo, hi - lo, None, None, mat, hide, tag))

    def slab(self, bone, c, s, mat, rot=(0, 0, 0), hide=(), tag=""):
        """Plate of size s centred at c, rotated about its own centre."""
        c, s = np.array(c, float), np.array(s, float)
        self.cubes.append(Cube(bone, c - s / 2, s, tuple(rot) if any(rot) else None, tuple(c), mat, hide, tag))

    def sym(self, fn, right, left):
        """call fn(bone, sign) for both sides; sign = -1 right (x<0), +1 left"""
        fn(right, -1)
        fn(left, +1)

    def fan(self, bone, axis, center, R, a0, a1, n, width, thick, mat, glow_every=0, rot_extra=(0, 0, 0)):
        """n tangent plates on an arc of radius R about `axis` (x|y|z) through `center`; angles in degrees measured
        in the plane perpendicular to the axis (0 = +up for x/z axes... see code).  width = size along the axis."""
        da = (a1 - a0) / n
        chord = 2 * R * math.tan(math.radians(abs(da)) / 2)
        for i in range(n):
            a = math.radians(a0 + da * (i + 0.5))
            if axis == "z":    # arc in the xy plane, 0 deg = +y, positive toward +x
                dirn = np.array([math.sin(a), math.cos(a), 0.0])
                size = (chord, thick, width)
                r = (0, 0, -math.degrees(a))
            elif axis == "x":  # arc in the yz plane, 0 deg = +y, positive toward +z
                dirn = np.array([0.0, math.cos(a), math.sin(a)])
                size = (width, thick, chord)
                r = (math.degrees(a), 0, 0)
            else:              # axis y: arc in the xz plane, 0 deg = +x, positive toward +z
                dirn = np.array([math.cos(a), 0.0, math.sin(a)])
                size = (thick, width, chord)
                r = (0, -math.degrees(a), 0)
            c = np.array(center, float) + dirn * (R + thick / 2)
            self.slab(bone, c, size, mat, rot=tuple(np.array(r) + np.array(rot_extra)))

    def horn(self, bone, base, plane, a0, bend, n, length, w0, w1, depth, mat, d1=None):
        """A curved spike: n touching segments from `base`; plane 'xy' (dir=(sin a, cos a, 0), a from +y toward +x)
        or 'yz' (dir=(0, cos a, sin a)); the angle changes by `bend` degrees per segment; width w0 -> w1 (x for yz, z-depth `depth`)."""
        pos = np.array(base, float)
        for i in range(n):
            t = i / max(n - 1, 1)
            w = w0 + (w1 - w0) * t
            dp = depth if d1 is None else depth + (d1 - depth) * t
            L = length / n
            a = math.radians(a0 + bend * i)
            if plane == "xy":
                dirn = np.array([math.sin(a), math.cos(a), 0.0])
                size, r = (w, L, dp), (0, 0, -math.degrees(a))
            else:
                dirn = np.array([0.0, math.cos(a), math.sin(a)])
                size, r = (dp, L, w), (math.degrees(a), 0, 0)
            self.slab(bone, pos + dirn * L / 2, size, mat, rot=r)
            pos = pos + dirn * L

    def spike(self, bone, origin, rot, n, length, w0, w1, d0, d1, mat, bend=(0, 0, 0)):
        """A tapering spike/horn of n touching segments along local +y of a frame at `origin` tilted by `rot`;
        every segment turns by `bend` (degrees, rx ry rz) relative to the one before.  w = x width, d = z depth."""
        R = rot_matrix(rot)
        pos = np.array(origin, float)
        L = length / n
        for i in range(n):
            t = i / max(n - 1, 1)
            size = (w0 + (w1 - w0) * t, L, d0 + (d1 - d0) * t)
            c = pos + R @ np.array([0, L / 2, 0])
            self.slab(bone, c, size, mat, rot=euler_of(R))
            pos = pos + R @ np.array([0, L, 0])
            R = R @ rot_matrix(bend)
        return pos

    def group(self, bone, origin, rot=(0, 0, 0)):
        return Group(self, bone, origin, rot)

    # --- mirrored copy of a right-side bone into the left bone
    def mirror_bone(self, src, dst):
        for c in [c for c in self.cubes if c.bone == src]:
            o, s = c.o.copy(), c.s.copy()
            no = np.array([-(o[0] + s[0]), o[1], o[2]])
            rot = None
            piv = None
            if c.rot:
                rot = (c.rot[0], -c.rot[1], -c.rot[2])
                piv = (-c.pivot[0], c.pivot[1], c.pivot[2])
            self.cubes.append(Cube(dst, no, s, rot, piv, c.mat, c.hide, c.tag))

    # --- checks
    def overlaps(self, tol=2e-3, rtol=0.15):
        cs = self.cubes
        obbs = [c.obb() for c in cs]
        bad = []
        for i in range(len(cs)):
            for j in range(i + 1, len(cs)):
                dep = _obb_overlap(obbs[i], obbs[j], rtol if (cs[i].rot or cs[j].rot) else tol)
                if dep:
                    bad.append(f"{cs[i].bone}[{i}]{np.round(obbs[i][0],1).tolist()} x {cs[j].bone}[{j}]{np.round(obbs[j][0],1).tolist()} depth {dep:.2f}")
        return bad

    def culled_hide(self):
        """faces of axis-aligned cubes that sit flush against / inside another axis-aligned cube are not drawn"""
        eps = 1e-4
        aab = [c for c in self.cubes if not c.rot]
        for c in aab:
            for fi, f in enumerate(FACES):
                if f in c.hide:
                    continue
                ax, sg = {"north": (2, -1), "south": (2, 1), "east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1)}[f]
                plane = c.o[ax] + (c.s[ax] if sg > 0 else 0)
                oth = [a for a in range(3) if a != ax]
                for d in aab:
                    if d is c:
                        continue
                    # d must cover the face rectangle in the other two axes and straddle/contact the plane from inside
                    if not all(d.o[a] <= c.o[a] + eps and d.o[a] + d.s[a] >= c.o[a] + c.s[a] - eps for a in oth):
                        continue
                    lo, hi = d.o[ax], d.o[ax] + d.s[ax]
                    if sg > 0 and lo <= plane + eps and hi > plane + eps:
                        c.hide.add(f)
                        break
                    if sg < 0 and hi >= plane - eps and lo < plane - eps:
                        c.hide.add(f)
                        break

    # --- output
    def build(self, size=None, seed=1, chamfer=None):
        """(geo dict, texture RGBA uint8, glowmask RGBA uint8)"""
        self.culled_hide()
        rects = []  # (w, h, key)
        for ci, c in enumerate(self.cubes):
            sx, sy, sz = c.s
            ext = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}
            c_faces = {}
            for f in FACES:
                if f in c.hide:
                    continue
                a, b = ext[f]
                w, h = max(1, round(a * K)), max(1, round(b * K))
                c_faces[f] = (w, h)
                rects.append((w, h, (ci, f)))
            c.faces = c_faces  # type: ignore[attr-defined]
        tsz, pos = _pack(rects, size)
        tex = np.zeros((tsz, tsz, 4), np.uint8)
        glow = np.zeros((tsz, tsz, 4), np.uint8)
        bones = {}
        nfaces = 0
        for ci, c in enumerate(self.cubes):
            rng = np.random.default_rng(seed * 100003 + ci)
            m = self.mats[c.mat]
            uv = {}
            for f, (w, h) in c.faces.items():  # type: ignore[attr-defined]
                x, y = pos[(ci, f)]
                rgb, gl = paint_face(m, f, w, h, rng)
                tex[y:y + h, x:x + w, :3] = rgb.astype(np.uint8)
                tex[y:y + h, x:x + w, 3] = 255
                if gl.any():
                    gm = gl[..., None]
                    glow[y:y + h, x:x + w, :3] = np.where(gm, rgb.astype(np.uint8), 0)
                    glow[y:y + h, x:x + w, 3] = np.where(gl, 255, 0)
                uv[f] = {"uv": [x, y], "uv_size": [w, h]}
                nfaces += 1
            cd = {"origin": [_r(v) for v in c.o], "size": [_r(v) for v in c.s], "uv": uv}
            if c.rot:
                cd["pivot"] = [_r(v) for v in c.pivot]
                cd["rotation"] = [_r(v) for v in c.rot]
            bones.setdefault(c.bone, []).append(cd)
        gb = []
        for bn in PIVOT:
            gb.append({"name": bn, "pivot": list(PIVOT[bn]), "cubes": bones.get(bn, [])})
        if chamfer:
            import hpkit
            step, R, mind = chamfer
            for b in gb:
                nc = []
                for cd in b["cubes"]:
                    nc += hpkit.round_cube(cd, step=step, R=R, min_dim=mind)
                b["cubes"] = nc
            nfaces = sum(len(cd["uv"]) for b in gb for cd in b["cubes"])
        self.n_out = (sum(len(b["cubes"]) for b in gb), nfaces)
        geo = {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{self.name}", "texture_width": tsz, "texture_height": tsz,
                    "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0],
                },
                "bones": gb,
            }],
        }
        return geo, tex, glow

    def stats(self, geo=None):
        return self.n_out


def _r(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def _pack(rects, size=None):
    for sz in ([size] if size else (256, 512, 1024, 2048)):
        x = y = shelf = 0
        pos = {}
        ok = True
        for w, h, key in sorted(rects, key=lambda r: (-r[1], -r[0])):
            if w > sz:
                ok = False
                break
            if x + w > sz:
                x, y, shelf = 0, y + shelf + 1, 0
            if y + h > sz:
                ok = False
                break
            pos[key] = (x, y)
            x += w + 1
            shelf = max(shelf, h)
        if ok:
            return sz, pos
    raise SystemExit("texture does not fit")


def _obb_overlap(a, b, tol):
    """True when two oriented boxes share volume (penetration > tol on every separating axis)."""
    ca, ha, Ra = a
    cb, hb, Rb = b
    d = cb - ca
    if np.linalg.norm(d) > np.linalg.norm(ha) + np.linalg.norm(hb) + 1:
        return 0
    axes = [Ra[:, i] for i in range(3)] + [Rb[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            x = np.cross(Ra[:, i], Rb[:, j])
            n = np.linalg.norm(x)
            if n > 1e-6:
                axes.append(x / n)
    depth = 1e9
    for ax in axes:
        ra = sum(ha[i] * abs(Ra[:, i] @ ax) for i in range(3))
        rb = sum(hb[i] * abs(Rb[:, i] @ ax) for i in range(3))
        pen = ra + rb - abs(d @ ax)
        if pen <= tol:
            return 0
        depth = min(depth, pen)
    return depth


# ------------------------------------------------------------------ mannequin + render
SKIN = (196, 150, 120)
CLOTH = (70, 92, 150)
TROUSER = (60, 60, 110)


def mannequin():
    """Vanilla Steve boxes with plain colours (geo dict + texture) for the comparison sheets."""
    tex = np.zeros((8, 8, 4), np.uint8)
    cols = {"skin": SKIN, "shirt": CLOTH, "trouser": TROUSER}
    for i, k in enumerate(cols):
        tex[0, i] = (*cols[k], 255)
    idx = {"skin": 0, "shirt": 1, "trouser": 2}

    def cube(lo, hi, k):
        uvf = {f: {"uv": [idx[k] + 0.5, 0.5], "uv_size": [0.01, 0.01]} for f in FACES}
        return {"origin": list(lo), "size": [hi[i] - lo[i] for i in range(3)], "uv": uvf}

    bones = [
        {"name": "armorHead", "pivot": [0, 24, 0], "cubes": [cube((-4, 24, -4), (4, 32, 4), "skin")]},
        {"name": "armorBody", "pivot": [0, 24, 0], "cubes": [cube((-4, 12, -2), (4, 24, 2), "shirt")]},
        {"name": "armorRightArm", "pivot": [-5, 22, 0], "cubes": [cube((-8, 12, -2), (-4, 24, 2), "shirt")]},
        {"name": "armorLeftArm", "pivot": [5, 22, 0], "cubes": [cube((4, 12, -2), (8, 24, 2), "shirt")]},
        {"name": "armorRightLeg", "pivot": [-1.9, 12, 0], "cubes": [cube((-4, 0, -2), (0, 12, 2), "trouser")]},
        {"name": "armorLeftLeg", "pivot": [1.9, 12, 0], "cubes": [cube((0, 0, -2), (4, 12, 2), "trouser")]},
    ]
    geo = {"minecraft:geometry": [{"description": {"texture_width": 8, "texture_height": 8}, "bones": bones}]}
    return geo, tex


POSES = {
    "stand": {},
    "arms_up": {"armorRightArm": (-150, 0, 0), "armorLeftArm": (-150, 0, 0)},
    "walk": {"armorRightArm": (28, 0, 0), "armorLeftArm": (-28, 0, 0), "armorRightLeg": (-32, 0, 0), "armorLeftLeg": (32, 0, 0),
             "armorRightBoot": (-32, 0, 0), "armorLeftBoot": (32, 0, 0)},
    "swing": {"armorRightArm": (-70, 0, 25), "armorLeftArm": (20, 0, -10)},
}


def faces_of(geo, tex, glow, pose=None):
    gm = glow if (glow is not None and (glow[..., 3] > 0).any()) else None
    return voxel.load_geo(geo, tex, glowmask=gm, pose=pose)


def render_set(armor_geo, tex, glow, pose="stand", yaw=180, pitch=10, size=(360, 460), mann=True):
    from PIL import ImageDraw  # noqa: F401
    pz = POSES[pose] if isinstance(pose, str) else pose
    fs = faces_of(armor_geo, tex, glow, pz)
    if mann:
        mg, mt = mannequin()
        fs = fs + voxel.load_geo(mg, mt, pose=pz)
    im = voxel.render(fs, yaw=-yaw, pitch=pitch, size=size)
    return im.transpose(Image.FLIP_LEFT_RIGHT)
