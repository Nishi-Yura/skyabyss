"""The four titan armor sets of design base v2: python tools/rough/armor_v2_sets.py [names] [--out dir]

Each set = the layered-plate frame (armor_v2_parts) + one silhouette motif of its titan + its own materials.
  stone_whale : rock + whale bone      motif: swept dorsal-fin crest, rib cage over the glow core, rising tusk, fluke tail
  tide_shell  : coral + pearl nacre    motif: coral antlers, scallop-ridged shoulder, coral spray on the back, fin boots
  thunder_wing: steel + feather        motif: two feather wings on the back, winged helm, lightning strokes, long tassets
  ember       : cracked ash + slag     motif: one broken horn, big slag pauldron against a bare one, ragged skirt, furnace grille
"""
import math
import sys

sys.path.insert(0, "tools/rough")
import armor_kit as ak  # noqa: E402
from armor_kit import Mat  # noqa: E402
import armor_v2_parts as P  # noqa: E402
from armor_v2_parts import H, B, RA, LA, RL, LL, RB, LB  # noqa: E402

CHAMFER = (0.5, 1, 2.0)
M_STD = {"main": "main", "alt": "alt", "trim": "trim", "void": "void", "glow": "glow"}


def R(*stops):
    return list(stops)


# ============================================================ materials
VOID = R((6, 8, 12), (12, 15, 22), (20, 24, 34), (32, 38, 52), (46, 54, 72))

WHALE = {
    "main": Mat(R((22, 28, 40), (44, 52, 72), (72, 84, 108), (108, 122, 148), (150, 164, 190)), "rock", edge=0.6, ao=0.35, grain=0.10, panel=2, rivet=(214, 202, 172)),
    "alt": Mat(R((26, 32, 46), (50, 60, 82), (80, 94, 120), (116, 132, 160), (156, 172, 198)), "ribbed", edge=0.6, ao=0.3, freq=0.8, contrast=0.55),
    "trim": Mat(R((110, 98, 78), (162, 148, 120), (204, 192, 164), (230, 221, 196), (250, 244, 228)), "striate", edge=0.5, ao=0.3, contrast=0.8),
    "glow": Mat(R((18, 68, 98), (36, 124, 164), (104, 212, 246), (200, 250, 255), (235, 255, 255)), "plain", glow=True, edge=0, ao=0),
    "void": Mat(VOID, "plain", edge=0, ao=0, grain=0),
}
TIDE = {
    "main": Mat(R((14, 44, 48), (26, 80, 82), (44, 120, 114), (78, 164, 152), (126, 208, 192)), "pit", edge=0.6, ao=0.35, panel=2, rivet=(240, 226, 236)),
    "alt": Mat(R((20, 58, 64), (36, 96, 100), (62, 136, 134), (106, 178, 170), (162, 218, 206)), "ribbed", edge=0.6, ao=0.3, freq=0.9, contrast=0.55),
    "trim": Mat(R((124, 104, 128), (170, 150, 172), (202, 186, 202), (222, 210, 222), (238, 230, 238)), "nacre", edge=0.5, ao=0.25, accent=(120, 220, 210)),
    "coral": Mat(R((96, 32, 52), (150, 60, 84), (206, 98, 114), (236, 140, 140), (255, 190, 176)), "pit", edge=0.5, ao=0.3),
    "glow": Mat(R((120, 40, 80), (220, 80, 130), (255, 140, 180), (255, 200, 220), (255, 240, 245)), "plain", glow=True, edge=0, ao=0),
    "void": Mat(VOID, "plain", edge=0, ao=0, grain=0),
}
STORM = {
    "main": Mat(R((22, 32, 58), (40, 58, 98), (66, 94, 144), (106, 140, 192), (158, 186, 226)), "metal", edge=0.6, ao=0.35, panel=2, rivet=(214, 222, 240), contrast=0.9),
    "alt": Mat(R((30, 44, 78), (54, 76, 124), (88, 116, 172), (132, 162, 210), (190, 212, 240)), "feather", edge=0.5, ao=0.3, freq=1.0),
    "trim": Mat(R((80, 88, 108), (124, 132, 154), (166, 174, 192), (198, 206, 222), (224, 230, 242)), "metal", edge=0.5, ao=0.25),
    "copper": Mat(R((96, 44, 20), (160, 84, 34), (214, 128, 52), (244, 176, 88), (255, 220, 140)), "metal", edge=0.5, ao=0.25),
    "glow": Mat(R((80, 120, 200), (140, 190, 255), (200, 230, 255), (240, 250, 255), (255, 255, 255)), "plain", glow=True, edge=0, ao=0),
    "void": Mat(VOID, "plain", edge=0, ao=0, grain=0),
}
EMBER = {
    "main": Mat(R((12, 10, 11), (24, 21, 23), (40, 35, 37), (62, 55, 56), (92, 84, 82)), "plate_cracked", edge=0.6, ao=0.35, grain=0.12, panel=2, rivet=(150, 124, 104), crack=(255, 130, 36)),
    "alt": Mat(R((26, 22, 22), (48, 40, 38), (76, 64, 58), (110, 94, 84), (150, 130, 114)), "metal", edge=0.55, ao=0.3),
    "trim": Mat(R((34, 28, 26), (62, 50, 44), (96, 78, 66), (136, 112, 94), (178, 150, 124)), "metal", edge=0.5, ao=0.3, contrast=0.9),
    "rag": Mat(R((30, 22, 22), (52, 36, 32), (80, 52, 42), (112, 72, 56), (150, 98, 72)), "cloth", edge=0.2, ao=0.3),
    "glow": Mat(R((150, 40, 10), (230, 90, 20), (255, 150, 40), (255, 200, 90), (255, 240, 170)), "plain", glow=True, edge=0, ao=0),
    "void": Mat(VOID, "plain", edge=0, ao=0, grain=0),
}


