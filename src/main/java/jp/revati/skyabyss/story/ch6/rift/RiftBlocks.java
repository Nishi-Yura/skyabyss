package jp.revati.skyabyss.story.ch6.rift;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 下弦の淵の仕掛けのブロックと自分の残像 (docs/design/46 の 4.6)。ブロックは壊せない (淵の形の一部)。
// 欠片の台・星の門・星の溜まり・影の橋は新月の谷のもの (valley.ValleyBlocks) を使い、置かれた場所で淵の決まりへ回す (RiftPuzzles)。
// 音の出来事は skyabyss_moon (tools/gen/ch6/gen_moon_mobs.py が sounds.json に書く)。絵は tools/gen/ch6/gen_rift_assets.py
public final class RiftBlocks {
    public static final String SOUND_NS = "skyabyss_moon";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // 下弦の鏡 (覚えているあいだと待つあいだは光る)
    public static final DeferredBlock<EchoMirrorBlock> ECHO_MIRROR = BLOCKS.register("echo_mirror",
            () -> new EchoMirrorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).noOcclusion()
                    .lightLevel(s -> s.getValue(EchoMirrorBlock.MODE) == EchoMirrorBlock.Mode.IDLE ? 4 : 11)));

    // 残像の板 (踏まれると光る)
    public static final DeferredBlock<EchoPlateBlock> ECHO_PLATE = BLOCKS.register("echo_plate",
            () -> new EchoPlateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noCollission()
                    .lightLevel(s -> s.getValue(EchoPlateBlock.PRESSED) ? 9 : 2)));

    public static final DeferredHolder<EntityType<?>, EntityType<EchoShade>> ECHO_SHADE = ENTITIES.register(EchoShade.ID,
            () -> EntityType.Builder.<EchoShade>of(EchoShade::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(10).updateInterval(1)
                    .noSave().build(SkyAbyss.MODID + ":" + EchoShade.ID));

    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_RECORD = sound("echo_record");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_TICK = sound("echo_tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_STORED = sound("echo_stored");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_RELEASE = sound("echo_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_FADE = sound("echo_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE_ON = sound("plate_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_OPEN = sound("door_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_CLOSE = sound("door_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> REMNANT_MARK = sound("remnant_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> REMNANT_RISE = sound("remnant_rise");

    private RiftBlocks() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NS, name)));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
