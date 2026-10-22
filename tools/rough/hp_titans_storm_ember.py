"""High-poly roughs for the two titan bodies of the sky chapters: thunder_wing (storm) and ember_titan (ember).
(docs/design/15_high_poly.md section 8, assignment 6b.  Near LOD only; the mid and far LODs are untouched.)

  A  face-heavy:   head (eyes, beak / mouth, horns), feet / claws
  B  wing / ridge: wing edge and flight feathers / lava crust of the ridge, chamfered big steps
  C  A + B inside the 12,000-face budget (design doc 15 section 9, item 5)

python tools/rough/hp_titans_storm_ember.py [thunder_wing|ember_titan]     -> docs/review/high_poly_roughs/<name>.png (+ assets/<name>_<A|B|C>/)
python tools/rough/hp_titans_storm_ember.py --install <name> [A|B|C]        -> writes the near geo + atlas into src/main/resources/assets
tools/install/install_thunder_wing.py / tools/install/install_ember_titan.py call install_titan() right after they wrote the raw model.

The back dimension bodies (tools/gen/items/gen_back_shape.py / tools/gen/items/gen_ember_back_body.py) read the *grid* of the install scripts (build_grid() /
tools/rough/ember_titan.py build), never the near geo, so nothing here can change them.  The shape, the proportions and the
colour layout are kept: chamfers are 0.5 unit (a quarter block) on free outer edges only, trims are small boxes.
hpkit.py is not touched; the helpers live in hp_mobs_storm_ember.py (prune, bevel, box, frame, pose_holes) and here.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import numpy as np
from PIL import Image

import hp_mobs_storm_ember as H
H.Q = 2   # 0.5-unit occupancy cells are enough here (the 0.25 grid of the mob script would need 4 GB for a titan)
H._Math.mod = staticmethod(lambda a, b: a % b if b else 0.0)   # ember_titan.animation.json uses math.mod (smoke loop)
import hp_view
import hpkit
import voxel
from hpkit import DIRS

ROOT = HERE.parents[1]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "docs/review/high_poly_roughs"
BASE = ROOT / "tools/sources/hp_base/entity"
BUDGET = 12000
NAMES = ["thunder_wing", "ember_titan"]
RECOMMEND = {"thunder_wing": "C", "ember_titan": "C"}
LODS = ("", "_mid", "_far")
_SRC = "base"


def load(name):
    if _SRC == "assets":
        return hpkit.Model.load(name)
    return hpkit.Model.load(name, geo_path=BASE / f"{name}.geo.json", tex_path=BASE / f"{name}.png",
                            glow_path=BASE / f"{name}_glowmask.png")


def shade(rgb, k):
    return tuple(max(0, min(255, int(v * k))) for v in rgb)


def put(m, bone, o, s, rgb, glow=False, faces=DIRS):
    return m.add_box(bone, o, s, rgb=rgb, glow=glow, faces=faces, safe=True)


def mirror(o, s):
    return (-(o[0] + s[0]), o[1], o[2]), s


def put2(m, bone_r, bone_l, o, s, rgb, **kw):
    """the box and its mirror image (bones may differ for left / right)"""
    put(m, bone_r, o, s, rgb, **kw)
    o2, s2 = mirror(o, s)
    put(m, bone_l, o2, s2, rgb, **kw)


def spend(m, steps, budget=BUDGET):
    for st in steps:
        trial = m.copy()
        st(trial)
        n = H.faces_of(trial)
        if n <= budget:
            m.geo, m.tex, m.glow = trial.geo, trial.tex, trial.glow
            m._palette, m._extra = trial._palette, trial._extra
            print(f"   {st.__name__}: {n}")
        else:
            print(f"   SKIPPED {st.__name__}: {n} > {budget}")
    return m


def bev(bones, **kw):
    """step factory: neighbour-aware chamfer on the given bones"""
    def run(m):
        k = dict(kw)
        axes = k.pop("axes", None)
        pred = k.pop("pred", None)
        rule = (lambda b, c: dict(axes=axes or "xyz") if (pred is None or pred(c)) else None) if (axes or pred) else None
        H.bevel_all(m, bones=lambda n: n in bones, rule=rule, **k)
    run.__name__ = "bevel_" + "_".join(sorted(bones))[:30]
    return run


# =============================================================================================== thunder wing
# game view: the head points to -Z, the bird's left is geo +X.  All coordinates are model (geo) units, 1 unit = 0.5 block.
def tw_head_trim(m):
    slate = (57, 63, 82)
    horn = (74, 58, 44)
    sand = (206, 196, 160)
    # culmen: a lighter ridge along the top of the beak (the beak steps up toward the face)
    for z, y in ((-124, 50), (-120, 54), (-116, 54), (-112, 58), (-108, 58)):
        put(m, "head", (-0.5, y, z), (1.0, 0.5, 4.0), shade(sand, 1.12), faces=("up", "north", "east", "west"))
    # cutting edge of the beak: thin light lips along the lower edge on both sides
    for sx in (-6.25, 6.0):
        put(m, "head", (sx, 46.0, -116.0), (0.25, 0.5, 8.0), shade(sand, 0.8))
        put(m, "head", (sx, 46.0, -108.0), (0.25, 0.5, 4.0), shade(sand, 0.8))
    # nostrils: two dark slits on the beak base
    for x in (-5.0, 3.5):
        put(m, "head", (x, 52.0, -116.25), (1.5, 1.0, 0.25), shade(horn, 0.5))
    # hooked tip: the beak's end bends down in two steps
    put(m, "head", (-1.5, 40.5, -123.5), (3.0, 1.5, 3.0), horn)
    put(m, "head", (-1.0, 39.5, -122.75), (2.0, 1.0, 2.0), shade(horn, 0.8))
    # brow ridge over the glowing eye on each side (the eye is the side face of the 28-wide slab, z -96..-92)
    for x in (-14.5, 14.0):
        put(m, "head", (x, 57.5, -97.0), (0.5, 1.0, 6.0), shade(slate, 0.55))
        put(m, "head", (x, 45.5, -96.5), (0.5, 0.5, 5.0), shade(slate, 0.55))
        put(m, "head", (x, 46.0, -97.0), (0.5, 12.0, 0.5), shade(slate, 0.6))
        put(m, "head", (x, 46.0, -91.5), (0.5, 12.0, 0.5), shade(slate, 0.6))
    # cheek feathers: short spikes sweeping back from the nape
    for sx in (-1, 1):
        for i, (y, z, l) in enumerate(((50, -88, 3.0), (54, -87, 2.5), (58, -86, 2.0))):
            xo = 13.5 if sx > 0 else -14.5
            put(m, "head", (xo, y, z), (1.0, 3.0, l), shade(slate, 1.25 - 0.1 * i))
            put(m, "head", (xo + 0.25, y + 0.5, z + l), (0.5, 2.0, 1.5), shade(slate, 1.45 - 0.1 * i))


def tw_feet(m):
    claw = (64, 58, 54)
    for bone, s in (("leg_l", 1), ("leg_r", -1)):
        xs = (7.0, 11.0, 15.0) if s > 0 else (-17.0, -13.0, -9.0)
        for x in xs:
            put(m, bone, (x, 18.0, 17.0), (2.0, 2.0, 3.0), claw)
            put(m, bone, (x + 0.25, 17.5, 15.5), (1.5, 1.5, 1.5), shade(claw, 0.85))
            put(m, bone, (x + 0.5, 17.0, 14.5), (1.0, 1.0, 1.0), shade(claw, 0.65))
        xb = 11.0 if s > 0 else -13.0                        # hind claw
        put(m, bone, (xb, 18.0, 44.0), (2.0, 2.0, 2.5), claw)
        put(m, bone, (xb + 0.5, 17.5, 46.0), (1.0, 1.5, 1.5), shade(claw, 0.75))


def tw_wing_tips(m):
    """flight-feather tips of both hands: a light tapered tip box on the free end of every long feather"""
    for bone in ("hand_l", "hand_r"):
        for c in list(m.bone(bone)["cubes"]):
            o, s = c["origin"], c["size"]
            if s[2] < 6 or s[0] > 2.5 or s[1] > 2.5 or "rotation" in c:
                continue
            f = c["uv"].get("north")
            if f is None or isinstance(f["uv"], tuple):
                continue
            col = m.color_at((f["uv"][0] + 0.2, f["uv"][1] + 0.2))
            put(m, bone, (o[0] + 0.5, o[1] + 0.5, o[2] - 1.0), (s[0] - 1.0, s[1] - 1.0, 1.0), shade(col, 1.1))


# =============================================================================================== ember titan
def et_head_trim(m):
    rock = (40, 30, 28)
    ash = (82, 82, 88)
    lava = (226, 120, 30)
    # brow plates over the two glowing eyes (eyes sit at x +-17..+-32, y 32..37, z -176..-171)
    for sx in (-1, 1):
        x0 = 17.0 if sx > 0 else -33.0
        put(m, "head", (x0, 36.0, -175.0), (16.0, 1.0, 1.0), shade(rock, 1.2))
        put(m, "head", (x0 + 1.0, 37.0, -175.0), (14.0, 0.5, 0.75), shade(ash, 0.8))
        put(m, "head", (x0 + 1.0, 31.5, -174.75), (14.0, 0.5, 0.75), shade(rock, 1.0))
    # horns: stepped spikes sweeping up and back from the rear corners of the skull, standing on whatever is highest there
    for sx in (-1, 1):
        x = 24.0 if sx > 0 else -28.0
        for z in (-146.0,):
            y0 = H.top_y(m, ["head"], x + 2, z + 1)
            if y0 is None:
                continue
            for i, (w, h, dz) in enumerate(((4.0, 3.0, 0.0), (3.0, 3.0, 1.5), (2.0, 3.0, 3.0), (1.0, 2.5, 4.5))):
                put(m, "head", (x + (4.0 - w) / 2, y0 + 3.0 * i, z + dz), (w, h, 3.0), shade(rock, 1.0 + 0.12 * i))
            put(m, "head", (x + 1.5, y0 + 9.0, z + 6.0), (1.0, 1.0, 2.0), lava, glow=True)


def et_claws(m):
    """three claws on the front of every paw (the paw = the boxes touching y = 0 of each leg)"""
    claw = (190, 184, 170)
    for bone in ("leg_fl", "leg_fr", "leg_rl", "leg_rr"):
        feet = [c for c in m.bone(bone)["cubes"] if c["origin"][1] <= 0.01 and "rotation" not in c]
        if not feet:
            continue
        zmin = min(c["origin"][2] for c in feet)
        front = [c for c in feet if c["origin"][2] <= zmin + 0.01]
        x0 = min(c["origin"][0] for c in front)
        x1 = max(c["origin"][0] + c["size"][0] for c in front)
        n = 3
        w = 2.5
        gap = (x1 - x0 - n * w) / (n + 1)
        for i in range(n):
            x = x0 + gap + i * (w + gap)
            put(m, bone, (x, 0.5, zmin - 2.0), (w, 3.0, 2.0), claw)
            put(m, bone, (x + 0.5, 0.75, zmin - 3.5), (w - 1.0, 2.0, 1.5), shade(claw, 0.88))
            put(m, bone, (x + 0.75, 1.0, zmin - 4.5), (w - 1.5, 1.0, 1.0), shade(claw, 0.7))


def et_ridge_rims(m, limit=420):
    """dark crust lips on the edge of lava on the ridge's top faces: wherever a lit top face meets an unlit one at the same height"""
    top = {}
    glow = m.glow
    for c in m.bone("ridge")["cubes"]:
        f = c["uv"].get("up")
        if f is None or "rotation" in c or isinstance(f["uv"], tuple):
            continue
        u, v = f["uv"]
        w, h = f["uv_size"]
        x0, y0, x1, y1 = int(min(u, u + w)), int(min(v, v + h)), int(max(u, u + w)), int(max(v, v + h))
        sub = glow[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1), 3] > 0
        lit = sub.size and sub.mean() > 0.6
        if not lit and float(np.mean(m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01)))) > 80:
            continue          # the pale crater rock is not lava crust: no dark lips there
        o, s = c["origin"], c["size"]
        top[(round(o[0], 2), round(o[2], 2), round(o[1] + s[1], 2), round(s[0], 2), round(s[2], 2))] = (bool(lit), c)
    n = 0
    rock = (36, 30, 30)
    for (x, z, y, sx, sz), (lit, c) in sorted(top.items()):
        if lit or n >= limit or sx < 1.5 or sz < 1.5:
            continue
        # any lit neighbour touching one of the four sides at the same top height?
        for (dx, dz, side) in ((sx, 0, "e"), (-1, 0, "w"), (0, sz, "s"), (0, -1, "n")):
            hit = False
            for (x2, z2, y2, sx2, sz2), (lit2, _) in top.items():
                if lit2 and abs(y2 - y) < 0.01 and x2 - 0.01 <= x + dx <= x2 + sx2 + 0.01 and z2 - 0.01 <= z + dz <= z2 + sz2 + 0.01 \
                        and (abs((x + (sx if side == "e" else 0)) - (x2 + (0 if side == "e" else sx2))) < 1.5 or side in "sn"):
                    hit = True
                    break
            if hit:
                if side in "ew":
                    o = (x + (sx - 0.5 if side == "e" else 0.0), y, z + 0.5)
                    s = (0.5, 0.25, sz - 1.0)
                else:
                    o = (x + 0.5, y, z + (sz - 0.5 if side == "s" else 0.0))
                    s = (sx - 1.0, 0.25, 0.5)
                # the two short ends are left out: they would lie on the walls of the taller steps beside the slab (z-fighting)
                fs = ("up", "east", "west") if side in "ew" else ("up", "north", "south")
                if put(m, "ridge", o, s, rock, faces=fs):
                    n += 1
                break


