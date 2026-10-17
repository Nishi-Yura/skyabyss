"""High-poly rough 1b: thunder-wing and ember mobs (feather_warden, static_moth, thunder_supplicant, ash_thrall,
ember_moth, cinder_roller).  3 options (A/B/C) each vs now.  Face budget per mob: 1,500 (docs/design/15_high_poly.md).

These six are already built from 0.5 cells (voxel greedy boxes) and sit at 560..1970 faces, so rounding boxes alone
does not help.  The recipe is therefore (all in this file, hpkit.py is untouched):

  prune   drop faces covered by a neighbour box that moves with the same rigid group (never across a joint,
          never under the crust / flare bones of ash_thrall)  -> frees the budget
  bevel   neighbour-aware stepped chamfer: only edges that really are a free outer edge get cut (a plain
          round_cube would also cut the seams between two touching boxes)
  trim    small 0.25 boxes (flat colour) for the face / hands / feathers / shell rims
  carve   holes (eye sockets, mouths)

python tools/rough/hp_mobs_storm_ember.py [name ...]      -> docs/review/high_poly_roughs/<name>.png (+ assets/<name>_<A|B|C>/)
The install scripts (install_storm_mobs.py, install_ember_mobs.py) call install_mob() after writing the raw model.
"""
from __future__ import annotations

import copy
import itertools
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import numpy as np
from PIL import Image

import hp_view
import hpkit
import voxel
from hpkit import DIRS, NORMAL, Model, _assemble, box_face_uvs, round_cube

ROOT = HERE.parents[1]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "docs/review/high_poly_roughs"
BASE = ROOT / "tools/sources/hp_base/entity"
BUDGET = 1500
Q = 4  # occupancy cells per unit (0.25)
NAMES = ["feather_warden", "static_moth", "thunder_supplicant", "ash_thrall", "ember_moth", "cinder_roller"]
# the textures that share the geo (same uv) and so must grow with the palette rows
SIBLINGS = {"ash_thrall": ["ash_thrall_flared"], "static_moth": ["static_moth_charge"]}


# ------------------------------------------------------------------ loading
def load(name, src="base"):
    """src='base': tools/sources/hp_base copy (idempotent); src='assets': what the install script just wrote."""
    if src == "assets":
        return Model.load(name)
    return Model.load(name, geo_path=BASE / f"{name}.geo.json", tex_path=BASE / f"{name}.png",
                      glow_path=BASE / f"{name}_glowmask.png")


def animated_bones(name):
    p = ASSETS / "animations/entity" / f"{name}.animation.json"
    s = set()
    for a in json.loads(p.read_text(encoding="utf-8"))["animations"].values():
        s.update(a.get("bones", {}).keys())
    return s


