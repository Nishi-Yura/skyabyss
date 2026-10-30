# -*- coding: utf-8 -*-
"""野の装備 (docs/design/51) の表。ここだけが品の名前・種類・階級・必要 Lv・速さ・能力・言葉を持つ。
gen_wild_gear.py がここから Java の表 (item/wild/WildGearTable)・言葉 (ja/en)・持ち方と振りの型の登録を作る。

能力は (種類, 数...) の組。種類の意味と数の並びは item/wild/WildEffect の Kind に書く (同じ名前)。
gain = 一体を続けて打つときの能力の平均の上乗せ (1 秒あたりの目安から攻撃力を逆に引くのに使う)。
"""
from __future__ import annotations

# ---------------------------------------------------------------- 強さの線 (51 の 2 章)
E_PTS = [(1, 8.0), (8, 10.0), (14, 12.6), (20, 14.6), (26, 16.0), (40, 19.4), (50, 23.0)]
B_PTS = [(1, 1.0), (8, 1.15), (14, 1.45), (20, 1.75), (26, 1.85), (40, 2.15), (50, 2.2)]
TIER_K = {"uncommon": 0.95, "rare": 0.98, "epic": 1.02, "heroic": 1.06}
# 防具 (重の 4 部位の合計): 必要 Lv → (防御, 強靭)
ARMOR_PTS = {3: (12, 0), 6: (15, 3), 9: (19, 7), 12: (19, 8), 16: (20, 10), 19: (21, 12), 22: (21, 13), 26: (22, 15), 30: (22, 16),
             32: (22, 15), 35: (23, 16), 38: (23, 16), 40: (23, 17), 42: (24, 17), 44: (24, 18), 46: (25, 18), 48: (25, 19), 50: (26, 20)}
ROLE_K = {"heavy": (1.0, 1.0), "mid": (0.93, 0.8), "light": (0.85, 0.6), "mystic": (0.9, 0.5)}
# 部位ごとの防御の割り振り (兜・胴・脚・靴)
PIECE_SPLIT = (0.2, 0.36, 0.28, 0.16)


def lerp(pts, x):
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        if x0 <= x <= x1:
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    return pts[-1][1]


def target(lv, tier):
    return lerp(E_PTS, lv) * TIER_K[tier]


def bow_mult(lv, tier):
    return lerp(B_PTS, lv) * TIER_K[tier]


# ---------------------------------------------------------------- 武器
# kind: sword greatsword spear glaive | hammer axe maul mace | staff | bow longbow | dagger claw fang | bell totem censer
JOB_KINDS = {
    "warrior": ("sword", "greatsword", "spear", "glaive"),
    "smith": ("hammer", "axe", "maul", "mace"),
    "mage": ("staff",),
    "archer": ("bow", "longbow"),
    "assassin": ("dagger", "claw", "fang"),
    "shaman": ("bell", "totem", "censer"),
}
WEAPONS = []


def W(id_, job, kind, tier, lv, region, speed, gain, fx, ja, en, abil_ja, abil_en):
    assert kind in JOB_KINDS[job], (id_, kind)
    WEAPONS.append(dict(id=id_, job=job, kind=kind, tier=tier, lv=lv, region=region, speed=speed, gain=gain, fx=fx,
                        ja=ja, en=en, abil_ja=abil_ja, abil_en=abil_en))


# ======================================================== 帯 I (Lv 1〜10)
W("wild_guard_sword", "warrior", "sword", "uncommon", 4, "meadow", 1.5, 0.0, [("MAX_HEALTH", 2)],
  "衛士の払い剣", "Guard's Surplus Sword", "体力 +2", "+2 max health")
W("wild_banner_spear", "warrior", "spear", "rare", 9, "meadow", 1.3, 0.06, [("REACH", 1.0), ("AURA_DAMAGE", 0.06, 6)],
  "古戦の旗槍", "Old War Banner Spear", "間合い +1。旗の下で: まわり 6 の仲間（自分も）の与える傷 +6%",
  "+1 reach. Under the banner: you and allies within 6 deal +6% damage")
W("wild_quarry_hammer", "smith", "hammer", "uncommon", 4, "meadow", 0.95, 0.0, [("KNOCKBACK", 0.5)],
  "石切りの鎚", "Quarry Hammer", "押し返し +0.5", "+0.5 knockback")
W("wild_tusk_axe", "smith", "axe", "rare", 9, "hills", 1.1, 0.12, [("SPRINT_BONUS", 0.25)],
  "猪牙の斧", "Boar Tusk Axe", "突進: 走りながら打つと +25%", "Charge: +25% damage while sprinting")
W("wild_scribe_staff", "mage", "staff", "uncommon", 4, "meadow", 1.2, 0.0, [("MANA_REGEN", 0.10)],
  "写本師の羽杖", "Scribe's Quill Staff", "マナの戻り +10%", "+10% mana regeneration")
W("wild_moss_staff", "mage", "staff", "rare", 9, "hills", 1.2, 0.08, [("POISON", 60, 0)],
  "苔灯りの杖", "Mosslight Staff", "打った相手に胞子（毒 I 3 秒）", "Hits spread spores (Poison I, 3s)")
W("wild_crow_bow", "archer", "bow", "uncommon", 4, "meadow", 0, 0.0, [("MOVE_SPEED", 0.05)],
  "鳥追いの弓", "Crow-Chaser Bow", "持っている間 移動の速さ +5%", "+5% movement speed while held")
W("wild_bonespike_bow", "archer", "bow", "rare", 9, "hills", 0, 0.10, [("BLEED", 0.30, 60)],
  "骨棘の弓", "Bonespike Bow", "当たった相手に出血（3 秒で矢の傷の 30%）", "Arrows cause bleeding (30% of the hit over 3s)")
W("wild_rat_knife", "assassin", "dagger", "uncommon", 4, "meadow", 2.0, 0.05, [("SLAYER_BEAST", 0.15)],
  "鼠捕りの小刀", "Ratcatcher's Knife", "獣（鼠・犬・狼・猪・兎・羊）に +15%", "+15% damage to beasts")
W("wild_shell_claw", "assassin", "claw", "rare", 9, "hills", 2.0, 0.10, [("BACKSTAB", 0.25)],
  "岩殻の爪", "Rockshell Claw", "背から +25%", "+25% damage from behind")
W("wild_scarecrow_bell_staff", "shaman", "bell", "uncommon", 4, "meadow", 1.4, 0.0, [("SLOW", 20, 0)],
  "案山子の鳴子杖", "Scarecrow Clapper Staff", "打った相手を 1 秒遅く I", "Hits slow the target (Slowness I, 1s)")
