# 配布の手順（試遊版）

試遊版の jar を作って渡すときの順。どれもリポジトリ直下で流す。設計は [01](design/01_overview.md) の 9 章（M6 仕上げ）。

## 1. 版を上げる

- `gradle.properties` の `mod_version` を上げる（例 0.9.18 → 0.9.19）。
- 同じ数字に直す所: `README.md`（jar の名前・末尾の「ベータ版（…）」）、`docs/beta_how_to_play.md`（jar の名前・冒頭の「… の試遊では」）、`docs/design/01_overview.md`（9 章の「試遊版は」）、`docs/design/14_beta.md`（1 章）、`docs/HANDOFF.md`（「今の形」の試遊版の行）。
- サイトの変更履歴 `tools/site/pages_c.py` の `ver` に版の塊を足す（日本語と英語の対）。`python tools/site/build.py` で `site/*.html` を作り直す（版の数字は `gradle.properties` から入る）。

## 2. 確かめる

```bash
bash tools/dev/quick_check.sh
```

compileJava・資源の参照と看板の幅・lang の鍵・メッシュの水密・生成物のずれ・文字のはみ出し・階段。ひとつでも落ちたら直してから進む。

```bash
bash tools/dev/selftest_fresh.sh
```

自己テスト全段を新しい大陸ワールドで流す（約 25 分）。`run/logs/latest.log` の `SELFTEST … FAILED` が 0 で、`all steps done` が出ること。急ぐときは変えた所の段だけ（`bash tools/dev/selftest_fresh.sh "cloudsea,skills"`）。

人の試遊で見る所は `docs/review/playtest_0.9.4.md`（六章 `playtest_0.9.1.md`、終章 `playtest_finale.md`）。

## 3. 作る

```bash
./gradlew build
```

`build/libs/skyabyss-<版>.jar` ができる（PNG の可逆圧縮と JSON の詰め込みが入る。Python が要る）。

## 4. 渡すもの

| もの | 中身 |
| --- | --- |
| 本体 | `build/libs/skyabyss-<版>.jar` |
| 必要な mod | GeckoLib（NeoForge 1.21.1 用。開発では `gradle.properties` の `geckolib_version`） |
| 導入先 | NeoForge 1.21.1（`gradle.properties` の `neo_version`）、Java 21 |
| 案内 | [docs/beta_how_to_play.md](beta_how_to_play.md)（遊び方・操作・既知の問題・感想の欄）、公式サイト（`site/` を GitHub Pages に） |
| 世界 | 新しいワールド。世界の種類は「天と淵の大陸」だけ（専用サーバーは `server.properties` に `level-type=skyabyss\:continent`）。難易度はピースフル以外 |
| ゲームルール | 順番 `skyabyssStoryGate` とレベル上限 `skyabyssStoryLevelCap` は入れたまま渡す（00 の 178） |

製品版で見えるコマンドは `/skyabyss play` と `/skyabyss inspect` だけ（`dev/CommandGate`。開発用のコマンドは開発環境でだけ出る）。

## 5. 記録

- `docs/HANDOFF.md` の先頭に版の節（何を変えたか・確かめたこと・次）。
- `docs/review/playtest_<版>.md` を足すのは、試遊で見る所が前の版と変わるときだけ。
- コミットして `m1-sky-pass` を push。
