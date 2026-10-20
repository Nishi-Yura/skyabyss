"""巨神シリーズ防具 (S1 / S2 / S3 x 4 巨神 x 3 階級) の見本画像を docs/review/armor_series/img/ に出す。順番に 1 つずつ作る (メモリを抑える)。
実行: python build_series.py [S1 S2 S3]   (省略で全部)   出力: 回転の帯 turn_*.png / 比べ cmp_*.png / 一覧 sheet_*.png / 着用 worn_*.png
インストールはしない (docs/review/armor_series/index.html で選んでから)。"""
from __future__ import annotations

import json
import sys
import time

import numpy as np
from PIL import Image

import aview
import scene
import spaint
import sstyle
from akit import ROOT, mannequin, pose_faces
from common import TITANS, TITAN_JP, SETS

aview.apaint = spaint
aview.SS = 2
OUT = ROOT / "docs/review/armor_series/img"
OPTS = ["S1", "S2", "S3"]
TIERS = ["common", "rare", "legend"]
BG = (28, 32, 40)
TW, TH, SC = 230, 350, 6.9          # 一覧のタイル
CEN = (0, 21.0, 0)
FR = 12                              # 回転のコマ数
RW, RH, RS = 150, 230, 4.9          # 回転の 1 コマ


def tile(res, T, yaw, pitch, size=(TW, TH), scale=SC, cen=CEN, pose=None):
    return aview.render(res, T, yaw, pitch, size, scale, center=cen, pose=pose, bg=BG)


def walk_pose(a=24):
    return {"armorRightArm": (a, 0, -3), "armorLeftArm": (-a, 0, 3), "armorRightLeg": (-a, 0, 0), "armorLeftLeg": (a, 0, 0),
            "armorRightBoot": (-a, 0, 0), "armorLeftBoot": (a, 0, 0)}


def first_person(res, T, size=(TW * 2, TH)):
    """一人称の見え方の模式: 頭と兜を消し、頭の後ろ上から前を見た向きで腕 (右が前へ伸びる) と肩・袖・籠手を写す。"""
    parts = [(r["faces"], r["atlas"], r["glow"]) for k, r in res.items() if k != "helmet"]
    flat = [(fs, rgb) for fs, rgb in mannequin() if not any(getattr(f, "bone", "") == "armorHead" for f in fs)]
    faces, atlas, glow = scene.compose(parts, flat)
    pose = {"armorRightArm": (44, 0, 3), "armorLeftArm": (28, 0, -3)}
    faces = pose_faces(faces, pose)
    im = scene.view(faces, atlas, glow, T, 0, 50, size, (0, 18.0, -4.0), 8.6, ss=aview.SS)
    bg = Image.new("RGBA", size, (46, 60, 78, 255))
    bg.alpha_composite(im)
    return bg.convert("RGB").transpose(Image.FLIP_LEFT_RIGHT)


def strip(res, T):
    im = Image.new("RGB", (RW * FR, RH))
    for i in range(FR):
        im.paste(aview.render(res, T, 180 + 360.0 * i / FR, 8, (RW, RH), RS, center=(0, 21.5, 0), bg=BG), (i * RW, 0))
    return im


def run(opts):
    OUT.mkdir(parents=True, exist_ok=True)
    stats = json.loads((OUT / "stats.json").read_text(encoding="utf-8")) if (OUT / "stats.json").exists() else {}
    for o in opts:
        tiles = {}
        worn = {}
        for T in TITANS:
            for rar in TIERS:
                t0 = time.time()
                res = aview.build_set(sstyle.build(T, rar, o), T, rar)
                stats.setdefault(o, {}).setdefault(T, {})[rar] = {"tris": aview.set_tris(res), "pieces": {k: v["tris"] for k, v in res.items()},
                                                                  "atlas": {k: v["size"] for k, v in res.items()}}
                tiles[(T, rar, "f")] = tile(res, T, 180, 6)
                tiles[(T, rar, "q")] = tile(res, T, 215, 14)
                tiles[(T, rar, "b")] = tile(res, T, 0, 6)
                strip(res, T).save(OUT / f"turn_{o}_{T}_{rar}.png", optimize=True)
                if rar == "rare":
                    worn[T] = [first_person(res, T), tile(res, T, 0, 14, (TW * 2, TH), SC * 1.05, pose=walk_pose(18)),
                               tile(res, T, 205, 10, (TW * 2, TH), SC * 1.05, pose=walk_pose(-18))]
                print(o, T, rar, stats[o][T][rar]["tris"], f"{time.time() - t0:.1f}s", flush=True)
        # 一覧 (行 = 巨神、列 = 階級 x [正面, 斜め, 背面])
        im = Image.new("RGB", (TW * 9, TH * 4), BG)
        for r, T in enumerate(TITANS):
            for c, rar in enumerate(TIERS):
                for k, v in enumerate("fqb"):
                    im.paste(tiles[(T, rar, v)], ((c * 3 + k) * TW, r * TH))
        im.save(OUT / f"sheet_{o}.png", optimize=True)
        # 家族 (4 巨神のレア、正面 + 背面)
        im = Image.new("RGB", (TW * 4, TH * 2), BG)
        for c, T in enumerate(TITANS):
            im.paste(tiles[(T, "rare", "f")], (c * TW, 0))
            im.paste(tiles[(T, "rare", "b")], (c * TW, TH))
        im.save(OUT / f"family_{o}.png", optimize=True)
        for T in TITANS:    # 着用の模式 (1P / 3P 背面 / 3P 前)
            w = Image.new("RGB", (TW * 6, TH), BG)
            for i, p in enumerate(worn[T]):
                w.paste(p, (i * TW * 2, 0))
            w.save(OUT / f"worn_{o}_{T}.png", optimize=True)
        (OUT / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    # 巨神ごとの比べ (行 = 案、列 = 通常正面 / レア正面 / 伝説正面 / 伝説背面)
    return stats


def cmp_sheets(opts):
    """sheet_*.png から巨神ごとの比べを切り出して並べる。"""
    shs = {o: Image.open(OUT / f"sheet_{o}.png") for o in opts}
    for r, T in enumerate(TITANS):
        im = Image.new("RGB", (TW * 4, TH * len(opts)), BG)
        for i, o in enumerate(opts):
            sh = shs[o]
            row = lambda c, k: sh.crop(((c * 3 + k) * TW, r * TH, (c * 3 + k + 1) * TW, (r + 1) * TH))
            im.paste(row(0, 0), (0, i * TH))
            im.paste(row(1, 0), (TW, i * TH))
            im.paste(row(2, 0), (TW * 2, i * TH))
            im.paste(row(2, 2), (TW * 3, i * TH))
        im.save(OUT / f"cmp_{T}.png", optimize=True)


if __name__ == "__main__":
    opts = [a for a in sys.argv[1:] if a in OPTS] or OPTS
    run(opts)
    if all((OUT / f"sheet_{o}.png").exists() for o in OPTS):
        cmp_sheets(OPTS)
