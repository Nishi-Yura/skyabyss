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

    // 鐘雲 (近くの鐘が鳴ると 20 秒固まる。BellIsle)
    public static final DeferredBlock<BellCloudBlock> BELL_CLOUD = BLOCKS.register("bell_cloud",
            () -> new BellCloudBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .lightLevel(s -> s.getValue(BellCloudBlock.SOLID) ? 9 : 0)));

    // 光の橋 (島と島をつなぐ白金の板。主を倒した人だけが渡れる。IsleBridges)
    public static final DeferredBlock<net.minecraft.world.level.block.Block> LIGHT_PATH = BLOCKS.register("light_path",
            () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).lightLevel(s -> 11)));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> LIGHT_PATH_SLAB = BLOCKS.register("light_path_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).lightLevel(s -> 11)));

    private FinaleBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        jp.revati.skyabyss.story.finale.mob.CloudMobsSetup.register(modEventBus);
    }
}
