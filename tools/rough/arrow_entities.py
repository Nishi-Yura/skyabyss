"""旅の矢・光の矢の飛ぶ姿の絵 (32x32。バニラの矢の絵の型に、選定の案の色を塗る。2026-10-27)。
バニラの矢の絵は、茶 = 矢柄、灰と白 = 羽 (左) と鏃 (右の 4 列)。明るさの段はそのまま残して、色だけ替える。
実行: python tools/rough/arrow_entities.py <バニラの arrow.png>
出力: src/main/resources/assets/skyabyss/textures/entity/travel_arrow.png・glow_arrow.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
LOOKS = {
    # (矢柄, 羽, 鏃)
    "travel_arrow": ((156, 120, 80), (224, 218, 204), (150, 156, 170)),
    "glow_arrow": ((172, 150, 110), (236, 248, 170), (210, 240, 250)),
}


def main(src):
    a = np.array(Image.open(src).convert("RGBA")).astype(float)
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    rgb = a[..., :3]
    lum = rgb.mean(-1) / 255.0
    sat = rgb.max(-1) - rgb.min(-1)
    solid = a[..., 3] > 0
    brown = solid & (sat > 25)
    grey = solid & ~brown
    tip = grey & ((xx % 16) >= 12)
    for name, (shaft, fletch, head) in LOOKS.items():
        out = a.copy()
        for mask, col in ((brown, shaft), (grey & ~tip, fletch), (tip, head)):
            k = (0.55 + 0.6 * lum)[..., None]
            out[..., :3] = np.where(mask[..., None], np.clip(np.array(col) * k, 0, 255), out[..., :3])
        dst = ROOT / "src/main/resources/assets/skyabyss/textures/entity" / f"{name}.png"
        Image.fromarray(out.astype(np.uint8), "RGBA").save(dst)
        print("wrote", dst.name)


if __name__ == "__main__":
    main(sys.argv[1])
