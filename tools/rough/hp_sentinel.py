"""High-poly rough 1: bone_sentinel (+ shield). 3 options vs now.

  A  chamfer only          : one-step chamfer (0.5) on every box, nothing else (automatic, no art work)
  B  two-step round + trim : A + two-step round on the big pieces + 0.25-unit trim (brow, cheek, frame, knuckles, studs)
  C  carve + fingers       : B + carved eye sockets / nose with glowing eyes, individual fingers, ribs, knee caps

python tools/rough/hp_sentinel.py  -> docs/review/high_poly_roughs/bone_sentinel.png (+ assets/bone_sentinel_<A|B|C>/)
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import hp_view
import hpkit
import voxel
from PIL import Image

OUT = Path("docs/review/high_poly_roughs")


def face_rgb(m, bone, idx, face="north"):
    c = m.bone(bone)["cubes"][idx]
    f = c["uv"][face]
    u, v = f["uv"]
    w, h = f["uv_size"]
    return m.color_at((u + w / 2 - 0.01, v + h / 2 - 0.01))


def shade(rgb, k):
    return tuple(max(0, min(255, int(v * k))) for v in rgb)


def build(variant):
    m = hpkit.Model.load("bone_sentinel")
    base = m.copy()
    bone_c = face_rgb(base, "head", 1)
    dark_c = face_rgb(base, "head", 2)
    plate_c = face_rgb(base, "body", 4)
    eye_c = (120, 235, 255)
    if variant == "now":
        return m, base

    def rule_big(b, c):
        s = sorted(c["size"])
        if variant == "C" and s[1] >= 6 and s[0] >= 3:
            return dict(R=3)
        if s[1] >= 4 and s[0] >= 1.5:
            return dict(R=2)
        if s[1] >= 2:
            return dict(R=1)
        return None

    if variant == "A":
        m.round_all(step=0.5, R=1, min_dim=2.0)
        return m, base

    skull = m.bone("head")["cubes"][1]
    if variant == "B":
        m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule_big)
    else:  # C: carve the skull after rounding everything else
        m.round_all(step=0.5, R=1, min_dim=2.0, rule=rule_big, skip=lambda b, c: c is skull)
        holes = []
        for sx in (-1, 1):
            x0, x1 = (1, 3) if sx > 0 else (-3, -1)
            holes.append(((x0, 27, -3.5), (x1, 29, -1.5)))      # eye sockets, 2 deep
        holes.append(((-0.5, 25.5, -3.5), (0.5, 27, -2.0)))      # nose cavity
        cubes = m.bone("head")["cubes"]
        i = next(k for k, c in enumerate(cubes) if c is skull)
        cubes[i:i + 1] = hpkit.round_cube(skull, step=0.5, R=3, holes=holes)

    # ---- 0.25 trim (B and C). Every box sits ON a surface (no overlap with the old boxes).
    # skull: brow ridge above the brow plate, cheekbones, temple studs
    m.add_box("head", (-3.5, 30.0, -4.5), (7.0, 0.5, 0.5), rgb=dark_c)
    for sx in (-1, 1):
        xa = 2.5 if sx > 0 else -3.5
        m.add_box("head", (xa, 25.5, -4.5), (1.0, 1.0, 0.5), rgb=bone_c)                      # cheekbone
        xs = 4.5 if sx > 0 else -4.75
        m.add_box("head", (xs, 25.5, -2.5), (0.25, 1.0, 1.0), rgb=dark_c)                     # temple stud
    # chest plate: raised frame + centre ridge + rivets (plate front at z=-2)
    fc = shade(plate_c, 1.35)
    m.add_box("body", (-3.5, 19.25, -2.25), (7.0, 0.5, 0.25), rgb=fc)
    m.add_box("body", (-3.5, 14.25, -2.25), (7.0, 0.5, 0.25), rgb=fc)
    m.add_box("body", (-3.75, 14.75, -2.25), (0.5, 4.5, 0.25), rgb=fc)
    m.add_box("body", (3.25, 14.75, -2.25), (0.5, 4.5, 0.25), rgb=fc)
    m.add_box("body", (-0.25, 14.75, -2.25), (0.5, 4.5, 0.25), rgb=shade(plate_c, 1.15))
    # hand: knuckles on the fist front
    for bn, sx in (("left_forearm", 1), ("right_forearm", -1)):
        x0 = 4.0 if sx > 0 else -7.0
        for i in range(4):
            m.add_box(bn, (x0 + 0.25 + i * 0.7, 7.25, -1.75), (0.5, 0.75, 0.25), rgb=shade(bone_c, 1.1))
    if variant == "C":
        # fingers: four separate boxes hanging under the fist
        for bn, sx in (("left_forearm", 1), ("right_forearm", -1)):
            x0 = 4.0 if sx > 0 else -7.0
            for i in range(4):
                m.add_box(bn, (x0 + 0.25 + i * 0.7, 5.5, -1.5), (0.5, 1.5, 0.5), rgb=shade(bone_c, 0.92))
        # ribs under the chest plate (the belly gap y11..14 shows the spine, add 3 ribs wrapping the front)
        for k in range(2):
            m.add_box("body", (-3.0, 12.25 + k * 1.0, -2.25), (6.0, 0.5, 0.5), rgb=shade(bone_c, 0.85))
        # knee caps
        for bn, x0 in (("leg_l", 0.5), ("leg_r", -3.5)):
            m.add_box(bn, (x0, 5.0, -3.0), (3.0, 2.0, 0.5), rgb=bone_c)
        # glowing eye points inside the sockets
        for sx in (-1, 1):
            x0 = 1.25 if sx > 0 else -2.75
            m.add_box("head", (x0, 27.5, -2.0), (1.5, 1.0, 0.25), rgb=eye_c, glow=True)
    return m, base


def crop_head(im):
    w, h = im.size
    return im.crop((int(w * 0.30), int(h * 0.05), int(w * 0.70), int(h * 0.37))).resize((330, 264), Image.LANCZOS)


def render_all(save_assets=True):
    sh = hpkit.Model.load("bone_sentinel_shield", kind="gear")
    cols = []
    notes = []
    for v, label in (("now", "now"), ("A", "A chamfer"), ("B", "B round+trim"), ("C", "C carve+fingers")):
        m, base = build(v)
        faces = m.faces() + hp_view.gear_faces(m.geo, sh.geo, sh.tex, "socket_off")
        geo = hpkit.finish_geo(m)
        n, q, mn, bn = hpkit.stats(geo)
        ov = hpkit.overlaps(geo, detail=True)
        notes.append(f"{v}: {n} boxes / {q} quads")
        print(v, n, q, mn, "overlaps", len(ov))
        for o in ov[:8]:
            print("   ", o)
        row = [hp_view.shot(faces, y, p, (330, 330)) for _, y, p in [("front", 180, 10), ("3/4", 215, 20), ("back", 35, 20)]]
        big = hp_view.shot(faces, 190, 12, (900, 900))
        row.append(crop_head(big))
        cols.append((f"{label} ({n}/{q})", row))
        if save_assets and v != "now":
            m.save(OUT / "assets" / f"bone_sentinel_{v}")
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = voxel.sheet("bone_sentinel high-poly options", cols, note="; ".join(notes))
    sheet.save(OUT / "bone_sentinel.png")


if __name__ == "__main__":
    render_all()
