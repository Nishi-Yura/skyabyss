# -*- coding: utf-8 -*-
"""黄金比の板 (client/ui/Golden) の地のテクスチャ。

- textures/gui/golden/stone.png : 板の地 (暗い石)。64x64 で継ぎ目なく並ぶ。1 画素 = GUI の 1 画素 (画素ちょうどで描くため 2 倍密度にしない)
- textures/gui/golden/recess.png : 欄の地 (一段暗い石)。同じく 64x64

縁・玉・線は Golden.java がコードで描く (板の大きさが変わっても同じ太さ)。
    python tools/gen/ui/gen_golden_assets.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/textures/gui/golden"
rng = np.random.default_rng(20261104)

SLATE = (28, 32, 41)
RECESS = (18, 21, 28)


def tile(base, sd):
    noise = rng.normal(0, sd, (64, 64))
    # ところどころに少し明るい・暗い粒 (石の目)
    grain = rng.random((64, 64))
    noise += np.where(grain > 0.985, 4.0, 0.0) - np.where(grain < 0.012, 3.0, 0.0)
    rgb = np.clip(np.array(base, dtype=float)[None, None, :] + noise[..., None], 0, 255).astype(np.uint8)
    alpha = np.full((64, 64, 1), 255, dtype=np.uint8)
    return Image.fromarray(np.concatenate([rgb, alpha], axis=2), "RGBA")


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    tile(SLATE, 1.6).save(ROOT / "stone.png")
    tile(RECESS, 1.0).save(ROOT / "recess.png")
    print("wrote", ROOT / "stone.png", ROOT / "recess.png")


if __name__ == "__main__":
    main()
