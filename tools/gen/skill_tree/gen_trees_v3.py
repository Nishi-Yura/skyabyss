#!/usr/bin/env python3
"""技の木 v3 (2026-11-04。docs/design/48 の 4 章)。六職の木を同じ骨組みで書く。

    PYTHONIOENCODING=utf-8 python -I tools/gen/skill_tree/gen_trees_v3.py

骨組み (全職共通):
  幹 (x 3〜5、0〜3 行): 発動① (4,0)・主の能力値 (4,1)・①の軽減 (3,1)・①の早さ (5,1)・常時 A (3,2)・発動② (4,2)・常時 B (5,2)・
      ②の軽減 (3,3)・能力値 (4,3)・②の早さ (5,3)
  通り道 (4 行目): 発動③ (2,4)・主の能力値 (3,4)・(4,4)・(5,4)・発動④ (6,4)。どの型からも横へ進めば 4 つとも取れる
  型 (左 x 0〜2 は③の下から、真ん中 x 3〜5 は幹の下から、右 x 6〜8 は④の下から): 5〜10 行に GRID、11 行に軽減・泉・早さ (真ん中は冴え・泉・冴え)
  線: 前提は「真上の節」と「同じ型 (幹) の同じ行の隣の節」だけ (縦と横の線。交差しない。上へは戻れない)
  奥義: 9 行目の真ん中。金色・3 SP・その型の節に 15 SP 使うと取れる
  排他 (blocks): 型どうし・型の中の 2 択・同じ技の変化どうし。どちらの向きにも書く

前の木 (tools/gen/skill_tree/trees_v2/<職>.json) の節の数値と名前を引き継ぎ、新しい節だけここで書く。
書くもの: assets/skyabyss/skill_tree/<職>.json、lang (node.skyabyss.<職>.* と arch.skyabyss.<職>.*)
"""
import io
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_trees import eff_text, put_lang  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/skill_tree"
V2 = Path(__file__).resolve().parent / "trees_v2"
LANG = ROOT / "src/main/resources/assets/skyabyss/lang"
GATE = 15
TARGET = 128
CHEAP, QUICK, KEEN = -0.25, -0.20, 0.12
MELEE = {"warrior", "assassin", "smith"}

