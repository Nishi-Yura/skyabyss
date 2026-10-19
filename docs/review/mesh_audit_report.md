# メッシュ監査 (tools/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 215 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **5597**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 612 / つぶれた三角形: 80
- 関節: detach 0 / shallow 0 / ok 402 (うち pivot が親の外: 223)
- ブロックモデル: 36 個中、面の欠け・宙に浮く部品のあるもの 0

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | young_ember.json | 132 | 0 | 44 | 0 | 0 | 0 | 0 | 0 |
| 2 | ash_smith.json | 90 | 0 | 6 | 0 | 0 | 0 | 1442 | 0 |
| 3 | armor/whale_road/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 4 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 5 | armor/stone_whale/chestplate_legend.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 6 | armor/thunder_wing/chestplate_legend.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 7 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 8 | pearl_keeper.json | 52 | 0 | 12 | 0 | 8 | 0 | 322 | 0 |
| 9 | armor/ember/chestplate_legend.json | 51 | 0 | 17 | 0 | 0 | 0 | 0 | 0 |
| 10 | armor/tide_shell/chestplate_legend.json | 51 | 0 | 17 | 0 | 0 | 0 | 0 | 0 |
| 11 | ash_thrall.json | 49 | 0 | 0 | 0 | 0 | 0 | 999 | 0 |
| 12 | armor/ember/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 13 | armor/ember/chestplate_rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 14 | armor/stone_whale/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 15 | armor/stone_whale/chestplate_rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 16 | armor/thunder_wing/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 17 | armor/thunder_wing/chestplate_rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 18 | armor/tide_shell/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 19 | armor/tide_shell/chestplate_rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 20 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 21 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 22 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 23 | artisan.json | 36 | 0 | 12 | 0 | 0 | 0 | 9 | 0 |
| 24 | bone_mortar.json | 31 | 0 | 0 | 0 | 0 | 0 | 633 | 0 |
| 25 | armor/ember/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 26 | armor/ember/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 27 | armor/ember/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 28 | armor/ember/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 29 | armor/ember/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 30 | armor/ember/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 31 | armor/stone_whale/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 32 | armor/stone_whale/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 33 | armor/stone_whale/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 34 | armor/stone_whale/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 35 | armor/stone_whale/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 36 | armor/stone_whale/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 37 | armor/thunder_wing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 38 | armor/thunder_wing/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 39 | armor/thunder_wing/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 40 | armor/thunder_wing/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 41 | armor/thunder_wing/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 42 | armor/thunder_wing/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 43 | armor/tide_shell/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 44 | armor/tide_shell/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 45 | armor/tide_shell/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 46 | armor/tide_shell/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 47 | armor/tide_shell/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 48 | armor/tide_shell/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 49 | armor/whale_road/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 50 | armor/whale_road/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 51 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 52 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 53 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 54 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 55 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 56 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 57 | items/tide_watching_shell/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 58 | items/tide_watching_shell/rare.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 59 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 60 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 61 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 62 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 63 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 64 | rib_archer.json | 4 | 0 | 0 | 0 | 0 | 0 | 93 | 0 |
| 65 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 66 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 67 | items/gale_feather/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 68 | items/gale_feather/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 69 | items/gale_feather/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 70 | items/storm_anklet/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 71 | items/storm_anklet/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 72 | items/storm_anklet/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 73 | items/storm_charm/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 74 | items/storm_charm/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 75 | items/storm_charm/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 76 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 77 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 78 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 79 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 80 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 81 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 82 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 83 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |

## 骨ごとの詳細 (上位 25 個)

### young_ember.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| saddle | 632 | 0 | 0 | 44 | 0 | 0 | 0 | 0 | ok(埋54%/深1.0) |

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

### smith_hammer.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| socket_main | 178 | 0 | 0 | 24 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 500 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 180 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 132 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 474 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### storyteller.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 110 | 0 | 0 | 13 | 0 | 0 | 0 | 0 | - |
| armL | 96 | 0 | 0 | 0 | 0 | 0 | 0 | 11 | ok(埋40%/深0.7) |
| legL | 46 | 0 | 0 | 0 | 0 | 0 | 0 | 176 | ok(埋69%/深0.9) |
| legR | 46 | 0 | 0 | 0 | 0 | 0 | 0 | 172 | ok(埋70%/深0.9) |

### pearl_keeper.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| shell | 918 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| claw_r_pincer | 38 | 0 | 0 | 0 | 0 | 0 | 0 | 322 | ok(埋31%/深3.0) |

### armor/ember/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 442 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 120 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 576 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### ash_thrall.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| arm_r | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | ok(埋53%/深1.1) |
| arm_l | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 26 | ok(埋50%/深1.1) |
| crust_body | 168 | 0 | 0 | 0 | 0 | 0 | 0 | 443 | ok(埋16%/深0.8) |
| flare_body | 108 | 0 | 0 | 0 | 0 | 0 | 0 | 509 | ok(埋36%/深0.7) |

### armor/ember/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 96 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 120 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 156 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 180 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 132 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 124 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 124 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 116 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 116 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### whale_chaser.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 258 | 0 | 0 | 16 | 0 | 0 | 0 | 0 | - |

### thunder_supplicant.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 84 | 0 | 0 | 10 | 0 | 0 | 0 | 0 | - |
| head | 312 | 0 | 0 | 1 | 0 | 0 | 0 | 121 | ok(埋20%/深1.3) |

### whalebone_captain.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 364 | 0 | 0 | 12 | 0 | 0 | 0 | 1 | - |
| handL | 76 | 0 | 0 | 0 | 0 | 0 | 0 | 50 | ok(埋46%/深1.3) |

### artisan.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 440 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armL | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | ok(埋40%/深1.0) |
| armR | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 5 | ok(埋37%/深1.0) |

### bone_mortar.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| shield | 198 | 0 | 0 | 0 | 0 | 0 | 0 | 633 | ok(埋35%/深1.3) |

### armor/ember/boots.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightBoot | 74 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftBoot | 74 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

70 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 4 個。
- `entity/bubble_puffer.geo.json`: 2/2000 本 {'body': 2}
- `entity/coral_thrall.geo.json`: 1/2000 本 {'head': 1}
- `entity/raiju.geo.json`: 1/2000 本 {'head': 1}
- `entity/whalebone_captain.geo.json`: 2/2000 本 {'body': 2}

## ブロックモデル

面の欠け・宙に浮く部品なし。
