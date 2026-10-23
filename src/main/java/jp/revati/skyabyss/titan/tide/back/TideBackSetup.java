package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 潮殻の背の中ボス 潮招き (順路の中ボスの間。trial/raid/mid) とその音の登録。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModBlocks・ModItems・ModEntities・ModSounds には手を入れない)
public final class TideBackSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 中ボス 潮招き (片方のはさみだけが大きな、山のような蟹)
    public static final DeferredHolder<EntityType<?>, EntityType<TideBeckoner>> TIDE_BECKONER = ENTITIES.register("tide_beckoner",
            () -> EntityType.Builder.of(TideBeckoner::new, MobCategory.MONSTER).sized(4.0f, 3.0f).eyeHeight(2.4f)
                    .clientTrackingRange(10).fireImmune().build(SkyAbyss.MODID + ":tide_beckoner"));

    // ---------------------------------------------------------------- 音 (tools/gen/mobs/gen_tide_back_assets.py、独自合成)
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
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TIDE_BECKONER.get(), TideBeckoner.createAttributes().build());
    }

}
