"""High-poly roughs, owner 1a: the stone-whale and tide-shell enemies (docs/design/15_high_poly.md, chapter 8).

  rib_archer, moss_crawler, waterway_maw, coral_thrall, reef_scuttler, bubble_puffer
  Each gets 3 options (A, B, C) next to "now".  A = automatic (cull + chamfer), B = + carving and trim,
  C = + the finest detail (0.25 units) the face budget allows.  Budget: <= 1,500 faces per body (they fill the screen).

  python tools/rough/hp_mobs_whale_tide.py [name ...]            sheets -> docs/review/high_poly_roughs/<name>.png (+ assets/<name>_<A|B|C>/)
  python tools/rough/hp_mobs_whale_tide.py --install name=A ...  write the picked option into assets/skyabyss/{geo,textures}/entity

The starting point of every build is tools/sources/hp_base/entity/<name>.* (the geo / textures before this pass), so a build
can always be repeated.  tools/install/install_tide_mobs.py calls `apply()` after it has drawn its own boxes.
Own helper in this file: cull_hidden (drops faces covered by a box of the same bone).  hpkit.py is not touched.
"""
import sys

sys.path.insert(0, "tools/rough")
import copy
import json
import math
from pathlib import Path

import numpy as np
import hp_view
import hpkit
import voxel
from PIL import Image

OUT = Path("docs/review/high_poly_roughs")
BASE = Path("tools/sources/hp_base/entity")
ASSETS = Path("src/main/resources/assets/skyabyss")
NAMES = ("rib_archer", "moss_crawler", "waterway_maw", "coral_thrall", "reef_scuttler", "bubble_puffer")
FACE_CAP = 1500


def load_base(name):
    return hpkit.Model.load(name, geo_path=BASE / f"{name}.geo.json", tex_path=BASE / f"{name}.png",
                            glow_path=BASE / f"{name}_glowmask.png")


def rgb_of(m, bone, idx, face="north"):
    c = m.bone(bone)["cubes"][idx]
    f = c["uv"][face]
    u, v = f["uv"]
    w, h = f["uv_size"]
    return m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))


def shade(rgb, k):
    return tuple(max(0, min(255, int(v * k))) for v in rgb)


def faces_count(m, bones=None):
    return sum(len(c["uv"]) for b in m.g["bones"] if bones is None or b["name"] in bones for c in b.get("cubes", []))


# ------------------------------------------------------------------ hidden-face culling (own helper; hpkit stays untouched)
Q = 0.25


def _cells(c):
    o = np.array(c["origin"], float) / Q
    e = o + np.array(c["size"], float) / Q
    if "rotation" in c or np.abs(o - np.round(o)).max() > 1e-4 or np.abs(e - np.round(e)).max() > 1e-4:
        return None
    return np.round(o).astype(int), np.round(e).astype(int)


def cull_hidden(m, bones=None):
    """Drop faces that are fully covered by another box of the SAME bone (bones move, so never across bones).
    Boxes with their own rotation or an off-grid corner take no part. Returns the number of faces dropped."""
    dropped = 0
    for b in m.g["bones"]:
        if bones is not None and b["name"] not in bones:
            continue
        cubes = b.get("cubes", [])
        grid = {}
        for i, c in enumerate(cubes):
            ce = _cells(c)
            if ce is None:
                continue
            o, e = ce
            for x in range(o[0], e[0]):
                for y in range(o[1], e[1]):
                    for z in range(o[2], e[2]):
                        grid[(x, y, z)] = i
        for i, c in enumerate(cubes):
            ce = _cells(c)
            if ce is None:
                continue
            o, e = ce
            for d in list(c["uv"].keys()):
                ax, sg = hpkit.NORMAL[d]
                a1, a2 = [a for a in range(3) if a != ax]
                layer = e[ax] if sg > 0 else o[ax] - 1
                ok = True
                for p in range(o[a1], e[a1]):
                    for q in range(o[a2], e[a2]):
                        key = [0, 0, 0]
                        key[ax], key[a1], key[a2] = layer, p, q
                        j = grid.get(tuple(key))
                        if j is None or j == i:
                            ok = False
                            break
                    if not ok:
                        break
                if ok:
                    del c["uv"][d]
                    dropped += 1
    return dropped


def drop_faces(m, bone, pred, dirs):
    """Remove the faces `dirs` of every box of `bone` for which pred(cube) is true (back / underside never seen)."""
    n = 0
    for c in m.bone(bone).get("cubes", []):
        if pred(c):
            for d in dirs:
                if d in c["uv"]:
                    del c["uv"][d]
                    n += 1
    return n


# ------------------------------------------------------------------ viewing: game-signed geo -> intended angles, animation poses
SIGN = (-1, 1, -1)       # GeckoLib negates X and Z (file = intended * SIGN); previews need the intended angles


def to_view(geo):
    g = copy.deepcopy(geo)
    for b in g["minecraft:geometry"][0]["bones"]:
        if "rotation" in b:
            b["rotation"] = [b["rotation"][i] * SIGN[i] for i in range(3)]
    return g


def _molang(expr, t):
    if isinstance(expr, (int, float)):
        return float(expr)
    e = expr.replace("math.sin", "_sin").replace("math.cos", "_cos").replace("math.abs", "abs")
    e = e.replace("query.anim_time", str(t)).replace("math.pi", str(math.pi))
    return float(eval(e, {"_sin": lambda x: math.sin(math.radians(x)), "_cos": lambda x: math.cos(math.radians(x)), "abs": abs}))


