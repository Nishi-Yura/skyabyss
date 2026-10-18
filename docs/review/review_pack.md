# 事前レビュー用パック（ゲーム内で未確認の見た目）

プレイの前に、まだ見ていないゲーム内の絵を一度に確かめるためのページ。
絵は `docs/review/previews/review_pack/` に JPEG で 37 枚（幅 960 以下、1 枚 約 50 KB）。
撮影は台本ランナー（`./gradlew runShots -Pscript=...`）で、言語は `ja_jp`（撮影フォルダの `options.txt` で指定）。
台本の作りは `tools/shots/review_*.txt`、JPEG の作成は `tools/build_review_pack.py`。

## 1. シネマティック（開幕・石の鯨の初見・心臓の登場・巨神の眠り）

| 絵 | 何が写っているか | 見るところ |
| --- | --- | --- |
| 01_opening_00_fade_in | 開幕の一枚目。草原と、画面の右下に手の持ち物 | 暗転から戻る瞬間。画面の端の操作の帯（W・A・S・D、Space） |
| 02_opening_eyelid | 開幕のまぶた（目が開く途中） | まぶたの開きが黒い帯で始まるか |
| 03_opening_lying | 寝ころびの視点 | 空と地面だけで、語りの字がまだ出ていない |
| 04_opening_title_frame | 題字の直前の一枚 | **題字は写っていない**（下の「撮れなかったもの」を参照） |
| 05_opening_heaven | 空を見上げる視点 | 空の色と、雲の形 |
| 06_opening_return | 体へ戻る一枚 | 操作の帯が出る前の景色 |
| 07_whale_first_sight_start | 石の鯨の初見の始め。日本語の字幕が下に出ている | 字幕が日本語で出ること、画面下の「Space 長押しでスキップ」 |
| 08_whale_first_sight_push | 鯨が空を横切る。日本語の字幕が出ている | 鯨の見え方（低ポリ）、空のシルエット |
| 09_whale_first_sight_whale | 鯨の正面寄り。日本語の字幕が出ている | 鯨の尾と胸びれの形、影 |
| 10_whale_first_sight_orbit | 鯨の周りを回る視点。村の小屋が右端に見える | 鯨と村の距離感（小屋が小さく見えるか） |
| 11_heart_appear_stone_40 | 石の心臓の登場（40 tick） | 心臓が床から立ち上がる途中、赤い光の粒 |
| 12_heart_appear_stone_72 | 心臓の登場（72 tick） | 光の粒が収まったあとの形、足元の輪 |
| 13_heart_death_stone_44 | 石の心臓が倒れる途中（44 tick） | 倒れる途中の姿勢と、床に残る欠片 |
| 14_dormancy_whale_song_stops | 石の鯨が眠る場面。歌が止まるところの日本語の字幕 | 字幕の文、鯨の姿勢 |
| 15_dormancy_whale_sinking | 鯨が沈んでいく途中 | 沈み方（速すぎないか）、胸の光が消えているか |
| 16_dormancy_whale_dormant | 鯨が眠ったあと。日本語の字幕が出ている | 眠りの字幕、鯨の残る姿 |

## 2. 章の題字の札・余韻の札（日本語）

| 絵 | 何が写っているか | 見るところ |
| --- | --- | --- |
| 17_card_chapter1_ja | 一章の題字の札を出したとき（**写っているのは黒い画面**） | 札が出る前の一瞬。題字は写っていない（下を参照） |
| 18_card_aftermath_ja | 一章の余韻の札（上段の見出しと、下の本文の一行。字は小さいので原寸で読むこと） | 札の文字が日本語で読めるか、本文の言い回し |
| 19_card_with_levelup_and_caption | 四章の題字の札、レベルアップの知らせ、同時に出る字幕 | 札とレベルアップの知らせが重ならないこと |

## 3. 章の小さな仕掛け

撮影は平らな世界に置き物として並べた（本番の章の拠点とは配置が違う）。

| 絵 | 何が写っているか | 見るところ |
| --- | --- | --- |
| 20_small_song_stones_row | 歌石 5 つを一列に並べた（右奥に小さな茶色の品。貝か別の置き物かは要確認） | 歌石の形と、5 つ並んだときの見分けやすさ |
| 21_small_song_stone_close | 歌石の寄り（右下の石） | 歌石の表面、光っていないときの色 |
| 22_small_storm_vane | 風見鶏の位置（**風見鶏の塔は置けなかったので、避雷の柱の根元で代用**） | 風見鶏の見つけやすさ。本物の風見鶏はこの絵には出ていない |
| 23_small_travel_waymark | 旅の目印（titan_waymark）を置いた所 | 目印が遠くから見えるか（ほとんど見えていない） |

## 4. 低ポリの見え方

