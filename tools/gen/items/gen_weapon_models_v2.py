"""武器3案 (案B 装飾, 4倍) の彫刻ボクセルを、NeoForge 1.21.1 の item モデルに書き出す。

入力: tools/sources/weapons/<item>/voxels.npz (採用した B 案。元は docs/roughs/v2/weapons/<item>/b) (grid[X,Y,Z,4] RGBA, glow[X,Y,Z], scale)
      ボクセルは立てた向き (刃は +Y)。X は右、Y は上。
出力:
  src/main/resources/assets/skyabyss/models/item/<item>.json   (separate_transforms: base=3D, gui=平面)
  src/main/resources/assets/skyabyss/textures/item/3d/<item>.png (パレット 16x16、1色1画素。行は繰り返して 16 行にする)
  src/main/resources/assets/skyabyss/textures/item/<item>.png    (採用の 16x16 アイコンをコピー)
  docs/review/previews/weapons_<item>.png                              (確認用の描画)

手順:
1. 色 (と光るか) をパレット番号にする。256色を超えるときは色を丸めて減らす。
2. 3D の貪欲法で同じ色の箱にまとめる (x, 次に y, 次に z へ伸ばす)。
3. 箱ごとに、外に面している面だけを書く (隣が埋まっている面は省く)。
4. ボクセル1個 = s 単位。立てた全長を目標の長さにし、中心を (8, 8, 8) に置く。
5. 要素ごとに z 軸まわり -45° (origin 8,8,8) で回し、アイコンと同じ斜めの向きにする。
   立てた状態を 45° 回すと、刃先が右上、柄が左下に来る。

使い方: python tools/gen/items/gen_weapon_models_v2.py
"""
from __future__ import annotations

# ---------------------------------------------------------------------------
# 上書き防止: このスクリプトは採用済みの武器モデル (models/item/*.json) とパレットを書き直す。
# 生成元のボクセルが変わっていなければ同じ物ができるはずだが、手で直した分は消える。
# 古い 16x16 アイコンのコピー (write_icon) は、さらに SKYABYSS_COPY_OLD_ICONS=1 のときだけ行う。
# 実行するときは環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。tools/README.md も参照。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen/items/gen_weapon_models_v2.py は停止中です: 採用済みの武器モデルとパレットを上書きするため。"
              "どうしても作り直すときだけ SKYABYSS_ALLOW_REGEN=1 を付けて実行してください (tools/README.md)。")

import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools" / "rough"))
from voxel import FACE_SHADE, Face, icon_big, load_png, render, sheet  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ICON_SRC = ROOT / "tools/sources/weapons"
VOX = ROOT / "tools/sources/weapons"
CHECK_DIR = ROOT / "docs/review/previews"

# item: 立てた全長 (単位)。手に持つ武器の長さ
ITEMS = {
    "whalebone_greatsword": 22.0,
    "captain_pike": 22.0,
    "waterway_fang": 16.0,
}
TILT_DEG = -45.0          # z 軸まわり。-45 で刃先が右上 (MC の回転の正の向きは右手系 = rotationZ)
CENTRE = 8.0
MAX_PALETTE = 256
PAL_W = 16
PAL_H = 16          # パレット画像の高さ。16 未満にしない (M1)
GLOW_DATA = {"block_light": 15, "sky_light": 15}

# 面の名前 -> (軸, 向き)  (x: east/west, y: up/down, z: south/north)
DIRS = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}


# バニラの item/handheld (+ item/generated) の持ち方
HANDHELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def load_voxels(item: str):
    d = np.load(VOX / item / "voxels.npz")
    grid = d["grid"]
    glow = d["glow"].astype(bool)
    return grid, glow


