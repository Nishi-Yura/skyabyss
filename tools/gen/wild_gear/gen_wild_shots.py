# -*- coding: utf-8 -*-
"""野の装備 (docs/design/51) の撮影の台本 tools/shots/wild_gear.txt を作る: 武器 102 本の三人称の正面 (弓は引き絵も一枚) と一人称、防具 18 式の前と後ろ。
実行: python -I tools/gen/wild_gear/gen_wild_shots.py
撮る: bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/wild_gear.txt -PshotsName=wild_gear
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import spec as S  # noqa: E402

OUT = HERE.parents[2] / "tools/shots/wild_gear.txt"
L = ["# 野の装備 (docs/design/51): 武器 102 本の一人称と三人称、防具 18 式の前と後ろ。tools/gen/wild_gear/gen_wild_shots.py が作る",
     "# bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/wild_gear.txt -PshotsName=wild_gear",
     "world shots flat", "size 960 540", "timeout 6000", "render 6", "cmd time set noon", "cmd weather clear", "cmd gamemode creative",
     "cmd fill -20 -61 -20 20 -61 20 minecraft:smooth_basalt destroy", "camera 0 -60 8 180 0", "waitchunks", "hud on", "wait 400",
     "cmd skyabyss cinematic stop", "close", "wait 20", "swingpose off", "mainhand right"]
for w in S.WEAPONS:
    L += ["perspective first", "hud on", f"cmd item replace entity @s weapon.mainhand with skyabyss:{w['id']}", "close", "wait 24", "chat clear",
          f"shot fp_{w['id']}", "perspective front", "hud off", "wait 8", f"shot tp_{w['id']}"]
L += ["cmd clear @s", "hud off", "fov 30"]
for a in S.ARMORS:
    for slot, p in (("feet", "boots"), ("legs", "leggings"), ("chest", "chestplate"), ("head", "helmet")):
        L.append(f"cmd item replace entity @s armor.{slot} with skyabyss:{a['id']}_{p}")
    L += ["perspective front", "bodyyaw 25", "wait 16", f"shot worn_{a['id']}", "perspective back", "bodyyaw 0", "wait 4", f"shot back_{a['id']}"]
L += ["fov 70"]
OUT.write_text("\n".join(L) + "\n", encoding="utf-8")
print("wrote", OUT, len(L), "lines")
