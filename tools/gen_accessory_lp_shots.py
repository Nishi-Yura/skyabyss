"""低ポリの装飾品・補助の品 (手・地面・額縁・体に付けた姿) を撮る台本を作る (docs/design/15 の 4.6.3)。

使い方: python tools/gen_accessory_lp_shots.py [thunder|ember|whale|tide ...]   (省くと 4 巨神。tools/shots/acc_lp_<巨神>.txt を書く)
撮る: ./gradlew runShots -Pscript=tools/shots/acc_lp_<巨神>.txt
絵: run/shots/acc_lp_<巨神>/shots/acc_lp_<巨神>/<id>_{fp,tp}_<common|rare|legend>.png、<id>_world.png、<id>_worn_{front,back,q,side}_<階級>.png
まとめ: python tools/accessory_lp_sheet.py -> docs/review/previews/acc_lp_<巨神>.png (コンタクトシート)

階級の固定は台本の `acctier`、平絵に戻して撮るのは `accmesh off`、体に付けるのは `accequip <id>` (クライアントの枠だけ。見た目の確認用)。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# 巨神: (品の id, 実際の階級, 体に付けるか)
TITANS = {
    "thunder": [("storm_charm", "rare", False), ("storm_anklet", "rare", True), ("storm_eye_feather", "legend", True), ("gale_feather", "common", False)],
    "ember": [("kindling_charm", "rare", False), ("ember_mantle", "rare", True), ("ember_lantern", "legend", True), ("ember_horn", "common", False),
              ("cinder_lamp", "common", False)],
    "whale": [("resonance_charm", "rare", False), ("moss_talisman", "rare", True), ("stone_whale_plating", "legend", True)],
    "tide": [("tide_chime_charm", "rare", False), ("moontide_pearl", "legend", True), ("tide_watching_shell", "common", False), ("tide_pearl", "common", False)],
}
TIERS = ["common", "rare", "legend"]

HEAD = """# 低ポリの装飾品 (手・地面・額縁・体に付けた姿) を階級ごとに撮る (tools/gen_accessory_lp_shots.py が作る)
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

# 体に付けた姿の視点: (名前, 視点, 体の向き)
WORN_VIEWS = [("front", "front", 0), ("back", "back", 0), ("q", "front", 50), ("side", "front", 90)]


def script(titan):
    o = [HEAD.rstrip("\n")]
    for item, natural, worn in TITANS[titan]:
        # 手: 一人称・三人称 x 階級
        o.append("accequip")
        for tier in TIERS:
            o.append(f"acctier {tier}")
            o.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{item}")
            for view, persp in (("fp", "first"), ("tp", "front")):
                o.append(f"perspective {persp}")
                o.append("hud on" if view == "fp" else "hud off")
                o.append("bodyyaw 35" if view == "tp" else "bodyyaw 0")
                o.append("wait 10")
                o.append("chat clear")
                o.append(f"shot {item}_{view}_{tier}")
        o.append("acctier off")
        # 元の平絵 (比べる用)
        o.append("accmesh off")
        o.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{item}")
        for view, persp in (("fp", "first"), ("tp", "front")):
            o.append(f"perspective {persp}")
            o.append("hud on" if view == "fp" else "hud off")
            o.append("bodyyaw 35" if view == "tp" else "bodyyaw 0")
            o.append("wait 10")
            o.append("chat clear")
            o.append(f"shot {item}_{view}_flat")
        o.append("accmesh on")
        # 地面・額縁 (品の階級のまま)
        o.append("cmd item replace entity @s weapon.mainhand with minecraft:air")
        o.append("cmd execute if entity @e[type=item] run kill @e[type=item]")
        o.append("cmd execute if entity @e[type=item_frame] run kill @e[type=item_frame]")
        o.append(f'cmd summon item 0 -59 4 {{Item:{{id:"skyabyss:{item}",count:1}},PickupDelay:32767,Age:-32768}}')
        o.append(f'cmd summon item_frame 3 -59 4 {{Facing:3b,Item:{{id:"skyabyss:{item}",count:1}},Invulnerable:1b}}')
        o.append("perspective first")
        o.append("hud off")
        o.append("bodyyaw 0")
        o.append("camera 1.5 -60 8 180 12")
        o.append("wait 20")
        o.append("chat clear")
        o.append(f"shot {item}_world")
        o.append("camera 0 -60 8 180 0")
        # 体に付けた姿
        if worn:
            o.append(f"accequip {item}")
            o.append("hud off")
            for tier in TIERS:
                o.append(f"acctier {tier}")
                for name, persp, yaw in WORN_VIEWS:
                    o.append(f"perspective {persp}")
                    o.append(f"bodyyaw {yaw}")
                    if yaw < 90:
                        o.append("wait 8")
                    o.append("chat clear")
                    o.append(f"shot {item}_worn_{name}_{tier}")
            o.append("acctier off")
            o.append("accequip")
            o.append("bodyyaw 0")
    if titan == "ember":
        # 燠の灯のブロック (置いた姿。床・吊り)
        o.append("cmd setblock 0 -58 3 skyabyss:cinder_lamp[hanging=false]")
        o.append("cmd setblock 3 -58 3 skyabyss:cinder_lamp[hanging=true]")
        o.append("perspective first")
        o.append("camera 1.5 -60 8 180 -4")
        o.append("wait 20")
        o.append("shot cinder_lamp_block")
    o.append("quit")
    return "\n".join(o) + "\n"


def main():
    for t in sys.argv[1:] or list(TITANS):
        p = ROOT / f"tools/shots/acc_lp_{t}.txt"
        p.write_text(script(t), encoding="utf-8")
        print(p.name)


if __name__ == "__main__":
    main()
