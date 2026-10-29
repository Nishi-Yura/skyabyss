package jp.revati.skyabyss.story.finale;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// 終章「雲海」のブロック (docs/design/50)。絵は tools/gen/finale/gen_finale_assets.py
public final class FinaleBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);

    // 雲の海 (壊せない。当たりなし)
    public static final DeferredBlock<CloudBlock> CLOUD = BLOCKS.register("cloud",
            () -> new CloudBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion().noCollission().isViewBlocking((s, l, p) -> false)));

    private FinaleBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
