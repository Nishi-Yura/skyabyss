# -*- coding: utf-8 -*-
"""持ち物の欄の絵 (textures/item/<id>.png) と、手や地面で描く点の模型 (lowpoly/items/<id>/<段>.json を自動変換) を並べた一覧を作る。
  python -I tools/gen/splat/icon_vs_model.py   -> docs/review/icon_vs_model/sheet_NN.png と list.csv
欄は 2 つ一組: 左が持ち物の欄の絵、右が点の模型 (正面と斜め)。名前は品の id と、使ったメッシュの段。
"""
import csv
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audit_auto as A  # noqa: E402
import splat_lib as S  # noqa: E402

ASSETS = S.ROOT / "src/main/resources/assets/skyabyss"
OUT = S.ROOT / "docs/review/icon_vs_model"
T = 160
TIER_ORDER = ("legend", "rare", "common")


def items():
    out = []
    for p in sorted((ASSETS / "models/item").glob("*.json")):
        d = json.loads(p.read_text(encoding="utf-8"))
        base = d.get("base", d)
        if base.get("parent") != "builtin/entity":
            continue
        gui = d.get("perspectives", {}).get("gui", {}).get("textures", {}).get("layer0")
        iid = p.stem
        mesh_dir = ASSETS / "lowpoly/items" / iid
        if not mesh_dir.is_dir():
            continue
        tiers = [t for t in TIER_ORDER if (mesh_dir / (t + ".json")).exists()]
        if not tiers or not gui:
            continue
        out.append((iid, tiers[0], gui))
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(3)
    rows, tiles = [], []
    for iid, tier, gui in items():
        icon_path = ASSETS / "textures" / (gui.split(":")[-1] + ".png")
        tile = Image.new("RGB", (T * 3, T), (14, 12, 18))
        if icon_path.exists():
            ic = Image.open(icon_path).convert("RGBA").resize((T, T), Image.NEAREST)
            bg = Image.new("RGBA", (T, T), (40, 38, 48, 255))
            bg.alpha_composite(ic)
            tile.paste(bg.convert("RGB"), (0, 0))
        pts, st = A.auto_points("items/%s/%s" % (iid, tier), rng)
        if pts is not None and len(pts[0]):
            tmp = OUT / "_t.png"
            # 品のメッシュは正面が -Z、刃が +Y。欄の絵に近い向き (正面 = 180) と斜め
            S.preview(pts[0], pts[1], pts[2], pts[3], tmp, yaw_list=(180, 225), pitch=0, px=T)
            tile.paste(Image.open(tmp).convert("RGB"), (T, 0))
        ImageDraw.Draw(tile).text((3, 3), "%s (%s)" % (iid, tier), fill=(240, 240, 240))
        tiles.append(tile)
        rows.append(dict(id=iid, tier=tier, icon=gui, icon_exists=icon_path.exists()))
    cols, rws = 4, 10
    per = cols * rws
    for si in range(0, len(tiles), per):
        sheet = Image.new("RGB", (cols * T * 3, rws * T))
        for j, t in enumerate(tiles[si:si + per]):
            sheet.paste(t, ((j % cols) * T * 3, (j // cols) * T))
        sheet.save(OUT / ("sheet_%02d.png" % (si // per)))
    with open(OUT / "list.csv", "w", newline="", encoding="utf-8") as fo:
        w = csv.DictWriter(fo, fieldnames=["id", "tier", "icon", "icon_exists"])
        w.writeheader()
        w.writerows(rows)
    (OUT / "_t.png").unlink(missing_ok=True)
    print(len(tiles), "items,", (len(tiles) + per - 1) // per, "sheets")


if __name__ == "__main__":
    main()
