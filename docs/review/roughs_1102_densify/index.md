# 密度上げ（監査 2026-11-01 項目 8）: 三角形の少ない敵 33 体のラフ

監査で「群の最大の 40% 未満」と出た敵を、既存の形の二次的な形（板・関節の輪・肋・毛束・ひび・縁取り・光る点）で 50〜70% に上げたラフ。輪郭・配色・骨の名前は変えていない（アニメが動く）。取り込み（`src/main/resources`）・ビルド・gradle は触っていない。コミットもしていない。

## 結果

- 対象: 33 体（下の群の一覧）。**依頼文は 37 体だったが、名前を列挙した群は合計 33 体**。残り 4 体は列挙に無いので、ここでは扱っていない（名前を教えてもらえれば同じ手順で作る）。
- 作ったもの: 33 体すべて。スキップ 0。
- 群ごとの数: 月 8 / 雲海 5 / 深淵 B 7 / 深淵 A 4（段 s1〜s3 は同じ形の 4 種）/ 三章 3 / 四章 1 / 大陸の敵 2 / 一章 1 / 二章 2。
- 割合（群の最大に対して、四捨五入）: 33 体すべてが 50〜70% の中。下限ぎりぎり（50%）は lamp_eater、past_shadow、peak_fledgling。
- 水密: 33 体とも、溶接後の開いた輪が 0（`tools/lib/mesh_close.py` の `close_mesh` で閉じた数を「密度上げ後」とした。取り込みと同じ数え方）。
- 骨: 追加はすべて既存の骨に付けた。骨の名前・pivot・木は変えていない。`ash_trail` は一度、採用案 C に無い脚の骨へ爪を付けていたので外して手の爪に直した。
- 埋まり検査（`check_buried`）: 新しく埋まった部品は 0。元から埋まっていた `bell_mourner`（外套の裾と鐘）・`white_shell_keeper`（巻き殻の帯）などは元のまま。

## 一覧（三角形は水密後の数。取り込み＝現在の `src/main/resources` の値）