def et_ridge_bevel(m):
    H.bevel_all(m, step=0.5, R=1, min_dim=8.0, bones=lambda n: n == "ridge")


# =============================================================================================== recipes
def build(name, v):
    m = load(name)
    if v == "now":
        return m
    H.prune(m, name, deep=0.0)
    if name == "thunder_wing":
        face = [bev({"head"}, step=0.5, R=1, min_dim=2.0), tw_head_trim, tw_feet]
        wing = [tw_wing_tips, bev({"tail"}, step=0.5, R=1, min_dim=4.0), bev({"neck"}, step=0.5, R=1, min_dim=4.0),
                bev({"wing_l", "wing_r"}, step=0.5, R=1, min_dim=2.0, axes="xz"),
                bev({"hand_l", "hand_r"}, step=0.5, R=1, min_dim=2.0, axes="xy", pred=lambda c: c["size"][2] >= 12),
                bev({"body"}, step=0.5, R=1, min_dim=4.0)]
    else:
        face = [bev({"head"}, step=0.5, R=1, min_dim=6.0), et_head_trim, et_claws,
                bev({"tail"}, step=0.5, R=1, min_dim=8.0), bev({"leg_fl", "leg_fr", "leg_rl", "leg_rr"}, step=0.5, R=1, min_dim=6.0)]
        wing = [et_ridge_rims, et_ridge_bevel, bev({"body"}, step=0.5, R=1, min_dim=8.0)]
    steps = {"A": face, "B": wing, "C": face + wing}[v]
    spend(m, steps)
    return m


