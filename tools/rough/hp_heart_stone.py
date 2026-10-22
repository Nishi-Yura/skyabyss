"""High-poly re-sculpt of the stone whale heart (岩鯨の心臓, final boss; docs/design/15_high_poly.md).

Reads tools/sources/heart/phaseN.* (the baked 0.5-unit boss_v4 option A, kept untouched as the input), re-sculpts it at
0.25 units with tools/rough/hp_heart_kit.py and writes tools/sources/heart_hp/phaseN.* (what tools/install/install_heart_v4.py installs).

The shell is 8 plates + 8 glowing seams, each its own Y-rotated bone (bone-local grids). Plates are only ever trimmed or
re-filled inside their own original volume, so rotated neighbours can never collide. Unrotated bones share the world grid;
anything they grow is kept out of the rotated parts' (dilated) volume (`clear_of_parts`).

Variants (compared on phase 1):
  A  rounded : the plates trimmed to one true sphere (no more stair-step tiers), crown and heart rounded at 0.25, ambient
               occlusion, painted stone grain / mortar courses / moss / hot-to-white seams. Shape only, nothing new carved.
  B  carved  : A + carved strata courses on the seen plates, chipped plate corners, raised moss tufts, hairline cracks
               (dark when sealed, glowing once the shell breaks), banded seams; the heart gets muscle (fibre bands,
               ventricle ridges, the interventricular groove, raised veins).
  C  ruin    : B + heavier damage at every phase: glowing fissures carved into the seen plates already at p1, bigger
               bites, weathered relief on the two front plates (over the face budget at p1; kept as the "how far" option).

Phase story (B/C): p1 sealed shell (dark hairlines, light only in the seams) -> p2 cracked shell, light leaking through
glowing fissures, the muscle visible through the open back -> p3 broken bowl with glowing fractures, the red heart
hanging by its chains with ridged ventricles and veins.

python -B tools/rough/hp_heart_stone.py rough      -> docs/review/high_poly_roughs/stone_whale_heart_hp.png (phase 1)
python -B tools/rough/hp_heart_stone.py build B    -> tools/sources/heart_hp/phase1..3 (phase 2 built from its own source)
python -B tools/rough/hp_heart_stone.py phases B   -> scratch sheet of all 3 phases of one variant (prints per-bone faces)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import hp_heart_kit as K  # noqa: E402

SRC = ROOT / "tools/sources/heart"
OUT = ROOT / "tools/sources/heart_hp"
PIV = np.array([0.0, 12.0, 0.0])      # every rotated bone turns about this point
R_SHELL = 11.6                       # the plates' outer sphere (centre PIV)

MORTAR = (58, 62, 66)
MOSS = (88, 120, 52)
MOSS_HI = (118, 150, 64)
CRACK_DARK = (34, 32, 34)
HOT = (255, 112, 46)
HOT_DEEP = (214, 70, 34)
WHITE_HOT = (255, 236, 176)
VEIN = (92, 18, 34)
VEIN_HI = (150, 40, 52)
FAT = (214, 186, 120)

# damage level per (variant, phase): cracks (count, width, glow), bites (count, radius)
DAMAGE = {
    ("B", 1): dict(cracks=3, cw=1, cglow=False, bites=2, br=0.9),
    ("B", 2): dict(cracks=2, cw=1, cglow=True, bites=3, br=1.1),
    ("B", 3): dict(cracks=2, cw=1, cglow=True, bites=3, br=1.2),
    ("C", 1): dict(cracks=2, cw=1, cglow=True, bites=4, br=1.3),
    ("C", 2): dict(cracks=3, cw=1, cglow=True, bites=5, br=1.5),
    ("C", 3): dict(cracks=3, cw=1, cglow=True, bites=5, br=1.5),
}


# ------------------------------------------------------------------ helpers the kit does not have
def front_of(v):
    """world direction (x, z) the bone-local +z of a rotated part points to."""
    d = v.R @ np.array([0.0, 0.0, 1.0])
    return d


def seen(v):
    """how much a rotated part faces the usual views (front +z and the sides): 1 front ... 0 back."""
    d = front_of(v)
    return float(np.clip(0.5 + 0.6 * d[2], 0, 1))


def local_r(v):
    X, Y, Z = v.coords()
    return np.sqrt(X ** 2 + (Y - PIV[1]) ** 2 + Z ** 2)


def sphere_trim(v, R, xq=0.5, yq=0.25):
    """carve the cells of a part outside the sphere of radius R about the pivot (trim only, never grows). Glow cells
    are trimmed too (the cells under them are glow as well, so the light stays flush with the new surface).
    xq / yq: the steps of the new surface across / up the plate (0.25 only on the seen plates)."""
    X, Y, Z = v.coords()
    Xq = np.floor(X / xq) * xq + xq / 2
    Yq = np.floor((Y - PIV[1]) / yq) * yq + yq / 2
    r = np.sqrt(Xq ** 2 + Yq ** 2 + Z ** 2)
    m = np.broadcast_to(r > R, v.n) & v.occ
    v.lab[m] = 0


def clear_of_parts(s, margin=1.5):
    """world-grid mask of the cells that are at least `margin` cells away from every rotated part."""
    w = s.world
    X, Y, Z = w.full_coords()
    P = np.stack([X, Y, Z], -1).reshape(-1, 3)
    hit = np.zeros(len(P), bool)
    for v in s.parts.values():
        occ = ndi.binary_dilation(v.occ, structure=K.ball(margin)) if margin > 0 else v.occ
        loc = (P - PIV) @ v.R + PIV          # R.T @ (p - piv) for every row
        idx = np.floor((loc - v.lo) / K.H).astype(int)
        ok = np.all((idx >= 0) & (idx < np.array(v.n)), axis=1)
        h = np.zeros(len(P), bool)
        h[ok] = occ[idx[ok, 0], idx[ok, 1], idx[ok, 2]]
        hit |= h
    return ~hit.reshape(w.n)


def nid(name):
    """a stable small number per bone name (str hash() changes per run)"""
    return sum(ord(c) * (i + 1) for i, c in enumerate(name)) % 97


def mask_of(v, names):
    ids = [v.names[n] for n in names if n in v.names]
    return np.isin(v.lab, ids)


def outward(v, occ=None):
    """per-cell world-space outward normal of the occupancy (blurred gradient) -> (n, 3) array field (nx, ny, nz)."""
    o = (v.occ if occ is None else occ).astype(np.float32)
    d = ndi.gaussian_filter(o, 1.2)
    g = -np.stack(np.gradient(d), -1)
    g = g @ v.R.T
    n = np.linalg.norm(g, axis=-1, keepdims=True)
    return g / np.maximum(n, 1e-6)


def grain(v, mask, amt=0.12, seed=1, scale=0.7):
    """stone grain: value noise on the albedo (free: paint only)."""
    n = K.smooth_noise(v, scale, seed, 3)
    m = mask & v.occ & ~v.glow
    v.col[m] = np.clip(v.col[m] * (1.0 + amt * n[m])[:, None], 0, 255)


def smooth_coarse(v, names, sigma=1.6, thr=0.5, where=None):
    """round a part on the 0.5 grid (steps of 0.5 = one old texel; a quarter of the faces of a 0.25 rounding).
    sigma is in 0.5 cells. Trim only: cells are removed, never added, glow cells are kept."""
    region = mask_of(v, names)
    if where is not None:
        region = region & where
    n2 = tuple(n // 2 for n in v.n)
    r = region[:n2[0] * 2, :n2[1] * 2, :n2[2] * 2].reshape(n2[0], 2, n2[1], 2, n2[2], 2).mean((1, 3, 5))
    keep = ndi.gaussian_filter(r, sigma) > thr
    up = np.zeros(v.n, bool)
    up[:n2[0] * 2, :n2[1] * 2, :n2[2] * 2] = keep.repeat(2, 0).repeat(2, 1).repeat(2, 2)
    v.lab[region & ~up & ~v.glow] = 0


def blur_albedo(v, mask, sigma=1.2):
    """soften the baked speckle of a part's albedo (inside mask, glow untouched)."""
    m = (mask & v.occ & ~v.glow)
    w = ndi.gaussian_filter(m.astype(np.float32), sigma)
    for c in range(3):
        b = ndi.gaussian_filter(v.col[..., c] * m, sigma) / np.maximum(w, 1e-4)
        v.col[..., c] = np.where(m, b, v.col[..., c])


