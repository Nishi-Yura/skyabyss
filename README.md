<p align="center">
  <img src="docs/readme/plate_title.png" width="840" alt="天と淵のあいだ　BETWEEN THE SKY AND THE ABYSS　空を追う人の旅の手帳　v0.2.2 ベータ版">
</p>

<p align="center">
  <img src="docs/readme/plate_tagline.png" width="840" alt="空を、何かが通った。山より大きな影が、雲の上を、音もなく。Minecraft Java 版 1.21.1（NeoForge）向けの RPG mod。建築も採掘も、いつものままに。そこへ、怖さと冒険と、少しずつ解けていく謎が重なります。">
</p>

<p align="center">
  <img src="docs/readme/plate_chapters.png" width="840" alt="巨神 1 体につき 1 章。一章 岩鯨（空を泳ぐ）、二章 潮殻（海を歩く）、三章 雷翼（嵐をまとう）、四章 余燼（地を泳ぐ）。巨神の背は、それ自体がダンジョンです。">
</p>

<p align="center">
  <img src="docs/readme/plate_pack.png" width="840" alt="旅の支度。Minecraft Java 版 1.21.1、NeoForge 1.21.1、GeckoLib（NeoForge 1.21.1 用）、Java 21。skyabyss-0.2.2.jar と GeckoLib を mods フォルダへ入れる。サーバーなら、サーバーの mods フォルダにも。新しいワールドで始める。難易度はピースフル以外。一人でも、仲間と 6 人までで遊べます。みんなで遊ぶなら、同じ jar を。">
</p>

<p align="center">
  <img src="docs/readme/plate_keys.png" width="840" alt="手の動き。J ステータス、K 技、U クエストログ、B 控え帳、R G V Z 技の枠 1 から 4、O パーティ、H 護符を使う、N 知らせの記録。控え帳は巨神の記録・墓の場所・大事な品。迷ったら画面の目的の帯か U キーを見る。キーは設定の操作で変えられます。">
</p>

<p align="center">
  <img src="docs/readme/plate_admin.png" width="840" alt="見張り番の頁、サーバーの管理者向け。ゲームルールは skyabyssStoryGate、skyabyssStoryLevelCap、skyabyssPartyShare、skyabyssDeathMode、skyabyssGraveKeepGear、skyabyssGraveOwnerOnly、skyabyssGraveExpire、skyabyssGraveRescue、skyabyssDeathDurability、skyabyssHeartReentryLimit。OP 用の /skyabyss コマンドは opening reset、cinematic reset、titan reset、playtest。ほかは docs/playtest_commands.md。">
</p>

<details>
<summary>管理者向けの表（コピーできる文字版）</summary>

`/gamerule` で決まりを変えられます。

| ゲームルール | 内容 |
| --- | --- |
| `skyabyssStoryGate` | `false` で巨神の順番と乗る資格の決まりを切る（好きな順に乗れる） |
| `skyabyssStoryLevelCap` | `false` で物語の進みによるレベル上限を切る |
| `skyabyssPartyShare` | その場にいなかった仲間への経験値の分け前（50〜80 %） |
| `skyabyssDeathMode` | 地上で倒れたときの型（0 墓 / 1 バニラ / 2 全部残す / 3 耐久だけ減る。既定 0） |
| `skyabyssGraveKeepGear` | 墓の型で、装備中の防具を持って復活する（既定 true） |
| `skyabyssGraveOwnerOnly` | 墓を開けられるのは持ち主だけ（既定 true） |
| `skyabyssGraveExpire` | 墓が消えるまでの分（0 で消えない） |
| `skyabyssGraveRescue` | 語り部の救いで戻る割合（％。0 で救いなし） |
| `skyabyssDeathDurability` | 耐久だけ減る型での減る割合（％） |
| `skyabyssHeartReentryLimit` | 戦いで倒れた人が、20 秒のあとに戻れる回数（既定 3。0 で数えない） |

OP（権限 2）用の `/skyabyss` コマンド:

| コマンド | 働き |
| --- | --- |
| `/skyabyss opening reset <名前>` | 序章を、もう一度遊べるように戻す |
| `/skyabyss cinematic reset <名前>` | 見た扱いの場面を戻す |
| `/skyabyss titan reset <titan>` | その巨神の進みを戻す（例: `/skyabyss titan reset ember`） |
| `/skyabyss playtest now` | 今の目的の出来事を、その場で起こす |
| `/skyabyss playtest next` | 今の目的を済ませて、次へ進める |

試遊用コマンドの全部は [docs/playtest_commands.md](docs/playtest_commands.md) にあります。設定ファイルでは、不穏な出来事の頻度を変えられます（`horrorEventFrequency` など。0 で出なくなります）。

</details>

<p align="center">
  <img src="docs/readme/plate_issues.png" width="840" alt="まだ書きかけの頁。マルチは、まだ人の手で通しの確認をしていません（特に 2 つのパーティが同じ背中にいるとき）。雷翼と余燼では、巨神に乗って移動する旅がまだありません。序盤の演出・予兆・仕掛けの濃さは、通しで確かめていません。余燼の背中は、人が通しで遊んでいません。噛み合わない所は教えてください。開いた直後の数分は、背中の島を建てるので少し重くなります。風見鶏と潮の真珠の絵は仮のものです。五章以降は、まだ枠だけです。気づいたことは、ぜひ教えてください。">
</p>

<p align="center">
  <img src="docs/readme/plate_end.png" width="840" alt="空と地の底のあいだで、あなたを待っているものがあります。">
</p>

<p align="center">
  <img src="docs/readme/plate_credits.png" width="840" alt="奥付。作 Nishi-Yura。All Rights Reserved。巨神の動きに GeckoLib を使っています。">
</p>

<p align="center">
  <sub>詳しい遊び方：<a href="docs/beta_how_to_play.md">docs/beta_how_to_play.md</a>　／　ベータ版の範囲：<a href="docs/design/14_beta.md">docs/design/14_beta.md</a><br>
  <code>docs/design/</code> の設計書には物語の核心が書かれています。まっさらな気持ちで遊びたい人は開かないでください。</sub>
</p>
