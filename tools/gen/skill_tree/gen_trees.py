# -*- coding: utf-8 -*-
"""技の木 v2 (docs/design/48) の 5 職の木 (鍛冶師・魔法使い・弓使い・暗殺者・呪術師) と、その節の文 (ja/en) を書く。
戦士の木 (warrior.json) は手で書いたものをそのまま使う。

  python tools/gen/skill_tree/gen_trees.py

出力: src/main/resources/assets/skyabyss/skill_tree/<職>.json と、lang の node.skyabyss.<職>.* / arch.skyabyss.<職>.*。
格子: 幅 9 (x 0〜8)、幹は x 4 の 0〜3 行、型は左 (0〜2)・中 (3〜5)・右 (6〜8) の 4〜10 行。節の点は使う技 2 (幹の根は 1)・常に効く技 2・段 1・
刻印 1〜2・基礎 1 (+4)・閾値 1・常に効く数 1〜2・型の要 3 (型の点 20)。"""
import io
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/skill_tree"
LANG = ROOT / "src/main/resources/assets/skyabyss/lang"


class Tree:
    def __init__(self, job, archetypes):
        self.job = job
        self.archetypes = archetypes
        self.nodes = []
        self.names = {}  # id -> (ja, en, ja_effect, en_effect)

    def n(self, id, kind, x, y, cost=1, req=None, arch=None, **extra):
        node = {"id": id, "kind": kind, "cost": cost, "x": x, "y": y}
        if arch:
            node["arch"] = arch
        if req:
            node["requires"] = req if isinstance(req, list) else [req]
        node.update(extra)
        self.nodes.append(node)
        return self

    # 使う技 / 常に効く技 / 要
    def active(self, skill, x, y, cost=2, req=None, arch=None):
        return self.n(skill, "active", x, y, cost, req, arch, skill=skill)

    def passive(self, skill, x, y, req=None, arch=None):
        return self.n(skill, "passive", x, y, 2, req, arch, skill=skill)

    def rank(self, skill, k, x, y, req=None, arch=None, cost=1):
        return self.n("%s_%d" % (skill, k), "rank", x, y, cost, req, arch, skill=skill)

    def rune(self, id, skill, r, x, y, req=None, arch=None, cost=1):
        return self.n(id, "rune", x, y, cost, req, arch, skill=skill, rune=r)

    def stat(self, id, stat, x, y, req=None, arch=None):
        return self.n(id, "stat", x, y, 1, req, arch, stat=stat, amount=4)

    def eff(self, id, x, y, effect, ja, en, ja_e, en_e, req=None, arch=None, cost=1, excludes=None, arch_points=0):
        extra = {"effect": effect}
        if excludes:
            extra["excludes"] = excludes if isinstance(excludes, list) else [excludes]
        if arch_points:
            extra["arch_points"] = arch_points
        self.names[id] = (ja, en, ja_e, en_e)
        return self.n(id, "effect", x, y, cost, req, arch, **extra)

    def thr(self, id, x, y, stat, at, effect, ja, en, ja_e, en_e, req=None, arch=None):
        self.names[id] = (ja, en, ja_e, en_e)
        return self.n(id, "threshold", x, y, 1, req, arch, stat=stat, at=at, effect=effect)

    def keystone(self, skill, x, y, req, arch):
        return self.n(skill, "keystone", x, y, 3, req, arch, skill=skill, arch_points=20)

    def trait(self, keystone, x, y, req, arch):
        return self.n(keystone, "trait", x, y, 3, req, arch, keystone=keystone, arch_points=20)

    def check(self):
        ids = set()
        cells = set()
        for nd in self.nodes:
            assert nd["id"] not in ids, (self.job, "dup", nd["id"])
            ids.add(nd["id"])
            assert (nd["x"], nd["y"]) not in cells, (self.job, "cell", nd["id"])
            cells.add((nd["x"], nd["y"]))
        for nd in self.nodes:
            for r in nd.get("requires", []) + nd.get("excludes", []):
                assert r in ids, (self.job, nd["id"], "unknown", r)

    def write(self):
        self.check()
        OUT.mkdir(parents=True, exist_ok=True)
        lines = ['{', '  "archetypes": %s,' % json.dumps(self.archetypes), '  "nodes": [']
        for i, nd in enumerate(self.nodes):
            lines.append("    " + json.dumps(nd, ensure_ascii=False) + ("," if i < len(self.nodes) - 1 else ""))
        lines += ["  ]", "}", ""]
        io.open(OUT / (self.job + ".json"), "w", encoding="utf-8", newline="\n").write("\n".join(lines))


ARCH_NAMES = {
    "assassin": {"shadow": ("影の刺客", "Shadow Stalker"), "dance": ("風の舞い手", "Wind Dancer"), "lone": ("孤風", "Lone Wind")},
    "shaman": {"ward": ("守りの唄", "Warding Song"), "ebb": ("引き潮", "Ebb Tide"), "curse": ("呪い", "Curse")},
    "smith": {"ember": ("熾火の槌", "Ember Hammer"), "temper": ("焼き入れ", "Tempering"), "ash": ("灰散らし", "Ash Scatter")},
    "archer": {"pierce": ("雲穿ち", "Cloud Piercer"), "trap": ("骨の罠", "Bone Trap"), "rain": ("天の驟雨", "Sky Rain")},
    "mage": {"thunder": ("雷", "Thunder"), "fire": ("火", "Fire"), "tide": ("潮", "Tide")},
}


