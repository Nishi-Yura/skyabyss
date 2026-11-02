package jp.revati.skyabyss.item.outfit;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 市の武具屋・道具屋の品 (2026-10-27 の声「ブロック以外はすべて mod で追加したアイテムで」)。バニラの盾・斧・つるはし・シャベル・
// 革と鉄の防具・ダイヤの胸当ての代わり。形と絵は tools/install/install_outfit.py (選定: 盾 A・道具 A・初めの旅装 B・鉄張り A・空石 C)。
//   旅の盾      ShieldItem (バニラの盾と同じ構え)。見た目は低ポリ (client/weapon。珊瑚の盾と同じ描き方)
//   伐り斧・鶴嘴・土掘り  鉄の段の斧・つるはし・シャベル (採集の道具。rpg/rules/Gathering がタグ minecraft:axes / pickaxes / shovels で見る)
//   初めの旅装 (wayfarer)    革の防具の数値 (1/3/2/1)。島で初めに着ている (RpgRules.starterKit)
//   鉄張りの旅装 (ironclad)  鉄の防具の数値 (2/6/5/2)。一章の支度で武具屋で買う
//   空石の胸当て (sky_stone) ダイヤの胸当ての数値 (8、強靭 2)。三章の支度「空の石」
public final class OutfitGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    private static Holder<ArmorMaterial> material(String name, int[] def, int enchant, float tough, Holder<net.minecraft.sounds.SoundEvent> sound) {
        return MATERIALS.register(name, () -> new ArmorMaterial(
                new EnumMap<>(Map.of(ArmorItem.Type.HELMET, def[0], ArmorItem.Type.CHESTPLATE, def[1], ArmorItem.Type.LEGGINGS, def[2],
                        ArmorItem.Type.BOOTS, def[3], ArmorItem.Type.BODY, def[1])),
                enchant, sound, () -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name))),
                tough, 0f));
    }

    public static final Holder<ArmorMaterial> WAYFARER = material("wayfarer", new int[] {1, 3, 2, 1}, 15, 0f, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Holder<ArmorMaterial> IRONCLAD = material("ironclad", new int[] {2, 6, 5, 2}, 9, 0f, SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Holder<ArmorMaterial> SKY_STONE = material("sky_stone", new int[] {3, 8, 6, 3}, 10, 2f, SoundEvents.ARMOR_EQUIP_DIAMOND);

    private static DeferredItem<Item> armor(String set, Holder<ArmorMaterial> mat, ArmorItem.Type type, int durability, Rarity rarity) {
        return ITEMS.register(set + "_" + type.getName(),
                () -> new OutfitArmorItem(set, mat, type, new Item.Properties().durability(type.getDurability(durability)).rarity(rarity)));
    }

    public static final DeferredItem<Item> WAYFARER_HELMET = armor("wayfarer", WAYFARER, ArmorItem.Type.HELMET, 5, Rarity.COMMON);
    public static final DeferredItem<Item> WAYFARER_CHESTPLATE = armor("wayfarer", WAYFARER, ArmorItem.Type.CHESTPLATE, 5, Rarity.COMMON);
    public static final DeferredItem<Item> WAYFARER_LEGGINGS = armor("wayfarer", WAYFARER, ArmorItem.Type.LEGGINGS, 5, Rarity.COMMON);
    public static final DeferredItem<Item> WAYFARER_BOOTS = armor("wayfarer", WAYFARER, ArmorItem.Type.BOOTS, 5, Rarity.COMMON);
    public static final DeferredItem<Item> IRONCLAD_HELMET = armor("ironclad", IRONCLAD, ArmorItem.Type.HELMET, 15, Rarity.COMMON);
    public static final DeferredItem<Item> IRONCLAD_CHESTPLATE = armor("ironclad", IRONCLAD, ArmorItem.Type.CHESTPLATE, 15, Rarity.COMMON);
    public static final DeferredItem<Item> IRONCLAD_LEGGINGS = armor("ironclad", IRONCLAD, ArmorItem.Type.LEGGINGS, 15, Rarity.COMMON);
    public static final DeferredItem<Item> IRONCLAD_BOOTS = armor("ironclad", IRONCLAD, ArmorItem.Type.BOOTS, 15, Rarity.COMMON);
    public static final DeferredItem<Item> SKY_STONE_CHESTPLATE = armor("sky_stone", SKY_STONE, ArmorItem.Type.CHESTPLATE, 33, Rarity.UNCOMMON);

    public static final DeferredItem<Item> TRAVEL_SHIELD = ITEMS.register("travel_shield",
            () -> new OutfitShieldItem(new Item.Properties().durability(336)));
    public static final DeferredItem<Item> GATHER_AXE = ITEMS.register("gather_axe",
            () -> new AxeItem(Tiers.IRON, new Item.Properties().attributes(AxeItem.createAttributes(Tiers.IRON, 5.0f, -3.1f))));
    public static final DeferredItem<Item> GATHER_PICKAXE = ITEMS.register("gather_pickaxe",
            () -> new PickaxeItem(Tiers.IRON, new Item.Properties().attributes(DiggerItem.createAttributes(Tiers.IRON, 1.0f, -2.8f))));
    public static final DeferredItem<Item> GATHER_SHOVEL = ITEMS.register("gather_shovel",
            () -> new ShovelItem(Tiers.IRON, new Item.Properties().attributes(ShovelItem.createAttributes(Tiers.IRON, 1.5f, -3.0f))));

    // 店・クエスト・初めの持ち物が名前で引く (shop/*.json の item もこの ID)
    public static final List<String> IDS = List.of("travel_shield", "gather_axe", "gather_pickaxe", "gather_shovel",
            "wayfarer_helmet", "wayfarer_chestplate", "wayfarer_leggings", "wayfarer_boots",
            "ironclad_helmet", "ironclad_chestplate", "ironclad_leggings", "ironclad_boots", "sky_stone_chestplate");

    private OutfitGear() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
    }
}
