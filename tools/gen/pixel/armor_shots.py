# -*- coding: utf-8 -*-
"""防具の一式をゲームで撮る台本を書く（防具立て 5 体: 前・斜め・斜め後ろ・後ろ・腕を上げて歩く姿）。
  python -X utf8 -I tools/gen/pixel/armor_shots.py <組> [<組> ...]   → tools/shots/armor_set_check.txt
  ./gradlew runShots --offline --no-daemon -Pscript=tools/shots/armor_set_check.txt -PshotsName=<名>
絵は run/shots/<名>/shots/armor_set_check/<組>.png。胸当てしか無い組 (sky_stone) は胴だけ着せる。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ONLY_CHEST = {'sky_stone'}
HEAD = """world shots flat
size 1280 720
timeout 3000
render 8
clouds off
wait 100
cmd skyabyss cinematic stop
wait 10
cmd skyabyss cinematic stop
cmd time set noon
cmd weather clear
cmd gamemode spectator
cmd fill -20 -61 -20 20 -61 20 minecraft:smooth_basalt destroy
cmd kill @e[type=!minecraft:player,distance=..64]
hud off
armorlevel 0
camera minecraft:overworld 0 -59.0 4.6 180 4
waitchunks
"""


def items(s):
    if s in ONLY_CHEST:
        return f'[{{}},{{}},{{id:"skyabyss:{s}_chestplate",count:1}},{{}}]'
    return (f'[{{id:"skyabyss:{s}_boots",count:1}},{{id:"skyabyss:{s}_leggings",count:1}},'
            f'{{id:"skyabyss:{s}_chestplate",count:1}},{{id:"skyabyss:{s}_helmet",count:1}}]')


def main(sets):
    out = [HEAD]
    stands = [(-2.6, 0, ''), (-1.3, 35, ''), (0, 150, ''), (1.3, 180, ''),
              (2.6, 20, ',Pose:{LeftArm:[-95f,0f,-12f],RightArm:[-100f,0f,14f],RightLeg:[-28f,0f,0f],LeftLeg:[22f,0f,0f]}')]
    for s in sets:
        out.append('cmd kill @e[type=minecraft:armor_stand]')
        for x, r, pose in stands:
            out.append(f'cmd summon armor_stand {x} -60 0 {{ShowArms:1b,NoBasePlate:1b,Rotation:[{r}f,0f],ArmorItems:{items(s)}{pose}}}')
        out.append('wait 30')
        out.append(f'shot {s}')
    p = ROOT / 'tools/shots/armor_set_check.txt'
    p.write_text('\n'.join(out) + '\n', encoding='utf-8')
    print(p.relative_to(ROOT).as_posix(), len(sets), 'sets')


if __name__ == '__main__':
    main(sys.argv[1:])
