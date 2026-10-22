# -*- coding: utf-8 -*-
"""試練のプール P3 (docs/design/29 の 3.4・3.7) の余燼の新作 5 (E03・E04・E08・E11・E12) の文を lang に足す。何度流しても同じ (キーは上書き)。
- assets/skyabyss_trial/lang: セルの名前と説明・修飾の口・任意の目標・障害・敵の組・一行の知らせ
- assets/skyabyss_ember/lang: 碑 (sign.skyabyss.ember_board_<id>.1〜4)・休み場の名前 (checkpoint.skyabyss.ember_k_*)
共通の名前 (no_burn・moths・gust・vents・waves) は、もとの文があればそのまま残す (SHARED)。
使い方 (リポジトリ直下で): python tools/once/add_trial_p3_ember_lang.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRIAL_LANG = ROOT / "src/main/resources/assets/skyabyss_trial/lang"
EMBER_LANG = ROOT / "src/main/resources/assets/skyabyss_ember/lang"

# key -> (ja, en)  試練の文 (skyabyss_trial)
ENTRIES = {
    # ---- セル
    "trial.skyabyss.cell.E03": ("鎖橋の溶けた段", "Melting Chain Bridge"),
    "trial.skyabyss.cell.E03.desc": ("熱くなる段を渡る。赤い段で焼け、坑へ落ちたら戻る。", "Cross planks that heat up under you. Red ones burn, and a fall sends you back."),
    "trial.skyabyss.cell.E04": ("熾の運び", "Carrying the Ember"),
    "trial.skyabyss.cell.E04.desc": ("熾を水と風から守って、道の終わりの火床まで運ぶ。", "Carry the coal to the far hearth, keeping it out of water and wind."),
    "trial.skyabyss.cell.E08": ("鋳型の間", "Mould Hall"),
    "trial.skyabyss.cell.E08.desc": ("示された色の順を覚え、同じ色の鋳型へ注ぐ。", "Remember the colour order shown and pour into the matching moulds."),
    "trial.skyabyss.cell.E11": ("灰荷車の護送", "Ash Cart Escort"),
    "trial.skyabyss.cell.E11.desc": ("荷車を押して炉まで。襲撃から守る。", "Push the cart to the furnace and guard it from raids."),
    "trial.skyabyss.cell.E12": ("大燠虫の巣", "Ember Bug Nest"),
    "trial.skyabyss.cell.E12.desc": ("丸まる虫は硬い。目を回したところを叩く。", "Curled bugs are hard. Strike them when they are dizzy."),
    # ---- 修飾の口
    "trial.skyabyss.num.hot_time": ("熱くなるまで", "Time to heat"),
    "trial.skyabyss.num.cool_time": ("冷えるまで", "Time to cool"),
    "trial.skyabyss.num.gust_period": ("風の間隔", "Gust interval"),
    "trial.skyabyss.num.pools": ("水の溜まり", "Water pools"),
    "trial.skyabyss.num.steps": ("示しの数", "Steps shown"),
    "trial.skyabyss.num.show_speed": ("示しの速さ", "Display speed"),
    "trial.skyabyss.num.raids": ("襲撃の回数", "Raids"),
    "trial.skyabyss.num.push_speed": ("押す速さ", "Push speed"),
    "trial.skyabyss.num.rollers": ("一波の大燠虫", "Bugs per wave"),
    # ---- 障害・任意の目標・敵の組
    "trial.skyabyss.hazard.steam": ("湯気", "Steam"),
    "trial.skyabyss.goal.no_douse": ("一度も消さずに", "Never let it go out"),
    "trial.skyabyss.goal.one_pour": ("一度で注ぐ", "Pour right the first time"),
    "trial.skyabyss.goal.no_dent": ("荷車を無傷で", "Cart unscratched"),
    "trial.skyabyss.goal.no_vent": ("噴気を踏まずに", "Without touching a vent"),
    "trial.skyabyss.enemy.E11.0": ("灰の番人の群れ", "Ash pack"),
    "trial.skyabyss.enemy.E11.1": ("大燠虫つき", "With ember bugs"),
    "trial.skyabyss.enemy.E12.0": ("大燠虫だけ", "Bugs only"),
    "trial.skyabyss.enemy.E12.1": ("灰の番人つき", "With ash thralls"),
    # ---- 一行の知らせ
    "message.skyabyss.trial.e03.start": ("段を西へ渡れ。立ち続けると段は赤くなり、赤い段で焼ける。坑へ落ちたら最後に立った段へ戻る",
                                         "Cross west. Standing still heats a plank red, and a red plank burns. A fall returns you to your last plank"),
    "message.skyabyss.trial.e03.fell": ("焼けた、または坑へ落ちた。最後に立った段へ戻る", "Burned or fell. Back to your last plank"),
    "message.skyabyss.trial.e03.crossed": ("向こうの岸に着いた。熾り石を鳴らせ", "You reached the far bank. Ring the stone"),
    "message.skyabyss.trial.e04.start": ("石から熾を取り、道の終わりの火床まで運べ。水に入ると熾は消える",
                                         "Take the coal from the stone and carry it to the far hearth. Water puts it out"),
    "message.skyabyss.trial.e04.taken": ("熾を取った。持つあいだ熱がたまる", "You took the coal. Heat builds while you carry it"),
    "message.skyabyss.trial.e04.held": ("熾はもう誰かが持っている", "Someone is already carrying the coal"),
    "message.skyabyss.trial.e04.gust_warn": ("風が来る。しゃがむか、仲間のそばに", "Wind is coming. Crouch, or stand near an ally"),
    "message.skyabyss.trial.e04.pushed": ("風に押された", "The wind pushed the coal"),
    "message.skyabyss.trial.e04.doused": ("熾が消えた。石からもう一度", "The coal went out. Take a new one from the stone"),
    "message.skyabyss.trial.e04.reached": ("火床に熾を運んだ。石を鳴らせ", "The coal reached the hearth. Ring the stone"),
    "message.skyabyss.trial.e08.start": ("注ぎ口が色を順に見せる。覚えて、同じ色の鋳型へ注げ", "The spout shows the colours in order. Remember them and pour into the matching mould"),
    "message.skyabyss.trial.e08.wait": ("まだ見返せない。少し待て", "Not yet. Wait a moment"),
    "message.skyabyss.trial.e08.again": ("もう一度見せる", "Showing it again"),
    "message.skyabyss.trial.e08.locked": ("まだ熱い。あと %s 秒", "Still hot. %s s left"),
    "message.skyabyss.trial.e08.wait_show": ("示しの途中だ。終わってから注げ", "The display is still running. Pour after it ends"),
    "message.skyabyss.trial.e08.poured": ("注いだ (%s / %s)", "Poured (%s / %s)"),
    "message.skyabyss.trial.e08.wrong": ("違う鋳型だ。熱が上がり、示しは最初から", "Wrong mould. Heat rises and the display starts over"),
    "message.skyabyss.trial.e08.cleared": ("示しを全部注いだ。石を鳴らせ", "All poured. Ring the stone"),
    "message.skyabyss.trial.e11.start": ("荷車のそばに立って押せ。敵が寄ると傷む。襲撃を退けて炉まで",
                                         "Stand near the cart to push it. Foes near it wear it down. Beat the raids and reach the furnace"),
    "message.skyabyss.trial.e11.raid": ("襲撃が来た (%s / %s)", "A raid comes (%s / %s)"),
    "message.skyabyss.trial.e11.broken": ("荷車が壊れた。最後の襲撃の所へ戻る", "The cart broke. Back to the last raid"),
    "message.skyabyss.trial.e11.arrived": ("荷車が炉に着いた。石を鳴らせ", "The cart reached the furnace. Ring the stone"),
    "message.skyabyss.trial.e12.start": ("穴から大燠虫が転がり出る。丸まった虫は硬い。壁にぶつかって目を回したら叩け",
                                         "Ember bugs roll out of the holes. A curled bug is hard. Strike it once it dizzies on a wall"),
    "message.skyabyss.trial.e12.wave": ("第 %s 波 / %s (大燠虫 %s)", "Wave %s / %s (%s bugs)"),
    "message.skyabyss.trial.e12.cleared": ("巣が静まった。石を鳴らせ", "The nest is quiet. Ring the stone"),
}

# 共通の名前: もとの文があれば残す
SHARED = {
    "trial.skyabyss.goal.no_burn": ("一度も焼かれずに", "Never burned"),
    "trial.skyabyss.hazard.moths": ("火の粉蛾", "Ember moths"),
    "trial.skyabyss.hazard.gust": ("突風", "Gust"),
    "trial.skyabyss.hazard.vents": ("噴気", "Vents"),
    "trial.skyabyss.num.waves": ("波の数", "Waves"),
}

# 碑 (skyabyss_ember): 各 4 行。向きと置き場は EmberDeck.BOARDS
SIGNS = {
    "e03_rule": (["鎖橋の溶けた段", "段は乗るほど熱くなる", "赤い段は踏むと焼ける", "離れれば冷める"],
                 ["Melting Chain Bridge", "Planks heat while stood on", "A red plank burns you", "Step off to cool it"]),
    "e03_hot": (["坑に落ちると", "最後に立った段へ戻る", "灰色の段は", "冷えが速い"],
                ["A fall into the pit", "returns you to your last plank", "Grey planks cool fast", "so cross them quickly"]),
    "e04_rule": (["熾の運び", "熾り石で熾を取る", "熾は水に入ると消える", "水は跳んで越えよ"],
                 ["Carrying the Ember", "Take the coal at the stone", "Water puts the coal out", "Jump over the pools"]),
    "e04_wind": (["風が来る", "しゃがむか、仲間のそばで", "熾を庇う", "道の終わりの火床へ"],
                 ["Wind comes", "Crouch, or stand near an ally", "to shield the coal", "Carry it to the far hearth"]),
    "e08_rule": (["鋳型の間", "注ぎ口が色を見せる", "覚えて同じ色の鋳型へ", "違えば三秒止まる"],
                 ["Mould Hall", "The spout shows colours", "Pour into the same colour", "A miss locks you for 3 s"]),
    "e08_mirror": (["印のついた示しは", "次の色の鋳型へ", "注ぎ口を右クリック", "で見返せる"],
                   ["A marked display", "pours into the next colour", "Right-click the spout", "to see it again"]),
    "e11_rule": (["灰荷車の護送", "荷車はそばに人がいて", "敵が近くにいないあいだ進む", "敵が寄ると傷む"],
                 ["Ash Cart Escort", "The cart moves only with", "someone near and no foes close", "Foes near it wear it down"]),
    "e11_road": (["襲撃は道のわきから", "止めて倒し、また押す", "荷車が壊れたら", "最後の襲撃へ戻る"],
                 ["Raids come from the sides", "Stop, beat them, push on", "If the cart breaks", "it returns to the last raid"]),
    "e12_rule": (["大燠虫の巣", "丸まった虫は硬い", "転がって壁にぶつかると", "目を回す。そこを叩け"],
                 ["Ember Bug Nest", "A curled bug is hard", "It dizzies on hitting a wall", "Strike it then"]),
    "e12_vent": (["噴気の割れ目は", "光ってから噴く", "噴いている上に立つと焼ける", "穴の線から外れよ"],
                 ["Vent cracks", "glow, then blast", "Standing on a blast burns", "Keep off the bug lines"]),
}

# 休み場 (checkpoint): 余燼の背の試練の階
EMBER_ENTRIES = {
    "checkpoint.skyabyss.ember_k_e03": ("鎖橋の間の入口", "Chain bridge entrance"),
    "checkpoint.skyabyss.ember_k_e08": ("鋳型の間の入口", "Mould hall entrance"),
    "checkpoint.skyabyss.ember_k_e11": ("荷車の道の入口", "Cart road entrance"),
    "checkpoint.skyabyss.ember_k_e12": ("大燠虫の巣の入口", "Bug nest entrance"),
    "checkpoint.skyabyss.ember_k_e04": ("熾の道の入口", "Coal road entrance"),
}


def sign_entries():
    out = {}
    for name, (ja, en) in SIGNS.items():
        for i in range(4):
            out["sign.skyabyss.ember_board_" + name + "." + str(i + 1)] = (ja[i], en[i])
    return out


def merge(path, idx, entries, shared=None):
    data = json.loads(path.read_text(encoding="utf-8"))
    for key, pair in entries.items():
        data[key] = pair[idx]
    if shared:
        for key, pair in shared.items():
            data.setdefault(key, pair[idx])
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    merge(TRIAL_LANG / "ja_jp.json", 0, ENTRIES, SHARED)
    merge(TRIAL_LANG / "en_us.json", 1, ENTRIES, SHARED)
    signs = sign_entries()
    merge(EMBER_LANG / "ja_jp.json", 0, {**signs, **EMBER_ENTRIES})
    merge(EMBER_LANG / "en_us.json", 1, {**signs, **EMBER_ENTRIES})
    print("wrote", len(ENTRIES), "trial keys and", len(signs) + len(EMBER_ENTRIES), "ember keys")


if __name__ == "__main__":
    main()
