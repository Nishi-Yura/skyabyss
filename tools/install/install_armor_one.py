#!/usr/bin/env python3
"""一品一案の防具 8 組 (2026-10-26。ユーザーが全部選んだ) を入れる。
入力: tools/rough/lowpoly/armor/build_one.py install の出力
  巨神 4 組: docs/review/lowpoly_roughs/armor/c/<組>/<部位>[_rare|_legend][_plusN]_{mesh.json,atlas.png,glowmask.png}
  旅装 4 組: docs/review/armor_one/raw/<組>/<部位>_{mesh.json,atlas.png,glowmask.png}
出力 (何度流しても同じ):
  assets/skyabyss/lowpoly/armor/<組>/<部位>[階級].json            メッシュ (閉じたもの)
  assets/skyabyss/textures/lowpoly/armor/<組>/<部位>[階級][_plusN].png と _glowmask.png
  assets/skyabyss/textures/item/<組>_<部位>.png                   持ち物のアイコン (32x32、3D から描く)
光る画素が一つもない部位 (旅装) は、留め具の 1 画素だけ光らせる (GeckoLib は空の glowmask で落ちる)。
使い方: python tools/install/install_armor_one.py
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "tools/lib"), str(ROOT / "tools/rough/lowpoly/armor")]
import mesh_close  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
TITAN_SRC = ROOT / "docs/review/lowpoly_roughs/armor/c"
TRAVEL_SRC = ROOT / "docs/review/armor_one/raw"
PIECES = ["helmet", "chestplate", "leggings", "boots"]
TITANS = ["stone_whale", "tide_shell", "thunder_wing", "ember"]
TRAVEL = ["whale_road", "tide_wader", "peak_crossing", "ash_walking"]


def put_mesh(src_mesh: Path, dst: Path, texture: str):
    d = json.loads(src_mesh.read_text(encoding="utf-8"))
    d.pop("triangle_count", None)
    d.pop("glowmask", None)
    d["texture"] = texture
    d["socket"] = False
    for b in d["bones"].values():
        b.pop("triangle_count", None)
    d, _ = mesh_close.close_mesh(d)
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(d, separators=(",", ":")), encoding="utf-8")
    return sum(len(b["triangles"]) for b in d["bones"].values())


def put_tex(atlas: Path, mask: Path, dst_stem: Path):
    img = Image.open(atlas).convert("RGBA")
    m = np.array(Image.open(mask).convert("RGBA"))[..., 3] > 0 if mask.exists() else np.zeros(img.size[::-1], bool)
    a = np.array(img)
    if not m.any():
        ys, xs = np.nonzero(a[..., 3] > 0)
        if len(ys):
            m[ys[len(ys) // 2], xs[len(xs) // 2]] = True
    glow = a.copy()
    glow[~m] = 0
    dst_stem.parent.mkdir(parents=True, exist_ok=True)
    img.save(str(dst_stem) + ".png", optimize=True)
    Image.fromarray(glow, "RGBA").save(str(dst_stem) + "_glowmask.png", optimize=True)


def main():
    total = 0
    for s in TITANS:
        src = TITAN_SRC / s
        for p in PIECES:
            for tier in ("", "_rare", "_legend"):
                total += put_mesh(src / f"{p}{tier}_mesh.json", ASSETS / "lowpoly/armor" / s / f"{p}{tier}.json", f"armor/{s}/{p}{tier}")
                for plus in ([""] if not tier else ["", "_plus5", "_plus10"]):
                    put_tex(src / f"{p}{tier}{plus}_atlas.png", src / f"{p}{tier}{plus}_glowmask.png",
                            ASSETS / "textures/lowpoly/armor" / s / f"{p}{tier}{plus}")
    for s in TRAVEL:
        src = TRAVEL_SRC / s
        for p in PIECES:
            total += put_mesh(src / f"{p}_mesh.json", ASSETS / "lowpoly/armor" / s / f"{p}.json", f"armor/{s}/{p}")
            put_tex(src / f"{p}_atlas.png", src / f"{p}_glowmask.png", ASSETS / "textures/lowpoly/armor" / s / p)
    icons()
    print("armor one: 8 sets installed,", total, "triangles in all meshes")


def icons():
    """持ち物のアイコン: 部位ごとに 3D から描く (巨神は通常の階級、旅装は一つ)"""
    import aview
    import apaint
    import build_series_icons as BI   # 読むと aview.apaint を差し替えるので、あとで戻す
    aview.apaint = apaint
    import build_one
    import titan_one
    aview.SS = 2
    for s in TITANS + TRAVEL:
        if s in TITANS:
            T = build_one.TITANS[s]
            res = aview.build_set(titan_one.build(T, "common"), T, "common")
        else:
            T = build_one.TRAVEL_GLOW[s]
            res = build_one.travel_set(s)
        for p in PIECES:
            ic = BI.to_icon(BI.render_piece(res, T, p))
            ic.save(ASSETS / "textures/item" / f"{s}_{p}.png")


if __name__ == "__main__":
    main()
