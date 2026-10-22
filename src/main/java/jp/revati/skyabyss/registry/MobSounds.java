package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// モブの鳴き声・足音・攻撃音 (見た目と材質に合わせた音)。音源は tools/gen/sound/gen_mob_sounds.py で合成している
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
    // 技の音 (tools/gen/sound/gen_boss_skill_sounds.py)。岩鯨の心臓と鯨骨の番兵長
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_HEART_ROAR = register("stone_heart_roar");
    // 心臓の領域の宣言の合図 (tools/gen/sound/gen_heart_domain_stings.py)
    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_STONE_SEA = register("domain_stone_sea");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_RISING_TIDE = register("domain_rising_tide");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_THUNDER_CAGE = register("domain_thunder_cage");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_HEARTH_CORE = register("domain_hearth_core");
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
    // 一章の地上の敵 (tools/gen/ch1/gen_ch1_ground_mobs.py。既存の音源に高さを付けた別名)
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCK_SHELL_BEETLE_AMBIENT = register("rock_shell_beetle_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCK_SHELL_BEETLE_HURT = register("rock_shell_beetle_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCK_SHELL_BEETLE_DEATH = register("rock_shell_beetle_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCK_SHELL_BEETLE_STEP = register("rock_shell_beetle_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCK_SHELL_BEETLE_CHARGE = register("rock_shell_beetle_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPINE_HARE_AMBIENT = register("spine_hare_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPINE_HARE_HURT = register("spine_hare_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPINE_HARE_DEATH = register("spine_hare_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPINE_HARE_STEP = register("spine_hare_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPINE_HARE_SHOOT = register("spine_hare_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPINE_HARE_RAISE = register("spine_hare_raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_HAULER_AMBIENT = register("bone_hauler_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_HAULER_HURT = register("bone_hauler_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_HAULER_DEATH = register("bone_hauler_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_HAULER_STEP = register("bone_hauler_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_AMBIENT = register("elder_sentry_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_HURT = register("elder_sentry_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_DEATH = register("elder_sentry_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_STEP = register("elder_sentry_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_GUARD = register("elder_sentry_guard");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_SWING = register("elder_sentry_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_CHARGE = register("elder_sentry_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_HORN = register("elder_sentry_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_SENTRY_STOMP = register("elder_sentry_stomp");
    // 二章の地上の敵 (tools/gen/ch2/gen_ch2_ground_mobs.py。既存の音の出来事に高さを付けた別名)
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDEPOOL_CLAM_AMBIENT = register("tidepool_clam_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDEPOOL_CLAM_OPEN = register("tidepool_clam_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDEPOOL_CLAM_SNAP = register("tidepool_clam_snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDEPOOL_CLAM_CLOSE = register("tidepool_clam_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDEPOOL_CLAM_HURT = register("tidepool_clam_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDEPOOL_CLAM_DEATH = register("tidepool_clam_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_AMBIENT = register("sand_burrower_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_BULGE = register("sand_burrower_bulge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_EMERGE = register("sand_burrower_emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_BITE = register("sand_burrower_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_HURT = register("sand_burrower_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_DEATH = register("sand_burrower_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND_BURROWER_STEP = register("sand_burrower_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_HERMIT_AMBIENT = register("drift_hermit_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_HERMIT_HURT = register("drift_hermit_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_HERMIT_DEATH = register("drift_hermit_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_HERMIT_STEP = register("drift_hermit_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_HERMIT_HIDE = register("drift_hermit_hide");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT_HERMIT_SPIT = register("drift_hermit_spit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_AMBIENT = register("shore_warden_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_HURT = register("shore_warden_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_DEATH = register("shore_warden_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_STEP = register("shore_warden_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_STANCE = register("shore_warden_stance");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_THRUST = register("shore_warden_thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_RING = register("shore_warden_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHORE_WARDEN_FLOOD = register("shore_warden_flood");

    private MobSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
