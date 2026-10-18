package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自のブロックエンティティ
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SkyAbyss.MODID);

    // 碑 (文のキーを持つ)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TabletBlockEntity>> TABLET = TYPES.register("tablet",
            () -> BlockEntityType.Builder.of(TabletBlockEntity::new, ModBlocks.TABLET.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