W("wild_bonebird_totem", "shaman", "totem", "rare", 9, "hills", 1.4, 0.0, [("TEAM_HEAL_ON_HIT", 0.5, 5)],
  "骨鳥の祈り柱", "Bonebird Prayer Totem", "打つたびに まわり 5 の仲間（自分も）の体力 +0.5", "Each hit heals you and allies within 5 by 0.5")

# ======================================================== 帯 II (Lv 11〜20)
W("wild_oar_blade", "warrior", "greatsword", "uncommon", 12, "coast", 1.0, 0.0, [("REACH", 0.5)],
  "渡し守の櫂剣", "Ferryman's Oar Blade", "間合い +0.5", "+0.5 reach")
W("wild_dune_sword", "warrior", "sword", "rare", 16, "coast", 1.5, 0.10, [("CLEAVE", 0.35, 2.5)],
  "砂潜りの曲刀", "Dune Diver Scimitar", "薙ぎ: まわり 2.5 の敵にも 35%", "Sweep: enemies within 2.5 also take 35%")
W("wild_ramhorn_greatsword", "warrior", "greatsword", "epic", 19, "heights", 1.0, 0.175, [("EVERY_N_BOLT", 4, 0.70, 2)],
  "羊角の大剣", "Ramhorn Greatsword", "4 度目ごとに雷（相手とまわり 2 に 70%）", "Every 4th hit calls lightning (70% to the target and within 2)")
W("wild_shipwright_maul", "smith", "maul", "uncommon", 12, "coast", 1.0, 0.04, [("ARMOR_SHRED", 1, 4, 60)],
  "船大工の木槌", "Shipwright's Maul", "打った相手の防具 −1（3 秒・最大 −4）", "Hits lower the target's armor by 1 (3s, up to -4)")
W("wild_crab_hammer", "smith", "hammer", "rare", 16, "coast", 0.95, 0.03, [("SLOW", 30, 1)],
  "大蟹の鋏鎚", "Crab Claw Hammer", "打った相手を 1.5 秒挟む（遅く II）", "Hits pinch the target (Slowness II, 1.5s)")
W("wild_conductor_axe", "smith", "axe", "epic", 19, "heights", 1.0, 0.12, [("CHAIN", 0.35, 2, 5)],
  "雷拾いの避雷斧", "Conductor Axe", "打つと雷が近くの 2 体へ跳ぶ（35%）", "Hits arc lightning to 2 nearby enemies (35%)")
W("wild_tidewatch_staff", "mage", "staff", "uncommon", 12, "coast", 1.2, 0.0, [("MAX_MANA", 6)],
  "潮見の杖", "Tidewatch Staff", "マナの上限 +6", "+6 max mana")
W("wild_lure_staff", "mage", "staff", "rare", 16, "coast", 1.2, 0.04, [("WEAKEN", 60, 0.15)],
  "漁り火の杖", "Lurelight Staff", "打った相手を霧で包む（3 秒 与える傷 −15%）", "Hits wrap the target in fog (-15% damage dealt, 3s)")
W("wild_fluff_staff", "mage", "staff", "epic", 19, "heights", 1.2, 0.18, [("DELAYED_BOLT", 40, 0.50, 4)],
  "綿雷の杖", "Static Fleece Staff", "打った相手に帯電の印、2 秒後に雷（攻撃力 50%）", "Hits charge the target; lightning strikes 2s later (50%)")
W("wild_gull_bow", "archer", "bow", "uncommon", 12, "coast", 0, 0.0, [("ARROW_SPEED", 0.20)],
  "海鳥の弓", "Gullwing Bow", "矢の速さ +20%", "+20% arrow speed")
W("wild_nacre_bow", "archer", "bow", "rare", 16, "coast", 0, 0.05, [("PIERCE", 1, 0.0)],
  "真珠層の弓", "Nacre Bow", "矢が 1 体貫く", "Arrows pierce 1 enemy")
W("wild_eaglet_longbow", "archer", "longbow", "epic", 19, "heights", 0, 0.15, [("FAR_BONUS", 15, 0.25)],
  "雛鷲の長弓", "Eaglet Longbow", "15 ブロックより遠い相手に +25%", "+25% damage beyond 15 blocks")
W("wild_shore_dagger", "assassin", "dagger", "uncommon", 12, "coast", 2.0, 0.0, [("MOVE_SPEED", 0.05)],
  "磯走りの針", "Shore Runner Needle", "移動の速さ +5%", "+5% movement speed")
W("wild_hermit_knife", "assassin", "dagger", "rare", 16, "coast", 2.0, 0.06, [("FIRST_STRIKE", 0.40)],
  "宿借りの隠し刃", "Hermit's Hidden Blade", "体力が満ちた相手への初撃 +40%", "+40% on the first hit against a full-health target")
W("wild_stormwolf_fang", "assassin", "fang", "epic", 19, "heights", 2.0, 0.11, [("EXECUTE", 0.30, 0.35)],
  "雷狼の牙", "Stormwolf Fang", "体力 30% を切った相手に +35%", "+35% against targets under 30% health")
W("wild_shell_bell_staff", "shaman", "bell", "uncommon", 12, "coast", 1.4, 0.0, [("MAX_HEALTH", 2)],
  "浜の貝鈴杖", "Seashell Bell Staff", "体力の上限 +2", "+2 max health")
W("wild_fogbell_bell_staff", "shaman", "bell", "rare", 16, "coast", 1.4, 0.08, [("MARK", 0.08, 80)],
  "霧鐘の鈴杖", "Foghorn Bell Staff", "打った相手に霧の印 4 秒（仲間全員の傷 +8%）", "Hits mark the target for 4s (+8% damage from everyone)")
W("wild_loom_bell_staff", "shaman", "bell", "epic", 19, "heights", 1.4, 0.08, [("TEAM_HASTE", 0.10, 0.08, 80, 6)],
  "機織りの風鈴杖", "Loom Windbell Staff", "打つと 4 秒 まわり 6 の仲間の移動 +10%・傷 +8%", "Hits grant allies within 6 +10% speed and +8% damage for 4s")

# ======================================================== 帯 III (Lv 21〜30)
W("wild_gale_glaive", "warrior", "glaive", "rare", 22, "heights", 1.3, 0.08, [("REACH", 1.0), ("PIERCE_LINE", 0.60)],
  "風鳴りの薙刀", "Galesong Glaive", "間合い +1、前の 2 体まで（2 体目に 60%）", "+1 reach; the strike carries on to a second enemy behind (60%)")
