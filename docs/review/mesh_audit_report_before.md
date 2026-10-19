# メッシュ監査 (tools/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 211 個 / 開いた辺の合計: **25655** / 裏面透けの画素数の合計: **193527**
- 逆法線の塊: 22 / 向きの食い違い: 1467 / 多様体でない辺: 167 / つぶれた三角形: 14
- 関節: pivot-out 235 / detach 15 / shallow 38 / ok 88
- ブロックモデル: 33 個中、面の欠け・宙に浮く部品のあるもの 6

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | thunder_wing.json | 1827 | 1750 | 0 | 0 | 0 | 0 | 701 | 10 |
| 2 | ember_titan.json | 1707 | 1505 | 0 | 26 | 1 | 0 | 1826 | 9 |
| 3 | tide_shell.json | 1366 | 1002 | 0 | 96 | 0 | 0 | 1527 | 0 |
| 4 | stone_whale.json | 1345 | 1297 | 0 | 4 | 0 | 0 | 111 | 7 |
| 5 | feather_warden.json | 972 | 416 | 0 | 0 | 0 | 0 | 10308 | 9 |
| 6 | stone_whale_heart_p1.json | 884 | 280 | 0 | 128 | 0 | 0 | 4412 | 0 |
| 7 | ash_smith.json | 850 | 455 | 6 | 0 | 0 | 0 | 6933 | 7 |
| 8 | stone_whale_heart_p2.json | 808 | 316 | 0 | 102 | 0 | 0 | 3730 | 0 |
| 9 | tide_beckoner.json | 789 | 343 | 0 | 96 | 0 | 0 | 2429 | 9 |
| 10 | stone_whale_heart_p3.json | 714 | 370 | 0 | 71 | 0 | 0 | 2628 | 0 |
| 11 | ember_titan_mid.json | 600 | 428 | 0 | 18 | 0 | 0 | 1543 | 9 |
| 12 | cinder_roller.json | 566 | 366 | 0 | 0 | 0 | 0 | 1596 | 25 |
| 13 | rib_archer.json | 531 | 333 | 0 | 0 | 0 | 0 | 3422 | 7 |
| 14 | moss_crawler.json | 527 | 383 | 0 | 0 | 0 | 0 | 782 | 21 |
| 15 | raiju.json | 520 | 399 | 0 | 0 | 0 | 0 | 1552 | 12 |
| 16 | tide_shell_mid.json | 518 | 291 | 0 | 48 | 0 | 0 | 1672 | 0 |
| 17 | thunder_supplicant.json | 517 | 254 | 0 | 0 | 0 | 0 | 4671 | 6 |
| 18 | thunder_wing_mid.json | 508 | 456 | 0 | 0 | 0 | 0 | 125 | 10 |
| 19 | pearl_keeper.json | 507 | 355 | 0 | 16 | 0 | 0 | 1998 | 1 |
| 20 | ember_heart_p1.json | 471 | 438 | 0 | 9 | 0 | 0 | 125 | 0 |
| 21 | ember_heart_p3.json | 463 | 345 | 0 | 6 | 0 | 0 | 2002 | 0 |
| 22 | elder_sentinel.json | 450 | 340 | 0 | 0 | 0 | 0 | 1587 | 7 |
| 23 | ember_heart_p2.json | 445 | 335 | 0 | 10 | 0 | 0 | 1603 | 0 |
| 24 | armor/thunder_wing/chestplate_legend.json | 424 | 150 | 4 | 60 | 0 | 0 | 1644 | 0 |
| 25 | bone_mortar.json | 416 | 187 | 0 | 0 | 0 | 0 | 4365 | 3 |
| 26 | tide_heart_p1.json | 408 | 371 | 0 | 4 | 0 | 0 | 503 | 0 |
| 27 | waterway_maw.json | 408 | 303 | 0 | 0 | 0 | 0 | 1292 | 9 |
| 28 | elder_sentry.json | 406 | 316 | 0 | 0 | 0 | 0 | 1173 | 8 |
| 29 | bubble_puffer.json | 374 | 180 | 0 | 4 | 0 | 0 | 3615 | 2 |
| 30 | armor/tide_shell/chestplate_legend.json | 367 | 260 | 1 | 8 | 0 | 0 | 1618 | 0 |
| 31 | armor/thunder_wing/chestplate_rare.json | 359 | 132 | 0 | 44 | 0 | 0 | 1911 | 0 |
| 32 | bone_sentinel.json | 353 | 284 | 0 | 0 | 0 | 0 | 858 | 7 |
| 33 | whalebone_captain.json | 341 | 192 | 0 | 0 | 0 | 0 | 1975 | 11 |
| 34 | armor/thunder_wing/chestplate.json | 324 | 120 | 0 | 36 | 0 | 0 | 1933 | 0 |
| 35 | armor/stone_whale/chestplate_legend.json | 322 | 210 | 4 | 6 | 0 | 0 | 1647 | 0 |
| 36 | armor/tide_shell/chestplate_rare.json | 319 | 220 | 0 | 0 | 0 | 0 | 1986 | 0 |
| 37 | armor/thunder_wing/helmet_legend.json | 311 | 54 | 0 | 30 | 0 | 2 | 2549 | 0 |
| 38 | armor/thunder_wing/helmet_rare.json | 311 | 54 | 0 | 30 | 0 | 2 | 2549 | 0 |
| 39 | storyteller.json | 311 | 172 | 0 | 0 | 0 | 0 | 2086 | 7 |
| 40 | coral_thrall.json | 306 | 258 | 0 | 0 | 0 | 0 | 463 | 5 |
| 41 | ash_thrall.json | 302 | 152 | 0 | 0 | 0 | 0 | 1676 | 15 |
| 42 | stone_whale_mid.json | 301 | 266 | 0 | 0 | 0 | 0 | 92 | 7 |
| 43 | artisan.json | 296 | 218 | 0 | 20 | 0 | 0 | 374 | 0 |
| 44 | whale_chaser.json | 295 | 250 | 0 | 0 | 0 | 0 | 400 | 5 |
| 45 | armor/ember/chestplate_legend.json | 292 | 184 | 1 | 8 | 0 | 0 | 1624 | 0 |
| 46 | armor/stone_whale/chestplate_rare.json | 288 | 188 | 0 | 0 | 0 | 0 | 2004 | 0 |
| 47 | items/cinder_lamp/common.json | 288 | 8 | 44 | 17 | 4 | 0 | 1943 | 0 |
| 48 | items/cinder_lamp/legend.json | 288 | 8 | 44 | 17 | 4 | 0 | 1943 | 0 |
| 49 | items/cinder_lamp/rare.json | 288 | 8 | 44 | 17 | 4 | 0 | 1943 | 0 |
| 50 | armor/tide_shell/chestplate.json | 283 | 184 | 0 | 0 | 0 | 0 | 1995 | 0 |
| 51 | armor/thunder_wing/helmet.json | 280 | 54 | 0 | 20 | 0 | 2 | 2534 | 0 |
| 52 | armor/stone_whale/chestplate.json | 268 | 168 | 0 | 0 | 0 | 0 | 2011 | 0 |
| 53 | ember_moth.json | 265 | 55 | 0 | 60 | 0 | 0 | 313 | 3 |
| 54 | armor/ember/chestplate_rare.json | 262 | 163 | 0 | 0 | 0 | 0 | 1990 | 0 |
| 55 | armor/ember/chestplate.json | 245 | 145 | 0 | 0 | 0 | 0 | 2005 | 0 |
| 56 | ember_titan_far.json | 242 | 94 | 0 | 14 | 1 | 0 | 1658 | 8 |
| 57 | storm_heart_p3.json | 241 | 227 | 0 | 0 | 0 | 0 | 299 | 0 |
| 58 | rock_shell_beetle.json | 234 | 98 | 0 | 0 | 0 | 0 | 1937 | 8 |
| 59 | armor/tide_shell/helmet_legend.json | 228 | 102 | 0 | 0 | 0 | 0 | 2533 | 0 |
| 60 | armor/tide_shell/helmet_rare.json | 228 | 102 | 0 | 0 | 0 | 0 | 2533 | 0 |
| 61 | hooked_glaive.json | 220 | 77 | 0 | 0 | 0 | 0 | 2872 | 0 |
| 62 | armor/tide_shell/helmet.json | 204 | 78 | 0 | 0 | 0 | 0 | 2535 | 0 |
| 63 | armor/stone_whale/helmet_legend.json | 201 | 74 | 0 | 0 | 0 | 0 | 2545 | 0 |
| 64 | armor/stone_whale/helmet_rare.json | 201 | 74 | 0 | 0 | 0 | 0 | 2545 | 0 |
| 65 | armor/thunder_wing/leggings.json | 200 | 60 | 0 | 8 | 0 | 2 | 1527 | 0 |
| 66 | armor/thunder_wing/leggings_legend.json | 200 | 60 | 0 | 8 | 0 | 2 | 1527 | 0 |
| 67 | armor/thunder_wing/leggings_rare.json | 200 | 60 | 0 | 8 | 0 | 2 | 1527 | 0 |
| 68 | static_moth.json | 196 | 188 | 0 | 0 | 0 | 0 | 62 | 1 |
| 69 | armor/ember/helmet_legend.json | 193 | 66 | 0 | 0 | 0 | 0 | 2552 | 0 |
| 70 | armor/ember/helmet_rare.json | 193 | 66 | 0 | 0 | 0 | 0 | 2552 | 0 |
| 71 | armor/stone_whale/helmet.json | 192 | 66 | 0 | 0 | 0 | 0 | 2521 | 0 |
| 72 | items/thunder_wing_bow/legend_pull0.json | 190 | 80 | 0 | 28 | 0 | 1 | 123 | 0 |
| 73 | armor/ember/helmet.json | 186 | 60 | 0 | 0 | 0 | 0 | 2539 | 0 |
| 74 | storm_heart_p2.json | 184 | 181 | 0 | 0 | 0 | 0 | 65 | 0 |
| 75 | reef_scuttler.json | 177 | 150 | 0 | 0 | 0 | 0 | 154 | 4 |
| 76 | tide_heart_p2.json | 177 | 165 | 0 | 0 | 0 | 0 | 252 | 0 |
| 77 | items/thunder_wing_bow/rare_pull0.json | 170 | 80 | 0 | 22 | 0 | 1 | 95 | 0 |
| 78 | armor/whale_road/chestplate.json | 169 | 68 | 0 | 4 | 0 | 0 | 1788 | 0 |
| 79 | thunder_wing_far.json | 169 | 130 | 0 | 0 | 0 | 0 | 89 | 7 |
| 80 | tide_heart_p3.json | 169 | 157 | 0 | 0 | 0 | 0 | 257 | 0 |
| 81 | items/thunder_wing_bow/legend_pull2.json | 164 | 80 | 0 | 26 | 0 | 0 | 120 | 0 |
| 82 | bone_hauler.json | 160 | 81 | 0 | 0 | 0 | 0 | 683 | 9 |
| 83 | items/thunder_wing_bow/common_pull0.json | 158 | 80 | 0 | 18 | 0 | 1 | 97 | 0 |
| 84 | storm_heart_p1.json | 150 | 137 | 0 | 0 | 0 | 0 | 268 | 0 |
| 85 | armor/whale_road/helmet.json | 146 | 26 | 0 | 0 | 0 | 0 | 2404 | 0 |
| 86 | armor/ember/leggings.json | 144 | 68 | 0 | 0 | 0 | 0 | 1525 | 0 |
| 87 | armor/ember/leggings_legend.json | 144 | 68 | 0 | 0 | 0 | 0 | 1525 | 0 |
| 88 | armor/ember/leggings_rare.json | 144 | 68 | 0 | 0 | 0 | 0 | 1525 | 0 |
| 89 | armor/tide_shell/leggings.json | 144 | 68 | 0 | 0 | 0 | 0 | 1531 | 0 |
| 90 | armor/tide_shell/leggings_legend.json | 144 | 68 | 0 | 0 | 0 | 0 | 1531 | 0 |
| 91 | armor/tide_shell/leggings_rare.json | 144 | 68 | 0 | 0 | 0 | 0 | 1531 | 0 |
| 92 | armor/stone_whale/leggings.json | 143 | 68 | 0 | 0 | 0 | 0 | 1507 | 0 |
| 93 | armor/stone_whale/leggings_legend.json | 143 | 68 | 0 | 0 | 0 | 0 | 1507 | 0 |
| 94 | armor/stone_whale/leggings_rare.json | 143 | 68 | 0 | 0 | 0 | 0 | 1507 | 0 |
| 95 | items/thunder_wing_bow/rare_pull2.json | 143 | 80 | 0 | 20 | 0 | 0 | 79 | 0 |
| 96 | items/warden_glaive/legend.json | 141 | 64 | 0 | 12 | 0 | 2 | 37 | 0 |
| 97 | items/warden_glaive/rare.json | 141 | 64 | 0 | 12 | 0 | 2 | 29 | 0 |
| 98 | items/thunder_wing_bow/legend_pull1.json | 138 | 80 | 0 | 18 | 0 | 0 | 99 | 0 |
| 99 | items/warden_glaive/common.json | 134 | 56 | 0 | 12 | 0 | 2 | 42 | 0 |
| 100 | items/thunder_wing_bow/common_pull2.json | 132 | 80 | 0 | 16 | 0 | 0 | 90 | 0 |
| 101 | stone_whale_far.json | 130 | 80 | 0 | 0 | 0 | 0 | 492 | 6 |
| 102 | armor/thunder_wing/boots.json | 127 | 52 | 0 | 4 | 0 | 0 | 1270 | 0 |
| 103 | armor/thunder_wing/boots_legend.json | 127 | 52 | 0 | 4 | 0 | 0 | 1270 | 0 |
| 104 | armor/thunder_wing/boots_rare.json | 127 | 52 | 0 | 4 | 0 | 0 | 1270 | 0 |
| 105 | armor/stone_whale/boots.json | 123 | 60 | 0 | 0 | 0 | 0 | 1260 | 0 |
| 106 | armor/stone_whale/boots_legend.json | 123 | 60 | 0 | 0 | 0 | 0 | 1260 | 0 |
| 107 | armor/stone_whale/boots_rare.json | 123 | 60 | 0 | 0 | 0 | 0 | 1260 | 0 |
| 108 | armor/tide_shell/boots.json | 123 | 60 | 0 | 0 | 0 | 0 | 1263 | 0 |
| 109 | armor/tide_shell/boots_legend.json | 123 | 60 | 0 | 0 | 0 | 0 | 1263 | 0 |
| 110 | armor/tide_shell/boots_rare.json | 123 | 60 | 0 | 0 | 0 | 0 | 1263 | 0 |
| 111 | armor/whale_road/leggings.json | 119 | 48 | 0 | 0 | 0 | 0 | 1438 | 0 |
| 112 | armor/ember/boots.json | 118 | 54 | 0 | 0 | 0 | 0 | 1292 | 0 |
| 113 | armor/ember/boots_legend.json | 118 | 54 | 0 | 0 | 0 | 0 | 1292 | 0 |
| 114 | armor/ember/boots_rare.json | 118 | 54 | 0 | 0 | 0 | 0 | 1292 | 0 |
| 115 | items/thunder_wing_bow/legend.json | 118 | 60 | 0 | 18 | 0 | 0 | 80 | 0 |
| 116 | items/thunder_wing_bow/rare_pull1.json | 118 | 80 | 0 | 12 | 0 | 0 | 58 | 0 |
| 117 | spine_hare.json | 111 | 71 | 0 | 0 | 0 | 0 | 114 | 7 |
| 118 | items/raiju_claw/legend.json | 108 | 54 | 0 | 18 | 0 | 0 | 0 | 0 |
| 119 | items/thunder_wing_bow/common_pull1.json | 106 | 80 | 0 | 8 | 0 | 0 | 58 | 0 |
| 120 | armor/whale_road/boots.json | 102 | 40 | 0 | 0 | 0 | 0 | 1247 | 0 |
| 121 | items/thunder_wing_bow/rare.json | 98 | 60 | 0 | 12 | 0 | 0 | 41 | 0 |
| 122 | items/ember_war_axe/legend.json | 97 | 70 | 0 | 4 | 0 | 0 | 303 | 0 |
| 123 | watcher.json | 96 | 62 | 0 | 0 | 0 | 0 | 269 | 5 |
| 124 | smith_hammer.json | 95 | 74 | 0 | 0 | 0 | 0 | 431 | 0 |
| 125 | bone_gull.json | 92 | 92 | 0 | 0 | 0 | 0 | 2 | 0 |
| 126 | tide_shell_far.json | 91 | 74 | 0 | 0 | 0 | 0 | 343 | 0 |
| 127 | items/thunder_wing_bow/common.json | 86 | 60 | 0 | 8 | 0 | 0 | 46 | 0 |
| 128 | items/coral_shield/legend.json | 83 | 60 | 0 | 6 | 0 | 0 | 109 | 0 |
| 129 | items/whalebone_greatsword/legend.json | 83 | 52 | 0 | 10 | 0 | 0 | 39 | 0 |
| 130 | items/ember_war_axe/rare.json | 81 | 66 | 0 | 0 | 0 | 0 | 303 | 0 |
| 131 | items/ember_war_axe/common.json | 75 | 62 | 0 | 0 | 0 | 0 | 277 | 0 |
| 132 | items/coral_harpoon/legend.json | 74 | 74 | 0 | 0 | 0 | 0 | 15 | 0 |
| 133 | items/smith_hammer/legend.json | 74 | 26 | 0 | 8 | 0 | 1 | 87 | 0 |
| 134 | items/tide_shell_trident/legend.json | 74 | 74 | 0 | 0 | 0 | 0 | 10 | 0 |
| 135 | smith_tongs.json | 72 | 40 | 0 | 0 | 0 | 0 | 642 | 0 |
| 136 | items/whalebone_greatsword/rare.json | 71 | 52 | 0 | 6 | 0 | 0 | 23 | 0 |
| 137 | items/captain_pike/legend.json | 64 | 44 | 0 | 6 | 0 | 0 | 42 | 0 |
| 138 | items/coral_shield/common.json | 63 | 40 | 0 | 6 | 0 | 0 | 110 | 0 |
| 139 | items/coral_shield/rare.json | 63 | 40 | 0 | 6 | 0 | 0 | 103 | 0 |
| 140 | items/ember_mantle/common.json | 60 | 28 | 0 | 0 | 0 | 0 | 648 | 0 |
| 141 | items/coral_harpoon/rare.json | 59 | 58 | 0 | 0 | 0 | 0 | 20 | 0 |
| 142 | items/ember_mantle/legend.json | 59 | 28 | 0 | 0 | 0 | 0 | 636 | 0 |
| 143 | items/ember_mantle/rare.json | 59 | 28 | 0 | 0 | 0 | 0 | 636 | 0 |
| 144 | items/thrall_poker/legend.json | 58 | 58 | 0 | 0 | 0 | 0 | 3 | 0 |
| 145 | items/tide_shell_trident/rare.json | 58 | 58 | 0 | 0 | 0 | 0 | 10 | 0 |
| 146 | rib_archer_bow.json | 58 | 44 | 0 | 0 | 0 | 0 | 85 | 2 |
| 147 | items/coral_harpoon/common.json | 54 | 54 | 0 | 0 | 0 | 0 | 13 | 0 |
| 148 | items/raiju_claw/rare.json | 54 | 54 | 0 | 0 | 0 | 0 | 1 | 0 |
| 149 | items/thrall_poker/rare.json | 54 | 54 | 0 | 0 | 0 | 0 | 4 | 0 |
| 150 | items/tide_watching_shell/common.json | 53 | 4 | 2 | 12 | 0 | 0 | 149 | 0 |
| 151 | items/tide_watching_shell/legend.json | 53 | 4 | 2 | 12 | 0 | 0 | 147 | 0 |
| 152 | items/tide_watching_shell/rare.json | 53 | 4 | 2 | 12 | 0 | 0 | 147 | 0 |
| 153 | ash_poker.json | 51 | 46 | 0 | 0 | 0 | 0 | 112 | 0 |
| 154 | items/ember_horn/common.json | 51 | 30 | 0 | 0 | 0 | 0 | 437 | 0 |
| 155 | items/ember_horn/legend.json | 51 | 30 | 0 | 0 | 0 | 0 | 428 | 0 |
| 156 | items/ember_horn/rare.json | 51 | 30 | 0 | 0 | 0 | 0 | 428 | 0 |
| 157 | items/tide_shell_trident/common.json | 50 | 50 | 0 | 0 | 0 | 0 | 18 | 0 |
| 158 | supplicant_staff.json | 50 | 36 | 4 | 0 | 0 | 0 | 59 | 0 |
| 159 | items/waterway_fang/legend.json | 47 | 46 | 0 | 0 | 0 | 0 | 39 | 0 |
| 160 | items/raiju_claw/common.json | 46 | 46 | 0 | 0 | 0 | 0 | 5 | 0 |
| 161 | items/thrall_poker/common.json | 46 | 46 | 0 | 0 | 0 | 0 | 4 | 0 |
| 162 | items/tidecaller_claw/legend.json | 46 | 46 | 0 | 0 | 0 | 0 | 7 | 0 |
| 163 | items/whalebone_greatsword/common.json | 46 | 46 | 0 | 0 | 0 | 0 | 7 | 0 |
| 164 | items/captain_pike/rare.json | 44 | 44 | 0 | 0 | 0 | 0 | 18 | 0 |
| 165 | items/waterway_fang/rare.json | 42 | 42 | 0 | 0 | 0 | 0 | 17 | 0 |
| 166 | coral_spear.json | 41 | 40 | 0 | 0 | 0 | 0 | 23 | 0 |
| 167 | coral_spear_statue.json | 41 | 40 | 0 | 0 | 0 | 0 | 23 | 0 |
| 168 | items/captain_pike/common.json | 41 | 40 | 0 | 0 | 0 | 0 | 24 | 0 |
| 169 | items/waterway_fang/common.json | 39 | 38 | 0 | 0 | 0 | 0 | 22 | 0 |
| 170 | items/tidecaller_claw/rare.json | 38 | 38 | 0 | 0 | 0 | 0 | 7 | 0 |
| 171 | items/storm_eye_feather/legend.json | 34 | 0 | 0 | 10 | 0 | 0 | 80 | 0 |
| 172 | items/tidecaller_claw/common.json | 30 | 30 | 0 | 0 | 0 | 0 | 4 | 0 |
| 173 | items/storm_charm/common.json | 27 | 0 | 1 | 8 | 0 | 0 | 9 | 0 |
| 174 | items/storm_charm/legend.json | 27 | 0 | 1 | 8 | 0 | 0 | 9 | 0 |
| 175 | items/storm_charm/rare.json | 27 | 0 | 1 | 8 | 0 | 0 | 9 | 0 |
| 176 | items/smith_hammer/rare.json | 26 | 26 | 0 | 0 | 0 | 0 | 2 | 0 |
| 177 | items/smith_hammer/common.json | 22 | 22 | 0 | 0 | 0 | 0 | 5 | 0 |
| 178 | bone_sentinel_shield.json | 17 | 16 | 0 | 0 | 0 | 0 | 27 | 0 |
| 179 | items/kindling_charm/common.json | 15 | 0 | 0 | 4 | 0 | 0 | 68 | 0 |
| 180 | items/kindling_charm/legend.json | 15 | 0 | 0 | 4 | 0 | 0 | 66 | 0 |
| 181 | items/kindling_charm/rare.json | 15 | 0 | 0 | 4 | 0 | 0 | 66 | 0 |
| 182 | items/storm_eye_feather/common.json | 12 | 0 | 0 | 4 | 0 | 0 | 13 | 0 |
| 183 | items/storm_eye_feather/rare.json | 12 | 0 | 0 | 4 | 0 | 0 | 11 | 0 |
| 184 | items/tide_pearl/common.json | 12 | 12 | 0 | 0 | 0 | 0 | 9 | 0 |
| 185 | items/tide_pearl/legend.json | 12 | 12 | 0 | 0 | 0 | 0 | 3 | 0 |
| 186 | items/tide_pearl/rare.json | 12 | 12 | 0 | 0 | 0 | 0 | 3 | 0 |
| 187 | items/tide_chime_charm/common.json | 8 | 8 | 0 | 0 | 0 | 0 | 0 | 0 |
| 188 | items/tide_chime_charm/legend.json | 8 | 8 | 0 | 0 | 0 | 0 | 0 | 0 |
| 189 | items/tide_chime_charm/rare.json | 8 | 8 | 0 | 0 | 0 | 0 | 0 | 0 |
| 190 | captain_pike.json | 4 | 4 | 0 | 0 | 0 | 0 | 8 | 0 |
| 191 | items/gale_feather/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 192 | items/gale_feather/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 193 | items/gale_feather/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 194 | items/storm_anklet/common.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 195 | items/storm_anklet/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 196 | items/storm_anklet/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |

