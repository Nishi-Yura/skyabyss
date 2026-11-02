package jp.revati.skyabyss.titan.tide.mob;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 潮殻の背の敵 (珊瑚憑き・磯走り・泡吹き) の登録: エンティティ、泡、スポーンエッグ、落とす素材、音。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModEntities・ModItems・ModSounds には手を入れない)
public final class TideMobSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // ---------------------------------------------------------------- エンティティ
    public static final DeferredHolder<EntityType<?>, EntityType<CoralThrall>> CORAL_THRALL = ENTITIES.register("coral_thrall",
            () -> EntityType.Builder.of(CoralThrall::new, MobCategory.MONSTER).sized(0.7f, 2.3f).eyeHeight(2.0f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":coral_thrall"));
    public static final DeferredHolder<EntityType<?>, EntityType<ReefScuttler>> REEF_SCUTTLER = ENTITIES.register("reef_scuttler",
            () -> EntityType.Builder.of(ReefScuttler::new, MobCategory.MONSTER).sized(0.9f, 0.55f).eyeHeight(0.4f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":reef_scuttler"));
    public static final DeferredHolder<EntityType<?>, EntityType<BubblePuffer>> BUBBLE_PUFFER = ENTITIES.register("bubble_puffer",
            () -> EntityType.Builder.of(BubblePuffer::new, MobCategory.MONSTER).sized(0.8f, 0.8f).eyeHeight(0.45f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":bubble_puffer"));
    public static final DeferredHolder<EntityType<?>, EntityType<BubbleShot>> BUBBLE_SHOT = ENTITIES.register("bubble_shot",
            () -> EntityType.Builder.<BubbleShot>of(BubbleShot::new, MobCategory.MISC).sized(0.4f, 0.4f)
                    .clientTrackingRange(6).updateInterval(2).noSave().build(SkyAbyss.MODID + ":bubble_shot"));

    // ---------------------------------------------------------------- 落とす素材 (08 の 6.1節)。
    // 潮殻の殻片と潮干しの貝は、心臓の報酬 (別の担当) でも使う。絵は仮 (tools/gen/mobs/gen_tide_mob_assets.py)
    public static final DeferredItem<Item> TIDE_SHELL_SHARD = ITEMS.registerSimpleItem("tide_shell_shard");
    public static final DeferredItem<Item> TIDE_DRIED_SHELL = ITEMS.register("tide_dried_shell",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f)
                    .effect(() -> new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0), 1f).build())));
    public static final DeferredItem<Item> BUBBLE_SAC = ITEMS.registerSimpleItem("bubble_sac");

    // テスト用のスポーンエッグ
    public static final DeferredItem<Item> CORAL_THRALL_EGG = ITEMS.register("coral_thrall_spawn_egg",
            () -> new DeferredSpawnEggItem(CORAL_THRALL, 0xCFC6AE, 0xAD4762, new Item.Properties()));
    public static final DeferredItem<Item> REEF_SCUTTLER_EGG = ITEMS.register("reef_scuttler_spawn_egg",
            () -> new DeferredSpawnEggItem(REEF_SCUTTLER, 0xA94B2A, 0x6B7078, new Item.Properties()));
    public static final DeferredItem<Item> BUBBLE_PUFFER_EGG = ITEMS.register("bubble_puffer_spawn_egg",
            () -> new DeferredSpawnEggItem(BUBBLE_PUFFER, 0x3F8189, 0xC3CBBC, new Item.Properties()));

    // ---------------------------------------------------------------- 音 (tools/gen_tide_mob_sounds.py)
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_AMBIENT = sound("coral_thrall_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_HURT = sound("coral_thrall_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_DEATH = sound("coral_thrall_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_THRUST = sound("coral_thrall_thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_PETRIFY = sound("coral_thrall_petrify");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_WAKE = sound("coral_thrall_wake");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_STONE_TAP = sound("coral_thrall_stone_tap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCUTTLER_AMBIENT = sound("reef_scuttler_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCUTTLER_HURT = sound("reef_scuttler_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCUTTLER_DEATH = sound("reef_scuttler_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCUTTLER_STEP = sound("reef_scuttler_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCUTTLER_FLIP = sound("reef_scuttler_flip");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFFER_AMBIENT = sound("bubble_puffer_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFFER_HURT = sound("bubble_puffer_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFFER_DEATH = sound("bubble_puffer_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFFER_SHOOT = sound("bubble_puffer_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFFER_INFLATE = sound("bubble_puffer_inflate");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUFFER_DEFLATE = sound("bubble_puffer_deflate");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE_POP = sound("bubble_pop");

    private TideMobSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(TideMobSetup::onAttributes);
        modEventBus.addListener(TideMobSetup::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CORAL_THRALL.get(), CoralThrall.createAttributes().build());
        event.put(REEF_SCUTTLER.get(), ReefScuttler.createAttributes().build());
        event.put(BUBBLE_PUFFER.get(), BubblePuffer.createAttributes().build());
    }

    // Mod のタブの末尾に並べる (ModItems の一覧には手を入れない)
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(TIDE_SHELL_SHARD.get());
            event.accept(TIDE_DRIED_SHELL.get());
            event.accept(BUBBLE_SAC.get());
            event.accept(CORAL_THRALL_EGG.get());
            event.accept(REEF_SCUTTLER_EGG.get());
            event.accept(BUBBLE_PUFFER_EGG.get());
        }
    }
}
