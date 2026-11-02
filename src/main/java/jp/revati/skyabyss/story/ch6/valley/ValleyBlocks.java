package jp.revati.skyabyss.story.ch6.valley;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 新月の谷の仕掛けのブロックと星の灯 (docs/design/46 の 4.3・4.3.1)。どれも壊せない (谷の形の一部)。
// 音の出来事は skyabyss_moon (assets/skyabyss_moon/sounds.json。tools/gen/ch6/gen_moon_mobs.py が書く)。文は assets/skyabyss_moon/lang
public final class ValleyBlocks {
    public static final String SOUND_NS = "skyabyss_moon";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // 影の橋: 暗がりにだけある黒い硝子の足場。星灯りの中では消える (ShadowBridgeBlock)
    public static final DeferredBlock<ShadowBridgeBlock> SHADOW_BRIDGE = BLOCKS.register("shadow_bridge",
            () -> new ShadowBridgeBlock(fixed(MapColor.COLOR_BLACK, SoundType.AMETHYST).noOcclusion()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
    // 欠片の台 (探す場所に一つずつ。右クリックで星の欠片)
    public static final DeferredBlock<StarPedestalBlock> STAR_PEDESTAL = BLOCKS.register("star_pedestal",
            () -> new StarPedestalBlock(fixed(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_BRICKS).noOcclusion().lightLevel(s -> 9)));
    // 鏡の石 (鏡の窪の縁に三つ。右クリックで回す)
    public static final DeferredBlock<MirrorStoneBlock> MIRROR_STONE = BLOCKS.register("mirror_stone",
            () -> new MirrorStoneBlock(fixed(MapColor.QUARTZ, SoundType.CALCITE).noOcclusion().lightLevel(s -> 4)));
    // 星の門 (台地の北の根元と、台地の上の帰り口)
    public static final DeferredBlock<StarGateBlock> STAR_GATE = BLOCKS.register("star_gate",
            () -> new StarGateBlock(fixed(MapColor.COLOR_BLUE, SoundType.AMETHYST).noOcclusion().lightLevel(s -> s.getValue(StarGateBlock.CORE) ? 12 : 6)));
    // 灯りの台 (主の間のまわりに六つ。星の灯を持って右クリックで灯る)
    public static final DeferredBlock<LampPostBlock> LAMP_POST = BLOCKS.register("lamp_post",
            () -> new LampPostBlock(fixed(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_BRICKS).noOcclusion().lightLevel(s -> s.getValue(LampPostBlock.LIT) ? 14 : 2)));
    // 星の溜まり (野営。星術を覚える所 = 星の灯をもらう)
    public static final DeferredBlock<StarPoolBlock> STAR_POOL = BLOCKS.register("star_pool",
            () -> new StarPoolBlock(fixed(MapColor.COLOR_PURPLE, SoundType.AMETHYST).noOcclusion().lightLevel(s -> 10)));
    // 星の印 (星の灯で置く。足もとの小さな光の結晶。当たりはなく、時間で消える)
    public static final DeferredBlock<StarMarkBlock> STAR_MARK = BLOCKS.register("star_mark",
            () -> new StarMarkBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noCollission().instabreak().noLootTable()
                    .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 13).replaceable().pushReaction(PushReaction.DESTROY)));

    // 星の灯 (星術「星灯り」。手に持って右クリックで足もとに星の印。同時に 2 つ・30 秒)
    public static final DeferredItem<StarLampItem> STAR_LAMP = ITEMS.register("star_lamp", () -> new StarLampItem(new Item.Properties().stacksTo(1)));
    // 星の欠片 (谷の三つの探す場所でもらう。門を開く印。手持ちには入れず、頼みの進みで数える。見せる絵と名前のための品)
    public static final DeferredItem<Item> STAR_SHARD = ITEMS.register("star_shard",
            () -> new jp.revati.skyabyss.story.ch4.Ch4Setup.Described(new Item.Properties()));

    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_LAMP_PLACE = sound("star_lamp_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_LAMP_FADE = sound("star_lamp_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADOW_BRIDGE_FADE = sound("shadow_bridge_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_TURN = sound("mirror_turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_ALIGN = sound("mirror_align");

    private ValleyBlocks() {
    }

    private static BlockBehaviour.Properties fixed(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable().sound(sound).pushReaction(PushReaction.BLOCK);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NS, name)));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        MoonMobsSetup.register(modEventBus);
        modEventBus.addListener(ValleyBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<? extends Item> i : List.of(STAR_LAMP, STAR_SHARD)) {
                event.accept(i.get());
            }
        }
    }
}
