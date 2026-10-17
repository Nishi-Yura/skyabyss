"""Raiju B with the tail left alone (budget check): python tools/rough/hp_raiju_lean.py"""
import sys

sys.path.insert(0, "tools/rough")
import hp_proto
import hpkit

m = hpkit.Model.load("raiju")
m.round_all(rule=lambda b, c: None if b["name"].startswith("tail") else hp_proto.rule_b(b, c))
geo = hpkit.finish_geo(m)
print("raiju B-lean", hpkit.stats(geo), "overlaps", len(hpkit.overlaps(geo)))
m.save("docs/review/high_poly_roughs/assets/raiju_Blean")
