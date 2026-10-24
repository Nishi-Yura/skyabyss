# 引き継ぎ

「天と淵のあいだ」(NeoForge 1.21.1)。今の仕様は `docs/design/README.md` の索引から読む（コードが正）。未決は `docs/design/00_decisions_needed.md` にだけある。リポジトリは github.com/Nishi-Yura/skyabyss、作業の幹は `m1-sky-pass`、途中の枝は `wip/*`。版は `gradle.properties` の `mod_version`（今 0.5.4）。

## 1. 各システムの状態

| 系 | 状態 | 書類 |
| --- | --- | --- |
| 序盤（序章・一章前半・拍子・目的の帯・知らせ・場面） | できている。語りと静かなホラーを最初から | 06・17・18・20 |
| 世界の背骨・都・跡地・地図 | できている。都は設計図 `capital`（`tools/capital/`）、施設と係の人 15 役・案内人と灯りの道、四つの跡地は方位と距離の帯、M の旅の地図。W4（墨の洗い）は未着手 | 33・35 |
| 一〜四章の地上と支度 | できている（支度の段・ミニダンジョン・敵・品）。五章の支度は黒い鉄の器（ブレイズロッド 3・黒曜石 2） | 21・34 |
| 四体の巨神と背 | 背は外見だけ（水平 ×1.6・垂直 ×1.3）。体の中は建てない。到着の台に着くと順路が始まる | 02・08・12・13・26 |
| 順路（レイド） | 到着の台 → 歩・智・闘のレイドの部屋（`trial/raid`、巨神ごとに枠ごと 1〜2 部屋）→ 中ボスの間 → 心臓の間（区画 0 番、`InstanceArenas`）。周回は地上の祠から歌片で。一人でも全部の部屋を越えられる | 29・32・39 |
| 心臓・領域・遺物の選択 | 領域は三つの仕掛けを解くまで続く。止めると巨神は眠り、遺物は専用画面で選ぶ | 02・30・20 |
| 五章「深淵」 | 都の下の大坑道から。四つの層（骨・雷は探索、塩・灰は護送）、番人、クリア後の悪夢・遺品・果てない深み。帯 Lv 32〜50 | 36 |
| 巨神の子と旅・乗り物 | 笛で子を呼びパーティで運ぶ。乗り物は笛で呼ぶ 7 体（都の厩舎） | 09・28・38 |
| 成長・スキル・装備・転生 | 5 つの木＋旅人の技、天職、変異・紋の盤ほか。13 武器と炉。転生 Lv 50+n | 04・27・03・31 |
| 旅嚢・死・墓・パーティ・ギルド | 旅嚢、柔らかい死（墓）、倒れた仲間の蘇生、6 人のパーティ、プレイヤーのギルド | 22・11・10 |
| 戦いの調整 | 盾: ふつうの敵の傷は全部、大物と予告の技は正面で 6 割止める（`combat/ShieldRules`）。mod の雑魚は体力 8 割・与える傷 9 割（`combat/MobTuning`）。心臓の領域の傷 ×0.85 | 03・02 |
| モデル・絵・サイト | 低ポリのメッシュ（水密・関節は親に沈める）、平らな絵は 32×32。`site/` は日英のドット絵 | 15・16・tools/README |

## 2. 動かす・試す・撮る

- **鉄則**: `gradlew --stop` は使わない（ユーザーの試遊クライアントが落ちる）。いつも `--no-daemon`。自分で起動した java は PID で止める（ユーザーの `javaw.exe` と、ほかのエージェントのプロセスは止めない）。
- **ユーザーが試遊中のチェックアウト（Desktop\mod）ではビルド・編集しない**。確認は worktree で。
- 型検査: `./gradlew compileJava --offline --no-daemon`（枠は要らない）。
- 自己テスト: `SKYABYSS_SELFTEST=1 bash tools/dev/mc_run.sh runServer`（全体で約 30 分。枠とメモリの待ち、ポート、雛形のワールドは `tools/README.md` の「開発の速い回し方」。絞るのは `SKYABYSS_SELFTEST_ONLY`）。終わったら `run/` のワールドを消す。
- 生成スクリプトとデータの同期: `PYTHONPATH=tools/lib python tools/audit/check_generators.py`（ドリフト 0 を保つ）。
- 絵の確認: `bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/<台本>.txt -PshotsName=<名>`。最後の行が `SHOTS OK` なら成功。OS のキー送り・フォーカス操作はしない。
- 試遊用コマンド: `/skyabyss play …`（`docs/design/37`・`docs/playtest_commands.md`）。
- 通しの試遊（全クリア・二人・周回の時間）はユーザーがする。

## 3. 配置の版（`LAYOUT_VERSION`。上げると古い島を消して建て直し、進みは戻る。初回の記録 `keep.*` は残る）

| 巨神 | 版 |
| --- | --- |
| 岩鯨 `TitanBackLayout` | 34 |
| 潮殻 `TideBackLayout` | 34 |
| 雷翼 `StormBackLayout` | 35 |
| 余燼 `EmberBackLayout` | 34 |

上げるときは、ほかの背の最大の次の番号にし、`docs/design/26` に書く。

## 4. 見る場所・選ぶ場所

