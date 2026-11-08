# メッシュ監査 (tools/audit/mesh_audit.py が生成)

水密性・関節の隙間・裏面透けの数え上げ。`python tools/audit/mesh_audit.py` で作り直す。手で編集しない。

## 要約

- 調べたメッシュ: 1025 個 / 開いた辺の合計: **0** / 裏面透けの画素数の合計: **12043**
- 逆法線の塊: 0 / 向きの食い違い: 0 / 多様体でない辺: 3698 / つぶれた三角形: 204
- 関節: detach 96 / shallow 115 / ok 1856 (うち pivot が親の外: 1000)
- ブロックモデル: 87 個中、面の欠け・宙に浮く部品のあるもの 0

## ランキング (悪い順)

| # | メッシュ | 点 | 開いた辺 | 非多様体 | 向き違い | つぶれ | 逆 | 透け画素 | 関節 NG |
|---|---|---|---|---|---|---|---|---|---|
| 1 | items/abyss_storm_hammer/legend.json | 336 | 0 | 112 | 0 | 0 | 0 | 0 | 0 |
| 2 | mount_carriage.json | 247 | 0 | 64 | 0 | 0 | 0 | 663 | 6 |
| 3 | items/whale_dagger/rare.json | 240 | 0 | 80 | 0 | 0 | 0 | 0 | 0 |
| 4 | items/whalebone_greatsword/rare.json | 216 | 0 | 72 | 0 | 0 | 0 | 0 | 0 |
| 5 | items/wild_ashrite_maul/legend.json | 204 | 0 | 68 | 0 | 0 | 0 | 0 | 0 |
| 6 | items/storm_staff/rare.json | 192 | 0 | 64 | 0 | 0 | 0 | 0 | 0 |
| 7 | npc_shrine_priest.json | 178 | 0 | 59 | 0 | 0 | 0 | 0 | 1 |
| 8 | npc_guest_camp_mapmaker.json | 177 | 0 | 59 | 0 | 0 | 0 | 0 | 0 |
| 9 | items/abyss_storm_sword/legend.json | 147 | 0 | 49 | 0 | 0 | 0 | 0 | 0 |
| 10 | items/storm_hammer/rare.json | 146 | 0 | 48 | 0 | 0 | 0 | 46 | 0 |
| 11 | items/abyss_storm_bell_staff/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 12 | items/abyss_storm_dagger/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 13 | items/abyss_storm_staff/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 14 | items/raiju_claw/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 15 | items/storm_bell_staff/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 16 | items/storm_dagger/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 17 | items/storm_sword/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 18 | items/warden_glaive/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 19 | items/waterway_fang/legend.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 20 | items/whale_hammer/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 21 | items/whale_staff/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 3 | 0 |
| 22 | items/wild_effigy_totem/rare.json | 144 | 0 | 48 | 0 | 0 | 0 | 0 | 0 |
| 23 | npc_guest_storm_elder.json | 123 | 0 | 40 | 0 | 0 | 0 | 60 | 0 |
| 24 | items/wild_scribe_staff/common.json | 122 | 0 | 31 | 0 | 0 | 0 | 583 | 0 |
| 25 | npc_guest_camp_pilgrim.json | 122 | 0 | 40 | 0 | 0 | 0 | 52 | 0 |
| 26 | items/wild_signal_fire/legend.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 27 | npc_guest_bellkeeper.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 28 | npc_guest_inner_ferryman.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 29 | npc_guest_town_guide.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 30 | npc_guide.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 31 | npc_material_trader.json | 120 | 0 | 40 | 0 | 0 | 0 | 0 | 0 |
| 32 | npc_black_marketeer.json | 119 | 0 | 39 | 0 | 0 | 0 | 51 | 0 |
| 33 | npc_guest_moon_past1.json | 119 | 0 | 39 | 0 | 0 | 0 | 52 | 0 |
| 34 | npc_tower_keeper.json | 119 | 0 | 39 | 0 | 0 | 0 | 52 | 0 |
| 35 | npc_gravekeeper.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 36 | npc_guest_bellwatch_crone.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 37 | npc_guest_camp_minstrel.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 38 | npc_guest_moon_past3.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 39 | npc_guest_tide_lightkeeper.json | 117 | 0 | 39 | 0 | 0 | 0 | 0 | 0 |
| 40 | heart_warden.json | 111 | 0 | 35 | 0 | 0 | 0 | 27 | 5 |
| 41 | items/tower_clap_bell_staff/legend.json | 109 | 0 | 32 | 0 | 0 | 0 | 272 | 0 |
| 42 | shadow_stitcher.json | 106 | 0 | 32 | 0 | 0 | 0 | 0 | 2 |
| 43 | items/abyss_tide_bow/legend_pull2.json | 105 | 0 | 32 | 0 | 0 | 0 | 181 | 0 |
| 44 | items/abyss_tide_bow/legend.json | 104 | 0 | 32 | 0 | 0 | 0 | 175 | 0 |
| 45 | items/abyss_tide_bow/legend_pull0.json | 104 | 0 | 32 | 0 | 0 | 0 | 171 | 0 |
| 46 | items/abyss_tide_bow/legend_pull1.json | 104 | 0 | 32 | 0 | 0 | 0 | 174 | 0 |
| 47 | armor/wild_windweave/helmet.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 48 | armor/wild_windweave/helmet_rare.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 49 | items/abyss_toll_bell_staff/rare.json | 96 | 0 | 32 | 0 | 0 | 0 | 0 | 0 |
| 50 | armor/wild_sailcloth/boots.json | 90 | 0 | 30 | 0 | 0 | 0 | 0 | 0 |
| 51 | armor/wild_oilskin/helmet.json | 75 | 0 | 25 | 0 | 0 | 0 | 0 | 0 |
| 52 | items/wild_cloudhymn_bell_staff/legend.json | 75 | 0 | 24 | 0 | 0 | 0 | 74 | 0 |
| 53 | items/abyss_ember_bell_staff/legend.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 54 | items/thunder_wing_bow/rare.json | 72 | 0 | 24 | 0 | 4 | 0 | 1 | 0 |
| 55 | items/thunder_wing_bow/rare_pull0.json | 72 | 0 | 24 | 0 | 8 | 0 | 1 | 0 |
| 56 | items/thunder_wing_bow/rare_pull1.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 57 | items/thunder_wing_bow/rare_pull2.json | 72 | 0 | 24 | 0 | 4 | 0 | 0 | 0 |
| 58 | smith_hammer.json | 72 | 0 | 24 | 0 | 0 | 0 | 0 | 0 |
| 59 | items/tower_balance_sword/legend.json | 69 | 0 | 23 | 0 | 0 | 0 | 0 | 0 |
| 60 | npc_guest_frostfort_hunter.json | 63 | 0 | 20 | 0 | 0 | 0 | 60 | 0 |
| 61 | npc_guest_isle_ferryman.json | 63 | 0 | 20 | 0 | 0 | 0 | 60 | 0 |
| 62 | npc_guest_ember_survivor.json | 62 | 0 | 20 | 0 | 0 | 0 | 52 | 0 |
| 63 | npc_guest_emberfort_survivor.json | 62 | 0 | 20 | 0 | 0 | 0 | 57 | 0 |
| 64 | npc_guest_frostfort_fletcher.json | 62 | 0 | 20 | 0 | 0 | 0 | 57 | 0 |
| 65 | npc_guest_windmill_mossguard.json | 62 | 0 | 20 | 0 | 0 | 0 | 54 | 0 |
| 66 | items/wild_scarecrow_bell_staff/common.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 67 | npc_abyss_porter.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 68 | npc_guest_bard.json | 60 | 0 | 19 | 0 | 0 | 0 | 63 | 0 |
| 69 | npc_guest_bonefort_beacon.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 70 | npc_guest_bonefort_scribe.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 71 | npc_guest_ember_watch.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 72 | npc_guest_island_ferryman.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 73 | npc_guest_lightvillage_comber.json | 60 | 0 | 20 | 0 | 0 | 0 | 1 | 0 |
| 74 | npc_guest_moon_past4.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 75 | npc_guest_mother.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 76 | npc_guest_storm_hutkeeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 77 | npc_guest_weaver.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 78 | npc_innkeeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 79 | npc_perch_keeper.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 80 | npc_survivor_bone.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 81 | npc_survivor_storm.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 82 | npc_training_master.json | 60 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| 83 | npc_apothecary.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 84 | npc_guest_child.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 85 | npc_guest_herbalist.json | 57 | 0 | 19 | 0 | 0 | 0 | 2 | 0 |
| 86 | npc_guest_tidefort_netmender.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 87 | npc_guest_tidefort_watch.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 88 | npc_survivor_salt.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 89 | npc_tool_merchant.json | 57 | 0 | 19 | 0 | 0 | 0 | 0 | 0 |
| 90 | storyteller.json | 56 | 0 | 13 | 0 | 0 | 0 | 359 | 0 |
| 91 | armor/tide_shell/leggings.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 92 | armor/tide_shell/leggings_legend.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 93 | armor/tide_shell/leggings_rare.json | 54 | 0 | 18 | 0 | 0 | 0 | 0 | 0 |
| 94 | items/wild_cloudhymn_slip/legend.json | 50 | 0 | 16 | 0 | 0 | 0 | 41 | 0 |
| 95 | armor/ash_walking/chestplate.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 96 | items/ember_lantern/legend.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 97 | items/ember_staff/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 98 | items/moon_tide_staff/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 99 | items/wild_hermit_knife/rare.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 100 | npc_survivor_storm_prop_back.json | 48 | 0 | 16 | 0 | 0 | 0 | 4 | 0 |
| 101 | whale_chaser.json | 48 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| 102 | ash_striker.json | 41 | 0 | 12 | 0 | 0 | 0 | 1 | 1 |
| 103 | items/wild_spark_dagger/rare.json | 39 | 0 | 13 | 0 | 0 | 0 | 0 | 0 |
| 104 | thunder_supplicant.json | 39 | 0 | 11 | 0 | 0 | 0 | 121 | 0 |
| 105 | whalebone_captain.json | 38 | 0 | 12 | 0 | 0 | 0 | 51 | 0 |
| 106 | bone_mortar.json | 36 | 0 | 0 | 0 | 0 | 0 | 633 | 1 |
| 107 | founder.json | 36 | 0 | 4 | 0 | 0 | 0 | 454 | 2 |
| 108 | items/tide_chime_charm/rare.json | 36 | 0 | 12 | 0 | 0 | 0 | 0 | 0 |
| 109 | armor/thunder_wing/chestplate_legend.json | 33 | 0 | 8 | 0 | 0 | 0 | 188 | 0 |
| 110 | king_bone_c2.json | 33 | 0 | 9 | 0 | 0 | 0 | 40 | 4 |
| 111 | king_ash_c2.json | 32 | 0 | 9 | 0 | 0 | 0 | 29 | 4 |
| 112 | king_salt_c2.json | 32 | 0 | 9 | 0 | 0 | 0 | 39 | 4 |
| 113 | king_shadow_c2.json | 32 | 0 | 9 | 0 | 0 | 0 | 38 | 4 |
| 114 | king_storm_c2.json | 32 | 0 | 9 | 0 | 0 | 0 | 33 | 4 |
| 115 | armor/thunder_wing/chestplate_rare.json | 30 | 0 | 8 | 0 | 0 | 0 | 121 | 0 |
| 116 | armor/tide_wader/chestplate.json | 30 | 0 | 10 | 0 | 0 | 0 | 0 | 0 |
| 117 | ash_dragger.json | 30 | 0 | 0 | 0 | 0 | 0 | 0 | 6 |
| 118 | armor/thunder_wing/chestplate.json | 28 | 0 | 8 | 0 | 0 | 0 | 81 | 0 |
| 119 | bone_bowman.json | 28 | 0 | 4 | 0 | 0 | 0 | 101 | 3 |
| 120 | rewinder.json | 28 | 0 | 0 | 0 | 0 | 0 | 472 | 1 |
| 121 | items/divine_rewind_dagger/legend.json | 27 | 0 | 9 | 0 | 0 | 0 | 0 | 0 |
| 122 | armor/ash_walking/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 123 | armor/ash_walking/helmet.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 124 | armor/ember/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 125 | armor/ember/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 126 | armor/ember/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 127 | armor/stone_whale/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 128 | armor/stone_whale/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 129 | armor/stone_whale/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 130 | armor/thunder_wing/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 131 | armor/thunder_wing/boots_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 132 | armor/thunder_wing/boots_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 133 | armor/thunder_wing/leggings_legend.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 134 | armor/thunder_wing/leggings_rare.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 135 | armor/tide_wader/boots.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 136 | armor/tide_wader/leggings.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 137 | armor/whale_road/chestplate.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 138 | bandit_archer.json | 24 | 0 | 5 | 0 | 0 | 0 | 84 | 1 |
| 139 | npc_drunkard_prop_r.json | 24 | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| 140 | waning_blade.json | 24 | 0 | 0 | 0 | 0 | 0 | 442 | 2 |
| 141 | bell_hermit.json | 22 | 0 | 0 | 0 | 0 | 0 | 456 | 0 |
| 142 | bubble_puffer.json | 22 | 0 | 0 | 0 | 0 | 0 | 440 | 0 |
| 143 | lord_storm.json | 21 | 0 | 0 | 0 | 0 | 0 | 19 | 5 |
| 144 | moon_leaper.json | 21 | 0 | 0 | 0 | 0 | 0 | 28 | 4 |
| 145 | moon_scale.json | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 146 | bone_footman.json | 19 | 0 | 0 | 0 | 0 | 0 | 184 | 2 |
| 147 | bone_standard.json | 19 | 0 | 0 | 0 | 0 | 0 | 61 | 4 |
| 148 | rib_archer.json | 19 | 0 | 0 | 0 | 0 | 0 | 93 | 3 |
| 149 | armor/stone_whale/chestplate_legend.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 150 | ash_smith.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 151 | items/wild_banner_spear/rare.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 152 | items/wild_lure_staff/rare.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 153 | items/wild_mourning_bell_staff/rare.json | 18 | 0 | 6 | 0 | 0 | 0 | 0 | 0 |
| 154 | pilgrim_hand.json | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 5 |
| 155 | lord_rib.json | 16 | 0 | 0 | 0 | 0 | 0 | 187 | 3 |
| 156 | armor/stone_whale/chestplate_rare.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 157 | armor/tide_shell/chestplate.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 158 | armor/tide_shell/chestplate_legend.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 159 | armor/tide_shell/chestplate_rare.json | 15 | 0 | 5 | 0 | 0 | 0 | 0 | 0 |
| 160 | ash_lizard.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 161 | peak_fledgling.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 162 | shadow_embracer.json | 15 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 163 | armor/wild_lurelight/chestplate.json | 14 | 0 | 4 | 0 | 0 | 0 | 54 | 0 |
| 164 | armor/wild_lurelight/chestplate_rare.json | 14 | 0 | 4 | 0 | 0 | 0 | 54 | 0 |
| 165 | abyss_lightning_idol_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 78 | 2 |
| 166 | abyss_rib_guard_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 69 | 2 |
| 167 | abyss_salt_husk_s1.json | 13 | 0 | 0 | 0 | 0 | 0 | 77 | 2 |
| 168 | armor/wayfarer/chestplate.json | 13 | 0 | 0 | 0 | 0 | 0 | 260 | 0 |
| 169 | armor/peak_crossing/helmet.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 170 | armor/stone_whale/helmet.json | 12 | 0 | 4 | 0 | 0 | 0 | 15 | 0 |
| 171 | armor/stone_whale/helmet_legend.json | 12 | 0 | 4 | 0 | 0 | 0 | 13 | 0 |
| 172 | armor/stone_whale/helmet_rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 15 | 0 |
| 173 | ember_mole.json | 12 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 174 | items/box_tower/common.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 175 | items/whale_bell_staff/rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 176 | items/wild_fortress_greatsword/rare.json | 12 | 0 | 4 | 0 | 0 | 0 | 1 | 0 |
| 177 | last_bellkeeper.json | 12 | 0 | 0 | 0 | 0 | 0 | 1 | 4 |
| 178 | sand_burrower.json | 12 | 0 | 0 | 0 | 0 | 0 | 54 | 2 |
| 179 | supplicant_staff.json | 12 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| 180 | abyss_charged_moth_s1.json | 11 | 0 | 0 | 0 | 0 | 0 | 4 | 3 |
| 181 | abyss_scorched_bird_s1.json | 11 | 0 | 3 | 0 | 0 | 0 | 44 | 0 |
| 182 | abyss_scorched_bird_s3.json | 11 | 0 | 3 | 0 | 0 | 0 | 43 | 0 |
| 183 | rope_hanger.json | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 184 | tower_master.json | 11 | 0 | 0 | 0 | 0 | 0 | 1 | 3 |
| 185 | abyss_scorched_bird_s2.json | 10 | 0 | 3 | 0 | 0 | 0 | 35 | 0 |
| 186 | abyss_white_shell_keeper_s1.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 187 | ash_trail.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 188 | cinder_mite.json | 10 | 0 | 0 | 0 | 0 | 0 | 3 | 2 |
| 189 | circuit_stake.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 190 | drift_hermit.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 191 | npc_guest_tide_ferryman.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 192 | npc_guest_windmill_keeper.json | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 193 | stray_wisp.json | 10 | 0 | 0 | 0 | 0 | 0 | 15 | 2 |
| 194 | armor/divine_bellkeeper/chestplate.json | 9 | 0 | 3 | 0 | 0 | 0 | 0 | 0 |
| 195 | armor/divine_bellkeeper/chestplate_legend.json | 9 | 0 | 3 | 0 | 0 | 0 | 0 | 0 |
| 196 | items/wild_shell_bell_staff/common.json | 9 | 0 | 3 | 0 | 0 | 0 | 0 | 0 |
| 197 | horn_beetle.json | 8 | 0 | 0 | 0 | 0 | 0 | 174 | 0 |
| 198 | lamp_eye.json | 8 | 0 | 0 | 0 | 0 | 0 | 60 | 1 |
| 199 | light_swarm.json | 8 | 0 | 0 | 0 | 0 | 0 | 69 | 1 |
| 200 | tide_crab.json | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 201 | tower_eye.json | 8 | 0 | 0 | 0 | 0 | 0 | 69 | 1 |
| 202 | ceiling_walker.json | 7 | 0 | 0 | 0 | 0 | 0 | 40 | 1 |
| 203 | mist_angler.json | 7 | 0 | 0 | 0 | 0 | 0 | 29 | 2 |
| 204 | armor/tide_shell/helmet_legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 205 | cinder_roller.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 206 | elder_sentinel.json | 6 | 0 | 2 | 0 | 0 | 0 | 7 | 0 |
| 207 | elder_sentry.json | 6 | 0 | 2 | 0 | 0 | 0 | 17 | 0 |
| 208 | items/wild_skylark_bow/legend.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 209 | items/wild_skylark_bow/legend_pull0.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 210 | items/wild_skylark_bow/legend_pull1.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 211 | items/wild_skylark_bow/legend_pull2.json | 6 | 0 | 2 | 0 | 0 | 0 | 0 | 0 |
| 212 | tide_heart_p2.json | 6 | 0 | 0 | 0 | 0 | 0 | 123 | 0 |
| 213 | tide_heart_p3.json | 6 | 0 | 0 | 0 | 0 | 0 | 122 | 0 |
| 214 | watcher.json | 6 | 0 | 0 | 0 | 0 | 0 | 137 | 0 |
| 215 | cliff_creeper.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 216 | king_ash_c1.json | 5 | 0 | 0 | 0 | 0 | 0 | 24 | 4 |
| 217 | king_bone_c1.json | 5 | 0 | 0 | 0 | 0 | 0 | 26 | 4 |
| 218 | king_salt_c1.json | 5 | 0 | 0 | 0 | 0 | 0 | 25 | 4 |
| 219 | king_shadow_c0.json | 5 | 0 | 0 | 0 | 0 | 0 | 26 | 4 |
| 220 | king_shadow_c1.json | 5 | 0 | 0 | 0 | 0 | 0 | 27 | 4 |
| 221 | king_storm_c1.json | 5 | 0 | 0 | 0 | 0 | 0 | 29 | 4 |
| 222 | moon_core.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 223 | moss_roller.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 224 | rift_armor.json | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 225 | scorched_warden.json | 5 | 0 | 0 | 0 | 0 | 0 | 9 | 1 |
| 226 | tide_heart_p1.json | 5 | 0 | 0 | 0 | 0 | 0 | 115 | 0 |
| 227 | armor/wild_oilskin/chestplate.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 228 | bone_roller.json | 4 | 0 | 0 | 0 | 0 | 0 | 90 | 0 |
| 229 | king_ash_c0.json | 4 | 0 | 0 | 0 | 0 | 0 | 26 | 3 |
| 230 | king_bone_c0.json | 4 | 0 | 0 | 0 | 0 | 0 | 23 | 3 |
| 231 | king_salt_c0.json | 4 | 0 | 0 | 0 | 0 | 0 | 26 | 3 |
| 232 | king_storm_c0.json | 4 | 0 | 0 | 0 | 0 | 0 | 20 | 3 |
| 233 | moss_crawler.json | 4 | 0 | 0 | 0 | 0 | 0 | 97 | 0 |
| 234 | pilgrim_eye.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 235 | shade_strider.json | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |
| 236 | young_thunder_wing.json | 4 | 0 | 0 | 0 | 0 | 0 | 81 | 0 |
| 237 | abyss_lightning_idol_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 73 | 0 |
| 238 | abyss_lightning_idol_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 79 | 0 |
| 239 | abyss_rib_guard_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 67 | 0 |
| 240 | abyss_rib_guard_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 74 | 0 |
| 241 | abyss_salt_husk_s2.json | 3 | 0 | 0 | 0 | 0 | 0 | 72 | 0 |
| 242 | abyss_salt_husk_s3.json | 3 | 0 | 0 | 0 | 0 | 0 | 77 | 0 |
| 243 | armor/stone_whale/chestplate.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 244 | armor/tide_shell/helmet.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 245 | armor/tide_shell/helmet_rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 246 | bell_mourner.json | 3 | 0 | 0 | 0 | 0 | 0 | 63 | 0 |
| 247 | bone_sentinel.json | 3 | 0 | 1 | 0 | 0 | 0 | 12 | 0 |
| 248 | bone_sentinel_shield.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 249 | items/tide_shell_trident/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 250 | items/wild_chain_greatsword/rare.json | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 |
| 251 | plume_warden.json | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |
| 252 | thunder_wing_mid.json | 3 | 0 | 0 | 0 | 0 | 0 | 65 | 0 |
| 253 | armor/wild_hanged/chestplate.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 254 | armor/wild_hanged/chestplate_legend.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 255 | armor/wild_windweave/boots.json | 2 | 0 | 0 | 0 | 0 | 0 | 52 | 0 |
| 256 | armor/wild_windweave/boots_rare.json | 2 | 0 | 0 | 0 | 0 | 0 | 52 | 0 |
| 257 | bell_hare.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 258 | full_keeper.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 259 | moss_dead.json | 2 | 0 | 0 | 0 | 0 | 0 | 54 | 0 |
| 260 | pilgrim_plume.json | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| 261 | stone_whale_heart_p3.json | 2 | 0 | 0 | 0 | 0 | 0 | 55 | 0 |
| 262 | thunder_wing_far.json | 2 | 0 | 0 | 0 | 0 | 0 | 58 | 0 |
| 263 | tide_beckoner.json | 2 | 0 | 0 | 0 | 12 | 0 | 56 | 0 |
| 264 | abyss_charged_moth_s2.json | 1 | 0 | 0 | 0 | 0 | 0 | 5 | 1 |
| 265 | abyss_charged_moth_s3.json | 1 | 0 | 0 | 0 | 0 | 0 | 8 | 1 |
| 266 | armor/wild_soot/chestplate.json | 1 | 0 | 0 | 0 | 0 | 0 | 22 | 0 |
| 267 | armor/wild_soot/chestplate_rare.json | 1 | 0 | 0 | 0 | 0 | 0 | 22 | 0 |
| 268 | armor/wild_stargazer/helmet.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 269 | armor/wild_stargazer/helmet_legend.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 270 | ash_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 271 | bone_centipede.json | 1 | 0 | 0 | 0 | 0 | 0 | 3 | 1 |
| 272 | crystal_lizard.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 273 | dust_crawler.json | 1 | 0 | 0 | 0 | 0 | 0 | 29 | 0 |
| 274 | elder_thrall.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 275 | ember_titan.json | 1 | 0 | 0 | 0 | 0 | 0 | 3 | 1 |
| 276 | ember_titan_mid.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 277 | lord_ash.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 278 | mount_horn_beast.json | 1 | 0 | 0 | 0 | 0 | 0 | 10 | 1 |
| 279 | npc_abyss_porter_prop_back.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 280 | npc_guest_camp_pilgrim_prop_back.json | 1 | 0 | 0 | 0 | 0 | 0 | 23 | 0 |
| 281 | past_shadow.json | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| 282 | stone_whale.json | 1 | 0 | 0 | 0 | 0 | 0 | 37 | 0 |
| 283 | stone_whale_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 284 | storm_heart_p2.json | 1 | 0 | 0 | 0 | 0 | 0 | 27 | 0 |
| 285 | storm_heart_p3.json | 1 | 0 | 0 | 0 | 0 | 0 | 34 | 0 |
| 286 | storm_ram.json | 1 | 0 | 0 | 0 | 0 | 0 | 2 | 1 |

