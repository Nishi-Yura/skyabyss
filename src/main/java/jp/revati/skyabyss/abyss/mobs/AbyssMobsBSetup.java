package jp.revati.skyabyss.abyss.mobs;

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

// 深淵の敵 B 組 (docs/design/36 の 4・A3): 登録。灰の層 3 種・深み 2 種・深淵だけ 4 種。
// 敵と品の ID は skyabyss の名前空間。音の出来事だけ skyabyss_abyss_b (assets/skyabyss_abyss_b/sounds.json。ほかの作業の sounds.json と分ける)。
// 素材と見た目は tools/gen/abyss/gen_abyss_mobs_b.py (絵は tools/abyss/paint_abyss_b.py)。SkyAbyss のコンストラクタから register を呼ぶ
public final class AbyssMobsBSetup {
    public static final String SOUND_NS = "skyabyss_abyss_b";

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // ---------------------------------------------------------------- 敵
    public static final DeferredHolder<EntityType<?>, EntityType<AshTrail>> ASH_TRAIL = ENTITIES.register(AshTrail.ID,
            () -> EntityType.Builder.of(AshTrail::new, MobCategory.MONSTER).sized(0.8f, 0.4f).eyeHeight(0.3f).fireImmune()
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + AshTrail.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<EmberKeeper>> EMBER_KEEPER = ENTITIES.register(EmberKeeper.ID,
            () -> EntityType.Builder.of(EmberKeeper::new, MobCategory.MONSTER).sized(0.65f, 1.95f).eyeHeight(1.7f).fireImmune()
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + EmberKeeper.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<SootChild>> SOOT_CHILD = ENTITIES.register(SootChild.ID,
            () -> EntityType.Builder.of(SootChild::new, MobCategory.MONSTER).sized(0.45f, 0.45f).eyeHeight(0.3f)
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":" + SootChild.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<PastShadow>> PAST_SHADOW = ENTITIES.register(PastShadow.ID,
            () -> EntityType.Builder.of(PastShadow::new, MobCategory.MONSTER).sized(0.65f, 1.95f).eyeHeight(1.7f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + PastShadow.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<NamelessShape>> NAMELESS_SHAPE = ENTITIES.register(NamelessShape.ID,
            () -> EntityType.Builder.of(NamelessShape::new, MobCategory.MONSTER).sized(1.2f, 1.6f).eyeHeight(1.2f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + NamelessShape.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<LampEater>> LAMP_EATER = ENTITIES.register(LampEater.ID,
            () -> EntityType.Builder.of(LampEater::new, MobCategory.MONSTER).sized(0.8f, 0.8f).eyeHeight(0.6f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + LampEater.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<CeilingWalker>> CEILING_WALKER = ENTITIES.register(CeilingWalker.ID,
            () -> EntityType.Builder.of(CeilingWalker::new, MobCategory.MONSTER).sized(0.7f, 1.9f).eyeHeight(1.6f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + CeilingWalker.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<HeartbeatMimic>> HEARTBEAT_MIMIC = ENTITIES.register(HeartbeatMimic.ID,
            () -> EntityType.Builder.of(HeartbeatMimic::new, MobCategory.MONSTER).sized(1.0f, 1.2f).eyeHeight(0.9f)
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":" + HeartbeatMimic.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<RopeHanger>> ROPE_HANGER = ENTITIES.register(RopeHanger.ID,
            () -> EntityType.Builder.of(RopeHanger::new, MobCategory.MONSTER).sized(0.7f, 2.4f).eyeHeight(2.0f)
                    .clientTrackingRange(12).build(SkyAbyss.MODID + ":" + RopeHanger.ID));

    public static final DeferredItem<Item> ASH_TRAIL_EGG = egg("ash_trail_spawn_egg", ASH_TRAIL, 0x2A2622, 0x9C8F80);
    public static final DeferredItem<Item> EMBER_KEEPER_EGG = egg("ember_keeper_spawn_egg", EMBER_KEEPER, 0x3A2A24, 0xFF6A2A);
    public static final DeferredItem<Item> SOOT_CHILD_EGG = egg("soot_child_spawn_egg", SOOT_CHILD, 0x1A1816, 0x4A4540);
    public static final DeferredItem<Item> PAST_SHADOW_EGG = egg("past_shadow_spawn_egg", PAST_SHADOW, 0x1C2328, 0x7FA3B0);
    public static final DeferredItem<Item> NAMELESS_SHAPE_EGG = egg("nameless_shape_spawn_egg", NAMELESS_SHAPE, 0x2A2F3A, 0xB0C0D8);
    public static final DeferredItem<Item> LAMP_EATER_EGG = egg("lamp_eater_spawn_egg", LAMP_EATER, 0x0F0F12, 0xFFB040);
    public static final DeferredItem<Item> CEILING_WALKER_EGG = egg("ceiling_walker_spawn_egg", CEILING_WALKER, 0x2A2E2C, 0x9AA89E);
    public static final DeferredItem<Item> HEARTBEAT_MIMIC_EGG = egg("heartbeat_mimic_spawn_egg", HEARTBEAT_MIMIC, 0x3A2630, 0xE06070);
    public static final DeferredItem<Item> ROPE_HANGER_EGG = egg("rope_hanger_spawn_egg", ROPE_HANGER, 0x3B3024, 0xB89A60);

    // ---------------------------------------------------------------- 品 (深淵の材料。説明の一行は item.skyabyss.<id>.desc)
    public static final DeferredItem<Item> LAMP_ASH = ITEMS.register("lamp_ash", () -> new Ch4Setup.Described(new Item.Properties()));
    public static final DeferredItem<Item> SOOT_CLUMP = ITEMS.register("soot_clump", () -> new Ch4Setup.Described(new Item.Properties()));
    public static final DeferredItem<Item> ROPE_KNOT = ITEMS.register("rope_knot", () -> new Ch4Setup.Described(new Item.Properties()));
    public static final DeferredItem<Item> ECHO_SHARD = ITEMS.register("echo_shard", () -> new Ch4Setup.Described(new Item.Properties()));
    public static final DeferredItem<Item> PULSE_MEMBRANE = ITEMS.register("pulse_membrane", () -> new Ch4Setup.Described(new Item.Properties()));

    // ---------------------------------------------------------------- 音 (既存の音の別名。assets/skyabyss_abyss_b/sounds.json)
    public static final DeferredHolder<SoundEvent, SoundEvent> ASH_TRAIL_STEP = sound("ash_trail_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASH_TRAIL_REVEAL = sound("ash_trail_reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASH_TRAIL_ATTACK = sound("ash_trail_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_KEEPER_IDLE = sound("ember_keeper_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_KEEPER_TELEGRAPH = sound("ember_keeper_telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_KEEPER_HEAT = sound("ember_keeper_heat");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOOT_CHILD_IDLE = sound("soot_child_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOOT_CHILD_BITE = sound("soot_child_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> PAST_SHADOW_ECHO = sound("past_shadow_echo");
    public static final DeferredHolder<SoundEvent, SoundEvent> PAST_SHADOW_MIRROR = sound("past_shadow_mirror");
    public static final DeferredHolder<SoundEvent, SoundEvent> NAMELESS_SHAPE_HUM = sound("nameless_shape_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> NAMELESS_SHAPE_SHOOT = sound("nameless_shape_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_EATER_SNUFF = sound("lamp_eater_snuff");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAMP_EATER_HISS = sound("lamp_eater_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> CEILING_CREAK = sound("ceiling_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> CEILING_DROP = sound("ceiling_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT_LURE = sound("heartbeat_lure");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT_BITE = sound("heartbeat_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_CREAK = sound("rope_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_PULL = sound("rope_pull");

    private AbyssMobsBSetup() {
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
        modEventBus.addListener(AbyssMobsBSetup::onAttributes);
        modEventBus.addListener(AbyssMobsBSetup::onCreativeTab);
        modEventBus.addListener(AbyssMobsBSetup::onCommonSetup);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ASH_TRAIL.get(), AshTrail.createAttributes().build());
        event.put(EMBER_KEEPER.get(), EmberKeeper.createAttributes().build());
        event.put(SOOT_CHILD.get(), SootChild.createAttributes().build());
        event.put(PAST_SHADOW.get(), PastShadow.createAttributes().build());
        event.put(NAMELESS_SHAPE.get(), NamelessShape.createAttributes().build());
        event.put(LAMP_EATER.get(), LampEater.createAttributes().build());
        event.put(CEILING_WALKER.get(), CeilingWalker.createAttributes().build());
        event.put(HEARTBEAT_MIMIC.get(), HeartbeatMimic.createAttributes().build());
        event.put(ROPE_HANGER.get(), RopeHanger.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredItem<Item> i : List.of(ASH_TRAIL_EGG, EMBER_KEEPER_EGG, SOOT_CHILD_EGG, PAST_SHADOW_EGG, NAMELESS_SHAPE_EGG,
                    LAMP_EATER_EGG, CEILING_WALKER_EGG, HEARTBEAT_MIMIC_EGG, ROPE_HANGER_EGG, LAMP_ASH, SOOT_CLUMP, ROPE_KNOT, ECHO_SHARD,
                    PULSE_MEMBRANE)) {
                event.accept(i.get());
            }
        }
    }

    // 成長の経験値 (章の経験値の枠には数えない小さな分)。深み・深淵だけの敵は段の高い分だけ多い
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            put(AshTrail.ID, 2);
            put(EmberKeeper.ID, 8);
            put(SootChild.ID, 1);
            put(PastShadow.ID, 14);
            put(NamelessShape.ID, 16);
            put(LampEater.ID, 6);
            put(CeilingWalker.ID, 8);
            put(HeartbeatMimic.ID, 10);
            put(RopeHanger.ID, 18);
        });
    }

    private static void put(String id, int xp) {
        GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + id, new GrowthRewards.Source(xp, AbyssScaling.ABYSS_LEVEL));
    }
}
