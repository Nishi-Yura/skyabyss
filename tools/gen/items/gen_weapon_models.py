"""武器 5 種の 3D モデルを作る (neoforge:separate_transforms)。

使い方: python tools/gen/items/gen_weapon_models.py [名前 ...]   (省略時は全部)

既存のアイコン (textures/item/*.png) の形をそのまま押し出して 3D にする。
アイコンの大きさは武器ごとに違う (WEAPONS の 4 つ目の値)。
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

# ---------------------------------------------------------------------------
# 上書き防止: 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: 武器 3 本の models/item/*.json (古い押し出しモデル)。途中で止まり半端な状態が残る
#   採用版の生成元: tools/gen/items/gen_weapon_models_v2.py (これも SKYABYSS_ALLOW_REGEN=1 が必要)
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen/items/gen_weapon_models.py は停止中です: 採用済みの素材 (武器 3 本の models/item/*.json (古い押し出しモデル)。途中で止まり半端な状態が残る) を上書きするため。"
              "採用版の生成元は tools/gen/items/gen_weapon_models_v2.py (これも SKYABYSS_ALLOW_REGEN=1 が必要) です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss"
TEX_DIR = ROOT / "textures/item"
MODEL_DIR = ROOT / "models/item"
NS = "skyabyss:item/"
UV_INSET = 0.25      # UV の縁から縮める量 (texel 単位)。隣の画素の色が混ざらないようにする

# 名前: (奥行き (単位), 種別, 拡大率 {tp, fp, ground, head, fixed}, アイコンの一辺 (px))
# 16px のアイコンは 1px = 1 単位 (モデル全体で 16 単位 = 1 ブロック)。32px は 1px = 0.5 単位。
# 種別 "hand": ふつうの剣と同じ持ち方 (item/handheld 基準)
# 種別 "gen" : 平らな generated と同じ持ち方 (護符)
WEAPONS = {
    # 大剣: 重い。手に持つと一回り大きく見せる
    "whalebone_greatsword": (3.0, "hand", {"tp": 1.4, "fp": 0.95, "ground": 0.8, "head": 1.0, "fixed": 0.7}, 32),
    # 鉾: 長柄。大剣に次ぐ大きさ
    "captain_pike": (2.0, "hand", {"tp": 1.35, "fp": 0.92, "ground": 0.75, "head": 1.0, "fixed": 0.7}, 32),
    # 牙の短剣: 軽く、薄い
    "waterway_fang": (1.5, "hand", {"tp": 0.95, "fp": 0.68, "ground": 0.5, "head": 0.9, "fixed": 0.5}, 32),
    # 骨棘の矢: 16px の平らなアイコンを 2 単位の厚さ (z 7〜9) に押し出す
    "bone_spike_arrow": (2.0, "hand", {"tp": 0.85, "fp": 0.68, "ground": 0.5, "head": 0.8, "fixed": 0.5}, 16),
    # 共鳴の護符: 丸い飾り
    "resonance_charm": (2.5, "gen", {"tp": 0.55, "fp": 0.68, "ground": 0.5, "head": 0.8, "fixed": 0.5}, 32),
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


def element(x, y, w, h, depth, size):
    """アイコン (size x size px) 上の (x,y,w,h) 画素を、奥行き depth の直方体にする。"""
    px = 16 / size  # アイコン 1px のモデル上の大きさ
    x0, y0 = x * px, 16 - (y + h) * px
    x1, y1 = (x + w) * px, 16 - y * px
    z0, z1 = 8 - depth / 2, 8 + depth / 2
    u0 = (x + UV_INSET) / (size / 16)
    v0 = (y + UV_INSET) / (size / 16)
    u1 = (x + w - UV_INSET) / (size / 16)
    v1 = (y + h - UV_INSET) / (size / 16)
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


def build(name, depth, kind, scales, size):
    tex = NS + name
    img = Image.open(TEX_DIR / f"{name}.png").convert("RGBA")
    assert img.size == (size, size), img.size
    elements = [element(x, y, w, h, depth, size) for x, y, w, h, _ in greedy_rects(img)]
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
    names = sys.argv[1:] or list(WEAPONS)
    for name in names:
        depth, kind, scales, size = WEAPONS[name]
        model, n = build(name, depth, kind, scales, size)
        out = MODEL_DIR / f"{name}.json"
        out.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {n} elements, depth {depth} -> {out}")


if __name__ == "__main__":
    main()