## 骨ごとの詳細 (上位 25 個)

### thunder_wing.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 480 | 514 | 127 | 0 | 0 | 0 | 0 | 650 | - |
| wing_l | 456 | 328 | 73 | 0 | 0 | 0 | 0 | 6 | shallow(埋3%/深11.8) |
| wing_r | 456 | 328 | 73 | 0 | 0 | 0 | 0 | 7 | shallow(埋3%/深10.2) |
| ruins | 294 | 108 | 23 | 0 | 0 | 0 | 0 | 6 | ok(埋5%/深5.4) |
| head | 432 | 96 | 16 | 0 | 0 | 0 | 0 | 0 | ok(埋11%/深10.8) |
| leg_l | 88 | 64 | 14 | 0 | 0 | 0 | 0 | 10 | pivot-out(埋10%/深11.1) |
| leg_r | 88 | 64 | 14 | 0 | 0 | 0 | 0 | 9 | pivot-out(埋11%/深10.1) |
| hand_l | 346 | 62 | 15 | 0 | 0 | 0 | 0 | 4 | pivot-out(埋10%/深5.9) |
| hand_r | 346 | 62 | 15 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋11%/深5.5) |
| tail | 198 | 44 | 11 | 0 | 0 | 0 | 0 | 5 | detach(埋0%/深0.0) |
| gate | 160 | 32 | 8 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋14%/深4.2) |
| crest | 84 | 28 | 7 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋16%/深3.3) |
| neck | 100 | 20 | 2 | 0 | 0 | 0 | 0 | 3 | pivot-out(埋10%/深18.6) |

