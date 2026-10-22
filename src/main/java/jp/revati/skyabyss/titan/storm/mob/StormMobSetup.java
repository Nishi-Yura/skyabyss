package jp.revati.skyabyss.titan.storm.mob;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 雷翼の背の敵 (羽守・雷蛾・雷乞い) の登録: エンティティ、スポーンエッグ、音。12 の 5.1節、11.2 の担当 2。
// 落とす素材 (雷翼の羽軸など) は装備の担当 (担当 5) が登録するので、ここでは登録しない (StormMobLoot が ID で引く)。
// SkyAbyss のコンストラクタから register を呼ぶ (共有の ModEntities・ModItems・ModSounds には手を入れない)
public final class StormMobSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // ---------------------------------------------------------------- エンティティ (大きさはモデルに合わせた)
    public static final DeferredHolder<EntityType<?>, EntityType<FeatherWarden>> FEATHER_WARDEN = ENTITIES.register("feather_warden",
            () -> EntityType.Builder.<FeatherWarden>of(FeatherWarden::new, MobCategory.MONSTER).sized(0.8f, 2.4f).eyeHeight(2.05f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":feather_warden"));
    public static final DeferredHolder<EntityType<?>, EntityType<StaticMoth>> STATIC_MOTH = ENTITIES.register("static_moth",
            () -> EntityType.Builder.of(StaticMoth::new, MobCategory.MONSTER).sized(0.9f, 0.6f).eyeHeight(0.4f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":static_moth"));
    public static final DeferredHolder<EntityType<?>, EntityType<ThunderSupplicant>> THUNDER_SUPPLICANT = ENTITIES.register("thunder_supplicant",
            () -> EntityType.Builder.of(ThunderSupplicant::new, MobCategory.MONSTER).sized(0.7f, 2.3f).eyeHeight(1.6f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":thunder_supplicant"));

    // テスト用のスポーンエッグ
    public static final DeferredItem<Item> FEATHER_WARDEN_EGG = ITEMS.register("feather_warden_spawn_egg",
            () -> new DeferredSpawnEggItem(FEATHER_WARDEN, 0x4A5268, 0xC47046, new Item.Properties()));
    public static final DeferredItem<Item> STATIC_MOTH_EGG = ITEMS.register("static_moth_spawn_egg",
            () -> new DeferredSpawnEggItem(STATIC_MOTH, 0xC4CEE2, 0xAAD2FF, new Item.Properties()));
    public static final DeferredItem<Item> THUNDER_SUPPLICANT_EGG = ITEMS.register("thunder_supplicant_spawn_egg",
            () -> new DeferredSpawnEggItem(THUNDER_SUPPLICANT, 0x52486A, 0xFFE278, new Item.Properties()));

    // ---------------------------------------------------------------- 音 (tools/gen/mobs/gen_storm_mob_assets.py、独自合成)
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_AMBIENT = sound("feather_warden_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_HURT = sound("feather_warden_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_DEATH = sound("feather_warden_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_SWING = sound("feather_warden_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_CROUCH = sound("feather_warden_crouch");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_DIVE = sound("feather_warden_dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_LAND = sound("feather_warden_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_AMBIENT = sound("static_moth_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_HURT = sound("static_moth_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_DEATH = sound("static_moth_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_CHARGE = sound("static_moth_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_DISCHARGE = sound("static_moth_discharge");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_BURST = sound("static_moth_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPLICANT_AMBIENT = sound("thunder_supplicant_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPLICANT_HURT = sound("thunder_supplicant_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPLICANT_DEATH = sound("thunder_supplicant_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPLICANT_RAISE = sound("thunder_supplicant_raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPLICANT_SWING = sound("thunder_supplicant_swing");

    private StormMobSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        // 三章の地上の敵 (帯電毛玉・峰の雛・雷拾い・焼けた羽守。21 の 5.1、P16)
        jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs.register(modEventBus);
        modEventBus.addListener(StormMobSetup::onAttributes);
        modEventBus.addListener(StormMobSetup::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(FEATHER_WARDEN.get(), FeatherWarden.createAttributes().build());
        event.put(STATIC_MOTH.get(), StaticMoth.createAttributes().build());
        event.put(THUNDER_SUPPLICANT.get(), ThunderSupplicant.createAttributes().build());
    }

    // Mod のタブの末尾に並べる (ModItems の一覧には手を入れない)
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(FEATHER_WARDEN_EGG.get());
            event.accept(STATIC_MOTH_EGG.get());
            event.accept(THUNDER_SUPPLICANT_EGG.get());
        }
    }
}
