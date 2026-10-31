package jp.revati.skyabyss.item.wild;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.wild.WildEffect.Kind;
import jp.revati.skyabyss.rpg.skill.Job;

import static jp.revati.skyabyss.item.wild.WildEffect.fx;

// 野の装備の表 (docs/design/51)。tools/gen/wild_gear/gen_wild_gear.py が spec.py から作る。手で直さない
public final class WildGearTable {
    private WildGearTable() {
    }

    // 武器 102 本: ID・職・種類・階級・必要 Lv・土地・攻撃力・速さ・矢の倍率 (弓だけ)・能力
    public static final List<WildGearTypes.Weapon> WEAPONS = List.of(
            weapon("wild_guard_sword", Job.WARRIOR, "sword", GearTier.UNCOMMON, 4, "meadow", 5.61, 1.5, 0, List.of(fx(Kind.MAX_HEALTH, 2))),
            weapon("wild_banner_spear", Job.WARRIOR, "spear", GearTier.RARE, 9, "meadow", 7.42, 1.3, 0, List.of(fx(Kind.REACH, 1), fx(Kind.AURA_DAMAGE, 0.06, 6))),
            weapon("wild_quarry_hammer", Job.SMITH, "hammer", GearTier.UNCOMMON, 4, "meadow", 8.86, 0.95, 0, List.of(fx(Kind.KNOCKBACK, 0.5))),
            weapon("wild_tusk_axe", Job.SMITH, "axe", GearTier.RARE, 9, "hills", 8.3, 1.1, 0, List.of(fx(Kind.SPRINT_BONUS, 0.25))),
            weapon("wild_scribe_staff", Job.MAGE, "staff", GearTier.UNCOMMON, 4, "meadow", 7.01, 1.2, 0, List.of(fx(Kind.MANA_REGEN, 0.1))),
            weapon("wild_moss_staff", Job.MAGE, "staff", GearTier.RARE, 9, "hills", 7.89, 1.2, 0, List.of(fx(Kind.POISON, 60, 0))),
            weapon("wild_crow_bow", Job.ARCHER, "bow", GearTier.UNCOMMON, 4, "meadow", 0, 0, 1.01, List.of(fx(Kind.MOVE_SPEED, 0.05))),
            weapon("wild_bonespike_bow", Job.ARCHER, "bow", GearTier.RARE, 9, "hills", 0, 0, 1.12, List.of(fx(Kind.BLEED, 0.3, 60))),
            weapon("wild_rat_knife", Job.ASSASSIN, "dagger", GearTier.UNCOMMON, 4, "meadow", 4.01, 2, 0, List.of(fx(Kind.SLAYER_BEAST, 0.15))),
            weapon("wild_shell_claw", Job.ASSASSIN, "claw", GearTier.RARE, 9, "hills", 4.65, 2, 0, List.of(fx(Kind.BACKSTAB, 0.25))),
            weapon("wild_scarecrow_bell_staff", Job.SHAMAN, "bell", GearTier.UNCOMMON, 4, "meadow", 6.01, 1.4, 0, List.of(fx(Kind.SLOW, 20, 0))),
            weapon("wild_bonebird_totem", Job.SHAMAN, "totem", GearTier.RARE, 9, "hills", 7.3, 1.4, 0, List.of(fx(Kind.TEAM_HEAL_ON_HIT, 0.5, 5))),
            weapon("wild_oar_blade", Job.WARRIOR, "greatsword", GearTier.UNCOMMON, 12, "coast", 11.15, 1, 0, List.of(fx(Kind.REACH, 0.5))),
            weapon("wild_dune_sword", Job.WARRIOR, "sword", GearTier.RARE, 16, "coast", 7.88, 1.5, 0, List.of(fx(Kind.CLEAVE, 0.35, 2.5))),
            weapon("wild_ramhorn_greatsword", Job.WARRIOR, "greatsword", GearTier.EPIC, 19, "heights", 12.38, 1, 0, List.of(fx(Kind.EVERY_N_BOLT, 4, 0.7, 2))),
            weapon("wild_shipwright_maul", Job.SMITH, "maul", GearTier.UNCOMMON, 12, "coast", 10.72, 1, 0, List.of(fx(Kind.ARMOR_SHRED, 1, 4, 60))),
            weapon("wild_crab_hammer", Job.SMITH, "hammer", GearTier.RARE, 16, "coast", 13.29, 0.95, 0, List.of(fx(Kind.SLOW, 30, 1))),
            weapon("wild_conductor_axe", Job.SMITH, "axe", GearTier.EPIC, 19, "heights", 12.99, 1, 0, List.of(fx(Kind.CHAIN, 0.35, 2, 5))),
            weapon("wild_tidewatch_staff", Job.MAGE, "staff", GearTier.UNCOMMON, 12, "coast", 9.29, 1.2, 0, List.of(fx(Kind.MAX_MANA, 6))),
            weapon("wild_lure_staff", Job.MAGE, "staff", GearTier.RARE, 16, "coast", 10.42, 1.2, 0, List.of(fx(Kind.WEAKEN, 60, 0.15))),
            weapon("wild_fluff_staff", Job.MAGE, "staff", GearTier.EPIC, 19, "heights", 10.28, 1.2, 0, List.of(fx(Kind.DELAYED_BOLT, 40, 0.5, 4))),
            weapon("wild_gull_bow", Job.ARCHER, "bow", GearTier.UNCOMMON, 12, "coast", 0, 0, 1.28, List.of(fx(Kind.ARROW_SPEED, 0.2))),
            weapon("wild_nacre_bow", Job.ARCHER, "bow", GearTier.RARE, 16, "coast", 0, 0, 1.48, List.of(fx(Kind.PIERCE, 1, 0))),
            weapon("wild_eaglet_longbow", Job.ARCHER, "longbow", GearTier.EPIC, 19, "heights", 0, 0, 1.61, List.of(fx(Kind.FAR_BONUS, 15, 0.25))),
            weapon("wild_shore_dagger", Job.ASSASSIN, "dagger", GearTier.UNCOMMON, 12, "coast", 5.57, 2, 0, List.of(fx(Kind.MOVE_SPEED, 0.05))),
            weapon("wild_hermit_knife", Job.ASSASSIN, "dagger", GearTier.RARE, 16, "coast", 6.13, 2, 0, List.of(fx(Kind.FIRST_STRIKE, 0.4))),
            weapon("wild_stormwolf_fang", Job.ASSASSIN, "fang", GearTier.EPIC, 19, "heights", 6.55, 2, 0, List.of(fx(Kind.EXECUTE, 0.3, 0.35))),
            weapon("wild_shell_bell_staff", Job.SHAMAN, "bell", GearTier.UNCOMMON, 12, "coast", 7.96, 1.4, 0, List.of(fx(Kind.MAX_HEALTH, 2))),
            weapon("wild_fogbell_bell_staff", Job.SHAMAN, "bell", GearTier.RARE, 16, "coast", 8.6, 1.4, 0, List.of(fx(Kind.MARK, 0.08, 80))),
            weapon("wild_loom_bell_staff", Job.SHAMAN, "bell", GearTier.EPIC, 19, "heights", 9.62, 1.4, 0, List.of(fx(Kind.TEAM_HASTE, 0.1, 0.08, 80, 6))),
            weapon("wild_gale_glaive", Job.WARRIOR, "glaive", GearTier.RARE, 22, "heights", 10.52, 1.3, 0, List.of(fx(Kind.REACH, 1), fx(Kind.PIERCE_LINE, 0.6))),
            weapon("wild_fortress_greatsword", Job.WARRIOR, "greatsword", GearTier.EPIC, 26, "wastes", 16.19, 0.9, 0, List.of(fx(Kind.BERSERK, 0.5, 0.25))),
            weapon("wild_lavajaw_greatsword", Job.WARRIOR, "greatsword", GearTier.HEROIC, 30, "wastes", 14.51, 1, 0, List.of(fx(Kind.FIRE_TAKEN, -0.2), fx(Kind.EVERY_N_BITE, 3, 0.6, 80))),
            weapon("wild_snowsplitter_axe", Job.SMITH, "axe", GearTier.RARE, 22, "heights", 14.34, 1, 0, List.of(fx(Kind.FREEZE_STACK, 3, 20))),
            weapon("wild_bellows_hammer", Job.SMITH, "hammer", GearTier.EPIC, 26, "wastes", 15.5, 0.9, 0, List.of(fx(Kind.HEAT_STACK, 5, 1, 3))),
            weapon("wild_ashrite_maul", Job.SMITH, "maul", GearTier.HEROIC, 30, "wastes", 18.4, 0.85, 0, List.of(fx(Kind.KILL_WARD, 3, 160), fx(Kind.EVERY_N_ASHRAIN, 6, 0.8, 4, 40))),
            weapon("wild_cloudreader_staff", Job.MAGE, "staff", GearTier.RARE, 22, "heights", 11.39, 1.2, 0, List.of(fx(Kind.WEATHER, 0.2, 0.1))),
            weapon("wild_emberbug_staff", Job.MAGE, "staff", GearTier.EPIC, 26, "wastes", 11.33, 1.2, 0, List.of(fx(Kind.EMBERBUG, 0.4, 40, 80))),
            weapon("wild_warden_crown_staff", Job.MAGE, "staff", GearTier.HEROIC, 30, "heights", 11.99, 1.2, 0, List.of(fx(Kind.MANA_REGEN, 0.2), fx(Kind.FEATHERS, 0.25, 3, 0.5, 2.5))),
            weapon("wild_windcutter_bow", Job.ARCHER, "bow", GearTier.RARE, 22, "heights", 0, 0, 1.66, List.of(fx(Kind.RICOCHET, 0.5, 6))),
            weapon("wild_ashhook_bow", Job.ARCHER, "bow", GearTier.EPIC, 26, "wastes", 0, 0, 1.83, List.of(fx(Kind.PULL, 2, 30, 0))),
            weapon("wild_crater_longbow", Job.ARCHER, "longbow", GearTier.HEROIC, 30, "wastes", 0, 0, 1.82, List.of(fx(Kind.FIRE_TAKEN, -0.2), fx(Kind.ARROW_BURST, 0.6, 2.5, 60))),
            weapon("wild_frostfang_dagger", Job.ASSASSIN, "dagger", GearTier.RARE, 22, "heights", 6.59, 2, 0, List.of(fx(Kind.CRIT, 0.15, 0.8))),
            weapon("wild_cinder_claw", Job.ASSASSIN, "claw", GearTier.EPIC, 26, "wastes", 6.8, 2, 0, List.of(fx(Kind.RAMP, 0.05, 0.3, 40))),
            weapon("wild_venom_fang", Job.ASSASSIN, "fang", GearTier.HEROIC, 30, "wastes", 6.92, 2, 0, List.of(fx(Kind.BACKSTAB, 0.25), fx(Kind.BACK_VENOM, 1.2, 80))),
            weapon("wild_peak_totem", Job.SHAMAN, "totem", GearTier.RARE, 22, "heights", 10.55, 1.4, 0, List.of(fx(Kind.TEAM_GUARD, 0.08, 60, 6))),
            weapon("wild_ashsow_bell_staff", Job.SHAMAN, "bell", GearTier.EPIC, 26, "wastes", 11.66, 1.4, 0, List.of(fx(Kind.LEECH_LOWEST, 0.08, 6))),
            weapon("wild_stormdrum_totem", Job.SHAMAN, "totem", GearTier.HEROIC, 30, "heights", 11.68, 1.4, 0, List.of(fx(Kind.AURA_DAMAGE, 0.05, 6), fx(Kind.DRUM, 5, 6, 20, 2))),
            weapon("wild_delver_sword", Job.WARRIOR, "sword", GearTier.RARE, 32, "abyss_upper", 10.37, 1.5, 0, List.of(fx(Kind.DARK_BONUS, 7, 0.12))),
            weapon("wild_saltcrystal_greatsword", Job.WARRIOR, "greatsword", GearTier.EPIC, 35, "abyss_upper", 17.75, 0.95, 0, List.of(fx(Kind.ARMOR_DOWN, 3, 80))),
            weapon("wild_rope_halberd", Job.WARRIOR, "glaive", GearTier.EPIC, 38, "abyss_upper", 14.75, 1.2, 0, List.of(fx(Kind.HOOK_PULL, 120, 0.3))),
            weapon("wild_inverted_greatsword", Job.WARRIOR, "greatsword", GearTier.HEROIC, 40, "abyss_upper", 17.43, 1, 0, List.of(fx(Kind.FALL_TAKEN, -1), fx(Kind.PLUNGE, 0.8, 0.6, 3))),
            weapon("wild_pick_hammer", Job.SMITH, "hammer", GearTier.RARE, 32, "abyss_upper", 15.84, 1, 0, List.of(fx(Kind.ARMOR_PIERCE, 0.2))),
            weapon("wild_saltmill_maul", Job.SMITH, "maul", GearTier.EPIC, 35, "abyss_upper", 17.18, 0.9, 0, List.of(fx(Kind.EVERY_N_QUAKE, 4, 0.8, 3))),
            weapon("wild_blasting_axe", Job.SMITH, "axe", GearTier.EPIC, 38, "abyss_upper", 17.54, 1, 0, List.of(fx(Kind.KILL_BLAST, 0.5, 3))),
            weapon("wild_lampeater_hammer", Job.SMITH, "hammer", GearTier.HEROIC, 40, "abyss_upper", 18.43, 0.9, 0, List.of(fx(Kind.DARK_BONUS, 7, 0.15), fx(Kind.LIFESTEAL, 0.08), fx(Kind.EVERY_N_SPIT, 6, 1.2, 5))),
            weapon("wild_bonepipe_staff", Job.MAGE, "staff", GearTier.RARE, 32, "abyss_upper", 12.96, 1.2, 0, List.of(fx(Kind.CLEAVE, 0.3, 3))),
            weapon("wild_salttear_staff", Job.MAGE, "staff", GearTier.EPIC, 35, "abyss_upper", 14.05, 1.2, 0, List.of(fx(Kind.ROOT, 30, 120))),
            weapon("wild_compass_staff", Job.MAGE, "staff", GearTier.EPIC, 38, "abyss_upper", 14.35, 1.2, 0, List.of(fx(Kind.FAR_BONUS, 8, 0.2))),
            weapon("wild_mimic_heart_staff", Job.MAGE, "staff", GearTier.HEROIC, 40, "abyss_upper", 14.52, 1.2, 0, List.of(fx(Kind.MAX_HEALTH, 6), fx(Kind.HEARTBEAT, 0.06, 10))),
            weapon("wild_saltcrow_bow", Job.ARCHER, "bow", GearTier.RARE, 32, "abyss_upper", 0, 0, 1.85, List.of(fx(Kind.SPLIT_EVERY, 3, 3, 0.6))),
            weapon("wild_bonestring_bow", Job.ARCHER, "bow", GearTier.EPIC, 35, "abyss_upper", 0, 0, 1.98, List.of(fx(Kind.PIERCE, 2, 0.15))),
            weapon("wild_lantern_bow", Job.ARCHER, "bow", GearTier.EPIC, 38, "abyss_upper", 0, 0, 2.05, List.of(fx(Kind.MARK, 0.1, 120))),
            weapon("wild_hanged_longbow", Job.ARCHER, "longbow", GearTier.HEROIC, 40, "abyss_upper", 0, 0, 2.05, List.of(fx(Kind.FALL_TAKEN, -0.5), fx(Kind.HANG, 30, 0.3))),
            weapon("wild_ropecut_knife", Job.ASSASSIN, "dagger", GearTier.RARE, 32, "abyss_upper", 8.55, 2, 0, List.of(fx(Kind.LIFESTEAL, 0.05))),
            weapon("wild_bone_needle_dagger", Job.ASSASSIN, "dagger", GearTier.EPIC, 35, "abyss_upper", 7.73, 2, 0, List.of(fx(Kind.BLEED_STACK, 5, 0.6))),
            weapon("wild_ceiling_claw", Job.ASSASSIN, "claw", GearTier.EPIC, 38, "abyss_upper", 8.61, 2, 0, List.of(fx(Kind.AIR_BONUS, 0.3))),
            weapon("wild_snuffer_dagger", Job.ASSASSIN, "dagger", GearTier.HEROIC, 40, "abyss_upper", 8.29, 2, 0, List.of(fx(Kind.STEALTH, 0.5), fx(Kind.SNUFF, 0.5, 60, 20))),
            weapon("wild_mourning_bell_staff", Job.SHAMAN, "bell", GearTier.RARE, 32, "abyss_upper", 12.22, 1.4, 0, List.of(fx(Kind.TEAM_REGEN, 100, 0, 6))),
            weapon("wild_saltpillar_totem", Job.SHAMAN, "totem", GearTier.EPIC, 35, "abyss_upper", 13.25, 1.4, 0, List.of(fx(Kind.WEAKEN, 100, 0.2))),
            weapon("wild_knotted_bell_staff", Job.SHAMAN, "bell", GearTier.EPIC, 38, "abyss_upper", 11.98, 1.4, 0, List.of(fx(Kind.LINK, 100, 0.3, 6))),
            weapon("wild_lullaby_bell_staff", Job.SHAMAN, "bell", GearTier.HEROIC, 40, "abyss_upper", 13.35, 1.4, 0, List.of(fx(Kind.TEAM_GUARD_AURA, 0.06, 8), fx(Kind.LULLABY, 8, 6, 40, 10, 3))),
            weapon("wild_fulgurite_sword", Job.WARRIOR, "sword", GearTier.RARE, 42, "abyss_lower", 11.74, 1.5, 0, List.of(fx(Kind.PROC_BOLT, 0.12, 1))),
            weapon("wild_twin_spear", Job.WARRIOR, "spear", GearTier.EPIC, 44, "abyss_lower", 13.8, 1.4, 0, List.of(fx(Kind.REACH, 1), fx(Kind.BACK_THRUST, 3, 1, 3))),
            weapon("wild_chain_greatsword", Job.WARRIOR, "greatsword", GearTier.EPIC, 46, "abyss_lower", 19.63, 1, 0, List.of(fx(Kind.REACH, 2), fx(Kind.FAR_BONUS, 3, 0.15))),
            weapon("wild_meteor_greatsword", Job.WARRIOR, "greatsword", GearTier.HEROIC, 48, "moon", 22.6, 0.95, 0, List.of(fx(Kind.MAX_HEALTH, 6), fx(Kind.WARD_STACK, 6, 200, 0.1))),
            weapon("wild_cloudcleaver_greatsword", Job.WARRIOR, "greatsword", GearTier.HEROIC, 50, "cloud", 20.32, 1, 0, List.of(fx(Kind.MOVE_SPEED, 0.06), fx(Kind.WIND_SLASH, 8, 0.5))),
            weapon("wild_molten_axe", Job.SMITH, "axe", GearTier.RARE, 42, "abyss_lower", 17.93, 1, 0, List.of(fx(Kind.BURN_BONUS, 60, 0.1))),
            weapon("wild_cog_hammer", Job.SMITH, "maul", GearTier.EPIC, 44, "abyss_lower", 20.02, 0.9, 0, List.of(fx(Kind.HASTE_RAMP, 0.05, 0.25, 40))),
            weapon("wild_abyss_mace", Job.SMITH, "mace", GearTier.EPIC, 46, "abyss_lower", 21.25, 0.9, 0, List.of(fx(Kind.PULL_AOE, 2, 0.5, 2.5))),
            weapon("wild_starforge_hammer", Job.SMITH, "hammer", GearTier.HEROIC, 48, "moon", 20.99, 0.9, 0, List.of(fx(Kind.TOUGHNESS, 3), fx(Kind.EVERY_N_SPARKS, 4, 1, 3, 60))),
            weapon("wild_skysplit_axe", Job.SMITH, "axe", GearTier.HEROIC, 50, "cloud", 21.04, 0.95, 0, List.of(fx(Kind.ARMOR_PIERCE, 0.3), fx(Kind.OPENER, 0.5, 0.2, 80, 0.2))),
            weapon("wild_ballspark_staff", Job.MAGE, "staff", GearTier.RARE, 42, "abyss_lower", 14.67, 1.2, 0, List.of(fx(Kind.CHAIN, 0.3, 3, 5))),
            weapon("wild_hourglass_staff", Job.MAGE, "staff", GearTier.EPIC, 44, "abyss_lower", 15.4, 1.2, 0, List.of(fx(Kind.SLOW_BONUS, 60, 1, 0.15))),
            weapon("wild_deep_eye_staff", Job.MAGE, "staff", GearTier.EPIC, 46, "abyss_lower", 15.94, 1.2, 0, List.of(fx(Kind.CRIT, 0.25, 0.6))),
            weapon("wild_starchart_staff", Job.MAGE, "staff", GearTier.HEROIC, 48, "moon", 15.14, 1.2, 0, List.of(fx(Kind.MANA_REGEN, 0.25), fx(Kind.CONSTELLATION, 5, 2, 5))),
            weapon("wild_prism_staff", Job.MAGE, "staff", GearTier.HEROIC, 50, "cloud", 16.65, 1.2, 0, List.of(fx(Kind.COOLDOWN, 0.1), fx(Kind.PRISM, 7))),
            weapon("wild_flash_bow", Job.ARCHER, "bow", GearTier.RARE, 42, "abyss_lower", 0, 0, 1.88, List.of(fx(Kind.PROC_BOLT, 0.25, 1))),
            weapon("wild_scope_longbow", Job.ARCHER, "longbow", GearTier.EPIC, 44, "abyss_lower", 0, 0, 2.01, List.of(fx(Kind.HOLD_BONUS, 20, 0.35))),
            weapon("wild_calling_bow", Job.ARCHER, "bow", GearTier.EPIC, 46, "abyss_lower", 0, 0, 2.1, List.of(fx(Kind.HOMING, 6, 0.1))),
            weapon("wild_lighthouse_longbow", Job.ARCHER, "longbow", GearTier.HEROIC, 48, "moon", 0, 0, 2.02, List.of(fx(Kind.BEAM, 40, 24, 1.5))),
            weapon("wild_skylark_bow", Job.ARCHER, "bow", GearTier.HEROIC, 50, "cloud", 0, 0, 2.07, List.of(fx(Kind.FALL_TAKEN, -1), fx(Kind.AIR_SPLIT, 3, 0.7, 0.15))),
            weapon("wild_spark_dagger", Job.ASSASSIN, "dagger", GearTier.RARE, 42, "abyss_lower", 8.7, 2.2, 0, List.of(fx(Kind.STUN_CHANCE, 0.15, 10))),
            weapon("wild_ash_sickle_claw", Job.ASSASSIN, "claw", GearTier.EPIC, 44, "abyss_lower", 9.24, 2, 0, List.of(fx(Kind.MOVE_RAMP, 0.04, 0.2, 60, 0.3))),
            weapon("wild_fishhook_knife", Job.ASSASSIN, "dagger", GearTier.EPIC, 46, "abyss_lower", 9.82, 2, 0, List.of(fx(Kind.HOOK, 1.5, 0.25))),
            weapon("wild_mirror_dagger", Job.ASSASSIN, "dagger", GearTier.HEROIC, 48, "moon", 9.68, 2, 0, List.of(fx(Kind.REFLECT, 0.1), fx(Kind.MIRROR, 10, 1))),
            weapon("wild_cloudveil_claw", Job.ASSASSIN, "claw", GearTier.HEROIC, 50, "cloud", 10.16, 2, 0, List.of(fx(Kind.MOVE_SPEED, 0.08), fx(Kind.VANISH, 0.08, 20, 1))),
            weapon("wild_raincall_bell_staff", Job.SHAMAN, "bell", GearTier.RARE, 42, "abyss_lower", 14.08, 1.4, 0, List.of(fx(Kind.TEAM_MANA, 0.2, 80, 6))),
            weapon("wild_calming_censer", Job.SHAMAN, "censer", GearTier.EPIC, 44, "abyss_lower", 14.46, 1.4, 0, List.of(fx(Kind.SMOKE, 60, 3, 0.1))),
            weapon("wild_effigy_totem", Job.SHAMAN, "totem", GearTier.EPIC, 46, "abyss_lower", 15.71, 1.4, 0, List.of(fx(Kind.PROTECT, 0.15, 0.2, 6))),
            weapon("wild_starbell_totem", Job.SHAMAN, "totem", GearTier.HEROIC, 48, "moon", 15.34, 1.4, 0, List.of(fx(Kind.TEAM_HEALTH_AURA, 4, 8), fx(Kind.STAR_FRUIT, 6, 4, 0.1, 120, 6))),
            weapon("wild_cloudhymn_bell_staff", Job.SHAMAN, "bell", GearTier.HEROIC, 50, "cloud", 16.59, 1.4, 0, List.of(fx(Kind.REVIVE_SPEED, 2), fx(Kind.HYMN, 0.1, 0.25, 80, 8))));

