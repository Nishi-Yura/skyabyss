"""Stone whale armor v2: three design bases (A lamellae knight, B living shell, C vestment).

python tools/rough/armor_v2_whale.py [A|B|C ...] [--out dir]   -> writes <out>/<opt>/ (geo, png, glow) and a sheet
"""
import math
import sys

sys.path.insert(0, "tools/rough")
import armor_kit as ak  # noqa: E402
from armor_kit import Mat  # noqa: E402

ROCK = [(16, 20, 30), (32, 38, 54), (54, 62, 82), (84, 94, 118), (124, 136, 162)]
BONE = [(104, 92, 74), (156, 142, 116), (200, 188, 160), (226, 217, 192), (248, 242, 224)]
CYAN = [(18, 68, 98), (36, 124, 164), (104, 212, 246), (200, 250, 255), (235, 255, 255)]
VOID = [(6, 8, 12), (12, 15, 22), (20, 24, 34), (32, 38, 52), (46, 54, 72)]
INDIGO = [(14, 16, 34), (26, 30, 62), (40, 48, 92), (62, 74, 128), (92, 108, 168)]

MATS = {
    "rock": Mat(ROCK, "rock", edge=0.55, ao=0.4, grain=0.10, panel=2, rivet=(200, 188, 160)),
    "rock2": Mat(ROCK, "ribbed", edge=0.6, ao=0.35, freq=1.2),
    "bone": Mat(BONE, "striate", edge=0.5, ao=0.3, freq=1.0, contrast=0.9),
    "glow": Mat(CYAN, "plain", glow=True, edge=0.0, ao=0.0),
    "void": Mat(VOID, "plain", edge=0.0, ao=0.0, grain=0.0),
    "cloth": Mat(INDIGO, "cloth", edge=0.2, ao=0.3),
}


CHAMFER = (0.5, 1, 2.0)


def new(opt):
    return ak.Armor(f"stone_whale_armor_{opt.lower()}", MATS)


# ============================================================ A: lamellae knight
def build_A():
    a = new("A")
    H, B, RA, RL, RB = "armorHead", "armorBody", "armorRightArm", "armorRightLeg", "armorRightBoot"
    # --- kabuto: tiered dome, bone band, V crest, cheek wings, neck lames
    a.box(H, (-4.6, 25.2, -4.6), (4.6, 28.6, 4.6), "rock")
    a.box(H, (-4.9, 28.6, -4.9), (4.9, 29.6, 4.9), "bone")
    a.box(H, (-4.5, 29.6, -4.5), (4.5, 32.6, 4.5), "rock2")
    a.box(H, (-3.4, 25.8, -5.0), (3.4, 27.6, -4.6), "void", hide=("down",))
    a.box(H, (-2.8, 26.5, -5.15), (2.8, 26.9, -5.0), "glow")
    for sx in (-1, 1):
        a.spike(H, (sx * 1.0, 32.9, -3.4), (0, 0, sx * -28), 3, 7.0, 1.6, 0.5, 0.9, 0.5, "bone", bend=(0, 0, sx * 6))
        a.slab(H, (sx * 5.3, 26.6, -0.4), (0.8, 3.2, 6.4), "rock2", rot=(0, 0, sx * -12))
    for i, (y, w, ang) in enumerate([(26.4, 9.0, -14), (24.8, 10.2, -22)]):
        a.slab(H, (0, y, 5.4 + 0.8 * i), (w, 1.7, 0.9), "rock2", rot=(ang, 0, 0))
    # --- cuirass: pectoral, glow ridge, collar, belt
    a.box(B, (-4.6, 17.4, -2.8), (4.6, 23.4, 2.6), "rock")
    a.box(B, (-1.0, 18.0, -3.4), (1.0, 22.4, -2.8), "glow")
    a.box(B, (-3.6, 18.0, -3.2), (-1.0, 22.6, -2.8), "bone")
    a.box(B, (1.0, 18.0, -3.2), (3.6, 22.6, -2.8), "bone")
    a.box(B, (-3.4, 23.4, -2.6), (3.4, 25.0, 2.6), "bone")
    a.box(B, (-4.4, 14.6, -2.8), (4.4, 17.4, 2.6), "rock2")
    a.box(B, (-4.6, 12.4, -3.1), (4.6, 14.6, 2.9), "bone")
    a.box(B, (-1.2, 12.9, -3.5), (1.2, 14.1, -3.1), "glow")
    for i, y in enumerate((22.0, 19.6, 17.2)):
        a.slab(B, (0, y, 4.2 + 0.5 * i), (1.0, 2.4, 3.0 + 0.3 * i), "bone", rot=(-16, 0, 0))
    # --- right arm: three-layer pauldron (sode), vambrace with flared cuff
    a.box(RA, (-8.4, 13.2, -2.5), (-4.6, 22.0, 2.5), "rock")
    a.box(RA, (-9.2, 11.4, -3.3), (-4.6, 13.2, 3.3), "bone")
    g = a.group(RA, (-6.0, 22.4, 0), (0, 0, -12))
    g.box((-7.6, 0, -3.8), (0.6, 1.8, 3.8), "rock")
    g.box((-8.0, 0.2, -3.4), (-7.6, 1.6, 3.4), "bone")
    g.box((-6.6, 1.8, -3.3), (0.2, 3.6, 3.3), "rock2")
    g.box((-7.0, 2.0, -2.9), (-6.6, 3.4, 2.9), "bone")
    g.box((-5.2, 3.6, -2.7), (-0.2, 5.0, 2.7), "rock")
    a.slab(RA, (-9.4, 17.4, 0), (0.9, 5.0, 3.8), "rock2", rot=(0, 0, -10))
    a.mirror_bone(RA, "armorLeftArm")
    # --- right leg: tasset lames flaring forward and out, knee wing
    a.box(RL, (-4.4, 6.4, -2.4), (-0.1, 10.6, 2.4), "rock")
    a.box(RL, (-4.5, 10.6, -2.7), (-0.1, 12.0, 2.7), "bone")
    for i in range(3):
        a.slab(RL, (-2.3 - 0.2 * i, 9.6 - 2.1 * i, -3.2 - 0.5 * i), (4.2 + 0.4 * i, 2.0, 0.9), "rock2" if i != 1 else "bone", rot=(14 + 5 * i, 0, 0))
    a.slab(RL, (-5.4, 8.0, 0), (1.0, 4.0, 5.2), "rock2", rot=(0, 0, -12))
    a.mirror_bone(RL, "armorLeftLeg")
    a.box(RB, (-4.4, 0.0, -2.4), (-0.1, 6.4, 2.4), "rock")
    a.box(RB, (-4.3, 0.0, -4.4), (-0.2, 1.8, -2.4), "rock2")
    a.slab(RB, (-2.3, 4.6, -3.0), (3.6, 4.0, 0.8), "bone", rot=(8, 0, 0))
    a.slab(RB, (-2.3, 2.4, -5.0), (3.6, 0.8, 2.6), "bone", rot=(-22, 0, 0))
    a.mirror_bone(RB, "armorLeftBoot")
    return a


