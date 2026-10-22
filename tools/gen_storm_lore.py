"""雷翼の巨神 (12) の伝承とクエストの担当 (担当 6) の生成元。文の名前空間 assets/skyabyss_storm/lang を一か所で書く。

12 の 11.1 節の約束: skyabyss_storm の lang はこの担当が持つ。ほかの担当 (1 本体と背中・2 敵・3 雷獣・4 心臓・5 装備) は
文の鍵を決めて、仮の英文を assets/skyabyss_storm/lang/parts/<担当>.json ({"鍵": "英文"} か {"鍵": {"en": .., "ja": ..}}) に書く。
ここでは、12 の各章から鍵を想定して日本語の文を先に用意しておき (ANTICIPATED)、parts と合わせて ja_jp / en_us を書き出す。
  - parts にあって、ここに日本語がない鍵: 英文を仮に ja_jp にも入れ、「日本語がない」と一覧に出す (合わせるときに書き足す)
  - ここにあって、どの parts にもない鍵: そのまま書き出し、「まだ誰も使っていない」と一覧に出す (鍵の名前が違えば直す)
  - 英文: ここに推敲した英文があればそれを使う。なければ parts の英文

ほかに書くもの:
  data/skyabyss/lore/entry/chapter1/{storm_*,inscription_storm_*,raiju,wind_ledger}.json   伝承の項目 (12 の 9.3 節)
  data/skyabyss/quest/{daily_storm_rounds,weekly_storm_heart}.json                         雷翼の背の周回 (10 の 8.6 節の形)
  docs/design/12_thunder_wing.md の「伝承とクエスト 生成」印の間                               文の一覧

雷翼のメイン「嵐を渡る翼」と、イサナの「イサナの古い地図」(風見鶏) は tools/gen_story_quests.py (skyabyss_story) にある。

実行 (リポジトリ直下):
    python tools/gen_storm_lore.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/skyabyss"
LANG = ROOT / "src/main/resources/assets/skyabyss_storm/lang"
PARTS = LANG / "parts"
DOC = ROOT / "docs/design/12_thunder_wing.md"

JA: dict[str, str] = {}
EN: dict[str, str] = {}
OWNER: dict[str, str] = {}  # 鍵 → 想定した担当 ("6" はこの担当)


def text(key, ja, en, owner="6"):
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en
    OWNER[key] = owner


# ================================================================ 看板 (12 の 9.2 節)
# 碑文 6 枚 (4 行。1 行は日本語 8 字まで、英語は 90 px まで。SignFit と同じ字の幅で、ここでも測る)
INSCRIPTIONS = {
    "tail": (("嵐は鳥の衣", "衣を脱がぬのは", "見つかるからだ", "上の影に"),
             ("Storm cloaks", "the bird. It", "hides from the", "shadow above")),
    "hub": (("凪には下を", "嵐には上を", "風の民は", "息を数えた"),
            ("In calm, below", "in storm, above.", "The wind folk", "counted breaths")),
    "west": (("骨は空洞", "風の通う道", "我らは骨の中で", "飛ぶことを知った"),
             ("Bones are hollow", "roads for wind.", "Inside them we", "learned to fly")),
    "east": (("雷は柱を選ぶ", "柱は我らを", "守ってくれた", "あの夜までは"),
             ("Bolts seek rods,", "and the rods", "kept us safe", "until that night")),
    "eye": (("眼は来る者を", "覚えている", "お前の顔は", "もう知っている"),
            ("The eye recalls", "all who come.", "It already knows", "your face")),
    "heart": (("雷は空から", "落ちてくる", "この拍だけは", "下から昇る"),
              ("Bolts fall", "from the sky.", "This beat alone", "rises from below")),
}
# 試練の入口の一言 3 枚 (2 行)
TRIAL_SIGNS = {
    "west": (("風切りの骨", "風に乗れ"), ("Windbone", "Ride the wind")),
    "east": (("雷の骨", "雷を柱へ"), ("Thunderbone", "Bolts to rods")),
    "front": (("眼の間", "光を三つへ"), ("Eye Hall", "Light all three")),
}
for place, (ja, en) in INSCRIPTIONS.items():
    for i in range(4):
        text(f"sign.skyabyss.lore_storm_{place}.{i + 1}", ja[i], en[i])
for trial, (ja, en) in TRIAL_SIGNS.items():
    for i in range(4):
        text(f"sign.skyabyss.storm_{trial}.{i + 1}", ja[i] if i < 2 else "", en[i] if i < 2 else "")

# ================================================================ 囁き 8 つ (StormWhispers。lore は場所の気配、hint は使い方)
WHISPERS = [
    ("storm_landing", "lore", "尾羽の上に立っている。腰の羽根の門の奥で、何かが脈打っている",
     "You stand on the tail feathers. Beyond the feather gate at the hips, something is beating"),
    ("storm_tail", "hint", "右上の風見の板が、凪と嵐の拍。西・東・前の三つの風鳴り石を鳴らせば、広間の床の竜骨の門が開く。西の壁の段は背の上へ",
     "The wind vane at the top right shows the beat of calm and storm. Ring the three wind stones (west, east, front) to open the keel gate "
     "in the hall floor. The steps in the west wall climb onto the back"),
    ("storm_hub", "lore", "凪には下を、嵐には上を。凪なら壁ぞいの坂で上の回廊へ。嵐なら、中央の昇り風がひと息に運ぶ",
     "In calm, below; in storm, above. In a calm, take the ramp along the wall to the upper gallery. In a storm, the central updraft "
     "carries you up in one breath"),
    ("storm_west", "hint", "風切りの羽根を持って、空中でしゃがむと滑空する。床の穴の昇り風は、凪には低く、嵐には高く上がる。横風は梁の陰でやり過ごせ",
     "With the gale feather, crouch in midair to glide. The updrafts from holes in the floor rise low in a calm and high in a storm. "
     "Wait out the crosswinds behind the beams"),
    ("storm_east", "hint", "足もとに白い輪が出たら、雷が来る。輪が銅の柱の近くなら、雷は柱へ落ちる。柱が満ちると、骨の戸が開く",
     "When a white ring appears at your feet, lightning is coming. If the ring is near a copper rod, the bolt strikes the rod instead. "
     "A full rod opens a bone door"),
    ("storm_front", "hint", "羽鏡は手で回る。眼から差す光を、壁の三つの受け羽へ導け。詰まったら、前室の戻しの鏡で最初の向きに戻せる",
     "The feather mirrors turn by hand. Guide the light from the eye to the three catch-feathers on the walls. If you get stuck, "
     "the reset mirror in the antechamber turns them back"),
    ("storm_heart", "hint", "この先は竜骨の間。心臓は、三つの試練で覚えたことを、一つずつ問うてくる",
     "Beyond lies the keel chamber. The heart will test what you learned in the three trials, one at a time"),
]
for wid, _, ja, en in WHISPERS:
    text(f"message.skyabyss.whisper.{wid}", ja, en)
text("message.skyabyss.storm.campfire_hint", "しゃがんで焚き火に触れると、試練をあきらめて尾の間へ戻れる",
     "Crouch and touch the campfire to give up the trial and return to the tail chamber")
text("item.skyabyss.lore_page.desc.storm_back", "雷翼の背で拾った。棒の刻みが、端までびっしり並んでいる",
     "Found on the thunder wing's back. Tally strokes fill it to the edges")

# ================================================================ 伝承の項目 (12 の 9.3 節。ほのめかしだけ。答えは書かない)
# (ID, 順, 記される条件, 関わる項目, 題, 本文, 碑文の看板)
LORE = [
    ("storm_wing", 159, [{"type": "near_entity", "target": "skyabyss:thunder_wing", "radius": 240}, {"type": "dimension", "target": "skyabyss:storm_back"}],
     ["chapter1/thunder_wing", "chapter1/storm_path"], ("風に逆らう嵐", "The Storm Against the Wind"),
     ("遠くの嵐が、風上へ向かって動いていた。稲妻が光るたびに、雨の幕の中に翼の形が浮かんだ。\n\n翼は、空の影より小さかった。",
      "A distant storm was moving upwind. Each time the lightning flashed, the shape of wings showed inside the curtain of rain.\n\n"
      "The wings were smaller than the sky shadow's."), None),
    ("storm_ruins", 161, [{"type": "dimension", "target": "skyabyss:storm_back"}],
     ["chapter1/storm_wing", "chapter1/inscription_storm_tail"], ("風の民の遺跡", "Ruins of the Wind Folk"),
     ("鳥の背に、石の塔が二本立っていた。塔の壁には、短い棒が数えきれないほど刻まれている。\n\n"
      "人の姿はない。骨の回廊を、銅の兜の番人が歩いていた。兜の奥には、何もなかった。",
      "Two stone towers stood on the bird's back. Their walls are cut with short strokes, more than anyone could count.\n\n"
      "No one was there. Wardens in copper helms walked the hollow bones. Behind the helms there was nothing."), None),
    ("storm_cower", 162, [{"type": "manual"}],
     ["chapter1/storm_wing", "prologue/shadow"], ("うずくまる巨神", "The Cowering Titan"),
     ("空の影が通るあいだ、雷翼は峰にしがみつき、翼で頭を覆っていた。山のような鳥が、怯えていた。",
      "While the sky shadow passed, the thunder wing clung to the peak with its head hidden under its wings. "
      "A bird the size of a mountain was afraid."), None),
    ("inscription_storm_tail", 163, None, ["chapter1/storm_ruins", "chapter1/storm_cower"], ("尾の間の碑文", "Inscription in the Tail Chamber"),
     ("尾の間の炉の上にあった。最後の行だけ刻みが浅く、急いで彫ったように見える。",
      "Above the hearth in the tail chamber. Only the last line is cut shallow, as if in a hurry."), "tail"),
    ("inscription_storm_hub", 164, None, ["chapter1/storm_ruins", "chapter1/wind_ledger"], ("風の広間の碑文", "Inscription in the Wind Hall"),
     ("天窓の真下、昇り風の柱のそばの石にあった。石の縁には、棒の刻みが四本ずつ束ねて並んでいる。",
      "On a stone beside the updraft, right under the skylight. Along its edge, strokes are cut in bundles of four."), "hub"),
    ("inscription_storm_west", 165, None, ["chapter1/inscription_storm_hub"], ("左手首の瘤の碑文", "Inscription in the Left Wrist"),
     ("風鳴り石の岩棚の壁にあった。そこへは、嵐の風に乗らなければ上がれない。",
      "On the wall of the ledge by the wind stone. No one reaches it except on the storm's wind."), "west"),
    ("inscription_storm_east", 166, None, ["chapter1/raiju"], ("右手首の瘤の碑文", "Inscription in the Right Wrist"),
     ("雷獣の巣の、六本の柱のまんなかの床にあった。床の石は、そこだけ焦げていない。",
      "On the floor at the center of the six rods in the beast's den. Only there is the stone unscorched."), "east"),
    ("inscription_storm_eye", 167, None, ["chapter1/inscription_storm_heart"], ("眼の間の碑文", "Inscription in the Eye Hall"),
     ("三つ目の受け羽の下にあった。そばの壁に、小さな人の浮き彫りがある。\n\n四角い頭をしていた。",
      "Beneath the third catch-feather. On the wall beside it is a small carving of a person.\n\nIts head was square."), "eye"),
    ("wind_ledger", 168, [{"type": "page", "target": "storm_back"}, {"type": "manual"}],
     ["chapter1/storm_ruins", "chapter1/inscription_storm_hub"], ("風見の帳", "The Wind Ledger"),
     ("凪と嵐が、棒の数で書き留めてある。ある日から、棒の横に小さな印が付きはじめる。上を見るな、という意味の印らしい。\n\n"
      "最後の頁には、棒が一本もない。",
      "Calms and storms are tallied in strokes. From one day on, a small mark begins to appear beside them. "
      "It seems to mean: do not look up.\n\nOn the last page, there are no strokes at all."), None),
    ("raiju", 169, [{"type": "kill", "target": "skyabyss:raiju", "radius": 48}, {"type": "manual"}],
     ["chapter1/inscription_storm_east"], ("雷獣", "The Raiju"),
     ("獣が倒れると、瘤の中に集まっていた雷が、行き場をなくして柱を何度も行き来した。やがて、柱は静かになった。",
      "When the beast fell, the lightning gathered in the knuckle had nowhere to go, and ran from rod to rod, again and again. "
      "At last the rods fell quiet."), None),
    ("inscription_storm_heart", 171, None, ["chapter1/inscription_tide_heart", "chapter1/storm_stilled"],
     ("竜骨の前室の碑文", "Inscription in the Keel Antechamber"),
     ("竜骨の門の下、前室の壁にあった。石に耳を当てると、拍は確かに、足もとのほうから昇ってくる。",
      "On the antechamber wall below the keel gate. With an ear to the stone, the beat does seem to rise from somewhere beneath your feet."),
     "heart"),
    ("storm_stilled", 172, [{"type": "kill", "target": "skyabyss:thunder_wing_heart", "radius": 64}, {"type": "manual"}],
     ["chapter1/inscription_storm_heart", "chapter1/tide_heart_stilled"], ("晴れた空", "A Clear Sky"),
     ("雷翼の心臓が止まった。胸の奥の風が、はじめて止んだ。\n\n嵐の上の空は、ずっと晴れていた。それでも鳥は、嵐を脱がなかった。\n\n"
      "三つの欠片を手のひらに並べると、わずかに下を向いて寄り合った。",
      "The thunder wing's heart stopped. The wind deep in its chest fell still for the first time.\n\n"
      "Above the storm, the sky had been clear all along. Even so, the bird never shed its storm.\n\n"
      "Laid side by side in my palm, the three fragments drew together, tilting slightly downward."), None),
]
for lid, order, unlock_, related, title, body, sign in LORE:
    key = "lore.skyabyss.chapter1." + lid
    text(key + ".title", *title)
    text(key + ".body", *body)

# ================================================================ クエスト (雷翼の背の周回。10 の 8.6 節・07 の 15 章の形)
QUESTS = []


def quest(qid, order, title, summary, done, journal, objective, reward, repeat, story):
    base = f"quest.skyabyss.{qid}"
    text(f"{base}.title", *title)
    text(f"{base}.summary", *summary)
    text(f"{base}.done", *done)
    sid = objective.pop("stage")
    text(f"{base}.{sid}.journal", *journal)
    text(f"{base}.{sid}.{objective['id']}", *objective.pop("text"))
    QUESTS.append((qid, {"category": "sub", "order": order, "repeat": repeat, "board": True, "min_stage": 3,
                         "stages": [{"id": sid, "objectives": [objective]}], "reward": reward}, title, story))


quest("daily_storm_rounds", 66, ("雷翼の背の見回り", "Rounds on the Thunder Wing"),
      ("雷翼の背で、羽守・雷蛾・雷乞いを八体倒す。仲間の分も数える。",
       "Defeat eight feather wardens, static moths or thunder supplicants on the thunder wing's back. Party members count too."),
      ("骨の回廊は、しばらく静かになるだろう。", "The hollow bones will be quiet for a while."),
      ("板の紙（雨に濡れて、字が少し流れている）：「鳥の背の骨の中で、銅の兜の番人と光る蛾が、また騒いでいる。八つ、静めてきてほしい」",
       "A note on the board, the ink run a little by rain: \"Inside the bones on the bird's back, the copper-helmed wardens and the glowing moths "
       "are stirring again. Quiet eight of them.\""),
      {"stage": "rounds", "id": "kill", "type": "kill", "targets": ["skyabyss:feather_warden", "skyabyss:static_moth", "skyabyss:thunder_supplicant"],
       "count": 8, "text": ("雷翼の背で、羽守・雷蛾・雷乞いを倒す", "Defeat feather wardens, static moths and thunder supplicants on the thunder wing")},
      {"xp": 110, "items": [{"item": "skyabyss:thunder_wing_shard", "count": 2, "optional": True},
                            {"item": "skyabyss:resonance_shard_thunder_wing", "count": 1}]},
      "daily", "雷翼の背の周回の入口 (段階 3)。潮殻の「潮殻の背の見回り」と同じ形")
quest("weekly_storm_heart", 84, ("嵐の鼓動をもう一度", "The Storm's Heart, Once More"),
      ("雷翼の心臓を、もう一度止める。戦いに加わった仲間の分も数える。", "Still the thunder wing's heart once more. Party members in the fight count too."),
      ("嵐は止んだ。けれど風は、また集まってくるのだろう。", "The storm has stopped. But the wind will gather again, surely."),
      ("板の隅の紙（同じ、名前のない字）：「嵐は七日で戻る。戻りきる前に、確かめてきてほしい」",
       "A note in the corner of the board, in the same unsigned hand: \"The storm comes back in seven days. Go and make sure, before it is back.\""),
      {"stage": "heart", "id": "heart", "type": "kill", "target": "skyabyss:thunder_wing_heart", "count": 1,
       "text": ("雷翼の心臓を止める", "Still the thunder wing's heart")},
      {"xp": 260, "items": [{"item": "skyabyss:resonance_crystal", "count": 1},
                            {"item": "skyabyss:thunder_wing_shard", "count": 3, "optional": True}]},
      "weekly", "「心臓をもう一度」「潮の鼓動をもう一度」と同じ名前のない紙。七日という数を、三度目にほのめかす")

# ================================================================ ほかの担当の文 (12 の各章から鍵を想定して先に用意した。parts と合わせる)
A = {}


def anticipate(owner, key, ja, en):
    text(key, ja, en, owner)


# ---- 担当 1: 本体と背中 (2・3・4・7 章)
for k, ja, en in [
    ("entity.skyabyss.thunder_wing", "雷翼の巨神", "Thunder Wing Titan"),
    ("entity.skyabyss.gale_column", "昇り風", "Updraft"),
    ("biome.skyabyss.storm_back", "嵐の上", "Above the Storm"),
    ("title.skyabyss.storm_back", "雷翼の背", "The Thunder Wing's Back"),
    ("title.skyabyss.storm_back.sub", "嵐の上を飛ぶ、鳥の背", "A bird's back, flying above the storm"),
    ("message.skyabyss.storm.against_wind", "遠くの嵐が、風に逆らって動いていた", "A distant storm was moving against the wind"),
    ("message.skyabyss.storm.perching", "風が向きを変えた。嵐が、峰へ降りてくる……", "The wind has turned. The storm is coming down onto the peak..."),
    ("message.skyabyss.storm.taking_off", "雷翼が翼を広げた。夕立の刻が終わる", "The thunder wing spreads its wings. The hour of the squall is over"),
    ("message.skyabyss.storm.column_hint", "昇り風に乗ると、背中へ運ばれる", "Step into the updraft to be carried onto its back"),
    ("message.skyabyss.storm.arrived", "風に運ばれて、鳥の尾羽の上にいた。足もとのずっと下で、嵐の雲が光っている。",
     "The wind carried you onto the bird's tail feathers. Far below your feet, the storm clouds flicker."),
    ("message.skyabyss.storm.leaving", "羽根の門をくぐると、雨の匂いの風が、地上へ吹き下ろした", "Through the feather gate, a wind smelling of rain blew you down to the ground"),
    ("message.skyabyss.storm.return_hint", "羽根の門の外の面に立つと、地上へ戻る", "Stand in the outer face of the feather gate to return to the ground"),
    ("message.skyabyss.storm.swept_back", "嵐に投げ返された", "The storm threw you back"),
    ("message.skyabyss.storm.storm_soon", "低い風の唸りが強まる。まもなく嵐", "The low moan of the wind grows. A storm is coming"),
    ("message.skyabyss.storm.calm_soon", "遠くで、高い鳴き声がした。まもなく凪", "A high cry, far off. A calm is coming"),
    ("message.skyabyss.storm.peaceful", "巨神は目覚めない（ピースフルのあいだは、試練は眠っている）", "The titan does not wake (the trials sleep while the game is on Peaceful)"),
    ("message.skyabyss.storm.gale_feather", "風切りの羽根を手にした。空中でしゃがむと、滑空する", "You take a gale feather. Crouch in midair to glide"),
    ("message.skyabyss.storm.gale_feather_gone", "試練の外へ出ると、羽根は風に溶けた", "Outside the trial, the feather melted into the wind"),
    ("message.skyabyss.storm.rod_full", "避雷の柱が満ちた。骨の戸が開いていく", "The rod is full. A bone door slides open"),
    ("message.skyabyss.storm.door_opened", "骨の戸が開いた (%s / 3)", "A bone door opened (%s / 3)"),
    ("message.skyabyss.storm.feather_lit", "受け羽が光った (%s / 3)", "A catch-feather lit up (%s / 3)"),
    ("message.skyabyss.storm.mirrors_reset", "鏡が、最初の向きに戻った", "The mirrors turned back to where they began"),
    ("message.skyabyss.storm.mirrors_reset_wait", "戻しの鏡はまだ重い (あと %s 秒)", "The reset mirror is still heavy (%s s)"),
    ("message.skyabyss.storm.stone_ringing", "風鳴り石が鳴りはじめた: %s (%s / %s)", "A wind stone begins to sing: %s (%s / %s)"),
    ("message.skyabyss.storm.stone_rung", "この石は、もう鳴っている", "This stone is already singing"),
    ("message.skyabyss.storm.stone_silent", "石は黙っている。巣の主が、まだ瘤の中にいる", "The stone is silent. The den's master is still inside the knuckle"),
    ("message.skyabyss.storm.trial_sleeping", "この試練は今、眠っている。石はすでに鳴っている", "This trial is asleep for now. Its stone is already singing"),
    ("message.skyabyss.storm.all_rung", "三つの風鳴り石が重なった。広間の床で、羽根の輪がほどけていく……",
     "The three wind stones sing together. On the hall floor, the ring of feathers comes undone..."),
    ("message.skyabyss.storm.keel_opened", "竜骨の門が開いた。下から、風が吹き上がってくる", "The keel gate is open. Wind rushes up from below"),
    ("message.skyabyss.storm.return_opened", "近くの床が光った。広間への抜け道が開いた", "The floor nearby lights up. A shortcut to the hall is open"),
    ("message.skyabyss.storm.trials_renewed", "雷翼の心臓が、再び打ちはじめた。背中の試練が、元の形に戻った",
     "The thunder wing's heart beats again. The trials on its back are as they were"),
    ("message.skyabyss.storm.sleeping_west", "今日、西の石はすでに鳴っている (風切りの骨は眠っている)", "Today the west stone is already singing (the Windbone sleeps)"),
    ("message.skyabyss.storm.sleeping_east", "今日、東の石はすでに鳴っている (雷の骨は眠っている)", "Today the east stone is already singing (the Thunderbone sleeps)"),
    ("message.skyabyss.storm.sleeping_front", "今日、前の石はすでに鳴っている (眼の間は眠っている)", "Today the front stone is already singing (the Eye Hall sleeps)"),
    ("message.skyabyss.storm.gave_up", "焚き火にしゃがみ込むと、風の音が遠のいた。尾の間に戻っている",
     "As you crouch by the campfire, the sound of the wind fades. You are back in the tail chamber"),
    ("gui.skyabyss.storm_gauge.calm", "凪", "Calm"),
    ("gui.skyabyss.storm_gauge.rising", "荒れる", "Rising"),
    ("gui.skyabyss.storm_gauge.storm", "嵐", "Storm"),
    ("gui.skyabyss.storm_gauge.falling", "静まる", "Easing"),
    ("gui.skyabyss.storm_gauge.left", "あと %s 秒", "%s s left"),
    ("gui.skyabyss.storm_gauge.held", "拍が止まっている", "Beat held"),
    ("trial.skyabyss.storm_west", "西 (風切りの骨)", "West (Windbone)"),
    ("trial.skyabyss.storm_east", "東 (雷の骨)", "East (Thunderbone)"),
    ("trial.skyabyss.storm_front", "前 (眼の間)", "Front (Eye Hall)"),
    ("checkpoint.skyabyss.storm_hub", "風の広間", "Wind Hall"),
    ("checkpoint.skyabyss.storm_w0", "左の肩", "Left Shoulder"),
    ("checkpoint.skyabyss.storm_w1", "左の肘の洞", "Left Elbow Hollow"),
    ("checkpoint.skyabyss.storm_e0", "右の肩", "Right Shoulder"),
    ("checkpoint.skyabyss.storm_e1", "右の肘の洞", "Right Elbow Hollow"),
    ("checkpoint.skyabyss.storm_f0", "首の前室", "Neck Antechamber"),
    ("checkpoint.skyabyss.storm_heart", "竜骨の前室", "Keel Antechamber"),
    ("block.skyabyss.wind_stone", "風鳴り石", "Wind Stone"),
    ("block.skyabyss.storm_rod", "避雷の柱", "Lightning Rod Pillar"),
    ("block.skyabyss.feather_mirror", "羽鏡", "Feather Mirror"),
    ("block.skyabyss.catch_feather", "受け羽", "Catch-feather"),
    ("block.skyabyss.reset_mirror", "戻しの鏡", "Reset Mirror"),
    ("item.skyabyss.gale_feather", "風切りの羽根", "Gale Feather"),
    ("item.skyabyss.gale_feather.desc", "空中でしゃがむと滑空する。風切りの骨の中でだけ", "Crouch in midair to glide. Only inside the Windbone"),
    ("item.skyabyss.weathercock", "風見鶏", "Weathercock"),
    ("item.skyabyss.weathercock.desc", "鶏は、風ではなく嵐のほうを向く", "The cock points not at the wind, but at the storm"),
    ("item.skyabyss.weathercock.desc2", "空の見える高い所で掲げると、嵐を呼べる", "Raise it somewhere high under open sky to call the storm"),
    ("message.skyabyss.weathercock.too_low", "ここは低すぎる。もっと高い所で", "Too low here. Somewhere higher"),
    ("message.skyabyss.weathercock.no_sky", "空が見えない。屋根の外で", "You can't see the sky. Go out from under the roof"),
    ("message.skyabyss.weathercock.called", "風見鶏に雷が落ちた。嵐が、こちらに気づいた", "Lightning struck the weathercock. The storm has noticed you"),
    ("message.skyabyss.weathercock.called_by", "%sが嵐を呼んだ", "%s called the storm"),
    ("message.skyabyss.weathercock.wait", "嵐はまだ応えない (あと %s 分)", "The storm will not answer yet (%s min)"),
    ("message.skyabyss.weathercock.next", "次の夕立の刻まで %s", "%s until the hour of the squall"),
    ("message.skyabyss.weathercock.now", "夕立の刻", "The hour of the squall"),
    ("message.skyabyss.weathercock.far", "遠い", "Far"),
    ("message.skyabyss.weathercock.near", "近い", "Near"),
    ("message.skyabyss.weathercock.close", "すぐそこ", "Close by"),
    ("message.skyabyss.weathercock.absent", "鶏は、くるくると回るばかりだ", "The cock only spins"),
    ("subtitles.skyabyss.storm_thunder", "遠雷", "Distant thunder"),
    ("subtitles.skyabyss.storm_wind", "風が唸る", "Wind moans"),
    ("subtitles.skyabyss.storm_wings", "大きな羽ばたき", "Great wingbeats"),
    ("subtitles.skyabyss.storm_cry", "高い鳴き声", "A high cry"),
    ("subtitles.skyabyss.storm_column", "昇り風が吹き上がる", "Updraft roars"),
    ("subtitles.skyabyss.storm_strike", "雷が落ちる", "Lightning strikes"),
    ("subtitles.skyabyss.storm_rod", "柱が雷を受ける", "Rod takes a bolt"),
    ("subtitles.skyabyss.storm_crosswind", "横風が笛のように鳴る", "Crosswind whistles"),
    ("subtitles.skyabyss.storm_stone", "風鳴り石が鳴る", "Wind stone sings"),
    ("subtitles.skyabyss.storm_mirror", "羽鏡が回る", "Feather mirror turns"),
    ("subtitles.skyabyss.storm_feather", "受け羽が震える", "Catch-feather trembles"),
    ("message.skyabyss.memory_title_thunder_wing", "── 巨神の記憶: 雷翼 ──", "── Titan Memory: Thunder Wing ──"),
]:
    anticipate("1", k, ja, en)

# ---- 担当 2: 敵 (5.1 節)
for mob, ja, en in [("feather_warden", "羽守", "Feather Warden"), ("static_moth", "雷蛾", "Static Moth"),
                    ("thunder_supplicant", "雷乞い", "Thunder Supplicant")]:
    anticipate("2", f"entity.skyabyss.{mob}", ja, en)
    anticipate("2", f"item.skyabyss.{mob}_spawn_egg", f"{ja}のスポーンエッグ", f"{en} Spawn Egg")
for k, ja, en in [
    ("subtitles.skyabyss.feather_warden_ambient", "外套の羽が擦れる", "Cloak feathers rustle"),
    ("subtitles.skyabyss.feather_warden_hurt", "羽守の兜が鳴る", "Feather warden's helm rings"),
    ("subtitles.skyabyss.feather_warden_death", "外套が崩れ落ちる", "Cloak collapses"),
    ("subtitles.skyabyss.feather_warden_crouch", "羽守が外套を広げる", "Feather warden spreads its cloak"),
    ("subtitles.skyabyss.feather_warden_dive", "羽守が突っ込む", "Feather warden dives"),
    ("subtitles.skyabyss.static_moth_ambient", "雷蛾の翅が唸る", "Static moth hums"),
    ("subtitles.skyabyss.static_moth_hurt", "雷蛾が散る", "Static moth scatters"),
    ("subtitles.skyabyss.static_moth_death", "雷蛾が弾ける", "Static moth bursts"),
    ("subtitles.skyabyss.static_moth_charge", "翅の模様が明るくなる", "Wing pattern brightens"),
    ("subtitles.skyabyss.static_moth_discharge", "放電", "Discharge"),
    ("subtitles.skyabyss.thunder_supplicant_ambient", "雷を乞う声", "Prayer for thunder"),
    ("subtitles.skyabyss.thunder_supplicant_hurt", "鳥籠が軋む", "Birdcage creaks"),
    ("subtitles.skyabyss.thunder_supplicant_death", "籠の中の稲妻が消える", "Caged lightning dies out"),
    ("subtitles.skyabyss.thunder_supplicant_raise", "雷乞いが杖を掲げる", "Thunder supplicant raises its staff"),
    ("item.skyabyss.moth_dust", "鱗粉", "Moth Dust"),
    ("item.skyabyss.moth_dust.desc", "矢に付けると、当たった敵が光る", "Coat an arrow with it, and what it hits will glow"),
]:
    anticipate("2", k, ja, en)

# ---- 担当 3: 雷獣 (5.2 節)
for k, ja, en in [
    ("entity.skyabyss.raiju", "雷獣", "Raiju"),
    ("block.skyabyss.ground_plug", "接地の栓", "Grounding Plug"),
    ("gui.skyabyss.boss.raiju", "雷獣", "Raiju"),
    ("message.skyabyss.raiju.gathering", "雷獣の巣へ入る。%s 秒のうちに集まれ", "Entering the raiju's den. Gather within %s s"),
    ("message.skyabyss.raiju.begin", "巣の口が閉じた。柱の上で、雷獣が身を起こす", "The den's mouth seals. On a rod, the raiju rises"),
    ("message.skyabyss.raiju.grounded", "柱を接地した (8 秒)", "The rod is grounded (8 s)"),
    ("message.skyabyss.raiju.plug_wait", "栓はまだ戻らない (あと %s 秒)", "The plug has not come back yet (%s s)"),
    ("message.skyabyss.raiju.stunned", "雷獣が、接地した柱に落ちた。今だ", "The raiju fell onto a grounded rod. Now!"),
    ("message.skyabyss.raiju.defeated", "雷獣が倒れた。瘤の中の雷が、行き場をなくしている", "The raiju has fallen. The lightning in the knuckle has nowhere to go"),
    ("message.skyabyss.raiju.asleep", "巣の主は眠っている (ピースフルのあいだは目を覚まさない)", "The den's master sleeps (it does not wake on Peaceful)"),
    ("subtitles.skyabyss.raiju_ambient", "雷獣が喉を鳴らす", "Raiju growls"),
    ("subtitles.skyabyss.raiju_hurt", "雷獣が吠える", "Raiju yelps"),
    ("subtitles.skyabyss.raiju_death", "雷獣が崩れ落ちる", "Raiju collapses"),
    ("subtitles.skyabyss.raiju_leap", "雷が柱を渡る", "Lightning leaps between rods"),
    ("subtitles.skyabyss.raiju_pounce", "雷獣が跳びかかる", "Raiju pounces"),
    ("subtitles.skyabyss.raiju_roar", "雷鳴の咆哮", "Thunderous roar"),
    ("subtitles.skyabyss.raiju_stun", "雷獣が痺れる", "Raiju is stunned"),
    ("subtitles.skyabyss.ground_plug", "栓が引かれる", "Plug pulled"),
]:
    anticipate("3", k, ja, en)

# ---- 担当 4: 心臓 (5.3 節)
for k, ja, en in [
    ("entity.skyabyss.thunder_wing_heart", "雷翼の心臓", "Heart of the Thunder Wing"),
    ("gui.skyabyss.boss.thunder_wing_heart.phase.1", "第一段階: 畳んだ翼", "Phase 1: Folded Wings"),
    ("gui.skyabyss.boss.thunder_wing_heart.phase.2", "第二段階: 開いた翼", "Phase 2: Open Wings"),
    ("gui.skyabyss.boss.thunder_wing_heart.phase.3", "第三段階: 骨だけの翼", "Phase 3: Bare Bones"),
    ("gui.skyabyss.boss.thunder_wing_heart.roaring", "咆哮", "Roar"),
    ("message.skyabyss.storm_heart.descend", "竜骨の門の下へ下りた。下から、雷のような拍が昇ってくる", "You go down below the keel gate. A beat like thunder rises from beneath"),
    ("message.skyabyss.storm_heart.go", "竜骨の間の前室へ集まれ (竜骨の門の下、螺旋の先)", "Gather in the keel antechamber (down the spiral below the keel gate)"),
    ("message.skyabyss.storm_heart.begin", "入口が閉ざされた。竜骨の間の中央で、雷翼の心臓が打ちはじめる", "The entrance seals. In the middle of the keel chamber, the thunder wing's heart begins to beat"),
    ("message.skyabyss.storm_heart.asleep", "心臓は深く眠っている (ピースフルのあいだは目を覚まさない)", "The heart is in a deep sleep (it does not wake on Peaceful)"),
    ("message.skyabyss.storm_heart.wings_torn", "翼が裂けた。今だ", "The wings are torn. Now!"),
    ("message.skyabyss.storm_heart.defeated", "雷翼の心臓が止まった。胸の奥の風が、はじめて止んだ", "The thunder wing's heart has stopped. The wind in its chest falls still for the first time"),
    ("message.skyabyss.storm_heart.pillar", "光の柱が、尾の間へ運んでくれた", "The pillar of light carried you to the tail chamber"),
    ("message.skyabyss.storm_heart.exit_hint", "光の柱に立つと、尾の間へ戻る", "Stand in the pillar of light to return to the tail chamber"),
    ("message.skyabyss.storm_heart.reward", "雷翼の心臓が遺したものが、手もとに渡った", "What the thunder wing's heart left behind has passed into your hands"),
    ("message.skyabyss.storm_heart.choice_later", "初めての撃破の贈り物 (専用伝説から一つ) は、品の形ができてから /titanmemory storm で選べる",
     "Your first-victory gift (one of the unique legendaries) can be chosen with /titanmemory storm once the items exist"),
    ("subtitles.skyabyss.storm_heart_beat", "竜骨の奥で、心臓が打つ", "A heart beats behind the keel"),
    ("subtitles.skyabyss.storm_heart_roar", "心臓が咆哮する", "The heart roars"),
    ("subtitles.skyabyss.storm_heart_strike", "雷を呼ぶ音", "Thunder is called"),
    ("subtitles.skyabyss.storm_heart_blade", "風の刃が笛のように鳴る", "Wind blade whistles"),
    ("subtitles.skyabyss.storm_heart_chain", "雷の鎖が伸びる", "Lightning chain stretches"),
    ("subtitles.skyabyss.storm_heart_flap", "大きな羽ばたき", "Great wingbeat"),
    ("subtitles.skyabyss.storm_heart_floor", "床を火花が這う", "Sparks crawl over the floor"),
    ("subtitles.skyabyss.storm_heart_feathers", "羽根が降る", "Feathers rain down"),
    ("subtitles.skyabyss.storm_heart_flash", "心臓が光を吸い込む", "The heart draws in the light"),
    ("subtitles.skyabyss.storm_heart_web", "稲妻の網が回る", "Lightning web turns"),
    ("subtitles.skyabyss.storm_heart_death", "心臓が止まり、風が止む", "The heart stops; the wind falls still"),
]:
    anticipate("4", k, ja, en)

# ---- 担当 5: 装備 (6 章)
for k, ja, en in [
    ("item.skyabyss.thunder_wing_shard", "雷翼の羽軸", "Thunder Wing Quill"),
    ("item.skyabyss.thunder_wing_core", "雷翼の心核", "Thunder Wing Core"),
    ("item.skyabyss.cloudgap_fruit", "雲間の干し果", "Cloudgap Dried Fruit"),
    ("item.skyabyss.cloudgap_fruit.desc", "食べると、しばらく静電を帯びない", "After eating, you carry no static for a while"),
    ("item.skyabyss.thunder_wing_helmet", "雷翼の羽衣・兜", "Thunder Wing Mantle Helm"),
    ("item.skyabyss.thunder_wing_chestplate", "雷翼の羽衣・胸当て", "Thunder Wing Mantle Breastplate"),
    ("item.skyabyss.thunder_wing_leggings", "雷翼の羽衣・脚衣", "Thunder Wing Mantle Leggings"),
    ("item.skyabyss.thunder_wing_boots", "雷翼の羽衣・靴", "Thunder Wing Mantle Boots"),
    ("item.skyabyss.thunder_wing_bow", "雷翼の弓", "Thunder Wing Bow"),
    ("item.skyabyss.storm_anklet", "嵐纏の足環", "Stormclad Anklet"),
    ("item.skyabyss.storm_charm", "嵐の護符", "Storm Charm"),
    ("item.skyabyss.raiju_claw", "雷獣の爪", "Raiju Claw"),
    ("item.skyabyss.warden_hook_spear", "羽守の鉤槍", "Warden's Hook Spear"),
    ("item.skyabyss.storm_eye_feather", "嵐の目の羽根", "Feather of the Storm's Eye"),
]:
    anticipate("5", k, ja, en)


# ================================================================ 看板の幅 (SignFit と同じ表)
ASCII_WIDTHS = "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247"


def width(t: str) -> int:
    w = 0
    for c in t:
        o = ord(c)
        w += 4 if c == " " else (int(ASCII_WIDTHS[o - 33]) if 32 < o < 127 else 9)
    return w


def check_signs() -> list[str]:
    bad = []
    for k, v in list(JA.items()):
        if k.startswith("sign.skyabyss."):
            for lang, t in (("ja", v), ("en", EN[k])):
                if width(t) > 90:
                    bad.append(f"{k} ({lang}) {width(t)} px: {t}")
            if len(v) > 8:
                bad.append(f"{k} (ja) {len(v)} chars (max 8): {v}")
    return bad


# ================================================================ parts と合わせる
def merge_parts():
    report = {"no_ja": [], "unclaimed": [], "parts": []}
    claimed = set()
    if PARTS.is_dir():
        for f in sorted(PARTS.glob("*.json")):
            report["parts"].append(f.name)
            data = json.loads(f.read_text(encoding="utf-8"))
            for k, v in data.items():
                en = v.get("en", "") if isinstance(v, dict) else v
                ja = v.get("ja") if isinstance(v, dict) else None
                claimed.add(k)
                if k in JA:
                    continue
                EN[k] = en
                JA[k] = ja if ja else en
                OWNER[k] = f.stem
                if not ja:
                    report["no_ja"].append(f"{f.stem}: {k}")
    if report["parts"]:
        report["unclaimed"] = [k for k, o in OWNER.items() if o not in ("6",) and k not in claimed]
    return report


# ================================================================ 書き出し
def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def md(s: str) -> str:
    return s.replace("|", "\\|").replace("\n", "<br>")


def doc_tables() -> str:
    L = ["#### 碑文と入口の一言（生成。看板の幅は SignFit と同じ表で測った）\n", "| 看板 | 日本語 | 英語 | 英語の最大幅 |", "| --- | --- | --- | --- |"]
    for place in INSCRIPTIONS:
        k = f"sign.skyabyss.lore_storm_{place}"
        ja = " / ".join(JA[f"{k}.{i}"] for i in range(1, 5))
        en = " / ".join(EN[f"{k}.{i}"] for i in range(1, 5))
        L.append(f"| `{k}` | {ja} | {en} | {max(width(EN[f'{k}.{i}']) for i in range(1, 5))} px |")
    for t in TRIAL_SIGNS:
        k = f"sign.skyabyss.storm_{t}"
        L.append(f"| `{k}` | {JA[k + '.1']} / {JA[k + '.2']} | {EN[k + '.1']} / {EN[k + '.2']} | {max(width(EN[f'{k}.{i}']) for i in (1, 2))} px |")
    L += ["\n#### 囁き（生成。`StormWhispers`）\n", "| ID | 種類 | 日本語 | 英語 |", "| --- | --- | --- | --- |"]
    for wid, kind, ja, en in WHISPERS:
        L.append(f"| `{wid}` | {kind} | {md(ja)} | {md(en)} |")
    L += ["\n#### 伝承の項目（生成。`lore.skyabyss.chapter1.*`）\n", "| 項目 | 順 | 記される条件 | 題 | 本文 |", "| --- | --- | --- | --- | --- |"]
    for lid, order, unlock_, related, title, body, sign in LORE:
        cond = f"碑文 `sign.skyabyss.lore_storm_{sign}`" if sign else "、".join(
            f"{u['type']} {u.get('target', '')}" + (f" {u['radius']:g}" if u.get("radius") else "") for u in unlock_)
        L.append(f"| `chapter1/{lid}` | {order} | {md(cond)} | {title[0]} | {md(body[0])} |")
    L += ["\n#### 雷翼の背の周回（生成。言伝ての板、段階 3）\n", "| クエスト | 繰り返し | 目的 | 書き付け | 報酬 | 短い筋 |", "| --- | --- | --- | --- | --- | --- |"]
    for qid, out, title, story in QUESTS:
        st = out["stages"][0]
        o = st["objectives"][0]
        items = "、".join(f"{i['item'].split(':')[1]} ×{i['count']}" + ("（品ができたら）" if i.get("optional") else "") for i in out["reward"]["items"])
        base = f"quest.skyabyss.{qid}.{st['id']}"
        rep = "日替わり" if out["repeat"] == "daily" else "週替わり"
        L.append(f"| {title[0]} `{qid}` | {rep} | {JA[base + '.' + o['id']]} ×{o['count']} "
                 f"| {md(JA[base + '.journal'])} | 経験値 {out['reward']['xp']}、{items} | {md(story)} |")
    L += ["\n#### ほかの担当の文（想定した鍵。合わせるときに lang/parts と照らす）\n", "| 担当 | 鍵の数 | 例 |", "| --- | --- | --- |"]
    for owner in ("1", "2", "3", "4", "5"):
        ks = [k for k, o in OWNER.items() if o == owner]
        if ks:
            L.append(f"| {owner} | {len(ks)} | `{ks[0]}` = {md(JA[ks[0]])}、`{ks[-1]}` = {md(JA[ks[-1]])} |")
    return "\n".join(L) + "\n"


START_MARK = "<!-- 伝承とクエスト 生成ここから (tools/gen_storm_lore.py が書き換える。手で直さない) -->"
END_MARK = "<!-- 伝承とクエスト 生成ここまで -->"


def main():
    bad = check_signs()
    if bad:
        print("\n".join(bad))
        sys.exit("signs do not fit")
    report = merge_parts()
    for lid, order, unlock_, related, title, body, sign in LORE:
        entry = {"chapter": "titan_storm", "order": order}
        if sign:
            key = f"sign.skyabyss.lore_storm_{sign}"
            entry["inscription_sign"] = key
            unlock_ = [{"type": "sign", "target": key}]
        entry["unlock"] = unlock_
        entry["related"] = related
        write_json(DATA / "lore" / "entry" / "chapter1" / f"{lid}.json", entry)
    for qid, out, _, _ in QUESTS:
        write_json(DATA / "quest" / f"{qid}.json", out)
    write_json(LANG / "ja_jp.json", dict(sorted(JA.items())))
    write_json(LANG / "en_us.json", dict(sorted(EN.items())))
    doc = DOC.read_text(encoding="utf-8")
    if START_MARK in doc:
        doc = re.sub(re.escape(START_MARK) + ".*?" + re.escape(END_MARK), lambda m: START_MARK + "\n\n" + doc_tables() + "\n" + END_MARK, doc, flags=re.S)
        with open(DOC, "w", encoding="utf-8", newline="\n") as f:
            f.write(doc)
    mine = sum(1 for o in OWNER.values() if o == "6")
    print(f"{len(JA)} keys ({mine} own, {len(JA) - mine} for other parts), {len(LORE)} lore entries, {len(QUESTS)} quests")
    if report["parts"]:
        print(f"parts: {', '.join(report['parts'])}")
        for k in report["no_ja"]:
            print(f"  no Japanese yet: {k}")
        for k in report["unclaimed"]:
            print(f"  anticipated but unused: {k}")


if __name__ == "__main__":
    main()
