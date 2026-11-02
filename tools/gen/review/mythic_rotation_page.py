# -*- coding: utf-8 -*-
"""神器の大きな能力・武器の数値の行・技の回し の案のページ (選んだものは db の choices) と、同じ中身の docs/review の md を書く"""
import html
import json
import os

ROOT = "C:/Users/nisimoto/Desktop/mod"
OUT_HTML = os.path.join(ROOT, "docs/review/mythic_rotation_1102/page.html")
OUT_MD = os.path.join(ROOT, "docs/review/mythic_rotation_1102/index.md")

POLICY = [
    {"id": "q340", "no": 340, "title": "神器の大きな能力（どこまで）",
     "why": "今の神器の固有効果は「数回に一度の追撃」や「数秒の上乗せ」で、Lv 30 のヒロイック（熔け顎の大剣: 3 度目ごとに 60% の追撃）と同じくらい。Wynncraft のミシックは、強い代わりに欠点もある大きな能力一つで、その品を軸にした型ができる。防具 6 式と装飾 4 は、武器が決まってから同じ形で見直す。",
     "options": [
         {"v": "A", "label": "神器の武器 18 本すべてを、大きな能力一つ（欠点つき）に作り替える", "sub": "終章の一本目 6・二本目 6・塔 6。一本目の技の書き換えはそのまま残す", "rec": True},
         {"v": "B", "label": "18 本を大きな能力にするが、欠点は付けない", "sub": "強さだけ上がる。神器どうしの選び分けは弱くなる"},
         {"v": "C", "label": "終章の一本目 6 本だけ作り替える", "sub": "二本目と塔の武器は今のまま"},
         {"v": "keep", "label": "今のまま"}]},
    {"id": "q341", "no": 341, "title": "武器の数値の行（Wynncraft の ID）",
     "why": "1 秒あたりの傷を階級ごとにそろえてあるので、同じ職の武器の差は速さ・固有効果一つ・ランダムな特性（4〜10%）だけ。Wynncraft の武器は「呪文 +20%・近接 −10%・速さ +8%」のように品ごとに決まった行を持ち、それが個性になる。",
     "options": [
         {"v": "A", "label": "品ごとに決まった数値の行を 2〜4 本。エピック以上は欠点の行も一つ", "sub": "行の種類: 近接の傷・技の傷・技の待ち・マナの戻り・マナ吸収・体力吸収・最大体力・移動の速さ・攻撃の速さ・防御。1 秒あたりの床とランダムな特性はそのまま", "rec": True},
         {"v": "B", "label": "1 秒あたりをそろえるのをやめ、重く遅い武器と軽く速い武器の差を大きくする", "sub": "一撃の重さで選ぶ形。数値の行は足さない"},
         {"v": "AB", "label": "A と B の両方"},
         {"v": "keep", "label": "今のまま（ランダムな特性だけ）"}]},
    {"id": "q342", "no": 342, "title": "技の回しの形",
     "why": "技 4 本はそれぞれ待ち 6〜22 秒で、技どうしのつながりがほとんど無く「空いた技から押す」形になりやすい。Wynncraft の型は「ある技で溜めて、別の技で放つ」ゲージを持ち、技の順番に意味が出る。",
     "options": [
         {"v": "A", "label": "型ごとに一つ「溜まるもの」（6 職 × 3 型 = 18 種）", "sub": "型の節を取ると出る。体力とマナの帯のそばに小さなゲージ。中身は下の 18 の案", "rec": True},
         {"v": "B", "label": "職ごとに一つ（6 種）", "sub": "作る量は少ない。型による違いは出ない"},
         {"v": "C", "label": "溜まるものは作らず、技どうしのつなぎだけ足す", "sub": "例: 技 A のあと 2 秒以内の技 B が +30%"},
         {"v": "keep", "label": "今のまま"}]},
    {"id": "q343", "no": 343, "title": "技の待ちとマナ",
     "why": "待ちが長いと、溜まるものを回す前に技が尽きる。Wynncraft はほぼ待ちがなく、マナで回す。主の大技のあとの 3 秒（与える傷 ×1.6）に技を重ねる遊びも、待ちが短いほど生きる。",
     "options": [
         {"v": "A", "label": "待ちを今の 6〜7 割に縮め、そのぶんマナを少し重くする", "sub": "打ち手の泉（殴るとマナ）やマナ吸収が生きる。主の強さは自己テストで測り直す", "rec": True},
         {"v": "B", "label": "待ちを今の半分ほどにする（Wynncraft により近い）", "sub": "マナの重さと主の体力を大きく直す"},
         {"v": "keep", "label": "今のまま（待ち 6〜22 秒）"}]},
]

JOBS = [("warrior", "戦士", "大剣"), ("smith", "鍛冶師", "大鎚"), ("mage", "魔法使い", "杖"),
        ("archer", "弓使い", "弓"), ("assassin", "暗殺者", "短剣"), ("shaman", "呪術師", "鈴杖")]

