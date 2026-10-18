"""作業用: python quick.py a  -> scratch に 前/後ろ/顔/膨らみ の 4 枚を出す"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from pf_common import *  # noqa
import importlib

key = sys.argv[1]
mod = importlib.import_module("opt_" + key)
r = build_option(key, mod, getattr(mod, "HINT", 128))
print(key, "tris", r["data"]["triangle_count"], "atlas", r["size"], {k: v["triangle_count"] for k, v in r["data"]["bones"].items()})
S = r["size"]
views = [render_pose(r, 212, 13, (400, 400), (0, 6.8, 0), 20.0),
         render_pose(r, 32, 13, (400, 400), (0, 6.8, 0), 20.0),
         render_pose(r, 195, 6, (400, 400), mod.HEAD_CENTER, mod.HEAD_SCALE),
         render_pose(r, 212, 13, (400, 400), (0, 8.5, 0), 13.5, inflated=True)]
c = Image.new("RGB", (1600, 400), (20, 20, 28))
for i, v in enumerate(views):
    c.paste(v, (i * 400, 0), v)
out = sys.argv[2] if len(sys.argv) > 2 else str(Path(sys.argv[0]).parent / "_q.png")
c.save(out)
at = Image.fromarray(r["atlas"], "RGBA")
b = Image.new("RGBA", at.size, (40, 40, 52, 255)); b.alpha_composite(at)
b.resize((S * 3, S * 3), Image.NEAREST).save(out.replace(".png", "_atlas.png"))