def _key(ch, t):
    def vec(v):
        v = v if isinstance(v, list) else (v.get("vector") or v.get("post") or v.get("pre"))
        return [_molang(x, t) for x in v]
    if isinstance(ch, dict):
        ks = sorted((float(k), v) for k, v in ch.items())
        if t <= ks[0][0]:
            return vec(ks[0][1])
        if t >= ks[-1][0]:
            return vec(ks[-1][1])
        for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
            if t0 <= t <= t1:
                k = (t - t0) / (t1 - t0)
                a, b = vec(v0), vec(v1)
                return [a[i] + (b[i] - a[i]) * k for i in range(3)]
    if isinstance(ch, list):
        return [_molang(x, t) for x in ch]
    return [_molang(ch, t)] * 3


def anim_pose(name, anim, t):
    """{bone: intended (rx, ry, rz)} of animation `anim` (e.g. 'shoot') at time t seconds (rotation channels only)."""
    d = json.loads((ASSETS / "animations/entity" / f"{name}.animation.json").read_text(encoding="utf-8"))
    a = d["animations"][f"animation.{name}.{anim}"]
    pose = {}
    for bn, ch in a.get("bones", {}).items():
        if "rotation" in ch:
            v = _key(ch["rotation"], t)
            pose[bn] = tuple(v[i] * SIGN[i] for i in range(3))
    return pose


def faces_of(m, pose=None, gear=None):
    """voxel faces of Model m (game geo, intended angles); gear=(Model, socket) adds a gear model on a socket bone."""
    geo, tex, glow = m.finalize()
    gm = glow
    if gm is not None and not (gm[..., 3] > 0).any():
        gm = None
    vg = to_view(geo)
    f = voxel.load_geo(vg, tex, glowmask=gm, pose=pose)
    if gear:
        gmod, sock = gear
        ggeo, gtex, gglow = gmod.finalize()
        gg = None if gglow is None or not (gglow[..., 3] > 0).any() else gglow
        f += hp_view.gear_faces(vg, to_view(ggeo), gtex, sock, pose=pose, gear_glow=gg)
    return f


# ------------------------------------------------------------------ sheets
# per model: yaw of the front view, crop of the face closeup (fractions of a 900 px render), poses to show, gear
VIEW = {}


def render_model(name, builders, save_assets=True):
    cfg = VIEW[name]
    gear = None
    if cfg.get("gear"):
        gname, sock = cfg["gear"]
        gear = (hpkit.Model.load(gname, kind="gear"), sock)
    yaw = cfg.get("yaw", 180)
    cols, notes = [], []
    for v in ("now",) + tuple(builders):
        m = load_base(name) if v == "now" else builders[v]()
        geo = hpkit.finish_geo(m)
        n, q, mn, bn = hpkit.stats(geo)
        ov = hpkit.overlaps(geo, detail=True)
        vis = q - sum(len(c["uv"]) for b in geo["minecraft:geometry"][0]["bones"] if b["name"] in cfg.get("hidden", ()) for c in b.get("cubes", []))
        notes.append(f"{v}: {n} boxes / {q} faces" + (f" ({vis} awake)" if vis != q else ""))
        print(name, v, n, q, "min", mn, "awake", vis, "overlaps", len(ov), "glow px",
              0 if m.glow is None else int((m.finalize()[2][..., 3] > 0).sum()))
        for o in ov[:6]:
            print("   ", o)
        f0 = faces_of(m, gear=gear)
        rows = [hp_view.shot(f0, yaw + dy, p, cfg.get("size", (330, 330))) for dy, p in cfg.get("views", ((0, 10), (35, 20), (180, 18)))]
        big = hp_view.shot(f0, yaw + cfg.get("crop_yaw", 0), cfg.get("crop_pitch", 12), (1000, 1000))
        w, h = big.size
        x0, y0, x1, y1 = cfg["crop"]
        rows.append(big.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1))).resize(cfg.get("crop_size", (330, 330)), Image.LANCZOS))
        for lab, anim, t in cfg.get("poses", ()):
            rows.append(hp_view.shot(faces_of(m, pose=anim_pose(name, anim, t), gear=gear), yaw + cfg.get("pose_yaw", 35), 15, cfg.get("size", (330, 330))))
        cols.append((f"{'now' if v == 'now' else v} ({q}{'/' + str(vis) if vis != q else ''})", rows))
        if save_assets and v != "now":
            m.save(OUT / "assets" / f"{name}_{v}")
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = voxel.sheet(f"{name} high-poly options", cols, note="; ".join(notes))
    sheet.save(OUT / f"{name}.png")
    return sheet


BUILD = {}       # name -> {variant: callable() -> Model}


def main(argv):
    if argv and argv[0] == "--install":
        if len(argv) == 1:
            install_picks()
        for spec in argv[1:]:
            name, v = spec.split("=")
            install(name, v)
        return
    for name in (argv or NAMES):
        render_model(name, BUILD[name])


PICKS = {"rib_archer": "C", "moss_crawler": "C", "waterway_maw": "C", "coral_thrall": "C", "reef_scuttler": "C", "bubble_puffer": "C"}


def install(name, variant):
    m = BUILD[name][variant]()
    write_game(name, m)
    if name == "coral_thrall":          # the statue skin: same boxes, bleached texture (as install_tide_mobs.thrall_statue_tex)
        import tide_mobs as tm
        geo, tex, glow = m.finalize()
        sgl = glow.copy()
        a = sgl[..., 3] > 0
        sgl[..., :3][a] = (sgl[..., :3][a] * 0.45).astype(np.uint8)
        d = ASSETS / "textures/entity"
        Image.fromarray(tm.bleach(tex), "RGBA").save(d / "coral_thrall_statue.png")
        Image.fromarray(sgl, "RGBA").save(d / "coral_thrall_statue_glowmask.png")


