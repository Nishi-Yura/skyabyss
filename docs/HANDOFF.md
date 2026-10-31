# 引き継ぎ（2026-10-31）

ブランチ `m1-sky-pass`（push 済み）と、技の木 v2 の作業ブランチ `wip/skill-tree-v2`。設計書の入口は `docs/design/README.md`。00 の未決は空。

## 今の形（要点）

- 世界: 天と淵の大陸（40）。はじまりの島 → 渡し船 → 鐘の都の町めぐり → 土地の町から各章（42）。6 職は島の野営地で選び、町の職の間（訓練場の師範）で替える（48）。技は職ごとの木 v2（Wynncraft 型。使う技は 4 本まで、基礎は木の節）。敵は Mod の敵だけ（44。バニラの敵はどの湧き方でも入らない）。
- 試遊版: 0.9.20（`build/libs/skyabyss-0.9.20.jar`。確認表 `docs/review/playtest_0.9.20.md`（島・町の人・店）・`docs/review/playtest_0.9.19.md`（野の装備・封の箱）と `docs/review/playtest_0.9.4.md`（0.9.5〜0.9.17 は点検の直し・章の小要素・終章の入口と島 1〜4・巡礼者の座・結末・神器・巡礼の試し・終章の通しの点検）、終章は `docs/review/playtest_finale.md`、月は `playtest_0.9.1.md`、遊び方 `docs/beta_how_to_play.md`）。
- 自己テスト: `bash tools/dev/selftest_fresh.sh "<名前,…>" [<世界名>]`（空なら全部。大陸の新しい世界で回す。前は scratchpad にあって消えた）。`abyss escort` はほかと一緒に流すと時間切れで落ちることがある（単独では通る）。

## 神器の二本目・防具・装飾（ラフ・承認待ち。2026-11-01。設計 [50](design/50_finale.md) の 4.5.1）

- 武器 6 本は `o_divine.py` の後半、防具 3 式は `tools/rough/lowpoly/armor/divine/build_divine_armor.py`、装飾の絵は `tools/gen/finale/gen_divine_gear_icons.py`。見せた絵 `docs/review/divine_gear/divine_gear_roughs.png`。品の登録・効き方・手に入れ方（雲上の箱）・入れ方はまだ。

## 超大型レイド「あいだの塔」（作りかけ。2026-11-01。設計 [54](design/54_endgame_plan.md) の 2 章）

- 声「Wynncraft のめっちゃでかい動かないボスのやつに似せて。途中のやつとかなくて、ボス戦だけ」「アナイアレーション参考にして」で、四つの階をやめ、橋の先に座る巨大な主との一戦だけにした（手本は Wynncraft の Prelude to Annihilation）。
- できた: 塔の次元 `skyabyss:tower`（`data/skyabyss/dimension*/tower.json`）、区画 16（`tower/TowerWorld`）、橋の舞台（`tools/gen/world/build_tower.py` → `data/skyabyss/tower/bridge.json`、`TowerTerrain` が区画へずらして流し込む）、挑戦の進め方（`Towers`。一日一回・全滅と 30 分で終わり・倒れたら入口・抜ける）、橋の上の時間と休み場と落ちたとき（`TowerArena`）、都の広場の受付とレイド隊 1〜12（`TowerDesk`。資格のある人にだけ光の柱、チャットで隊を作る・入る・始める）、自己テスト `tower`、撮影 `tools/shots/tower_floors.txt`。
- 主「あいだに座すもの」（形はユーザー承認済み。ラフ `tools/rough/lowpoly/cloud/tower_master.py`、入れるのは `gen_cloud_mobs.py tower_master`。1/5 で作り、ゲームで 5 倍に描く。原点は北の広場の床で、形は LIFT で玉座へ上げてある）: 体 `tower/mob/TowerMaster`（当たり 28×32、体力の止まり所 floor、月のあいだの shielded）、側の目 `TowerEye`（灯守の目の形）、落ちる月 `TowerMoon`（巡礼者の月の形）、一戦の進め方 `tower/TowerFight`（技 11・群れ・高まり・月 2 回・側の目・繋ぎ手・鎖の子の回復落ち・勝ち）。越えた回数と最速は人のデータ `skyabyss_tower_clears` / `_best`。開発用 `/skyabyss tower act <技>`・`hp <%>`。撮影 `tools/shots/tower_boss.txt`。
- 報酬（54 の 2.6）: 塔の欠片 `tower_shard`・塔の箱 `box_tower`（`BoxKind.Type.TOWER`、由来 `tower` の装備が入る）・越えた人に箱 1 と欠片 10（`Towers.reward`）・塔の店（受付の [塔の店]、`/skyabyss_tower shop`、`data/skyabyss/shop/tower.json`）・塔の武器 6 本（`tower/gear/TowerGear`・`TowerGearSetup`・`TowerGearEvents`）。
- 形（承認済み・入れた）: 武器 6 本 `tools/rough/lowpoly/weapons/one/o_tower.py` → `install_job_weapons.py`（持ち方・振り `gen_weapon_moves.py`・`WeaponKind`・`MoveSets`・弓は `WeaponClient`）、塔の箱 `o_boxes.py box_tower` → `install_boxes.py`、塔の欠片 `tools/gen/tower/gen_tower_assets.py --install`。ゲームの中の絵 `docs/review/tower_rewards/tower_weapons_ingame.jpg`、撮影 `tools/shots/tower_weapons.txt`。
- 防具 3 式 12 部位と装飾 2（承認済み・入れた）: 品 `TowerArmorItem`・`TowerGearSetup.ARMOR/RING/CHAIN`、効き方 `TowerGearEvents`（光輪の衣は `WildGearEvents.manaRegenBonus・cooldownScale` から）。形 `tools/rough/lowpoly/armor/tower/build_tower_armor.py` → `tools/install/install_tower_armor.py`、装飾の絵 `gen_tower_assets.py --install`。塔の箱と塔の店（部位 50・装飾 60）にも入る。ゲームの中の絵 `docs/review/tower_rewards/tower_armor_ingame.jpg`、撮影 `tools/shots/tower_armor.txt`。
- まだ:、賭け・記録の板・称号、塔守の姿。数字（体力・傷・月の速さ）は遊んで詰める。

## 鐘銭まわり（2026-11-01。設計 [35](design/35_capital_facilities.md) の 6.2・6.11、[52](design/52_loot_boxes.md) の 5 章）

- 声「アイテム売ってお金にできる機能を箱空ける NPC に」「お金の上限もっと増やしていい（旅のインベントリのコインの上の数は見せなくていい）」「トレードで使えるのはお金のみ。アイテムは無し」「統合できそうな NPC は統合してもいい」。
- 買い取り: 封切り師の「品を買い取ってもらう」→ 店の画面を買い取りだけで開く（`facility/Buyback`、`ShopService` が id `buyback` を振り分け、`ShopScreen` は売るだけの形）。装備を階級と必要 Lv で値付け。会話の文は `tools/gen/loot/gen_box_text.py`。
- 上限: `Coins.MAX` を 999,999,999 に。`Coins.give` は旅嚢へまとめて入れる（前は 99 枚ずつ回していた）。旅嚢の枠の上の鐘銭の数は出さない（`PouchScreen.renderSlot`）。
- 取引所: 値と申し出は鐘銭だけ（`Trades.list`・`apply` が品を断る）。受けるときの鐘銭は旅嚢で受け渡す（前は持ち物へ 99 枚ずつ出していたので、約 3,500 枚を超える額は「空きがない」で止まるか払いきれなかった）。画面は値を鐘銭の絵と桁区切りの額で出し、つまみに重なっていた列の見出しを外した。
- 統合（ユーザーが「旅の番にまとめる」だけを選んだ）: 止まり木の番を「旅の番」にして、乗り物の店と乗り笛の決まりを足した。厩舎の番は引かせた（`AttendantRole.RETIRED`、会話・絵・形を消した。町の設計図の印 `npc_stable_keeper` は町を建て直さないよう残した）。町の案内の一覧から厩舎を外し、都の町めぐりの乗り笛は旅の番のいる止まり木で渡す（`TownTour`）。取引所と預かり所、鍛冶師と封切り師はそのまま。
- 確かめ: 自己テスト `facility`（trade・extras・guild）・`loot boxes`（買い取りと上限）・`pouch`・`lang` が通った。撮影 `tools/shots/money.txt`（買い取り・市・出す・自分の品・旅嚢）。

## ヒロイック防具の二回目の磨き（2026-11-01。設計 [51](design/51_world_gear.md) の 5 章）

- 縄の人の吊り衣の背: 外套の上に交差する縄と結び目、腰の縄、紫に光る背筋、垂れる三本の縄の先の光る重り（`build_wild_armor.py` の `_hanged_back`）。
- 星詠みの法衣の正面: 胸の真鍮の星盤、左右の星座、帯から下がる星の飾り（`_stargazer_front`）。
- 雲上の騎士の足もとの黒と白の欠けは形ではなく、一式の効果「雲の守り」のバニラの雲の粒（消えぎわに暗くなる）だった。白と金の光の輪に替えた（`WildArmorEvents`）。
- 入れ方は `python -I tools/rough/lowpoly/armor/wild/build_wild_armor.py wild_hanged wild_stargazer` → `python -I tools/install/install_wild_gear.py armor`。撮影 `tools/shots/heroic_armor.txt`（見分けの 2 枚 `zz_*` を足した）、一覧 `docs/review/roughs_wild/sheets/heroic_armor_worn.jpg`。

## 裏ボス「初代の空見」（2026-11-01。設計 [53](design/53_founder.md)）

- 三つの結末を見終えた人に、語り部が数え歌の「やっつめ」を話す（頼み `side_founder`）。大鐘楼の門の選びの［鐘の内へ］から、雲海の次元の南の閉じた大鐘「鐘の内」へ。
- 始祖は人の大きさの一騎打ち。鐘の拍子、耳で狙う（うるさい人を）、聞き耳の返し、竿の三つの技、数え歌の七行を技にした歌（言葉どおりに動けば避けられる）、段 3 の二行重ねと写し。体力が尽きると膝をつき、やっつめ「やっつ　やすんで　いいのよ」を歌って消える。
- 報酬は称号「やっつめを聞いた者」・見た目だけの空見の肩掛け（背中に描く。装飾の枠は使わない）・始祖の小鐘・伝承 2 項。挑み直すと J の記録に最速が残る。強さは増やさない。
- 同じ作業で見つけた不具合: 倒れて新しい体になると、月と終章の記録（選んだ結末・巡礼の試しと位相の挑戦の段・神器を渡した印など）が消えていた。`util/LifeKeep` に足した（神器を二度もらえる穴もふさいだ）。
- 確かめ: 自己テスト `founder`（と `cloudsea`・`rebirth`・`record`）が通った。撮影 `tools/shots/founder.txt`（鐘の内・始祖の構え・退けたあとの札・肩掛け）。手応え（数値・技の間）と歌の避けやすさは試遊で（`docs/review/playtest_founder.md`）。

## 0.9.20 のあと（遊ばずに決められる片づけ。2026-10-31）

- 砦の老兵を消した（`AttendantRole.RETIRED` に足した。前の世界の人は消える）。試練の記録（図鑑）は J の記録の巨神のつまみの行「試練の記録」の「開く」から（`RecordSnapshot.ACTION_RECORDS` → `RecordNetwork.OpenTrials` → `KeepRecords.open`）。前の空見の頁は都の社の祭司が渡す（`attendant_shrine_priest_page.json`。伝承 `chapter1/keep_prev_page` が開いていれば「もう渡した」）。
- 誂えの生成（`tools/gen/shop/gen_made_to_order.py`）: 表を今の店に合わせた（糧は食料屋、バニラの花火と光の矢は外した）。手で置いた品は消さず、誂えの品だけ入れ替える。`check_generators.py` の一覧に、これと `gen_wild_gear.py`・`gen_box_text.py` を足した（流して店がずれたら quick_check が落ちる）。
- `abyss escort`: 全段で流すと、前の段の敵や灰の熱で試しの人が倒れ、付き添いが止まっていた。試しの人を倒れないように（`AbyssEscortSelfTest.keepAlive`）。直したあとの全段は失敗 0（206 段）。
- ゲームの中の確かめ: `tools/shots/townsfolk_check.txt`・`townsfolk_check2.txt`（都の封切り師と案内人の噂・描き足し、社の祭司の頁、汐守・風待の封切り師の挨拶、道具屋・素材屋・武具屋・鍛冶場の品）。灰炉の封切り師は撮影の位置合わせで話しかけられなかった（立つことは前の撮影 `box_openers` で見た）。

