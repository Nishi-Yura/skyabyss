"""Mod の進捗 (advancement) の木を作る。

出力:
  src/main/resources/data/skyabyss/advancement/**.json
  src/main/resources/assets/skyabyss/textures/gui/advancements/backgrounds/skyabyss.png (16x16 を並べる背景)
  src/main/resources/assets/skyabyss/lang/{ja_jp,en_us}.json に advancement.skyabyss.* を足す

使い方: python tools/gen_advancements.py  (リポジトリ直下。何度動かしても同じ結果)

条件は 2 種類:
  - バニラ: minecraft:tick (根)、minecraft:changed_dimension (背に乗る)、minecraft:inventory_changed (持ち物)
  - この Mod の skyabyss:beat (advancement/Beats.java が名前の一覧)。{"event": "<名前>"}
方針: チャットに流さない (announce_to_chat false。「チャットが流れて見づらい」の試遊の指摘)。文は短く、先の物語を明かさない。
後の巨神 (潮殻・雷翼・余燼) と鍵は hidden (前の進捗を終えるまで見えない)。
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

from regroup_lore_chapters import CHAPTERS  # 章の名前は 1 か所 (story/Chapters.java と同じ表)

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
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


def add(id_, parent, icon, frame, criterion, ja, en, hidden=False):
    A.append(dict(id=id_, parent=parent, icon=icon, frame=frame, criterion=criterion, ja=ja, en=en, hidden=hidden))


# ---------------------------------------------------------------- 序盤 (17 の拍子の順)
add("root", None, "skyabyss:sky_key_fragment", "task", {"trigger": "minecraft:tick"},
    ("天と淵のあいだ", "空の下で、目を覚ました。"), ("Between Sky and Abyss", "You woke beneath the sky."))
add("camp", "root", "minecraft:campfire", "task", beat("place:camp"),
    ("消えた火の跡", "誰かの野営の跡。火はもう冷えている。"), ("Cold Embers", "Someone camped here. The fire has gone cold."))
add("footprint", "camp", "minecraft:podzol", "task", beat("place:footprint"),
    ("巨大な足跡", "人のものではない足跡が、地面に深く残っている。"), ("A Giant's Footprint", "A footprint pressed deep into the ground. Not a person's."), hidden=True)
add("sky_shadow", "camp", "minecraft:phantom_membrane", "task", beat("signal:sky_shadow_seen"),
    ("空を渡る影", "空を、何か大きなものが横切った。"), ("The Shadow Overhead", "Something huge crossed the sky."), hidden=True)
add("bell_lodge", "camp", "minecraft:bell", "task", beat("place:shelter"),
    ("鐘の宿り", "小さく鳴る鐘をたどって、人のいる場所へ。"), ("Bell Lodge", "You followed a faint bell to where people live."))
add("storyteller", "bell_lodge", "minecraft:written_book", "task", beat("talk:skyabyss:storyteller"),
    ("語り部", "宿りの語り部と言葉を交わした。"), ("The Storyteller", "You spoke with the lodge's storyteller."))
add("lore_book", "storyteller", "skyabyss:lore_book", "task", has("skyabyss:lore_book"),
    ("伝承の書", "読むたびに、頁が増えていく本を手に入れた。"), ("The Book of Lore", "You hold a book whose pages grow as you learn."))
add("isana", "storyteller", "minecraft:spyglass", "task", beat("talk:skyabyss:whale_chaser"),
    ("鯨を追う人", "イサナと出会った。"), ("The Whale Chaser", "You met Isana."))
add("bone_hill", "isana", "minecraft:bone_block", "task", beat("place:ribs"),
    ("骨の丘", "丘の下に、巨大な骨が埋まっている。"), ("Bone Hill", "Enormous bones lie buried beneath the hill."), hidden=True)
# 一章の支度の段 (34 の 4.2)。骨の坑の荷で前の旅人の頁を見つける → 三本 (鉄・剣・盾と糧) をそろえる
add("bone_pit", "isana", "minecraft:iron_ore", "task", beat("lore:ch1/trail_iron"),
    ("骨の坑", "坑の奥に、誰かの荷が残っていた。"), ("The Bone Pit", "Deep in the pit, someone's pack was left behind."))
add("whale_trail", "bone_pit", "skyabyss:whale_chaser_mark", "goal", beat("signal:ch1_ready"),
    ("鯨追いの支度", "鉄を着て、剣を研ぎ、盾と糧を背負った。"), ("Ready to Chase the Whale", "Iron on, blade honed, shield and provisions on your back."))

# ---------------------------------------------------------------- 巨神 (背に乗る → 試練 → 心臓)
add("stone_whale_board", "isana", "skyabyss:stone_whale_plating", "goal", dimension("titan_back"),
    ("岩鯨の背", "岩の鯨の背に乗った。"), ("On the Stone Whale", "You climbed onto the stone whale's back."))
add("tablet", "stone_whale_board", "minecraft:chiseled_stone_bricks", "task", beat("tablet:read"),
    ("刻まれた言葉", "石の碑に目を向けると、昔の言葉が聞こえる。"), ("Carved Words", "Look at a stone tablet, and old words speak."))
add("stone_whale_trial", "stone_whale_board", "skyabyss:resonance_crystal", "goal", beat("trial:stone_whale"),
    ("最初の試練", "岩鯨の背の試練を、一つ越えた。"), ("The First Trial", "You cleared a trial on the stone whale."))
add("stone_whale_heart", "stone_whale_trial", "skyabyss:stone_whale_core", "challenge", beat("stopped:stone_whale"),
    ("岩鯨の心臓", "岩鯨の心臓を止めた。"), ("Heart of the Stone Whale", "You stilled the stone whale's heart."), hidden=True)
add("voyage", "stone_whale_heart", "skyabyss:titan_waymark", "goal", beat("signal:titan_voyage"),
    ("巨神の旅", "巨神の背で、別の土地へ渡った。"), ("Voyage", "You crossed to another land on a titan's back."))

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

add("ember_board", "thunder_wing_heart", "skyabyss:ember_shard", "goal", dimension("ember_back"),
    ("余燼の背", "燃え残る火の巨神の背に乗った。"), ("On the Ember", "You climbed onto the titan of lingering fire."), hidden=True)
add("ember_trial", "ember_board", "skyabyss:resonance_crystal", "goal", beat("trial:ember"),
    ("火守りの試練", "余燼の背の試練を、一つ越えた。"), ("Trial of the Fire-Keepers", "You cleared a trial on the ember titan."), hidden=True)
add("ember_heart", "ember_trial", "skyabyss:ember_core", "challenge", beat("stopped:ember"),
    ("余燼の心臓", "余燼の心臓を止めた。"), ("Heart of the Ember", "You stilled the ember titan's heart."), hidden=True)
add("sky_key", "ember_heart", "skyabyss:sky_key", "challenge", beat("signal:sky_key_assembled"),
    ("天の鍵", "四つの欠片が、一つになった。"), ("The Sky Key", "Four fragments became one."), hidden=True)

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
for _id in ("camp", "footprint", "sky_shadow", "bell_lodge", "storyteller", "lore_book"):
    CHAPTER_OF[_id] = "prologue"
for _id in ("isana", "bone_hill", "bone_pit", "whale_trail", "stone_whale_board", "tablet", "stone_whale_trial", "stone_whale_heart", "voyage"):
    CHAPTER_OF[_id] = "titan_whale"
for _id, _ch in (("tide_shell", "titan_tide"), ("thunder_wing", "titan_storm"), ("ember", "titan_ember")):
    for _suffix in ("board", "trial", "heart"):
        CHAPTER_OF[f"{_id}_{_suffix}"] = _ch
CHAPTER_OF["scorched_watch"] = "titan_storm"
CHAPTER_OF["storm_trail"] = "titan_storm"
CHAPTER_OF["sky_key"] = "titan_ember"
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
            "title": {"translate": f"advancement.skyabyss.{a['id']}.title"},
            "description": {"translate": f"advancement.skyabyss.{a['id']}.description"},
            "frame": a["frame"],
            "show_toast": a["id"] != "root",
            "announce_to_chat": False,
            "hidden": a["hidden"],
        }
        if a["id"] == "root":
            display["background"] = "skyabyss:textures/gui/advancements/backgrounds/skyabyss.png"
        d["display"] = display
        d["criteria"] = {"done": a["criterion"]}
        d["requirements"] = [["done"]]
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


def main():
    ADV.mkdir(parents=True, exist_ok=True)
    # ほかの道具が書く進捗 (転生の rebirth_* など) は消さない。ここで持つ id だけを書き直す
    for id_, d in build().items():
        (ADV / f"{id_}.json").write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    background()
    for code, idx in (("ja_jp", "ja"), ("en_us", "en")):
        p = LANG / f"{code}.json"
        lang = json.loads(p.read_text(encoding="utf-8"))
        for a in A:
            title, desc = chaptered(a, 0 if idx == "ja" else 1)
            lang[f"advancement.skyabyss.{a['id']}.title"] = title
            lang[f"advancement.skyabyss.{a['id']}.description"] = desc
        p.write_text(json.dumps(lang, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(A)} advancements")


if __name__ == "__main__":
    main()
