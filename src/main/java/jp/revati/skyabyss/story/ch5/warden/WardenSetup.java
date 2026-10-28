package jp.revati.skyabyss.story.ch5.warden;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 心臓の番人 (docs/design/36 の 6) の登録。SkyAbyss のコンストラクタから register を一度だけ呼ぶ
public final class WardenSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<HeartWarden>> HEART_WARDEN = ENTITIES.register("heart_warden",
            () -> EntityType.Builder.<HeartWarden>of(HeartWarden::new, MobCategory.MONSTER)
                    .sized(1.9f, 4.3f)   // 描く大きさ 1.35 倍 (client WardenRenderer.SCALE) に合わせる
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":heart_warden"));

    private WardenSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(WardenSetup::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HEART_WARDEN.get(), HeartWarden.attributes().build());
    }
}
