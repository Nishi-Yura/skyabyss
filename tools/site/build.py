"""site/ の HTML を書き出す (リポジトリ直下で `python -I tools/site/build.py`)。

wiki (site/wiki*.html) の元は tools/sources/site/wiki.json (ゲームの中の書き出し。tools/shots/wiki_dump.txt) と mod の JSON・lang。
絵は先に作っておく: 職の立ち姿 `python -I tools/site/cut_job_renders.py` (撮り直したときだけ) →
`python -I tools/site/gen_site_shots.py`。デプロイに build は要らない (site/ をそのまま GitHub Pages に載せる。
.github/workflows/pages.yml)。出力 (site/*.html) もコミットする。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import changelog
import pages
import wiki
from sitelib import redirect

# 前の版のサイトの頁の名前 → 今の頁 (外から張られた道を切らない)
OLD = {
    "play.html": "start.html",
    "faq.html": "start.html",
    "titans.html": "adventure.html",
    "skills.html": "combat.html",
    "rebirth.html": "gear.html",
    "pouch.html": "gear.html",
    "party.html": "together.html",
    "story.html": "world.html",
}


def main():
    pages.index()
    pages.world()
    pages.combat()
    pages.adventure()
    pages.gear()
    pages.together()
    pages.start()
    changelog.changelog()
    wiki.build()
    for old, new in OLD.items():
        redirect(old, new)
    print("built")


if __name__ == "__main__":
    main()
