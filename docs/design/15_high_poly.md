# 天と淵のあいだ 全モデルのハイポリゴン化

- 文書番号: 15
- 範囲: 巨神・心臓・中ボス・敵・NPC・武器の 3D モデルの今の形と、これからモデルを作るときの決まり（方針・負荷の予算・骨の割り方・手順・確かめ方）
- 関連: [16 防具デザイン](16_armor_design.md)（防具 4 組と装備 3D の意匠）、[14 ベータ](14_beta.md)、[02 岩鯨](02_stone_whale.md)、[08 潮殻](08_tide_shell.md)、[12 雷翼](12_thunder_wing.md)、[13 余燼](13_ember.md)

全モデルは「箱を並べただけ」から「彫ったもの」へ作り直してある（採用案はどの家族も C 案、心臓は各案を 4.2 に書いた）。
決まり: 3D は 2D を厚くしただけに見せない / 重なって埋まった箱は禁止（`check_overlaps`）/ 32px 密度のテクスチャ / 武器は mob に焼き込まず別モデルで手のソケットへ。

## 1. 方針

### 1.1 箱を切り分ける（`hpkit`）

作り直しの中心は `tools/rough/hpkit.py`。出来上がった geo とテクスチャを読み、箱を**互いに重ならない小箱に切り分け**て、角を階段状に丸める・穴を掘る・細部の箱を足す。

- 切った小箱の面は、元の箱の同じ向きの面の同じ位置のテクスチャを使う。**テクスチャは変えない**（新しい絵は要らない）。切り口の新しい面（段の壁）も元の面の同じ座標を使う。中に埋まった面は出さない。
- 小箱は重ならないので `check_overlaps` はきれいなまま。ボーン・pivot・回転は触らないので、アニメは変わらない。
- 試験 `tools/rough/hp_selftest.py`: 切るだけで何も削らない場合、描画が元と同じ（違う画素は 0.1% 未満）。
- 元の geo とテクスチャは `tools/sources/hp_base/<種類>/` に控える。作り直しは必ずここから始める（取り込み後に走らせても二重に丸めない）。
- 学んだこと: 箱の少ないモデル（番兵・NPC・潮招き）は、丸め + 穴 + 光る目 + 指で見た目が大きく変わる。すでに 0.5 の小箱で彫ってあるモデル（心臓・雷獣・番兵長・鍛え手）は、面取りだけでは面が増えるわりに差が出ない。元の彫りを 0.25 で作り直すか、顔・手・縁の飾りを足す。

### 1.2 細かさの使い分け

| 単位 | 使う所 | 理由 |
| --- | --- | --- |
| 0.5（= 1 画素、32px 密度） | 大きな部品の面取り、顔・手・縁・装飾の主な形 | テクスチャの 1 画素にちょうど合う。段が見える最小の細かさ |
| 0.25 | 顔・手・縁・飾りだけ（見える所だけ）。面取り、リベット、指、歯、牙 | 面が半画素になるので単色の飾りにだけ使う（1.4）。大きな面には使わない |
| 1.0 以上 | 体の大きな塊、巨神の中・遠景 | 細かさが見えない所は粗いまま |

目安: 正面から見て 1 画素が 1 度以上に見える所だけ 0.25。背中側・下側・普通の距離で見えない所は 0.5。

### 1.3 面取りと起伏の作り方

| 手法 | `hpkit` の呼び方 | 向く所 | 面数の増え方 |
| --- | --- | --- | --- |
| 一段の面取り | `round_all(step=0.5, R=1)` | 大きな箱（短い辺が 2 以上） | 箱が 3〜5 倍、面は 2.5 倍前後 |
| 二段・三段の丸め | `R=2`、`R=3` | 頭蓋・胴・胸甲など大きな塊だけ | 箱が 5〜9 倍 |
| 一辺だけ丸める | `axes="xz"` など | 腕・脚（八角柱） | 3 箱 |
| 穴を掘る | `round_cube(holes=[...])` | 眼窩・鼻・口・彫り込み | 穴の面だけ増える |
| 細部の箱を足す | `add_box`（`safe=True` で重なりを避ける。ぶつかる箱は黙って落とすので、落とした数を表示すること） | 眉・頬骨・リベット・縁の枠・指・歯・膝 | 1 箱ずつ |
| 浮き彫りの筋 | `ridges` | 殻の筋・毛並み・装甲の継ぎ目 | 筋の数だけ |
| 光る点を足す | `add_box(glow=True)` | 目・結晶 | glowmask に画素が増える（光る画素ゼロでは落ちる） |

### 1.4 テクスチャの密度との釣り合い

- 32px 密度 = 1 単位に 2 画素。0.5 の箱の面は 1 画素角、**0.25 の箱の面は半画素**で、普通に UV を書くと隣の画素（光る画素も）を拾う。
- 対策（`hpkit` に実装済み）: 1 画素より小さな面は、面の中心の画素 1 つだけを拾う。足す飾りは「1 色 = 2x2 画素の見本」をテクスチャの下に追加して拾う（番兵は 66 → 82 行）。
- だから 0.25 の箱は**単色の飾り**に限る。柄のある面は 0.5 以上にする。顔の目・口は元の絵のまま、その上に 0.25 の起伏を重ねる。
- 心臓だけは 64px 密度（0.25 の面 = 64 px/単位。4.2）。ほかは 32px のまま（未決 9.1）。

## 2. 描画の負荷の予算

### 2.1 測ったこと（この PC、1280x720、垂直同期なし。値は目安）

- GeckoLib の描画は CPU で面ごとに頂点を作るので、**面数にほぼ比例**する。箱の数でなく面の数で予算を立てる。
- 番兵 36 体の実測は 1 面 約 0.13 µs。中ボスを多数並べた実測は 0.5〜0.9 µs と、その 2〜3 倍。**中ボスの 8,000 面は 1 体 4〜5 ms** と見る。
- **発光層（AutoGlowingGeoLayer）はモデルをもう一度描く**ので、面の費用が 2 倍以上になる。光る所のあるモデルは面の予算を半分で見る。発光層の要否は `GuardRenderers.geo(..., glow=)` で決める（光る所の無いモデルは `glow=false`）。
- ばらつきが大きい。比べるときは同じ台本を 2 回回す。

| 対象 | 結果 |
| --- | --- |
| 骨の番兵 36 体 | 発光なし 7.7 ms、目が光る 13〜18 ms（作り直し前は 3.4〜4.1 ms） |
| 磯走り 12 + 泡吹き 12 | 9.2〜14.0 ms（前は 20.5〜20.8 ms。泡吹きの面を 2,490 → 1,488 に減らしたので軽くなった） |
| 番兵長 ×6 / 潮招き ×4 / 雷獣 ×6 / 鍛え手 ×6 | 19.6 / 17.8 / 19.0 / 18.9 ms（1 体あたり +0.9 / +3.6 / +1.2 / +1.4 ms） |
| 巨神の近景 | 岩鯨 6.7、潮殻 6.3、雷翼 5.6、余燼 4.7 ms（前から +0〜0.6 ms。ばらつきの内） |
| 心臓 12・NPC 3 体・武器と防具・雷と余燼の敵 | 測っていない（面は各上限の内） |

巨神の近景は面が 4〜27% 増えても、発光層・地面・空の描画が大半なので増分は 0.5 ms 以内。重いのは**中ボスを多く並べた場面**。実戦は 1〜2 体なので許容と見ている。重いと感じたら、潮招きを B 案（6,256 面）に戻す、鍛え手の胴の二段丸めを一段にする、で 1 割ほど減る。

### 2.2 面の予算（発光層ありを前提に、1 フレームの GeckoLib 全体で 5 ms ≒ 約 20,000 面を目安）

| 種類 | 1 体の上限（面） | 同時に見える数の目安 | 今 |
| --- | --- | --- | --- |
| 巨神 近景（<64 ブロック） | 12,000 | 1 | 11,650〜11,981（ほぼ上限。足すなら隠れた面を減らして相殺する。超えると近くで 3 ms 以上） |
| 巨神 中景（<160） | 5,000 | 1 | 2,200〜4,500（変えない） |
| 巨神 遠景 | 1,500 | 1 | 1,100〜1,300（変えない） |
| 心臓（段階ごと） | 6,000 | 1 | 4,565〜5,990 |
| 中ボス | 8,000 | 1〜2 | 4,616〜6,820 |
| 敵（画面に多い） | 1,500 | 8〜12 | 785〜1,497 |
| 敵（たまに出る） | 2,500 | 3〜4 | |
| NPC・見る者 | 3,000 | 1〜3 | 1,290〜2,176 |
| 武器（gear） | 600 | 敵の数だけ | 144〜466 |
| 立体防具（4 部位） | 5 部位合計 3,000 | プレイヤー数 | 16 の 3.8 |

### 2.3 負荷を抑える作り方

| 手 | 内容 |
| --- | --- |
| 見える面だけ出す | `hpkit` は埋まった面を出さない。元の geo にも同じ骨の別の箱に隠れた面があるので、面数（`hp_geo_stats.py`）で洗う。別の骨で隠れた面は、腕が振れると開いて透けるので、アニメの全姿勢で試して**開く面は戻す**（`pose_holes`） |
| 地面を向く面を消す | 上から見て見えない `down` の面（顔の下顎は残す） |
| 小さな箱は面を減らす | 0.25 の飾りで見えない向きの面は uv を書かない（`add_box(faces=...)`） |
| LOD | 巨神は中・遠で元の粗い箱を使う。敵・中ボスには LOD を付けない（同時に 20 体を超える場面が出たら付ける） |
| 発光は点で足す | 面積の広い glowmask は避け、目・結晶・筋だけを光らせる |
| 雑音の多い布・鎧は切り直す | 荒い格子で多数決に直す（`remesh`）と、細かい段が「ひだのある面」になって面が大きく減る |

## 3. 骨の割り方（アニメへの影響）

| 事柄 | 決まり |
| --- | --- |
| 骨の木・pivot | 変えない。`hpkit` は箱だけを差し替える（アニメ・ソケット・molang は動く）。骨の名前・pivot・回転を変えない |
| 骨の細分化 | 指・歯・顎・耳など、**動かしたい細部だけ**新しい骨を足す。骨が増えると 1 体あたりの計算も増えるので、敵は 1.5 割増まで。今の指は手の骨に付けている |
| 丸めた箱の回転 | 箱ごとの `rotation` は丸めても引き継ぐ。`check_overlaps` は回転した箱を厳密に比べないので、目で確認する |
| 動く部品の隙間 | 関節（pivot の上下 1 単位）は面取りしない（`rule` で外す）。他の箱と接する縁は削らない |
| 回る骨 | 風切り（`hand_*`）や翼（`wing_*`）など回る骨の面は、他の骨に隠れていても落とさない（落とすのは同じ剛体の中だけ） |
| 武器 | 手のソケットに別モデルで付ける。mob に焼き込まない |

## 4. 今のモデル

面 = uv を持つ面の数（`hpkit.stats` の quads）。数は `python tools/rough/hp_geo_stats.py` で出せる。重なり（`check_overlaps`）はすべて 0。光る画素は今と同じかそれ以上。最小の箱はほぼ 0.25〜0.5。

### 4.1 巨神本体（近 <64 ブロック、中 <160、遠 それ以上の 3 段 LOD。近景だけ作り直した）

> **今の描画は低ポリのメッシュ**（9.6 の「巨神本体」）。岩鯨は案 C「皮が岩になる鯨」、潮殻は案 B「段になった珊瑚礁の島」、雷翼は案 A「嵐の大鷲」、余燼は案 C「皮が岩になる火蜥蜴」。LOD の切り替えの距離（近 <64 / 80 / 120 / 96、中 <160 / 160 / 240 / 192、遠はそれ以上。軸までの距離）はそのままで、近・中・遠それぞれが別のメッシュ。**以下の表と説明は、`LowPoly.enabled = false` のときに出る箱版（戻し先）の記録。**

