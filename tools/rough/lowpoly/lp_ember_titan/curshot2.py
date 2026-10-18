"""今の geo の部分アップ: python curshot2.py out_prefix"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lp_stone_whale"))
from common import *  # noqa
import build_all as B

faces = voxel.load_geo(GEO / "ember_titan.geo.json", ASSETS / "textures/entity/ember_titan.png", ASSETS / "textures/entity/ember_titan_glowmask.png")
B.cache["cur"] = faces
P = sys.argv[1]
for nm, (y, p, c, s, sz) in {"htop": (90, 90, (0, 30, -165), 6, (700, 500)), "hside": (90, 0, (0, 30, -165), 6, (700, 400)),
                             "hfront": (180, 10, (0, 30, -165), 6, (600, 400)),
                             "volc": (90, 0, (0, 90, -50), 5, (700, 500)), "voltop": (90, 90, (0, 90, -50), 5, (700, 500)),
                             "leg": (90, 60, (80, 20, -90), 6, (500, 400)), "tailtop": (90, 90, (0, 20, 170), 5, (700, 400))}.items():
    B.cur_shot(y, p, sz, c, s).save(f"{P}_{nm}.png")
