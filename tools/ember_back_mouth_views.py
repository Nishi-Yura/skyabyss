"""余燼の背の口まわり (舌・上あご・喉の壁・到着点) のブロックの図を、案 C のメッシュの口と並べて描く。
使い方: SKYABYSS_DUMP=ember_back ./gradlew runServer  で run/dumps/ember_back.sbd を書いてから
        python tools/ember_back_mouth_views.py run/dumps/ember_back.sbd
出力: docs/review/previews/ember_back_mouth_{side,front,top}.png (左 = ブロック、右 = 案 C のメッシュ。メッシュは作り直さず描くだけ)
ブロックの図は structure/render.py (断面 x=0 と z=-155、真上は y 76 より上を除く)。"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/structure"))
sys.path.insert(0, str(ROOT / "tools/rough/lowpoly/lp_ember_titan"))
sys.path.insert(0, str(ROOT / "tools/rough"))
sys.path.insert(0, str(ROOT / "tools"))
import render as R  # noqa: E402
from sbd import load  # noqa: E402
import ember_back_c_views as V  # noqa: E402

OUT = ROOT / "docs/review/previews"


def main():
    d = load(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    cam = [float(v) for v in sys.argv[2:5]] if len(sys.argv) >= 5 else [0.0, 12.0, -190.0]
    V.pair(R.section(d, "x", 0, scale=8), V.mesh_view(90, 0, tuple(cam), 9.0, (900, 500)),
           "ember back blocks: section x=0 (head left)", "option C mesh (side, mouth)", OUT / "ember_back_mouth_side.png")
    V.pair(R.section(d, "z", -155, scale=8), V.mesh_view(0, 0, tuple(cam), 9.0, (900, 500)),
           "ember back blocks: section z=-155 (mouth)", "option C mesh (front, mouth)", OUT / "ember_back_mouth_front.png")
    V.pair(R.section(d, "y", 71, scale=8), V.mesh_view(180, 90, tuple(cam), 9.0, (900, 700)),
           "ember back blocks: section y=71 (mouth room)", "option C mesh (top, mouth)", OUT / "ember_back_mouth_top.png")


if __name__ == "__main__":
    main()
