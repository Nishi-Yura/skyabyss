<p align="center">
  <img src="c_title.png" alt="天と淵のあいだ　BETWEEN THE SKY AND THE ABYSS　v0.2.2">
</p>

<br>

<p align="center">
  空を、何かが通った。<br>
  山より大きな影が、雲の上を、音もなく。
</p>

<p align="center">
  <img src="c_orn.png" alt="">
</p>

<p align="center">
  Minecraft Java 版 1.21.1（NeoForge）向けの RPG mod。<br>
  建築も採掘も、いつものままに。<br>
  そこへ、怖さと冒険と、少しずつ解けていく謎が重なります。
</p>

<br><br>

<p align="center">
  <img src="c_four.png" alt="一章 岩鯨（空を泳ぐ）、二章 潮殻（海を歩く）、三章 雷翼（嵐をまとう）、四章 余燼（地を泳ぐ）">
</p>

<p align="center">
  巨神 1 体につき 1 章。<br>
  その背に乗り込めば、背中はそれ自体がダンジョンです。
</p>

<br><br>

<p align="center">
  <img src="c_pack.png" alt="Minecraft 1.21.1、NeoForge 1.21.1、GeckoLib 必須、Java 21。みんなで遊ぶなら、同じ jar を。">
</p>

<p align="center">
  <code>skyabyss-0.2.2.jar</code> と GeckoLib を <code>mods</code> フォルダへ。<br>
  サーバーにも、同じものを。<br>
  <sub>新しいワールドで、ピースフル以外の難易度で。6 人まで。</sub>
</p>

<br>

<p align="center">
  <kbd>J</kbd> ステータス　<kbd>K</kbd> 技　<kbd>U</kbd> クエストログ　<kbd>B</kbd> 控え帳<br>
  <kbd>O</kbd> パーティ　<kbd>H</kbd> 護符　<kbd>N</kbd> 知らせの記録　<kbd>R</kbd> <kbd>G</kbd> <kbd>V</kbd> <kbd>Z</kbd> 技の枠
</p>

<br><br>

<p align="center">
  <img src="c_end.png" alt="空と地の底のあいだで、あなたを待っているものがあります。">
</p>

<br>

<p align="center">
  <sub>ベータ版 v0.2.2　序章から天の鍵がそろうところまで</sub>
</p>

<details>
<summary align="center">サーバーの管理者へ</summary>

<br>

`/gamerule` で決まりを変えられます。

| ゲームルール | 内容 |
| --- | --- |
| `skyabyssStoryGate` | `false` で巨神の順番と乗る資格の決まりを切る |
| `skyabyssStoryLevelCap` | `false` で物語の進みによるレベル上限を切る |
| `skyabyssPartyShare` | その場にいなかった仲間への経験値の分け前（50〜80 %） |
| `skyabyssDeathMode` | 倒れたときの型（0 墓 / 1 バニラ / 2 全部残す / 3 耐久だけ減る。既定 0） |
| `skyabyssHeartReentryLimit` | 戦いで倒れた人が戻れる回数（既定 3） |

墓まわり（`skyabyssGrave*`）のルールと、詰まったときの `/skyabyss` コマンドは [beta_how_to_play.md](../../../beta_how_to_play.md) に。

</details>

<details>
<summary align="center">既知の問題</summary>

<br>

マルチプレイは、まだ人の手で通しの確認をしていません。特に、2 つのパーティが同じ背中にいるときの動きです。雷翼と余燼では、巨神に乗って移動する旅がまだできません。世界を開いた直後の数分は、背中の島を建てているので動きが重いことがあります。五章以降は、まだ枠だけです。

気づいたことがあれば、ぜひ教えてください。

</details>

<details>
<summary align="center">クレジット</summary>

<br>

作：Nishi-Yura　／　ライセンス：All Rights Reserved　／　アニメーションに [GeckoLib](https://github.com/bernie-g/geckolib) を使っています。

</details>
