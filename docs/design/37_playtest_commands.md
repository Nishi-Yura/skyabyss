# 37 試遊コマンドの棚卸しと「/skyabyss play」

- 範囲: `/skyabyss ...` 以下に登録されている開発・試遊のコマンドの一覧（棚卸し）と、試遊のための新しい木 `/skyabyss play ...` の仕様と使い方。
- 状態: **棚卸しは静的（ソースの登録とその参照先を読んだもの）。実行して確かめたのは `play` の自己テストだけ。** 古いコマンドの実行確認は未了。
- 関係: 試遊のコマンドは `/skyabyss play ...` の一本に集めた。旧 `/skyabyss playtest ...` は当面そのまま動く（働きの同じ語は `play` に統合済み。1 節の表の「試遊（旧）」）。`play` は `Playtest` の補助と公開の API（`Quests`・`Progression`・`Growth`・`Lore`・`TrialRoute`）だけを使う薄い層。
- 実装: `jp.revati.skyabyss.dev.play`（`PlayCommands`・`PlayChapters`・`PlayPlaces`・`PlayQuests`・`PlayRaid`・`PlaySelfTest`・`PlayMultiSelfTest`）。文言は `assets/skyabyss_play/lang`。

## 1. 棚卸し

数え方: `Commands.literal("...")` の ASCII の語を grep で数えた。約 400 行（自己テスト用の文字や表示用の語を含むので概数）。`RegisterCommandsEvent` を使う登録ファイルは 79 本。

区分:
- **試遊**: 試遊や撮影で使う近道。プレイヤーにも見せてよい語（権限 2 のものが多い）
- **開発**: `dev` 以下や、作り手が部品を確かめるための語（`!FMLEnvironment.production` の条件つきも含む）
- **プレイヤー**: 通常のプレイで使う語（ギルド・パーティ・技など）
- **要確認**: 登録の根が `skyabyss` 以外、またはクライアント側の名前で、試遊の木へ入れるか決めていない