| モデル | 近 箱 / 面 | 中 箱 | 遠 箱 | テクスチャ | 近景に足したもの |
| --- | --- | --- | --- | --- | --- |
| 岩鯨 stone_whale | 2,838 / 11,650 | 839 | 248 | 2048x1056（足した行ぶん増） | 顔（瞼 2 段・眉・瞳の縦線・唇の帯・下顎の歯）、ひれの前縁の筋と肋、尾びれの肋、背の遺跡・塔の上面の筋 |
| 潮殻 tide_shell | 2,868 / 11,981 | 650 | 293 | 2048x2672 | 顔・嘴（瞼・眉・鼻孔・嘴の背の筋・板の継ぎ目）、ひれ・縁の鋸歯（119 本）、甲羅の放射状の筋 4 本。上限に最も近い |
| 雷翼 thunder_wing | 2,684 / 11,884 | 468 | 238 | 2048x1136 | 嘴の背の筋・縁・鼻孔・先の鉤、目の上下の縁、頬の羽根の穂先、足の爪 3 本 + 蹴爪、風切りの先、尾・首の面取り |
| 余燼 ember_titan | 3,046 / 11,779 | 1,028 | 286 | 2048x2944 | 目の縁（0.25）、頭の後ろの角 2 本（4 段 + 先が光る）、前後の足の爪 3 本、頭・尾の面取り、尾根の黒い縁（溶岩に隣り合う暗い岩の縁に薄板） |

- 生成: `install_stone_whale.py`・`install_tide_shell.py`・`install_thunder_wing.py`・`install_ember_titan.py` が箱から全部作る（中・遠も同じスクリプトが間引く。最小の箱は近 0.5、中 2〜4、遠 4〜8）。最後に hp の処理（岩鯨・潮殻は `hp_titan_body.install(名前, "C", rebase=True)`、雷翼・余燼は `install_titan()`）を呼んで、素の近景に飾りを足す。呼ばないときは `--no-hp`（岩鯨・潮殻）。
- 道具: 岩鯨・潮殻は `tools/rough/hp_titan_body.py`（`hp_occ.py`）。飾りは「実際の表面」に敷く（`Skin.relief`: 0.5 角の格子で形を決め、各格子の表面の高さから 0.25〜1.0 だけ盛る）ので隙間も重なりもできない。雷翼・余燼は `hp_titans_storm_ember.py`（`hp_mobs_storm_ember.py` の `prune`・`bevel`・`pose_holes` を流用）。
- 輪郭・大きさ・色の配置は変えない。飾りは面から 1 単位以内。色は元の面の色を持ち上げ・暗くしただけ。テクスチャの高さは飾りの見本の行だけ増え、中・遠の geo は `texture_height` だけ同じ値に書き直した（uv は画素なので見た目は同じ）。
- 面の相殺: 余燼は元から 11,289 面で、同じ剛体（体 + 尾根）の別の箱に隠れた面を落として 1,657 面を空けた。雷翼は 234 面。落とした面は全アニメの全姿勢で開く面 0 を取り込みの条件にした。
- 雷翼の翼は 4 単位の段の大きな板で、面取りは面が大きく増える（風切りだけで +2,800）ので入れていない。
- 背中のディメンション（岩鯨の背・潮殻の亀の島・雷翼の鳥・余燼の火山の背）は、install スクリプトの**格子**から作り、近景の geo は読まない。格子の関数に触れていないので、背中の生成の入力は変わらない。
- 実装: `EmberSelfTest` の近景の箱数の範囲は 2,000〜3,500。
- 戻し方: `tools/sources/hp_base/entity/` の geo・テクスチャ・発光マスクを `assets/skyabyss/geo/entity` と `textures/entity` へ戻し、中・遠の geo の `texture_height` を戻す（岩鯨 1056・潮殻 2672）。または install の hp 呼び出しを外して流し直す。

### 4.2 心臓（最終ボス。段階 1 / 2 / 3 で別の geo。上限 6,000）

> 低ポリのメッシュに切り替え済みのモデルは、9.6 の表が今の見た目。この節の表は元の箱のモデル（`LowPoly.enabled = false` で出る姿）の記録。

| 心臓 | 面（段階 1 / 2 / 3） | 採用 | 中身 | 生成元（入力） |
| --- | --- | --- | --- | --- |
| 岩鯨 | 5,040 / 5,990 / 5,948 | B 彫り | 板を一つの球に削って段々を無くし、地層の溝・欠け・苔、割れ目（段階 2 から光る）。段階 3 は筋繊維・脂の溝・盛った血管 | `tools/sources/heart/`（`install_heart_v4.py`） |
| 潮殻 | 5,856 / 5,384 / 4,565 | C 真珠と割れ | 丸い心臓、核から放射の溝、塗りの血管網、核の光が漏れる割れ（段階 1 は 5 本 → 3 は 8 本 + 欠け）。殻の巻は 0.5 のまま | `sources/tide_heart/`（`install_tide_heart.py`） |
| 雷翼 | 5,085 / 5,847 / 4,702 | C 嵐の傷 | 丸い心臓、翼の軸・羽枝・筋・先の切れ込み、段階ごとに増える光るひびと焦げ。稲妻の網は細いまま | `sources/storm_heart/` |
| 余燼 | 5,760 / 5,797 / 5,309 | B 板割れ | 殻を丸い玉に作り直し、ボロノイの板割れ。割れ目の床が光る（段階 1 細い → 2 広く深く → 3 灰で鈍く）。垂れは先を細く | `sources/ember_heart/` |

出力は `tools/sources/<心臓>_hp/` に 3 段階を書き、各 `install_*heart*.py` の元をそこへ向けて取り込む（色・ガンマ・発光の足し方は従来どおり）。段階 2 は補間でなく、それぞれの段階の元から作った。

方式（`tools/rough/hp_heart_kit.py`、各心臓の `hp_heart_<名前>.py`）:
1. 焼き済みの 0.5 のラフ（入力として残す）を、面の画素から格子（セル）に戻す。回転した骨（岩鯨の板）は骨ごとの格子、それ以外は共有の格子。往復だけの試験で面数が元と一致する。
2. 0.25 の格子にして、丸め（ぼかして閾値）・彫り（溝、割れ目、欠け）・盛り（筋、角）・塗り（血管、苔、すす）を重ねる。**塗りは面が増えない**ので多用し、彫りと盛りは見える所だけ。
3. 面の陰は、ぼかした法線 + 環境光の遮り（AO）で焼き直す。光る画素は平らな色のままで、丸めで消さない。
4. 箱に貪欲法でまとめ、面 1 つに 1 画素（0.25 の面 = 64 px/単位）。骨の名前・木・pivot・回転は元のまま。
5. `python -B tools/rough/hp_heart_<名前>.py rough` で比較画像、`... build <案>` で書き出す。

学んだこと:
- 0.25 で全面を丸めると面が 4〜8 倍。**見える所だけ 0.25、殻や背面は 0.5 の段で丸める**のが予算に合う。
- 面を増やさずに効くのは塗りと AO の焼き直し。回転した骨（岩鯨の板）は削るだけにして、周りとぶつからないようにする。
- 段階 2・3 を実機で撮るには、アリーナの戦闘か `/skyabyss <心臓> phase <n>` が要る（NoAI の召喚だけでは段階 1 のまま。`tools/shots/hp_hearts.txt` は 4 体の段階 1）。

### 4.3 中ボス（上限 8,000）

> 低ポリのメッシュに切り替え済みのモデルは、9.6 の表が今の見た目。この節の表は元の箱のモデル（`LowPoly.enabled = false` で出る姿）の記録。

| モデル | 箱 / 面 | 骨 | 生成元 | 足したもの |
| --- | --- | --- | --- | --- |
| 番兵長 whalebone_captain | 1,318 / 4,616 | 20 | `gen_mobs.py`（+ ラフ `captain_v4.py`）。鉾は別モデル（item） | 兜・拳・腿の二段丸め（腕・脚の骨は縦の辺だけ）、鼻梁、覗き窓の縁、冠の継ぎ目、拳の関節、腕当て、指 4 本 + 親指、兜の口の格子 6 枚 |
| 潮招き tide_beckoner | 1,356 / 6,820 | 12 | `install_tide_caller.py` | 甲羅の段と爪を 1.0 刻みで二段丸め、目の枠、全 6 脚の関節の棘、大鋏の歯（上下 2 段 × 12）、爪の筋、甲羅の背棘。差が最も大きい（元は 188 箱の粗いモデル） |
| 雷獣 raiju | 1,322 / 6,195 | 17 | `install_raiju.py` | 胸・胴・頭・脚の丸め（尾 3 骨・たてがみの結晶・1 単位の脚板は元のまま。全面だと 8,400 面で上限なので範囲を絞った）、牙 2、眉の庇、鼻の縁、肩・腰の棘（2 段）、背の筋 |
| 鍛え手 ash_smith | 1,225 / 6,794 | 15 | `install_ember_smith.py`（大鎚・火箸は gear） | 頭・腕・前腕・脚・腹と胴の積み板の丸め（鞴 2 つと前掛けは元のまま）、兜の覗き窓の縁、口の格子 5 枚、胴・脚の板の鋲（約 85） |

- 作り方は `tools/rough/hp_midboss.py`（`--install <名前> C`）。範囲の調整は `python tools/rough/hp_midboss_probe.py <名前> x0 y0 z0 x1 y1 z1` でその範囲の元の箱を引く。面取りの後に足す箱は同じ場所に元の箱があって入れないことが多い（`SKIPPED` に覚えて表示）。ポーズ確認は `hp_midboss_pose.py`。
- 鍛え手の白い発光マスク（`ash_smith_glowmask_white.png`）もテクスチャと同じ uv なので取り込みで作り直す（`install_ember_smith.py` の `whiten` と同じ式）。geo は元の整形（indent=1）のまま書く（`AshSmithSelfTest` が `"name": "bone"` の文字列を探す）。
- 暗い飾りの色は、元のテクスチャに掛けた持ち上げ（08 の 5.4 節）と同じ式（`shade()`）を通す。
- `install_{tide_caller,raiju,ember_smith}.py`・`gen_mobs.py` は元の geo を作り直して上書きするので、取り込み後に走らせない（走らせたら `hp_midboss.py --install <名前> C` で戻す）。
- 番兵長・鍛え手は元から 0.5 の階段で彫ってあり、丸め + 0.25 では全身の差は小さい（顔・手の接写で分かる）。大きく変えるなら、鍛え手の炉の口・兜、番兵長の兜を 0.25 で彫り直す。
- 戻し方: `tools/sources/hp_base/entity/` から geo・テクスチャ・発光マスクを戻す。

### 4.4 敵（上限 1,500。たまに出る敵は 2,500）

> 低ポリのメッシュに切り替え済みのモデルは、9.6 の表が今の見た目。この節の表は元の箱のモデル（`LowPoly.enabled = false` で出る姿）の記録。

