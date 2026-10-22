# 引き継ぎ (2026-10-21 時点)

「天と淵のあいだ」(NeoForge 1.21.1)。今の仕様は `docs/design/README.md` の索引から読む（コードが正）。未決は `docs/design/00_decisions_needed.md` にだけある（エージェントは触らない。番号付けは親）。リポジトリは github.com/Nishi-Yura/skyabyss、作業の幹は `m1-sky-pass`、途中の枝は `wip/*`。

## 1. 各システムの状態

| 系 | 状態 | 書類 |
| --- | --- | --- |
| 序盤（序章・一章前半・拍子・目的の帯・知らせ・場面） | できている。試遊の声で何度か直した。語りと静かなホラーを最初から | 06・17・18・20 |
| 四体の巨神と背（岩鯨・潮殻・雷翼・余燼） | 四体とも遊べ、背は水平 ×1.6・垂直 ×1.3 に作り直し済み | 02・08・12・13・26 |
| 試練のプール | 基盤・板・図鑑・称号・今週の試練・深淵は実装済み。**四体とも十二（48 すべて）実装済み**（`trial/TrialCatalog`） | 29 |
| はじまりの都（33 の W1） | **できている**: 鐘の宿りは都の設計図 `capital`（`tools/capital/town.py` + 建築の部品集 `tools/capital/vox.py` → `data/skyabyss/opening/capital.json`。runs 形式、約 40 万ブロック、建つまで約 15 秒）。作風 B「灰石と緋の屋根」（2026-10-21 決定）。壁 (高さ 12、歩廊、外に塔) と門楼つきの四門、北の段 (大鐘楼 46 段・語り部の家・砦・八角の記録の塔の館・庭)、広場と丸い市、組合、石の輪の社、鍛冶場、街区ごとに通りに面して並ぶ町家 (店・張り出し窓・露台・旗・小塔・中庭)、空き区画 5、西門の外の墓地。**施設は 35 の 2.1 見取り図 A で建て直し済み（2026-10-21、`tools/capital/facilities.py`）**: 道具屋・預かり所・薬師の店・宿「鐘の宿」(客室 9)・子の止まり木・訓練場 (はじめから開いている)・闇市・地図屋 (屋根裏)・酒場と地下・古井戸・墓守の小屋、組合と鍛冶場に受付の机。係の人 15 役の印すべて、大通りの 1 列目は入れる家 (町のクエストの家 11)、入れない家は 2 列目より奥の 43 棟 (前は 83)。撮影 `tools/shots/capital_rebuild.txt`、絵は `docs/review/town_roughs/rebuild_a/`。地下の部品は `cave_air` で書く (`air` は書き出されない)、整地の床下埋めは地下のある列を飛ばす (`Blueprint.deepColumns`)。碑 26、村人 10、イサナの野営 (印 `chaser`)。場所は野営地から 260〜340（だめなら 240〜480、最後は段差最小の所）、水は 1 割まで埋める。閉じた施設は章で開く（`opening/site/CapitalDoors`）。碑を見ると序章の伝承 `prologue/capital_*` 4 項目、語り部は碑を一つ読んだあと「見たもの」→「この都は、どこも戸が閉じている」で一度だけ。自己テスト `opening`・`opening (capital doors)`・`lore`・`npc` 通過。撮影は `tools/shots/capital.txt`（開発コマンド `skyabyss dev opening at shelter 0 <x> <y> <z> <yaw> <pitch>` は設計図の座標で置く）、ブロック名の検査は `python -I tools/capital/check_states.py <設計図>`。**注意**: 設計図を変えたら自己テストの前に `run/world` を、撮影の前に `run/shots/capital/saves` を消す。古い台本は `SKYABYSS_SMALL_SHELTER=1` で小屋のまま撮れる。**作り込み（2026-10-21 夜、枝 `wip/capital3`）**: 町のクエストの家 11 は住み手ごとに中身が違う（書き役・年寄りの船乗り・機織り・漁師・子だくさんの家族・パン焼き・鍛冶場の弟子の下宿・薬草摘み・元兵士・吟遊詩人・喪の家。35 の 6.8、`tools/capital/interiors.py`）、窓から見える施設の二階にも家具、宿の二階の廊下は区切りの梁・明るい天井・両端の窓・灯り・小卓・踊り場の席。古井戸の下の迷路と宝（縁の内のレバーで格子が開く。印 `well_down`・`well_chest`）、酒場の地下の奥の隠し通路と封じた戸（壁は閉じたまま。印 `cellar_gap`・`cellar_passage`・`abyss_hint`・`abyss_door`。35 の 6.9、`backalley.py`）。訓練場ははじめから開いている（`CapitalDoors` から外した）。都はバニラの村・構造物から 176 離して置き、建てたとき範囲の村人・鉄の番人を外す。建築の監査の浮きの検査は地下のある列だけ飛ばす（`SiteAudit`）。撮影 `tools/shots/capital_rebuild2.txt` → `docs/review/town_roughs/rebuild_a2/`。**まだ**: 作り込みは参考画像の水準の手前（一棟ずつ部品を足していく）、壁の外の地形はならしていない、閉じた施設の中身（名簿・鍵の間・記録の間・書庫）は空 | 33・17 の 3.3・35 |
| 世界の背骨と地図・章の筋 | **採用（2026-10-20）。W1（都）・W2（方位の跡地と祠の置き場）・W3（M の地図）と 34 の C1（一章）・C2（二章の地上）・C3（三章の地上。34 の 4.4.1）・C4（四章の地上。34 の 4.5.1）は実装済み、W4 と 34 の C5 は未実装**。33 = 世界はバニラのまま、町（約 200×200、ラグニ級。壁・門・空き区画）と跡地と祠だけ固定（方位 北東西南・距離の帯）、M の地図は印だけ。34 = 四章ぶんの物語の背骨と、支度の段でバニラの進み（鉄・船・銅と雷・ダイヤ・醸造）を通すメインクエスト。33 は W1（都。**「絶えかけの国の都」で決定**: 建物は 33 の 2.2、章ごとに施設が開く。見取り図は第 2 回の案 A「鐘の都」で決定。砦は見張りの砦・村人は少し・国の名は「鐘の国」。次は W1 の R0（17 の 3.3・01・05・07 の本文）→ 目覚めと都の場所の選び方 → 都を建てる（建物ごとにラフ 3 案））、34 は C1（一章の骨の坑と支度の段）から。32 の R1 とは独立 | 33・34 |
| 章の跡地（33 の W2。2026-10-21） | **できている**: 都から決まった方位と距離の帯（北 280〜460 岩鯨・東 800〜1,400 潮殻・西 1,000〜1,800 雷翼・南 1,200〜2,000 余燼）に、章ごとに世界で一つの跡地（`opening/site/ChapterGrounds`。扇 ±25° を内側から 32 刻み、村などの構造物は 192 避ける）。合う地形がなければ帯の中のいちばん乾いた所に形を整える（丘 96・入り江 160・峰 128 で +60・灰の盆地 160。`ShapeJob`。一章は目覚めの前、二章以降は章が開いたとき）。章の地上の場所（21）は跡地に世界で一つ（`ChapterSites.Builder.atGround/atRoad`）、旅の跡・荷車・日記だけ拠点のまわり。都から跡地への石標の道しるべ（40 ごと。一章は鯨道の石標 7）。一章の骨は北の丘の中。潮殻の干潮の寄りと潮見の貝は東の浜だけ（`titan/tide/TideBeach`）。地図に跡地の噂→見た。**祠の口**: `ChapterGrounds.shrineSpot(server, titan)`（32 の `TitanShrine.ensure` が使う）。雷翼の止まり木と風見鶏は西の峰だけ（`titan/storm/StormPeak`）、余燼の浮上と温石は南の灰の地だけ（`titan/ember/EmberBasin`。どちらも中心から 256 以内に人がいるとき）。道しるべは低い平地・浅い水（深さ 10 まで）でも置き、水の上は石の台を水底から立てる（`SiteJob` の pillar。小さな小要素も動かし尽くしたら台の上に建てる）。丘と峰の裾は元の地面へなだらかに寄せる（切り立った壁を作らない）。M の地図に祠（心臓を止めた人に）。自己テスト `chapter grounds`、開発コマンド `skyabyss dev grounds info|prepare|shrine|go`、撮影 `tools/shots/chapter_grounds.txt`（絵は `docs/review/world_roughs/grounds_w2/`）。**まだ**: 序盤の場所の計画が跡地の探しで 15〜30 秒ほど長くなった（別のスレッド）、撮影の台本のカメラの何か所かが木や地面の中になる（世界の種しだい） | 33 の 3 章 |
| 旅の地図（33 の W3） | **できている**: M で開く印だけの地図（`client/map/TravelMapScreen`、北が上、町が中心、縮尺 600 / 2,400 をホイールで）。印の出どころはサーバーの `map/MapMarks` 一か所（町・野営地・足跡・骨・積み石は序盤の拍子から噂/見た、着いた章の場所、覚えた碑、出会った巨神）+ クライアントの自分・仲間・墓・目的。子の海図は同じ画面に重なる（旧 `TravelChartScreen` は消した）。見た目は設定 `mapStyle`（SLATE 既定 / PARCHMENT / INK。`docs/review/map_roughs/`）。合図「ここ」のキーは M から ' へ移した。自己テスト `travel map`。**まだ**: 32 の祠・言伝ての板の目印（`MapMarks` に足す。W2 の四つの跡地は入った）、4.2 の墨の洗い（W4） | 33 の 4 章 |
| 二章の支度の段と地上（34 の C2。2026-10-21） | **できている**: メインの段 `ch2/salt_road`（塩の道。語り部。標 5 → 沈んだ塩の蔵の奥の刻み + 潮の番人）・`ch2/ebb_boat`（船と貝。イサナ。船 → 沖の難破船の箱で潮の民の頁 + 潮見の貝）が、メイン「満ち引きの門」の噂のあとに二本とも出る（順不同。`PrepQuests` の章の表、二本で `ch2_ready`）。ミニダンジョン**沈んだ塩の蔵**（`story/ch2/SaltVault`。部品 `ch2_salt_vault`。潮の喉は世界の潮で乾く/沈む（どちらでも通れる）・入口の間・塩樽の間（樽 3 を乾いた棚へ → 塩の戸）・奥の間（膝の浅瀬、前の私の刻み）・出口の梯子）、塩の道の標 5（浜から蔵へ。チャンクが読み込まれたときに建つ）、潮だまりの浜 2、漂着の筏の跡（職人の二章の立つ所）、引き潮の観測の杭、沖の難破船（`story/ch2/Wreck`。バニラの型を跡地の沖の浅瀬に一度だけ）。サブ `ch2/drift_trail`・`ch2/wader_gear`、日替わり `daily_pool_sweep`、頁 4、語り部の報告に村の記憶と数え歌のふたつめ、進捗 `salt_vault`・`tide_trail`。生成は `tools/gen/ch2/gen_ch2_ground.py`。自己テスト `ch2 ground`。開発コマンド `skyabyss dev ch2 vault|place|tide|door|mark|wreck`。撮影 `tools/shots/ch2_ground.txt`（`docs/review/ch2_ground/`）。地上の敵 4 と品は「二章の地上の敵と品」の行。**まだ**: 実機の跡地での通し（蔵の向き・標の並び・難破船の浅瀬が世界ごとに合うか）、潮見の貝の「刻みを読む」稽古（21 の NPC の筋） | 34 の 4.3.1・4.3.2・21 の 4.1 |
| 二章の地上の敵と品（21 の 4.1。2026-10-21） | **できている**: 敵 4（`entity/TidepoolClam`・`SandBurrower`・`DriftHermit`・`ShoreWarden`。水は `ShoreFlood`）。潮だまり貝は閉じた殻で待ち、近づくと 0.8 秒で開いて挟む。砂潜りは潜っている間は見えず傷も受けず、真上で砂が盛り上がって噛む（浜のひとり歩きは干潮だけ。蔵の湧き台は潮に関係なく出る）。宿借りは敵意なしの行列。潮の番人（名づき）は銛の突き 1.1 秒と満ち潮の輪 1.2 秒（床が 5 秒だけ一段沈み、必ず戻る）。ひとり歩きの登録は `story/ch2/Ch2Mobs`、素材は `tools/gen/ch2/gen_ch2_ground_mobs.py`、ラフは `docs/review/ch2_roughs/`。品は `guide/Ch2Gear`（`tools/gen/ch2/gen_ch2_gear.py`）: 素材 4（小真珠・塩結晶・真珠の護り糸・貝の欠け。持ち物の品で旅嚢には入れない）、潮渡りの旅装・塩の砥石・潮の塩漬け・真珠の粉薬・潮の刻みの砂時計、レシピ 8 本（二章の旅の覚書で開く）。潮殻の鼓動守は職人が護り糸から編む（人ごとに一度、`ch2t_woven`。編み直しは塩結晶 8。`HeartbeatWard.WEAVES`）。二章の記念品はない。自己テスト `ch2 mobs`・`ch2 gear`・`travel gear`。**まだ**: 実機の浜での湧きの数と潮の切り替わりの通し | 21 の 4.1・1.2 |
| 一章の支度の段（34 の C1。2026-10-21） | **できている**: メインの段 `ch1/trail_iron`（鉄の跡。イサナ）・`trail_blade`（折れた剣。職人）・`trail_shield`（盾と糧。語り部）がイサナの鯨の話のあとに三本とも出る（旧い `ch1/prep_*` を吸収。`PrepQuests` の章の表）。新しいミニダンジョン**骨の坑**（`story/ch1/BonePit`。坑口の肋骨と碑・水没の坑道・前の私の荷・冷えの戸・出口の梯子。部品 `ch1_bone_pit`）、目的 `wear`（着ている防具の数）、頁 4、記念品「鯨追いの印」（`whale_chaser_mark`。案 B）、進捗 `bone_pit`・`whale_trail`、目的の帯の行き先（骨の坑・洞）。生成は `tools/gen/ch1/gen_ch1_trails.py`・`tools/gen/ch1/gen_ch1_sites.py`・`tools/gen/ch1/gen_ch1_trail_assets.py`。自己テスト `ch1 p6`（骨の坑と三本の流れ）ほか。撮影 `tools/shots/ch1_bone_pit.txt`。骨の坑・割れ殻の洞・見張りの丘・歌石・骨の集め場・骨は一章の跡地（北の丘。W2）、鯨道の石標は都から北の丘への道。**まだ**: 三本を済ませないまま二章へ進むと、三本は進行中のまま残る | 34 の 4.2.2・21 の 3.1・07 の 6.8 |
| 巨神の順路（レイド化） | **R1〜R4 実装済み（m1-sky-pass に合流済み）**。歩 → 智 → 闘 →（中ボス）→ 心臓の順路（`trial/TrialRoute`）。越えると 4 秒後に参加者を次の段の入口の休み場へ。初回は入口の箱に全員で入ると始まり、周回は地上の巨神の祠（`titan/shrine`）で歌片を一つ使う。今の段のセルの戸だけが開く。中ボスの原型を引いていない周は段 3.5。あきらめは全員の同意で背の外へ。区画（`TrialSlot`）に新作のセルを写して、グループごとに遊ぶ。巨神と乗り込み口は章の当事者とそのパーティにだけ見える（`titan/TitanVisibility`）。鼓動の間の板・碑・封印・今週の試練・任意の翼・既知の道・抜け道・背の炉は消した（版 30）。**R5 済み**（枝 `wip/route5`）: 周回では原型の十二すべてが長い（`trial/RouteLength`。初回は今の長さ）。W09 の波・T05 と S06 の二つ目の配置・T09 の巣 8・E05 の鍵二本・E09 の火を二度・W05 は灯を一度に二つまで（中ほどの苗床は眠る）・S05 は戸ごとに雷を二度（回路が組み変わる）・T01 は真珠を二つ（道の形が組み替わる）・W01・S01・E01 は前の道（`trial/LongWalk`。前の谷 WP・風の渡り・前の湖。背の版 31）。**R6-lite 済み**: 一人の通しの撮影 `tools/shots/raid_route_walk.txt` と自己テスト `trial walk`（`docs/review/raid_route/notes.md`）。運ばれた人は見通しのきく向きを向く。背の建設と区画の写しは tick に分けた（`BackBuildJobs.Steps`・`BackReach.RepairJob`・`TrialSlot.buildSoon`）。**2026-10-22 の二人の試遊から**: 背の中は歩いてつながらない（Wynncraft のレイドの形。入口の箱の先と、試練をつないでいた広間の口を閉じた戸で封じる。`trial/RouteSeals`、自己テスト `route rooms`）。背の中では耐久が減らない（`mixin/ItemStackDurabilityMixin`）。心臓の硬さを下げた（岩鯨・余燼 0.8 倍、潮殻 0.91 倍、雷翼 0.88 倍。一人 約 200〜225 秒）。印は赤だけ（安全な所の印は出さない）、技は壁で止まる（`Telegraph.walled`）と壁を抜ける（`piercing()`。濃い赤の破線。領域の決まり）に分けた。領域の決まりの印を細くした。起こすのは 1.5 秒・地上でも倒れて待つ（`party/Downed`）。人が当てた敵の無敵は 0 tick（`combat/MobHitFrames`。自己テスト `combat rules`）。**route6（枝 `wip/route6`）**: 封じた広間（岩鯨の鼓動の間と入口の通路・潮殻の潮の広間・雷翼の風の広間と尾の間）の飾りと灯りを外した（版 32。形は残す）。広間へ人を戻していた所（グループが変わった・リセット・心臓のあとの光・記録の無い復活）は到着の台へ（`RouteSeals.safeFeet`）、戦いの前室で倒れてもその休み場で目覚める（封じた広間に閉じ込められる不具合だった）。運ばれるのを待つあいだの帯は「まもなく〇〇へ運ばれる」（`RouteState.carrying`）。試練の文は「ゴール」→「光る印」、画面は常体、全角の括弧。背の裏の建設はチャンクを先に読んで建て終わるまで残し（`BackBuildJobs.loadArea`・`hold`。外すのは 400 tick 後）、形の読み込みは別スレッド（`afterAsync`）で、いちばん長い tick は 余燼 0.39 秒・岩鯨 0.28 秒・雷翼 0.18 秒・潮殻 0.14 秒（前 2.3 / 0.57 / 0.93 / 0.82 秒）。自己テスト `back builds`（建て終わるまで待つ）・`route party`（二人の一行）。二人の本物の通しはまだ | 32 |
| 心臓・領域・遺物の選択 | 領域は三つの仕掛けを解くまで続く。心臓を止めると巨神は眠り、遺物は専用画面で選ぶ | 02・30・20 |
| 巨神の子と旅 | 笛で子を呼び、パーティ全員で乗って海図から行き先を選ぶ。成体は動かない | 09・28 |
| 成長・スキル | 5 つの木＋旅人の 36 技。天職は専門。タグ・セット・連鎖・構え・熟練・キーストーン・試着・型 4 つ。変異・橋・紋の盤・前世の技・淵の木 | 27 の 17〜18 |
| 装備 | 13 武器、特性、炉（強化・分解・再鍛造・交換・刻み）。穴・欠片・技のルーン・アスペクト・世代。深淵の専用伝説 4 点 | 03 の 3.6〜3.7 |
| 転生 | Lv 50+n（上限 20）、魂盤・恩恵・祖の力・導きの欠片・色と記号・称号 24・順位の石・飾り板・墓の飾り | 31 |
| 旅嚢・死・墓・パーティ | 旅嚢（品ごとの固定枠、倒れても残る）、柔らかい死（墓から立て直し）、倒れた仲間の蘇生、6 人まで | 22・11・10 |
| モデル | 低ポリのメッシュ（水密・関節は親に沈める）。平らな絵は 32×32 | 15・16 と tools/README |
| サイト | `site/`（GitHub Pages、日英、ドット絵のみ）。`python tools/site/gen_art.py` の後に `python tools/site/build.py`。生成物の `site/*.html` もコミットする | tools/site/ |
| 画面と札（2026-10-21 夜） | **決定（2026-10-21: 札 B 左の帯の下、進捗のチャットは残す、絵柄 A 暗い板）**。札（獲得・進展・クエスト・伝承・バニラと mod の進捗）は `client/notice/CardFeed` の一つの出口、置き場は設定 `cardPlace`（既定 LEFT_FEED = 左の目的の帯の下に一行ずつ）。バニラの進捗の札は `mixin/ToastAddMixin` で mod の札に、レシピ・操作説明の札は出さない。バニラの画面は同梱のリソースパック `skyabyss_ui_a/b/c`（`tools/ui/gen_vanilla_skin.py`、既定 A をはじめての起動で選ぶ。`client/ui/UiSkin`）。見出しの文字・ツールチップの色も絵柄に合わせる。撮影は `SKYABYSS_UI_SKIN=a|b|c|none` | 18 の 3.1・3.2 |
| 三章の地上（34 の C3。2026-10-21、枝 `wip/ch3`） | **できている**: 西の峰の跡地に避雷の塔の跡（部品を `ch3_rod_tower` に替え、台 4・銅の戸・石の箱・避雷の見本）・風の道の旗 5・焼けた見張り塔（三階の小さなダンジョン。敵は雷翼の背の敵で代用、頂の柱の雷、焼けた羽守 = 羽守 + 体力 190）・雷拾いの原・風読みの崖。支度の段 `ch3/lightning_pillars`・`scorched_watch`・`sky_stone`（順不同、三本で `ch3_ready`・進捗 `storm_trail`）とサブ `ch3/wind_flags`、頁 4、語り部の数え歌「みっつ」、イサナの決意（俺は残る・温石）、職人の三章の立つ所（塔の野営）、眼の間の遅れた影（`MirrorLag`、クライアントだけの人影）。生成 `tools/gen/ch3/gen_ch3_ground.py`、自己テスト `ch3 ground`、撮影 `tools/shots/ch3_ground.txt`（絵 `docs/review/ch3_ground/`）。**新しい敵と品（2026-10-22、枝 `wip/ch3extra`）**: 地上の敵 4（帯電毛玉・峰の雛・雷拾い・焼けた羽守。`titan/storm/chapter/Ch3Mobs` と `mob/`。塔の湧き台は雷翼の背の敵の代用をやめた）、品（峰越えの旅装・雷の砥石・風干しの団子・避雷の油・風読みの小旗・素材 4。`guide/Ch3Gear`）、雷翼の鼓動守の編み方（職人、`dialogue/ch3/artisan_gear.json`）、塔・石の箱・原の箱に三章の素材。どれもラフ 3 案から仮の選定（`docs/review/ch3_roughs/choices.md`）。自己テスト `ch3 mobs`・`ch3 gear`、撮影 `tools/shots/ch3_extra.txt`（絵 `docs/review/ch3_extra/`）。**まだ**: 備えの頼み `ch3/prep_*`・`ch3/gleaner_trail`・日替わり `daily_puff_clear`、眼の間の影の撮影 | 34 の 4.4.1、21 の 5.1 |
| 四章の地上（34 の C4。2026-10-22、枝 `wip/ch4`） | **できている**: 都の南の灰の地の跡地に灰の道の標 5・消えた炉の坑（地下三部屋の小さなダンジョン。熾り蟲 4 と温石の台・灰被りの屑 2・古い灰被り（名づき。殻 40、亀裂・熱の波・扇の火掻き）、炉の壇の碑と前の私の荷、出口の梯子）・温かい岩の群れ 2・煤の窯の跡（灰ひきずりの巣）・灰降りの高台。支度の段 `ch4/fire_draught`（耐火のポーション 2 → 職人）・`ch4/forge_ruins`（坑の頁 → 語り部）（順不同、二本で `ch4_ready`・進捗 `dead_forge_pit` → `ember_trail`）とサブ `ch4/ash_road`・`ch4/dragger_trail`・日替わり `daily_mite_clear`、メイン「灰の昇る山」に「乗る」の段、天の鍵は語り部とイサナの二人の答え（よっつめ → いつつめ → 返せ / 眠らせてよかったのか）で終わる。灰の鍛え手の一言「返しに来たか」、頁 4、職人の四章の立つ所（坑の野営）・覚書・余燼の鼓動守の編み方（灰の護り糸）。新しい敵 4 種（`story/ch4/mob`。選定済み: 熾り蟲 A・屑 C・灰ひきずり A・古い灰被り A）と品（煤鉄・灰麻・灰の護り糸・煤の砥石・灰の冷やし水は選定済み、灰歩きの旅装 4 部位と灰焼きのパンは仮の A。`story/ch4/Ch4Gear`、`tools/gen/ch4/gen_ch4_gear.py`）。目的の帯の `品@効き目` の数え方（`QuestItems`）。生成 `tools/gen/ch4/gen_ch4_ground.py`・`tools/gen/ch4/gen_ch4_ground_mobs.py`、自己テスト `ch4`（`ch4 ground`・`ch4 mobs`・`ch4 flow`・`ch4 ground (sites)`）、撮影 `tools/shots/ch4_ground.txt`（絵 `docs/review/ch4_ground/`）、ラフの選定ページ `docs/review/_gallery_ch4/`。**まだ**: 灰歩きの旅装と灰焼きのパンのユーザーの選定 | 34 の 4.5.1、21 の 6.1 |
| 都の施設（35 の F2・F3。2026-10-21、枝 `wip/fac`） | **仕組みはできている**: 係の人 15 役（`facility/`。エンティティ `skyabyss:attendant` 一つに役。会話 `attendant_<役>`、初めは一言の説明 → できることの選択肢、頭に施設の印、見た目は低ポリ 3 案で既定 A）、鐘銭 `bell_coin`（頼み・敵の落とし物・売り）、店（`data/skyabyss/shop/`、道具屋・薬師・闇市の日替わり、画面 `client/facility/ShopScreen`）、預かり所（人ごと 54 枠）、宿の寝台（目覚める場所）と噂の入口、止まり木の番（子を呼ぶ・地図）、鍛冶師の修理、プレイヤーのギルド（作る・申請・抜ける・30 人・名札の [印]・`/guild`）。生成は `python tools/gen/world/gen_facility_content.py`。自己テスト `facility`。都の設計図が印 `npc_<役>`（15 役）・`inn_bed`〜`inn_bed_9` を置く（35 の 6.6）。**F4 と章で開く施設（2026-10-21、枝 `wip/fac2`）**: ギルドの位（長・副長）・倉庫 54 枠（取り出せる位を長が決める）・週替わりの頼み（6 種、済むと鐘銭 20・共鳴の欠片 1）・旗の作り直しと旗の品、訓練場（はじめから使える）の師範の振り直し（結晶か鐘銭 30 = 結晶 1）と木偶の数字、祭司の誓いの説明と鍵の間（欠片を納める。控え帳の在りか `key_room`）、老兵の試練の記録の画面、書庫番の伝承の読み返しと地図の写し（祠と跡地）、薬師の醸造の手ほどき（四章）。自己テスト `facility extras`・`facility guild`、撮影 `tools/shots/facilities2.txt`。**取引所とギルドの続き（2026-10-21、枝 `wip/trade`）**: 取引所（`facility/trade/`、係の人 `trade_keeper` で 16 役。出す品は預かる・申請で品は動かない・受けた瞬間に一度に入れ替え・いない人は受けられない・受け取り箱・いない人への知らせ・`/trade`。画面 `client/facility/TradeScreen`。35 の 6.11）、ギルドの旗の模様 28（はじめから 21）と名札の特別な色 4、功績 10（`GuildAchievements`）、長は抜けられない（譲る・解散。二度押し）、ギルドの家と空き区画の仕組みは消した（都の保護は都の中を全部守る）。自己テスト `facility trade`・`facility guild`、撮影 `tools/shots/trade.txt`。**まだ**: 前の私の頁（三章の中身）、笛の手入れ（笛に手入れの中身がない） | 35 の 6 章 |
| 町のクエストと裏通り（35 の F5・F6。2026-10-21、枝 `wip/townquest`） | **できている**: 町のクエスト 8 本（一〜四章に 2 本ずつ: 見習いの鯨追い・鐘守の頼み・漁師の網・旅の吟遊詩人・雷の夜・機織りの針・灰の弟子・老婆の薬鍋）。宿の女将の噂で始まり、進めている人が都にいる間だけ限定の人（`skyabyss:town_guest`、8 人、保存しない）が町のクエストの家に立つ。報酬は鐘銭と品。裏通り: 墓守の名のない墓 4 段（見る者のほのめかし。六章まで続ける）、酔いどれ 3 段（深淵の手がかり。四章で酒場の地下の奥の壁が開く）、地図屋の噂と描き足し（未見の跡地を M の地図へ、鐘銭 5）、古井戸の宝（一人一度）。仕組み `town/`、生成 `tools/gen/story/gen_town_quests.py`・`tools/gen/mobs/gen_town_guests.py`、コマンド `/skyabyss town …`、自己テスト `town`、撮影 `tools/shots/town_quests.txt`。**まだ**: 大鐘楼の頂へ上がれない（鐘守の頼みは `/skyabyss town bell` でしか済まない）、古井戸の下の迷路と `well_chest`、地下の通路の `cellar_gap`・`abyss_hint` は建物の担当が作った（35 の 6.9） | 35 の 3.3・3.5・6.10 |
| 文のネタばれ・クエストログ・背までの近道（2026-10-21 の試遊。枝 `wip/textpass`） | **できている**: 目的・クエスト・知らせ・進捗の文から「これから起きること」を抜いた（鎖を待とう → 空の岩鯨を、追いかけよう、など。岩鯨の鎖の待ちの帯は一本だけ）。進捗は足跡・空の影・骨の丘・岩鯨の心臓を hidden に（鐘の宿りは野営地の下、岩鯨の背はイサナの下へ）。クエストログの終えたものは、押すと日・終わりの書き付け・やったこと（段ごとの ■）・受け取った報酬が出る（`quest/QuestDoneView`）。章の見出しは押すと最初のクエストを開き、一覧はホイールで送る。岩鯨は初見（B10）のあとすぐ降りる（`story/ch1/Ch1Descent`: 見たか・場面の外かだけ。日・支度の本数で止めない。17 の 2.8）。見つけてから背まで約 2〜3 分（前は早くて約 10 分、支度なしで約 50 分）。生成元 `tools/gen/story/gen_opening_text.py` は今の lang とずれていて走らせると多くを消すので、文は生成元と json の両方を手で直した（直すときも同じに）。自己テスト `npc`（終えたクエストの頁）・`ch1 p6`（降下の条件と長さ） | 17 の 2.8・07 の 6.3・19・02・21 の 3.2 |
| 物語の運び・メインの一本化・文の見直し（2026-10-22 の二人の試遊。枝 `wip/story`） | **できている**: 一章の支度の段は鉄の跡 → 折れた剣 → 盾と糧の順に一本ずつ（二章は塩の道 → 船と貝、三章は避雷の柱 → 焼けた見張り塔 → 空の石。前の段の終わりで次の依頼主を指す）。親のメイン（`main_song`・`main_tide`・`main_thunder`）に段 `prepare`（目的 `condition` の `prep`。巨神を見た人は飛ばす）。**岩鯨の初登場は一章の支度 3 本のあと**（一章の山場。`story/ch1/Ch1Trails.callReady` + `TitanEvents` + `OpeningDirector.readyPlayer/preparedPlayer`。そろってから 30 秒で、夕方を待たない。パーティの仲間が済ませていればよい。5 日目の詰み止めも支度は要る）。目的の帯はメインを一つだけ出す（支度の段を先に。`ObjectiveStrip.current`）、メインは追跡の札に出さない（同じ文が二つ出ていた不具合。帯が序盤の目的を出しているときに追跡の札がメインの同じ文を重ねていた）。メインの段の書き出しに一言（`<段>.lead`。`QuestClient.leadKey`。字幕に一度）。全名前空間の文の見直し（170 か所。`docs/review/text_review_2026-10-22.md`）: 心臓は「止める」、鱗片と欠片、語り部「あたし」・イサナ「あんた」、画面は常体、傷・ブロック・旅嚢、バニラの品名、巨神の子が運ぶ。文の直しは生成元に入れて流し直した（`gen_opening_text`・`gen_free_quests`・`gen_story_quests` の分は lang を直に）。四章と試練・順路の文は触っていない。自己テスト `lang,objective band,npc,lore,opening story,opening beats,ch1,chapter grounds,freequest,town,storm chapter,ch2 ground,ch3 ground` | 34 の 3・4.2〜4.4、17 の 2.8・4.3、07 の 6.8、21 の 3.2 |
| 巨神の乗り込み・笛・見え方・持ち方（2026-10-21 の試遊。枝 `wip/titanfix`） | **できている**: 笛はいつ吹いても子を呼ぶ（待ち・戦闘中・水の中・ほかの乗り物・巨神の眠りで断らない。遠くの空の子は呼び直す。出る所はゆるめて最後は足もと。断るのはオーバーワールドの外・場面の最中・出会っていない巨神・仲間を運んでいる子・稚貝で 64 内に海がないとき。28 の 3.2）。心臓を止めた人から巨神が消える（判定を止めた時に捨て、送り先を 1 秒ごとに合わせ直す `TitanVisibility.resync`。運び出された人は眠りが終わるまで見る `witnessRest`。32 の 3.3）。潮殻は甲羅に触れると潮門へ渡る（休んでいなくても。`boardOnTouch`）、休むと岸に浜の乗り場の光の輪（`tide_shell#shore`。08 の 2.4）。余燼は口の判定と通り道を広げ、口が開いているあいだ頭に触れれば口へ（13 の 2.4）。雷淵の弓は引き絵の印 (pull/pulling) を登録（引く間の持ち方が直った）、底なしの大剣はメッシュを y 軸まわり 180 度（背と刃が鯨骨の大剣と同じ向き。`tools/install/install_lowpoly_abyss_weapons.py` の `TURN_Y180`）。比べ絵 `docs/review/gear_fix/`、台本 `tools/shots/gear_fix.txt`。自己テスト `tide`（甲羅に触れて乗る）・`ch2`（浜の乗り場を歩いて乗る）・`young`（眠っていても呼べる・遠い子の呼び直し・待ちなし）・`trial shrine`（眠りの見届け）・`ember surface`（広い口）。**注意**: 空の影（06 の 2 章）はワールドの出来事のままなので、岩鯨を止めた人にも通る（決めるなら 00 へ） | 08・09・13・28・32 |
| 最適化（2026-10-21 夜） | 見た目・中身を変えない形で: HUD の配置の計算が入力の同じフレームで飛ぶように（Fit を入力で比べる `Fit.keyed`）、ボスバーの持ち主探しを tick ごとに一回（`client/BossOwners`）、光の筋の 128KB の置き場を使い回し、クエストの札の組み立てを一フレーム一回、蛾と野営の探しはチャンクの区画の表で先に見る（`util/SectionScan`）、装備の打ち消しの早抜け。残りの候補は見直して不要と判断（`EmberTrials` は人のいる部屋だけ・小さな箱だけ、`VowEvents` は毎秒の型の確認だけ）。`GuideTrailRenderer` の床探しは効果が小さく未着手 | — |

