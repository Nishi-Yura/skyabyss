package jp.revati.skyabyss.client.weapon;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// プレイヤーが持つ mod 武器の「型」(三人称の腕の構えの登録名 SKYABYSS_*)。
// 動きそのもの (構え・振り・連撃・当たる瞬間・ヒットストップ) は、品ごとに weapon/MoveSet (assets の weapon_moves/<id>.json。tools/gen/items/gen_weapon_moves.py が作る) が持つ。
// 弓 (雷翼の弓・深淵の雷弓) と盾 (珊瑚の盾) は、バニラの引き・構えの動きに MoveSet の一人称の動きを足す (WeaponClient)。
// 槍・長柄・火かき棒は、穂先が腕の向きに前へ伸びる持ち方 (tools/lib/weapon_display.py の forward) で、構えも腕を前へ出す
public enum WeaponKind {
    GREATSWORD,
    PIKE,
    GLAIVE,
    FANG,
    CLAW,
    AXE,
    HAMMER,
    POKER;

    // 武器の id (パス) → 型
    private static final Map<String, WeaponKind> BY_ITEM = Map.ofEntries(
            Map.entry("whalebone_greatsword", GREATSWORD),
            Map.entry("captain_pike", PIKE),
            Map.entry("tide_shell_trident", PIKE),
            Map.entry("coral_harpoon", PIKE),
            Map.entry("warden_glaive", GLAIVE),
            Map.entry("waterway_fang", FANG),
            Map.entry("raiju_claw", CLAW),
            Map.entry("tidecaller_claw", CLAW),
            Map.entry("ember_war_axe", AXE),
            Map.entry("smith_hammer", HAMMER),
            Map.entry("thrall_poker", POKER),
            // 深淵のアーティファクト (剣型の 3 点。弓は WeaponClient が BowExtension で登録する)
            Map.entry("abyss_greatsword", GREATSWORD),
            Map.entry("abyss_bell_staff", POKER),
            // 五章「深淵」の装備 (abyss/gear)
            Map.entry("abyss_lamp_sword", GREATSWORD),
            Map.entry("abyss_sinking_anchor", HAMMER),
            Map.entry("abyss_pulse_dagger", FANG),
            Map.entry("abyss_ash_scythe", AXE),
            // 島の祠の始めの武器 (rpg/skill/JobChoice)
            Map.entry("shrine_staff", POKER),
            Map.entry("shrine_bell_staff", POKER),
            Map.entry("shrine_dagger", FANG),
            // 章の杖と鈴杖 (item/ChapterGear)
            Map.entry("whale_staff", POKER), Map.entry("whale_bell_staff", POKER), Map.entry("tide_staff", POKER), Map.entry("tide_bell_staff", POKER),
            Map.entry("storm_staff", POKER), Map.entry("storm_bell_staff", POKER), Map.entry("ember_staff", POKER), Map.entry("ember_censer", POKER),
            // 職の章の武器と祠の始めの武器 (弓は WeaponClient が BowExtension で登録する)
            Map.entry("whale_hammer", HAMMER), Map.entry("tide_hammer", HAMMER), Map.entry("storm_hammer", HAMMER), Map.entry("ember_greatsword", GREATSWORD),
            Map.entry("ember_dagger", FANG), Map.entry("whale_dagger", FANG), Map.entry("tide_dagger", FANG), Map.entry("storm_sword", GREATSWORD), Map.entry("storm_dagger", FANG), Map.entry("shrine_sword", GREATSWORD), Map.entry("shrine_hammer", HAMMER),
            // 伝説の武器と五章の武器 (abyss/LegendGear)
            Map.entry("abyss_whale_hammer", HAMMER),
            Map.entry("abyss_whale_staff", POKER),
            Map.entry("abyss_whale_fang", FANG),
            Map.entry("abyss_whale_bell_staff", POKER),
            Map.entry("abyss_tide_sword", GREATSWORD),
            Map.entry("abyss_tide_hammer", HAMMER),
            Map.entry("abyss_tide_staff", POKER),
            Map.entry("abyss_tide_dagger", FANG),
            Map.entry("abyss_storm_sword", GREATSWORD),
            Map.entry("abyss_storm_hammer", HAMMER),
            Map.entry("abyss_storm_staff", POKER),
            Map.entry("abyss_storm_dagger", FANG),
            Map.entry("abyss_storm_bell_staff", POKER),
            Map.entry("abyss_ember_sword", GREATSWORD),
            Map.entry("abyss_ember_staff", POKER),
            Map.entry("abyss_ember_dagger", FANG),
            Map.entry("abyss_ember_bell_staff", POKER),
            Map.entry("abyss_lamp_staff", POKER),
            Map.entry("abyss_toll_bell_staff", POKER),
            // 巨神のヒロイック武器 (item/HeroicGear。弓は WeaponClient が BowExtension で登録する)
            Map.entry("whale_choir_hammer", HAMMER), Map.entry("whale_star_staff", POKER), Map.entry("whale_deep_bell_staff", POKER),
            Map.entry("tide_reef_hammer", HAMMER), Map.entry("tide_pearl_staff", POKER), Map.entry("tide_surf_bell_staff", POKER),
            Map.entry("storm_anvil_hammer", HAMMER), Map.entry("storm_cloud_staff", POKER), Map.entry("storm_thunder_bell_staff", POKER),
            Map.entry("ember_cinder_blade", GREATSWORD), Map.entry("ember_ash_knife", FANG), Map.entry("ember_kiln_staff", POKER),
            Map.entry("ember_censer_bell_staff", POKER),
            // 市の採集の道具 (item/outfit。振りの動きは持たない。持ち方と描き方だけ)
            Map.entry("gather_axe", AXE), Map.entry("gather_pickaxe", HAMMER), Map.entry("gather_shovel", HAMMER),
            // イサナの祖父の風見鶏 (雷翼を指す道具。杖のように持つ)
            Map.entry("weathercock", POKER),
            // 六章「月」の武器 (story/ch6/gear。弓は WeaponClient が BowExtension で登録する)
            Map.entry("moon_scale_hammer", HAMMER), Map.entry("moon_tide_staff", POKER), Map.entry("moon_crescent_dagger", FANG),
            Map.entry("moon_starlight_bell_staff", POKER), Map.entry("moon_waning_blade", GREATSWORD),
            Map.entry("moon_light_sword", GREATSWORD), Map.entry("moon_full_hammer", HAMMER), Map.entry("moon_new_staff", POKER),
            Map.entry("moon_umbra_dagger", FANG), Map.entry("moon_halo_bell_staff", POKER));

    public static Iterable<Map.Entry<String, WeaponKind>> items() {
        return BY_ITEM.entrySet();
    }

    public static WeaponKind of(String path) {
        return BY_ITEM.get(path);
    }

    public static WeaponKind of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "skyabyss".equals(id.getNamespace()) ? BY_ITEM.get(id.getPath()) : null;
    }
}
