"""Smooth-style rough, round 2: moss crawler (苔這い) character redesign. 3 options (a/b/c).

python tools/rough/smooth_mob2.py  -> docs/review/smooth_mob_roughs2/moss_crawler/<a|b|c>/ + moss_crawler.png

The user liked the smooth style and the thick round moss cushion (round-1 B) but asked for a new character design.
  A  宿り頭蓋  : a fat round moss ball caged by bone ribs, a big-eyed skull peeking out the front, 4 short legs (dome silhouette)
  B  苔芋虫    : a segmented grub in bone hoops with moss cushions, one big detailed cyclops eye, lure feelers (long low wave)
  C  目玉蟹    : a wide sideways crab: moss-cushion shell, two bone pincer arms, two eyes on stalks, 6 walking legs (wide, claws)
All legs are split upper/lower bones; nothing overlaps; eyes/teeth on a 0.25 grid, the rest 0.5.
"""
import sys

sys.path.insert(0, "tools/rough")
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import hp_view
import smooth_kit as K
import smooth_npc as N
import voxel

OUT = Path("docs/review/smooth_mob_roughs2/moss_crawler")
E, C = K.Model.ellipsoid, K.Model.capsule
blend, soft = N.blend, N.soft


def mats(m):
    m.add_mat("bone", (232, 220, 186), (128, 112, 140), light=(1.0, 0.97, 0.9))
    m.add_mat("bone2", (206, 190, 150), (104, 92, 112))
    m.add_mat("stone", (150, 150, 146), (70, 76, 100))
    m.add_mat("flesh", (206, 192, 170), (112, 96, 118))
    m.add_mat("moss", (112, 168, 70), (30, 72, 60), light=(1.0, 1.0, 0.85))
    m.add_mat("moss2", (150, 190, 82), (50, 96, 62))
    m.add_mat("dark", (40, 34, 38), (20, 18, 24))
    m.add_mat("eye", (236, 246, 244), (130, 150, 170))
    m.add_mat("tooth", (244, 236, 210), (140, 128, 150))
    m.add_mat("lure", (150, 245, 255), (90, 160, 190))


def leg(m, pre, parent, hip, knee, foot, r):
    """upper + lower bone; pivots at hip / knee"""
    m.add_bone(pre + "_u", parent, hip, 0.5)
    m.add_bone(pre + "_l", pre + "_u", knee, 0.5)
    m.add(pre + "_u", C(hip, knee, r, r * 0.9), "bone")
    m.add(pre + "_u", E(knee, (r * 1.15,) * 3), "bone2")
    m.add(pre + "_l", C(knee, foot, r * 0.8, r * 0.55), "bone")
    fx = np.array(foot, float)
    m.add(pre + "_l", E((fx[0], fx[1] + 0.35, fx[2] + 0.6), (r * 0.9, 0.55, r * 1.5)), "bone2")


def teeth_row(m, bone, xs, y, zf, h=0.6):
    for x in xs:
        m.add(bone, E((x, y, zf(x)), (0.28, h, 0.28)), "tooth")


