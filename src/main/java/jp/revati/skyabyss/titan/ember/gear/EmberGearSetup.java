package jp.revati.skyabyss.titan.ember.gear;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.MaterialItem;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.ember.EmberIds;
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

// 余燼の装備の登録 (13 の 11.2節の担当 G、03 の 12章)。品・防具の素材・素材の品・角杯・図鑑の表 (TitanGearSets)・クリエイティブのタブ。
// 名前はすべて EmberIds から取る (ID の約束)。SkyAbyss のコンストラクタから register を呼ぶ。
// 潮殻 (titan/tide/gear) と同じ形: 表と数値は EmberGear、登録はここ
public final class EmberGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    // ---------------------------------------------------------------- 素材 (13 の 6章)
    public static final DeferredItem<Item> SHARD = ITEMS.register(EmberIds.EMBER_SHARD,
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant(), 0xF08A4B, "item.skyabyss.ember_shard.desc", false));
    public static final DeferredItem<Item> CORE = ITEMS.register(EmberIds.EMBER_CORE,
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), 0xFF9A3C,
                    "item.skyabyss.ember_core.desc", true));
    public static final DeferredItem<Item> SPARK_DUST = ITEMS.register(EmberIds.SPARK_DUST,
            () -> new MaterialItem(new Item.Properties().fireResistant(), 0xF2B53A, "item.skyabyss.spark_dust.desc", false));
    public static final DeferredItem<Item> CINDER_HUSK = ITEMS.register(EmberIds.CINDER_HUSK,
            () -> new MaterialItem(new Item.Properties().fireResistant(), 0xB06A48, "item.skyabyss.cinder_husk.desc", false));
    public static final DeferredItem<Item> HORN = ITEMS.register(EmberIds.EMBER_HORN,
            () -> new EmberHornItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).fireResistant()));

    // 余燼の鎧の素材: 防御 4/8/6/4 = 22、強靭 3.25 (4 部位 13。2 部位の一式で +3 して 03 の 9.8節の 16)、ノックバック耐性 0.05。
    // 余燼の欠片で直せる。重ねて着たときのバニラの絵 (立体を描けないとき) は textures/models/armor/ember_layer_{1,2}.png
    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("ember", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, EmberGear.ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, EmberGear.ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, EmberGear.ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, EmberGear.ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 9)),
            15,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(SHARD.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember"))),
            EmberGear.ARMOR_TOUGHNESS,
            EmberGear.ARMOR_KNOCKBACK));

    // ---------------------------------------------------------------- 図鑑の 10 品
    public static final DeferredItem<Item> HELMET = ITEMS.register(EmberIds.EMBER_HELMET,
            () -> new EmberArmorItem(ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> CHESTPLATE = ITEMS.register(EmberIds.EMBER_CHESTPLATE,
            () -> new EmberArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> LEGGINGS = ITEMS.register(EmberIds.EMBER_LEGGINGS,
            () -> new EmberArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> BOOTS = ITEMS.register(EmberIds.EMBER_BOOTS,
            () -> new EmberArmorItem(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> WAR_AXE = ITEMS.register(EmberIds.EMBER_WAR_AXE,
            () -> new EmberWarAxeItem(new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> MANTLE = ITEMS.register(EmberIds.EMBER_MANTLE,
            () -> new EmberTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(), GearTier.RARE,
                    "item.skyabyss.ember_mantle.desc", false));
    public static final DeferredItem<Item> CHARM = ITEMS.register(EmberIds.KINDLING_CHARM,
            () -> new KindlingCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> HAMMER = ITEMS.register(EmberIds.SMITH_HAMMER,
            () -> new SmithHammerItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> POKER = ITEMS.register(EmberIds.THRALL_POKER,
            () -> new ThrallPokerItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> LANTERN = ITEMS.register(EmberIds.EMBER_LANTERN,
            () -> new EmberTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), GearTier.LEGENDARY,
                    "item.skyabyss.ember_lantern.desc", true));

    private EmberGearSetup() {
    }

    public static List<DeferredItem<Item>> armor() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        TitanGearSets.register(EmberGear.SET);
        modEventBus.addListener(EmberGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredHolder<Item, Item> item : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, WAR_AXE, MANTLE, CHARM, HAMMER, POKER, LANTERN,
                    HORN, SHARD, CORE, SPARK_DUST, CINDER_HUSK)) {
                event.accept(item.get());
            }
        }
    }
}
