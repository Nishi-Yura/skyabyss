package jp.revati.skyabyss.story.ch6.mob;

import java.util.List;
import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.ch4.Ch4Setup;
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

// 六章 区画 1「新月の谷」・区画 2「上弦の崖」・区画 3「満月の野」の敵と主の登録 (docs/design/46 の 4.3・4.4・4.5)。素材は tools/gen/ch6/gen_moon_mobs.py。音は skyabyss_moon の名前空間。
// ValleyBlocks.register から呼ぶ
public final class MoonMobsSetup {
    public static final String SOUND_NS = "skyabyss_moon";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    public static final DeferredHolder<EntityType<?>, EntityType<DustCrawler>> DUST_CRAWLER = ENTITIES.register(DustCrawler.ID,
            () -> EntityType.Builder.of(DustCrawler::new, MobCategory.MONSTER).sized(1.0f, 0.6f).eyeHeight(0.4f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + DustCrawler.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<ShellHare>> SHELL_HARE = ENTITIES.register(ShellHare.ID,
            () -> EntityType.Builder.of(ShellHare::new, MobCategory.MONSTER).sized(0.7f, 0.75f).eyeHeight(0.55f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + ShellHare.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowStitcher>> SHADOW_STITCHER = ENTITIES.register(ShadowStitcher.ID,
            () -> EntityType.Builder.of(ShadowStitcher::new, MobCategory.MONSTER).sized(0.7f, 2.4f).eyeHeight(2.1f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + ShadowStitcher.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowEmbracer>> SHADOW_EMBRACER = ENTITIES.register(ShadowEmbracer.ID,
            () -> EntityType.Builder.of(ShadowEmbracer::new, MobCategory.MONSTER).sized(1.6f, 4.3f).eyeHeight(3.6f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + ShadowEmbracer.ID));

    // 区画 2「上弦の崖」(docs/design/46 の 4.4)
    public static final DeferredHolder<EntityType<?>, EntityType<MoonLeaper>> MOON_LEAPER = ENTITIES.register(MoonLeaper.ID,
            () -> EntityType.Builder.of(MoonLeaper::new, MobCategory.MONSTER).sized(0.8f, 0.95f).eyeHeight(0.7f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + MoonLeaper.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<PushStone>> PUSH_STONE = ENTITIES.register(PushStone.ID,
            () -> EntityType.Builder.of(PushStone::new, MobCategory.MONSTER).sized(1.1f, 1.5f).eyeHeight(0.9f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + PushStone.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<CliffCreeper>> CLIFF_CREEPER = ENTITIES.register(CliffCreeper.ID,
            () -> EntityType.Builder.of(CliffCreeper::new, MobCategory.MONSTER).sized(0.9f, 0.7f).eyeHeight(0.4f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + CliffCreeper.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<MoonScale>> MOON_SCALE = ENTITIES.register(MoonScale.ID,
            () -> EntityType.Builder.of(MoonScale::new, MobCategory.MONSTER).sized(2.2f, 4.4f).eyeHeight(3.9f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + MoonScale.ID));

    // 区画 3「満月の野」(docs/design/46 の 4.5)
    public static final DeferredHolder<EntityType<?>, EntityType<LightSwarm>> LIGHT_SWARM = ENTITIES.register(LightSwarm.ID,
            () -> EntityType.Builder.of(LightSwarm::new, MobCategory.MONSTER).sized(1.0f, 1.0f).eyeHeight(0.5f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + LightSwarm.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<ShadeStrider>> SHADE_STRIDER = ENTITIES.register(ShadeStrider.ID,
            () -> EntityType.Builder.of(ShadeStrider::new, MobCategory.MONSTER).sized(1.4f, 3.0f).eyeHeight(2.6f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + ShadeStrider.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<MirrorBearer>> MIRROR_BEARER = ENTITIES.register(MirrorBearer.ID,
            () -> EntityType.Builder.of(MirrorBearer::new, MobCategory.MONSTER).sized(1.4f, 1.6f).eyeHeight(0.7f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + MirrorBearer.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<FullKeeper>> FULL_KEEPER = ENTITIES.register(FullKeeper.ID,
            () -> EntityType.Builder.of(FullKeeper::new, MobCategory.MONSTER).sized(2.0f, 5.0f).eyeHeight(4.2f).fireImmune()
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + FullKeeper.ID));

    public static final DeferredItem<Item> DUST_CRAWLER_EGG = egg("dust_crawler_spawn_egg", DUST_CRAWLER, 0x1E2230, 0xA0A6B4);
    public static final DeferredItem<Item> SHELL_HARE_EGG = egg("shell_hare_spawn_egg", SHELL_HARE, 0x9AA0AE, 0xD8E4FF);
    public static final DeferredItem<Item> SHADOW_STITCHER_EGG = egg("shadow_stitcher_spawn_egg", SHADOW_STITCHER, 0x06060E, 0xBCD8FF);
    public static final DeferredItem<Item> SHADOW_EMBRACER_EGG = egg("shadow_embracer_spawn_egg", SHADOW_EMBRACER, 0x14162A, 0x8C96B0);
    public static final DeferredItem<Item> MOON_LEAPER_EGG = egg("moon_leaper_spawn_egg", MOON_LEAPER, 0x26262E, 0xC8D8F0);
    public static final DeferredItem<Item> PUSH_STONE_EGG = egg("push_stone_spawn_egg", PUSH_STONE, 0x5A5C64, 0xB0C8F0);
    public static final DeferredItem<Item> CLIFF_CREEPER_EGG = egg("cliff_creeper_spawn_egg", CLIFF_CREEPER, 0x3A3A42, 0x9A9AA2);
    public static final DeferredItem<Item> MOON_SCALE_EGG = egg("moon_scale_spawn_egg", MOON_SCALE, 0x2C2E36, 0xE0E4EC);
    public static final DeferredItem<Item> LIGHT_SWARM_EGG = egg("light_swarm_spawn_egg", LIGHT_SWARM, 0xB8CCEE, 0xF4F8FF);
    public static final DeferredItem<Item> SHADE_STRIDER_EGG = egg("shade_strider_spawn_egg", SHADE_STRIDER, 0x10121E, 0x9AA8D0);
    public static final DeferredItem<Item> MIRROR_BEARER_EGG = egg("mirror_bearer_spawn_egg", MIRROR_BEARER, 0x22263A, 0xDCE6F8);
    public static final DeferredItem<Item> FULL_KEEPER_EGG = egg("full_keeper_spawn_egg", FULL_KEEPER, 0xB4B6C0, 0xFFFFFF);

    // 落とし物 (月の材料。説明の一行は item.skyabyss.<id>.desc)
    public static final DeferredItem<Item> MOON_DUST = ITEMS.register("moon_dust", () -> new Ch4Setup.Described(new Item.Properties()));
    public static final DeferredItem<Item> CRESCENT_SHELL = ITEMS.register("crescent_shell", () -> new Ch4Setup.Described(new Item.Properties()));
    public static final DeferredItem<Item> SHADOW_THREAD = ITEMS.register("shadow_thread", () -> new Ch4Setup.Described(new Item.Properties()));

    public static final DeferredHolder<SoundEvent, SoundEvent> CRAWLER_DIG = sound("crawler_dig");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAWLER_BURST = sound("crawler_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAWLER_BITE = sound("crawler_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARE_HOP = sound("hare_hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARE_SLAM = sound("hare_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARE_IDLE = sound("hare_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> STITCHER_WHISPER = sound("stitcher_whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> STITCHER_REACH = sound("stitcher_reach");
    public static final DeferredHolder<SoundEvent, SoundEvent> STITCHER_EXPOSED = sound("stitcher_exposed");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_HUM = sound("embracer_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_SINK = sound("embracer_sink");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_GRAB = sound("embracer_grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_THROW = sound("embracer_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_WAVE = sound("embracer_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_FROZEN = sound("embracer_frozen");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRACER_ROAR = sound("embracer_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAPER_JUMP = sound("leaper_jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAPER_LAND = sound("leaper_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_CHARGE = sound("stone_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_PUSH = sound("stone_push");
    public static final DeferredHolder<SoundEvent, SoundEvent> CREEPER_LIFT = sound("creeper_lift");
    public static final DeferredHolder<SoundEvent, SoundEvent> CREEPER_ROCK = sound("creeper_rock");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_CREAK = sound("scale_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_THROW = sound("scale_throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_WEIGHT = sound("scale_weight");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_TILT = sound("scale_tilt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_SLAM = sound("scale_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_SWEEP = sound("scale_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_TOPPLE = sound("scale_topple");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCALE_ROAR = sound("scale_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWARM_TELL = sound("swarm_tell");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWARM_DASH = sound("swarm_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_FOLD = sound("strider_fold");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIDER_STOMP = sound("strider_stomp");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEARER_AIM = sound("bearer_aim");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEARER_BEAM = sound("bearer_beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEARER_BREAK = sound("bearer_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEARER_HIDE = sound("bearer_hide");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_CHARGE = sound("keeper_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_RELEASE = sound("keeper_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_KNEEL = sound("keeper_kneel");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_SWEEP = sound("keeper_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_ORB = sound("keeper_orb");
    public static final DeferredHolder<SoundEvent, SoundEvent> KEEPER_ROAR = sound("keeper_roar");

    private MoonMobsSetup() {
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
        modEventBus.addListener(MoonMobsSetup::onAttributes);
        modEventBus.addListener(MoonMobsSetup::onCreativeTab);
        modEventBus.addListener(MoonMobsSetup::onCommonSetup);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DUST_CRAWLER.get(), DustCrawler.createAttributes().build());
        event.put(SHELL_HARE.get(), ShellHare.createAttributes().build());
        event.put(SHADOW_STITCHER.get(), ShadowStitcher.createAttributes().build());
        event.put(SHADOW_EMBRACER.get(), ShadowEmbracer.createAttributes().build());
        event.put(MOON_LEAPER.get(), MoonLeaper.createAttributes().build());
        event.put(PUSH_STONE.get(), PushStone.createAttributes().build());
        event.put(CLIFF_CREEPER.get(), CliffCreeper.createAttributes().build());
        event.put(MOON_SCALE.get(), MoonScale.createAttributes().build());
        event.put(LIGHT_SWARM.get(), LightSwarm.createAttributes().build());
        event.put(SHADE_STRIDER.get(), ShadeStrider.createAttributes().build());
        event.put(MIRROR_BEARER.get(), MirrorBearer.createAttributes().build());
        event.put(FULL_KEEPER.get(), FullKeeper.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> i : List.of(DUST_CRAWLER_EGG, SHELL_HARE_EGG, SHADOW_STITCHER_EGG, SHADOW_EMBRACER_EGG, MOON_LEAPER_EGG, PUSH_STONE_EGG,
                    CLIFF_CREEPER_EGG, MOON_SCALE_EGG, LIGHT_SWARM_EGG, SHADE_STRIDER_EGG, MIRROR_BEARER_EGG, FULL_KEEPER_EGG, MOON_DUST, CRESCENT_SHELL,
                    SHADOW_THREAD)) {
                event.accept(i.get());
            }
        }
    }

    // 成長の経験値 (六章の帯の推奨の段 MoonMob.MOON_LEVEL)
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            put(DustCrawler.ID, 14);
            put(ShellHare.ID, 14);
            put(ShadowStitcher.ID, 20);
            put(ShadowEmbracer.ID, 400);
            put2(MoonLeaper.ID, 16);
            put2(PushStone.ID, 18);
            put2(CliffCreeper.ID, 18);
            put2(MoonScale.ID, 480);
            put3(LightSwarm.ID, 18);
            put3(ShadeStrider.ID, 20);
            put3(MirrorBearer.ID, 20);
            put3(FullKeeper.ID, 560);
        });
    }

    private static void put(String id, int xp) {
        GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + id, new GrowthRewards.Source(xp, MoonMob.MOON_LEVEL));
    }

    private static void put3(String id, int xp) {
        GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + id, new GrowthRewards.Source(xp, MoonMob.MOON_LEVEL_3));
    }

    private static void put2(String id, int xp) {
        GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + id, new GrowthRewards.Source(xp, MoonMob.MOON_LEVEL_2));
    }
}
