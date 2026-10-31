package jp.revati.skyabyss.tower.gear;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.ChapterBowItem;
import jp.revati.skyabyss.item.ChapterWeaponItem;
import jp.revati.skyabyss.item.GearItem;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// あいだの塔の報酬の品の登録 (docs/design/54 の 2.6)。塔の欠片・塔の武器 6 本・防具 3 式 12 部位・装飾 2 (どれも神器・必要 Lv 50)。
// 数値は TowerGear、効果は TowerGearEvents
public final class TowerGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    public static final DeferredItem<Item> SHARD = ITEMS.register("tower_shard",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xE8D8FF, "item.skyabyss.tower_shard.desc", false));

    public static final DeferredItem<Item> SWORD = weapon("tower_balance_sword", TowerGear.SWORD_ATTACK, TowerGear.SWORD_SPEED);
    public static final DeferredItem<Item> HAMMER = weapon("tower_pull_hammer", TowerGear.HAMMER_ATTACK, TowerGear.HAMMER_SPEED);
    public static final DeferredItem<Item> STAFF = weapon("tower_moon_staff", TowerGear.STAFF_ATTACK, TowerGear.STAFF_SPEED);
    public static final DeferredItem<Item> BOW = ITEMS.register("tower_eye_bow",
            () -> new Bow(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1200), TowerGear.BOW_MULTIPLIER, "item.skyabyss.tower_eye_bow.desc"));
    public static final DeferredItem<Item> DAGGER = weapon("tower_sigil_dagger", TowerGear.DAGGER_ATTACK, TowerGear.DAGGER_SPEED);
    public static final DeferredItem<Item> BELL = weapon("tower_clap_bell_staff", TowerGear.BELL_ATTACK, TowerGear.BELL_SPEED);

    // 防具の素材 (一式ごと。塔の欠片で直す) と、一式 → 部位の品 (頭・胴・脚・靴の順)
    static final Map<String, Holder<ArmorMaterial>> MATERIAL = new LinkedHashMap<>();
    public static final Map<String, List<DeferredItem<Item>>> ARMOR = new LinkedHashMap<>();
    static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    static {
        for (int i = 0; i < TowerGear.ARMOR_SETS.size(); i++) {
            String set = TowerGear.ARMOR_SETS.get(i);
            int[] def = TowerGear.DEFENSE[i];
            float tough = TowerGear.TOUGHNESS[i];
            MATERIAL.put(set, MATERIALS.register(set, () -> new ArmorMaterial(
                    new EnumMap<>(Map.of(ArmorItem.Type.HELMET, def[0], ArmorItem.Type.CHESTPLATE, def[1], ArmorItem.Type.LEGGINGS, def[2],
                            ArmorItem.Type.BOOTS, def[3], ArmorItem.Type.BODY, def[1])),
                    16, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(SHARD.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, set))), tough, 0.05f)));
            List<DeferredItem<Item>> pieces = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                ArmorItem.Type type = TYPES[k];
                pieces.add(ITEMS.register(set + "_" + TowerGear.PIECES.get(k),
                        () -> new TowerArmorItem(MATERIAL.get(set), type, new Item.Properties().rarity(Rarity.EPIC), set)));
            }
            ARMOR.put(set, List.copyOf(pieces));
        }
    }

    public static final DeferredItem<Item> RING = ITEMS.register("tower_heaven_ring",
            () -> new Trinket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "item.skyabyss.tower_heaven_ring.desc"));
    public static final DeferredItem<Item> CHAIN = ITEMS.register("tower_abyss_chain",
            () -> new Trinket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "item.skyabyss.tower_abyss_chain.desc"));

    private TowerGearSetup() {
    }

    static DeferredItem<Item> weapon(String id, double attack, double speed) {
        return ITEMS.register(id, () -> new Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), attack, speed, "item.skyabyss." + id + ".desc"));
    }

    public static class Weapon extends ChapterWeaponItem {
        public Weapon(Item.Properties properties, double attack, double speed, String descKey) {
            super(properties, TowerGear.DUNGEON, attack, speed, GearTier.MYTHIC, descKey);
        }

        @Override
        public int requiredLevel() {
            return TowerGear.REQUIRED_LEVEL;
        }
    }

    public static class Bow extends ChapterBowItem {
        public Bow(Item.Properties properties, double multiplier, String descKey) {
            super(properties, TowerGear.DUNGEON, multiplier, GearTier.MYTHIC, descKey);
        }

        @Override
        public int requiredLevel() {
            return TowerGear.REQUIRED_LEVEL;
        }
    }

    public static class Trinket extends GearItem {
        public Trinket(Item.Properties properties, String descKey) {
            super(properties, GearTier.MYTHIC, descKey, true);
        }

        @Override
        public String dungeon() {
            return TowerGear.DUNGEON;
        }

        @Override
        public int requiredLevel() {
            return TowerGear.REQUIRED_LEVEL;
        }
    }

    // 塔の装備のすべて (武器・防具・装飾)
    public static List<DeferredItem<Item>> all() {
        List<DeferredItem<Item>> out = new ArrayList<>(weapons());
        ARMOR.values().forEach(out::addAll);
        out.add(RING);
        out.add(CHAIN);
        return out;
    }

    public static List<DeferredItem<Item>> weapons() {
        return List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        TitanGearSets.register(TowerGear.SET);
        modEventBus.addListener(TowerGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredHolder<Item, Item> item : all()) {
                event.accept(item.get());
            }
            event.accept(SHARD.get());
        }
    }
}
