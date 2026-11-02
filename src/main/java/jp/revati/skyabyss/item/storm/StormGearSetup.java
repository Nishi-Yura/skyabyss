package jp.revati.skyabyss.item.storm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.MaterialItem;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 雷翼の装備の登録 (03 の 12章)。品・素材・防具の素材・干し果の効き目・図鑑の表 (TitanGearSets)・クリエイティブのタブ。
// SkyAbyss のコンストラクタから register を呼ぶ (潮殻の TideGearSetup と同じ形)。
// 素材 4 つ (欠片・心核・干し果・鱗粉) もここで登録する (12 の 11.1節。敵・中ボス・心臓の担当は ID で引く)
public final class StormGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);

    // 静電避け (雲間の干し果): 雷蛾の放電の連鎖を受けない・受けても次へ跳ばない (判定は雷蛾の担当が ID で引く)
    public static final DeferredHolder<MobEffect, MobEffect> STATIC_WARD = EFFECTS.register("static_ward", StaticWardEffect::new);

    // 雷翼の羽衣の素材: 防御 3/8/7/3 (21)、強靭 3.5 (4 部位 14 = 03 の 9.8節)、ノックバック耐性 0.05。雷翼の羽軸で直せる
    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("thunder_wing", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, StormGear.ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, StormGear.ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, StormGear.ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, StormGear.ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 8)),
            12,
            SoundEvents.ARMOR_EQUIP_ELYTRA,
            () -> Ingredient.of(StormGearSetup.SHARD.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "thunder_wing"))),
            StormGear.ARMOR_TOUGHNESS,
            StormGear.ARMOR_KNOCKBACK));

    // ---------------------------------------------------------------- 素材
    public static final DeferredItem<Item> SHARD = ITEMS.register("thunder_wing_shard",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), 0x9FC8E8, "item.skyabyss.thunder_wing_shard.desc", false));
    public static final DeferredItem<Item> CORE = ITEMS.register("thunder_wing_core",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), 0xE8D27A,
                    "item.skyabyss.thunder_wing_core.desc", true));
    public static final DeferredItem<Item> FRUIT = ITEMS.register("cloudgap_fruit",
            () -> new MaterialItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(StormGear.FRUIT_NUTRITION).saturationModifier(StormGear.FRUIT_SATURATION).alwaysEdible()
                    .effect(() -> new MobEffectInstance(STATIC_WARD, StormGear.FRUIT_WARD_TICKS, 0), 1.0f)
                    .build()), 0xC9B8F0, "item.skyabyss.cloudgap_fruit.desc", false));
    public static final DeferredItem<Item> DUST = ITEMS.register("moth_dust",
            () -> new MaterialItem(new Item.Properties(), 0xBFE6FF, "item.skyabyss.moth_dust.desc", false));

    // ---------------------------------------------------------------- 図鑑の 10 品
    public static final DeferredItem<Item> HELMET = ITEMS.register("thunder_wing_helmet",
            () -> new ThunderWingArmorItem(ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CHESTPLATE = ITEMS.register("thunder_wing_chestplate",
            () -> new ThunderWingArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> LEGGINGS = ITEMS.register("thunder_wing_leggings",
            () -> new ThunderWingArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> BOOTS = ITEMS.register("thunder_wing_boots",
            () -> new ThunderWingArmorItem(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> BOW = ITEMS.register("thunder_wing_bow",
            () -> new ThunderWingBowItem(new Item.Properties().durability(1100).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> ANKLET = ITEMS.register("storm_anklet",
            () -> new StormTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), GearTier.EPIC,
                    "item.skyabyss.storm_anklet.desc", false));
    public static final DeferredItem<Item> CHARM = ITEMS.register("storm_charm",
            () -> new StormCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CLAW = ITEMS.register("raiju_claw",
            () -> new RaijuClawItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> GLAIVE = ITEMS.register("warden_glaive",
            () -> new WardenGlaiveItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> EYE_FEATHER = ITEMS.register("storm_eye_feather",
            () -> new StormTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), GearTier.HEROIC,
                    "item.skyabyss.storm_eye_feather.desc", true));

    private StormGearSetup() {
    }

    public static List<DeferredItem<Item>> armor() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        EFFECTS.register(modEventBus);
        TitanGearSets.register(StormGear.SET);
        modEventBus.addListener(StormGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredHolder<Item, Item> item : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, BOW, ANKLET, CHARM, CLAW, GLAIVE, EYE_FEATHER,
                    SHARD, CORE, FRUIT, DUST)) {
                event.accept(item.get());
            }
        }
    }
}
