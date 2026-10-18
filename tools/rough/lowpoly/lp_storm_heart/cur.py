"""今の箱のモデルの確認: python cur.py out.png"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools/rough"))
import voxel
from PIL import Image
GEO = ROOT / "src/main/resources/assets/skyabyss/geo/entity"
A = ROOT / "src/main/resources/assets/skyabyss"
tiles = []
for ph in (1, 2, 3):
    f = voxel.load_geo(GEO / f"storm_heart_p{ph}.geo.json", A / f"textures/entity/storm_heart_p{ph}.png",
                       A / f"textures/entity/storm_heart_p{ph}_glowmask.png")
    for yaw in (200, 20):
        tiles.append(voxel.render(f, yaw=yaw, pitch=10, size=(560, 560)).convert("RGB"))
W = Image.new("RGB", (560 * 2, 560 * 3))
for i, t in enumerate(tiles):
    W.paste(t, ((i % 2) * 560, (i // 2) * 560))
W.save(sys.argv[1])
