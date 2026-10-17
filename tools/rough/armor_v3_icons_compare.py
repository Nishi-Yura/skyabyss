"""Icon sheet v2 (git history) vs v3 (working tree): python tools/rough/armor_v3_icons_compare.py [rev] [out]"""
import io
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
rev = sys.argv[1] if len(sys.argv) > 1 else "76438da"
out = sys.argv[2] if len(sys.argv) > 2 else str(ROOT / "docs/review/armor_v2_roughs/icons_v2_v3.png")
names = ["stone_whale", "tide_shell", "thunder_wing", "ember"]
parts = ["helmet", "chestplate", "leggings", "boots"]
S = 4
W = Image.new("RGB", (8 * 32 * S + 40, 4 * 32 * S), (34, 34, 46))
for r, n in enumerate(names):
    for c, p in enumerate(parts):
        rel = f"src/main/resources/assets/skyabyss/textures/item/{n}_{p}.png"
        old = Image.open(io.BytesIO(subprocess.run(["git", "-C", str(ROOT), "show", f"{rev}:{rel}"], capture_output=True, check=True).stdout)).convert("RGBA")
        new = Image.open(ROOT / rel).convert("RGBA")
        for k, im in enumerate((old, new)):
            im = im.resize((32 * S, 32 * S), Image.NEAREST)
            W.paste(im, ((c + 4 * k) * 32 * S + 40 * k, r * 32 * S), im)
W.save(out)
