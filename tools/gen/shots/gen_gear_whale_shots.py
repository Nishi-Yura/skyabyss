"""岩鯨の武器 3 点 (鯨骨の大剣・番兵長の鉾・水路の牙) の確認の台本 tools/shots/gear_whale.txt を作る。
一人称・三人称 (正面・斜め)・地面に落ちた姿・生き物 (ゾンビ) が持つ姿・GUI (持ち物画面) を、通常・レア・伝説の 3 段で撮る。
使い方: python tools/gen/shots/gen_gear_whale_shots.py → ./gradlew runShots --no-daemon -Pscript=tools/shots/gear_whale.txt
絵: run/shots/gear_whale/shots/gear_whale/*.png  防具は tools/shots/armor_lp_stone_whale.txt (tools/gen/shots/gen_armor_lp_shots.py)。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ITEMS = ["whalebone_greatsword", "captain_pike", "waterway_fang"]
TIERS = ["common", "rare", "legend"]
OUT = ["""# 岩鯨の武器の確認 (tools/gen/shots/gen_gear_whale_shots.py が作る)
world shots flat
size 960 540
timeout 3000
render 6
cmd time set noon
cmd weather clear
cmd gamemode creative
camera 0 -60 8 180 0
waitchunks
hud on
wait 350
cmd skyabyss cinematic stop
close
wait 20"""]


def view(name, persp, hud, yaw=0):
    OUT.extend([f"perspective {persp}", f"bodyyaw {yaw}", "hud on" if hud else "hud off", "close", "wait 8", "chat clear", f"shot {name}"])


for tier in TIERS:
    OUT.append(f"weapontier {tier}")
    for w in ITEMS:
        OUT.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
        OUT.append("mainhand right")
        view(f"hold_{w}_{tier}_fpR", "first", True)
        view(f"hold_{w}_{tier}_tpR", "front", False, 0)
        view(f"hold_{w}_{tier}_tpSide", "front", False, 50)
# 地面と生き物が持つ姿 (段は伝説)
OUT.append("weapontier legend")
OUT.append("cmd gamemode creative")
OUT.append("cmd kill @e[type=!minecraft:player,distance=..64]")
for i, w in enumerate(ITEMS):
    x = (i - 1) * 2
    OUT.append(f'cmd summon item {x} -59 4 {{Item:{{id:"skyabyss:{w}",count:1}},PickupDelay:32767,Age:-32768}}')
    OUT.append(f'cmd summon zombie {x} -60 -1 {{PersistenceRequired:1b,NoAI:1b,Rotation:[0f,0f],HandItems:[{{id:"skyabyss:{w}",count:1}},{{}}],'
               f'ArmorItems:[{{}},{{}},{{}},{{}}],HandDropChances:[0f,0f]}}')
OUT.extend(["camera 0 -58.4 8 180 -6", "perspective first", "hud off", "wait 30", "shot ground_and_mob_all"])
OUT.extend(["camera 0 -59.2 2.4 180 25", "wait 6", "shot ground_close"])
OUT.extend(["camera 0 -59.6 -6.0 0 3", "wait 6", "shot mob_held_close"])
OUT.append("cmd kill @e[type=!minecraft:player,distance=..64]")
# GUI
OUT.append("cmd clear @s")
for w in ITEMS:
    OUT.append(f"cmd give @s skyabyss:{w}")
OUT.extend(["cmd gamemode survival", "guiscale 4", "chat clear", "open inventory", "wait 10", "shot gui_inventory", "close", "quit"])
(ROOT / "tools/shots/gear_whale.txt").write_text("\n".join(OUT) + "\n", encoding="utf-8")
print(len(OUT), "lines")