W("wild_fortress_greatsword", "warrior", "greatsword", "epic", 26, "wastes", 0.9, 0.12, [("BERSERK", 0.5, 0.25)],
  "焼け砦の門剣", "Fortress Gate Greatsword", "背水: 体力が半分を切ると +25%", "Last stand: +25% damage below half health")
W("wild_lavajaw_greatsword", "warrior", "greatsword", "heroic", 30, "wastes", 1.0, 0.24, [("FIRE_TAKEN", -0.20), ("EVERY_N_BITE", 3, 0.60, 80)],
  "熔け顎の大剣", "Lavajaw Greatsword", "常に 火の傷 −20%。3 度目ごとに顎が閉じる（攻撃力 60% の追撃と燃え 4 秒）",
  "Always: -20% fire damage taken. Every 3rd hit the jaw snaps shut (extra 60% and burning for 4s)")
W("wild_snowsplitter_axe", "smith", "axe", "rare", 22, "heights", 1.0, 0.03, [("FREEZE_STACK", 3, 20)],
  "雪割りの斧", "Snowsplitter Axe", "打つたびに凍え（遅く I）、3 度で遅く III 1 秒", "Hits chill (Slowness I); every 3rd freezes (Slowness III, 1s)")
W("wild_bellows_hammer", "smith", "hammer", "epic", 26, "wastes", 0.9, 0.17, [("HEAT_STACK", 5, 1.0, 3)],
  "灰炉の鞴鎚", "Ashforge Bellows Hammer", "打つたびに熱（最大 5）、5 で次の一撃が爆ぜる（まわり 3 に 100%）",
  "Hits build heat (max 5); at 5 the next hit bursts (100% within 3)")
W("wild_ashrite_maul", "smith", "maul", "heroic", 30, "wastes", 0.85, 0.15, [("KILL_WARD", 3, 160), ("EVERY_N_ASHRAIN", 6, 0.80, 4, 40)],
  "灰の民の葬り鎚", "Ashfolk Funeral Maul", "常に 倒した敵の灰が膜 3（8 秒）。6 度目ごとに灰の雨（まわり 4 に 80%・目くらみ 2 秒）",
  "Always: kills give 3 absorption (8s). Every 6th hit rains ash (80% within 4, blinds 2s)")
W("wild_cloudreader_staff", "mage", "staff", "rare", 22, "heights", 1.2, 0.08, [("WEATHER", 0.20, 0.10)],
  "雲読みの杖", "Cloudreader Staff", "雨か嵐のとき +20%、晴れはマナの戻り +10%", "+20% damage in rain or storm; +10% mana regeneration in clear weather")
W("wild_emberbug_staff", "mage", "staff", "epic", 26, "wastes", 1.2, 0.20, [("EMBERBUG", 0.40, 40, 80)],
  "熾り蟲の籠杖", "Emberbug Cage Staff", "打つたびに熾り蟲が飛び、2 秒後にいちばん近い敵で弾ける（40%・燃やす）",
  "Each hit releases an emberbug that bursts on the nearest enemy after 2s (40%, burning)")
W("wild_warden_crown_staff", "mage", "staff", "heroic", 30, "heights", 1.2, 0.25, [("MANA_REGEN", 0.20), ("FEATHERS", 0.25, 3, 0.50, 2.5)],
  "羽守の焼け冠杖", "Scorched Warden Crown Staff", "常に マナの戻り +20%。打つと 25% で雷の羽 3 本（まわり 2.5 に各 50%）",
  "Always: +20% mana regeneration. Hits have a 25% chance to drop 3 thunder feathers (50% each within 2.5)")
W("wild_windcutter_bow", "archer", "bow", "rare", 22, "heights", 0, 0.10, [("RICOCHET", 0.50, 6)],
  "風切りの弓", "Windcutter Bow", "当たった矢が近くの敵へ一度跳ねる（50%）", "Arrows ricochet once to a nearby enemy (50%)")
W("wild_ashhook_bow", "archer", "bow", "epic", 26, "wastes", 0, 0.06, [("PULL", 2.0, 30, 0)],
  "灰ひきずりの鉤弓", "Ashdragger Hook Bow", "当たった相手を 2 ブロック手前へ引き、1.5 秒遅く", "Arrows drag the target 2 blocks toward you and slow it for 1.5s")
W("wild_crater_longbow", "archer", "longbow", "heroic", 30, "wastes", 0, 0.25, [("FIRE_TAKEN", -0.20), ("ARROW_BURST", 0.60, 2.5, 60)],
  "火口の大弓", "Crater Longbow", "常に 火の傷 −20%。満ちて放った矢が当たると爆ぜる（まわり 2.5 に 60%・燃やす）",
  "Always: -20% fire damage taken. Fully drawn arrows explode on impact (60% within 2.5, burning)")
W("wild_frostfang_dagger", "assassin", "dagger", "rare", 22, "heights", 2.0, 0.12, [("CRIT", 0.15, 0.8)],
  "霜牙の短剣", "Frostfang Dagger", "会心 15%（×1.8）", "15% critical chance (x1.8)")
W("wild_cinder_claw", "assassin", "claw", "epic", 26, "wastes", 2.0, 0.20, [("RAMP", 0.05, 0.30, 40)],
  "灰の屑の双爪", "Cinder Twin Claws", "同じ相手を続けて打つと +5% ずつ（最大 +30%）", "+5% per consecutive hit on the same target (max +30%)")
W("wild_venom_fang", "assassin", "fang", "heroic", 30, "wastes", 2.0, 0.30, [("BACKSTAB", 0.25), ("BACK_VENOM", 1.20, 80)],
  "灰炉の毒牙", "Ashforge Venom Fang", "常に 背から +25%。背からの一撃に灰の毒（4 秒で攻撃力の 120%）",
  "Always: +25% from behind. Backstabs inject ash venom (120% of attack over 4s)")
W("wild_peak_totem", "shaman", "totem", "rare", 22, "heights", 1.4, 0.0, [("TEAM_GUARD", 0.08, 60, 6)],
  "峰の祈り柱", "Summit Prayer Totem", "打つと 3 秒 まわり 6 の仲間の受ける傷 −8%", "Hits grant allies within 6 -8% damage taken for 3s")
W("wild_ashsow_bell_staff", "shaman", "bell", "epic", 26, "wastes", 1.4, 0.0, [("LEECH_LOWEST", 0.08, 6)],
  "灰撒きの鈴杖", "Ashsower Bell Staff", "与えた傷の 8% を、まわり 6 でいちばん体力の低い仲間へ", "8% of damage dealt heals the most wounded ally within 6")