## 2. 動かす・試す・撮る

- **鉄則**: `gradlew --stop` は使わない（ユーザーの試遊クライアントが落ちる）。いつも `--no-daemon`。ゲームもサーバーも同時に 1 つだけ。終わったら自分で起動した java を PID で止める（ユーザーの CurseForge の `javaw.exe` と、動いている他のエージェントのプロセスは止めない）。
- **メモリ**: `gradle.properties` は `-Xmx1G`。重い python（レンダラ・メッシュ処理）は 1.5 GB 以内で、並列にしない。
- **ユーザーが試遊中のチェックアウト（Desktop\mod）では絶対にビルド・編集しない**（`build/classes` が差し替わり ClassNotFoundException で落ちる）。確認は worktree でする。
- 型検査: `./gradlew compileJava --offline --no-daemon`。
- 自己テスト: `SKYABYSS_SELFTEST=1 ./gradlew runServer --no-daemon`（全体は約 10 分。`SKYABYSS_SELFTEST` があるときだけ build.gradle が `run/server.properties` の `max-tick-time` を -1 にしてウォッチドッグを切る。巨神の着地の段がチャンクの同期生成で 1 tick 200 秒かかるため。**自己テスト中は重い python を並列で流さない**。`build audit` の地上の場所の指摘は、2026-10-22 の仕上げ（`SiteJob.settle`）で 5 つの種とも 0。繰り返しは `SKYABYSS_SELFTEST_ONLY=<段の名前の頭>`、例 `abyss`・`gear freedom`・`whale scale`。全体は最後に 1 回）。
- 絵の確認: 台本で撮る `./gradlew runShots -Pscript=tools/shots/<台本>.txt --no-daemon`。出力は `run/shots/<台本>/shots/<台本>/`、最後の行が `SHOTS OK` なら成功。台本の書き方は `tools/README.md`。**OS のキー送り・フォーカス操作はしない**。音は自動で 0、言語は日本語（`SKYABYSS_LANG` で変更）。
- マルチ: 専用サーバー + 二人のクライアント。ポートは `server-port=25666`・`rcon.port=25766`（パスワード `test`、`online-mode=false`）。worktree が並ぶときは番号を変える。
- 試遊用コマンド（OP 権限 2）: `docs/playtest_commands.md`（`/skyabyss playtest now|next|board|goto|trials|gear|chapter|fast|reset`、`skyabyss dev trial|rebirth|…`、`skyabyss skill …`、`skyabyss dev backmetrics`）。
- 構造は歩かずデータで確かめる: `build audit`・到達の検査・`titan back`（`tools/README.md` の「構造をデータで確かめる」）。
- ユーザーが通しの試遊（全クリア・2 人・周回の時間）を自分でやる。エージェントはやらない。