| 群 | 根（`skyabyss` の下） | 主な語 | 権限 | 区分 | 備考 |
| --- | --- | --- | --- | --- | --- |
| 試遊（旧） | `playtest`・`試遊` | help skip opening whale board goto trials gear chapter heal day fast reset god now next | 2 | 試遊 | `play` と重なる（後述 3） |
| 試遊（新） | `play` | help status chapter goto kit boss quest lore time weather peaceful heal feed god reset | 2 | 試遊 | 本書 4 節 |
| 序章 | `opening`・`dev opening` | go back reset status・info build view at goto dump | 2・開発 | 試遊 | `SiteBuilds` の `dev opening` と根が別 |
| 章の印（ch1・ch2・ch4） | `dev ch1`・`dev ch2`・`dev ch4` | place chamber・place vault tide door mark wreck・warm crack wave curl | 開発 | 開発 | 座標を取る。撮影用 |
| 章の地面 | `dev grounds` | go info sites prepare shrine | 開発 | 開発 | `dev ch2 vault` と場所の役が近い（後述 3） |
| 章の空 | `scar`・`check`・`here`・`list`・`eye`・`show`・`next`・`reset`・`moon`・`set`・`clear`・`chapter1` | （空の見え方・月・目の数の確認） | 2 | 試遊 | 章の空の確認用 |
| 主線 | `quest` | list start stage next forget signal talk say（`dev` の下にも） | 2 | 試遊 | `play quest` で段階を決める |
| 進み | `progress` | show grant revoke world | 2 | 試遊 | 巨神の心臓を止めた記録 |
| 物語 | `story` | ready key scene reset | 2 | 試遊 | 根 `story` を `ProgressCommands` も使う（要確認） |
| 幕 | `dev arc` | status reset card aftermath | 開発 | 開発 | |
| 伝承 | `lore` | list unlock lock reset book page drop | 2 | 試遊 | `play lore` で全部開く |
| 成長 | `growth` | add level show set addxp resetstats reset fragment give clear | 2 | 試遊 | 体力・攻撃の目安 |
| 技 | `skill` | show learn forget points mana full cooldowns slot cast calling rune stance keystone mastery chain rebirth glyph socket mutate respecs | 一部 2 | プレイヤー・試遊 | 語の数が多い |
| 転生 | `rebirth`・`soul` | title titles board show hide dev rebirth rebirth_do・show set do shards spare open | 2 | 試遊 | |
| 詞（付与） | `affix` | show reroll clear set trial exchange reset | 2 | 試遊 | |
| 記録 | `record` | inspect | 2 | 試遊 | |
| 巨神 | `titan` | summon rest board rebuild reset leave | 2 | 試遊 | `play` の基になる |
| 巨神の旅 | `travel` | unlock lock met young go cooldown status end | 2 | 試遊 | |
| 試練（背） | `dev trial`・`skyroute` | build list cycle force mods tp・status start enter clear pass tidyhud replayable useshrine shrine shard | 開発・2 | 開発・試遊 | `route shrine` と `dev grounds shrine` の名が重なる（後述 3） |
| 誓い | `vow` | add remove clear | 2 | 試遊 | |
| 岩鯨の背 | `dev backmetrics`・`dev buildaudit`・`dev routelight`・`dev titanground`・`dev force` | （計測・監査・灯り・地面・強制） | 開発 | 開発 | |
| 潮殻・雷翼・余燼の心臓 | `tideheart`・`stormheart`・`emberheart`（start phase reset chamber など）・`dev tideforce`・`dev emberforce` | start phase reset rebuild | 2・開発 | 試遊 | 「`/skyabyss tideheart start`」の形（`play boss` が使う） |
| 深淵 | `abyss`・`dev abyss` | enter build leave mob art learn up open・on off status | 2・開発 | 試遊 | 層の建て直しは `build` |
| 深淵の番人 | `abyss warden` | wake reset dev(lamp dark) | 2・開発 | 試遊 | |
| 深淵の深さ | `abyss depth` | go kind records | 2 | 試遊 | |
| 悪夢 | `skyabyss_nightmare` | enter | 2 | 要確認 | 根が `skyabyss` の外（統一が要る） |
| 村・人 | `npc` | spawn village affinity bonds reset loop set advance | 2 | 試遊 | |
| 町 | `town` | list rumor start place bell well reset cellar open close mapmaker draw | 2 | 試遊 | |
| 施設・ギルド・交易 | `facility`・`guild`・`trade` | spawn mark place shop stash coins look records dummy keyroom offer return mapcopy respec brew trade demo・create apply join accept deny leave kick open info list menu officer take storage deliver banner leader disband confirm pattern plate achievements | 一部 2 | プレイヤー | `coins` が `FacilityCommands` に二度ある（後述 3） |
| パーティ | `party` | invite accept decline leave kick leader | なし | プレイヤー | |
| 拾得・報酬 | `titanmemory`・`heartreward` | claim choose memory set clear・reward reset dev | 2 | 試遊 | |
| 旅嚢 | `pouch` | （品の袋の確認） | 2 | 試遊 | |
| 準備・通知 | `readiness`・`notice`・`dev ux` | prompt summary whisper・place・stele grave party depart clear | 開発 | 開発 | |
| 序盤の見張り | `watcher` | show next reset | 2 | 試遊 | |
| 演出 | `cinematic` | play stop reset | 2 | 試遊 | |
| 試し・計測 | `dev routelight`・`dev buildaudit` 他 | | 開発 | 開発 | |
| 雲の目 | `eyevariant` | | 開発 | 要確認 | クライアント側の名前が server の一覧に出る |
| HUD の見本 | `skyabyss_hud` | demo layout | 開発 | 要確認 | 根が `skyabyss` の外 |
| 反動の見本 | `rebirthfx` | | 開発 | 要確認 | 根が `skyabyss` の外 |
| 自己テスト用 | `x`・`sub`・`X`・`T`・`U`・`test`・`renamed`・`sync_p` など | | — | 要確認 | 自己テストが木を組むための語。実際の木に出ていないか確かめる |

### 集計

