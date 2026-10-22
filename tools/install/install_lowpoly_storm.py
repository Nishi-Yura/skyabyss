#!/usr/bin/env python3
"""Install the chosen storm-back low-poly models (feather_warden A, static_moth A, thunder_supplicant A, raiju A, storm heart A p1-p3)
and the coral thrall (coral_thrall2 A: body + spear, plus the white-stone statue atlases).
Inputs: docs/review/lowpoly_roughs/<folder>/<option>/. Run from the repository root: python tools/install/install_lowpoly_storm.py"""
import os
import shutil
import subprocess
import sys

R = "docs/review/lowpoly_roughs/"
I = [sys.executable, "tools/install/install_lowpoly.py"]
TEX = "src/main/resources/assets/skyabyss/textures/lowpoly/"


def one(name, folder, stem, gear=None):
    d = R + folder + "/"
    j = [name, "--mesh", d + stem + "_mesh.json", "--atlas", d + stem + "_atlas.png", "--glow", d + stem + "_glowmask.png"]
    for g in gear or []:
        j += ["--gear", d + g[0] + "_mesh.json:" + d + g[1] + "_atlas.png:" + g[2] + ":" + g[3]]
    return j


c = "coral_thrall2/a/coral_thrall2_a"
jobs = [
    one("coral_thrall", "coral_thrall2/a", "coral_thrall2_a",
        gear=[("coral_thrall2_a_spear", "coral_thrall2_a_spear", "coral_spear", "socket_main"),
              # the statue spear: same mesh, the white-stone atlas
              ("coral_thrall2_a_spear", "coral_thrall2_a_spear_statue", "coral_spear_statue", "socket_main")]),
    one("feather_warden", "lp_feather_warden/a", "feather_warden_a",
        gear=[("feather_warden_a_spear", "feather_warden_a_spear", "hooked_glaive", "socket_main")]),
    one("static_moth", "lp_static_moth/a", "lp_static_moth_a"),
    one("thunder_supplicant", "lp_thunder_supplicant/a", "thunder_supplicant_a",
        gear=[("thunder_supplicant_a_staff", "thunder_supplicant_a_staff", "supplicant_staff", "socket_main")]),
    one("raiju", "lp_raiju/a", "raiju_a"),
] + [one("storm_heart_p%d" % k, "lp_storm_heart/a", "p%d" % k) for k in (1, 2, 3)]
for j in jobs:
    print("==", j[0])
    subprocess.check_call(I + j)
shutil.copyfile(R + c + "_statue_atlas.png", TEX + "coral_thrall_statue.png")
print("== coral_thrall statue atlas -> textures/lowpoly/coral_thrall_statue.png")

# static moth charge light: the wing faces of the atlas (UV regions of wing_l / wing_r) brightened toward blue-white, black elsewhere.
# StormMobRenderers.ChargeLayer adds it (RenderType.eyes) scaled by the charge, so the wings brighten as the moth charges.
import json
from PIL import Image, ImageDraw

mesh = json.load(open("src/main/resources/assets/skyabyss/lowpoly/static_moth.json", encoding="utf-8"))
atlas = Image.open(TEX + "static_moth.png").convert("RGBA")
w, h = atlas.size
mask = Image.new("L", (w, h), 0)
d = ImageDraw.Draw(mask)
for bn in ("wing_l", "wing_r"):
    b = mesh["bones"][bn]
    for t in b["triangles"]:
        d.polygon([(b["uvs"][i][0] * w, b["uvs"][i][1] * h) for i in t], fill=255)
out = Image.new("RGBA", (w, h), (0, 0, 0, 255))
src = atlas.load()
mk = mask.load()
px = out.load()
for y in range(h):
    for x in range(w):
        if mk[x, y] and src[x, y][3] > 0:
            r, g, bl, _ = src[x, y]
            px[x, y] = (min(255, int(r * 0.9 + 70)), min(255, int(g * 0.95 + 90)), min(255, int(bl * 1.0 + 120)), 255)
out.save(TEX + "static_moth_charge.png")
print("== static_moth charge light -> textures/lowpoly/static_moth_charge.png")

# storm heart phases 2 and 3: tone the glow (the rough's lit pixels read as blown-out white, and the single lit pixels sprinkled over the stone
# shell looked like blocky dots). In the installed copies only: the stone-shell bones (heart, vessel) lose their glow pixels, and the lit
# colours are pulled from white / near-white toward ice blue so the core reads as a light with a shape.
SOFT = {(255, 255, 255): (186, 220, 250), (216, 238, 255): (172, 212, 248), (142, 192, 255): (120, 174, 240)}
for ph in (2, 3):
    name = "storm_heart_p%d" % ph
    mesh = json.load(open("src/main/resources/assets/skyabyss/lowpoly/%s.json" % name, encoding="utf-8"))
    atlas = Image.open(TEX + name + ".png").convert("RGBA")
    maskf = TEX + name + "_glowmask.png"
    mask = Image.open(maskf).convert("RGBA")
    w, h = atlas.size
    stone = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(stone)
    for bn in ("heart", "vessel"):
        b = mesh["bones"][bn]
        for t in b["triangles"]:
            d.polygon([(b["uvs"][i][0] * w, b["uvs"][i][1] * h) for i in t], fill=255)
    ap, mp, sp = atlas.load(), mask.load(), stone.load()
    cleared = softened = 0
    for y in range(h):
        for x in range(w):
            if mp[x, y][3] == 0:
                continue
            if sp[x, y]:
                mp[x, y] = (0, 0, 0, 0)
                cleared += 1
                continue
            r, g, bl, a = ap[x, y]
            if (r, g, bl) in SOFT:
                ap[x, y] = SOFT[(r, g, bl)] + (a,)
                softened += 1
    atlas.save(TEX + name + ".png")
    mask.save(maskf)
    print("== %s glow toned: %d stone-shell pixels cleared, %d lit pixels softened" % (name, cleared, softened))

# storm heart glow textures: the atlas colours of the lit pixels on black (RenderType.eyes adds them without a face shade, as the cube version did;
# AutoGlowingGeoLayer would shade them grey). StormHeartRenderer.GlowLayer draws textures/lowpoly/storm_heart_pN_glow.png.
for ph in (1, 2, 3):
    name = "storm_heart_p%d" % ph
    atlas = Image.open(TEX + name + ".png").convert("RGBA")
    mask = Image.open(TEX + name + "_glowmask.png").convert("RGBA")
    out = Image.new("RGBA", atlas.size, (0, 0, 0, 255))
    ap, mp, op = atlas.load(), mask.load(), out.load()
    for y in range(atlas.size[1]):
        for x in range(atlas.size[0]):
            if mp[x, y][3] > 0:
                r, g, bl, _ = ap[x, y]
                op[x, y] = (int(r * 0.8), int(g * 0.8), int(bl * 0.8), 255)
    out.save(TEX + name + "_glow.png")
    print("== %s glow texture -> textures/lowpoly/%s_glow.png" % (name, name))