# ---------------------------------------------------------------- 暗殺者 (風読み)
def assassin():
    t = Tree("assassin", ["shadow", "dance", "lone"])
    t.active("lunge", 4, 0, cost=1)
    t.stat("agi_a", "agility", 3, 1, "lunge").stat("str_a", "strength", 5, 1, "lunge")
    t.passive("nimble", 3, 2, "agi_a").passive("foresight", 5, 2, "str_a").active("backstep", 4, 2, req=["agi_a", "str_a"])
    t.stat("agi_b", "agility", 2, 3, ["nimble", "foresight"]).stat("agi_c", "agility", 4, 3, "backstep").stat("str_b", "strength", 6, 3, ["nimble", "foresight"])
    a = "shadow"
    t.rank("nimble", 2, 0, 4, "agi_b", a)
    t.active("mist_veil", 1, 4, req="agi_b", arch=a)
    t.active("twin_fang", 2, 4, req="agi_b", arch=a)
    t.eff("shadow_step", 0, 5, {"move": 0.04}, "影の歩み", "Shadow Step", "移動 +4%", "Movement +4%", ["mist_veil", "nimble_2"], a)
    t.stat("agi_d", "agility", 1, 5, "mist_veil", a)
    t.eff("veil_edge", 2, 5, {"attack": 0.06}, "霧の刃", "Veiled Edge", "与える傷 +6%", "Damage dealt +6%", ["mist_veil", "twin_fang"], a)
    t.stat("agi_e", "agility", 0, 6, "shadow_step", a)
    t.thr("backstab", 1, 6, "agility", 20, {"attack": 0.05}, "背刺し", "Backstab", "与える傷 +5%", "Damage dealt +5%", "agi_d", a)
    t.stat("agi_f", "agility", 2, 6, "veil_edge", a)
    t.eff("quiet_blade", 0, 7, {"skill_power": 0.05}, "静かな刃", "Quiet Blade", "技の力 +5%", "Skill power +5%", "agi_e", a)
    t.rank("mist_veil", 2, 1, 7, "backstab", a)
    t.eff("thin_air", 2, 7, {"damage_taken": -0.05}, "薄い空気", "Thin Air", "受ける傷 −5%", "Damage taken −5%", "agi_f", a)
    t.stat("agi_g", "agility", 0, 8, "quiet_blade", a)
    t.thr("night_eye", 1, 8, "agility", 36, {"attack": 0.08}, "夜の目", "Night Eye", "与える傷 +8%", "Damage dealt +8%", "mist_veil_2", a)
    t.stat("agi_h", "agility", 2, 8, "thin_air", a)
    t.rank("nimble", 3, 0, 9, "agi_g", a)
    t.trait("glass_blade", 1, 9, ["agi_g", "night_eye", "agi_h"], a)
    t.rank("twin_fang", 2, 2, 9, "agi_h", a)
    t.eff("shadow_thirst", 0, 10, {"lifesteal": 0.03}, "影の渇き", "Shadow Thirst", "近接で与えた傷の 3% を吸う（1 回 2 まで）", "Heal 3% of melee damage dealt (up to 2 per hit)", "glass_blade", a, cost=2)
    t.eff("fade", 2, 10, {"cooldown": -0.05, "move": 0.04}, "消え際", "Fade", "技の待ち −5%、移動 +4%", "Skill cooldowns −5%, movement +4%", "glass_blade", a, cost=2)
    a = "dance"
    t.rune("pass_through", "lunge", 1, 3, 4, "agi_c", a).rune("chain_lunge", "lunge", 2, 4, 4, "agi_c", a).rune("pierce_through", "lunge", 3, 5, 4, "agi_c", a, cost=2)
    t.eff("light_step", 3, 5, {"move": 0.05}, "軽い足", "Light Step", "移動 +5%", "Movement +5%", ["pass_through", "chain_lunge"], a)
    t.stat("agi_i", "agility", 4, 5, ["chain_lunge", "pierce_through"], a)
    t.rank("lunge", 2, 5, 5, "pierce_through", a)
    t.rune("return_fire", "backstep", 1, 3, 6, "light_step", a).rune("long_leap", "backstep", 2, 4, 6, "agi_i", a).rune("wind_remnant", "backstep", 3, 5, 6, "lunge_2", a, cost=2)
    t.stat("agi_j", "agility", 3, 7, ["return_fire", "long_leap"], a)
    t.rank("backstep", 2, 4, 7, ["long_leap", "wind_remnant"], a)
    t.eff("dance_wind", 5, 7, {"cooldown": -0.06}, "舞いの風", "Dancing Wind", "技の待ち −6%", "Skill cooldowns −6%", "wind_remnant", a)
    t.thr("quick_feet", 3, 8, "agility", 24, {"attack_speed": 0.08}, "速い足", "Quick Feet", "攻撃速度 +8%", "Attack speed +8%", "agi_j", a)
    t.stat("agi_k", "agility", 4, 8, "backstep_2", a).stat("agi_l", "agility", 5, 8, "dance_wind", a)
    t.trait("wind_dance", 4, 9, ["quick_feet", "agi_k", "agi_l"], a)
    t.eff("endless_step", 3, 10, {"mana_cost": -0.08}, "尽きない足", "Endless Step", "技のマナの費用 −8%", "Skill mana cost −8%", "wind_dance", a, cost=2)
    t.thr("storm_feet", 5, 10, "agility", 40, {"move": 0.06, "attack_speed": 0.06}, "嵐の足", "Storm Feet", "移動 +6%、攻撃速度 +6%", "Movement +6%, attack speed +6%", "wind_dance", a)
    a = "lone"
    t.rank("foresight", 2, 6, 4, "str_b", a)
    t.active("wind_rend", 7, 4, req="str_b", arch=a)
    t.rune("back_take", "wind_rend", 1, 6, 5, ["wind_rend", "foresight_2"], a).rune("bleed", "wind_rend", 2, 7, 5, "wind_rend", a).rune("companion_wind", "wind_rend", 3, 8, 5, "wind_rend", a, cost=2)
    t.stat("str_c", "strength", 6, 6, ["back_take", "bleed"], a)
    t.rank("wind_rend", 2, 7, 6, "bleed", a)
    t.stat("str_d", "strength", 8, 6, "companion_wind", a)
    t.eff("lone_edge", 6, 7, {"attack": 0.06}, "孤刃", "Lone Edge", "与える傷 +6%", "Damage dealt +6%", "str_c", a)
    t.eff("keen_wind", 7, 7, {"skill_power": 0.05}, "鋭い風", "Keen Wind", "技の力 +5%", "Skill power +5%", "wind_rend_2", a)
    t.stat("str_e", "strength", 8, 7, "str_d", a)
    t.thr("heavy_hand", 6, 8, "strength", 20, {"attack": 0.06}, "重い手", "Heavy Hand", "与える傷 +6%", "Damage dealt +6%", "lone_edge", a)
    t.stat("str_f", "strength", 7, 8, "keen_wind", a)
    t.eff("cold_wind", 8, 8, {"damage_taken": -0.04}, "冷たい風", "Cold Wind", "受ける傷 −4%", "Damage taken −4%", "str_e", a)
    t.keystone("lone_wind", 7, 9, ["heavy_hand", "str_f", "cold_wind"], a)
    t.rank("foresight", 3, 8, 9, "cold_wind", a)
    t.eff("last_breath", 6, 10, {"low_hp_armor": 4}, "最後の息", "Last Breath", "体力が少ないとき、防具 +4", "Armor +4 while low on health", "lone_wind", a, cost=2)
    t.thr("red_wind", 8, 10, "strength", 24, {"lifesteal": 0.03, "attack": 0.04}, "赤い風", "Red Wind", "近接で与えた傷の 3% を吸う、与える傷 +4%", "Heal 3% of melee damage dealt, damage dealt +4%", "lone_wind", a)
    return t