## 3. 配置の版（`LAYOUT_VERSION`。上げると古い島を消して建て直す。試練の進みは戻り、初回の記録 `keep.*` は残る）

| 巨神 | 版 | 最近の変更 |
| --- | --- | --- |
| 岩鯨 `TitanBackLayout` | 30 | 版 30 で任意の翼・抜け道・鼓動の間の板と碑・背の炉を消した（32 の 7 章） |
| 潮殻 `TideBackLayout` | 30 | 版 30 で息の間・ひれの道・殻の上の寄り道・抜け道・背の炉を消した |
| 雷翼 `StormBackLayout` | 30 | 版 30 で任意の翼（塔・庭ほか）・抜け道・背の炉を消した。試練の甲板は残す。`RaijuNest` は版 4 |
| 余燼 `EmberBackLayout` | 30 | 版 30 で脇の洞・抜け道を消した |

版を上げるときは、ほかの背の最大を超えない番号で次に進め（慣習: 他の背の最大の次）、`docs/design/26` の該当章に書く。

## 4. 見る場所・選ぶ場所

- 選定ページ（`docs/review/<題>/index.html`）: `builds`（27）、`rebirth`（31）、`trial_pool`（29）、`backs_scale`（26）、`abyss_weapons`、`storm_review`（25）、`tide_climb`（24）、`titan_young`（28）、`_decisions` ほか。全体の一覧は `docs/review/`。
- ラフの比較ページは artifact `https://claude.ai/artifact/EqFcC7E183D8uyyAo8yKMM`（`tools/rough/build_gallery.py <manifest.json>` で作る）。ユーザーの選びはその db の collection `choices` に入る（`ArtifactData` で読む）。選び終えたラフは消す（元の入力と選ばれなかった案は `tools/sources/` に残す）。
- 試遊の声と判断の経緯: メモリ（`MEMORY.md` の索引）。設計書には決定の経緯を書かない（現在の仕様だけ）。

