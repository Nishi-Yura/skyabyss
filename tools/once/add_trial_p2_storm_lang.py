# -*- coding: utf-8 -*-
"""試練のプール P2 (docs/design/29) の雷翼の新作 4 本 (S02 気嚢の滑空・S03 雷柱の渡り・S09 雷乞いの巣・S10 羽守の見張り台) の文を、
assets/skyabyss_trial/lang と assets/skyabyss_storm/lang の ja_jp / en_us に足す。何度流しても同じ (キーは上書き)。
使い方 (リポジトリ直下で): python tools/once/add_trial_p2_storm_lang.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRIAL = ROOT / "src/main/resources/assets/skyabyss_trial/lang"
STORM = ROOT / "src/main/resources/assets/skyabyss_storm/lang"

# key -> (ja, en)
TRIAL_ENTRIES = {
    # ---- セル
    "trial.skyabyss.cell.S02": ("気嚢の滑空", "Air Sac Glide"),
    "trial.skyabyss.cell.S02.desc": ("膨らんだ気嚢の昇り風に乗り、滑空して次の気嚢へ渡る。", "Ride the updraft of an inflated sac and glide to the next one."),
    "trial.skyabyss.cell.S03": ("雷柱の渡り", "Thunder Pillar Crossing"),
    "trial.skyabyss.cell.S03.desc": ("橙に光る床の雷を避け、銅の柱を渡る。", "Cross copper pillars, dodging the lightning that strikes the orange tiles."),
    "trial.skyabyss.cell.S09": ("雷乞いの巣", "Caller's Nest"),
    "trial.skyabyss.cell.S09.desc": ("避雷の柱で雷の輪を受け、雷乞いを倒す。", "Catch lightning rings with the rods and defeat the callers."),
    "trial.skyabyss.cell.S10": ("羽守の見張り台", "Warden Watch"),
    "trial.skyabyss.cell.S10.desc": ("守りを倒して風の穴を目覚めさせ、台から台へ渡る。", "Defeat the guards to wake the vents and cross from post to post."),
    # ---- 一行の知らせ
    "message.skyabyss.trial.s02.start": ("気嚢は膨らんでいるあいだだけ昇り風を吹く。光る気嚢の真ん中に立って浮き、しゃがんで滑空して次の気嚢へ",
                                          "A sac blows an updraft only while inflated. Stand in the middle of a lit sac, rise, then sneak to glide to the next"),
    "message.skyabyss.trial.s02.feather": ("風切りの羽根を受け取った。空中でしゃがむと滑空する", "You received a gale feather. Sneak in the air to glide"),
    "message.skyabyss.trial.s02.fell": ("足を踏み外した。休み場へ戻る", "You fell. Back to the rest point"),
    "message.skyabyss.trial.s02.crossed": ("向こうの台に着いた。石を鳴らそう", "You reached the far deck. Ring the stone"),
    "message.skyabyss.trial.s03.start": ("橙に光った床には、あとで雷が落ちる。金の床は安全。雷は柱を順に走る",
                                          "Orange tiles get struck a moment later. Gold tiles are safe. Lightning runs from pillar to pillar"),
    "message.skyabyss.trial.s03.fell": ("柱から落ちた。休み場へ戻る", "You fell from a pillar. Back to the rest point"),
    "message.skyabyss.trial.s03.crossed": ("向こうの台に着いた。石を鳴らそう", "You reached the far deck. Ring the stone"),
    "message.skyabyss.trial.s09.start": ("柱のそばなら雷の輪は柱へ落ちる。柱は三度受けると放電する。雷乞いを倒そう",
                                          "Rings near a rod fall on the rod. A rod that caught three rings discharges. Defeat the callers"),
    "message.skyabyss.trial.s09.overload": ("柱が満ちた。まもなく放電する。離れよう", "A rod is full and about to discharge. Move away"),
    "message.skyabyss.trial.s09.fell": ("巣から落ちた。控えへ戻る", "You fell from the nest. Back to the foyer"),
    "message.skyabyss.trial.s09.occupied": ("ほかの一行が巣を使っている。控えで待とう", "Another party is using the nest. Wait in the foyer"),
    "message.skyabyss.trial.s09.peaceful": ("ピースフルでは戦いは始まらない", "The fight does not begin on Peaceful"),
    "message.skyabyss.trial.s09.wave": ("第 %s 波 / %s", "Wave %s / %s"),
    "message.skyabyss.trial.s09.wave_done": ("波を退けた。次の波に備えよう", "Wave cleared. Brace for the next"),
    "message.skyabyss.trial.s09.cleared": ("雷乞いを退けた。東の橋が開く。石を鳴らそう", "The callers are beaten. The east bridge opens. Ring the stone"),
    "message.skyabyss.trial.s10.start": ("見張り台の守りを倒すと、その台の風の穴が目覚める。穴で浮いて、滑空で次の台へ",
                                          "Beat a post's guards to wake its wind vent. Rise on the vent and glide to the next post"),
    "message.skyabyss.trial.s10.feather": ("風切りの羽根を受け取った。空中でしゃがむと滑空する", "You received a gale feather. Sneak in the air to glide"),
    "message.skyabyss.trial.s10.fell": ("台から落ちた。休み場へ戻る", "You fell from a post. Back to the rest point"),
    "message.skyabyss.trial.s10.guards": ("台 %s の守りが現れた", "The guards of post %s appear"),
    "message.skyabyss.trial.s10.quiet": ("この台に守りはいない。風の穴が目覚めた", "No guards here. The wind vent is awake"),
    "message.skyabyss.trial.s10.cleared_platform": ("守りを倒した。風の穴が目覚めた", "Guards beaten. The wind vent is awake"),
    "message.skyabyss.trial.s10.cleared": ("最後の台を制した。石を鳴らそう", "The last post is yours. Ring the stone"),
    "message.skyabyss.trial.weekly_stone_rung": ("この石は鳴らした", "This stone has already rung"),
    # ---- 修飾の口の名前
    "trial.skyabyss.num.sac_period": ("気嚢の周期", "Sac period"),
    "trial.skyabyss.num.sac_window": ("膨らむ長さ", "Inflation length"),
    "trial.skyabyss.num.bolt_tempo": ("雷の速さ", "Lightning tempo"),
    "trial.skyabyss.num.bolt_shapes": ("橙の形", "Orange shapes"),
    "trial.skyabyss.num.callers": ("雷乞いの数", "Callers"),
    "trial.skyabyss.num.nest_waves": ("波の数", "Waves"),
    "trial.skyabyss.num.wardens": ("羽守の数", "Wardens"),
    "trial.skyabyss.num.watch_posts": ("守りのいる台", "Guarded posts"),
    "trial.skyabyss.hazard.crosswind": ("横風", "Crosswind"),
    "trial.skyabyss.hazard.spark_moth": ("雷蛾", "Spark moth"),
    "trial.skyabyss.hazard.ground_arc": ("地を這う線", "Ground arcs"),
    "trial.skyabyss.hazard.overload": ("柱の過負荷", "Rod overload"),
    "trial.skyabyss.hazard.sky_bolt": ("空の雷", "Sky bolts"),
    "trial.skyabyss.enemy.S09.0": ("雷乞いと雷蛾", "Callers and moths"),
    "trial.skyabyss.enemy.S09.1": ("雷乞いと羽守", "Callers and wardens"),
    "trial.skyabyss.enemy.S10.0": ("羽守と雷蛾", "Wardens and moths"),
    "trial.skyabyss.enemy.S10.1": ("羽守と雷乞い", "Wardens and callers"),
}

STORM_ENTRIES = {
    # ---- 休み場の名前
    "checkpoint.skyabyss.storm_yard": ("試練の渡り場", "Trial Landing"),
    "checkpoint.skyabyss.storm_s02a": ("気嚢の滑空 始めの台", "Air Sac Glide: start deck"),
    "checkpoint.skyabyss.storm_s02m": ("気嚢の滑空 中ほどの台", "Air Sac Glide: middle deck"),
    "checkpoint.skyabyss.storm_s03a": ("雷柱の渡り 始めの台", "Pillar Crossing: start deck"),
    "checkpoint.skyabyss.storm_s03m": ("雷柱の渡り 休み場の台", "Pillar Crossing: rest deck"),
    "checkpoint.skyabyss.storm_s09a": ("雷乞いの巣 控え", "Caller's Nest: foyer"),
    "checkpoint.skyabyss.storm_s10a": ("羽守の見張り台 始めの台", "Warden Watch: start post"),
    "checkpoint.skyabyss.storm_s10m": ("羽守の見張り台 中ほどの台", "Warden Watch: middle post"),
    # ---- 部屋の入口の一行
    "message.skyabyss.storm.room.d_bridge": ("橋: 背の広場から尾の上の空へ。南の渡り場に四つの戸がある", "Bridge: from the back plaza out over the tail. Four doors wait at the Landing to the south"),
    "message.skyabyss.storm.room.d_yard": ("試練の渡り場: 休み場。引かれた戸だけが開く。色の線が戸へ導く (水色は風、金は雷)", "Trial Landing: a rest point. Only drawn doors open. Colored lines lead to them (light blue wind, gold thunder)"),
    "message.skyabyss.storm.room.d_s02": ("気嚢の滑空: 光る気嚢の真ん中に立つと昇り風。浮いたらしゃがんで滑空して、次の気嚢へ", "Air Sac Glide: stand in a lit sac for an updraft, then sneak to glide to the next sac"),
    "message.skyabyss.storm.room.d_s03": ("雷柱の渡り: 橙の床には雷が落ちる。金の床は安全。雷は柱を順に走る", "Pillar Crossing: orange tiles get struck, gold tiles are safe, and lightning runs pillar to pillar"),
    "message.skyabyss.storm.room.d_s10": ("羽守の見張り台: 守りを倒すと風の穴が目覚める。穴で浮いて、滑空で次の台へ", "Warden Watch: beating the guards wakes the wind vent. Rise on it and glide to the next post"),
    "message.skyabyss.storm.room.d_s09": ("雷乞いの巣: 柱の近くの雷の輪は柱が受ける。三度受けた柱は放電する", "Caller's Nest: rods catch nearby rings. A rod that caught three will discharge"),
    # ---- 目的の帯
    "objective.skyabyss.trial_storm_deck_hall": ("広間の南の出口から、試練の渡り場へ向かおう", "Leave the hall by its south exit"),
    "objective.skyabyss.trial_storm_deck_turret": ("尾の間の北西の螺旋を上って、背の広場へ出よう", "Climb the tail hall spiral to the plaza"),
    "objective.skyabyss.trial_storm_deck_bridge": ("背の広場の南の橋を渡って、試練の渡り場へ行こう", "Cross the south bridge of the plaza"),
    "objective.skyabyss.trial_storm_deck_gate": ("渡り場の、開いている戸から試練へ入ろう", "Enter through an open door"),
    "objective.skyabyss.trial_storm_deck_s02": ("光る気嚢に立って浮き、滑空で次の島へ渡ろう", "Rise on a lit sac, glide on"),
    "objective.skyabyss.trial_storm_deck_s03": ("橙の床を避けて、次の柱へ渡ろう", "Dodge the orange, cross the pillars"),
    "objective.skyabyss.trial_storm_deck_s09": ("巣の真ん中へ進もう", "Advance to the middle of the nest"),
    "objective.skyabyss.trial_storm_deck_s10_vent": ("目覚めた風の穴で浮いて、次の台へ滑空しよう", "Ride the vent to the next post"),
    "objective.skyabyss.trial_storm_deck_s10_guard": ("台の守りを全部倒そう", "Defeat all the guards of this post"),
    "objective.skyabyss.trial_storm_deck_stone": ("石を鳴らそう", "Ring the stone"),
}

# 碑 (4 行。日本語は 1 行 8 文字まで)
def sign(key, ja, en):
    out = {}
    for i in range(4):
        out["sign.skyabyss.%s.%d" % (key, i + 1)] = (ja[i], en[i])
    return out

SIGNS = {}
SIGNS.update(sign("storm_d_bridge", ["試練の渡り場", "この橋の先に", "四つの戸が", "空に浮かぶ"], ["Trial Landing", "Four doors", "hang in the", "open sky"]))
SIGNS.update(sign("storm_d_yard", ["渡り場", "引かれた戸だけ", "開いている", "色の線を追う"], ["The Landing", "Only drawn", "doors open", "Follow lines"]))
SIGNS.update(sign("storm_d_s02", ["気嚢の滑空", "風の渡り", "しゃがんで", "滑空する"], ["Air Sac Glide", "Wind crossing", "Hold sneak", "to glide"]))
SIGNS.update(sign("storm_d_s02_start", ["光る気嚢に立つ", "昇り風で浮く", "しゃがんで滑空", "次の気嚢へ"], ["Stand on a lit", "sac to rise.", "Sneak to glide", "to the next."]))
SIGNS.update(sign("storm_d_s02_mid", ["ここから先", "気嚢は小さく", "光は短い", "黄色は予告"], ["Beyond here", "sacs shrink", "and glow short.", "Yellow = soon"]))
SIGNS.update(sign("storm_d_s03", ["雷柱の渡り", "橙に光る床は", "雷が落ちる", "金の床は安全"], ["Thunder Pillars", "Orange tiles", "get struck.", "Gold is safe."]))
SIGNS.update(sign("storm_d_s03_start", ["雷は柱を", "前へ走る", "落ちたあとの", "柱は安全"], ["Lightning runs", "pillar to pillar.", "Cross behind", "the strike."]))
SIGNS.update(sign("storm_d_s03_mid", ["ここから雷は", "向こうから来る", "橙の形も広がる", "金の床へ"], ["From here it", "runs at you.", "Shapes widen:", "stand on gold."]))
SIGNS.update(sign("storm_d_s10", ["羽守の見張り台", "台ごとに守り", "倒すと風の穴", "が目覚める"], ["Warden Watch", "Posts have", "guards. Clear", "to wake vents."]))
SIGNS.update(sign("storm_d_s10_start", ["灰色の穴は", "眠っている", "守りを倒すと", "光って運ぶ"], ["A gray vent", "sleeps. Beat", "the guards to", "wake and ride."]))
SIGNS.update(sign("storm_d_s10_mid", ["後ろの台ほど", "守りは厚い", "雷蛾と雷乞い", "も加わる"], ["Later posts", "hold more", "guards, plus", "moths, callers"]))
SIGNS.update(sign("storm_d_s09", ["雷乞いの巣", "柱が雷を受ける", "三度で放電", "敵も巻き込め"], ["Caller's Nest", "Rods catch rings.", "Three hits and", "they discharge."]))
SIGNS.update(sign("storm_d_s09_start", ["柱のそばでは", "雷の輪は柱へ", "電球が三つで", "放電する"], ["Stand by a rod:", "rings go to it.", "Three bulbs and", "it blasts."]))
SIGNS.update(sign("storm_d_s09_arena", ["放電は敵にも", "大きく当たる", "満ちた柱へ", "敵を誘え"], ["The blast hurts", "foes far more.", "Lure them to", "a full rod."]))
STORM_ENTRIES.update(SIGNS)


def merge(folder, entries):
    for lang, idx in (("ja_jp", 0), ("en_us", 1)):
        path = folder / (lang + ".json")
        data = json.loads(path.read_text(encoding="utf-8"))
        for k, v in entries.items():
            data[k] = v[idx]
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    merge(TRIAL, TRIAL_ENTRIES)
    merge(STORM, STORM_ENTRIES)
    print("trial", len(TRIAL_ENTRIES), "storm", len(STORM_ENTRIES))