# 変化 (Rune の id -> 技・番号・SP)。4〜7 は武器の書き換え・熟練の刻印なので、4 つ目の変化は 8
RUNES = {
    "wide_blade": ("titan_cleave", 1, 1), "deep_blade": ("titan_cleave", 2, 1), "ground_run": ("titan_cleave", 3, 2),
    "long_voice": ("rock_call", 1, 1), "heavy_voice": ("rock_call", 2, 1), "rock_return": ("rock_call", 3, 2),
    "push_tide": ("whale_sweep", 1, 1), "pincer": ("whale_sweep", 2, 1), "double_sweep": ("whale_sweep", 3, 2), "spin_sweep": ("whale_sweep", 8, 2),
    "bash_rush": ("shield_rush", 1, 2),
    "pass_through": ("lunge", 1, 1), "chain_lunge": ("lunge", 2, 1), "pierce_through": ("lunge", 3, 2), "veil_lunge": ("lunge", 8, 2),
    "long_leap": ("backstep", 2, 1), "wind_remnant": ("backstep", 3, 2),
    "back_take": ("wind_rend", 1, 1), "bleed": ("wind_rend", 2, 1), "companion_wind": ("wind_rend", 3, 2),
    "triple_fang": ("twin_fang", 1, 1), "venom_fang": ("twin_fang", 2, 1),
    "thick_song": ("ward_song", 1, 1), "wide_song": ("ward_song", 2, 1), "cycling_song": ("ward_song", 3, 2), "cycle_ward": ("ward_song", 8, 2),
    "strong_tide": ("ebb_tide", 1, 1), "whirl": ("ebb_tide", 2, 1), "push_back": ("ebb_tide", 3, 2),
    "walking_lamp": ("lamp_charm", 1, 1), "burning_lamp": ("lamp_charm", 2, 1),
    "long_mark": ("curse_mark", 1, 1), "passing_mark": ("curse_mark", 2, 1),
    "long_fire": ("ember_hammer", 1, 1), "double_strike": ("ember_hammer", 2, 1), "fire_pillar": ("ember_hammer", 3, 2),
    "spark_stomp": ("forge_stomp", 1, 2), "heavy_stomp": ("forge_stomp", 2, 1),
    "deep_temper": ("temper", 1, 1), "shared_fire": ("temper", 2, 1),
    "thick_ash": ("ash_scatter", 1, 1), "ash_wind": ("ash_scatter", 2, 1),
    "thin_arrow": ("cloud_pierce", 1, 2), "heavy_arrow": ("cloud_pierce", 2, 1), "lightning": ("cloud_pierce", 3, 2), "snare_pierce": ("cloud_pierce", 8, 2),
    "thrown_trap": ("bone_trap", 1, 1), "great_trap": ("bone_trap", 2, 1),
    "narrow_rain": ("sky_rain", 1, 1), "long_rain": ("sky_rain", 2, 1),
    "parting_arrows": ("archer_leap", 1, 1),
    "stardust_bolt": ("thunder_bolt", 1, 2), "branch_bolt": ("thunder_bolt", 2, 1), "heavy_bolt": ("thunder_bolt", 3, 1),
    "triple_orb": ("ember_orb", 1, 1), "ember_bed": ("ember_orb", 2, 1),
    "ice_ring": ("frost_tide", 1, 1), "frost_road": ("frost_tide", 2, 1),
    "star_remnant": ("blink", 1, 1), "double_blink": ("blink", 2, 1),
}
# 奥義: id -> (種類, 中身)。trait = 鍵石 (keystone の id)、keystone = 要の技 (skill)。効果の数は effect (NodeEffects の鍵)
KEYS = {
    "stone_oath": ("keystone", "stone_oath", None), "glass_blade": ("trait", "glass_blade", None), "wind_dance": ("trait", "wind_dance", None),
    "lone_wind": ("keystone", "lone_wind", None),
    "full_tide": ("trait", "full_tide", None), "chorus_voice": ("keystone", "chorus_voice", None),
    "hex_chant": ("trait", "hex_chant", {"hex_mark": 0.1, "skill_power": 0.06}),
    "ash_pact": ("trait", "ash_pact", None), "smith_eye": ("keystone", "smith_eye", None),
    "ember_lung": ("trait", "ember_lung", {"cd_kill": 1.0, "regen": 0.1}),
    "lone_string": ("trait", "lone_string", None), "still_string": ("keystone", "still_string", None),
    "storm_caller": ("trait", "storm_caller", {"storm_call": 1.0, "skill_power": 0.06}),
    "star_oath": ("keystone", "star_oath", None),
    "sun_heart": ("trait", "sun_heart", {"sun_burst": 0.2, "skill_power": 0.06}),
    "tide_walker": ("trait", "tide_walker", {"blink_guard": 0.4, "move": 0.05}),
}
# 新しい常に効く数 (名と数。前の木にない節、名や数を変えた節)
NEW = {
    ("warrior", "reckless"): ({"attack": 0.15, "damage_taken": 0.06}, 3, "捨て身", "Reckless"),
    ("assassin", "still_dance"): ({"cooldown": -0.12, "move": -0.05}, 2, "静かな舞", "Still Dance"),
    ("shaman", "flood"): ({"skill_power": 0.1, "mana_cost": 0.1}, 2, "上げ潮", "Rising Tide"),
}

