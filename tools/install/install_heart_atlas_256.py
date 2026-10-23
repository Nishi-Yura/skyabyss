#!/usr/bin/env python3
"""Repaint the tide heart p2/p3 and storm heart p2 low-poly atlases at 256 (they packed into 128; p1 and the other hearts are 256).

    python tools/install/install_heart_atlas_256.py

The rough generators (tools/rough/lowpoly/lp_tide_heart, lp_storm_heart: build_phase) now paint every phase at >= 256
(common.MIN_ATLAS): the 128 chart layout is kept and doubled (lpkit.upscale_atlas) and the paint is evaluated again from the
3D positions, with the finer-atlas details (shell growth lines, nacre rings, stone hairlines, barb lines, unlit halos around
the glowing cracks; weaker dither). Normalized UVs do not change, so the INSTALLED meshes (already closed / joint-sunk by
mesh_close and later passes) are kept as they are: only their texture_size / texels_per_unit fields are updated, and the
textures are replaced. The rough outputs in docs/review/lowpoly_roughs/ are rewritten too, so install_lowpoly_tide.py /
install_lowpoly_storm.py install the same pictures.

Storm heart: the same post-steps as install_lowpoly_storm.py (stone-shell bones lose their glow pixels, near-white lit colours
are softened to ice blue, textures/lowpoly/storm_heart_pN_glow.png is baked from the lit pixels).
"""
import importlib
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
TEX = os.path.join(ASSETS, "textures", "lowpoly")
ROUGH = os.path.join(ROOT, "docs", "review", "lowpoly_roughs")
JOBS = [("tide", "b", (2, 3)), ("storm", "a", (2,))]
SOFT = {(255, 255, 255): (186, 220, 250), (216, 238, 255): (172, 212, 248), (142, 192, 255): (120, 174, 240)}


def load_generator(which, opt):
    d = os.path.join(ROOT, "tools", "rough", "lowpoly", "lp_%s_heart" % which)
    for m in ("common", "parts", "opt_a", "opt_b", "opt_c"):
        sys.modules.pop(m, None)
    sys.path.insert(0, d)
    try:
        common = importlib.import_module("common")
        o = importlib.import_module("opt_" + opt).OPT
    finally:
        sys.path.remove(d)
    return common, o


def check_uvs(installed, rough):
    """Every rough vertex UV must still be in the installed mesh (same bone): the new atlas then lands on the same faces.
    Tolerance 2e-4: the 0.02-texel inset is now 0.02 of a 256 texel instead of a 128 one (8e-5 in normalized UV)."""
    bad = 0
    for bn, b in rough["bones"].items():
        a = np.array(b["uvs"], float)
        c = np.array(installed["bones"][bn]["uvs"], float)
        if not len(a):
            continue
        d = np.abs(a[:, None, :] - c[None]).max(-1).min(1)
        bad += int((d > 2e-4).sum())
    return bad


def storm_post(name, mesh):
    atlas = Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")
    maskf = os.path.join(TEX, name + "_glowmask.png")
    mask = Image.open(maskf).convert("RGBA")
    w, h = atlas.size
    stone = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(stone)
    for bn in ("heart", "vessel"):
        b = mesh["bones"][bn]
        for t in b["triangles"]:
            d.polygon([(b["uvs"][i][0] * w, b["uvs"][i][1] * h) for i in t], fill=255)
    a, m, s = np.array(atlas), np.array(mask), np.array(stone) > 0
    lit = m[..., 3] > 0
    m[lit & s] = 0
    lit &= ~s
    for src, dst in SOFT.items():
        hit = lit & (a[..., :3] == src).all(-1)
        a[hit, :3] = dst
    Image.fromarray(a).save(os.path.join(TEX, name + ".png"))
    Image.fromarray(m).save(maskf)
    g = np.zeros_like(a)
    g[..., 3] = 255
    g[lit, :3] = (a[lit, :3].astype(int) * 4 // 5).astype(np.uint8)
    Image.fromarray(g).save(os.path.join(TEX, name + "_glow.png"))
    print("   storm post: %d lit pixels after clearing the stone shell" % int(lit.sum()))


def main():
    for which, opt, phases in JOBS:
        common, o = load_generator(which, opt)
        for ph in phases:
            name = "%s_heart_p%d" % (which, ph)
            out = os.path.join(ROUGH, "lp_%s_heart" % which, opt)
            r = common.build_phase(o, ph, out, save=True)
            mpath = os.path.join(ASSETS, "lowpoly", name + ".json")
            raw = open(mpath, encoding="utf-8").read()
            installed = json.loads(raw)
            bad = check_uvs(installed, r["data"])
            if bad:
                raise SystemExit("%s: %d rough UVs are missing from the installed mesh (layout changed?)" % (name, bad))
            old = installed["texture_size"][0]
            tpu = r["data"]["texels_per_unit"]
            for key, val in (("texture_size", [r["size"], r["size"]]), ("texels_per_unit", tpu)):
                a = '"%s":%s' % (key, json.dumps(installed[key], separators=(",", ":")))
                assert raw.count(a) == 1, (name, a)
                raw = raw.replace(a, '"%s":%s' % (key, json.dumps(val, separators=(",", ":"))))
            with open(mpath, "w", encoding="utf-8") as f:
                f.write(raw)
            Image.open(os.path.join(out, "p%d_atlas.png" % ph)).save(os.path.join(TEX, name + ".png"))
            Image.open(os.path.join(out, "p%d_glowmask.png" % ph)).save(os.path.join(TEX, name + "_glowmask.png"))
            print("== %s: %d -> %d, %d lit pixels, texels_per_unit %s" % (name, old, r["size"], int(r["glow"].sum()), tpu))
            if which == "storm":
                storm_post(name, installed)


if __name__ == "__main__":
    main()
