# -*- coding: utf-8 -*-
"""作業台をなくした代わりの「誂え」(2026-10-26 の声「クラフトとかもしない。出来ないようにして」)。
Mod のレシピ (もとは data/skyabyss/recipe/*.json。この表に写したので消した) を、店の品 (made = true) にする:
  - 素材 = Mod の品 (巨神の欠片・土地の敵の落とし物) だけ。バニラの材料 (麦・鉄・革…) は掘れない・育てられないので、鐘銭に換えて払う
  - 章 = もとのレシピの鍵の章 (旅の覚書・職人が教える章)。それより前は伏せて見せる
  - 店 = 鍛冶場 (旅装・砥石・穴の欠片・道具) / 薬師 (糧・薬) / 道具屋 (矢・花火)
出力: src/main/resources/data/skyabyss/shop/smithy.json (新しい店) と、apothecary.json・tool_shop.json の goods の末尾 (made の品を入れ替える)
使い方: python tools/gen/shop/gen_made_to_order.py
"""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SHOP = ROOT / "src/main/resources/data/skyabyss/shop"

# 品 → (店, 章, 数, 素材 {品: 数}, バニラの材料 {品: 数})
R = {}


def add(out, count, shop, ch, mods, vanilla=None):
    R[out] = (shop, ch, count, mods, vanilla or {})


S, A, T = "smithy", "apothecary", "tool_shop"
# 一章 (岩鯨)
add("whale_road_helmet", 1, S, 1, {"rock_shell_scrap": 3}, {"iron_ingot": 2})
add("whale_road_chestplate", 1, S, 1, {"rock_shell_scrap": 5}, {"iron_ingot": 2, "leather": 1})
add("whale_road_leggings", 1, S, 1, {"rock_shell_scrap": 2}, {"iron_ingot": 3, "leather": 2})
add("whale_road_boots", 1, S, 1, {"rock_shell_scrap": 2, "bone_chip": 2})
add("rock_whetstone", 2, S, 1, {"rock_shell_scrap": 2, "bone_chip": 1}, {"flint": 1})
add("road_hardtack", 3, A, 1, {"bone_chip": 1}, {"wheat": 3})
add("moss_salve", 2, A, 1, {"bone_chip": 1}, {"moss_block": 1, "clay_ball": 1})
add("socket_shard_stone", 1, S, 1, {"stone_whale_shard": 4, "resonance_crystal": 1})
add("socket_shard_bone", 1, S, 1, {"bone_chip": 4, "resonance_crystal": 1})
add("stone_whale_call", 1, S, 1, {"stone_whale_core": 1, "stone_whale_shard": 1}, {"bone": 1})
add("titan_waymark", 2, S, 1, {"stone_whale_shard": 1}, {"bone_block": 1, "polished_deepslate": 1})
add("resonance_forge", 1, S, 1, {"resonance_crystal": 1, "stone_whale_shard": 1}, {"bone_block": 2, "polished_deepslate": 5})
add("message_board", 1, S, 1, {}, {"paper": 3, "oak_planks": 3, "stick": 2})
# 二章 (潮殻)
add("tide_wader_helmet", 1, S, 2, {"small_pearl": 2, "salt_crystal": 3, "stone_whale_shard": 1})
add("tide_wader_chestplate", 1, S, 2, {"salt_crystal": 5, "small_pearl": 2, "stone_whale_shard": 1}, {"leather": 1})
add("tide_wader_leggings", 1, S, 2, {"small_pearl": 3, "stone_whale_shard": 1, "salt_crystal": 2}, {"leather": 2})
add("tide_wader_boots", 1, S, 2, {"salt_crystal": 2, "small_pearl": 3, "stone_whale_shard": 1})
add("salt_whetstone", 2, S, 2, {"salt_crystal": 2, "shell_chip": 1}, {"flint": 1})
add("tide_pickle", 3, A, 2, {"salt_crystal": 1}, {"dried_kelp": 3})
add("pearl_powder", 2, A, 2, {"small_pearl": 1, "salt_crystal": 1}, {"sugar": 1})
add("tide_hourglass", 1, S, 2, {"salt_crystal": 4, "small_pearl": 1}, {"glass": 2})
add("socket_shard_tide", 1, S, 2, {"tide_shell_shard": 4, "resonance_crystal": 1})
add("tide_shell_call", 1, S, 2, {"tide_shell_shard": 1}, {"nautilus_shell": 1, "heart_of_the_sea": 1})
# 三章 (雷翼)
add("peak_crossing_helmet", 1, S, 3, {"scorched_feather": 2, "static_cotton": 3, "tide_shell_shard": 1})
add("peak_crossing_chestplate", 1, S, 3, {"scorched_feather": 5, "copper_wire": 2, "tide_shell_shard": 1, "static_cotton": 1})
add("peak_crossing_leggings", 1, S, 3, {"scorched_feather": 2, "tide_shell_shard": 1, "copper_wire": 2, "static_cotton": 2})
add("peak_crossing_boots", 1, S, 3, {"scorched_feather": 3, "tide_shell_shard": 1, "copper_wire": 2})
add("thunder_whetstone", 2, S, 3, {"copper_wire": 2, "static_cotton": 1}, {"flint": 1})
add("wind_dumpling", 3, A, 3, {}, {"wheat": 3, "sugar": 1, "stick": 1})
add("lightning_oil", 2, S, 3, {"static_cotton": 1, "copper_wire": 1}, {"glass_bottle": 2})
add("wind_streamer", 1, S, 3, {"scorched_feather": 2, "static_cotton": 1, "copper_wire": 2})
add("copper_wire", 3, S, 3, {}, {"copper_ingot": 1})
add("socket_shard_wind", 1, S, 3, {"thunder_wing_shard": 4, "resonance_crystal": 1})
add("thunder_wing_call", 1, S, 3, {"thunder_wing_shard": 1}, {"feather": 1, "copper_ingot": 1})
add("minecraft:firework_rocket", 3, T, 3, {"spark_dust": 1}, {"paper": 1})
# 四章 (余燼)
add("ash_walking_helmet", 1, S, 4, {"soot_iron": 2, "ash_hemp": 3, "thunder_wing_shard": 1})
add("ash_walking_chestplate", 1, S, 4, {"soot_iron": 6, "thunder_wing_shard": 1, "ash_hemp": 2})
add("ash_walking_leggings", 1, S, 4, {"ash_hemp": 4, "thunder_wing_shard": 1, "soot_iron": 2})
add("ash_walking_boots", 1, S, 4, {"soot_iron": 4, "thunder_wing_shard": 1, "ash_hemp": 1})
add("soot_whetstone", 1, S, 4, {"soot_iron": 2}, {"flint": 1})
add("ash_cooler", 1, A, 4, {"ash_hemp": 2}, {"glass_bottle": 1})
add("ash_baked_bread", 2, A, 4, {"ash_hemp": 1}, {"wheat": 3})
add("socket_shard_ash", 1, S, 4, {"ember_shard": 4, "resonance_crystal": 1})
add("ember_call", 1, S, 4, {"ember_shard": 1}, {"iron_nugget": 1, "charcoal": 1})
add("cinder_lamp", 1, S, 4, {"cinder_husk": 1}, {"chain": 1, "iron_nugget": 2, "coal": 1})
add("warm_stone", 1, S, 4, {"ember_shard": 1}, {"stone": 4, "magma_block": 1})
add("minecraft:spectral_arrow", 2, T, 4, {"moth_dust": 1}, {"arrow": 2})
# 五章 (深淵)
add("abyss_lantern_charm", 1, S, 5, {"abyss_relic_bone_2": 1, "abyss_anchor_iron": 3}, {"soul_lantern": 1})
add("abyss_pulse_listener", 1, S, 5, {"abyss_anchor_iron": 5, "abyss_relic_ash_3": 1}, {"amethyst_shard": 1})

