"""メッシュ JSON の検査: 骨名・pivot・添字・uv の範囲・左右の鏡像。python check.py"""
import json
from common import *  # noqa

geo = bone_defs()
bad = 0
for o in "abc":
    for lod in ("near", "mid", "far"):
        m = json.loads((OUTDIR / o / f"{lod}_mesh.json").read_text(encoding="utf-8"))
        assert set(m["bones"]) == set(geo), (o, lod, set(m["bones"]) ^ set(geo))
        n = 0
        for k, b in m["bones"].items():
            assert b["parent"] == geo[k][0] and b["pivot"] == geo[k][1], (o, lod, k)
            nv = len(b["vertices"])
            assert len(b["uvs"]) == nv
            for t in b["triangles"]:
                assert all(0 <= i < nv for i in t)
            for u, v in b["uvs"]:
                assert 0 <= u <= 1 and 0 <= v <= 1
            n += len(b["triangles"])
        assert n == m["triangle_count"]
        # 左右の鏡像
        for a, b_ in (("wing_l", "wing_r"), ("hand_l", "hand_r"), ("leg_l", "leg_r")):
            A = np.array(m["bones"][a]["vertices"]) if m["bones"][a]["vertices"] else np.zeros((0, 3))
            B = np.array(m["bones"][b_]["vertices"]) if m["bones"][b_]["vertices"] else np.zeros((0, 3))
            B2 = B.copy()
            B2[:, 0] *= -1
            sa = sorted(map(tuple, np.round(A, 2)))
            sb = sorted(map(tuple, np.round(B2, 2)))
            same = sa == sb
            if not same:
                bad += 1
            print(o, lod, a, b_, len(A), len(B), "mirror OK" if same else "MIRROR DIFF")
        print(o, lod, "tris", n, "atlas", m["texture_size"])
print("bad", bad)