- 試遊・プレイヤー向けの群: 約 30（うち新しい `play` は 1 群）
- 開発専用の群: 約 20
- 要確認: 5（悪夢・雲の目・HUD の見本・反動の見本・自己テスト用の語）
- **重複（同じ語・同じ働きの二重）**:
  1. `coins`: `FacilityCommands` に二度登録されている（17 行目と 26 行目）。後のものが効くか、同じ経路か、要確認。
  2. `story` の根: `StoryCommands`（ready key scene reset）と `ProgressCommands` が同じ根を使う。`reset` などの語が重なるか要確認。
  3. 試遊の二つの木: `playtest`（旧）と `play`（新）。skip/chapter/heal/god/reset/goto は働きが重なる。両方残す（旧は既存の資料が指している）。
  4. 章の地面の名: `dev grounds shrine` と `skyroute shrine`（名が同じで別の物）。
  5. 章の印: `dev ch2 vault` と `dev grounds go` が、二章の地上の置き場所として近い。
- **壊れている（確認できたもの）**: 静的には見つからなかった。ただし実行していないので「壊れていない」の証明ではない。`play` の自己テストで、ボスの開始コマンドの形（`tideheart start` など）を確かめる。

## 2. 試遊の木（`/skyabyss play`）

権限 2。試遊のコマンドはこの一本の木（`/skyabyss play`）に集めた。旧い `/skyabyss playtest ...` は当面そのまま動く（同じ働きの語は `play` に統合済み。`/skyabyss play help` の末尾に案内を出す）。文言は `commands.skyabyss_play.*`（ja_jp・en_us）。

### 2.0 対象（複数人）の決まり

- どの人向けのコマンドも `[対象]`（`@a`・`@p`・名前・パーティの人たち）を取り、**省略で自分**。複数人なら全員に同じことをする（結果は件数で返す）。
- `[対象]` の位置: 必須の引数があるものは**そのあと**（`goto <場所> [対象]`・`raid <巨神> <部屋> [対象]`）。省略できる引数があるものは**対象が先**（`chapter <n> [対象] [段階]`・`boss <名> [対象] [tier]`・`quest next [対象] [id]`）。段階や id と選択子の区別をつけるため。
- 世界に効くもの（`time`・`weather`・`peaceful`・`day`・`fast`）は対象を取らない。
- 候補（tab 補完）: 場所名・章の段階・クエスト id（選んだ id の段階も）・ボス名・巨神名・部屋（段・枠・id）・道具・時刻・天気。

```
/skyabyss play help
/skyabyss play status [対象]
/skyabyss play chapter <0-5> [対象] [段階]
/skyabyss play goto <場所> [対象]
/skyabyss play skip opening [対象]
/skyabyss play now [対象]
/skyabyss play kit <iron|whale|tide|storm|ember> [対象]
/skyabyss play boss <whale|tide|storm|ember|raiju|smith|warden|nightmare> [対象] [tier 0-4]
/skyabyss play raid <whale|tide|storm|ember> <部屋> [対象]
/skyabyss play trials <巨神> [対象]
/skyabyss play quest list [対象]
/skyabyss play quest next [対象] [id]
/skyabyss play quest set <id> <段階> [対象]
/skyabyss play quest done <id> [対象]
/skyabyss play quest reset <id> [対象]
/skyabyss play lore unlock-all|reset [対象]
/skyabyss play heal|feed [対象]
/skyabyss play god on|off [対象]
/skyabyss play reset [対象]
/skyabyss play time day|noon|night|midnight
/skyabyss play day <n>
/skyabyss play fast on|off
/skyabyss play weather clear|rain|thunder
/skyabyss play peaceful on|off
```

旧い形で残しているもの: `quest <id> [段階|done|reset]`（自分だけ）・`reset me [対象]`。旧 `playtest` の `board`（= `goto <巨神>`）・`goto <巨神> <地点>`（= `goto <巨神>-<地点>`）・`gear`（= `kit`）・`whale`（= `boss whale`）に当たる語は、`play` 側の新しい語へ寄せた。

### 2.1 章（chapter）

