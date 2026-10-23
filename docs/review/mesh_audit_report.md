# メッシュ監査 (tools/audit/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/audit/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 290 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **4548**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 2028 / つぶれた三角形: 104
- 関節: detach 37 / shallow 7 / ok 630 (うち pivot が親の外: 365)
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
| 20 | armor/ash_walking/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 21 | armor/peak_crossing/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 22 | armor/tide_wader/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 23 | armor/whale_road/chestplate.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 24 | attendant_b.json | 74 | 0 | 24 | 0 | 0 | 0 | 41 | 0 |
| 25 | armor/ember/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 26 | armor/ember/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 27 | armor/ember/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 28 | armor/stone_whale/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 29 | armor/stone_whale/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 30 | armor/stone_whale/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 31 | armor/thunder_wing/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 32 | armor/thunder_wing/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 33 | armor/thunder_wing/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 34 | armor/tide_shell/chestplate.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 35 | armor/tide_shell/chestplate_legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 36 | armor/tide_shell/chestplate_rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 37 | items/abyss_storm_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 2 | 0 |
| 38 | items/abyss_storm_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 3 | 0 |
| 39 | items/abyss_storm_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 8 | 0 | 3 | 0 |
| 40 | items/abyss_storm_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 3 | 0 |
| 41 | items/captain_pike/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 42 | items/captain_pike/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 43 | items/captain_pike/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 44 | items/thunder_wing_bow/common.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 45 | items/thunder_wing_bow/common_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 46 | items/thunder_wing_bow/common_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 47 | items/thunder_wing_bow/common_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 48 | items/thunder_wing_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 49 | items/thunder_wing_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 0 | 0 |
| 50 | items/thunder_wing_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 51 | items/thunder_wing_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 52 | items/thunder_wing_bow/rare.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 53 | items/thunder_wing_bow/rare_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 54 | items/thunder_wing_bow/rare_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 55 | items/thunder_wing_bow/rare_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 56 | items/tidecaller_claw/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 57 | items/tidecaller_claw/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 58 | items/tidecaller_claw/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 59 | items/whalebone_greatsword/common.json | 72 | 0 | 24 | 0 | 0 | 0 | 2 | 0 |
| 60 | items/whalebone_greatsword/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 4 | 0 |
| 61 | items/whalebone_greatsword/rare.json | 72 | 0 | 24 | 0 | 0 | 0 | 4 | 0 |
| 62 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 63 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 64 | pearl_keeper.json | 52 | 0 | 12 | 0 | 8 | 0 | 322 | 0 |
| 65 | items/ember_lantern/common.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 66 | items/ember_lantern/legend.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 67 | items/ember_lantern/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 68 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 69 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 70 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 71 | artisan.json | 36 | 0 | 12 | 0 | 0 | 0 | 9 | 0 |
| 72 | attendant_a.json | 36 | 0 | 12 | 0 | 0 | 0 | 8 | 0 |
| 73 | bone_mortar.json | 36 | 0 | 0 | 0 | 0 | 0 | 633 | 1 |
| 74 | items/tide_chime_charm/legend.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 75 | items/tide_chime_charm/rare.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 76 | ash_dragger.json | 30 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| 77 | peak_fledgling.json | 25 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| 78 | armor/ash_walking/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 79 | armor/ash_walking/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 80 | armor/peak_crossing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 81 | armor/peak_crossing/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 82 | armor/tide_wader/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 83 | armor/tide_wader/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 84 | armor/whale_road/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 85 | armor/whale_road/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 86 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 87 | abyss_charged_moth_s1.json | 20 | 0 | 0 | 0 | 0 | 0 | 113 | 3 |
| 88 | rib_archer.json | 19 | 0 | 0 | 0 | 0 | 0 | 93 | 3 |
| 89 | ash_smith.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 90 | abyss_lightning_idol_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 78 | 2 |
| 91 | abyss_rib_guard_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 69 | 2 |
| 92 | abyss_salt_husk_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 77 | 2 |
| 93 | ceiling_walker.json | 12 | 0 | 0 | 0 | 0 | 0 | 58 | 2 |
| 94 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 95 | abyss_scorched_bird_s1.json | 11 | 0 | 3 | 0 | 0 | 0 | 44 | 0 |
| 96 | abyss_scorched_bird_s3.json | 11 | 0 | 3 | 0 | 0 | 0 | 43 | 0 |
| 97 | abyss_scorched_bird_s2.json | 10 | 0 | 3 | 0 | 0 | 0 | 35 | 0 |
| 98 | abyss_white_shell_keeper_s1.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 99 | ash_trail.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 100 | cinder_mite.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 101 | drift_hermit.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 102 | abyss_charged_moth_s3.json | 6 | 0 | 0 | 0 | 0 | 0 | 126 | 0 |
| 103 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 104 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 105 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 106 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 107 | items/tide_watching_shell/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 108 | items/tide_watching_shell/rare.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 109 | sand_burrower.json | 6 | 0 | 0 | 0 | 0 | 0 | 27 | 1 |
| 110 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 111 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 112 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 113 | abyss_charged_moth_s2.json | 5 | 0 | 0 | 0 | 0 | 0 | 113 | 0 |
| 114 | rope_hanger.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 115 | scorched_warden.json | 5 | 0 | 0 | 0 | 0 | 0 | 9 | 1 |
| 116 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 117 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 118 | young_thunder_wing.json | 4 | 0 | 0 | 0 | 0 | 0 | 81 | 0 |
| 119 | abyss_lightning_idol_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 73 | 0 |
| 120 | abyss_lightning_idol_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 79 | 0 |
| 121 | abyss_rib_guard_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 67 | 0 |
| 122 | abyss_rib_guard_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 74 | 0 |
| 123 | abyss_salt_husk_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 72 | 0 |
| 124 | abyss_salt_husk_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 77 | 0 |
| 125 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 126 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 127 | heart_warden.json | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 128 | items/abyss_greatsword/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 11 | 0 |
| 129 | items/tide_shell_trident/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 130 | items/tide_shell_trident/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 131 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 132 | lightning_gleaner.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 133 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 134 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 135 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 136 | ash_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 137 | elder_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 138 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 139 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 140 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 141 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |

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

### armor/ash_walking/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 146 | 0 | 0 | 17 | 0 | 0 | 0 | 0 | - |
| armorRightArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 66 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | - |

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

### armor/ember/chestplate.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorRightArm | 400 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |
| armorLeftArm | 400 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | - |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

99 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 4 個。
- `entity/bubble_puffer.geo.json`: 2/2000 本 {'body': 2}
- `entity/coral_thrall.geo.json`: 1/2000 本 {'head': 1}
- `entity/raiju.geo.json`: 1/2000 本 {'head': 1}
- `entity/whalebone_captain.geo.json`: 2/2000 本 {'body': 2}

## ブロックモデル

面の欠け・宙に浮く部品なし。
