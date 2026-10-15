# tools/

素材を作るスクリプト。どれもリポジトリ直下で `python tools/<名前>.py` のように実行する。
採用済みの素材を古い版で上書きしてしまうスクリプトは止めてある。
どうしても動かすときだけ、環境変数 `SKYABYSS_ALLOW_REGEN=1` を付ける。`gen_items.py` だけは `SKYABYSS_ALLOW_GEN_ITEMS=1`。
経緯は `docs/review/assets_audit.md` の S1。

## 採用版の生成元 (実行してよい)

| 素材 (`src/main/resources/assets/skyabyss/` 以下) | 生成元 | 備考 |
|---|---|---|
| `textures/item/*.png` (32x32)、`textures/block/*.png` | `rough/items_blocks_32.py` | 出力は `docs/roughs/v4/items_blocks/b/`。src へは手でコピーする |
| 岩鯨: `geo/entity/stone_whale{,_mid,_far}.geo.json`、`textures/entity/stone_whale{,_glowmask}.png`、`animations/entity/stone_whale.animation.json` | `install_stone_whale.py` | 実行しても今の素材と同じ物ができる |
| 心臓: `geo/entity/stone_whale_heart_p1..p3.geo.json`、`textures/entity/stone_whale_heart_p*`、`animations/entity/stone_whale_heart.animation.json` | `install_heart_v4.py` (形の元は `rough/boss_v4.py`) | |
| 射手: `geo/entity/rib_archer.geo.json`、弓の `geo/gear/rib_archer_bow.geo.json` など | `rough/rib_archer_v4.py` | 同じ物ができる |
| 番兵長: `geo/entity/whalebone_captain.geo.json` / png / glowmask / animation | `rough/captain_v4.py --install` | 同じ物ができる |
| 苔這い: geo / png / glowmask / animation | `rough/moss_crawler_v3.py` | 監査では今の素材と比べていない |
| `textures/gui/sky_vignette.png` | `gen_vignette.py` | 同じ物ができる |
| 成長: `textures/gui/growth/{status,hud}.png`、`sounds/rpg/level_up.ogg` | `gen_growth_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わるが、音は同じ)。画面の配置を変えたら `GrowthScreen.java` と値をそろえる |
| 見る者: `geo/entity/watcher.geo.json`、`textures/entity/watcher{,_glowmask,_glint}.png`、`animations/entity/watcher.animation.json` | `rough/watcher.py --install` | ラフ 3 案は `docs/review/watcher_roughs/`。`--install` なしなら docs にだけ書く |
| `sounds/prologue/*.ogg` (見る者の音) | `gen_watcher_sounds.py` | 同じ物ができる |
| 技: `textures/gui/skill/{icons,skills}.png`、`textures/particle/{rune_spark,sky_mote}_*.png`、`particles/*.json`、`sounds/rpg/skill/*.ogg` | `gen_skill_assets.py` | 同じ物ができる (ogg はバイト列だけ毎回変わる)。アイコンのラフ 3 案と比較画像は `docs/review/skill_icons_roughs/`、採用は `ADOPTED`。`--roughs` なら docs にだけ書く。画面の配置を変えたら `SkillScreen.java` と値をそろえる |
| 共鳴の炉の画面: `textures/gui/forge/forge.png` (左右の板・つまみ・印・候補の札、2 倍密度) | `gen_forge_assets.py` | 同じ物ができる。画面の配置を変えたら `ResonanceForgeScreen.java` と値をそろえる |
| 語り部・鯨追い: `geo/entity/{storyteller,whale_chaser}.geo.json`、`textures/entity/{storyteller,whale_chaser}.png`、`animations/entity/{storyteller,whale_chaser}.animation.json`、顔 `textures/gui/npc/face/*.png` | `rough/npc.py --install` (画面の見本は `rough/npc_ui_mock.py`) | ラフは `docs/review/npc_roughs/`。`--install` なしなら docs にだけ書く |
| 会話とクエスト: `data/skyabyss/{dialogue,quest}/*.json`、`assets/skyabyss_npc/lang/*.json`、`docs/design/07_npc_quest.md` の表 | `gen_npc_content.py` | 文と構造の正本はこのスクリプト。JSON や lang を手で直したら、こちらにも反映する |

`rough/boss_v4.py`、`rough/sentinel_v4.py`、`rough/voxel.py`、`rough/build_gallery.py` は `src/` を読むだけで書かない。
`rough/` のそのほか (`armor*.py`、`boss.py`、`mobs_*.py`、`stone_whale*.py`、`weapons*.py`、`captain_v2.py` など) は `docs/roughs/` にしか書かない。履歴として残している。

生成元のないもの (今の `src/` が正本): 番兵 (`bone_sentinel`) と盾、水路の顎 (`waterway_maw`)、甲殻の防具 (`geo/armor`、`textures/armor`)、`textures/item/3d/bone_spike_arrow.png`、`sounds/**/*.ogg`。

## 止めてあるもの (引退)

| スクリプト | 上書きしてしまうもの | 代わり |
|---|---|---|
| `gen_items.py` | `textures/item/*.png` 16 枚と `textures/models/armor/*` | `rough/items_blocks_32.py` |
| `gen_block_textures.py` | `textures/block/*.png` と `sky_key_fragment.png` | `rough/items_blocks_32.py` |
| `gen_mobs.py` | 番兵・射手・苔這い・顎・番兵長の geo / png / glowmask / animation | 上の表 |
| `gen_titan_model.py` | 岩鯨の近景 geo / png / glowmask / animation | `install_stone_whale.py` |
| `gen_heart.py` | 心臓の animation (古い骨名に戻る) | `install_heart_v4.py` |
| `gen_armor_model.py` | 甲殻の防具の geo / png / glowmask | なし (今の src が正本) |
| `gen_weapon_models.py` (v1) | 武器 3 本のモデルを古い押し出しモデルに戻し、途中で止まる | `gen_weapon_models_v2.py` |
| `rough/rib_archer_v3.py` | 射手の geo / animation を v3 に戻す | `rough/rib_archer_v4.py` |
| `rough/captain_v3.py --install` | 番兵長を v3 に戻す (`--install` なしなら docs にだけ書くので動く) | `rough/captain_v4.py --install` |
| `gen_sky_sounds.py`、`gen_titan_sounds.py` | `sounds/sky/*.ogg`、`sounds/titan/*.ogg` | なし (今の src が正本) |

`gen_weapon_models_v2.py` も止めてある。武器 3 本の `models/item/{whalebone_greatsword,captain_pike,waterway_fang}.json` と
パレット `textures/item/3d/*.png` (16x16) の生成元で、`SKYABYSS_ALLOW_REGEN=1` を付ければ今と同じ物ができる。
古い 16x16 アイコンでの上書き (`write_icon`) は、さらに `SKYABYSS_COPY_OLD_ICONS=1` を付けたときだけ行う。