# ---------------------------------------------------------------- 呪術師 (潮歌い)
def shaman():
    t = Tree("shaman", ["ward", "ebb", "curse"])
    t.active("ward_song", 4, 0, cost=1)
    t.stat("spi_a", "spirit", 3, 1, "ward_song").stat("vit_a", "vitality", 5, 1, "ward_song")
    t.passive("calm_heart", 3, 2, "spi_a").passive("ripple", 5, 2, "vit_a").active("ebb_tide", 4, 2, req=["spi_a", "vit_a"])
    t.stat("spi_b", "spirit", 2, 3, ["calm_heart", "ripple"]).stat("spi_c", "spirit", 4, 3, "ebb_tide").stat("vit_b", "vitality", 6, 3, ["calm_heart", "ripple"])
    a = "ward"
    t.rune("thick_song", "ward_song", 1, 0, 4, "spi_b", a).rune("wide_song", "ward_song", 2, 1, 4, "spi_b", a).rune("cycling_song", "ward_song", 3, 2, 4, "spi_b", a, cost=2)
    t.rank("ward_song", 2, 0, 5, ["thick_song", "wide_song"], a)
    t.stat("vit_c", "vitality", 1, 5, "wide_song", a)
    t.active("lamp_charm", 2, 5, req=["cycling_song", "wide_song"], arch=a)
    t.rank("ward_song", 3, 0, 6, "ward_song_2", a)
    t.eff("steady_voice", 1, 6, {"regen": 0.1}, "揺るがぬ声", "Steady Voice", "マナの回復 +10%", "Mana regeneration +10%", "vit_c", a)
    t.rank("lamp_charm", 2, 2, 6, "lamp_charm", a)
    t.stat("vit_d", "vitality", 0, 7, "ward_song_3", a)
    t.thr("deep_breath", 1, 7, "vitality", 20, {"damage_taken": -0.05}, "深い息", "Deep Breath", "受ける傷 −5%", "Damage taken −5%", "steady_voice", a)
    t.eff("warm_lamp", 2, 7, {"low_hp_armor": 4}, "温かい灯", "Warm Lamp", "体力が少ないとき、防具 +4", "Armor +4 while low on health", "lamp_charm_2", a)
    t.stat("vit_e", "vitality", 0, 8, "vit_d", a).stat("spi_d", "spirit", 1, 8, "deep_breath", a).stat("vit_f", "vitality", 2, 8, "warm_lamp", a)
    t.trait("full_tide", 1, 9, ["vit_e", "spi_d", "vit_f"], a)
    t.thr("tide_skin", 0, 10, "vitality", 24, {"armor": 2, "knockback": 0.1}, "潮の肌", "Tide Skin", "防具 +2、ノックバック耐性 +10%", "Armor +2, knockback resistance +10%", "full_tide", a)
    t.eff("long_song", 2, 10, {"cooldown": -0.06}, "長い唄", "Long Song", "技の待ち −6%", "Skill cooldowns −6%", "full_tide", a, cost=2)
    a = "ebb"
    t.rune("strong_tide", "ebb_tide", 1, 3, 4, "spi_c", a).rune("whirl", "ebb_tide", 2, 4, 4, "spi_c", a).rune("push_back", "ebb_tide", 3, 5, 4, "spi_c", a, cost=2)
    t.rank("ebb_tide", 2, 3, 5, ["strong_tide", "whirl"], a)
    t.stat("spi_e", "spirit", 4, 5, "whirl", a)
    t.active("cycle_song", 5, 5, req=["push_back", "whirl"], arch=a)
    t.eff("undertow", 3, 6, {"skill_power": 0.05}, "底の流れ", "Undertow", "技の力 +5%", "Skill power +5%", "ebb_tide_2", a)
    t.stat("spi_f", "spirit", 4, 6, "spi_e", a)
    t.rank("cycle_song", 2, 5, 6, "cycle_song", a)
    t.stat("spi_g", "spirit", 3, 7, "undertow", a)
    t.thr("tide_mind", 4, 7, "spirit", 24, {"mana": 0.1}, "潮の心", "Tide Mind", "最大マナ +10%", "Max mana +10%", "spi_f", a)
    t.eff("wide_cycle", 5, 7, {"regen": 0.08}, "広い巡り", "Wide Cycle", "マナの回復 +8%", "Mana regeneration +8%", "cycle_song_2", a)
    t.eff("cold_current", 3, 8, {"attack": 0.05}, "冷たい流れ", "Cold Current", "与える傷 +5%", "Damage dealt +5%", "spi_g", a)
    t.stat("spi_h", "spirit", 4, 8, "tide_mind", a).stat("spi_i", "spirit", 5, 8, "wide_cycle", a)
    t.keystone("chorus_voice", 4, 9, ["cold_current", "spi_h", "spi_i"], a)
    t.eff("flood", 3, 10, {"skill_power": 0.05, "mana_cost": 0.05}, "満ち潮", "Flood", "技の力 +5%、技のマナの費用 +5%", "Skill power +5%, skill mana cost +5%", "chorus_voice", a, cost=2)
    t.thr("still_water", 5, 10, "spirit", 40, {"cooldown": -0.06, "regen": 0.06}, "静かな水", "Still Water", "技の待ち −6%、マナの回復 +6%", "Skill cooldowns −6%, mana regeneration +6%", "chorus_voice", a)
    a = "curse"
    t.rank("calm_heart", 2, 6, 4, "vit_b", a)
    t.active("curse_mark", 7, 4, req="vit_b", arch=a)
    t.rank("ripple", 2, 8, 4, "vit_b", a)
    t.eff("bitter_salt", 6, 5, {"attack": 0.05}, "苦い塩", "Bitter Salt", "与える傷 +5%", "Damage dealt +5%", ["curse_mark", "calm_heart_2"], a)
    t.stat("spi_j", "spirit", 7, 5, "curse_mark", a)
    t.rank("curse_mark", 2, 8, 5, ["curse_mark", "ripple_2"], a)
    t.stat("spi_k", "spirit", 6, 6, "bitter_salt", a)
    t.thr("hex_eye", 7, 6, "spirit", 20, {"skill_power": 0.06}, "呪いの目", "Hex Eye", "技の力 +6%", "Skill power +6%", "spi_j", a)
    t.eff("drain", 8, 6, {"lifesteal": 0.03}, "吸い", "Drain", "近接で与えた傷の 3% を吸う（1 回 2 まで）", "Heal 3% of melee damage dealt (up to 2 per hit)", "curse_mark_2", a)
    t.eff("salt_wound", 6, 7, {"big_target": 0.1}, "塩の傷", "Salted Wound", "大きな相手への技の傷 +10%", "Skill damage to large foes +10%", "spi_k", a)
    t.stat("spi_l", "spirit", 7, 7, "hex_eye", a).stat("spi_m", "spirit", 8, 7, "drain", a)
    t.stat("spi_n", "spirit", 6, 8, "salt_wound", a)
    t.eff("dark_tide", 7, 8, {"attack": 0.05, "damage_taken": 0.03}, "黒い潮", "Dark Tide", "与える傷 +5%、受ける傷 +3%（「冷えた心」と排他）", "Damage dealt +5%, damage taken +3% (excludes Cold Heart)", ["spi_l", "spi_m"], a, excludes="cold_heart")
    t.eff("cold_heart", 8, 8, {"damage_taken": -0.04}, "冷えた心", "Cold Heart", "受ける傷 −4%（「黒い潮」と排他）", "Damage taken −4% (excludes Dark Tide)", "spi_m", a, excludes="dark_tide")
    t.rank("calm_heart", 3, 6, 9, "spi_n", a)
    t.eff("hex_chant", 7, 9, {"skill_power": 0.06, "mana_cost": 0.06}, "呪いの詠唱", "Hex Chant", "技の力 +6%、技のマナの費用 +6%", "Skill power +6%, skill mana cost +6%", ["spi_n", "dark_tide", "cold_heart"], a, cost=2, arch_points=20)
    t.rank("ripple", 3, 8, 9, ["cold_heart", "dark_tide"], a)
    t.thr("deep_curse", 6, 10, "spirit", 36, {"attack": 0.06, "skill_power": 0.04}, "深い呪い", "Deep Curse", "与える傷 +6%、技の力 +4%", "Damage dealt +6%, skill power +4%", "hex_chant", a)
    t.eff("black_salt", 8, 10, {"lifesteal": 0.02, "attack": 0.03}, "黒い塩", "Black Salt", "近接で与えた傷の 2% を吸う、与える傷 +3%", "Heal 2% of melee damage dealt, damage dealt +3%", "hex_chant", a, cost=2)
    return t


