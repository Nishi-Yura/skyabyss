# 引き継ぎ (2026-10-20 時点)

「天と淵のあいだ」(NeoForge 1.21.1)。今の仕様は `docs/design/README.md` の索引から読む（コードが正）。未決は `docs/design/00_decisions_needed.md` にだけある（エージェントは触らない。番号付けは親）。リポジトリは github.com/Nishi-Yura/skyabyss、作業の幹は `m1-sky-pass`、途中の枝は `wip/*`。

## 1. 各システムの状態

| 系 | 状態 | 書類 |
| --- | --- | --- |
| 序盤（序章・一章前半・拍子・目的の帯・知らせ・場面） | できている。試遊の声で何度か直した。語りと静かなホラーを最初から | 06・17・18・20 |
| 四体の巨神と背（岩鯨・潮殻・雷翼・余燼） | 四体とも遊べ、背は水平 ×1.6・垂直 ×1.3 に作り直し済み | 02・08・12・13・26 |
| 試練のプール | 基盤・板・図鑑・称号・今週の試練・深淵は実装済み。**四体とも十二（48 すべて）実装済み**（`trial/TrialCatalog`） | 29 |
| はじまりの都（33 の W1） | **できている**: 鐘の宿りは都の設計図 `capital`（`tools/capital/town.py` + 建築の部品集 `tools/capital/vox.py` → `data/skyabyss/opening/capital.json`。runs 形式、約 40 万ブロック、建つまで約 15 秒）。作風 B「灰石と緋の屋根」（2026-10-21 決定）。壁 (高さ 12、歩廊、外に塔) と門楼つきの四門、北の段 (大鐘楼 46 段・語り部の家・砦・八角の記録の塔の館・庭)、広場と丸い市、組合、石の輪の社、鍛冶場、街区ごとに通りに面して隙間なく並ぶ町家 (店・張り出し窓・露台・旗・小塔・中庭)、空き区画 5、西門の外の墓地。碑 16、村人 10、イサナの野営 (印 `chaser`)。場所は野営地から 260〜340（だめなら 240〜480、最後は段差最小の所）、水は 1 割まで埋める。閉じた施設は章で開く（`opening/site/CapitalDoors`）。碑を見ると序章の伝承 `prologue/capital_*` 4 項目、語り部は碑を一つ読んだあと「見たもの」→「この都は、どこも戸が閉じている」で一度だけ。自己テスト `opening`・`opening (capital doors)`・`lore`・`npc` 通過。撮影は `tools/shots/capital.txt`（開発コマンド `skyabyss dev opening at shelter 0 <x> <y> <z> <yaw> <pitch>` は設計図の座標で置く）、ブロック名の検査は `python -I tools/capital/check_states.py <設計図>`。**注意**: 設計図を変えたら自己テストの前に `run/world` を、撮影の前に `run/shots/capital/saves` を消す。古い台本は `SKYABYSS_SMALL_SHELTER=1` で小屋のまま撮れる。**まだ**: 作り込みは参考画像の水準の手前（一棟ずつ部品を足していく）、壁の外の地形はならしていない、閉じた施設の中身（名簿・鍵の間・記録の間・書庫）は空 | 33・17 の 3.3 |
| 世界の背骨と地図・章の筋 | **採用（2026-10-20）。W1（都）・W2（方位の跡地）・W3（M の地図）と 34 の C1（一章）は実装済み、W4 と 34 の C2〜C5、32 の祠の本当の置き場への差し替えは未実装**。33 = 世界はバニラのまま、町（約 200×200、ラグニ級。壁・門・空き区画）と跡地と祠だけ固定（方位 北東西南・距離の帯）、M の地図は印だけ。34 = 四章ぶんの物語の背骨と、支度の段でバニラの進み（鉄・船・銅と雷・ダイヤ・醸造）を通すメインクエスト。33 は W1（都。**「絶えかけの国の都」で決定**: 建物は 33 の 2.2、章ごとに施設が開く。見取り図は第 2 回の案 A「鐘の都」で決定。砦は見張りの砦・村人は少し・国の名は「鐘の国」。次は W1 の R0（17 の 3.3・01・05・07 の本文）→ 目覚めと都の場所の選び方 → 都を建てる（建物ごとにラフ 3 案））、34 は C1（一章の骨の坑と支度の段）から。32 の R1 とは独立 | 33・34 |
| 章の跡地（33 の W2。2026-10-21） | **できている**: 都から決まった方位と距離の帯（北 300〜600 岩鯨・東 800〜1,400 潮殻・西 1,000〜1,800 雷翼・南 1,200〜2,000 余燼）に、章ごとに世界で一つの跡地（`opening/site/ChapterGrounds`。扇 ±25° を内側から 32 刻み、村などの構造物は 192 避ける）。合う地形がなければ帯の中のいちばん乾いた所に形を整える（丘 96・入り江 160・峰 128 で +60・灰の盆地 160。`ShapeJob`。一章は目覚めの前、二章以降は章が開いたとき）。章の地上の場所（21）は跡地に世界で一つ（`ChapterSites.Builder.atGround/atRoad`）、旅の跡・荷車・日記だけ拠点のまわり。都から跡地への石標の道しるべ（40 ごと。一章は鯨道の石標 7）。一章の骨は北の丘の中。潮殻の干潮の寄りと潮見の貝は東の浜だけ（`titan/tide/TideBeach`）。地図に跡地の噂→見た。**祠の口**: `ChapterGrounds.shrineSpot(server, titan)`（32 の `TitanShrine.ensure` の仮の置き場を、合わさるときにこれへ差し替える）。自己テスト `chapter grounds`、開発コマンド `skyabyss dev grounds info|prepare|go`、撮影 `tools/shots/chapter_grounds.txt`（未撮影）。**まだ**: 雷翼の止まり木・余燼の浮上を跡地に寄せる（33 の 3.1。今は人の近く）、地形の悪い世界（沼・海ばかり）では跡地の小要素や道しるべが水たまりで建たず諦めることがある、序盤の場所の計画が跡地の探しで 15 秒ほど長くなった（別のスレッド） | 33 の 3 章 |
| 旅の地図（33 の W3） | **できている**: M で開く印だけの地図（`client/map/TravelMapScreen`、北が上、町が中心、縮尺 600 / 2,400 をホイールで）。印の出どころはサーバーの `map/MapMarks` 一か所（町・野営地・足跡・骨・積み石は序盤の拍子から噂/見た、着いた章の場所、覚えた碑、出会った巨神）+ クライアントの自分・仲間・墓・目的。子の海図は同じ画面に重なる（旧 `TravelChartScreen` は消した）。見た目は設定 `mapStyle`（SLATE 既定 / PARCHMENT / INK。`docs/review/map_roughs/`）。合図「ここ」のキーは M から ' へ移した。自己テスト `travel map`。**まだ**: 32 の祠・言伝ての板の目印（`MapMarks` に足す。W2 の四つの跡地は入った）、4.2 の墨の洗い（W4） | 33 の 4 章 |
| 一章の支度の段（34 の C1。2026-10-21） | **できている**: メインの段 `ch1/trail_iron`（鉄の跡。イサナ）・`trail_blade`（折れた剣。職人）・`trail_shield`（盾と糧。語り部）がイサナの鯨の話のあとに三本とも出る（旧い `ch1/prep_*` を吸収。`PrepQuests` の章の表）。新しいミニダンジョン**骨の坑**（`story/ch1/BonePit`。坑口の肋骨と碑・水没の坑道・前の私の荷・冷えの戸・出口の梯子。部品 `ch1_bone_pit`）、目的 `wear`（着ている防具の数）、頁 4、記念品「鯨追いの印」（`whale_chaser_mark`。案 B）、進捗 `bone_pit`・`whale_trail`、目的の帯の行き先（骨の坑・洞）。生成は `tools/gen_ch1_trails.py`・`gen_ch1_sites.py`・`gen_ch1_trail_assets.py`。自己テスト `ch1 p6`（骨の坑と三本の流れ）ほか。撮影 `tools/shots/ch1_bone_pit.txt`。骨の坑・割れ殻の洞・見張りの丘・歌石・骨の集め場・骨は一章の跡地（北の丘。W2）、鯨道の石標は都から北の丘への道。**まだ**: 三本を済ませないまま二章へ進むと、三本は進行中のまま残る | 34 の 4.2.2・21 の 3.1・07 の 6.8 |
| 巨神の順路（レイド化） | **設計だけ（2026-10-20 採用）**。歩 → 智 → 闘 → 中ボス → 心臓の順路。背は順路だけ（鼓動の間・板・誓い・炉・任意の翼・今週の試練は消す）。初回は入口に全員で入ると始まり、周回は祠から専用の品で。順路ごとの区画で待ちなし。巨神は章の当事者にだけ見える。実装の段は 32 の 8 章（R0 は済み、R1 から）。未決は 00 の 205〜208 | 32 |
| 心臓・領域・遺物の選択 | 領域は三つの仕掛けを解くまで続く。心臓を止めると巨神は眠り、遺物は専用画面で選ぶ | 02・30・20 |
| 巨神の子と旅 | 笛で子を呼び、パーティ全員で乗って海図から行き先を選ぶ。成体は動かない | 09・28 |
| 成長・スキル | 5 つの木＋旅人の 36 技。天職は専門。タグ・セット・連鎖・構え・熟練・キーストーン・試着・型 4 つ。変異・橋・紋の盤・前世の技・淵の木 | 27 の 17〜18 |
| 装備 | 13 武器、特性、炉（強化・分解・再鍛造・交換・刻み）。穴・欠片・技のルーン・アスペクト・世代。深淵の専用伝説 4 点 | 03 の 3.6〜3.7 |
| 転生 | Lv 50+n（上限 20）、魂盤・恩恵・祖の力・導きの欠片・色と記号・称号 24・順位の石・飾り板・墓の飾り | 31 |
| 旅嚢・死・墓・パーティ | 旅嚢（品ごとの固定枠、倒れても残る）、柔らかい死（墓から立て直し）、倒れた仲間の蘇生、6 人まで | 22・11・10 |
| モデル | 低ポリのメッシュ（水密・関節は親に沈める）。平らな絵は 32×32 | 15・16 と tools/README |
| サイト | `site/`（GitHub Pages、日英、ドット絵のみ）。`python tools/site/gen_art.py` の後に `python tools/site/build.py`。生成物の `site/*.html` もコミットする | tools/site/ |
| 画面と札（2026-10-21 夜） | **決定（2026-10-21: 札 B 左の帯の下、進捗のチャットは残す、絵柄 A 暗い板）**。札（獲得・進展・クエスト・伝承・バニラと mod の進捗）は `client/notice/CardFeed` の一つの出口、置き場は設定 `cardPlace`（既定 LEFT_FEED = 左の目的の帯の下に一行ずつ）。バニラの進捗の札は `mixin/ToastAddMixin` で mod の札に、レシピ・操作説明の札は出さない。バニラの画面は同梱のリソースパック `skyabyss_ui_a/b/c`（`tools/ui/gen_vanilla_skin.py`、既定 A をはじめての起動で選ぶ。`client/ui/UiSkin`）。見出しの文字・ツールチップの色も絵柄に合わせる。撮影は `SKYABYSS_UI_SKIN=a|b|c|none` | 18 の 3.1・3.2 |
| 最適化（2026-10-21 夜） | 見た目・中身を変えない形で: HUD の配置の計算が入力の同じフレームで飛ぶように（Fit を入力で比べる `Fit.keyed`）、ボスバーの持ち主探しを tick ごとに一回（`client/BossOwners`）、光の筋の 128KB の置き場を使い回し、クエストの札の組み立てを一フレーム一回、蛾と野営の探しはチャンクの区画の表で先に見る（`util/SectionScan`）、装備の打ち消しの早抜け。残りの候補は見直して不要と判断（`EmberTrials` は人のいる部屋だけ・小さな箱だけ、`VowEvents` は毎秒の型の確認だけ）。`GuideTrailRenderer` の床探しは効果が小さく未着手 | — |