# ------------------------------------------------------------------ paint
def make_paint(eyes=()):
    def eye_color(p, mat):
        d = [(np.linalg.norm(p - e["c"]), e) for e in eyes]
        dist, e = min(d, key=lambda t: t[0])
        v = p - e["c"]
        v = v / max(np.linalg.norm(v), 1e-6)
        f = np.array(e["f"], float)
        f /= np.linalg.norm(f)
        th = float(np.arccos(np.clip(v @ f, -1, 1)))
        up = np.array((0, 1, 0), float)
        ang = np.arctan2(v @ up, v @ np.cross(f, up))
        iris = np.array(e["iris"], float)
        if th < 0.25:
            return e["pupil"], False
        if th < 0.8:
            k = 0.5 + 0.5 * np.sin(ang * 9.0)
            t = (th - 0.25) / 0.55
            col = blend(blend((255, 235, 190) if e.get("warm") else (230, 255, 250), iris, 0.35 + 0.5 * t), tuple(iris * 0.6), 0.35 * k * (1 - t))
            if th < 0.36:
                col = blend(col, e["pupil"], 0.5)
            return col, True
        if th < 0.95:
            return tuple(iris * 0.45), True
        h = (int(p[0] * 9) * 7 + int(p[1] * 11) * 5 + int(p[2] * 7) * 3) % 9
        if h == 0 and th > 1.1:
            return e.get("vein", (214, 130, 130)), False
        return mat["albedo"], False

    def paint(m, p, n, mat, bone):
        x, y, z = p
        nm = mat["name"]
        if nm == "eye":
            return eye_color(p, mat)
        if nm == "lure":
            return (150, 245, 255), True
        if nm in ("bone", "bone2"):
            t = 0.5 + 0.5 * np.sin(x * 2.1 + z * 1.7) * np.sin(y * 2.3 + z * 0.9)
            col = np.array(blend(mat["albedo"], np.array(mat["albedo"]) * 0.88, t * 0.5))
            h = (int(x * 5.1) * 73 + int(y * 4.7) * 31 + int(z * 6.3) * 17) % 23
            if h == 0:
                col = col * 0.72
            return tuple(col), False
        if nm == "stone":
            t = 0.5 + 0.5 * np.sin(x * 1.3 + z * 1.1) * np.sin(y * 1.9 + x * 0.7)
            h = (int(x * 4.1) * 61 + int(y * 3.7) * 29 + int(z * 5.3) * 13) % 17
            col = np.array(blend(mat["albedo"], (120, 124, 126), t * 0.6))
            return tuple(col * (0.8 if h == 0 else 1.0)), False
        if nm == "flesh":
            t = 0.5 + 0.5 * np.sin(z * 2.4 + 0.8) * 0.9
            col = blend(mat["albedo"], (176, 150, 140), t * 0.35)
            h = (int(x * 6.1) * 53 + int(y * 5.3) * 37 + int(z * 4.7) * 11) % 19
            if h == 0:
                col = tuple(np.array(col) * 0.86)
            return col, False
        if nm in ("moss", "moss2"):
            t = 0.5 + 0.5 * np.sin(x * 3.1 + y * 2.3) * np.cos(z * 2.7 - y * 1.9)
            col = blend(mat["albedo"], (150, 196, 84), t * 0.6)
            h = (int(x * 5.7) * 41 + int(y * 6.1) * 19 + int(z * 5.1) * 7) % 21
            if h == 0:
                col = blend(col, (214, 232, 120), 0.7)
            return col, False
        return None, False
    return paint


