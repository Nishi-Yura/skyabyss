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

// 終章の神器の登録 (docs/design/50 の 4.5)。6 本 (神器・必要 Lv 50)・雲上の心核・心核の欠片。数値は DivineGear、効果は DivineGearEvents
public final class DivineGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

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

    public static List<DeferredItem<Item>> relics() {
        return List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TitanGearSets.register(DivineGear.SET);
        modEventBus.addListener(DivineGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredHolder<Item, Item> item : List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL, SHARD, CORE)) {
                event.accept(item.get());
            }
        }
    }
}
