# メッシュ監査 (tools/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 211 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **15260**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 724 / つぶれた三角形: 80
- 関節: detach 43 / shallow 52 / ok 281 (うち pivot が親の外: 239)
- ブロックモデル: 33 個中、面の欠け・宙に浮く部品のあるもの 0

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | items/cinder_lamp/common.json | 181 | 0 | 44 | 0 | 0 | 0 | 984 | 0 |
| 2 | items/cinder_lamp/legend.json | 181 | 0 | 44 | 0 | 0 | 0 | 984 | 0 |
| 3 | items/cinder_lamp/rare.json | 181 | 0 | 44 | 0 | 0 | 0 | 984 | 0 |
| 4 | ash_smith.json | 143 | 0 | 22 | 0 | 0 | 0 | 1442 | 5 |
| 5 | armor/stone_whale/chestplate_legend.json | 83 | 0 | 20 | 0 | 0 | 0 | 460 | 0 |
| 6 | armor/thunder_wing/chestplate_legend.json | 79 | 0 | 20 | 0 | 0 | 0 | 388 | 0 |
| 7 | armor/whale_road/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 8 | armor/tide_shell/chestplate_legend.json | 72 | 0 | 17 | 0 | 0 | 0 | 424 | 0 |
| 9 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 10 | armor/stone_whale/chestplate.json | 70 | 0 | 16 | 0 | 0 | 0 | 456 | 0 |
| 11 | armor/stone_whale/chestplate_rare.json | 70 | 0 | 16 | 0 | 0 | 0 | 454 | 0 |
| 12 | armor/ember/chestplate_legend.json | 69 | 0 | 17 | 0 | 0 | 0 | 367 | 0 |
| 13 | armor/tide_shell/chestplate.json | 69 | 0 | 16 | 0 | 0 | 0 | 420 | 0 |
| 14 | armor/tide_shell/chestplate_rare.json | 69 | 0 | 16 | 0 | 0 | 0 | 422 | 0 |
| 15 | armor/thunder_wing/chestplate.json | 67 | 0 | 16 | 0 | 0 | 0 | 389 | 0 |
| 16 | armor/thunder_wing/chestplate_rare.json | 67 | 0 | 16 | 0 | 0 | 0 | 387 | 0 |
| 17 | armor/ember/chestplate.json | 65 | 0 | 16 | 0 | 0 | 0 | 358 | 0 |
| 18 | armor/ember/chestplate_rare.json | 65 | 0 | 16 | 0 | 0 | 0 | 359 | 0 |
| 19 | artisan.json | 60 | 0 | 20 | 0 | 0 | 0 | 8 | 0 |
| 20 | thunder_supplicant.json | 59 | 0 | 11 | 0 | 0 | 0 | 120 | 4 |
| 21 | whale_chaser.json | 49 | 0 | 16 | 0 | 0 | 0 | 0 | 1 |
| 22 | storyteller.json | 45 | 0 | 13 | 0 | 0 | 0 | 0 | 2 |
| 23 | whalebone_captain.json | 40 | 0 | 12 | 0 | 0 | 0 | 8 | 4 |
| 24 | pearl_keeper.json | 36 | 0 | 12 | 0 | 8 | 0 | 0 | 0 |
| 25 | ember_titan_mid.json | 30 | 0 | 0 | 0 | 0 | 0 | 1 | 6 |
| 26 | armor/ember/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 27 | armor/ember/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 28 | armor/ember/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 29 | armor/ember/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 30 | armor/ember/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 31 | armor/ember/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 32 | armor/stone_whale/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 33 | armor/stone_whale/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 34 | armor/stone_whale/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 35 | armor/stone_whale/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 36 | armor/stone_whale/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 37 | armor/stone_whale/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 38 | armor/thunder_wing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 39 | armor/thunder_wing/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 40 | armor/thunder_wing/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 41 | armor/thunder_wing/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 42 | armor/thunder_wing/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 43 | armor/thunder_wing/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 44 | armor/tide_shell/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 45 | armor/tide_shell/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 46 | armor/tide_shell/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 47 | armor/tide_shell/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 48 | armor/tide_shell/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 49 | armor/tide_shell/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 50 | armor/whale_road/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 51 | armor/whale_road/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 52 | ash_thrall.json | 24 | 0 | 0 | 0 | 0 | 0 | 173 | 4 |
| 53 | armor/stone_whale/helmet_legend.json | 20 | 0 | 0 | 0 | 0 | 0 | 400 | 0 |
| 54 | armor/stone_whale/helmet_rare.json | 20 | 0 | 0 | 0 | 0 | 0 | 400 | 0 |
| 55 | armor/stone_whale/helmet.json | 19 | 0 | 0 | 0 | 0 | 0 | 397 | 0 |
| 56 | armor/thunder_wing/helmet.json | 19 | 0 | 0 | 0 | 0 | 0 | 398 | 0 |
| 57 | armor/thunder_wing/helmet_legend.json | 19 | 0 | 0 | 0 | 0 | 0 | 397 | 0 |
| 58 | armor/thunder_wing/helmet_rare.json | 19 | 0 | 0 | 0 | 0 | 0 | 397 | 0 |
| 59 | armor/tide_shell/helmet.json | 19 | 0 | 0 | 0 | 0 | 0 | 380 | 0 |
| 60 | armor/tide_shell/helmet_legend.json | 19 | 0 | 0 | 0 | 0 | 0 | 381 | 0 |
| 61 | armor/tide_shell/helmet_rare.json | 19 | 0 | 0 | 0 | 0 | 0 | 381 | 0 |
| 62 | ember_titan.json | 19 | 0 | 0 | 0 | 0 | 0 | 4 | 7 |
| 63 | armor/ember/helmet.json | 18 | 0 | 0 | 0 | 0 | 0 | 375 | 0 |
| 64 | armor/ember/helmet_legend.json | 18 | 0 | 0 | 0 | 0 | 0 | 376 | 0 |
| 65 | armor/ember/helmet_rare.json | 18 | 0 | 0 | 0 | 0 | 0 | 376 | 0 |
| 66 | elder_sentinel.json | 18 | 0 | 2 | 0 | 0 | 0 | 4 | 4 |
| 67 | rock_shell_beetle.json | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| 68 | ember_titan_far.json | 16 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 69 | bone_sentinel.json | 15 | 0 | 1 | 0 | 0 | 0 | 9 | 4 |
| 70 | raiju.json | 15 | 0 | 0 | 0 | 0 | 0 | 18 | 3 |
| 71 | thunder_wing_far.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 72 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 73 | bone_mortar.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 74 | reef_scuttler.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 75 | rib_archer.json | 9 | 0 | 0 | 0 | 0 | 0 | 5 | 5 |
| 76 | elder_sentry.json | 8 | 0 | 2 | 0 | 0 | 0 | 12 | 2 |
| 77 | moss_crawler.json | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 8 |
| 78 | cinder_roller.json | 7 | 0 | 2 | 0 | 0 | 0 | 0 | 1 |
| 79 | ember_moth.json | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 80 | thunder_wing.json | 7 | 0 | 0 | 0 | 0 | 0 | 12 | 3 |
| 81 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 82 | items/tide_watching_shell/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 83 | items/tide_watching_shell/rare.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 84 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 85 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 86 | feather_warden.json | 5 | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| 87 | thunder_wing_mid.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 88 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 89 | watcher.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 90 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 91 | items/gale_feather/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 92 | items/gale_feather/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 93 | items/gale_feather/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 94 | items/storm_anklet/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 95 | items/storm_anklet/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 96 | items/storm_anklet/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 97 | items/storm_charm/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 98 | items/storm_charm/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 99 | items/storm_charm/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 100 | tide_beckoner.json | 3 | 0 | 0 | 0 | 12 | 0 | 50 | 1 |
| 101 | armor/whale_road/helmet.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 102 | bone_hauler.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 103 | bubble_puffer.json | 2 | 0 | 0 | 0 | 0 | 0 | 46 | 0 |
| 104 | rib_archer_bow.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 105 | stone_whale.json | 2 | 0 | 0 | 0 | 0 | 0 | 38 | 1 |
| 106 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 107 | stone_whale_mid.json | 2 | 0 | 0 | 0 | 0 | 0 | 36 | 1 |
| 108 | tide_shell_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 46 | 0 |
| 109 | coral_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 110 | static_moth.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 111 | stone_whale_far.json | 1 | 0 | 0 | 0 | 0 | 0 | 12 | 1 |
| 112 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 113 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 114 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 115 | tide_shell.json | 1 | 0 | 0 | 0 | 0 | 0 | 38 | 0 |
| 116 | tide_shell_mid.json | 1 | 0 | 0 | 0 | 0 | 0 | 38 | 0 |