## 2. 動かす・試す・撮る

- **鉄則**: `gradlew --stop` は使わない（ユーザーの試遊クライアントが落ちる）。いつも `--no-daemon`。ゲームもサーバーも同時に 1 つだけ。終わったら自分で起動した java を PID で止める（ユーザーの CurseForge の `javaw.exe` と、動いている他のエージェントのプロセスは止めない）。
- **メモリ**: `gradle.properties` は `-Xmx1G`。重い python（レンダラ・メッシュ処理）は 1.5 GB 以内で、並列にしない。
- **ユーザーが試遊中のチェックアウト（Desktop\mod）では絶対にビルド・編集しない**（`build/classes` が差し替わり ClassNotFoundException で落ちる）。確認は worktree でする。
- 型検査: `./gradlew compileJava --offline --no-daemon`。
- 自己テスト: `SKYABYSS_SELFTEST=1 ./gradlew runServer --no-daemon`（全体は約 10 分。`SKYABYSS_SELFTEST` があるときだけ build.gradle が `run/server.properties` の `max-tick-time` を -1 にしてウォッチドッグを切る。巨神の着地の段がチャンクの同期生成で 1 tick 200 秒かかるため。**自己テスト中は重い python を並列で流さない**。`build audit` の地上の場所の指摘（浮いた石塚・足跡の水漏れ）は run/ の地形しだいで出る。繰り返しは `SKYABYSS_SELFTEST_ONLY=<段の名前の頭>`、例 `abyss`・`gear freedom`・`whale scale`。全体は最後に 1 回）。
- 絵の確認: 台本で撮る `./gradlew runShots -Pscript=tools/shots/<台本>.txt --no-daemon`。出力は `run/shots/<台本>/shots/<台本>/`、最後の行が `SHOTS OK` なら成功。台本の書き方は `tools/README.md`。**OS のキー送り・フォーカス操作はしない**。音は自動で 0、言語は日本語（`SKYABYSS_LANG` で変更）。
- マルチ: 専用サーバー + 二人のクライアント。ポートは `server-port=25666`・`rcon.port=25766`（パスワード `test`、`online-mode=false`）。worktree が並ぶときは番号を変える。
- 試遊用コマンド（OP 権限 2）: `docs/playtest_commands.md`（`/skyabyss playtest now|next|board|goto|trials|gear|chapter|fast|reset`、`skyabyss dev trial|rebirth|…`、`skyabyss skill …`、`skyabyss dev backmetrics`）。
- 構造は歩かずデータで確かめる: `build audit`・到達の検査・`titan back`（`tools/README.md` の「構造をデータで確かめる」）。
- ユーザーが通しの試遊（全クリア・2 人・周回の時間）を自分でやる。エージェントはやらない。