# ------------------------------------------------------------------ A: hermit skull
def opt_a():
    m = K.Model("moss_crawler_d2a", lo=(-16, -2, -18), hi=(16, 18, 18))
    mats(m)
    m.add_bone("root")
    m.add_bone("body", "root", (0, 6, -1.5))
    m.add_bone("skull", "body", (0, 4.5, 5.0))
    m.add_bone("eyes", "skull", (0, 5, 8), 0.25)
    m.add_bone("jaw", "skull", (0, 2.6, 6.5))
    m.add_bone("teeth", "jaw", (0, 2.6, 6.5), 0.25)
    for pre, sx, z in (("fl", 1, 3.0), ("fr", -1, 3.0), ("bl", 1, -5.0), ("br", -1, -5.0)):
        leg(m, pre, "body", (sx * 5.0, 4.2, z), (sx * 7.4, 3.2, z), (sx * 8.0, 0.2, z + 0.2), 1.0)
    zc = -1.5
    m.add("body", E((0, 7.0, zc), (7.0, 5.2, 6.5)), "moss")
    m.add("body", E((0, 11.0, zc - 0.5), (4.4, 1.9, 4.2)), "moss2")
    m.add("body", E((0, 6.2, zc - 5.2), (4.2, 3.0, 2.4)), "moss2")
    # rib cage hoops bulging out of the moss
    for dz in (-3.0, 0.0, 3.0):
        s = np.sqrt(1 - (dz / 6.5) ** 2)
        rx, ry = 7.4 * s, 5.5 * s
        pts = [(rx * np.cos(t), 7.0 + ry * np.sin(t), zc + dz) for t in np.radians(np.linspace(5, 175, 9))]
        for a, b in zip(pts[:-1], pts[1:]):
            m.add("body", C(a, b, 0.62, 0.62), "bone")
    for z in (-4.0, -0.5, 3.0):
        m.add("body", C((0, 11.2, z), (0, 13.7, z), 0.85, 0.25), "bone")
    # skull
    m.add("skull", E((0, 4.6, 6.3), (3.5, 3.0, 3.4)), "bone")
    m.add("skull", E((0, 3.6, 8.4), (2.2, 1.5, 1.6)), "bone")
    m.add("skull", E((0, 6.7, 7.9), (2.9, 0.75, 1.5)), "bone2")
    m.add("skull", E((0, 7.9, 6.0), (2.7, 1.2, 2.5)), "moss")
    m.add("skull", E((-1.6, 8.4, 5.2), (1.2, 0.9, 1.2)), "moss2")
    for s in (1, -1):
        m.sub("skull", E((s * 1.55, 5.2, 9.2), (1.35, 1.35, 1.2)))
        m.add("eyes", E((s * 1.55, 5.2, 8.3), (1.2, 1.2, 1.2)), "eye")
    m.sub("skull", E((0, 3.9, 9.9), (0.4, 0.7, 0.6)))
    m.add("jaw", E((0, 2.1, 7.2), (2.2, 0.85, 2.6)), "bone2")
    teeth_row(m, "teeth", [-1.5, -0.75, 0, 0.75, 1.5], 2.75, lambda x: 9.35 - abs(x) * 0.5, 0.55)
    ey = [dict(c=np.array((s * 1.55, 5.2, 8.3)), f=(0, 0, 1), iris=(110, 235, 215), pupil=(8, 24, 28)) for s in (1, -1)]
    m.paint = make_paint(ey)
    return m


# ------------------------------------------------------------------ B: grub
def opt_b():
    m = K.Model("moss_crawler_e2b", lo=(-16, -2, -20), hi=(16, 18, 20))
    mats(m)
    zs = [-10.5, -7.0, -3.4, 0.2, 3.8]
    rx = [2.3, 3.0, 3.5, 3.5, 3.1]
    m.add_bone("root")
    prev = "root"
    for i, z in enumerate(zs):
        m.add_bone(f"seg{i + 1}", prev, (0, 4, z))
        prev = f"seg{i + 1}"
    m.add_bone("head", "seg5", (0, 5, 8))
    m.add_bone("eyes", "head", (0, 5.6, 12), 0.25)
    m.add_bone("jaw", "head", (0, 2.4, 9))
    m.add_bone("teeth", "jaw", (0, 2.4, 9), 0.25)
    m.add_bone("ant_l", "head", (1.6, 8, 9))
    m.add_bone("ant_r", "head", (-1.6, 8, 9))
    m.add_bone("lure_l", "ant_l", (2.4, 12.2, 10.4), 0.5)
    m.add_bone("lure_r", "ant_r", (-2.4, 12.2, 10.4), 0.5)
    m.add_bone("tail", "seg1", (0, 4, -12))
    for i, z in enumerate(zs):
        r = rx[i]
        m.add(f"seg{i + 1}", E((0, 4.0, z), (r, r * 0.95, 2.4)), "flesh")
    for i, z in enumerate(zs):
        r = rx[i]
        b = f"seg{i + 1}"
        if i >= 1:
            pts = [(r * 1.06 * np.cos(t), 4.0 + r * 1.0 * np.sin(t), z) for t in np.radians(np.linspace(-30, 210, 7))]
            for a, c in zip(pts[:-1], pts[1:]):
                m.add(b, C(a, c, 0.6, 0.6), "bone")
        m.add(b, E((0, 4.0 + r * 0.72, z), (r * 0.95, 1.9, 2.1)), "moss" if i % 2 == 0 else "moss2")
    m.add("tail", C((0, 3.8, -12.0), (0, 6.4, -15.2), 0.85, 0.25), "bone")
    # head: bone mask with one big eye
    m.add("head", E((0, 5.2, 9.4), (3.9, 3.6, 3.7)), "bone")
    m.add("head", E((0, 5.6, 11.6), (3.1, 3.1, 1.5)), "bone2")
    m.add("head", E((0, 8.5, 8.6), (2.6, 1.0, 2.4)), "moss")
    m.add("eyes", E((0, 5.6, 12.4), (1.9, 1.9, 1.9)), "eye")
    m.add("jaw", E((0, 2.1, 10.6), (2.9, 1.0, 2.7)), "bone2")
    teeth_row(m, "teeth", [-2.0, -1.0, 0, 1.0, 2.0], 3.0, lambda x: 10.4 + 2.9 * np.sqrt(max(0, 1 - (x / 3.1) ** 2)) * 0.9, 0.55)
    for sx, ant, lure in ((1, "ant_l", "lure_l"), (-1, "ant_r", "lure_r")):
        m.add(ant, C((sx * 1.6, 8.3, 9.0), (sx * 2.4, 11.6, 10.4), 0.6, 0.4), "bone")
        m.add(lure, E((sx * 2.4, 12.2, 10.4), (0.65, 0.65, 0.65)), "lure")
    for k, z in enumerate((-5.2, -1.6, 2.0)):
        seg = ["seg2", "seg3", "seg5"][k]
        for sx, s in (("l", 1), ("r", -1)):
            leg(m, f"{sx}{k + 1}", seg, (s * 3.0, 3.0, z), (s * 4.6, 1.9, z), (s * 5.2, 0.2, z + 0.1), 0.95)
    ey = [dict(c=np.array((0, 5.6, 12.4)), f=(0, 0, 1), iris=(250, 150, 80), pupil=(10, 8, 14), warm=True)]
    m.paint = make_paint(ey)
    return m