| 敵 | 群 | 案 | 取り込み（現在） | 再現（元の水密後） | 密度上げ後 | 群の最大 | 割合（目標 50〜70%） | 画像 | 元 → 後 の成果物 |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| shadow_stitcher | moon | c | 1032 | 1032 | 1576 | 2656 | 59% | [shadow_stitcher.png](shadow_stitcher.png) | [before](shadow_stitcher/before/) / [after](shadow_stitcher/after/) |
| shade_strider | moon | a | 1014 | 1014 | 1634 | 2656 | 62% | [shade_strider.png](shade_strider.png) | [before](shade_strider/before/) / [after](shade_strider/after/) |
| shell_hare | moon | b | 932 | 924 | 1386 | 2656 | 52% | [shell_hare.png](shell_hare.png) | [before](shell_hare/before/) / [after](shell_hare/after/) |
| dust_crawler | moon | a | 870 | 870 | 1438 | 2656 | 54% | [dust_crawler.png](dust_crawler.png) | [before](dust_crawler/before/) / [after](dust_crawler/after/) |
| rift_wing | moon | a | 820 | 820 | 1420 | 2656 | 53% | [rift_wing.png](rift_wing.png) | [before](rift_wing/before/) / [after](rift_wing/after/) |
| cliff_creeper | moon | b | 780 | 780 | 1436 | 2656 | 54% | [cliff_creeper.png](cliff_creeper.png) | [before](cliff_creeper/before/) / [after](cliff_creeper/after/) |
| moon_leaper | moon | a | 700 | 700 | 1344 | 2656 | 51% | [moon_leaper.png](moon_leaper.png) | [before](moon_leaper/before/) / [after](moon_leaper/after/) |
| push_stone | moon | a | 676 | 676 | 1372 | 2656 | 52% | [push_stone.png](push_stone.png) | [before](push_stone/before/) / [after](push_stone/after/) |
| bell_mourner | cloud | a | 1002 | 1002 | 1612 | 2652 | 61% | [bell_mourner.png](bell_mourner.png) | [before](bell_mourner/before/) / [after](bell_mourner/after/) |
| mist_bird | cloud | a | 892 | 892 | 1374 | 2652 | 52% | [mist_bird.png](mist_bird.png) | [before](mist_bird/before/) / [after](mist_bird/after/) |
| backfire | cloud | a | 760 | 760 | 1508 | 2652 | 57% | [backfire.png](backfire.png) | [before](backfire/before/) / [after](backfire/after/) |
| ash_striker | cloud | a | 754 | 754 | 1426 | 2652 | 54% | [ash_striker.png](ash_striker.png) | [before](ash_striker/before/) / [after](ash_striker/after/) |
| fallen_plume | cloud | a | 486 | 486 | 1350 | 2652 | 51% | [fallen_plume.png](fallen_plume.png) | [before](fallen_plume/before/) / [after](fallen_plume/after/) |
| soot_child | abyssB | a | 742 | 742 | 1026 | 1918 | 53% | [soot_child.png](soot_child.png) | [before](soot_child/before/) / [after](soot_child/after/) |
| rope_hanger | abyssB | b | 652 | 652 | 1032 | 1918 | 54% | [rope_hanger.png](rope_hanger.png) | [before](rope_hanger/before/) / [after](rope_hanger/after/) |
| lamp_eater | abyssB | a | 496 | 496 | 968 | 1918 | 50% | [lamp_eater.png](lamp_eater.png) | [before](lamp_eater/before/) / [after](lamp_eater/after/) |
| past_shadow | abyssB | c | 442 | 442 | 950 | 1918 | 50% | [past_shadow.png](past_shadow.png) | [before](past_shadow/before/) / [after](past_shadow/after/) |
| ceiling_walker | abyssB | a | 412 | 412 | 1022 | 1918 | 53% | [ceiling_walker.png](ceiling_walker.png) | [before](ceiling_walker/before/) / [after](ceiling_walker/after/) |
| ash_trail | abyssB | c | 380 | 380 | 992 | 1918 | 52% | [ash_trail.png](ash_trail.png) | [before](ash_trail/before/) / [after](ash_trail/after/) |
| nameless_shape | abyssB | a | 308 | 308 | 984 | 1918 | 51% | [nameless_shape.png](nameless_shape.png) | [before](nameless_shape/before/) / [after](nameless_shape/after/) |
| bone_picker | abyssA | a | 472 | 472 | 744 | 1466 | 51% | [bone_picker.png](bone_picker.png) | [before](bone_picker/before/) / [after](bone_picker/after/) |
| charged_moth | abyssA | a | 442 | 442 | 888 | 1466 | 61% | [charged_moth.png](charged_moth.png) | [before](charged_moth/before/) / [after](charged_moth/after/) |
| bone_flute | abyssA | a | 340 | 340 | 742 | 1466 | 51% | [bone_flute.png](bone_flute.png) | [before](bone_flute/before/) / [after](bone_flute/after/) |
| white_shell_keeper | abyssA | a | 336 | 336 | 800 | 1466 | 55% | [white_shell_keeper.png](white_shell_keeper.png) | [before](white_shell_keeper/before/) / [after](white_shell_keeper/after/) |
| lightning_gleaner | ch3 | c | 404 | 404 | 568 | 1090 | 52% | [lightning_gleaner.png](lightning_gleaner.png) | [before](lightning_gleaner/before/) / [after](lightning_gleaner/after/) |
| static_puff | ch3 | b | 402 | 384 | 626 | 1090 | 57% | [static_puff.png](static_puff.png) | [before](static_puff/before/) / [after](static_puff/after/) |
| peak_fledgling | ch3 | b | 228 | 228 | 548 | 1090 | 50% | [peak_fledgling.png](peak_fledgling.png) | [before](peak_fledgling/before/) / [after](peak_fledgling/after/) |
| cinder_mite | ch4 | a | 278 | 278 | 614 | 1166 | 53% | [cinder_mite.png](cinder_mite.png) | [before](cinder_mite/before/) / [after](cinder_mite/after/) |
| grain_rat | foes | a | 606 | 606 | 1134 | 1978 | 57% | [grain_rat.png](grain_rat.png) | [before](grain_rat/before/) / [after](grain_rat/after/) |
| stray_wisp | foes | a | 324 | 324 | 1062 | 1978 | 54% | [stray_wisp.png](stray_wisp.png) | [before](stray_wisp/before/) / [after](stray_wisp/after/) |
| bone_gull | ch1 | a | 450 | 450 | 736 | 1456 | 51% | [bone_gull.png](bone_gull.png) | [before](bone_gull/before/) / [after](bone_gull/after/) |
| tidepool_clam | ch2 | a | 376 | 376 | 730 | 1182 | 62% | [tidepool_clam.png](tidepool_clam.png) | [before](tidepool_clam/before/) / [after](tidepool_clam/after/) |
| sand_burrower | ch2 | c | 256 | 268 | 620 | 1182 | 52% | [sand_burrower.png](sand_burrower.png) | [before](sand_burrower/before/) / [after](sand_burrower/after/) |

