package jp.revati.skyabyss.titan.tide.gear;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.MaterialItem;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
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

// 潮殻の装備の登録 (03 の 11章)。品・防具の素材・図鑑の表 (TitanGearSets)・クリエイティブのタブ。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModItems の一覧には手を入れない)。
// 後の巨神の装備も、この形 (<巨神>Gear に表と数値、<巨神>GearSetup に登録) で足す
public final class TideGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    // 潮殻の鎧の素材: 防御 3/8/6/3 (ダイヤと同じ 20)、強靭 2.25 (4 部位 9。2 部位の一式で +3 して 03 の 9.8節の 12)、ノックバック耐性 0.05。
    // 潮殻の殻片で直せる
    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("tide_shell", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, TideGear.ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, TideGear.ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, TideGear.ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, TideGear.ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 8)),
            12,
            SoundEvents.ARMOR_EQUIP_TURTLE,
            () -> Ingredient.of(shard()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_shell"))),
            TideGear.ARMOR_TOUGHNESS,
            TideGear.ARMOR_KNOCKBACK));

    public static final DeferredItem<Item> HELMET = ITEMS.register("tide_shell_helmet",
            () -> new TideShellArmorItem(ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CHESTPLATE = ITEMS.register("tide_shell_chestplate",
            () -> new TideShellArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> LEGGINGS = ITEMS.register("tide_shell_leggings",
            () -> new TideShellArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> BOOTS = ITEMS.register("tide_shell_boots",
            () -> new TideShellArmorItem(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> TRIDENT = ITEMS.register("tide_shell_trident",
            () -> new TideTridentItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> SHIELD = ITEMS.register("coral_shield",
            () -> new CoralShieldItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CHARM = ITEMS.register("tide_chime_charm",
            () -> new TideChimeCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> CLAW = ITEMS.register("tidecaller_claw",
            () -> new TidecallerClawItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> HARPOON = ITEMS.register("coral_harpoon",
            () -> new CoralHarpoonItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> PEARL = ITEMS.register("moontide_pearl",
            () -> new TideTrinketItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), GearTier.LEGENDARY,
                    "item.skyabyss.moontide_pearl.desc", true));
    public static final DeferredItem<Item> CORE = ITEMS.register("tide_shell_core",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), 0xF08A9C,
                    "item.skyabyss.tide_shell_core.desc", true));

    private TideGearSetup() {
    }

    // 潮殻の殻片 (背中の敵の担当の登録)
    public static Item shard() {
        return TideMobSetup.TIDE_SHELL_SHARD.get();
    }

    public static List<DeferredItem<Item>> armor() {
        return List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        TitanGearSets.register(TideGear.SET);
        modEventBus.addListener(TideGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredHolder<Item, Item> item : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, TRIDENT, SHIELD, CHARM, CLAW, HARPOON, PEARL, CORE)) {
                event.accept(item.get());
            }
        }
    }
}
