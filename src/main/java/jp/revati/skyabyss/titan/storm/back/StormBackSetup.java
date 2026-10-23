package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// 雷翼のブロックの登録: 羽鏡と避雷の柱 (雷獣の巣・心臓の間・中ボスの間)。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModBlocks・ModItems には手を入れない)。音は StormSetup (地上の担当) にある
public final class StormBackSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);

    public static final DeferredBlock<StormBlocks.Mirror> MIRROR = BLOCKS.register("feather_mirror",
            () -> new StormBlocks.Mirror(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(-1.0f, 3600000.0f)
                    .noLootTable().noOcclusion().sound(SoundType.COPPER).lightLevel(s -> 4)));

    // 避雷の柱 (台・段の電球・軸・先の針)。壊せない。見た目は tools/gen/items/gen_back_blocks.py の専用モデル
    private static BlockBehaviour.Properties fixed(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable().sound(sound).noOcclusion();
    }

    public static final DeferredBlock<StormBlocks.RodPost> ROD_BASE = BLOCKS.register("storm_rod",
            () -> new StormBlocks.RodPost(fixed(MapColor.COLOR_ORANGE, SoundType.COPPER), Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Block.box(2, 8, 2, 14, 12, 14), Block.box(5, 12, 5, 11, 16, 11))));
    public static final DeferredBlock<StormBlocks.RodBulb> ROD_BULB = BLOCKS.register("storm_rod_bulb",
            () -> new StormBlocks.RodBulb(fixed(MapColor.COLOR_ORANGE, SoundType.COPPER).lightLevel(s -> s.getValue(StormBlocks.RodBulb.LIT) ? 12 : 0)));
    public static final DeferredBlock<StormBlocks.RodPost> ROD_SHAFT = BLOCKS.register("storm_rod_shaft",
            () -> new StormBlocks.RodPost(fixed(MapColor.COLOR_ORANGE, SoundType.COPPER).lightLevel(s -> 3), Block.box(5, 0, 5, 11, 16, 11)));
    public static final DeferredBlock<StormBlocks.RodPost> ROD_TIP = BLOCKS.register("storm_rod_tip",
            () -> new StormBlocks.RodPost(fixed(MapColor.COLOR_ORANGE, SoundType.COPPER).lightLevel(s -> 6), Block.box(6, 0, 6, 10, 16, 10)));

    private StormBackSetup() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
