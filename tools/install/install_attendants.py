#!/usr/bin/env python3
"""都の施設の番人 (15 役) の低ポリモデルを、3 案 (a / b / c) とも入れる。ラフは tools/rough/lowpoly/fac/attendants.py。

  python tools/install/install_attendants.py

- 案ごとにメッシュは 1 つ (lowpoly/attendant_<案>.json)、絵だけ役ごと:
    textures/lowpoly/attendant_<案>_<役>.png (15 枚)。attendant_<案>.png = 組合の受付の書記と同じ (LowPoly の既定の名前)
- root は回さない (前 = -Z のメッシュはそのままで前を向く。2026-10-21 のゲームの中の絵で、180 度足すと後ろ向きになると分かった。頭の上下の符号は AttendantRenderer が直す)
- 光る画素は無い (マスクを渡さない = 空のマスクは入れない)
- アニメは旅の職人のものを写し、名前を animation.attendant.* に替える (animations/entity/attendant.animation.json: idle / walk / talk)
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "docs", "review", "npc_roughs")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
TEX = os.path.join(ASSETS, "textures", "lowpoly")
TMP = os.path.join(ROOT, "build", "tmp_attendant")

ROLES = ["guild_clerk", "smith", "tool_merchant", "apothecary", "storage_keeper", "innkeeper", "perch_keeper",
         "shrine_priest", "keep_veteran", "archivist", "training_master", "black_marketeer", "gravekeeper",
         "mapmaker", "drunkard", "trade_keeper", "stable_keeper"]
DEFAULT = "guild_clerk"


def install_look(k):
    d = os.path.join(SRC, k)
    mesh = json.load(open(os.path.join(d, "attendant_%s_mesh.json" % k), encoding="utf-8"))
    # root は回さない (ラフのメッシュは前 = -Z で、そのままゲームの前を向く。旅の職人のように 180 度足すと後ろ向きになる)
    mesh["bones"]["root"]["rotation"] = [0.0, 0.0, 0.0]
    mesh_path = os.path.join(TMP, "attendant_%s_mesh.json" % k)
    with open(mesh_path, "w", encoding="utf-8") as f:
        json.dump(mesh, f, separators=(",", ":"))
    name = "attendant_" + k
    # --glow には在りもしない道を渡す (ラフの空のマスクを探しに行かせない)
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), name, "--mesh", mesh_path,
                           "--atlas", os.path.join(d, "attendant_%s_%s_atlas.png" % (k, DEFAULT)),
                           "--glow", os.path.join(TMP, "no_glowmask.png")])
    for role in ROLES:
        shutil.copyfile(os.path.join(d, "attendant_%s_%s_atlas.png" % (k, role)), os.path.join(TEX, "%s_%s.png" % (name, role)))
    print("textures/lowpoly/%s_<role>.png x %d" % (name, len(ROLES)))


def install_anim():
    src = os.path.join(ASSETS, "animations", "entity", "artisan.animation.json")
    data = json.load(open(src, encoding="utf-8"))
    anims = {}
    for n, v in data["animations"].items():
        anims[n.replace("animation.artisan.", "animation.attendant.")] = v
    data["animations"] = anims
    for need in ("idle", "walk", "talk"):
        assert "animation.attendant." + need in anims, need
    dst = os.path.join(ASSETS, "animations", "entity", "attendant.animation.json")
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("animations/entity/attendant.animation.json:", ", ".join(anims))


def main():
    os.makedirs(TMP, exist_ok=True)
    for k in "abc":
        install_look(k)
    install_anim()


if __name__ == "__main__":
    main()