## 0.9.20（島・町の人・店の直しの試遊版。2026-10-31）

- jar `build/libs/skyabyss-0.9.20.jar`。確かめ表 `docs/review/playtest_0.9.20.md`（島・蜂の巣・町の人・店・成長の知らせ・ヒロイックの防具・封切り師の挨拶）。変更履歴はサイト（`tools/site/pages_c.py`）。新しい世界で始めること（島の地形を作り直した）。
- 確かめ: 自己テスト全段（失敗は `abyss escort` だけ。いつもの時間切れで、単独で流して通った）、`quick_check.sh` 全部、jar の中身と版。

## はじまりの島を要素の紹介だけに・蜂の巣の音（2026-10-31。設計 [40](design/40_rpg_world_and_start.md) の 7 章）

- 声「最初の島のチュートリアルなんだけど、意味のないものは建造物、クエストごと消して。見張りの塔とか、大樹とか。このゲームの要素紹介になってない」。島の目的は 職 → 帳面 → 足跡 → 峠の野盗 → 休み場 → 祠 → 渡し守 だけ（どれも要素を一つ教える。40 の 7 章「島の場所はどれも要素を一つ教える」）。
- 消したもの: 寄り道 5 か所（見張り塔の跡・大樹・洞窟・漁師小屋・旅人の野営）の目的（`IslandTrail` の印・敵を出す `Spot`）、言葉、見どころ（`pois.json` では並びの番号を保つため `gone` に。`ContinentPois.Poi.site` で建てない・地図に出さない・数えない）、脇道と脇道の谷（`continent_layout.py` の道の並びの最後の 5 本と、`island_plan` の開けた所と首を消し、`gen_continent.py` で地形を作り直した。変わったのは島だけ）。使っていなかった島の分かれ目の札（`buildSigns`・`islandSigns`・`sign.skyabyss.island.*`）も消した。前のセーブの印（16〜256）は見ない。前の世界ですでに建った寄り道の建物は残る（新しい世界から消える）。
- 声「蜂の巣からめっちゃ音が聞こえる」: バニラの木の飾りが蜂入りの巣を付け、蜂はバニラの生き物なので世界に入れず（`RpgRules.onJoin`）、巣が昼のあいだ毎 tick 出る音を鳴らしていた。巣の付く木 15 種の `configured_feature` を飾りなしで上書き（`data/minecraft/worldgen/configured_feature`。作るのは `tools/gen/world/quiet_bee_trees.py`）。前の版でできたチャンクの巣は読んだときに空気にする（`rpg/rules/QuietHives`。取ると記録に `[hives] removed`）。
- 撮影 `tools/shots/island_v3.txt`（`island_v2`・`island_camps` は消した）。自己テスト `island trail`（消した寄り道が残っていない検査を足した）・`quiet hives`（新しい段）。

## 係の人と店の見直し（2026-10-31）

- 係の人を全員見た: 丸ごと消すのは書庫番・地図屋だけ（下の節）。墓守・酔いどれは都の町の頼みの起点、土地の町の人と客は全員クエストを出すので残す。砦の老兵は試練の記録を開くだけだが、本丸の頁の話があるので残した（記録を J に移せば消せる）。
- 直し: 止まり木の番の「地図を見る」（M と同じ）を外した。冒険モードで置けないブロックを店から外した（道具屋の松明・ランタン・寝台・蝋燭、鍛冶場の言伝ての板・共鳴の炉・巨神の道標）。重なっていた品は一か所に: 砥石は鍛冶場の誂えだけ（一章の職人の頼みが鍛冶場で教える）、穴の粉 5 種は素材屋だけ（章は前の鍛冶場と同じ）。
- 注意: `tools/gen/shop/gen_made_to_order.py` は apothecary.json と tool_shop.json の made の品も書き直し、手で外した品（固焼きなど）を戻してしまう。流したら smithy.json 以外は git で戻すこと。
- 確かめ: 自己テスト `facility`・`npc`・`artisan`・`travel`・`gear freedom`・`town`・`lang`。

## 書庫番・地図屋を消した（2026-10-31）

- 声「書庫番や地図屋って必要？」→ 両方消す。`AttendantRole` から外し、会話・言葉・体・顔・印を消した（設計図の印 `npc_archivist`・`npc_mapmaker` は残っても立たない。前の世界に立っている人は `AttendantRole.RETIRED` で消える）。生成の道具（肖像・印・npc2・attendants）からも外した。
- 働きの引っ越し: 祠と開いた章の跡地は M の地図に最初から載る（前は書庫番の「地図の写し」）。跡地の噂と描き足し（鐘銭 5）は都の案内人が言う（`attendant_guide_town.json`、都の中だけ）。伝承の読み返しは伝承の書の画面のまま。
- 同日: レベルアップの知らせから「振り分けられる力」を消して技の点に、記録（J）の振り分けの行を消した。町の案内の一覧に封切り師（鍛冶場の中）。
- 確かめ: 自己テスト `facility`・`town`・`town guide`・`npc`・`lowpoly npc`・`continent`・`record`・`lang` と quick_check。

## 0.9.19（野の装備と封の箱の試遊版。2026-10-31）

- jar `build/libs/skyabyss-0.9.19.jar`。確かめ表 `docs/review/playtest_0.9.19.md`（箱が落ちる・封切り師で開ける・旅嚢の欄・野の装備・箱にならないもの）。変更履歴はサイト（`tools/site/pages_c.py`）。
- 版の前の直し: 自己テスト全段で落ちた 2 つ。野の装備 200 品の物語の一行（`tools/gen/wild_gear/stories.py` → `gen_wild_gear.py` が `skyabyss_wild` へ。`GearCurveSelfTest` がそこも読む）と、封切り師の会話の文を `skyabyss_fac` へ（`gen_box_text.py`。`NpcQuestSelfTest` が読む所）。月の装備・神器の由来の行の鍵も足した。
- 確かめ: 自己テスト全段（上の 2 つのほか失敗なし。直したあと該当の段を流し直して通った）、`quick_check.sh` 全部、jar の中身と版。

## 野の装備（武器 102 本・防具 18 式。2026-10-31。設計 [51](design/51_world_gear.md)）

- 声「ストーリーに沿っての物しか出ていなかったので、他にも出るように」。Lv 帯ごとに職ごとの武器（帯 I〜V で 2・3・3・4・5 本 × 6 職）と防具の一式（3・3・3・4・5 式）。階級はアンコモン〜ヒロイック（帯が進むほど上が混ざる）。どの品も一品一案の形と、自分の能力（約 110 種）。
- 表は一つ: `tools/gen/wild_gear/spec.py` → `python -I tools/gen/wild_gear/gen_wild_gear.py` が `item/wild/WildGearTable`（生成）・言葉（`assets/skyabyss_wild/lang`）・鍛冶屋の品（アンコモン、一章・二章から）を作る。
- Java: `item/wild/`（`WildGearSetup` 登録と引き、`WildGearItems`・`WildArmorItem`、`WildEffect` 能力の種類、`WildGearEvents` 武器の能力、`WildArmorEvents` 一式、`WildLoot` 落とし物、`WildGearSelfTest`）。つなぎ: 由来の行（`ModGear.originKey`）、階級の床から外す（`TierFloor`）、基礎の要求（`GearRequirement`）、マナの戻りと技の待ち（`SkillManager`）、起こす速さ（`Downed`）、持ち方と振り（`WeaponKind`・`MoveSets`・`WeaponClient`）、クリエの並び（`CreativeTabs`）、古い自己テストの除外（`GearTierSelfTest`・`JobSelfTest`）。
- 落とし物: 土地の敵（大陸の土地）・深淵の層・月・雲海の敵から人ごとの抽選、主（名のある強敵と帯を持つ型）は初回必ず。武器は 2/3 で今の職。
- 形: 武器 `tools/rough/lowpoly/weapons/wild/`（`o_wild1〜5.py`・塗り `paint_wild.py`・`build_wild.py`・見直し `look_wild.py`）、防具 `tools/rough/lowpoly/armor/wild/build_wild_armor.py`。入れる `python -I tools/install/install_wild_gear.py [weapons|armor]`。一覧の絵 `docs/review/roughs_wild/sheets/`。撮影 `tools/shots/wild_gear.txt`（`gen_wild_shots.py`）。
- 確かめ: 自己テスト `wild gear`・`gear tiers`・`jobs`・`weapon moves`・`lang` が通った。quick_check の資源・言葉・メッシュも通った。
- 装飾の枠の品 26 品（同日の声「この流れで、装飾も色々追加してほしい。レベルごとに」。設計 51 の 6 章）: 帯ごとに装飾 2・3・3・4・5 と護符 1・1・2・2・3。`WildAccessoryItem`（護符の使い方）、装飾の効果は `WildArmorEvents.active` が一式と一緒に数える、体に付ける所は表の mount（`AccessoryWornLayer`）、手・地面・額縁の低ポリは `AccessoryMeshes.IDS` に足した。形 `tools/rough/lowpoly/weapons/wild/o_wild_acc.py`、入れる `install_wild_gear.py accessories`。撮影は `tools/shots/wild_gear.txt` の終わり（`accequip` で体に付けた姿）。
- まだ: 能力の数値と落ちる確率は試遊で。ヒロイックの防具の一段上の飾り（地まで届く外套・光る継ぎ目・頭の飾り）は 2026-10-31 の磨きで済んだ（`build_wild_armor.py` の `HEROIC`）。

## 封の箱と封切り師（2026-10-31。設計 [52](design/52_loot_boxes.md)）

- 声「敵が直接防具を落とすのをやめたい。Wynncraft のように低確率で箱を落とす形に。同じ種類ならスタック。町に開封してくれる NPC、ルーレット。巨神は専用の箱 4 種、その他はレア度で」。00 の 306〜311 は推奨どおり（金の箱あり・深淵と月も階級の箱・5 人・無料・旅嚢の欄・心臓の数は今の品の数）。
- 仕組み: 抽選は今のまま。当たった装備は渡す直前に箱に替わり、品（特性つき）は人ごとの封の列（`LootBoxData`、保存 `skyabyss_loot_boxes`）へ。開けると古い順。列が空の箱は表から引く。包むのは `TitanLoot.handOver`・`WildLoot.give`・敵の落とし物（`LivingDropsEvent` の一番後）。包まないのは `LootBoxes.direct`（交換・初回の選択・野営の返却）と `keepDirect`（層・区画の主の初回の部位）。
- Java: `loot/box/`（`BoxKind` 19 品、`SealedBoxItem`、`LootBoxSetup`、`LootBoxes` 包む・表・開ける、`BoxService`・`BoxNetwork` 画面、`BoxOpeners` 立つ所、`BoxCommands`、`LootBoxSelfTest`）、`client/box/`（`BoxScreen` ルーレット、`BoxPouchScreen` 旅嚢の四つ目のつまみ）。つなぎ: `AttendantRole.BOX_OPENER`・`FacilityActions` の `boxes`・`PouchConfig.Category.BOX`（袋の枠には並ばない `PouchLayout.onGrid`）・つまみ 4 つ（`AccessoryScreen.addTabs`）・`AccessoryMeshes`（箱の形は `BoxKind.look()`）・告知の口止め（`TitanLoot.announcePieces`・`LegendLoot`）。
- 封切り師: 都と四つの町の鍛冶屋の係の人（`npc_smith`）から 2〜4 ブロックの空いた床に立つ（設計図は触らない）。体は `tools/rough/lowpoly/npc2/cast.py` の box_opener（`install_npc2.py box_opener`）、顔と印は `gen_attendant_portraits.py`・`gen_facility_icons.py`（どちらも全員を描き直すので、ほかの役の絵は git で戻すこと）。
- 形と絵: `tools/rough/lowpoly/weapons/wild/o_boxes.py`（9 形）→ `build_wild.py o_boxes` → `python -I tools/install/install_boxes.py`（帯の数字入りの絵も作る）。言葉と会話 `python tools/gen/loot/gen_box_text.py`。
- 試遊: `/skyabyss play box give <種類> [数]`・`/skyabyss play box open`。撮影 `tools/shots/box_ui.txt`（画面と形）・`tools/shots/box_openers.txt`（都と町の立つ所）。
- 確かめ: 自己テスト `loot boxes`・`pouch`・`facility`・`lowpoly npc`・`wild gear`・`command gate`・`lang` ほかが通った。quick_check も通った。
- まだ: 箱の落ちる確率の手ざわり、ルーレットの長さ、町ごとの封切り師の台詞（今は一人分）は試遊で。

