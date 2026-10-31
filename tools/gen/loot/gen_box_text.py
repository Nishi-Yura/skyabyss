"""封の箱の文言と封切り師の会話 (docs/design/52) を書く。"""
import json
import os

ROOT = "C:/Users/nisimoto/Desktop/mod/src/main/resources/"
LANG = ROOT + "assets/skyabyss_box/lang/"
os.makedirs(LANG, exist_ok=True)

TITANS = [("stone_whale", "岩鯨", "Stone Whale"), ("tide_shell", "潮殻", "Tide Shell"), ("thunder_wing", "雷翼", "Thunder Wing"), ("ember", "余燼", "Ember")]
TIERS = [("uncommon", "アンコモン", "Uncommon", [1, 2]), ("rare", "レア", "Rare", [1, 2, 3, 4, 5]), ("epic", "エピック", "Epic", [2, 3, 4, 5]),
         ("heroic", "ヒロイック", "Heroic", [3, 4, 5])]

ja, en = {}, {}
for t, j, e in TITANS:
    ja[f"item.skyabyss.box_{t}"] = f"{j}の封の箱"
    en[f"item.skyabyss.box_{t}"] = f"{e} Sealed Box"
for t, j, e, bands in TIERS:
    for b in bands:
        lo, hi = (b - 1) * 10 + 1, b * 10
        ja[f"item.skyabyss.box_{t}_{b}"] = f"{j}の封の箱 [Lv {lo}-{hi}]"
        en[f"item.skyabyss.box_{t}_{b}"] = f"{e} Sealed Box [Lv {lo}-{hi}]"
ja["item.skyabyss.box_artifact"] = "淵の金の箱"
en["item.skyabyss.box_artifact"] = "Abyssal Gold Box"