## 5. 残っている課題

- **2 つ目の鏡の間**（雷翼の眼の間まわり）: 未着手。経緯は 25 の 4・5 章。
- **嵐の塔の頂の戦い**（雷翼の任意の翼）: 未仕上げ。仕様は 12 の 3.3、26 の 10.5。
- **試練の P3**: 各巨神を十二にするまで、岩鯨 W08、潮殻 T03・T04・T08・T11・T12、雷翼と余燼の新作 5 つずつ（29 の 5.5）。
- **モデルの検査（2026-10-20 の時点）**: `python tools/audit/mesh_audit.py` は開いた辺 0・関節 NG 0。ブロックモデルの欠けた面 32 個（遺物のブロック）は `python tools/once/fix_block_faces.py` で塞いだ。低ポリの「裏面透け」の画素は残る（ash_smith・ash_thrall・bone_mortar・bubble_puffer・storyteller・pearl_keeper など）が、描画は `entityCutoutNoCull`（両面）で、アトラスに透明な画素もないので、ゲームで穴にはならない。**浮いた部品**（休めの姿勢で、ほかのどの部品の面からも 0.35 単位より離れた塊）は、面を 0.3 刻みで標本化して測ると 0（`docs/review/floating_parts.md`）。頂点どうしの距離だけで測ると防具の背の板・鍛え手の腕・番兵の脚の飾りなどが「浮く」と出るが、面では触れている。ゲームで浮いて見える所は、動いた姿勢か絵の見え方なので、場所を指して（スクショ）直す。
- **順路（32）の残り**: R1〜R4 は `wip/raid-route`（worktree `../mod-raid`）。自己テスト `trial route`（初回・五周 × 四体の周回・中ボスの段・あきらめ・区画）と `trial shrine`（祠・断り方・歌片・見え方）。**まだ**: R6 の残り（二人で通す。一人の通しで見た所と残りは `docs/review/raid_route/notes.md`）。区画は新作 36 セルを背の次元の遠く（x 4096 + 番号 × 1024）へ写して動かす（`TrialSlot.build`・`TrialWindow`・`TrialFrame`。自己テスト `trial slot`）。祠は跡地の置き場に建つ（跡地のない世界だけ、都の鐘から方位の帯の近い端）。歌片の絵は仮の 32×32（`tools` ではなく一度だけ書いた）。依頼 `weekly_route` の報いの歌片は岩鯨のものだけ。口の手がかり:
  - 越えた合図: `TrialSteles.onRung` → `TrialRuntime.onStoneRung`（図鑑・報い）と `TrialRoute.onCleared`（今の段なら 4 秒後に `carry`）。原型の石は近づくだけ（`TrialStones`）。
  - 段ごとの試練は `TrialRoute.defOf(draw, step)`、入口の休み場は `entryRest`、戸は `TrialRuntime.syncGates`（今の段だけ）。記録は `keep.trial.step/mid/route/party/start/slot`。
  - 祠: `TitanShrine.ensure`（心臓を初めて止めたときに建つ）・`hold`/`activate`（長押し）・`refusal`。祠の画面は旧試練の板の画面（`TrialBoard`・`TrialBoardNet`・`client/trial/TrialBoardScreen`）。開発用 `/skyroute shrine <巨神>`・`/skyroute shard <巨神>`・`/skyroute status|start|clear`。
