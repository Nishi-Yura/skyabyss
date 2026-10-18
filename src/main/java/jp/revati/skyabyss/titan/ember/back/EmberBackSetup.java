package jp.revati.skyabyss.titan.ember.back;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 余燼の背の体と広間の登録 (13 の 11.2節の担当 B): 熾り石と、背中の音 (遠い溶岩と風・石が鳴る・炉の火が落ちる)。
// SkyAbyss のコンストラクタから register を呼ぶ
public final class EmberBackSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 熾り石 (壊せない。鳴ると光る)
    public static final DeferredBlock<KindleStoneBlock> KINDLE_STONE = BLOCKS.register(EmberIds.KINDLE_STONE,
            () -> new KindleStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f)
                    .noLootTable().sound(SoundType.POLISHED_DEEPSLATE).lightLevel(s -> s.getValue(KindleStoneBlock.ACTIVE) ? 13 : 3)));

    public static final DeferredHolder<SoundEvent, SoundEvent> BACK_AMBIENT = sound(EmberIds.SOUND_BACK_AMBIENT);
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_RING = sound(EmberIds.SOUND_STONE_RING);
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_DIM = sound(EmberIds.SOUND_FORGE_DIM);

    private EmberBackSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        SOUNDS.register(modEventBus);
        // グループごとの見かけ (鳴った石・門の火) と、グループが変わった人の後始末
        EmberBackTrials.registerGroupHooks();
    }
}
