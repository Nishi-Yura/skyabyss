"""武器 5 種の 3D モデルを作る (neoforge:separate_transforms)。

使い方: python tools/gen_weapon_models.py

既存の 32x32 アイコン (textures/item/*.png) の形をそのまま押し出して 3D にする。
- 色が同じ画素の横長の連なりを、欠けなく重ならないよう長方形にまとめる (greedy meshing)
- 各長方形を、アイコンの同じ画素領域を指す UV をもつ直方体にする。だから 3D 表示の色は 2D アイコンと完全に一致する
- 奥行きは武器ごとに決める (大剣は厚く、牙は薄い)
- 持った時の向き・拡大率は、ふつうの剣 (item/handheld) と同じ基準に、武器ごとの倍率を掛けて決める
- GUI (インベントリ) だけは従来どおり平らな generated モデルのまま

出力 (src/main/resources/assets/skyabyss/models/item/):
  <name>.json  : neoforge:separate_transforms。base は 3D 直方体モデル、perspectives.gui は平らなモデル
NeoForge の separate_transforms は base / perspectives の各値を BlockModel のインライン JSON として読むため、
参照ではなくインラインで書く。

単位は model 座標 (16 = 1 ブロック)。アイコン 1px = 0.5 単位。
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
TEX_DIR = ROOT / "textures/item"
MODEL_DIR = ROOT / "models/item"
NS = "skyabyss:item/"
ICON_SIZE = 32
PX = 16 / ICON_SIZE  # アイコン 1px のモデル上の大きさ
UV_INSET = 0.25      # UV の縁から縮める量 (texel 単位)。隣の画素の色が混ざらないようにする

# 名前: (奥行き (単位), 種別, 拡大率 {tp, fp, ground, head, fixed})
# 種別 "hand": ふつうの剣と同じ持ち方 (item/handheld 基準)
# 種別 "gen" : 平らな generated と同じ持ち方 (護符)
WEAPONS = {
    # 大剣: 重い。手に持つと一回り大きく見せる
    "whalebone_greatsword": (3.0, "hand", {"tp": 1.4, "fp": 0.95, "ground": 0.8, "head": 1.0, "fixed": 0.7}),
    # 鉾: 長柄。大剣に次ぐ大きさ
    "captain_pike": (2.0, "hand", {"tp": 1.35, "fp": 0.92, "ground": 0.75, "head": 1.0, "fixed": 0.7}),
    # 牙の短剣: 軽く、薄い
    "waterway_fang": (1.5, "hand", {"tp": 0.95, "fp": 0.68, "ground": 0.5, "head": 0.9, "fixed": 0.5}),
    # 骨棘の矢
    "bone_spike_arrow": (1.0, "hand", {"tp": 0.85, "fp": 0.68, "ground": 0.5, "head": 0.8, "fixed": 0.5}),
    # 共鳴の護符: 丸い飾り
    "resonance_charm": (2.5, "gen", {"tp": 0.55, "fp": 0.68, "ground": 0.5, "head": 0.8, "fixed": 0.5}),
}


def greedy_rects(img):
    """不透明な画素を、同じ色の長方形の集まりにする。返り値: [(x, y, w, h, rgba)]"""
    px = img.load()
    w, h = img.size
    done = [[False] * w for _ in range(h)]
    rects = []
    for y in range(h):
        for x in range(w):
            if done[y][x]:
                continue
            c = px[x, y]
            if c[3] < 128:
                done[y][x] = True
                continue
            rw = 1
            while x + rw < w and not done[y][x + rw] and px[x + rw, y] == c:
                rw += 1
            rh = 1
            while y + rh < h:
                ok = all(not done[y + rh][x + i] and px[x + i, y + rh] == c for i in range(rw))
                if not ok:
                    break
                rh += 1
            for yy in range(y, y + rh):
                for xx in range(x, x + rw):
                    done[yy][xx] = True
            rects.append((x, y, rw, rh, c))
    return rects


def r3(v):
    v = round(v, 3)
    return int(v) if v == int(v) else v


def element(x, y, w, h, depth, tex_region):
    """アイコン上の (x,y,w,h) 画素を、奥行き depth の直方体にする。"""
    x0, y0 = x * PX, 16 - (y + h) * PX
    x1, y1 = (x + w) * PX, 16 - y * PX
    z0, z1 = 8 - depth / 2, 8 + depth / 2
    u0 = (x + UV_INSET) / 2
    v0 = (y + UV_INSET) / 2
    u1 = (x + w - UV_INSET) / 2
    v1 = (y + h - UV_INSET) / 2
    uv = [r3(u0), r3(v0), r3(u1), r3(v1)]
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        faces[f] = {"uv": uv, "texture": "#tex"}
    return {
        "from": [r3(x0), r3(y0), r3(z0)],
        "to": [r3(x1), r3(y1), r3(z1)],
        "faces": faces,
    }


def display(kind, s):
    if kind == "hand":
        return {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [s["tp"]] * 3},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [s["tp"]] * 3},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [s["fp"]] * 3},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [-1.13, 3.2, 1.13], "scale": [s["fp"]] * 3},
            "head": {"translation": [0, 13, 7], "scale": [s["head"]] * 3},
            "ground": {"translation": [0, 2, 0], "scale": [s["ground"]] * 3},
            "fixed": {"rotation": [0, 180, 0], "scale": [s["fixed"]] * 3},
        }
    return {
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [s["tp"]] * 3},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [s["tp"]] * 3},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [s["fp"]] * 3},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [-1.13, 3.2, 1.13], "scale": [s["fp"]] * 3},
        "head": {"translation": [0, 13, 7], "scale": [s["head"]] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [s["ground"]] * 3},
        "fixed": {"rotation": [0, 180, 0], "scale": [s["fixed"]] * 3},
    }


def build(name, depth, kind, scales):
    tex = NS + name
    img = Image.open(TEX_DIR / f"{name}.png").convert("RGBA")
    assert img.size == (ICON_SIZE, ICON_SIZE), img.size
    elements = [element(x, y, w, h, depth, None) for x, y, w, h, _ in greedy_rects(img)]
    base = {
        "textures": {"tex": tex},
        "elements": elements,
        "display": display(kind, scales),
        "ambientocclusion": False,
    }
    gui = {"parent": "minecraft:item/generated", "textures": {"layer0": tex}}
    return {
        "loader": "neoforge:separate_transforms",
        "base": base,
        "perspectives": {"gui": gui},
    }, len(elements)


def main():
    for name, (depth, kind, scales) in WEAPONS.items():
        model, n = build(name, depth, kind, scales)
        out = MODEL_DIR / f"{name}.json"
        out.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {n} elements, depth {depth} -> {out}")


if __name__ == "__main__":
    main()
