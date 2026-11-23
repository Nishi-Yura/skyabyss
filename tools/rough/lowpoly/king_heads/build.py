# -*- coding: utf-8 -*-
"""淵の王 5 体の新しい頭の確かめの絵 (heads.py)。元の模型 (tools/sources/boss_split_base/) に、選んだ案の体の部品と新しい頭を付けて描く。
上の段 = 今の姿、下の段 = 新しい頭 (正面・斜め・横)。

    python -X utf8 -I tools/rough/lowpoly/king_heads/build.py
出力: docs/review/king_heads.png
"""
from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("bs", ROOT / "tools/rough/lowpoly/boss_split/build.py")
bs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bs)
from PIL import Image, ImageDraw  # noqa: E402

BASE = ROOT / "tools/sources/boss_split_base"
PICKS = {"king_bone": 1, "king_salt": 0, "king_storm": 1, "king_ash": 1, "king_shadow": 0}
TITLES = {"king_bone": "骨の王 — 獣の頭蓋", "king_salt": "塩の王 — 塩の晶の冠", "king_storm": "雷の王 — 閉じた兜と避雷針",
          "king_ash": "灰の王 — 炉と煙突", "king_shadow": "影の王 — 白い面と頭巾"}


def load_base(stem):
    data = json.loads((BASE / f"{stem}.json").read_text(encoding="utf-8"))
    geo = json.loads((bs.A / f"geo/entity/{stem}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    xf = bs.he.rest_xf(geo)
    tex = data.get("texture", stem)
    img = np.array(Image.open(BASE / f"{tex}.png").convert("RGBA"))
    gp = BASE / f"{tex}_glowmask.png"
    glow = np.array(Image.open(gp).convert("RGBA"))[..., 3] > 0 if gp.exists() else np.zeros(img.shape[:2], bool)
    H, W = img.shape[:2]
    faces = []
    for bn, b in data["bones"].items():
        if not b["vertices"]:
            continue
        Rm, t = xf(bn)
        Vw = np.array(b["vertices"], float) @ Rm.T + t
        UV = np.array(b["uvs"], float) * np.array([W, H])
        for tr in b["triangles"]:
            v = Vw[tr]
            n = np.cross(v[1] - v[0], v[2] - v[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n = n / np.linalg.norm(n)
            f = bs.Face(verts=v, bone=bn, mat="x", ref=v.mean(0) - n)
            f.normal = n
            f.uv = UV[tr]
            faces.append(f)
    return faces, img, glow


def main():
    W, Hh = 300, 330
    views = ((180, 4), (215, 10), (270, 4))
    names = sys.argv[1:] or list(PICKS)
    cv = Image.new("RGB", (20 + len(views) * W, 30 + len(names) * (2 * Hh + 50)), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    F = bs.voxel.font
    for i, name in enumerate(names):
        y0 = 20 + i * (2 * Hh + 50)
        d.text((20, y0), TITLES[name] + "  (上: 今 / 下: 新しい頭)", font=F(22), fill=(236, 232, 218))
        now_f, now_i, now_g = bs.load(f"{name}_c0")
        faces, img, glow = load_base(f"{name}_c0")
        label, body, metal, light, drop, fn = bs.KINGS[f"{name}_c0"][1][PICKS[name]]
        R = bs.Ref(faces)
        kept = [f for f in faces if f.bone not in drop]
        ri = bs.recolor(img, glow, bs.PAL[body], bs.PAL[metal], bs.PAL[light])
        af, aa, ag = bs.parts(fn, R, light)
        new_f, new_i, new_g = bs.merge(kept, ri, glow, af, aa, ag)
        print(name, "added tris", sum(len(f.tris()) for f in af), flush=True)
        c = np.array([0, 57, 0.0])
        for r, (ff, at, g) in enumerate(((now_f, now_i, now_g), (new_f, new_i, new_g))):
            a2, g2 = bs.square(at, g)
            for j, (yaw, pitch) in enumerate(views):
                im = bs.render(ff, a2[..., :3], g2, yaw, pitch, (W, Hh), c, W * 0.46 / 20)
                bg = Image.new("RGBA", (W, Hh), (28, 28, 38, 255))
                bg.alpha_composite(im)
                cv.paste(bg.convert("RGB"), (20 + j * W, y0 + 36 + r * Hh))
    out = ROOT / ("docs/review/king_heads.png" if len(names) == len(PICKS) else f"docs/review/king_heads_{names[0]}.png")
    cv.save(out)
    print(out)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
