# -*- coding: utf-8 -*-
"""前の私の落とし物 (深淵の墓の遺品 12 点) の文。docs/design/36 の 9 章・決定 257。

  python -I tools/abyss/gen_abyss_relic_texts.py

書くもの: src/main/resources/assets/skyabyss_abyss_rl/lang/ja_jp.json・en_us.json
  item.skyabyss.abyss_relic_<層>_<番号>        品の名
  item.skyabyss.abyss_relic_<層>_<番号>.desc   品の一行 (墓の文の要点)
  item.skyabyss.abyss_relic.set_hint / .bonus.2 / .bonus.4 / .bonus.6   組で効く段の説明
  message.skyabyss.abyss.relic.got             受け取ったとき (AbyssRelics.onGrave)
  message.skyabyss.abyss.relic.all             十二の墓をすべて弔ったとき (六章の手がかりの前ふり)
  lore.skyabyss.ch5.graves_all.title / body    伝承の頁 (data/skyabyss/lore/entry/ch5/graves_all.json)
文の調子は tools/gen_ch5_entrance.py の墓の文に合わせる。六章の月は名指しせず「欠けた丸いもの」で示す。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_abyss_rl", "lang")

# 層, 番号, 品の名 (ja, en), 一行 (ja, en)
RELICS = [
    ("bone", 1, ("握っていた骨片", "Clutched Bone Shard"),
     ("眠るまで握っていた、骨の欠片。", "A bone shard, held until the end.")),
    ("bone", 2, ("空の手燭", "Empty Taper Lamp"),
     ("灯りの入っていない、小さな手燭。「七度目」の墓のそばにあった。", "A small lamp with no light. It lay beside the grave marked \"the seventh time\".")),
    ("bone", 3, ("帳面の切れ端", "Notebook Scrap"),
     ("石に挟まっていた切れ端。「上で待っている人に、ごめんと」。", "A scrap wedged under a stone. \"Tell the one waiting above I'm sorry.\"")),
    ("salt", 1, ("上を向いた塩", "Salt That Faces Up"),
     ("塩に覆われた形の欠片。顔の向きは、上を見ていた。", "A salt-crusted fragment. Its face was turned upward.")),
    ("salt", 2, ("自分と同じ名前の札", "Tag With Your Name"),
     ("名札の一つ。自分と同じ名前で、日付だけが違う。", "A name tag bearing your own name. Only the date is different.")),
    ("salt", 3, ("三つの貝殻", "Three Shells"),
     ("あとから供えられた貝殻。三つ、並んでいた。", "Shells left by someone later. Three, laid in a row.")),
    ("storm", 1, ("焦げた外套の紐", "Scorched Cloak Cord"),
     ("焦げた外套の紐。拍を数えた指の跡が残っている。", "A scorched cloak cord. Traces of fingers counting beats remain.")),
    ("storm", 2, ("銅の輪", "Copper Ring"),
     ("雷で溶けた名札から、残った銅の輪。", "A copper ring left when the lightning melted the tag.")),
    ("storm", 3, ("起こし鈴", "Wake Bell"),
     ("「次の私が来たら、起こして」。鳴らすと、遠くで返事のような音がした気がする。", "\"Wake me when the next me arrives.\" When it rings, something far off seems to answer.")),
    ("ash", 1, ("灯りを探した手袋", "Glove That Searched for Light"),
     ("灰の中に指の跡を残した手袋。最後まで灯りを探していた。", "A glove that left finger marks in the ash. It kept searching for a light to the end.")),
    ("ash", 2, ("白紙の名札", "Blank Name Tag"),
     ("名前を書く前に来た人の、白い名札。", "The white tag of someone who came before writing a name.")),
    ("ash", 3, ("心臓の形の石", "Heart-Shaped Stone"),
     ("墓の石の裏に彫られていた、心臓の形の石片。打っているように見える。", "A stone chip carved with a heart from the back of a grave. It almost seems to beat.")),
]

# 組で効く段 (AbyssRelics.Bonus と揃える)
BONUS = {
    "2": ("夜目。深淵の中で、灯りの先まで見える。声がたまるのも遅くなる。", "Night sight. In the abyss you see past the light, and your voice gathers more slowly."),
    "4": ("足音の警告。近くの深淵の敵が来ると、足もとで鈴が鳴る。", "Footsteps warn. A bell sounds underfoot when an abyss foe comes near."),
    "6": ("軽い足。深淵の中で、跳ぶと少し高く跳べる。", "Light step. In the abyss you jump a little higher."),
}

ALL_NARRATION = (
    "十二の墓を、すべて弔った。遺品はどれも、同じ夜空を見上げるように置かれていた。欠けた丸いものを、誰もが指さしている。",
    "All twelve graves are mourned. Every belonging was left facing the same night sky. Each one points at something round and broken.",
)

LORE_TITLE = ("前の私の落とし物（十二）", "Belongings of Those Before (Twelve)")
LORE_BODY = (
    "深淵の四つの層で、前の私の墓を十二、弔った。\n\n"
    "遺品はどれも、同じ夜空を見上げるように置かれていた。\n\n"
    "空に、欠けた丸いものがあった。誰も、それがなんなのかを書き残していない。\n\n"
    "「あれが満ちたら、上へ戻れる」\n\n"
    "名札を書き忘れた人だけが、その一言を残していた。",
    "Twelve graves of those before, mourned across the four layers of the abyss.\n\n"
    "Every belonging was left facing the same night sky.\n\n"
    "In the sky there was something round and broken. No one wrote down what it was.\n\n"
    "\"When that is full, we can go back up.\"\n\n"
    "Only the one who forgot to write a name left those words.",
)


def build(lang_index):
    """lang_index: 0 = ja_jp, 1 = en_us。キーと文の対応を作る。"""
    out = {}
    for layer, n, name, desc in RELICS:
        rid = "abyss_relic_%s_%d" % (layer, n)
        out["item.skyabyss." + rid] = name[lang_index]
        out["item.skyabyss." + rid + ".desc"] = desc[lang_index]
    out["item.skyabyss.abyss_relic.set_hint"] = (
        "二つ・四つ・六つ、違う遺品を持っていると効く（持っているあいだ）" if lang_index == 0
        else "Works while carried: two, four or six different belongings.")
    for need, pair in BONUS.items():
        out["item.skyabyss.abyss_relic.bonus." + need] = pair[lang_index]
    out["message.skyabyss.abyss.relic.got"] = ("前の私の落とし物を受け取った: %s" if lang_index == 0
                                               else "A belonging of one before: %s")
    out["message.skyabyss.abyss.relic.all"] = ALL_NARRATION[lang_index]
    out["lore.skyabyss.ch5.graves_all.title"] = LORE_TITLE[lang_index]
    out["lore.skyabyss.ch5.graves_all.body"] = LORE_BODY[lang_index]
    return out


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    write(os.path.join(LANG, "ja_jp.json"), build(0))
    write(os.path.join(LANG, "en_us.json"), build(1))
    print("wrote", LANG)


if __name__ == "__main__":
    main()
