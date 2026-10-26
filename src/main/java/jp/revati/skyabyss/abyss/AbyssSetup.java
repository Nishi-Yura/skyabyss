package jp.revati.skyabyss.abyss;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 深淵のアーティファクト 4 点の登録 (docs/design/31 の B04・03 の「深淵」)。巨神ごとに一つ。入手は深淵の心臓だけ (AbyssLoot)。
// 巨神の図鑑 (TitanGearSet) には入れない: 通常の天井・共鳴の欠片での交換の対象外 (深淵の天井は AbyssLoot が別に数える)
public final class AbyssSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    public static final String GREATSWORD = "abyss_greatsword";
    public static final String BELL_STAFF = "abyss_bell_staff";
    public static final String STORM_BOW = "abyss_storm_bow";
    public static final String ASH_SCYTHE = "abyss_ash_scythe";

    // 攻撃力 = 1 + 5 + 値。ヒロイック (大鎚 19・鉾 14 前後) の幅の中
    public static final DeferredItem<Item> GREATSWORD_ITEM = ITEMS.register(GREATSWORD,
            () -> new AbyssWeaponItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), Titans.STONE_WHALE, 12, -3.1f, "item.skyabyss.abyss_greatsword.desc"));
    public static final DeferredItem<Item> BELL_STAFF_ITEM = ITEMS.register(BELL_STAFF,
            () -> new AbyssWeaponItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), Titans.TIDE_SHELL, 6, -2.4f, "item.skyabyss.abyss_bell_staff.desc"));
    public static final DeferredItem<Item> STORM_BOW_ITEM = ITEMS.register(STORM_BOW,
            () -> new AbyssBowItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(900), Titans.THUNDER_WING, "item.skyabyss.abyss_storm_bow.desc"));
    public static final DeferredItem<Item> ASH_SCYTHE_ITEM = ITEMS.register(ASH_SCYTHE,
            () -> new AbyssWeaponItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), Titans.EMBER, 10, -2.8f, "item.skyabyss.abyss_ash_scythe.desc"));

    private AbyssSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(AbyssSetup::onCreativeTab);
    }

    // 巨神ごとの専用の逸品 (品の id)
    public static String legendOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> BELL_STAFF;
            case Titans.THUNDER_WING -> STORM_BOW;
            case Titans.EMBER -> ASH_SCYTHE;
            default -> GREATSWORD;
        };
    }

    public static List<String> legends() {
        return List.of(GREATSWORD, BELL_STAFF, STORM_BOW, ASH_SCYTHE);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> item : List.of(GREATSWORD_ITEM, BELL_STAFF_ITEM, STORM_BOW_ITEM, ASH_SCYTHE_ITEM)) {
                event.accept(item.get());
            }
        }
    }
}
