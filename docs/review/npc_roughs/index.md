# 都の施設の番人 (facility attendants) — 低ポリ 3 案・肖像・頭の上の印

施設ごとに一人の番人 (15 役)。旅の職人と同じ低ポリの NPC の作り (骨 root / body / head / armL / armR / legL / legR、ピボットも同じ) なので、
旅の職人のアニメ (idle / walk / talk) をそのまま使う。**案ごとにメッシュと UV は 1 つで、役の違いは絵 (アトラスの色) だけ。**
小物はメッシュに付けない (全役で同じ形のため)。腰の袋だけは全員が持つ。

## 3 案

| 案 | 一覧 | 形 | 役の色が出るところ | 三角形 (ラフ / 入れたあと) | 埋まった部品 |
|---|---|---|---|---|---|
| **A 上着と前掛け (推奨)** | `look_a.png` | 旅の職人 B の体から道具・眼鏡を外したもの。上着・前掛け・吊り紐・帯・襟・釦 | 前掛け・縁・襟・袖口・上着 | 580 / 824 | なし |
| B 長い衣と帯 | `look_b.png` | 裾が脛までの衣と幅の広い帯 (前の結びと垂れ)、髷。脚は裾の下から出る | 衣・帯・襟・裾の縁 | 550 / 762 | なし |
| C 短い上着と肩掛け | `look_c.png` | 短い上着の上に肩掛け、背へ下ろした頭巾、前の釦の列 | 肩掛け・頭巾・縁 | 580 / 746 | なし |

入れたあとの三角形は、`install_lowpoly.py` が穴をふさいで水密にした数 (旅の職人と同じ扱い)。
推奨は **A**。いまの NPC (旅の職人) と体つき・前掛け・色の置き方が同じで、並べたときに同じ世界の人に見える。前掛けの面が大きいので、役の色が遠くからでも読める。
B は祭司・書庫番のような落ち着いた役に合うが、鍛冶師や酔いどれまで衣で揃うと修道院のように見える。C は肩掛けの色がよく目立つが、旅の職人とは別の服の系統になる。

比べ: `compare.png` (3 案 x 書記・鍛冶師・女将・祭司・顔を隠した商人)。各案の詳しい絵 (前・横・後ろ・話す・顔の近く・アトラス): `<案>/attendant_<案>_detail.png`。

形を共有するので、次のことは色だけで出している:
- 顔を隠した商人: 鼻から下を布の色で塗る (口は描かない)。髪を黒にして頭巾のように見せる
- 宿の女将: 赤い髪 (布の色に見える)、唇の色とまつげの線
- 酔いどれ: 赤い鼻と頬

## 役の色

| 役 | id | 上着 (coat) | 役の色 (前掛け・衣・肩掛け) | 縁・襟 | 髪 | 肌 | 頭の上の印 |
|---|---|---|---|---|---|---|---|
| 組合の受付の書記 | guild_clerk | 灰青 | からし色 | 暗い茶・真鍮 | 茶 | 中 | 旗 |
| 鍛冶師 | smith | 炭色 | 赤茶の革 | 煤・銅 | 黒 | 日焼け | 槌 |
| 道具屋 | tool_merchant | 苔色 | 帆布の生成り | 茶・真鍮 | 茶 | 中 | 提灯 |
| 薬師 | apothecary | 薄い緑 | 白緑の麻 | 苔・若葉 | 灰 | 白い | 薬瓶 |
| 預かり番 | storage_keeper | 茶 | 錆色 | 鉄 | こげ茶 | 褐色 | 箱 |
| 宿の女将 | innkeeper | 臙脂 | 生成りの前掛け | 茜・金 | 赤 | 血色のよい | 寝台 |
| 止まり木の番 | perch_keeper | 空の灰青 | 羽の白 | 青緑 | 茶 | 日焼け | 羽 |
| 社の祭司 | shrine_priest | 白 | 朱 (脚も朱) | 金 | 白 | 老い | 鳥居 |
| 砦の老兵 | keep_veteran | 鉄灰 | 深い赤 | 鋼 | 白髪 | 老い | 盾 |
| 書庫番 | archivist | 藍 | 羊皮紙 | 暗い藍・真鍮 | 灰 | 白い | 本 |
| 師範 | training_master | 生成りの道着 | 藍 (脚も藍) | 黒 | 黒 | 日焼け | 的 |
| 顔を隠した商人 | black_marketeer | ほぼ黒 | 暗い葡萄色 | 黒・古い真鍮 | 黒 (頭巾) | 中 + 顔の布 | 面 |
| 墓守 | gravekeeper | 灰 | くすんだ苔 | 暗い灰 | 薄い灰 | 白い | 墓石 |
| 地図屋 | mapmaker | 青緑 | 地図の黄土 | 藍・真鍮 | 赤茶 | 中 | 地図と X |
| 酔いどれ | drunkard | あせた茶 | 葡萄酒のしみ色 | 茶 | 茶 | 赤ら顔 | 杯 |

