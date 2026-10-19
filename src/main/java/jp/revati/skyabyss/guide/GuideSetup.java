package jp.revati.skyabyss.guide;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 章の導きの基盤 (docs/design/21 の 1.4・1.5、P3) の登録。SkyAbyss のコンストラクタから register を一度だけ呼ぶ。
// 共有の ModItems・ModDataComponents には手を入れず、ここで別に登録する
public final class GuideSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(SkyAbyss.MODID);

    // 旅の覚書の章 (0 = 序章、1〜4 = 一〜四章)。冊ごとに持つ
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NOTES_CHAPTER = COMPONENTS.registerComponentType("notes_chapter",
            b -> b.persistent(Codec.intRange(0, 4)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<Item> WAYFARER_NOTES = ITEMS.register("wayfarer_notes",
            () -> new WayfarerNotesItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // 鼓動守 (巨神ごと)。titan は ProgressRules.TITAN_ORDER の名前
    public static final DeferredItem<Item> HEARTBEAT_WARD_STONE_WHALE = ward("stone_whale");
    public static final DeferredItem<Item> HEARTBEAT_WARD_TIDE_SHELL = ward("tide_shell");
    public static final DeferredItem<Item> HEARTBEAT_WARD_THUNDER_WING = ward("thunder_wing");
    public static final DeferredItem<Item> HEARTBEAT_WARD_EMBER = ward("ember");

    // 一章の地上の素材 (21 の 3.1、P6 が先に置いた仮の登録。絵は仮 = tools/gen_ch1_materials.py。落とす敵は P7、旅装・砥石のレシピは P5 が足す。
    // ID は 21 の表のとおり。P5・P7 は、同じ ID を二重に登録せず、この 4 つを使うこと)
    public static final DeferredItem<Item> ROCK_SHELL_SCRAP = ITEMS.register("rock_shell_scrap", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> BONE_CHIP = ITEMS.register("bone_chip", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> HARE_QUILL = ITEMS.register("hare_quill", () -> new Item(new Item.Properties()));
    // 岩殻の護り糸 (鼓動守 岩鯨の材料)
    public static final DeferredItem<Item> ROCK_SHELL_THREAD = ITEMS.register("rock_shell_thread", () -> new Item(new Item.Properties()));

    // 歩哨の割符 (老いた歩哨の落とし物。人ごとの記念品。一章の背中の詰所の戸の鍵 = Ch1Prep.SENTRY_TALLY。P8 が読む)
    public static final DeferredItem<Item> SENTRY_TALLY = ITEMS.register("sentry_tally",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private GuideSetup() {
    }

    private static DeferredItem<Item> ward(String titan) {
        return ITEMS.register("heartbeat_ward_" + titan, () -> new HeartbeatWardItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), titan));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(GuideSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (int c = 0; c <= WayfarerNotes.MAX_CHAPTER; c++) {
                event.accept(WayfarerNotes.make(c));
            }
            event.accept(ROCK_SHELL_SCRAP.get());
            event.accept(BONE_CHIP.get());
            event.accept(HARE_QUILL.get());
            event.accept(ROCK_SHELL_THREAD.get());
            event.accept(SENTRY_TALLY.get());
            event.accept(HEARTBEAT_WARD_STONE_WHALE.get());
            event.accept(HEARTBEAT_WARD_TIDE_SHELL.get());
            event.accept(HEARTBEAT_WARD_THUNDER_WING.get());
            event.accept(HEARTBEAT_WARD_EMBER.get());
        }
    }

    // 巨神の名前から鼓動守 (知らない巨神は null)
    public static Item wardOf(String titan) {
        return switch (titan) {
            case "stone_whale" -> HEARTBEAT_WARD_STONE_WHALE.get();
            case "tide_shell" -> HEARTBEAT_WARD_TIDE_SHELL.get();
            case "thunder_wing" -> HEARTBEAT_WARD_THUNDER_WING.get();
            case "ember" -> HEARTBEAT_WARD_EMBER.get();
            default -> null;
        };
    }
}
