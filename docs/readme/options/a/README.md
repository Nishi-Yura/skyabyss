<p align="center">
  <img src="cover.png" alt="天と淵のあいだ　空を追う人の旅の手帳　BETWEEN THE SKY AND THE ABYSS　v0.2.2">
</p>

<p align="center">
  Minecraft Java 版 1.21.1（NeoForge）向けの RPG mod です。<br>
  建築も採掘も、いつものマイクラのまま。<br>
  そこに、怖さと冒険と、少しずつ解けていく謎が重なります。
</p>

<br>

<p align="center">
  <img src="page_sky.png" alt="一頁。雲の上にて。空を、何かが通った。山より大きな影が、雲の上を、音もなく。遠くの煙、響く鐘、巨大すぎる足跡。">
</p>

<p align="center">
  目を開けると、そこは見知らぬ野営地。<br>
  人が去った跡と、一冊の古い帳面だけがあります。<br>
  帳面を拾い、煙と鐘の音をたどって、あなたは旅を始めます。
</p>

<br>

<p align="center">
  <img src="page_titans.png" alt="二頁。出会った四体。一章 岩鯨（空を泳ぐ）、二章 潮殻（海を歩く）、三章 雷翼（嵐をまとう）、四章 余燼（地を泳ぐ）">
</p>

<p align="center">
  空を泳ぎ、海を歩き、嵐をまとい、地の底を泳ぐ。<br>
  四体の巨神に出会い、その背に乗り込みます。<br>
  背中はそれ自体がダンジョン。三つの試練と、体の奥で待つ守護者が、あなたを待っています。<br>
  物語は巨神 1 体につき 1 章。仲間とは 6 人まで、同じ旅ができます。
</p>

<p align="center">
  <sub>ベータ版（v0.2.2）です。序章と四つの章を越えて、「天の鍵」がそろうところまで遊べます。</sub>
</p>

<br>

<p align="center">
  <img src="page_pack.png" alt="三頁。旅の支度。Minecraft Java 版 1.21.1、NeoForge 1.21.1、GeckoLib（NeoForge 用）、Java 21。仲間と行くなら、みんな同じ jar を。版が違うと入れません。">
</p>

<p align="center">
  <code>skyabyss-0.2.2.jar</code> と GeckoLib の jar を、どちらも <code>mods</code> フォルダへ。<br>
  サーバーで遊ぶなら、サーバーの <code>mods</code> にも同じものを入れます。<br>
  新しいワールドで、ピースフル以外の難易度で始めてください。<br>
  <sub>ソースから作るときは <code>./gradlew build</code>（<code>build/libs/</code> にできます）。</sub>
</p>

<p align="center">
  最初に世界へ入ると、暗い画面から始まります。少し待ってください。<br>
  迷ったら画面の「目的の帯」を見るか、<kbd>U</kbd> キーでクエストログを開きます。
</p>

<br>

<p align="center">
  <img src="page_keys.png" alt="四頁。手の動き。J ステータス、K 技、U クエストログ、B 控え帳、O パーティ、H 護符を使う、N 知らせの記録、R G V Z 技の枠。キーは設定の「操作」で変えられる。">
</p>

<br>

<details>
<summary><img src="page_admin.png" alt="見張り番（サーバーの管理者）の頁"></summary>

<br>

`/gamerule` で決まりを変えられます。

| ゲームルール | 内容 |
| --- | --- |
| `skyabyssStoryGate` | `false` で巨神の順番と乗る資格の決まりを切る |
| `skyabyssStoryLevelCap` | `false` で物語の進みによるレベル上限を切る |
| `skyabyssPartyShare` | その場にいなかった仲間への経験値の分け前（50〜80 %） |
| `skyabyssDeathMode` | 地上で倒れたときの型（0 墓 / 1 バニラ / 2 全部残す / 3 耐久だけ減る。既定 0） |
| `skyabyssGraveKeepGear` | 墓の型で、装備中の防具を持って復活する（既定 true） |
| `skyabyssGraveOwnerOnly` | 墓を開けられるのは持ち主だけ（既定 true） |
| `skyabyssGraveExpire` | 墓が消えるまでの分（0 で消えない） |
| `skyabyssGraveRescue` | 語り部の救いで戻る割合（％。0 で救いなし） |
| `skyabyssDeathDurability` | 耐久だけ減る型での減る割合（％） |
| `skyabyssHeartReentryLimit` | 戦いで倒れた人が、20 秒のあとに戻れる回数（既定 3） |

詰まったときのためのコマンドが `/skyabyss` にそろっています（巨神を呼ぶ、背中から戻す、場面を流す、進みを戻す、など）。一覧とテスター向けの手順は [beta_how_to_play.md](../../../beta_how_to_play.md) にあります。

</details>

<details>
<summary><img src="page_torn.png" alt="まだ書きかけの頁"></summary>

<br>

作りかけの部分があります。気づいたことがあれば、ぜひ教えてください。

マルチプレイは、まだ人の手で通しの確認をしていません。特に、2 つのパーティが同じ背中にいるときの動きです。雷翼と余燼では、巨神に乗って移動する旅がまだできません。序盤の演出や章の小さな仕掛けは、濃さ（出すぎる・気づかない）の感想をください。世界を開いた直後の数分は、背中の島を建てているので動きが重いことがあります。五章以降は、まだ枠だけです。

</details>

<br>

<p align="center">
  <img src="page_end.png" alt="空と地の底のあいだで、あなたを待っているものがあります。">
</p>

<p align="center">
  <sub>
    作：Nishi-Yura　／　ライセンス：All Rights Reserved<br>
    アニメーションに <a href="https://github.com/bernie-g/geckolib">GeckoLib</a> を使っています。
  </sub>
</p>
