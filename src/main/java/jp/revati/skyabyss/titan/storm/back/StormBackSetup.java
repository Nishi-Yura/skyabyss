package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 雷翼の背 (鳥の形の背中・三つの試練) の登録: 風鳴り石、羽鏡、受け羽、風切りの羽根。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModBlocks・ModItems には手を入れない)。音は StormSetup (地上の担当) にある
public final class StormBackSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    public static final DeferredBlock<StormBlocks.ChimeStone> CHIME_STONE = BLOCKS.register("wind_stone",
            () -> new StormBlocks.ChimeStone(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(-1.0f, 3600000.0f)
                    .noLootTable().sound(SoundType.AMETHYST).lightLevel(s -> s.getValue(StormBlocks.ChimeStone.ACTIVE) ? 13 : 3)));
    public static final DeferredBlock<StormBlocks.Mirror> MIRROR = BLOCKS.register("feather_mirror",
            () -> new StormBlocks.Mirror(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(-1.0f, 3600000.0f)
                    .noLootTable().noOcclusion().sound(SoundType.COPPER).lightLevel(s -> 4)));
    public static final DeferredBlock<StormBlocks.Receiver> RECEIVER = BLOCKS.register("catch_feather",
            () -> new StormBlocks.Receiver(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(-1.0f, 3600000.0f)
                    .noLootTable().sound(SoundType.WOOL).lightLevel(s -> s.getValue(StormBlocks.Receiver.LIT) ? 15 : 2)));

    // 風切りの羽根 (西の試練の品。試練の外へ出ると消える)
    public static final DeferredItem<Item> GALE_FEATHER = ITEMS.register("gale_feather",
            () -> new GaleFeather(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private StormBackSetup() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        // 碑文と入口の一言の置き場所を伝承の担当へ (近くで目を向けるだけで読める)
        java.util.List<java.util.Map.Entry<String, net.minecraft.core.BlockPos>> signs = new java.util.ArrayList<>();
        StormBackLayout.LORE_SIGNS.forEach(i -> signs.add(java.util.Map.entry(i.key(), i.pos())));
        jp.revati.skyabyss.titan.storm.lore.StormLore.inscriptionPositions(() -> signs);
    }
}
