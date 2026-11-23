# 切り絵の作り替えの進み具合（docs/design/56 の 2.1）

- 1 周目: 共通の部品 `client/ui/Golden` の描く関数（panel・headLine・frame・recess・selected・stripe・rule・tab・button・slot・gem）を切り絵にした。画面の大中小の板（論理の 0,0 の Layout）は黒い紙と桃色の山を窓の端まで敷く。HUD の枠 `hudFrame` は夜空のまま（範囲外）。
- 2 周目（2026-11-24）:
  - 釦 = A 斜めの短冊（3 案のラフ `buttons/rough_1920_g4.png` からユーザーが選んだ）。`Paper.slip`・`Paper.button`・`Paper.tab` が描き、`Paper.Tag`・`Paper.tag(…)`（`Button.builder` と同じ書き方）・`Golden.button/tab`・`RpgTabs`・持ち物のつまみ（`GearPanel`）・バニラの釦（`GlassButtonMixin`）がそれを通る。
  - バニラの画面は絵柄のパック A（`skyabyss_ui_a`、切り絵）が入っていれば mod の釦で描く。滑り棒と入力欄は黒い紙（バニラが白い字を描くため）。
  - 字の色を対応表で置き換えた: 金 → `Paper.ON_BLACK_PINK`、空色 → `Paper.ON_BLACK_HI`、淡い藍 → `ON_BLACK_SOFT`、手がかり → `ON_BLACK_DIM`、藍の地 → 黒い紙の色、細い枠 → `Paper.edge`（右下に濃い桃色の影）。ツールチップは黒い紙に桃色の縁。
- 数える道具: `python -I tools/dev/paper_audit.py`（`Button.builder`・直書きの金や空色・`fillGradient`・`Golden.outline(`・Golden の夜空の色の定数）。0 なら「済」。
- 「組み直し」= 四角い板に四角い欄の組み方をやめて、紙の重ねで組み直した画面。「部品だけ」= 部品と色の差し替えだけで、組み方は四角い板のままの画面。

| 画面 | 状態 | 夜空の残り（数） |
|---|---|---|
| 職を選ぶ（JobChoiceScreen） | 組み直し | 0 |
| スキルの木・名残（SkillScreen・ExtPage） | 組み直し | 0 |
| ResonanceForge・Guild・TrialBoard・TowerDesk・Party・Trade・Box・RouteBoon・PartyFinder・CircuitCamp・CircuitReward | 部品だけ | 0 |
| Soul・Rebirth・Profile・Journey・HunterNotes・GearPanel・DeepChoice | 部品だけ | 0 |
| TownGuide・Shop・Records・RecordsHub・QuestLog・MountShop・Dialogue・Board・NoticeLog・Growth・GlassChest・FacilityUi・MountName | 部品だけ | 0 |
| 板を使わない画面: 持ち物（SkyInventoryScreen）・ポーチ（PouchScreen・BoxPouchScreen・PouchWheelScreen）・旅の地図（地の暗さだけ）・輪（WheelScreen）・チャット（入力欄はパック A の黒い紙） | 色だけ | 0 |
| 伝承の書（LoreBookScreen）・記録の帳（RecordScreen）: ユーザー「切り絵に寄せる」→ 絵柄 p（`tools/gen/story/paper_cut.py`。`python -I tools/gen/story/gen_lore_book.py --install p`・`gen_record_screen.py --install p`。アイテムの絵は替えない）。黒い紙に生成りの頁を重ね、つまみは斜めの紙（重ねて並ぶので筋なし）。字は墨・墨の桃色。記録の進みの琥珀色は意味の色で残す。撮影 `tools/shots/paper_books.txt`（重なり 0）、絵 `books_1920_g4.png` | 組み直し | 0 |
| バニラの釦・滑り棒・入力欄・箱の画面（リソースパック skyabyss_ui_a。`tools/ui/gen_vanilla_skin.py`） | 切り絵 | — |
| 表の外（HUD の知らせと演出。手を付けていない）: QuestToast・CardFeed・QuickCard・SkillHud・AspectRevealScreen・SkyKeyScene・RegenLoadingScreen・CinematicScreen | 夜空のまま | 28 |
| ツールチップ: 地と縁は切り絵（黒い紙・桃色の縁）。中の字の色（神器の目覚め・装備の属性）は品の色のまま | 地だけ | — |
| 旅の地図の枠（藍の縁・青緑の菱）は地図の絵の側。地の暗さだけ替えた | 枠は夜空 | — |

- 色が種類を表すもの（旅の土地の色など）は替えない。行に「意味の色」と書けば数えない。
- 主の釦（桃色）にしたもの: ギルドを作る・取引を出す・依頼を受ける・封印の箱を開ける・乗り物の名前の決定・鍛冶の強化と選ぶ。
- 全画面の撮影（2 周目、`run/shots/paper_round2`、パック A）: 156 件中、重なり 1 件 = 旅の地図の GUI 4 で、読み込み中の「マーカーを確認中...」の末尾の点を省きと見なしたもの（撮る時刻による。重なりではない）。一覧 `all/round2_1920_g4.png`。
