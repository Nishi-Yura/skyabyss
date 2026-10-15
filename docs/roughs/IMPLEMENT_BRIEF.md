# 本実装の共通ルール (第2回ラフの採用案をゲームに入れる)

対象: NeoForge 1.21.1 / GeckoLib 4.9.3 の mod「天と淵のあいだ」(mod id skyabyss, package jp.revati.skyabyss)。
ラフの素材は docs/roughs/v2/<subject>/<a|b|c>/ (geo.json, texture.png, glowmask.png)。描画ツール tools/rough/voxel.py。

## 守ること
1. 採用案のデザインを変えない。直す指示があるところだけ直す。
2. 立方体の重なりは禁止 (check_overlaps が空)。アニメーションで動かしたときも、部品が相手に深く食い込まないよう、関節の pivot を部品の接する所に置く。
3. GeckoLib の molang の math.sin / math.cos は「度」で受け取る (math.sin(query.anim_time*90) は4秒で1周)。ループは継ぎ目なく。
4. Java が使うアニメーション名・骨の名前を確かめてから変える (grep で src/main/java を探す)。GuardMob は animation.<prefix>.idle / .walk と、triggerAnim で animation.<prefix>.<名前> を使う。
5. 体の大きさを変えたら、client/entity/GuardRenderers.java の withScale と registry/ModEntities.java の当たり判定を見直す (モデルの見た目とずれないように)。他の担当も同じファイルを触るので、自分の行だけを Edit で小さく直す。直前に読み直す。
6. テクスチャ密度は 1単位 = 2画素。geo の texture_width/height は実際の画素数、uv は画素で書く (per-face uv)。
7. 直したら、ラフと同じ描画ツールで「直した後」の比較シートを docs/roughs/v3/<subject>.png に出す (前回 / 直した後、通常時と動作時の両方)。ユーザーに見せる。Read で必ず見て確かめる。
8. `./gradlew compileJava --offline` (bash) を通す。他の担当の作業中のエラーは無視してよいが、自分のファイルのエラーはゼロにする。
9. git commit しない。docs/roughs/IMPLEMENTED.md に自分の節を追記する (何を変えたか、未確認の点)。