# ============================================================ B: living shell
def build_B():
    a = new("B")
    H, B, RA, LA, RL, RB = "armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorRightBoot"
    # --- head: skull dome in two tiers, bone band, swept-back side fins, jawbone guards, dorsal crest
    a.box(H, (-4.6, 25.2, -4.6), (4.6, 28.6, 4.6), "rock")
    a.box(H, (-4.9, 28.6, -4.9), (4.9, 29.6, 4.9), "bone")
    a.box(H, (-4.5, 29.6, -4.5), (4.5, 32.6, 4.5), "rock2")
    a.box(H, (-3.4, 25.8, -5.0), (3.4, 27.6, -4.6), "void", hide=("down",))
    a.box(H, (-2.8, 26.5, -5.15), (2.8, 26.9, -5.0), "glow")
    for sx in (-1, 1):
        a.spike(H, (sx * 5.3, 30.4, 0.0), (62, 0, sx * -10), 4, 8.0, 1.6, 0.6, 1.4, 0.6, "bone", bend=(14, 0, 0))
        a.slab(H, (sx * 5.3, 26.6, -0.4), (0.8, 3.2, 6.4), "bone", rot=(0, 0, sx * -10))
    a.spike(H, (0, 32.9, -2.4), (-10, 0, 0), 3, 5.0, 1.4, 0.9, 4.4, 2.6, "bone", bend=(-16, 0, 0))
    a.slab(H, (0, 26.4, 5.6), (8.8, 1.7, 0.9), "rock2", rot=(-14, 0, 0))
    # --- body: ribcage chest with a dark cavity and glow core, collar, belt, carapace and dorsal fins behind
    a.box(B, (-4.6, 17.0, -0.2), (4.6, 23.4, 2.6), "rock")
    a.box(B, (-4.0, 17.0, -2.4), (4.0, 23.4, -0.2), "void")
    a.box(B, (-1.6, 18.2, -3.0), (1.6, 22.6, -2.4), "glow")
    a.box(B, (-3.4, 23.4, -2.6), (3.4, 25.0, 2.6), "bone")
    for y in (18.0, 20.3, 22.4):
        a.fan(B, "y", (0, y, 0.0), 4.4, -142, -38, 6, 1.3, 1.0, "bone")
    a.box(B, (-4.4, 14.8, -2.6), (4.4, 17.0, 2.6), "rock2")
    a.box(B, (-4.6, 12.4, -2.9), (4.6, 14.8, 2.9), "bone")
    a.box(B, (-1.1, 12.9, -3.3), (1.1, 14.3, -2.9), "glow")
    a.fan(B, "x", (0, 19.8, 0), 3.4, 62, 132, 4, 8.8, 1.0, "rock2")
    for i, y in enumerate((22.6, 19.8, 17.0)):
        a.slab(B, (0, y, 6.2 + 0.3 * i), (1.3, 2.4, 2.2), "bone", rot=(-38, 0, 0))
    for sx in (-1, 1):
        a.slab(B, (sx * 5.0, 13.4, 7.6), (5.6, 1.2, 7.0), "rock2", rot=(-16, sx * 32, 0))
    # --- right arm: pauldron on a frame with a curling tusk
    a.box(RA, (-8.4, 13.0, -2.5), (-4.6, 22.0, 2.5), "rock")
    a.box(RA, (-8.9, 11.4, -2.9), (-4.6, 13.0, 2.9), "bone")
    g = a.group(RA, (-6.0, 22.4, 0), (0, 0, -14))
    g.box((-7.4, 0, -3.6), (0.6, 2.4, 3.6), "rock")
    g.box((-6.2, 2.4, -3.0), (0.0, 4.6, 3.0), "rock2")
    a.spike(RA, g.point((-4.4, 4.6, 0)), (0, 0, -34), 4, 7.0, 2.2, 0.6, 2.2, 0.6, "bone", bend=(0, 0, 11))
    # --- left arm: smaller pauldron over a hanging pectoral fin
    a.box(LA, (4.6, 13.0, -2.5), (8.4, 22.0, 2.5), "rock")
    a.box(LA, (4.6, 11.4, -2.9), (8.9, 13.0, 2.9), "bone")
    g = a.group(LA, (6.0, 22.4, 0), (0, 0, 14))
    g.box((-0.6, 0, -3.6), (7.4, 2.4, 3.6), "rock")
    a.spike(LA, g.point((7.2, 0.4, 0)), (0, 0, 172), 4, 12.0, 1.6, 0.8, 5.6, 2.2, "bone", bend=(0, 0, -3))
    # --- legs and boots
    a.box(RL, (-4.4, 6.4, -2.4), (-0.1, 10.6, 2.4), "rock")
    a.box(RL, (-4.5, 10.6, -2.7), (-0.1, 12.0, 2.7), "bone")
    a.slab(RL, (-5.6, 8.2, 0), (1.2, 4.0, 5.6), "rock2", rot=(0, 0, -14))
    a.spike(RL, (-2.3, 8.0, -2.7), (-102, 0, 0), 2, 3.0, 1.6, 0.7, 2.0, 0.9, "bone")
    a.mirror_bone(RL, "armorLeftLeg")
    a.box(RB, (-4.4, 0.0, -2.4), (-0.1, 6.4, 2.4), "rock")
    a.box(RB, (-4.3, 0.0, -4.4), (-0.2, 1.8, -2.4), "rock2")
    a.slab(RB, (-5.3, 3.4, 0), (0.8, 4.2, 4.0), "bone", rot=(0, 0, -12))
    a.slab(RB, (-2.3, 2.6, -4.8), (3.6, 0.8, 2.8), "bone", rot=(-24, 0, 0))
    a.slab(RB, (-2.3, 3.6, 3.9), (3.4, 0.8, 2.4), "bone", rot=(24, 0, 0))
    a.mirror_bone(RB, "armorLeftBoot")
    return a