def cross_check(a):
    return a


# ============================================================ stone whale
def stone_whale():
    a = ak.Armor("stone_whale_armor", WHALE)
    M = M_STD
    P.kabuto(a, M, neck=2, cheek=False)
    for sx in (-1, 1):  # jawbone guards, wide and bone
        a.slab(H, (sx * 5.4, 26.6, -0.4), (1.1, 3.4, 6.6), "trim", rot=(0, 0, sx * -12))
    a.spike(H, (0, 32.9, -3.2), (-8, 0, 0), 6, 9.0, 1.2, 0.7, 4.2, 1.6, "trim", bend=(7, 0, 0))   # swept dorsal fin
    # chest: bulging rib arcs over a glow core instead of the plain ridge
    P.cuirass(a, M, ridge=False, front=False)
    a.box(B, (-1.5, 18.0, -3.8), (1.5, 22.6, -3.4), "glow")
    for y in (18.4, 20.4, 22.2):
        a.fan(B, "y", (0, y, -0.4), 4.2, -135, -45, 5, 1.1, 0.9, "trim")
    a.box(B, (-1.0, 12.8, 3.1), (1.0, 14.0, 4.8), "trim")
    for sx in (-1, 1):  # fluke tail
        a.slab(B, (sx * 4.6, 13.4, 7.4), (5.4, 1.2, 6.6), "alt", rot=(-16, sx * 30, 0))
    # arms: right has the rising tusk, left the plain pauldron
    P.arm_shell(a, M, RA)
    g = P.sode(a, M, RA, layers=3)
    a.mirror_bone(RA, LA)
    top = 2.2 + 2.0 + 1.6
    a.spike(RA, g.point((-4.4, top, 0)), (0, 0, -26), 6, 8.0, 2.2, 0.6, 2.2, 0.6, "trim", bend=(0, 0, 6))
    P.leg_set(a, M)
    P.boots(a, M)
    return a