## 0.9.18（全体監査の直し。2026-10-31）

- 監査の書類 `docs/review/audit_2026-10-31.md`（通ったもの・おかしい所・書類の古さ・足したいもの・まだやること）。
- 直し:
  - `quick_check.sh` が落ちていた 2 つ: `layout.json` の道の端（汐守の東の脇道、0.9.5 の手直し）を `gen_continent.py` の `HAND_ROAD_ENDS` で再現。`check_stairs.py` の許す例に、土地の村と都の広場の縁（床の高さの階段の帯）、祠と環状列石の台座の面取り、汐守の宿の正面の飾りの階段を足した（どれも見た目の帯で、段ではない）。
  - 看板のはみ出し（1 行 90 px）: ja 5 行・en 98 行を短く割り直した（`sign.skyabyss.*`。足した行の ja は空）。
  - 一章の地上の敵 3 種の足音と死の音: `sounds.json` がバニラに無いファイル名（`block/stone/step1` など）を指していて鳴らなかった → 出来事参照（`type: event`）に。
  - `RecordService`・`TownGuide` の人ごとの時刻の表を `PlayerMaps.track` で包んだ（ログアウトで消える）。`ChapterCards` は `ArcSetup.onLogout` が消していたのでそのまま。
  - 新しい検査 `tools/audit/check_resources.py`（JSON・モデルの参照・blockstate・ogg・バニラの音のファイル名・看板の幅・ja/en の鍵の対）。`quick_check.sh` に `check_resources`・`check_lang_keys`・`mesh_audit --check` を足した。
  - 自己テストを新しい大陸ワールドで流す `tools/dev/selftest_fresh.sh`（前は scratchpad にあって消えた）。
  - 書類を 0.9.17 の形に: README・`docs/beta_how_to_play.md`（終章まで、J は見るだけ、3b を 0.9 に、既知の問題）、サイト（`tools/site/pages_a.py`・`pages_c.py` → `python tools/site/build.py`。既知の制限と v0.9.17 の変更）、01（M5 済・M6 仕上げ）、14（0.9.17・終章）、03（神器は 50 の形）、46・`MoonEnd`（終章へのつなぎ）、37（五章〜終章の開発コマンド 2.7）、設計 README の 04 の行。
- 自己テスト全段を新しい大陸ワールドで流した（22 分。失敗は深淵の伝説の書き換えの数の期待値だけ → 61 に）。`RebirthFx` の文字の表示に `alignment`（バニラの「Display entity Not a string」のログ止め）。
- 監査の「足したほうがいい・まだやること」（同日）:
  - 設計書の古い案を今の形に: 03 の 13 章（神器は 50 の形。クロスダンジョン装備・天と淵のセットは作らない）と 14 章（今あるエンドコンテンツの表。ハード版・ボスラッシュ・8 人レイドなどは作らない）、2.1 の宝箱の行（`SiteTreasure`）、04・27（天職は 43・48 で削った）、07（修理そのものが無い）、10（ループごとの頼み 3 つは作らない）、11（備えの台詞の分岐は付けない）、12・`WeathercockItem`（回る動きは付けない）、03 の護符の枠（採らない）。
  - 配布の手順 `docs/release.md`（版上げ・確かめ・作る・渡すもの・記録）。
  - `docs/review` の掃除: モデルを入れるスクリプトが読まない出力（音楽と声の見本・武器と防具の比べ頁・日付の報告の絵・古い一覧 177 ファイル）を消した。残りは install/gen の入力か、未選定の案。
  - 取り込み済みの `wip/*` 79 本の削除は auto mode に止められた（`git push origin --delete $(git branch -r --merged m1-sky-pass | grep origin/wip/ | sed 's|  origin/||')` と `git branch -d wip/continent-only wip/skill-tree-v2` をユーザーが流す）。
- 確かめ: `quick_check.sh` 全部 OK。自己テストは全段 1 回と、直した `abyss`・`rebirth` の流し直しが通った。
- 前の私の頁（35 の空見の砦。「今は話だけ」だった分）: 心臓を二つ止めた人（三章）に砦の老兵が一度だけ破れた頁 `keep_page` を渡し、読むと伝承「本丸の頁」（`chapter1/keep_prev_page`。文は `skyabyss_storm` の lang。前の空見が嵐と影を見誤るなと書いた一枚。筆の止め方が自分と同じ）が書に移る。会話の `give` に `page` を付けると破れた頁になる（`quest/Action`）。自己テスト `facility`。
- 次: 終章の通しの試遊とその声での直し（ユーザーは「ある程度完成してから」）。

## 0.9.17（終章の試遊の途中の声）

- 技の木の節の大きさを種類で変えた（`SkillScreen.v2Size`）: 使う技 30（金の縁で囲む）・型の要 28・常に効く 24・段と刻印 18・基礎 15。当たりは 18 より小さくしない。声「どれがスキルかわからない」。
- `/skyabyss cloudsea enter` が「コマンドの引数が正しくありません」で止まった: 後ろに空白が残っていた。この作品のコマンドは、全角の空白を半角に・続く空白を一つに・前後の空白を取って読み直す（`CommandTidy`）。
- 撮影の道具 `tools/dev/mc_run.sh`: 古い枠に owner.txt が残っていると消せずに止まり続けた（rmdir → rm -rf）。
- 確かめ: 撮影 `tools/shots/skill_tree_sizes.txt`（見直しの絵は 10-31 に消した。戦士の幹・盾の枝・魔法使い）。自己テスト `cloudsea`（空白入りのコマンド）・`skills`・`jobs`・`lang` は通った。

## 終章「雲海」その 10: 通しの点検（0.9.16）

- 頼みのつながり: 雲の上へ → 鐘 → 灯 → 炉 → 約束 → 座。どれも前の頼みを終えると自動で始まり、最後の段で済む（語り部へ戻らなくてよい）。関所は前の島の頼みが済んでいるかで見る。
- 直し:
  - 座の第 2 形態の目の視線が効いていなかった（灯の島の見張りが、灯の島の外の人の重さを毎 tick 外していた）。座の中は外さない（`LampIsle`）。
  - 敵と座の手の「初めての一言」が、サーバーで一度きりだった（組の二人目・別のワールドに出ない）。人ごとに一度に（`FinaleHints`）。
  - サーバーが止まったら、終章の静的な状態（座の戦い・付いて歩く相手・待っている打ち・鐘の鳴った時刻・巻き戻りの相など）を片付ける（`FinaleReset`）。一人用でワールドを替えても前の状態が残らない。
  - 約束の島の四人が座まで付いてきたら天幕の前へ帰す（`PilgrimSeat.sendSelvesHome`。座の第 3 形態には座の四人が出る）。
  - 結末のあとの都の鐘は 1 分に一度まで（昼夜が止まっていると鳴り続けた）。
- 試遊の確かめ表 `docs/review/playtest_finale.md`（島ごとに見る所と、跳ぶ開発用コマンド）。
- 次: 試遊（終章を通しで）と、その声での直し。

## 終章「雲海」その 9: クリア後「巡礼の試し」（0.9.15。設計 [50](design/50_finale.md) の 4.6）

- 座の台の雲の暦（日時計。`FinaleWorld` の版 7 で座を流し直す）→ チャットで今の段への挑戦（`PilgrimTrial`）。段 1〜5 で体力と傷に倍率、段 3 から技の間が三分の二、段 5 は形態ごとの重ね（手の円が三つ・目の隙が半分と光の雨が倍・月の巻き戻しが三度）。報酬は心核の欠片 3＋段と雲上の心核 段×10%、段 5 を初めて越えると伝承の頁。
- 直し: 生き物の体力の上限 1024 で、目（1500）と月（1800）が黙って 1024 に切られていた。`CloudMob.tuneCloud` が上限を超える分を「受ける傷の倍率」（`toughness`）にして、決めた硬さにした。
- 確かめ: 自己テスト `cloudsea`（暦・閉じた人・段の倍率と体力・技の間・報酬と記録・片付け）。撮影はしていない。段ごとの難しさは試遊で。
- 次: 終章の作る順はすべて済み。残りは試遊（終章を通しで）と、その声での直し。

## 終章「雲海」その 8: 神器（0.9.14。設計 [50](design/50_finale.md) の 4.5）

- 6 職に一つずつの神器（アーティファクト、必要 Lv 50、一品一案。`o_divine.py`・雲海の塗り `paint_divine.py`）: 天穹の剣・七つ鐘の大鎚・暁の灯杖・巡礼の羽弓・巻き戻りの短剣・約束の鈴杖。固有の効果（`DivineGearEvents`）と、職の幹の技の書き換え（`Rune.DIVINE_*`・`LegendRewrites`）。
- 巡礼者の月を初めて倒すと、いまの職の神器と雲上の心核（`DivineGear.reward`。`SeatArena.onDeath`）。職の間で職を替えると神器も替わる（`JobChoice.switchTo` → `DivineGear.swap`。強化そのまま）。燃料は心核の欠片で、七つの心核はしゃがんで使うと欠片 5 つに砕ける。
- 確かめ: 自己テスト `cloudsea`（6 本と職・受け取り・持ち替え・心核・書き換え）と装備の検査。撮影 `tools/shots/divine_relics.txt`（見直し `docs/review/finale/divine_relics.jpg`）。数値と書き換えの手ざわりは試遊で。
- 次: 作る順 6（クリア後: 巡礼の試し）。その 9 で済み。

## 終章「雲海」その 7: 結末と結末のあとの世界（0.9.13。設計 [50](design/50_finale.md) の 4.4）

- 月を倒したあと、座のそばでチャットの選び（[鎮める]・[壊す]・条件を満たした人だけ [受け入れる]）。選ぶと結末の紙芝居（`finale_end_<結末>`、5 枚ずつ。`tools/story/pixel/finale_end.py`）→ 見終えると記録（人ごと。`FinaleEnding`）、終章の完の札・伝承の頁・結末の札。座に触れ直すと選び直す／挑み直す。
- 結末のあとの世界（本人だけ）: 鎮める＝月が遠く空が晴れ、巨神の歌がときどき・都の鐘が明るく／壊す＝空の裂け目・鐘が低く／受け入れる＝翼が昼の空を巡り・鐘は鳴らない。どれも雲の中の目はもう出ない（`CloudEyeEvents`）。空は `ChapterSky.stage` が結末の段を先に返し、絵は `client/sky/EndingSkyClient`。
- 確かめ: 自己テスト `cloudsea`（結末の条件・記録・空の段・素材）。撮影 `tools/shots/finale_endings.txt`（見直し `docs/review/finale/ending_sky.jpg`、語りは `docs/review/finale/ending_reels.jpg`）。紙芝居の読み心地と、空の見え方は試遊で。
- 次: 作る順 5（神器）。その 8 で済み。

## 終章「雲海」その 6: 巡礼者の座とラスボス（0.9.12。設計 [50](design/50_finale.md) の 4.3）

- 月見の岩の上の北の縁の橋の台から光の橋が上る。約束の島を終えていない人は橋の関所で戻る（`PilgrimSeat.gate`）。
- 地形 `seat.json`（`FinaleWorld` の版 6）: 内の固い床（金の八芒の星）・鐘雲の輪（`BellIsle` に足して同じ見張りで固める）・四隅の鐘と陰の柱・南北の道・北の台の巡礼者の座。
- 戦い（`SeatArena`）: 座に触れると始まる（頼み `main_finale_seat`）。第 1 形態 手 二つ（叩きつけのあと床に突いたまま＝打てる隙・輪を削ると鐘の固まりが解ける）→ 第 2 形態 目（回る筋・凝視は柱の陰・閉じると傷 ×1.5）→ 第 3 形態 月（月の引き・巻き戻し・満ち欠けの刃・月落とし）。第 3 形態で前の自分たち四人が加勢し、術は約束の島と同じ（`PromiseIsle.active` が座の四人を先に見る）。月が倒れると合図 `finale_lord_pilgrim`、語りの最後に「結末は次の版」。
- 確かめ: 自己テスト `cloudsea`（橋・関所・鐘雲の輪と鐘・三形態の部位・四人の盾の円・片付け）。撮影 `tools/shots/cloudsea_seat.txt`（見直し `docs/review/finale/seat_shots.jpg`）。戦いの長さと難しさ（体力の合計 4820）は試遊で。
- 次: 作る順 4（結末の場面と結末のあとの世界）。その 7 で済み。

