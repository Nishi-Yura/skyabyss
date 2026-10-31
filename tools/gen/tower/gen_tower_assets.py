# -*- coding: utf-8 -*-
"""あいだの塔の報酬の平らな絵 (docs/design/54 の 2.6。32x32)。塔の武器の絵とメッシュは o_tower.py → install_job_weapons.py、塔の箱は o_boxes.py → install_boxes.py。

  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/tower/gen_tower_assets.py            見せる絵だけ (docs/review/tower_rewards/)
  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/tower/gen_tower_assets.py --install  assets へ入れる (承認のあと)

  tower_shard   塔の欠片: 縦に長い欠片が斜めのぎざぎざで割れ、左は白と金 (天)、右は黒い石 (淵) に紫の割れ目。割れ目のあいだに朝の光が一筋
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
REVIEW = ROOT / "docs/review/tower_rewards"
OUTLINE = (30, 24, 36, 255)


def put(px, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        px[x, y] = c


def outline(im):
    px = im.load()
    src = im.copy().load()
    for y in range(32):
        for x in range(32):
            if src[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and src[x + dx, y + dy][3] > 0
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE


def shard():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    pts = [(15, 2), (22, 9), (23, 20), (17, 29), (10, 25), (8, 13)]

    def inside(x, y):
        c = False
        for i in range(len(pts)):
            (x0, y0), (x1, y1) = pts[i], pts[i - 1]
            if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
                c = not c
        return c

    # 割れ目の線 (上から下へ、ぎざぎざに): その y での x
    def seam(y):
        return 15.5 + 1.6 * math.sin(y * 0.9) + (y - 16) * 0.12

    for y in range(32):
        for x in range(32):
            if not inside(x + 0.5, y + 0.5):
                continue
            s = seam(y)
            facet = (x - 16) * 0.5 - (y - 16) * 0.45
            if x + 0.5 < s - 0.6:
                col = (250, 244, 228) if facet < -3 else (232, 222, 196) if facet < 0 else (214, 184, 118)
            elif x + 0.5 > s + 0.6:
                col = (78, 74, 96) if facet < 1 else (48, 44, 62) if facet < 4 else (30, 28, 40)
            else:
                col = (255, 236, 176)
            put(px, x, y, col + (255,))
    # 淵の側の紫の割れ目
    for x, y in ((19, 11), (20, 12), (20, 13), (21, 14), (19, 18), (18, 19), (19, 20), (20, 21), (18, 24)):
        put(px, x, y, (190, 140, 255, 255))
    # 天の側の金の筋と光
    for x, y in ((11, 14), (12, 15), (12, 16), (13, 17)):
        put(px, x, y, (226, 186, 104, 255))
    for x, y in ((13, 5), (12, 7), (12, 8)):
        put(px, x, y, (255, 255, 255, 255))
    outline(im)
    return im


def main():
    install = "--install" in sys.argv
    REVIEW.mkdir(parents=True, exist_ok=True)
    for name, fn in (("tower_shard", shard),):
        im = fn()
        im.save(REVIEW / f"{name}.png")
        im.resize((256, 256), Image.NEAREST).save(REVIEW / f"{name}_x8.png")
        if install:
            im.save(A / "textures/item" / f"{name}.png")
            (A / "models/item" / f"{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}},
                                                                      indent=2), encoding="utf-8")
    print("ok")


if __name__ == "__main__":
    main()