def palette_keys(grid, glow):
    """各ボクセルに色 (と光るか) のパレット番号 (1..) を振る。0 は空。

    色が 256 を超えるときは、RGB を段階的に丸めて数を減らす。
    """
    solid = grid[..., 3] > 0
    for step in (1, 2, 4, 8, 12, 16, 24, 32):
        rgb = grid[..., :3].astype(np.int64)
        if step > 1:
            rgb = np.clip((rgb // step) * step + step // 2, 0, 255)
        packed = ((rgb[..., 0] << 16) | (rgb[..., 1] << 8) | rgb[..., 2]) * 2 + glow.astype(np.int64)
        uniq = np.unique(packed[solid])
        if len(uniq) <= MAX_PALETTE:
            break
    else:
        raise SystemExit("palette still too large")
    idx = np.searchsorted(uniq, packed)
    keys = np.where(solid, idx + 1, 0).astype(np.int32)
    palette = []
    for u in uniq:
        g = bool(u & 1)
        v = int(u >> 1)
        palette.append(((v >> 16) & 255, (v >> 8) & 255, v & 255, g))
    return keys, palette, step


def greedy_boxes(K):
    """同じ番号の隣り合う区画を、x → y → z の順に伸ばして箱にまとめる。"""
    X, Y, Z = K.shape
    done = np.zeros(K.shape, bool)
    boxes = []
    for x, y, z in zip(*np.nonzero(K)):
        if done[x, y, z]:
            continue
        k = K[x, y, z]
        x1 = x
        while x1 + 1 < X and K[x1 + 1, y, z] == k and not done[x1 + 1, y, z]:
            x1 += 1
        y1 = y
        while y1 + 1 < Y and np.all(K[x:x1 + 1, y1 + 1, z] == k) and not np.any(done[x:x1 + 1, y1 + 1, z]):
            y1 += 1
        z1 = z
        while z1 + 1 < Z and np.all(K[x:x1 + 1, y:y1 + 1, z1 + 1] == k) and not np.any(done[x:x1 + 1, y:y1 + 1, z1 + 1]):
            z1 += 1
        done[x:x1 + 1, y:y1 + 1, z:z1 + 1] = True
        boxes.append((int(x), int(y), int(z), int(x1), int(y1), int(z1), int(k)))
    return boxes


def face_covered(solid, box, name):
    """面の外側一層が、面全体で埋まっているか (埋まっていれば内側の面なので描かない)。"""
    axis, sgn = DIRS[name]
    lo = box[:3]
    hi = box[3:6]
    idx = []
    for a in range(3):
        if a == axis:
            i = hi[a] + 1 if sgn > 0 else lo[a] - 1
            if i < 0 or i >= solid.shape[a]:
                return False
            idx.append(slice(i, i + 1))
        else:
            idx.append(slice(lo[a], hi[a] + 1))
    return bool(solid[tuple(idx)].all())


def pixel_uv(i):
    """パレット番号 i (0 始まり) の画素の uv。texture_size = [16, 16] の画素単位。画素の中心寄りを使う。"""
    col, row = i % PAL_W, i // PAL_W
    return [col + 0.25, row + 0.25, col + 0.75, row + 0.75]


def build_model(item: str, target_len: float):
    grid, glow = load_voxels(item)
    solid = grid[..., 3] > 0
    keys, palette, step = palette_keys(grid, glow)
    xs, ys, zs = np.nonzero(solid)
    lo = np.array([xs.min(), ys.min(), zs.min()])
    hi = np.array([xs.max(), ys.max(), zs.max()])
    length_cells = hi[1] - lo[1] + 1           # 立てたときの縦の長さ (刃の方向)
    s = target_len / length_cells              # 1ボクセル = s 単位
    centre = (lo + hi + 1) / 2.0               # ボクセル座標での中心 (格子の角)

    def mp(v, a):
        return round(CENTRE + (v - centre[a]) * s, 4)

    boxes = greedy_boxes(keys)
    H = math.ceil(len(palette) / PAL_W)
    elements = []
    glow_faces = 0
    for box in boxes:
        x0, y0, z0, x1, y1, z1, k = box
        fr = [mp(x0, 0), mp(y0, 1), mp(z0, 2)]
        to = [mp(x1 + 1, 0), mp(y1 + 1, 1), mp(z1 + 1, 2)]
        r, g, b, is_glow = palette[k - 1]
        uv = pixel_uv(k - 1)
        faces = {}
        for name in DIRS:
            if face_covered(solid, box, name):
                continue
            f = {"uv": uv, "texture": "#pal"}
            if is_glow:
                f["neoforge_data"] = GLOW_DATA
                glow_faces += 1
            faces[name] = f
        if not faces:
            continue
        elements.append({
            "from": fr,
            "to": to,
            "rotation": {"angle": TILT_DEG, "axis": "z", "origin": [8, 8, 8]},
            "faces": faces,
        })
    model = {
        "loader": "neoforge:separate_transforms",
        "base": {
            # 親に item/handheld を指定すると、その先の item/generated が「アイコンから平らなモデルを作る」
            # 特別な扱いになり、ここで並べた要素が無視されて何も描かれない。持ち方は handheld と同じ値を直接書く
            "gui_light": "front",
            "display": HANDHELD_DISPLAY,
            "textures": {"pal": f"skyabyss:item/3d/{item}", "particle": f"skyabyss:item/{item}"},
            "texture_size": [PAL_W, PAL_H],
            "elements": elements,
        },
        "perspectives": {
            "gui": {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"skyabyss:item/{item}"},
            },
        },
    }
    stats = {
        "boxes": len(elements),
        "faces": sum(len(e["faces"]) for e in elements),
        "glow_faces": glow_faces,
        "palette": len(palette),
        "palette_step": step,
        "length_cells": int(length_cells),
        "voxel_size": round(s, 4),
    }
    return model, palette, H, stats


def write_palette(item, palette, H):
    # 高さ 1px の画像は block atlas 全体のミップマップを 0 に落とす。
    # 常に 16x16 で書く。モデルの uv は 16 単位 = 画像全体なので、16x16 なら uv の 1 単位がちょうど 1 画素になる。
    # 使わない行は、パレットの行を繰り返して埋める (H=1 なら 1 行目を 16 回)。
    assert H <= PAL_H, (item, H)
    pal = np.zeros((H, PAL_W, 4), np.uint8)
    for i, (r, g, b, _) in enumerate(palette):
        pal[i // PAL_W, i % PAL_W] = (r, g, b, 255)
    arr = pal[np.arange(PAL_H) % H]
    out = ASSETS / "textures/item/3d" / f"{item}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(out)


def write_icon(item):
    src = ICON_SRC / item / "old_icon.png"
    dst = ASSETS / "textures/item" / f"{item}.png"
    shutil.copyfile(src, dst)


# ---------------------------------------------------------------- 確認用の読み込み (JSON から面を作る)

def _rot_z(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])


def faces_from_model(model, palette_img):
    """書き出した JSON を読み直して、描く面の一覧にする (JSON どおりに描くための確認)。"""
    out = []
    R_cache = {}
    for el in model["base"]["elements"]:
        fr = np.array(el["from"], float)
        to = np.array(el["to"], float)
        rot = el.get("rotation")
        if rot:
            R = R_cache.setdefault(rot["angle"], _rot_z(rot["angle"]))
            org = np.array(rot["origin"], float)
        for name, f in el["faces"].items():
            ax, sgn = DIRS[name]
            # 面の一隅と2辺 (回す前)
            lo = fr.copy()
            hi = to.copy()
            plane = hi[ax] if sgn > 0 else lo[ax]
            o = lo.copy()
            o[ax] = plane
            eu = np.zeros(3)
            ev = np.zeros(3)
            others = [a for a in range(3) if a != ax]
            eu[others[0]] = hi[others[0]] - lo[others[0]]
            ev[others[1]] = hi[others[1]] - lo[others[1]]
            if rot:
                o = R @ (o - org) + org
                eu = R @ eu
                ev = R @ ev
            u0, v0, u1, v1 = f["uv"]
            px = int((u0 + u1) / 2)
            py = int((v0 + v1) / 2)
            rgba = palette_img[py, px].reshape(1, 1, 4)
            glow = None
            if "neoforge_data" in f:
                glow = np.ones((1, 1), bool)
            out.append(Face(o, eu, ev, rgba, glow, FACE_SHADE[name]))
    return out


def main():
    CHECK_DIR.mkdir(parents=True, exist_ok=True)
    results = {}
    for item, target in ITEMS.items():
        model, palette, H, stats = build_model(item, target)
        write_palette(item, palette, H)
        # 採用のアイコンは 32x32 (tools/rough/items_blocks_32.py)。古い 16x16 アイコンでの上書きは、明示したときだけ
        if _os.environ.get("SKYABYSS_COPY_OLD_ICONS") == "1":
            write_icon(item)
        # 範囲の確認
        for el in model["base"]["elements"]:
            for v in el["from"] + el["to"]:
                assert -16 <= v <= 32, (item, v)
        out = ASSETS / "models/item" / f"{item}.json"
        out.write_text(json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        json.loads(out.read_text(encoding="utf-8"))  # 形式の確認
        results[item] = stats
        print(item, stats)

        # 確認画像: JSON を読み直して描く
        pal_img = np.array(Image.open(ASSETS / "textures/item/3d" / f"{item}.png").convert("RGBA"))
        faces = faces_from_model(model, pal_img)
        icon = load_png(ASSETS / "textures/item" / f"{item}.png")
        front = render(faces, yaw=0, pitch=0, size=(520, 520), margin=0.04, outline_jump=0.6)
        hand = render(faces, yaw=35, pitch=20, size=(520, 520), margin=0.04, outline_jump=0.6)
        side = render(faces, yaw=90, pitch=0, size=(520, 520), margin=0.04, outline_jump=0.6)
        cols = [
            ("アイコン (採用)", [icon_big(icon, 12)]),
            ("正面 (GUI と同じ向き)", [front]),
            ("斜め (手に持つ向き)", [hand]),
            ("横 (刃の厚み)", [side]),
        ]
        sheet(f"{item} 3D 版 (weapons B)", cols,
              f"要素 {stats['boxes']} 個 / 面 {stats['faces']} / パレット {stats['palette']} 色 / 45° 回転済み").save(
            CHECK_DIR / f"weapons_{item}.png")
    print(json.dumps(results, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