| モデル | 箱 / 面 | 骨 | 生成元 | 中身 |
| --- | --- | --- | --- | --- |
| 骨の番兵 bone_sentinel | 236 / 1,243 | 9 | `gen_mobs.py` | 頭蓋を丸め、眼窩を掘って**目が光る**（今まで光る所が無かった）、頬骨・眉・縁の枠・リベット・指・肋・膝 |
| 肋の射手 rib_archer | 183 / 882 | 10 | `gen_mobs.py` | 頭蓋 3 段、眼窩に光る点、角は先細り、眉・頬骨、歯 10 本、指 4 本、膝・肘・肋の先 |
| 苔這い moss_crawler | 646 / 2,121 | 27 | `install_moss_crawler_b.py`（ラフ `rough/smooth_mob2.py` の B 苔芋虫） | 滑らか版（上限 1,500 は超える。ラフの 672 / 2,171 から、体の下に隠れる膝の丸を外した）。五つの節（骨 `seg1`〜`seg5` が尾から頭へ鎖になり、前の節は後ろの節の回転を引き継ぐ）に骨の輪と背の苔、一つ目（骨 `eyes`。虹彩と瞳が光る）、顎と歯、2 本の触角と先の誘いの光（`ant_*`・`lure_*`）、六本の脚（`l1..3_u/_l`・`r1..3_u/_l`）、尾。描画は 1.3 倍。アニメは idle（節の呼吸・触角・瞬きと見回し）、walk（節に沿って進む波と脚）、pounce（前の節を起こして飛び込む）、hurt、death（渦を巻いて丸まる）。入れ方は `python tools/install_moss_crawler_b.py`（geo・png・glowmask・animation を書き、`fix_geo_face_sides.py --apply` と重なり検査 0 まで回す）。ゲーム内の絵は `docs/review/previews/moss_b_*.png`（台本 `tools/shots/moss_crawler_b.txt`。`/skyabyss dev anim` で動きの途中を撮る） |
| 水路の顎 waterway_maw | 288 / 1,393 | 11 | `gen_mobs.py` | 大箱を 2〜3 段で丸め、歯（3 段の先細り、上 10 対・下 7 対）、えらのすじ、背のとげ、ルアーの襟 |
| 珊瑚憑き coral_thrall | 1,332 / 1,494 | 11 | `install_tide_mobs.py` | 歯 6 本・眉・頬の飾り・指・こぶし、冠と肩の珊瑚に細い穂先、布の裾にぼろ。像の珊瑚（`statue_coral`）は 3 枚の板を重ねた塊に作り直し（約 160 面）。像の姿は「起きた体 + 像の珊瑚」が全部見える |
| 磯走り reef_scuttler | 151 / 785 | 6 | `install_tide_mobs.py` | はさみの噛み合う歯、脚の面取り、甲羅の縁の飾り、目の柄の輪、脚先のつめ |
| 泡吹き bubble_puffer | 352 / 1,488 | 6 | `install_tide_mobs.py` | 唇・2 本の前歯、目の上のまぶた、胸びれのすじと縁。表面の凹凸を 4〜5 個に 1 個へ間引き（とげは 4 本に 1 本）、腹の最下段以外の `down` を消した。**目は「小さく濁った」まま**（光る画素 12・弱い光） |
| 羽守 feather_warden | 316 / 1,471 | 12 | `install_storm_mobs.py` | 胸甲の縁取りと鋲、手の爪、肩当ての鋲と角、膝当て。胴は切り直し（434 → 175 面） |
| 雷蛾 static_moth | 242 / 1,291 | 5 | `install_storm_mobs.py` | 翅の縁の歯、頭・胴の 0.25 の面取り、触角のひげ、背の毛筋、口吻と脚 |
| 雷乞い thunder_supplicant | 295 / 1,394 | 9 | `install_storm_mobs.py` | 鳥籠の先端飾り・足の輪・襟の鋲、袖口の縁、指、衣の縦の縫い目。衣は切り直し（965 → 308 面）。ぼろ布の裾の房は無くなった |
| 灰被り ash_thrall | 339 / 1,496 | 20 | `install_ember_mobs.py` | 眉庇、炎の先、殻の岩の面取り。上限は殻と燃え上がりの骨の**両方を数えた合計**（描かれるのは一方だけ。殻の姿 1,385 / 燃え上がり 1,088） |
| 火の粉蛾 ember_moth | 304 / 1,474 | 5 | `install_ember_mobs.py` | 腹の輪、頭のひげ、脚の関節、翅の縁の欠け、頭・胴の 0.25 の面取り |
| 燠虫 cinder_roller | 285 / 1,392 | 37 | `install_ember_mobs.py` | 甲羅の面取り、背の筋、足の爪、触角のひげ、尾の棘 |

- 光る画素: 射手 24〜28、苔 32、顎 108、珊瑚憑き 2、磯走り 48、泡吹き 12、羽守 62、雷蛾 190、雷乞い 200、灰被り 306、火の粉蛾 678、燠虫 3,786。
- 生成: 岩鯨・潮殻の敵は `tools/rough/hp_mobs_whale_tide.py`（`cull_hidden` が同じ骨の箱に完全に覆われた面を消す。`install_tide_mobs.py` が `install_picks` で採用案へ置き換える。珊瑚憑きの像のテクスチャも作る。`python tools/rough/hp_mobs_whale_tide.py [名前]` でシート、`--install`）。`gen_mobs.py`（停止中）の 3 種は、動かしたあとで `--install` をやり直す。
- 雷翼・余燼の敵は `tools/rough/hp_mobs_storm_ember.py`（取り込みは `install_storm_mobs.py`・`install_ember_mobs.py` の末尾の `install_mob()`。素の geo とテクスチャから作り直して上書きするので何度流しても同じ）。灰被りの燃え上がり、雷蛾の帯電の光の絵も、飾りの見本の行を足して同じ高さにそろえる。
- `hp_mobs_storm_ember.py` の部品（他でも流用できる）: `prune`/`repair_pruned`/`prepare`（同じ剛体グループの箱に隠れた面を落とす。灰被りの殻 `crust_` と燃え上がり `flare_` は別状態なので互いの下の面は落とさない）、`pose_holes(name, geo, hide=)`（全アニメの全姿勢で開く面を調べる）、`bevel_cube`/`bevel_all`（近傍を見る面取り）、`remesh(m, bone, f=(2,1,2))`（荒い格子の多数決で箱を作り直す）、`veins`/`fringe`/`barbs`/`frame`/`box(stick=)`（筋・縁の歯・ひげ・縁取り・飾り）、`finish`（全姿勢で開く面があれば色で戻す安全網）、`spend`（工程を優先順に並べ、面が上限を超える工程は取り消す）。
- 射手の弓（`socket_off`）・珊瑚憑きの銛（`socket_main`）は別モデルのまま。
- 余白が無い 3 種（灰被り・火の粉蛾・燠虫）は飾りが各 10〜15 面ずつで、近景で見られる灰被りの顔にだけ足した。もっと足すなら上限を 2,500 に上げるのが早い。
- 戻し方: `tools/sources/hp_base/entity/` の控え（灰被りの `_flared`、雷蛾の `_charge`、珊瑚憑きの像の絵も）を `assets/skyabyss/{geo,textures}/entity/` へ戻す。または `install_*_mobs.py` から `install_mob` の呼び出しを外して流し直す。

### 4.5 NPC・見る者（上限 3,000）

| モデル | 箱 / 面 | 骨 | 生成元 | 中身 |
| --- | --- | --- | --- | --- |
| 語り部 storyteller | 688 / 1,981 | 10 | `tools/install_smooth_npcs.py`（ラフ `rough/smooth_npc.py` の B 頭巾の語り部） | 滑らか版。頭は 1 つの丸い塊（0.25 の段の球）で、目・眉・口・頬を塊の表面に描く。鼻と眉の張りは同じ肌の塊の出っぱり。頭巾は頭にかぶさる殻（`hair`・`hair2`）で、前に生え際の窓を開ける。首と襟があり頭が体の軸に載る。肩掛け、帯、結び紐、組んだ手。床の高さの骨の pivot は新しい（アニメの骨名は同じ） |
| イサナ whale_chaser | 690 / 2,362 | 9 | `tools/install_smooth_npcs.py`（ラフ `rough/smooth_npc.py` の A つば広帽の旅人） | 滑らか版。頭は 1 つの丸い塊で、顔は表面に描く（太い眉・目・口）。つば広の帽子は頭にかぶさる殻、マフラー、ボタンつきのコート、手袋、ブーツ、背負い袋（丸めた地図）。`hair`・`hood2` の骨は箱なしの空 |
| 見る者 watcher | 226 / 1,290 | 7 | `gen_npc_content.py`（ラフ `watcher_v2.py`） | **人らしくしすぎない**（鼻・眉・指・口は作らない）。頭を丸め首との間に隙間を残し、外套の長い襞、裾と袖口のほつれ、埃の粒。目（`eyes` 骨）はそのままで、まわりに暗い縁取りを置いて切れ込みに見せる。元の glowmask をそのまま保つ |

- 語り部・イサナ・見る者は低ポリのメッシュに差し替えた（9.6）。下の滑らか版の記述は、`LowPoly.enabled = false` のときの箱の描画の話になる。
- 語り部とイサナは滑らか版（顔が頭の塊に収まる作り）に差し替えた。入れ方は `python tools/install_smooth_npcs.py`（geo と png を入れ、`fix_geo_face_sides.py --apply` で面の左右を GeckoLib の並びにする。穴 0、重なり 0、glowmask は光る画素が無いので入れない）。比較シートは `docs/review/smooth_npc_roughs/{storyteller,whale_chaser}.png`、ゲーム内は `docs/review/previews/smooth_*.png`（台本 `tools/shots/smooth_npcs.txt`）。以下の `hp_npc.py` の記述は見る者にだけ当てはまる。
- 作り方は `tools/rough/hp_npc.py`（`python tools/rough/hp_npc.py` でシート、`--install C` でゲームの assets へ）。NPC は発光層なし。小道具 `band`（巻き帯）・`vstrips`/`hstrips`（襞と縫い目）・`round_one`（小さな箱の 0.25 面取り）は `hp_npc.py` の中にあり、他でも写して使える。`add_box(safe=True)` が落とした数: 語り部 32、イサナ 43、見る者 11。
- 語り部とイサナの顔のアイコン（`gui/face`）は絵なのでそのまま。
- `npc.py`（ラフ）と `gen_npc_content.py` の geo 出力は古い箱のままなので、**再生成すると取り込みが戻る**。geo を再生成するときは先に `hp_npc.py --install C` を後ろに回す。
- 見る者はクライアントだけの存在で遠くに立つ（`/skyabyss watcher show`）ので、ゲーム内では遠景しか撮れない。
- 戻し方: `tools/sources/hp_base/entity/` の geo・png（見る者は glowmask も）を assets に戻す。

### 4.6 武器・盾・防具

| 種類 | 中身 |
| --- | --- |
| gear の geo（mob の手に付く。600 面以内） | 灰被りの火掻き棒 168、番兵の盾 420、珊瑚の槍 144（像も同じ。細い軸は元のまま）、鉤槍 401、射手の弓 246（元のまま）、鍛え手の大鎚 466・火箸 364、雷乞いの杖 274。テクスチャ 64x18〜512x64 |
| item モデル（プレイヤーの武器） | 13 個の低ポリメッシュ（4.6.2。階級 3 × 強化 3 の絵）。GUI の 32x32 の絵と持ち方（display）は 4.6.1。元の絵を彫ったボクセル（18 モデル、要素 64〜752）は `models/item_voxel/` に戻り先として残す（`gen_weapon_models_v2.py` は停止中、`tools/sources/weapons/*/voxels.npz` が入力） |
| item モデル（装飾品・補助の品） | 16 個の低ポリメッシュ（4.6.4。階級 3 の絵、鍛えないので強化の段なし）。GUI の 32x32 の絵は今のまま。元の平絵のモデルは `models/item_flat/` に戻り先として残す。装飾の枠に付けた姿はプレイヤーの体に描く（`AccessoryWornLayer`） |
| 立体防具 | 4 組の低ポリメッシュ（C 骨組み。4.6.3。一式の三角形は通常 627〜864、レア 699〜980、伝説 804〜1,080）。`LowPolyArmor.enabled = false` で元のキューブ（geo/armor。[16](16_armor_design.md) の重ね板の基盤、`tools/install_armor_v2.py`、面 1,250〜1,463）に戻る |

