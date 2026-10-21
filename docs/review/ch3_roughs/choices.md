# 三章の地上の敵と品 ラフ 3 案 (選定ページ用)

三章 (雷翼) の地上の新しい敵 4 種と、新しい品 9 つ (旅装 1 組・小物 8) のラフです (設計書 21 の 5.1、P16)。
どれも仮の選定 (★) で入れてあります。選び直すときは、敵は `tools/gen_ch3_ground_mobs.py` の `CHOICE`、品は `tools/install_ch3_gear_assets.py` の `CHOICE` を変えて流し直します。
画像の場所はリポジトリの根からの相対です。敵の `preview.png` は全身の前 3/4 の一枚、`<id>.png` は姿勢と技の予告まで並べた一覧です。ゲームの中の絵は `docs/review/ch3_extra/` にあります。

## 敵

### 帯電毛玉 (static_puff)
宙に浮く綿毛の塊。毛が逆立つのが放電の予告。一覧: `docs/review/ch3_roughs/static_puff.png`

| 案 | 画像 | ひとこと | 仮 |
| --- | --- | --- | --- |
| A 綿毛の玉 | `docs/review/ch3_roughs/static_puff/a/preview.png` | 白い芯から細い毛が放射に伸び、毛の先に青白い火花。逆立つと一回り大きく見える | ★ |
| B 静電の羊毛 | `docs/review/ch3_roughs/static_puff/b/preview.png` | もこもこの毛の房の塊に黒い目二つ。逆立つと房がふくらみ火花の棘が立つ | |
| C 雷雲の子 | `docs/review/ch3_roughs/static_puff/c/preview.png` | 灰青の平たい雲の塊。底から静電の糸が垂れ、隙間から核が光る | |

### 峰の雛 (peak_fledgling)
岩棚の巣の雛。地面に光る線を引いて急降下する。一覧: `docs/review/ch3_roughs/peak_fledgling.png`

| 案 | 画像 | ひとこと | 仮 |
| --- | --- | --- | --- |
| A 灰毛の雛 | `docs/review/ch3_roughs/peak_fledgling/a/preview.png` | 丸い灰の綿羽、短い翼と銅の嘴、頭に寝ぐせ。峰の岩に紛れる | |
| B 鷲の子 | `docs/review/ch3_roughs/peak_fledgling/b/preview.png` | 白青の羽の細身。銅の冠羽が羽守の兜に似る | |
| C 焦げ羽の雛 | `docs/review/ch3_roughs/peak_fledgling/c/preview.png` | 黒く焼けた羽に青白い焦げ筋、琥珀の目。暗い塔でも筋の光で見える | ★ |

### 雷拾い (lightning_gleaner)
銅の脚の長い鳥。敵意なし。驚くと荷を落として逃げ、雷拾いの原へ導く。一覧: `docs/review/ch3_roughs/lightning_gleaner.png`

| 案 | 画像 | ひとこと | 仮 |
| --- | --- | --- | --- |
| A 銅脚の鷺 | `docs/review/ch3_roughs/lightning_gleaner/a/preview.png` | 灰青の鷺。長い銅の脚と嘴、嘴に銅の切れ端 | |
| B 荷負い鳥 | `docs/review/ch3_roughs/lightning_gleaner/b/preview.png` | 首の短い丸い鳥。背に銅線の束を焦げた布で括る。荷を落とすのが一目で分かる | ★ |
| C 避雷の鶴 | `docs/review/ch3_roughs/lightning_gleaner/c/preview.png` | 白い鶴。黒い脚に銅線を巻き、頭に光る銅の冠 | |

### 焼けた羽守 (scorched_warden。名づき)
羽守 (鷲兜の番人) の変種。骨と鉤槍は羽守と同じ。一覧: `docs/review/ch3_roughs/scorched_warden.png`

| 案 | 画像 | ひとこと | 仮 |
| --- | --- | --- | --- |
| A 焼けた外套 | `docs/review/ch3_roughs/scorched_warden/a/preview.png` | 外套と鎧羽が黒く焼け、兜の正面に青白い焦げ筋が一本 | |
| B 青い焦げ筋 | `docs/review/ch3_roughs/scorched_warden/b/preview.png` | 羽の色は羽守のまま、兜・肩・胸に焦げ筋が何本も走り、銅は緑青に | ★ |
| C 雷を受けた兜 | `docs/review/ch3_roughs/scorched_warden/c/preview.png` | 冠が一本折れて光り、兜を縦に割る稲妻の傷。外套は焼け縮れて短い | |

## 品

### 峰越えの旅装 (peak_crossing。4 部位)
一覧: `docs/review/ch3_roughs/items/armor_ch3.png` (下にインベントリのアイコン)

| 案 | 画像 | ひとこと | 仮 |
| --- | --- | --- | --- |
| A 羽の肩 | `docs/review/ch3_roughs/items/armor_ch3/a/preview.png` | 白青の羽の肩 3 段、灰青の上着、銅の襟巻き、兜に銅の避雷の立て飾り | ★ |
| B 銅線の外套 | `docs/review/ch3_roughs/items/armor_ch3/b/preview.png` | 焦げ色の長い外套に銅線の帯と縁、銅のボタン 4 つ | |
| C 雲の頭巾 | `docs/review/ch3_roughs/items/armor_ch3/c/preview.png` | 雲の白の布、後ろへ長い頭巾の尾、灰青の肩と背の板 | |

### 小物 (32×32)
一覧: `docs/review/ch3_roughs/items/icons_ch3.png`。画像はどれも `docs/review/ch3_roughs/items/icons/<品>_<案>_32.png`。

| 品 | A | B | C | 仮 |
| --- | --- | --- | --- | --- |
| 焦げ羽 (scorched_feather) | 先の焦げた白青の羽 | 青い筋の黒い羽 | 二枚を銅で束ねる | B |
| 帯電綿 (static_cotton) | 火花の毛の玉 | 三つの房と稲妻 | 銅の糸巻き | B |
| 銅線 (copper_wire) | 丸いかせ | 木の芯に巻く | よじれた拾い物 | B |
| 雷の護り糸 (thunder_ward_thread) | 銅のかせと光の玉 | 稲妻の房の護り結び | 羽の軸に巻く | B |
| 雷の砥石 (thunder_whetstone) | 灰石に銅の筋 | 黒い石に光るひび | 銅を巻いた握り | B |
| 風干しの団子 (wind_dumpling) | 串の三つ | 笹の包み | 羽を挿した団子 | A |
| 避雷の油 (lightning_oil) | 硝子の小瓶 | 黒い壺と銅の帯 | 油の布の包み | A |
| 風読みの小旗 (wind_streamer) | 白い吹き流し | 緋の三角旗 | 風見の矢羽 | A |

- 効き目の絵 (18×18、案は一つ): 雷の刃 `docs/review/ch3_roughs/items/icons/thunder_whetted_18.png`、避雷 `docs/review/ch3_roughs/items/icons/grounded_18.png`。
- 雷翼の鼓動守と旅の覚書 (三章) は既存の絵のまま (新しい絵はない)。