VIEWS = {
    "thunder_wing": [("front", 180, 12), ("3/4", 215, 24), ("top", 180, 62)],
    "ember_titan": [("front", 180, 12), ("3/4", 215, 24), ("top", 180, 62)],
}
ZOOMS = {  # (label, aabb in geo coordinates, yaw, pitch)
    "thunder_wing": [("head", ((-16, 36, -126), (16, 95, -80)), 200, 12), ("wing tip", ((120, 40, -46), (230, 70, 46)), 200, 40),
                     ("foot", ((-20, 14, 14), (20, 30, 50)), 200, 22)],
    "ember_titan": [("head", ((-42, -2, -208), (42, 46, -134)), 205, 16), ("ridge", ((-58, 50, -96), (60, 142, 102)), 215, 30),
                    ("foot", ((44, -2, -114), (106, 20, -62)), 215, 20)],
}


def zoom(faces, aabb, yaw, pitch, size=(420, 420)):
    a0, a1 = np.array(aabb[0], float), np.array(aabb[1], float)
    keep = []
    for f in faces:
        c = f.origin + f.eu / 2 + f.ev / 2
        if (c >= a0 - 1e-6).all() and (c <= a1 + 1e-6).all():
            keep.append(f)
    return hp_view.shot(keep, yaw, pitch, size)


