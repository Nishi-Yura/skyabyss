"""巨神の子 (docs/design/28) の lang を書く。使い方: リポジトリ直下で  python tools/add_young_lang.py

- assets/skyabyss_young/lang/{ja_jp,en_us}.json を (再) 生成する
- assets/skyabyss_travel/lang の呼び笛 4 本の説明・出会いの文・海図の題を子の仕様に書き換え、成体を呼ぶ旅の古い文を取り除く
  (取り除くのは、コード・データのどこからも参照されなくなった鍵だけ。参照は src/main の java と data の文字列で調べる)
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
TITANS = ["stone_whale", "tide_shell", "thunder_wing", "ember"]

JA_NAME = {"stone_whale": "岩鯨", "tide_shell": "潮殻", "thunder_wing": "雷翼", "ember": "余燼"}
EN_NAME = {"stone_whale": "stone whale", "tide_shell": "tide shell", "thunder_wing": "thunder wing", "ember": "ember"}
JA_YOUNG = {"stone_whale": "岩鯨の仔", "tide_shell": "潮殻の稚貝", "thunder_wing": "雷翼の雛", "ember": "余燼の仔"}
EN_YOUNG = {"stone_whale": "Stone Whale Calf", "tide_shell": "Tide Shell Hatchling", "thunder_wing": "Thunder Wing Fledgling", "ember": "Ember Whelp"}
MODEL = {"stone_whale": "young_stone_whale", "tide_shell": "young_tide_shell", "thunder_wing": "young_thunder_wing", "ember": "young_ember"}

ja = {}
en = {}


def both(key, j, e):
    ja[key] = j
    en[key] = e


# ---- 子の名前
for t in TITANS:
    both("entity.skyabyss." + MODEL[t], JA_YOUNG[t], EN_YOUNG[t])

# ---- 呼ぶ・乗る・降りる (巨神ごとの言い回し)
CALLED = {
    "stone_whale": ("笛の歌に、岩鯨の仔が応えた。足もとに、小さな石の鯨が浮かび上がる。", "The calf answers the whale's song. A small stone whale rises at your feet."),
    "tide_shell": ("貝の音に、潮殻の稚貝が応えた。水の上に、小さな殻がゆっくりと浮かぶ。", "The hatchling answers the shell's call. A small shell drifts up on the water."),
    "thunder_wing": ("羽の音に、雷翼の雛が応えた。風の中で、羽毛がほどけて形になる。", "The fledgling answers the feather's call. Down gathers out of the wind into a shape."),
    "ember": ("鈴の音に、余燼の仔が応えた。足もとの灰が、小さな獣の形に固まる。", "The whelp answers the bell. Ash at your feet sets into the shape of a small beast."),
}
SEATED_LEADER = {
    "stone_whale": ("岩鯨の仔の鞍に座った。海図で行き先を選ぶ（最大 6 人。仲間も右クリックか笛で乗れる）。閉じてしまったら、笛を吹くと開き直せる。",
                    "You sit in the calf's saddle. Pick a destination on the chart (up to 6 riders; party members board by right-click or by blowing their call). Blow your call to reopen the chart."),
    "tide_shell": ("潮殻の稚貝の殻の縁に座った。潮の海図で行き先を選ぶ（最大 6 人。仲間も右クリックか貝で乗れる）。閉じてしまったら、貝を吹くと開き直せる。",
                   "You sit on the hatchling's shell. Pick a destination on the tide chart (up to 6 riders; party members board by right-click or by blowing their conch). Blow your conch to reopen the chart."),
    "thunder_wing": ("雷翼の雛の背に座った。稜線の海図で行き先を選ぶ（最大 6 人。仲間も右クリックか羽で乗れる）。閉じてしまったら、羽を掲げると開き直せる。",
                     "You sit on the fledgling's back. Pick a destination on the ridge chart (up to 6 riders; party members board by right-click or by raising their quill). Raise your quill to reopen the chart."),
    "ember": ("余燼の仔の背の鞍に座った。灰の海図で行き先を選ぶ（最大 6 人。仲間も右クリックか鈴で乗れる）。閉じてしまったら、鈴を鳴らすと開き直せる。",
              "You sit on the whelp's saddle. Pick a destination on the ash chart (up to 6 riders; party members board by right-click or by ringing their bell). Ring your bell to reopen the chart."),
}
SEATED = {
    "stone_whale": ("岩鯨の仔の鞍に座った。行き先は %s が決める。", "You sit in the calf's saddle. %s decides the destination."),
    "tide_shell": ("潮殻の稚貝の殻に座った。行き先は %s が決める。", "You sit on the hatchling's shell. %s decides the destination."),
    "thunder_wing": ("雷翼の雛の背に座った。行き先は %s が決める。", "You sit on the fledgling's back. %s decides the destination."),
    "ember": ("余燼の仔の背に座った。行き先は %s が決める。", "You sit on the whelp's back. %s decides the destination."),
}
DEPART = {
    "stone_whale": ("岩鯨の仔が雲の上へ舞い上がる。%s へ。", "The calf rises above the clouds, bound for %s."),
    "tide_shell": ("潮殻の稚貝が水を切って滑りだす。%s へ。", "The hatchling slides off across the water, bound for %s."),
    "thunder_wing": ("雷翼の雛が翼を広げ、稜線すれすれを飛びはじめる。%s へ。", "The fledgling spreads its wings and skims the ridgelines, bound for %s."),
    "ember": ("余燼の仔が地を蹴って駆けだす。%s へ。", "The whelp kicks off and runs, bound for %s."),
}
VEIL = {
    "stone_whale": ("岩鯨の仔が雲の海へ潜る。白い闇の中で、長い時が流れた気がした。", "The calf dives into the sea of clouds. In the white dark, it felt like a long while passed."),
    "tide_shell": ("稚貝が深い潮の流れに身を沈める。青い闇の中で、遠い海の歌が聞こえた。", "The hatchling sinks into a deep current. In the blue dark, you heard a distant sea song."),
    "thunder_wing": ("雛が雷雲の底へ潜る。白い閃光の中で、長い時が流れた気がした。", "The fledgling dives under the thunderhead. In the white flashes, it felt like a long while passed."),
    "ember": ("仔が熱い灰の流れへ潜る。赤い闇の中で、長い時が流れた気がした。", "The whelp drops into a stream of hot ash. In the red dark, it felt like a long while passed."),
}
ARRIVED = {
    "stone_whale": ("岩鯨の仔は %s にあなたたちを降ろし、歌に溶けていく。", "The calf sets you down at %s and dissolves into song."),
    "tide_shell": ("稚貝は %s の近くにあなたたちを降ろし、潮に溶けていく。", "The hatchling sets you down near %s and melts into the tide."),
    "thunder_wing": ("雛は %s にあなたたちを降ろし、風に溶けていく。", "The fledgling sets you down at %s and fades into the wind."),
    "ember": ("仔は %s にあなたたちを降ろし、灰に溶けていく。", "The whelp sets you down at %s and crumbles back into ash."),
}
RECOGNIZED = {
    "stone_whale": ("胸の奥で、岩鯨の歌がこだました。あの巨神は、もうあなたを覚えている。", "The stone whale's song echoed deep in your chest. The titan remembers you now."),
    "tide_shell": ("胸の奥で、潮の満ちる音がした。あの巨神は、もうあなたを覚えている。", "You heard the tide rising deep in your chest. The titan remembers you now."),
    "thunder_wing": ("胸の奥で、遠い雷が鳴った。あの巨神は、もうあなたを覚えている。", "Far thunder rolled deep in your chest. The titan remembers you now."),
    "ember": ("胸の奥で、燠が小さくはぜた。あの巨神は、もうあなたを覚えている。", "An ember popped softly deep in your chest. The titan remembers you now."),
}
for t in TITANS:
    both("message.skyabyss.young.%s.called" % t, *CALLED[t])
    both("message.skyabyss.young.%s.seated_leader" % t, *SEATED_LEADER[t])
    both("message.skyabyss.young.%s.seated" % t, *SEATED[t])
    both("message.skyabyss.young.%s.depart" % t, *DEPART[t])
    both("message.skyabyss.young.%s.veil" % t, *VEIL[t])
    both("message.skyabyss.young.%s.arrived" % t, *ARRIVED[t])
    both("message.skyabyss.young.recognized." + t, *RECOGNIZED[t])
both("message.skyabyss.young.recognized_hint",
     "呼び笛を手に入れた。吹くと、その巨神の「子」が足もとに現れる。仲間（最大 6 人）と一緒に乗って、行き先まで運んでもらえる。巨神そのものは動かない。",
     "You obtained a call. Blow it and the titan's young one appears at your feet; you and your party (up to 6) can ride it to a destination. The titan itself does not move.")

# ---- 共通の文
both("message.skyabyss.young.not_now", "今は行き先を決められない。", "You cannot choose a destination right now.")
both("message.skyabyss.young.chosen", "行き先は %s。仲間が乗るのを少し待って、出発する。", "Destination: %s. We leave once your party is aboard.")
both("message.skyabyss.young.cancelled", "持ち主が降りた。行き先は決め直しになる。", "The owner got off. The destination must be chosen again.")
both("message.skyabyss.young.countdown", "あと %s 秒で出発", "Leaving in %s s")
both("message.skyabyss.young.countdown_wait", "あと %s 秒で出発。まだ乗っていない仲間が %s 人いる", "Leaving in %s s. %s party member(s) not aboard yet")
both("message.skyabyss.young.mate_calling", "%s の子がもうすぐ出発する（あと %s 秒）。右クリックか笛で乗る", "%s's young one leaves soon (%s s). Right-click it or blow your call to board")
both("message.skyabyss.young.mate_boarded", "%s が乗った（%s 人）", "%s boarded (%s aboard)")
both("message.skyabyss.young.not_leader", "行き先を決めるのは %s だ。", "%s decides the destination.")
both("message.skyabyss.young.no_dismount", "着くまで降りられない。", "You cannot get off until we arrive.")
both("message.skyabyss.young.have_one", "子はもう呼んである。近くへ行って右クリックか笛で乗る。", "Your young one is already out. Go to it and right-click or blow your call.")
both("message.skyabyss.young.not_yours", "これは %s の子だ。同じパーティの人だけが乗れる。", "This is %s's young one. Only party members can ride.")
both("message.skyabyss.young.full", "席がもういっぱいだ（最大 6 人）。", "All six seats are taken.")
both("message.skyabyss.young.too_late", "子はもう出発している。", "The young one has already left.")
both("message.skyabyss.young.arrived_titan", "%s のもとに着いた。巨神は動かない。降りて、足もとの光の輪へ。", "We reached %s. The titan does not move. Step down into the ring of light.")
both("message.skyabyss.young.arrived_titan_hint", "光の輪の中に立つと、背へ入れる。見えるのは同じパーティの人だけ。", "Stand in the ring of light to enter its back. Only your party can see it.")
both("message.skyabyss.young.refuse.no_sea", "%s が滑り出せる水が近くにない。海辺（水際から 24 ブロック以内）で吹く。", "No water nearby for %s to slide out on. Blow it near the shore (within 24 blocks of water).")
both("message.skyabyss.young.refuse.no_room", "%s の子が現れる広さがない。もう少し開けた所で吹く。", "No room for %s's young one to appear. Blow it in a more open spot.")
both("message.skyabyss.young.dissolved.gone", "呼んだ人がいなくなり、子は歌に戻っていった。", "The one who called it is gone; the young one returned to song.")
both("message.skyabyss.young.dissolved.faded", "子は待ちくたびれて、歌に戻っていった。", "The young one grew tired of waiting and returned to song.")
both("message.skyabyss.young.dissolved.blocked", "道がふさがれて、子は歌に溶けた。あなたは地面へ降ろされた。", "The way was blocked and the young one dissolved. You were set down on the ground.")

# ---- 海図 (選べない行き先の理由)
both("gui.skyabyss.young.lock.sleeping", "眠っている（目覚めを待つ）", "Asleep (wait for it to wake)")
both("gui.skyabyss.young.lock.absent", "まだ姿を見せていない", "Has not shown itself yet")
both("gui.skyabyss.young.lock.too_far", "この子には遠すぎる", "Too far for this young one")
both("gui.skyabyss.young.lock.no_land", "海の上には降りられない", "Cannot land on open sea")
both("gui.skyabyss.young.lock.open_sea", "広い海は渡れない", "Cannot cross the open sea")
both("gui.skyabyss.young.lock.water", "水の上は渡れない", "Cannot cross water")
both("gui.skyabyss.young.lock.no_sea", "近くに海がない", "No sea nearby")
both("gui.skyabyss.travel.title.stone_whale", "空の海図", "Sky Chart")
both("gui.skyabyss.travel.title.thunder_wing", "稜線の海図", "Ridge Chart")
both("gui.skyabyss.travel.title.ember", "灰の海図", "Ash Chart")

# ---- 呼び笛の説明 (skyabyss_travel の既存キーを書き換える)
ITEM = {}
USE_JA = "吹く → 子が足もとに現れる → 乗る → 海図で行き先を選ぶ。仲間（最大 6 人）も乗れる。成体の巨神は動かない"
USE_EN = "Blow: the young one appears at your feet; board it and pick a destination on the chart. Party members (up to 6) can ride too. The adult titan never moves"
ITEM["stone_whale"] = (
    "鯨の骨をくり抜いた笛。吹くと、岩鯨の仔が雲の向こうから応える", "A hollowed whalebone. When blown, a stone whale calf answers from beyond the clouds",
    "空を泳ぐ子。どこへでも運ぶ。はじまりの地・覚えた碑・出会った巨神のもとへ", "The calf swims the sky and can carry you anywhere: the starting land, remembered tablets, any titan you have met")
ITEM["tide_shell"] = (
    "珊瑚色の口をした巻貝。耳に当てると、遠い潮の満ち引きが聞こえる", "A coral-lipped conch. Held to the ear, it murmurs with a distant tide",
    "水際で吹く。海の上を滑る子。海辺から海辺へ運ぶ（陸は短く転がるだけ）", "Blow it at the water's edge. The hatchling glides over the sea from shore to shore (it only rolls briefly over land)")
ITEM["thunder_wing"] = (
    "雷の走る風切り羽。高く掲げると、嵐の中で小さな羽が応える", "A flight feather with lightning running down its vane. Held high, a small wing answers from the storm",
    "低く飛び、稜線を渡る子。陸の行き先だけ。広い海の上は渡らない", "The fledgling flies low along the ridges. Land destinations only; it will not cross the open sea")
ITEM["ember"] = (
    "灰をかぶった小さな鈴。鳴らすと、足もとの熱が身じろぎして形になる", "A small bell caked in ash. When rung, the warmth underfoot stirs and takes shape",
    "地を駆け、溶岩を渡る子。陸の道が続く所だけ。深い水は渡らない", "The whelp runs over land and wades lava. Only where land runs on; it will not cross deep water")
RULE_JA = "出会った巨神の笛だけが使える。眠っている巨神の子は応えない。昼夜・天気・潮に関わらない"
RULE_EN = "Only the call of a titan you have met works. A sleeping titan's young one does not answer. Day, night, weather and tide do not matter"

travel_ja = {}
travel_en = {}
for t in TITANS:
    nm = "item.skyabyss.%s_call" % ("stone_whale" if t == "stone_whale" else t)
    j_desc, e_desc, j_how, e_how = ITEM[t]
    travel_ja[nm + ".desc"] = j_desc
    travel_en[nm + ".desc"] = e_desc
    travel_ja[nm + ".use"] = USE_JA + "。" + j_how
    travel_en[nm + ".use"] = USE_EN + ". " + e_how
    travel_ja[nm + ".rule"] = RULE_JA
    travel_en[nm + ".rule"] = RULE_EN
travel_ja["message.skyabyss.titan_call.met_hint"] = "呼び笛は旅嚢から吹ける。出会った巨神の笛を吹くと、その子が足もとに現れて、仲間と一緒に行き先まで運んでくれる"
travel_en["message.skyabyss.titan_call.met_hint"] = "Your call can be used from the pouch. Blow a met titan's call and its young one appears at your feet and carries you and your party to a destination"
travel_ja["message.skyabyss.titan_call.met.stone_whale"] = "岩鯨の歌が、胸の奥でこだました。この笛で、岩鯨の仔を呼べる"
travel_en["message.skyabyss.titan_call.met.stone_whale"] = "The stone whale's song echoed deep in your chest. With this whistle you can call its calf"
travel_ja["message.skyabyss.titan_call.met.tide_shell"] = "潮のざわめきが、耳の奥に残った。この貝で、潮殻の稚貝を呼べる"
travel_en["message.skyabyss.titan_call.met.tide_shell"] = "The murmur of the tide stays in your ear. With this conch you can call its hatchling"
travel_ja["message.skyabyss.titan_call.met.thunder_wing"] = "風切り羽が一枚、嵐の中から舞い落ちた。これで、雷翼の雛を呼べる"
travel_en["message.skyabyss.titan_call.met.thunder_wing"] = "A flight feather drifted down out of the storm. With it you can call the thunder wing's fledgling"
travel_ja["message.skyabyss.titan_call.met.ember"] = "足もとの熱が、小さな鈴の形に固まった。これで、余燼の仔を呼べる"
travel_en["message.skyabyss.titan_call.met.ember"] = "The warmth underfoot set into the shape of a small bell. With it you can call the ember's whelp"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def code_text():
    """src/main の java と data の文字列 (参照の確認用)"""
    chunks = []
    for base in (os.path.join(ROOT, "src", "main", "java"), os.path.join(ROOT, "src", "main", "resources", "data")):
        for dp, _, files in os.walk(base):
            for fn in files:
                if fn.endswith((".java", ".json", ".mcfunction")):
                    try:
                        with open(os.path.join(dp, fn), encoding="utf-8") as f:
                            chunks.append(f.read())
                    except OSError:
                        pass
    return "\n".join(chunks)


def main():
    dump(os.path.join(ASSETS, "skyabyss_young", "lang", "ja_jp.json"), ja)
    dump(os.path.join(ASSETS, "skyabyss_young", "lang", "en_us.json"), en)
    src = code_text()
    for loc, upd in (("ja_jp", travel_ja), ("en_us", travel_en)):
        p = os.path.join(ASSETS, "skyabyss_travel", "lang", loc + ".json")
        data = load(p)
        for k, v in upd.items():
            data[k] = v
        removed = []
        for k in list(data):
            if k in upd:
                continue
            if k.startswith(("message.skyabyss.travel.", "title.skyabyss.travel.", "message.skyabyss.titan_call.")):
                dyn = k.startswith("message.skyabyss.travel.compass.")
                if '"%s"' % k not in src and not dyn and not re.search(re.escape(k.rsplit(".", 1)[0]) + r'\.?"\s*\+', src):
                    removed.append(k)
                    del data[k]
        dump(p, data)
        print(loc, "removed", len(removed))
        if loc == "ja_jp":
            print("\n".join(removed))


if __name__ == "__main__":
    main()