gear の作り（`tools/rough/hp_gear.py`、取り込み `tools/install_hp_gear.py`。入力は `tools/sources/hp_base/gear/`）: 大きな部品は二段丸め、厚さ 1 以上の板・柄は 0.25 の面取り、0.25 の浮き縁（板の上下の継ぎ目）、長い柄の巻き輪（8 単位ごとの 0.25 の帯）、盾・槌頭の縁のリベット。足した箱は単色で既存の箱に重ならない。**細い軸（厚さ 1 未満の槍・弓・火掻き棒の柄）は面取りすると痩せて見えるので面取りしない**。インストーラーが重なりを検査して落とす。骨の名前（`armorHead` など）・pivot・手の受け口は触らない。

#### 4.6.1 プレイヤーが持つ武器（手に持つ姿と攻撃の動き）

手に持つときも、GUI（インベントリ）以外は立体を使う（`neoforge:separate_transforms` の base = 立体、gui = 32x32 の平面）。立体は低ポリメッシュ（4.6.2）。一人称・三人称・ほかのプレイヤーから見た姿・地面・頭・額縁の全部が同じ立体になる。

| 武器 | 種類 | 持ち方（三人称） | 振りの動き |
| --- | --- | --- | --- |
| 鯨骨の大剣 | 大剣 | バニラの剣と同じ向き、大きめ（1.2 倍） | 両手で頭の上まで振りかぶり、斜めに断ち下ろす。体を前へ倒す |
| 番兵長の鉾・潮殻の三叉槍・珊瑚の銛 | 長柄 | 穂先が腕の向きに前へ（`weapon_display.py` の forward） | 肩ごと引いて、まっすぐ突く |
| 羽守の鉤槍 | 長柄（薙ぎ） | 穂先が前へ。両手で構える | 両手で水平に大きく薙ぐ |
| 水路の牙 | 短剣 | バニラの向き | 素早い一閃 |
| 雷獣の爪・潮招きの爪 | 爪 | バニラの向き | 振り上げて斜めに引き裂く |
| 余燼の戦斧 | 斧 | バニラの向き | 片手で振りかぶって打ち下ろす |
| 鍛え手の大鎚 | 鎚 | バニラの向き（0.9 倍） | 大きく振りかぶって体ごと叩きつけ、少し止まる |
| 灰被りの火かき棒 | 突き | 穂先が前へ | 短く鋭い突き |
| 雷翼の弓 | 弓 | バニラの弓の構え。引き絵は 4 段階の別モデル | 引く動きはバニラ。放した瞬間から 7 tick、弓の手が跳ね、弦の手が開く（強く引くほど大きい） |
| 珊瑚の盾 | 盾 | バニラの盾の構え | バニラ |

仕組み（コードは `client/weapon/`）:

- **一人称**: `IClientItemExtensions#applyForgeHandTransform`。バニラの手元の置き方（`applyItemArmTransform`）と同じ位置から、種類ごとのキーフレーム（`SwingMove`。移動と回転）で動かす。振りの進み（`attackAnim`）0..1 から作る。
- **三人称・ほかのプレイヤー**: `HumanoidModel.ArmPose` を NeoForge の拡張 enum（`META-INF/enumextensions.json`、`neoforge.mods.toml` の `enumExtensions`）で種類ごとに足し、`IClientItemExtensions#getArmPose` で利き手の武器にだけ返す（反対の手はバニラ）。構えの中でバニラの腕振り・胴ひねりを止め、同じ進み（`model.attackTime`）から利き腕・反対の腕（何も持っていないときだけ）・胴のひねり・肩の前後を作る。普段の構えは長柄・火かき棒だけ腕を前へ出す（ほかはバニラの片手持ち）。
- **体の傾き**: 大剣・鎚・斧・爪・長柄は、振るとき `RenderLivingEvent` で腰を中心に体ごと前へ倒す（先に少し反ってから前へ）。
- 動きは全部バニラの「振り」の進みから作るので、サーバーへ何も送らず、ほかのプレイヤーにも同じに見える。左利きは左右反転する（左手の display は `weapon_display.py` の `left_of`）。振りの長さはバニラの 6 tick（約 0.3 秒）のまま。
- 持ち方（display）の表は `tools/weapon_display.py`。モデルを作り直す生成スクリプトは display をバニラの値で上書きするので、そのあとに `python tools/weapon_display.py` を通す。
- 確認: `tools/gen_weapon_pose_shots.py` の台本と `tools/weapon_pose_sheet.py`（`tools/README.md`）。

#### 4.6.2 プレイヤーの武器の低ポリメッシュ（階級と強化の見た目）

13 個（鯨骨の大剣・番兵長の鉾・水路の牙・潮殻の三叉槍・珊瑚の銛・潮招きの爪・珊瑚の盾・雷翼の弓・羽守の鉤槍・雷獣の爪・余燼の戦斧・鍛え手の大鎚・灰被りの火かき棒）。手に持つ・落ちている・額縁・頭の見た目だけ。GUI は元の 32x32 の絵、持ち方（display）と振りの動き（4.6.1）は変えない。階級と強化の見た目の段は [03](03_gear_and_endgame.md) の 1.2 節。

| 決めるもの | 選び方 |
| --- | --- |
| メッシュ | 品の階級（`ModGear#tier`）。伝説以上 = legend、レア = rare、それ以下 = common。階級ごとに 1 つ（弓は引き絵の段ごとにも）。三角形は 134〜348 |
| アトラスと光る画像 | 強化（`skyabyss:enhance`）。+0〜+4 = 基準、+5〜+9 = plus5、+10 = plus10。メッシュは強化で変わらない |
| 引き絵 | 弓だけ。引いている間は `pull0`、引きの 0.65 から `pull1`、0.9 から `pull2`（バニラの述語と同じ。引いていないときは基準のメッシュ） |
| 光る画素 | アトラスの色のまま `entityTranslucentEmissive` で描き足す（明るさ最大）。光る画素のない絵は足さない |

仕組み（コードは `client/weapon/WeaponItemRenderer`。メッシュの読み込みと焼きは 9 章の `LowPolyMeshes`・`LowPolyMesh` をそのまま使う）:

- item モデル（`models/item/<id>.json`）の base は `builtin/entity`（display と粒子の絵だけ）、gui は 32x32 の平面（`perspectives`）。base のときだけ `IClientItemExtensions#getCustomRenderer`（`BlockEntityWithoutLevelRenderer`）が呼ばれてメッシュを描く。振りの動き・構え・弓の反動の拡張と同じ拡張に同居。盾は構えを変えず（バニラの盾の構え、構え中は `coral_shield_blocking` の display）メッシュだけ差し替える
- 置き方: item モデルの原点は block の角で、メッシュは x = 0, z = 0 が中心なので +0.5 ブロック（元の item モデルの 8）。`LowPolyMesh` は geo 向けに x を反転して焼くので、描くとき `scale(-1, 1, 1)` で戻す。元のボクセルは盾以外すべて z 軸まわり -45 度（中心 8,8,8）に傾けていて、display はその前提の値なので、メッシュ（刃を立てて作ってある）も同じだけ傾ける（盾は傾けない）。y の高さと握りは元のまま
- アセット: `lowpoly/items/<id>/<段>[_pullN].json`、`textures/lowpoly/items/<id>/<段>[_pullN][_plus5|_plus10]_atlas.png`（光る画像は `..._atlas_glow.png` = `*_glowmask.png` の白い画素にアトラスの色を載せたもの）。入れ方は `python tools/install_lowpoly_weapons.py`（入力 `docs/review/lowpoly_roughs/weapons_final/<id>/`。何度でも流せる）。base の書き換えと、元のボクセルの `models/item_voxel/` への退避も同じスクリプト
- 弓の引き: 一人称は自分、三人称は `RenderLivingEvent` の間だけ入れる生き物（`WeaponItemRenderer.holder`）から取る（モデルを替えずに段だけ変える）
- スイッチ: `LowPoly.weaponMeshes`（既定 true）。false で元のボクセル（`models/item_voxel/<名>.json`。`ModelEvent.RegisterAdditional` で読む）を同じ置き方で描く。そのため `item_voxel/` と `textures/item/3d/` は消さない。台本は `weaponmesh on|off`、階級を固定して撮るのは `weapontier common|rare|legend|off`（開発用）

確かめ（`python tools/gen_weapon_lp_shots.py` → `python tools/run_weapon_lp_shots.py -j 3` → `python tools/weapon_lp_sheet.py`。絵は `docs/review/previews/weapon_lp_<id>.png` がコンタクトシート、`_draw`・`_block`・`_world` が弓の引き・盾の構え・地面と額縁）:

- 全 13 個で、一人称・三人称 × 階級 3 × 強化 3、元のボクセル、地面（`PickupDelay` 付きの item）・額縁を撮った。三人称の向きは元のボクセルと合う（大剣・鉾で確認）。階級で輪郭、強化で縁の色と光る筋が変わる。弓は引きの 3 段で弦の位置が変わる。盾は構え中も盾の姿
- 負荷（armor stand 20 体が +10 の大剣を持つ、1280x720、垂直同期なし、他の作業と並行で目安）: 低ポリ 4.4〜5.1 ms/frame、ボクセル 8.1〜9.4 ms/frame（約 1.8 倍速い）。台本 `tools/shots/weapon_lp_perf.txt`
- 左利き: 8 本（大剣・鉾・弓・盾・爪・斧・鉤槍・鎚）を右利きと並べて撮り、鏡写しで持つ向きに問題なし（`tools/gen_weapon_extra_shots.py` → `tools/weapon_extra_sheet.py`。`docs/review/previews/weapon_lp_extra_left.png`）。左利きで弓を引く一人称・三人称も撮った
- 輝き（foil）: `stack.hasFoil()`（エンチャント・品の `foil`）のとき、本体のメッシュを `ItemRenderer.getFoilBuffer`（glint の描画型）へもう一度流す。光る画素（emissive）の層はそのまま。大剣・弓・盾の手（一人称・三人称）で紫の輝きを確認（`weapon_lp_extra_foil.png`）。地面の item・額縁は小さく輝きは見分けにくい
- 盾の三人称の構え（構えていないとき）: バニラの盾の display の平行移動 [10,6,-4] は、盾の板の中心が原点から -8 ずれる前提の値だった。メッシュ・元のボクセルは中心が原点なので、盾が腕から 1 枚ぶん横に離れて浮いていた。中心の位置を合わせて [2,-2,4]（左右とも）にした（`models/item/coral_shield.json`・`item_voxel/coral_shield.json`）
- 他の生き物の弓の引き絵: 引きの段は `holder`（`RenderLivingEvent` の間の生き物）の `isUsingItem` と使っている品の種類で決める（以前は同じ stack の参照比較で、ほかのプレイヤー・生き物の同期した stack では外れうる）。スケルトンに弓を持たせた三人称は撮ったが、横向きで弦の段までは見分けられなかった。未確認: ほかのプレイヤーの手の弓の引き絵の段（実機マルチ）、専用サーバーのマルチ

#### 4.6.3 立体防具の低ポリメッシュ（C 骨組み）

4 組（岩鯨・潮殻・雷翼・余燼）× 4 部位 × 階級 3（通常・レア・伝説）のメッシュ。アトラスと光る画像は強化（+0〜4 / +5 / +10）で選ぶ。コードは `client/lowpoly/LowPolyArmor`・`LowPolyArmorRenderer`（骨の付け方は GeckoLib の `GeoArmorRenderer` のまま、骨ごとに低ポリメッシュを流す）。入れ方は `python tools/install_armor_lowpoly.py`（入力 `docs/review/lowpoly_roughs/armor/c/`、作り方は `tools/rough/lowpoly/armor/`）。