# ============================================================ C: vestment (cloth, ring, sash)
def build_C():
    a = new("C")
    H, B, RA, RL, RB = "armorHead", "armorBody", "armorRightArm", "armorRightLeg", "armorRightBoot"
    # --- hood with a bone mask, a peak curling back, and a halo ring on a stalk
    a.box(H, (-4.7, 25.2, -4.7), (4.7, 32.7, 4.7), "cloth")
    a.spike(H, (0, 32.7, 0.6), (-12, 0, 0), 3, 7.0, 3.6, 1.0, 3.6, 1.0, "cloth", bend=(-14, 0, 0))
    a.box(H, (-3.4, 25.4, -5.3), (3.4, 29.6, -4.7), "bone")
    a.box(H, (-2.6, 27.0, -5.5), (2.6, 27.8, -5.3), "void")
    a.box(H, (-2.0, 27.2, -5.6), (2.0, 27.6, -5.5), "glow")
    for sx in (-1, 1):
        a.box(H, (sx * 2.2 - 0.3, 25.4, -5.6), (sx * 2.2 + 0.3, 26.8, -5.3), "bone")
    a.slab(H, (0, 24.6, 5.3), (9.2, 3.6, 0.9), "cloth", rot=(-12, 0, 0))
    a.box(H, (-0.6, 26.0, 4.7), (0.6, 28.4, 7.6), "bone")
    a.fan(H, "z", (0, 30.0, 8.1), 5.6, -180, 180, 14, 0.9, 0.9, "bone")
    # --- robe with a V of bone beads, sash, ritual disc on the back
    a.box(B, (-4.6, 14.8, -2.7), (4.6, 23.4, 2.7), "cloth")
    for i in range(4):
        a.box(B, (-3.2 + i * 0.8, 22.6 - i * 1.2, -3.3), (-2.4 + i * 0.8, 23.4 - i * 1.2, -2.7), "bone")
        a.box(B, (2.4 - i * 0.8, 22.6 - i * 1.2, -3.3), (3.2 - i * 0.8, 23.4 - i * 1.2, -2.7), "bone")
    a.box(B, (-1.0, 17.0, -3.4), (1.0, 19.0, -2.7), "glow")
    a.box(B, (-3.0, 23.4, -2.9), (3.0, 25.0, 2.9), "bone")
    a.box(B, (-4.6, 12.4, -3.0), (4.6, 14.8, 3.0), "bone")
    a.box(B, (-4.6, 13.0, -3.3), (4.6, 14.2, -3.0), "cloth")
    a.box(B, (-1.2, 12.6, -3.7), (1.2, 14.4, -3.3), "glow")
    a.fan(B, "z", (0, 19.2, 4.4), 3.6, -180, 180, 12, 0.9, 0.9, "bone")
    a.box(B, (-1.0, 18.2, 2.7), (1.0, 20.2, 4.4), "glow")
    # --- right arm: wide sleeve, bone mantle plates
    a.box(RA, (-8.4, 13.2, -2.6), (-4.6, 22.0, 2.6), "cloth")
    a.box(RA, (-9.8, 11.0, -3.6), (-4.6, 13.2, 3.6), "cloth")
    a.fan(RA, "z", (-6.2, 20.4, 0), 3.4, -92, -16, 4, 5.8, 1.1, "bone")
    a.box(RA, (-9.6, 10.6, -3.4), (-4.8, 11.0, 3.4), "bone")
    a.mirror_bone(RA, "armorLeftArm")
    # --- legs: wide cloth, a split tabard on each leg, bone greaves
    a.box(RL, (-4.5, 6.8, -2.6), (-0.1, 10.8, 2.6), "cloth")
    a.box(RL, (-4.5, 10.8, -2.8), (-0.1, 12.0, 2.8), "bone")
    a.box(RL, (-4.0, 3.2, -3.4), (-0.5, 11.0, -2.8), "cloth")
    a.box(RL, (-4.0, 2.6, -3.5), (-0.5, 3.2, -2.7), "bone")
    a.mirror_bone(RL, "armorLeftLeg")
    a.box(RB, (-4.4, 0.0, -2.4), (-0.1, 6.4, 2.4), "cloth")
    a.box(RB, (-4.3, 0.0, -4.4), (-0.2, 1.8, -2.4), "bone")
    a.box(RB, (-4.6, 5.0, -2.7), (-0.1, 6.4, 2.7), "bone")
    a.box(RB, (-3.8, 1.8, -2.8), (-0.6, 5.0, -2.4), "bone")
    a.mirror_bone(RB, "armorLeftBoot")
    return a