## 3. 配置の版（`LAYOUT_VERSION`。上げると古い島を消して建て直す。試練の進みは戻り、初回の記録 `keep.*` は残る）

| 巨神 | 版 | 最近の変更 |
| --- | --- | --- |
| 岩鯨 `TitanBackLayout` | 21 | 版 18 で拡大の作り直し、試練の階と鼓動の間の碑 |
| 潮殻 `TideBackLayout` | 21 | 版 20 で拡大、版 21 で試練の階 `TideDeck` |
| 雷翼 `StormBackLayout` | 22 | 版 21 で任意の翼、版 22 で試練の甲板。`RaijuNest` は版 4 |
| 余燼 `EmberBackLayout` | 23 | 版 20 で拡大、版 23 で試練の階 |

版を上げるときは、ほかの背の最大を超えない番号で次に進め（慣習: 他の背の最大の次）、`docs/design/26` の該当章に書く。

## 4. 見る場所・選ぶ場所

- 選定ページ（`docs/review/<題>/index.html`）: `builds`（27）、`rebirth`（31）、`trial_pool`（29）、`backs_scale`（26）、`abyss_weapons`、`storm_review`（25）、`tide_climb`（24）、`titan_young`（28）、`_decisions` ほか。全体の一覧は `docs/review/`。
- ラフの比較ページは artifact `https://claude.ai/artifact/EqFcC7E183D8uyyAo8yKMM`（`tools/rough/build_gallery.py <manifest.json>` で作る）。ユーザーの選びはその db の collection `choices` に入る（`ArtifactData` で読む）。選び終えたラフは消す（元の入力と選ばれなかった案は `tools/sources/` に残す）。
- 試遊の声と判断の経緯: メモリ（`MEMORY.md` の索引）。設計書には決定の経緯を書かない（現在の仕様だけ）。

