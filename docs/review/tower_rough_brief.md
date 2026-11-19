# 塔シリーズ ラフの頼み（2026-11-20）

あいだの塔の武器を、3 案のラフから作り直す。ユーザーが案を選ぶ。

## 前の版で直すこと

前の 5 本（`tools/models/x32/look5/tower_bold.py` の今の関数）は、神器シリーズ用の共通の道具（`divine_kit.py` / `paintbox.py`）で塔の色に塗り替えただけだった。書類 60 にいくつも反している。

- 1.0 シリーズの統一: 承認済みの塔の弓とそろっていない。
- 1.4: 刃の真ん中に細い光る溝がある。
- 1.9 の 6: ほかのシリーズの関数を借りている。
- 2 章: 職の領分を越えている。
  - 魔法使い以外の武器に浮く部品がある。
  - 短剣に大きな金の鍔と大きな光る宝石がある。
  - 鈴杖が先から吊るさず、手を上に掲げている。

前の形は参考にしない。`divine_kit.py` と `paintbox.py` も使わない。

## 階級

書類 03 で、あいだの塔の装備は**神器**（いちばん上の階級）。60 の 1.6 の「★ 伝説・神器」の派手さにする。

## 色（2026-11-20 に直した。いちばん大事）

書類 54 の 2 章「塔の装備」: どれも「天と淵のあいだ」の形。半分が**白の大理石と金**、半分が**黒い石と紫の割れ目**、つなぎ目に**天秤の印**。

- 塔の品は白と黒の二つに分かれた品にする。青灰の slate 一色の品にはしない。
- 色の段階は案のファイルの中で `lib.RAMPS.update` に足す。白の大理石・金・黒い石・紫の割れ目（光）・天秤の印の光、の 5 系統まで。全品で同じ段階を使う（頼み書きの下の表に書き足す。決めたら主の担当に報告）。
- 前のラフ `tools/rough/lowpoly/weapons/one/o_tower.py`（塗りは `paint_tower.py`）が白黒の元の設定。色と天秤の印の作りはそこを読む。
- 承認済みの弓は青灰なので、色はそろっていない。形の言葉（段の石積み・縁金・尖塔・印の目と印の輪）は弓から取り、色は白黒にする。


### 決めた色の段階と分け方（2026-11-20。全品これを使う）

色の段階は `tower_bold.py` に入れた（`from tower_bold import WM, WG, WB, WN, WC, WL`）。案のファイルで `lib.RAMPS.update` を書かない。

| 名前 | 中身 |
| --- | --- |
| `WM` = tw_marble | 白の大理石（天）10 段 |
| `WG` = tw_gold | 金 7 段 |
| `WB` = tw_black | 淵の黒い石 6 段 |
| `WN` = tw_night | 夜の紫（柄・割れ目の地）6 段 |
| `WC` = tw_crack | 紫の割れ目の光（淵の側の光）6 段 |
| `WL` = tw_libra | 天秤の印の光（金白。天の側の光・つなぎ目の印）4 段 |

- 白と黒は**左右（x）で割る**（-x が天の白、+x が淵の黒）。前後で割ると正面と三人称で半分に見えないため。
- 割れ目のつなぎ目に天秤の印（libra の光）を一点置く。黒い側には紫の割れ目（crack）を入れる。
- 画像: 3 案は一案ずつ大きく撮り（表と裏の斜め）、弓と並べた一覧に加えて、案ごとの大きな画像も付ける。

## シリーズの言葉（承認済みの塔の弓 `tower_eye_bow` から。形をこれにそろえる。色は上の白黒）

弓の関数は `tower_bold.py` の `tower_eye_bow`。表は `docs/review/tower_ingame.png` の右下を見る。