### ember_titan.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| ridge | 2108 | 744 | 131 | 0 | 26 | 0 | 0 | 820 | ok(埋16%/深6.4) |
| body | 1268 | 244 | 39 | 0 | 0 | 0 | 0 | 5 | - |
| head | 740 | 176 | 36 | 0 | 0 | 0 | 0 | 161 | shallow(埋4%/深18.2) |
| tail | 542 | 110 | 22 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深1.4) |
| leg_fl | 131 | 51 | 12 | 0 | 0 | 0 | 0 | 221 | ok(埋5%/深16.9) |
| leg_fr | 131 | 51 | 12 | 0 | 0 | 0 | 0 | 202 | ok(埋5%/深16.9) |
| leg_rl | 131 | 51 | 12 | 0 | 0 | 0 | 0 | 213 | shallow(埋2%/深8.4) |
| leg_rr | 131 | 51 | 12 | 0 | 0 | 0 | 0 | 204 | shallow(埋2%/深8.8) |
| jaw | 148 | 27 | 9 | 0 | 0 | 1 | 0 | 0 | pivot-out(埋12%/深8.9) |
| smoke_0 | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| smoke_1 | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| smoke_2 | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| smoke_3 | 28 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |

### tide_shell.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| coral | 864 | 448 | 89 | 0 | 0 | 0 | 0 | 217 | ok(埋17%/深5.7) |
| ruins | 766 | 296 | 66 | 0 | 0 | 0 | 0 | 410 | ok(埋11%/深3.6) |
| kelp | 160 | 120 | 10 | 0 | 0 | 0 | 0 | 46 | ok(埋43%/深4.4) |
| head | 394 | 82 | 18 | 0 | 0 | 0 | 0 | 9 | ok(埋7%/深9.6) |
| gate | 70 | 28 | 7 | 0 | 0 | 0 | 0 | 15 | ok(埋24%/深8.5) |
| fin_front_left | 52 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋35%/深14.0) |
| fin_front_right | 52 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋35%/深13.6) |
| fin_rear_left | 40 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋34%/深10.0) |
| fin_rear_right | 40 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋36%/深10.0) |
| tail | 4 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋32%/深5.8) |
| body | 2112 | 0 | 0 | 0 | 96 | 0 | 0 | 830 | - |

