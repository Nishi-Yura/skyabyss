#!/usr/bin/env python3
"""旅の職人 (21 の P4) の低ポリモデルを入れる。選んだ案 B (槌と砥石の荷人) の形と、章ごとの 4 色 (docs/review/ch1_roughs/artisan_colorways/{a,b,c,d})。

  python tools/install_artisan.py

- 形・骨・UV は 4 色で同じ (骨 root / body / head / armL / armR / legL / legR)。メッシュは 1 つ (lowpoly/artisan.json)、絵だけ章ごと:
    textures/lowpoly/artisan_ch1.png (岩殻の縁) / _ch2 (塩の縁) / _ch3 (銅の針金の襟) / _ch4 (煤の裾と火の糸)。artisan.png = 一章と同じ (LowPoly の既定の名前)
- ラフのメッシュは root に回転が無い (前 = -Z)。ゲームの NPC (語り部・イサナ) の geo は root を yaw 180 度回してあり、NpcRenderer の頭の向き (pitch の符号) も
  それに合わせてあるので、ここで root に [0, 180, 0] を足して同じ向きにそろえる (「ピボットの修正」)。ほかの骨のピボットはラフのまま (腰 12、肩 20.6、首 22、脚の付け根 12.4)
- 光る画素は無い (マスクが空 = GeckoLib が落ちるので入れない)
- 面の顔 (対話画面の 80x80) は tools/gen_artisan_faces.py
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "review", "ch1_roughs", "artisan_colorways")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "lowpoly")
TMP = os.path.join(ROOT, "build", "tmp_artisan")


def main():
    os.makedirs(TMP, exist_ok=True)
    mesh = json.load(open(os.path.join(SRC, "a", "artisan_colorways_a_mesh.json"), encoding="utf-8"))
    mesh["bones"]["root"]["rotation"] = [0.0, 0.0, 0.0]  # 回さない (2026-10-21: 180 度だとゲームの中で後ろ向き。頭の上下は NpcRenderer.ArtisanModel で符号を戻す)
    mesh_path = os.path.join(TMP, "artisan_mesh.json")
    with open(mesh_path, "w", encoding="utf-8") as f:
        json.dump(mesh, f, separators=(",", ":"))
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install_lowpoly.py"), "artisan", "--mesh", mesh_path, "--atlas",
                           os.path.join(SRC, "a", "artisan_colorways_a_atlas.png"), "--glow", os.path.join(SRC, "a", "artisan_colorways_a_glowmask.png")])
    for n, k in enumerate("abcd", 1):
        shutil.copyfile(os.path.join(SRC, k, "artisan_colorways_%s_atlas.png" % k), os.path.join(TEX, "artisan_ch%d.png" % n))
        print("textures/lowpoly/artisan_ch%d.png" % n)


if __name__ == "__main__":
    main()
