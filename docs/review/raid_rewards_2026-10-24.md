# レイドの部屋の報い (2026-10-24)

docs/design/32 の 9.4 の数を、部屋と中ボスの間を越えたときに渡す実装。`trial/raid/RaidRewards`。

## 報いの表 (一人分、満額)

| 場所 | 素材 | 共鳴の欠片 | その他 |
|---|---|---|---|
| 歩の部屋 | 1 | 1 | |
| 智の部屋 | 1 | 1 | |
| 闘の部屋 | 2 | 1 | |
| 中ボスの間 | 2 | 2 | 共鳴の結晶 1 |
| 崩れなしで順路を越えた (心臓を止めた人) | | 2 | |

- 素材は巨神の章のもの (潮殻の殻片・雷翼の欠片・余燼の燠石・岩鯨の鱗片)。
- 渡し方は経験値と同じ。居合わせた人は満額。同じパーティで居合わせなかった仲間は `Parties.shareRate`。渡せない人の分は `TitanLoot.handOver` が預かり箱へ。
- 周回 (初回の順路のあと、祠から): 部屋の報いは半分。その日に周回の部屋を 6 つ超えたら 4 分の 1。心臓の順路の報い (崩れなし) も周回では半分。

## 称号

巨神ごとに三つ、一人一度だけ。`TrialCodex` に `raid.<種類>.<巨神>` で保存。

| 種類 | 条件 | 例 (潮殻) |
|---|---|---|
| full | 順路を初めて全部越えた | raid.full.tide_shell |
| rooms | 六つの部屋 (歩・智・闘の二案ずつ) を全部越えた | raid.rooms.tide_shell |
| nowipe | 一度も崩れずに順路を越えた | raid.nowipe.tide_shell |

- 名前は trial lang の `trial.skyabyss.title.raid.<id>` (pool.py から生成)。
- 得たときは `Notice.unlock` で知らせる (偽の人には送らない)。
- 表示: 試練の板 (`TrialBoardScreen`) の「称号」の行。三つ揃うと二行に折り返す。

## 実装の場所

- `src/main/java/jp/revati/skyabyss/trial/raid/RaidRewards.java` (数・中身・称号・渡し方)
- `src/main/java/jp/revati/skyabyss/trial/raid/RaidRun.java` (部屋・中ボスの間を越えた時に呼ぶ)
- `src/main/java/jp/revati/skyabyss/client/trial/TrialBoardScreen.java` (称号の行、カードと足の配置)
- `src/main/java/jp/revati/skyabyss/client/notice/CardFeed.java` (獲得の知らせの本文の行数)
- 開発用: `/skyroute pass` (今の部屋を越えたことにする)、`/skyabyss dev trial title <id>` (称号を一つ渡す)、`/skyabyss dev trial board tide_shell` (試練の板を開く。`tide` では開かない、id は `tide_shell`)

## 今回の修正

1. `/skyabyss dev trial title` が「引数が正しくありません」で失敗していた。`TrialCommands.java` で `title` の枝が `mods` の枝の下に付いていたので、閉じ括弧の位置を直した。`granted raid.full.tide_shell` などが返ることを確認。
2. 獲得の知らせの文が途中で「…」に切れていた。`CardFeed.lines` は本文を二行で切っていたので、四行まで出すように変えた (カードは行数だけ高くなる)。最後の行に入らない分だけ「…」。撮り直しで「受け取ったもの: 【潮殻の殻片】 【共鳴の欠片（潮殻）】」が全部出ることを確認。
3. 試練の板 (`TrialBoardScreen`) の GUI 3 での崩れ:
   - 三つ目のカードが足の行に重なっていた (カードは 56 px おきに 52 px、足は画面の下から 40 px)。カードを 52 px おき・48 px の高さにして、板の高さを 244 → 252 に。
   - 称号の行が右端で切れていた。足の称号を `JpWrap` で二行に折り返し (`TrialBoardScreen.renderFoot`)。
   - 「初めて」の印がカードの下の縁を越えていた。枠の絵を 18 px、枠の名を y+24、印を y+34 に寄せた。
