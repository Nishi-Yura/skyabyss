"""High-poly roughs 2 and 3: tide_heart_p1 (a heart, phase 1) and raiju (a beast). 3 options each vs now.

  A  chamfer 0.5 on every box (automatic)
  B  big boxes two-step round (0.5 x2), small boxes 0.25 chamfer
  C  B + raised ribs (0.25 high strips) on the big faces

python tools/rough/hp_proto.py [tide_heart_p1|raiju]  -> docs/review/high_poly_roughs/<name>.png (+ assets/<name>_<A|B|C>/)
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import hp_view
import hpkit
import voxel

OUT = Path("docs/review/high_poly_roughs")

CFG = {
    "tide_heart_p1": dict(
        views=[("front", 180, 10), ("3/4", 215, 22), ("back 3/4", 35, 25)],
        zoom=(180, 10, (0.30, 0.30, 0.70, 0.70)),
        ridge=dict(bones=None, face="north", spacing=2.0),
        title="tide_heart_p1 (shell heart, phase 1)",
    ),
    "raiju": dict(
        views=[("front", 180, 10), ("3/4", 215, 22), ("side", 270, 8)],
        zoom=(200, 14, (0.28, 0.0, 0.72, 0.42)),
        ridge=dict(bones={"body", "chest", "mane", "tail", "head"}, face="up", spacing=2.0),
        title="raiju (storm beast, mid boss)",
    ),
}


def rule_b(b, c):
    s = sorted(c["size"])
    if s[1] >= 4 and s[0] >= 2:
        return dict(step=0.5, R=2, min_dim=2.0)
    if s[0] >= 1.5:
        return dict(step=0.25, R=1, min_dim=1.0, min_center=0.5)
    return None


def build(name, variant):
    m = hpkit.Model.load(name)
    if variant == "now":
        return m
    if variant == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m
    m.round_all(rule=rule_b)
    if variant == "C":
        r = CFG[name]["ridge"]
        n = m.ridges(r["bones"], face=r["face"], spacing=r["spacing"])
        print("  ridges added", n)
    return m


def run(name):
    cfg = CFG[name]
    cols = []
    notes = []
    for v, label in (("now", "now"), ("A", "A chamfer"), ("B", "B round"), ("C", "C round+ribs")):
        m = build(name, v)
        faces = m.faces()
        geo = hpkit.finish_geo(m)
        n, q, mn, bn = hpkit.stats(geo)
        ov = hpkit.overlaps(geo, detail=True)
        print(name, v, n, q, mn, "overlaps", len(ov))
        for o in ov[:5]:
            print("   ", o)
        row = [hp_view.shot(faces, y, p, (330, 330)) for _, y, p in cfg["views"]]
        yaw, pitch, (x0, y0, x1, y1) = cfg["zoom"]
        big = hp_view.shot(faces, yaw, pitch, (1100, 1100))
        w, h = big.size
        row.append(big.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1))).resize((330, int(330 * (y1 - y0) / (x1 - x0))), 1))
        notes.append(f"{v}: {n} boxes / {q} quads")
        cols.append((f"{label} ({n}/{q})", row))
        if v != "now":
            m.save(OUT / "assets" / f"{name}_{v}")
    OUT.mkdir(parents=True, exist_ok=True)
    voxel.sheet(cfg["title"] + " high-poly options", cols, note="; ".join(notes)).save(OUT / f"{name}.png")


if __name__ == "__main__":
    for n in sys.argv[1:] or list(CFG):
        run(n)
