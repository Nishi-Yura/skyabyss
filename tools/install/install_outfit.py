#!/usr/bin/env python3
"""市の武具屋・道具屋の品を入れる (2026-10-27 の選定。バニラの盾・斧・つるはし・シャベル・革と鉄の防具・ダイヤの胸当ての代わり)。
  手の品 4 点 (旅の盾 A・伐り斧 A・鶴嘴 A・土掘り A): tools/rough/lowpoly/weapons/outfit/build_outfit.py の docs/review/roughs_outfit/raw/<id>/<案>/
      を install_job_weapons と同じ形で入れる。持ち方は盾 = 珊瑚の盾 (構えたときの coral_shield_blocking も写す)、道具 = 余燼の戦斧
  防具 3 組 (初めの旅装 B = wayfarer・鉄張りの旅装 A = ironclad・空石の胸当て C = sky_stone): tools/rough/lowpoly/armor/build_outfit.py の形を
      書き出して、旅装と同じ所 (lowpoly/armor/<組>/<部位>.json・textures/lowpoly/armor/<組>/<部位>.png・textures/item/<組>_<部位>.png) へ
使い方: python -I tools/install/install_outfit.py   (何度流してもよい)
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "tools/install"), str(ROOT / "tools/lib"), str(ROOT / "tools/rough/lowpoly/armor"),
                str(ROOT / "tools/rough/lowpoly"), str(ROOT / "tools/rough/lowpoly/weapons")]

import install_job_weapons as IJ  # noqa: E402
import install_armor_one as IA  # noqa: E402

HELD = {"travel_shield": ("a", "coral_shield"), "gather_axe": ("a", "ember_war_axe"), "gather_pickaxe": ("a", "ember_war_axe"),
        "gather_shovel": ("a", "ember_war_axe")}
# 初めの旅装と鉄張りの旅装は 2026-10-28 の作り直しの選定 (outfit_two の start2_a 旅の外套・iron2_a 鎖帷子と陣羽織)
ARMOR = {"wayfarer": "start2_a", "ironclad": "iron2_a", "sky_stone": "sky_c"}


def held():
    for item, (opt, analog) in HELD.items():
        IJ.ITEMS[item] = ("roughs_outfit", "common", analog, 1)
        IJ.install(item, opt)
    # 盾を構えたときのモデル
    models = IJ.ASSETS / "models/item"
    data = (models / "coral_shield_blocking.json").read_text(encoding="utf-8").replace("coral_shield", "travel_shield")
    (models / "travel_shield_blocking.json").write_text(data, encoding="utf-8")


def armor():
    import build_outfit  # noqa: F401  (旅装の表に組を足す)
    import build_outfit2  # noqa: F401  (作り直しの組 start2_* / iron2_*)
    import build_one
    import aview
    aview.SS = 2
    raw = ROOT / "docs/review/roughs_outfit/armor/raw"
    import apaint
    import build_series_icons as BI
    aview.apaint = apaint
    for name, key in ARMOR.items():
        res = build_one.travel_set(key)
        aview.save_set(res, raw / key, key)
        for p in res:
            IA.put_mesh(raw / key / f"{p}_mesh.json", IA.ASSETS / "lowpoly/armor" / name / f"{p}.json", f"armor/{name}/{p}")
            IA.put_tex(raw / key / f"{p}_atlas.png", raw / key / f"{p}_glowmask.png", IA.ASSETS / "textures/lowpoly/armor" / name / p)
            ic = BI.to_icon(BI.render_piece(res, build_one.TRAVEL_GLOW[key], p))
            ic.save(IA.ASSETS / "textures/item" / f"{name}_{p}.png")
        print(name, "armor installed:", ", ".join(res))


if __name__ == "__main__":
    if "armor" not in sys.argv[1:]:
        held()
    armor()