## 骨ごとの詳細 (上位 25 個)

### items/cinder_lamp/common.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 136 | 0 | 0 | 44 | 0 | 0 | 0 | 984 | - |

### items/cinder_lamp/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 136 | 0 | 0 | 44 | 0 | 0 | 0 | 984 | - |

### items/cinder_lamp/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 136 | 0 | 0 | 44 | 0 | 0 | 0 | 984 | - |

### ash_smith.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| belly | 108 | 0 | 0 | 16 | 0 | 0 | 0 | 0 | shallow(埋2%/深0.8) |
| apron | 162 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | ok(埋4%/深1.3) |
| head | 264 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋2%/深3.0) |
| forearm_r | 254 | 0 | 0 | 0 | 0 | 0 | 0 | 729 | ok(埋4%/深1.2) |
| arm_l | 196 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋2%/深2.8) |
| forearm_l | 254 | 0 | 0 | 0 | 0 | 0 | 0 | 713 | ok(埋4%/深1.2) |
| bellows_l | 138 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋2%/深2.6) |
| bellows_r | 138 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋3%/深2.8) |

### armor/stone_whale/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 500 | 0 | 0 | 4 | 0 | 0 | 0 | 460 | - |
| armorRightArm | 180 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 132 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 474 | 0 | 0 | 4 | 0 | 0 | 0 | 388 | - |
| armorRightArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/whale_road/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 134 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 576 | 0 | 0 | 1 | 0 | 0 | 0 | 424 | - |
| armorRightArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### smith_hammer.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| socket_main | 178 | 0 | 0 | 24 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 338 | 0 | 0 | 0 | 0 | 0 | 0 | 456 | - |
| armorRightArm | 156 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/stone_whale/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 386 | 0 | 0 | 0 | 0 | 0 | 0 | 454 | - |
| armorRightArm | 180 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 132 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 442 | 0 | 0 | 1 | 0 | 0 | 0 | 367 | - |
| armorRightArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 120 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 338 | 0 | 0 | 0 | 0 | 0 | 0 | 420 | - |
| armorRightArm | 116 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 116 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/tide_shell/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 386 | 0 | 0 | 0 | 0 | 0 | 0 | 422 | - |
| armorRightArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 148 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 308 | 0 | 0 | 0 | 0 | 0 | 0 | 389 | - |
| armorRightArm | 124 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 124 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 356 | 0 | 0 | 0 | 0 | 0 | 0 | 387 | - |
| armorRightArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 144 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 268 | 0 | 0 | 0 | 0 | 0 | 0 | 358 | - |
| armorRightArm | 96 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### armor/ember/chestplate_rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 316 | 0 | 0 | 0 | 0 | 0 | 0 | 359 | - |
| armorRightArm | 108 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 120 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | - |

### artisan.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 440 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| head | 326 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | ok(埋14%/深2.8) |
| armL | 76 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | ok(埋32%/深1.8) |
| armR | 76 | 0 | 0 | 0 | 0 | 0 | 0 | 4 | ok(埋27%/深1.6) |

### thunder_supplicant.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 84 | 0 | 0 | 10 | 0 | 0 | 0 | 0 | - |
| head | 332 | 0 | 0 | 1 | 0 | 0 | 0 | 120 | detach(埋0%/深0.0) |
| robe | 322 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| arm_r | 56 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| arm_l | 56 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |

### whale_chaser.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 258 | 0 | 0 | 16 | 0 | 0 | 0 | 0 | - |
| hat | 228 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋3%/深0.8) |

### storyteller.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 110 | 0 | 0 | 13 | 0 | 0 | 0 | 0 | - |
| hair | 68 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| hair2 | 320 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋1%/深0.8) |

### whalebone_captain.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 364 | 0 | 0 | 12 | 0 | 0 | 0 | 8 | - |
| armR | 190 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋0%/深1.5) |
| forearmR | 146 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋3%/深1.0) |
| armL | 190 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋1%/深0.7) |
| forearmL | 146 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋3%/深1.0) |

### pearl_keeper.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| shell | 918 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

### ember_titan_mid.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| ridge | 520 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | ok(埋32%/深8.3) |
| jaw | 68 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| tail | 202 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| smoke_0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| smoke_1 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| smoke_2 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| smoke_3 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

66 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 4 個。
- `entity/bubble_puffer.geo.json`: 2/2000 本 {'body': 2}
- `entity/coral_thrall.geo.json`: 1/2000 本 {'head': 1}
- `entity/raiju.geo.json`: 1/2000 本 {'head': 1}
- `entity/whalebone_captain.geo.json`: 2/2000 本 {'body': 2}

## ブロックモデル

面の欠け・宙に浮く部品なし。