- **一章の敵がフルスイートの負荷で不安定**: 自己テストの全体を回すと、一章の mob の段が時々落ちる。単独の `SKYABYSS_SELFTEST_ONLY` では通る。
- **地上の場所の建築の監査（2026-10-22）**: 種 4242・777・31337・12345・2024 の新しい世界で、地上（背を除く）の指摘は 0。直し方は部品ごとではなく、建て終わりの仕上げ `SiteJob.settle()`（すべての部品。Post より前）: 最下段の下の空きを地面まで埋める・1 列だけのくぼみを埋める（部品の列は 1 段だけ）・支えのない草花を消す・立つ場所の印を空ける・水と溶岩の源の漏れを塞ぐ（範囲 +3）・取り残しの自然のブロック（割れた氷は水へ）を消す・ならしで支えを失った村の松明やボタンを消す。監査の側は、泥・土の道（14〜15/16 の高さ）を地面に数え、水面の列は比べず、雪のブロック・粉雪・テラコッタ・氷・ドリップリーフを自然とし、部品の範囲の外の戸と向きのある物（近くの村）を数えない（`SiteAudit`・`BuildAuditExtra`）。章の小要素の置き場探し（`ChapterPlanner`）は、段階をゆるめても見つからないとき、最後に 24〜150 の全体を 5 度・5 ブロックおき・余白 2 で探す（凍った海に囲まれた岩鯨の跡地で、一章の見張りの丘・歌石 3〜5 などが置かれずに消えていた）。見つからない要素はログに `Chapter sites: no spot found`。撮影 `tools/shots/ground_sites_qa.txt`（`skyabyss dev grounds go <章> <部品の名前[.n]> ...`・`grounds sites`。絵は `docs/review/ground_sites_qa/`）
- **新しい世界で揺れる自己テスト（2026-10-21 に 3 回流して確認）**: `heartbeat ward`・`site cave`・`young`（着地）・`ch1 back mobs`（突進）・`build audit` の背の明るさと足跡・骨の丘の段は、世界ごとに通ったり落ちたりする（都の作り直しの前から。都の飾りの階段は監査で許可済み）。
- **自己テスト `ch2` の小要素の数**: 「二章の小要素は 3〜5」の検査が 8 で落ちる（二章の地上の合流で小要素が増えた。`TideChapterSelfTest.data`）。数の決まりを直すか、検査の上限を上げるかは判断待ち。
- **岩鯨の広間の広さ**（26 の 8.6）: 1〜2 人には広い。判断待ち。
- **実機で確かめていないもの**: 深淵の心臓の戦い・専用伝説 4 点の見た目と効き、紋の盤・変異の画面、転生の台座から転生を終えるまで（本番の jar）、順位の石・飾り板の複数人での表示、版 22・23 の試練の階の歩き（雷翼・余燼）、巨神の子の遠い旅。