| 章 | 作るもの | 目的の主線 |
| --- | --- | --- |
| 0 | 何も持たない初めて（`reset` と同じ） | なし |
| 1 | 序章を飛ばし、岩鯨の装備（鉄）、岩鯨を呼ぶ | `main_song`（段階 `see_whale`） |
| 2 | 岩鯨を止めた状態、潮殻の装備、潮殻を呼ぶ | `main_tide` |
| 3 | 潮殻まで止めた状態、雷翼の装備 | `main_thunder` |
| 4 | 雷翼まで止めた状態、余燼の装備 | `main_ember` |
| 5 | 四つを止めた状態、余燼まで終えた装備。`main_key` を済ませ、地上へ | `main_abyss`（段階 `rumor`） |

- 章の用意は、いつも `reset` から始める（前の状態を残さない）。
- `[段階]` は、その章の主線の段階 id を決める（例: `chapter 2 see`）。存在しない段階は失敗する。
- 章 5 の段階: `door` `shaft` `gate` `descend` `warden` `heart` `report` のどれでも決められる。

### 2.2 場所（goto）

| 名前 | 行き先 |
| --- | --- |
| `overworld` | 地上（共有の出現点） |
| `whale` `tide` `storm` `ember` | 背の入口（乗る。呼び出しを待つことがある） |
| `<巨神>-<地点>` | 背の中の地点（`hub`・`heart`・休み場の id。`Playtest.spots` の一覧） |
| `abyss` | 深淵へ（`skyabyss abyss enter`） |
| `abyss-<印>` | 深淵の印（`arrival` `rest_1` `rest_2` `rest_3` `heart`）。層が組まれていなければ飛ばす |
| `abyss-depth-<n>` | 深淵の深さ n（`skyabyss abyss depth go n`） |

- 建っていない場所は飛ばし、理由をチャットに出す（翻訳キー `place_missing`）。
- 自己テストは、背の地点と深淵の印が「立てる場所」か（足もとが固く、体の高さに空きがある）を見る。建っていない世界は理由つきで飛ばす。

### 2.3 道具（kit）

| 名前 | 中身（`Playtest.gear` の tier） |
| --- | --- |
| `iron` | 鉄の防具・鉄の剣・弓（tier 0。岩鯨に挑む装備） |
| `whale` | 岩鯨の装備（tier 1。潮殻に挑む） |
| `tide` | 潮殻の装備（tier 2。雷翼に挑む） |
| `storm` | 雷翼の装備（tier 3。余燼に挑む） |
| `ember` | 余燼の装備（tier 4。全部終えた状態） |

どれも、矢・盾・焼いた肉・金の林檎・回復の薬と、その章の体力の目安（備えの灯が緑になる値）を付ける。

### 2.4 ボス（boss）

| 名前 | 動き |
| --- | --- |
| `whale` | 岩鯨の心臓の部屋へ（開始のコマンドはない。部屋に入ると始まる） |
| `tide` `storm` `ember` | 背に乗せ、心臓の部屋へ行き、`skyabyss <巨神>heart start` を流す |
| `raiju` `smith` | 雷翼・余燼の背に乗せ、順路を始めて中ボスの間（`trial/raid/mid`）へ運ぶ。間に着くと戦いが始まる |
| `warden` | 深淵に入り、`skyabyss abyss warden wake` |
| `nightmare` | 深淵に入り、`skyabyss_nightmare enter`。段階は記録で決まる（選べない） |

`[段階 0-4]` を付けると、先に `kit` と同じ装備にする。

### 2.5 クエスト（quest）・伝承・時刻・その他

