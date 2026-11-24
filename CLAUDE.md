# 天と淵のあいだ（Minecraft mod）— AI 向けの地図

毎回読まれる書類なので短く保つ。くわしい中身はここに写さず、場所だけ書く。

## 決まり

- 返事は日本語。作業ブランチは `m1-sky-pass`。`main` へ直接コミットしない。頼まれるまでプッシュ・コミットしない。
- コミットに入れないもの: `docs/design/60_gear_look_roles.md`（他の人の途中の変更あり）、`tools/shots/gear_tower.txt`、`docs/review/tower_ruin_rough.png`、`tools/models/x32/look5/vox.py`。
- 模型の直しは必ず Blockbench (MCP) を通す。`.bbmodel` へ数値を直接書かない。
- 防具は 1 部位ずつ作り、見直しは回数の上限なしで納得いくまで（`docs/HANDOFF.md` の「作り方」）。
- フォルダの名前や場所は変えない（スクリプト・サイト・書類が場所を決め打ちで指している）。

## トークンを使いすぎないための働き方

消費の大半は「長くなった会話を毎ステップ読み直す分」。だから会話を短く・ステップを少なく保つ。

- 大きい書類を丸ごと読まない。まず `Grep` で見出し（`^#`）や語を探し、`Read` は `offset`/`limit` で必要な所だけ。
  - 対象: `docs/design/*.md`（全部で 2.4MB、1 本 最大 180KB）、`tools/README.md`（78KB）、`docs/HANDOFF.md`、`docs/history/*`、50KB を超える Java。
- コマンドの出力は `tail`・`grep`・件数だけに絞る。長い一覧や数値の列を会話に出さない。
- データは私（AI）を通さず、スクリプトがファイルから直接読む。数値を読んで打ち直す流れを作らない。
- 独立した確認は 1 回のコマンドにまとめる。同じことを小分けに何度も呼ばない。
- 絵は必要なときだけ、小さく。確認用の一覧は 1 枚にまとめる。Blockbench の画面の絵を返す道具は必要なときだけ使う。
- 作業の区切り（コミットした・話題が変わった）で、新しいセッションに移ることをユーザーに勧める。引き継ぎは `docs/HANDOFF.md` の「今」に 3〜5 行で足りる。
- 調べ直しを減らすため、分かった手順はここか `tools/README.md` の該当の節に 1〜2 行で足す（経緯は書かない）。

## どこに何があるか

- `src/main/java/jp/revati/skyabyss/` … mod 本体（約 2000 ファイル）。`src/main/resources/assets/skyabyss/` … 模型・絵・文。
- `docs/design/README.md` … 設計書の索引（まずここを Grep）。`00` は未決の問い。設計書は今の仕様だけを書く。
- `docs/HANDOFF.md` … 今の作業の作り方と状態。古い記録は `docs/history/`。
- `docs/review/` … ユーザーに見せた絵（約 1 万枚）。一覧を出さない。名前が分かっているものだけ開く。
- `tools/README.md` … 道具の使い方（節の見出しを Grep）。`tools/sources/` … 作り直しの元データ（消さない）。
- 武器の模型: 正本は `tools/models/x32/<id>.bbmodel`、台帳は `tools/models/x32/manifest.json`、道具は `tools/models/x32/look5/`。

## よく使うコマンド（リポジトリ直下で）

- 自己テスト: `bash tools/dev/selftest_fresh.sh "<steps>"`
- 模型の検査: `python -I tools/models/x32/look5/model_check.py [id ...] | tail -3`
- 持ち方の検査: `python -I tools/gen/pixel/look_holds.py --check | tail -1`
- 模型の取り込み: `python -I tools/models/x32/look5/install.py <書き出し.bbmodel> <id>`（弓は `install_bow.py`）
- Blockbench の通し直し: `python -I tools/models/x32/look5/bbpass.py prep|done <id> <作業フォルダ>`
- ゲーム内の撮影: `tools/README.md` の「台本で撮るスクショ」の節

## 環境の癖

- Windows。Bash のヒアドキュメントは日本語が化けるので、日本語を含むスクリプトは Write で書く。
- 多くのファイルが CRLF。置き換えるスクリプトは改行をそろえてから。
- Python は 3.9。日本語の長い行を含むスクリプトは先頭に `# -*- coding: utf-8 -*-` を書く（ないと SyntaxError になることがある）。