# 職ごとの骨組みの中身。actives = ①②③④、passives = 常時 A (3,2)・B (5,2) (B が None なら能力値)、main・sub = 幹の能力値
JOBS = {
    "warrior": dict(archs=["shield", "cleaver", "tail"], main="strength", sub="vitality",
                    actives=["titan_cleave", "shield_rush", "rock_call", "whale_sweep"], passives=["stone_stance", "riposte"],
                    grid={
                        "shield": ["r:heavy_voice n:rock_call_2 s:vitality",
                                   "r:long_voice h:rock_skin e:shield_wall",
                                   "e:unmoving s:vitality r:rock_return",
                                   "e:quick_iron n:stone_stance_2 r:bash_rush",
                                   "e:wall_voice k:stone_oath .",
                                   ". n:stone_stance_3 s:strength"],
                        "cleaver": ["r:wide_blade n:titan_cleave_2 r:ground_run",
                                    "e:big_foe s:strength e:reckless",
                                    "e:thirst h:heavy_blow e:dull_iron",
                                    "r:deep_blade n:titan_cleave_3 e:deep_wound",
                                    "m:cleave_cliff k:glass_blade n:shield_rush_2",
                                    ". s:strength ."],
                        "tail": ["n:whale_sweep_2 s:agility r:push_tide",
                                 "r:pincer e:tail_wind r:double_sweep",
                                 "e:embers_tail h:light_foot r:spin_sweep",
                                 "e:circle_guard n:riposte_2 s:agility",
                                 "m:sweep_tail k:wind_dance .",
                                 ". n:riposte_3 s:strength"]},
                    blocks=[("shield_wall", "reckless"), ("dull_iron", "embers_tail"),
                            ("unmoving", "quick_iron"), ("big_foe", "thirst"), ("tail_wind", "circle_guard")]),
    "assassin": dict(archs=["shadow", "dance", "lone"], main="agility", sub="strength",
                     actives=["lunge", "backstep", "twin_fang", "wind_rend"], passives=["nimble", "foresight"],
                     grid={
                         "shadow": ["r:veil_lunge n:twin_fang_2 s:agility",
                                    "h:backstab e:quiet_blade r:venom_fang",
                                    "e:shadow_thirst s:agility r:triple_fang",
                                    "e:veil_edge h:night_eye n:nimble_2",
                                    "e:fade k:glass_blade n:nimble_3",
                                    ". s:strength ."],
                         "dance": ["r:pass_through n:lunge_2 r:pierce_through",
                                   "r:long_leap s:agility r:wind_remnant",
                                   "h:quick_feet e:endless_step e:light_step",
                                   "h:storm_feet n:backstep_2 e:still_dance",
                                   "m:lunge_surge k:wind_dance r:chain_lunge",
                                   ". s:agility ."],
                         "lone": ["n:wind_rend_2 s:strength r:back_take",
                                  "e:cold_wind e:keen_wind r:bleed",
                                  "h:heavy_hand s:agility r:companion_wind",
                                  "e:lone_edge n:foresight_2 e:last_breath",
                                  "m:rend_blade k:lone_wind .",
                                  ". n:foresight_3 s:strength"]},
                     blocks=[("veil_edge", "quick_feet"), ("light_step", "cold_wind"),
                             ("backstab", "shadow_thirst"), ("storm_feet", "still_dance"), ("lone_edge", "last_breath")]),
    "shaman": dict(archs=["ward", "ebb", "curse"], main="spirit", sub="vitality",
                   actives=["ward_song", "ebb_tide", "lamp_charm", "curse_mark"], passives=["calm_heart", "ripple"],
                   grid={
                       "ward": ["r:thick_song n:lamp_charm_2 s:spirit",
                                "r:wide_song s:vitality r:walking_lamp",
                                "r:cycling_song h:tide_skin r:burning_lamp",
                                "h:deep_breath n:calm_heart_2 e:steady_voice",
                                "s:vitality k:full_tide e:warm_lamp",
                                ". n:calm_heart_3 ."],
                       "ebb": ["r:strong_tide n:ebb_tide_2 r:push_back",
                               "e:flood s:spirit e:wide_cycle",
                               "r:cycle_ward h:tide_mind r:whirl",
                               "e:undertow n:ward_song_2 h:still_water",
                               "s:spirit k:chorus_voice m:ebb_whirl",
                               ". n:ward_song_3 ."],
                       "curse": ["n:curse_mark_2 s:spirit r:long_mark",
                                 "e:drain h:hex_eye r:passing_mark",
                                 "e:dark_tide n:ripple_2 e:bitter_salt",
                                 "e:cold_heart e:salt_wound s:spirit",
                                 "e:black_salt k:hex_chant .",
                                 ". n:ripple_3 s:vitality"]},
                   blocks=[("steady_voice", "flood"), ("wide_cycle", "drain"),
                           ("deep_breath", "warm_lamp"), ("still_water", "undertow"), ("dark_tide", "cold_heart")]),
    "smith": dict(archs=["ember", "temper", "ash"], main="strength", sub="spirit",
                  actives=["temper", "forge_stomp", "ember_hammer", "ash_scatter"], passives=["ember_heart", "forge_memory"],
                  grid={
                      "ember": ["r:long_fire n:ember_hammer_2 s:strength",
                                "e:fire_skin h:heavy_arm r:double_strike",
                                "e:cinder s:strength r:fire_pillar",
                                "e:big_hammer n:ember_heart_2 e:hot_iron",
                                "m:hammer_meteor k:ash_pact h:mountain_arm",
                                ". n:ember_heart_3 ."],
                      "temper": ["r:deep_temper n:temper_2 r:heavy_stomp",
                                 "h:iron_will s:strength e:master_eye",
                                 "e:keen_temper e:hard_edge e:thick_plate",
                                 "r:shared_fire n:temper_3 n:forge_stomp_2",
                                 "e:quench k:smith_eye h:steel_body",
                                 ". s:strength ."],
                      "ash": ["n:ash_scatter_2 s:spirit r:thick_ash",
                              "e:grey_cloud e:hot_ash r:ash_wind",
                              "s:spirit h:ash_mind r:spark_stomp",
                              "h:white_ash n:forge_memory_2 e:ash_heart",
                              "s:strength k:ember_lung .",
                              ". n:forge_memory_3 s:spirit"]},
                  blocks=[("big_hammer", "master_eye"), ("thick_plate", "grey_cloud"),
                          ("fire_skin", "cinder"), ("iron_will", "keen_temper"), ("white_ash", "ash_heart")]),
    "archer": dict(archs=["trap", "pierce", "rain"], main="agility", sub="strength",
                   actives=["cloud_pierce", "archer_leap", "bone_trap", "sky_rain"], passives=["far_shot", "hawk_eye"],
                   grid={
                       "trap": ["r:parting_arrows n:bone_trap_2 s:agility",
                                "e:light_pack s:agility r:great_trap",
                                "e:soft_landing e:hunter_mark r:thrown_trap",
                                "h:patient n:far_shot_2 e:bone_cage",
                                "m:trap_mine k:still_string .",
                                ". n:far_shot_3 s:strength"],
                       "pierce": ["r:thin_arrow n:cloud_pierce_2 r:lightning",
                                  "e:quick_nock s:agility e:long_draw",
                                  "h:steady_hand e:sharp_tip r:heavy_arrow",
                                  "e:last_arrow n:cloud_pierce_3 n:archer_leap_2",
                                  "h:eagle_eye k:lone_string r:snare_pierce",
                                  ". s:agility ."],
                       "rain": ["n:sky_rain_2 s:strength r:narrow_rain",
                                "e:endless_rain e:cloud_break r:long_rain",
                                "h:strong_arm s:agility e:heavy_rain",
                                "e:wide_sky n:hawk_eye_2 e:quiver",
                                "m:rain_comet k:storm_caller .",
                                ". n:hawk_eye_3 s:strength"]},
                   blocks=[("patient", "quick_nock"), ("long_draw", "endless_rain"),
                           ("light_pack", "soft_landing"), ("steady_hand", "last_arrow"), ("strong_arm", "wide_sky")]),
    "mage": dict(archs=["fire", "thunder", "tide"], main="spirit", sub="agility",
                 actives=["thunder_bolt", "blink", "ember_orb", "frost_tide"], passives=["star_reading", None],
                 grid={
                     "fire": ["r:triple_orb n:ember_orb_2 s:spirit",
                              "e:kindle h:blaze r:ember_bed",
                              "e:ember_skin s:spirit e:hot_core",
                              "e:warm_hands n:star_reading_2 e:ash_robe",
                              "e:ember_eye k:sun_heart s:agility",
                              ". h:white_fire ."],
                     "thunder": ["r:stardust_bolt n:thunder_bolt_2 r:heavy_bolt",
                                 "e:storm_mind s:spirit e:arc_light",
                                 "h:charged e:far_bolt r:branch_bolt",
                                 "e:high_voice n:thunder_bolt_3 n:blink_2",
                                 "m:thunder_cloud k:star_oath h:starfall",
                                 ". e:thin_air ."],
                     "tide": ["n:frost_tide_2 s:spirit r:frost_road",
                              "e:frost_armor e:cold_feet r:ice_ring",
                              "h:light_robe s:agility r:star_remnant",
                              "e:mirror_step n:star_reading_3 r:double_blink",
                              "h:deep_frost k:tide_walker m:frozen_sea",
                              ". e:far_step ."]},
                 blocks=[("warm_hands", "storm_mind"), ("arc_light", "frost_armor"),
                         ("kindle", "ember_skin"), ("high_voice", "starfall"), ("light_robe", "mirror_step")]),
}
ARCH_X = (0, 3, 6)
STAT_SHORT = {"strength": "str", "agility": "agi", "spirit": "spi", "vitality": "vit"}


