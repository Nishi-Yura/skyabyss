# 岩鯨まわり リソースと登録の監査

対象はブランチ `m1-sky-pass` の `1dda4f0` 時点（心臓 v4 の導入 `7b84734` と岩鯨のひれ修正 `fba4516` を含む）。
読むだけで調べた。ゲーム内では確かめていない。
並行作業で心臓と岩鯨の素材が入れ替わったので、最後にもう一度測り直した。この文書の数値は入れ替え後のもの。

調べ方:
- 登録名は `registry/*.java` から抜き出し、lang、モデル、テクスチャと突き合わせた。
- geo は `texture_width/height` と PNG の実寸、UV の最大値、骨名の重複、親骨の有無、アニメーションの骨名を照合した。
- 発光マスクは「α>0 かつ黒でない」画素を数えた。
- 生成スクリプトは、リポジトリを一時フォルダへ丸ごと写し、その中で実行して差分を取った。実際の `src/` には何も書いていない。

---

## must-fix（直さないと見た目が壊れる、または遊ぶ人に誤った情報が出る）

### M1. 武器 3D モデルのパレットが 16x1 で、ブロック atlas 全体のミップマップが 0 に落ちる
- 該当ファイル:
  - `src/main/resources/assets/skyabyss/textures/item/3d/captain_pike.png`（16x1）
  - `src/main/resources/assets/skyabyss/textures/item/3d/waterway_fang.png`（16x1）
  - `src/main/resources/assets/skyabyss/textures/item/3d/whalebone_greatsword.png`（16x1）
- 起きること: アイテムのテクスチャはブロックと同じ `minecraft:textures/atlas/blocks.png` に入る。1.21.1 の `SpriteLoader` は、一番小さい辺の 2 の累乗に合わせて atlas 全体のミップレベルを下げ、`dropping miplevel for skyabyss:item/3d/... as it is only 16x1` と警告を出す。高さ 1px なのでミップは 0 になる。その結果、バニラも含めた全ブロックが遠くでちらつく（モアレが出る）。
- 直し方: 3 枚とも 16x16 にして、1 行目を 16 行ぶん繰り返す。モデルの UV は v=0.25〜0.75（16 単位の空間）なので、行を繰り返せば今の JSON のままで同じ色になる。
  - 生成元の `tools/gen_weapon_models_v2.py` の `write_palette()` も `H = max(16, H)` にして、行を繰り返して書くように直す。直さないと、次に実行したときに 16x1 へ戻る。
  - ついでの改善: 1 色 1 画素のパレットは、ミップが効くと隣の色がにじむ。気になるなら、各色を 2x2 以上のマスにするか、色と色のあいだに 1px の余白を入れる。

### M2. 報酬まわりの文が、心臓が最終ボスになる前の内容のまま
報酬は今、心臓を倒したとき（`loot/TitanLoot.java` の `onHeartDefeat`）に出る。心核、装備の抽選、`clears++` もここで行う。ところが次の文は、番兵長がボスだった頃のまま残っている。
| キー | 今の ja | 直し方の案 |
|---|---|---|
| `message.skyabyss.boss_reward`（心臓撃破時に送られる） | 番兵長の遺したものが、あなたの手に渡った | 岩鯨の心臓が遺したものが、あなたの手に渡った |
| `message.skyabyss.memory_clears` | 番兵長の撃破 %s回 · … | 心臓の撃破 %s回 · … |
| `item.skyabyss.stone_whale_core.desc` | 番兵長が守っていた、岩鯨の心の一片… | 岩鯨の心臓から取れた、心の一片… |
- en も同じ: `The captain's remains…`、`Captain kills %s`、`stone_whale_core.desc` を直す。
- `message.skyabyss.mid_boss_reward`（番兵長は 岩鯨の欠片 と 共鳴の欠片 と経験値を落とす）は内容としては正しい。ただ「欠片」がどれのことか分からないので、S3 で直す。

---

## should-fix（壊れてはいないが、直したほうがよい）

### S1. 生成スクリプトを走らせると、採用済みの素材が上書きされる
一時フォルダで実行し、今の素材と比べた結果は次のとおり。

