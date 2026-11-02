package jp.revati.skyabyss.quest.free;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 自由なモードのサブクエストの登録 (言伝ての板)。SkyAbyss のコンストラクタから register を呼ぶ。
// 共有の ModBlocks・ModItems には手を入れず、ここで別に登録する
public final class FreeQuestSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    public static final DeferredBlock<MessageBoardBlock> MESSAGE_BOARD = BLOCKS.register("message_board",
            () -> new MessageBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD)
                    .noOcclusion().ignitedByLava()));
    public static final DeferredItem<BlockItem> MESSAGE_BOARD_ITEM = ITEMS.registerSimpleBlockItem(MESSAGE_BOARD);

    private FreeQuestSetup() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(FreeQuestSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(MESSAGE_BOARD_ITEM.get());
        }
    }
}
