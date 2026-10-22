"""潮殻の背 (tide_back) の島を、巨神本体のモデル (採用案 B 珊瑚礁の背) と同じ形・同じ色のブロックの亀にする
(08 の 21 章「巨神の形の背中」)。中身は tools/gen/items/gen_back_shape.py の turtle の設定 (共通の道具)。

使い方 (リポジトリ直下で): python tools/gen/mobs/gen_tide_back_turtle.py [--preview]
出力: src/main/resources/data/skyabyss/tide_back/turtle.bin (gzip。TideTurtle.java が読む)
      --preview で docs/review/tide_shell_roughs/install/tide_back_turtle_{top,side,front}.png
縮尺・高さ・材料・腹の板・水際の藻の決まりは tools/gen/items/gen_back_shape.py の turtle_cfg に書いてある。
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../items')]
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_back_shape  # noqa: E402


def main():
    gen_back_shape.run("turtle", "--preview" in sys.argv)


if __name__ == "__main__":
    main()
