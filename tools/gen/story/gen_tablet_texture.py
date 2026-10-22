"""碑 (skyabyss:tablet) のテクスチャ 2 枚 (32x32) と、ブロック状態・モデルを作る。

  tablet_front.png : 文の面。青みの暗い石に、縁の彫りと、かすかに光る青緑の刻み (文字ではなく、読めない古い印)
  tablet_side.png  : 側面・裏・上。刻みのない石
  blockstates/tablet.json、models/block/tablet_wall.json (壁掛け)・tablet_standing.json (立碑)

使い方: python tools/gen/story/gen_tablet_texture.py
出力先: src/main/resources/assets/skyabyss/
文字そのものは描かない (文は字幕で見せる)。刻みは横に 4 段、縦の線と短い横線を組み合わせた模様。
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss"
ROOT = ASSETS / "textures/block"
S = 32
DARK = np.array([38, 46, 58], float)
LIGHT = np.array([88, 102, 116], float)
GLOW = np.array([127, 214, 208], float)


def stone(seed):
    r = np.random.default_rng(seed)
    n = r.random((S, S)) * 0.5
    big = np.asarray(Image.fromarray((r.random((6, 6)) * 255).astype(np.uint8)).resize((S, S), Image.BICUBIC), float) / 255.0
    t = np.clip(n * 0.5 + big * 0.6, 0, 1)
    return DARK + (LIGHT - DARK) * t[..., None]


def front():
    img = stone(7)
    # 縁の彫り (外側が暗く、内側の線がやや明るい)
    img[0:2, :] *= 0.7
    img[-2:, :] *= 0.7
    img[:, 0:2] *= 0.7
    img[:, -2:] *= 0.7
    for a in (3, S - 4):
        img[a, 3:S - 3] = img[a, 3:S - 3] * 0.6 + LIGHT * 0.4
        img[3:S - 3, a] = img[3:S - 3, a] * 0.6 + LIGHT * 0.4
    r = np.random.default_rng(11)
    # 刻み: 4 段。各段に縦の線と短い横線を並べる
    for y0 in (6, 12, 18, 24):
        x = 7
        while x < S - 9:
            kind = r.integers(0, 3)
            if kind == 0:
                for y in range(y0, y0 + 4):
                    img[y, x] = GLOW
                x += 3
            elif kind == 1:
                for dx in range(0, 3):
                    img[y0 + 1, x + dx] = GLOW
                img[y0 + 2, x + 1] = GLOW
                x += 5
            else:
                for dy in range(0, 4):
                    img[y0 + dy, x + (dy % 2)] = GLOW
                x += 4
    # 光のにじみ (刻みの隣を少し明るく)
    glow = (img[..., 0] > 120) & (img[..., 1] > 200)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sh = np.roll(np.roll(glow, dy, 0), dx, 1) & ~glow
        img[sh] = img[sh] * 0.6 + GLOW * 0.4
    return img


def save(name, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(ROOT / name)


def blockstate():
    variants = {}
    for standing in ("false", "true"):
        model = "skyabyss:block/tablet_standing" if standing == "true" else "skyabyss:block/tablet_wall"
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            entry = {"model": model}
            if y:
                entry["y"] = y
            variants[f"facing={facing},standing={standing}"] = entry
    (ASSETS / "blockstates/tablet.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")


def models():
    tex = {"particle": "skyabyss:block/tablet_side", "front": "skyabyss:block/tablet_front", "side": "skyabyss:block/tablet_side"}
    wall = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": [{
        "from": [1, 2, 13], "to": [15, 15, 16],
        "faces": {
            "north": {"uv": [1, 1, 15, 14], "texture": "#front"},
            "south": {"uv": [1, 1, 15, 14], "texture": "#side", "cullface": "south"},
            "east": {"uv": [13, 1, 16, 14], "texture": "#side"},
            "west": {"uv": [13, 1, 16, 14], "texture": "#side"},
            "up": {"uv": [1, 13, 15, 16], "texture": "#side"},
            "down": {"uv": [1, 13, 15, 16], "texture": "#side"}}}]}
    standing = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": [
        {"from": [2, 0, 5], "to": [14, 2, 11], "faces": {
            "north": {"uv": [2, 14, 14, 16], "texture": "#side"},
            "south": {"uv": [2, 14, 14, 16], "texture": "#side"},
            "east": {"uv": [5, 14, 11, 16], "texture": "#side"},
            "west": {"uv": [5, 14, 11, 16], "texture": "#side"},
            "up": {"uv": [2, 5, 14, 11], "texture": "#side"},
            "down": {"uv": [2, 5, 14, 11], "texture": "#side", "cullface": "down"}}},
        {"from": [3, 2, 6], "to": [13, 15, 10], "faces": {
            "north": {"uv": [3, 1, 13, 14], "texture": "#front"},
            "south": {"uv": [3, 1, 13, 14], "texture": "#front"},
            "east": {"uv": [6, 1, 10, 14], "texture": "#side"},
            "west": {"uv": [6, 1, 10, 14], "texture": "#side"},
            "up": {"uv": [3, 6, 13, 10], "texture": "#side"}}}]}
    for name, m in (("tablet_wall", wall), ("tablet_standing", standing)):
        (ASSETS / f"models/block/{name}.json").write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")


def lang():
    for code, name in (("ja_jp", "碑"), ("en_us", "Inscribed Tablet")):
        p = ASSETS / f"lang/{code}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        d["block.skyabyss.tablet"] = name
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    save("tablet_front.png", front())
    save("tablet_side.png", stone(23))
    blockstate()
    models()
    lang()
    print("wrote tablet textures, blockstate, models, lang")
