"""Render the current (v1) armor on the mannequin: python tools/rough/armor_v2_now.py <name> <out.png>"""
import sys

sys.path.insert(0, "tools/rough")
import armor_v2_common as c  # noqa: E402

name, out = sys.argv[1], sys.argv[2]
g, t, gl = c.load_current(name)
rl = [v[0] for v in c.VIEWS] + [v[0] for v in c.POSE_VIEWS]
c.sheet(name, [c.column(g, t, gl)], ["now"], rl).save(out)