## 終章「雲海」その 5: 島 4「約束の島」（0.9.11。設計 [50](design/50_finale.md) の 4.2.4）

- 大炉の上の主の間の北の縁から光の橋が下る。炉の島を終えていない人は橋の関所で大炉の上の入口へ戻る（`PromiseIsle.gate`）。
- 地形 `isle4.json`（`FinaleWorld` の版 5）: 前の月の野営（月の砂の地面・くぼみ・焚き火と四つの石の座・四人の天幕と持ち物・刺さった巡礼者の羽）、北の端の月見の岩（上に主の間 半径 12、北の縁に座への橋の台 `seat_bridge`）。
- 前の自分たち: 天幕の前に月の四人の幻（`TownGuestEntity` moon_past1〜4、名札に術の名）。話して「並ぶ」（会話 `fin_promise_*` が合図 `finale_promise_<k>`）と付いて歩き、術で助ける: 谷 = 盾の円（傷半分・押されない・審きの光を防ぐ）・崖 = 引きつけ・野 = 一人でも倒れて起こしてもらえる（`Downed.mayDown` / `tick` が `PromiseIsle.canRaise` / `holds` を聞く。60 秒に一度）・淵 = 癒し。四人と並ぶと月見の岩の扉が開く（頼み `main_finale_promise`）。
- 敵: 羽の番人（槍の突きで押し出す）・落ち羽（光の筋に羽根の矢）。主: 巡礼者の羽（羽ばたき・羽の雨・先触れの印・審きの光・落ち羽を呼ぶ）。模型は一品一案（`docs/review/cloud_roughs/`）。
- 確かめ: 自己テスト `cloudsea`（橋・関所・幻・並ぶ・扉・盾の円・起こす・筋・主）。撮影 `tools/shots/cloudsea_isle4.txt`（見直し `docs/review/finale/isle4_shots.jpg`）。戦いの手ざわりと、四人の術の効き方は試遊で。
- 次: 作る順 3（巡礼者の座とラスボス「雲上の巡礼者」）。その 6 で済み。

## 終章「雲海」その 4: 島 3「炉の島」（0.9.10。設計 [50](design/50_finale.md) の 4.2.3）

- 灯台の上の主の間の北の縁から光の橋が下る。灯の島を終えていない人は橋の関所で灯台の上の入口へ戻る（`ForgeIsle.gate`）。
- 地形 `isle3.json`（`FinaleWorld` の版 4）: 前の灰炉（灰と黒石の斑の地面・冷えた溶け跡・崩れた鍛冶場 4・割れた金床・燃え残りの焚き火）、かけら 3（吹子場・鋳型場・炭置き場）と崩れた石の橋、北の端の大炉（煉瓦。上に主の間 半径 12）。
- 巻き戻り: 橋の崩れた所と主の間の外の輪は戻り石（`skyabyss:rewind_stone`）。10 秒ごとに 1 秒の残り火の輪郭 → 3 秒だけ石（最後の 1 秒は薄い）。橋ごとに時がずれる。主の間の輪は戻し手の段 2 から巻き戻る。敵と主の技は同じ所でもう一度起こる（`Echoes`。残り火の印が予告）。炉 3 つで扉が開く（頼み `main_finale_forge`）。
- 敵: 灰の打ち手（大槌 → 2.5 秒後にもう一度）・戻り火（火の玉 → 2.5 秒後にもう一度）。主: 戻し手（打ち下ろし・薙ぎが 3 秒後にもう一度、段 3 は二度・足跡が燃える・打ち手を呼ぶ）。模型は一品一案（`docs/review/cloud_roughs/`）。
- 確かめ: 自己テスト `cloudsea`（橋・関所・炉・扉・戻り石の組と相・打ちの範囲・主）。撮影 `tools/shots/cloudsea_isle3.txt`（見直し `docs/review/finale/isle3_shots.jpg`。崩れた橋は時をずらした 4 枚）。戦いの手ざわりは試遊で。
- 次: 島 4「約束の島」（前の自分たちと並ぶ）。その 5 で済み。

## 終章「雲海」その 3: 島 2「灯の島」（0.9.9。設計 [50](design/50_finale.md) の 4.2.2）

- 鐘の島の主の間の北の縁から光の橋（`skyabyss:light_path`）が下る。鐘の島を終えていない人は橋の関所で主の間の入口へ戻る（`LampIsle.gate`）。
- 地形 `isle2.json`（`FinaleWorld` の版 3）: 前の潮の浜（砂と砂利・潮だまり・流木・伏せた舟・干し網の竿）、漁師の小屋 5、海食の岩の柱 12 と崩れた石垣 5（陰）、西・東・南東の突堤と漁の灯、北の端の傾いた灯台（灯の部屋の帯・上に主の間 半径 12・石柱 4）。
- 視線: 灯の部屋から光の筋が 12 秒で島を一周。見えていると重い（歩み半分・跳べない。属性の一時の変え）、2 秒より長いと毎秒 最大体力 6%。陰で外れる。灯蛾に灯されると陰でも見つかる。漁の灯 3 つで扉が開く（頼み `main_finale_lamp`）。
- 敵: 網打ち（扇へ網、1.5 秒の足止め）・灯蛾（光って触れると 6 秒灯す）。主: 灯守の目（回る筋・凝視は石柱の陰で避ける・凝視のあと 3 秒 目を閉じて傷 1.5 倍・灯の雨・灯蛾を呼ぶ）。模型は一品一案（`docs/review/cloud_roughs/`）。
- 確かめ: 自己テスト `cloudsea`（橋の途切れ・関所・灯・扉・筋と陰と灯される・重さ・主）。撮影 `tools/shots/cloudsea_isle2.txt`（見直し `docs/review/finale/isle2_shots.jpg`）。戦いの手ざわりは試遊で。
- 次: 島 3「炉の島」（巻き戻り）。その 4 で済み。

## 終章「雲海」その 2: 島 1「鐘の島」（0.9.8。設計 [50](design/50_finale.md) の 4.2・4.2.1）

- 四つの島はそれぞれ座の決まりを一つずつ教える（鐘で雲が固まる・視線・巻き戻り・前の自分たちと並ぶ）。島 1 を通しで作った（地形・仕掛け・敵 2 種・主・頼み・伝承 3 頁）。島 2〜4 はまだ。
- 地形 `isle1.json`（`FinaleWorld` の版 2）: 着く所から北へ白い橋（地面 64 → 島の南の端の大通りの高さ）、前の都の大通り・広場・崩れた家 6、北の端の折れた鐘楼（扉 → 上の主の間）、まわりのかけら 3（西・東・北西）と鐘雲の道。
- 鐘雲 `skyabyss:bell_cloud`: 鐘（半径 16）が鳴ると 20 秒固まる（終わりの 4 秒は薄く）。亡者と主の無音でほどける（`BellIsle.silence`）。かけらの鐘 3 つで扉が開く（頼み `main_finale_bell` の段 door）。
- 敵: 鐘の亡者（割れた鐘を振り上げて近くの鐘の固まりを消す）・霞鳥（影の円 0.8 秒 → 急降下と押し戻し）。主: 最後の鐘守（振り下ろし・鐘の波・無音・亡者を呼ぶ・段 3 で床の半分が鐘雲）。模型は一品一案（`docs/review/cloud_roughs/`）。
- 確かめ: 自己テスト `cloudsea`（橋・鐘・鐘雲・扉・敵の素材・主の出る所・床の崩れと戻り）。撮影 `tools/shots/cloudsea_isle1.txt`。戦いの難しさ・手ざわりは試遊で。
- 次: 島 2「灯の島」（視線）。その 3 で済み。

## 終章「雲海」その 1: 舞台と行き方（0.9.7。設計 [50](design/50_finale.md) の 4.1）

- 00 の 295〜305 はすべて推奨の A（舞台 雲の上の海・四体の巨神に運ばれる・巡礼の道・01 の三形態・座で選ぶ・受け入れるは記憶 12・結末で空と都が変わり選び直せる・6 職の神器・巡礼の試し・Lv 50・結末は人ごと）。
- ディメンション `skyabyss:cloudsea`（雲の海 y 36〜39 = `story/finale/CloudBlock`、夜明けに止めた空と霧 `client/CloudSkyClient`）。雲の下へ落ちると最後に立っていた所へ戻り体力 2 割（`FinaleWorld.rescueCheck`）。
- 着く所「巡礼の門の島」（`tools/gen/world/build_cloud_arrival.py` → `data/skyabyss/finale/arrival.json`、流し込みは `FinaleTerrain`）: 白い広場・崩れた巡礼の門と門の鐘（合図 `finale_gate`）・四体の記念の台・帰りの台・北の橋の台（印 `bridge_n`）・まわりの雲のうねり。
- 行き方: メイン「雲の上へ」`main_finale_ascend`（月の核のあと。語り部の数え歌のななつめ → 大鐘楼の門 → 鐘）。初めての門は紙芝居「巨神の昇り」`finale_ascend`（`tools/story/pixel/finale_ascend.py`）、二度目からは月／雲海のチャットの二択（`story/finale/FinaleGate`、`/skyabyss_gate`）。開発用 `/skyabyss cloudsea enter|leave`。撮影 `tools/shots/cloudsea_arrival.txt`。自己テスト `cloudsea`。
- 次: 作る順 2（雲の島 1〜4 と敵）。島 1 はその 2 で済み。

## 終章の前の残り（0.9.6）

- 章ごとの未作成の要素を作った（01 の 4.1 は「人が通しで見ていない」と終章だけになった）: 縁の音と暮らしの変化（`story/ChapterOmens`）、章の見る者が巨神の方角を向く（`WatcherEvents.startTitan`、画面は `TITAN_FACING` の印で振り向かない）、一章の鯨の碑・四章の灰の碑 各 3 枚（`story/ChapterTablets`、部品 `ch1_tablet`・`ch4_tablet` は `build_chsites.py`、伝承 `chapter1/whale_tablets`・`chapter1/ember_tablets`）、前の私の足取りの頁の検査（`LoreSelfTest.FOOTSTEPS`）。
- 古い透け: 透けている三角形を裏返す直しを試したが、この作品のメッシュは裏面も描く（NoCull）ので穴は残り、隣と向きが食い違うだけだった（向きの食い違い 0 → 173）。取り消した。残る透けの多くは足の裏・盾の下面など真下からしか見えない所（骨の迫撃兵の盾 633・語り部の足の裏・泡ふぐのひれ・剣守の胴の底など）。直すならラフの形から。祠の剣の開いた辺は塞いだ（開いた辺は全体で 0）。
- 汐守の町の階段の 9 件は家の土台を回り込む段（角で向きが変わる）で、撮って崩れのないことを見た（誤検出）。

## 終章の前の点検（全部の自己テスト・静的な点検・メッシュの点検）

- 全部の自己テスト（大陸の新しい世界）: 195 通過。落ちたものを直した: 月の装備 17 点の由来の一文が無かった（説明欄の最後の一行。`skyabyss_gear_story`）、町の案内人の体が無かった（都の案内人の体を写す `tools/install/install_town_guide_body.py`、`RegionFolk.body`）。ほかは検査側の古さ: 鍵の数 22（構えを消した）、ヒロイックの書き換え 55（月の 6 本）、深淵のルーンの検査（木 v2 で段を上げる途中の節で刻印が付く）、六章は「名前だけの章」でない（`Chapters.reserved` は終章だけ）、町の人の絵は v2、淵の前の自分、焼けた羽守の名前の lang。
- 月の地図の絵が作り込みの二回目の前の地形のままだった（`gen_moon_map.py` で描き直し）。月の核の傾きの浅い輪 ring_a が、入れるときの付け根の処理で折れて裏面が透けていた（4114 画素）→ 輪を継ぎ目のない環にし、珠と留め軸二本でつないだ。残像の板のブロックの縁の面。英語の目的の一行が帯からはみ出していた。layout.json の末尾の改行。
- 弓の技の傷の基準: 技の傷は近接の攻撃力から出していて、弓を持つと 1 のままだった（深淵の嵐の弓の天落としが 0.4、罠もほとんど効かない）。弓を持つ人は、弓の満月の一矢の傷（6 × 弓の倍率、強化込み）を基準にする（`SkillActions.attackDamage`）。技・木・職・深淵・心臓の釣り合いの自己テストは通る。
- 書類: 01・14・README の六章の書き方を今の形に（全部できた・試遊版 0.9.5）。
- 残したもの: 剣守の胴の真下の透け（442 画素、下からしか見えない）、ほかの古い透け（語り部・骨の迫撃・影縫い・泡ふぐ ほか 150〜630 画素）、非多様体の辺。階段の点検の 67 件はほとんど腰かけ（汐守の町の 9 件は場所を特定できていない）。01 の 4.1「まだ作っていない」（章ごとの見る者の向き・縁の音・暮らしの変化など）。終章の設計（01 の M5）はまだ。

