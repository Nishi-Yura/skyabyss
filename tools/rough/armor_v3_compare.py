"""v3 before/after sheets of the four titan armors on the mannequin (v2 from the repository history, v3 from armor_v2_sets).

python tools/rough/armor_v3_compare.py [--out docs/review/armor_v2_roughs]
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, "tools/rough")
import armor_v2_common as c  # noqa: E402
import armor_v2_sets as S  # noqa: E402

c.V1_REV = "76438da"   # the commit that still has the v2 armor (open-face helmets came after)

NAMES = {"stone_whale": "stone_whale_armor", "tide_shell": "tide_shell_armor", "thunder_wing": "thunder_wing_armor", "ember": "ember_armor"}

ap = argparse.ArgumentParser()
ap.add_argument("--out", default="docs/review/armor_v2_roughs")
ar = ap.parse_args()
out = Path(ar.out)
out.mkdir(parents=True, exist_ok=True)
rl = [v[0] for v in c.VIEWS] + [v[0] for v in c.POSE_VIEWS]
allcols, alllabels = [], []
for n, full in NAMES.items():
    g0, t0, gl0 = c.load_v1(full)
    a = S.SETS[n]()
    g1, t1, gl1 = a.build(chamfer=S.CHAMFER)
    cu, fq = a.stats()
    col0 = c.column(g0, t0, gl0, size=(260, 340))
    col1 = c.column(g1, t1, gl1, size=(260, 340))
    c.sheet(f"{n}: before (v2) / after (v3)", [col0, col1], ["before v2", f"after v3 ({cu}/{fq})"], rl, size=(260, 340)).save(out / f"compare_v3_{n}.png")
    allcols.append(col1)
    alllabels.append(f"{n} ({cu}/{fq})")
c.sheet("armor v3: the four titan sets", allcols, alllabels, rl, size=(260, 340)).save(out / "sets_v3.png")
print("done")
