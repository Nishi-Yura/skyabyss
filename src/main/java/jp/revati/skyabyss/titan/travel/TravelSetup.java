package jp.revati.skyabyss.titan.travel;

import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 巨神での移動の登録 (呼び笛、碑、音、プレイヤーの記録)。SkyAbyss のコンストラクタから register を呼ぶ
public final class TravelSetup {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);

    // 岩鯨の呼び笛。心臓を倒した人が吹くと、岩鯨が迎えに来る
    public static final DeferredItem<Item> STONE_WHALE_CALL = ITEMS.register("stone_whale_call",
            () -> new TitanCallItem(() -> TitanKind.STONE_WHALE, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    // 潮殻の呼び貝。潮殻の心臓を倒した人が海辺で吹くと、潮殻が浅瀬へ寄り、海を渡って運んでくれる
    public static final DeferredItem<Item> TIDE_SHELL_CALL = ITEMS.register("tide_shell_call",
            () -> new TitanCallItem(() -> TitanKind.TIDE_SHELL, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    // 巨神の碑。触れると、巨神での移動の行き先に加わる
    public static final DeferredBlock<TitanWaymarkBlock> TITAN_WAYMARK = BLOCKS.register("titan_waymark",
            () -> new TitanWaymarkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(2.5f, 1200f)
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(state -> 6).noOcclusion()));
    public static final DeferredItem<BlockItem> TITAN_WAYMARK_ITEM = ITEMS.registerSimpleBlockItem(TITAN_WAYMARK);

    public static final DeferredHolder<SoundEvent, SoundEvent> CALL_SOUND = sound("titan_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEPART_SOUND = sound("titan_depart");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEIL_SOUND = sound("titan_veil");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAYMARK_SOUND = sound("titan_waymark");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_CALL_SOUND = sound("tide_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_DEPART_SOUND = sound("tide_depart");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_CURRENT_SOUND = sound("tide_current");

    // プレイヤーの記録。死んでも引き継ぎ、プレイヤーのデータと一緒に保存する
    public static final Supplier<AttachmentType<TravelState>> STATE = ATTACHMENTS.register("titan_travel",
            () -> AttachmentType.builder(TravelState::new).serialize(TravelState.CODEC).copyOnDeath().build());

    private TravelSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        SOUNDS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(TravelSetup::onCreativeTab);
    }

    // Mod のタブの末尾に並べる (ModItems の一覧には手を入れない)
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(STONE_WHALE_CALL.get());
            event.accept(TIDE_SHELL_CALL.get());
            event.accept(TITAN_WAYMARK_ITEM.get());
        }
    }
}
