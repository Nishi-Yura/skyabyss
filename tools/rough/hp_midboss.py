"""High-poly roughs for the four mid bosses (docs/design/15_high_poly.md section 8, assignment 2).

  whalebone_captain  tide_beckoner  raiju  ash_smith       3 options each vs now
  A  chamfer only (automatic)
  B  rounded big pieces + 0.25 trim (face, hands, rims)
  C  B + hand-sculpted details (fingers, teeth, claws, carved sockets, ridges)

python tools/rough/hp_midboss.py [name ...]          -> docs/review/high_poly_roughs/<name>.png (+ assets/<name>_<A|B|C>/)
python tools/rough/hp_midboss.py --install name V    -> writes the chosen option into src/main/resources/assets/skyabyss
The original geo / texture / glowmask are kept in tools/sources/hp_base/entity/ (the rough always starts from them).
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import hp_view
import hpkit
import voxel
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/high_poly_roughs"
BASE = ROOT / "tools/sources/hp_base/entity"
GAME = ROOT / "src/main/resources/assets/skyabyss"

NAMES = ["whalebone_captain", "tide_beckoner", "raiju", "ash_smith"]


def load(name):
    """always from the kept originals (so a rerun after install does not round twice)"""
    return hpkit.Model.load(name, geo_path=BASE / f"{name}.geo.json", tex_path=BASE / f"{name}.png",
                            glow_path=BASE / f"{name}_glowmask.png")


def shade(rgb, k):
    """rgb * k. Darker shades get the same lift as the base textures (c' = 255*(c/255)^0.78*1.08+5, design doc 08 19.8) so a dark
    trim colour does not sink into mud in a dim room."""
    out = [max(0, min(255, v * k)) for v in rgb]
    if k < 1.0:
        out = [255.0 * (v / 255.0) ** 0.78 * 1.08 + 5 for v in out]
    return tuple(max(0, min(255, int(v))) for v in out)


def face_rgb(m, bone, idx, face="north"):
    c = m.bone(bone)["cubes"][idx]
    f = c["uv"][face]
    u, v = f["uv"]
    w, h = f["uv_size"]
    return m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))


SKIPPED = []


def put(m, bone, origin, size, **kw):
    """Model.add_box; remembers the boxes safe=True refused (they would overlap) so a typo in a coordinate is visible"""
    r = m.add_box(bone, origin, size, **kw)
    if r is None:
        SKIPPED.append((bone, tuple(origin), tuple(size)))
    return r


def studs(m, bones, face, pick, spacing=3.0, size=0.5, height=0.25, k=1.35, inset=1.5, rgb=None):
    """rivet-like studs on one face of every box that pick(bone, cube) accepts, in a row along the longer in-plane axis."""
    ax, sg = hpkit.NORMAL[face]
    others = [a for a in range(3) if a != ax]
    n = 0
    for b in list(m.g["bones"]):
        if bones is not None and b["name"] not in bones:
            continue
        for c in list(b.get("cubes", [])):
            if face not in c["uv"] or "rotation" in c or not pick(b, c):
                continue
            o, sz = c["origin"], c["size"]
            run = max(others, key=lambda a: sz[a])
            short = [a for a in others if a != run][0]
            u, v = c["uv"][face]["uv"], c["uv"][face]["uv_size"]
            col = rgb
            if col is None:
                base = m.color_at((u[0] + v[0] / 2 - 0.01, u[1] + v[1] / 2 - 0.01))
                col = shade(base, k)
            t = inset
            while t + size <= sz[run] - inset + 1e-6:
                org, siz = [0.0] * 3, [0.0] * 3
                org[run], siz[run] = o[run] + t, size
                org[short], siz[short] = o[short] + sz[short] / 2 - size / 2, size
                org[ax] = o[ax] + sz[ax] if sg > 0 else o[ax] - height
                siz[ax] = height
                faces = tuple(d for d in hpkit.DIRS if d != {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}[face])
                if put(m, b["name"], org, siz, rgb=col, faces=faces, safe=True):
                    n += 1
                t += spacing
    return n


def dims(c):
    return sorted(c["size"])


# =====================================================================================================  captain
CAPTAIN_LIMBS = {"legR", "legL", "armR", "armL", "forearmR", "forearmL"}


def captain_rule(b, c):
    s = dims(c)
    if b["name"].startswith("spine"):
        return None
    ax = "xz" if b["name"] in CAPTAIN_LIMBS else "xyz"
    if s[1] >= 4 and s[0] >= 2:
        return dict(step=0.5, R=2, min_dim=2.0, axes=ax)
    if s[0] >= 1.5:
        return dict(step=0.25, R=1, min_dim=1.0, min_center=0.5, axes=ax)
    return None


def mx(origin, size):
    """mirror a box across x=0 (the model is symmetric left/right)"""
    return (-(origin[0] + size[0]), origin[1], origin[2]), size


def add_lr(m, bone_r, bone_l, origin, size, **kw):
    """add a box on the right bone and its mirror on the left bone"""
    a = put(m, bone_r, origin, size, **kw)
    o2, s2 = mx(origin, size)
    b = put(m, bone_l, o2, s2, **kw)
    return a, b


def build_captain(v):
    m = load("whalebone_captain")
    if v == "now":
        return m
    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m
    bone = (214, 203, 168)
    bone_d = shade(bone, 0.78)
    steel = face_rgb(m, "handR", 1, "north")
    dark = shade(steel, 0.55)
    m.round_all(rule=captain_rule)
    # ---- helmet (0.25 trim). front of the face is z=-6; the visor opening is x -3..3, y 37..40.5
    put(m, "head", (-0.5, 37.0, -4.5), (1.0, 2.5, 1.5), rgb=shade(steel, 1.25), safe=True)          # nose bridge between the eyes
    put(m, "head", (-3.0, 36.75, -6.0), (6.0, 0.25, 1.0), rgb=shade(steel, 1.3), safe=True)         # visor lip
    for x in (-5.25, 4.25):
        for y in (36.0, 41.25):
            put(m, "head", (x, y, -5.75), (0.5, 0.5, 0.25), rgb=shade(steel, 1.4), safe=True)       # cheek rivets
    for x in (-5.5, 5.0):
        put(m, "head", (x, 43.0, -3.5), (0.5, 0.25, 7.0), rgb=shade(steel, 1.2), safe=True)           # crown seam on the helmet top
    # ---- hands: knuckles
    for bn, sx in (("handR", -1), ("handL", 1)):
        x0 = -16.5 if sx < 0 else 12.5
        for i in range(4):
            put(m, bn, (x0 + 0.75 + i * 0.75, 9.5, -2.75), (0.5, 0.75, 0.25), rgb=shade(steel, 1.3), safe=True)
    # ---- forearm bracers: plates on the faces
    for bn, sx in (("forearmR", -1), ("forearmL", 1)):
        x0 = -15.5 if sx < 0 else 13.5
        put(m, bn, (x0, 11.25, -2.25), (2.0, 0.5, 0.25), rgb=shade(steel, 1.3), safe=True)
        put(m, bn, (x0, 14.0, -2.25), (2.0, 0.5, 0.25), rgb=shade(steel, 1.3), safe=True)
    if v == "C":
        # ---- fingers: four boxes in front of the fist, thumb on the inner side
        for bn, sx in (("handR", -1), ("handL", 1)):
            x0 = -16.0 if sx < 0 else 13.0
            for i in range(4):
                put(m, bn, (x0 + i * 0.75, 7.25, -3.25), (0.5, 2.75, 0.75), rgb=shade(steel, 1.12 + 0.06 * (i % 2)), safe=True)
            xt = -12.5 if sx < 0 else 12.0
            put(m, bn, (xt, 8.5, -1.5), (0.5, 1.5, 2.0), rgb=shade(steel, 0.95), safe=True)          # thumb
        # ---- mouth grill under the visor: dark slats on the chin plate
        for i in range(6):
            put(m, "head", (-2.5 + i * 1.25, 35.75, -6.25), (0.5, 1.0, 0.25), rgb=dark, safe=True)
        # ---- bone studs along the pauldron rims and a skull belt buckle ridge
        for bn, sx in (("body", 1),):
            for i in range(5):
                put(m, "body", (-9.0 + i * 4.5, 12.0, -6.25), (1.0, 0.5, 0.25), rgb=bone_d, safe=True)
    return m


# =====================================================================================================  beckoner
def beck_rule(b, c):
    s = dims(c)
    if s[0] >= 3 and s[1] >= 8:
        return dict(step=1.0, R=2, min_dim=2.0)        # shell tiers, claw blocks: a 1-unit round
    if s[0] >= 2 and s[1] >= 3:
        return dict(step=0.5, R=2, min_dim=2.0)
    if s[0] >= 1 and s[1] >= 2:
        return dict(step=0.25, R=1, min_dim=1.0)
    return None


def tooth(m, bone, x, y, z, down, rgb, w=2.0):
    """a two-tier tooth (base + tip) growing from the surface at height y; down=True hangs from a ceiling"""
    d = -1 if down else 1
    base_h, tip_h = 1.5, 1.5
    yb = y - base_h if down else y
    yt = yb - tip_h if down else yb + base_h
    put(m, bone, (x, yb, z), (w, base_h, w), rgb=rgb, safe=True)
    put(m, bone, (x + w / 4, yt, z + w / 4), (w / 2, tip_h, w / 2), rgb=shade(rgb, 1.1), safe=True)


def build_beckoner(v):
    m = load("tide_beckoner")
    if v == "now":
        return m
    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m
    shell = face_rgb(m, "body", 3, "up")
    claw = face_rgb(m, "claw_big", 19, "west")
    cream = (226, 212, 178)
    m.round_all(rule=beck_rule)
    # ---- eyes: a 0.25 frame around each glowing lens (lens front face z=25.5)
    for x0 in (-10.0, 6.0):
        col = shade(shell, 0.45)
        put(m, "eyes", (x0 - 0.25, 42.5, 25.0), (4.5, 0.25, 0.5), rgb=col, safe=True)
        put(m, "eyes", (x0 - 0.25, 39.75, 25.0), (4.5, 0.25, 0.5), rgb=col, safe=True)
        put(m, "eyes", (x0 - 0.25, 40.0, 25.0), (0.25, 2.5, 0.5), rgb=col, safe=True)
        put(m, "eyes", (x0 + 4.0, 40.0, 25.0), (0.25, 2.5, 0.5), rgb=col, safe=True)
    # ---- leg joint spikes (top of the thigh box of every leg)
    for side in ("r", "l"):
        for i in (1, 2, 3):
            bn = f"leg_{side}{i}"
            c0 = m.bone(bn)["cubes"]
            big = max(c0, key=lambda c: c["size"][0] * c["size"][2] * c["size"][1])
            ox, oy, oz = big["origin"]
            sx, sy, sz = big["size"]
            top = oy + sy
            put(m, bn, (ox + sx / 2 - 1.0, top, oz + sz / 2 - 1.0), (2.0, 1.0, 2.0), rgb=shade(claw, 0.9), safe=True)
            put(m, bn, (ox + sx / 2 - 0.5, top + 1.0, oz + sz / 2 - 0.5), (1.0, 1.5, 1.0), rgb=shade(claw, 1.2), safe=True)
    if v == "C":
        # ---- pincer teeth: lower jaw (top y=12) and upper jaw (underside y=30)
        for k in range(3):
            for x in (-46.5, -42.5):
                tooth(m, "claw_big", x, 12.0, 75.0 + k * 3.0, False, cream)
                tooth(m, "claw_big_pincer", x, 30.0, 73.0 + k * 3.0, True, cream)
        # ---- ridges along the claw blocks and the small claw
        m.ridges({"claw_big", "claw_small"}, face="up", spacing=3.0, width=0.5, height=0.25, k=1.3)
        # ---- shell: dorsal spines (stepped) on the top tier
        for z in (-12.0, -6.0, 6.0, 12.0):
            for x in (-8.0, 6.0):
                put(m, "body", (x, 34.0, z), (2.0, 2.0, 2.0), rgb=shade(shell, 1.05), safe=True)
                put(m, "body", (x + 0.5, 36.0, z + 0.5), (1.0, 2.0, 1.0), rgb=shade(shell, 1.25), safe=True)
    return m


# =====================================================================================================  raiju
def raiju_rule(b, c):
    """narrowed range (design doc 15 section 7): chest/body lumps, the head, the big leg boxes. The tail, the mane crystals
    and the 1-unit leg plates are left alone, so the face count stays near 6,000."""
    n = b["name"]
    s = dims(c)
    if n.startswith("tail") or n == "mane":
        return None
    if n in ("chest", "body"):
        return dict(step=0.5, R=1, min_dim=2.0) if (s[0] >= 2 and s[1] >= 4) else None
    if n == "head":
        if s[1] >= 4 and s[0] >= 2:
            return dict(step=0.5, R=2, min_dim=2.0)
        if s[0] >= 1.5:
            return dict(step=0.25, R=1, min_dim=1.0)
        return None
    if s[0] >= 2 and s[1] >= 4:
        return dict(step=0.25, R=1, min_dim=2.0)
    return None


def build_raiju(v):
    m = load("raiju")
    if v == "now":
        return m
    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m
    fur = face_rgb(m, "chest", 0, "up")
    ivory = (232, 226, 205)
    m.round_all(rule=raiju_rule)
    # ---- face. +z is the front; the snout is x -3..3, y 36..41, z 41..48; the mouth gap is y 35.5..36 (the jaw is |x|<2.5)
    for x in (-3.0, 2.5):                                     # upper fangs beside the jaw
        put(m, "head", (x, 34.75, 46.0), (0.5, 1.25, 0.5), rgb=ivory, safe=True)
    for x in (-5.0, 3.0):                                     # brow ridge over each eye (the eyes are y 40..41 at z 42..42.5)
        put(m, "head", (x, 41.5, 41.0), (2.0, 0.5, 1.75), rgb=shade(fur, 0.6), safe=True)
        put(m, "head", (x, 41.0, 42.0), (2.0, 0.5, 0.75), rgb=shade(fur, 0.5), safe=True)
    if v == "C":
        n = m.ridges({"chest", "body"}, face="up", spacing=4.0, width=0.5, height=0.25, k=1.25, min_len=8)
        # shoulder and hip spikes (stepped, two boxes each)
        for sx in (-1, 1):
            for (bone, x, y, z) in (("chest", 10.0, 26.0, 13.0), ("chest", 10.0, 26.0, 8.5), ("body", 8.0, 24.0, -2.0), ("body", 8.0, 24.0, -9.0)):
                xo = x if sx > 0 else -x - 1.0
                put(m, bone, (xo, y, z), (1.0, 2.0, 2.0), rgb=shade(fur, 1.15), safe=True)
                xt = x + 1.0 if sx > 0 else -x - 2.0
                put(m, bone, (xt, y + 0.5, z + 0.5), (1.0, 1.0, 1.0), rgb=shade(fur, 1.45), safe=True)
        # nose rim
        for x, w in ((-1.25, 0.25), (1.0, 0.25)):
            put(m, "head", (x, 38.5, 48.0), (w, 1.5, 0.25), rgb=shade(fur, 0.6), safe=True)
    return m


# =====================================================================================================  smith
SMITH_LIMBS = {"head", "arm_r", "arm_l", "forearm_r", "forearm_l", "leg_r", "leg_l", "belly"}


def smith_rule(b, c):
    s = dims(c)
    n = b["name"]
    if n in SMITH_LIMBS:
        return dict(step=0.5, R=1, min_dim=2.0) if s[0] >= 1.5 else None
    if n == "body":   # the stacked torso slabs only; the 0.5-1 unit plates on them stay
        if s[0] >= 4 and s[1] >= 8:
            return dict(step=0.5, R=2, min_dim=2.0)
        return dict(step=0.5, R=1, min_dim=2.0) if (s[0] >= 3 and s[1] >= 6) else None
    return None       # apron, bellows (about 200 boxes of detail): untouched, they are already fine


def build_smith(v):
    m = load("ash_smith")
    if v == "now":
        return m
    if v == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m
    steel = face_rgb(m, "head", 11, "south")
    if v == "C":   # studs go on the plain boxes first (inset from the edges, so the rounding does not touch them)
        n1 = studs(m, {"body"}, "south", lambda b, c: dims(c)[0] >= 3 and dims(c)[1] >= 6, spacing=3.0)
        n2 = studs(m, {"body"}, "up", lambda b, c: dims(c)[0] >= 3 and dims(c)[1] >= 8, spacing=4.0, inset=2.0)
        n3 = studs(m, {"leg_r", "leg_l", "forearm_r", "forearm_l"}, "south", lambda b, c: dims(c)[0] >= 2 and dims(c)[1] >= 5, spacing=3.0)
        print("   studs", n1, n2, n3)
    m.round_all(rule=smith_rule)
    # ---- furnace helm. The front plane is z=33 (posts + lower plate); the glowing slit is y 50.5..51.5, recessed at z 31.5
    for i in range(5):                                                  # vent slats on the lower plate
        put(m, "head", (-3.25 + i * 1.25, 48.5, 33.0), (0.5, 1.5, 0.25), rgb=shade(steel, 0.45), safe=True)
    put(m, "head", (-4.0, 51.25, 33.0), (8.0, 0.25, 0.25), rgb=shade(steel, 1.3), safe=True)   # brow rim over the slit
    put(m, "head", (-4.0, 50.0, 33.0), (8.0, 0.25, 0.25), rgb=shade(steel, 1.3), safe=True)    # cheek rim under the slit
    return m


BUILD = {"whalebone_captain": build_captain, "tide_beckoner": build_beckoner, "raiju": build_raiju, "ash_smith": build_smith}

VIEWS = {
    "whalebone_captain": [("front", 180, 10), ("3/4", 215, 20), ("back", 35, 20)],
    "tide_beckoner": [("front", 160, 22), ("3/4", 215, 26), ("top", 180, 68)],
    "raiju": [("front", 180, 10), ("3/4", 215, 22), ("rear 3/4", 40, 20)],
    "ash_smith": [("front", 180, 10), ("3/4", 215, 20), ("back", 35, 20)],
}
ZOOM = {  # (yaw, pitch, crop fraction, render px)
    "whalebone_captain": (185, 6, (0.30, 0.04, 0.70, 0.30), 2000),
    "tide_beckoner": (160, 22, (0.50, 0.35, 0.88, 0.85), 1400),
    "raiju": (180, 8, (0.30, 0.05, 0.70, 0.45), 2400),
    "ash_smith": (184, 6, (0.36, 0.28, 0.64, 0.52), 2000),
}


def render(name, variants=("now", "A", "B", "C")):
    cols, notes = [], []
    for v in variants:
        m = BUILD[name](v)
        faces = m.faces()
        geo = hpkit.finish_geo(m)
        n, q, mn, bn = hpkit.stats(geo)
        ov = hpkit.overlaps(geo, detail=True)
        for sk in SKIPPED[:12]:
            print("    skipped", sk)
        SKIPPED.clear()
        gpx = 0 if m.glow is None else int((m.glow[..., 3] > 0).sum())
        print(name, v, "skipped trims", len(SKIPPED), "boxes", n, "faces", q, "min", mn, "overlaps", len(ov), "glow px", gpx)
        for o in ov[:6]:
            print("   ", o)
        row = [hp_view.shot(faces, y, p, (330, 330)) for _, y, p in VIEWS[name]]
        yaw, pitch, (x0, y0, x1, y1), px = ZOOM[name]
        big = hp_view.shot(faces, yaw, pitch, (px, px))
        w, h = big.size
        row.append(big.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1))).resize((330, int(330 * (y1 - y0) / (x1 - x0))), Image.LANCZOS))
        notes.append(f"{v}: {n} boxes / {q} quads")
        label = {"now": "now", "A": "A chamfer", "B": "B round+trim", "C": "C sculpt+trim"}[v]
        cols.append((f"{label} ({n}/{q})", row))
        if v != "now":
            m.save(OUT / "assets" / f"{name}_{v}")
    OUT.mkdir(parents=True, exist_ok=True)
    voxel.sheet(f"{name} high-poly options", cols, note="; ".join(notes)).save(OUT / f"{name}.png")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--dbg":   # --dbg name V yaw pitch x0 y0 x1 y1 out [px]  (a crop of a big render; no checks)
        name, v, yaw, pitch = args[1], args[2], float(args[3]), float(args[4])
        x0, y0, x1, y1 = (float(a) for a in args[5:9])
        px = int(args[10]) if len(args) > 10 else 1400
        m = BUILD[name](v)
        big = hp_view.shot(m.faces(), yaw, pitch, (px, px))
        w, h = big.size
        big.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1))).save(args[9])
        n, q, mn, bn = hpkit.stats(hpkit.finish_geo(m))
        print(name, v, n, q)
    elif args and args[0] == "--install":
        name, v = args[1], args[2]
        m = BUILD[name](v)
        geo = hpkit.finish_geo(m)
        assert not hpkit.overlaps(geo), "overlaps"
        m.save(OUT / "assets" / f"{name}_{v}")
        import shutil
        d = OUT / "assets" / f"{name}_{v}"
        shutil.copy(d / "model.geo.json", GAME / "geo/entity" / f"{name}.geo.json")
        shutil.copy(d / "tex.png", GAME / "textures/entity" / f"{name}.png")
        if (d / "glow.png").exists():
            shutil.copy(d / "glow.png", GAME / "textures/entity" / f"{name}_glowmask.png")
        if name == "ash_smith":   # AshSmithSelfTest greps the geo text for '"name": "bone"', so keep the original indent=1 layout
            import json
            gp = GAME / "geo/entity" / f"{name}.geo.json"
            gp.write_text(json.dumps(json.loads(gp.read_text(encoding="utf-8")), ensure_ascii=False, indent=1), encoding="utf-8")
        if name == "ash_smith":   # the white glow layer (bellows after the breath, rage) shares the uv of the texture: rebuild it
            import numpy as np
            gl = np.array(Image.open(d / "glow.png").convert("RGBA"))
            out = gl.astype(float)
            a = gl[..., 3] > 0
            out[..., :3][a] = out[..., :3][a] * 0.25 + np.array([255, 248, 232], float) * 0.75   # same as install_ember_smith.whiten
            Image.fromarray(out.astype(np.uint8), "RGBA").save(GAME / "textures/entity/ash_smith_glowmask_white.png")
        print("installed", name, v)
    else:
        for n in args or NAMES:
            render(n)