色の数値は `tools/rough/lowpoly/fac/attendants.py` の `PAL` (肖像は `tools/gen_attendant_portraits.py` の `R`)。

## 肖像 (対話画面 128x128)

`portraits.png` (2 倍の一覧)。今の肖像と同じ案 A (粒子のにじむ絵画調) の描き方 (`tools/gen_portraits.py` をそのまま使う)。
頭は共通の形 (旅の職人の肖像から額の眼鏡を外したもの) で、体は低ポリの案 A に合わせて上着・前掛け・吊り紐。
役ごとに替えるのは肌・髪・服の色と、かぶり物 (布・帽・祭司の高い冠・頭巾・鉢巻・ベレー・つば広の帽)、眼鏡 (薬師・書庫番)、ひげ、羽 (止まり木の番)、傷 (老兵)。
顔を隠した商人は頭巾と、鼻から下を覆う布。表情は neutral / smile / grave。

## 頭の上の印 (16x24)

`facility_icons.png` (6 倍。最後の 3 つは今の話の印で、比べ用)。描き方は `tools/gen_npc_marker_icons.py` と同じ (暗い縁取り・左上の明るい縁・右下の影)。
色は淡い骨の色だけにして、金の「!」・青緑の巻物 (話の印) と取り違えないようにした。

## ファイル

| 中身 | 道 |
|---|---|
| ラフの生成 | `tools/rough/lowpoly/fac/attendants.py` |
| ラフの出力 | `docs/review/npc_roughs/<案>/attendant_<案>_mesh.json`、`attendant_<案>_<役>_atlas.png` (15 枚)、`attendant_<案>_detail.png`; `look_{a,b,c}.png`、`compare.png`、`attendants_report.json` (三角形・埋まった部品) |
| 入れる | `tools/install_attendants.py` (3 案とも) |
| 入れた先 | `assets/skyabyss/lowpoly/attendant_<案>.json`、`geo/entity/attendant_<案>_lowpoly.geo.json` (+ `_debug`)、`textures/lowpoly/attendant_<案>.png` (= 書記)、`textures/lowpoly/attendant_<案>_<役>.png`、`animations/entity/attendant.animation.json` (animation.attendant.idle / walk / talk)。光る画素のマスクは無い |
| 肖像 | `tools/gen_attendant_portraits.py` → `assets/skyabyss/textures/gui/npc/face/attendant_<役>_{neutral,smile,grave}.png` (45 枚) |
| 頭の上の印 | `tools/gen_facility_icons.py` → `assets/skyabyss/textures/gui/npc_marker/fac_<役>.png` (15 枚) |
| 鐘銭 | `tools/gen_bell_coin.py` → `docs/review/coin_roughs/` (別の index.md) |

作り直し:

```
python tools/rough/lowpoly/fac/attendants.py
python tools/install_attendants.py
python tools/gen_attendant_portraits.py
python tools/gen_facility_icons.py
```

## ゲームの中の絵

台本 `tools/shots/attendants.txt`（`./gradlew runShots -Pscript=tools/shots/attendants.txt --no-daemon --offline`）。15 役を前 8・後ろ 7（一段上）に並べ、案ごとに `/skyabyss facility look <1..3>` で替えて撮る。

| 絵 | 中身 |
| --- | --- |
| `ingame_look_a_all.png` / `_b_` / `_c_` | 3 案の 15 役の並び（前から） |
| `ingame_look_*_near.png` `_near2.png` `_back.png` | 近く・斜め・後ろから |
| `ingame_icons_all.png` `ingame_icons_near.png` | 頭の上の施設の印（初めて話す前なので、その上にクエストの吹き出しも出ている） |
| `ingame_dialogue_intro.png` `ingame_dialogue_menu.png` | 道具屋との会話（肖像・選択肢） |
| `ingame_shop_tool.png` `_apothecary` `_black_market` | 店の画面 |
| `ingame_stash.png` | 預かり所（バニラの大きな箱・絵柄 A） |
| `ingame_guild_none.png` `ingame_guild_mine.png` | ギルドの窓口（入っていない / 入っている長） |

- 既定は **A**（`AttendantLook.DEFAULT`）。B・C は `NpcEntity.variant` 2・3 で残してある（選び直すなら DEFAULT の一字を替える）。
- 向き: 旅の職人と同じく root に yaw 180 を足すと後ろ向きになったので、係の人は root を回さずに入れた（頭の上下の符号は `AttendantRenderer` が戻す）。**旅の職人（`skyabyss:artisan`）は今も後ろ向き**（`docs/review/npc_roughs/ingame_facing_check.png`）。
