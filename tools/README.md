# tools/

素材を作るスクリプト。どれもリポジトリ直下で `python tools/<名前>.py` のように実行する。
採用済みの素材を古い版で上書きしてしまうスクリプトは止めてある。
どうしても動かすときだけ、環境変数 `SKYABYSS_ALLOW_REGEN=1` を付ける。`gen_items.py` だけは `SKYABYSS_ALLOW_GEN_ITEMS=1`。
経緯は `docs/review/assets_audit.md` の S1。

## ゲームを立ち上げる担当の決まり (並列で動かすとき)

マイクラをいくつ同時に立ち上げてもよい。ただし、ほかの担当のウィンドウに入力が入らないよう、次を守る。

- **OS のキー送り・マウス操作・ウィンドウのフォーカス操作は禁止** (PowerShell の SendKeys、AppActivate、SetForegroundWindow、nircmd など全部)。前面のウィンドウは誰のものか分からない
- **画面の確認は台本で撮る** (下の「台本で撮るスクショ」)。ゲームの中から撮るので、ウィンドウが裏にあっても撮れる。最小化だけはしない
- **建築の抜け・不具合はデータで確かめる** (下の「構造をデータで確かめる」)。試遊 (歩いて回る) での確認はしない
- **コマンドを打つときは** 台本の `cmd`、または専用サーバー (`./gradlew runServer`) の RCON か自己テスト。クライアントのチャット欄へ打ち込まない
- 専用サーバーを並べて立てるときは、worktree ごとに `run/server.properties` の `server-port` (と `rcon.port`) を別の番号にする (同じ番号だと起動時にポートを取れない)
- 終わったら java を残さない。台本は最後に自分でゲームを閉じる (止まっても `timeout` 秒で閉じる)

## 台本で撮るスクショ (`client/dev/ShotRunner`)

```
./gradlew runShots -Pscript=tools/shots/example_tide_back.txt
```

- ゲームのフォルダは `run/shots/<台本の名前>/`、撮った絵と記録は `run/shots/<台本の名前>/shots/<台本の名前>/` (`*.png` と `shots.log`)。台本ごとにフォルダが分かれるので、同じ worktree で別の台本を同時に動かしても混ざらない。同じ台本を 2 本同時に動かすときは `-PshotsName=<別名>` を付ける
- `shots.log` の最後の行が `SHOTS OK <枚数> shots` なら成功、`SHOTS FAILED: <理由>` なら失敗。コマンドの返事もここに出る (チャットには出さない)
- `runClientTest` などほかの起動でも、環境変数 `SKYABYSS_SHOTS=<台本の絶対パス>` を付ければ動く (出力先は `SKYABYSS_SHOTS_OUT` で変えられる)。開発環境でだけ動く
- 台本の世界は `world` の名前で作る (無ければ平らな世界をクリエイティブ・コマンド可で作る。昼は進まない)。2 回目からは同じ世界を開く。巨神の背は `cmd skyabyss dev arrive <titan_back|tide_back>` で建って (無ければ) 移れる

台本の書き方 (1 行 1 手順。`#` で始まる行は注釈):

