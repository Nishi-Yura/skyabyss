package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// モブの鳴き声・足音・攻撃音 (見た目と材質に合わせた音)。音源は tools/gen_mob_sounds.py で合成している
public final class MobSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_SENTINEL_AMBIENT = register("bone_sentinel_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_SENTINEL_HURT = register("bone_sentinel_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_SENTINEL_DEATH = register("bone_sentinel_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_SENTINEL_STEP = register("bone_sentinel_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_SENTINEL_GUARD = register("bone_sentinel_guard");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIB_ARCHER_AMBIENT = register("rib_archer_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIB_ARCHER_HURT = register("rib_archer_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIB_ARCHER_DEATH = register("rib_archer_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIB_ARCHER_STEP = register("rib_archer_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIB_ARCHER_SHOOT = register("rib_archer_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOSS_CRAWLER_AMBIENT = register("moss_crawler_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOSS_CRAWLER_HURT = register("moss_crawler_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOSS_CRAWLER_DEATH = register("moss_crawler_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOSS_CRAWLER_STEP = register("moss_crawler_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATERWAY_MAW_AMBIENT = register("waterway_maw_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATERWAY_MAW_HURT = register("waterway_maw_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATERWAY_MAW_DEATH = register("waterway_maw_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALEBONE_CAPTAIN_AMBIENT = register("whalebone_captain_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALEBONE_CAPTAIN_HURT = register("whalebone_captain_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALEBONE_CAPTAIN_DEATH = register("whalebone_captain_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALEBONE_CAPTAIN_STEP = register("whalebone_captain_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALEBONE_CAPTAIN_SWING = register("whalebone_captain_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALEBONE_CAPTAIN_SLAM = register("whalebone_captain_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_WHALE_HEART_AMBIENT = register("stone_whale_heart_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_WHALE_HEART_HURT = register("stone_whale_heart_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_WHALE_HEART_DEATH = register("stone_whale_heart_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBER_HEART_HURT = register("ember_heart_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_HEART_HURT = register("storm_heart_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDE_HEART_HURT = register("tide_heart_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ASH_THRALL_STEP = register("ash_thrall_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_THRALL_STEP = register("coral_thrall_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> FEATHER_WARDEN_STEP = register("feather_warden_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUNDER_SUPPLICANT_STEP = register("thunder_supplicant_step");

    private MobSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