- 一式（4 部位）の三角形: 岩鯨 842 / 958 / 1,050、潮殻 742 / 858 / 1,008、雷翼 864 / 980 / 1,080、余燼 627 / 699 / 804（通常 / レア / 伝説）。伝説は 1,100 以下。岩鯨・雷翼の伝説は背の環を軽くし（輪を 9 → 7 点、支柱を 1 区間）、レアより増える「背の弧・羽の 3 組目」をやめた（`style_c.LEAN`、`python tools/rough/lowpoly/armor/build_legend_lean.py` で岩鯨・雷翼の伝説だけ作り直せる。環・布は残る）
- 陰影: アトラスには面の向きの陰影（上が明るく下が暗い）が焼いてある。バニラの方向光（姿勢で回った法線）も強く効かせると、腕を上げて面が下を向いたとき（防具立ての腕上げ）に肩の板が二重に暗く沈む。防具の描画では法線を真上へ 0.4 だけ寄せて（`LowPolyArmorRenderer.SHADE_FLATTEN`、`LowPoly.drawBone` の `flatten`）、姿勢で極端に暗くならないようにした。バニラの陰（向きによる明暗）の効きはその分だけ弱くなる

#### 4.6.4 装飾品・補助の品の低ポリメッシュ（A 案。階級と体に付けた姿）

16 個。雷翼 = 嵐の護符・嵐纏の足環・嵐の目の羽根・風切りの羽根、余燼 = 熾りの護符・熾火の外套・余燼の灯・熾火の角杯・燠の灯、岩鯨 = 共鳴の護符・苔の護石・岩鯨の補強板、潮殻 = 潮鳴りの護符・月潮の真珠・潮見の貝・潮の真珠。形は各巨神の武器の A 案と同じ言葉（雷翼 = 風切羽の重ね、余燼 = 割れ板、岩鯨 = 肋の曲刃（鯨骨・石・苔）、潮殻 = 珊瑚の枝槍（真珠層・濡れた石・珊瑚）。材質は武器と共通）。嵐の護符の稲妻は、矢に見えないよう右へ折り返す 3 枚の折れ板。GUI は今の 32x32 の絵のまま（燠の灯だけ今まで絵が無くブロックの 3D を出していたので、メッシュから 32x32 の絵を作った）。

| 決めるもの | 選び方 |
| --- | --- |
| メッシュ | 品の階級（`ModGear#tier`。`ModGear` でない品はコモン）。コモン・レア・伝説の 3 段。レア = 部品を 1 つ足す + 青みの縁、伝説 = 輪郭を変える大きな一点 + 金の縁 + 縁がまだらに光る。三角形は身につける品 200 以下、護符・手に持つ品 150 以下（実際の階級の段は全部収まる） |
| アトラスと光る画像 | 階級ごとに 1 枚。**装飾品は鍛えない**（03 の 4 章）ので +5 / +10 の絵は無い |
| 光る画素 | アトラスの色のまま `entityTranslucentEmissive` で描き足す |

三角形（コモン / レア / 伝説。★ が実際の階級）: 嵐の護符 132 / 144★ / 144、嵐纏の足環 112 / 176★ / 176、嵐の目の羽根 100 / 112 / 136★、風切りの羽根 112★ / 124 / 124、熾りの護符 92 / 104★ / 104、熾火の外套 128 / 140★ / 140、余燼の灯 84 / 96 / 120★、熾火の角杯 96★ / 108 / 108、燠の灯 132★、共鳴の護符 136 / 148★ / 148、苔の護石 132 / 144★ / 164、岩鯨の補強板 116 / 128 / 164★、潮鳴りの護符 122 / 134★ / 134、月潮の真珠 154 / 166 / 196★、潮見の貝 132★ / 144 / 144、潮の真珠 100★ / 136 / 136。

仕組み（コードは `client/accessory/`）:

- **手・地面・額縁・頭**: `AccessoryItemRenderer`（`BlockEntityWithoutLevelRenderer`）。item モデルの base は `builtin/entity`（display はバニラの generated の表を土台にした全品共通の値）、gui は 32x32 の平面。メッシュは中心が原点・正面が -Z なので、+0.5 ブロック・y 軸まわり 180 度・x 反転（`LowPolyMesh` は x 反転で焼く）で置く。メッシュの選び方と描き方は `AccessoryMeshes`（手と体の描画が共有）
- **体に付けた姿**: `AccessoryWornLayer`（プレイヤーの描画に足す `RenderLayer`。`default`・`slim` の両方）。枠（`AccessoryData`）の 1・2 番（装飾）だけ描く。護符（0 番）は使って効く品で身につけないので描かない。付け場所と大きさ（モデルの部位の中の座標）: 足環 = 右足首（0.5 倍）、嵐の目の羽根 = 右耳の後ろ（頭、外へ傾ける 0.8 倍）、熾火の外套 = 背中（体、肩から垂れる 0.85 倍）、余燼の灯 = 腰の左（0.45 倍）、苔の護石 = 腰の右（0.5 倍）、岩鯨の補強板 = 背中（0.6 倍）、月潮の真珠 = 胸の前（0.5 倍）。一人称では自分の体が描かれないので出ない。ほかのプレイヤーにも見える（枠の同期を持ち主だけから、周りで追うプレイヤー全員へ広げた。`AccessorySetup.DATA`）。透明・観戦のプレイヤーは描かない。体のほかの装備（防具）の上に重なる
- 防具立て（armor stand）は装飾の枠を持たないので、付けた姿は出ない
- 燠の灯（ブロック）: 置いたブロックは今までのキューブのモデル（`block/cinder_lamp{,_hanging}`）のまま。非立方体のブロック描画は専用のブロックエンティティが要り、1 灯のために重いので入れない。持つ・落とす・額縁・頭は低ポリのメッシュ
- アセット: `lowpoly/items/<id>/<段>.json`、`textures/lowpoly/items/<id>/<段>_atlas.png`（光る画像は `_atlas_glow.png`）。形のラフは `tools/rough/lowpoly/accessories/{thunder,ember,whale,tide}.py`、書き出しは `build_final.py`（出力 `docs/review/lowpoly_roughs/accessories_final/`）、入れ方は `python tools/install_lowpoly_accessories.py`（何度でも。元の平絵は `models/item_flat/` へ退避）
- スイッチ（戻しの旗）: `LowPoly.accessoryMeshes`（既定 true。false で平絵、`item_flat/` を同じ置き方で描く）、`LowPoly.accessoryWorn`（既定 true。false で体に描かない）。台本は `accmesh on|off`、`accworn on|off`、`acctier common|rare|legend|off`、`accequip <id> [<id>]`

確かめ（`python tools/gen_accessory_lp_shots.py` → `./gradlew runShots -Pscript=tools/shots/acc_lp_<巨神>.txt` → `python tools/accessory_lp_sheet.py`。絵は `docs/review/previews/acc_lp_<巨神>.png`）。

## 5. これからモデルを作る手順

**geo の面の名前（GeckoLib の決まり。必ず守る）**: GeckoLib は X を鏡に写して焼くので、面の名前の位置が一般の読み方と違う。`west` の面は箱の x の**大きい**側、`east` の面は x の**小さい**側に付く（`north` は z の小さい側、`south` は大きい側、`up` `down` は同じ）。ラフの道具（`tools/rough/voxel.py`）は `west` を x の小さい側として読むので、ラフの geo をそのまま書き出すと、見える面だけを書き出した箱の東西の面が逆の側に付き、横から見ると中が見える（ベータで「一定の角度から見ると透ける」と言われた原因）。心臓（`install_heart_v4.py` の変換）は最初から合っている。それ以外の geo は `tools/fix_geo_face_sides.py --apply --fill` で直す（east/west の名前を入れ替え、north/south/east/west の画像の向きを GeckoLib に合わせ、元から面が抜けている所を塞ぐ）。書き出し直したら、そのあとにもう一度流す。`--check` が各 geo の「透ける穴」の数（休め姿勢で、あらゆる向きの光線が最初に裏の面に当たる数）を出す。

1. 元の geo とテクスチャを `tools/sources/hp_base/<種類>/` に控える（やり直しはここから）。
2. `tools/rough/hp_<モデル>.py`（`hp_sentinel.py` が見本）で: `hpkit.Model.load(名前)` → `round_all(rule=...)` → 穴（`round_cube(holes=)`）→ `add_box` の飾り → `hpkit.stats` と `hpkit.overlaps` で箱・面・重なりを確認。
3. `voxel` 描画で「今 / A / B / C」を並べたシートを `docs/review/high_poly_roughs/` に出し、review ページで選ぶ（新しい見た目はまずラフ）。A は自動の面取りだけ、B は大きな塊の丸め + 0.25 の飾り、C は B + 手で足す細部（浮き彫りの筋など）の型。
4. 採用案を `m.save(...)` で geo・テクスチャ・glowmask に書き出し、`assets/skyabyss/geo/...` と `textures/...` に入れる（取り込みは各家族の `install_*.py` に「hpkit の処理」を足す形。焼き済みの心臓は geo を直接差し替える）。採用しなかった案の assets は消す（`tools/README.md`「ラフの片付け」）。
5. 光る画素を足したら glowmask を置き、レンダラーの発光層を有効にする（`GuardRenderers.geo(..., glow=true)`）。光る画素ゼロの glowmask は落ちる。
6. `./gradlew compileJava --offline`、`SKYABYSS_SELFTEST=1 ./gradlew runServer`、`runShots` で前後を撮る。

## 6. 確かめ方

| 確認 | 道具 | 合格 |
| --- | --- | --- |
| 重なり・埋まり | `hpkit.overlaps`（= `voxel.check_overlaps`） | 0 件 |
| 光る画素 | glowmask に光る画素が 1 個以上（`EmberSelfTest.glowPixels` と同じ見方） | 1 個以上、または発光層なし |
| 箱数・面数・最小の箱 | `hp_geo_stats.py`、`hpkit.stats` | 2.2 の予算の内 |
| アニメ | ラフの `voxel.load_geo(pose=)` で先に確認。台本で攻撃・歩行の姿勢を撮る（`cmd data merge` で状態を作る）。`pose_holes` は開く面が 0（`install_mob` が assert） | 隙間・めり込みなし |
| 前後の絵 | `runShots` の台本（`tools/shots/hp_sentinel.txt` が見本。`-PshotsName=<名前>_before/after` で 2 回。台本は OS の入力を使わない） | 同じ構図で並べる |
| フレーム時間 | 台本の `perf <tick> <名前>`（NoAI で並べる。巨神は `titan rest ... here` で止める。タイトルの世界は毎回消して作り直す） | 同じ台本 2 回の平均で、2.2 の予算を超えない |

回転の符号: ラフの回転は GeckoLib の符号（X・Z を反転）に直して描く（`to_view`）。

## 7. 既知の問題・制限

- **余燼の火口の白い岩の縞**: 火口の円錐の壁（厚さ 2・高さ 72 の薄い板が x 方向に 2 単位ずつ重なる所）の面に斜めの縞が出る。取り込み前から出ていて、模様は撮るたびに変わる。同じ平面の面の取り合いは geo に無く、面の接面を切り分ける案（面 +1,500 で上限超え）とテクスチャの隙間の色直しは効かなかった。残る説は、遠くで重なる薄い板の深度の精度か、息のアニメの拡大縮小の揺れ。近づいて見る場面が少ないので保留。直すなら、薄い壁板の重なりを 1 枚の厚い板にまとめる（火口だけの小さな作り直し）。撮る台本 `tools/shots/ember_crater_stripes.txt`（`-PshotsName=<名前>`）。
- `tools/install_ember_titan.py` は大きな配列を使うので、空きメモリが少ないと止まる。島の検査（`drop_islands`・`islands_fix`・`islands`）は組の箱の中だけを `bincount` で数える作りにして、ピーク 2.4 GB に収めてある。
- 心臓 12 個の面が 4,565〜5,990 と上限 6,000 に近い。ゲーム内の前後は撮っていない（段階 2・3 は `docs/review/previews/*_heart.png` と比較画像で確認）。描画時間も測っていない。
- 自己テスト `ash smith` が新しいワールドでまれに落ちたことがある（`the smith outside the room was not brought back`）。検査の置き方の問題で、`AshSmithSelfTest.checkBattle` は動かす距離を連れ戻しの距離（17）を少し超える +22 に縮め、先にそのチャンクを `getChunk` で読む。同じ型の検査（心臓・雷獣）は触っていない。
- 雷乞いの衣と羽守の胴は「ぼろ布の裾の房」が無い。房を戻すなら、衣を切り直さずに面を 600 減らす別の策が要る。

