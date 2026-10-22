#!/usr/bin/env python3
"""Install the chosen ember-back low-poly models, the ember heart, and the NPCs / the watcher:
ash_thrall A (shell + flared atlases, poker), ember_moth A, cinder_roller B, ash_smith A (hammer + tongs), ember_heart C p1-p3,
storyteller A, whale_chaser (Isana) A, watcher A.
Inputs: docs/review/lowpoly_roughs/<folder>/<option>/. Run from the repository root: python tools/install/install_lowpoly_ember_npc.py
The original cube geos, textures and animations are never touched (LowPoly.enabled = false brings the cubes back)."""
import os
import shutil
import subprocess
import sys

from PIL import Image

R = "docs/review/lowpoly_roughs/"
I = [sys.executable, "tools/install/install_lowpoly.py"]
TEX = "src/main/resources/assets/skyabyss/textures/lowpoly/"


def one(name, folder, stem, gear=None, gear_dir=None):
    d = R + folder + "/"
    mesh_suffix = "_mesh.json"
    atlas_suffix = "_atlas.png"
    glow_suffix = "_glowmask.png"
    if stem.endswith("_lowpoly"):
        mesh_suffix, atlas_suffix, glow_suffix = "_mesh.json", "_atlas.png", "_glowmask.png"
    j = [name, "--mesh", d + stem + mesh_suffix, "--atlas", d + stem + atlas_suffix, "--glow", d + stem + glow_suffix]
    for g in gear or []:
        j += ["--gear", d + g[0] + "_mesh.json:" + d + g[1] + "_atlas.png:" + g[2] + ":" + g[3]]
    return j


jobs = [
    one("ash_thrall", "lp_ash_thrall/a", "ash_thrall_a",
        gear=[("ash_thrall_a_poker", "ash_thrall_a_poker", "ash_poker", "socket_main")]),
    one("ember_moth", "lp_ember_moth/a", "lp_ember_moth_a"),
    one("cinder_roller", "lp_cinder_roller/b", "cinder_roller_lowpoly"),
    one("ash_smith", "lp_ash_smith/a", "ash_smith_a_lowpoly",
        gear=[("smith_hammer_a", "smith_hammer_a", "smith_hammer", "socket_main"),
              ("smith_tongs_a", "smith_tongs_a", "smith_tongs", "socket_off")]),
    one("storyteller", "lp_storyteller/a", "storyteller_a_lowpoly"),
    one("whale_chaser", "lp_isana/a", "whale_chaser_a_lowpoly"),
    one("watcher", "lp_watcher/a", "watcher_a"),
] + [one("ember_heart_p%d" % k, "lp_ember_heart/c", "p%d" % k) for k in (1, 2, 3)]
for j in jobs:
    print("==", j[0])
    subprocess.check_call(I + j)

# ash thrall, flared state: the same mesh and UVs, its own atlas and glow mask (EmberMobRenderers.ThrallModel swaps the texture,
# AutoGlowingGeoLayer finds ash_thrall_flared_glowmask.png next to it)
d = R + "lp_ash_thrall/a/"
shutil.copyfile(d + "ash_thrall_a_atlas_flared.png", TEX + "ash_thrall_flared.png")
shutil.copyfile(d + "ash_thrall_a_glowmask_flared.png", TEX + "ash_thrall_flared_glowmask.png")
print("== ash_thrall flared atlas + glowmask -> textures/lowpoly/ash_thrall_flared*.png")


def lit_pixels(path):
    return sum(1 for px in Image.open(path).convert("RGBA").getdata() if px[3] > 0)


# Glow textures for the renderers that add their glow as light (RenderType.eyes: no face shade, added), as the cube versions did.
# AutoGlowingGeoLayer shades the lit pixels (the colour sinks to grey in a dim room), so the hearts and the smith use this instead:
# the atlas colours of the lit pixels, scaled by GAIN, on black. (Tone: a hot core must read as a light with a shape, not a white blob.)
GAIN = {"ember_heart_p1": 0.8, "ember_heart_p2": 0.7, "ember_heart_p3": 0.8, "ash_smith": 0.8}


def bake_glow(name, white=False):
    atlas = Image.open(TEX + name + ".png").convert("RGBA")
    mask = Image.open(TEX + name + "_glowmask.png").convert("RGBA")
    out = Image.new("RGBA", atlas.size, (0, 0, 0, 255))
    ap, mp, op = atlas.load(), mask.load(), out.load()
    g = GAIN[name]
    lit = 0
    for y in range(atlas.size[1]):
        for x in range(atlas.size[0]):
            if mp[x, y][3] > 0:
                r, gr, b, _ = ap[x, y]
                c = (r * g, gr * g, b * g)
                if white:
                    c = tuple(v * 0.25 + w * 0.75 * g for v, w in zip(c, (255, 248, 232)))
                op[x, y] = tuple(min(255, int(v)) for v in c) + (255,)
                lit += 1
    suffix = "_glow_white.png" if white else "_glow.png"
    out.save(TEX + name + suffix)
    print("== %s%s: %d lit pixels baked (gain %.2f)" % (name, suffix, lit, g))


for k in (1, 2, 3):
    bake_glow("ember_heart_p%d" % k)
bake_glow("ash_smith")
bake_glow("ash_smith", white=True)

# watcher: its eyes are drawn as an additive glow (WatcherRenderer.EyeLayer): atlas colours of the lit pixels on black
w_atlas = Image.open(TEX + "watcher.png").convert("RGBA")
w_mask = Image.open(TEX + "watcher_glowmask.png").convert("RGBA")
w_out = Image.new("RGBA", w_atlas.size, (0, 0, 0, 255))
wa, wm, wo = w_atlas.load(), w_mask.load(), w_out.load()
for y in range(w_atlas.size[1]):
    for x in range(w_atlas.size[0]):
        if wm[x, y][3] > 0:
            wo[x, y] = wa[x, y][:3] + (255,)
w_out.save(TEX + "watcher_glow.png")
print("== watcher eyes glow -> textures/lowpoly/watcher_glow.png (%d lit)" % lit_pixels(TEX + "watcher_glowmask.png"))