### stone_whale.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 1655 | 481 | 80 | 0 | 0 | 0 | 0 | 21 | - |
| head | 963 | 369 | 52 | 0 | 0 | 0 | 0 | 41 | shallow(埋3%/深23.0) |
| jaw | 449 | 217 | 33 | 0 | 4 | 0 | 0 | 16 | pivot-out(埋7%/深14.3) |
| tail | 644 | 104 | 12 | 0 | 0 | 0 | 0 | 26 | pivot-out(埋0%/深2.5) |
| spine | 56 | 56 | 14 | 0 | 0 | 0 | 0 | 0 | ok(埋35%/深3.3) |
| ruins | 272 | 46 | 9 | 0 | 0 | 0 | 0 | 1 | ok(埋19%/深9.7) |
| chain | 40 | 16 | 4 | 0 | 0 | 0 | 0 | 6 | pivot-out(埋2%/深1.9) |
| fin_left | 50 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋6%/深4.8) |
| fin_right | 50 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋6%/深4.8) |
| fluke | 176 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |

### feather_warden.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 169 | 91 | 19 | 0 | 0 | 0 | 0 | 491 | - |
| cloak_l | 32 | 48 | 8 | 0 | 0 | 0 | 0 | 2802 | pivot-out(埋7%/深0.9) |
| cloak_r | 32 | 48 | 8 | 0 | 0 | 0 | 0 | 2810 | pivot-out(埋12%/深3.2) |
| head | 136 | 44 | 10 | 0 | 0 | 0 | 0 | 1079 | detach(埋0%/深0.0) |
| forearm_l | 51 | 37 | 8 | 0 | 0 | 0 | 0 | 522 | shallow(埋2%/深0.4) |
| leg_r | 42 | 32 | 8 | 0 | 0 | 0 | 0 | 63 | pivot-out(埋9%/深1.7) |
| leg_l | 42 | 32 | 8 | 0 | 0 | 0 | 0 | 65 | pivot-out(埋6%/深2.3) |
| arm_r | 32 | 28 | 5 | 0 | 0 | 0 | 0 | 1009 | pivot-out(埋3%/深0.9) |
| forearm_r | 42 | 28 | 5 | 0 | 0 | 0 | 0 | 466 | pivot-out(埋1%/深0.8) |
| arm_l | 32 | 28 | 5 | 0 | 0 | 0 | 0 | 1001 | pivot-out(埋3%/深0.8) |

