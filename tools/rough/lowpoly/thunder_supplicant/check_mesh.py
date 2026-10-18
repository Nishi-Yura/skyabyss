"""書き出したメッシュ JSON を geo と突き合わせる (開発用)。python check_mesh.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
root = ROOT / "docs/review/lowpoly_roughs/lp_thunder_supplicant"
geo = json.load(open(ROOT / "src/main/resources/assets/skyabyss/geo/entity/thunder_supplicant.geo.json"))["minecraft:geometry"][0]["bones"]
gb = {b["name"]: (b.get("parent"), b["pivot"], b.get("rotation")) for b in geo}
for k in "abc":
    m = json.load(open(root / k / f"thunder_supplicant_{k}_mesh.json"))
    assert set(m["bones"]) == set(gb)
    tot = 0
    for n, b in m["bones"].items():
        assert b["parent"] == gb[n][0] and b["pivot"] == gb[n][1], n
        if gb[n][2]:
            assert b.get("rotation") == gb[n][2], n
        nv = len(b["vertices"])
        assert all(0 <= i < nv for t in b["triangles"] for i in t)
        assert all(-0.001 <= u <= 1.001 for uv in b["uvs"] for u in uv)
        tot += len(b["triangles"])
    s = json.load(open(root / k / f"thunder_supplicant_{k}_staff_mesh.json"))
    sb = s["bones"]["staff"]
    assert all(0 <= i < len(sb["vertices"]) for t in sb["triangles"] for i in t)
    ys = [v[1] for v in sb["vertices"]]
    print(k, tot, m["triangle_count"], "staff", len(sb["triangles"]), "y", min(ys), max(ys), m["texture_size"],
          sorted(p.name for p in (root / k).iterdir()))