# 神器の武器 18 本。src: 出どころ、now: 今の固有効果、a/b: 案 (name, 中身, 欠点)
DIVINE = {
    "warrior": [
        ("divine_sky_sword", "天穹の剣", "終章・一本目（技の書き換え「天穹の手」つき）", "4 度目ごとに光の手（攻撃力の 60%、まわり 2）",
         ("天の手", "近接で当てるたびに背に天の手が一つ増え（最大 5）、巨神斬りを当てると全部が落ちる（一つにつき攻撃力の 80%、まわり 3）", "巨神斬りの待ち +2 秒"),
         ("天秤の剣", "体力が満ちているあいだ技の傷 +35%。傷を受けると 4 秒消える", "最大体力 −10%")),
        ("divine_warden_blade", "羽の番人の大剣", "終章・二本目", "傷を受けると番の構え 3 秒（次の一撃 +40%）",
         ("番人の誓い", "受けた傷の 40% を「誓い」として溜め（最大は最大体力の半分）、次に当てた技にまとめて足す", "受ける傷 +10%"),
         ("不動の番", "止まっているあいだ受ける傷 ×0.65、押されない", "動いているあいだ与える傷 −20%")),
        ("tower_balance_sword", "あいだの大剣", "あいだの塔", "体力が半分より上なら傷 +15%、半分以下なら与えた傷の 10% を体力に",
         ("天と淵の天秤", "体力が半分より上なら与える傷 +30%、半分以下なら与えた傷の 25% を体力に", "ほかから受ける癒し −50%"),
         ("淵落ち", "体力が 3 割を切ると 8 秒、与える傷 ×1.6（60 秒に一度）", "その 8 秒は受ける傷 ×1.3")),
    ],
    "smith": [
        ("divine_bell_hammer", "七つ鐘の大鎚", "終章・一本目（技の書き換え「七つ鐘の波」つき）", "3 度目ごとに鐘（まわり 3.5 に攻撃力の 30% と鈍り 1 秒）",
         ("七つ鐘", "当てるたびに鐘が一つ鳴り、七つ目で大鐘（まわり 6 に攻撃力の 300%。小さな敵は 2 秒気絶、大きな相手は鈍り）", "攻撃の速さ −15%"),
         ("残響", "自分の技の範囲 ×1.5", "技の待ち +20%")),
        ("divine_ash_hammer", "灰打ちの大鎚", "終章・二本目", "打った敵を 3 秒溶かす（受ける傷 +10%）",
         ("溶かし打ち", "打つたびに敵の守りが溶け、受ける傷 +6% が重なる（5 重ねで +30%。だれの攻撃にも乗る。4 秒ごとに一つ戻る）", "自分の防御 −15%"),
         ("消えない熾", "焼き入れの熾が切れない（ずっと炎の傷が乗る）", "マナの戻り −30%")),
        ("tower_pull_hammer", "引き合いの鎚", "あいだの塔", "4 度目ごとに ×1.5、まわり 5 の敵を引き寄せる",
         ("引力", "4 度目ごとに、まわり 8 の敵を足もとへ引き寄せて攻撃力の 200% で叩く（大きな相手は引かれず傷だけ）", "移動の速さ −10%"),
         ("重さ", "引き寄せた敵一体につき 5 秒 防御 +2（最大 +10）。今の 4 度目ごとの引き寄せはそのまま", "なし（今の効果に足す形）")),
    ],
    "mage": [
        ("divine_dawn_staff", "暁の灯杖", "終章・一本目（技の書き換え「暁の視線」つき）", "打った相手を 4 秒灯す（だれの傷も +12%）",
         ("命の灯", "マナが足りないときは体力で技が使える（マナ 1 につき最大体力の 1%）", "最大マナ −30%"),
         ("暁の一灯", "灯した敵へはだれの傷も +25%（今の倍）", "灯せるのは同時に一体だけ")),
        ("divine_eyelid_staff", "雲の瞼の杖", "終章・二本目", "凝視の糸 3 秒。糸のある敵が 6 より離れると光の柱（攻撃力の 80%）",
         ("凝視", "同じ敵を見続けると技の傷が上がる（1 秒ごとに +8%、最大 +40%）。目を離すと消える", "移動の速さ −10%"),
         ("遠見", "離れているほど技の傷が上がる（16 ブロックで最大 +35%）", "6 ブロック以内では技の傷 −25%")),
        ("tower_moon_staff", "落ちる月の杖", "あいだの塔", "月の印 6 秒。印の敵への 3 打目ごとに小さな月（まわり 3 に攻撃力の 120%）",
         ("新しい月", "技を 3 回当てるごとに、狙った所へ小さな月が落ちる（まわり 4 に攻撃力の 250%）", "技のマナ +20%"),
         ("満ち欠け", "20 秒ごとに満月と新月が替わる。満月は技の傷 +30%、新月はマナの戻り +60%", "新月のあいだ技の傷 −15%")),
    ],
    "archer": [
        ("divine_plume_bow", "巡礼の羽弓", "終章・一本目（技の書き換え「落ち羽の雨」つき）", "当たると 0.8 秒後に羽根が三本（当たった傷の 30% ずつ）",
         ("羽の嵐", "矢が当たるたびに背に羽根が一枚（最大 12）。技を使うと全部が狙った敵へ飛ぶ（一枚 攻撃力の 25%）", "ふつうの矢の傷 −15%"),
         ("風切り", "空中で放った矢 +40%", "地上で放った矢 −10%")),
        ("divine_mist_bow", "霞鳥の弓", "終章・二本目", "当たった所に 3 秒の霞（中の敵は遅く、受ける傷 +10%）",
         ("霞隠れ", "自分が霞の中にいるあいだ敵に見失われ、矢 +20%。霞は当たった所に 4 秒", "最大体力 −15%"),
         ("霞の檻", "霞の中の敵は 2 秒動けない（同じ敵は 10 秒に一度。大きな相手は鈍りだけ）。霞の広さ ×2", "霞の中の敵の受ける傷 +10% は無くなる")),
        ("tower_eye_bow", "側の目の弓", "あいだの塔", "同じ相手に続けて当てるたびに +6%（5 重ねまで）",
         ("凝視の矢", "同じ敵に当て続けると +10% ずつ（10 重ねで +100%）。別の敵に当てると全部消える", "雲穿ちが貫かなくなる"),
         ("三つ目の光", "3 本に 1 本、狙った敵へ光線が追う（攻撃力の 80%、壁を抜ける）", "なし（今の重ねは無くなる）")),
    ],
    "assassin": [
        ("divine_rewind_dagger", "巻き戻りの短剣", "終章・一本目（技の書き換え「巻き戻る踏み込み」つき）", "1.5 秒後に同じ相手がもう一度打たれる（35%）",
         ("巻き戻し", "技を使った 1.5 秒後、同じ技がもう一度出る（力は半分、マナは要らない）", "受ける傷 +15%"),
         ("時の傷", "与えた傷の 40% を 2 秒後にもう一度与える", "ほかから受ける癒し −30%")),
        ("divine_moth_dagger", "灯蛾の短剣", "終章・二本目", "打つと灯の粉。光った相手を背から刺すと ×1.5",
         ("灯の粉", "打つたびに粉が重なり（5 まで）、背から刺すと粉一つにつき +30% で弾ける", "正面からの傷 −20%"),
         ("蛾の群れ", "霧隠れのあいだ、まわり 4 の敵に蛾がまとわりつく（毎秒 攻撃力の 40%）", "霧隠れの待ち +3 秒")),
        ("tower_sigil_dagger", "追う印の短剣", "あいだの塔", "打つと 3 秒足が速くなり、その間の次の一撃 +35%",
         ("追う印", "最初に打った敵に印（離れても消えない）。印の敵へは傷 +40%。印の敵を倒すと技の待ちが全部戻る", "印のない敵への傷 −20%"),
         ("疾走の刃", "走っているあいだの最初の一撃 ×2（3 秒に一度）", "防御 −15%")),
    ],
    "shaman": [
        ("divine_promise_bell_staff", "約束の鈴杖", "終章・一本目（技の書き換え「約束の円」つき）", "まわり 5 の仲間に約束の守り 3 秒（受ける傷 ×0.85）",
         ("約束", "まわり 8 の仲間が受ける傷の 25% を代わりに受け、受けた分の半分をマナにする", "自分の与える傷 −20%"),
         ("四つの灯", "技を使うたびに灯が一つ（4 つまで）。灯一つにつき仲間の受ける傷 −4%、四つ目で仲間みなの体力を 1 割戻して灯が消える", "なし")),
        ("divine_keeper_bell_staff", "鐘守の鈴杖", "終章・二本目", "4 度目ごとに沈黙の鐘（まわり 5 の敵を 2 秒遅く、仲間の体力を少し戻す）",
         ("沈黙の鐘", "4 度目ごとの鐘で、まわり 6 の敵の技を 2 秒止める（主は止まらず、予告が 1 秒のびる）", "攻撃の速さ −20%"),
         ("弔いの鐘", "倒れた仲間がいるあいだ、自分の技の力 +40%、起こす速さ ×2", "なし")),
        ("tower_clap_bell_staff", "合掌の鈴杖", "あいだの塔", "打つたびに祈り 1。5 つで合掌（まわり 6 の仲間と自分の体力とマナを少し戻す）",
         ("合掌", "祈り 5 つで合掌: まわり 8 の仲間の体力を 3 割戻し、6 秒 受ける傷 −20%", "自分の近接の傷 −25%"),
         ("祈りの力", "祈りの数だけ技の力 +8%（5 つで +40%）。技を使うと祈りは消える", "合掌の癒しは無くなる")),
    ],
}