### stone_whale_heart_p1.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| tendrils | 126 | 54 | 18 | 0 | 0 | 0 | 0 | 27 | - |
| heart | 94 | 18 | 4 | 0 | 0 | 0 | 0 | 26 | - |
| seam_0 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 19 | - |
| seam_1 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| seam_2 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| seam_3 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| seam_4 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| seam_5 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| seam_6 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| seam_7 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| crown | 102 | 8 | 1 | 0 | 0 | 0 | 0 | 1945 | - |
| base | 54 | 8 | 1 | 0 | 0 | 0 | 0 | 2340 | - |
| plate_0 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 4 | - |
| plate_1 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 2 | - |
| plate_2 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 8 | - |
| plate_3 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 4 | - |
| plate_4 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 9 | - |
| plate_5 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 19 | - |
| plate_6 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 5 | - |
| plate_7 | 96 | 8 | 2 | 0 | 16 | 0 | 0 | 4 | - |

### ash_smith.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| head | 206 | 58 | 12 | 0 | 0 | 0 | 0 | 911 | shallow(埋4%/深3.0) |
| body | 198 | 56 | 9 | 0 | 0 | 0 | 0 | 1640 | - |
| arm_r | 68 | 48 | 8 | 0 | 0 | 0 | 0 | 353 | pivot-out(埋4%/深2.8) |
| arm_l | 68 | 48 | 8 | 0 | 0 | 0 | 0 | 362 | pivot-out(埋4%/深2.6) |
| apron | 117 | 45 | 9 | 6 | 0 | 0 | 0 | 458 | ok(埋5%/深1.3) |
| forearm_r | 132 | 42 | 7 | 0 | 0 | 0 | 0 | 969 | pivot-out(埋0%/深0.0) |
| forearm_l | 132 | 42 | 7 | 0 | 0 | 0 | 0 | 968 | pivot-out(埋0%/深0.0) |
| belly | 76 | 32 | 4 | 0 | 0 | 0 | 0 | 298 | ok(埋4%/深4.7) |
| leg_r | 140 | 24 | 4 | 0 | 0 | 0 | 0 | 447 | - |
| leg_l | 140 | 24 | 4 | 0 | 0 | 0 | 0 | 457 | - |
| bellows_l | 120 | 18 | 3 | 0 | 0 | 0 | 0 | 34 | pivot-out(埋3%/深3.4) |
| bellows_r | 120 | 18 | 3 | 0 | 0 | 0 | 0 | 36 | pivot-out(埋7%/深13.4) |

### stone_whale_heart_p2.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| tendrils | 126 | 54 | 18 | 0 | 0 | 0 | 0 | 25 | - |
| spikes | 96 | 32 | 8 | 0 | 0 | 0 | 0 | 82 | - |
| heart_veins | 78 | 24 | 8 | 0 | 0 | 0 | 0 | 16 | - |
| heart | 194 | 18 | 4 | 0 | 0 | 0 | 0 | 15 | - |
| plate_1 | 92 | 16 | 4 | 0 | 16 | 0 | 0 | 26 | - |
| plate_2 | 92 | 16 | 4 | 0 | 16 | 0 | 0 | 18 | - |
| plate_3 | 92 | 16 | 4 | 0 | 16 | 0 | 0 | 13 | - |
| plate_6 | 92 | 16 | 4 | 0 | 20 | 0 | 0 | 6 | - |
| crack_0 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 3 | - |
| crack_1 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 1 | - |
| crack_2 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 2 | - |
| crack_3 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 2 | - |
| crack_7 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 1 | - |
| plate_0 | 92 | 14 | 3 | 0 | 16 | 0 | 0 | 3 | - |
| plate_7 | 92 | 14 | 3 | 0 | 18 | 0 | 0 | 10 | - |
| crown | 102 | 8 | 1 | 0 | 0 | 0 | 0 | 1321 | - |
| base | 54 | 8 | 1 | 0 | 0 | 0 | 0 | 2186 | - |

