"""雷翼の背 (鳥の形の背中・三つの試練) のブロックとアイテムの絵とモデル (12 の 4 章)。

使い方 (リポジトリ直下で): python tools/gen/items/gen_storm_back_assets.py
- 羽鏡 (feather_mirror): 台の上に 45 度に立てた磨いた銅の羽の板 (片面。裏は暗い銅)。ROT 0〜3 は y の回転
絵はすべて 32x32 (art direction)。乱数は seed 固定
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
random.seed(20261016)


def save_png(img, rel):
    p = A / "textures" / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)


def write(rel, obj):
    p = A / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def copper(dark=False):
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            n = random.randint(-10, 10)
            c = (200 + n, 120 + n // 2, 72 + n // 3) if not dark else (110 + n, 62 + n // 2, 40)
            # 羽の筋
            if (x + y // 2) % 6 == 0 and not dark:
                c = (236, 170, 110)
            img.putpixel((x, y), c + (255,))
    return img


def main():
    save_png(copper(), "block/feather_mirror.png")
    save_png(copper(True), "block/feather_mirror_back.png")

    # 羽鏡: 低い台 + 45 度に立てた板 (ROT 0 は北東を向く面 = 北と東をつなぐ)
    mirror = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "skyabyss:block/feather_mirror", "face": "skyabyss:block/feather_mirror",
                     "back": "skyabyss:block/feather_mirror_back", "base": "minecraft:block/chiseled_quartz_block_top"},
        "elements": [
            {"from": [3, 0, 3], "to": [13, 2, 13],
             "faces": {d: {"texture": "#base"} for d in ("north", "south", "east", "west", "up", "down")}},
            # 板 (x 方向に長く、z の中心に薄く。y 軸で 45 度回す)
            {"from": [-1.5, 2, 7.25], "to": [17.5, 15, 8.75],
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": False},
             "faces": {"north": {"texture": "#face"}, "south": {"texture": "#back"},
                       "east": {"texture": "#back"}, "west": {"texture": "#back"},
                       "up": {"texture": "#back"}, "down": {"texture": "#back"}}},
        ],
    }
    write("models/block/feather_mirror.json", mirror)
    write("blockstates/feather_mirror.json", {"variants": {
        f"rot={k}": {"model": "skyabyss:block/feather_mirror", "y": [0, 90, 180, 270][k]} for k in range(4)}})
    print("storm back assets written")


if __name__ == "__main__":
    main()
