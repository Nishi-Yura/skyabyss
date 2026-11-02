package jp.revati.skyabyss.story.ch6.cliff;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
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

// 上弦の崖の仕掛けのブロックと錘 (docs/design/46 の 4.4)。どれも壊せない (崖の形の一部)。
// 欠片の台・星の門・星の溜まりは新月の谷のもの (valley.ValleyBlocks) を使い、置かれた場所で崖の決まりへ回す (CliffPuzzles)。
// 音の出来事は skyabyss_moon (assets/skyabyss_moon/sounds.json。tools/gen/ch6/gen_moon_mobs.py が書く)。絵は tools/gen/ch6/gen_cliff_assets.py
public final class CliffBlocks {
    public static final String SOUND_NS = "skyabyss_moon";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // 崩れる棚: 乗ると 1 秒震えて落ち、しばらくして戻る (CrumblingLedgeBlock)
    public static final DeferredBlock<CrumblingLedgeBlock> CRUMBLING_LEDGE = BLOCKS.register("crumbling_ledge",
            () -> new CrumblingLedgeBlock(fixed(MapColor.STONE, SoundType.TUFF).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)));
    // 天秤の皿 (二枚。CliffBalance が上げ下げする)
    public static final DeferredBlock<BalancePlateBlock> BALANCE_PLATE = BLOCKS.register("balance_plate",
            () -> new BalancePlateBlock(fixed(MapColor.COLOR_BLACK, SoundType.POLISHED_DEEPSLATE)));
    // 月の錘 (西の皿に載せると重くなる。右クリックで取り戻す)
    public static final DeferredBlock<MoonWeightBlock> MOON_WEIGHT = BLOCKS.register("moon_weight",
            () -> new MoonWeightBlock(fixed(MapColor.STONE, SoundType.DEEPSLATE).noOcclusion()));
    // 錘の山 (道の脇。右クリックで錘を一つ持つ)
    public static final DeferredBlock<WeightPileBlock> WEIGHT_PILE = BLOCKS.register("weight_pile",
            () -> new WeightPileBlock(fixed(MapColor.STONE, SoundType.DEEPSLATE).noOcclusion()));

    // 錘 (持ち物。西の皿の上で右クリックすると載せる。崖から離れると山へ戻る)
    public static final DeferredItem<MoonWeightItem> MOON_WEIGHT_ITEM = ITEMS.register("moon_weight",
            () -> new MoonWeightItem(MOON_WEIGHT.get(), new Item.Properties().stacksTo(4)));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEDGE_SHAKE = sound("ledge_shake");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEDGE_FALL = sound("ledge_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEDGE_BACK = sound("ledge_back");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_MOVE = sound("plate_move");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEIGHT_SET = sound("weight_set");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_BODY = sound("light_body");

    private CliffBlocks() {
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
        modEventBus.addListener(CliffBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<? extends Item> i : List.of(MOON_WEIGHT_ITEM)) {
                event.accept(i.get());
            }
        }
    }
}