# 型ごとの溜まるもの (18)。(id, 型の名, 型の主な技, a, b)。a/b = (名前, 中身)
ARCH = {
    "warrior": [
        ("guard", "盾の守り手", "盾の踏み込み・岩呼び・盾殴り",
         ("岩の重み", "受けた傷と盾の技で重みが溜まる（100）。満ちると盾殴りが「岩砕き」になる（まわり 4 に攻撃力の 250%、小さな敵は 1.5 秒気絶）"),
         ("守りの誓い", "岩呼びで引いた敵一体につき 6 秒 防御 +2（最大 +10）。誓いが 5 つのとき、次の盾の踏み込みでまわりの仲間を 4 秒かばう（受ける傷 −30%）")),
        ("cleave", "巨神斬りの剣士", "巨神斬り・鯨骨の振り",
         ("研ぎ", "近接で当てるたびに研ぎ +1（最大 5）。巨神斬りが研ぎ一つにつき +20%（全部使う）"),
         ("巨神の型", "盾の踏み込み → 鯨骨の振り → 巨神斬りの順に 4 秒以内で出すと、巨神斬りが ×1.8")),
        ("sweep", "鯨尾の振り回し", "鯨尾薙ぎ・回し斬り",
         ("勢い", "回し斬り・鯨尾薙ぎで当てた敵の数だけ勢い（最大 10）。勢い一つにつき攻撃の速さ +3%・移動 +2%。2 秒止まると減っていく"),
         ("大回転", "勢いが 10 のとき、次の鯨尾薙ぎが三回転する（半径 +2）。勢いは全部使う")),
    ],
    "smith": [
        ("ember", "熾火の槌", "熾火の槌・炉の踏み",
         ("炉の熱", "技を使うたびに熱が上がる（100。戦っていないと冷める）。満ちると熾火の槌が「炉開き」になる（前に火の柱 3 本、攻撃力の 300%）。今の炉の記憶はこれに寄せる"),
         ("熾の連打", "炉の記憶の熾を近接で使うと、熾一つにつき技の待ちが 1 秒戻る")),
        ("temper", "焼き入れ", "焼き入れ",
         ("鍛え", "焼き入れした武器で当てるたびに鍛え +1。10 で焼き入れの時間が満ちて、6 秒 炎の傷 ×2"),
         ("重ね焼き", "焼き入れを仲間にかけると自分にも半分の熾。熾をかけた仲間一人につき自分の技の傷 +5%")),
        ("ash", "灰散らし", "灰散らし・火花散らし",
         ("灰の層", "灰かぶりの敵に技を当てると灰が積もる（5 まで）。火花散らしで積もった灰を一斉に燃やす（一つ 攻撃力の 60%）"),
         ("灰の帳", "まわりの灰かぶりの敵一体につき、自分の受ける傷 −4%（最大 −20%）")),
    ],
    "mage": [
        ("thunder", "雷", "雷の矢・星屑",
         ("帯電", "雷の矢を当てると敵が帯電する（3 重ね）。3 重ねの敵へ雷の矢を当てると、まわり 6 の敵 5 体へ跳ねる"),
         ("雷速", "雷の矢を続けて当てるほど待ちが短くなる（最大 −50%）。外すと戻る")),
        ("fire", "火", "熾火の玉",
         ("熾火", "燃えている敵に技を当てると熾火が溜まる（5 まで）。熾火の玉が熾火一つにつき大きく強くなる（+1 ブロック・+20%）"),
         ("延焼", "燃えている敵が倒れると、炎がまわり 5 の敵へ移る")),
        ("tide", "潮", "凍て潮・転移",
         ("凍え", "凍て潮で鈍った敵に技を当てると凍る（2 秒動けない。同じ敵は 8 秒に一度。大きな相手は鈍りが強まるだけ）。凍った敵への雷の矢は +50%"),
         ("潮の盾", "凍て潮を使うと 4 秒、自分の受ける傷 −30%")),
    ],
    "archer": [
        ("pierce", "雲穿ち", "雲穿ち・足止めの矢",
         ("狙い", "動かずにいると狙いが溜まる（1 秒ごとに一段、3 段）。雲穿ちが一段につき貫き +1・傷 +30%。動くと一段ずつ減る"),
         ("一点", "同じ敵に雲穿ちを当てるたびに、次の雲穿ちの待ち −1.5 秒")),
        ("trap", "骨の罠", "骨の罠・足止めの矢",
         ("罠の糸", "罠どうしが糸でつながり、糸を越えた敵は 2 秒鈍る。足止めの矢を当てた敵のまわりの罠は全部はぜる（一つ 攻撃力の 120%）"),
         ("仕掛け人", "罠で縛った敵への矢 +35%。置ける罠 +1")),
        ("rain", "天の驟雨", "天の驟雨",
         ("空の矢筒", "ふつうの矢を当てるたびに空に矢が溜まる（最大 20）。天の驟雨で溜めた矢がまとめて降る（一本 攻撃力の 30%）"),
         ("嵐の目", "天の驟雨の中に立つと、自分の矢の速さ +30%、矢が二本になる")),
    ],
    "assassin": [
        ("shadow", "影の刺客", "踏み込み・二連牙・風裂き",
         ("影の印", "踏み込み・二連牙で敵に影の印（3 つまで）。風裂きで印を全部弾けさせる（一つ 攻撃力の 100%）"),
         ("背の傷", "背から当てるたびに背の傷 +1（5 まで）。霧隠れから出た最初の一撃に、傷一つにつき +25%")),
        ("dance", "風の舞い手", "跳び退き・風裂き",
         ("舞", "違う技を順につなぐと舞が一段上がる（最大 4。同じ技を続けると 0 に戻る）。一段ごとに技の傷 +8%・待ち −5%"),
         ("跳び返し", "跳び退きのあと 2 秒、次の技が +40%、マナ半分")),
        ("lone", "孤風", "霧隠れ",
         ("孤り", "まわり 12 に仲間がいないあいだ孤りが溜まる（6 秒で満ちる）。満ちると次の一撃が ×2.5"),
         ("一騎", "同じ敵だけを打ち続けると傷 +5% ずつ（8 重ね）。別の敵を打つと消える")),
    ],
    "shaman": [
        ("song", "守りの唄", "守りの唄・巡りの唄・灯の守り札",
         ("調べ", "守りの唄・巡りの唄で調べが一つ（3 つまで）。灯の守り札が調べの数だけ広く長くなる（一つにつき +1 ブロック・+3 秒）"),
         ("割れた護り", "仲間の護りの体力が割れるたびにマナ +3。割れた仲間は 2 秒 受ける傷 −30%")),
        ("ebb", "引き潮", "引き潮",
         ("潮位", "引き潮で引いた敵の数だけ潮位（最大 8）。満ちると次の技が「満ち潮」になる（まわり 6 の敵を押し流し、攻撃力の 200%）"),
         ("渦", "引き潮のあと 3 秒、引いた所に渦（中の敵は毎秒 攻撃力の 30%、中の敵への仲間の技 +15%）")),
        ("curse", "呪い", "呪いの印",
         ("呪いの重ね", "呪いの印を同じ敵に重ねられる（3 重ね。重ねるほど受ける傷が増える）。3 重ねで印が弾ける（攻撃力の 300%、まわりの敵へ印が一つ移る）"),
         ("呪いの巡り", "印の敵が倒れると、まわり 8 の敵へ印が残りの時間ごと移る")),
    ],
}