## 0.9.2 の試遊のあと（二人での試遊の声への直し。試遊版 0.9.3・0.9.4）

- 導入の順: 島の道の間（渡し船の前）は章を序章に保つ（野営地の帳面で一章の頼みが始まり、10-28 の「頼みの章を優先」で一章になっていた）。章の場所は島に置かず、島の拠点は都の南の門の外に読み替え、島のまわりに置いた古い計画からは人を外して置き直す（旅の職人の荷車が島にあった件）。職を選ぶ画面は開幕の絵芝居とカメラの場面のあと（絵芝居の上に開いて飛ばしていた）。島の新しい人を legacy と取り違えない。島の道の間は空の通過を起こさない（鯨が最初から見えた・BGM が止まった）。島で時間の湧きを止めた。[40](design/40_rpg_world_and_start.md) の 2 章・7 章。
- 宝箱と樽は人ごと（`loot/SharedChests`。右クリックで写しを受け取り、人ごと・箱ごとに 20 分待つ）。倒れたら宿の寝台を含めていちばん近い休み場で目覚める。[40](design/40_rpg_world_and_start.md) の 2 章。
- 大樹を高さ 45・幅 30 に作り直した（`tools/gen/world/build_great_tree.py`。建ってある大樹は建て直さない）。土地の町の広場に町の案内人（[35](design/35_capital_facilities.md) の 6.12）。割れ殻の洞の鳴り石に光と手がかり（[07](design/07_npc_quest.md)）。骨玉運びの敵を減らした（[32](design/32_titan_raid_route.md) の 9.1）。遠くからの矢はボスに弱まる（[47](design/47_combat_difficulty.md) の 2.5）。BGM の場所を生き返り・移動のあとに送り直す（[49](design/49_bgm.md)）。
- 短剣と斧の持ち方（0.9.4。ユーザーの答え「一人称で」）: 宿り木の短剣と採取の斧のメッシュは刃が逆向きに作ってあり、一人称で刃が後ろを向いていた。`WeaponItemRenderer.EDGE_FLIP` の品は柄の軸で半回転して描く（どの見え方でも）。ほかの短剣（三日月・脈打つ短剣など）は前へ反っていて直さない。撮影 `tools/shots/hold_fp_1029c.txt`。

## 2026-10-31 夜（BGM を軽く。試遊版 0.9.2）

- BGM 17 曲を音源から書き出し直した（`tools/gen/music/synth.py` の `QUALITY` = 0.2、約 96 kbps。前は 0.5 で約 160 kbps）。26 MB → 15 MB、jar 74 MB → 65 MB（`build/libs/skyabyss-0.9.2.jar`）。ループのつなぎ目は全曲で聞こえない段差のまま、自己テスト `music` 通る。聞き心地はまだ耳で確かめていない。
- ほかの中身: プログラム 9.8 MB（うち自己テスト 2.1 MB）、低ポリの絵 7.1 MB（使っていない絵はほぼない）、画面の絵柄パック A・B・C 1.8 MB（B・C は選べる絵柄として残す）。

## 2026-10-31 夕（試遊版 0.9.0 と、試遊と並行の監査）

- 試遊版 `build/libs/skyabyss-0.9.0.jar`（確かめ表 `docs/review/playtest_0.9.0.md`）。
- 監査で直した（0.9.1 の jar `build/libs/skyabyss-0.9.1.jar` に入れた。確かめ表 `docs/review/playtest_0.9.1.md`。`moon memories`・`moon gear`・`gear tiers` 通る）:
  - 月のヒロイックの必要 Lv が 52 だった。レベルの上限は 50（その先は転生）なので着けなかった。`MoonGear.requiredLevel` を上限で止め、全部 50（03 の 12.2 の表も）。
  - 記憶の石が二つ置かれうる（流し直さなかった区画に前の石が残る）。`MoonMemories.place` は石がある区画を飛ばす。
  - 前の版の世界で区画を流し直したとき、もう月にいる人が岩に埋まりうる。流し直したら、ぶつかっている人を着く所へ。
- 見て問題なしとしたもの: 翻訳の鍵の抜けなし（静的に洗った。拾ったのは文字を繋ぐ鍵の頭とバニラの鍵だけ）、登録した品のモデルと名前、ブロックの状態、武器の職の見分け（ID の語）。

## 2026-10-31 午後（崖・野・淵の作り込みの二回目。設計 [46](design/46_moon.md) の 4.4.3・4.5.3・4.6.3）

- 崖（`build_moon_cliff.py`）: 頂の尖りの群れ 24・段の縁の石・岩山と外の崖の裾の崩れた斜面。野（`build_moon_field.py`）: 溝の壁の層の筋と割れ目と根の石・塔の控え壁と硝子の窓と梁・小さなクレーター 14。淵（`build_moon_rift.py`）: 縁の歯 106・主の間の割れた鏡の柱 7。
- 月の世界の版 12 で三つを流し込み直し、記憶の石を置き直す（新しい世界では最初の組み立てだけ）。
- 確かめ: 月の 10 段すべて通る。撮影 `tools/shots/moon_sections_pass2.txt`、見比べ `docs/review/moon/sections_pass2.jpg`。
- 六章で作る残りはない。まだ: 試遊。

## 2026-10-31 昼（六章の仕上げ: 記憶の断片と伝承・位相の挑戦の段 3/5・月のヒロイックの書き換え・谷の二回目。設計 [46](design/46_moon.md) の 4.3.3・4.8.1・4.9）

- 記憶の断片 12 と伝承 19 頁（`story/ch6/MoonMemories`・記憶の石 `MoonMemoryStoneBlock`・`tools/gen/ch6/gen_moon_lore.py`）。区画ごとに 前の自分と話す・記憶の石・主。十二で「見る者」、核のあとで「月の核」、段 5 の記念の札 5。`chapter3/unknown_*` の枠は消した（`LoreSelfTest` は六章も中身ありに、文は `skyabyss_moon` も読む）。
- 位相の挑戦: 段 3 から技の間が三分の二（`MoonMob.paced`）、段 5 の位相の重ね（`MoonMob.overlaid`。谷 = 影の手、崖 = 傾き 1.6 倍、野 = 満ち引き 2 倍、淵 = 残像 2 秒、核 = 欠片の回り 1.6 倍）。
- 月のヒロイック 6 本の技の書き換え（`Rune` の MOON_*、`LegendRewrites`）。刻印は 85（`SkillSelfTest`）、書き換えは 55（`BuildsSelfTest`）。
- 谷の作り込みの二回目（`build_moon_valley.py`。月の世界の版 11 で谷を流し込み直す）: 入口の段々の下りと崩れた斜面・入口の石柱一対・崖の岩棚 14・台地の立ち石 5 と方解石の輪。見比べ `docs/review/moon/valley_pass2.jpg`。
- 確かめ: 月の 12 段（`moon memories` を足した）・`lore`・`skills`・`gear tiers` の 13 本すべて通る。
- 気づいたこと: 撮影の世界は名前が同じだと前の版の月のまま残る（版が上がっても、その世界ではもう上がった後）。地形を直したら台本の `world` の名前を変える。
- まだ: 試遊（六章全体）。崖・野・淵の作り込みの二回目。

## 2026-10-31 朝（月の職の武器をレアとヒロイックの組に。[03](design/03_gear_and_endgame.md) の 12.2）

- 声「なんで一つだけヒロイック？全部レアとヒロイックでそれぞれひとつずつ」: 6 職それぞれにレア一本とヒロイック一本。エピックだった 5 本はレアに（数値はそのまま、弓だけ矢 ×2.4 → ×2.0。残像の矢込みで近接の 0.85 倍に合わせた）、戦士のレア「月光の長剣」とヒロイック 5 本（望月の大鎚・朔の杖・星落ちの弓・朔影の短剣・月暈の鈴杖）を足した。固有効果は `MoonGearEvents`。
- ヒロイック 5 本は巨神のヒロイック武器と同じく図鑑に入れず、月の核の別枠（`MoonGearLoot.heroicDrops`、一本ずつ 2% × (1 + 0.1 × 段)）。残月の太刀と環は図鑑の専用の逸品のまま。
- 確かめ: `moon gear`・`gear tiers`・`moon trial`・`weapon moves` 通る。撮影 `tools/shots/moon_gear.txt`、見比べ `docs/review/moon/gear_weapons_game.jpg`（行ごとに左 2 枚がレア、右 2 枚がヒロイック）。
- まだ: 試遊。ヒロイックの技の書き換え（巨神のヒロイックにある英雄の段のルーン）は月の 6 本には付けていない。

## 2026-10-31 未明（クリア後の位相の挑戦と月の装備。設計 [46](design/46_moon.md) の 4.8.1・[03](design/03_gear_and_endgame.md) の 12.2）

- 月の装備 12 品と素材 3 つを入れた（`story/ch6/gear/`）: 月渡りの装束 4 部位（低ポリ `lowpoly/armor/moon_walker`）、武器 6 本（鎚・杖・弓・短剣・鈴杖はエピック、残月の太刀はヒロイック）、満月の環、月の涙、月の欠片・月の心核・共鳴の欠片（月）。固有効果は `MoonGearEvents`、報酬は `MoonGearLoot`（区画の主は初回にその主の鎧の一部位、核は初回に心核）。
- 見た目は一品一案: 武器 `tools/rough/lowpoly/weapons/one/o_moon.py`（塗り `paint_moon.py`。`build_one.py o_moon`）、鎧 `tools/rough/lowpoly/gear_gaps/armor_moon.py`、平らな絵と月の暦 `tools/gen/ch6/gen_moon_gear_assets.py`。入れるのは `tools/install/install_moon_gear.py`。持ち方・振りは同じ型の武器を借りた（`WeaponKind`・`MoveSets`・`gen_weapon_moves.py`・`weapon_display.py`）。
- 位相の挑戦（`story/ch6/MoonTrial`）: 着く所の南の月の暦（月の世界の版 9）→ チャットで主を選ぶ → 段 1〜5 の強い主。倒すと人ごとの段の記録と報酬（ふだんの主の報酬もここで配る）。段 3 の技の間・段 5 の「位相の重ね」と記念の札はまだ。
- 確かめ: 自己テスト `moon gear`・`moon trial`・`gear tiers`（表に六章の行を足した。月は専用の逸品が 2 つ）と月の 7 段すべて通る。撮影 `tools/shots/moon_gear.txt`、見直しの絵 `docs/review/moon/gear_weapons_game.jpg`・`gear_armor_game.jpg`。
- 気づいたこと: 撮影で `armortier` を決め打つ前に初めの旅装を脱がないと、旅装の cube の絵が無くて落ちる（台本で `cmd clear @s` してから）。
- まだ: 試遊（効果の手ごたえ・暦からの流れ）、記憶の断片と伝承。

## 2026-10-30 深夜（六章 月の核。設計 [46](design/46_moon.md) の 4.7・4.7.1）

- 月の核を一つ通しで作った: 核の間（`build_moon_core.py` → `core.json`、`story/ch6/core/MoonCore`。月の世界の版 8）、着く所の北の核の門、主「月の核」（欠片の盾 → 巻き戻し → 満月落としと四人の加勢）、頼み `main_moon_core`、前の自分たちの門と核の間での言葉、語りの紙芝居 `moon_core_end`（5 枚）、六章の「完」の札を核のあとへ。
- 月の自己テストは先に `moon (load)`（`MoonTestLoad`）で谷〜核の塊を読み込むようにした（読み込みきる前に置いた生き物が見つからず、谷・崖・野のテストが揺れていた）。月の 7 段すべて通る。
- 撮影 `tools/shots/moon_core.txt`、見直しの絵 `docs/review/moon/core_shots_2026-10-30.png`。
- まだ: 試遊。満月落としの予告と四人の術はゲームの中で動きを見ていない（自己テストでだけ確かめた）。