### tide_beckoner.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| claw_big | 397 | 103 | 23 | 0 | 0 | 0 | 0 | 686 | shallow(埋1%/深4.9) |
| body | 640 | 80 | 14 | 0 | 96 | 0 | 0 | 1093 | - |
| claw_small | 114 | 44 | 9 | 0 | 0 | 0 | 0 | 282 | shallow(埋3%/深4.4) |
| claw_big_pincer | 42 | 28 | 8 | 0 | 0 | 0 | 0 | 59 | ok(埋30%/深10.2) |
| eyes | 56 | 16 | 4 | 0 | 0 | 0 | 0 | 38 | detach(埋1%/深2.1) |
| leg_r1 | 40 | 12 | 3 | 0 | 0 | 0 | 0 | 42 | pivot-out(埋10%/深4.0) |
| leg_r2 | 40 | 12 | 3 | 0 | 0 | 0 | 0 | 42 | pivot-out(埋3%/深5.5) |
| leg_r3 | 40 | 12 | 3 | 0 | 0 | 0 | 0 | 51 | pivot-out(埋6%/深3.6) |
| leg_l1 | 40 | 12 | 3 | 0 | 0 | 0 | 0 | 45 | pivot-out(埋9%/深3.5) |
| leg_l2 | 40 | 12 | 3 | 0 | 0 | 0 | 0 | 42 | pivot-out(埋3%/深6.8) |
| leg_l3 | 40 | 12 | 3 | 0 | 0 | 0 | 0 | 49 | pivot-out(埋5%/深2.4) |

### stone_whale_heart_p3.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| tendrils | 126 | 54 | 18 | 0 | 0 | 0 | 0 | 28 | - |
| heart_veins | 96 | 30 | 10 | 0 | 0 | 0 | 0 | 73 | - |
| heart | 238 | 18 | 4 | 0 | 0 | 0 | 0 | 93 | - |
| bowl_seam_0 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 9 | - |
| bowl_seam_1 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| bowl_seam_2 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| bowl_seam_3 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| bowl_seam_4 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| bowl_seam_5 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| bowl_seam_6 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| bowl_seam_7 | 24 | 16 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| chains | 154 | 12 | 2 | 0 | 0 | 0 | 0 | 7 | - |
| bowl_0 | 46 | 12 | 3 | 0 | 8 | 0 | 0 | 26 | - |
| bowl_1 | 46 | 12 | 3 | 0 | 8 | 0 | 0 | 3 | - |
| bowl_2 | 46 | 12 | 3 | 0 | 8 | 0 | 0 | 1 | - |
| bowl_3 | 46 | 12 | 3 | 0 | 8 | 0 | 0 | 0 | - |
| bowl_4 | 46 | 12 | 4 | 0 | 11 | 0 | 0 | 3 | - |
| bowl_5 | 46 | 12 | 3 | 0 | 8 | 0 | 0 | 1 | - |
| bowl_6 | 46 | 12 | 3 | 0 | 8 | 0 | 0 | 0 | - |
| bowl_7 | 46 | 12 | 3 | 0 | 12 | 0 | 0 | 0 | - |
| shard_0 | 44 | 12 | 3 | 0 | 0 | 0 | 0 | 0 | - |
| shard_1 | 44 | 12 | 3 | 0 | 0 | 0 | 0 | 0 | - |
| base | 54 | 8 | 1 | 0 | 0 | 0 | 0 | 2384 | - |

### ember_titan_mid.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| ridge | 378 | 134 | 33 | 0 | 18 | 0 | 0 | 551 | ok(埋13%/深7.7) |
| head | 308 | 76 | 16 | 0 | 0 | 0 | 0 | 155 | pivot-out(埋1%/深12.7) |
| body | 318 | 72 | 15 | 0 | 0 | 0 | 0 | 20 | - |
| tail | 160 | 42 | 9 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| leg_fl | 49 | 23 | 6 | 0 | 0 | 0 | 0 | 201 | ok(埋7%/深13.6) |
| leg_fr | 49 | 23 | 6 | 0 | 0 | 0 | 0 | 202 | ok(埋7%/深13.6) |
| leg_rl | 49 | 23 | 6 | 0 | 0 | 0 | 0 | 204 | shallow(埋4%/深9.3) |
| leg_rr | 49 | 23 | 6 | 0 | 0 | 0 | 0 | 209 | pivot-out(埋3%/深7.4) |
| jaw | 56 | 12 | 4 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋6%/深7.0) |
| smoke_0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| smoke_1 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| smoke_2 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |
| smoke_3 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |

### cinder_roller.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| seg8 | 52 | 40 | 6 | 0 | 0 | 0 | 0 | 269 | - |
| seg0 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 281 | - |
| seg1 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 176 | - |
| seg2 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 151 | - |
| seg3 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 158 | - |
| seg4 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 145 | - |
| seg5 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 137 | - |
| seg6 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 111 | - |
| seg7 | 40 | 30 | 5 | 0 | 0 | 0 | 0 | 121 | - |
| uropods | 18 | 6 | 2 | 0 | 0 | 0 | 0 | 19 | shallow(埋2%/深1.1) |
| mem0 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 6 | pivot-out(埋17%/深0.3) |
| mem1 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 4 | pivot-out(埋25%/深0.4) |
| mem2 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 8 | pivot-out(埋25%/深0.4) |
| mem3 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋25%/深0.5) |
| mem4 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋25%/深0.5) |
| mem5 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋17%/深0.5) |
| mem6 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋17%/深0.4) |
| mem7 | 2 | 4 | 1 | 0 | 0 | 0 | 0 | 2 | pivot-out(埋8%/深0.2) |
| antenna_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 2 | pivot-out(埋5%/深0.6) |
| antenna_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋2%/深0.2) |
| leg1_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋16%/深1.1) |
| leg1_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋32%/深1.4) |
| leg2_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋19%/深0.9) |
| leg2_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋32%/深1.5) |
| leg3_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋23%/深1.2) |
| leg3_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋33%/深1.5) |
| leg4_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋23%/深1.2) |
| leg4_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋33%/深1.5) |
| leg5_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋23%/深1.2) |
| leg5_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋33%/深1.5) |
| leg6_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋19%/深0.9) |
| leg6_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋32%/深1.5) |
| leg7_r | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋16%/深1.1) |
| leg7_l | 9 | 3 | 1 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋30%/深1.3) |

### rib_archer.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| spine | 116 | 106 | 15 | 0 | 0 | 0 | 0 | 1959 | - |
| skull | 190 | 92 | 14 | 0 | 0 | 0 | 0 | 1053 | pivot-out(埋0%/深0.0) |
| forearm_r | 48 | 30 | 8 | 0 | 0 | 0 | 0 | 35 | shallow(埋1%/深0.1) |
| leg_l | 59 | 29 | 7 | 0 | 0 | 0 | 0 | 150 | pivot-out(埋0%/深0.0) |
| leg_r | 59 | 29 | 7 | 0 | 0 | 0 | 0 | 174 | pivot-out(埋0%/深0.0) |
| forearm_l | 45 | 27 | 7 | 0 | 0 | 0 | 0 | 34 | shallow(埋1%/深0.1) |
| arm_l | 44 | 10 | 2 | 0 | 0 | 0 | 0 | 9 | pivot-out(埋11%/深0.7) |
| arm_r | 44 | 10 | 2 | 0 | 0 | 0 | 0 | 8 | pivot-out(埋13%/深0.9) |

