"""巨神ごとの共鳴の欠片 (交換の通貨。docs/design/03 の 2.2節) の絵を作る。

共通の共鳴の欠片 (item/resonance_shard.png。紫と青緑の二色) の色味を、巨神ごとの二色に差し替えて書く。形・明るさ・縁・芯の白は変えない。
  textures/item/resonance_shard_stone_whale.png   岩鯨: 骨の黄白と深い青緑
  textures/item/resonance_shard_tide_shell.png    潮殻: 青緑と海の青
  textures/item/resonance_shard_thunder_wing.png  雷翼: 銅の金と雷の青白
  textures/item/resonance_shard_ember.png         余燼: 熾火の橙と琥珀
品のモデル (models/item/resonance_shard_<巨神>.json) は手で置いてある。古い共通の欠片の絵は触らない。

使い方 (リポジトリ直下): python tools/gen/items/gen_resonance_shards.py
"""
import colorsys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/item"

# (紫の側の色相, 紫の側の彩度の掛け率, 青緑の側の色相, 青緑の側の彩度の掛け率)  色相は 0〜360 度
PALETTES = {
    "stone_whale": (42, 0.50, 188, 0.85),
    "tide_shell": (172, 0.95, 208, 0.90),
    "thunder_wing": (46, 1.05, 200, 0.55),
    "ember": (14, 1.10, 38, 1.00),
}


def recolor(img, violet_hue, violet_sat, cyan_hue, cyan_sat):
    out = img.convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.12:
                continue  # 芯の白・灰は変えない
            deg = h * 360
            # 紫 (230〜330 度) は一色目、青緑 (150〜230 度) は二色目
            if 230 <= deg <= 330:
                nh, ns = violet_hue, min(1.0, s * violet_sat)
            elif 150 <= deg < 230:
                nh, ns = cyan_hue, min(1.0, s * cyan_sat)
            else:
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(nh / 360, ns, v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def main():
    base = Image.open(TEX / "resonance_shard.png")
    for titan, pal in PALETTES.items():
        recolor(base, *pal).save(TEX / f"resonance_shard_{titan}.png")
    print("resonance shard icons written")


if __name__ == "__main__":
    main()
