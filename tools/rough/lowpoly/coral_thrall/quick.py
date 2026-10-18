"""1 案の素早い確認: python quick.py a out.png"""
import importlib
import sys

from common import *  # noqa
from PIL import Image

key, outp = sys.argv[1], sys.argv[2]
mod = importlib.import_module(f"opt_{key}")
r = build_option(key, mod.build, mod.PAL, getattr(mod, "SPEAR_PAL", None), body_hint=getattr(mod, "HINT", 128),
                 statue_fn=getattr(mod, "statue_coral", None))
print(key, "body", r["data"]["triangle_count_normal"], "statue", r["data"]["triangle_count_statue_only"], "spear", r["sdata"]["triangle_count"],
      r["size"], r["ssize"])
big, gl, yoff = combined_atlas(r)
W, H = 400, 620
ims = []
hc = getattr(mod, "HEAD_CENTER", (0, 30, -1))
for yaw, pitch, pose, ctr, sc, pos in ((212, 13, {}, (0, 18.5, -0.5), 14.0, None), (32, 13, {}, (0, 18.5, -0.5), 14.0, None),
                                       (160, 10, THRUST, (0, 18.5, -3), 12.5, POS),
                                       (200, 8, {}, hc, getattr(mod, "HEAD_SCALE", 22), None)):
    bg = Image.new("RGBA", (W, H), (28, 28, 38, 255))
    im = render_pose(r, big, gl, yoff, pose, yaw, pitch, (W, H), ctr, sc, pos=pos)
    bg.alpha_composite(im)
    ims.append(bg)
c = Image.new("RGB", (W * 4, H))
for i, im in enumerate(ims):
    c.paste(im.convert("RGB"), (i * W, 0))
c.save(outp)
a = Image.fromarray(r["atlas"], "RGBA")
bgc = Image.new("RGBA", a.size, (40, 40, 52, 255))
bgc.alpha_composite(a)
bgc.convert("RGB").resize((a.width * 3, a.height * 3), Image.NEAREST).save(outp.replace(".png", "_atlas.png"))
