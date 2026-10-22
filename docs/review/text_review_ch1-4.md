# 日本語テキストの見直し（序章〜四章）

- 範囲: 序章〜四章の台詞（語り部・イサナ・職人の会話）、クエストの題・目的・書き出し・日誌（ch1〜ch4 と序章に関わる quest / objective）、伝承（lore）、告知と地の文（message）。
- 方法: lang（ja_jp / en_us）の値を読み、生成元（`tools/gen_*.py`）の同じ文字列も直した。生成元の再実行を止めている `gen_story_quests.py` `gen_free_quests.py` も、文字列の直しだけ入れた（再実行はしていない）。
- 検査: `python -I tools/check_text_fit.py` = 8508 件、問題 0。`python -I tools/check_lang_keys.py` = 2336 キー、欠け 0。自己テストの結果は branch の最後のコミット時点で確認（報告参照）。
- 対象外（次の見直しへ）: 町の村人（宿・酒場・組合など）の台詞、HUD、アイテム説明の細部、英語の全行。語り部などの「……」は全体で 195 箇所あり、今回は整理せず、量の判断を次の回に回した。

## 変更の内訳（種類別）

| 種類 | 件数 | 主な例 |
| --- | --- | --- |
| 訳文調・不自然な言い回し | 4 | 「あの荷の手は」→「あの手つきは」 / 「つなげられる」→「絶えずに済むよ」 |
| 用語の統一 | 5 | 「ベッド」→「寝台」（町の宿と揃える） / 「亀」→「潮殻」（巨神名） / 「背中の石」→「背中の鱗片」（品名「岩鯨の鱗片」に揃える） |
| 口調（人物ごとの一貫性） | 2 | 職人「言っておくれ」→「言ってくれ」 / 鯨追い「似合ってるよ」→「似合ってるな」 |
| 事実・論理の誤り | 1 | 灰の道: 「灰が濃くなるほうへ歩けば縁に着く」→「薄くなるほう」（杭の灰は円の中ほどで濃い） |
| 目的・告知の明確さ | 4 | 「積み石を見て回る」→「道の積み石を二つ見に行く」 / 「振りが利く」→「攻撃が強くなる」 / 「三つの何か」→「三つの樽」 / 「鉄が冷えを受け止めた」→「鉄の防具が冷えを受け止めた」 |
| 重複・語順・表記 | 4 | 「研がずに振って折れた」（重複）→「それで折れた」 / 「嵐の、跡」→「嵐の跡」 / 「巨きな」→「大きな」 / 「見せないためだと」→「見ないようにするためだと」 |
| 英語の誤り・不整合 | 5 | 「That sword broke because it was.」→「That sword broke that way.」 / 「turtle's shell」→「Tide Shell's carapace」 / 「three things」→「three barrels」 / 「stone whale's young」→「Travel on the stone whale」（ja と生成元に揃えた） / 目的「Look over the cairns」→「Go look at two cairns along the road」 |
| 一括（語の統一） | 8 箇所 | 「仲間も数える」→「仲間の分も数える」（日誌・目的の全章。lang 8 箇所、生成元 8 箇所） |

## ファイル別の変更

（件数は「値の変更」の数。生成元も同じ文字列を直した。）

### src/main/resources/assets/skyabyss_ch1/lang/ja_jp.json（9）
- `dialogue.skyabyss.storyteller.ch1p_artisan_pointer`: 「あの荷の手は、あたしも覚えている」→「あの手つきは、あたしも覚えている」
- `dialogue.skyabyss.whale_chaser.ch1t_c_iron`: 「似合ってるよ。」→「似合ってるな。」
- `quest.skyabyss.ch1.day1_firewood.done`: 「これで夜の火は、つなげられる」→「これで夜の火が絶えずに済むよ」
- `quest.skyabyss.ch1.day1_cairns.round.round`（目的）: 「積み石を見て回る」→「道の積み石を二つ見に行く」
- `quest.skyabyss.ch1.trail_blade.forge.lead`: 「あの剣は、研がずに振って折れた」→「あの剣は、それで折れた」
- `message.skyabyss.ch1.arrive.ch1_bone_pit`: 「巨きな肋骨」→「大きな肋骨」
- `message.skyabyss.ch1.nook.bed`: 「宿りに、ベッドが置かれている。」→「宿りに、寝台が置かれている。」
- `message.skyabyss.ch1.pit.cold_open`: 「鉄が冷えを受け止めた。」→「鉄の防具が冷えを受け止めた。」
- `message.skyabyss.whetstone.used`: 「二十分のあいだ、振りが利く」→「二十分のあいだ、攻撃が強くなる」