## 2026-10-30 夜（六章 区画 4「下弦の淵」。設計 [46](design/46_moon.md) の 4.6・4.6.1）

- 区画 4 を一つ通しで作った: 地形（`build_moon_rift.py` → `rift.json`、`story/ch6/rift/MoonRift`。月の世界の版 7）、自分の残像（`EchoShade`・`RiftEcho`・下弦の鏡・残像の板）、二つの板の門・残像の渡り・灯りの回廊、敵の残像（`RiftRemnants`・`mob/RiftMob`）、敵 3 種と主「残月の剣守」（主の残像が 3 秒遅れでなぞる）、前の自分（四人目「鏡の写し手」）、頼み `main_moon_rift`、会話、文、地図、絵。
- 模型は一品一案（ラフの並べた絵 `docs/review/moon_roughs/rift_roughs_2026-10-30.png`。直しの声が来たら `tools/rough/lowpoly/moon/<id>.py` を直して `gen_moon_mobs.py <id>`）。
- 「ここまで」の札は淵を終えた人に出る形に移した（野のテストも合わせた）。文は「月の核から先は次の版」。
- 確かめ: 自己テスト `moon rift`（先に `moon rift (load)`。月の 6 段すべて通る）。撮影 `tools/shots/moon_rift.txt`、見直しの絵 `docs/review/moon/rift_shots_2026-10-30.png`。
- まだ: 試遊（残像の操作・渡りの難しさ・回廊・剣守の残像の読みやすさ）。自分の残像の再生と剣守の残像の当たりは撮影では動きを見ていない（自己テストでだけ確かめた）。

## 2026-10-30 夕（六章 区画 3「満月の野」。設計 [46](design/46_moon.md) の 4.5・4.5.1）

- 区画 3 を一つ通しで作った: 地形（`build_moon_field.py` → `field.json`、`story/ch6/field/MoonField`。月の世界の版 6）、光の満ち引き（`FieldTide`・`TideStoneBlock`）、星術「一瞬止め」（`MomentStop`・月の砂時計）、降る光（`FallingLight`）、敵 3 種と主「望の灯守」、前の自分（三人目「砂時計の数え手」）、頼み `main_moon_field`、会話、文、地図、絵。
- 模型は一品一案（3 案を並べない）で作り、ラフの絵を送ったうえで入れた。直しの声が来たら `tools/rough/lowpoly/moon/<id>.py` を直して `gen_moon_mobs.py <id>`。
- 模型の手直し（「模型を直す」）: 灯守に頭巾・一つ目・三枚の肩当て・鐘の袖、影の竹馬の胴と脚を太く、鏡背負いの脚を亀の脚に。ゲームの絵 `docs/review/moon/field_models_2026-10-30.png`、撮影 `tools/shots/moon_field_models.txt`・`moon_strider_fold.txt`。
- 止めた構えを保つ: `GuardMob.holdsAction`（終わった姿で止める動作）と `hurtAnimBlocked`（被弾の動きで崩さない）。竹馬の像・鏡背負いの殻こもり・光の群れの散り・灯守の光溜めに使う。それまで `thenPlay` は json の hold を無視して戻っていた（ほかの章の hold の動きは今までどおり戻る）。
- 「ここまで」の札は野を終えた人に出る形に移した（崖のテストも合わせた）。
- 確かめ: 自己テスト `moon field`（ほか `moon world`・`moon valley`・`moon cliff`）。撮影 `tools/shots/moon_field.txt`。

## 2026-10-30 午後（六章 区画 2「上弦の崖」。設計 [46](design/46_moon.md) の 4.4.2）

- 区画 2 を一つ通しで作った: 地形（`tools/gen/world/build_moon_cliff.py` → `data/skyabyss/moon/cliff.json`、`story/ch6/cliff/MoonCliff`。月の世界の版 4）、仕掛け（崩れる棚・天秤の皿と錘・跳び石の塔。欠片の台・星の門・溜まりは谷のブロックを置き場所で `CliffPuzzles` へ回す）、星術「軽身」（空中でもう一段跳ぶ。`LightBody`・`client/LightBodyClient`）、敵 3 種（月跳ね・押し石・崖這い）と主「月秤」（`story/ch6/mob`。模型はラフの選んだ案を `gen_moon_mobs.py` が入れ、動きを書いた）、前のループの自分（二人目「面頬の登り手」。`cast_moon.py` の案 C）、頼み `main_moon_cliff`、会話、文、地図、ブロックの絵（`gen_cliff_assets.py`）。
- 「ここまで」の札（`MoonEnd`）は崖を終えた人に出る形に移した（谷のテストも合わせた）。
- 気づいた決まり: 生き物の体力の上限は 1024（月秤は 850、区画 1 の影抱きの 1100 も 1024 で止まっている）。月の跳びは大きい（一段 4.7・軽身 9.4、平らなら 12〜19 先まで）ので、主の皿へは地形だけで塞がず、門を通らずに皿に立った人を押し戻す（`CliffTicker.guardPlate`）。観戦モードの人は天秤の重さに数えない。
- 確かめ: 自己テスト `moon cliff`（道が跳ばずに登れる・跳び石と天秤の高さが軽身でだけ届く・崩れる棚・皿の守り・頼みと札・敵の数字・主）、`moon world`・`moon valley`・`npc`・`lang`。撮影 `tools/shots/moon_cliff.txt`（見比べ `docs/review/moon/cliff_2026-10-30.jpg`）。戦いの数字と難しさ、軽身の手ざわりは試遊で。

## 2026-10-30 昼（BGM 17 曲。設計 [49](design/49_bgm.md)）

- マインクラフトの曲を止め、場所と戦いの曲を流す（`client/music/MusicDirector`・`LoopMusic`、`music/MusicTracks`・`MusicArea`）。地上の場所は `ContinentAreas` の読みを `MusicArea` でサーバーから知らせる。ボスの帯 16 本に `MusicArea.bossBar`（ボスの曲の印。名のある野良の敵と護衛には付けない）、心臓かどうかは帯の名前の鍵（差し込み `BossHealthOverlayAccessor`）。見る者・空の通過・雲の目の間は無音（`hushesMusic`）。
- 曲は `tools/gen/music`（音源 GeneralUser GS を自前の `sf2.py` で鳴らし、`arrange.py` で組み立て、`check.py` で検める）。17 曲・約 26 MB。どれも 1 周で頭へつながるループ。
- 音源 `tools/sources/soundfont/GeneralUser-GS.sf2` は git に入れない（README に入手先）。曲を作り直すときだけ要る。
- 確かめ: 自己テスト `music`、撮影の台本 `tools/shots/music_check.txt`（場所・ボス・心臓・深淵・月・巨神の背・島で狙った曲が流れ、1 周を超えて鳴り続けた）。曲の聞き心地は耳で確かめていない（試遊の声で直す）。
- 撮影の台本に `music`（記録・決め打ち・全体の音量）を足した。撮影は全体の音量 0 で、本体は音を鳴らさない。

## 2026-10-30 朝（残りの片付け: 戦士の筋力、監査の「低」）

- 戦士の木に筋力の節 8 つ（`str_g`〜`str_n`。幹 2・盾 2・断ち 2・尾 2。筋力 24 → 56、木は 81 節 114 点）。どの型を進んでも深淵の武器の要求値 24（`GearRequirement`）に届く。48 の 2.1・2.2 の数字を直した。ほかの 5 職は生成器が主の基礎を 44〜72 置いているのでそのまま。
- 「Unprimed heightmap」の ERROR: 建築の監査 `SiteAudit.dry` が読み込み済みのチャンクに地形生成用（`_WG`）の高さ図を求めていた。ふつうの高さ図に。
- `Config.loopLengthDays` の説明を今の役（`sky/ChapterSky` の仮の月の周期）に。鍵の名前はそのまま。
- ログアウトで消えなかった UUID の表 11 個（縄の人の引き・旅嚢の最後の道具・淵のほのめかし 4 つ・石板の最後の位置と時刻・試練の足跡・試練の最後の使用・武器の連撃）を `util/PlayerMaps.track` で包んだ（ログアウトで行が消える）。ほかの `static Map<UUID>` は各係が消している。
- `catch (Exception)` 74 か所は見た: 自己テスト以外は全部が記録するか投げ直す（`VoiceTiming` だけ黙って「時刻なし」にする。意図どおり）。直す所なし。
- 不安定だった `abyss escort`・`ch4 ground`（sites と later も）を木・技・職・成長・施設の自己テストと一緒に新しい大陸の世界で流して、13 本すべて通った。「Unprimed heightmap」は 0 件。

## 2026-10-30 未明（48 の残り: 淵・前世の木、紋の寄せ、撮影）

- 淵・前世を職をまたぐ木に（`assets/skyabyss/skill_tree/deep.json`・`past.json`。`AbilityTrees` は SkillTree で引く。`SkillData.nodesOf(SkillTree)` など SkillTree 版を足し、Job 版はその薄い包み。節の `rebirth_at`、`TakeResult.REBIRTH`。前世の点は `pastSpent` が節の点、淵は `sharedSpent` 経由で職の点と分け合う。使う技 4 本は今の職 + 淵 + 前世で数える）。`SkillNodePayload` に木の id。
- 紋: 隣は木の線でつながる技の節（`Glyph.neighbors`）、差している紋は木の節の左下に絵、節の説明に行（`Glyph.centerGlyph`）。
- 技の見た目の台本 `tools/gen/shots/gen_skill_fx_shots.py`: 消した技を外し、新技 7 本を足し、技ごとに `job choose` と始めの武器を持たせる（持ち替えでは職が替わらないため）。撮影 → `tools/site/encode_skill_fx_clips.py` → `build.py`。
- 数値の目安を 48 の 2.1 に（使う技 4 本 = 9〜14 点、型の要 = Lv 23〜27）。
- 不具合: 弓を持つたびに前の版の「近接/弓で枠の組を入れ替える」（31 の A12。`SkillManager.syncBar`・`SkillData.bars`）が古い組を枠へ戻し、職の帯を上書きしていた（撮影で弓使いの技が EMPTY になって発覚）。仕組みごと消した（枠は職の帯 `jobBars` と木 v2 だけ。NBT の `bar*` は読まなくなる）。
- 撮影の的: バニラの敵は世界に入らない（44）ので、技の見た目の台本の的を野盗（`skyabyss:bandit`。踏み込みは岩猪）にした。`/skyabyss skill cast` と `skill learn` の出力に職・手の武器・枠が出る。

## 2026-10-29 深夜 その 2（48 の 7 章の残り）

- 内側の木のセット・タグの数の足し・条件の特性を消した（`SkillBuild` は型の要・木の節・外の修飾だけ。タグの数は淵の鏡・誓いと紋の条件にだけ残る）。`SkillRules` の SET_AT・TAG_*・COND_MIN/BIG/FULL_MANA と lang の skillset/tag.step/tag.cond は消した。
- 変異は木の節 `mutation`（各職の木に 1〜2。極めた技の枝の奥。巨神の核 1・欠片 3 を `SkillManager.takeNode` が払う。一度払えば `SkillData.mutations` に残り、取り直しはただ）。K の 3 ページ目は「紋の盤」だけ。`SkillExtOps.mutate` と通信 MUTATE は消した。
- 装備の基礎の要求値 `rpg/skill/GearRequirement`（レア以上の職の武器。一章 10・二章 14・三章 18・四章 22・深淵 24。届かないと近接半分・木の印が効かない。説明の 3 行目に出る。03 の 6.5）。
- 新技 7 本の専用 FX（`fx/FxTree`）。絵は icons2 の 37〜43 のまま。
- 木の生成器に `balance()`（型の門の手前で取れる点が門 +4 に届くよう節の点を上げる）。木 1 本 75〜105 点。戦士の閾値を 24/24/20 に。
- サイトの技のページは木 v2 の盤（`sitelib.tree_v2_svg`。JSON と lang から。`gen_art.py` が icons2 の 0〜4・45〜47 も切り出す）。
- 自己テスト: `tree`・`skills`（木を外した前の覚え方で技の仕組み）・`jobs`・`skill lesson`・`lang`。

