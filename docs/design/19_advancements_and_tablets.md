# 天と淵のあいだ 進捗と碑

- 文書番号: 19
- 範囲: この Mod 専用の進捗（advancement）の木と、構造物に置く看板の代わりの石の碑（`skyabyss:tablet`）。
- 関連: [18](18_notices.md)（札・字幕）、[05](05_lore_npc_quest.md)（碑文の条件 `sign`）、[17](17_opening_and_rpg_feel.md)（序盤の拍子）、[10](10_progression_multiplayer.md)

---

## 1. 方針

- 試遊の指摘: 「このmod限定の進捗を足してもいい。本を見なくても直感的にいろいろ分かる」「看板に文字を書いていると世界観がこわれる」。
- 進捗はバニラの進捗画面（L）で見える、物語の道しるべ。書や控え帳を開かなくても、今どこまで来たかが分かる。文は短く、先を明かさない。
- 看板は置かない。文字は石の碑に刻み、見る・触れると画面の字幕に出す。

---

## 2. 進捗の木

データは `data/skyabyss/advancement/*.json`、文は `assets/skyabyss/lang/{ja_jp,en_us}.json` の `advancement.skyabyss.*`。**生成は `tools/gen_advancements.py`**（直すときはここを直して再生成。背景の絵 `textures/gui/advancements/backgrounds/skyabyss.png` も作る。ほかの道具が書く進捗 = 転生の `rebirth_*` は消さない）。38 個。

| 枝 | 進捗（id） |
| --- | --- |
| 根 | `root`（「天と淵のあいだ」。入った時点で付く。アイコンは天の鍵の欠片、背景は暗い石） |
| 序盤 | `camp` 消えた火の跡 → `footprint` 巨大な足跡 → `sky_shadow` 空を渡る影 → `bell_lodge` 鐘の宿り → `storyteller` 語り部 → `lore_book` 伝承の書 / `isana` 鯨を追う人 → `bone_hill` 骨の丘 / `isana` → `bone_pit` 骨の坑（坑の頁 `lore:ch1/trail_iron`。アイコンは鉄鉱石）→ `whale_trail` 鯨追いの支度（支度の段 3 本 `signal:ch1_ready`。goal。アイコンは鯨追いの印） |
| 岩鯨 | `stone_whale_board` 岩鯨の背（背に乗る）→ `tablet` 刻まれた言葉 / `stone_whale_trial` 最初の試練 → `stone_whale_heart` 岩鯨の心臓 → `voyage` 巨神の旅 |
| 潮殻・雷翼・余燼 | それぞれ `*_board`（背）→ `*_trial`（試練を一つ越える）→ `*_heart`（心臓を止める）。前の巨神の心臓を止めるまで見えない（hidden） |
| 鍵 | `sky_key` 天の鍵（余燼の心臓のあと。hidden） |
| 成長 | `skill` 最初の技 → `tree_sword` 剣の道 / `tree_bow` 弓の道。`level_5` → `level_10` → `level_20` → `level_30`（hidden）→ `level_40` → `level_50` |
| 鍛え | `forge` 共鳴の炉 → `forge_enhance` 一打ち → `forge_dismantle` 砕いて、残す |

- **章**（[05](05_lore_npc_quest.md) の 8 章、`story/Chapters`）: 進捗の id・親子・条件は変えず、**文（description）の頭に章の名前を付けて**枝を章ごとに読めるようにする（「一章　岩鯨　岩の鯨の背に乗った。」、英語は「Ch. I Stone Whale: …」）。序章 = `camp`〜`lore_book`（B0〜B6）、一章 = `isana`・`bone_hill`・`bone_pit`・`whale_trail`・岩鯨の枝・`voyage`、二章 = 潮殻の枝、三章 = 雷翼の枝、四章 = 余燼の枝と `sky_key`。根・成長・鍛えの枝は章に属さない。章の名は `tools/regroup_lore_chapters.py` の表を `gen_advancements.py` が読む（`CHAPTER_OF` が id → 章）。
- 枠: 通常は `task`、巨神の背・最初の試練・技の木・巨神の旅は `goal`、心臓・天の鍵・高いレベルは `challenge`。
- チャットには流さない（`announce_to_chat: false`）。画面の右上に札（トースト）だけ出す。
- クラスを選ぶ画面はない（04 の 7 章）ので、「クラスの選択」は「その木の最初の技を覚えた」（`tree_*`）で代える。

### 2.1 条件

| 種類 | 使う所 |
| --- | --- |
| `minecraft:tick` | 根 |
| `minecraft:changed_dimension`（`to` が各背のディメンション） | 4 つの背に乗る |
| `minecraft:inventory_changed` | 伝承の書、共鳴の炉 |
| `skyabyss:beat`（`{"event": "<名前>"}`） | そのほか全部 |