| 絵 | 何が写っているか | 見るところ |
| --- | --- | --- |
| 24_titan_whale_mid | 岩鯨、中くらいの距離 | 体の面の数と、影の出方 |
| 25_titan_tide_mid | 潮殻、中くらいの距離（上に小さな塔） | 殻の形、海の上の見え方 |
| 26_titan_storm_mid | 雷翼、中くらいの距離 | 翼の薄さ。背景の空が暗く見える |
| 27_titan_ember_mid | 余燼、中くらいの距離（夕暮れ風の空） | 体の影、空の色との馴染み |
| 28_back_lineup_whale | 岩鯨の背の敵の並び（骨の番兵・苔這い・射手・鯨骨の将） | 4 種の見分けやすさ、顔の向き |
| 29_back_lineup_tide | 潮殻の背の敵の並び（礁の這い・泡袋・潮を呼ぶ者・珊瑚の這い） | 泡袋の大きさと浮き方 |
| 30_back_lineup_storm | 雷翼の背の敵の並び（静かな蛾・雷獣・祈る者・羽の番人） | 蛾の浮き、雷獣の形 |
| 31_back_lineup_ember | 余燼の背の敵の並び（灰被り・燠の蛾・燠の転がし・灰の鍛え手） | 灰被りの色、燠の蛾の光 |
| 32_npc_storyteller_three_quarter | 語り部の四分の三の姿 | 服と頭巾の形 |
| 33_npc_storyteller_face | 語り部の顔（近く） | 顔の面の数。目と口の見え方 |
| 34_armor_stone_whale_player_idle | 岩鯨の防具（一般）を着た人、立ち | 防具の形、肩の板 |
| 35_armor_stone_whale_legend10_q34 | 岩鯨の伝説の防具（強化 +10）、四分の三 | 光る画素、面の見え方 |
| 36_weapon_ember_war_axe_first_person_legend | 余燼の戦斧（伝説）、一人称 | 手に持った見え方、光る画素 |
| 37_weapon_ember_war_axe_third_person_common | 余燼の戦斧（一般）、三人称 | 持つ位置と向き |

## 撮れなかったもの・気になる点

- **開幕の題字（日本語）**: 撮った 6 枚のどれにも題字が写っていない。題字は暗転のあとに出るので、台本のタイミングが合っていない可能性が高い。次は `tools/shots/opening_v2.txt` の時刻に合わせて撮り直す。
- **目的の帯**: 撮れていない（開幕のあとに出る画面の帯）。
- **潮見の貝の吹く合図（潮の合図）**: 撮れていない。貝を持たせる（`cmd give`）ところまでで、吹く場面は撮っていない。
- **旅の跡（章の小要素の箱と頁）**: 撮れていない。今回は歌石と旅の目印（titan_waymark）を置いただけ。本番の配置は章の拠点の仕組みで出るので、置き物では再現できない。
- **風見鶏の塔**: `skyabyss:storm_vane` は置けるブロックではなかった（章の拠点の型）。代わりに避雷の柱の根元（`skyabyss:storm_rod`）を置いた。本物の風見鶏は未確認。
- **失敗した手順（絵は撮れている）**:
  - `rc_heart`: kill コマンドの手順が 3 回失敗（対象が無かっただけ。絵は 44 枚撮れている）。
  - `rc_backs`: fill コマンドが 1 回失敗（x=140 の所がまだ読み込まれていなかった）と、対象の無い kill が 1 回失敗。絵は 8 枚撮れている。
  - `rc_small`: fill コマンドが 1 回失敗（もともと草だった）。絵は 4 枚撮れている。
  - `rc_t_storm`: 初回は「カメラが着かなかった」で失敗（0 枚）。`rc_t_storm2` で撮り直して成功（18 枚）。
- **語り部のほかの NPC**: イサナ・見る者の顔は今回の撮影に入っていない（`lp_npc` は語り部だけ）。

## 撮影の記録

| 台本 | 撮影名 | 結果 |
| --- | --- | --- |
| cinematic_demo | rc_cin | SHOTS OK（17 枚） |
| heart_cinematics | rc_heart | 3 手順が失敗、44 枚（対象が無かった殺し） |
| titan_end_whale | rc_endwhale | SHOTS OK（7 枚） |
| chapter_cards | rc_cards | SHOTS OK（3 枚） |
| lp_npc | rc_npc | SHOTS OK（24 枚） |
| armor_lp_stone_whale | rc_armor | SHOTS OK（39 枚） |
| weapon_lp_ember_war_axe | rc_weapon | SHOTS OK（22 枚） |
| lp_titan_whale | rc_t_whale | SHOTS OK（22 枚） |
| lp_titan_tide | rc_t_tide | SHOTS OK（24 枚） |
| lp_titan_storm | rc_t_storm（失敗）→ rc_t_storm2 | rc_t_storm2 で SHOTS OK（18 枚） |
| lp_titan_ember | rc_t_ember | SHOTS OK（20 枚） |
| review_backs_lineup（新規） | rc_backs | 2 手順が失敗、8 枚 |
| review_small_elements（新規） | rc_small | 1 手順が失敗（床）、4 枚 |
