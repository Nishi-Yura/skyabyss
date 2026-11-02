package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneGull;
import jp.revati.skyabyss.entity.BoneMortar;
import jp.revati.skyabyss.entity.ElderSentinel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 岩鯨の背の新しい敵: 骨の砲手・骨鳥・老番兵。型・属性・スポーンエッグ。
// 登録は ModEntities.register から呼ぶ (SkyAbyss のコンストラクタは触らない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch1BackEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items EGGS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 骨の砲手は丸い台座で低く幅がある (描画 1.5 倍。台座の幅 2 マス・砲身の先 2 マス)
    public static final DeferredHolder<EntityType<?>, EntityType<BoneMortar>> BONE_MORTAR = ENTITIES.register("bone_mortar",
            () -> EntityType.Builder.<BoneMortar>of(BoneMortar::new, MobCategory.MONSTER)
                    .sized(1.6f, 1.9f).clientTrackingRange(10).build(SkyAbyss.MODID + ":bone_mortar"));
    // 骨鳥は小さい (描画 1.6 倍。翼を広げて 2.2 マス)
    public static final DeferredHolder<EntityType<?>, EntityType<BoneGull>> BONE_GULL = ENTITIES.register("bone_gull",
            () -> EntityType.Builder.<BoneGull>of(BoneGull::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.7f).eyeHeight(0.4f).clientTrackingRange(10).build(SkyAbyss.MODID + ":bone_gull"));
    // 老番兵は骨の番兵 (0.8 x 2.4) の 1.12 倍の変種 (ラフの寸法そのまま)
    public static final DeferredHolder<EntityType<?>, EntityType<ElderSentinel>> ELDER_SENTINEL = ENTITIES.register("elder_sentinel",
            () -> EntityType.Builder.<ElderSentinel>of(ElderSentinel::new, MobCategory.MONSTER)
                    .sized(0.9f, 2.9f).clientTrackingRange(10).build(SkyAbyss.MODID + ":elder_sentinel"));

    public static final DeferredItem<Item> BONE_MORTAR_EGG = EGGS.register("bone_mortar_spawn_egg",
            () -> new DeferredSpawnEggItem(BONE_MORTAR, 0xC9BE9E, 0x6E7480, new Item.Properties()));
    public static final DeferredItem<Item> BONE_GULL_EGG = EGGS.register("bone_gull_spawn_egg",
            () -> new DeferredSpawnEggItem(BONE_GULL, 0xE4DCC4, 0xC9962A, new Item.Properties()));
    public static final DeferredItem<Item> ELDER_SENTINEL_EGG = EGGS.register("elder_sentinel_spawn_egg",
            () -> new DeferredSpawnEggItem(ELDER_SENTINEL, 0x7B8591, 0xC9962A, new Item.Properties()));

    private Ch1BackEntities() {
    }

    @SubscribeEvent
    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BONE_MORTAR.get(), BoneMortar.createAttributes().build());
        event.put(BONE_GULL.get(), BoneGull.createAttributes().build());
        event.put(ELDER_SENTINEL.get(), ElderSentinel.createAttributes().build());
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        EGGS.register(modEventBus);
        Ch1BackSounds.register(modEventBus);
    }
}