# ---------------------------------------------------------------- 鍛冶師 (灰の鍛え人)
def smith():
    t = Tree("smith", ["ember", "temper", "ash"])
    t.active("temper", 4, 0, cost=1)
    t.stat("str_a", "strength", 3, 1, "temper").stat("spi_a", "spirit", 5, 1, "temper")
    t.passive("ember_heart", 3, 2, "str_a").passive("forge_memory", 5, 2, "spi_a").active("ember_hammer", 4, 2, req=["str_a", "spi_a"])
    t.stat("str_b", "strength", 2, 3, ["ember_heart", "forge_memory"]).stat("str_c", "strength", 4, 3, "ember_hammer").stat("spi_b", "spirit", 6, 3, ["ember_heart", "forge_memory"])
    a = "ember"
    t.rune("long_fire", "ember_hammer", 1, 0, 4, "str_b", a).rune("double_strike", "ember_hammer", 2, 1, 4, "str_b", a).rune("fire_pillar", "ember_hammer", 3, 2, 4, "str_b", a, cost=2)
    t.rank("ember_hammer", 2, 0, 5, ["long_fire", "double_strike"], a)
    t.stat("str_d", "strength", 1, 5, "double_strike", a)
    t.eff("hot_iron", 2, 5, {"attack": 0.05}, "熱い鉄", "Hot Iron", "与える傷 +5%", "Damage dealt +5%", "fire_pillar", a)
    t.stat("str_e", "strength", 0, 6, "ember_hammer_2", a)
    t.thr("heavy_arm", 1, 6, "strength", 20, {"attack_speed": 0.06}, "重い腕", "Heavy Arm", "攻撃速度 +6%", "Attack speed +6%", "str_d", a)
    t.eff("fire_skin", 2, 6, {"fire_resist": 0.2}, "火の肌", "Fire Skin", "火の傷 −20%", "Fire damage −20%", "hot_iron", a)
    t.eff("anvil_weight", 0, 7, {"knockback": 0.15}, "金床の重み", "Anvil Weight", "ノックバック耐性 +15%", "Knockback resistance +15%", "str_e", a)
    t.stat("str_f", "strength", 1, 7, "heavy_arm", a).stat("spi_c", "spirit", 2, 7, "fire_skin", a)
    t.stat("str_g", "strength", 0, 8, "anvil_weight", a)
    t.eff("big_hammer", 1, 8, {"big_target": 0.1}, "大槌", "Big Hammer", "大きな相手への技の傷 +10%", "Skill damage to large foes +10%", "str_f", a)
    t.eff("cinder", 2, 8, {"skill_power": 0.05}, "燠", "Cinder", "技の力 +5%", "Skill power +5%", "spi_c", a)
    t.trait("ash_pact", 1, 9, ["str_g", "big_hammer", "cinder"], a)
    t.thr("mountain_arm", 0, 10, "strength", 40, {"attack": 0.06, "big_target": 0.06}, "山の腕", "Mountain Arm", "与える傷 +6%、大きな相手への技の傷 +6%", "Damage dealt +6%, skill damage to large foes +6%", "ash_pact", a)
    t.eff("slow_burn", 2, 10, {"regen": 0.08}, "とろ火", "Slow Burn", "マナの回復 +8%", "Mana regeneration +8%", "ash_pact", a, cost=2)
    a = "temper"
    t.rank("temper", 2, 3, 4, "str_c", a)
    t.active("forge_stomp", 4, 4, req="str_c", arch=a)
    t.stat("str_h", "strength", 5, 4, "str_c", a)
    t.eff("hard_edge", 3, 5, {"attack": 0.04}, "硬い刃", "Hard Edge", "与える傷 +4%", "Damage dealt +4%", "temper_2", a)
    t.rank("forge_stomp", 2, 4, 5, "forge_stomp", a)
    t.stat("str_i", "strength", 5, 5, "str_h", a)
    t.rank("temper", 3, 3, 6, "hard_edge", a)
    t.eff("wide_stomp", 4, 6, {"knockback": 0.1}, "広い踏み", "Wide Stomp", "ノックバック耐性 +10%", "Knockback resistance +10%", "forge_stomp_2", a)
    t.thr("iron_will", 5, 6, "strength", 24, {"damage_taken": -0.05}, "鉄の意志", "Iron Will", "受ける傷 −5%", "Damage taken −5%", "str_i", a)
    t.stat("spi_d", "spirit", 3, 7, "temper_3", a).stat("str_j", "strength", 4, 7, "wide_stomp", a).stat("str_k", "strength", 5, 7, "iron_will", a)
    t.eff("keen_temper", 3, 8, {"skill_power": 0.06}, "鋭い焼き", "Keen Temper", "技の力 +6%", "Skill power +6%", "spi_d", a)
    t.eff("thick_plate", 4, 8, {"armor": 2}, "厚い板金", "Thick Plate", "防具 +2", "Armor +2", "str_j", a)
    t.eff("quench", 5, 8, {"fire_resist": 0.15, "damage_taken": -0.03}, "焼き戻し", "Quench", "火の傷 −15%、受ける傷 −3%", "Fire damage −15%, damage taken −3%", "str_k", a)
    t.rank("forge_memory", 2, 3, 9, "keen_temper", a)
    t.keystone("smith_eye", 4, 9, ["keen_temper", "thick_plate", "quench"], a)
    t.rank("forge_memory", 3, 5, 9, "quench", a)
    t.eff("master_eye", 3, 10, {"attack": 0.05, "cooldown": -0.05}, "匠の目", "Master's Eye", "与える傷 +5%、技の待ち −5%", "Damage dealt +5%, skill cooldowns −5%", "smith_eye", a, cost=2)
    t.thr("steel_body", 5, 10, "strength", 40, {"armor": 2, "knockback": 0.15}, "鋼の体", "Steel Body", "防具 +2、ノックバック耐性 +15%", "Armor +2, knockback resistance +15%", "smith_eye", a)
    a = "ash"
    t.rank("ember_heart", 2, 6, 4, "spi_b", a)
    t.active("ash_scatter", 7, 4, req="spi_b", arch=a)
    t.active("spark_burst", 8, 4, req="spi_b", arch=a)
    t.stat("spi_e", "spirit", 6, 5, ["ash_scatter", "ember_heart_2"], a)
    t.rank("ash_scatter", 2, 7, 5, "ash_scatter", a)
    t.eff("grey_cloud", 8, 5, {"damage_taken": -0.04}, "灰色の雲", "Grey Cloud", "受ける傷 −4%", "Damage taken −4%", ["ash_scatter", "spark_burst"], a)
    t.eff("hot_ash", 6, 6, {"skill_power": 0.05}, "熱い灰", "Hot Ash", "技の力 +5%", "Skill power +5%", "spi_e", a)
    t.stat("spi_f", "spirit", 7, 6, "ash_scatter_2", a).stat("spi_g", "spirit", 8, 6, "grey_cloud", a)
    t.thr("ash_mind", 6, 7, "spirit", 20, {"mana": 0.1}, "灰の心", "Ash Mind", "最大マナ +10%", "Max mana +10%", "hot_ash", a)
    t.eff("choking_ash", 7, 7, {"attack": 0.05}, "むせる灰", "Choking Ash", "与える傷 +5%", "Damage dealt +5%", "spi_f", a)
    t.eff("ash_veil", 8, 7, {"move": 0.04}, "灰の帳", "Ash Veil", "移動 +4%", "Movement +4%", "spi_g", a)
    t.stat("spi_h", "spirit", 6, 8, "ash_mind", a).stat("spi_i", "spirit", 7, 8, "choking_ash", a).stat("spi_j", "spirit", 8, 8, "ash_veil", a)
    t.rank("ember_heart", 3, 6, 9, "spi_h", a)
    t.rank("spark_burst", 2, 8, 9, "spi_j", a)
    t.eff("ember_lung", 7, 9, {"regen": 0.1, "skill_power": 0.04}, "熾火の肺", "Ember Lung", "マナの回復 +10%、技の力 +4%", "Mana regeneration +10%, skill power +4%", ["spi_h", "spi_i", "spi_j"], a, cost=2, arch_points=20)
    t.thr("white_ash", 6, 10, "spirit", 36, {"skill_power": 0.06, "cooldown": -0.04}, "白い灰", "White Ash", "技の力 +6%、技の待ち −4%", "Skill power +6%, skill cooldowns −4%", "ember_lung", a)
    t.eff("ash_heart", 8, 10, {"low_hp_armor": 4, "fire_resist": 0.1}, "灰の心臓", "Ash Heart", "体力が少ないとき防具 +4、火の傷 −10%", "Armor +4 while low on health, fire damage −10%", "ember_lung", a, cost=2)
    return t


