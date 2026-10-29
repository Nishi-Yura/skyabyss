# メッシュ監査 (tools/audit/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/audit/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 582 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **7796**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 2908 / つぶれた三角形: 132
- 関節: detach 75 / shallow 52 / ok 1453 (うち pivot が親の外: 782)
- ブロックモデル: 87 個中、面の欠け・宙に浮く部品のあるもの 0

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | items/abyss_storm_hammer/legend.json | 336 | 0 | 112 | 0 | 0 | 0 | 0 | 0 |
| 2 | items/whale_dagger/rare.json | 240 | 0 | 80 | 0 | 0 | 0 | 0 | 0 |
| 3 | items/whalebone_greatsword/rare.json | 216 | 0 | 72 | 0 | 0 | 0 | 0 | 0 |
| 4 | items/storm_staff/rare.json | 192 | 0 | 64 | 0 | 0 | 0 | 0 | 0 |
| 5 | npc_shrine_priest.json | 177 | 0 | 59 | 0 | 0 | 0 | 0 | 0 |
| 6 | items/abyss_storm_sword/legend.json | 147 | 0 | 49 | 0 | 0 | 0 | 0 | 0 |
| 7 | items/storm_hammer/rare.json | 146 | 0 | 48 | 0 | 0 | 0 | 46 | 0 |
| 8 | items/abyss_storm_bell_staff/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 9 | items/abyss_storm_dagger/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 10 | items/abyss_storm_staff/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 11 | items/raiju_claw/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 12 | items/storm_bell_staff/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 13 | items/storm_dagger/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 14 | items/storm_sword/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 15 | items/warden_glaive/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 16 | items/waterway_fang/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 17 | items/whale_hammer/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 18 | items/whale_staff/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 3 | 0 |
| 19 | npc_guest_storm_elder.json | 123 | 0 | 40 | 0 | 0 | 0 | 60 | 0 |
| 20 | npc_archivist.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 21 | npc_guest_bellkeeper.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 22 | npc_guest_inner_ferryman.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 23 | npc_guest_town_guide.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 24 | npc_guide.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 25 | npc_material_trader.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 26 | npc_black_marketeer.json | 119 | 0 | 39 | 0 | 0 | 0 | 51 | 0 |
| 27 | npc_guest_moon_past1.json | 119 | 0 | 39 | 0 | 0 | 0 | 52 | 0 |
| 28 | npc_gravekeeper.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 29 | npc_guest_moon_past3.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 30 | npc_guest_tide_lightkeeper.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 31 | items/abyss_tide_bow/legend_pull2.json | 105 | 0 | 32 | 0 | 0 | 0 | 181 | 0 |
| 32 | items/abyss_tide_bow/legend.json | 104 | 0 | 32 | 0 | 0 | 0 | 175 | 0 |
| 33 | items/abyss_tide_bow/legend_pull0.json | 104 | 0 | 32 | 0 | 0 | 0 | 171 | 0 |
| 34 | items/abyss_tide_bow/legend_pull1.json | 104 | 0 | 32 | 0 | 0 | 0 | 174 | 0 |
| 35 | items/abyss_toll_bell_staff/rare.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 36 | items/abyss_ember_bell_staff/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 37 | items/thunder_wing_bow/common.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 38 | items/thunder_wing_bow/common_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 39 | items/thunder_wing_bow/common_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 40 | items/thunder_wing_bow/common_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 41 | items/thunder_wing_bow/legend.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 42 | items/thunder_wing_bow/legend_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 0 | 0 |
| 43 | items/thunder_wing_bow/legend_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 44 | items/thunder_wing_bow/legend_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 45 | items/thunder_wing_bow/rare.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 46 | items/thunder_wing_bow/rare_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 47 | items/thunder_wing_bow/rare_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 48 | items/thunder_wing_bow/rare_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 49 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 50 | npc_stable_keeper.json | 70 | 0 | 20 | 0 | 0 | 0 | 0 | 2 |
| 51 | npc_guest_ember_survivor.json | 62 | 0 | 20 | 0 | 0 | 0 | 52 | 0 |
| 52 | npc_abyss_porter.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 53 | npc_guest_bard.json | 60 | 0 | 19 | 0 | 0 | 0 | 63 | 0 |
| 54 | npc_guest_ember_watch.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 55 | npc_guest_island_ferryman.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 56 | npc_guest_moon_past4.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 57 | npc_guest_mother.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 58 | npc_guest_storm_hutkeeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 59 | npc_guest_weaver.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 60 | npc_innkeeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 61 | npc_perch_keeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 62 | npc_survivor_bone.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 63 | npc_survivor_storm.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 64 | npc_training_master.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 65 | npc_apothecary.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 66 | npc_guest_child.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 67 | npc_guest_herbalist.json | 57 | 0 | 19 | 0 | 0 | 0 | 2 | 0 |
| 68 | npc_survivor_salt.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 69 | npc_tool_merchant.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 70 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 71 | armor/tide_shell/leggings.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 72 | armor/tide_shell/leggings_legend.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 73 | armor/tide_shell/leggings_rare.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 74 | armor/ash_walking/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 75 | items/ember_lantern/common.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 76 | items/ember_lantern/legend.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 77 | items/ember_lantern/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 78 | items/ember_staff/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 79 | items/moon_tide_staff/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 80 | npc_survivor_storm_prop_back.json | 48 | 0 | 16 | 0 | 0 | 0 | 4 | 0 |
| 81 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 82 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 83 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 84 | artisan.json | 36 | 0 | 12 | 0 | 0 | 0 | 9 | 0 |
| 85 | attendant_a.json | 36 | 0 | 12 | 0 | 0 | 0 | 8 | 0 |
| 86 | bone_mortar.json | 36 | 0 | 0 | 0 | 0 | 0 | 633 | 1 |
| 87 | items/tide_chime_charm/legend.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 88 | items/tide_chime_charm/rare.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 89 | armor/thunder_wing/chestplate_legend.json | 33 | 0 | 8 | 0 | 0 | 0 | 188 | 0 |
| 90 | armor/thunder_wing/chestplate_rare.json | 30 | 0 | 8 | 0 | 0 | 0 | 121 | 0 |
| 91 | armor/tide_wader/chestplate.json | 30 | 0 | 10 | 0 | 0 | 0 | 0 | 0 |
| 92 | ash_dragger.json | 30 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| 93 | armor/thunder_wing/chestplate.json | 28 | 0 | 8 | 0 | 0 | 0 | 81 | 0 |
| 94 | bone_bowman.json | 28 | 0 | 4 | 0 | 0 | 0 | 101 | 3 |
| 95 | peak_fledgling.json | 25 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| 96 | armor/ash_walking/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 97 | armor/ash_walking/helmet.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 98 | armor/ember/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 99 | armor/ember/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 100 | armor/ember/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 101 | armor/stone_whale/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 102 | armor/stone_whale/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 103 | armor/stone_whale/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 104 | armor/thunder_wing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 105 | armor/thunder_wing/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 106 | armor/thunder_wing/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 107 | armor/thunder_wing/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 108 | armor/thunder_wing/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 109 | armor/tide_wader/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 110 | armor/tide_wader/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 111 | armor/whale_road/chestplate.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 112 | bandit_archer.json | 24 | 0 | 5 | 0 | 0 | 0 | 84 | 1 |
| 113 | npc_drunkard_prop_r.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 114 | shadow_stitcher.json | 24 | 0 | 0 | 0 | 0 | 0 | 291 | 2 |
| 115 | waning_blade.json | 24 | 0 | 0 | 0 | 0 | 0 | 442 | 2 |
| 116 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 117 | lord_storm.json | 21 | 0 | 0 | 0 | 0 | 0 | 19 | 5 |
| 118 | abyss_charged_moth_s1.json | 20 | 0 | 0 | 0 | 0 | 0 | 113 | 3 |
| 119 | moon_scale.json | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 120 | bone_footman.json | 19 | 0 | 0 | 0 | 0 | 0 | 184 | 2 |
| 121 | bone_standard.json | 19 | 0 | 0 | 0 | 0 | 0 | 61 | 4 |
| 122 | rib_archer.json | 19 | 0 | 0 | 0 | 0 | 0 | 93 | 3 |
| 123 | armor/stone_whale/chestplate_legend.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 124 | ash_smith.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 125 | lord_rib.json | 16 | 0 | 0 | 0 | 0 | 0 | 187 | 3 |
| 126 | armor/stone_whale/chestplate_rare.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 127 | armor/tide_shell/chestplate.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 128 | armor/tide_shell/chestplate_legend.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 129 | armor/tide_shell/chestplate_rare.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 130 | ash_lizard.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 131 | shadow_embracer.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 132 | abyss_lightning_idol_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 78 | 2 |
| 133 | abyss_rib_guard_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 69 | 2 |
| 134 | abyss_salt_husk_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 77 | 2 |
| 135 | armor/wayfarer/chestplate.json | 13 | 0 | 0 | 0 | 0 | 0 | 260 | 0 |
| 136 | armor/peak_crossing/helmet.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 137 | armor/stone_whale/helmet.json | 12 | 0 | 4 | 0 | 0 | 0 | 15 | 0 |
| 138 | armor/stone_whale/helmet_legend.json | 12 | 0 | 4 | 0 | 0 | 0 | 13 | 0 |
| 139 | armor/stone_whale/helmet_rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 15 | 0 |
| 140 | ceiling_walker.json | 12 | 0 | 0 | 0 | 0 | 0 | 58 | 2 |
| 141 | items/whale_bell_staff/rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 142 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 143 | abyss_scorched_bird_s1.json | 11 | 0 | 3 | 0 | 0 | 0 | 44 | 0 |
| 144 | abyss_scorched_bird_s3.json | 11 | 0 | 3 | 0 | 0 | 0 | 43 | 0 |
| 145 | moon_leaper.json | 11 | 0 | 0 | 0 | 0 | 0 | 21 | 2 |
| 146 | abyss_scorched_bird_s2.json | 10 | 0 | 3 | 0 | 0 | 0 | 35 | 0 |
| 147 | abyss_white_shell_keeper_s1.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 148 | ash_trail.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 149 | cinder_mite.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 150 | drift_hermit.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 151 | npc_guest_tide_ferryman.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 152 | light_swarm.json | 8 | 0 | 0 | 0 | 0 | 0 | 69 | 1 |
| 153 | tide_crab.json | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 154 | heart_warden.json | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 155 | mist_angler.json | 7 | 0 | 0 | 0 | 0 | 0 | 29 | 2 |
| 156 | abyss_charged_moth_s3.json | 6 | 0 | 0 | 0 | 0 | 0 | 126 | 0 |
| 157 | armor/tide_shell/helmet_legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 158 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 159 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 160 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 161 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 162 | items/tide_watching_shell/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 163 | items/tide_watching_shell/rare.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 164 | sand_burrower.json | 6 | 0 | 0 | 0 | 0 | 0 | 27 | 1 |
| 165 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 166 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 167 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 168 | abyss_charged_moth_s2.json | 5 | 0 | 0 | 0 | 0 | 0 | 113 | 0 |
| 169 | cliff_creeper.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 170 | moon_core.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 171 | rift_armor.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 172 | rope_hanger.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 173 | scorched_warden.json | 5 | 0 | 0 | 0 | 0 | 0 | 9 | 1 |
| 174 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 175 | king_bone_c0.json | 4 | 0 | 0 | 0 | 0 | 0 | 5 | 4 |
| 176 | king_bone_c2.json | 4 | 0 | 0 | 0 | 0 | 0 | 11 | 4 |
| 177 | king_shadow_c1.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 178 | king_shadow_c2.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 179 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 180 | young_thunder_wing.json | 4 | 0 | 0 | 0 | 0 | 0 | 81 | 0 |
| 181 | abyss_lightning_idol_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 73 | 0 |
| 182 | abyss_lightning_idol_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 79 | 0 |
| 183 | abyss_rib_guard_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 67 | 0 |
| 184 | abyss_rib_guard_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 74 | 0 |
| 185 | abyss_salt_husk_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 72 | 0 |
| 186 | abyss_salt_husk_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 77 | 0 |
| 187 | armor/stone_whale/chestplate.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 188 | armor/tide_shell/helmet.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 189 | armor/tide_shell/helmet_rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 190 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 191 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 192 | items/tide_shell_trident/legend.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 193 | items/tide_shell_trident/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 194 | king_bone_c1.json | 3 | 0 | 0 | 0 | 0 | 0 | 6 | 3 |
| 195 | king_shadow_c0.json | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 196 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 197 | full_keeper.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 198 | king_storm_c0.json | 2 | 0 | 0 | 0 | 0 | 0 | 4 | 2 |
| 199 | king_storm_c1.json | 2 | 0 | 0 | 0 | 0 | 0 | 4 | 2 |
| 200 | king_storm_c2.json | 2 | 0 | 0 | 0 | 0 | 0 | 4 | 2 |
| 201 | lightning_gleaner.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 202 | moss_dead.json | 2 | 0 | 0 | 0 | 0 | 0 | 54 | 0 |
| 203 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 204 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 205 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 206 | ash_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 207 | dust_crawler.json | 1 | 0 | 0 | 0 | 0 | 0 | 31 | 0 |
| 208 | elder_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 209 | king_ash_c1.json | 1 | 0 | 0 | 0 | 0 | 0 | 7 | 1 |
| 210 | king_ash_c2.json | 1 | 0 | 0 | 0 | 0 | 0 | 7 | 1 |
| 211 | king_salt_c1.json | 1 | 0 | 0 | 0 | 0 | 0 | 28 | 0 |
| 212 | king_salt_c2.json | 1 | 0 | 0 | 0 | 0 | 0 | 29 | 0 |
| 213 | lord_ash.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 214 | mount_horn_beast.json | 1 | 0 | 0 | 0 | 0 | 0 | 10 | 1 |
| 215 | npc_abyss_porter_prop_back.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 216 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 217 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 218 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 219 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 220 | storm_ram.json | 1 | 0 | 0 | 0 | 0 | 0 | 2 | 1 |