## 6. 作業の決まり（短く）

- 設計書は現在の仕様だけを書く。00 は触らない。`docs/design/README.md` の索引を更新する。
- 大きな仕事は担当ファイルを分けて並列に（同時 5 つまで）。各エージェントに「トークン予算」（grep・offset で必要な所だけ読む、再読しない、ログは tail、狙った自己テスト）と短い日本語の報告を求める。
- 並列で衝突しやすいもの: `00_decisions_needed.md`、`sounds.json`・lang（キーは和集合）、自己テストのサーバーのポート、他の担当が後で本実装するスタブ。
- 絵は 32×32、ラフ 3 案から、重ねない、武器とモブのモデルは別。モデルは閉じた立体で、関節の部品は親に沈める。
- 時間・天気・背の状態で試練を閉じない。状態は遊び方を変えるだけ。
- READMEとサイトの絵は手作りのドット絵（ゲームのスクショと絵文字は使わない）。
- セッションの終わりが近いときは、この文書を更新し、`m1-sky-pass` と `wip/*` を push して止める。

## 7. 途中の作業（古い。下の 8 を見る）（2026-10-22 朝、再起動の前）
- **五章の入口（メインの作業。枝 `wip/ch5-entrance`、未テスト）**: 34 の 4.7。`tools/gen/ch5/gen_ch5_entrance.py`（main_abyss・ch5/black_iron・会話・skyabyss_ch5 の文）と、都の設計図の大坑道（`tools/capital/backalley.py` の `abyss_shaft`。螺旋の段・踊り場 2・底の境。検査 `tools/capital/audit_shaft.py` は 0 件・跳ぶ所 0）。**まだ**: Java（封じた戸を天の鍵で開ける・踊り場と底の合図 `ch5_abyss_door`・`ch5_threshold`、ShelterBuilder の extras に印 `abyss_landing_1/2`・`abyss_gate`、SiteGuard の都の範囲を下へ 110 まで広げる）、自己テスト、撮影。
- **深淵の中の形**: 選定ページの子ページ `abyss/`（問い `ab_*`）の答え待ち（00 の 245）。
- **サブの作業（再起動で止まる）**: 巨神の背を軽くする（`mod-route`、枝 `wip/route5`）、地上の場所の検査の片付け（`mod-ch4`、枝 `wip/ch4`）。どちらも続きから頼み直す。

