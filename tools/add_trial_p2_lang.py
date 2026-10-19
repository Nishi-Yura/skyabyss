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

# ---- 潮殻
ENTRIES.update({
    "message.skyabyss.trial.w12": ("鯨骨の闘技場", "Whalebone Arena"),
    "message.skyabyss.trial.t10": ("満ち潮の円環", "Rising Tide Ring"),
    "trial.skyabyss.cell.T02": ("潮の背渡り", "Tide Crossing"),
    "trial.skyabyss.cell.T02.desc": ("潮で変わる砂州と浮き石の道で、入江を渡る。", "Cross a cove by sandbars at the ebb and floating stones at the flood."),
    "trial.skyabyss.cell.T06": ("貝の合唱", "Shell Chorus"),
    "trial.skyabyss.cell.T06.desc": ("壁画の色の順に、貝を叩く。", "Strike the shells in the order of the mural's colours."),
    "trial.skyabyss.cell.T07": ("二つの海", "Two Seas"),
    "trial.skyabyss.cell.T07.desc": ("栓で二つの水槽の水を動かし、目盛りに合わせる。", "Move the water of two tanks with plugs and match the marks."),
    "trial.skyabyss.cell.T10": ("満ち潮の円環", "Rising Tide Ring"),
    "trial.skyabyss.cell.T10.desc": ("潮が満ちて足場が沈む円い島で戦う。", "Fight on a round island that the rising tide washes away."),
    "message.skyabyss.trial.t02.start": ("引き潮は砂州、満ち潮は浮き石の道。潮の変わりめは二つの道が重なる。浮き石は乗りつづけると沈む。向こうの岸へ", "Sandbars at the ebb, floating stones at the flood; the two paths overlap at the turn. Stones sink if you linger. Reach the far shore"),
    "message.skyabyss.trial.t02.crossed": ("向こうの岸に着いた。石を鳴らそう", "You reached the far shore. Ring the stone"),
    "message.skyabyss.trial.t02.splash": ("水に落ちた。泳いで岸か道へ戻ろう", "You fell in. Swim back to a shore or a path"),
    "message.skyabyss.trial.t06.start": ("壁画の絵の色の順に、七つの貝を叩く。合唱は三つ。違う貝は海を濁らせる", "Strike the seven shells in the order of the mural's colours. Three choruses. A wrong shell clouds the sea"),
    "message.skyabyss.trial.t06.rule0": ("一つ目の合唱: 上の段を、左から右へ", "First chorus: the top row, left to right"),
    "message.skyabyss.trial.t06.rule1": ("二つ目の合唱: 下の段を、右から左へ", "Second chorus: the bottom row, right to left"),
    "message.skyabyss.trial.t06.rule2": ("三つ目の合唱: 上と下を、左から交互に (上・下・上・下)", "Third chorus: top and bottom rows, alternating from the left"),
    "message.skyabyss.trial.t06.step": ("貝の音 %s / %s", "Shell %s / %s"),
    "message.skyabyss.trial.t06.murk": ("海が濁った。この合唱は最初から", "The sea clouded. This chorus starts over"),
    "message.skyabyss.trial.t06.recalled": ("壁画がしばらく浮かぶ", "The mural surfaces for a while"),
    "message.skyabyss.trial.t06.recall_showing": ("壁画はいま見えている", "The mural is showing now"),
    "message.skyabyss.trial.t06.recall_always": ("壁画はずっと見えている", "The mural stays visible"),
    "message.skyabyss.trial.t06.recall_cooling": ("書見台はまだ冷めていない。あと %s 秒", "The lectern is still cooling. %s s left"),
    "message.skyabyss.trial.t06.solved": ("合唱が終わった。奥の戸が開く", "The chorus is done. The far gate opens"),
    "message.skyabyss.trial.t06.done": ("貝はもう鳴り終えた", "The shells have sung already"),
    "message.skyabyss.trial.t07.start": ("三つの栓で二つの海の水を動かし、金色の目盛りへ同時に合わせる (上 %s / 下 %s)。しばらく保つ。白い栓で最初へ戻せる", "Move the two seas with the three plugs and match the gold marks at once (A %s / B %s). Hold them. The white plug resets"),
    "message.skyabyss.trial.t07.moved": ("水位 上 %s・下 %s (目盛り 上 %s・下 %s)", "Levels A %s, B %s (marks A %s, B %s)"),
    "message.skyabyss.trial.t07.blocked": ("それ以上は動かない (上 %s・下 %s)", "It will not move further (A %s, B %s)"),
    "message.skyabyss.trial.t07.reset": ("最初の水位へ戻した (上 %s・下 %s)", "Back to the start (A %s, B %s)"),
    "message.skyabyss.trial.t07.hold": ("目盛りに合った。%s 秒、そのまま", "On the marks. Hold for %s s"),
    "message.skyabyss.trial.t07.solved": ("二つの海が合った。奥の戸が開く", "The seas agree. The far gate opens"),
    "message.skyabyss.trial.t10.start": ("三つの波を退ける。波のたびに潮が満ち、島の砂が洗われる。砂が先、固めた砂岩があと", "Beat three waves. The tide rises each time and washes the island away, sand first, sandstone later"),
    "message.skyabyss.trial.t10.wave": ("第 %s 波 / %s", "Wave %s / %s"),
    "message.skyabyss.trial.t10.collapse": ("潮が満ちる。あと %s 秒", "The tide rises in %s s"),
    "message.skyabyss.trial.t10.cleared": ("円環を制した。出口の橋が現れる", "The ring is yours. The exit bridge appears"),
    "message.skyabyss.trial.t10.reset": ("円環が元に戻る", "The ring resets"),
    "message.skyabyss.trial.t10.fell": ("深く沈んだ。控えへ戻る", "You sank deep. Back to the foyer"),
    "message.skyabyss.trial.t10.wet": ("水に浸かった。足場へ戻ろう", "You are in the water. Get back onto a foothold"),
    "trial.skyabyss.num.overlap": ("道の重なり", "Path overlap"),
    "trial.skyabyss.num.tide_speed": ("潮の速さ", "Tide speed"),
    "trial.skyabyss.num.chorus": ("合唱の長さ", "Chorus length"),
    "trial.skyabyss.num.mural": ("壁画の見え方", "Mural visibility"),
    "trial.skyabyss.num.pulls": ("目盛りまでの引き", "Pulls to the marks"),
    "trial.skyabyss.enemy.T10.0": ("磯の群れ", "Reef pack"),
    "trial.skyabyss.enemy.T10.1": ("泡吹きつき", "With bubble puffers"),
    "trial.skyabyss.hazard.eddy": ("渦", "Eddy"),
    "trial.skyabyss.hazard.swell": ("色のうねり", "Colour swell"),
    "trial.skyabyss.hazard.puffers": ("泡吹き", "Bubble puffers"),
    "trial.skyabyss.hazard.crash": ("高波", "Crash wave"),
    "trial.skyabyss.goal.no_swim": ("水に落ちずに", "Without falling in"),
    "trial.skyabyss.goal.no_murk": ("濁らせずに", "Without clouding the sea"),
    "trial.skyabyss.goal.no_reset": ("戻しの栓を使わずに", "Without the reset plug"),
    "trial.skyabyss.goal.no_wet": ("水に浸からずに", "Staying dry"),
    "checkpoint.skyabyss.tide_td0": ("試練の広間", "Trial hall"),
    "checkpoint.skyabyss.tide_t02a": ("入江の手前の岸", "Cove near shore"),
    "checkpoint.skyabyss.tide_t02m": ("入江の中島", "Cove islet"),
    "checkpoint.skyabyss.tide_t06a": ("貝の間の入口", "Shell hall entrance"),
    "checkpoint.skyabyss.tide_t07a": ("水槽の間の入口", "Tank hall entrance"),
    "checkpoint.skyabyss.tide_t10a": ("円環の控え", "Ring foyer"),
})
SIGNS = {
    "sign.skyabyss.tide_deck_guide": (["試練の広間", "板で三つを", "読み、引いた", "戸から入れ"], ["Trial hall.", "Read the board", "for your three.", "Rest by fire"]),
    "sign.skyabyss.tide_t02": (["潮の背渡り", "引き潮は砂州", "満ち潮は石", "止まるな"], ["Tide Crossing", "Sand at ebb", "Stone at flood", "Do not linger"]),
    "sign.skyabyss.tide_t10": (["満ち潮の円環", "波ごとに潮が", "満ち砂が先に", "洗われる"], ["Tide Ring", "Each wave the", "tide rises.", "Sand goes first"]),
    "sign.skyabyss.tide_t06": (["貝の合唱", "壁画の色の", "順に貝を叩け", "違えば濁る"], ["Shell Chorus", "Strike by the", "mural's colours", "A miss clouds it"]),
    "sign.skyabyss.tide_t07": (["二つの海", "栓で水を動かし", "金の目盛りへ", "白い栓で戻す"], ["Two Seas", "Move water with", "the plugs; match", "the gold marks"]),
}
for key, (ja, en) in SIGNS.items():
    for i in range(4):
        ENTRIES[key + "." + str(i + 1)] = (ja[i], en[i])


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
