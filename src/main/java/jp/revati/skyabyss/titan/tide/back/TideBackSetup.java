package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 潮殻の背の本番 (島・三つの試練・潮招き) の登録: 潮鳴り石、潮の真珠、群れの巣、潮招き、音。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModBlocks・ModItems・ModEntities・ModSounds には手を入れない)
public final class TideBackSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 潮鳴り石 (三つの試練の終わり)。壊せない。鳴ると光る
    public static final DeferredBlock<TideChimeStoneBlock> CHIME_STONE = BLOCKS.register("tide_chime_stone",
            () -> new TideChimeStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(-1.0f, 3600000.0f)
                    .noLootTable().sound(SoundType.AMETHYST).lightLevel(s -> s.getValue(TideChimeStoneBlock.ACTIVE) ? 12 : 2)));

    // 潮の真珠 (珊瑚の庭で運ぶ。一度に一つだけ島にある)
    public static final DeferredItem<Item> TIDE_PEARL = ITEMS.register("tide_pearl",
            () -> new TidePearlItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // 群れの巣 (動かない。干潮で開いて磯走りを出し、満潮で閉じる)。見た目は島のブロックで作る (このエンティティは当たり判定と体力だけ)
    public static final DeferredHolder<EntityType<?>, EntityType<ReefNest>> REEF_NEST = ENTITIES.register("reef_nest",
            () -> EntityType.Builder.of(ReefNest::new, MobCategory.MISC).sized(3.2f, 2.2f).clientTrackingRange(8).noSave()
                    .fireImmune().build(SkyAbyss.MODID + ":reef_nest"));
    // 中ボス 潮招き (片方のはさみだけが大きな、山のような蟹)
    public static final DeferredHolder<EntityType<?>, EntityType<TideBeckoner>> TIDE_BECKONER = ENTITIES.register("tide_beckoner",
            () -> EntityType.Builder.of(TideBeckoner::new, MobCategory.MONSTER).sized(4.0f, 3.0f).eyeHeight(2.4f)
                    .clientTrackingRange(10).fireImmune().build(SkyAbyss.MODID + ":tide_beckoner"));

    // ---------------------------------------------------------------- 音 (tools/gen_tide_back_assets.py、独自合成)
    public static final DeferredHolder<SoundEvent, SoundEvent> HORN_LOW = sound("tide_horn_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> HORN_HIGH = sound("tide_horn_high");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME = sound("tide_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> PEARL_TAKE = sound("tide_pearl_take");
    public static final DeferredHolder<SoundEvent, SoundEvent> PEARL_RETURN = sound("tide_pearl_return");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_FLOW = sound("tide_plug_flow");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCK = sound("tide_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEST_OPEN = sound("reef_nest_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEST_CLOSE = sound("reef_nest_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEST_BREAK = sound("reef_nest_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_AMBIENT = sound("tide_beckoner_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_HURT = sound("tide_beckoner_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_DEATH = sound("tide_beckoner_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_RAISE = sound("tide_beckoner_raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_SLAM = sound("tide_beckoner_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_BECKON = sound("tide_beckoner_beckon");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_BREATH = sound("tide_beckoner_breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_BURROW = sound("tide_beckoner_burrow");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_EMERGE = sound("tide_beckoner_emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECKONER_ROAR = sound("tide_beckoner_roar");

    private TideBackSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(TideBackSetup::onAttributes);
        modEventBus.addListener(TideBackSetup::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(REEF_NEST.get(), ReefNest.createAttributes().build());
        event.put(TIDE_BECKONER.get(), TideBeckoner.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(TIDE_PEARL.get());
        }
    }
}