| 項目 | 中身 |
| --- | --- |
| 道具 | `lib.Model`（`M()`）の `box` / `seg` / `chain` / `arc` と `lib.Canvas` の板の絵。弓と同じ道具で組む |
| 色 | `tower_bold.py` の `lib.RAMPS` の 4 系統だけ。主は slate（塔の石、青灰）、副は brass（古い真鍮）、光は sigil（印の青白）、差しは abyssal（淵の暗い紫）。品ごとに色の段階を変えない |
| 形の言葉 | 角張った石の板を段に重ねた「塔の石積み」の階段の形（弓腕がそう）。丸い曲線より、段と角 |
| 金具 | 石の板の縁に真鍮の縁金（`plate_rim`）。先は真鍮の尖塔（`spire`）で、先に印の菱の宝石 |
| 見せ所 | 印の目（石の瞼に抱かれた sigil の目、小さな目が継ぎ目ごとに開く）と、印の文字を刻んだ輪（真鍮の二重の輪のあいだに 3×3 の印の文字）。品ごとに置き場所と形を変えて、どれか一つを一点繰り返す |
| 握り | 石の握りに真鍮の四角い輪。柄頭は石の四角錐（今の `grip()`）。シリーズで同じ作りにする |

## 職ごとの品

60 の 2 章の骨格・許されない所を必ず守る。

| 担当 | id | 名前と元の設定（docs/design/03 の塔の装備） |
| --- | --- | --- |
| 戦士 | tower_balance_sword | あいだの大剣: 天の白と淵の黒が半分ずつ。真ん中の境に印。鍔は天秤 |
| 鍛冶師 | tower_pull_hammer | 引き合いの鎚: 二つの重い石の塊を真鍮の環がつなぎ、環の芯（引力の点）が光る |
| 魔法使い | tower_moon_staff | 落ちる月の杖: 石の棒の先に真鍮の爪が下向きの月（光る珠）を吊る。月の下に落ちる欠片 |
| 暗殺者 | tower_sigil_dagger | 追う印の短剣: 細い石の刃に印が刻まれる。鍔は真鍮の四角い輪。刃元に印の菱形 |
| 呪術師 | tower_clap_bell_staff | 合掌の鈴杖: 先に合わせた二つの石の掌。掌のあいだが光る。真鍮の腕輪、鈴が下がる |

## ラフの作り方（ほかの担当と同時に動く。ぶつからないように）

1. 案は自分専用のファイル `tools/models/x32/look5/rough_tower_<担当>.py` に書く（`<担当>` は warrior / smith / mage / assassin / shaman）。
   - 関数名は `<id>_ra` / `_rb` / `_rc`。
   - 色の段階と `M` / `grip` / `diamond` などは `tower_bold.py` から import してよい（同じシリーズの道具なので）。
   - **`tower_bold.py` そのものは書き換えない。**
2. 下見の `preview.py` は `*_bold.py` を全部読み込む。下見のときだけ、案のファイルを `zz_rough_<担当>_bold.py` という名前にコピーし（書き終えたファイルを丸ごとコピー）、走らせたら消す。
   - 作業先は自分専用の `C:/Users/nisimoto/AppData/Local/Temp/claude/789edb19516a07b1/33cb91ee-5ffd-4188-9573-8953440b061c/scratchpad/rough_<担当>/` にする。
   - 例: `python -I tools/models/x32/look5/preview.py <作業先> <作業先>/abc.png <id>_ra <id>_rb <id>_rc`
3. 継ぎ目の検査（`joint_check.py`）は `*_bold.py` の関数を読む。同じくコピーの間に `<id>_ra` などで走らせ、離れた部品がないこと（浮く部品は名前を `float_` で始める。浮かせてよいのは魔法使いだけ）。
4. 影の検査（60 の 1.1）: 単色の正面の影で種類が言い当てられること。
5. 下見が作ったもの（`tools/models/x32/<id>_r?.bbmodel` と `src/main/resources/assets/skyabyss/` の中の `<id>_r?` の模型と絵）は、終わったら消す。取り込み済みの塔の品（`<id>` そのもの）には触らない。
6. 3 案を、**承認済みの塔の弓と並べた**画像にまとめて `docs/review/tower_rough_<id>.png` に置く（並べてそろって見えるかの確認が 1.0 の決まり）。Blockbench は使わない（清書のときに使う）。
7. コミットしない。ユーザーに直接聞かない。

