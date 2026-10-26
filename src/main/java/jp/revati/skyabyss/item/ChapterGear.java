package jp.revati.skyabyss.item;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 職の章の武器 (docs/design/43 の 5 段目と 8 節)。巨神の装備の流れで、どの章でも 6 職それぞれに専用の武器がそろうよう、
// 元の巨神の装備に無かった職の武器を足す。どれもエピックで、巨神の心臓の報酬の図鑑に入る (各巨神の TitanGearSet と、岩鯨は TitanLoot)。
// 強さ (1 秒あたり) は章のエピックの武器 (鯨骨の大剣 10・三叉槍 12.75・雷翼の弓 15.2 相当・余燼の戦斧 16) の少し下。杖の攻撃力は魔法の元
//
// | 品               | 巨神 | 職       | 攻撃力 / 矢の倍率 | 速さ | 1 秒あたり |
// | 鯨骨の星杖       | 岩鯨 | 魔法使い | 8      | 1.2  | 9.6  |
// | 鯨歌いの鈴杖     | 岩鯨 | 呪術師   | 6.5    | 1.4  | 9.1  |
// | 鯨骨の鎚         | 岩鯨 | 鍛冶師   | 11     | 0.9  | 9.9  |
// | 鯨肋の弓         | 岩鯨 | 弓使い   | 矢 ×1.15 | —  | 近接の目安 9.6 |
// | 潮殻の真珠杖     | 潮殻 | 魔法使い | 10     | 1.2  | 12.0 |
// | 潮鳴りの鈴杖     | 潮殻 | 呪術師   | 8.5    | 1.4  | 11.9 |
// | 潮殻の鎚         | 潮殻 | 鍛冶師   | 14     | 0.9  | 12.6 |
// | 珊瑚の弓         | 潮殻 | 弓使い   | 矢 ×1.45 | —  | 近接の目安 12.1 |
// | 雷羽の杖         | 雷翼 | 魔法使い | 12     | 1.2  | 14.4 |
// | 雷鳴りの鈴杖     | 雷翼 | 呪術師   | 10     | 1.4  | 14.0 |
// | 雷の鎚           | 雷翼 | 鍛冶師   | 16.5   | 0.9  | 14.85 |
// | 熾火の杖         | 余燼 | 魔法使い | 13     | 1.2  | 15.6 |
// | 灰の香炉         | 余燼 | 呪術師   | 11     | 1.4  | 15.4 |
// | 熾火の大剣       | 余燼 | 戦士     | 15.5   | 1.0  | 15.5 |
// | 灰の弓           | 余燼 | 弓使い   | 矢 ×1.85 | —  | 近接の目安 15.4 |
// | 熾の短剣         | 余燼 | 暗殺者   | 7.8    | 2.0  | 15.6 |
public final class ChapterGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    public static final String WHALE_STAFF = SkyAbyss.MODID + ":whale_staff";
    public static final String WHALE_BELL = SkyAbyss.MODID + ":whale_bell_staff";
    public static final String WHALE_HAMMER = SkyAbyss.MODID + ":whale_hammer";
    public static final String WHALE_BOW = SkyAbyss.MODID + ":whale_bow";
    public static final String TIDE_STAFF = SkyAbyss.MODID + ":tide_staff";
    public static final String TIDE_BELL = SkyAbyss.MODID + ":tide_bell_staff";
    public static final String TIDE_HAMMER = SkyAbyss.MODID + ":tide_hammer";
    public static final String TIDE_BOW = SkyAbyss.MODID + ":tide_bow";
    public static final String STORM_STAFF = SkyAbyss.MODID + ":storm_staff";
    public static final String STORM_BELL = SkyAbyss.MODID + ":storm_bell_staff";
    public static final String STORM_HAMMER = SkyAbyss.MODID + ":storm_hammer";
    public static final String EMBER_STAFF = SkyAbyss.MODID + ":ember_staff";
    public static final String EMBER_CENSER = SkyAbyss.MODID + ":ember_censer";
    public static final String EMBER_GREATSWORD = SkyAbyss.MODID + ":ember_greatsword";
    public static final String EMBER_BOW = SkyAbyss.MODID + ":ember_bow";
    public static final String EMBER_DAGGER = SkyAbyss.MODID + ":ember_dagger";
    // 心臓での確率 (章のエピックの武器と同じ 20%)
    public static final float CHANCE = 0.20f;
    // 巨神ごとに足した品 (図鑑の数の検査が使う)
    public static final List<String> WHALE = List.of(WHALE_STAFF, WHALE_BELL, WHALE_HAMMER, WHALE_BOW);
    public static final List<String> TIDE = List.of(TIDE_STAFF, TIDE_BELL, TIDE_HAMMER, TIDE_BOW);
    public static final List<String> STORM = List.of(STORM_STAFF, STORM_BELL, STORM_HAMMER);
    public static final List<String> EMBER = List.of(EMBER_STAFF, EMBER_CENSER, EMBER_GREATSWORD, EMBER_BOW, EMBER_DAGGER);

    public static final DeferredItem<Item> WHALE_STAFF_ITEM = melee("whale_staff", "stone_whale", 8, 1.2);
    public static final DeferredItem<Item> WHALE_BELL_ITEM = melee("whale_bell_staff", "stone_whale", 6.5, 1.4);
    public static final DeferredItem<Item> WHALE_HAMMER_ITEM = melee("whale_hammer", "stone_whale", 11, 0.9);
    public static final DeferredItem<Item> WHALE_BOW_ITEM = bow("whale_bow", "stone_whale", 1.15);
    public static final DeferredItem<Item> TIDE_STAFF_ITEM = melee("tide_staff", "tide_shell", 10, 1.2);
    public static final DeferredItem<Item> TIDE_BELL_ITEM = melee("tide_bell_staff", "tide_shell", 8.5, 1.4);
    public static final DeferredItem<Item> TIDE_HAMMER_ITEM = melee("tide_hammer", "tide_shell", 14, 0.9);
    public static final DeferredItem<Item> TIDE_BOW_ITEM = bow("tide_bow", "tide_shell", 1.45);
    public static final DeferredItem<Item> STORM_STAFF_ITEM = melee("storm_staff", "thunder_wing", 12, 1.2);
    public static final DeferredItem<Item> STORM_BELL_ITEM = melee("storm_bell_staff", "thunder_wing", 10, 1.4);
    public static final DeferredItem<Item> STORM_HAMMER_ITEM = melee("storm_hammer", "thunder_wing", 16.5, 0.9);
    public static final DeferredItem<Item> EMBER_STAFF_ITEM = melee("ember_staff", "ember", 13, 1.2);
    public static final DeferredItem<Item> EMBER_CENSER_ITEM = melee("ember_censer", "ember", 11, 1.4);
    public static final DeferredItem<Item> EMBER_GREATSWORD_ITEM = melee("ember_greatsword", "ember", 15.5, 1.0);
    public static final DeferredItem<Item> EMBER_BOW_ITEM = bow("ember_bow", "ember", 1.85);
    public static final DeferredItem<Item> EMBER_DAGGER_ITEM = melee("ember_dagger", "ember", 7.8, 2.0);

    private ChapterGear() {
    }

    // 余燼の品は溶岩で燃えない (余燼の装備と同じ)
    private static Item.Properties props(String dungeon) {
        Item.Properties p = new Item.Properties().rarity(Rarity.EPIC);
        return dungeon.equals("ember") ? p.fireResistant() : p;
    }

    private static DeferredItem<Item> melee(String path, String dungeon, double attack, double speed) {
        return ITEMS.register(path, () -> new ChapterWeaponItem(props(dungeon), dungeon, attack, speed, "item.skyabyss." + path + ".desc"));
    }

    private static DeferredItem<Item> bow(String path, String dungeon, double multiplier) {
        return ITEMS.register(path, () -> new ChapterBowItem(props(dungeon).durability(600), dungeon, multiplier, "item.skyabyss." + path + ".desc"));
    }

    public static List<DeferredItem<Item>> all() {
        return List.of(WHALE_STAFF_ITEM, WHALE_BELL_ITEM, WHALE_HAMMER_ITEM, WHALE_BOW_ITEM, TIDE_STAFF_ITEM, TIDE_BELL_ITEM, TIDE_HAMMER_ITEM, TIDE_BOW_ITEM,
                STORM_STAFF_ITEM, STORM_BELL_ITEM, STORM_HAMMER_ITEM, EMBER_STAFF_ITEM, EMBER_CENSER_ITEM, EMBER_GREATSWORD_ITEM, EMBER_BOW_ITEM, EMBER_DAGGER_ITEM);
    }

    public static List<String> forTitan(String titan) {
        return switch (titan) {
            case "stone_whale" -> WHALE;
            case "tide_shell" -> TIDE;
            case "thunder_wing" -> STORM;
            case "ember" -> EMBER;
            default -> List.of();
        };
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ChapterGear::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredItem<Item> item : all()) {
                event.accept(item.get());
            }
        }
    }
}