J = {
    "item.skyabyss.box.kind_titan": "巨神の箱 · %s",
    "item.skyabyss.box.kind_rarity": "%sの箱 · Lv %s〜%s",
    "item.skyabyss.box.kind_artifact": "金の箱 · 深淵のアーティファクトと伝説の武器",
    "item.skyabyss.box.contents": "武器・防具・装飾のどれかが入っている",
    "item.skyabyss.box.open_hint": "町の封切り師に渡すと開けてくれる",
    "message.skyabyss.box.opened": "封を切った (%s): %s",
    "skyabyss_box.tab": "封の箱",
    "skyabyss_box.screen.title": "封切り師",
    "skyabyss_box.screen.open": "開ける",
    "skyabyss_box.screen.open_all": "まとめて開ける (%s)",
    "skyabyss_box.screen.again": "もう一つ開ける",
    "skyabyss_box.screen.back": "一覧へ",
    "skyabyss_box.screen.count": "持っている数: %s",
    "skyabyss_box.screen.cutting": "封を切っている…",
    "skyabyss_box.screen.none": "封の箱を持っていない。敵や主がまれに落とす。巨神の背では、その巨神の箱が出る。",
    "skyabyss_box.screen.skip": "Esc で飛ばす",
    "skyabyss_box.screen.summary": "開けた品 (%s)",
    "skyabyss_box.pouch.titan": "巨神の箱",
    "skyabyss_box.pouch.rarity": "階級の箱",
    "skyabyss_box.pouch.artifact": "金の箱",
    "skyabyss_box.pouch.hint": "町の封切り師 (都と四つの町の鍛冶屋のそば) に渡すと、ルーレットで中身が決まる。",
    "skyabyss_pouch.category.box": "封の箱",
    "entity.skyabyss.attendant.box_opener": "封切り師",
    "entity.skyabyss.attendant_box_opener": "封切り師",
    "dialogue.skyabyss.attendant_box_opener.intro": "封の箱を持ってきたか。敵が落とす箱は、封を切るまで中身が眠っている。わしが切ってやろう。代はいらん。封の音を聞くのが好きでな。",
    "dialogue.skyabyss.attendant_box_opener.menu": "……箱か。",
    "dialogue.skyabyss.attendant_box_opener.menu.open": "箱を開けてもらう",
    "dialogue.skyabyss.attendant_box_opener.menu.sell": "品を買い取ってもらう",
    "dialogue.skyabyss.attendant_box_opener.menu.rules": "箱のこと",
    "dialogue.skyabyss.attendant_box_opener.menu.about": "ここは何をする所？",
    "dialogue.skyabyss.attendant_box_opener.menu.bye": "また来る",
    "dialogue.skyabyss.attendant_box_opener.rules": "巨神の背で落ちる箱は、その巨神の印の箱だ。中にはその巨神の品しか入っておらん。ほかの箱は色で階級が分かる。緑・青・紫・朱。帯の数は、中の品の重さ (Lv) の幅だ。金の箱は淵の底のもの。同じ箱なら重ねて持てる。",
}
E = {
    "item.skyabyss.box.kind_titan": "Titan box · %s",
    "item.skyabyss.box.kind_rarity": "%s box · Lv %s-%s",
    "item.skyabyss.box.kind_artifact": "Gold box · abyssal artifacts and legendary weapons",
    "item.skyabyss.box.contents": "Holds one weapon, armor piece or trinket",
    "item.skyabyss.box.open_hint": "A box opener in town will break the seal",
    "message.skyabyss.box.opened": "Seal broken (%s): %s",
    "skyabyss_box.tab": "Boxes",
    "skyabyss_box.screen.title": "Box Opener",
    "skyabyss_box.screen.open": "Open",
    "skyabyss_box.screen.open_all": "Open all (%s)",
    "skyabyss_box.screen.again": "Open another",
    "skyabyss_box.screen.back": "Back to list",
    "skyabyss_box.screen.count": "Held: %s",
    "skyabyss_box.screen.cutting": "Breaking the seal...",
    "skyabyss_box.screen.none": "You have no sealed boxes. Foes and lords drop them now and then; on a titan's back you find that titan's box.",
    "skyabyss_box.screen.skip": "Esc to skip",
    "skyabyss_box.screen.summary": "Opened (%s)",
    "skyabyss_box.pouch.titan": "Titan boxes",
    "skyabyss_box.pouch.rarity": "Rarity boxes",
    "skyabyss_box.pouch.artifact": "Gold box",
    "skyabyss_box.pouch.hint": "Bring them to a box opener (next to the smith in the capital and the four towns) to spin for the contents.",
    "skyabyss_pouch.category.box": "Sealed boxes",
    "entity.skyabyss.attendant.box_opener": "Box Opener",
    "entity.skyabyss.attendant_box_opener": "Box Opener",
    "dialogue.skyabyss.attendant_box_opener.intro": "Brought me a sealed box? What a foe drops stays asleep until the seal is cut. I'll cut it for you, no charge. I like the sound a seal makes.",
    "dialogue.skyabyss.attendant_box_opener.menu": "...A box?",
    "dialogue.skyabyss.attendant_box_opener.menu.open": "Open my boxes",
    "dialogue.skyabyss.attendant_box_opener.menu.sell": "Sell my gear",
    "dialogue.skyabyss.attendant_box_opener.menu.rules": "About the boxes",
    "dialogue.skyabyss.attendant_box_opener.menu.about": "What is this place?",
    "dialogue.skyabyss.attendant_box_opener.menu.bye": "I'll come back",
    "dialogue.skyabyss.attendant_box_opener.rules": "Boxes from a titan's back bear that titan's mark, and hold only its gear. The others show their rank by colour: green, blue, purple, vermilion. The band number is the Lv range inside. The gold box comes from the bottom of the abyss. Boxes of the same kind stack.",
}
TOWN_LINES = {
    "whale": ("……箱か。鯨骨の留めは固い。小刀でこじるのが一番だ。", "...A box? Whalebone clasps are stubborn. A small knife pries them best."),
    "tide": ("……箱か。潮で封が湿っとる。少し乾かしてから切ろう。", "...A box? The tide's dampened the seal. Let it dry a moment before I cut."),
    "storm": ("……箱か。風の強い日は、封がひとりでに鳴るんだ。", "...A box? On windy days the seals hum all by themselves."),
    "ember": ("……箱か。燠で焼き切れば、封は音もなく開く。", "...A box? Burn it with an ember and the seal opens without a sound."),
}
for town, (j, e) in TOWN_LINES.items():
    J["dialogue.skyabyss.attendant_box_opener.menu_" + town] = j
    E["dialogue.skyabyss.attendant_box_opener.menu_" + town] = e