# ------------------------------------------------------------------ C: crab
def opt_c():
    m = K.Model("moss_crawler_f2c", lo=(-16, -2, -12), hi=(16, 16, 20))
    mats(m)
    m.add_bone("root")
    m.add_bone("body", "root", (0, 5, 0))
    m.add_bone("face", "body", (0, 5, 5))
    m.add_bone("jaw", "face", (0, 3.6, 6))
    m.add_bone("teeth", "jaw", (0, 3.6, 6), 0.25)
    for sx, s in (("l", 1), ("r", -1)):
        m.add_bone(f"stalk_{sx}", "body", (s * 1.9, 7, 4.6))
        m.add_bone(f"eye_{sx}", f"stalk_{sx}", (s * 2.8, 11, 7), 0.25)
    m.add("body", E((0, 5.2, 0), (7.6, 3.0, 5.6)), "stone")
    m.add("body", E((0, 7.5, -0.7), (6.5, 2.7, 4.7)), "moss")
    m.add("body", E((0, 9.5, -1.0), (3.8, 1.5, 2.8)), "moss2")
    for s in (1, -1):
        m.add("body", E((s * 6.6, 5.9, -1.2), (1.3, 1.4, 2.8)), "moss2")
        m.add("body", C((s * 5.4, 8.0, -4.6), (s * 3.0, 9.0, -5.4), 0.9, 0.2), "bone")
    m.add("body", E((0, 6.0, -5.6), (3.4, 1.8, 1.6)), "moss2")
    m.add("face", E((0, 4.6, 5.2), (3.0, 2.0, 1.7)), "bone")
    m.add("face", E((0, 6.1, 5.2), (2.2, 0.8, 1.2)), "bone2")
    m.sub("face", E((0, 4.9, 6.7), (0.4, 0.6, 0.5)))
    m.add("jaw", E((0, 3.1, 5.8), (2.2, 0.8, 1.4)), "bone2")
    teeth_row(m, "teeth", [-1.5, -0.75, 0, 0.75, 1.5], 3.75, lambda x: 7.0 - abs(x) * 0.25, 0.5)
    eyes = []
    for sx, s in (("l", 1), ("r", -1)):
        m.add(f"stalk_{sx}", C((s * 1.9, 7.0, 4.6), (s * 2.7, 10.0, 6.6), 0.7, 0.6), "bone")
        c = (s * 2.8, 11.0, 7.1)
        m.add(f"eye_{sx}", E(c, (1.4, 1.4, 1.4)), "eye")
        eyes.append(dict(c=np.array(c), f=(s * 0.25, 0.1, 1), iris=(255, 120, 90), pupil=(10, 8, 14), vein=(214, 120, 120)))
        # pincer arm: upper, fore + palm, movable finger
        m.add_bone(f"arm_{sx}_u", "body", (s * 5.8, 5, 4))
        m.add_bone(f"arm_{sx}_l", f"arm_{sx}_u", (s * 8.0, 4.4, 7.4))
        m.add_bone(f"claw_{sx}", f"arm_{sx}_l", (s * 6.5, 3.4, 11.5))
        m.add(f"arm_{sx}_u", C((s * 5.6, 5.0, 3.6), (s * 8.0, 4.4, 7.4), 1.1, 1.0), "bone")
        m.add(f"arm_{sx}_u", E((s * 8.0, 4.4, 7.4), (1.3, 1.3, 1.3)), "bone2")
        m.add(f"arm_{sx}_l", C((s * 8.0, 4.4, 7.4), (s * 6.8, 3.9, 10.4), 0.95, 1.1), "bone")
        m.add(f"arm_{sx}_l", E((s * 6.5, 4.0, 11.2), (1.7, 1.5, 1.9)), "bone2")
        m.add(f"arm_{sx}_l", C((s * 6.6, 4.8, 12.2), (s * 5.4, 4.8, 15.0), 0.85, 0.2), "bone")
        m.add(f"claw_{sx}", C((s * 6.6, 3.2, 12.2), (s * 5.6, 3.1, 14.6), 0.75, 0.2), "bone")
        m.add(f"claw_{sx}", E((s * 6.5, 3.3, 12.0), (1.0, 0.8, 1.0)), "bone2")
    for sx, s in (("l", 1), ("r", -1)):
        for k, z in enumerate((1.6, -2.8)):
            leg(m, f"w{sx}{k + 1}", "body", (s * 6.8, 4.6, z), (s * 10.6, 6.0, z + 0.2), (s * 12.6, 0.3, z + 0.4), 0.85)
    m.paint = make_paint(eyes)
    return m