## 5. 残っている課題

- **2 つ目の鏡の間**（雷翼の眼の間まわり）: 未着手。経緯は 25 の 4・5 章。
- **嵐の塔の頂の戦い**（雷翼の任意の翼）: 未仕上げ。仕様は 12 の 3.3、26 の 10.5。
- **試練の P3**: 各巨神を十二にするまで、岩鯨 W08、潮殻 T03・T04・T08・T11・T12、雷翼と余燼の新作 5 つずつ（29 の 5.5）。
- **モデルの検査（2026-10-20 の時点）**: `python tools/mesh_audit.py` は開いた辺 0・関節 NG 0。ブロックモデルの欠けた面 32 個（遺物のブロック）は `python tools/fix_block_faces.py` で塞いだ。低ポリの「裏面透け」の画素は残る（ash_smith・ash_thrall・bone_mortar・bubble_puffer・storyteller・pearl_keeper など）が、描画は `entityCutoutNoCull`（両面）で、アトラスに透明な画素もないので、ゲームで穴にはならない。**浮いた部品**（休めの姿勢で、ほかのどの部品の面からも 0.35 単位より離れた塊）は、面を 0.3 刻みで標本化して測ると 0（`docs/review/floating_parts.md`）。頂点どうしの距離だけで測ると防具の背の板・鍛え手の腕・番兵の脚の飾りなどが「浮く」と出るが、面では触れている。ゲームで浮いて見える所は、動いた姿勢か絵の見え方なので、場所を指して（スクショ）直す。
- **順路（32）の実装**: R0（書類）は済み。**R1 の最初の切り口は `wip/raid-route`（worktree `../mod-raid`）にコミット済み**（`trial/TrialRoute`・`RouteCommands`・`TrialRouteSelfTest`。入口の全員判定 → 段 1、越えたら 4 秒後に次の入口へ、三つで心臓の前室、焚き火のあきらめは全員の同意。自己テスト `trial route`・`trial hub` 通過）。**まだ消していない**: 鼓動の間・碑・板・試練の階の回廊・戸の封じ・任意の翼・今週の試練・既知の道（32 の 7 章）。**まだ作っていない**: 段の札と目的の帯の順路の文、区画（R2）、祠と品（R3）、巨神の見え方（R3）。入口の箱（`TrialRoute.entryBox`）は仮の座標で、実機で未確認。worktree の run/ は新しい世界なので、地形依存の段（opening・site cave・chapter sites・ch1 p6・titan ground・ch2・heartbeat ward）は順路と無関係に落ちる（変更前でも同じ）。入口の手がかり:
  - 越えた合図は `titan/group/TrialSteles.onRung` → `TrialRuntime.onStoneRung`（図鑑・報い）と `TrialHub.onCleared`（4 秒後に `tickPending` が中の人を `sendToHub` で鼓動の間へ）。**ここを「次の段の入口の休み場へ」に差し替える**のが R1 の芯。`TrialHub.sendToHub` の運びの作り（teleportTo・FakePlayer・音）をそのまま使う。
  - 枠と石の対応: `Trial.Slot`（岩鯨 0 歩 / 1 闘 / 2 智）、潮殻・雷翼は `TrialRegions.drawnCell`（west 歩 / east 智 / front 闘）、余燼は同 190 行あたり（西 歩 / 東 智 / 南 闘）。原型の入口の休み場: 岩鯨 `w0`・`mz0`（`MAZE_ENTRY`）・`e0`（`TitanBackTrials` の Checkpoint 一覧 129〜157 行）、潮殻・雷翼・余燼は各 `*BackTrials`／`EmberTrials` の Checkpoint と `respawnCheckpoint`。
  - 順路の記録はグループの記録 `BackGroupData.Record` の `keep.trial.*`（`TrialData` に定数。`keep.` はリセットで消えない）。グループの人は `Groups.online/inBox`。
  - 消す対象（32 の 7 章）: `trial/TrialHub`（封・碑）、`trial/HubTablets`、`trial/TrialBoard*`、`trial/TrialTabletBlock`、`trial/TrialSealBlock`、`trial/WeeklyGate`、各 `*Deck*`（回廊・戸・案内。`TrialCell.build` は残す）、各背の任意の翼と広場・祠・踏み跡の建て方、`FirstRun` の既知の道。
- **一章の敵がフルスイートの負荷で不安定**: 自己テストの全体を回すと、一章の mob の段が時々落ちる。単独の `SKYABYSS_SELFTEST_ONLY` では通る。
- **新しい世界で揺れる自己テスト（2026-10-21 に 3 回流して確認）**: `heartbeat ward`・`site cave`・`ch2`・`young`（着地）・`ch1 back mobs`（突進）・`build audit` の背の明るさと足跡・骨の丘の段は、世界ごとに通ったり落ちたりする（都の作り直しの前から。都の飾りの階段は監査で許可済み）。
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
