# 引き継ぎ（2026-11-21）: 全防具の作り直し（look5）

ユーザーの頼み: 「武器のモデルがすべて終わったので、全防具のモデルの作り直しを行って」。対象は 37 組（野の wild_* も含む）、部位 4 x 階級（無印・_rare・_legend）。

## 今
- 岩鯨の兜 v15 が決まった（2026-11-21、ユーザー OK）。正本 `tools/models/x32/armor/stone_whale.bbmodel`（Blockbench プロジェクト sw_helm15、群 armorHead）。絵 `docs/review/armor_look5/stone_whale_helm_v15b.png`・`_v15b_game.png`。
- 次: 岩鯨の胴（armorBody＋両腕）→ 脚 → 靴を同じ作り方で。そのあと残り 36 組をシリーズごとに。まだ: アイコン（3D から）、強化段階（_plus5/_plus10）、古い生成スクリプト（install_armor_v2.py ほか）が新しい模型を上書きしないようにする。

## 作り方（決まり。理由はメモリ skyabyss-armor-rebuild）
- 大きさは体・頭の箱＋1 画素（x32 で ±2 単位。頭は ±8 → 兜の外面 ±10）。1 画素 = 2 単位、板の厚さは 2 単位。
- 形は Blockbench で手で置く。角は外側の面どうしが 1 本の線で出会う（突き合わせ。斜めの板は回転の中心を外側の面の線に置く）。のぞき穴・格子は本当の隙間にして、中に暗い芯（d_ 、頭より少し大きい）を置く。覆う兜に顔を描かない（骨色の縦格子は歯に見える）。
- 塗り: Blockbench から書き出し → `python -X utf8 -I tools/models/x32/look5/armor_paint.py <in> <out>`（箱名の頭 b_ 骨 / d_ 影 / g_・_glow 光 / ほか岩。名前に _t で縁取り、_p で「板の中の枠」）→ `python -X utf8 -I tools/gen/pixel/armor_view_head.py <out> <png>` で確かめる → Blockbench に create_texture（uv 128）して set_cube_uv で全箱の UV を貼る → 正本へ書き出し → `python -X utf8 -I tools/gen/pixel/bb_armor_import.py <正本>`（ファイル名が組の名前になる）。
- 確かめ: 取り込みの rot_point で角の座標・はみ出しを数値で見る（y 回転の向きの取り違えがあった）。ゲームは `./gradlew runShots --offline --no-daemon -Pscript=tools/shots/armor_helm_close.txt -PshotsName=<名>`（防具立ての兜を 斜め前・正面・後ろ。絵は run/shots/<名>/shots/armor_helm_close/）。
- 描画: `GearLook.armor` は 'a' が既定。`LowPolyArmorRenderer` が look の模型（階級 → 無ければ無印）を描く。古い `lowpoly/armor/`・`splat/armor/` は触らない。

# 引き継ぎ（2026-11-18 夜）: 全武器の作り直し

ユーザーの頼み: 「武器をすべて新しくして。すべて完成してから、シリーズごとにまとめて見せて」。続けて「ちゃんと Blockbench で作って」「レベル低くない？何回もブラッシュアップしてから次の武器に行くようにして」「完成までやり切って」（ユーザーは離席中）。

## 今の作り方: look5（2026-11-18 夜、ユーザーが「めちゃいい。これで行こう」と決めた）

手本: 鯨の鎚 v5。
- 箱と下塗り: `tools/models/x32/look5/whale_hammer.py`
- 模型: `tools/models/x32/whale_hammer.bbmodel`
- ゲーム内の撮影: `docs/review/whale_hammer_v5_ingame.png`
- 参照: `docs/review/refs_1118/`（12〜15 が決め手）

見た目の決まり（ユーザーの言葉: 「のっぺり」「チープ」「最低でも 2 ドット」「弓みたいに綺麗に曲げる」「違和感」を直した結果）:
1. **形**: 細く長くする。柄は太さ 3、全体は一方向に流れる。部品はどれも 2 ドット以上（1 ドットの部品は作らない）。絵の細かさは 1 単位 = 1 画素（x32）のまま。
2. **曲線**: 段の箱で作らない。少しずつ回した節（rotation z）をつないで円弧にする。`bb_import` は回転に対応している。
3. **色**: 1 色相の系統（鯨なら深い紺 → 青 → 泡の白）に、差し色 1 つ（骨）と光 1 つ（結晶）。素材を 5 つ同じ強さで並べない。
4. **色の流れ**: 長さ方向に流す。柄は根元が暗く頭へ明るく、刃・ひれは根元が暗く先端が白近くまで明るい。
5. **面の塗り**: 面の向きで明暗を変える（上 +1・横 -1・下 -2）。上と左の縁は明るく、下と右の縁は暗く、左上の角に光を入れる。大きな面には、1 画素内側に骨の縁取りの線。結晶は菱形の切子。握りは 2 ドットの輪と影の帯を交互に巻く。点の模様（まだら）は使わない。
6. **元の特徴は残す**: 参照の構造を写さない（四角い枠など）。前の模型の素材・特徴の部品で組む。

