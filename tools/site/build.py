"""site/ の HTML を書き出す (リポジトリ直下で `python tools/site/build.py`)。

先に `python tools/site/gen_art.py` で絵を作っておく。デプロイに build は要らない
(site/ をそのまま GitHub Pages に載せる)。この build は、日英の対と技の木の図を
同じ元から作るための道具で、出力 (site/*.html) もコミットする。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import pages_a
import pages_b
import pages_c
import pages_d


def main():
    pages_a.index()
    pages_a.play()
    pages_a.titans()
    pages_b.skills()
    pages_b.gear()
    pages_d.rebirth()
    pages_b.pouch()
    pages_b.party()
    pages_c.story()
    pages_c.faq()
    pages_c.changelog()
    pages_c.alt_b()
    pages_c.alt_c()
    print("built")


if __name__ == "__main__":
    main()
