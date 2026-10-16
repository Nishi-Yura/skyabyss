"""余燼の巨神 (13) の品・伝承・締めの担当 (担当 G) の文の生成元。

書くもの:
  src/main/resources/data/skyabyss/lore/entry/chapter1/{ember_titan,ember_ruins,forge_ledger,ash_smith,ember_stilled,inscription_ember_*}.json
  src/main/resources/assets/skyabyss_ember/lang/{ja_jp,en_us}.json の「__ember.G」の区切りの下 (ほかの担当の行は触らない)

lang は余燼の全担当が一つのファイルを直す (13 の 11.2節)。この担当の行は、鍵の頭で見分ける (G_PREFIXES と G_KEYS)。
実行のたびに、この担当の行を消してから「__ember.G」の直後に書き直す。ほかの担当の行と順は変えない。

ほかの担当の文 (A 地上・B 広間・C 試練・E 鍛え手・F 心臓) のうち、語りの色の文は 13 から鍵を想定して、ここで日本語を先に書いた
(message.skyabyss.ember.lore.<担当>.*。Java の鍵は titan/ember/lore/EmberLore)。担当は自分で同じ文を書かずに、この鍵を使う。
文を直すときは、ここを直して実行する。

実行 (リポジトリ直下):
    python tools/gen_ember_lore.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/skyabyss/lore/entry/chapter1"
LANG = ROOT / "src/main/resources/assets/skyabyss_ember/lang"

JA: dict[str, str] = {}
EN: dict[str, str] = {}


def text(key: str, ja: str, en: str) -> None:
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


# この担当の行 (書き直す前に消す)
G_PREFIXES = (
    "message.skyabyss.ember.lore.",
    "tooltip.skyabyss.ember.",
    "sign.skyabyss.lore_ember_",
    "sign.skyabyss.ember_",
    "lore.skyabyss.chapter1.ember_titan.",
    "lore.skyabyss.chapter1.ember_ruins.",
    "lore.skyabyss.chapter1.forge_ledger.",
    "lore.skyabyss.chapter1.ash_smith.",
    "lore.skyabyss.chapter1.ember_stilled.",
    "lore.skyabyss.chapter1.inscription_ember_",
)
ITEMS = ["ember_helmet", "ember_chestplate", "ember_leggings", "ember_boots", "ember_armor", "ember_war_axe", "ember_mantle",
         "kindling_charm", "smith_hammer", "thrall_poker", "ember_lantern", "ember_horn", "ember_shard", "ember_core", "spark_dust",
         "cinder_husk"]
G_KEYS = {"gear.skyabyss.origin.ember", "gui.skyabyss.forge.exchange_no_clear.ember", "gui.skyabyss.forge.exchange_all_owned.ember",
          "skyabyss_record.h.ember", "item.skyabyss.lore_page.desc.ember_back"}


def owned(key: str) -> bool:
    if key in G_KEYS or key.startswith(G_PREFIXES):
        return True
    for item in ITEMS:
        if key == f"item.skyabyss.{item}" or key.startswith(f"item.skyabyss.{item}."):
            return True
    return False


# ================================================================ 看板 (13 の 9.2節)
# 碑文 6 枚 (4 行。1 行は日本語 8 字まで、英語は 90 px まで。SignFit が自己テストで測る)。
# 言いすぎない: 「なぜ分けたか」「下にいる者は何か」は書かない。四体目で「分けた者は まだ 下にいる」まで進める
INSCRIPTIONS = {
    "mouth": (("火に呑まれよ", "呑まれて 歩け", "喉の先に", "我らの炉がある"),
              ("Be swallowed,", "and walk on.", "Past the throat", "lies our forge")),
    "hub": (("この火は", "下から来た", "我らは 一度も", "絶やさなかった"),
            ("This fire came", "from below.", "Not once did we", "let it go out")),
    "west": (("灰をまけば", "血は冷える", "冷えた血は", "いずれ 崩れる"),
             ("Scatter ash,", "and blood cools.", "Cooled blood", "crumbles in time")),
    "east": (("鍵は 一つだった", "四つに割り", "四つの背に", "預けた"),
             ("The key was one.", "We broke it", "in four, and gave", "it to four backs")),
    "south": (("火が細れば", "巨神は眠る", "眠るたびに", "下へ 沈む"),
              ("When fire thins,", "the titan sleeps.", "Each sleep sinks", "it deeper")),
    "heart": (("四つの拍は", "一つから 分けた", "分けた者は", "まだ 下にいる"),
              ("Four beats were", "split from one.", "The one who split", "them is below")),
}
# 試練の入口の一言 3 枚 (2 行)
TRIAL_SIGNS = {
    "west": (("灰の道", "灰で道を作る"), ("Ash Path", "Ash makes a road")),
    "east": (("鍛えの間", "色を読んで打つ"), ("Forge Hall", "Strike by color")),
    "south": (("火守り", "火を絶やすな"), ("Firekeep", "Keep it burning")),
}
for place, (ja, en) in INSCRIPTIONS.items():
    for i in range(4):
        text(f"sign.skyabyss.lore_ember_{place}.{i + 1}", ja[i], en[i])
for trial, (ja, en) in TRIAL_SIGNS.items():
    for i in range(4):
        text(f"sign.skyabyss.ember_{trial}.{i + 1}", ja[i] if i < 2 else "", en[i] if i < 2 else "")

# ================================================================ 伝承の書 (13 の 9.3節)
# (ID, 順, 記される条件, 関連, 題, 本文, 看板)。本文は答えを書かず、問いか物で終える
LORE = [
    ("ember_titan", 177,
     [{"type": "near_entity", "target": "skyabyss:ember_titan", "radius": 200}, {"type": "dimension", "target": "skyabyss:ember_back"}],
     ["chapter1/ember", "chapter1/ember_ruins"],
     ("地を泳ぐもの", "The One That Swims the Earth"),
     ("畑の向こうの丘が、夜のうちに少しだけ近づいていた。朝、丘と畑のあいだの土だけが、帯のように温かかった。\n\n"
      "その晩、丘は煙を吐いて、眠っていた。",
      "The hill beyond the fields had come a little closer overnight. In the morning, only the soil between the hill and the fields "
      "was warm, in a long band.\n\nThat night the hill breathed smoke, and slept."),
     None),
    ("ember_ruins", 178,
     [{"type": "dimension", "target": "skyabyss:ember_back"}],
     ["chapter1/ember_titan", "chapter1/inscription_ember_hub"],
     ("灰の民の炉", "The Forge of the Ash Folk"),
     ("胸の奥に、大きな炉があった。鞴も金床も焼き入れの槽も、ついさっきまで使っていたように、手の届く所に置いてある。\n\n"
      "床の灰は、ところどころ、人の背丈ほどに積もっていた。",
      "Deep in the chest stood a great forge. Bellows, anvil, quenching trough: all within reach, as if someone had used them a moment ago.\n\n"
      "Here and there, the ash on the floor was heaped as high as a person."),
     None),
    ("forge_ledger", 178,
     [{"type": "page", "target": "ember_back"}, {"type": "manual"}],
     ["chapter1/ember_ruins", "chapter1/inscription_ember_south"],
     ("鍛えの帳", "The Forge Ledger"),
     ("炉にくべた物の数が、日ごとに書いてある。終わりに近づくほど、数が増えていく。火を保つのに要る物が、年ごとに増えていったらしい。\n\n"
      "最後の行は、数ではなく「下へ」とだけある。",
      "Each day, a count of what was fed to the forge. Toward the end the numbers grow. "
      "Year by year, it seems, the fire needed more to keep it alive.\n\n"
      "The last line is not a number. It says only: \"down.\""),
     None),
    ("ash_smith", 179,
     [{"type": "kill", "target": "skyabyss:ash_smith", "radius": 64}, {"type": "manual"}],
     ["chapter1/inscription_ember_east", "chapter1/ember_stilled"],
     ("灰の鍛え手", "The Ash Smith"),
     ("鎚が床に落ちても、火箸は塊を放さなかった。塊はまだ赤かった。\n\n最後に打つはずだった一打を、待っているようだった。",
      "The hammer fell to the floor, but the tongs never let go of the bloom. It was still red.\n\n"
      "As if it were waiting for the last blow it was meant to receive."),
     None),
    ("ember_stilled", 179,
     [{"type": "kill", "target": "skyabyss:ember_heart", "radius": 64}, {"type": "manual"}],
     ["chapter1/inscription_ember_heart", "chapter1/storm_stilled", "chapter1/sky_key_whole"],
     ("消えた火", "The Fire Gone Out"),
     ("余燼の心臓が止まった。血管の光が一つずつ消えて、部屋は急に冷えた。灰だけが、まだ降りつづけている。\n\n"
      "この火は、どこから来て、どこへ行ったのか。",
      "The ember heart stopped. One by one the veins went dark, and the room grew suddenly cold. Only the ash kept falling.\n\n"
      "Where did this fire come from, and where has it gone?"),
     None),
]
# 碑文ごとの項目 (看板の 4 行を写す。本文は見つけた場所)
INSCRIPTION_LORE = {
    "mouth": ("舌の上の碑文", "Inscription on the Tongue",
              "喉へ下りる手前、巨神の舌の上に立っていた。客を迎える言葉のようにも、言いつけのようにも読める。",
              "It stood on the titan's tongue, just before the throat. It reads like a welcome, or like an order."),
    "hub": ("炉心の広間の碑文", "Inscription of the Hearth Hall",
            "広間の大きな炉の脇にあった。炉の底をのぞくと、赤い光がずっと下まで続いている。",
            "It stood beside the great forge in the hall. Looking into the forge's floor, the red glow goes on far below."),
    "west": ("灰の道の碑文", "Inscription of the Ash Path",
             "灰の道の奥の岸にあった。読んでいるあいだにも、足もとの冷え殻が小さく鳴っていた。",
             "It stood on the far shore of the Ash Path. Even as you read, the cooled crust underfoot creaked."),
    "east": ("鍛え手の間の碑文", "Inscription of the Smith's Hall",
             "鍛え手の間の、焼き入れの槽の上に刻まれていた。四つのうちの一つを、いま自分が持っている。",
             "It was carved above the quenching trough in the Smith's Hall. One of the four is in your hands now."),
    "south": ("火床の碑文", "Inscription of the Hearth",
              "背の亀裂の底、火床の石に刻まれていた。火が細るたびに、足もとがかすかに沈む気がする。",
              "It was carved into the hearthstone at the bottom of the rift. Each time the fire thins, the ground seems to sink a little."),
    "heart": ("熾の底の前室の碑文", "Inscription Before the Ember Depths",
              "熾の底へ下りる前室の壁にあった。この一枚だけ、何度も彫り直した跡がある。",
              "It was on the wall of the room before the Ember Depths. This one alone has been carved over, again and again."),
}
for place, (tja, ten, bja, ben) in INSCRIPTION_LORE.items():
    related = ["chapter1/ember_ruins"]
    if place == "east":
        related = ["chapter1/ash_smith", "chapter1/sky_key_whole"]
    elif place == "heart":
        related = ["chapter1/ember_stilled", "chapter1/inscription_storm_heart"]
    LORE.append((f"inscription_ember_{place}", 178, [{"type": "sign", "target": f"sign.skyabyss.lore_ember_{place}"}], related,
                 (tja, ten), (bja, ben), f"sign.skyabyss.lore_ember_{place}"))

for lid, order, unlock, related, title, body, sign in LORE:
    text(f"lore.skyabyss.chapter1.{lid}.title", title[0], title[1])
    text(f"lore.skyabyss.chapter1.{lid}.body", body[0], body[1])
text("item.skyabyss.lore_page.desc.ember_back", "余燼の背で拾った。煤けた字で、数がびっしり並んでいる",
     "Found on the ember titan's back. Columns of numbers in sooty ink")

# ================================================================ 囁き 8 つ (B の EmberWhispers。lore は場所の気配、hint は使い方)
WHISPERS = [
    ("tongue", "舌の上に立っている。喉の奥から、鞴のような息がゆっくり行き来している",
     "You stand on the tongue. From deep in the throat, a breath like a bellows comes and goes"),
    ("throat", "右上の目盛りが体の熱。溶岩のそばでたまり、灰溜まりで冷える。火照っていると、灰の殻が割れやすい",
     "The gauge at the top right is your heat. It rises near lava and falls in ash heaps. While you are flushed, ash shells crack easily"),
    ("hub", "西・東・南の三つの熾り石を鳴らせば、中央の炉が冷えて、炉の底がひらく",
     "Ring the three kindle stones, west, east and south, and the central forge will cool and its floor will open"),
    ("west", "壺の灰を溶岩にまくと、冷えた殻の道になる。殻はやがて崩れる。灰は島の灰溜まりで満ちる",
     "Scatter ash from the urn on the lava to make a crust road. The crust soon crumbles. Ash heaps on the islands refill the urn"),
    ("east", "炉の色を読め。刻みの色が来たら打て。打ち終えたら、冷えて割れる前に槽へ",
     "Read the forge's color. Strike when it matches the mark. Then quench it before it cools and cracks"),
    ("south", "火床の火を絶やすな。熾をくべ、噴く割れ目の上へ敵を誘え",
     "Do not let the hearth die. Feed it live coals, and lure enemies over the vents"),
    ("well", "縦穴の底から、炉の熱が上へ逃げていく。登るなら、一息に",
     "The forge's heat escapes up the shaft. If you climb, climb in one breath"),
    ("heart", "格子の下から、熾の光と、少し遅れた拍が上がってくる",
     "From under the grate rise an ember glow and a beat that comes a little late"),
]
for place, ja, en in WHISPERS:
    text(f"message.skyabyss.ember.lore.whisper.{place}", ja, en)

# ================================================================ 語りの文 (13 から想定した鍵。担当ごとの頭。Java は EmberLore)
LINES = [
    # A 地上 (2 章・2.6節)
    ("surface.sleep_near", "土の匂いが熱い。どこかで、山が寝息をたてている……",
     "The soil smells hot. Somewhere, a mountain is breathing in its sleep..."),
    ("surface.breath_hint", "息を吸っているあいだに、口へ", "While it breathes in, into the mouth"),
    ("surface.spat_out", "巨神の息が、あなたを吐き出した。まだ早い", "The titan's breath spat you out. It is too soon"),
    ("surface.stone_given", "懐に、いつのまにか温かい石があった", "Somehow, there is a warm stone in your pocket"),
    ("surface.warm.right", "右手が温かい", "Your right hand is warm"),
    ("surface.warm.left", "左手が温かい", "Your left hand is warm"),
    ("surface.warm.ahead", "胸のあたりが温かい", "Your chest is warm"),
    ("surface.warm.behind", "背中が温かい", "Your back is warm"),
    ("surface.dist.far", "遠い", "far"),
    ("surface.dist.near", "近い", "near"),
    ("surface.dist.close", "すぐそこ", "close by"),
    ("surface.state.sleeping", "眠っている", "asleep"),
    ("surface.state.burrowing", "地の中", "under the earth"),
    ("surface.asked", "石を握った。今夜は、このそばで眠るだろう", "You grip the stone. Tonight it will sleep near here"),
    ("surface.asked_other", "今夜は、%sの近くで眠る", "Tonight it sleeps near %s"),
    ("surface.ask_taken", "石は温まらない。今夜は、もう誰かが呼んでいる", "The stone stays cool. Someone else has called it tonight"),
    # B 体と広間
    ("back.stone_rang", "熾り石が鳴った。広間の炉の火が、ひとつ落ちた", "A kindle stone rang. One of the hall's fires sank"),
    ("back.all_stones", "三つの熾り石が鳴った。炉が冷えて、底の格子がひらく",
     "All three kindle stones rang. The forge cools, and the grate in its floor opens"),
    ("back.trial_asleep", "%sの火は、いまは眠っている", "The fire of the %s sleeps for now"),
    ("back.trial.west", "灰の道", "Ash Path"),
    ("back.trial.east", "鍛えの間", "Forge Hall"),
    ("back.trial.south", "火守り", "Firekeep"),
    # C1 灰の道・C2 鍛えの間・C3 火守り
    ("west.engulfed", "熾に呑まれた", "The embers swallowed you"),
    ("west.urn_empty", "壺が空になった", "The urn is empty"),
    ("west.urn_full", "壺に灰が満ちた", "The urn fills with ash"),
    ("east.doused", "炉に灰が投げこまれた", "Ash was thrown into the forge"),
    ("east.key_cracked", "鍵が冷えて、割れた", "The key cooled, and cracked"),
    ("east.quenched", "鍵が、じゅっと鳴って締まった", "The key hissed, and set hard"),
    ("east.door", "扉の文様に、火が灯った", "Fire runs along the door's pattern"),
    ("south.low", "火が、また小さくなった", "The fire has grown small again"),
    ("south.full", "火床の火が、背の亀裂まで立ちのぼった", "The hearth fire rises all the way to the rift above"),
    # E 鍛え手・F 心臓
    ("smith.wake", "炉の前で、灰をかぶった大きな影が、鎚を拾った", "Before the forge, a great ash-covered shape picks up its hammer"),
    ("smith.fallen", "鍛え手が膝をついた。火箸は、まだ塊を放さない", "The smith falls to its knees. The tongs still grip the bloom"),
    ("heart.chill", "血管の光が消えた。部屋が、冷えていく", "The veins go dark. The room is growing cold"),
    ("heart.stilled", "拍が、止まった", "The beating stops"),
    ("heart.fragment", "四つ目の欠片。手の中で、ほかの欠片を探している", "The fourth fragment. In your hand, it searches for the others"),
    # G 締め (9.4節。心臓を止めて地上へ出た最初の夜明け、その人だけ)
    ("finale.dawn", "足の下の、ずっと深いところで、何かが寝返りをうった", "Far beneath your feet, something turned over in its sleep"),
]
for key, ja, en in LINES:
    text(f"message.skyabyss.ember.lore.{key}", ja, en)

# ================================================================ 品 (03 の 13章、13 の 6.5節)
GEAR = [
    ("ember_helmet", "余燼の兜", "Ember Helm", None, None),
    ("ember_chestplate", "余燼の胸当て", "Ember Breastplate", None, None),
    ("ember_leggings", "余燼のすね当て", "Ember Greaves", None, None),
    ("ember_boots", "余燼の靴", "Ember Boots", None, None),
    ("ember_armor", None, None,
     "二つで殻が固くなり、四つで熱が体になじむ。余燼の背では熱がたまりにくく、火照っているあいだは攻めが重くなる。火に触れると力が湧く",
     "Two pieces harden the shell; four let heat settle into you. On the ember titan's back heat builds slowly, "
     "and your blows land harder while flushed. Touching fire fills you with strength"),
    ("ember_war_axe", "余燼の戦斧", "Ember War Axe",
     "打った相手をくすぶらせる。燃えている相手、くすぶる相手には深く入る",
     "Leaves what it strikes smoldering. Bites deeper into anything burning or smoldering"),
    ("ember_mantle", "熾火の外套", "Embercloak",
     "装飾の枠に付けていると、熱がたまりにくく、火と溶岩の傷がやわらぐ（火そのものは防がない）",
     "While worn as a trinket, heat builds slowly and fire and lava hurt less (it does not stop fire itself)"),
    ("kindling_charm", "熾りの護符", "Kindling Charm",
     "使うと、体にたまった熱を護符が吸い、まわりの敵へ吐き出す（使うと50秒は熾らない）",
     "When used, it draws the heat out of your body and breathes it onto nearby foes (rests 50 seconds after use)"),
    ("smith_hammer", "鍛え手の大鎚", "Ash Smith's Sledge",
     "両手で振る大鎚。三度目ごとに床へ亀裂が走り、少し遅れて噴く",
     "A two-handed sledge. Every third blow sends a crack through the floor that erupts a moment later"),
    ("thrall_poker", "灰被りの火掻き", "Thrall's Poker",
     "間合いが長い。打つたびに灰を積み、積もった灰が固まると、相手の足と守りが鈍る",
     "Long reach. Each blow heaps ash on the foe; when it sets, their legs and guard go dull"),
    ("ember_lantern", "余燼の灯", "Last Ember Lantern",
     "装飾の枠に付けていると、倒れるほどの傷を一度だけこらえ、まわりを焼く（一度燃えると600秒は眠る）",
     "While worn as a trinket, it once holds you up against a fatal blow and burns what is near (sleeps 600 seconds after)"),
    ("ember_horn", "熾火の角杯", "Ember Horn",
     "熾を溶かした温かい飲み物。余燼の背で飲むと熱がたまりにくくなり、ほかの場所では少しのあいだ火を防ぐ",
     "A warm drink with embers melted into it. On the ember titan's back it slows your heat; elsewhere it wards off fire for a while"),
    ("ember_shard", "余燼の欠片", "Ember Shard",
     "余燼の巨神から欠けた、まだ温かい殻。余燼の装備を鍛える",
     "A still-warm flake of the ember titan's shell. Used to temper ember gear"),
    ("ember_core", "余燼の心核", "Ember Core",
     "余燼の心臓から取れた、消えない熾。いつか神器の材料になる",
     "An ember from the ember heart that will not go out. One day it will become part of a relic"),
    ("spark_dust", "火の粉の粉", "Spark Dust",
     "火の粉蛾の翅から落ちた粉。火薬の代わりに花火に使える",
     "Powder shed from an ember moth's wings. Works in fireworks in place of gunpowder"),
    ("cinder_husk", "燠の殻", "Cinder Husk",
     "燠虫の背の板。冷めても、継ぎ目だけがほんのり赤い",
     "A plate from a cinder roller's back. Even cold, its seams glow faintly red"),
]
for item, nja, nen, dja, den in GEAR:
    if nja:
        text(f"item.skyabyss.{item}", nja, nen)
    if dja:
        text(f"item.skyabyss.{item}.desc", dja, den)
text("gear.skyabyss.origin.ember", "余燼の専用装備", "Ember titan gear")
text("gui.skyabyss.forge.exchange_no_clear.ember", "余燼の心臓を倒すと、選べるようになる", "Defeat the ember heart to choose")
text("gui.skyabyss.forge.exchange_all_owned.ember", "余燼の専用伝説は、すべて手にしている", "You already own every ember legendary")
text("skyabyss_record.h.ember", "余燼", "Ember")
text("tooltip.skyabyss.ember.horn_drunk", "体の芯が、ゆっくり温まった。しばらく熱がたまりにくい",
     "Warmth spreads slowly from your core. Heat will build more slowly for a while")


# ================================================================ 書き出し
def write_lore() -> None:
    DATA.mkdir(parents=True, exist_ok=True)
    for lid, order, unlock, related, title, body, sign in LORE:
        entry: dict = {"chapter": "chapter1", "order": order}
        if sign:
            entry["inscription_sign"] = sign
        entry["unlock"] = unlock
        entry["related"] = related
        (DATA / f"{lid}.json").write_text(json.dumps(entry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_lang(code: str, texts: dict[str, str]) -> None:
    path = LANG / f"{code}.json"
    current = json.loads(path.read_text(encoding="utf-8"))
    kept = [(k, v) for k, v in current.items() if not owned(k)]
    out: dict[str, str] = {}
    placed = False
    for k, v in kept:
        out[k] = v
        if k == "__ember.G":
            out.update(texts)
            placed = True
    if not placed:
        out["__ember.G"] = "品・伝承 (item.skyabyss.*、tooltip.skyabyss.ember.*)"
        out.update(texts)
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    write_lore()
    write_lang("ja_jp", JA)
    write_lang("en_us", EN)
    print(f"lore entries: {len(LORE)}, lang keys: {len(JA)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
