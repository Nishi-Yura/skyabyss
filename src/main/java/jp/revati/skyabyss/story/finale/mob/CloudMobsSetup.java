package jp.revati.skyabyss.story.finale.mob;

import java.util.List;
import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 終章「雲海」の島の敵と主の登録 (docs/design/50 の 4.2)。素材は tools/gen/finale/gen_cloud_mobs.py。音は skyabyss_finale の名前空間。
// FinaleBlocks.register から呼ぶ
public final class CloudMobsSetup {
    public static final String SOUND_NS = "skyabyss_finale";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // 島 1「鐘の島」(docs/design/50 の 4.2.1)
    public static final DeferredHolder<EntityType<?>, EntityType<BellMourner>> BELL_MOURNER = ENTITIES.register(BellMourner.ID,
            () -> EntityType.Builder.of(BellMourner::new, MobCategory.MONSTER).sized(0.8f, 2.0f).eyeHeight(1.7f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + BellMourner.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<MistBird>> MIST_BIRD = ENTITIES.register(MistBird.ID,
            () -> EntityType.Builder.of(MistBird::new, MobCategory.MONSTER).sized(1.4f, 0.8f).eyeHeight(0.5f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + MistBird.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<LastBellkeeper>> LAST_BELLKEEPER = ENTITIES.register(LastBellkeeper.ID,
            () -> EntityType.Builder.of(LastBellkeeper::new, MobCategory.MONSTER).sized(2.0f, 4.3f).eyeHeight(3.7f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + LastBellkeeper.ID));

    public static final DeferredItem<Item> BELL_MOURNER_EGG = egg("bell_mourner_spawn_egg", BELL_MOURNER, 0xC4C0B6, 0x8C6A3E);
    public static final DeferredItem<Item> MIST_BIRD_EGG = egg("mist_bird_spawn_egg", MIST_BIRD, 0xF4F4F8, 0xDCB250);
    public static final DeferredItem<Item> LAST_BELLKEEPER_EGG = egg("last_bellkeeper_spawn_egg", LAST_BELLKEEPER, 0x343640, 0xDCB250);

    public static final DeferredHolder<SoundEvent, SoundEvent> MOURNER_IDLE = sound("mourner_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOURNER_TELL = sound("mourner_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOURNER_RING = sound("mourner_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOURNER_STRIKE = sound("mourner_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIRD_CRY = sound("bird_cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIRD_TELL = sound("bird_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIRD_DIVE = sound("bird_dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_SLAM_TELL = sound("keeper_slam_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_SLAM = sound("keeper_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_WAVE_TELL = sound("keeper_wave_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_WAVE = sound("keeper_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_SILENCE = sound("keeper_silence");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_CALL = sound("keeper_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_ROAR = sound("keeper_roar");

    private CloudMobsSetup() {
    }

    private static <T extends Mob> DeferredItem<Item> egg(String name, Supplier<? extends EntityType<T>> type, int bg, int fg) {
        return ITEMS.register(name, () -> new DeferredSpawnEggItem(type, bg, fg, new Item.Properties()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NS, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(CloudMobsSetup::onAttributes);
        modEventBus.addListener(CloudMobsSetup::onCreativeTab);
        modEventBus.addListener(CloudMobsSetup::onCommonSetup);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(BELL_MOURNER.get(), BellMourner.createAttributes().build());
        event.put(MIST_BIRD.get(), MistBird.createAttributes().build());
        event.put(LAST_BELLKEEPER.get(), LastBellkeeper.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> i : List.of(BELL_MOURNER_EGG, MIST_BIRD_EGG, LAST_BELLKEEPER_EGG)) {
                event.accept(i.get());
            }
        }
    }

    // 成長の経験値 (終章の推奨の段 CloudMob.CLOUD_LEVEL)
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            put(BellMourner.ID, 24);
            put(MistBird.ID, 22);
            put(LastBellkeeper.ID, 720);
        });
    }

    private static void put(String id, int xp) {
        GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + id, new GrowthRewards.Source(xp, CloudMob.CLOUD_LEVEL));
    }
}