    // 防具 18 式: ID・役・階級・必要 Lv・土地・部位ごとの防御 (兜・胴・脚・靴)・部位ごとの強靭・2 部位の効果・4 部位の効果
    public static final List<WildGearTypes.ArmorSet> ARMORS = List.of(
            armor("wild_oilskin", "light", GearTier.UNCOMMON, 3, "island", new int[]{2, 4, 3, 2}, 0, List.of(), List.of(fx(Kind.MOVE_SPEED, 0.05), fx(Kind.SWIM, 0.5))),
            armor("wild_belfry", "mystic", GearTier.UNCOMMON, 6, "meadow", new int[]{3, 5, 4, 2}, 0.4, List.of(), List.of(fx(Kind.MANA_REGEN, 0.15))),
            armor("wild_oldsoldier", "heavy", GearTier.RARE, 9, "meadow", new int[]{4, 7, 5, 3}, 1.8, List.of(fx(Kind.MAX_HEALTH, 4)), List.of(fx(Kind.LAST_STAND, 0.3, 60, 1200))),
            armor("wild_sailcloth", "light", GearTier.UNCOMMON, 12, "coast", new int[]{3, 6, 5, 3}, 1.2, List.of(), List.of(fx(Kind.MOVE_SPEED, 0.05), fx(Kind.BREATH, 1))),
            armor("wild_lurelight", "mystic", GearTier.RARE, 16, "coast", new int[]{4, 6, 5, 3}, 1.2, List.of(fx(Kind.MAX_MANA, 5)), List.of(fx(Kind.NIGHT_MANA, 0.25, 0.05))),
            armor("wild_ramfleece", "heavy", GearTier.EPIC, 19, "heights", new int[]{4, 8, 6, 3}, 3, List.of(fx(Kind.TOUGHNESS, 2)), List.of(fx(Kind.THORN_BOLT, 0.25, 4))),
            armor("wild_windweave", "light", GearTier.RARE, 22, "heights", new int[]{4, 6, 5, 3}, 1.9, List.of(), List.of(fx(Kind.FALL_TAKEN, -0.5), fx(Kind.MOVE_SPEED, 0.06))),
            armor("wild_ashleather", "mystic", GearTier.EPIC, 26, "wastes", new int[]{4, 7, 6, 3}, 1.9, List.of(), List.of(fx(Kind.FIRE_TAKEN, -0.25), fx(Kind.BURN_BONUS_SET, 0.1), fx(Kind.MANA_REGEN, 0.15))),
            armor("wild_fortress", "heavy", GearTier.HEROIC, 30, "wastes", new int[]{4, 8, 6, 4}, 4, List.of(fx(Kind.TOUGHNESS, 3)), List.of(fx(Kind.BULWARK, 0.05, 0.2))),
            armor("wild_delver", "light", GearTier.RARE, 32, "abyss_upper", new int[]{4, 7, 5, 3}, 2.2, List.of(), List.of(fx(Kind.FALL_TAKEN, -0.3), fx(Kind.MOVE_SPEED, 0.04), fx(Kind.NIGHT_VISION, 1))),
            armor("wild_saltplate", "heavy", GearTier.EPIC, 35, "abyss_upper", new int[]{5, 8, 6, 4}, 4, List.of(), List.of(fx(Kind.TOUGHNESS, 2), fx(Kind.THORNS, 0.2, 4))),
            armor("wild_lampkeeper", "mystic", GearTier.EPIC, 38, "abyss_upper", new int[]{4, 7, 6, 3}, 2, List.of(), List.of(fx(Kind.MANA_REGEN, 0.25), fx(Kind.LIGHT, 1))),
            armor("wild_hanged", "light", GearTier.HEROIC, 40, "abyss_upper", new int[]{4, 7, 5, 3}, 2.5, List.of(fx(Kind.MOVE_SPEED, 0.05)), List.of(fx(Kind.NOOSE, 6, 3600))),
            armor("wild_copperwire", "mid", GearTier.RARE, 42, "abyss_lower", new int[]{4, 8, 6, 4}, 3.4, List.of(), List.of(fx(Kind.LIGHTNING_TAKEN, -0.4), fx(Kind.THORN_BOLT, 0.15, 5))),
            armor("wild_soot", "light", GearTier.EPIC, 44, "abyss_lower", new int[]{4, 7, 6, 3}, 2.7, List.of(), List.of(fx(Kind.MOVE_SPEED, 0.08), fx(Kind.SNEAK_STEALTH, 0.5))),
            armor("wild_deepwarden", "heavy", GearTier.EPIC, 46, "abyss_lower", new int[]{5, 9, 7, 4}, 4.5, List.of(), List.of(fx(Kind.GRIT, 0.08, 0.16))),
            armor("wild_stargazer", "mystic", GearTier.HEROIC, 48, "moon", new int[]{4, 8, 6, 4}, 2.4, List.of(), List.of(fx(Kind.MANA_REGEN, 0.35), fx(Kind.NIGHT_DAMAGE, 0.08))),
            armor("wild_cloudknight", "heavy", GearTier.HEROIC, 50, "cloud", new int[]{5, 9, 7, 4}, 5, List.of(), List.of(fx(Kind.CLOUD_WARD, 400, 6, 0.1))));