def skill_names():
    out = {}
    for lang in ("ja_jp", "en_us"):
        d = json.loads(io.open(LANG / (lang + ".json"), encoding="utf-8").read())
        out[lang] = {k[len("skill.skyabyss."):]: v for k, v in d.items() if k.startswith("skill.skyabyss.") and k.count(".") == 2}
    return out


def old_names(job):
    out = {}
    for lang in ("ja_jp", "en_us"):
        d = json.loads(io.open(LANG / (lang + ".json"), encoding="utf-8").read())
        p = "node.skyabyss.%s." % job
        out[lang] = {k[len(p):]: v for k, v in d.items() if k.startswith(p)}
    return out


class Builder:
    def __init__(self, job, spec):
        self.job = job
        self.spec = spec
        self.old = {n["id"]: n for n in json.load(io.open(V2 / (job + ".json"), encoding="utf-8"))["nodes"]}
        self.nodes = []
        self.cell = {}
        self.names = {}  # id -> (ja, en, ja_e, en_e) (node.skyabyss.<職>.<id>)
        self.sn = skill_names()
        self.on = old_names(job)
        self.stat_n = 0

    def add(self, node):
        assert (node["x"], node["y"]) not in self.cell, (self.job, node)
        assert node["id"] not in {n["id"] for n in self.nodes}, (self.job, node["id"])
        self.nodes.append(node)
        self.cell[(node["x"], node["y"])] = node
        return node

    def stat(self, stat, x, y, arch=None, amount=4, cost=1):
        self.stat_n += 1
        node = {"id": "%s_%d" % (STAT_SHORT[stat], self.stat_n), "kind": "stat", "cost": cost, "x": x, "y": y, "stat": stat, "amount": amount}
        if arch:
            node["arch"] = arch
        return self.add(node)

    def skill_effect(self, id, x, y, effect, nja, nen, eja, een, arch=None, cost=1):
        node = {"id": id, "kind": "effect", "cost": cost, "x": x, "y": y, "effect": effect}
        if arch:
            node["arch"] = arch
        self.names[id] = (nja, nen, eja, een)
        return self.add(node)

    def cheap(self, skill, x, y, arch=None):
        ja, en = self.sn["ja_jp"][skill], self.sn["en_us"][skill]
        return self.skill_effect("cheap_" + skill, x, y, {"mana_cost@" + skill: CHEAP}, "%sの軽減" % ja, "Cheaper %s" % en,
                                 "%s のマナ −25%%" % ja, "%s mana cost −25%%" % en, arch)

    def quick(self, skill, x, y, arch=None):
        ja, en = self.sn["ja_jp"][skill], self.sn["en_us"][skill]
        return self.skill_effect("quick_" + skill, x, y, {"cooldown@" + skill: QUICK}, "%sの早さ" % ja, "Faster %s" % en,
                                 "%s の再使用までの時間 −20%%" % ja, "%s cooldown −20%%" % en, arch)

    def keen(self, arch, skill, x, y):
        ja, en = self.sn["ja_jp"][skill], self.sn["en_us"][skill]
        return self.skill_effect("%s_%s_keen" % (arch, skill), x, y, {"power@" + skill: KEEN}, "%sの冴え" % ja, "Keener %s" % en,
                                 "%s の力 +12%%" % ja, "%s power +12%%" % en, arch)

    def well(self, arch, x, y):
        if self.job in MELEE:
            return self.skill_effect("%s_mana_hit" % arch, x, y, {"mana_hit": 1.0}, "打ち手の泉", "Striker's Well",
                                     "近接で当てるたびにマナ +1（0.6 秒に一度）", "Melee hits restore 1 mana (once per 0.6 s)", arch, cost=2)
        return self.skill_effect("%s_mana_kill" % arch, x, y, {"mana_kill": 2.0}, "仕留めの泉", "Hunter's Well",
                                 "敵を倒すとマナ +2", "Defeating an enemy restores 2 mana", arch, cost=2)

    def token(self, tok, x, y, arch):
        if tok == ".":
            return None
        t, id = tok.split(":", 1)
        if t == "r":
            skill, idx, cost = RUNES[id]
            return self.add({"id": id, "kind": "rune", "cost": cost, "x": x, "y": y, "arch": arch, "skill": skill, "rune": idx})
        if t == "n":
            skill = id.rsplit("_", 1)[0]
            return self.add({"id": id, "kind": "rank", "cost": 1, "x": x, "y": y, "arch": arch, "skill": skill})
        if t == "s":
            return self.stat(id, x, y, arch, amount=8, cost=2)
        if t == "m":
            return self.add({"id": id, "kind": "mutation", "cost": 2, "x": x, "y": y, "arch": arch, "mutation": id})
        if t == "k":
            kind, ref, effect = KEYS[id]
            node = {"id": id, "kind": kind, "cost": 3, "x": x, "y": y, "arch": arch, "arch_points": GATE}
            node["keystone" if kind == "trait" else "skill"] = ref
            if effect:
                node["effect"] = effect
            return self.add(node)
        if t in ("e", "h"):
            if (self.job, id) in NEW:
                effect, cost, nja, nen = NEW[(self.job, id)]
                eja, een = eff_text(effect, self.job)
                self.names[id] = (nja, nen, eja, een)
                return self.add({"id": id, "kind": "effect", "cost": cost, "x": x, "y": y, "arch": arch, "effect": effect})
            o = self.old[id]
            node = {"id": id, "kind": o["kind"], "cost": o["cost"], "x": x, "y": y, "arch": arch, "effect": o["effect"]}
            if o["kind"] == "threshold":
                node["stat"] = o["stat"]
                node["at"] = o["at"]
            self.names[id] = (self.on["ja_jp"][id], self.on["en_us"][id], self.on["ja_jp"][id + ".effect"], self.on["en_us"][id + ".effect"])
            return self.add(node)
        raise SystemExit("bad token " + tok)

    def build(self):
        s = self.spec
        a1, a2, a3, a4 = s["actives"]
        pa, pb = s["passives"]
        self.add({"id": a1, "kind": "active", "cost": 1, "x": 4, "y": 0, "skill": a1})
        self.stat(s["main"], 4, 1)
        self.cheap(a1, 3, 1)
        self.quick(a1, 5, 1)
        self.add({"id": pa, "kind": "passive", "cost": 2, "x": 3, "y": 2, "skill": pa})
        self.add({"id": a2, "kind": "active", "cost": 2, "x": 4, "y": 2, "skill": a2})
        if pb:
            self.add({"id": pb, "kind": "passive", "cost": 2, "x": 5, "y": 2, "skill": pb})
        else:
            self.stat(s["sub"], 5, 2)
        self.cheap(a2, 3, 3)
        self.stat(s["sub"], 4, 3)
        self.quick(a2, 5, 3)
        self.add({"id": a3, "kind": "active", "cost": 2, "x": 2, "y": 4, "skill": a3})
        for x in (3, 4, 5):
            self.stat(s["main"], x, 4)
        self.add({"id": a4, "kind": "active", "cost": 2, "x": 6, "y": 4, "skill": a4})
        for k, arch in enumerate(s["archs"]):
            g0 = ARCH_X[k]
            rows = s["grid"][arch]
            assert len(rows) == 6, (self.job, arch)
            for r, row in enumerate(rows):
                toks = row.split()
                assert len(toks) == 3, (self.job, arch, row)
                for c, tok in enumerate(toks):
                    self.token(tok, g0 + c, 5 + r, arch)
            # 一番下の行: 左 = ③の軽減・泉・③の早さ、右 = ④の、真ん中 = ①の冴え・泉・②の冴え
            if k == 0:
                self.cheap(a3, g0, 11, arch)
                self.well(arch, g0 + 1, 11)
                self.quick(a3, g0 + 2, 11, arch)
            elif k == 2:
                self.cheap(a4, g0, 11, arch)
                self.well(arch, g0 + 1, 11)
                self.quick(a4, g0 + 2, 11, arch)
            else:
                self.keen(arch, a1, g0, 11)
                self.well(arch, g0 + 1, 11)
                self.keen(arch, a2, g0 + 2, 11)
        self.link()
        self.blocks()
        self.fit_thresholds()
        self.tune()
        self.verify()
        return self

    def link(self):
        """前提: 真上の節 (同じ型か、幹から型へ降りる所) と、同じ型 (幹) の同じ行の隣の節"""
        for n in self.nodes:
            if (n["x"], n["y"]) == (4, 0):
                continue
            req = []
            up = self.cell.get((n["x"], n["y"] - 1))
            if up is not None and (up.get("arch") == n.get("arch") or up.get("arch") is None):
                req.append(up["id"])
            for dx in (-1, 1):
                side = self.cell.get((n["x"] + dx, n["y"]))
                if side is not None and side.get("arch") == n.get("arch"):
                    req.append(side["id"])
            assert req, (self.job, n["id"], "no way in")
            n["requires"] = req

    def blocks(self):
        by = {n["id"]: n for n in self.nodes}
        pairs = list(self.spec["blocks"])
        # 同じ技の変化どうし
        runes = {}
        for n in self.nodes:
            if n["kind"] == "rune":
                runes.setdefault(n["skill"], []).append(n["id"])
        for ids in runes.values():
            for i in range(len(ids)):
                for j in range(i + 1, len(ids)):
                    pairs.append((ids[i], ids[j]))
        for a, b in pairs:
            assert a in by and b in by, (self.job, a, b)
            assert by[a]["kind"] not in ("keystone", "trait") and by[b]["kind"] not in ("keystone", "trait"), (self.job, a, b)
            by[a].setdefault("blocks", [])
            by[b].setdefault("blocks", [])
            if b not in by[a]["blocks"]:
                by[a]["blocks"].append(b)
            if a not in by[b]["blocks"]:
                by[b]["blocks"].append(a)

    def tune(self):
        """SP の合計を TARGET にそろえる: 型の奥の常に効く数 (2 SP) から 3 SP に上げ、数も 1.5 倍に (SP あたりの強さはそのまま)"""
        total = sum(n["cost"] for n in self.nodes)
        cands = [n for n in self.nodes if n["kind"] in ("effect", "threshold") and n["cost"] == 2 and n.get("arch") and n["y"] <= 10
                 and not any(k.split("@")[0] in ("mana_hit", "mana_kill", "low_hp_armor", "armor") for k in n["effect"])]
        cands.sort(key=lambda n: (-n["y"], n["x"]))
        for n in cands:
            if total >= TARGET:
                break
            n["cost"] = 3
            n["effect"] = {k: round(v * 1.5, 3) for k, v in n["effect"].items()}
            nja, nen, _, _ = self.names[n["id"]]
            eja, een = eff_text(n["effect"], self.job)
            self.names[n["id"]] = (nja, nen, eja, een)
            total += 1

    def stat_max(self, stat, arch):
        return sum(n["amount"] for n in self.nodes if n["kind"] == "stat" and n["stat"] == stat and n.get("arch") in (None, arch))

    def fit_thresholds(self):
        """閾値は、幹とその型の能力値の節だけで届く値にする (4 の倍数に下げる)"""
        for n in self.nodes:
            if n["kind"] == "threshold":
                top = self.stat_max(n["stat"], n["arch"])
                if n["at"] > top:
                    print("  threshold", self.job, n["id"], n["stat"], n["at"], "->", top)
                    n["at"] = top

    def reachable(self, start_taken, banned):
        """取った節から前提をたどって取れる節 (banned は取らない)"""
        by = {n["id"]: n for n in self.nodes}
        got = set(start_taken)
        changed = True
        while changed:
            changed = False
            for n in self.nodes:
                if n["id"] in got or n["id"] in banned:
                    continue
                if any(r in got for r in n.get("requires", [])) or (n["x"], n["y"]) == (4, 0):
                    got.add(n["id"])
                    changed = True
        return got

    def verify(self):
        by = {n["id"]: n for n in self.nodes}
        # 線: 真上か同じ行の隣だけ (縦と横)。上へ戻らない
        for n in self.nodes:
            for r in n.get("requires", []):
                m = by[r]
                dx, dy = m["x"] - n["x"], m["y"] - n["y"]
                assert (dx == 0 and dy == -1) or (dy == 0 and abs(dx) == 1), (self.job, n["id"], r)
        # どの節も幹から取れる
        allids = self.reachable(set(), set())
        assert allids == set(by), (self.job, set(by) - allids)
        # どの節も、自分を封じる節を通らずに届く (封じ合う変化が並んで、片方からしか入れない形を作らない)
        bad = []
        for n in self.nodes:
            if n.get("blocks") and n["id"] not in self.reachable(set(), set(n["blocks"])):
                bad.append(n["id"])
        assert not bad, (self.job, "reachable only through a blocker", bad)
        # 変化 (紫) と変異 (桃) を一つも取らずに、ほかのすべての節へ届く (変化を取らないと先へ進めない形を作らない)
        variants = {n["id"] for n in self.nodes if n["kind"] in ("rune", "mutation")}
        plain = self.reachable(set(), variants)
        gated = [n["id"] for n in self.nodes if n["id"] not in variants and n["id"] not in plain]
        assert not gated, (self.job, "reachable only through a variant", gated)
        # 発動は 4 つ。型を持たない (どの型からも取れる)
        acts = [n for n in self.nodes if n["kind"] == "active"]
        assert len(acts) == 4 and all("arch" not in n for n in acts), self.job
        for arch in self.spec["archs"]:
            key = next(n for n in self.nodes if n.get("arch") == arch and n["kind"] in ("keystone", "trait"))
            # 排他の片方を取っても、型の奥義に届く (一つの節で型が閉じない)
            for n in self.nodes:
                for b in n.get("blocks", []):
                    got = self.reachable(set(), {b})
                    assert key["id"] in got, (self.job, arch, "blocked by", b)
            # 奥義の門: 奥義とその先を除いた型の節で 15 SP に届く (同じ技の変化は一つ・排他の片方だけ)
            pool = [n for n in self.nodes if n.get("arch") == arch and n["id"] != key["id"] and n["kind"] != "mutation"]
            pts = 0
            seen_skill = set()
            taken = set()
            for n in sorted(pool, key=lambda n: (n["y"], n["x"])):
                if n["kind"] == "rune":
                    if n["skill"] in seen_skill:
                        continue
                    seen_skill.add(n["skill"])
                if any(b in taken for b in n.get("blocks", [])):
                    continue
                taken.add(n["id"])
                pts += n["cost"]
            assert pts >= GATE + 4, (self.job, arch, pts)
        # 主な能力値は、幹とどの型でも 24 に届く
        for arch in self.spec["archs"]:
            assert self.stat_max(self.spec["main"], arch) >= 24, (self.job, arch, self.stat_max(self.spec["main"], arch))

    def write(self):
        lines = ['{', '  "archetypes": %s,' % json.dumps(self.spec["archs"]), '  "nodes": [']
        for i, nd in enumerate(self.nodes):
            lines.append("    " + json.dumps(nd, ensure_ascii=False) + ("," if i < len(self.nodes) - 1 else ""))
        lines += ["  ]", "}", ""]
        io.open(OUT / (self.job + ".json"), "w", encoding="utf-8", newline="\n").write("\n".join(lines))


