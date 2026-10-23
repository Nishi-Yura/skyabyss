# tools/

素材を作るスクリプト。どれもリポジトリ直下で `python tools/<グループ>/<名前>.py` のように実行する。

## フォルダの地図

| フォルダ | 中身 |
|---|---|
| `gen/ch1` … `gen/ch5` | 章ごとの地上・部品・レシピ・文を書く生成スクリプト |
| `gen/abyss` | 深淵の層・敵・文 |
| `gen/items` | 武器・防具・装飾・ブロック・背の形の素材 |
| `gen/mobs` | モブ・NPC・肖像 |
| `gen/sound` | 効果音・声・字幕の生成 |
| `gen/story` | 物語・クエスト・台詞・進捗・伝承の文 |
| `gen/ui` | UI・アイコン・スキルの素材 |
| `gen/world` | 巨神 (背・心臓・ハート)・街・地形・旅の地図の素材 |
| `gen/shots` | 撮影用の台本 (`tools/shots/*.txt`) を書くスクリプト |
| `gen/misc` | どこにも入らない生成スクリプト |
| `install/` | 生成物を `src/main/resources` に取り込むスクリプト (`install_*.py`) |
| `sheets/` | `docs/review` の比べシート・レビューページを作るスクリプト (テンプレート html を含む) |
| `audit/` | 検査 (`check_*.py`、`mesh_audit.py`、`check_text_fit.py` など) |
| `lib/` | ほかのスクリプトが `import` する共有の部品 (`mesh_close`、`whale_bin`、`weapon_display` など) |
| `once/` | 一度だけ走らせた移行スクリプト (`add_*_lang`・`fix_*`)。履歴として残す。もう走らせない |
| `misc/` | 単発の調査・プローブ |
| `dev/` | 開発用 (`rcon.py`、`mc_run.sh`、`quick_check.sh`、`world_template.sh`)。使い方は下の「開発の速い回し方」 |
| `shots/` | 撮影の台本 (`*.txt`) だけ。台本を書くスクリプトは `gen/shots/`、撮った絵を並べる台紙のスクリプトは `sheets/` に置く |
| `abyss/` `capital/` `rough/` `readme/` `structure/` `ui/` `trial_lang/` `sources/` | 既存の作業フォルダ (ラフ・都・構造の検査・入力の素材など) |
| `site/` | サイト用 (別作業) |

フォルダの移動にともなう `sys.path` は、各スクリプトの先頭で他グループを足して解決する。
採用済みの素材を古い版で上書きしてしまうスクリプトは止めてある。
どうしても動かすときだけ、環境変数 `SKYABYSS_ALLOW_REGEN=1` を付ける。`tools/gen/items/gen_items.py` だけは `SKYABYSS_ALLOW_GEN_ITEMS=1`。
経緯は `docs/review/assets_audit.md` の S1。

## ゲームを立ち上げる担当の決まり (並列で動かすとき)

マイクラをいくつ同時に立ち上げてもよい。ただし、ほかの担当のウィンドウに入力が入らないよう、次を守る。

- `tools/gen/story/gen_opening_text.py` は再実行しない（書き出したあとの手直しの方が新しい。走らせても何も書かない。書き直すときだけ `SKYABYSS_FORCE_GEN=1`）。`tools/gen/story/gen_free_quests.py`・`tools/gen/story/gen_story_quests.py` は、いまは手直しを取り込んであり (下の「生成スクリプトとコミット済みデータの同期」)、順番を守れば再実行してもデータは変わらない。
- **OS のキー送り・マウス操作・ウィンドウのフォーカス操作は禁止** (PowerShell の SendKeys、AppActivate、SetForegroundWindow、nircmd など全部)。前面のウィンドウは誰のものか分からない
- **画面の確認は台本で撮る** (下の「台本で撮るスクショ」)。ゲームの中から撮るので、ウィンドウが裏にあっても撮れる。最小化だけはしない
- **建築の抜け・不具合はデータで確かめる** (下の「構造をデータで確かめる」)。試遊 (歩いて回る) での確認はしない
- **コマンドを打つときは** 台本の `cmd`、または専用サーバー (`./gradlew runServer`) の RCON か自己テスト。クライアントのチャット欄へ打ち込まない
- 専用サーバーを並べて立てるときは、worktree ごとに `run/server.properties` の `server-port` (と `rcon.port`) を別の番号にする (同じ番号だと起動時にポートを取れない)
- 終わったら java を残さない。台本は最後に自分でゲームを閉じる (止まっても `timeout` 秒で閉じる)

## 低ポリメッシュのモデルを入れる (`tools/install/install_lowpoly.py`)

```
python tools/install/install_lowpoly.py bone_sentinel --gear docs/review/lowpoly_roughs/bone_sentinel_shield_mesh.json:docs/review/lowpoly_roughs/bone_sentinel_shield_atlas.png:bone_sentinel_shield:socket_off
```

`docs/review/lowpoly_roughs/<名前>_lowpoly_mesh.json` / `_lowpoly_atlas.png` / `_lowpoly_glowmask.png` を `assets/skyabyss/lowpoly/` と `textures/lowpoly/` に入れ、骨だけの geo (`geo/entity/<名前>_lowpoly.geo.json`) と、骨ごとの箱の debug geo を書く。元の geo・アニメは触らない（`LowPoly.enabled = false` で元に戻る）。盾などを別メッシュにするときは `--gear`、メッシュの中の骨を切り出すときは `--split 骨:受け口:名前`。台本の `lowpoly on|off|debug|nodebug` で切り替えて撮る (`tools/shots/lowpoly_basic.txt` `lowpoly_action.txt` `lowpoly_perf.txt`)。仕様は `docs/design/15_high_poly.md` の 9 章。骨の休めの回転・親・pivot は元の geo から写す（メッシュ JSON に書いてあればそちら）。4 体 (番兵長・苔這い・射手・心臓) は `python tools/install/install_lowpoly_five.py` で一度に入る。確認の台本は `python tools/gen/shots/gen_lowpoly_five_shots.py` が `tools/shots/lp_<名>.txt` に書く。雷翼の背の 5 体と珊瑚憑きは `python tools/install/install_lowpoly_storm.py`、確認の台本は `python tools/gen/shots/gen_lowpoly_storm_shots.py`、比較シートは `python tools/sheets/lowpoly_storm_sheets.py`。余燼の背の敵・心臓・鍛え手、語り部・イサナ・見る者は `python tools/install/install_lowpoly_ember_npc.py`、確認の台本は `python tools/gen/shots/gen_lowpoly_ember_npc_shots.py`、比較シートは `python tools/sheets/lowpoly_ember_npc_sheets.py` (15 の 9.6)。

## モデルの透けの検査と修理・心臓の演出

- **水密性の検査 (`python tools/audit/mesh_audit.py`)**: 低ポリのメッシュ (`assets/skyabyss/lowpoly/**`) を骨ごとに、開いた辺・向きの食い違い・逆法線・つぶれた三角形・裏面透け (外からの光線の最初の当たりが裏面)・親子の付け根 (埋まりが足りない・離れる。アニメの最大回転で曲げて見る) を数え、`docs/review/mesh_audit_report.md` に悪い順で書く。キューブの geo の透けとブロックモデルの欠けた面・宙に浮く部品も見る。**ゲートは `python tools/audit/mesh_audit.py --check`** (数秒。開いた辺・向き違い・逆法線・ブロックの欠けが 1 つでもあれば終了コード 1。`--full` で裏面透け (全体の 0.2% 超で失敗)、`--joints` で付け根も失敗にする)。`--selftest` は検査器自身の自己テストと、入っている全メッシュの `--check`。
- **水密にする (`python tools/lib/mesh_close.py --all`)**: 生成スクリプトは見える面だけを書くので、書き出したメッシュは縁が開いている。`tools/lib/mesh_close.py` が、開いた縁の輪を蓋で塞ぎ (蓋は内側へ少し引っ込めた扇。貼りつく面と同じ平面で z が競らない)、向きの食い違い・逆法線を直し、子の骨の回転軸 (pivot) に袖 (`collar`: 根元の輪の断面のまま細めた筒を親の中へ延ばす。輪がない閉じた部品は親へ向いた端の面を取り `extrude_root` で押し出す) を足す。`install_lowpoly*.py` ・`tools/install/install_armor_lowpoly.py` ・`tools/install/install_whale_road_assets.py` は書き出すときに自動で通す (書き出し直しても水密のまま)。巨神は蓋の頂点の始まり (`cap_vertex_start`) を JSON に残し、読み込み側 (`LowPolyMesh`) が蓋の面を暗く出す。ブロックモデルの外へ向いた欠け面は `python tools/once/fix_block_faces.py` が足す。
- 関節の見た目の確認 (休め / 動作の最大付近。モブを目の前の決まった位置に出し、体の高さに合わせて構える): `python -I tools/gen/shots/gen_joints_shots.py` → `./gradlew runShots --no-daemon -Pscript=tools/shots/joints.txt` → `python -I tools/sheets/joints_sheet.py` で `docs/review/previews/joints_sheet.png`。付け根が抜ける・浅い骨は `mesh_close.fix_joints` が付け根の頂点を親の中へ沈めて直す (新しい面は足さない)。
- 直す前 / 直した後の接写の比べ表: `python tools/sheets/watertight_sheet.py` → `docs/review/previews/watertight_sheet.png` (関節をアニメの最大回転まで曲げ、裏面が見える所を紫で塗る)
- `python tools/lib/fix_geo_face_sides.py --check` : 全 geo の「透けて中が見える穴」を数える。`--apply --fill` で直す (面の左右の名前を GeckoLib に合わせ、元から抜けた面を塞ぐ。二度は直さない)。経緯は `docs/design/15_high_poly.md` の 5 章。書き出し直したら、そのあとにもう一度流す。見た目の比べは `tools/shots/geo_sides.txt`
- `python tools/install/install_heart_cinematics.py` : 四つの心臓の登場 (intro) と倒れる (death) のアニメーションを各 `animation.json` に足す。`install_*_heart.py` で書き出し直したら、そのあとにもう一度流す。長さは `HeartBoss` の `INTRO_TICKS` `DEATH_TICKS` と合わせる

