# 引き継ぎ（2026-11-18 夜）: 全武器の作り直し

ユーザーの頼み: 「武器をすべて新しくして。すべて完成してから、シリーズごとにまとめて見せて」。続けて「ちゃんと Blockbench で作って」「レベル低くない？何回もブラッシュアップしてから次の武器に行くようにして」「完成までやり切って」（ユーザーは離席中）。

## 今の作り方（これ以外で作らない）

1. 平面の設定画（正面のドット絵、1 画素 = 1 単位 x32）を描き、暗い背景で参照の斧（`docs/review/refs_1118/02_crystal_axe.jpg`）と並べて 3 回以上直す。
2. 箱の割り方を書く。
3. `tools/gen/pixel/concept_boxes.py` で絵の板を作る（箱の表と裏に設定画、側面に素材の絵）。
4. Blockbench（MCP）で箱を置き、側面と表を筆で描き込む。
5. 書き出して取り込み、`tools/gen/pixel/dark_compare.py` で参照と並べる。
6. ブラッシュアップを 3 回以上。

- 手本: `tools/models/x32/whale_hammer.bbmodel` と `tools/models/x32/concepts/whale_hammer_*`。見比べの絵は `docs/review/whale_hammer_exemplar_1118.png`。
- 決まりの文書: `docs/design/60_gear_look_roles.md` の 1.9（作り方・ブラッシュアップ）と 1.8（参照との差）。
- 担当の指示: `.claude/agents/gear-*.md`（設定画 → Blockbench の流れ、ブラッシュアップ、Blockbench の注意）。

## Blockbench の注意（無人で回すとき）

- **プロジェクトを閉じない。** `close_project` は保存の確認の窓を出し、窓が出ている間は MCP の道具が止まる。人が押すまで戻らない。
- **Blockbench は 1 つなので、担当は 1 人ずつ。**
- `create_project` のあとは `get_project_info` で作業先を確かめる。タブが溜まると作業先がずれる。
- 画面の外で描く表示（offscreen view）は 4 つまでで、消す道具は今エラーになる。既にある `look` を使い回す。
- 絵は data URL ではなく PNG の絶対パスで `create_texture` に渡す（uv_width/uv_height を絵の大きさで）。data URL を手で写すと壊れる。
- 書き出しは `export_model`（codec project、path を指定、overwrite）。

## 進み具合（台帳 `tools/models/x32/manifest.json` の craft、人が読む表は `docs/review/gear_manifest.md`）

- polished（参照の水準）
  - 祠の 6 品（ユーザーが OK）
  - 鯨の鎚（手本）
- bb（Blockbench で置き直しただけ。水準が低い。作り直す）
  - 鯨の残り 9 品
  - 潮の 5 品（鎚 2・杖 2・鈴杖）
- script（スクリプト版。作り直す）
  - 潮の残り
  - 嵐、火の一部
- none（まだ）
  - 月、神器、塔、深淵、野の武器 85 品、その他

## 次にすること（順番）

1. 鯨の伝説の 1 品（`whale_choir_hammer`）を同じ作り方で手本にする。
2. 鯨の残りを 1 品ずつ（担当を 1 人ずつ、指示どおり）。各品を `dark_compare.py` で見て、手本より低ければ戻す。
3. 潮 → 嵐 → 火 → 月 → 神器 → 塔 → 深淵 → 野（土地ごと）→ その他。シリーズごとにコミットする（main には入れない）。
4. 全部終わったら、シリーズごとの画像（暗い背景の並び＋ゲーム内の撮影）を作って見せる。

## 残っている問題

- 立体の描き方（look a）は、強化の段（+5/+10）・輝き（foil）・神器の光の脈を描いていない。レア以上を「済み」と言う前に足す。
- 前へ突き出す持ち方（槍・長柄・火かき棒）は、三人称の 1/4 ブロックの持ち上げで撮って確かめていない。
- 道具を直したら `bash tools/models/x32/build_shrine.sh` で祠の 6 品を作り直し、`look/a/shrine_*` に差分が出ないことを確かめる。
