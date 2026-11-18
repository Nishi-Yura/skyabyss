"""古い防具 7 組の作り直し (old_armor_v2.py) の見本の絵と、ゲームへの入れ。

    python -X utf8 tools/rough/lowpoly/armor/build_old_armor_v2.py review [組 ...]
    python -X utf8 tools/rough/lowpoly/armor/build_old_armor_v2.py install [組 ...]

review : docs/review/armor_old/v2_<組>.png (前・横・後ろ・3/4)
install: lowpoly/armor/<組>/<部位>.json・textures/lowpoly/armor/<組>/<部位>.png (+ _glowmask)・textures/item/<組>_<部位>.png (3D から描くアイコン)
塗りの表: 旅装 4 組は tpaint の組の表、初めの旅装・鉄張り・空石は build_outfit の START / IRON / SKY。
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path[:0] = [str(ROOT / "tools/install"), str(ROOT / "tools/lib"), str(HERE), str(HERE.parent)]

import build_one  # noqa: E402
import build_outfit  # noqa: E402
import tpaint  # noqa: E402
import travel_one  # noqa: E402
import old_armor_v2 as OV  # noqa: E402

REVIEW = ROOT / "docs/review/armor_old"
TABLE = {"wayfarer": build_outfit.START, "ironclad": build_outfit.IRON, "sky_stone": build_outfit.SKY}
GLOW = {"whale_road": "whale", "tide_wader": "tide", "peak_crossing": "thunder", "ash_walking": "ember"}


def register():
    for name, fn in OV.SETS.items():
        key = "v2_" + name
        travel_one.SETS[key] = fn
        tpaint.SETS[key] = TABLE.get(name) or tpaint.SETS[name]
        build_one.TRAVEL_GLOW[key] = GLOW.get(name, "whale")


def review(names):
    import aview
    aview.SS = 2
    REVIEW.mkdir(parents=True, exist_ok=True)
    rows = []
    for name in names:
        res = build_one.travel_set("v2_" + name)
        im = build_one.strip(res, build_one.TRAVEL_GLOW["v2_" + name], name)
        im.save(REVIEW / f"v2_{name}.png")
        rows.append(im)
        print(name, sum(r["tris"] for r in res.values()), {k: r["tris"] for k, r in res.items()}, flush=True)
    if len(rows) > 1:
        S = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
        y = 0
        for r in rows:
            S.paste(r, (0, y))
            y += r.height
        S.save(REVIEW / "v2_all.png")


def install(names):
    import aview
    import apaint
    import install_armor_one as IA
    import build_series_icons as BI
    aview.apaint = apaint
    aview.SS = 2
    raw = REVIEW / "raw"
    for name in names:
        key = "v2_" + name
        res = build_one.travel_set(key)
        aview.save_set(res, raw / name, name)
        for p in res:
            IA.put_mesh(raw / name / f"{p}_mesh.json", IA.ASSETS / "lowpoly/armor" / name / f"{p}.json", f"armor/{name}/{p}")
            IA.put_tex(raw / name / f"{p}_atlas.png", raw / name / f"{p}_glowmask.png", IA.ASSETS / "textures/lowpoly/armor" / name / p)
            ic = BI.to_icon(BI.render_piece(res, build_one.TRAVEL_GLOW[key], p))
            ic.save(IA.ASSETS / "textures/item" / f"{name}_{p}.png")
        print(name, "installed:", ", ".join(f"{p} {r['tris']}" for p, r in res.items()), flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    register()
    mode = sys.argv[1] if len(sys.argv) > 1 else "review"
    names = sys.argv[2:] or list(OV.SETS)
    (install if mode == "install" else review)(names)