W("wild_stormdrum_totem", "shaman", "totem", "heroic", 30, "heights", 1.4, 0.10, [("AURA_DAMAGE", 0.05, 6), ("DRUM", 5, 6, 20, 2)],
  "嵐呼びの太鼓柱", "Stormcaller Drum Totem", "常に まわり 6 の仲間の傷 +5%。5 度目ごとに太鼓（まわり 6 の敵を 1 秒すくませ、仲間の体力 +2）",
  "Always: allies within 6 deal +5% damage. Every 5th hit beats the drum (enemies within 6 are stunned 1s, allies heal 2)")

# ======================================================== 帯 IV (Lv 31〜40)
W("wild_delver_sword", "warrior", "sword", "rare", 32, "abyss_upper", 1.5, 0.10, [("DARK_BONUS", 7, 0.12)],
  "探掘隊の柄長剣", "Delver's Longsword", "暗い所（明るさ 7 以下）で +12%", "+12% damage in darkness (light 7 or less)")
W("wild_saltcrystal_greatsword", "warrior", "greatsword", "epic", 35, "abyss_upper", 0.95, 0.10, [("ARMOR_DOWN", 3, 80)],
  "塩晶の大剣", "Saltcrystal Greatsword", "打った相手の防具 −3（4 秒）", "Hits lower the target's armor by 3 (4s)")
W("wild_rope_halberd", "warrior", "glaive", "epic", 38, "abyss_upper", 1.2, 0.09, [("HOOK_PULL", 120, 0.30)],
  "縄打ちの鉤槍", "Ropehook Halberd", "6 秒に一度、打った相手を引き寄せ、次の一撃 +30%", "Once every 6s, a hit drags the target in; your next hit deals +30%")
W("wild_inverted_greatsword", "warrior", "greatsword", "heroic", 40, "abyss_upper", 1.0, 0.18, [("FALL_TAKEN", -1.0), ("PLUNGE", 0.8, 0.60, 3)],
  "逆さ歩きの天剣", "Inverted Walker's Greatsword", "常に 落下の傷なし。落ちながら打つと ×1.8、まわり 3 に衝撃（60%）",
  "Always: no fall damage. Falling strikes deal x1.8 and shock enemies within 3 (60%)")
W("wild_pick_hammer", "smith", "hammer", "rare", 32, "abyss_upper", 1.0, 0.08, [("ARMOR_PIERCE", 0.20)],
  "探掘隊のつるはし鎚", "Delver's Pick Hammer", "相手の防具を 20% 無視", "Ignores 20% of the target's armor")
W("wild_saltmill_maul", "smith", "maul", "epic", 35, "abyss_upper", 0.9, 0.20, [("EVERY_N_QUAKE", 4, 0.80, 3)],
  "岩塩の臼鎚", "Rock Salt Millstone Maul", "4 度目ごとに地を割る（まわり 3 に 80%）", "Every 4th hit splits the ground (80% within 3)")
W("wild_blasting_axe", "smith", "axe", "epic", 38, "abyss_upper", 1.0, 0.10, [("KILL_BLAST", 0.50, 3)],
  "発破の斧", "Blasting Axe", "倒した敵が爆ぜる（まわり 3 に 50%）", "Slain enemies explode (50% within 3)")
W("wild_lampeater_hammer", "smith", "hammer", "heroic", 40, "abyss_upper", 0.9, 0.24, [("DARK_BONUS", 7, 0.15), ("LIFESTEAL", 0.08), ("EVERY_N_SPIT", 6, 1.20, 5)],
  "灯喰いの顎鎚", "Lampeater Jaw Hammer", "常に 暗い所で +15%。傷の 8% を体力に、6 度目ごとに呑んだ灯を吐く（前の扇 5 に 120%）",
  "Always: +15% in darkness. Heals 8% of damage dealt; every 6th hit spews swallowed light (120% in a cone of 5)")
W("wild_bonepipe_staff", "mage", "staff", "rare", 32, "abyss_upper", 1.2, 0.10, [("CLEAVE", 0.30, 3)],
  "骨笛の杖", "Bonepipe Staff", "打つと音が響き、まわり 3 の敵へ 30%", "Hits resonate: enemies within 3 take 30%")
W("wild_salttear_staff", "mage", "staff", "epic", 35, "abyss_upper", 1.2, 0.10, [("ROOT", 30, 120)],
  "塩の涙の杖", "Salt Tear Staff", "打った相手を 1.5 秒縛る（主は遅く II）、待ち 6 秒", "Hits root the target for 1.5s (bosses: Slowness II); 6s cooldown")
W("wild_compass_staff", "mage", "staff", "epic", 38, "abyss_upper", 1.2, 0.12, [("FAR_BONUS", 8, 0.20)],
  "探掘隊の羅針杖", "Delver's Compass Staff", "8 ブロックより遠い相手に +20%", "+20% damage beyond 8 blocks")
W("wild_mimic_heart_staff", "mage", "staff", "heroic", 40, "abyss_upper", 1.2, 0.18, [("MAX_HEALTH", 6), ("HEARTBEAT", 0.06, 10)],
  "鼓動もどきの心杖", "Mimic Heart Staff", "常に 体力 +6。傷の 6% を体力に、満ちているときは鼓動を溜め（最大 10）、10 で次の一撃 ×2",
  "Always: +6 max health. Heals 6% of damage; at full health stores heartbeats instead (max 10), and at 10 the next hit deals x2")
W("wild_saltcrow_bow", "archer", "bow", "rare", 32, "abyss_upper", 0, 0.10, [("SPLIT_EVERY", 3, 3, 0.60)],
  "塩鴉の弓", "Saltcrow Bow", "3 本目ごとに矢が 3 本に分かれる（各 60%）", "Every 3rd arrow splits into 3 (60% each)")
W("wild_bonestring_bow", "archer", "bow", "epic", 35, "abyss_upper", 0, 0.10, [("PIERCE", 2, 0.15)],
  "骨弦の弓", "Bonestring Bow", "矢が 2 体貫き、貫くたび +15%", "Arrows pierce 2 enemies, +15% per enemy pierced")
W("wild_lantern_bow", "archer", "bow", "epic", 38, "abyss_upper", 0, 0.10, [("MARK", 0.10, 120)],
  "灯籠の弓", "Lantern Bow", "当たった相手を 6 秒光らせる（仲間全員の傷 +10%）", "Arrows light the target for 6s (+10% damage from everyone)")