| スクリプト | 上書きするもの（今の素材と中身が違う） | 現状 |
|---|---|---|
| `tools/gen_block_textures.py` | `textures/block/*.png` 12 枚すべて、`textures/item/sky_key_fragment.png`（採用案 B の 32x32 が古い絵に戻る） | 止める仕組みなし |
| `tools/gen_items.py` | `textures/item/*.png` 16 枚、`textures/models/armor/stone_whale_layer_1/2.png` | 環境変数 `SKYABYSS_ALLOW_GEN_ITEMS=1` がないと止まる（対処済み） |
| `tools/gen_mobs.py` | 番兵・射手・苔這い・顎・番兵長の geo / png / glowmask / animation（20 ファイル） | 止める仕組みなし |
| `tools/gen_titan_model.py` | `geo/entity/stone_whale.geo.json`、`stone_whale.png`、`_glowmask.png`、`animations/entity/stone_whale.animation.json`。近景だけが古い形に戻り、mid/far とテクスチャが合わなくなる | 止める仕組みなし |
| `tools/gen_heart.py` | `animations/entity/stone_whale_heart.animation.json`（v4 の 3 段階共用。古い骨名に戻る）。どこからも読まれない `geo/entity/stone_whale_heart.geo.json`、`textures/entity/stone_whale_heart.png`、`_glowmask.png` も作る | 止める仕組みなし |
| `tools/gen_armor_model.py` | `geo/armor/stone_whale_armor.geo.json`、`textures/armor/stone_whale_armor.png`、`_glowmask.png` | 止める仕組みなし |
| `tools/gen_weapon_models.py`（v1） | `models/item/whalebone_greatsword.json`、`captain_pike.json`、`waterway_fang.json` を古い押し出しモデルで上書きする。その後 `bone_spike_arrow` のところで `AssertionError: (32, 32)` で止まるので、半端な状態が残る | 止める仕組みなし |
| `tools/gen_weapon_models_v2.py` | `textures/item/{whalebone_greatsword,captain_pike,waterway_fang}.png` を `docs/roughs/v2` の古い 16x16 アイコンに戻す（`write_icon`）。M1 のパレットも 16x1 で書き直す | 止める仕組みなし |
| `tools/rough/rib_archer_v3.py` | 引数なしで実行しただけで `geo/entity/rib_archer.geo.json` と `animations/entity/rib_archer.animation.json` を v3 に戻す（v4 が採用版） | 止める仕組みなし |
| `tools/rough/captain_v3.py --install` | 番兵長の geo / png / glowmask / animation を v3 に戻す（v4 が採用版） | `--install` を付けたときだけ |
| `tools/gen_sky_sounds.py`、`tools/gen_titan_sounds.py` | `sounds/sky/*.ogg`、`sounds/titan/*.ogg` のバイト列が変わる（ogg のエンコードが毎回変わるだけかもしれない。聴いての比較はしていない） | 止める仕組みなし |

今の素材をそのまま作り直せるもの（実行しても差分なし）: `tools/install_stone_whale.py`、`tools/rough/rib_archer_v4.py`、`tools/rough/captain_v4.py --install`、`tools/gen_vignette.py`。
`tools/install_heart_v4.py` は作業中（未コミットの変更あり）なので比べていない。
`tools/rough/boss_v4.py`、`sentinel_v4.py`、`voxel.py` は `src/` を読むだけで、書き込まない。

直し方: 上の表で「止める仕組みなし」の 10 本に、`gen_items.py` と同じ止め方（`__main__` で環境変数を見て `sys.exit`）を入れる。または `tools/legacy/` へ移して README に「採用版の生成元」の一覧を書く。
採用版の生成元: ブロックとアイテムは `rough/items_blocks_32.py`、岩鯨は `install_stone_whale.py`、心臓は `install_heart_v4.py`、射手は `rough/rib_archer_v4.py`、番兵長は `rough/captain_v4.py`。