| 手順 | 意味 |
|---|---|
| `world <名前> [flat\|normal]` | 使う世界 (既定 `shots flat`)。頭に書く |
| `size <幅> <高さ>` | ウィンドウの大きさ (撮る絵の大きさ) |
| `timeout <秒>` | これを過ぎたら失敗として閉じる (既定 600) |
| `cmd <コマンド>` | 統合サーバーで権限 4 で実行 (`/` はあってもなくても)。終わるまで待つ |
| `camera [次元] <x> <y> <z> <yaw> <pitch>` | 体ごとそこへ置いて向きを決める (yaw 0=南 90=西 180=北 270=東、pitch 90=真下)。着くまで待つ。壁を抜けたいときは先に `cmd gamemode spectator` |
| `look <yaw> <pitch>` | 向きだけ変える |
| `waitchunks [最大 tick]` | まわりの描画が出そろうまで待つ (既定 600 tick まで) |
| `wait <tick>` | 待つ |
| `hud on\|off` | HUD と手を出す/隠す |
| `clouds on\|off`、`render <チャンク>`、`fov <度>`、`guiscale <n>` | 描き方の設定 |
| `open <名前>` | mod の画面を開く: `lore` `quests` `growth` `skills` `party` `inventory`、または引数なしで作れる画面のクラスの完全な名前 |
| `key <キーの名前>` | そのキーを押したことにする (KeyMapping。OS の入力ではない)。サーバーから中身を受け取る画面に使う。例 `key key.skyabyss_record.open` (控え帳) |
| `close` | 画面を閉じる |
| `chat clear` | チャット欄と右上の通知 (トースト) を消す |
| `shot <名前>` | 次に描いた絵を `<名前>.png` に保存 |
| `waitdump` | 構造の書き出し (`cmd skyabyss dev dump ...`) が終わるまで待つ |
| `echo <文>` | 記録に書くだけ |
| `quit` | ゲームを閉じる (書かなくても、最後まで来たら閉じる) |

例: `tools/shots/example_titan_arrival.txt` (岩鯨の背の到着)、`tools/shots/example_tide_back.txt` (潮殻の背の全景)。

## 構造をデータで確かめる (`dev/StructureDump` と `tools/structure/`)

ブロックを範囲ごとファイル (`.sbd`) に書き出し、Python で図と検査を出す。歩いて回らなくても、穴・行き止まり・水漏れ・看板が分かる。

書き出し (どれか):

```
./gradlew runShots -Pscript=tools/shots/dump_backs.txt                              # 岩鯨・潮殻の背 → run/shots/dump_backs/dumps/*.sbd
SKYABYSS_DUMP=titan_back,tide_back ./gradlew runServer                               # 専用サーバーで書いて止まる → run/dumps/*.sbd (ポートが空いているとき)
/skyabyss dev dump preset <titan_back|tide_back|storm_back>                           # 台本の cmd や RCON から (島が無ければ建てる)
/execute in skyabyss:titan_back run skyabyss dev dump <名前> <x1 y1 z1> <x2 y2 z2>     # 好きな範囲
```

書き出し先は `<ゲームのフォルダ>/dumps/<名前>.sbd` (環境変数 `SKYABYSS_DUMP_OUT` で変えられる)。台本では書き終わるまで `waitdump` で待つ。
`/skyabyss dev arrive <titan_back|tide_back|storm_back>` は到着地点へ到着の向きで移る (台本で撮るとき用)。

図と検査:

```
python tools/structure/check.py run/shots/dump_backs/dumps/titan_back.sbd --out docs/review --prefix structure_titan_back     --goal shrine --goal hub --seed hub --section y=94 --section x=0 --iso-step 1
```

| 出力 (`<out>/<prefix>_`) | 中身 |
|---|---|
| `top.png` / `iso.png` / `sections.png` | 真上から・等角 (南東の上から)・断面 (`--section y=/x=/z=`)。色はブロックの地図の色、水は青 |
| `walk.png` | 歩き始め (`--walk-from`、既定 `arrival`) から歩いて行ける足の位置 (高さで色) と目標の印 |
| `report.txt` | 目標 (`--goal`) に何歩で着くか (着かなければ一番近くまで来た所。終了コード 1)、部屋 (`--seed`) から外への口の一覧、水が広がる見積もり (今流れている水、流れたら埋まる空気。部屋の中の分)、看板の一覧 (lang のキー) |

