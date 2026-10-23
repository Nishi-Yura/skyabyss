# -*- coding: utf-8 -*-
"""試練のプール P3 (docs/design/29) の雷翼の新作 5 本 (S04 羽根の吊り橋・S07 二枚の鏡の間・S08 気圧の弁・S11 避雷の護送・S12 羽守長の頂) の文を、
assets/skyabyss_trial/lang と assets/skyabyss_storm/lang の ja_jp / en_us に足す。何度流しても同じ (キーは上書き)。
使い方 (リポジトリ直下で): python tools/once/add_trial_p3_storm_lang.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRIAL = ROOT / "src/main/resources/assets/skyabyss_trial/lang"
STORM = ROOT / "src/main/resources/assets/skyabyss_storm/lang"

# key -> (ja, en)
TRIAL_ENTRIES = {
    # ---- セル
    "trial.skyabyss.cell.S04": ("羽根の吊り橋", "Feather Suspension Bridge"),
    "trial.skyabyss.cell.S04.desc": ("突風の前の羽毛を読み、柱の陰で風をやりすごして、手すりのない橋を渡る。",
                                     "Read the drifting feathers, shelter in a pillar's lee, and cross the railless bridge."),
    "trial.skyabyss.cell.S07": ("二枚の鏡の間", "Hall of Two Mirrors"),
    "trial.skyabyss.cell.S07.desc": ("鏡を裏返して光を窓へ、そして奥の部屋の受け羽へ届ける。同じ色の鏡はつながっている。",
                                     "Flip mirrors to send the beam through the window to the far catcher. Same-colored mirrors are linked."),
    "trial.skyabyss.cell.S08": ("気圧の弁", "Pressure Valves"),
    "trial.skyabyss.cell.S08.desc": ("弁を回して気嚢の圧を金の印に合わせ、しばらく保つ。", "Turn valves to set the sacs' pressure to the gold marks and hold it."),
    "trial.skyabyss.cell.S11": ("避雷の護送", "Lightning-Rod Escort"),
    "trial.skyabyss.cell.S11.desc": ("避雷の籠を道に沿って塔へ運ぶ。雷は籠に落ちる。離れて守り、敵を雷へ誘う。",
                                     "Guide the rod cage along its path to the tower. Lightning strikes the cage: keep clear and lure foes into it."),
    "trial.skyabyss.cell.S12": ("羽守長の頂", "Warden Chief's Summit"),
    "trial.skyabyss.cell.S12.desc": ("動く羽の足場の上で羽守長と戦う。羽の嵐の予告を見て、芯か別の扇へ移る。",
                                     "Fight the Warden Chief on drifting feather pads. Read the storm warning and shift to the core or another pad."),
    "trial.skyabyss.s12.chief": ("羽守長", "Warden Chief"),
    # ---- 一行の知らせ
    "message.skyabyss.trial.s04.start": ("突風の前に羽毛が風下へ流れ、柱の陰の床が白く光る。光った陰に入れば風は当たらない。前半の風はいつも北から",
                                          "Before a gust, feathers stream downwind and the lee floor of each pillar glows white. Stand in the glow to be safe. The first bridge's wind always blows from the north"),
    "message.skyabyss.trial.s04.twist": ("ここから風の向きが毎回変わる。羽毛の流れる向きと、光る陰を見よう", "From here the wind changes each time. Watch the feathers and the glowing lee"),
    "message.skyabyss.trial.s04.fell": ("橋から落ちた。休み場へ戻る", "You fell off the bridge. Back to the rest point"),
    "message.skyabyss.trial.s04.crossed": ("向こうの台に着いた。石を鳴らそう", "You reached the far deck. Ring the stone"),
    "message.skyabyss.trial.s07.start": ("鏡を右クリックで裏返す。光は鏡で曲がる。部屋 1 の鏡を直して、光を中ほどの窓へ当てよう",
                                          "Right-click a mirror to flip it. The beam bends at mirrors. Fix room 1 so the beam strikes the window"),
    "message.skyabyss.trial.s07.window": ("窓が開いた。同じ色の台の鏡はつながっている。片方を裏返すと、もう片方も裏返る",
                                           "The window opens. Mirrors on same-colored pedestals are linked: flipping one flips the other"),
    "message.skyabyss.trial.s07.hold": ("受け羽に光が届いた。%s 秒、保とう", "The catcher is lit. Hold it for %s s"),
    "message.skyabyss.trial.s07.cleared": ("光が受け羽に届いた。石を鳴らそう", "The beam reached the catcher. Ring the stone"),
    "message.skyabyss.trial.s07.fell": ("広間から落ちた。休み場へ戻る", "You fell. Back to the rest point"),
    "message.skyabyss.trial.s08.start": ("弁を右クリックで回すと、壁の板のとおりに気嚢の圧が動く (緑 +1・赤 −1)。しゃがんで回すと逆回し。金の印に合わせて保とう",
                                          "Right-click a valve: sac pressure moves as its wall panel shows (green +1, red -1). Sneak to turn back. Match the gold marks and hold"),
    "message.skyabyss.trial.s08.locked": ("この弁はまだ固い", "This valve is still stuck"),
    "message.skyabyss.trial.s08.stuck": ("それ以上は回らない (圧が 0〜5 を出る)", "It won't turn further (pressure would leave 0-5)"),
    "message.skyabyss.trial.s08.hold": ("圧が合った。%s 秒、保とう", "Pressure matched. Hold for %s s"),
    "message.skyabyss.trial.s08.hold_left": ("あと %s 秒", "%s s to go"),
    "message.skyabyss.trial.s08.stage2": ("気嚢 C・D が目覚めた。三つ目の弁も回る。弁が C・D にも効く", "Sacs C and D wake. The third valve turns, and valves now affect C and D too"),
    "message.skyabyss.trial.s08.cleared": ("圧が揃った。石を鳴らそう", "Pressure set. Ring the stone"),
    "message.skyabyss.trial.s08.done": ("弁はもう動かさなくてよい", "The valves are done"),
    "message.skyabyss.trial.s08.leak": ("気嚢 %s が漏れている。直そう", "Sac %s is leaking. Fix it"),
    "message.skyabyss.trial.s11.start": ("籠は 7 マス以内に人がいるあいだ道を進む。雷は籠に落ちる。籠から 3 マス以上離れて守ろう",
                                          "The cage follows its path while someone is within 7 blocks. Lightning strikes the cage: stay 3+ blocks away and guard it"),
    "message.skyabyss.trial.s11.rule": ("雷は籠に落ちる。籠のまわりの輪から出よう", "Lightning will strike the cage. Get out of the ring around it"),
    "message.skyabyss.trial.s11.raid": ("襲撃だ (%s / %s)。敵を籠のそばへ誘い込めば雷に当たる", "Raid (%s / %s). Lure foes next to the cage so the lightning hits them"),
    "message.skyabyss.trial.s11.pinned": ("籠が囲まれている。丈夫さ %s%%", "The cage is surrounded. Integrity %s%%"),
    "message.skyabyss.trial.s11.broken": ("籠が壊れた。最後に襲撃が来た所からやり直す", "The cage broke. Restarting from the last raid"),
    "message.skyabyss.trial.s11.arrived": ("籠が塔に着いた。石を鳴らそう", "The cage reached the tower. Ring the stone"),
    "message.skyabyss.trial.s11.fell": ("甲板から落ちた。休み場へ戻る", "You fell off the deck. Back to the rest point"),
    "message.skyabyss.trial.s12.start": ("足場は半分ずつ運び去られ、また戻る。黄色く瞬く扇は消える合図。橙の扇には羽の嵐が降る。芯は嵐が来ない",
                                          "Pads are carried away half at a time and return. Yellow flicker = leaving soon. Orange pads get a feather storm. The core is safe from storms"),
    "message.skyabyss.trial.s12.chief": ("羽守長が現れた", "The Warden Chief appears"),
    "message.skyabyss.trial.s12.support": ("羽守長が取り巻きを呼んだ", "The Warden Chief calls its retinue"),
    "message.skyabyss.trial.s12.storm": ("羽の嵐が降る。橙の扇から離れよう", "A feather storm is coming. Leave the orange pad"),
    "message.skyabyss.trial.s12.cleared": ("羽守長を倒した。足場が戻り、出口の橋が開く。石を鳴らそう",
                                            "The Warden Chief falls. The pads return and the exit bridge opens. Ring the stone"),
    "message.skyabyss.trial.s12.fell": ("頂から落ちた。控えへ戻る", "You fell off the summit. Back to the foyer"),
    "message.skyabyss.trial.s12.peaceful": ("ピースフルでは戦いは始まらない", "The fight does not begin on Peaceful"),
    "message.skyabyss.trial.s12.reset": ("頂に誰もいなくなった。最初からやり直す", "Nobody is left on the summit. Starting over"),
    # ---- 修飾の口の名前
    "trial.skyabyss.num.gust_gap": ("突風の間隔", "Gust interval"),
    "trial.skyabyss.num.gust_blow": ("風の吹く長さ", "Gust length"),
    "trial.skyabyss.num.mirror_flips": ("最短の裏返し", "Fewest flips"),
    "trial.skyabyss.num.beam_hold": ("光を保つ長さ", "Beam hold"),
    "trial.skyabyss.num.valve_turns": ("目盛りまでの回し", "Turns to the marks"),
    "trial.skyabyss.num.valve_hold": ("圧を保つ長さ", "Pressure hold"),
    "trial.skyabyss.num.cage_speed": ("籠の速さ", "Cage speed"),
    "trial.skyabyss.num.pad_swap": ("足場の入れ替え", "Pad swap"),
    "trial.skyabyss.num.feather_storm": ("羽の嵐の間隔", "Feather storm interval"),
    "trial.skyabyss.hazard.bellows_leak": ("気嚢の漏れ", "Leaking sacs"),
    "trial.skyabyss.hazard.double_bolt": ("二連の雷", "Double bolts"),
    "trial.skyabyss.goal.few_flips": ("裏返しを少なく", "Few flips"),
    "trial.skyabyss.goal.few_turns": ("回しを少なく", "Few turns"),
    "trial.skyabyss.goal.cage_whole": ("籠を無傷で", "Cage unscathed"),
    "trial.skyabyss.enemy.S11.0": ("雷蛾の群れ", "Moth swarm"),
    "trial.skyabyss.enemy.S11.1": ("羽守と雷蛾", "Wardens and moths"),
    "trial.skyabyss.enemy.S12.0": ("羽守長と雷蛾", "Chief and moths"),
    "trial.skyabyss.enemy.S12.1": ("羽守長と羽守", "Chief and wardens"),
}

STORM_ENTRIES = {
    # ---- 休み場の名前
    "checkpoint.skyabyss.storm_yard2": ("第二の渡り場", "Second Landing"),
    "checkpoint.skyabyss.storm_s04a": ("羽根の吊り橋 始めの台", "Feather Bridge: start deck"),
    "checkpoint.skyabyss.storm_s04m": ("羽根の吊り橋 中ほどの台", "Feather Bridge: middle deck"),
    "checkpoint.skyabyss.storm_s07a": ("二枚の鏡の間 入口", "Two Mirrors: entrance"),
    "checkpoint.skyabyss.storm_s08a": ("気圧の弁 広間", "Pressure Valves: hall"),
    "checkpoint.skyabyss.storm_s11a": ("避雷の護送 入口の甲板", "Rod Escort: entry deck"),
    "checkpoint.skyabyss.storm_s12a": ("羽守長の頂 控え", "Chief's Summit: foyer"),
    # ---- 部屋の入口の一行
    "message.skyabyss.storm.room.d2_bridge": ("第二の橋: 第一の渡り場の南から、さらに南の空へ。もう一つの渡り場に五つの戸がある",
                                              "Second Bridge: south of the first Landing, out over the sky. Five more doors wait at the second Landing"),
    "message.skyabyss.storm.room.d2_yard": ("第二の渡り場: 休み場。引かれた戸だけが開く。色の線が戸へ導く", "Second Landing: a rest point. Only drawn doors open. Colored lines lead to the doors"),
    "message.skyabyss.storm.room.d_s04": ("羽根の吊り橋: 突風の前の羽毛を読み、白く光る陰に入って風をやりすごす", "Feather Bridge: read the feathers before a gust and wait in the glowing lee"),
    "message.skyabyss.storm.room.d_s08": ("気圧の弁: 弁を回して気嚢の圧を金の印に合わせる。壁の板の緑は +1、赤は −1", "Pressure Valves: turn valves to set sac pressure to the gold marks. Green panel = +1, red = -1"),
    "message.skyabyss.storm.room.d_s07": ("二枚の鏡の間: 鏡を裏返して光を窓へ。同じ色の台の鏡はつながっている", "Two Mirrors: flip mirrors to send the beam to the window. Same-colored pedestals are linked"),
    "message.skyabyss.storm.room.d_s12": ("羽守長の頂: 足場は半分ずつ運ばれる。黄は消える合図、橙は羽の嵐。芯は安全", "Chief's Summit: pads leave half at a time. Yellow = leaving, orange = feather storm. The core is safe"),
    "message.skyabyss.storm.room.d_s11": ("避雷の護送: 籠は近くに人がいるあいだ進む。雷は籠に落ちる。離れて守る", "Rod Escort: the cage moves while someone is near. Lightning strikes it. Guard from a distance"),
    # ---- 目的の帯
    "objective.skyabyss.trial_storm_deck_bridge2": ("南の橋を渡って、第二の渡り場へ行こう", "Cross the south bridge"),
    "objective.skyabyss.trial_storm_deck_s04": ("白く光る陰を探して、柱から柱へ渡ろう", "Cross, sheltering at pillars"),
    "objective.skyabyss.trial_storm_deck_s07_a": ("鏡を裏返して、光を窓へ当てよう", "Flip mirrors, light the window"),
    "objective.skyabyss.trial_storm_deck_s07_b": ("奥の部屋の鏡を直して、光を東の受け羽へ送ろう", "Aim the beam at the catcher"),
    "objective.skyabyss.trial_storm_deck_s08": ("弁を回して、気嚢の圧を金の印に合わせよう", "Turn the valves to match the gold marks"),
    "objective.skyabyss.trial_storm_deck_s11": ("籠から離れて見守りながら、道を進もう", "Follow the cage from a safe distance"),
    "objective.skyabyss.trial_storm_deck_s12": ("頂の真ん中へ進もう", "Advance to the middle of the summit"),
    "objective.skyabyss.trial_storm_deck_s12_fight": ("羽守長を倒そう", "Defeat the Warden Chief"),
}


# 碑 (4 行。日本語は 1 行 8 文字まで)
def sign(key, ja, en):
    out = {}
    for i in range(4):
        out["sign.skyabyss.%s.%d" % (key, i + 1)] = (ja[i], en[i])
    return out


SIGNS = {}
SIGNS.update(sign("storm_d2_bridge", ["第二の橋", "この先の空に", "もう一つの", "渡り場がある"], ["Second Bridge", "Another", "landing hangs", "in the sky"]))
SIGNS.update(sign("storm_d2_yard", ["第二の渡り場", "引かれた戸だけ", "開いている", "色の線を追う"], ["Second Landing", "Only drawn", "doors open.", "Follow lines"]))
SIGNS.update(sign("storm_d_s04", ["羽根の吊り橋", "手すりはない", "突風が横から", "吹く"], ["Feather Bridge", "No railings.", "Gusts blow", "from the side."]))
SIGNS.update(sign("storm_d_s04_start", ["羽毛が流れたら", "突風の合図", "白く光る床は", "風が来ない"], ["Feathers drift:", "a gust comes.", "A white glow", "is out of wind"]))
SIGNS.update(sign("storm_d_s04_mid", ["ここから風の", "向きが変わる", "羽毛の向きと", "光る側を見る"], ["From here the", "wind turns.", "Watch feathers", "and the glow."]))
SIGNS.update(sign("storm_d_s08", ["気圧の弁", "弁を回すと", "気嚢の圧が動く", "金の印に合わせる"], ["Pressure Valves", "Turning a valve", "moves the sacs.", "Match the gold."]))
SIGNS.update(sign("storm_d_s08_start", ["板の緑は +1", "赤は −1", "しゃがむと", "逆回し"], ["Green = +1", "Red = -1", "Sneak to", "turn back."]))
SIGNS.update(sign("storm_d_s08_mid", ["合わせたら", "C・Dが目覚め", "弁が三つになり", "効きも変わる"], ["When matched,", "C and D wake,", "a third valve", "joins in."]))
SIGNS.update(sign("storm_d_s07", ["二枚の鏡の間", "鏡を右クリック", "光は鏡で曲がる", "窓へ当てる"], ["Two Mirrors", "Right-click a", "mirror to flip.", "Hit the window."]))
SIGNS.update(sign("storm_d_s07_start", ["光が窓に届くと", "窓が開く", "部屋 1 の鏡を", "直そう"], ["When the beam", "hits the window", "it opens. Fix", "room 1 first."]))
SIGNS.update(sign("storm_d_s07_mid", ["同じ色の台の", "鏡はつながる", "片方を回すと", "もう片方も回る"], ["Same-colored", "mirrors link:", "flip one and", "the twin flips."]))
SIGNS.update(sign("storm_d_s12", ["羽守長の頂", "足場は半分ずつ", "運び去られる", "黄の瞬きが合図"], ["Chief's Summit", "Pads leave half", "at a time.", "Yellow = soon."]))
SIGNS.update(sign("storm_d_s12_start", ["芯と筋は", "いつもある", "羽の嵐は", "芯に来ない"], ["Core and spokes", "always stay.", "Feather storms", "skip the core."]))
SIGNS.update(sign("storm_d_s12_arena", ["橙の扇には", "羽の嵐が降る", "別の扇か芯へ", "移ろう"], ["Orange pads", "get a storm.", "Move to another", "pad or the core."]))
SIGNS.update(sign("storm_d_s11", ["避雷の護送", "籠は近くの人に", "ついて進む", "雷は籠に落ちる"], ["Rod Escort", "The cage follows", "those nearby.", "Lightning hits it."]))
SIGNS.update(sign("storm_d_s11_start", ["籠から3マス", "離れて守る", "7マス以内に", "いれば進む"], ["Stay 3+ away", "from the cage,", "within 7 and", "it moves on."]))
SIGNS.update(sign("storm_d_s11_mid", ["襲撃が来る", "敵を籠のそばへ", "誘うと雷が", "敵に当たる"], ["Raiders come.", "Lure them near", "the cage: the", "bolt hits them."]))
STORM_ENTRIES.update(SIGNS)


def merge(folder, entries):
    for lang, idx in (("ja_jp", 0), ("en_us", 1)):
        path = folder / (lang + ".json")
        data = json.loads(path.read_text(encoding="utf-8"))
        for k, v in entries.items():
            data[k] = v[idx]
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    merge(TRIAL, TRIAL_ENTRIES)
    merge(STORM, STORM_ENTRIES)
    print("trial", len(TRIAL_ENTRIES), "storm", len(STORM_ENTRIES))
