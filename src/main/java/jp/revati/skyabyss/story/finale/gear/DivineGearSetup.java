package jp.revati.skyabyss.story.finale.gear;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.ChapterBowItem;
import jp.revati.skyabyss.item.ChapterWeaponItem;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.MaterialItem;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 終章の神器の登録 (docs/design/50 の 4.5・4.5.1)。二本目 6 本・防具 3 式 12 部位・装飾 2 も (どれも神器)。6 本 (神器・必要 Lv 50)・雲上の心核・心核の欠片。数値は DivineGear、効果は DivineGearEvents
public final class DivineGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<net.minecraft.world.item.ArmorMaterial> MATERIALS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    public static final DeferredItem<Item> SHARD = ITEMS.register("heart_core_shard",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xFFE8B4, "item.skyabyss.heart_core_shard.desc", false));
    public static final DeferredItem<Item> CORE = ITEMS.register("cloud_heart",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 0xFFF4D8, "item.skyabyss.cloud_heart.desc", true));

    public static final DeferredItem<Item> SWORD = weapon("divine_sky_sword", DivineGear.SWORD_ATTACK, DivineGear.SWORD_SPEED);
    public static final DeferredItem<Item> HAMMER = weapon("divine_bell_hammer", DivineGear.HAMMER_ATTACK, DivineGear.HAMMER_SPEED);
    public static final DeferredItem<Item> STAFF = weapon("divine_dawn_staff", DivineGear.STAFF_ATTACK, DivineGear.STAFF_SPEED);
    public static final DeferredItem<Item> BOW = ITEMS.register("divine_plume_bow",
            () -> new Bow(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1200), DivineGear.BOW_MULTIPLIER, "item.skyabyss.divine_plume_bow.desc"));
    public static final DeferredItem<Item> DAGGER = weapon("divine_rewind_dagger", DivineGear.DAGGER_ATTACK, DivineGear.DAGGER_SPEED);
    public static final DeferredItem<Item> BELL = weapon("divine_promise_bell_staff", DivineGear.BELL_ATTACK, DivineGear.BELL_SPEED);
    // 二本目 (数値は一本目と同じ)
    public static final DeferredItem<Item> WARDEN = weapon("divine_warden_blade", DivineGear.SWORD_ATTACK, DivineGear.SWORD_SPEED);
    public static final DeferredItem<Item> ASH = weapon("divine_ash_hammer", DivineGear.HAMMER_ATTACK, DivineGear.HAMMER_SPEED);
    public static final DeferredItem<Item> EYELID = weapon("divine_eyelid_staff", DivineGear.STAFF_ATTACK, DivineGear.STAFF_SPEED);
    public static final DeferredItem<Item> MIST = ITEMS.register("divine_mist_bow",
            () -> new Bow(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1200), DivineGear.BOW_MULTIPLIER, "item.skyabyss.divine_mist_bow.desc"));
    public static final DeferredItem<Item> MOTH = weapon("divine_moth_dagger", DivineGear.DAGGER_ATTACK, DivineGear.DAGGER_SPEED);
    public static final DeferredItem<Item> KEEPER = weapon("divine_keeper_bell_staff", DivineGear.BELL_ATTACK, DivineGear.BELL_SPEED);

    // 防具 (一式ごとの素材は心核の欠片で直す。着た姿は塔の防具と同じ低ポリの描画)
    public static final java.util.Map<String, java.util.List<DeferredItem<Item>>> ARMOR = new java.util.LinkedHashMap<>();
    static final net.minecraft.world.item.ArmorItem.Type[] TYPES = {net.minecraft.world.item.ArmorItem.Type.HELMET,
            net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS};

    static {
        for (int i = 0; i < DivineGear.ARMOR_SETS.size(); i++) {
            String set = DivineGear.ARMOR_SETS.get(i);
            int[] def = DivineGear.ARMOR_DEFENSE[i];
            float tough = DivineGear.ARMOR_TOUGHNESS[i];
            var mat = MATERIALS.register(set, () -> new net.minecraft.world.item.ArmorMaterial(
                    new java.util.EnumMap<>(java.util.Map.of(TYPES[0], def[0], TYPES[1], def[1], TYPES[2], def[2], TYPES[3], def[3],
                            net.minecraft.world.item.ArmorItem.Type.BODY, def[1])),
                    16, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, () -> net.minecraft.world.item.crafting.Ingredient.of(SHARD.get()),
                    java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, set))),
                    tough, 0.05f));
            java.util.List<DeferredItem<Item>> pieces = new java.util.ArrayList<>();
            for (int k = 0; k < 4; k++) {
                var type = TYPES[k];
                pieces.add(ITEMS.register(set + "_" + jp.revati.skyabyss.tower.gear.TowerGear.PIECES.get(k),
                        () -> new DivineArmorItem(mat, type, new Item.Properties().rarity(Rarity.EPIC), set)));
            }
            ARMOR.put(set, java.util.List.copyOf(pieces));
        }
    }

    public static final DeferredItem<Item> CHARM = ITEMS.register("divine_plume_charm",
            () -> new Trinket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "item.skyabyss.divine_plume_charm.desc"));
    public static final DeferredItem<Item> CHIME = ITEMS.register("divine_promise_chime",
            () -> new Trinket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "item.skyabyss.divine_promise_chime.desc"));

    private DivineGearSetup() {
    }

    static DeferredItem<Item> weapon(String id, double attack, double speed) {
        return ITEMS.register(id, () -> new Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), attack, speed, "item.skyabyss." + id + ".desc"));
    }

    public static class Weapon extends ChapterWeaponItem {
        public Weapon(Item.Properties properties, double attack, double speed, String descKey) {
            super(properties, DivineGear.DUNGEON, attack, speed, GearTier.MYTHIC, descKey);
        }

        @Override
        public int requiredLevel() {
            return DivineGear.REQUIRED_LEVEL;
        }
    }

    public static class Bow extends ChapterBowItem {
        public Bow(Item.Properties properties, double multiplier, String descKey) {
            super(properties, DivineGear.DUNGEON, multiplier, GearTier.MYTHIC, descKey);
        }

        @Override
        public int requiredLevel() {
            return DivineGear.REQUIRED_LEVEL;
        }
    }

    public static class Trinket extends jp.revati.skyabyss.item.GearItem {
        public Trinket(Item.Properties properties, String descKey) {
            super(properties, GearTier.MYTHIC, descKey, true);
        }

        @Override
        public String dungeon() {
            return DivineGear.DUNGEON;
        }

        @Override
        public int requiredLevel() {
            return DivineGear.REQUIRED_LEVEL;
        }
    }

    public static List<DeferredItem<Item>> relics() {
        return List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL, WARDEN, ASH, EYELID, MIST, MOTH, KEEPER);
    }

    // 雲上の箱の中身 (神器の武器 12・防具 12 部位・装飾 2)
    public static List<DeferredItem<Item>> all() {
        java.util.List<DeferredItem<Item>> out = new java.util.ArrayList<>(relics());
        ARMOR.values().forEach(out::addAll);
        out.add(CHARM);
        out.add(CHIME);
        return out;
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        TitanGearSets.register(DivineGear.SET);
        modEventBus.addListener(DivineGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredHolder<Item, Item> item : all()) {
                event.accept(item.get());
            }
            event.accept(SHARD.get());
            event.accept(CORE.get());
        }
    }
}
