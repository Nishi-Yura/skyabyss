"""High-poly re-sculpt of the storm / thunder-wing heart (雷翼の心臓, assignment 3, docs/design/15_high_poly.md).

Reads tools/sources/storm_heart/phaseN.* (the baked 0.5-unit rough, kept as the input), re-sculpts it at 0.25 units with
tools/rough/hp_heart_kit.py and writes tools/sources/storm_heart_hp/phaseN.* (what tools/install/install_storm_heart.py installs).
Every bone, the bone tree and the pivots stay; the lightning web (phase 3) is never smoothed (thin, jagged on purpose).

Variants (compared on phase 1):
  A  rounded       : the heart, the core and the vessel rounded at 0.25 (front half), ambient occlusion, soft stone mottling.
  B  feathered     : A + feather lines on the wings (painted rachis, barbs, dark seams between the feathers, notched tips),
                     carved lobe seams and a painted scar network on the heart.
  C  storm-scarred : B + glowing cracks from the core and soot scorch on the heart, growing with the phase
                     (p1 a few hairlines, p2 more + charged glow sparks along the wing bones, p3 many cracks, charred fingers
                     and bones, burnt flung feathers with glowing edges).

python -B tools/rough/hp_heart_storm.py rough        -> docs/review/high_poly_roughs/storm_heart_hp.png (phase 1, now/A/B/C)
python -B tools/rough/hp_heart_storm.py build C      -> tools/sources/storm_heart_hp/phase1..3 (phase 2 built from its own source)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import hp_heart_kit as K  # noqa: E402

SRC = ROOT / "tools/sources/storm_heart"
OUT = ROOT / "tools/sources/storm_heart_hp"

GLOW = (170, 210, 255)          # the core / web colour of the source (install_storm_heart keys on the glow flag only)
SCAR = (92, 98, 124)
SOOT = (46, 46, 58)
FEATHER_SEAM = 0.45
HEART_LABELS = ["heart", "heart_vein", "core", "core_eye"]
WINGS = ["wing_l", "wing_r", "crest_l", "crest_r"]


def ids(v, names):
    return [v.names[n] for n in names if n in v.names]


def own(v, names):
    return np.isin(v.lab, ids(v, names))


def centre_y(ph):
    return 21.0 if ph == 2 else 17.0


def pivots(s):
    return {b["name"]: np.array(b["pivot"], float) for b in s.bones}


# ------------------------------------------------------------------ smoothing on the 0.5 grid (the kit's smooth works at 0.25,
# which costs ~3x the faces on a big round organ; the face budget only allows 0.25 steps on the details)
def _blocks(a):
    nx, ny, nz = a.shape
    return a.reshape(nx // 2, 2, ny // 2, 2, nz // 2, 2)


def _up(a):
    return np.repeat(np.repeat(np.repeat(a, 2, 0), 2, 1), 2, 2)


def smooth_coarse(v, labels, sigma=1.1, thr=0.5, where=None, thin_r=1.2, grow=1):
    """kit.smooth on 0.5-unit blocks: the silhouette is rounded but the steps stay 0.5 (one texel), so the box count stays low.
    sigma / thin_r / grow are in 0.5 cells. Cells outside `where` are left alone."""
    assert all(n % 2 == 0 for n in v.n)
    region = np.isin(v.lab, ids(v, labels))
    if where is not None:
        region &= where
    if not region.any():
        return
    blk = _blocks(region).mean((1, 3, 5)) >= 0.5
    d = ndi.gaussian_filter(blk.astype(np.float32), sigma)
    new = d > thr
    bulk = ndi.binary_opening(blk, structure=K.ball(thin_r))
    new |= blk & ~ndi.binary_dilation(bulk, structure=K.ball(thin_r + 0.3))
    if grow:
        new &= ndi.binary_dilation(blk, structure=K.ball(grow)) | blk
    else:
        new &= blk
    full = _up(new)
    if where is not None:
        full &= where
    keep = region & v.glow
    removed = region & ~full & ~keep
    added = full & ~region & (v.lab == 0)
    v.lab[removed] = 0
    if added.any():
        v.nearest_fill(region & ~removed, added)


def block_align(mask):
    """grow a cell mask to whole 0.5 blocks (cuts on the 0.5 grid cost fewer faces)."""
    return _up(_blocks(mask).any((1, 3, 5)))


def blur_albedo(v, mask, sigma, mix):
    """soften the baked 0.5 texel noise of `mask` (free: colour only)."""
    m = (mask & ~v.glow).astype(np.float32)
    w = ndi.gaussian_filter(m, sigma)
    for ch in range(3):
        b = ndi.gaussian_filter(v.col[..., ch] * m, sigma) / np.maximum(w, 1e-4)
        c = v.col[..., ch]
        sel = m > 0
        c[sel] = c[sel] * (1 - mix) + b[sel] * mix


# ------------------------------------------------------------------ wing anatomy (by colour: the bone arm, the claws, feathers)
def wing_parts(v, name):
    m = v.lab == v.names[name] if name in v.names else np.zeros(v.n, bool)
    c = v.col
    bone = m & (c[..., 0] - c[..., 2] > 22) & (c[..., 0] > 140)
    claw = m & ~bone & (c.min(-1) > 195)
    feather = m & ~bone & ~claw & ~v.glow
    return m, bone, claw, feather


def along_bone(v, bone, region, pivot):
    """for every cell of region: t = distance from the wing pivot of the nearest bone cell, d = distance to the bone (units)."""
    if not bone.any():
        return None, None
    d, idx = ndi.distance_transform_edt(~bone, return_indices=True)
    X, Y, Z = v.coords()
    ax = v.axes()
    bx = ax[0][idx[0]]
    by = ax[1][idx[1]]
    bz = ax[2][idx[2]]
    t = np.sqrt((bx - pivot[0]) ** 2 + (by - pivot[1]) ** 2 + (bz - pivot[2]) ** 2)
    return t, d * K.H


def feathers(v, s, ph, variant, seed, notch=True):
    pv = pivots(s)
    X, Y, Z = v.coords()
    for name in WINGS:
        if name not in v.names:
            continue
        m, bone, claw, fea = wing_parts(v, name)
        if not fea.any() or not bone.any():
            continue
        t, d = along_bone(v, bone, fea, pv[name])
        P = 1.5                                         # one feather every 1.5 units along the arm
        f = (t / P) % 1.0
        seam = fea & (f < 0.13)
        blur_albedo(v, fea, 1.0, 0.6)
        rachis = fea & (np.abs(f - 0.55) < 0.07) & (d > 0.4)
        barbs = fea & ~seam & ~rachis & (((t + 0.7 * d) / 0.5) % 1.0 < 0.34)
        tipdark = fea & (d > 2.6)
        v.paint(barbs, lambda q: v.col[q] * 0.86)
        v.paint(tipdark & ~seam, lambda q: v.col[q] * 0.9)
        v.paint(rachis, lambda q: np.clip(v.col[q] * 1.45 + 18, 0, 210))
        v.paint(seam, lambda q: v.col[q] * FEATHER_SEAM)
        # notched trailing edge: cut the seam through the outer tips of the feathers (visible side only)
        vis = np.broadcast_to(Z > -1.5, v.n) if ph != 2 else np.broadcast_to(Z > -2.5, v.n)
        if notch:
            tips = block_align(seam & (d > 2.0) & vis) & fea & (d > 1.6)
            v.carve(tips)
        # bone arm: a darker joint ring every 3 units, a pale ridge line
        v.paint(bone & (((t / 3.0) % 1.0) < 0.08), lambda q: v.col[q] * 0.72)


def scars(v, ph, seed, heart, front, n=6, steps=40):
    cy = centre_y(ph)
    start = K.nearest_surface_cell(v, heart & front, (0.0, cy + 5.5, 3.0))
    if start is None:
        return
    dirs = [(-1, -0.4, 0.4), (1, -0.4, 0.4), (-0.6, -1, 0.4), (0.6, -1, 0.4), (-1.2, 0.1, 0.3), (1.2, 0.1, 0.3)][:n]
    paths = K.surface_walk(v, heart & front, [start] * len(dirs), steps=steps, seed=seed + 5, branch=0.04, persist=0.96,
                           width=1, dirs=dirs, jitter=0.18)
    print(f"   scars p{ph}: {int(paths.sum())} cells")
    near = ndi.binary_dilation(paths, structure=K.ball(1.2))
    v.paint(paths & heart, lambda q: v.col[q] * 0.45 + np.array(SCAR, np.float32) * 0.55)
    v.paint(near & ~paths & heart, lambda q: v.col[q] * 0.85 + np.array(SCAR, np.float32) * 0.15)


def seams(v, ph, heart, front):
    """two carved lobe seams curving down from the vessel root past the core, and a cleft at the bottom."""
    X, Y, Z = v.coords()
    cy = centre_y(ph)
    yy = Y - cy
    s1 = np.abs(np.abs(X) - (3.6 + 0.10 * yy ** 2 * 0.25)) < 0.2
    s1 = s1 & (yy > -4.0) & (yy < 5.0)
    s2 = (np.abs(X) < 0.2) & (yy < -2.4)
    cond = np.broadcast_to(s1 | s2, v.n) & front
    K.groove(v, heart, cond, depth=1, color=None, only=["heart"])


# ------------------------------------------------------------------ storm damage (C), growing with the phase
def cracks(v, ph, seed, heart, front):
    X, Y, Z = v.coords()
    cy = centre_y(ph)
    n = {1: 3, 2: 6, 3: 10}[ph]
    steps = {1: 18, 2: 26, 3: 32}[ph]
    width = 1
    rng = np.random.default_rng(seed + 40)
    ang = np.linspace(0, 2 * np.pi, n, endpoint=False) + 0.4 + rng.uniform(-0.3, 0.3, n)
    starts, dirs = [], []
    for a in ang:
        c = K.nearest_surface_cell(v, heart & front, (3.0 * np.cos(a), cy + 1.0 + 3.0 * np.sin(a), 4.0))
        if c is None:
            continue
        starts.append(c)
        dirs.append((np.cos(a), np.sin(a), -0.1))
    # jagged: low persistence, high jitter (lightning-like), like the web
    paths = K.surface_walk(v, heart & front, starts, steps=steps, seed=seed + 7, branch=0.07, persist=0.8, width=width,
                           dirs=dirs, jitter=0.6)
    print(f"   cracks p{ph}: {len(starts)} starts, {int(paths.sum())} cells")
    # painted, not carved: a glowing hairline costs no faces (carving them cost ~1000 faces on phase 3)
    v.paint(paths & heart, GLOW, glow=True)
    halo = ndi.binary_dilation(paths, structure=K.ball(1.1)) & heart & ~paths
    v.paint(halo, lambda q: v.col[q] * 0.55 + np.array((120, 150, 200), np.float32) * 0.25)
    if ph == 3:        # chipped lobes: a few bites out of the rim, on the 0.5 grid
        spots = [(-5.6, 20.0, 2.0), (5.9, 14.4, 1.5), (-4.4, 12.6, 2.2), (4.2, 21.4, 2.4)]
        for p in spots:
            cell = K.nearest_surface_cell(v, heart, p)
            if cell is None:
                continue
            c = v.lo + (cell + 0.5) * K.H
            bite = np.broadcast_to((X - c[0]) ** 2 + (Y - c[1]) ** 2 + (Z - c[2]) ** 2 < 1.4 ** 2, v.n)
            bite = block_align(bite) & heart & (K.depth_inside(v) <= 4)
            v.carve(bite)
            rim = ndi.binary_dilation(bite, structure=K.ball(1.5)) & v.occ & own(v, ["heart"]) & ~v.glow
            v.paint(rim, lambda q: v.col[q] * 0.5 + np.array(SOOT, np.float32) * 0.5)
    # soot around the cracks
    soot = ndi.binary_dilation(paths, structure=K.ball(1.6 + 0.5 * ph)) & heart & ~v.glow
    nz = K.smooth_noise(v, 0.8, seed + 50 + ph, 2)
    soot &= nz < 0.3
    k = {1: 0.25, 2: 0.4, 3: 0.55}[ph]
    v.paint(soot, lambda q: v.col[q] * (1 - k) + np.array(SOOT, np.float32) * k)
    return paths


def scorch_patches(v, mask, ph, seed, amount):
    """soot blotches (painted, no faces)."""
    nz = K.smooth_noise(v, 1.2, seed, 2)
    m = mask & (nz > 1.0 - amount) & ~v.glow
    v.paint(m, lambda q: v.col[q] * 0.5 + np.array(SOOT, np.float32) * 0.5)
    m2 = mask & (nz > 1.0 - amount - 0.12) & ~m & ~v.glow
    v.paint(m2, lambda q: v.col[q] * 0.8 + np.array(SOOT, np.float32) * 0.2)


def charge(v, s, ph, seed):
    """phase 2: glowing sparks along the wing bones (painted glow, no faces)."""
    pv = pivots(s)
    for name in WINGS:
        if name not in v.names:
            continue
        m, bone, claw, fea = wing_parts(v, name)
        t, d = along_bone(v, bone, bone, pv[name])
        if t is None:
            continue
        surf = v.surface() & bone
        X, Y, Z = v.coords()
        zz = np.broadcast_to(Z, v.n)
        # the front-most surface line of the bone, broken into dashes
        spark = surf & (((t / 1.25) % 1.0) < 0.45) & (zz > np.percentile(zz[bone], 55))
        v.paint(spark, GLOW, glow=True)


def burn_phase3(v, s, seed):
    X, Y, Z = v.coords()
    bones = own(v, ["finger", "wing_l", "wing_r"])
    # charred: darken toward the finger tips (far from the centre)
    r = np.broadcast_to(np.sqrt(X ** 2 + ((Y - 18.0) * 1.2) ** 2), v.n)
    k = np.clip((r - 9.0) / 12.0, 0, 0.75)
    nz = K.smooth_noise(v, 0.9, seed + 61, 2)
    m = bones & ~v.glow & (nz < 0.5)
    v.col[m] = v.col[m] * (1 - k[m])[:, None] + np.array(SOOT, np.float32) * k[m][:, None]
    # flung feathers: rachis + burnt glowing edge on each feather
    if "shed" in v.names:
        sh = v.lab == v.names["shed"]
        lab, n = ndi.label(sh)
        for i in range(1, n + 1):
            q = lab == i
            pts = np.argwhere(q).astype(float)
            if len(pts) < 8:
                continue
            c = pts.mean(0)
            w, vec = np.linalg.eigh(np.cov((pts - c).T))
            ax = vec[:, -1]
            along = (pts - c) @ ax
            off = (pts - c) - along[:, None] * ax[None, :]
            dist = np.linalg.norm(off, axis=1)
            cells = tuple(pts.astype(int).T)
            col = v.col[cells]
            col = np.where((dist < 0.75)[:, None], np.clip(col * 1.7 + 30, 0, 200), col)
            col = np.where(((along % 2.0) < 0.6)[:, None] & (dist >= 0.75)[:, None], col * 0.7, col)
            v.col[cells] = col
            # glowing burnt end (the far end from the heart)
            wc = v.lo + (pts + 0.5) * K.H
            rr = np.linalg.norm(wc[:, :2] - np.array([0, 18.0]), axis=1)
            hot = (rr > np.percentile(rr, 88))
            g = tuple(pts[hot].astype(int).T)
            v.col[g] = GLOW
            v.glow[g] = True


# ------------------------------------------------------------------ the build
def build(variant, ph, seed=23):
    s = K.Source(SRC, ph)
    if variant == "now":
        return s
    v = s.world
    X, Y, Z = v.coords()
    cy = centre_y(ph)
    vis = np.broadcast_to(Z > -2.0, v.n)
    # ---- A: the organ, the core and the vessel rounded at 0.25 (front and sides only, the back stays as baked)
    smooth_coarse(v, ["heart"], sigma=1.6, where=vis, grow=2)
    if ph == 2:        # phase 2 has the least spare faces (big spread wings and crests): its core is rounded on the 0.5 grid
        smooth_coarse(v, ["core", "core_eye"], sigma=1.0)
    else:
        K.smooth(v, ["core", "core_eye"], sigma=1.3)
    heart = own(v, ["heart"]) & ~v.glow
    blur_albedo(v, heart, 1.6, 0.75)
    front = np.broadcast_to(Z > 0.0, v.n)
    n1 = K.smooth_noise(v, 1.0, seed + ph, 2)
    v.col[heart] = np.clip(v.col[heart] * (1.0 + 0.08 * n1[heart])[:, None], 0, 255)
    if variant in ("B", "C"):
        seams(v, ph, own(v, ["heart"]) & ~v.glow, front)
        heart = own(v, ["heart"]) & ~v.glow
        scars(v, ph, seed, heart, front, n=4)
        feathers(v, s, ph, variant, seed)
    if variant == "C":
        heart = own(v, ["heart"]) & ~v.glow
        cracks(v, ph, seed, heart, front)
        heart = own(v, ["heart"]) & ~v.glow
        scorch_patches(v, heart, ph, seed + 70, {1: 0.12, 2: 0.2, 3: 0.32}[ph])
        if ph == 2:
            charge(v, s, ph, seed)
        if ph == 3:
            burn_phase3(v, s, seed)
    return s


CROPS = [(20, 12, (0.3, 0.25, 0.7, 0.7)), (35, 18, (0.08, 0.1, 0.5, 0.9))]

if __name__ == "__main__":
    if sys.argv[1] == "rough":
        K.rough_sheet("storm_heart", "storm_heart phase 1: now / A / B / C (0.25 re-sculpt)", build, crops=CROPS)
    else:
        K.build_phases("storm_heart", build, sys.argv[2], OUT)