def mix(v, mask, color, f):
    m = mask & v.occ & ~v.glow
    if m.any():
        v.col[m] = v.col[m] * (1 - f) + np.array(color, np.float32) * f
    return m


# ------------------------------------------------------------------ the shell
def plate_names(s):
    return [n for n in s.parts if n.startswith(("plate_", "bowl_")) and "seam" not in n]


def seam_names(s):
    return [n for n in list(s.parts) + list(s.world.names) if n.startswith(("seam_", "crack_", "bowl_seam_"))]


def shell_common(s, ph, variant, seed):
    """A: sphere-trim every plate, paint grain / mortar / moss."""
    for nm in plate_names(s):
        v = s.parts[nm]
        v.orig = v.occ.copy()
        if nm.startswith("plate_"):
            sphere_trim(v, R_SHELL, xq=0.5, yq=0.25 if seen(v) > 0.45 else 0.5)
        elif seen(v) > 0.45:   # bowl pieces (p3) on the seen side: a softened rim, trim only
            K.smooth(v, sigma=(0.01, 1.4, 1.4), allow_grow=False)
        X, Y, Z = v.coords()
        surf = v.surface()
        grain(v, v.occ, 0.13, seed + nid(nm))
        # mortar between the stone courses: a dark line every 1.5 units (paint only)
        fy = np.broadcast_to(((Y - 3.0) % 1.5) < 0.26, v.n)
        mix(v, surf & fy, MORTAR, 0.55)
        # moss on the upward faces, thicker low on the shell
        nrm = outward(v)
        up = nrm[..., 1] > 0.35
        nz = K.smooth_noise(v, 0.8, seed + 7, 2)
        mix(v, surf & up & (nz > 0.15), MOSS, 0.75)
        mix(v, surf & up & (nz > 0.55), MOSS_HI, 0.8)


