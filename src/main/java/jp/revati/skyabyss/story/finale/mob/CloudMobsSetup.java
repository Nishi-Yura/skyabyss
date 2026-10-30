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

    // 島 2「灯の島」(docs/design/50 の 4.2.2)
    public static final DeferredHolder<EntityType<?>, EntityType<NetCaster>> NET_CASTER = ENTITIES.register(NetCaster.ID,
            () -> EntityType.Builder.of(NetCaster::new, MobCategory.MONSTER).sized(0.8f, 2.0f).eyeHeight(1.7f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + NetCaster.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<LampMoth>> LAMP_MOTH = ENTITIES.register(LampMoth.ID,
            () -> EntityType.Builder.of(LampMoth::new, MobCategory.MONSTER).sized(0.9f, 0.6f).eyeHeight(0.35f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + LampMoth.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<LampEye>> LAMP_EYE = ENTITIES.register(LampEye.ID,
            () -> EntityType.Builder.of(LampEye::new, MobCategory.MONSTER).sized(2.4f, 2.8f).eyeHeight(1.4f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + LampEye.ID));

    // 島 3「炉の島」(docs/design/50 の 4.2.3)
    public static final DeferredHolder<EntityType<?>, EntityType<AshStriker>> ASH_STRIKER = ENTITIES.register(AshStriker.ID,
            () -> EntityType.Builder.of(AshStriker::new, MobCategory.MONSTER).sized(1.0f, 2.1f).eyeHeight(1.8f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + AshStriker.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<Backfire>> BACKFIRE = ENTITIES.register(Backfire.ID,
            () -> EntityType.Builder.of(Backfire::new, MobCategory.MONSTER).sized(0.8f, 1.2f).eyeHeight(0.7f).fireImmune()
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + Backfire.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<Rewinder>> REWINDER = ENTITIES.register(Rewinder.ID,
            () -> EntityType.Builder.of(Rewinder::new, MobCategory.MONSTER).sized(1.6f, 3.7f).eyeHeight(3.2f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + Rewinder.ID));

    // 島 4「約束の島」(docs/design/50 の 4.2.4)
    public static final DeferredHolder<EntityType<?>, EntityType<FallenPlume>> FALLEN_PLUME = ENTITIES.register(FallenPlume.ID,
            () -> EntityType.Builder.of(FallenPlume::new, MobCategory.MONSTER).sized(0.9f, 2.6f).eyeHeight(1.6f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + FallenPlume.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<PlumeWarden>> PLUME_WARDEN = ENTITIES.register(PlumeWarden.ID,
            () -> EntityType.Builder.of(PlumeWarden::new, MobCategory.MONSTER).sized(0.9f, 2.3f).eyeHeight(2.0f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + PlumeWarden.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<PilgrimPlume>> PILGRIM_PLUME = ENTITIES.register(PilgrimPlume.ID,
            () -> EntityType.Builder.of(PilgrimPlume::new, MobCategory.MONSTER).sized(3.0f, 4.5f).eyeHeight(1.9f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + PilgrimPlume.ID));

    public static final DeferredItem<Item> BELL_MOURNER_EGG = egg("bell_mourner_spawn_egg", BELL_MOURNER, 0xC4C0B6, 0x8C6A3E);
    public static final DeferredItem<Item> MIST_BIRD_EGG = egg("mist_bird_spawn_egg", MIST_BIRD, 0xF4F4F8, 0xDCB250);
    public static final DeferredItem<Item> LAST_BELLKEEPER_EGG = egg("last_bellkeeper_spawn_egg", LAST_BELLKEEPER, 0x343640, 0xDCB250);
    public static final DeferredItem<Item> NET_CASTER_EGG = egg("net_caster_spawn_egg", NET_CASTER, 0x2E3C3A, 0xA08860);
    public static final DeferredItem<Item> LAMP_MOTH_EGG = egg("lamp_moth_spawn_egg", LAMP_MOTH, 0xF4F2EC, 0xFFD890);
    public static final DeferredItem<Item> LAMP_EYE_EGG = egg("lamp_eye_spawn_egg", LAMP_EYE, 0xB89646, 0x5A9C88);
    public static final DeferredItem<Item> ASH_STRIKER_EGG = egg("ash_striker_spawn_egg", ASH_STRIKER, 0x605E5A, 0xF08C32);
    public static final DeferredItem<Item> BACKFIRE_EGG = egg("backfire_spawn_egg", BACKFIRE, 0x323238, 0xFFB45A);
    public static final DeferredItem<Item> REWINDER_EGG = egg("rewinder_spawn_egg", REWINDER, 0x54382A, 0xB08C54);
    public static final DeferredItem<Item> FALLEN_PLUME_EGG = egg("fallen_plume_spawn_egg", FALLEN_PLUME, 0xF4F4F8, 0xDCB250);
    public static final DeferredItem<Item> PLUME_WARDEN_EGG = egg("plume_warden_spawn_egg", PLUME_WARDEN, 0xDCB250, 0xF4F4F8);
    public static final DeferredItem<Item> PILGRIM_PLUME_EGG = egg("pilgrim_plume_spawn_egg", PILGRIM_PLUME, 0xFFFFFF, 0xFFE8B4);

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
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM_CAUGHT = sound("beam_caught");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_LIT = sound("lamp_lit");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_OUT = sound("lamp_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASTER_TELL = sound("caster_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASTER_CAST = sound("caster_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASTER_IDLE = sound("caster_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_FLARE = sound("moth_flare");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_MARK = sound("moth_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_STARE = sound("eye_stare");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_BEAM = sound("eye_beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_CLOSE = sound("eye_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_RAIN_TELL = sound("eye_rain_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_RAIN = sound("eye_rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_CALL = sound("eye_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_ROAR = sound("eye_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWIND_BACK = sound("rewind_back");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORGE_LIT = sound("forge_lit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_MARK = sound("echo_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_BURST = sound("echo_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKER_TELL = sound("striker_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKER_SLAM = sound("striker_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKFIRE_TELL = sound("backfire_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKFIRE_THROW = sound("backfire_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWINDER_SLAM_TELL = sound("rewinder_slam_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWINDER_SLAM = sound("rewinder_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWINDER_SWEEP = sound("rewinder_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWINDER_PRINTS = sound("rewinder_prints");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWINDER_CALL = sound("rewinder_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWINDER_ROAR = sound("rewinder_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_JOIN = sound("self_join");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_SHIELD = sound("self_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_TAUNT = sound("self_taunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_RAISE = sound("self_raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUME_TELL = sound("plume_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUME_VOLLEY = sound("plume_volley");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_TELL = sound("warden_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_CHARGE = sound("warden_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARDEN_HIT = sound("warden_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_BEAT_TELL = sound("wing_beat_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_BEAT = sound("wing_beat");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_RAIN_TELL = sound("wing_rain_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_RAIN = sound("wing_rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_MARK = sound("wing_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_PLUNGE = sound("wing_plunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_JUDGE_TELL = sound("wing_judge_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_JUDGE = sound("wing_judge");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_CALL = sound("wing_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> WING_ROAR = sound("wing_roar");

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
        event.put(NET_CASTER.get(), NetCaster.createAttributes().build());
        event.put(LAMP_MOTH.get(), LampMoth.createAttributes().build());
        event.put(LAMP_EYE.get(), LampEye.createAttributes().build());
        event.put(ASH_STRIKER.get(), AshStriker.createAttributes().build());
        event.put(BACKFIRE.get(), Backfire.createAttributes().build());
        event.put(REWINDER.get(), Rewinder.createAttributes().build());
        event.put(FALLEN_PLUME.get(), FallenPlume.createAttributes().build());
        event.put(PLUME_WARDEN.get(), PlumeWarden.createAttributes().build());
        event.put(PILGRIM_PLUME.get(), PilgrimPlume.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> i : List.of(BELL_MOURNER_EGG, MIST_BIRD_EGG, LAST_BELLKEEPER_EGG, NET_CASTER_EGG, LAMP_MOTH_EGG, LAMP_EYE_EGG, ASH_STRIKER_EGG,
                    BACKFIRE_EGG, REWINDER_EGG, FALLEN_PLUME_EGG, PLUME_WARDEN_EGG, PILGRIM_PLUME_EGG)) {
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
            put(NetCaster.ID, 26);
            put(LampMoth.ID, 20);
            put(LampEye.ID, 760);
            put(AshStriker.ID, 28);
            put(Backfire.ID, 22);
            put(Rewinder.ID, 800);
            put(FallenPlume.ID, 22);
            put(PlumeWarden.ID, 30);
            put(PilgrimPlume.ID, 840);
        });
    }

    private static void put(String id, int xp) {
        GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + id, new GrowthRewards.Source(xp, CloudMob.CLOUD_LEVEL));
    }
}