LINES_EXAMPLE = [
    ("鯨骨の大剣（エピック・戦士）", "近接の傷 +12%・技の待ち −5%", "攻撃の速さ −6%"),
    ("熔け顎の大剣（ヒロイック・戦士）", "近接の傷 +15%・体力吸収・火の傷 −20%", "移動の速さ −8%"),
    ("雲の瞼の杖（神器・魔法使い）", "技の傷 +25%・マナの戻り +20%", "最大体力 −12%"),
    ("霞鳥の弓（神器・弓使い）", "矢の傷 +18%・移動の速さ +10%", "防御 −10%"),
]


def questions():
    qs = list(POLICY)
    for job, jname, wname in JOBS:
        for (iid, name, src, now, a, b) in DIVINE[job]:
            qs.append({"id": "dv_" + iid, "group": "divine", "job": job, "title": name, "src": src, "now": now,
                       "options": [{"v": "a", "label": "案 1「%s」" % a[0], "sub": a[1] + "。欠点: " + a[2], "rec": True},
                                   {"v": "b", "label": "案 2「%s」" % b[0], "sub": b[1] + "。欠点: " + b[2]},
                                   {"v": "keep", "label": "今のまま"}]})
        for (aid, aname, skills, a, b) in ARCH[job]:
            qs.append({"id": "ar_%s_%s" % (job, aid), "group": "arch", "job": job, "title": aname, "src": "主な技: " + skills,
                       "options": [{"v": "a", "label": "案 1「%s」" % a[0], "sub": a[1], "rec": True},
                                   {"v": "b", "label": "案 2「%s」" % b[0], "sub": b[1]}]})
    return qs


