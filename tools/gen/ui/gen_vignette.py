"""影の下で画面の縁を暗くするビネット画像を作る。

使い方: python tools/gen/ui/gen_vignette.py
出力先: src/main/resources/assets/skyabyss/textures/gui/sky_vignette.png
"""
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 256
OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/textures/gui/sky_vignette.png"

y, x = np.mgrid[0:SIZE, 0:SIZE]
r = np.hypot((x + 0.5) / SIZE * 2 - 1, (y + 0.5) / SIZE * 2 - 1) / np.sqrt(2)
t = np.clip((r - 0.25) / 0.75, 0, 1)
alpha = (t * t * (3 - 2 * t)) ** 1.3

img = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
img[..., :3] = (4, 4, 10)
img[..., 3] = (alpha * 255).astype(np.uint8)
OUT.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(img, "RGBA").save(OUT)
print(OUT.name)