def install_picks(names=None):
    """The picked options (PICKS) into assets/. tools/install/install_tide_mobs.py calls this after it has drawn its own boxes."""
    for n in names or NAMES:
        install(n, PICKS[n])


def write_game(name, m, geo_dir=ASSETS / "geo/entity", tex_dir=ASSETS / "textures/entity"):
    geo, tex, glow = m.finalize()
    (geo_dir / f"{name}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex).save(tex_dir / f"{name}.png")
    assert glow is not None and (glow[..., 3] > 0).any(), f"no lit pixel: {name}"
    Image.fromarray(glow).save(tex_dir / f"{name}_glowmask.png")
    print("installed", name, hpkit.stats(geo))


# ================================================================== rib_archer
VIEW["rib_archer"] = dict(yaw=180, views=((0, 8), (35, 20), (180, 15)), crop=(0.31, 0.04, 0.69, 0.30), crop_pitch=8,
                          gear=("rib_archer_bow", "socket_off"),
                          poses=(("shoot", "shoot", 0.5), ("walk", "walk", 0.25)))


def archer(v):
    m = load_base("rib_archer")
    bone_c, lite_c, hi_c = (191, 179, 154), (220, 210, 182), (243, 237, 218)
    dark_c, shad_c = (181, 169, 130), (125, 114, 89)
    skull = m.bone("skull")["cubes"][0]
    jaw = m.bone("skull")["cubes"][9]
    teeth = m.bone("skull")["cubes"][10:14]
    horns = m.bone("skull")["cubes"][14:18]

    def thin(b, c):
        return dict(step=0.25, R=1, axes="xz", min_dim=0) if min(c["size"]) >= 1.0 and "rotation" not in c else None

    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        cull_hidden(m)
        return m

    def rule(b, c):
        s = sorted(c["size"])
        if s[1] >= 6 and s[0] >= 3:
            return dict(R=3)
        if s[1] >= 3 and s[0] >= 1.5:
            return dict(R=2)
        if s[1] >= 2 and s[0] >= 1.5:
            return dict(R=1)
        if b["name"] in ("arm_l", "arm_r", "forearm_l", "forearm_r", "leg_l", "leg_r") and s[0] >= 1.0:
            return dict(step=0.25, R=1, axes="xz", min_dim=0)
        if b["name"] == "spine" and s[0] >= 1.0:
            return dict(step=0.25, R=1, min_dim=0)
        if s[0] == 0.5 and s[1] >= 2:        # thin plates: soften the edges by a quarter
            return dict(step=0.25, R=1, min_dim=0, min_center=0.0)
        return None

    kept = [jaw] + list(teeth) + list(horns)
    m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule, skip=lambda b, c: c is skull or any(c is k for k in kept))
    # skull: three-step round, carved sockets and nose cavity (the glow texels stay where they were, now two steps deeper)
    holes = []
    for x0, x1 in ((-3.0, -1.0), (1.0, 3.0)):
        holes.append(((x0, 27.5, 2.5), (x1, 29.0, 3.5)))           # eye sockets, 1 deep
    holes.append(((-0.5, 25.5, 2.5), (0.5, 27.0, 3.5)))             # nose cavity
    cubes = m.bone("skull")["cubes"]
    i = next(k for k, c in enumerate(cubes) if c is skull)
    cubes[i:i + 1] = hpkit.round_cube(skull, step=0.5, R=3, holes=holes)
    # horns: tapered (a thick foot + a thin tip) instead of a stack of blocks
    for h in horns:
        m.bone("skull")["cubes"].remove(h)
    for sx in (-1, 1):
        x = 1.5 if sx > 0 else -2.5
        m.add_box("skull", (x, 31.0, -0.5), (1.0, 1.0, 1.0), rgb=(207, 196, 164))
        m.add_box("skull", (x + 0.25, 32.0, -0.25), (0.5, 0.75, 0.5), rgb=(207, 196, 164))
        m.add_box("skull", (x + 0.375, 32.75, -0.125), (0.25, 0.75, 0.25), rgb=hi_c)
    # brow ridge (a thin bar over the sockets) and cheekbones
    m.add_box("skull", (-3.0, 29.0, 4.0), (6.0, 0.25, 0.25), rgb=hi_c, safe=True)
    for sx in (-1, 1):
        xa = 2.75 if sx > 0 else -3.75
        m.add_box("skull", (xa, 26.5, 3.5), (1.0, 1.0, 0.5), rgb=lite_c, safe=True)
    # teeth: ten 0.25 pegs in a row instead of four blocks
    for t in teeth:
        m.bone("skull")["cubes"].remove(t)
    for k in range(10):
        x = -2.5 + k * 0.5
        m.add_box("skull", (x, 23.0, 4.0), (0.25, 0.5, 0.25), rgb=hi_c if k % 2 else (207, 196, 164))
    if v == "B":
        cull_hidden(m)
        return m
    # ---- C: the finest detail
    # brow plate gets an eyebrow step, glowing pinpoints ride in the sockets
    for sx in (-1, 1):
        x0 = 1.25 if sx > 0 else -2.75
        m.add_box("skull", (x0, 27.75, 2.5), (1.5, 0.75, 0.25), rgb=(120, 235, 255), glow=True, faces=("south",))
    # hands: four fingers + a thumb under each hand block, knuckles on top
    for bn, x0 in (("forearm_l", 2.5), ("forearm_r", -3.5)):
        for i in range(4):
            m.add_box(bn, (x0 + i * 0.25, 10.0, -0.5), (0.25, 1.0, 0.25), rgb=lite_c, safe=True)
        m.add_box(bn, (x0 + (1.0 if x0 > 0 else -0.25), 10.25, -0.25), (0.25, 0.75, 0.25), rgb=bone_c, safe=True)
    # knees and a joint ring at each elbow
    for bn, x0 in (("leg_l", 0.0), ("leg_r", -1.0)):
        m.add_box(bn, (x0 - 0.25, 5.25, -0.75), (1.5, 1.0, 0.25), rgb=hi_c, safe=True)
    for bn, x0 in (("arm_l", 2.25), ("arm_r", -3.75)):
        m.add_box(bn, (x0, 16.5, -0.75), (1.5, 0.5, 1.5), rgb=bone_c, safe=True)
    # spine: a small spur on each rib bar's tip (front) and a collar bone under the neck
    for y in (14.0, 16.0, 18.0, 20.0):
        m.add_box("spine", (-2.75, y, 1.25), (0.25, 0.5, 0.25), rgb=hi_c, safe=True)
        m.add_box("spine", (2.5, y, 1.25), (0.25, 0.5, 0.25), rgb=hi_c, safe=True)
    cull_hidden(m)
    return m