def glow_px(m):
    g = m.finalize()[2]
    return 0 if g is None else int((g[..., 3] > 0).sum())


def render_sheet(name, variants=("now", "A", "B", "C")):
    cols, notes = [], []
    for v in variants:
        m = build(name, v)
        geo = hpkit.finish_geo(m)
        n, q, mn, bn = hpkit.stats(geo)
        ov = hpkit.overlaps(geo, detail=True)
        gl = glow_px(m)
        print(name, v, "boxes", n, "faces", q, "min", mn, "overlaps", len(ov), "glow px", gl, flush=True)
        for o in ov[:6]:
            print("   ", o)
        f = m.faces()
        row = [hp_view.shot(f, y, p, (420, 420)) for _, y, p in VIEWS[name]]
        row += [zoom(f, a, y, p) for _, a, y, p in ZOOMS[name]]
        notes.append(f"{v}: {n}/{q}")
        cols.append((f"{v} ({n} boxes / {q} faces)" if v != "now" else f"now ({n}/{q})", row))
        if v != "now":
            d = OUT / "assets" / f"{name}_{v}"
            m.save(d)
    OUT.mkdir(parents=True, exist_ok=True)
    voxel.sheet(f"{name} near LOD high-poly options", cols, note="; ".join(notes)).save(OUT / f"{name}.png")


