"""Smooth-style rough of one early mob (moss crawler 苔這い, met on the stone whale's back). 3 options.

python tools/rough/smooth_mob.py  -> docs/review/smooth_mob_roughs/moss_crawler/<a|b|c>/ + moss_crawler.png

All three: stepped-sphere / capsule silhouettes on a 0.5 grid (eyes + teeth 0.25), baked gradient texture with soft
ambient occlusion, no overlapping boxes, same bone layout as the installed model (root, s1..s3, skull, jaw, 6 legs).
  A  polished bone    : smooth ivory skull + slim round ribs, small moss tufts            (lightest)
  B  moss cushion     : thick round moss mats over spine / ribs / skull, bone peeking out
  C  watching eye     : big detailed eyeball in the skull (iris rings, glowing), bone spikes along the back
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

OUT = Path("docs/review/smooth_mob_roughs/moss_crawler")
E, C, R = K.Model.ellipsoid, K.Model.capsule, K.Model.rbox
blend, soft = N.blend, N.soft

SEG = [-6.0, -2.0, 2.0]


def base(opt):
    m = K.Model(f"moss_crawler_{opt}", lo=(-12, -2, -14), hi=(12, 20, 14))
    m.add_mat("bone", (232, 220, 186), (128, 112, 140), light=(1.0, 0.97, 0.9))
    m.add_mat("bone2", (206, 190, 150), (104, 92, 112))
    m.add_mat("moss", (112, 168, 70), (30, 72, 60), light=(1.0, 1.0, 0.85))
    m.add_mat("moss2", (150, 190, 82), (50, 96, 62))
    m.add_mat("dark", (40, 34, 38), (20, 18, 24))
    m.add_mat("eye", (236, 246, 244), (130, 150, 170))
    m.add_mat("tooth", (244, 236, 210), (140, 128, 150))
    m.add_mat("spike", (214, 200, 160), (110, 90, 110))
    m.add_bone("root")
    m.add_bone("s1", "root", (0, 8, -6))
    m.add_bone("s2", "s1", (0, 8, -2))
    m.add_bone("s3", "s2", (0, 8, 2))
    m.add_bone("skull", "s3", (0, 8, 4.5))
    m.add_bone("eyes", "skull", (0, 8.6, 6.0), 0.25)
    m.add_bone("jaw", "skull", (0, 5.5, 5.0), 0.5)
    m.add_bone("teeth", "jaw", (0, 5.5, 5.0), 0.25)
    m.add_bone("moss", "s2", (0, 10, 0), 0.5)
    for i, z in enumerate(SEG):
        for side in "lr":
            m.add_bone(f"s{i + 1}{side}", f"s{i + 1}", (3.0 if side == "l" else -3.0, 8, z), 0.5)
    # spine: knobbly vertebrae along z
    for i, z in enumerate(SEG):
        bone = f"s{i + 1}"
        m.add(bone, E((0, 9.4, z), (1.9, 1.6, 1.9)), "bone")
        m.add(bone, E((0, 9.4, z + 2.0), (1.1, 0.9, 1.1)), "bone2")
    m.add("s1", E((0, 9.6, -7.8), (1.4, 1.2, 1.5)), "bone")
    return m


def legs(m, thick=0.95):
    for i, z in enumerate(SEG):
        for side, s in (("l", 1), ("r", -1)):
            b = f"s{i + 1}{side}"
            zz = z + (0.4 if i == 0 else 0.0)
            m.add(b, C((s * 1.7, 8.6, zz), (s * 4.5, 7.3, zz + 0.2), thick, thick * 0.85), "bone")
            m.add(b, E((s * 4.7, 7.2, zz + 0.2), (1.05, 1.0, 1.05)), "bone2")
            m.add(b, C((s * 4.8, 7.0, zz + 0.2), (s * 5.4, 1.2, zz + 0.8), thick * 0.8, thick * 0.55), "bone")
            m.add(b, E((s * 5.5, 0.85, zz + 1.5), (0.8, 0.8, 1.5)), "bone2")


def ribs(m, r=0.62, drop=True):
    for i, z in enumerate(SEG):
        b = f"s{i + 1}"
        for s in (1, -1):
            m.add(b, C((s * 1.3, 10.0, z), (s * 3.6, 9.2, z), r, r), "bone")
            m.add(b, C((s * 3.6, 9.2, z), (s * 4.1, 6.0, z + 0.1), r, r * 0.8), "bone")


def skull(m, scale=1.0):
    m.add("skull", E((0, 8.6, 5.6), (3.6, 3.2, 3.9)), "bone")
    m.add("skull", E((0, 7.2, 7.0), (2.7, 2.0, 2.8)), "bone")             # muzzle
    m.add("skull", E((0, 11.3, 4.4), (2.6, 1.0, 2.6)), "bone2")            # brow dome / crown
    # eye sockets: carved, eyes sit deep inside
    for s in (1, -1):
        m.sub("skull", E((s * 1.9, 8.8, 8.2), (1.4, 1.4, 1.6)))
    m.sub("skull", E((0, 7.0, 9.6), (0.45, 0.8, 0.8)))                      # nose hole
    # jaw
    m.add("jaw", E((0, 5.8, 6.2), (2.6, 1.0, 3.1)), "bone2")


def teeth(m, n=5):
    for k in range(n):
        x = -1.75 + k * (3.5 / (n - 1))
        m.add("teeth", E((x, 7.0, 8.7), (0.28, 0.65, 0.28)), "tooth")
    for k in range(n - 1):
        x = -1.5 + k * (3.0 / (n - 2)) if n > 2 else 0
        m.add("teeth", E((x, 6.4, 8.5), (0.25, 0.5, 0.25)), "tooth")


def eyeballs(m, radius=1.15, depth=7.7):
    for s in (1, -1):
        m.add("eyes", E((s * 1.9, 8.8, depth), (radius, radius, radius)), "eye")


def bone_paint(extra=None, cracks=True):
    def paint(m, p, n, mat, bone):
        x, y, z = p
        nm = mat["name"]
        if nm in ("bone", "bone2", "spike"):
            t = 0.5 + 0.5 * np.sin(x * 2.1 + z * 1.7) * np.sin(y * 2.3 + z * 0.9)
            col = np.array(blend(mat["albedo"], np.array(mat["albedo"]) * 0.88, t * 0.5))
            h = (int(x * 5.1) * 73 + int(y * 4.7) * 31 + int(z * 6.3) * 17) % 23
            if cracks and h == 0:
                col = col * 0.72
            return tuple(col), False
        if nm in ("moss", "moss2"):
            t = 0.5 + 0.5 * np.sin(x * 3.1 + y * 2.3) * np.cos(z * 2.7 - y * 1.9)
            col = blend(mat["albedo"], (150, 196, 84), t * 0.6)
            return col, False
        if extra:
            return extra(m, p, n, mat, bone)
        return None, False
    return paint


def eye_paint(iris=(80, 230, 245), pupil=(8, 20, 28), vein=None, sclera=(232, 244, 244)):
    def paint(m, p, n, mat, bone):
        if mat["name"] != "eye":
            return None, False
        x, y, z = p
        s = 1 if x > 0 else -1
        cx, cy = s * 1.9, 8.8
        if z < 7.0:
            return None, False
        r = np.hypot((x - cx) * 0.95, y - cy)
        # iris disc on the front of the ball
        if r < 0.35:
            return pupil, False
        if r < 0.9:
            ang = np.arctan2(y - cy, x - cx)
            k = 0.5 + 0.5 * np.sin(ang * 7.0)
            return tuple(blend(iris, (230, 255, 255), 0.25 + 0.5 * k * soft(r, 0.9) * 0.0 + 0.3 * (r / 0.9))), True
        if r < 1.15:
            return tuple(np.array(iris) * 0.55), True
        if vein is not None:
            h = (int(x * 9) * 7 + int(y * 11) * 5 + int(z * 3)) % 7
            if h == 0:
                return vein, False
        return sclera, False
    return paint


def opt_a():
    m = base("a")
    legs(m)
    ribs(m)
    skull(m)
    teeth(m)
    eyeballs(m, 1.0)
    # small moss tufts: crown and a few along the spine
    m.add("moss", E((0.3, 12.1, 4.2), (1.6, 0.9, 1.6)), "moss")
    m.add("moss", E((-1.1, 11.9, 3.2), (0.9, 0.7, 0.9)), "moss2")
    for z in (-6.0, -2.0):
        m.add("moss", E((0.2, 10.7, z), (1.2, 0.7, 1.4)), "moss")
    m.add("moss", E((-3.4, 9.0, 5.2), (0.7, 1.0, 1.1)), "moss2")
    m.paint = bone_paint(eye_paint())
    return m


def opt_b():
    m = base("b")
    legs(m, 0.85)
    ribs(m, 0.55)
    skull(m)
    teeth(m, 4)
    eyeballs(m, 1.0)
    # thick moss: mats over the back, draped on the ribs, a cushion on the skull
    for i, z in enumerate(SEG):
        m.add("moss", E((0, 10.5, z), (3.3, 1.7, 1.9)), "moss")
        for s in (1, -1):
            m.add("moss", C((s * 2.6, 10.0, z), (s * 3.9, 7.6, z + 0.2), 1.2, 0.9), "moss2")
    m.add("moss", E((0, 10.3, -7.0), (2.3, 1.5, 2.2)), "moss2")
    m.add("moss", E((0, 12.2, 4.0), (3.0, 1.5, 3.1)), "moss")
    m.add("moss", E((0, 12.9, 3.8), (1.8, 0.9, 1.9)), "moss2")
    m.add("moss", E((-3.3, 9.9, 5.2), (1.1, 1.7, 1.9)), "moss")
    m.add("moss", E((3.4, 10.2, 4.0), (1.0, 1.4, 1.6)), "moss2")
    # fuzzy drips over the back of the legs' tops
    for z in (-6.0, -2.0, 2.0):
        for s in (1, -1):
            m.add(f"s{SEG.index(z) + 1}{'l' if s > 0 else 'r'}", E((s * 2.1, 8.7, z), (1.0, 0.9, 1.1)), "moss")
    m.paint = bone_paint(eye_paint(iris=(120, 235, 200)))
    return m


def opt_c():
    m = base("c")
    legs(m)
    ribs(m)
    skull(m)
    teeth(m, 5)
    # big eyeballs bulging out of the sockets
    for s in (1, -1):
        m.add("eyes", E((s * 2.0, 8.8, 8.0), (1.7, 1.7, 1.6)), "eye")
    # spikes along the back (stepped cones) and two brow horns
    for z in (-7.0, -4.5, -2.0, 0.5, 3.0):
        m.add("moss", C((0, 10.6, z), (0, 13.2, z - 0.4), 0.9, 0.2), "spike")
    for s in (1, -1):
        m.add("skull", C((s * 2.4, 11.8, 5.0), (s * 3.4, 14.4, 4.0), 0.85, 0.2), "spike")
    m.add("moss", E((0, 11.9, 4.4), (1.4, 0.8, 1.4)), "moss")
    m.add("moss", E((0, 10.5, -7.2), (1.3, 0.8, 1.4)), "moss2")

    def spike_extra(mm, p, n, mat, bone):
        if mat["name"] == "spike":
            t = np.clip((p[1] - 10.6) / 3.5, 0, 1)
            return tuple(blend((206, 120, 86), (150, 40, 40), t)), False
        return None, False
    ep = eye_paint(iris=(250, 120, 90), vein=(214, 120, 120), sclera=(236, 222, 214))
    bp = bone_paint(spike_extra)

    def paint(m_, p, n, mat, bone):
        r = ep(m_, p, n, mat, bone)
        if r[0] is not None:
            return r
        return bp(m_, p, n, mat, bone)
    m.paint = paint
    return m


OPTS = {"a": opt_a, "b": opt_b, "c": opt_c}
VIEWS = [("front", 180, 8), ("3/4", 215, 22), ("side", 270, 6), ("top", 200, 60), ("back 3/4", 35, 20)]


def main():
    cols = []
    for key, fn in OPTS.items():
        m = fn()
        geo, tex, glow = m.build()
        K.save(m, OUT / key, "moss_crawler", geo, tex, glow)
        faces = voxel.load_geo(geo, tex, glowmask=glow)
        ov = voxel.check_overlaps(geo)
        print(key, m.stats, "overlaps", len(ov))
        sk = [f for f in faces if (lambda c: c[1] >= 5.8 and c[2] >= 2.5)(f.origin + (f.eu + f.ev) / 2)]
        imgs = [hp_view.shot(faces, y, p, (400, 400)) for _, y, p in VIEWS]
        imgs.append(hp_view.shot(sk, 200, 8, (400, 400)))
        imgs.append(hp_view.shot(sk, 235, 18, (400, 400)))
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