### S2. ja の表記ゆれと誤訳
| キー | 問題 | 案 |
|---|---|---|
| `message.skyabyss.descending` | 「鎖がゆっくりと地上へ降りていった」は、鎖だけが降りたように読める。en は "lowers you"。 | 錨石に触れると、鎖があなたをゆっくりと地上へ降ろしていく |
| `entity.skyabyss.bone_spike` | 「骨の棘」。射手と矢は「骨棘」。 | 骨棘 |
| `gear.skyabyss.tier.*` | アンコモン、レア（カタカナ）と「★ 伝説」（漢字）が混ざっている。メッセージでも「伝説」を使っている。 | 上質、希少、★ 伝説（漢字にそろえる） |
| 括弧 | 全角は `message.skyabyss.resonance`、`commands.skyabyss.titan.missing`、`commands.skyabyss.titan.rebuilt`、`item.skyabyss.resonance_charm.desc`。半角は `message.skyabyss.memory_soft`、`legendary_dupe`、`first_clear_hint`、`gui.skyabyss.forge.max`。 | 全角にそろえる |
| `item.skyabyss.resonance_charm.desc` | 「（1分休む）」では、使ったあと 1 分使えないという意味が伝わりにくい。 | （使うと1分間は使えない） |
| `message.skyabyss.chain_hint` | 「背中へ」。ほかは「背」「岩鯨の背」。 | 鎖を右クリックすると、岩鯨の背へ引き上げられる |
| `item.skyabyss.waterway_fang.desc`（en・ja とも） | `+30%` の `%` が書式として読まれる。en は `% a` が `%a` と解釈されて書式エラーになり、文字列がそのまま出るだけで済んでいる。`%s` を足すと壊れる。 | `+30%%` にする |

### S3. 「欠片」が 3 つの別のアイテムを指している
天の鍵の欠片（`sky_key_fragment`）、岩鯨の欠片（`stone_whale_shard`）、共鳴の欠片（`resonance_shard`）。
台座の看板（`sign.skyabyss.pedestal.*`）と `message.skyabyss.pedestal_sleep` は「欠片が応える」と書いていて、`mid_boss_reward` は「欠片と経験が残った」と書いている。どれも「欠片」としか言っていない。
- 案: アイテム名を分ける。たとえば 岩鯨の欠片 → 岩鯨の石片、共鳴の欠片 → 共鳴の残響。または、文の中で毎回フルネームを書く（「岩鯨の欠片と共鳴の欠片、経験が残った」「天の鍵の欠片が応える」）。
- en も "Resonance Fragment" と "Sky Key Fragment" がぶつかる。"Resonance Shard" にすれば、ほかの "Shard" とそろう。

### S4. 骨の番兵の発光マスクが 1 画素だけ
- `textures/entity/bone_sentinel_glowmask.png` は光る画素が 1 個しかない。0 個だと `AutoGlowingGeoLayer` が落ちるので、それを避けるために置いたもの（`docs/roughs/IMPLEMENTED.md` に記録あり）。
- 誰かが「不要な画素」として消すと、起動直後に落ちる。`GuardRenderers.geo()` で、番兵にだけ `AutoGlowingGeoLayer` を付けないようにする。または `GearLayer` と同じように、マスクがあるときだけ付ける。そのうえでマスクのファイルを消すほうが安全。

### S5. 岩鯨のテクスチャが 2048x2048 なのに、半分しか使っていない
- `textures/entity/stone_whale.png` と `_glowmask.png` は 2048x2048。3 つの LOD の geo が使う UV は、高さ 1044px（近景）までしかない。
- 2048x1024 にして、geo 3 つの `texture_height` を 1024 にすれば、GPU のメモリが約 16MB（RGBA 2 枚）減る。`tools/install_stone_whale.py` の書き出しサイズも合わせて直す。

### S6. 台座の文が、欠片を受け取ったあとも同じ
- `TitanBackTrials`（`message.skyabyss.pedestal_sleep` の分岐、640 行付近）は、心臓を倒して欠片を受け取ったあとも「台座は眠っている」と出す。
- 用意されている `message.skyabyss.fragment_already` を、`TitanBackData.claimFragment` を済ませた人に出すようにする。そうすれば未使用キーも 1 つ減る。