## 8. 未決

00 の 192〜196 と同じ項目。

| # | 問い | 今 |
| --- | --- | --- |
| 1 | 顔・手だけ 64px 密度のテクスチャにするか（顔の柄・目・口を描き直す） | 保留。32px のまま。描きたいなら、先に語り部・イサナの顔だけ 64px にして見る |
| 2 | 描画の予算（2.2）の値は**この PC** のもの。低い PC を想定するか | このまま。低い PC なら敵 1,000 面、中ボスは潮招き B 案と鍛え手の二段丸めを一段にする。ユーザーの PC で `perf` を測り直す（`tools/shots/hp_midboss_perf.txt`） |
| 3 | 番兵の目が光る（新しい特徴）をこのまま採るか | 採っている。戻すなら `tools/sources/hp_base/entity/` の 2 ファイルと `GuardRenderers.java` の `true` → `false` |
| 4 | 灰被りの上限を、状態ごと（殻の姿・燃え上がり）で数えてよいか | 合計で守っている。状態ごとなら、燃え上がりの炎に 400 面ほど足せる |
| 5 | 余燼の火口の縞（7）を直すか | 保留 |

## 9. 低ポリメッシュの描画（非キューブのモデル）

新しい統一スタイル: 三角形の低ポリメッシュ + マイクラ風のドット絵テクスチャ（最近傍サンプリング）。GeckoLib 4.9.3 は geo の `poly_mesh` を読むだけで描かないので、**骨とアニメは GeckoLib、骨ごとのメッシュは自前で描く**。1〜8 章の箱のモデルとは別の道で、モブごとに切り替える。実装は `client/lowpoly/`。

### 9.1 素材と形式

| ファイル | 中身 |
| --- | --- |
| `assets/skyabyss/lowpoly/<name>.json` | メッシュ（下の形式） |
| `assets/skyabyss/textures/lowpoly/<name>.png` | アトラス（32 画素/ブロック = `texels_per_unit` 2）。`<name>_glowmask.png` があれば光る画素（目）。光る画素 0 のマスクは置かない（GeckoLib が落ちる） |
| `assets/skyabyss/geo/entity/<name>_lowpoly.geo.json` | 骨だけの geo（cube なし。メッシュの骨名・pivot と同じ） |
| `assets/skyabyss/geo/entity/<name>_lowpoly_debug.geo.json` | 骨ごとにメッシュにぴったりの箱を 1 つ持つ geo（重ね合わせの確認用） |
| `assets/skyabyss/animations/entity/<name>.animation.json` | 元のものをそのまま使う（骨名が同じなので変わらない） |

メッシュ JSON: `{format_version:1, units:"1/16 block, y up, front = -Z", texture, texture_size:[w,h], texels_per_unit, bones:{<骨名>:{parent, pivot:[x,y,z], vertices:[[x,y,z]..], uvs:[[u,v]..], triangles:[[i,j,k]..]}}}`

- 頂点は**骨の相対でなくモデル全体の絶対座標**で、GeckoLib の geo の cube の座標（Bedrock 流、1/16 ブロック、y 上、正面が -Z）と同じ。GeckoLib が cube を焼くときの x 反転は、メッシュを焼くときに同じように掛ける（`LowPolyMesh`）
- uvs は頂点ごと。0..1 に正規化し v は画像の上から下（1 を超える値があれば画素座標とみなして割る）。triangles は頂点の添字で、geo 空間で反時計回りが外向き
- 盾・武器の別メッシュは `"socket": true`（または `attach`）を持ち、頂点は**受け口の骨の pivot を原点とした相対座標**（`geo/gear/` と同じ置き方）。自分のアトラスを持てる

### 9.2 描画の仕組み

- `LowPolyMeshes`（リソースの再読み込みで `lowpoly/*.json` を読む）が `LowPolyMesh` へ焼く: 骨ごとの頂点・uv・面の法線の配列。三角形は QUADS の描画型に合わせて四角にする（位置も uv も平行四辺形に並ぶ隣の二枚は一枚に、残りは 4 つ目の頂点を繰り返す）。法線は面ごと（フラット）に先に計算する
- `LowPolyGeoRenderer`（`GeoEntityRenderer` の派生）が `renderCubesOfBone` を差し替え、cube の代わりにその骨のメッシュを GeckoLib が渡す buffer へ流す。骨のポーズは GeckoLib がかけ終えた PoseStack をそのまま使うので、向き・アニメ・被弾の赤・死に方・明るさ・発光層（`AutoGlowingGeoLayer`、マスクがあるときだけ）は cube のモデルと同じ。毎フレームは頂点の座標変換だけ（頂点 1 つあたり行列 1 回、法線は面ごとに 1 回）
- テクスチャは `textures/lowpoly/<name>.png`、描画型は GeckoLib の `entityCutoutNoCull`（最近傍）
- 受け口: `GearLayer.lowPoly(renderer, "socket_off", "<meshName>")`（`LowPolyGearLayer`）が受け口の骨のポーズでメッシュを描く。アイテム・geo を描く元の `GearLayer` は他のモブのためにそのまま動く
- **スイッチ**: `LowPoly.enabled`（既定 true）。false にすると低ポリ対応のモブは元の cube の geo・テクスチャ・`GearLayer` に戻る。モブごとには `GuardRenderers` の登録の行を `LowPolyGeoRenderer` から `geo(...)` へ戻せば元に戻る。台本では `lowpoly on|off|debug|nodebug`

### 9.3 予算

- 普通の敵: **1 体 約 600 三角形**、テクスチャ密度 32/ブロック。画面に多く見える数は 8〜12 体
- 負荷は面の数でなく送る頂点の数（四角 = 4 頂点）に比例する。cube の GeckoLib の描画より 1 面あたりずっと軽い（9.5）
- 光る目があると発光層がもう一度メッシュを描く（頂点が 2 倍）

### 9.4 入れ方

`python tools/install_lowpoly.py <name> [--gear メッシュ.json:アトラス.png:名前:受け口の骨]`（`tools/README.md`）。`docs/review/lowpoly_roughs/<name>_lowpoly_mesh.json`・`_lowpoly_atlas.png`・`_lowpoly_glowmask.png` を assets に入れ、骨だけの geo と debug geo を書く。元の geo とアニメは触らない。入れ直しは同じ名前で何度でもよい。

### 9.5 骨の番兵での確かめ（`tools/shots/lowpoly_*.txt`、`docs/review/previews/lowpoly_*.png`）

- 重ね合わせ: `lowpoly_align_front/side.png`。骨ごとの箱（GeckoLib の cube）の面にメッシュの端がぴったり付く（x 反転も合っている）
- 動き・被弾・夜の松明: `lowpoly_walk_a/b`・`lowpoly_attack`・`lowpoly_hurt`・`lowpoly_night_torch_front/side`。盾は受け口の骨に付いて動く
- 負荷（この PC、1280x720、垂直同期なし、目の発光あり、他の作業と並行なので目安）: 番兵 36 体は低ポリ 7.8〜11.0 ms、cube（1,416 面）24.4〜29.7 ms。20 体は低ポリ 5.2〜7.1 ms、cube 12.0〜15.1 ms。目のマスクが無かった先の試走では 36 体 3.3〜4.3 ms 対 cube 19.5〜23.4 ms

### 9.6 採用した低ポリ（番兵のあとの 5 体。`python tools/install_lowpoly_five.py` で一度に入れ直せる）

| モブ | 採用 | 三角形 | 骨 | 入力（`docs/review/lowpoly_roughs/`） |
| --- | --- | --- | --- | --- |
| 骨の番兵 bone_sentinel | 先の試作 | 584 + 盾 60 | 9 | `bone_sentinel_*` |
| 鯨骨の番兵長 whalebone_captain | A 石鎧の将 | 1,266 + 鉾 148 | 20 | `whalebone_captain/a/`（鉾 `captain_pike` は `socket_main`、1.8 倍込み） |
| 苔這い moss_crawler | B 骨・棘 | 601 | 27 | `lp_moss_crawler/b/` |
| 肋の射手 rib_archer | C ひげ板の頭巾 | 605 + 弓 102（弦の骨 2） | 10 | `rib_archer/c/`（弓 `rib_archer_bow` は `socket_off`） |
| 水路の顎 waterway_maw | B 提灯鮟鱇 | 643 | 11 | `waterway_maw/b/` |
| 岩鯨の心臓 stone_whale_heart | A 地層の板 | 1,336 / 1,322 / 1,364（段階 1 / 2 / 3） | 30 / 32 / 47 | `lp_whale_heart/a/p1..p3`（段階ごとに別の geo・メッシュ・アトラス） |
| 磯走り reef_scuttler | A 岩礁蟹 | 514 | 6 | `reef_scuttler/a/` |
| 泡吹き bubble_puffer | A まんまる提灯 | 488 | 6 | `lp_bubble_puffer/a/`（ふくらみは体の骨の拡大と棘の骨のアニメ。ふくらんだ姿も棘・体とも合っている） |
| 潮招き tide_beckoner | A 瓦の大蟹 | 1,489 | 12 | `lp_tide_beckoner/a/`（アトラス 512） |
| 潮殻の心臓 tide_heart | B 珊瑚の殻 | 1,561 / 1,155 / 1,301（段階 1 / 2 / 3） | 14 / 14 / 14 | `lp_tide_heart/b/p1..p3`（`tide_heart_pN`。`TideHeartRenderer` は `LowPolyGeoRenderer` の派生で、キューブのときだけ自前の発光層、低ポリのときは `autoGlow()` の発光層） |
| 珊瑚憑き coral_thrall | A 岩礁の溺れ人（第 2 弾） | 722 + 銛 118 | 11 | `coral_thrall2/a/`（銛 `coral_spear` は `socket_main`。像は同じメッシュ + 骨 `statue_coral`、白い石のアトラス `coral_thrall_statue.png`、像の銛は `coral_spear_statue`） |
| 羽守 feather_warden | A 鷲兜の番人 | 610 + 鉤槍 109 | 12 | `lp_feather_warden/a/`（鉤槍 `hooked_glaive` は `socket_main`） |
| 雷蛾 static_moth | A 綿毛の雷蛾 | 622 | 5 | `lp_static_moth/a/`（両面の薄い翅。帯電の光は `static_moth_charge.png`） |
| 雷乞い thunder_supplicant | A 風見の籠 | 600 + 杖 76 | 9 | `lp_thunder_supplicant/a/`（杖 `supplicant_staff` は `socket_main`） |
| 雷獣 raiju | A 嵐の狼 | 1,305（アトラス 512） | 17 | `lp_raiju/a/` |
| 雷翼の心臓 thunder_wing_heart | A 風切りの殻 | 1,127 / 1,519 / 1,857（段階 1 / 2 / 3） | 13 | `lp_storm_heart/a/p1..p3`（`storm_heart_pN`） |

入れ方で直した共通部分（`tools/install_lowpoly.py` と `client/lowpoly/`）:

- **骨の休めの回転**: 骨だけの geo（と debug geo）に、元の geo の `rotation`（メッシュ JSON に書いてあればそちら）を書く。射手・苔這いの root の yaw 180、射手の `socket_off`、心臓の殻の板や継ぎ目は、これが無いと向きがずれる。メッシュの頂点は、回転をかける前の geo の cube と同じ座標。GeckoLib が掛けた PoseStack の上にそのまま流すので、cube を回した骨の下に置いたのと同じになる。親・pivot・回転が元の geo と違えば入れるときに警告する
- **回転の符号の流儀**: 試作ごとに回転の符号の書き方が違っても、メッシュは回さない静的なものなので、骨の回転は元の geo をそのまま使えば合う（アニメは触っていない）
- **動く部品のある武器**（弓の弦）: 複数の骨のメッシュは、骨だけの `geo/gear/<名>_lowpoly.geo.json` と元の `animations/gear/<名>.animation.json` で動かす（`GearLayer.lowPolyAnimated`）。今は動く武器の発光には未対応
- **武器の光る画素**: マスク（白）に元のアトラスの色を載せた `textures/lowpoly/<名>_glow.png` を焼く（`LowPolyMeshes` はこれを探す）
- **光る画素 0 のマスクは入れない**（GeckoLib が落ちる）。段階ごとのモブは `LowPolyGeoRenderer` に GeoModel と「メッシュ名の関数」を渡す（心臓は `StoneWhaleHeartModel.meshName`）
- 元の箱の描画は、5 体とも `LowPoly.enabled = false` で出る。動きの確認用に `/skyabyss dev anim` は GuardMob 以外の GeckoLib のモブ（水路の顎）にも効く

確かめ（台本 `tools/shots/lp_<名>.txt`、`tools/gen_lowpoly_five_shots.py` が書く。絵は `docs/review/previews/lp_*.png`）: 待機の前・斜め・横・後ろ、顔、番兵長の薙ぎ払い・叩きつけ・咆哮、射手の構えと弦、苔這いの飛びかかり・被弾・死、水路の顎の噛みつき、心臓の段階 1〜3 と登場・倒れる。動きは同じ姿勢の箱版（`cubes_*`）と並べて見た。負荷（1280x720、垂直同期なし、苔這い 20 + 射手 10 の 30 体、`tools/shots/lp_perf.txt`）: 低ポリ 6.5〜7.4 ms/frame、箱 22.1〜24.5 ms/frame（約 3.3 倍速い）。

潮殻の背の 4 体は `python tools/install_lowpoly_tide.py` で一度に入る（珊瑚憑きは下の雷翼の背のスクリプトで入る）。磯走り・泡吹きは `TideMobRenderers.lowPoly()`、潮招きは `TideBackClient.BeckonerRenderer`、心臓は `TideHeartRenderer`。どれも `LowPoly.enabled = false` で元の箱に戻る。確かめの台本は `python tools/gen_lowpoly_tide_shots.py` が `tools/shots/lp_tide_{scuttler,puffer,beckoner,heart}.txt` に書く（心臓の段階 2・3 は NoAI だと段階 1 のままなので、体力を下げた AI ありの心臓が自分で吠えて進むのを待って撮る）。絵は `docs/review/previews/lp_tide_*`（箱版は `cubes_tide_*`、モブごとの比較シートは `lp_tide_<名>_sheet.png`、心臓の登場・倒れるは `lp_tide_heart_cinematic_sheet.png`。`python tools/lowpoly_tide_sheets.py`）。負荷（`tools/shots/lp_tide_perf.txt`、磯走り 15 + 泡吹き 10 + 潮招き 2 = 27 体、1280x720、垂直同期なし）: 低ポリ 5.1〜5.6 ms/frame、箱 15.5〜16.8 ms/frame（約 3 倍速い）。

雷翼の背の 5 体と珊瑚憑きは `python tools/install_lowpoly_storm.py` で一度に入る（珊瑚憑き・羽守・雷蛾・雷乞い・雷獣・心臓の段階 1〜3）。描画は羽守・雷蛾・雷乞いが `StormMobRenderers.MobRenderer`（`LowPolyGeoRenderer` の派生。鉤槍・杖は `GearLayer.lowPoly`、元の `GearLayer` は `activeWhen(!LowPoly.enabled)`）、珊瑚憑きが `TideMobRenderers.ThrallRenderer`（像のときは `LowPolyGeoRenderer.Model` を継いだ `ThrallModel` が白い石のアトラスを返し、骨 `statue_coral` を見せ、像の銛 `coral_spear_statue` に替える）、雷獣が `RaijuRenderer`（頭を向けない `Model("raiju", false)`）、心臓が `StormHeartRenderer`。どれも `LowPoly.enabled = false` で元の箱に戻る。

- **雷蛾の帯電の光**: 箱の `static_moth_charge.png` は UV が違うので、低ポリでは翅の面のアトラスを明るくした `textures/lowpoly/static_moth_charge.png` を `ChargeLayer` が足す（スクリプトが焼く）。
- **雷獣**: 低ポリでは `LowPolyGeoRenderer` の発光層（`autoGlow()` = `LowPoly.enabled`）、箱のときは自前の `GlowLayer`。
- **雷翼の心臓の発光**: `AutoGlowingGeoLayer` は光る画素に面の陰をかけるため、核・稲妻が灰青に沈んだ（実測で本来の色の 0.6 倍）。心臓は低ポリでも箱と同じく目の光で足す（`StormHeartRenderer.GlowLayer`、`autoGlow()` = false）。足す絵は、アトラスの光る画素の色（0.8 倍）だけを載せた `textures/lowpoly/storm_heart_pN_glow.png`。ラフの段階 2・3 は、白飛びした光と、石の殻（`heart`・`vessel`）にばらまかれた 1 画素の光（ゲームではブロックの点に見えた）があったので、インストール先のコピーだけで、殻の骨の光る画素を消し、白・ほぼ白の光る色を氷色（186,220,250 など）に和らげた。ラフの入力は触らない。ゲームでは段階 3 の核は淡い氷色の六角、稲妻は青い線で読める。
- **確かめ**: 台本は `python tools/gen_lowpoly_storm_shots.py` が `tools/shots/lp_storm_{warden,moth,moth_charged,supplicant,raiju,heart_p1,heart_p2,heart_p3,perf}.txt` と `lp_coral_thrall.txt` に書く（心臓は段階ごとに別の台本。段階 2・3 は NoAI だと段階 1 のままなので、体力を下げた AI ありの心臓が吠えて進むのを待ち、そのあと NoAI にして原点へ戻して撮る。珊瑚憑きの像は `tidemob tide high` で潮を満ちに止めてから）。比較シートは `python tools/lowpoly_storm_sheets.py` が `docs/review/previews/lp_storm_<名>_sheet.png`・`lp_coral_thrall_sheet.png` に書く（上が低ポリ、下が箱）。負荷（`tools/shots/lp_storm_perf.txt`、羽守 8 + 雷蛾 8 + 雷乞い 8 + 雷獣 2 = 26 体、1280x720、垂直同期なし、他の作業と並行）: 低ポリ 6.4〜7.3 ms/frame、箱 19.8〜22.8 ms/frame（約 3 倍速い）。
- 雷蛾の帯電の明るさは、台本（`lp_storm_moth_charged`）では動く蛾が画面からはずれ、明るい翅を確かに撮れていない（箱版との並べも未確認）。

#### 巨神本体（岩鯨・潮殻・雷翼・余燼。近・中・遠の 3 段 LOD、`python tools/install_lowpoly_titans.py` で一度に入る）

| 巨神 | 採用 | 三角形 近 / 中 / 遠 | 骨 | アトラス 近 / 中 / 遠 | 入力（`docs/review/lowpoly_roughs/`） |
| --- | --- | --- | --- | --- | --- |
| 岩鯨 stone_whale | C 皮が岩になる鯨 | 4,355 / 980 / 242 | 10 | 1024 / 512 / 256（遠は光る画素なし） | `lp_stone_whale/c/{near,mid,far}_*` |
| 潮殻 tide_shell | B 段になった珊瑚礁の島 | 4,554 / 1,481 / 466 | 11 | 同上 | `lp_tide_shell/b/` |
| 雷翼 thunder_wing | A 嵐の大鷲（薄い羽根は両面） | 3,528 / 1,436 / 452 | 14 | 同上 | `lp_thunder_wing/a/` |
| 余燼 ember_titan | C 皮が岩になる火蜥蜴 | 5,414 / 1,426 / 373 | 13 | 同上 | `lp_ember_titan/c/` |

- テクセル密度は近 2 / 中 1 / 遠 0.5（1 単位あたり）。段ごとに別のモデル名で入れる: 近 `<名>`、中 `<名>_mid`、遠 `<名>_far`（元の箱の geo `geo/entity/<名>{,_mid,_far}.geo.json` と同じ名前なので、骨・pivot・休めの回転は 3 段とも元と一致する。入れるときの警告は 0）。`lowpoly/<名>*.json`、`textures/lowpoly/<名>*.png`（`_glowmask.png` は光る画素があるときだけ）、`geo/entity/<名>*_lowpoly{,_debug}.geo.json`。
- 描画: 4 体とも `LowPolyGeoRenderer` の派生（岩鯨 `client/titan/StoneWhaleRenderer`、潮殻 `TideShellRenderer`、雷翼 `StormWingRenderer`、余燼 `EmberTitanRenderer`）。段の選び方は今まで通り（カメラから体の軸までの距離）を `LowPolyLodModel`（`client/lowpoly/`。`lod()` が 0 近・1 中・2 遠を返す）に移しただけ。段ごとにモデル名・geo・アトラス・メッシュが替わる（心臓の段階と同じ仕組み）。`LowPoly.enabled = false` で、元の箱の geo・`textures/entity/<名>.png`・自動の発光層に戻る。岩鯨の鎖の描画（`render`）、4 体の向き（`applyRotations`）は触っていない。
- 発光: 岩鯨・潮殻・雷翼は `LowPolyGeoRenderer` の `autoGlow`（目・刻文・稲妻の筋・潮門・手首の瘤。マスクのある段だけ）。余燼は光の強さを巨神の様子で変える `DimmableGlowLayer`（`autoGlow()` を false にして、箱・低ポリのどちらにも足す）。
- 背のディメンション（ブロックの複製）は触っていない（もう案に合わせて作り直してある）。
- **当たり・乗り場との整合**（`python tools/titan_align_check.py` が、メッシュ・箱の geo に対して座席の面の高さ・鎖の取り付け・潮門・口を測る）:
  - 岩鯨: 頭の座席の面は箱より 0〜0.8 ブロック低い（メッシュ 31.1〜32.0）ので座席の高さを下げた（`SEATS` を上 32.0 / 31.2 に）。錨石（後ろ 30.5・左 10.5・上 19.5）と鎖の根元は今のまま合っている（メッシュの錨石は y 31.7〜41.1 単位で、鎖の根元 28 単位はその直下）。頭の乗り場の鎖の経路（前 44、頬の外）も頬の面から 1 ブロック以内。**胸びれの付け根は左右で非対称**（geo の `fin_left` pivot x=26、`fin_right` x=-31）。そのままにしてある。
  - 潮殻: **要修正だったので直した**。甲羅が 34 → 47 ブロック（外の段 34、中の段 41、上の段 47）、中心に塔（高さ約 71）が立つ。旧座席（前 11・高さ 34.4 と前 17・高さ 36.4）は段の中に埋まるので、塔の前の上の段（高さ 47、平ら。塔は横 ±6・前 -4〜+6）の前 10 と 13、高さ 47.4 に動かした（`TideShellEntity.SEATS`）。光を測る甲羅の頂 `DOME_TOP` は 34 → 47。潮門は合っている（アーチ x 33〜38・後ろ 25〜39・高さ 5〜22.6 ブロックが、判定の横 29〜39・後ろ 27.5〜36.5・高さ 1.5〜15 と重なる。敷居は水面 3.5 の 1.5 上で、旧は 1）。
  - 雷翼: 合っている。足（左右 ±6、メッシュの脚は横 0.9〜11.1・後ろ 5.5〜21、足先は底の 6.6 上）、腰の羽根の門（高さ 25〜35）、頭の先（前 62）は箱と同じ。昇り風の柱は足の外に立ち、台本の画で足の真下に出ている。
  - 余燼: **口が合わない（要判断）**。メッシュ案 C は口が閉じている: 顎の上面が 3.8 ブロック、頭の下面が 4.0 ブロックで、あいだの隙間は 0.2 ブロック。顎のアニメは 6 度しか開かない（口の位置で 0.85 ブロック）。箱版は舌の上 3 から上顎の下 7.5 まで 4.5 ブロックの空洞が先端から奥へ続いていた。判定（`inMouth`: 横 ±2.5・前 81〜85・高さ 2.5〜7）と息の扇は、メッシュの頭の中に入る。口に入るのは見た目ではただの頭への突入になる。口の空洞を作る（案 C のメッシュに洞を足す、または顎のアニメを大きく開く + 地面の高さを舌の上に合わせる）かの決定が要る。体の寸法は合っている（鼻先 前 104、尾の先 後ろ 124、尾根の縁 69.5 ≒ 68）。