## 骨ごとの詳細 (上位 25 個)

### items/abyss_storm_hammer/legend.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| weapon | 1296 | 0 | 0 | 112 | 0 | 0 | 0 | 0 | - |

### mount_carriage.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| wheel_fl | 392 | 0 | 0 | 16 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| wheel_fr | 392 | 0 | 0 | 16 | 0 | 0 | 0 | 0 | detach(埋0%/深0.0) |
| wheel_bl | 392 | 0 | 0 | 16 | 0 | 0 | 0 | 322 | detach(埋0%/深0.0) |
| wheel_br | 392 | 0 | 0 | 16 | 0 | 0 | 0 | 333 | detach(埋0%/深0.0) |
| a_body | 690 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | - |
| a_neck | 386 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋51%/深0.4) |
| b_body | 690 | 0 | 0 | 0 | 0 | 0 | 0 | 6 | - |
| b_neck | 386 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | shallow(埋51%/深0.4) |

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
| head | 744 | 0 | 0 | 19 | 0 | 0 | 0 | 0 | shallow(埋46%/深0.3) |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋25%/深0.5) |

### npc_guest_camp_mapmaker.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 362 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| head | 628 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋40%/深0.5) |
| hem | 140 | 0 | 0 | 19 | 0 | 0 | 0 | 0 | ok(埋53%/深0.4) |

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

### npc_guest_camp_pilgrim.json

| 骨 | 三角形 | 開いた辺 | 輪 | 非多様体 | 向き違い | つぶれ | 逆 | 透け | 付け根 |
|---|---|---|---|---|---|---|---|---|---|
| body | 370 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | - |
| head | 712 | 0 | 0 | 0 | 0 | 0 | 0 | 52 | ok(埋57%/深0.4) |
| hem | 140 | 0 | 0 | 20 | 0 | 0 | 0 | 0 | ok(埋25%/深0.5) |

## キューブの geo (低ポリが無いときの戻り先・防具・武器の元モデル)

109 個を、外から撃った光線の最初の当たりが裏面かどうかで検査 (tools/fix_geo_face_sides.holes)。透けあり 0 個。

## ブロックモデル

面の欠け・宙に浮く部品なし。