def build(opt):
    return {"A": build_A, "B": build_B, "C": build_C}[opt]()


if __name__ == "__main__":
    import armor_v2_common as c
    import json
    from pathlib import Path
    from PIL import Image
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("opts", nargs="*", default=["A"])
    ap.add_argument("--out", default="tools/rough/_out")
    ar = ap.parse_args()
    opts, out = ar.opts, Path(ar.out)
    cols, labels = [], []
    g0, t0, gl0 = c.load_current("stone_whale_armor")
    cols.append(c.column(g0, t0, gl0, size=(260, 340)))
    labels.append("now")
    for o in opts:
        a = build(o)
        ov = a.overlaps()
        geo, tex, glow = a.build(chamfer=CHAMFER)
        n, q = a.stats()
        print(o, "cubes", n, "faces", q, "tex", tex.shape[0], "overlaps", len(ov)); [print("  ", x) for x in ov]
        d = out / o
        d.mkdir(parents=True, exist_ok=True)
        (d / "armor.geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex).save(d / "armor.png")
        Image.fromarray(glow).save(d / "armor_glowmask.png")
        cols.append(c.column(geo, tex, glow, size=(260, 340)))
        labels.append(f"{o} ({n}/{q})")
    rl = [v[0] for v in c.VIEWS] + [v[0] for v in c.POSE_VIEWS]
    c.sheet("stone whale armor v2", cols, labels, rl, size=(260, 340)).save(out / "sheet.png")