### S7. 防具と武器が、バニラのエンチャント用タグに入っていない（判断が必要）
- 1.21 では、エンチャントテーブルや金床でどのエンチャントを付けられるかを `#minecraft:enchantable/*` で決める。`data/minecraft/tags/item/` には `arrows.json` しかないので、甲殻の防具（`StoneWhaleArmorItem`）と 3 本の剣（`SwordItem`）にはエンチャントが付かない。
- 独自の強化（共鳴の炉）だけにするつもりなら、今のままでよい。その場合は設計書に明記しておく。

---

## cleanup（消しても動きは変わらない）

### C1. 使われていない lang キー（en・ja 両方）
- `entity.skyabyss.guard_captain`（古き番兵長）: 今の名前は `whalebone_captain` で、このキーを読むコードはない。消す。
- `message.skyabyss.arena_begin`: `hall_begin` に置き換わっている。消す。
- `checkpoint.skyabyss.maze`（休みの泡）: `TitanBackTrials.CHECKPOINTS` に `maze` がない。消すか、水路に休み場を足す。
- `message.skyabyss.fragment_already`: S6 で使うなら残す。

不足しているキーはない。確かめた範囲は次のとおり。
- en と ja のキーの集合は同じで、`%s` の数もすべてそろっている。
- 登録されているアイテム・ブロック・エンティティ・効果の名前、`container.skyabyss.resonance_forge`、字幕 7 個、看板（文字列で組み立てる `sign.skyabyss.*` はどれも `.1`〜`.4` がそろっている）、休み場 `w0 w1 w2 e0 ce1 ce2 heart`（`hub` はメッセージを出さない）、ボスバーの `phase.1`〜`3` と `roaring`、コマンドの出力、設定画面。

### C2. 使われていない素材
- `textures/models/armor/stone_whale_layer_1.png`、`stone_whale_layer_2.png`（256x128）: 防具は GeckoLib（`StoneWhaleArmorRenderer`）で描くので、表には出ない。ただし `ModArmorMaterials` の `ArmorMaterial.Layer("stone_whale")` がこの名前を指していて、バニラの防具レイヤーがこの名前でバッファを取りにくる。消すと「テクスチャが見つからない」警告が出るかもしれない。消すなら 16x16 の透明画像に差し替えるのが無難。残す場合も、`gen_items.py` 以外に生成元がないことを書いておく。
- `tools/rough/` の `armor.py`、`boss.py`、`mobs_large.py`、`mobs_small*.py`、`stone_whale*.py`、`weapons*.py`、`captain_v2.py`、`gallery*.json`: 出力先は `docs/roughs/` だけで、`src/` は触らない。履歴として残してよい。整理するなら `tools/rough/old/` へ移す。
- `tools/__pycache__`、`tools/rough/__pycache__`: `.gitignore` に入れる。
- 古い geo（単体の `stone_whale_heart.geo.json` など）は今のツリーに残っていない（`gen_heart.py` を走らせたときだけできる）。

### C3. その他
- `animations/armor/stone_whale_armor.animation.json` は `"animations": {}` で空。GeckoLib は空でも読める。動かす予定がなければ、このままでよい。
- 心臓の 3 段階の geo は、識別子が `geometry.skyabyss.stone_whale_heart.pN` で、ほかの geo（`geometry.<name>`）と形式が違う。GeckoLib はファイルの場所で読み分けるので、動きには関係ない。

---

## 確かめて問題がなかったもの

