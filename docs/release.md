# 配布の手順（試遊版）

試遊版の jar を作って渡すときの順。どれもリポジトリ直下で流す。設計は [01](design/01_overview.md) の 9 章（M6 仕上げ）。

## 1. 版を上げる

- `gradle.properties` の `mod_version` を上げる（例 0.9.18 → 0.9.19）。
- 同じ数字に直す所: `README.md`（jar の名前・末尾の「ベータ版（…）」）、`docs/beta_how_to_play.md`（jar の名前・冒頭の「… の試遊では」）、`docs/design/01_overview.md`（9 章の「試遊版は」）、`docs/design/14_beta.md`（1 章）、`docs/HANDOFF.md`（「今の形」の試遊版の行）。
- サイトの変更履歴 `tools/site/changelog.py` の `ver` に版の塊を足す（日本語と英語の対）。`python tools/site/build.py` で `site/*.html` を作り直す（版の数字は `gradle.properties` から入る）。

## 2. 確かめる

```bash
bash tools/dev/quick_check.sh
```

compileJava・資源の参照と看板の幅・lang の鍵・メッシュの水密・生成物のずれ・文字のはみ出し・階段。ひとつでも落ちたら直してから進む。

```bash
bash tools/dev/selftest_fresh.sh
```

自己テスト全段を新しい大陸ワールドで流す（約 25 分）。`run/logs/latest.log` の `SELFTEST … FAILED` が 0 で、`all steps done` が出ること。急ぐときは変えた所の段だけ（`bash tools/dev/selftest_fresh.sh "cloudsea,skills"`）。

**本番の形での起動確認**（配る jar を、開発用でない NeoForge で動かす。2026-11-02 に始めた）:

1. リポジトリの外のフォルダ（例 `C:\Users\<名前>\skyabyss_prod`）に、NeoForge のインストーラー（maven.neoforged.net の `neoforge-<neo_version>-installer.jar`）で `--installServer server` と `--installClient client`（先に `client/launcher_profiles.json` に `{"profiles":{}}`）。
2. 両方の `mods/` に `build/libs/skyabyss-<版>.jar` と GeckoLib の jar（gradle の置き場の `geckolib-neoforge-1.21.1-<版>.jar`）。サーバーは `eula=true`、`server.properties` に `online-mode=false`・`level-type=skyabyss\:continent`・`server-port=25599`。
3. サーバーを起動して `Done` と、記録の `command gate: … developer commands hidden` を見る。
4. クライアントは `python -X utf8 tools/dev/prod_client.py <client> --script tools/shots/prod_check.txt`（一人遊びで大陸の世界）と `--script tools/shots/prod_check_mp.txt`（サーバーへ入る）。撮影の台本は本番の jar では `-Dskyabyss.prodcheck=true` のときだけ動く（`prod_client.py` が付ける）。絵は `<client>/shots/<台本>/`。
5. 相性: よく使われる mod（JEI・Sodium・Jade・Shoulder Surfing Reloaded など。Modrinth の NeoForge 1.21.1 版）を足して `tools/shots/prod_check_compat.txt`。Shoulder Surfing が入っているときは、この mod の肩越しは使わない。

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

製品版のコマンド（`dev/CommandGate`。開発用のコマンドは開発環境でだけ出る）:

- 誰でも: `/skyabyss help`（一覧）、`/party`・`/guild`・`/trade`・`/skyrecord`・`/growth`・`/skill`・`/skyabyss_codex`・`/skyabyss_journal`・`/skyabyss_fishing`・`/skyabyss_look`・`/skyabyss_arts`・`/skyabyss_tower`・`/skyabyss_circuit`。
- 管理者: `/skyabyss admin …`（一覧は `/skyabyss admin help`）。使える権限の段はサーバーの設定 `serverconfig/skyabyss-server.toml` の `adminPermissionLevel`（既定 2 = OP。シングルプレイでは「チートの許可」をオンにした世界）。前の `/skyabyss play` は開発の別名で、製品版では隠れる。

## 5. 版を上げるとき（前の版の世界を引き継ぐ）

前の版で遊んでいた世界は、新しい版の jar に替えてもそのまま開ける。版の間で変わったデータは、次の仕組みで移す（2026-11-02 の声「アップデートで、mod の ver が上がって変更しても対応できるようにできるの？」）。