    // 装飾の枠の品 26 品: ID・護符か・階級・必要 Lv・土地・待ち (tick、護符だけ)・能力・体に描く場所 (装飾だけ)
    public static final List<WildGearTypes.Accessory> ACCESSORIES = List.of(
            accessory("wild_guard_badge", false, GearTier.UNCOMMON, 3, "meadow", 0, List.of(fx(Kind.MAX_HEALTH, 2)), new WildGearTypes.Mount("BODY", 0f, 1.4f, -2.7f, 0f, 0f, 0f, 0.45f, 0f, 5f, 0f)),
            accessory("wild_mossball_netsuke", false, GearTier.RARE, 8, "hills", 0, List.of(fx(Kind.REGEN_TICK, 1, 100)), new WildGearTypes.Mount("BODY", -3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_herb_vial", true, GearTier.UNCOMMON, 5, "meadow", 900, List.of(fx(Kind.CHARM_HEAL, 6)), null),
            accessory("wild_float_netsuke", false, GearTier.UNCOMMON, 12, "coast", 0, List.of(fx(Kind.SWIM, 0.4), fx(Kind.BREATH, 1), fx(Kind.MOVE_SPEED, 0.03)), new WildGearTypes.Mount("BODY", 3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_wolf_tooth_necklace", false, GearTier.RARE, 16, "heights", 0, List.of(fx(Kind.CRIT, 0.08, 0.5)), new WildGearTypes.Mount("BODY", 0f, 1.4f, -2.7f, 0f, 0f, 0f, 0.45f, 0f, 5f, 0f)),
            accessory("wild_crab_backplate", false, GearTier.EPIC, 19, "coast", 0, List.of(fx(Kind.TOUGHNESS, 2), fx(Kind.THORNS, 0.15, 3)), new WildGearTypes.Mount("BODY", 0f, 4f, 3f, 0f, 180f, 0f, 0.55f, 0f, 3f, 0f)),
            accessory("wild_fog_handbell", true, GearTier.RARE, 15, "coast", 800, List.of(fx(Kind.CHARM_FOG, 8, 60)), null),
            accessory("wild_weave_bracelet", false, GearTier.RARE, 22, "heights", 0, List.of(fx(Kind.MOVE_SPEED, 0.05), fx(Kind.FALL_TAKEN, -0.3)), new WildGearTypes.Mount("RIGHT_LEG", 0f, 10.4f, 0f, 0f, 0f, 0f, 0.42f, 0f, 0f, 0f)),
            accessory("wild_emberbug_amber", false, GearTier.EPIC, 26, "wastes", 0, List.of(fx(Kind.BURN_BONUS_SET, 0.1), fx(Kind.FIRE_TAKEN, -0.15)), new WildGearTypes.Mount("BODY", -3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_ramhorn_bugle", false, GearTier.HEROIC, 30, "heights", 0, List.of(fx(Kind.BERSERK, 0.5, 0.15), fx(Kind.TOUGHNESS, 2)), new WildGearTypes.Mount("BODY", 3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_ash_talisman", true, GearTier.EPIC, 25, "wastes", 1000, List.of(fx(Kind.CHARM_CLEANSE, 100)), null),
            accessory("wild_signal_fire", true, GearTier.HEROIC, 29, "heights", 1200, List.of(fx(Kind.CHARM_RALLY, 8, 0.15, 160)), null),
            accessory("wild_plumb_line_ring", false, GearTier.RARE, 32, "abyss_upper", 0, List.of(fx(Kind.FALL_TAKEN, -0.5), fx(Kind.NIGHT_VISION, 1)), new WildGearTypes.Mount("BODY", 3.6f, 10.6f, 3.1f, 0f, 0f, 0f, 0.4f, 0f, 6f, 0f)),
            accessory("wild_salttear_earring", false, GearTier.EPIC, 35, "abyss_upper", 0, List.of(fx(Kind.MANA_REGEN, 0.15), fx(Kind.MAX_MANA, 6)), new WildGearTypes.Mount("HEAD", -4.3f, -5.2f, 1.2f, 25f, 0f, 22f, 0.6f, 0f, 0f, 0f)),
            accessory("wild_bonepipe_necklace", false, GearTier.EPIC, 38, "abyss_upper", 0, List.of(fx(Kind.DARK_BONUS, 7, 0.1)), new WildGearTypes.Mount("BODY", 0f, 1.4f, -2.7f, 0f, 0f, 0f, 0.45f, 0f, 5f, 0f)),
            accessory("wild_hangman_knot", false, GearTier.HEROIC, 40, "abyss_upper", 0, List.of(fx(Kind.NOOSE, 5, 4800), fx(Kind.MOVE_SPEED, 0.04)), new WildGearTypes.Mount("BODY", 3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_lamp_charm", true, GearTier.RARE, 33, "abyss_upper", 900, List.of(fx(Kind.CHARM_LIGHT, 10, 0.1, 200)), null),
            accessory("wild_mimic_heart_charm", true, GearTier.HEROIC, 39, "abyss_upper", 2400, List.of(fx(Kind.CHARM_HEART, 0.5)), null),
            accessory("wild_fulgurite_necklace", false, GearTier.RARE, 42, "abyss_lower", 0, List.of(fx(Kind.LIGHTNING_TAKEN, -0.3), fx(Kind.THORN_BOLT, 0.1, 5)), new WildGearTypes.Mount("BODY", 0f, 1.4f, -2.7f, 0f, 0f, 0f, 0.45f, 0f, 5f, 0f)),
            accessory("wild_ash_hourglass_charm", false, GearTier.EPIC, 44, "abyss_lower", 0, List.of(fx(Kind.COOLDOWN, 0.08)), new WildGearTypes.Mount("BODY", -3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_deep_eye_charm", false, GearTier.EPIC, 46, "abyss_lower", 0, List.of(fx(Kind.CRIT, 0.12, 0.6)), new WildGearTypes.Mount("BODY", 0f, 1.4f, -2.7f, 0f, 0f, 0f, 0.45f, 0f, 5f, 0f)),
            accessory("wild_meteor_ring", false, GearTier.HEROIC, 48, "moon", 0, List.of(fx(Kind.MAX_HEALTH, 6), fx(Kind.TOUGHNESS, 2), fx(Kind.GRIT, 0.05, 0.1)), new WildGearTypes.Mount("BODY", 3.4f, 10f, -2.9f, 0f, 0f, 0f, 0.45f, 0f, 5.4f, 0f)),
            accessory("wild_skylark_plume", false, GearTier.HEROIC, 50, "cloud", 0, List.of(fx(Kind.FALL_TAKEN, -1), fx(Kind.MOVE_SPEED, 0.08)), new WildGearTypes.Mount("HEAD", -4.3f, -5.2f, 1.2f, 25f, 0f, 22f, 0.6f, 0f, 0f, 0f)),
            accessory("wild_starchart_compass", true, GearTier.EPIC, 45, "abyss_lower", 400, List.of(fx(Kind.CHARM_BLINK, 8)), null),
            accessory("wild_moon_hourglass", true, GearTier.HEROIC, 49, "moon", 1500, List.of(fx(Kind.CHARM_STOP, 10, 60, 20)), null),
            accessory("wild_cloudhymn_slip", true, GearTier.HEROIC, 50, "cloud", 1800, List.of(fx(Kind.CHARM_HYMN, 10, 10, 0.2, 160)), null));

    private static WildGearTypes.Accessory accessory(String id, boolean charm, GearTier tier, int level, String region, int cooldown, List<WildEffect.Fx> fx,
            WildGearTypes.Mount mount) {
        return new WildGearTypes.Accessory(id, charm, tier, level, region, cooldown, fx, mount);
    }

    private static WildGearTypes.Weapon weapon(String id, Job job, String kind, GearTier tier, int level, String region, double attack, double speed,
            double bow, List<WildEffect.Fx> fx) {
        return new WildGearTypes.Weapon(id, job, kind, tier, level, region, attack, speed, bow, fx);
    }

    private static WildGearTypes.ArmorSet armor(String id, String role, GearTier tier, int level, String region, int[] defense, double toughness,
            List<WildEffect.Fx> set2, List<WildEffect.Fx> set4) {
        return new WildGearTypes.ArmorSet(id, role, tier, level, region, defense, toughness, set2, set4);
    }
}
