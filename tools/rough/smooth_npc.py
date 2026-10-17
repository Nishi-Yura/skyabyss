"""Smooth NPC roughs: storyteller (語り部) and Isana (whale_chaser), 3 options each, face integrated into one head volume.

python tools/rough/smooth_npc.py [storyteller|whale_chaser]  -> docs/review/smooth_npc_roughs/<name>/<a|b|c>/ + <name>.png

Face fix in all options: the head is ONE rounded volume (stepped sphere, 0.25 cells), the face is painted on it in 3D
(eyes, brows, mouth, blush follow the surface), nose / brow ridge are real bumps of the same skin volume, hair / hood /
hat wrap the skull as a shell with a window (hairline), and there is a neck + collar so the head sits on the body axis.
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import numpy as np
from PIL import Image

import hp_view
import smooth_kit as K
import voxel

OUT = Path("docs/review/smooth_npc_roughs")
E = K.Model.ellipsoid
C = K.Model.capsule
R = K.Model.rbox
CONE = K.Model.cone_y


def blend(a, b, t):
    t = float(np.clip(t, 0, 1))
    return tuple(np.array(a, float) * (1 - t) + np.array(b, float) * t)


def soft(d, r):
    """1 at d=0, 0 at d>=r (smooth)"""
    t = np.clip(1 - d / r, 0, 1)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ shared face paint
class Face:
    """Face parameters, head-local: eyes at (+-ex, ey), z>fz marks the front."""

    def __init__(self, ey, ex=1.45, brow_col=(200, 200, 205), eye_kind="closed", mouth_y=None, lash=None, glint=None,
                 stubble=0.0, skin=(214, 160, 120), hair=(210, 212, 218), blush=0.35, ez=0.2):
        self.ey, self.ex, self.brow_col, self.eye_kind = ey, ex, brow_col, eye_kind
        self.mouth_y = mouth_y if mouth_y is not None else ey - 2.0
        self.stubble, self.skin, self.hair, self.blush, self.lash, self.glint, self.ez = stubble, skin, hair, blush, lash, glint, ez


def face_paint(face, head_bones=("head",)):
    def paint(m, p, n, mat, bone):
        if bone not in head_bones or mat["name"] not in ("skin",):
            return None, False
        x, y, z = p
        if z < face.ez + 1.2:   # only the front of the head
            return None, False
        ax = abs(x)
        base = np.array(mat["albedo"], float)
        # cheeks blush
        d = np.hypot(ax - 2.15, y - (face.ey - 1.15))
        base = np.array(blend(base, (226, 120, 112), face.blush * soft(d, 1.4)))
        # eyes
        xe = ax - face.ex
        ye = y - face.ey
        if face.eye_kind == "closed":      # smiling arc
            curve = 0.30 * (xe / 0.8) ** 2 - 0.15
            dd = np.hypot(xe / 0.85, (ye - curve) / 0.2)
            if dd < 1:
                return (60, 38, 34), False
        elif face.eye_kind == "open":
            dd = np.hypot(xe / 0.55, ye / 0.42)
            if dd < 1:
                if face.glint and xe < -0.0 and ye > 0.0:
                    return face.glint, True
                return (40, 30, 34), False
            dd2 = np.hypot(xe / 0.8, (ye) / 0.6)
            if dd2 < 1:
                return tuple(np.array(base) * 0.92), False
        elif face.eye_kind == "slit":       # narrowed, heavy lid
            dd = np.hypot(xe / 0.7, (ye + 0.05) / 0.2)
            if dd < 1:
                if face.glint and xe > 0.1:
                    return face.glint, True
                return (36, 26, 28), False
            if np.hypot(xe / 0.85, (ye - 0.28) / 0.16) < 1:
                return tuple(np.array(base) * 0.7), False
        # brows
        yb = face.ey + 0.95 + 0.2 * (xe / 1.0)
        if abs(xe) < 1.15 and abs(y - yb) < 0.3 and face.brow_col is not None:
            return face.brow_col, False
        # mouth
        if abs(x) < 1.0 and abs(y - face.mouth_y - 0.25 * (x / 1.0) ** 2) < 0.2:
            return (140, 70, 66), False
        # stubble
        if face.stubble > 0 and y < face.mouth_y + 0.6 and ax > 0.2:
            h = (int(p[0] * 7.3) * 31 + int(p[1] * 9.1) * 17 + int(p[2] * 5.7)) % 5
            if h < 5 * face.stubble:
                return tuple(np.array(base) * 0.82), False
        return tuple(base), False
    return paint


# ------------------------------------------------------------------ storyteller
SKIN = (216, 164, 124)
SKIN_SH = (160, 104, 98)


def story_base(opt):
    m = K.Model(f"storyteller_{opt}", lo=(-14, -2, -14), hi=(14, 34, 14))
    m.add_mat("skin", SKIN, SKIN_SH, light=(1.0, 0.96, 0.9))
    m.add_mat("hair", (226, 228, 234), (120, 128, 160))
    m.add_mat("robe", (118, 112, 100), (62, 58, 84))
    m.add_mat("shawl", (46, 66, 118), (22, 30, 66))
    m.add_mat("rope", (150, 98, 54), (74, 44, 40))
    m.add_mat("shoe", (232, 228, 214), (130, 124, 150))
    m.add_mat("hood", (52, 72, 124), (24, 32, 70))
    m.add_mat("star", (130, 240, 250), (130, 240, 250))
    m.add_bone("root")
    m.add_bone("body", "root", (0, 12, 0), 0.5)
    m.add_bone("head", "body", (0, 19, 0.5), 0.25)
    m.add_bone("hair", "head", (0, 19, 0.5), 0.25)
    m.add_bone("hair2", "head", (0, 19, 0.5), 0.5)
    m.add_bone("shawl", "body", (0, 15, 0), 0.5)
    m.add_bone("armL", "body", (4.6, 16.4, 0.4), 0.5)
    m.add_bone("armR", "body", (-4.6, 16.4, 0.4), 0.5)
    m.add_bone("legL", "body", (1.4, 1.5, 0.5), 0.5)
    m.add_bone("legR", "body", (-1.4, 1.5, 0.5), 0.5)
    m.add_bone("cord", "body", (2.2, 10.5, 3.5), 0.5)
    # robe: long, slightly bell-shaped, centre a bit forward at the hem
    m.add("body", CONE(0, 0.2, 1.0, 14.5, 5.9, 3.9, 0.82), "robe")
    # neck
    m.add("body", C((0, 17.2, 0.5), (0, 19.8, 0.7), 1.55), "skin")
    # shawl: soft shoulder cape (a round yoke)
    m.add("shawl", R((0, 15.0, 0.0), (5.1, 2.6, 3.9), 1.9), "shawl")
    m.add("shawl", E((0, 17.4, 0.2), (3.1, 1.2, 2.7)), "shawl")          # collar roll hugging the neck
    # arms folded in front
    for s, bone in ((1, "armL"), (-1, "armR")):
        m.add(bone, C((s * 4.7, 15.8, 0.2), (s * 4.9, 12.6, 1.4), 1.55, 1.45), "robe")
        m.add(bone, C((s * 4.7, 12.6, 1.4), (s * 1.6, 12.2, 3.5), 1.45, 1.25), "robe")
        m.add(bone, E((s * 0.9, 12.1, 3.9), (1.15, 1.0, 1.0)), "skin")
    # feet
    for s, bone in ((1, "legL"), (-1, "legR")):
        m.add(bone, E((s * 1.5, 0.9, 2.2), (1.2, 0.9, 2.0)), "shoe")
    # cord tassel
    m.add("cord", C((2.2, 10.6, 3.9), (2.5, 7.2, 4.0), 0.55), "rope")
    return m


def story_paint(m_face, extra=None):
    fp = face_paint(m_face)

    def paint(m, p, n, mat, bone):
        r, g = fp(m, p, n, mat, bone)
        if r is not None:
            return r, g
        x, y, z = p
        nm = mat["name"]
        if nm == "robe" and bone == "body":
            ang = np.arctan2(x, z)
            fold = 1.0 + 0.07 * np.sin(ang * 9.0) + 0.04 * np.sin(ang * 23 + y * 0.3)
            col = np.array(mat["albedo"]) * fold
            if 10.6 < y < 11.7:
                col = np.array(blend(col, (150, 98, 54), 0.9))
            if y < 3.0:   # hem trim
                col = np.array(blend(col, (86, 78, 70), 0.6 * soft(abs(y - 1.6), 1.6)))
            return tuple(col), False
        if nm == "shawl":
            ang = np.arctan2(x, z)
            col = np.array(mat["albedo"]) * (1.0 + 0.08 * np.sin(ang * 14.0 + y * 0.9))
            return tuple(col), False
        if extra:
            return extra(m, p, n, mat, bone)
        return None, False
    return paint


def story_a():
    m = story_base("a")
    # head: one egg volume
    m.add("head", E((0, 21.6, 0.5), (3.55, 3.9, 3.55)), "skin")
    m.add("head", E((0, 20.1, 0.9), (3.1, 2.6, 3.1)), "skin")           # fuller cheeks / jaw
    m.add("head", E((0, 21.1, 3.7), (0.7, 0.95, 0.9)), "skin")           # nose
    m.add("head", E((0, 22.8, 3.3), (2.4, 0.7, 0.9)), "skin")            # brow ridge
    for s in (1, -1):
        m.add("head", E((s * 3.5, 21.2, 0.5), (0.5, 1.0, 0.8)), "skin")  # ears
    # hair: shell around the back/top, hairline window in front
    hair, bb = E((0, 22.3, 0.2), (4.1, 4.3, 4.1))

    def hairshape(X, Y, Z):
        h = hair(X, Y, Z)
        yline = 23.0 - 0.10 * X ** 2
        window = (Z > 1.0) & (Y < yline) & (np.abs(X) < 2.6)
        low = Y < 19.6 + 0.0 * X
        return h & ~window & ~low
    m.fill("hair", hairshape, "hair", bb)
    m.add("hair", E((0, 26.5, -0.5), (1.9, 1.5, 1.9)), "hair")            # bun
    m.add("hair", C((0, 26.2, -0.5), (0.2, 28.3, -0.5), 0.35), "rope")    # pin
    m.paint = story_paint(Face(ey=21.9, eye_kind="closed", brow_col=(236, 238, 244), blush=0.4))
    return m


def story_b():
    m = story_base("b")
    m.add("head", E((0, 21.3, 0.9), (3.4, 3.7, 3.4)), "skin")
    m.add("head", E((0, 19.9, 1.2), (3.0, 2.5, 3.0)), "skin")
    m.add("head", E((0, 20.9, 4.0), (0.7, 0.9, 0.9)), "skin")
    m.add("head", E((0, 22.5, 3.6), (2.3, 0.65, 0.9)), "skin")
    # hood: soft cowl, oval opening in the front, falls onto the shoulders at the back
    hood, bb = E((0, 22.0, -0.2), (4.7, 5.0, 4.7))
    opening = lambda X, Y, Z: (Z > 0.9) & ((X / 3.35) ** 2 + ((Y - 21.2) / 4.0) ** 2 < 1.0)

    def hoodshape(X, Y, Z):
        return hood(X, Y, Z) & ~opening(X, Y, Z) & (Y > 18.8)
    m.fill("hair2", hoodshape, "hood", bb)
    m.add("hair2", E((0, 19.2, -1.6), (4.6, 2.6, 3.4)), "hood")            # drapes over the nape
    m.add("hair2", E((0, 27.2, -1.3), (1.2, 1.1, 1.2)), "hood")            # peaked tip
    # white fringe and long white brows peeking out under the hood
    m.add("hair", E((0, 24.0, 2.6), (2.6, 0.55, 1.0)), "hair")
    m.add("hair", C((-3.1, 20.8, 1.7), (-3.4, 18.4, 1.3), 0.55), "hair")
    m.add("hair", C((3.1, 20.8, 1.7), (3.4, 18.4, 1.3), 0.55), "hair")

    def extra(mm, p, n, mat, bone):
        if mat["name"] == "hood" and p[2] < 1.0:
            # stitched hem line in lighter thread near the opening
            ang = np.arctan2(p[0], p[1] - 21.0)
            return None, False
        return None, False
    m.paint = story_paint(Face(ey=21.4, ex=1.5, eye_kind="open", glint=(170, 235, 240), brow_col=(236, 238, 244), blush=0.3,
                               ez=0.6), extra)
    return m


def story_c():
    m = story_base("c")
    m.add("head", E((0, 21.5, 0.6), (3.5, 3.85, 3.55)), "skin")
    m.add("head", E((0, 20.0, 1.0), (3.05, 2.6, 3.05)), "skin")
    m.add("head", E((0, 21.0, 3.8), (0.65, 0.9, 0.9)), "skin")
    m.add("head", E((0, 22.8, 3.4), (2.3, 0.65, 0.9)), "skin")
    # long silver hair: cap + bangs framing the face + long fall behind the shoulders, side locks in front of the ears
    cap, bb = E((0, 22.2, -0.1), (4.05, 4.35, 4.1))

    def capshape(X, Y, Z):
        yline = 23.2 - 0.12 * X ** 2 + 0.5 * np.abs(X) * 0.0
        window = (Z > 0.9) & (Y < yline) & (np.abs(X) < 2.5)
        part = (np.abs(X) < 0.35) & (Z > 1.0) & (Y > yline - 1.0)   # centre parting is a dark line in the paint
        return cap(X, Y, Z) & ~window & (Y > 17.5)
    m.fill("hair", capshape, "hair", bb)
    m.add("hair2", CONE(0, -2.3, 12.5, 22.0, 3.7, 4.1, 0.55), "hair")      # long fall behind
    for s in (1, -1):
        m.add("hair2", C((s * 3.6, 23.0, 1.6), (s * 3.6, 15.6, 2.2), 1.1, 0.8), "hair")   # side locks
        m.add("hair", E((s * 3.1, 23.0, 2.6), (0.9, 1.3, 1.0)), "hair")
    # glowing star clasp at the throat
    m.add("shawl", E((0, 16.0, 3.9), (0.55, 0.55, 0.3)), "star")

    def extra(mm, p, n, mat, bone):
        if mat["name"] == "star":
            return (150, 245, 252), True
        if mat["name"] == "hair" and bone == "hair":
            # slight silver-blue sheen streaks
            t = 0.5 + 0.5 * np.sin(p[0] * 7.0 + p[1] * 1.3)
            return tuple(blend((222, 226, 236), (186, 200, 226), 0.5 * t)), False
        return None, False
    m.paint = story_paint(Face(ey=21.8, ex=1.5, eye_kind="open", glint=(190, 240, 255), brow_col=(236, 238, 244),
                               blush=0.3, ez=0.8), extra)
    return m


# ------------------------------------------------------------------ Isana
def isana_base(opt):
    m = K.Model(f"whale_chaser_{opt}", lo=(-14, -2, -14), hi=(14, 36, 14))
    m.add_mat("skin", (190, 128, 92), (128, 78, 82), light=(1.0, 0.95, 0.88))
    m.add_mat("coat", (88, 94, 70), (40, 46, 56))
    m.add_mat("coatd", (60, 66, 52), (28, 32, 44))
    m.add_mat("pants", (112, 106, 84), (54, 54, 70))
    m.add_mat("boot", (92, 62, 42), (40, 30, 40))
    m.add_mat("scarf", (170, 66, 50), (90, 30, 40))
    m.add_mat("hat", (98, 66, 42), (44, 30, 38))
    m.add_mat("hatband", (216, 204, 170), (120, 112, 120))
    m.add_mat("pack", (140, 108, 70), (66, 50, 56))
    m.add_mat("hair", (74, 52, 40), (34, 28, 40))
    m.add_mat("glass", (120, 220, 235), (120, 220, 235))
    m.add_mat("metal", (170, 150, 110), (84, 72, 80))
    m.add_mat("glove", (72, 50, 38), (34, 26, 34))
    m.add_bone("root")
    m.add_bone("body", "root", (0, 12, 0), 0.5)
    m.add_bone("head", "body", (0, 21, 0), 0.25)
    m.add_bone("hat", "head", (0, 26, 0), 0.25)
    m.add_bone("hair", "head", (0, 24, 0), 0.25)
    m.add_bone("hood2", "head", (0, 24, 0), 0.5)
    m.add_bone("armL", "body", (5.0, 19, 0), 0.5)
    m.add_bone("armR", "body", (-5.0, 19, 0), 0.5)
    m.add_bone("legL", "body", (1.7, 10, 0), 0.5)
    m.add_bone("legR", "body", (-1.7, 10, 0), 0.5)
    m.add_bone("pack", "body", (0, 12, -4.5), 0.5)
    # coat: long, a little flared, to the knee
    m.add("body", CONE(0, 0.0, 6.0, 19.5, 4.7, 5.1, 0.74), "coat")
    m.add("body", R((0, 16.8, 0.0), (5.2, 3.0, 3.6), 1.9), "coat")           # shoulders
    m.add("body", C((0, 19.5, 0.3), (0, 22.0, 0.5), 1.8), "skin")           # neck
    # scarf roll
    m.add("body", E((0, 19.6, 0.4), (3.0, 1.3, 2.9)), "scarf")
    m.add("body", C((-0.8, 19.2, 3.0), (-1.6, 13.6, 3.9), 0.85, 0.7), "scarf")  # hanging end
    # arms
    for s, bone in ((1, "armL"), (-1, "armR")):
        m.add(bone, C((s * 5.4, 18.4, 0.0), (s * 5.7, 12.4, 0.4), 1.65, 1.5), "coat")
        m.add(bone, C((s * 5.7, 12.4, 0.4), (s * 5.7, 7.0, 1.0), 1.5, 1.3), "coat")
        m.add(bone, E((s * 5.7, 5.9, 1.1), (1.25, 1.35, 1.2)), "glove")
    # legs: pants + boots
    for s, bone in ((1, "legL"), (-1, "legR")):
        m.add(bone, C((s * 1.9, 5.0, 0.3), (s * 1.9, 2.4, 0.4), 1.7), "pants")
        m.add(bone, E((s * 1.9, 1.3, 1.2), (1.8, 1.4, 2.7)), "boot")
    # backpack with rolled map and pot
    m.add("pack", R((0, 15.0, -5.2), (3.0, 3.8, 1.5), 1.0), "pack")
    m.add("pack", C((-3.4, 19.4, -5.2), (3.4, 19.4, -5.2), 1.0), "hatband")
    m.add("pack", C((-2.6, 10.2, -5.8), (2.6, 10.2, -5.8), 0.9), "metal")
    return m


def isana_paint(face, extra=None):
    fp = face_paint(face)

    def paint(m, p, n, mat, bone):
        r, g = fp(m, p, n, mat, bone)
        if r is not None:
            return r, g
        x, y, z = p
        nm = mat["name"]
        if nm in ("coat", "coatd"):
            ang = np.arctan2(x, z)
            col = np.array(mat["albedo"]) * (1.0 + 0.07 * np.sin(ang * 10.0) + 0.03 * np.sin(y * 1.3))
            if bone == "body" and 12.0 < y < 13.4 and z > 0:
                col = np.array(blend(col, (70, 52, 40), 0.9))      # belt
            if bone == "body" and z > 3.2 and abs(x) < 0.5 and y < 19:    # placket
                col = col * 0.8
            for by in (17.0, 14.8):
                if bone == "body" and z > 3.0 and abs(x - 1.1) < 0.35 and abs(y - by) < 0.35:
                    return (200, 168, 80), False
            return tuple(col), False
        if nm == "scarf":
            return tuple(np.array(mat["albedo"]) * (1.0 + 0.1 * np.sin(y * 4.0 + x))), False
        if extra:
            return extra(m, p, n, mat, bone)
        return None, False
    return paint


def isana_head(m, jaw=True):
    m.add("head", E((0, 23.4, 0.3), (3.7, 3.9, 3.6)), "skin")
    m.add("head", R((0, 21.9, 0.7), (3.3, 2.0, 3.2), 1.4), "skin")        # squarer jaw
    m.add("head", E((0, 22.5, 3.9), (0.75, 1.05, 0.9)), "skin")           # nose
    m.add("head", E((0, 24.6, 3.3), (2.6, 0.7, 0.9)), "skin")            # brow ridge
    for s in (1, -1):
        m.add("head", E((s * 3.65, 23.0, 0.4), (0.5, 1.1, 0.8)), "skin")


def isana_a():
    m = isana_base("a")
    isana_head(m)
    # wide-brim hat sitting on the brow
    m.add("hat", E((0, 25.9, 0.0), (4.3, 2.1, 4.4), power=2.6), "hat")                 # crown
    m.add("hat", E((0, 25.4, 0.4), (6.5, 0.6, 6.7), power=3.0), "hat")                 # brim, slightly drooping
    m.add("hat", R((0, 27.3, 0.0), (3.3, 1.4, 3.3), 1.2), "hat")
    m.add("hat", E((0, 25.9, 0.0), (4.4, 0.55, 4.5), power=3.0), "hatband")
    m.add("hat", E((-3.6, 26.9, 2.5), (0.5, 0.5, 1.3)), "hatband")                     # whale-bone charm
    m.paint = isana_paint(Face(ey=23.0, eye_kind="slit", brow_col=(46, 32, 28), stubble=0.9, mouth_y=21.0, blush=0.15, ez=0.4))
    return m


def isana_b():
    m = isana_base("b")
    isana_head(m)
    # short tousled hair + bandana + goggles pushed up
    cap, bb = E((0, 24.1, 0.0), (4.0, 3.8, 4.05))
    m.fill("hair", lambda X, Y, Z: cap(X, Y, Z) & ~((Z > 1.2) & (Y < 25.2 - 0.12 * X ** 2)) & (Y > 22.6), "hair", bb)
    for i, (x, z) in enumerate([(-2, 3.4), (-0.6, 3.7), (1.0, 3.6), (2.4, 3.2)]):
        m.add("hair", E((x, 26.0, z - 0.2), (0.9, 0.7, 0.8)), "hair")
    band, bb2 = E((0, 25.3, 0.0), (4.1, 2.0, 4.15))
    m.fill("hat", lambda X, Y, Z: band(X, Y, Z) & (Y < 26.2) & (Y > 24.2) & ~(E((0, 25.3, 0.0), (3.9, 1.8, 3.95))[0](X, Y, Z)), "scarf", bb2)
    m.add("hat", C((-1.0, 24.8, -3.6), (-2.0, 22.4, -4.6), 0.8, 0.5), "scarf")        # bandana tail
    m.add("hat", C((0.5, 24.8, -3.7), (1.2, 22.0, -4.8), 0.8, 0.5), "scarf")
    # goggles on the forehead
    for s in (1, -1):
        m.add("hat", E((s * 1.75, 25.6, 3.4), (1.45, 1.15, 0.9)), "metal")
        m.add("hat", E((s * 1.75, 25.6, 4.0), (1.0, 0.8, 0.5)), "glass")
    m.add("hat", E((0, 25.6, 3.5), (0.55, 0.35, 0.55)), "metal")
    m.add("hat", E((0, 25.6, -0.2), (4.35, 0.5, 4.3), power=3.0), "metal")           # strap

    def extra(mm, p, n, mat, bone):
        if mat["name"] == "glass":
            return (110, 214, 232), True
        if mat["name"] == "hair":
            t = 0.5 + 0.5 * np.sin(p[0] * 5.0 + p[2] * 3.0)
            return tuple(blend((74, 52, 40), (104, 74, 54), t * 0.6)), False
        return None, False
    m.paint = isana_paint(Face(ey=23.0, eye_kind="open", brow_col=(60, 42, 34), stubble=0.7, mouth_y=21.0, blush=0.2, ez=0.4,
                               glint=(240, 240, 235)), extra)
    return m


def isana_c():
    m = isana_base("c")
    # head a touch smaller inside the hood
    m.add("head", E((0, 23.2, 0.7), (3.45, 3.7, 3.4)), "skin")
    m.add("head", R((0, 21.8, 1.0), (3.0, 1.9, 3.0), 1.3), "skin")
    m.add("head", E((0, 22.4, 4.0), (0.7, 1.0, 0.85)), "skin")
    m.add("head", E((0, 24.4, 3.5), (2.4, 0.7, 0.9)), "skin")
    hood, bb = E((0, 24.0, -0.4), (5.0, 5.3, 5.0))
    m.fill("hood2", lambda X, Y, Z: hood(X, Y, Z) & ~((Z > 0.7) & (((X / 3.1) ** 2 + ((Y - 23.4) / 3.5) ** 2) < 1.0)) & (Y > 20.2), "coatd", bb)
    m.add("hood2", E((0, 21.0, -1.8), (5.2, 2.6, 3.8)), "coatd")                       # cowl on the shoulders
    m.add("hood2", C((0, 28.0, -2.0), (0, 29.6, -3.6), 1.0, 0.4), "coatd")             # peaked, tilting tip
    # scarf pulled up over the chin
    m.add("body", E((0, 20.2, 1.6), (3.1, 1.3, 2.9)), "scarf")

    def extra(mm, p, n, mat, bone):
        return None, False
    m.paint = isana_paint(Face(ey=23.2, ex=1.35, eye_kind="slit", brow_col=(46, 32, 28), stubble=0.9, mouth_y=21.3, blush=0.0, ez=0.7,
                               glint=(255, 214, 120)), extra)
    return m


OPTIONS = {
    "storyteller": {"a": story_a, "b": story_b, "c": story_c},
    "whale_chaser": {"a": isana_a, "b": isana_b, "c": isana_c},
}

VIEWS = [("front", 180, 6), ("3/4", 215, 14), ("side", 270, 6), ("back 3/4", 35, 16)]


def render_option(name, key, model):
    geo, tex, glow = model.build()
    d = OUT / name / key
    K.save(model, d, name, geo, tex, glow)
    faces = voxel.load_geo(geo, tex, glowmask=glow)
    ov = voxel.check_overlaps(geo)
    print(name, key, model.stats, "overlaps", len(ov))
    return geo, faces, model.stats, len(ov)


def head_faces(faces, y0):
    return [f for f in faces if f.origin[1] + min(0, f.ev[1]) + 0 >= y0 - 0.01 or (f.origin + f.eu + f.ev)[1] >= y0 + 0.5 and f.origin[1] >= y0 - 0.01]


def main():
    names = sys.argv[1:] or list(OPTIONS)
    for name in names:
        cols = []
        hy = 17.5 if name == "storyteller" else 20.0
        for key, fn in OPTIONS[name].items():
            geo, faces, st, ov = render_option(name, key, fn())
            hf = [f for f in faces if (f.origin + (f.eu + f.ev) / 2)[1] >= hy]
            imgs = [hp_view.shot(faces, y, p, (380, 480)) for _, y, p in VIEWS]
            imgs.append(hp_view.shot(hf, 200, 8, (380, 380)))
            imgs.append(hp_view.shot(hf, 240, 14, (380, 380)))
            imgs.append(hp_view.shot(hf, 270, 4, (380, 380)))
            cols.append((f"{key.upper()}  ({st['boxes']} boxes / {st['faces']} faces)", imgs, st))
        # compose
        W, H = 380 * len(cols), 40
        rows = [("front", 480), ("3/4", 480), ("side", 480), ("back", 480), ("face front", 380), ("face 3/4", 380), ("face side", 380)]
        sheet = Image.new("RGB", (W, 60 + sum(r[1] for r in rows)), (16, 18, 28))
        from PIL import ImageDraw
        dr = ImageDraw.Draw(sheet)
        for ci, (label, imgs, st) in enumerate(cols):
            dr.text((ci * 380 + 12, 20), label, fill=(150, 225, 235), font=voxel.font(22))
            y = 60
            for ri, im in enumerate(imgs):
                sheet.paste(im, (ci * 380, y))
                y += rows[ri][1]
        (OUT / name).mkdir(parents=True, exist_ok=True)
        sheet.save(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