| 変わる物 | 移し方 |
| --- | --- |
| 世界のデータ（保存の中身・決まり・建て直しの印） | `upgrade/WorldUpgrade`。世界ごとに「データの版」（`data/skyabyss_world_version.dat`）を持ち、サーバーが始まったとき、それより新しい段を古い順に一度ずつ流す。新しい段は `CURRENT` を一つ上げて `STEPS` の終わりに足す。段は何度流しても同じ結果になるように書く。一つの段がこけても世界は開け、版は進めずに次に開いたときもう一度流す |
| 人のデータ（持ち物・人の印） | `upgrade/PlayerUpgrade`。人の永続データ `skyabyss_data_version`（倒れても残す `util/LifeKeep`）。入ったときに流す。足し方は世界と同じ |
| 大陸の地上（地形・道・木・建物の置き場） | `upgrade/ContinentRegen`。大陸の地上は Mod が作る物だけ（人はブロックを置けず壊せない）なので、まるごと作り直せる。地形・道・建物の置き場所の決まりを変えたら `VERSION` を一つ上げる。前の版の世界を開くと、地上を読む前に地上のチャンクの保存（`region`・`entities`・`poi`）を `skyabyss_backup/overworld_v<前の版>/` へ移し（前の控えは消して一つだけ残す）、新しい世界と同じに作り直す。開いたあと、建て物の置き場をすべて捨て（野営地の場所だけ残す）、新しい世界と同じに置き場から決め直して建てる（都・町・島・船着き場・外れの集落・見どころ・章の跡地と小要素・祠は今の場所に・沖の難破船。跡地を見た・章の小要素に着いた記録は引き継ぐ `SiteData.clearForRegen`）。巨神は居場所へ出し直し、地上の墓は印のブロックを置き直す。語り部・鯨追い・職人は地上の生き物ごと消えるので、立たせた時刻の記録も消して、近づいたときにすぐ立て直す（`NpcWorldData.forgetAll`。1.1.0 で作り直した世界は世界の直しの段 2 で同じことをする）。版が上がってから初めて入った人は、読み込み画面が終わったときに立ち位置を直す（`ContinentRegen.settle`）: 地面の中なら上の空いた所へ、足の下 3 までに地面が無く下が陸ならその上へ、海・湖の上や水の中なら近くの陸（まわり 96 まで）へ、陸が無ければ起き上がる場所へ。人の大陸の版の印は倒れても残す（`util/LifeKeep`）。開発の確かめは `/skyabyss dev regen settle`。進み（クエスト・持ち物・見つけた見どころ・伝承・巨神の進み・墓の中身）は保存のデータにあるのでそのまま。版の記録は世界のフォルダの `skyabyss/continent_version.txt`。大陸でない世界は作り直さない。作り直す前の姿を見るときは `SKYABYSS_NO_REGEN=1`。作り直したあと初めて入る人（版が上がってから初めての人と、その起動で作り直した世界に初めて入る人）には、まわり 6 チャンクの地形と、まわり 200 の建物（都・町・野営地…。待つ間は先に建てる）がそろうまで読み込み画面を出す（`upgrade/RegenLoading`・`client/upgrade/RegenLoadingScreen`。最長 150 秒。待つ間は傷つかず落ちない）。撮影 `tools/shots/regen_loading.txt`（建てるのを見るときは `SKYABYSS_OPENING_SITES=1`） |
| 建物（町・跡地） | 大陸の地上を作り直さない小さな直しは、その仕組みの「作りの版」の印で建て直す（土地の町は `ContinentTowns.VERSION_MARK`。上げると、人が近づいたときに建て直す）。巨神の背・塔・深淵などの別の世界は、それぞれの作りの版で建て直す（巨神の背は `*BodyBuilder.buildVersion`、深淵の大縦穴は `AbyssBuild.VERSION`） |
| 都の係の人の立ち位置・町の印 | 今の設計図の印から読む（`facility/Attendants.capitalSpots`・`town/TownGuests.marks`）。設計図の印が立てる床なら新しい位置、立てなければ建てたときの位置。あとから足した印（町のクエストの家など）は設計図から足す |
| 品・ブロック・敵の ID | 消した ID は、世界を開くと持ち物からも消える（NeoForge が外す）。名前を替えるときは消さずに `DeferredRegister.addAlias(古い ID, 新しい ID)` で古い ID を新しい物へつなぐ |
| チャンクが要る直し | 段では直す印だけ立て、人が近づいたとき各仕組みが直す（例: 島の峠の柵は `IslandTrail.clearOldGate`、厩舎の名札は `ShelterBuilder.keepLabels`） |

- 新しい版で保存した世界を古い版で開くと（データの版が `CURRENT` より大きい）、記録に知らせを残し、管理者が入ると注意を出す。版は下げない。
- 版の確かめ: `/skyabyss admin version`（Mod の版・世界と自分のデータの版・最後に開いた Mod の版）。
- 版を上げる前に、前の版で遊んだ世界（`run/` の古い世界の写し）を新しい jar で開き、記録（`run/logs/latest.log`）の `World upgrade:` と `Player upgrade:` の行と、町・係の人・クエストの進みが残っていることを見る。大陸の地上を作り直す版では、`Continent regen:` の行と、都・町が建ち直ることも見る（`tools/shots/regen_check.txt`。撮影では場所の建てが止まっているので `SKYABYSS_OPENING_SITES=1` を付ける）。

## 6. 記録

- `docs/HANDOFF.md` の先頭に版の節（何を変えたか・確かめたこと・次）。
- `docs/review/playtest_<版>.md` を足すのは、試遊で見る所が前の版と変わるときだけ。
- コミットして `m1-sky-pass` を push。

## 7. 正式版（1.0）へ

2026-11-02 に決めた（00 の 362・363）:

- 次の版は 0.9.23 を**候補版**として出す。通しの試遊（終章と、あいだの塔を 1 人と 4 人で）と全段の自己テストのあとで 1.0.0 にする。
- 公開サイト・README・遊び方の「ベータ版」の表記、遊び方に残る古いコマンドの案内（`/skyabyss titan` ほか）、サイトの更新履歴は、1.0.0 を出すときにまとめて直す（サイトは今 0.9.20）。
- 2026-11-02 の夜に 1.0.0 にした（声「その後に 1.0.0 いこう」）。候補の 0.9.23 のあと、本番の形での起動確認・相性・英語の見直しをして、通しの試遊の前に上げた。遊んで出たものは 1.0.x で直す。表記・コマンドの案内・更新履歴は上のとおり直し、サイトを作り直した。