J["dialogue.skyabyss.attendant_box_opener.menu"] = "……箱か。封蝋の匂いは、都の鐘の音より好きでね。"
E["dialogue.skyabyss.attendant_box_opener.menu"] = "...A box? I like the smell of sealing wax more than the capital's bells."
ja.update(J)
en.update(E)
assert set(ja) == set(en)
# 封切り師の名と会話の文は、ほかの係の人と同じ skyabyss_fac へ (NpcQuestSelfTest が会話の文を読む所)
FAC = ROOT + "assets/skyabyss_fac/lang/"
for name, d in (("ja_jp", ja), ("en_us", en)):
    fac_keys = {k: v for k, v in d.items() if k.startswith("dialogue.skyabyss.attendant_box_opener") or k.startswith("entity.skyabyss.attendant")}
    box = {k: v for k, v in d.items() if k not in fac_keys}
    with open(LANG + name + ".json", "w", encoding="utf-8") as f:
        json.dump(box, f, ensure_ascii=False, indent=2)
        f.write("\n")
    fac = json.load(open(FAC + name + ".json", encoding="utf-8"))
    fac.update(fac_keys)
    with open(FAC + name + ".json", "w", encoding="utf-8") as f:
        json.dump(fac, f, ensure_ascii=False, indent=2)
        f.write("\n")

# 町ごとの挨拶の一行 (メニューの文。docs/design/52 の 5 章)。都は "menu"、土地の町は "menu_<町>" (会話の条件 in_town)
K = "dialogue.skyabyss.attendant_box_opener."
OPTIONS = [
    {"id": "open", "kind": "talk", "text": K + "menu.open", "do": [{"type": "facility", "open": "boxes"}], "goto": "end"},
    # 買い取り (2026-11-01「アイテム売ってお金にできる機能を箱空けるnpcに」。facility/Buyback)
    {"id": "sell", "kind": "talk", "text": K + "menu.sell", "do": [{"type": "facility", "open": "buyback"}], "goto": "end"},
    {"id": "rules", "kind": "talk", "text": K + "menu.rules", "goto": "rules"},
    {"id": "about", "kind": "talk", "text": K + "menu.about", "goto": "again"},
    {"id": "bye", "kind": "leave", "text": K + "menu.bye", "goto": "end"},
]
nodes = {
    "intro": {"face": "smile", "do": [{"type": "flag", "flag": "intro"}], "next": "menu"},
    "again": {"face": "neutral", "next": "menu", "text": K + "intro"},
    "rules": {"face": "neutral", "next": "menu"},
    "menu": {"face": "neutral", "text": K + "menu", "options": OPTIONS},
}
entries = [{"if": {"type": "not", "of": {"type": "flag", "flag": "intro"}}, "node": "intro"}]
for town in TOWN_LINES:
    nodes["menu_" + town] = {"face": "neutral", "text": K + "menu_" + town, "options": OPTIONS}
    entries.append({"if": {"type": "in_town", "town": town}, "node": "menu_" + town})
entries.append({"node": "menu"})
dialogue = {"npc": "skyabyss:attendant_box_opener", "entries": entries, "nodes": nodes}
with open(ROOT + "data/skyabyss/dialogue/attendant_box_opener.json", "w", encoding="utf-8") as f:
    json.dump(dialogue, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(len(ja), "keys")
