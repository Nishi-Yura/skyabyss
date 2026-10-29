# -*- coding: utf-8 -*-
"""終章「雲海」の絵と見た目の定義 (docs/design/50)。
  雲 (skyabyss:cloud): 32x32。白い綿の塊が重なり、影は淡い青灰、縁は少し透ける (描き方 translucent)
  python -X utf8 tools/gen/finale/gen_finale_assets.py
"""
import json
import math
from pathlib import Path

from PIL import Image

A = Path(__file__).resolve().parents[3] / "src/main/resources/assets/skyabyss"


def hsh(x, y, s):
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def cloud_tex():
    N = 32
    img = Image.new("RGBA", (N, N))
    # 綿の塊: いくつかの円を重ね、上から光、下に影 (タイルとしてつながるように円を折り返す)
    puffs = [(6, 8, 7), (20, 6, 8), (28, 18, 7), (12, 22, 8), (2, 26, 6), (24, 28, 6), (16, 14, 6)]
    for y in range(N):
        for x in range(N):
            best = -1.0
            shade = 0.0
            for cx, cy, r in puffs:
                for ox in (-N, 0, N):
                    for oy in (-N, 0, N):
                        dx, dy = x - (cx + ox), y - (cy + oy)
                        d = math.hypot(dx, dy) / r
                        if d < 1.0:
                            v = 1 - d
                            if v > best:
                                best = v
                                shade = dy / r  # 下ほど影
            base = 0.93 + 0.05 * best - 0.10 * max(0.0, shade) + 0.03 * (hsh(x, y, 1) - 0.5)
            if best < 0:
                base = 0.84 + 0.03 * (hsh(x, y, 2) - 0.5)
            r = int(min(255, 255 * base))
            g = int(min(255, 255 * (base - 0.005)))
            b = int(min(255, 255 * (base + 0.02)))
            a = 235 if best >= 0 else 205
            img.putpixel((x, y), (r, g, b, a))
    return img


def main():
    (A / "textures/block").mkdir(parents=True, exist_ok=True)
    cloud_tex().save(A / "textures/block/cloud.png")
    (A / "models/block/cloud.json").write_text(json.dumps({
        "parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
        "textures": {"all": "skyabyss:block/cloud", "particle": "skyabyss:block/cloud"}}, indent=2), encoding="utf-8")
    (A / "blockstates/cloud.json").write_text(json.dumps({"variants": {"": {"model": "skyabyss:block/cloud"}}}, indent=2), encoding="utf-8")
    print("cloud assets written")


if __name__ == "__main__":
    main()
