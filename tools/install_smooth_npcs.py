"""語り部 (storyteller) と イサナ (whale_chaser) の滑らか版ラフを、ゲームの assets へ入れる。

選んだ案: 語り部 = B 頭巾の語り部、イサナ = A つば広帽の旅人。
元: docs/review/smooth_npc_roughs/<名前>/<案>/ (tools/rough/smooth_npc.py が作る)。ラフは面の名前が「west = x の小さい側」の
並びなので、入れたあとに tools/fix_geo_face_sides.py --apply で GeckoLib の並びへ直す (顔・テクスチャの左右が逆にならない)。

  python tools/install_smooth_npcs.py

やること: geo の identifier を本番の名前にして geo/entity へ、png を textures/entity へ。光る所は無く、NpcRenderer は
発光層を付けないので glowmask は入れない (古い glowmask があれば消す)。骨の名前はアニメ・NpcRenderer と同じ。
ラフを作り直したときも、このスクリプトを回し直せば同じ結果になる。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skyabyss")
PICKS = {"storyteller": "b", "whale_chaser": "a"}


def main():
    for name, opt in PICKS.items():
        src = os.path.join(ROOT, "docs/review/smooth_npc_roughs", name, opt)
        with open(os.path.join(src, f"{name}.geo.json"), encoding="utf-8") as f:
            geo = json.load(f)
        geo["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.{name}"
        with open(os.path.join(ASSETS, "geo/entity", f"{name}.geo.json"), "w", encoding="utf-8") as f:
            json.dump(geo, f, indent=1)
        shutil.copyfile(os.path.join(src, f"{name}.png"), os.path.join(ASSETS, "textures/entity", f"{name}.png"))
        glow = os.path.join(ASSETS, "textures/entity", f"{name}_glowmask.png")
        if os.path.exists(glow):
            os.remove(glow)
        print("installed", name, "option", opt)
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools/fix_geo_face_sides.py"), "--apply", *PICKS])


if __name__ == "__main__":
    main()