OPTS = {"a": opt_a, "b": opt_b, "c": opt_c}
HEAD = {"a": lambda c: c[2] <= -3.0 and c[1] <= 9.5, "b": lambda c: c[2] <= -6.5, "c": lambda c: c[2] <= -3.0 and abs(c[0]) <= 5 and c[1] >= 2}
VIEWS = [("front", 180, 8), ("3/4", 215, 22), ("side", 270, 6), ("back 3/4", 35, 20), ("top", 200, 60)]


def main():
    cols = []
    for key, fn in OPTS.items():
        m = fn()
        geo, tex, glow = m.build()
        K.save(m, OUT / key, "moss_crawler", geo, tex, glow)
        faces = voxel.load_geo(geo, tex, glowmask=glow)
        ov = voxel.check_overlaps(geo)
        print(key, m.stats, "overlaps", len(ov), "bones", len(m.bones))
        hd = [f for f in faces if HEAD[key](f.origin + (f.eu + f.ev) / 2)]
        imgs = [hp_view.shot(faces, y, p, (400, 400)) for _, y, p in VIEWS]
        imgs.append(hp_view.shot(hd, 200, 8, (400, 400)))
        imgs.append(hp_view.shot(hd, 235, 18, (400, 400)))
        cols.append((f"{key.upper()}  ({m.stats['boxes']} boxes / {m.stats['faces']} faces)", imgs))
    n = len(cols[0][1])
    sheet = Image.new("RGB", (400 * len(cols), 60 + 400 * n), (16, 18, 28))
    dr = ImageDraw.Draw(sheet)
    for ci, (label, imgs) in enumerate(cols):
        dr.text((ci * 400 + 12, 20), label, fill=(150, 225, 235), font=voxel.font(22))
        for ri, im in enumerate(imgs):
            sheet.paste(im, (ci * 400, 60 + ri * 400))
    sheet.save(OUT.parent / "moss_crawler.png")


if __name__ == "__main__":
    main()
