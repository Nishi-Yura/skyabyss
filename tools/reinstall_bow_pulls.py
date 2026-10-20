"""fix_bow_arrow.py で直した引き絵のメッシュを、水密にして入れ直す (雷翼の弓の全階級 + 深淵の雷弓 案 b)。使い方: python tools/reinstall_bow_pulls.py"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import install_lowpoly_weapons as w  # noqa: E402
import mesh_close  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
w.install_meshes()
for st in ("legend_pull1", "legend_pull2"):
    mesh = json.loads((ROOT / "docs/review/lowpoly_roughs/abyss_weapons/abyss_storm_bow/b" / f"{st}_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/abyss_storm_bow/{st}_atlas"
    mesh.pop("glowmask", None)
    mesh, _ = mesh_close.close_mesh(mesh)
    (ROOT / "src/main/resources/assets/skyabyss/lowpoly/items/abyss_storm_bow" / f"{st}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