# ============================================================ tide shell
def tide_shell():
    a = ak.Armor("tide_shell_armor", TIDE)
    M = {"main": "main", "alt": "alt", "trim": "trim", "void": "void", "glow": "glow"}
    P.kabuto(a, M, neck=0, cheek=True)
    a.box(H, (-0.9, 29.7, -5.0), (0.9, 31.5, -4.5), "glow")        # brow pearl
    for sx in (-1, 1):  # coral antlers with a branch
        a.spike(H, (sx * 2.4, 32.9, -0.6), (0, 0, sx * -18), 3, 7.0, 1.8, 0.6, 1.8, 0.6, "coral", bend=(0, 0, sx * 7))
        a.spike(H, (sx * 4.4, 36.4, -0.6), (0, 0, sx * -58), 2, 3.4, 1.2, 0.5, 1.2, 0.5, "coral")
    P.cuirass(a, M, spine=False)
    # coral spray on the upper back (above the arm swing)
    for i, (x, rz, rx) in enumerate([(-3.2, 24, 30), (0, 0, 38), (3.2, -24, 30)]):
        a.spike(B, (x, 21.8, 4.5), (rx, 0, rz), 3, 6.0, 1.6, 0.5, 1.6, 0.5, "coral", bend=(8, 0, 0))
    P.arm_shell(a, M, RA)
    g = P.sode(a, M, RA, layers=3, mats=["main", "alt", "main"])
    y = 2.2 + 2.0 + 1.6
    for i in range(3):  # scallop ridges across the top lame
        g.box((-4.2 + i * 1.5, y, -2.2), (-3.6 + i * 1.5, y + 0.8, 2.2), "trim")
    a.mirror_bone(RA, LA)
    P.leg_set(a, M, tass_mat="alt")
    P.boots(a, M)
    for sx, bone in ((-1, RB), (1, LB)):  # fin flare on the outer ankle
        a.slab(bone, (sx * 5.4, 2.6, 0.4), (0.8, 3.4, 4.4), "alt", rot=(0, 0, sx * -18))
    return a


# ============================================================ thunder wing
def thunder_wing():
    a = ak.Armor("thunder_wing_armor", STORM)
    M = {"main": "main", "alt": "alt", "trim": "trim", "void": "void", "glow": "glow"}
    P.kabuto(a, M, neck=0, cheek=False)
    for sx in (-1, 1):  # winged helm: three feathers fanned back from each temple
        for i in range(3):
            a.slab(H, (sx * (6.0 + 0.9 * i), 29.8 + 0.1 * i, -1.2 + 1.9 * i), (0.7, 1.5, 3.2), "alt", rot=(0, sx * (-12 - 14 * i), sx * -6))
    a.spike(H, (0, 32.9, -2.0), (-6, 0, 0), 4, 8.0, 1.0, 0.6, 3.2, 1.2, "alt", bend=(14, 0, 0))
    P.cuirass(a, M, ridge=False, front=False)
    for x0, x1, y0, y1 in [(-1.0, 2.4, 21.9, 22.7), (-1.0, -0.2, 20.5, 21.9), (-2.4, 1.0, 19.8, 20.5), (0.2, 1.0, 18.4, 19.8), (-1.4, 1.8, 17.7, 18.4)]:
        a.box(B, (x0, y0, -3.8), (x1, y1, -3.4), "glow")        # lightning bolt
    # wings: two layers of broad tapering feathers fanning up and out from each shoulder blade (y >= 17, behind the arm swing)
    for sx in (-1, 1):
        for lay, angs in enumerate(((14, 39, 64), (26, 51, 76))):
            for i, ang in enumerate(angs):
                L = 14.5 - 1.5 * i - 1.8 * lay
                rot = (0, 0, -sx * ang)
                g = a.group(B, (sx * 2.2, 22.2 - 0.5 * lay, 5.8 + 1.1 * lay), rot)
                a.spike(B, g.point((0, 6.0, 0)), rot, 3, L, 2.8, 1.3, 1.0, 0.7, "alt" if lay == 0 else "main", bend=(0, 0, -sx * 4))
    P.arm_shell(a, M, RA)
    g = P.sode(a, M, RA, layers=3, mats=["main", "alt", "main"])
    for i in range(3):  # hanging feather tips
        g.slab((-6.6 + i * 1.6, -2.6, 0), (1.4, 2.8, 0.7), "alt", rot=(0, 0, -8))
    a.mirror_bone(RA, LA)
    P.leg_set(a, M, tassets=3, tass_mat="alt")
    P.boots(a, M)
    for sx, bone in ((-1, RB), (1, LB)):  # winged heels
        a.slab(bone, (sx * 5.0, 3.0, 3.2), (0.7, 3.4, 4.6), "alt", rot=(0, sx * -22, sx * -14))
    return a