def esc(s):
    return html.escape(s, quote=True)


def write_md():
    L = ["# 2026-11-02 神器の大きな能力・武器の数値の行・技の回し（案）", "",
         "声（2026-11-02）「Wynncraft のミシックみたいに神器にはそれぞれ一つ特出した特殊能力あるけど、ある？」「武器の数値や能力等…それぞれの武器の良さでてる？」「戦っていて楽しいようなスキル回しとかできる？」→「案をまとめて」。",
         "", "選ぶページ https://claude.ai/artifact/NbFMC5stEczhu1gEMJB9Zn （選んだものは db の `choices`）。ページ（`page.html`）とこの md は `tools/gen/review/mythic_rotation_page.py` が書く。直したら Artifact をこの URL へ出し直す。未決の 340〜345（[00](../../design/00_decisions_needed.md)）と同じ中身。", "",
         "## いまの見立て", "",
         "- 神器: 固有効果は一つずつあるが、「数回に一度の追撃」「数秒の上乗せ」で、Lv 30 のヒロイックと同じくらい。1 秒あたりも神器どうしでそろえてあり、持ち替えても遊び方が変わらない。",
         "- 武器: 1 秒あたりを階級ごとにそろえている（`TierFloor`）ので、差は速さ・固有効果一つ・ランダムな特性（4〜10%）だけ。品ごとに決まった数値の行が無い。",
         "- 技: 4 本、待ち 6〜22 秒、マナ 4〜18。主の大技のあとの隙（3 秒 ×1.6）・呪いの印・足止め・打ち手の泉・炉の記憶・秘伝はあるが、技どうしのつながりが少なく「空いた技から押す」形になりやすい。", ""]
    L += ["## 方針（340〜343）", ""]
    for q in POLICY:
        L.append("### %d. %s" % (q["no"], q["title"]))
        L.append("")
        L.append(q["why"])
        L.append("")
        for o in q["options"]:
            L.append("- **%s** %s%s%s" % (o["v"], o["label"], "（推奨）" if o.get("rec") else "", "。" + o["sub"] if o.get("sub") else ""))
        L.append("")
    L += ["例（341 の A。数値は見本）:", "", "| 品 | 行 | 欠点の行 |", "| --- | --- | --- |"]
    for n, l, d in LINES_EXAMPLE:
        L.append("| %s | %s | %s |" % (n, l, d))
    L += ["", "## 344. 神器の武器 18 本の大きな能力（推奨はどれも案 1）", ""]
    for job, jname, wname in JOBS:
        L.append("### %s（%s）" % (jname, wname))
        L.append("")
        L.append("| 品 | 出どころ | 今 | 案 1 | 案 2 |")
        L.append("| --- | --- | --- | --- | --- |")
        for (iid, name, src, now, a, b) in DIVINE[job]:
            L.append("| %s `%s` | %s | %s | **%s**: %s。欠点: %s | **%s**: %s。欠点: %s |" % (name, iid, src, now, a[0], a[1], a[2], b[0], b[1], b[2]))
        L.append("")
    L += ["## 345. 型ごとの溜まるもの 18（推奨はどれも案 1）", ""]
    for job, jname, wname in JOBS:
        L.append("### %s" % jname)
        L.append("")
        L.append("| 型 | 主な技 | 案 1 | 案 2 |")
        L.append("| --- | --- | --- | --- |")
        for (aid, aname, skills, a, b) in ARCH[job]:
            L.append("| %s | %s | **%s**: %s | **%s**: %s |" % (aname, skills, a[0], a[1], b[0], b[1]))
        L.append("")
    os.makedirs(os.path.dirname(OUT_MD), exist_ok=True)
    with open(OUT_MD, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L))


