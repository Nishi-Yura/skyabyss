package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.ChapterBowItem;
import jp.revati.skyabyss.item.ChapterWeaponItem;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 伝説の武器を巨神ごとに 6 職ぶんへ (2026-10-26)。深淵の専用伝説 (AbyssSetup の 4 本) に、足りない職の 20 本を足す。
// 出どころは伝説と同じ (abyss/LegendLoot。五章の番人・悪夢・深みの王の褒美。巨神の心臓からは出ない)。
// あわせて五章 (深淵) の装備に、無かった職の武器 3 本 (魔法使い・弓使い・呪術師。エピック。AbyssGear.SET の図鑑)。
// 強さは今の伝説と同じ 1 秒あたり 19 前後 (剣 19.9・鎚 20・杖 20.4・短剣 20・鈴杖 19.6・弓 近接の目安 20.1。四章の英雄の 1.05 倍より上)。五章の 3 本は灯り番の剣 (19.4) の少し下
public final class LegendGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final List<DeferredItem<Item>> LEGENDS = new ArrayList<>();
    private static final List<DeferredItem<Item>> CH5 = new ArrayList<>();

    // 伝説 (巨神 × 足りない職)
    static {
        legend("abyss_whale_hammer", Titans.STONE_WHALE, "hammer");
        legend("abyss_whale_staff", Titans.STONE_WHALE, "staff");
        legend("abyss_whale_bow", Titans.STONE_WHALE, "bow");
        legend("abyss_whale_fang", Titans.STONE_WHALE, "dagger");
        legend("abyss_whale_bell_staff", Titans.STONE_WHALE, "bell");
        legend("abyss_tide_sword", Titans.TIDE_SHELL, "sword");
        legend("abyss_tide_hammer", Titans.TIDE_SHELL, "hammer");
        legend("abyss_tide_staff", Titans.TIDE_SHELL, "staff");
        legend("abyss_tide_bow", Titans.TIDE_SHELL, "bow");
        legend("abyss_tide_dagger", Titans.TIDE_SHELL, "dagger");
        legend("abyss_storm_sword", Titans.THUNDER_WING, "sword");
        legend("abyss_storm_hammer", Titans.THUNDER_WING, "hammer");
        legend("abyss_storm_staff", Titans.THUNDER_WING, "staff");
        legend("abyss_storm_dagger", Titans.THUNDER_WING, "dagger");
        legend("abyss_storm_bell_staff", Titans.THUNDER_WING, "bell");
        legend("abyss_ember_sword", Titans.EMBER, "sword");
        legend("abyss_ember_staff", Titans.EMBER, "staff");
        legend("abyss_ember_bow", Titans.EMBER, "bow");
        legend("abyss_ember_dagger", Titans.EMBER, "dagger");
        legend("abyss_ember_bell_staff", Titans.EMBER, "bell");
        // 五章の武器 (エピック)
        CH5.add(ITEMS.register("abyss_lamp_staff", () -> new ChapterWeaponItem(new Item.Properties().rarity(Rarity.EPIC), "abyss", 15.8, 1.2,
                "item.skyabyss.abyss_lamp_staff.desc")));
        CH5.add(ITEMS.register("abyss_depth_bow", () -> new ChapterBowItem(new Item.Properties().rarity(Rarity.EPIC).durability(800), "abyss", 2.15,
                "item.skyabyss.abyss_depth_bow.desc")));
        CH5.add(ITEMS.register("abyss_toll_bell_staff", () -> new ChapterWeaponItem(new Item.Properties().rarity(Rarity.EPIC), "abyss", 13.5, 1.4,
                "item.skyabyss.abyss_toll_bell_staff.desc")));
    }

    private LegendGear() {
    }

    private static void legend(String path, String titan, String kind) {
        Item.Properties p = new Item.Properties().stacksTo(1).rarity(Rarity.EPIC);
        if (titan.equals(Titans.EMBER)) {
            p = p.fireResistant();
        }
        Item.Properties props = p;
        String desc = "item.skyabyss." + path + ".desc";
        LEGENDS.add(ITEMS.register(path, () -> switch (kind) {
            // 攻撃力 = 1 + 5 + 上乗せ、速さ = 4 + 差
            case "sword" -> new AbyssWeaponItem(props, titan, 13, -2.95f, desc);
            case "hammer" -> new AbyssWeaponItem(props, titan, 19, -3.2f, desc);
            case "staff" -> new AbyssWeaponItem(props, titan, 11, -2.8f, desc);
            case "dagger" -> new AbyssWeaponItem(props, titan, 4, -2.0f, desc);
            case "bell" -> new AbyssWeaponItem(props, titan, 8, -2.6f, desc);
            default -> new ChapterBowItem(props.durability(900), titan, 2.4, GearTier.ARTIFACT, desc);
        }));
    }

    // 足した伝説の id (名前空間つき)
    public static List<String> legendIds() {
        return LEGENDS.stream().map(d -> d.getId().toString()).toList();
    }

    public static List<DeferredItem<Item>> ch5() {
        return CH5;
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(LegendGear::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> item : LEGENDS) {
                event.accept(item.get());
            }
            for (DeferredItem<Item> item : CH5) {
                event.accept(item.get());
            }
        }
    }
}