# バニラの材料の鐘銭 (一つあたり。掘れない・育てられない代わりに職人が仕入れる)
VALUE = dict(iron_ingot=3, leather=2, flint=1, wheat=0.5, moss_block=1, clay_ball=0.5, bone=1, bone_block=2, polished_deepslate=1, paper=0.5,
             oak_planks=0.5, stick=0.25, dried_kelp=0.5, sugar=0.5, glass=1, nautilus_shell=8, heart_of_the_sea=24, glass_bottle=1, copper_ingot=2,
             feather=0.5, iron_nugget=0.5, charcoal=1, chain=2, coal=1, stone=0.25, magma_block=3, arrow=0.5, soul_lantern=4, amethyst_shard=3)
# 誂えの手間賃 (章ごとに少し上がる)
FEE = {1: 2, 2: 3, 3: 4, 4: 5, 5: 8}


def ident(i):
    return i if ":" in i else "skyabyss:" + i


def goods(shop):
    out = []
    for item, (sh, ch, count, mods, van) in R.items():
        if sh != shop:
            continue
        coins = FEE[ch] + math.ceil(sum(VALUE[k] * n for k, n in van.items()))
        g = {"id": "made_" + item.split(":")[-1], "item": ident(item), "count": count, "coins": coins}
        if mods:
            g["materials"] = [{"item": ident(k), "count": n} for k, n in mods.items()]
        g["chapter"] = ch
        g["made"] = True
        out.append(g)
    return sorted(out, key=lambda g: (g["chapter"], g["id"]))


def main():
    # 鍛冶場の誂え (新しい店)
    smithy = {"title": "gui.skyabyss.shop.smithy", "goods": goods(S), "buys": []}
    (SHOP / "smithy.json").write_text(json.dumps(smithy, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for shop in (A, T):
        p = SHOP / f"{shop}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        made_items = {g["item"] for g in goods(shop)}
        # 前からある同じ品 (買うだけの品) は残し、誂えの品を入れ替える
        d["goods"] = [g for g in d["goods"] if not g.get("made")] + [g for g in goods(shop) if g["item"] not in
                                                                    {x["item"] for x in d["goods"] if not x.get("made")}]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(shop, len(made_items), "made goods")
    print("smithy", len(smithy["goods"]), "goods")


if __name__ == "__main__":
    main()