`skyabyss:beat` は `advancement/BeatTrigger`（`SimpleCriterionTrigger`）。物語のコードからは `Beats.fire(player, "<名前>")` の一行で送る。名前の一覧は `advancement/Beats` の先頭の表。既存の呼び出しの中に **自動で送る口**を置いたので、個々のクエストやボスの処理には手を入れていない。

| 名前 | 送る場所 |
| --- | --- |
| `signal:<合図>` | `Quests.signal`（`sky_shadow_seen`、`titan_voyage`、`sky_key_assembled` など） |
| `talk:<役>` | `Quests.onTalk`（`skyabyss:storyteller`、`skyabyss:whale_chaser`） |
| `lore:<項目>` | `Lore.unlock`（記された頁すべて） |
| `place:camp / shelter / footprint / ribs` | `OpeningDirector.placeBeats`（序盤の場所の半径内に入った。見回りのたびに送る） |
| `trial:<巨神>` | `Quests.signal` が背中の試練の達成 `back_trial_cleared` を受けたとき、いる背の巨神で |
| `stopped:<巨神>` | `Progression.onTitanStopped`（その場にいる参加者） |
| `level:<n>` | `Growth.celebrate`（飛び越えた分も一つずつ） |
| `skill:learned`、`skill:tree:<木>` | `SkillManager.learn` |
| `forge:enhance / dismantle / reforge` | `ResonanceForgeMenu` |
| `tablet:read` | `Tablets.read`（碑を読んだ） |

新しい区切りを足すときは、`tools/gen_advancements.py` に一行足して再生成し、必要なら `Beats.fire` を一行呼ぶ。

### 2.2 札の重なり

進捗の札も、18 の知らせの札（`NoticeToast`）、クエストの札、伝承の札も、すべてバニラの `ToastComponent`（右上の同じ列）に並ぶ。列が一つなので**重ならない**。同時に出た札は順番に積まれる（最大 5 枚が見え、残りは待つ）。字幕・上の帯・画面中央は別の場所なので、進捗の札とは重ならない。ただし、心臓を倒した瞬間は「獲得」「進展」「伝承」「進捗」が続くので、札の列がしばらく埋まる。

---

## 3. 碑（`skyabyss:tablet`）

- ブロック: `tablet`（`tablet/TabletBlock`、持ち主のエンティティ `TabletBlockEntity` が文のキーを持つ）。壊せない（構造物の一部）、当たり判定なし（看板と同じ）、明るさ 5。壁掛け（`standing=false`、`facing` = 文の向き。板が反対側の壁にぴったり付く）と、台つきで立てる形（`standing=true`）がある。
- 見た目: 青みの暗い石に、読めない古い印がかすかに青緑に光る。ときどき光の粒（`END_ROD`）が昇る。絵は `tools/gen_tablet_texture.py`（モデルとブロック状態もここで作る）。
- 読み方: **右クリック**か、**4.5 ブロック以内で目を向ける**（0.25 秒ごとに見回す）と、刻まれた文が画面下中央の字幕（NARRATE、[18](18_notices.md)）に、看板だった時の行のまま出る。同じ碑は 30 秒たつまで読み直さない。読むと伝承の条件 `sign`（05）と進捗 `tablet:read` が進む。
- 文: lang の `sign.skyabyss.<名前>.1〜4`（看板の時のキーのまま。空の行は飛ばす）。
- 置き方: 各背のキット（`BuildKit` `TideKit` `StormKit` `EmberKit`）の `wallSign` と、立てる碑の `standingSign` / `standingTablet`。呼び出しは変えず、中身を碑に替えた。序盤の石板は blueprint `opening/slab.json`（`tools/rough/opening_sites.py` の `slab`）に碑を入れ、`RibBuilder` が文を刻む。
- 置いてある所: 岩鯨の背 5（碑文）、潮殻 9、雷翼 9、余燼 9、序盤の骨の石板 1、骨の坑 2（坑口 `sign.skyabyss.ch1_bone_pit`・冷えの戸の奥 `sign.skyabyss.ch1_cold_door`。部品の碑に `BonePit.finish` が刻む）。 試練の入口の碑は置かない（順路の入口は [32](32_titan_raid_route.md) の 2.2）。
- `dev/StructureDump` は碑を看板と同じ欄（`signs`）に 4 行のキーで書く（`tools/structure` の `## signs` で見える）。

### 3.1 自己テスト

| 段 | 見ること |
| --- | --- |
| `titan back` / `tide back` / `storm back` / `ember back` | 背中の碑が決めた数・決めた場所にあり、後ろの壁が固く、文が lang にある（看板は 0 枚） |
| `opening`（`SiteSelfTest`） | 骨の石板に碑があり、`lore_bone_slab` の文が刻まれている |
| `lore` | 碑文のキーが全部、伝承の `sign` の条件と対応する |