### GeckoLib（v4 心臓、ひれ修正後の岩鯨で測り直し済み）
| geo | 宣言サイズ | PNG | UV 最大 | glowmask（光る画素） | アニメの骨 |
|---|---|---|---|---|---|
| entity/stone_whale（近・mid・far） | 2048x2048 | 一致 | 2048x1044 | 6600 | すべてある（swim） |
| entity/stone_whale_heart_p1 / p2 / p3 | 512x94 / 512x82 / 512x71 | 一致 | 一致 | 4201 / 4222 / 3470 | 13 本すべて、どの段階でも骨の欠けなし |
| entity/bone_sentinel | 256x66 | 一致 | 一致 | 1（S4） | すべてある |
| entity/rib_archer | 256x26 | 一致 | 256x25 | 24 | すべてある |
| entity/moss_crawler | 256x30 | 一致 | 一致 | 32 | すべてある |
| entity/waterway_maw | 256x80 | 一致 | 256x65 | 108 | すべてある |
| entity/whalebone_captain | 1024x112 | 一致 | 1024x111 | 172 | すべてある |
| armor/stone_whale_armor | 256x113 | 一致 | 一致 | 206 | `armorHead` など 8 本、GeckoLib の既定名どおり |
| gear/bone_sentinel_shield | 64x54 | 一致 | 64x53 | なし（GearLayer は光らせない） | すべてある |
| gear/rib_archer_bow | 64x18 | 一致 | 一致 | なし | すべてある |

- どの geo にも、骨名の重複や存在しない親骨の参照はない。
- Java から呼ぶアニメーション名はすべてファイルにある。
  - `GuardMob` の idle / walk と各 action。
  - 心臓の `roar_1`、`roar_2` と攻撃 9 種。`roar_ + (phase+1)` は phase 0〜1 のあいだしか呼ばれないので、`roar_3` は要らない。
  - `WaterwayMaw` の swim / bite、`StoneWhaleEntity` の swim。
  - GearLayer の `idle`、`block_impact`、`draw`。
- 心臓の段階ごとの名前は `stone_whale_heart_pN`（geo、png、`_glowmask`）、ボスバーの名前は `gui/boss/stone_whale_heart_{icon,frame}_pN.png`。p1〜p3 とも全部ある。

### アイテムモデル（`neoforge:separate_transforms`）
- `whalebone_greatsword`、`captain_pike`、`waterway_fang`、`bone_spike_arrow` の 4 本を確かめた。
  - GUI: `perspectives.gui` = `minecraft:item/generated` + `skyabyss:item/<name>`（32x32 があることを確認）
  - 手に持ったとき（first / third person の左右）: `base.display`
  - 地面（ground）: `base.display`
  - 額縁（fixed）: `base.display`
  - 頭（head）: `base.display`
  - 4 本とも上の 5 つを満たしている。
- 面のテクスチャは `#pal`（武器 3 本）と `#tex`（矢）だけを参照している。どちらも実在する。`particle` も実在する。座標は -16〜32、UV は 0〜16 の範囲に収まっている。
- 番兵長が持つ鉾（`GearLayer.item`、`ItemDisplayContext.NONE`）は `base` を変換なしで使うので、これも問題ない。
- そのほかのアイテムモデル（generated、スポーンエッグ、ブロックアイテム）は、指しているテクスチャやモデルがすべて実在する。

### ブロック、データ、音
- blockstates の値は、ブロックのプロパティと一致している。
  - `crumbling_bone` の `stage=solid|cracking|gone`
  - `resonance_forge` の `facing`
  - `resonance_stone` の `active`
- 試練用ブロックは `noLootTable()` で、ルートテーブルは要らない。`loot_table/blocks/resonance_forge.json` は、レシピと同じ ID の `skyabyss:resonance_forge` を落とす。
- レシピが使う素材（`resonance_crystal`、`stone_whale_shard`、バニラの 2 種）はすべて実在する。
- タグ: `mineable/pickaxe` に `resonance_forge`、`#minecraft:arrows` に `bone_spike_arrow`（`ArrowItem` を継承しているので正しい）。
- `dimension/titan_back`、`dimension_type/titan_back`、`worldgen/biome/titan_back` は互いを正しく参照していて、`TitanTravel.TITAN_BACK` と ID が一致する。
- 消した宝箱やアイテムを参照しているデータはない（`TitanBackSelfTest` は「宝箱が無いこと」を確かめている）。
- `sounds.json` の 7 イベントは `ModSounds` の 7 個と一致し、どれもコードから使われている。ogg は 6 本とも実在する。`resonance` はバニラの `block/bell/resonate` と `block/amethyst/resonate1` を使う。
- `mob_effect/bleeding.png` は 18x18（バニラと同じで、効果アイコンの atlas に入る）。
- クリエイティブタブには、登録したアイテムがすべて入っている。