# ---------------------------------------------------------------- 弓使い (骨の射手)
def archer():
    t = Tree("archer", ["pierce", "trap", "rain"])
    t.active("cloud_pierce", 4, 0, cost=1)
    t.stat("agi_a", "agility", 3, 1, "cloud_pierce").stat("str_a", "strength", 5, 1, "cloud_pierce")
    t.passive("far_shot", 3, 2, "agi_a").passive("hawk_eye", 5, 2, "str_a").active("archer_leap", 4, 2, req=["agi_a", "str_a"])
    t.stat("agi_b", "agility", 2, 3, ["far_shot", "hawk_eye"]).stat("agi_c", "agility", 4, 3, "archer_leap").stat("str_b", "strength", 6, 3, ["far_shot", "hawk_eye"])
    a = "pierce"
    t.rune("thin_arrow", "cloud_pierce", 1, 0, 4, "agi_b", a).rune("heavy_arrow", "cloud_pierce", 2, 1, 4, "agi_b", a).rune("lightning", "cloud_pierce", 3, 2, 4, "agi_b", a, cost=2)
    t.rank("cloud_pierce", 2, 0, 5, ["thin_arrow", "heavy_arrow"], a)
    t.stat("agi_d", "agility", 1, 5, "heavy_arrow", a)
    t.eff("long_draw", 2, 5, {"projectile": 0.06}, "長い引き", "Long Draw", "矢の傷 +6%", "Projectile damage +6%", "lightning", a)
    t.rank("cloud_pierce", 3, 0, 6, "cloud_pierce_2", a)
    t.thr("steady_hand", 1, 6, "agility", 20, {"projectile": 0.05}, "揺るがぬ手", "Steady Hand", "矢の傷 +5%", "Projectile damage +5%", "agi_d", a)
    t.stat("agi_e", "agility", 2, 6, "long_draw", a)
    t.stat("agi_f", "agility", 0, 7, "cloud_pierce_3", a)
    t.eff("sharp_tip", 1, 7, {"big_target": 0.1}, "鋭い鏃", "Sharp Tip", "大きな相手への技の傷 +10%", "Skill damage to large foes +10%", "steady_hand", a)
    t.eff("quick_nock", 2, 7, {"attack_speed": 0.06}, "速い番え", "Quick Nock", "攻撃速度 +6%", "Attack speed +6%", "agi_e", a)
    t.stat("agi_g", "agility", 0, 8, "agi_f", a).stat("agi_h", "agility", 1, 8, "sharp_tip", a).stat("agi_i", "agility", 2, 8, "quick_nock", a)
    t.trait("lone_string", 1, 9, ["agi_g", "agi_h", "agi_i"], a)
    t.thr("eagle_eye", 0, 10, "agility", 40, {"projectile": 0.08}, "鷲の目", "Eagle Eye", "矢の傷 +8%", "Projectile damage +8%", "lone_string", a)
    t.eff("last_arrow", 2, 10, {"skill_power": 0.05, "cooldown": -0.04}, "最後の矢", "Last Arrow", "技の力 +5%、技の待ち −4%", "Skill power +5%, skill cooldowns −4%", "lone_string", a, cost=2)
    a = "trap"
    t.active("snare_shot", 3, 4, req="agi_c", arch=a)
    t.active("bone_trap", 4, 4, req="agi_c", arch=a)
    t.stat("str_c", "strength", 3, 5, ["bone_trap", "snare_shot"], a)
    t.rank("bone_trap", 2, 4, 5, "bone_trap", a)
    t.eff("light_pack", 5, 5, {"move": 0.04}, "軽い荷", "Light Pack", "移動 +4%", "Movement +4%", "bone_trap", a)
    t.eff("trap_wire", 3, 6, {"knockback": 0.1}, "罠の糸", "Trap Wire", "ノックバック耐性 +10%", "Knockback resistance +10%", "str_c", a)
    t.stat("agi_j", "agility", 4, 6, "bone_trap_2", a)
    t.rank("archer_leap", 2, 5, 6, "light_pack", a)
    t.stat("str_d", "strength", 3, 7, "trap_wire", a)
    t.thr("patient", 4, 7, "agility", 24, {"damage_taken_still": -0.08}, "辛抱", "Patience", "1 秒以上動かずにいる間、受ける傷 −8%", "After standing still for 1 s, damage taken −8%", "agi_j", a)
    t.eff("soft_landing", 5, 7, {"damage_taken": -0.04}, "柔らかい着地", "Soft Landing", "受ける傷 −4%", "Damage taken −4%", "archer_leap_2", a)
    t.eff("hunter_mark", 3, 8, {"attack": 0.05}, "狩人の印", "Hunter's Mark", "与える傷 +5%", "Damage dealt +5%", "str_d", a)
    t.stat("agi_k", "agility", 4, 8, "patient", a).stat("agi_l", "agility", 5, 8, "soft_landing", a)
    t.rank("snare_shot", 2, 3, 9, "hunter_mark", a)
    t.keystone("still_string", 4, 9, ["hunter_mark", "agi_k", "agi_l"], a)
    t.eff("bone_cage", 3, 10, {"skill_power": 0.06}, "骨の檻", "Bone Cage", "技の力 +6%", "Skill power +6%", "still_string", a, cost=2)
    t.thr("still_air", 5, 10, "agility", 36, {"damage_taken_still": -0.06, "projectile": 0.04}, "止まった空気", "Still Air", "動かずにいる間、受ける傷 −6%。矢の傷 +4%", "While standing still, damage taken −6%; projectile damage +4%", "still_string", a)
    a = "rain"
    t.rank("far_shot", 2, 6, 4, "str_b", a)
    t.active("sky_rain", 7, 4, req="str_b", arch=a)
    t.rank("hawk_eye", 2, 8, 4, "str_b", a)
    t.stat("str_e", "strength", 6, 5, ["sky_rain", "far_shot_2"], a)
    t.rank("sky_rain", 2, 7, 5, "sky_rain", a)
    t.eff("wide_sky", 8, 5, {"skill_power": 0.05}, "広い空", "Wide Sky", "技の力 +5%", "Skill power +5%", ["sky_rain", "hawk_eye_2"], a)
    t.eff("heavy_rain", 6, 6, {"attack": 0.04}, "重い雨", "Heavy Rain", "与える傷 +4%", "Damage dealt +4%", "str_e", a)
    t.stat("str_f", "strength", 7, 6, "sky_rain_2", a).stat("str_g", "strength", 8, 6, "wide_sky", a)
    t.thr("strong_arm", 6, 7, "strength", 20, {"projectile": 0.06}, "強い腕", "Strong Arm", "矢の傷 +6%", "Projectile damage +6%", "heavy_rain", a)
    t.stat("str_h", "strength", 7, 7, "str_f", a)
    t.eff("quiver", 8, 7, {"mana_cost": -0.06}, "深い矢筒", "Deep Quiver", "技のマナの費用 −6%", "Skill mana cost −6%", "str_g", a)
    t.stat("str_i", "strength", 6, 8, "strong_arm", a)
    t.eff("cloud_break", 7, 8, {"big_target": 0.1}, "雲割り", "Cloud Break", "大きな相手への技の傷 +10%", "Skill damage to large foes +10%", "str_h", a)
    t.stat("str_j", "strength", 8, 8, "quiver", a)
    t.rank("far_shot", 3, 6, 9, "str_i", a)
    t.eff("storm_caller", 7, 9, {"skill_power": 0.06, "cooldown": -0.04}, "嵐呼び", "Storm Caller", "技の力 +6%、技の待ち −4%", "Skill power +6%, skill cooldowns −4%", ["str_i", "cloud_break", "str_j"], a, cost=2, arch_points=20)
    t.rank("hawk_eye", 3, 8, 9, "str_j", a)
    t.thr("thunder_arm", 6, 10, "strength", 36, {"projectile": 0.06, "attack": 0.04}, "雷の腕", "Thunder Arm", "矢の傷 +6%、与える傷 +4%", "Projectile damage +6%, damage dealt +4%", "storm_caller", a)
    t.eff("endless_rain", 8, 10, {"mana": 0.1, "regen": 0.06}, "止まぬ雨", "Endless Rain", "最大マナ +10%、マナの回復 +6%", "Max mana +10%, mana regeneration +6%", "storm_caller", a, cost=2)
    return t


