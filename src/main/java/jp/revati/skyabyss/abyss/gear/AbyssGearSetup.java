package jp.revati.skyabyss.abyss.gear;

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

// 五章「深淵」の装備の登録 (03 の 12 章)。品・防具の素材・素材の品 (錨鉄・心核・共鳴の欠片)・図鑑の表・タブ。
// 表と数値は AbyssGear。巨神の装備 (titan/ember/gear/EmberGearSetup) と同じ形
public final class AbyssGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    // ---------------------------------------------------------------- 素材
    // 錨鉄: 深淵の装備の強化・修理の燃料。レア以下を分解すると戻る
    public static final DeferredItem<Item> SHARD = ITEMS.register("abyss_anchor_iron",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), 0xB7A6D8, "item.skyabyss.abyss_anchor_iron.desc", false));
    public static final DeferredItem<Item> CORE = ITEMS.register("abyss_core",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 0xC79BFF, "item.skyabyss.abyss_core.desc", true));
    // 共鳴の欠片 (深淵): 深淵のアーティファクト 3 つとの交換 (30 個)
    public static final DeferredItem<Item> RESONANCE_SHARD = ITEMS.register("resonance_shard_abyss",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xB48CFF, "item.skyabyss.resonance_shard_abyss.desc", false));

    // 淵潜りの鎧の素材: 防御 4/9/6/4 = 23、強靭 3.75 (4 部位 15。2 部位で +3 して 18)、ノックバック耐性 0.05。錨鉄で直す
    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("abyss_diver", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, AbyssGear.ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, AbyssGear.ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, AbyssGear.ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, AbyssGear.ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 10)),
            15,
            SoundEvents.ARMOR_EQUIP_CHAIN,
            () -> Ingredient.of(SHARD.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_diver"))),
            AbyssGear.ARMOR_TOUGHNESS,
            AbyssGear.ARMOR_KNOCKBACK));

    // ---------------------------------------------------------------- 図鑑の 10 品
    public static final DeferredItem<Item> HELMET = ITEMS.register("abyss_diver_helmet",
            () -> new AbyssDiverArmorItem(ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CHESTPLATE = ITEMS.register("abyss_diver_chestplate",
            () -> new AbyssDiverArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> LEGGINGS = ITEMS.register("abyss_diver_leggings",
            () -> new AbyssDiverArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> BOOTS = ITEMS.register("abyss_diver_boots",
            () -> new AbyssDiverArmorItem(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> LAMP_SWORD = ITEMS.register("abyss_lamp_sword",
            () -> new LampSwordItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> LISTENER = ITEMS.register("abyss_pulse_listener",
            () -> new AbyssTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), GearTier.EPIC,
                    "item.skyabyss.abyss_pulse_listener.desc", false));
    public static final DeferredItem<Item> CHARM = ITEMS.register("abyss_lantern_charm",
            () -> new LanternCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> ANCHOR = ITEMS.register("abyss_sinking_anchor",
            () -> new SinkingAnchorItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> DAGGER = ITEMS.register("abyss_pulse_dagger",
            () -> new PulseDaggerItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> PLUMB = ITEMS.register("abyss_surveyor_plumb",
            () -> new AbyssTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), GearTier.HEROIC,
                    "item.skyabyss.abyss_surveyor_plumb.desc", true));

    private AbyssGearSetup() {
    }

    public static List<DeferredItem<Item>> armor() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
    }

    public static List<DeferredItem<Item>> all() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, LAMP_SWORD, LISTENER, CHARM, ANCHOR, DAGGER, PLUMB);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        TitanGearSets.register(AbyssGear.SET);
        AbyssGearEvents.init();
        modEventBus.addListener(AbyssGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredHolder<Item, Item> item : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, LAMP_SWORD, LISTENER, CHARM, ANCHOR, DAGGER, PLUMB,
                    SHARD, CORE, RESONANCE_SHARD)) {
                event.accept(item.get());
            }
        }
    }
}
