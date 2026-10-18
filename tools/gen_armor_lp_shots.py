#!/usr/bin/env python3
"""Write the scripted-screenshot scripts for the low-poly armor (tools/shots/armor_lp_<set>.txt, armor_lp_perf.txt).

  python tools/gen_armor_lp_shots.py
  ./gradlew runShots -Pscript=tools/shots/armor_lp_stone_whale.txt      (images: run/shots/<name>/shots/<name>/*.png)
docs/design/16_armor_design.md section 6.  Names: armor_lp_<set>_<config>_<view>.png, armor_lp_<set>_player_<config>_<pose>.png
configs: common / rare / legend / rare5 / rare10 / legend10   (LowPolyArmor.forcedTier / forcedLevel; the items are all RARE)
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'tools', 'shots')
SETS = {
    'stone_whale': 'stone_whale_{p}',
    'tide_shell': 'tide_shell_{p}',
    'thunder_wing': 'thunder_wing_{p}',
    'ember': 'ember_{p}',
}
PIECES = ['boots', 'leggings', 'chestplate', 'helmet']
CONFIGS = {
    'common': ('common', 0), 'rare': ('rare', 0), 'legend': ('legend', 0),
    'rare5': ('rare', 5), 'rare10': ('rare', 10), 'legend10': ('legend', 10),
}
STAND_VIEWS = {'front': 0, 'back': 180, 'q34': 40}
HEADER = """# Low-poly armor ({title}) on armor stands and on the player (docs/design/16_armor_design.md section 6).
# ./gradlew runShots -Pscript=tools/shots/armor_lp_{set}.txt
world shots flat
size 1280 720
timeout 1500
render 8
clouds off
cmd skyabyss cinematic stop
cmd time set noon
cmd weather clear
cmd gamemode spectator
cmd fill -20 -61 -20 20 -61 20 minecraft:smooth_basalt destroy
cmd kill @e[type=!minecraft:player,distance=..64]
hud off
cmd summon armor_stand 0 -60 12 {{Invisible:1b}}
"""


def armor_items(set_):
    return ','.join('{id:"skyabyss:%s",count:1}' % SETS[set_].format(p=p) for p in PIECES)


def stand(set_, yaw, x=0, extra=''):
    return f'cmd summon armor_stand {x} -60 0 {{ShowArms:1b,NoBasePlate:1b,Rotation:[{yaw}f,0f]{extra},ArmorItems:[{armor_items(set_)}]}}'


def gen_set(set_):
    L = [HEADER.format(title=set_, set=set_)]
    for cfg, (tier, level) in CONFIGS.items():
        L.append(f'armortier {tier}')
        L.append(f'armorlevel {level}')
        views = STAND_VIEWS if cfg in ('common', 'rare', 'legend', 'legend10') else {'front': 0}
        for v, yaw in views.items():
            L.append('cmd kill @e[type=minecraft:armor_stand]')
            L.append(stand(set_, yaw))
            L.append('camera minecraft:overworld 0 -60.6 3.7 180 0')
            L.append('waitchunks')
            L.append('wait 25')
            L.append(f'shot armor_lp_{set_}_{cfg}_{v}')
    # stands with the arms raised / swung (pose clipping)
    L.append('armortier legend')
    L.append('armorlevel 10')
    poses = {
        'armsup': 'Pose:{LeftArm:[-150f,0f,10f],RightArm:[-150f,0f,-10f]}',
        'armsside': 'Pose:{LeftArm:[0f,0f,-75f],RightArm:[0f,0f,75f]}',
        'armsside45': 'Pose:{LeftArm:[0f,0f,-45f],RightArm:[0f,0f,45f]}',
        'armsback': 'Pose:{LeftArm:[40f,0f,0f],RightArm:[40f,0f,0f],LeftLeg:[-30f,0f,0f],RightLeg:[30f,0f,0f]}',
        'walk': 'Pose:{LeftArm:[-28f,0f,0f],RightArm:[28f,0f,0f],LeftLeg:[32f,0f,0f],RightLeg:[-32f,0f,0f]}',
    }
    for name, pose in poses.items():
        L.append('cmd kill @e[type=minecraft:armor_stand]')
        L.append(stand(set_, 35, extra=',' + pose))
        L.append('camera minecraft:overworld 0 -60.6 3.7 180 0')
        L.append('waitchunks')
        L.append('wait 25')
        L.append(f'shot armor_lp_{set_}_pose_{name}')
    L.append('cmd kill @e[type=minecraft:armor_stand]')
    # the player (creative so the level requirement does not take the armor off)
    L.append('cmd gamemode creative')
    for p in PIECES:
        slot = {'boots': 'feet', 'leggings': 'legs', 'chestplate': 'chest', 'helmet': 'head'}[p]
        L.append(f'cmd item replace entity @s armor.{slot} with skyabyss:{SETS[set_].format(p=p)}')
    for cfg in ('common', 'rare', 'legend10'):
        tier, level = CONFIGS[cfg]
        L.append(f'armortier {tier}')
        L.append(f'armorlevel {level}')
        L.append('camera minecraft:overworld 0 -60 0 180 0')
        L.append('perspective front')
        L.append('waitchunks')
        L.append('wait 25')
        L.append(f'shot armor_lp_{set_}_player_{cfg}_idle')
        L.append('perspective back')
        L.append('wait 4')
        L.append(f'shot armor_lp_{set_}_player_{cfg}_back')
        L.append('perspective front')
        L.append('swing')
        L.append('wait 2')
        L.append(f'shot armor_lp_{set_}_player_{cfg}_attack')
        L.append('wait 20')
        L.append('sneak on')
        L.append('wait 12')
        L.append(f'shot armor_lp_{set_}_player_{cfg}_crouch')
        L.append('sneak off')
        L.append('walk on')
        L.append('wait 9')
        L.append(f'shot armor_lp_{set_}_player_{cfg}_walk')
        L.append('wait 4')
        L.append(f'shot armor_lp_{set_}_player_{cfg}_walk2')
        L.append('walk off')
        L.append('wait 10')
    # riding a boat (legs forward, arms on the sides)
    L.append('armortier legend')
    L.append('armorlevel 10')
    L.append('cmd summon minecraft:boat 0 -60 0')
    L.append('cmd ride @s mount @e[type=minecraft:boat,limit=1,sort=nearest]')
    L.append('wait 20')
    L.append('perspective front')
    L.append(f'shot armor_lp_{set_}_player_riding')
    L.append('perspective back')
    L.append('wait 4')
    L.append(f'shot armor_lp_{set_}_player_riding_back')
    L.append('quit')
    return '\n'.join(L) + '\n'


def gen_perf():
    """20 armored stands (5 per set, back row of 4 sets) and 20 armored stands cube vs low-poly."""
    L = [HEADER.format(title='perf: 20 armored stands', set='perf')]
    sets = list(SETS)
    for i in range(20):
        s = sets[i % 4]
        x = -9.5 + (i % 10) * 2.0
        z = -(i // 10) * 2.0
        L.append(f'cmd summon armor_stand {x} -60 {z} {{ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:[{armor_items(s)}]}}')
    L.append('armortier legend')
    L.append('armorlevel 10')
    L.append('camera minecraft:overworld 0 -57 9 180 22')
    L.append('waitchunks')
    L.append('wait 40')
    for label, cmd in (('lowpoly_legend', None), ('lowpoly_rare', 'armortier rare'), ('cubes', 'lowpoly armoroff')):
        if cmd:
            L.append(cmd)
        L.append('wait 20')
        for rep in range(3):
            L.append(f'perf 100 armor20_{label}_{rep}')
        L.append(f'shot armor_lp_perf_{label}')
    L.append('quit')
    return '\n'.join(L) + '\n'


def main():
    for s in SETS:
        with open(os.path.join(OUT, f'armor_lp_{s}.txt'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(gen_set(s))
    with open(os.path.join(OUT, 'armor_lp_perf.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(gen_perf())


if __name__ == '__main__':
    main()
