# -*- coding: utf-8 -*-
"""潮殻の背の拡大 (26 の 9 章) の文を assets/skyabyss_tide/lang の ja_jp / en_us に足す。何度流しても同じ (キーは上書き)。
使い方 (リポジトリ直下で): python tools/once/add_tide_scale_lang.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LANG = ROOT / "src/main/resources/assets/skyabyss_tide/lang"

# 碑 (4 行) : id -> (ja 4 行, en 4 行)
TABLETS = {
    "garden_a": (["潮が引くと", "ここに座り", "真珠の行方を", "待った者がいた"], ["At the ebb", "someone sat here", "and waited for", "the pearl's road"]),
    "garden_b": (["大貝は眠らず", "ただ息を止める", "泡の数を数え", "夜を越えた"], ["The clam never", "sleeps, it holds", "its breath. Count", "bubbles till dawn"]),
    "neck_a": (["背骨は鳴る", "歩く者の数だけ", "一つ目の骨は", "帰りの音"], ["The spine rings", "once per walker.", "The first bone", "is the way back"]),
    "neck_b": (["巣は引き潮に", "口を開ける", "満ちる前に", "壊しておけ"], ["Nests open at", "the ebb. Break", "them before the", "flood returns"]),
    "cave": (["ここで休め", "広間の水は", "まだ満ちない", "息を整えよ"], ["Rest here.", "The hall's water", "has not risen.", "Steady breath"]),
    "terrace": (["灯台の目は", "海でなく", "歩く者を見る", "迷うな"], ["The lamp looks", "not at the sea", "but at walkers.", "Do not stray"]),
    "crown_1": (["冠は貝の", "積もった年", "一枚が一潮", "数は誰も知らぬ"], ["The crown is", "shells piled by", "years; one for", "each tide"]),
    "crown_2": (["息は上へ", "昇る者に", "皿は水を", "貸している"], ["Breath rises;", "the dishes lend", "their water to", "those who climb"]),
    "crown_3": (["最上の皿から", "塔の中は", "風の道だけが", "知っている"], ["From the last", "dish, the tower", "is known only", "to the wind"]),
    "cairn_0": (["北西の標石", "足もとの下に", "庭がある。壁の", "色を覚えよ"], ["NW cairn.", "Below you lies", "the garden. Mark", "its wall colours"]),
    "cairn_1": (["北東の標石", "下は潮見の間", "緑青の水門。", "水位を数えよ"], ["NE cairn.", "Below: the Tide", "Watch. Count the", "water levels"]),
    "cairn_2": (["南東の標石", "遠くに灯台が", "ひとつ。ひれの", "先を見よ"], ["SE cairn.", "A lamp far off,", "alone. Look to", "the fin's tip"]),
    "cairn_3": (["南西の標石", "浜の天窓が", "足もとに光る", "潮門は近い"], ["SW cairn.", "Skylights glow", "below you.", "The gate is near"]),
    "cairn_4": (["北の標石", "首の先に頭が", "ある。亀は北へ", "歩いている"], ["North cairn.", "Beyond the neck", "the head. It", "walks north"]),
    "fin_w_gate": (["西のひれの道", "貝の間は二つ", "先に景色と宝", "宝は隠れている"], ["West fin road:", "two shell halls,", "then a view and", "a hidden cache"]),
    "fin_e_gate": (["東のひれの道", "貝の間は二つ", "先に灯りと宝", "宝は隠れている"], ["East fin road:", "two shell halls,", "then a lamp and", "a hidden cache"]),
    "fin_w1": (["三つの貝が鳴く", "低い声から", "高い声へ", "順に叩け"], ["Three shells.", "Strike them from", "the low voice to", "the high voice"]),
    "fin_w2": (["海は向きを変える", "引き潮は昇り", "満ち潮は降りる", "声の順も同じ"], ["The sea turns:", "at the ebb climb,", "at the flood fall.", "So does the song"]),
    "fin_e1": (["壁の色を読め", "北から南へ", "同じ色の貝を", "順に叩け"], ["Read the wall.", "North to south,", "strike the shell", "of each colour"]),
    "fin_e2": (["海は鏡に書く", "壁を南から", "北へ読み", "同じ色を叩け"], ["The sea writes in", "a mirror. Read", "the wall south to", "north, then strike"]),
    "fin_w_tip": (["ひれの先に着いた", "海は広く", "亀は止まらない", "歩み続ける"], ["The fin's tip.", "The sea is wide.", "The turtle never", "stops walking"]),
    "fin_e_tip": (["東の先で", "灯りは海を見ない", "歩く者を見る", "あなたを見る"], ["At the east tip", "the lamp watches", "no sea but you", "who walked here"]),
}
MESSAGES = {
    "message.skyabyss.tide_fin_done": ("貝はもう鳴り終えた", "The shells have sung already"),
    "message.skyabyss.tide_fin_open": ("奥の戸がほどけた", "The far gate loosens"),
    "message.skyabyss.tide_fin_step": ("貝の音 %s / %s", "Shell %s / %s"),
    "message.skyabyss.tide_fin_wrong": ("違う。最初から", "Wrong. Begin again"),
    "message.skyabyss.tide_cache_found": ("隠れた宝を見つけた", "You found a hidden cache"),
    "message.skyabyss.tide_cache_empty": ("宝箱は空だ。もう取った", "The chest is empty. You took it already"),
}


def main():
    for lang, idx in (("ja_jp", 0), ("en_us", 1)):
        p = LANG / f"{lang}.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        for tid, texts in TABLETS.items():
            for i, line in enumerate(texts[idx], 1):
                d[f"sign.skyabyss.tide_lore_{tid}.{i}"] = line
        for k, v in MESSAGES.items():
            d[k] = v[idx]
        extra = EXTRA.get(lang, {})
        d.update(extra)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(lang, len(d))


# 後から足す文 (ひれの道・貝の間・目的の板など)。章ごとに追記する
EXTRA = {"ja_jp": {
    "gui.skyabyss.tide_goal.title.fin": "ひれの道",
    "gui.skyabyss.tide_goal.title.crown": "冠の回廊",
    "gui.skyabyss.tide_goal.fin.hall": "貝を決まりの順に叩く (%1$s / %2$s)。間違えても罰はない。奥の戸が開く",
    "gui.skyabyss.tide_goal.fin.tip": "戸は開いた。先端の碑と宝へ。戻りは同じ道",
    "gui.skyabyss.tide_goal.crown.ring": "冠の回廊。壁の碑と宝を探せ。昇るなら縁の光る泡",
}, "en_us": {
    "gui.skyabyss.tide_goal.title.fin": "Fin road",
    "gui.skyabyss.tide_goal.title.crown": "Crown gallery",
    "gui.skyabyss.tide_goal.fin.hall": "Strike the shells in the rule's order (%1$s / %2$s). No penalty for a miss. The far gate opens",
    "gui.skyabyss.tide_goal.fin.tip": "The gate is open. On to the tablet and cache at the tip. Return the same way",
    "gui.skyabyss.tide_goal.crown.ring": "Crown gallery. Find the tablets and caches. To climb, use the glowing bubble on the rim",
}}

if __name__ == "__main__":
    main()
