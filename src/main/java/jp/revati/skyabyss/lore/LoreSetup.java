package jp.revati.skyabyss.lore;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 伝承の書まわりの登録 (アイテム、頁の出どころ、プレイヤーの記録)。SkyAbyss のコンストラクタから register を呼ぶ
public final class LoreSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);

    // 伝承の書。右クリックで開く
    public static final DeferredItem<Item> LORE_BOOK = ITEMS.register("lore_book",
            () -> new LoreBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));
    // 破れた頁。読むと、まだ記されていない伝承が一つ書に移る
    public static final DeferredItem<Item> LORE_PAGE = ITEMS.register("lore_page",
            () -> new LorePageItem(new Item.Properties().stacksTo(16)));

    // 頁の出どころ (village / titan_back)。読んだときに、その出どころの項目から選ぶ
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PAGE_SOURCE = COMPONENTS.registerComponentType("lore_page_source",
            builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // プレイヤーの記録。死んでも引き継ぎ (copyOnDeath)、プレイヤーのデータと一緒に保存する
    public static final Supplier<AttachmentType<LoreState>> STATE = ATTACHMENTS.register("lore_state",
            () -> AttachmentType.builder(LoreState::new).serialize(LoreState.CODEC).copyOnDeath().build());

    private LoreSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(LoreSetup::onCreativeTab);
    }

    // Mod のタブの末尾に並べる (ModItems の一覧には手を入れない)
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(LORE_BOOK.get());
            event.accept(LORE_PAGE.get());
        }
    }
}