# ---------------------------------------------------------------- 魔法使い (星読み)
def mage():
    t = Tree("mage", ["thunder", "fire", "tide"])
    t.active("thunder_bolt", 4, 0, cost=1)
    t.stat("spi_a", "spirit", 3, 1, "thunder_bolt").stat("agi_a", "agility", 5, 1, "thunder_bolt")
    t.passive("star_reading", 3, 2, "spi_a").stat("spi_b", "spirit", 5, 2, "agi_a").active("ember_orb", 4, 2, req=["spi_a", "agi_a"])
    t.stat("spi_c", "spirit", 2, 3, ["star_reading", "spi_b"]).stat("spi_d", "spirit", 4, 3, "ember_orb").stat("agi_b", "agility", 6, 3, ["star_reading", "spi_b"])
    a = "thunder"
    t.rank("thunder_bolt", 2, 0, 4, "spi_c", a)
    t.stat("spi_e", "spirit", 1, 4, "spi_c", a)
    t.eff("quick_cast", 2, 4, {"cooldown": -0.05}, "速い詠唱", "Quick Cast", "技の待ち −5%", "Skill cooldowns −5%", "spi_c", a)
    t.rank("thunder_bolt", 3, 0, 5, "thunder_bolt_2", a)
    t.thr("charged", 1, 5, "spirit", 20, {"skill_power": 0.06}, "帯電", "Charged", "技の力 +6%", "Skill power +6%", "spi_e", a)
    t.stat("spi_f", "spirit", 2, 5, "quick_cast", a)
    t.stat("spi_g", "spirit", 0, 6, "thunder_bolt_3", a)
    t.eff("arc_light", 1, 6, {"attack": 0.05}, "弧の光", "Arc Light", "与える傷 +5%", "Damage dealt +5%", "charged", a)
    t.eff("thin_air", 2, 6, {"mana_cost": -0.06}, "薄い空気", "Thin Air", "技のマナの費用 −6%", "Skill mana cost −6%", "spi_f", a)
    t.eff("far_bolt", 0, 7, {"projectile": 0.06}, "遠い雷", "Far Bolt", "飛び道具の傷 +6%", "Projectile damage +6%", "spi_g", a)
    t.stat("spi_h", "spirit", 1, 7, "arc_light", a).stat("spi_i", "spirit", 2, 7, "thin_air", a)
    t.stat("spi_j", "spirit", 0, 8, "far_bolt", a)
    t.eff("storm_mind", 1, 8, {"mana": 0.1}, "嵐の心", "Storm Mind", "最大マナ +10%", "Max mana +10%", "spi_h", a)
    t.stat("spi_k", "spirit", 2, 8, "spi_i", a)
    t.keystone("star_oath", 1, 9, ["spi_j", "storm_mind", "spi_k"], a)
    t.thr("starfall", 0, 10, "spirit", 40, {"skill_power": 0.08}, "星降り", "Starfall", "技の力 +8%", "Skill power +8%", "star_oath", a)
    t.eff("high_voice", 2, 10, {"cooldown": -0.05, "mana_cost": 0.04}, "高い声", "High Voice", "技の待ち −5%、技のマナの費用 +4%", "Skill cooldowns −5%, skill mana cost +4%", "star_oath", a, cost=2)
    a = "fire"
    t.rank("star_reading", 2, 3, 4, "spi_d", a)
    t.rank("ember_orb", 2, 4, 4, "spi_d", a)
    t.active("stardust", 5, 4, req="spi_d", arch=a)
    t.stat("spi_l", "spirit", 3, 5, ["ember_orb_2", "star_reading_2"], a)
    t.eff("hot_core", 4, 5, {"skill_power": 0.05}, "熱い芯", "Hot Core", "技の力 +5%", "Skill power +5%", "ember_orb_2", a)
    t.eff("ash_robe", 5, 5, {"fire_resist": 0.2}, "灰の衣", "Ash Robe", "火の傷 −20%", "Fire damage −20%", ["ember_orb_2", "stardust"], a)
    t.eff("kindle", 3, 6, {"attack": 0.05}, "焚きつけ", "Kindle", "与える傷 +5%", "Damage dealt +5%", "spi_l", a)
    t.stat("spi_m", "spirit", 4, 6, "hot_core", a).stat("spi_n", "spirit", 5, 6, "ash_robe", a)
    t.stat("spi_o", "spirit", 3, 7, "kindle", a)
    t.thr("blaze", 4, 7, "spirit", 24, {"skill_power": 0.06}, "炎", "Blaze", "技の力 +6%", "Skill power +6%", "spi_m", a)
    t.eff("warm_hands", 5, 7, {"regen": 0.08}, "温かい手", "Warm Hands", "マナの回復 +8%", "Mana regeneration +8%", "spi_n", a)
    t.eff("ember_eye", 3, 8, {"big_target": 0.1}, "熾火の目", "Ember Eye", "大きな相手への技の傷 +10%", "Skill damage to large foes +10%", "spi_o", a)
    t.stat("spi_p", "spirit", 4, 8, "blaze", a).stat("spi_q", "spirit", 5, 8, "warm_hands", a)
    t.rank("star_reading", 3, 3, 9, "ember_eye", a)
    t.rank("stardust", 2, 5, 9, "spi_q", a)
    t.eff("sun_heart", 4, 9, {"skill_power": 0.06, "mana_cost": 0.06}, "太陽の心臓", "Sun Heart", "技の力 +6%、技のマナの費用 +6%", "Skill power +6%, skill mana cost +6%", ["ember_eye", "spi_p", "spi_q"], a, cost=2, arch_points=20)
    t.thr("white_fire", 3, 10, "spirit", 36, {"skill_power": 0.05, "attack": 0.04}, "白い火", "White Fire", "技の力 +5%、与える傷 +4%", "Skill power +5%, damage dealt +4%", "sun_heart", a)
    t.eff("ember_skin", 5, 10, {"fire_resist": 0.15, "low_hp_armor": 3}, "熾火の肌", "Ember Skin", "火の傷 −15%、体力が少ないとき防具 +3", "Fire damage −15%, armor +3 while low on health", "sun_heart", a, cost=2)
    a = "tide"
    t.active("frost_tide", 7, 4, req="agi_b", arch=a)
    t.stat("agi_c", "agility", 6, 5, "frost_tide", a)
    t.rank("frost_tide", 2, 7, 5, "frost_tide", a)
    t.active("blink", 8, 5, req="frost_tide", arch=a)
    t.eff("cold_feet", 6, 6, {"move": 0.04}, "冷たい足", "Cold Feet", "移動 +4%", "Movement +4%", "agi_c", a)
    t.stat("spi_r", "spirit", 7, 6, "frost_tide_2", a)
    t.rank("blink", 2, 8, 6, "blink", a)
    t.stat("agi_d", "agility", 6, 7, "cold_feet", a)
    t.eff("frost_armor", 7, 7, {"damage_taken": -0.05}, "霜の鎧", "Frost Armor", "受ける傷 −5%", "Damage taken −5%", "spi_r", a)
    t.eff("far_step", 8, 7, {"cooldown": -0.05}, "遠い一歩", "Far Step", "技の待ち −5%", "Skill cooldowns −5%", "blink_2", a)
    t.thr("light_robe", 6, 8, "agility", 20, {"move": 0.05}, "軽い衣", "Light Robe", "移動 +5%", "Movement +5%", "agi_d", a)
    t.stat("agi_e", "agility", 7, 8, "frost_armor", a).stat("agi_f", "agility", 8, 8, "far_step", a)
    t.eff("tide_walker", 7, 9, {"move": 0.05, "mana": 0.08}, "潮渡り", "Tide Walker", "移動 +5%、最大マナ +8%", "Movement +5%, max mana +8%", ["light_robe", "agi_e", "agi_f"], a, cost=2, arch_points=20)
    t.thr("deep_frost", 6, 10, "spirit", 36, {"skill_power": 0.06}, "深い霜", "Deep Frost", "技の力 +6%", "Skill power +6%", "tide_walker", a)
    t.eff("mirror_step", 8, 10, {"damage_taken": -0.04, "cooldown": -0.04}, "鏡の歩み", "Mirror Step", "受ける傷 −4%、技の待ち −4%", "Damage taken −4%, skill cooldowns −4%", "tide_walker", a, cost=2)
    return t


