"""Shared pieces of the armor v2 roughs: sheet rendering and the 'now' armor next to the mannequin."""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
import armor_kit as ak  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
VIEWS = [("front", 180, 8), ("side", 270, 6), ("back", 0, 8), ("3/4", 215, 18)]
POSE_VIEWS = [("arms up", "arms_up", 200, 14), ("walk (side)", "walk", 270, 6), ("swing 3/4", "swing", 150, 14)]


def font(sz=16):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/arial.ttf"):
        try:
            return ImageFont.truetype(f, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def json_load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def load_current(name):
    geo = json_load(RES / f"geo/armor/{name}.geo.json")
    tex = np.array(Image.open(RES / f"textures/armor/{name}.png").convert("RGBA"))
    g = RES / f"textures/armor/{name}_glowmask.png"
    glow = np.array(Image.open(g).convert("RGBA")) if g.exists() else None
    return geo, tex, glow


def column(geo, tex, glow, size=(300, 400), extra=True):
    """list of PIL images (views + poses) for one model"""
    ims = [ak.render_set(geo, tex, glow, "stand", y, p, size) for _, y, p in VIEWS]
    if extra:
        ims += [ak.render_set(geo, tex, glow, pz, y, p, size) for _, pz, y, p in POSE_VIEWS]
    return ims


def sheet(title, cols, labels, rowlabels, size=(300, 400)):
    """cols: one list[Image] per model; drawn as one ROW per model, one column per view"""
    ncol = len(cols[0])
    W = ncol * size[0] + 130
    H = len(cols) * size[1] + 70
    img = Image.new("RGB", (W, H), (22, 22, 30))
    d = ImageDraw.Draw(img)
    d.text((10, 8), title, fill=(230, 230, 240), font=font(20))
    for ri, rl in enumerate(rowlabels):
        d.text((130 + ri * size[0] + 6, 40), rl, fill=(200, 200, 160), font=font(15))
    for ci, (col, lab) in enumerate(zip(cols, labels)):
        d.text((8, 70 + ci * size[1]), lab, fill=(120, 210, 230), font=font(16))
        for ri, im in enumerate(col):
            img.paste(im.convert("RGB"), (130 + ri * size[0], 66 + ci * size[1]))
    return img
