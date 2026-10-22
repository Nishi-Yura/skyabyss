"""Install the high-poly gear (weapons/shields) and 3D armor chosen in docs/review/high_poly_roughs (option C by default).

  python tools/install/install_hp_gear.py [--option A|B|C]

Input: tools/sources/hp_base/gear/ (the models before the high-poly pass), processed by tools/rough/hp_gear.py. (Armor left this script: tools/install/install_armor_v2.py, docs/design/16.)
Output: assets/skyabyss/geo/{gear,armor}/<n>.geo.json, textures/{gear,armor}/<n>.png (+ _glowmask.png when it had one).
Bones, pivots and the bone names (armorHead ...) are untouched.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import hp_gear  # noqa: E402
import hpkit  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", default="C", choices="ABC")
    a = ap.parse_args()
    import os
    os.chdir(ROOT)
    for kind, names in (("gear", hp_gear.GEAR), ("armor", hp_gear.ARMOR)):
        for n in names:
            m, _ = hp_gear.build(n, kind, a.option)
            geo, tex, glow = m.finalize()
            ov = hpkit.overlaps(geo)
            assert not ov, (n, ov[:3])
            had_glow = (hp_gear.BASE / kind / f"{n}_glowmask.png").exists()
            if had_glow:
                assert (glow[..., 3] > 0).any(), f"{n}: glowmask has no glowing pixel"
            (RES / "geo" / kind / f"{n}.geo.json").write_text(json.dumps(geo, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
            Image.fromarray(tex).save(RES / "textures" / kind / f"{n}.png")
            if had_glow:
                Image.fromarray(glow).save(RES / "textures" / kind / f"{n}_glowmask.png")
            print(kind, n, hpkit.stats(geo)[:2])


if __name__ == "__main__":
    main()