4. 試練の板が開かない原因は `dev trial board tide` の `tide` (playtest の名前) だった。`tide_shell` で開く。
5. 撮影の台本: 背の組み立て (潮殻の背は一分ほど) が終わる前に背へ入ろうとして「背の上ではない」になっていた。待ちを 300 → 1500 tick に。

## テスト

- `compileJava`: 成功 (警告のみ、既存)。
- `SKYABYSS_SELFTEST_ONLY="raid rewards"` (新しい世界): `SELFTEST raid rewards PASSED` (1 回目の失敗だった no-wipe の欠片は、偽の人がサーバーの一覧に居ない問題。直したあとの再実行で通った)。
- 全体の自己テスト (新しい世界、カードと知らせの変更の前): 174 件 `PASSED`、`FAILED` 0 件。`SELFTEST runner: all steps done in 1552 s`。プロセスは exit 0。
- 知らせと試練の板の変更のあとは、全体の自己テストは回していない (撮影と目視だけ)。
- `check_lang_keys.py`: 2095 キー、不足 0。
- `check_text_fit.py`: 8970 件、問題 0 (知らせと板の変更のあと、再実行で 0)。
- `check_generators.py` (`PYTHONPATH=tools/lib`、Python 3.9.10、numpy 1.26.4 / PIL 11.1.0 あり): 全生成スクリプトが走り、ドリフト 0、失敗 0。

## 撮った絵 (`docs/review/previews/`)

- `raid_rewards_notice_walk.png`: 歩の部屋を越えた直後。「部屋を越えた」「獲得 受け取ったもの: 【潮殻の殻片】 【共鳴の欠片（潮殻）】」「進捗 満ち引きの試練」。文は切れていない。
- `raid_rewards_notice_late.png`: 少し後。知らせは消えて、次の部屋の壁画と「潮見の暦」の手がかりだけが見える。
- `raid_rewards_board_gui3.png`: 試練の板 (GUI 3)。三つのカードが足に重ならず、印も枠の中。称号は「称号 潮殻の順路を越えた者 / 潮殻を崩さずに越えた者 / 潮殻の六つの部屋を見届けた者」の二行。足の最後の行 (周回の説明) は画面の下端でやや切れる。
- `raid_rewards_board_gui2.png`: 試練の板 (GUI 2)。**まだ崩れている**。文字が左へ約 140 px ずれて、左の端と右の端で切れる。「巨神の祠」の題と「潮殻 1 周目」が出ていない。同じ崩れが修正前の撮りでも出ていたので、板の描き方 (文字の位置の基準) の問題で、今回の変更では直っていない。

## 未解決

1. 試練の板が GUI 2 で崩れる。文字の x 位置が約 140 gui px ずれる (板の枠は正しい位置)。`TrialBoardScreen.render` 周りの文字の基準か、`textBounds` の扱いを見る必要がある。
2. 試練の板の足の最後の行 (周回の説明) が GUI 3 で画面の下端ぎりぎりになる。板の高さを減らすか、説明を一行短くする。
3. 撮影の途中で一度、`raid_rewards` の世界が前の撮影のプロセスに掴まれていた (ログの ファイル使用中 の警告)。以後の撮りは別の名前 (raid_rewards2 … 7) で行った。前のプロセスは止められていない (他の人のものと区別がつかないため)。枠 `.mc_lock2` (pid 1543) の掃除は、ユーザーの判断に任せた。

## 変更したファイル

- `src/main/java/jp/revati/skyabyss/trial/TrialCommands.java` (title の枝の括弧)
- `src/main/java/jp/revati/skyabyss/client/notice/CardFeed.java` (本文は四行まで)
- `src/main/java/jp/revati/skyabyss/client/trial/TrialBoardScreen.java` (カード・足・称号の行)
- `tools/shots/raid_rewards.txt` (撮影の台本)
- `docs/review/raid_rewards_2026-10-24.md` (この文書)
- `docs/review/previews/raid_rewards_*.png` (gitignore の対象。`git add -f`)
