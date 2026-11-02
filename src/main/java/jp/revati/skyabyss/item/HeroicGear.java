package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 巨神のヒロイック武器 (専用の逸品) を全職ぶんへ (2026-10-28 の試遊「なぜ巨神のヒロイック武器、暗殺者と戦士しかないの」。docs/design/03 の 1.1)。
// 巨神ごとに、まだ専用の逸品のない職の武器を一本ずつ足した 17 本。形は 3 案のラフから選んだもの (tools/rough/lowpoly/weapons/heroic)。
// 数値は同じ巨神の章の武器 (エピック) の 1.12 倍を目安に置き、その章の最も強いエピック以上・アーティファクトの 1/1.05 以下に収める (自己テスト gear tiers)。
// 入手は心臓を止めたときの別枠 (heartDrops。一本ずつ CHANCE)。巨神の図鑑の表 (TitanGearSet) には入れない
// (入れると図鑑がそろうまでの周回が 16 回から 29 回に延びた。2026-10-28)。
// 技の書き換えは一本に一つ (rpg/skill/Rune の英雄の段、効き目は rpg/skill/LegendRewrites)
public final class HeroicGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final List<DeferredItem<Item>> ALL = new ArrayList<>();
    // 心臓での確率 (今の専用の逸品の 0.04 より低く。図鑑がそろうまでの周回が延びすぎないように)
    public static final float CHANCE = 0.02f;

    public static final String WHALE_HAMMER = id("whale_choir_hammer");
    public static final String WHALE_STAFF = id("whale_star_staff");
    public static final String WHALE_BOW = id("whale_rib_longbow");
    public static final String WHALE_BELL = id("whale_deep_bell_staff");
    public static final String TIDE_HAMMER = id("tide_reef_hammer");
    public static final String TIDE_STAFF = id("tide_pearl_staff");
    public static final String TIDE_BOW = id("tide_coral_bow");
    public static final String TIDE_BELL = id("tide_surf_bell_staff");
    public static final String STORM_HAMMER = id("storm_anvil_hammer");
    public static final String STORM_STAFF = id("storm_cloud_staff");
    public static final String STORM_BOW = id("storm_gale_bow");
    public static final String STORM_BELL = id("storm_thunder_bell_staff");
    public static final String EMBER_BLADE = id("ember_cinder_blade");
    public static final String EMBER_KNIFE = id("ember_ash_knife");
    public static final String EMBER_STAFF = id("ember_kiln_staff");
    public static final String EMBER_BOW = id("ember_flare_bow");
    public static final String EMBER_BELL = id("ember_censer_bell_staff");

    static {
        melee("whale_choir_hammer", "stone_whale", 12.3, 0.9);
        melee("whale_star_staff", "stone_whale", 9.0, 1.2);
        bow("whale_rib_longbow", "stone_whale", 1.3);
        melee("whale_deep_bell_staff", "stone_whale", 7.3, 1.4);
        melee("tide_reef_hammer", "tide_shell", 15.7, 0.9);
        melee("tide_pearl_staff", "tide_shell", 11.2, 1.2);
        bow("tide_coral_bow", "tide_shell", 1.6);
        melee("tide_surf_bell_staff", "tide_shell", 9.5, 1.4);
        melee("storm_anvil_hammer", "thunder_wing", 17.5, 0.9);
        melee("storm_cloud_staff", "thunder_wing", 13.0, 1.2);
        bow("storm_gale_bow", "thunder_wing", 1.8);
        melee("storm_thunder_bell_staff", "thunder_wing", 11.2, 1.4);
        melee("ember_cinder_blade", "ember", 18.2, 1.0);
        melee("ember_ash_knife", "ember", 9.1, 2.0);
        melee("ember_kiln_staff", "ember", 15.2, 1.2);
        bow("ember_flare_bow", "ember", 2.1);
        melee("ember_censer_bell_staff", "ember", 13.0, 1.4);
    }

    private HeroicGear() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    private static Item.Properties props(String dungeon) {
        Item.Properties p = new Item.Properties().stacksTo(1).rarity(Rarity.EPIC);
        return dungeon.equals("ember") ? p.fireResistant() : p;
    }

    private static void melee(String path, String dungeon, double attack, double speed) {
        ALL.add(ITEMS.register(path, () -> new ChapterWeaponItem(props(dungeon), dungeon, attack, speed, GearTier.HEROIC, "item.skyabyss." + path + ".desc")));
    }

    private static void bow(String path, String dungeon, double multiplier) {
        ALL.add(ITEMS.register(path, () -> new ChapterBowItem(props(dungeon).durability(900), dungeon, multiplier, GearTier.HEROIC, "item.skyabyss." + path + ".desc")));
    }

    public static List<DeferredItem<Item>> all() {
        return ALL;
    }

    // 巨神ごとの品 (名前空間つきの id)
    public static List<String> forTitan(String titan) {
        return switch (titan) {
            case "stone_whale" -> List.of(WHALE_HAMMER, WHALE_STAFF, WHALE_BOW, WHALE_BELL);
            case "tide_shell" -> List.of(TIDE_HAMMER, TIDE_STAFF, TIDE_BOW, TIDE_BELL);
            case "thunder_wing" -> List.of(STORM_HAMMER, STORM_STAFF, STORM_BOW, STORM_BELL);
            case "ember" -> List.of(EMBER_BLADE, EMBER_KNIFE, EMBER_STAFF, EMBER_BOW, EMBER_BELL);
            default -> List.of();
        };
    }

    // 心臓を止めたときの別枠の落とし物 (図鑑の表・天井・交換には入れない。一本ずつ CHANCE)。特性つき
    public static List<net.minecraft.world.item.ItemStack> heartDrops(String titan, net.minecraft.util.RandomSource random) {
        List<net.minecraft.world.item.ItemStack> out = new ArrayList<>();
        for (String id : forTitan(titan)) {
            if (random.nextFloat() < CHANCE) {
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).ifPresent(item ->
                        out.add(jp.revati.skyabyss.item.affix.AffixRules.withAffixes(new net.minecraft.world.item.ItemStack(item), random)));
            }
        }
        return out;
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(HeroicGear::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> item : ALL) {
                event.accept(item.get());
            }
        }
    }
}
