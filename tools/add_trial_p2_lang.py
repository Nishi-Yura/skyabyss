# -*- coding: utf-8 -*-
"""試練のプール P2 (docs/design/29) の文を assets/skyabyss_trial/lang の ja_jp / en_us に足す。何度流しても同じ (キーは上書き)。
岩鯨の新作 4 (W03・W04・W11・W12)・潮殻の新作 4 (T02・T06・T07・T10)・試練の階の休み場の名前・修飾の口の名前。
使い方 (リポジトリ直下で): python tools/add_trial_p2_lang.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/skyabyss_trial/lang"

# key -> (ja, en)
ENTRIES = {
    # ---- 岩鯨: セル
    "trial.skyabyss.cell.W03": ("苔の滑り坂", "Mossy Slide"),
    "trial.skyabyss.cell.W03.desc": ("流れる苔の床を、骨の島を踏み石に下る。", "Descend a flowing moss slope, using bone islands as footholds."),
    "trial.skyabyss.cell.W04": ("噴気孔の昇り", "Blowhole Ascent"),
    "trial.skyabyss.cell.W04.desc": ("吹き上げに乗って縦の穴を昇る。", "Ride the blasts up a vertical shaft."),
    "trial.skyabyss.cell.W11": ("棺の護送", "Coffin Escort"),
    "trial.skyabyss.cell.W11.desc": ("棺を押して祭壇へ。群れから守る。", "Push the coffin to the altar and guard it from the pack."),
    "trial.skyabyss.cell.W12": ("鯨骨の闘技場", "Whalebone Arena"),
    "trial.skyabyss.cell.W12.desc": ("波ごとに崩れる円い床で戦う。", "Fight on a round floor that falls away wave by wave."),
    # ---- 岩鯨: 一行の知らせ
    "message.skyabyss.trial.w03.start": ("苔の床は、灯りのついた壁の側へ流れる。骨の島で足を止め、流れを読んで次の島へ", "The moss flows toward the lit wall. Rest on bone islands, read the current, and cross to the next one"),
    "message.skyabyss.trial.w03.fell": ("穴に落ちた。最後に立った島へ戻る", "You fell into the gap. Back to the last island you stood on"),
    "message.skyabyss.trial.w03.crossed": ("坂を下りきった。石を鳴らそう", "You reached the bottom of the slope. Ring the stone"),
    "message.skyabyss.trial.w04.start": ("噴気孔の台は、白く瞬いたあと吹き上げる。吹き上げに乗って岩棚を昇り、南の出口へ。飛び降りてもふわりと降りる", "A vent pad flickers white, then blows. Ride the blasts up the ledges to the south exit. The drop into the shaft is gentle"),
    "message.skyabyss.trial.w04.plugged": ("栓の台を踏んだ。みなの吹き上げが長くなる", "Plug pad pressed. Every blast lasts longer"),
    "message.skyabyss.trial.w04.back_down": ("底へ戻った。次の吹き上げを待とう", "Back at the bottom. Wait for the next blast"),
    "message.skyabyss.trial.w04.reached": ("出口の岩棚に着いた。戸が開く", "You reached the exit ledge. The gate opens"),
    "message.skyabyss.trial.w11.start": ("棺の近くに立つと棺が進む。群れに囲まれると動けない。押す人と守る人に分かれ、祭壇まで", "The coffin moves while someone stands near it, but not while foes surround it. Split into pushers and guards and bring it to the altar"),
    "message.skyabyss.trial.w11.raid": ("骨の群れが来た (%s / %s)", "A bone pack attacks (%s / %s)"),
    "message.skyabyss.trial.w11.pinned": ("棺が囲まれている。丈夫さ %s%%", "The coffin is surrounded. Sturdiness %s%%"),
    "message.skyabyss.trial.w11.broken": ("棺が壊れた。最後に群れが来た所からやり直す", "The coffin broke. Back to where the last pack came"),
    "message.skyabyss.trial.w11.arrived": ("棺が祭壇に着いた。奥の戸が開く", "The coffin reached the altar. The far gate opens"),
    "message.skyabyss.trial.w12.start": ("三つの波を退ける。波のたびに床が崩れる。ひびの入った床が先に崩れる", "Beat three waves. The floor falls away after each one. Cracked tiles go first"),
    "message.skyabyss.trial.w12.wave": ("第 %s 波 / %s", "Wave %s / %s"),
    "message.skyabyss.trial.w12.collapse": ("床が崩れる。あと %s 秒", "The floor collapses in %s s"),
    "message.skyabyss.trial.w12.cleared": ("闘技場を制した。出口の橋がかかる", "The arena is yours. The exit bridge appears"),
    "message.skyabyss.trial.w12.reset": ("闘技場が元に戻る", "The arena resets"),
    "message.skyabyss.trial.w12.fell": ("闘技場から落ちた。控えへ戻る", "You fell out of the arena. Back to the foyer"),
    # ---- 岩鯨: 修飾の口の名前
    "trial.skyabyss.num.flow": ("流れの強さ", "Current strength"),
    "trial.skyabyss.num.turn": ("流れの入れ替わり", "Current reversals"),
    "trial.skyabyss.num.vent_period": ("孔の周期", "Vent period"),
    "trial.skyabyss.num.vent_burst": ("吹き上げの長さ", "Blast length"),
    "trial.skyabyss.num.raids": ("襲撃の回数", "Raids"),
    "trial.skyabyss.num.coffin_speed": ("棺の速さ", "Coffin speed"),
    "trial.skyabyss.num.foes": ("波の敵の数", "Foes per wave"),
    "trial.skyabyss.num.warning": ("崩れる前の予告", "Collapse warning"),
    "trial.skyabyss.enemy.W11.0": ("骨の群れ", "Bone pack"),
    "trial.skyabyss.enemy.W11.1": ("射手つき", "With archers"),
    "trial.skyabyss.enemy.W12.0": ("剣と盾", "Swords and shields"),
    "trial.skyabyss.enemy.W12.1": ("弓つき", "With archers"),
    "trial.skyabyss.hazard.surge": ("うねり", "Surge"),
    "trial.skyabyss.goal.no_scratch": ("棺を無傷で", "Coffin unscratched"),
    # ---- 岩鯨: 試練の階の休み場
    "checkpoint.skyabyss.d0": ("試練の階の広間", "Trial deck hall"),
    "checkpoint.skyabyss.s0": ("試練の階 南の広間", "Trial deck south hall"),
    "checkpoint.skyabyss.s1": ("試練の階 最南の広間", "Trial deck far south hall"),
    "checkpoint.skyabyss.w02a": ("鐘楼の入口の岩棚", "Bell tower entry ledge"),
    "checkpoint.skyabyss.w02m": ("鐘楼の中ほどの台", "Bell tower middle platform"),
    "checkpoint.skyabyss.w02c": ("鐘楼の向こうの岩棚", "Bell tower far ledge"),
    "checkpoint.skyabyss.w10a": ("砦の入口", "Fort entrance"),
    "checkpoint.skyabyss.w03a": ("滑り坂の入口の広間", "Slide entry hall"),
    "checkpoint.skyabyss.w03m": ("滑り坂の中ほどの広間", "Slide middle hall"),
    "checkpoint.skyabyss.w04a": ("噴気孔の穴の底", "Blowhole shaft bottom"),
    "checkpoint.skyabyss.w11a": ("護送の広間の入口", "Escort hall entrance"),
    "checkpoint.skyabyss.w12a": ("闘技場の控え", "Arena foyer"),
}


def merge(path, idx):
    data = json.loads(path.read_text(encoding="utf-8"))
    for key, pair in ENTRIES.items():
        data[key] = pair[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    merge(LANG / "ja_jp.json", 0)
    merge(LANG / "en_us.json", 1)
    print("wrote", len(ENTRIES), "keys")


if __name__ == "__main__":
    main()