BUILD["rib_archer"] = {k: (lambda k=k: archer(k)) for k in "ABC"}

# ================================================================== waterway_maw  (front = -z, no root turn)
VIEW["waterway_maw"] = dict(yaw=-90, views=((0, 10), (35, 22), (180, 15)), crop=(0.40, 0.18, 1.0, 0.78), crop_yaw=35, crop_pitch=18,
                            crop_size=(400, 330), size=(400, 330),
                            poses=(("bite", "bite", 0.12), ("swim", "swim", 0.25)))


def fang(m, bone, x, y, z, up, rgb, tall=False, w=1.0):
    """A tapered tooth: three tiers, each thinner (0.25 grid). up=True points up from y, False hangs down from y (y = root)."""
    tiers = [(w, 1.5 if tall else 1.0), (w * 0.5, 1.0 if tall else 0.75), (w * 0.25, 0.5 if tall else 0.25)]
    cx, cz = x + w / 2, z + w / 2
    cur = y
    for ww, hh in tiers:
        y0 = cur if up else cur - hh
        m.add_box(bone, (cx - ww / 2, y0, cz - ww / 2), (ww, hh, ww), rgb=rgb, faces=("north", "south", "east", "west", "up"))
        cur = cur + hh if up else cur - hh


def maw(v):
    m = load_base("waterway_maw")
    tooth_c, tooth_s = (236, 236, 226), (200, 204, 200)
    old_teeth_h = m.bone("head")["cubes"][4:18]
    old_teeth_j = m.bone("jaw")["cubes"][1:7]
    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0, rule=lambda b, c: dict(R=2) if min(c["size"]) >= 6 else (dict(R=1) if sorted(c["size"])[1] >= 3 else None),
                    skip=lambda b, c: any(c is t for t in old_teeth_h + old_teeth_j))
        cull_hidden(m)
        return m

    def rule(b, c):
        s = sorted(c["size"])
        if s[0] >= 8:
            return dict(R=3)
        if s[0] >= 4:
            return dict(R=2)
        if s[0] >= 2 and s[1] >= 3:
            return dict(R=1)
        if s[0] >= 1 and s[1] >= 5:
            return dict(step=0.25, R=1, min_dim=0)       # fin plates: a quarter-step edge
        return None

    skipset = old_teeth_h + old_teeth_j
    m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule, skip=lambda b, c: any(c is t for t in skipset))
    m.bone("head")["cubes"][:] = [c for c in m.bone("head")["cubes"] if not any(c is t for t in old_teeth_h)]
    m.bone("jaw")["cubes"][:] = [c for c in m.bone("jaw")["cubes"] if not any(c is t for t in old_teeth_j)]
    # fangs: two rows each side. Upper teeth hang from the palate plate (y 7), lower teeth rise from the jaw (y 5)
    n_up = 8 if v == "B" else 10
    for k in range(n_up):
        z = -35.5 + k * (27.0 / (n_up - 1))
        for x in (5.0, -6.0):
            fang(m, "head", x, 7.0, z, False, tooth_c, tall=(k % 3 == 1))
    n_lo = 5 if v == "B" else 7
    for k in range(n_lo):
        z = -33.5 + k * (17.0 / (n_lo - 1))
        for x in (2.0, -3.0):
            fang(m, "jaw", x, 5.0, z, True, tooth_s)
    if v == "B":
        cull_hidden(m)
        return m
    # ---- C: gill slits cut into the head sides, a spine ridge of thorns on top of every segment, lure collar
    for sx in (-1, 1):
        x = 8.75 if sx > 0 else -9.0
        for k in range(3):
            m.add_box("head", (x, 12.0 + k * 3.0, -20.5), (0.25, 2.0, 0.5), rgb=(14, 20, 40), safe=True)
    for bn, (top, x0, z0, w) in {"seg1": (20.0, -1.0, 1.0, 2), "seg2": (19.0, -1.0, 9.0, 2), "seg3": (18.0, -1.0, 17.0, 2), "seg4": (17.0, -1.0, 25.0, 2)}.items():
        for k in range(3):
            m.add_box(bn, (-0.5, top + 0.0, z0 + 0.5 + k * 2.0), (1.0, 1.25 + (1 - abs(k - 1)) * 0.5, 0.5), rgb=(88, 130, 200), safe=True)
    m.add_box("lure", (-1.5, 29.5, -18.5), (3.0, 0.5, 3.0), rgb=(30, 38, 73), safe=True)
    cull_hidden(m)
    return m


BUILD["waterway_maw"] = {k: (lambda k=k: maw(k)) for k in "ABC"}


