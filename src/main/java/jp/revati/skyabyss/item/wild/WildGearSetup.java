package jp.revati.skyabyss.item.wild;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 野の装備の登録と引き (docs/design/51)。表 (WildGearTable) の行ごとに品を作る。防具は一式ごとに素材と 4 部位
public final class WildGearSetup {
    private WildGearSetup() {
    }

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);
    public static final Map<String, DeferredItem<Item>> WEAPON_ITEMS = new LinkedHashMap<>();
    public static final Map<String, List<DeferredItem<Item>>> ARMOR_ITEMS = new LinkedHashMap<>();
    public static final Map<String, WildGearTypes.Weapon> WEAPON_ROWS = new HashMap<>();
    public static final Map<String, WildGearTypes.ArmorSet> ARMOR_ROWS = new HashMap<>();

    public static void register(IEventBus bus) {
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            WEAPON_ROWS.put(w.id(), w);
            WEAPON_ITEMS.put(w.id(), ITEMS.register(w.id(), () -> w.isBow()
                    ? new WildGearItems.Bow(props(w.tier()).durability(900).attributes(passives(w.fx())), w)
                    : new WildGearItems.Weapon(props(w.tier()), w)));
        }
        for (WildGearTypes.ArmorSet a : WildGearTable.ARMORS) {
            ARMOR_ROWS.put(a.id(), a);
            Holder<ArmorMaterial> material = MATERIALS.register(a.id(), () -> new ArmorMaterial(
                    new EnumMap<>(Map.of(
                            ArmorItem.Type.HELMET, a.defense()[0],
                            ArmorItem.Type.CHESTPLATE, a.defense()[1],
                            ArmorItem.Type.LEGGINGS, a.defense()[2],
                            ArmorItem.Type.BOOTS, a.defense()[3],
                            ArmorItem.Type.BODY, a.defense()[1])),
                    12,
                    a.role().equals("heavy") ? SoundEvents.ARMOR_EQUIP_IRON : a.role().equals("mystic") ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.ARMOR_EQUIP_CHAIN,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, a.id()))),
                    (float) a.toughness(),
                    a.role().equals("heavy") ? 0.05f : 0f));
            List<DeferredItem<Item>> pieces = new ArrayList<>();
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
                pieces.add(ITEMS.register(a.id() + "_" + type.getName(), () -> new WildArmorItem(material, type, props(a.tier()), a)));
            }
            ARMOR_ITEMS.put(a.id(), pieces);
        }
        ITEMS.register(bus);
        MATERIALS.register(bus);
    }

    public static Item.Properties props(GearTier tier) {
        return new Item.Properties().stacksTo(1).rarity(switch (tier) {
            case UNCOMMON -> Rarity.UNCOMMON;
            case RARE -> Rarity.RARE;
            default -> Rarity.EPIC;
        }).fireResistant();
    }

    // 手に持つ間の数値の能力 (体力・マナ・押し返し・間合い・移動・強靭) を、品の修飾子に
    public static void addPassives(ItemAttributeModifiers.Builder b, List<WildEffect.Fx> fx) {
        for (WildEffect.Fx f : fx) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild/" + f.kind().name().toLowerCase(java.util.Locale.ROOT));
            switch (f.kind()) {
                case MAX_HEALTH -> b.add(Attributes.MAX_HEALTH, new AttributeModifier(id, f.p(0), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                case MAX_MANA -> b.add(jp.revati.skyabyss.rpg.Rpg.MAX_MANA, new AttributeModifier(id, f.p(0), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                case KNOCKBACK -> b.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(id, f.p(0), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                case REACH -> b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(id, f.p(0), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                case MOVE_SPEED -> b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, f.p(0), AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
                case TOUGHNESS -> b.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, f.p(0), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                default -> {
                }
            }
        }
    }

    public static ItemAttributeModifiers passives(List<WildEffect.Fx> fx) {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        addPassives(b, fx);
        return b.build();
    }

    // ---------------------------------------------------------------- 引き
    public static WildGearTypes.Weapon weapon(ItemStack stack) {
        return stack.isEmpty() ? null : weapon(stack.getItem());
    }

    public static WildGearTypes.Weapon weapon(Item item) {
        return item instanceof WildGearItems.Weapon w ? w.row : item instanceof WildGearItems.Bow b ? b.row : null;
    }

    public static WildGearTypes.ArmorSet armorSet(ItemStack stack) {
        return stack.getItem() instanceof WildArmorItem a ? a.set : null;
    }

    public static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id));
    }

    // 由来の行のキー (ModGear.originKey が使う)
    public static String originKey(Item item) {
        String region = item instanceof WildGear g ? g.region() : "meadow";
        boolean weapon = weapon(item) != null;
        String head = weapon ? "origin_weapon" : "origin";
        return "gear.skyabyss." + head + ".wild_" + region;
    }
}