def shell_carve(s, ph, variant, seed):
    """B/C: strata grooves (p1), chipped corners, moss tufts (p1), cracks: dark hairlines when sealed, glowing once broken."""
    dmg = DAMAGE[(variant, ph)]
    rng = np.random.default_rng(seed + ph * 13)
    for nm in plate_names(s):
        v = s.parts[nm]
        X, Y, Z = v.coords()
        sv = seen(v)
        plate = mask_of(v, [nm])
        # strata: a groove one cell deep at every second course, only on the seen plates of the sealed shell
        if ph == 1 and sv > 0.45:
            fy = np.broadcast_to(((Y - 3.0) % 3.0) < 0.26, v.n) & np.broadcast_to(np.abs(X) < 3.0, v.n)
            K.groove(v, plate, v.surface() & fy, depth=1, color=MORTAR)
        # chipped corners: spheres bitten out of the plate's outer corners (the bowl: out of its broken top rim)
        nb = dmg["bites"] if sv > 0.45 else dmg["bites"] // 3
        for k in range(nb):
            cx = rng.choice([-3.2, 3.2]) if nm.startswith("plate_") else rng.uniform(-3.0, 3.0)
            cy = rng.uniform(4.5, 19.5) if nm.startswith("plate_") else 9.0
            cell = K.nearest_surface_cell(v, plate & ~v.glow, (cx, cy, 12.0))
            if cell is None:
                continue
            c = v.lo + (cell + 0.5) * K.H
            rad = dmg["br"] * rng.uniform(0.8, 1.2)
            bite = np.broadcast_to((X - c[0]) ** 2 + (Y - c[1]) ** 2 + (Z - c[2]) ** 2 < rad ** 2, v.n)
            v.carve(bite & plate)
            fresh = ndi.binary_dilation(bite, structure=K.ball(1.2)) & v.surface() & mask_of(v, [nm])
            mix(v, fresh, (150, 156, 150), 0.45)      # fresh, paler stone inside the chip
        plate = mask_of(v, [nm])
        # moss tufts on the sealed shell: raised back inside the plate's own original volume only
        if ph == 1 and sv > 0.45:
            nrm = outward(v)
            nz = K.smooth_noise(v, 0.5, seed + 31, 1)
            tuft = v.surface() & (nrm[..., 1] > 0.5) & np.broadcast_to(nz > 0.62, v.n)
            K.raise_pattern(v, v.orig & ~v.occ, tuft, height=1, color=MOSS_HI, only=[nm], anchored=True)
        # cracks (surface walks)
        n = dmg["cracks"] if sv > 0.45 else (dmg["cracks"] + 1) // 3
        starts, dirs = [], []
        for k in range(n):
            p = (rng.uniform(-2.0, 2.0), rng.uniform(6.0, 18.0) if nm.startswith("plate_") else rng.uniform(4.5, 8.0), 12.0)
            c = K.nearest_surface_cell(v, plate & ~v.glow, p)
            if c is not None:
                starts.append(c)
                dirs.append((rng.uniform(-1, 1), rng.choice([-1, 1]) * rng.uniform(0.5, 1.0), 0.0))
        if not starts:
            continue
        paths = K.surface_walk(v, plate & ~v.glow, starts, steps=24 + 4 * ph, seed=seed + 3 + nid(nm),
                               branch=0.05, persist=0.8, width=1, dirs=dirs, jitter=0.55)
        print(f"  {nm} crack path cells: {int(paths.sum())} from {len(starts)} starts")
        halo = ndi.binary_dilation(paths, structure=K.ball(1.2)) & v.occ & ~v.glow & ~paths
        if dmg["cglow"] and variant == "C" and sv > 0.45:   # C: real fissures on the seen plates (cost faces)
            K.groove(v, plate, paths, depth=dmg["cw"], glow=True, color=HOT, only=[nm])
            mix(v, halo, HOT_DEEP, 0.25)
        elif dmg["cglow"]:                            # B: glowing lines painted flush (free)
            core = paths & v.occ
            v.paint(core, HOT, glow=True)
            v.paint(core & np.broadcast_to(K.smooth_noise(v, 0.4, seed + 77, 1) > 0.35, v.n), WHITE_HOT, glow=True)
            mix(v, halo, HOT_DEEP, 0.2)
        else:                                         # sealed: dark hairlines with a paler lip
            mix(v, paths & ~v.glow, CRACK_DARK, 0.8)
            mix(v, halo, (150, 156, 150), 0.18)


