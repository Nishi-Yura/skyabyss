"""Mod の進捗 (advancement) の木を作る。

出力:
  src/main/resources/data/skyabyss/advancement/**.json
  src/main/resources/assets/skyabyss/textures/gui/advancements/backgrounds/skyabyss.png (16x16 を並べる背景)
  src/main/resources/assets/skyabyss/lang/{ja_jp,en_us}.json に advancement.skyabyss.* を足す

使い方: python tools/gen/story/gen_advancements.py  (リポジトリ直下。何度動かしても同じ結果)

条件は 2 種類:
  - バニラ: minecraft:tick (根)、minecraft:changed_dimension (背に乗る)、minecraft:inventory_changed (持ち物)
  - この Mod の skyabyss:beat (advancement/Beats.java が名前の一覧)。{"event": "<名前>"}
方針: チャットに流さない (announce_to_chat false。「チャットが流れて見づらい」の試遊の指摘)。文は短く、先の物語を明かさない。
後の巨神 (潮殻・雷翼・余燼) と鍵は hidden (前の進捗を終えるまで見えない)。
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../../lib',)]
import json
from pathlib import Path

import numpy as np
from PIL import Image

from regroup_lore_chapters import CHAPTERS  # 章の名前は 1 か所 (story/Chapters.java と同じ表)

ROOT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources"
ADV = ROOT / "data/skyabyss/advancement"
LANG = ROOT / "assets/skyabyss/lang"
BG = ROOT / "assets/skyabyss/textures/gui/advancements/backgrounds/skyabyss.png"


def beat(event):
    return {"trigger": "skyabyss:beat", "conditions": {"event": event}}


def dimension(name):
    return {"trigger": "minecraft:changed_dimension", "conditions": {"to": f"skyabyss:{name}"}}


def has(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}


# id, 親, 文の鍵の元 (advancement.skyabyss.<id>.title/.description)、アイコン、枠、条件、hidden、ja (題, 文)、en (題, 文)
# 親は同じ階層の id。root は親なし
A = []


# 報酬の鐘銭 (進捗・実績のタブ。2026-10-21 の試遊: mod の実績と報酬も載せたい)。枠ごとの既定 (task 3・goal 10・challenge 25、根は 0)。
# 報酬は進捗の rewards.function (data/skyabyss/function/reward/coins_<n>.mcfunction) で渡し、説明の終わりに「報酬: 鐘銭 n」を書く
COINS = {"task": 3, "goal": 10, "challenge": 25}


def add(id_, parent, icon, frame, criterion, ja, en, hidden=False, coins=None):
    A.append(dict(id=id_, parent=parent, icon=icon, frame=frame, criterion=criterion, ja=ja, en=en, hidden=hidden,
                  coins=(0 if parent is None else COINS[frame]) if coins is None else coins))


# ---------------------------------------------------------------- 序盤 (17 の拍子の順)
add("root", None, "skyabyss:sky_key_fragment", "task", {"trigger": "minecraft:tick"},
    ("天と淵のあいだ", "空の下で、目を覚ました。"), ("Between Sky and Abyss", "You woke beneath the sky."))
# 2026-10-27: 大陸の RPG の流れ (はじまりの島 → 渡し → 都 → 土地の町) に合わせて作り直した。前の開幕 (野営の跡・鐘の宿り・語り部・イサナ・骨の丘) は大陸では起きないので消した
add("island_job", "root", "minecraft:iron_sword", "task", beat("job:chosen"),
    ("戦い方を選ぶ", "島の野営地で、自分の戦い方を決めた。"), ("Choosing a Way to Fight", "At the island camp, you chose how you fight."))
add("island_prints", "island_job", "minecraft:podzol", "task", beat("island:prints"),
    ("足あとを追う", "島の道に残った足あとをたどった。"), ("Following the Prints", "You followed the prints along the island road."))
add("island_bandits", "island_job", "minecraft:iron_axe", "task", beat("island:bandits"),
    ("峠を越える", "峠に居座る野盗を退けた。"), ("Over the Pass", "You drove off the bandits holding the pass."))
add("island_ferry", "island_bandits", "minecraft:oak_boat", "goal", beat("island:ferry"),
    ("海を渡る", "渡し守の舟で、大陸へ渡った。"), ("Across the Water", "The ferryman rowed you to the continent."))
add("capital_tour", "island_ferry", "minecraft:bell", "goal", beat("town:tour"),
    ("都をめぐる", "案内に連れられて、都の宿・鍛冶場・店をひと回りした。"), ("A Turn Around the Capital", "You were shown the capital's inn, smithy and shops."))
add("lore_book", "capital_tour", "skyabyss:lore_book", "task", has("skyabyss:lore_book"),
    ("伝承の書", "読むたびに、頁が増えていく本を手に入れた。"), ("The Book of Lore", "You hold a book whose pages grow as you learn."))
for _r, _icon, _ja, _en in (("whale", "skyabyss:stone_whale_plating", "鯨見の町", "Kujirami"), ("tide", "skyabyss:tide_dried_shell", "汐守の港", "Shiomori Harbor"),
                            ("storm", "skyabyss:thunder_wing_shard", "風待の里", "Kazamachi"), ("ember", "skyabyss:ember_shard", "灰炉の村", "Hairo")):
    add(f"town_{_r}", "capital_tour", _icon, "task", beat(f"town:visit:{_r}"),
        (_ja, f"{_ja}の門をくぐった。"), (_en, f"You passed through the gates of {_en}."), hidden=_r != "whale")
# 一章の支度の段 (34 の 4.2)。骨の坑の荷で前の旅人の頁を見つける → 三本 (鉄・剣・盾と糧) をそろえる
add("bone_pit", "town_whale", "minecraft:iron_ore", "task", beat("lore:ch1/trail_iron"),
    ("骨の坑", "坑の奥に、誰かの荷が残っていた。"), ("The Bone Pit", "Deep in the pit, someone's pack was left behind."))
add("whale_trail", "bone_pit", "skyabyss:whale_chaser_mark", "goal", beat("signal:ch1_ready"),
    ("鯨追いの支度", "鉄を着て、剣を研ぎ、盾と糧を背負った。"), ("Ready to Chase the Whale", "Iron on, blade honed, shield and provisions on your back."))

# ---------------------------------------------------------------- 巨神 (背に乗る → 試練 → 心臓)
add("stone_whale_board", "capital_tour", "skyabyss:stone_whale_plating", "goal", dimension("titan_back"),
    ("岩鯨の背", "岩の鯨の背に乗った。"), ("On the Stone Whale", "You climbed onto the stone whale's back."))
add("tablet", "stone_whale_board", "minecraft:chiseled_stone_bricks", "task", beat("tablet:read"),
    ("刻まれた言葉", "石の碑に目を向けると、昔の言葉が聞こえる。"), ("Carved Words", "Look at a stone tablet, and old words speak."))
add("stone_whale_trial", "stone_whale_board", "skyabyss:resonance_crystal", "goal", beat("trial:stone_whale"),
    ("最初の試練", "岩鯨の背の試練を、一つ越えた。"), ("The First Trial", "You cleared a trial on the stone whale."))
add("stone_whale_heart", "stone_whale_trial", "skyabyss:stone_whale_core", "challenge", beat("stopped:stone_whale"),
    ("岩鯨の心臓", "岩鯨の心臓を止めた。"), ("Heart of the Stone Whale", "You stilled the stone whale's heart."), hidden=True)
add("voyage", "stone_whale_heart", "skyabyss:titan_waymark", "goal", beat("signal:titan_voyage"),
    ("巨神の旅", "巨神の背で、別の土地へ渡った。"), ("Voyage", "You crossed to another land on a titan's back."))

# 二章の支度の段 (34 の 4.3)。沈んだ塩の蔵の奥の刻みを読む → 二本 (塩の道・船と貝) をそろえる
add("salt_vault", "stone_whale_heart", "minecraft:calcite", "task", beat("lore:ch2/salt_vault"),
    ("沈んだ塩の蔵", "蔵の奥の壁に、誰かの刻みがあった。"), ("The Sunken Salt Vault", "On the vault's back wall, someone had carved words."), hidden=True)
add("tide_trail", "salt_vault", "skyabyss:tide_watching_shell", "goal", beat("signal:ch2_ready"),
    ("潮渡りの支度", "塩の道を辿り、船で沖の難破船へ渡った。"), ("Ready to Cross the Tide", "You walked the salt road and rowed to the wreck offshore."), hidden=True)
add("tide_shell_board", "stone_whale_heart", "skyabyss:tide_dried_shell", "goal", dimension("tide_back"),
    ("潮殻の背", "潮を抱く亀の背に乗った。"), ("On the Tide Shell", "You climbed onto the tide-bearing turtle."), hidden=True)
add("tide_shell_trial", "tide_shell_board", "skyabyss:resonance_crystal", "goal", beat("trial:tide_shell"),
    ("満ち引きの試練", "潮殻の背の試練を、一つ越えた。"), ("Trial of the Tides", "You cleared a trial on the tide shell."), hidden=True)
add("tide_shell_heart", "tide_shell_trial", "skyabyss:tide_shell_core", "challenge", beat("stopped:tide_shell"),
    ("潮殻の心臓", "潮殻の心臓を止めた。"), ("Heart of the Tide Shell", "You stilled the tide shell's heart."), hidden=True)

# 三章の支度の段 (34 の 4.4)。焼けた見張り塔の頂の頁 → 三本 (避雷の柱・焼けた見張り塔・空の石) をそろえる
add("scorched_watch", "tide_shell_heart", "minecraft:lightning_rod", "task", beat("lore:ch3/scorched_watch"),
    ("焼けた見張り塔", "塔の頂の柱に、雷が落ち続けていた。"), ("The Scorched Watch", "Lightning kept falling on the pillar atop the tower."), hidden=True)
add("storm_trail", "scorched_watch", "minecraft:diamond", "goal", beat("signal:ch3_ready"),
    ("峰へ行く支度", "針を立て、塔を抜け、空の石を手にした。"), ("Ready for the Peaks", "Rods raised, the tower climbed, sky stone in hand."), hidden=True)
add("thunder_wing_board", "tide_shell_heart", "skyabyss:thunder_wing_shard", "goal", dimension("storm_back"),
    ("雷翼の背", "雷をまとう鳥の背に乗った。"), ("On the Thunder Wing", "You climbed onto the storm-winged bird."), hidden=True)
add("thunder_wing_trial", "thunder_wing_board", "skyabyss:resonance_crystal", "goal", beat("trial:thunder_wing"),
    ("風と雷の試練", "雷翼の背の試練を、一つ越えた。"), ("Trial of Wind and Thunder", "You cleared a trial on the thunder wing."), hidden=True)
add("thunder_wing_heart", "thunder_wing_trial", "skyabyss:thunder_wing_core", "challenge", beat("stopped:thunder_wing"),
    ("雷翼の心臓", "雷翼の心臓を止めた。"), ("Heart of the Thunder Wing", "You stilled the thunder wing's heart."), hidden=True)

# 四章の支度の段 (34 の 4.5)。消えた炉の坑の奥の頁 → 二本 (火除けの薬・炉跡) をそろえる
add("dead_forge_pit", "thunder_wing_heart", "minecraft:blast_furnace", "task", beat("lore:ch4/forge_page"),
    ("消えた炉の坑", "冷えた金床の上に、破れた頁があった。"), ("The Dead Forge Pit", "A torn page lay on the cold anvil."), hidden=True)
add("ember_trail", "dead_forge_pit", "minecraft:magma_cream", "goal", beat("signal:ch4_ready"),
    ("灰の地へ行く支度", "火の薬を作り、炉跡の頁を見つけた。"), ("Ready for the Ash", "Fire draughts brewed, the forge page found."), hidden=True)
add("ember_board", "thunder_wing_heart", "skyabyss:ember_shard", "goal", dimension("ember_back"),
    ("余燼の背", "燃え残る火の巨神の背に乗った。"), ("On the Ember", "You climbed onto the titan of lingering fire."), hidden=True)
add("ember_trial", "ember_board", "skyabyss:resonance_crystal", "goal", beat("trial:ember"),
    ("火守りの試練", "余燼の背の試練を、一つ越えた。"), ("Trial of the Fire-Keepers", "You cleared a trial on the ember titan."), hidden=True)
add("ember_heart", "ember_trial", "skyabyss:ember_core", "challenge", beat("stopped:ember"),
    ("余燼の心臓", "余燼の心臓を止めた。"), ("Heart of the Ember", "You stilled the ember titan's heart."), hidden=True)
add("sky_key", "ember_heart", "skyabyss:sky_key", "challenge", beat("signal:sky_key_assembled"),
    ("天の鍵", "四つの欠片が、一つになった。"), ("The Sky Key", "Four fragments became one."), hidden=True)

# ---------------------------------------------------------------- 実績 (二つ目のつまみ。都の暮らし・人との関わり・周回)
add("achievement/root", None, "skyabyss:bell_coin", "task", {"trigger": "minecraft:tick"},
    ("実績", "旅のあいだに成し遂げたこと。鐘銭が受け取れる。"), ("Achievements", "Things done along the way. Each pays bell coins."))
add("achievement/shop", "achievement/root", "minecraft:emerald", "task", beat("shop:buy"),
    ("はじめての買い物", "都の店で品を買った。"), ("First Purchase", "Bought something at a capital shop."), coins=5)
add("achievement/trade", "achievement/shop", "minecraft:chest", "goal", beat("trade:done"),
    ("取引成立", "取引所で、ほかの旅人と品を取り替えた。"), ("Deal Made", "Swapped goods with another traveller at the trading post."), coins=10)
add("achievement/quest", "achievement/root", "minecraft:writable_book", "task", beat("quest:done"),
    ("頼まれごと", "頼みを一つ、最後までやり遂げた。"), ("A Favour Done", "Saw a request through to the end."), coins=5)
add("achievement/board", "achievement/quest", "skyabyss:message_board", "task", beat("quest:board"),
    ("言伝ての板", "言伝ての板の頼みを果たした。"), ("The Message Board", "Finished a request from the message board."), coins=5)
add("achievement/town", "achievement/quest", "minecraft:bell", "goal", beat("quest:town"),
    ("都の顔なじみ", "都の人の頼みを果たした。"), ("A Familiar Face", "Helped someone in the capital."), coins=10)
add("achievement/guild", "achievement/root", "minecraft:white_banner", "goal", beat("guild:join"),
    ("旗の下に", "ギルドを作るか、ギルドに入った。"), ("Under One Banner", "Founded or joined a guild."), coins=10)
add("achievement/respec", "achievement/root", "minecraft:target", "task", beat("facility:respec"),
    ("鍛え直し", "訓練場の師範のもとで、技か力を振り直した。"), ("Back to Basics", "Reset your skills or stats with the training master."), coins=3)
add("achievement/grave", "achievement/root", "minecraft:skeleton_skull", "task", beat("grave:open"),
    ("置いてきたもの", "自分の墓から、品を取り戻した。"), ("What Was Left Behind", "Took your things back from your grave."), coins=3)


# ---------------------------------------------------------------- 名のある強敵 (docs/design/44 の 5。world/continent/NamedFoes が倒した人に beat を流す)
add("named_any", "root", "minecraft:golden_sword", "goal", beat("named:any"),
    ("名のある強敵", "名札を下げた強敵を、はじめて倒した。"), ("A Name Brought Down", "You felled a named foe for the first time."))
NAMED = (("island", "skyabyss:bandit_chief_spawn_egg", "goal", "峠の頭目 赤帯", "島の峠を荒らす赤帯の頭目を倒した。", "Redsash, Chief of the Pass", "You felled Redsash, who ruled the island pass."),
         ("meadow", "skyabyss:bone_standard_spawn_egg", "goal", "最後の旗手", "夜の草原をさまよう最後の旗手を倒した。", "The Last Standard-Bearer", "You felled the last standard-bearer wandering the night meadow."),
         ("whale", "skyabyss:rock_boar_spawn_egg", "challenge", "岩背の大猪", "岩鯨の丘の大猪を倒した。", "Rockback Boar", "You felled the great boar of the whale hills."),
         ("tide", "skyabyss:tide_crab_spawn_egg", "challenge", "潮招きの大蟹", "潮の浜の大蟹を倒した。", "Tidecaller Crab", "You felled the great crab of the tide shore."),
         ("storm", "skyabyss:storm_ram_spawn_egg", "challenge", "雷角の大羊", "嵐の高地の大羊を倒した。", "Thunderhorn Ram", "You felled the great ram of the storm heights."),
         ("ember", "skyabyss:ash_lizard_spawn_egg", "challenge", "灰被りの大蜥蜴", "灰の荒野の大蜥蜴を倒した。", "Ashcloak Lizard", "You felled the great lizard of the ash wastes."))
for _r, _icon, _frame, _ja, _jd, _en, _ed in NAMED:
    add(f"named_{_r}", "named_any", _icon, _frame, beat(f"named:{_r}"), (_ja, _jd), (_en, _ed), hidden=_r not in ("island", "meadow"))
add("named_all", "named_any", "minecraft:nether_star", "challenge", {f"named_{r[0]}": beat(f"named:{r[0]}") for r in NAMED},
    ("六つの名", "六つの土地の名のある強敵を、すべて倒した。"), ("Six Names", "You felled the named foe of every land."), hidden=True, coins=50)


# ---------------------------------------------------------------- 成長と鍛え
add("skill", "root", "minecraft:enchanted_book", "task", beat("skill:learned"),
    ("最初の技", "技を一つ覚えた。"), ("A First Skill", "You learned a skill."))
add("tree_sword", "skill", "skyabyss:whalebone_greatsword", "goal", beat("skill:tree:sword"),
    ("近接の道", "岩守り・風読み・灰の鍛え人のどれかの技を覚えた。"), ("The Melee Path", "Learned a skill of the Rockwarden, Windreader or Ashsmith."))
add("tree_bow", "skill", "minecraft:bow", "goal", beat("skill:tree:bow"),
    ("弓の道", "骨の射手の技を覚えた。"), ("The Bow Path", "Learned a skill of the Boneshooter."))
levels = [(5, "task", "minecraft:experience_bottle"), (10, "task", "minecraft:experience_bottle"), (20, "goal", "minecraft:experience_bottle"),
          (30, "goal", "minecraft:experience_bottle"), (40, "challenge", "minecraft:experience_bottle"), (50, "challenge", "minecraft:nether_star")]
prev = "root"
for n, frame, icon in levels:
    add(f"level_{n}", prev, icon, frame, beat(f"level:{n}"), (f"レベル {n}", f"レベル {n} に達した。"), (f"Level {n}", f"You reached level {n}."),
        hidden=n >= 30)
    prev = f"level_{n}"
add("forge", "root", "skyabyss:resonance_forge", "task", has("skyabyss:resonance_forge"),
    ("共鳴の炉", "装備を鍛える炉を手に入れた。"), ("Resonance Forge", "You got a forge for tempering gear."))
add("forge_enhance", "forge", "minecraft:anvil", "task", beat("forge:enhance"),
    ("一打ち", "炉で装備を鍛えた。"), ("A Single Strike", "You tempered gear at the forge."))
add("forge_dismantle", "forge_enhance", "minecraft:grindstone", "task", beat("forge:dismantle"),
    ("砕いて、残す", "要らない装備を分解して、素材に変えた。"), ("Break It Down", "You turned unwanted gear into materials."))


# 章 (docs/design/05 の 2 章、story/Chapters.java)。進捗の id は変えず、文の頭に章の名前を付けて枝を章ごとに読めるようにする。
# 序章 = B0〜B6 (野営地から足音まで)、一章 = イサナ (B7) から岩鯨の心臓まで。巨神の背・試練・心臓はその巨神の章。天の鍵は四章の終わり
CHAPTER_OF = {}
for _id in ("island_job", "island_prints", "island_bandits", "island_ferry"):
    CHAPTER_OF[_id] = "prologue"
for _id in ("bone_pit", "whale_trail", "stone_whale_board", "tablet", "stone_whale_trial", "stone_whale_heart", "voyage"):
    CHAPTER_OF[_id] = "titan_whale"
for _id in ("salt_vault", "tide_trail"):
    CHAPTER_OF[_id] = "titan_tide"
for _id, _ch in (("tide_shell", "titan_tide"), ("thunder_wing", "titan_storm"), ("ember", "titan_ember")):
    for _suffix in ("board", "trial", "heart"):
        CHAPTER_OF[f"{_id}_{_suffix}"] = _ch
CHAPTER_OF["scorched_watch"] = "titan_storm"
CHAPTER_OF["storm_trail"] = "titan_storm"
CHAPTER_OF["sky_key"] = "titan_ember"
CHAPTER_OF["dead_forge_pit"] = "titan_ember"
CHAPTER_OF["ember_trail"] = "titan_ember"
_CH = {c[0]: c for c in CHAPTERS}


def chaptered(a, idx):
    """(題, 文) の文の頭に「一章　岩鯨　」を付ける。idx 0 = ja、1 = en"""
    title, desc = a[("ja", "en")[idx]]
    ch = CHAPTER_OF.get(a["id"])
    if ch is None:
        return title, desc
    tab, name, _ = _CH[ch][2 + idx]
    return title, (f"{tab}　{name}　" if idx == 0 else f"Ch. {tab} {name}: ") + desc


def build():
    out = {}
    for a in A:
        d = {}
        if a["parent"]:
            d["parent"] = f"skyabyss:{a['parent']}"
        display = {
            "icon": {"id": a["icon"]},
            "title": {"translate": f"advancement.skyabyss.{a['id'].replace('/', '.')}.title"},
            "description": {"translate": f"advancement.skyabyss.{a['id'].replace('/', '.')}.description"},
            "frame": a["frame"],
            "show_toast": a["parent"] is not None,
            "announce_to_chat": False,
            "hidden": a["hidden"],
        }
        if a["parent"] is None:
            display["background"] = "skyabyss:textures/gui/advancements/backgrounds/skyabyss.png"
        d["display"] = display
        if "trigger" in a["criterion"]:
            d["criteria"] = {"done": a["criterion"]}
            d["requirements"] = [["done"]]
        else:
            # いくつもの条件をすべて満たす (六つの名など)
            d["criteria"] = dict(a["criterion"])
            d["requirements"] = [[k] for k in a["criterion"]]
        if a["coins"] > 0:
            d["rewards"] = {"function": f"skyabyss:reward/coins_{a['coins']}"}
        out[a["id"]] = d
    return out


def background():
    r = np.random.default_rng(5)
    base = np.array([26, 32, 44], float)
    img = np.zeros((16, 16, 3), float) + base
    img += r.normal(0, 3.2, (16, 16, 1))
    for _ in range(6):
        x, y = r.integers(0, 16, 2)
        img[y, x] += 14
    img[0, :] *= 0.92
    img[:, 0] *= 0.92
    BG.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(BG)


# 控え帳の「記し」のつまみ (record/DeedPages) の分け方と並び。根 (root・achievement/root) はひとりでに済むので載せない。
# 転生 (rebirth_*) はほかの道具が書く進捗だが、ここで成長の頁に並べる
DEED_PAGES = {
    "journey": ["island_job", "island_prints", "island_bandits", "island_ferry", "capital_tour", "lore_book", "town_whale", "town_tide", "town_storm", "town_ember",
                "bone_pit", "whale_trail", "voyage", "salt_vault", "tide_trail", "scorched_watch", "storm_trail", "dead_forge_pit", "ember_trail", "sky_key"],
    "titans": ["stone_whale_board", "tablet", "stone_whale_trial", "stone_whale_heart", "tide_shell_board", "tide_shell_trial", "tide_shell_heart",
               "thunder_wing_board", "thunder_wing_trial", "thunder_wing_heart", "ember_board", "ember_trial", "ember_heart"],
    "foes": ["named_any"] + [f"named_{r[0]}" for r in NAMED] + ["named_all"],
    "growth": ["level_5", "level_10", "level_20", "level_30", "level_40", "level_50", "skill", "tree_sword", "tree_bow", "forge", "forge_enhance", "forge_dismantle",
               "rebirth_1", "rebirth_5", "rebirth_10", "rebirth_20", "rebirth_flawless"],
    "town": ["achievement/shop", "achievement/trade", "achievement/quest", "achievement/board", "achievement/town", "achievement/guild", "achievement/respec",
             "achievement/grave"],
}
# 大陸の流れに合わなくなって消した進捗 (残っていれば消す)
RETIRED = ["camp", "footprint", "sky_shadow", "bell_lodge", "storyteller", "isana", "bone_hill"]


def main():
    ADV.mkdir(parents=True, exist_ok=True)
    for id_ in RETIRED:
        (ADV / f"{id_}.json").unlink(missing_ok=True)
    known = {a["id"] for a in A} | {f"rebirth_{n}" for n in ("1", "5", "10", "20", "flawless")}
    listed = [i for ids in DEED_PAGES.values() for i in ids]
    assert set(listed) <= known, set(listed) - known
    assert known - set(listed) == {"root", "achievement/root"}, known - set(listed)
    (ROOT / "data/skyabyss/deeds.json").write_text(json.dumps({"_comment": "控え帳の記しのつまみ (tools/gen/story/gen_advancements.py)", "pages": DEED_PAGES},
                                                                ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # ほかの道具が書く進捗 (転生の rebirth_* など) は消さない。ここで持つ id だけを書き直す
    for id_, d in build().items():
        f = ADV / f"{id_}.json"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    fn = ROOT / "data/skyabyss/function/reward"
    fn.mkdir(parents=True, exist_ok=True)
    for n in sorted({a["coins"] for a in A if a["coins"] > 0}):
        (fn / f"coins_{n}.mcfunction").write_text(f"# 進捗・実績の報酬 (tools/gen/story/gen_advancements.py)\ngive @s skyabyss:bell_coin {n}\n", encoding="utf-8")
    background()
    for code, idx in (("ja_jp", "ja"), ("en_us", "en")):
        p = LANG / f"{code}.json"
        lang = json.loads(p.read_text(encoding="utf-8"))
        for id_ in RETIRED:
            lang.pop(f"advancement.skyabyss.{id_}.title", None)
            lang.pop(f"advancement.skyabyss.{id_}.description", None)
        for a in A:
            title, desc = chaptered(a, 0 if idx == "ja" else 1)
            if a["coins"] > 0:
                desc += f"\n報酬: 鐘銭 {a['coins']}" if idx == "ja" else f"\nReward: {a['coins']} bell coins"
            key = a["id"].replace("/", ".")
            lang[f"advancement.skyabyss.{key}.title"] = title
            lang[f"advancement.skyabyss.{key}.description"] = desc
        p.write_text(json.dumps(lang, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(A)} advancements")


if __name__ == "__main__":
    main()