注: `shell_hare` の再現値（924）と取り込み（932）、`static_puff`（384 / 402）、`sand_burrower`（268 / 256）は、取り込み後に生成元（ラフ）が変わった差（取り込みの数は動かさない）。割合は密度上げ後の値で計算。

## 取り込みのコマンド（群ごと）

既定の取り込み（生成スクリプト）は、群のラフの置き場（`docs/review/<群>_roughs/<id>/<案>/`）から読む。今回の出力はそこに無いので、そのまま流すと旧いラフが入る。次のどちらかを選ぶ。

1. 直接入れる（メッシュ・絵・発光マスク・骨の骨格だけ。段の絵 s1/s2 やアニメは変わらない）。例（1 体）:

```
python -I tools/install/install_lowpoly.py <名前> --mesh docs/review/roughs_1102_densify/<id>/after/<id>_after_mesh_closed.json --atlas docs/review/roughs_1102_densify/<id>/after/<id>_after_atlas.png --glow docs/review/roughs_1102_densify/<id>/after/<id>_after_glowmask.png
```

2. 生成スクリプトの流れに乗せる（段の絵・動きも同じ生成で書く）。`docs/review/<群>_roughs/<id>/<案>/` に `<id>_<案>_mesh.json`・`_atlas.png`・`_glowmask.png` を置いてから流す。これは既存の出力を置き換えるので、先に確認が要る。

群ごとの対応（名前は id。深淵 A は s1〜s3 の三回）:

- **月 雑魚**（moon）: tools/gen/ch6/gen_moon_mobs.py（入力 docs/review/moon_roughs/<id>/<案>/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **雲海 雑魚**（cloud）: tools/gen/finale/gen_cloud_mobs.py（入力 docs/review/cloud_roughs/<id>/a/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **深淵 B 群**（abyssB）: tools/gen/abyss/gen_abyss_mobs_b.py（入力 docs/review/abyss_roughs/<id>/<案>/。絵は tools/abyss/paint_abyss_b.py）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **深淵 A 群 (段ごとの _s1/_s2/_s3 は同じ形)**（abyssA）: tools/gen/abyss/gen_abyss_mobs_a.py（入力 docs/review/abyss_roughs/<id>/<案>/。名前は abyss_<id>_s1 / _s2 / _s3）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py abyss_<id>_s1 ...`（s2・s3 も同じ形。絵は同じ 1 枚になるので、段の違いは生成スクリプトで出す）
- **三章 地上**（ch3）: tools/gen/ch3/gen_ch3_ground_mobs.py（入力 docs/review/ch3_roughs/<id>/<案>/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **四章**（ch4）: tools/gen/ch4/gen_ch4_ground_mobs.py（入力 docs/review/ch4_roughs/<id>/<案>/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **大陸の敵**（foes）: tools/gen/foes/gen_foes_one.py（入力 docs/review/foes_one/<id>/a/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **一章 背**（ch1）: tools/install/install_ch1_back_mobs.py（入力は固定の docs/review/ch1_roughs/bone_gull/a/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）
- **二章 地上**（ch2）: tools/gen/ch2/gen_ch2_ground_mobs.py（入力 docs/review/ch2_roughs/<id>/<案>/）
  - 直接入れる場合: `python -I tools/install/install_lowpoly.py <id> --mesh ... --atlas ... --glow ...`（上の 1 体の形）

注意: 生成スクリプトが絵を描き直す群（深淵 B は `tools/abyss/paint_abyss_b.py`、深淵 A は `gen_abyss_mobs_a.py` の塗り関数）は、新しい面にも色が付くが、ゲームでの見え方は撮って確かめる。取り込みは行っていない。

## 道具と再現

- 共通部品: `tools/rough/lowpoly/densify_1102/dens.py`（元のラフを読み、採用案の数を取り込みと照合し、before / after を書き、水密化の数と埋まりを出す）。
- 各敵: `tools/rough/lowpoly/densify_1102/<id>.py`（元の build に二次的な形を足すだけ。実行: `python -I tools/rough/lowpoly/densify_1102/<id>.py`）。
- 索引の再作成: `python -I tools/rough/lowpoly/densify_1102/index_build.py`。
- 採用案: 取り込みの三角形数に一番近い案（`dens.choose`）。深淵 B・三章・四章・大陸などは生成スクリプトの CHOICE の案を直接指定し、再現の数が取り込みと一致することを確かめた（`shell_hare` 等の差は注に書いた）。

---
作成: 2026-11-02。
