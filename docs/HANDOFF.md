# 作業の引き継ぎ（2026-10-18 時点）

別の PC からすぐ再開するためのメモ。区切りがついたら、このファイルを書き直す。

## 0. 再開の手順

1. `git fetch origin` → `git checkout m1-sky-pass` → `git pull`
2. 途中の作業（wip ブランチ）は今は無い。前回の 4 本は 1 章のとおり m1-sky-pass に取り込んだ。origin に残っている `wip/*` の 5 本（per-party-progress・titan-defeat-ending・ember-mouth-whale-fins・armor-install・weapon-install）は、どれも取り込み済みなので消してよい。
3. ビルド: `./gradlew compileJava --offline`（初回は `--offline` を外す）。
4. 自己テスト: `SKYABYSS_SELFTEST=1 ./gradlew runServer`（一部だけは `SKYABYSS_SELFTEST_ONLY=<名前の頭>`）。**新しいワールドと `level-seed=4242`** で回す（古いワールドは建物の配置が古くて落ちる段がある）。今は 58 段ぜんぶ PASSED（約 6 分）。
5. 設計書は `docs/design/README.md` から。未決の一覧 `docs/design/00_decisions_needed.md` は今は空（全部回答済み、変えた答えは付録に「（実装中）」付き）。

## 1. 前回の途中の作業（取り込み済み）

クラウドの環境（Linux）で続けて、m1-sky-pass に取り込んだ。どれも全体の自己テストが通っている。台本の撮影は Xvfb（`xvfb-run -a ./gradlew runShots ...`）で動いた。

| 元のブランチ | 状態 | 残り・注意 |
| --- | --- | --- |
| `wip/per-party-progress` | 4 体の背中とも、進みをグループ（パーティ、または一人）ごとに。マルチの M4〜M7 も済み。設計書 02/08/10/12/13 も今の仕様。余燼の背の自己テストが落ちていた（一度も背中で見ていない記録を放置と見なして、すぐリセットしていた）のを `GroupProgress.idleGroups` で直した | マルチの実機の確認（2 グループが同じ背中にいるとき）は人の目でまだ |
| `wip/titan-defeat-ending` | 眠り・運び出し・初回だけの場面・目覚め・乗り込み口の光・岩鯨の到着の台、と自己テスト `titan end` まで済み。グループごとの背中と合わせた: 心臓を止めたグループで `TitanEnd.onHeartStopped`、どれかのグループの試練が戻ると `TitanDormancy.requestWake`（最短 90 秒は守る） | 一つのグループが心臓を止めると、巨神はみんなにとって眠る（ほかのグループは背中に残るが、90 秒以上は新しく乗れない）。気になれば未決 200 番から |
| `wip/ember-mouth-whale-fins` | 余燼 C の口の空洞（舌 y 6・上あご y 12、奥は喉の壁）と、岩鯨の胸びれの回転の中心 ±28.5 は済み。ひれのアニメは左右対称の式なので直す所なし。調べ用の残り（`tools/_w.py`、空の `throat()`）は消した | 背中の体（`body.bin`）は口を開ける前のメッシュのまま。背中の口を合わせるなら `tools/gen_ember_back_body.py` を流し直して `LAYOUT_VERSION` を上げる。なお、今の環境で `build_all.py` を流すと c のメッシュがコミット済みのものと少しずれる（数値の環境の差。入れてあるメッシュはそのまま） |
| `wip/armor-install` | 防具 C の低ポリ 4 組が入った（階級でメッシュ、強化 +5・+10 で絵と光る画素。`LowPolyArmor.enabled = false` で元のキューブ）。台本 `tools/shots/armor_lp_*.txt` で、立ち・歩き・しゃがみ・攻撃・乗り物・腕の姿勢を撮って、目立つ食い込みは無かった。ブランチで消されていた案 A・B のラフ（`docs/review/lowpoly_roughs/armor/a`・`b`）は戻した | 岩鯨・雷翼の伝説は三角形がやや多い（1,110 / 1,144）。防具立ての腕上げの姿勢で、肩の板が暗く見える |

## 2. 終わっていること（m1-sky-pass に入っている）

- ベータ 2 の感想 23 項目への対応（目的の帯、シネマティック、空の影と星、墓、進捗、碑、UI の重なり、フォントをマイクラ標準に統一 など）。
- 巨神ごとに章を分けた（序章・一〜四章・五章以降は枠）。章の題字と余韻の札、予兆の仕組み、小要素を拠点の近くに置く仕組み、各章の中身。
- 低ポリ化: 敵（4 巨神の背）、心臓 4 体、語り部・イサナ・見る者、巨神本体 4 体（近・中・遠）。箱のモデルへは `LowPoly.enabled = false` で戻せる（ベータの試遊が終わるまで残す）。
- 背中のダンジョンの外形を、選んだ巨神の形に建て直した（岩鯨 C、潮殻 B（上の段に増築用の空き部屋 3 つ）、雷翼 A は色だけ、余燼 C）。
- 回答ページの細かい決定（目安 Lv の数字を消す、雨でも歌、大干潮は人ごと、潮見の貝を語り部が渡し直す、心核・笛の投げ捨て禁止、素材名「〇〇の欠片」→ 鱗片・殻片・羽軸・燠石、共鳴の欠片を巨神ごとに、装備の説明に物語の一行、墓を選べる救い）。
- 岩鯨の心臓・番兵長の専用の技の音、雷翼の背の専用ブロック、燠の灯。
- 武器 13 本の低ポリをゲームに入れた（階級でメッシュ、強化で絵と光る画素が変わる。`LowPoly.weaponMeshes = false` で元のボクセルに戻る）。未確認: 他のプレイヤーが持つ弓の引き絵、左利き、マルチ、輝き（foil）。素材を作り直す古い生成スクリプトを動かしたら `tools/install_lowpoly_weapons.py` と `weapon_display.py` を流し直す。

