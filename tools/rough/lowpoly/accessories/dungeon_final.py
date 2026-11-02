"""地下のダンジョンの装飾品 4 品 (00 の 368〜371 で選んだ案) を作ってゲームへ入れる (docs/design/55 の 5 章)。
形は dungeon.py のラフ (368 A 顎飾り・369 A 螺貝・370 C 背晶・371 A 炉の熾)。背晶の欠片だけ、吊る輪を上へ付け替えた (体に下げるため)。

    python -X utf8 -I tools/rough/lowpoly/accessories/dungeon_final.py

出力: docs/review/lowpoly_roughs/accessories_final/<id>/rare_{mesh.json,atlas.png,glowmask.png} (レアもエピックも形の段は rare)、
      assets/skyabyss/textures/item/<id>.png (32x32 の持ち物欄の絵)、そのあと tools/install/install_lowpoly_accessories.py の手順で
      lowpoly/items/<id>/rare.json・textures/lowpoly/items/<id>/・models/item/<id>.json (手・地面は立体、持ち物欄は絵)・models/item_flat/<id>.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import dungeon as D  # noqa: E402
from common import *  # noqa: E402,F401,F403

FINAL = ROOT / "docs/review/lowpoly_roughs/accessories_final"
ICON_DIR = ROOT / "src/main/resources/assets/skyabyss/textures/item"
MODEL_DIR = ROOT / "src/main/resources/assets/skyabyss/models/item"


def crystal_final(r):
    """370 C 角の枠に浮かぶ菱形の結晶 (吊る輪を上に)"""
    f = []
    for s in (1, -1):
        f += D.bent("main", [(s * 1.0, -5.0, 0), (s * 3.6, -1.6, 0), (s * 3.0, 2.6, 0), (s * 1.2, 5.0, 0)], [0.8, 0.8, 0.5, 0.25])
    f += lpkit.spike("w", "gem", (0, -0.4, 0), (0, 1, 0), 3.6, 1.6, sides=4, rings=1)
    f += lpkit.spike("w", "gem", (0, -0.4, 0), (0, -1, 0), 3.0, 1.6, sides=4, rings=1)
    f += block("guard", (0, 5.4, 0), (3.0, 1.2, 1.8))
    f += D.cord((0, 7.0, 0))
    return f


# id, 形, 巨神の材質, 名前
ITEMS = [("pit_jaw_pendant", D.jaw_a, "whale"), ("tide_spiral_shell", D.shell_a, "tide"),
         ("storm_crystal_shard", crystal_final, "thunder"), ("mine_forge_ember", D.forge_a, "ember")]


def icon(r, titan):
    pts = np.concatenate([f.verts for f in r["faces"]])
    ext = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]))
    mid = (pts.max(0) + pts.min(0)) / 2
    im = view(r["faces"], r["atlas"], r["glow"], titan, 200, 8, (128, 128), (mid[0], mid[1], mid[2]), 28.0 / ext * 4)
    small = Image.fromarray(np.array(im)).resize((32, 32), Image.BOX)
    arr = np.array(small).astype(float)
    solid = arr[..., 3] >= 70
    rgb = np.clip(arr[..., :3] * 1.45 + 10, 0, 255)
    out = np.zeros((32, 32, 4), np.uint8)
    out[solid, :3] = rgb[solid].astype(np.uint8)
    out[solid, 3] = 255
    near = np.zeros_like(solid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(solid, dx, 1), dy, 0)
    out[~solid & near] = (14, 10, 22, 255)
    return Image.fromarray(out, "RGBA")


def main():
    for iid, fn, titan in ITEMS:
        r = make(fn, titan, "rare")
        save(r, FINAL / iid, "rare")
        icon(r, titan).save(ICON_DIR / f"{iid}.png")
        path = MODEL_DIR / f"{iid}.json"
        if not path.exists():
            path.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{iid}"}}) + "\n", encoding="utf-8")
        print(iid, r["tris"], "tris", flush=True)
    sys.path.insert(0, str(ROOT / "tools/install"))
    import install_lowpoly_accessories as I
    I.ITEMS = [iid for iid, _, _ in ITEMS]
    I.TIERS = ["rare"]
    I.install_meshes()
    I.install_models()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