## 2026-10-29 深夜（技の木 v2 を実装。ブランチ `wip/skill-tree-v2`）

- 設計 [48](design/48_skill_tree_v2.md) の 7 章 1〜5 を一気に作った。要点:
  - 木のデータ: `assets/skyabyss/skill_tree/<職>.json`（戦士は手書き、ほかは `tools/gen/skill_tree/gen_trees.py` が JSON と lang の節の文を書く）。読み込み・決まりは `rpg/skill/tree/`（`AbilityNode`・`AbilityTree`・`AbilityTrees`・`NodeEffects`・自己テスト `AbilityTreeSelfTest`）。壊れた木は起動で止まる。
  - 記録: `SkillData.nodes`（職 → 取った節）。技の段・刻印・鍵石は `applyNodes` が節から導く。点は 1 レベル 1 点、職ごと。`canTake` が決まりを検める（前提・排他・型の点・使う技 4 本・点）。基礎は `SkillManager.refreshStats` が木から `GrowthData.stats` へ写す。J の振り分けと力の振り直しは消した。
  - 画面: K は今の職の木を格子で出す（`SkillScreen` の v2 の部分。`SkillNodePayload`）。節の絵は icons2 の 0〜4・45〜47（基礎・刻印・常に・閾値・型の要）。J は読むだけ。
  - 職の間: 師範の会話「職を替える」→ `job_room` → `JobChoice.openRoom` → `JobChoiceScreen(true)`。`JobEvents`（持ち替えの切り替え）は消した。職の武器でなければ技が使えず近接半分、HUD の枠が薄い。
  - 新しい使う技 7 本（`TreeActions`）: 盾殴り・鯨骨の振り・回し斬り（戦士）、二連牙（暗殺者）、火花散らし（鍛冶師）、足止めの矢（弓使い）、星屑（魔法使い）。絵は icons2 の 37〜43（`gen_deep_skill_assets.py`）。FX は似た技のものを借りている。
- 確かめ方: `SKYABYSS_SELFTEST_ONLY="tree,jobs,skills,growth,skill lesson,~facility,~deep"`。撮影 `./gradlew runShots -Pscript=tools/shots/skill_tree_v2.txt`。開発コマンド `/skyabyss skill node @p <節>`、`/skyabyss job open|choose <職>`。
- 残り: 48 の 7 章（数値の試遊、専用の絵と FX、装備の要求値、変異を節へ、内側のセット・タグの削除、サイトの技のページを木の形に）。

## 2026-10-29（技の木 v2 の方向）

- ユーザーの決定: 技の木を Wynncraft 並みに（職ごとに型 3 つ・節 50〜70）、使う技は木に 8〜10 本あるが取れるのは 4 本まで、基礎（ステータス）は別の画面をなくして木の節で上げる、職の切り替えは持ち替えでなく町の「職の間」で。設計書 [48](design/48_skill_tree_v2.md) に下書き（戦士の木の節の表つき）。未決は 00 の 292〜294。次は 48 の 7 章の順（木のデータを JSON に → 点と節の決まり → 戦士の木と格子の画面 → 職の間 → 残りの職）。

## 2026-10-28 深夜（全体の見直しの「高」の直し）

全体の監査（品質の統一・不要データ・面白さと継続性・不具合）の「高」の 5 つを直した。残りの「中」「低」は監査の一覧（会話の中。要点は下）に残っている。

- 公式サイト（`tools/site` → `site/`）を 0.8.1 と 6 職の仕様に書き直した: 技のページは職ごとの木（`skilldata.py` が `Job`/`Skill.java` から読む。星読みの木と職の新しい技は `icons2.png` の 125〜136）、トップ・遊び方・FAQ・更新履歴（0.6.0〜0.8.1）、旅嚢の「作る」の文を消した。天職・旅人・構え・連鎖・刻み目の説明は消した。`python tools/site/build.py` で作り直す（`gen_art.py` は icons2 と祠の武器も写す）。
- `README.md`・`docs/beta_how_to_play.md`・`14` を 0.8.1 と完全な RPG の形に。`01` の軸・やらないこと・ループ・RPG の柱（職の表）・ロードマップを今の方針に書き直した。索引の 27 の行と、消した書類（17・33）への参照を直した。
- 六章の行き止まり: 「新月の谷」のメインを終えた人に「六章　月　完」の札と「この版はここまで」の一言（`story/ch6/MoonEnd`。`moon valley` の自己テストが見張る）。区画 2 のメインができたら置き換える。
- 名前の重複: 二章の職の弓 `tide_bow` を「潮殻の弓 / Tideshell Bow」に（ヒロイックの `tide_coral_bow` が「珊瑚の弓」）。未登録の `warden_hook_spear` の文を消した。

監査の「中」も直した（2026-10-29 深夜）:
- 旧スキル制度のコードを消した（43 の 3 章どおり）: 天職（`SkillCalling`・`CallingScreen`・通信 2 本）、構え（`SkillStance`・キー `.`）、連鎖（枠の印）、刻み目（`Breakpoints`）、旅人の木（合図・備えの報い・野営の手・目利き・逃げ足・手帖）と橋の木（5 本）。技は 65 → 54 本、アイコンの番号は `Skill.iconIndex`（淵・前世は icons2 の 5 番から）。技の画面の「ビルド」のページは鍵石だけ。自己テスト `skills`・`jobs`・`soul`・`lang`・`skill lesson` を通した。
- 絵: 穴の欠片 5 枚を土地ごとに別の形に（確認の絵は 10-31 に消した。2026-10-30 にユーザーが確認して決まり）。技の印の同じ絵 4 組を描き分け、潮殻の `shell_slam`・`shell_sweep` の印を足した（`gen_attack_icons.py`）。`gui.skyabyss.attack.pearl_keeper.*` の古い文を消した。
- 係の人の見た目は A だけ（B・C の 54 枚と 2 体の骨格を消した。`AttendantLook.LOOKS = {"a"}`）。孤児の低ポリの絵 23 枚（`*_flared`・`waterway_maw`・`pearl_keeper`・古い `guest_*`・小道具の `_glow`）を消した。
- 片付け: `run/shots`・`run/mp_check`・`run/world_all4`・dump・`config/*.bak`・`build/portraits_v2`・`build/tmp_attendant`・0.8.0 の jar。`docs/review` の古い報告（assets/gear/mod_blocks/weapon_design_base と試遊表 0.7.1〜0.8.0）、決まったラフの案（legends・ch5・voice2・survivor・warden_paint・材料。比較の絵だけ残す）、`tools/once`・`tools/misc` の 4 本・`tools/rough` の v2〜v4 と gallery_v*。消した書類を指していたコメントと README は直した。
- 残したもの（判断）: `textures/entity/` と `lowpoly/` の同じ絵 35 組は、立方体の geo（`LowPoly.enabled = false` の控え）が残っているのでそのまま。`elder_sentry`（一章の洞窟）と `elder_sentinel`（背の試練）は役が別なので二体のまま（突進・踏みの印は同じ動きなので同じ絵）。メッシュ監査（`tools/audit/mesh_audit.py`）: 開いた辺 0、非多様体の辺 2,028、つぶれた三角 104、関節の離れ 37（主に防具の脚）。直すなら `tools/lib/mesh_close.py` から。

「低」も 2026-10-30 に片付けた（上の「残りの片付け」）。残るのはメッシュ監査の非多様体の辺・つぶれた三角・関節の離れ（直すなら `tools/lib/mesh_close.py` から）。

## 2026-10-28 夜（0.8.0 の試遊の声 その 2。`memory: skyabyss-080b-feedback`）

できたこと:
- 心臓の報酬: 初めて止めたときの選ぶ画面は出さない（`HeartReward.CHOICE_ON_FIRST_CLEAR = false`。30 に記した）。専用の逸品は心臓の周回でまれに落ちるだけ。
- 武器を持ち替えてもスキルの帯が消えない（武器でない物を持つ間は前の職の帯を薄く出す）。章の表示は進めているメインの頼みの章へ（地上で落ち着いたら札を待たない）。緑の経験値バーは出さない。
- 主と名のある敵は下がらない（`combat/BossFooting`）。
- 戦いの手ごたえ（47）: 主の構えと隙（`combat/BossOpenings`: ふだん ×0.3、技が当たり終わって 3 秒 ×1.6、領域を解くと 6 秒）、ふつうの敵の身構え（3 秒に 5 回打たれると 2 秒 ×0.4）、戦いの中の食事は 20 秒に一つ（`combat/FightRations`）。
- コマンド: 製品版では `/skyabyss` の下は `play` と `inspect` だけ見せる（`dev/CommandGate`。中から流す呼び出しは通る）。`play raid tide …` で潮殻の試練へ行けなかったのは、潮殻を「自分から 120 先の海」に呼ぶ作りで大陸の真ん中に海がなかったため。
- 巨神の居場所（40 の 3.6、`world/continent/ContinentLairs`、`data/skyabyss/continent/lairs.json`）: 四体は決まった所で休み続ける。体の下の地面は地形の生成で体より低くしてある。休む巨神はブロックを壊さない。自己テストの間は居場所を使わない（古い動きの段のため。`titan lairs` だけが使う）。
- 祠の場所: 土地の町から跡地への道の、町を出て 90 ほどの道ばた（`lairs.json` の shrines）。地形の生成で台座の広場をならす。
- 全滅: 心臓の戦いで参加者が全員倒れたら、物語の一度目は地上の巨神の前、周回は祠の前で目覚める（`titan/group/WipeReturn`）。
- 月の旅の地図（46 の 4.1）: 月にいる間は月の絵（`tools/gen/ch6/gen_moon_map.py` → `textures/gui/map/moon.png`）。
- テストの直し: 島の湧きの表は空のまま・freequest は四章の日替わりを数えない・都の掃き出しは外へ押し出せばよい・樽などは道にかかっても置く。

残り（この順）:
1. （済み）ヒロイック武器 17 本: 選んだ案を入れた（`item/HeroicGear`、心臓で一本ずつ 2% の別枠、`Rune` の書き換え 17、`install_job_weapons.py` の HEROIC、手持ちは `WeaponKind`・`WeaponClient`。撮影 `tools/shots/heroic_weapons_review.txt`）。
2. （済み）心臓の間の大広間（`trial/raid/HeartHall`、32 の 10 章、撮影 `tools/shots/heart_rooms.txt`）。
3. （済み）試練をそろえる共通の決まり（`trial/raid/RaidPace`、32 の 9.6）。試遊で部屋ごとの声を聞いて、部屋の数値を詰める。
4. （済み）祠を撮って確かめた（`tools/shots/shrines.txt`。四つとも町を出た道ばたの平らな広場に建つ）。
5. （済み）0.8.1 の試遊用の jar（`build/libs/skyabyss-0.8.1.jar`、確かめ表 `docs/review/playtest_0.8.1.md`）。全体の自己テストは 189 通過、残りは直して単独で通した（`ch4 ground (sites)` は全部を続けて流すとときどき落ちる。単独では通る）。

## 2026-10-27 にやったこと（0.7.0 の試遊の声）