- スキルの自由度 (`docs/design/27` の 17 節): `tools/shots/skill_freedom.txt` (天職の前の木・木のセット・熟練の刻印・構え/連鎖/タグ/鍵石のページ・試着。開発コマンド `skyabyss skill stance|keystone|mastery|chain|rebirth`) → `python tools/sheets/skill_freedom_sheet.py` で `docs/review/previews/skill_freedom_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=skills ./gradlew runServer`
- 変異・橋・紋の盤・前世の技・淵 (`docs/design/27` の 18 節): `python tools/gen/ui/gen_deep_skill_assets.py` が `icons2.png` を、`python tools/gen/ui/gen_deep_skill_lang.py` が lang (ja/en) を書く。撮影は `tools/shots/deep_skills.txt` (橋の輪・淵・前世の技・変異と紋のページ・変異の見た目。開発コマンド `skyabyss dev rebirth <n>`・`skyabyss skill glyph|socket|mutate`) → `python tools/sheets/deep_skills_sheet.py` で `docs/review/previews/deep_skills_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=skills ./gradlew runServer` (`DeepSkillsSelfTest`)
- 備えの三灯 (`docs/design/11` の 9 章): `tools/shots/readiness.txt` (控え帳の巨神の頁の「備え」の行、乗り込み口の一言、前室の囁き。`/skyabyss dev readiness prompt|whisper <巨神>` を使う)。絵は `docs/review/previews/readiness_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=readiness ./gradlew runServer`
- 二章の地上の敵 (`docs/design/21` の 4.1 の P12。潮だまり貝・砂潜り・宿借り・潮の番人): ラフ 3 案は `python -I tools/rough/lowpoly/ch2/<id>.py` (`tidepool_clam` `sand_burrower` `drift_hermit` `shore_warden`。共通は `ch2/ck2.py` = 一章の `ch1/ck.py` の出力先と色を替えたもの。番人は珊瑚憑きの `coral_thrall2/opt_a` に部品を足す) → `docs/review/ch2_roughs/<id>/{a,b,c}/` とシート `<id>.png`・`<id>_manifest.json`。選んだ案の取り込みは `python -I tools/gen/ch2/gen_ch2_ground_mobs.py` (案は先頭の `CHOICE`。メッシュ・アトラス・番人の銛 `shore_harpoon`・アニメ・`sounds.json` の末尾の塊 (既存の音の出来事の別名)・文 `assets/skyabyss_ch2mob/lang`・スポーンエッグの絵)。撮影は `./gradlew runShots -Pscript=tools/shots/ch2_mobs.txt` (待機・予告・技の姿と 4 体の並び。選んだ絵は `docs/review/ch2_roughs/ingame/`)。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="ch2 mobs" ./gradlew runServer`
- 一章の地上の新しい場所 (`docs/design/17` の 1.5 の P6): `python tools/gen/ch1/gen_ch1_sites.py` が部品 (石標・祠・骨の集め場・荷車の傍らの道・割れ殻の洞・歌の間) を `data/skyabyss/opening/` に、`python tools/gen/ch1/gen_ch1_prep.py` が備えの頼み 3 本・会話の入口・伝承 3 項目・文 (`assets/skyabyss_ch1/lang`)・素材 4 つの仮の絵を書く。撮影は `tools/shots/ch1_sites.txt` (平らな世界へ開発コマンド `skyabyss dev ch1 place|chamber` で直接置く。洞と歌の間は石の台地の上) → `python tools/sheets/ch1_sites_sheet.py` で `docs/review/previews/ch1_sites_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=ch1 ./gradlew runServer` の `ch1 p6`
- 三章の地上 (`docs/design/34` の 4.4.1 C3): `PYTHONIOENCODING=utf-8 python -I tools/gen/ch3/gen_ch3_ground.py` が部品 (避雷の塔の跡 `ch3_rod_tower`・風の道の旗・焼けた見張り塔・雷拾いの原・風読みの崖) を `data/skyabyss/opening/` に、支度の段 3 本とサブ (`quest/ch3/`)・会話の足し書き (`dialogue/ch3/*_ground.json`)・伝承 4 (`lore/entry/ch3/`)・文 (`assets/skyabyss_ch3/lang` に混ぜる) を書く。進捗 `scorched_watch`・`storm_trail` は `tools/gen/story/gen_advancements.py` (`-I` で動かすときは `sys.path` に tools を足して runpy で)。撮影は `./gradlew runShots -Pscript=tools/shots/ch3_ground.txt -PshotsName=ch3_ground` (平らな世界へ `skyabyss dev ch1 place` で置く。帯は `/skyabyss quest start @s "ch3/..."`)。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="ch3 ground" ./gradlew runServer`
- 四章の地上 (`docs/design/34` の 4.5.1 C4、21 の 6.1): `PYTHONIOENCODING=utf-8 python -I tools/gen/ch4/gen_ch4_ground.py` が部品 (灰の道の標・消えた炉の坑・温かい岩の群れ・煤の窯の跡・灰降りの高台) を `data/skyabyss/opening/ch4_*.json` に、支度の段 2 本とサブ・日替わり (`quest/ch4/`・`quest/daily_mite_clear.json`)・会話 (`dialogue/ch4/*_ground.json`・`artisan.json`)・伝承 4 (`lore/entry/ch4/`)・レシピ 2・文 (`assets/skyabyss_ch4/lang` に混ぜる) を書き、メイン `main_ember` の「乗る」の段と `main_key` の二人の答え (語り部・イサナの物語の会話の節) をデータへ直接足す (`story_patch`。何度流しても同じ)。進捗 `dead_forge_pit`・`ember_trail` は `tools/gen/story/gen_advancements.py`。敵 4 種のラフは `python -I tools/rough/lowpoly/ch4/<id>.py` (`cinder_mite`・`ash_dragger`) と `tools/rough/lowpoly/ch4/thrall_variants.py` (灰被りの変種 `ash_husk`・`elder_thrall`)、小物は `icons_ch4.py`、選定ページは `manifest_ch4.py` → `python -I tools/rough/build_gallery.py docs/review/ch4_roughs/ch4_manifest.json docs/review _gallery_ch4`。選んだ案の取り込みは `python -I tools/gen/ch4/gen_ch4_ground_mobs.py` (`CHOICE`・`ITEM_CHOICE`。音は `assets/skyabyss_ch4/sounds.json`)。撮影は `./gradlew runShots -Pscript=tools/shots/ch4_ground.txt -PshotsName=ch4_ground` (開発コマンド `skyabyss dev ch4 warm|crack|wave|curl`)。自己テストは `SKYABYSS_SELFTEST_ONLY="chapter grounds,ch4"`。灰歩きの旅装のラフは `python -I tools/rough/lowpoly/ch4/armor_ch4.py`（`docs/review/ch4_roughs/items/armor_ch4.png`）、灰焼きのパンと煤の刃の絵は `icons_ch4.py`。旅装・パンのレシピ・文・絵の取り込みは `PYTHONIOENCODING=utf-8 python -I tools/gen/ch4/gen_ch4_gear.py`（`CHOICE`）。四章の小要素 3 つ（炉跡・観測点・岩棚）の部品と語りの文は `python tools/gen/ch4/gen_ch4_content.py`（余燼の lang は `__ember.H` と伝承の行だけを書き直す）
- 二章の地上 (`docs/design/34` の 4.3・C2、21 の 4.1): `python tools/gen/ch2/gen_ch2_ground.py` が部品 (沈んだ塩の蔵・塩の道の標・潮だまりの浜・漂着の筏の跡・引き潮の観測の杭) を `data/skyabyss/opening/ch2_*.json` に、支度の段 `ch2/salt_road`・`ch2/ebb_boat` とサブ・日替わり・会話 (`dialogue/ch2/*_trail.json`・`artisan.json`)・伝承 4 項目・文 (`assets/skyabyss_ch2/lang` の末尾にひとまとまり。既存の行はそのまま) を書く。何度でも動かせる。撮影は `tools/shots/ch2_ground.txt` (平らな世界へ開発コマンド `skyabyss dev ch2 vault|place|mark|wreck|tide wet|dry|door` で置く)。自己テストは `SKYABYSS_SELFTEST_ONLY="ch2 ground"`
- 三章の地上の敵 (`docs/design/21` の 5.1 の P16。帯電毛玉・峰の雛・雷拾い・焼けた羽守): ラフ 3 案は `python -I tools/rough/lowpoly/ch3/<id>.py` (`static_puff` `peak_fledgling` `lightning_gleaner` `scorched_warden`。共通は `ch3/ck3.py`。羽守は `feather_warden/opt_a` に色と部品を足す) → `docs/review/ch3_roughs/<id>/{a,b,c}/` とシート `<id>.png`。取り込みは `python -I tools/gen/ch3/gen_ch3_ground_mobs.py` (案は `CHOICE`。メッシュ・アトラス・鉤槍 `scorched_glaive`・アニメ・`sounds.json` の末尾の塊・文 `assets/skyabyss_ch3mob/lang`)。撮影は `./gradlew runShots -Pscript=tools/shots/ch3_extra.txt -PshotsName=ch3_extra` (敵の姿と技・品の額・旅装。絵は `docs/review/ch3_extra/`)。自己テストは `SKYABYSS_SELFTEST_ONLY="ch3 mobs"`
- 三章の備えの品 (`docs/design/21` の 1.2「三章の分」・5.1): `python -I tools/gen/ch3/gen_ch3_gear.py` がレシピ 9 本・職人の会話 `dialogue/ch3/artisan_gear.json`・lang (`assets/skyabyss_ch3gear/lang`。丸ごと書く) を、`python -I tools/rough/lowpoly/ch3/armor_ch3.py` が旅装のラフ 3 案、`python -I tools/rough/lowpoly/ch3/icons_ch3.py` が小物 8 つの 3 案ずつと効き目の絵 2 枚を書く。`python -I tools/install/install_ch3_gear_assets.py` が `CHOICE` の案を assets に入れ、3 案の元絵を `tools/sources/<品>/` に残す。選定ページ用の一覧は `docs/review/ch3_roughs/choices.md`。検査は `SKYABYSS_SELFTEST_ONLY="ch3 gear"`
- 二章の備えの品 (`docs/design/21` の 1.2「二章の分」・4.1): `python -I tools/gen/ch2/gen_ch2_gear.py` がレシピ 8 本 (潮渡りの旅装 4・塩の砥石・潮の塩漬け・真珠の粉薬・潮の刻みの砂時計) と lang (`assets/skyabyss_ch2gear/lang`。丸ごと書く) を、`python -I tools/rough/lowpoly/ch2/armor_ch2.py` が旅装のラフ 3 案 (`docs/review/ch2_roughs/items/armor_ch2.png`)、`python -I tools/rough/lowpoly/ch2/icons_ch2.py` が小物 8 つの 3 案ずつと塩の刃の効き目の絵 (`docs/review/ch2_roughs/items/icons_ch2.png`) を書く。`python -I tools/install/install_ch2_gear_assets.py` が `CHOICE` の案を assets に入れ (旅装のメッシュは水密に閉じる)、3 案の元絵を `tools/sources/<品>/` に残す。検査は `SKYABYSS_SELFTEST_ONLY="ch2 gear"`
- 使い心地の磨き (rpg_brushup A4 / A8 / A11): `tools/shots/ux_polish.txt` (仲間の札の記号・自分の墓の標の札と矢印・石の「誰が鳴らしたか」の知らせ・控え帳の試練の石の行。見本は `/skyabyss dev ux stele|grave|party|clear` で出す)。絵は `python tools/sheets/ux_polish_sheet.py` で `docs/review/previews/ux_polish_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="ux polish" ./gradlew runServer` (背中の復活の守り・誰が鳴らしたか・墓の標のパケット)
- 巨神の眠り (心臓を止めたあと。`docs/design/20_cinematics.md` の 3.4) の絵: `tools/shots/titan_end_whale.txt`・`titan_end_ember.txt`・`titan_end_storm.txt` (平らな世界。巨神を眠らせて場面を流し、見せ場を撮る)、`titan_end_tide.txt` (普通の世界の海。座標は `titan_end_scout.txt` が `locate` で探す)、乗り込み口の光 `titan_end_glow.txt`、到着の台と登りの階段 `titan_end_arrival.txt`。絵は `docs/review/previews/titan_end_*.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="titan end" ./gradlew runServer` (server.properties の `max-tick-time=-1` を付けると、重い読み込みでウォッチドッグに止められない)
- はじまりの都の施設 (`docs/design/35` の 2.1・6.6): 設計図は `python -I tools/capital/town.py` (施設の建物と中身は `tools/capital/facilities.py`、家具の型と町のクエストの家の住み手・施設の上の階は `interiors.py`、古井戸の下の迷路と酒場の地下の隠し通路は `backalley.py`)、検査は `python -I tools/capital/audit.py` (戸の前と内側・係の人 `npc_*`・宿の寝台・クエストの家・梯子・歩いて届くか・建物どうしの間) と `check_states.py`、見た目の違和感 (浮いた塊・支えのない付き物・外の通りで跳ばないと上がれない段・外の段の行き止まり) は `python -I tools/capital/audit_odd.py [-q]`。絵は `tools/shots/capital_rebuild.txt` (上から・四つの大通り・施設の中と係の人。台本は移るたびに `skyabyss facility place` で係の人を立たせる。設計図を変えたら `run/shots/capital_rebuild/saves` を消す)、選んだ絵は `docs/review/town_roughs/rebuild_a/`。作り込み (宿の廊下・クエストの家 11 の一階と二階・施設の上の階・井戸の下・地下の隠し通路・訓練場の門) は `tools/shots/capital_rebuild2.txt` → `docs/review/town_roughs/rebuild_a2/`。古井戸の下は、検査 (`audit.py`) が井戸の底 `well_down` からも歩く (格子はレバーで開く)
- 雷翼の背の拡大 (`docs/design/26` の 9 章。版 20): `tools/shots/storm_scale.txt` (到着・羽軸の道・広場・門・広間・肩の階段・風の谷・島・書庫・肘・全景 (室内の場所は消えた: 肋の間・回路・炉・巣・冠の間・眼の間。台本の表から外す)。場所の表は `python tools/gen/shots/gen_storm_scale_shots.py` が台本へ書く) → `python tools/sheets/storm_scale_sheet.py` で `docs/review/previews/storm_scale_sheet.png`。鳥の形は `python tools/gen/items/gen_back_shape.py bird` (既定が版 20 の倍率)。建てた形を絵なしで見るには `SKYABYSS_STORM_SPEC="plan,y,x1,x2,z1,z2;secz,z,x1,x2,y1,y2;secx,x,z1,z2,y1,y2" SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="storm back" ./gradlew runServer` (`run/storm_back_dump/`)。嵐だけの建築の監査は `SKYABYSS_AUDIT_ONLY=storm SKYABYSS_SELFTEST_ONLY="build audit"`。歩く道の灯りは `/skyabyss dev routelight storm` (rcon でよい。`run/route_lamps_storm.json` を `data/skyabyss/storm_back/route_lamps.json` へ写す)
- 雷翼の背の任意の翼 (`docs/design/26` の 9.5。版 21): `python tools/gen/shots/gen_storm_scale2_shots.py` が台本 `tools/shots/storm_scale2.txt` を書く (関所・背の広場・二つの塔・外廊・翼の先・庭・広間の天井と壁。場所の表はそちら) → `./gradlew runShots -Pscript=tools/shots/storm_scale2.txt` → `python tools/sheets/storm_scale2_sheet.py` で `docs/review/previews/storm_scale2_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="storm back,storm scan,build audit,trial hub,lang" SKYABYSS_AUDIT_ONLY=storm ./gradlew runServer` (`StormBackSelfTest.checkExtra`)
- 岩鯨の背の作り直し (`docs/design/26` の 8.5。版 18): `tools/shots/whale_fix.txt` (鐘楼・西の三つの谷・鼓動の間・封印の回廊・胸びれの先・書庫。東の翼の広間は消えた) → `python -I tools/sheets/whale_fix_sheet.py` で `docs/review/previews/whale_fix_sheet.png`。建てた形を絵なしで見るには、環境変数 `SKYABYSS_WHALE_DUMP=whale_dump.txt` を付けて `SKYABYSS_SELFTEST_ONLY="titan back (build),whale scale"` を流し、`run/whale_dump.txt.spec` (`plan y x1 x2 z1 z2`・`secz`・`secx`・`light y …`・`widths y`) の図を `run/whale_dump.txt` に書かせる。
- 潮殻の背の拡大 (`docs/design/26` の 9 章。版 20): `python tools/gen/items/gen_back_shape.py turtle` が体 (`TURTLE_H`・`TURTLE_V` で倍率。既定 1.6・1.3) を作り、`tools/shots/tide_scale.txt` (浜・広間・庭・潮見の間・首の廊下・干潟・ひれの道・息の間・冠の回廊) → `python tools/sheets/tide_scale_sheet.py` で `docs/review/previews/tide_scale_sheet.png`。文は `python tools/once/add_tide_scale_lang.py` が ja・en に足す (碑・宝・ひれ・目的の板)。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="tide back,tide fins,breath,pearl keeper" ./gradlew runServer`。歩く道の灯りの測り直しは、島を建てた専用サーバーへ rcon で `skyabyss dev routelight tide` を流し、サーバーのフォルダの `route_lamps_tide.json` を `data/skyabyss/tide_back/route_lamps.json` へ写す。
- 余燼の背の拡大 (`docs/design/26` の 11 章。版 20): 体は `python tools/gen/items/gen_ember_back_body.py --cache --preview` (環境変数 `WH`・`WV`・`ZSHIFT`・`EMBER_OUT`。既定 x1.6・x1.3)。撮影は `tools/shots/ember_scale.txt` (口・尾根の広間・広間・洞・三つの試練・背へ出る洞。世界は `run/shots/ember_scale/saves` を消すと建て直す) → `python -I tools/sheets/ember_scale_sheet.py` で `docs/review/previews/ember_scale_sheet.png`。建てた形を絵なしで見るには、環境変数 `SKYABYSS_EMBER_DUMP=dump.txt` と `run/dump.txt.spec` (行 `plan y x1 x2 z1 z2`・`secx x y1 y2 z1 z2`・`secz z y1 y2 x1 x2`) を用意して `SKYABYSS_SELFTEST_ONLY="ember back (load)"` を流す。歩く道の灯りは、`route_lamps.json` を `[]` にして建て直し、専用サーバーで `python tools/dev/rcon.py --port <rcon> "skyabyss dev routelight ember"` を流し、サーバーの `route_lamps_ember.json` を `data/skyabyss/ember_back/route_lamps.json` へ写す。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="build audit,ember,ash smith,trial,lang" ./gradlew runServer`

- 章の跡地 (`docs/design/33` の 3 章。W2): 撮影は `tools/shots/chapter_grounds.txt` (四つの跡地を整え、`skyabyss dev grounds shrine <章>` で祠も置き場に建てて、上と地面から撮る。形を撮り直すときは `run/shots/chapter_grounds/saves` を消す)。選んだ絵は `docs/review/world_roughs/grounds_w2/`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="chapter grounds" ./gradlew runServer`
- 心臓の予告（赤だけ）と領域をまとめて撮る: `tools/shots/telegraph_red.txt`（`telegraph_heart`・`telegraph_tide_heart`・`telegraph_storm`・`telegraph_ember_heart`・`heart_domains` をつないだもの。絵の名前の頭に元の台本の名前）
- 旅の地図 (M。`docs/design/33` の 4 章): `python tools/gen/world/gen_travel_map_assets.py` が印・地・海図の盤 (`textures/gui/map/`) を書く。撮影は `tools/shots/travel_map.txt` (3 案 x 縮尺 2 段と子の海図、最後に本物の M) → 絵を `docs/review/map_roughs/` へ。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="travel map" ./gradlew runServer`
- 心臓の領域と技の名前 (`docs/design/02` の 5.6・5.7、`18` の 6.1): `python tools/gen/sound/gen_heart_domain_stings.py` が宣言の音 4 本 (`sounds/mobs/domain_*.ogg`)・`sounds.json`・字幕を書く。撮影は `tools/shots/heart_domains.txt` (四つの心臓を一体ずつ出し、技の名前・領域の題字と天の声・領域のあいだを撮る。`/skyabyss dev force <心臓> DOMAIN|LEAD|CUTOFF|CHASE`) → `python tools/sheets/heart_domains_sheet.py` で `docs/review/previews/heart_domains_sheet.png`。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="heart bots" ./gradlew runServer` (回る人形は負け、予告を読む人形は生き残る、領域の流れ・砕く)

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
| `open <名前>` | mod の画面を開く: `lore` `quests` `growth` `skills` `party` `inventory` `grave`（語り部の墓の一覧の見本）、`travel_map_demo` / `travel_map_voyage_demo`（旅の地図の見本の印。子の海図を重ねた姿）、または引数なしで作れる画面のクラスの完全な名前 |
| `key <キーの名前>` | そのキーを押したことにする (KeyMapping。OS の入力ではない)。サーバーから中身を受け取る画面に使う。例 `key key.skyabyss_record.open` (控え帳) |
| `close` | 画面を閉じる |
| `mapstyle slate\|parchment\|ink`、`mapscale near\|far` | 旅の地図の見た目 (設定 `mapStyle`) と縮尺。開いている地図にもすぐ効く |
| `chat clear` | チャット欄と右上の通知 (トースト) を消す |
| `shot <名前>` | 次に描いた絵を `<名前>.png` に保存 |
| `waitdump` | 構造の書き出し (`cmd skyabyss dev dump ...`) が終わるまで待つ |
| `perspective first\|back\|front` | 視点 (一人称 / 三人称の背中側 / 三人称の正面) |
| `swingpose <0..1\|off>` | 武器を振る途中の姿で止める (自分だけ。一人称・三人称とも。`client/weapon/WeaponClient.forcedSwing`)。弓は 0 = 放した直後の反動の最大、1 = 戻りきった |
| `swing` | 利き手を一振りする (止めずに動かす) |
| `swingclock on\|off` | 武器の振りの長さ (攻撃速度で決まる。`client/weapon/SwingClock`) の入り切り。`off` = 昔のバニラの 6 tick (変更前後の比べ用) |
| `use` / `stopuse` | 右クリックを押し続ける / 放す (KeyMapping。弓を引く姿と放す動きの確認用) |
| `mainhand left\|right` | 利き手の設定 (左利きの確認用。終わったら `right` に戻す) |
| `bodyyaw <度>` | 体の向きを視線から何度ずらすか (人は 50 度までしかずれないので、それ以上は効かない。斜めの絵用) |
| `echo <文>` | 記録に書くだけ |
| `watch on\|off` | 開発用の監視。on のあいだ、目的の帯の文・開いた画面・流れた知らせが変わるたびに、ゲーム内の時刻 (`game=` 総 tick・`day=`・`tod=`) つきで記録 (`[watch]`) に書く (`first_hour.txt`) |
| `band` | いまの目的の帯の文 (と距離・方角の札) を一行 `[band]` に書く |
| `waitgame <総 tick>` | 総 tick (`time set` に左右されない通算) がその値になるまで待つ。`cmd tick rate 100` でサーバーを 5 倍にした台本の待ちはこれで書く (`wait` はクライアントの tick) |
| `waitband <最大 tick> <語>` / `waitnotice <最大 tick> <語>` | 帯の文 / 知らせにその語が出るまで待つ。来なければ `[gap]` を記録して先へ進む (失敗にはしない)。`waitnotice` は `watch on` が要る |
| `waitscreen <最大 tick> <none\|画面のクラス名>` | その画面が開く / 全部閉じるまで待つ。来なければ `[gap]` |
| `quit` | ゲームを閉じる (書かなくても、最後まで来たら閉じる) |

例: `tools/shots/example_titan_arrival.txt` (岩鯨の背の到着)、`tools/shots/example_tide_back.txt` (潮殻の背の全景)。

### 武器を持った姿 (一人称・三人称・振りの途中)

```
python tools/gen/shots/gen_weapon_pose_shots.py [--left] [武器の id ...]   # 台本 tools/shots/weapon_poses.txt を作る (id を省くと全部。--left は左利き)
./gradlew runShots -Pscript=tools/shots/weapon_poses.txt           # 約 2 分 (195 枚)
python tools/sheets/weapon_pose_sheet.py                                  # 武器ごとの 1 枚 docs/review/previews/weapon_pose_<id>.png (行 = 一人称 / 三人称正面 / 横向き、列 = 構え・進み 0.2 / 0.4 / 0.55 / 0.75)
./gradlew runShots -Pscript=tools/shots/weapon_bow.txt             # 雷翼の弓: 引く・放す
python tools/gen/shots/gen_weapon_swing_shots.py [--new-only] [id[!] ...]    # 台本 tools/shots/weapon_swing.txt: 実際に `swing` して 1,3,5,7,9,11 tick 後を撮る (一人称・三人称、昔 old / 今 new。id の末尾 ! = new だけ)
./gradlew runShots -Pscript=tools/shots/weapon_swing.txt
python tools/sheets/weapon_swing_sheet.py                                 # docs/review/previews/weapon_swing_sheet.png (代表 4 本の昔 / 今) と weapon_swing_<id>.png
python tools/gen/shots/gen_weapon_swing_mp_shots.py <合図の置き場>            # マルチ: swing_mp_alice.txt (振る側) / swing_mp_bob.txt (見る側。old → new で 60 枚ずつ)。下の「マルチの確かめ」で二人入れ、rcon で Alice に武器・Bob を正面 4 ブロックに置き、置き場に go を作る
python tools/lib/weapon_display.py [--check]                            # 持ち方 (display) の表を models/item に反映 / 食い違いの確認
```

持ち方と動きの決まりは `docs/design/15_high_poly.md` の 4.6.1。

### 装飾品の低ポリメッシュ (手・地面・額縁と、体に付けた姿。15 の 4.6.4)

```
cd tools/rough/lowpoly/accessories && python build_final.py   # 採用した A 案 16 品 x 階級 3 -> docs/review/lowpoly_roughs/accessories_final/<id>/ (面数は summary.json)
python tools/install/install_lowpoly_accessories.py                     # メッシュ・アトラス・光る画像を入れ、models/item/<id>.json の base を builtin/entity にする (何度でも)
python tools/gen/shots/gen_accessory_lp_shots.py [thunder|ember|whale|tide]  # 台本 tools/shots/acc_lp_<巨神>.txt (手・平絵・地面・額縁・体に付けた姿)
./gradlew runShots -Pscript=tools/shots/acc_lp_<巨神>.txt
python tools/sheets/accessory_lp_sheet.py [巨神]                       # docs/review/previews/acc_lp_<巨神>.png (コンタクトシート)
```

台本の手順 `acctier common|rare|legend|off` (階級を固定)、`accmesh on|off` (低ポリ / 平絵)、`accworn on|off` (体に付けた姿の入り切り)、`accequip <id> [<id>]` (自分のクライアントの装飾の枠に品を入れる。見た目だけ)。元の平絵のモデルは `models/item_flat/` に退避してある (`LowPoly.accessoryMeshes = false` の戻り先)。ラフの形は `tools/rough/lowpoly/accessories/{thunder,ember,whale,tide}.py` (A 案)。`tools/gen/items/gen_accessory_assets.py` 以外で models/item/<id>.json を書き直す生成スクリプトを動かしたら `tools/install/install_lowpoly_accessories.py` を流し直す。燠の灯 (ブロック) の持ち物欄の絵は `tools/rough/lowpoly/accessories/cinder_sprite.py`。

### 武器の低ポリメッシュ (手・地面・額縁。15 の 4.6.2)

```
python tools/install/install_lowpoly_weapons.py        # weapons_final/ のメッシュ・アトラス・光る画像を入れ、models/item/<id>.json の base を builtin/entity にする (何度でも)
python tools/install/install_lowpoly_abyss_weapons.py  # 深淵の専用伝説 4 点 (docs/review/lowpoly_roughs/abyss_weapons/<id>/<案> から。--option abyss_greatsword=b で案を替える。3 案は rough/lowpoly/weapons/abyss/build_abyss.py)
python tools/gen/shots/gen_weapon_lp_shots.py [id ...]   # 台本 tools/shots/weapon_lp_<id>.txt (階級 3 x 強化 3 x 一人称/三人称、ボクセル版、弓の引き、盾の構え、地面・額縁) と weapon_lp_perf.txt
./gradlew runShots -Pscript=tools/shots/weapon_lp_<id>.txt
python tools/sheets/weapon_lp_sheet.py [id ...]       # docs/review/previews/weapon_lp_<id>.png (コンタクトシート) ほか
```

台本の手順 `weaponmesh on|off` (低ポリ / 元のボクセル)、`weapontier common|rare|legend|off` (メッシュの階級を固定。開発用)。元のボクセル版の models/item/*.json は `models/item_voxel/` に退避してある (`LowPoly.weaponMeshes = false` の戻り先)。`tools/gen/items/gen_ember_items.py` `tools/install/install_storm_gear.py` `tools/install/install_tide_gear.py` `tools/gen/items/gen_weapon_models_v2.py` は models/item/<id>.json をボクセルで書き直すので、動かしたあとに `tools/install/install_lowpoly_weapons.py` と `tools/lib/weapon_display.py` を流す。

### マルチの確かめ (専用サーバー + 二人)
`run/server.properties` に `online-mode=false`・`server-port=25666`・`enable-rcon=true`・`rcon.port=25766`・`rcon.password=test`・新しい `level-name`・`level-seed=4242`、`run/eula.txt` に `eula=true`。`./gradlew runServer` で起動し、台本の頭の `server localhost:25666` で専用サーバーへ入るクライアントを `./gradlew runShots -Pscript=tools/shots/mp_alice.txt -PshotsUser=Alice`、`mp_bob.txt -PshotsUser=Bob` で動かす (Alice が入ってから Bob を起動)。台本の `waitfile <ファイル>` は外 (`signal` か手) がファイルを置くまで待つ。サーバーへの命令は `python tools/dev/rcon.py "<命令>"` (OP の権限。`skyabyss cinematic play <場面> Bob` など)。

### 最初の 1 時間の通し (`tools/shots/first_hour.txt`、rpg_brushup A10)

```
rm -rf run/shots/first_hour/saves/opening_first_hour      # 毎回、新しい世界で (新しいプレイヤーの目覚めから)
SKYABYSS_LANG=en_us ./gradlew runShots -Pscript=tools/shots/first_hour.txt   # 約 25 分。音は自動で 0。台本の待ち (waitband / waitnotice) は英語の文なので英語で
```

新しいプレイヤーが、序章 → 帳面 → 煙 → 語り部 → 最初の影 → 破れた頁 → イサナ → 足音の夜 → 歌の夜 → 岩鯨の初見 → 鎖、を進む間の「帯の文・画面・知らせ」を、ゲーム内の時刻つきで `run/shots/first_hour/shots/first_hour/shots.log` の `[watch]` に残す (開発用。本番の計測ではない)。拍子の強制 (`opening <拍子>`) は使わず、影・足音・歌・岩鯨は本物の判断に任せて総 tick を 60 分ぶん流す (`cmd tick rate 100` でサーバーだけ 5 倍)。歩きは `dev opening view|goto` で運び、会話は `dev talk "<役>"` / `dev talk say <continue|leave|番号>` で本物の会話 (DialogueService) を進める (節と選択肢が `[dialogue]` に出る)。結果の読み方と見つけた穴は `docs/review/first_hour_log.md`。

- 巨神が地形にめり込まない・潮殻の乗り場の輪 (`TitanGround`、08 の 2.2〜2.4): `python tools/gen/shots/gen_titan_ground_shots.py` → `SKYABYSS_SHOTS_SEED=4242 ./gradlew runShots -Pscript=tools/shots/titan_ground.txt` → `python tools/sheets/titan_ground_sheet.py` で `docs/review/previews/titan_ground_sheet.png` (前後。開発コマンド `/skyabyss dev titanground <true|false>`・`place tide <x> <z> <yaw>`・`scout`)。自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="titan ground" ./gradlew runServer` (体積の柱ごとにブロックを数える)。台本の世界の種は環境変数 `SKYABYSS_SHOTS_SEED` で変えられる

## 生成スクリプトとコミット済みデータの同期 (`tools/audit/check_generators.py`)

`src/main/resources` の `data/` と `assets/*/lang` を書く生成スクリプトは、あとからの手直しを取り込んである (コミット済みのデータが正)。
走らせても、書き換わるのは生成物の写しの `docs/design/07` などの表だけ。ずれ (ドリフト) は次で確かめる。

```
PYTHONPATH=tools/lib python tools/audit/check_generators.py                 # 一覧を順に走らせ、最後にコミット済みと違うファイルを出す (一時コピーの中で走る。本物は触らない)
PYTHONPATH=tools/lib python tools/audit/check_generators.py --isolated      # 1 本ずつ別々に走らせ、本ごとに出す (ほかの生成スクリプトが足す分は、その本では足りなく見える)
PYTHONPATH=tools/lib python tools/audit/check_generators.py story_quests --diff   # 名前の一部で絞る / 差分の中身も出す
PYTHONPATH=tools/lib python tools/audit/check_generators.py --list          # 一覧だけ
```

- 比べ方: 改行コード (CRLF/LF) と PNG の再圧縮は無視、JSON はキー順を無視 (`--strict` でバイト列まで)。見る場所は `data/` と `assets/*/lang`。終了コードは 0 (ドリフトなし) か 1。
- **順番が大事**: 同じファイルを何本かが書く。一章の lang は `gen_ch1_content` が土台を書き、`gen_ch1_prep`・`gen_ch1_trails`・`gen_ch1_travel`・`gen_artisan_dialogue` が足す。`gen_ch4_ground` は `main_ember.json`・`main_key.json` (と lang) に支度の段・イサナの目的を足す (`gen_story_quests` のあと)。`gen_ch2_ground`・`gen_ch3_ground` も、章の content のあと。一覧 (`GENERATORS`) の並びがその順。
- 持たないキーは残す: lang の書き出しは、いまあるファイルを土台に自分のキーだけ上書きする (`tools/lib/langmerge.py`。`gen_ch4_content` が先にやっていたのと同じ決まり)。手で足した文・ほかのスクリプトの文は消えない。
- 手で直した言い回しは、生成スクリプトの中に取り込んである。まとまった量は `tools/trial_lang/zz_hand_edits.py` (試練)、`HAND_EDITS` の表 (`gen_storm_lore.py`・`gen_ch5_layers.py`)。直したあとの文を変えるときは、ここも合わせる。
- 一時コピーを使い回す開発用の環境変数: `SKYABYSS_GENCHECK_DIR` (コピー先。2 回目から速い)、`SKYABYSS_GENCHECK_EXPORT` (ドリフトしたファイルの生成結果の書き出し先)。
- 古い・見ない生成スクリプト (一覧に入れていない。止めたまま残す):
  - `tools/gen/items/gen_relic_blocks.py` — 遺物の部品の模型・テクスチャ用。封印 (`trial_seal`) まわりは古く、走らせると消した `trial_seal` の blockstate・模型を作り直し、試練の lang に `block.skyabyss.trial_seal` を足す。**古い (走らせない)**。
  - `tools/gen/world/gen_titan_volumes.py` — 巨神の立入禁止の体積をメッシュから焼く。メッシュが変わると結果が変わる (`data/skyabyss/titan_volumes/*`)。メッシュを直したときだけ手で走らせる。
  - `tools/gen/ch4/gen_ch4_gear.py`・`tools/gen/abyss/gen_abyss_mobs_a.py` など `docs/review` のラフを読むもの — ラフが無いと走らない (一時コピーに入れていない)。
  - `*_ground_mobs.py`・`gen_mobs.py`・`gen_items.py`・`gen_weapon_models*.py`・`gen_block_textures.py`・`gen_opening_sounds.py`・`gen_sky_sounds.py`・`gen_titan_sounds.py`・`gen_heart.py`・`gen_titan_model.py` — 採用済みの素材を上書きするので止めてある (`SKYABYSS_ALLOW_REGEN`)。**古い**。
- `sys.path` の足し (`for p in ('../lib')` のように文字列の括弧だけだったもの) は、1 字ずつ分かれて効いていなかった。`('../lib',)` に直した (40 本)。

## ソースの検査 (ゲームを立てずに)

```
python tools/audit/check_lang_keys.py     # コード・データの文字列リテラルの翻訳キーが ja_jp / en_us にあるか (LangSelfTest (4) と同じ規則。偽陽性の許可リストは両方に同じ内容)
python tools/audit/check_band_width.py    # 目的の帯の文が幅 230 px に入るか (ObjectiveBandSelfTest と同じ字幅表。目印の付く目的は距離の札の分を引く)
python tools/audit/check_stairs.py        # 設計図 (data/skyabyss/opening/*.json) の階段: 床の逆さ・逆向き・並びの食い違い・1 段より高い段・埋まった段・戸口の段・屋根の抜け・角の抜け。--sbd <書き出し> で Java で建てる物 (祠は SKYABYSS_DUMP=shrine_<巨神>) も。都は town.py が同じ規則の fix() で直してから書く
```

自己テストは `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=lang ./gradlew runServer` (`lang` と `lang objective band` の二段)。

## 地上の場所を新しい世界で確かめる

- 監査: `run/server.properties` の `level-seed` を変え、`run/world` を消してから `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="chapter grounds,build audit" ./gradlew runServer`。地形しだいで指摘が変わるので、2〜3 の種で流す（共有のロックを取る）
- 撮影: `./gradlew runShots -Pscript=tools/shots/ground_sites_qa.txt -PshotsName=ground_sites_qa`（一〜三章の地上の場所を、目の高さと上から。拠点のまわりの場所＝旅の跡・荷車・日記は、この世界には建たない）
- 地上の場所の浮き・くぼみ・漏れは、部品ごとに直す前に、建て終わりの仕上げ `SiteJob.settle()` で直せないかを見る

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

起点の名前はヘッダにある (`titan_back`: `arrival` `shrine` `hub`、`tide_back`: `arrival` `rest_hub` `rest_g0` …、`storm_back`: `arrival` `hub` `rest_*`)。雷翼の背は `tools/shots/storm_back.txt` で書き出しと撮影を一度にする。`"x,y,z"`、`"x,~,z"` (列でいちばん上の立てる所) でもよい。
歩く決まり (上り 1・下り 3・頭 2 マス・水の中は 1 つ上へ) は背の自己テスト (`TideBackSelfTest.walk`) と同じ。水は実際より多めに出る (バニラは近くの落ち口の方へだけ流れるため)。
部品は `sbd.py` (読む)、`analyze.py` (囲まれた空間と口・歩く・水・看板)、`render.py` (図)。新しく背中の検査を書くときは、自己テストに同じ探索を書き足す前にこれで見る。例は `docs/review/structure_*`。

## 場面の台詞の声 (`tools/gen/sound/gen_cinematic_voice.py`)

台詞の「声のようなもの」(言葉にならないつぶやき。自前の合成) を作る。仕様は `docs/design/20_cinematics.md` の 5 章。

```
python tools/gen/sound/gen_cinematic_voice.py --install [--style a|b|c]   # 全台詞の .ogg と sounds.json を src に書く (既定 a。選んだ様式への切り替えはこの一語)
python tools/gen/sound/gen_cinematic_voice.py --check                      # 全台詞にクリップ・音の登録があるか、長さが表示時間に収まるか
python tools/gen/sound/gen_cinematic_voice.py --review                     # 3 様式の見本を docs/review/voice/ に (選定ページ。テンプレートは tools/sheets/voice_review_template.html)
```

入力は `src/main/resources/assets/skyabyss_cinematic/voice_manifest.json` (台本の台詞の一覧)。台本を変えたら、環境変数 `SKYABYSS_DUMP_VOICE=<そのパス>` を付けて `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=cinematic ./gradlew runServer` を一度流して書き直し、`--install` する (自己テスト `SELFTEST cinematic` が、一覧・sounds.json・.ogg が台本と合っているかを見る)。書く先は `skyabyss_cinematic/sounds/voice/` と `sounds.json` と `voice_style.txt` だけ。

## 採用版の生成元 (実行してよい)

| 素材 (`src/main/resources/assets/skyabyss/` 以下) | 生成元 | 備考 |
|---|---|---|
| `textures/item/*.png` (32x32)、`textures/block/*.png` | `rough/items_blocks_32.py` | 出力先 (`docs/roughs/`) は片付けた。作り直すときは一時フォルダに書き出し、採用した物だけ src へ手でコピーする |
| 岩鯨: `geo/entity/stone_whale{,_mid,_far}.geo.json`、`textures/entity/stone_whale{,_glowmask}.png`、`animations/entity/stone_whale.animation.json` | `tools/install/install_stone_whale.py` | 実行しても今の素材と同じ物ができる |
| 潮殻の巨神: `geo/entity/tide_shell{,_mid,_far}.geo.json`、`textures/entity/tide_shell{,_glowmask}.png`、`animations/entity/tide_shell.animation.json`、`sounds/tide/*.ogg` | `tools/install/install_tide_shell.py` (形の元は `rough/tide_shell_titan.py` の案 B。珊瑚の LOD の塊・塔・段々の庭は install 側で足す)、音は `tools/gen/sound/gen_tide_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。確認用の絵は `docs/review/tide_shell_roughs/install/`。アニメの角度は `tools/install/install_tide_shell.py` の `ANIM` に意図した向きで書く (GeckoLib の符号はスクリプトが直す) |
| 潮殻の背の島 (低ポリ案 B「段になった珊瑚礁の島」をブロックにした亀、08 の 3.2・3.8節): `data/skyabyss/tide_back/turtle.bin` | `tools/gen/mobs/gen_tide_back_turtle.py` (形は `tools/lib/lowpoly_tide_voxel.py` が案 B の近景メッシュから作る格子。材料はアトラスの色にいちばん近いバニラのブロック。置き方・体の中・腹の板は共通の `tools/gen/items/gen_back_shape.py` の turtle。`--preview` で上・横・正面の色の地図)。構造の書き出しとメッシュの並べ描きは `tools/sheets/tide_back_b_views.py` | 同じ物ができる。変えたら配置の版 (`TideBackLayout.LAYOUT_VERSION`) を上げる |
| 潮殻の背の敵: `geo/entity/{coral_thrall,reef_scuttler,bubble_puffer}.geo.json`、`textures/entity/<名前>{,_glowmask}.png` (珊瑚憑きは `_statue` も)、`animations/entity/<名前>.animation.json`、銛 `geo/gear/coral_spear{,_statue}.geo.json` など | `tools/install/install_tide_mobs.py` (形の元は `rough/tide_mobs_v2.py` の A 案。頭と泡吹きの目を変えた) | 同じ物ができる。`--eyes original` で泡吹きの目をラフ A に戻す。目の比較は `docs/review/tide_shell_roughs/bubble_puffer_eyes.png`、確認用の絵は `docs/review/previews/tide_mobs/` |
| 雷翼の背の敵: `geo/entity/{feather_warden,static_moth,thunder_supplicant}.geo.json`、`textures/entity/<名前>{,_glowmask}.png` (雷蛾は帯電で足す光 `static_moth_charge.png` も)、`animations/entity/<名前>.animation.json`、鉤槍と杖 `geo/gear/{hooked_glaive,supplicant_staff}.geo.json` など | `tools/install/install_storm_mobs.py` (形の元は `rough/thunder_mobs.py`。既定は 12 の 14 章の推奨 羽守 B・雷蛾 B・雷乞い C、`--warden/--moth/--supplicant a\|b\|c` で選び直せる。面の隠れは同じ骨の中だけで数える) | 同じ物ができる。確認用の絵は `docs/review/previews/storm_mobs/` |
| 雷翼の背の敵の音 `sounds/storm_mobs/*.ogg` と `sounds.json` の 18 行、スポーンエッグのモデル | `tools/gen/mobs/gen_storm_mob_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。`sounds.json` は自分の行だけ書き直す |
| 余燼の背の敵: `geo/entity/{ash_thrall,ember_moth,cinder_roller}.geo.json`、`textures/entity/<名前>{,_glowmask}.png` (灰被りは燃え上がった姿の `ash_thrall_flared` も)、`animations/entity/<名前>.animation.json`、火掻き棒 `geo/gear/ash_poker.geo.json` など。音 `sounds/ember_mobs/*.ogg` | `tools/install/install_ember_mobs.py` (形の元は `rough/ember_mobs_{thrall,moth,roller}.py` の A 案。`--option thrall=c` などで差し替え。アニメの姿勢の重なりも検査する。約 2 分)、音は `tools/gen/sound/gen_ember_mob_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。確認用の絵は `docs/review/previews/ember_mobs/`、ゲームの中は台本 `tools/shots/ember_mobs.txt` (13 の 18 章) |
| モブの鳴き声・足音・攻撃音 (岩鯨の背の番兵たち・各心臓の被弾・背の敵の足音): `sounds/mobs/*.ogg` | `tools/gen/sound/gen_mob_sounds.py` (登録は `registry/MobSounds`、`sounds.json` は末尾の行) | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 岩鯨の心臓と鯨骨の番兵長 (石鎧の将) の技の音: `sounds/mobs/stone_heart_*.ogg` 22 本と `captain_*.ogg` 5 本、`sounds.json` の同名の行、字幕 `skyabyss_mobsounds/lang` (登録は `registry/MobSounds`) | `tools/gen/sound/gen_boss_skill_sounds.py` (部品は `tools/gen/sound/gen_mob_sounds.py`。石の共鳴・板の軋み・鎖・鯨の骨と石の鎧。バニラの音は使わない。使い分けは `docs/design/02` の 5 章) | 同じ物ができる (ogg はバイト列だけ毎回変わる)。`sounds.json` は自分の行だけ書き直す |
| 避雷の柱 (`storm_rod`・`storm_rod_bulb`・`storm_rod_shaft`・`storm_rod_tip`)・戻しの鏡 (`reset_mirror`。登録は消えた。絵だけ残る)・燠の灯 (`cinder_lamp`): `models/block/*.json`、`blockstates/*.json`、`textures/block/{storm_rod_*,reset_mirror,cinder_lamp}.png` (32x32)、`models/item/cinder_lamp.json`、`data/skyabyss/{loot_table/blocks,recipe}/cinder_lamp.json` | `tools/gen/items/gen_back_blocks.py` (雷翼は磨いた銅・青白い光・白い羽、燠の灯は鉄と黒石の籠の熾。確認は台本 `shots/back_blocks.txt` のあと `tools/sheets/back_blocks_sheet.py` で `docs/review/previews/blocks_*.png`) | 同じ物ができる (決定的)。ブロックの置き換えは `docs/design/12` の 3.5 と `13` の 6.4 |
| 余燼の中ボス 灰の鍛え手: `geo/entity/ash_smith.geo.json`、`textures/entity/ash_smith{,_glowmask,_glowmask_white}.png`、`animations/entity/ash_smith.animation.json`、大鎚・火箸 `geo/gear/{smith_hammer,smith_tongs}.*` (受け口 socket_main / socket_off に付く別モデル)、ボスバー `textures/gui/boss/ash_smith_{frame,notches,icon}.png`。音 `sounds/ember_smith/*.ogg` と `sounds.json` の ash_smith_* の行 | `tools/install/install_ember_smith.py` (形の元は `rough/ember_smith.py` の案 A 鞴の巨人。`--option b\|c` で差し替え。基のテクスチャを持ち上げ、白の発光とバーも作る。アニメの姿勢の重なりも検査する。約 2.5 分)、音は `tools/gen/sound/gen_ember_smith_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。確認用の絵は `docs/review/previews/ember_smith/`。体を前へ倒す技では、腹・前掛けが脚の箱に数単位食い込む (姿勢の検査が数える。リグの限界) |
| 天の鍵 `textures/item/sky_key.png`、場面の音 `assets/skyabyss_story/sounds/*.ogg` と `sounds.json` (14 の 4 章) | `tools/gen/story/gen_story_assets.py` (`--option A\|B\|C`、既定 C。比較は `docs/review/sky_key_roughs/compare.png`) | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 1 章の続きのクエスト・会話の足し書き `data/skyabyss/{quest,dialogue/*_story.json,lore/entry/chapter1}`、文 `assets/skyabyss_story/lang`、07 の 16 章の表 | `tools/gen/story/gen_story_quests.py` (雷翼・余燼の仮の名は `NAMES`) | 同じ物ができる |
| 潮殻の背の敵の音 `sounds/tide_mobs/*.ogg`、泡 `textures/entity/bubble_shot.png`、素材の絵 `textures/item/{tide_shell_shard,tide_dried_shell,bubble_sac}.png` (仮) | `tools/gen/mobs/gen_tide_mob_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 心臓: `geo/entity/stone_whale_heart_p1..p3.geo.json`、`textures/entity/stone_whale_heart_p*`、`animations/entity/stone_whale_heart.animation.json` | `tools/install/install_heart_v4.py` (形の元は `tools/sources/heart/`。`rough/boss_v4.py` の A 案) | |
| 射手: `geo/entity/rib_archer.geo.json`、弓の `geo/gear/rib_archer_bow.geo.json` など | `rough/rib_archer_v4.py` | 同じ物ができる |
| 番兵長: `geo/entity/whalebone_captain.geo.json` / png / glowmask / animation | `rough/captain_v4.py --install` | 同じ物ができる |
| 苔這い (苔芋虫 B 案): geo / png / glowmask / animation | `tools/install/install_moss_crawler_b.py` (形の元は `rough/smooth_mob2.py` の `opt_b`。ラフの出力 `docs/review/smooth_mob_roughs2/moss_crawler/b/` も更新する) | 同じ物ができる。`tools/gen/mobs/gen_mobs.py` の苔這いは古い版なので走らせない |
| `textures/gui/sky_vignette.png` | `tools/gen/ui/gen_vignette.py` | 同じ物ができる |
| 成長: `textures/gui/growth/{status,hud}.png`、`sounds/rpg/level_up.ogg` | `tools/gen/items/gen_growth_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わるが、音は同じ)。画面の配置を変えたら `GrowthScreen.java` と値をそろえる |
| 見る者: `geo/entity/watcher.geo.json`、`textures/entity/watcher{,_glowmask,_glint}.png`、`animations/entity/watcher.animation.json` | `rough/watcher.py --install` | ラフ 3 案は `docs/review/watcher_roughs/`。`--install` なしなら docs にだけ書く |
| `sounds/prologue/*.ogg` (見る者の音) | `tools/gen/sound/gen_watcher_sounds.py` | 同じ物ができる |
| 技: `textures/gui/skill/{icons,skills}.png`、`textures/particle/{rune_spark,sky_mote}_*.png`、`particles/*.json`、`sounds/rpg/skill/*.ogg` | `tools/gen/ui/gen_skill_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。アイコンのラフ 3 案と比較画像は `docs/review/skill_icons_roughs/`、採用は `ADOPTED`。`--roughs` なら docs にだけ書く。画面の配置を変えたら `SkillScreen.java` と値をそろえる |
| 共鳴の炉の画面: `textures/gui/forge/forge.png` (左右の板・つまみ・印・候補の札、2 倍密度) | `tools/gen/items/gen_forge_assets.py` | 同じ物ができる。画面の配置を変えたら `ResonanceForgeScreen.java` と値をそろえる |
| 旅嚢の画面: `textures/gui/pouch/pouch.png` (画面・枠 3 種・「？」・ボタン 3 状態・輪の札と中心、2 倍密度。装飾の画面と同じ作り。`--preview` で `docs/review/_gallery_pouch/pouch_screen_preview.png`) | `tools/gen/items/gen_pouch_assets.py` | 同じ物ができる。画面の配置を変えたら `PouchLayout.java`・`PouchScreen.java` と値をそろえる。つまみは装飾の画面の素材を使う (`tools/gen/items/gen_accessory_assets.py`) |
| 語り部・鯨追い: `geo/entity/{storyteller,whale_chaser}.geo.json`、`textures/entity/{storyteller,whale_chaser}.png`、`animations/entity/{storyteller,whale_chaser}.animation.json`、顔 `textures/gui/npc/face/*.png` | `rough/npc.py --install` (画面の見本は `rough/npc_ui_mock.py`) | ラフは `docs/review/npc_roughs/`。`--install` なしなら docs にだけ書く |
| 会話とクエスト: `data/skyabyss/{dialogue,quest}/*.json`、`assets/skyabyss_npc/lang/*.json`、`docs/design/07_npc_quest.md` の表 | `tools/gen/mobs/gen_npc_content.py` | 文と構造の正本はこのスクリプト。JSON や lang を手で直したら、こちらにも反映する |
| 1章の空 雲の目: `textures/environment/sky_eye/{sclera,iris,lid,lid_glow,patch}.png` | `rough/sky_eye.py --install` | ラフ 3 案と夜空の比較は `docs/review/sky_eye_roughs/`、採用は `CHOSEN` (今は b 月蝕の瞳)。`--install` なしなら docs にだけ書く。描画の割合を変えたら `CloudEyeRenderer.java` と値をそろえる |
| `sounds/sky_ch1/*.ogg` (削られた山頂・雲の目の音) | `tools/gen/sound/gen_chapter_sky_sounds.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる) |
| 巨神での移動: `textures/item/stone_whale_call.png`、`textures/block/titan_waymark_{front,side,base,cap}.png`、`textures/gui/travel/chart.png`、`sounds/titan/travel/*.ogg` | `tools/gen/world/gen_titan_travel_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。ラフ 3 案と比較画像は `docs/review/titan_travel_roughs/`、採用は `ADOPTED_CALL` / `ADOPTED_MARK`。`--roughs` なら docs にだけ書く。画面の配置を変えたら `TravelChartScreen.java` と値をそろえる |
| 章の導きの仮の絵 (旅の覚書・鼓動守): `textures/item/wayfarer_notes.png`、`textures/item/heartbeat_ward.png` (どちらも 32x32。右上にマゼンタの印がある仮の絵。鼓動守の 4 種は同じ絵)、`models/item/{wayfarer_notes,heartbeat_ward_<巨神>}.json` | `tools/gen/story/gen_guide_placeholders.py` | ユーザーが 2 案から絵を選んだら、この 2 枚のテクスチャを選んだ絵に置き換える (JSON は変えなくてよい)。この生成は置き換えのあとは流さない |
| 言伝ての板: `models/block/message_board.json`、`models/item/message_board.json`、`blockstates/message_board.json`、`textures/block/message_board_*.png` (32x32) | `tools/gen/story/gen_message_board.py` | 採用は既定で A。`--option B` などで別案に差し替えられる。ラフ 3 案と比較画像は `docs/review/board_roughs/`。`--roughs-only` なら docs にだけ書く |
| 序盤の構造物 (野営地・鐘の宿り・積み石・巨大な足跡・巨神の骨・骨の石板): `data/skyabyss/opening/{camp,shelter,cairn,footprint,ribs,slab}.json` (ブロックの配置・印・地形の整え方。ゲームの `opening/site/Blueprint` が読む) | `rough/opening_sites.py --install` (ラフ 3 案と比較画像は `docs/review/opening_site_roughs/`。採用は 野営地 A・宿り A・足跡 A・骨 B。`--option <場所>=<a\|b\|c>` で差し替え。`--install` なしなら docs にだけ書く) | 同じ物ができる (決定的)。配置の検査は `/skyabyss dev opening dump <種類> <番号>` で書き出して `tools/structure/check.py` |
| 潮殻の装備 (三叉槍・盾・大鋏・銛): `models/item/{tide_shell_trident,coral_shield,coral_shield_blocking,tidecaller_claw,coral_harpoon}.json`、`textures/item/3d/*.png`、アイコン `textures/item/{tide_shell_trident,coral_shield,tidecaller_claw,coral_harpoon,tide_chime_charm,moontide_pearl,tide_shell_core}.png` と護符・真珠・心核の `models/item/*.json` | `tools/install/install_tide_gear.py` (ラフは `rough/tide_gear.py`、3 案と比較画像は `docs/review/tide_gear_roughs/`。採用は全部 A。`--option <品>=<a\|b\|c>` で差し替え、`--from-sources` で `tools/sources/tide_gear/` から入れ直す) | 同じ物ができる。盾の持ち方 (`SHIELD_DISPLAY`) はバニラの盾の値 (03 の 11.6節) |
| 余燼の装備 (戦斧・大鎚・火掻き): `models/item/{ember_war_axe,smith_hammer,thrall_poker}.json`、`textures/item/3d/*.png`、アイコン `textures/item/{ember_war_axe,smith_hammer,thrall_poker,ember_mantle,kindling_charm,ember_lantern,ember_horn,ember_core,ember_shard,spark_dust,cinder_husk}.png` と、アイコンだけの品の `models/item/*.json` | `tools/gen/items/gen_ember_items.py` (ラフは `rough/ember_gear.py`、3 案と比較画像は `docs/review/ember_gear_roughs/`。採用は全部 A。`--option <品>=<a\|b\|c>` で差し替え、`--from-sources` で `tools/sources/ember_gear/` から入れ直す) | 同じ物ができる。大鎚の持ち方 (`HAMMER_DISPLAY`) は目分量の最初の値 (実機で確認すること) |
| 4 巨神の立体防具 (岩鯨の甲殻・潮殻の鎧・雷翼の羽衣・余燼の鎧): `geo/armor/<名前>_armor.geo.json`、`textures/armor/<名前>_armor{,_glowmask}.png`、`textures/item/<名前>_{helmet,chestplate,leggings,boots}.png` (32x32、3D の各部位を描いた絵) | `tools/install/install_armor_v2.py` (設計は 16。部品は `rough/armor_v2_parts.py`、4 体の形・素材・飾りは `rough/armor_v2_sets.py`、道具は `rough/armor_kit.py`、アイコンは `rough/armor_v2_icons.py`。重なり 0・光る画素・面の予算を検査して書く。台本 `shots/armor_v2.txt`) | 同じ物ができる (決定的)。バニラ層 `textures/models/armor/*_layer_{1,2}.png` と `models/item/*.json` は触らない |
| 雷翼の装備 (弓・爪・鉤槍と、アイコンだけの品 7 つ): `models/item/{thunder_wing_bow,thunder_wing_bow_pulling_{0,1,2},raiju_claw,warden_glaive,storm_charm,storm_anklet,storm_eye_feather,thunder_wing_shard,thunder_wing_core,cloudgap_fruit,moth_dust}.json`、`textures/item/3d/*.png`、`textures/item/*.png`、`textures/mob_effect/static_ward.png` | `tools/install/install_storm_gear.py` (ラフは `rough/storm_items.py`、3 案と比較画像は `docs/review/storm_gear_roughs/`。採用は 03 の 12.4節。`--option <品>=<a\|b\|c>`、`--from-sources` で `tools/sources/storm_gear/` から) | 同じ物ができる。弓の持ち方はバニラの `item/bow.json` の値 |
| 都の施設の会話・店・文 (35 の 6 章): `data/skyabyss/dialogue/attendant_<役>.json`、`data/skyabyss/shop/*.json`、`assets/skyabyss_fac/lang/*.json`、`models/item/bell_coin.json` | `tools/gen/world/gen_facility_content.py` (係の人 16 役の選択肢 (取引所の番を含む)、ギルドの模様・名札の色・功績の名前 (`PATTERNS`・`PLATES`・`ACHIEVEMENTS` は `GuildEmblems`・`GuildAchievements` とそろえる)、取引所の文 (`TRADE`)、自分で書いた節 `NODES` = 師範の振り直し・祭司の誓い・薬師の醸造、ギルド・訓練場・鍵の間・記録の文。誓いの一覧 `VOWS` は `titan/vow/Vows` の登録とそろえる。撮影 `shots/attendants.txt`・`shots/facilities2.txt`) | 同じ物ができる。lang は `skyabyss_fac` の名前空間だけに書く (共有の lang は触らない) |
| 都の町のクエスト (35 の 3.5・3.3): `data/skyabyss/quest/town/*.json`、会話 `dialogue/guest_<人>.json` と係の人の足し書き `dialogue/attendant_<役>_town.json`、伝承 `lore/entry/town/*.json`、文 `assets/skyabyss_town/lang` | `tools/gen/story/gen_town_quests.py` (毎回すべて書き直す。手で直さない) | 同じ物ができる |
| 町のクエストの限定の人の絵: `textures/lowpoly/guest_<人>.png`、肖像 `textures/gui/npc/face/guest_<人>_<表情>.png`、動き `animations/entity/town_guest.animation.json`、一覧 `docs/review/town_quests/guests.png` | `tools/gen/mobs/gen_town_guests.py` (係の人の案 A の形と描き方 `rough/lowpoly/fac/attendants.py`・`tools/gen/mobs/gen_attendant_portraits.py` を読むだけ。色の表はこのスクリプトが持つ) | 同じ物ができる (決定的) |

`rough/boss_v4.py`、`rough/sentinel_v4.py`、`rough/voxel.py`、`rough/build_gallery.py` は `src/` を読むだけで書かない。
`rough/` のそのほか (`boss.py`、`mobs_*.py`、`stone_whale*.py`、`weapons*.py`、`captain_v2.py` など) は、片付けた `docs/roughs/` にしか書かない。履歴として残している (ラフの中身は git の 7b69ce9 以前にある)。

## 作り直しの元データ (`tools/sources/`)

ラフのうち、生成スクリプトが入力に使う物だけをここに残す。

| 場所 | 中身 | 使うスクリプト |
|---|---|---|
| `sources/weapons/<item>/voxels.npz` | 武器 3 種の採用案 (B) の立体 | `tools/gen/items/gen_weapon_models_v2.py` |
| `sources/weapons/<item>/old_icon.png` | 古い 16x16 アイコン (`SKYABYSS_COPY_OLD_ICONS=1` のときだけ使う) | `tools/gen/items/gen_weapon_models_v2.py` |
| `sources/heart/phaseN.{geo.json,tex.png,glow.png}` | 心臓 3 段階の採用案 (A) | `tools/install/install_heart_v4.py` |

確認用の絵 (`install_*`、`tools/gen/items/gen_weapon_models_v2.py` が書く) は `docs/review/previews/` に出る。git には入れない。

## ラフの片付け

採用が決まった要素のラフは、その都度片付ける。残すのは次の物だけ。

- 生成スクリプトが入力に使う物 (`tools/sources/` へ移す)
- ユーザーがまだ選んでいない案 (`docs/review/*_roughs/`)。選ばれたら、比較画像 1 枚と選定理由 (書類) だけ残し、ほかは消す

下の `_roughs/` を書くスクリプトは、どれも `docs/review` のラフを入力に読まない (`--install` も、その場で作った案を書き出す)。消したラフはスクリプトを動かせば作り直せるので、`tools/sources/` へ移した物はない。


生成元のないもの (今の `src/` が正本): 番兵 (`bone_sentinel`) と盾、`textures/item/3d/bone_spike_arrow.png`、`sounds/**/*.ogg`。

## 消えた内容を含む残りの生成物 (流す前に外す)

流すと消えた素材・場所を作り直す / 飛ぶので、次の箇所を外してから流す。
- `tools/gen/mobs/gen_anemone_art.py`: 光るイソギンチャクの品の絵 (`item_{a,b,c}`) は生きているが、灯りの台の絵 (`pedestal_*`・`anemone_bloom`) は消えた。台の部分を外す。
- `tools/gen/items/gen_back_blocks.py`: 戻しの鏡 (`reset_mirror`) は登録されていない。絵とモデルだけ残っている。鏡の部分を外す。
- `tools/shots/whale_fix2.txt`・`whale_scale.txt`・`whale_board.txt`・`storm_scale.txt`・`storm_scale2.txt`: 室内の場所 (東の翼・肋・眼・回路・炉など) へ飛ぶ行があれば外す。

## 止めてあるもの (引退)

| スクリプト | 上書きしてしまうもの | 代わり |
|---|---|---|
| `tools/gen/items/gen_items.py` | `textures/item/*.png` 16 枚と `textures/models/armor/*` | `rough/items_blocks_32.py` |
| `tools/gen/items/gen_block_textures.py` | `textures/block/*.png` と `sky_key_fragment.png` | `rough/items_blocks_32.py` |
| `tools/gen/mobs/gen_mobs.py` | 番兵・射手・苔這い・顎・番兵長の geo / png / glowmask / animation | 上の表 |
| `tools/gen/world/gen_titan_model.py` | 岩鯨の近景 geo / png / glowmask / animation | `tools/install/install_stone_whale.py` |
| `tools/gen/world/gen_heart.py` | 心臓の animation (古い骨名に戻る) | `tools/install/install_heart_v4.py` |
| `tools/gen/items/gen_weapon_models.py` (v1) | 武器 3 本のモデルを古い押し出しモデルに戻し、途中で止まる | `tools/gen/items/gen_weapon_models_v2.py` |
| `rough/rib_archer_v3.py` | 射手の geo / animation を v3 に戻す | `rough/rib_archer_v4.py` |
| `rough/captain_v3.py --install` | 番兵長を v3 に戻す (`--install` なしなら docs にだけ書くので動く) | `rough/captain_v4.py --install` |
| `tools/gen/sound/gen_sky_sounds.py`、`tools/gen/sound/gen_titan_sounds.py` | `sounds/sky/*.ogg`、`sounds/titan/*.ogg` | なし (今の src が正本) |

`tools/gen/items/gen_weapon_models_v2.py` も止めてある。武器 3 本の `models/item/{whalebone_greatsword,captain_pike,waterway_fang}.json` と
パレット `textures/item/3d/*.png` (16x16) の生成元で、`SKYABYSS_ALLOW_REGEN=1` を付ければ今と同じ物ができる。
古い 16x16 アイコンでの上書き (`write_icon`) は、さらに `SKYABYSS_COPY_OLD_ICONS=1` を付けたときだけ行う。

## 自己テスト

`SKYABYSS_SELFTEST=1 ./gradlew runServer` (入口は `src/main/java/jp/revati/skyabyss/SelfTestRunner.java`。18 系統を 1 tick に 1 段ずつ流し、重い段のあとはサーバーが追いつくのを待つので、`max-tick-time` は既定の 60000 のままでよい。ログの `SELFTEST <名前> PASSED / FAILED` と最後の `SELFTEST PASSED / FAILED` を見る。`SELFTEST runner:` の行に段ごとの時間)。所要時間 (目安): 空の run/ で 146〜169 秒 (うちテスト 120〜143 秒、いちばん重い tick は 1章の空の峰で約 20 秒)、既存のワールド (本体の run/) で 76〜98 秒。

## 進捗と碑の生成

- `python tools/gen/story/gen_advancements.py`: この Mod の進捗の木 (`data/skyabyss/advancement/`)、背景の絵、lang の `advancement.skyabyss.*` を作る。何度でも動かせる。仕様は `docs/design/19_advancements_and_tablets.md`
- `python tools/gen/story/gen_tablet_texture.py`: 碑 (`skyabyss:tablet`) の絵 2 枚、ブロック状態、モデル、lang の名前を作る

| 岩鯨の背の島 (低ポリ案 C をブロックにした鯨、02 の 3.1 章): `data/skyabyss/titan_back/whale.bin` | `tools/gen/items/gen_titan_back_whale.py` (形は `tools/lib/lowpoly_whale_voxel.py` が案 C の近景メッシュから作る。`--fit` で部屋と殻の距離、`--preview` で上と横の色の地図)。構造の書き出しとメッシュの並べ描きは `tools/sheets/whale_back_c_views.py` | 同じ物ができる。変えたら配置の版 (`TitanBackLayout.LAYOUT_VERSION`) を上げる |
| 余燼の背の体 (低ポリ案 C「皮が岩になる火蜥蜴」をブロックにした火蜥蜴、13 の 3.2 章): `data/skyabyss/ember_back/body.bin` | `tools/gen/items/gen_ember_back_body.py` (形は `tools/lib/lowpoly_ember_voxel.py` が案 C の近景メッシュから作る。`--preview` で上・横・前の色の地図、`--cache` で `rough/_ember_back_body_cache.npz`)。メッシュとの並べ描きは `--cache` のあとで `tools/sheets/ember_back_c_views.py` | 同じ物ができる。変えたら配置の版 (`EmberBackLayout.LAYOUT_VERSION`) を上げる |

雷翼の背の鳥: `data/skyabyss/storm_back/bird.bin` は `python tools/gen/items/gen_back_shape.py bird [--preview]`。形・骨は `install_thunder_wing.build_grid()`、材料は低ポリ案 A の近景メッシュ (`tools/lib/lowpoly_thunder_voxel.py` が体積化。`recolor` で近いセルの材料を写す)。変えたら `StormBackLayout.LAYOUT_VERSION` を上げる。ブロックの鳥と案 A のメッシュの並べ描きは `tools/sheets/storm_back_a_views.py` (`SKYABYSS_DUMP=storm_back ./gradlew runServer` で書き出した `.sbd` を渡す)。

## 装備まわりの文と絵

- `python tools/gen/story/gen_gear_story.py` : 装備 40 点のツールチップの「物語の一行」(`assets/skyabyss_gear_story/lang/`)。03 の 3.4節
- `python tools/gen/items/gen_resonance_shards.py` : 巨神ごとの共鳴の欠片の絵 4 枚 (`textures/item/resonance_shard_<巨神>.png`。共通の欠片の色味を替える)。03 の 2.2節
- `python tools/gen/world/gen_grave_assets.py` : 墓の絵・モデル・語り部の墓の会話と文 (墓の一覧の画面の文も含む)。11 の 6.4節

### 上から落ちる影 (空の影・巨神の影。`client/sky/OverheadShadow`、設計書 06 の 2.4)

画素ごとに世界の位置で掛ける影の確かめ。台本 `tools/shots/overhead_shadow_pass.txt` (動かない空の影を `skypass` で置き、形を `skycircle` の円にして、影の下・屋根の下・木の下・戸口・影の縁を撮る) と `overhead_shadow_whale.txt` (家と木の上に岩鯨を浮かべる)。`_off` の台本は `overheadshadow off` = 昔の地面の板 (before)。台本の手順: `overheadshadow on|off`、`skypass <x> <z> <dirX> <dirZ>|off`、`skycircle <x> <z> <半径>|off`。同じ世界 (`overhead_shadow_pass`) を使い回す (`-PshotsName=osp`)。世界の新規作成は重くて固まることがあるので、固まったら java を PID で止めて、同じ台本をもう一度流す (2 回目は開くだけ)。比べの絵: `python tools/sheets/overhead_shadow_sheet.py <before の空の影> <after の空の影> <before の鯨> <after の鯨>` → `docs/review/previews/overhead_shadow_sheet.png`。岩鯨は `/summon` だと記録の個体と違って取り消されるので `skyabyss titan summon` で呼ぶ。

## 公式サイト (`site/`)

`python tools/site/gen_art.py`（ドット絵と素材の写し）→ `python tools/site/build.py`（HTML を書き出す）。`site/` はそのまま GitHub Pages に載る（`.github/workflows/pages.yml`）。技の木は `Skill.java` と lang から読むので、技を足したら build し直す。

## 文字のはみ出しの検査 (`python tools/audit/check_text_fit.py`)

日本語・英語の画面・HUD の文を、字体の幅の表で箱 (幅・行数) と照らす。GUI 倍率 2・3・4 (1920x1080 の窓)。箱の登録簿 `BOXES` は画面のコードから写した値なので、画面の寸法を変えたらここも直す。知らせ (`Notice.*`) は Java のソースから経路を拾って、字幕 3 行・中央 2 行・札 3 行・アクションバー 1 行などに照らす。失敗が 1 件でもあれば終了コード 1。引数の `%s` は 2 字、名前が入る箇所は余裕を見て箱を決めてある。

## 武器の動き (`tools/gen/items/gen_weapon_moves.py`・`tools/sheets/weapon_moves_sheet.py`)

- 武器ごとの動き (構え・連撃の各段のキーフレーム・当たる瞬間・ヒットストップ・画面の揺れ・軌跡・点検) は `src/main/resources/assets/skyabyss/weapon_moves/<品の id>.json`。**正は `tools/gen/items/gen_weapon_moves.py`** (直して `python tools/gen/items/gen_weapon_moves.py` で書き出す)。ゲームの読み込みは `weapon/MoveSet.java`、サーバーの「当たる tick」は `weapon/WeaponStrikes.java`
- 各段に `hit` の key が 1 つあり、その時刻 = 武器の `strike` (サーバーがダメージを入れる、振りの何割か)。自己テスト `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="weapon" ./gradlew runServer` が、同期・連撃・釣り合い・預けたクリックの当たる tick を見る
- 一人称の連続の絵 `python tools/sheets/weapon_moves_sheet.py` → `docs/review/previews/weapon_moves_sheet.png`。ゲーム内の確認は `tools/shots/weapon_moves.txt` (台本の `swingstep <段>`・`inspectpose <0..1>` が使える)
- 画面の揺れは設定 `weaponShake`、軌跡は `weaponTrail`。点検のキーは `[` (`KeyDefaults.WEAPON_INSPECT`)

## 開発の速い回し方 (エージェント向け)

- **コミット前の確認**: `bash tools/dev/quick_check.sh` (compileJava + check_generators + check_text_fit + 階段の検査があれば。ゲームは起動しない。`--no-java` で Python の確認だけ)。生成物を手で書き直さない (`check_generators.py` がずれを見つける)。
- **ゲームの起動は必ず枠を通す**: `bash tools/dev/mc_run.sh runServer` / `runShots ...`。枠は `MC_SLOTS` 個 (既定 4)、空き物理メモリが `MC_SLOT_MB` (既定 3500) を切ると取らずに待つ (待つあいだ 30 秒おきに状況を出す)。`gradlew runServer` を直接叩かない。
- **自己テストの絞り方** `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=<指定>[,<指定>…]`。指定は 名前の頭 (`"storm back"`) / `group:<タグ>` (`group:raid` `abyss` `ui` `story` `titan` `combat` `backs` `mobs` `world`) / `~<語>` (名前に含む。例 `~quest`)。合うものが無ければ段の一覧を出して止める。複数指定しても流れる順は全体の順。
- **段の名前の一覧**: `SKYABYSS_SELFTEST_LIST=1 ./gradlew --no-daemon --offline runServer` はワールドを読む前に `selftest_steps.txt` (run/ の中) と標準出力へ一覧とグループを出して終わる (枠は要らない。起動は数十秒)。
- **ワールドの雛形**: 背を建て済みの `run/world` を、同じソース (titan/ abyss/ story/ch5/ registry/ block/ data/ の中身) ごとに一度だけ作り、`mc_run.sh` が `run/world` が無いときに自動で写す。作るのは `bash tools/dev/world_template.sh make ["titan back (build)"]`、無効化は `SKYABYSS_NO_TEMPLATE=1`、キーは `... key`。対象のソースを変えると別の雛形になる (古い雛形で試して見落とさないため)。
- 自分が起動した java だけ PID で止める。`run/` の自分のワールドは終わったら消す。`gradlew --stop` は使わない。

