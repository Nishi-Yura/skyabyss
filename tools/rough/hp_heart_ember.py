"""High-poly re-sculpt of the ember heart 余燼の心臓 (assignment 3, docs/design/15_high_poly.md).

Reads tools/sources/ember_heart/phaseN.* (the baked 0.5-unit rough B 熔け殻, kept as the input), re-sculpts it at 0.25 units
with tools/rough/hp_heart_kit.py and writes tools/sources/ember_heart_hp/phaseN.* (what tools/install/install_ember_heart.py installs).

Budget note: a clean round shell of this size costs ~13000 faces at 0.25 steps, so the big shell is rebuilt as a radial height
field at 0.5 steps (rebuild_shell: lumps gone, the sealed cavity of phase 1 is filled so no inner / hidden heart faces) and
0.25 is spent on what is seen up close: the carved cracks on the face, the bare heart of phase 2, tapered drips, painted
texture (one texel per 0.25 face).

Variants (compared on phase 1):
  A  rounded  : the shell rebuilt round (0.5 steps), the old crack blots follow it as paint, slag mottling + 0.25 grain, AO.
  B  plates   : A + a Voronoi plate network: cracks carved 0.25 deep on the face (glowing floor = crust_glow, heated rims),
                painted around the back, plates a little lighter/darker; tapered drips. p2 wider/deeper, p3 dull painted
                cracks with a few dull-red embers and flaked corners.
  C  volcanic : B + rocky relief noise, more and wider cracks, chipped plate corners everywhere, ember specks
                (carving pulled back to the very front to stay near the budget).

The phases tell the story: p1 sealed hot shell with fine cracks -> p2 split open, cracks widened, molten strands ->
p3 cooled ash, thin dull (non-glowing) cracks, flaking plates, a few embers.
Colour semantics kept for install_ember_heart.recolor: the shell stays low saturation (it is remapped by brightness), the
heated rims are warm (saturation > 60, kept), glow cells are flat-coloured (the glow layer is additive).

python -B tools/rough/hp_heart_ember.py rough          -> docs/review/high_poly_roughs/ember_heart_hp.png (phase 1, now/A/B/C)
python -B tools/rough/hp_heart_ember.py build B        -> tools/sources/ember_heart_hp/phase1..3 (each built from its own phase)
python -B tools/rough/hp_heart_ember.py phases B OUT.png  -> all three phases of one variant on one sheet (checking only)
python -B tools/rough/hp_heart_ember.py count B OUTDIR 1 2 3 -> faces per bone and one render per phase (checking only)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import hp_heart_kit as K  # noqa: E402
import voxel  # noqa: E402

SRC = ROOT / "tools/sources/ember_heart"
OUT = ROOT / "tools/sources/ember_heart_hp"
CY = 16.0

# colours (albedo before ao_shade; install recolours by brightness / saturation, see its colour section)
BASALT_D = (26, 24, 28)
BASALT = (44, 40, 44)
BASALT_L = (68, 62, 64)
SLAG = (58, 50, 50)              # low saturation: still "shell" for recolor
RIM = (120, 46, 26)              # heated rim around a crack: warm (sat > 60), kept by recolor
RIM_P3 = (112, 64, 52)           # p3: a faint rust rim (sat 60)
ASH_D = (92, 88, 86)             # p3 dull crack (low saturation)
ASH_W = (214, 208, 198)
EMBER = (255, 122, 32)
MOLTEN = (255, 204, 68)
DIM_RED = (196, 58, 30)
GLOW_COL = {1: EMBER, 2: MOLTEN, 3: DIM_RED}

MERGE = True
NEAR = {1: 6.0, 2: 1.0, 3: 1.0}
HOLE_OPEN = {1: None, 2: 3, 3: 6}
REBUILD = {1: True, 2: True, 3: True}
SIGMA = {1: (5.0, 7.0), 2: (7.0, 9.0), 3: (5.0, 7.0)}
HOLE_SIGMA = {1: 1.0, 2: 1.6, 3: 1.0}
ROOM = {1: 4.0, 2: 2.0, 3: 4.0}
SHELL_LABELS = {1: ["crust", "crust_glow"], 2: ["crust", "crust_glow"], 3: ["ash_shell"]}


def up2(a):
    return np.repeat(np.repeat(np.repeat(a, 2, 0), 2, 1), 2, 2)


def blocks(a):
    n = a.shape
    return a.reshape(n[0] // 2, 2, n[1] // 2, 2, n[2] // 2, 2).mean(axis=(1, 3, 5))


NE, NA = 90, 180          # 2 degree bins of elevation / azimuth around the heart centre


def _polar(pts):
    d = pts - np.array([0.0, CY, 0.0])
    r = np.sqrt((d ** 2).sum(-1))
    el = np.arcsin(np.clip(d[..., 1] / np.maximum(r, 1e-6), -1, 1))
    az = np.arctan2(d[..., 0], d[..., 2])
    fe = (el + np.pi / 2) / np.pi * NE - 0.5
    fa = (az + np.pi) / (2 * np.pi) * NA - 0.5
    return r, fe, fa


def _sample(m, fe, fa):
    """bilinear sample of a (NE, NA) map, wrapping in azimuth."""
    p = np.concatenate([m[:, -2:], m, m[:, :2]], 1)
    return ndi.map_coordinates(p, [np.clip(fe, 0, NE - 1), fa + 2], order=1, mode="nearest")


def rebuild_shell(v, labels, sigma=(5.0, 7.0), hole_open=3, hole_sigma=1.0, near_sigma=6.0, merge=False, back_az=None, room_sigma=4.0):
    """Rebuild the shell as a radial height field around the heart centre, at 0.5 steps (the big shell would cost 4x at 0.25).

    R_out(direction) = the outermost old shell cell of each 2-degree bin, blurred in direction space (sigma in bins) -> a clean
    round outer surface (the lumpy stair steps go). Bins without shell are openings (the split top of phase 2, the holes of
    the ash cage); hole_open drops openings smaller than that many bins (None: every pinhole is sealed, the shell of phase 1).
    Far from every opening the shell is solid down to the heart: that cavity is never seen, so no inner faces and no hidden
    heart faces (the heart moves/scales inside a closed shell). Near an opening (blurred opening mask > 0.03, near_sigma) the
    inner surface R_in is kept (blurred too) so the heart keeps its room there.
    Every new cell takes the owner/colour of the nearest old shell cell, so the crack network (crust_glow) follows the new
    surface; cells deeper than 3 cells are plain shell. merge: every shell cell joins labels[0] (glow stays painted on it)."""
    shell = owned(v, labels)
    other = v.occ & ~shell
    pts = v.lo + (np.argwhere(shell) + 0.5) * K.H
    r, fe, fa = _polar(pts)
    ie = np.clip(np.round(fe).astype(int), 0, NE - 1)
    ia = np.round(fa).astype(int) % NA
    rout = np.full((NE, NA), -1.0)
    rin = np.full((NE, NA), 1e9)
    np.maximum.at(rout, (ie, ia), r)
    np.minimum.at(rin, (ie, ia), r)
    have = rout > 0
    valid = ndi.binary_closing(np.pad(have, ((2, 2), (0, 0)), mode="edge"), structure=np.ones((3, 3)))[2:-2]
    holes = ~valid
    if hole_open is None:
        holes[:] = False
    elif hole_open > 0:
        holes = ndi.binary_opening(holes, structure=np.ones((hole_open, hole_open)))
    if back_az is not None:     # close the openings on the back (|azimuth| > back_az degrees) below the top vent
        az = (np.arange(NA) + 0.5) / NA * 360.0 - 180.0
        el = (np.arange(NE) + 0.5) / NE * 180.0 - 90.0
        holes &= (np.abs(az)[None, :] < back_az) | (el[:, None] > 55.0)
    idx = ndi.distance_transform_edt(~have, return_distances=False, return_indices=True)
    pad = lambda m: np.concatenate([m[:, -16:], m, m[:, :16]], 1)
    cut = lambda m: m[:, 16:-16]
    blur = lambda m, s: cut(ndi.gaussian_filter(pad(m), s, mode="nearest"))
    rout_s = blur(rout[idx[0], idx[1]], sigma)
    rin_s = blur(rin[idx[0], idx[1]], sigma)
    hole_s = blur(holes.astype(float), hole_sigma)
    near_s = blur(holes.astype(float), near_sigma)
    # evaluate on the 0.5 grid (block centres)
    n2 = tuple(k // 2 for k in v.n)
    a = [v.lo[i] + (np.arange(n2[i]) + 0.5) * 0.5 for i in range(3)]
    P = np.stack(np.meshgrid(*a, indexing="ij"), -1)
    rr, ffe, ffa = _polar(P)
    ok = (rr <= _sample(rout_s, ffe, ffa)) & (_sample(hole_s, ffe, ffa) < 0.5)
    ok &= (rr >= _sample(rin_s, ffe, ffa)) | (_sample(near_s, ffe, ffa) < 0.03)
    # around an opening the fill keeps 0.5 units off the other bones (the heart shrinks / shakes in its animations)
    room = ndi.binary_dilation(blocks(other) > 0, structure=K.ball(1.0)) & (_sample(blur(holes.astype(float), room_sigma), ffe, ffa) > 0.03)
    ok &= ~room | (rr >= _sample(rin_s, ffe, ffa))
    new_shell = up2(ok) & ~other
    removed = shell & ~new_shell
    added = new_shell & ~shell
    base = np.median(v.col[shell & ~v.glow], 0) if (shell & ~v.glow).any() else np.array(BASALT, np.float32)
    v.lab[removed] = 0
    v.nearest_fill(shell & ~removed, added)
    deep = owned(v, labels) & (K.depth_inside(v) > 3)
    v.glow[deep] = False
    v.lab[deep] = v.names[labels[0]]
    v.col[deep & added] = base
    if merge:
        v.lab[owned(v, labels)] = v.names[labels[0]]


def ids(v, names):
    return [v.names[n] for n in names if n in v.names]


def owned(v, names):
    return np.isin(v.lab, ids(v, names))


def build(variant, ph, seed=23):
    s = K.Source(SRC, ph)
    if variant == "now":
        return s
    v = s.world
    X, Y, Z = v.coords()
    visible = np.broadcast_to(Z > -2.5, v.n)          # the back stays at 0.5 (2x2x2 cells mesh like before)
    shell_names = SHELL_LABELS[ph]
    # ---- A: round the shell and the organ (only where it can be seen), mottle the slag
    if REBUILD[ph]:
        rebuild_shell(v, shell_names, sigma=SIGMA[ph], hole_sigma=HOLE_SIGMA[ph], hole_open=HOLE_OPEN[ph], near_sigma=NEAR[ph], merge=MERGE, back_az=110.0 if ph == 3 else None, room_sigma=ROOM[ph])
    if ph == 2:      # the heart is bare only at phase 2 (hidden in the sealed shell at 1, under the ash at 3)
        K.smooth(v, ["heart", "heart_vein"], sigma=1.6, where=np.broadcast_to((Z > -1.0) & (Y > 14.5), v.n))   # above the bowl
    _mottle(v, ph, seed)
    if variant in ("B", "C"):
        heavy = variant == "C"
        if heavy:
            K.relief(v, owned(v, shell_names) & visible, amp=0.9, scale=1.4, seed=seed + 11 * ph, octaves=2)
        gap3 = _plates(v, ph, seed, visible, heavy)
        _taper(v, ["drips", "ash_drips"])
        if heavy or ph == 3:        # p3 flakes in B too (the ash shell is cheap, flaking is its story)
            _chips(v, ph, seed, visible, gap3)
        if heavy:
            _specks(v, ph, seed, visible)
    return s


# ------------------------------------------------------------------ A
def _mottle(v, ph, seed):
    shell = owned(v, SHELL_LABELS[ph]) & ~v.glow
    n1 = K.smooth_noise(v, 1.1, seed + ph, 2)
    n2 = K.smooth_noise(v, 0.4, seed + 7 * ph, 1)
    grain = np.random.default_rng(seed + 40 + ph).uniform(-1, 1, v.n).astype(np.float32)   # 0.25 texel grain (painted, free)
    f = (1.0 + 0.16 * n1 + 0.10 * n2 + 0.07 * grain)[..., None]
    v.col[shell] = np.clip(v.col[shell] * f[shell], 0, 255)
    if ph < 3:   # slag patches (grey-violet, low saturation) and darker pits
        K.tint(v, shell & (n1 > 0.35), [BASALT, SLAG, BASALT_L], scale=0.7, seed=seed + 2, mix=0.5)
        v.paint(shell & (n2 < -0.62), BASALT_D)
    else:        # ash: soot streaks and pale flakes
        K.tint(v, shell & (n1 > 0.3), [ASH_D, (150, 144, 138), ASH_W], scale=0.6, seed=seed + 2, mix=0.45)
    organ = owned(v, ["heart", "heart_vein"]) & ~v.glow
    v.col[organ] = np.clip(v.col[organ] * (1.0 + 0.10 * n1[organ])[:, None], 0, 255)


# ------------------------------------------------------------------ B / C: the plate network
PLATES = {1: 26, 2: 16, 3: 30}          # Voronoi plates over the whole sphere of directions
WIDTH = {1: 0.30, 2: 0.55, 3: 0.26}     # crack half-width in units: p1 fine, p2 torn open, p3 thin and dull
DEPTH = {1: 1, 2: 2, 3: 1}              # carved depth in 0.25 cells (p3 is painted only)
CHIPS = {1: 6, 2: 5, 3: 9}               # chipped corners (C everywhere, B only on the ash shell)
CARVE_Z = {1: 3.5, 2: 6.5, 3: 99.0}      # cracks are carved where z > this (the face), painted elsewhere (C: +1.5)


def plate_gaps(v, mask, n, seed, warp=0.30):
    """For the cells of mask: (gap, plate, gap3). Plates = Voronoi cells of n even directions around the heart centre,
    warped by smooth noise so the edges wander like real cracks. gap = arc distance (units) between the nearest and the
    second nearest seed direction (0 on a crack line), gap3 the same for the third (small at the plate corners)."""
    rng = np.random.default_rng(seed)
    k = np.arange(n) + 0.5
    phi = np.arccos(1 - 2 * k / n)
    th = np.pi * (1 + 5 ** 0.5) * k
    seeds = np.stack([np.sin(phi) * np.cos(th), np.cos(phi), np.sin(phi) * np.sin(th)], 1)
    seeds += rng.normal(scale=0.09, size=seeds.shape)
    seeds /= np.linalg.norm(seeds, axis=1, keepdims=True)
    idx = np.argwhere(mask)
    p = v.lo + (idx + 0.5) * K.H - np.array([0.0, CY, 0.0])
    r = np.linalg.norm(p, axis=1)
    u = p / np.maximum(r, 1e-6)[:, None]
    for a in range(3):
        nz = K.smooth_noise(v, 2.2, seed + 100 + a, 2)
        u[:, a] += warp * nz[tuple(idx.T)]
    u /= np.linalg.norm(u, axis=1, keepdims=True)
    ang = np.arccos(np.clip(u @ seeds.T, -1, 1))
    order = np.argsort(ang, axis=1)[:, :3]
    a3 = np.take_along_axis(ang, order, 1)
    gap = np.full(v.n, np.inf, np.float32)
    gap3 = np.full(v.n, np.inf, np.float32)
    plate = np.full(v.n, -1, np.int32)
    gap[tuple(idx.T)] = (a3[:, 1] - a3[:, 0]) * r * 0.5
    gap3[tuple(idx.T)] = (a3[:, 2] - a3[:, 0]) * r * 0.5
    plate[tuple(idx.T)] = order[:, 0]
    return gap, plate, gap3


def _plates(v, ph, seed, visible, heavy):
    names = SHELL_LABELS[ph]
    shell = owned(v, names)
    surf = v.surface() & shell
    gap, plate, gap3 = plate_gaps(v, surf, int(PLATES[ph] * (1.3 if heavy else 1.0)), seed + 17 * ph)
    w = WIDTH[ph] * (1.35 if heavy else 1.0)
    # every plate a little lighter or darker (painted, free)
    rng = np.random.default_rng(seed + 3 * ph)
    fac = rng.uniform(0.86, 1.14, PLATES[ph] * 2)
    m = surf & ~v.glow & (plate >= 0)
    v.col[m] = np.clip(v.col[m] * fac[plate[m]][:, None], 0, 255)
    if ph < 3:       # the old glow blots give way to the plate network (p1: the top vent stays, p2: the molten lip stays)
        X, Y, Z = v.coords()
        old = shell & v.glow & np.broadcast_to(Y < (25.0 if ph == 1 else 13.5), v.n)
        v.glow[old] = False
        v.col[old] = np.array(BASALT, np.float32) * rng.uniform(0.85, 1.15, (int(old.sum()), 1))
    crack = surf & (gap < w)
    X, Y, Z = v.coords()
    front = crack & np.broadcast_to(Z > CARVE_Z[ph] + (1.5 if heavy else 0.0), v.n)       # carved only on the face turned to the player (faces cost)
    back = crack & ~front
    rim_t = np.clip((gap - w) / 0.7, 0, 1)
    rim = surf & ~crack & (gap < w + 0.7)
    if ph < 3:
        col = GLOW_COL[ph]
        cut = K.groove(v, np.ones(v.n, bool), front, depth=DEPTH[ph], glow=True, color=col, only=names)
        floor = owned(v, names) & v.glow & ndi.binary_dilation(cut, structure=K.ball(1.1))
        v.lab[floor] = v.lid("crust_glow")
        v.paint(back, col, glow=True)
        rc = np.array(RIM, np.float32)
        v.col[rim & v.occ] = (rc * (1 - rim_t[..., None]) + v.col * rim_t[..., None])[rim & v.occ]
    else:
        cut = K.groove(v, np.ones(v.n, bool), front, depth=1, color=ASH_D, only=names)
        v.paint(back, ASH_D)
        n = K.smooth_noise(v, 0.8, seed + 9, 1)
        rc = np.array(RIM_P3, np.float32)
        rr = rim & v.occ & (n > 0.1)
        v.col[rr] = (rc * (1 - rim_t[..., None]) + v.col * rim_t[..., None])[rr]
        # a few crack floors still hold a dull red ember (lower half, where the heart is)
        X, Y, Z = v.coords()
        fl = owned(v, names) & (crack | ndi.binary_dilation(cut, structure=K.ball(1.1))) & v.surface() & np.broadcast_to(Y < 19.0, v.n)
        if fl.any():
            fl &= n > np.quantile(n[fl], 0.88)
        v.paint(fl, DIM_RED, glow=True)
        print(f"  p3 ember cells in the cracks: {int(fl.sum())}")
    print(f"  plates p{ph}: crack cells {int(crack.sum())} (carved {int(cut.sum())}), rim {int(rim.sum())}")
    return gap3


def _taper(v, names):
    """Drips end in tapered strands: in each horizontal slice of a strand, the lower part loses its outer ring
    (never the whole slice), so the tip narrows."""
    for name in names:
        if name not in v.names:
            continue
        m = v.lab == v.names[name]
        if not m.any():
            continue
        comp, nc = ndi.label(m)
        for c in range(1, nc + 1):
            cm = comp == c
            yy = np.nonzero(cm.any(axis=(0, 2)))[0]
            y0, y1 = yy.min(), yy.max()
            if y1 - y0 < 4:
                continue
            for y in range(y0, y1 + 1):
                t = (y - y0) / (y1 - y0)
                sl = cm[:, y, :]
                if t > 0.4:
                    continue
                d = ndi.distance_transform_cdt(sl, metric="taxicab")
                need = 2 if t < 0.15 else 1
                if (d > need).any():
                    cut = sl & (d <= need)
                    col = v.lab[:, y, :]
                    col[cut] = 0


# ------------------------------------------------------------------ C
def _chips(v, ph, seed, visible, gap3):
    """Chipped plates: bites where three plates meet; p1 the hot slag under the crust shows (warm, not glowing),
    p2 the bite glows, p3 a flake of ash has come off (paler ash below)."""
    names = SHELL_LABELS[ph]
    shell = owned(v, names)
    corner = v.surface() & shell & visible & (gap3 < WIDTH[ph] * 2.5)
    lab, nc = ndi.label(corner)
    rng = np.random.default_rng(seed + 60 + ph)
    pick = rng.permutation(np.arange(1, nc + 1))[:CHIPS[ph]]
    chips = ndi.binary_dilation(np.isin(lab, pick), structure=K.ball(2.5)) & shell
    lab = chips.astype(int)
    nc = len(pick)
    if ph == 1:
        K.groove(v, lab > 0, np.ones(v.n, bool), depth=2, color=RIM, only=names)
    elif ph == 2:
        K.groove(v, lab > 0, np.ones(v.n, bool), depth=2, glow=True, color=GLOW_COL[2], only=names)
    else:
        K.groove(v, lab > 0, np.ones(v.n, bool), depth=1, color=ASH_W, only=names)
    print(f"  chips p{ph}: {nc}")


def _specks(v, ph, seed, visible):
    shell = v.surface() & owned(v, SHELL_LABELS[ph]) & ~v.glow & visible
    rng = np.random.default_rng(seed + 70 + ph)
    p = {1: 0.010, 2: 0.016, 3: 0.003}[ph]
    sp = shell & (rng.random(v.n) < p)
    v.paint(sp, GLOW_COL[ph], glow=True)


# ------------------------------------------------------------------ checking sheet of all phases
def phases_sheet(variant, out):
    cols = []
    for ph in (1, 2, 3):
        src = build(variant, ph)
        geo, tex, gl, st = K.finish(src)
        nb, nq = K.count(geo)
        print(f"phase {ph}: {nb} boxes / {nq} quads, overlaps {len(K.sat_check(geo))}, glow {int((gl[..., 3] > 0).sum())}",
              st["per_bone"])
        row = [K.render_geo(geo, tex, gl, y, p, (360, 360)) for y, p in ((35, 22), (0, 8), (200, 15))]
        row += [K.render_crop(geo, tex, gl, y, p, c, out=(360, 360)) for y, p, c in CROPS]
        cols.append((f"p{ph} {nb}/{nq}", row))
    voxel.sheet(f"ember_heart {variant}: phases 1-3", cols).save(out)


CROPS = [(25, 15, (0.3, 0.22, 0.7, 0.62)), (0, 6, (0.36, 0.36, 0.64, 0.64))]

if __name__ == "__main__":
    if sys.argv[1] == "rough":
        K.rough_sheet("ember_heart", "ember_heart phase 1: now / A / B / C (0.25 re-sculpt)", build, crops=CROPS)
    elif sys.argv[1] == "count":
        import time
        import gc
        for a in sys.argv[4:]:       # count VARIANT OUTDIR PHASE...: budget per bone + a render per phase (checking only)
            t = time.time()
            geo, tex, gl, st = K.finish(build(sys.argv[2], int(a)))
            gc.collect()
            print(sys.argv[2], a, K.count(geo), len(K.sat_check(geo)), int((gl[..., 3] > 0).sum()), st["per_bone"], f"{time.time() - t:.0f}s")
            K.render_geo(geo, tex, gl, 25, 15, (600, 600)).save(Path(sys.argv[3]) / f"ember_{sys.argv[2]}{a}.png")
    elif sys.argv[1] == "phases":
        phases_sheet(sys.argv[2], sys.argv[3])
    else:
        K.build_phases("ember_heart", build, sys.argv[2], OUT)
