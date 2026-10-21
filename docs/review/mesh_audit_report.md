# メッシュ監査 (tools/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 249 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **8110**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 1978 / つぶれた三角形: 104
- 関節: detach 15 / shallow 6 / ok 486 (うち pivot が親の外: 290)
- ブロックモデル: 92 個中、面の欠け・宙に浮く部品のあるもの 0

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | armor/ember/leggings.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 2 | armor/ember/leggings_legend.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 3 | armor/ember/leggings_rare.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 4 | armor/stone_whale/leggings.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 5 | armor/stone_whale/leggings_legend.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 6 | armor/stone_whale/leggings_rare.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 7 | armor/thunder_wing/leggings.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 8 | armor/thunder_wing/leggings_legend.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 9 | armor/thunder_wing/leggings_rare.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 10 | armor/tide_shell/leggings.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 11 | armor/tide_shell/leggings_legend.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 12 | armor/tide_shell/leggings_rare.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 13 | attendant_c.json | 108 | 0 | 36 | 0 | 0 | 0 | 7 | 0 |
| 14 | items/smith_hammer/common.json | 108 | 0 | 36 | 0 | 0 | 0 | 6 | 0 |
| 15 | items/smith_hammer/legend.json | 108 | 0 | 36 | 0 | 0 | 0 | 2 | 0 |
| 16 | items/smith_hammer/rare.json | 108 | 0 | 36 | 0 | 0 | 0 | 2 | 0 |
| 17 | items/waterway_fang/common.json | 108 | 0 | 36 | 0 | 0 | 0 | 0 | 0 |
| 18 | items/waterway_fang/legend.json | 108 | 0 | 36 | 0 | 0 | 0 | 0 | 0 |
| 19 | items/waterway_fang/rare.json | 108 | 0 | 36 | 0 | 0 | 0 | 0 | 0 |
| 20 | ash_smith.json | 90 | 0 | 6 | 0 | 0 | 0 | 1442 | 0 |
| 21 | ash_husk.json | 76 | 0 | 0 | 0 | 0 | 0 | 1486 | 2 |
| 22 | armor/peak_crossing/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 23 | armor/tide_wader/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 24 | armor/whale_road/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 25 | attendant_b.json | 74 | 0 | 24 | 0 | 0 | 0 | 41 | 0 |
| 26 | armor/ember/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 27 | armor/ember/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 28 | armor/ember/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 29 | armor/stone_whale/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 30 | armor/stone_whale/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 31 | armor/stone_whale/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 32 | armor/thunder_wing/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 33 | armor/thunder_wing/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 34 | armor/thunder_wing/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 35 | armor/tide_shell/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 36 | armor/tide_shell/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 37 | armor/tide_shell/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 38 | items/abyss_storm_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 2 | 0 |
| 39 | items/abyss_storm_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 3 | 0 |
| 40 | items/abyss_storm_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 8 | 0 | 3 | 0 |
| 41 | items/abyss_storm_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 3 | 0 |
| 42 | items/captain_pike/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 43 | items/captain_pike/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 44 | items/captain_pike/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 45 | items/thunder_wing_bow/common.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 46 | items/thunder_wing_bow/common_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 47 | items/thunder_wing_bow/common_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 48 | items/thunder_wing_bow/common_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 49 | items/thunder_wing_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 50 | items/thunder_wing_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 0 | 0 |
| 51 | items/thunder_wing_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 52 | items/thunder_wing_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 53 | items/thunder_wing_bow/rare.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 54 | items/thunder_wing_bow/rare_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 55 | items/thunder_wing_bow/rare_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 56 | items/thunder_wing_bow/rare_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 57 | items/tidecaller_claw/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 58 | items/tidecaller_claw/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 59 | items/tidecaller_claw/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 60 | items/whalebone_greatsword/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 2 | 0 |
| 61 | items/whalebone_greatsword/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 4 | 0 |
| 62 | items/whalebone_greatsword/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 4 | 0 |
| 63 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 64 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 65 | pearl_keeper.json | 52 | 0 | 12 | 0 | 8 | 0 | 322 | 0 |
| 66 | ash_thrall.json | 49 | 0 | 0 | 0 | 0 | 0 | 999 | 0 |
| 67 | items/ember_lantern/common.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 68 | items/ember_lantern/legend.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 69 | items/ember_lantern/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 70 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 71 | elder_thrall.json | 43 | 0 | 0 | 0 | 0 | 0 | 829 | 2 |
| 72 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 73 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 74 | artisan.json | 36 | 0 | 12 | 0 | 0 | 0 | 9 | 0 |
| 75 | attendant_a.json | 36 | 0 | 12 | 0 | 0 | 0 | 8 | 0 |
| 76 | items/tide_chime_charm/legend.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 77 | items/tide_chime_charm/rare.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 78 | bone_mortar.json | 31 | 0 | 0 | 0 | 0 | 0 | 633 | 0 |
| 79 | ash_dragger.json | 30 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| 80 | peak_fledgling.json | 25 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| 81 | armor/peak_crossing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 82 | armor/peak_crossing/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 83 | armor/tide_wader/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 84 | armor/tide_wader/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 85 | armor/whale_road/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 86 | armor/whale_road/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 87 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 88 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 89 | drift_hermit.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 90 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 91 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 92 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 93 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 94 | items/tide_watching_shell/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 95 | items/tide_watching_shell/rare.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 96 | sand_burrower.json | 6 | 0 | 0 | 0 | 0 | 0 | 27 | 1 |
| 97 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 98 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 99 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 100 | scorched_warden.json | 5 | 0 | 0 | 0 | 0 | 0 | 9 | 1 |
| 101 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 102 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 103 | rib_archer.json | 4 | 0 | 0 | 0 | 0 | 0 | 93 | 0 |
| 104 | young_thunder_wing.json | 4 | 0 | 0 | 0 | 0 | 0 | 81 | 0 |
| 105 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 106 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 107 | items/abyss_greatsword/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 11 | 0 |
| 108 | items/tide_shell_trident/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 109 | items/tide_shell_trident/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 110 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 111 | lightning_gleaner.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 112 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 113 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 114 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 115 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 116 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 117 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 118 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |

## 骨ごとの詳細 (上位 25 個)

### armor/ember/leggings.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 270 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/ember/leggings_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/ember/leggings_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/leggings.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 270 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/leggings_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/leggings_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/leggings.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 270 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/leggings_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/leggings_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/leggings.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 270 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/leggings_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/leggings_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightLeg | 298 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| armorLeftLeg | 284 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |

### attendant_c.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 318 | 0 | 0 | 28 | 0 | 0 | 0 | 0 | - |
| head | 156 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | ok(埋36%/深0.9) |
| armL | 76 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | ok(埋30%/深0.9) |
| armR | 76 | 0 | 0 | 0 | 0 | 0 | 0 | 6 | ok(埋33%/深0.9) |

### items/smith_hammer/common.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 370 | 0 | 0 | 36 | 0 | 0 | 0 | 6 | - |

### items/smith_hammer/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 498 | 0 | 0 | 36 | 0 | 0 | 0 | 2 | - |

### items/smith_hammer/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 426 | 0 | 0 | 36 | 0 | 0 | 0 | 2 | - |

### items/waterway_fang/common.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 354 | 0 | 0 | 36 | 0 | 0 | 0 | 0 | - |

### items/waterway_fang/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 470 | 0 | 0 | 36 | 0 | 0 | 0 | 0 | - |

### items/waterway_fang/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 422 | 0 | 0 | 36 | 0 | 0 | 0 | 0 | - |

### ash_smith.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| apron | 172 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | ok(埋32%/深1.3) |
| forearm_r | 186 | 0 | 0 | 0 | 0 | 0 | 0 | 729 | ok(埋51%/深1.8) |
| forearm_l | 186 | 0 | 0 | 0 | 0 | 0 | 0 | 713 | ok(埋51%/深1.8) |

### ash_husk.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| arm_r | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 16 | ok(埋52%/深1.2) |
| arm_l | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 20 | ok(埋52%/深1.2) |
| crust_body | 252 | 0 | 0 | 0 | 0 | 0 | 0 | 1132 | shallow(埋20%/深0.6) |
| flare_body | 108 | 0 | 0 | 0 | 0 | 0 | 0 | 318 | shallow(埋52%/深0.6) |

### armor/peak_crossing/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 134 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

### armor/tide_wader/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 134 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

### armor/whale_road/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 134 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

### attendant_b.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 276 | 0 | 0 | 24 | 0 | 0 | 0 | 0 | - |
| armL | 94 | 0 | 0 | 0 | 0 | 0 | 0 | 40 | ok(埋46%/深0.8) |
| armR | 86 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | ok(埋42%/深1.0) |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

82 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 4 個。
- `entity/bubble_puffer.geo.json`: 2/2000 本 {'body': 2}
- `entity/coral_thrall.geo.json`: 1/2000 本 {'head': 1}
- `entity/raiju.geo.json`: 1/2000 本 {'head': 1}
- `entity/whalebone_captain.geo.json`: 2/2000 本 {'body': 2}

## ブロックモデル

面の欠け・宙に浮く部品なし。
