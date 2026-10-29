package jp.revati.skyabyss.story.ch6.gear;

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

// 六章「月」の装備の登録 (docs/design/03 の 12.2)。品・防具の素材・素材の品 (月の欠片・月の心核・共鳴の欠片)・図鑑の表。
// 表と数値は MoonGear。深淵の装備 (abyss/gear/AbyssGearSetup) と同じ形
public final class MoonGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    // ---------------------------------------------------------------- 素材
    public static final DeferredItem<Item> SHARD = ITEMS.register("moon_fragment",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), 0xC8D8F4, "item.skyabyss.moon_fragment.desc", false));
    public static final DeferredItem<Item> CORE = ITEMS.register("moon_heart",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 0xE8F0FF, "item.skyabyss.moon_heart.desc", true));
    public static final DeferredItem<Item> RESONANCE_SHARD = ITEMS.register("resonance_shard_moon",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xA8C4FF, "item.skyabyss.resonance_shard_moon.desc", false));

    // 月渡りの装束の素材: 防御 5/9/7/5 = 26、強靭 4 (4 部位 16。2 部位で +4 して 20)、ノックバック耐性 0.05。月の欠片で直す
    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("moon_walker", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, MoonGear.ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, MoonGear.ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, MoonGear.ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, MoonGear.ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 11)),
            16,
            SoundEvents.ARMOR_EQUIP_CHAIN,
            () -> Ingredient.of(SHARD.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moon_walker"))),
            MoonGear.ARMOR_TOUGHNESS,
            MoonGear.ARMOR_KNOCKBACK));

    // ---------------------------------------------------------------- 図鑑の 12 品・別枠のヒロイック 5 本と月の涙
    public static final DeferredItem<Item> SWORD = ITEMS.register("moon_light_sword",
            () -> new MoonGearItems.Weapon(new Item.Properties().rarity(Rarity.RARE), MoonGear.SWORD_ATTACK, MoonGear.SWORD_SPEED, GearTier.RARE,
                    "item.skyabyss.moon_light_sword.desc"));
    public static final DeferredItem<Item> HELMET = ITEMS.register("moon_walker_helmet",
            () -> new MoonWalkerArmorItem(ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CHESTPLATE = ITEMS.register("moon_walker_chestplate",
            () -> new MoonWalkerArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> LEGGINGS = ITEMS.register("moon_walker_leggings",
            () -> new MoonWalkerArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> BOOTS = ITEMS.register("moon_walker_boots",
            () -> new MoonWalkerArmorItem(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> HAMMER = ITEMS.register("moon_scale_hammer",
            () -> new MoonGearItems.Weapon(new Item.Properties().rarity(Rarity.RARE), MoonGear.HAMMER_ATTACK, MoonGear.HAMMER_SPEED, GearTier.RARE,
                    "item.skyabyss.moon_scale_hammer.desc"));
    public static final DeferredItem<Item> STAFF = ITEMS.register("moon_tide_staff",
            () -> new MoonGearItems.Weapon(new Item.Properties().rarity(Rarity.RARE), MoonGear.STAFF_ATTACK, MoonGear.STAFF_SPEED, GearTier.RARE,
                    "item.skyabyss.moon_tide_staff.desc"));
    public static final DeferredItem<Item> BOW = ITEMS.register("moon_waning_bow",
            () -> new MoonGearItems.Bow(new Item.Properties().rarity(Rarity.RARE).durability(900), MoonGear.BOW_MULTIPLIER, GearTier.RARE,
                    "item.skyabyss.moon_waning_bow.desc"));
    public static final DeferredItem<Item> DAGGER = ITEMS.register("moon_crescent_dagger",
            () -> new MoonGearItems.Weapon(new Item.Properties().rarity(Rarity.RARE), MoonGear.DAGGER_ATTACK, MoonGear.DAGGER_SPEED, GearTier.RARE,
                    "item.skyabyss.moon_crescent_dagger.desc"));
    public static final DeferredItem<Item> BELL = ITEMS.register("moon_starlight_bell_staff",
            () -> new MoonGearItems.Weapon(new Item.Properties().rarity(Rarity.RARE), MoonGear.BELL_ATTACK, MoonGear.BELL_SPEED, GearTier.RARE,
                    "item.skyabyss.moon_starlight_bell_staff.desc"));
    public static final DeferredItem<Item> BLADE = ITEMS.register("moon_waning_blade",
            () -> new MoonGearItems.Weapon(new Item.Properties().rarity(Rarity.EPIC), MoonGear.BLADE_ATTACK, MoonGear.BLADE_SPEED, GearTier.HEROIC,
                    "item.skyabyss.moon_waning_blade.desc"));
    public static final DeferredItem<Item> FULL_HAMMER = ITEMS.register("moon_full_hammer",
            () -> new MoonGearItems.Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), MoonGear.FULL_HAMMER_ATTACK, MoonGear.FULL_HAMMER_SPEED,
                    GearTier.HEROIC, "item.skyabyss.moon_full_hammer.desc"));
    public static final DeferredItem<Item> NEW_STAFF = ITEMS.register("moon_new_staff",
            () -> new MoonGearItems.Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), MoonGear.NEW_STAFF_ATTACK, MoonGear.NEW_STAFF_SPEED,
                    GearTier.HEROIC, "item.skyabyss.moon_new_staff.desc"));
    public static final DeferredItem<Item> STARFALL_BOW = ITEMS.register("moon_starfall_bow",
            () -> new MoonGearItems.Bow(new Item.Properties().rarity(Rarity.EPIC).durability(900), MoonGear.STARFALL_MULTIPLIER, GearTier.HEROIC,
                    "item.skyabyss.moon_starfall_bow.desc"));
    public static final DeferredItem<Item> UMBRA_DAGGER = ITEMS.register("moon_umbra_dagger",
            () -> new MoonGearItems.Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), MoonGear.UMBRA_ATTACK, MoonGear.UMBRA_SPEED,
                    GearTier.HEROIC, "item.skyabyss.moon_umbra_dagger.desc"));
    public static final DeferredItem<Item> HALO_BELL = ITEMS.register("moon_halo_bell_staff",
            () -> new MoonGearItems.Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), MoonGear.HALO_ATTACK, MoonGear.HALO_SPEED,
                    GearTier.HEROIC, "item.skyabyss.moon_halo_bell_staff.desc"));
    public static final DeferredItem<Item> RING = ITEMS.register("moon_full_ring",
            () -> new MoonGearItems.Trinket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), GearTier.HEROIC, "item.skyabyss.moon_full_ring.desc", true));
    public static final DeferredItem<Item> TEAR = ITEMS.register("moon_tear",
            () -> new MoonGearItems.Tear(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    private MoonGearSetup() {
    }

    public static List<DeferredItem<Item>> armor() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
    }

    public static List<DeferredItem<Item>> all() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, HAMMER, STAFF, BOW, DAGGER, BELL, BLADE, FULL_HAMMER, NEW_STAFF, STARFALL_BOW,
                UMBRA_DAGGER, HALO_BELL, RING, TEAR);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        TitanGearSets.register(MoonGear.SET);
        modEventBus.addListener(MoonGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredHolder<Item, Item> item : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, SWORD, HAMMER, STAFF, BOW, DAGGER, BELL, BLADE,
                    FULL_HAMMER, NEW_STAFF, STARFALL_BOW, UMBRA_DAGGER, HALO_BELL, RING, TEAR, SHARD, CORE, RESONANCE_SHARD)) {
                event.accept(item.get());
            }
        }
    }
}
