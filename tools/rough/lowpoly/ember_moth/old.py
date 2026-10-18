"""現行の火の粉蛾 (箱) を描く。雷蛾の compare.py の fit_old を借りる。"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "static_moth"))
from em_common import *  # noqa
import compare as C  # noqa


def old_faces(flap=0.0):
    g = json.loads((ASSETS / "geo/entity/ember_moth.geo.json").read_text(encoding="utf-8"))
    tex = ASSETS / "textures/entity/ember_moth.png"
    gm = ASSETS / "textures/entity/ember_moth_glowmask.png"
    pose = {"wing_l": (0, 0, flap), "wing_r": (0, 0, -flap)} if flap else None
    n = sum(len(b.get("cubes", [])) for b in g["minecraft:geometry"][0]["bones"])
    return voxel.load_geo(g, tex, gm, pose if gm.exists() else pose), n


if __name__ == "__main__":
    f, n = old_faces()
    ims = [C.fit_old(f, y, p, (420, 300), 22, (0, 7.5, 0)) for y, p in ((212, 28), (180, 4), (180, 80))]
    w = Image.new("RGB", (1260, 300))
    for i, im in enumerate(ims):
        w.paste(im, (i * 420, 0))
    w.save(sys.argv[1])
    print(n)