# ================================================================== moss_crawler  (face = +z, root turned 180)
VIEW["moss_crawler"] = dict(yaw=180, views=((0, 10), (35, 22), (270 - 180 + 0, 10)), crop=(0.26, 0.10, 0.74, 0.50), crop_pitch=10, crop_size=(360, 300),
                            poses=(("walk", "walk", 0.25), ("pounce", "pounce", 0.3)))
ALLBUT_FRONT = {(0, -1), (0, 1), (1, -1), (1, 1), (2, -1)}


def crawler(v):
    m = load_base("moss_crawler")
    bone_c, lite_c, hi_c, tooth_c = (191, 179, 154), (220, 210, 182), (243, 237, 218), (237, 229, 204)
    dark_c, shad_c = (181, 169, 130), (125, 114, 89)
    sk = m.bone("skull")["cubes"]
    block, base_pl = sk[1], sk[0]
    top_plate = sk[2]
    old_teeth = [sk[i] for i in range(16, 21)]
    jaw_teeth = m.bone("jaw")["cubes"][2:6]
    legs_u = [b["name"] for b in m.g["bones"] if b["name"].endswith("_u")]
    legs_l = [b["name"] for b in m.g["bones"] if b["name"].endswith("_l")]

    def round_legs(R, foot_rule=True):
        def rule(b, c):
            s = sorted(c["size"])
            if b["name"].endswith("_u") or (b["name"].endswith("_l") and s[0] >= 1.5):
                return dict(R=1)
            if b["name"].endswith("_l") and s[0] >= 0.5 and s[1] >= 2.0 and foot_rule:
                return dict(step=0.25, R=1, min_dim=0, min_center=0.0)
            return None
        m.round_all(step=0.5, R=1, min_dim=0, bones=legs_u + legs_l, rule=rule)

    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=1.5, bones=["skull", "jaw"] + legs_u + legs_l,
                    rule=lambda b, c: dict(R=1) if sorted(c["size"])[0] >= 1.5 else None)
        cull_hidden(m)
        return m
    # B: skull block rounded (not on the front, where the face plates sit), jaw plate, legs with claw feet, teeth as 0.25 fangs
    keep = [block] + old_teeth + list(jaw_teeth)
    m.round_all(step=0.5, R=1, min_dim=0, bones=["skull", "jaw"], skip=lambda b, c: any(c is k for k in keep),
                rule=lambda b, c: dict(R=1) if sorted(c["size"])[0] >= 1.0 and sorted(c["size"])[1] >= 3 else None)
    i = next(k for k, c in enumerate(m.bone("skull")["cubes"]) if c is block)
    m.bone("skull")["cubes"][i:i + 1] = hpkit.round_cube(block, step=0.5, R=2, sides=ALLBUT_FRONT)
    round_legs(1)
    # toes: carve gaps into the foot plates
    for bn in legs_l:
        for c in list(m.bone(bn)["cubes"]):
            if c["size"][0] == 2.5 and c["size"][1] == 0.5 and c["size"][2] == 3.0:
                pass
    # fangs: 0.25-wide pegs along the upper lip, longer canines at the sides; lower row on the jaw
    m.bone("skull")["cubes"][:] = [c for c in m.bone("skull")["cubes"] if not any(c is t for t in old_teeth)]
    m.bone("jaw")["cubes"][:] = [c for c in m.bone("jaw")["cubes"] if not any(c is t for t in jaw_teeth)]
    for k in range(9):
        x = -2.25 + k * 0.5
        m.add_box("skull", (x, 5.0, 7.5), (0.25, 0.5, 0.25), rgb=tooth_c if k % 2 else lite_c, faces=("north", "south", "east", "west", "down"))
    for x in (-2.75, 2.5):
        m.add_box("skull", (x, 4.0, 7.5), (0.25, 1.5, 0.5), rgb=hi_c)
    for k in range(7):
        x = -1.5 + k * 0.5
        m.add_box("jaw", (x, 4.5, 7.5), (0.25, 0.5 if k % 2 else 0.75, 0.25), rgb=tooth_c, faces=("north", "south", "east", "west", "up"))
    # brow ridge on top of the face plate and cheek studs
    m.add_box("skull", (-3.0, 10.5, 7.0), (6.0, 0.25, 1.0), rgb=hi_c, safe=True)
    if v == "B":
        cull_hidden(m)
        return m
    # C: moss blades and carved eye sockets; rib edges
    for sx in (-1, 1):
        x0 = 0.5 if sx > 0 else -2.5
        m.add_box("skull", (x0, 9.0, 8.0), (2.0, 0.25, 0.25), rgb=dark_c, safe=True)       # lid over each eye
    for bn in ("s1", "s2", "s3"):
        zc = {"s1": -8.0, "s2": -4.0, "s3": 0.0}[bn]
        for k in range(3):
            x = (-1.5, 0.25, 1.25)[k]
            m.add_box(bn, (x, 10.0 + (k % 2) * 0.5, zc + 0.25 + k * 0.25), (0.25, 1.5 - (k % 2) * 0.5, 0.25), rgb=(112, 152, 72), safe=True)
    cull_hidden(m)
    return m


BUILD["moss_crawler"] = {k: (lambda k=k: crawler(k)) for k in "ABC"}


# ================================================================== reef_scuttler  (claws = +z, root turned 180)
VIEW["reef_scuttler"] = dict(yaw=180, views=((0, 14), (35, 24), (90, 12)), crop=(0.16, 0.18, 0.84, 0.66), crop_pitch=22, crop_size=(400, 280),
                             poses=(("walk", "walk", 0.25), ("attack", "attack", 0.3)))


