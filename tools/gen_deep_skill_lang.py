"""橋・淵・前世の技・紋・変異の lang (ja_jp / en_us) を末尾へ足す。docs/design/31 の A08・A09・A14・B08・B09。
すでに入っているキーは足さない (何度流しても同じ結果)。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "src/main/resources/assets/skyabyss/lang"

# (id, ja 名前, ja 言い伝え, ja 効果, en name, en flavor, en effect)
SKILLS = [
    ("dust_charge", "砂塵の盾突き", "岩は風に乗ると、砂の壁になる。",
     "前へ %1$s ブロック駆け抜け、通り道の敵を攻撃力の %2$s%% で打って脇へ押しのける。待ち %3$s 秒。",
     "Dust Shield-Rush", "Rock, carried by the wind, becomes a wall of sand.",
     "Rush %1$s blocks forward, striking foes on the path for %2$s%% attack and shoving them aside. Cooldown %3$s s."),
    ("sea_breeze", "潮風の唄", "追い風は、潮の匂いがする。",
     "移動 +%1$s%%、マナの回復 +%2$s%%。",
     "Tidewind Song", "A tailwind always smells of the sea.",
     "Movement +%1$s%%, mana regeneration +%2$s%%."),
    ("steam_bell", "蒸気の鐘", "灰の熱が潮に触れて、鐘になった。",
     "周り（半径 %1$s）の敵を攻撃力の %2$s%% で打ち、足を %3$s%% 鈍らせる（%4$s 秒）。",
     "Steam Bell", "Ash-heat touched the tide, and became a bell.",
     "Strike foes around you (radius %1$s) for %2$s%% attack and slow them by %3$s%% (%4$s s)."),
    ("cinder_volley", "熾の矢雨", "火の矢は、数を放てば空が赤くなる。",
     "前方の扇へ火の矢を %1$s 本。1 本ごとに手前の 2 体まで攻撃力の %2$s%%（射程 %3$s ブロック）、%4$s 秒燃える。",
     "Cinder Volley", "Fire the arrows in numbers, and the sky turns red.",
     "Loose %1$s fire bolts in a fan. Each hits up to 2 foes for %2$s%% attack (range %3$s blocks) and sets them alight for %4$s s."),
    ("stone_aim", "狙い石", "動かない者の矢は、石のように落ち着いている。",
     "%2$s 秒立ち止まると、骨のタグの技の力 +%1$s%%、防具 +%3$s。",
     "Stone Aim", "The arrow of one who stands still is as calm as a stone.",
     "After standing still for %2$s s: Bone-tag skills +%1$s%% power, armor +%3$s."),
    ("deep_pull", "淵の引き", "淵は覗く者を、少しだけ引く。",
     "周り（半径 %1$s）の敵を足もとの前へ寄せ、攻撃力の %2$s%% で打つ。足を %3$s%% 鈍らせる（%4$s 秒）。",
     "Deep Pull", "The deep tugs, a little, at those who look in.",
     "Draw foes around you (radius %1$s) to a point ahead and strike for %2$s%% attack. Slow them by %3$s%% (%4$s s)."),
    ("deep_mirror", "淵の鏡", "混ぜた数だけ、鏡は深くなる。",
     "タグが %2$s 種類以上あるとき、すべての技の力 +%1$s%%。",
     "Deep Mirror", "The more you mix, the deeper the mirror.",
     "While you count %2$s or more different tags, all skills +%1$s%% power."),
    ("deep_echo", "淵の残響", "終わったはずの技が、まだ鳴っている。",
     "%2$s 秒のあいだ、すべての技の力 +%1$s%%（傷そのものは与えない）。待ち %3$s 秒。",
     "Deep Echo", "The skill that should have ended is still ringing.",
     "For %2$s s, all skills +%1$s%% power (deals no damage itself). Cooldown %3$s s."),
    ("deep_tithe", "淵の貢ぎ", "多い者には要らない。少ない者への、淵の貢ぎ物。",
     "いちばん多いタグ以外で、1 つ以上あるタグのうち多い順に %1$s 種を、それぞれ +1 数える。",
     "Deep Tithe", "Not for the one with plenty: an offering from the deep to the one with few.",
     "Of your tags other than the most numerous, the %1$s largest (with at least 1) each count +1."),
    ("deep_maw", "淵の顎", "淵に口があるなら、笑っているだろう。",
     "見ている先（%3$s ブロック以内）の半径 %1$s の敵を引きずり込み、0.4 秒後に攻撃力の %2$s%% で噛み砕く。待ち %4$s 秒。",
     "Deep Maw", "If the deep has a mouth, it is smiling.",
     "Drag foes within radius %1$s of where you look (up to %3$s blocks) in, then bite for %2$s%% attack 0.4 s later. Cooldown %4$s s."),
    ("deep_oath", "淵の誓い", "五つの色を抱いた者にだけ、淵は誓う。",
     "岩・風・潮・灰・骨の 5 つのタグを 1 つ以上ずつ持つと、すべての技の力 +%1$s%%、マナの回復 +%2$s%%。",
     "Deep Oath", "The deep swears only to one who holds all five colors.",
     "While you count at least one of each tag (Rock, Wind, Tide, Ash, Bone): all skills +%1$s%% power, mana regeneration +%2$s%%."),
    ("echo_strike", "遺響の一撃", "前の人生の、最後の一撃の残り香。",
     "前方（%2$s ブロック以内）の 1 体を、攻撃力の合計 %1$s%% で打つ。その 65%% をすぐ、35%% を 0.5 秒後の残響で。",
     "Echoing Strike", "The lingering scent of the last blow of a past life.",
     "Strike one foe ahead (within %2$s blocks) for %1$s%% attack in total: 65%% now, 35%% as an echo 0.5 s later."),
    ("old_vigil", "旧き見張り", "夜を数えた者の、目の置き場所。",
     "防具 +%1$s、最大マナ +%2$s。",
     "Old Vigil", "Where the eyes rest of one who has counted the nights.",
     "Armor +%1$s, max mana +%2$s."),
    ("grave_bell", "墓鐘", "鳴らすと、かつての自分が足を止める。",
     "周り（半径 %1$s）の敵を攻撃力の %2$s%% で打ち、足を %3$s%% 重くする（%4$s 秒）。",
     "Grave Bell", "When it tolls, the person you once were stops in their tracks.",
     "Strike foes around you (radius %1$s) for %2$s%% attack and slow them by %3$s%% (%4$s s)."),
    ("memory_edge", "記憶の刃", "生まれ変わるたび、刃は少しだけ研がれる。",
     "転生 1 回につき、技の力 +%2$s%% × 段（%3$s 回ぶんまで。この段の最大 +%1$s%%）。",
     "Memory Edge", "With every rebirth the blade is honed a little.",
     "Per rebirth, skills +%2$s%% power x rank (up to %3$s rebirths; this rank's cap is +%1$s%%)."),
    ("final_light", "最期の光", "前の人生の最後の瞬間を、もう一度だけ。",
     "足もとから光の輪が三度ひろがり、周り（半径 %1$s）の敵を攻撃力の %2$s%% で打って押し出す。待ち %3$s 秒。",
     "Final Light", "The last moment of a past life, once more.",
     "Rings of light spread three times; foes around you (radius %1$s) take %2$s%% attack and are pushed out. Cooldown %3$s s."),
    ("soul_thread", "魂の糸", "何度結んでも、糸は同じ場所で切れない。",
     "すべての技の待ち −%1$s%%。",
     "Soul Thread", "However often it is tied, the thread never breaks in the same place.",
     "All skill cooldowns -%1$s%%."),
]

MUTATIONS = [
    ("cleave_cliff", "断崖", "扇の代わりに、前へ 10 ブロック・幅 3 の岩の列を突き上げる。傷は 8 割（大きな相手はさらに 1.5 倍）、最大 5 体。",
     "Cliff", "Instead of a fan, a column of rock 10 blocks long and 3 wide heaves up. 80% damage (x1.5 more on large foes), up to 5 foes."),
    ("sweep_tail", "鯨尾の地鳴らし", "1 度の薙ぎが、広がる 2 つの輪になる。内の輪と、0.4 秒後の外の輪（押し出し）に、それぞれ 6 割。",
     "Tail Quake", "One sweep becomes two spreading rings: 60% in the inner ring, 60% again in the outer ring 0.4 s later (with a shove)."),
    ("rend_blade", "飛ぶ刃", "近づかず、前へ 12 ブロック飛ぶ三日月の刃。行きに手前の 3 体、0.4 秒後の戻りにもう一度（8 割）。",
     "Flying Blade", "A crescent blade flies 12 blocks forward, hitting up to 3 foes, and returns 0.4 s later for 80% more."),
    ("rain_comet", "雷星", "6 回の雨を、1 つの大きな星にする。0.6 秒後に半径 3.5 へ一度に落ち、雨 6 回の 85%% を与える。",
     "Thunder Star", "The six pulses of rain become one great star: after 0.6 s it falls on radius 3.5 for 85% of the six pulses."),
    ("ebb_whirl", "渦潮", "前だけでなく周りの敵を足もとの 1 点へ巻き込み、足を鈍らせる。渦がしばらく残る。",
     "Whirlpool", "Foes on every side are drawn into one point and slowed. The whirl lingers."),
    ("lunge_surge", "潮走り", "狙いがいなくても、前へ 8 ブロック水のように駆け、通り道の全員を斬る。",
     "Tide Dash", "Even with no target, dash 8 blocks forward like water, cutting everyone in your path."),
    ("hammer_meteor", "熾の墜落", "前 6 ブロックへ火の塊を落とす。0.5 秒後に槌と同じ傷、熾火の床を残す。",
     "Ember Meteor", "A ball of fire falls 6 blocks ahead; 0.5 s later it deals the hammer's damage and leaves an ember bed."),
    ("trap_mine", "熾火の罠", "縛る罠の代わりに、見ている先へ火の地雷を埋める。踏まれると（踏まれなくても 6 秒で）半径 3 を吹き飛ばして燃やす。",
     "Ember Mine", "Instead of a binding trap, bury a fire mine where you look. When stepped on (or after 6 s) it blasts radius 3 and burns."),
]

GLYPHS = [
    ("rock", "岩の紋", "中心の技の力 +%1$s%%、隣の技 +%2$s%%。", "Rock Glyph", "Center skill +%1$s%% power, neighbors +%2$s%%."),
    ("wind", "風の紋", "中心の技の待ち −%1$s%%、隣の技 −%2$s%%。", "Wind Glyph", "Center skill cooldown -%1$s%%, neighbors -%2$s%%."),
    ("tide", "潮の紋", "中心の技のマナ −%1$s%%、隣の技 −%2$s%%。", "Tide Glyph", "Center skill mana -%1$s%%, neighbors -%2$s%%."),
    ("ash", "灰の紋", "中心の技の力 +%1$s%%（隣へは届かない）。", "Ash Glyph", "Center skill +%1$s%% power (does not reach neighbors)."),
    ("bone", "骨の紋", "中心の技の待ち −%1$s%%（隣へは届かない）。", "Bone Glyph", "Center skill cooldown -%1$s%% (does not reach neighbors)."),
]

TREES = [
    ("hybrid", "橋", "隣り合う 2 本の木で技を 3 つずつ覚えると開く、混ぜた人だけの技。輪の辺に 1 つずつ、全部で 5 つ。",
     "Bridges", "Skills for those who mix: open when you know 3 skills in each of two adjacent trees. One on each edge of the ring, five in all."),
    ("deep", "淵", "転生した人にだけ開く 6 本目の木。転生 1 回で入口、5 回で全段。ほかの木に混ぜるための技が中心。",
     "The Deep", "A sixth tree open only to the reborn. Entry at rebirth 1, every tier by rebirth 5. Built around skills meant to be mixed into the other trees."),
    ("past", "前世の技", "転生の回数で開く、前の人生の技。点は技の点とは別枠（転生 1 回につき 2 点、最大 12 点）。",
     "Past-Life Skills", "Skills of a past life, opened by your rebirth count. Their points are a separate pool (2 per rebirth, up to 12)."),
]

# gui / message
EXTRA = {
    "gui.skyabyss.ext.page": ("変異・紋", "Mutations"),
    "gui.skyabyss.ext.mutation_head": ("技の変異（巨神の素材。1 技に 1 つ）", "Skill mutations (titan materials, one per skill)"),
    "gui.skyabyss.ext.need_master": ("極めてから", "master it first"),
    "gui.skyabyss.ext.need_material": ("素材が足りない", "need materials"),
    "gui.skyabyss.ext.ready": ("素材そろい", "ready"),
    "gui.skyabyss.ext.confirm": ("もう一度で変異", "press again"),
    "gui.skyabyss.ext.mutation_of": ("%1$s の変異", "Mutation of %1$s"),
    "gui.skyabyss.ext.material": ("素材: %1$s %2$s/%3$s ・ %4$s %5$s/%6$s", "Materials: %1$s %2$s/%3$s, %4$s %5$s/%6$s"),
    "gui.skyabyss.ext.mutation_done": ("変異済み。外せない。刻印とは別に使える。", "Mutated. Cannot be undone. Works alongside runes."),
    "gui.skyabyss.ext.mutation_once": ("一度選ぶと外せない。力・待ち・マナは元のまま、形だけが変わる。", "Permanent once chosen. Power, cooldown and mana stay the same; only the shape changes."),
    "gui.skyabyss.ext.mutation_master": ("その技を極めると選べる。", "Available once the skill is mastered."),
    "gui.skyabyss.ext.mutated_line": ("変異「%1$s」: %2$s", "Mutation \"%1$s\": %2$s"),
    "gui.skyabyss.ext.glyph_head": ("紋の盤（穴 %1$s/%2$s）", "Glyph board (sockets %1$s/%2$s)"),
    "gui.skyabyss.ext.socket_closed": ("転生 %1$s で開く", "opens at rebirth %1$s"),
    "gui.skyabyss.ext.pick_head": ("中心の技（ホイールで送る）", "Center skill (scroll)"),
    "gui.skyabyss.ext.bag_head": ("持っている紋", "Your glyphs"),
    "gui.skyabyss.ext.bag_count": ("%1$s/%2$s", "%1$s/%2$s"),
    "gui.skyabyss.ext.bag_empty": ("紋は試練を越えるともらえる", "Glyphs drop when you clear a trial"),
    "gui.skyabyss.ext.glyph_name": ("%1$s  段 %2$s", "%1$s  Lv %2$s"),
    "gui.skyabyss.ext.glyph_tag": ("差している間、%1$s のタグを +1 数える", "While socketed, counts +1 %1$s tag"),
    "gui.skyabyss.ext.glyph_center": ("中心: %1$s", "Center: %1$s"),
    "gui.skyabyss.ext.hint_socket": ("先に穴を選ぶ", "Pick a socket first"),
    "gui.skyabyss.ext.hint_pick": ("先に中心の技を選ぶ（覚えている技）", "Pick a center skill first (one you know)"),
    "gui.skyabyss.ext.past_points": ("前世の点 %1$s/%2$s", "Past points %1$s/%2$s"),
    "gui.skyabyss.ext.need_rebirth": ("転生 %1$s 回から取れる", "Requires %1$s rebirths"),
    "gui.skyabyss.ext.need_bridge": ("橋: %1$s で %2$s/%5$s、%3$s で %4$s/%5$s（技の数）", "Bridge: %1$s %2$s/%5$s, %3$s %4$s/%5$s (skills known)"),
    "gui.skyabyss.ext.need_past_points": ("前世の点が足りない", "Not enough past points"),
    "message.skyabyss.skill.learn_failed.rebirth": ("転生の回数が足りない", "Not enough rebirths"),
    "message.skyabyss.skill.mutated": ("%1$s が「%2$s」に変異した", "%1$s mutated into \"%2$s\""),
    "message.skyabyss.skill.glyph_got": ("紋を授かった: %1$s（段 %2$s）", "Glyph gained: %1$s (Lv %2$s)"),
    "message.skyabyss.skill.glyph_full": ("紋を持ちきれない。盤か持ち物を空けてから", "Your glyph bag is full. Free some space"),
    "message.skyabyss.skill.ext_failed.unknown": ("その変異はない", "No such mutation"),
    "message.skyabyss.skill.ext_failed.not_mastered": ("その技を極めてから", "Master the skill first"),
    "message.skyabyss.skill.ext_failed.already": ("もう変異している", "Already mutated"),
    "message.skyabyss.skill.ext_failed.no_material": ("巨神の素材が足りない", "Not enough titan materials"),
    "message.skyabyss.skill.ext_failed.bad_socket": ("その穴はない", "No such socket"),
    "message.skyabyss.skill.ext_failed.closed": ("その穴はまだ開いていない（転生で開く）", "That socket is not open yet (opens with rebirth)"),
    "message.skyabyss.skill.ext_failed.bad_item": ("その紋はない", "No such glyph"),
    "message.skyabyss.skill.ext_failed.bad_skill": ("中心の技は覚えている技から", "The center must be a skill you know"),
    "message.skyabyss.skill.ext_failed.not_quiet": ("紋の差し替えは、戦いの外（30 秒）で", "Change glyphs outside combat (30 s)"),
    "message.skyabyss.skill.ext_failed.bag_full": ("紋を持ちきれない", "Your glyph bag is full"),
    "message.skyabyss.skill.ext_failed.bad": ("できなかった", "Could not do that"),
}


def entries(lang):
    ja = lang == "ja_jp"
    out = {}
    for sid, jn, jf, je, en, ef, ee in SKILLS:
        out[f"skill.skyabyss.{sid}"] = jn if ja else en
        out[f"skill.skyabyss.{sid}.flavor"] = jf if ja else ef
        out[f"skill.skyabyss.{sid}.effect"] = je if ja else ee
    for tid, jn, jd, en, ed in TREES:
        out[f"skilltree.skyabyss.{tid}"] = jn if ja else en
        out[f"skilltree.skyabyss.{tid}.desc"] = jd if ja else ed
        out[f"skilltree.skyabyss.{tid}.sealed"] = ""
    for mid, jn, je, en, ee in MUTATIONS:
        out[f"mutation.skyabyss.{mid}"] = jn if ja else en
        out[f"mutation.skyabyss.{mid}.effect"] = (je if ja else ee).replace("%%", "%").replace("%", "%%")
    for gid, jn, je, en, ee in GLYPHS:
        out[f"glyph.skyabyss.{gid}"] = jn if ja else en
        out[f"glyph.skyabyss.{gid}.effect"] = je if ja else ee
    for k, (j, e) in EXTRA.items():
        out[k] = j if ja else e
    return out


def main():
    for lang in ("ja_jp", "en_us"):
        path = LANG / f"{lang}.json"
        text = path.read_text(encoding="utf-8")
        existing = json.loads(text)
        add = {k: v for k, v in entries(lang).items() if k not in existing}
        if not add:
            print(lang, "nothing to add")
            continue
        body = text.rstrip()
        assert body.endswith("}")
        body = body[:-1].rstrip()
        lines = ",\n".join(f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}" for k, v in add.items())
        path.write_text(body + ",\n" + lines + "\n}\n", encoding="utf-8")
        json.loads(path.read_text(encoding="utf-8"))
        print(lang, "added", len(add))


if __name__ == "__main__":
    sys.exit(main())
