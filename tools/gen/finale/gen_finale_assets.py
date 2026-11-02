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


def bell_cloud_tex(solid, fade):
    """鐘雲: ふだんは雲より薄く透ける。固まると白く光り、縁に淡い金の筋 (鐘の音の輪)。終わりの 4 秒は少し薄い"""
    img = cloud_tex()
    N = img.size[0]
    for y in range(N):
        for x in range(N):
            r, g, b, a = img.getpixel((x, y))
            if not solid:
                img.putpixel((x, y), (r, g, min(255, b + 6), int(a * 0.62)))
                continue
            ring = abs(math.hypot(x - 15.5, y - 15.5) - 11) < 0.9 or abs(math.hypot(x - 15.5, y - 15.5) - 5.5) < 0.7
            if ring:
                r, g, b = 255, 236, 176
            else:
                r, g, b = min(255, r + 8), min(255, g + 8), min(255, b + 4)
            img.putpixel((x, y), (r, g, b, 190 if fade else 250))
    return img


def light_path_tex():
    """光の橋: 白金の板。縁に金の細い枠、面に斜めの淡い光の筋 (32x32)"""
    N = 32
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            edge = x in (0, N - 1) or y in (0, N - 1)
            inner = x in (1, N - 2) or y in (1, N - 2)
            streak = ((x + y) % 11) in (0, 1)
            v = 0.9 + 0.05 * (hsh(x, y, 9) - 0.5)
            if edge:
                c = (232, 196, 110)
            elif inner:
                c = (255, 238, 196)
            elif streak:
                c = (255, 250, 232)
            else:
                c = (int(250 * v), int(240 * v), int(214 * v))
            img.putpixel((x, y), c + (255,))
    return img


def rewind_tex(phase):
    """戻り石: 石の煉瓦に残り火の筋。0 = 灰の薄い影 (ほとんど透ける)・1 = 残り火の輪郭だけ・2 = 石・3 = 薄い石"""
    N = 32
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            row = y // 8
            mortar = y % 8 == 7 or (x + (8 if row % 2 else 0)) % 16 == 15
            edge = x in (0, N - 1) or y in (0, N - 1)
            vein = abs(((x * 0.6 + y) % 13) - 6.5) < 0.6 and hsh(x, y, 31) < 0.7
            v = 0.42 + 0.1 * hsh(x // 2, y // 2, 30)
            stone = (int(255 * v * 0.95), int(255 * v * 0.9), int(255 * v * 0.88))
            ember = (255, 150, 60)
            if phase == 0:
                c, a = ((70, 64, 60), 40) if not edge else ((110, 90, 80), 70)
            elif phase == 1:
                c, a = (ember, 220) if edge or vein else ((90, 60, 40), 46)
            else:
                c = (60, 52, 48) if mortar else stone
                if vein:
                    c = (230, 120, 50)
                a = 255 if phase == 2 else 150
            img.putpixel((x, y), c + (a,))
    return img


def main():
    (A / "textures/block").mkdir(parents=True, exist_ok=True)
    cloud_tex().save(A / "textures/block/cloud.png")
    (A / "models/block/cloud.json").write_text(json.dumps({
        "parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
        "textures": {"all": "skyabyss:block/cloud", "particle": "skyabyss:block/cloud"}}, indent=2), encoding="utf-8")
    (A / "blockstates/cloud.json").write_text(json.dumps({"variants": {"": {"model": "skyabyss:block/cloud"}}}, indent=2), encoding="utf-8")
    variants = {}
    for solid in (False, True):
        for fade in (False, True):
            name = "bell_cloud" + ("_solid" if solid else "") + ("_fade" if fade and solid else "")
            bell_cloud_tex(solid, fade).save(A / ("textures/block/%s.png" % name))
            (A / ("models/block/%s.json" % name)).write_text(json.dumps({
                "parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                "textures": {"all": "skyabyss:block/" + name, "particle": "skyabyss:block/" + name}}, indent=2), encoding="utf-8")
            variants["fade=%s,solid=%s" % (str(fade).lower(), str(solid).lower())] = {"model": "skyabyss:block/" + name}
    (A / "blockstates/bell_cloud.json").write_text(json.dumps({"variants": variants}, indent=2), encoding="utf-8")
    light_path_tex().save(A / "textures/block/light_path.png")
    tex = {"all": "skyabyss:block/light_path", "particle": "skyabyss:block/light_path"}
    (A / "models/block/light_path.json").write_text(json.dumps({"parent": "minecraft:block/cube_all", "textures": tex}, indent=2), encoding="utf-8")
    (A / "blockstates/light_path.json").write_text(json.dumps({"variants": {"": {"model": "skyabyss:block/light_path"}}}, indent=2), encoding="utf-8")
    st = {"bottom": "skyabyss:block/light_path", "top": "skyabyss:block/light_path", "side": "skyabyss:block/light_path"}
    (A / "models/block/light_path_slab.json").write_text(json.dumps({"parent": "minecraft:block/slab", "textures": st}, indent=2), encoding="utf-8")
    (A / "models/block/light_path_slab_top.json").write_text(json.dumps({"parent": "minecraft:block/slab_top", "textures": st}, indent=2), encoding="utf-8")
    (A / "blockstates/light_path_slab.json").write_text(json.dumps({"variants": {
        "type=bottom": {"model": "skyabyss:block/light_path_slab"}, "type=top": {"model": "skyabyss:block/light_path_slab_top"},
        "type=double": {"model": "skyabyss:block/light_path"}}}, indent=2), encoding="utf-8")
    variants = {}
    for ph in range(4):
        name = "rewind_stone_%d" % ph
        rewind_tex(ph).save(A / ("textures/block/%s.png" % name))
        (A / ("models/block/%s.json" % name)).write_text(json.dumps({
            "parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
            "textures": {"all": "skyabyss:block/" + name, "particle": "skyabyss:block/rewind_stone_2"}}, indent=2), encoding="utf-8")
        variants["phase=%d" % ph] = {"model": "skyabyss:block/" + name}
    (A / "blockstates/rewind_stone.json").write_text(json.dumps({"variants": variants}, indent=2), encoding="utf-8")
    print("cloud assets written")


if __name__ == "__main__":
    main()
