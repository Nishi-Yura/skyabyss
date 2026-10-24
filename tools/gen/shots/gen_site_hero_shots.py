# -*- coding: utf-8 -*-
"""サイトの頁の頭に使う、実機の絵の撮影台本を書く (tools/site の gen_site_shots.py が webp にする)。

使い方: python tools/gen/shots/gen_site_hero_shots.py   → tools/shots/site_hero.txt
撮る:   bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/site_hero.txt -PshotsName=site_hero
絵:     run/shots/site_hero/shots/site_hero/*.png を tools/sources/site/shots/ へ写す (名前は下の SHOTS の名)

場面: 台本の頭で世界を作り、「頭の文」(開幕の札) が引くまで待ってから撮る。HUD は `hud off` で隠すが、
mod の角の表示 (Lv の札・目的の一行) は出たまま残る場合がある (撮ったあと絵で確かめる)。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "tools/shots/site_hero.txt"

HEAD = """# サイトの頁の頭の絵 (site_hero)。tools/gen/shots/gen_site_hero_shots.py が書く。
world shots flat
size 1280 720
timeout 900
render 16
clouds on
cmd time set 11500
cmd weather clear
cmd gamemode spectator
hud off
chat clear
"""

SCENES = [
    # 頁の頭 (index): 巨神の背の上から、夕方の空の方を見る (背の縁の向こうに空が開ける)
    ("site_index_sky", [
        "cmd skyabyss dev arrive tide_back",
        "waitchunks",
        "wait 300",
        "chat clear",
        "hud off",
        "camera skyabyss:tide_back 150 190 150 135 28",
        "waitchunks",
        "wait 40",
        "shot site_index_sky",
    ]),
    # 頁の頭 (titans): 巨神の背に立って、背の中ほどを見下ろす
    ("site_titans_back", [
        "cmd skyabyss dev arrive titan_back",
        "waitchunks",
        "wait 300",
        "chat clear",
        "hud off",
        "camera skyabyss:titan_back 0 150 140 180 25",
        "waitchunks",
        "wait 40",
        "shot site_titans_back",
    ]),
]


def main():
    lines = [HEAD]
    for name, steps in SCENES:
        lines.append("# %s" % name)
        lines.extend(steps)
        lines.append("")
    lines.append("quit")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
