"""雷翼の背 (鳥の形の背中・三つの試練) のブロックとアイテムの絵とモデル (12 の 4 章)。

使い方 (リポジトリ直下で): python tools/gen_storm_back_assets.py
- 風鳴り石 (storm_chime_stone): 方解石の地に銅の羽の模様。鳴ると青白く光る (active)
- 羽鏡 (feather_mirror): 台の上に 45 度に立てた磨いた銅の羽の板 (片面。裏は暗い銅)。ROT 0〜3 は y の回転
- 受け羽 (feather_receiver): 壁の白い羽の飾り。光ると真珠の灯りの色 (lit)
- 風切りの羽根 (gale_feather): 32x32 のアイテムの絵 (灰青の風切羽に白い羽軸。仮。ラフ 3 案はまだ)
絵はすべて 32x32 (art direction)。乱数は seed 固定
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
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


def stone(active):
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            n = random.randint(-8, 8)
            base = (226 + n, 228 + n, 222 + n)
            # 羽の模様 (中心から外へ開く筋)
            dx, dy = x - 15.5, y - 15.5
            a = math.atan2(dy, dx)
            r = math.hypot(dx, dy)
            vane = abs(math.sin(a * 4)) < 0.18 and 4 < r < 14
            if vane:
                base = (110, 190, 255) if active else (184, 115, 64)
            if r < 3.5:
                base = (235, 248, 255) if active else (150, 160, 175)
            if x in (0, 31) or y in (0, 31):
                base = (160, 165, 170)
            img.putpixel((x, y), base + (255,))
    return img


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


def receiver(lit):
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            n = random.randint(-6, 6)
            c = (214 + n, 216 + n, 220 + n)
            dx = x - 15.5
            # 縦の白い羽と羽軸
            if abs(dx) < 9 - abs(y - 16) * 0.25 and 2 < y < 30:
                c = (250, 250, 252) if not lit else (255, 250, 236)
                if (y + int(abs(dx))) % 5 == 0:
                    c = (226, 230, 236) if not lit else (255, 236, 200)
            if abs(dx) < 1 and 2 < y < 30:
                c = (180, 186, 196) if not lit else (255, 214, 150)
            img.putpixel((x, y), c + (255,))
    return img


def feather_item():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # 斜めの風切羽 (左下 → 右上)。羽軸は白、羽弁は灰青で外が濃い、先は白い
    for t in range(0, 100):
        u = t / 99
        cx = 5 + u * 22
        cy = 27 - u * 22
        w = 5.2 * math.sin(math.pi * min(1, u * 1.1)) + 0.5
        for s in range(-7, 8):
            if abs(s) > w:
                continue
            # 羽軸に垂直な向き
            px = int(round(cx + s * 0.7071))
            py = int(round(cy + s * 0.7071))
            if not (0 <= px < 32 and 0 <= py < 32):
                continue
            edge = abs(s) / max(w, 0.1)
            if abs(s) < 0.6:
                c = (240, 244, 248)
            elif u > 0.85:
                c = (232, 238, 246)
            else:
                g = int(150 - 50 * edge)
                c = (g - 20, g, g + 30)
                if (t + s * 3) % 9 == 0:
                    c = (200, 212, 228)
            img.putpixel((px, py), c + (255,))
    # 根元の銅の結び
    for (x, y) in [(4, 27), (5, 27), (4, 28), (5, 28), (6, 26)]:
        img.putpixel((x, y), (200, 120, 70, 255))
    # 輪郭 (暗い縁)
    out = img.copy()
    for y in range(32):
        for x in range(32):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < 32 and 0 <= yy < 32 and img.getpixel((xx, yy))[3] > 0:
                        out.putpixel((x, y), (40, 46, 60, 255))
                        break
    return out


def main():
    save_png(stone(False), "block/storm_chime_stone.png")
    save_png(stone(True), "block/storm_chime_stone_active.png")
    save_png(copper(), "block/feather_mirror.png")
    save_png(copper(True), "block/feather_mirror_back.png")
    save_png(receiver(False), "block/feather_receiver.png")
    save_png(receiver(True), "block/feather_receiver_lit.png")
    save_png(feather_item(), "item/gale_feather.png")

    write("blockstates/storm_chime_stone.json", {"variants": {
        "active=false": {"model": "skyabyss:block/storm_chime_stone"},
        "active=true": {"model": "skyabyss:block/storm_chime_stone_active"}}})
    write("models/block/storm_chime_stone.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "skyabyss:block/storm_chime_stone"}})
    write("models/block/storm_chime_stone_active.json", {"parent": "minecraft:block/cube_all",
                                                         "textures": {"all": "skyabyss:block/storm_chime_stone_active"}})
    write("blockstates/feather_receiver.json", {"variants": {
        "lit=false": {"model": "skyabyss:block/feather_receiver"},
        "lit=true": {"model": "skyabyss:block/feather_receiver_lit"}}})
    write("models/block/feather_receiver.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "skyabyss:block/feather_receiver"}})
    write("models/block/feather_receiver_lit.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "skyabyss:block/feather_receiver_lit"}})
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
    write("models/item/gale_feather.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/gale_feather"}})
    print("storm back assets written")


if __name__ == "__main__":
    main()
