"""High-poly re-sculpt of the tide-shell heart (assignment 3, docs/design/15_high_poly.md).

Reads tools/sources/tide_heart/phaseN.* (the baked 0.5-unit rough, kept as the input), re-sculpts it at 0.25 units with
tools/rough/hp_heart_kit.py and writes tools/sources/tide_heart_hp/phaseN.* (what tools/install/install_tide_heart.py installs).

Variants (compared on phase 1):
  A  smooth pearl : the heart organ rounded at 0.25, soft nacre light/dark, ambient occlusion. Nothing new is added.
  B  carved pearl : A + scallop grooves in the lobes, a raised purple vein net, a petal collar around the core, tapered arteries.
  C  shell and pearl : B + growth ridges on the spiral shell and teeth on the aperture lip (more faces).

python -B tools/rough/hp_heart_tide.py rough          -> docs/review/high_poly_roughs/tide_heart_hp.png (phase 1, now/A/B/C)
python -B tools/rough/hp_heart_tide.py build B        -> tools/sources/tide_heart_hp/phase1..3 (+ phase2 as built, not interpolated)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import hp_heart_kit as K  # noqa: E402

SRC = ROOT / "tools/sources/tide_heart"
OUT = ROOT / "tools/sources/tide_heart_hp"
CY = 16.0
VEIN = (122, 66, 122)
VEIN_L = (156, 92, 150)
PEARL_HI = (246, 238, 234)
NACRE_HI = (236, 224, 244)

HEART_LABELS = ["heart", "heart_vein", "core_ring", "core_eye"]


def build(variant, ph, seed=11):
    s = K.Source(SRC, ph)
    v = s.world
    X, Y, Z = v.coords()
    dz = 5.5 if False else 0.0
    if variant == "now":
        return s
    # ---- the organ at 0.25: only where it can be seen (front and the sides), the back stays at 0.5
    visible = np.broadcast_to(Z > -1.5, v.n)
    K.smooth(v, HEART_LABELS, sigma=1.7, where=visible)
    K.smooth(v, ["core_ring", "core_eye", "chamber"], sigma=1.3)
    heart = np.isin(v.lab, [v.names[n] for n in ("heart", "heart_vein") if n in v.names])
    # soft nacre: light and dark patches (value only: install_tide_heart.pearlize recolours the hue)
    n1 = K.smooth_noise(v, 0.9, seed + ph, 2)
    shade = (1.0 + 0.10 * n1)[..., None]
    hm = heart & ~v.glow
    v.col[hm] = np.clip(v.col[hm] * shade[hm], 0, 255)
    if variant in ("B", "C"):
        _carve_heart(v, ph, seed, heart)
    if variant == "C":
        _shell_detail(v, ph, seed)
    return s


def _carve_heart(v, ph, seed, heart):
    X, Y, Z = v.coords()
    rr = np.sqrt(X ** 2 + (Y - CY) ** 2)
    th = np.arctan2(Y - CY, X)
    front = np.broadcast_to(Z > 0.6, v.n)
    hb = heart & ~v.glow
    # scallop grooves radiating from the core: 18 ribs, one cell deep, outside the core socket
    ribs = np.broadcast_to((np.cos(18 * th) > 0.78) & (rr > 3.2) & (rr < 6.6), v.n)
    K.groove(v, front & hb, ribs, depth=1, only=["heart"])
    # purple vein net: painted on the surface (no new faces), running down from the aorta root over both lobes
    start = K.nearest_surface_cell(v, hb & front, (0.0, CY + 4.4, 2.5))
    starts = [start] * 4
    dirs = [(-1, -0.6, 0.5), (1, -0.6, 0.5), (-0.45, -1, 0.5), (0.45, -1, 0.5)]
    paths = K.surface_walk(v, hb & front, starts, steps=40, seed=seed + 5, branch=0.035, persist=0.95, width=1, dirs=dirs, jitter=0.22)
    v.paint(paths & hb, VEIN)
    # petal collar around the core: 12 raised nacre teeth just outside the glowing core (core centre z = 3.5 at phase 1-2)
    cz = 3.5 + (5.5 if ph == 3 else 0.0)
    rc = np.sqrt(X ** 2 + (Y - CY) ** 2)
    petal = (np.cos(12 * th) > 0.35) & (rc > 2.6) & (rc < 3.9) & (Z > cz - 1.6)
    K.raise_pattern(v, np.ones(v.n, bool), np.broadcast_to(petal, v.n), height=2, color=PEARL_HI, only=["heart"], bone="heart")


CRACK = {1: (120, 246, 222), 2: (120, 246, 222), 3: (168, 204, 255)}


def _cracks(v, ph, seed):
    """Hairline cracks that leak the core's light: 3 at phase 1, 6 (wider) at phase 2, 9 and chipped lobes at phase 3."""
    X, Y, Z = v.coords()
    heart = np.isin(v.lab, [v.names[n] for n in ("heart", "heart_vein") if n in v.names]) & ~v.glow
    front = np.broadcast_to(Z > 0.4, v.n)
    n = {1: 3, 2: 6, 3: 9}[ph]
    steps = {1: 22, 2: 28, 3: 30}[ph]
    width = {1: 2, 2: 2, 3: 2}[ph]
    n = {1: 5, 2: 7, 3: 8}[ph]
    rng = np.random.default_rng(seed + 40)
    ang = (np.linspace(0, 2 * np.pi, n, endpoint=False) + 0.5 + rng.uniform(-0.25, 0.25, n))
    rc = 3.5 + (0.5 if ph == 1 else 0.0)
    starts, dirs = [], []
    for a in ang:
        c = K.nearest_surface_cell(v, heart & front, (rc * np.cos(a), CY + rc * np.sin(a), 3.0))
        if c is None:
            continue
        starts.append(c)
        dirs.append((np.cos(a), np.sin(a), 0.15))
    paths = K.surface_walk(v, heart & front, starts, steps=steps, seed=seed + 7, branch=0.05, persist=0.9, width=width, dirs=dirs, jitter=0.5)
    K.groove(v, np.ones(v.n, bool), paths, depth=width, glow=True, color=CRACK[ph], only=["heart"])
    if ph >= 2:        # chipped lobes: a few small bites out of the rim
        k = 2 if ph == 2 else 4
        spots = [(-5.6, CY + 3.0, 2.0), (5.9, CY - 2.6, 1.5), (-4.4, CY - 4.8, 2.2), (3.8, CY + 4.6, 2.4)][:k]
        for p in spots:
            cell = K.nearest_surface_cell(v, heart, p)
            if cell is None:
                continue
            c = v.lo + (cell + 0.5) * K.H
            bite = (X - c[0]) ** 2 + (Y - c[1]) ** 2 + (Z - c[2]) ** 2 < (1.25 + 0.25 * ph) ** 2
            bite = np.broadcast_to(bite, v.n)
            K.groove(v, bite & heart, np.ones(v.n, bool), depth=1, color=(244, 234, 238), only=["heart"])


def _shell_detail(v, ph, seed):
    _cracks(v, ph, seed)


CROPS = [(18, 12, (0.22, 0.2, 0.78, 0.76)), (0, 4, (0.3, 0.28, 0.7, 0.68))]

if __name__ == "__main__":
    if sys.argv[1] == "rough":
        K.rough_sheet("tide_heart", "tide_heart phase 1: now / A / B / C (0.25 re-sculpt)", build, crops=CROPS)
    else:
        K.build_phases("tide_heart", build, sys.argv[2], OUT)