def scuttler_build(v):
    m = load_base("reef_scuttler")
    lt, md, dk = (242, 167, 119), (218, 119, 72), (110, 42, 24)
    leg_c = (173, 95, 58)
    legs = ("leg_l", "leg_r")
    claws = ("claw_l", "claw_r")
    stalks = [m.bone("body")["cubes"][i] for i in (26, 27)]
    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=0, bones=["body"] + list(claws),
                    rule=lambda b, c: dict(R=1) if sorted(c["size"])[1] >= 2.5 and sorted(c["size"])[0] >= 1.0 else None)
        cull_hidden(m)
        return m

    def rule(b, c):
        s = sorted(c["size"])
        if b["name"] in claws and s[0] >= 1.0 and s[1] >= 2.5:
            return dict(R=2, step=0.5, axes="xz") if False else dict(R=1, axes="xz", step=0.5)
        if b["name"] in legs and s[0] >= 1.0 and s[1] >= 1.5:
            return dict(step=0.25, R=1, min_dim=0)
        if b["name"] in claws and s[0] >= 0.5 and s[1] >= 1.5 and s[2] >= 1.0:
            return dict(step=0.25, R=1, min_dim=0, min_center=0.0)
        if b["name"] == "body" and s[0] >= 0.5 and s[2] >= 7:
            return dict(step=0.25, R=1, min_dim=0, min_center=0.0)
        return None

    m.round_all(step=0.5, R=1, min_dim=0, rule=rule)
    # pincer teeth: a short comb on each jaw edge inside the mouth gap (y 3.5 .. 4.0)
    for bn, x0, ts in (("claw_l", 4.0, 1), ("claw_r", -6.5, 1)):
        n = 5 if v == "B" else 6
        for k in range(n):
            z = 7.25 + k * (2.0 / (n - 1)) * 1.0
            for x in (x0 + 0.25, x0 + 1.75):
                m.add_box(bn, (x, 3.5, z), (0.25, 0.25, 0.25), rgb=lt, faces=("north", "south", "east", "west", "up"))
                m.add_box(bn, (x + (0.25 if k % 2 else 0.0), 3.75, z), (0.25, 0.25, 0.25), rgb=lt, faces=("north", "south", "east", "west", "down"))
    # studs along the shell front plate and knee thorns on every leg
    for k in range(5):
        m.add_box("body", (-4.5 + k * 2.0, 3.75, -4.25), (0.5, 0.75, 0.25), rgb=lt)
    for bn, xk, sg in (("leg_l", 7.25, 1), ("leg_r", -7.25, -1)):
        for z in (-3.0, -0.5, 2.0):
            x = xk + (0.75 if sg > 0 else -0.25)
            m.add_box(bn, (x if sg > 0 else xk - 0.25 - 0.25 + 0.25, 4.75, z + 0.375), (0.25, 0.75, 0.25), rgb=lt, safe=True)
    if v == "B":
        cull_hidden(m)
        return m
    # C: carapace ridge, eye rings, leg toes, side spikes
    m.add_box("body", (-4.5, 6.0, -3.5), (9.0, 0.25, 0.25), rgb=lt, safe=True)
    for sx in (-1, 1):
        xc = 1.5 if sx > 0 else -2.5
        m.add_box("body", (xc - 0.25, 7.0, 2.25), (1.5, 0.25, 0.25), rgb=dk, safe=True)       # ring round each eye stalk
        for z in (-3.0, -0.5, 2.0):
            xs = 9.25 if sx > 0 else -9.5
            bn = "leg_l" if sx > 0 else "leg_r"
            for dz in (0.0, 0.5):
                m.add_box(bn, (xs + (0.25 if sx > 0 else -0.25 + 0.25) , 0.0, z + 0.125 + dz * 0.5), (0.25, 0.5, 0.25), rgb=leg_c, safe=True)
        for k in range(3):
            xs = 5.0 if sx > 0 else -5.25
            m.add_box("body", (xs, 4.0 + k * 0.5, -2.5 + k * 2.5), (0.25, 0.5, 0.5), rgb=lt, safe=True)
    cull_hidden(m)
    return m


BUILD["reef_scuttler"] = {k: (lambda k=k: scuttler_build(k)) for k in "ABC"}


# ================================================================== coral_thrall  (front = +z, root turned 180)
# Budget note: the old model is 1,803 faces with the statue coral and 1,384 awake.  Faces that point at the ground are
# dropped (nobody sees them from above), and the statue coral is rebuilt from a few stepped slabs instead of 0.5-voxel
# ellipsoids, so the awake body AND the statue (= awake + statue_coral) both stay under 1,500 with the new detail.
VIEW["coral_thrall"] = dict(yaw=180, views=((0, 8), (35, 18), (180, 12)), crop=(0.37, 0.12, 0.63, 0.30), crop_pitch=6, size=(300, 400),
                            crop_size=(330, 300), gear=("coral_spear", "socket_main"), hidden=("statue_coral",),
                            poses=(("attack", "attack", 0.25), ("walk", "walk", 0.25)))
STATUE_BLOBS = [((-3.5, 24.5, 1.5), (1.2, 1.0, 1.0)), ((3.5, 22.0, 1.8), (1.0, 1.5, 0.8)), ((2.5, 17.0, 2.0), (1.4, 1.2, 0.8)),
                ((-2.0, 18.5, -2.0), (1.5, 1.5, 0.8)), ((1.0, 21.0, -2.2), (1.2, 1.8, 0.7)), ((0.0, 15.0, 2.0), (2.0, 1.0, 0.7)),
                ((-3.0, 12.0, 1.5), (1.0, 1.5, 0.8)), ((3.0, 11.5, -1.0), (0.9, 1.8, 0.9))]