- `quest list [対象]`: 進行中のクエストを一行ずつ（メインが先。種類・id・段階名・何段中何段目）。
- `quest next [対象] [id]`: **次の段階へ進める**。id を省略すると、追跡中のクエスト、進行中のメイン（定義順）、未着手の最初のメイン（始める）の順に選ぶ。id を渡せばサブ・サイドも同じ。未着手なら始める。進め方は本物の道（`Quests.forceStage` = 段の完了、報酬・シグナル、次の段の計測・アクション、同期）なので、日誌・目的の帯・NPC の話題・パーティの進みは本物と同じに更新される。結果は「前 → 後」の一行で、完了済みのものは進めずに知らせる。
- `quest set <id> <段階> [対象]`: 段階を決める（`Quests.setStage`）。`quest done <id> [対象]`: 最後まで済ませる（段を順に完了。上限 60）。`quest reset <id> [対象]`: 最初に戻す。
- `lore unlock-all`（伝承をすべて開く。`unlockable` のものだけ）・`lore reset`（白紙に戻す）
- `time`・`weather` は vanilla の `time set` と `weather`、`day <n>` は n 日目の朝、`fast` は tick レート 2 倍/通常
- `peaceful on|off` は `difficulty peaceful` / `difficulty normal`（世界の難易度。全員に効く）
- `heal` は体力を全快し、効果と火を消す。`feed` は満腹の値を最大にする（空腹はない）。`god on|off` は無敵
- `reset` は、章・クエスト・伝承・止めた巨神・体力の目安・無敵を全部戻す
- `skip opening` は序章を飛ばす。`now` は今の目的の次の出来事を本物の仕組みで起こす（旧 `playtest now`）
- `trials <巨神>` はその背の試練を全部済ませた状態にする（旧 `playtest trials`）

### 2.5.1 レイドの部屋へ（raid）

`raid <巨神> <部屋> [対象]`: 対象全員を背に乗せ、順路の指定の部屋へ運ぶ。

- 部屋の指定: 段 `1`〜`3`（歩・智・闘の順）、枠 `walk`・`wit`・`fight`（その周の引きの部屋）、部屋の id（例 `WR1`。引きと違えば、その枠の引きを入れ替えて保存する）。
- 流れ: 全員の準備（序章を飛ばす・前の巨神を止めた状態・その巨神の装備・**主線をその巨神の順路の段階 `inscriptions` に合わせる**）、全員が背に乗ったら、グループごとに公開の入口（`TrialRoute.start`、段が 1 でなければ `TrialRoute.carry`）で順路を始めて運ぶ。部屋の建てかたと運びは `RaidRun` 側が持つので、このコマンドは薄い層。
- 巨神がまだ出ていなければ、出るのを待ってから乗せる（`boss` と同じ）。レイドの部屋のない巨神は断る（`raid_none`）。
- 順路の記録（段・参加者・引き）は、本物の入口から始めたときと同じになる。パーティの人は同じグループの記録を共有する。

### 2.6 状態（status）

主線の五つ（`main_song` `main_tide` `main_thunder` `main_ember` `main_key` `main_abyss`）の段階（未着手・完了・段階名）と、四つの巨神の「止めた・まだ」を一行ずつ出す。

### 2.7 五章・六章・終章の開発コマンド（権限 2。製品版では見えない）

| コマンド | 中身 | 書類 |
| --- | --- | --- |
| `/skyabyss abyss …` | 深淵の層・番人・深みへ跳ぶ | [36](36_abyss.md)、`docs/review/playtest_0.8.1.md` |
| `/skyabyss moonworld <valley|cliff|field|rift|core> …`、`core_lord`、`core_clear` | 月の区画へ跳ぶ、核の主、核を倒したことにする | [46](46_moon.md)、`docs/review/playtest_0.9.1.md` |
| `/skyabyss cloudsea enter`、`ring <鐘>`、`lord`、`crumble`、`lamp <w\|e\|s>`、`eye`、`forge <w\|e\|n>`、`rewinder`、`selves`、`plume`、`seat <1\|2\|3>`、`ending <calm\|break\|accept\|none>`、`trial <1..5>` | 終章: 着く所へ跳ぶ、島ごとの仕掛けと主、座の形態、結末の空、巡礼の試しの段 | [50](50_finale.md)、`docs/review/playtest_finale.md` |
| `/skyabyss quest next <頼み>`、`quest signal <合図>` | 頼みを一段進める、合図を立てる（終章は `main_finale_bell`・`lamp`・`forge`・`promise`・`seat`） | 07 |

コマンドの引数は、全角の空白を半角に・続く空白を一つに・前後の空白を取って読み直す（`CommandTidy`）。

## 3. 試遊の進め方（コピーして使う）

専用の世界（平ら、または普通の世界）で、OP の権限のある人が次の順に流す。どの章も、最後に `status` で段階を確かめる。

```
/skyabyss play chapter 0
/skyabyss play status
```

