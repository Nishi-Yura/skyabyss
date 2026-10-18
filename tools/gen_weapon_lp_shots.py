"""低ポリの武器 (手・地面・額縁) を撮る台本を作る (docs/design/15 の 4.6.2)。

使い方: python tools/gen_weapon_lp_shots.py [武器の id ...]   (省くと 13 個全部。武器ごとの台本 tools/shots/weapon_lp_<id>.txt と、負荷の台本 weapon_lp_perf.txt を書く)
撮る: ./gradlew runShots -Pscript=tools/shots/weapon_lp_<id>.txt   (全部まとめて: python tools/run_weapon_lp_shots.py があれば不要。ここでは 1 本ずつ)
絵: run/shots/weapon_lp_<id>/shots/weapon_lp_<id>/<id>_{fp,tp}_<common|rare|legend>_p<0|5|10>.png ほか
まとめ: python tools/weapon_lp_sheet.py   -> docs/review/previews/weapon_lp_<id>.png (コンタクトシート) と weapon_lp_<id>_*.png

階級の固定は台本の `weapontier`、強化は品の component `skyabyss:enhance`、元のボクセルに戻して撮るのは `weaponmesh off`。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALL = ["whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon", "tidecaller_claw", "coral_shield",
       "thunder_wing_bow", "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker"]
TIERS = ["common", "rare", "legend"]
LEVELS = [0, 5, 10]
# 品の階級 (GearTier)。地面・額縁は品の階級のまま撮る
NATURAL = {"whalebone_greatsword": "rare", "captain_pike": "legend", "waterway_fang": "legend", "tide_shell_trident": "rare", "coral_harpoon": "legend",
           "tidecaller_claw": "legend", "coral_shield": "rare", "thunder_wing_bow": "rare", "warden_glaive": "legend", "raiju_claw": "legend",
           "ember_war_axe": "rare", "smith_hammer": "legend", "thrall_poker": "legend"}

HEAD = """# 低ポリの武器 (手・地面・額縁) を階級 x 強化で撮る (tools/gen_weapon_lp_shots.py が作る)
world shots flat
size 1280 720
timeout 2400
render 6
cmd time set noon
cmd weather clear
cmd gamemode creative
camera 0 -60 8 180 0
waitchunks
hud on
wait 350
cmd setblock 0 -59 3 minecraft:stone
cmd setblock 3 -59 3 minecraft:stone
"""


def stack(item, lv):
    return f'skyabyss:{item}[skyabyss:enhance={lv}]' if lv else f"skyabyss:{item}"


def nbt(item, lv):
    comp = f',components:{{"skyabyss:enhance":{lv}}}' if lv else ""
    return f'{{id:"skyabyss:{item}",count:1{comp}}}'


def script(item):
    o = [HEAD.rstrip("\n")]
    # 手: 一人称・三人称 x 階級 x 強化
    for tier in TIERS:
        o.append(f"weapontier {tier}")
        for lv in LEVELS:
            o.append(f"cmd item replace entity @s weapon.mainhand with {stack(item, lv)}")
            for view, persp in (("fp", "first"), ("tp", "front")):
                o.append(f"perspective {persp}")
                o.append("hud on" if view == "fp" else "hud off")
                o.append("bodyyaw 35" if view == "tp" else "bodyyaw 0")
                o.append("wait 10")
                o.append("chat clear")
                o.append(f"shot {item}_{view}_{tier}_p{lv}")
    o.append("weapontier off")
    # 元のボクセル (比べる用)
    o.append("weaponmesh off")
    o.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{item}")
    for view, persp in (("fp", "first"), ("tp", "front")):
        o.append(f"perspective {persp}")
        o.append("hud on" if view == "fp" else "hud off")
        o.append("bodyyaw 35" if view == "tp" else "bodyyaw 0")
        o.append("wait 10")
        o.append("chat clear")
        o.append(f"shot {item}_{view}_voxel")
    o.append("weaponmesh on")
    # 弓の引き / 盾の構え
    if item == "thunder_wing_bow":
        o.append("weapontier legend")
        o.append(f"cmd item replace entity @s weapon.mainhand with {stack(item, 10)}")
        for view, persp in (("fp", "first"), ("tp", "front")):
            o.append(f"perspective {persp}")
            o.append("hud on" if view == "fp" else "hud off")
            o.append("wait 10")
            o.append("chat clear")
            o.append("use")
            o.append("wait 4")
            o.append(f"shot {item}_{view}_draw0")
            o.append("wait 13")
            o.append(f"shot {item}_{view}_draw1")
            o.append("wait 12")
            o.append(f"shot {item}_{view}_draw2")
            o.append("stopuse")
            o.append("wait 20")
        o.append("weapontier off")
    if item == "coral_shield":
        for tier, lv in (("rare", 0), ("legend", 10)):
            o.append(f"weapontier {tier}")
            o.append(f"cmd item replace entity @s weapon.mainhand with {stack(item, lv)}")
            for view, persp in (("fp", "first"), ("tp", "front")):
                o.append(f"perspective {persp}")
                o.append("hud on" if view == "fp" else "hud off")
                o.append("wait 10")
                o.append("use")
                o.append("wait 14")
                o.append(f"shot {item}_{view}_block_{tier}_p{lv}")
                o.append("stopuse")
                o.append("wait 10")
        o.append("weapontier off")
    # 地面・額縁 (品の階級のまま。強化 +0 と +10)
    o.append("cmd item replace entity @s weapon.mainhand with minecraft:air")
    for lv in (0, 10):
        if lv:
            o.append("cmd kill @e[type=item]")
        if lv:
            o.append("cmd kill @e[type=item_frame]")
        o.append(f'cmd summon item 0 -59 4 {{Item:{nbt(item, lv)},PickupDelay:32767,Age:-32768}}')
        o.append(f'cmd summon item_frame 3 -59 4 {{Facing:3b,Item:{nbt(item, lv)},Invulnerable:1b}}')
        o.append("perspective first")
        o.append("hud off")
        o.append("camera 1.5 -60 8 180 12")
        o.append("wait 20")
        o.append("chat clear")
        o.append(f"shot {item}_world_p{lv}")
    o.append("quit")
    return "\n".join(o) + "\n"


def perf():
    """武器を持った armor stand 20 体 (ground の大剣 +10 / 伝説) を、低ポリとボクセルで比べる。"""
    o = [HEAD.rstrip("\n")]
    o.append("weapontier legend")
    rows = 4
    cols = 5
    stands = []
    for r in range(rows):
        for c in range(cols):
            x = -4 + c * 2
            z = -2 + r * 2
            stands.append(f'cmd summon armor_stand {x} -60 {z} {{ShowArms:1b,NoGravity:1b,Rotation:[0f,0f],HandItems:[{{id:"skyabyss:whalebone_greatsword",count:1,components:{{"skyabyss:enhance":10}}}},{{}}]}}')
    o += stands
    o.append("camera 0 -58 14 180 5")
    o.append("hud off")
    o.append("perspective first")
    o.append("wait 40")
    for mode in ("on", "off", "on", "off"):
        o.append(f"weaponmesh {mode}")
        o.append("wait 20")
        o.append(f"perf 160 stands20_{mode}")
    o.append("shot perf_stands")
    o.append("quit")
    return "\n".join(o) + "\n"


def main():
    items = sys.argv[1:] or ALL
    for item in items:
        p = ROOT / f"tools/shots/weapon_lp_{item}.txt"
        p.write_text(script(item), encoding="utf-8")
        print(p.name)
    (ROOT / "tools/shots/weapon_lp_perf.txt").write_text(perf(), encoding="utf-8")


if __name__ == "__main__":
    main()