# ============================================================ ember
def ember():
    a = ak.Armor("ember_armor", EMBER)
    M = {"main": "main", "alt": "alt", "trim": "trim", "void": "void", "glow": "glow"}
    P.kabuto(a, M, neck=2, cheek=False)
    a.spike(H, (3.0, 32.9, -1.0), (0, 0, -16), 4, 8.4, 2.0, 0.6, 2.0, 0.6, "alt", bend=(0, 0, 7))      # the whole horn
    a.spike(H, (-3.0, 32.9, -1.0), (0, 0, 14), 2, 2.4, 2.0, 1.6, 2.0, 1.6, "alt")                        # the broken stump
    a.box(H, (-3.4, 35.5, -1.4), (-2.0, 36.1, -0.2), "glow")
    for i in range(3):
        a.spike(H, (-1.6 + i * 1.6, 32.9, 3.2), (-20, 0, 0), 2, 2.8, 1.0, 0.5, 1.0, 0.5, "trim")
    P.cuirass(a, M, ridge=False, front=False)
    a.box(B, (-3.2, 17.8, -3.8), (3.2, 23.0, -3.4), "void")           # furnace grille
    a.box(B, (-2.6, 18.4, -4.1), (2.6, 22.4, -3.8), "glow")
    for x in (-1.6, 0.0, 1.6):
        a.box(B, (x - 0.35, 18.0, -4.5), (x + 0.35, 22.8, -4.1), "alt")
    # asymmetric shoulders: heavy slag pauldron on the left, bare shell on the right
    P.arm_shell(a, M, RA)
    P.sode(a, M, RA, layers=2)
    P.arm_shell(a, M, LA, sign=1)
    gl = a.group(LA, (6.9, 23.0, 0), (0, 0, 12))
    y = 0.0
    for i, h in enumerate((1.8, 1.8, 1.6, 1.4)):
        L = 8.6 - 1.0 * i
        d = 4.2 - 0.5 * i
        gl.box((-0.6, y, -d), (L - 0.6, y + h, d), "main" if i % 2 == 0 else "alt")
        y += h
    a.spike(LA, gl.point((4.0, y, 0)), (0, 0, 24), 3, 6.4, 2.4, 0.6, 2.4, 0.6, "alt", bend=(0, 0, -6))
    P.leg_set(a, M, tassets=3, tass_mat="rag")
    P.boots(a, M)
    return a


SETS = {"stone_whale": stone_whale, "tide_shell": tide_shell, "thunder_wing": thunder_wing, "ember": ember}


def build_all(names=None):
    out = {}
    for n in names or SETS:
        a = SETS[n]()
        out[n] = a
    return out


if __name__ == "__main__":
    import argparse
    import json
    from pathlib import Path

    import numpy as np
    from PIL import Image

    import armor_v2_common as c

    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*", default=list(SETS))
    ap.add_argument("--out", default="tools/rough/_out_sets")
    ar = ap.parse_args()
    out = Path(ar.out)
    cols, labels = [], []
    for n in ar.names:
        a = SETS[n]()
        ov = a.overlaps()
        geo, tex, glow = a.build(chamfer=CHAMFER)
        cu, fq = a.stats()
        print(n, "cubes", cu, "faces", fq, "tex", tex.shape[0], "overlaps", len(ov))
        for x in ov:
            print("  ", x)
        d = out / n
        d.mkdir(parents=True, exist_ok=True)
        (d / "armor.geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex).save(d / "armor.png")
        Image.fromarray(glow).save(d / "armor_glowmask.png")
        cols.append(c.column(geo, tex, glow, size=(260, 340)))
        labels.append(f"{n} ({cu}/{fq})")
    rl = [v[0] for v in c.VIEWS] + [v[0] for v in c.POSE_VIEWS]
    c.sheet("armor v2 sets", cols, labels, rl, size=(260, 340)).save(out / "sheet.png")
