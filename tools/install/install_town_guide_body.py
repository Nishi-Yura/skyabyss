# -*- coding: utf-8 -*-
"""土地の町の案内人 (town/RegionFolk の town_guide_<町>) の体を、都の案内人 (係の人 guide。install_npc2.py guide) から写す。
都の案内人と同じ白い外套と角灯の杖で、「案内人」と一目で分かるように (2026-10-29)。
写すもの: lowpoly/npc_guide(.json / _prop_r.json)・textures/lowpoly/npc_guide(.png / _prop_r.png)・geo・animation。
名前は npc_guest_town_guide、動きの名前は町の人の型 (animation.town_guest.*) に替える。都の案内人を作り直したら、これをもう一度流す。
使い方: python -X utf8 tools/install/install_town_guide_body.py
"""
import json
import shutil
from pathlib import Path

A = Path(__file__).resolve().parents[2] / "src/main/resources/assets/skyabyss"
SRC, DST = "npc_guide", "npc_guest_town_guide"

for part in ("", "_prop_r"):
    d = json.loads((A / "lowpoly" / f"{SRC}{part}.json").read_text(encoding="utf-8"))
    d["name"] = DST + part
    d["texture"] = DST + part
    (A / "lowpoly" / f"{DST}{part}.json").write_text(json.dumps(d, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    shutil.copyfile(A / "textures/lowpoly" / f"{SRC}{part}.png", A / "textures/lowpoly" / f"{DST}{part}.png")
geo = (A / "geo/entity" / f"{SRC}_lowpoly.geo.json").read_text(encoding="utf-8")
(A / "geo/entity" / f"{DST}_lowpoly.geo.json").write_text(geo.replace(f"geometry.{SRC}_lowpoly", f"geometry.{DST}_lowpoly"), encoding="utf-8")
anim = json.loads((A / "animations/entity" / f"{SRC}.animation.json").read_text(encoding="utf-8"))
anim["animations"] = {k.replace("animation.attendant.", "animation.town_guest."): v for k, v in anim["animations"].items()}
(A / "animations/entity" / f"{DST}.animation.json").write_text(json.dumps(anim, ensure_ascii=False, indent=2), encoding="utf-8")
print("wrote", DST)