## 8. 途中の状態（古い。下の 9 を見る）（2026-10-22 午後）
- 作業場所は `mod` 一つ（m1-sky-pass）。サブは一時の作業場所（`mod/.claude/worktrees/agent-*`）で作業し、合流したら消す。
- 五章: 入口（34 の 4.7。大坑道まで）・深淵のディメンションと層の大縦穴（36 の A1・A2）・潜るときの圧（A4）・深淵術（A5）はできた。敵（A3）は 3 案のラフを作っている（`wip/abyss-mobs-a`・`wip/abyss-mobs-b`）。番人（A6）・門を開ける（A7）・クリア後（A8）はまだ。
- 自己テスト `trial pool`・`trial pool ember` は、試すあいだ試練の階のチャンクを読み込んだままにする (順路の作業 R6 のあと、湧かせた敵やボスが見つからずに回ごとに違う試練で落ちていた。2026-10-22 に直した)。
- 全部の自己テストは、終わるとサーバーを止める (SelfTestRunner。前は titan back の段が止めていた)。

## 9. 前の状態（古い。下の 10 を見る）（2026-10-23 0 時。0.5.0 の jar を渡した）

- **jar**: build/libs/skyabyss-0.5.0.jar。試遊の確かめ順は docs/review/playtest_0.5.0.md。試遊用コマンドは `/skyabyss play …`（docs/design/37）。
- **五章「深淵」**: 入口（都の地下の戸・大坑道・底の門）→ 四つの層の遊び（骨の肋の橋・塩の湖・雷の道・灰の迷路）・脇の洞（墓・刻み）・鎖の近道・足音と見る者・縦穴の目印 → 心臓の番人（モデル B）→ 報告。クリア後: 番人の悪夢（段 1〜10）・遺品 12・果てない深み（区画 8 種・仕掛け 8 種）。設計は 36 の 8・9 章。
- **UI**: 画面のゆとり（ScrollPanel・PanZoomCanvas・UiFit）。容器の画面 3 つ（旅嚢・装飾・鍛冶）は未対応。
- **最適化**: HUD の描き（塗りをまとめて送る・文の作り直しを tick ごとに）。景色の描きとサーバーは、変えずに削れる所が見つからなかった。
- **試験**: 全体 161 件通過・失敗 0（2026-10-22 夜）。止めるときの固まりは、外し忘れの強制読み込みを止める前に外して直した（SelfTestRunner.releaseForced）。
- **ゲームの起動の枠**: `bash tools/dev/mc_run.sh runServer|runShots ...` で二つまで同時（.mc_lock と .mc_lock2、ポートは枠ごと）。compileJava は枠なしでよい。
- **選定**: 答えの出ていない問いはない（00 は 272 番から）。
- **次**: 試遊の結果を受けて直す。深みの浮かぶ墓場の島の下の光、遺品の絵の描き込み（B）、番人の倒れたあとの場面のカメラ。