作業の流れ:
1. `tools/models/x32/look5/<id>.py` を手本にならって書く（箱・回した節・素材・下塗りの規則）。作業フォルダで実行し、`v5.json` と `v5_base.png` を出す。
2. Blockbench で次の順に組む。
   - create_project → get_project_info で作業先を確かめる。
   - create_texture（data に `v5_base.png` の絶対パス、uv_width/uv_height は絵の大きさ）。
   - 箱を place_cube で一つずつ置く（faces に UV、回す節は origin と rotation）。
3. 3/4 の角度（offscreen view `look`）で見て、筆（paint_with_brush）で描き足す。
4. 書き出して取り込み、撮影で確かめる。
   - export_model（スクラッチパッドへ）→ name を item id にして `tools/models/x32/<id>.bbmodel` に置く。
   - `bb_import`、`look_holds.py`。
   - `./gradlew runShots --offline --no-daemon -Pscript=tools/shots/gear_plate.txt -PshotsName=gear_plate` で、祠の鎚・剣と並べて撮る。

- 古い作り方（設定画 → 箱の絵の板、薄い絵の板）はユーザーが却下した。`concept_boxes.py` の流れには戻らない。
- 担当の指示（`.claude/agents/gear-*.md`。git には入らない手元のファイル）と doc 60 の 1.6・1.9・1.10・2.4 は 2026-11-19 に look5 とこれまでの決まり（レア度ほど派手・形そのものをデザイン・板の絵）に書き直した。防具の担当だけは古い道具の使い方のまま。

## シリーズの個性（2026-11-18 夜、鯨で決まったこと）

- ユーザー: 「祠（初期）の武器に寄せすぎ」「それぞれのシリーズでもっと個性や奇抜な物に」「剣でか」。
  - 祠の組み方（段の刃・箱の頭）を使わず、シリーズの生き物・題材のモチーフで一品ずつ形を立てる。
  - 鯨の例: 一角の牙の短剣、顎の骨の大剣、潮吹きの杖、尾びれの星杖、ひげ板の鈴杖、鯨の背の鈴杖、頭骨の鎚、フジツボの弓、肋骨の籠の長弓。
- ユーザー: 「blockbenchでちゃんと作り直した？」。
  - スクリプトで箱を置いただけでは手作りと言わない。
  - 必ず Blockbench で 3/4 を見て、箱を目で直し（modify_cube / remove_element）、筆で描き足す。
  - 報告では、スクリプトで作った部分と手で直した部分をはっきり分けて書く。
- 大きさ: 剣は祠の剣（全長 70 前後）を超えない。
- 道具（tools/models/x32/look5/）:
  - lib.py: 塗りの規則、節、円弧。
  - whale_bold.py: 鯨の設計。シリーズごとに <series>_bold.py を作る。
  - preview.py: 下見。Blockbench で組む前に形を確かめる。
  - install.py / install_bow.py: 取り込み。弓の引きの 3 段は弦の差し替えで作る。
- 古い *_parts.py は build_all.sh で新しい模型を上書きしてしまうので、シリーズを作り直したら消す。

## 進み具合（look5）

- 全武器の look5 は済み（225 品。祠 6 品は元のまま）。シリーズごとの経緯・色・形の決まりの記録は `docs/history/handoff_look5_2026-11-18_to_21.md`（丸ごと読まず、シリーズ名や品の id で Grep）。
- 次: 全武器 look5 完了。一覧は docs/review/all_weapons.html。
- 作業の手順のこつ: 作業フォルダで `python -I <series>_bold.py` → `python -I tools/models/x32/look5/dump.py <id>`（箱ごとの place_cube 引数を出す）→ Blockbench で place_cube を 1 箱ずつ → 3/4 を見て modify_cube → 筆 → export → install.py。

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

## わかったこと（鯨の合唱の鎚を担当に 2 回作らせて、どちらも差し戻し）