# =============================================================================================== install
def install_titan(name, variant=None):
    """called by the install script right after the raw model was written: replace the near geo (and the shared atlas) by the
    chosen variant.  mid / far geos keep their boxes; only their texture_height follows the atlas (new swatch rows at the bottom)."""
    global _SRC
    variant = variant or RECOMMEND[name]
    if variant == "now":
        return None
    _SRC = "assets"
    try:
        m = build(name, variant)
        m = H.finish(m, name)
    finally:
        _SRC = "base"
    geo, tex, glow = m.finalize()
    n, q, mn, bn = hpkit.stats(geo)
    ov = hpkit.overlaps(geo)
    gl = int((glow[..., 3] > 0).sum())
    holes = sum(len(v) for v in H.pose_holes(name, geo, samples=36).values())
    print(f"high-poly {name} {variant}: {n} boxes / {q} quads, min {mn}, overlaps {len(ov)}, glow px {gl}, opening faces {holes}")
    assert not ov, ov[:5]
    assert holes == 0
    assert q <= BUDGET, q
    assert gl > 0
    gdir, tdir = ASSETS / "geo/entity", ASSETS / "textures/entity"
    (gdir / f"{name}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(tdir / f"{name}.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(tdir / f"{name}_glowmask.png", optimize=True)
    H1 = tex.shape[0]
    for suf in ("_mid", "_far"):   # same atlas, taller: uv are in pixels, only the declared height changes
        p = gdir / f"{name}{suf}.geo.json"
        g = json.loads(p.read_text(encoding="utf-8"))
        g["minecraft:geometry"][0]["description"]["texture_height"] = H1
        p.write_text(json.dumps(g, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return geo


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--install":
        install_titan(args[1], args[2] if len(args) > 2 else None)
    else:
        for n in args or NAMES:
            render_sheet(n)
