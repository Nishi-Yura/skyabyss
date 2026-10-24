# -*- coding: utf-8 -*-
"""サイトの頁の頭に使う、実機の絵の撮影台本を書く (tools/site の gen_site_shots.py が webp にする)。

使い方: python -I tools/gen/shots/gen_site_hero_shots.py
        → tools/shots/site_hero_flat.txt (平らな世界。巨神の背・技・防具・転生)
          tools/shots/site_hero_town.txt (はじまりの都。夕暮れ・語り部・大通り・焚き火)
撮る:   bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/site_hero_flat.txt -PshotsName=site_hero_flat
        bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/site_hero_town.txt -PshotsName=site_hero_town
絵:     run/shots/<名>/shots/<名>/*.png を tools/sources/site/shots/ へ写す (名前は下の SHOTS の名)

要点 (前の撮影が失敗した原因への対策):
- 開幕の場面 (題字・暗転) が残ると絵が黒くなる。最初に `cmd skyabyss cinematic stop` を流し、待つ。
- 時刻は `cmd time set` が効く (確かめた)。日没は 12000 前後、夕暮れは 12600 ほど。
- HUD は `hud off` で隠す。mod の層も hideGui で隠れる (client/hud/HudManager の層の見張り)。
- カメラは被写体から離して、形が読める距離に置く (近すぎると面だけになる)。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT_FLAT = ROOT / "tools/shots/site_hero_flat.txt"
OUT_TOWN = ROOT / "tools/shots/site_hero_town.txt"

HEAD_FLAT = """# サイトの頁の頭の絵 (平らな世界)。tools/gen/shots/gen_site_hero_shots.py が書く。
world shots flat
size 1280 720
timeout 1200
render 16
clouds on
cmd gamemode creative
cmd skyabyss cinematic stop
cmd time set 12200
cmd weather clear
cmd gamerule doDaylightCycle false
cmd skyabyss dev arrive titan_back
waitchunks
wait 300
cmd skyabyss cinematic stop
chat clear
hud off
cmd gamemode spectator
"""

# 平らな世界の場面: (名前, 行)。巨神の背は skyabyss:titan_back の次元
SCENES_FLAT = [
    ("site_index", [
        # 地上から空の背を見上げる (候補を三つ撮って、読める一枚を選ぶ)
        "camera skyabyss:titan_back -60 -59 60 0 -35",
        "waitchunks",
        "wait 40",
        "shot site_index_a",
        "camera skyabyss:titan_back 0 -59 -120 0 -30",
        "waitchunks",
        "wait 40",
        "shot site_index_b",
        "camera skyabyss:titan_back -200 -59 170 270 -30",
        "waitchunks",
        "wait 40",
        "shot site_index_c",
    ]),
    ("site_titans", [
        "camera skyabyss:titan_back -150 150 60 290 10",
        "waitchunks",
        "wait 40",
        "shot site_titans",
    ]),
    ("site_party", [
        "cmd execute in skyabyss:titan_back run summon armor_stand -70 98 176 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{id:\"skyabyss:ember_boots\",count:1},{id:\"skyabyss:ember_leggings\",count:1},{id:\"skyabyss:ember_chestplate\",count:1},{id:\"skyabyss:ember_helmet\",count:1}]}",
        "cmd execute in skyabyss:titan_back run summon armor_stand -66 98 178 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{id:\"skyabyss:ember_boots\",count:1},{id:\"skyabyss:ember_leggings\",count:1},{id:\"skyabyss:ember_chestplate\",count:1},{id:\"skyabyss:ember_helmet\",count:1}]}",
        "cmd execute in skyabyss:titan_back run summon armor_stand -62 98 178 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{id:\"skyabyss:ember_boots\",count:1},{id:\"skyabyss:ember_leggings\",count:1},{id:\"skyabyss:ember_chestplate\",count:1},{id:\"skyabyss:ember_helmet\",count:1}]}",
        "cmd execute in skyabyss:titan_back run summon armor_stand -58 98 176 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{id:\"skyabyss:ember_boots\",count:1},{id:\"skyabyss:ember_leggings\",count:1},{id:\"skyabyss:ember_chestplate\",count:1},{id:\"skyabyss:ember_helmet\",count:1}]}",
        "camera skyabyss:titan_back -64 101 158 0 4",
        "waitchunks",
        "wait 40",
        "shot site_party",
    ]),
    ("site_skills", [
        "cmd gamemode creative",
        "cmd skyabyss level set @p 50",
        "cmd skyabyss skill forget @p",
        "cmd skyabyss skill points @p 40",
        "cmd skyabyss skill learn @p titan_cleave 3",
        "cmd skyabyss skill slot @p 1 titan_cleave",
        "camera skyabyss:titan_back -66 98 173 270 4",
        "perspective back",
        "fov 48",
        "waitchunks",
        "wait 20",
        "cmd skyabyss skill cast @p 1",
        "wait 6",
        "shot site_skills",
        "perspective first",
        "fov 70",
        "cmd gamemode spectator",
    ]),
    ("site_gear", [
        "camera minecraft:overworld 0 -58.6 5 180 8",
        "cmd summon armor_stand 0 -60 0 {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{id:\"skyabyss:ember_boots\",count:1},{id:\"skyabyss:ember_leggings\",count:1},{id:\"skyabyss:ember_chestplate\",count:1},{id:\"skyabyss:ember_helmet\",count:1}]}",
        "waitchunks",
        "wait 40",
        "shot site_gear",
    ]),
    ("site_rebirth", [
        "cmd skyabyss dev rebirth 3",
        "wait 140",
        "cmd gamemode spectator",
        "open rebirth",
        "wait 40",
        "shot site_rebirth",
        "close",
    ]),
]

HEAD_TOWN = """# サイトの頁の頭の絵 (はじまりの都)。tools/gen/shots/gen_site_hero_shots.py が書く。
world opening_capital_rebuild normal
size 1280 720
timeout 2400
render 16
clouds off
cmd skyabyss cinematic stop
wait 100
cmd skyabyss cinematic stop
cmd time set 12600
cmd weather clear
cmd gamerule doDaylightCycle false
cmd gamemode spectator
cmd skyabyss dev opening build
hud off
"""

# 都の場面: 「at shelter 0 x y z yaw pitch」は設計図の座標と向き (yaw 0 = 南)。移るたびに係の人を立たせる
SCENES_TOWN = [
    ("site_play", [
        "cmd skyabyss dev opening at shelter 0 0 70 150 180 26",
        "waitchunks",
        "cmd skyabyss facility place",
        "wait 120",
        "shot site_play",
    ]),
    ("site_faq", [
        "cmd skyabyss dev opening at shelter 0 20 4 0 -90 6",
        "waitchunks",
        "cmd skyabyss facility place",
        "wait 60",
        "shot site_faq",
    ]),
    ("site_changelog", [
        "cmd skyabyss dev opening at shelter 0 0 150 60 180 62",
        "waitchunks",
        "cmd skyabyss facility place",
        "wait 200",
        "shot site_changelog",
    ]),
    ("site_story", [
        "cmd skyabyss dev opening at shelter 0 -12 6 -50 180 8",
        "waitchunks",
        "cmd skyabyss facility place",
        "wait 60",
        "shot site_story",
    ]),
    ("site_pouch", [
        "cmd skyabyss dev opening at shelter 0 0 6 8 180 22",
        "waitchunks",
        "cmd skyabyss facility place",
        "wait 60",
        "shot site_pouch",
    ]),
]


def script(head, scenes):
    lines = [head]
    for name, steps in scenes:
        lines.append("# %s" % name)
        lines.extend(steps)
        lines.append("")
    lines.append("quit")
    return "\n".join(lines) + "\n"


def main():
    OUT_FLAT.parent.mkdir(parents=True, exist_ok=True)
    OUT_FLAT.write_text(script(HEAD_FLAT, SCENES_FLAT), encoding="utf-8")
    OUT_TOWN.write_text(script(HEAD_TOWN, SCENES_TOWN), encoding="utf-8")
    print("wrote", OUT_FLAT)
    print("wrote", OUT_TOWN)


if __name__ == "__main__":
    main()