def shell_ruin(s, ph, seed):
    """C extra: weathered relief on the seen plates (cut only, coarse patches so it stays near the face budget)."""
    for nm in plate_names(s):
        v = s.parts[nm]
        if seen(v) > 0.9:
            K.relief(v, mask_of(v, [nm]), amp=0.5, scale=1.6, seed=seed + 61, octaves=1, grow=False)


# ------------------------------------------------------------------ crown, spikes, base
def crown(s, ph, variant, seed):
    w = s.world
    for nm in ("crown",):     # the crown's stepped tiers are its design: grain only (rounding them cost 400 faces for little)
        if nm in w.names:
            grain(w, mask_of(w, [nm]), 0.1, seed + 5)
    if "base" in w.names:
        grain(w, mask_of(w, ["base"]), 0.12, seed + 9)


# ------------------------------------------------------------------ the heart (muscle)
def heart(s, ph, variant, seed):
    """the muscle: rounded on the seen side, fibre bands, the interventricular groove, veins (B: raised trunks at p3)."""
    w = s.world
    if ph == 1 or "heart" not in w.names:
        return
    X, Y, Z = w.coords()
    if ph == 2:   # seen through the open back-left (plates 4 and 5 fall)
        vis = np.broadcast_to((-X * 0.7 - Z * 0.7) > 1.0, w.n)
    else:         # the front half (the core side); the back stays at 0.5
        vis = np.broadcast_to(Z > 0.0, w.n)
    smooth_coarse(w, ["heart"], sigma=1.4)                                  # the organ shape, every side, 0.5 steps
    K.smooth(w, ["heart"], sigma=1.2, where=vis & np.broadcast_to(FINE_HEART[ph](X, Y, Z), w.n), allow_grow=False)
    c = w.col
    red = mask_of(w, ["heart"]) & (c[..., 0] > 1.5 * c[..., 1])        # the muscle, not the pale band round it
    blur_albedo(w, red, 1.4)
    K.smooth(w, ["core"], sigma=1.0, allow_grow=False)
    hm = mask_of(w, ["heart"]) & ~w.glow
    # muscle fibre bands spiralling round the ventricles (paint)
    fib = np.sin((X * 0.55 + (Y - 13.0) * 0.85) * 3.2 + 0.6 * np.sin(Z * 0.8))
    fib = np.broadcast_to(fib, w.n)
    w.col[hm] = np.clip(w.col[hm] * (1.0 + 0.10 * fib[hm])[:, None], 0, 255)
    grain(w, hm, 0.06, seed + 13, 0.5)
    if variant == "A":
        return
    surf = w.surface() & hm
    # ventricle ridges: dark bands between the fibre bundles (B paints them, C carves them)
    mix(w, surf & (fib > 0.8), (110, 20, 30), 0.4)
    mix(w, surf & (fib < -0.85), (196, 70, 70), 0.25)
    if variant == "C":
        K.groove(w, vis, surf & vis & (fib > 0.9), depth=1, color=(110, 22, 30), only=["heart"])
    # interventricular groove: a curved channel from the top centre down to the apex, lined with pale fat
    cxl = 0.8 - 0.22 * (18.0 - Y)
    lane = np.broadcast_to(np.abs(X - cxl) < 0.55, w.n)
    if ph == 3:
        K.groove(w, vis & lane, w.surface() & hm & lane, depth=1, color=FAT, only=["heart"])
    else:
        mix(w, w.surface() & hm & lane, FAT, 0.6)
    # veins: surface walks from the top, painted dark; at p3 the trunks raised one cell where there is room
    hm = mask_of(w, ["heart"]) & ~w.glow
    if ph == 2:
        pts = (((-3.0, 18.0, -4.0), (-0.6, -1.0, -0.2)), ((-5.0, 16.0, -1.0), (-0.3, -1.0, 0.0)), ((-1.0, 18.0, -4.5), (0.3, -1.0, -0.2)))
    else:
        pts = (((-3.0, 20.5, 4.5), (-0.6, -1.0, 0.1)), ((-1.0, 20.5, 4.5), (-0.2, -1.0, 0.1)),
               ((1.5, 20.5, 4.5), (0.3, -1.0, 0.1)), ((3.5, 20.5, 4.5), (0.7, -1.0, 0.1)))
    starts, dirs = [], []
    for p, d in pts:
        c = K.nearest_surface_cell(w, hm & vis, p)
        if c is not None:
            starts.append(c)
            dirs.append(d)
    paths = K.surface_walk(w, hm & vis, starts, steps=40, seed=seed + 21, branch=0.08, persist=0.86, width=1, dirs=dirs, jitter=0.4)
    print(f"  heart vein path cells: {int(paths.sum())} from {len(starts)} starts")
    w.paint(paths & hm, VEIN)
    near = ndi.binary_dilation(paths, structure=K.ball(1.2)) & hm & ~paths
    mix(w, near, VEIN, 0.25)
    if ph == 3:
        trunk = K.surface_walk(w, hm & vis, starts, steps=22, seed=seed + 21, branch=0.0, persist=0.86, width=1, dirs=dirs, jitter=0.4)
        trunk &= paths
        add = K.raise_pattern(w, clear_of_parts(s, 1.5) & vis, trunk, height=1, color=VEIN_HI, only=["heart"], anchored=True)
        print(f"  raised vein cells: {int(add.sum())}")


