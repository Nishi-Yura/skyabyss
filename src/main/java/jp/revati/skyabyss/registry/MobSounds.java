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
    // 技の音 (tools/gen_boss_skill_sounds.py)。岩鯨の心臓と鯨骨の番兵長
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_ROAR = register("stone_heart_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_SHOCKWAVE = register("stone_heart_shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_PHASE = register("stone_heart_phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_GRIND = register("stone_heart_grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_SLAM = register("stone_heart_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_PLATE_LOCK = register("stone_heart_plate_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_SPIKE_CHARGE = register("stone_heart_spike_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_SPIKE_BURST = register("stone_heart_spike_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_PULSE = register("stone_heart_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_FLAME_CHARGE = register("stone_heart_flame_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_FLAME_BURST = register("stone_heart_flame_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_CHAIN = register("stone_heart_chain");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_VOLLEY_SHOT = register("stone_heart_volley_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_BEAM_CHARGE = register("stone_heart_beam_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_BEAM_FIRE = register("stone_heart_beam_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_GUARD = register("stone_heart_guard");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_EMERGE = register("stone_heart_emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_RESONANCE = register("stone_heart_resonance");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_PLATE_FALL = register("stone_heart_plate_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_CHAIN_BREAK = register("stone_heart_chain_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_CORE_SHATTER = register("stone_heart_core_shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_SHARD = register("stone_heart_shard");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTAIN_LEAP_ROAR = register("captain_leap_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTAIN_SHOCKWAVE = register("captain_shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTAIN_ERUPT_CHARGE = register("captain_erupt_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTAIN_ERUPT_BURST = register("captain_erupt_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTAIN_ROAR = register("captain_roar");
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