起点の名前はヘッダにある (`titan_back`: `arrival` `shrine` `hub`、`tide_back`: `arrival` `rest_hub` `rest_g0` …、`storm_back`: `arrival` `hub` `rest_*` `west_stone` `east_stone` `front_stone` `eye_hall`)。雷翼の背は `tools/shots/storm_back.txt` で書き出しと撮影を一度にする。`"x,y,z"`、`"x,~,z"` (列でいちばん上の立てる所) でもよい。
歩く決まり (上り 1・下り 3・頭 2 マス・水の中は 1 つ上へ) は背の自己テスト (`TideBackSelfTest.walk`) と同じ。水は実際より多めに出る (バニラは近くの落ち口の方へだけ流れるため)。
部品は `sbd.py` (読む)、`analyze.py` (囲まれた空間と口・歩く・水・看板)、`render.py` (図)。新しく背中の検査を書くときは、自己テストに同じ探索を書き足す前にこれで見る。例は `docs/review/structure_*`。

## 採用版の生成元 (実行してよい)

| 素材 (`src/main/resources/assets/skyabyss/` 以下) | 生成元 | 備考 |
|---|---|---|
| `textures/item/*.png` (32x32)、`textures/block/*.png` | `rough/items_blocks_32.py` | 出力先 (`docs/roughs/`) は片付けた。作り直すときは一時フォルダに書き出し、採用した物だけ src へ手でコピーする |
| 岩鯨: `geo/entity/stone_whale{,_mid,_far}.geo.json`、`textures/entity/stone_whale{,_glowmask}.png`、`animations/entity/stone_whale.animation.json` | `install_stone_whale.py` | 実行しても今の素材と同じ物ができる |
| 潮殻の巨神: `geo/entity/tide_shell{,_mid,_far}.geo.json`、`textures/entity/tide_shell{,_glowmask}.png`、`animations/entity/tide_shell.animation.json`、`sounds/tide/*.ogg` | `install_tide_shell.py` (形の元は `rough/tide_shell_titan.py` の案 B。珊瑚の LOD の塊・塔・段々の庭は install 側で足す)、音は `gen_tide_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。確認用の絵は `docs/review/tide_shell_roughs/install/`。アニメの角度は `install_tide_shell.py` の `ANIM` に意図した向きで書く (GeckoLib の符号はスクリプトが直す) |
| 潮殻の背の島 (巨神 B と同じ形のブロックの亀、08 の 21 章): `data/skyabyss/tide_back/turtle.bin` | `gen_tide_back_turtle.py` (形の元は `install_tide_shell.build_grid()`。ブロックの色は `rough/block_colors.json`。`--preview` で上と横の色の地図、`--cache` で調べる用の npz) | 同じ物ができる。変えたら配置の版 (`TideBackLayout.LAYOUT_VERSION`) を上げる |
| 潮殻の背の敵: `geo/entity/{coral_thrall,reef_scuttler,bubble_puffer}.geo.json`、`textures/entity/<名前>{,_glowmask}.png` (珊瑚憑きは `_statue` も)、`animations/entity/<名前>.animation.json`、銛 `geo/gear/coral_spear{,_statue}.geo.json` など | `install_tide_mobs.py` (形の元は `rough/tide_mobs_v2.py` の A 案。頭と泡吹きの目を変えた) | 同じ物ができる。`--eyes original` で泡吹きの目をラフ A に戻す。目の比較は `docs/review/tide_shell_roughs/bubble_puffer_eyes.png`、確認用の絵は `docs/review/previews/tide_mobs/` |
| 雷翼の背の敵: `geo/entity/{feather_warden,static_moth,thunder_supplicant}.geo.json`、`textures/entity/<名前>{,_glowmask}.png` (雷蛾は帯電で足す光 `static_moth_charge.png` も)、`animations/entity/<名前>.animation.json`、鉤槍と杖 `geo/gear/{hooked_glaive,supplicant_staff}.geo.json` など | `install_storm_mobs.py` (形の元は `rough/thunder_mobs.py`。既定は 12 の 14 章の推奨 羽守 B・雷蛾 B・雷乞い C、`--warden/--moth/--supplicant a\|b\|c` で選び直せる。面の隠れは同じ骨の中だけで数える) | 同じ物ができる。確認用の絵は `docs/review/previews/storm_mobs/` |
| 雷翼の背の敵の音 `sounds/storm_mobs/*.ogg` と `sounds.json` の 18 行、スポーンエッグのモデル | `gen_storm_mob_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。`sounds.json` は自分の行だけ書き直す |
| 余燼の背の敵: `geo/entity/{ash_thrall,ember_moth,cinder_roller}.geo.json`、`textures/entity/<名前>{,_glowmask}.png` (灰被りは燃え上がった姿の `ash_thrall_flared` も)、`animations/entity/<名前>.animation.json`、火掻き棒 `geo/gear/ash_poker.geo.json` など。音 `sounds/ember_mobs/*.ogg` | `install_ember_mobs.py` (形の元は `rough/ember_mobs_{thrall,moth,roller}.py` の A 案。`--option thrall=c` などで差し替え。アニメの姿勢の重なりも検査する。約 2 分)、音は `gen_ember_mob_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。確認用の絵は `docs/review/previews/ember_mobs/`、ゲームの中は台本 `tools/shots/ember_mobs.txt` (13 の 18 章) |
| 天の鍵 `textures/item/sky_key.png`、場面の音 `assets/skyabyss_story/sounds/*.ogg` と `sounds.json` (14 の 4 章) | `gen_story_assets.py` (`--option A\|B\|C`、既定 C。比較は `docs/review/sky_key_roughs/compare.png`) | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 1 章の続きのクエスト・会話の足し書き `data/skyabyss/{quest,dialogue/*_story.json,lore/entry/chapter1}`、文 `assets/skyabyss_story/lang`、07 の 16 章の表 | `gen_story_quests.py` (雷翼・余燼の仮の名は `NAMES`) | 同じ物ができる |
| 潮殻の背の敵の音 `sounds/tide_mobs/*.ogg`、泡 `textures/entity/bubble_shot.png`、素材の絵 `textures/item/{tide_shell_shard,tide_dried_shell,bubble_sac}.png` (仮) | `gen_tide_mob_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 心臓: `geo/entity/stone_whale_heart_p1..p3.geo.json`、`textures/entity/stone_whale_heart_p*`、`animations/entity/stone_whale_heart.animation.json` | `install_heart_v4.py` (形の元は `tools/sources/heart/`。`rough/boss_v4.py` の A 案) | |
| 射手: `geo/entity/rib_archer.geo.json`、弓の `geo/gear/rib_archer_bow.geo.json` など | `rough/rib_archer_v4.py` | 同じ物ができる |
| 番兵長: `geo/entity/whalebone_captain.geo.json` / png / glowmask / animation | `rough/captain_v4.py --install` | 同じ物ができる |
| 苔這い: geo / png / glowmask / animation | `rough/moss_crawler_v3.py` | 監査では今の素材と比べていない |
| `textures/gui/sky_vignette.png` | `gen_vignette.py` | 同じ物ができる |
| 成長: `textures/gui/growth/{status,hud}.png`、`sounds/rpg/level_up.ogg` | `gen_growth_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わるが、音は同じ)。画面の配置を変えたら `GrowthScreen.java` と値をそろえる |
| 見る者: `geo/entity/watcher.geo.json`、`textures/entity/watcher{,_glowmask,_glint}.png`、`animations/entity/watcher.animation.json` | `rough/watcher.py --install` | ラフ 3 案は `docs/review/watcher_roughs/`。`--install` なしなら docs にだけ書く |
| `sounds/prologue/*.ogg` (見る者の音) | `gen_watcher_sounds.py` | 同じ物ができる |
| 技: `textures/gui/skill/{icons,skills}.png`、`textures/particle/{rune_spark,sky_mote}_*.png`、`particles/*.json`、`sounds/rpg/skill/*.ogg` | `gen_skill_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。アイコンのラフ 3 案と比較画像は `docs/review/skill_icons_roughs/`、採用は `ADOPTED`。`--roughs` なら docs にだけ書く。画面の配置を変えたら `SkillScreen.java` と値をそろえる |
| 共鳴の炉の画面: `textures/gui/forge/forge.png` (左右の板・つまみ・印・候補の札、2 倍密度) | `gen_forge_assets.py` | 同じ物ができる。画面の配置を変えたら `ResonanceForgeScreen.java` と値をそろえる |
| 語り部・鯨追い: `geo/entity/{storyteller,whale_chaser}.geo.json`、`textures/entity/{storyteller,whale_chaser}.png`、`animations/entity/{storyteller,whale_chaser}.animation.json`、顔 `textures/gui/npc/face/*.png` | `rough/npc.py --install` (画面の見本は `rough/npc_ui_mock.py`) | ラフは `docs/review/npc_roughs/`。`--install` なしなら docs にだけ書く |
| 会話とクエスト: `data/skyabyss/{dialogue,quest}/*.json`、`assets/skyabyss_npc/lang/*.json`、`docs/design/07_npc_quest.md` の表 | `gen_npc_content.py` | 文と構造の正本はこのスクリプト。JSON や lang を手で直したら、こちらにも反映する |
| 1章の空 雲の目: `textures/environment/sky_eye/{sclera,iris,lid,lid_glow,patch}.png` | `rough/sky_eye.py --install` | ラフ 3 案と夜空の比較は `docs/review/sky_eye_roughs/`、採用は `CHOSEN` (今は b 月蝕の瞳)。`--install` なしなら docs にだけ書く。描画の割合を変えたら `CloudEyeRenderer.java` と値をそろえる |
| `sounds/sky_ch1/*.ogg` (削られた山頂・雲の目の音) | `gen_chapter_sky_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 巨神での移動: `textures/item/stone_whale_call.png`、`textures/block/titan_waymark_{front,side,base,cap}.png`、`textures/gui/travel/chart.png`、`sounds/titan/travel/*.ogg` | `gen_titan_travel_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。ラフ 3 案と比較画像は `docs/review/titan_travel_roughs/`、採用は `ADOPTED_CALL` / `ADOPTED_MARK`。`--roughs` なら docs にだけ書く。画面の配置を変えたら `TravelChartScreen.java` と値をそろえる |
| 言伝ての板: `models/block/message_board.json`、`models/item/message_board.json`、`blockstates/message_board.json`、`textures/block/message_board_*.png` (32x32) | `gen_message_board.py` | 採用は既定で A。`--option B` などで別案に差し替えられる。ラフ 3 案と比較画像は `docs/review/board_roughs/`。`--roughs-only` なら docs にだけ書く |
| 潮殻の装備 (三叉槍・盾・大鋏・銛): `models/item/{tide_shell_trident,coral_shield,coral_shield_blocking,tidecaller_claw,coral_harpoon}.json`、`textures/item/3d/*.png`、アイコン `textures/item/{tide_shell_trident,coral_shield,tidecaller_claw,coral_harpoon,tide_chime_charm,moontide_pearl,tide_shell_core}.png` と護符・真珠・心核の `models/item/*.json` | `install_tide_gear.py` (ラフは `rough/tide_gear.py`、3 案と比較画像は `docs/review/tide_gear_roughs/`。採用は全部 A。`--option <品>=<a\|b\|c>` で差し替え、`--from-sources` で `tools/sources/tide_gear/` から入れ直す) | 同じ物ができる。盾の持ち方 (`SHIELD_DISPLAY`) はバニラの盾の値 (03 の 11.6節) |
| 余燼の装備 (戦斧・大鎚・火掻き): `models/item/{ember_war_axe,smith_hammer,thrall_poker}.json`、`textures/item/3d/*.png`、アイコン `textures/item/{ember_war_axe,smith_hammer,thrall_poker,ember_mantle,kindling_charm,ember_lantern,ember_horn,ember_core,ember_shard,spark_dust,cinder_husk}.png` と、アイコンだけの品の `models/item/*.json` | `gen_ember_items.py` (ラフは `rough/ember_gear.py`、3 案と比較画像は `docs/review/ember_gear_roughs/`。採用は全部 A。`--option <品>=<a\|b\|c>` で差し替え、`--from-sources` で `tools/sources/ember_gear/` から入れ直す) | 同じ物ができる。大鎚の持ち方 (`HAMMER_DISPLAY`) は目分量の最初の値 (実機で確認すること) |
| 潮殻の鎧: `geo/armor/tide_shell_armor.geo.json`、`textures/armor/tide_shell_armor{,_glowmask}.png`、`textures/models/armor/tide_shell_layer_{1,2}.png`、`textures/item/tide_shell_{helmet,chestplate,leggings,boots}.png`、`models/item/tide_shell_*.json` | `install_tide_armor.py` (ラフは `rough/tide_armor.py`、採用は B。`--option a\|b\|c`、`--from-sources` で `tools/sources/tide_armor/` から) | 同じ物ができる |
| 雷翼の装備 (弓・爪・鉤槍と、アイコンだけの品 7 つ): `models/item/{thunder_wing_bow,thunder_wing_bow_pulling_{0,1,2},raiju_claw,warden_glaive,storm_charm,storm_anklet,storm_eye_feather,thunder_wing_shard,thunder_wing_core,cloudgap_fruit,moth_dust}.json`、`textures/item/3d/*.png`、`textures/item/*.png`、`textures/mob_effect/static_ward.png` | `install_storm_gear.py` (ラフは `rough/storm_items.py`、3 案と比較画像は `docs/review/storm_gear_roughs/`。採用は 03 の 12.4節。`--option <品>=<a\|b\|c>`、`--from-sources` で `tools/sources/storm_gear/` から) | 同じ物ができる。弓の持ち方はバニラの `item/bow.json` の値 |
| 雷翼の羽衣: `geo/armor/thunder_wing_armor.geo.json`、`textures/armor/thunder_wing_armor{,_glowmask}.png`、`animations/armor/thunder_wing_armor.animation.json`、`textures/models/armor/thunder_wing_layer_{1,2}.png`、`textures/item/thunder_wing_{helmet,chestplate,leggings,boots}.png`、`models/item/thunder_wing_*.json` | `install_storm_armor.py` (ラフは `rough/storm_armor.py`、採用は A。`--option a\|b\|c`、`--from-sources` で `tools/sources/storm_armor/` から) | 同じ物ができる |
| 余燼の鎧: `geo/armor/ember_armor.geo.json`、`textures/armor/ember_armor{,_glowmask}.png`、`textures/models/armor/ember_layer_{1,2}.png`、`textures/item/ember_{helmet,chestplate,leggings,boots}.png`、`animations/armor/ember_armor.animation.json`、`models/item/ember_{helmet,chestplate,leggings,boots}.json` | `install_ember_armor.py` (ラフは `rough/ember_armor.py`、採用は A 火山の背。`--option a\|b\|c`、`--from-sources` で `tools/sources/ember_armor/` から) | 同じ物ができる |

`rough/boss_v4.py`、`rough/sentinel_v4.py`、`rough/voxel.py`、`rough/build_gallery.py` は `src/` を読むだけで書かない。
`rough/` のそのほか (`armor*.py`、`boss.py`、`mobs_*.py`、`stone_whale*.py`、`weapons*.py`、`captain_v2.py` など) は、片付けた `docs/roughs/` にしか書かない。履歴として残している (ラフの中身は git の 7b69ce9 以前にある)。

## 作り直しの元データ (`tools/sources/`)

ラフのうち、生成スクリプトが入力に使う物だけをここに残す。

| 場所 | 中身 | 使うスクリプト |
|---|---|---|
| `sources/weapons/<item>/voxels.npz` | 武器 3 種の採用案 (B) の立体 | `gen_weapon_models_v2.py` |
| `sources/weapons/<item>/old_icon.png` | 古い 16x16 アイコン (`SKYABYSS_COPY_OLD_ICONS=1` のときだけ使う) | `gen_weapon_models_v2.py` |
| `sources/heart/phaseN.{geo.json,tex.png,glow.png}` | 心臓 3 段階の採用案 (A) | `install_heart_v4.py` |

確認用の絵 (`install_*`、`gen_weapon_models_v2.py` が書く) は `docs/review/previews/` に出る。git には入れない。

## ラフの片付け

採用が決まった要素のラフは、その都度片付ける。残すのは次の物だけ。

- 生成スクリプトが入力に使う物 (`tools/sources/` へ移す)
- ユーザーがまだ選んでいない案 (`docs/review/*_roughs/`)。選ばれたら、比較画像 1 枚と選定理由 (書類) だけ残し、ほかは消す

下の `_roughs/` を書くスクリプトは、どれも `docs/review` のラフを入力に読まない (`--install` も、その場で作った案を書き出す)。消したラフはスクリプトを動かせば作り直せるので、`tools/sources/` へ移した物はない。

| 日付 | フォルダ (`docs/review/`) | 残した | 消した |
|---|---|---|---|
| 2026-10-17 | `watcher_roughs/` (E、00 の 8) | `v2/watcher_v2_roughs.png` (v1 の B と D・E・F を並べた比較) | v1 の `a/`〜`c/`・`watcher_roughs.png`・`watcher_night.png`・`report.txt`、v2 の `d/`〜`f/`・`watcher_v2_night.png`・`report.txt` |
| 2026-10-17 | `npc_roughs/` (語り部 A改・鯨追い A、00 の 9) | `storyteller_roughs.png`、`whale_chaser_roughs.png` (NPC ごとの比較)、`README.md` (選定理由) | `storyteller/`・`whale_chaser/` の各案、`faces.png`、`ui_mock.png`、`report.txt` |
| 2026-10-17 | `lore_book_roughs/` (b、00 の 10) | `compare.png` | `a/`〜`c/` |
| 2026-10-17 | `skill_icons_roughs/` | `compare.png` | `a/`〜`c/` |
| 2026-10-17 | `sky_eye_roughs/` (b) | `sky_eye_roughs.png` | `a/`〜`c/`、`lid.png`・`lid_glow.png`・`patch.png`、`sky_eye_night.png`、`v2_preview.png` |
| 2026-10-17 | `titan_travel_roughs/` (呼び笛 A・碑) | `compare_call.png`、`compare_waymark.png` (品ごとの比較) | `call_a〜c.png`、`waymark_front_a〜c.png`、`chart_texture.png` |
| 2026-10-17 | `record_screen_roughs/` | `compare.png` | `a/`〜`c/` |
| 2026-10-17 | `accessory_slot_roughs/` (B) | `compare.png` (片付けのときに `a.png`〜`c.png` を横に並べて作った) | `a.png`〜`c.png` |
| 2026-10-17 | `board_roughs/` (A を仮に採用。比べ直しは 00 の 85 の比較画像で) | `compare.png`、`README.md` (選定理由) | `option_A/`〜`option_C/` |
| 2026-10-17 | `tide_shell_roughs/` (本体 B、心臓 B、珊瑚憑き A・磯走り A・泡吹き A、00 の 55) | 比較画像 `titan.png`・`heart.png`・`coral_thrall.png`・`reef_scuttler.png`・`bubble_puffer.png`。選ばれた案のデータ `titan/b/`・`heart/b/`・`heart/bars.png`・`mobs/coral_thrall/a/`・`mobs/coral_spear/`・`mobs/reef_scuttler/a/`・`mobs/bubble_puffer/a/` (心臓と敵の担当が並行で読むので、今は場所を変えない。取り込みが済んだら `tools/sources/` へ移すか消す)。`mobs/bubble_puffer/c/` (月明かりのクラゲ混じり) も残す: 08 の 14.3節で「3 章以降の別の種に回せる」としたため。`install/` は `install_tide_shell.py` の確認用の出力。`install_tide_shell.py` は B 案を `rough/tide_shell_titan.py` から import して作り、ここのファイルは比較の絵 (`titan/b/far.png`、無ければ飛ばす) しか読まない | `titan/a/`・`titan/c/`、`heart/a/`・`heart/c/`、`mobs/coral_thrall/b/`・`c/`、`mobs/reef_scuttler/b/`・`c/`、`mobs/bubble_puffer/b/` |


生成元のないもの (今の `src/` が正本): 番兵 (`bone_sentinel`) と盾、水路の顎 (`waterway_maw`)、甲殻の防具 (`geo/armor`、`textures/armor`)、`textures/item/3d/bone_spike_arrow.png`、`sounds/**/*.ogg`。

## 止めてあるもの (引退)

| スクリプト | 上書きしてしまうもの | 代わり |
|---|---|---|
| `gen_items.py` | `textures/item/*.png` 16 枚と `textures/models/armor/*` | `rough/items_blocks_32.py` |
| `gen_block_textures.py` | `textures/block/*.png` と `sky_key_fragment.png` | `rough/items_blocks_32.py` |
| `gen_mobs.py` | 番兵・射手・苔這い・顎・番兵長の geo / png / glowmask / animation | 上の表 |
| `gen_titan_model.py` | 岩鯨の近景 geo / png / glowmask / animation | `install_stone_whale.py` |
| `gen_heart.py` | 心臓の animation (古い骨名に戻る) | `install_heart_v4.py` |
| `gen_armor_model.py` | 甲殻の防具の geo / png / glowmask | なし (今の src が正本) |
| `gen_weapon_models.py` (v1) | 武器 3 本のモデルを古い押し出しモデルに戻し、途中で止まる | `gen_weapon_models_v2.py` |
| `rough/rib_archer_v3.py` | 射手の geo / animation を v3 に戻す | `rough/rib_archer_v4.py` |
| `rough/captain_v3.py --install` | 番兵長を v3 に戻す (`--install` なしなら docs にだけ書くので動く) | `rough/captain_v4.py --install` |
| `gen_sky_sounds.py`、`gen_titan_sounds.py` | `sounds/sky/*.ogg`、`sounds/titan/*.ogg` | なし (今の src が正本) |

`gen_weapon_models_v2.py` も止めてある。武器 3 本の `models/item/{whalebone_greatsword,captain_pike,waterway_fang}.json` と
パレット `textures/item/3d/*.png` (16x16) の生成元で、`SKYABYSS_ALLOW_REGEN=1` を付ければ今と同じ物ができる。
古い 16x16 アイコンでの上書き (`write_icon`) は、さらに `SKYABYSS_COPY_OLD_ICONS=1` を付けたときだけ行う。

## 自己テスト

`SKYABYSS_SELFTEST=1 ./gradlew runServer` (入口は `src/main/java/jp/revati/skyabyss/SelfTestRunner.java`。18 系統を 1 tick に 1 段ずつ流し、重い段のあとはサーバーが追いつくのを待つので、`max-tick-time` は既定の 60000 のままでよい。ログの `SELFTEST <名前> PASSED / FAILED` と最後の `SELFTEST PASSED / FAILED` を見る。`SELFTEST runner:` の行に段ごとの時間)。所要時間 (2026-10-16): 空の run/ で 146〜169 秒 (うちテスト 120〜143 秒、いちばん重い tick は 1章の空の峰で約 20 秒)、既存のワールド (本体の run/) で 76〜98 秒。
