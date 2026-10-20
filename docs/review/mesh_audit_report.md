# メッシュ監査 (tools/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 222 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **5705**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 1824 / つぶれた三角形: 104
- 関節: detach 0 / shallow 0 / ok 402 (うち pivot が親の外: 223)
- ブロックモデル: 95 個中、面の欠け・宙に浮く部品のあるもの 32

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
| 13 | items/smith_hammer/common.json | 108 | 0 | 36 | 0 | 0 | 0 | 6 | 0 |
| 14 | items/smith_hammer/legend.json | 108 | 0 | 36 | 0 | 0 | 0 | 2 | 0 |
| 15 | items/smith_hammer/rare.json | 108 | 0 | 36 | 0 | 0 | 0 | 2 | 0 |
| 16 | items/waterway_fang/common.json | 108 | 0 | 36 | 0 | 0 | 0 | 0 | 0 |
| 17 | items/waterway_fang/legend.json | 108 | 0 | 36 | 0 | 0 | 0 | 0 | 0 |
| 18 | items/waterway_fang/rare.json | 108 | 0 | 36 | 0 | 0 | 0 | 0 | 0 |
| 19 | ash_smith.json | 90 | 0 | 6 | 0 | 0 | 0 | 1442 | 0 |
| 20 | armor/whale_road/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 21 | armor/ember/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 22 | armor/ember/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 23 | armor/ember/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 24 | armor/stone_whale/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 25 | armor/stone_whale/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 26 | armor/stone_whale/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 27 | armor/thunder_wing/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 28 | armor/thunder_wing/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 29 | armor/thunder_wing/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 30 | armor/tide_shell/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 31 | armor/tide_shell/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 32 | armor/tide_shell/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 33 | items/abyss_storm_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 2 | 0 |
| 34 | items/abyss_storm_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 3 | 0 |
| 35 | items/abyss_storm_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 8 | 0 | 3 | 0 |
| 36 | items/abyss_storm_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 3 | 0 |
| 37 | items/captain_pike/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 38 | items/captain_pike/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 39 | items/captain_pike/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 40 | items/thunder_wing_bow/common.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 41 | items/thunder_wing_bow/common_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 42 | items/thunder_wing_bow/common_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 43 | items/thunder_wing_bow/common_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 44 | items/thunder_wing_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 45 | items/thunder_wing_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 0 | 0 |
| 46 | items/thunder_wing_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 47 | items/thunder_wing_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 48 | items/thunder_wing_bow/rare.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 49 | items/thunder_wing_bow/rare_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 50 | items/thunder_wing_bow/rare_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 51 | items/thunder_wing_bow/rare_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 52 | items/tidecaller_claw/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 53 | items/tidecaller_claw/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 54 | items/tidecaller_claw/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 55 | items/whalebone_greatsword/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 2 | 0 |
| 56 | items/whalebone_greatsword/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 4 | 0 |
| 57 | items/whalebone_greatsword/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 4 | 0 |
| 58 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 59 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 60 | pearl_keeper.json | 52 | 0 | 12 | 0 | 8 | 0 | 322 | 0 |
| 61 | ash_thrall.json | 49 | 0 | 0 | 0 | 0 | 0 | 999 | 0 |
| 62 | items/ember_lantern/common.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 63 | items/ember_lantern/legend.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 64 | items/ember_lantern/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 65 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 66 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 67 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 68 | artisan.json | 36 | 0 | 12 | 0 | 0 | 0 | 9 | 0 |
| 69 | items/tide_chime_charm/legend.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 70 | items/tide_chime_charm/rare.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 71 | bone_mortar.json | 31 | 0 | 0 | 0 | 0 | 0 | 633 | 0 |
| 72 | armor/whale_road/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 73 | armor/whale_road/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 74 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 75 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 76 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 77 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 78 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 79 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 80 | items/tide_watching_shell/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 81 | items/tide_watching_shell/rare.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 82 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 83 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 84 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 85 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 86 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 87 | rib_archer.json | 4 | 0 | 0 | 0 | 0 | 0 | 93 | 0 |
| 88 | young_thunder_wing.json | 4 | 0 | 0 | 0 | 0 | 0 | 81 | 0 |
| 89 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 90 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 91 | items/abyss_greatsword/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 13 | 0 |
| 92 | items/tide_shell_trident/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 93 | items/tide_shell_trident/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 94 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 95 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 96 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 97 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 98 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 99 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 100 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 101 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |

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