- 画面外の判定（`getBoundingBoxForCulling`）: 潮殻の塔が 73 ブロックに伸びたので、上へ 60 → 80、余燼は頭の後ろの角が 96 ブロックなので上へ 94 → 100。岩鯨（上 36.5、前後 -81.5〜66、横 ±37）と雷翼（翼 ±115、上 45）は今の範囲の内。
- 実機の確かめ: 台本は `python tools/gen_lowpoly_titan_shots.py` が `tools/shots/lp_titan_{whale,tide,storm,ember}.txt` に書く（近・中・遠 × 地面から・少し上から、座席・鎖・潮門・昇り風・口・顔・尾根のクローズアップ。メッシュ `lp_titan_<名>_<視点>.png` と箱 `cubes_titan_<名>_<視点>.png`）。比較シートは `python tools/lowpoly_titan_sheets.py` が `docs/review/previews/lp_titan_<名>_sheet.png`（上がメッシュ、下が箱）に書く。**遠景の撮影には、プレイヤーの追跡の範囲が要る**: 統合サーバーがエンティティを送る距離は、プレイヤーが申告した見える距離で決まる。`render` の台本の手順は設定を書き換えるだけで申告し直さず、約 190 ブロックより遠い巨神が消えていた（`ShotRunner` の `render` に `broadcastOptions()` を足して直した）。普通の遊びでも、見える距離が 12 チャンクなら、雷翼の遠景（軸から 240 ブロック超）はそもそも見えない。
- 負荷（1280x720、垂直同期なし、近景に巨神が画面いっぱい、他の作業と並行なので目安。`perf 300` ×2 の平均、ms/frame）: 

| 巨神 | 低ポリ | 箱 | 
| --- | --- | --- |
| 岩鯨 | 4.1〜4.7（先の試走 3.2） | 6.5〜6.8（先の試走 8.7） |
| 潮殻 | 2.5〜2.9 | 11.0〜11.2 |
| 雷翼 | 5.1〜5.8 | 10.4〜10.9 |
| 余燼 | 5.2 | 11.8〜12.2 |

余燼の背の敵・心臓・鍛え手、NPC、見る者（`python tools/install_lowpoly_ember_npc.py` で一度に入る）:

| モブ | 採用 | 三角形 | 骨 | 入力（`docs/review/lowpoly_roughs/`） |
| --- | --- | --- | --- | --- |
| 灰被り ash_thrall | A 灰衣の火番 | 726 + 火掻き棒 152 | 20 | `lp_ash_thrall/a/`（殻と燃え上がりは同じメッシュ・UV で、骨 `crust_*` / `flare_*` の出し分けと、アトラス `_atlas` / `_atlas_flared`・マスクを替える） |
| 火の粉蛾 ember_moth | A 燃え殻の灰蛾 | 683 | 5 | `lp_ember_moth/a/`（両面の薄い翅） |
| 燠虫 cinder_roller | B 熔皮の棘玉 | 550 | 37 | `lp_cinder_roller/b/`（丸まっている間の脚・触角・尾肢・節の間の膜の骨隠しは、箱のときからの `RollerRenderer.preRender`。そのまま効く） |
| 鍛え手 ash_smith | A 玄武の鍛冶王 | 1,517 + 大鎚 104 + 火箸 124 | 15 | `lp_ash_smith/a/`（大鎚 `smith_hammer` は `socket_main`、火箸 `smith_tongs` は `socket_off`） |
| 余燼の心臓 ember_heart | C 黒曜の鱗 | 1,570 / 1,567 / 1,661（段階 1 / 2 / 3） | 13 | `lp_ember_heart/c/p1..p3`（`ember_heart_pN`） |
| 語り部 storyteller | A | 714 | 11 | `lp_storyteller/a/`（滑らかなボクセル版の geo は `LowPoly.enabled = false` の箱の描画として assets に残る） |
| イサナ whale_chaser | A | 736 | 11 | `lp_isana/a/` |
| 見る者 watcher | A | 300 | 7 | `lp_watcher/a/`（半透明のまま。単層のメッシュ） |

- **描画**: 敵 3 種は `EmberMobRenderers.MobRenderer`（`LowPolyGeoRenderer` の派生。発光は `LowPolyGeoRenderer` の `AutoGlowingGeoLayer`）、灰被りは `ThrallModel` が燃え上がりのときだけ `textures/lowpoly/ash_thrall_flared.png` を返す（火掻き棒は `GearLayer.lowPoly(.., "socket_main", "ash_poker")`、箱の `GearLayer` は `activeWhen(!LowPoly.enabled)`）。心臓は `EmberHeartRenderer`、鍛え手は `AshSmithClient.SmithRenderer`（大鎚・火箸は `GearLayer.lowPoly`）、NPC は `NpcRenderer`（`LowPolyGeoRenderer.Model` を継ぎ、頭が相手を向く処理はそのまま）、見る者は `WatcherRenderer`。どれも `LowPoly.enabled = false` で元の箱の geo・テクスチャ・`GearLayer` に戻る。
- **発光**: 心臓と鍛え手は `autoGlow()` = false。`AutoGlowingGeoLayer` は光る画素に面の陰をかけるので、暗い部屋で沈む（箱のときと同じ理由）。スクリプトが焼く `textures/lowpoly/ember_heart_pN_glow.png`・`ash_smith_glow.png`・`ash_smith_glow_white.png`（鞴が白く光る間・怒りの白い光。アトラスの光る画素の色だけを黒の上に置き、1 画素に足す強さは 0.7〜0.8 倍）を、箱のときと同じ `RenderType.eyes`（陰なし・足し合わせ）の層で重ねる。段階 2 の「白熱」は箱では黄が飛んでいたが、メッシュでは核の形が読める。
- **見る者**: `WatcherRenderer` は `LowPolyGeoRenderer` の派生で、半透明の描画（`entityTranslucent`）と「薄れる」濃さ、目のにじみ（glint）は箱のときのまま。目の座標は、箱では骨 `eyes` の cube の頂点の平均だったが、低ポリの geo には cube が無いので、メッシュの骨 `eyes` の頂点の平均（焼いたあとの座標）から取る。目の光は `textures/lowpoly/watcher_glow.png`（アトラスの光る画素の色を黒の上に）。台本用に `ShotRunner` に `watcher x y z yaw`（足元の位置・体の向き。消えずに立つ。`WatcherClient.placeForShot`）を足した。
- **確かめ**: 台本は `python tools/gen_lowpoly_ember_npc_shots.py` が `tools/shots/lp_ember_{thrall,moth,roller,smith,heart_p1,heart_p2,heart_p3,perf}.txt`・`lp_npc.txt`・`lp_watcher.txt` に書く（新しい世界は最初の約 360 tick が開幕の字幕なので、最初の絵の前に 330 tick 待つ。心臓の段階 2・3 は体力を下げた AI ありの心臓が吠えて進むのを待つ。燠虫の丸まりは、AI の燠虫と生存者の人が 3〜14 ブロックにいると丸まる）。比較シートは `python tools/lowpoly_ember_npc_sheets.py` が `docs/review/previews/lp_ember_{thrall,moth,roller,smith,heart,heart_cinematic}_sheet.png`・`lp_npc_{storyteller,isana}_sheet.png`・`lp_watcher_sheet.png` に書く（上がメッシュ、下が箱）。見る者の夜の 14・40 ブロックは暗いので、シートでは明るさを 3 倍にしてある（元の絵は `run/shots/lp_watcher/` に出る）。
- **負荷**（`tools/shots/lp_ember_perf.txt`、灰被り 8（殻 4・燃え上がり 4）+ 火の粉蛾 8 + 燠虫 8 + 鍛え手 1 = 25 体、1280x720、垂直同期なし、他の作業と並行）: 低ポリ 7.6〜11.3 ms/frame（最初の一回は 11.3、あとの二回は 7.6）、箱 16.8〜27.4 ms/frame（2〜3 倍ほど速い）。
- **滑らかな NPC のラフ**（`docs/review/smooth_npc_roughs/<名前>/<案>/`）は消した（`tools/install_smooth_npcs.py` が読んでいたが、その入力は `LowPoly.enabled = false` の箱の描画としてすでに assets に入っている。スクリプトは回らなくなったので、冒頭に書いた）。

## 旧節番号の対応

コードのコメントにある旧い節番号が、今どこにあるか。この書類は担当ごとの記録で見出し番号が重複していたので、見出しの題も添える。

| 旧 | 今 |
| --- | --- |
| 0 / 0.1 | 4（家族ごとの今の形） |
| 0.2 | 2.1 |
| 0.3 | 7（火口の縞・メモリ）。選ばれなかった案の片付けは 5 の 4 |
| 1 / 1.1〜1.6 | 4.1〜4.6 |
| 1.7 | 1.1 の「学んだこと」 |
| 2 | 1 |
| 2.1 / 2.2 / 2.3 / 2.4 | 1.1 / 1.2 / 1.3 / 1.4 |
| 3 / 3.1 | 2 / 2.1 |
| 3.2 | 2.2 |
| 3.3 | 2.3 |
| 4 | 3 |
| 5 | 5 |
| 6 | 6 |
| 7（試作の結果） | 1.1 の「学んだこと」、4.4（番兵）、4.2（潮殻の心臓）、4.3（雷獣） |
| 8（作り直しの担当の分け方） | （削除: 担当の分け方は作業の経緯。敵・中ボス・心臓・NPC・gear・巨神の今の形は 4。`gen_mobs.py`・`install_tide_mobs.py` が指す「15 の 8 章、担当 1a」は 4.4） |
| 8.1（心臓の記録） | 4.2 |
| 9（判断が要る点） | 8 |
| 10（武器と防具の記録） | 4.6（防具は 16） |
| 10（NPC と見る者の記録） | 4.5 |
| 10（敵（岩鯨・潮殻）の記録）10.1〜10.4 | 4.4（10.2 の面の予算の取り方は 2.3） |
| 10（敵（雷翼・余燼）の記録）10.1〜10.6 | 4.4（10.3 の部品の表は 4.4 の道具の行、10.5 の判断は 8） |
| 中ボスの記録 | 4.3 |
| 巨神本体（岩鯨・潮殻）の記録 | 4.1 |
| 巨神本体（雷翼・余燼）の記録 | 4.1 |
| 11（判断が要る点（まとめ）） | 8 と 2.2・7 |
| 11.1 | 7（`ash smith` の落ち） |