## 派手さの目標（2026-11-20 に足した。一回目のラフは全員が足りなかった）

- 目標の水準: この会話で承認された神器の天穹の剣（`docs/review/divine_sky_pilot7_ingame.png`）と、`docs/review/refs_1119/`。並べて負けないこと。
- 神器 = 大きな頭・厚い板・大きな見せ所。頭（刃・鎚の頭・杖の先）は、5 本の今の版（`docs/review/tower_ingame.png`）より大きく、厚く。
- 部品は 60〜100 個を使ってよい（1.7）。一回目のラフは 20〜30 個の箱で、ただの棒と箱に見えた。
- 輪郭で派手にする: 段の石積みを大きく張り出す、翼・角・尖塔・縁金を重ねて輪郭を変える、白と黒の対比を大きな面で見せる。
- 案ごとに、表と裏の斜めを大きく撮った画像を付ける（小さな一列の絵では判断できない）。

## 返すもの（日本語で短く）

- A/B/C それぞれ一行: 形の芯（モチーフ）、見せ所、シリーズの言葉のどれを繰り返したか。
- おすすめ一つと理由一行。
- 画像のパス。継ぎ目・影の検査の結果。
- 迷ったこと。

## 清書（2026-11-20。ユーザーが 5 本とも A を選んだ）

選ばれた案: 大剣 `tower_balance_sword_ra`、鎚 `tower_pull_hammer_ra`、杖 `tower_moon_staff_sa`、短剣 `tower_sigil_dagger_ra`、鈴杖 `tower_clap_bell_staff_ra`。並べた画像は `docs/review/tower_roughs_all.png`。

清書は一人ずつ順番に行う（Blockbench は一つ、`tower_bold.py` も一つ）。

1. 選ばれた案を `tower_bold.py` の同じ名前の関数（例: `tower_balance_sword`）に清書して置き換える。
   - 今ある関数は神器の道具を借りた前の版なので、丸ごと消す。`towered` / `tgrip` は誰も使わなくなったら消す。
   - `tower_bold.py` の色（WM〜WL）と `M` / `diamond` は使ってよい。`divine_kit.py` は import しない。
   - 塗りは `paintbox.skin` と `pix3d.sprite_planes` を使ってよい（塗りの道具）。
2. 清書で足すこと: 頭を大きく厚くする（ラフの正直な評価で各担当が書いた「足りない所」を直す）。天穹の剣（`docs/review/divine_sky_pilot7_ingame.png`）と並べて負けない描き込みにする。60 の 1.7 の禁止（単色の面・細い光る溝・大きな光る面・鋲）を守る。
3. `joint_check.py <id>` が OK になるまで直す。
4. 取り込み（60 の 1.9 の 3〜5）。作業フォルダは自分専用にする。
   - `WORK=<作業先> bash tools/models/x32/look5/mstep.sh prep tower_bold <id>`
   - Blockbench: create_project（free）→ from_geo_json → create_texture（`<作業先>/geo/<id>_tex.png`）→ g_root に apply_texture（applyTo all）→ export_model（codec project、`<作業先>/out_<id>.bbmodel`、overwrite、max_content_length 0）
   - `WORK=<作業先> bash tools/models/x32/look5/mstep.sh done <id>`
5. ゲームで撮る: `bash tools/models/x32/look5/series_check.sh <id> <id>`。画像は `docs/review/<id>_ingame.png`。斜めで見て、足りない所を直してもう一度（ブラッシュアップは 60 の 1.9 の 7）。
6. 自分の rough ファイル `rough_tower_<担当>.py` は、清書が入ったら消す。
7. コミットしない。ユーザーに直接聞かない。

返すもの: ゲーム内の画像のパス、部品数、スクリプトで作った所と Blockbench で直した所、直しきれなかった所。
