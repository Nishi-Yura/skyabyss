"""Big views of one armor folder: python tools/rough/armor_v2_view.py <dir> <out.png> [pose] [yaw,pitch ...]
<dir> holds armor.geo.json / armor.png / armor_glowmask.png (what armor_v2_*.py writes)."""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, "tools/rough")
import armor_kit as ak  # noqa: E402
import armor_v2_common as c  # noqa: E402

d = Path(sys.argv[1])
out = sys.argv[2]
pose = sys.argv[3] if len(sys.argv) > 3 else "stand"
views = [tuple(float(v) for v in a.split(",")) for a in sys.argv[4:]] or [(180, 8), (215, 18), (270, 6), (0, 8)]
geo = c.json_load(d / "armor.geo.json")
tex = np.array(Image.open(d / "armor.png").convert("RGBA"))
glow = np.array(Image.open(d / "armor_glowmask.png").convert("RGBA"))
ims = [ak.render_set(geo, tex, glow, pose, y, p, (440, 560)) for y, p in views]
W = Image.new("RGB", (440 * len(ims), 560))
for i, im in enumerate(ims):
    W.paste(im.convert("RGB"), (440 * i, 0))
W.save(out)