## 骨ごとの詳細 (上位 25 個)

### items/abyss_storm_hammer/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 1296 | 0 | 0 | 112 | 0 | 0 | 0 | 0 | - |

### items/whale_dagger/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 452 | 0 | 0 | 80 | 0 | 0 | 0 | 0 | - |

### items/whalebone_greatsword/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 580 | 0 | 0 | 72 | 0 | 0 | 0 | 0 | - |

### items/storm_staff/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 764 | 0 | 0 | 64 | 0 | 0 | 0 | 0 | - |

### npc_shrine_priest.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 428 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| head | 752 | 0 | 0 | 19 | 0 | 0 | 0 | 0 | ok(埋36%/深0.5) |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋25%/深0.5) |

### items/abyss_storm_sword/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 952 | 0 | 0 | 49 | 0 | 0 | 0 | 0 | - |

### items/storm_hammer/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 688 | 0 | 0 | 48 | 0 | 0 | 0 | 46 | - |

### items/abyss_storm_bell_staff/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 744 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/abyss_storm_dagger/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 576 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/abyss_storm_staff/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 980 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/raiju_claw/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 624 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/storm_bell_staff/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 692 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/storm_dagger/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 520 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/storm_sword/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 604 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/warden_glaive/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 466 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/waterway_fang/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 528 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/whale_hammer/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 482 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### items/whale_staff/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 650 | 0 | 0 | 48 | 0 | 0 | 0 | 3 | - |

### npc_guest_storm_elder.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 338 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| head | 614 | 0 | 0 | 0 | 0 | 0 | 0 | 60 | ok(埋49%/深0.5) |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋57%/深0.5) |

### npc_archivist.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 416 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋25%/深0.5) |

### npc_guest_bellkeeper.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 370 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋25%/深0.5) |

### npc_guest_inner_ferryman.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 358 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋44%/深0.4) |

### npc_guest_town_guide.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 484 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋28%/深0.4) |

### npc_guide.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 484 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋28%/深0.4) |

### npc_material_trader.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 428 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋25%/深0.5) |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

49 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 0 個。

## ブロックモデル

面の欠け・宙に浮く部品なし。
