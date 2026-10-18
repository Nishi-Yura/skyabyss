"""今の箱の geo を撮る (確認用): python tools/rough/lowpoly/lp_ember_titan/curshot.py out_prefix"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lp_stone_whale"))
from common import *  # noqa
import build_all as B

faces = voxel.load_geo(GEO / "ember_titan.geo.json", ASSETS / "textures/entity/ember_titan.png", ASSETS / "textures/entity/ember_titan_glowmask.png")
B.cache["cur"] = faces
P = sys.argv[1]
for nm, (y, p, c, s, sz) in {"side": (90, 5, (0, 70, 20), 1.6, (900, 400)), "q": (140, 25, (0, 70, 20), 1.6, (900, 500)),
                             "face": (150, 8, (0, 20, -170), 6, (600, 400)), "top": (90, 90, (0, 70, 20), 1.6, (900, 400))}.items():
    B.cur_shot(y, p, sz, c, s).save(f"{P}_{nm}.png")