- 担当が作ると、頭が奥行きの深い四角い箱になり、斜めから見ると「棒の先の角灯・箱」に見える。設定画を正面で直しても、立体では奥行き（z）が箱らしさを決める。
- 次からの決まり: 頭の奥行きは高さの半分以下（例: 高さ 16 なら z ±4）にし、表と裏の設定画が見える面を広くとる（参照は平たい絵）。頭は横長（幅 ≥ 高さの 1.8 倍）。柄は全長の 45〜55% を残す（頭の割合のために柄を詰めない）。吊り輪など上に立つ部品は角灯に見えるので付けない。
- 手本（鯨の鎚）も、奥行きを 6〜8 に薄くするとさらに参照に近づくはず（未確認）。
- 鯨の合唱の鎚は bb のまま（`docs/review/whale_choir_hammer_bb.png`）。上の決まりで作り直す。

## 次にすること（順番）

1. 鯨の伝説の 1 品（`whale_choir_hammer`）を同じ作り方で手本にする。
2. 鯨の残りを 1 品ずつ（担当を 1 人ずつ、指示どおり）。各品を `dark_compare.py` で見て、手本より低ければ戻す。
3. 潮 → 嵐 → 火 → 月 → 神器 → 塔 → 深淵 → 野（土地ごと）→ その他。シリーズごとにコミットする（main には入れない）。
4. 全部終わったら、シリーズごとの画像（暗い背景の並び＋ゲーム内の撮影）を作って見せる。

## 残っている問題

- 立体の描き方（look a）は、強化の段（+5/+10）・輝き（foil）・神器の光の脈を描いていない。レア以上を「済み」と言う前に足す。
- 前へ突き出す持ち方（槍・長柄・火かき棒）は、三人称の 1/4 ブロックの持ち上げで撮って確かめていない。
- 道具を直したら `bash tools/models/x32/build_shrine.sh` で祠の 6 品を作り直し、`look/a/shrine_*` に差分が出ないことを確かめる。
- 2026-11-21 までの日付つきの記録（弓の作り直し、深淵・野シリーズの作り直し、全武器の確認、名前の変更、五章の武器、専用の逸品の決まり、武器の向き・大きさ）は `docs/history/handoff_look5_2026-11-18_to_21.md` へ移した。
- 2026-11-21 ユーザー「blockbench で直しを必ずしてね」: 模型の直しは必ず Blockbench (MCP) を通す。数値を .bbmodel へ直接書かない（単純な拡大縮小でも）。通し直しの道具 `tools/models/x32/look5/bbpass.py`（prep → Blockbench で create_project・create_texture・from_geo_json（パス渡し）・メッシュは place_mesh・export_model → done。形と絵が元と同じか確かめてから取り込む。Blockbench の中で形を直したときは done に `--edited`）。直接書いていた形の直し 15 本（縮めた剣・短剣 8、沈め錨、灯り番の剣、鞴・歯車・月秤の鎚、火掻き、星図の杖）は通し直し済み（どれも形は同一）。淵の下・上の野の武器 42 品の色の持ち上げも、42 品すべて Blockbench を通し直した（どれも形・絵は同一。絵の明るさの計算そのものはスクリプトで、Blockbench では読み込み → 書き出しだけ）。弓 7 本は通したあと install_bow.py で引き絞りの模型（_pull0〜2）を作り直したので、引いた弓の絵も明るくなった。取り込み後の look a の模型は HEAD と数値で比べて、頂点・UV とも丸め誤差 (0.0001) だけ。bbpass.py は UV の大きさを絵の欄 (uv_width / uv_height) から取る（wild_spark_dagger は resolution 欄が 16x16・絵は 128x16 と食い違っていて、一度 UV が 8 倍にずれた → 直して取り込み直し済み）。done は UV も比べるようにした。入っている模型そのものの検査 `tools/models/x32/look5/model_check.py`（離れ・同じ平面の面の重なり・柄の突き抜け。joint_check.py は設計ファイルを組み直すので、手を入れたあとの模型は見ていない）。全 225 品の結果: 離れ 2（番人の大鍵の石 = 浮かせる意図、螺旋貝の… ではなく大蛤の鎚 abyss_tide_hammer の蝶番が 0.2 浮いていた → Blockbench で沈めた）、突き抜け 8 件は絵で見て問題なし（雷石の鎚の柄の先は意図した栓、ほかは判定の粗さ）、面の重なり 193 件は同じ素材の箱を十字に重ねる作りで、ゲーム内の拡大撮影（1920x1080 の一人称 12 本）でちらつき・縞は出ていない。
