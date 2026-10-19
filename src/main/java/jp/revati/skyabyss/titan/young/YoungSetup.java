package jp.revati.skyabyss.titan.young;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 巨神の子の登録 (エンティティ四種)。SkyAbyss のコンストラクタから register を呼ぶ。
// 当たり判定は 4.6 × 3.4 (6 人が座る鞍が収まる広さ)。保存しない・コマンドで出せない
public final class YoungSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<YoungEntity>> WHALE = reg(YoungKind.WHALE);
    public static final DeferredHolder<EntityType<?>, EntityType<YoungEntity>> TIDE = reg(YoungKind.TIDE);
    public static final DeferredHolder<EntityType<?>, EntityType<YoungEntity>> STORM = reg(YoungKind.STORM);
    public static final DeferredHolder<EntityType<?>, EntityType<YoungEntity>> EMBER = reg(YoungKind.EMBER);

    private YoungSetup() {
    }

    private static DeferredHolder<EntityType<?>, EntityType<YoungEntity>> reg(YoungKind kind) {
        return ENTITIES.register(kind.model, () -> EntityType.Builder.<YoungEntity>of((type, level) -> new YoungEntity(type, level, kind), MobCategory.MISC)
                .sized(4.6f, 3.4f)
                .clientTrackingRange(24)
                .updateInterval(2)
                .fireImmune()
                .noSave()
                .noSummon()
                .build(SkyAbyss.MODID + ":" + kind.model));
    }

    public static EntityType<YoungEntity> type(YoungKind kind) {
        return switch (kind) {
            case WHALE -> WHALE.get();
            case TIDE -> TIDE.get();
            case STORM -> STORM.get();
            case EMBER -> EMBER.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
