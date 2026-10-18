"""二章「引く海」(潮殻。docs/design/08 の 2.8節) の素材を作る: 小要素の部品 4 つ、潮見の貝のアイテム絵と模型、sounds.json。

使い方: python tools/gen_ch2_assets.py
出力:
  src/main/resources/data/skyabyss/opening/tide_cairn_a.json, tide_cairn_b.json, tide_cargo.json, tide_stele.json  (部品。座標は構造物の中心が (0,0)、y=0 が最初の段、地面の上面は y=-1)
  src/main/resources/assets/skyabyss/textures/item/tide_watching_shell.png (32x32)
  src/main/resources/assets/skyabyss/models/item/tide_watching_shell.json
  src/main/resources/assets/skyabyss_ch2/sounds.json (音の本体は tools/gen_ch2_sounds.py)
乱数は使わない (何度実行しても同じ)。
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"


def write_json(path, obj, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(obj, ensure_ascii=False, separators=(",", ":")) if compact else json.dumps(obj, ensure_ascii=False, indent=2)
    path.write_text(text + "\n", encoding="utf-8")


# ---------------------------------------------------------------- 部品
def blueprint(name, palette, blocks, marks, rect, note, level=False, wet_ok=False, ring=2):
    meta = {"rect": rect, "level": level}
    if wet_ok:
        meta["wetOk"] = True
    meta["ring"] = ring
    obj = {"site": name, "note": note, "palette": palette, "blocks": blocks, "marks": marks, "meta": meta}
    write_json(RES / "data/skyabyss/opening" / (name + ".json"), obj, compact=True)


def cairn(name, height, note):
    """塩の積み石。足もとに塩の白い輪、岸の側へ細い足跡 (泥) が数歩。height 段の積み石 (高い = 朝の潮、低い = 夕の潮)"""
    palette = [
        "minecraft:stone",                              # 0
        "minecraft:calcite",                            # 1 塩の白
        "minecraft:diorite",                            # 2
        "minecraft:smooth_stone_slab[type=bottom]",     # 3
        "minecraft:mud",                                # 4 足跡
        "minecraft:packed_mud",                         # 5
        "minecraft:cobblestone",                        # 6
    ]
    blocks = []
    # 塩の輪 (地面を置き換える)
    for x, z in [(-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)]:
        blocks.append([x, -1, z, 1])
    blocks.append([0, -1, 0, 1])
    # 積み石 (下の段ほど大きい)
    blocks += [[0, 0, 0, 0], [1, 0, 0, 2], [0, 0, 1, 6]]
    for y in range(1, height):
        blocks.append([0, y, 0, 1 if y % 2 else 0])
    blocks.append([0, height, 0, 3])
    # 足跡: +x の向きへ 7 歩 (左右交互に半歩ずらす)。泥で、ところどころ固い泥
    for k in range(7):
        x = 3 + k
        z = 1 if k % 2 == 0 else -1
        blocks.append([x, -1, z, 4 if k % 3 else 5])
    blocks.sort(key=lambda b: (b[1], b[0], b[2]))
    blueprint(name, palette, blocks, {"top": [0, height, 0]}, [-2, -2, 10, 2], note, level=False, wet_ok=True)


def cargo():
    """漂着の荷: 壊れた木箱 (チェスト)、流木、乾いた昆布の束、折れた柵"""
    palette = [
        "minecraft:chest[facing=south]",                # 0
        "minecraft:oak_planks",                         # 1
        "minecraft:stripped_oak_log[axis=x]",           # 2 流木
        "minecraft:dried_kelp_block",                   # 3
        "minecraft:oak_fence",                          # 4
        "minecraft:barrel[facing=up]",                  # 5
        "minecraft:oak_slab[type=bottom]",              # 6
        "minecraft:stripped_oak_log[axis=z]",           # 7
    ]
    blocks = [
        [0, 0, 0, 0],
        [-1, 0, 0, 1], [1, 0, 0, 6],
        [0, 0, -1, 1],
        [-2, 0, 1, 2], [-1, 0, 1, 2],
        [2, 0, -1, 3], [2, 0, 0, 3],
        [-2, 0, -1, 4],
        [1, 0, 1, 5],
        [3, 0, 1, 7],
    ]
    blocks.sort(key=lambda b: (b[1], b[0], b[2]))
    blueprint("tide_cargo", palette, blocks, {"chest": [0, 0, 0]}, [-3, -2, 4, 2],
              "二章の小要素: 漂着の荷。箱と流木。着くと前の私の頁と潮見の貝", level=True, ring=2)


def stele():
    """浜の碑: 潮の民の碑。低い台に立つ細い石の柱"""
    palette = [
        "minecraft:chiseled_stone_bricks",              # 0
        "minecraft:stone_bricks",                       # 1
        "minecraft:mossy_stone_bricks",                 # 2
        "minecraft:stone_brick_slab[type=bottom]",      # 3
        "minecraft:cracked_stone_bricks",               # 4
        "minecraft:stone_brick_stairs[facing=north,half=bottom]",  # 5
        "minecraft:stone_brick_stairs[facing=south,half=bottom]",  # 6
    ]
    blocks = [
        [-1, 0, -1, 1], [0, 0, -1, 5], [1, 0, -1, 1],
        [-1, 0, 0, 3], [0, 0, 0, 0], [1, 0, 0, 3],
        [-1, 0, 1, 1], [0, 0, 1, 6], [1, 0, 1, 4],
        [0, 1, 0, 1], [0, 2, 0, 2], [0, 3, 0, 1], [0, 4, 0, 3],
    ]
    blocks.sort(key=lambda b: (b[1], b[0], b[2]))
    blueprint("tide_stele", palette, blocks, {"top": [0, 4, 0]}, [-2, -2, 2, 2],
              "二章の小要素: 浜の碑。着くと潮の民の言葉が書に記される", level=True, ring=2)


# ---------------------------------------------------------------- 潮見の貝 (32x32)
def shell_texture():
    S = 32
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    outline = (74, 50, 60, 255)
    # 巻き貝: 右上の尖った先から左下の口へ広がる
    body = [
        (22, 4), (25, 6), (27, 10), (27, 15), (25, 20), (21, 25), (15, 28), (9, 28), (5, 25), (4, 21), (6, 17), (10, 15), (13, 12), (16, 8), (19, 5),
    ]
    d.polygon(body, fill=(233, 214, 190, 255), outline=outline)
    # 巻きの帯 (淡い桃と砂の色を交互に)
    bands = [
        [(21, 7), (25, 9), (26, 14)],
        [(18, 10), (23, 13), (24, 18)],
        [(14, 14), (20, 17), (21, 22)],
        [(10, 19), (16, 22), (16, 26)],
    ]
    for i, band in enumerate(bands):
        d.line(band, fill=(214, 168, 150, 255) if i % 2 == 0 else (190, 150, 130, 255), width=2)
    # 口 (桃色の内側) と縁
    d.ellipse((5, 19, 13, 27), fill=(233, 150, 150, 255), outline=outline)
    d.ellipse((7, 21, 11, 25), fill=(196, 96, 112, 255))
    # 光る縁 (潮の青緑の糸を巻いてある)
    d.line([(8, 17), (13, 14), (17, 9), (21, 5)], fill=(88, 190, 190, 255), width=1)
    d.point([(21, 5), (22, 4)], fill=(255, 255, 255, 255))
    # ハイライト
    d.line([(23, 9), (24, 12)], fill=(255, 246, 232, 255), width=1)
    d.line([(17, 13), (19, 15)], fill=(255, 246, 232, 255), width=1)
    path = RES / "assets/skyabyss/textures/item/tide_watching_shell.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    write_json(RES / "assets/skyabyss/models/item/tide_watching_shell.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/tide_watching_shell"}})


# ---------------------------------------------------------------- sounds.json
def sounds_json():
    def ev(name, stream=False):
        return {"sounds": [{"name": "skyabyss_ch2:" + name, "stream": stream}], "subtitle": "subtitles.skyabyss_ch2." + name}
    write_json(RES / "assets/skyabyss_ch2/sounds.json", {
        "conch_far": ev("conch_far"),
        "ebb_recede": ev("ebb_recede", True),
        "ebb_return": ev("ebb_return", True),
    })


if __name__ == "__main__":
    cairn("tide_cairn_a", 5, "二章の小要素: 塩の積み石 (高い。朝の潮)")
    cairn("tide_cairn_b", 3, "二章の小要素: 塩の積み石 (低い。夕の潮)")
    cargo()
    stele()
    shell_texture()
    sounds_json()
    print("done")