def cube_at(m, bone, origin, tol=1e-3):
    for c in m.bone(bone)["cubes"]:
        if all(abs(c["origin"][i] - origin[i]) < tol for i in range(3)):
            return c
    return None


def cube_rgb(m, c, face="north"):
    f = c["uv"][face]
    u, v = f["uv"]
    w, h = f["uv_size"]
    return m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))


def snap(v, g=0.25):
    return round(v / g) * g


def slab_blob(m, bone, c, r, rgb):
    """A rounded lump from three stacked slabs (core + inset caps): ~15 faces instead of an ellipsoid of 0.5 voxels."""
    rx, ry, rz = (snap(x) for x in r)
    cx, cy, cz = (snap(x) for x in c)
    hy = max(0.5, snap(ry * 0.6))
    cap = max(0.25, snap(ry - hy))
    inset = 0.5 if min(rx, rz) > 0.75 else 0.25
    m.add_box(bone, (cx - rx, cy - hy, cz - rz), (2 * rx, 2 * hy, 2 * rz), rgb=rgb)
    for sg in (-1, 1):
        y0 = cy + hy if sg > 0 else cy - hy - cap
        m.add_box(bone, (cx - rx + inset, y0, cz - rz + inset), (2 * rx - 2 * inset, cap, 2 * rz - 2 * inset), rgb=shade(rgb, 1.08 if sg > 0 else 0.8))


def thrall_diet(m):
    """Faces toward the ground are never seen; the statue coral becomes slab lumps."""
    # statue coral first (its colours come from the old ellipsoids)
    sc = m.bone("statue_coral")
    keep_org = [(-1.0, 31.0, -3.5), (-1.5, 33.5, -3.5), (0.5, 30.0, -3.5), (-4.5, 26.5, -0.5), (-5.0, 29.0, -0.5), (4.5, 25.5, 0.5),
                (-4.0, 0.0, -2.0), (3.0, 0.0, 1.0), (-1.0, 0.0, -2.5), (3.5, 0.0, -2.0)]
    cols = []
    for c, r in STATUE_BLOBS:
        k = next((q for q in sc["cubes"] if all(q["origin"][i] - 1e-6 <= c[i] <= q["origin"][i] + q["size"][i] + 1e-6 for i in range(3))), None)
        cols.append(cube_rgb(m, k, "north") if k else (200, 120, 110))
    sc["cubes"][:] = [q for q in sc["cubes"] if any(all(abs(q["origin"][i] - o[i]) < 1e-3 for i in range(3)) for o in keep_org)]
    for (c, r), col in zip(STATUE_BLOBS, cols):
        slab_blob(m, "statue_coral", c, r, col)
    cull_hidden(m)
    # ground-facing faces everywhere except the chin / jaw (below y 28.5 in the head)
    for b in m.g["bones"]:
        for c in b.get("cubes", []):
            if b["name"] == "head" and c["origin"][1] < 28.5:
                continue
            c["uv"].pop("down", None)


def thrall(v):
    m = load_base("coral_thrall")
    thrall_diet(m)
    teeth_c, bone_c, dark_c = (232, 226, 206), (207, 198, 174), (125, 114, 96)
    # 0.25 teeth along the jaw front (the painted ones stay under them)
    for k in range(6):
        m.add_box("head", (-1.25 + 0.5 * k, 26.0, 2.5), (0.25, 0.5, 0.25), rgb=teeth_c, faces=("north", "south", "east", "west", "up"))
    # fingers on the free hand (left), knuckles on the spear fist (right)
    for k in range(4):
        m.add_box("forearm_l", (3.0 + 0.4 * k, 5.5, 0.25), (0.25, 1.0, 0.25), rgb=shade(bone_c, 0.95 - 0.04 * (k % 2)), faces=("north", "south", "east", "west", "down"))
    for k in range(3):
        m.add_box("forearm_r", (-4.375 + 0.375 * k, 11.0, 3.5), (0.25, 0.75, 0.25), rgb=shade(bone_c, 1.1), safe=True)
    if v == "A":
        return m
    # ---- B: brow ridge, cheek studs, nose bridge, shoulder / hip edges at 0.25
    m.add_box("head", (-2.5, 29.5, 2.5), (5.0, 0.25, 0.5), rgb=shade(bone_c, 0.8), safe=True)
    m.add_box("head", (-0.25, 28.75, 2.5), (0.5, 0.75, 0.25), rgb=shade(bone_c, 1.05), safe=True)
    for sx in (-1, 1):
        m.add_box("head", (2.5 if sx > 0 else -3.25, 27.5, 1.5), (0.75, 0.25, 0.5), rgb=shade(bone_c, 1.1), safe=True)
        m.add_box("head", (-3.75 if sx < 0 else 3.5, 28.0, 0.5), (0.25, 0.5, 0.5), rgb=shade(bone_c, 0.9), safe=True)
    for bn, x0 in (("arm_l", 3.0), ("arm_r", -4.5)):
        m.add_box(bn, (x0 - 0.25 if x0 > 0 else x0 - 0.25, 25.25, -0.5), (0.25, 0.5, 1.0), rgb=shade(bone_c, 0.9), safe=True)
    for k in range(4):
        m.add_box("body", (-2.0 + k * 1.25, 15.75, 1.5), (0.25, 0.5, 0.25), rgb=shade(bone_c, 0.85), safe=True)
    if v == "B":
        cull_hidden(m)
        return m
    # ---- C: coral tips (tapered thin spires on the crown and shoulders) and cloth hem tatters
    for org, tip in (((0.0, 34.0, -0.5), (0.375, 38.0, -0.125)), ((-2.0, 34.0, -1.0), (-1.625, 36.0, -0.625)),
                     ((1.5, 33.0, -2.0), (1.875, 35.5, -1.625))):
        c = cube_at(m, "head", org)
        if c is not None:
            col = cube_rgb(m, c)
            m.add_box("head", tip, (0.25, 0.75, 0.25), rgb=shade(col, 1.12), safe=True)
    for bn, org in (("arm_l", (3.5, 25.0, -0.5)), ("arm_r", (-4.5, 25.0, -0.5))):
        c = cube_at(m, bn, org)
        if c is not None:
            col = cube_rgb(m, c)
            m.add_box(bn, (org[0] + 0.375, 27.0, -0.125), (0.25, 0.75, 0.25), rgb=shade(col, 1.12), safe=True)
    for org in ((0.5, 8.5, 1.5), (1.5, 9.5, -2.5), (-3.5, 9.5, 1.5), (-2.5, 10.5, -2.5), (2.5, 10.5, 1.5), (-1.5, 11.0, 1.5)):
        c = cube_at(m, "body", org)
        if c is not None:
            col = cube_rgb(m, c, "up")
            m.add_box("body", (org[0] + 0.25, org[1] - 0.75, org[2] + 0.25), (0.5, 0.75, 0.5), rgb=shade(col, 0.9), faces=("north", "south", "east", "west"))
    cull_hidden(m)
    return m


