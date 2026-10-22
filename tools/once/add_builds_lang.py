"""ビルド (5 つの木・36 の技・刻印・天職・刻み目・木の印) の lang を ja_jp.json / en_us.json に入れる。
何度実行しても同じ結果 (同じキーは上書き、前の剣士・弓使いの木のキーは消す)。docs/design/27_builds_and_classes.md。

使い方: python tools/once/add_builds_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent.parent / "src/main/resources/assets/skyabyss/lang"

REMOVE = ["skilltree.skyabyss.sword", "skilltree.skyabyss.sword.desc", "skilltree.skyabyss.sword.sealed",
          "skilltree.skyabyss.bow", "skilltree.skyabyss.bow.desc", "skilltree.skyabyss.bow.sealed"]

JA = {}
EN = {}


def both(key, ja, en):
    JA[key] = ja
    EN[key] = en


# ---------------------------------------------------------------- 木
TREES = {
    "rock": ("岩守り", "Rockwarden", "筋力と耐久で受け止める近接の技。敵の狙いを引き、仲間の盾になる。壁の役目。",
             "Close-range arts of Strength and Endurance. Draw the foe's aim and stand as the party's wall."),
    "wind": ("風読み", "Windreader", "敏捷で動く、短い間合いの攻め。踏み込み、跳び退き、背後からの二連。",
             "Quick close-range offense of Agility. Lunge, leap away, and cut twice from behind."),
    "tide": ("潮歌い", "Tidesinger", "精神で歌う支えと崩し。黄ハートとマナで仲間を支え、敵の足を鈍らせる。回復はしない。",
             "Support and disruption sung with Spirit. Shield allies with absorption and mana, slow foes. No healing."),
    "ash": ("灰の鍛え人", "Ashsmith", "筋力と精神で戦う炎の近接。武器を熾にし、槌で地を焼く。",
            "Fiery melee of Strength and Spirit. Set your weapon aglow and scorch the ground with a hammer."),
    "bone": ("骨の射手", "Boneshooter", "敏捷で遠くから射る。貫く矢、罠、印で、仲間の火力も上げる。",
             "Long-range shooting of Agility. Piercing arrows, traps and marks that lift the whole party's damage."),
    "traveler": ("旅人", "Wayfarer", "誰でも取れる共通の木。合図、備え、野営、そして刻印を持ち歩く手帖。",
                 "A common tree anyone may take. Signals, readiness, camp craft, and a journal that carries your runes."),
}
for tid, (ja, en, jad, end) in TREES.items():
    both(f"skilltree.skyabyss.{tid}", ja, en)
    both(f"skilltree.skyabyss.{tid}.desc", jad, end)
    both(f"skilltree.skyabyss.{tid}.sealed", "", "")

# ---------------------------------------------------------------- 技 (新しい 26 本。前の 10 本の文は残す)
# id: (名前, 名前 EN, 言い伝え, 言い伝え EN, 効果, 効果 EN, 型のおまけ, 型のおまけ EN)
SK = {
    "rock_call": ("岩呼び", "Rock Call", "「ここだ」と岩が呼ぶと、敵は足を止めて振り向く。", "\"Here,\" calls the rock, and the foe stops and turns.",
                  "周り %1$s ブロックの敵の狙いを %2$s 秒、自分へ引く。引いた敵の攻撃を受けても、押される強さは %3$s 倍。",
                  "Draws the aim of every foe within %1$s blocks onto you for %2$s s. Knockback from drawn foes is x%3$s.",
                  "引く時間 +1 秒", "+1 s of draw time"),
    "stone_oath": ("守り石の約束", "Oath of the Warding Stone", "石は黙って、仲間の分まで重みを受ける。", "The stone says nothing and bears the party's weight.",
                   "近く（%3$s ブロック以内）の仲間が受ける傷の %1$s%% を、代わりに自分が受ける（1 回 %2$s❤ まで）。仲間の体力は減りにくく、自分が減る。",
                   "Take %1$s%% of the damage allies within %3$s blocks would take (up to %2$s heart per hit). They lose less; you lose more.", "", ""),
    "nimble": ("軽業", "Nimble", "背の民は、足音を風に預けて歩く。", "The people of the back walk with their steps lent to the wind.",
               "地上の移動が %1$s%% 速くなり、落下の傷が %2$s ブロック分やわらぐ。",
               "Ground movement is %1$s%% faster, and falls hurt %2$s blocks less.", "移動 +2%", "+2% movement"),
    "foresight": ("見切り", "Foresight", "来ると知れば、紙一重で足りる。", "Knowing what comes, a hair's breadth is enough.",
                  "敵の予告（円・線）が自分に当たらず終わった直後 %2$s 秒、攻撃速度が %1$s%% 上がる。",
                  "For %2$s s after a foe's telegraph (circle or line) ends without hitting you, attack speed is %1$s%% higher.", "効果 +5%", "+5% effect"),
    "wind_rend": ("風裂き", "Wind Rend", "風は二度、同じ場所を裂く。背中は風の通り道。", "The wind tears the same place twice. A back is the wind's road.",
                  "素早い二連。一撃ごとに、正面なら攻撃力の %2$s%%、背後から当てると %1$s%%。",
                  "Two quick cuts. Each hits for %2$s%% of your attack from the front, %1$s%% from behind.", "倍率 +10%", "+10% multiplier"),
    "lone_wind": ("孤風", "Lone Wind", "群れを離れた風は、いちばん速い。", "A wind that left the herd runs fastest.",
                  "近く（%3$s ブロック以内）に他のプレイヤーがいないあいだ、攻撃力 +%1$s%%・移動 +%2$s%%。仲間がいれば効かない（一人遊びの木）。",
                  "While no other player is within %3$s blocks: attack +%1$s%%, movement +%2$s%%. Does nothing with company (the solo path).", "", ""),
    "ward_song": ("守りの唄", "Ward Song", "海の歌は、泡のように一度だけ守る。", "A sea song guards once, like a bubble.",
                  "近く（%1$s ブロック以内）の仲間（自分を含む）に、%4$s 秒の吸収 %2$s 点（黄ハート %3$s❤）。重ねがけしても増えない。",
                  "Grants allies within %1$s blocks (you included) %2$s absorption (%3$s yellow hearts) for %4$s s. Does not stack.", "", ""),
    "calm_heart": ("凪の心得", "Calm Heart", "凪いだ胸には、力がゆっくり満ちる。", "In a calm chest, strength fills slowly.",
                   "自分と近くの仲間のマナの回復が %1$s%% 速くなる（いちばん高い人の分だけ。重ならない）。",
                   "Mana regeneration for you and nearby allies is %1$s%% faster (the highest only; no stacking).", "", ""),
    "ebb_tide": ("引き潮", "Ebb Tide", "引く波は、足もとの砂ごと連れていく。", "The ebbing wave takes the sand from under your feet.",
                 "前方 %1$s ブロックの敵を足もとへ引き寄せ、%2$s 秒の鈍足。大きな相手は引かれず、鈍足だけ。",
                 "Pulls foes within %1$s blocks ahead to your feet and slows them for %2$s s. Large foes are only slowed.", "届き +1 ブロック", "+1 block of reach"),
    "ripple": ("波紋", "Ripple", "触れたものに、波は残る。", "What is touched keeps the wave.",
               "自分の技が当たった敵の移動が %2$s 秒、%1$s%% 遅くなる。大きな相手には半分。",
               "Foes hit by your skills move %1$s%% slower for %2$s s. Half against large foes.", "", ""),
    "cycle_song": ("巡りの唄", "Cycle Song", "唄は一巡りして、息をととのえる。", "The song goes round once and steadies the breath.",
                   "近く（%1$s ブロック以内）の仲間のマナの回復を %2$s 秒、%3$s%% 速める（自分は %4$s%%）。",
                   "Speeds mana regeneration of allies within %1$s blocks by %3$s%% for %2$s s (%4$s%% for you).", "", ""),
    "chorus_voice": ("共鳴の声", "Chorus Voice", "ひとりの声は、重なるほど遠くへ届く。", "One voice carries farther the more voices join.",
                     "近く（%3$s ブロック以内）の仲間 1 人につき、自分の技の効果が %1$s%% 増える（最大 %2$s 人）。自分だけなら効かない。",
                     "For each ally within %3$s blocks, your skills are %1$s%% stronger (up to %2$s allies). Nothing alone.", "", ""),
    "temper": ("焼き入れ", "Tempering", "熾の色の刃は、振るたびに息をする。", "A blade the color of embers breathes with each swing.",
               "手の武器を %1$s 秒のあいだ「熾」にする。攻撃に攻撃力の %2$s%% の炎の傷が乗り、敵を %3$s 秒燃やす。自分に、または見ている仲間（%4$s ブロック以内）1 人に向けられる。",
               "Sets your weapon aglow for %1$s s. Attacks add %2$s%% of attack as fire damage and burn the foe for %3$s s. Aim at yourself or one ally in view (%4$s blocks).",
               "炎の傷 +5%", "+5% fire damage"),
    "ember_heart": ("熾の心得", "Ember Heart", "火を知る者は、火に焼かれにくい。", "One who knows fire is slow to burn.",
                    "火の傷を受ける量が %1$s%% 減る。", "Fire damage you take is %1$s%% lower.", "", ""),
    "ember_hammer": ("熾火の槌", "Ember Hammer", "叩いた地は、しばらく眠らない。", "Ground that is struck does not sleep for a while.",
                     "前方に叩きつけ（半径 %1$s ブロック）、攻撃力の %2$s%%。地に %3$s 秒の熾火が残り、敵だけを燃やす。",
                     "Slams ahead (radius %1$s) for %2$s%% of attack. Embers stay %3$s s and burn only foes.", "半径 +0.5", "+0.5 radius"),
    "forge_memory": ("炉の記憶", "Forge Memory", "炉は、打たれた数だけ熱を覚えている。", "The forge remembers as much heat as it has been struck.",
                     "技を使うたびに熾が 1 つ溜まる（最大 %1$s）。次の近接の攻撃が、1 つにつき %2$s%% 強くなる（使うと全部消える）。",
                     "Each skill use gathers one ember (up to %1$s). Your next melee hit is %2$s%% stronger per ember (all are spent).", "", ""),
    "ash_scatter": ("灰散らし", "Ash Scatter", "灰をかぶれば、牙も矢も鈍る。", "Under ash, fang and arrow both dull.",
                    "周り %1$s ブロックの敵に %2$s 秒の「灰かぶり」。攻撃力 −%3$s%%、飛び道具の速さ −%4$s%%。",
                    "Foes within %1$s blocks are Ash-Covered for %2$s s: attack -%3$s%%, projectile speed -%4$s%%.", "", ""),
    "smith_eye": ("鍛え人の目", "Smith's Eye", "鍛えた分だけ、刃は技に応える。", "The more a blade is forged, the more it answers the art.",
                  "手の武器の強化段階 1 つにつき、技の傷が %1$s%% 増える（+10 で %2$s%%）。",
                  "Skill damage rises %1$s%% per enhancement level of your held weapon (%2$s%% at +10).", "", ""),
    "bone_trap": ("骨の罠", "Bone Trap", "骨は、踏まれるのを待つのがうまい。", "Bone is good at waiting to be stepped on.",
                  "足もとに骨の罠を置く（最大 %1$s つ）。踏んだ敵を %2$s 秒縛り、攻撃力の %3$s%%。大きな相手は縛られず、%4$s%% の傷だけ。",
                  "Sets a bone trap at your feet (up to %1$s). A foe that steps on it is bound for %2$s s and takes %3$s%% of attack. Large foes are not bound and take %4$s%%.", "", ""),
    "still_string": ("静かな弦", "Still String", "引き絞って待つ者の矢は、重い。", "The arrow of one who draws and waits is heavy.",
                     "%2$s 秒動かずにいて放った矢（と雲穿ち）の威力が %1$s%% 増える。動くとやり直し。",
                     "An arrow (or Cloud Pierce) loosed after standing still for %2$s s deals %1$s%% more. Moving resets it.", "", ""),
    "signal": ("標を打つ", "Mark Target", "声を出さずとも、光は伝わる。", "Light carries even where no voice does.",
               "見ている先（%1$s ブロック）に %2$s 秒の光の標を立てる。仲間全員に見える。敵に当てると %4$s 秒の印（%3$s%%。鷹の目と同じ。高いほう）。",
               "Raises a beacon of light %1$s blocks ahead for %2$s s, visible to all allies. Hitting a foe marks it for %4$s s (%3$s%%, like Hawk Eye; the higher applies).", "", ""),
    "readiness_reward": ("備えの報い", "Readiness Reward", "備えた者には、道がやさしい。", "The road is kind to those who prepared.",
                         "備えの三灯（身・刃・糧）がすべて青のあいだ、最大マナ +%1$s、攻撃力 +%2$s%%。",
                         "While all three lamps (body, blade, provisions) are blue: max mana +%1$s, attack +%2$s%%.", "", ""),
    "camp_hand": ("野営の手", "Camp Hand", "火の傍では、手がよく動く。", "Hands work well beside a fire.",
                  "焚き火・共鳴の炉の近くで、マナの回復が %1$s 倍、満腹度の減りが %2$s%% 小さくなる。",
                  "Near a campfire or resonance forge, mana regenerates x%1$s and hunger drains %2$s%% less.", "", ""),
    "appraiser": ("目利き", "Appraiser", "値打ちを見る目は、財布にも効く。", "An eye for worth helps the purse too.",
                  "共鳴の炉での振り直しの結晶が 1 つ減る（2 つ以上のとき）。特性の説明に、値の幅が出る。",
                  "Respecs at the resonance forge cost 1 fewer crystal (when 2 or more). Affix tooltips show the value range.", "", ""),
    "fleet_foot": ("逃げ足", "Fleet Foot", "逃げるのも、立派な技。", "Running away is a fine art too.",
                   "体力が %1$s❤ 以下になったとき、%3$s 秒、移動が %2$s%% 速くなる（%4$s 秒に 1 度）。",
                   "When your health drops to %1$s hearts or less, movement is %2$s%% faster for %3$s s (once per %4$s s).", "", ""),
    "journal": ("旅の手帖", "Traveler's Journal", "書きつけておけば、どこでも続きから始められる。", "Written down, it can be picked up anywhere.",
                "戦っていないあいだなら、炉や焚き火に行かなくても、刻印と型の保存を切り替えられる。",
                "While out of combat, switch runes and saved loadouts anywhere, without a forge or fire.", "", ""),
}
for sid, (ja, en, fja, fen, eja, een, tja, ten) in SK.items():
    both(f"skill.skyabyss.{sid}", ja, en)
    both(f"skill.skyabyss.{sid}.flavor", fja, fen)
    both(f"skill.skyabyss.{sid}.effect", eja, een)
    if tja:
        both(f"skill.skyabyss.{sid}.type", tja, ten)

# 前の 10 本のうち、型のおまけがあるもの
TYPES_OLD = {
    "titan_cleave": ("威力 +10%", "+10% power"),
    "stone_stance": ("防具 +1", "+1 armor"),
    "whale_sweep": ("半径 +0.5", "+0.5 radius"),
    "lunge": ("届き +1 ブロック", "+1 block of reach"),
    "cloud_pierce": ("貫通 +1", "+1 pierce"),
    "hawk_eye": ("印 +2%", "+2% mark"),
}
for sid, (ja, en) in TYPES_OLD.items():
    both(f"skill.skyabyss.{sid}.type", ja, en)

# ---------------------------------------------------------------- 刻印
RUNES = {
    "wide_blade": ("広刃", "Wide Blade", "扇が 180 度に広がる。威力 −10%。", "The cone widens to 180 degrees. Power -10%."),
    "deep_blade": ("深刃", "Deep Blade", "扇が 30 度に狭まり、いちばん近い 1 体だけに威力 ×1.4。", "The cone narrows to 30 degrees and hits only the nearest foe for x1.4."),
    "ground_run": ("地走り", "Ground Run", "扇をやめ、前方 8 ブロック・幅 1.5 の衝撃波が地を走る。", "No cone; a shockwave 8 blocks long and 1.5 wide runs along the ground."),
    "push_tide": ("押し潮", "Pushing Tide", "押し出しが 2 倍。威力 −20%。", "Knockback is doubled. Power -20%."),
    "pincer": ("挟み", "Pincer", "当てた敵を足もとへ引き寄せる（大きな相手は動かない）。", "Pulls struck foes to your feet (large foes do not move)."),
    "double_sweep": ("二度薙ぎ", "Double Sweep", "半分の力で 2 回薙ぐ。", "Sweeps twice at half power."),
    "long_voice": ("長い声", "Long Voice", "半径 12 ブロック。待ち +4 秒。", "Radius 12 blocks. Cooldown +4 s."),
    "heavy_voice": ("重い声", "Heavy Voice", "狙いを引いているあいだ、受ける傷 −10%。", "While foes are drawn, damage taken -10%."),
    "rock_return": ("岩の返り", "Rock Return", "引いた敵の攻撃を受けるたび、次の近接 +10%。", "Each time a drawn foe hits you, your next melee +10%."),
    "pass_through": ("通り抜け", "Pass Through", "敵の背後へ着く（足場があるとき）。", "Land behind the foe (if there is footing)."),
    "chain_lunge": ("連ね", "Chain Lunge", "斬りつけたとき、待ちが半分になる。", "On a hit, the cooldown is halved."),
    "pierce_through": ("突き通し", "Pierce Through", "通り道の敵全員に ×0.6 の傷。", "Every foe on the path takes x0.6 damage."),
    "return_fire": ("射返し", "Return Fire", "跳んだ直後 3 秒、飛び道具の威力 ×1.2。", "For 3 s after the leap, projectile damage x1.2."),
    "long_leap": ("長跳び", "Long Leap", "およそ 6 ブロック跳ぶ。", "Leap about 6 blocks."),
    "wind_remnant": ("風の置き土産", "Wind Remnant", "元の場所に 2 秒の渦が残り、敵の足を鈍らせる。", "A whirl stays 2 s where you stood and slows foes."),
    "back_take": ("背取り", "Back Take", "背後から当てると 1.8 倍、正面からは 0.75 倍。", "x1.8 from behind, x0.75 from the front."),
    "bleed": ("出血", "Bleed", "斬った敵が 3 秒、血を流す（継続傷）。", "The cut foe bleeds for 3 s (damage over time)."),
    "companion_wind": ("連れ風", "Companion Wind", "近くの仲間の移動が 4 秒、10% 速くなる。", "Nearby allies move 10% faster for 4 s."),
    "thick_song": ("厚い歌", "Thick Song", "吸収 +1（上限 4）。4 秒に縮む。", "Absorption +1 (cap 4). Shortened to 4 s."),
    "wide_song": ("広い歌", "Wide Song", "半径 12 ブロック。吸収 −1。", "Radius 12 blocks. Absorption -1."),
    "cycling_song": ("巡る歌", "Cycling Song", "吸収が割れたとき、近くの 1 人へ半分が移る。", "When the absorption breaks, half passes to a nearby ally."),
    "strong_tide": ("強い潮", "Strong Tide", "大きな相手も 1 ブロックほど引く。", "Large foes are pulled about a block too."),
    "whirl": ("渦", "Whirl", "引いた先に 2 秒の渦が残り、鈍足 III。", "A whirl stays 2 s there, Slowness III."),
    "push_back": ("押し返し", "Push Back", "引かずに、前へ押し出す（壁へ寄せられる）。", "Pushes ahead instead of pulling (to a wall)."),
    "long_fire": ("長い火", "Long Fire", "熾火が 8 秒続く。叩きの傷 −30%。", "Embers last 8 s. Slam damage -30%."),
    "double_strike": ("二度叩き", "Double Strike", "叩きが 2 回。傷は ×0.6 ずつ。", "Two slams, x0.6 damage each."),
    "fire_pillar": ("火柱", "Fire Pillar", "範囲が半径 1.5 に狭まり、中心の敵に ×1.8。", "Radius narrows to 1.5; the foe at the center takes x1.8."),
    "thin_arrow": ("細矢", "Thin Arrow", "貫通 +2。威力 −10%。", "Pierce +2. Power -10%."),
    "heavy_arrow": ("重矢", "Heavy Arrow", "貫通 −2。威力 ×1.3、速さ −30%。", "Pierce -2. Power x1.3, speed -30%."),
    "lightning": ("稲光", "Lightning", "矢が当たった敵の近くの別の敵へ、小さな雷（矢の傷の 30%）。", "A small bolt (30% of the hit) jumps to another foe near the one struck."),
}
for rid, (ja, en, eja, een) in RUNES.items():
    both(f"rune.skyabyss.{rid}", ja, en)
    both(f"rune.skyabyss.{rid}.effect", eja, een)

# ---------------------------------------------------------------- 武器の型・刻み目
for wid, ja, en in [("heavy", "重", "Heavy"), ("long", "長", "Long"), ("fast", "速", "Fast"), ("ranged", "遠", "Ranged"), ("guard", "守", "Guard")]:
    both(f"weapon_type.skyabyss.{wid}", ja, en)

BP = {
    "strength_15": ("筋力 15: 重い武器（大剣・斧・鎚）の振りの後隙が 8% 短くなる", "Strength 15: heavy weapons (greatsword, axe, hammer) recover 8% faster after a swing"),
    "strength_30": ("筋力 30: 範囲の技の押し出しが 1.25 倍", "Strength 30: area skills push 1.25x harder"),
    "agility_15": ("敏捷 15: 予告をよけた直後 2 秒、移動が 8% 速い", "Agility 15: for 2 s after dodging a telegraph, movement +8%"),
    "agility_30": ("敏捷 30: 踏み込み・跳び退き・風裂きの待ちが 10% 短い", "Agility 30: Lunge, Backstep and Wind Rend recover 10% faster"),
    "spirit_15": ("精神 15: マナの回復が 10% 速い", "Spirit 15: mana regenerates 10% faster"),
    "spirit_30": ("精神 30: 仲間に向ける技（唄・合図）の効果が 10% 増える", "Spirit 30: skills aimed at allies (songs, signal) are 10% stronger"),
    "vitality_15": ("耐久 15: ノックバック耐性 +10%", "Vitality 15: knockback resistance +10%"),
    "vitality_30": ("耐久 30: 火・毒の傷が 15% 減る", "Vitality 30: fire and poison damage -15%"),
}
for bid, (ja, en) in BP.items():
    both(f"breakpoint.skyabyss.{bid}", ja, en)
both("gui.skyabyss.growth.breakpoints", "刻み目", "Milestones")

# ---------------------------------------------------------------- 画面
G = {
    "gui.skyabyss.skill.loadout_load": ("型%s", "Set %s"),
    "gui.skyabyss.skill.loadout_save": ("保%s", "Sv%s"),
    "gui.skyabyss.skill.loadout_load_tip": ("型 %1$s に切り替える（枠と刻印。点は変わらない。%2$s）。炉・焚き火の近く、戦いの外で。旅の手帖があればどこでも。",
                                            "Switch to set %1$s (slots and runes; points do not change; %2$s). Near a forge or fire, out of combat; anywhere with the Journal."),
    "gui.skyabyss.skill.loadout_save_tip": ("今の枠と刻印を型 %1$s に保存する（%2$s）。", "Save the current slots and runes as set %1$s (%2$s)."),
    "gui.skyabyss.skill.loadout_saved": ("保存あり", "saved"),
    "gui.skyabyss.skill.loadout_empty": ("空", "empty"),
    "gui.skyabyss.skill.main_stat_none": ("全員が取れる共通の木", "A common tree anyone may take"),
    "gui.skyabyss.skill.kind_keystone": ("要（かなめ）", "Keystone"),
    "gui.skyabyss.skill.type_line": ("%1$sの型 ─ %2$s", "%1$s type: %2$s"),
    "gui.skyabyss.skill.runes_head": ("刻印", "Runes"),
    "gui.skyabyss.skill.runes_need_calling": ("（天職の木の技だけ）", "(calling trees only)"),
    "gui.skyabyss.skill.runes_need_max": ("（技を極めると開く %1$s/%2$s）", "(opens when mastered %1$s/%2$s)"),
    "gui.skyabyss.skill.runes_pick": ("（1 点で選ぶ）", "(1 point to choose)"),
    "gui.skyabyss.skill.rune_again": ("もう一度押すと付け替え（結晶 %s）", "Press again to swap (%s crystal)"),
    "gui.skyabyss.skill.rune_locked": ("刻印は、天職の木の技を極めると選べる。", "Runes open once you master a skill of a calling tree."),
    "gui.skyabyss.skill.slot_locked": ("この枠は、二つ目の天職（三章のあと）で開く。", "This slot opens with the second calling (after chapter 3)."),
    "gui.skyabyss.skill.respec_early": ("Lv %3$s になるまでは、どこでも無料で振り直せる。", "Until level %3$s, respec anywhere for free."),
    "gui.skyabyss.skill.rank_capped": ("天職の外の木の技は、段 %s まで。", "Skills outside your calling trees stop at rank %s."),
    "gui.skyabyss.skill.need_calling": ("天職を選ぶと、この行が開く。", "Choosing a calling opens this row."),
    "gui.skyabyss.skill.need_calling_deep": ("天職の木だけが、ここまで深くなる。", "Only a calling tree goes this deep."),
    "gui.skyabyss.skill.before_calling": ("岩鯨の心臓が止まったあと、天職を選ぶ。それまでは、どの木も 1 行目だけ試せる。", "After the whale's heart stops you choose a calling. Until then, only the first row of each tree can be tried."),
    "gui.skyabyss.skill.calling_choose": ("天職を選ぶ", "Choose a calling"),
    "gui.skyabyss.skill.calling_mark": ("天職", "Calling"),
    "gui.skyabyss.skill.calling2_mark": ("二つ目の天職", "Second calling"),
}
for k, (ja, en) in G.items():
    both(k, ja, en)

M = {
    "message.skyabyss.skill.rune_failed.not_available": ("その刻印は、まだ選べない。", "That rune cannot be chosen yet."),
    "message.skyabyss.skill.rune_failed.same": ("その刻印は、すでに選んでいる。", "That rune is already chosen."),
    "message.skyabyss.skill.rune_failed.no_points": ("技の点が足りない。", "Not enough skill points."),
    "message.skyabyss.skill.rune_failed.not_quiet": ("戦いの外（30 秒、傷を受けも与えもしない）でだけ付け替えられる。", "Runes can be swapped only out of combat (30 s without hurting or being hurt)."),
    "message.skyabyss.skill.rune_failed.no_place": ("炉か焚き火の近くで付け替える（旅の手帖があればどこでも）。", "Swap near a forge or campfire (anywhere with the Journal)."),
    "message.skyabyss.skill.rune_failed.no_crystals": ("共鳴の結晶が足りない。", "Not enough resonance crystals."),
    "message.skyabyss.skill.loadout_saved": ("型 %s を保存した。", "Saved set %s."),
    "message.skyabyss.skill.loadout_failed.empty": ("その型は、まだ保存していない。", "That set is not saved yet."),
    "message.skyabyss.skill.loadout_failed.not_quiet": ("戦いの外（30 秒）でだけ切り替えられる。", "Sets switch only out of combat (30 s)."),
    "message.skyabyss.skill.loadout_failed.no_place": ("炉か焚き火の近くで切り替える（旅の手帖があればどこでも）。", "Switch near a forge or campfire (anywhere with the Journal)."),
    "message.skyabyss.skill.learn_failed.calling": ("天職を選ぶまでは、ここは開かない。", "This stays closed until a calling is chosen."),
}
for k, (ja, en) in M.items():
    both(k, ja, en)

# 実績 (前の剣士・弓使いの文を、新しい木に合わせる)
both("advancement.skyabyss.tree_sword.title", "近接の道", "The Melee Path")
both("advancement.skyabyss.tree_sword.description", "岩守り・風読み・灰の鍛え人のどれかの技を覚えた。", "Learned a skill of the Rockwarden, Windreader or Ashsmith.")
both("advancement.skyabyss.tree_bow.title", "弓の道", "The Bow Path")
both("advancement.skyabyss.tree_bow.description", "骨の射手の技を覚えた。", "Learned a skill of the Boneshooter.")

both("key.skyabyss.skill_slot_5", "技の枠 5", "Skill Slot 5")

# ---------------------------------------------------------------- 天職 (P2)
CALLINGS = {
    "rock": ("岩守りの天職", "岩の肌", "防具強度 +0.5、ノックバック耐性 +10%。", "壁。敵の狙いを引き、仲間の前に立つ。",
             "Rockwarden", "Stone Skin", "Armor +0.5, knockback resistance +10%.", "Wall. Draw aim and stand before allies."),
    "wind": ("風読みの天職", "見切りの目", "予告の円・線の縁が少し濃く見える。移動 +3%。", "機動と近い攻め。背後と速さ。",
             "Windreader", "Eye of Foresight", "Telegraph edges look slightly bolder. Movement +3%.", "Mobile close offense. Backs and speed."),
    "tide": ("潮歌いの天職", "歌い手", "最大マナ +10、仲間に向ける技の範囲 +1 ブロック。", "支えと崩し。黄ハートとマナ、足止め。",
             "Tidesinger", "Singer", "Max mana +10, ally-aimed skills reach +1 block.", "Support and disruption. Absorption, mana, slowing."),
    "ash": ("灰の鍛え人の天職", "炉の手", "武器の強化の効果 +10%、火の傷 −10%。", "近接の攻めと火。熾と槌。",
            "Ashsmith", "Forge Hands", "Weapon enhancement effect +10%, fire damage -10%.", "Fiery melee offense. Embers and hammer."),
    "bone": ("骨の射手の天職", "骨の目", "飛び道具の威力 +5%、撃った矢の 25% は消えない。", "遠くからの攻めと罠。印で火力を上げる。",
             "Boneshooter", "Bone Eye", "Projectile damage +5%; 25% of arrows shot are not spent.", "Ranged offense and traps. Marks lift damage."),
}
for cid, (jn, jt, jd, jr, en_n, en_t, en_d, en_r) in CALLINGS.items():
    both(f"calling.skyabyss.{cid}", jn.replace("の天職", ""), en_n)
    both(f"calling.skyabyss.{cid}.trait", jt, en_t)
    both(f"calling.skyabyss.{cid}.trait.desc", jd, en_d)
    both(f"calling.skyabyss.{cid}.role", jr, en_r)

C = {
    "gui.skyabyss.calling.title": ("天職を選ぶ", "Choose a Calling"),
    "gui.skyabyss.calling.title2": ("二つ目の天職を選ぶ", "Choose a Second Calling"),
    "gui.skyabyss.calling.lead": ("巨神は眠った。あなたの手は、何を継ぐ？", "The titan sleeps. What will your hands inherit?"),
    "gui.skyabyss.calling.lead2": ("雷翼は眠った。もう一つ、手が覚えたものがある。", "The thunder-wing sleeps. Your hands learned one more thing."),
    "gui.skyabyss.calling.deep": ("この木が全部開く（要・刻印・天性）。ほかの木の技は 1 行目だけ・段 2 まで。", "This tree opens fully (keystone, runes, innate trait). Other trees keep only row 1, rank 2."),
    "gui.skyabyss.calling.deep2": ("この木は 3 行目まで開く（要と天性はなし）。枠が 1 つ増える。", "This tree opens to row 3 (no keystone or trait). One more slot opens."),
    "gui.skyabyss.calling.trait": ("天性: %s", "Innate: %s"),
    "gui.skyabyss.calling.role": ("役目: %s", "Role: %s"),
    "gui.skyabyss.calling.inherit": ("継ぐ", "Inherit"),
    "gui.skyabyss.calling.confirm": ("もう一度押して決める", "Press again to decide"),
    "gui.skyabyss.calling.later": ("あとで（K の画面から選べる）", "Later (choose from the K screen)"),
    "gui.skyabyss.calling.taken": ("すでに選んでいる", "Already chosen"),
    "message.skyabyss.calling.chosen": ("天職を継いだ: %s", "Calling inherited: %s"),
    "message.skyabyss.calling.chosen2": ("二つ目の天職を継いだ: %s", "Second calling inherited: %s"),
    "message.skyabyss.calling.offer": ("巨神は眠った。手が、継ぐものを探している。（K の画面から天職を選べる）", "The titan sleeps. Your hands look for something to inherit. (Choose a calling from the K screen.)"),
    "message.skyabyss.calling.offer2": ("雷翼も眠った。もう一つ、天職を継げる。（K の画面から）", "The thunder-wing sleeps too. You may inherit a second calling. (From the K screen.)"),
    "message.skyabyss.calling.failed": ("その天職は選べない。", "That calling cannot be chosen."),
}
for k, (ja, en) in C.items():
    both(k, ja, en)

# ---------------------------------------------------------------- 木の印 (P3)
MARKS = {
    "mark_stone": ("岩守りの技の力 +%s%%", "Rockwarden skill power +%s%%"),
    "mark_wind": ("風読みの技の力 +%s%%", "Windreader skill power +%s%%"),
    "mark_tide": ("潮歌いの技の力 +%s%%", "Tidesinger skill power +%s%%"),
    "mark_ash": ("灰の鍛え人の技の力 +%s%%", "Ashsmith skill power +%s%%"),
    "mark_bone": ("骨の射手の技の力 +%s%%", "Boneshooter skill power +%s%%"),
}
for k, (ja, en) in MARKS.items():
    both(f"affix.skyabyss.{k}", ja, en)

LEGEND = {
    "pike_bone": ("突き通し・骨", "Pierce Through: Bone", "踏み込み", "通り道の全員を突き、3 回に 1 度、足もとから骨が噴く。",
                  "Lunge", "Pierces everyone on the path; every third time bone erupts underfoot."),
    "fang_bite": ("噛みつき", "Bite", "風裂き", "二連が三連になり、背後の傷が出血に変わる（3 秒）。",
                  "Wind Rend", "Two cuts become three, and the damage from behind becomes bleeding (3 s)."),
    "shears_cut": ("挟み切り", "Shear", "鯨尾薙ぎ", "当てた敵を 1 秒、動けなくする（大きな相手は 0.5 秒）。",
                   "Whale Sweep", "Freezes struck foes for 1 s (0.5 s for large foes)."),
    "harpoon_tie": ("銛打ち", "Harpoon Tie", "引き潮", "敵を引くのではなく、銛で結ぶ。結ばれた敵が動くと近くの仲間も引かれる。",
                    "Ebb Tide", "Instead of pulling, ties foes with a harpoon. When a tied foe moves, its neighbors are dragged too."),
    "claw_ring": ("雷の輪", "Ring of Thunder", "跳び退き", "跳んだ元の場所と着地点が雷の輪で結ばれ、間の敵に ×0.8。",
                  "Backstep", "The start and landing points are joined by a ring of thunder; foes between take x0.8."),
    "halberd_hook": ("鉤寄せ", "Hook Gather", "岩呼び", "引いた敵を 1 か所へ寄せる（群れが固まる）。",
                     "Rock Call", "Gathers drawn foes into one spot (the pack clumps)."),
    "hammer_forge": ("二度打ち・火床", "Double Strike: Hearth", "熾火の槌", "熾火が 8 秒、敵が熾火の中にいる間、攻撃力 −10%。",
                     "Ember Hammer", "Embers last 8 s; foes standing in them deal 10% less."),
    "poker_share": ("火移し", "Fire Share", "焼き入れ", "仲間 2 人まで同時に焼き入れできる。",
                    "Tempering", "Temper up to two allies at once."),
}
for k, (ja, en, jskill, jd, eskill, ed) in LEGEND.items():
    both(f"legend.skyabyss.{k}", ja, en)
    both(f"legend.skyabyss.{k}.effect", jd, ed)
    both(f"legend.skyabyss.{k}.skill", jskill, eskill)
both("gui.skyabyss.skill.legend_head", "伝説の書き換え", "Legendary Rewrite")
both("gui.skyabyss.skill.legend_need", "その武器を持っているあいだだけ効く。%s を覚えている必要がある。", "Works only while you hold that weapon. Requires %s.")
both("gui.skyabyss.skill.rune_legend", "★ %s", "★ %s")
both("tooltip.skyabyss.legend_rewrite", "技の書き換え: %1$s ─ %2$s", "Skill rewrite: %1$s - %2$s")


def apply(name, extra):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k in REMOVE:
        data.pop(k, None)
    data.update(extra)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(name, len(extra), "keys")


if __name__ == "__main__":
    apply("ja_jp.json", JA)
    apply("en_us.json", EN)