W("wild_hanged_longbow", "archer", "longbow", "heroic", 40, "abyss_upper", 0, 0.22, [("FALL_TAKEN", -0.5), ("HANG", 30, 0.30)],
  "逆さ吊りの長弓", "Hanged Longbow", "常に 落下の傷 −50%。満ちた矢で 1.5 秒吊る（主は遅く III）、吊った相手に +30%",
  "Always: -50% fall damage. Fully drawn arrows hang the target for 1.5s (bosses: Slowness III); +30% against hanged targets")
W("wild_ropecut_knife", "assassin", "dagger", "rare", 32, "abyss_upper", 2.0, 0.0, [("LIFESTEAL", 0.05)],
  "縄切りの鉤小刀", "Ropecutter Hook Knife", "傷の 5% を体力に", "Heals 5% of damage dealt")
W("wild_bone_needle_dagger", "assassin", "dagger", "epic", 35, "abyss_upper", 2.0, 0.20, [("BLEED_STACK", 5, 0.6)],
  "骨針の暗器", "Bone Needle Stiletto", "出血を重ねる（最大 5、1 秒ごとに重ね × 0.6）", "Stacks bleeding (max 5; 0.6 per stack each second)")
W("wild_ceiling_claw", "assassin", "claw", "epic", 38, "abyss_upper", 2.0, 0.12, [("AIR_BONUS", 0.30)],
  "逆さ歩きの鉤爪", "Ceiling Walker's Claw", "空中で当てると +30%", "+30% damage while airborne")
W("wild_snuffer_dagger", "assassin", "dagger", "heroic", 40, "abyss_upper", 2.0, 0.24, [("STEALTH", 0.5), ("SNUFF", 0.5, 60, 20)],
  "灯消しの短剣", "Snuffer Dagger", "常に 敵に気づかれにくい（狙われる距離 −50%）。背からの一撃 ×1.5 と灯を消す（相手は 3 秒 狙いを失い遅く。主は 1 秒）",
  "Always: enemies notice you at half range. Backstabs deal x1.5 and snuff the target's light (it loses you and slows for 3s; bosses 1s)")
W("wild_mourning_bell_staff", "shaman", "bell", "rare", 32, "abyss_upper", 1.4, 0.0, [("TEAM_REGEN", 100, 0, 6)],
  "弔いの手鈴杖", "Mourning Handbell Staff", "打つと 5 秒 まわり 6 の仲間に再生 I", "Hits grant allies within 6 Regeneration I for 5s")
W("wild_saltpillar_totem", "shaman", "totem", "epic", 35, "abyss_upper", 1.4, 0.0, [("WEAKEN", 100, 0.20)],
  "塩の柱の祭具", "Salt Pillar Totem", "打った相手の与える傷 −20%（5 秒）", "Hits make the target deal -20% damage (5s)")
W("wild_knotted_bell_staff", "shaman", "bell", "epic", 38, "abyss_upper", 1.4, 0.15, [("LINK", 100, 0.30, 6)],
  "縄結びの鈴杖", "Knotted Rope Bell Staff", "打った相手と近くの敵一体を 5 秒結ぶ（片方の傷の 30% をもう一方も）",
  "Hits bind the target to a nearby enemy for 5s (each shares 30% of the other's damage)")
W("wild_lullaby_bell_staff", "shaman", "bell", "heroic", 40, "abyss_upper", 1.4, 0.10, [("TEAM_GUARD_AURA", 0.06, 8), ("LULLABY", 8, 6, 40, 10, 3)],
  "深淵の子守鐘", "Abyssal Lullaby Bell Staff", "常に まわり 8 の仲間の受ける傷 −6%。8 度目ごとに子守唄（まわり 6 の敵を 2 秒眠らせる。主は 0.5 秒）、仲間の体力 +3",
  "Always: allies within 8 take -6% damage. Every 8th hit sings a lullaby (enemies within 6 sleep 2s, bosses 0.5s; allies heal 3)")

# ======================================================== 帯 V (Lv 41〜50)
W("wild_fulgurite_sword", "warrior", "sword", "rare", 42, "abyss_lower", 1.5, 0.12, [("PROC_BOLT", 0.12, 1.0)],
  "雷柱の剣", "Fulgurite Sword", "打つたびに 12% で雷（100%）", "Hits have a 12% chance to call lightning (100%)")
W("wild_twin_spear", "warrior", "spear", "epic", 44, "abyss_lower", 1.4, 0.10, [("REACH", 1.0), ("BACK_THRUST", 3, 1.0, 3)],
  "双頭の槍", "Twin-Headed Spear", "間合い +1。3 度目ごとに後ろも突く（後ろ 3 に 100%）", "+1 reach. Every 3rd hit also thrusts behind you (100% within 3)")
W("wild_chain_greatsword", "warrior", "greatsword", "epic", 46, "abyss_lower", 1.0, 0.12, [("REACH", 2.0), ("FAR_BONUS", 3, 0.15)],
  "淵の鎖剣", "Abyss Chain Greatsword", "間合い +2。3 ブロックより遠い相手に +15%", "+2 reach. +15% against targets beyond 3 blocks")
W("wild_meteor_greatsword", "warrior", "greatsword", "heroic", 48, "moon", 0.95, 0.10, [("MAX_HEALTH", 6), ("WARD_STACK", 6, 200, 0.10)],
  "隕鉄の大剣", "Meteoric Iron Greatsword", "常に 体力 +6。打つたびに膜 1（最大 6、10 秒）、膜があると +10%",
  "Always: +6 max health. Each hit grants 1 absorption (max 6, 10s); +10% damage while it holds")
W("wild_cloudcleaver_greatsword", "warrior", "greatsword", "heroic", 50, "cloud", 1.0, 0.20, [("MOVE_SPEED", 0.06), ("WIND_SLASH", 8, 0.50)],
  "雲断ちの大剣", "Cloudcleaver Greatsword", "常に 移動の速さ +6%。振るたびに前へ風の刃（8 ブロック・通った敵に 50%）",
  "Always: +6% movement speed. Each swing sends a wind blade forward (8 blocks, 50% to enemies it passes)")
W("wild_molten_axe", "smith", "axe", "rare", 42, "abyss_lower", 1.0, 0.10, [("BURN_BONUS", 60, 0.10)],
  "溶け鉄の斧", "Molten Iron Axe", "燃やす 3 秒、燃えている相手に +10%", "Sets targets ablaze (3s); +10% against burning targets")
W("wild_cog_hammer", "smith", "maul", "epic", 44, "abyss_lower", 0.9, 0.18, [("HASTE_RAMP", 0.05, 0.25, 40)],
  "歯車の大鎚", "Cogwheel Maul", "続けて打つたび攻撃の速さ +5%（最大 +25%、2 秒で戻る）", "Each consecutive hit adds +5% attack speed (max +25%, resets after 2s)")