### src/main/resources/assets/skyabyss_ch2/lang/ja_jp.json（2）
- `dialogue.skyabyss.artisan.ch2a_first`: 「直すものがあれば言っておくれ。」→「直すものがあれば言ってくれ。」
- `message.skyabyss.ch2.vault.door_shut`: 「棚が、三つの何かを待っている。」→「棚が、三つの樽を待っている。」

### src/main/resources/assets/skyabyss_ch3/lang/ja_jp.json（1）
- `quest.skyabyss.ch3.sub_wind_marks.summary`: 「風に逆らう嵐の、跡をたどる。」→「風に逆らって来る嵐の跡をたどる。」

### src/main/resources/assets/skyabyss_ch4/lang/ja_jp.json（1）
- `dialogue.skyabyss.storyteller.ch4g_road_tale`: 「灰が濃くなるほうへ歩けば」→「灰が薄くなるほうへ歩けば」

### src/main/resources/assets/skyabyss_npc/lang/ja_jp.json（2）
- `dialogue.skyabyss.whale_chaser.c_shards_offer`: 「背中の石を、少し持ってきてくれないか。」→「背中の鱗片を、少し持ってきてくれないか。」
- `dialogue.skyabyss.storyteller.song0_b`: 「見えないものを見せないためだと」→「見えないものを見ないようにするためだと」

### src/main/resources/assets/skyabyss_story/lang/ja_jp.json（2）
- `dialogue.skyabyss.whale_chaser.ms_c_tide_back2`: 「亀は海で刻を守る」→「潮殻は海で刻を守る」
- `quest.skyabyss.daily_tide_rounds.rounds.journal`: 「亀の甲羅の中で」→「潮殻の甲羅の中で」

### src/main/resources/assets/skyabyss_quest/lang/ja_jp.json（1）
- `quest.skyabyss.sub_waymarks.ride.ride`: 「岩鯨の子に乗って、旅をする」→「岩鯨に乗って、旅をする」（生成元は元から「岩鯨に乗って」。lang だけ食い違っていた）

### 英語（en_us.json）（5）
- skyabyss_ch1: `quest.skyabyss.ch1.trail_blade.forge.lead` 「That sword broke because it was.」→「That sword broke that way.」
- skyabyss_ch1: `quest.skyabyss.ch1.day1_cairns.round.round` 「Look over the cairns」→「Go look at two cairns along the road」
- skyabyss_story: `quest.skyabyss.daily_tide_rounds.rounds.journal` 「inside the turtle's shell」→「inside the Tide Shell's carapace」
- skyabyss_quest: `quest.skyabyss.sub_waymarks.ride.ride` 「Travel on the stone whale's young」→「Travel on the stone whale」
- skyabyss_ch2: `message.skyabyss.ch2.vault.door_shut` 「three things」→「three barrels」

### 一括（lang 8 箇所 / 生成元 8 箇所）
- 「仲間も数える」→「仲間の分も数える」: 日誌・目的の概要（全 lang の該当 8 箇所）。生成元は `tools/gen_free_quests.py` `tools/gen_storm_lore.py` `tools/gen_story_quests.py`。

### 生成元の直し（tools/）（17 + 英語 1）
- `tools/gen_ch1_prep.py` `tools/gen_ch1_trails.py` `tools/gen_ch1_content.py` `tools/gen_ch1_travel.py` `tools/gen_ch2_ground.py` `tools/gen_ch3_content.py` `tools/gen_ch4_ground.py` `tools/gen_npc_content.py` `tools/gen_story_quests.py`: 上の各文字列と同じ直し。
- `tools/gen_ch1_content.py`: 英語の目的「Look over the cairns」も直した。

## 問い（docs/design/00_decisions_needed.md の 264〜271）

- 264 鯨追い（イサナ）の呼び方（目的・書き出しと台詞の揺れ）
- 265 「古い灰被り」「灰被りの屑」の呼び名（童話の連想）
- 266 耐火の薬の呼び名（「ポーション」と「薬」の混ざり）
- 267 鼓動守の「一日にひとつだけ」（回数の決まりの有無）
- 268 冷えの戸の開け方（鉄を着る・持つ）
- 269 鯨道の旅装と「鉄の防具」の書き分け
- 270 数え歌の数字の書き方（かな・漢数字）
- 271 五番目の歌詞を語り部がいつ知っているか

## 気づいた点（直していない）

- 生成元と lang の食い違い（`岩鯨の子に乗って` は lang だけに残っていた）。`gen_story_quests.py` などは再実行しない決まりなので、lang と生成元の両方を手で揃えた。次に再実行するときは、この二つの差が出ないか見ること。
- `lore.skyabyss.chapter1.*` のキーが二章・三章・四章のモジュールにもある（キー名の付け方の揺れ。表示には出ない）。
- 語り部の台詞に「……」が多い（全体で 195 箇所）。次の回で、一場面に一つまでに絞るかを決める。
