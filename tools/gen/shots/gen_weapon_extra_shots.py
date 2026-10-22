"""低ポリ武器の「未確認だった所」を撮る台本 tools/shots/weapon_lp_extra.txt を作る (docs/design/15 の 4.6.2)。

使い方: python tools/gen/shots/gen_weapon_extra_shots.py
撮る: ./gradlew runShots -Pscript=tools/shots/weapon_lp_extra.txt
絵: run/shots/weapon_lp_extra/shots/weapon_lp_extra/
  left_<id>_<fp|tp|tp34>_<l|r>.png   左利き (l) と右利き (r) の比べ
  foil_<id>_<fp|tp>.png / foil_world.png   エンチャントで輝くとき (メッシュをもう一度 glint の描画型で描く)
  skel_<n>.png     スケルトン (ほかの生き物) が雷翼の弓を引く三人称の引き絵 (段 0 / 1 / 2)
まとめ: python tools/sheets/weapon_extra_sheet.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
LEFT_IDS = ["whalebone_greatsword", "captain_pike", "thunder_wing_bow", "coral_shield", "raiju_claw", "ember_war_axe", "warden_glaive", "smith_hammer"]
FOIL_IDS = ["whalebone_greatsword", "thunder_wing_bow", "coral_shield"]
ENCH = '[minecraft:enchantments={levels:{"minecraft:unbreaking":1}}]'

HEAD = """# 低ポリ武器の左利き・輝き・他の生き物の弓の引き (tools/gen/shots/gen_weapon_extra_shots.py が作る)
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
cmd skyabyss cinematic stop
wait 20
cmd setblock 0 -59 3 minecraft:stone
cmd setblock 3 -59 3 minecraft:stone
"""


def main():
    o = [HEAD.rstrip("\n")]
    # 左利き: 同じ武器を右・左で
    for hand, tag in (("right", "r"), ("left", "l")):
        o.append(f"mainhand {hand}")
        for w in LEFT_IDS:
            o.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
            for view, persp, body in (("fp", "first", 0), ("tp", "front", 0), ("tp34", "front", 80)):
                o.append(f"perspective {persp}")
                o.append(f"bodyyaw {body}")
                o.append("hud on" if view == "fp" else "hud off")
                o.append("wait 10")
                o.append("chat clear")
                o.append(f"shot left_{w}_{view}_{tag}")
    o.append("mainhand right")
    o.append("bodyyaw 0")
    # 左利きで弓を引く (三人称は自分)
    o.append("mainhand left")
    o.append("cmd item replace entity @s weapon.mainhand with skyabyss:thunder_wing_bow")
    for view, persp in (("fp", "first"), ("tp", "front")):
        o.append(f"perspective {persp}")
        o.append("hud on" if view == "fp" else "hud off")
        o.append("wait 10")
        o.append("use")
        o.append("wait 24")
        o.append(f"shot leftdraw_{view}_1")
        o.append("wait 14")
        o.append(f"shot leftdraw_{view}_2")
        o.append("stopuse")
        o.append("wait 20")
    o.append("mainhand right")
    # 輝き: エンチャントつき (手・地面・額縁)
    for w in FOIL_IDS:
        o.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}{ENCH}")
        for view, persp in (("fp", "first"), ("tp", "front")):
            o.append(f"perspective {persp}")
            o.append("hud on" if view == "fp" else "hud off")
            o.append("wait 10")
            o.append("chat clear")
            o.append(f"shot foil_{w}_{view}")
        o.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
        for view, persp in (("fp", "first"), ("tp", "front")):
            o.append(f"perspective {persp}")
            o.append("hud on" if view == "fp" else "hud off")
            o.append("wait 10")
            o.append("chat clear")
            o.append(f"shot nofoil_{w}_{view}")
    o.append("cmd item replace entity @s weapon.mainhand with minecraft:air")
    o.append("cmd kill @e[type=item]")
    o.append("cmd kill @e[type=item_frame]")
    for i, w in enumerate(FOIL_IDS):
        x = i - 1
        o.append(f'cmd summon item {x} -59 4 {{Item:{{id:"skyabyss:{w}",count:1,components:{{"minecraft:enchantments":{{levels:{{"minecraft:unbreaking":1}}}}}}}},PickupDelay:32767,Age:-32768}}')
        o.append(f'cmd summon item_frame {x * 1.0 + 4} -59 4 {{Facing:3b,Item:{{id:"skyabyss:{w}",count:1,components:{{"minecraft:enchantments":{{levels:{{"minecraft:unbreaking":1}}}}}}}},Invulnerable:1b}}')
    o.append("perspective first")
    o.append("hud off")
    o.append("camera 1.5 -60 8 180 12")
    o.append("wait 20")
    o.append("chat clear")
    o.append("shot foil_world")
    o.append("cmd kill @e[type=item]")
    o.append("cmd kill @e[type=item_frame]")
    # ほかの生き物の三人称の引き絵: スケルトン (兜つき = 日光で燃えない) に雷翼の弓を持たせ、生存のプレイヤーを狙わせる
    o.append("cmd gamemode survival")
    o.append("cmd effect give @s minecraft:resistance 999 4 true")
    o.append("cmd effect give @s minecraft:instant_health 1 10 true")
    o.append('cmd summon skeleton 0 -60 -6 {PersistenceRequired:1b,attributes:[{id:"minecraft:generic.movement_speed",base:0}],HandItems:[{id:"skyabyss:thunder_wing_bow",count:1},{}],ArmorItems:[{},{},{},{id:"minecraft:iron_helmet",count:1}],HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}')
    o.append("camera 0 -60 0 180 0")
    o.append("perspective first")
    o.append("hud off")
    for n in range(40):
        o.append("wait 2")
        o.append(f"shot skel_{n:02d}")
    o.append("cmd kill @e[type=skeleton]")
    o.append("quit")
    p = ROOT / "tools/shots/weapon_lp_extra.txt"
    p.write_bytes(("\n".join(o) + "\n").encode("utf-8"))
    print(p)


if __name__ == "__main__":
    main()