W("wild_abyss_mace", "smith", "mace", "epic", 46, "abyss_lower", 0.9, 0.15, [("PULL_AOE", 2.0, 0.50, 2.5)],
  "深み呑みの鉄球", "Deepmaw Mace", "打った相手を 2 ブロック引き寄せ、まわり 2.5 に 50%", "Hits drag the target 2 blocks in and deal 50% within 2.5")
W("wild_starforge_hammer", "smith", "hammer", "heroic", 48, "moon", 0.9, 0.25, [("TOUGHNESS", 3), ("EVERY_N_SPARKS", 4, 1.0, 3, 60)],
  "星の炉の大鎚", "Starforge Hammer", "常に 強靭 +3。4 度目ごとに星の火花（まわり 3 に 100%・燃やす）",
  "Always: +3 toughness. Every 4th hit sprays star sparks (100% within 3, burning)")
W("wild_skysplit_axe", "smith", "axe", "heroic", 50, "cloud", 0.95, 0.22, [("ARMOR_PIERCE", 0.30), ("OPENER", 0.5, 0.20, 80, 0.20)],
  "天割りの両刃斧", "Skysplitter Axe", "常に 相手の防具を 30% 無視。体力が半分より多い相手に +20%、倒すと 4 秒 攻撃の速さ +20%",
  "Always: ignores 30% armor. +20% against targets above half health; kills grant +20% attack speed for 4s")
W("wild_ballspark_staff", "mage", "staff", "rare", 42, "abyss_lower", 1.2, 0.12, [("CHAIN", 0.30, 3, 5)],
  "雷玉の杖", "Ball Lightning Staff", "雷が近くの 3 体へ跳ぶ（30%）", "Hits arc lightning to 3 nearby enemies (30%)")
W("wild_hourglass_staff", "mage", "staff", "epic", 44, "abyss_lower", 1.2, 0.15, [("SLOW_BONUS", 60, 1, 0.15)],
  "灰の砂時計の杖", "Ash Hourglass Staff", "打った相手を 3 秒遅く II、遅い相手に +15%", "Hits slow the target (Slowness II, 3s); +15% against slowed targets")
W("wild_deep_eye_staff", "mage", "staff", "epic", 46, "abyss_lower", 1.2, 0.15, [("CRIT", 0.25, 0.6)],
  "深みの眼杖", "Deep Eye Staff", "会心 25%（×1.6）", "25% critical chance (x1.6)")
W("wild_starchart_staff", "mage", "staff", "heroic", 48, "moon", 1.2, 0.30, [("MANA_REGEN", 0.25), ("CONSTELLATION", 5, 2.0, 5)],
  "星図の杖", "Starchart Staff", "常に マナの戻り +25%。打つたびに星を描き、5 つで星座（まわり 5 の敵に 200%）",
  "Always: +25% mana regeneration. Each hit draws a star; at 5 the constellation strikes (200% within 5)")
W("wild_prism_staff", "mage", "staff", "heroic", 50, "cloud", 1.2, 0.22, [("COOLDOWN", 0.10), ("PRISM", 7)],
  "虹の端の杖", "Rainbow's End Staff", "常に 技の待ち −10%。打つたびに七色を順に（燃やす・凍え・雷・毒・縛り・癒し・印）",
  "Always: -10% skill cooldowns. Hits cycle the seven colours (burn, chill, lightning, poison, root, heal, mark)")
W("wild_flash_bow", "archer", "bow", "rare", 42, "abyss_lower", 0, 0.25, [("PROC_BOLT", 0.25, 1.0)],
  "稲光の弓", "Flashlight Bow", "当たると 25% で雷（矢の傷の 100%）", "Arrows have a 25% chance to call lightning (100%)")
W("wild_scope_longbow", "archer", "longbow", "epic", 44, "abyss_lower", 0, 0.20, [("HOLD_BONUS", 20, 0.35)],
  "遠眼鏡の長弓", "Spyglass Longbow", "満ちて 1 秒保つと、次の矢 +35%", "Hold a full draw for 1s: the arrow deals +35%")
W("wild_calling_bow", "archer", "bow", "epic", 46, "abyss_lower", 0, 0.12, [("HOMING", 6, 0.10)],
  "呼び声の弓", "Calling Bow", "矢が 6 ブロック内の敵へ曲がる、+10%", "Arrows curve toward enemies within 6; +10% damage")
W("wild_lighthouse_longbow", "archer", "longbow", "heroic", 48, "moon", 0, 0.30, [("BEAM", 40, 24, 1.5)],
  "灯台の大弓", "Lighthouse Longbow", "満ちて 2 秒保つと光の矢（直線 24 の全部の敵に ×1.5）", "Hold a full draw for 2s: fires a beam of light (x1.5 to every enemy in a 24-block line)")
W("wild_skylark_bow", "archer", "bow", "heroic", 50, "cloud", 0, 0.25, [("FALL_TAKEN", -1.0), ("AIR_SPLIT", 3, 0.70, 0.15)],
  "雲雀の弓", "Skylark Bow", "常に 落下の傷なし。空中で放つ矢は 3 本に分かれ（各 70%）、×1.15",
  "Always: no fall damage. Arrows loosed in the air split into 3 (70% each) and deal x1.15")
W("wild_spark_dagger", "assassin", "dagger", "rare", 42, "abyss_lower", 2.2, 0.03, [("STUN_CHANCE", 0.15, 10)],
  "火花の双刃", "Sparkjoin Dagger", "15% で相手を 0.5 秒しびれさせる", "15% chance to stun the target for 0.5s")
W("wild_ash_sickle_claw", "assassin", "claw", "epic", 44, "abyss_lower", 2.0, 0.15, [("MOVE_RAMP", 0.04, 0.20, 60, 0.30)],
  "灰被りの鎌爪", "Ashen Sickle Claw", "打つたびに移動 +4%（最大 +20%）、倒すと 3 秒 +30%", "Each hit adds +4% speed (max +20%); kills grant +30% speed for 3s")
W("wild_fishhook_knife", "assassin", "dagger", "epic", 46, "abyss_lower", 2.0, 0.12, [("HOOK", 1.5, 0.25)],
  "深みの釣り針", "Deepwater Fishhook Knife", "打った相手を手前へ引き、次の一撃 +25%", "Hits reel the target in; your next hit deals +25%")
