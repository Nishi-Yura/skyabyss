"""【役目を終えた】語り部とイサナは低ポリ版 (`tools/install/install_lowpoly_ember_npc.py`、15 の 9.6) に替わった。ここが入れた滑らかなボクセルの geo・png
(`geo/entity/{storyteller,whale_chaser}.geo.json`、`textures/entity/`) は、`LowPoly.enabled = false` のときの箱の描画として assets に残してある。
入力の `docs/review/smooth_npc_roughs/<名前>/<案>/` は消したので、このスクリプトはもう回らない (経緯のために残す)。

語り部 (storyteller) と イサナ (whale_chaser) の滑らか版ラフを、ゲームの assets へ入れる。

選んだ案: 語り部 = B 頭巾の語り部、イサナ = A つば広帽の旅人。
元: docs/review/smooth_npc_roughs/<名前>/<案>/ (tools/rough/smooth_npc.py が作る)。ラフは面の名前が「west = x の小さい側」の
並びなので、入れたあとに tools/lib/fix_geo_face_sides.py --apply で GeckoLib の並びへ直す (顔・テクスチャの左右が逆にならない)。

  python tools/install/install_smooth_npcs.py

やること: geo の identifier を本番の名前にして geo/entity へ、png を textures/entity へ。光る所は無く、NpcRenderer は
発光層を付けないので glowmask は入れない (古い glowmask があれば消す)。骨の名前はアニメ・NpcRenderer と同じ。
ラフを作り直したときも、このスクリプトを回し直せば同じ結果になる。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
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
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools/lib/fix_geo_face_sides.py"), "--apply", *PICKS])


if __name__ == "__main__":
    main()