- **序盤（章 0 から）**: `/skyabyss play chapter 0` → 序章をふつうに遊ぶ。
- **岩鯨（章 1）**: `/skyabyss play chapter 1` → `/skyabyss play boss whale`（心臓の部屋へ）。順路の部屋なら `/skyabyss play raid whale 2 @a`
- **潮殻（章 2）**: `/skyabyss play chapter 2` → `/skyabyss play boss tide @s 1`
- **雷翼（章 3）**: `/skyabyss play chapter 3` → `/skyabyss play boss raiju @s 2` → `/skyabyss play boss storm @s 2`
- **余燼（章 4）**: `/skyabyss play chapter 4` → `/skyabyss play boss smith @s 3` → `/skyabyss play boss ember @s 3`
- **パーティで**: 全員に `@a` を付ける（例 `/skyabyss play chapter 2 @a`、`/skyabyss play quest next @a`、`/skyabyss play raid tide wit @a`）。一人ずつ違うことをしたいときは名前で指す
- **クエストを進める**: `/skyabyss play quest next`（追跡中）、`/skyabyss play quest next @a main_tide`、サブは id を付ける。段の指定は `quest set <id> <段階>`
- **深淵（章 5）**: `/skyabyss play chapter 5` → `/skyabyss play goto abyss` → `/skyabyss play boss warden` → `/skyabyss play goto abyss-depth-5`
- **どこでも**: `/skyabyss play time noon` `/skyabyss play weather clear` `/skyabyss play god on` `/skyabyss play reset`

## 4. 未対応（理由つき）

- **二章の塩の蔵（ch2 vault）**: 場所が固定の座標ではなく、地形で決まる。`dev ch2 vault x y z` で置く。`play` からは飛ばさない。
- **四章の地上の拠点（ch4 sites）**: `dev grounds go` で行く（場所は `dev grounds sites` で一覧）。`play` の中には入れていない。
- **五章の扉（ch5 door）**: 扉の座標は地上の構造物で、固定の一つがない。`chapter 5 door` で段階を決めてから、地上を探す。
- **悪夢の段階**: 次の段階は記録で決まるので、`boss nightmare` で選べない。
- **岩鯨の心臓の開始**: 専用の開始コマンドが見つからなかった。部屋に入ると始まる前提。確かめていない。
- **深さの候補**: `abyss-depth-<n>` は 1・5・10 だけ候補に出す（任意の n は受け付ける）。

## 5. 自己テスト

`SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="playtest" bash tools/dev/mc_run.sh runServer`（`PlaySelfTest`＝段 `playtest play`、`PlayMultiSelfTest`＝段 `playtest multi`、旧 `PlaytestSelfTest`＝段 `playtest`）。見ること:

0. 複数人（`PlayMultiSelfTest`）: 新しい形（対象つき・なし・旧い形）がすべて構文を通る。偽の二人で `quest next` がメインとサブを一段進め（段階が変わり、追跡が空にならない）、完了後は進めず、`set`・`done` が効く。同じパーティの偽の二人に `raid` を流すと、二人とも指定の段（`2`）と指定の部屋（id）の中に着き、順路の記録の段が合い、主線が `inscriptions` になる。

1. 登録: `skyabyss play` があり、権限 2（権限 1 では通らない）。全部の例の構文が通る。
2. 移動先: 候補の場所を解決し、立てる場所なら OK。立てる場所が取れないものは理由つきで飛ばす（ログに出る）。**この自己テストの世界では背も深淵も組まれていなかったため、立てる場所は 0 件確認、101 件を飛ばした。移動先が本当に立てるかは、背と層を組んだ世界で確かめる必要がある。**
3. 章: 0〜5 の各章で、主線の段階が期待どおり（章 1 は `see_whale`、章 5 は `main_key` 済みと `main_abyss` の `rumor`）。存在しない段階は拒否する。
4. 道具: tier 1〜4 の各巨神の装備の品が、登録されている品。
5. 文言: `ja_jp` と `en_us` の両方に、使う鍵があり、件数が同じ。
6. `lang` の自己テスト（`LangSelfTest`）は、名前空間を自動で拾うので、この名前空間も対象になる。