## 3. まだ手を付けていないこと

- README を今の mod に合わせて書き直す（ユーザーの依頼。区切りがついてから）。README の絵は手描きのドット絵。ゲームのスクショと絵文字は使わない。
- 雷翼・余燼の装飾品（足環・護符・外套・灯など）の低ポリ化。
- 取り込まれていない古い作業コピー 3 つ（`.claude/worktrees/agent-a2188628431d3bc03`、`a56c7abd31fd65baf`、`aba09111be057701d`。この PC だけ）の中身の確認。
- ゲーム内での通しの確認（シネマティック、小要素、日本語の題字、低ポリの見え方など）は人の目でまだ見ていない。

## 4. 続けるときの注意

- **やり方**: 大きな作業はサブエージェントに分けて並列（担当するファイルを分ける）。モデルは作業で使い分ける: 単純な作業（移動・文書や lang の書き換え・既存の手順どおりの組み込み・片付け）は Haiku 5.5、設計・モデル・ゲームのコード・不具合調べは Sonnet 5.5。各エージェントは worktree で動かし、終わったら m1-sky-pass に取り込む。
- **見た目の決め方**: モブ・武器・防具は、まず 3 案のラフを選定ページ（https://claude.ai/artifact/EqFcC7E183D8uyyAo8yKMM 、`tools/rough/build_gallery.py` で作る。選んだ結果は artifact の db `choices`）で選んでもらってから作る。共通のアートの決まり: 非立方体の低ポリ＋1 ブロック 32 画素のドット絵、軽さ重視（敵 約 600 三角形）。巨神ごとに形と色の言葉を分ける（他の巨神の敵の使い回しに見えると嫌がられた）。
- **設計書**: `docs/design/` は今の仕様だけを書く（決定ログは作らない）。00 はサブエージェントに触らせない。新しい未決は 200 番から。
- **自己テスト**: 毎回新しい level-name と `level-seed=4242`。並列で動かすときは `run/server.properties` の `server-port` と `rcon.port` をエージェントごとに分ける。`SKYABYSS_SELFTEST_ONLY=freequest` だけで回すと落ちる（会話が読まれていないため。全体なら通る）。
- **重いときの岩鯨の自己テスト**: サーバーを 2 台と撮影を同時に動かすと、`SELFTEST whale` が「the new whale is not registered」などで落ちる（試す場所のチャンクの読み込みが 40 tick に間に合わない）。コードの不具合ではない。1 台で回せば通る。
- **クラウドの環境で動かすとき**: ネットワークの許可に `maven.neoforged.net`・`libraries.minecraft.net`・`piston-meta.mojang.com`・`piston-data.mojang.com`・`resources.download.minecraft.net`・`dl.cloudsmith.io`・`maven.parchmentmc.org` が要る。`gradlew` に実行の権限が無いので `bash ./gradlew ...`。撮影は `xvfb-run -a -s "-screen 0 1920x1080x24" bash ./gradlew runShots -Pscript=...`。
- **java を全部落とさない**（`taskkill /IM java.exe` は他の作業のサーバーまで落とす）。止めるのは自分が立てたプロセスだけ。
- **ゲームの窓を OS のキー操作で動かさない**。見た目の確認は台本ランナー（`./gradlew runShots`、`tools/README.md`）で撮る。ボスの通しのプレイはユーザーがする。
- **取り込みでよくぶつかる所**: 自己テストの名前空間の一覧（`LoreSelfTest`・`NpcQuestSelfTest`・`ArcSelfTest`）、`docs/design/15`・`tools/README.md`、各背の `LAYOUT_VERSION`。両方を残す形で直す。
- **Windows の Python**: 日本語の長い行を含む .py を heredoc で渡すと文字コードで失敗する。スクリプトはファイルに書いて `python <file>` で動かす。
- **生成スクリプト**: lang や構造物は `tools/gen_*.py` が作るものが多い。出力だけ直さず生成元を直す（ただし `gen_story_quests.py` などは今のデータと食い違っていて、丸ごと再実行すると手直しが消える）。
- **古いワールド**: 背の配置の版（`LAYOUT_VERSION`）を上げると次の起動で背が一度建て直され、試練の進みと休み場の記録が戻る。
- **このメモの更新**: セッションの残りが少なくなったら、このファイルを今の状態に書き直して push する。

## 5. 関連のページ

- 選定ページ（ラフの比較と選択）: https://claude.ai/artifact/EqFcC7E183D8uyyAo8yKMM
- 未決の回答ページ（175 問、回答済み）: https://claude.ai/artifact/EHPMvP8tusfKdAyLM5s38d
- テスター向けの遊び方: `docs/beta_how_to_play.md`