def write_html():
    qs = questions()
    nav = "".join('<a href="#%s">%s</a>' % (job, jname) for job, jname, _ in JOBS)
    job_sections = []
    for job, jname, wname in JOBS:
        job_sections.append(
            '<section class="job" id="%s"><h2>%s<span class="wk">%s</span></h2>'
            '<h3 class="sub">神器の大きな能力</h3><div class="qs" data-group="divine" data-job="%s"></div>'
            '<h3 class="sub">型ごとの溜まるもの</h3><div class="qs" data-group="arch" data-job="%s"></div></section>'
            % (job, jname, esc(wname), job, job))
    lines_rows = "".join("<tr><td>%s</td><td>%s</td><td class=\"neg\">%s</td></tr>" % (esc(n), esc(l), esc(d)) for n, l, d in LINES_EXAMPLE)
    data = json.dumps(qs, ensure_ascii=False)
    page = TEMPLATE.replace("%%NAV%%", nav).replace("%%JOBS%%", "\n".join(job_sections)).replace("%%LINES%%", lines_rows).replace("%%DATA%%", data)
    with open(OUT_HTML, "w", encoding="utf-8", newline="\n") as f:
        f.write(page)


TEMPLATE = r"""<title>神器と技の回し 11/02</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DotGothic16&family=Zen+Antique&display=swap">
<style>
/* Layout: one reading column — the diagnosis, four policy questions, then one band per job holding its three mythic weapons and three archetype gauges */
:root {
  --bg: #eef0f4; --panel: #ffffff; --fg: #1d2230; --soft: #5a6274; --line: #cfd4de;
  --accent: #7a2a8c; --gold: #92660f; --pick: #f3e6f6; --neg: #a3343a;
  --display: "Zen Antique", "Hiragino Mincho ProN", "Yu Mincho", serif;
  --body: "DotGothic16", "Hiragino Sans", "Yu Gothic", system-ui, sans-serif;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #13141c; --panel: #1c1d28; --fg: #e8e3d6; --soft: #a9acba; --line: #2e3040;
  --accent: #d38be3; --gold: #f0c35a; --pick: #2c1f33; --neg: #ef8a8f; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #13141c; --panel: #1c1d28; --fg: #e8e3d6; --soft: #a9acba; --line: #2e3040;
  --accent: #d38be3; --gold: #f0c35a; --pick: #2c1f33; --neg: #ef8a8f; color-scheme: dark }
body { background: var(--bg); color: var(--fg); font-family: var(--body); line-height: 1.75;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 28px 72px; }
main { max-width: 780px; margin: 0 auto; display: grid; gap: 30px; }
h1, h2 { font-family: var(--display); font-weight: 400; text-wrap: balance; margin: 0; }
h1 { font-size: 1.9rem; letter-spacing: .04em; }
h2 { font-size: 1.35rem; color: var(--gold); border-bottom: 1px solid var(--line); padding-bottom: 6px; display: flex; gap: 12px; align-items: baseline; }
h2 .wk { font-family: var(--body); font-size: .85rem; color: var(--soft); }
h3 { font-size: 1rem; margin: 0; font-weight: 400; }
h3.sub { color: var(--accent); letter-spacing: .06em; font-size: .92rem; }
p { margin: 0; }
.lede { color: var(--soft); }
section { display: grid; gap: 14px; }
ul { margin: 0; padding-left: 1.2em; display: grid; gap: 4px; }
nav.jobs { display: flex; flex-wrap: wrap; gap: 8px; }
nav.jobs a { color: var(--accent); text-decoration: none; border: 1px solid var(--line); border-radius: 4px; padding: 2px 10px; background: var(--panel); }
nav.jobs a:focus-visible, nav.jobs a:hover { border-color: var(--accent); }
.qs { display: grid; gap: 12px; }
.q { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 14px 16px; display: grid; gap: 9px; min-width: 0; }
.q .head { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; }
.q .no { color: var(--soft); font-size: .85rem; font-variant-numeric: tabular-nums; }
.q .src { color: var(--soft); font-size: .85rem; }
.q .why { color: var(--soft); font-size: .92rem; }
.q .now { font-size: .9rem; border-left: 3px solid var(--line); padding-left: 10px; color: var(--soft); }
.q .now b { color: var(--fg); font-weight: 400; }
.opts { display: grid; gap: 6px; }
.opt { display: flex; gap: 10px; align-items: flex-start; border: 1px solid var(--line); border-radius: 5px; padding: 7px 10px; cursor: pointer; }
.opt input { margin-top: 6px; accent-color: var(--accent); flex: none; }
.opt.on { background: var(--pick); border-color: var(--accent); }
.opt span { min-width: 0; }
.opt small { display: block; color: var(--soft); font-size: .88rem; }
.rec { color: var(--gold); font-size: .82rem; margin-left: 6px; }
textarea { width: 100%; box-sizing: border-box; font: inherit; color: var(--fg); background: var(--bg); border: 1px solid var(--line); border-radius: 4px; padding: 6px 8px; min-height: 2.2em; }
.row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
button { font: inherit; background: var(--accent); color: var(--bg); border: 0; border-radius: 4px; padding: 5px 14px; cursor: pointer; }
button.ghost { background: transparent; color: var(--accent); border: 1px solid var(--accent); }
button:focus-visible, textarea:focus-visible, .opt:focus-within { outline: 2px solid var(--gold); outline-offset: 2px; }
.state { color: var(--soft); font-size: .85rem; }
.bar { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--bg); border-bottom: 1px solid var(--line); padding-block: 8px; display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; }
body::before { content: ""; position: fixed; inset: 0 0 auto; height: env(safe-area-inset-top, 0px); z-index: 10; background: var(--bg); }
.count { font-variant-numeric: tabular-nums; color: var(--soft); font-size: .9rem; }
.tablebox { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; font-size: .9rem; }
th, td { text-align: left; border-bottom: 1px solid var(--line); padding: 6px 8px; vertical-align: top; }
th { color: var(--soft); font-weight: 400; }
td.neg { color: var(--neg); }
.note { color: var(--soft); font-size: .9rem; }
.diag li b { color: var(--accent); font-weight: 400; }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto !important; } }
</style>

<main>
  <header style="display:grid;gap:8px">
    <h1>神器と技の回し</h1>
    <p class="lede">「神器に特出した能力はある？」「武器の良さは出ている？」「楽しい技の回しはできる？」への見立てと、直す案です。方針の 4 つと、職ごとの神器 3 本・型 3 つを選んでもらえれば、その形で作ります。おすすめはどれも案 1 です。</p>
  </header>

  <section>
    <h2>いまの見立て</h2>
    <ul class="diag">
      <li><b>神器</b>: 固有効果は一つずつあるが、「数回に一度の追撃」「数秒の上乗せ」で、Lv 30 のヒロイックと同じくらい。1 秒あたりも神器どうしでそろえてあり、持ち替えても遊び方が変わらない。</li>
      <li><b>武器</b>: 1 秒あたりを階級ごとにそろえているので、同じ職の武器の差は速さ・固有効果一つ・ランダムな特性（4〜10%）だけ。品ごとに決まった数値の行が無い。</li>
      <li><b>技</b>: 4 本、待ち 6〜22 秒、マナ 4〜18。主の大技のあとの隙（3 秒、与える傷 ×1.6）・呪いの印・足止め・打ち手の泉・炉の記憶はあるが、技どうしのつながりが少なく「空いた技から押す」形になりやすい。</li>
    </ul>
  </section>

  <div class="bar">
    <span class="count" id="count">選んだ数 0 / 0</span>
    <button type="button" id="allrec">まだ選んでいないものを、おすすめで保存</button>
    <span class="state" id="dbstate">選ぶと保存されます（この画面を開いた人みなで同じ記録）。</span>
  </div>

  <section>
    <h2>方針</h2>
    <div class="qs" data-group="policy"></div>
    <div class="tablebox">
      <table>
        <caption class="note" style="text-align:left;padding-bottom:6px">341 の A にした場合の数値の行の例（数値は見本）</caption>
        <thead><tr><th>品</th><th>行</th><th>欠点の行</th></tr></thead>
        <tbody>%%LINES%%</tbody>
      </table>
    </div>
  </section>

  <section>
    <h2>職ごとの案</h2>
    <p class="note">方針 340 が A のときの神器 18 本の中身と、342 が A のときの型 18 の中身です。神器の数値は今の強さ（1 秒あたり）を基に、欠点のぶん少し強くしてあります。細かい数は作るときに測って直します。</p>
    <nav class="jobs" aria-label="職">%%NAV%%</nav>
  </section>

%%JOBS%%
</main>

<script>
const QUESTIONS = %%DATA%%;
const picks = {};
let db = null;
const stateEl = document.getElementById("dbstate");
const countEl = document.getElementById("count");

function card(q) {
  const c = document.createElement("div"); c.className = "q";
  const head = document.createElement("div"); head.className = "head";
  if (q.no) { const n = document.createElement("span"); n.className = "no"; n.textContent = String(q.no); head.append(n); }
  const h = document.createElement("h3"); h.textContent = q.title; head.append(h);
  if (q.src) { const s = document.createElement("span"); s.className = "src"; s.textContent = q.src; head.append(s); }
  c.append(head);
  if (q.why) { const w = document.createElement("p"); w.className = "why"; w.textContent = q.why; c.append(w); }
  if (q.now) { const w = document.createElement("p"); w.className = "now"; w.textContent = "今: "; const b = document.createElement("b"); b.textContent = q.now; w.append(b); c.append(w); }
  const opts = document.createElement("div"); opts.className = "opts";
  const cur = picks[q.id] || {};
  for (const o of q.options) {
    const lab = document.createElement("label"); lab.className = "opt" + (cur.pick === o.v ? " on" : "");
    const inp = document.createElement("input"); inp.type = "radio"; inp.name = q.id; inp.id = q.id + "_" + o.v; inp.value = o.v; inp.checked = cur.pick === o.v;
    inp.addEventListener("change", () => save(q.id, o.v, null));
    const t = document.createElement("span"); t.textContent = o.label;
    if (o.rec) { const r = document.createElement("span"); r.className = "rec"; r.textContent = "おすすめ"; t.append(r); }
    if (o.sub) { const s = document.createElement("small"); s.textContent = o.sub; t.append(s); }
    lab.append(inp, t); opts.append(lab);
  }
  c.append(opts);
  const ta = document.createElement("textarea"); ta.id = q.id + "_note"; ta.placeholder = "ひとこと（あれば）"; ta.value = cur.note || "";
  const row = document.createElement("div"); row.className = "row";
  const b = document.createElement("button"); b.type = "button"; b.className = "ghost"; b.textContent = "ひとことを保存";
  const st = document.createElement("span"); st.className = "state"; st.textContent = cur.pick ? "保存済み" : "";
  b.addEventListener("click", () => save(q.id, (picks[q.id] || {}).pick || "", ta.value, st));
  row.append(b, st);
  c.append(ta, row);
  return c;
}

function render() {
  for (const box of document.querySelectorAll(".qs")) {
    const g = box.dataset.group, j = box.dataset.job;
    box.textContent = "";
    for (const q of QUESTIONS) {
      const qg = q.group || "policy";
      if (qg === g && (!j || q.job === j)) box.append(card(q));
    }
  }
  const done = QUESTIONS.filter(q => (picks[q.id] || {}).pick).length;
  countEl.textContent = "選んだ数 " + done + " / " + QUESTIONS.length;
}

async function save(id, pick, note, st) {
  const prev = picks[id] || {};
  picks[id] = { pick, note: note === null ? (prev.note || "") : note };
  render();
  if (!db) { stateEl.textContent = "この画面では保存できません（選んだものはチャットで伝えてください）。"; return; }
  try {
    await db.collection("choices").doc(id).set({ pick: picks[id].pick, note: picks[id].note, at: new Date().toISOString() });
    if (st) st.textContent = "保存しました";
  } catch (e) {
    stateEl.textContent = "保存できませんでした: " + (e && e.message ? e.message : "不明");
  }
}

document.getElementById("allrec").addEventListener("click", async () => {
  const todo = QUESTIONS.filter(q => !(picks[q.id] || {}).pick);
  if (!todo.length) { stateEl.textContent = "どれも選んであります。"; return; }
  for (const q of todo) {
    const rec = q.options.find(o => o.rec);
    if (rec) await save(q.id, rec.v, null);
  }
  if (db) stateEl.textContent = todo.length + " 件をおすすめで保存しました。";
});

render();
(async () => {
  try { db = await window.claude?.use?.("db"); } catch (e) { db = null; }
  if (!db) { stateEl.textContent = "この画面では保存できません（選んだものはチャットで伝えてください）。"; return; }
  const timer = setTimeout(() => { stateEl.textContent = "記録の読み込みに時間がかかっています。選ぶと保存は試みます。"; }, 10000);
  db.collection("choices").onSnapshot((snap) => {
    clearTimeout(timer);
    for (const d of snap.docs) { const v = d.data() || {}; picks[d.id] = { pick: String(v.pick || ""), note: String(v.note || "") }; }
    stateEl.textContent = "選ぶと保存されます（この画面を開いた人みなで同じ記録）。";
    const active = document.activeElement && document.activeElement.tagName === "TEXTAREA";
    if (!active) render();
  }, () => { clearTimeout(timer); stateEl.textContent = "記録を読めませんでした。選んだものはチャットで伝えてください。"; });
})();
</script>
"""

if __name__ == "__main__":
    write_md()
    write_html()
    print(len(questions()), "questions")
