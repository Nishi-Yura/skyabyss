# tools/

素材を作るスクリプト。どれもリポジトリ直下で `python tools/<名前>.py` のように実行する。
採用済みの素材を古い版で上書きしてしまうスクリプトは止めてある。
どうしても動かすときだけ、環境変数 `SKYABYSS_ALLOW_REGEN=1` を付ける。`gen_items.py` だけは `SKYABYSS_ALLOW_GEN_ITEMS=1`。
経緯は `docs/review/assets_audit.md` の S1。

## 採用版の生成元 (実行してよい)

| 素材 (`src/main/resources/assets/skyabyss/` 以下) | 生成元 | 備考 |
|---|---|---|
| `textures/item/*.png` (32x32)、`textures/block/*.png` | `rough/items_blocks_32.py` | 出力先 (`docs/roughs/`) は片付けた。作り直すときは一時フォルダに書き出し、採用した物だけ src へ手でコピーする |
| 岩鯨: `geo/entity/stone_whale{,_mid,_far}.geo.json`、`textures/entity/stone_whale{,_glowmask}.png`、`animations/entity/stone_whale.animation.json` | `install_stone_whale.py` | 実行しても今の素材と同じ物ができる |
| 潮殻の巨神: `geo/entity/tide_shell{,_mid,_far}.geo.json`、`textures/entity/tide_shell{,_glowmask}.png`、`animations/entity/tide_shell.animation.json`、`sounds/tide/*.ogg` | `install_tide_shell.py` (形の元は `rough/tide_shell_titan.py` の案 B。珊瑚の LOD の塊・塔・段々の庭は install 側で足す)、音は `gen_tide_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。確認用の絵は `docs/review/tide_shell_roughs/install/`。アニメの角度は `install_tide_shell.py` の `ANIM` に意図した向きで書く (GeckoLib の符号はスクリプトが直す) |
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