### moss_crawler.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| head | 99 | 41 | 10 | 0 | 0 | 0 | 0 | 74 | pivot-out(埋0%/深0.8) |
| seg2 | 56 | 36 | 7 | 0 | 0 | 0 | 0 | 150 | pivot-out(埋0%/深0.0) |
| seg3 | 56 | 36 | 7 | 0 | 0 | 0 | 0 | 107 | pivot-out(埋4%/深0.2) |
| seg4 | 56 | 36 | 7 | 0 | 0 | 0 | 0 | 98 | pivot-out(埋3%/深0.6) |
| seg5 | 56 | 36 | 7 | 0 | 0 | 0 | 0 | 102 | pivot-out(埋5%/深0.7) |
| teeth | 15 | 15 | 5 | 0 | 0 | 0 | 0 | 1 | pivot-out(埋34%/深0.5) |
| ant_l | 21 | 15 | 5 | 0 | 0 | 0 | 0 | 1 | ok(埋5%/深0.9) |
| ant_r | 21 | 15 | 5 | 0 | 0 | 0 | 0 | 0 | ok(埋5%/深0.7) |
| seg1 | 36 | 14 | 3 | 0 | 0 | 0 | 0 | 12 | - |
| jaw | 24 | 14 | 1 | 0 | 0 | 0 | 0 | 156 | pivot-out(埋3%/深0.4) |
| tail | 15 | 9 | 3 | 0 | 0 | 0 | 0 | 2 | ok(埋13%/深1.1) |
| l1_u | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 10 | pivot-out(埋0%/深0.0) |
| l1_l | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 5 | pivot-out(埋0%/深0.0) |
| r1_u | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 6 | pivot-out(埋0%/深0.0) |
| r1_l | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 7 | pivot-out(埋5%/深0.4) |
| l2_u | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 8 | pivot-out(埋0%/深0.0) |
| l2_l | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 4 | pivot-out(埋0%/深0.0) |
| r2_u | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 7 | pivot-out(埋0%/深0.0) |
| r2_l | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 3 | pivot-out(埋5%/深0.4) |
| l3_u | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 11 | pivot-out(埋0%/深0.0) |
| l3_l | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 6 | pivot-out(埋0%/深0.0) |
| r3_u | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 7 | pivot-out(埋0%/深0.0) |
| r3_l | 9 | 9 | 3 | 0 | 0 | 0 | 0 | 5 | pivot-out(埋5%/深0.4) |
| eyes | 22 | 8 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋20%/深0.9) |
| lure_l | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋7%/深0.1) |
| lure_r | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋0%/深0.0) |

### raiju.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| mane | 231 | 77 | 17 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋17%/深3.2) |
| chest | 96 | 50 | 15 | 0 | 0 | 0 | 0 | 40 | pivot-out(埋0%/深0.0) |
| leg_bl_lo | 90 | 40 | 9 | 0 | 0 | 0 | 0 | 291 | shallow(埋2%/深2.1) |
| leg_br_lo | 90 | 40 | 9 | 0 | 0 | 0 | 0 | 295 | pivot-out(埋2%/深2.1) |
| head | 250 | 38 | 12 | 0 | 0 | 0 | 0 | 96 | pivot-out(埋5%/深4.5) |
| leg_fl_lo | 70 | 30 | 7 | 0 | 0 | 0 | 0 | 166 | shallow(埋2%/深2.6) |
| leg_fr_lo | 70 | 30 | 7 | 0 | 0 | 0 | 0 | 155 | shallow(埋2%/深2.6) |
| tail | 40 | 16 | 4 | 0 | 0 | 0 | 0 | 101 | ok(埋4%/深4.2) |
| tail_2 | 28 | 12 | 3 | 0 | 0 | 0 | 0 | 163 | shallow(埋2%/深1.3) |
| tail_3 | 20 | 12 | 3 | 0 | 0 | 0 | 0 | 91 | ok(埋4%/深2.4) |
| leg_fl | 48 | 10 | 2 | 0 | 0 | 0 | 0 | 15 | pivot-out(埋0%/深0.0) |
| leg_fr | 48 | 10 | 2 | 0 | 0 | 0 | 0 | 24 | pivot-out(埋0%/深0.0) |
| leg_bl | 44 | 10 | 2 | 0 | 0 | 0 | 0 | 54 | pivot-out(埋25%/深5.8) |
| leg_br | 44 | 10 | 2 | 0 | 0 | 0 | 0 | 61 | pivot-out(埋27%/深5.4) |
| body | 70 | 8 | 1 | 0 | 0 | 0 | 0 | 0 | - |
| jaw | 66 | 6 | 2 | 0 | 0 | 0 | 0 | 0 | ok(埋51%/深5.0) |

### tide_shell_mid.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| coral | 221 | 137 | 28 | 0 | 0 | 0 | 0 | 47 | ok(埋17%/深5.5) |
| ruins | 172 | 68 | 17 | 0 | 0 | 0 | 0 | 290 | ok(埋13%/深4.2) |
| head | 168 | 34 | 7 | 0 | 0 | 0 | 0 | 6 | ok(埋6%/深10.9) |
| gate | 60 | 24 | 6 | 0 | 0 | 0 | 0 | 15 | ok(埋29%/深8.8) |
| fin_front_left | 40 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋36%/深14.9) |
| fin_front_right | 40 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋36%/深15.5) |
| fin_rear_left | 28 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋34%/深13.0) |
| fin_rear_right | 28 | 6 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋35%/深11.3) |
| tail | 4 | 4 | 1 | 0 | 0 | 0 | 0 | 0 | ok(埋46%/深7.4) |
| body | 720 | 0 | 0 | 0 | 48 | 0 | 0 | 1314 | - |

### thunder_supplicant.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| head | 226 | 86 | 24 | 0 | 0 | 0 | 0 | 310 | pivot-out(埋0%/深0.0) |
| robe | 168 | 74 | 11 | 0 | 0 | 0 | 0 | 3530 | pivot-out(埋1%/深1.3) |
| body | 54 | 30 | 8 | 0 | 0 | 0 | 0 | 49 | - |
| forearm_r | 48 | 24 | 4 | 0 | 0 | 0 | 0 | 363 | pivot-out(埋3%/深0.7) |
| forearm_l | 48 | 24 | 4 | 0 | 0 | 0 | 0 | 363 | pivot-out(埋3%/深0.8) |
| arm_r | 28 | 8 | 2 | 0 | 0 | 0 | 0 | 29 | pivot-out(埋0%/深0.0) |
| arm_l | 28 | 8 | 2 | 0 | 0 | 0 | 0 | 27 | pivot-out(埋0%/深0.0) |