- 選定ページは `docs/review/<題>/index.html`。ラフの比較は artifact `https://claude.ai/artifact/EqFcC7E183D8uyyAo8yKMM`（`tools/rough/build_gallery.py <manifest.json>`）。選びは db の collection `choices`（`ArtifactData` で読む）。選び終えたラフは消す（入力と選ばれなかった案は `tools/sources/` と `docs/review/*_roughs/`）。
- 今の状態のまとめ: `docs/review/status_2026-10-24.md`、クエストの流れ `quest_flow_2026-10-23.md`、設計の見直し `fun_review_2026-10-23.md`、試遊の確かめ順 `playtest_0.5.4.md`、夜の作業のまとめ `overnight_2026-10-24.md`。
- 試遊の声と判断の経緯はメモリ（`MEMORY.md` の索引）。設計書には経緯を書かない。

## 5. 残っている課題（2026-10-24 昼、PC の再起動の前に書いた）

- **0.5.4**: jar を作って渡した（`build/libs/skyabyss-0.5.4.jar`、41.7 MB）。`docs/review/playtest_0.5.4.md` の順で試遊してもらう。声を受けて直す。
- **途中の枝（再起動で止めた。続きから）**:
  - `wip/skill-fx3`: 技の見た目の三周目。光の面で形を描く道具 `client/skill/fx/FxDraw`（弧・線・輪・円盤・扇・柱・稲妻・殻）と 25 技の段取り、撮影の台本 `tools/gen/shots/gen_skill_fx_shots.py` → `tools/shots/skill_fx.txt`、台紙 `tools/sheets/skill_fx_sheet.py` → `docs/review/previews/skill_fx_sheet.png`。直す所: 輪が点線・柱が横縞のはしごに見える（貼り方か区切りの隙間）、的のまわりの白い四角い粒（バニラの当たりの粒か）、砂の突進の画面の端の四角、雲穿ち・合図の標・天の驟雨・火の矢の三人称が弱い。全 25 技が一コマで読めるまで直してから本線へ。
  - `wip/raid-rewards`: レイドの部屋の報いに素材・共鳴の欠片・称号を足す（ユーザーが決めた）。途中の状態は枝の最後のコミットを見る。
- **決まったこと（10-24）**: 字は DotGothic16（英数も）と Zen Antique、斜体は C（mod の字のとき斜体をやめ、場所の副題に暗い帯）、技の画面の枠の組は「近接」/「弓」、二〜四章の道しるべの石標はやめた、届かない雷翼の試練の階の文は消した。
- **実機で確かめていないもの**: 心臓の戦いに勝つまで、台座で転生を実行するまで、順位の石・飾り板の複数人での表示（自己テストは通る。`docs/review/verify_ch5_2026-10-24.md`）。
- **測っていないもの**: 成体の巨神の休む場所の探しの最悪の場合、`TitanEnd.landing` の潮殻の分岐の同期の区画生成（`docs/review/call_perf_2026-10-24.md`）。
- **潜水服の鎧**: 腕を真上に上げると兜に当たる（兜が頭より大きいので鎧では直せない。受け入れた）。
- **古い四角い模型**: `tools/sources/geo_cube/` へ移した（jar に入れない）。撮影で比べるときは `python tools/dev/cube_geo.py back`、終わったら `out`。生成スクリプトの多くは今も `src/main/resources` へ書き戻すので、流したあとは `out` を忘れない。

## 6. 作業の決まり（短く）

- 設計書は現在の仕様だけを書く。00 は未決だけ（答えが出たら本文へ書き、付録の引き当てに番号を残す）。`docs/design/README.md` の索引を更新する。
- 大きな仕事は担当ファイルを分けて並列に（同時 3 つまで）。各エージェントにトークン予算（grep・offset、ログは tail、狙った自己テスト）と短い日本語の報告を求める。
- 衝突しやすいもの: `00_decisions_needed.md`、`sounds.json`・lang（キーは和集合。生成スクリプトも同時に直す）、自己テストのポート。
- 絵は 32×32、ラフ 3 案から、重ねない、武器とモブのモデルは別。モデルは閉じた立体で、関節の部品は親に沈める。
- 時間・天気・背の状態で試練を閉じない。
- README とサイトの絵は手作りのドット絵。
- セッションの終わりが近いときは、この文書を更新し、`m1-sky-pass` と `wip/*` を push して止める。

## 7. 今の状態（2026-10-24 未明）

- 本線 `m1-sky-pass` に、背の外見化（体の中・試練のプール・セル・甲板を消し、部屋と中ボスの間と心臓の間を区画へ）、レイドの部屋 24 と中ボスの間 4 つの建築、心臓とボスの残り（ひるみ・第二段階の絵・羽の雨・岩鯨の子・灰の王の構え）、会話の顔 35 人の描き直し、深淵の装備の作り直しと潜水服の鎧、レア度 6 段、一章の積み石の削除、羽の鏡の心臓の間の入口への移動が入っている。
- 枝 `wip/final-tidy`（10-24）: 背の中の中ボス戦（`RaijuBattle`・`RaijuCommands`・`RaijuState`・`AshSmithBattle`・`AshSmithCommands`）と、体の中の休み場の表（残すのは心臓の前室と雷翼の尾の広場）、戦い中の前室の決まり（`respawnCheckpoint`）、谷落ちの戻し、使われない文 92 件と封印・イソギンチャクの絵を消した。`/skyabyss play boss raiju|smith` は順路の中ボスの間へ運ぶ。`tools/shots/` は台本だけにした（台紙は `tools/sheets/`、`heart_fx` の台本作りは `tools/gen/shots/gen_heart_fx_shots.py`）。