# ------------------------------------------------------------------ occupancy (0.25 grid)
class Occ:
    def __init__(self, m, code_of=None):
        cubes = [(b["name"], c) for b in m.g["bones"] for c in b.get("cubes", [])]
        lo = np.min([c["origin"] for _, c in cubes], axis=0) - 2
        hi = np.max([np.add(c["origin"], c["size"]) for _, c in cubes], axis=0) + 2
        self.off = np.floor(lo)
        self.shape = tuple(int(v) for v in np.ceil((hi - self.off) * Q))
        self.a = np.zeros(self.shape, np.int32)
        self.codes = {}
        for bn, c in cubes:
            if "rotation" in c:
                continue
            code = (code_of or (lambda b: 1))(bn)
            k = self.codes.setdefault(code, len(self.codes) + 1)
            i0, i1 = self.idx(c["origin"]), self.idx(np.add(c["origin"], c["size"]))
            self.a[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = k

    def idx(self, p):
        return [int(round((p[i] - self.off[i]) * Q)) for i in range(3)]

    def region(self, lo, hi):
        i0, i1 = self.idx(lo), self.idx(hi)
        i0 = [max(0, v) for v in i0]
        return self.a[i0[0]:max(i0[0], i1[0]), i0[1]:max(i0[1], i1[1]), i0[2]:max(i0[2], i1[2])]

    def any(self, lo, hi):
        return bool(self.region(lo, hi).any())


# ------------------------------------------------------------------ prune hidden faces
def prune(m, name, protect=(), deep=1.0):
    """Drop faces fully covered by another box of the same rigid group.  Rigid group = nearest animated ancestor
    (or itself).  Kinds: ash_thrall's crust_ / flare_ bones are only shown in one state each, so a base-bone face is
    never dropped under them, and a crust face is never dropped under a flare box (and vice versa)."""
    anim = animated_bones(name)
    parent = {b["name"]: b.get("parent") for b in m.g["bones"]}

    def group(b):
        while b not in anim and parent.get(b):
            b = parent[b]
        return b

    def kind(b):
        return "crust" if b.startswith("crust_") else "flare" if b.startswith("flare_") else "base"

    occ = Occ(m, code_of=lambda b: (group(b), kind(b)))
    inv = {v: k for k, v in occ.codes.items()}
    m._pruned = []      # (bone, cube, dir, uv) so that repair_pruned can put a face back

    def ok(owner, hider):  # may `hider` hide a face of `owner`?
        if owner[0] != hider[0]:
            return False
        return hider[1] == "base" or hider[1] == owner[1]

    def kind_ok(owner, hider):
        return hider[1] == "base" or hider[1] == owner[1]

    lut = {}
    dropped = 0
    for b in m.g["bones"]:
        if b["name"] in protect:
            continue
        ow = (group(b["name"]), kind(b["name"]))
        for c in b.get("cubes", []):
            if "rotation" in c:
                continue
            o, s = np.array(c["origin"]), np.array(c["size"])
            for d in list(c["uv"].keys()):
                ax, sg = NORMAL[d]
                lo, hi = o.copy(), o + s
                if sg > 0:
                    lo[ax], hi[ax] = hi[ax], hi[ax] + 1 / Q
                else:
                    lo[ax], hi[ax] = lo[ax] - 1 / Q, lo[ax]
                reg = occ.region(lo, hi)
                if reg.size == 0 or (reg == 0).any():
                    continue
                good = True
                for k in np.unique(reg):
                    key = (ow, int(k))
                    if key not in lut:
                        lut[key] = ok(ow, inv[int(k)])
                    if not lut[key]:
                        good = False
                        break
                if not good and deep:
                    # buried `deep` units under other groups' boxes (joints swing by less than that): drop too
                    lo2, hi2 = o.copy(), o + s
                    if sg > 0:
                        lo2[ax], hi2[ax] = hi2[ax], hi2[ax] + deep
                    else:
                        lo2[ax], hi2[ax] = lo2[ax] - deep, lo2[ax]
                    r2 = occ.region(lo2, hi2)
                    good = bool(r2.size) and not (r2 == 0).any() and all(
                        kind_ok(ow, inv[int(k)]) for k in np.unique(r2))
                if good:
                    m._pruned.append((b["name"], c, d, c["uv"][d]))
                    del c["uv"][d]
                    dropped += 1
    return dropped


# ------------------------------------------------------------------ neighbour-aware bevel
def bevel_cube(c, occ, step=0.25, R=1, axes="xyz", min_dim=None, min_center=0.5, holes=None):
    """round_cube, but a corner cell is only cut when it is a free outer edge of the whole model (the cells next
    to it, outside this box, are empty).  Touching boxes therefore keep a flush seam."""
    o = np.array(c["origin"], float)
    s = np.array(c["size"], float)
    rect = box_face_uvs(c)
    fallback = next(iter(rect.values()), None)
    holes = [(np.array(h[0], float), np.array(h[1], float)) for h in (holes or [])]
    L = [max(0, min(R, int(np.floor((s[a] - min_center) / (2 * step) + 1e-9)))) for a in range(3)]
    asel = ["xyz".index(ch) for ch in axes]
    rounding = sum(1 for a in asel if L[a] > 0) >= 2 and R > 0 and not (min_dim is not None and sorted(s)[1] < min_dim)
    if not rounding and not holes:
        return [hpkit._plain(c, o, s, rect, fallback)]
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
    oo, ee = o, o + s
    lev = []
    for a in asel:
        row = []
        for i in range(n[a]):
            mm = mids[a][i]
            dl, dh = mm - oo[a], ee[a] - mm
            if dl < L[a] * step:
                row.append((int(dl // step), -1))
            elif dh < L[a] * step:
                row.append((int(dh // step), 1))
            else:
                row.append(None)
        lev.append(row)
    for idx in itertools.product(*[range(k) for k in n]):
        mx, my, mz = (mids[a][idx[a]] for a in range(3))
        if holes and any(lo[0] < mx < hi[0] and lo[1] < my < hi[1] and lo[2] < mz < hi[2] for lo, hi in holes):
            filled[idx] = False
            continue
        if not rounding:
            continue
        lv = [(j, lev[j][idx[a]]) for j, a in enumerate(asel) for a in [a] if lev[j][idx[a]] is not None]
        if any(lv[p][1][0] + lv[q][1][0] < R for p in range(len(lv)) for q in range(p + 1, len(lv))):
            # candidate for removal: the cell must be a free edge
            lo = [cuts[a][idx[a]] for a in range(3)]
            hi = [cuts[a][idx[a] + 1] for a in range(3)]
            # every side on which the cell touches the box surface (not only the rounded axes): whatever lies there
            # must be empty, or removing the cell would uncover a face we dropped from the neighbour
            outs = []
            for a in range(3):
                if idx[a] == 0:
                    outs.append((a, -1))
                if idx[a] == n[a] - 1:
                    outs.append((a, 1))
            free = True
            for r in range(1, len(outs) + 1):
                for sub in itertools.combinations(outs, r):
                    l2, h2 = list(lo), list(hi)
                    for a, sd in sub:
                        if sd < 0:
                            l2[a], h2[a] = lo[a] - step, lo[a]
                        else:
                            l2[a], h2[a] = hi[a], hi[a] + step
                    if occ.any(l2, h2):
                        free = False
                        break
                if not free:
                    break
            if free:
                filled[idx] = False
    return _assemble(c, o, s, rect, fallback, cuts, filled, merge=True)


def bevel_all(m, step=0.25, R=1, min_dim=1.0, bones=None, skip=None, rule=None, occ=None):
    occ = occ or Occ(m)
    for b in m.g["bones"]:
        if bones is not None and not bones(b["name"]):
            continue
        new = []
        for c in b.get("cubes", []):
            if "rotation" in c or (skip and skip(b, c)) or any(isinstance(f["uv"], tuple) for f in c["uv"].values()):
                new.append(c)        # (added trim boxes carry palette placeholders: leave them alone)
                continue
            kw = dict(step=step, R=R, min_dim=min_dim)
            if rule:
                r = rule(b, c)
                if r is None:
                    new.append(c)
                    continue
                kw.update(r)
            new.extend(bevel_cube(c, occ, **kw))
        if "cubes" in b:
            b["cubes"] = new


# ------------------------------------------------------------------ small helpers
def faces_of(m):
    return hpkit.stats(hpkit.finish_geo(m))[1]


def shade(rgb, k):
    return tuple(max(0, min(255, int(v * k))) for v in rgb)


def face_rgb(m, bone, idx, face="north"):
    c = m.bone(bone)["cubes"][idx]
    f = c["uv"][face]
    u, v = f["uv"]
    w, h = f["uv_size"]
    return m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))


def pick(m, bone, pred, face=None):
    """(index, cube) of the first cube of `bone` for which pred(cube) holds"""
    for i, c in enumerate(m.bone(bone)["cubes"]):
        if pred(c):
            return i, c
    raise KeyError(bone)


def colour_of(m, c, prefer=("north", "up", "south", "east", "west", "down")):
    for d in prefer:
        if d in c["uv"]:
            f = c["uv"][d]
            u, v = f["uv"]
            w, h = f["uv_size"]
            return m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))
    return (128, 128, 128)


def count_glow(glow):
    return 0 if glow is None else int((glow[..., 3] > 0).sum())


# ------------------------------------------------------------------ decoration helpers (flat colour, 0.25 ok)
def cubes_of(m, bones):
    for b in m.g["bones"]:
        if bones is None or b["name"] in bones:
            for c in b.get("cubes", []):
                yield b["name"], c


def top_y(m, bones, x, z):
    """highest +y surface of `bones` over the column (x, z); None when empty"""
    best = None
    for _, c in cubes_of(m, bones):
        o, s = c["origin"], c["size"]
        if o[0] - 1e-6 <= x < o[0] + s[0] and o[2] - 1e-6 <= z < o[2] + s[2]:
            t = o[1] + s[1]
            best = t if best is None or t > best else best
    return best


def box(m, bone, o, s, rgb, glow=False, safe=True, faces=DIRS, stick=None):
    """flat-colour box.  stick = the direction of the face that is glued to the surface: it is left out only when the
    cells just outside that face are really inside other boxes of the same bone (so it can never open up)"""
    o_, s_ = np.array(o, float), np.array(s, float)
    if stick and stick in faces:
        ax, sg = NORMAL[stick]
        oa = [a for a in range(3) if a != ax]
        pts = []
        for fa, fb in ((0.5, 0.5), (0.1, 0.1), (0.9, 0.1), (0.1, 0.9), (0.9, 0.9)):
            p = o_.copy()
            p[oa[0]] = o_[oa[0]] + s_[oa[0]] * fa
            p[oa[1]] = o_[oa[1]] + s_[oa[1]] * fb
            p[ax] = (o_[ax] + s_[ax] + 0.05) if sg > 0 else (o_[ax] - 0.05)
            pts.append(p)
        cs = [c for c in m.bone(bone)["cubes"] if "rotation" not in c]
        covered = all(any(((np.array(c["origin"]) - 1e-6 <= p) & (p <= np.add(c["origin"], c["size"]) + 1e-6)).all() for c in cs)
                      for p in pts)
        if covered:
            faces = tuple(d for d in faces if d != stick)
    return m.add_box(bone, o, s, rgb=rgb, glow=glow, safe=safe, faces=faces)


def frame(m, bone, lo, hi, axis, side, rgb, t=0.25, w=0.5, stick=None):
    """raised frame (rim) of width w and height |t| around the rectangle lo..hi lying in the plane `axis` = side.
    t > 0 stands on the surface towards +axis, t < 0 towards -axis."""
    a, b = [i for i in range(3) if i != axis]
    n = 0
    for (la, ha, lb, hb) in ((lo[a], hi[a], lo[b], lo[b] + w), (lo[a], hi[a], hi[b] - w, hi[b]),
                             (lo[a], lo[a] + w, lo[b] + w, hi[b] - w), (hi[a] - w, hi[a], lo[b] + w, hi[b] - w)):
        o, s = [0, 0, 0], [0, 0, 0]
        o[a], s[a], o[b], s[b] = la, ha - la, lb, hb - lb
        o[axis], s[axis] = (side, t) if t > 0 else (side + t, -t)
        if box(m, bone, o, s, rgb, stick=stick):
            n += 1
    return n


def spend(m, steps, budget=BUDGET):
    """apply steps (callables on a model) in order; a step that would push the faces over the budget is undone"""
    for st in steps:
        trial = m.copy()
        st(trial)
        if faces_of(trial) <= budget:
            m.geo, m.tex, m.glow = trial.geo, trial.tex, trial.glow
            m._palette, m._extra = trial._palette, trial._extra
        else:
            print(f"   skipped {getattr(st, '__name__', 'step')}: {faces_of(trial)} > {budget}")
    return m


# ------------------------------------------------------------------ variants
BUILD = {}      # name -> fn(variant) -> Model       (variant in "A", "B", "C")
RECOMMEND = {}  # name -> variant installed into the game
VIEW_SET = {}   # name -> (views, crop (yaw, pitch, box_frac))


def register(name):
    def deco(fn):
        BUILD[name] = fn
        return fn
    return deco


def build(name, variant):
    if variant == "now":
        return load(name)
    return finish(BUILD[name](variant), name)


def finish(m, name, samples=36):
    """last safety net: any box side that has no face and that a pose (rest included) leaves uncovered gets a face
    again (flat colour of the box's own first face).  The dropped faces are all supposed to be safe already, so this
    normally adds a handful of faces at most."""
    names = [b["name"] for b in m.g["bones"]]
    states = [()]
    if any(b.startswith("flare_") for b in names):
        states = [tuple(b for b in names if b.startswith("flare_")), tuple(b for b in names if b.startswith("crust_"))]
    added = 0
    for hide in states:
        need = {(bn, o, d) for lst in pose_holes(name, m.geo, hide=hide, samples=samples).values() for bn, o, d in lst}
        if not need:
            continue
        for b in m.g["bones"]:
            for c in b.get("cubes", []):
                for d in DIRS:
                    if d not in c["uv"] and (b["name"], tuple(c["origin"]), d) in need:
                        src = next((f for f in c["uv"].values()), None)
                        if src is None:
                            continue
                        if isinstance(src["uv"], tuple):
                            c["uv"][d] = {"uv": src["uv"], "uv_size": src["uv_size"]}
                        else:
                            uv, uvs = hpkit._texel_of((src["uv"][0], src["uv"][1], src["uv_size"][0], src["uv_size"][1]))
                            c["uv"][d] = {"uv": uv, "uv_size": uvs}
                        added += 1
    if added:
        print(f"   {name}: finish put back {added} faces ({faces_of(m)} faces)")
    return m


def finalize_all(m, name):
    """-> (geo, {file name: image array}): main texture, glowmask and the sibling textures, all with the new height"""
    geo, tex, glow = m.finalize()
    H0 = m.tex.shape[0]
    H1 = tex.shape[0]
    out = {name: tex, name + "_glowmask": glow if glow is not None else np.zeros_like(tex)}
    for sib in SIBLINGS.get(name, []):
        for suffix in ("", "_glowmask"):
            p = (ASSETS / "textures/entity" if _SRC == "assets" else BASE) / f"{sib}{suffix}.png"
            if not p.exists():
                continue
            a = np.array(Image.open(p).convert("RGBA"))
            b = np.zeros((H1, a.shape[1], 4), np.uint8)
            b[:a.shape[0]] = a
            if H1 > H0 and not sib.endswith("_charge"):
                b[H0:] = out[name + "_glowmask"][H0:] if suffix == "_glowmask" else tex[H0:]
            out[sib + suffix] = b
    return geo, out


def write_assets(name, m, out_dir=None):
    geo, imgs = finalize_all(m, name)
    if out_dir is None:
        gdir, tdir = ASSETS / "geo/entity", ASSETS / "textures/entity"
    else:
        gdir = tdir = Path(out_dir)
        Path(out_dir).mkdir(parents=True, exist_ok=True)
    (gdir / f"{name}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    for k, a in imgs.items():
        Image.fromarray(a, "RGBA").save(tdir / f"{k}.png")
    return geo, imgs


def install_mob(name, variant=None):
    """called by the install scripts right after they wrote the raw model: replace geo + textures with the chosen
    high-poly variant (built from the raw files that were just written, so the install stays idempotent)."""
    global _SRC
    variant = variant or RECOMMEND[name]
    if variant == "now":
        return None
    _SRC = "assets"
    try:
        m = finish(BUILD[name](variant), name)
    finally:
        _SRC = "base"
    geo, imgs = write_assets(name, m)
    n, q, mn, bn = hpkit.stats(geo)
    ov = hpkit.overlaps(geo)
    gl = count_glow(imgs[name + "_glowmask"])
    names = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
    states = [()]
    if any(b.startswith("flare_") for b in names):
        states = [tuple(b for b in names if b.startswith("flare_")), tuple(b for b in names if b.startswith("crust_"))]
    holes = 0
    for hide in states:
        for lst in pose_holes(name, geo, hide=hide, samples=36).values():
            holes += len(lst)
    print(f"high-poly {name} {variant}: {n} boxes / {q} quads, min {mn}, overlaps {len(ov)}, glow px {gl}, "
          f"faces that open in animation poses {holes}")
    assert not ov, ov[:5]
    assert holes == 0, "a dropped face opens up in an animation pose"
    assert q <= BUDGET, q
    assert gl > 0, "no glow pixel"
    return geo


_SRC = "base"


def L(name):
    """the raw model to start a variant from"""
    return load(name, _SRC)


# ------------------------------------------------------------------ sheets
def zoom_shot(faces, aabb, yaw, pitch, size=(330, 330)):
    """render only the faces whose centre lies in aabb ((x0, y0, z0), (x1, y1, z1), file coordinates) -> auto zoom"""
    a0, a1 = np.array(aabb[0], float), np.array(aabb[1], float)
    lo, hi = np.array([-a1[0], a0[1], -a1[2]]), np.array([-a0[0], a1[1], -a0[2]])   # faces carry the root's 180 deg yaw
    keep = []
    for f in faces:
        c = f.origin + f.eu / 2 + f.ev / 2
        if (c >= lo - 1e-6).all() and (c <= hi + 1e-6).all():
            keep.append(f)
    return hp_view.shot(keep, yaw, pitch, size)


def render_sheet(name, save_assets=True, variants=("now", "A", "B", "C")):
    cols, notes = [], []
    views, zoom = VIEW_SET[name]
    for v in variants:
        m = build(name, v)
        geo = hpkit.finish_geo(m)
        n, q, mn, bn = hpkit.stats(geo)
        ov = hpkit.overlaps(geo, detail=True)
        gl = count_glow(m.finalize()[2])
        notes.append(f"{v}: {n}/{q}")
        names = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
        states = [()]
        if any(b.startswith("flare_") for b in names):
            states = [tuple(b for b in names if b.startswith("flare_")), tuple(b for b in names if b.startswith("crust_"))]
        holes = sum(len(x) for hide in states for x in pose_holes(name, geo, hide=hide, samples=9).values())
        print(name, v, n, q, "min", mn, "overlaps", len(ov), "glow", gl, "opening faces", holes, flush=True)
        for o in ov[:6]:
            print("   ", o)
        f = m.faces()
        row = [hp_view.shot(f, y, p, (330, 330)) for y, p in views]
        row.append(zoom_shot(f, zoom[0], zoom[1], zoom[2]))
        cols.append((f"{v} ({n} boxes / {q} faces)" if v != "now" else f"now ({n}/{q})", row))
        if save_assets and v != "now":
            write_assets(name, m, out_dir=OUT / "assets" / f"{name}_{v}")
    OUT.mkdir(parents=True, exist_ok=True)
    voxel.sheet(f"{name} high-poly options", cols, note="; ".join(notes)).save(OUT / f"{name}.png")


def main(argv):
    names = [a for a in argv if a in BUILD] or list(BUILD)
    for n in names:
        render_sheet(n)


# ================================================================== shared model helpers
def cells(m, bones, grid=0.5):
    """{(ix, iz): top y} of the columns covered by `bones` on a `grid` lattice"""
    out = {}
    for _, c in cubes_of(m, bones):
        o, s = c["origin"], c["size"]
        for ix in range(int(round(o[0] / grid)), int(round((o[0] + s[0]) / grid))):
            for iz in range(int(round(o[2] / grid)), int(round((o[2] + s[2]) / grid))):
                t = o[1] + s[1]
                if (ix, iz) not in out or t > out[(ix, iz)]:
                    out[(ix, iz)] = t
    return out


def veins(m, bone, root, angles, r0, r1, rgb, w=0.5, h=0.25):
    """thin raised lines on the top surface of `bone` radiating from root (x, z) at the given angles (degrees)"""
    import math
    seen = set()
    n = 0
    for a in angles:
        r = r0
        while r <= r1:
            x = root[0] + r * math.cos(math.radians(a))
            z = root[1] + r * math.sin(math.radians(a))
            ix, iz = int(math.floor(x / w)), int(math.floor(z / w))
            if (ix, iz) not in seen:
                seen.add((ix, iz))
                y = top_y(m, [bone], ix * w + w / 2, iz * w + w / 2)
                if y is not None and box(m, bone, (ix * w, y, iz * w), (w, h, w), rgb, faces=("up", "north", "south", "east", "west")):
                    n += 1
            r += w * 0.7
    return n


def fringe(m, bone, rgb, every=2, depth=0.25, grid=0.5):
    """small teeth on the free rim of a flat part (wing): one tooth every `every` rim cells, stuck to the rim"""
    cl = cells(m, [bone], grid)
    n = 0
    for (ix, iz), y in sorted(cl.items()):
        for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
            if (ix + dx, iz + dz) in cl:
                continue
            if (ix * 3 + iz * 5 + k) % every:
                continue
            x0, z0 = ix * grid, iz * grid
            if dx:
                o = (x0 + grid if dx > 0 else x0 - depth, y - 0.25, z0 + 0.125)
                s = (depth, 0.25, grid - 0.25)
            else:
                o = (x0 + 0.125, y - 0.25, z0 + grid if dz > 0 else z0 - depth)
                s = (grid - 0.25, 0.25, depth)
            if box(m, bone, o, s, rgb):
                n += 1
    return n


def barbs(m, bone, pred, rgb, depth=0.25, axes=(0,), h=0.25):
    """feathery barbs on both sides of the thin cubes of `bone` for which pred(cube) holds"""
    n = 0
    for c in list(m.bone(bone)["cubes"]):
        if not pred(c) or "rotation" in c:
            continue
        o, s = c["origin"], c["size"]
        for ax in axes:
            oo = list(o)
            ss = [min(h, s[1]) if i == 1 else s[i] for i in range(3)]
            ss[ax] = depth
            for side in (-1, 1):
                p = list(o)
                p[ax] = o[ax] + s[ax] if side > 0 else o[ax] - depth
                p[1] = o[1] + (s[1] - ss[1]) / 2
                if box(m, bone, p, ss, rgb):
                    n += 1
    return n


# ================================================================== static_moth
VIEW_SET["static_moth"] = ([(180, 10), (215, 20), (35, 28)], (((-4, 2, 2.5), (4, 12, 8)), 195, 12))


@register("static_moth")
def static_moth(v):
    n = "static_moth"
    m = start(n)
    pale = (176, 188, 212)
    if v == "A":      # chamfer only
        bevel_all(m, step=0.5, R=1, min_dim=1.0)
        return m
    if v == "B":
        bevel_all(m, step=0.25, R=1, min_dim=1.0)
    else:   # C: soft wing rims (0.5) so the budget goes to the details; body / head at 0.25
        bevel_all(m, step=0.5, R=1, min_dim=1.0, bones=lambda b: b.startswith("wing"))
        bevel_all(m, step=0.25, R=1, min_dim=1.0, bones=lambda b: not b.startswith("wing"))

    def vein(t):
        for bone, sx in (("wing_l", 1), ("wing_r", -1)):
            veins(t, bone, (1.5 * sx, 0.0), [(a if sx > 0 else 180 - a) for a in (-50, 0, 50)], 1.0, 7.0, pale)

    def fur(t):
        for z in np.arange(-2.5, 3.5, 0.5):
            y = top_y(t, ["body"], 0.0, z + 0.25)
            if y:
                box(t, "body", (-0.25, y, z), (0.5, 0.25, 0.5), (200, 210, 230))

    def ant(t):
        barbs(t, "antennae", lambda c: c["size"][0] <= 0.5 and c["size"][2] <= 0.5 and c["origin"][1] >= 8.5, (226, 215, 193))

    def legs(t):
        for z in (-2.5, -1.0, 0.5):
            for sx in (-1, 1):
                xx = 0.5 if sx > 0 else -1.0
                box(t, "body", (xx, 3.25, z), (0.5, 0.75, 0.5), (86, 93, 112))
                box(t, "body", (xx + (0.5 if sx > 0 else -0.25), 2.5, z + 0.125), (0.25, 0.75, 0.25), (70, 76, 95))

    def probo(t):
        for (o, s_) in (((-0.25, 5.0, 5.5), (0.5, 0.5, 0.75)), ((-0.25, 4.5, 6.0), (0.5, 0.5, 0.25)), ((-0.25, 4.25, 5.5), (0.5, 0.25, 0.75))):
            box(t, "body", o, s_, (216, 206, 184))

    def rim(t):
        for bone in ("wing_l", "wing_r"):
            fringe(t, bone, (150, 160, 182), every=2)

    steps = [fur, ant, rim] if v == "B" else [fur, ant, rim, probo, legs, vein]
    spend(m, steps)
    return m


RECOMMEND["static_moth"] = "C"


# ------------------------------------------------------------------ remesh a noisy part at a coarser grid
def _texel_uv(cubes, p, d):
    """uv (one texel, centre-sampled) of the original cubes' face `d` at the surface point p (None if no cube there)"""
    best = None
    for c in cubes:
        o, s = np.array(c["origin"], float), np.array(c["size"], float)
        if ((p >= o - 1e-6) & (p <= o + s + 1e-6)).all():
            rect = box_face_uvs(c)
            dd = d if d in rect else next(iter(rect), None)
            if dd is None:
                continue
            q = p.copy()
            e = 0.125
            so, ss = q - e, np.full(3, 2 * e)
            so = np.maximum(so, o)
            ss = np.minimum(so + ss, o + s) - so
            ss = np.where(ss <= 0, 1e-3, ss)
            try:
                uv, uvs = hpkit.sub_uv(o, s, dd, rect[dd], so, ss)
            except Exception:
                continue
            on = np.isclose(p[NORMAL[d][0]], o[NORMAL[d][0]] + (s[NORMAL[d][0]] if NORMAL[d][1] > 0 else 0), atol=1e-3)
            if dd == d and on:
                return uv, uvs
            best = best or (uv, uvs)
    return best


def remesh(m, bone, f=(2, 1, 2), thresh=0.5):
    """Re-voxelise `bone` at a grid f * 0.5 (f per axis) by majority, greedy-mesh it again and sample each new face's
    colour from the original texel under it (flat colour per face).  Fewer, bigger boxes: use it on noisy parts that
    are cloth / feathers, then bevel."""
    b = m.bone(bone)
    cubes = [c for c in b["cubes"] if "rotation" not in c]
    lo = np.floor(np.min([c["origin"] for c in cubes], axis=0) / 0.5) * 0.5
    hi = np.ceil(np.max([np.add(c["origin"], c["size"]) for c in cubes], axis=0) / 0.5) * 0.5
    n = [int(round((hi[a] - lo[a]) / 0.5)) for a in range(3)]
    n = [int(np.ceil(n[a] / f[a]) * f[a]) for a in range(3)]
    base = np.zeros(n, bool)
    for c in cubes:
        i0 = [int(round((c["origin"][a] - lo[a]) / 0.5)) for a in range(3)]
        i1 = [int(round((c["origin"][a] + c["size"][a] - lo[a]) / 0.5)) for a in range(3)]
        base[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = True
    cn = [n[a] // f[a] for a in range(3)]
    filled = base.reshape(cn[0], f[0], cn[1], f[1], cn[2], f[2]).mean(axis=(1, 3, 5)) >= thresh
    cuts = [[float(lo[a] + 0.5 * f[a] * i) for i in range(cn[a] + 1)] for a in range(3)]
    dummy = {"origin": list(lo), "size": [1, 1, 1], "uv": {}}
    rect = {d: (0.0, 0.0, 1.0, 1.0) for d in DIRS}
    size = [cuts[a][-1] - cuts[a][0] for a in range(3)]
    new = _assemble(dummy, np.array(lo, float), np.array(size, float), rect, rect["north"], cuts, filled, merge=True)
    keep = []
    for nc in new:
        o, s = np.array(nc["origin"]), np.array(nc["size"])
        for d in list(nc["uv"].keys()):
            ax, sg = NORMAL[d]
            oa = [a for a in range(3) if a != ax]
            # sample point: centre of the face, pushed a little inside the box
            pnt = o + s / 2
            pnt[ax] = (o[ax] + s[ax] - 0.01) if sg > 0 else (o[ax] + 0.01)
            r = _texel_uv(cubes, pnt, d)
            if r is None:
                # nothing under it (the box grew over empty cells): look a bit deeper
                for k in (0.25, 0.5, 1.0):
                    pp = pnt.copy()
                    pp[ax] += (-k if sg > 0 else k)
                    r = _texel_uv(cubes, pp, d)
                    if r:
                        break
            if r is None:
                del nc["uv"][d]
                continue
            nc["uv"][d] = {"uv": r[0], "uv_size": r[1]}
        keep.append(nc)
    b["cubes"] = keep + [c for c in b["cubes"] if "rotation" in c]
    return len(keep)


# ------------------------------------------------------------------ animation check: faces we dropped must stay covered
class _Math:
    sin = staticmethod(lambda d: __import__("math").sin(__import__("math").radians(d)))
    cos = staticmethod(lambda d: __import__("math").cos(__import__("math").radians(d)))
    abs = staticmethod(abs)


class _Q:
    def __init__(self, t):
        self.anim_time = t


def _chan(val, t):
    if isinstance(val, dict):
        ks = sorted(val, key=float)
        vec = lambda k: np.array(val[k]["vector"] if isinstance(val[k], dict) else val[k], float)      # noqa: E731
        if t <= float(ks[0]):
            return vec(ks[0])
        for k0, k1 in zip(ks, ks[1:]):
            if float(k0) <= t <= float(k1):
                u = (t - float(k0)) / (float(k1) - float(k0)) if float(k1) > float(k0) else 0
                return vec(k0) * (1 - u) + vec(k1) * u
        return vec(ks[-1])
    env = {"math": _Math, "query": _Q(t)}
    return np.array([float(eval(str(e), {}, env)) for e in val])


def pose_holes(name, geo, hide=(), samples=9, only=None):
    """For every face the high-poly pass left out (a box side with no uv), check over the animation poses that the
    outside of the face is still inside some other box.  Returns {animation: [(bone, origin, dir), ...]} of the
    faces that open up (empty lists = fine).  Pose = rest rotation + animation channel, like GeckoLib (file angles)."""
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}
    anim = json.loads((ASSETS / "animations/entity" / f"{name}.animation.json").read_text(encoding="utf-8"))["animations"]
    sign = np.array([-1.0, 1.0, -1.0])
    vox = voxel

    def transforms(pose):
        cache = {}
        zero3, one3 = np.zeros(3), np.ones(3)

        def world(n):
            if n in cache:
                return cache[n]
            b = bones[n]
            piv = np.array(b.get("pivot", [0, 0, 0]), float)
            ar, ap, asc = pose.get(n, (zero3, zero3, one3))
            rot = np.array(b.get("rotation", [0, 0, 0]), float) + ar
            R = vox._rot(*(rot * sign)) @ np.diag(asc)
            M = (R, piv - R @ piv + ap)
            if "parent" in b:
                PR, Pt = world(b["parent"])
                M = (PR @ M[0], PR @ M[1] + Pt)
            cache[n] = M
            return M
        return world

    live = [(b["name"], c) for b in g["bones"] if b["name"] not in hide for c in b.get("cubes", [])]
    # the sample points just outside each missing face (bone-local)
    pts = []     # (cube index, dir, local points (5, 3))
    for i, (bn, c) in enumerate(live):
        o, s_ = np.array(c["origin"], float), np.array(c["size"], float)
        for d in DIRS:
            if d in c["uv"] or (only is not None and (bn, tuple(c["origin"]), d) not in only):
                continue
            ax, sg = NORMAL[d]
            oa = [a for a in range(3) if a != ax]
            P = []
            for fa, fb in ((0.5, 0.5), (0.15, 0.15), (0.85, 0.15), (0.15, 0.85), (0.85, 0.85)):
                p = o.copy()
                p[oa[0]] = o[oa[0]] + s_[oa[0]] * fa
                p[oa[1]] = o[oa[1]] + s_[oa[1]] * fb
                p[ax] = (o[ax] + s_[ax] + 0.05) if sg > 0 else (o[ax] - 0.05)
                P.append(p)
            pts.append((i, d, np.array(P)))
    los = np.array([c["origin"] for _, c in live], float)
    his = np.array([np.add(c["origin"], c["size"]) for _, c in live], float)
    result = {}
    own = np.array([i for i, _, _ in pts for _ in range(5)], int)
    local = np.concatenate([P for _, _, P in pts]) if pts else np.zeros((0, 3))
    for an, spec in sorted(anim.items()) + [("rest", {"animation_length": 1.0, "bones": {}})]:
        ln = float(spec.get("animation_length", 1.0))
        worst = []
        for k in range(samples):
            t = ln * k / samples
            pose = {}
            for bn, ch in spec.get("bones", {}).items():
                r = _chan(ch["rotation"], t) if "rotation" in ch else np.zeros(3)
                p_ = _chan(ch["position"], t) if "position" in ch else np.zeros(3)
                sc = _chan(ch["scale"], t) if "scale" in ch else np.ones(3)
                pose[bn] = (r, p_, sc)
            world = transforms(pose)
            Rs = [world(bn) for bn, _ in live]
            Ri = [np.linalg.inv(R_) for R_, _ in Rs]
            W = np.empty_like(local)
            for i in set(own.tolist()):
                m_ = own == i
                R, t0 = Rs[i]
                W[m_] = local[m_] @ R.T + t0
            cov = np.zeros(len(W), bool)
            for j in range(len(live)):
                Rj, tj = Rs[j]
                Lc = (W - tj) @ Ri[j].T
                inside = ((Lc >= los[j] - 1e-4) & (Lc <= his[j] + 1e-4)).all(axis=1) & (own != j)
                cov |= inside
            bad = []
            for fi, (i, d, _) in enumerate(pts):
                if not cov[fi * 5:fi * 5 + 5].all():
                    bad.append((live[i][0], tuple(live[i][1]["origin"]), d))
            if len(bad) > len(worst):
                worst = bad
        result[an] = worst
    return result


def repair_pruned(m, name, hide=(), samples=36):
    """put back every pruned face that opens up in some animation pose (pose_holes); returns (restored, kept)"""
    only = {(bn, tuple(c["origin"]), d) for bn, c, d, _ in m._pruned}
    holes = pose_holes(name, m.geo, hide=hide, samples=samples, only=only)
    need = {(bn, o, d) for lst in holes.values() for bn, o, d in lst}
    restored = 0
    for bn, c, d, uv in m._pruned:
        if (bn, tuple(c["origin"]), d) in need and d not in c["uv"]:
            c["uv"][d] = uv
            restored += 1
    kept = len(m._pruned) - restored
    m._pruned = [(bn, c, d, uv) for bn, c, d, uv in m._pruned if d not in c["uv"]]
    return restored, kept


def prepare(m, name):
    """prune the covered faces, then put back the ones that an animation pose would open (per visible-bone state)"""
    n0 = faces_of(m)
    prune(m, name)
    names = [b["name"] for b in m.g["bones"]]
    states = [()]
    if any(b.startswith("flare_") for b in names):
        states = [tuple(b for b in names if b.startswith("flare_")), tuple(b for b in names if b.startswith("crust_"))]
    for hide in states:
        repair_pruned(m, name, hide=hide)
    print(f"   {name}: {n0} -> {faces_of(m)} faces after prune+repair")
    return m


SIMPLIFY = {}     # name -> fn(m): cut the noisy voxel parts into bigger boxes before the faces are pruned
_START = {}


def start(name):
    """the raw model -> simplified -> pruned (pose-checked); memoised per process"""
    key = (name, _SRC)
    if key not in _START:
        m = L(name)
        if name in SIMPLIFY:
            SIMPLIFY[name](m)
        prepare(m, name)
        _START[key] = m.copy()
    return _START[key].copy()


def mir(x, w):
    """mirror an x interval across x = 0"""
    return -x - w


def both(t, fn):
    """call fn(t, sx) for the two sides (sx = +1 left / -1 right)"""
    fn(t, 1)
    fn(t, -1)


# ================================================================== feather_warden
VIEW_SET["feather_warden"] = ([(180, 10), (215, 20), (35, 20)], (((-6, 24, -5), (6, 38, 5)), 200, 12))


def _big_rule(b, c):
    s = sorted(c["size"])
    if s[1] >= 4 and s[0] >= 1.5:
        return dict(R=2)
    if s[1] >= 2:
        return dict(R=1)
    return None


def no(d):
    """all faces but d (the one stuck to the surface)"""
    return tuple(x for x in DIRS if x != d)


def _warden_simplify(m):
    remesh(m, "body", f=(2, 1, 2))


SIMPLIFY["feather_warden"] = _warden_simplify


def surf(m, bones, axis, sign, point, span=0.0):
    """outermost surface coordinate on `axis` (sign +1: max, -1: min) of `bones` over the point (the other two axes)"""
    oa = [a for a in range(3) if a != axis]
    best = None
    for _, c in cubes_of(m, bones):
        o, s_ = c["origin"], c["size"]
        if all(o[oa[i]] - 1e-6 <= point[i] <= o[oa[i]] + s_[oa[i]] + 1e-6 for i in range(2)):
            v = o[axis] + s_[axis] if sign > 0 else o[axis]
            if best is None or (v > best if sign > 0 else v < best):
                best = v
    return best


@register("feather_warden")
def feather_warden(v):
    n = "feather_warden"
    m = start(n)
    orange, light = (190, 108, 68), (227, 150, 100)
    steel, dark = (122, 126, 138), (36, 40, 54)

    def arms(t):
        bevel_all(t, step=0.5, R=1, min_dim=2.0, bones=lambda b: b in ("arm_r", "arm_l"))

    def forearms(t):
        bevel_all(t, step=0.5, R=1, min_dim=2.0, bones=lambda b: b.startswith("forearm"))

    def legs(t):
        bevel_all(t, step=0.5, R=1, min_dim=2.0, bones=lambda b: b.startswith("leg"))

    def legs2(t):
        bevel_all(t, step=0.5, R=2, min_dim=2.0, rule=_big_rule, bones=lambda b: b.startswith("leg"))

    def plate(t):
        zf = surf(t, ["body"], 2, 1, (0.0, 19.0))
        frame(t, "body", (-2.75, 16.75, zf), (2.75, 21.75, zf), 2, zf, light, t=0.25, w=0.5, stick="north")
        for x in (-2.5, 2.0):
            for y in (17.0, 21.0):
                box(t, "body", (x, y, zf + 0.25), (0.5, 0.5, 0.25), steel, stick="north")
        zb = surf(t, ["body"], 2, 1, (0.0, 16.0)) or zf
        box(t, "body", (-0.5, 15.5, zb), (1.0, 1.0, 0.25), steel, stick="north")     # belt buckle

    def torso(t):
        bevel_all(t, step=0.5, R=1, min_dim=2.0, bones=lambda b: b == "body")

    def pauldron(t):
        def f(t, sx):
            bone = "arm_l" if sx > 0 else "arm_r"
            x0 = 8.5 if sx > 0 else mir(8.5, 1.5)
            for z in (-1.5, 0.0, 1.5):
                box(t, bone, (x0 + 0.25, 25.5, z - 0.25), (1.0, 0.5, 0.5), steel, stick="down")
        both(t, f)

    def knees(t):
        def f(t, sx):
            x0 = 0.5 if sx > 0 else mir(0.5, 3.5)
            bone = "leg_l" if sx > 0 else "leg_r"
            box(t, bone, (x0, 6.0, 2.5), (3.5, 1.5, 0.25), steel, stick="north")
        both(t, f)

    def spikes(t):
        def f(t, sx):
            bone = "arm_l" if sx > 0 else "arm_r"
            for z, h in ((-1.5, 1.5), (0.0, 2.0), (1.5, 1.5)):
                x = 8.75 if sx > 0 else mir(8.75, 0.75)
                box(t, bone, (x, 26.0, z - 0.25), (0.75, h - 0.5, 0.5), (176, 182, 196), stick="down")
                box(t, bone, (x + 0.25, 26.0 + h - 0.5, z - 0.125), (0.25, 0.75, 0.25), (210, 215, 225), stick="down")
        both(t, f)

    def claws(t):
        for bone, xs, z0 in (("forearm_r", (-7.5, -6.75, -6.0, -5.5), 5.5), ("forearm_l", (5.75, 6.5, 7.25), 3.0)):
            for x in xs:
                box(t, bone, (x, 11.25, z0), (0.25, 0.5, 1.0), (200, 205, 215), stick="north")
                box(t, bone, (x, 11.25, z0 + 1.0), (0.25, 0.25, 0.5), (232, 235, 242), stick="north")

    steps = {"A": [torso, legs, arms, forearms],
             "B": [plate, torso, legs, arms, pauldron],
             "C": [plate, claws, pauldron, spikes, knees, torso, legs, arms]}[v]
    spend(m, steps)
    return m


RECOMMEND["feather_warden"] = "C"


# ================================================================== thunder_supplicant
VIEW_SET["thunder_supplicant"] = ([(180, 10), (215, 20), (35, 20)], (((-5, 16, -2), (5, 30, 7)), 200, 14))
CAGE_POSTS = ((-3.0, 1.5, 0.5, 1.0), (-1.5, -0.5, 0.5, 0.5), (1.0, -0.5, 0.5, 0.5), (2.5, 1.5, 0.5, 1.0), (-1.5, 4.0, 0.5, 0.5), (1.0, 4.0, 0.5, 0.5))


SIMPLIFY["thunder_supplicant"] = lambda m: remesh(m, "robe", f=(2, 1, 2))


@register("thunder_supplicant")
def thunder_supplicant(v):
    n = "thunder_supplicant"
    m = start(n)
    copper, gold = (141, 80, 50), (255, 226, 120)

    def robe_bev(t):
        bevel_all(t, step=0.5, R=1, min_dim=1.0, bones=lambda b: b == "robe")

    def arms(t):
        bevel_all(t, step=0.5, R=2, min_dim=2.0, bones=lambda b: b in ("arm_r", "arm_l"))

    def sleeves(t):
        bevel_all(t, step=0.5, R=1, min_dim=2.0, bones=lambda b: b.startswith("forearm"))

    def body(t):
        bevel_all(t, step=0.5, R=1, min_dim=2.0, bones=lambda b: b == "body")

    def finials(t):
        for x, z, w, d in CAGE_POSTS:
            box(t, "head", (x + w / 2 - 0.125, 28.5, z + d / 2 - 0.125), (0.25, 1.0, 0.25), (176, 110, 66), stick="down")
            box(t, "head", (x + w / 2 - 0.125, 29.5, z + d / 2 - 0.125), (0.25, 0.25, 0.25), gold, glow=True, stick="down")

    def feet(t):
        for x, z, w, d in CAGE_POSTS:
            box(t, "head", (x - 0.125, 22.0, z - 0.125), (w + 0.25, 0.5, d + 0.25), (108, 59, 37), stick="up")

    def cuffs(t):
        for bone, x0, x1, z1 in (("forearm_r", -6.0, -3.5, 5.5), ("forearm_l", 3.5, 6.0, 4.5)):
            frame(t, bone, (x0 - 0.25, 10.0, 0.25), (x1 + 0.25, 10.0, z1 + 0.25), 1, 10.0, (150, 140, 175), t=0.5, w=0.25)

    def fingers(t):
        for bone, x0, z0, nx in (("forearm_r", -5.5, 6.5, 3), ("forearm_l", 4.0, 5.5, 3)):
            for i in range(nx):
                box(t, bone, (x0 + 0.25 + i * 0.5, 9.5, z0 - 0.25), (0.25, 0.75, 0.25), (190, 196, 210), stick="up")
                box(t, bone, (x0 + 0.25 + i * 0.5, 9.25, z0), (0.25, 0.5, 0.25), (214, 218, 230), stick="up")

    def collar(t):
        for k in range(8):
            import math
            a = k * math.pi / 4
            x, z = 3.0 * math.cos(a), 2.0 + 3.0 * math.sin(a)
            box(t, "head", (x - 0.25, 22.0, z - 0.25), (0.5, 0.5, 0.5), (200, 130, 80), stick="down")

    def hem(t):
        # raised vertical seams down the front of the robe (front is +z)
        for x in (-3.0, -1.5, 0.0, 1.5, 3.0):
            for y0 in np.arange(1.0, 13.0, 3.0):
                zt = None
                for _, c in cubes_of(t, ["robe"]):
                    o, s_ = c["origin"], c["size"]
                    if o[0] <= x + 0.1 < o[0] + s_[0] and o[1] <= y0 + 0.1 < o[1] + s_[1]:
                        zz = o[2] + s_[2]
                        zt = zz if zt is None or zz > zt else zt
                if zt:
                    box(t, "robe", (x - 0.125, y0, zt), (0.25, 2.0, 0.25), (84, 74, 104), stick="north")

    steps = {"A": [robe_bev, arms, sleeves, body],
             "B": [robe_bev, arms, finials, cuffs, fingers, sleeves, collar],
             "C": [robe_bev, finials, feet, collar, cuffs, fingers, hem, arms, sleeves, body]}[v]
    spend(m, steps)
    return m


RECOMMEND["thunder_supplicant"] = "C"


# ================================================================== ash_thrall
# (budget is the geo total, crust + flare bones both counted, although the game shows only one of them at a time)
VIEW_SET["ash_thrall"] = ([(180, 10), (215, 20), (35, 20)], (((-6, 27, -5), (6, 40, 5)), 200, 12))


@register("ash_thrall")
def ash_thrall(v):
    n = "ash_thrall"
    m = start(n)

    def rocks(pred, step=0.5, R=1):
        def f(t):
            bevel_all(t, step=step, R=R, min_dim=1.0, bones=pred)
        f.__name__ = "rocks"
        return f

    def tips(t):
        for bone in ("flare_body", "flare_head"):
            for c in list(t.bone(bone)["cubes"]):
                o, s_ = c["origin"], c["size"]
                if s_[1] >= 1.0 and s_[0] <= 0.5 and s_[2] <= 0.5:
                    box(t, bone, (o[0] + 0.125, o[1] + s_[1], o[2] + 0.125), (0.25, 0.75, 0.25), (255, 242, 176), glow=True, stick="down")

    def brow(t):
        # raised brow ridge over the eyes + cheek studs on the hood (front is +z, hood front face z = 4)
        box(t, "head", (-2.5, 31.5, 4.0), (5.0, 0.5, 0.25), (79, 74, 70), stick="north")
        for x in (-3.5, 3.25):
            box(t, "head", (x, 30.5, 3.5), (0.25, 1.0, 0.5), (113, 107, 101), stick="north")

    crust_legs = lambda b: b.startswith("crust_leg")      # noqa: E731
    crust_body = lambda b: b in ("crust_body", "crust_arm_l", "crust_arm_r")      # noqa: E731
    crust_arms = lambda b: b.startswith(("crust_arm", "crust_fore"))      # noqa: E731
    steps = {"A": [rocks(crust_arms), rocks(crust_body), rocks(crust_legs)],
             "B": [tips, brow, rocks(crust_arms)],
             "C": [tips, brow, rocks(crust_arms), rocks(lambda b: b == "head"), rocks(crust_legs), rocks(crust_body)]}[v]
    spend(m, steps)
    return m


RECOMMEND["ash_thrall"] = "C"


# ================================================================== ember_moth
VIEW_SET["ember_moth"] = ([(180, 10), (215, 22), (35, 28)], (((-5, 4, 3), (5, 14, 11)), 195, 12))


def slice_bbox(m, bones, axis, lo, hi):
    """bbox (lo xyz, hi xyz) of the boxes of `bones` that overlap the slab lo..hi on `axis` (None if none)"""
    pts = [(c["origin"], np.add(c["origin"], c["size"])) for _, c in cubes_of(m, bones)
           if c["origin"][axis] < hi and c["origin"][axis] + c["size"][axis] > lo]
    if not pts:
        return None
    return np.min([p[0] for p in pts], axis=0), np.max([p[1] for p in pts], axis=0)


@register("ember_moth")
def ember_moth(v):
    n = "ember_moth"
    m = start(n)
    if v == "A":
        bevel_all(m, step=0.5, R=1, min_dim=1.0)
        return m
    if v == "B":
        bevel_all(m, step=0.25, R=1, min_dim=1.0, bones=lambda b: b in ("body", "head"))
        bevel_all(m, step=0.5, R=1, min_dim=1.0, bones=lambda b: b.startswith("wing"))
    else:
        bevel_all(m, step=0.25, R=1, min_dim=1.0, bones=lambda b: b in ("body", "head"))
        bevel_all(m, step=0.25, R=1, min_dim=1.0, bones=lambda b: b.startswith("wing"))
    ash, dark = (176, 176, 170), (60, 58, 56)

    def flakes(t):
        for bone in ("wing_l", "wing_r"):
            fringe(t, bone, (120, 118, 112), every=2)

    def bands(t):
        for z0 in (-8.0, -6.5, -5.0):
            bb = slice_bbox(t, ["body"], 2, z0, z0 + 0.5)
            if bb is None:
                continue
            lo, hi = bb[0].copy(), bb[1].copy()
            lo[:] = lo - 0.25
            hi[:] = hi + 0.25
            lo[2], hi[2] = z0, z0
            frame(t, "body", lo, hi, 2, z0, dark, t=0.5, w=0.25)

    def tufts(t):
        barbs(t, "head", lambda c: c["origin"][1] >= 10.0 and c["size"][0] <= 0.5 and c["size"][2] <= 0.5, ash)

    def legs(t):
        for z in (-3.5, -2.0, -0.5):
            for sx in (-1, 1):
                x = 1.25 if sx > 0 else -1.5
                box(t, "body", (x, 3.75, z), (0.25, 0.75, 0.25), (86, 60, 44), stick="up")
                box(t, "body", (x + 0.25 * sx, 3.0, z), (0.25, 0.75, 0.25), (70, 48, 36))

    def mand(t):
        # mandibles in front of the head (front is +z, head front face z = 9.5)
        for x in (-1.5, 1.25):
            box(t, "head", (x, 7.0, 9.5), (0.25, 0.25, 0.75), (84, 70, 60), stick="north")

    steps = {"B": [flakes, bands, tufts], "C": [bands, tufts, flakes, legs, mand]}[v]
    spend(m, steps)
    return m


RECOMMEND["ember_moth"] = "C"


# ================================================================== cinder_roller
VIEW_SET["cinder_roller"] = ([(180, 10), (215, 22), (35, 28)], (((-6, 0, 8), (6, 8, 16)), 195, 20))


@register("cinder_roller")
def cinder_roller(v):
    n = "cinder_roller"
    m = start(n)
    segs = [f"seg{i}" for i in range(9)]
    plate, spine_c = (76, 70, 84), (118, 110, 130)

    def shell(step, R):
        def f(t):
            bevel_all(t, step=step, R=R, min_dim=1.0, bones=lambda b: b in segs or b.startswith("mem"))
        f.__name__ = "shell"
        return f

    def spine(t):
        for sg in segs:
            bb = slice_bbox(t, [sg], 0, -0.25, 0.25)
            if bb is None:
                continue
            y = top_y(t, [sg], 0.0, 0.25) if False else None
            zs = sorted({round(c["origin"][2], 3) for _, c in cubes_of(t, [sg])})
            zlo = min(c["origin"][2] for _, c in cubes_of(t, [sg]))
            zhi = max(c["origin"][2] + c["size"][2] for _, c in cubes_of(t, [sg]))
            y = top_y(t, [sg], 0.0, (zlo + zhi) / 2)
            if y is None:
                continue
            box(t, sg, (-0.25, y, zlo + 0.25), (0.5, 0.25, zhi - zlo - 0.5), spine_c, stick="down")

    def toes(t):
        for b in list(t.g["bones"]):
            if not b["name"].startswith("leg"):
                continue
            for c in list(b.get("cubes", [])):
                o, s_ = c["origin"], c["size"]
                if o[1] <= 0.01:
                    box(t, b["name"], (o[0], 0.0, o[2] - 0.25), (s_[0], 0.5, 0.25), (110, 104, 120), stick="south")
                    box(t, b["name"], (o[0], 0.0, o[2] + s_[2]), (s_[0], 0.5, 0.25), (110, 104, 120), stick="north")

    def feel(t):
        for bone in ("antenna_r", "antenna_l"):
            barbs(t, bone, lambda c: c["size"][0] <= 0.5 and c["size"][2] <= 0.5, (118, 110, 130), axes=(1,))

    def tines(t):
        for x in (-2.0, -0.75, 0.5, 1.75):
            box(t, "uropods", (x, 1.5, -14.25), (0.25, 0.5, 0.75), (118, 110, 130), stick="south")

    def hot(t):
        # glowing seam line under each shell edge
        for sg in segs[1:8]:
            zlo = min(c["origin"][2] for _, c in cubes_of(t, [sg]))
            for sx in (-1, 1):
                xe = max(abs(c["origin"][0] + (c["size"][0] if sx > 0 else 0)) for _, c in cubes_of(t, [sg]) if (c["origin"][0] >= 0) == (sx > 0))
                box(t, sg, ((xe if sx > 0 else -xe - 0.25), 3.0, zlo + 0.25), (0.25, 1.5, 1.0), (255, 150, 40), glow=True, stick="north" if False else DIRS)

    steps = {"A": [shell(0.5, 1)],
             "B": [shell(0.5, 1), toes, feel],
             "C": [shell(0.5, 1), spine, toes, feel, tines]}[v]
    spend(m, steps)
    return m


RECOMMEND["cinder_roller"] = "C"


if __name__ == "__main__":
    main(sys.argv[1:])