### thunder_wing_mid.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| wing_l | 120 | 84 | 20 | 0 | 0 | 0 | 0 | 16 | pivot-out(埋3%/深10.8) |
| wing_r | 120 | 84 | 20 | 0 | 0 | 0 | 0 | 17 | pivot-out(埋3%/深11.1) |
| hand_l | 225 | 49 | 12 | 0 | 0 | 0 | 0 | 5 | pivot-out(埋14%/深7.1) |
| hand_r | 225 | 49 | 12 | 0 | 0 | 0 | 0 | 3 | pivot-out(埋19%/深7.7) |
| head | 260 | 36 | 6 | 0 | 0 | 0 | 0 | 1 | ok(埋10%/深11.1) |
| ruins | 96 | 30 | 7 | 0 | 0 | 0 | 0 | 0 | shallow(埋2%/深4.8) |
| leg_l | 36 | 28 | 6 | 0 | 0 | 0 | 0 | 14 | pivot-out(埋24%/深10.7) |
| leg_r | 36 | 28 | 6 | 0 | 0 | 0 | 0 | 12 | pivot-out(埋24%/深10.4) |
| tail | 60 | 24 | 6 | 0 | 0 | 0 | 0 | 16 | detach(埋0%/深0.0) |
| neck | 48 | 16 | 2 | 0 | 0 | 0 | 0 | 8 | ok(埋11%/深12.6) |
| crest | 12 | 12 | 4 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋43%/深3.1) |
| body | 102 | 8 | 1 | 0 | 0 | 0 | 0 | 30 | - |
| gate | 96 | 8 | 2 | 0 | 0 | 0 | 0 | 3 | pivot-out(埋12%/深6.6) |

### pearl_keeper.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| shell | 773 | 145 | 27 | 0 | 16 | 0 | 0 | 952 | - |
| claw_r | 116 | 36 | 8 | 0 | 0 | 0 | 0 | 114 | - |
| head | 180 | 28 | 8 | 0 | 0 | 0 | 0 | 19 | - |
| leg_r1 | 20 | 20 | 5 | 0 | 0 | 0 | 0 | 109 | - |
| leg_r2 | 20 | 20 | 5 | 0 | 0 | 0 | 0 | 169 | - |
| leg_r3 | 20 | 20 | 5 | 0 | 0 | 0 | 0 | 148 | - |
| leg_l1 | 20 | 20 | 5 | 0 | 0 | 0 | 0 | 131 | - |
| leg_l2 | 20 | 20 | 5 | 0 | 0 | 0 | 0 | 146 | - |
| leg_l3 | 20 | 20 | 5 | 0 | 0 | 0 | 0 | 24 | - |
| claw_l | 72 | 18 | 4 | 0 | 0 | 0 | 0 | 139 | - |
| claw_r_pincer | 22 | 8 | 2 | 0 | 0 | 0 | 0 | 47 | ok(埋13%/深2.8) |
| pearl | 64 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | pivot-out(埋4%/深1.0) |

### ember_heart_p1.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| crust | 1338 | 426 | 106 | 0 | 9 | 0 | 0 | 125 | - |
| drips | 36 | 12 | 3 | 0 | 0 | 0 | 0 | 0 | - |

### ember_heart_p3.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| ash_shell | 970 | 284 | 58 | 0 | 6 | 0 | 0 | 1995 | - |
| ash_drips | 60 | 20 | 5 | 0 | 0 | 0 | 0 | 0 | - |
| heart_vein | 90 | 18 | 6 | 0 | 0 | 0 | 0 | 7 | - |
| vessel | 149 | 15 | 3 | 0 | 0 | 0 | 0 | 0 | - |
| core_ring | 88 | 8 | 2 | 0 | 0 | 0 | 0 | 0 | - |

### elder_sentinel.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 316 | 160 | 22 | 0 | 0 | 0 | 0 | 617 | - |
| head | 166 | 52 | 13 | 0 | 0 | 0 | 0 | 366 | pivot-out(埋0%/深0.0) |
| right_forearm | 69 | 27 | 7 | 0 | 0 | 0 | 0 | 61 | shallow(埋4%/深2.1) |
| leg_l | 92 | 24 | 6 | 0 | 0 | 0 | 0 | 186 | pivot-out(埋2%/深0.8) |
| leg_r | 92 | 24 | 6 | 0 | 0 | 0 | 0 | 202 | pivot-out(埋10%/深2.5) |
| left_forearm | 53 | 19 | 5 | 0 | 0 | 0 | 0 | 39 | ok(埋8%/深2.0) |
| shield | 72 | 14 | 4 | 0 | 0 | 0 | 0 | 52 | pivot-out(埋6%/深1.2) |
| left_arm | 106 | 10 | 2 | 0 | 0 | 0 | 0 | 27 | pivot-out(埋1%/深0.7) |
| right_arm | 106 | 10 | 2 | 0 | 0 | 0 | 0 | 37 | pivot-out(埋1%/深0.8) |

### ember_heart_p2.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| crust | 629 | 193 | 47 | 0 | 10 | 0 | 0 | 149 | - |
| drips | 144 | 48 | 16 | 0 | 0 | 0 | 0 | 179 | - |
| heart_vein | 150 | 30 | 10 | 0 | 0 | 0 | 0 | 32 | - |
| crust_glow | 71 | 25 | 1 | 0 | 0 | 0 | 0 | 1243 | - |
| core_ring | 144 | 24 | 6 | 0 | 0 | 0 | 0 | 0 | - |
| vessel | 149 | 15 | 3 | 0 | 0 | 0 | 0 | 0 | - |

### armor/thunder_wing/chestplate_legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| armorBody | 376 | 98 | 24 | 4 | 36 | 0 | 0 | 1394 | - |
| armorRightArm | 118 | 26 | 6 | 0 | 10 | 0 | 0 | 124 | - |
| armorLeftArm | 118 | 26 | 6 | 0 | 14 | 0 | 0 | 126 | - |

### bone_mortar.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 234 | 88 | 21 | 0 | 0 | 0 | 0 | 3618 | - |
| cannon | 100 | 64 | 12 | 0 | 0 | 0 | 0 | 399 | pivot-out(埋2%/深2.4) |
| shield | 150 | 32 | 7 | 0 | 0 | 0 | 0 | 344 | shallow(埋1%/深2.0) |
| head | 27 | 3 | 1 | 0 | 0 | 0 | 0 | 4 | pivot-out(埋0%/深0.0) |

## ブロックモデル

- `anemone_tally_dot0.json`: element 0: faces missing south,east,west,up,down / element 1: faces missing north,east,west,up,down / element 2: faces missing north,south,east,up,down / element 3: faces missing north,south,west,up,down / element 0 floats (touches no other element) / element 1 floats (touches no other element)
- `anemone_tally_dot1.json`: element 0: faces missing south,east,west,up,down / element 1: faces missing north,east,west,up,down / element 2: faces missing north,south,east,up,down / element 3: faces missing north,south,west,up,down / element 0 floats (touches no other element) / element 1 floats (touches no other element)
- `anemone_tally_dot2.json`: element 0: faces missing south,east,west,up,down / element 1: faces missing north,east,west,up,down / element 2: faces missing north,south,east,up,down / element 3: faces missing north,south,west,up,down / element 0 floats (touches no other element) / element 1 floats (touches no other element)
- `grave.json`: element 3: faces missing south,east,west,up,down / element 4: faces missing north,east,west,up,down / element 5: faces missing north,south,east,up,down / element 6: faces missing north,south,west,up,down
- `tablet_standing.json`: element 1: faces missing down
- `titan_waymark.json`: element 1: faces missing up,down
