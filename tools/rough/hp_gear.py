"""High-poly rough 5: weapons/shields (geo/gear) and 3D armor (geo/armor).  3 options vs now per family.

  A  chamfer only      : one-step 0.5 chamfer on every box that is big enough (automatic)
  B  round + fine edge : two-step round on big pieces, 0.25 chamfer on thin shafts/plates (edges only, textures untouched)
  C  B + seams/trim    : B + raised 0.25 seams, rims, studs sitting on the surfaces (single-colour trim, no overlaps)

Input is always tools/sources/hp_base/{gear,armor}/ (the pre-high-poly models), so re-running is idempotent.
  python tools/rough/hp_gear.py            -> docs/review/high_poly_roughs/{gear,armor}_*.png (+ assets/<name>_<A|B|C>/)
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import hp_view
import hpkit
import voxel

BASE = Path("tools/sources/hp_base")
OUT = Path("docs/review/high_poly_roughs")
GEAR = "ash_poker bone_sentinel_shield coral_spear coral_spear_statue hooked_glaive rib_archer_bow smith_hammer smith_tongs supplicant_staff".split()
ARMOR = "stone_whale_armor tide_shell_armor thunder_wing_armor ember_armor".split()


def load(n, k):
    return hpkit.Model.load(n, kind=k, geo_path=BASE / k / f"{n}.geo.json", tex_path=BASE / k / f"{n}.png",
                            glow_path=BASE / k / f"{n}_glowmask.png")


def shade(rgb, k):
    return tuple(max(0, min(255, int(v * k))) for v in rgb)


def rule_a(b, c):
    return dict(R=1) if sorted(c["size"])[1] >= 1.5 else None


def rule_b(b, c):
    s = sorted(c["size"])
    if s[0] >= 2 and s[1] >= 4:
        return dict(R=2)
    if s[1] >= 1.5 and s[0] >= 1.5:
        return dict(R=1)
    if s[1] >= 2.0 and s[0] >= 1.0:      # thin shaft / plate: quarter-unit bevel
        return dict(R=1, step=0.25, min_dim=0.5, min_center=0.0)
    return None


def avg_face(m, c):
    f = next(iter(c["uv"].values()))
    u, v = f["uv"]
    w, h = f["uv_size"]
    return m.color_at((u + w / 2 - 0.01 * (1 if w >= 0 else -1), v + h / 2 - 0.01 * (1 if h >= 0 else -1)))


def trim(m, bones, min_w=3.0, k=1.3):
    """C: raised 0.25 frame (edge seam) on the biggest outward face of every large box (gear/armor plates)."""
    n = 0
    for b in list(m.g["bones"]):
        if bones is not None and b["name"] not in bones:
            continue
        for c in list(b.get("cubes", [])):
            if "rotation" in c or c["size"][1] < 0.01:
                continue
            o, s = c["origin"], c["size"]
            dirs = [(a, sg) for a in (0, 2) for sg in (-1, 1)]
            # biggest face among horizontal normals
            ax = min((0, 2), key=lambda a: s[a])
            if s[ax] > 1.5:
                continue
            u_ax = 2 if ax == 0 else 0
            if s[u_ax] < min_w or s[1] < min_w:
                continue
            col = shade(avg_face(m, c), k)
            for sg in (-1, 1):
                org = list(o)
                org[ax] = o[ax] + s[ax] if sg > 0 else o[ax] - 0.25
                sz = [s[0], s[1], s[2]]
                sz[ax] = 0.25
                for side in ((0, 0.5, "h0"), (1, 0.5, "h1")):
                    pass
                # top and bottom seams
                for yy in (o[1] + 0.5, o[1] + s[1] - 1.0):
                    p2, s2 = list(org), list(sz)
                    p2[1], s2[1] = yy, 0.5
                    p2[u_ax], s2[u_ax] = o[u_ax] + 0.5, s[u_ax] - 1.0
                    if m.add_box(b["name"], p2, s2, rgb=col, safe=True):
                        n += 1
    return n


def build(model, kind, variant):
    m = load(model, kind)
    base = m.copy()
    if variant == "now":
        return m, base
    if variant == "A":
        m.round_all(step=0.5, R=1, min_dim=1.5, rule=rule_a)
        return m, base
    if variant == "C":
        # decorate on the original boxes (so shafts are still whole), then round only the old boxes
        old = {id(c) for bb in m.g["bones"] for c in bb.get("cubes", [])}
        decorate(m, model, kind)
        m.round_all(step=0.5, R=1, min_dim=0.5, rule=rule_b, skip=lambda b, c: id(c) not in old)
    else:
        m.round_all(step=0.5, R=1, min_dim=0.5, rule=rule_b)
    return m, base


def collars(m, every=8.0, t=0.25):
    """wrap a thin ring (4 slabs) round long thin shafts, every `every` units (darker than the shaft)."""
    n = 0
    for b in list(m.g["bones"]):
        for c in list(b.get("cubes", [])):
            if "rotation" in c:
                continue
            o, s = c["origin"], c["size"]
            if s[1] < 8 or max(s[0], s[2]) > 2.0 or min(s[0], s[2]) < 0.5:
                continue
            col = shade(avg_face(m, c), 0.7)
            y = o[1] + 2.0
            while y + 0.5 < o[1] + s[1] - 1.0:
                ok = [m.add_box(b["name"], (o[0] - t, y, o[2] - t), (s[0] + 2 * t, 0.5, t), rgb=col, safe=True),
                      m.add_box(b["name"], (o[0] - t, y, o[2] + s[2]), (s[0] + 2 * t, 0.5, t), rgb=col, safe=True),
                      m.add_box(b["name"], (o[0] - t, y, o[2]), (t, 0.5, s[2]), rgb=col, safe=True),
                      m.add_box(b["name"], (o[0] + s[0], y, o[2]), (t, 0.5, s[2]), rgb=col, safe=True)]
                n += sum(1 for v in ok if v)
                y += every
    return n


def studs(m, spacing=2.0):
    """rivets (0.25 cube-ish) along the border of big flat plates (shield face, hammer head)."""
    n = 0
    for b in list(m.g["bones"]):
        for c in list(b.get("cubes", [])):
            if "rotation" in c:
                continue
            o, s = c["origin"], c["size"]
            ax = min((0, 2), key=lambda a: s[a])
            u = 2 if ax == 0 else 0
            if s[ax] > 3 or s[u] < 5 or s[1] < 5:
                continue
            col = shade(avg_face(m, c), 1.45)
            for sg in (-1, 1):
                for yy in (o[1] + 1.0, o[1] + s[1] - 1.5):
                    k = 1.0
                    while k + 0.5 < s[u] - 0.5:
                        org = [0, yy, 0]
                        sz = [0.5, 0.5, 0.5]
                        org[ax] = o[ax] + s[ax] if sg > 0 else o[ax] - 0.25
                        sz[ax] = 0.25
                        org[u] = o[u] + k
                        if m.add_box(b["name"], org, sz, rgb=col, safe=True):
                            n += 1
                        k += spacing
    return n


def decorate(m, name, kind):
    if kind == "gear":
        trim(m, None, min_w=4.0, k=1.25)
        collars(m)
        studs(m)
    else:
        trim(m, None, min_w=4.0, k=1.25)


def sheet_gear(names, kind, title, fname, size=240, views=((180, 10), (215, 20), (270, 8))):
    rows = {}
    notes = []
    for v in ("now", "A", "B", "C"):
        cols = []
        for n in names:
            m, _ = build(n, kind, v)
            geo = hpkit.finish_geo(m)
            nb, q, mn, _ = hpkit.stats(geo)
            ov = hpkit.overlaps(geo)
            rows.setdefault(n, {})[v] = (m, nb, q, len(ov))
    return rows


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    allstats = []
    for kind, names in (("gear", GEAR), ("armor", ARMOR)):
        for n in names:
            cols = []
            for v, label in (("now", "now"), ("A", "A chamfer"), ("B", "B round+fine"), ("C", "C +seams")):
                m, _ = build(n, kind, v)
                geo = hpkit.finish_geo(m)
                nb, q, mn, _ = hpkit.stats(geo)
                ov = hpkit.overlaps(geo)
                allstats.append((kind, n, v, nb, q, len(ov)))
                print(kind, n, v, nb, q, "overlaps", len(ov))
                f = m.faces()
                cols.append((f"{label} ({nb}/{q})", [hp_view.shot(f, y, p, (300, 300)) for y, p in ((180, 10), (215, 20), (270, 8), (35, 20))]))
                if v != "now":
                    m.save(OUT / "assets" / f"{n}_{v}")
            voxel.sheet(f"{n} high-poly options", cols).save(OUT / f"hp_{kind}_{n}.png")


if __name__ == "__main__":
    main()
