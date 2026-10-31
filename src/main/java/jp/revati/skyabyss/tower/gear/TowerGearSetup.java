package jp.revati.skyabyss.tower.gear;

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

// あいだの塔の報酬の品の登録 (docs/design/54 の 2.6)。塔の欠片・塔の武器 6 本 (ヒロイック・必要 Lv 50)。数値は TowerGear、効果は TowerGearEvents
public final class TowerGearSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    public static final DeferredItem<Item> SHARD = ITEMS.register("tower_shard",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xE8D8FF, "item.skyabyss.tower_shard.desc", false));

    public static final DeferredItem<Item> SWORD = weapon("tower_balance_sword", TowerGear.SWORD_ATTACK, TowerGear.SWORD_SPEED);
    public static final DeferredItem<Item> HAMMER = weapon("tower_pull_hammer", TowerGear.HAMMER_ATTACK, TowerGear.HAMMER_SPEED);
    public static final DeferredItem<Item> STAFF = weapon("tower_moon_staff", TowerGear.STAFF_ATTACK, TowerGear.STAFF_SPEED);
    public static final DeferredItem<Item> BOW = ITEMS.register("tower_eye_bow",
            () -> new Bow(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1200), TowerGear.BOW_MULTIPLIER, "item.skyabyss.tower_eye_bow.desc"));
    public static final DeferredItem<Item> DAGGER = weapon("tower_sigil_dagger", TowerGear.DAGGER_ATTACK, TowerGear.DAGGER_SPEED);
    public static final DeferredItem<Item> BELL = weapon("tower_clap_bell_staff", TowerGear.BELL_ATTACK, TowerGear.BELL_SPEED);

    private TowerGearSetup() {
    }

    static DeferredItem<Item> weapon(String id, double attack, double speed) {
        return ITEMS.register(id, () -> new Weapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), attack, speed, "item.skyabyss." + id + ".desc"));
    }

    public static class Weapon extends ChapterWeaponItem {
        public Weapon(Item.Properties properties, double attack, double speed, String descKey) {
            super(properties, TowerGear.DUNGEON, attack, speed, GearTier.HEROIC, descKey);
        }

        @Override
        public int requiredLevel() {
            return TowerGear.REQUIRED_LEVEL;
        }
    }

    public static class Bow extends ChapterBowItem {
        public Bow(Item.Properties properties, double multiplier, String descKey) {
            super(properties, TowerGear.DUNGEON, multiplier, GearTier.HEROIC, descKey);
        }

        @Override
        public int requiredLevel() {
            return TowerGear.REQUIRED_LEVEL;
        }
    }

    public static List<DeferredItem<Item>> weapons() {
        return List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TitanGearSets.register(TowerGear.SET);
        modEventBus.addListener(TowerGearSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredHolder<Item, Item> item : List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL, SHARD)) {
                event.accept(item.get());
            }
        }
    }
}