### armor/whale_road/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 134 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 400 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 400 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 462 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 462 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 414 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 414 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 400 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 400 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 462 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 462 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

70 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 4 個。
- `entity/bubble_puffer.geo.json`: 2/2000 本 {'body': 2}
- `entity/coral_thrall.geo.json`: 1/2000 本 {'head': 1}
- `entity/raiju.geo.json`: 1/2000 本 {'head': 1}
- `entity/whalebone_captain.geo.json`: 2/2000 本 {'body': 2}

## ブロックモデル

- `relic_bell_ember_ring.json`: element 15: exposed faces missing north,south / element 16: exposed faces missing north,south / element 17: exposed faces missing west,east / element 18: exposed faces missing west,east / element 19: exposed faces missing north,south / element 20: exposed faces missing north,south
- `relic_bell_storm_ring.json`: element 13: exposed faces missing north,south / element 14: exposed faces missing north,south / element 15: exposed faces missing west,east / element 16: exposed faces missing west,east / element 17: exposed faces missing north,south / element 18: exposed faces missing north,south
- `relic_bell_tide_ring.json`: element 14: exposed faces missing north,south / element 15: exposed faces missing north,south / element 16: exposed faces missing west,east / element 17: exposed faces missing west,east / element 18: exposed faces missing north,south / element 19: exposed faces missing north,south
- `relic_bell_whale_ring.json`: element 14: exposed faces missing north,south / element 15: exposed faces missing north,south / element 16: exposed faces missing west,east / element 17: exposed faces missing west,east / element 18: exposed faces missing north,south / element 19: exposed faces missing north,south
- `relic_button_ember_pressed.json`: element 1: exposed faces missing west,east,north,south / element 3: exposed faces missing west,east,north,south
- `relic_button_ember_up.json`: element 1: exposed faces missing west,east,north,south / element 3: exposed faces missing west,east,north,south
- `relic_button_storm_pressed.json`: element 1: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,north,south
- `relic_button_storm_up.json`: element 1: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,north,south
- `relic_button_tide_pressed.json`: element 1: exposed faces missing west,east,north,south / element 4: exposed faces missing west,east,north,south
- `relic_button_tide_up.json`: element 1: exposed faces missing west,east,north,south / element 4: exposed faces missing west,east,north,south
- `relic_button_whale_pressed.json`: element 1: exposed faces missing west,east,north,south / element 3: exposed faces missing west,east,north,south
- `relic_button_whale_up.json`: element 1: exposed faces missing west,east,north,south / element 3: exposed faces missing west,east,north,south
- `relic_cache_ember_open.json`: element 8: exposed faces missing west,east,north,south
- `relic_cache_storm_open.json`: element 8: exposed faces missing west,east,north,south
- `relic_cache_tide_open.json`: element 8: exposed faces missing west,east,north,south
- `relic_cache_whale_open.json`: element 8: exposed faces missing west,east,north,south
- `relic_lever_ember_off.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_ember_on.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_storm_off.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_storm_on.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_tide_off.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_tide_on.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_whale_off.json`: element 2: exposed faces missing west,east,north,south
- `relic_lever_whale_on.json`: element 2: exposed faces missing west,east,north,south
- `relic_plate_ember_pressed.json`: element 4: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_ember_up.json`: element 4: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_storm_pressed.json`: element 4: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_storm_up.json`: element 4: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_tide_pressed.json`: element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_tide_up.json`: element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_whale_pressed.json`: element 4: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
- `relic_plate_whale_up.json`: element 4: exposed faces missing west,east,north,south / element 5: exposed faces missing west,east,down,north,south / element 6: exposed faces missing west,east,down,north,south