# ---------------------------------------------------------------- lang
def put_lang(path, entries):
    """既存の node.skyabyss.<職>.* / arch.skyabyss.<職>.* (この生成の分) を書き直し、無ければ末尾に足す。"""
    txt = io.open(path, encoding="utf-8").read()
    for k, v in entries.items():
        line = '  %s: %s' % (json.dumps(k), json.dumps(v, ensure_ascii=False))
        pat = re.compile(r'^  %s: .*?(,?)$' % re.escape(json.dumps(k)), re.M)
        m = pat.search(txt)
        if m:
            txt = txt[:m.start()] + line + m.group(1) + txt[m.end():]
        else:
            body = txt.rstrip()
            assert body.endswith("}")
            body = body[:-1].rstrip()
            txt = body + ",\n" + line + "\n}\n"
    json.loads(txt)
    io.open(path, "w", encoding="utf-8", newline="\n").write(txt)


def main():
    ja = {}
    en = {}
    trees = [assassin(), shaman(), smith(), archer(), mage()]
    for t in trees:
        t.write()
        for arch, (a_ja, a_en) in ARCH_NAMES[t.job].items():
            ja["arch.skyabyss.%s.%s" % (t.job, arch)] = a_ja
            en["arch.skyabyss.%s.%s" % (t.job, arch)] = a_en
        for id, (n_ja, n_en, e_ja, e_en) in t.names.items():
            ja["node.skyabyss.%s.%s" % (t.job, id)] = n_ja
            ja["node.skyabyss.%s.%s.effect" % (t.job, id)] = e_ja
            en["node.skyabyss.%s.%s" % (t.job, id)] = n_en
            en["node.skyabyss.%s.%s.effect" % (t.job, id)] = e_en
        total = sum(n["cost"] for n in t.nodes)
        print(t.job, len(t.nodes), "nodes", total, "points")
    put_lang(LANG / "ja_jp.json", ja)
    put_lang(LANG / "en_us.json", en)


if __name__ == "__main__":
    main()
