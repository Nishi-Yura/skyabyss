"""潮殻のゲア (三叉槍・珊瑚の盾・潮招きの大鋏・珊瑚憑きの銛) と、護符・真珠・心核を採用案で書き出す。

使い方:
    python tools/install_tide_gear.py                       # 推奨案 (RECOMMEND) で書く
    python tools/install_tide_gear.py --option coral_shield=b   # 案を指定 (複数可)
    python tools/install_tide_gear.py --from-sources        # tools/sources/tide_gear/ から書き直す

入力: docs/review/tide_gear_roughs/<item>/<a|b|c>/ (icon.png, voxels.npz)
      選んだ案を tools/sources/tide_gear/<item>/ にコピーし、そこから書く (再実行できるように)。
出力:
  src/main/resources/assets/skyabyss/models/item/<item>.json
  src/main/resources/assets/skyabyss/models/item/coral_shield_blocking.json  (盾の構え用。minecraft:blocking の上書きで使う)
  src/main/resources/assets/skyabyss/textures/item/<item>.png        (32x32 のアイコン)
  src/main/resources/assets/skyabyss/textures/item/3d/<item>.png     (3D のパレット 16x16)
  docs/review/previews/tide_gear_<item>.png                           (確認用の描画。採用外ではない確認画像)

3D の作り: ボクセルを直方体 (貪欲法) にまとめ、外に見える面だけ書く。武器は -45 度に傾ける
(刃先が右上。アイコンと同じ向き)。盾は傾けず、表が +Z 側、背面 (-Z 側) に持ち手の横棒を付ける。
持ち方は、武器はバニラの handheld と同じ値 (gen_weapon_models_v2)。盾はバニラの shield の値
(この環境に vanilla の jar が無いため、値は覚えによる。実機で確認すること)。
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "rough"))
from gen_weapon_models_v2 import DIRS, GLOW_DATA, HANDHELD_DISPLAY, face_covered, faces_from_model, greedy_boxes, pixel_uv  # noqa: E402
from tide_gear import ITEMS, ROT_DEG, OPTS, preview_3d  # noqa: E402
from voxel import Face, render  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
REVIEW = ROOT / "docs/review/tide_gear_roughs"
SOURCES = ROOT / "tools/sources/tide_gear"
PREVIEW = ROOT / "docs/review/previews"

PAL_W = 16
PAL_H = 16
MAX_ELEMENTS = 1500

# 推奨案 (理由は docs/review/tide_gear_roughs/README.md)
RECOMMEND = {
    "tide_shell_trident": "a",
    "coral_shield": "a",
    "tidecaller_claw": "a",
    "coral_harpoon": "a",
    "tide_chime_charm": "a",
    "moontide_pearl": "a",
    "tide_shell_core": "a",
}

# 盾の持ち方。盾の要素 (x 2.4〜13.6、y -1〜17、表が +Z、取っ手が -Z) はバニラの盾の立体 (組み込みの描画で
# x 2〜14、y -3〜19、表が +Z、取っ手が -Z) とほぼ同じ所にあるので、バニラの item/shield.json・shield_blocking.json の
# 値をそのまま使う (ゲームで見て確かめた。03 の 11章)
SHIELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
# 構え (盾を前に上げた姿勢)。バニラ item/shield_blocking の値
SHIELD_BLOCKING_DISPLAY = {
    "thirdperson_righthand": {"rotation": [45, 135, 0], "translation": [3.51, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
}


def _pack_palette(grid, glow):
    """色 (と光るか) ごとに番号を振る。0 は空。戻り値: (keys, palette[(r,g,b,glow)])。"""
    solid = grid[..., 3] > 0
    rgb = grid[..., :3].astype(np.int64)
    packed = ((rgb[..., 0] << 16) | (rgb[..., 1] << 8) | rgb[..., 2]) * 2 + glow.astype(np.int64)
    uniq = np.unique(packed[solid])
    if len(uniq) > 256:
        raise SystemExit(f"palette {len(uniq)} > 256")
    keys = np.where(solid, np.searchsorted(uniq, packed) + 1, 0).astype(np.int32)
    palette = []
    for u in uniq:
        v = int(u >> 1)
        palette.append(((v >> 16) & 255, (v >> 8) & 255, v & 255, bool(u & 1)))
    return keys, palette


def build_elements(grid, glow, target, tilt):
    """格子を直方体の一覧にする。target は立てたときの縦の長さ (単位)。"""
    solid = grid[..., 3] > 0
    keys, palette = _pack_palette(grid, glow)
    xs, ys, zs = np.nonzero(solid)
    lo = np.array([xs.min(), ys.min(), zs.min()])
    hi = np.array([xs.max(), ys.max(), zs.max()])
    length_cells = hi[1] - lo[1] + 1
    s = target / length_cells
    centre = (lo + hi + 1) / 2.0

    def mp(v, a):
        return round(8.0 + (v - centre[a]) * s, 4)

    elements = []
    for x0, y0, z0, x1, y1, z1, k in greedy_boxes(keys):
        r, g, b, is_glow = palette[k - 1]
        faces = {}
        for name in DIRS:
            if face_covered(solid, (x0, y0, z0, x1, y1, z1), name):
                continue
            f = {"uv": pixel_uv(k - 1), "texture": "#pal"}
            if is_glow:
                f["neoforge_data"] = GLOW_DATA
            faces[name] = f
        if not faces:
            continue
        el = {"from": [mp(x0, 0), mp(y0, 1), mp(z0, 2)], "to": [mp(x1 + 1, 0), mp(y1 + 1, 1), mp(z1 + 1, 2)]}
        if tilt:
            el["rotation"] = {"angle": tilt, "axis": "z", "origin": [8, 8, 8]}
        el["faces"] = faces
        elements.append(el)
    return elements, palette, round(s, 4), int(length_cells)


def write_palette(item, palette):
    H = (len(palette) + PAL_W - 1) // PAL_W
    pal = np.zeros((H, PAL_W, 4), np.uint8)
    for i, (r, g, b, _) in enumerate(palette):
        pal[i // PAL_W, i % PAL_W] = (r, g, b, 255)
    arr = pal[np.arange(PAL_H) % H]   # 16 行に繰り返す (1px 高さはミップで崩れるため)
    out = ASSETS / "textures/item/3d" / f"{item}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(out)


def model_json(item, elements, display, blocking=False):
    return {
        "loader": "neoforge:separate_transforms",
        "base": {
            "gui_light": "front",
            "display": display,
            "textures": {"pal": f"skyabyss:item/3d/{item}", "particle": f"skyabyss:item/{item}"},
            "texture_size": [PAL_W, PAL_H],
            "elements": elements,
        },
        "perspectives": {
            "gui": {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}},
        },
        # 盾は構えている間 (バニラの blocking の印。TideGearClient が登録する) に構えのモデルへ替える
        **({"overrides": [{"predicate": {"blocking": 1}, "model": "skyabyss:item/coral_shield_blocking"}]}
           if item == "coral_shield" and not blocking else {}),
    }


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    json.loads(path.read_text(encoding="utf-8"))  # 形式の確認


def install_item(item: str, opt: str):
    cfg = ITEMS[item]
    src = REVIEW / item / opt
    dst = SOURCES / item
    dst.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src / "icon.png", dst / "icon.png")
    if (src / "voxels.npz").exists():
        shutil.copyfile(src / "voxels.npz", dst / "voxels.npz")
    (dst / "option.txt").write_text(opt + "\n", encoding="utf-8")
    return dst


def write_item(item: str):
    cfg = ITEMS[item]
    src = SOURCES / item
    icon = np.array(Image.open(src / "icon.png").convert("RGBA"))
    assert icon.shape == (32, 32, 4), icon.shape
    Image.fromarray(icon, "RGBA").save(ASSETS / "textures/item" / f"{item}.png")
    info = {"item": item, "option": (src / "option.txt").read_text(encoding="utf-8").strip()}
    if cfg["kind"] == "icon":
        write_json(ASSETS / "models/item" / f"{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{item}"}})
        return info
    d = np.load(src / "voxels.npz")
    grid, glow = d["grid"], d["glow"].astype(bool)
    tilt = ROT_DEG if cfg["kind"] == "weapon" else 0
    elements, palette, s, length = build_elements(grid, glow, float(cfg["target"]), tilt)
    if len(elements) > MAX_ELEMENTS:
        raise SystemExit(f"{item}: elements {len(elements)} > {MAX_ELEMENTS}")
    write_palette(item, palette)
    display = HANDHELD_DISPLAY if cfg["kind"] == "weapon" else SHIELD_DISPLAY
    write_json(ASSETS / "models/item" / f"{item}.json", model_json(item, elements, display))
    if item == "coral_shield":
        write_json(ASSETS / "models/item" / "coral_shield_blocking.json",
                   model_json(item, elements, SHIELD_BLOCKING_DISPLAY, blocking=True))
    info.update({"elements": len(elements), "faces": sum(len(e["faces"]) for e in elements),
                 "palette": len(palette), "voxel_size": s, "length_cells": length})
    # 確認用の描画
    PREVIEW.mkdir(parents=True, exist_ok=True)
    front, hand = preview_3d(grid, glow, tilt)
    Image.fromarray(np.hstack([np.array(front.convert("RGB")), np.array(hand.convert("RGB"))]), "RGB").save(
        PREVIEW / f"tide_gear_{item}.png")
    if item == "coral_shield":
        # 盾の持ち方の確認 (簡易。手と腕は描かず、板と持ち手だけを表示位置へ動かして描く)
        model = json.loads((ASSETS / "models/item" / f"{item}.json").read_text(encoding="utf-8"))
        pal = np.array(Image.open(ASSETS / "textures/item/3d" / f"{item}.png").convert("RGBA"))
        faces = faces_from_model(model, pal)
        views = [
            render(apply_display(faces, SHIELD_DISPLAY["thirdperson_lefthand"]), yaw=0, pitch=0, size=(300, 300), margin=0.06),
            render(apply_display(faces, SHIELD_BLOCKING_DISPLAY["thirdperson_lefthand"]), yaw=0, pitch=0, size=(300, 300), margin=0.06),
            render(apply_display(faces, SHIELD_DISPLAY["firstperson_lefthand"]), yaw=0, pitch=0, size=(300, 300), margin=0.06),
        ]
        Image.fromarray(np.hstack([np.array(v.convert("RGB")) for v in views]), "RGB").save(
            PREVIEW / f"tide_gear_{item}_hold.png")
    return info


def _rot_xyz(rx, ry, rz):
    """MC の display の回転 (度, 順に X, Y, Z) を行列にする。"""
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def apply_display(faces, d):
    """display の変換 (中心 (8,8,8)、拡大、回転、平行移動) を面の一覧に掛ける。確認用の近似。"""
    R = _rot_xyz(*d.get("rotation", [0, 0, 0]))
    sc = np.array(d.get("scale", [1, 1, 1]), float)
    tr = np.array(d.get("translation", [0, 0, 0]), float)
    out = []
    for f in faces:
        out.append(Face(R @ ((f.origin - 8) * sc) + 8 + tr, R @ (f.eu * sc), R @ (f.ev * sc), f.tex, f.glow, f.shade))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", action="append", default=[], help="item=a|b|c")
    ap.add_argument("--from-sources", action="store_true", help="tools/sources/tide_gear/ から書き直す")
    args = ap.parse_args()
    choice = dict(RECOMMEND)
    for kv in args.option:
        item, opt = kv.split("=")
        if item not in ITEMS or opt not in OPTS:
            raise SystemExit(f"bad option {kv}")
        choice[item] = opt
    results = []
    for item in ITEMS:
        if not args.from_sources:
            install_item(item, choice[item])
        results.append(write_item(item))
    for r in results:
        print(json.dumps(r, ensure_ascii=False))


if __name__ == "__main__":
    main()