def chains(s, ph, variant, seed):
    w = s.world
    if "chains" in w.names:
        grain(w, mask_of(w, ["chains"]), 0.08, seed + 41)
    if "heart_veins" in w.names:
        K.smooth(w, ["heart_veins"], sigma=0.9, thin_r=1.0, allow_grow=False)


# ------------------------------------------------------------------ driver
def build(variant, ph, seed=23):
    s = K.Source(SRC, ph)
    if variant == "now":
        return s
    shell_common(s, ph, variant, seed)
    crown(s, ph, variant, seed)
    heart(s, ph, variant, seed)
    chains(s, ph, variant, seed)
    if variant in ("B", "C"):
        shell_carve(s, ph, variant, seed)
    if variant == "C":
        shell_ruin(s, ph, seed)
    return s


FINE_HEART = {2: lambda X, Y, Z: (-X * 0.7 - Z * 0.7) > 2.0, 3: lambda X, Y, Z: Z > 3.0}

VIEWS = {1: (35, 24), 2: (150, 22), 3: (22, 20)}
CROPS = [(20, 10, (0.3, 0.22, 0.72, 0.64)), (35, 24, (0.18, 0.08, 0.6, 0.5))]


def phases_sheet(variant):
    import voxel
    cols = []
    for ph in (1, 2, 3):
        src = build(variant, ph)
        geo, tex, gl, st = K.finish(src)
        nb, nq = K.count(geo)
        print(f"phase {ph}: {nb} boxes / {nq} quads, overlaps {len(K.sat_check(geo))}, glow {int((gl[..., 3] > 0).sum())}")
        print("  ", dict(sorted(st["per_bone"].items(), key=lambda kv: -kv[1])))
        y, p = VIEWS[ph]
        cols.append((f"p{ph} {nb}/{nq}", [K.render_geo(geo, tex, gl, y, p, (480, 480)), K.render_geo(geo, tex, gl, 0, 8, (480, 480)),
                                         K.render_crop(geo, tex, gl, 0, 8, (0.25, 0.2, 0.75, 0.7))]))
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("heart_phases.png")
    voxel.sheet(f"stone heart {variant}", cols).save(out)
    print("wrote", out)


if __name__ == "__main__":
    if sys.argv[1] == "rough":
        K.rough_sheet("stone_whale_heart", "stone_whale_heart phase 1: now / A rounded / B carved / C ruin (0.25 re-sculpt)",
                      build, views=[VIEWS[1], (0, 8)], crops=CROPS)
    elif sys.argv[1] == "phases":
        phases_sheet(sys.argv[2])
    else:
        K.build_phases("stone_whale_heart", build, sys.argv[2], OUT)