BUILD["coral_thrall"] = {k: (lambda k=k: thrall(k)) for k in "ABC"}


# ================================================================== bubble_puffer  (front = +z, root turned 180)
# The old model is 2,032 faces after culling (cap 1,500): about a third of the little surface bumps go, faces toward the
# ground go, and the freed faces pay for the lips, teeth, fin rays and spine tips.  The eye glow (small, murky) is untouched.
VIEW["bubble_puffer"] = dict(yaw=180, views=((0, 10), (35, 20), (90, 10)), crop=(0.18, 0.30, 0.82, 0.88), crop_pitch=10, crop_size=(360, 330))


def thin_bumps(m, bones, keep, vol=1.0):
    """Remove small boxes that sit on a surface (exactly one face hidden) except every `keep`-th one. Returns the number removed."""
    n = 0
    for bn in bones:
        cs = m.bone(bn)["cubes"]
        out, k = [], 0
        for c in cs:
            sz = c["size"]
            o = c["origin"]
            face_zone = (abs(o[0] + sz[0] / 2) >= 2.5 and 6.0 <= o[1] <= 9.5 and o[2] + sz[2] >= 3.5) or (o[1] <= 5.0 and o[2] + sz[2] >= 5.0)   # eyes, mouth
            if len(c["uv"]) == 5 and sz[0] * sz[1] * sz[2] <= vol and "rotation" not in c and not face_zone:
                k += 1
                if k % keep != 0:
                    n += 1
                    continue
            out.append(c)
        cs[:] = out
    return n


def puffer_build(v):
    m = load_base("bubble_puffer")
    cull_hidden(m)
    keep = {"A": 2, "B": 2, "C": 2}[v]
    thin_bumps(m, ["body"], keep=5)
    thin_bumps(m, ["spines"], keep=4)
    cull_hidden(m)
    for b in m.g["bones"]:
        for c in b.get("cubes", []):
            if b["name"] == "body" and c["origin"][1] < 2.5:
                continue
            c["uv"].pop("down", None)
    lip, teeth_c = (221, 231, 210), (240, 244, 232)
    if v == "A":
        return m
    # ---- B: lips, two buck teeth, brow over each eye, fin rays
    m.add_box("body", (-2.25, 4.5, 6.0), (4.5, 0.25, 0.25), rgb=lip, safe=True)
    m.add_box("body", (-2.25, 3.5, 6.0), (4.5, 0.25, 0.25), rgb=shade(lip, 0.9), safe=True)
    for x in (-2.25, 2.0):
        m.add_box("body", (x, 3.75, 6.0), (0.25, 0.75, 0.25), rgb=lip, safe=True)
    for x in (-0.625, 0.125):
        m.add_box("body", (x, 3.5, 6.25), (0.5, 0.75, 0.25), rgb=teeth_c, faces=("north", "south", "east", "west", "down"), safe=True)
    for sx in (-1, 1):
        x0 = 3.5 if sx > 0 else -5.0
        m.add_box("body", (x0, 8.5, 4.0), (1.5, 0.25, 1.5), rgb=(63, 129, 137), safe=True)
    for bn, x0, w in (("fin_r", -7.5, 1.5), ("fin_l", 6.5, 1.0)):
        for z in (-0.5, 0.5, 1.5):
            m.add_box(bn, (x0, 5.5, z), (w, 0.25, 0.25), rgb=(106, 176, 176), safe=True)
    if v == "B":
        cull_hidden(m)
        return m
    # ---- C: fin tips, tapered spine tips on the biggest spines
    for bn, x0, sg in (("fin_r", -8.0, -1), ("fin_l", 7.5, 1)):
        for z in (-0.5, 1.5):
            m.add_box(bn, (x0, 5.0, z), (0.5, 0.25, 0.5), rgb=(158, 214, 207), safe=True)
    n = 0
    for c in list(m.bone("spines")["cubes"]):
        if c["size"][1] >= 1.0 and len(c["uv"]) >= 5 and n < 3:
            col = cube_rgb(m, c)
            o = c["origin"]
            m.add_box("spines", (o[0] + 0.125, o[1] + c["size"][1], o[2] + 0.125), (0.25, 0.5, 0.25), rgb=shade(col, 1.1), safe=True)
            n += 1
    cull_hidden(m)
    return m


BUILD["bubble_puffer"] = {k: (lambda k=k: puffer_build(k)) for k in "ABC"}


if __name__ == "__main__":
    main(sys.argv[1:])
