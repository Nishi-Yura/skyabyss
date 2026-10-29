package jp.revati.skyabyss.story.ch6.field;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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

// 満月の野の仕掛けのブロック・月の砂時計・光の塊 (docs/design/46 の 4.5)。ブロックは壊せない (野の形の一部)。
// 欠片の台・星の門・星の溜まりは新月の谷のもの (valley.ValleyBlocks) を使い、置かれた場所で野の決まりへ回す (FieldPuzzles)。
// 音の出来事は skyabyss_moon (assets/skyabyss_moon/sounds.json。tools/gen/ch6/gen_moon_mobs.py が書く)。絵は tools/gen/ch6/gen_field_assets.py
public final class FieldBlocks {
    public static final String SOUND_NS = "skyabyss_moon";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // 満ち引きの石 (光の石・影の石。FieldTide が状態を決める)。光の石は足場のとき光る
    public static final DeferredBlock<TideStoneBlock> TIDE_STONE = BLOCKS.register("tide_stone",
            () -> new TideStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)
                    .lightLevel(s -> !s.getValue(TideStoneBlock.SHADE) && s.getValue(TideStoneBlock.STATE) > 0 ? 11 : 0)));

    // 月の砂時計 (星術「一瞬止め」)
    public static final DeferredItem<MoonHourglassItem> MOON_HOURGLASS = ITEMS.register("moon_hourglass",
            () -> new MoonHourglassItem(new Item.Properties().stacksTo(1)));

    // 光の塊 (降る光・光の球)
    public static final DeferredHolder<EntityType<?>, EntityType<FallingLight>> FALLING_LIGHT = ENTITIES.register(FallingLight.ID,
            () -> EntityType.Builder.<FallingLight>of(FallingLight::new, MobCategory.MISC).sized(0.8f, 0.8f).clientTrackingRange(8).updateInterval(1)
                    .noSave().build(SkyAbyss.MODID + ":" + FallingLight.ID));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_BRIGHT = sound("tide_bright");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_DIM = sound("tide_dim");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_TURN = sound("tide_turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOMENT_STOP = sound("moment_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_TELL = sound("light_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHT_IMPACT = sound("light_impact");

    private FieldBlocks() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NS, name)));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(FieldBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<? extends Item> i : List.of(MOON_HOURGLASS)) {
                event.accept(i.get());
            }
        }
    }
}