def main():
    ja, en = {}, {}
    for job, spec in JOBS.items():
        b = Builder(job, spec).build()
        b.write()
        used = {n["id"] for n in b.nodes}
        dropped = [i for i, o in b.old.items() if i not in used and o["kind"] in ("effect", "threshold") and not i.startswith(("cheap_", "quick_"))
                   and "_mana_" not in i and not i.endswith("_keen")]
        for id, (nja, nen, eja, een) in b.names.items():
            ja["node.skyabyss.%s.%s" % (job, id)] = nja
            ja["node.skyabyss.%s.%s.effect" % (job, id)] = eja
            en["node.skyabyss.%s.%s" % (job, id)] = nen
            en["node.skyabyss.%s.%s.effect" % (job, id)] = een
        print(job, len(b.nodes), "nodes", sum(n["cost"] for n in b.nodes), "SP", "dropped:", dropped)
    put_lang(LANG / "ja_jp.json", ja)
    put_lang(LANG / "en_us.json", en)
    # 使わなくなった節の文を消す (この木にない node.skyabyss.<職>.*)
    for lang in ("ja_jp", "en_us"):
        path = LANG / (lang + ".json")
        d = json.loads(io.open(path, encoding="utf-8").read())
        keep = {}
        for k, v in d.items():
            m = re.match(r"node\.skyabyss\.(warrior|assassin|shaman|smith|archer|mage)\.([a-z0-9_]+)(\.effect)?$", k)
            if m:
                tree = json.loads(io.open(OUT / (m.group(1) + ".json"), encoding="utf-8").read())
                if m.group(2) not in {n["id"] for n in tree["nodes"]}:
                    continue
            keep[k] = v
        io.open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(keep, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