W("wild_mirror_dagger", "assassin", "dagger", "heroic", 48, "moon", 2.0, 0.22, [("REFLECT", 0.10), ("MIRROR", 10, 1.0)],
  "鏡写しの短剣", "Mirror Dagger", "常に 受けた傷の 10% を相手へ返す。背からの一撃で 0.5 秒後に分身がもう一度",
  "Always: reflects 10% of damage taken. Backstabs are repeated by your reflection 0.5s later")
W("wild_cloudveil_claw", "assassin", "claw", "heroic", 50, "cloud", 2.0, 0.20, [("MOVE_SPEED", 0.08), ("VANISH", 0.08, 20, 1.0)],
  "雲隠れの爪", "Cloudveil Claw", "常に 移動の速さ +8%。打つたびに 8% で 1 秒姿を消し、次の一撃 ×2",
  "Always: +8% movement speed. Hits have an 8% chance to vanish you for 1s; your next hit deals x2")
W("wild_raincall_bell_staff", "shaman", "bell", "rare", 42, "abyss_lower", 1.4, 0.0, [("TEAM_MANA", 0.20, 80, 6)],
  "雨乞いの鈴杖", "Raincaller Bell Staff", "打つと 4 秒 まわり 6 の仲間のマナの戻り +20%", "Hits grant allies within 6 +20% mana regeneration for 4s")
W("wild_calming_censer", "shaman", "censer", "epic", 44, "abyss_lower", 1.4, 0.05, [("SMOKE", 60, 3, 0.10)],
  "鎮めの香炉", "Calming Censer", "打つたびに足もとに香の煙（3 秒・半径 3: 中の仲間は受ける傷 −10%、敵は遅く I）",
  "Each hit leaves incense smoke (3s, radius 3: allies inside take -10% damage, enemies are slowed)")
W("wild_effigy_totem", "shaman", "totem", "epic", 46, "abyss_lower", 1.4, 0.0, [("PROTECT", 0.15, 0.20, 6)],
  "身代わりの人形柱", "Effigy Totem", "まわり 6 の仲間の受ける傷の 15% を肩代わりし、自分の受ける傷 −20%",
  "Takes 15% of the damage dealt to allies within 6; you take -20% damage")
W("wild_starbell_totem", "shaman", "totem", "heroic", 48, "moon", 1.4, 0.10, [("TEAM_HEALTH_AURA", 4, 8), ("STAR_FRUIT", 6, 4, 0.10, 120, 6)],
  "星鈴の木", "Starbell Tree", "常に まわり 8 の仲間の体力の上限 +4。6 度目ごとに星の実が弾ける（まわり 6 の仲間の体力 +4・6 秒 傷 +10%）",
  "Always: allies within 8 gain +4 max health. Every 6th hit bursts a star fruit (allies within 6 heal 4 and deal +10% for 6s)")
W("wild_cloudhymn_bell_staff", "shaman", "bell", "heroic", 50, "cloud", 1.4, 0.05, [("REVIVE_SPEED", 2.0), ("HYMN", 0.10, 0.25, 80, 8)],
  "雲上の祝詞の鈴杖", "Cloudhymn Bell Staff", "常に 倒れた仲間を起こす速さ ×2。打つと 10% で祝詞（まわり 8 の仲間に 4 秒 受ける傷 −25%・体力の戻り 2/秒）",
  "Always: revive fallen allies twice as fast. Hits have a 10% chance to chant (allies within 8 take -25% damage and heal 2/s for 4s)")

# ---------------------------------------------------------------- 防具 (一式)
ARMORS = []


def A(id_, role, tier, lv, region, set2, set4, ja, en, set_ja, set_en):
    ARMORS.append(dict(id=id_, role=role, tier=tier, lv=lv, region=region, set2=set2, set4=set4, ja=ja, en=en, set_ja=set_ja, set_en=set_en))


PIECES = ("helmet", "chestplate", "leggings", "boots")
PIECE_JA = {"helmet": "兜", "chestplate": "胴", "leggings": "脚", "boots": "靴"}
PIECE_EN = {"helmet": "Hood", "chestplate": "Coat", "leggings": "Leggings", "boots": "Boots"}

A("wild_oilskin", "light", "uncommon", 3, "island", [], [("MOVE_SPEED", 0.05), ("SWIM", 0.5)],
  "漁り合羽", "Fisher's Oilskin", "4 部位: 移動 +5%、水の中の移動 +", "4 pieces: +5% speed, faster swimming")
A("wild_belfry", "mystic", "uncommon", 6, "meadow", [], [("MANA_REGEN", 0.15)],
  "鐘撞きの法衣", "Bellringer's Vestment", "4 部位: マナの戻り +15%", "4 pieces: +15% mana regeneration")
A("wild_oldsoldier", "heavy", "rare", 9, "meadow", [("MAX_HEALTH", 4)], [("LAST_STAND", 0.3, 60, 1200)],
  "骸兵の古鎧", "Old Soldier's Armor", "2 部位: 体力 +4。4 部位: 体力 30% を切ると 3 秒 耐性 II（待ち 60 秒）",
  "2 pieces: +4 max health. 4 pieces: below 30% health gain Resistance II for 3s (60s cooldown)")
A("wild_sailcloth", "light", "uncommon", 12, "coast", [], [("MOVE_SPEED", 0.05), ("BREATH", 1)],
  "汐守の帆布装", "Shiomori Sailcloth", "4 部位: 移動 +5%、水の中の息が長い", "4 pieces: +5% speed, longer breath underwater")
A("wild_lurelight", "mystic", "rare", 16, "coast", [("MAX_MANA", 5)], [("NIGHT_MANA", 0.25, 0.05)],
  "漁り火の外套", "Lurelight Mantle", "2 部位: マナの上限 +5。4 部位: 夜はマナの戻り +25%、傷 +5%",
  "2 pieces: +5 max mana. 4 pieces: at night +25% mana regeneration and +5% damage")
A("wild_ramfleece", "heavy", "epic", 19, "heights", [("TOUGHNESS", 2)], [("THORN_BOLT", 0.25, 4)],
  "雷角の毛皮鎧", "Ramhorn Fleece Armor", "2 部位: 強靭 +2。4 部位: 打たれると 25% で相手に雷（4）",
  "2 pieces: +2 toughness. 4 pieces: when hit, 25% chance to strike the attacker with lightning (4)")
A("wild_windweave", "light", "rare", 22, "heights", [], [("FALL_TAKEN", -0.5), ("MOVE_SPEED", 0.06)],
  "風待の織り装", "Kazamachi Windweave", "4 部位: 落下 −50%、移動 +6%", "4 pieces: -50% fall damage, +6% speed")