- 伝説 20 本の技の書き換え（43 の終わり）。
- クリエイティブの欄を五つに分けた（武器・防具と飾り・敵の卵・ブロック・素材と品。`registry/CreativeTabs`）。装備の名前は階級の色（`mixin/ItemNameMixin`）。
- 階級の床（`item/TierFloor`）: ヒロイックは同じ由来のエピックより強く、アーティファクトは 1 秒 19.5 以上。自己テスト `item audit` が見張る（03 の 1.4.1）。
- 足りなかった職の章の武器 4 本（鯨歯の短剣・馬刀貝の短剣・雷翼の剣・稲妻の短剣）。どの章にも 6 職の武器がそろった（03 の 1.4.1、43 の 8 節）。
- 一人称で武器を見せる（構えていないときだけ型ごとに手元をずらす。43 の 8 節）。
- モデルの作り込みをそろえた: 古い作りの 12 本を一品一案で描き直した（15 の 4.6.2、`o_remake.py`）。ユーザーが全部選んだ（鯨骨の大剣の離れた部品・羽守の鉤槍の形は直した）。
- 道: 町と都の門へつなぐ・地均しで道を消さない・道の雪と木を取る・見どころと飾りを道から離す（40 の 7 章）。
- はじまりの島 v2: 広い谷と低い丘、寄り道 5 か所（見張り塔の跡・洞窟・漁師小屋・旅人の野営・大樹）と脇道・札、島の敵を 3 種足した（40 の 7 章）。地形は `gen_continent.py` で作り直した（生成は今のデータと一致していたので安全に流せる）。
- 町の作り直し（45）:
  - 並べ方は A 街道と市場に決まった。四つの土地の町を A で建て直し、町ごとに鏡写しにした（`python tools/capital/town_plans.py towns`）。
  - 家どうしは軒から 1〜2 マスあける（`check_gaps` が 0）。奥の家は 6 軒まで。
  - 作り手の手本 8 枚（`docs/review/build_refs`）を建築の水準にした（45 の 4 章）。
  - 仕上げ `dress.py` を足した: 風化の斑・鎧戸・花の箱・戸の灯り・蔦・煙突の煙・小路の鎖・地面のすり減りと岩・露台。
  - 上の町（3 段高い石垣の段と階段。`terrace.py`）、町ごとの目印（`landmarks.py`）。
  - 土地の町に都と同じ施設を全部置いた（45 の 5 章。町の版 `town_v3`）。大陸の世界で四つの町とも 15 人が立つのを確かめた（`tools/shots/town_facilities_live.txt`）。
  - 都の外の係の人は、都にしかない物の名を出さない（会話の条件 `in_capital`）。町の頼みの選びものと師範の稽古は都だけ。訓練の的も都だけ。
- 島の渡し守と土地の町の 6 人の会話の顔が出なかった（肖像が無かった）。体と同じく都の町の人の肖像を借りる（`DialogueScreen`）。自己テスト `region folk` が見張る。
- 0.7.1 にした（jar・確認表）。
- 2026-10-27 午後の声（`memory: skyabyss-071-feedback`）:
  - バニラの生き物は村人のほか入れない（動物も）。
  - 敵の落とし物は Mod の品と、店の品・矢・パンだけ。
  - 爆発は大きな技だけにした。
  - ダメージの数字を太く大きく長く出す。
  - 島の敵は寄り道の場所のまわりだけ。島の名のある敵はなし。島の目的は順番どおり。
  - 町は敵を押し戻す（消さない）。
  - 落ちる頁は頭の上から。町めぐりに厩舎（乗り笛）。都の組合ははじめから開く。
  - 街道は幅 7。内海は渡し守と話して渡る。お金の上限は 9,999,999。
  - 都と見どころに家の型と仕上げを使った（都の入れる家は 11 軒に）。控え帳の「巨神」に語りの見直し。
- 書類の整理: `docs/review` の決まったラフ・古い頁・日付の報告を消した（886 → 約 395 MB。残りはモデルを入れるスクリプトが読むラフ）。消したものを指していた書類の文を直し、大陸より前の書類（06・17・21・33・34）に「状態」の行、03・14・15・19・40・43・44・README を今の形に。
- 2026-10-27 夕方の声（`memory: skyabyss-071-feedback` の続き）:
  - 市の四人を入れた（食料屋 = 腸詰めの売り子、武具屋 = 体格のいい人で武器は持たない、素材屋 = 鉱石の目利き）。店を道具・食料・武具・素材に分け、薬師は薬だけ。台詞と日誌の「道具屋」を品のある店に直した。
  - 名のある強敵は土地ごとの棲み家（見どころ一つ）に出る。旅の地図に髑髏の印、生きている間は光る縁取りと光の柱（44 の 5）。
  - 巨神が早く休む: 鯨を見た記しの距離 160 → 400（高く巡るので降りてこなかった）、鯨の次の降下まで 30〜60 秒、初めて乗る待ち 10 秒、潮殻も浜で待つ人に寄る。
  - 序盤の「白く霞む丘」の寄り道を消した。三章「空の石」は掘らずに市で手に入れる。
  - 道: 使っていない町の門から近くの見どころへ脇道。地図の道を太く濃く。
  - クエストの報酬からバニラのがらくたを外した（mod の品に）。焼けた砦の見回りは野盗（modの敵）を倒す形に直した（前はもう出ないバニラの敵だった）。羊の日課を消した。
  - 採集は道具ごと（斧・つるはし・シャベル）。採った物は素材屋が買う。バニラの「持ち物の保持」を入れた。

- 試練に入った瞬間に死ぬ不具合を直した: 大陸の世界の縁（一辺 4,400）が背の次元にも効き、x 5,000 あたりの試練の部屋が縁の外だった。縁は狭めない（前の版で狭めた世界は広げ直す）。`rpg rules` の自己テストが部屋の枠を見張る。

## 2026-10-28 にやったこと

- 深淵の心臓の番人を最後のボスらしく（体力 1000・技 7 つ追加・段で解放）、層の主 4 体に技ごとの構えと打ちのアニメ（36）。
- 見どころ 24 か所を建築の水準へ（17 を建て直し、7 に仕上げ。`tools/gen/world/build_pois.py`・`build_pois_dress.py`）。
- 選定どおり: 風見鶏 A（低ポリの持ち物）、小舟 A（低ポリの渡し舟。向きはバニラの舟と同じを撮って確かめた）。生き残りは今の一人ずつの形（v2）のまま（ラフ C は入れない。ユーザーの選び直し）。
- 組合の踏破の記録（`TitanRunData`。書記と `/skyroute runs`。10 の 7.2）。
- 深淵の夜の見直し（2026-10-28 夜）: 場所の名（深淵・余燼の背）とバイオーム名の自己テスト、果てない深みの階の仕上げ（材料の斑・縁の段・蛇腹）、心臓の間の骨の肋、塩・灰の下り道の支え木、骨・雷の部屋の小物、塩・灰の頼みの文。深淵の建て直しの版 15。見直しの撮影 `tools/shots/abyss_review.txt`（印を狙う。作り直しても古くならない）・`abyss_heart*.txt`・`abyss_escort_walk.txt`・`abyss_fight.txt`。
- 全部の自己テストを回して 3 つ直した（都の吊り灯と重ねた階段・誓いの試験・日替わりの試験の引きの依存）。`abyss escort` はほかと一緒だと落ちることがある（単独で通る）。`abyss enemies spawn` は `abyss enemies (load)` と一緒に回す。
- 続き（同じ夜）: 塩・灰の待ち場にも小物（護りの道から 2 離す）、手記と地図の頼みの報酬に伝承の書の項目、番人が倒れたあとの場面 `abyss_warden_fallen`（声の様式 l2 で。`--style l2` を必ず付ける）、docs 36 の古い「まだ」（主・王の見た目は専用の姿がある）を直した。
- 0.7.3 の jar を作った（06:10 に作り直し）。
- 朝の三つ（全部「お願い」）: 心臓の間の入口を戦いのあいだ黒い幕で閉じる（`WardenSeal`）、深みの順位の石を画面に（`client/depths/DepthRankScreen`。組は記録したときの顔ぶれの名前）、番人の絵は A 古い骨に決まり（「もうすこし大きく・前後に骨」で 1.35 倍・背の棘と胸の肋の先）。`gen_heart_warden.py` は書き直したあと七つの技の動きを足し直す（前は消えていた）。
- 町と都の壁の外: まわりの土地の地面へ斑で塗り、小物・都は畑と木立（`tools/gen/world/town_outskirts.py`。45）。台の縁は輪 14 のなめらかなつなぎ。町の版 `town_v4`（建った町は建て直す。都は建て直しの仕組みがないので新しい世界から）。

## 2026-10-28 午後（見比べと六章の入口）

- 見比べ（全部の要素を一番良いものに合わせる）: 一章の素材アイコン 4 つ、初めの旅装・鉄張りの旅装の作り直し、土地の町の 8 人の体と肖像、持ち物のクラフト枠を消す、仲間と称号の画面、名前の抜け（矢・小舟・真珠）と名前の自己テスト、章の小要素 39 か所の作り直し（`tools/gen/world/build_chsites.py`）。
- 「踏破の時間」の目的 `run` と週替わり「速い踏破」。
- 五章から六章への引き「近づく月」（`story/ch5/MoonNear`。自己テスト `moon near`）。
- 六章「月」の設計 `docs/design/46_moon.md`。方向は決まった（月の面・鐘楼の門・月の上だけで巡る位相・四つの区画・前の自分・星術・時を戻す核・位相の挑戦）。次は 46 の 5 章の順で作る。

## 2026-10-28 夕方（六章 区画 1「新月の谷」の一つ目の通し）

- ユーザーの声「後から作り直しみたいなことをできるだけなくしたいので、全力で」。仕様を先に決め (46 の 4.2・4.3)、ラフ 3 案ずつ → 選定 → 実装の順にした。
- 選定 (46 の 4.3.1): 配置 C 輪の谷、砂這い A・殻背の兎 B・影の綴じ手 C・影抱き B 影の母・前の自分 C、星灯り = 星の灯の道具、仕掛けのブロックの様式 A 黒曜の星図、品の絵は全部 A。
- できたもの (46 の 4.3.2): 谷の地形 (`tools/gen/world/build_moon_valley.py` → `data/skyabyss/moon/valley.json`、月の世界の版 2 で流し込む)、仕掛け (`story/ch6/valley/`)、星の灯、敵と主 (`story/ch6/mob/`、素材 `tools/gen/ch6/gen_moon_mobs.py`)、前の自分 (npc2 の体 `cast_moon.py`・肖像)、頼み `main_moon_valley`、自己テスト `moon valley` (頼みの通しも)、撮影 `tools/shots/moon_valley*.txt`。
- 新しい世界でなくても、月の世界の版が 1 なら次に入ったときに谷が流し込まれる。
- まだ: 試遊 (数字)、谷の作り込みの二回目、月の材料の使い道 (月の装備と一緒に)、区画 2〜4。

## 気をつけること

- 日本語を含む Python はヒアドキュメントで渡さず、ファイルに書いて `PYTHONIOENCODING=utf-8 python -X utf8` で流す。
- `runShots` と `runServer` を同時に動かさない。コンパイル中も同じ。
- 観戦モードの人には見どころが建たない（撮影はクリエイティブで）。月の撮影は観戦モードで (軽い重さで宙から落ちていく)。前の自分は観戦モードの人には立たない。
- アニメの値 (tools/gen の kf) は x が負 = 前へ傾く (垂れた腕なら前へ上がる)。bone([...]) に直接書く文字の式は逆。
- Python 3.9 は長い行に日本語があると「Non-UTF-8 code」で落ちることがある。日本語の .py の頭に `# -*- coding: utf-8 -*-`。

## 次にやること

0. **見直しの片付け（2026-10-27）は済み**: 作る仕組みの残骸・野営の置ける物・七日目の夜・大陸でない世界の仕組み（世界の種類は大陸だけ。場所は図だけ。大陸より前の docs 06・17・21・33・34 は消した）・`docs/review` の見比べの絵（404 → 152 MB）。残りの気づき:
   - 消した docs を指すコメント、docs/07 の古い書き写しは直した。データと食い違った生成スクリプト 47 本（と gen_relic_blocks）は消した。データを直接直す。
   - `docs/review` の選ばれなかった案は、モデルを入れるスクリプトが同じ品の別の案を読むものだけ消した（728 ファイル）。読む案が分からない品（引数で案を選ぶスクリプトなど）は残した。
1. 技の木 v2（48）: 試遊で数値を詰める（節の値・点の総量・閾値・要求値）。
2. 試遊 0.8.0（`docs/review/playtest_0.8.0.md`）の声を待つ。BGM（49）の聞き心地も試遊の声で直す（`build_all.py <id>`）。
3a. 六章の続き（46 の 5 章）: 六章の本筋はできた（月の核まで。4.7.1）。残りはクリア後の位相の挑戦（3.8）、月の装備（03 の 12.2）、記憶の断片と伝承、終章「雲海」。
3. クエストの見直しは済んだ（初日のお使いと風の道の旗を消し、墓守を 2 段に、大干潮と峰の足もとを時刻で閉じない形に、折れた剣は自分の得物を研ぐ形に）。
4. 都・見どころの仕上げの撮影は 2026-10-30 に撮って見た（絵は 10-31 に消した）。崩れ・抜けなし。見どころは作りが小さめ（崩れた家・見張り台）なので、作り込むなら 45 の仕上げの段で。試遊 0.7.1 の声の直しの確かめは試遊の声待ち。
5. 大きく変えたら jar を作り直す。
