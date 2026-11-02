# メッシュ監査 (tools/audit/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/audit/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 961 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **9524**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 3201 / つぶれた三角形: 204
- 関節: detach 92 / shallow 77 / ok 1555 (うち pivot が親の外: 831)
- ブロックモデル: 87 個中、面の欠け・宙に浮く部品のあるもの 0

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | items/abyss_storm_hammer/legend.json | 336 | 0 | 112 | 0 | 0 | 0 | 0 | 0 |
| 2 | items/whale_dagger/rare.json | 240 | 0 | 80 | 0 | 0 | 0 | 0 | 0 |
| 3 | items/whalebone_greatsword/rare.json | 216 | 0 | 72 | 0 | 0 | 0 | 0 | 0 |
| 4 | items/wild_ashrite_maul/legend.json | 204 | 0 | 68 | 0 | 0 | 0 | 0 | 0 |
| 5 | items/storm_staff/rare.json | 192 | 0 | 64 | 0 | 0 | 0 | 0 | 0 |
| 6 | npc_shrine_priest.json | 177 | 0 | 59 | 0 | 0 | 0 | 0 | 0 |
| 7 | items/abyss_storm_sword/legend.json | 147 | 0 | 49 | 0 | 0 | 0 | 0 | 0 |
| 8 | items/storm_hammer/rare.json | 146 | 0 | 48 | 0 | 0 | 0 | 46 | 0 |
| 9 | items/abyss_storm_bell_staff/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 10 | items/abyss_storm_dagger/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 11 | items/abyss_storm_staff/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 12 | items/raiju_claw/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 13 | items/storm_bell_staff/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 14 | items/storm_dagger/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 15 | items/storm_sword/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 16 | items/warden_glaive/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 17 | items/waterway_fang/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 18 | items/whale_hammer/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 19 | items/whale_staff/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 3 | 0 |
| 20 | items/wild_effigy_totem/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 21 | npc_guest_storm_elder.json | 123 | 0 | 40 | 0 | 0 | 0 | 60 | 0 |
| 22 | items/wild_scribe_staff/common.json | 122 | 0 | 31 | 0 | 0 | 0 | 583 | 0 |
| 23 | items/wild_signal_fire/legend.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 24 | npc_guest_bellkeeper.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 25 | npc_guest_inner_ferryman.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 26 | npc_guest_town_guide.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 27 | npc_guide.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 28 | npc_material_trader.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 29 | npc_black_marketeer.json | 119 | 0 | 39 | 0 | 0 | 0 | 51 | 0 |
| 30 | npc_guest_moon_past1.json | 119 | 0 | 39 | 0 | 0 | 0 | 52 | 0 |
| 31 | npc_tower_keeper.json | 119 | 0 | 39 | 0 | 0 | 0 | 52 | 0 |
| 32 | npc_gravekeeper.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 33 | npc_guest_moon_past3.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 34 | npc_guest_tide_lightkeeper.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 35 | items/tower_clap_bell_staff/legend.json | 109 | 0 | 32 | 0 | 0 | 0 | 272 | 0 |
| 36 | shadow_stitcher.json | 106 | 0 | 32 | 0 | 0 | 0 | 0 | 2 |
| 37 | items/abyss_tide_bow/legend_pull2.json | 105 | 0 | 32 | 0 | 0 | 0 | 181 | 0 |
| 38 | items/abyss_tide_bow/legend.json | 104 | 0 | 32 | 0 | 0 | 0 | 175 | 0 |
| 39 | items/abyss_tide_bow/legend_pull0.json | 104 | 0 | 32 | 0 | 0 | 0 | 171 | 0 |
| 40 | items/abyss_tide_bow/legend_pull1.json | 104 | 0 | 32 | 0 | 0 | 0 | 174 | 0 |
| 41 | armor/wild_windweave/helmet.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 42 | armor/wild_windweave/helmet_rare.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 43 | items/abyss_toll_bell_staff/rare.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 44 | armor/wild_sailcloth/boots.json | 90 | 0 | 30 | 0 | 0 | 0 | 0 | 0 |
| 45 | armor/wild_oilskin/helmet.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 46 | items/wild_cloudhymn_bell_staff/legend.json | 75 | 0 | 24 | 0 | 0 | 0 | 74 | 0 |
| 47 | items/abyss_ember_bell_staff/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 48 | items/thunder_wing_bow/rare.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 49 | items/thunder_wing_bow/rare_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 50 | items/thunder_wing_bow/rare_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 51 | items/thunder_wing_bow/rare_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 52 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 53 | items/tower_balance_sword/legend.json | 69 | 0 | 23 | 0 | 0 | 0 | 0 | 0 |
| 54 | npc_guest_ember_survivor.json | 62 | 0 | 20 | 0 | 0 | 0 | 52 | 0 |
| 55 | items/wild_scarecrow_bell_staff/common.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 56 | npc_abyss_porter.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 57 | npc_guest_bard.json | 60 | 0 | 19 | 0 | 0 | 0 | 63 | 0 |
| 58 | npc_guest_ember_watch.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 59 | npc_guest_island_ferryman.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 60 | npc_guest_moon_past4.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 61 | npc_guest_mother.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 62 | npc_guest_storm_hutkeeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 63 | npc_guest_weaver.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 64 | npc_innkeeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 65 | npc_perch_keeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 66 | npc_survivor_bone.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 67 | npc_survivor_storm.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 68 | npc_training_master.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 69 | npc_apothecary.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 70 | npc_guest_child.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 71 | npc_guest_herbalist.json | 57 | 0 | 19 | 0 | 0 | 0 | 2 | 0 |
| 72 | npc_survivor_salt.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 73 | npc_tool_merchant.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 74 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 75 | armor/tide_shell/leggings.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 76 | armor/tide_shell/leggings_legend.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 77 | armor/tide_shell/leggings_rare.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 78 | items/wild_cloudhymn_slip/legend.json | 50 | 0 | 16 | 0 | 0 | 0 | 41 | 0 |
| 79 | armor/ash_walking/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 80 | items/ember_lantern/legend.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 81 | items/ember_staff/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 82 | items/moon_tide_staff/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 83 | items/wild_hermit_knife/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 84 | npc_survivor_storm_prop_back.json | 48 | 0 | 16 | 0 | 0 | 0 | 4 | 0 |
| 85 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 86 | ash_striker.json | 41 | 0 | 12 | 0 | 0 | 0 | 1 | 1 |
| 87 | items/wild_spark_dagger/rare.json | 39 | 0 | 13 | 0 | 0 | 0 | 0 | 0 |
| 88 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 89 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 90 | artisan.json | 36 | 0 | 12 | 0 | 0 | 0 | 9 | 0 |
| 91 | attendant_a.json | 36 | 0 | 12 | 0 | 0 | 0 | 8 | 0 |
| 92 | bone_mortar.json | 36 | 0 | 0 | 0 | 0 | 0 | 633 | 1 |
| 93 | items/tide_chime_charm/rare.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 94 | armor/thunder_wing/chestplate_legend.json | 33 | 0 | 8 | 0 | 0 | 0 | 188 | 0 |
| 95 | armor/thunder_wing/chestplate_rare.json | 30 | 0 | 8 | 0 | 0 | 0 | 121 | 0 |
| 96 | armor/tide_wader/chestplate.json | 30 | 0 | 10 | 0 | 0 | 0 | 0 | 0 |
| 97 | ash_dragger.json | 30 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| 98 | armor/thunder_wing/chestplate.json | 28 | 0 | 8 | 0 | 0 | 0 | 81 | 0 |
| 99 | bone_bowman.json | 28 | 0 | 4 | 0 | 0 | 0 | 101 | 3 |
| 100 | rewinder.json | 28 | 0 | 0 | 0 | 0 | 0 | 472 | 1 |
| 101 | items/divine_rewind_dagger/legend.json | 27 | 0 | 9 | 0 | 0 | 0 | 0 | 0 |
| 102 | armor/ash_walking/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 103 | armor/ash_walking/helmet.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 104 | armor/ember/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 105 | armor/ember/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 106 | armor/ember/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 107 | armor/stone_whale/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 108 | armor/stone_whale/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 109 | armor/stone_whale/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 110 | armor/thunder_wing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 111 | armor/thunder_wing/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 112 | armor/thunder_wing/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 113 | armor/thunder_wing/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 114 | armor/thunder_wing/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 115 | armor/tide_wader/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 116 | armor/tide_wader/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 117 | armor/whale_road/chestplate.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 118 | bandit_archer.json | 24 | 0 | 5 | 0 | 0 | 0 | 84 | 1 |
| 119 | npc_drunkard_prop_r.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 120 | waning_blade.json | 24 | 0 | 0 | 0 | 0 | 0 | 442 | 2 |
| 121 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 122 | lord_storm.json | 21 | 0 | 0 | 0 | 0 | 0 | 19 | 5 |
| 123 | moon_leaper.json | 21 | 0 | 0 | 0 | 0 | 0 | 28 | 4 |
| 124 | moon_scale.json | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 125 | bone_footman.json | 19 | 0 | 0 | 0 | 0 | 0 | 184 | 2 |
| 126 | bone_standard.json | 19 | 0 | 0 | 0 | 0 | 0 | 61 | 4 |
| 127 | rib_archer.json | 19 | 0 | 0 | 0 | 0 | 0 | 93 | 3 |
| 128 | armor/stone_whale/chestplate_legend.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 129 | ash_smith.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 130 | items/wild_banner_spear/rare.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 131 | items/wild_lure_staff/rare.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 132 | items/wild_mourning_bell_staff/rare.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 133 | founder.json | 17 | 0 | 0 | 0 | 0 | 0 | 2 | 5 |
| 134 | pilgrim_hand.json | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| 135 | lord_rib.json | 16 | 0 | 0 | 0 | 0 | 0 | 187 | 3 |
| 136 | armor/stone_whale/chestplate_rare.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 137 | armor/tide_shell/chestplate.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 138 | armor/tide_shell/chestplate_legend.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 139 | armor/tide_shell/chestplate_rare.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 140 | ash_lizard.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 141 | peak_fledgling.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 142 | shadow_embracer.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 143 | armor/wild_lurelight/chestplate.json | 14 | 0 | 4 | 0 | 0 | 0 | 54 | 0 |
| 144 | armor/wild_lurelight/chestplate_rare.json | 14 | 0 | 4 | 0 | 0 | 0 | 54 | 0 |
| 145 | abyss_lightning_idol_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 78 | 2 |
| 146 | abyss_rib_guard_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 69 | 2 |
| 147 | abyss_salt_husk_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 77 | 2 |
| 148 | armor/wayfarer/chestplate.json | 13 | 0 | 0 | 0 | 0 | 0 | 260 | 0 |
| 149 | armor/peak_crossing/helmet.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 150 | armor/stone_whale/helmet.json | 12 | 0 | 4 | 0 | 0 | 0 | 15 | 0 |
| 151 | armor/stone_whale/helmet_legend.json | 12 | 0 | 4 | 0 | 0 | 0 | 13 | 0 |
| 152 | armor/stone_whale/helmet_rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 15 | 0 |
| 153 | items/box_tower/common.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 154 | items/whale_bell_staff/rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 155 | items/wild_fortress_greatsword/rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 1 | 0 |
| 156 | last_bellkeeper.json | 12 | 0 | 0 | 0 | 0 | 0 | 1 | 4 |
| 157 | sand_burrower.json | 12 | 0 | 0 | 0 | 0 | 0 | 54 | 2 |
| 158 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 159 | abyss_charged_moth_s1.json | 11 | 0 | 0 | 0 | 0 | 0 | 4 | 3 |
| 160 | abyss_scorched_bird_s1.json | 11 | 0 | 3 | 0 | 0 | 0 | 44 | 0 |
| 161 | abyss_scorched_bird_s3.json | 11 | 0 | 3 | 0 | 0 | 0 | 43 | 0 |
| 162 | rope_hanger.json | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 163 | tower_master.json | 11 | 0 | 0 | 0 | 0 | 0 | 1 | 3 |
| 164 | abyss_scorched_bird_s2.json | 10 | 0 | 3 | 0 | 0 | 0 | 35 | 0 |
| 165 | abyss_white_shell_keeper_s1.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 166 | ash_trail.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 167 | cinder_mite.json | 10 | 0 | 0 | 0 | 0 | 0 | 3 | 2 |
| 168 | circuit_stake.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 169 | drift_hermit.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 170 | npc_guest_tide_ferryman.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 171 | stray_wisp.json | 10 | 0 | 0 | 0 | 0 | 0 | 15 | 2 |
| 172 | armor/divine_bellkeeper/chestplate.json | 9 | 0 | 3 | 0 | 0 | 0 | 0 | 0 |
| 173 | armor/divine_bellkeeper/chestplate_legend.json | 9 | 0 | 3 | 0 | 0 | 0 | 0 | 0 |
| 174 | items/wild_shell_bell_staff/common.json | 9 | 0 | 3 | 0 | 0 | 0 | 0 | 0 |
| 175 | lamp_eye.json | 8 | 0 | 0 | 0 | 0 | 0 | 60 | 1 |
| 176 | light_swarm.json | 8 | 0 | 0 | 0 | 0 | 0 | 69 | 1 |
| 177 | tide_crab.json | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 178 | tower_eye.json | 8 | 0 | 0 | 0 | 0 | 0 | 69 | 1 |
| 179 | ceiling_walker.json | 7 | 0 | 0 | 0 | 0 | 0 | 40 | 1 |
| 180 | heart_warden.json | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 181 | mist_angler.json | 7 | 0 | 0 | 0 | 0 | 0 | 29 | 2 |
| 182 | armor/tide_shell/helmet_legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 183 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 184 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 185 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 186 | items/tide_watching_shell/common.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 187 | items/wild_skylark_bow/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 188 | items/wild_skylark_bow/legend_pull0.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 189 | items/wild_skylark_bow/legend_pull1.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 190 | items/wild_skylark_bow/legend_pull2.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 191 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 192 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 193 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 194 | cliff_creeper.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 195 | forge_giant.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 196 | moon_core.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 197 | rift_armor.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 198 | scorched_warden.json | 5 | 0 | 0 | 0 | 0 | 0 | 9 | 1 |
| 199 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 200 | armor/wild_oilskin/chestplate.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 201 | king_bone_c0.json | 4 | 0 | 0 | 0 | 0 | 0 | 5 | 4 |
| 202 | king_bone_c2.json | 4 | 0 | 0 | 0 | 0 | 0 | 11 | 4 |
| 203 | king_shadow_c1.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 204 | king_shadow_c2.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 205 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 206 | pilgrim_eye.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 207 | shade_strider.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 208 | young_thunder_wing.json | 4 | 0 | 0 | 0 | 0 | 0 | 81 | 0 |
| 209 | abyss_lightning_idol_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 73 | 0 |
| 210 | abyss_lightning_idol_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 79 | 0 |
| 211 | abyss_rib_guard_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 67 | 0 |
| 212 | abyss_rib_guard_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 74 | 0 |
| 213 | abyss_salt_husk_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 72 | 0 |
| 214 | abyss_salt_husk_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 77 | 0 |
| 215 | armor/stone_whale/chestplate.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 216 | armor/tide_shell/helmet.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 217 | armor/tide_shell/helmet_rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 218 | bell_mourner.json | 3 | 0 | 0 | 0 | 0 | 0 | 63 | 0 |
| 219 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 220 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 221 | items/tide_shell_trident/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 222 | items/wild_chain_greatsword/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 223 | king_bone_c1.json | 3 | 0 | 0 | 0 | 0 | 0 | 6 | 3 |
| 224 | king_shadow_c0.json | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 225 | plume_warden.json | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 226 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 227 | armor/wild_hanged/chestplate.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 228 | armor/wild_hanged/chestplate_legend.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 229 | armor/wild_windweave/boots.json | 2 | 0 | 0 | 0 | 0 | 0 | 52 | 0 |
| 230 | armor/wild_windweave/boots_rare.json | 2 | 0 | 0 | 0 | 0 | 0 | 52 | 0 |
| 231 | full_keeper.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 232 | king_storm_c0.json | 2 | 0 | 0 | 0 | 0 | 0 | 4 | 2 |
| 233 | king_storm_c1.json | 2 | 0 | 0 | 0 | 0 | 0 | 4 | 2 |
| 234 | king_storm_c2.json | 2 | 0 | 0 | 0 | 0 | 0 | 4 | 2 |
| 235 | moss_dead.json | 2 | 0 | 0 | 0 | 0 | 0 | 54 | 0 |
| 236 | pilgrim_plume.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 237 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 238 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 239 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 240 | abyss_charged_moth_s2.json | 1 | 0 | 0 | 0 | 0 | 0 | 5 | 1 |
| 241 | abyss_charged_moth_s3.json | 1 | 0 | 0 | 0 | 0 | 0 | 8 | 1 |
| 242 | armor/wild_soot/chestplate.json | 1 | 0 | 0 | 0 | 0 | 0 | 22 | 0 |
| 243 | armor/wild_soot/chestplate_rare.json | 1 | 0 | 0 | 0 | 0 | 0 | 22 | 0 |
| 244 | armor/wild_stargazer/helmet.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 245 | armor/wild_stargazer/helmet_legend.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 246 | ash_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 247 | bone_centipede.json | 1 | 0 | 0 | 0 | 0 | 0 | 3 | 1 |
| 248 | crystal_lizard.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 249 | dust_crawler.json | 1 | 0 | 0 | 0 | 0 | 0 | 29 | 0 |
| 250 | elder_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 251 | king_ash_c1.json | 1 | 0 | 0 | 0 | 0 | 0 | 7 | 1 |
| 252 | king_ash_c2.json | 1 | 0 | 0 | 0 | 0 | 0 | 7 | 1 |
| 253 | king_salt_c1.json | 1 | 0 | 0 | 0 | 0 | 0 | 28 | 0 |
| 254 | king_salt_c2.json | 1 | 0 | 0 | 0 | 0 | 0 | 29 | 0 |
| 255 | lord_ash.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 256 | mount_horn_beast.json | 1 | 0 | 0 | 0 | 0 | 0 | 10 | 1 |
| 257 | npc_abyss_porter_prop_back.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 258 | past_shadow.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 259 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 260 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 261 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 262 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 263 | storm_ram.json | 1 | 0 | 0 | 0 | 0 | 0 | 2 | 1 |

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

### items/wild_ashrite_maul/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 518 | 0 | 0 | 68 | 0 | 0 | 0 | 0 | - |

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

### items/wild_effigy_totem/rare.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 468 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | - |

### npc_guest_storm_elder.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 338 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| head | 614 | 0 | 0 | 0 | 0 | 0 | 0 | 60 | ok(埋49%/深0.5) |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋57%/深0.5) |

### items/wild_scribe_staff/common.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 456 | 0 | 0 | 31 | 0 | 0 | 0 | 583 | - |

### items/wild_signal_fire/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 280 | 0 | 0 | 40 | 0 | 0 | 0 | 0 | - |

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

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

90 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 0 個。

## ブロックモデル

面の欠け・宙に浮く部品なし。