A("wild_ashleather", "mystic", "epic", 26, "wastes", [], [("FIRE_TAKEN", -0.25), ("BURN_BONUS_SET", 0.10), ("MANA_REGEN", 0.15)],
  "灰炉の焼き革", "Haibaro Ashleather", "4 部位: 火の傷 −25%、燃えている相手に +10%、マナの戻り +15%",
  "4 pieces: -25% fire damage taken, +10% against burning targets, +15% mana regeneration")
A("wild_fortress", "heavy", "heroic", 30, "wastes", [("TOUGHNESS", 3)], [("BULWARK", 0.05, 0.20)],
  "焼け砦の重鎧", "Burnt Fortress Plate", "2 部位: 強靭 +3。4 部位「砦」: 止まっていると 1 秒ごとに受ける傷 −5%（最大 −20%）",
  "2 pieces: +3 toughness. 4 pieces, Bulwark: standing still reduces damage taken by 5% per second (max -20%)")
A("wild_delver", "light", "rare", 32, "abyss_upper", [], [("FALL_TAKEN", -0.3), ("MOVE_SPEED", 0.04), ("NIGHT_VISION", 1)],
  "探掘隊の作業着", "Delver's Workwear", "4 部位: 落下 −30%、移動 +4%、暗い所で暗視", "4 pieces: -30% fall damage, +4% speed, night vision in the dark")
A("wild_saltplate", "heavy", "epic", 35, "abyss_upper", [], [("TOUGHNESS", 2), ("THORNS", 0.20, 4)],
  "塩晶の鎧", "Saltcrystal Plate", "4 部位: 強靭 +2、打たれると 20% で塩が刺さり返す（4）", "4 pieces: +2 toughness; when hit, 20% chance to stab the attacker with salt (4)")
A("wild_lampkeeper", "mystic", "epic", 38, "abyss_upper", [], [("MANA_REGEN", 0.25), ("LIGHT", 1)],
  "灯守の法衣", "Lampkeeper's Vestment", "4 部位: マナの戻り +25%、まわりが明るい", "4 pieces: +25% mana regeneration; you light up your surroundings")
A("wild_hanged", "light", "heroic", 40, "abyss_upper", [("MOVE_SPEED", 0.05)], [("NOOSE", 6, 3600)],
  "縄の人の吊り衣", "Hanged One's Shroud", "2 部位: 移動 +5%。4 部位「吊り」: 倒れる傷を体力 1 で耐え、後ろへ跳ぶ（待ち 180 秒）",
  "2 pieces: +5% speed. 4 pieces, Noose: survive a lethal blow at 1 health and leap back (180s cooldown)")
A("wild_copperwire", "mid", "rare", 42, "abyss_lower", [], [("LIGHTNING_TAKEN", -0.4), ("THORN_BOLT", 0.15, 5)],
  "銅線の避雷鎧", "Copperwire Armor", "4 部位: 雷の傷 −40%、打たれると 15% で雷を返す", "4 pieces: -40% lightning damage taken; when hit, 15% chance to return lightning")
A("wild_soot", "light", "epic", 44, "abyss_lower", [], [("MOVE_SPEED", 0.08), ("SNEAK_STEALTH", 0.5)],
  "煤の外套", "Soot Cloak", "4 部位: 移動 +8%、しゃがむと狙われにくい", "4 pieces: +8% speed; enemies lose track of you while sneaking")
A("wild_deepwarden", "heavy", "epic", 46, "abyss_lower", [], [("GRIT", 0.08, 0.16)],
  "深みの番兵鎧", "Deepwarden Armor", "4 部位: 受ける傷 −8%、体力が半分より少ないと −16%", "4 pieces: -8% damage taken, -16% below half health")
A("wild_stargazer", "mystic", "heroic", 48, "moon", [], [("MANA_REGEN", 0.35), ("NIGHT_DAMAGE", 0.08)],
  "星詠みの法衣", "Stargazer's Vestment", "4 部位: マナの戻り +35%、夜は与える傷 +8%", "4 pieces: +35% mana regeneration; +8% damage at night")
A("wild_cloudknight", "heavy", "heroic", 50, "cloud", [], [("CLOUD_WARD", 400, 6, 0.10)],
  "雲上の騎士鎧", "Cloud Knight Armor", "4 部位「雲の守り」: 20 秒ごとに膜 6、膜があるあいだ移動 +10%",
  "4 pieces, Cloud Guard: every 20s gain 6 absorption; +10% speed while it holds")

REGIONS = {
    "island": ("はじまりの島の品", "Item of the Isle of Beginnings"),
    "meadow": ("都のまわりの品", "Item of the Capital Meadows"),
    "hills": ("岩鯨の丘陵の品", "Item of the Stone Whale Hills"),
    "coast": ("潮の浜の品", "Item of the Tide Coast"),
    "heights": ("嵐の高地の品", "Item of the Storm Heights"),
    "wastes": ("灰の荒野の品", "Item of the Ash Wastes"),
    "abyss_upper": ("深淵の上の層の品", "Item of the Upper Abyss"),
    "abyss_lower": ("深淵の下の層の品", "Item of the Lower Abyss"),
    "moon": ("月の面の品", "Item of the Moon"),
    "cloud": ("雲海の品", "Item of the Sea of Clouds"),
}


def band(lv):
    return 1 if lv <= 10 else 2 if lv <= 20 else 3 if lv <= 30 else 4 if lv <= 40 else 5


def weapon_stats(w):
    """(攻撃力, 速さ) か 弓の (矢の倍率)"""
    if w["kind"] in ("bow", "longbow"):
        return round(bow_mult(w["lv"], w["tier"]) / (1 + w["gain"] * 0.5), 2)
    t = target(w["lv"], w["tier"])
    return round(t / (w["speed"] * (1 + w["gain"])), 2)


def armor_stats(a):
    d, t = ARMOR_PTS[a["lv"]]
    kd, kt = ROLE_K[a["role"]]
    total = d * kd
    pieces = [max(1, round(total * s)) for s in PIECE_SPLIT]
    return pieces, round(t * kt / 4, 1)


if __name__ == "__main__":
    from collections import Counter
    print(len(WEAPONS), "weapons", len(ARMORS), "armor sets")
    print(Counter((band(w["lv"]), w["job"]) for w in WEAPONS))
    for w in WEAPONS:
        print(w["id"], w["tier"], w["lv"], weapon_stats(w), round(target(w["lv"], w["tier"]), 1))
    for a in ARMORS:
        print(a["id"], armor_stats(a))