## 10. 今の状態（2026-10-23 昼。Claude の再起動で全部止めた）

m1-sky-pass に入っているもの（push 済み・compileJava 通る）: 倒れ中は動けず技も使えない／召喚技を間引き／岩以外の技を強化／ボスの予告と着弾の演出／盾は正面の傷の一部だけ止める（combat/ShieldRules）／敵の頭上の HP 帯（client/hud/EnemyHealthBars）／マナを満腹度の場所に（VitalsHud）／岩の砥石は「作る」で頼みが進む／鐘の部屋 2 つと水路の道しるべを削除・水路を幅 1 に／潮殻の領域を「合わせ貝」（ShellMatchDomain）に／遺物の「持っている」判定と倒した巨神が見える件／NPC 会話の前に poll・五章の合図をパーティへ／**深淵に入ると落ちる**（AbyssGate.onTick の ConcurrentModification。ch5・abyss・town の players() ループを写しに）。
試遊者のゲームの落ちた記録: `C:\Users\nisimoto\curseforge\minecraft\Instances\test\crash-reports\`。

途中で止めた作業（WIP を push 済み。worktree は .claude/worktrees/agent-* に残っている。続けるときは各ブランチから）:
- `wip/raid-whale` レイドの土台（trial/raid/RaidRoom・RaidRooms・RaidKit・RaidCtx・RaidRun、部屋は巨神ごとに固定・全滅でその部屋の最初から）と岩鯨の 3 部屋（WR1 骨玉運び・WR2 共鳴の骨・WR3 鯨の核を守る）。
- `wip/raid-tide`（TR1 真珠の灯り運び・TR2 貝の音合わせ・TR3 珊瑚の苗を守る、水なし）、`wip/raid-storm`（SR1 避雷の羽運び・SR2 銅の回路つなぎ・SR3 帯電の連鎖）、`wip/raid-ember`（ER1 熾火運び・ER2 鍛冶の手順・ER3 炉を守る）。どれも raid-whale の API の上。始めたばかり。
- `wip/mount-shop` 乗り物の店（笛で呼ぶ・都の厩舎・荷獣 + 巨神ゆかり 4 種・速さ/跳び/体力 5 段を鐘銭で）。SelfTestRunner への登録の途中。
- 選定ページ（MmR9oX8Z5G9fcfibcD9GQp）の「決めたいこと」21 問（rd_* レイド・mt_* 乗り物）はまだ答えなし。上は全部、推奨/A で作り始めたもの。答えが来たら合わせる。

次にやること（試遊者の声、2026-10-23）:
- 深淵に入ると左の UI がかぶる（撮影 `tools/shots/abyss_hud.txt` → run/shots/abyss_hud/、まだ見ていない）。
- 深淵は「階段を降りて敵を倒すだけで退屈」。深み（クリア後）・ボスも作り込み不足で「意味がわからない」「章もない」。作り直しの案を選定ページに出してから作る。
- 試練の体の中の古い部屋（戸列・GOAL_SPOTS など）はレイドの順路が入ったら消す。
- 前のテストのサーバー（java PID 25044 と 27404、0:20 起動）が残っていて私の権限では止められない。止めてもらってから全体の自己テストを流す。

## 11. 夜の作業（2026-10-23 夜明け前）

m1-sky-pass に入ったもの: レイドの枠組み（trial/raid、枠ごとに複数部屋・周ごとに引く）と 4 巨神 × 3 部屋（WR・TR・SR・ER）／乗り物の店（荷獣・鹿・大トカゲ + 巨神ゆかり 4、都の厩舎、docs/design/38）／深淵の層の作り直し（野営 → 三本の道 → 印 → 門 → 層の主、節の札・場面、生き残り 4 人と荷運び、術の仕掛け）／深みの不思議のダンジョン化（5 階ごとに歪んだ王）／HUD の点検（深淵の術・深さを HudLayout に、HP 帯の遅れ帯の速さ）／ギルド画面の送り／都の厩舎の壊れたブロック名。
全部の自己テスト: 152 PASSED、かみ合わせの失敗が残っていて 2 つの作業が直している（`wip/fix-story-npc`: npc・cinematic・story・freequest・帯の長さ、`wip/fix-raid-tests`: trial walk・route party/rooms・trial pool storm・ash smith・ch1/ch3 mobs）。`wip/abyss-organic` は深淵の層を箱の部屋から洞窟の形へ。
朝に選んでもらうもの: 部屋の 12 問（各ふたつ、ASCII の id に直した）／乗り物 7 体の絵（docs/review/mount_roughs/）／層の主・生き残り・歪んだ王の見た目。
