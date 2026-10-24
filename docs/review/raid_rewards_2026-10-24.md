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
- 表示: 試練の板 (`TrialBoardScreen`) の「称号」の行。未取得は「称号: なし」相当の表記。

## 実装の場所

- `src/main/java/jp/revati/skyabyss/trial/raid/RaidRewards.java` (数・中身・称号・渡し方)
- `src/main/java/jp/revati/skyabyss/trial/raid/RaidRun.java` (部屋・中ボスの間を越えた時に呼ぶ)
- `src/main/java/jp/revati/skyabyss/client/trial/TrialBoardScreen.java` (称号の行)
- 開発用: `/skyroute pass` (今の部屋を越えたことにする)、`/skyabyss dev trial title <id>` (称号を一つ渡す)

今回の修正: `/skyabyss dev trial title` が「引数が正しくありません」で失敗していた。`TrialCommands.java` で `title` の枝が `mods` の枝の下に付いていたので、閉じ括弧の位置を直した。直したあと `granted raid.full.tide_shell` と `granted raid.nowipe.tide_shell` が返ることを確認。

## テスト

- `compileJava`: 成功 (警告のみ、既存)。
- `SKYABYSS_SELFTEST_ONLY="raid rewards"` (新しい世界): `SELFTEST raid rewards PASSED` (1 回目の失敗だった no-wipe の欠片は、偽の人がサーバーの一覧に居ない問題。直したあとの再実行で通った)。
- 全体の自己テスト (新しい世界): 174 件 `PASSED`、`FAILED` 0 件。`SELFTEST runner: all steps done in 1552 s`。プロセスは exit 0。
- `check_lang_keys.py`: 2095 キー、不足 0。
- `check_text_fit.py`: 8970 件、問題 0。
- `check_generators.py` (`PYTHONPATH=tools/lib`、Python 3.9.10、numpy 1.26.4 / PIL 11.1.0 あり): 全生成スクリプトが走り、ドリフト 0、失敗 0。abyss_layers.json のドリフトは出ず。

## 撮った絵 (`docs/review/previews/`)

- `raid_rewards_notice_walk.png`: 歩の部屋を越えた直後。「部屋を越えた」「獲得 【共鳴の欠片 (潮殻)】」「獲得 受け取ったもの: 【潮殻の殻片…」「進捗 満ち引きの試練」の四行。
- `raid_rewards_notice_late.png`: 少し後。知らせは消えて、次の部屋の壁画と「潮見の暦」の手がかりだけが見える。
- `raid_rewards_board_no_screen.png`: 試練の板の撮影。**板は映っていない**。`/skyabyss dev trial board tide` は成功 (`result 1`) を返したが、画面は開かなかった。称号の行はまだ目で確かめていない。

## 未解決

1. 知らせの文が切れる: 「獲得 【共鳴の欠片（潮殻）】を、…」「受け取ったもの: 【潮殻の殻片…」と、枠の幅で末尾が「…」になる。知らせの枠の幅か文の短縮の扱いを直す必要がある。
2. 試練の板の撮影: `dev trial board` で板が開かない。板を開くのに別の手が要る (板のブロック近くへ置くか、画面を開く別のコマンド)。板の称号の行はまだ見ていない。
3. 撮影の途中で一度、`raid_rewards` の世界が前の撮影のプロセスに掴まれていた (ログの ファイル使用中 の警告)。今回は `raid_rewards2` という別の名前で撮った。前のプロセスは止められていない (他の人のものと区別がつかないため)。

## 変更したファイル

- `src/main/java/jp/revati/skyabyss/trial/TrialCommands.java` (title の枝の括弧)
- `tools/shots/raid_rewards.txt` (撮影の台本。背に入ってから部屋へ)
- `docs/review/raid_rewards_2026-10-24.md` (この文書)
- `docs/review/previews/raid_rewards_*.png` (gitignore の対象。`git add -f`)
